package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.behavior.custom.AlarmHandler;
import com.dxrk.escalationmod.behavior.custom.DarkParanoiaHandler;
import com.dxrk.escalationmod.behavior.custom.LonelinessHandler;
import com.dxrk.escalationmod.behavior.custom.SlipperyBlocksHandler;
import com.dxrk.escalationmod.behavior.templates.SpawnEliteHandler;
import com.dxrk.escalationmod.behavior.templates.TriggerActionHandler;
import com.dxrk.escalationmod.behavior.templates.VillagerPriceHandler;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.pool.PoolRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Реестр "pool id -> обработчик".
 * Источники (в порядке приоритета):
 *  1) config/escalation/behaviors_override.json  — твои правки поверх (по id, частичные поля допустимы);
 *  2) встроенный /escalation_default_behaviors.json — то, что поставляется с модом (обновляется со сборкой).
 * Каждая запись: {"template": "<имя шаблона/кастомного обработчика>", "enabled": true, ...параметры}.
 * Изоляция: ошибка в одном id не затрагивает другие; после N ошибок id отключается до /reload.
 */
public final class BehaviorRegistry {

    public interface Factory {
        BehaviorHandler create(String poolId, JsonObject params) throws Exception;
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BUNDLED = "/escalation_default_behaviors.json";
    private static final String OVERRIDE_PATH = "escalation/behaviors_override.json";

    private static final Map<String, Factory> TEMPLATES = new HashMap<>();
    private static Map<String, BehaviorHandler> handlers = new HashMap<>();
    private static Map<String, JsonObject> specs = new HashMap<>();
    private static final Map<String, Integer> errorCounts = new HashMap<>();
    private static final Set<String> disabledByErrors = new HashSet<>();
    private static final Set<String> warned = new HashSet<>();
    private static int version = 0;

    static {
        TEMPLATES.put("trigger_action", TriggerActionHandler::new);
        TEMPLATES.put("spawn_elite", SpawnEliteHandler::new);
        TEMPLATES.put("villager_price", VillagerPriceHandler::new);
        TEMPLATES.put("custom_slippery_blocks", SlipperyBlocksHandler::new);
        TEMPLATES.put("custom_alarm", AlarmHandler::new);
        TEMPLATES.put("custom_dark_paranoia", DarkParanoiaHandler::new);
        TEMPLATES.put("custom_loneliness", LonelinessHandler::new);
    }

    private BehaviorRegistry() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        errorCounts.clear();
        disabledByErrors.clear();
        warned.clear();

        Map<String, JsonObject> merged = new LinkedHashMap<>();
        readBundled(merged);
        readOverrides(merged);

        Map<String, BehaviorHandler> built = new HashMap<>();
        Map<String, JsonObject> builtSpecs = new HashMap<>();
        int skippedDisabled = 0;
        int failed = 0;

        for (Map.Entry<String, JsonObject> en : merged.entrySet()) {
            String id = en.getKey();
            JsonObject spec = en.getValue();
            if (PoolRegistry.getById(id) == null) {
                LOGGER.warn("Escalation behaviors: id {} нет в пуле — запись пропущена.", id);
                continue;
            }
            if (!BehaviorJson.bool(spec, "enabled", true)) {
                skippedDisabled++;
                builtSpecs.put(id, spec);
                continue;
            }
            String template = BehaviorJson.str(spec, "template", "");
            Factory factory = TEMPLATES.get(template);
            if (factory == null) {
                LOGGER.error("Escalation behaviors: {} — неизвестный шаблон '{}'.", id, template);
                failed++;
                continue;
            }
            try {
                built.put(id, factory.create(id, spec));
                builtSpecs.put(id, spec);
            } catch (Exception e) {
                LOGGER.error("Escalation behaviors: {} — не удалось создать обработчик (шаблон {}): {}", id, template, e.toString());
                failed++;
            }
        }

        handlers = built;
        specs = builtSpecs;
        version++;
        LOGGER.info("Escalation behaviors: активных обработчиков {}, выключено вручную {}, с ошибкой конфигурации {}.",
                built.size(), skippedDisabled, failed);
    }

    /** Обработчик id или null (нет реализации / выключен / отключён из-за ошибок). */
    public static BehaviorHandler get(String poolId) {
        if (disabledByErrors.contains(poolId)) return null;
        return handlers.get(poolId);
    }

    public static boolean isImplemented(String poolId) {
        return handlers.containsKey(poolId);
    }

    public static JsonObject spec(String poolId) {
        return specs.get(poolId);
    }

    public static int implementedCount() {
        return handlers.size();
    }

    public static boolean isDisabledByErrors(String poolId) {
        return disabledByErrors.contains(poolId);
    }

    /** Меняется при перезагрузке/отключении — кэш групп перестраивается. */
    public static int version() {
        return version;
    }

    public static void reportError(String poolId, Throwable t) {
        int n = errorCounts.merge(poolId, 1, Integer::sum);
        int limit = EscalationConfigManager.get().behavior.errorsBeforeDisable;
        LOGGER.error("Escalation behaviors: ошибка в {} (#{}): {}", poolId, n, t.toString(), t);
        if (n >= limit && disabledByErrors.add(poolId)) {
            version++;
            LOGGER.error("Escalation behaviors: {} ОТКЛЮЧЁН после {} ошибок (остальные усложнения работают). " +
                    "Исправь параметры в behaviors_override.json и сделай /escalation debug behavior reload.", poolId, n);
        }
    }

    public static void warnOnce(String message) {
        if (warned.add(message)) {
            LOGGER.warn("Escalation behaviors: {}", message);
        }
    }

    // ---------------------------------------------------------------- загрузка файлов

    private static void readBundled(Map<String, JsonObject> into) {
        try (InputStream in = BehaviorRegistry.class.getResourceAsStream(BUNDLED)) {
            if (in == null) {
                LOGGER.error("Escalation behaviors: встроенный ресурс {} не найден в jar — это баг сборки.", BUNDLED);
                return;
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                collect(JsonParser.parseReader(r), "behaviors", into, false);
            }
        } catch (Exception e) {
            LOGGER.error("Escalation behaviors: не удалось прочитать встроенный behaviors.json", e);
        }
    }

    private static void readOverrides(Map<String, JsonObject> into) {
        Path path = FMLPaths.CONFIGDIR.get().resolve(OVERRIDE_PATH);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                JsonObject skeleton = new JsonObject();
                skeleton.addProperty("_help", "Здесь можно править усложнения без пересборки. Ключ — pool id. " +
                        "Поля записи заменяют поля встроенной (например {\"chance\": 10}). " +
                        "{\"enabled\": false} выключает усложнение. После правки: /escalation debug behavior reload");
                skeleton.add("overrides", new JsonObject());
                try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(skeleton, w);
                }
                return;
            }
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                collect(JsonParser.parseReader(r), "overrides", into, true);
            }
        } catch (Exception e) {
            LOGGER.error("Escalation behaviors: не удалось прочитать {} — переопределения проигнорированы.", path, e);
        }
    }

    private static void collect(JsonElement root, String key, Map<String, JsonObject> into, boolean merge) {
        if (root == null || !root.isJsonObject()) return;
        JsonElement section = root.getAsJsonObject().get(key);
        if (section == null || !section.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> en : section.getAsJsonObject().entrySet()) {
            if (!en.getValue().isJsonObject()) continue;
            JsonObject incoming = en.getValue().getAsJsonObject();
            JsonObject existing = into.get(en.getKey());
            if (merge && existing != null) {
                JsonObject copy = existing.deepCopy();
                for (Map.Entry<String, JsonElement> f : incoming.entrySet()) {
                    copy.add(f.getKey(), f.getValue());
                }
                into.put(en.getKey(), copy);
            } else {
                into.put(en.getKey(), incoming);
            }
        }
    }
}

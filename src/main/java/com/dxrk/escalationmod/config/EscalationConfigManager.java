package com.dxrk.escalationmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Загрузка/перезагрузка config/escalation/escalation_config.json. */
public final class EscalationConfigManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RELATIVE_PATH = "escalation/escalation_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile EscalationConfig current = new EscalationConfig();

    private EscalationConfigManager() {
    }

    public static EscalationConfig get() {
        return current;
    }

    public static void init() {
        reload();
    }

    /** @return true если конфиг успешно применён; при ошибке остаётся предыдущий и файл не трогается. */
    public static boolean reload() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(RELATIVE_PATH);
        try {
            Files.createDirectories(path.getParent());

            if (!Files.exists(path)) {
                EscalationConfig defaults = new EscalationConfig().sanitize();
                write(path, defaults);
                current = defaults;
                LOGGER.info("Escalation: создан файл конфига со значениями по умолчанию: {}", path);
                return true;
            }

            EscalationConfig loaded;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, EscalationConfig.class);
            }
            if (loaded == null) {
                LOGGER.error("Escalation: конфиг {} пуст или повреждён — используются предыдущие значения.", path);
                return false;
            }

            loaded.sanitize();
            current = loaded;
            write(path, loaded); // дописывает ключи, которых не было в файле
            LOGGER.info("Escalation: конфиг загружен из {}", path);
            return true;

        } catch (JsonParseException | IOException e) {
            LOGGER.error("Escalation: не удалось прочитать конфиг {} — используются предыдущие значения (файл не изменён).", path, e);
            return false;
        }
    }

    private static void write(Path path, EscalationConfig config) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
    }
}

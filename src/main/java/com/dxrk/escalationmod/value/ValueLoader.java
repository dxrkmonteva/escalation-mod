package com.dxrk.escalationmod.value;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

/** Копия паттерна PoolLoader — тот же принцип bundled-default -> копия в config -> редактируемо без пересборки. */
public class ValueLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUNDLED_RESOURCE_PATH = "/escalation_default_values.json";
    private static final String EXTERNAL_RELATIVE_PATH = "escalation/value.json";

    public static List<EscalationValueEntry> loadValues() {
        Path externalPath = FMLPaths.CONFIGDIR.get().resolve(EXTERNAL_RELATIVE_PATH);

        ensureExternalFileExists(externalPath);

        try (Reader reader = Files.newBufferedReader(externalPath, StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            Type listType = new TypeToken<List<EscalationValueEntry>>() {}.getType();
            List<EscalationValueEntry> values = gson.fromJson(reader, listType);

            if (values == null) {
                LOGGER.error("Escalation: value.json прочитался как null — файл {} повреждён или пуст.", externalPath);
                return Collections.emptyList();
            }

            LOGGER.info("Escalation: загружено {} записей value.json из {}", values.size(), externalPath);
            return values;

        } catch (IOException e) {
            LOGGER.error("Escalation: не удалось прочитать value.json из {}", externalPath, e);
            return Collections.emptyList();
        }
    }

    private static void ensureExternalFileExists(Path externalPath) {
        if (Files.exists(externalPath)) {
            return;
        }
        try {
            Files.createDirectories(externalPath.getParent());
            try (InputStream bundled = ValueLoader.class.getResourceAsStream(BUNDLED_RESOURCE_PATH)) {
                if (bundled == null) {
                    LOGGER.error("Escalation: встроенный ресурс {} не найден внутри jar — это баг сборки.", BUNDLED_RESOURCE_PATH);
                    return;
                }
                Files.copy(bundled, externalPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Escalation: value.json впервые скопирован в {} (можно редактировать без пересборки).", externalPath);
            }
        } catch (IOException e) {
            LOGGER.error("Escalation: не удалось создать внешний файл {}", externalPath, e);
        }
    }
}

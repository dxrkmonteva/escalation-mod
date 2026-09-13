package com.dxrk.escalationmod.pool;

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

public class PoolLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUNDLED_RESOURCE_PATH = "/escalation_default_pool.json";
    private static final String EXTERNAL_RELATIVE_PATH = "escalation/escalation_pool_full.json";

    public static List<EscalationDefinition> loadPool() {
        Path externalPath = FMLPaths.CONFIGDIR.get().resolve(EXTERNAL_RELATIVE_PATH);

        ensureExternalFileExists(externalPath);

        try (Reader reader = Files.newBufferedReader(externalPath, StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            Type listType = new TypeToken<List<EscalationDefinition>>() {}.getType();
            List<EscalationDefinition> pool = gson.fromJson(reader, listType);

            if (pool == null) {
                LOGGER.error("Escalation: пул усложнений прочитался как null — файл {} повреждён или пуст.", externalPath);
                return Collections.emptyList();
            }

            LOGGER.info("Escalation: загружено {} усложнений из {}", pool.size(), externalPath);
            return pool;

        } catch (IOException e) {
            LOGGER.error("Escalation: не удалось прочитать пул усложнений из {}", externalPath, e);
            return Collections.emptyList();
        }
    }

    private static void ensureExternalFileExists(Path externalPath) {
        if (Files.exists(externalPath)) {
            return;
        }

        try {
            Files.createDirectories(externalPath.getParent());

            try (InputStream bundled = PoolLoader.class.getResourceAsStream(BUNDLED_RESOURCE_PATH)) {
                if (bundled == null) {
                    LOGGER.error("Escalation: встроенный ресурс {} не найден внутри jar — это баг сборки.", BUNDLED_RESOURCE_PATH);
                    return;
                }
                Files.copy(bundled, externalPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Escalation: пул усложнений впервые скопирован в {} (теперь можно редактировать без пересборки мода).", externalPath);
            }

        } catch (IOException e) {
            LOGGER.error("Escalation: не удалось создать внешний файл пула {}", externalPath, e);
        }
    }
}

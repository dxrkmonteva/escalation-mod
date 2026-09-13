package com.dxrk.escalationmod.pool;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;

public class PoolRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static List<EscalationDefinition> allDefinitions = Collections.emptyList();
    private static Map<Integer, List<EscalationDefinition>> byTier = Collections.emptyMap();
    private static Map<String, EscalationDefinition> byId = Collections.emptyMap();

    public static void init() {
        allDefinitions = PoolLoader.loadPool();

        byId = new HashMap<>();
        byTier = new HashMap<>();

        int duplicateIds = 0;

        for (EscalationDefinition def : allDefinitions) {
            if (def.id == null) {
                LOGGER.warn("Escalation: запись пула без id пропущена: {}", def.name);
                continue;
            }

            if (byId.containsKey(def.id)) {
                duplicateIds++;
                LOGGER.warn("Escalation: дублирующийся id в пуле: {} — вторая запись проигнорирована.", def.id);
                continue;
            }

            byId.put(def.id, def);
            byTier.computeIfAbsent(def.tier, k -> new ArrayList<>()).add(def);
        }

        for (int tier = 1; tier <= 10; tier++) {
            int count = byTier.getOrDefault(tier, Collections.emptyList()).size();
            if (count == 0) {
                LOGGER.error("Escalation: тир {} пуст — это баг данных пула, усложнения из этого тира никогда не выпадут.", tier);
            } else {
                LOGGER.info("Escalation: тир {} — {} усложнений.", tier, count);
            }
        }

        if (duplicateIds > 0) {
            LOGGER.warn("Escalation: всего найдено {} дублирующихся id в пуле.", duplicateIds);
        }

        LOGGER.info("Escalation: реестр пула готов, всего уникальных усложнений: {}", byId.size());
    }

    public static List<EscalationDefinition> getByTier(int tier) {
        return byTier.getOrDefault(tier, Collections.emptyList());
    }

    public static EscalationDefinition getById(String id) {
        return byId.get(id);
    }

    public static List<EscalationDefinition> getAll() {
        return allDefinitions;
    }

    public static int size() {
        return byId.size();
    }
}

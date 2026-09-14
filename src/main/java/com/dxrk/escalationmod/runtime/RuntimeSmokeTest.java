package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.pool.EscalationDefinition;
import com.dxrk.escalationmod.pool.PoolRegistry;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;

/**
 * ВРЕМЕННЫЙ класс для проверки TierSelector/RarityRoller через лог сервера.
 * Убрать после появления нормального планировщика спавна и/или debug-команды.
 */
public class RuntimeSmokeTest {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void run() {
        LOGGER.info("Escalation [SMOKE TEST]: TierSelector, прогресс 0..80ч (ascending, jitter=2)");

        double totalTargetHours = 80.0;
        for (int hour = 0; hour <= 80; hour += 8) {
            int tier = TierSelector.selectTier(hour, totalTargetHours, TierSelector.Order.ASCENDING, 2);
            LOGGER.info("Escalation [SMOKE TEST]: elapsed={}h -> tier={}", hour, tier);
        }

        LOGGER.info("Escalation [SMOKE TEST]: RarityRoller — 2000 бросков на тире 1 и тире 10");
        logRarityDistribution(1, 2000);
        logRarityDistribution(10, 2000);

        List<EscalationDefinition> tier5Defs = PoolRegistry.getByTier(5);
        if (!tier5Defs.isEmpty()) {
            EscalationDefinition def = tier5Defs.get(0);
            EscalationInstance instance = new EscalationInstance(def, Rarity.LEGENDARY, 0L);
            LOGGER.info("Escalation [SMOKE TEST]: создан инстанс {} (id={}, rarity={})",
                    instance.instanceId, instance.poolId, instance.rarity);

            for (int sec = 1; sec <= 32; sec++) {
                instance.tickEscalation();
                if (sec % 4 == 0 || sec == 30) {
                    LOGGER.info("Escalation [SMOKE TEST]: t={}s -> currentMultiplier={}, effectiveMultiplier={}",
                            sec, instance.currentMultiplier, instance.getEffectiveMultiplier());
                }
            }
        }
    }

    private static void logRarityDistribution(int tier, int rolls) {
        int common = 0, rare = 0, epic = 0, legendary = 0;
        for (int i = 0; i < rolls; i++) {
            switch (RarityRoller.roll(tier)) {
                case COMMON -> common++;
                case RARE -> rare++;
                case EPIC -> epic++;
                case LEGENDARY -> legendary++;
            }
        }
        LOGGER.info("Escalation [SMOKE TEST]: тир {} / {} бросков -> common={}%, rare={}%, epic={}%, legendary={}%",
                tier, rolls, pct(common, rolls), pct(rare, rolls), pct(epic, rolls), pct(legendary, rolls));
    }

    private static double pct(int count, int total) {
        return Math.round(count * 1000.0 / total) / 10.0;
    }
}

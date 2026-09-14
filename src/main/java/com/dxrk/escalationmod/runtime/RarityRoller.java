package com.dxrk.escalationmod.runtime;

import java.security.SecureRandom;

public class RarityRoller {

    // Раздел 12 спеки: только SecureRandom, никакого java.util.Random.
    private static final SecureRandom RNG = new SecureRandom();

    // Табличные значения раздела 6 — тир 1 и тир 10, между ними линейная интерполяция.
    private static final double COMMON_T1 = 70.0, COMMON_T10 = 40.0;
    private static final double RARE_T1 = 22.0, RARE_T10 = 32.0;
    private static final double EPIC_T1 = 7.0, EPIC_T10 = 21.0;

    public static Rarity roll(int tier) {
        double t = clampTier(tier);
        double fraction = (t - 1.0) / 9.0;

        double common = lerp(COMMON_T1, COMMON_T10, fraction);
        double rare = lerp(RARE_T1, RARE_T10, fraction);
        double epic = lerp(EPIC_T1, EPIC_T10, fraction);
        double legendary = 0.5 + t * 0.65;

        double total = common + rare + epic + legendary;
        common /= total;
        rare /= total;
        epic /= total;
        legendary /= total;

        double roll = RNG.nextDouble();

        if (roll < common) {
            return Rarity.COMMON;
        }
        roll -= common;
        if (roll < rare) {
            return Rarity.RARE;
        }
        roll -= rare;
        if (roll < epic) {
            return Rarity.EPIC;
        }
        return Rarity.LEGENDARY;
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double clampTier(int tier) {
        return Math.max(1, Math.min(10, tier));
    }
}

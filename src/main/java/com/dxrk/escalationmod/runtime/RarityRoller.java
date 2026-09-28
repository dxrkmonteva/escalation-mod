package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;

import java.security.SecureRandom;

public class RarityRoller {

    // Раздел 12 спеки: только SecureRandom, никакого java.util.Random.
    private static final SecureRandom RNG = new SecureRandom();

    public static Rarity roll(int tier) {
        EscalationConfig.WeightsSection w = EscalationConfigManager.get().rarity.weights;

        double t = clampTier(tier);
        double fraction = (t - 1.0) / 9.0;

        double common = lerp(w.commonTier1, w.commonTier10, fraction);
        double rare = lerp(w.rareTier1, w.rareTier10, fraction);
        double epic = lerp(w.epicTier1, w.epicTier10, fraction);
        double legendary = w.legendaryBasePercent + t * w.legendaryPerTierPercent;

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

package com.dxrk.escalationmod.runtime;

import java.security.SecureRandom;

public class TierSelector {

    private static final SecureRandom RNG = new SecureRandom();

    public enum Order {
        ASCENDING, DESCENDING, RANDOM
    }

    public static int selectTier(double elapsedRealHours, double totalTargetHours, Order order, int jitterMax) {
        if (order == Order.RANDOM) {
            return 1 + RNG.nextInt(10);
        }

        double progress = elapsedRealHours / totalTargetHours;
        int baseTier = (int) Math.round(progress * 10.0);

        if (order == Order.DESCENDING) {
            baseTier = 11 - baseTier;
        }

        int jitter = jitterMax > 0 ? RNG.nextInt(2 * jitterMax + 1) - jitterMax : 0;
        int finalTier = baseTier + jitter;

        return Math.max(1, Math.min(10, finalTier));
    }
}

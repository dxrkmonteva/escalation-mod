package com.dxrk.escalationmod.behavior;

import java.security.SecureRandom;

/** Раздел 12 спеки: вся случайность — только SecureRandom. */
public final class BehaviorRng {

    public static final SecureRandom RNG = new SecureRandom();

    private BehaviorRng() {
    }

    public static boolean roll(double probability) {
        if (probability <= 0.0) return false;
        if (probability >= 1.0) return true;
        return RNG.nextDouble() < probability;
    }
}

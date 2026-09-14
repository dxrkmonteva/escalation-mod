package com.dxrk.escalationmod.runtime;

public enum Rarity {
    COMMON(1.0),
    RARE(1.5),
    EPIC(2.5),
    LEGENDARY(4.0);

    private final double baseMultiplier;

    Rarity(double baseMultiplier) {
        this.baseMultiplier = baseMultiplier;
    }

    public double getBaseMultiplier() {
        return baseMultiplier;
    }
}

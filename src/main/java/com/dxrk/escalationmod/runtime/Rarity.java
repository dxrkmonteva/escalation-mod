package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;

public enum Rarity {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY;

    /** Множители редкости из конфига (rarity.multipliers). */
    public double getBaseMultiplier() {
        EscalationConfig.MultipliersSection m = EscalationConfigManager.get().rarity.multipliers;
        return switch (this) {
            case COMMON -> m.common;
            case RARE -> m.rare;
            case EPIC -> m.epic;
            case LEGENDARY -> m.legendary;
        };
    }
}

package com.dxrk.escalationmod.data;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public class EscalationCapabilities {

    public static final Capability<IEscalationPlayerData> PLAYER_DATA =
            CapabilityManager.get(new CapabilityToken<>() {});

    private EscalationCapabilities() {
    }
}

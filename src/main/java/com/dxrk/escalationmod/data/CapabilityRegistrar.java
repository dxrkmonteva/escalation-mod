package com.dxrk.escalationmod.data;

import com.dxrk.escalationmod.Escalation;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Escalation.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CapabilityRegistrar {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IEscalationPlayerData.class);
    }
}

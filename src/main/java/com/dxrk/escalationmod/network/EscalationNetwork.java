package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class EscalationNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Escalation.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++,
                ShowToastPacket.class,
                ShowToastPacket::encode,
                ShowToastPacket::decode,
                ShowToastPacket::handle);
    }

    private EscalationNetwork() {
    }
}

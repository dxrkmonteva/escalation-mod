package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.stats.ClientModifiers;
import com.dxrk.escalationmod.stats.ToolFactors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент, раз в секунду: множители, которые клиент применяет сам (добыча, прыжок). */
public class ClientModifiersPacket {

    public final ToolFactors factors;

    public ClientModifiersPacket(ToolFactors factors) {
        this.factors = factors;
    }

    public static void encode(ClientModifiersPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.factors.pickaxe());
        buf.writeFloat(msg.factors.shovel());
        buf.writeFloat(msg.factors.all());
        buf.writeFloat(msg.factors.stone());
        buf.writeFloat(msg.factors.jump());
    }

    public static ClientModifiersPacket decode(FriendlyByteBuf buf) {
        return new ClientModifiersPacket(new ToolFactors(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
    }

    public static void handle(ClientModifiersPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> ClientModifiers.update(msg.factors)));
        ctx.setPacketHandled(true);
    }
}

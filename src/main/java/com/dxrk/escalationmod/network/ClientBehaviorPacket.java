package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.behavior.ClientBehaviorState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Сервер -> клиент: набор "ключ -> число" для клиентской части behavioral-эффектов (шлётся только при изменении). */
public class ClientBehaviorPacket {

    public final Map<String, Float> values;

    public ClientBehaviorPacket(Map<String, Float> values) {
        this.values = values;
    }

    public static void encode(ClientBehaviorPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.values.size());
        for (Map.Entry<String, Float> e : msg.values.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeFloat(e.getValue());
        }
    }

    public static ClientBehaviorPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Float> m = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            m.put(k, buf.readFloat());
        }
        return new ClientBehaviorPacket(m);
    }

    public static void handle(ClientBehaviorPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> ClientBehaviorState.replace(msg.values)));
        ctx.setPacketHandled(true);
    }
}

package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.client.toast.ActiveEscalationToast;
import com.dxrk.escalationmod.client.toast.ClientToastManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

/** Сервер -> клиент: "покажи тост об этом усложнении". Отправляется из EscalationScheduler. */
public class ShowToastPacket {

    private final int tier;
    private final String rarity; // "COMMON" / "RARE" / "EPIC" / "LEGENDARY"
    private final String name;
    private final String description;

    public ShowToastPacket(int tier, String rarity, String name, String description) {
        this.tier = tier;
        this.rarity = rarity;
        this.name = name;
        this.description = description;
    }

    public static void encode(ShowToastPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.tier);
        buf.writeUtf(msg.rarity);
        buf.writeUtf(msg.name);
        buf.writeUtf(msg.description);
    }

    public static ShowToastPacket decode(FriendlyByteBuf buf) {
        return new ShowToastPacket(buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(ShowToastPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            boolean legendary = "LEGENDARY".equals(msg.rarity);
            String title = "НОВОЕ УСЛОЖНЕНИЕ [Тир " + msg.tier + " \u00b7 " + msg.rarity + "]";
            ClientToastManager.INSTANCE.addToast(new ActiveEscalationToast(
                    msg.tier,
                    msg.rarity.toLowerCase(Locale.ROOT),
                    title,
                    msg.name,
                    msg.description,
                    legendary));
        }));
        ctx.setPacketHandled(true);
    }
}

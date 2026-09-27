package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.client.hud.ClientHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клиент, раз в секунду: снимок для таймер-бара и журнала (раздел 10). */
public class EscalationHudSyncPacket {

    public final boolean netherUnlocked;
    public final long netherRemainingTicks;
    public final String endStatus; // "locked_long" | "locked_soon" | "unlocked" — точные часы никогда не шлём (раздел 3.2)
    public final boolean mercyPauseActive;
    public final long mercyPauseRemainingTicks;
    public final long nextSpawnRemainingTicks;
    public final List<JournalRow> journalRows;

    public EscalationHudSyncPacket(boolean netherUnlocked, long netherRemainingTicks, String endStatus,
                                    boolean mercyPauseActive, long mercyPauseRemainingTicks,
                                    long nextSpawnRemainingTicks, List<JournalRow> journalRows) {
        this.netherUnlocked = netherUnlocked;
        this.netherRemainingTicks = netherRemainingTicks;
        this.endStatus = endStatus;
        this.mercyPauseActive = mercyPauseActive;
        this.mercyPauseRemainingTicks = mercyPauseRemainingTicks;
        this.nextSpawnRemainingTicks = nextSpawnRemainingTicks;
        this.journalRows = journalRows;
    }

    public static class JournalRow {
        public final String poolId;
        public final int count;
        public final long firstSeenTick;

        public JournalRow(String poolId, int count, long firstSeenTick) {
            this.poolId = poolId;
            this.count = count;
            this.firstSeenTick = firstSeenTick;
        }
    }

    public static void encode(EscalationHudSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.netherUnlocked);
        buf.writeVarLong(msg.netherRemainingTicks);
        buf.writeUtf(msg.endStatus);
        buf.writeBoolean(msg.mercyPauseActive);
        buf.writeVarLong(msg.mercyPauseRemainingTicks);
        buf.writeVarLong(msg.nextSpawnRemainingTicks);

        buf.writeVarInt(msg.journalRows.size());
        for (JournalRow row : msg.journalRows) {
            buf.writeUtf(row.poolId);
            buf.writeVarInt(row.count);
            buf.writeVarLong(row.firstSeenTick);
        }
    }

    public static EscalationHudSyncPacket decode(FriendlyByteBuf buf) {
        boolean netherUnlocked = buf.readBoolean();
        long netherRemainingTicks = buf.readVarLong();
        String endStatus = buf.readUtf();
        boolean mercyPauseActive = buf.readBoolean();
        long mercyPauseRemainingTicks = buf.readVarLong();
        long nextSpawnRemainingTicks = buf.readVarLong();

        int size = buf.readVarInt();
        List<JournalRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String poolId = buf.readUtf();
            int count = buf.readVarInt();
            long firstSeenTick = buf.readVarLong();
            rows.add(new JournalRow(poolId, count, firstSeenTick));
        }

        return new EscalationHudSyncPacket(netherUnlocked, netherRemainingTicks, endStatus,
                mercyPauseActive, mercyPauseRemainingTicks, nextSpawnRemainingTicks, rows);
    }

    public static void handle(EscalationHudSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> ClientHudState.INSTANCE.update(msg)));
        ctx.setPacketHandled(true);
    }
}

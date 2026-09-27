package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.client.hud.ClientHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EscalationHudSyncPacket {

    public final boolean netherUnlocked;
    public final long netherRemainingTicks;
    public final String endStatus;
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

    /** Разбивка по редкости прямо в снимке — тир резолвится на клиенте через PoolRegistry, не шлём. */
    public static class JournalRow {
        public final String poolId;
        public final long firstSeenTick;
        public final int commonCount;
        public final int rareCount;
        public final int epicCount;
        public final int legendaryCount;

        public JournalRow(String poolId, long firstSeenTick, int commonCount, int rareCount, int epicCount, int legendaryCount) {
            this.poolId = poolId;
            this.firstSeenTick = firstSeenTick;
            this.commonCount = commonCount;
            this.rareCount = rareCount;
            this.epicCount = epicCount;
            this.legendaryCount = legendaryCount;
        }

        public int total() {
            return commonCount + rareCount + epicCount + legendaryCount;
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
            buf.writeVarLong(row.firstSeenTick);
            buf.writeVarInt(row.commonCount);
            buf.writeVarInt(row.rareCount);
            buf.writeVarInt(row.epicCount);
            buf.writeVarInt(row.legendaryCount);
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
            long firstSeenTick = buf.readVarLong();
            int c = buf.readVarInt();
            int r = buf.readVarInt();
            int e = buf.readVarInt();
            int l = buf.readVarInt();
            rows.add(new JournalRow(poolId, firstSeenTick, c, r, e, l));
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

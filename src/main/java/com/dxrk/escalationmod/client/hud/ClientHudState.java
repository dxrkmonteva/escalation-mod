package com.dxrk.escalationmod.client.hud;

import com.dxrk.escalationmod.network.EscalationHudSyncPacket;
import com.dxrk.escalationmod.pool.EscalationDefinition;
import com.dxrk.escalationmod.pool.PoolRegistry;

import java.util.ArrayList;
import java.util.List;

/** Клиентский синглтон — последний полученный снимок HUD-состояния от сервера. */
public final class ClientHudState {

    public static final ClientHudState INSTANCE = new ClientHudState();

    private volatile boolean netherUnlocked = false;
    private volatile long netherRemainingTicks = 0L;
    private volatile String endStatus = "locked_long";
    private volatile boolean mercyPauseActive = false;
    private volatile long mercyPauseRemainingTicks = 0L;
    private volatile long nextSpawnRemainingTicks = 0L;
    private volatile List<JournalDisplayRow> journalRows = new ArrayList<>();
    private volatile boolean hasData = false;

    private ClientHudState() {
    }

    public void update(EscalationHudSyncPacket packet) {
        this.netherUnlocked = packet.netherUnlocked;
        this.netherRemainingTicks = packet.netherRemainingTicks;
        this.endStatus = packet.endStatus;
        this.mercyPauseActive = packet.mercyPauseActive;
        this.mercyPauseRemainingTicks = packet.mercyPauseRemainingTicks;
        this.nextSpawnRemainingTicks = packet.nextSpawnRemainingTicks;

        List<JournalDisplayRow> resolved = new ArrayList<>(packet.journalRows.size());
        for (EscalationHudSyncPacket.JournalRow row : packet.journalRows) {
            EscalationDefinition def = PoolRegistry.getById(row.poolId);
            String name = def != null ? def.name : row.poolId;
            resolved.add(new JournalDisplayRow(name, row.count, row.firstSeenTick));
        }
        this.journalRows = resolved;
        this.hasData = true;
    }

    public boolean hasData() { return hasData; }
    public boolean isNetherUnlocked() { return netherUnlocked; }
    public long getNetherRemainingTicks() { return netherRemainingTicks; }
    public String getEndStatus() { return endStatus; }
    public boolean isMercyPauseActive() { return mercyPauseActive; }
    public long getMercyPauseRemainingTicks() { return mercyPauseRemainingTicks; }
    public long getNextSpawnRemainingTicks() { return nextSpawnRemainingTicks; }
    public List<JournalDisplayRow> getJournalRows() { return journalRows; }

    public static class JournalDisplayRow {
        public final String name;
        public final int count;
        public final long firstSeenTick;

        public JournalDisplayRow(String name, int count, long firstSeenTick) {
            this.name = name;
            this.count = count;
            this.firstSeenTick = firstSeenTick;
        }
    }
}

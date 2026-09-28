package com.dxrk.escalationmod.client.hud;

import com.dxrk.escalationmod.network.EscalationHudSyncPacket;
import com.dxrk.escalationmod.pool.EscalationDefinition;
import com.dxrk.escalationmod.pool.PoolRegistry;

import java.util.ArrayList;
import java.util.List;

public final class ClientHudState {

    public static final ClientHudState INSTANCE = new ClientHudState();

    private volatile boolean netherUnlocked = false;
    private volatile long netherRemainingTicks = 0L;
    private volatile String endStatus = "locked_long";
    private volatile boolean mercyPauseActive = false;
    private volatile long mercyPauseRemainingTicks = 0L;
    private volatile long nextSpawnRemainingTicks = 0L;
    private volatile int runState = EscalationHudSyncPacket.RUN_NORMAL;
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
        this.runState = packet.runState;

        List<JournalDisplayRow> resolved = new ArrayList<>(packet.journalRows.size());
        for (EscalationHudSyncPacket.JournalRow row : packet.journalRows) {
            EscalationDefinition def = PoolRegistry.getById(row.poolId);
            String name = def != null ? def.name : row.poolId;
            String description = def != null ? def.description : "";
            int tier = def != null ? def.tier : 0;
            resolved.add(new JournalDisplayRow(row.poolId, name, description, tier, row.firstSeenTick,
                    row.commonCount, row.rareCount, row.epicCount, row.legendaryCount));
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
    public int getRunState() { return runState; }
    public List<JournalDisplayRow> getJournalRows() { return journalRows; }

    public static class JournalDisplayRow {
        public final String poolId;
        public final String name;
        public final String description;
        public final int tier;
        public final long firstSeenTick;
        public final int commonCount;
        public final int rareCount;
        public final int epicCount;
        public final int legendaryCount;

        public JournalDisplayRow(String poolId, String name, String description, int tier, long firstSeenTick,
                                  int commonCount, int rareCount, int epicCount, int legendaryCount) {
            this.poolId = poolId;
            this.name = name;
            this.description = description;
            this.tier = tier;
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
}

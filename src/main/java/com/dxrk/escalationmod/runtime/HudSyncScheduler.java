package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.dimension.DimensionLockHandler;
import com.dxrk.escalationmod.dimension.EscalationWorldData;
import com.dxrk.escalationmod.network.EscalationHudSyncPacket;
import com.dxrk.escalationmod.network.EscalationNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class HudSyncScheduler {

    private static final long END_SOON_THRESHOLD_TICKS = 10L * 60 * 60 * 20;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long currentTick = event.getServer().overworld().getGameTime();
        if (currentTick % 20 != 0) return;

        EscalationWorldData worldData = EscalationWorldData.get(event.getServer().overworld());
        long endRequiredTicks = (long) (worldData.getEndRealHoursThreshold() * 60.0 * 60.0 * 20.0);

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data ->
                    sendSnapshot(player, data, currentTick, endRequiredTicks));
        }
    }

    private static void sendSnapshot(ServerPlayer player, IEscalationPlayerData data,
                                      long currentTick, long endRequiredTicks) {
        long playerTicks = data.getRealPlaytimeTicks();

        boolean netherUnlocked = playerTicks >= DimensionLockHandler.NETHER_REQUIRED_TICKS;
        long netherRemaining = Math.max(0, DimensionLockHandler.NETHER_REQUIRED_TICKS - playerTicks);

        long endRemaining = endRequiredTicks - playerTicks;
        String endStatus;
        if (endRemaining <= 0) {
            endStatus = "unlocked";
        } else if (endRemaining < END_SOON_THRESHOLD_TICKS) {
            endStatus = "locked_soon";
        } else {
            endStatus = "locked_long";
        }

        boolean mercyActive = currentTick < data.getMercyPauseEndTick();
        long mercyRemaining = mercyActive ? data.getMercyPauseEndTick() - currentTick : 0L;
        long nextSpawnRemaining = mercyActive ? 0L : Math.max(0, data.getNextSpawnAtTick() - currentTick);

        List<EscalationHudSyncPacket.JournalRow> rows = buildJournalRows(data);

        EscalationNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EscalationHudSyncPacket(netherUnlocked, netherRemaining, endStatus,
                        mercyActive, mercyRemaining, nextSpawnRemaining, rows));
    }

    private static List<EscalationHudSyncPacket.JournalRow> buildJournalRows(IEscalationPlayerData data) {
        Map<String, List<EscalationInstance>> grouped = new LinkedHashMap<>();
        for (EscalationInstance inst : data.getActiveInstances()) {
            grouped.computeIfAbsent(inst.poolId, k -> new ArrayList<>()).add(inst);
        }

        List<EscalationHudSyncPacket.JournalRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<EscalationInstance>> entry : grouped.entrySet()) {
            List<EscalationInstance> instances = entry.getValue();
            long firstSeen = instances.stream().mapToLong(i -> i.spawnedAtTick).min().orElse(0L);

            int common = 0, rare = 0, epic = 0, legendary = 0;
            for (EscalationInstance inst : instances) {
                switch (inst.rarity) {
                    case COMMON -> common++;
                    case RARE -> rare++;
                    case EPIC -> epic++;
                    case LEGENDARY -> legendary++;
                }
            }
            rows.add(new EscalationHudSyncPacket.JournalRow(entry.getKey(), firstSeen, common, rare, epic, legendary));
        }
        rows.sort((a, b) -> Long.compare(a.firstSeenTick, b.firstSeenTick));
        return rows;
    }
}

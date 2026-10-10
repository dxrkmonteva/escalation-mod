package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Consumer;

/** Кэш групп игрока + раздача событий обработчикам ТОЛЬКО его активных id. */
public final class BehaviorManager {

    private static final Map<UUID, Cache> CACHE = new HashMap<>();

    private static final class Cache {
        IEscalationPlayerData data;
        int size = -1;
        String firstId;
        String lastId;
        int registryVersion = -1;
        List<BehaviorGroup> groups = List.of();
    }

    private BehaviorManager() {
    }

    public static List<BehaviorGroup> groupsOf(ServerPlayer player) {
        IEscalationPlayerData[] holder = new IEscalationPlayerData[1];
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(d -> holder[0] = d);
        return holder[0] == null ? List.of() : groups(player, holder[0]);
    }

    public static List<BehaviorGroup> groups(ServerPlayer player, IEscalationPlayerData data) {
        List<EscalationInstance> active = data.getActiveInstances();
        String first = active.isEmpty() ? null : active.get(0).instanceId;
        String last = active.isEmpty() ? null : active.get(active.size() - 1).instanceId;

        Cache c = CACHE.computeIfAbsent(player.getUUID(), k -> new Cache());
        if (c.data != data || c.size != active.size() || !Objects.equals(c.firstId, first)
                || !Objects.equals(c.lastId, last) || c.registryVersion != BehaviorRegistry.version()) {
            c.groups = build(active, data);
            c.data = data;
            c.size = active.size();
            c.firstId = first;
            c.lastId = last;
            c.registryVersion = BehaviorRegistry.version();
        }
        return c.groups;
    }

    private static List<BehaviorGroup> build(List<EscalationInstance> active, IEscalationPlayerData data) {
        Map<String, List<EscalationInstance>> byId = new LinkedHashMap<>();
        for (EscalationInstance inst : active) {
            if (BehaviorRegistry.get(inst.poolId) != null) {
                byId.computeIfAbsent(inst.poolId, k -> new ArrayList<>()).add(inst);
            }
        }
        List<BehaviorGroup> out = new ArrayList<>(byId.size());
        for (Map.Entry<String, List<EscalationInstance>> en : byId.entrySet()) {
            out.add(new BehaviorGroup(en.getKey(), en.getValue(), BehaviorRegistry.get(en.getKey()), data));
        }
        return out;
    }

    public static void invalidate(UUID playerId) {
        Cache c = CACHE.get(playerId);
        if (c != null) c.size = -1;
    }

    public static void invalidateAll() {
        for (Cache c : CACHE.values()) c.size = -1;
    }

    public static void forget(UUID playerId) {
        CACHE.remove(playerId);
    }

    public static void safe(String poolId, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            BehaviorRegistry.reportError(poolId, t);
        }
    }

    /** Раздать событие всем группам игрока. prep заполняет контекст, post читает выходные флаги. */
    public static void dispatch(Trigger trigger, ServerPlayer player,
                                Consumer<BehaviorContext> prep, Consumer<BehaviorContext> post) {
        if (!EscalationConfigManager.get().behavior.enabled) return;
        if (BehaviorActions.isExecuting()) return;

        for (BehaviorGroup g : groupsOf(player)) {
            BehaviorContext ctx = new BehaviorContext(trigger, player, g);
            if (prep != null) prep.accept(ctx);
            safe(g.poolId, () -> g.handler.onTrigger(ctx));
            if (post != null) post.accept(ctx);
        }
    }

    // ---------- жизненный цикл (вызывается из ActiveEscalations) ----------

    static void fireActivated(ServerPlayer player, IEscalationPlayerData data, String poolId) {
        BehaviorHandler h = BehaviorRegistry.get(poolId);
        if (h == null) return;
        for (BehaviorGroup g : groups(player, data)) {
            if (g.poolId.equals(poolId)) {
                safe(poolId, () -> h.onActivated(player, g));
                break;
            }
        }
    }

    static void fireDeactivated(ServerPlayer player, IEscalationPlayerData data, String poolId) {
        BehaviorHandler h = BehaviorRegistry.get(poolId);
        if (h != null) {
            safe(poolId, () -> h.onDeactivated(player, poolId));
        }
        data.getBehaviorState().remove(poolId);
    }
}

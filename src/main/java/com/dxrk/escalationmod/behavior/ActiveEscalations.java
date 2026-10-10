package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import com.dxrk.escalationmod.stats.StatEngine;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

/**
 * ЕДИНАЯ точка изменения списка активных усложнений. Все, кто добавляет/снимает усложнения
 * (спавнер, Mercy Rule, победа, endless, debug), ходят только сюда — тогда кэши (StatEngine,
 * BehaviorManager) и жизненный цикл обработчиков (onActivated/onDeactivated) всегда в порядке.
 */
public final class ActiveEscalations {

    private ActiveEscalations() {
    }

    public static void add(ServerPlayer player, IEscalationPlayerData data, EscalationInstance instance) {
        data.getActiveInstances().add(instance);
        StatEngine.invalidate(player.getUUID());
        BehaviorManager.invalidate(player.getUUID());
        BehaviorManager.fireActivated(player, data, instance.poolId);
    }

    /** @return сколько ЭКЗЕМПЛЯРОВ снято. onDeactivated вызывается для id, у которых не осталось ни одного экземпляра. */
    public static int removeIf(ServerPlayer player, IEscalationPlayerData data, Predicate<EscalationInstance> filter) {
        Set<String> touched = new HashSet<>();
        int removed = 0;
        Iterator<EscalationInstance> it = data.getActiveInstances().iterator();
        while (it.hasNext()) {
            EscalationInstance inst = it.next();
            if (filter.test(inst)) {
                touched.add(inst.poolId);
                it.remove();
                removed++;
            }
        }
        if (removed == 0) return 0;

        StatEngine.invalidate(player.getUUID());
        BehaviorManager.invalidate(player.getUUID());

        Set<String> stillActive = new HashSet<>();
        for (EscalationInstance inst : data.getActiveInstances()) {
            stillActive.add(inst.poolId);
        }
        for (String id : touched) {
            if (!stillActive.contains(id)) {
                BehaviorManager.fireDeactivated(player, data, id);
            }
        }
        return removed;
    }

    public static int clear(ServerPlayer player, IEscalationPlayerData data) {
        return removeIf(player, data, i -> true);
    }
}

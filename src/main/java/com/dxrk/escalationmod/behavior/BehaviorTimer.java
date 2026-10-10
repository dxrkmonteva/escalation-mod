package com.dxrk.escalationmod.behavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Персистентный таймер "раз в N секунд" в состоянии группы (переживает рестарт). */
public final class BehaviorTimer {

    private static final String KEY = "next";

    private BehaviorTimer() {
    }

    /**
     * @return true, когда пора сработать; следующий срок назначается автоматически.
     * firstDelaySec < 0 -> первая задержка = periodSec.
     * stackPeriod: период делится на число копий, но не меньше minPeriodSec.
     */
    public static boolean due(ServerPlayer player, BehaviorGroup group, double periodSec,
                              double firstDelaySec, boolean stackPeriod, double minPeriodSec) {
        long now = player.level().getGameTime();
        CompoundTag st = group.state();

        if (!st.contains(KEY)) {
            double first = firstDelaySec >= 0 ? firstDelaySec : periodSec;
            st.putLong(KEY, now + (long) (first * 20.0));
            return false;
        }
        if (now < st.getLong(KEY)) {
            return false;
        }
        double eff = effectivePeriod(group, periodSec, stackPeriod, minPeriodSec);
        st.putLong(KEY, now + (long) (eff * 20.0));
        return true;
    }

    public static double effectivePeriod(BehaviorGroup group, double periodSec, boolean stackPeriod, double minPeriodSec) {
        if (!stackPeriod) return periodSec;
        double divided = periodSec / Math.max(1, group.count());
        return Math.max(minPeriodSec, divided);
    }

    /** Сдвинуть таймер (для debug fire). */
    public static void reset(BehaviorGroup group) {
        group.state().remove(KEY);
    }
}

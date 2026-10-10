package com.dxrk.escalationmod.behavior.custom;

import com.dxrk.escalationmod.behavior.BehaviorGroup;
import com.dxrk.escalationmod.behavior.BehaviorHandler;
import com.dxrk.escalationmod.behavior.BehaviorJson;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

/**
 * id 0048 "Тотальная тревога": раз в несколько часов на 5 минут ВСЕ враждебные мобы в радиусе 64 блоков
 * нацеливаются на игрока. JSON: period_sec, duration_sec, radius, first_delay_sec.
 * Состояние: next (время след. запуска), until (конец текущей тревоги) — в game ticks, переживает рестарт.
 */
public class AlarmHandler implements BehaviorHandler {

    private final double periodSec;
    private final double durationSec;
    private final double radius;
    private final double firstDelaySec;

    public AlarmHandler(String poolId, JsonObject p) {
        this.periodSec = BehaviorJson.dbl(p, "period_sec", 10800.0);
        this.durationSec = BehaviorJson.dbl(p, "duration_sec", 300.0);
        this.radius = BehaviorJson.dbl(p, "radius", 64.0);
        this.firstDelaySec = BehaviorJson.dbl(p, "first_delay_sec", 3600.0);
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        long now = player.level().getGameTime();
        CompoundTag st = group.state();
        if (!st.contains("next")) {
            st.putLong("next", now + (long) (firstDelaySec * 20.0));
        }

        long until = st.getLong("until");
        if (now < until) {
            aggro(player);
            return;
        }
        if (until > 0) {
            st.putLong("until", 0L);
            player.displayClientMessage(Component.literal("§7Тревога улеглась."), true);
        }
        if (now >= st.getLong("next")) {
            start(player, group, now);
        }
    }

    private void start(ServerPlayer player, BehaviorGroup group, long now) {
        CompoundTag st = group.state();
        double eff = periodSec / Math.max(1, group.count());
        st.putLong("until", now + (long) (durationSec * group.averageMultiplier() * 20.0));
        st.putLong("next", now + (long) ((Math.max(eff, durationSec * 2) + durationSec) * 20.0));
        player.displayClientMessage(Component.literal("§c§l⚠ ТОТАЛЬНАЯ ТРЕВОГА: все мобы вокруг идут за вами!"), false);
    }

    private void aggro(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(radius);
        for (Monster m : player.level().getEntitiesOfClass(Monster.class, box)) {
            if (m.isAlive() && m.getTarget() != player) {
                m.setTarget(player);
            }
        }
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        start(player, group, player.level().getGameTime());
    }

    @Override
    public String describe() {
        return "тревога раз в " + periodSec + "с на " + durationSec + "с, радиус " + radius;
    }
}

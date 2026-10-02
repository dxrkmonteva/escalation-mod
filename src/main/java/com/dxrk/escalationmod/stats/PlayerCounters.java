package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Счётчики для усложнений "после N ...": своё (в persistent data игрока, переживает смерть)
 * и ванильная статистика (Stats), которую игра и так ведёт.
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public final class PlayerCounters {

    private static final String ROOT = "escalation_counters";

    private PlayerCounters() {
    }

    public static long get(Player player, String key) {
        return player.getPersistentData().getCompound(ROOT).getLong(key);
    }

    public static void add(Player player, String key, long delta) {
        CompoundTag data = player.getPersistentData();
        CompoundTag root = data.getCompound(ROOT);
        root.putLong(key, root.getLong(key) + delta);
        data.put(ROOT, root);
    }

    public static long custom(ServerPlayer player, ResourceLocation stat) {
        return player.getStats().getValue(Stats.CUSTOM.get(stat));
    }

    public static long itemUsed(ServerPlayer player, Item item) {
        return player.getStats().getValue(Stats.ITEM_USED.get(item));
    }

    public static long entityKilled(ServerPlayer player, EntityType<?> type) {
        return player.getStats().getValue(Stats.ENTITY_KILLED.get(type));
    }

    /** Тики с последнего сна (ванильная статистика TIME_SINCE_REST). */
    public static long timeSinceRest(ServerPlayer player) {
        return custom(player, Stats.TIME_SINCE_REST);
    }

    /** 5+ игровых суток без сна: общий дебафф "ко всем характеристикам" (id 0828). */
    public static double allStatsFactor(ServerPlayer player) {
        if (timeSinceRest(player) >= 5L * 24000L) {
            return StatEngine.factor(player, "all_stats_after_5_days_no_sleep");
        }
        return 1.0;
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(ROOT)) {
            event.getEntity().getPersistentData().put(ROOT, old.getCompound(ROOT).copy());
        }
    }
}

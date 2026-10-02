package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Пакет B (скорость): всё через transient-модификаторы атрибутов — сервер меняет атрибут,
 * ваниль сама синхронизирует его клиенту, поэтому предсказание движения не ломается.
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class MovementHooks {

    private static final UUID MOVE_ID = UUID.fromString("6f1c2a10-5b7e-4c1a-9a35-1d0e8b7a4c11");
    private static final UUID SWIM_ID = UUID.fromString("6f1c2a10-5b7e-4c1a-9a35-1d0e8b7a4c12");
    private static final UUID MAX_HEALTH_ID = UUID.fromString("6f1c2a10-5b7e-4c1a-9a35-1d0e8b7a4c13");
    private static final UUID MOUNT_ID = UUID.fromString("6f1c2a10-5b7e-4c1a-9a35-1d0e8b7a4c14");

    private static final long WAKEUP_WINDOW_TICKS = 30L * 20;
    private static final int INVENTORY_FULL_SLOTS = 33;          // 90% от 36
    private static final int INVENTORY_FULL_SECONDS = 5 * 60;
    private static final long CROUCH_TICKS_THRESHOLD = 5L * 3600 * 20;     // 5 часов
    private static final long SWIM_CM_THRESHOLD = 14_400_000L;             // ~20 ч плавания (оценка по дистанции)
    private static final long RIDE_CM_100H = 320_000_000L;                 // ~100 ч верхом (оценка по дистанции)
    private static final long RIDE_CM_MILEAGE = 50_000_000L;               // ~500 000 блоков (оценка)
    private static final int RIDE_STAMINA_SECONDS = 10 * 60;
    private static final double DEEP_Y = -30.0;

    private static final Map<UUID, Long> wakeTick = new HashMap<>();
    private static final Map<UUID, Integer> invFullSeconds = new HashMap<>();
    private static final Map<UUID, Integer> rideSeconds = new HashMap<>();

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            wakeTick.put(player.getUUID(), player.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        if (player.tickCount % 20 == 0) {
            countSeconds(player);
        }
        if (player.tickCount % 4 != 0) return;

        updateWalkSpeed(player);
        updateSwimSpeed(player);
        updateMaxHealth(player);
        updateMountSpeed(player);
    }

    private static void countSeconds(ServerPlayer player) {
        UUID id = player.getUUID();

        int filled = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty()) filled++;
        }
        if (filled >= INVENTORY_FULL_SLOTS) {
            invFullSeconds.merge(id, 1, Integer::sum);
        } else {
            invFullSeconds.remove(id);
        }

        if (player.getVehicle() instanceof LivingEntity) {
            rideSeconds.merge(id, 1, Integer::sum);
        } else {
            rideSeconds.remove(id);
        }
    }

    private static void updateWalkSpeed(ServerPlayer player) {
        UUID id = player.getUUID();
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        double f = 1.0;

        if (player.isInWater() && level.getBiome(pos).value().getBaseTemperature() <= 0.15f) {
            f *= StatEngine.factor(player, "movement_speed_cold_water");
        }

        Long wake = wakeTick.get(id);
        if (wake != null && level.getGameTime() - wake <= WAKEUP_WINDOW_TICKS) {
            f *= StatEngine.factor(player, "movement_speed_after_wakeup");
        }

        if (invFullSeconds.getOrDefault(id, 0) >= INVENTORY_FULL_SECONDS) {
            f *= StatEngine.factor(player, "inventory_near_full_movement_speed");
        }

        if (player.isCrouching() && PlayerCounters.custom(player, Stats.CROUCH_TIME) >= CROUCH_TICKS_THRESHOLD) {
            f *= StatEngine.factor(player, "crouch_speed_after_5h_crouching");
        }

        if (DefenseHooks.combatDurationTicks(player) >= 3L * 60 * 20) {
            f *= StatEngine.factor(player, "combat_fatigue_damage_speed");
        }

        f *= PlayerCounters.allStatsFactor(player);

        setModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVE_ID, "escalation_player_speed", f);
    }

    private static void updateSwimSpeed(ServerPlayer player) {
        double f = 1.0;
        if (player.isInWater() && PlayerCounters.custom(player, Stats.SWIM_ONE_CM) >= SWIM_CM_THRESHOLD) {
            f = StatEngine.factor(player, "swim_speed_after_20h_swimming");
        }
        setModifier(player.getAttribute(ForgeMod.SWIM_SPEED.get()), SWIM_ID, "escalation_swim_speed", f);
    }

    private static void updateMaxHealth(ServerPlayer player) {
        double f = 1.0;
        if (player.getY() < DEEP_Y) {
            f = StatEngine.factor(player, "max_health_deep_caves");
        }
        setModifier(player.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH_ID, "escalation_deep_health", f);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void updateMountSpeed(ServerPlayer player) {
        if (!(player.getVehicle() instanceof LivingEntity mount)) return;

        double f = 1.0;
        long rideCm = PlayerCounters.custom(player, Stats.HORSE_ONE_CM);
        if (rideCm >= RIDE_CM_100H) {
            f *= StatEngine.factor(player, "mount_max_speed_after_100h_ridden");
        }
        if (rideCm >= RIDE_CM_MILEAGE) {
            f *= StatEngine.factor(player, "horse_max_speed_cumulative_mileage");
        }
        if (rideSeconds.getOrDefault(player.getUUID(), 0) >= RIDE_STAMINA_SECONDS) {
            f *= StatEngine.factor(player, "horse_stamina_after_10min_ride");
        }
        setModifier(mount.getAttribute(Attributes.MOVEMENT_SPEED), MOUNT_ID, "escalation_mount_speed", f);
    }

    // слез с коня — снять с него наш модификатор
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isDismounting()) return;
        Entity mounted = event.getEntityBeingMounted();
        if (mounted instanceof LivingEntity mount) {
            AttributeInstance attr = mount.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(MOUNT_ID);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        wakeTick.remove(id);
        invFullSeconds.remove(id);
        rideSeconds.remove(id);
    }

    private static void setModifier(AttributeInstance attr, UUID id, String name, double factor) {
        if (attr == null) return;
        double amount = factor - 1.0;
        AttributeModifier current = attr.getModifier(id);
        if (Math.abs(amount) < 1.0E-9) {
            if (current != null) attr.removeModifier(id);
            return;
        }
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0E-9) return;
        if (current != null) attr.removeModifier(id);
        attr.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}

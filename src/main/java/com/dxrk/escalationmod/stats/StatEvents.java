package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хуки, через которые множители StatEngine реально влияют на игру.
 * Подключено: голод (+3 условных варианта), урон от падения, урон мобов (ночь / полнолуние),
 * урон при 1 сердце, здоровье зомби/скелетов, скорость монстров.
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class StatEvents {

    private static final UUID MOB_HEALTH_MODIFIER_ID = UUID.fromString("6f1c2a10-5b7e-4c1a-9a35-1d0e8b7a4c01");
    private static final UUID MOB_SPEED_MODIFIER_ID = UUID.fromString("6f1c2a10-5b7e-4c1a-9a35-1d0e8b7a4c02");

    private static final Map<UUID, Float> lastExhaustion = new HashMap<>();

    // ---------- раз в секунду: рост эскалации легендарок (раньше tickEscalation нигде не вызывался) ----------
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long currentTick = event.getServer().overworld().getGameTime();
        if (currentTick % 20 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
                boolean changed = false;
                for (EscalationInstance inst : data.getActiveInstances()) {
                    if (inst.escalationActive && inst.escalationElapsedSec < inst.escalationDurationSec) {
                        inst.tickEscalation();
                        changed = true;
                    }
                }
                if (changed) {
                    StatEngine.invalidate(player.getUUID());
                }
            });
        }
    }

    // ---------- голод: extra-истощение пропорционально приросту exhaustion за тик ----------
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        FoodData food = player.getFoodData();
        float current = food.getExhaustionLevel();
        Float prev = lastExhaustion.put(player.getUUID(), current);
        if (prev == null) return;

        // FoodData.tick снимает 4.0 exhaustion при достижении порога — учитываем один такой "перелом" за тик
        float added = current >= prev ? current - prev : current + 4.0f - prev;
        if (added <= 0.0f) return;

        double factor = hungerFactor(player);
        if (factor <= 1.0) return;

        food.addExhaustion((float) (added * (factor - 1.0)));
        lastExhaustion.put(player.getUUID(), food.getExhaustionLevel());
    }

    private static double hungerFactor(ServerPlayer player) {
        double f = StatEngine.factor(player, "hunger_depletion_rate");

        Level level = player.level();
        BlockPos pos = player.blockPosition();
        boolean sky = level.canSeeSky(pos);
        boolean underground = pos.getY() < 40;

        if (isNight(level) && sky) {
            f *= StatEngine.factor(player, "hunger_depletion_rate_night_outdoor");
        }
        if (underground) {
            f *= StatEngine.factor(player, "hunger_depletion_rate_underground");
            if (!sky && level.getBrightness(LightLayer.BLOCK, pos) < 4) {
                f *= StatEngine.factor(player, "hunger_thirst_depletion_underground_no_torch");
            }
        }
        return f;
    }

    // ---------- входящий урон игроку (до брони — LivingHurtEvent) ----------
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        DamageSource source = event.getSource();
        double mult = 1.0;

        if (source.is(DamageTypes.FALL)) {
            mult *= StatEngine.factor(player, "fall_damage");
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof Monster) {
            Level level = player.level();
            if (isNight(level)) {
                mult *= StatEngine.factor(player, "mob_night_damage_bonus");
                if (level.getMoonPhase() == 0) {
                    mult *= StatEngine.factor(player, "mob_damage_full_moon");
                }
            }
        }

        if (player.getHealth() <= 2.0f) {
            mult *= StatEngine.factor(player, "incoming_damage_low_health");
        }

        if (mult != 1.0) {
            event.setAmount((float) (event.getAmount() * mult));
        }
    }

    // ---------- монстры при появлении: множитель ближайшего игрока фиксируется на спавне ----------
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof Monster mob)) return;

        double radius = EscalationConfigManager.get().stacking.mobStatNearestPlayerRadius;
        Player nearest = event.getLevel().getNearestPlayer(mob, radius);
        if (!(nearest instanceof ServerPlayer player)) return;

        double speedFactor = StatEngine.factor(player, "mob_movement_speed");
        if (speedFactor != 1.0) {
            applyMultiplier(mob.getAttribute(Attributes.MOVEMENT_SPEED), MOB_SPEED_MODIFIER_ID,
                    "escalation_mob_speed", speedFactor);
        }

        if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
            double healthFactor = StatEngine.factor(player, "mob_health_zombie_skeleton");
            if (healthFactor != 1.0) {
                applyMultiplier(mob.getAttribute(Attributes.MAX_HEALTH), MOB_HEALTH_MODIFIER_ID,
                        "escalation_mob_health", healthFactor);
                mob.setHealth(mob.getMaxHealth());
            }
        }
    }

    private static void applyMultiplier(AttributeInstance attr, UUID modifierId, String name, double factor) {
        if (attr == null || attr.getModifier(modifierId) != null) return;
        attr.addPermanentModifier(new AttributeModifier(modifierId, name, factor - 1.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ---------- чистка состояния ----------
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        lastExhaustion.remove(id);
        StatEngine.forget(id);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        lastExhaustion.remove(event.getEntity().getUUID());
    }

    private static boolean isNight(Level level) {
        if (level.dimension() != Level.OVERWORLD) return false;
        long t = level.getDayTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }
}

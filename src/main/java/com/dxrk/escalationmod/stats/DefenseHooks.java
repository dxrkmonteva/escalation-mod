package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Пакет A: броня, лечение, усталость в бою, щит. */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class DefenseHooks {

    private static final long COMBAT_GAP_TICKS = 200L;                 // пауза > 10 с — бой закончился
    private static final long FATIGUE_SHORT_TICKS = 3L * 60 * 20;      // 3 минуты (id 0175)
    private static final long FATIGUE_LONG_TICKS = 30L * 60 * 20;      // 30 минут (id 0818)
    private static final long ARMOR_WORN_TICKS = 24000L;               // "сутки" (id 0293)
    private static final long DEBUFF_TICKS = 100L;                     // 5 секунд (id 0894)

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Map<UUID, Long> armorWornSince = new HashMap<>();
    private static final Map<UUID, long[]> lastElementalHit = new HashMap<>(); // {категория, тик}
    private static final Map<UUID, Long> defenseDebuffUntil = new HashMap<>();
    private static final Map<UUID, long[]> combat = new HashMap<>();           // {начало, последний тик}

    /** Длительность текущего непрерывного боя (0, если бой не идёт). */
    public static long combatDurationTicks(ServerPlayer player) {
        long[] c = combat.get(player.getUUID());
        if (c == null) return 0L;
        long now = player.level().getGameTime();
        if (now - c[1] > COMBAT_GAP_TICKS) return 0L;
        return c[1] - c[0];
    }

    private static void touchCombat(ServerPlayer player) {
        long now = player.level().getGameTime();
        long[] c = combat.get(player.getUUID());
        if (c == null || now - c[1] > COMBAT_GAP_TICKS) {
            combat.put(player.getUUID(), new long[]{now, now});
        } else {
            c[1] = now;
        }
    }

    // раз в секунду: "полный зачарованный сет без снятия"
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        boolean full = true;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack s = player.getItemBySlot(slot);
            if (s.isEmpty() || !s.isEnchanted()) {
                full = false;
                break;
            }
        }
        if (full) {
            armorWornSince.putIfAbsent(player.getUUID(), player.level().getGameTime());
        } else {
            armorWornSince.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();

        // игрок наносит урон: усталость в бою + общий дебафф без сна
        if (source.getEntity() instanceof ServerPlayer attacker && event.getEntity() != attacker) {
            touchCombat(attacker);
            long duration = combatDurationTicks(attacker);
            double f = 1.0;
            if (duration >= FATIGUE_SHORT_TICKS) {
                f *= StatEngine.factor(attacker, "combat_fatigue_damage_speed");
            }
            if (duration >= FATIGUE_LONG_TICKS) {
                f *= StatEngine.factor(attacker, "combat_fatigue_cumulative_damage");
            }
            f *= PlayerCounters.allStatsFactor(attacker);
            if (f != 1.0) {
                event.setAmount((float) (event.getAmount() * f));
            }
        }

        // игрок получает урон: ослабление брони
        if (event.getEntity() instanceof ServerPlayer player) {
            touchCombat(player);
            trackElementalSwitch(player, source);
            applyArmorWeakening(event, player, source);
            if (player.getArmorValue() > 0) {
                PlayerCounters.add(player, "armor_hits", 1L);
            }
        }
    }

    private static void trackElementalSwitch(ServerPlayer player, DamageSource source) {
        int category = source.is(DamageTypeTags.IS_FIRE) ? 1 : source.is(DamageTypeTags.IS_FREEZING) ? 2 : 0;
        if (category == 0) return;
        long now = player.level().getGameTime();
        long[] last = lastElementalHit.get(player.getUUID());
        if (last != null && last[0] != category && now - last[1] <= DEBUFF_TICKS) {
            defenseDebuffUntil.put(player.getUUID(), now + DEBUFF_TICKS);
        }
        lastElementalHit.put(player.getUUID(), new long[]{category, now});
    }

    /**
     * Броня в ваниле применяется ПОСЛЕ LivingHurtEvent, поэтому "слабее броня" делаем так:
     * считаем, сколько урона броня погасила бы, и подбираем (бисекцией) входной урон так,
     * чтобы после ванильного поглощения прошло на (1 - factor) больше.
     */
    private static void applyArmorWeakening(LivingHurtEvent event, ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return;

        long now = player.level().getGameTime();
        double f = StatEngine.factor(player, "armor_effectiveness");

        Long since = armorWornSince.get(player.getUUID());
        if (since != null && now - since >= ARMOR_WORN_TICKS) {
            f *= StatEngine.factor(player, "armor_effectiveness_worn_without_removing");
        }
        if (PlayerCounters.get(player, "armor_hits") >= 1000L) {
            f *= StatEngine.factor(player, "armor_max_defense_after_1000_hits");
        }
        Long until = defenseDebuffUntil.get(player.getUUID());
        if (until != null && now <= until) {
            f *= StatEngine.factor(player, "defense_debuff_after_damage_type_switch");
        }
        if (f >= 1.0) return;

        float amount = event.getAmount();
        float armor = (float) player.getArmorValue();
        float toughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (armor <= 0.0f || amount <= 0.0f) return;

        float after = CombatRules.getDamageAfterAbsorb(amount, armor, toughness);
        float desired = amount - (amount - after) * (float) f;

        float lo = amount;
        float hi = Math.max(amount, desired) * 6.0f;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) * 0.5f;
            if (CombatRules.getDamageAfterAbsorb(mid, armor, toughness) < desired) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        event.setAmount(hi);
    }

    // лечение слабее (id 0005). Затрагивает всё лечение игрока, не только от сытости.
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        double f = StatEngine.factor(player, "saturation_regen_rate");
        if (f < 1.0) {
            event.setAmount((float) (event.getAmount() * f));
        }
    }

    // щит (id 0524 и 0810)
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        double f = 1.0;
        Entity attacker = event.getDamageSource().getEntity();
        if (attacker instanceof Creeper creeper && creeper.isPowered()) {
            f *= StatEngine.factor(player, "shield_block_effectiveness_vs_charged_creeper");
        }
        PlayerCounters.add(player, "shield_blocks", 1L);
        if (PlayerCounters.get(player, "shield_blocks") >= 500L) {
            f *= StatEngine.factor(player, "shield_effectiveness_after_500_blocks");
        }
        if (f < 1.0) {
            event.setBlockedDamage((float) (event.getBlockedDamage() * f));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        armorWornSince.remove(id);
        lastElementalHit.remove(id);
        defenseDebuffUntil.remove(id);
        combat.remove(id);
    }
}

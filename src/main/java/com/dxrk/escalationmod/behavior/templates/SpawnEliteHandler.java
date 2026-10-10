package com.dxrk.escalationmod.behavior.templates;

import com.dxrk.escalationmod.behavior.*;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/**
 * Периодически спавнит усиленного ("элитного") моба рядом с игроком, который идёт к нему.
 *
 * JSON: entity, period_sec, first_delay_sec, stack_period, min_period_sec, cap (макс. живых таких мобов),
 *       health_mult, damage_mult, speed_mult (база; усиливается редкостью: 1 + (mult-1)*средний_множитель),
 *       armor: none|leather|iron|golden|diamond|copy_player, weapon (id предмета), glowing, name, message,
 *       follow_range, fire_resist, despawn_on_deactivate, conditions
 */
public class SpawnEliteHandler implements BehaviorHandler {

    private static final UUID HEALTH_ID = UUID.fromString("5e1f7a10-0c4d-4b3a-9a11-a5ca1a710001");
    private static final UUID DAMAGE_ID = UUID.fromString("5e1f7a10-0c4d-4b3a-9a11-a5ca1a710002");
    private static final UUID SPEED_ID = UUID.fromString("5e1f7a10-0c4d-4b3a-9a11-a5ca1a710003");

    private final String poolId;
    private final EntityType<?> type;
    private final double periodSec, firstDelaySec, minPeriodSec;
    private final boolean stackPeriod;
    private final int cap;
    private final double healthMult, damageMult, speedMult;
    private final String armor;
    private final String weapon;
    private final boolean glowing;
    private final String name;
    private final String message;
    private final double followRange;
    private final boolean fireResist;
    private final boolean despawnOnDeactivate;
    private final List<String> conditions;
    private final String tag;

    public SpawnEliteHandler(String poolId, JsonObject p) {
        this.poolId = poolId;
        this.tag = "escalation_elite_" + poolId;
        this.type = BehaviorJson.entityType(BehaviorJson.str(p, "entity", "minecraft:zombie"));
        if (type == null) throw new IllegalArgumentException("неизвестный entity");
        this.periodSec = BehaviorJson.dbl(p, "period_sec", 3600.0);
        this.firstDelaySec = BehaviorJson.dbl(p, "first_delay_sec", -1.0);
        this.minPeriodSec = BehaviorJson.dbl(p, "min_period_sec", 600.0);
        this.stackPeriod = BehaviorJson.bool(p, "stack_period", true);
        this.cap = Math.max(1, BehaviorJson.integer(p, "cap", 3));
        this.healthMult = BehaviorJson.dbl(p, "health_mult", 2.0);
        this.damageMult = BehaviorJson.dbl(p, "damage_mult", 1.5);
        this.speedMult = BehaviorJson.dbl(p, "speed_mult", 1.0);
        this.armor = BehaviorJson.str(p, "armor", "none");
        this.weapon = BehaviorJson.str(p, "weapon", "");
        this.glowing = BehaviorJson.bool(p, "glowing", false);
        this.name = BehaviorJson.str(p, "name", "");
        this.message = BehaviorJson.str(p, "message", "");
        this.followRange = BehaviorJson.dbl(p, "follow_range", 64.0);
        this.fireResist = BehaviorJson.bool(p, "fire_resist", true);
        this.despawnOnDeactivate = BehaviorJson.bool(p, "despawn_on_deactivate", true);
        this.conditions = BehaviorJson.strings(p.get("conditions"));
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        if (!BehaviorTimer.due(player, group, periodSec, firstDelaySec, stackPeriod, minPeriodSec)) return;
        if (!BehaviorConditions.all(player, conditions)) return;
        spawn(player, group);
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        spawn(player, group);
    }

    @Override
    public void onDeactivated(ServerPlayer player, String id) {
        if (!despawnOnDeactivate || !(player.level() instanceof ServerLevel level)) return;
        AABB box = player.getBoundingBox().inflate(192.0);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.getTags().contains(tag))) {
            m.discard();
        }
    }

    private void spawn(ServerPlayer player, BehaviorGroup group) {
        if (!(player.level() instanceof ServerLevel level)) return;

        AABB box = player.getBoundingBox().inflate(160.0);
        if (level.getEntitiesOfClass(Mob.class, box, e -> e.getTags().contains(tag)).size() >= cap) return;

        BlockPos spot = findSpot(level, player.blockPosition());
        if (spot == null) return;

        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) return;

        double avg = group.averageMultiplier();
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, BehaviorRng.RNG.nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
        mob.ejectPassengers();
        if (mob instanceof Zombie z) z.setBaby(false);
        mob.addTag(tag);
        mob.setPersistenceRequired();

        scale(mob, Attributes.MAX_HEALTH, HEALTH_ID, "escalation_elite_health", 1.0 + (healthMult - 1.0) * avg);
        scale(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "escalation_elite_damage", 1.0 + (damageMult - 1.0) * avg);
        scale(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, "escalation_elite_speed", 1.0 + (speedMult - 1.0) * avg);
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(followRange);
        mob.setHealth(mob.getMaxHealth());

        equip(mob, player);
        if (glowing) mob.setGlowingTag(true);
        if (fireResist) mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 60 * 10, 0, false, false));
        if (!name.isEmpty()) {
            mob.setCustomName(Component.literal(name));
            mob.setCustomNameVisible(true);
        }
        mob.setTarget(player);

        level.addFreshEntity(mob);
        mob.setHealth(mob.getMaxHealth());

        if (!message.isEmpty()) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    private static void scale(Mob mob, Attribute attr, UUID id, String modName, double factor) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null || factor == 1.0) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, modName, factor - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private void equip(Mob mob, ServerPlayer player) {
        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        if (armor.equals("copy_player")) {
            for (EquipmentSlot s : armorSlots) {
                mob.setItemSlot(s, player.getItemBySlot(s).copy());
                mob.setDropChance(s, 0.0f);
            }
            mob.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copy());
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            mob.setItemSlot(EquipmentSlot.OFFHAND, player.getOffhandItem().copy());
            mob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
            return;
        }

        Item[] set = switch (armor) {
            case "leather" -> new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
            case "iron" -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
            case "golden" -> new Item[]{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
            case "diamond" -> new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
            default -> null;
        };
        if (set != null) {
            for (int i = 0; i < armorSlots.length; i++) {
                mob.setItemSlot(armorSlots[i], new ItemStack(set[i]));
                mob.setDropChance(armorSlots[i], 0.0f);
            }
        }
        if (!weapon.isEmpty()) {
            var rl = BehaviorJson.rl(weapon);
            if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ForgeRegistries.ITEMS.getValue(rl)));
                mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
        }
    }

    /** Безопасная точка на расстоянии 14-28 блоков: воздух, воздух, твёрдый пол. null — не нашли. */
    private static BlockPos findSpot(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = BehaviorRng.RNG.nextDouble() * Math.PI * 2.0;
            int dist = 14 + BehaviorRng.RNG.nextInt(15);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = 6; dy >= -8; dy--) {
                BlockPos pos = new BlockPos(x, center.getY() + dy, z);
                if (!level.isLoaded(pos)) break;
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                    return pos;
                }
            }
        }
        return null;
    }

    @Override
    public String describe() {
        return "спавн " + ForgeRegistries.ENTITY_TYPES.getKey(type) + " раз в " + periodSec + "с (cap " + cap + "), ×hp "
                + healthMult + ", ×dmg " + damageMult + ", броня " + armor;
    }
}

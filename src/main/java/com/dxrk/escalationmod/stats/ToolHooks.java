package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.network.ClientModifiersPacket;
import com.dxrk.escalationmod.network.EscalationNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Пакеты B и C: добыча, лук, дроп, ночное зрение, прочность, синхронизация клиенту. */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class ToolHooks {

    private static final SecureRandom RNG = new SecureRandom();

    private static final EquipmentSlot[] TRACKED = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final class Tracked {
        ItemStack ref;
        int damage;
        double carry;
    }

    private static final Map<UUID, Tracked[]> durability = new HashMap<>();
    private static final Map<UUID, Integer> lastNightVision = new HashMap<>();

    // ---------- скорость добычи (серверная сторона) ----------
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ToolFactors f = ToolFactors.compute(player);
        event.setNewSpeed(f.applyBreakSpeed(player, event.getState(), event.getNewSpeed()));
    }

    // ---------- синхронизация клиенту раз в секунду ----------
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long tick = event.getServer().overworld().getGameTime();
        if (tick % 20 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            EscalationNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ClientModifiersPacket(ToolFactors.compute(player)));
        }
    }

    // ---------- прочность, ночное зрение ----------
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        trackDurability(player);
        if (player.tickCount % 20 == 0) {
            adjustNightVision(player);
        }
    }

    /**
     * У ванили нет события "предмет получил износ", поэтому смотрим на прирост damage каждый тик
     * и добавляем лишний износ пропорционально множителю (дробный остаток копим).
     */
    private static void trackDurability(ServerPlayer player) {
        double toolFactor = StatEngine.factor(player, "tool_durability_loss_rate");
        double armorFactor = StatEngine.factor(player, "armor_durability_loss_rate");
        if (toolFactor <= 1.0 && armorFactor <= 1.0) {
            durability.remove(player.getUUID());
            return;
        }

        Tracked[] arr = durability.computeIfAbsent(player.getUUID(), k -> {
            Tracked[] t = new Tracked[TRACKED.length];
            for (int i = 0; i < t.length; i++) t[i] = new Tracked();
            return t;
        });

        for (int i = 0; i < TRACKED.length; i++) {
            final EquipmentSlot slot = TRACKED[i];
            boolean isArmor = slot.getType() == EquipmentSlot.Type.ARMOR;
            double factor = isArmor ? armorFactor : toolFactor;
            Tracked t = arr[i];

            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                t.ref = null;
                t.carry = 0.0;
                continue;
            }
            if (t.ref != stack) { // другой предмет в слоте — начинаем отсчёт заново
                t.ref = stack;
                t.damage = stack.getDamageValue();
                t.carry = 0.0;
                continue;
            }

            int delta = stack.getDamageValue() - t.damage;
            if (delta > 0 && factor > 1.0) {
                double extra = delta * (factor - 1.0) + t.carry;
                int whole = (int) Math.min(extra, 1_000_000.0);
                t.carry = extra - whole;
                if (whole > 0) {
                    stack.hurtAndBreak(whole, player, p -> p.broadcastBreakEvent(slot));
                }
            }

            ItemStack after = player.getItemBySlot(slot);
            if (after.isEmpty()) {
                t.ref = null;
                t.carry = 0.0;
            } else {
                t.damage = after.getDamageValue();
            }
        }
    }

    /** Новое наложение ночного зрения (длительность выросла) заменяем укороченным (id 0004). */
    private static void adjustNightVision(ServerPlayer player) {
        MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
        UUID id = player.getUUID();
        if (nv == null || nv.getDuration() < 0) {
            lastNightVision.remove(id);
            return;
        }

        int duration = nv.getDuration();
        Integer last = lastNightVision.get(id);
        boolean fresh = last == null || duration > last;
        double f = StatEngine.factor(player, "night_vision_duration");

        if (fresh && f < 1.0) {
            int shortened = Math.max(1, (int) (duration * f));
            player.removeEffect(MobEffects.NIGHT_VISION);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, shortened, nv.getAmplifier(),
                    nv.isAmbient(), nv.isVisible(), nv.showIcon()));
            duration = shortened;
        }
        lastNightVision.put(id, duration);
    }

    // ---------- лук: разброс после 5000 использований (id 0804) ----------
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer player)) return;
        if (PlayerCounters.itemUsed(player, Items.BOW) < 5000L) return;

        double f = StatEngine.factor(player, "bow_accuracy_after_5000_shots");
        if (f >= 1.0) return;

        Vec3 v = arrow.getDeltaMovement();
        double deviation = (1.0 - f) * 0.15 * v.length();
        arrow.setDeltaMovement(v.add(
                RNG.nextGaussian() * deviation, RNG.nextGaussian() * deviation, RNG.nextGaussian() * deviation));
    }

    // ---------- дроп с мобов после 5000 убийств того же вида (id 0821) ----------
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (PlayerCounters.entityKilled(player, event.getEntity().getType()) < 5000L) return;

        double f = StatEngine.factor(player, "rare_drop_chance_after_5000_kills");
        if (f >= 1.0) return;

        double loss = 1.0 - f;
        event.getDrops().removeIf(drop -> RNG.nextDouble() < loss);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        durability.remove(id);
        lastNightVision.remove(id);
    }
}

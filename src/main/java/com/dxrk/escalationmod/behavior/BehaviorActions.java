package com.dxrk.escalationmod.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Действия из JSON: {"type": "...", ...}. Любое действие может содержать "message" (action bar)
 * и "sound" (id звука, "volume", "pitch") — они выполняются после основного действия.
 *
 * Типы: effect, damage, drop_item, item_damage, break_equipped, destroy_item,
 *       xp_levels, cancel, cancel_sleep, rain, message, sound
 */
public final class BehaviorActions {

    private static int executingDepth = 0;

    private BehaviorActions() {
    }

    /** true, пока выполняется действие — защита от рекурсии (урон от действия снова вызывает триггер). */
    public static boolean isExecuting() {
        return executingDepth > 0;
    }

    public static void run(JsonArray actions, BehaviorContext ctx) {
        executingDepth++;
        try {
            for (JsonElement el : actions) {
                if (el.isJsonObject()) {
                    runOne(el.getAsJsonObject(), ctx);
                }
            }
        } finally {
            executingDepth--;
        }
    }

    private static void runOne(JsonObject a, BehaviorContext ctx) {
        ServerPlayer player = ctx.player;
        double mag = ctx.group.magnitude();
        String type = BehaviorJson.str(a, "type", "");

        switch (type) {
            case "effect" -> {
                MobEffect effect = BehaviorJson.effect(BehaviorJson.str(a, "effect", ""));
                if (effect == null) {
                    BehaviorRegistry.warnOnce("Неизвестный эффект '" + BehaviorJson.str(a, "effect", "") + "' (" + ctx.group.poolId + ")");
                    break;
                }
                double seconds = BehaviorJson.dbl(a, "seconds", 5.0) * mag;
                int ticks = (int) Math.max(1, Math.min(20.0 * 3600.0, seconds * 20.0));
                int amp = BehaviorJson.integer(a, "amplifier", 0);
                player.addEffect(new MobEffectInstance(effect, ticks, amp));
            }
            case "damage" -> {
                float amount = (float) (BehaviorJson.dbl(a, "amount", 2.0) * mag);
                player.hurt(player.damageSources().generic(), amount);
            }
            case "drop_item" -> dropRandomItem(player);
            case "item_damage" -> {
                ItemStack target = !ctx.stack.isEmpty() ? ctx.stack : player.getMainHandItem();
                if (target.isDamageableItem()) {
                    int amt = (int) Math.max(1, Math.round(BehaviorJson.dbl(a, "amount", 1.0) * mag));
                    target.setDamageValue(Math.min(target.getMaxDamage() - 1, target.getDamageValue() + amt));
                }
            }
            case "break_equipped" -> breakRandomEquipped(player);
            case "destroy_item" -> {
                if (ctx.drop != null) {
                    ctx.removeDrop = true;
                }
            }
            case "xp_levels" -> player.giveExperienceLevels(BehaviorJson.integer(a, "amount", -1));
            case "cancel" -> ctx.cancel = true;
            case "cancel_sleep" -> ctx.cancelSleep = true;
            case "rain" -> {
                if (player.level() instanceof ServerLevel level && !level.isRaining()) {
                    int ticks = (int) (BehaviorJson.dbl(a, "duration_sec", 600.0) * 20.0);
                    level.setWeatherParameters(0, ticks, true, false);
                }
            }
            case "message", "sound" -> {
                // выполняются общей частью ниже
            }
            default -> BehaviorRegistry.warnOnce("Неизвестный тип действия '" + type + "' (" + ctx.group.poolId + ")");
        }

        String msg = BehaviorJson.str(a, "message", null);
        if (msg != null) {
            player.displayClientMessage(Component.literal(msg), true);
        }
        String snd = BehaviorJson.str(a, "sound", null);
        if (snd != null) {
            playSound(player, snd, (float) BehaviorJson.dbl(a, "volume", 1.0), (float) BehaviorJson.dbl(a, "pitch", 1.0));
        }
    }

    public static void playSound(ServerPlayer player, String id, float volume, float pitch) {
        SoundEvent ev = BehaviorJson.sound(id);
        if (ev == null) {
            BehaviorRegistry.warnOnce("Неизвестный звук '" + id + "'");
            return;
        }
        player.playNotifySound(ev, SoundSource.PLAYERS, volume, pitch);
    }

    private static void dropRandomItem(ServerPlayer player) {
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty()) candidates.add(s);
        }
        if (candidates.isEmpty()) return;
        ItemStack chosen = candidates.get(BehaviorRng.RNG.nextInt(candidates.size()));
        ItemStack one = chosen.split(1);
        player.drop(one, true);
    }

    private static void breakRandomEquipped(ServerPlayer player) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        List<EquipmentSlot> filled = new ArrayList<>();
        for (EquipmentSlot s : slots) {
            if (!player.getItemBySlot(s).isEmpty()) filled.add(s);
        }
        if (filled.isEmpty()) return;
        EquipmentSlot chosen = filled.get(BehaviorRng.RNG.nextInt(filled.size()));
        player.setItemSlot(chosen, ItemStack.EMPTY);
        playSound(player, "minecraft:entity.item.break", 1.0f, 0.8f);
    }
}

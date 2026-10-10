package com.dxrk.escalationmod.behavior.custom;

import com.dxrk.escalationmod.behavior.BehaviorGroup;
import com.dxrk.escalationmod.behavior.BehaviorHandler;
import com.dxrk.escalationmod.behavior.BehaviorJson;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * id 0178 "Тревога одиночества": час реального времени без единого живого существа рядом -> слабый дебафф.
 * JSON: alone_seconds (3600), radius (32), effect (minecraft:weakness), amplifier (0).
 */
public class LonelinessHandler implements BehaviorHandler {

    private final int aloneSeconds;
    private final double radius;
    private final MobEffect effect;
    private final int amplifier;

    public LonelinessHandler(String poolId, JsonObject p) {
        this.aloneSeconds = BehaviorJson.integer(p, "alone_seconds", 3600);
        this.radius = BehaviorJson.dbl(p, "radius", 32.0);
        this.effect = BehaviorJson.effect(BehaviorJson.str(p, "effect", "minecraft:weakness"));
        this.amplifier = BehaviorJson.integer(p, "amplifier", 0);
        if (effect == null) throw new IllegalArgumentException("неизвестный effect");
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        CompoundTag st = group.state();
        boolean company = !player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive()).isEmpty();
        int alone = company ? 0 : st.getInt("alone") + 1;
        st.putInt("alone", Math.min(alone, 1_000_000));

        if (alone >= aloneSeconds) {
            if (alone == aloneSeconds) {
                player.displayClientMessage(Component.literal("§7Слишком тихо. Слишком давно вы никого не видели..."), true);
            }
            player.addEffect(new MobEffectInstance(effect, 100, amplifier, false, false));
        }
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        player.addEffect(new MobEffectInstance(effect, 200, amplifier));
    }

    @Override
    public String describe() {
        return "дебафф после " + aloneSeconds + "с без живых существ в радиусе " + radius;
    }
}

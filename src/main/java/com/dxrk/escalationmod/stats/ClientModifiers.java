package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Клиентская сторона: применяет множители, присланные сервером (скорость добычи и прыжок). */
@Mod.EventBusSubscriber(modid = Escalation.MODID, value = Dist.CLIENT)
public final class ClientModifiers {

    private static volatile ToolFactors current = ToolFactors.NEUTRAL;

    private ClientModifiers() {
    }

    public static void update(ToolFactors factors) {
        current = factors;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player local = Minecraft.getInstance().player;
        if (local == null || event.getEntity() != local) return;
        event.setNewSpeed(current.applyBreakSpeed(local, event.getState(), event.getNewSpeed()));
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        Player local = Minecraft.getInstance().player;
        if (local == null || event.getEntity() != local) return;
        float f = current.jump();
        if (f < 1f) {
            Vec3 v = local.getDeltaMovement();
            local.setDeltaMovement(v.x, v.y * f, v.z);
        }
    }
}

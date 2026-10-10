package com.dxrk.escalationmod.behavior.client;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.behavior.ClientBehaviorState;
import com.dxrk.escalationmod.behavior.custom.SlipperyBlocksHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентская часть id 0003: часть обычных блоков (детерминированно по координатам) скользит как лёд.
 * Эмуляция: после ванильного трения возвращаем скорость к трению льда (0.98 / трение блока), с ограничением скорости.
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID, value = Dist.CLIENT)
public final class SlipperyClient {

    private static final double MAX_SPEED = 0.45;

    private SlipperyClient() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient()) return;
        LocalPlayer local = Minecraft.getInstance().player;
        if (local == null || event.player != local) return;

        float chance = ClientBehaviorState.get(SlipperyBlocksHandler.CLIENT_KEY);
        if (chance <= 0f || !local.onGround() || local.isInWater() || local.isPassenger()) return;

        BlockPos below = local.blockPosition().below();
        if (!isSlippery(below, chance)) return;

        BlockState state = local.level().getBlockState(below);
        float friction = state.getBlock().getFriction();
        if (friction >= 0.9f || friction <= 0f) return;

        double ratio = 0.98 / friction;
        Vec3 v = local.getDeltaMovement();
        double nx = v.x * ratio;
        double nz = v.z * ratio;
        double speed = Math.sqrt(nx * nx + nz * nz);
        if (speed > MAX_SPEED) {
            nx *= MAX_SPEED / speed;
            nz *= MAX_SPEED / speed;
        }
        local.setDeltaMovement(nx, v.y, nz);
    }

    private static boolean isSlippery(BlockPos pos, float chance) {
        int h = pos.getX() * 73856093 ^ pos.getY() * 19349663 ^ pos.getZ() * 83492791;
        h = (h ^ (h >>> 16)) & 0x7fffffff;
        return (h % 10000) < chance * 10000f;
    }
}

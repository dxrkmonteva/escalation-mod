package com.dxrk.escalationmod.mercy;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class MercyRuleEvents {

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            data.incrementDeathCounter();

            if (data.getDeathCounter() % 5 == 0) {
                long currentTick = player.level().getGameTime();
                MercyRuleManager.onDeathBlockOfFive(player, data, currentTick);
            }
        });
    }
}

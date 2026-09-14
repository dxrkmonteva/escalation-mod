package com.dxrk.escalationmod.data;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class PlayerDataEvents {

    private static final ResourceLocation PLAYER_DATA_ID =
            new ResourceLocation(Escalation.MODID, "player_data");

    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PLAYER_DATA_ID, new PlayerDataProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(oldData ->
                event.getEntity().getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(newData ->
                        newData.deserializeNBT(oldData.serializeNBT())));
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide) {
            return;
        }
        event.player.getCapability(EscalationCapabilities.PLAYER_DATA)
                .ifPresent(data -> data.addRealPlaytimeTicks(1));
    }
}

package com.dxrk.escalationmod.dimension;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class DimensionLockHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final long NETHER_REQUIRED_TICKS = 20L * 60 * 60 * 20;
    private static final long MESSAGE_COOLDOWN_TICKS = 60L;

    private static final Map<UUID, Long> lastRefusalTick = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        EscalationWorldData worldData = EscalationWorldData.get(event.getServer().overworld());
        LOGGER.info("Escalation: порог открытия End для этого мира — {} реальных часов (сгенерирован один раз при создании мира).",
                String.format("%.2f", worldData.getEndRealHoursThreshold()));
        LOGGER.info("Escalation: порог открытия Nether — фиксированные 20 реальных часов (одинаково для всех миров).");
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        long requiredTicks;
        String dimensionLabel;

        if (event.getDimension() == Level.NETHER) {
            requiredTicks = NETHER_REQUIRED_TICKS;
            dimensionLabel = "Nether";
        } else if (event.getDimension() == Level.END) {
            ServerLevel overworld = serverLevel.getServer().overworld();
            EscalationWorldData worldData = EscalationWorldData.get(overworld);
            requiredTicks = (long) (worldData.getEndRealHoursThreshold() * 60.0 * 60.0 * 20.0);
            dimensionLabel = "End";
        } else {
            return;
        }

        long playerTicks = player.getCapability(EscalationCapabilities.PLAYER_DATA)
                .map(IEscalationPlayerData::getRealPlaytimeTicks)
                .orElse(0L);

        if (playerTicks >= requiredTicks) {
            return;
        }

        event.setCanceled(true);
        player.teleportTo(player.getX(), player.getY() + 1.0, player.getZ());

        long currentTick = serverLevel.getGameTime();
        UUID uuid = player.getUUID();
        Long last = lastRefusalTick.get(uuid);
        if (last == null || currentTick - last > MESSAGE_COOLDOWN_TICKS) {
            lastRefusalTick.put(uuid, currentTick);

            player.sendSystemMessage(Component.literal(
                    "§c🔒 " + dimensionLabel + " пока недоступен — нужно больше реального времени в игре."));

            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);

            LOGGER.info("Escalation: игрок {} попытался попасть в {} раньше времени ({} / {} тиков реального времени)",
                    player.getName().getString(), dimensionLabel, playerTicks, requiredTicks);
        }
    }
}

package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.network.EscalationNetwork;
import com.dxrk.escalationmod.network.ShowToastPacket;
import com.dxrk.escalationmod.pool.EscalationDefinition;
import com.dxrk.escalationmod.pool.PoolRegistry;
import com.dxrk.escalationmod.win.WinConditionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class EscalationScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SecureRandom RNG = new SecureRandom();

    private static EscalationConfig cfg() {
        return EscalationConfigManager.get();
    }

    private static TierSelector.Order tierOrder() {
        try {
            return TierSelector.Order.valueOf(cfg().tierProgression.order.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TierSelector.Order.ASCENDING;
        }
    }

    private static int selectTierFor(IEscalationPlayerData data) {
        double elapsedHours = data.getRealPlaytimeTicks() / 20.0 / 3600.0;
        EscalationConfig.TierProgressionSection tp = cfg().tierProgression;
        return TierSelector.selectTier(elapsedHours, tp.totalTargetHours, tierOrder(), tp.jitter);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            if (data.getNextSpawnAtTick() <= 0) {
                long delay = randomIntervalTicks();
                data.setNextSpawnAtTick(player.level().getGameTime() + delay);
                LOGGER.info("Escalation: первый спавн для {} назначен через {} тиков", player.getName().getString(), delay);
            }
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long currentTick = event.getServer().overworld().getGameTime();
        if (currentTick % 20 != 0) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
                if (currentTick < data.getNextSpawnAtTick()) {
                    return;
                }
                if (data.hasWon() && !data.isEndlessMode() && cfg().winCondition.stopNewSpawnsAfterWin) {
                    return;
                }
                if (currentTick < data.getMercyPauseEndTick()) {
                    return;
                }
                trySpawn(player, data, currentTick);
                data.setNextSpawnAtTick(currentTick + randomIntervalTicks());
            });
        }
    }

    private static void trySpawn(ServerPlayer player, IEscalationPlayerData data, long currentTick) {
        int tier = selectTierFor(data);

        EscalationDefinition definition = pickDefinition(tier);
        if (definition == null) {
            LOGGER.warn("Escalation: для {} тир {} пуст в пуле — это баг данных", player.getName().getString(), tier);
            return;
        }

        Rarity rarity = RarityRoller.roll(tier);
        applyAndNotify(player, data, tier, rarity, definition, currentTick);
    }

    private static void applyAndNotify(ServerPlayer player, IEscalationPlayerData data,
                                        int tier, Rarity rarity, EscalationDefinition definition, long currentTick) {
        EscalationInstance instance = new EscalationInstance(definition, rarity, currentTick);

        data.getSeenPoolIds().add(definition.id);
        data.getActiveInstances().add(instance);
        if (rarity == Rarity.LEGENDARY) {
            data.incrementLegendaryCountEver();
        }

        player.sendSystemMessage(Component.literal(
                "§e⚠ НОВОЕ УСЛОЖНЕНИЕ [Тир " + tier + " · " + rarity.name() + "] " + definition.name
                        + " — " + definition.description));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);

        EscalationNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ShowToastPacket(tier, rarity.name(), definition.name, definition.description));

        LOGGER.info("Escalation: игроку {} выпало '{}' (id={}, тир={}, редкость={}), легендарок всего: {}",
                player.getName().getString(), definition.name, definition.id, tier, rarity,
                data.getLegendaryCountEver());

        if (rarity == Rarity.LEGENDARY) {
            WinConditionManager.checkWinCondition(player, data);
        }
    }

    public static void debugForceSpawn(ServerPlayer player) {
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            long currentTick = player.level().getGameTime();
            trySpawn(player, data, currentTick);
        });
    }

    public static void debugForceLegendaryToast(ServerPlayer player) {
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            long currentTick = player.level().getGameTime();
            int tier = selectTierFor(data);
            EscalationDefinition definition = pickDefinition(tier);
            if (definition == null) {
                LOGGER.warn("Escalation debug: тир {} пуст в пуле", tier);
                return;
            }
            applyAndNotify(player, data, tier, Rarity.LEGENDARY, definition, currentTick);
        });
    }

    /** Выдать конкретное усложнение с заданной редкостью (debug). @return false если id нет в пуле. */
    public static boolean debugGive(ServerPlayer player, String poolId, Rarity rarity) {
        EscalationDefinition definition = PoolRegistry.getById(poolId);
        if (definition == null) {
            return false;
        }
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data ->
                applyAndNotify(player, data, definition.tier, rarity, definition, player.level().getGameTime()));
        return true;
    }

    /** Назначает следующий спавн через случайный интервал от текущего момента (нужно при включении endless). */
    public static void scheduleNextSpawn(ServerPlayer player, IEscalationPlayerData data) {
        data.setNextSpawnAtTick(player.level().getGameTime() + randomIntervalTicks());
    }

    private static EscalationDefinition pickDefinition(int tier) {
        List<EscalationDefinition> tierPool = PoolRegistry.getByTier(tier);
        if (tierPool.isEmpty()) {
            return null;
        }
        return tierPool.get(RNG.nextInt(tierPool.size()));
    }

    private static long randomIntervalTicks() {
        long minTicks = cfg().timing.intervalMinSec * 20L;
        long maxTicks = cfg().timing.intervalMaxSec * 20L;
        long range = maxTicks - minTicks;
        return minTicks + (long) (RNG.nextDouble() * range);
    }
}

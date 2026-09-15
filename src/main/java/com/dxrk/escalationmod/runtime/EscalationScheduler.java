package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
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
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class EscalationScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SecureRandom RNG = new SecureRandom();

    private static final double TOTAL_TARGET_HOURS = 80.0;
    private static final TierSelector.Order ORDER = TierSelector.Order.ASCENDING;
    private static final int JITTER = 2;

    private static final long INTERVAL_MIN_TICKS = 60L * 20;
    private static final long INTERVAL_MAX_TICKS = 600L * 20;

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
                if (data.hasWon() && !data.isEndlessMode()) {
                    return;
                }
                trySpawn(player, data, currentTick);
                data.setNextSpawnAtTick(currentTick + randomIntervalTicks());
            });
        }
    }

    private static void trySpawn(ServerPlayer player, IEscalationPlayerData data, long currentTick) {
        double elapsedHours = data.getRealPlaytimeTicks() / 20.0 / 3600.0;
        int tier = TierSelector.selectTier(elapsedHours, TOTAL_TARGET_HOURS, ORDER, JITTER);

        EscalationDefinition definition = pickUnseenDefinition(tier, data.getSeenPoolIds());
        if (definition == null) {
            LOGGER.warn("Escalation: для {} не нашлось новых усложнений на тире {} (пул тира исчерпан)",
                    player.getName().getString(), tier);
            return;
        }

        Rarity rarity = RarityRoller.roll(tier);
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

        LOGGER.info("Escalation: игроку {} выпало '{}' (id={}, тир={}, редкость={}), легендарок всего: {}",
                player.getName().getString(), definition.name, definition.id, tier, rarity,
                data.getLegendaryCountEver());

        if (rarity == Rarity.LEGENDARY) {
            WinConditionManager.checkWinCondition(player, data);
        }
    }

    private static EscalationDefinition pickUnseenDefinition(int tier, Set<String> seenIds) {
        List<EscalationDefinition> tierPool = PoolRegistry.getByTier(tier);
        List<EscalationDefinition> unseen = tierPool.stream()
                .filter(def -> !seenIds.contains(def.id))
                .toList();

        if (unseen.isEmpty()) {
            return null;
        }
        return unseen.get(RNG.nextInt(unseen.size()));
    }

    private static long randomIntervalTicks() {
        long range = INTERVAL_MAX_TICKS - INTERVAL_MIN_TICKS;
        return INTERVAL_MIN_TICKS + (long) (RNG.nextDouble() * range);
    }
}

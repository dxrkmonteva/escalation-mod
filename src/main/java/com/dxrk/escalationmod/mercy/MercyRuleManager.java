package com.dxrk.escalationmod.mercy;

import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.security.SecureRandom;
import java.util.*;

public class MercyRuleManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SecureRandom RNG = new SecureRandom();

    private static final long PAUSE_DURATION_TICKS = 30L * 60 * 20;
    private static final long IGNORE_IF_REMAINING_ABOVE_TICKS = 10L * 60 * 20;
    private static final double EXTEND_PERCENT = 0.25;
    private static final double REMOVAL_PERCENT_MIN = 0.40;
    private static final double REMOVAL_PERCENT_MAX = 0.50;

    public static void onDeathBlockOfFive(ServerPlayer player, IEscalationPlayerData data, long currentTick) {
        boolean currentlyPaused = currentTick < data.getMercyPauseEndTick();

        if (!currentlyPaused) {
            startNewPause(player, data, currentTick);
            return;
        }

        long remaining = data.getMercyPauseEndTick() - currentTick;
        if (remaining > IGNORE_IF_REMAINING_ABOVE_TICKS) {
            LOGGER.info("Escalation Mercy Rule: у {} новый триггер проигнорирован — осталось {} тиков (> 10 мин)",
                    player.getName().getString(), remaining);
            player.sendSystemMessage(Component.literal(
                    "§7🛡 Передышка уже активна, до конца больше 10 минут — новый триггер проигнорирован."));
            return;
        }

        long extended = (long) (remaining * (1.0 + EXTEND_PERCENT));
        data.setMercyPauseEndTick(currentTick + extended);

        LOGGER.info("Escalation Mercy Rule: у {} пауза продлена — было {} тиков, стало {} тиков (+25%)",
                player.getName().getString(), remaining, extended);

        performRemoval(player, data, true);
    }

    private static void startNewPause(ServerPlayer player, IEscalationPlayerData data, long currentTick) {
        data.setMercyPauseEndTick(currentTick + PAUSE_DURATION_TICKS);

        LOGGER.info("Escalation Mercy Rule: у {} началась передышка на 30 минут (5-я смерть цикла)",
                player.getName().getString());

        performRemoval(player, data, false);
    }

    /**
     * Раздел 9: единица удаления — уникальный id целиком, процент считается от
     * количества уникальных активных id (не от сырого числа инстансов), выбор — FIFO
     * по времени первого появления id. Снятые id никак специально "не возвращаются
     * в пул" — они и так не отфильтрованы от повторного выпадения (см. seenPoolIds).
     */
    private static void performRemoval(ServerPlayer player, IEscalationPlayerData data, boolean isExtend) {
        List<EscalationInstance> active = data.getActiveInstances();

        Map<String, List<EscalationInstance>> grouped = new LinkedHashMap<>();
        for (EscalationInstance inst : active) {
            grouped.computeIfAbsent(inst.poolId, k -> new ArrayList<>()).add(inst);
        }

        int totalUniqueIds = grouped.size();

        if (totalUniqueIds == 0) {
            player.sendSystemMessage(Component.literal(
                    (isExtend ? "§7🛡 Передышка продлена" : "§b🛡 Передышка началась")
                            + " — активных усложнений пока нет, снимать нечего."));
            return;
        }

        double percent = REMOVAL_PERCENT_MIN + RNG.nextDouble() * (REMOVAL_PERCENT_MAX - REMOVAL_PERCENT_MIN);
        int removeGroupCount = Math.min(totalUniqueIds, (int) Math.round(totalUniqueIds * percent));

        List<Map.Entry<String, List<EscalationInstance>>> sortedGroups = new ArrayList<>(grouped.entrySet());
        sortedGroups.sort((a, b) -> Long.compare(firstSeen(a.getValue()), firstSeen(b.getValue())));

        Set<String> idsToRemove = new HashSet<>();
        for (int i = 0; i < removeGroupCount; i++) {
            idsToRemove.add(sortedGroups.get(i).getKey());
        }

        int removedInstanceCount = 0;
        Iterator<EscalationInstance> it = active.iterator();
        while (it.hasNext()) {
            if (idsToRemove.contains(it.next().poolId)) {
                it.remove();
                removedInstanceCount++;
            }
        }

        int actualPercent = (int) Math.round(removeGroupCount * 100.0 / totalUniqueIds);

        player.sendSystemMessage(Component.literal(String.format(
                "§b🛡 %s: снято %d%% усложнений (%d из %d уникальных id, %d экземпляров всего)",
                isExtend ? "Передышка продлена" : "Передышка", actualPercent, removeGroupCount, totalUniqueIds, removedInstanceCount)));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);

        LOGGER.info("Escalation Mercy Rule: у {} снято {} уникальных id ({} экземпляров) из {} уникальных ({}%)",
                player.getName().getString(), removeGroupCount, removedInstanceCount, totalUniqueIds, actualPercent);
    }

    private static long firstSeen(List<EscalationInstance> instances) {
        return instances.stream().mapToLong(i -> i.spawnedAtTick).min().orElse(0L);
    }
}

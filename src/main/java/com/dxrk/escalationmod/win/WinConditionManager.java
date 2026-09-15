package com.dxrk.escalationmod.win;

import com.dxrk.escalationmod.data.IEscalationPlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class WinConditionManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int REQUIRED_LEGENDARY_COUNT = 10;

    public static void checkWinCondition(ServerPlayer player, IEscalationPlayerData data) {
        if (data.getLegendaryCountEver() < REQUIRED_LEGENDARY_COUNT) {
            return;
        }
        if (data.hasWon()) {
            return;
        }

        data.setHasWon(true);

        int clearedCount = data.getActiveInstances().size();
        data.getActiveInstances().clear();

        player.sendSystemMessage(Component.literal(
                "§6§l✦ ПОБЕДА! ✦ §r§6Вы получили " + REQUIRED_LEGENDARY_COUNT
                        + " легендарных усложнений. Все активные усложнения сняты (" + clearedCount + " шт)."));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);

        LOGGER.info("Escalation: игрок {} выполнил условие победы (10 легендарок). Снято {} активных усложнений.",
                player.getName().getString(), clearedCount);
    }
}

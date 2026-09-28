package com.dxrk.escalationmod.win;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class WinConditionManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void checkWinCondition(ServerPlayer player, IEscalationPlayerData data) {
        EscalationConfig.WinConditionSection win = EscalationConfigManager.get().winCondition;

        if (data.getLegendaryCountEver() < win.requiredLegendaryCount) {
            return;
        }
        if (data.hasWon()) {
            return;
        }

        data.setHasWon(true);

        int clearedCount = 0;
        if (win.clearAllActive) {
            clearedCount = data.getActiveInstances().size();
            data.getActiveInstances().clear();
        }

        player.sendSystemMessage(Component.literal(
                "§6§l✦ ПОБЕДА! ✦ §r§6Вы получили " + win.requiredLegendaryCount
                        + " легендарных усложнений."
                        + (win.clearAllActive ? " Все активные усложнения сняты (" + clearedCount + " шт)." : "")));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);

        if (win.stopNewSpawnsAfterWin && win.endlessModeToggleAvailable) {
            MutableComponent buttons = Component.literal("§7Что дальше?  ")
                    .append(button("[▶ Продолжить (endless)]", "/escalation endless on", ChatFormatting.GREEN,
                            "Усложнения продолжат появляться, лимита легендарок сверху нет"))
                    .append(Component.literal("  "))
                    .append(button("[■ Остановиться]", "/escalation endless off", ChatFormatting.RED,
                            "Новые усложнения не появятся — забег закончен"));
            player.sendSystemMessage(buttons);
        }

        LOGGER.info("Escalation: игрок {} выполнил условие победы ({} легендарок). Снято {} активных усложнений.",
                player.getName().getString(), win.requiredLegendaryCount, clearedCount);
    }

    private static MutableComponent button(String label, String command, ChatFormatting color, String hint) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hint))));
    }
}

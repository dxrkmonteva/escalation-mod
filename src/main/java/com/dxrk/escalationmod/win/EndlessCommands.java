package com.dxrk.escalationmod.win;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationScheduler;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

/** /escalation endless [on|off] — доступно всем игрокам, но только после победы (раздел 8.1). */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class EndlessCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("escalation")
                .then(Commands.literal("endless")
                        .executes(EndlessCommands::status)
                        .then(Commands.literal("on").executes(ctx -> setEndless(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setEndless(ctx, false)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            String text;
            if (!data.hasWon()) {
                text = "Escalation: победа ещё не достигнута — endless пока недоступен.";
            } else if (data.isEndlessMode()) {
                text = "Escalation: endless-режим ВКЛЮЧЁН (усложнения продолжают появляться).";
            } else {
                text = "Escalation: endless-режим выключен (новые усложнения не появляются).";
            }
            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        });
        return 1;
    }

    private static int setEndless(CommandContext<CommandSourceStack> ctx, boolean enable)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        EscalationConfig cfg = EscalationConfigManager.get();
        if (!cfg.winCondition.endlessModeToggleAvailable) {
            ctx.getSource().sendFailure(Component.literal("Escalation: переключатель endless отключён в конфиге."));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AtomicInteger result = new AtomicInteger(0);

        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            if (!data.hasWon()) {
                ctx.getSource().sendFailure(Component.literal("Escalation: endless доступен только после победы."));
                return;
            }

            if (enable) {
                if (data.isEndlessMode()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("Escalation: endless уже включён."), false);
                    return;
                }
                data.setEndlessMode(true);
                EscalationScheduler.scheduleNextSpawn(player, data);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6∞ Endless включён — усложнения снова появляются, лимита легендарок сверху нет."), false);
                result.set(1);
            } else {
                if (!data.isEndlessMode()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("Escalation: endless и так выключен."), false);
                    return;
                }
                data.setEndlessMode(false);
                int cleared = 0;
                if (cfg.winCondition.clearActiveWhenEndlessStopped) {
                    cleared = data.getActiveInstances().size();
                    data.getActiveInstances().clear();
                }
                int clearedFinal = cleared;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7■ Endless выключен — новые усложнения больше не появляются"
                                + (clearedFinal > 0 ? " (снято накопленных: " + clearedFinal + ")." : ".")), false);
                result.set(1);
            }
        });
        return result.get();
    }
}

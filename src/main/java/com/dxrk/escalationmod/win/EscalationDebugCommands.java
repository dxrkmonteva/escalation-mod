package com.dxrk.escalationmod.win;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.mercy.MercyRuleManager;
import com.dxrk.escalationmod.runtime.EscalationScheduler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class EscalationDebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var addLegendary = Commands.literal("addlegendary")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
                        data.incrementLegendaryCountEver();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Escalation debug: legendaryCountEver = " + data.getLegendaryCountEver()), false);
                        WinConditionManager.checkWinCondition(player, data);
                    });
                    return 1;
                });

        var forceDeaths = Commands.literal("forcedeaths")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
                        for (int i = 0; i < 5; i++) {
                            data.incrementDeathCounter();
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Escalation debug: deathCounter = " + data.getDeathCounter()
                                        + " (симулировано +5, триггер Mercy Rule запущен)"), false);
                        long currentTick = player.level().getGameTime();
                        MercyRuleManager.onDeathBlockOfFive(player, data, currentTick);
                    });
                    return 1;
                });

        var setMercyRemaining = Commands.literal("setmercyremaining")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                        .executes(ctx -> {
                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
                                long currentTick = player.level().getGameTime();
                                data.setMercyPauseEndTick(currentTick + (long) seconds * 20L);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Escalation debug: до конца передышки выставлено " + seconds + " сек"), false);
                            });
                            return 1;
                        }));

        var spawnToast = Commands.literal("spawntoast")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    EscalationScheduler.debugForceSpawn(player);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Escalation debug: форсирован немедленный спавн усложнения (случайная редкость)"), false);
                    return 1;
                });

        var spawnLegendaryToast = Commands.literal("spawnlegendarytoast")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    EscalationScheduler.debugForceLegendaryToast(player);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Escalation debug: форсирован немедленный LEGENDARY-спавн (для проверки тоста/эскалации)"), false);
                    return 1;
                });

        dispatcher.register(Commands.literal("escalation")
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(addLegendary)
                        .then(forceDeaths)
                        .then(setMercyRemaining)
                        .then(spawnToast)
                        .then(spawnLegendaryToast)));
    }
}

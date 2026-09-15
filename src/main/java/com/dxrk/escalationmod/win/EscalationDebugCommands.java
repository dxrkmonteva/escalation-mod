package com.dxrk.escalationmod.win;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.mojang.brigadier.CommandDispatcher;
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

        dispatcher.register(Commands.literal("escalation")
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("addlegendary")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
                                        data.incrementLegendaryCountEver();
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Escalation debug: legendaryCountEver = " + data.getLegendaryCountEver()), false);
                                        WinConditionManager.checkWinCondition(player, data);
                                    });
                                    return 1;
                                }))));
    }
}

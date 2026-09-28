package com.dxrk.escalationmod.config;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class ConfigCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("escalation")
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            boolean ok = EscalationConfigManager.reload();
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Escalation: конфиг перезагружен. (Порог End уже созданных миров не меняется.)"), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal(
                                    "Escalation: не удалось перечитать конфиг — смотри лог, действуют прежние значения."));
                            return 0;
                        })));
    }
}

package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.runtime.EscalationScheduler;
import com.dxrk.escalationmod.runtime.Rarity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

/**
 * /escalation debug give <id|номер> [rarity] — выдать конкретное усложнение (номер 30 = escalation_hand_0030)
 * /escalation debug stats                    — текущие множители всех статов
 * /escalation debug clearall                 — снять все активные усложнения
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class StatDebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var stats = Commands.literal("stats")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    List<String> lines = StatEngine.describe(player);
                    if (lines.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Escalation debug: нет активных усложнений с числовым эффектом."), false);
                    } else {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Escalation debug: множители статов (" + lines.size() + "):"), false);
                        for (String line : lines) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§7" + line), false);
                        }
                    }
                    return 1;
                });

        var clearAll = Commands.literal("clearall")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
                        int n = data.getActiveInstances().size();
                        data.getActiveInstances().clear();
                        StatEngine.invalidate(player.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Escalation debug: снято экземпляров: " + n), false);
                    });
                    return 1;
                });

        var give = Commands.literal("give")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "id"), "COMMON"))
                        .then(Commands.argument("rarity", StringArgumentType.word())
                                .executes(ctx -> give(ctx,
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "rarity")))));

        event.getDispatcher().register(Commands.literal("escalation")
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(stats)
                        .then(clearAll)
                        .then(give)));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, String rawId, String rawRarity)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        String poolId = rawId.matches("\\d{1,4}")
                ? String.format("escalation_hand_%04d", Integer.parseInt(rawId))
                : rawId;

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(rawRarity.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(
                    "Escalation debug: редкость должна быть common / rare / epic / legendary."));
            return 0;
        }

        if (!EscalationScheduler.debugGive(player, poolId, rarity)) {
            ctx.getSource().sendFailure(Component.literal("Escalation debug: в пуле нет id " + poolId));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Escalation debug: выдано " + poolId + " (" + rarity.name() + ")"), false);
        return 1;
    }
}

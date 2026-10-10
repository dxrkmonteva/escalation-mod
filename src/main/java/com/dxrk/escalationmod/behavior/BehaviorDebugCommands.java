package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.pool.EscalationDefinition;
import com.dxrk.escalationmod.pool.PoolRegistry;
import com.dxrk.escalationmod.runtime.EscalationScheduler;
import com.dxrk.escalationmod.runtime.Rarity;
import com.dxrk.escalationmod.value.EscalationValueEntry;
import com.dxrk.escalationmod.value.ValueRegistry;
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

/**
 * /escalation debug behavior status        — сколько behavioral/flavor реализовано
 * /escalation debug behavior info <id>     — шаблон и параметры, реализован ли id
 * /escalation debug behavior fire <id>     — выдать id (если не активен) и принудительно выполнить эффект
 * /escalation debug behavior reload        — перечитать behaviors.json + behaviors_override.json
 * <id> — полный pool id или номер (30 = escalation_hand_0030).
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class BehaviorDebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var status = Commands.literal("status").executes(BehaviorDebugCommands::status);

        var info = Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "id"))));

        var fire = Commands.literal("fire")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> fire(ctx, StringArgumentType.getString(ctx, "id"))));

        var reload = Commands.literal("reload").executes(ctx -> {
            BehaviorRegistry.reload();
            BehaviorManager.invalidateAll();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Escalation behaviors: перезагружено, обработчиков: " + BehaviorRegistry.implementedCount()), false);
            return 1;
        });

        event.getDispatcher().register(Commands.literal("escalation")
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("behavior")
                                .then(status).then(info).then(fire).then(reload))));
    }

    private static String normalize(String raw) {
        return raw.matches("\\d{1,4}") ? String.format("escalation_hand_%04d", Integer.parseInt(raw)) : raw;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        int behavioral = 0, flavor = 0, doneBehavioral = 0, doneFlavor = 0;
        for (EscalationDefinition def : PoolRegistry.getAll()) {
            EscalationValueEntry v = ValueRegistry.getById(def.id);
            String cls = v == null ? "" : String.valueOf(v.stacking_class);
            boolean done = BehaviorRegistry.isImplemented(def.id);
            if (cls.equals("behavioral")) {
                behavioral++;
                if (done) doneBehavioral++;
            } else if (cls.equals("flavor")) {
                flavor++;
                if (done) doneFlavor++;
            }
        }
        int fb = behavioral, ff = flavor, db = doneBehavioral, df = doneFlavor;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Escalation behaviors: behavioral " + db + "/" + fb + ", flavor " + df + "/" + ff
                        + " реализовано (обработчиков всего " + BehaviorRegistry.implementedCount() + ")"), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String rawId) {
        String id = normalize(rawId);
        if (PoolRegistry.getById(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Escalation behaviors: в пуле нет id " + id));
            return 0;
        }
        BehaviorHandler h = BehaviorRegistry.get(id);
        String line;
        if (BehaviorRegistry.isDisabledByErrors(id)) {
            line = "§c" + id + " — ОТКЛЮЧЁН из-за ошибок (см. лог), исправь параметры и сделай reload";
        } else if (h == null) {
            line = "§7" + id + " — обработчика нет (ещё не реализован или выключен)";
        } else {
            line = "§a" + id + " — " + h.describe();
        }
        String text = line;
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int fire(CommandContext<CommandSourceStack> ctx, String rawId) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = normalize(rawId);
        if (PoolRegistry.getById(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Escalation behaviors: в пуле нет id " + id));
            return 0;
        }
        if (BehaviorRegistry.get(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Escalation behaviors: у " + id + " нет активного обработчика"));
            return 0;
        }

        BehaviorGroup group = find(player, id);
        if (group == null) {
            EscalationScheduler.debugGive(player, id, Rarity.COMMON);
            group = find(player, id);
        }
        if (group == null) {
            ctx.getSource().sendFailure(Component.literal("Escalation behaviors: не удалось выдать " + id));
            return 0;
        }
        BehaviorGroup g = group;
        BehaviorManager.safe(id, () -> g.handler.debugFire(player, g));
        ctx.getSource().sendSuccess(() -> Component.literal("Escalation behaviors: " + id + " — эффект выполнен принудительно"), false);
        return 1;
    }

    private static BehaviorGroup find(ServerPlayer player, String id) {
        for (BehaviorGroup g : BehaviorManager.groupsOf(player)) {
            if (g.poolId.equals(id)) return g;
        }
        return null;
    }
}

#!/usr/bin/env bash
# Каркас behavioral для Escalation. Запускать из корня репозитория: bash apply_behavior.sh
set -e
test -f build.gradle || { echo "Запусти из корня репо (где build.gradle)"; exit 1; }
mkdir -p "$(dirname "docs/BEHAVIOR_FRAMEWORK.md")"
cat > "docs/BEHAVIOR_FRAMEWORK.md" << '__ESC_EOF__'
# Каркас behavioral-усложнений (волна 1)

**Идея:** у каждого id своя запись в `behaviors.json` (свои параметры, свой `enabled`, свой статус в трекере).
Общий код — только шаблоны; уникальным механикам даём отдельный класс (`template: "custom_..."`).
Ошибка в одном id НЕ ломает остальные: после `behavior.errorsBeforeDisable` (по умолчанию 5) ошибок этот id отключается, остальные работают.

## Файлы
- `src/main/resources/escalation_default_behaviors.json` — встроенные реализации (обновляются со сборкой, копировать в config не нужно).
- `config/escalation/behaviors_override.json` — ТВОИ правки поверх (создаётся при первом запуске). Пример:
  ```json
  { "overrides": {
      "escalation_hand_0021": { "chance": 10 },
      "escalation_hand_0038": { "period_sec": 1800, "health_mult": 3.0 },
      "escalation_hand_0011": { "enabled": false }
  } }
  ```
  После правки: `/escalation debug behavior reload`.
- `docs/behavior_tracker.csv` — статус каждого из 1115 behavioral+flavor усложнений.
- Конфиг: `escalation_config.json -> behavior.enabled / errorsBeforeDisable`.

## Правила стакинга (одинаковые id)
- шанс: `1 - Π(1 - min(1, база × множитель_редкости_i))`;
- величина (урон/длительность/цена): `Σ множителей_редкости`;
- период (спавн мобов): `период / число копий`, но не меньше `min_period_sec` (если `stack_period: true`).

## Шаблоны
- `trigger_action` — триггер + условия + шанс + действия. Триггеры: periodic, block_break, player_hurt, player_attack,
  item_crafted, entity_killed, death_drops, sleep_attempt, right_click_block, anvil_use.
  Действия: effect, damage, drop_item, item_damage, break_equipped, destroy_item, xp_levels, cancel, cancel_sleep, rain, message, sound.
  Условия: raining, clear_weather, thundering, night, day, morning, noon, full_moon, overworld, nether, end, outdoors,
  underground, dark, in_nether_forest, in_badlands, no_helmet, no_boots, on_grass, recent_combat, low_health, no_sleep_days:N (префикс `!` = НЕ).
- `spawn_elite` — периодический спавн усиленного моба, идущего к игроку.
- `villager_price` — надбавка цены у жителя профессии X на предметы Y (при открытии торговли).
- `custom_*` — уникальные обработчики (скользкие блоки, тревога, паранойя, одиночество).

## Как добавить новое усложнение
1. Найти id в `docs/behavior_tracker.csv`.
2. Добавить запись в `escalation_default_behaviors.json` (или в override для пробы).
3. Нужна новая механика — новый шаблон/класс + регистрация в `BehaviorRegistry` (статический блок `TEMPLATES`).

## Debug
`/escalation debug behavior status | info <id> | fire <id> | reload` (id или номер: `21` = escalation_hand_0021).
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/Escalation.java")"
cat > "src/main/java/com/dxrk/escalationmod/Escalation.java" << '__ESC_EOF__'
package com.dxrk.escalationmod;

import com.dxrk.escalationmod.behavior.BehaviorRegistry;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.network.EscalationNetwork;
import com.dxrk.escalationmod.pool.PoolRegistry;
import com.dxrk.escalationmod.runtime.RuntimeSmokeTest;
import com.dxrk.escalationmod.stats.StatEngineSmokeTest;
import com.dxrk.escalationmod.value.ValueRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(Escalation.MODID)
public class Escalation {

    public static final String MODID = "escalation";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Escalation() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        EscalationNetwork.register();
        LOGGER.info("Escalation mod: конструктор вызван, регистрация прошла успешно.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        EscalationConfigManager.init();
        LOGGER.info("Escalation mod: commonSetup — загружаю пул усложнений...");
        PoolRegistry.init();
        ValueRegistry.init();
        BehaviorRegistry.init();
        RuntimeSmokeTest.run();
        StatEngineSmokeTest.run();
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/ActiveEscalations.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/ActiveEscalations.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import com.dxrk.escalationmod.stats.StatEngine;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

/**
 * ЕДИНАЯ точка изменения списка активных усложнений. Все, кто добавляет/снимает усложнения
 * (спавнер, Mercy Rule, победа, endless, debug), ходят только сюда — тогда кэши (StatEngine,
 * BehaviorManager) и жизненный цикл обработчиков (onActivated/onDeactivated) всегда в порядке.
 */
public final class ActiveEscalations {

    private ActiveEscalations() {
    }

    public static void add(ServerPlayer player, IEscalationPlayerData data, EscalationInstance instance) {
        data.getActiveInstances().add(instance);
        StatEngine.invalidate(player.getUUID());
        BehaviorManager.invalidate(player.getUUID());
        BehaviorManager.fireActivated(player, data, instance.poolId);
    }

    /** @return сколько ЭКЗЕМПЛЯРОВ снято. onDeactivated вызывается для id, у которых не осталось ни одного экземпляра. */
    public static int removeIf(ServerPlayer player, IEscalationPlayerData data, Predicate<EscalationInstance> filter) {
        Set<String> touched = new HashSet<>();
        int removed = 0;
        Iterator<EscalationInstance> it = data.getActiveInstances().iterator();
        while (it.hasNext()) {
            EscalationInstance inst = it.next();
            if (filter.test(inst)) {
                touched.add(inst.poolId);
                it.remove();
                removed++;
            }
        }
        if (removed == 0) return 0;

        StatEngine.invalidate(player.getUUID());
        BehaviorManager.invalidate(player.getUUID());

        Set<String> stillActive = new HashSet<>();
        for (EscalationInstance inst : data.getActiveInstances()) {
            stillActive.add(inst.poolId);
        }
        for (String id : touched) {
            if (!stillActive.contains(id)) {
                BehaviorManager.fireDeactivated(player, data, id);
            }
        }
        return removed;
    }

    public static int clear(ServerPlayer player, IEscalationPlayerData data) {
        return removeIf(player, data, i -> true);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorActions.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorActions.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Действия из JSON: {"type": "...", ...}. Любое действие может содержать "message" (action bar)
 * и "sound" (id звука, "volume", "pitch") — они выполняются после основного действия.
 *
 * Типы: effect, damage, drop_item, item_damage, break_equipped, destroy_item,
 *       xp_levels, cancel, cancel_sleep, rain, message, sound
 */
public final class BehaviorActions {

    private static int executingDepth = 0;

    private BehaviorActions() {
    }

    /** true, пока выполняется действие — защита от рекурсии (урон от действия снова вызывает триггер). */
    public static boolean isExecuting() {
        return executingDepth > 0;
    }

    public static void run(JsonArray actions, BehaviorContext ctx) {
        executingDepth++;
        try {
            for (JsonElement el : actions) {
                if (el.isJsonObject()) {
                    runOne(el.getAsJsonObject(), ctx);
                }
            }
        } finally {
            executingDepth--;
        }
    }

    private static void runOne(JsonObject a, BehaviorContext ctx) {
        ServerPlayer player = ctx.player;
        double mag = ctx.group.magnitude();
        String type = BehaviorJson.str(a, "type", "");

        switch (type) {
            case "effect" -> {
                MobEffect effect = BehaviorJson.effect(BehaviorJson.str(a, "effect", ""));
                if (effect == null) {
                    BehaviorRegistry.warnOnce("Неизвестный эффект '" + BehaviorJson.str(a, "effect", "") + "' (" + ctx.group.poolId + ")");
                    break;
                }
                double seconds = BehaviorJson.dbl(a, "seconds", 5.0) * mag;
                int ticks = (int) Math.max(1, Math.min(20.0 * 3600.0, seconds * 20.0));
                int amp = BehaviorJson.integer(a, "amplifier", 0);
                player.addEffect(new MobEffectInstance(effect, ticks, amp));
            }
            case "damage" -> {
                float amount = (float) (BehaviorJson.dbl(a, "amount", 2.0) * mag);
                player.hurt(player.damageSources().generic(), amount);
            }
            case "drop_item" -> dropRandomItem(player);
            case "item_damage" -> {
                ItemStack target = !ctx.stack.isEmpty() ? ctx.stack : player.getMainHandItem();
                if (target.isDamageableItem()) {
                    int amt = (int) Math.max(1, Math.round(BehaviorJson.dbl(a, "amount", 1.0) * mag));
                    target.setDamageValue(Math.min(target.getMaxDamage() - 1, target.getDamageValue() + amt));
                }
            }
            case "break_equipped" -> breakRandomEquipped(player);
            case "destroy_item" -> {
                if (ctx.drop != null) {
                    ctx.removeDrop = true;
                }
            }
            case "xp_levels" -> player.giveExperienceLevels(BehaviorJson.integer(a, "amount", -1));
            case "cancel" -> ctx.cancel = true;
            case "cancel_sleep" -> ctx.cancelSleep = true;
            case "rain" -> {
                if (player.level() instanceof ServerLevel level && !level.isRaining()) {
                    int ticks = (int) (BehaviorJson.dbl(a, "duration_sec", 600.0) * 20.0);
                    level.setWeatherParameters(0, ticks, true, false);
                }
            }
            case "message", "sound" -> {
                // выполняются общей частью ниже
            }
            default -> BehaviorRegistry.warnOnce("Неизвестный тип действия '" + type + "' (" + ctx.group.poolId + ")");
        }

        String msg = BehaviorJson.str(a, "message", null);
        if (msg != null) {
            player.displayClientMessage(Component.literal(msg), true);
        }
        String snd = BehaviorJson.str(a, "sound", null);
        if (snd != null) {
            playSound(player, snd, (float) BehaviorJson.dbl(a, "volume", 1.0), (float) BehaviorJson.dbl(a, "pitch", 1.0));
        }
    }

    public static void playSound(ServerPlayer player, String id, float volume, float pitch) {
        SoundEvent ev = BehaviorJson.sound(id);
        if (ev == null) {
            BehaviorRegistry.warnOnce("Неизвестный звук '" + id + "'");
            return;
        }
        player.playNotifySound(ev, SoundSource.PLAYERS, volume, pitch);
    }

    private static void dropRandomItem(ServerPlayer player) {
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty()) candidates.add(s);
        }
        if (candidates.isEmpty()) return;
        ItemStack chosen = candidates.get(BehaviorRng.RNG.nextInt(candidates.size()));
        ItemStack one = chosen.split(1);
        player.drop(one, true);
    }

    private static void breakRandomEquipped(ServerPlayer player) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        List<EquipmentSlot> filled = new ArrayList<>();
        for (EquipmentSlot s : slots) {
            if (!player.getItemBySlot(s).isEmpty()) filled.add(s);
        }
        if (filled.isEmpty()) return;
        EquipmentSlot chosen = filled.get(BehaviorRng.RNG.nextInt(filled.size()));
        player.setItemSlot(chosen, ItemStack.EMPTY);
        playSound(player, "minecraft:entity.item.break", 1.0f, 0.8f);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorConditions.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorConditions.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.stats.PlayerCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Строковые условия для JSON. Префикс "!" инвертирует. Параметр через двоеточие: "no_sleep_days:3".
 * Неизвестное условие = false (и один раз пишется в лог через реестр) — чтобы опечатка не включала эффект везде.
 */
public final class BehaviorConditions {

    private BehaviorConditions() {
    }

    public static boolean all(ServerPlayer player, List<String> conditions) {
        for (String c : conditions) {
            if (!check(player, c)) return false;
        }
        return true;
    }

    public static boolean check(ServerPlayer player, String raw) {
        boolean negate = raw.startsWith("!");
        String cond = negate ? raw.substring(1) : raw;
        return evaluate(player, cond) != negate;
    }

    private static boolean evaluate(ServerPlayer player, String cond) {
        String name = cond;
        double arg = 0.0;
        int colon = cond.indexOf(':');
        if (colon >= 0) {
            name = cond.substring(0, colon);
            try {
                arg = Double.parseDouble(cond.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }

        Level level = player.level();
        BlockPos pos = player.blockPosition();
        long dayTime = level.getDayTime() % 24000L;

        switch (name) {
            case "raining":
                return level.isRainingAt(pos);
            case "clear_weather":
                return !level.isRaining();
            case "thundering":
                return level.isThundering();
            case "night":
                return level.dimension() == Level.OVERWORLD && dayTime >= 13000L && dayTime < 23000L;
            case "day":
                return level.dimension() == Level.OVERWORLD && (dayTime < 13000L || dayTime >= 23000L);
            case "morning":
                return level.dimension() == Level.OVERWORLD && dayTime < 4000L;
            case "noon":
                return level.dimension() == Level.OVERWORLD && dayTime >= 5000L && dayTime < 9000L;
            case "full_moon":
                return level.dimension() == Level.OVERWORLD && level.getMoonPhase() == 0
                        && dayTime >= 13000L && dayTime < 23000L;
            case "overworld":
                return level.dimension() == Level.OVERWORLD;
            case "nether":
                return level.dimension() == Level.NETHER;
            case "end":
                return level.dimension() == Level.END;
            case "outdoors":
                return level.canSeeSky(pos);
            case "underground":
                return pos.getY() < (arg > 0 ? arg : 40);
            case "dark":
                return level.getMaxLocalRawBrightness(pos) < (arg > 0 ? (int) arg : 4);
            case "in_nether_forest":
                return level.getBiome(pos).is(Biomes.CRIMSON_FOREST) || level.getBiome(pos).is(Biomes.WARPED_FOREST);
            case "in_badlands":
                return level.getBiome(pos).is(BiomeTags.IS_BADLANDS);
            case "no_helmet":
                return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
            case "no_boots":
                return player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            case "on_grass":
                return level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK);
            case "recent_combat":
                return player.tickCount - player.getLastHurtMobTimestamp() < 600
                        || player.tickCount - player.getLastHurtByMobTimestamp() < 600;
            case "low_health":
                return player.getHealth() <= player.getMaxHealth() * (arg > 0 ? arg / 100.0 : 0.3);
            case "no_sleep_days":
                return PlayerCounters.timeSinceRest(player) >= (long) (arg * 24000.0);
            default:
                BehaviorRegistry.warnOnce("Неизвестное условие '" + cond + "' в behaviors.json (считается false)");
                return false;
        }
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorContext.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorContext.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Данные одного срабатывания триггера для одной группы + флаги "что сделать с событием". */
public final class BehaviorContext {

    public final Trigger trigger;
    public final ServerPlayer player;
    public final BehaviorGroup group;

    public BlockState block;
    public BlockPos pos;
    public ItemStack stack = ItemStack.EMPTY;
    public ItemStack stack2 = ItemStack.EMPTY;
    public float amount;
    public LivingEntity other;
    public ItemEntity drop;
    public AbstractVillager villager;

    /** Выходные флаги: диспетчер применяет их к исходному событию Forge. */
    public boolean cancel;
    public boolean cancelSleep;
    public boolean removeDrop;

    public BehaviorContext(Trigger trigger, ServerPlayer player, BehaviorGroup group) {
        this.trigger = trigger;
        this.player = player;
        this.group = group;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorDebugCommands.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorDebugCommands.java" << '__ESC_EOF__'
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
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorEvents.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorEvents.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.network.ClientBehaviorPacket;
import com.dxrk.escalationmod.network.EscalationNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * ЕДИНСТВЕННЫЙ диспетчер событий Forge для behavioral-усложнений: находит игрока и вызывает
 * только обработчики его АКТИВНЫХ id (а не 1056 подписчиков).
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public final class BehaviorEvents {

    private static final Map<UUID, AbstractVillager> LAST_TRADER = new HashMap<>();
    private static final Map<UUID, Map<String, Float>> LAST_SENT = new HashMap<>();

    private BehaviorEvents() {
    }

    // ------------------------------------------------------------------ раз в секунду

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!EscalationConfigManager.get().behavior.enabled) return;
        if (event.getServer().getTickCount() % 20 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            List<BehaviorGroup> groups = BehaviorManager.groupsOf(player);
            Map<String, Float> client = new TreeMap<>();
            for (BehaviorGroup g : groups) {
                BehaviorManager.safe(g.poolId, () -> g.handler.onSecond(player, g));
                BehaviorManager.safe(g.poolId, () -> g.handler.contributeClientState(player, g, client));
            }
            syncClient(player, client);
        }
    }

    private static void syncClient(ServerPlayer player, Map<String, Float> current) {
        Map<String, Float> prev = LAST_SENT.get(player.getUUID());
        Map<String, Float> before = prev == null ? Map.of() : prev;
        if (before.equals(current)) return;
        LAST_SENT.put(player.getUUID(), new HashMap<>(current));
        EscalationNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientBehaviorPacket(new HashMap<>(current)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        BehaviorManager.forget(id);
        LAST_TRADER.remove(id);
        LAST_SENT.remove(id);
    }

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        var pos = event.getPos();
        BehaviorManager.dispatch(Trigger.BLOCK_BREAK, player, c -> {
            c.block = state;
            c.pos = pos;
        }, null);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        float amount = event.getAmount();
        Entity source = event.getSource().getEntity();

        if (event.getEntity() instanceof ServerPlayer victim) {
            LivingEntity attacker = source instanceof LivingEntity le ? le : null;
            BehaviorManager.dispatch(Trigger.PLAYER_HURT, victim, c -> {
                c.amount = amount;
                c.other = attacker;
            }, null);
        }
        if (source instanceof ServerPlayer attackerPlayer && event.getEntity() != attackerPlayer) {
            LivingEntity target = event.getEntity();
            BehaviorManager.dispatch(Trigger.PLAYER_ATTACK, attackerPlayer, c -> {
                c.amount = amount;
                c.other = target;
            }, null);
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer && event.getEntity() != killer) {
            LivingEntity victim = event.getEntity();
            BehaviorManager.dispatch(Trigger.ENTITY_KILLED, killer, c -> c.other = victim, null);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        BehaviorManager.dispatch(Trigger.ITEM_CRAFTED, player, c -> c.stack = crafted, null);
    }

    @SubscribeEvent
    public static void onDeathDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            boolean[] remove = {false};
            BehaviorManager.dispatch(Trigger.DEATH_DROPS, player, c -> {
                c.drop = drop;
                c.stack = drop.getItem();
            }, c -> {
                if (c.removeDrop) remove[0] = true;
            });
            if (remove[0]) it.remove();
        }
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BehaviorManager.dispatch(Trigger.SLEEP_ATTEMPT, player, null, c -> {
            if (c.cancelSleep) event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        });
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        ItemStack held = event.getItemStack();
        var pos = event.getPos();
        BehaviorManager.dispatch(Trigger.RIGHT_CLICK_BLOCK, player, c -> {
            c.block = state;
            c.pos = pos;
            c.stack = held;
        }, c -> {
            if (c.cancel) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        BehaviorManager.dispatch(Trigger.ANVIL_USE, player, c -> {
            c.stack = left;
            c.stack2 = right;
        }, null);
    }

    // ------------------------------------------------------------------ торговля

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getTarget() instanceof AbstractVillager villager && event.getEntity() instanceof ServerPlayer player) {
            LAST_TRADER.put(player.getUUID(), villager);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof MerchantMenu)) return;
        AbstractVillager villager = LAST_TRADER.get(player.getUUID());
        if (villager == null) return;

        try {
            VillagerNumericHooks.apply(player, villager);
        } catch (Throwable t) {
            BehaviorRegistry.warnOnce("VillagerNumericHooks: " + t);
        }
        BehaviorManager.dispatch(Trigger.MERCHANT_OPEN, player, c -> c.villager = villager, null);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorFilters.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorFilters.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Фильтры блоков и предметов по строковым токенам из JSON.
 * Блоки: any | ore | log | chest | lever_or_button | contains:подстрока | minecraft:id
 * Предметы: any | tool_or_weapon | armor | enchanted_book | enchanted | damageable | contains:подстрока | id
 * Спецификация — строка или массив строк (достаточно совпадения с любым токеном).
 */
public final class BehaviorFilters {

    private BehaviorFilters() {
    }

    public static boolean block(JsonElement spec, BlockState state) {
        if (state == null) return false;
        List<String> tokens = BehaviorJson.strings(spec);
        if (tokens.isEmpty()) return true;
        Block block = state.getBlock();
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        String path = key == null ? "" : key.getPath();
        String full = key == null ? "" : key.toString();

        for (String t : tokens) {
            switch (t) {
                case "any" -> {
                    return true;
                }
                case "ore" -> {
                    if (state.is(Tags.Blocks.ORES) || path.endsWith("_ore") || path.equals("ancient_debris")) return true;
                }
                case "log" -> {
                    if (state.is(BlockTags.LOGS)) return true;
                }
                case "chest" -> {
                    if (block instanceof ChestBlock) return true;
                }
                case "lever_or_button" -> {
                    if (block instanceof LeverBlock || block instanceof ButtonBlock) return true;
                }
                default -> {
                    if (matchesIdToken(t, path, full)) return true;
                }
            }
        }
        return false;
    }

    public static boolean item(JsonElement spec, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        List<String> tokens = BehaviorJson.strings(spec);
        if (tokens.isEmpty()) return true;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = key == null ? "" : key.getPath();
        String full = key == null ? "" : key.toString();

        for (String t : tokens) {
            switch (t) {
                case "any" -> {
                    return true;
                }
                case "tool_or_weapon" -> {
                    if (stack.getItem() instanceof TieredItem) return true;
                }
                case "armor" -> {
                    if (stack.getItem() instanceof ArmorItem) return true;
                }
                case "enchanted_book" -> {
                    if (stack.is(Items.ENCHANTED_BOOK)) return true;
                }
                case "enchanted_armor" -> {
                    if (stack.getItem() instanceof ArmorItem && stack.isEnchanted()) return true;
                }
                case "enchanted" -> {
                    if (stack.isEnchanted()) return true;
                }
                case "damageable" -> {
                    if (stack.isDamageableItem()) return true;
                }
                default -> {
                    if (matchesIdToken(t, path, full)) return true;
                }
            }
        }
        return false;
    }

    /** Содержит ли путь id предмета подстроку (для торговли: "terracotta", "brick"...). */
    public static boolean pathContains(ItemStack stack, String part) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.getPath().contains(part);
    }

    private static boolean matchesIdToken(String token, String path, String full) {
        if (token.startsWith("contains:")) {
            return path.contains(token.substring("contains:".length()));
        }
        String normalized = token.contains(":") ? token : "minecraft:" + token;
        return normalized.equals(full);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorGroup.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorGroup.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * Все активные экземпляры ОДНОГО pool id у игрока (×23 одинаковых = одна группа).
 * Правила стакинга behavioral (решение по дизайну):
 *  - шанс:   1 - Π(1 - min(1, база × множитель_редкости_i))   -> chance()
 *  - величина (урон/длительность): Σ множителей редкости        -> magnitude()
 *  - период: делится на число копий, если handler включил stack_period (с нижним порогом)
 */
public final class BehaviorGroup {

    public final String poolId;
    public final List<EscalationInstance> instances;
    public final BehaviorHandler handler;
    private final IEscalationPlayerData data;

    public BehaviorGroup(String poolId, List<EscalationInstance> instances,
                         BehaviorHandler handler, IEscalationPlayerData data) {
        this.poolId = poolId;
        this.instances = instances;
        this.handler = handler;
        this.data = data;
    }

    public int count() {
        return instances.size();
    }

    /** Σ эффективных множителей (редкость × эскалация легендарки). Один common = 1.0. */
    public double magnitude() {
        double sum = 0.0;
        for (EscalationInstance i : instances) {
            sum += i.getEffectiveMultiplier();
        }
        return sum;
    }

    public double averageMultiplier() {
        return instances.isEmpty() ? 1.0 : magnitude() / instances.size();
    }

    /** Итоговый шанс (0..1) для базового шанса base (0..1) с учётом всех копий. */
    public double chance(double base) {
        double miss = 1.0;
        for (EscalationInstance i : instances) {
            double p = Math.max(0.0, Math.min(1.0, base * i.getEffectiveMultiplier()));
            miss *= (1.0 - p);
        }
        return 1.0 - miss;
    }

    public boolean roll(double base) {
        return BehaviorRng.roll(chance(base));
    }

    /** Персистентное состояние этого id у игрока (таймеры, счётчики). Стирается при снятии id. */
    public CompoundTag state() {
        CompoundTag root = data.getBehaviorState();
        if (!root.contains(poolId, Tag.TAG_COMPOUND)) {
            root.put(poolId, new CompoundTag());
        }
        return root.getCompound(poolId);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Реализация ОДНОГО усложнения (или шаблона, параметризованного из behaviors.json).
 * Любое исключение в методах ловится диспетчером и не ломает остальные усложнения.
 */
public interface BehaviorHandler {

    /** Вызывается при добавлении каждого экземпляра (group уже включает новый). */
    default void onActivated(ServerPlayer player, BehaviorGroup group) {
    }

    /** Вызывается, когда id снят ЦЕЛИКОМ (Mercy Rule, победа, clearall). Откатить эффекты/мобов. */
    default void onDeactivated(ServerPlayer player, String poolId) {
    }

    /** Раз в секунду, пока id активен. */
    default void onSecond(ServerPlayer player, BehaviorGroup group) {
    }

    /** Событие из {@link Trigger}. */
    default void onTrigger(BehaviorContext ctx) {
    }

    /** Значения, которые нужно отправить клиенту (клиентская часть эффекта). */
    default void contributeClientState(ServerPlayer player, BehaviorGroup group, Map<String, Float> out) {
    }

    /** Принудительно выполнить эффект (для /escalation debug behavior fire). */
    default void debugFire(ServerPlayer player, BehaviorGroup group) {
    }

    /** Краткое описание параметров для /escalation debug behavior info. */
    default String describe() {
        return getClass().getSimpleName();
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorJson.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorJson.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Мелкие помощники для чтения параметров из behaviors.json и безопасного поиска в реестрах. */
public final class BehaviorJson {

    private BehaviorJson() {
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsString() : def;
    }

    public static double dbl(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive()) {
            try {
                return e.getAsDouble();
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static int integer(JsonObject o, String key, int def) {
        return (int) Math.round(dbl(o, key, def));
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsBoolean() : def;
    }

    /** Строка или массив строк -> список строк. */
    public static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        if (e.isJsonPrimitive()) {
            out.add(e.getAsString());
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonPrimitive()) out.add(x.getAsString());
            }
        }
        return out;
    }

    public static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonArray()) ? e.getAsJsonArray() : new JsonArray();
    }

    public static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : new JsonObject();
    }

    // ---------- реестры: без getValue-по-умолчанию (у некоторых реестров "пустой" дефолт) ----------

    public static ResourceLocation rl(String id) {
        if (id == null) return null;
        return ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
    }

    public static MobEffect effect(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null || !ForgeRegistries.MOB_EFFECTS.containsKey(rl)) return null;
        return ForgeRegistries.MOB_EFFECTS.getValue(rl);
    }

    public static SoundEvent sound(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null || !ForgeRegistries.SOUND_EVENTS.containsKey(rl)) return null;
        return ForgeRegistries.SOUND_EVENTS.getValue(rl);
    }

    public static EntityType<?> entityType(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return null;
        return ForgeRegistries.ENTITY_TYPES.getValue(rl);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorManager.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorManager.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Consumer;

/** Кэш групп игрока + раздача событий обработчикам ТОЛЬКО его активных id. */
public final class BehaviorManager {

    private static final Map<UUID, Cache> CACHE = new HashMap<>();

    private static final class Cache {
        IEscalationPlayerData data;
        int size = -1;
        String firstId;
        String lastId;
        int registryVersion = -1;
        List<BehaviorGroup> groups = List.of();
    }

    private BehaviorManager() {
    }

    public static List<BehaviorGroup> groupsOf(ServerPlayer player) {
        IEscalationPlayerData[] holder = new IEscalationPlayerData[1];
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(d -> holder[0] = d);
        return holder[0] == null ? List.of() : groups(player, holder[0]);
    }

    public static List<BehaviorGroup> groups(ServerPlayer player, IEscalationPlayerData data) {
        List<EscalationInstance> active = data.getActiveInstances();
        String first = active.isEmpty() ? null : active.get(0).instanceId;
        String last = active.isEmpty() ? null : active.get(active.size() - 1).instanceId;

        Cache c = CACHE.computeIfAbsent(player.getUUID(), k -> new Cache());
        if (c.data != data || c.size != active.size() || !Objects.equals(c.firstId, first)
                || !Objects.equals(c.lastId, last) || c.registryVersion != BehaviorRegistry.version()) {
            c.groups = build(active, data);
            c.data = data;
            c.size = active.size();
            c.firstId = first;
            c.lastId = last;
            c.registryVersion = BehaviorRegistry.version();
        }
        return c.groups;
    }

    private static List<BehaviorGroup> build(List<EscalationInstance> active, IEscalationPlayerData data) {
        Map<String, List<EscalationInstance>> byId = new LinkedHashMap<>();
        for (EscalationInstance inst : active) {
            if (BehaviorRegistry.get(inst.poolId) != null) {
                byId.computeIfAbsent(inst.poolId, k -> new ArrayList<>()).add(inst);
            }
        }
        List<BehaviorGroup> out = new ArrayList<>(byId.size());
        for (Map.Entry<String, List<EscalationInstance>> en : byId.entrySet()) {
            out.add(new BehaviorGroup(en.getKey(), en.getValue(), BehaviorRegistry.get(en.getKey()), data));
        }
        return out;
    }

    public static void invalidate(UUID playerId) {
        Cache c = CACHE.get(playerId);
        if (c != null) c.size = -1;
    }

    public static void invalidateAll() {
        for (Cache c : CACHE.values()) c.size = -1;
    }

    public static void forget(UUID playerId) {
        CACHE.remove(playerId);
    }

    public static void safe(String poolId, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            BehaviorRegistry.reportError(poolId, t);
        }
    }

    /** Раздать событие всем группам игрока. prep заполняет контекст, post читает выходные флаги. */
    public static void dispatch(Trigger trigger, ServerPlayer player,
                                Consumer<BehaviorContext> prep, Consumer<BehaviorContext> post) {
        if (!EscalationConfigManager.get().behavior.enabled) return;
        if (BehaviorActions.isExecuting()) return;

        for (BehaviorGroup g : groupsOf(player)) {
            BehaviorContext ctx = new BehaviorContext(trigger, player, g);
            if (prep != null) prep.accept(ctx);
            safe(g.poolId, () -> g.handler.onTrigger(ctx));
            if (post != null) post.accept(ctx);
        }
    }

    // ---------- жизненный цикл (вызывается из ActiveEscalations) ----------

    static void fireActivated(ServerPlayer player, IEscalationPlayerData data, String poolId) {
        BehaviorHandler h = BehaviorRegistry.get(poolId);
        if (h == null) return;
        for (BehaviorGroup g : groups(player, data)) {
            if (g.poolId.equals(poolId)) {
                safe(poolId, () -> h.onActivated(player, g));
                break;
            }
        }
    }

    static void fireDeactivated(ServerPlayer player, IEscalationPlayerData data, String poolId) {
        BehaviorHandler h = BehaviorRegistry.get(poolId);
        if (h != null) {
            safe(poolId, () -> h.onDeactivated(player, poolId));
        }
        data.getBehaviorState().remove(poolId);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorRegistry.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorRegistry.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.behavior.custom.AlarmHandler;
import com.dxrk.escalationmod.behavior.custom.DarkParanoiaHandler;
import com.dxrk.escalationmod.behavior.custom.LonelinessHandler;
import com.dxrk.escalationmod.behavior.custom.SlipperyBlocksHandler;
import com.dxrk.escalationmod.behavior.templates.SpawnEliteHandler;
import com.dxrk.escalationmod.behavior.templates.TriggerActionHandler;
import com.dxrk.escalationmod.behavior.templates.VillagerPriceHandler;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.pool.PoolRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Реестр "pool id -> обработчик".
 * Источники (в порядке приоритета):
 *  1) config/escalation/behaviors_override.json  — твои правки поверх (по id, частичные поля допустимы);
 *  2) встроенный /escalation_default_behaviors.json — то, что поставляется с модом (обновляется со сборкой).
 * Каждая запись: {"template": "<имя шаблона/кастомного обработчика>", "enabled": true, ...параметры}.
 * Изоляция: ошибка в одном id не затрагивает другие; после N ошибок id отключается до /reload.
 */
public final class BehaviorRegistry {

    public interface Factory {
        BehaviorHandler create(String poolId, JsonObject params) throws Exception;
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BUNDLED = "/escalation_default_behaviors.json";
    private static final String OVERRIDE_PATH = "escalation/behaviors_override.json";

    private static final Map<String, Factory> TEMPLATES = new HashMap<>();
    private static Map<String, BehaviorHandler> handlers = new HashMap<>();
    private static Map<String, JsonObject> specs = new HashMap<>();
    private static final Map<String, Integer> errorCounts = new HashMap<>();
    private static final Set<String> disabledByErrors = new HashSet<>();
    private static final Set<String> warned = new HashSet<>();
    private static int version = 0;

    static {
        TEMPLATES.put("trigger_action", TriggerActionHandler::new);
        TEMPLATES.put("spawn_elite", SpawnEliteHandler::new);
        TEMPLATES.put("villager_price", VillagerPriceHandler::new);
        TEMPLATES.put("custom_slippery_blocks", SlipperyBlocksHandler::new);
        TEMPLATES.put("custom_alarm", AlarmHandler::new);
        TEMPLATES.put("custom_dark_paranoia", DarkParanoiaHandler::new);
        TEMPLATES.put("custom_loneliness", LonelinessHandler::new);
    }

    private BehaviorRegistry() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        errorCounts.clear();
        disabledByErrors.clear();
        warned.clear();

        Map<String, JsonObject> merged = new LinkedHashMap<>();
        readBundled(merged);
        readOverrides(merged);

        Map<String, BehaviorHandler> built = new HashMap<>();
        Map<String, JsonObject> builtSpecs = new HashMap<>();
        int skippedDisabled = 0;
        int failed = 0;

        for (Map.Entry<String, JsonObject> en : merged.entrySet()) {
            String id = en.getKey();
            JsonObject spec = en.getValue();
            if (PoolRegistry.getById(id) == null) {
                LOGGER.warn("Escalation behaviors: id {} нет в пуле — запись пропущена.", id);
                continue;
            }
            if (!BehaviorJson.bool(spec, "enabled", true)) {
                skippedDisabled++;
                builtSpecs.put(id, spec);
                continue;
            }
            String template = BehaviorJson.str(spec, "template", "");
            Factory factory = TEMPLATES.get(template);
            if (factory == null) {
                LOGGER.error("Escalation behaviors: {} — неизвестный шаблон '{}'.", id, template);
                failed++;
                continue;
            }
            try {
                built.put(id, factory.create(id, spec));
                builtSpecs.put(id, spec);
            } catch (Exception e) {
                LOGGER.error("Escalation behaviors: {} — не удалось создать обработчик (шаблон {}): {}", id, template, e.toString());
                failed++;
            }
        }

        handlers = built;
        specs = builtSpecs;
        version++;
        LOGGER.info("Escalation behaviors: активных обработчиков {}, выключено вручную {}, с ошибкой конфигурации {}.",
                built.size(), skippedDisabled, failed);
    }

    /** Обработчик id или null (нет реализации / выключен / отключён из-за ошибок). */
    public static BehaviorHandler get(String poolId) {
        if (disabledByErrors.contains(poolId)) return null;
        return handlers.get(poolId);
    }

    public static boolean isImplemented(String poolId) {
        return handlers.containsKey(poolId);
    }

    public static JsonObject spec(String poolId) {
        return specs.get(poolId);
    }

    public static int implementedCount() {
        return handlers.size();
    }

    public static boolean isDisabledByErrors(String poolId) {
        return disabledByErrors.contains(poolId);
    }

    /** Меняется при перезагрузке/отключении — кэш групп перестраивается. */
    public static int version() {
        return version;
    }

    public static void reportError(String poolId, Throwable t) {
        int n = errorCounts.merge(poolId, 1, Integer::sum);
        int limit = EscalationConfigManager.get().behavior.errorsBeforeDisable;
        LOGGER.error("Escalation behaviors: ошибка в {} (#{}): {}", poolId, n, t.toString(), t);
        if (n >= limit && disabledByErrors.add(poolId)) {
            version++;
            LOGGER.error("Escalation behaviors: {} ОТКЛЮЧЁН после {} ошибок (остальные усложнения работают). " +
                    "Исправь параметры в behaviors_override.json и сделай /escalation debug behavior reload.", poolId, n);
        }
    }

    public static void warnOnce(String message) {
        if (warned.add(message)) {
            LOGGER.warn("Escalation behaviors: {}", message);
        }
    }

    // ---------------------------------------------------------------- загрузка файлов

    private static void readBundled(Map<String, JsonObject> into) {
        try (InputStream in = BehaviorRegistry.class.getResourceAsStream(BUNDLED)) {
            if (in == null) {
                LOGGER.error("Escalation behaviors: встроенный ресурс {} не найден в jar — это баг сборки.", BUNDLED);
                return;
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                collect(JsonParser.parseReader(r), "behaviors", into, false);
            }
        } catch (Exception e) {
            LOGGER.error("Escalation behaviors: не удалось прочитать встроенный behaviors.json", e);
        }
    }

    private static void readOverrides(Map<String, JsonObject> into) {
        Path path = FMLPaths.CONFIGDIR.get().resolve(OVERRIDE_PATH);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                JsonObject skeleton = new JsonObject();
                skeleton.addProperty("_help", "Здесь можно править усложнения без пересборки. Ключ — pool id. " +
                        "Поля записи заменяют поля встроенной (например {\"chance\": 10}). " +
                        "{\"enabled\": false} выключает усложнение. После правки: /escalation debug behavior reload");
                skeleton.add("overrides", new JsonObject());
                try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(skeleton, w);
                }
                return;
            }
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                collect(JsonParser.parseReader(r), "overrides", into, true);
            }
        } catch (Exception e) {
            LOGGER.error("Escalation behaviors: не удалось прочитать {} — переопределения проигнорированы.", path, e);
        }
    }

    private static void collect(JsonElement root, String key, Map<String, JsonObject> into, boolean merge) {
        if (root == null || !root.isJsonObject()) return;
        JsonElement section = root.getAsJsonObject().get(key);
        if (section == null || !section.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> en : section.getAsJsonObject().entrySet()) {
            if (!en.getValue().isJsonObject()) continue;
            JsonObject incoming = en.getValue().getAsJsonObject();
            JsonObject existing = into.get(en.getKey());
            if (merge && existing != null) {
                JsonObject copy = existing.deepCopy();
                for (Map.Entry<String, JsonElement> f : incoming.entrySet()) {
                    copy.add(f.getKey(), f.getValue());
                }
                into.put(en.getKey(), copy);
            } else {
                into.put(en.getKey(), incoming);
            }
        }
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorRng.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorRng.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import java.security.SecureRandom;

/** Раздел 12 спеки: вся случайность — только SecureRandom. */
public final class BehaviorRng {

    public static final SecureRandom RNG = new SecureRandom();

    private BehaviorRng() {
    }

    public static boolean roll(double probability) {
        if (probability <= 0.0) return false;
        if (probability >= 1.0) return true;
        return RNG.nextDouble() < probability;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/BehaviorTimer.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/BehaviorTimer.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Персистентный таймер "раз в N секунд" в состоянии группы (переживает рестарт). */
public final class BehaviorTimer {

    private static final String KEY = "next";

    private BehaviorTimer() {
    }

    /**
     * @return true, когда пора сработать; следующий срок назначается автоматически.
     * firstDelaySec < 0 -> первая задержка = periodSec.
     * stackPeriod: период делится на число копий, но не меньше minPeriodSec.
     */
    public static boolean due(ServerPlayer player, BehaviorGroup group, double periodSec,
                              double firstDelaySec, boolean stackPeriod, double minPeriodSec) {
        long now = player.level().getGameTime();
        CompoundTag st = group.state();

        if (!st.contains(KEY)) {
            double first = firstDelaySec >= 0 ? firstDelaySec : periodSec;
            st.putLong(KEY, now + (long) (first * 20.0));
            return false;
        }
        if (now < st.getLong(KEY)) {
            return false;
        }
        double eff = effectivePeriod(group, periodSec, stackPeriod, minPeriodSec);
        st.putLong(KEY, now + (long) (eff * 20.0));
        return true;
    }

    public static double effectivePeriod(BehaviorGroup group, double periodSec, boolean stackPeriod, double minPeriodSec) {
        if (!stackPeriod) return periodSec;
        double divided = periodSec / Math.max(1, group.count());
        return Math.max(minPeriodSec, divided);
    }

    /** Сдвинуть таймер (для debug fire). */
    public static void reset(BehaviorGroup group) {
        group.state().remove(KEY);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/ClientBehaviorState.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/ClientBehaviorState.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Клиентская копия значений, присланных сервером (ClientBehaviorPacket). Чистый Java — безопасно грузится на сервере. */
public final class ClientBehaviorState {

    private static volatile Map<String, Float> values = Collections.emptyMap();

    private ClientBehaviorState() {
    }

    public static void replace(Map<String, Float> newValues) {
        values = new HashMap<>(newValues);
    }

    public static float get(String key) {
        Float v = values.get(key);
        return v == null ? 0.0f : v;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/Trigger.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/Trigger.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

/** События, которые диспетчер раздаёт обработчикам (периодика идёт отдельно через onSecond). */
public enum Trigger {
    BLOCK_BREAK,
    PLAYER_HURT,
    PLAYER_ATTACK,
    ITEM_CRAFTED,
    ENTITY_KILLED,
    DEATH_DROPS,
    SLEEP_ATTEMPT,
    RIGHT_CLICK_BLOCK,
    ANVIL_USE,
    MERCHANT_OPEN,
    PERIODIC
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/VillagerNumericHooks.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/VillagerNumericHooks.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import com.dxrk.escalationmod.stats.StatEngine;
import com.dxrk.escalationmod.value.EscalationValueEntry;
import com.dxrk.escalationmod.value.ValueRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Числовые усложнения торговли, которые раньше не были подключены:
 *  - villager_terracotta_price (id 0544): цена терракоты у жителей;
 *  - villager_price_inflation_per_game_hour (id 0129): рост всех цен со временем (в игровых часах с момента выпадения).
 * Применяется при открытии окна торговли через специальную надбавку цены (сбрасывается самой игрой при закрытии).
 */
public final class VillagerNumericHooks {

    private static final String INFLATION_ID = "escalation_hand_0129";

    private VillagerNumericHooks() {
    }

    public static void apply(ServerPlayer player, AbstractVillager villager) {
        double terracotta = StatEngine.factor(player, "villager_terracotta_price");
        double inflation = inflationFactor(player);
        if (terracotta == 1.0 && inflation == 1.0) return;

        for (MerchantOffer offer : villager.getOffers()) {
            double f = inflation;
            if (BehaviorFilters.pathContains(offer.getResult(), "terracotta")) {
                f *= terracotta;
            }
            if (f > 1.0) {
                int base = offer.getBaseCostA().getCount();
                offer.addToSpecialPriceDiff((int) Math.ceil(base * (f - 1.0)));
            }
        }
    }

    private static double inflationFactor(ServerPlayer player) {
        double[] pct = {0.0};
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            long now = player.level().getGameTime();
            for (EscalationInstance inst : data.getActiveInstances()) {
                if (!INFLATION_ID.equals(inst.poolId)) continue;
                EscalationValueEntry v = ValueRegistry.getById(INFLATION_ID);
                double base = v.base_value != null ? v.base_value : 1.0;
                double hours = Math.max(0.0, (now - inst.spawnedAtTick) / 1000.0);
                pct[0] += base * inst.getEffectiveMultiplier() * hours;
            }
        });
        return 1.0 + pct[0] / 100.0;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/client/SlipperyClient.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/client/SlipperyClient.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.client;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.behavior.ClientBehaviorState;
import com.dxrk.escalationmod.behavior.custom.SlipperyBlocksHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентская часть id 0003: часть обычных блоков (детерминированно по координатам) скользит как лёд.
 * Эмуляция: после ванильного трения возвращаем скорость к трению льда (0.98 / трение блока), с ограничением скорости.
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID, value = Dist.CLIENT)
public final class SlipperyClient {

    private static final double MAX_SPEED = 0.45;

    private SlipperyClient() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient()) return;
        LocalPlayer local = Minecraft.getInstance().player;
        if (local == null || event.player != local) return;

        float chance = ClientBehaviorState.get(SlipperyBlocksHandler.CLIENT_KEY);
        if (chance <= 0f || !local.onGround() || local.isInWater() || local.isPassenger()) return;

        BlockPos below = local.blockPosition().below();
        if (!isSlippery(below, chance)) return;

        BlockState state = local.level().getBlockState(below);
        float friction = state.getBlock().getFriction();
        if (friction >= 0.9f || friction <= 0f) return;

        double ratio = 0.98 / friction;
        Vec3 v = local.getDeltaMovement();
        double nx = v.x * ratio;
        double nz = v.z * ratio;
        double speed = Math.sqrt(nx * nx + nz * nz);
        if (speed > MAX_SPEED) {
            nx *= MAX_SPEED / speed;
            nz *= MAX_SPEED / speed;
        }
        local.setDeltaMovement(nx, v.y, nz);
    }

    private static boolean isSlippery(BlockPos pos, float chance) {
        int h = pos.getX() * 73856093 ^ pos.getY() * 19349663 ^ pos.getZ() * 83492791;
        h = (h ^ (h >>> 16)) & 0x7fffffff;
        return (h % 10000) < chance * 10000f;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/custom/AlarmHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/custom/AlarmHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.custom;

import com.dxrk.escalationmod.behavior.BehaviorGroup;
import com.dxrk.escalationmod.behavior.BehaviorHandler;
import com.dxrk.escalationmod.behavior.BehaviorJson;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

/**
 * id 0048 "Тотальная тревога": раз в несколько часов на 5 минут ВСЕ враждебные мобы в радиусе 64 блоков
 * нацеливаются на игрока. JSON: period_sec, duration_sec, radius, first_delay_sec.
 * Состояние: next (время след. запуска), until (конец текущей тревоги) — в game ticks, переживает рестарт.
 */
public class AlarmHandler implements BehaviorHandler {

    private final double periodSec;
    private final double durationSec;
    private final double radius;
    private final double firstDelaySec;

    public AlarmHandler(String poolId, JsonObject p) {
        this.periodSec = BehaviorJson.dbl(p, "period_sec", 10800.0);
        this.durationSec = BehaviorJson.dbl(p, "duration_sec", 300.0);
        this.radius = BehaviorJson.dbl(p, "radius", 64.0);
        this.firstDelaySec = BehaviorJson.dbl(p, "first_delay_sec", 3600.0);
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        long now = player.level().getGameTime();
        CompoundTag st = group.state();
        if (!st.contains("next")) {
            st.putLong("next", now + (long) (firstDelaySec * 20.0));
        }

        long until = st.getLong("until");
        if (now < until) {
            aggro(player);
            return;
        }
        if (until > 0) {
            st.putLong("until", 0L);
            player.displayClientMessage(Component.literal("§7Тревога улеглась."), true);
        }
        if (now >= st.getLong("next")) {
            start(player, group, now);
        }
    }

    private void start(ServerPlayer player, BehaviorGroup group, long now) {
        CompoundTag st = group.state();
        double eff = periodSec / Math.max(1, group.count());
        st.putLong("until", now + (long) (durationSec * group.averageMultiplier() * 20.0));
        st.putLong("next", now + (long) ((Math.max(eff, durationSec * 2) + durationSec) * 20.0));
        player.displayClientMessage(Component.literal("§c§l⚠ ТОТАЛЬНАЯ ТРЕВОГА: все мобы вокруг идут за вами!"), false);
    }

    private void aggro(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(radius);
        for (Monster m : player.level().getEntitiesOfClass(Monster.class, box)) {
            if (m.isAlive() && m.getTarget() != player) {
                m.setTarget(player);
            }
        }
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        start(player, group, player.level().getGameTime());
    }

    @Override
    public String describe() {
        return "тревога раз в " + periodSec + "с на " + durationSec + "с, радиус " + radius;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/custom/DarkParanoiaHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/custom/DarkParanoiaHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.custom;

import com.dxrk.escalationmod.behavior.BehaviorActions;
import com.dxrk.escalationmod.behavior.BehaviorGroup;
import com.dxrk.escalationmod.behavior.BehaviorHandler;
import com.dxrk.escalationmod.behavior.BehaviorJson;
import com.dxrk.escalationmod.behavior.BehaviorRng;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * id 0131 "Паранойя темноты": 5+ минут без света -> редкие фальшивые звуки шагов (слышит только игрок).
 * JSON: dark_seconds (300), light_level (4), chance (процент в секунду, 4).
 */
public class DarkParanoiaHandler implements BehaviorHandler {

    private static final String[] SOUNDS = {
            "minecraft:entity.zombie.step", "minecraft:entity.skeleton.step", "minecraft:entity.spider.step"
    };

    private final int darkSeconds;
    private final int lightLevel;
    private final double chancePct;

    public DarkParanoiaHandler(String poolId, JsonObject p) {
        this.darkSeconds = BehaviorJson.integer(p, "dark_seconds", 300);
        this.lightLevel = BehaviorJson.integer(p, "light_level", 4);
        this.chancePct = BehaviorJson.dbl(p, "chance", 4.0);
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        CompoundTag st = group.state();
        boolean dark = player.level().getMaxLocalRawBrightness(player.blockPosition()) < lightLevel;
        int seconds = dark ? st.getInt("dark") + 1 : 0;
        st.putInt("dark", seconds);

        if (seconds >= darkSeconds && group.roll(chancePct / 100.0)) {
            fake(player);
        }
    }

    private static void fake(ServerPlayer player) {
        String id = SOUNDS[BehaviorRng.RNG.nextInt(SOUNDS.length)];
        BehaviorActions.playSound(player, id, 0.7f, 0.85f + BehaviorRng.RNG.nextFloat() * 0.2f);
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        fake(player);
    }

    @Override
    public String describe() {
        return "фальшивые шаги после " + darkSeconds + "с в темноте (<" + lightLevel + "), шанс " + chancePct + "%/с";
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/custom/LonelinessHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/custom/LonelinessHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.custom;

import com.dxrk.escalationmod.behavior.BehaviorGroup;
import com.dxrk.escalationmod.behavior.BehaviorHandler;
import com.dxrk.escalationmod.behavior.BehaviorJson;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * id 0178 "Тревога одиночества": час реального времени без единого живого существа рядом -> слабый дебафф.
 * JSON: alone_seconds (3600), radius (32), effect (minecraft:weakness), amplifier (0).
 */
public class LonelinessHandler implements BehaviorHandler {

    private final int aloneSeconds;
    private final double radius;
    private final MobEffect effect;
    private final int amplifier;

    public LonelinessHandler(String poolId, JsonObject p) {
        this.aloneSeconds = BehaviorJson.integer(p, "alone_seconds", 3600);
        this.radius = BehaviorJson.dbl(p, "radius", 32.0);
        this.effect = BehaviorJson.effect(BehaviorJson.str(p, "effect", "minecraft:weakness"));
        this.amplifier = BehaviorJson.integer(p, "amplifier", 0);
        if (effect == null) throw new IllegalArgumentException("неизвестный effect");
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        CompoundTag st = group.state();
        boolean company = !player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive()).isEmpty();
        int alone = company ? 0 : st.getInt("alone") + 1;
        st.putInt("alone", Math.min(alone, 1_000_000));

        if (alone >= aloneSeconds) {
            if (alone == aloneSeconds) {
                player.displayClientMessage(Component.literal("§7Слишком тихо. Слишком давно вы никого не видели..."), true);
            }
            player.addEffect(new MobEffectInstance(effect, 100, amplifier, false, false));
        }
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        player.addEffect(new MobEffectInstance(effect, 200, amplifier));
    }

    @Override
    public String describe() {
        return "дебафф после " + aloneSeconds + "с без живых существ в радиусе " + radius;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/custom/SlipperyBlocksHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/custom/SlipperyBlocksHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.custom;

import com.dxrk.escalationmod.behavior.BehaviorGroup;
import com.dxrk.escalationmod.behavior.BehaviorHandler;
import com.dxrk.escalationmod.behavior.BehaviorJson;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * id 0003 "Скользкий лёд везде": доля обычных блоков, ведущих себя как лёд.
 * Сервер только считает итоговую долю (шанс стакается по копиям) и отправляет клиенту;
 * само скольжение делает клиент (SlipperyClient), потому что движение игрока рассчитывается на клиенте.
 * JSON: chance (процент блоков, по умолчанию 5).
 */
public class SlipperyBlocksHandler implements BehaviorHandler {

    public static final String CLIENT_KEY = "slippery_block_chance";

    private final double chancePct;

    public SlipperyBlocksHandler(String poolId, JsonObject p) {
        this.chancePct = BehaviorJson.dbl(p, "chance", 5.0);
    }

    @Override
    public void contributeClientState(ServerPlayer player, BehaviorGroup group, Map<String, Float> out) {
        out.put(CLIENT_KEY, (float) group.chance(chancePct / 100.0));
    }

    @Override
    public String describe() {
        return "доля скользких блоков " + chancePct + "% (клиентский эффект)";
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/templates/SpawnEliteHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/templates/SpawnEliteHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.templates;

import com.dxrk.escalationmod.behavior.*;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/**
 * Периодически спавнит усиленного ("элитного") моба рядом с игроком, который идёт к нему.
 *
 * JSON: entity, period_sec, first_delay_sec, stack_period, min_period_sec, cap (макс. живых таких мобов),
 *       health_mult, damage_mult, speed_mult (база; усиливается редкостью: 1 + (mult-1)*средний_множитель),
 *       armor: none|leather|iron|golden|diamond|copy_player, weapon (id предмета), glowing, name, message,
 *       follow_range, fire_resist, despawn_on_deactivate, conditions
 */
public class SpawnEliteHandler implements BehaviorHandler {

    private static final UUID HEALTH_ID = UUID.fromString("5e1f7a10-0c4d-4b3a-9a11-a5ca1a710001");
    private static final UUID DAMAGE_ID = UUID.fromString("5e1f7a10-0c4d-4b3a-9a11-a5ca1a710002");
    private static final UUID SPEED_ID = UUID.fromString("5e1f7a10-0c4d-4b3a-9a11-a5ca1a710003");

    private final String poolId;
    private final EntityType<?> type;
    private final double periodSec, firstDelaySec, minPeriodSec;
    private final boolean stackPeriod;
    private final int cap;
    private final double healthMult, damageMult, speedMult;
    private final String armor;
    private final String weapon;
    private final boolean glowing;
    private final String name;
    private final String message;
    private final double followRange;
    private final boolean fireResist;
    private final boolean despawnOnDeactivate;
    private final List<String> conditions;
    private final String tag;

    public SpawnEliteHandler(String poolId, JsonObject p) {
        this.poolId = poolId;
        this.tag = "escalation_elite_" + poolId;
        this.type = BehaviorJson.entityType(BehaviorJson.str(p, "entity", "minecraft:zombie"));
        if (type == null) throw new IllegalArgumentException("неизвестный entity");
        this.periodSec = BehaviorJson.dbl(p, "period_sec", 3600.0);
        this.firstDelaySec = BehaviorJson.dbl(p, "first_delay_sec", -1.0);
        this.minPeriodSec = BehaviorJson.dbl(p, "min_period_sec", 600.0);
        this.stackPeriod = BehaviorJson.bool(p, "stack_period", true);
        this.cap = Math.max(1, BehaviorJson.integer(p, "cap", 3));
        this.healthMult = BehaviorJson.dbl(p, "health_mult", 2.0);
        this.damageMult = BehaviorJson.dbl(p, "damage_mult", 1.5);
        this.speedMult = BehaviorJson.dbl(p, "speed_mult", 1.0);
        this.armor = BehaviorJson.str(p, "armor", "none");
        this.weapon = BehaviorJson.str(p, "weapon", "");
        this.glowing = BehaviorJson.bool(p, "glowing", false);
        this.name = BehaviorJson.str(p, "name", "");
        this.message = BehaviorJson.str(p, "message", "");
        this.followRange = BehaviorJson.dbl(p, "follow_range", 64.0);
        this.fireResist = BehaviorJson.bool(p, "fire_resist", true);
        this.despawnOnDeactivate = BehaviorJson.bool(p, "despawn_on_deactivate", true);
        this.conditions = BehaviorJson.strings(p.get("conditions"));
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        if (!BehaviorTimer.due(player, group, periodSec, firstDelaySec, stackPeriod, minPeriodSec)) return;
        if (!BehaviorConditions.all(player, conditions)) return;
        spawn(player, group);
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        spawn(player, group);
    }

    @Override
    public void onDeactivated(ServerPlayer player, String id) {
        if (!despawnOnDeactivate || !(player.level() instanceof ServerLevel level)) return;
        AABB box = player.getBoundingBox().inflate(192.0);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.getTags().contains(tag))) {
            m.discard();
        }
    }

    private void spawn(ServerPlayer player, BehaviorGroup group) {
        if (!(player.level() instanceof ServerLevel level)) return;

        AABB box = player.getBoundingBox().inflate(160.0);
        if (level.getEntitiesOfClass(Mob.class, box, e -> e.getTags().contains(tag)).size() >= cap) return;

        BlockPos spot = findSpot(level, player.blockPosition());
        if (spot == null) return;

        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) return;

        double avg = group.averageMultiplier();
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, BehaviorRng.RNG.nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
        mob.ejectPassengers();
        if (mob instanceof Zombie z) z.setBaby(false);
        mob.addTag(tag);
        mob.setPersistenceRequired();

        scale(mob, Attributes.MAX_HEALTH, HEALTH_ID, "escalation_elite_health", 1.0 + (healthMult - 1.0) * avg);
        scale(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "escalation_elite_damage", 1.0 + (damageMult - 1.0) * avg);
        scale(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, "escalation_elite_speed", 1.0 + (speedMult - 1.0) * avg);
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(followRange);
        mob.setHealth(mob.getMaxHealth());

        equip(mob, player);
        if (glowing) mob.setGlowingTag(true);
        if (fireResist) mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 60 * 10, 0, false, false));
        if (!name.isEmpty()) {
            mob.setCustomName(Component.literal(name));
            mob.setCustomNameVisible(true);
        }
        mob.setTarget(player);

        level.addFreshEntity(mob);
        mob.setHealth(mob.getMaxHealth());

        if (!message.isEmpty()) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    private static void scale(Mob mob, Attribute attr, UUID id, String modName, double factor) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null || factor == 1.0) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, modName, factor - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private void equip(Mob mob, ServerPlayer player) {
        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        if (armor.equals("copy_player")) {
            for (EquipmentSlot s : armorSlots) {
                mob.setItemSlot(s, player.getItemBySlot(s).copy());
                mob.setDropChance(s, 0.0f);
            }
            mob.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copy());
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            mob.setItemSlot(EquipmentSlot.OFFHAND, player.getOffhandItem().copy());
            mob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
            return;
        }

        Item[] set = switch (armor) {
            case "leather" -> new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
            case "iron" -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
            case "golden" -> new Item[]{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
            case "diamond" -> new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
            default -> null;
        };
        if (set != null) {
            for (int i = 0; i < armorSlots.length; i++) {
                mob.setItemSlot(armorSlots[i], new ItemStack(set[i]));
                mob.setDropChance(armorSlots[i], 0.0f);
            }
        }
        if (!weapon.isEmpty()) {
            var rl = BehaviorJson.rl(weapon);
            if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ForgeRegistries.ITEMS.getValue(rl)));
                mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
        }
    }

    /** Безопасная точка на расстоянии 14-28 блоков: воздух, воздух, твёрдый пол. null — не нашли. */
    private static BlockPos findSpot(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = BehaviorRng.RNG.nextDouble() * Math.PI * 2.0;
            int dist = 14 + BehaviorRng.RNG.nextInt(15);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = 6; dy >= -8; dy--) {
                BlockPos pos = new BlockPos(x, center.getY() + dy, z);
                if (!level.isLoaded(pos)) break;
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                    return pos;
                }
            }
        }
        return null;
    }

    @Override
    public String describe() {
        return "спавн " + ForgeRegistries.ENTITY_TYPES.getKey(type) + " раз в " + periodSec + "с (cap " + cap + "), ×hp "
                + healthMult + ", ×dmg " + damageMult + ", броня " + armor;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/templates/TriggerActionHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/templates/TriggerActionHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.templates;

import com.dxrk.escalationmod.behavior.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * Универсальный шаблон "триггер + условия + шанс + действия".
 *
 * JSON:
 *  trigger:   periodic | block_break | player_hurt | player_attack | item_crafted | entity_killed |
 *             death_drops | sleep_attempt | right_click_block | anvil_use
 *  chance:    базовый шанс в процентах (по умолчанию 100); стакается как 1-Π(1-p)
 *  period_sec / first_delay_sec / stack_period / min_period_sec  — только для periodic
 *  conditions: ["raining", "!outdoors", "no_sleep_days:3", ...]   (см. BehaviorConditions)
 *  filters:   { block, item, damage_min, both_enchanted_books }    (см. BehaviorFilters)
 *  actions:   [ {type: ...}, ... ]                                 (см. BehaviorActions)
 */
public class TriggerActionHandler implements BehaviorHandler {

    private final String poolId;
    private final boolean periodic;
    private final Trigger trigger;
    private final double chancePct;
    private final double periodSec;
    private final double firstDelaySec;
    private final double minPeriodSec;
    private final boolean stackPeriod;
    private final JsonObject filters;
    private final List<String> conditions;
    private final JsonArray actions;

    public TriggerActionHandler(String poolId, JsonObject p) {
        this.poolId = poolId;
        String t = BehaviorJson.str(p, "trigger", "periodic").toUpperCase(Locale.ROOT);
        this.periodic = t.equals("PERIODIC");
        this.trigger = periodic ? null : Trigger.valueOf(t);
        this.chancePct = BehaviorJson.dbl(p, "chance", 100.0);
        this.periodSec = BehaviorJson.dbl(p, "period_sec", 60.0);
        this.firstDelaySec = BehaviorJson.dbl(p, "first_delay_sec", -1.0);
        this.minPeriodSec = BehaviorJson.dbl(p, "min_period_sec", 1.0);
        this.stackPeriod = BehaviorJson.bool(p, "stack_period", false);
        this.filters = BehaviorJson.object(p, "filters");
        this.conditions = BehaviorJson.strings(p.get("conditions"));
        this.actions = BehaviorJson.array(p, "actions");
        if (actions.size() == 0) {
            throw new IllegalArgumentException("нет actions");
        }
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        if (!periodic) return;
        if (!BehaviorTimer.due(player, group, periodSec, firstDelaySec, stackPeriod, minPeriodSec)) return;
        if (!BehaviorConditions.all(player, conditions)) return;
        if (!group.roll(chancePct / 100.0)) return;
        BehaviorActions.run(actions, new BehaviorContext(Trigger.PERIODIC, player, group));
    }

    @Override
    public void onTrigger(BehaviorContext ctx) {
        if (periodic || ctx.trigger != trigger) return;
        if (!filtersMatch(ctx)) return;
        if (!BehaviorConditions.all(ctx.player, conditions)) return;
        if (!ctx.group.roll(chancePct / 100.0)) return;
        BehaviorActions.run(actions, ctx);
    }

    private boolean filtersMatch(BehaviorContext ctx) {
        if (filters.has("block") && !BehaviorFilters.block(filters.get("block"), ctx.block)) return false;
        if (filters.has("item") && !BehaviorFilters.item(filters.get("item"), ctx.stack)) return false;
        if (filters.has("damage_min") && ctx.amount < BehaviorJson.dbl(filters, "damage_min", 0.0)) return false;
        if (BehaviorJson.bool(filters, "both_enchanted_books", false)
                && !(ctx.stack.is(Items.ENCHANTED_BOOK) && ctx.stack2.is(Items.ENCHANTED_BOOK))) return false;
        return true;
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        BehaviorContext ctx = new BehaviorContext(Trigger.PERIODIC, player, group);
        BehaviorActions.run(actions, ctx);
    }

    @Override
    public String describe() {
        return (periodic ? "periodic каждые " + periodSec + "с" : "trigger=" + trigger)
                + ", шанс " + chancePct + "%, условия " + conditions + ", действий " + actions.size();
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/behavior/templates/VillagerPriceHandler.java")"
cat > "src/main/java/com/dxrk/escalationmod/behavior/templates/VillagerPriceHandler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.behavior.templates;

import com.dxrk.escalationmod.behavior.*;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Надбавка к цене у жителя конкретной профессии при открытии окна торговли.
 *
 * JSON: profession ("fisherman"; пусто = любой), result (токены предметов как в BehaviorFilters),
 *       percent (+% к базовой цене), flat (+N к количеству), conditions.
 * Надбавка идёт через specialPriceDiff — игра сама сбрасывает её при закрытии окна торговли.
 */
public class VillagerPriceHandler implements BehaviorHandler {

    private final String profession;
    private final com.google.gson.JsonElement resultFilter;
    private final double percent;
    private final int flat;
    private final List<String> conditions;

    public VillagerPriceHandler(String poolId, JsonObject p) {
        this.profession = BehaviorJson.str(p, "profession", "");
        this.resultFilter = p.get("result");
        this.percent = BehaviorJson.dbl(p, "percent", 0.0);
        this.flat = BehaviorJson.integer(p, "flat", 0);
        this.conditions = BehaviorJson.strings(p.get("conditions"));
        if (percent <= 0.0 && flat <= 0) {
            throw new IllegalArgumentException("нужен percent > 0 или flat > 0");
        }
    }

    @Override
    public void onTrigger(BehaviorContext ctx) {
        if (ctx.trigger != Trigger.MERCHANT_OPEN || ctx.villager == null) return;
        if (!professionMatches(ctx.villager)) return;
        if (!BehaviorConditions.all(ctx.player, conditions)) return;

        double mag = ctx.group.magnitude();
        for (MerchantOffer offer : ctx.villager.getOffers()) {
            ItemStack result = offer.getResult();
            if (resultFilter != null && !BehaviorFilters.item(resultFilter, result)) continue;
            int base = offer.getBaseCostA().getCount();
            int extra = (int) Math.ceil(base * percent / 100.0 * mag) + (int) Math.round(flat * mag);
            if (extra > 0) {
                offer.addToSpecialPriceDiff(extra);
            }
        }
    }

    private boolean professionMatches(AbstractVillager v) {
        if (profession.isEmpty()) return true;
        if (!(v instanceof Villager villager)) return false;
        ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(villager.getVillagerData().getProfession());
        return key != null && key.getPath().equals(profession);
    }

    @Override
    public String describe() {
        return "цена у '" + (profession.isEmpty() ? "любого" : profession) + "' +" + percent + "% / +" + flat
                + ", условия " + conditions;
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/config/EscalationConfig.java")"
cat > "src/main/java/com/dxrk/escalationmod/config/EscalationConfig.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.config;

/**
 * Раздел 13: все настраиваемые числа мода в одном месте.
 * Файл: config/escalation/escalation_config.json. Значения по умолчанию = то, что было
 * захардкожено раньше. Отсутствующие в файле ключи остаются со значением по умолчанию.
 */
public class EscalationConfig {

    public TimingSection timing = new TimingSection();
    public TierProgressionSection tierProgression = new TierProgressionSection();
    public DimensionLockSection dimensionLock = new DimensionLockSection();
    public RaritySection rarity = new RaritySection();
    public StackingSection stacking = new StackingSection();
    public MercyRuleSection mercyRule = new MercyRuleSection();
    public WinConditionSection winCondition = new WinConditionSection();
    public UiSection ui = new UiSection();
    public BehaviorSection behavior = new BehaviorSection();

    public static class TimingSection {
        public int intervalMinSec = 60;
        public int intervalMaxSec = 600;
    }

    public static class TierProgressionSection {
        /** ASCENDING | DESCENDING | RANDOM */
        public String order = "ASCENDING";
        public double totalTargetHours = 80.0;
        public int jitter = 2;
    }

    public static class DimensionLockSection {
        public double netherRealHours = 20.0;
        /** Порог End генерируется ОДИН раз при создании мира — на уже созданные миры не влияет. */
        public double endRealHoursMin = 50.0;
        public double endRealHoursMax = 100.0;
        public double endSoonThresholdHours = 10.0;
    }

    public static class RaritySection {
        public WeightsSection weights = new WeightsSection();
        public MultipliersSection multipliers = new MultipliersSection();
        public LegendaryEscalationSection legendaryEscalation = new LegendaryEscalationSection();
    }

    public static class WeightsSection {
        public double commonTier1 = 70.0;
        public double commonTier10 = 40.0;
        public double rareTier1 = 22.0;
        public double rareTier10 = 32.0;
        public double epicTier1 = 7.0;
        public double epicTier10 = 21.0;
        /** legendary% = legendaryBasePercent + tier * legendaryPerTierPercent */
        public double legendaryBasePercent = 0.5;
        public double legendaryPerTierPercent = 0.65;
    }

    public static class MultipliersSection {
        public double common = 1.0;
        public double rare = 1.5;
        public double epic = 2.5;
        public double legendary = 4.0;
    }

    public static class LegendaryEscalationSection {
        public double perSecond = 0.2;
        public int durationSec = 30;
    }

    /** Раздел 7: стакинг и клэмпы. */
    public static class StackingSection {
        /** Максимум снижения для percent_stat_clamped (%), на каждый инстанс и на итог. */
        public double percentStatClamp = 99.9;
        /** Инженерный потолок против NaN/Infinity — не балансный. */
        public double engineSafetyCap = 1.0E15;
        /** Радиус поиска ближайшего игрока, чей множитель применяется к заспавнившемуся мобу. */
        public double mobStatNearestPlayerRadius = 128.0;
    }

    public static class MercyRuleSection {
        public int deathsToTrigger = 5;
        public double pauseDurationMin = 30.0;
        public double removalPercentMin = 40.0;
        public double removalPercentMax = 50.0;
        public double ignoreIfRemainingMinAbove = 10.0;
        public double extendPercentIfBelowOrEqual = 25.0;
    }

    public static class WinConditionSection {
        public int requiredLegendaryCount = 10;
        public boolean clearAllActive = true;
        public boolean stopNewSpawnsAfterWin = true;
        public boolean endlessModeToggleAvailable = true;
        /** При выборе "Остановиться" в endless-режиме снимать все накопленные усложнения. */
        public boolean clearActiveWhenEndlessStopped = true;
    }

    /** Каркас behavioral-усложнений (behaviors.json). */
    public static class BehaviorSection {
        /** Главный выключатель: false -> ни один behavioral-обработчик не работает. */
        public boolean enabled = true;
        /** После скольких ошибок конкретный id отключается (остальные продолжают работать). */
        public int errorsBeforeDisable = 5;
    }

    public static class UiSection {
        public double toastGrowMs = 220.0;
        public double toastHoldMinSec = 5.0;
        public double toastHoldMaxSec = 8.0;
        public double toastShrinkMs = 180.0;
        public double toastTypewriterMsPerChar = 18.0;
        public double journalStaggerMs = 55.0;
        public double journalTypewriterMsPerChar = 8.0;
        public int timerBarYellowBelowSec = 30;
        public int timerBarRedBelowSec = 10;
    }

    /** Чинит null-секции (если их стёрли из файла) и приводит значения к разумным границам. */
    public EscalationConfig sanitize() {
        if (timing == null) timing = new TimingSection();
        if (tierProgression == null) tierProgression = new TierProgressionSection();
        if (dimensionLock == null) dimensionLock = new DimensionLockSection();
        if (rarity == null) rarity = new RaritySection();
        if (rarity.weights == null) rarity.weights = new WeightsSection();
        if (rarity.multipliers == null) rarity.multipliers = new MultipliersSection();
        if (rarity.legendaryEscalation == null) rarity.legendaryEscalation = new LegendaryEscalationSection();
        if (stacking == null) stacking = new StackingSection();
        if (mercyRule == null) mercyRule = new MercyRuleSection();
        if (winCondition == null) winCondition = new WinConditionSection();
        if (ui == null) ui = new UiSection();
        if (behavior == null) behavior = new BehaviorSection();
        behavior.errorsBeforeDisable = Math.max(1, behavior.errorsBeforeDisable);

        timing.intervalMinSec = Math.max(1, timing.intervalMinSec);
        timing.intervalMaxSec = Math.max(timing.intervalMinSec, timing.intervalMaxSec);

        if (tierProgression.order == null) tierProgression.order = "ASCENDING";
        tierProgression.totalTargetHours = Math.max(0.1, tierProgression.totalTargetHours);
        tierProgression.jitter = Math.max(0, tierProgression.jitter);

        dimensionLock.netherRealHours = Math.max(0.0, dimensionLock.netherRealHours);
        dimensionLock.endRealHoursMin = Math.max(0.0, dimensionLock.endRealHoursMin);
        dimensionLock.endRealHoursMax = Math.max(dimensionLock.endRealHoursMin, dimensionLock.endRealHoursMax);
        dimensionLock.endSoonThresholdHours = Math.max(0.0, dimensionLock.endSoonThresholdHours);

        WeightsSection w = rarity.weights;
        w.commonTier1 = Math.max(0.0, w.commonTier1);
        w.commonTier10 = Math.max(0.0, w.commonTier10);
        w.rareTier1 = Math.max(0.0, w.rareTier1);
        w.rareTier10 = Math.max(0.0, w.rareTier10);
        w.epicTier1 = Math.max(0.0, w.epicTier1);
        w.epicTier10 = Math.max(0.0, w.epicTier10);
        w.legendaryBasePercent = Math.max(0.0, w.legendaryBasePercent);
        w.legendaryPerTierPercent = Math.max(0.0, w.legendaryPerTierPercent);
        // легендарка на тире 1 никогда не должна быть нулевой (раздел 6) — защита от нулевой суммы весов
        if (w.legendaryBasePercent + w.legendaryPerTierPercent <= 0.0) {
            w.legendaryBasePercent = 0.5;
        }

        MultipliersSection m = rarity.multipliers;
        m.common = Math.max(0.0, m.common);
        m.rare = Math.max(0.0, m.rare);
        m.epic = Math.max(0.0, m.epic);
        m.legendary = Math.max(0.0, m.legendary);

        rarity.legendaryEscalation.perSecond = Math.max(0.0, rarity.legendaryEscalation.perSecond);
        rarity.legendaryEscalation.durationSec = Math.max(0, rarity.legendaryEscalation.durationSec);

        stacking.percentStatClamp = clamp(stacking.percentStatClamp, 0.0, 99.99);
        stacking.engineSafetyCap = Math.max(1.0, stacking.engineSafetyCap);
        stacking.mobStatNearestPlayerRadius = Math.max(1.0, stacking.mobStatNearestPlayerRadius);

        mercyRule.deathsToTrigger = Math.max(1, mercyRule.deathsToTrigger);
        mercyRule.pauseDurationMin = Math.max(0.1, mercyRule.pauseDurationMin);
        mercyRule.removalPercentMin = clamp(mercyRule.removalPercentMin, 0.0, 100.0);
        mercyRule.removalPercentMax = clamp(mercyRule.removalPercentMax, mercyRule.removalPercentMin, 100.0);
        mercyRule.ignoreIfRemainingMinAbove = Math.max(0.0, mercyRule.ignoreIfRemainingMinAbove);
        mercyRule.extendPercentIfBelowOrEqual = Math.max(0.0, mercyRule.extendPercentIfBelowOrEqual);

        winCondition.requiredLegendaryCount = Math.max(1, winCondition.requiredLegendaryCount);

        ui.toastGrowMs = Math.max(0.0, ui.toastGrowMs);
        ui.toastHoldMinSec = Math.max(0.5, ui.toastHoldMinSec);
        ui.toastHoldMaxSec = Math.max(ui.toastHoldMinSec, ui.toastHoldMaxSec);
        ui.toastShrinkMs = Math.max(0.0, ui.toastShrinkMs);
        ui.toastTypewriterMsPerChar = Math.max(0.0, ui.toastTypewriterMsPerChar);
        ui.journalStaggerMs = Math.max(0.0, ui.journalStaggerMs);
        ui.journalTypewriterMsPerChar = Math.max(0.0, ui.journalTypewriterMsPerChar);
        ui.timerBarRedBelowSec = Math.max(0, ui.timerBarRedBelowSec);
        ui.timerBarYellowBelowSec = Math.max(ui.timerBarRedBelowSec, ui.timerBarYellowBelowSec);

        return this;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/data/EscalationPlayerData.java")"
cat > "src/main/java/com/dxrk/escalationmod/data/EscalationPlayerData.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.data;

import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EscalationPlayerData implements IEscalationPlayerData {

    private long realPlaytimeTicks = 0L;
    private long nextSpawnAtTick = 0L;
    private int legendaryCountEver = 0;
    private int deathCounter = 0;
    private boolean hasWon = false;
    private boolean endlessMode = false;
    private long mercyPauseEndTick = 0L;

    private final Set<String> seenPoolIds = new LinkedHashSet<>();
    private final List<EscalationInstance> activeInstances = new ArrayList<>();

    @Override
    public long getRealPlaytimeTicks() {
        return realPlaytimeTicks;
    }

    @Override
    public void setRealPlaytimeTicks(long ticks) {
        this.realPlaytimeTicks = ticks;
    }

    @Override
    public void addRealPlaytimeTicks(long ticks) {
        this.realPlaytimeTicks += ticks;
    }

    @Override
    public long getNextSpawnAtTick() {
        return nextSpawnAtTick;
    }

    @Override
    public void setNextSpawnAtTick(long tick) {
        this.nextSpawnAtTick = tick;
    }

    @Override
    public Set<String> getSeenPoolIds() {
        return seenPoolIds;
    }

    @Override
    public List<EscalationInstance> getActiveInstances() {
        return activeInstances;
    }

    @Override
    public int getLegendaryCountEver() {
        return legendaryCountEver;
    }

    @Override
    public void incrementLegendaryCountEver() {
        this.legendaryCountEver++;
    }

    @Override
    public int getDeathCounter() {
        return deathCounter;
    }

    @Override
    public void incrementDeathCounter() {
        this.deathCounter++;
    }

    @Override
    public boolean hasWon() {
        return hasWon;
    }

    @Override
    public void setHasWon(boolean won) {
        this.hasWon = won;
    }

    @Override
    public boolean isEndlessMode() {
        return endlessMode;
    }

    @Override
    public void setEndlessMode(boolean endless) {
        this.endlessMode = endless;
    }

    @Override
    public long getMercyPauseEndTick() {
        return mercyPauseEndTick;
    }

    @Override
    public void setMercyPauseEndTick(long tick) {
        this.mercyPauseEndTick = tick;
    }

    private CompoundTag behaviorState = new CompoundTag();

    @Override
    public CompoundTag getBehaviorState() {
        return behaviorState;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("realPlaytimeTicks", realPlaytimeTicks);
        tag.putLong("nextSpawnAtTick", nextSpawnAtTick);
        tag.putInt("legendaryCountEver", legendaryCountEver);
        tag.putInt("deathCounter", deathCounter);
        tag.putBoolean("hasWon", hasWon);
        tag.putBoolean("endlessMode", endlessMode);
        tag.putLong("mercyPauseEndTick", mercyPauseEndTick);

        ListTag seenTag = new ListTag();
        for (String id : seenPoolIds) {
            seenTag.add(StringTag.valueOf(id));
        }
        tag.put("seenPoolIds", seenTag);

        ListTag instancesTag = new ListTag();
        for (EscalationInstance instance : activeInstances) {
            instancesTag.add(instance.serializeNBT());
        }
        tag.put("activeInstances", instancesTag);
        tag.put("behaviorState", behaviorState.copy());

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.realPlaytimeTicks = tag.getLong("realPlaytimeTicks");
        this.nextSpawnAtTick = tag.getLong("nextSpawnAtTick");
        this.legendaryCountEver = tag.getInt("legendaryCountEver");
        this.deathCounter = tag.getInt("deathCounter");
        this.hasWon = tag.getBoolean("hasWon");
        this.endlessMode = tag.getBoolean("endlessMode");
        this.mercyPauseEndTick = tag.getLong("mercyPauseEndTick");

        seenPoolIds.clear();
        if (tag.contains("seenPoolIds")) {
            ListTag seenTag = tag.getList("seenPoolIds", Tag.TAG_STRING);
            for (int i = 0; i < seenTag.size(); i++) {
                seenPoolIds.add(seenTag.getString(i));
            }
        }

        this.behaviorState = tag.contains("behaviorState", Tag.TAG_COMPOUND)
                ? tag.getCompound("behaviorState").copy() : new CompoundTag();

        activeInstances.clear();
        if (tag.contains("activeInstances")) {
            ListTag instancesTag = tag.getList("activeInstances", Tag.TAG_COMPOUND);
            for (int i = 0; i < instancesTag.size(); i++) {
                activeInstances.add(EscalationInstance.deserializeNBT(instancesTag.getCompound(i)));
            }
        }
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/data/IEscalationPlayerData.java")"
cat > "src/main/java/com/dxrk/escalationmod/data/IEscalationPlayerData.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.data;

import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Set;

public interface IEscalationPlayerData {
    long getRealPlaytimeTicks();
    void setRealPlaytimeTicks(long ticks);
    void addRealPlaytimeTicks(long ticks);

    long getNextSpawnAtTick();
    void setNextSpawnAtTick(long tick);

    Set<String> getSeenPoolIds();
    List<EscalationInstance> getActiveInstances();

    int getLegendaryCountEver();
    void incrementLegendaryCountEver();

    int getDeathCounter();
    void incrementDeathCounter();

    boolean hasWon();
    void setHasWon(boolean won);

    boolean isEndlessMode();
    void setEndlessMode(boolean endless);

    long getMercyPauseEndTick();
    void setMercyPauseEndTick(long tick);

    /** Персистентное состояние behavioral-обработчиков: подтег на каждый pool id (таймеры, счётчики). */
    CompoundTag getBehaviorState();

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/mercy/MercyRuleManager.java")"
cat > "src/main/java/com/dxrk/escalationmod/mercy/MercyRuleManager.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.mercy;

import com.dxrk.escalationmod.behavior.ActiveEscalations;
import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
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

    private static EscalationConfig.MercyRuleSection cfg() {
        return EscalationConfigManager.get().mercyRule;
    }

    private static long pauseDurationTicks() {
        return (long) (cfg().pauseDurationMin * 60.0 * 20.0);
    }

    public static void onDeathBlockOfFive(ServerPlayer player, IEscalationPlayerData data, long currentTick) {
        boolean currentlyPaused = currentTick < data.getMercyPauseEndTick();

        if (!currentlyPaused) {
            startNewPause(player, data, currentTick);
            return;
        }

        long remaining = data.getMercyPauseEndTick() - currentTick;
        long ignoreAboveTicks = (long) (cfg().ignoreIfRemainingMinAbove * 60.0 * 20.0);
        if (remaining > ignoreAboveTicks) {
            LOGGER.info("Escalation Mercy Rule: у {} новый триггер проигнорирован — осталось {} тиков (> порога {})",
                    player.getName().getString(), remaining, ignoreAboveTicks);
            player.sendSystemMessage(Component.literal(
                    "§7🛡 Передышка уже активна, до конца больше "
                            + trimNumber(cfg().ignoreIfRemainingMinAbove) + " минут — новый триггер проигнорирован."));
            return;
        }

        long extended = (long) (remaining * (1.0 + cfg().extendPercentIfBelowOrEqual / 100.0));
        data.setMercyPauseEndTick(currentTick + extended);

        LOGGER.info("Escalation Mercy Rule: у {} пауза продлена — было {} тиков, стало {} тиков (+{}%)",
                player.getName().getString(), remaining, extended, trimNumber(cfg().extendPercentIfBelowOrEqual));

        performRemoval(player, data, true);
    }

    private static void startNewPause(ServerPlayer player, IEscalationPlayerData data, long currentTick) {
        data.setMercyPauseEndTick(currentTick + pauseDurationTicks());

        LOGGER.info("Escalation Mercy Rule: у {} началась передышка на {} мин ({}-я смерть цикла)",
                player.getName().getString(), trimNumber(cfg().pauseDurationMin), cfg().deathsToTrigger);

        performRemoval(player, data, false);
    }

    /**
     * Раздел 9: единица удаления — уникальный id целиком, процент считается от
     * количества уникальных активных id, выбор — FIFO по времени первого появления id.
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

        double minP = cfg().removalPercentMin / 100.0;
        double maxP = cfg().removalPercentMax / 100.0;
        double percent = minP + RNG.nextDouble() * (maxP - minP);
        int removeGroupCount = Math.min(totalUniqueIds, (int) Math.round(totalUniqueIds * percent));

        List<Map.Entry<String, List<EscalationInstance>>> sortedGroups = new ArrayList<>(grouped.entrySet());
        sortedGroups.sort((a, b) -> Long.compare(firstSeen(a.getValue()), firstSeen(b.getValue())));

        Set<String> idsToRemove = new HashSet<>();
        for (int i = 0; i < removeGroupCount; i++) {
            idsToRemove.add(sortedGroups.get(i).getKey());
        }

        int removedInstanceCount = ActiveEscalations.removeIf(player, data, inst -> idsToRemove.contains(inst.poolId));

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

    private static String trimNumber(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/network/ClientBehaviorPacket.java")"
cat > "src/main/java/com/dxrk/escalationmod/network/ClientBehaviorPacket.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.behavior.ClientBehaviorState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Сервер -> клиент: набор "ключ -> число" для клиентской части behavioral-эффектов (шлётся только при изменении). */
public class ClientBehaviorPacket {

    public final Map<String, Float> values;

    public ClientBehaviorPacket(Map<String, Float> values) {
        this.values = values;
    }

    public static void encode(ClientBehaviorPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.values.size());
        for (Map.Entry<String, Float> e : msg.values.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeFloat(e.getValue());
        }
    }

    public static ClientBehaviorPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Float> m = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            m.put(k, buf.readFloat());
        }
        return new ClientBehaviorPacket(m);
    }

    public static void handle(ClientBehaviorPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> ClientBehaviorState.replace(msg.values)));
        ctx.setPacketHandled(true);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/network/EscalationNetwork.java")"
cat > "src/main/java/com/dxrk/escalationmod/network/EscalationNetwork.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.network;

import com.dxrk.escalationmod.Escalation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class EscalationNetwork {

    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Escalation.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++,
                ShowToastPacket.class,
                ShowToastPacket::encode,
                ShowToastPacket::decode,
                ShowToastPacket::handle);
        CHANNEL.registerMessage(id++,
                EscalationHudSyncPacket.class,
                EscalationHudSyncPacket::encode,
                EscalationHudSyncPacket::decode,
                EscalationHudSyncPacket::handle);
        CHANNEL.registerMessage(id++,
                ClientModifiersPacket.class,
                ClientModifiersPacket::encode,
                ClientModifiersPacket::decode,
                ClientModifiersPacket::handle);
        CHANNEL.registerMessage(id++,
                ClientBehaviorPacket.class,
                ClientBehaviorPacket::encode,
                ClientBehaviorPacket::decode,
                ClientBehaviorPacket::handle);
    }

    private EscalationNetwork() {
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/runtime/EscalationScheduler.java")"
cat > "src/main/java/com/dxrk/escalationmod/runtime/EscalationScheduler.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.behavior.ActiveEscalations;
import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.network.EscalationNetwork;
import com.dxrk.escalationmod.network.ShowToastPacket;
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
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class EscalationScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SecureRandom RNG = new SecureRandom();

    private static EscalationConfig cfg() {
        return EscalationConfigManager.get();
    }

    private static TierSelector.Order tierOrder() {
        try {
            return TierSelector.Order.valueOf(cfg().tierProgression.order.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TierSelector.Order.ASCENDING;
        }
    }

    private static int selectTierFor(IEscalationPlayerData data) {
        double elapsedHours = data.getRealPlaytimeTicks() / 20.0 / 3600.0;
        EscalationConfig.TierProgressionSection tp = cfg().tierProgression;
        return TierSelector.selectTier(elapsedHours, tp.totalTargetHours, tierOrder(), tp.jitter);
    }

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
                if (data.hasWon() && !data.isEndlessMode() && cfg().winCondition.stopNewSpawnsAfterWin) {
                    return;
                }
                if (currentTick < data.getMercyPauseEndTick()) {
                    return;
                }
                trySpawn(player, data, currentTick);
                data.setNextSpawnAtTick(currentTick + randomIntervalTicks());
            });
        }
    }

    private static void trySpawn(ServerPlayer player, IEscalationPlayerData data, long currentTick) {
        int tier = selectTierFor(data);

        EscalationDefinition definition = pickDefinition(tier);
        if (definition == null) {
            LOGGER.warn("Escalation: для {} тир {} пуст в пуле — это баг данных", player.getName().getString(), tier);
            return;
        }

        Rarity rarity = RarityRoller.roll(tier);
        applyAndNotify(player, data, tier, rarity, definition, currentTick);
    }

    private static void applyAndNotify(ServerPlayer player, IEscalationPlayerData data,
                                        int tier, Rarity rarity, EscalationDefinition definition, long currentTick) {
        EscalationInstance instance = new EscalationInstance(definition, rarity, currentTick);

        data.getSeenPoolIds().add(definition.id);
        ActiveEscalations.add(player, data, instance);
        if (rarity == Rarity.LEGENDARY) {
            data.incrementLegendaryCountEver();
        }

        player.sendSystemMessage(Component.literal(
                "§e⚠ НОВОЕ УСЛОЖНЕНИЕ [Тир " + tier + " · " + rarity.name() + "] " + definition.name
                        + " — " + definition.description));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);

        EscalationNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ShowToastPacket(tier, rarity.name(), definition.name, definition.description));

        LOGGER.info("Escalation: игроку {} выпало '{}' (id={}, тир={}, редкость={}), легендарок всего: {}",
                player.getName().getString(), definition.name, definition.id, tier, rarity,
                data.getLegendaryCountEver());

        if (rarity == Rarity.LEGENDARY) {
            WinConditionManager.checkWinCondition(player, data);
        }
    }

    public static void debugForceSpawn(ServerPlayer player) {
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            long currentTick = player.level().getGameTime();
            trySpawn(player, data, currentTick);
        });
    }

    public static void debugForceLegendaryToast(ServerPlayer player) {
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            long currentTick = player.level().getGameTime();
            int tier = selectTierFor(data);
            EscalationDefinition definition = pickDefinition(tier);
            if (definition == null) {
                LOGGER.warn("Escalation debug: тир {} пуст в пуле", tier);
                return;
            }
            applyAndNotify(player, data, tier, Rarity.LEGENDARY, definition, currentTick);
        });
    }

    /** Выдать конкретное усложнение с заданной редкостью (debug). @return false если id нет в пуле. */
    public static boolean debugGive(ServerPlayer player, String poolId, Rarity rarity) {
        EscalationDefinition definition = PoolRegistry.getById(poolId);
        if (definition == null) {
            return false;
        }
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data ->
                applyAndNotify(player, data, definition.tier, rarity, definition, player.level().getGameTime()));
        return true;
    }

    /** Назначает следующий спавн через случайный интервал от текущего момента (нужно при включении endless). */
    public static void scheduleNextSpawn(ServerPlayer player, IEscalationPlayerData data) {
        data.setNextSpawnAtTick(player.level().getGameTime() + randomIntervalTicks());
    }

    private static EscalationDefinition pickDefinition(int tier) {
        List<EscalationDefinition> tierPool = PoolRegistry.getByTier(tier);
        if (tierPool.isEmpty()) {
            return null;
        }
        return tierPool.get(RNG.nextInt(tierPool.size()));
    }

    private static long randomIntervalTicks() {
        long minTicks = cfg().timing.intervalMinSec * 20L;
        long maxTicks = cfg().timing.intervalMaxSec * 20L;
        long range = maxTicks - minTicks;
        return minTicks + (long) (RNG.nextDouble() * range);
    }
}
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/stats/StatDebugCommands.java")"
cat > "src/main/java/com/dxrk/escalationmod/stats/StatDebugCommands.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.behavior.ActiveEscalations;
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
                        int n = ActiveEscalations.clear(player, data);
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
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/win/EndlessCommands.java")"
cat > "src/main/java/com/dxrk/escalationmod/win/EndlessCommands.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.win;

import com.dxrk.escalationmod.behavior.ActiveEscalations;
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
                    cleared = ActiveEscalations.clear(player, data);
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
__ESC_EOF__
mkdir -p "$(dirname "src/main/java/com/dxrk/escalationmod/win/WinConditionManager.java")"
cat > "src/main/java/com/dxrk/escalationmod/win/WinConditionManager.java" << '__ESC_EOF__'
package com.dxrk.escalationmod.win;

import com.dxrk.escalationmod.behavior.ActiveEscalations;
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
            clearedCount = ActiveEscalations.clear(player, data);
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
__ESC_EOF__
mkdir -p "$(dirname "src/main/resources/escalation_default_behaviors.json")"
cat > "src/main/resources/escalation_default_behaviors.json" << '__ESC_EOF__'
{
 "version": 1,
 "_help": "Встроенные реализации behavioral/flavor. Править лучше через config/escalation/behaviors_override.json.",
 "behaviors": {
  "escalation_hand_0007": {
   "template": "trigger_action",
   "note": "5%: свежескрафченный инструмент/оружие сразу теряет 1 прочность",
   "trigger": "item_crafted",
   "chance": 5,
   "filters": {
    "item": "tool_or_weapon"
   },
   "actions": [
    {
     "type": "item_damage",
     "amount": 1,
     "sound": "minecraft:entity.item.break",
     "volume": 0.4
    }
   ]
  },
  "escalation_hand_0010": {
   "template": "trigger_action",
   "note": "15%: кровать не даёт заснуть",
   "trigger": "sleep_attempt",
   "chance": 15,
   "actions": [
    {
     "type": "cancel_sleep",
     "message": "Не получается заснуть..."
    }
   ]
  },
  "escalation_hand_0011": {
   "template": "trigger_action",
   "note": "2%: при получении урона выпадает один предмет из инвентаря",
   "trigger": "player_hurt",
   "chance": 2,
   "actions": [
    {
     "type": "drop_item"
    }
   ]
  },
  "escalation_hand_0021": {
   "template": "trigger_action",
   "note": "25% при добыче руды: отравление на 4 с (газ/пыль)",
   "trigger": "block_break",
   "chance": 25,
   "filters": {
    "block": "ore"
   },
   "actions": [
    {
     "type": "effect",
     "effect": "minecraft:poison",
     "seconds": 4,
     "amplifier": 0,
     "message": "Пыль и газ в шахте..."
    }
   ]
  },
  "escalation_hand_0035": {
   "template": "trigger_action",
   "note": "6% при рубке дерева: падающая ветка, 2 ед. урона",
   "trigger": "block_break",
   "chance": 6,
   "filters": {
    "block": "log"
   },
   "actions": [
    {
     "type": "damage",
     "amount": 2,
     "message": "Ветка упала вам на голову!"
    }
   ]
  },
  "escalation_hand_0036": {
   "template": "trigger_action",
   "note": "при смерти каждый предмет с шансом 30% сгорает безвозвратно",
   "trigger": "death_drops",
   "chance": 30,
   "actions": [
    {
     "type": "destroy_item"
    }
   ]
  },
  "escalation_hand_0051": {
   "template": "trigger_action",
   "note": "3% при уроне >= 8: случайная надетая вещь ломается навсегда",
   "trigger": "player_hurt",
   "chance": 3,
   "filters": {
    "damage_min": 8
   },
   "actions": [
    {
     "type": "break_equipped",
     "message": "Критический удар — что-то сломалось навсегда!"
    }
   ]
  },
  "escalation_hand_0061": {
   "template": "trigger_action",
   "note": "10% под дождём: кресало не поджигает",
   "trigger": "right_click_block",
   "chance": 10,
   "conditions": [
    "raining"
   ],
   "filters": {
    "item": "flint_and_steel"
   },
   "actions": [
    {
     "type": "cancel",
     "sound": "minecraft:block.fire.extinguish",
     "message": "Спички отсырели"
    }
   ]
  },
  "escalation_hand_0065": {
   "template": "trigger_action",
   "note": "3%: сундук заедает при открытии, нужен повторный клик",
   "trigger": "right_click_block",
   "chance": 3,
   "filters": {
    "block": "chest"
   },
   "actions": [
    {
     "type": "cancel",
     "sound": "minecraft:block.wooden_trapdoor.close",
     "message": "Крышка заела"
    }
   ]
  },
  "escalation_hand_0116": {
   "template": "trigger_action",
   "note": "7%: рычаг/кнопка залипает",
   "trigger": "right_click_block",
   "chance": 7,
   "filters": {
    "block": "lever_or_button"
   },
   "actions": [
    {
     "type": "cancel",
     "sound": "minecraft:block.lever.click",
     "pitch": 0.6,
     "message": "Залипло"
    }
   ]
  },
  "escalation_hand_0069": {
   "template": "trigger_action",
   "note": "любая операция на наковальне стоит на 1 уровень опыта больше (списывается при получении результата)",
   "trigger": "anvil_use",
   "actions": [
    {
     "type": "xp_levels",
     "amount": -1,
     "message": "Наковальня берёт лишний уровень"
    }
   ]
  },
  "escalation_hand_0086": {
   "template": "trigger_action",
   "note": "комбинирование зачарованных книг стоит на 1 уровень больше (списывается при получении результата)",
   "trigger": "anvil_use",
   "filters": {
    "both_enchanted_books": true
   },
   "actions": [
    {
     "type": "xp_levels",
     "amount": -1,
     "message": "Дорогие книги: -1 уровень"
    }
   ]
  },
  "escalation_hand_0153": {
   "template": "trigger_action",
   "note": "3+ игровых суток без сна: каждые 45 с тошнота 6 с",
   "trigger": "periodic",
   "period_sec": 45,
   "conditions": [
    "no_sleep_days:3"
   ],
   "actions": [
    {
     "type": "effect",
     "effect": "minecraft:nausea",
     "seconds": 6,
     "amplifier": 0
    }
   ]
  },
  "escalation_hand_0104": {
   "template": "trigger_action",
   "note": "грибные леса Нижнего мира: каждые 30 с тошнота 5 с",
   "trigger": "periodic",
   "period_sec": 30,
   "conditions": [
    "in_nether_forest"
   ],
   "actions": [
    {
     "type": "effect",
     "effect": "minecraft:nausea",
     "seconds": 5,
     "amplifier": 0
    }
   ]
  },
  "escalation_hand_0144": {
   "template": "trigger_action",
   "note": "грибные леса Нижнего мира без шлема: замедление",
   "trigger": "periodic",
   "period_sec": 10,
   "conditions": [
    "in_nether_forest",
    "no_helmet"
   ],
   "actions": [
    {
     "type": "effect",
     "effect": "minecraft:slowness",
     "seconds": 8,
     "amplifier": 0
    }
   ]
  },
  "escalation_hand_0155": {
   "template": "trigger_action",
   "note": "утром на траве без обуви: лёгкое замедление",
   "trigger": "periodic",
   "period_sec": 2,
   "conditions": [
    "morning",
    "on_grass",
    "no_boots"
   ],
   "actions": [
    {
     "type": "effect",
     "effect": "minecraft:slowness",
     "seconds": 3,
     "amplifier": 0
    }
   ]
  },
  "escalation_hand_0904": {
   "template": "trigger_action",
   "note": "полдень в бэдлендах под открытым небом: слабость (перегрев)",
   "trigger": "periodic",
   "period_sec": 5,
   "conditions": [
    "in_badlands",
    "noon",
    "outdoors"
   ],
   "actions": [
    {
     "type": "effect",
     "effect": "minecraft:weakness",
     "seconds": 6,
     "amplifier": 0
    }
   ]
  },
  "escalation_hand_0033": {
   "template": "trigger_action",
   "note": "раз в игровые сутки (20 мин реального времени) на 150 с резко темнеет (эффект Тьмы)",
   "trigger": "periodic",
   "period_sec": 1200,
   "conditions": [
    "overworld"
   ],
   "actions": [
    {
     "type": "effect",
     "effect": "minecraft:darkness",
     "seconds": 150,
     "amplifier": 0,
     "message": "Среди дня наступает тьма..."
    }
   ]
  },
  "escalation_hand_0017": {
   "template": "trigger_action",
   "note": "каждые 5 мин при ясной погоде с шансом 35% начинается дождь на 10 мин",
   "trigger": "periodic",
   "period_sec": 300,
   "chance": 35,
   "conditions": [
    "overworld",
    "clear_weather"
   ],
   "actions": [
    {
     "type": "rain",
     "duration_sec": 600
    }
   ]
  },
  "escalation_hand_0003": {
   "template": "custom_slippery_blocks",
   "note": "5% обычных блоков скользят как лёд (клиентский эффект)",
   "chance": 5
  },
  "escalation_hand_0048": {
   "template": "custom_alarm",
   "note": "раз в 3 ч на 5 мин все враждебные мобы в радиусе 64 идут на игрока",
   "period_sec": 10800,
   "duration_sec": 300,
   "radius": 64,
   "first_delay_sec": 3600
  },
  "escalation_hand_0131": {
   "template": "custom_dark_paranoia",
   "note": "5+ мин без света: фальшивые шаги",
   "dark_seconds": 300,
   "light_level": 4,
   "chance": 4
  },
  "escalation_hand_0178": {
   "template": "custom_loneliness",
   "note": "час без живых существ рядом: слабость",
   "alone_seconds": 3600,
   "radius": 32,
   "effect": "minecraft:weakness",
   "amplifier": 0
  },
  "escalation_hand_0038": {
   "template": "spawn_elite",
   "note": "раз в час элитный охотник идёт к игроку",
   "entity": "minecraft:zombie",
   "period_sec": 3600,
   "first_delay_sec": 1800,
   "stack_period": true,
   "min_period_sec": 600,
   "cap": 2,
   "health_mult": 2.5,
   "damage_mult": 1.5,
   "speed_mult": 1.1,
   "armor": "iron",
   "weapon": "iron_sword",
   "glowing": true,
   "name": "Охотник",
   "message": "§c☠ Охота началась: по вашему следу идёт Охотник!"
  },
  "escalation_hand_0245": {
   "template": "spawn_elite",
   "note": "уникальный зомби-чемпион в золотой броне преследует игрока",
   "entity": "minecraft:zombie",
   "period_sec": 5400,
   "first_delay_sec": 3600,
   "stack_period": false,
   "cap": 1,
   "health_mult": 3.0,
   "damage_mult": 1.3,
   "speed_mult": 1.05,
   "armor": "golden",
   "weapon": "golden_sword",
   "follow_range": 96,
   "name": "Позолоченный преследователь",
   "message": "§6☠ Золотой преследователь учуял вас."
  },
  "escalation_hand_0053": {
   "template": "spawn_elite",
   "note": "раз в несколько часов тёмный клон игрока с его экипировкой",
   "entity": "minecraft:zombie",
   "period_sec": 10800,
   "first_delay_sec": 5400,
   "stack_period": false,
   "cap": 1,
   "health_mult": 1.5,
   "damage_mult": 1.5,
   "speed_mult": 1.15,
   "armor": "copy_player",
   "glowing": false,
   "name": "Двойник",
   "message": "§8☠ Ваш двойник вышел на охоту."
  },
  "escalation_hand_0310": {
   "template": "spawn_elite",
   "note": "редко: копия игрока с текущим инвентарём — дуэль с собой",
   "entity": "minecraft:zombie",
   "period_sec": 9000,
   "first_delay_sec": 4500,
   "stack_period": false,
   "cap": 1,
   "health_mult": 2.0,
   "damage_mult": 1.4,
   "speed_mult": 1.1,
   "armor": "copy_player",
   "name": "Тёмное отражение",
   "message": "§5☠ Тёмное отражение ищет вас."
  },
  "escalation_hand_0455": {
   "template": "villager_price",
   "note": "рыбаки продают удочки на 10% дороже",
   "profession": "fisherman",
   "result": [
    "fishing_rod"
   ],
   "percent": 10
  },
  "escalation_hand_0460": {
   "template": "villager_price",
   "note": "кузнецы-бронники продают зачарованную броню дороже на 1",
   "profession": "armorer",
   "result": [
    "enchanted_armor"
   ],
   "flat": 1
  },
  "escalation_hand_0470": {
   "template": "villager_price",
   "note": "пастухи продают ножницы и шерсть на 10% дороже",
   "profession": "shepherd",
   "result": [
    "contains:shears",
    "contains:wool"
   ],
   "percent": 10
  },
  "escalation_hand_0473": {
   "template": "villager_price",
   "note": "библиотекари продают зачарованные книги на 1 изумруд дороже",
   "profession": "librarian",
   "result": [
    "enchanted_book"
   ],
   "flat": 1
  },
  "escalation_hand_0497": {
   "template": "villager_price",
   "note": "инструментальщики продают алмазные кирки на 1 изумруд дороже",
   "profession": "toolsmith",
   "result": [
    "diamond_pickaxe"
   ],
   "flat": 1
  },
  "escalation_hand_0772": {
   "template": "villager_price",
   "note": "каменщики продают кирпичи и терракоту на 10% дороже в полнолуние",
   "profession": "mason",
   "result": [
    "contains:brick",
    "contains:terracotta"
   ],
   "percent": 10,
   "conditions": [
    "full_moon"
   ]
  },
  "escalation_hand_0774": {
   "template": "villager_price",
   "note": "оперщики продают стрелы на 10% дороже, если недавно был бой",
   "profession": "fletcher",
   "result": [
    "contains:arrow"
   ],
   "percent": 10,
   "conditions": [
    "recent_combat"
   ]
  },
  "escalation_hand_0781": {
   "template": "villager_price",
   "note": "каменщики продают мозаичные блоки на 10% дороже в дождь",
   "profession": "mason",
   "result": [
    "contains:chiseled"
   ],
   "percent": 10,
   "conditions": [
    "raining"
   ]
  }
 }
}
__ESC_EOF__
echo "Готово: записано 38 файлов"

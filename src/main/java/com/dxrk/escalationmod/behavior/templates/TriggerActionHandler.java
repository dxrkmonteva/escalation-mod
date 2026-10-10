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

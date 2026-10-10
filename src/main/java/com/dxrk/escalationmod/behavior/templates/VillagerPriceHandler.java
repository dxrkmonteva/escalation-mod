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

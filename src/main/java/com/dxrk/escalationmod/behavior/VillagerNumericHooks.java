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

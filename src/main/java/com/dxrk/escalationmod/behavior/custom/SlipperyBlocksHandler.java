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

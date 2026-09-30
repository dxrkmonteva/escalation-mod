package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.pool.EscalationDefinition;
import com.dxrk.escalationmod.pool.PoolRegistry;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import com.dxrk.escalationmod.runtime.Rarity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Проверка мат-слоя без игры — результат в логе runServer ("[STAT TEST] ... OK/FAIL").
 * Ожидания считаются от текущего конфига; числа записей (0001/0002/0004/0025/0129) — из value.json.
 * Временный класс: можно убрать, когда мат-слой устоится.
 */
public class StatEngineSmokeTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static int failed = 0;

    public static void run() {
        try {
            runInternal();
        } catch (Exception e) {
            LOGGER.error("Escalation [STAT TEST]: тест упал с исключением", e);
        }
    }

    private static void runInternal() {
        failed = 0;
        EscalationConfig cfg = EscalationConfigManager.get();
        double legendary = Rarity.LEGENDARY.getBaseMultiplier();
        double common = Rarity.COMMON.getBaseMultiplier();
        double fullEsc = 1.0 + cfg.rarity.legendaryEscalation.perSecond * cfg.rarity.legendaryEscalation.durationSec;
        double clamp = cfg.stacking.percentStatClamp;
        double cap = cfg.stacking.engineSafetyCap;

        LOGGER.info("Escalation [STAT TEST]: проверка мат-слоя раздела 7 (клэмп {}%, потолок {})", clamp, cap);

        // 1. три common id 0004 (night_vision_duration, 20%): 0.8^3
        check("3×common 0004 (снижение, без клэмпа)",
                factorOf(list(3, 4, Rarity.COMMON, false), "night_vision_duration"),
                Math.pow(1.0 - 20.0 * common / 100.0, 3));

        // 2. одна legendary 0004 на полной эскалации: 20×4×7=560% -> клэмп 99.9% -> ×0.001
        check("1×legendary(full) 0004 (клэмп 99.9%)",
                factorOf(list(1, 4, Rarity.LEGENDARY, true), "night_vision_duration"),
                1.0 - clamp / 100.0);

        // 3. две legendary 0001 в момент спавна (15%×4=60%): 1.6^2
        check("2×legendary(start) 0001 (множитель без клэмпа)",
                factorOf(list(2, 1, Rarity.LEGENDARY, false), "tool_durability_loss_rate"),
                Math.pow(1.0 + 15.0 * legendary / 100.0, 2));

        // 4. две legendary 0001 на полной эскалации (15%×28=420%): 5.2^2
        check("2×legendary(full) 0001",
                factorOf(list(2, 1, Rarity.LEGENDARY, true), "tool_durability_loss_rate"),
                Math.pow(1.0 + 15.0 * legendary * fullEsc / 100.0, 2));

        // 5. 30 legendary(full) 0025 (30%) -> должен упереться в инженерный потолок
        check("30×legendary(full) 0025 (потолок)",
                factorOf(list(30, 25, Rarity.LEGENDARY, true), "mob_spawn_density_night"),
                cap);

        // 6. разные статы не смешиваются: 0001 и 0002 по одному common
        List<EscalationInstance> mixed = new ArrayList<>();
        mixed.addAll(list(1, 1, Rarity.COMMON, false));
        mixed.addAll(list(1, 2, Rarity.COMMON, false));
        Map<String, StatEngine.StatResult> mixedRes = StatEngine.compute(mixed);
        check("разные статы независимы: durability", factor(mixedRes, "tool_durability_loss_rate"), 1.0 + 15.0 * common / 100.0);
        check("разные статы независимы: hunger", factor(mixedRes, "hunger_depletion_rate"), 1.0 + 10.0 * common / 100.0);

        // 7. unit != percent (0129, %/игровой час) — в множитель не входит, но копится в rawSum
        Map<String, StatEngine.StatResult> raw = StatEngine.compute(list(1, 129, Rarity.COMMON, false));
        StatEngine.StatResult r129 = raw.get("villager_price_inflation_per_game_hour");
        check("unit != percent: множитель нейтрален", r129 == null ? -1 : r129.factor(), 1.0);
        check("unit != percent: rawSum", r129 == null ? -1 : r129.rawSum, 1.0 * common);

        if (failed == 0) {
            LOGGER.info("Escalation [STAT TEST]: ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ");
        } else {
            LOGGER.error("Escalation [STAT TEST]: провалено проверок: {}", failed);
        }
    }

    private static double factor(Map<String, StatEngine.StatResult> res, String key) {
        StatEngine.StatResult r = res.get(key);
        return r == null ? 1.0 : r.factor();
    }

    private static double factorOf(List<EscalationInstance> instances, String key) {
        return factor(StatEngine.compute(instances), key);
    }

    private static List<EscalationInstance> list(int count, int poolNumber, Rarity rarity, boolean fullEscalation) {
        EscalationConfig.LegendaryEscalationSection esc = EscalationConfigManager.get().rarity.legendaryEscalation;
        EscalationDefinition def = PoolRegistry.getById(String.format("escalation_hand_%04d", poolNumber));
        if (def == null) {
            throw new IllegalStateException("в пуле нет escalation_hand_" + poolNumber);
        }
        List<EscalationInstance> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EscalationInstance inst = new EscalationInstance(def, rarity, 0L);
            if (fullEscalation) {
                for (int s = 0; s < esc.durationSec + 2; s++) {
                    inst.tickEscalation();
                }
            }
            out.add(inst);
        }
        return out;
    }

    private static void check(String name, double actual, double expected) {
        double tolerance = Math.max(1.0E-9, Math.abs(expected) * 1.0E-9);
        boolean ok = Math.abs(actual - expected) <= tolerance;
        if (!ok) {
            failed++;
        }
        LOGGER.info("Escalation [STAT TEST]: {} -> {} (ожидалось {}) {}", name, actual, expected, ok ? "OK" : "FAIL");
    }
}

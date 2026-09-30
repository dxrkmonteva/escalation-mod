package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.data.EscalationCapabilities;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import com.dxrk.escalationmod.value.EscalationValueEntry;
import com.dxrk.escalationmod.value.ValueRegistry;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * Разделы 7 и 11: мат-слой стакинга + кэш.
 *
 * Для каждого активного инстанса берётся запись value.json: e = base_value × редкость × эскалация (в %).
 *  - percent_stat_clamped:      множитель *= (1 - min(e, clamp)/100), итог не ниже (1 - clamp/100)
 *  - direct_multiplier_uncapped: множитель *= (1 + e/100), потолок — инженерный (engineSafetyCap)
 * Записи с unit != "percent" (шансы, "в час") в множитель не входят — их сумма доступна через rawSum().
 *
 * Кэш на игрока: пересчитывается, когда изменился список (размер / id последнего инстанса)
 * или сработал invalidate() (ежесекундный рост эскалации легендарок).
 */
public final class StatEngine {

    public static final String CLASS_PERCENT_CLAMPED = "percent_stat_clamped";
    public static final String CLASS_DIRECT_UNCAPPED = "direct_multiplier_uncapped";
    public static final String UNIT_PERCENT = "percent";

    private static final Map<UUID, PlayerCache> CACHES = new HashMap<>();

    private StatEngine() {
    }

    public static final class StatResult {
        public double directProduct = 1.0;
        public double percentProduct = 1.0;
        public double rawSum = 0.0;
        public int instances = 0;

        public double factor() {
            EscalationConfig.StackingSection s = EscalationConfigManager.get().stacking;
            double minPercent = 1.0 - s.percentStatClamp / 100.0;
            double p = Math.max(percentProduct, minPercent);
            return Math.min(s.engineSafetyCap, directProduct * p);
        }
    }

    private static final class PlayerCache {
        int size = -1;
        String lastInstanceId = null;
        Map<String, StatResult> stats = new HashMap<>();
    }

    /** Чистая функция: считает все статы по списку инстансов (используется и кэшем, и смоук-тестом). */
    public static Map<String, StatResult> compute(List<EscalationInstance> active) {
        EscalationConfig.StackingSection s = EscalationConfigManager.get().stacking;
        Map<String, StatResult> result = new HashMap<>();

        for (EscalationInstance inst : active) {
            EscalationValueEntry v = ValueRegistry.getById(inst.poolId);
            if (v.affected_stat == null || v.base_value == null) {
                continue;
            }

            StatResult r = result.computeIfAbsent(v.affected_stat, k -> new StatResult());
            r.instances++;

            double e = v.base_value * inst.getEffectiveMultiplier();

            if (!UNIT_PERCENT.equals(v.unit)) {
                r.rawSum += e;
                continue;
            }

            if (CLASS_PERCENT_CLAMPED.equals(v.stacking_class)) {
                double reduction = Math.min(e, s.percentStatClamp);
                r.percentProduct *= (1.0 - reduction / 100.0);
            } else if (CLASS_DIRECT_UNCAPPED.equals(v.stacking_class)) {
                r.directProduct = Math.min(s.engineSafetyCap, r.directProduct * (1.0 + e / 100.0));
            }
        }
        return result;
    }

    /** Итоговый множитель стата для игрока (1.0 — нет эффекта). */
    public static double factor(Player player, String statKey) {
        StatResult r = result(player, statKey);
        return r == null ? 1.0 : r.factor();
    }

    /** Сумма "сырых" значений для записей с unit != percent (шанс %, %/час и т.п.). */
    public static double rawSum(Player player, String statKey) {
        StatResult r = result(player, statKey);
        return r == null ? 0.0 : r.rawSum;
    }

    public static List<String> describe(Player player) {
        List<String> lines = new ArrayList<>();
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data -> {
            PlayerCache c = cacheFor(player.getUUID(), data.getActiveInstances());
            c.stats.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(en -> {
                        StatResult r = en.getValue();
                        StringBuilder sb = new StringBuilder(en.getKey()).append(": ×").append(fmt(r.factor()));
                        if (r.rawSum != 0.0) {
                            sb.append("  [Σ ").append(fmt(r.rawSum)).append("]");
                        }
                        sb.append("  (").append(r.instances).append(" шт.)");
                        lines.add(sb.toString());
                    });
        });
        return lines;
    }

    /** Сбросить кэш игрока — пересчитается при следующем обращении. */
    public static void invalidate(UUID playerId) {
        PlayerCache c = CACHES.get(playerId);
        if (c != null) {
            c.size = -1;
        }
    }

    public static void forget(UUID playerId) {
        CACHES.remove(playerId);
    }

    private static StatResult result(Player player, String statKey) {
        StatResult[] out = new StatResult[1];
        player.getCapability(EscalationCapabilities.PLAYER_DATA).ifPresent(data ->
                out[0] = cacheFor(player.getUUID(), data.getActiveInstances()).stats.get(statKey));
        return out[0];
    }

    private static PlayerCache cacheFor(UUID id, List<EscalationInstance> active) {
        PlayerCache c = CACHES.computeIfAbsent(id, k -> new PlayerCache());
        String lastId = active.isEmpty() ? null : active.get(active.size() - 1).instanceId;
        if (c.size != active.size() || !Objects.equals(c.lastInstanceId, lastId)) {
            c.stats = compute(active);
            c.size = active.size();
            c.lastInstanceId = lastId;
        }
        return c;
    }

    private static String fmt(double v) {
        if (Math.abs(v) >= 1.0E6 || (v != 0.0 && Math.abs(v) < 1.0E-3)) {
            return String.format(Locale.ROOT, "%.3e", v);
        }
        return String.format(Locale.ROOT, "%.4f", v);
    }
}

package com.dxrk.escalationmod.value;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;

public class ValueRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EscalationValueEntry UNKNOWN_FALLBACK = fallback();

    private static Map<String, EscalationValueEntry> byId = Collections.emptyMap();
    private static final Set<String> warnedMissing = new HashSet<>();

    public static void init() {
        List<EscalationValueEntry> all = ValueLoader.loadValues();
        byId = new HashMap<>();
        for (EscalationValueEntry v : all) {
            if (v.id == null) continue;
            byId.put(v.id, v);
        }
        LOGGER.info("Escalation: реестр value.json готов, размечено {} записей (из 1208 в полном пуле).", byId.size());
    }

    /**
     * Пока value.json размечен не полностью (сейчас только батч 1, 0001-0350) —
     * для id вне размеченного диапазона возвращается безопасный поведенческий
     * фолбэк вместо null, чтобы остальной код не падал. В лог пишется один раз на id.
     */
    public static EscalationValueEntry getById(String id) {
        EscalationValueEntry entry = byId.get(id);
        if (entry != null) {
            return entry;
        }
        if (warnedMissing.add(id)) {
            LOGGER.warn("Escalation: для {} ещё нет записи в value.json (следующие батчи) — используется поведенческий фолбэк.", id);
        }
        return UNKNOWN_FALLBACK;
    }

    public static int size() {
        return byId.size();
    }

    private static EscalationValueEntry fallback() {
        EscalationValueEntry e = new EscalationValueEntry();
        e.stacking_class = "behavioral";
        e.notes = "не размечено в value.json";
        return e;
    }
}

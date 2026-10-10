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

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

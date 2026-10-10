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

package com.dxrk.escalationmod.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Данные одного срабатывания триггера для одной группы + флаги "что сделать с событием". */
public final class BehaviorContext {

    public final Trigger trigger;
    public final ServerPlayer player;
    public final BehaviorGroup group;

    public BlockState block;
    public BlockPos pos;
    public ItemStack stack = ItemStack.EMPTY;
    public ItemStack stack2 = ItemStack.EMPTY;
    public float amount;
    public LivingEntity other;
    public ItemEntity drop;
    public AbstractVillager villager;

    /** Выходные флаги: диспетчер применяет их к исходному событию Forge. */
    public boolean cancel;
    public boolean cancelSleep;
    public boolean removeDrop;

    public BehaviorContext(Trigger trigger, ServerPlayer player, BehaviorGroup group) {
        this.trigger = trigger;
        this.player = player;
        this.group = group;
    }
}

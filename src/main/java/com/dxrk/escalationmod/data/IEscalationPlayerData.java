package com.dxrk.escalationmod.data;

import net.minecraft.nbt.CompoundTag;

public interface IEscalationPlayerData {
    long getRealPlaytimeTicks();
    void setRealPlaytimeTicks(long ticks);
    void addRealPlaytimeTicks(long ticks);

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}

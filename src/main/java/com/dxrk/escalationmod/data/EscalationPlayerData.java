package com.dxrk.escalationmod.data;

import net.minecraft.nbt.CompoundTag;

public class EscalationPlayerData implements IEscalationPlayerData {

    private long realPlaytimeTicks = 0L;

    @Override
    public long getRealPlaytimeTicks() {
        return realPlaytimeTicks;
    }

    @Override
    public void setRealPlaytimeTicks(long ticks) {
        this.realPlaytimeTicks = ticks;
    }

    @Override
    public void addRealPlaytimeTicks(long ticks) {
        this.realPlaytimeTicks += ticks;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("realPlaytimeTicks", realPlaytimeTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.realPlaytimeTicks = tag.getLong("realPlaytimeTicks");
    }
}

package com.dxrk.escalationmod.data;

import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Set;

public interface IEscalationPlayerData {
    long getRealPlaytimeTicks();
    void setRealPlaytimeTicks(long ticks);
    void addRealPlaytimeTicks(long ticks);

    long getNextSpawnAtTick();
    void setNextSpawnAtTick(long tick);

    Set<String> getSeenPoolIds();
    List<EscalationInstance> getActiveInstances();

    int getLegendaryCountEver();
    void incrementLegendaryCountEver();

    int getDeathCounter();
    void incrementDeathCounter();

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}

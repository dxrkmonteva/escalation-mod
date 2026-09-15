package com.dxrk.escalationmod.data;

import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EscalationPlayerData implements IEscalationPlayerData {

    private long realPlaytimeTicks = 0L;
    private long nextSpawnAtTick = 0L;
    private int legendaryCountEver = 0;
    private int deathCounter = 0;

    private final Set<String> seenPoolIds = new LinkedHashSet<>();
    private final List<EscalationInstance> activeInstances = new ArrayList<>();

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
    public long getNextSpawnAtTick() {
        return nextSpawnAtTick;
    }

    @Override
    public void setNextSpawnAtTick(long tick) {
        this.nextSpawnAtTick = tick;
    }

    @Override
    public Set<String> getSeenPoolIds() {
        return seenPoolIds;
    }

    @Override
    public List<EscalationInstance> getActiveInstances() {
        return activeInstances;
    }

    @Override
    public int getLegendaryCountEver() {
        return legendaryCountEver;
    }

    @Override
    public void incrementLegendaryCountEver() {
        this.legendaryCountEver++;
    }

    @Override
    public int getDeathCounter() {
        return deathCounter;
    }

    @Override
    public void incrementDeathCounter() {
        this.deathCounter++;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("realPlaytimeTicks", realPlaytimeTicks);
        tag.putLong("nextSpawnAtTick", nextSpawnAtTick);
        tag.putInt("legendaryCountEver", legendaryCountEver);
        tag.putInt("deathCounter", deathCounter);

        ListTag seenTag = new ListTag();
        for (String id : seenPoolIds) {
            seenTag.add(StringTag.valueOf(id));
        }
        tag.put("seenPoolIds", seenTag);

        ListTag instancesTag = new ListTag();
        for (EscalationInstance instance : activeInstances) {
            instancesTag.add(instance.serializeNBT());
        }
        tag.put("activeInstances", instancesTag);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.realPlaytimeTicks = tag.getLong("realPlaytimeTicks");
        this.nextSpawnAtTick = tag.getLong("nextSpawnAtTick");
        this.legendaryCountEver = tag.getInt("legendaryCountEver");
        this.deathCounter = tag.getInt("deathCounter");

        seenPoolIds.clear();
        if (tag.contains("seenPoolIds")) {
            ListTag seenTag = tag.getList("seenPoolIds", Tag.TAG_STRING);
            for (int i = 0; i < seenTag.size(); i++) {
                seenPoolIds.add(seenTag.getString(i));
            }
        }

        activeInstances.clear();
        if (tag.contains("activeInstances")) {
            ListTag instancesTag = tag.getList("activeInstances", Tag.TAG_COMPOUND);
            for (int i = 0; i < instancesTag.size(); i++) {
                activeInstances.add(EscalationInstance.deserializeNBT(instancesTag.getCompound(i)));
            }
        }
    }
}

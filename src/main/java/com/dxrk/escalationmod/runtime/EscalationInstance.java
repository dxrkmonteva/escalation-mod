package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.pool.EscalationDefinition;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class EscalationInstance {

    public final String instanceId;
    public final String poolId;
    public final Rarity rarity;
    public final long spawnedAtTick;
    public final String stackMode;

    public boolean escalationActive;
    public double escalationPerSecond;
    public int escalationDurationSec;
    public int escalationElapsedSec;
    public double currentMultiplier;

    public EscalationInstance(EscalationDefinition definition, Rarity rarity, long spawnedAtTick) {
        this.instanceId = "inst_" + UUID.randomUUID().toString().substring(0, 8);
        this.poolId = definition.id;
        this.rarity = rarity;
        this.spawnedAtTick = spawnedAtTick;
        this.stackMode = definition.stackMode;

        if (rarity == Rarity.LEGENDARY) {
            this.escalationActive = true;
            this.escalationPerSecond = 0.2;
            this.escalationDurationSec = 30;
        } else {
            this.escalationActive = false;
            this.escalationPerSecond = 0.0;
            this.escalationDurationSec = 0;
        }
        this.escalationElapsedSec = 0;
        this.currentMultiplier = 1.0;
    }

    private EscalationInstance(String instanceId, String poolId, Rarity rarity, long spawnedAtTick, String stackMode) {
        this.instanceId = instanceId;
        this.poolId = poolId;
        this.rarity = rarity;
        this.spawnedAtTick = spawnedAtTick;
        this.stackMode = stackMode;
    }

    public void tickEscalation() {
        if (!escalationActive) {
            return;
        }
        if (escalationElapsedSec < escalationDurationSec) {
            escalationElapsedSec++;
        }
        currentMultiplier = 1.0 + escalationPerSecond * Math.min(escalationElapsedSec, escalationDurationSec);
    }

    public double getEffectiveMultiplier() {
        return rarity.getBaseMultiplier() * currentMultiplier;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("instanceId", instanceId);
        tag.putString("poolId", poolId);
        tag.putString("rarity", rarity.name());
        tag.putLong("spawnedAtTick", spawnedAtTick);
        tag.putString("stackMode", stackMode == null ? "multiplicative" : stackMode);
        tag.putBoolean("escalationActive", escalationActive);
        tag.putDouble("escalationPerSecond", escalationPerSecond);
        tag.putInt("escalationDurationSec", escalationDurationSec);
        tag.putInt("escalationElapsedSec", escalationElapsedSec);
        tag.putDouble("currentMultiplier", currentMultiplier);
        return tag;
    }

    public static EscalationInstance deserializeNBT(CompoundTag tag) {
        EscalationInstance instance = new EscalationInstance(
                tag.getString("instanceId"),
                tag.getString("poolId"),
                Rarity.valueOf(tag.getString("rarity")),
                tag.getLong("spawnedAtTick"),
                tag.getString("stackMode")
        );
        instance.escalationActive = tag.getBoolean("escalationActive");
        instance.escalationPerSecond = tag.getDouble("escalationPerSecond");
        instance.escalationDurationSec = tag.getInt("escalationDurationSec");
        instance.escalationElapsedSec = tag.getInt("escalationElapsedSec");
        instance.currentMultiplier = tag.getDouble("currentMultiplier");
        return instance;
    }
}

package com.dxrk.escalationmod.runtime;

import com.dxrk.escalationmod.pool.EscalationDefinition;

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
}

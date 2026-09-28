package com.dxrk.escalationmod.dimension;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.security.SecureRandom;

public class EscalationWorldData extends SavedData {

    private static final String DATA_NAME = "escalation_world_data";
    private static final SecureRandom RNG = new SecureRandom();

    private double endRealHoursThreshold;

    /** Вызывается один раз при создании мира — диапазон берётся из конфига (dimensionLock.endRealHoursMin/Max). */
    public EscalationWorldData() {
        EscalationConfig.DimensionLockSection dl = EscalationConfigManager.get().dimensionLock;
        this.endRealHoursThreshold = dl.endRealHoursMin + RNG.nextDouble() * (dl.endRealHoursMax - dl.endRealHoursMin);
    }

    public double getEndRealHoursThreshold() {
        return endRealHoursThreshold;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putDouble("endRealHoursThreshold", endRealHoursThreshold);
        return tag;
    }

    public static EscalationWorldData load(CompoundTag tag) {
        EscalationWorldData data = new EscalationWorldData();
        data.endRealHoursThreshold = tag.getDouble("endRealHoursThreshold");
        return data;
    }

    public static EscalationWorldData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                EscalationWorldData::load,
                EscalationWorldData::new,
                DATA_NAME
        );
    }
}

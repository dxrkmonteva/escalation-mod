package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.data.IEscalationPlayerData;
import com.dxrk.escalationmod.runtime.EscalationInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * Все активные экземпляры ОДНОГО pool id у игрока (×23 одинаковых = одна группа).
 * Правила стакинга behavioral (решение по дизайну):
 *  - шанс:   1 - Π(1 - min(1, база × множитель_редкости_i))   -> chance()
 *  - величина (урон/длительность): Σ множителей редкости        -> magnitude()
 *  - период: делится на число копий, если handler включил stack_period (с нижним порогом)
 */
public final class BehaviorGroup {

    public final String poolId;
    public final List<EscalationInstance> instances;
    public final BehaviorHandler handler;
    private final IEscalationPlayerData data;

    public BehaviorGroup(String poolId, List<EscalationInstance> instances,
                         BehaviorHandler handler, IEscalationPlayerData data) {
        this.poolId = poolId;
        this.instances = instances;
        this.handler = handler;
        this.data = data;
    }

    public int count() {
        return instances.size();
    }

    /** Σ эффективных множителей (редкость × эскалация легендарки). Один common = 1.0. */
    public double magnitude() {
        double sum = 0.0;
        for (EscalationInstance i : instances) {
            sum += i.getEffectiveMultiplier();
        }
        return sum;
    }

    public double averageMultiplier() {
        return instances.isEmpty() ? 1.0 : magnitude() / instances.size();
    }

    /** Итоговый шанс (0..1) для базового шанса base (0..1) с учётом всех копий. */
    public double chance(double base) {
        double miss = 1.0;
        for (EscalationInstance i : instances) {
            double p = Math.max(0.0, Math.min(1.0, base * i.getEffectiveMultiplier()));
            miss *= (1.0 - p);
        }
        return 1.0 - miss;
    }

    public boolean roll(double base) {
        return BehaviorRng.roll(chance(base));
    }

    /** Персистентное состояние этого id у игрока (таймеры, счётчики). Стирается при снятии id. */
    public CompoundTag state() {
        CompoundTag root = data.getBehaviorState();
        if (!root.contains(poolId, Tag.TAG_COMPOUND)) {
            root.put(poolId, new CompoundTag());
        }
        return root.getCompound(poolId);
    }
}

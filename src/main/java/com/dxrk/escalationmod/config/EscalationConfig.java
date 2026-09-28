package com.dxrk.escalationmod.config;

/**
 * Раздел 13: все настраиваемые числа мода в одном месте.
 * Файл: config/escalation/escalation_config.json. Значения по умолчанию = то, что было
 * захардкожено раньше. Отсутствующие в файле ключи остаются со значением по умолчанию.
 */
public class EscalationConfig {

    public TimingSection timing = new TimingSection();
    public TierProgressionSection tierProgression = new TierProgressionSection();
    public DimensionLockSection dimensionLock = new DimensionLockSection();
    public RaritySection rarity = new RaritySection();
    public MercyRuleSection mercyRule = new MercyRuleSection();
    public WinConditionSection winCondition = new WinConditionSection();
    public UiSection ui = new UiSection();

    public static class TimingSection {
        public int intervalMinSec = 60;
        public int intervalMaxSec = 600;
    }

    public static class TierProgressionSection {
        /** ASCENDING | DESCENDING | RANDOM */
        public String order = "ASCENDING";
        public double totalTargetHours = 80.0;
        public int jitter = 2;
    }

    public static class DimensionLockSection {
        public double netherRealHours = 20.0;
        /** Порог End генерируется ОДИН раз при создании мира — на уже созданные миры не влияет. */
        public double endRealHoursMin = 50.0;
        public double endRealHoursMax = 100.0;
        public double endSoonThresholdHours = 10.0;
    }

    public static class RaritySection {
        public WeightsSection weights = new WeightsSection();
        public MultipliersSection multipliers = new MultipliersSection();
        public LegendaryEscalationSection legendaryEscalation = new LegendaryEscalationSection();
    }

    public static class WeightsSection {
        public double commonTier1 = 70.0;
        public double commonTier10 = 40.0;
        public double rareTier1 = 22.0;
        public double rareTier10 = 32.0;
        public double epicTier1 = 7.0;
        public double epicTier10 = 21.0;
        /** legendary% = legendaryBasePercent + tier * legendaryPerTierPercent */
        public double legendaryBasePercent = 0.5;
        public double legendaryPerTierPercent = 0.65;
    }

    public static class MultipliersSection {
        public double common = 1.0;
        public double rare = 1.5;
        public double epic = 2.5;
        public double legendary = 4.0;
    }

    public static class LegendaryEscalationSection {
        public double perSecond = 0.2;
        public int durationSec = 30;
    }

    public static class MercyRuleSection {
        public int deathsToTrigger = 5;
        public double pauseDurationMin = 30.0;
        public double removalPercentMin = 40.0;
        public double removalPercentMax = 50.0;
        public double ignoreIfRemainingMinAbove = 10.0;
        public double extendPercentIfBelowOrEqual = 25.0;
    }

    public static class WinConditionSection {
        public int requiredLegendaryCount = 10;
        public boolean clearAllActive = true;
        public boolean stopNewSpawnsAfterWin = true;
        public boolean endlessModeToggleAvailable = true;
        /** При выборе "Остановиться" в endless-режиме снимать все накопленные усложнения. */
        public boolean clearActiveWhenEndlessStopped = true;
    }

    public static class UiSection {
        public double toastGrowMs = 220.0;
        public double toastHoldMinSec = 5.0;
        public double toastHoldMaxSec = 8.0;
        public double toastShrinkMs = 180.0;
        public double toastTypewriterMsPerChar = 18.0;
        public double journalStaggerMs = 55.0;
        public double journalTypewriterMsPerChar = 8.0;
        public int timerBarYellowBelowSec = 30;
        public int timerBarRedBelowSec = 10;
    }

    /** Чинит null-секции (если их стёрли из файла) и приводит значения к разумным границам. */
    public EscalationConfig sanitize() {
        if (timing == null) timing = new TimingSection();
        if (tierProgression == null) tierProgression = new TierProgressionSection();
        if (dimensionLock == null) dimensionLock = new DimensionLockSection();
        if (rarity == null) rarity = new RaritySection();
        if (rarity.weights == null) rarity.weights = new WeightsSection();
        if (rarity.multipliers == null) rarity.multipliers = new MultipliersSection();
        if (rarity.legendaryEscalation == null) rarity.legendaryEscalation = new LegendaryEscalationSection();
        if (mercyRule == null) mercyRule = new MercyRuleSection();
        if (winCondition == null) winCondition = new WinConditionSection();
        if (ui == null) ui = new UiSection();

        timing.intervalMinSec = Math.max(1, timing.intervalMinSec);
        timing.intervalMaxSec = Math.max(timing.intervalMinSec, timing.intervalMaxSec);

        if (tierProgression.order == null) tierProgression.order = "ASCENDING";
        tierProgression.totalTargetHours = Math.max(0.1, tierProgression.totalTargetHours);
        tierProgression.jitter = Math.max(0, tierProgression.jitter);

        dimensionLock.netherRealHours = Math.max(0.0, dimensionLock.netherRealHours);
        dimensionLock.endRealHoursMin = Math.max(0.0, dimensionLock.endRealHoursMin);
        dimensionLock.endRealHoursMax = Math.max(dimensionLock.endRealHoursMin, dimensionLock.endRealHoursMax);
        dimensionLock.endSoonThresholdHours = Math.max(0.0, dimensionLock.endSoonThresholdHours);

        WeightsSection w = rarity.weights;
        w.commonTier1 = Math.max(0.0, w.commonTier1);
        w.commonTier10 = Math.max(0.0, w.commonTier10);
        w.rareTier1 = Math.max(0.0, w.rareTier1);
        w.rareTier10 = Math.max(0.0, w.rareTier10);
        w.epicTier1 = Math.max(0.0, w.epicTier1);
        w.epicTier10 = Math.max(0.0, w.epicTier10);
        w.legendaryBasePercent = Math.max(0.0, w.legendaryBasePercent);
        w.legendaryPerTierPercent = Math.max(0.0, w.legendaryPerTierPercent);
        // легендарка на тире 1 никогда не должна быть нулевой (раздел 6) — защита от нулевой суммы весов
        if (w.legendaryBasePercent + w.legendaryPerTierPercent <= 0.0) {
            w.legendaryBasePercent = 0.5;
        }

        MultipliersSection m = rarity.multipliers;
        m.common = Math.max(0.0, m.common);
        m.rare = Math.max(0.0, m.rare);
        m.epic = Math.max(0.0, m.epic);
        m.legendary = Math.max(0.0, m.legendary);

        rarity.legendaryEscalation.perSecond = Math.max(0.0, rarity.legendaryEscalation.perSecond);
        rarity.legendaryEscalation.durationSec = Math.max(0, rarity.legendaryEscalation.durationSec);

        mercyRule.deathsToTrigger = Math.max(1, mercyRule.deathsToTrigger);
        mercyRule.pauseDurationMin = Math.max(0.1, mercyRule.pauseDurationMin);
        mercyRule.removalPercentMin = clamp(mercyRule.removalPercentMin, 0.0, 100.0);
        mercyRule.removalPercentMax = clamp(mercyRule.removalPercentMax, mercyRule.removalPercentMin, 100.0);
        mercyRule.ignoreIfRemainingMinAbove = Math.max(0.0, mercyRule.ignoreIfRemainingMinAbove);
        mercyRule.extendPercentIfBelowOrEqual = Math.max(0.0, mercyRule.extendPercentIfBelowOrEqual);

        winCondition.requiredLegendaryCount = Math.max(1, winCondition.requiredLegendaryCount);

        ui.toastGrowMs = Math.max(0.0, ui.toastGrowMs);
        ui.toastHoldMinSec = Math.max(0.5, ui.toastHoldMinSec);
        ui.toastHoldMaxSec = Math.max(ui.toastHoldMinSec, ui.toastHoldMaxSec);
        ui.toastShrinkMs = Math.max(0.0, ui.toastShrinkMs);
        ui.toastTypewriterMsPerChar = Math.max(0.0, ui.toastTypewriterMsPerChar);
        ui.journalStaggerMs = Math.max(0.0, ui.journalStaggerMs);
        ui.journalTypewriterMsPerChar = Math.max(0.0, ui.journalTypewriterMsPerChar);
        ui.timerBarRedBelowSec = Math.max(0, ui.timerBarRedBelowSec);
        ui.timerBarYellowBelowSec = Math.max(ui.timerBarRedBelowSec, ui.timerBarYellowBelowSec);

        return this;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

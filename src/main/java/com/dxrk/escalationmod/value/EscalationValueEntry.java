package com.dxrk.escalationmod.value;

/** Одна запись value.json — числовая/классификационная привязка к id из escalation_pool_full.json (раздел 7). */
public class EscalationValueEntry {

    public String id;

    /** "percent_stat_clamped" | "direct_multiplier_uncapped" | "behavioral" | "flavor" */
    public String stacking_class;

    public String affected_stat;   // null для behavioral/flavor
    public Double base_value;      // null для behavioral/flavor
    public String unit;            // "percent" | "percent_per_hour" | ...
    public String value_source;    // "explicit" | "estimated" | null
    public String notes;
}

package com.dxrk.escalationmod.pool;

import com.google.gson.annotations.SerializedName;

public class EscalationDefinition {
    public String id;
    public int tier;
    public String name;
    public String description;
    public String category;

    @SerializedName("base_value")
    public Double baseValue;

    @SerializedName("stack_mode")
    public String stackMode;

    public String source;
}

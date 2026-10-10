package com.dxrk.escalationmod.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Мелкие помощники для чтения параметров из behaviors.json и безопасного поиска в реестрах. */
public final class BehaviorJson {

    private BehaviorJson() {
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsString() : def;
    }

    public static double dbl(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive()) {
            try {
                return e.getAsDouble();
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static int integer(JsonObject o, String key, int def) {
        return (int) Math.round(dbl(o, key, def));
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsBoolean() : def;
    }

    /** Строка или массив строк -> список строк. */
    public static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        if (e.isJsonPrimitive()) {
            out.add(e.getAsString());
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonPrimitive()) out.add(x.getAsString());
            }
        }
        return out;
    }

    public static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonArray()) ? e.getAsJsonArray() : new JsonArray();
    }

    public static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : new JsonObject();
    }

    // ---------- реестры: без getValue-по-умолчанию (у некоторых реестров "пустой" дефолт) ----------

    public static ResourceLocation rl(String id) {
        if (id == null) return null;
        return ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
    }

    public static MobEffect effect(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null || !ForgeRegistries.MOB_EFFECTS.containsKey(rl)) return null;
        return ForgeRegistries.MOB_EFFECTS.getValue(rl);
    }

    public static SoundEvent sound(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null || !ForgeRegistries.SOUND_EVENTS.containsKey(rl)) return null;
        return ForgeRegistries.SOUND_EVENTS.getValue(rl);
    }

    public static EntityType<?> entityType(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return null;
        return ForgeRegistries.ENTITY_TYPES.getValue(rl);
    }
}

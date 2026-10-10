package com.dxrk.escalationmod.behavior.custom;

import com.dxrk.escalationmod.behavior.BehaviorActions;
import com.dxrk.escalationmod.behavior.BehaviorGroup;
import com.dxrk.escalationmod.behavior.BehaviorHandler;
import com.dxrk.escalationmod.behavior.BehaviorJson;
import com.dxrk.escalationmod.behavior.BehaviorRng;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * id 0131 "Паранойя темноты": 5+ минут без света -> редкие фальшивые звуки шагов (слышит только игрок).
 * JSON: dark_seconds (300), light_level (4), chance (процент в секунду, 4).
 */
public class DarkParanoiaHandler implements BehaviorHandler {

    private static final String[] SOUNDS = {
            "minecraft:entity.zombie.step", "minecraft:entity.skeleton.step", "minecraft:entity.spider.step"
    };

    private final int darkSeconds;
    private final int lightLevel;
    private final double chancePct;

    public DarkParanoiaHandler(String poolId, JsonObject p) {
        this.darkSeconds = BehaviorJson.integer(p, "dark_seconds", 300);
        this.lightLevel = BehaviorJson.integer(p, "light_level", 4);
        this.chancePct = BehaviorJson.dbl(p, "chance", 4.0);
    }

    @Override
    public void onSecond(ServerPlayer player, BehaviorGroup group) {
        CompoundTag st = group.state();
        boolean dark = player.level().getMaxLocalRawBrightness(player.blockPosition()) < lightLevel;
        int seconds = dark ? st.getInt("dark") + 1 : 0;
        st.putInt("dark", seconds);

        if (seconds >= darkSeconds && group.roll(chancePct / 100.0)) {
            fake(player);
        }
    }

    private static void fake(ServerPlayer player) {
        String id = SOUNDS[BehaviorRng.RNG.nextInt(SOUNDS.length)];
        BehaviorActions.playSound(player, id, 0.7f, 0.85f + BehaviorRng.RNG.nextFloat() * 0.2f);
    }

    @Override
    public void debugFire(ServerPlayer player, BehaviorGroup group) {
        fake(player);
    }

    @Override
    public String describe() {
        return "фальшивые шаги после " + darkSeconds + "с в темноте (<" + lightLevel + "), шанс " + chancePct + "%/с";
    }
}

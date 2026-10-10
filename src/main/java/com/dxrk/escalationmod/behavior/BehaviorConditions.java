package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.stats.PlayerCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Строковые условия для JSON. Префикс "!" инвертирует. Параметр через двоеточие: "no_sleep_days:3".
 * Неизвестное условие = false (и один раз пишется в лог через реестр) — чтобы опечатка не включала эффект везде.
 */
public final class BehaviorConditions {

    private BehaviorConditions() {
    }

    public static boolean all(ServerPlayer player, List<String> conditions) {
        for (String c : conditions) {
            if (!check(player, c)) return false;
        }
        return true;
    }

    public static boolean check(ServerPlayer player, String raw) {
        boolean negate = raw.startsWith("!");
        String cond = negate ? raw.substring(1) : raw;
        return evaluate(player, cond) != negate;
    }

    private static boolean evaluate(ServerPlayer player, String cond) {
        String name = cond;
        double arg = 0.0;
        int colon = cond.indexOf(':');
        if (colon >= 0) {
            name = cond.substring(0, colon);
            try {
                arg = Double.parseDouble(cond.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }

        Level level = player.level();
        BlockPos pos = player.blockPosition();
        long dayTime = level.getDayTime() % 24000L;

        switch (name) {
            case "raining":
                return level.isRainingAt(pos);
            case "clear_weather":
                return !level.isRaining();
            case "thundering":
                return level.isThundering();
            case "night":
                return level.dimension() == Level.OVERWORLD && dayTime >= 13000L && dayTime < 23000L;
            case "day":
                return level.dimension() == Level.OVERWORLD && (dayTime < 13000L || dayTime >= 23000L);
            case "morning":
                return level.dimension() == Level.OVERWORLD && dayTime < 4000L;
            case "noon":
                return level.dimension() == Level.OVERWORLD && dayTime >= 5000L && dayTime < 9000L;
            case "full_moon":
                return level.dimension() == Level.OVERWORLD && level.getMoonPhase() == 0
                        && dayTime >= 13000L && dayTime < 23000L;
            case "overworld":
                return level.dimension() == Level.OVERWORLD;
            case "nether":
                return level.dimension() == Level.NETHER;
            case "end":
                return level.dimension() == Level.END;
            case "outdoors":
                return level.canSeeSky(pos);
            case "underground":
                return pos.getY() < (arg > 0 ? arg : 40);
            case "dark":
                return level.getMaxLocalRawBrightness(pos) < (arg > 0 ? (int) arg : 4);
            case "in_nether_forest":
                return level.getBiome(pos).is(Biomes.CRIMSON_FOREST) || level.getBiome(pos).is(Biomes.WARPED_FOREST);
            case "in_badlands":
                return level.getBiome(pos).is(BiomeTags.IS_BADLANDS);
            case "no_helmet":
                return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
            case "no_boots":
                return player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            case "on_grass":
                return level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK);
            case "recent_combat":
                return player.tickCount - player.getLastHurtMobTimestamp() < 600
                        || player.tickCount - player.getLastHurtByMobTimestamp() < 600;
            case "low_health":
                return player.getHealth() <= player.getMaxHealth() * (arg > 0 ? arg / 100.0 : 0.3);
            case "no_sleep_days":
                return PlayerCounters.timeSinceRest(player) >= (long) (arg * 24000.0);
            default:
                BehaviorRegistry.warnOnce("Неизвестное условие '" + cond + "' в behaviors.json (считается false)");
                return false;
        }
    }
}

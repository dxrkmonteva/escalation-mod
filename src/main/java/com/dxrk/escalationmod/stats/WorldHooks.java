package com.dxrk.escalationmod.stats;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.security.SecureRandom;

/**
 * Пакет D: события мира без владельца-игрока. Для роста берём ближайшего игрока в радиусе
 * (stacking.mobStatNearestPlayerRadius), для печей — самый строгий множитель среди онлайн-игроков.
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public class WorldHooks {

    private static final SecureRandom RNG = new SecureRandom();

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
        double radius = EscalationConfigManager.get().stacking.mobStatNearestPlayerRadius;
        Player p = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius, false);
        return p instanceof ServerPlayer sp ? sp : null;
    }

    // рост культур (id 0857 тёмный лес, id 0856 засуха)
    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        ServerPlayer player = nearestPlayer(level, pos);
        if (player == null) return;

        double keep = 1.0;
        Holder<Biome> biome = level.getBiome(pos);

        if (biome.is(Biomes.DARK_FOREST) && level.getBrightness(LightLayer.SKY, pos) < 12) {
            keep *= StatEngine.factor(player, "crop_growth_rate_dark_oak_canopy");
        }
        if (!level.isRaining() && biome.value().getBaseTemperature() >= 1.0f) {
            keep *= StatEngine.factor(player, "farm_fertility_during_drought");
        }

        if (keep < 1.0 && RNG.nextDouble() > keep) {
            event.setResult(Event.Result.DENY);
        }
    }

    // рост деревьев без прямого солнца (id 0840)
    @SubscribeEvent
    public static void onSaplingGrow(SaplingGrowTreeEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        if (level.canSeeSky(pos.above())) return;

        ServerPlayer player = nearestPlayer(level, pos);
        if (player == null) return;

        double keep = StatEngine.factor(player, "tree_growth_rate_no_direct_sun");
        if (keep < 1.0 && RNG.nextDouble() > keep) {
            event.setResult(Event.Result.DENY);
        }
    }

    // топливо для печей (id 0097) и доменных печей/коптилен (id 0421)
    @SubscribeEvent
    public static void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        int burnTime = event.getBurnTime();
        if (burnTime <= 0) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        RecipeType<?> type = event.getRecipeType();
        boolean blastOrSmoke = type == RecipeType.BLASTING || type == RecipeType.SMOKING;
        String key = blastOrSmoke ? "blast_furnace_smoker_fuel_required" : "furnace_fuel_required";

        double worst = 1.0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            worst = Math.max(worst, StatEngine.factor(p, key));
        }
        if (worst > 1.0) {
            event.setBurnTime(Math.max(1, (int) (burnTime / worst)));
        }
    }
}

package com.dxrk.escalationmod.stats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Множители, которым нужен и сервер, и клиент (скорость добычи, прыжок).
 * Сервер считает их по счётчикам и раз в секунду шлёт клиенту (ClientModifiersPacket).
 * Все значения — множители скорости (1.0 = без эффекта, меньше = хуже).
 */
public record ToolFactors(float pickaxe, float shovel, float all, float stone, float jump) {

    public static final ToolFactors NEUTRAL = new ToolFactors(1f, 1f, 1f, 1f, 1f);

    private static final Item[] PICKAXES = {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
            Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};
    private static final Item[] SHOVELS = {Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL,
            Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL};

    public static ToolFactors compute(ServerPlayer p) {
        float pick = sumUsed(p, PICKAXES) >= 50_000L
                ? (float) StatEngine.factor(p, "pickaxe_speed_after_50000_blocks") : 1f;
        float shovel = sumUsed(p, SHOVELS) >= 10_000L
                ? (float) StatEngine.factor(p, "shovel_speed_after_10000_blocks") : 1f;
        float all = (float) PlayerCounters.allStatsFactor(p);
        // "время добычи камня +N%" -> скорость делится на множитель
        float stone = (float) (1.0 / StatEngine.factor(p, "stone_mining_time_no_efficiency"));
        float jump = PlayerCounters.custom(p, Stats.JUMP) >= 10_000L
                ? (float) StatEngine.factor(p, "jump_height_after_10000_jumps") : 1f;
        return new ToolFactors(pick, shovel, all, stone, jump);
    }

    private static long sumUsed(ServerPlayer p, Item[] items) {
        long total = 0L;
        for (Item item : items) {
            total += PlayerCounters.itemUsed(p, item);
        }
        return total;
    }

    /** Одна и та же формула на сервере и на клиенте. */
    public float applyBreakSpeed(Player player, BlockState state, float speed) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof PickaxeItem) {
            speed *= pickaxe;
        } else if (held.getItem() instanceof ShovelItem) {
            speed *= shovel;
        }
        speed *= all;
        if (stone != 1f && state.is(BlockTags.BASE_STONE_OVERWORLD)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, held) <= 0) {
            speed *= stone;
        }
        return speed;
    }
}

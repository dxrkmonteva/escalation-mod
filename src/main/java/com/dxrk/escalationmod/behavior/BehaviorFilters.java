package com.dxrk.escalationmod.behavior;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Фильтры блоков и предметов по строковым токенам из JSON.
 * Блоки: any | ore | log | chest | lever_or_button | contains:подстрока | minecraft:id
 * Предметы: any | tool_or_weapon | armor | enchanted_book | enchanted | damageable | contains:подстрока | id
 * Спецификация — строка или массив строк (достаточно совпадения с любым токеном).
 */
public final class BehaviorFilters {

    private BehaviorFilters() {
    }

    public static boolean block(JsonElement spec, BlockState state) {
        if (state == null) return false;
        List<String> tokens = BehaviorJson.strings(spec);
        if (tokens.isEmpty()) return true;
        Block block = state.getBlock();
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        String path = key == null ? "" : key.getPath();
        String full = key == null ? "" : key.toString();

        for (String t : tokens) {
            switch (t) {
                case "any" -> {
                    return true;
                }
                case "ore" -> {
                    if (state.is(Tags.Blocks.ORES) || path.endsWith("_ore") || path.equals("ancient_debris")) return true;
                }
                case "log" -> {
                    if (state.is(BlockTags.LOGS)) return true;
                }
                case "chest" -> {
                    if (block instanceof ChestBlock) return true;
                }
                case "lever_or_button" -> {
                    if (block instanceof LeverBlock || block instanceof ButtonBlock) return true;
                }
                default -> {
                    if (matchesIdToken(t, path, full)) return true;
                }
            }
        }
        return false;
    }

    public static boolean item(JsonElement spec, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        List<String> tokens = BehaviorJson.strings(spec);
        if (tokens.isEmpty()) return true;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = key == null ? "" : key.getPath();
        String full = key == null ? "" : key.toString();

        for (String t : tokens) {
            switch (t) {
                case "any" -> {
                    return true;
                }
                case "tool_or_weapon" -> {
                    if (stack.getItem() instanceof TieredItem) return true;
                }
                case "armor" -> {
                    if (stack.getItem() instanceof ArmorItem) return true;
                }
                case "enchanted_book" -> {
                    if (stack.is(Items.ENCHANTED_BOOK)) return true;
                }
                case "enchanted_armor" -> {
                    if (stack.getItem() instanceof ArmorItem && stack.isEnchanted()) return true;
                }
                case "enchanted" -> {
                    if (stack.isEnchanted()) return true;
                }
                case "damageable" -> {
                    if (stack.isDamageableItem()) return true;
                }
                default -> {
                    if (matchesIdToken(t, path, full)) return true;
                }
            }
        }
        return false;
    }

    /** Содержит ли путь id предмета подстроку (для торговли: "terracotta", "brick"...). */
    public static boolean pathContains(ItemStack stack, String part) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.getPath().contains(part);
    }

    private static boolean matchesIdToken(String token, String path, String full) {
        if (token.startsWith("contains:")) {
            return path.contains(token.substring("contains:".length()));
        }
        String normalized = token.contains(":") ? token : "minecraft:" + token;
        return normalized.equals(full);
    }
}

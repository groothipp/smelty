package cloud.hipp.smelty.recipe;

import cloud.hipp.smelty.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * Defines the crafting recipes for the Smelty crafting anvil.
 *
 * Supported materials: heated copper, heated iron, heated gold, and diamond.
 * Spears, tools, and armor for all tiers.
 */
public class AnvilRecipes {

    public record AnvilRecipe(int sticks, int materials, Map<Item, Item> outputs) {}

    private static final List<AnvilRecipe> RECIPES = new ArrayList<>();

    static {
        // ── Tools ─────────────────────────────────────────────────────

        // Spears: 2 sticks + 1 material (long handle)
        Map<Item, Item> spears = new HashMap<>();
        spears.put(ModItems.HEATED_COPPER_INGOT,         Items.COPPER_SPEAR);
        spears.put(ModItems.HEATED_IRON_INGOT,           Items.IRON_SPEAR);
        spears.put(ModItems.HEATED_GOLD_INGOT,           Items.GOLDEN_SPEAR);
        spears.put(Items.DIAMOND,                        Items.DIAMOND_SPEAR);
        RECIPES.add(new AnvilRecipe(2, 1, spears));

        // Shovels: 1 stick + 1 material (short handle)
        Map<Item, Item> shovels = new HashMap<>();
        shovels.put(ModItems.HEATED_COPPER_INGOT,        Items.COPPER_SHOVEL);
        shovels.put(ModItems.HEATED_IRON_INGOT,          Items.IRON_SHOVEL);
        shovels.put(ModItems.HEATED_GOLD_INGOT,          Items.GOLDEN_SHOVEL);
        shovels.put(Items.DIAMOND,                       Items.DIAMOND_SHOVEL);
        RECIPES.add(new AnvilRecipe(1, 1, shovels));

        // Swords: 1 stick + 2 materials
        Map<Item, Item> swords = new HashMap<>();
        swords.put(ModItems.HEATED_COPPER_INGOT,         Items.COPPER_SWORD);
        swords.put(ModItems.HEATED_IRON_INGOT,           Items.IRON_SWORD);
        swords.put(ModItems.HEATED_GOLD_INGOT,           Items.GOLDEN_SWORD);
        swords.put(Items.DIAMOND,                        Items.DIAMOND_SWORD);
        RECIPES.add(new AnvilRecipe(1, 2, swords));

        // Hoes: 2 sticks + 2 materials
        Map<Item, Item> hoes = new HashMap<>();
        hoes.put(ModItems.HEATED_COPPER_INGOT,           Items.COPPER_HOE);
        hoes.put(ModItems.HEATED_IRON_INGOT,             Items.IRON_HOE);
        hoes.put(ModItems.HEATED_GOLD_INGOT,             Items.GOLDEN_HOE);
        hoes.put(Items.DIAMOND,                          Items.DIAMOND_HOE);
        RECIPES.add(new AnvilRecipe(2, 2, hoes));

        // Pickaxes: 2 sticks + 3 materials
        Map<Item, Item> pickaxes = new HashMap<>();
        pickaxes.put(ModItems.HEATED_COPPER_INGOT,       Items.COPPER_PICKAXE);
        pickaxes.put(ModItems.HEATED_IRON_INGOT,         Items.IRON_PICKAXE);
        pickaxes.put(ModItems.HEATED_GOLD_INGOT,         Items.GOLDEN_PICKAXE);
        pickaxes.put(Items.DIAMOND,                      Items.DIAMOND_PICKAXE);
        RECIPES.add(new AnvilRecipe(2, 3, pickaxes));

        // Axes: 1 stick + 3 materials
        Map<Item, Item> axes = new HashMap<>();
        axes.put(ModItems.HEATED_COPPER_INGOT,           Items.COPPER_AXE);
        axes.put(ModItems.HEATED_IRON_INGOT,             Items.IRON_AXE);
        axes.put(ModItems.HEATED_GOLD_INGOT,             Items.GOLDEN_AXE);
        axes.put(Items.DIAMOND,                          Items.DIAMOND_AXE);
        RECIPES.add(new AnvilRecipe(1, 3, axes));

        // ── Armor ─────────────────────────────────────────────────────

        // Boots: 0 sticks + 4 materials
        Map<Item, Item> boots = new HashMap<>();
        boots.put(ModItems.HEATED_COPPER_INGOT,          Items.COPPER_BOOTS);
        boots.put(ModItems.HEATED_IRON_INGOT,            Items.IRON_BOOTS);
        boots.put(ModItems.HEATED_GOLD_INGOT,            Items.GOLDEN_BOOTS);
        boots.put(Items.DIAMOND,                         Items.DIAMOND_BOOTS);
        RECIPES.add(new AnvilRecipe(0, 4, boots));

        // Helmet: 0 sticks + 5 materials
        Map<Item, Item> helmets = new HashMap<>();
        helmets.put(ModItems.HEATED_COPPER_INGOT,        Items.COPPER_HELMET);
        helmets.put(ModItems.HEATED_IRON_INGOT,          Items.IRON_HELMET);
        helmets.put(ModItems.HEATED_GOLD_INGOT,          Items.GOLDEN_HELMET);
        helmets.put(Items.DIAMOND,                       Items.DIAMOND_HELMET);
        RECIPES.add(new AnvilRecipe(0, 5, helmets));

        // Leggings: 0 sticks + 7 materials
        Map<Item, Item> leggings = new HashMap<>();
        leggings.put(ModItems.HEATED_COPPER_INGOT,       Items.COPPER_LEGGINGS);
        leggings.put(ModItems.HEATED_IRON_INGOT,         Items.IRON_LEGGINGS);
        leggings.put(ModItems.HEATED_GOLD_INGOT,         Items.GOLDEN_LEGGINGS);
        leggings.put(Items.DIAMOND,                      Items.DIAMOND_LEGGINGS);
        RECIPES.add(new AnvilRecipe(0, 7, leggings));

        // Chestplate: 0 sticks + 8 materials
        Map<Item, Item> chestplates = new HashMap<>();
        chestplates.put(ModItems.HEATED_COPPER_INGOT,    Items.COPPER_CHESTPLATE);
        chestplates.put(ModItems.HEATED_IRON_INGOT,      Items.IRON_CHESTPLATE);
        chestplates.put(ModItems.HEATED_GOLD_INGOT,      Items.GOLDEN_CHESTPLATE);
        chestplates.put(Items.DIAMOND,                   Items.DIAMOND_CHESTPLATE);
        RECIPES.add(new AnvilRecipe(0, 8, chestplates));
    }

    @Nullable
    public static Item findOutput(int stickCount, int materialCount, Item material) {
        for (AnvilRecipe recipe : RECIPES) {
            if (recipe.sticks() == stickCount && recipe.materials() == materialCount) {
                Item output = recipe.outputs().get(material);
                if (output != null) {
                    return output;
                }
            }
        }
        return null;
    }

    public static boolean hasRecipeForCounts(int stickCount, int materialCount) {
        for (AnvilRecipe recipe : RECIPES) {
            if (recipe.sticks() == stickCount && recipe.materials() == materialCount) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidMaterial(Item item) {
        for (AnvilRecipe recipe : RECIPES) {
            if (recipe.outputs().containsKey(item)) {
                return true;
            }
        }
        return false;
    }
}

package cloud.hipp.smelty.recipe;

import cloud.hipp.smelty.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines what the smelter can process and what it produces.
 *
 * Two types of input:
 *   ORE    → 1 heated ingot guaranteed + chance for a bonus ingot (reward for ore!)
 *   INGOT  → 1 heated ingot, no bonus (just reheating)
 *
 * This is a simple in-code recipe system. For a more robust mod you'd use
 * Minecraft's recipe system with JSON files, but for v1 this is cleaner
 * and easier to understand.
 */
public class SmelterRecipes {

    /**
     * Represents a smelter recipe.
     *
     * @param output     The heated ingot item to produce
     * @param bonusChance Chance (0.0 to 1.0) of getting an extra output.
     *                    0.0 = no bonus (for reheating ingots)
     *                    0.5 = 50% chance of a second ingot (for ores)
     */
    public record SmelterRecipe(Item output, float bonusChance) {}

    private static final Map<Item, SmelterRecipe> RECIPES = new HashMap<>();

    static {
        // ── Ore recipes: 50% chance of bonus ingot ────────────────────
        // Raw ores get a chance at doubling — this is the reward for using
        // the smelter instead of a regular furnace!
        RECIPES.put(Items.RAW_IRON,   new SmelterRecipe(ModItems.HEATED_IRON_INGOT,   0.5f));
        RECIPES.put(Items.RAW_GOLD,   new SmelterRecipe(ModItems.HEATED_GOLD_INGOT,   0.5f));
        RECIPES.put(Items.RAW_COPPER, new SmelterRecipe(ModItems.HEATED_COPPER_INGOT, 0.5f));

        // ── Ingot recipes: no bonus (just reheating) ─────────────────
        // Already-smelted ingots can be reheated for anvil crafting,
        // but you don't get a bonus — that would be exploitable!
        RECIPES.put(Items.IRON_INGOT,   new SmelterRecipe(ModItems.HEATED_IRON_INGOT,   0.0f));
        RECIPES.put(Items.GOLD_INGOT,   new SmelterRecipe(ModItems.HEATED_GOLD_INGOT,   0.0f));
        RECIPES.put(Items.COPPER_INGOT, new SmelterRecipe(ModItems.HEATED_COPPER_INGOT, 0.0f));
    }

    /**
     * Looks up the recipe for an input item.
     * @return The recipe, or null if the item can't be smelted
     */
    public static SmelterRecipe getRecipe(Item input) {
        return RECIPES.get(input);
    }

    /**
     * Checks if an item can be processed by the smelter.
     */
    public static boolean canSmelt(Item input) {
        return RECIPES.containsKey(input);
    }
}

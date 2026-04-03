package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.Smelty;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class SmeltyToolRecipeSerializer {
	public static final RecipeSerializer<SmeltyToolRecipe> INSTANCE = Registry.register(
			Registries.RECIPE_SERIALIZER,
			Identifier.of(Smelty.MOD_ID, "tool_crafting"),
			new SpecialCraftingRecipe.SpecialRecipeSerializer<>(SmeltyToolRecipe::new)
	);

	public static void initialize() {
		// Forces static initializer to run
	}
}

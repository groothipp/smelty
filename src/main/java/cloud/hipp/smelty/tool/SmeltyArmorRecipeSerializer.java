package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.Smelty;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class SmeltyArmorRecipeSerializer {
	public static final RecipeSerializer<SmeltyArmorRecipe> INSTANCE = Registry.register(
			Registries.RECIPE_SERIALIZER,
			Identifier.of(Smelty.MOD_ID, "armor_crafting"),
			new SpecialCraftingRecipe.SpecialRecipeSerializer<>(SmeltyArmorRecipe::new)
	);

	public static void initialize() {
		// Forces static initializer to run
	}
}

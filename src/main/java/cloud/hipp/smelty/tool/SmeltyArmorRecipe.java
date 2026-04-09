package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import java.util.List;

public class SmeltyArmorRecipe extends SpecialCraftingRecipe {

	public SmeltyArmorRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	// Armor patterns: 'I' = ingot, ' ' = empty
	private static final char[][] HELMET_PATTERN = {
			{'I', 'I', 'I'},
			{'I', ' ', 'I'}
	};
	private static final char[][] CHESTPLATE_PATTERN = {
			{'I', ' ', 'I'},
			{'I', 'I', 'I'},
			{'I', 'I', 'I'}
	};
	private static final char[][] LEGGINGS_PATTERN = {
			{'I', 'I', 'I'},
			{'I', ' ', 'I'},
			{'I', ' ', 'I'}
	};
	private static final char[][] BOOTS_PATTERN = {
			{'I', ' ', 'I'},
			{'I', ' ', 'I'}
	};

	private record PatternMatch(SmeltyArmorType armorType, char[][] pattern) {}

	private static final PatternMatch[] ALL_PATTERNS = {
			new PatternMatch(SmeltyArmorType.HELMET, HELMET_PATTERN),
			new PatternMatch(SmeltyArmorType.CHESTPLATE, CHESTPLATE_PATTERN),
			new PatternMatch(SmeltyArmorType.LEGGINGS, LEGGINGS_PATTERN),
			new PatternMatch(SmeltyArmorType.BOOTS, BOOTS_PATTERN),
	};

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		return findMatch(input) != null;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		MatchResult result = findMatch(input);
		if (result == null) return ItemStack.EMPTY;

		SmeltyArmorType armorType = result.armorType;
		AlloyComposition composition = result.composition;

		// Compute stats from composition using new formula system
		int defense = ArmorStatCalculator.computeSlotDefense(composition, armorType);
		float armorToughness = ArmorStatCalculator.computeArmorToughness(composition);
		float movementSpeed = ArmorStatCalculator.computeMovementSpeedModifier(composition);
		int durability = ArmorStatCalculator.computeDurability(composition, armorType);

		// Get output item
		Item outputItem = getOutputItem(armorType);
		ItemStack stack = new ItemStack(outputItem);

		// Store composition data for dynamic naming (coarse base for stacking consistency)
		AlloyComposition normalizedComp = composition.toNormalized(AlloyComposition.ITEM_RATIO_BASE);
		List<Float> percentages = new java.util.ArrayList<>();
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			percentages.add((float) normalizedComp.getMaterials().getOrDefault(mat, 0));
		}
		// [7] = defense value for tooltip, [8] = tier
		percentages.add((float) defense);
		percentages.add((float) composition.getTier());
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
				new CustomModelDataComponent(
						percentages, List.of(), List.of(),
						List.of(normalizedComp.getBlendedColor())));

		// Set durability
		stack.set(DataComponentTypes.MAX_DAMAGE, durability);

		// Set equippable component with dyeable alloy armor model
		net.minecraft.registry.RegistryKey<net.minecraft.item.equipment.EquipmentAsset> alloyAsset =
				net.minecraft.registry.RegistryKey.of(net.minecraft.item.equipment.EquipmentAssetKeys.REGISTRY_KEY,
						net.minecraft.util.Identifier.of("smelty", "alloy"));
		stack.set(DataComponentTypes.EQUIPPABLE,
				EquippableComponent.builder(armorType.getEquipmentSlot())
						.equipSound(SoundEvents.ITEM_ARMOR_EQUIP_IRON)
						.model(alloyAsset)
						.build());

		// Set dyed color so the dyeable equipment model tints with the alloy color
		stack.set(DataComponentTypes.DYED_COLOR,
				new net.minecraft.component.type.DyedColorComponent(normalizedComp.getBlendedColor()));

		// Set attribute modifiers
		AttributeModifiersComponent.Builder attrBuilder = AttributeModifiersComponent.builder();
		attrBuilder.add(EntityAttributes.ARMOR,
				new EntityAttributeModifier(armorType.getModifierId(),
						defense, EntityAttributeModifier.Operation.ADD_VALUE),
				armorType.getAttributeSlot());
		attrBuilder.add(EntityAttributes.ARMOR_TOUGHNESS,
				new EntityAttributeModifier(armorType.getModifierId(),
						armorToughness, EntityAttributeModifier.Operation.ADD_VALUE),
				armorType.getAttributeSlot());
		if (movementSpeed != 0) {
			attrBuilder.add(EntityAttributes.MOVEMENT_SPEED,
					new EntityAttributeModifier(armorType.getModifierId(),
							movementSpeed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE),
					armorType.getAttributeSlot());
		}
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, attrBuilder.build());

		// Set enchantability
		stack.set(DataComponentTypes.ENCHANTABLE, new net.minecraft.component.type.EnchantableComponent(14));

		// Hide vanilla attribute modifiers and dyed color tooltips (replaced by custom Smelty tooltip)
		stack.set(DataComponentTypes.TOOLTIP_DISPLAY,
				net.minecraft.component.type.TooltipDisplayComponent.DEFAULT
						.with(DataComponentTypes.ATTRIBUTE_MODIFIERS, true)
						.with(DataComponentTypes.DYED_COLOR, true));

		return stack;
	}

	private record MatchResult(SmeltyArmorType armorType, AlloyComposition composition) {}

	private MatchResult findMatch(CraftingRecipeInput input) {
		int w = input.getWidth();
		int h = input.getHeight();

		for (PatternMatch pm : ALL_PATTERNS) {
			char[][] pattern = pm.pattern;
			int ph = pattern.length;
			int pw = pattern[0].length;
			if (w != pw || h != ph) continue;

			MatchResult result = tryMatchPattern(input, pattern, pm.armorType);
			if (result != null) return result;
		}
		return null;
	}

	private MatchResult tryMatchPattern(CraftingRecipeInput input, char[][] pattern, SmeltyArmorType armorType) {
		int w = input.getWidth();
		int h = input.getHeight();

		ItemStack firstIngot = null;

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				ItemStack stack = input.getStackInSlot(x, y);
				char expected = pattern[y][x];

				if (expected == 'I') {
					if (!isValidIngot(stack)) return null;
					if (firstIngot == null) {
						firstIngot = stack;
					} else if (!areSameMaterial(firstIngot, stack)) {
						return null;
					}
				} else {
					if (!stack.isEmpty()) return null;
				}
			}
		}

		if (firstIngot == null) return null;

		AlloyComposition composition = getComposition(firstIngot);
		if (composition == null) return null;

		return new MatchResult(armorType, composition);
	}

	private static boolean isValidIngot(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Item item = stack.getItem();
		return item == Items.COPPER_INGOT
				|| item == Items.IRON_INGOT
				|| item == Items.GOLD_INGOT
				|| item == SmeltyItems.CASTED_DIAMOND
				|| item == Items.NETHERITE_INGOT
				|| item == SmeltyItems.OBSIDIAN_INGOT
				|| item == SmeltyItems.CASTED_EMERALD
				|| item == SmeltyItems.ALLOY_INGOT;
	}

	private static boolean areSameMaterial(ItemStack a, ItemStack b) {
		if (!a.isOf(b.getItem())) return false;
		if (a.isOf(SmeltyItems.ALLOY_INGOT)) {
			CustomModelDataComponent cmdA = a.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			CustomModelDataComponent cmdB = b.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			if (cmdA == null || cmdB == null) return cmdA == cmdB;
			return cmdA.floats().equals(cmdB.floats());
		}
		return true;
	}

	private static AlloyComposition getComposition(ItemStack stack) {
		Item item = stack.getItem();

		SmeltyMaterial pureMaterial = getPureMaterial(item);
		if (pureMaterial != null) {
			AlloyComposition comp = new AlloyComposition();
			comp.addMaterial(pureMaterial, 100);
			return comp;
		}

		if (item == SmeltyItems.ALLOY_INGOT) {
			CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			if (cmd != null && !cmd.floats().isEmpty()) {
				return AlloyComposition.fromPercentages(cmd.floats());
			}
		}
		return null;
	}

	private static SmeltyMaterial getPureMaterial(Item item) {
		if (item == Items.COPPER_INGOT) return SmeltyMaterial.COPPER;
		if (item == Items.IRON_INGOT) return SmeltyMaterial.IRON;
		if (item == Items.GOLD_INGOT) return SmeltyMaterial.GOLD;
		if (item == SmeltyItems.CASTED_DIAMOND) return SmeltyMaterial.DIAMOND;
		if (item == Items.NETHERITE_INGOT) return SmeltyMaterial.NETHERITE;
		if (item == SmeltyItems.OBSIDIAN_INGOT) return SmeltyMaterial.OBSIDIAN;
		if (item == SmeltyItems.CASTED_EMERALD) return SmeltyMaterial.EMERALD;
		return null;
	}

	private static Item getOutputItem(SmeltyArmorType armorType) {
		return switch (armorType) {
			case HELMET -> SmeltyItems.ALLOY_HELMET;
			case CHESTPLATE -> SmeltyItems.ALLOY_CHESTPLATE;
			case LEGGINGS -> SmeltyItems.ALLOY_LEGGINGS;
			case BOOTS -> SmeltyItems.ALLOY_BOOTS;
		};
	}

	@Override
	public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
		return SmeltyArmorRecipeSerializer.INSTANCE;
	}
}

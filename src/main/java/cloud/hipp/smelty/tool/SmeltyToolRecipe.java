package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.World;

import java.util.List;

public class SmeltyToolRecipe extends SpecialCraftingRecipe {

	private static final int STICK_COLOR = 0xC8A060;

	public SmeltyToolRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	// Pattern definitions: 'I' = ingot, 'R' = rod, ' ' = empty
	// Each pattern is [height][width]
	private static final char[][] SWORD_PATTERN = {
			{'I'},
			{'I'},
			{'R'}
	};
	private static final char[][] PICKAXE_PATTERN = {
			{'I', 'I', 'I'},
			{' ', 'R', ' '},
			{' ', 'R', ' '}
	};
	private static final char[][] AXE_PATTERN = {
			{'I', 'I'},
			{'I', 'R'},
			{' ', 'R'}
	};
	private static final char[][] AXE_PATTERN_MIRROR = {
			{'I', 'I'},
			{'R', 'I'},
			{'R', ' '}
	};
	private static final char[][] SHOVEL_PATTERN = {
			{'I'},
			{'R'},
			{'R'}
	};
	private static final char[][] HOE_PATTERN = {
			{'I', 'I'},
			{' ', 'R'},
			{' ', 'R'}
	};
	private static final char[][] HOE_PATTERN_MIRROR = {
			{'I', 'I'},
			{'R', ' '},
			{'R', ' '}
	};
	private static final char[][] SPEAR_PATTERN = {
			{' ', ' ', 'I'},
			{' ', 'R', ' '},
			{'R', ' ', ' '}
	};
	private static final char[][] SPEAR_PATTERN_MIRROR = {
			{'I', ' ', ' '},
			{' ', 'R', ' '},
			{' ', ' ', 'R'}
	};

	private record PatternMatch(SmeltyToolType toolType, char[][] pattern) {}

	private static final PatternMatch[] ALL_PATTERNS = {
			new PatternMatch(SmeltyToolType.SWORD, SWORD_PATTERN),
			new PatternMatch(SmeltyToolType.PICKAXE, PICKAXE_PATTERN),
			new PatternMatch(SmeltyToolType.AXE, AXE_PATTERN),
			new PatternMatch(SmeltyToolType.AXE, AXE_PATTERN_MIRROR),
			new PatternMatch(SmeltyToolType.SHOVEL, SHOVEL_PATTERN),
			new PatternMatch(SmeltyToolType.HOE, HOE_PATTERN),
			new PatternMatch(SmeltyToolType.HOE, HOE_PATTERN_MIRROR),
			new PatternMatch(SmeltyToolType.SPEAR, SPEAR_PATTERN),
			new PatternMatch(SmeltyToolType.SPEAR, SPEAR_PATTERN_MIRROR),
	};

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		return findMatch(input) != null;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		MatchResult result = findMatch(input);
		if (result == null) return ItemStack.EMPTY;

		SmeltyToolType toolType = result.toolType;
		AlloyComposition headComp = result.headComposition;
		AlloyComposition handleComp = result.handleComposition; // null for sticks

		// Merge head and handle into a single composition for stat computation
		// The tool's stats come from the combined alloy
		AlloyComposition combined = new AlloyComposition();
		combined.mergeFrom(headComp);
		if (handleComp != null) {
			combined.mergeFrom(handleComp);
		}

		// Compute stats using new formula system
		double attackDamage = ToolStatCalculator.computeAttackDamage(combined, toolType);
		double attackSpeed = ToolStatCalculator.computeAttackSpeed(combined);
		double miningSpeed = ToolStatCalculator.computeMiningSpeed(combined);
		int durability = ToolStatCalculator.computeDurability(combined);
		TagKey<Block> incorrectTag = ToolStatCalculator.computeIncorrectBlocksTag(combined);

		// Get output item
		Item outputItem = getOutputItem(toolType);
		ItemStack stack = new ItemStack(outputItem);

		// Store composition data for dynamic naming + tooltip stats
		// Floats: [head material percentages (7), miningSpeed, miningTierIndex,
		//          handle material percentages (7), attackDamage, attackSpeed, tier]
		AlloyComposition normalizedHead = headComp.toNormalized(AlloyComposition.ITEM_RATIO_BASE);
		AlloyComposition normalizedHandle = handleComp != null
				? handleComp.toNormalized(AlloyComposition.ITEM_RATIO_BASE) : null;
		List<Float> floats = new java.util.ArrayList<>();
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			floats.add((float) normalizedHead.getMaterials().getOrDefault(mat, 0));
		}
		floats.add((float) miningSpeed);
		floats.add((float) ToolStatCalculator.computeMiningTierIndex(combined));
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			floats.add(normalizedHandle != null
					? (float) normalizedHandle.getMaterials().getOrDefault(mat, 0) : 0f);
		}
		floats.add((float) attackDamage);
		floats.add((float) attackSpeed);
		floats.add((float) combined.getTier());
		int handleColor = normalizedHandle != null
				? normalizedHandle.getBlendedColor() : STICK_COLOR;
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
				new CustomModelDataComponent(
						floats, List.of(), List.of(),
						List.of(normalizedHead.getBlendedColor(), handleColor)));

		// Set durability
		stack.set(DataComponentTypes.MAX_DAMAGE, durability);

		// Set attribute modifiers (attack damage and speed)
		// attackDamage from formula is total damage; modifier = total - 1.0 (base hand damage)
		double attackDamageMod = Math.max(0, attackDamage - 1.0);
		double attackSpeedMod = attackSpeed - 4.0;
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS,
				AttributeModifiersComponent.builder()
						.add(EntityAttributes.ATTACK_DAMAGE,
								new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
										(float) attackDamageMod, EntityAttributeModifier.Operation.ADD_VALUE),
								AttributeModifierSlot.MAINHAND)
						.add(EntityAttributes.ATTACK_SPEED,
								new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID,
										(float) attackSpeedMod, EntityAttributeModifier.Operation.ADD_VALUE),
								AttributeModifierSlot.MAINHAND)
						.build());

		// Set tool component for mining
		RegistryEntryLookup<Block> blockLookup = lookup.getOrThrow(RegistryKeys.BLOCK);
		if (toolType == SmeltyToolType.SWORD) {
			// Sword: fast cobweb breaking, sword-efficient blocks
			stack.set(DataComponentTypes.TOOL, new ToolComponent(
					List.of(
							ToolComponent.Rule.ofAlwaysDropping(
									net.minecraft.registry.entry.RegistryEntryList.of(net.minecraft.block.Blocks.COBWEB.getRegistryEntry()), 15.0f),
							ToolComponent.Rule.of(blockLookup.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
							ToolComponent.Rule.of(blockLookup.getOrThrow(BlockTags.SWORD_EFFICIENT), 1.5f)
					), 1.0f, 2, false));
			stack.set(DataComponentTypes.WEAPON, new WeaponComponent(1));
		} else if (toolType.getMineableTag() != null) {
			// Mining tools
			stack.set(DataComponentTypes.TOOL, new ToolComponent(
					List.of(
							ToolComponent.Rule.ofNeverDropping(blockLookup.getOrThrow(incorrectTag)),
							ToolComponent.Rule.ofAlwaysDropping(blockLookup.getOrThrow(toolType.getMineableTag()), (float) miningSpeed)
					), 1.0f, 1, true));
			float blockingDisable = toolType == SmeltyToolType.AXE ? 5.0f : 0.0f;
			stack.set(DataComponentTypes.WEAPON, new WeaponComponent(2, blockingDisable));
		} else {
			// Spear: melee weapon, no special mining
			stack.set(DataComponentTypes.WEAPON, new WeaponComponent(1));
		}

		// Set enchantability
		stack.set(DataComponentTypes.ENCHANTABLE, new net.minecraft.component.type.EnchantableComponent(14));

		// Hide vanilla attribute modifiers tooltip (replaced by custom Smelty tooltip)
		stack.set(DataComponentTypes.TOOLTIP_DISPLAY,
				net.minecraft.component.type.TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.ATTRIBUTE_MODIFIERS, true));

		return stack;
	}

	private record MatchResult(SmeltyToolType toolType, AlloyComposition headComposition, AlloyComposition handleComposition) {}

	private MatchResult findMatch(CraftingRecipeInput input) {
		int w = input.getWidth();
		int h = input.getHeight();

		for (PatternMatch pm : ALL_PATTERNS) {
			char[][] pattern = pm.pattern;
			int ph = pattern.length;
			int pw = pattern[0].length;
			if (w != pw || h != ph) continue;

			MatchResult result = tryMatchPattern(input, pattern, pm.toolType);
			if (result != null) return result;
		}
		return null;
	}

	private MatchResult tryMatchPattern(CraftingRecipeInput input, char[][] pattern, SmeltyToolType toolType) {
		int w = input.getWidth();
		int h = input.getHeight();

		// First pass: check that all slots match the pattern character type
		ItemStack firstIngot = null;
		ItemStack firstRod = null;

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
				} else if (expected == 'R') {
					if (!isValidRod(stack)) return null;
					if (firstRod == null) {
						firstRod = stack;
					} else if (!areSameMaterial(firstRod, stack)) {
						return null;
					}
				} else {
					// Expected empty
					if (!stack.isEmpty()) return null;
				}
			}
		}

		if (firstIngot == null || firstRod == null) return null;

		AlloyComposition headComp = getComposition(firstIngot);
		if (headComp == null) return null;
		// handleComp is null for sticks (wooden handles add no material properties)
		AlloyComposition handleComp = getComposition(firstRod);

		return new MatchResult(toolType, headComp, handleComp);
	}

	// --- Material validation ---

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

	private static boolean isValidRod(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Item item = stack.getItem();
		return item == Items.STICK
				|| item == SmeltyItems.COPPER_ROD
				|| item == SmeltyItems.IRON_ROD
				|| item == SmeltyItems.GOLD_ROD
				|| item == SmeltyItems.DIAMOND_ROD
				|| item == SmeltyItems.NETHERITE_ROD
				|| item == SmeltyItems.OBSIDIAN_ROD
				|| item == SmeltyItems.EMERALD_ROD
				|| item == SmeltyItems.ALLOY_ROD;
	}

	/**
	 * Two items are "the same material" if they are the same Item type,
	 * and for alloy items, they also have matching compositions.
	 */
	private static boolean areSameMaterial(ItemStack a, ItemStack b) {
		if (!a.isOf(b.getItem())) return false;
		// For alloy items, compare CustomModelData percentages
		if (a.isOf(SmeltyItems.ALLOY_INGOT) || a.isOf(SmeltyItems.ALLOY_ROD)) {
			CustomModelDataComponent cmdA = a.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			CustomModelDataComponent cmdB = b.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			if (cmdA == null || cmdB == null) return cmdA == cmdB;
			return cmdA.floats().equals(cmdB.floats());
		}
		return true;
	}

	/**
	 * Extract the AlloyComposition from an ingot or rod item stack.
	 */
	private static AlloyComposition getComposition(ItemStack stack) {
		Item item = stack.getItem();

		// Pure material items
		SmeltyMaterial pureMaterial = getPureMaterial(item);
		if (pureMaterial != null) {
			AlloyComposition comp = new AlloyComposition();
			comp.addMaterial(pureMaterial, 100);
			return comp;
		}

		// Alloy items: extract from CustomModelData
		if (item == SmeltyItems.ALLOY_INGOT || item == SmeltyItems.ALLOY_ROD) {
			CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			if (cmd != null && !cmd.floats().isEmpty()) {
				return AlloyComposition.fromPercentages(cmd.floats());
			}
		}
		return null;
	}

	private static SmeltyMaterial getPureMaterial(Item item) {
		if (item == Items.COPPER_INGOT || item == SmeltyItems.COPPER_ROD) return SmeltyMaterial.COPPER;
		if (item == Items.IRON_INGOT || item == SmeltyItems.IRON_ROD) return SmeltyMaterial.IRON;
		if (item == Items.GOLD_INGOT || item == SmeltyItems.GOLD_ROD) return SmeltyMaterial.GOLD;
		if (item == SmeltyItems.CASTED_DIAMOND || item == SmeltyItems.DIAMOND_ROD) return SmeltyMaterial.DIAMOND;
		if (item == Items.NETHERITE_INGOT || item == SmeltyItems.NETHERITE_ROD) return SmeltyMaterial.NETHERITE;
		if (item == SmeltyItems.OBSIDIAN_INGOT || item == SmeltyItems.OBSIDIAN_ROD) return SmeltyMaterial.OBSIDIAN;
		if (item == SmeltyItems.CASTED_EMERALD || item == SmeltyItems.EMERALD_ROD) return SmeltyMaterial.EMERALD;
		return null;
	}

	private static Item getOutputItem(SmeltyToolType toolType) {
		return switch (toolType) {
			case SWORD -> SmeltyItems.ALLOY_SWORD;
			case PICKAXE -> SmeltyItems.ALLOY_PICKAXE;
			case AXE -> SmeltyItems.ALLOY_AXE;
			case SHOVEL -> SmeltyItems.ALLOY_SHOVEL;
			case HOE -> SmeltyItems.ALLOY_HOE;
			case SPEAR -> SmeltyItems.ALLOY_SPEAR;
		};
	}

	@Override
	public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
		return SmeltyToolRecipeSerializer.INSTANCE;
	}
}

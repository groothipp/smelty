package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.ClientAlloyRegistry;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/**
 * Base tool item that dynamically resolves its name from the stored alloy composition.
 * Used for swords, pickaxes, and spears (which don't need special useOnBlock behavior).
 */
public class AlloyToolItem extends Item {
	private final SmeltyToolType toolType;

	public AlloyToolItem(Settings settings, SmeltyToolType toolType) {
		super(settings);
		this.toolType = toolType;
	}

	@Override
	public Text getName(ItemStack stack) {
		Text name = getToolName(stack, toolType.getDisplayName());
		return name != null ? name : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
							  Consumer<Text> tooltip, TooltipType type) {
		appendToolStats(stack, tooltip, toolType);
		super.appendTooltip(stack, context, displayComponent, tooltip, type);
	}

	/**
	 * Resolve the tool name from stored composition data.
	 * Pure material -> "Material Tool"
	 * Named alloy -> "AlloyName Tool"
	 * Unnamed alloy -> "Alloy Tool"
	 */
	public static Text getToolName(ItemStack stack, String toolTypeName) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd != null && !cmd.floats().isEmpty()) {
			AlloyComposition comp = AlloyComposition.fromPercentages(cmd.floats());
			if (comp.getMaterials().size() == 1 && comp.getModifiers().isEmpty()) {
				SmeltyMaterial material = comp.getMaterials().keySet().iterator().next();
				return Text.literal(material.getDisplayName() + " " + toolTypeName);
			}
			String name = ClientAlloyRegistry.getAlloyName(comp);
			if (name != null) {
				return Text.literal(name + " " + toolTypeName);
			}
			return Text.literal("Alloy " + toolTypeName);
		}
		return null;
	}

	/**
	 * Append tool-type-specific stats to tooltip from CustomModelData.
	 * Floats layout: [0-6] = head composition (7 materials), [7] = miningSpeed, [8] = miningTierIndex,
	 *                [9-15] = handle composition (7 materials), [16] = attackDamage, [17] = attackSpeed, [18] = tier
	 *
	 * Mining tools (pickaxe, axe, shovel, hoe): Mining Tier, Mining Speed, Durability
	 * Weapons (sword, spear): Damage, Attack Speed, Durability
	 */
	public static void appendToolStats(ItemStack stack, Consumer<Text> tooltip, SmeltyToolType toolType) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd.floats().size() < 9) return;

		int matCount = SmeltyMaterial.values().length; // 7
		int miningSpeedIdx = matCount;     // 7
		int tierIdx = matCount + 1;        // 8
		int atkDmgIdx = matCount + 2 + matCount;     // 16
		int atkSpdIdx = matCount + 2 + matCount + 1; // 17
		int toolTierIdx = matCount + 2 + matCount + 2; // 18

		boolean isMiningTool = toolType.getMineableTag() != null;

		// Show alloy tier
		if (cmd.floats().size() > toolTierIdx) {
			int tier = Math.round(cmd.floats().get(toolTierIdx));
			if (tier >= 1 && tier <= AlloyComposition.TIER_NAMES.length) {
				tooltip.accept(Text.literal("Tier " + AlloyComposition.TIER_NAMES[tier - 1])
						.formatted(Formatting.GOLD));
			}
		}

		if (isMiningTool) {
			int miningTierIndex = Math.round(cmd.floats().get(tierIdx));
			if (miningTierIndex >= 0 && miningTierIndex < ToolStatCalculator.MINING_TIER_NAMES.length) {
				tooltip.accept(Text.literal("Mining Tier: " + ToolStatCalculator.MINING_TIER_NAMES[miningTierIndex])
						.formatted(Formatting.GRAY));
			}

			float miningSpeed = cmd.floats().get(miningSpeedIdx);
			tooltip.accept(Text.literal("Mining Speed: " + String.format("%.1f", miningSpeed))
					.formatted(Formatting.DARK_GREEN));
		} else {
			// Weapons: show damage and attack speed
			if (cmd.floats().size() > atkSpdIdx) {
				float attackDamage = cmd.floats().get(atkDmgIdx);
				float attackSpeed = cmd.floats().get(atkSpdIdx);
				tooltip.accept(Text.literal("Damage: " + String.format("%.1f", attackDamage))
						.formatted(Formatting.DARK_RED));
				tooltip.accept(Text.literal("Attack Speed: " + String.format("%.1f", attackSpeed))
						.formatted(Formatting.DARK_GREEN));
			}
		}

		// Durability for all tool types
		Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
		if (maxDamage != null) {
			int currentDamage = stack.getOrDefault(DataComponentTypes.DAMAGE, 0);
			int remaining = maxDamage - currentDamage;
			tooltip.accept(Text.literal("Durability: " + remaining + "/" + maxDamage)
					.formatted(Formatting.BLUE));
		}
	}
}

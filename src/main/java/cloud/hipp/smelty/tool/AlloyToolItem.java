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
			if (comp.getMaterials().size() == 1) {
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
	 * Floats layout: [0-4] = head composition, [5] = miningSpeed, [6] = miningTierIndex,
	 *                [7-11] = handle composition, [12] = attackDamage, [13] = attackSpeed
	 *
	 * Mining tools (pickaxe, axe, shovel, hoe): Mining Tier, Mining Speed, Durability
	 * Weapons (sword, spear): Damage, Attack Speed, Durability
	 */
	public static void appendToolStats(ItemStack stack, Consumer<Text> tooltip, SmeltyToolType toolType) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd.floats().size() < 7) return;

		boolean isMiningTool = toolType.getMineableTag() != null;

		if (isMiningTool) {
			int tierIndex = Math.round(cmd.floats().get(6));
			if (tierIndex >= 0 && tierIndex < ToolStatCalculator.TIER_NAMES.length) {
				tooltip.accept(Text.literal("Mining Tier: " + ToolStatCalculator.TIER_NAMES[tierIndex])
						.formatted(Formatting.GRAY));
			}

			float miningSpeed = cmd.floats().get(5);
			tooltip.accept(Text.literal("Mining Speed: " + String.format("%.1f", miningSpeed))
					.formatted(Formatting.DARK_GREEN));
		} else {
			// Weapons: show damage and attack speed
			if (cmd.floats().size() >= 14) {
				float attackDamage = cmd.floats().get(12);
				float attackSpeed = cmd.floats().get(13);
				tooltip.accept(Text.literal("Damage: " + String.format("%.1f", attackDamage))
						.formatted(Formatting.DARK_RED));
				tooltip.accept(Text.literal("Attack Speed: " + String.format("%.1f", attackSpeed))
						.formatted(Formatting.DARK_GREEN));
			}
		}

		// Durability for all tool types
		Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
		if (maxDamage != null) {
			tooltip.accept(Text.literal("Durability: " + maxDamage)
					.formatted(Formatting.BLUE));
		}
	}
}

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
	private final String toolTypeName;

	public AlloyToolItem(Settings settings, String toolTypeName) {
		super(settings);
		this.toolTypeName = toolTypeName;
	}

	@Override
	public Text getName(ItemStack stack) {
		Text name = getToolName(stack, toolTypeName);
		return name != null ? name : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
							  Consumer<Text> tooltip, TooltipType type) {
		appendToolStats(stack, tooltip);
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
	 * Append mining speed and mining tier to tooltip if stored in CustomModelData.
	 * Floats layout: [0-4] = composition, [5] = miningSpeed, [6] = miningTierIndex
	 */
	public static void appendToolStats(ItemStack stack, Consumer<Text> tooltip) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd.floats().size() < 7) return;

		float miningSpeed = cmd.floats().get(5);
		int tierIndex = Math.round(cmd.floats().get(6));

		if (miningSpeed > 1.0f) {
			tooltip.accept(Text.literal("Mining Speed: " + String.format("%.1f", miningSpeed))
					.formatted(Formatting.DARK_GREEN));
		}

		if (tierIndex >= 0 && tierIndex < ToolStatCalculator.TIER_NAMES.length) {
			tooltip.accept(Text.literal("Mining Tier: " + ToolStatCalculator.TIER_NAMES[tierIndex])
					.formatted(Formatting.GRAY));
		}
	}
}

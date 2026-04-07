package cloud.hipp.smelty.tool;

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
 * Armor item with dynamic naming and custom tooltip based on stored alloy composition.
 */
public class AlloyArmorItem extends Item {
	private final String armorTypeName;

	public AlloyArmorItem(Settings settings, String armorTypeName) {
		super(settings);
		this.armorTypeName = armorTypeName;
	}

	@Override
	public Text getName(ItemStack stack) {
		Text name = AlloyToolItem.getToolName(stack, armorTypeName);
		return name != null ? name : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
							  Consumer<Text> tooltip, TooltipType type) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		int matCount = cloud.hipp.smelty.material.SmeltyMaterial.values().length; // 7
		int defenseIdx = matCount;     // 7
		int tierIdx = matCount + 1;    // 8

		if (cmd != null && cmd.floats().size() > tierIdx) {
			int tier = Math.round(cmd.floats().get(tierIdx));
			if (tier >= 1 && tier <= cloud.hipp.smelty.material.AlloyComposition.TIER_NAMES.length) {
				tooltip.accept(Text.literal("Tier " + cloud.hipp.smelty.material.AlloyComposition.TIER_NAMES[tier - 1])
						.formatted(Formatting.GOLD));
			}
		}

		if (cmd != null && cmd.floats().size() > defenseIdx) {
			int defense = Math.round(cmd.floats().get(defenseIdx));
			tooltip.accept(Text.literal("Armor: " + defense)
					.formatted(Formatting.DARK_GREEN));
		}

		Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
		if (maxDamage != null) {
			int currentDamage = stack.getOrDefault(DataComponentTypes.DAMAGE, 0);
			int remaining = maxDamage - currentDamage;
			tooltip.accept(Text.literal("Durability: " + remaining + "/" + maxDamage)
					.formatted(Formatting.BLUE));
		}

		super.appendTooltip(stack, context, displayComponent, tooltip, type);
	}
}

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
		if (cmd != null && cmd.floats().size() >= 6) {
			int defense = Math.round(cmd.floats().get(5));
			tooltip.accept(Text.literal("Armor: " + defense)
					.formatted(Formatting.DARK_GREEN));
		}

		Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
		if (maxDamage != null) {
			tooltip.accept(Text.literal("Durability: " + maxDamage)
					.formatted(Formatting.BLUE));
		}

		super.appendTooltip(stack, context, displayComponent, tooltip, type);
	}
}

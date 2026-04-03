package cloud.hipp.smelty.tool;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class AlloyAxeItem extends AxeItem {
	public AlloyAxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Settings settings) {
		super(material, attackDamage, attackSpeed, settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		Text name = AlloyToolItem.getToolName(stack, "Axe");
		return name != null ? name : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
							  Consumer<Text> tooltip, TooltipType type) {
		AlloyToolItem.appendToolStats(stack, tooltip);
		super.appendTooltip(stack, context, displayComponent, tooltip, type);
	}
}

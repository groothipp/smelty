package cloud.hipp.smelty.tool;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class AlloyHoeItem extends HoeItem {
	public AlloyHoeItem(ToolMaterial material, float attackDamage, float attackSpeed, Settings settings) {
		super(material, attackDamage, attackSpeed, settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		Text name = AlloyToolItem.getToolName(stack, "Hoe");
		return name != null ? name : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
							  Consumer<Text> tooltip, TooltipType type) {
		AlloyToolItem.appendToolStats(stack, tooltip, SmeltyToolType.HOE);
		super.appendTooltip(stack, context, displayComponent, tooltip, type);
	}
}

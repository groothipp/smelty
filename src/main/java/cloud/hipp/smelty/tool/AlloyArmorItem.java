package cloud.hipp.smelty.tool;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Armor item with dynamic naming based on stored alloy composition.
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
}

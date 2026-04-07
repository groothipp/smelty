package cloud.hipp.smelty.item;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.ClientAlloyRegistry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Item subclass for alloy items (ingot, nugget, rod, plate) that dynamically
 * resolves the alloy name from the client-side registry.
 */
public class AlloyItem extends Item {
	private final String suffix;

	public AlloyItem(Settings settings, String suffix) {
		super(settings);
		this.suffix = suffix;
	}

	@Override
	public Text getName(ItemStack stack) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd != null && !cmd.floats().isEmpty()) {
			AlloyComposition comp = AlloyComposition.fromPercentages(cmd.floats());
			String name = ClientAlloyRegistry.getAlloyName(comp);
			if (name != null) {
				return Text.literal(name + " " + suffix);
			}
		}
		return super.getName(stack);
	}
}

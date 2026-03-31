package cloud.hipp.smelty.item;

import cloud.hipp.smelty.block.SmeltyBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;

public class SmeltyItems {
	public static final Item SMELTER_BLOCK = Items.register(SmeltyBlocks.SMELTER_BLOCK);
	public static final Item SMELTER_CONTROLLER = Items.register(SmeltyBlocks.SMELTER_CONTROLLER);

	public static void initialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(SMELTER_BLOCK);
			entries.add(SMELTER_CONTROLLER);
		});
	}
}

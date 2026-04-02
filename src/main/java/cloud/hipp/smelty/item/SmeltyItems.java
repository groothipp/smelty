package cloud.hipp.smelty.item;

import cloud.hipp.smelty.block.SmeltyBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;

public class SmeltyItems {
	public static final Item SMELTER_BLOCK = Items.register(SmeltyBlocks.SMELTER_BLOCK);
	public static final Item VALVE = Items.register(SmeltyBlocks.VALVE);
	public static final Item SMELTER_CONTROLLER = Items.register(SmeltyBlocks.SMELTER_CONTROLLER);
	public static final Item CHANNEL = Items.register(SmeltyBlocks.CHANNEL);
	public static final Item CASTING_BASIN = Items.register(SmeltyBlocks.CASTING_BASIN);
	public static final Item CASTING_TABLE = Items.register(SmeltyBlocks.CASTING_TABLE);
	public static final Item SOLID_ALLOY = Items.register(SmeltyBlocks.SOLID_ALLOY);

	public static void initialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(SMELTER_BLOCK);
			entries.add(VALVE);
			entries.add(SMELTER_CONTROLLER);
			entries.add(CHANNEL);
			entries.add(CASTING_BASIN);
			entries.add(CASTING_TABLE);
			entries.add(SOLID_ALLOY);
		});
	}
}

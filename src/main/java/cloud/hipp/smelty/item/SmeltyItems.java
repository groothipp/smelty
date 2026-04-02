package cloud.hipp.smelty.item;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.EnumMap;

public class SmeltyItems {
	// Block items
	public static final Item SMELTER_BLOCK = Items.register(SmeltyBlocks.SMELTER_BLOCK);
	public static final Item VALVE = Items.register(SmeltyBlocks.VALVE);
	public static final Item SMELTER_CONTROLLER = Items.register(SmeltyBlocks.SMELTER_CONTROLLER);
	public static final Item CHANNEL = Items.register(SmeltyBlocks.CHANNEL);
	public static final Item CASTING_BASIN = Items.register(SmeltyBlocks.CASTING_BASIN);
	public static final Item CASTING_TABLE = Items.register(SmeltyBlocks.CASTING_TABLE);
	public static final Item SOLID_ALLOY = Items.register(SmeltyBlocks.SOLID_ALLOY);

	// Plates (one per material + alloy for mixed compositions)
	public static final Item COPPER_PLATE = register("copper_plate");
	public static final Item IRON_PLATE = register("iron_plate");
	public static final Item GOLD_PLATE = register("gold_plate");
	public static final Item DIAMOND_PLATE = register("diamond_plate");
	public static final Item NETHERITE_PLATE = register("netherite_plate");
	public static final Item ALLOY_PLATE = register("alloy_plate");

	// Molds
	public static final Item INGOT_MOLD = register("ingot_mold");
	public static final Item NUGGET_MOLD = register("nugget_mold");
	public static final Item ROD_MOLD = register("rod_mold");

	private static final EnumMap<SmeltyMaterial, Item> PLATE_MAP = new EnumMap<>(SmeltyMaterial.class);

	static {
		PLATE_MAP.put(SmeltyMaterial.COPPER, COPPER_PLATE);
		PLATE_MAP.put(SmeltyMaterial.IRON, IRON_PLATE);
		PLATE_MAP.put(SmeltyMaterial.GOLD, GOLD_PLATE);
		PLATE_MAP.put(SmeltyMaterial.DIAMOND, DIAMOND_PLATE);
		PLATE_MAP.put(SmeltyMaterial.NETHERITE, NETHERITE_PLATE);
	}

	public static Item getPlateForMaterial(SmeltyMaterial material) {
		return PLATE_MAP.get(material);
	}

	private static Item register(String id) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		return Registry.register(Registries.ITEM, key, new Item(new Item.Settings().registryKey(key)));
	}

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
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(COPPER_PLATE);
			entries.add(IRON_PLATE);
			entries.add(GOLD_PLATE);
			entries.add(DIAMOND_PLATE);
			entries.add(NETHERITE_PLATE);
			entries.add(ALLOY_PLATE);
			entries.add(INGOT_MOLD);
			entries.add(NUGGET_MOLD);
			entries.add(ROD_MOLD);
		});
	}
}

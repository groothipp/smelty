package cloud.hipp.smelty.item;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.material.SmeltyMaterial;
import cloud.hipp.smelty.tool.AlloyArmorItem;
import cloud.hipp.smelty.tool.AlloyAxeItem;
import cloud.hipp.smelty.tool.AlloyHoeItem;
import cloud.hipp.smelty.tool.AlloyShovelItem;
import cloud.hipp.smelty.tool.AlloyToolItem;
import cloud.hipp.smelty.tool.SmeltyToolType;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
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
	public static final Item ANALYSIS_BENCH = Items.register(SmeltyBlocks.ANALYSIS_BENCH);
	public static final Item SOLID_ALLOY = Items.register(SmeltyBlocks.SOLID_ALLOY);

	// Plates (one per material + alloy for mixed compositions)
	public static final Item COPPER_PLATE = register("copper_plate");
	public static final Item IRON_PLATE = register("iron_plate");
	public static final Item GOLD_PLATE = register("gold_plate");
	public static final Item DIAMOND_PLATE = register("diamond_plate");
	public static final Item NETHERITE_PLATE = register("netherite_plate");
	public static final Item ALLOY_PLATE = registerAlloy("alloy_plate", "Plate");

	// Molds
	public static final Item INGOT_MOLD = register("ingot_mold");
	public static final Item NUGGET_MOLD = register("nugget_mold");
	public static final Item ROD_MOLD = register("rod_mold");

	// Cast ingots (materials without vanilla ingots)
	public static final Item DIAMOND_INGOT = register("diamond_ingot");

	// Cast nuggets (materials without vanilla nuggets)
	public static final Item DIAMOND_NUGGET = register("diamond_nugget");
	public static final Item NETHERITE_NUGGET = register("netherite_nugget");

	// Rods (all materials)
	public static final Item COPPER_ROD = register("copper_rod");
	public static final Item IRON_ROD = register("iron_rod");
	public static final Item GOLD_ROD = register("gold_rod");
	public static final Item DIAMOND_ROD = register("diamond_rod");
	public static final Item NETHERITE_ROD = register("netherite_rod");

	// Alloy cast outputs (mixed compositions)
	public static final Item ALLOY_INGOT = registerAlloy("alloy_ingot", "Ingot");
	public static final Item ALLOY_NUGGET = registerAlloy("alloy_nugget", "Nugget");
	public static final Item ALLOY_ROD = registerAlloy("alloy_rod", "Rod");

	// Tools
	public static final Item ALLOY_SWORD = registerTool("alloy_sword", SmeltyToolType.SWORD);
	public static final Item ALLOY_PICKAXE = registerTool("alloy_pickaxe", SmeltyToolType.PICKAXE);
	public static final Item ALLOY_AXE = registerAxe("alloy_axe");
	public static final Item ALLOY_SHOVEL = registerShovel("alloy_shovel");
	public static final Item ALLOY_HOE = registerHoe("alloy_hoe");
	public static final Item ALLOY_SPEAR = registerTool("alloy_spear", SmeltyToolType.SPEAR);

	// Armor
	public static final Item ALLOY_HELMET = registerArmor("alloy_helmet", "Helmet");
	public static final Item ALLOY_CHESTPLATE = registerArmor("alloy_chestplate", "Chestplate");
	public static final Item ALLOY_LEGGINGS = registerArmor("alloy_leggings", "Leggings");
	public static final Item ALLOY_BOOTS = registerArmor("alloy_boots", "Boots");

	private static final EnumMap<SmeltyMaterial, Item> PLATE_MAP = new EnumMap<>(SmeltyMaterial.class);
	private static final EnumMap<SmeltyMaterial, Item> CAST_INGOT_MAP = new EnumMap<>(SmeltyMaterial.class);
	private static final EnumMap<SmeltyMaterial, Item> CAST_NUGGET_MAP = new EnumMap<>(SmeltyMaterial.class);
	private static final EnumMap<SmeltyMaterial, Item> ROD_MAP = new EnumMap<>(SmeltyMaterial.class);

	static {
		PLATE_MAP.put(SmeltyMaterial.COPPER, COPPER_PLATE);
		PLATE_MAP.put(SmeltyMaterial.IRON, IRON_PLATE);
		PLATE_MAP.put(SmeltyMaterial.GOLD, GOLD_PLATE);
		PLATE_MAP.put(SmeltyMaterial.DIAMOND, DIAMOND_PLATE);
		PLATE_MAP.put(SmeltyMaterial.NETHERITE, NETHERITE_PLATE);

		CAST_INGOT_MAP.put(SmeltyMaterial.COPPER, Items.COPPER_INGOT);
		CAST_INGOT_MAP.put(SmeltyMaterial.IRON, Items.IRON_INGOT);
		CAST_INGOT_MAP.put(SmeltyMaterial.GOLD, Items.GOLD_INGOT);
		CAST_INGOT_MAP.put(SmeltyMaterial.DIAMOND, DIAMOND_INGOT);
		CAST_INGOT_MAP.put(SmeltyMaterial.NETHERITE, Items.NETHERITE_INGOT);

		CAST_NUGGET_MAP.put(SmeltyMaterial.IRON, Items.IRON_NUGGET);
		CAST_NUGGET_MAP.put(SmeltyMaterial.GOLD, Items.GOLD_NUGGET);
		CAST_NUGGET_MAP.put(SmeltyMaterial.DIAMOND, DIAMOND_NUGGET);
		CAST_NUGGET_MAP.put(SmeltyMaterial.NETHERITE, NETHERITE_NUGGET);

		ROD_MAP.put(SmeltyMaterial.COPPER, COPPER_ROD);
		ROD_MAP.put(SmeltyMaterial.IRON, IRON_ROD);
		ROD_MAP.put(SmeltyMaterial.GOLD, GOLD_ROD);
		ROD_MAP.put(SmeltyMaterial.DIAMOND, DIAMOND_ROD);
		ROD_MAP.put(SmeltyMaterial.NETHERITE, NETHERITE_ROD);
	}

	public static Item getPlateForMaterial(SmeltyMaterial material) {
		return PLATE_MAP.get(material);
	}

	public static Item getCastIngot(SmeltyMaterial material) {
		return CAST_INGOT_MAP.get(material);
	}

	public static Item getCastNugget(SmeltyMaterial material) {
		return CAST_NUGGET_MAP.get(material);
	}

	public static Item getRodForMaterial(SmeltyMaterial material) {
		return ROD_MAP.get(material);
	}

	private static Item register(String id) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		return Registry.register(Registries.ITEM, key, new Item(new Item.Settings().registryKey(key)));
	}

	private static Item registerAlloy(String id, String suffix) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		return Registry.register(Registries.ITEM, key, new AlloyItem(new Item.Settings().registryKey(key), suffix));
	}

	private static Item registerTool(String id, SmeltyToolType toolType) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		Item.Settings settings = new Item.Settings().registryKey(key)
				.sword(ToolMaterial.IRON, 3.0f, -2.4f);
		return Registry.register(Registries.ITEM, key, new AlloyToolItem(settings, toolType));
	}

	private static Item registerAxe(String id) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		return Registry.register(Registries.ITEM, key,
				new AlloyAxeItem(ToolMaterial.IRON, 6.0f, -3.1f, new Item.Settings().registryKey(key)));
	}

	private static Item registerShovel(String id) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		return Registry.register(Registries.ITEM, key,
				new AlloyShovelItem(ToolMaterial.IRON, 1.5f, -3.0f, new Item.Settings().registryKey(key)));
	}

	private static Item registerHoe(String id) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		return Registry.register(Registries.ITEM, key,
				new AlloyHoeItem(ToolMaterial.IRON, -2.0f, -1.0f, new Item.Settings().registryKey(key)));
	}

	private static Item registerArmor(String id, String armorTypeName) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, id));
		Item.Settings settings = new Item.Settings().registryKey(key).maxDamage(1);
		return Registry.register(Registries.ITEM, key, new AlloyArmorItem(settings, armorTypeName));
	}

	public static void initialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(SMELTER_BLOCK);
			entries.add(VALVE);
			entries.add(SMELTER_CONTROLLER);
			entries.add(CHANNEL);
			entries.add(CASTING_BASIN);
			entries.add(CASTING_TABLE);
			entries.add(ANALYSIS_BENCH);
			entries.add(SOLID_ALLOY);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(COPPER_PLATE);
			entries.add(IRON_PLATE);
			entries.add(GOLD_PLATE);
			entries.add(DIAMOND_PLATE);
			entries.add(NETHERITE_PLATE);
			entries.add(ALLOY_PLATE);
			entries.add(DIAMOND_INGOT);
			entries.add(DIAMOND_NUGGET);
			entries.add(NETHERITE_NUGGET);
			entries.add(COPPER_ROD);
			entries.add(IRON_ROD);
			entries.add(GOLD_ROD);
			entries.add(DIAMOND_ROD);
			entries.add(NETHERITE_ROD);
			entries.add(ALLOY_INGOT);
			entries.add(ALLOY_NUGGET);
			entries.add(ALLOY_ROD);
			entries.add(INGOT_MOLD);
			entries.add(NUGGET_MOLD);
			entries.add(ROD_MOLD);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(ALLOY_SWORD);
			entries.add(ALLOY_AXE);
			entries.add(ALLOY_SPEAR);
			entries.add(ALLOY_HELMET);
			entries.add(ALLOY_CHESTPLATE);
			entries.add(ALLOY_LEGGINGS);
			entries.add(ALLOY_BOOTS);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(ALLOY_PICKAXE);
			entries.add(ALLOY_SHOVEL);
			entries.add(ALLOY_HOE);
		});
	}
}

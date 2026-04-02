package cloud.hipp.smelty.material;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MaterialItems {
	// 1 ingot = 180 units (divisible by 9 for nuggets, by 20 for tick math)
	public static final int UNITS_PER_INGOT = 180;
	public static final int BLOCK_VOLUME = UNITS_PER_INGOT * 9;   // 1620 (9 ingots)
	public static final int INGOT_VOLUME = UNITS_PER_INGOT;       // 180
	public static final int NUGGET_VOLUME = UNITS_PER_INGOT / 9;  // 20

	private static final Map<Item, MaterialEntry> ITEM_MAP = new HashMap<>();
	private static final EnumMap<SmeltyMaterial, Item> INGOT_MAP = new EnumMap<>(SmeltyMaterial.class);
	private static final EnumMap<SmeltyMaterial, Item> BLOCK_MAP = new EnumMap<>(SmeltyMaterial.class);
	private static final EnumMap<SmeltyMaterial, Item> NUGGET_MAP = new EnumMap<>(SmeltyMaterial.class);

	static {
		// Copper
		register(Items.COPPER_INGOT, SmeltyMaterial.COPPER, INGOT_VOLUME, false);
		register(Items.COPPER_BLOCK, SmeltyMaterial.COPPER, BLOCK_VOLUME, false);
		register(Items.RAW_COPPER, SmeltyMaterial.COPPER, INGOT_VOLUME, true);
		INGOT_MAP.put(SmeltyMaterial.COPPER, Items.COPPER_INGOT);
		BLOCK_MAP.put(SmeltyMaterial.COPPER, Items.COPPER_BLOCK);

		// Iron
		register(Items.IRON_INGOT, SmeltyMaterial.IRON, INGOT_VOLUME, false);
		register(Items.IRON_BLOCK, SmeltyMaterial.IRON, BLOCK_VOLUME, false);
		register(Items.IRON_NUGGET, SmeltyMaterial.IRON, NUGGET_VOLUME, false);
		register(Items.RAW_IRON, SmeltyMaterial.IRON, INGOT_VOLUME, true);
		INGOT_MAP.put(SmeltyMaterial.IRON, Items.IRON_INGOT);
		BLOCK_MAP.put(SmeltyMaterial.IRON, Items.IRON_BLOCK);
		NUGGET_MAP.put(SmeltyMaterial.IRON, Items.IRON_NUGGET);

		// Gold
		register(Items.GOLD_INGOT, SmeltyMaterial.GOLD, INGOT_VOLUME, false);
		register(Items.GOLD_BLOCK, SmeltyMaterial.GOLD, BLOCK_VOLUME, false);
		register(Items.GOLD_NUGGET, SmeltyMaterial.GOLD, NUGGET_VOLUME, false);
		register(Items.RAW_GOLD, SmeltyMaterial.GOLD, INGOT_VOLUME, true);
		INGOT_MAP.put(SmeltyMaterial.GOLD, Items.GOLD_INGOT);
		BLOCK_MAP.put(SmeltyMaterial.GOLD, Items.GOLD_BLOCK);
		NUGGET_MAP.put(SmeltyMaterial.GOLD, Items.GOLD_NUGGET);

		// Diamond
		register(Items.DIAMOND, SmeltyMaterial.DIAMOND, INGOT_VOLUME, false);
		register(Items.DIAMOND_BLOCK, SmeltyMaterial.DIAMOND, BLOCK_VOLUME, false);
		INGOT_MAP.put(SmeltyMaterial.DIAMOND, Items.DIAMOND);
		BLOCK_MAP.put(SmeltyMaterial.DIAMOND, Items.DIAMOND_BLOCK);

		// Netherite
		register(Items.NETHERITE_INGOT, SmeltyMaterial.NETHERITE, INGOT_VOLUME, false);
		register(Items.NETHERITE_BLOCK, SmeltyMaterial.NETHERITE, BLOCK_VOLUME, false);
		INGOT_MAP.put(SmeltyMaterial.NETHERITE, Items.NETHERITE_INGOT);
		BLOCK_MAP.put(SmeltyMaterial.NETHERITE, Items.NETHERITE_BLOCK);
	}

	private static void register(Item item, SmeltyMaterial material, int volumeMl, boolean rawOre) {
		ITEM_MAP.put(item, new MaterialEntry(material, volumeMl, rawOre));
	}

	public static @Nullable MaterialEntry lookup(ItemStack stack) {
		return ITEM_MAP.get(stack.getItem());
	}

	public static @Nullable Item getIngotItem(SmeltyMaterial material) {
		return INGOT_MAP.get(material);
	}

	public static @Nullable Item getBlockItem(SmeltyMaterial material) {
		return BLOCK_MAP.get(material);
	}

	public static @Nullable Item getNuggetItem(SmeltyMaterial material) {
		return NUGGET_MAP.get(material);
	}

	/**
	 * If the composition is a single pure material, return vanilla item stacks
	 * (blocks, ingots, nuggets) that represent the volume. Returns null if mixed alloy.
	 */
	public static java.util.@Nullable List<ItemStack> getPureVanillaItems(AlloyComposition composition) {
		var materials = composition.getMaterials();
		if (materials.size() != 1) return null;

		var entry = materials.entrySet().iterator().next();
		SmeltyMaterial material = entry.getKey();
		int volume = entry.getValue();

		java.util.List<ItemStack> items = new java.util.ArrayList<>();
		int remaining = volume;

		// Blocks (9 ingots each)
		Item blockItem = BLOCK_MAP.get(material);
		if (blockItem != null && remaining >= BLOCK_VOLUME) {
			int blocks = remaining / BLOCK_VOLUME;
			items.add(new ItemStack(blockItem, blocks));
			remaining %= BLOCK_VOLUME;
		}

		// Ingots
		Item ingotItem = INGOT_MAP.get(material);
		if (ingotItem != null && remaining >= INGOT_VOLUME) {
			int ingots = remaining / INGOT_VOLUME;
			items.add(new ItemStack(ingotItem, ingots));
			remaining %= INGOT_VOLUME;
		}

		// Nuggets
		Item nuggetItem = NUGGET_MAP.get(material);
		if (nuggetItem != null && remaining >= NUGGET_VOLUME) {
			int nuggets = remaining / NUGGET_VOLUME;
			items.add(new ItemStack(nuggetItem, nuggets));
		}

		return items.isEmpty() ? null : items;
	}

	public record MaterialEntry(SmeltyMaterial material, int volumeMl, boolean rawOre) {
	}
}

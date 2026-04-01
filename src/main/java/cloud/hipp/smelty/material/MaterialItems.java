package cloud.hipp.smelty.material;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MaterialItems {
	public static final int BLOCK_ML = 1000;
	public static final int INGOT_ML = 111;
	public static final int NUGGET_ML = 12;

	private static final Map<Item, MaterialEntry> ITEM_MAP = new HashMap<>();
	private static final EnumMap<SmeltyMaterial, Item> INGOT_MAP = new EnumMap<>(SmeltyMaterial.class);

	static {
		// Copper
		register(Items.COPPER_INGOT, SmeltyMaterial.COPPER, INGOT_ML, false);
		register(Items.COPPER_BLOCK, SmeltyMaterial.COPPER, BLOCK_ML, false);
		register(Items.RAW_COPPER, SmeltyMaterial.COPPER, INGOT_ML, true);
		INGOT_MAP.put(SmeltyMaterial.COPPER, Items.COPPER_INGOT);

		// Iron
		register(Items.IRON_INGOT, SmeltyMaterial.IRON, INGOT_ML, false);
		register(Items.IRON_BLOCK, SmeltyMaterial.IRON, BLOCK_ML, false);
		register(Items.IRON_NUGGET, SmeltyMaterial.IRON, NUGGET_ML, false);
		register(Items.RAW_IRON, SmeltyMaterial.IRON, INGOT_ML, true);
		INGOT_MAP.put(SmeltyMaterial.IRON, Items.IRON_INGOT);

		// Gold
		register(Items.GOLD_INGOT, SmeltyMaterial.GOLD, INGOT_ML, false);
		register(Items.GOLD_BLOCK, SmeltyMaterial.GOLD, BLOCK_ML, false);
		register(Items.GOLD_NUGGET, SmeltyMaterial.GOLD, NUGGET_ML, false);
		register(Items.RAW_GOLD, SmeltyMaterial.GOLD, INGOT_ML, true);
		INGOT_MAP.put(SmeltyMaterial.GOLD, Items.GOLD_INGOT);

		// Diamond
		register(Items.DIAMOND, SmeltyMaterial.DIAMOND, INGOT_ML, false);
		register(Items.DIAMOND_BLOCK, SmeltyMaterial.DIAMOND, BLOCK_ML, false);
		INGOT_MAP.put(SmeltyMaterial.DIAMOND, Items.DIAMOND);

		// Netherite
		register(Items.NETHERITE_INGOT, SmeltyMaterial.NETHERITE, INGOT_ML, false);
		register(Items.NETHERITE_BLOCK, SmeltyMaterial.NETHERITE, BLOCK_ML, false);
		INGOT_MAP.put(SmeltyMaterial.NETHERITE, Items.NETHERITE_INGOT);
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

	public record MaterialEntry(SmeltyMaterial material, int volumeMl, boolean rawOre) {
	}
}

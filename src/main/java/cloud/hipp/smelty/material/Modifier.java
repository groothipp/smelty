package cloud.hipp.smelty.material;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Modifiers are non-metal items thrown into the smelter that boost alloy stats.
 * Each modifier follows an exponential decay curve: bonus = maxBonus * (1 - e^(-k * concentration))
 * where concentration is modifier items per ingot of alloy.
 */
public enum Modifier {
	COAL(
			new Effect[]{new Effect(MaterialProperty.HARDNESS, 15, 0.5)},
			0x555555  // Gray tint
	),
	BONE_MEAL(
			new Effect[]{new Effect(MaterialProperty.TOUGHNESS, 15, 0.5)},
			0xE8E4D4  // Bone-white tint
	),
	SLIME_BALL(
			new Effect[]{new Effect(MaterialProperty.DUCTILITY, 15, 0.5)},
			0x7EBF6E  // Green tint
	),
	CLAY_BALL(
			new Effect[]{new Effect(MaterialProperty.MALLEABILITY, 15, 0.5)},
			0xA4907C  // Tan/brown tint
	),
	LAPIS_LAZULI(
			new Effect[]{new Effect(MaterialProperty.CORROSION_RESISTANCE, 15, 0.5)},
			0x345EC3  // Blue tint
	),
	SUGAR(
			new Effect[]{new Effect(MaterialProperty.DENSITY, -15, 0.5)},
			0xF0F0F0  // White tint
	),
	BLAZE_POWDER(
			new Effect[]{new Effect(MaterialProperty.DENSITY, 15, 0.5)},
			0xFF8C00  // Orange tint
	),
	GLOWSTONE_DUST(
			new Effect[]{
					new Effect(MaterialProperty.HARDNESS, 10, 0.4),
					new Effect(MaterialProperty.CORROSION_RESISTANCE, 10, 0.4)
			},
			0xFFDD33  // Yellow tint
	),
	REDSTONE(
			new Effect[]{
					new Effect(MaterialProperty.TOUGHNESS, 10, 0.4),
					new Effect(MaterialProperty.DUCTILITY, 10, 0.4)
			},
			0xCC0000  // Red tint
	),
	ENDER_PEARL(
			new Effect[]{
					new Effect(MaterialProperty.HARDNESS, 5, 0.3),
					new Effect(MaterialProperty.TOUGHNESS, 5, 0.3),
					new Effect(MaterialProperty.DUCTILITY, 5, 0.3),
					new Effect(MaterialProperty.MALLEABILITY, 5, 0.3),
					new Effect(MaterialProperty.DENSITY, 5, 0.3),
					new Effect(MaterialProperty.CORROSION_RESISTANCE, 5, 0.3)
			},
			0x0C5E4E  // Purple-green tint
	),
	MEAT(
			new Effect[0],
			0xBB5544  // Reddish-brown tint
	);

	private final Effect[] effects;
	private final int tintColor;

	Modifier(Effect[] effects, int tintColor) {
		this.effects = effects;
		this.tintColor = tintColor;
	}

	public Effect[] getEffects() { return effects; }
	public int getTintColor() { return tintColor; }

	/**
	 * Compute the stat bonus for a given property at the specified concentration (items per ingot).
	 */
	public double getBonus(MaterialProperty property, double concentration) {
		for (Effect effect : effects) {
			if (effect.property == property) {
				double absCon = Math.abs(concentration);
				double bonus = effect.maxBonus * (1 - Math.exp(-effect.k * absCon));
				return concentration < 0 ? -bonus : bonus;
			}
		}
		return 0;
	}

	public record Effect(MaterialProperty property, double maxBonus, double k) {}

	// --- Item mapping ---

	private static final Map<Item, Modifier> ITEM_MAP = new HashMap<>();
	private static final Map<Item, Integer> ITEM_MULTIPLIER = new HashMap<>();

	static {
		ITEM_MAP.put(Items.COAL, COAL);
		ITEM_MAP.put(Items.CHARCOAL, COAL);
		ITEM_MAP.put(Items.COAL_BLOCK, COAL);
		ITEM_MULTIPLIER.put(Items.COAL_BLOCK, 9);
		ITEM_MAP.put(Items.BONE_MEAL, BONE_MEAL);
		ITEM_MAP.put(Items.BONE_BLOCK, BONE_MEAL);
		ITEM_MULTIPLIER.put(Items.BONE_BLOCK, 9);
		ITEM_MAP.put(Items.SLIME_BALL, SLIME_BALL);
		ITEM_MAP.put(Items.SLIME_BLOCK, SLIME_BALL);
		ITEM_MULTIPLIER.put(Items.SLIME_BLOCK, 9);
		ITEM_MAP.put(Items.CLAY_BALL, CLAY_BALL);
		ITEM_MAP.put(Items.CLAY, CLAY_BALL);
		ITEM_MULTIPLIER.put(Items.CLAY, 4);
		ITEM_MAP.put(Items.LAPIS_LAZULI, LAPIS_LAZULI);
		ITEM_MAP.put(Items.LAPIS_BLOCK, LAPIS_LAZULI);
		ITEM_MULTIPLIER.put(Items.LAPIS_BLOCK, 9);
		ITEM_MAP.put(Items.SUGAR, SUGAR);
		ITEM_MAP.put(Items.SUGAR_CANE, SUGAR);
		ITEM_MULTIPLIER.put(Items.SUGAR_CANE, 1);
		ITEM_MAP.put(Items.BLAZE_POWDER, BLAZE_POWDER);
		ITEM_MAP.put(Items.GLOWSTONE_DUST, GLOWSTONE_DUST);
		ITEM_MAP.put(Items.GLOWSTONE, GLOWSTONE_DUST);
		ITEM_MULTIPLIER.put(Items.GLOWSTONE, 4);
		ITEM_MAP.put(Items.REDSTONE, REDSTONE);
		ITEM_MAP.put(Items.REDSTONE_BLOCK, REDSTONE);
		ITEM_MULTIPLIER.put(Items.REDSTONE_BLOCK, 9);
		ITEM_MAP.put(Items.ENDER_PEARL, ENDER_PEARL);
		// Meat (various raw meats)
		ITEM_MAP.put(Items.BEEF, MEAT);
		ITEM_MAP.put(Items.PORKCHOP, MEAT);
		ITEM_MAP.put(Items.CHICKEN, MEAT);
		ITEM_MAP.put(Items.MUTTON, MEAT);
		ITEM_MAP.put(Items.RABBIT, MEAT);
		ITEM_MAP.put(Items.COD, MEAT);
		ITEM_MAP.put(Items.SALMON, MEAT);
	}

	public static @Nullable Modifier fromItem(Item item) {
		return ITEM_MAP.get(item);
	}

	/**
	 * Returns how many modifier units one item counts as.
	 * Block items count as multiple (e.g. coal block = 9).
	 */
	public static int getMultiplier(Item item) {
		return ITEM_MULTIPLIER.getOrDefault(item, 1);
	}
}

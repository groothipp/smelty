package cloud.hipp.smelty.material;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Modifiers are non-metal items thrown into the smelter that boost alloy stats.
 * Each modifier follows an exponential decay curve: bonus = maxBonus * (1 - e^(-k * n))
 * where n is the number of that modifier item thrown into the smelter.
 */
public enum Modifier {
	COAL(
			new Effect[]{new Effect(MaterialProperty.HARDNESS, 20, 0.5)},
			0x555555  // Gray tint
	),
	BONE_MEAL(
			new Effect[]{new Effect(MaterialProperty.TOUGHNESS, 20, 0.5)},
			0xE8E4D4  // Bone-white tint
	),
	SLIME_BALL(
			new Effect[]{new Effect(MaterialProperty.DUCTILITY, 20, 0.5)},
			0x7EBF6E  // Green tint
	),
	CLAY_BALL(
			new Effect[]{new Effect(MaterialProperty.MALLEABILITY, 20, 0.5)},
			0xA4907C  // Tan/brown tint
	),
	LAPIS_LAZULI(
			new Effect[]{new Effect(MaterialProperty.CORROSION_RESISTANCE, 20, 0.5)},
			0x345EC3  // Blue tint
	),
	SUGAR(
			new Effect[]{new Effect(MaterialProperty.DENSITY, -20, 0.5)},
			0xF0F0F0  // White tint
	),
	BLAZE_POWDER(
			new Effect[]{new Effect(MaterialProperty.DENSITY, 20, 0.5)},
			0xFF8C00  // Orange tint
	),
	GLOWSTONE_DUST(
			new Effect[]{
					new Effect(MaterialProperty.HARDNESS, 13, 0.4),
					new Effect(MaterialProperty.CORROSION_RESISTANCE, 13, 0.4)
			},
			0xFFDD33  // Yellow tint
	),
	REDSTONE(
			new Effect[]{
					new Effect(MaterialProperty.TOUGHNESS, 13, 0.4),
					new Effect(MaterialProperty.DUCTILITY, 13, 0.4)
			},
			0xCC0000  // Red tint
	),
	ENDER_PEARL(
			new Effect[]{
					new Effect(MaterialProperty.HARDNESS, 9, 0.3),
					new Effect(MaterialProperty.TOUGHNESS, 9, 0.3),
					new Effect(MaterialProperty.DUCTILITY, 9, 0.3),
					new Effect(MaterialProperty.MALLEABILITY, 9, 0.3),
					// Density handled specially in AlloyComposition.getModifierBonus:
					// pushes density away from 50 (increases if >50, decreases if <50)
					new Effect(MaterialProperty.CORROSION_RESISTANCE, 9, 0.3)
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
	 * Compute the stat bonus for a given property based on modifier item count.
	 * Individual effectiveness: maxBonus * (1 - exp(-k * n))
	 * Stacking the same modifier gives exponentially diminishing returns.
	 */
	public double getBonus(MaterialProperty property, int itemCount) {
		for (Effect effect : effects) {
			if (effect.property == property) {
				double bonus = effect.maxBonus * (1 - Math.exp(-effect.k * itemCount));
				return bonus;
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

package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialProperty;

/**
 * Computes armor stats from metallurgical properties.
 *
 * Armor defense: 1.901 * hardness^0.3054 * slotMultiplier
 *   Calibrated: iron chestplate = 6 (vanilla 6), diamond chestplate = 8 (vanilla 8)
 *
 * Armor toughness: max(0, (toughness - 25) * 0.05)
 *   Iron = 2.25, Netherite = 3.25, Diamond = 0 (brittle)
 *
 * Knockback resistance: max(0, (density - 30) / 500)
 *   Gold = 0.132 (heaviest), Netherite = 0.052
 *
 * Durability: same formula as tools, with slot multiplier
 */
public final class ArmorStatCalculator {

	private ArmorStatCalculator() {}

	/**
	 * Get blended property values from a single composition (armor has no handle).
	 */
	public static double[] getProperties(AlloyComposition composition) {
		double[] props = new double[MaterialProperty.values().length];
		for (MaterialProperty prop : MaterialProperty.values()) {
			props[prop.ordinal()] = composition.getBlendedProperty(prop);
		}
		return props;
	}

	/**
	 * Armor defense points for a given slot.
	 * Formula: round(slotMultiplier * 1.901 * hardness^0.3054)
	 */
	public static int computeDefense(double[] props, SmeltyArmorType armorType) {
		double hardness = Math.max(props[MaterialProperty.HARDNESS.ordinal()], 1);
		double baseDefense = 1.901 * Math.pow(hardness, 0.3054);
		return Math.max(1, (int) Math.round(armorType.getDefenseMultiplier() * baseDefense));
	}

	/**
	 * Armor toughness. Reduces damage scaling from strong attacks.
	 * Formula: max(0, (toughness - 25) * 0.05)
	 */
	public static float computeArmorToughness(double[] props) {
		double toughness = props[MaterialProperty.TOUGHNESS.ordinal()];
		return (float) Math.max(0, (toughness - 25) * 0.05);
	}

	/**
	 * Knockback resistance from density.
	 * Formula: max(0, (density - 30) / 500)
	 */
	public static float computeKnockbackResistance(double[] props) {
		double density = props[MaterialProperty.DENSITY.ordinal()];
		return (float) Math.max(0, (density - 30) / 500.0);
	}

	/**
	 * Armor durability: same base formula as tools, scaled by slot multiplier.
	 */
	public static int computeDurability(double[] props, SmeltyArmorType armorType) {
		int baseDurability = ToolStatCalculator.computeDurability(props);
		return Math.max(1, (int) Math.round(baseDurability * armorType.getDurabilityMultiplier()));
	}
}

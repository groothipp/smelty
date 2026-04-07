package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialProperty;

/**
 * Computes armor stats from alloy compositions using the formulas in notes/alloy_rework.md.
 * All formulas use final alloy properties (blended + modifiers + diversity bonus)
 * and are scaled by the tier multiplier L (except movement speed).
 */
public final class ArmorStatCalculator {

	private ArmorStatCalculator() {}

	// --- Armor Points ---
	// a = L * (c_H * H + c_Du * Du)
	private static final double ARMOR_CH = 0.033;
	private static final double ARMOR_CDU = 0.06;

	// --- Armor Toughness ---
	// t = max(0, L * (c_T * T - c_M * M))
	private static final double TOUGH_CT = 0.037;
	private static final double TOUGH_CM = 0.053;

	// --- Movement Speed Modifier ---
	// M_v = clamp(-M_max * ((rho - 50) / 30)^3, -M_max, M_max)
	private static final double MOVE_MAX = 0.025;
	private static final double MOVE_DIVISOR = 30.0;

	/**
	 * Armor defense points for a given slot.
	 * Formula: L * (c_H * H + c_Du * Du)
	 * Ductility weighted 60% — armor that absorbs impact is more protective.
	 */
	public static double computeDefense(AlloyComposition comp) {
		double L = comp.getTierMultiplier();
		double H = comp.getFinalProperty(MaterialProperty.HARDNESS);
		double Du = comp.getFinalProperty(MaterialProperty.DUCTILITY);
		return L * (ARMOR_CH * H + ARMOR_CDU * Du);
	}

	/**
	 * Armor defense for a specific slot (scaled by slot multiplier).
	 */
	public static int computeSlotDefense(AlloyComposition comp, SmeltyArmorType armorType) {
		return Math.max(1, (int) Math.round(computeDefense(comp) * armorType.getDefenseMultiplier()));
	}

	/**
	 * Armor toughness: max(0, L * (c_T * T - c_M * M))
	 * Only high-toughness, low-malleability materials produce meaningful armor toughness.
	 */
	public static float computeArmorToughness(AlloyComposition comp) {
		double L = comp.getTierMultiplier();
		double T = comp.getFinalProperty(MaterialProperty.TOUGHNESS);
		double M = comp.getFinalProperty(MaterialProperty.MALLEABILITY);
		return (float) Math.max(0, L * (TOUGH_CT * T - TOUGH_CM * M));
	}

	/**
	 * Armor movement speed modifier per piece.
	 * Cubic centered on iron's baseline density (50), clamped to ±2.5%.
	 * Light armor = speed bonus, heavy armor = speed penalty.
	 * No tier multiplier.
	 */
	public static float computeMovementSpeedModifier(AlloyComposition comp) {
		double rho = comp.getFinalProperty(MaterialProperty.DENSITY);
		double normalized = (rho - 50) / MOVE_DIVISOR;
		double modifier = -MOVE_MAX * normalized * normalized * normalized;
		return (float) Math.max(-MOVE_MAX, Math.min(MOVE_MAX, modifier));
	}

	/**
	 * Armor durability: same base formula as tools, scaled by slot multiplier.
	 */
	public static int computeDurability(AlloyComposition comp, SmeltyArmorType armorType) {
		int baseDurability = ToolStatCalculator.computeDurability(comp);
		return Math.max(1, (int) Math.round(baseDurability * armorType.getDurabilityMultiplier()));
	}
}

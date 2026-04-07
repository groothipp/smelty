package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialProperty;
import net.minecraft.block.Block;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

/**
 * Computes tool stats from alloy compositions using the formulas in notes/alloy_rework.md.
 * All formulas use final alloy properties (blended + modifiers + diversity bonus)
 * and are scaled by the tier multiplier L.
 */
public final class ToolStatCalculator {

	private ToolStatCalculator() {}

	// --- Durability ---
	// Dur = L * c_T * T^3 * (1 + c_C * C) / (1 + c_M * M)
	private static final double DUR_CT = 0.00141;
	private static final double DUR_CC = 0.022;
	private static final double DUR_CM = 0.035;

	// --- Sword Damage ---
	// A = L * (c_H * H + c_Du * Du + c_rho * rho)
	private static final double SWORD_CH = 0.011;
	private static final double SWORD_CDU = 0.072;
	private static final double SWORD_CRHO = 0.015;

	// --- Axe Damage ---
	// A = L * (c_rho * rho + c_H * H)
	private static final double AXE_CRHO = 0.029;
	private static final double AXE_CH = 0.059;

	// --- Spear Damage ---
	// A = L * (c_H * H + c_T * T + c_rho * (50 - rho))
	private static final double SPEAR_CH = 0.003;
	private static final double SPEAR_CT = 0.074;
	private static final double SPEAR_CRHO = 0.010;

	// --- Attack Speed ---
	// S_A = c_rho / rho (no tier multiplier)
	private static final double ATKSPD_CRHO = 80.0;

	// --- Mining Speed ---
	// S_M = L * (c_rho / rho + c_H * H)
	private static final double MINE_CRHO = 20.0;
	private static final double MINE_CH = 0.01;

	/**
	 * Compute durability: L * c_T * T^3 * (1 + c_C * C) / (1 + c_M * M)
	 * Toughness cubed is the foundation; corrosion resistance provides a multiplicative boost;
	 * malleability applies a minor penalty.
	 */
	public static int computeDurability(AlloyComposition comp) {
		double L = comp.getTierMultiplier();
		double T = comp.getFinalProperty(MaterialProperty.TOUGHNESS);
		double C = comp.getFinalProperty(MaterialProperty.CORROSION_RESISTANCE);
		double M = comp.getFinalProperty(MaterialProperty.MALLEABILITY);
		double durability = L * DUR_CT * T * T * T * (1 + DUR_CC * C) / (1 + DUR_CM * M);
		return Math.max(1, (int) Math.round(durability));
	}

	/**
	 * Sword attack damage: L * (c_H * H + c_Du * Du + c_rho * rho)
	 * Ductility dominates — flexible, edge-holding materials make the best blades.
	 */
	public static double computeSwordDamage(AlloyComposition comp) {
		double L = comp.getTierMultiplier();
		double H = comp.getFinalProperty(MaterialProperty.HARDNESS);
		double Du = comp.getFinalProperty(MaterialProperty.DUCTILITY);
		double rho = comp.getFinalProperty(MaterialProperty.DENSITY);
		return L * (SWORD_CH * H + SWORD_CDU * Du + SWORD_CRHO * rho);
	}

	/**
	 * Axe attack damage: L * (c_rho * rho + c_H * H)
	 * Hardness is 2x density weight. Axes reward hard, heavy materials.
	 */
	public static double computeAxeDamage(AlloyComposition comp) {
		double L = comp.getTierMultiplier();
		double rho = comp.getFinalProperty(MaterialProperty.DENSITY);
		double H = comp.getFinalProperty(MaterialProperty.HARDNESS);
		return L * (AXE_CRHO * rho + AXE_CH * H);
	}

	/**
	 * Spear attack damage: L * (c_H * H + c_T * T + c_rho * (50 - rho))
	 * Toughness dominates. Lighter-than-iron spears get a damage bonus.
	 */
	public static double computeSpearDamage(AlloyComposition comp) {
		double L = comp.getTierMultiplier();
		double H = comp.getFinalProperty(MaterialProperty.HARDNESS);
		double T = comp.getFinalProperty(MaterialProperty.TOUGHNESS);
		double rho = comp.getFinalProperty(MaterialProperty.DENSITY);
		return L * (SPEAR_CH * H + SPEAR_CT * T + SPEAR_CRHO * (50 - rho));
	}

	/**
	 * Attack damage dispatched by tool type.
	 */
	public static double computeAttackDamage(AlloyComposition comp, SmeltyToolType toolType) {
		return switch (toolType) {
			case SWORD -> computeSwordDamage(comp);
			case AXE -> computeAxeDamage(comp);
			case SPEAR -> computeSpearDamage(comp);
			// Non-weapon tools: minimal damage
			case PICKAXE, SHOVEL, HOE -> 1.0;
		};
	}

	/**
	 * Attack speed: c_rho / rho (no tier multiplier).
	 * Density alone governs swing speed. Returns the attack speed value (not modifier).
	 * Density has a minimum floor of 1 to prevent division by zero.
	 */
	public static double computeAttackSpeed(AlloyComposition comp) {
		double rho = Math.max(1, comp.getFinalProperty(MaterialProperty.DENSITY));
		return ATKSPD_CRHO / rho;
	}

	/**
	 * Mining speed: L * (c_rho / rho + c_H * H)
	 * Light, hard materials mine fastest.
	 */
	public static double computeMiningSpeed(AlloyComposition comp) {
		double L = comp.getTierMultiplier();
		double rho = Math.max(1, comp.getFinalProperty(MaterialProperty.DENSITY));
		double H = comp.getFinalProperty(MaterialProperty.HARDNESS);
		return L * (MINE_CRHO / rho + MINE_CH * H);
	}

	/**
	 * Mining tier from hardness thresholds.
	 * Returns the INCORRECT_FOR tag key for blocks this tool cannot mine.
	 */
	public static TagKey<Block> computeIncorrectBlocksTag(AlloyComposition comp) {
		double hardness = comp.getFinalProperty(MaterialProperty.HARDNESS);
		if (hardness >= 90) return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
		if (hardness >= 60) return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
		if (hardness >= 35) return BlockTags.INCORRECT_FOR_IRON_TOOL;
		if (hardness >= 20) return BlockTags.INCORRECT_FOR_STONE_TOOL;
		return BlockTags.INCORRECT_FOR_WOODEN_TOOL;
	}

	/**
	 * Mining tier index from hardness thresholds.
	 * 0=Wood, 1=Stone, 2=Iron, 3=Diamond, 4=Netherite
	 */
	public static int computeMiningTierIndex(AlloyComposition comp) {
		double hardness = comp.getFinalProperty(MaterialProperty.HARDNESS);
		if (hardness >= 90) return 4;
		if (hardness >= 60) return 3;
		if (hardness >= 35) return 2;
		if (hardness >= 20) return 1;
		return 0;
	}

	public static final String[] MINING_TIER_NAMES = {"Wood", "Stone", "Iron", "Diamond", "Netherite"};
}

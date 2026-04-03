package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialProperty;
import net.minecraft.block.Block;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import org.jspecify.annotations.Nullable;

/**
 * Computes tool stats from blended metallurgical properties using the formulas
 * in notes/design.md. All formulas are calibrated so that pure-material tools
 * produce values close to vanilla.
 */
public final class ToolStatCalculator {

	private ToolStatCalculator() {}

	/**
	 * Blend head and handle compositions according to the tool type's weight distribution.
	 * If handle is null (stick), stats come entirely from the head material.
	 * Returns an array of 8 blended property values (one per MaterialProperty, in enum order).
	 */
	public static double[] blendProperties(AlloyComposition head, @Nullable AlloyComposition handle, SmeltyToolType toolType) {
		double[] blended = new double[MaterialProperty.values().length];
		if (handle == null) {
			for (MaterialProperty prop : MaterialProperty.values()) {
				blended[prop.ordinal()] = head.getBlendedProperty(prop);
			}
		} else {
			double hw = toolType.getHeadWeight();
			double sw = toolType.getHandleWeight();
			for (MaterialProperty prop : MaterialProperty.values()) {
				double headVal = head.getBlendedProperty(prop);
				double handleVal = handle.getBlendedProperty(prop);
				blended[prop.ordinal()] = hw * headVal + sw * handleVal;
			}
		}
		return blended;
	}

	/**
	 * Attack damage formula: 0.798 * hardness^0.432 * ductility^0.115
	 * Returns the total damage (including base 1.0 hand damage).
	 * The result is multiplied by the tool type's damage multiplier.
	 */
	public static double computeAttackDamage(double[] props, SmeltyToolType toolType) {
		double hardness = Math.max(props[MaterialProperty.HARDNESS.ordinal()], 1);
		double ductility = Math.max(props[MaterialProperty.DUCTILITY.ordinal()], 1);
		double baseDamage = 0.798 * Math.pow(hardness, 0.432) * Math.pow(ductility, 0.115);
		return baseDamage * toolType.getDamageMultiplier();
	}

	/**
	 * Attack speed formula: base_speed * (33 / density)^0.3
	 * Returns the modifier to add to the base attack speed of 4.0.
	 * base_speed for each tool type is (4.0 + baseAttackSpeedModifier) at iron density (33).
	 */
	public static double computeAttackSpeedModifier(double[] props, SmeltyToolType toolType) {
		double density = Math.max(props[MaterialProperty.DENSITY.ordinal()], 1);
		double baseSpeed = 4.0 + toolType.getBaseAttackSpeedModifier(); // iron baseline speed
		double actualSpeed = baseSpeed * Math.pow(33.0 / density, 0.3);
		return actualSpeed - 4.0;
	}

	/**
	 * Mining speed formula: base_speed * (hardness / 39)^0.305
	 * Where base_speed is the tool type's base mining speed and 39 is iron's hardness.
	 */
	public static double computeMiningSpeed(double[] props, SmeltyToolType toolType) {
		double hardness = Math.max(props[MaterialProperty.HARDNESS.ordinal()], 1);
		return toolType.getBaseMiningSpeed() * Math.pow(hardness / 39.0, 0.305);
	}

	/**
	 * Mining tier from hardness thresholds.
	 * Returns the INCORRECT_FOR tag key that defines blocks this tool cannot mine.
	 */
	public static TagKey<Block> computeIncorrectBlocksTag(double[] props) {
		double hardness = props[MaterialProperty.HARDNESS.ordinal()];
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
	public static int computeMiningTierIndex(double[] props) {
		double hardness = props[MaterialProperty.HARDNESS.ordinal()];
		if (hardness >= 90) return 4;
		if (hardness >= 60) return 3;
		if (hardness >= 35) return 2;
		if (hardness >= 20) return 1;
		return 0;
	}

	public static final String[] TIER_NAMES = {"Wood", "Stone", "Iron", "Diamond", "Netherite"};

	/**
	 * Durability formula: 0.460 * toughness^1.93 * malleability^(-0.75) * corrosion^0.31
	 */
	public static int computeDurability(double[] props) {
		double toughness = Math.max(props[MaterialProperty.TOUGHNESS.ordinal()], 1);
		double malleability = Math.max(props[MaterialProperty.MALLEABILITY.ordinal()], 1);
		double corrosion = Math.max(props[MaterialProperty.CORROSION_RESISTANCE.ordinal()], 1);
		double durability = 0.460 * Math.pow(toughness, 1.93) * Math.pow(malleability, -0.75) * Math.pow(corrosion, 0.31);
		return Math.max(1, (int) Math.round(durability));
	}
}

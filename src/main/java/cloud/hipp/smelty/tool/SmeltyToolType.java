package cloud.hipp.smelty.tool;

import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import org.jspecify.annotations.Nullable;

public enum SmeltyToolType {
	//                    headW  handleW  baseAtkSpd  baseMineSpd  dmgMult  headCnt  handleCnt  mineableTag
	SWORD   ("Sword",     0.70,  0.30,    -2.4,       1.0,         1.0,     2,       1,         null),
	PICKAXE ("Pickaxe",   0.60,  0.40,    -2.8,       6.0,         0.667,   3,       2,         BlockTags.PICKAXE_MINEABLE),
	AXE     ("Axe",       0.65,  0.35,    -3.1,       6.0,         1.5,     3,       2,         BlockTags.AXE_MINEABLE),
	SHOVEL  ("Shovel",    0.40,  0.60,    -3.0,       6.0,         0.75,    1,       2,         BlockTags.SHOVEL_MINEABLE),
	HOE     ("Hoe",       0.30,  0.70,    -1.0,       6.0,         0.167,   2,       2,         BlockTags.HOE_MINEABLE),
	SPEAR   ("Spear",     0.50,  0.50,    -2.4,       1.0,         0.833,   1,       2,         null);

	private final String displayName;
	private final double headWeight;
	private final double handleWeight;
	private final double baseAttackSpeedModifier;
	private final double baseMiningSpeed;
	private final double damageMultiplier;
	private final int headCount;
	private final int handleCount;
	private final @Nullable TagKey<Block> mineableTag;

	SmeltyToolType(String displayName, double headWeight, double handleWeight,
				   double baseAttackSpeedModifier, double baseMiningSpeed, double damageMultiplier,
				   int headCount, int handleCount, @Nullable TagKey<Block> mineableTag) {
		this.displayName = displayName;
		this.headWeight = headWeight;
		this.handleWeight = handleWeight;
		this.baseAttackSpeedModifier = baseAttackSpeedModifier;
		this.baseMiningSpeed = baseMiningSpeed;
		this.damageMultiplier = damageMultiplier;
		this.headCount = headCount;
		this.handleCount = handleCount;
		this.mineableTag = mineableTag;
	}

	public String getDisplayName() { return displayName; }
	public double getHeadWeight() { return headWeight; }
	public double getHandleWeight() { return handleWeight; }
	public double getBaseAttackSpeedModifier() { return baseAttackSpeedModifier; }
	public double getBaseMiningSpeed() { return baseMiningSpeed; }
	public double getDamageMultiplier() { return damageMultiplier; }
	public int getHeadCount() { return headCount; }
	public int getHandleCount() { return handleCount; }
	public @Nullable TagKey<Block> getMineableTag() { return mineableTag; }
}

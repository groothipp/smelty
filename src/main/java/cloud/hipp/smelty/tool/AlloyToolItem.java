package cloud.hipp.smelty.tool;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.ClientAlloyRegistry;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/**
 * Base tool item that dynamically resolves its name from the stored alloy composition.
 * Used for swords, pickaxes, and spears (which don't need special useOnBlock behavior).
 */
public class AlloyToolItem extends Item {
	private final SmeltyToolType toolType;

	public AlloyToolItem(Settings settings, SmeltyToolType toolType) {
		super(settings);
		this.toolType = toolType;
	}

	@Override
	public Text getName(ItemStack stack) {
		Text name = getToolName(stack, toolType.getDisplayName());
		return name != null ? name : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
							  Consumer<Text> tooltip, TooltipType type) {
		appendToolStats(stack, tooltip, toolType);
		super.appendTooltip(stack, context, displayComponent, tooltip, type);
	}

	/**
	 * Resolve the tool name from stored composition data.
	 * Floats layout: [0-6] head materials, [7] miningSpeed, [8] miningTierIndex,
	 *                [9-15] handle materials, [16] attackDamage, [17] attackSpeed, [18] tier
	 *
	 * Pure material head + sticks -> "Material Tool"
	 * Named alloy -> "AlloyName Tool"
	 * Unnamed alloy -> "Alloy Tool"
	 */
	public static Text getToolName(ItemStack stack, String toolTypeName) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd.floats().size() < 19) return null;

		var floats = cmd.floats();
		SmeltyMaterial[] mats = SmeltyMaterial.values();

		// Extract head composition (floats 0-6)
		AlloyComposition headComp = new AlloyComposition();
		for (int i = 0; i < mats.length; i++) {
			int amount = Math.round(floats.get(i));
			if (amount > 0) headComp.addMaterial(mats[i], amount);
		}

		// Extract handle composition (floats 9-15)
		boolean handleIsSticks = true;
		for (int i = 9; i < 9 + mats.length; i++) {
			if (Math.round(floats.get(i)) > 0) {
				handleIsSticks = false;
				break;
			}
		}

		// Pure material head + sticks = material name
		if (headComp.getMaterials().size() == 1 && handleIsSticks) {
			SmeltyMaterial material = headComp.getMaterials().keySet().iterator().next();
			return Text.literal(material.getDisplayName() + " " + toolTypeName);
		}

		// Check for named alloy using combined composition
		AlloyComposition combined = new AlloyComposition();
		for (int i = 0; i < mats.length; i++) {
			int headAmt = Math.round(floats.get(i));
			int handleAmt = Math.round(floats.get(9 + i));
			if (headAmt + handleAmt > 0) combined.addMaterial(mats[i], headAmt + handleAmt);
		}
		String name = ClientAlloyRegistry.getAlloyName(combined);
		if (name != null) {
			return Text.literal(name + " " + toolTypeName);
		}
		return Text.literal("Alloy " + toolTypeName);
	}

	/**
	 * Append tool-type-specific stats to tooltip from CustomModelData.
	 * Floats layout: [0-6] = head composition (7 materials), [7] = miningSpeed, [8] = miningTierIndex,
	 *                [9-15] = handle composition (7 materials), [16] = attackDamage, [17] = attackSpeed, [18] = tier
	 *
	 * Sword: Tier, Attack Damage, Attack Speed, Durability
	 * Axe: Tier, Attack Damage, Attack Speed, Mine Speed, Durability
	 * Spear: Tier, Attack Damage, Attack Speed, Durability
	 * Pickaxe: Tier, Mine Speed, Durability
	 * Shovel: Tier, Mine Speed, Durability
	 * Hoe: Tier, Durability
	 */
	public static void appendToolStats(ItemStack stack, Consumer<Text> tooltip, SmeltyToolType toolType) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd.floats().size() < 9) return;

		int matCount = SmeltyMaterial.values().length; // 7
		int miningSpeedIdx = matCount;     // 7
		int atkDmgIdx = matCount + 2 + matCount;     // 16
		int atkSpdIdx = matCount + 2 + matCount + 1; // 17
		int toolTierIdx = matCount + 2 + matCount + 2; // 18

		// Tier (all tools)
		if (cmd.floats().size() > toolTierIdx) {
			int tier = Math.round(cmd.floats().get(toolTierIdx));
			if (tier >= 1 && tier <= AlloyComposition.TIER_NAMES.length) {
				tooltip.accept(Text.literal("Tier " + AlloyComposition.TIER_NAMES[tier - 1])
						.formatted(Formatting.GOLD));
			}
		}

		boolean showAttack = toolType == SmeltyToolType.SWORD || toolType == SmeltyToolType.AXE || toolType == SmeltyToolType.SPEAR;
		boolean showMineSpeed = toolType == SmeltyToolType.PICKAXE || toolType == SmeltyToolType.AXE || toolType == SmeltyToolType.SHOVEL;

		// Attack Damage + Attack Speed (sword, axe, spear)
		if (showAttack && cmd.floats().size() > atkSpdIdx) {
			float attackDamage = cmd.floats().get(atkDmgIdx);
			float attackSpeed = cmd.floats().get(atkSpdIdx);
			tooltip.accept(Text.literal("Attack Damage: " + String.format("%.1f", attackDamage))
					.formatted(Formatting.DARK_RED));
			tooltip.accept(Text.literal("Attack Speed: " + String.format("%.1f", attackSpeed))
					.formatted(Formatting.DARK_GREEN));
		}

		// Mine Speed (pickaxe, axe, shovel)
		if (showMineSpeed) {
			float miningSpeed = cmd.floats().get(miningSpeedIdx);
			tooltip.accept(Text.literal("Mine Speed: " + String.format("%.1f", miningSpeed))
					.formatted(Formatting.DARK_GREEN));
		}

		// Durability (all tools)
		Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
		if (maxDamage != null) {
			int currentDamage = stack.getOrDefault(DataComponentTypes.DAMAGE, 0);
			int remaining = maxDamage - currentDamage;
			tooltip.accept(Text.literal("Durability: " + remaining + "/" + maxDamage)
					.formatted(Formatting.BLUE));
		}
	}
}

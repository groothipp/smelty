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
 * Armor item with dynamic naming and custom tooltip based on stored alloy composition.
 */
public class AlloyArmorItem extends Item {
	private final String armorTypeName;

	public AlloyArmorItem(Settings settings, String armorTypeName) {
		super(settings);
		this.armorTypeName = armorTypeName;
	}

	@Override
	public Text getName(ItemStack stack) {
		Text name = getArmorName(stack);
		return name != null ? name : super.getName(stack);
	}

	/**
	 * Resolve armor name from stored composition data.
	 * Armor floats layout: [0-6] materials, [7] defense, [8] tier
	 */
	private Text getArmorName(ItemStack stack) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd.floats().size() < 9) return null;

		var floats = cmd.floats();
		SmeltyMaterial[] mats = SmeltyMaterial.values();

		AlloyComposition comp = new AlloyComposition();
		for (int i = 0; i < mats.length; i++) {
			int amount = Math.round(floats.get(i));
			if (amount > 0) comp.addMaterial(mats[i], amount);
		}

		// Pure single material = material name
		if (comp.getMaterials().size() == 1) {
			SmeltyMaterial material = comp.getMaterials().keySet().iterator().next();
			return Text.literal(material.getDisplayName() + " " + armorTypeName);
		}

		// Named alloy
		String name = ClientAlloyRegistry.getAlloyName(comp);
		if (name != null) {
			return Text.literal(name + " " + armorTypeName);
		}

		return Text.literal("Alloy " + armorTypeName);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
							  Consumer<Text> tooltip, TooltipType type) {
		CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		int matCount = cloud.hipp.smelty.material.SmeltyMaterial.values().length; // 7
		int defenseIdx = matCount;     // 7
		int tierIdx = matCount + 1;    // 8

		// Tier
		if (cmd != null && cmd.floats().size() > tierIdx) {
			int tier = Math.round(cmd.floats().get(tierIdx));
			if (tier >= 1 && tier <= cloud.hipp.smelty.material.AlloyComposition.TIER_NAMES.length) {
				tooltip.accept(Text.literal("Tier " + cloud.hipp.smelty.material.AlloyComposition.TIER_NAMES[tier - 1])
						.formatted(Formatting.GOLD));
			}
		}

		// Armor defense
		if (cmd != null && cmd.floats().size() > defenseIdx) {
			int defense = Math.round(cmd.floats().get(defenseIdx));
			tooltip.accept(Text.literal("Armor: " + defense)
					.formatted(Formatting.DARK_GREEN));
		}

		// Armor Toughness and Movement Speed from attribute modifiers
		net.minecraft.component.type.AttributeModifiersComponent attrComp = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		if (attrComp != null) {
			for (var entry : attrComp.modifiers()) {
				if (entry.attribute().equals(net.minecraft.entity.attribute.EntityAttributes.ARMOR_TOUGHNESS)) {
					float toughness = (float) entry.modifier().value();
					if (toughness > 0) {
						tooltip.accept(Text.literal("Toughness: " + String.format("%.1f", toughness))
								.formatted(Formatting.BLUE));
					}
				}
			}
			for (var entry : attrComp.modifiers()) {
				if (entry.attribute().equals(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED)) {
					float speed = (float) entry.modifier().value();
					int speedPct = (int) (speed * 100);
					if (speedPct == 0) continue;
					String speedText = speedPct > 0
							? "+" + speedPct + "%"
							: speedPct + "%";
					tooltip.accept(Text.literal("Move Speed: " + speedText)
							.formatted(speedPct > 0 ? Formatting.GREEN : Formatting.RED));
				}
			}
		}

		super.appendTooltip(stack, context, displayComponent, tooltip, type);
	}
}

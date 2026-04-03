package cloud.hipp.smelty.tool;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;

public enum SmeltyArmorType {
	HELMET   ("Helmet",     EquipmentSlot.HEAD,  AttributeModifierSlot.HEAD,  0.333, 0.69),
	CHESTPLATE("Chestplate", EquipmentSlot.CHEST, AttributeModifierSlot.CHEST, 1.0,   1.0),
	LEGGINGS ("Leggings",   EquipmentSlot.LEGS,  AttributeModifierSlot.LEGS,  0.833, 0.94),
	BOOTS    ("Boots",      EquipmentSlot.FEET,  AttributeModifierSlot.FEET,  0.333, 0.81);

	private final String displayName;
	private final EquipmentSlot equipmentSlot;
	private final AttributeModifierSlot attributeSlot;
	private final double defenseMultiplier;
	private final double durabilityMultiplier;
	private final Identifier modifierId;

	SmeltyArmorType(String displayName, EquipmentSlot equipmentSlot, AttributeModifierSlot attributeSlot,
					double defenseMultiplier, double durabilityMultiplier) {
		this.displayName = displayName;
		this.equipmentSlot = equipmentSlot;
		this.attributeSlot = attributeSlot;
		this.defenseMultiplier = defenseMultiplier;
		this.durabilityMultiplier = durabilityMultiplier;
		this.modifierId = Identifier.ofVanilla("armor." + name().toLowerCase());
	}

	public String getDisplayName() { return displayName; }
	public EquipmentSlot getEquipmentSlot() { return equipmentSlot; }
	public AttributeModifierSlot getAttributeSlot() { return attributeSlot; }
	public double getDefenseMultiplier() { return defenseMultiplier; }
	public double getDurabilityMultiplier() { return durabilityMultiplier; }
	public Identifier getModifierId() { return modifierId; }
}

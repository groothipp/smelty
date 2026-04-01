package cloud.hipp.smelty.material;

public enum SmeltyMaterial {
	//                                          H   T   MP  Ma  Du  De  CR  TC  reqHeat  color
	COPPER  ("Copper",    23, 50, 26, 80, 91, 39, 60, 53,  10, 0xB87333),
	IRON    ("Iron",      39, 70, 39, 45, 45, 33, 20, 12,  50, 0xA0A0A0),
	GOLD    ("Gold",      13, 25, 25, 95, 82, 96, 95, 47,  10, 0xFFD700),
	DIAMOND ("Diamond",  100, 10, 99,  5,  5,  8, 100, 97, 200, 0x4AEDD9),
	NETHERITE("Netherite", 75, 90, 68, 25, 15, 56, 95, 28, 150, 0x4A3B2C);

	private final String displayName;
	private final int hardness;
	private final int toughness;
	private final int meltingPoint;
	private final int malleability;
	private final int ductility;
	private final int density;
	private final int corrosionResistance;
	private final int thermalConductivity;
	private final int requiredHeat;
	private final int color;

	SmeltyMaterial(String displayName, int hardness, int toughness, int meltingPoint,
				   int malleability, int ductility, int density, int corrosionResistance,
				   int thermalConductivity, int requiredHeat, int color) {
		this.displayName = displayName;
		this.hardness = hardness;
		this.toughness = toughness;
		this.meltingPoint = meltingPoint;
		this.malleability = malleability;
		this.ductility = ductility;
		this.density = density;
		this.corrosionResistance = corrosionResistance;
		this.thermalConductivity = thermalConductivity;
		this.requiredHeat = requiredHeat;
		this.color = color;
	}

	public int getRequiredHeat() {
		return requiredHeat;
	}

	public String getDisplayName() { return displayName; }
	public int getHardness() { return hardness; }
	public int getToughness() { return toughness; }
	public int getMeltingPoint() { return meltingPoint; }
	public int getMalleability() { return malleability; }
	public int getDuctility() { return ductility; }
	public int getDensity() { return density; }
	public int getCorrosionResistance() { return corrosionResistance; }
	public int getThermalConductivity() { return thermalConductivity; }
	public int getColor() { return color; }

	public int getProperty(MaterialProperty property) {
		return switch (property) {
			case HARDNESS -> hardness;
			case TOUGHNESS -> toughness;
			case MELTING_POINT -> meltingPoint;
			case MALLEABILITY -> malleability;
			case DUCTILITY -> ductility;
			case DENSITY -> density;
			case CORROSION_RESISTANCE -> corrosionResistance;
			case THERMAL_CONDUCTIVITY -> thermalConductivity;
		};
	}
}

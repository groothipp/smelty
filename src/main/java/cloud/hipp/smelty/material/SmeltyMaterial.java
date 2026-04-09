package cloud.hipp.smelty.material;

public enum SmeltyMaterial {
	//                                          H   T   MP   Ma  Du  De  CR   reqHeat  color
	COPPER   ("Copper",    35, 40,  10, 55, 65, 45, 20,  10, 0xB87333),
	IRON     ("Iron",      60, 65,  40, 45, 50, 50, 30,  50, 0xD0D0D0),
	GOLD     ("Gold",      10, 15,  10, 35, 20, 65, 70,  10, 0xFFD700),
	DIAMOND  ("Diamond",   90, 75, 160, 20, 55, 25, 90, 160, 0x4AEDD9),
	NETHERITE("Netherite",  85, 90, 200, 35, 50, 75, 85, 200, 0x4A3B2C),
	OBSIDIAN ("Obsidian",  95, 10, 160,  5,  5, 55, 80, 160, 0x1B0B2E),
	EMERALD  ("Emerald",   45, 70,  40, 50, 55, 20, 60,  50, 0x17DD62);

	private final String displayName;
	private final int hardness;
	private final int toughness;
	private final int meltingPoint;
	private final int malleability;
	private final int ductility;
	private final int density;
	private final int corrosionResistance;
	private final int requiredHeat;
	private final int color;

	SmeltyMaterial(String displayName, int hardness, int toughness, int meltingPoint,
				   int malleability, int ductility, int density, int corrosionResistance,
				   int requiredHeat, int color) {
		this.displayName = displayName;
		this.hardness = hardness;
		this.toughness = toughness;
		this.meltingPoint = meltingPoint;
		this.malleability = malleability;
		this.ductility = ductility;
		this.density = density;
		this.corrosionResistance = corrosionResistance;
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
		};
	}
}

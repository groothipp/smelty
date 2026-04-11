package cloud.hipp.smelty.material;

import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

import java.util.EnumMap;
import java.util.Map;

public class AlloyComposition {
	public static final int RATIO_BASE = 100;
	/**
	 * Coarser normalization base for item stacking. Using 20 (5% resolution)
	 * collapses the ±1-2 mL drain rounding that occurs during fluid transfer,
	 * ensuring ingots cast from the same alloy batch always have identical data.
	 */
	public static final int ITEM_RATIO_BASE = 20;

	/**
	 * Volume units per modifier item. Same as ingot volume so that
	 * modifier amounts scale proportionally with material volumes.
	 */
	public static final int MODIFIER_VOLUME = MaterialItems.UNITS_PER_INGOT;

	/** Minimum volume fraction (10%) for a material to count as "distinct" in diversity bonus. */
	private static final double DIVERSITY_THRESHOLD = 0.10;

	private static final double[] DIVERSITY_BONUSES = {0.0, 0.0, 0.15, 0.30, 0.45, 0.55};

	private final EnumMap<SmeltyMaterial, Integer> materials = new EnumMap<>(SmeltyMaterial.class);
	private final EnumMap<Modifier, Integer> modifiers = new EnumMap<>(Modifier.class);

	public void addMaterial(SmeltyMaterial material, int volumeMl) {
		materials.merge(material, volumeMl, Integer::sum);
	}

	public void addModifier(Modifier modifier, int amount) {
		modifiers.merge(modifier, amount, Integer::sum);
	}

	public int getTotalVolumeMl() {
		int total = 0;
		for (int ml : materials.values()) {
			total += ml;
		}
		return total;
	}

	public boolean isEmpty() {
		return materials.isEmpty() || getTotalVolumeMl() == 0;
	}

	public void clear() {
		materials.clear();
		modifiers.clear();
	}

	public void drain(int drainMl) {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0 || drainMl <= 0) return;
		if (drainMl >= totalMl) {
			materials.clear();
			modifiers.clear();
			return;
		}
		double ratio = (double) drainMl / totalMl;
		drainMap(materials, ratio, drainMl);
		// Modifiers stay — they don't drain with materials
	}

	/**
	 * Drains materials proportionally and returns the drained portion.
	 * Modifiers are copied (stamped) onto the drained portion but NOT removed from the source.
	 * When fully drained, modifiers move to the drained portion and the source is cleared.
	 */
	public AlloyComposition drainAndReturn(int drainMl) {
		AlloyComposition drained = new AlloyComposition();
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0 || drainMl <= 0) return drained;
		if (drainMl >= totalMl) {
			drained.materials.putAll(materials);
			drained.modifiers.putAll(modifiers);
			materials.clear();
			modifiers.clear();
			return drained;
		}
		double ratio = (double) drainMl / totalMl;
		drainMapAndReturn(materials, drained.materials, ratio, drainMl);
		// Copy all modifiers to drained portion (source keeps them)
		drained.modifiers.putAll(modifiers);
		return drained;
	}

	private <K extends Enum<K>> void drainMap(EnumMap<K, Integer> map, double ratio, int targetDrain) {
		EnumMap<K, Integer> drainAmounts = new EnumMap<>(map);
		int roundedTotal = 0;
		K largestKey = null;
		int largestValue = 0;
		for (var entry : map.entrySet()) {
			int toDrain = (int) Math.round(ratio * entry.getValue());
			drainAmounts.put(entry.getKey(), toDrain);
			roundedTotal += toDrain;
			if (largestKey == null || entry.getValue() > largestValue) {
				largestValue = entry.getValue();
				largestKey = entry.getKey();
			}
		}
		if (roundedTotal != targetDrain && largestKey != null) {
			int adjusted = drainAmounts.get(largestKey) + (targetDrain - roundedTotal);
			drainAmounts.put(largestKey, Math.max(0, Math.min(adjusted, map.get(largestKey))));
		}
		var iterator = map.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int toDrain = drainAmounts.getOrDefault(entry.getKey(), 0);
			int remaining = entry.getValue() - toDrain;
			if (remaining <= 0) {
				iterator.remove();
			} else {
				entry.setValue(remaining);
			}
		}
	}

	private <K extends Enum<K>> void drainMapAndReturn(EnumMap<K, Integer> source, EnumMap<K, Integer> dest,
													   double ratio, int targetDrain) {
		EnumMap<K, Integer> drainAmounts = new EnumMap<>(source);
		int roundedTotal = 0;
		K largestKey = null;
		int largestValue = 0;
		for (var entry : source.entrySet()) {
			int toDrain = (int) Math.round(ratio * entry.getValue());
			drainAmounts.put(entry.getKey(), toDrain);
			roundedTotal += toDrain;
			if (largestKey == null || entry.getValue() > largestValue) {
				largestValue = entry.getValue();
				largestKey = entry.getKey();
			}
		}
		if (roundedTotal != targetDrain && largestKey != null) {
			int adjusted = drainAmounts.get(largestKey) + (targetDrain - roundedTotal);
			drainAmounts.put(largestKey, Math.max(0, Math.min(adjusted, source.get(largestKey))));
		}
		var iterator = source.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int toDrain = drainAmounts.getOrDefault(entry.getKey(), 0);
			if (toDrain > 0) {
				dest.put(entry.getKey(), toDrain);
			}
			int remaining = entry.getValue() - toDrain;
			if (remaining <= 0) {
				iterator.remove();
			} else {
				entry.setValue(remaining);
			}
		}
	}

	private <K extends Enum<K>> void drainModifiers(double ratio) {
		var iterator = modifiers.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int toDrain = (int) Math.round(ratio * entry.getValue());
			int remaining = entry.getValue() - toDrain;
			if (remaining <= 0) {
				iterator.remove();
			} else {
				entry.setValue(remaining);
			}
		}
	}

	private <K extends Enum<K>> void drainMapProportional(EnumMap<K, Integer> source, EnumMap<K, Integer> dest, double ratio) {
		var iterator = source.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int toDrain = (int) Math.round(ratio * entry.getValue());
			if (toDrain > 0) {
				dest.put(entry.getKey(), toDrain);
			}
			int remaining = entry.getValue() - toDrain;
			if (remaining <= 0) {
				iterator.remove();
			} else {
				entry.setValue(remaining);
			}
		}
	}

	/**
	 * Returns a new AlloyComposition representing what the given amount
	 * of this composition would look like, without modifying this instance.
	 */
	public AlloyComposition createSnapshot(int amountMl) {
		AlloyComposition snapshot = new AlloyComposition();
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0 || amountMl <= 0) return snapshot;
		if (amountMl >= totalMl) {
			snapshot.mergeFrom(this);
			return snapshot;
		}
		double ratio = (double) amountMl / totalMl;
		scaleInto(materials, snapshot.materials, ratio, amountMl);
		// Scale modifiers proportionally
		for (var entry : modifiers.entrySet()) {
			int amount = (int) Math.round(ratio * entry.getValue());
			if (amount > 0) {
				snapshot.modifiers.put(entry.getKey(), amount);
			}
		}
		return snapshot;
	}

	private <K extends Enum<K>> void scaleInto(EnumMap<K, Integer> source, EnumMap<K, Integer> dest,
											   double ratio, int targetTotal) {
		int roundedTotal = 0;
		K largestKey = null;
		int largestValue = 0;
		EnumMap<K, Integer> amounts = new EnumMap<>(source);
		for (var entry : source.entrySet()) {
			int amount = (int) Math.round(ratio * entry.getValue());
			amounts.put(entry.getKey(), amount);
			roundedTotal += amount;
			if (largestKey == null || entry.getValue() > largestValue) {
				largestValue = entry.getValue();
				largestKey = entry.getKey();
			}
		}
		if (roundedTotal != targetTotal && largestKey != null) {
			int adjusted = amounts.get(largestKey) + (targetTotal - roundedTotal);
			amounts.put(largestKey, Math.max(0, adjusted));
		}
		for (var entry : amounts.entrySet()) {
			if (entry.getValue() > 0) {
				dest.put(entry.getKey(), entry.getValue());
			}
		}
	}

	/**
	 * Returns a new AlloyComposition with material values proportionally scaled
	 * to sum to targetTotal, preserving ratios. Modifiers are scaled by the same factor.
	 */
	public AlloyComposition toNormalized(int targetTotal) {
		AlloyComposition result = new AlloyComposition();
		int total = getTotalVolumeMl();
		if (total == 0 || targetTotal <= 0) return result;
		if (total == targetTotal) {
			result.mergeFrom(this);
			return result;
		}
		double scaleFactor = (double) targetTotal / total;
		scaleInto(materials, result.materials, scaleFactor, targetTotal);
		// Scale modifiers by the same factor
		for (var entry : modifiers.entrySet()) {
			int amount = (int) Math.round(scaleFactor * entry.getValue());
			if (amount > 0) {
				result.modifiers.put(entry.getKey(), amount);
			}
		}
		return result;
	}

	public Map<SmeltyMaterial, Integer> getMaterials() {
		return materials;
	}

	public Map<Modifier, Integer> getModifiers() {
		return modifiers;
	}

	// --- Raw blended properties (weighted average, no modifiers or diversity) ---

	public double getBlendedProperty(MaterialProperty property) {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0) return 0;

		double blended = 0;
		for (var entry : materials.entrySet()) {
			double ratio = (double) entry.getValue() / totalMl;
			blended += ratio * entry.getKey().getProperty(property);
		}
		return blended;
	}

	public double getBlendedMeltingPoint() {
		return getBlendedProperty(MaterialProperty.MELTING_POINT);
	}

	// --- Modifier bonuses ---

	/**
	 * Get the item count of a specific modifier (amount / MODIFIER_VOLUME).
	 */
	public int getModifierItemCount(Modifier modifier) {
		return modifiers.getOrDefault(modifier, 0) / MODIFIER_VOLUME;
	}

	/**
	 * Get the total number of modifier items across all modifiers.
	 */
	public int getTotalModifierItemCount() {
		int total = 0;
		for (int units : modifiers.values()) {
			total += units / MODIFIER_VOLUME;
		}
		return total;
	}

	// Overall modifier effectiveness decay rate (based on total modifier item count).
	// Shifted by 1 so the first modifier is always 100% effective.
	private static final double OVERALL_MODIFIER_K = 0.12;

	/**
	 * Compute the overall modifier effectiveness based on total modifier item count.
	 * effectiveness = exp(-K * (totalCount - 1))
	 * First modifier is always fully effective; decay kicks in from the 2nd item onward.
	 */
	public double getOverallModifierEffectiveness() {
		int totalCount = getTotalModifierItemCount();
		if (totalCount <= 1) return 1.0;
		return Math.exp(-OVERALL_MODIFIER_K * (totalCount - 1));
	}

	/**
	 * Compute total modifier bonus for a given property across all modifiers,
	 * scaled by overall modifier effectiveness.
	 * Individual bonus: maxBonus * (1 - exp(-k * n)) where n = item count
	 * Total effectiveness: exp(-K * totalModifierCount)
	 * Overall = sum(individual) * totalEffectiveness
	 */
	public double getModifierBonus(MaterialProperty property) {
		double totalBonus = 0;
		for (Modifier mod : Modifier.values()) {
			int count = getModifierItemCount(mod);
			if (count > 0) {
				totalBonus += mod.getBonus(property, count);
			}
		}
		// Ender Pearl density: push away from 50 (increase if >50, decrease if <50)
		if (property == MaterialProperty.DENSITY) {
			int pearlCount = getModifierItemCount(Modifier.ENDER_PEARL);
			if (pearlCount > 0) {
				double magnitude = 9.0 * (1 - Math.exp(-0.3 * pearlCount));
				double blendedDensity = getBlendedProperty(MaterialProperty.DENSITY);
				totalBonus += (blendedDensity >= 50) ? magnitude : -magnitude;
			}
		}
		return totalBonus * getOverallModifierEffectiveness();
	}

	// --- Diversity bonus ---

	/**
	 * Count materials that make up at least 10% of the alloy by volume.
	 */
	public int getDistinctMaterialCount() {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0) return 0;
		int count = 0;
		for (int vol : materials.values()) {
			if ((double) vol / totalMl >= DIVERSITY_THRESHOLD) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Get the diversity bonus multiplier based on distinct material count.
	 */
	public double getDiversityBonus() {
		int distinct = getDistinctMaterialCount();
		if (distinct >= DIVERSITY_BONUSES.length) {
			return DIVERSITY_BONUSES[DIVERSITY_BONUSES.length - 1];
		}
		return DIVERSITY_BONUSES[distinct];
	}

	// --- Final alloy properties (blended + modifiers + diversity) ---

	/**
	 * Compute the final alloy property value after applying modifier bonuses and diversity bonus.
	 * P_final = (P_blended + modifier_bonus) * (1 + diversity_bonus)
	 *
	 * For density, diversity pushes the value away from 50 (further toward extremes):
	 * density_final = 50 + (base - 50) * (1 + diversity_bonus)
	 */
	public double getFinalProperty(MaterialProperty property) {
		double blended = getBlendedProperty(property);
		double modBonus = getModifierBonus(property);
		double diversity = getDiversityBonus();
		double base = blended + modBonus;
		if (property == MaterialProperty.DENSITY) {
			return 50 + (base - 50) * (1 + diversity);
		}
		return base * (1 + diversity);
	}

	/**
	 * Compute the density score for the stat total.
	 * D_s = (ρ - 50)² / 25
	 */
	public double getDensityScore() {
		double density = getFinalProperty(MaterialProperty.DENSITY);
		double diff = density - 50;
		return (diff * diff) / 25.0;
	}

	/**
	 * Compute the stat total: sum of final properties across all non-melting-point properties,
	 * using density score instead of raw density.
	 */
	public double getStatTotal() {
		double total = 0;
		for (MaterialProperty prop : MaterialProperty.values()) {
			if (prop == MaterialProperty.MELTING_POINT) continue;
			if (prop == MaterialProperty.DENSITY) {
				total += getDensityScore();
			} else {
				total += getFinalProperty(prop);
			}
		}
		return total;
	}

	/**
	 * Get the tool tier (I-V) based on the stat total.
	 */
	public int getTier() {
		double statTotal = getStatTotal();
		if (statTotal >= 455) return 5;
		if (statTotal >= 333) return 4;
		if (statTotal >= 245) return 3;
		if (statTotal >= 180) return 2;
		return 1;
	}

	/**
	 * Get the tier multiplier (L) for tool/armor formulas.
	 */
	public double getTierMultiplier() {
		return switch (getTier()) {
			case 1 -> 0.4;
			case 2 -> 0.8;
			case 3 -> 1.0;
			case 4 -> 1.5;
			case 5 -> 2.25;
			default -> 1.0;
		};
	}

	public static final String[] TIER_NAMES = {"I", "II", "III", "IV", "V"};

	// --- Color ---

	public int getBlendedColor() {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0) return 0x808080;

		double r = 0, g = 0, b = 0;
		for (var entry : materials.entrySet()) {
			double ratio = (double) entry.getValue() / totalMl;
			int color = entry.getKey().getColor();
			r += ratio * ((color >> 16) & 0xFF);
			g += ratio * ((color >> 8) & 0xFF);
			b += ratio * (color & 0xFF);
		}
		// Modifier visual effects are now rendered as separate overlay textures
		// (driven by CustomModelDataComponent flags), not blended into the base color.
		return ((int) Math.min(255, r) << 16) | ((int) Math.min(255, g) << 8) | (int) Math.min(255, b);
	}

	/**
	 * Returns a list of boolean flags indicating which modifiers are present.
	 * Each flag index corresponds to the Modifier enum ordinal.
	 * Used by CustomModelDataComponent to drive conditional overlay rendering.
	 */
	public java.util.List<Boolean> getModifierFlags() {
		java.util.List<Boolean> flags = new java.util.ArrayList<>();
		for (Modifier mod : Modifier.values()) {
			flags.add(modifiers.containsKey(mod) && modifiers.get(mod) > 0);
		}
		return flags;
	}

	public int getRequiredHeat() {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0) return 0;

		double blended = 0;
		for (var entry : materials.entrySet()) {
			double ratio = (double) entry.getValue() / totalMl;
			blended += ratio * entry.getKey().getRequiredHeat();
		}
		return (int) blended;
	}

	/**
	 * Returns a canonical string key for this composition.
	 * Materials are normalized to RATIO_BASE (sum to 100).
	 * Modifiers use absolute item counts (volume / MODIFIER_VOLUME).
	 * Format: "COPPER:30,IRON:70|COAL:5" (sorted by enum order, zeros omitted).
	 */
	public String getNormalizedKey() {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0) return "";

		double matScale = (double) RATIO_BASE / totalMl;
		StringBuilder sb = new StringBuilder();
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			int vol = materials.getOrDefault(mat, 0);
			int normalized = (int) Math.round(matScale * vol);
			if (normalized > 0) {
				if (!sb.isEmpty()) sb.append(',');
				sb.append(mat.name()).append(':').append(normalized);
			}
		}
		// Modifiers as absolute item counts (independent of alloy volume)
		boolean hasModifier = false;
		for (Modifier mod : Modifier.values()) {
			int count = getModifierItemCount(mod);
			if (count > 0) {
				sb.append(hasModifier ? ',' : '|');
				hasModifier = true;
				sb.append(mod.name()).append(':').append(count);
			}
		}
		return sb.toString();
	}

	/**
	 * Convert to a list of float percentages.
	 * Layout: [7 material percentages, 11 modifier amounts (scaled)]
	 */
	public java.util.List<Float> toPercentages() {
		AlloyComposition normalized = toNormalized(RATIO_BASE);
		java.util.List<Float> result = new java.util.ArrayList<>();
		// Materials
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			int vol = normalized.getMaterials().getOrDefault(mat, 0);
			result.add((float) vol);
		}
		// Modifiers (scaled alongside materials)
		for (Modifier mod : Modifier.values()) {
			int amount = normalized.getModifiers().getOrDefault(mod, 0);
			result.add((float) amount);
		}
		return result;
	}

	/**
	 * Create a composition from float percentages.
	 * Layout: [7 material percentages, 11 modifier amounts (raw volumes)]
	 * Handles legacy data with fewer entries gracefully.
	 */
	public static AlloyComposition fromPercentages(java.util.List<Float> percentages) {
		AlloyComposition comp = new AlloyComposition();
		SmeltyMaterial[] mats = SmeltyMaterial.values();
		for (int i = 0; i < mats.length && i < percentages.size(); i++) {
			int amount = Math.round(percentages.get(i));
			if (amount > 0) {
				comp.addMaterial(mats[i], amount);
			}
		}
		// Read modifiers if present (stored as raw volumes)
		Modifier[] mods = Modifier.values();
		int modStart = mats.length;
		for (int i = 0; i < mods.length && (modStart + i) < percentages.size(); i++) {
			int amount = Math.round(percentages.get(modStart + i));
			if (amount > 0) {
				comp.addModifier(mods[i], amount);
			}
		}
		return comp;
	}

	public void mergeFrom(AlloyComposition other) {
		for (var entry : other.materials.entrySet()) {
			addMaterial(entry.getKey(), entry.getValue());
		}
		for (var entry : other.modifiers.entrySet()) {
			addModifier(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Merge materials (additive) but set modifiers (overwrite).
	 * Used when receiving fluid from upstream — modifiers are stamped from the
	 * smelter's state, not accumulated across multiple drain ticks.
	 */
	public void mergeMaterialsAndSetModifiers(AlloyComposition other) {
		for (var entry : other.materials.entrySet()) {
			addMaterial(entry.getKey(), entry.getValue());
		}
		if (!other.modifiers.isEmpty()) {
			modifiers.clear();
			modifiers.putAll(other.modifiers);
		}
	}

	public void writeToView(WriteView.ListView list) {
		for (var entry : materials.entrySet()) {
			WriteView item = list.add();
			item.putString("Material", entry.getKey().name());
			item.putInt("Volume", entry.getValue());
		}
		for (var entry : modifiers.entrySet()) {
			WriteView item = list.add();
			item.putString("Modifier", entry.getKey().name());
			item.putInt("Amount", entry.getValue());
		}
	}

	public void readFromView(ReadView.ListReadView list) {
		materials.clear();
		modifiers.clear();
		for (ReadView item : list) {
			String materialName = item.getString("Material", "");
			if (!materialName.isEmpty()) {
				int volume = item.getInt("Volume", 0);
				if (volume > 0) {
					try {
						SmeltyMaterial material = SmeltyMaterial.valueOf(materialName);
						materials.put(material, volume);
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			String modifierName = item.getString("Modifier", "");
			if (!modifierName.isEmpty()) {
				int amount = item.getInt("Amount", 0);
				if (amount > 0) {
					try {
						Modifier modifier = Modifier.valueOf(modifierName);
						modifiers.put(modifier, amount);
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		}
	}

	/**
	 * Reads the stored normalized key from an item's CustomModelDataComponent strings.
	 * Returns null if no key is stored (legacy items).
	 */
	public static String getStoredKey(net.minecraft.item.ItemStack stack) {
		net.minecraft.component.type.CustomModelDataComponent cmd =
				stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_MODEL_DATA);
		if (cmd != null && !cmd.strings().isEmpty()) {
			return cmd.strings().getFirst();
		}
		return null;
	}
}

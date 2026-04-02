package cloud.hipp.smelty.material;

import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

import java.util.EnumMap;
import java.util.Map;

public class AlloyComposition {
	public static final int RATIO_BASE = 100;

	private final EnumMap<SmeltyMaterial, Integer> materials = new EnumMap<>(SmeltyMaterial.class);

	public void addMaterial(SmeltyMaterial material, int volumeMl) {
		materials.merge(material, volumeMl, Integer::sum);
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
	}

	public void drain(int drainMl) {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0 || drainMl <= 0) return;
		if (drainMl >= totalMl) {
			clear();
			return;
		}
		double ratio = (double) drainMl / totalMl;
		// Compute rounded drain amounts
		EnumMap<SmeltyMaterial, Integer> drainAmounts = new EnumMap<>(SmeltyMaterial.class);
		int roundedTotal = 0;
		SmeltyMaterial largestMat = null;
		int largestDrain = 0;
		for (var entry : materials.entrySet()) {
			int toDrain = (int) Math.round(ratio * entry.getValue());
			drainAmounts.put(entry.getKey(), toDrain);
			roundedTotal += toDrain;
			if (toDrain > largestDrain) {
				largestDrain = toDrain;
				largestMat = entry.getKey();
			}
		}
		// Adjust largest material to compensate for rounding error
		if (roundedTotal != drainMl && largestMat != null) {
			drainAmounts.put(largestMat, drainAmounts.get(largestMat) + (drainMl - roundedTotal));
		}
		// Apply
		var iterator = materials.entrySet().iterator();
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

	/**
	 * Drains proportionally and returns a new AlloyComposition containing the drained portion.
	 */
	public AlloyComposition drainAndReturn(int drainMl) {
		AlloyComposition drained = new AlloyComposition();
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0 || drainMl <= 0) return drained;
		if (drainMl >= totalMl) {
			drained.mergeFrom(this);
			clear();
			return drained;
		}
		double ratio = (double) drainMl / totalMl;
		// Compute rounded drain amounts
		EnumMap<SmeltyMaterial, Integer> drainAmounts = new EnumMap<>(SmeltyMaterial.class);
		int roundedTotal = 0;
		SmeltyMaterial largestMat = null;
		int largestDrain = 0;
		for (var entry : materials.entrySet()) {
			int toDrain = (int) Math.round(ratio * entry.getValue());
			drainAmounts.put(entry.getKey(), toDrain);
			roundedTotal += toDrain;
			if (toDrain > largestDrain) {
				largestDrain = toDrain;
				largestMat = entry.getKey();
			}
		}
		// Adjust largest material to compensate for rounding error
		if (roundedTotal != drainMl && largestMat != null) {
			drainAmounts.put(largestMat, drainAmounts.get(largestMat) + (drainMl - roundedTotal));
		}
		// Apply
		var iterator = materials.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int toDrain = drainAmounts.getOrDefault(entry.getKey(), 0);
			if (toDrain > 0) {
				drained.addMaterial(entry.getKey(), toDrain);
			}
			int remaining = entry.getValue() - toDrain;
			if (remaining <= 0) {
				iterator.remove();
			} else {
				entry.setValue(remaining);
			}
		}
		return drained;
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
		int roundedTotal = 0;
		SmeltyMaterial largestMat = null;
		int largestAmount = 0;
		EnumMap<SmeltyMaterial, Integer> amounts = new EnumMap<>(SmeltyMaterial.class);
		for (var entry : materials.entrySet()) {
			int amount = (int) Math.round(ratio * entry.getValue());
			amounts.put(entry.getKey(), amount);
			roundedTotal += amount;
			if (amount > largestAmount) {
				largestAmount = amount;
				largestMat = entry.getKey();
			}
		}
		if (roundedTotal != amountMl && largestMat != null) {
			amounts.put(largestMat, amounts.get(largestMat) + (amountMl - roundedTotal));
		}
		for (var entry : amounts.entrySet()) {
			if (entry.getValue() > 0) {
				snapshot.addMaterial(entry.getKey(), entry.getValue());
			}
		}
		return snapshot;
	}

	/**
	 * Returns a new AlloyComposition with material values proportionally scaled
	 * to sum to targetTotal, preserving ratios. Used to normalize compositions
	 * to a canonical form (e.g., RATIO_BASE) and to reconstruct absolute volumes
	 * from normalized ratios.
	 */
	public AlloyComposition toNormalized(int targetTotal) {
		AlloyComposition result = new AlloyComposition();
		int total = getTotalVolumeMl();
		if (total == 0 || targetTotal <= 0) return result;
		if (total == targetTotal) {
			result.mergeFrom(this);
			return result;
		}
		int roundedTotal = 0;
		SmeltyMaterial largestMat = null;
		int largestAmount = 0;
		EnumMap<SmeltyMaterial, Integer> amounts = new EnumMap<>(SmeltyMaterial.class);
		for (var entry : materials.entrySet()) {
			int amount = (int) Math.round((double) entry.getValue() / total * targetTotal);
			amounts.put(entry.getKey(), amount);
			roundedTotal += amount;
			if (amount > largestAmount) {
				largestAmount = amount;
				largestMat = entry.getKey();
			}
		}
		if (roundedTotal != targetTotal && largestMat != null) {
			amounts.put(largestMat, amounts.get(largestMat) + (targetTotal - roundedTotal));
		}
		for (var entry : amounts.entrySet()) {
			if (entry.getValue() > 0) {
				result.addMaterial(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	public Map<SmeltyMaterial, Integer> getMaterials() {
		return materials;
	}

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

	public int getBlendedColor() {
		int totalMl = getTotalVolumeMl();
		if (totalMl == 0) return 0x808080; // default gray (no materials)

		double r = 0, g = 0, b = 0;
		for (var entry : materials.entrySet()) {
			double ratio = (double) entry.getValue() / totalMl;
			int color = entry.getKey().getColor();
			r += ratio * ((color >> 16) & 0xFF);
			g += ratio * ((color >> 8) & 0xFF);
			b += ratio * (color & 0xFF);
		}
		return ((int) r << 16) | ((int) g << 8) | (int) b;
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
	 * Returns a canonical string key for this composition, normalized to sum to 100.
	 * Format: "COPPER:30,IRON:70" (sorted by enum order, zeros omitted).
	 */
	public String getNormalizedKey() {
		AlloyComposition normalized = toNormalized(100);
		StringBuilder sb = new StringBuilder();
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			int amount = normalized.getMaterials().getOrDefault(mat, 0);
			if (amount > 0) {
				if (!sb.isEmpty()) sb.append(',');
				sb.append(mat.name()).append(':').append(amount);
			}
		}
		return sb.toString();
	}

	/**
	 * Convert to a list of float percentages (one per SmeltyMaterial in enum order).
	 */
	public java.util.List<Float> toPercentages() {
		int total = getTotalVolumeMl();
		java.util.List<Float> result = new java.util.ArrayList<>();
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			int vol = materials.getOrDefault(mat, 0);
			result.add(total > 0 ? (float) vol * 100f / total : 0f);
		}
		return result;
	}

	/**
	 * Create a composition from float percentages (one per SmeltyMaterial in enum order).
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
		return comp;
	}

	public void mergeFrom(AlloyComposition other) {
		for (var entry : other.materials.entrySet()) {
			addMaterial(entry.getKey(), entry.getValue());
		}
	}

	public void writeToView(WriteView.ListView list) {
		for (var entry : materials.entrySet()) {
			WriteView item = list.add();
			item.putString("Material", entry.getKey().name());
			item.putInt("Volume", entry.getValue());
		}
	}

	public void readFromView(ReadView.ListReadView list) {
		materials.clear();
		for (ReadView item : list) {
			String name = item.getString("Material", "");
			int volume = item.getInt("Volume", 0);
			if (!name.isEmpty() && volume > 0) {
				try {
					SmeltyMaterial material = SmeltyMaterial.valueOf(name);
					materials.put(material, volume);
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
	}
}

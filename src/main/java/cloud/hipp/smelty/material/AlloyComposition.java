package cloud.hipp.smelty.material;

import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

import java.util.EnumMap;
import java.util.Map;

public class AlloyComposition {
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
		var iterator = materials.entrySet().iterator();
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
		if (totalMl == 0) return 0xFF6600; // default orange

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

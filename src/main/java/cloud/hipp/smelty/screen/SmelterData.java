package cloud.hipp.smelty.screen;

import cloud.hipp.smelty.material.Modifier;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.EnumMap;
import java.util.Map;

public record SmelterData(
		int heatLevel,
		int maxVolume,
		int currentVolume,
		Map<SmeltyMaterial, Integer> moltenBreakdown,
		Map<SmeltyMaterial, Integer> solidBreakdown,
		Map<Modifier, Integer> modifierBreakdown
) {
	public int solidVolumeMl() {
		int total = 0;
		for (int ml : solidBreakdown.values()) total += ml;
		return total;
	}

	public static final PacketCodec<RegistryByteBuf, SmelterData> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public SmelterData decode(RegistryByteBuf buf) {
			int heat = PacketCodecs.VAR_INT.decode(buf);
			int maxVol = PacketCodecs.VAR_INT.decode(buf);
			int curVol = PacketCodecs.VAR_INT.decode(buf);

			EnumMap<SmeltyMaterial, Integer> molten = decodeBreakdown(buf);
			EnumMap<SmeltyMaterial, Integer> solid = decodeBreakdown(buf);
			EnumMap<Modifier, Integer> modifiers = decodeModifiers(buf);

			return new SmelterData(heat, maxVol, curVol, molten, solid, modifiers);
		}

		@Override
		public void encode(RegistryByteBuf buf, SmelterData data) {
			PacketCodecs.VAR_INT.encode(buf, data.heatLevel);
			PacketCodecs.VAR_INT.encode(buf, data.maxVolume);
			PacketCodecs.VAR_INT.encode(buf, data.currentVolume);

			encodeBreakdown(buf, data.moltenBreakdown);
			encodeBreakdown(buf, data.solidBreakdown);
			encodeModifiers(buf, data.modifierBreakdown);
		}

		private EnumMap<SmeltyMaterial, Integer> decodeBreakdown(RegistryByteBuf buf) {
			EnumMap<SmeltyMaterial, Integer> map = new EnumMap<>(SmeltyMaterial.class);
			for (SmeltyMaterial mat : SmeltyMaterial.values()) {
				int ml = PacketCodecs.VAR_INT.decode(buf);
				if (ml > 0) map.put(mat, ml);
			}
			return map;
		}

		private void encodeBreakdown(RegistryByteBuf buf, Map<SmeltyMaterial, Integer> breakdown) {
			for (SmeltyMaterial mat : SmeltyMaterial.values()) {
				PacketCodecs.VAR_INT.encode(buf, breakdown.getOrDefault(mat, 0));
			}
		}

		private EnumMap<Modifier, Integer> decodeModifiers(RegistryByteBuf buf) {
			EnumMap<Modifier, Integer> map = new EnumMap<>(Modifier.class);
			for (Modifier mod : Modifier.values()) {
				int amount = PacketCodecs.VAR_INT.decode(buf);
				if (amount > 0) map.put(mod, amount);
			}
			return map;
		}

		private void encodeModifiers(RegistryByteBuf buf, Map<Modifier, Integer> modifiers) {
			for (Modifier mod : Modifier.values()) {
				PacketCodecs.VAR_INT.encode(buf, modifiers.getOrDefault(mod, 0));
			}
		}
	};
}

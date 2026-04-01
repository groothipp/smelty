package cloud.hipp.smelty.screen;

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
		int solidVolumeMl
) {
	public static final PacketCodec<RegistryByteBuf, SmelterData> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public SmelterData decode(RegistryByteBuf buf) {
			int heat = PacketCodecs.VAR_INT.decode(buf);
			int maxVol = PacketCodecs.VAR_INT.decode(buf);
			int curVol = PacketCodecs.VAR_INT.decode(buf);
			int solidVol = PacketCodecs.VAR_INT.decode(buf);

			EnumMap<SmeltyMaterial, Integer> molten = new EnumMap<>(SmeltyMaterial.class);
			SmeltyMaterial[] materials = SmeltyMaterial.values();
			for (SmeltyMaterial mat : materials) {
				int ml = PacketCodecs.VAR_INT.decode(buf);
				if (ml > 0) {
					molten.put(mat, ml);
				}
			}

			return new SmelterData(heat, maxVol, curVol, molten, solidVol);
		}

		@Override
		public void encode(RegistryByteBuf buf, SmelterData data) {
			PacketCodecs.VAR_INT.encode(buf, data.heatLevel);
			PacketCodecs.VAR_INT.encode(buf, data.maxVolume);
			PacketCodecs.VAR_INT.encode(buf, data.currentVolume);
			PacketCodecs.VAR_INT.encode(buf, data.solidVolumeMl);

			SmeltyMaterial[] materials = SmeltyMaterial.values();
			for (SmeltyMaterial mat : materials) {
				int ml = data.moltenBreakdown.getOrDefault(mat, 0);
				PacketCodecs.VAR_INT.encode(buf, ml);
			}
		}
	};
}

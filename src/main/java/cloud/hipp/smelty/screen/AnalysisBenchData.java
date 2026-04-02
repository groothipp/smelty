package cloud.hipp.smelty.screen;

import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.Map;

public record AnalysisBenchData(
		Map<SmeltyMaterial, Integer> composition,
		String materialName,
		boolean isRenameable,
		BlockPos benchPos
) {
	public static final PacketCodec<RegistryByteBuf, AnalysisBenchData> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public AnalysisBenchData decode(RegistryByteBuf buf) {
			EnumMap<SmeltyMaterial, Integer> comp = new EnumMap<>(SmeltyMaterial.class);
			for (SmeltyMaterial mat : SmeltyMaterial.values()) {
				int amount = PacketCodecs.VAR_INT.decode(buf);
				if (amount > 0) comp.put(mat, amount);
			}
			String name = PacketCodecs.STRING.decode(buf);
			boolean renameable = buf.readBoolean();
			BlockPos pos = BlockPos.PACKET_CODEC.decode(buf);
			return new AnalysisBenchData(comp, name, renameable, pos);
		}

		@Override
		public void encode(RegistryByteBuf buf, AnalysisBenchData data) {
			for (SmeltyMaterial mat : SmeltyMaterial.values()) {
				PacketCodecs.VAR_INT.encode(buf, data.composition.getOrDefault(mat, 0));
			}
			PacketCodecs.STRING.encode(buf, data.materialName);
			buf.writeBoolean(data.isRenameable);
			BlockPos.PACKET_CODEC.encode(buf, data.benchPos);
		}
	};
}

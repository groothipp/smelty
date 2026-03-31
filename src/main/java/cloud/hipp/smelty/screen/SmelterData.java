package cloud.hipp.smelty.screen;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record SmelterData(int heatLevel, int maxVolume, int currentVolume) {
	public static final PacketCodec<RegistryByteBuf, SmelterData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SmelterData::heatLevel,
			PacketCodecs.VAR_INT, SmelterData::maxVolume,
			PacketCodecs.VAR_INT, SmelterData::currentVolume,
			SmelterData::new
	);
}

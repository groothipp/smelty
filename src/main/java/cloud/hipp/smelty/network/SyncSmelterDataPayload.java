package cloud.hipp.smelty.network;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.screen.SmelterData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncSmelterDataPayload(int syncId, SmelterData data) implements CustomPayload {
	public static final Id<SyncSmelterDataPayload> ID =
			new Id<>(Identifier.of(Smelty.MOD_ID, "sync_smelter_data"));

	public static final PacketCodec<RegistryByteBuf, SyncSmelterDataPayload> CODEC = new PacketCodec<>() {
		@Override
		public SyncSmelterDataPayload decode(RegistryByteBuf buf) {
			int syncId = buf.readVarInt();
			SmelterData data = SmelterData.PACKET_CODEC.decode(buf);
			return new SyncSmelterDataPayload(syncId, data);
		}

		@Override
		public void encode(RegistryByteBuf buf, SyncSmelterDataPayload payload) {
			buf.writeVarInt(payload.syncId);
			SmelterData.PACKET_CODEC.encode(buf, payload.data);
		}
	};

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

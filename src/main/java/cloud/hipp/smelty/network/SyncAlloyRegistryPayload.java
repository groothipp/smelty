package cloud.hipp.smelty.network;

import cloud.hipp.smelty.Smelty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public record SyncAlloyRegistryPayload(Map<String, String> compositionToName) implements CustomPayload {
	public static final Id<SyncAlloyRegistryPayload> ID =
			new Id<>(Identifier.of(Smelty.MOD_ID, "sync_alloy_registry"));

	public static final PacketCodec<RegistryByteBuf, SyncAlloyRegistryPayload> CODEC = new PacketCodec<>() {
		@Override
		public SyncAlloyRegistryPayload decode(RegistryByteBuf buf) {
			int size = PacketCodecs.VAR_INT.decode(buf);
			Map<String, String> map = new HashMap<>();
			for (int i = 0; i < size; i++) {
				String key = PacketCodecs.STRING.decode(buf);
				String name = PacketCodecs.STRING.decode(buf);
				map.put(key, name);
			}
			return new SyncAlloyRegistryPayload(map);
		}

		@Override
		public void encode(RegistryByteBuf buf, SyncAlloyRegistryPayload payload) {
			Map<String, String> map = payload.compositionToName();
			PacketCodecs.VAR_INT.encode(buf, map.size());
			for (var entry : map.entrySet()) {
				PacketCodecs.STRING.encode(buf, entry.getKey());
				PacketCodecs.STRING.encode(buf, entry.getValue());
			}
		}
	};

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

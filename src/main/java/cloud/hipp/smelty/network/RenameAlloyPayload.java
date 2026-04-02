package cloud.hipp.smelty.network;

import cloud.hipp.smelty.Smelty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record RenameAlloyPayload(BlockPos pos, String name) implements CustomPayload {
	public static final Id<RenameAlloyPayload> ID =
			new Id<>(Identifier.of(Smelty.MOD_ID, "rename_alloy"));

	public static final PacketCodec<RegistryByteBuf, RenameAlloyPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, RenameAlloyPayload::pos,
			PacketCodecs.STRING, RenameAlloyPayload::name,
			RenameAlloyPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

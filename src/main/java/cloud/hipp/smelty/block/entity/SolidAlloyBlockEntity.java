package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.material.AlloyComposition;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

public class SolidAlloyBlockEntity extends BlockEntity {
	private final AlloyComposition composition = new AlloyComposition();
	private int volumeMl;

	public SolidAlloyBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.SOLID_ALLOY, pos, state);
	}

	public AlloyComposition getComposition() {
		return composition;
	}

	public void setComposition(AlloyComposition source) {
		composition.clear();
		AlloyComposition normalized = source.toNormalized(AlloyComposition.RATIO_BASE);
		composition.mergeFrom(normalized);
	}

	public int getVolumeMl() {
		return volumeMl;
	}

	public void setVolumeMl(int volumeMl) {
		this.volumeMl = volumeMl;
	}

	public int getColor() {
		return composition.getBlendedColor();
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createComponentlessNbt(registryLookup);
	}

	@Override
	protected void readData(ReadView view) {
		composition.readFromView(view.getListReadView("Composition"));
		volumeMl = view.getInt("VolumeMl", 0);

		// Ensure volumeMl is set for old-format data where it may be 0
		if (volumeMl <= 0 && !composition.isEmpty()) {
			volumeMl = composition.getTotalVolumeMl();
		}

		// Normalize composition to ratios (handles old-format data with absolute volumes)
		if (!composition.isEmpty() && composition.getTotalVolumeMl() != AlloyComposition.RATIO_BASE) {
			AlloyComposition normalized = composition.toNormalized(AlloyComposition.RATIO_BASE);
			composition.clear();
			composition.mergeFrom(normalized);
		}

		// Trigger client-side chunk re-render so block color updates immediately
		if (world != null && world.isClient()) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeData(WriteView view) {
		composition.writeToView(view.getList("Composition"));
		view.putInt("VolumeMl", volumeMl);
	}
}

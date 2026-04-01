package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.material.AlloyComposition;
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
	private boolean managed;

	public SolidAlloyBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.SOLID_ALLOY, pos, state);
	}

	public AlloyComposition getComposition() {
		return composition;
	}

	public void setComposition(AlloyComposition source) {
		composition.clear();
		composition.mergeFrom(source);
	}

	public int getVolumeMl() {
		return volumeMl;
	}

	public void setVolumeMl(int volumeMl) {
		this.volumeMl = volumeMl;
	}

	public boolean isManaged() {
		return managed;
	}

	public void setManaged(boolean managed) {
		this.managed = managed;
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
		managed = view.getBoolean("Managed", false);
	}

	@Override
	protected void writeData(WriteView view) {
		composition.writeToView(view.getList("Composition"));
		view.putInt("VolumeMl", volumeMl);
		view.putBoolean("Managed", managed);
	}
}

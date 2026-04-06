package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.block.ValveBlock;
import cloud.hipp.smelty.material.AlloyComposition;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ValveBlockEntity extends BlockEntity {
	private static final int FLOW_RATE_PER_TICK = 81; // 9 ingots/sec at 20 tps

	private boolean needsSync;
	private boolean activeDownwardFlow;
	private int flowColor;

	public ValveBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.VALVE, pos, state);
	}

	public boolean isActiveDownwardFlow() {
		return activeDownwardFlow;
	}

	public int getFlowColor() {
		return flowColor;
	}

	/**
	 * Push fluid from the smelter through this valve to downstream targets.
	 * The valve stores nothing — it drains directly from the source and pushes
	 * into channels, basins, or casting tables below/around it.
	 * Returns the total amount of fluid pushed through.
	 */
	public int pushFluidThrough(AlloyComposition source, int amount) {
		BlockState state = getCachedState();
		if (!(state.getBlock() instanceof ValveBlock)) return 0;
		if (!state.get(ValveBlock.OPEN)) return 0;

		if (world == null || world.isClient()) return 0;

		int budget = Math.min(FLOW_RATE_PER_TICK, amount);
		int totalPushed = 0;
		boolean pushedDown = false;
		int colorBeforePush = source.getBlendedColor();

		// Waterfall: check up to 3 blocks below for channels, basins, or tables
		for (int dy = 1; dy <= 3 && budget - totalPushed > 0; dy++) {
			BlockPos below = pos.down(dy);
			BlockEntity belowBe = world.getBlockEntity(below);
			int remaining = budget - totalPushed;
			if (belowBe instanceof ChannelBlockEntity channel && !channel.isFull()) {
				int pushed = channel.addFluid(source, remaining);
				totalPushed += pushed;
				if (pushed > 0) pushedDown = true;
				break;
			} else if (belowBe instanceof CastingBasinBlockEntity basin && !basin.isFull() && !basin.isSolidified()) {
				int pushed = basin.addFluid(source, remaining);
				totalPushed += pushed;
				if (pushed > 0) pushedDown = true;
				break;
			} else if (belowBe instanceof CastingTableBlockEntity table && !table.isFull() && !table.isSolidified()) {
				int pushed = table.addFluid(source, remaining);
				totalPushed += pushed;
				if (pushed > 0) pushedDown = true;
				break;
			} else if (belowBe != null) {
				break;
			}
		}

		// Push to connected blocks on the 3 open sides (not the back/wall side)
		Direction facing = state.get(ValveBlock.FACING);
		Direction[] openSides = getOpenSides(facing);

		// Prioritize casting blocks over channels
		for (Direction dir : openSides) {
			if (budget - totalPushed <= 0) break;
			int remaining = budget - totalPushed;
			BlockPos neighborPos = pos.offset(dir);
			BlockEntity neighborBe = world.getBlockEntity(neighborPos);
			if (neighborBe instanceof CastingBasinBlockEntity basin && !basin.isFull() && !basin.isSolidified()) {
				totalPushed += basin.addFluid(source, remaining);
			} else if (neighborBe instanceof CastingTableBlockEntity table && !table.isFull() && !table.isSolidified()) {
				totalPushed += table.addFluid(source, remaining);
			}
		}

		for (Direction dir : openSides) {
			if (budget - totalPushed <= 0) break;
			int remaining = budget - totalPushed;
			BlockPos neighborPos = pos.offset(dir);
			BlockEntity neighborBe = world.getBlockEntity(neighborPos);
			if (neighborBe instanceof ChannelBlockEntity channel && !channel.isFull()) {
				totalPushed += channel.addFluid(source, remaining);
			}
		}

		// Track active downward flow for waterfall rendering
		if (pushedDown) {
			if (!activeDownwardFlow || flowColor != colorBeforePush) {
				activeDownwardFlow = true;
				flowColor = colorBeforePush;
				needsSync = true;
			}
		} else if (activeDownwardFlow) {
			activeDownwardFlow = false;
			needsSync = true;
		}

		if (needsSync) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			needsSync = false;
		}

		return totalPushed;
	}

	public void serverTick(ServerWorld world) {
		// The valve no longer stores fluid, so the only thing to do on tick
		// is clear the downward flow state if no fluid was pushed this tick.
		// Flow state is managed by pushFluidThrough() called from the controller.
		if (needsSync) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			needsSync = false;
		}
	}

	/**
	 * Called each tick by the controller when the valve is NOT being fed fluid,
	 * so the waterfall rendering can stop.
	 */
	public void clearFlow() {
		if (activeDownwardFlow) {
			activeDownwardFlow = false;
			needsSync = true;
		}
	}

	private Direction[] getOpenSides(Direction facing) {
		Direction back = facing.getOpposite();
		return java.util.Arrays.stream(Direction.values())
				.filter(d -> d.getAxis() != Direction.Axis.Y && d != back)
				.toArray(Direction[]::new);
	}

	// --- Network sync ---

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createComponentlessNbt(registryLookup);
	}

	// --- Serialization ---

	@Override
	protected void readData(ReadView view) {
		activeDownwardFlow = view.getBoolean("ActiveDownwardFlow", false);
		flowColor = view.getInt("FlowColor", 0);
	}

	@Override
	protected void writeData(WriteView view) {
		view.putBoolean("ActiveDownwardFlow", activeDownwardFlow);
		view.putInt("FlowColor", flowColor);
	}
}

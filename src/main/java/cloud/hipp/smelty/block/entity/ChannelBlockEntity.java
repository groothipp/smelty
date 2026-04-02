package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.block.CastingBasinBlock;
import cloud.hipp.smelty.block.CastingTableBlock;
import cloud.hipp.smelty.block.ChannelBlock;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialItems;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class ChannelBlockEntity extends BlockEntity {
	public static final int MAX_CAPACITY = MaterialItems.UNITS_PER_INGOT * 4; // 4 ingots
	private static final int FLOW_RATE_PER_TICK = 81; // 9 ingots/sec at 20 tps
	private static final Direction[] HORIZONTAL_DIRS = {
			Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
	};

	private final AlloyComposition fluidComposition = new AlloyComposition();
	private int fluidLevel;
	private boolean needsSync;
	private long lastProcessedTick = -1;
	private boolean activeDownwardFlow;
	private int flowColor;

	public ChannelBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.CHANNEL, pos, state);
	}

	public AlloyComposition getFluidComposition() {
		return fluidComposition;
	}

	public int getFluidLevelMl() {
		return fluidLevel;
	}

	public int getColor() {
		return fluidComposition.getBlendedColor();
	}

	public float getFillRatio() {
		return (float) fluidLevel / MAX_CAPACITY;
	}

	public boolean isActiveDownwardFlow() {
		return activeDownwardFlow;
	}

	public int getFlowColor() {
		return flowColor;
	}

	public boolean isFull() {
		return fluidLevel >= MAX_CAPACITY;
	}

	public boolean isEmpty() {
		return fluidLevel <= 0;
	}

	/**
	 * Add fluid to this channel. Returns the amount actually accepted.
	 */
	public int addFluid(AlloyComposition source, int amount) {
		int space = MAX_CAPACITY - fluidLevel;
		int accepted = Math.min(amount, space);
		if (accepted <= 0) return 0;

		AlloyComposition portion = source.drainAndReturn(accepted);
		fluidComposition.mergeFrom(portion);
		fluidLevel += accepted;
		needsSync = true;
		markDirty();
		return accepted;
	}

	/**
	 * Drain fluid from this channel. Returns a composition with the drained material.
	 */
	public AlloyComposition drainFluid(int amount) {
		int toDrain = Math.min(amount, fluidLevel);
		if (toDrain <= 0) return new AlloyComposition();

		AlloyComposition drained = fluidComposition.drainAndReturn(toDrain);
		fluidLevel -= toDrain;
		if (fluidLevel <= 0) {
			fluidLevel = 0;
			fluidComposition.clear();
		}
		needsSync = true;
		markDirty();
		return drained;
	}

	public void serverTick(ServerWorld world) {
		updateDownState(world);
		if (lastProcessedTick != world.getTime()) {
			processNetwork(world);
		}
		if (needsSync) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			needsSync = false;
		}
	}

	/**
	 * Re-evaluate the DOWN blockstate property. neighborUpdate only fires for
	 * directly adjacent blocks, so targets placed 2-3 blocks below are missed.
	 */
	private void updateDownState(ServerWorld world) {
		BlockState currentState = getCachedState();
		boolean currentDown = currentState.get(ChannelBlock.DOWN);
		boolean shouldDown = checkDownwardTarget(world);
		if (currentDown != shouldDown) {
			world.setBlockState(pos, currentState.with(ChannelBlock.DOWN, shouldDown), Block.NOTIFY_ALL);
		}
	}

	private boolean checkDownwardTarget(ServerWorld world) {
		for (int dy = 1; dy <= 3; dy++) {
			BlockPos below = pos.down(dy);
			Block block = world.getBlockState(below).getBlock();
			if (block instanceof ChannelBlock || block instanceof CastingBasinBlock || block instanceof CastingTableBlock) {
				return true;
			}
			if (!world.getBlockState(below).isAir()) return false;
		}
		return false;
	}

	/**
	 * Processes the entire connected channel network as one fluid body:
	 * 1. BFS to find all horizontally connected channels
	 * 2. Merge all fluid into one pool
	 * 3. Push from the pool to gravity targets and endpoints (basins/tables)
	 * 4. Distribute remaining fluid evenly across channels
	 *
	 * This eliminates Zeno's paradox where equalization fights drainage —
	 * the full network volume is available for endpoint pushes each tick.
	 */
	private void processNetwork(ServerWorld world) {
		long currentTick = world.getTime();

		// BFS to find all horizontally connected channels
		List<ChannelBlockEntity> network = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Queue<BlockPos> bfsQueue = new LinkedList<>();

		bfsQueue.add(pos);
		visited.add(pos);

		while (!bfsQueue.isEmpty()) {
			BlockPos current = bfsQueue.poll();
			BlockEntity be = world.getBlockEntity(current);
			if (be instanceof ChannelBlockEntity channel) {
				if (channel.lastProcessedTick == currentTick) return; // already processed
				network.add(channel);
				BlockState st = channel.getCachedState();
				for (Direction dir : HORIZONTAL_DIRS) {
					if (!channel.isConnected(st, dir)) continue;
					BlockPos neighbor = current.offset(dir);
					if (visited.add(neighbor)) {
						bfsQueue.add(neighbor);
					}
				}
			}
		}

		// Merge all fluid into one pool
		AlloyComposition pool = new AlloyComposition();
		int totalFluid = 0;
		for (ChannelBlockEntity ch : network) {
			if (ch.fluidLevel > 0) {
				pool.mergeFrom(ch.fluidComposition);
				ch.fluidComposition.clear();
				totalFluid += ch.fluidLevel;
				ch.fluidLevel = 0;
			}
		}

		// Track which channels pushed fluid downward for waterfall rendering
		Set<BlockPos> downwardFlowChannels = new HashSet<>();
		int poolColor = totalFluid > 0 ? pool.getBlendedColor() : 0;

		if (totalFluid > 0) {
			// Push from the pool to gravity targets and endpoints
			for (ChannelBlockEntity ch : network) {
				if (totalFluid <= 0) break;

				// Gravity: waterfall up to 3 blocks below
				for (int dy = 1; dy <= 3 && totalFluid > 0; dy++) {
					BlockPos below = ch.getPos().down(dy);
					if (visited.contains(below)) break; // same network
					BlockEntity belowBe = world.getBlockEntity(below);
					if (belowBe instanceof ChannelBlockEntity belowCh && !belowCh.isFull()) {
						int pushed = pushFromPool(pool, belowCh, totalFluid);
						totalFluid -= pushed;
						if (pushed > 0) downwardFlowChannels.add(ch.getPos());
						break;
					} else if (belowBe instanceof CastingBasinBlockEntity basin && !basin.isFull() && !basin.isSolidified()) {
						int pushed = pushFromPoolToCasting(pool, basin, totalFluid);
						totalFluid -= pushed;
						if (pushed > 0) downwardFlowChannels.add(ch.getPos());
						break;
					} else if (belowBe instanceof CastingTableBlockEntity table && !table.isFull() && !table.isSolidified()) {
						int pushed = pushFromPoolToCasting(pool, table, totalFluid);
						totalFluid -= pushed;
						if (pushed > 0) downwardFlowChannels.add(ch.getPos());
						break;
					} else if (!world.getBlockState(below).isAir()) {
						break;
					}
				}

				// Horizontal endpoints: only push to valves/channels (basins/tables receive fluid only from above)
			}

			// Distribute remaining fluid evenly across channels
			if (totalFluid > 0 && pool.getTotalVolumeMl() > 0) {
				int perChannel = totalFluid / network.size();
				int remainder = totalFluid % network.size();
				for (int i = 0; i < network.size(); i++) {
					ChannelBlockEntity ch = network.get(i);
					int share = perChannel + (i < remainder ? 1 : 0);
					if (share > 0 && pool.getTotalVolumeMl() > 0) {
						AlloyComposition portion = pool.drainAndReturn(share);
						ch.fluidComposition.mergeFrom(portion);
						ch.fluidLevel = share;
					}
				}
			}
		}

		// Mark all channels as processed, update flow state, and sync
		for (ChannelBlockEntity ch : network) {
			ch.activeDownwardFlow = downwardFlowChannels.contains(ch.getPos());
			if (ch.activeDownwardFlow) {
				ch.flowColor = poolColor;
			}
			ch.lastProcessedTick = currentTick;
			ch.needsSync = true;
			ch.markDirty();
		}
	}

	private int pushFromPool(AlloyComposition pool, ChannelBlockEntity target, int available) {
		int push = Math.min(FLOW_RATE_PER_TICK, available);
		if (push <= 0) return 0;
		AlloyComposition offer = pool.createSnapshot(push);
		int accepted = target.addFluid(offer, push);
		if (accepted > 0) {
			pool.drain(accepted);
		}
		return accepted;
	}

	private int pushFromPoolToCasting(AlloyComposition pool, CastingBasinBlockEntity basin, int available) {
		int push = Math.min(FLOW_RATE_PER_TICK, available);
		if (push <= 0) return 0;
		AlloyComposition offer = pool.createSnapshot(push);
		int accepted = basin.addFluid(offer, push);
		if (accepted > 0) {
			pool.drain(accepted);
		}
		return accepted;
	}

	private int pushFromPoolToCasting(AlloyComposition pool, CastingTableBlockEntity table, int available) {
		int push = Math.min(FLOW_RATE_PER_TICK, available);
		if (push <= 0) return 0;
		AlloyComposition offer = pool.createSnapshot(push);
		int accepted = table.addFluid(offer, push);
		if (accepted > 0) {
			pool.drain(accepted);
		}
		return accepted;
	}

	private boolean isConnected(BlockState state, Direction dir) {
		return switch (dir) {
			case NORTH -> state.get(ChannelBlock.NORTH);
			case SOUTH -> state.get(ChannelBlock.SOUTH);
			case EAST -> state.get(ChannelBlock.EAST);
			case WEST -> state.get(ChannelBlock.WEST);
			default -> false;
		};
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
		fluidComposition.readFromView(view.getListReadView("Composition"));
		fluidLevel = view.getInt("FluidLevel", 0);
		activeDownwardFlow = view.getBoolean("ActiveDownwardFlow", false);
		flowColor = view.getInt("FlowColor", 0);
	}

	@Override
	protected void writeData(WriteView view) {
		fluidComposition.writeToView(view.getList("Composition"));
		view.putInt("FluidLevel", fluidLevel);
		view.putBoolean("ActiveDownwardFlow", activeDownwardFlow);
		view.putInt("FlowColor", flowColor);
	}
}

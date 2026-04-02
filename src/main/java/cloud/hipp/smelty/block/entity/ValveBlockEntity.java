package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.block.ValveBlock;
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

public class ValveBlockEntity extends BlockEntity {
	public static final int MAX_CAPACITY = MaterialItems.UNITS_PER_INGOT * 4; // 4 ingots
	private static final int FLOW_RATE_PER_TICK = 81; // 9 ingots/sec at 20 tps

	private final AlloyComposition fluidComposition = new AlloyComposition();
	private int fluidLevel;
	private boolean needsSync;
	private boolean activeDownwardFlow;
	private int flowColor;

	public ValveBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.VALVE, pos, state);
	}

	public int getFluidLevelMl() {
		return fluidLevel;
	}

	public int getColor() {
		return fluidComposition.getBlendedColor();
	}

	public AlloyComposition getFluidComposition() {
		return fluidComposition;
	}

	public float getFillRatio() {
		return (float) fluidLevel / MAX_CAPACITY;
	}

	public boolean isFull() {
		return fluidLevel >= MAX_CAPACITY;
	}

	public boolean isEmpty() {
		return fluidLevel <= 0;
	}

	public boolean isActiveDownwardFlow() {
		return activeDownwardFlow;
	}

	public int getFlowColor() {
		return flowColor;
	}

	/**
	 * Add fluid from smelter controller. Returns amount accepted.
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

	public void serverTick(ServerWorld world) {
		if (fluidLevel <= 0 && !activeDownwardFlow) {
			if (needsSync) {
				world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
				needsSync = false;
			}
			return;
		}

		BlockState state = getCachedState();
		if (!(state.getBlock() instanceof ValveBlock)) return;

		// Valve closed — stop all flow
		if (!state.get(ValveBlock.OPEN)) {
			if (activeDownwardFlow) {
				activeDownwardFlow = false;
				needsSync = true;
			}
			if (needsSync) {
				world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
				needsSync = false;
			}
			return;
		}

		Direction facing = state.get(ValveBlock.FACING);
		int remaining = Math.min(FLOW_RATE_PER_TICK, fluidLevel);
		boolean pushedDown = false;
		int colorBeforePush = fluidComposition.getBlendedColor(); // capture color before fluid is drained

		// Waterfall: check up to 3 blocks below for channels, basins, or tables
		for (int dy = 1; dy <= 3 && remaining > 0; dy++) {
			BlockPos below = pos.down(dy);
			BlockEntity belowBe = world.getBlockEntity(below);
			if (belowBe instanceof ChannelBlockEntity channel && !channel.isFull()) {
				int pushed = pushFluidTo(channel, remaining);
				remaining -= pushed;
				if (pushed > 0) pushedDown = true;
				break;
			} else if (belowBe instanceof CastingBasinBlockEntity basin && !basin.isFull() && !basin.isSolidified()) {
				int pushed = pushFluidToCasting(basin, remaining);
				remaining -= pushed;
				if (pushed > 0) pushedDown = true;
				break;
			} else if (belowBe instanceof CastingTableBlockEntity table && !table.isFull() && !table.isSolidified()) {
				int pushed = pushFluidToCasting(table, remaining);
				remaining -= pushed;
				if (pushed > 0) pushedDown = true;
				break;
			} else if (belowBe != null) {
				break;
			}
		}

		// Push to connected blocks on the 3 open sides (not the back/wall side)
		// Prioritize casting blocks over channels
		Direction[] openSides = getOpenSides(facing);

		for (Direction dir : openSides) {
			if (remaining <= 0) break;
			BlockPos neighborPos = pos.offset(dir);
			BlockEntity neighborBe = world.getBlockEntity(neighborPos);
			if (neighborBe instanceof CastingBasinBlockEntity basin && !basin.isFull() && !basin.isSolidified()) {
				remaining -= pushFluidToCasting(basin, remaining);
			} else if (neighborBe instanceof CastingTableBlockEntity table && !table.isFull() && !table.isSolidified()) {
				remaining -= pushFluidToCasting(table, remaining);
			}
		}

		for (Direction dir : openSides) {
			if (remaining <= 0) break;
			BlockPos neighborPos = pos.offset(dir);
			BlockEntity neighborBe = world.getBlockEntity(neighborPos);
			if (neighborBe instanceof ChannelBlockEntity channel && !channel.isFull()) {
				remaining -= pushFluidTo(channel, remaining);
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
	}

	private Direction[] getOpenSides(Direction facing) {
		Direction back = facing.getOpposite();
		return java.util.Arrays.stream(Direction.values())
				.filter(d -> d.getAxis() != Direction.Axis.Y && d != back)
				.toArray(Direction[]::new);
	}

	private int pushFluidTo(ChannelBlockEntity target, int budget) {
		int pushAmount = Math.min(budget, fluidLevel);
		if (pushAmount <= 0) return 0;

		AlloyComposition offer = fluidComposition.createSnapshot(pushAmount);
		int accepted = target.addFluid(offer, pushAmount);
		if (accepted > 0) {
			fluidComposition.drain(accepted);
			fluidLevel -= accepted;
			if (fluidLevel <= 0) {
				fluidLevel = 0;
				fluidComposition.clear();
			}
			needsSync = true;
			markDirty();
		}
		return accepted;
	}

	private int pushFluidToCasting(CastingBasinBlockEntity basin, int budget) {
		int pushAmount = Math.min(budget, fluidLevel);
		if (pushAmount <= 0) return 0;

		AlloyComposition offer = fluidComposition.createSnapshot(pushAmount);
		int accepted = basin.addFluid(offer, pushAmount);
		if (accepted > 0) {
			fluidComposition.drain(accepted);
			fluidLevel -= accepted;
			if (fluidLevel <= 0) {
				fluidLevel = 0;
				fluidComposition.clear();
			}
			needsSync = true;
			markDirty();
		}
		return accepted;
	}

	private int pushFluidToCasting(CastingTableBlockEntity table, int budget) {
		int pushAmount = Math.min(budget, fluidLevel);
		if (pushAmount <= 0) return 0;

		AlloyComposition offer = fluidComposition.createSnapshot(pushAmount);
		int accepted = table.addFluid(offer, pushAmount);
		if (accepted > 0) {
			fluidComposition.drain(accepted);
			fluidLevel -= accepted;
			if (fluidLevel <= 0) {
				fluidLevel = 0;
				fluidComposition.clear();
			}
			needsSync = true;
			markDirty();
		}
		return accepted;
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

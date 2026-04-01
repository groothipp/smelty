package cloud.hipp.smelty.fluid;

import cloud.hipp.smelty.block.SmeltyBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.util.math.BlockPos;

public abstract class MoltenAlloyFluid extends FlowableFluid {

	@Override
	public Fluid getFlowing() {
		return SmeltyFluids.MOLTEN_ALLOY_FLOWING;
	}

	@Override
	public Fluid getStill() {
		return SmeltyFluids.MOLTEN_ALLOY_STILL;
	}

	@Override
	public Item getBucketItem() {
		return Items.AIR;
	}

	@Override
	protected boolean isInfinite(ServerWorld world) {
		return false;
	}

	@Override
	protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state) {
		BlockEntity be = world.getBlockEntity(pos);
		Block.dropStacks(state, world, pos, be);
	}

	@Override
	public int getMaxFlowDistance(WorldView world) {
		return 4;
	}

	@Override
	public int getLevelDecreasePerBlock(WorldView world) {
		return 1;
	}

	@Override
	public int getTickRate(WorldView world) {
		return 20;
	}

	@Override
	protected float getBlastResistance() {
		return 100.0f;
	}

	@Override
	public boolean matchesType(Fluid fluid) {
		return fluid == SmeltyFluids.MOLTEN_ALLOY_STILL || fluid == SmeltyFluids.MOLTEN_ALLOY_FLOWING;
	}

	@Override
	public boolean canBeReplacedWith(FluidState state, net.minecraft.world.BlockView world, BlockPos pos, Fluid fluid, net.minecraft.util.math.Direction direction) {
		return false;
	}

	@Override
	public BlockState toBlockState(FluidState state) {
		return SmeltyBlocks.MOLTEN_ALLOY_BLOCK.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL, getBlockStateLevel(state));
	}

	public static class Still extends MoltenAlloyFluid {
		@Override
		public int getLevel(FluidState state) {
			return 8;
		}

		@Override
		public boolean isStill(FluidState state) {
			return true;
		}
	}

	public static class Flowing extends MoltenAlloyFluid {
		@Override
		protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) {
			super.appendProperties(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getLevel(FluidState state) {
			return state.get(LEVEL);
		}

		@Override
		public boolean isStill(FluidState state) {
			return false;
		}
	}
}

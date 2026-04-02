package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.ChannelBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jspecify.annotations.Nullable;

public class ChannelBlock extends BlockWithEntity {
	public static final MapCodec<ChannelBlock> CODEC = createCodec(ChannelBlock::new);

	public static final BooleanProperty NORTH = BooleanProperty.of("north");
	public static final BooleanProperty SOUTH = BooleanProperty.of("south");
	public static final BooleanProperty EAST = BooleanProperty.of("east");
	public static final BooleanProperty WEST = BooleanProperty.of("west");
	public static final BooleanProperty DOWN = BooleanProperty.of("down");

	// Trough shape: bottom slab (4px) + walls on unconnected sides
	private static final VoxelShape BOTTOM = Block.createCuboidShape(0, 0, 0, 16, 4, 16);
	private static final VoxelShape WALL_NORTH = Block.createCuboidShape(0, 4, 0, 16, 8, 2);
	private static final VoxelShape WALL_SOUTH = Block.createCuboidShape(0, 4, 14, 16, 8, 16);
	private static final VoxelShape WALL_EAST = Block.createCuboidShape(14, 4, 0, 16, 8, 16);
	private static final VoxelShape WALL_WEST = Block.createCuboidShape(0, 4, 0, 2, 8, 16);

	public ChannelBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState()
				.with(NORTH, false)
				.with(SOUTH, false)
				.with(EAST, false)
				.with(WEST, false)
				.with(DOWN, false));
	}

	@Override
	protected MapCodec<? extends ChannelBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(NORTH, SOUTH, EAST, WEST, DOWN);
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ChannelBlockEntity(pos, state);
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) return null;
		return validateTicker(type, cloud.hipp.smelty.block.entity.SmeltyBlockEntities.CHANNEL,
				(w, pos, s, be) -> be.serverTick((net.minecraft.server.world.ServerWorld) w));
	}

	private boolean canConnect(BlockState state) {
		Block block = state.getBlock();
		return block instanceof ChannelBlock
				|| block instanceof ValveBlock;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
			@Nullable WireOrientation wireOrientation, boolean notify) {
		// Recompute connections when a neighbor changes
		BlockState updated = state
				.with(NORTH, canConnect(world.getBlockState(pos.north())))
				.with(SOUTH, canConnect(world.getBlockState(pos.south())))
				.with(EAST, canConnect(world.getBlockState(pos.east())))
				.with(WEST, canConnect(world.getBlockState(pos.west())))
				.with(DOWN, hasDownwardTarget(world, pos));
		if (!updated.equals(state)) {
			world.setBlockState(pos, updated, Block.NOTIFY_ALL);
		}
	}

	/**
	 * Check up to 3 blocks below for a valid waterfall target (channel, basin, or table),
	 * with only air in between.
	 */
	private boolean hasDownwardTarget(World world, BlockPos pos) {
		for (int dy = 1; dy <= 3; dy++) {
			BlockPos below = pos.down(dy);
			Block block = world.getBlockState(below).getBlock();
			if (block instanceof ChannelBlock || block instanceof CastingBasinBlock || block instanceof CastingTableBlock) {
				return true;
			}
			if (!world.getBlockState(below).isAir()) {
				return false;
			}
		}
		return false;
	}

	private static @Nullable BooleanProperty getPropertyForDirection(Direction dir) {
		return switch (dir) {
			case NORTH -> NORTH;
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			case WEST -> WEST;
			default -> null;
		};
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		VoxelShape shape = BOTTOM;
		if (!state.get(NORTH)) shape = VoxelShapes.union(shape, WALL_NORTH);
		if (!state.get(SOUTH)) shape = VoxelShapes.union(shape, WALL_SOUTH);
		if (!state.get(EAST)) shape = VoxelShapes.union(shape, WALL_EAST);
		if (!state.get(WEST)) shape = VoxelShapes.union(shape, WALL_WEST);
		return shape;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return getOutlineShape(state, world, pos, context);
	}

	/**
	 * Called during placement to compute initial connection state.
	 */
	@Override
	public @Nullable BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx) {
		World world = ctx.getWorld();
		BlockPos pos = ctx.getBlockPos();
		return getDefaultState()
				.with(NORTH, canConnect(world.getBlockState(pos.north())))
				.with(SOUTH, canConnect(world.getBlockState(pos.south())))
				.with(EAST, canConnect(world.getBlockState(pos.east())))
				.with(WEST, canConnect(world.getBlockState(pos.west())))
				.with(DOWN, hasDownwardTarget(world, pos));
	}
}

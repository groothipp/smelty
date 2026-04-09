package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
import cloud.hipp.smelty.block.entity.ValveBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jspecify.annotations.Nullable;

public class ValveBlock extends BlockWithEntity {
	public static final MapCodec<ValveBlock> CODEC = createCodec(ValveBlock::new);
	public static final BooleanProperty OPEN = Properties.OPEN;
	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

	// Spigot VoxelShapes per direction (cached)
	private static final VoxelShape SHAPE_NORTH = buildSpigotShape(Direction.NORTH);
	private static final VoxelShape SHAPE_SOUTH = buildSpigotShape(Direction.SOUTH);
	private static final VoxelShape SHAPE_EAST = buildSpigotShape(Direction.EAST);
	private static final VoxelShape SHAPE_WEST = buildSpigotShape(Direction.WEST);

	public ValveBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(OPEN, false).with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends ValveBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(OPEN, FACING);
	}

	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction side = ctx.getSide();
		// If placed on a horizontal face, use that direction; otherwise use player facing
		if (side.getAxis() == Direction.Axis.Y) {
			side = ctx.getHorizontalPlayerFacing();
		}
		return getDefaultState()
				.with(FACING, side)
				.with(OPEN, false);
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ValveBlockEntity(pos, state);
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) return null;
		return validateTicker(type, SmeltyBlockEntities.VALVE,
				(w, pos, s, be) -> be.serverTick((ServerWorld) w));
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient()) {
			boolean open = !state.get(OPEN);
			world.setBlockState(pos, state.with(OPEN, open), Block.NOTIFY_ALL);
			if (!open) clearValveFlow(world, pos);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
			@Nullable WireOrientation wireOrientation, boolean notify) {
		if (!world.isClient()) {
			boolean powered = world.isReceivingRedstonePower(pos);
			if (powered != state.get(OPEN)) {
				world.setBlockState(pos, state.with(OPEN, powered), Block.NOTIFY_ALL);
				if (!powered) clearValveFlow(world, pos);
			}
		}
	}

	private void clearValveFlow(World world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof ValveBlockEntity valve) {
			valve.clearFlow();
		}
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(FACING)) {
			case SOUTH -> SHAPE_SOUTH;
			case EAST -> SHAPE_EAST;
			case WEST -> SHAPE_WEST;
			default -> SHAPE_NORTH;
		};
	}

	private static VoxelShape buildSpigotShape(Direction facing) {
		// Model elements (defined for NORTH, rotated for other directions):
		// Flange: [5,4,14]-[11,10,16], Pipe: [6,5,11]-[10,9,14], Nozzle: [5,4,8]-[11,9,11]
		return switch (facing) {
			case NORTH -> VoxelShapes.union(
					VoxelShapes.cuboid(5/16f, 4/16f, 14/16f, 11/16f, 10/16f, 1),
					VoxelShapes.cuboid(6/16f, 5/16f, 11/16f, 10/16f, 9/16f, 14/16f),
					VoxelShapes.cuboid(5/16f, 4/16f, 8/16f, 11/16f, 9/16f, 11/16f)
			);
			case SOUTH -> VoxelShapes.union(
					VoxelShapes.cuboid(5/16f, 4/16f, 0, 11/16f, 10/16f, 2/16f),
					VoxelShapes.cuboid(6/16f, 5/16f, 2/16f, 10/16f, 9/16f, 5/16f),
					VoxelShapes.cuboid(5/16f, 4/16f, 5/16f, 11/16f, 9/16f, 8/16f)
			);
			case EAST -> VoxelShapes.union(
					VoxelShapes.cuboid(0, 4/16f, 5/16f, 2/16f, 10/16f, 11/16f),
					VoxelShapes.cuboid(2/16f, 5/16f, 6/16f, 5/16f, 9/16f, 10/16f),
					VoxelShapes.cuboid(5/16f, 4/16f, 5/16f, 8/16f, 9/16f, 11/16f)
			);
			case WEST -> VoxelShapes.union(
					VoxelShapes.cuboid(14/16f, 4/16f, 5/16f, 1, 10/16f, 11/16f),
					VoxelShapes.cuboid(11/16f, 5/16f, 6/16f, 14/16f, 9/16f, 10/16f),
					VoxelShapes.cuboid(8/16f, 4/16f, 5/16f, 11/16f, 9/16f, 11/16f)
			);
			default -> VoxelShapes.fullCube();
		};
	}
}

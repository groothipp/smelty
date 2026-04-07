package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.AnalysisBenchBlockEntity;
import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

public class AnalysisBenchBlock extends BlockWithEntity {
	public static final MapCodec<AnalysisBenchBlock> CODEC = createCodec(AnalysisBenchBlock::new);
	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty HAS_PLATE = BooleanProperty.of("has_plate");

	// Basin inner area: 2/16 to 14/16 on both axes
	private static final double BASIN_MIN = 2.0 / 16.0;
	private static final double BASIN_MAX = 14.0 / 16.0;

	private static final VoxelShape TOP = Block.createCuboidShape(0, 10, 0, 16, 12, 16);
	private static final VoxelShape RIM_N = Block.createCuboidShape(0, 12, 0, 16, 13, 2);
	private static final VoxelShape RIM_S = Block.createCuboidShape(0, 12, 14, 16, 13, 16);
	private static final VoxelShape RIM_W = Block.createCuboidShape(0, 12, 2, 2, 13, 14);
	private static final VoxelShape RIM_E = Block.createCuboidShape(14, 12, 2, 16, 13, 14);
	private static final VoxelShape LEG_NW = Block.createCuboidShape(0, 0, 0, 2, 10, 2);
	private static final VoxelShape LEG_NE = Block.createCuboidShape(14, 0, 0, 16, 10, 2);
	private static final VoxelShape LEG_SW = Block.createCuboidShape(0, 0, 14, 2, 10, 16);
	private static final VoxelShape LEG_SE = Block.createCuboidShape(14, 0, 14, 16, 10, 16);
	private static final VoxelShape SHAPE = VoxelShapes.union(
			TOP, RIM_N, RIM_S, RIM_W, RIM_E, LEG_NW, LEG_NE, LEG_SW, LEG_SE);

	public AnalysisBenchBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(HAS_PLATE, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, HAS_PLATE);
	}

	@Override
	protected MapCodec<? extends AnalysisBenchBlock> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new AnalysisBenchBlockEntity(pos, state);
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return null;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient()) return ActionResult.SUCCESS;

		if (world.getBlockEntity(pos) instanceof AnalysisBenchBlockEntity bench) {
			boolean clickedBasin = isBasinHit(hit, pos);
			return bench.onInteract(player, clickedBasin) ? ActionResult.SUCCESS : ActionResult.PASS;
		}
		return ActionResult.PASS;
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		// Drops are handled by AnalysisBenchBlockEntity.onBlockReplaced(),
		// which is called before the block entity is removed from the chunk.
		super.onStateReplaced(state, world, pos, moved);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	private static boolean isBasinHit(BlockHitResult hit, BlockPos pos) {
		Vec3d hitPos = hit.getPos();
		double localX = hitPos.x - pos.getX();
		double localZ = hitPos.z - pos.getZ();
		return localX > BASIN_MIN && localX < BASIN_MAX
				&& localZ > BASIN_MIN && localZ < BASIN_MAX;
	}
}

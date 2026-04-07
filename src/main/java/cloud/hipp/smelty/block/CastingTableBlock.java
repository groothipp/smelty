package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.CastingTableBlockEntity;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

public class CastingTableBlock extends BlockWithEntity {
	public static final MapCodec<CastingTableBlock> CODEC = createCodec(CastingTableBlock::new);

	// Flat table: 4 legs + top slab
	private static final VoxelShape TOP = Block.createCuboidShape(0, 10, 0, 16, 12, 16);
	private static final VoxelShape LEG_NW = Block.createCuboidShape(0, 0, 0, 2, 10, 2);
	private static final VoxelShape LEG_NE = Block.createCuboidShape(14, 0, 0, 16, 10, 2);
	private static final VoxelShape LEG_SW = Block.createCuboidShape(0, 0, 14, 2, 10, 16);
	private static final VoxelShape LEG_SE = Block.createCuboidShape(14, 0, 14, 16, 10, 16);
	private static final VoxelShape SHAPE = VoxelShapes.union(TOP, LEG_NW, LEG_NE, LEG_SW, LEG_SE);

	public CastingTableBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends CastingTableBlock> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CastingTableBlockEntity(pos, state);
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) return null;
		return validateTicker(type, SmeltyBlockEntities.CASTING_TABLE,
				(w, pos, s, be) -> be.serverTick((ServerWorld) w));
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof CastingTableBlockEntity table) {
			return table.onInteract(player) ? ActionResult.SUCCESS : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		// Drops are handled by CastingTableBlockEntity.onBlockReplaced(),
		// which is called before the block entity is removed from the chunk.
		super.onStateReplaced(state, world, pos, moved);
	}
}

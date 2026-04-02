package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.CastingBasinBlockEntity;
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

public class CastingBasinBlock extends BlockWithEntity {
	public static final MapCodec<CastingBasinBlock> CODEC = createCodec(CastingBasinBlock::new);

	// Cauldron-like shape: bottom slab + 4 walls
	private static final VoxelShape BOTTOM = Block.createCuboidShape(2, 0, 2, 14, 4, 14);
	private static final VoxelShape WALL_N = Block.createCuboidShape(2, 0, 0, 14, 10, 2);
	private static final VoxelShape WALL_S = Block.createCuboidShape(2, 0, 14, 14, 10, 16);
	private static final VoxelShape WALL_E = Block.createCuboidShape(14, 0, 0, 16, 10, 16);
	private static final VoxelShape WALL_W = Block.createCuboidShape(0, 0, 0, 2, 10, 16);
	private static final VoxelShape SHAPE = VoxelShapes.union(BOTTOM, WALL_N, WALL_S, WALL_E, WALL_W);

	public CastingBasinBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends CastingBasinBlock> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CastingBasinBlockEntity(pos, state);
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) return null;
		return validateTicker(type, SmeltyBlockEntities.CASTING_BASIN,
				(w, pos, s, be) -> be.serverTick((ServerWorld) w));
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof CastingBasinBlockEntity basin) {
			return basin.tryExtract(player) ? ActionResult.SUCCESS : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}

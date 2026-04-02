package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.SmelterControllerBlockEntity;
import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
import cloud.hipp.smelty.structure.MultiblockValidator;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

public class SmelterControllerBlock extends BlockWithEntity {
	public static final MapCodec<SmelterControllerBlock> CODEC = createCodec(SmelterControllerBlock::new);
	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty LIT = Properties.LIT;

	public SmelterControllerBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected MapCodec<? extends SmelterControllerBlock> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SmelterControllerBlockEntity(pos, state);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (!world.isClient()) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof SmelterControllerBlockEntity controller) {
				MultiblockValidator.Result result = MultiblockValidator.validate(world, pos);
				controller.setMultiblockData(result);
			}
		}
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) return null;
		return validateTicker(type, SmeltyBlockEntities.SMELTER_CONTROLLER, SmelterControllerBlockEntity::serverTick);
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		super.onStateReplaced(state, world, pos, moved);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient()) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof SmelterControllerBlockEntity controller) {
				if (!controller.isValid()) {
					player.sendMessage(net.minecraft.text.Text.literal("Smelter structure is not formed"), true);
					return ActionResult.CONSUME;
				}
				player.openHandledScreen(controller);
			}
		}
		return ActionResult.SUCCESS;
	}
}

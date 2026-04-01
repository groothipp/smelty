package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.SolidAlloyBlockEntity;
import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.jspecify.annotations.Nullable;

public class SolidAlloyBlock extends BlockWithEntity {
	public static final MapCodec<SolidAlloyBlock> CODEC = createCodec(SolidAlloyBlock::new);

	// When true, removing this block won't drop items (used by controller during managed operations)
	private static boolean suppressDrops = false;

	public SolidAlloyBlock(Settings settings) {
		super(settings);
	}

	public static void setSuppressDrops(boolean suppress) {
		suppressDrops = suppress;
	}

	@Override
	protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof SolidAlloyBlockEntity solidBe && solidBe.isManaged()) {
			return 0.0F; // Unbreakable when managed by a smelter controller
		}
		return super.calcBlockBreakingDelta(state, player, world, pos);
	}

	@Override
	protected MapCodec<? extends SolidAlloyBlock> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SolidAlloyBlockEntity(pos, state);
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (!suppressDrops) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof SolidAlloyBlockEntity solidBe && !solidBe.getComposition().isEmpty()) {
				ItemStack stack = new ItemStack(this);
				stack.set(DataComponentTypes.BLOCK_ENTITY_DATA,
						TypedEntityData.create(SmeltyBlockEntities.SOLID_ALLOY,
								solidBe.createNbt(world.getRegistryManager())));
				ItemEntity itemEntity = new ItemEntity(world,
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
				world.spawnEntity(itemEntity);
			}
		}
		super.onStateReplaced(state, world, pos, moved);
	}
}

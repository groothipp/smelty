package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.SolidAlloyBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

public class SolidAlloyBlock extends BlockWithEntity {
	public static final MapCodec<SolidAlloyBlock> CODEC = createCodec(SolidAlloyBlock::new);

	public SolidAlloyBlock(Settings settings) {
		super(settings);
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
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		// Apply BE data on both client and server so client has color data immediately
		var beData = itemStack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
		if (beData != null && world.getBlockEntity(pos) instanceof SolidAlloyBlockEntity solidBe) {
			beData.applyToBlockEntity(solidBe, world.getRegistryManager());
		}
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
			serverWorld.getChunkManager().markForUpdate(pos);
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		// Drops are handled by SolidAlloyBlockEntity.onBlockReplaced(),
		// which is called before the block entity is removed from the chunk.
		super.onStateReplaced(state, world, pos, moved);
	}
}

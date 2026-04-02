package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.SmelterControllerBlockEntity;
import cloud.hipp.smelty.structure.MultiblockValidator;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class SmelterBlock extends Block {
	public static final MapCodec<SmelterBlock> CODEC = createCodec(SmelterBlock::new);

	public SmelterBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		notifyNearbyControllers(world, pos);
		super.onStateReplaced(state, world, pos, moved);
	}

	private void notifyNearbyControllers(ServerWorld world, BlockPos brokenPos) {
		int range = MultiblockValidator.MAX_SIZE;
		for (int dx = -range; dx <= range; dx++) {
			for (int dy = -range; dy <= range; dy++) {
				for (int dz = -range; dz <= range; dz++) {
					BlockPos check = brokenPos.add(dx, dy, dz);
					if (world.getBlockEntity(check) instanceof SmelterControllerBlockEntity controller) {
						controller.forceRevalidate();
					}
				}
			}
		}
	}
}

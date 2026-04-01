package cloud.hipp.smelty.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.jspecify.annotations.Nullable;

public class MoltenAlloyBlock extends FluidBlock {
	public MoltenAlloyBlock(FlowableFluid fluid, Settings settings) {
		super(fluid, settings);
	}

	@Override
	public ItemStack tryDrainFluid(@Nullable LivingEntity entity, WorldAccess world, BlockPos pos, BlockState state) {
		return ItemStack.EMPTY;
	}
}

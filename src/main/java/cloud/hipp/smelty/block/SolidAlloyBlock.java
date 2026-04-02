package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.SolidAlloyBlockEntity;
import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
import cloud.hipp.smelty.material.AlloyRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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
		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof SolidAlloyBlockEntity solidBe && !solidBe.getComposition().isEmpty()) {
			ItemStack stack = new ItemStack(this);
			// Use temp BE at ORIGIN so block position doesn't affect item NBT (enables stacking)
			SolidAlloyBlockEntity tempBe = new SolidAlloyBlockEntity(BlockPos.ORIGIN, SmeltyBlocks.SOLID_ALLOY.getDefaultState());
			tempBe.setComposition(solidBe.getComposition());
			tempBe.setVolumeMl(solidBe.getVolumeMl());
			stack.set(DataComponentTypes.BLOCK_ENTITY_DATA,
					TypedEntityData.create(SmeltyBlockEntities.SOLID_ALLOY,
							tempBe.createNbt(world.getRegistryManager())));
			stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
					new CustomModelDataComponent(
							java.util.List.of(), java.util.List.of(), java.util.List.of(),
							java.util.List.of(tempBe.getColor())));
			AlloyRegistry registry = AlloyRegistry.get(world);
			String alloyName = registry.getAlloyName(solidBe.getComposition());
			if (alloyName != null) {
				stack.set(DataComponentTypes.CUSTOM_NAME,
						Text.literal(alloyName + " Block").styled(s -> s.withItalic(false)));
			}
			ItemEntity itemEntity = new ItemEntity(world,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
			world.spawnEntity(itemEntity);
		}
		super.onStateReplaced(state, world, pos, moved);
	}
}

package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.AlloyRegistry;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class SolidAlloyBlockEntity extends BlockEntity {
	private final AlloyComposition composition = new AlloyComposition();
	private int volumeMl;

	public SolidAlloyBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.SOLID_ALLOY, pos, state);
	}

	public AlloyComposition getComposition() {
		return composition;
	}

	public void setComposition(AlloyComposition source) {
		composition.clear();
		// Normalize materials to RATIO_BASE; keep modifiers as absolute amounts
		AlloyComposition matNorm = source.toNormalized(AlloyComposition.RATIO_BASE);
		for (var entry : matNorm.getMaterials().entrySet()) {
			composition.addMaterial(entry.getKey(), entry.getValue());
		}
		for (var entry : source.getModifiers().entrySet()) {
			composition.addModifier(entry.getKey(), entry.getValue());
		}
	}

	public int getVolumeMl() {
		return volumeMl;
	}

	public void setVolumeMl(int volumeMl) {
		this.volumeMl = volumeMl;
	}

	public int getColor() {
		return composition.getBlendedColor();
	}

	@Override
	public void onBlockReplaced(BlockPos pos, BlockState state) {
		super.onBlockReplaced(pos, state);
		if (composition.isEmpty() || !(world instanceof ServerWorld serverWorld)) return;

		ItemStack stack = new ItemStack(state.getBlock());
		SolidAlloyBlockEntity tempBe = new SolidAlloyBlockEntity(BlockPos.ORIGIN, SmeltyBlocks.SOLID_ALLOY.getDefaultState());
		tempBe.setComposition(composition);
		tempBe.setVolumeMl(volumeMl);
		stack.set(DataComponentTypes.BLOCK_ENTITY_DATA,
				TypedEntityData.create(SmeltyBlockEntities.SOLID_ALLOY,
						tempBe.createNbt(serverWorld.getRegistryManager())));
		AlloyComposition matNormalized = composition.toNormalized(AlloyComposition.ITEM_RATIO_BASE);
		String normalizedKey = composition.getNormalizedKey();
		List<Float> percentages = new ArrayList<>();
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			percentages.add((float) matNormalized.getMaterials().getOrDefault(mat, 0));
		}
		for (cloud.hipp.smelty.material.Modifier mod : cloud.hipp.smelty.material.Modifier.values()) {
			percentages.add((float) composition.getModifiers().getOrDefault(mod, 0));
		}
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
				new CustomModelDataComponent(
						percentages, List.of(), List.of(normalizedKey),
						List.of(composition.getBlendedColor())));
		AlloyRegistry registry = AlloyRegistry.get(serverWorld);
		String alloyName = registry.getAlloyName(normalizedKey);
		if (alloyName != null) {
			stack.set(DataComponentTypes.CUSTOM_NAME,
					Text.literal(alloyName + " Block").styled(s -> s.withItalic(false)));
		}
		ItemEntity itemEntity = new ItemEntity(serverWorld,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
		serverWorld.spawnEntity(itemEntity);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createComponentlessNbt(registryLookup);
	}

	@Override
	protected void readData(ReadView view) {
		composition.readFromView(view.getListReadView("Composition"));
		volumeMl = view.getInt("VolumeMl", 0);

		// Ensure volumeMl is set for old-format data where it may be 0
		if (volumeMl <= 0 && !composition.isEmpty()) {
			volumeMl = composition.getTotalVolumeMl();
		}

		// Normalize material ratios (handles old-format data with absolute volumes)
		// Modifiers are kept as absolute amounts, not scaled
		if (!composition.isEmpty() && composition.getTotalVolumeMl() != AlloyComposition.RATIO_BASE) {
			AlloyComposition matNorm = composition.toNormalized(AlloyComposition.RATIO_BASE);
			java.util.EnumMap<cloud.hipp.smelty.material.Modifier, Integer> savedMods =
					new java.util.EnumMap<>(composition.getModifiers());
			composition.clear();
			for (var entry : matNorm.getMaterials().entrySet()) {
				composition.addMaterial(entry.getKey(), entry.getValue());
			}
			composition.getModifiers().putAll(savedMods);
		}

		// Trigger client-side chunk re-render so block color updates immediately
		if (world != null && world.isClient()) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeData(WriteView view) {
		composition.writeToView(view.getList("Composition"));
		view.putInt("VolumeMl", volumeMl);
	}
}

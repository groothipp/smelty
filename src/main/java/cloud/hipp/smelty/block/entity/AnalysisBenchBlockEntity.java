package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.block.AnalysisBenchBlock;
import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.AlloyRegistry;
import cloud.hipp.smelty.material.SmeltyMaterial;
import cloud.hipp.smelty.screen.AnalysisBenchData;
import cloud.hipp.smelty.screen.AnalysisBenchScreenHandler;
import cloud.hipp.smelty.screen.SmeltyScreenHandlers;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class AnalysisBenchBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<AnalysisBenchData> {
	private ItemStack plateItem = ItemStack.EMPTY;

	public AnalysisBenchBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.ANALYSIS_BENCH, pos, state);
	}

	public ItemStack getPlateItem() {
		return plateItem;
	}

	public boolean hasPlate() {
		return !plateItem.isEmpty();
	}

	public boolean onInteract(PlayerEntity player, boolean clickedBasin) {
		ItemStack held = player.getMainHandStack();

		if (clickedBasin) {
			// Basin click: plate add/remove/swap
			if (hasPlate()) {
				if (!held.isEmpty() && isPlate(held)) {
					// Swap plates
					ItemStack oldPlate = plateItem.copy();
					plateItem = held.copyWithCount(1);
					held.decrement(1);
					giveOrDrop(player, oldPlate);
					syncToClient();
					markDirty();
					return true;
				}
				// Pop plate out
				giveOrDrop(player, plateItem.copy());
				plateItem = ItemStack.EMPTY;
				syncToClient();
				markDirty();
				return true;
			}
			// Place plate on empty bench
			if (!held.isEmpty() && isPlate(held)) {
				plateItem = held.copyWithCount(1);
				held.decrement(1);
				syncToClient();
				markDirty();
				return true;
			}
			return false;
		}

		// Rim/arm click: open UI if plate is present
		if (hasPlate() && player instanceof ServerPlayerEntity) {
			player.openHandledScreen(this);
			return true;
		}
		return false;
	}

	public static boolean isPlate(ItemStack stack) {
		Item item = stack.getItem();
		if (item == SmeltyItems.ALLOY_PLATE) return true;
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			if (SmeltyItems.getPlateForMaterial(mat) == item) return true;
		}
		return false;
	}

	public static AlloyComposition getCompositionFromPlate(ItemStack plate) {
		AlloyComposition comp = new AlloyComposition();
		Item item = plate.getItem();

		// Pure material plates
		for (SmeltyMaterial mat : SmeltyMaterial.values()) {
			if (SmeltyItems.getPlateForMaterial(mat) == item) {
				comp.addMaterial(mat, 100);
				return comp;
			}
		}

		// Alloy plate — read from CustomModelDataComponent floats
		if (item == SmeltyItems.ALLOY_PLATE) {
			CustomModelDataComponent cmd = plate.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			if (cmd != null && !cmd.floats().isEmpty()) {
				return AlloyComposition.fromPercentages(cmd.floats());
			}
		}

		return comp;
	}

	private String getMaterialName(AlloyComposition composition) {
		Map<SmeltyMaterial, Integer> mats = composition.getMaterials();
		if (mats.size() == 1) {
			return mats.keySet().iterator().next().getDisplayName();
		}
		// Check AlloyRegistry for a custom name
		if (world instanceof ServerWorld serverWorld) {
			AlloyRegistry registry = AlloyRegistry.get(serverWorld);
			String customName = registry.getAlloyName(composition);
			if (customName != null) return customName;
		}
		return "Alloy";
	}

	private AnalysisBenchData buildScreenData() {
		AlloyComposition comp = getCompositionFromPlate(plateItem);
		AlloyComposition normalized = comp.toNormalized(100);
		EnumMap<SmeltyMaterial, Integer> map = new EnumMap<>(SmeltyMaterial.class);
		map.putAll(normalized.getMaterials());

		String name = getMaterialName(comp);
		boolean renameable = comp.getMaterials().size() > 1 && name.equals("Alloy");

		return new AnalysisBenchData(map, name, renameable, pos);
	}

	@Override
	public AnalysisBenchData getScreenOpeningData(ServerPlayerEntity player) {
		return buildScreenData();
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.smelty.analysis_bench");
	}

	@Override
	public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new AnalysisBenchScreenHandler(syncId, playerInventory, buildScreenData());
	}

	@Override
	public void onBlockReplaced(BlockPos pos, BlockState state) {
		super.onBlockReplaced(pos, state);
		if (world instanceof ServerWorld serverWorld) {
			dropContents(serverWorld);
		}
	}

	private void dropContents(ServerWorld world) {
		if (!plateItem.isEmpty()) {
			ItemEntity itemEntity = new ItemEntity(world,
					pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, plateItem.copy());
			world.spawnEntity(itemEntity);
			plateItem = ItemStack.EMPTY;
		}
	}

	private void giveOrDrop(PlayerEntity player, ItemStack stack) {
		if (!player.giveItemStack(stack)) {
			ItemEntity itemEntity = new ItemEntity(world,
					pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
			world.spawnEntity(itemEntity);
		}
	}

	private void syncToClient() {
		if (world instanceof ServerWorld sw) {
			BlockState current = getCachedState();
			boolean plate = hasPlate();
			if (current.get(AnalysisBenchBlock.HAS_PLATE) != plate) {
				BlockState updated = current.with(AnalysisBenchBlock.HAS_PLATE, plate);
				sw.setBlockState(pos, updated, Block.NOTIFY_ALL);
			}
			sw.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	// --- Network sync ---

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createComponentlessNbt(registryLookup);
	}

	// --- Serialization ---

	@Override
	protected void readData(ReadView view) {
		plateItem = view.read("PlateItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void writeData(WriteView view) {
		view.put("PlateItem", ItemStack.OPTIONAL_CODEC, plateItem);
	}
}

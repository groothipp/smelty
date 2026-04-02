package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

public class CastingBasinBlockEntity extends BlockEntity implements Inventory {
	public static final int CAPACITY = MaterialItems.UNITS_PER_INGOT * 9; // 9 ingots = 810
	private static final int COOLDOWN_TICKS = 80; // 4 seconds

	private final AlloyComposition fluidComposition = new AlloyComposition();
	private int fluidLevelMl;
	private int cooldownTicks = -1; // -1 = not cooling
	private boolean solidified;
	private boolean needsSync;
	private ItemStack cachedOutput = ItemStack.EMPTY;

	public CastingBasinBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.CASTING_BASIN, pos, state);
	}

	public AlloyComposition getFluidComposition() {
		return fluidComposition;
	}

	public int getFluidLevelMl() {
		return fluidLevelMl;
	}

	public int getColor() {
		return fluidComposition.getBlendedColor();
	}

	public float getFillRatio() {
		return (float) fluidLevelMl / CAPACITY;
	}

	public boolean isSolidified() {
		return solidified;
	}

	public boolean isFull() {
		return fluidLevelMl >= CAPACITY;
	}

	@Override
	public boolean isEmpty() {
		return cachedOutput.isEmpty();
	}

	public boolean hasNoFluid() {
		return fluidLevelMl <= 0;
	}

	/**
	 * Add fluid from a channel. Returns amount accepted.
	 */
	public int addFluid(AlloyComposition source, int amountMl) {
		if (solidified || cooldownTicks >= 0) return 0; // Don't accept fluid while cooling/solidified

		int space = CAPACITY - fluidLevelMl;
		int accepted = Math.min(amountMl, space);
		if (accepted <= 0) return 0;

		AlloyComposition portion = source.drainAndReturn(accepted);
		fluidComposition.mergeFrom(portion);
		fluidLevelMl += accepted;
		needsSync = true;
		markDirty();
		return accepted;
	}

	public void serverTick(ServerWorld world) {
		if (fluidLevelMl <= 0 && !solidified) return;

		// Start cooldown when full
		if (isFull() && cooldownTicks < 0) {
			cooldownTicks = COOLDOWN_TICKS;
		}

		if (cooldownTicks > 0) {
			cooldownTicks--;

			// Cooling particles
			if (cooldownTicks % 10 == 0) {
				double x = pos.getX() + 0.5;
				double y = pos.getY() + 0.7;
				double z = pos.getZ() + 0.5;
				world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.15, 0.1, 0.15, 0.02);
			}

			if (cooldownTicks <= 0) {
				solidified = true;
				populateOutput(world);
				markDirty();
				needsSync = true;
			}
		}

		// Re-populate output after chunk load if needed
		if (solidified && cachedOutput.isEmpty() && !fluidComposition.isEmpty()) {
			populateOutput(world);
		}

		// Sync to client (during filling, cooling, and solidification)
		if (needsSync || cooldownTicks >= 0 || solidified) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			needsSync = false;
		}
	}

	/**
	 * Player right-clicks to extract solidified alloy block.
	 */
	public boolean tryExtract(PlayerEntity player) {
		if (!solidified) return false;

		if (!cachedOutput.isEmpty()) {
			giveOrDrop(player, cachedOutput.copy());
		}

		resetBasin();
		return true;
	}

	private void giveOrDrop(PlayerEntity player, ItemStack stack) {
		if (!player.giveItemStack(stack)) {
			ItemEntity itemEntity = new ItemEntity(world,
					pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
			world.spawnEntity(itemEntity);
		}
	}

	private void populateOutput(ServerWorld serverWorld) {
		java.util.List<ItemStack> vanillaItems = MaterialItems.getPureVanillaItems(fluidComposition);
		if (vanillaItems != null && !vanillaItems.isEmpty()) {
			cachedOutput = vanillaItems.getFirst();
		} else {
			ItemStack stack = new ItemStack(SmeltyBlocks.SOLID_ALLOY);
			SolidAlloyBlockEntity tempBe = new SolidAlloyBlockEntity(BlockPos.ORIGIN, SmeltyBlocks.SOLID_ALLOY.getDefaultState());
			tempBe.setComposition(fluidComposition);
			tempBe.setVolumeMl(fluidLevelMl);
			stack.set(DataComponentTypes.BLOCK_ENTITY_DATA,
					TypedEntityData.create(SmeltyBlockEntities.SOLID_ALLOY,
							tempBe.createNbt(serverWorld.getRegistryManager())));
			stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
					new CustomModelDataComponent(
							java.util.List.of(), java.util.List.of(), java.util.List.of(),
							java.util.List.of(tempBe.getColor())));
			cachedOutput = stack;
		}
	}

	private void resetBasin() {
		fluidComposition.clear();
		fluidLevelMl = 0;
		cooldownTicks = -1;
		solidified = false;
		cachedOutput = ItemStack.EMPTY;
		markDirty();
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	// --- Inventory (hopper support) ---

	@Override
	public int size() { return 1; }

	@Override
	public ItemStack getStack(int slot) {
		return slot == 0 ? cachedOutput : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		if (slot != 0 || cachedOutput.isEmpty()) return ItemStack.EMPTY;
		ItemStack result = cachedOutput.split(amount);
		if (cachedOutput.isEmpty()) {
			resetBasin();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		if (slot != 0) return ItemStack.EMPTY;
		ItemStack result = cachedOutput;
		cachedOutput = ItemStack.EMPTY;
		resetBasin();
		return result;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		if (slot == 0) cachedOutput = stack;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) { return true; }

	@Override
	public void clear() { cachedOutput = ItemStack.EMPTY; }

	@Override
	public boolean isValid(int slot, ItemStack stack) { return false; }

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
		fluidComposition.readFromView(view.getListReadView("Composition"));
		fluidLevelMl = view.getInt("FluidLevel", 0);
		cooldownTicks = view.getInt("CooldownTicks", -1);
		solidified = view.getBoolean("Solidified", false);
	}

	@Override
	protected void writeData(WriteView view) {
		fluidComposition.writeToView(view.getList("Composition"));
		view.putInt("FluidLevel", fluidLevelMl);
		view.putInt("CooldownTicks", cooldownTicks);
		view.putBoolean("Solidified", solidified);
	}
}

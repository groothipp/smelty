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

public class CastingTableBlockEntity extends BlockEntity {
	// 1 ingot capacity. Mold system will replace this with mold-specific values.
	public static final int DEFAULT_CAPACITY = MaterialItems.UNITS_PER_INGOT; // 90
	private static final int COOLDOWN_TICKS = 60; // 3 seconds (faster than basin)

	private final AlloyComposition fluidComposition = new AlloyComposition();
	private int fluidLevelMl;
	private int capacity = DEFAULT_CAPACITY;
	private int cooldownTicks = -1;
	private boolean solidified;
	private boolean needsSync;
	private ItemStack moldItem = ItemStack.EMPTY; // Future: mold determines output

	public CastingTableBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.CASTING_TABLE, pos, state);
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
		return capacity > 0 ? (float) fluidLevelMl / capacity : 0;
	}

	public boolean isSolidified() {
		return solidified;
	}

	public boolean isFull() {
		return fluidLevelMl >= capacity;
	}

	public boolean isEmpty() {
		return fluidLevelMl <= 0;
	}

	public ItemStack getMoldItem() {
		return moldItem;
	}

	public boolean hasMold() {
		return !moldItem.isEmpty();
	}

	/**
	 * Add fluid from a channel. Returns amount accepted.
	 */
	public int addFluid(AlloyComposition source, int amountMl) {
		if (solidified || cooldownTicks >= 0) return 0;

		int space = capacity - fluidLevelMl;
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
		if (isEmpty()) return;

		// Start cooldown when full
		if (isFull() && cooldownTicks < 0) {
			cooldownTicks = COOLDOWN_TICKS;
		}

		if (cooldownTicks > 0) {
			cooldownTicks--;

			if (cooldownTicks % 10 == 0) {
				double x = pos.getX() + 0.5;
				double y = pos.getY() + 0.85;
				double z = pos.getZ() + 0.5;
				world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.2, 0.05, 0.2, 0.01);
			}

			if (cooldownTicks <= 0) {
				solidified = true;
				markDirty();
				needsSync = true;
			}
		}

		// Sync to client (during filling, cooling, and solidification)
		if (needsSync || cooldownTicks >= 0 || solidified) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			needsSync = false;
		}
	}

	/**
	 * Player right-click interaction.
	 * - If solidified: extract the result.
	 * - Otherwise: future mold placement (currently no-op).
	 */
	public boolean onInteract(PlayerEntity player) {
		if (solidified) {
			return tryExtract(player);
		}
		return false;
	}

	private boolean tryExtract(PlayerEntity player) {
		if (!solidified) return false;

		// Pure single-material alloy → return vanilla items
		java.util.List<ItemStack> vanillaItems = MaterialItems.getPureVanillaItems(fluidComposition);
		if (vanillaItems != null) {
			for (ItemStack vanillaStack : vanillaItems) {
				giveOrDrop(player, vanillaStack);
			}
		} else {
			// Mixed alloy → solid alloy block
			ItemStack stack = new ItemStack(SmeltyBlocks.SOLID_ALLOY);
			SolidAlloyBlockEntity tempBe = new SolidAlloyBlockEntity(BlockPos.ORIGIN, SmeltyBlocks.SOLID_ALLOY.getDefaultState());
			tempBe.setComposition(fluidComposition);
			tempBe.setVolumeMl(fluidLevelMl);

			if (world instanceof ServerWorld serverWorld) {
				stack.set(DataComponentTypes.BLOCK_ENTITY_DATA,
						TypedEntityData.create(SmeltyBlockEntities.SOLID_ALLOY,
								tempBe.createNbt(serverWorld.getRegistryManager())));
				stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
						new CustomModelDataComponent(
								java.util.List.of(), java.util.List.of(), java.util.List.of(),
								java.util.List.of(tempBe.getColor())));
			}
			giveOrDrop(player, stack);
		}

		reset();
		return true;
	}

	private void giveOrDrop(PlayerEntity player, ItemStack stack) {
		if (!player.giveItemStack(stack)) {
			ItemEntity itemEntity = new ItemEntity(world,
					pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
			world.spawnEntity(itemEntity);
		}
	}

	private void reset() {
		fluidComposition.clear();
		fluidLevelMl = 0;
		cooldownTicks = -1;
		solidified = false;
		markDirty();
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	/**
	 * Drop contents when the block is broken.
	 */
	public void dropContents(ServerWorld world) {
		if (solidified && !fluidComposition.isEmpty()) {
			java.util.List<ItemStack> vanillaItems = MaterialItems.getPureVanillaItems(fluidComposition);
			if (vanillaItems != null) {
				for (ItemStack vanillaStack : vanillaItems) {
					ItemEntity itemEntity = new ItemEntity(world,
							pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, vanillaStack);
					world.spawnEntity(itemEntity);
				}
			} else {
				ItemStack stack = new ItemStack(SmeltyBlocks.SOLID_ALLOY);
				SolidAlloyBlockEntity tempBe = new SolidAlloyBlockEntity(BlockPos.ORIGIN, SmeltyBlocks.SOLID_ALLOY.getDefaultState());
				tempBe.setComposition(fluidComposition);
				tempBe.setVolumeMl(fluidLevelMl);

				stack.set(DataComponentTypes.BLOCK_ENTITY_DATA,
						TypedEntityData.create(SmeltyBlockEntities.SOLID_ALLOY,
								tempBe.createNbt(world.getRegistryManager())));
				stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
						new CustomModelDataComponent(
								java.util.List.of(), java.util.List.of(), java.util.List.of(),
								java.util.List.of(tempBe.getColor())));

				ItemEntity itemEntity = new ItemEntity(world,
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
				world.spawnEntity(itemEntity);
			}
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
		fluidComposition.readFromView(view.getListReadView("Composition"));
		fluidLevelMl = view.getInt("FluidLevel", 0);
		capacity = view.getInt("Capacity", DEFAULT_CAPACITY);
		cooldownTicks = view.getInt("CooldownTicks", -1);
		solidified = view.getBoolean("Solidified", false);
	}

	@Override
	protected void writeData(WriteView view) {
		fluidComposition.writeToView(view.getList("Composition"));
		view.putInt("FluidLevel", fluidLevelMl);
		view.putInt("Capacity", capacity);
		view.putInt("CooldownTicks", cooldownTicks);
		view.putBoolean("Solidified", solidified);
	}
}

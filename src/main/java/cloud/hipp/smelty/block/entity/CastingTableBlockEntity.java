package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialItems;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

import java.util.Map;
import java.util.Set;

public class CastingTableBlockEntity extends BlockEntity {
	// 2 ingots capacity for plates and molds
	public static final int DEFAULT_CAPACITY = MaterialItems.UNITS_PER_INGOT * 2; // 360
	private static final int COOLDOWN_TICKS = 60; // 3 seconds

	/** Items that can be placed as a pattern for ingot molds. */
	private static final Set<Item> INGOT_PATTERN_ITEMS = Set.of(
			Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT,
			Items.NETHERITE_INGOT
	);

	/** Items that can be placed as a pattern for diamond molds. */
	private static final Set<Item> DIAMOND_PATTERN_ITEMS = Set.of(Items.DIAMOND);

	/** Items that can be placed as a pattern for nugget molds. */
	private static final Set<Item> NUGGET_PATTERN_ITEMS = Set.of(
			Items.IRON_NUGGET, Items.GOLD_NUGGET
	);

	private final AlloyComposition fluidComposition = new AlloyComposition();
	private int fluidLevelMl;
	private int capacity = DEFAULT_CAPACITY;
	private int cooldownTicks = -1;
	private boolean solidified;
	private boolean needsSync;
	private ItemStack patternItem = ItemStack.EMPTY;

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

	public ItemStack getPatternItem() {
		return patternItem;
	}

	public boolean hasPattern() {
		return !patternItem.isEmpty();
	}

	/**
	 * Returns the sole material if the composition is pure, or null for alloys.
	 */
	public SmeltyMaterial getSoleMaterial() {
		Map<SmeltyMaterial, Integer> mats = fluidComposition.getMaterials();
		if (mats.size() == 1) return mats.keySet().iterator().next();
		return null;
	}

	/**
	 * Compute the output item that would be produced, without side effects.
	 * Used by the renderer to display the solidified result.
	 */
	public ItemStack computeOutputItem() {
		if (!solidified || fluidComposition.isEmpty()) return ItemStack.EMPTY;
		Item patItem = patternItem.getItem();
		if (hasPattern() && isMold(patItem)) {
			return createMoldOutput(patItem);
		} else if (hasPattern() && isPureIron()) {
			Item moldItem = getMoldForRawPattern(patternItem);
			return moldItem != null ? new ItemStack(moldItem) : createPlateOutput();
		} else {
			return createPlateOutput();
		}
	}

	/**
	 * Add fluid from a channel. Returns amount accepted.
	 */
	public int addFluid(AlloyComposition source, int amountMl) {
		if (solidified || cooldownTicks >= 0) return 0;
		if (!acceptsFluid(source)) return 0;

		int space = capacity - fluidLevelMl;
		int accepted = Math.min(amountMl, space);
		if (accepted <= 0) return 0;

		AlloyComposition portion = source.drainAndReturn(accepted);
		int actualVolume = portion.getTotalVolumeMl();
		fluidComposition.mergeFrom(portion);
		fluidLevelMl += actualVolume;
		needsSync = true;
		markDirty();
		return actualVolume;
	}

	/**
	 * Check whether the current mold accepts the given fluid.
	 * Diamond mold only accepts pure diamond; ingot mold rejects pure diamond.
	 */
	private boolean acceptsFluid(AlloyComposition source) {
		if (!hasPattern()) return true;
		Item mold = patternItem.getItem();
		boolean sourceIsPureDiamond = source.getMaterials().size() == 1
				&& source.getMaterials().containsKey(SmeltyMaterial.DIAMOND);
		if (mold == SmeltyItems.DIAMOND_MOLD) {
			return sourceIsPureDiamond;
		}
		if (mold == SmeltyItems.INGOT_MOLD) {
			return !sourceIsPureDiamond;
		}
		return true;
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
	 * - If empty (no fluid): place or remove pattern item.
	 */
	public boolean onInteract(PlayerEntity player) {
		if (solidified) {
			return tryExtract(player);
		}
		if (isEmpty()) {
			return tryPatternInteraction(player);
		}
		return false;
	}

	/**
	 * Handle placing or removing a pattern item on the empty table.
	 */
	private boolean tryPatternInteraction(PlayerEntity player) {
		ItemStack held = player.getMainHandStack();

		if (hasPattern() && held.isEmpty()) {
			// Remove pattern
			giveOrDrop(player, patternItem.copy());
			patternItem = ItemStack.EMPTY;
			capacity = DEFAULT_CAPACITY;
			needsSync = true;
			markDirty();
			if (world instanceof ServerWorld sw) {
				sw.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			}
			return true;
		}

		if (hasPattern() && !held.isEmpty() && isMold(held.getItem()) && isMold(patternItem.getItem())) {
			if (held.getItem() == patternItem.getItem()) {
				// Same mold: remove it
				giveOrDrop(player, patternItem.copy());
				patternItem = ItemStack.EMPTY;
				capacity = DEFAULT_CAPACITY;
			} else {
				// Different mold: swap
				giveOrDrop(player, patternItem.copy());
				patternItem = held.copyWithCount(1);
				held.decrement(1);
				capacity = capacityForPattern(patternItem);
			}
			needsSync = true;
			markDirty();
			if (world instanceof ServerWorld sw) {
				sw.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			}
			return true;
		}

		if (!hasPattern() && !held.isEmpty() && isValidPattern(held)) {
			// Place pattern
			patternItem = held.copyWithCount(1);
			held.decrement(1);
			capacity = capacityForPattern(patternItem);
			needsSync = true;
			markDirty();
			if (world instanceof ServerWorld sw) {
				sw.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
			}
			return true;
		}

		return false;
	}

	/**
	 * Check if an item can be placed as a casting pattern.
	 */
	private boolean isValidPattern(ItemStack stack) {
		Item item = stack.getItem();
		return INGOT_PATTERN_ITEMS.contains(item)
				|| NUGGET_PATTERN_ITEMS.contains(item)
				|| DIAMOND_PATTERN_ITEMS.contains(item)
				|| item == Items.STICK
				|| isMold(item);
	}

	public static boolean isMold(Item item) {
		return item == SmeltyItems.INGOT_MOLD
				|| item == SmeltyItems.NUGGET_MOLD
				|| item == SmeltyItems.ROD_MOLD
				|| item == SmeltyItems.DIAMOND_MOLD;
	}

	/**
	 * Returns the capacity for the given pattern, or DEFAULT_CAPACITY if no mold.
	 */
	private static int capacityForPattern(ItemStack pattern) {
		if (pattern.isEmpty()) return DEFAULT_CAPACITY;
		Item item = pattern.getItem();
		if (item == SmeltyItems.INGOT_MOLD) return MaterialItems.INGOT_VOLUME;   // 180
		if (item == SmeltyItems.NUGGET_MOLD) return MaterialItems.NUGGET_VOLUME; // 20
		if (item == SmeltyItems.ROD_MOLD) return MaterialItems.INGOT_VOLUME / 2; // 90
		if (item == SmeltyItems.DIAMOND_MOLD) return MaterialItems.INGOT_VOLUME; // 180
		return DEFAULT_CAPACITY; // raw patterns use plate capacity
	}

	private boolean isPureIron() {
		Map<SmeltyMaterial, Integer> materials = fluidComposition.getMaterials();
		return materials.size() == 1 && materials.containsKey(SmeltyMaterial.IRON);
	}

	private boolean tryExtract(PlayerEntity player) {
		if (!solidified) return false;

		Item patItem = patternItem.getItem();

		if (hasPattern() && isMold(patItem)) {
			// Using a mold: produce the cast item, keep the mold
			giveOrDrop(player, createMoldOutput(patItem));
			resetKeepPattern();
		} else if (hasPattern()) {
			// Mold creation: raw pattern + pure iron → mold item + return pattern
			if (isPureIron()) {
				Item moldItem = getMoldForRawPattern(patternItem);
				if (moldItem != null) {
					giveOrDrop(player, new ItemStack(moldItem));
				}
				giveOrDrop(player, patternItem.copy());
			} else {
				// Wrong alloy — return the pattern and produce a plate
				giveOrDrop(player, patternItem.copy());
				giveOrDrop(player, createPlateOutput());
			}
			reset();
		} else {
			// No pattern → produce a plate
			giveOrDrop(player, createPlateOutput());
			reset();
		}

		return true;
	}

	/**
	 * Produce an item from a mold cast.
	 */
	private ItemStack createMoldOutput(Item mold) {
		Map<SmeltyMaterial, Integer> materials = fluidComposition.getMaterials();
		if (materials.size() == 1) {
			SmeltyMaterial material = materials.keySet().iterator().next();
			if (mold == SmeltyItems.INGOT_MOLD || mold == SmeltyItems.DIAMOND_MOLD) {
				Item ingot = SmeltyItems.getCastIngot(material);
				if (ingot != null) return new ItemStack(ingot);
			} else if (mold == SmeltyItems.NUGGET_MOLD) {
				Item nugget = SmeltyItems.getCastNugget(material);
				if (nugget != null) return new ItemStack(nugget);
			} else if (mold == SmeltyItems.ROD_MOLD) {
				Item rod = SmeltyItems.getRodForMaterial(material);
				if (rod != null) return new ItemStack(rod);
			}
		}
		// Mixed alloy fallback
		if (mold == SmeltyItems.INGOT_MOLD || mold == SmeltyItems.DIAMOND_MOLD) return createAlloyStack(SmeltyItems.ALLOY_INGOT);
		if (mold == SmeltyItems.NUGGET_MOLD) return createAlloyStack(SmeltyItems.ALLOY_NUGGET);
		if (mold == SmeltyItems.ROD_MOLD) return createAlloyStack(SmeltyItems.ALLOY_ROD);
		return createPlateOutput();
	}

	private Item getMoldForRawPattern(ItemStack pattern) {
		Item item = pattern.getItem();
		if (INGOT_PATTERN_ITEMS.contains(item)) return SmeltyItems.INGOT_MOLD;
		if (NUGGET_PATTERN_ITEMS.contains(item)) return SmeltyItems.NUGGET_MOLD;
		if (DIAMOND_PATTERN_ITEMS.contains(item)) return SmeltyItems.DIAMOND_MOLD;
		if (item == Items.STICK) return SmeltyItems.ROD_MOLD;
		return null;
	}

	private ItemStack createPlateOutput() {
		Map<SmeltyMaterial, Integer> materials = fluidComposition.getMaterials();
		if (materials.size() == 1) {
			SmeltyMaterial material = materials.keySet().iterator().next();
			Item plate = SmeltyItems.getPlateForMaterial(material);
			if (plate != null) {
				return new ItemStack(plate);
			}
		}
		// Mixed alloy → alloy plate
		return createAlloyStack(SmeltyItems.ALLOY_PLATE);
	}

	private ItemStack createAlloyStack(Item item) {
		// Normalize to coarse base (5% resolution) to collapse ±1-2 mL drain rounding,
		// ensuring ingots from the same alloy batch always have identical component data.
		AlloyComposition normalized = fluidComposition.toNormalized(AlloyComposition.ITEM_RATIO_BASE);
		ItemStack stack = new ItemStack(item);
		java.util.List<Float> percentages = new java.util.ArrayList<>();
		for (cloud.hipp.smelty.material.SmeltyMaterial mat : cloud.hipp.smelty.material.SmeltyMaterial.values()) {
			percentages.add((float) normalized.getMaterials().getOrDefault(mat, 0));
		}
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
				new CustomModelDataComponent(
						percentages, java.util.List.of(), java.util.List.of(),
						java.util.List.of(normalized.getBlendedColor())));
		return stack;
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
		patternItem = ItemStack.EMPTY;
		capacity = DEFAULT_CAPACITY;
		markDirty();
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** Reset fluid state but keep the mold on the table for reuse. */
	private void resetKeepPattern() {
		fluidComposition.clear();
		fluidLevelMl = 0;
		cooldownTicks = -1;
		solidified = false;
		// patternItem and capacity stay
		markDirty();
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public void onBlockReplaced(BlockPos pos, BlockState state) {
		super.onBlockReplaced(pos, state);
		if (world instanceof ServerWorld serverWorld) {
			dropContents(serverWorld);
		}
	}

	/**
	 * Drop contents when the block is broken.
	 */
	private void dropContents(ServerWorld world) {
		if (hasPattern()) {
			Item patItem = patternItem.getItem();
			if (solidified && !fluidComposition.isEmpty() && isMold(patItem)) {
				// Mold + solidified → drop the mold and the cast output
				dropAt(world, patternItem.copy());
				dropAt(world, createMoldOutput(patItem));
			} else if (solidified && !fluidComposition.isEmpty()) {
				// Raw pattern + solidified
				if (isPureIron()) {
					Item moldItem = getMoldForRawPattern(patternItem);
					if (moldItem != null) {
						dropAt(world, new ItemStack(moldItem));
					}
					dropAt(world, patternItem.copy());
				} else {
					dropAt(world, patternItem.copy());
					dropAt(world, createPlateOutput());
				}
			} else {
				// Not solidified — just drop the pattern
				dropAt(world, patternItem.copy());
			}
		} else if (solidified && !fluidComposition.isEmpty()) {
			dropAt(world, createPlateOutput());
		}
	}

	private void dropAt(ServerWorld world, ItemStack stack) {
		ItemEntity itemEntity = new ItemEntity(world,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
		world.spawnEntity(itemEntity);
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
		patternItem = view.read("PatternItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void writeData(WriteView view) {
		fluidComposition.writeToView(view.getList("Composition"));
		view.putInt("FluidLevel", fluidLevelMl);
		view.putInt("Capacity", capacity);
		view.putInt("CooldownTicks", cooldownTicks);
		view.putBoolean("Solidified", solidified);
		view.put("PatternItem", ItemStack.OPTIONAL_CODEC, patternItem);
	}
}

package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.block.SmelterBlock;
import cloud.hipp.smelty.block.SmelterControllerBlock;
import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.block.SolidAlloyBlock;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialItems;
import cloud.hipp.smelty.material.SmeltyMaterial;
import cloud.hipp.smelty.screen.SmelterControllerScreenHandler;
import cloud.hipp.smelty.screen.SmelterData;
import cloud.hipp.smelty.structure.MultiblockValidator;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SmelterControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<SmelterData> {
	private static final int ML_PER_INTERIOR_BLOCK = 2000;

	// Static color registries for fluid rendering
	private static final Map<BlockPos, int[]> COLOR_REGISTRY = new ConcurrentHashMap<>();
	private static final Map<BlockPos, Integer> OUTFLOW_COLORS = new ConcurrentHashMap<>();

	public static int lookupFluidColor(BlockPos fluidPos) {
		int closestColor = 0xFF6600;
		int closestDistSq = Integer.MAX_VALUE;

		// Check outflow sources (point-to-point distance)
		for (var entry : OUTFLOW_COLORS.entrySet()) {
			BlockPos sourcePos = entry.getKey();
			int dx = fluidPos.getX() - sourcePos.getX();
			int dy = fluidPos.getY() - sourcePos.getY();
			int dz = fluidPos.getZ() - sourcePos.getZ();
			int distSq = dx * dx + dy * dy + dz * dz;
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closestColor = entry.getValue();
			}
		}

		// Check smelter interiors (distance to nearest point on interior box)
		for (var entry : COLOR_REGISTRY.entrySet()) {
			int[] data = entry.getValue();
			int color = data[0], mX = data[1], mY = data[2], mZ = data[3], w = data[4], d = data[5], h = data[6];
			int cx = Math.max(mX + 1, Math.min(fluidPos.getX(), mX + w - 2));
			int cy = Math.max(mY + 2, Math.min(fluidPos.getY(), mY + h - 1));
			int cz = Math.max(mZ + 1, Math.min(fluidPos.getZ(), mZ + d - 2));
			int dx = fluidPos.getX() - cx;
			int dy = fluidPos.getY() - cy;
			int dz = fluidPos.getZ() - cz;
			int distSq = dx * dx + dy * dy + dz * dz;
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closestColor = color;
			}
		}

		return closestColor;
	}

	private void updateColorRegistry() {
		if (width > 0) {
			COLOR_REGISTRY.put(pos.toImmutable(),
					new int[]{cachedAlloyColor, minX, minY, minZ, width, depth, height});
		} else {
			COLOR_REGISTRY.remove(pos.toImmutable());
		}
	}

	// --- Structure data ---
	private boolean valid;
	private int width, depth, height;
	private int heatLevel;
	private int minX, minY, minZ;
	private int maxVolume;
	private int currentVolume;

	// --- Alloy data ---
	private final AlloyComposition moltenAlloy = new AlloyComposition();
	private final AlloyComposition unmeltedMaterials = new AlloyComposition();
	private int cachedAlloyColor = 0xFF6600;

	// --- Queue/Stack for fluid and solid placement ---
	private final ArrayDeque<BlockPos> emptyQueue = new ArrayDeque<>();
	private final ArrayDeque<BlockPos> filledStack = new ArrayDeque<>();
	private final ArrayDeque<BlockPos> solidPositions = new ArrayDeque<>();
	private int lastSyncedFluidCount = -1;
	private int lastSyncedSolidCount = -1;
	private boolean needsInitialSync = false;

	// --- Outflow management ---
	private final List<BlockPos> activeOutflows = new ArrayList<>();

	public SmelterControllerBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.SMELTER_CONTROLLER, pos, state);
	}

	// --- Structure formation ---

	public void setMultiblockData(MultiblockValidator.Result result) {
		this.valid = result.valid();
		this.width = result.width();
		this.depth = result.depth();
		this.height = result.height();
		this.heatLevel = result.heatLevel();
		this.minX = result.minX();
		this.minY = result.minY();
		this.minZ = result.minZ();

		if (valid) {
			int interiorW = width - 2;
			int interiorD = depth - 2;
			int chamberH = height - 2;
			this.maxVolume = interiorW * interiorD * chamberH * ML_PER_INTERIOR_BLOCK;
			initializeQueues();
			updateColorRegistry();
			Smelty.LOGGER.info("Smelter formed! {}x{}x{}, heat: {}, max volume: {}mL", width, depth, height, heatLevel, maxVolume);
			spawnFormationParticles(result);
		} else {
			this.maxVolume = 0;
			Smelty.LOGGER.info("Smelter structure invalid");
		}

		markDirty();
	}

	private void initializeQueues() {
		emptyQueue.clear();
		filledStack.clear();
		solidPositions.clear();
		lastSyncedFluidCount = -1;
		lastSyncedSolidCount = -1;

		// Populate emptyQueue: bottom-up, X then Z per layer
		for (int y = minY + 2; y < minY + height; y++) {
			for (int x = minX + 1; x < minX + width - 1; x++) {
				for (int z = minZ + 1; z < minZ + depth - 1; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (world != null) {
						BlockState state = world.getBlockState(p);
						if (state.isOf(SmeltyBlocks.MOLTEN_ALLOY_BLOCK)) {
							filledStack.push(p);
						} else if (state.isOf(SmeltyBlocks.SOLID_ALLOY)) {
							solidPositions.push(p);
						} else {
							emptyQueue.add(p);
						}
					} else {
						emptyQueue.add(p);
					}
				}
			}
		}
	}

	// --- Melting logic ---

	private void tryMeltUnmeltedMaterials() {
		if (unmeltedMaterials.isEmpty()) return;

		var iterator = unmeltedMaterials.getMaterials().entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<SmeltyMaterial, Integer> entry = iterator.next();
			if (entry.getKey().getRequiredHeat() <= heatLevel) {
				moltenAlloy.addMaterial(entry.getKey(), entry.getValue());
				iterator.remove();
			}
		}
		checkSolidification();
	}

	private void checkSolidification() {
		if (moltenAlloy.isEmpty()) return;
		if (moltenAlloy.getRequiredHeat() > heatLevel) {
			unmeltedMaterials.mergeFrom(moltenAlloy);
			moltenAlloy.clear();
		}
	}

	private boolean canMelt(SmeltyMaterial material) {
		return material.getRequiredHeat() <= heatLevel;
	}

	private void updateCurrentVolume() {
		this.currentVolume = moltenAlloy.getTotalVolumeMl() + unmeltedMaterials.getTotalVolumeMl();
	}

	// --- Item processing ---

	private void processItemEntities(ServerWorld serverWorld) {
		double x1 = minX + 1;
		double y1 = minY + 2;
		double z1 = minZ + 1;
		double x2 = minX + width - 1;
		double y2 = minY + height;
		double z2 = minZ + depth - 1;

		Box interiorBox = new Box(x1, y1, z1, x2, y2, z2);
		java.util.List<ItemEntity> items = serverWorld.getEntitiesByClass(ItemEntity.class, interiorBox, entity -> true);

		for (ItemEntity itemEntity : items) {
			ItemStack stack = itemEntity.getStack();

			if (stack.isOf(SmeltyBlocks.SOLID_ALLOY.asItem())) {
				processSolidAlloyItem(serverWorld, itemEntity, stack);
				continue;
			}

			MaterialItems.MaterialEntry entry = MaterialItems.lookup(stack);
			if (entry == null) continue;

			int itemCount = stack.getCount();
			int processed = 0;

			for (int i = 0; i < itemCount; i++) {
				int volumePerItem = entry.volumeMl();

				if (entry.rawOre() && serverWorld.getRandom().nextFloat() < 0.1f) {
					volumePerItem *= 2;
				}

				if (currentVolume + volumePerItem > maxVolume) break;

				if (canMelt(entry.material())) {
					moltenAlloy.addMaterial(entry.material(), volumePerItem);
				} else {
					unmeltedMaterials.addMaterial(entry.material(), volumePerItem);
				}

				processed++;
				updateCurrentVolume();
			}

			if (processed > 0) {
				if (processed >= itemCount) {
					itemEntity.discard();
				} else {
					stack.decrement(processed);
				}
				checkSolidification();
				updateCurrentVolume();
				markDirty();
			}
		}
	}

	private void processSolidAlloyItem(ServerWorld serverWorld, ItemEntity itemEntity, ItemStack stack) {
		var beData = stack.get(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA);
		if (beData == null) return;

		SolidAlloyBlockEntity tempBe = new SolidAlloyBlockEntity(pos, world.getBlockState(pos));
		tempBe.setWorld(world);
		if (!beData.applyToBlockEntity(tempBe, serverWorld.getRegistryManager())) return;

		AlloyComposition comp = tempBe.getComposition();
		if (comp.isEmpty()) return;

		int totalMl = comp.getTotalVolumeMl();
		if (currentVolume + totalMl > maxVolume) return;

		for (Map.Entry<SmeltyMaterial, Integer> entry : comp.getMaterials().entrySet()) {
			if (canMelt(entry.getKey())) {
				moltenAlloy.addMaterial(entry.getKey(), entry.getValue());
			} else {
				unmeltedMaterials.addMaterial(entry.getKey(), entry.getValue());
			}
		}

		stack.decrement(1);
		if (stack.isEmpty()) {
			itemEntity.discard();
		}

		checkSolidification();
		updateCurrentVolume();
		markDirty();
	}

	// --- Fluid block management (Queue/Stack) ---

	private void syncSolidBlocks(ServerWorld serverWorld) {
		int bottomLayerCapacity = (width - 2) * (depth - 2);
		int solidDesired = Math.min(unmeltedMaterials.getTotalVolumeMl() / 1000, bottomLayerCapacity);

		if (solidDesired == lastSyncedSolidCount) return;
		lastSyncedSolidCount = solidDesired;

		// Fill: place solid alloy blocks at bottom layer only
		while (solidPositions.size() < solidDesired && !emptyQueue.isEmpty()) {
			BlockPos nextPos = emptyQueue.peek();
			if (nextPos.getY() != minY + 2) break; // only bottom layer
			emptyQueue.poll();
			serverWorld.setBlockState(nextPos, SmeltyBlocks.SOLID_ALLOY.getDefaultState(), Block.NOTIFY_LISTENERS);
			BlockEntity be = serverWorld.getBlockEntity(nextPos);
			if (be instanceof SolidAlloyBlockEntity solidBe) {
				solidBe.setComposition(unmeltedMaterials);
				solidBe.setManaged(true);
				solidBe.markDirty();
			}
			solidPositions.push(nextPos);
		}

		// Drain: remove excess solid blocks
		if (solidPositions.size() > solidDesired) {
			SolidAlloyBlock.setSuppressDrops(true);
			try {
				while (solidPositions.size() > solidDesired) {
					BlockPos drainPos = solidPositions.pop();
					serverWorld.setBlockState(drainPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					emptyQueue.addFirst(drainPos);
				}
			} finally {
				SolidAlloyBlock.setSuppressDrops(false);
			}
		}

		markDirty();
	}

	private void syncFluidBlocks(ServerWorld serverWorld) {
		int desiredCount = moltenAlloy.getTotalVolumeMl() / 1000;

		if (desiredCount == lastSyncedFluidCount) return;
		lastSyncedFluidCount = desiredCount;

		// Update color
		int newColor = moltenAlloy.isEmpty() ? 0xFF6600 : moltenAlloy.getBlendedColor();
		if (newColor != cachedAlloyColor) {
			cachedAlloyColor = newColor;
			updateColorRegistry();
			serverWorld.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}

		BlockState fluidState = SmeltyBlocks.MOLTEN_ALLOY_BLOCK.getDefaultState();

		// Fill: dequeue empty → place fluid → push to filled
		while (filledStack.size() < desiredCount && !emptyQueue.isEmpty()) {
			BlockPos fillPos = emptyQueue.poll();
			serverWorld.setBlockState(fillPos, fluidState, Block.NOTIFY_LISTENERS);
			filledStack.push(fillPos);
		}

		// Drain: pop filled → remove fluid → return to empty queue
		while (filledStack.size() > desiredCount) {
			BlockPos drainPos = filledStack.pop();
			serverWorld.setBlockState(drainPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			emptyQueue.addFirst(drainPos);
		}

		markDirty();
	}

	/** Called when controller block is broken — clears all interior blocks, data is lost */
	public void clearAllInteriorBlocks(ServerWorld serverWorld) {
		clearOutflows(serverWorld);
		SolidAlloyBlock.setSuppressDrops(true);
		try {
			while (!filledStack.isEmpty()) {
				BlockPos p = filledStack.pop();
				BlockState current = serverWorld.getBlockState(p);
				if (current.isOf(SmeltyBlocks.MOLTEN_ALLOY_BLOCK) || current.isOf(SmeltyBlocks.SOLID_ALLOY)) {
					serverWorld.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
			while (!solidPositions.isEmpty()) {
				BlockPos p = solidPositions.pop();
				if (serverWorld.getBlockState(p).isOf(SmeltyBlocks.SOLID_ALLOY)) {
					serverWorld.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		} finally {
			SolidAlloyBlock.setSuppressDrops(false);
		}
		COLOR_REGISTRY.remove(pos.toImmutable());
	}

	/** Called when structure becomes invalid — keeps alloy data and all blocks in place */
	private void onInvalidated(ServerWorld serverWorld) {
		// Leave all blocks in place — fluid flows naturally, solids stay visible
		// Keep filledStack so outflow can drain from it; clear the rest for re-validation rebuild
		emptyQueue.clear();
		solidPositions.clear();

		this.valid = false;
		this.heatLevel = 0;
		this.lastSyncedFluidCount = -1;
		this.lastSyncedSolidCount = -1;
		markDirty();
	}

	// --- Outflow management ---

	/**
	 * Runs every 20 ticks while the smelter is invalid.
	 * For each wall gap, processes the outflow drain cycle.
	 */
	private void manageOutflow(ServerWorld serverWorld) {
		if (width <= 0) return;

		long time = world.getTime();
		if (time % 20 != 0) return;

		List<BlockPos> gaps = findWallGaps();

		// Clean up outflows at sealed gaps (wall replaced)
		var iter = activeOutflows.iterator();
		while (iter.hasNext()) {
			BlockPos outflow = iter.next();
			if (!gaps.contains(outflow)) {
				despawnOutflow(serverWorld, outflow);
				iter.remove();
			}
		}

		// Process each gap through the drain cycle
		for (BlockPos gap : gaps) {
			processOutflowCycle(serverWorld, gap);
		}

		// Update alloy color after all drains
		if (!moltenAlloy.isEmpty()) {
			int newColor = moltenAlloy.getBlendedColor();
			if (newColor != cachedAlloyColor) {
				cachedAlloyColor = newColor;
				updateColorRegistry();
				for (BlockPos outflow : activeOutflows) {
					OUTFLOW_COLORS.put(outflow.toImmutable(), cachedAlloyColor);
				}
			}
		}

		markDirty();
	}

	/**
	 * Processes one drain cycle for a single wall gap.
	 * Follows the flow: check → spawn → drain → maybe remove block → maybe despawn.
	 */
	private void processOutflowCycle(ServerWorld serverWorld, BlockPos gapPos) {
		int fluidLevelY = filledStack.isEmpty() ? minY + 1 : filledStack.peek().getY();

		// Step 1: if volume is 0 or fluid level is below the hole, do nothing
		if (moltenAlloy.getTotalVolumeMl() == 0 || fluidLevelY < gapPos.getY()) {
			if (activeOutflows.contains(gapPos)) {
				despawnOutflow(serverWorld, gapPos);
				activeOutflows.remove(gapPos);
			}
			return;
		}

		// Skip if backed up
		if (isOutflowBackedUp(serverWorld, gapPos)) return;

		// Step 2: spawn a molten alloy fluid source at the hole
		if (!serverWorld.getBlockState(gapPos).isOf(SmeltyBlocks.MOLTEN_ALLOY_BLOCK)) {
			serverWorld.setBlockState(gapPos, SmeltyBlocks.MOLTEN_ALLOY_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		if (!activeOutflows.contains(gapPos)) {
			activeOutflows.add(gapPos.toImmutable());
		}
		OUTFLOW_COLORS.put(gapPos.toImmutable(), cachedAlloyColor);

		// Step 3: decrease current volume by 1 block
		moltenAlloy.drain(1000);
		updateCurrentVolume();

		// Step 4: if current volume is above interior capacity (maxVolume / 2), skip to step 7
		int interiorCapacityMl = maxVolume / 2;
		if (currentVolume < interiorCapacityMl) {
			// Step 5: pop filled stack, destroy block, add to free queue
			if (!filledStack.isEmpty()) {
				BlockPos drainPos = filledStack.pop();
				if (serverWorld.getBlockState(drainPos).isOf(SmeltyBlocks.MOLTEN_ALLOY_BLOCK)) {
					serverWorld.setBlockState(drainPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
				emptyQueue.addFirst(drainPos);
				updateCurrentVolume();
			}
		}

		// Step 6: if fluid level now below hole or volume is 0, despawn and stop
		fluidLevelY = filledStack.isEmpty() ? minY + 1 : filledStack.peek().getY();
		if (fluidLevelY < gapPos.getY() || moltenAlloy.getTotalVolumeMl() == 0) {
			despawnOutflow(serverWorld, gapPos);
			activeOutflows.remove(gapPos);
			return;
		}

		// Step 7: wait (handled by the 20-tick cycle in manageOutflow)
	}

	private void despawnOutflow(ServerWorld serverWorld, BlockPos outflowPos) {
		if (serverWorld.getBlockState(outflowPos).isOf(SmeltyBlocks.MOLTEN_ALLOY_BLOCK)) {
			serverWorld.setBlockState(outflowPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		OUTFLOW_COLORS.remove(outflowPos.toImmutable());
	}

	private void clearOutflows(ServerWorld serverWorld) {
		for (BlockPos outflow : activeOutflows) {
			despawnOutflow(serverWorld, outflow);
		}
		activeOutflows.clear();
	}

	private List<BlockPos> findWallGaps() {
		List<BlockPos> gaps = new ArrayList<>();
		for (int y = minY + 2; y < minY + height; y++) {
			for (int x = minX; x < minX + width; x++) {
				for (int z = minZ; z < minZ + depth; z++) {
					boolean onPerimeter = x == minX || x == minX + width - 1 || z == minZ || z == minZ + depth - 1;
					if (!onPerimeter) continue;

					BlockPos wallPos = new BlockPos(x, y, z);
					if (wallPos.equals(pos)) continue; // skip controller

					BlockState state = world.getBlockState(wallPos);
					if (!(state.getBlock() instanceof SmelterBlock) && !(state.getBlock() instanceof SmelterControllerBlock)) {
						gaps.add(wallPos);
					}
				}
			}
		}
		return gaps;
	}

	private Direction getOutwardDirection(BlockPos gapPos) {
		if (gapPos.getX() == minX) return Direction.WEST;
		if (gapPos.getX() == minX + width - 1) return Direction.EAST;
		if (gapPos.getZ() == minZ) return Direction.NORTH;
		if (gapPos.getZ() == minZ + depth - 1) return Direction.SOUTH;
		return Direction.NORTH;
	}

	private boolean isOutflowBackedUp(ServerWorld serverWorld, BlockPos outflowPos) {
		Direction outward = getOutwardDirection(outflowPos);
		BlockPos outsidePos = outflowPos.offset(outward);
		BlockState state = serverWorld.getBlockState(outsidePos);
		return !state.isAir() && !state.isLiquid();
	}

	// --- Heat scanning ---

	private void rescanHeat() {
		int totalHeat = 0;
		for (int x = minX + 1; x < minX + width - 1; x++) {
			for (int z = minZ + 1; z < minZ + depth - 1; z++) {
				totalHeat += MultiblockValidator.getHeatValue(world.getBlockState(new BlockPos(x, minY, z)));
			}
		}
		if (totalHeat != heatLevel) {
			int oldHeat = heatLevel;
			heatLevel = totalHeat;

			if (heatLevel > oldHeat) {
				tryMeltUnmeltedMaterials();
			}
			if (heatLevel < oldHeat) {
				checkSolidification();
			}
			updateCurrentVolume();
			markDirty();
		}
	}

	// --- Structure validation ---

	private void revalidateStructure() {
		MultiblockValidator.Result result = MultiblockValidator.validate(world, pos);
		if (!result.valid()) {
			if (valid && world instanceof ServerWorld serverWorld) {
				onInvalidated(serverWorld);
			}
			return;
		}

		boolean wasInvalid = !valid;
		boolean boundsChanged = width != result.width() || depth != result.depth()
				|| height != result.height() || minX != result.minX()
				|| minY != result.minY() || minZ != result.minZ();

		this.valid = true;
		this.width = result.width();
		this.depth = result.depth();
		this.height = result.height();
		this.heatLevel = result.heatLevel();
		this.minX = result.minX();
		this.minY = result.minY();
		this.minZ = result.minZ();

		if (wasInvalid || boundsChanged) {
			int interiorW = width - 2;
			int interiorD = depth - 2;
			int chamberH = height - 2;
			this.maxVolume = interiorW * interiorD * chamberH * ML_PER_INTERIOR_BLOCK;
			initializeQueues();
			updateColorRegistry();
			updateCurrentVolume();
			lastSyncedFluidCount = -1;
			lastSyncedSolidCount = -1;
			markDirty();

			if (wasInvalid && world instanceof ServerWorld sw) {
				clearOutflows(sw);
				Smelty.LOGGER.info("Smelter re-validated! {}x{}x{}, heat: {}", width, depth, height, heatLevel);
				spawnFormationParticles(result);
			}
		}
	}

	// --- Particles ---

	private void spawnFormationParticles(MultiblockValidator.Result result) {
		if (!(world instanceof ServerWorld serverWorld)) return;

		double centerX = result.minX() + result.width() / 2.0;
		double centerZ = result.minZ() + result.depth() / 2.0;
		double topY = result.minY() + result.height();
		double spreadX = (result.width() - 2) / 2.0;
		double spreadZ = (result.depth() - 2) / 2.0;

		serverWorld.spawnParticles(ParticleTypes.FLAME,
				centerX, topY, centerZ, 50, spreadX, 0.5, spreadZ, 0.1);
		serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
				centerX, topY + 0.5, centerZ, 30, spreadX, 0.8, spreadZ, 0.05);
		serverWorld.spawnParticles(ParticleTypes.LAVA,
				centerX, topY, centerZ, 15, spreadX, 0.3, spreadZ, 0.0);
	}

	// --- Server tick ---

	public static void serverTick(World world, BlockPos pos, BlockState state, SmelterControllerBlockEntity be) {
		if (!(world instanceof ServerWorld serverWorld)) return;

		// Force color registry update after load
		if (be.needsInitialSync) {
			be.needsInitialSync = false;
			be.updateColorRegistry();
			be.updateCurrentVolume();
			serverWorld.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		}

		long time = world.getTime();

		// Revalidate structure every 2 seconds (even when invalid, to detect wall repairs)
		if (time % 40 == 0) {
			be.revalidateStructure();
		}

		if (!be.valid) {
			be.manageOutflow(serverWorld);
			return;
		}

		// Rescan heat sources every second
		if (time % 20 == 0) {
			be.rescanHeat();
		}

		// Process thrown items every 10 ticks
		if (time % 10 == 0) {
			be.processItemEntities(serverWorld);
		}

		// Sync solid blocks (bottom layer) then fluid blocks
		be.syncSolidBlocks(serverWorld);
		be.syncFluidBlocks(serverWorld);

		if (be.heatLevel <= 0) return;

		// --- Particles ---
		double centerX = be.minX + be.width / 2.0;
		double centerZ = be.minZ + be.depth / 2.0;
		double topY = be.minY + be.height;
		double spreadX = (be.width - 2) / 2.0;
		double spreadZ = (be.depth - 2) / 2.0;
		float heatRatio = Math.min(1.0f, be.heatLevel / 3200.0f);

		if (time % 5 == 0) {
			serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
					centerX, topY, centerZ,
					2 + (int) (heatRatio * 15), spreadX * 0.4, 0.0, spreadZ * 0.4, 0.02);
			serverWorld.spawnParticles(ParticleTypes.SMOKE,
					centerX, topY + 0.5, centerZ,
					1 + (int) (heatRatio * 10), spreadX * 0.5, 0.3, spreadZ * 0.5, 0.05);
		}

		if (time % 10 == 0) {
			serverWorld.spawnParticles(ParticleTypes.FLAME,
					centerX, topY, centerZ,
					2 + (int) (heatRatio * 20), spreadX * 0.5, 0.2, spreadZ * 0.5, 0.08);
		}

		if (time % 15 == 0) {
			serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
					centerX, topY + 1.0, centerZ,
					1 + (int) (heatRatio * 10), spreadX * 0.5, 0.5, spreadZ * 0.5, 0.04);
		}

		if (heatRatio > 0.3f && time % 20 == 0) {
			serverWorld.spawnParticles(ParticleTypes.LAVA,
					centerX, topY, centerZ,
					1 + (int) (heatRatio * 8), spreadX * 0.4, 0.1, spreadZ * 0.4, 0.0);
		}
	}

	// --- Accessors ---

	public boolean isValid() { return valid; }
	public int getHeatLevel() { return heatLevel; }
	public int getMaxVolume() { return maxVolume; }
	public int getCurrentVolume() { return currentVolume; }

	// --- Client sync ---

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createComponentlessNbt(registryLookup);
	}

	// --- Screen ---

	private SmelterData buildScreenData() {
		return new SmelterData(
				heatLevel, maxVolume, currentVolume,
				new EnumMap<>(moltenAlloy.getMaterials()),
				unmeltedMaterials.getTotalVolumeMl()
		);
	}

	@Override
	public SmelterData getScreenOpeningData(ServerPlayerEntity player) {
		return buildScreenData();
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.smelty.smelter_controller");
	}

	@Override
	public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new SmelterControllerScreenHandler(syncId, playerInventory, buildScreenData());
	}

	// --- Serialization ---

	@Override
	protected void readData(ReadView view) {
		this.valid = view.getBoolean("Valid", false);
		this.width = view.getInt("Width", 0);
		this.depth = view.getInt("Depth", 0);
		this.height = view.getInt("Height", 0);
		this.heatLevel = view.getInt("HeatLevel", 0);
		this.maxVolume = view.getInt("MaxVolume", 0);
		this.currentVolume = view.getInt("CurrentVolume", 0);
		this.minX = view.getInt("MinX", 0);
		this.minY = view.getInt("MinY", 0);
		this.minZ = view.getInt("MinZ", 0);
		this.cachedAlloyColor = view.getInt("CachedAlloyColor", 0xFF6600);

		moltenAlloy.readFromView(view.getListReadView("MoltenAlloy"));
		unmeltedMaterials.readFromView(view.getListReadView("UnmeltedMaterials"));

		// Read queue/stack
		emptyQueue.clear();
		for (ReadView item : view.getListReadView("EmptyQueue")) {
			emptyQueue.add(new BlockPos(
					item.getInt("X", 0), item.getInt("Y", 0), item.getInt("Z", 0)));
		}
		filledStack.clear();
		for (ReadView item : view.getListReadView("FilledStack")) {
			filledStack.push(new BlockPos(
					item.getInt("X", 0), item.getInt("Y", 0), item.getInt("Z", 0)));
		}
		solidPositions.clear();
		for (ReadView item : view.getListReadView("SolidPositions")) {
			solidPositions.push(new BlockPos(
					item.getInt("X", 0), item.getInt("Y", 0), item.getInt("Z", 0)));
		}
		activeOutflows.clear();
		for (ReadView item : view.getListReadView("ActiveOutflows")) {
			BlockPos outflowPos = new BlockPos(
					item.getInt("X", 0), item.getInt("Y", 0), item.getInt("Z", 0));
			activeOutflows.add(outflowPos);
			OUTFLOW_COLORS.put(outflowPos.toImmutable(), cachedAlloyColor);
		}

		// If we have valid bounds but empty queues, rebuild
		if (valid && width > 0 && emptyQueue.isEmpty() && filledStack.isEmpty()) {
			// Defer to first tick since world may not be available yet
			needsInitialSync = true;
		}

		updateCurrentVolume();
		needsInitialSync = true;
	}

	@Override
	protected void writeData(WriteView view) {
		view.putBoolean("Valid", valid);
		view.putInt("Width", width);
		view.putInt("Depth", depth);
		view.putInt("Height", height);
		view.putInt("HeatLevel", heatLevel);
		view.putInt("MaxVolume", maxVolume);
		view.putInt("CurrentVolume", currentVolume);
		view.putInt("MinX", minX);
		view.putInt("MinY", minY);
		view.putInt("MinZ", minZ);
		view.putInt("CachedAlloyColor", cachedAlloyColor);

		moltenAlloy.writeToView(view.getList("MoltenAlloy"));
		unmeltedMaterials.writeToView(view.getList("UnmeltedMaterials"));

		// Write queue/stack
		WriteView.ListView queueList = view.getList("EmptyQueue");
		for (BlockPos p : emptyQueue) {
			WriteView item = queueList.add();
			item.putInt("X", p.getX());
			item.putInt("Y", p.getY());
			item.putInt("Z", p.getZ());
		}
		WriteView.ListView stackList = view.getList("FilledStack");
		for (BlockPos p : filledStack) {
			WriteView item = stackList.add();
			item.putInt("X", p.getX());
			item.putInt("Y", p.getY());
			item.putInt("Z", p.getZ());
		}
		WriteView.ListView solidList = view.getList("SolidPositions");
		for (BlockPos p : solidPositions) {
			WriteView item = solidList.add();
			item.putInt("X", p.getX());
			item.putInt("Y", p.getY());
			item.putInt("Z", p.getZ());
		}
		WriteView.ListView outflowList = view.getList("ActiveOutflows");
		for (BlockPos p : activeOutflows) {
			WriteView item = outflowList.add();
			item.putInt("X", p.getX());
			item.putInt("Y", p.getY());
			item.putInt("Z", p.getZ());
		}
	}
}

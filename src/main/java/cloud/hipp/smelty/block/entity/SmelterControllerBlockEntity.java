package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.block.SmelterBlock;
import cloud.hipp.smelty.block.SmelterControllerBlock;
import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.block.SolidAlloyBlock;
import cloud.hipp.smelty.block.ValveBlock;
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
import net.minecraft.item.Items;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SmelterControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<SmelterData> {
	// Each interior block = 9 ingots, max volume = 2× interior volume
	private static final int VOLUME_PER_INTERIOR_BLOCK = MaterialItems.UNITS_PER_INGOT * 9 * 2; // 1620

	private static final RegistryKey<DamageType> MOLTEN_ALLOY_DAMAGE =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Smelty.MOD_ID, "molten_alloy"));

	// Client-side registry of active smelter bounds for overlay rendering
	public record FluidBoundsData(int minX, int minY, int minZ, int width, int depth, int height,
								  int maxVolume, int totalVolumeMl, int color) {}
	private static final Map<BlockPos, FluidBoundsData> CLIENT_SMELTERS = new ConcurrentHashMap<>();

	public static Map<BlockPos, FluidBoundsData> getClientSmelters() {
		return CLIENT_SMELTERS;
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
	private int cachedAlloyColor = 0x808080;
	private boolean needsClientSync;

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
			this.maxVolume = interiorW * interiorD * chamberH * VOLUME_PER_INTERIOR_BLOCK;
			Smelty.LOGGER.info("Smelter formed! {}x{}x{}, heat: {}, max volume: {} ingots", width, depth, height, heatLevel, maxVolume / MaterialItems.UNITS_PER_INGOT);
			spawnFormationParticles(result);
		} else {
			this.maxVolume = 0;
			Smelty.LOGGER.info("Smelter structure invalid");
		}

		updateCurrentVolume();
		markDirty();
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
		List<ItemEntity> items = serverWorld.getEntitiesByClass(ItemEntity.class, interiorBox, entity -> true);

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
				} else if (stack.isOf(Items.DIAMOND) && serverWorld.getRandom().nextFloat() < 0.3f) {
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
				serverWorld.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
						SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 2.0f + serverWorld.getRandom().nextFloat() * 0.4f);
				serverWorld.spawnParticles(ParticleTypes.FLAME,
						itemEntity.getX(), itemEntity.getY() + 0.2, itemEntity.getZ(),
						3 + processed, 0.15, 0.1, 0.15, 0.02);
				if (processed >= itemCount) {
					itemEntity.discard();
				} else {
					stack.decrement(processed);
				}
				checkSolidification();
				updateCurrentVolume();
				updateCachedColor();
				markDirty();
				needsClientSync = true;
			}
		}
	}

	private void processSolidAlloyItem(ServerWorld serverWorld, ItemEntity itemEntity, ItemStack stack) {
		var beData = stack.get(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA);
		if (beData == null) return;

		SolidAlloyBlockEntity tempBe = new SolidAlloyBlockEntity(BlockPos.ORIGIN, SmeltyBlocks.SOLID_ALLOY.getDefaultState());
		if (!beData.applyToBlockEntity(tempBe, serverWorld.getRegistryManager())) return;

		AlloyComposition comp = tempBe.getComposition();
		if (comp.isEmpty()) return;

		int volumeMl = tempBe.getVolumeMl();
		if (volumeMl <= 0) return;

		int itemCount = stack.getCount();
		int processed = 0;

		for (int i = 0; i < itemCount; i++) {
			if (currentVolume + volumeMl > maxVolume) break;

			// Reconstruct absolute volumes from normalized ratios
			AlloyComposition absolute = comp.toNormalized(volumeMl);

			for (Map.Entry<SmeltyMaterial, Integer> entry : absolute.getMaterials().entrySet()) {
				if (canMelt(entry.getKey())) {
					moltenAlloy.addMaterial(entry.getKey(), entry.getValue());
				} else {
					unmeltedMaterials.addMaterial(entry.getKey(), entry.getValue());
				}
			}

			processed++;
			updateCurrentVolume();
		}

		if (processed > 0) {
			serverWorld.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
					SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 2.0f + serverWorld.getRandom().nextFloat() * 0.4f);
			serverWorld.spawnParticles(ParticleTypes.FLAME,
					itemEntity.getX(), itemEntity.getY() + 0.2, itemEntity.getZ(),
					3 + processed, 0.15, 0.1, 0.15, 0.02);
			if (processed >= itemCount) {
				itemEntity.discard();
			} else {
				stack.decrement(processed);
			}
			checkSolidification();
			updateCurrentVolume();
			updateCachedColor();
			markDirty();
			needsClientSync = true;
		}
	}

	private void updateCachedColor() {
		if (!moltenAlloy.isEmpty()) {
			cachedAlloyColor = moltenAlloy.getBlendedColor();
		} else if (!unmeltedMaterials.isEmpty()) {
			cachedAlloyColor = unmeltedMaterials.getBlendedColor();
		}
	}

	// --- Valve output ---

	private static final int VALVE_FLOW_RATE_PER_TICK = 81; // 9 ingots/sec at 20 tps

	private void manageValveOutput(ServerWorld serverWorld) {
		List<ValveBlockEntity> allValves = findOpenValves(serverWorld);

		if (moltenAlloy.isEmpty()) {
			// Clear flow state on all open valves when smelter is empty
			for (ValveBlockEntity valve : allValves) {
				valve.clearFlow();
			}
			return;
		}

		for (ValveBlockEntity valve : allValves) {
			if (moltenAlloy.isEmpty()) {
				valve.clearFlow();
				continue;
			}

			int available = moltenAlloy.getTotalVolumeMl();
			int pushAmount = Math.min(VALVE_FLOW_RATE_PER_TICK, available);
			if (pushAmount <= 0) continue;

			int accepted = valve.pushFluidThrough(moltenAlloy, pushAmount);
			if (accepted > 0) {
				updateCurrentVolume();
				updateCachedColor();
				markDirty();
				needsClientSync = true;
			}
		}
	}

	/**
	 * Find open valves attached to the outside of the smelter walls.
	 * Scans one block outside each wall face.
	 */
	private List<ValveBlockEntity> findOpenValves(ServerWorld serverWorld) {
		List<ValveBlockEntity> valves = new ArrayList<>();

		for (int y = minY + 1; y < minY + height; y++) {
			// North wall (z == minZ): check z == minZ - 1
			for (int x = minX; x < minX + width; x++) {
				checkValveAt(serverWorld, new BlockPos(x, y, minZ - 1), valves);
			}
			// South wall (z == minZ + depth - 1): check z == minZ + depth
			for (int x = minX; x < minX + width; x++) {
				checkValveAt(serverWorld, new BlockPos(x, y, minZ + depth), valves);
			}
			// West wall (x == minX): check x == minX - 1
			for (int z = minZ; z < minZ + depth; z++) {
				checkValveAt(serverWorld, new BlockPos(minX - 1, y, z), valves);
			}
			// East wall (x == minX + width - 1): check x == minX + width
			for (int z = minZ; z < minZ + depth; z++) {
				checkValveAt(serverWorld, new BlockPos(minX + width, y, z), valves);
			}
		}
		return valves;
	}

	private void checkValveAt(ServerWorld world, BlockPos pos, List<ValveBlockEntity> valves) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof ValveBlock && state.get(ValveBlock.OPEN)) {
			if (world.getBlockEntity(pos) instanceof ValveBlockEntity valve) {
				valves.add(valve);
			}
		}
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
			updateCachedColor();
			markDirty();
			needsClientSync = true;
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
			this.maxVolume = interiorW * interiorD * chamberH * VOLUME_PER_INTERIOR_BLOCK;
			updateCurrentVolume();
			markDirty();
			needsClientSync = true;

			if (wasInvalid) {
				Smelty.LOGGER.info("Smelter re-validated! {}x{}x{}, heat: {}", width, depth, height, heatLevel);
				spawnFormationParticles(result);
			}
		}
	}

	public void forceRevalidate() {
		if (world instanceof ServerWorld) {
			revalidateStructure();
		}
	}

	/** Called when structure becomes invalid — all data is lost */
	private void onInvalidated(ServerWorld serverWorld) {
		this.valid = false;
		this.heatLevel = 0;
		this.maxVolume = 0;
		this.currentVolume = 0;
		this.moltenAlloy.clear();
		this.unmeltedMaterials.clear();
		this.cachedAlloyColor = 0x808080;
		markDirty();
		serverWorld.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
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

		long time = world.getTime();

		// Revalidate structure every 2 seconds
		if (time % 40 == 0) {
			be.revalidateStructure();
		}

		if (!be.valid) return;

		// Rescan heat sources every second
		if (time % 20 == 0) {
			be.rescanHeat();
		}

		// Process thrown items every 10 ticks
		if (time % 10 == 0) {
			be.processItemEntities(serverWorld);
		}

		// Push molten alloy through open valves into adjacent channels
		be.manageValveOutput(serverWorld);

		// Molten alloy effects: damage, item destruction, sounds, light
		boolean hasMolten = !be.moltenAlloy.isEmpty();
		if (hasMolten) {
			int totalMl = be.moltenAlloy.getTotalVolumeMl() + be.unmeltedMaterials.getTotalVolumeMl();
			float fillRatio = (float) totalMl / be.maxVolume;
			double fluidTopY = be.minY + 2 + fillRatio * (be.height - 2);

			Box fluidBox = new Box(
					be.minX + 1, be.minY + 2, be.minZ + 1,
					be.minX + be.width - 1, fluidTopY, be.minZ + be.depth - 1);

			// Fire damage to living entities in molten alloy
			if (time % 10 == 0) {
				RegistryEntry<DamageType> damageType = serverWorld.getRegistryManager()
						.getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(MOLTEN_ALLOY_DAMAGE);
				DamageSource moltenAlloyDamage = new DamageSource(damageType);
				for (LivingEntity entity : serverWorld.getEntitiesByClass(LivingEntity.class, fluidBox, e -> true)) {
					entity.setOnFireFor(5);
					entity.damage(serverWorld, moltenAlloyDamage, 4.0f);
				}
			}

			// Destroy non-smeltable items in molten alloy
			if (time % 10 == 0) {
				Box interiorBox = new Box(
						be.minX + 1, be.minY + 2, be.minZ + 1,
						be.minX + be.width - 1, be.minY + be.height, be.minZ + be.depth - 1);
				for (ItemEntity item : serverWorld.getEntitiesByClass(ItemEntity.class, interiorBox, e -> true)) {
					ItemStack itemStack = item.getStack();
					if (!itemStack.isOf(SmeltyBlocks.SOLID_ALLOY.asItem()) && MaterialItems.lookup(itemStack) == null) {
						serverWorld.playSound(null, item.getX(), item.getY(), item.getZ(),
								SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 2.0f + serverWorld.getRandom().nextFloat() * 0.4f);
						item.discard();
					}
				}
			}

			// Lava bubbling sounds
			if (time % 80 == 0 && serverWorld.getRandom().nextFloat() < 0.5f) {
				double soundX = be.minX + 1 + serverWorld.getRandom().nextFloat() * (be.width - 2);
				double soundZ = be.minZ + 1 + serverWorld.getRandom().nextFloat() * (be.depth - 2);
				serverWorld.playSound(null, soundX, fluidTopY, soundZ,
						SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.BLOCKS, 0.8f, 1.0f);
			}
		}

		// Update light emission
		boolean isLit = state.get(SmelterControllerBlock.LIT);
		if (isLit != hasMolten) {
			world.setBlockState(pos, state.with(SmelterControllerBlock.LIT, hasMolten), Block.NOTIFY_ALL);
		}

		// Sync render data to client
		if (be.needsClientSync) {
			be.needsClientSync = false;
			serverWorld.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		}

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

	// --- Accessors (used by renderer and GUI) ---

	public boolean isValid() { return valid; }
	public int getHeatLevel() { return heatLevel; }
	public int getMaxVolume() { return maxVolume; }
	public int getCurrentVolume() { return currentVolume; }
	public int getCachedAlloyColor() { return cachedAlloyColor; }
	public AlloyComposition getMoltenAlloy() { return moltenAlloy; }
	public AlloyComposition getUnmeltedMaterials() { return unmeltedMaterials; }
	public int getWidth() { return width; }
	public int getDepth() { return depth; }
	public int getHeight() { return height; }
	public int getMinX() { return minX; }
	public int getMinY() { return minY; }
	public int getMinZ() { return minZ; }

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

	public SmelterData buildScreenData() {
		return new SmelterData(
				heatLevel, maxVolume, currentVolume,
				new EnumMap<>(moltenAlloy.getMaterials()),
				new EnumMap<>(unmeltedMaterials.getMaterials())
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
		if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			return new SmelterControllerScreenHandler(syncId, playerInventory, buildScreenData(), this, serverPlayer);
		}
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
		// Recalculate maxVolume from dimensions to handle unit system changes
		if (valid) {
			int interiorW = width - 2;
			int interiorD = depth - 2;
			int chamberH = height - 2;
			this.maxVolume = interiorW * interiorD * chamberH * VOLUME_PER_INTERIOR_BLOCK;
		} else {
			this.maxVolume = 0;
		}
		// currentVolume is derived from compositions via updateCurrentVolume() below
		this.minX = view.getInt("MinX", 0);
		this.minY = view.getInt("MinY", 0);
		this.minZ = view.getInt("MinZ", 0);
		this.cachedAlloyColor = view.getInt("CachedAlloyColor", 0x808080);

		moltenAlloy.readFromView(view.getListReadView("MoltenAlloy"));
		unmeltedMaterials.readFromView(view.getListReadView("UnmeltedMaterials"));

		updateCurrentVolume();
		updateClientRegistry();
	}

	private void updateClientRegistry() {
		if (valid && maxVolume > 0) {
			int totalMl = moltenAlloy.getTotalVolumeMl() + unmeltedMaterials.getTotalVolumeMl();
			int color = !moltenAlloy.isEmpty() ? moltenAlloy.getBlendedColor()
					: (!unmeltedMaterials.isEmpty() ? unmeltedMaterials.getBlendedColor() : 0);
			CLIENT_SMELTERS.put(pos.toImmutable(), new FluidBoundsData(
					minX, minY, minZ, width, depth, height, maxVolume, totalMl, color));
		} else {
			CLIENT_SMELTERS.remove(pos.toImmutable());
		}
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		CLIENT_SMELTERS.remove(pos.toImmutable());
	}

	@Override
	protected void writeData(WriteView view) {
		view.putBoolean("Valid", valid);
		view.putInt("Width", width);
		view.putInt("Depth", depth);
		view.putInt("Height", height);
		view.putInt("HeatLevel", heatLevel);
		view.putInt("MinX", minX);
		view.putInt("MinY", minY);
		view.putInt("MinZ", minZ);
		view.putInt("CachedAlloyColor", cachedAlloyColor);

		moltenAlloy.writeToView(view.getList("MoltenAlloy"));
		unmeltedMaterials.writeToView(view.getList("UnmeltedMaterials"));
	}
}

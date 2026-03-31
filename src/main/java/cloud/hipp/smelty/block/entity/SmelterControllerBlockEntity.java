package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.screen.SmelterControllerScreenHandler;
import cloud.hipp.smelty.screen.SmelterData;
import cloud.hipp.smelty.structure.MultiblockValidator;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

public class SmelterControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<SmelterData> {
	private static final int ML_PER_INTERIOR_BLOCK = 2000;

	private boolean valid;
	private int width;
	private int depth;
	private int height;
	private int heatLevel;
	private int maxVolume;
	private int currentVolume;
	private int minX;
	private int minY;
	private int minZ;

	public SmelterControllerBlockEntity(BlockPos pos, BlockState state) {
		super(SmeltyBlockEntities.SMELTER_CONTROLLER, pos, state);
	}

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
			Smelty.LOGGER.info("Smelter formed! {}x{}x{}, heat: {}, max volume: {}mL", width, depth, height, heatLevel, maxVolume);
			spawnFormationParticles(result);
		} else {
			this.maxVolume = 0;
			Smelty.LOGGER.info("Smelter structure invalid");
		}

		markDirty();
	}

	private void spawnFormationParticles(MultiblockValidator.Result result) {
		if (!(world instanceof ServerWorld serverWorld)) return;

		// Spawn fire and smoke particles erupting from the open top of the smelter
		double centerX = result.minX() + result.width() / 2.0;
		double centerZ = result.minZ() + result.depth() / 2.0;
		double topY = result.minY() + result.height();

		double spreadX = (result.width() - 2) / 2.0;
		double spreadZ = (result.depth() - 2) / 2.0;

		// Big burst of flame particles shooting upward
		serverWorld.spawnParticles(ParticleTypes.FLAME,
				centerX, topY, centerZ,
				50, spreadX, 0.5, spreadZ, 0.1);

		// Large smoke billowing up
		serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
				centerX, topY + 0.5, centerZ,
				30, spreadX, 0.8, spreadZ, 0.05);

		// Lava drip particles for extra flair
		serverWorld.spawnParticles(ParticleTypes.LAVA,
				centerX, topY, centerZ,
				15, spreadX, 0.3, spreadZ, 0.0);
	}

	private void rescanHeat() {
		int totalHeat = 0;
		for (int x = minX + 1; x < minX + width - 1; x++) {
			for (int z = minZ + 1; z < minZ + depth - 1; z++) {
				totalHeat += MultiblockValidator.getHeatValue(world.getBlockState(new BlockPos(x, minY, z)));
			}
		}
		if (totalHeat != heatLevel) {
			heatLevel = totalHeat;
			markDirty();
		}
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, SmelterControllerBlockEntity be) {
		if (!be.valid) return;
		if (!(world instanceof ServerWorld serverWorld)) return;

		long time = world.getTime();

		// Rescan heat sources every second
		if (time % 20 == 0) {
			be.rescanHeat();
		}

		if (be.heatLevel <= 0) return;

		double centerX = be.minX + be.width / 2.0;
		double centerZ = be.minZ + be.depth / 2.0;
		double topY = be.minY + be.height;
		double spreadX = (be.width - 2) / 2.0;
		double spreadZ = (be.depth - 2) / 2.0;

		float heatRatio = Math.min(1.0f, be.heatLevel / 3200.0f);

		// Campfire-style smoke columns rising high — every 5 ticks
		if (time % 5 == 0) {
			int smokeCount = 2 + (int) (heatRatio * 15);
			serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
					centerX, topY, centerZ,
					smokeCount, spreadX * 0.4, 0.0, spreadZ * 0.4, 0.02);
		}

		// Regular smoke — every 5 ticks
		if (time % 5 == 0) {
			int smokeCount = 1 + (int) (heatRatio * 10);
			serverWorld.spawnParticles(ParticleTypes.SMOKE,
					centerX, topY + 0.5, centerZ,
					smokeCount, spreadX * 0.5, 0.3, spreadZ * 0.5, 0.05);
		}

		// Flames erupting from the top — every 10 ticks
		if (time % 10 == 0) {
			int flameCount = 2 + (int) (heatRatio * 20);
			serverWorld.spawnParticles(ParticleTypes.FLAME,
					centerX, topY, centerZ,
					flameCount, spreadX * 0.5, 0.2, spreadZ * 0.5, 0.08);
		}

		// Large smoke billowing upward — every 15 ticks
		if (time % 15 == 0) {
			int bigSmokeCount = 1 + (int) (heatRatio * 10);
			serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
					centerX, topY + 1.0, centerZ,
					bigSmokeCount, spreadX * 0.5, 0.5, spreadZ * 0.5, 0.04);
		}

		// Lava drip particles at high heat — every 20 ticks
		if (heatRatio > 0.3f && time % 20 == 0) {
			int lavaCount = 1 + (int) (heatRatio * 8);
			serverWorld.spawnParticles(ParticleTypes.LAVA,
					centerX, topY, centerZ,
					lavaCount, spreadX * 0.4, 0.1, spreadZ * 0.4, 0.0);
		}
	}

	public boolean isValid() { return valid; }
	public int getHeatLevel() { return heatLevel; }
	public int getMaxVolume() { return maxVolume; }
	public int getCurrentVolume() { return currentVolume; }

	@Override
	public SmelterData getScreenOpeningData(ServerPlayerEntity player) {
		return new SmelterData(heatLevel, maxVolume, currentVolume);
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.smelty.smelter_controller");
	}

	@Override
	public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new SmelterControllerScreenHandler(syncId, playerInventory, new SmelterData(heatLevel, maxVolume, currentVolume));
	}

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
	}
}

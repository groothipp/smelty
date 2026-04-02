package cloud.hipp.smelty.structure;

import cloud.hipp.smelty.block.SmelterBlock;
import cloud.hipp.smelty.block.SmelterControllerBlock;
import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.block.SolidAlloyBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MultiblockValidator {
	public static final int MIN_SIZE = 3;
	public static final int MAX_SIZE = 10;
	public static final int MAX_HEIGHT = 9;

	public record Result(boolean valid, int width, int depth, int height, int heatLevel, int minX, int minY, int minZ) {
		public static final Result INVALID = new Result(false, 0, 0, 0, 0, 0, 0, 0);
	}

	/**
	 * Validates a multiblock smelter structure from the controller position.
	 * Structure: Layer 0 = solid floor, Layer 1 = frame + heat sources, Layer 2+ = walls + air interior.
	 * Controller must be on a wall at layer 2+.
	 */
	public static Result validate(World world, BlockPos controllerPos) {
		for (int w = MIN_SIZE; w <= MAX_SIZE; w++) {
			for (int d = MIN_SIZE; d <= MAX_SIZE; d++) {
				for (int h = MIN_SIZE; h <= MAX_HEIGHT; h++) {
					Result result = tryAllPositions(world, controllerPos, w, d, h);
					if (result.valid()) {
						return result;
					}
				}
			}
		}
		return Result.INVALID;
	}

	private static Result tryAllPositions(World world, BlockPos controllerPos, int w, int d, int h) {
		int cx = controllerPos.getX();
		int cy = controllerPos.getY();
		int cz = controllerPos.getZ();

		// Controller must be at y offset 2..h-1 from the bottom (layer 2+, 0-indexed)
		for (int yOff = 2; yOff < h; yOff++) {
			int minY = cy - yOff;

			// Try all origins where controller is on the perimeter
			for (int minX = cx - (w - 1); minX <= cx; minX++) {
				for (int minZ = cz - (d - 1); minZ <= cz; minZ++) {
					// Check controller is on the perimeter (not interior, not corner)
					int relX = cx - minX;
					int relZ = cz - minZ;
					if (!isOnPerimeter(relX, relZ, w, d)) {
						continue;
					}

					Result result = validateStructure(world, controllerPos, minX, minY, minZ, w, d, h);
					if (result.valid()) {
						return result;
					}
				}
			}
		}
		return Result.INVALID;
	}

	private static boolean isOnPerimeter(int relX, int relZ, int w, int d) {
		return relX == 0 || relX == w - 1 || relZ == 0 || relZ == d - 1;
	}

	private static boolean isSmelterBlock(BlockState state) {
		return state.getBlock() instanceof SmelterBlock;
	}

	private static boolean isSmelterOrController(BlockState state) {
		return state.getBlock() instanceof SmelterBlock || state.getBlock() instanceof SmelterControllerBlock;
	}

	public static int getHeatValue(BlockState state) {
		if (state.getBlock() instanceof CampfireBlock) return 10;
		if (state.isOf(Blocks.LAVA)) return 50;
		return 0;
	}

	private static boolean isAlloyBlock(BlockState state) {
		return state.getBlock() instanceof SolidAlloyBlock;
	}

	private static boolean isHeatSourceOrAir(BlockState state) {
		return state.isAir() || state.getBlock() instanceof CampfireBlock || state.isOf(Blocks.LAVA);
	}

	private static Result validateStructure(World world, BlockPos controllerPos, int minX, int minY, int minZ, int w, int d, int h) {
		int totalHeat = 0;
		boolean foundController = false;

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
					BlockState state = world.getBlockState(pos);
					boolean perimeter = isOnPerimeter(x, z, w, d);

					if (y == 0) {
						// Layer 0: not validated, just scan interior for heat sources
						if (!perimeter) {
							totalHeat += getHeatValue(state);
						}
					} else if (y == 1) {
						// Layer 1: solid cover — all smelter blocks
						if (!isSmelterBlock(state)) return Result.INVALID;
					} else {
						// Layer 2+: perimeter is smelter/controller, interior is air (open top)
						if (perimeter) {
							boolean isCorner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
							if (pos.equals(controllerPos)) {
								foundController = true;
							} else if (!isSmelterBlock(state)) {
								return Result.INVALID;
							}
						} else {
							if (!state.isAir() && !isAlloyBlock(state)) return Result.INVALID;
						}
					}
				}
			}
		}

		if (!foundController) return Result.INVALID;

		return new Result(true, w, d, h, totalHeat, minX, minY, minZ);
	}
}

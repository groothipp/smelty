package cloud.hipp.smelty.structure;

import cloud.hipp.smelty.block.ModBlocks;
import cloud.hipp.smelty.tag.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Validates whether a 3x3x3 smelter multiblock structure is correctly built.
 *
 * The structure looks like this (viewed from the side with the gap):
 *
 *   TOP (y+2):     MIDDLE (y+1):    BOTTOM (y+0):
 *   S S S          S S S            S S S
 *   S · S          S · S            S H _  ← gap on one side
 *   S S S          S S S            S S S
 *
 * Where:
 *   S = any block in the smelty:smelter_wall tag
 *   H = campfire (unlit when building, lit when active)
 *   · = air (hollow interior)
 *   _ = air (the output gap, must be center of one side)
 *
 * The heat source position is the reference point. Everything is checked relative to it.
 */
public class SmelterValidator {

    /**
     * The result of a structure validation check.
     * If valid, contains the direction of the output gap.
     */
    public record ValidationResult(boolean valid, @Nullable Direction gapDirection) {
        public static final ValidationResult INVALID = new ValidationResult(false, null);
    }

    /**
     * Validates the smelter structure around a heat source position.
     * Accepts campfires regardless of lit state — used for initial activation.
     */
    public static ValidationResult validate(World world, BlockPos heatPos) {
        // ── Step 1: Verify the heat source itself (campfire, lit or unlit) ──
        if (!isHeatSource(world, heatPos)) {
            return ValidationResult.INVALID;
        }

        // ── Step 2: Check the bottom layer perimeter ──────────────────
        BlockPos[] corners = {
                heatPos.add(-1, 0, -1),
                heatPos.add(-1, 0,  1),
                heatPos.add( 1, 0, -1),
                heatPos.add( 1, 0,  1)
        };

        for (BlockPos corner : corners) {
            if (!isWall(world, corner)) {
                return ValidationResult.INVALID;
            }
        }

        // Of the 4 side-centers, exactly 1 must be air (the gap), 3 must be walls
        Direction gapDirection = null;
        int gapCount = 0;

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos sidePos = heatPos.offset(dir);
            BlockState state = world.getBlockState(sidePos);

            if (state.isAir()) {
                gapDirection = dir;
                gapCount++;
            } else if (!isWall(world, sidePos)) {
                return ValidationResult.INVALID;
            }
        }

        if (gapCount != 1) {
            return ValidationResult.INVALID;
        }

        // ── Step 3: Check middle and top layers ───────────────────────
        for (int yOffset = 1; yOffset <= 2; yOffset++) {
            BlockPos center = heatPos.up(yOffset);

            // Center must be air OR our smelter core (which we place at y+1)
            BlockState centerState = world.getBlockState(center);
            if (!centerState.isAir() && !centerState.isOf(ModBlocks.SMELTER_CORE)) {
                return ValidationResult.INVALID;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    BlockPos pos = center.add(dx, 0, dz);
                    if (!isWall(world, pos)) {
                        return ValidationResult.INVALID;
                    }
                }
            }
        }

        // ── Step 4: Check that the top is open ──
        if (!world.getBlockState(heatPos.up(3)).isAir()) {
            return ValidationResult.INVALID;
        }

        return new ValidationResult(true, gapDirection);
    }

    /**
     * Validates the structure AND checks the campfire is lit.
     * Used for ongoing validation — if the campfire goes out, the smelter deactivates.
     */
    public static ValidationResult validateActive(World world, BlockPos heatPos) {
        ValidationResult result = validate(world, heatPos);
        if (!result.valid()) {
            return ValidationResult.INVALID;
        }

        // For an active smelter, the campfire must be lit
        BlockState state = world.getBlockState(heatPos);
        if (state.contains(CampfireBlock.LIT) && !state.get(CampfireBlock.LIT)) {
            return ValidationResult.INVALID;
        }

        return result;
    }

    private static boolean isWall(World world, BlockPos pos) {
        return world.getBlockState(pos).isIn(ModTags.Blocks.SMELTER_WALL);
    }

    /**
     * Checks if a block is a campfire (lit or unlit).
     * Used for initial activation — the flint & steel will light it.
     */
    public static boolean isHeatSource(World world, BlockPos pos) {
        return world.getBlockState(pos).isIn(ModTags.Blocks.HEAT_SOURCE);
    }
}

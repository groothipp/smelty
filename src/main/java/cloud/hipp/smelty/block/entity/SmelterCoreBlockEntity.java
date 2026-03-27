package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.structure.SmelterValidator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * The Smelter Core Block Entity — the "brain" of each active smelter.
 *
 * Each smelter in the world gets its own instance of this class.
 * It stores:
 *   - Which direction the output gap faces
 *   - The position of the heat source
 *   - (Later: smelting progress, what's being smelted)
 *
 * It ticks 20 times per second on the server and:
 *   - Periodically validates the structure is still intact
 *   - (Later: detects ore items, progresses smelting, ejects output)
 *
 * DATA PERSISTENCE (ReadView/WriteView):
 *   When the world saves, writeData() is called — we write our fields.
 *   When the world loads, readData() is called — we read them back.
 *   This is how the smelter "remembers" its state between play sessions.
 */
public class SmelterCoreBlockEntity extends BlockEntity {

    /** Which direction the output gap faces (for ejecting items). */
    @Nullable
    private Direction gapDirection;

    /** Position of the heat source (center of bottom layer). */
    @Nullable
    private BlockPos heatPos;

    /** Counter for periodic structure checks — we don't check every tick. */
    private int validationTimer = 0;
    private static final int VALIDATION_INTERVAL = 40; // ticks (2 seconds)

    public SmelterCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER_CORE, pos, state);
    }

    // ── Initialization ────────────────────────────────────────────────

    /**
     * Called manually after the core block is placed to set up initial state.
     * markDirty() tells Minecraft "save this block entity, its data changed."
     */
    public void initialize(Direction gapDirection, BlockPos heatPos) {
        this.gapDirection = gapDirection;
        this.heatPos = heatPos;
        markDirty();
    }

    // ── Ticking ───────────────────────────────────────────────────────

    /**
     * Called every game tick (20x/sec) on the server.
     *
     * Static method pattern: Minecraft uses this for performance — avoids
     * virtual dispatch overhead when ticking thousands of block entities.
     */
    public static void tick(World world, BlockPos pos, BlockState state, SmelterCoreBlockEntity entity) {
        if (entity.heatPos == null || entity.gapDirection == null) {
            return;
        }

        // Validate structure every 2 seconds
        entity.validationTimer++;
        if (entity.validationTimer >= VALIDATION_INTERVAL) {
            entity.validationTimer = 0;

            SmelterValidator.ValidationResult result = SmelterValidator.validateActive(world, entity.heatPos);

            if (!result.valid()) {
                Smelty.LOGGER.info("Smelter structure broken at {}, deactivating", pos);
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
                return;
            }

            entity.gapDirection = result.gapDirection();
        }

        // ── Smelting Logic (TODO — next step!) ───────────────────────
        // Will: detect ore items, absorb them, progress smelting, eject output
    }

    // ── Data Persistence ──────────────────────────────────────────────
    // In 1.21.11, block entities use ReadView/WriteView instead of raw NBT.
    // WriteView has methods like putString(), putInt() for writing.
    // ReadView has getString(key, default), getInt(key, default) for reading.

    @Override
    protected void writeData(WriteView data) {
        super.writeData(data);

        if (gapDirection != null) {
            // Direction.getId() returns "north", "south", "east", "west"
            data.putString("GapDirection", gapDirection.getId());
        }
        if (heatPos != null) {
            data.putInt("HeatX", heatPos.getX());
            data.putInt("HeatY", heatPos.getY());
            data.putInt("HeatZ", heatPos.getZ());
        }
    }

    @Override
    protected void readData(ReadView data) {
        super.readData(data);

        // getOptionalString returns Optional<String> — if the key doesn't exist, it's empty
        data.getOptionalString("GapDirection").ifPresent(name -> {
            gapDirection = Direction.byId(name);
        });

        // getOptionalInt returns Optional<Integer>
        data.getOptionalInt("HeatX").ifPresent(x -> {
            int y = data.getInt("HeatY", 0);
            int z = data.getInt("HeatZ", 0);
            heatPos = new BlockPos(x, y, z);
        });
    }

    // ── Getters ───────────────────────────────────────────────────────

    @Nullable
    public Direction getGapDirection() {
        return gapDirection;
    }

    @Nullable
    public BlockPos getHeatPos() {
        return heatPos;
    }
}

package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.item.HeatedIngotItem;
import cloud.hipp.smelty.recipe.SmelterRecipes;
import cloud.hipp.smelty.structure.SmelterValidator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Smelter Core Block Entity — the "brain" of each active smelter.
 *
 * Processing flow:
 *   1. Scan for item entities falling through the interior column
 *   2. If we find a valid ore/ingot and we're not already processing: absorb it
 *   3. Count down the processing timer (with particles!)
 *   4. When done: spawn heated ingot(s) at the output gap
 *   5. Roll for bonus ingot if the input was ore
 *
 * State machine:
 *   IDLE → item detected → PROCESSING → timer done → eject output → IDLE
 */
public class SmelterCoreBlockEntity extends BlockEntity {

    // ── Configuration ─────────────────────────────────────────────────
    private static final int VALIDATION_INTERVAL = 40;   // ticks between structure checks (2 sec)
    private static final int PROCESSING_TIME = 80;        // ticks to smelt one item (4 seconds)
    private static final int ITEM_SCAN_INTERVAL = 5;      // ticks between item scans (4x/sec)

    // ── State ─────────────────────────────────────────────────────────
    @Nullable private Direction gapDirection;
    @Nullable private BlockPos heatPos;

    // Smelting state
    private int processingTimer = 0;          // counts UP to PROCESSING_TIME
    @Nullable private Item currentInput = null; // what we're currently smelting (null = idle)

    // Timers
    private int validationTimer = 0;
    private int itemScanTimer = 0;

    public SmelterCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER_CORE, pos, state);
    }

    // ── Initialization ────────────────────────────────────────────────

    public void initialize(Direction gapDirection, BlockPos heatPos) {
        this.gapDirection = gapDirection;
        this.heatPos = heatPos;
        markDirty();
    }

    // ── Ticking ───────────────────────────────────────────────────────

    public static void tick(World world, BlockPos pos, BlockState state, SmelterCoreBlockEntity entity) {
        if (entity.heatPos == null || entity.gapDirection == null) return;

        // ── Structure Validation (every 2 seconds) ───────────────────
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

        // ── Item Scanning (when idle) ────────────────────────────────
        if (entity.currentInput == null) {
            entity.itemScanTimer++;
            if (entity.itemScanTimer >= ITEM_SCAN_INTERVAL) {
                entity.itemScanTimer = 0;
                entity.tryAbsorbItem(world);
            }
            return; // Don't process if we haven't absorbed anything
        }

        // ── Processing (when smelting) ───────────────────────────────
        entity.processingTimer++;

        // Spawn processing particles — smoke rising from the interior
        if (world instanceof ServerWorld serverWorld && entity.processingTimer % 10 == 0) {
            serverWorld.spawnParticles(
                    ParticleTypes.SMOKE,
                    entity.heatPos.getX() + 0.5,
                    entity.heatPos.getY() + 2.5, // rises out the top
                    entity.heatPos.getZ() + 0.5,
                    3, 0.2, 0.2, 0.2, 0.01
            );
            serverWorld.spawnParticles(
                    ParticleTypes.FLAME,
                    entity.heatPos.getX() + 0.5,
                    entity.heatPos.getY() + 0.8, // near the campfire
                    entity.heatPos.getZ() + 0.5,
                    2, 0.15, 0.1, 0.15, 0.005
            );
        }

        // ── Eject output when done ───────────────────────────────────
        if (entity.processingTimer >= PROCESSING_TIME) {
            entity.ejectOutput(world);
            entity.processingTimer = 0;
            entity.currentInput = null;
            entity.markDirty();
        }
    }

    /**
     * Scans the interior column for item entities and absorbs valid inputs.
     *
     * We create a "search box" (AABB) covering the interior of the smelter —
     * from the campfire level up through the opening at the top.
     * Then we ask the world for all ItemEntity objects inside that box.
     *
     * getEntitiesByClass() is Minecraft's spatial query — it uses the chunk system
     * to efficiently find entities in a region without scanning the entire world.
     */
    private void tryAbsorbItem(World world) {
        // Search box: the 1x3x1 interior column
        Box searchBox = new Box(
                heatPos.getX(),     heatPos.getY(),     heatPos.getZ(),
                heatPos.getX() + 1, heatPos.getY() + 3, heatPos.getZ() + 1
        );

        // Find all item entities in the column
        List<ItemEntity> items = world.getEntitiesByClass(
                ItemEntity.class,
                searchBox,
                itemEntity -> SmelterRecipes.canSmelt(itemEntity.getStack().getItem())
        );

        if (items.isEmpty()) return;

        // Grab the first valid item
        ItemEntity itemEntity = items.getFirst();
        ItemStack stack = itemEntity.getStack();

        // Remember what we're smelting
        currentInput = stack.getItem();

        // Consume one item from the stack
        if (stack.getCount() > 1) {
            stack.decrement(1);
        } else {
            itemEntity.discard(); // remove the entity entirely
        }

        // Play absorption sound — item sizzles as it hits the heat
        world.playSound(null, heatPos, SoundEvents.BLOCK_LAVA_EXTINGUISH,
                SoundCategory.BLOCKS, 0.5f, 1.5f);

        processingTimer = 0;
        markDirty();

        Smelty.LOGGER.info("Smelter absorbing: {}", currentInput);
    }

    /**
     * Spawns the output item(s) at the gap position.
     *
     * The gap is where the output "pours" out — we spawn an ItemEntity
     * at the gap position with a slight velocity pushing it outward.
     */
    private void ejectOutput(World world) {
        if (currentInput == null || gapDirection == null || heatPos == null) return;

        SmelterRecipes.SmelterRecipe recipe = SmelterRecipes.getRecipe(currentInput);
        if (recipe == null) return;

        // Calculate gap position — one block out from the heat source in the gap direction
        BlockPos gapPos = heatPos.offset(gapDirection);

        // Spawn position: center of the gap block, at ground level
        double x = gapPos.getX() + 0.5;
        double y = gapPos.getY() + 0.25;
        double z = gapPos.getZ() + 0.5;

        // Small velocity pushing the item out of the gap
        double vx = gapDirection.getOffsetX() * 0.15;
        double vz = gapDirection.getOffsetZ() * 0.15;

        // ── Guaranteed output: 1 heated ingot ─────────────────────────
        // Damage starts at 0 = freshly heated (full durability bar)
        ItemStack output = new ItemStack(recipe.output(), 1);

        ItemEntity outputEntity = new ItemEntity(world, x, y, z, output, vx, 0.1, vz);
        world.spawnEntity(outputEntity);

        // ── Bonus roll: chance for a second ingot! ────────────────────
        if (recipe.bonusChance() > 0 && world.getRandom().nextFloat() < recipe.bonusChance()) {
            ItemStack bonus = new ItemStack(recipe.output(), 1);

            // Slightly offset so the two items don't merge
            ItemEntity bonusEntity = new ItemEntity(world, x, y + 0.2, z, bonus, vx * 0.8, 0.15, vz * 0.8);

            Smelty.LOGGER.info("Bonus ingot! Lucky smelt from {}", currentInput);
        }

        // ── Effects ───────────────────────────────────────────────────
        // Play a satisfying "item pops out" sound
        world.playSound(null, gapPos, SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.BLOCKS, 0.8f, 0.7f);

        // Flame particles at the output
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.FLAME,
                    x, y + 0.3, z,
                    8, 0.1, 0.1, 0.1, 0.02
            );
        }
    }

    // ── Data Persistence ──────────────────────────────────────────────

    @Override
    protected void writeData(WriteView data) {
        super.writeData(data);

        if (gapDirection != null) {
            data.putString("GapDirection", gapDirection.getId());
        }
        if (heatPos != null) {
            data.putInt("HeatX", heatPos.getX());
            data.putInt("HeatY", heatPos.getY());
            data.putInt("HeatZ", heatPos.getZ());
        }

        // Save smelting progress so it survives chunk unloads / server restarts
        data.putInt("ProcessingTimer", processingTimer);
        if (currentInput != null) {
            // Save the item's registry name (e.g., "minecraft:raw_iron")
            var key = net.minecraft.registry.Registries.ITEM.getId(currentInput);
            if (key != null) {
                data.putString("CurrentInput", key.toString());
            }
        }
    }

    @Override
    protected void readData(ReadView data) {
        super.readData(data);

        data.getOptionalString("GapDirection").ifPresent(name -> {
            gapDirection = Direction.byId(name);
        });

        data.getOptionalInt("HeatX").ifPresent(x -> {
            int y = data.getInt("HeatY", 0);
            int z = data.getInt("HeatZ", 0);
            heatPos = new BlockPos(x, y, z);
        });

        processingTimer = data.getInt("ProcessingTimer", 0);
        data.getOptionalString("CurrentInput").ifPresent(id -> {
            var identifier = net.minecraft.util.Identifier.tryParse(id);
            if (identifier != null) {
                currentInput = net.minecraft.registry.Registries.ITEM.get(identifier);
            }
        });
    }

    // ── Getters ───────────────────────────────────────────────────────

    @Nullable public Direction getGapDirection() { return gapDirection; }
    @Nullable public BlockPos getHeatPos() { return heatPos; }
    public boolean isProcessing() { return currentInput != null; }
}

package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.CraftingAnvilBlockEntity;
import cloud.hipp.smelty.block.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * The Crafting Anvil — a custom anvil for forging tools from heated ingots.
 *
 * This block:
 *   - Looks like a vanilla anvil (uses the same model)
 *   - Has a FACING property (rotates when placed, like vanilla anvil)
 *   - Has a block entity to store placed items (sticks + heated ingots)
 *   - Overrides right-click to place/remove items instead of opening repair GUI
 *
 * We extend BlockWithEntity (for block entity support) and add the FACING property
 * manually (since we can't extend both BlockWithEntity and HorizontalFacingBlock).
 */
public class CraftingAnvilBlock extends BlockWithEntity {

    public static final MapCodec<CraftingAnvilBlock> CODEC = createCodec(CraftingAnvilBlock::new);

    /** The direction the anvil faces — determines its visual rotation. */
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    public CraftingAnvilBlock(Settings settings) {
        super(settings);
        // Set default state: facing south
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    // ── Block Entity ──────────────────────────────────────────────────

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingAnvilBlockEntity(pos, state);
    }

    // ── Rendering ─────────────────────────────────────────────────────

    /**
     * MODEL = render using the blockstate/model JSON files.
     * We override BlockWithEntity's default of INVISIBLE because
     * we DO want this block to be visible (it looks like an anvil!).
     */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // ── Placement ─────────────────────────────────────────────────────

    /**
     * When the player places this block, face it perpendicular to the player.
     * Same behavior as vanilla anvil.
     */
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().rotateYClockwise());
    }

    /**
     * Register the FACING property in the block's state manager.
     * Without this, Minecraft doesn't know our block has a facing property
     * and the blockstate JSON won't work.
     */
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // ── Collision Shape ───────────────────────────────────────────────
    // We use the same collision shape as vanilla anvil.
    // Anvils have a distinctive shape — thinner in the middle.

    // For simplicity, we'll just use the full block shape.
    // TODO: Copy the exact anvil VoxelShapes for proper collision.

    // ── Right-Click Interaction ───────────────────────────────────────

    /**
     * Called when the player right-clicks with an item in hand.
     * This fires BEFORE onUse (empty hand).
     *
     * We handle:
     *   - Sticks → add to left side
     *   - Heated ingots → add to right side
     *
     * Returns:
     *   SUCCESS → handled, stop processing (arm swings)
     *   PASS_TO_DEFAULT_BLOCK_ACTION → not our item, let default behavior happen
     */
    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS; // Client: just swing arm
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof CraftingAnvilBlockEntity anvil) {
            if (anvil.tryAddItem(player, stack, world)) {
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
    }

    /**
     * Called when the player right-clicks with an empty hand.
     * We use this to remove the last placed item.
     */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof CraftingAnvilBlockEntity anvil) {
            if (anvil.tryRemoveItem(player, world)) {
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    // ── Drop contents when broken ─────────────────────────────────────

    /**
     * Called when the block is removed (broken, exploded, etc.).
     * We drop any items that were placed on the anvil.
     */
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof CraftingAnvilBlockEntity anvil) {
            anvil.dropContents(world, pos);
        }
        super.onStateReplaced(state, world, pos, moved);
    }
}

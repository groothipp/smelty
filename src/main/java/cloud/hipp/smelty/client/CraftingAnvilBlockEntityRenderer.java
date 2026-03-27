package cloud.hipp.smelty.client;

import cloud.hipp.smelty.block.CraftingAnvilBlock;
import cloud.hipp.smelty.block.entity.CraftingAnvilBlockEntity;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Renders sticks and materials on top of the Crafting Anvil.
 *
 * In Minecraft's rendering pipeline:
 *   1. createRenderState() — creates our state object (once)
 *   2. updateRenderState() — copies block entity data to the state (each frame)
 *   3. render() — draws the items using the state data (each frame)
 *
 * Items are rendered as flat sprites lying on the anvil surface:
 *   - Sticks on the left side
 *   - Materials (ingots, diamonds, etc.) on the right side
 *   - Stacked slightly upward for each additional item
 */
public class CraftingAnvilBlockEntityRenderer implements BlockEntityRenderer<CraftingAnvilBlockEntity, CraftingAnvilRenderState> {

    private final ItemModelManager itemModelManager;

    public CraftingAnvilBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.itemModelManager = ctx.itemModelManager();
    }

    @Override
    public CraftingAnvilRenderState createRenderState() {
        return new CraftingAnvilRenderState();
    }

    /**
     * Copies data from the block entity to the render state.
     * This runs on the render thread and bridges server data → client rendering.
     */
    @Override
    public void updateRenderState(CraftingAnvilBlockEntity entity, CraftingAnvilRenderState state, float tickDelta,
                                   Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
        // Copy block entity data to render state
        state.facing = entity.getCachedState().get(CraftingAnvilBlock.FACING);
        state.stickCount = entity.getStickCount();
        state.materialCount = entity.getMaterialCount();

        World world = entity.getWorld();

        // Prepare stick render model
        if (state.stickCount > 0 && world != null) {
            ItemStack stickStack = new ItemStack(Items.STICK);
            itemModelManager.update(state.stickRenderState, stickStack,
                    ItemDisplayContext.FIXED, world, null, 0);
        } else {
            state.stickRenderState.clear();
        }

        // Prepare material render model
        if (state.materialCount > 0 && entity.getMaterialType() != null && world != null) {
            ItemStack materialStack = new ItemStack(entity.getMaterialType());
            itemModelManager.update(state.materialRenderState, materialStack,
                    ItemDisplayContext.FIXED, world, null, 0);
        } else {
            state.materialRenderState.clear();
        }
    }

    /**
     * Renders the items on the anvil.
     *
     * The anvil top surface is at roughly y=0.625 (10/16 blocks).
     * We render items as FIXED display (like in item frames) lying flat.
     *
     * Positioning depends on the anvil's facing direction:
     *   - The anvil model is elongated along one axis
     *   - "Left" and "right" are relative to that orientation
     */
    @Override
    public void render(CraftingAnvilRenderState state, MatrixStack matrices,
                       OrderedRenderCommandQueue renderQueue, CameraRenderState camera) {

        int light = state.lightmapCoordinates;

        // The anvil faces perpendicular to the player when placed
        Direction facing = state.facing;
        float rotation = facing.getPositiveHorizontalDegrees();

        // ── Render sticks (left side) ─────────────────────────────────
        if (state.stickCount > 0 && !state.stickRenderState.isEmpty()) {
            for (int i = 0; i < state.stickCount; i++) {
                matrices.push();

                // Move to anvil top center
                matrices.translate(0.5, 0.69, 0.5);

                // Rotate to match anvil orientation
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotation));

                // Offset to left side + stack upward
                matrices.translate(-0.15, i * 0.03, 0.0);

                // Lay flat on the surface
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));

                // Scale down
                matrices.scale(0.35f, 0.35f, 0.35f);

                state.stickRenderState.render(matrices, renderQueue, light, 0xFFFFFF, 0);

                matrices.pop();
            }
        }

        // ── Render materials (right side) ─────────────────────────────
        if (state.materialCount > 0 && !state.materialRenderState.isEmpty()) {
            for (int i = 0; i < state.materialCount; i++) {
                matrices.push();

                // Move to anvil top center
                matrices.translate(0.5, 0.69, 0.5);

                // Rotate to match anvil orientation
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotation));

                // Offset to right side + stack upward
                matrices.translate(0.15, i * 0.03, 0.0);

                // Lay flat on the surface
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));

                // Scale down
                matrices.scale(0.35f, 0.35f, 0.35f);

                state.materialRenderState.render(matrices, renderQueue, light, 0xFFFFFF, 0);

                matrices.pop();
            }
        }
    }
}

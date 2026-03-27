package cloud.hipp.smelty.client;

import cloud.hipp.smelty.block.CraftingAnvilBlock;
import cloud.hipp.smelty.block.entity.CraftingAnvilBlockEntity;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
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
 * Items are rendered as flat sprites lying on the anvil surface:
 *   - Sticks on the left side
 *   - Materials on the right side
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

    @Override
    public void updateRenderState(CraftingAnvilBlockEntity entity, CraftingAnvilRenderState state, float tickDelta,
                                   Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
        // Call the super to populate base fields like lightmapCoordinates and blockState
        CraftingAnvilRenderState.updateBlockEntityRenderState(entity, state, crumbling);

        state.facing = entity.getCachedState().get(CraftingAnvilBlock.FACING);
        state.stickCount = entity.getStickCount();
        state.materialCount = entity.getMaterialCount();

        World world = entity.getWorld();

        // Copy rotations from block entity
        state.stickRotations.clear();
        state.stickRotations.addAll(entity.getStickRotations());
        state.materialRotations.clear();
        state.materialRotations.addAll(entity.getMaterialRotations());

        // Build stick render states — create fresh ones each frame like campfire does
        state.stickRenderStates.clear();
        if (state.stickCount > 0 && world != null) {
            ItemStack stickStack = new ItemStack(Items.STICK);
            for (int i = 0; i < state.stickCount; i++) {
                ItemRenderState renderState = new ItemRenderState();
                itemModelManager.clearAndUpdate(renderState, stickStack,
                        ItemDisplayContext.FIXED, world, null, i);
                state.stickRenderStates.add(renderState);
            }
        }

        // Build material render states
        state.materialRenderStates.clear();
        if (state.materialCount > 0 && entity.getMaterialType() != null && world != null) {
            ItemStack materialStack = new ItemStack(entity.getMaterialType());
            for (int i = 0; i < state.materialCount; i++) {
                ItemRenderState renderState = new ItemRenderState();
                itemModelManager.clearAndUpdate(renderState, materialStack,
                        ItemDisplayContext.FIXED, world, null, i + 100);
                state.materialRenderStates.add(renderState);
            }
        }
    }

    @Override
    public void render(CraftingAnvilRenderState state, MatrixStack matrices,
                       OrderedRenderCommandQueue renderQueue, CameraRenderState camera) {

        int light = state.lightmapCoordinates;
        Direction facing = state.facing;
        float rotation = facing.getPositiveHorizontalDegrees();

        // ── Render sticks (left side) ─────────────────────────────────
        for (int i = 0; i < state.stickRenderStates.size(); i++) {
            ItemRenderState itemState = state.stickRenderStates.get(i);
            if (itemState.isEmpty()) continue;

            matrices.push();
            matrices.translate(0.5, 1.01, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotation));
            matrices.translate(0.0, i * 0.03, -0.25);
            // Apply the stored random rotation for this item
            if (i < state.stickRotations.size()) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.stickRotations.get(i)));
            }
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.scale(0.3f, 0.3f, 0.3f);

            itemState.render(matrices, renderQueue, light, OverlayTexture.DEFAULT_UV, 0);

            matrices.pop();
        }

        // ── Render materials (right side) ─────────────────────────────
        for (int i = 0; i < state.materialRenderStates.size(); i++) {
            ItemRenderState itemState = state.materialRenderStates.get(i);
            if (itemState.isEmpty()) continue;

            matrices.push();
            matrices.translate(0.5, 1.01, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotation));
            matrices.translate(0.0, i * 0.03, 0.25);
            if (i < state.materialRotations.size()) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.materialRotations.get(i)));
            }
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.scale(0.3f, 0.3f, 0.3f);

            itemState.render(matrices, renderQueue, light, OverlayTexture.DEFAULT_UV, 0);

            matrices.pop();
        }
    }
}

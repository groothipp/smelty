package cloud.hipp.smelty.client;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.util.math.Direction;

/**
 * Render state for the Crafting Anvil.
 *
 * This is a snapshot of the block entity's data that the renderer uses.
 * It's populated on the render thread via updateRenderState() and read
 * in render(). This avoids threading issues between the server/client.
 */
public class CraftingAnvilRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.SOUTH;
    public int stickCount = 0;
    public int materialCount = 0;

    // Pre-computed item render states — the renderer fills these in updateRenderState
    public final ItemRenderState stickRenderState = new ItemRenderState();
    public final ItemRenderState materialRenderState = new ItemRenderState();
}

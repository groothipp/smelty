package cloud.hipp.smelty.client;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Render state for the Crafting Anvil.
 * Populated on the render thread, read in render().
 */
public class CraftingAnvilRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.SOUTH;
    public int stickCount = 0;
    public int materialCount = 0;

    public final List<ItemRenderState> stickRenderStates = new ArrayList<>();
    public final List<ItemRenderState> materialRenderStates = new ArrayList<>();
    public final List<Float> stickRotations = new ArrayList<>();
    public final List<Float> materialRotations = new ArrayList<>();
}

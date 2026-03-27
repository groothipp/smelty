package cloud.hipp.smelty;

import cloud.hipp.smelty.block.entity.ModBlockEntities;
import cloud.hipp.smelty.client.CraftingAnvilBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

/**
 * Client-side mod initializer.
 *
 * This runs ONLY on the client (not the dedicated server).
 * We use it to register renderers, client-side event handlers,
 * and other visual-only features.
 */
public class SmeltyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register the crafting anvil renderer so items show on top
        BlockEntityRendererRegistry.register(
                ModBlockEntities.CRAFTING_ANVIL,
                CraftingAnvilBlockEntityRenderer::new
        );

        Smelty.LOGGER.info("Smelty client initialized!");
    }
}

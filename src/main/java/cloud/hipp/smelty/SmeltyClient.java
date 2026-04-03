package cloud.hipp.smelty;

import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
import cloud.hipp.smelty.block.entity.SolidAlloyBlockEntity;
import cloud.hipp.smelty.client.MoltenAlloyOverlay;
import cloud.hipp.smelty.client.render.AnalysisBenchBlockEntityRenderer;
import cloud.hipp.smelty.client.render.CastingBasinBlockEntityRenderer;
import cloud.hipp.smelty.client.render.CastingTableBlockEntityRenderer;
import cloud.hipp.smelty.client.render.ChannelBlockEntityRenderer;
import cloud.hipp.smelty.client.render.SmelterControllerBlockEntityRenderer;
import cloud.hipp.smelty.client.render.ValveBlockEntityRenderer;
import cloud.hipp.smelty.material.ClientAlloyRegistry;
import cloud.hipp.smelty.network.SyncAlloyRegistryPayload;
import cloud.hipp.smelty.network.SyncSmelterDataPayload;
import cloud.hipp.smelty.screen.SmelterControllerScreenHandler;
import cloud.hipp.smelty.screen.AnalysisBenchScreen;
import cloud.hipp.smelty.screen.SmelterControllerScreen;
import cloud.hipp.smelty.screen.SmeltyScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class SmeltyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(SmeltyScreenHandlers.SMELTER_CONTROLLER, SmelterControllerScreen::new);
		HandledScreens.register(SmeltyScreenHandlers.ANALYSIS_BENCH, AnalysisBenchScreen::new);

		// Block entity renderers
		BlockEntityRendererFactories.register(SmeltyBlockEntities.SMELTER_CONTROLLER, SmelterControllerBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(SmeltyBlockEntities.CHANNEL, ChannelBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(SmeltyBlockEntities.CASTING_BASIN, CastingBasinBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(SmeltyBlockEntities.CASTING_TABLE, CastingTableBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(SmeltyBlockEntities.VALVE, ValveBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(SmeltyBlockEntities.ANALYSIS_BENCH, AnalysisBenchBlockEntityRenderer::new);

		// Render layers
		BlockRenderLayerMap.putBlock(SmeltyBlocks.VALVE, BlockRenderLayer.CUTOUT);
		BlockRenderLayerMap.putBlock(SmeltyBlocks.ANALYSIS_BENCH, BlockRenderLayer.CUTOUT);

		// Alloy registry sync
		ClientPlayNetworking.registerGlobalReceiver(SyncAlloyRegistryPayload.ID,
				(payload, context) -> ClientAlloyRegistry.update(payload.compositionToName()));

		// Smelter data sync (real-time UI updates)
		ClientPlayNetworking.registerGlobalReceiver(SyncSmelterDataPayload.ID,
				(payload, context) -> {
					if (context.player().currentScreenHandler instanceof SmelterControllerScreenHandler handler
							&& handler.syncId == payload.syncId()) {
						handler.setData(payload.data());
					}
				});

		// Molten alloy submersion overlay
		MoltenAlloyOverlay.register();

		// Solid alloy block color from its block entity
		ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> {
			if (view == null || pos == null) return 0x808080;
			BlockEntity be = view.getBlockEntity(pos);
			if (be instanceof SolidAlloyBlockEntity solidBe) {
				return solidBe.getColor();
			}
			return 0x808080;
		}, SmeltyBlocks.SOLID_ALLOY);

	}
}

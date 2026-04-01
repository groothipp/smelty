package cloud.hipp.smelty;

import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.block.entity.SmelterControllerBlockEntity;
import cloud.hipp.smelty.block.entity.SolidAlloyBlockEntity;
import cloud.hipp.smelty.fluid.SmeltyFluids;
import cloud.hipp.smelty.screen.SmelterControllerScreen;
import cloud.hipp.smelty.screen.SmeltyScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import org.jspecify.annotations.Nullable;

public class SmeltyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(SmeltyScreenHandlers.SMELTER_CONTROLLER, SmelterControllerScreen::new);

		// Register molten alloy fluid rendering — grayscale lava textures, tinted by alloy color
		Identifier moltenStill = Identifier.of(Smelty.MOD_ID, "block/molten_alloy_still");
		Identifier moltenFlow = Identifier.of(Smelty.MOD_ID, "block/molten_alloy_flow");
		FluidRenderHandlerRegistry.INSTANCE.register(
				SmeltyFluids.MOLTEN_ALLOY_STILL, SmeltyFluids.MOLTEN_ALLOY_FLOWING,
				new SimpleFluidRenderHandler(moltenStill, moltenFlow) {
					@Override
					public int getFluidColor(@Nullable BlockRenderView view, @Nullable BlockPos pos, FluidState state) {
						if (view == null || pos == null) return 0xFF6600;
						return findAlloyColor(view, pos);
					}
				}
		);

		// Render fluid on translucent layer
		BlockRenderLayerMap.putFluids(BlockRenderLayer.TRANSLUCENT,
				SmeltyFluids.MOLTEN_ALLOY_STILL, SmeltyFluids.MOLTEN_ALLOY_FLOWING);

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

	private static int findAlloyColor(BlockRenderView view, BlockPos pos) {
		return SmelterControllerBlockEntity.lookupFluidColor(pos);
	}
}

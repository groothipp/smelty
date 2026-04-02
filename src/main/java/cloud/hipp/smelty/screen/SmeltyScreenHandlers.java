package cloud.hipp.smelty.screen;

import cloud.hipp.smelty.Smelty;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class SmeltyScreenHandlers {
	public static final ScreenHandlerType<SmelterControllerScreenHandler> SMELTER_CONTROLLER =
			Registry.register(
					Registries.SCREEN_HANDLER,
					Identifier.of(Smelty.MOD_ID, "smelter_controller"),
					new ExtendedScreenHandlerType<>(SmelterControllerScreenHandler::new, SmelterData.PACKET_CODEC)
			);

	public static final ScreenHandlerType<AnalysisBenchScreenHandler> ANALYSIS_BENCH =
			Registry.register(
					Registries.SCREEN_HANDLER,
					Identifier.of(Smelty.MOD_ID, "analysis_bench"),
					new ExtendedScreenHandlerType<>(AnalysisBenchScreenHandler::new, AnalysisBenchData.PACKET_CODEC)
			);

	public static void initialize() {
	}
}

package cloud.hipp.smelty;

import cloud.hipp.smelty.screen.SmelterControllerScreen;
import cloud.hipp.smelty.screen.SmeltyScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class SmeltyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(SmeltyScreenHandlers.SMELTER_CONTROLLER, SmelterControllerScreen::new);
	}
}

package cloud.hipp.smelty;

import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.screen.SmeltyScreenHandlers;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Smelty implements ModInitializer {
	public static final String MOD_ID = "smelty";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SmeltyBlocks.initialize();
		SmeltyBlockEntities.initialize();
		SmeltyItems.initialize();
		SmeltyScreenHandlers.initialize();
		LOGGER.info("Smelty initialized!");
	}
}
package cloud.hipp.smelty;

import cloud.hipp.smelty.block.SmeltyBlocks;
import cloud.hipp.smelty.block.entity.AnalysisBenchBlockEntity;
import cloud.hipp.smelty.block.entity.SmeltyBlockEntities;
import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.AlloyRegistry;
import cloud.hipp.smelty.network.RenameAlloyPayload;
import cloud.hipp.smelty.network.SyncAlloyRegistryPayload;
import cloud.hipp.smelty.network.SyncSmelterDataPayload;
import cloud.hipp.smelty.screen.SmeltyScreenHandlers;
import cloud.hipp.smelty.tool.SmeltyArmorRecipeSerializer;
import cloud.hipp.smelty.tool.SmeltyToolRecipeSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

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
		SmeltyToolRecipeSerializer.initialize();
		SmeltyArmorRecipeSerializer.initialize();
		registerNetworking();
		LOGGER.info("Smelty initialized!");
	}

	private void registerNetworking() {
		PayloadTypeRegistry.playC2S().register(RenameAlloyPayload.ID, RenameAlloyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncAlloyRegistryPayload.ID, SyncAlloyRegistryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncSmelterDataPayload.ID, SyncSmelterDataPayload.CODEC);

		// Sync alloy registry to client on join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			server.execute(() -> {
				ServerWorld overworld = server.getOverworld();
				AlloyRegistry registry = AlloyRegistry.get(overworld);
				ServerPlayNetworking.send(handler.getPlayer(),
						new SyncAlloyRegistryPayload(registry.getCompositionToName()));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RenameAlloyPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerWorld serverWorld = context.player().getEntityWorld();
				if (serverWorld.getBlockEntity(payload.pos()) instanceof AnalysisBenchBlockEntity bench) {
					if (!bench.hasPlate()) return;

					String name = payload.name().trim();
					if (name.isEmpty() || name.length() > 32) return;

					AlloyComposition comp = AnalysisBenchBlockEntity.getCompositionFromPlate(bench.getPlateItem());
					if (comp.getMaterials().size() <= 1) return;

					AlloyRegistry registry = AlloyRegistry.get(serverWorld);
					// Don't allow overwriting an existing name
					if (registry.getAlloyName(comp) != null) return;
					registry.setAlloyName(comp.getNormalizedKey(), name);

					// Sync updated registry to all players
					SyncAlloyRegistryPayload syncPayload = new SyncAlloyRegistryPayload(registry.getCompositionToName());
					for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
						ServerPlayNetworking.send(player, syncPayload);
					}
				}
			});
		});
	}
}
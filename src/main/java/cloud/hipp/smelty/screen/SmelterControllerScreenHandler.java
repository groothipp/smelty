package cloud.hipp.smelty.screen;

import cloud.hipp.smelty.block.entity.SmelterControllerBlockEntity;
import cloud.hipp.smelty.network.SyncSmelterDataPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

public class SmelterControllerScreenHandler extends ScreenHandler {
	private SmelterData data;
	private final SmelterControllerBlockEntity blockEntity; // null on client
	private final ServerPlayerEntity serverPlayer; // null on client
	private SmelterData lastSentData;

	// Server constructor
	public SmelterControllerScreenHandler(int syncId, PlayerInventory playerInventory,
										  SmelterData data, SmelterControllerBlockEntity blockEntity,
										  ServerPlayerEntity player) {
		super(SmeltyScreenHandlers.SMELTER_CONTROLLER, syncId);
		this.data = data;
		this.blockEntity = blockEntity;
		this.serverPlayer = player;
		this.lastSentData = data;
	}

	// Client constructor
	public SmelterControllerScreenHandler(int syncId, PlayerInventory playerInventory, SmelterData data) {
		super(SmeltyScreenHandlers.SMELTER_CONTROLLER, syncId);
		this.data = data;
		this.blockEntity = null;
		this.serverPlayer = null;
		this.lastSentData = null;
	}

	public SmelterData getData() {
		return data;
	}

	public void setData(SmelterData data) {
		this.data = data;
	}

	@Override
	public void sendContentUpdates() {
		super.sendContentUpdates();
		if (blockEntity != null && serverPlayer != null) {
			SmelterData newData = blockEntity.buildScreenData();
			if (!newData.equals(lastSentData)) {
				lastSentData = newData;
				ServerPlayNetworking.send(serverPlayer,
						new SyncSmelterDataPayload(syncId, newData));
			}
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}

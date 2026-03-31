package cloud.hipp.smelty.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public class SmelterControllerScreenHandler extends ScreenHandler {
	private final SmelterData data;

	public SmelterControllerScreenHandler(int syncId, PlayerInventory playerInventory, SmelterData data) {
		super(SmeltyScreenHandlers.SMELTER_CONTROLLER, syncId);
		this.data = data;
	}

	public SmelterData getData() {
		return data;
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

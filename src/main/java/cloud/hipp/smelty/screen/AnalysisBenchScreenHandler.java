package cloud.hipp.smelty.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public class AnalysisBenchScreenHandler extends ScreenHandler {
	private final AnalysisBenchData data;

	public AnalysisBenchScreenHandler(int syncId, PlayerInventory playerInventory, AnalysisBenchData data) {
		super(SmeltyScreenHandlers.ANALYSIS_BENCH, syncId);
		this.data = data;
	}

	public AnalysisBenchData getData() {
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

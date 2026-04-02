package cloud.hipp.smelty.client;

import cloud.hipp.smelty.block.entity.SmelterControllerBlockEntity;
import cloud.hipp.smelty.block.entity.SmelterControllerBlockEntity.FluidBoundsData;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * Renders an alloy-colored screen tint when the player is submerged in molten alloy.
 */
public class MoltenAlloyOverlay implements HudRenderCallback {
	private int overlayColor = 0;
	private int checkCooldown = 0;

	public static void register() {
		HudRenderCallback.EVENT.register(new MoltenAlloyOverlay());
	}

	@Override
	public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;

		if (--checkCooldown <= 0) {
			checkCooldown = 3;
			overlayColor = computeSubmersionColor(client);
		}

		if (overlayColor != 0) {
			drawContext.fill(0, 0,
					client.getWindow().getScaledWidth(),
					client.getWindow().getScaledHeight(),
					overlayColor);
		}
	}

	private static int computeSubmersionColor(MinecraftClient client) {
		Vec3d eyePos = client.player.getEyePos();
		Map<net.minecraft.util.math.BlockPos, FluidBoundsData> smelters =
				SmelterControllerBlockEntity.getClientSmelters();

		for (FluidBoundsData data : smelters.values()) {
			if (data.totalVolumeMl() <= 0 || data.maxVolume() <= 0) continue;

			float fillRatio = (float) data.totalVolumeMl() / data.maxVolume();
			double fluidTopY = data.minY() + 2 + fillRatio * (data.height() - 2);

			// Check if eye position is within the fluid bounds
			if (eyePos.x > data.minX() + 1 && eyePos.x < data.minX() + data.width() - 1
					&& eyePos.z > data.minZ() + 1 && eyePos.z < data.minZ() + data.depth() - 1
					&& eyePos.y > data.minY() + 2 && eyePos.y < fluidTopY) {
				int r = (data.color() >> 16) & 0xFF;
				int g = (data.color() >> 8) & 0xFF;
				int b = data.color() & 0xFF;
				return (0x99 << 24) | (r << 16) | (g << 8) | b; // 60% alpha
			}
		}

		return 0;
	}
}

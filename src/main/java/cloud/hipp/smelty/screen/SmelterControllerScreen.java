package cloud.hipp.smelty.screen;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.ClientAlloyRegistry;
import cloud.hipp.smelty.material.MaterialItems;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

public class SmelterControllerScreen extends HandledScreen<SmelterControllerScreenHandler> {
	// Max heat: 8x8 interior of 10x10 smelter, all lava (64 * 50 = 3200)
	private static final int MAX_HEAT_DISPLAY = 3200;

	private static final int HEAT_BAR_WIDTH = 18;
	private static final int BAR_HEIGHT = 52;
	private static final int VOLUME_BAR_WIDTH = 110;

	public SmelterControllerScreen(SmelterControllerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 200;
		this.backgroundHeight = 166;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		// Dark panel background
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xCC222222);
		// Border
		context.fill(x, y, x + backgroundWidth, y + 1, 0xFF555555);
		context.fill(x, y + backgroundHeight - 1, x + backgroundWidth, y + backgroundHeight, 0xFF555555);
		context.fill(x, y, x + 1, y + backgroundHeight, 0xFF555555);
		context.fill(x + backgroundWidth - 1, y, x + backgroundWidth, y + backgroundHeight, 0xFF555555);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		SmelterData data = handler.getData();

		// Title centered
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Smelter Controller"),
				backgroundWidth / 2, 6, 0xFFFFFFFF);

		int barY = 20;

		// === Heat bar (left side) ===
		int heatBarX = 20;
		drawHeatBar(context, heatBarX, barY, data.heatLevel());

		// Heat value below bar
		String heatText = String.valueOf(data.heatLevel());
		int heatTextW = textRenderer.getWidth(heatText);
		context.drawTextWithShadow(textRenderer, Text.literal(heatText),
				heatBarX + (HEAT_BAR_WIDTH - heatTextW) / 2, barY + BAR_HEIGHT + 5, 0xFFFFAA00);

		// "Heat" label below value
		String heatLabel = "Heat";
		int heatLabelW = textRenderer.getWidth(heatLabel);
		context.drawTextWithShadow(textRenderer, Text.literal(heatLabel),
				heatBarX + (HEAT_BAR_WIDTH - heatLabelW) / 2, barY + BAR_HEIGHT + 16, 0xFFAAAAAA);

		// === Volume bar (right side, fills remaining space) ===
		int volumeBarX = 60;
		drawVolumeBar(context, volumeBarX, barY, data.currentVolume(), data.maxVolume());

		// Volume text below bar (display in ingots)
		int curIngots = data.currentVolume() / MaterialItems.UNITS_PER_INGOT;
		int maxIngots = data.maxVolume() / MaterialItems.UNITS_PER_INGOT;
		int percentage = data.maxVolume() > 0 ? (data.currentVolume() * 100 / data.maxVolume()) : 0;
		String volText = curIngots + " / " + maxIngots + " ingots (" + percentage + "%)";
		int volTextW = textRenderer.getWidth(volText);
		context.drawTextWithShadow(textRenderer, Text.literal(volText),
				volumeBarX + (VOLUME_BAR_WIDTH - volTextW) / 2, barY + BAR_HEIGHT + 5, 0xFF55FFFF);

		// "Volume" label below value
		String volLabel = "Volume";
		int volLabelW = textRenderer.getWidth(volLabel);
		context.drawTextWithShadow(textRenderer, Text.literal(volLabel),
				volumeBarX + (VOLUME_BAR_WIDTH - volLabelW) / 2, barY + BAR_HEIGHT + 16, 0xFFAAAAAA);

		// === Alloy composition section ===
		int compositionY = barY + BAR_HEIGHT + 30;
		drawComposition(context, compositionY, data);
	}

	private void drawComposition(DrawContext context, int startY, SmelterData data) {
		boolean hasMolten = !data.moltenBreakdown().isEmpty();
		boolean hasSolid = data.solidVolumeMl() > 0;

		if (!hasMolten && !hasSolid) return;

		// Separator line
		context.fill(10, startY - 2, backgroundWidth - 10, startY - 1, 0xFF444444);

		int y = startY;

		if (hasMolten) {
			String moltenName = getCompositionName(data.moltenBreakdown());
			context.drawTextWithShadow(textRenderer, Text.literal("Molten " + moltenName),
					10, y, 0xFFFFAA00);
			y += 12;

			y = drawBreakdownLines(context, y, data.moltenBreakdown());
		}

		if (hasSolid) {
			if (hasMolten) y += 2;
			String solidName = getCompositionName(data.solidBreakdown());
			context.drawTextWithShadow(textRenderer, Text.literal("Solid " + solidName),
					10, y, 0xFF888888);
			y += 12;

			y = drawBreakdownLines(context, y, data.solidBreakdown());
		}
	}

	private int drawBreakdownLines(DrawContext context, int y, Map<SmeltyMaterial, Integer> breakdown) {
		int totalMl = 0;
		for (int ml : breakdown.values()) totalMl += ml;

		for (Map.Entry<SmeltyMaterial, Integer> entry : breakdown.entrySet()) {
			SmeltyMaterial material = entry.getKey();
			int ml = entry.getValue();
			int pct = totalMl > 0 ? (ml * 100 / totalMl) : 0;
			String ingotStr = formatIngots(ml);

			String line = material.getDisplayName() + " - " + ingotStr + " (" + pct + "%)";
			context.drawTextWithShadow(textRenderer, Text.literal(line),
					14, y, getMaterialColor(material));
			y += 11;
		}
		return y;
	}

	private String getCompositionName(Map<SmeltyMaterial, Integer> breakdown) {
		if (breakdown.size() == 1) {
			return breakdown.keySet().iterator().next().getDisplayName();
		}
		AlloyComposition comp = new AlloyComposition();
		for (Map.Entry<SmeltyMaterial, Integer> entry : breakdown.entrySet()) {
			comp.addMaterial(entry.getKey(), entry.getValue());
		}
		String name = ClientAlloyRegistry.getAlloyName(comp);
		return name != null ? name : "Alloy";
	}

	private String formatIngots(int volume) {
		int ingots = volume / MaterialItems.UNITS_PER_INGOT;
		int remainder = volume % MaterialItems.UNITS_PER_INGOT;
		if (remainder == 0) {
			if (ingots % 9 == 0 && ingots >= 9) {
				int blocks = ingots / 9;
				return blocks + (blocks == 1 ? " block" : " blocks");
			}
			return ingots + (ingots == 1 ? " ingot" : " ingots");
		}
		double fractional = volume / (double) MaterialItems.UNITS_PER_INGOT;
		return String.format("%.1f ingots", fractional);
	}

	private int getMaterialColor(SmeltyMaterial material) {
		return switch (material) {
			case COPPER -> 0xFFE07040;
			case IRON -> 0xFFD0D0D0;
			case GOLD -> 0xFFFFDD44;
			case DIAMOND -> 0xFF44EEDD;
			case NETHERITE -> 0xFF5C4033;
		};
	}

	private void drawHeatBar(DrawContext context, int barX, int barY, int heatLevel) {
		// Bar border
		context.fill(barX - 1, barY - 1, barX + HEAT_BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xFF444444);
		// Bar background
		context.fill(barX, barY, barX + HEAT_BAR_WIDTH, barY + BAR_HEIGHT, 0xFF111111);

		// Fill with lava texture from bottom up
		float fillRatio = Math.min(1.0f, (float) heatLevel / MAX_HEAT_DISPLAY);
		int fillHeight = (int) (BAR_HEIGHT * fillRatio);

		if (fillHeight > 0) {
			int fillTop = barY + BAR_HEIGHT - fillHeight;

			// Get lava sprite from block atlas
			SpriteIdentifier lavaId = new SpriteIdentifier(
					SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
					Identifier.ofVanilla("block/lava_still"));
			Sprite lavaSprite = context.getSprite(lavaId);

			// Draw lava texture stretched into the fill area
			context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, lavaSprite,
					barX, fillTop, HEAT_BAR_WIDTH, fillHeight);
		}

		// Tick marks on the right side of the bar for visual reference
		drawBarTickMark(context, barX, barY, 0.25f, 0xFF333333);
		drawBarTickMark(context, barX, barY, 0.50f, 0xFF333333);
		drawBarTickMark(context, barX, barY, 0.75f, 0xFF333333);
	}

	private void drawBarTickMark(DrawContext context, int barX, int barY, float ratio, int color) {
		int tickY = barY + BAR_HEIGHT - (int) (BAR_HEIGHT * ratio);
		context.fill(barX + HEAT_BAR_WIDTH - 3, tickY, barX + HEAT_BAR_WIDTH, tickY + 1, color);
	}

	private void drawVolumeBar(DrawContext context, int barX, int barY, int currentVolume, int maxVolume) {
		// Bar border
		context.fill(barX - 1, barY - 1, barX + VOLUME_BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xFF444444);
		// Bar background
		context.fill(barX, barY, barX + VOLUME_BAR_WIDTH, barY + BAR_HEIGHT, 0xFF111111);

		// Fill from bottom up with molten metal color
		float fillRatio = maxVolume > 0 ? Math.min(1.0f, (float) currentVolume / maxVolume) : 0;
		int fillHeight = (int) (BAR_HEIGHT * fillRatio);

		if (fillHeight > 0) {
			int fillTop = barY + BAR_HEIGHT - fillHeight;
			// Molten metal gradient: darker orange at bottom, brighter at top
			context.fillGradient(barX, fillTop, barX + VOLUME_BAR_WIDTH, barY + BAR_HEIGHT,
					0xFFEE8800, 0xFFCC4400);
		}

		// Tick marks
		for (int i = 1; i <= 3; i++) {
			float ratio = i / 4.0f;
			int tickY = barY + BAR_HEIGHT - (int) (BAR_HEIGHT * ratio);
			context.fill(barX + VOLUME_BAR_WIDTH - 4, tickY, barX + VOLUME_BAR_WIDTH, tickY + 1, 0xFF333333);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
	}
}

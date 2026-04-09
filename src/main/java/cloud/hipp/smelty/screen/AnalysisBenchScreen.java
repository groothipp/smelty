package cloud.hipp.smelty.screen;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialProperty;
import cloud.hipp.smelty.material.Modifier;
import cloud.hipp.smelty.material.SmeltyMaterial;
import cloud.hipp.smelty.network.RenameAlloyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class AnalysisBenchScreen extends HandledScreen<AnalysisBenchScreenHandler> {
	private static final int BAR_WIDTH = 86;
	private static final int BAR_HEIGHT = 8;
	private static final int BAR_SPACING = 14;
	private static final int LABEL_WIDTH = 66;

	// Custom display order: melting point first, then combat-relevant properties
	private static final MaterialProperty[] DISPLAY_ORDER = {
			MaterialProperty.MELTING_POINT,
			MaterialProperty.HARDNESS,
			MaterialProperty.TOUGHNESS,
			MaterialProperty.MALLEABILITY,
			MaterialProperty.DUCTILITY,
			MaterialProperty.DENSITY,
			MaterialProperty.CORROSION_RESISTANCE,
	};

	private TextFieldWidget nameField;
	private ButtonWidget saveButton;
	private String lastSentName;
	private boolean nameSaved;

	public AnalysisBenchScreen(AnalysisBenchScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 220;
		this.backgroundHeight = 240;
	}

	@Override
	protected void init() {
		super.init();
		AnalysisBenchData data = handler.getData();

		if (data.isRenameable()) {
			lastSentName = data.materialName();
			nameField = new TextFieldWidget(textRenderer, x + 10, y + 8, backgroundWidth - 56, 14, Text.literal("Alloy Name"));
			nameField.setMaxLength(32);
			nameField.setText("");
			nameField.setPlaceholder(Text.literal("Name this alloy...").styled(s -> s.withColor(0x888888)));
			addDrawableChild(nameField);

			saveButton = ButtonWidget.builder(Text.literal("Save"), button -> confirmName())
					.dimensions(x + backgroundWidth - 42, y + 7, 32, 16)
					.build();
			addDrawableChild(saveButton);
		}
	}

	private void confirmName() {
		if (nameField == null || nameSaved) return;
		String name = nameField.getText().trim();
		if (!name.isEmpty()) {
			AnalysisBenchData data = handler.getData();
			ClientPlayNetworking.send(new RenameAlloyPayload(data.benchPos(), name));
			lastSentName = name;
			nameSaved = true;
			nameField.setEditable(false);
			nameField.setFocused(false);
			if (saveButton != null) saveButton.active = false;
		}
	}

	@Override
	public boolean keyPressed(KeyInput keyInput) {
		if (nameField != null && nameField.isFocused()) {
			int keyCode = keyInput.key();
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				confirmName();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				return super.keyPressed(keyInput);
			}
			// Let the text field handle the key, but consume it to prevent
			// chat/inventory shortcuts from firing
			nameField.keyPressed(keyInput);
			return true;
		}
		return super.keyPressed(keyInput);
	}

	@Override
	public void close() {
		// Name is only saved via explicit confirm (Enter or Save button)
		super.close();
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xCC222222);
		// Border
		context.fill(x, y, x + backgroundWidth, y + 1, 0xFF555555);
		context.fill(x, y + backgroundHeight - 1, x + backgroundWidth, y + backgroundHeight, 0xFF555555);
		context.fill(x, y, x + 1, y + backgroundHeight, 0xFF555555);
		context.fill(x + backgroundWidth - 1, y, x + backgroundWidth, y + backgroundHeight, 0xFF555555);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AnalysisBenchData data = handler.getData();

		// Title (non-editable for pure materials)
		int titleY = 10;
		if (!data.isRenameable()) {
			context.drawTextWithShadow(textRenderer, Text.literal(data.materialName()),
					10, titleY, 0xFFFFFFFF);
		}

		// Separator
		int sepY = 26;
		context.fill(10, sepY, backgroundWidth - 10, sepY + 1, 0xFF555555);

		// Build composition for property computation
		AlloyComposition comp = new AlloyComposition();
		for (Map.Entry<SmeltyMaterial, Integer> entry : data.composition().entrySet()) {
			comp.addMaterial(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<Modifier, Integer> entry : data.modifiers().entrySet()) {
			comp.addModifier(entry.getKey(), entry.getValue());
		}

		// Draw property bars with modifier bonus annotations (custom order, melting point first)
		int startY = 32;
		for (int i = 0; i < DISPLAY_ORDER.length; i++) {
			MaterialProperty prop = DISPLAY_ORDER[i];
			double finalValue = comp.getFinalProperty(prop);
			double modBonus = comp.getModifierBonus(prop);
			int barY = startY + i * BAR_SPACING;
			if (prop == MaterialProperty.MELTING_POINT) {
				drawMeltingPointRow(context, finalValue, modBonus, barY);
			} else if (prop == MaterialProperty.DENSITY) {
				drawDensityBar(context, finalValue, modBonus, barY);
			} else {
				drawPropertyBar(context, prop, finalValue, modBonus, barY);
			}
		}

		// Separator before composition section
		int compY = startY + DISPLAY_ORDER.length * BAR_SPACING + 4;
		context.fill(10, compY, backgroundWidth - 10, compY + 1, 0xFF444444);
		compY += 5;

		// Diversity bonus
		double diversity = comp.getDiversityBonus();
		if (diversity > 0) {
			String diversityText = "+" + Math.round(diversity * 100) + "%";
			context.drawTextWithShadow(textRenderer, Text.literal(diversityText),
					10, compY, 0xFF88FF88);
			compY += 12;
		}

		int sectionStartY = compY;
		int rightCol = backgroundWidth / 2 + 2;

		// Materials section (left column)
		if (data.composition().size() > 0) {
			context.drawTextWithShadow(textRenderer, Text.literal("Materials:"),
					10, compY, 0xFFFFAA00);
			compY += 12;

			for (Map.Entry<SmeltyMaterial, Integer> entry : data.composition().entrySet()) {
				String line = entry.getKey().getDisplayName() + " " + entry.getValue() + "%";
				context.drawTextWithShadow(textRenderer, Text.literal(line),
						14, compY, getMaterialColor(entry.getKey()));
				compY += 11;
			}
		}

		// Modifiers section (right column, same Y as materials)
		if (!data.modifiers().isEmpty()) {
			int modY = sectionStartY;
			context.drawTextWithShadow(textRenderer, Text.literal("Modifiers:"),
					rightCol, modY, 0xFFBB99FF);
			modY += 12;

			for (Map.Entry<Modifier, Integer> entry : data.modifiers().entrySet()) {
				context.drawTextWithShadow(textRenderer, Text.literal(getModifierName(entry.getKey())),
						rightCol + 4, modY, 0xFF000000 | entry.getKey().getTintColor());
				modY += 11;
			}
			if (modY > compY) compY = modY;
		}
	}

	private void drawPropertyBar(DrawContext context, MaterialProperty prop, double value, double modBonus, int barY) {
		String label = getPropertyLabel(prop);
		int labelX = 10;
		int barX = labelX + LABEL_WIDTH;

		// Label
		context.drawTextWithShadow(textRenderer, Text.literal(label), labelX, barY, 0xFFAAAAAA);

		// Bar background
		context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF111111);
		// Bar border
		context.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY, 0xFF444444);
		context.fill(barX - 1, barY + BAR_HEIGHT, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xFF444444);
		context.fill(barX - 1, barY, barX, barY + BAR_HEIGHT, 0xFF444444);
		context.fill(barX + BAR_WIDTH, barY, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT, 0xFF444444);

		// Fill
		int fillWidth = (int) (BAR_WIDTH * Math.min(1.0, value / 100.0));
		if (fillWidth > 0) {
			int color = getBarColor(value);
			context.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, color);
		}

		// Value text + modifier bonus
		String valueText = String.format("%.0f", value);
		int textX = barX + BAR_WIDTH + 4;
		context.drawTextWithShadow(textRenderer, Text.literal(valueText), textX, barY, 0xFFCCCCCC);

		if (Math.abs(modBonus) >= 0.5) {
			String bonusText = String.format("(%+.0f)", modBonus);
			int bonusX = textX + textRenderer.getWidth(valueText) + 2;
			int bonusColor = modBonus > 0 ? 0xFF88FF88 : 0xFFFF8888;
			context.drawTextWithShadow(textRenderer, Text.literal(bonusText), bonusX, barY, bonusColor);
		}
	}

	private void drawMeltingPointRow(DrawContext context, double value, double modBonus, int barY) {
		int labelX = 10;
		// Label
		context.drawTextWithShadow(textRenderer, Text.literal("Melt Point"), labelX, barY, 0xFFAAAAAA);

		// Just show the value as a number (no bar — melting point isn't good or bad)
		String valueText = String.format("%.0f", value);
		int textX = labelX + LABEL_WIDTH;
		context.drawTextWithShadow(textRenderer, Text.literal(valueText), textX, barY, 0xFFFFAA00);

		if (Math.abs(modBonus) >= 0.5) {
			String bonusText = String.format("(%+.0f)", modBonus);
			int bonusX = textX + textRenderer.getWidth(valueText) + 2;
			int bonusColor = modBonus > 0 ? 0xFF88FF88 : 0xFFFF8888;
			context.drawTextWithShadow(textRenderer, Text.literal(bonusText), bonusX, barY, bonusColor);
		}
	}

	private void drawDensityBar(DrawContext context, double value, double modBonus, int barY) {
		int labelX = 10;
		int barX = labelX + LABEL_WIDTH;

		// Label
		context.drawTextWithShadow(textRenderer, Text.literal("Density"), labelX, barY, 0xFFAAAAAA);

		// Bar background
		context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF111111);
		// Bar border
		context.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY, 0xFF444444);
		context.fill(barX - 1, barY + BAR_HEIGHT, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xFF444444);
		context.fill(barX - 1, barY, barX, barY + BAR_HEIGHT, 0xFF444444);
		context.fill(barX + BAR_WIDTH, barY, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT, 0xFF444444);

		// Fill — density uses U-shape: extremes (near 0 or 100) are good, middle (50) is worst
		int fillWidth = (int) (BAR_WIDTH * Math.min(1.0, value / 100.0));
		if (fillWidth > 0) {
			// U-shape color: distance from 50 determines quality
			double distFrom50 = Math.abs(value - 50);
			int color;
			if (distFrom50 > 33) {
				color = 0xFF44AA44; // green — far from middle
			} else if (distFrom50 > 16) {
				color = 0xFFCCAA44; // yellow — moderate
			} else {
				color = 0xFFCC4444; // red — near middle
			}
			context.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, color);
		}

		// Center marker at 50% to show the "worst" point
		int midX = barX + BAR_WIDTH / 2;
		context.fill(midX, barY, midX + 1, barY + BAR_HEIGHT, 0xFF666666);

		// Value text + modifier bonus
		String valueText = String.format("%.0f", value);
		int textX = barX + BAR_WIDTH + 4;
		context.drawTextWithShadow(textRenderer, Text.literal(valueText), textX, barY, 0xFFCCCCCC);

		if (Math.abs(modBonus) >= 0.5) {
			String bonusText = String.format("(%+.0f)", modBonus);
			int bonusX = textX + textRenderer.getWidth(valueText) + 2;
			int bonusColor = modBonus > 0 ? 0xFF88FF88 : 0xFFFF8888;
			context.drawTextWithShadow(textRenderer, Text.literal(bonusText), bonusX, barY, bonusColor);
		}
	}

	private int getBarColor(double value) {
		// Gradient from red (low) through yellow to green (high)
		if (value < 33) {
			return 0xFFCC4444;
		} else if (value < 66) {
			return 0xFFCCAA44;
		} else {
			return 0xFF44AA44;
		}
	}

	private String getPropertyLabel(MaterialProperty prop) {
		return switch (prop) {
			case HARDNESS -> "Hardness";
			case TOUGHNESS -> "Toughness";
			case MELTING_POINT -> "Melt Point";
			case MALLEABILITY -> "Malleability";
			case DUCTILITY -> "Ductility";
			case DENSITY -> "Density";
			case CORROSION_RESISTANCE -> "Corr. Resist.";
		};
	}

	private String getModifierName(Modifier modifier) {
		return switch (modifier) {
			case COAL -> "Coal";
			case BONE_MEAL -> "Bone Meal";
			case SLIME_BALL -> "Slime Ball";
			case CLAY_BALL -> "Clay Ball";
			case LAPIS_LAZULI -> "Lapis Lazuli";
			case SUGAR -> "Sugar";
			case BLAZE_POWDER -> "Blaze Powder";
			case GLOWSTONE_DUST -> "Glowstone Dust";
			case REDSTONE -> "Redstone";
			case ENDER_PEARL -> "Ender Pearl";
			case MEAT -> "Meat";
		};
	}

	private int getMaterialColor(SmeltyMaterial material) {
		return switch (material) {
			case COPPER -> 0xFFE07040;
			case IRON -> 0xFFD0D0D0;
			case GOLD -> 0xFFFFDD44;
			case DIAMOND -> 0xFF44EEDD;
			case NETHERITE -> 0xFF5C4033;
			case OBSIDIAN -> 0xFF3B1E5E;
			case EMERALD -> 0xFF17DD62;
		};
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
	}
}

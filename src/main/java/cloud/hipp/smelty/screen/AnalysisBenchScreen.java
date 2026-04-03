package cloud.hipp.smelty.screen;

import cloud.hipp.smelty.material.AlloyComposition;
import cloud.hipp.smelty.material.MaterialProperty;
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
	private static final int BAR_WIDTH = 120;
	private static final int BAR_HEIGHT = 8;
	private static final int BAR_SPACING = 14;
	private static final int LABEL_WIDTH = 50;

	private TextFieldWidget nameField;
	private ButtonWidget saveButton;
	private String lastSentName;
	private boolean nameSaved;

	public AnalysisBenchScreen(AnalysisBenchScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 220;
		this.backgroundHeight = 210;
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
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(data.materialName()),
					backgroundWidth / 2, titleY, 0xFFFFFFFF);
		}
		// If renameable, the TextFieldWidget handles it

		// Separator
		int sepY = 26;
		context.fill(10, sepY, backgroundWidth - 10, sepY + 1, 0xFF555555);

		// Build composition for blended properties
		AlloyComposition comp = new AlloyComposition();
		for (Map.Entry<SmeltyMaterial, Integer> entry : data.composition().entrySet()) {
			comp.addMaterial(entry.getKey(), entry.getValue());
		}

		// Draw property bars
		int startY = 32;
		MaterialProperty[] properties = MaterialProperty.values();
		for (int i = 0; i < properties.length; i++) {
			MaterialProperty prop = properties[i];
			double value = comp.getBlendedProperty(prop);
			int barY = startY + i * BAR_SPACING;
			drawPropertyBar(context, prop, value, barY);
		}

		// Composition breakdown at the bottom
		int compY = startY + properties.length * BAR_SPACING + 6;
		if (data.composition().size() > 1) {
			context.fill(10, compY - 2, backgroundWidth - 10, compY - 1, 0xFF444444);
			for (Map.Entry<SmeltyMaterial, Integer> entry : data.composition().entrySet()) {
				String line = entry.getKey().getDisplayName() + ": " + entry.getValue() + "%";
				context.drawTextWithShadow(textRenderer, Text.literal(line),
						14, compY, getMaterialColor(entry.getKey()));
				compY += 11;
			}
		}
	}

	private void drawPropertyBar(DrawContext context, MaterialProperty prop, double value, int barY) {
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

		// Value text
		String valueText = String.format("%.0f", value);
		int textX = barX + BAR_WIDTH + 4;
		context.drawTextWithShadow(textRenderer, Text.literal(valueText), textX, barY, 0xFFCCCCCC);
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
			case MALLEABILITY -> "Malleabl.";
			case DUCTILITY -> "Ductility";
			case DENSITY -> "Density";
			case CORROSION_RESISTANCE -> "Corr. Res.";
			case THERMAL_CONDUCTIVITY -> "Therm. C.";
		};
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

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
	}
}

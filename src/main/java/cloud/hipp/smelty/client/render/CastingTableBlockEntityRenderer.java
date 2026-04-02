package cloud.hipp.smelty.client.render;

import cloud.hipp.smelty.block.entity.CastingTableBlockEntity;
import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

public class CastingTableBlockEntityRenderer
		implements BlockEntityRenderer<CastingTableBlockEntity, CastingTableBlockEntityRenderer.RenderState> {

	private static final Identifier FLUID_TEXTURE = Identifier.of("smelty", "textures/block/molten_alloy_still.png");
	private static final float V_FRAME = 1f / 20f;

	// Basin geometry (from casting_table.json)
	private static final float BX1 = 2f / 16f, BZ1 = 2f / 16f;
	private static final float BX2 = 14f / 16f, BZ2 = 14f / 16f;
	private static final float SURFACE_Y = 11f / 16f; // sunken center top
	private static final float RIM_Y = 12f / 16f;     // rim top — mold surface level

	// Mold textures (iron block with item shape cut out)
	private static final Identifier INGOT_MOLD_TEX = Identifier.of("smelty", "textures/item/ingot_mold.png");
	private static final Identifier NUGGET_MOLD_TEX = Identifier.of("smelty", "textures/item/nugget_mold.png");
	private static final Identifier ROD_MOLD_TEX = Identifier.of("smelty", "textures/item/rod_mold.png");

	public CastingTableBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void updateRenderState(CastingTableBlockEntity entity, RenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumbling);

		state.fluidLevel = entity.getFluidLevelMl();
		state.fillRatio = entity.getFillRatio();
		state.color = entity.getColor();
		state.solidified = entity.isSolidified();
		state.animationTime = entity.getWorld() != null ? entity.getWorld().getTime() + tickDelta : 0;

		// Pattern texture: available whenever there's a pattern and not solidified
		// (used on empty table AND during filling to mask fluid)
		state.patternTexture = null;
		if (entity.hasPattern() && !entity.isSolidified()) {
			state.patternTexture = getPatternTexture(entity.getPatternItem().getItem());
		}

		// Solidified output display
		state.solidItemTexture = null;
		state.solidMoldTexture = null;
		if (entity.isSolidified()) {
			computeSolidifiedDisplay(entity, state);
		}
	}

	private static Identifier getPatternTexture(Item item) {
		if (item == SmeltyItems.INGOT_MOLD) return INGOT_MOLD_TEX;
		if (item == SmeltyItems.NUGGET_MOLD) return NUGGET_MOLD_TEX;
		if (item == SmeltyItems.ROD_MOLD) return ROD_MOLD_TEX;
		if (item == Items.COPPER_INGOT) return Identifier.ofVanilla("textures/item/copper_ingot.png");
		if (item == Items.IRON_INGOT) return Identifier.ofVanilla("textures/item/iron_ingot.png");
		if (item == Items.GOLD_INGOT) return Identifier.ofVanilla("textures/item/gold_ingot.png");
		if (item == Items.DIAMOND) return Identifier.ofVanilla("textures/item/diamond.png");
		if (item == Items.NETHERITE_INGOT) return Identifier.ofVanilla("textures/item/netherite_ingot.png");
		if (item == Items.IRON_NUGGET) return Identifier.ofVanilla("textures/item/iron_nugget.png");
		if (item == Items.GOLD_NUGGET) return Identifier.ofVanilla("textures/item/gold_nugget.png");
		if (item == Items.STICK) return Identifier.ofVanilla("textures/item/stick.png");
		return null;
	}

	private static Identifier getMoldTexture(Item moldItem) {
		if (moldItem == SmeltyItems.INGOT_MOLD) return INGOT_MOLD_TEX;
		if (moldItem == SmeltyItems.NUGGET_MOLD) return NUGGET_MOLD_TEX;
		if (moldItem == SmeltyItems.ROD_MOLD) return ROD_MOLD_TEX;
		return null;
	}

	private static Identifier getItemTexture(Item item) {
		// Copper
		if (item == Items.COPPER_INGOT || item == SmeltyItems.COPPER_PLATE || item == SmeltyItems.COPPER_ROD)
			return Identifier.ofVanilla("textures/block/copper_block.png");
		// Iron
		if (item == Items.IRON_INGOT || item == Items.IRON_NUGGET || item == SmeltyItems.IRON_PLATE || item == SmeltyItems.IRON_ROD)
			return Identifier.ofVanilla("textures/block/iron_block.png");
		// Gold
		if (item == Items.GOLD_INGOT || item == Items.GOLD_NUGGET || item == SmeltyItems.GOLD_PLATE || item == SmeltyItems.GOLD_ROD)
			return Identifier.ofVanilla("textures/block/gold_block.png");
		// Diamond
		if (item == Items.DIAMOND || item == SmeltyItems.DIAMOND_INGOT || item == SmeltyItems.DIAMOND_NUGGET
				|| item == SmeltyItems.DIAMOND_PLATE || item == SmeltyItems.DIAMOND_ROD)
			return Identifier.ofVanilla("textures/block/diamond_block.png");
		// Netherite
		if (item == Items.NETHERITE_INGOT || item == SmeltyItems.NETHERITE_NUGGET
				|| item == SmeltyItems.NETHERITE_PLATE || item == SmeltyItems.NETHERITE_ROD)
			return Identifier.ofVanilla("textures/block/netherite_block.png");
		// Alloy items (tinted with alloy color at render time)
		if (isAlloyItem(item))
			return Identifier.ofVanilla("textures/block/iron_block.png");
		return null;
	}

	private static boolean isAlloyItem(Item item) {
		return item == SmeltyItems.ALLOY_PLATE || item == SmeltyItems.ALLOY_INGOT
				|| item == SmeltyItems.ALLOY_NUGGET || item == SmeltyItems.ALLOY_ROD;
	}

	private static void computeSolidifiedDisplay(CastingTableBlockEntity entity, RenderState state) {
		ItemStack output = entity.computeOutputItem();
		if (output.isEmpty()) return;
		Item outItem = output.getItem();
		boolean hasMoldPattern = entity.hasPattern()
				&& CastingTableBlockEntity.isMold(entity.getPatternItem().getItem());

		if (CastingTableBlockEntity.isMold(outItem)) {
			// Output IS a mold (raw pattern + pure iron) — show mold + raw pattern underneath
			state.solidMoldTexture = getMoldTexture(outItem);
			state.solidItemTexture = getPatternTexture(entity.getPatternItem().getItem());
			state.solidItemColor = 0xFFFFFFFF;
		} else if (hasMoldPattern) {
			// Casting from a mold: item below + mold overlay above
			state.solidMoldTexture = getMoldTexture(entity.getPatternItem().getItem());
			state.solidItemTexture = getItemTexture(outItem);
			state.solidItemColor = isAlloyItem(outItem)
					? (entity.getColor() | 0xFF000000) : 0xFFFFFFFF;
		} else {
			// Plate — fill basin, no mold overlay
			state.solidMoldTexture = null;
			state.solidItemTexture = getItemTexture(outItem);
			state.solidItemColor = isAlloyItem(outItem)
					? (entity.getColor() | 0xFF000000) : 0xFFFFFFFF;
		}
	}

	@Override
	public void render(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {

		// === Empty table: show pattern at rim level ===
		if (state.fluidLevel <= 0 && !state.solidified) {
			if (state.patternTexture != null) {
				renderSprite(matrices, queue, state.patternTexture, RIM_Y, 0xFFFFFFFF);
			}
			return;
		}

		// === Solidified: show output item ===
		if (state.solidified) {
			if (state.solidItemTexture != null) {
				float itemY = state.solidMoldTexture != null ? RIM_Y - 0.001f : SURFACE_Y + 0.001f;
				renderSprite(matrices, queue, state.solidItemTexture, itemY, state.solidItemColor);
			}
			if (state.solidMoldTexture != null) {
				renderSprite(matrices, queue, state.solidMoldTexture, RIM_Y, 0xFFFFFFFF);
			}
			return;
		}

		// === Filling: render fluid, then pattern on top to mask ===
		float overlap = 0.005f;
		float x1 = BX1 - overlap, z1 = BZ1 - overlap;
		float x2 = BX2 + overlap, z2 = BZ2 + overlap;
		float y1 = SURFACE_Y;
		float patternOffset = state.patternTexture != null ? 0.002f : 0f;
		float y2 = y1 + state.fillRatio * (1f / 16f) - patternOffset;

		int color = state.color | 0xDD000000;
		int step = ((int) state.animationTime / 2) % 38;
		int frame = step < 20 ? step : 38 - step;
		float v0 = frame * V_FRAME;
		float v1 = v0 + V_FRAME;

		var fluidLayer = RenderLayers.entitySolid(FLUID_TEXTURE);
		matrices.push();
		queue.submitCustom(matrices, fluidLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			renderTopAndSides(vc, matrix, x1, y1, z1, x2, y2, z2, color, 15728880, v0, v1);
		});
		matrices.pop();

		// Pattern/mold on top masks the fluid — fluid only visible through transparent pixels
		if (state.patternTexture != null) {
			renderSprite(matrices, queue, state.patternTexture, RIM_Y, 0xFFFFFFFF);
		}
	}

	/** Render a texture as a flat quad filling the basin area. */
	private void renderSprite(MatrixStack matrices, OrderedRenderCommandQueue queue,
			Identifier texture, float y, int color) {
		var renderLayer = RenderLayers.entityCutout(texture);
		matrices.push();
		queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			vc.vertex(matrix, BX1, y, BZ1).color(color).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
			vc.vertex(matrix, BX1, y, BZ2).color(color).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
			vc.vertex(matrix, BX2, y, BZ2).color(color).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
			vc.vertex(matrix, BX2, y, BZ1).color(color).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
		});
		matrices.pop();
	}

	private void renderTopAndSides(VertexConsumer vc, Matrix4f matrix,
			float x1, float y1, float z1, float x2, float y2, float z2,
			int color, int light, float v0, float v1) {
		// Top
		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		// Bottom
		vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		// North
		vc.vertex(matrix, x1, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x2, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		// South
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
	}

	public static class RenderState extends BlockEntityRenderState {
		public int fluidLevel;
		public float fillRatio;
		public int color;
		public boolean solidified;
		public float animationTime;
		// Pattern (available during empty table + filling, not solidified)
		public Identifier patternTexture;
		// Solidified output
		public Identifier solidItemTexture;
		public int solidItemColor;
		public Identifier solidMoldTexture;
	}
}

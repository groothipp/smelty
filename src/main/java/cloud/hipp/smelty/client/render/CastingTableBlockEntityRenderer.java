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
	private static final Identifier ALLOY_SOLID_TEXTURE = Identifier.ofVanilla("textures/block/iron_block.png");
	private static final float V_FRAME = 1f / 20f;

	private static final Map<SmeltyMaterial, Identifier> SOLID_TEXTURES;
	static {
		SOLID_TEXTURES = new EnumMap<>(SmeltyMaterial.class);
		SOLID_TEXTURES.put(SmeltyMaterial.COPPER, Identifier.ofVanilla("textures/block/copper_block.png"));
		SOLID_TEXTURES.put(SmeltyMaterial.IRON, Identifier.ofVanilla("textures/block/iron_block.png"));
		SOLID_TEXTURES.put(SmeltyMaterial.GOLD, Identifier.ofVanilla("textures/block/gold_block.png"));
		SOLID_TEXTURES.put(SmeltyMaterial.DIAMOND, Identifier.ofVanilla("textures/block/diamond_block.png"));
		SOLID_TEXTURES.put(SmeltyMaterial.NETHERITE, Identifier.ofVanilla("textures/block/netherite_block.png"));
	}

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

		// Pattern item display (visible when table is empty and not solidified)
		state.patternTexture = null;
		if (entity.hasPattern() && entity.getFluidLevelMl() <= 0 && !entity.isSolidified()) {
			state.patternTexture = getPatternTexture(entity.getPatternItem());
		}

		if (entity.isSolidified()) {
			SmeltyMaterial sole = entity.getSoleMaterial();
			if (sole != null) {
				state.solidTexture = SOLID_TEXTURES.getOrDefault(sole, ALLOY_SOLID_TEXTURE);
				state.solidColor = 0xFFFFFFFF;
			} else {
				state.solidTexture = ALLOY_SOLID_TEXTURE;
				state.solidColor = entity.getColor() | 0xFF000000;
			}
		}
	}

	private static Identifier getPatternTexture(ItemStack stack) {
		Item item = stack.getItem();
		// Ingots
		if (item == Items.COPPER_INGOT) return Identifier.ofVanilla("textures/item/copper_ingot.png");
		if (item == Items.IRON_INGOT) return Identifier.ofVanilla("textures/item/iron_ingot.png");
		if (item == Items.GOLD_INGOT) return Identifier.ofVanilla("textures/item/gold_ingot.png");
		if (item == Items.DIAMOND) return Identifier.ofVanilla("textures/item/diamond.png");
		if (item == Items.NETHERITE_INGOT) return Identifier.ofVanilla("textures/item/netherite_ingot.png");
		// Nuggets
		if (item == Items.IRON_NUGGET) return Identifier.ofVanilla("textures/item/iron_nugget.png");
		if (item == Items.GOLD_NUGGET) return Identifier.ofVanilla("textures/item/gold_nugget.png");
		// Stick
		if (item == Items.STICK) return Identifier.ofVanilla("textures/item/stick.png");
		// Mold items — show iron block texture
		if (item == SmeltyItems.INGOT_MOLD || item == SmeltyItems.NUGGET_MOLD || item == SmeltyItems.ROD_MOLD) {
			return Identifier.ofVanilla("textures/block/iron_block.png");
		}
		return null;
	}

	@Override
	public void render(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		// Pattern item on empty table surface
		if (state.patternTexture != null) {
			renderPatternItem(state, matrices, queue);
		}

		if (state.fluidLevel <= 0) return;

		// Fluid sits on sunken center surface (11/16) and fills up to rim (12/16)
		float overlap = 0.005f;
		float x1 = 2f / 16f - overlap;
		float z1 = 2f / 16f - overlap;
		float x2 = 14f / 16f + overlap;
		float z2 = 14f / 16f + overlap;
		float y1 = 11f / 16f;
		float y2 = y1 + state.fillRatio * (1f / 16f);

		if (state.solidified) {
			int color = state.solidColor;
			var renderLayer = RenderLayers.entitySolid(state.solidTexture);
			matrices.push();
			queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
				Matrix4f matrix = entry.getPositionMatrix();
				renderTopAndSides(vc, matrix, x1, y1, z1, x2, y2, z2, color, 15728880, 0, 1);
			});
			matrices.pop();
		} else {
			int color = state.color | 0xDD000000;
			int step = ((int) state.animationTime / 2) % 38;
			int frame = step < 20 ? step : 38 - step;
			float v0 = frame * V_FRAME;
			float v1 = v0 + V_FRAME;

			var renderLayer = RenderLayers.entitySolid(FLUID_TEXTURE);
			matrices.push();
			queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
				Matrix4f matrix = entry.getPositionMatrix();
				renderTopAndSides(vc, matrix, x1, y1, z1, x2, y2, z2, color, 15728880, v0, v1);
			});
			matrices.pop();
		}
	}

	private void renderPatternItem(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
		float py = 11f / 16f + 0.001f; // just above sunken center surface
		float px1 = 4f / 16f, px2 = 12f / 16f;
		float pz1 = 4f / 16f, pz2 = 12f / 16f;

		var renderLayer = RenderLayers.entityCutout(state.patternTexture);
		matrices.push();
		queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			int light = 15728880;
			int white = 0xFFFFFFFF;
			// Top face — flat sprite on table
			vc.vertex(matrix, px1, py, pz1).color(white).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
			vc.vertex(matrix, px1, py, pz2).color(white).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
			vc.vertex(matrix, px2, py, pz2).color(white).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
			vc.vertex(matrix, px2, py, pz1).color(white).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
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
		public Identifier solidTexture;
		public int solidColor;
		public Identifier patternTexture;
	}
}

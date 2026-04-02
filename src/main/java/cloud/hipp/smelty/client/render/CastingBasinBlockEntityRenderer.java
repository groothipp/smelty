package cloud.hipp.smelty.client.render;

import cloud.hipp.smelty.block.entity.CastingBasinBlockEntity;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class CastingBasinBlockEntityRenderer
		implements BlockEntityRenderer<CastingBasinBlockEntity, CastingBasinBlockEntityRenderer.RenderState> {

	private static final Identifier FLUID_TEXTURE = Identifier.of("smelty", "textures/block/molten_alloy_still.png");
	private static final Identifier SOLID_TEXTURE = Identifier.ofVanilla("textures/block/iron_block.png");
	private static final float V_FRAME = 1f / 20f;

	public CastingBasinBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void updateRenderState(CastingBasinBlockEntity entity, RenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumbling);

		state.fluidLevel = entity.getFluidLevelMl();
		state.fillRatio = entity.getFillRatio();
		state.color = entity.getColor();
		state.solidified = entity.isSolidified();
		state.animationTime = entity.getWorld() != null ? entity.getWorld().getTime() + tickDelta : 0;
	}

	@Override
	public void render(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (state.fluidLevel <= 0) return;

		// Interior of basin: walls are 2px, bottom at 4/16
		// Slight overlap into walls to prevent visible gaps
		float overlap = 0.005f;
		float x1 = 2f / 16f - overlap;
		float z1 = 2f / 16f - overlap;
		float x2 = 14f / 16f + overlap;
		float z2 = 14f / 16f + overlap;
		float y1 = 4f / 16f;
		float y2 = y1 + state.fillRatio * (6f / 16f); // max at 10/16 (wall top)

		int alpha = state.solidified ? 0xFF000000 : 0xDD000000;
		int color = state.color | alpha;

		Identifier texture = state.solidified ? SOLID_TEXTURE : FLUID_TEXTURE;
		// Animation: 38-step pingpong cycle (frames 0-19-0), 2 ticks per frame
		float v0, vMax;
		if (state.solidified) {
			v0 = 0;
			vMax = 1f;
		} else {
			int step = ((int) state.animationTime / 2) % 38;
			int frame = step < 20 ? step : 38 - step;
			v0 = frame * V_FRAME;
			vMax = v0 + V_FRAME;
		}
		var renderLayer = RenderLayers.entitySolid(texture);
		matrices.push();
		queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			int light = 15728880;
			renderBox(vc, matrix, x1, y1, z1, x2, y2, z2, color, light, v0, vMax);
		});
		matrices.pop();
	}

	private void renderBox(VertexConsumer vc, Matrix4f matrix,
			float x1, float y1, float z1, float x2, float y2, float z2, int color, int light,
			float v0, float vMax) {
		// Top
		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(0, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		// Bottom
		vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		// North
		vc.vertex(matrix, x1, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x2, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		// South
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(1, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(0, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		// West
		vc.vertex(matrix, x1, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x1, y2, z1).color(color).texture(1, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(0, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		// East
		vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(0, vMax).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
	}

	public static class RenderState extends BlockEntityRenderState {
		public int fluidLevel;
		public float fillRatio;
		public int color;
		public boolean solidified;
		public float animationTime;
	}
}

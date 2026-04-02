package cloud.hipp.smelty.client.render;

import cloud.hipp.smelty.block.entity.CastingTableBlockEntity;
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

public class CastingTableBlockEntityRenderer
		implements BlockEntityRenderer<CastingTableBlockEntity, CastingTableBlockEntityRenderer.RenderState> {

	private static final Identifier FLUID_TEXTURE = Identifier.of("smelty", "textures/block/molten_alloy_still.png");
	private static final float V_FRAME = 1f / 20f;

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
	}

	@Override
	public void render(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (state.fluidLevel <= 0) return;

		// Fluid renders on top of table surface (12/16)
		// Thin layer: 1-2 px tall
		// Slight overlap into walls to prevent visible gaps
		float overlap = 0.005f;
		float x1 = 2f / 16f - overlap;
		float z1 = 2f / 16f - overlap;
		float x2 = 14f / 16f + overlap;
		float z2 = 14f / 16f + overlap;
		float y1 = 12f / 16f;
		float y2 = y1 + state.fillRatio * (2f / 16f); // thin layer, max 14/16

		int alpha = state.solidified ? 0xFF000000 : 0xDD000000;
		int color = state.color | alpha;

		// Animation: 38-step pingpong cycle (frames 0-19-0), 2 ticks per frame
		float v0, v1;
		if (state.solidified) {
			v0 = 0;
			v1 = V_FRAME;
		} else {
			int step = ((int) state.animationTime / 2) % 38;
			int frame = step < 20 ? step : 38 - step;
			v0 = frame * V_FRAME;
			v1 = v0 + V_FRAME;
		}

		var renderLayer = RenderLayers.entitySolid(FLUID_TEXTURE);
		matrices.push();
		queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			int light = 15728880;

			// Just render top face and sides (thin layer)
			// Top
			vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
			vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
			vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
			vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
			// Bottom (on table surface)
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
		});
		matrices.pop();
	}

	public static class RenderState extends BlockEntityRenderState {
		public int fluidLevel;
		public float fillRatio;
		public int color;
		public boolean solidified;
		public float animationTime;
	}
}

package cloud.hipp.smelty.client.render;

import cloud.hipp.smelty.block.entity.SmelterControllerBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.OverlayTexture;
import org.joml.Matrix4f;

public class SmelterControllerBlockEntityRenderer
		implements BlockEntityRenderer<SmelterControllerBlockEntity, SmelterControllerBlockEntityRenderer.RenderState> {

	private static final Identifier FLUID_TEXTURE = Identifier.ofVanilla("textures/block/smelty_molten_alloy.png");
	private static final Identifier SOLID_TEXTURE = Identifier.ofVanilla("textures/block/smooth_stone.png");

	public SmelterControllerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void updateRenderState(SmelterControllerBlockEntity entity, RenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumbling);

		state.valid = entity.isValid();
		state.width = entity.getWidth();
		state.depth = entity.getDepth();
		state.height = entity.getHeight();
		state.minX = entity.getMinX();
		state.minY = entity.getMinY();
		state.minZ = entity.getMinZ();
		state.maxVolume = entity.getMaxVolume();
		state.moltenMl = entity.getMoltenAlloy().getTotalVolumeMl();
		state.unmeltedMl = entity.getUnmeltedMaterials().getTotalVolumeMl();
		state.alloyColor = entity.getMoltenAlloy().isEmpty() ? 0x808080
				: entity.getMoltenAlloy().getBlendedColor();
		state.unmeltedColor = entity.getUnmeltedMaterials().isEmpty() ? 0x808080
				: entity.getUnmeltedMaterials().getBlendedColor();
		state.animationTime = entity.getWorld() != null ? entity.getWorld().getTime() + tickDelta : 0;
	}

	@Override
	public void render(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (!state.valid || state.maxVolume <= 0) return;
		int totalMl = state.moltenMl + state.unmeltedMl;
		if (totalMl <= 0) return;

		// Animation: 38-step pingpong cycle (frames 0-19-0), 2 ticks per frame
		int step = ((int) state.animationTime / 2) % 38;
		int frame = step < 20 ? step : 38 - step;
		float v0 = frame * V_FRAME;
		float v1 = v0 + V_FRAME;

		// Interior bounds relative to the block entity position
		float interiorMinX = state.minX + 1 - state.pos.getX();
		float interiorMinZ = state.minZ + 1 - state.pos.getZ();
		float interiorMinY = state.minY + 2 - state.pos.getY();
		float interiorW = state.width - 2;
		float interiorD = state.depth - 2;
		float interiorH = state.height - 2;

		float unmeltedRatio = (float) state.unmeltedMl / state.maxVolume;
		float moltenRatio = (float) state.moltenMl / state.maxVolume;

		float solidTopY = interiorMinY + unmeltedRatio * interiorH;
		float fluidTopY = interiorMinY + (unmeltedRatio + moltenRatio) * interiorH;

		// Slight overlap into walls to prevent visible gaps
		float overlap = 0.005f;
		float x1 = interiorMinX - overlap;
		float z1 = interiorMinZ - overlap;
		float x2 = interiorMinX + interiorW + overlap;
		float z2 = interiorMinZ + interiorD + overlap;

		// Render solid (unmelted) layer with static texture
		if (state.unmeltedMl > 0) {
			var solidLayer = RenderLayers.entitySolid(SOLID_TEXTURE);
			float sy1 = interiorMinY;
			float sy2 = solidTopY;
			int color = state.unmeltedColor | 0xFF000000;
			matrices.push();
			renderColoredBox(matrices, queue, solidLayer, x1, sy1, z1, x2, sy2, z2, color, 0, 1);
			matrices.pop();
		}

		// Render fluid (molten) layer with animated texture
		if (state.moltenMl > 0) {
			var fluidLayer = RenderLayers.entitySolid(FLUID_TEXTURE);
			float fy1 = solidTopY;
			float fy2 = fluidTopY;
			int color = state.alloyColor | 0xFF000000;
			matrices.push();
			renderColoredBox(matrices, queue, fluidLayer, x1, fy1, z1, x2, fy2, z2, color, v0, v1);
			matrices.pop();
		}
	}

	private void renderColoredBox(MatrixStack matrices, OrderedRenderCommandQueue queue,
			RenderLayer renderLayer, float x1, float y1, float z1, float x2, float y2, float z2, int color,
			float v0, float v1) {
		queue.submitCustom(matrices, renderLayer, (entry, vertexConsumer) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			int light = 15728880; // fullbright

			// Top face (Y+)
			renderQuad(vertexConsumer, matrix, x1, y2, z1, x2, y2, z2, 0, 1, 0, color, light, v0, v1);
			// Bottom face (Y-)
			renderQuad(vertexConsumer, matrix, x2, y1, z1, x1, y1, z2, 0, -1, 0, color, light, v0, v1);
			// North face (Z-)
			renderQuadVertical(vertexConsumer, matrix, x1, y1, z1, x2, y2, z1, 0, 0, -1, color, light, v0, v1);
			// South face (Z+)
			renderQuadVertical(vertexConsumer, matrix, x2, y1, z2, x1, y2, z2, 0, 0, 1, color, light, v0, v1);
			// West face (X-)
			renderQuadVerticalX(vertexConsumer, matrix, x1, y1, z2, x1, y2, z1, -1, 0, 0, color, light, v0, v1);
			// East face (X+)
			renderQuadVerticalX(vertexConsumer, matrix, x2, y1, z1, x2, y2, z2, 1, 0, 0, color, light, v0, v1);
		});
	}

	// Texture is 16x320 (20 animation frames stacked). One frame = V range [0, 1/20].
	private static final float V_FRAME = 1f / 20f;

	// Horizontal quad (top/bottom face)
	private void renderQuad(VertexConsumer vc, Matrix4f matrix,
			float x1, float y, float z1, float x2, float y2, float z2,
			float nx, float ny, float nz, int color, int light, float v0, float v1) {
		vc.vertex(matrix, x1, y, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x1, y, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
	}

	// Vertical quad on Z axis (north/south face)
	private void renderQuadVertical(VertexConsumer vc, Matrix4f matrix,
			float x1, float y1, float z, float x2, float y2, float z2,
			float nx, float ny, float nz, int color, int light, float v0, float v1) {
		vc.vertex(matrix, x1, y1, z).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x1, y2, z).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
	}

	// Vertical quad on X axis (east/west face)
	private void renderQuadVerticalX(VertexConsumer vc, Matrix4f matrix,
			float x, float y1, float z1, float x2, float y2, float z2,
			float nx, float ny, float nz, int color, int light, float v0, float v1) {
		vc.vertex(matrix, x, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
		vc.vertex(matrix, x, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
	}

	@Override
	public boolean rendersOutsideBoundingBox() {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 256;
	}

	// Render state — copies data from BE for use in render thread
	public static class RenderState extends BlockEntityRenderState {
		public boolean valid;
		public int width, depth, height;
		public int minX, minY, minZ;
		public int maxVolume;
		public int moltenMl;
		public int unmeltedMl;
		public int alloyColor;
		public int unmeltedColor;
		public float animationTime;
	}
}

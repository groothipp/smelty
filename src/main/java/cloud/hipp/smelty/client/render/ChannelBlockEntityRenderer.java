package cloud.hipp.smelty.client.render;

import cloud.hipp.smelty.block.CastingBasinBlock;
import cloud.hipp.smelty.block.CastingTableBlock;
import cloud.hipp.smelty.block.ChannelBlock;
import cloud.hipp.smelty.block.entity.ChannelBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public class ChannelBlockEntityRenderer
		implements BlockEntityRenderer<ChannelBlockEntity, ChannelBlockEntityRenderer.RenderState> {

	private static final Identifier FLUID_TEXTURE = Identifier.ofVanilla("textures/block/smelty_molten_alloy.png");

	private static final float WALL = 2f / 16f;    // wall thickness
	private static final float OVERLAP = 0.005f;  // slight overlap to prevent gaps

	public ChannelBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void updateRenderState(ChannelBlockEntity entity, RenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumbling);

		state.fluidLevel = entity.getFluidLevelMl();
		state.fillRatio = entity.getFillRatio();
		state.color = entity.getColor();

		BlockState blockState = entity.getCachedState();
		state.connNorth = blockState.get(ChannelBlock.NORTH);
		state.connSouth = blockState.get(ChannelBlock.SOUTH);
		state.connEast = blockState.get(ChannelBlock.EAST);
		state.connWest = blockState.get(ChannelBlock.WEST);

		// Query neighbor fill ratios for fluid surface interpolation
		state.neighborFillNorth = -1f;
		state.neighborFillSouth = -1f;
		state.neighborFillEast = -1f;
		state.neighborFillWest = -1f;

		World world = entity.getWorld();
		BlockPos pos = entity.getPos();
		if (world != null) {
			if (state.connNorth && world.getBlockEntity(pos.north()) instanceof ChannelBlockEntity ch)
				state.neighborFillNorth = ch.getFillRatio();
			if (state.connSouth && world.getBlockEntity(pos.south()) instanceof ChannelBlockEntity ch)
				state.neighborFillSouth = ch.getFillRatio();
			if (state.connEast && world.getBlockEntity(pos.east()) instanceof ChannelBlockEntity ch)
				state.neighborFillEast = ch.getFillRatio();
			if (state.connWest && world.getBlockEntity(pos.west()) instanceof ChannelBlockEntity ch)
				state.neighborFillWest = ch.getFillRatio();
		}

		// Compute waterfall depth by checking blocks below
		// Render waterfall when channel has fluid OR is actively flowing downward
		state.waterfallDepth = 0;
		state.targetSurfaceY = 4f / 16f; // default: channel trough bottom
		state.activeDownwardFlow = entity.isActiveDownwardFlow();
		state.flowColor = entity.getFlowColor();
		if (entity.getFluidLevelMl() > 0 || entity.isActiveDownwardFlow()) {
			if (world != null) {
				for (int dy = 1; dy <= 3; dy++) {
					BlockPos below = pos.down(dy);
					Block block = world.getBlockState(below).getBlock();
					if (block instanceof ChannelBlock) {
						state.waterfallDepth = dy;
						state.targetSurfaceY = 4f / 16f; // channel trough bottom
						break;
					} else if (block instanceof CastingBasinBlock) {
						state.waterfallDepth = dy;
						state.targetSurfaceY = 4f / 16f; // basin floor
						break;
					} else if (block instanceof CastingTableBlock) {
						state.waterfallDepth = dy;
						state.targetSurfaceY = 11f / 16f; // table surface
						break;
					}
					if (!world.getBlockState(below).isAir()) break;
				}
			}
		}
	}

	@Override
	public void render(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (state.fluidLevel <= 0 && !state.activeDownwardFlow) return;

		float v0 = 0;
		float v1 = 1;

		// Extend fluid past block edge on connected sides, overlap into wall on closed sides
		float x1 = state.connWest ? -OVERLAP : WALL - OVERLAP;
		float x2 = state.connEast ? 1f + OVERLAP : 1f - WALL + OVERLAP;
		float z1 = state.connNorth ? -OVERLAP : WALL - OVERLAP;
		float z2 = state.connSouth ? 1f + OVERLAP : 1f - WALL + OVERLAP;
		float y1 = 4f / 16f;
		float fluidHeight = 4f / 16f;
		float selfY2 = y1 + state.fillRatio * fluidHeight;

		// Per-corner Y heights: average self level with connected neighbor levels
		float yNW = cornerY(selfY2, state.neighborFillNorth, state.neighborFillWest, y1, fluidHeight);
		float yNE = cornerY(selfY2, state.neighborFillNorth, state.neighborFillEast, y1, fluidHeight);
		float ySE = cornerY(selfY2, state.neighborFillSouth, state.neighborFillEast, y1, fluidHeight);
		float ySW = cornerY(selfY2, state.neighborFillSouth, state.neighborFillWest, y1, fluidHeight);

		int color = state.color | 0xFF000000;
		// For waterfall when channel is empty but actively flowing
		int waterfallColor = (state.fluidLevel > 0 ? state.color : state.flowColor) | 0xFF000000;

		var renderLayer = RenderLayers.entitySolid(FLUID_TEXTURE);
		matrices.push();
		queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			int light = 15728880;

			// Only render trough fluid if there's actual fluid
			if (state.fluidLevel > 0) {
				// Top face (per-corner Y for smooth interpolation between channels)
				vc.vertex(matrix, x1, yNW, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
				vc.vertex(matrix, x1, ySW, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
				vc.vertex(matrix, x2, ySE, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
				vc.vertex(matrix, x2, yNE, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);

				// Bottom face
				vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
				vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
				vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
				vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);

				// Side faces where there's a wall
				if (!state.connNorth) {
					vc.vertex(matrix, x1, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
					vc.vertex(matrix, x2, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
					vc.vertex(matrix, x2, yNE, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
					vc.vertex(matrix, x1, yNW, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
				}

				if (!state.connSouth) {
					vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
					vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
					vc.vertex(matrix, x1, ySW, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
					vc.vertex(matrix, x2, ySE, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
				}

				if (!state.connWest) {
					vc.vertex(matrix, x1, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
					vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
					vc.vertex(matrix, x1, yNW, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
					vc.vertex(matrix, x1, ySW, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
				}

				if (!state.connEast) {
					vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
					vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
					vc.vertex(matrix, x2, ySE, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
					vc.vertex(matrix, x2, yNE, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
				}
			}

			// Waterfall stream — renders when channel has fluid or has active downward flow
			if (state.waterfallDepth > 0 && state.activeDownwardFlow) {
				float sx1 = 6f / 16f, sx2 = 10f / 16f;
				float sz1 = 6f / 16f, sz2 = 10f / 16f;
				float sy2 = OVERLAP; // top: slightly above channel bottom
				float sy1 = -state.waterfallDepth + state.targetSurfaceY - OVERLAP; // bottom: target block surface

				renderStreamDoubleSided(vc, matrix, sx1, sy1, sz1, sx2, sy2, sz2, waterfallColor, light, v0, v1);
			}
		});
		matrices.pop();
	}

	/**
	 * Renders a waterfall stream column with double-sided faces to ensure
	 * visibility from all angles (entitySolid uses face culling).
	 */
	private void renderStreamDoubleSided(VertexConsumer vc, Matrix4f matrix,
			float x1, float y1, float z1, float x2, float y2, float z2, int color, int light,
			float v0, float v1) {
		// Top (both sides)
		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);

		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);

		// Bottom (both sides)
		vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);
		vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, -1, 0);

		vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
		vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);

		// North face (both sides)
		vc.vertex(matrix, x1, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x2, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);

		vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x2, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x1, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);

		// South face (both sides)
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);

		vc.vertex(matrix, x2, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);

		// West face (both sides)
		vc.vertex(matrix, x1, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x1, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);

		vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x1, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x1, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);

		// East face (both sides)
		vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
		vc.vertex(matrix, x2, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);

		vc.vertex(matrix, x2, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
	}

	@Override
	public boolean rendersOutsideBoundingBox() {
		return true;
	}

	/**
	 * Computes a corner's Y height by averaging self level with up to 2 connected neighbor levels.
	 * neighborFillA/B are -1 if no neighbor on that edge, otherwise the neighbor's fill ratio.
	 */
	private static float cornerY(float selfY, float neighborFillA, float neighborFillB,
								  float baseY, float fluidHeight) {
		float sum = selfY;
		int count = 1;
		if (neighborFillA >= 0) { sum += baseY + neighborFillA * fluidHeight; count++; }
		if (neighborFillB >= 0) { sum += baseY + neighborFillB * fluidHeight; count++; }
		return Math.max(sum / count, baseY);
	}

	public static class RenderState extends BlockEntityRenderState {
		public int fluidLevel;
		public float fillRatio;
		public int color;
		public boolean connNorth, connSouth, connEast, connWest;
		public float neighborFillNorth = -1f, neighborFillSouth = -1f;
		public float neighborFillEast = -1f, neighborFillWest = -1f;
		public int waterfallDepth; // 0 = none, 1-3 = blocks to target
		public float targetSurfaceY; // target block's fluid surface Y (in block-local coords)
		public boolean activeDownwardFlow;
		public int flowColor;
	}
}

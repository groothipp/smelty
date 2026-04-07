package cloud.hipp.smelty.client.render;

import cloud.hipp.smelty.block.CastingBasinBlock;
import cloud.hipp.smelty.block.CastingTableBlock;
import cloud.hipp.smelty.block.ChannelBlock;
import cloud.hipp.smelty.block.ValveBlock;
import cloud.hipp.smelty.block.entity.ValveBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayers;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public class ValveBlockEntityRenderer
		implements BlockEntityRenderer<ValveBlockEntity, ValveBlockEntityRenderer.RenderState> {

	private static final Identifier FLUID_TEXTURE = Identifier.ofVanilla("textures/block/smelty_molten_alloy.png");
	private static final float OVERLAP = 0.005f;

	public ValveBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void updateRenderState(ValveBlockEntity entity, RenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumbling);

		BlockState blockState = entity.getCachedState();
		if (blockState.getBlock() instanceof ValveBlock) {
			state.facing = blockState.get(ValveBlock.FACING);
		} else {
			state.facing = Direction.NORTH;
		}

		// Compute waterfall depth when actively flowing down
		state.waterfallDepth = 0;
		state.activeDownwardFlow = entity.isActiveDownwardFlow();
		state.flowColor = entity.getFlowColor();
		if (entity.isActiveDownwardFlow()) {
			World world = entity.getWorld();
			if (world != null) {
				BlockPos pos = entity.getPos();
				for (int dy = 1; dy <= 3; dy++) {
					BlockPos below = pos.down(dy);
					Block block = world.getBlockState(below).getBlock();
					if (block instanceof ChannelBlock || block instanceof CastingBasinBlock || block instanceof CastingTableBlock) {
						state.waterfallDepth = dy;
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
		if (!state.activeDownwardFlow) return;

		float v0 = 0;
		float v1 = 1;

		// Stream position at the nozzle opening
		// Nozzle model coords (facing=north): [5,4,8]-[11,9,11], stream at center-bottom
		float x1, x2, z1, z2;
		switch (state.facing) {
			case NORTH -> { x1 = 7f/16; x2 = 9f/16; z1 = 8f/16; z2 = 10f/16; }
			case SOUTH -> { x1 = 7f/16; x2 = 9f/16; z1 = 6f/16; z2 = 8f/16; }
			case EAST  -> { x1 = 6f/16; x2 = 8f/16; z1 = 7f/16; z2 = 9f/16; }
			case WEST  -> { x1 = 8f/16; x2 = 10f/16; z1 = 7f/16; z2 = 9f/16; }
			default -> { return; }
		}

		// Stream height: from nozzle down to target (or block floor if no target)
		float y2 = 5f / 16f; // top: inside nozzle
		float y1;
		if (state.waterfallDepth > 0) {
			y1 = -state.waterfallDepth + 4f / 16f - OVERLAP; // bottom: into target trough
		} else {
			y1 = 0; // bottom: block floor
		}

		int color = state.flowColor | 0xFF000000;

		var renderLayer = RenderLayers.entitySolid(FLUID_TEXTURE);
		matrices.push();
		queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			int light = 15728880;

			// Double-sided rendering for waterfall stream visibility from all angles

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

			// North (both sides)
			vc.vertex(matrix, x1, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
			vc.vertex(matrix, x2, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
			vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
			vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);

			vc.vertex(matrix, x1, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
			vc.vertex(matrix, x2, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
			vc.vertex(matrix, x2, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
			vc.vertex(matrix, x1, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);

			// South (both sides)
			vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
			vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
			vc.vertex(matrix, x1, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
			vc.vertex(matrix, x2, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);

			vc.vertex(matrix, x2, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
			vc.vertex(matrix, x1, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
			vc.vertex(matrix, x1, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);
			vc.vertex(matrix, x2, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, -1);

			// West (both sides)
			vc.vertex(matrix, x1, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
			vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
			vc.vertex(matrix, x1, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
			vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);

			vc.vertex(matrix, x1, y2, z2).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
			vc.vertex(matrix, x1, y2, z1).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
			vc.vertex(matrix, x1, y1, z1).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
			vc.vertex(matrix, x1, y1, z2).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);

			// East (both sides)
			vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
			vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
			vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);
			vc.vertex(matrix, x2, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(1, 0, 0);

			vc.vertex(matrix, x2, y2, z1).color(color).texture(0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
			vc.vertex(matrix, x2, y2, z2).color(color).texture(1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
			vc.vertex(matrix, x2, y1, z2).color(color).texture(1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
			vc.vertex(matrix, x2, y1, z1).color(color).texture(0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(-1, 0, 0);
		});
		matrices.pop();
	}

	@Override
	public boolean rendersOutsideBoundingBox() {
		return true;
	}

	public static class RenderState extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public int waterfallDepth; // 0 = none, 1-3 = blocks to target
		public boolean activeDownwardFlow;
		public int flowColor;
	}
}

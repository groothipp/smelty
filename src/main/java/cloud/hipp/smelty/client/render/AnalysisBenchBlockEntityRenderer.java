package cloud.hipp.smelty.client.render;

import cloud.hipp.smelty.block.entity.AnalysisBenchBlockEntity;
import cloud.hipp.smelty.item.SmeltyItems;
import cloud.hipp.smelty.material.SmeltyMaterial;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class AnalysisBenchBlockEntityRenderer
		implements BlockEntityRenderer<AnalysisBenchBlockEntity, AnalysisBenchBlockEntityRenderer.RenderState> {

	// Plate area on bench surface
	private static final float PX1 = 3f / 16f, PZ1 = 3f / 16f;
	private static final float PX2 = 13f / 16f, PZ2 = 13f / 16f;
	private static final float PLATE_Y = 12.01f / 16f;

	public AnalysisBenchBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void updateRenderState(AnalysisBenchBlockEntity entity, RenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumbling);

		state.plateTexture = null;
		state.plateColor = 0xFFFFFFFF;

		state.modifierFlags = null;
		state.animationTime = entity.getWorld() != null ? entity.getWorld().getTime() + tickDelta : 0;

		if (entity.hasPlate()) {
			ItemStack plate = entity.getPlateItem();
			state.plateTexture = getPlateTexture(plate);
			state.plateColor = getPlateColor(plate);
			if (plate.getItem() == SmeltyItems.ALLOY_PLATE) {
				CustomModelDataComponent cmd = plate.get(DataComponentTypes.CUSTOM_MODEL_DATA);
				if (cmd != null && !cmd.flags().isEmpty()) {
					state.modifierFlags = cmd.flags();
				}
			}
		}
	}

	private static Identifier getPlateTexture(ItemStack plate) {
		Item item = plate.getItem();
		if (item == SmeltyItems.COPPER_PLATE) return Identifier.ofVanilla("textures/block/copper_block.png");
		if (item == SmeltyItems.IRON_PLATE) return Identifier.ofVanilla("textures/block/iron_block.png");
		if (item == SmeltyItems.GOLD_PLATE) return Identifier.ofVanilla("textures/block/gold_block.png");
		if (item == SmeltyItems.DIAMOND_PLATE) return Identifier.ofVanilla("textures/block/diamond_block.png");
		if (item == SmeltyItems.NETHERITE_PLATE) return Identifier.ofVanilla("textures/block/netherite_block.png");
		if (item == SmeltyItems.OBSIDIAN_PLATE) return Identifier.ofVanilla("textures/block/obsidian.png");
		if (item == SmeltyItems.EMERALD_PLATE) return Identifier.ofVanilla("textures/block/emerald_block.png");
		if (item == SmeltyItems.ALLOY_PLATE) return Identifier.ofVanilla("textures/block/iron_block.png");
		return null;
	}

	private static int getPlateColor(ItemStack plate) {
		if (plate.getItem() == SmeltyItems.ALLOY_PLATE) {
			CustomModelDataComponent cmd = plate.get(DataComponentTypes.CUSTOM_MODEL_DATA);
			if (cmd != null) {
				Integer color = cmd.getColor(0);
				if (color != null) return color | 0xFF000000;
			}
		}
		return 0xFFFFFFFF;
	}

	@Override
	public void render(RenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (state.plateTexture == null) return;

		var renderLayer = RenderLayers.entityCutout(state.plateTexture);
		int color = state.plateColor;
		matrices.push();
		queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
			Matrix4f matrix = entry.getPositionMatrix();
			vc.vertex(matrix, PX1, PLATE_Y, PZ1).color(color).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
			vc.vertex(matrix, PX1, PLATE_Y, PZ2).color(color).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
			vc.vertex(matrix, PX2, PLATE_Y, PZ2).color(color).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
			vc.vertex(matrix, PX2, PLATE_Y, PZ1).color(color).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
		});
		matrices.pop();

		// Render modifier overlays
		if (state.modifierFlags != null) {
			float overlayY = PLATE_Y + 0.001f;
			for (int i = 0; i < state.modifierFlags.size()
					&& i < CastingTableBlockEntityRenderer.MODIFIER_NAMES.length; i++) {
				if (state.modifierFlags.get(i)) {
					Identifier overlayTex = Identifier.of("smelty",
							"textures/item/overlay/plate_"
							+ CastingTableBlockEntityRenderer.MODIFIER_NAMES[i] + ".png");
					int frameCount = CastingTableBlockEntityRenderer.MODIFIER_FRAMES[i];
					float ov0, ov1;
					if (frameCount <= 1) {
						ov0 = 0; ov1 = 1;
					} else {
						int frametime = CastingTableBlockEntityRenderer.MODIFIER_FRAMETIME[i];
						int frame = ((int) state.animationTime / frametime) % frameCount;
						ov0 = (float) frame / frameCount;
						ov1 = (float) (frame + 1) / frameCount;
					}
					final float fov0 = ov0, fov1 = ov1;
					var overlayLayer = RenderLayers.entityCutout(overlayTex);
					matrices.push();
					queue.submitCustom(matrices, overlayLayer, (entry, vc) -> {
						Matrix4f matrix = entry.getPositionMatrix();
						vc.vertex(matrix, PX1, overlayY, PZ1).color(0xFFFFFFFF).texture(0, fov0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
						vc.vertex(matrix, PX1, overlayY, PZ2).color(0xFFFFFFFF).texture(0, fov1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
						vc.vertex(matrix, PX2, overlayY, PZ2).color(0xFFFFFFFF).texture(1, fov1).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
						vc.vertex(matrix, PX2, overlayY, PZ1).color(0xFFFFFFFF).texture(1, fov0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
					});
					matrices.pop();
				}
			}
		}
	}

	public static class RenderState extends BlockEntityRenderState {
		public Identifier plateTexture;
		public int plateColor;
		public List<Boolean> modifierFlags;
		public float animationTime;
	}
}

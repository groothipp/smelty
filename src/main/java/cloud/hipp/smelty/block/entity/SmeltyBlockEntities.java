package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.block.SmeltyBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class SmeltyBlockEntities {
	public static final BlockEntityType<SmelterControllerBlockEntity> SMELTER_CONTROLLER =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(Smelty.MOD_ID, "smelter_controller"),
					FabricBlockEntityTypeBuilder.create(SmelterControllerBlockEntity::new, SmeltyBlocks.SMELTER_CONTROLLER).build()
			);

	public static void initialize() {
	}
}

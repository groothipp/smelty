package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registers all block entity types for the mod.
 *
 * A BlockEntityType is a "factory registration" that tells Minecraft:
 *   "Here's how to create a SmelterCoreBlockEntity, and it's valid on SMELTER_CORE blocks."
 *
 * We use FabricBlockEntityTypeBuilder (from Fabric API) because vanilla's
 * BlockEntityType.Builder.create() is private in 1.21.11.
 * Fabric's builder provides the same functionality with a public API.
 */
public class ModBlockEntities {

    public static final BlockEntityType<SmelterCoreBlockEntity> SMELTER_CORE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Smelty.MOD_ID, "smelter_core"),
            FabricBlockEntityTypeBuilder.create(SmelterCoreBlockEntity::new, ModBlocks.SMELTER_CORE).build()
    );

    public static void initialize() {
        Smelty.LOGGER.info("Registering Smelty block entities...");
    }
}

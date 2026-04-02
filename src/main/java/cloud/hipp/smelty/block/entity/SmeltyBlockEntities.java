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

	public static final BlockEntityType<ChannelBlockEntity> CHANNEL =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(Smelty.MOD_ID, "channel"),
					FabricBlockEntityTypeBuilder.create(ChannelBlockEntity::new, SmeltyBlocks.CHANNEL).build()
			);

	public static final BlockEntityType<CastingBasinBlockEntity> CASTING_BASIN =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(Smelty.MOD_ID, "casting_basin"),
					FabricBlockEntityTypeBuilder.create(CastingBasinBlockEntity::new, SmeltyBlocks.CASTING_BASIN).build()
			);

	public static final BlockEntityType<CastingTableBlockEntity> CASTING_TABLE =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(Smelty.MOD_ID, "casting_table"),
					FabricBlockEntityTypeBuilder.create(CastingTableBlockEntity::new, SmeltyBlocks.CASTING_TABLE).build()
			);

	public static final BlockEntityType<ValveBlockEntity> VALVE =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(Smelty.MOD_ID, "valve"),
					FabricBlockEntityTypeBuilder.create(ValveBlockEntity::new, SmeltyBlocks.VALVE).build()
			);

	public static final BlockEntityType<AnalysisBenchBlockEntity> ANALYSIS_BENCH =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(Smelty.MOD_ID, "analysis_bench"),
					FabricBlockEntityTypeBuilder.create(AnalysisBenchBlockEntity::new, SmeltyBlocks.ANALYSIS_BENCH).build()
			);

	public static final BlockEntityType<SolidAlloyBlockEntity> SOLID_ALLOY =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(Smelty.MOD_ID, "solid_alloy"),
					FabricBlockEntityTypeBuilder.create(SolidAlloyBlockEntity::new, SmeltyBlocks.SOLID_ALLOY).build()
			);

	public static void initialize() {
	}
}

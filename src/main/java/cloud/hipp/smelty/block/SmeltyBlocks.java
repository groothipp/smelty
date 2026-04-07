package cloud.hipp.smelty.block;

import cloud.hipp.smelty.Smelty;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class SmeltyBlocks {
	public static final Block SMELTER_BLOCK = register("smelter_block",
			SmelterBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.strength(3.5F, 6.0F)
					.sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
					.requiresTool()
	);

	public static final Block VALVE = register("valve",
			ValveBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.strength(3.5F, 6.0F)
					.sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
					.requiresTool()
					.nonOpaque()
					.solidBlock((state, world, pos) -> false)
					.suffocates((state, world, pos) -> false)
					.blockVision((state, world, pos) -> false)
	);

	public static final Block SMELTER_CONTROLLER = register("smelter_controller",
			SmelterControllerBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.strength(3.5F, 6.0F)
					.sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
					.requiresTool()
					.luminance(state -> state.get(SmelterControllerBlock.LIT) ? 15 : 0)
	);

	public static final Block CHANNEL = register("channel",
			ChannelBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.strength(2.0F, 6.0F)
					.sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
					.requiresTool()
					.nonOpaque()
					.solidBlock((state, world, pos) -> false)
	);

	public static final Block CASTING_BASIN = register("casting_basin",
			CastingBasinBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.strength(2.0F, 6.0F)
					.sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
					.requiresTool()
					.nonOpaque()
					.solidBlock((state, world, pos) -> false)
	);

	public static final Block CASTING_TABLE = register("casting_table",
			CastingTableBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.strength(2.0F, 6.0F)
					.sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
					.requiresTool()
					.nonOpaque()
					.solidBlock((state, world, pos) -> false)
	);

	public static final Block ANALYSIS_BENCH = register("analysis_bench",
			AnalysisBenchBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.OAK_TAN)
					.strength(2.0F, 3.0F)
					.sounds(BlockSoundGroup.WOOD)
					.nonOpaque()
					.solidBlock((state, world, pos) -> false)
					.luminance(state -> state.get(AnalysisBenchBlock.HAS_PLATE) ? 10 : 0)
	);

	public static final Block CASTED_DIAMOND_BLOCK = register("casted_diamond_block",
			Block::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DIAMOND_BLUE)
					.strength(5.0F, 6.0F)
					.sounds(BlockSoundGroup.METAL)
					.requiresTool()
	);

	public static final Block CASTED_EMERALD_BLOCK = register("casted_emerald_block",
			Block::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.EMERALD_GREEN)
					.strength(5.0F, 6.0F)
					.sounds(BlockSoundGroup.METAL)
					.requiresTool()
	);

	public static final Block SOLID_ALLOY = register("solid_alloy",
			SolidAlloyBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.GRAY)
					.strength(2.0F, 6.0F)
					.sounds(BlockSoundGroup.METAL)
					.requiresTool()
					.dropsNothing() // We handle drops manually in onStateReplaced
	);

	private static Block register(String id, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(Smelty.MOD_ID, id));
		Block block = factory.apply(settings.registryKey(key));
		return Registry.register(Registries.BLOCK, key, block);
	}

	public static void initialize() {
	}
}

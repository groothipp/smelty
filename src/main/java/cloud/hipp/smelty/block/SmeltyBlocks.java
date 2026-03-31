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

	public static final Block SMELTER_CONTROLLER = register("smelter_controller",
			SmelterControllerBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.strength(3.5F, 6.0F)
					.sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
					.requiresTool()
	);

	private static Block register(String id, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(Smelty.MOD_ID, id));
		Block block = factory.apply(settings.registryKey(key));
		return Registry.register(Registries.BLOCK, key, block);
	}

	public static void initialize() {
	}
}

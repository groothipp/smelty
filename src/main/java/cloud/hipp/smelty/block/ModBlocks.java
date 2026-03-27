package cloud.hipp.smelty.block;

import cloud.hipp.smelty.Smelty;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ModBlocks {

    // ── Blocks ────────────────────────────────────────────────────────

    /**
     * The Smelter Core — an invisible, non-solid block placed inside the structure.
     *
     * Settings explained:
     *   .noCollision()  — players and items pass through it (essential for item dropping!)
     *   .dropsNothing() — doesn't drop anything when broken (it's invisible, no loot needed)
     *   .nonOpaque()    — doesn't block light or rendering of adjacent blocks
     *   .strength(-1)   — unbreakable by normal means (only removed programmatically)
     *                     -1 hardness = same as bedrock, prevents players from punching it out
     *   .registryKey()  — required in modern MC, links settings to the registry entry
     */
    public static final Block SMELTER_CORE = registerBlockNoItem(
            "smelter_core",
            SmelterCoreBlock::new,
            AbstractBlock.Settings.create()
                    .noCollision()
                    .dropsNothing()
                    .nonOpaque()
                    .strength(-1.0f, 3600000.0f) // unbreakable like bedrock
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(Smelty.MOD_ID, "smelter_core")))
    );

    // ── Registration Helpers ──────────────────────────────────────────

    /**
     * Registers a block WITH a BlockItem (for blocks players can hold/place).
     * We'll use this later for things like the Casting Table.
     */
    private static Block registerBlock(String name, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        Identifier id = Identifier.of(Smelty.MOD_ID, name);
        Block block = Registry.register(Registries.BLOCK, id, factory.apply(settings));

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings itemSettings = new Item.Settings()
                .useBlockPrefixedTranslationKey()
                .registryKey(itemKey);
        Registry.register(Registries.ITEM, id, new BlockItem(block, itemSettings));

        return block;
    }

    /**
     * Registers a block WITHOUT a BlockItem.
     * Used for blocks that shouldn't appear in inventories — like our invisible core.
     * Players never "hold" or "place" the core; it's placed programmatically.
     */
    private static Block registerBlockNoItem(String name, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        Identifier id = Identifier.of(Smelty.MOD_ID, name);
        return Registry.register(Registries.BLOCK, id, factory.apply(settings));
    }

    public static void initialize() {
        Smelty.LOGGER.info("Registering Smelty blocks...");
    }
}

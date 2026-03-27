package cloud.hipp.smelty.tag;

import cloud.hipp.smelty.Smelty;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Holds references to our custom tags.
 *
 * A "tag" in Minecraft is a named group of blocks/items defined in JSON.
 * For example, minecraft:logs includes oak_log, birch_log, etc.
 *
 * We don't define the contents here — just the reference (like a variable name).
 * The actual contents are in the JSON file at:
 *   data/smelty/tags/block/smelter_wall.json
 *
 * TagKey is essentially just an Identifier that points to a tag file.
 * When we check block.isIn(tag), Minecraft looks up the tag and checks membership.
 */
public class ModTags {

    public static class Blocks {
        /**
         * Any block in this tag can be used as a wall for the smelter multiblock.
         * This lets us support stone, cobblestone, deepslate, bricks, etc.
         * Players can even mix and match!
         */
        public static final TagKey<Block> SMELTER_WALL = TagKey.of(
                RegistryKeys.BLOCK,
                Identifier.of(Smelty.MOD_ID, "smelter_wall")
        );

        /**
         * Valid heat sources for the smelter (campfire, soul campfire, lava).
         * We make this a tag too so it's easy to extend later (maybe modded heat sources).
         */
        public static final TagKey<Block> HEAT_SOURCE = TagKey.of(
                RegistryKeys.BLOCK,
                Identifier.of(Smelty.MOD_ID, "heat_source")
        );
    }
}

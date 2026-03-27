package cloud.hipp.smelty.component;

import cloud.hipp.smelty.Smelty;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Custom data components for our items.
 *
 * In 1.21+, Minecraft moved away from NBT tags on items to a "component" system.
 * Components are typed, validated pieces of data attached to an ItemStack.
 * Think of them like strongly-typed fields instead of a loose JSON bag.
 *
 * For example, vanilla uses components for:
 *   - DAMAGE (integer) — how damaged a tool is
 *   - CUSTOM_NAME (Text) — renamed items
 *   - FOOD (FoodComponent) — edible item properties
 *
 * We create HEAT_REMAINING — an integer tracking how many ticks of heat are left.
 * When it reaches 0, the heated ingot cools into a regular ingot.
 */
public class ModComponents {

    /**
     * Tracks remaining heat ticks on a heated ingot.
     *
     * ComponentType.builder() creates the type definition:
     *   .codec(Codec.INT) — how to save/load from disk (serialize an integer)
     *   .packetCodec(PacketCodecs.VAR_INT) — how to send over network (client↔server sync)
     *   .build() — finalize the type
     *
     * We then register it in the DATA_COMPONENT_TYPE registry so Minecraft knows about it.
     */
    public static final ComponentType<Integer> HEAT_REMAINING = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Smelty.MOD_ID, "heat_remaining"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.VAR_INT)
                    .build()
    );

    public static void initialize() {
        Smelty.LOGGER.info("Registering Smelty components...");
    }
}

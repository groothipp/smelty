package cloud.hipp.smelty.item;

import cloud.hipp.smelty.Smelty;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Registers all custom items for the mod.
 *
 * Heated ingots use the DURABILITY system as a heat gauge:
 *   - maxDamage(1200) = 60 seconds of heat, stack size 1
 *   - Each tick, damage increments by 1
 *   - The durability bar shows remaining heat
 *   - The texture changes through 5 stages via range_dispatch in item JSON
 */
public class ModItems {

    // ── Heated Ingots ─────────────────────────────────────────────────

    public static final Item HEATED_IRON_INGOT = registerItem(
            "heated_iron_ingot",
            settings -> new HeatedIngotItem(settings, Items.IRON_INGOT),
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, "heated_iron_ingot")))
                    .useItemPrefixedTranslationKey()
                    .maxDamage(HeatedIngotItem.MAX_HEAT_TICKS) // durability = heat gauge
    );

    public static final Item HEATED_GOLD_INGOT = registerItem(
            "heated_gold_ingot",
            settings -> new HeatedIngotItem(settings, Items.GOLD_INGOT),
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, "heated_gold_ingot")))
                    .useItemPrefixedTranslationKey()
                    .maxDamage(HeatedIngotItem.MAX_HEAT_TICKS)
    );

    public static final Item HEATED_COPPER_INGOT = registerItem(
            "heated_copper_ingot",
            settings -> new HeatedIngotItem(settings, Items.COPPER_INGOT),
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, "heated_copper_ingot")))
                    .useItemPrefixedTranslationKey()
                    .maxDamage(HeatedIngotItem.MAX_HEAT_TICKS)
    );

    // ── Tools ───────────────────────────────────────────────────────

    public static final Item HAMMER = registerItem(
            "hammer",
            HammerItem::new,
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Smelty.MOD_ID, "hammer")))
                    .useItemPrefixedTranslationKey()
                    .maxCount(1)
    );

    // ── Registration Helper ───────────────────────────────────────────

    private static Item registerItem(String name, java.util.function.Function<Item.Settings, Item> factory, Item.Settings settings) {
        Identifier id = Identifier.of(Smelty.MOD_ID, name);
        return Registry.register(Registries.ITEM, id, factory.apply(settings));
    }

    public static void initialize() {
        Smelty.LOGGER.info("Registering Smelty items...");

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(HEATED_IRON_INGOT);
            entries.add(HEATED_GOLD_INGOT);
            entries.add(HEATED_COPPER_INGOT);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(HAMMER);
        });
    }
}

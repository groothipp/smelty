package cloud.hipp.smelty.item;

import cloud.hipp.smelty.Smelty;

/**
 * This class will hold all custom item registrations (molten metals, casts, etc).
 * For now it's empty — our only item is the BlockItem for Smelter Brick,
 * which is auto-registered in ModBlocks.
 *
 * We'll populate this later when we add molten metal items and cast molds.
 */
public class ModItems {

    public static void initialize() {
        Smelty.LOGGER.info("Registering Smelty items...");
    }
}

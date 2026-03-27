package cloud.hipp.smelty.item;

import net.minecraft.item.Item;

/**
 * The Smelty Hammer — used to strike the crafting anvil and forge tools.
 *
 * For now it's a simple item with no special behavior — the crafting logic
 * is handled by the anvil's AttackBlockCallback in Smelty.java.
 * The hammer just needs to exist so we can check if the player is holding it.
 *
 * Later we could add durability so the hammer wears out over time.
 */
public class HammerItem extends Item {

    public HammerItem(Settings settings) {
        super(settings);
    }
}

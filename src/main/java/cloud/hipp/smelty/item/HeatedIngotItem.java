package cloud.hipp.smelty.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;


/**
 * A heated ingot — the output of the smelter.
 *
 * Uses the DURABILITY system as a heat gauge:
 *   - Full durability (damage=0) = freshly heated, bright red-orange
 *   - Depleted durability (damage=max) = cooled down, transforms to regular ingot
 *
 * The damage bar at the bottom of the item serves as a visual heat indicator!
 * The item texture also changes through 5 stages (via range_dispatch in the item JSON)
 * from red-hot → orange → warm → barely warm → almost original color.
 *
 * MAX_DAMAGE = 1200 ticks = 60 seconds of heat.
 * Each tick, damage increases by 1.
 */
public class HeatedIngotItem extends Item {

    /** Total heat ticks before cooling. 60 seconds = 1200 ticks. */
    public static final int MAX_HEAT_TICKS = 1200;

    /** The regular ingot this transforms into when cooled. */
    private final Item cooledItem;

    public HeatedIngotItem(Settings settings, Item cooledItem) {
        super(settings);
        this.cooledItem = cooledItem;
    }

    /**
     * Called every tick for each stack in a player's inventory (server-side).
     *
     * We increment damage by 1 each tick. When damage reaches maxDamage,
     * the ingot has fully cooled — we replace it with the regular ingot.
     *
     * Because we use maxDamage(MAX_HEAT_TICKS), each item has stack size 1.
     * This means each ingot cools independently, which makes sense —
     * an ingot you picked up 30 seconds ago shouldn't cool at the same rate
     * as one you just grabbed.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        int damage = stack.getDamage();
        int maxDamage = stack.getMaxDamage();

        if (damage >= maxDamage - 1) {
            // Cooled down! Transform to regular ingot.
            if (entity instanceof PlayerEntity player) {
                ItemStack cooledStack = new ItemStack(cooledItem, 1);
                stack.setCount(0);

                if (!player.getInventory().insertStack(cooledStack)) {
                    player.dropItem(cooledStack, false);
                }
            }
            return;
        }

        // Increment damage = decrement heat
        stack.setDamage(damage + 1);
    }

    /**
     * Enchantment shimmer to show the item is special/hot.
     */
    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    public Item getCooledItem() {
        return cooledItem;
    }
}

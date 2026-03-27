package cloud.hipp.smelty.item;

import cloud.hipp.smelty.component.ModComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * A heated ingot — the output of the smelter.
 *
 * Uses HEAT_REMAINING component to track cooling.
 * The whole stack cools together — items from the same batch have
 * identical heat values, so they stack naturally.
 *
 * Visual heat stages are driven by CUSTOM_MODEL_DATA float[0],
 * which range_dispatch in the item JSON reads to select textures.
 *
 * MAX_HEAT_TICKS = 1200 ticks = 60 seconds.
 */
public class HeatedIngotItem extends Item {

    public static final int MAX_HEAT_TICKS = 1200;

    private final Item cooledItem;

    public HeatedIngotItem(Settings settings, Item cooledItem) {
        super(settings);
        this.cooledItem = cooledItem;
    }

    /**
     * Called every tick for each stack in a player's inventory (server-side).
     * Decrements heat for the whole stack at once. When heat runs out,
     * the entire stack transforms into regular ingots.
     *
     * Also updates CUSTOM_MODEL_DATA float[0] with the heat fraction
     * (0.0 = hottest, 1.0 = fully cooled) so the item JSON's
     * range_dispatch can select the appropriate texture stage.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        int remaining = stack.getOrDefault(ModComponents.HEAT_REMAINING, 0);

        if (remaining <= 0) {
            // Cooled down! Transform the whole stack to regular ingots.
            if (entity instanceof PlayerEntity player) {
                int count = stack.getCount();
                stack.setCount(0);

                ItemStack cooledStack = new ItemStack(cooledItem, count);
                if (!player.getInventory().insertStack(cooledStack)) {
                    player.dropItem(cooledStack, false);
                }
            }
            return;
        }

        // Decrement heat (whole stack cools together)
        remaining--;
        stack.set(ModComponents.HEAT_REMAINING, remaining);

        // Update visual heat stage via custom model data
        // float[0] = heat fraction: 0.0 = hottest, 1.0 = fully cooled
        float heatFraction = 1.0f - ((float) remaining / MAX_HEAT_TICKS);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
                new CustomModelDataComponent(
                        List.of(heatFraction),  // floats
                        List.of(),              // flags
                        List.of(),              // strings
                        List.of()               // colors
                ));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    public Item getCooledItem() {
        return cooledItem;
    }
}

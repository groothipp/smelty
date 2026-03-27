package cloud.hipp.smelty.mixin;

import cloud.hipp.smelty.item.HeatedIngotItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ItemEntity to handle water quenching of heated ingots.
 *
 * A Mixin is Fabric's way of modifying vanilla code without replacing the whole class.
 * We "inject" our code into ItemEntity.tick() — every tick, if the item entity
 * is a heated ingot and it's touching water, we quench it into a regular ingot.
 *
 * This gives a satisfying "throw hot ingots in water to cool them" mechanic.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Shadow
    public abstract ItemStack getStack();

    @Shadow
    public abstract void setStack(ItemStack stack);

    /**
     * Injected at the end of ItemEntity.tick().
     * If this item entity is a heated ingot and it's submerged in water, quench it.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void smelty$onTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;

        // Only on server
        if (self.getEntityWorld().isClient()) return;

        // Check if the item is a heated ingot
        ItemStack stack = getStack();
        if (!(stack.getItem() instanceof HeatedIngotItem heatedIngot)) return;

        // Check if the item is in water (isTouchingWater checks the entity's bounding box)
        if (!self.isTouchingWater()) return;

        // ── Quench! ──────────────────────────────────────────────────
        // Replace the heated ingot with the cooled version
        Item cooledItem = heatedIngot.getCooledItem();
        ItemStack cooledStack = new ItemStack(cooledItem, stack.getCount());
        setStack(cooledStack);

        // Steam particles — hot metal hitting water!
        if (self.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.CLOUD,
                    self.getX(), self.getY() + 0.2, self.getZ(),
                    10, 0.2, 0.2, 0.2, 0.02
            );
        }

        // Sizzle sound
        self.getEntityWorld().playSound(null, self.getBlockPos(),
                SoundEvents.BLOCK_LAVA_EXTINGUISH,
                SoundCategory.BLOCKS, 0.6f, 2.0f);
    }
}

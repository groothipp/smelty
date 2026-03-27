package cloud.hipp.smelty.block.entity;

import cloud.hipp.smelty.Smelty;
import cloud.hipp.smelty.item.HeatedIngotItem;
import cloud.hipp.smelty.item.ModItems;
import cloud.hipp.smelty.recipe.AnvilRecipes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Crafting Anvil.
 *
 * Stores:
 *   - stickCount: how many sticks are placed (0-2)
 *   - materialCount: how many material items are stacked (0-3)
 *   - materialType: which material is being used (all must be the same type)
 *
 * The anvil acts like a simple 2-slot crafting surface:
 *   Left side  = sticks (handle material)
 *   Right side = materials (tool head — heated ingots, diamonds, planks, etc.)
 */
public class CraftingAnvilBlockEntity extends BlockEntity {

    private int stickCount = 0;
    private int materialCount = 0;
    @Nullable private Item materialType = null;

    private static final int MAX_STICKS = 2;
    private static final int MAX_MATERIALS = 8; // up to 8 for chestplate

    public CraftingAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFTING_ANVIL, pos, state);
    }

    // ── Interactions ──────────────────────────────────────────────────

    /**
     * Player right-clicks with an item. Returns true if we handled it.
     */
    public boolean tryAddItem(PlayerEntity player, ItemStack stack, World world) {
        if (stack.isOf(Items.STICK)) {
            if (stickCount >= MAX_STICKS) {
                return false;
            }
            stickCount++;
            if (!player.isCreative()) stack.decrement(1);
            syncToClient();
            sendStatus(player);

            world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE,
                    SoundCategory.BLOCKS, 0.7f, 1.2f);
            return true;
        }

        // Check if this item is a valid anvil material
        if (AnvilRecipes.isValidMaterial(stack.getItem())) {
            if (materialCount >= MAX_MATERIALS) {
                return false;
            }
            // All materials must be the same type
            if (materialType != null && !stack.isOf(materialType)) {
                return false;
            }

            materialType = stack.getItem();
            materialCount++;
            if (!player.isCreative()) stack.decrement(1);
            syncToClient();
            sendStatus(player);

            world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE,
                    SoundCategory.BLOCKS, 0.3f, 1.5f);
            return true;
        }

        return false;
    }

    /**
     * Player right-clicks with empty hand — remove the last placed item.
     */
    public boolean tryRemoveItem(PlayerEntity player, World world) {
        if (materialCount > 0) {
            materialCount--;
            if (!player.isCreative()) {
                giveOrDrop(player, new ItemStack(materialType, 1));
            }
            if (materialCount == 0) materialType = null;
            syncToClient();
            sendStatus(player);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.BLOCKS, 0.5f, 1.0f);
            return true;
        }

        if (stickCount > 0) {
            stickCount--;
            if (!player.isCreative()) {
                giveOrDrop(player, new ItemStack(Items.STICK, 1));
            }
            syncToClient();
            sendStatus(player);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.BLOCKS, 0.5f, 1.0f);
            return true;
        }

        return false;
    }

    /**
     * Player left-clicks with the hammer (iron pickaxe) — attempt to craft!
     */
    public boolean tryCraft(PlayerEntity player, World world) {
        if (stickCount == 0 && materialCount == 0) {
            return false;
        }

        if (materialType == null) {
            return true;
        }

        // Look up the output based on stick count + material count + material type
        Item output = AnvilRecipes.findOutput(stickCount, materialCount, materialType);

        if (output == null) {
            return true;
        }

        // ── CRAFT! ───────────────────────────────────────────────────
        // Remember material before clearing
        Item craftedMaterial = materialType;

        stickCount = 0;
        materialCount = 0;
        materialType = null;
        syncToClient();

        ItemStack result = new ItemStack(output, 1);
        giveOrDrop(player, result);

        // Anvil strike sound
        world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Sparks colored by material!
        if (world instanceof ServerWorld serverWorld) {
            int color = getMaterialParticleColor(craftedMaterial);

            // Colored dust particles — material sparks flying off
            serverWorld.spawnParticles(
                    new DustParticleEffect(color, 1.0f),
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    20, 0.3, 0.15, 0.3, 0.08
            );

            // Crit sparkles
            serverWorld.spawnParticles(
                    ParticleTypes.CRIT,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    10, 0.3, 0.2, 0.3, 0.1
            );

            // Small flame burst for heated metals
            if (craftedMaterial instanceof HeatedIngotItem) {
                serverWorld.spawnParticles(
                        ParticleTypes.FLAME,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        8, 0.2, 0.1, 0.2, 0.03
                );
            }
        }


        Smelty.LOGGER.info("Player {} forged {}", player.getName().getString(), output);

        return true;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void giveOrDrop(PlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    private void sendStatus(PlayerEntity player) {
        // No-op: items are visible on the anvil now
    }

    /**
     * Returns an RGB color int for particle effects based on the material type.
     * These colors represent the "sparks" that fly off when forging.
     */
    private static int getMaterialParticleColor(Item material) {
        if (material == ModItems.HEATED_IRON_INGOT)
            return 0xD4D4D4; // light grey — iron sparks
        if (material == ModItems.HEATED_GOLD_INGOT)
            return 0xFFD700; // gold
        if (material == ModItems.HEATED_COPPER_INGOT)
            return 0xE07040; // copper orange
        if (material == Items.DIAMOND)
            return 0x5CF0E8; // cyan — diamond sparkle
        return 0xAAAAAA;
    }

    public boolean isEmpty() {
        return stickCount == 0 && materialCount == 0;
    }

    public int getStickCount() { return stickCount; }
    public int getMaterialCount() { return materialCount; }
    @Nullable public Item getMaterialType() { return materialType; }

    // ── Data Persistence ──────────────────────────────────────────────

    @Override
    protected void writeData(WriteView data) {
        super.writeData(data);
        data.putInt("StickCount", stickCount);
        data.putInt("MaterialCount", materialCount);
        if (materialType != null) {
            var key = net.minecraft.registry.Registries.ITEM.getId(materialType);
            if (key != null) {
                data.putString("MaterialType", key.toString());
            }
        }
    }

    @Override
    protected void readData(ReadView data) {
        super.readData(data);
        stickCount = data.getInt("StickCount", 0);
        materialCount = data.getInt("MaterialCount", 0);
        data.getOptionalString("MaterialType").ifPresent(id -> {
            var identifier = net.minecraft.util.Identifier.tryParse(id);
            if (identifier != null) {
                materialType = net.minecraft.registry.Registries.ITEM.get(identifier);
            }
        });
    }

    // ── Client Sync ─────────────────────────────────────────────────
    // Block entities only exist server-side by default. For the renderer to
    // know what's on the anvil, we need to send data to the client.
    // toUpdatePacket() → sent when we call world.updateListeners()
    // toInitialChunkDataNbt() → sent when the chunk first loads on the client

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    /**
     * Marks dirty AND notifies the client to re-sync.
     * Without updateListeners, the client would never know the data changed.
     */
    private void syncToClient() {
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    // ── Drop contents when block is broken ────────────────────────────

    public void dropContents(World world, BlockPos pos) {
        if (stickCount > 0) {
            ItemEntity stickEntity = new ItemEntity(world,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    new ItemStack(Items.STICK, stickCount));
            world.spawnEntity(stickEntity);
        }
        if (materialCount > 0 && materialType != null) {
            ItemEntity matEntity = new ItemEntity(world,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    new ItemStack(materialType, materialCount));
            world.spawnEntity(matEntity);
        }
    }
}

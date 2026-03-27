package cloud.hipp.smelty.block;

import cloud.hipp.smelty.block.entity.ModBlockEntities;
import cloud.hipp.smelty.block.entity.SmelterCoreBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * The Smelter Core — an invisible block placed inside the smelter when activated.
 *
 * BLOCK vs BLOCK ENTITY:
 *   Block = the type definition (shared by all instances, like a class)
 *   BlockEntity = per-placement data & logic (like an instance)
 *
 * We extend BlockWithEntity which:
 *   - Implements BlockEntityProvider (lets this block have a block entity)
 *   - Defaults render type to INVISIBLE (perfect for us!)
 *   - Provides validateTicker() for safe ticker registration
 */
public class SmelterCoreBlock extends BlockWithEntity {

    public static final MapCodec<SmelterCoreBlock> CODEC = createCodec(SmelterCoreBlock::new);

    public SmelterCoreBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    /**
     * Creates a new block entity when this block is placed in the world.
     */
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SmelterCoreBlockEntity(pos, state);
    }

    /**
     * Registers the tick function. Only ticks on the server — no client logic needed yet.
     *
     * validateTicker() checks that the BlockEntityType matches before returning
     * the ticker function, preventing ClassCastExceptions.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, ModBlockEntities.SMELTER_CORE, SmelterCoreBlockEntity::tick);
    }

    /**
     * INVISIBLE = don't render this block at all. Players see the stone walls
     * and heat source, not this internal marker block.
     */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }
}

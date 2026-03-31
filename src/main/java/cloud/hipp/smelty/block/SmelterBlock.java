package cloud.hipp.smelty.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;

public class SmelterBlock extends Block {
	public static final MapCodec<SmelterBlock> CODEC = createCodec(SmelterBlock::new);

	public SmelterBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}
}

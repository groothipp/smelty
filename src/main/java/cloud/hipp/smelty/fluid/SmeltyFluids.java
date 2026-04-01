package cloud.hipp.smelty.fluid;

import cloud.hipp.smelty.Smelty;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class SmeltyFluids {
	public static final FlowableFluid MOLTEN_ALLOY_STILL = Registry.register(
			Registries.FLUID,
			Identifier.of(Smelty.MOD_ID, "molten_alloy"),
			new MoltenAlloyFluid.Still()
	);

	public static final FlowableFluid MOLTEN_ALLOY_FLOWING = Registry.register(
			Registries.FLUID,
			Identifier.of(Smelty.MOD_ID, "flowing_molten_alloy"),
			new MoltenAlloyFluid.Flowing()
	);

	public static void initialize() {
	}
}

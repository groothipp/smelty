package cloud.hipp.smelty.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

public class MetallurgyGuideItem extends Item {
	public MetallurgyGuideItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user instanceof ServerPlayerEntity serverPlayer) {
			var content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
			if (content != null) {
				serverPlayer.networkHandler.sendPacket(new OpenWrittenBookS2CPacket(hand));
			}
		}
		return ActionResult.SUCCESS;
	}

	public static WrittenBookContentComponent createBookContent() {
		List<RawFilteredPair<Text>> pages = List.of(
				page(titlePage()),
				page(propertiesPage()),
				page(offensePage()),
				page(miningTierPage()),
				page(durabilityPage()),
				page(blendingPage())
		);
		return new WrittenBookContentComponent(
				RawFilteredPair.of("Metallurgy Guide"),
				"The Smelter",
				0,
				pages,
				true
		);
	}

	private static RawFilteredPair<Text> page(Text text) {
		return RawFilteredPair.of(text);
	}

	private static Text titlePage() {
		return Text.empty()
				.append(Text.literal("Metallurgy Guide\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("A guide to the properties of metals and how they influence tool stats.\n\n"))
				.append(Text.literal("Use the Analysis Bench to inspect a material's properties."));
	}

	private static Text propertiesPage() {
		return Text.empty()
				.append(Text.literal("Properties\n\n").formatted(Formatting.BOLD))
				.append(Text.literal("Hardness\n").formatted(Formatting.DARK_RED))
				.append(Text.literal("Toughness\n").formatted(Formatting.BLUE))
				.append(Text.literal("Melting Point\n").formatted(Formatting.GOLD))
				.append(Text.literal("Malleability\n").formatted(Formatting.DARK_GREEN))
				.append(Text.literal("Ductility\n").formatted(Formatting.DARK_AQUA))
				.append(Text.literal("Density\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("Corrosion Resist.\n\n").formatted(Formatting.GREEN))
				.append(Text.literal("All values 0-100. Alloys blend by proportion.\nMulti-material alloys get a diversity bonus!").formatted(Formatting.GRAY));
	}

	private static Text offensePage() {
		return Text.empty()
				.append(Text.literal("Attack Damage\n").formatted(Formatting.BOLD, Formatting.DARK_RED))
				.append(Text.literal("Sword: ").formatted(Formatting.GRAY))
				.append(Text.literal("Ductility").formatted(Formatting.DARK_AQUA))
				.append(Text.literal(" > H > D\n"))
				.append(Text.literal("Axe: ").formatted(Formatting.GRAY))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" > Density\n"))
				.append(Text.literal("Spear: ").formatted(Formatting.GRAY))
				.append(Text.literal("Toughness").formatted(Formatting.BLUE))
				.append(Text.literal(" > H, light\n\n"))
				.append(Text.literal("Attack Speed\n").formatted(Formatting.BOLD, Formatting.DARK_GREEN))
				.append(Text.literal("Density").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" (lower is faster)\n\n"))
				.append(Text.literal("Mining Speed\n").formatted(Formatting.BOLD, Formatting.BLUE))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" + light density"));
	}

	private static Text miningTierPage() {
		return Text.empty()
				.append(Text.literal("Mining Tier\n\n").formatted(Formatting.BOLD, Formatting.BLUE))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" thresholds:\n\n"))
				.append(Text.literal(" <20 ").formatted(Formatting.RED))
				.append(Text.literal("Wood\n"))
				.append(Text.literal(" <35 ").formatted(Formatting.GOLD))
				.append(Text.literal("Stone\n"))
				.append(Text.literal(" <60 ").formatted(Formatting.YELLOW))
				.append(Text.literal("Iron\n"))
				.append(Text.literal(" <90 ").formatted(Formatting.GREEN))
				.append(Text.literal("Diamond\n"))
				.append(Text.literal(" 90+ ").formatted(Formatting.AQUA))
				.append(Text.literal("Netherite"));
	}

	private static Text durabilityPage() {
		return Text.empty()
				.append(Text.literal("Durability\n\n").formatted(Formatting.BOLD, Formatting.BLUE))
				.append(Text.literal("Toughness").formatted(Formatting.BLUE))
				.append(Text.literal(" (dominant)\n"))
				.append(Text.literal("Corrosion Resist.").formatted(Formatting.GREEN))
				.append(Text.literal(" (slight boost)\n"))
				.append(Text.literal("Malleability").formatted(Formatting.DARK_GREEN))
				.append(Text.literal(" (slight penalty)\n\n"))
				.append(Text.literal("Toughness scales cubically!\nZero toughness = zero durability.").formatted(Formatting.GRAY));
	}

	private static Text blendingPage() {
		return Text.empty()
				.append(Text.literal("Diversity Bonus\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("Alloys of 2+ materials\nget a stat multiplier:\n\n"))
				.append(Text.literal("2 metals: ").formatted(Formatting.GRAY))
				.append(Text.literal("+15%\n").formatted(Formatting.GREEN))
				.append(Text.literal("3 metals: ").formatted(Formatting.GRAY))
				.append(Text.literal("+30%\n").formatted(Formatting.GREEN))
				.append(Text.literal("4 metals: ").formatted(Formatting.GRAY))
				.append(Text.literal("+45%\n").formatted(Formatting.GREEN))
				.append(Text.literal("5+ metals: ").formatted(Formatting.GRAY))
				.append(Text.literal("+55%\n\n").formatted(Formatting.DARK_GREEN))
				.append(Text.literal("Each metal must be 10%+ of the alloy to count.").formatted(Formatting.GRAY));
	}
}

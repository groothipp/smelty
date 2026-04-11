package cloud.hipp.smelty.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
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
				page(materialsPage()),
				page(modifiersPage1()),
				page(modifiersPage2()),
				page(modifiersPage3()),
				page(modifiersPage4()),
				page(diversityPage()),
				page(moldsPage1()),
				page(moldsPage2()),
				page(analysisBenchPage()),
				page(toolTiersPage()),
				page(durabilityAndSwordPage()),
				page(damagePage1()),
				page(damagePage2()),
				page(armorStatsPage1()),
				page(armorStatsPage2())
		);
		return new WrittenBookContentComponent(
				RawFilteredPair.of("Metallurgy Guide"),
				"Groot",
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
				.append(Text.literal("Contents\n").formatted(Formatting.BOLD))
				.append(Text.literal("- Properties\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Materials\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Modifiers\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Diversity Bonus\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Molds\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Analysis Bench\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Tool Tiers\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Tool Stats\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("- Armor Stats\n").formatted(Formatting.DARK_GRAY));
	}

	private static Text propertiesPage() {
		return Text.empty()
				.append(Text.literal("Properties\n\n").formatted(Formatting.BOLD))
				.append(Text.literal("All values 0-100.\n\n"))
				.append(Text.literal("Hardness\n").formatted(Formatting.DARK_RED))
				.append(Text.literal("Toughness\n").formatted(Formatting.BLUE))
				.append(Text.literal("Melting Point\n").formatted(Formatting.GOLD))
				.append(Text.literal("Malleability\n").formatted(Formatting.DARK_GREEN))
				.append(Text.literal("Ductility\n").formatted(Formatting.DARK_AQUA))
				.append(Text.literal("Density\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("Corrosion Resistance\n").formatted(Formatting.GREEN));
	}

	private static Text materialsPage() {
		return Text.empty()
				.append(Text.literal("Materials\n\n").formatted(Formatting.BOLD))
				.append(Text.literal("Copper\n").formatted(Formatting.GOLD))
				.append(Text.literal("Iron\n").formatted(Formatting.GRAY))
				.append(Text.literal("Gold\n").styled(s -> s.withColor(0x8B6914)))
				.append(Text.literal("Diamond\n").formatted(Formatting.AQUA))
				.append(Text.literal("Netherite\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("Obsidian\n").formatted(Formatting.DARK_PURPLE))
				.append(Text.literal("Emerald\n").formatted(Formatting.GREEN));
	}

	private static Text modifiersPage1() {
		return Text.empty()
				.append(Text.literal("Modifiers\n\n").formatted(Formatting.BOLD, Formatting.DARK_GREEN))
				.append(Text.literal("Coal\n").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("  Hardness\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Bone Meal\n").formatted(Formatting.GOLD))
				.append(Text.literal("  Toughness\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Slime Ball\n").formatted(Formatting.GREEN))
				.append(Text.literal("  Ductility\n").formatted(Formatting.GRAY));
	}

	private static Text modifiersPage2() {
		return Text.empty()
				.append(Text.literal("Modifiers (cont.)\n\n").formatted(Formatting.BOLD, Formatting.DARK_GREEN))
				.append(Text.literal("Clay Ball\n").formatted(Formatting.RED))
				.append(Text.literal("  Malleability\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Lapis Lazuli\n").formatted(Formatting.BLUE))
				.append(Text.literal("  Corr. Resist.\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Sugar\n").styled(s -> s.withColor(0x8B6914)))
				.append(Text.literal("  -Density\n").formatted(Formatting.GRAY));
	}

	private static Text modifiersPage3() {
		return Text.empty()
				.append(Text.literal("Modifiers (cont.)\n\n").formatted(Formatting.BOLD, Formatting.DARK_GREEN))
				.append(Text.literal("Blaze Powder\n").formatted(Formatting.GOLD))
				.append(Text.literal("  +Density\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Glowstone Dust\n").styled(s -> s.withColor(0x8B6914)))
				.append(Text.literal("  Hardness\n").formatted(Formatting.GRAY))
				.append(Text.literal("  Corr. Resist.\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Redstone\n").formatted(Formatting.DARK_RED))
				.append(Text.literal("  Toughness\n").formatted(Formatting.GRAY))
				.append(Text.literal("  Ductility\n").formatted(Formatting.GRAY));
	}

	private static Text modifiersPage4() {
		return Text.empty()
				.append(Text.literal("Modifiers (cont.)\n\n").formatted(Formatting.BOLD, Formatting.DARK_GREEN))
				.append(Text.literal("Ender Pearl\n").formatted(Formatting.DARK_AQUA))
				.append(Text.literal("  All stats\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Meat\n").styled(s -> s.withColor(0xBB5544)))
				.append(Text.literal("  No effect\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Nether Wart\n").formatted(Formatting.DARK_RED))
				.append(Text.literal("  Removes 1 random\n  modifier item\n").formatted(Formatting.GRAY));
	}

	private static Text diversityPage() {
		return Text.empty()
				.append(Text.literal("Diversity Bonus\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("Alloys with multiple metals get a bonus to all stats. The more distinct metals, the bigger the bonus.\n\n"))
				.append(Text.literal("Each metal must be at least 10% of the alloy to count.").formatted(Formatting.GRAY));
	}

	private static Text moldsPage1() {
		return Text.empty()
				.append(Text.literal("Molds\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("Place a pattern on a casting table and pour pure iron.\n\n"))
				.append(Text.literal("Ingot Mold\n").formatted(Formatting.BOLD))
				.append(Text.literal("Place any ingot\n\n"))
				.append(Text.literal("Nugget Mold\n").formatted(Formatting.BOLD))
				.append(Text.literal("Place any nugget\n\n"))
				.append(Text.literal("Rod Mold\n").formatted(Formatting.BOLD))
				.append(Text.literal("Place a stick"));
	}

	private static Text moldsPage2() {
		return Text.empty()
				.append(Text.literal("Molds (cont.)\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("Diamond Mold\n").formatted(Formatting.BOLD))
				.append(Text.literal("Place a diamond\n\n"))
				.append(Text.literal("Emerald Mold\n").formatted(Formatting.BOLD))
				.append(Text.literal("Place an emerald\n\n"))
				.append(Text.literal("Pour alloy over a mold to cast items. No mold = plate.\n\n").formatted(Formatting.GRAY))
				.append(Text.literal("Molds are reusable.").formatted(Formatting.GRAY));
	}

	private static Text analysisBenchPage() {
		return Text.empty()
				.append(Text.literal("Analysis Bench\n\n").formatted(Formatting.BOLD, Formatting.DARK_AQUA))
				.append(Text.literal("Place an alloy item to inspect its composition, properties, and modifier bonuses.\n\n"))
				.append(Text.literal("You can also name your alloys here.").formatted(Formatting.GRAY));
	}

	private static Text toolTiersPage() {
		return Text.empty()
				.append(Text.literal("Tool Tiers\n\n").formatted(Formatting.BOLD, Formatting.GOLD))
				.append(Text.literal("The combined stats of the equipment's material place it into one of five tiers:\n\n"))
				.append(Text.literal(" Tier I\n").formatted(Formatting.RED))
				.append(Text.literal(" Tier II\n").formatted(Formatting.GOLD))
				.append(Text.literal(" Tier III\n").styled(s -> s.withColor(0x8B6914)))
				.append(Text.literal(" Tier IV\n").formatted(Formatting.GREEN))
				.append(Text.literal(" Tier V\n\n").formatted(Formatting.AQUA))
				.append(Text.literal("Higher tier = stronger tools.").formatted(Formatting.GRAY));
	}

	private static Text durabilityAndSwordPage() {
		return Text.empty()
				.append(Text.literal("Durability\n\n").formatted(Formatting.BOLD, Formatting.BLUE))
				.append(Text.literal("Toughness").formatted(Formatting.BLUE))
				.append(Text.literal(" major\n"))
				.append(Text.literal("Corr. Resist.").formatted(Formatting.GREEN))
				.append(Text.literal(" minor\n"))
				.append(Text.literal("Malleability").formatted(Formatting.DARK_GREEN))
				.append(Text.literal(" penalty\n\n"))
				.append(Text.literal("Sword Damage\n\n").formatted(Formatting.BOLD, Formatting.DARK_RED))
				.append(Text.literal("Ductility").formatted(Formatting.DARK_AQUA))
				.append(Text.literal(" major\n"))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" minor\n"))
				.append(Text.literal("Density").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" minor"));
	}

	private static Text damagePage1() {
		return Text.empty()
				.append(Text.literal("Axe Damage\n\n").formatted(Formatting.BOLD, Formatting.DARK_RED))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" major\n"))
				.append(Text.literal("Density").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" minor\n\n"))
				.append(Text.literal("Spear Damage\n\n").formatted(Formatting.BOLD, Formatting.DARK_RED))
				.append(Text.literal("Toughness").formatted(Formatting.BLUE))
				.append(Text.literal(" major\n"))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" minor\n"))
				.append(Text.literal("Density").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" penalty"));
	}

	private static Text damagePage2() {
		return Text.empty()
				.append(Text.literal("Attack Speed\n\n").formatted(Formatting.BOLD, Formatting.DARK_GREEN))
				.append(Text.literal("Density").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" — lighter is faster\n\n"))
				.append(Text.literal("Mining Speed\n\n").formatted(Formatting.BOLD, Formatting.BLUE))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" major\n"))
				.append(Text.literal("Density").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" — lighter is faster"));
	}

	private static Text armorStatsPage1() {
		return Text.empty()
				.append(Text.literal("Defense\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("Ductility").formatted(Formatting.DARK_AQUA))
				.append(Text.literal(" major\n"))
				.append(Text.literal("Hardness").formatted(Formatting.DARK_RED))
				.append(Text.literal(" minor\n\n"))
				.append(Text.literal("Armor Toughness\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("Toughness").formatted(Formatting.BLUE))
				.append(Text.literal(" major\n"))
				.append(Text.literal("Malleability").formatted(Formatting.DARK_GREEN))
				.append(Text.literal(" penalty"));
	}

	private static Text armorStatsPage2() {
		return Text.empty()
				.append(Text.literal("Movement Speed\n\n").formatted(Formatting.BOLD, Formatting.DARK_PURPLE))
				.append(Text.literal("Density").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(" — light is faster,\nheavy is slower"));
	}
}

package cloud.hipp.smelty;

import cloud.hipp.smelty.block.ModBlocks;
import cloud.hipp.smelty.block.entity.ModBlockEntities;
import cloud.hipp.smelty.component.ModComponents;
import cloud.hipp.smelty.block.entity.CraftingAnvilBlockEntity;
import cloud.hipp.smelty.block.entity.SmelterCoreBlockEntity;
import cloud.hipp.smelty.item.ModItems;
import cloud.hipp.smelty.item.HammerItem;
import cloud.hipp.smelty.structure.SmelterValidator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Smelty implements ModInitializer {
	public static final String MOD_ID = "smelty";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// ── Registration ──────────────────────────────────────────────
		// Order matters! Blocks must be registered before block entities,
		// because ModBlockEntities.SMELTER_CORE references ModBlocks.SMELTER_CORE.
		ModComponents.initialize();  // Components first — items reference them
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModItems.initialize();

		// ── Event Handlers ────────────────────────────────────────────
		registerFlintAndSteelHandler();
		registerAnvilHammerHandler();

		LOGGER.info("Smelty initialized!");
	}

	/**
	 * Registers the flint and steel interaction for activating smelters.
	 *
	 * UseBlockCallback is a Fabric API event that fires when a player right-clicks on a block.
	 * It fires BEFORE the item's normal use action, so we can intercept it.
	 *
	 * The callback returns an ActionResult:
	 *   PASS    — "I don't care about this, let normal behavior happen"
	 *   SUCCESS — "I handled this, stop processing" (prevents flint & steel's normal fire-placing)
	 *
	 * Flow:
	 *   Player right-clicks campfire with flint & steel
	 *   → Our callback fires
	 *   → We check: is this flint & steel on a heat source?
	 *   → We validate the multiblock structure
	 *   → If valid: place core block, play sound, consume durability → return SUCCESS
	 *   → If not valid: return PASS (let normal flint & steel behavior happen)
	 */
	private void registerFlintAndSteelHandler() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			// Check if the player is holding flint and steel
			var stack = player.getStackInHand(hand);
			if (!stack.isOf(Items.FLINT_AND_STEEL)) {
				return ActionResult.PASS;
			}

			// Check if they clicked on a heat source
			BlockPos hitPos = hitResult.getBlockPos();
			if (!SmelterValidator.isHeatSource(world, hitPos)) {
				return ActionResult.PASS;
			}

			LOGGER.info("Flint & steel used on heat source at {}", hitPos);

			// Validate the multiblock structure around the heat source
			SmelterValidator.ValidationResult result = SmelterValidator.validate(world, hitPos);
			if (!result.valid()) {
				LOGGER.info("Structure validation FAILED at {}", hitPos);
				return ActionResult.PASS; // Not a valid structure, let flint & steel do its thing
			}

			// Check if there's already a core (smelter already active)
			BlockPos corePos = hitPos.up(1); // Core goes in the center of the middle layer
			if (world.getBlockState(corePos).isOf(ModBlocks.SMELTER_CORE)) {
				return ActionResult.PASS; // Already activated
			}

			// ── Activate the smelter! ─────────────────────────────────
			// Only modify the world on the server side
			if (!world.isClient()) {
				// Light the campfire! If it's unlit, set the LIT property to true.
				// BlockState is immutable — .with() returns a NEW state with the change.
				BlockState campfireState = world.getBlockState(hitPos);
				if (campfireState.contains(CampfireBlock.LIT) && !campfireState.get(CampfireBlock.LIT)) {
					world.setBlockState(hitPos, campfireState.with(CampfireBlock.LIT, true));
				}

				// Place the invisible core block
				world.setBlockState(corePos, ModBlocks.SMELTER_CORE.getDefaultState());

				// Get the block entity we just created and initialize it
				BlockEntity be = world.getBlockEntity(corePos);
				if (be instanceof SmelterCoreBlockEntity core) {
					core.initialize(result.gapDirection(), hitPos);
				}

				// Damage the flint and steel (1 durability)
				stack.damage(1, player);

				// Play flint & steel click, then the firework rocket launch sound
				world.playSound(null, hitPos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
						SoundCategory.BLOCKS, 1.0f, 1.0f);
				world.playSound(null, hitPos, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
						SoundCategory.BLOCKS, 1.5f, 0.8f);

				// ── Activation particles ──────────────────────────────
				if (world instanceof ServerWorld serverWorld) {
					// Big flame burst out the top — the smelter roaring to life
					serverWorld.spawnParticles(
							ParticleTypes.FLAME,
							hitPos.getX() + 0.5, hitPos.getY() + 3.0, hitPos.getZ() + 0.5,
							50,              // lots of particles
							0.3, 0.5, 0.3,  // spread wide and tall
							0.05             // faster speed
					);
					// Lava drip particles mixed in for extra drama
					serverWorld.spawnParticles(
							ParticleTypes.LAVA,
							hitPos.getX() + 0.5, hitPos.getY() + 3.0, hitPos.getZ() + 0.5,
							15,
							0.3, 0.4, 0.3,
							0.0
					);
					// Smoke billowing out the top
					serverWorld.spawnParticles(
							ParticleTypes.CAMPFIRE_COSY_SMOKE,
							hitPos.getX() + 0.5, hitPos.getY() + 3.2, hitPos.getZ() + 0.5,
							10,
							0.2, 0.1, 0.2,
							0.01
					);

					// Stone crack particles on every wall block — the structure shudders!
					// We loop over all 3 layers and emit particles on each wall position.
					for (int yOffset = 0; yOffset <= 2; yOffset++) {
						for (int dx = -1; dx <= 1; dx++) {
							for (int dz = -1; dz <= 1; dz++) {
								// Skip center blocks (not walls)
								if (dx == 0 && dz == 0) continue;
								// On bottom layer, skip the gap
								if (yOffset == 0) {
									BlockPos checkPos = hitPos.add(dx, 0, dz);
									if (world.getBlockState(checkPos).isAir()) continue;
								}

								double px = hitPos.getX() + dx + 0.5;
								double py = hitPos.getY() + yOffset + 0.5;
								double pz = hitPos.getZ() + dz + 0.5;

								// BLOCK_MARKER with stone texture — looks like stone cracking
								serverWorld.spawnParticles(
										ParticleTypes.CRIT,
										px, py, pz,
										5,              // particles per wall block
										0.3, 0.3, 0.3, // slight spread
										0.05
								);
							}
						}
					}
				}

				LOGGER.info("Smelter activated at {} (gap: {})", hitPos, result.gapDirection());
			}

			// Return SUCCESS on both client and server to prevent normal flint & steel behavior
			return ActionResult.SUCCESS;
		});
	}

	/**
	 * Registers the left-click (attack) handler for the crafting anvil.
	 *
	 * AttackBlockCallback fires when a player left-clicks a block.
	 * We use this so the player can "hammer" the anvil with an iron pickaxe.
	 *
	 * Signature: (player, world, hand, blockPos, direction) → ActionResult
	 *   PASS = continue normal behavior (start breaking block)
	 *   SUCCESS = handled, don't break the block
	 */
	private void registerAnvilHammerHandler() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			// Only handle our crafting anvil
			if (!world.getBlockState(pos).isOf(ModBlocks.CRAFTING_ANVIL)) {
				return ActionResult.PASS;
			}

			var stack = player.getStackInHand(hand);

			// Hammer time!
			if (stack.isOf(ModItems.HAMMER)) {
				if (!world.isClient()) {
					var be = world.getBlockEntity(pos);
					if (be instanceof CraftingAnvilBlockEntity anvil) {
						anvil.tryCraft(player, world);
					}
				}
				return ActionResult.SUCCESS; // Prevent block breaking
			}

			// Empty hand on a non-empty anvil — prevent breaking, show status
			if (stack.isEmpty()) {
				var be = world.getBlockEntity(pos);
				if (be instanceof CraftingAnvilBlockEntity anvil && !anvil.isEmpty()) {
					return ActionResult.SUCCESS; // Prevent accidental breaking
				}
			}

			return ActionResult.PASS;
		});
	}
}

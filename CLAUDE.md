# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Smelty is a Minecraft mod built on Fabric that overhauls toolmaking to focus on smelting and alloy creation. Materials have real metallurgy-inspired properties (hardness, toughness, density, etc.) that feed into formulas to produce tool stats at the forge step.

## Build & Run

```bash
./gradlew build                    # Compile and build the mod JAR
./gradlew runClient                # Launch Minecraft client with the mod
./gradlew runServer                # Launch dedicated server with the mod
./gradlew runDatagen               # Run Fabric data generators
```

Build artifacts are output to `build/libs/`.

## Tech Stack

- **Mod Framework**: Fabric (Loader 0.18.5, Fabric API 0.141.3+1.21.11)
- **Minecraft**: 1.21.11
- **Mappings**: Yarn 1.21.11+build.4
- **Java**: 21 (source, target, and release)
- **Build**: Gradle 9.3.0 with Fabric Loom plugin

## Architecture

Source is under `src/main/java/cloud/hipp/smelty/`. For design questions — formulas, material properties, system mechanics — refer to `DESIGN.md` (the authoritative design reference reflecting what is actually implemented). The `notes/` directory contains informal thoughts and brainstorming (not shipped with the mod).

### Entrypoints
- `Smelty.java` — Main entrypoint (`ModInitializer`), mod ID is `smelty`
- `SmeltyClient.java` — Client entrypoint, registers fluid rendering and color providers
- `SmeltyDataGenerator.java` — Fabric data generation entrypoint

### Blocks
- `SmelterBlock.java` — Simple wall/frame block for smelter construction
- `SmelterControllerBlock.java` — Controller block with `onUse` (opens GUI) and `onStateReplaced` (clears interior on break)
- `MoltenAlloyBlock.java` — Fluid block extending `FluidBlock`, prevents bucket pickup
- `SolidAlloyBlock.java` — Block with managed/unmanaged states, unbreakable when managed, drops composition NBT when unmanaged
- `ChannelBlock.java` — Horizontal fluid transport channel
- `ValveBlock.java` — Directional control valve for fluid flow
- `CastingBasinBlock.java` — Large casting vessel (9-ingot capacity), solidifies alloys into block items
- `CastingTableBlock.java` — Small casting surface (2-ingot capacity), casts alloys into mold-shaped items

### Block Entities
- `SmelterControllerBlockEntity.java` — Central smelter logic: multiblock validation, alloy processing, queue/stack fluid management, outflow system, heat scanning, color registries, GUI data
- `SolidAlloyBlockEntity.java` — Stores `AlloyComposition`, volume, and managed flag
- `ChannelBlockEntity.java` — Fluid flow through horizontal channels (4-ingot capacity, 9 ingots/sec flow)
- `ValveBlockEntity.java` — Directional fluid flow control (4-ingot capacity, 9 ingots/sec flow)
- `CastingBasinBlockEntity.java` — Basin solidification logic, implements `Inventory` for hopper support, outputs vanilla items for pure materials or solid alloy blocks for mixed alloys
- `CastingTableBlockEntity.java` — Casting table logic: pattern/mold placement, fluid acceptance, cooldown timer, solidification, item extraction. Produces ingots, nuggets, rods, or plates depending on mold and composition
- `SmeltyBlockEntities.java` — Block entity type registration

### Material System
- `SmeltyMaterial.java` — Enum of 5 materials (Copper, Iron, Gold, Diamond, Netherite) with 8 metallurgical properties + requiredHeat + color
- `MaterialProperty.java` — Enum of 8 material properties
- `AlloyComposition.java` — `EnumMap<SmeltyMaterial, Integer>` tracking material volumes in mL, with blending, draining, color mixing, and NBT serialization
- `MaterialItems.java` — Registry mapping vanilla items (ingots, nuggets, blocks, raw ores) to materials and volumes

### Fluid System
- `MoltenAlloyFluid.java` — Custom `FlowableFluid` with Still and Flowing variants, lava-like behavior
- `SmeltyFluids.java` — Fluid registration

### Screen/GUI
- `SmelterControllerScreen.java` — Client-side GUI rendering heat bar, volume bar, and composition breakdown
- `SmelterControllerScreenHandler.java` — Server-side screen handler
- `SmelterData.java` — Record with packet codec for syncing smelter state to client
- `SmeltyScreenHandlers.java` — Screen handler registration

### Structure
- `MultiblockValidator.java` — Validates rectangular prism structure (3-10 W/D, 3-9 H), returns dimensions, heat, and bounds

### Client Rendering
- `SmelterControllerBlockEntityRenderer.java` — Renders fluid inside the smelter multiblock
- `ChannelBlockEntityRenderer.java` — Renders fluid flowing through channels
- `ValveBlockEntityRenderer.java` — Renders fluid in valves
- `CastingBasinBlockEntityRenderer.java` — Renders fluid/solidified output in the basin
- `CastingTableBlockEntityRenderer.java` — Renders fluid, mold overlays, and solidified output on the table surface. Uses block textures as solid fills (mold provides shape), alloy items tinted with composition color via `solidItemColor`

### Registration
- `SmeltyBlocks.java` — Block registration (smelter_block, smelter_controller, valve, channel, casting_basin, casting_table, molten_alloy, solid_alloy)
- `SmeltyItems.java` — Item registration: block items, plates (per material + alloy), molds (ingot/nugget/rod), cast items (diamond_ingot, diamond_nugget, netherite_nugget), rods (per material + alloy), alloy variants (alloy_ingot, alloy_nugget, alloy_rod). Also provides casting lookup maps: `getCastIngot()`, `getCastNugget()`, `getRodForMaterial()`
- `SmeltyBlockEntities.java` — Block entity registration
- `SmeltyFluids.java` — Fluid registration
- `SmeltyScreenHandlers.java` — Screen handler registration

### Key Patterns
- The controller owns all alloy data — no interior block entities for fluid. Fluid and solid blocks are placed/removed by the controller's queue/stack system.
- `COLOR_REGISTRY` (static `ConcurrentHashMap`) maps controller positions to color + bounding box for fluid rendering inside smelters.
- `OUTFLOW_COLORS` (static `ConcurrentHashMap`) maps outflow source positions to color for fluid flowing out of broken smelters.
- `lookupFluidColor()` uses nearest-wins across both registries.
- Casting table uses pattern items to determine output: raw vanilla items (ingot/nugget/stick) as patterns create molds when cast with pure iron; mold items (ingot_mold/nugget_mold/rod_mold) shape fluid into the corresponding item type.
- Alloy items (alloy_plate, alloy_ingot, alloy_nugget, alloy_rod) use `CustomModelDataComponent` with colors[0] for the base alloy blend color and flags[] for modifier presence. Item definitions use `minecraft:composite` to layer a base model (tinted with the blend color) with conditional modifier overlays (`minecraft:condition` checking `custom_model_data` flags). Overlay textures in `assets/smelty/textures/item/overlay/` have colors baked in (not tinted). Generated by `generate_overlays.py`.
- MC 1.21+ item model system: each item needs both `assets/smelty/items/<id>.json` (item definition pointing to model, with optional tints) and `assets/smelty/models/item/<id>.json` (the actual model with textures).

## Key Design Decisions

- Material properties are metallurgically grounded (normalized 0-100 scale) and deliberately diverge from vanilla Minecraft where realism conflicts (e.g., diamond is brittle, gold has low mining speed)
- Tool stats are computed from material properties at forge time via multiplicative formulas — see `DESIGN.md` for the full formula set
- Components (blade, shaft, head) carry raw material properties; conversion to tool stats happens only when components are combined at the forge
- The smelter controller owns all alloy data. Breaking a wall keeps data (allows repair). Breaking the controller loses everything.
- Fluid blocks inside the smelter are managed via queue (empty positions) and stack (filled positions) — fill bottom-up, drain top-down
- Each interior position holds 2000 mL of alloy data, but each fluid block visually represents 1000 mL
- Outflow drains 1 block (1000 mL) per second through wall gaps when the smelter is invalid

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

The mod is in early development. Current source is under `src/main/java/cloud/hipp/smelty/`.

- `Smelty.java` — Main entrypoint (`ModInitializer`), mod ID is `smelty`
- `SmeltyDataGenerator.java` — Fabric data generation entrypoint
- `mixin/ExampleMixin.java` — Mixin scaffold

Design documents for the alloy/tool system are in `notes/` (not shipped with the mod). For any questions about the design of the project, refer to `notes/design.md`.

## Key Design Decisions

- Material properties are metallurgically grounded (normalized 0-100 scale) and deliberately diverge from vanilla Minecraft where realism conflicts (e.g., diamond is brittle, gold has low mining speed)
- Tool stats are computed from material properties at forge time via multiplicative formulas — see `notes/design.md` for the full formula set
- Components (blade, shaft, head) carry raw material properties; conversion to tool stats happens only when components are combined at the forge

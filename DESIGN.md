# Smelty Design Document

Complete reference for every system, formula, and constant implemented in the mod.

---

## Materials

Seven materials with 7 metallurgical properties each (0-100 scale), plus a display color. Required heat to melt equals the material's melting point.

| Material  | Hardness | Toughness | Melting Pt | Malleability | Ductility | Density | Corrosion Res | Color    |
|-----------|----------|-----------|------------|--------------|-----------|---------|---------------|----------|
| Copper    | 35       | 40        | 10         | 55           | 65        | 45      | 20            | 0xB87333 |
| Iron      | 60       | 65        | 40         | 45           | 50        | 50      | 30            | 0xD0D0D0 |
| Gold      | 10       | 15        | 10         | 35           | 20        | 65      | 70            | 0xFFD700 |
| Diamond   | 90       | 75        | 160        | 20           | 55        | 25      | 90            | 0x4AEDD9 |
| Netherite | 85       | 90        | 200        | 35           | 50        | 75      | 85            | 0x4A3B2C |
| Obsidian  | 95       | 10        | 160        | 5            | 5         | 55      | 80            | 0x1B0B2E |
| Emerald   | 45       | 70        | 40         | 50           | 55        | 20      | 60            | 0x17DD62 |

**Properties:** Hardness (H), Toughness (T), Melting Point (MP), Malleability (M), Ductility (Du), Density (rho), Corrosion Resistance (C).

**Design philosophy:** Properties are metallurgically grounded. Diamond is extremely hard but low malleability (brittle). Gold is soft and dense with high corrosion resistance. Obsidian is the hardest but has almost no toughness, ductility, or malleability. Emerald is light, tough, and ductile.

---

## Volume Units

All fluid volumes are tracked in milliliters (mL). The base unit is 1 ingot = 180 mL, chosen because 180 is divisible by both 9 (for nuggets) and 20 (for tick math).

| Item Form | Volume (mL) | Derivation       |
|-----------|-------------|------------------|
| Nugget    | 20          | 180 / 9          |
| Rod       | 90          | 180 / 2          |
| Ingot     | 180         | Base unit        |
| Plate     | 360         | 180 * 2          |
| Block     | 1620        | 180 * 9          |

---

## Alloy Composition System

An `AlloyComposition` is an `EnumMap<SmeltyMaterial, Integer>` tracking volume in mL per material, plus an `EnumMap<Modifier, Integer>` tracking modifier amounts.

### Blended Properties

Each property is a volume-weighted average:

```
P_blended = sum(ratio_i * P_i)  where ratio_i = volume_i / total_volume
```

### Modifiers

Modifiers are non-metal items thrown into the smelter that boost specific properties. Each follows an **exponential decay curve** based on item count:

```
bonus = maxBonus * (1 - e^(-k * n))
```

Where `n` is the number of that modifier item thrown into the smelter. Stacking the same modifier gives exponentially diminishing returns.

| Modifier      | Items                          | Effect(s)                                           | Max Bonus | k   | Tint Color |
|---------------|--------------------------------|-----------------------------------------------------|-----------|-----|------------|
| Coal          | Coal, Charcoal, Coal Block (9x)| Hardness +20                                        | 20        | 0.5 | 0x555555   |
| Bone Meal     | Bone Meal, Bone Block (9x)     | Toughness +20                                       | 20        | 0.5 | 0xE8E4D4   |
| Slime Ball    | Slime Ball, Slime Block (9x)   | Ductility +20                                       | 20        | 0.5 | 0x7EBF6E   |
| Clay Ball     | Clay Ball, Clay Block (4x)     | Malleability +20                                    | 20        | 0.5 | 0xA4907C   |
| Lapis Lazuli  | Lapis Lazuli, Lapis Block (9x) | Corrosion Resistance +20                            | 20        | 0.5 | 0x345EC3   |
| Sugar         | Sugar                          | Density -20                                         | -20       | 0.5 | 0xF0F0F0   |
| Blaze Powder  | Blaze Powder                   | Density +20                                         | 20        | 0.5 | 0xFF8C00   |
| Glowstone Dust| Glowstone Dust, Glowstone (4x) | Hardness +13, Corrosion Resistance +13              | 13 each   | 0.4 | 0xFFDD33   |
| Redstone      | Redstone, Redstone Block (9x)  | Toughness +13, Ductility +13                        | 13 each   | 0.4 | 0xCC0000   |
| Ender Pearl   | Ender Pearl                    | All properties +9; density pushes away from 50       | 9 each    | 0.3 | 0x0C5E4E   |
| Meat          | All raw meats/fish             | No stat effect (cosmetic only)                      | 0         | -   | 0xBB5544   |

Block items count as multiple modifier units (multiplier shown in parentheses). Single-property modifiers have k=0.5, dual-property modifiers k=0.4, all-property modifier (Ender Pearl) k=0.3. Higher k means the bonus saturates faster.

**Modifiers stay in the smelter.** When alloy is poured out, modifiers are NOT drained — they persist across multiple pours. All cast items receive the smelter's full modifier set. Modifiers are only removed when Nether Wart is thrown in, or when the smelter reaches 0 volume. Re-smelting alloy items does NOT add their modifiers back to the smelter; only explicitly thrown modifier items count.

### Netherwart (Modifier Removal)

Throwing **Nether Wart** into a smelter with modifiers removes 1 item from a random modifier per wart consumed. A stack of netherwart processes multiple removals. If a modifier's count reaches 0, it is removed entirely. If all modifiers are already removed, remaining warts are not consumed.

### Overall Modifier Effectiveness

All modifier bonuses are scaled by an overall effectiveness factor that decays with the total number of modifier items. The first modifier is always fully effective; decay kicks in from the 2nd item onward:

```
effectiveness = e^(-0.12 * (totalModifierCount - 1))
```

Where `totalModifierCount` is the sum of all modifier item counts in the alloy. Sweet spot is 3-4 items of one type for ~20% stat boost; diminishing returns set in beyond 5 total items.

The final modifier bonus for a property is: `sum(individual bonuses) * overall effectiveness`.

### Diversity Bonus

Materials contributing >= 10% of total volume count as "distinct." The diversity bonus is a flat multiplier based on distinct material count:

| Distinct Materials | Bonus |
|--------------------|-------|
| 0-1                | 0%    |
| 2                  | 15%   |
| 3                  | 30%   |
| 4                  | 45%   |
| 5+                 | 55%   |

### Final Property Computation

```
P_final = (P_blended + modifier_bonus) * (1 + diversity_bonus)
```

**Density exception:** For density, the diversity bonus pushes the value away from 50 (the neutral point) rather than uniformly increasing it:

```
density_final = 50 + (density_base - 50) * (1 + diversity_bonus)
```

This means light alloys (density < 50) get lighter with diversity, and heavy alloys (density > 50) get heavier. Density exactly at 50 is unaffected.

### Stat Total and Tier

The **stat total** is the sum of all final properties (excluding Melting Point), with density replaced by a **density score**:

```
D_s = (rho - 50)^2 / 25
```

This means density 50 (iron baseline) contributes 0, while extremes in either direction contribute positively. The stat total determines the alloy's tier:

| Tier | Name | Stat Total Threshold | Tier Multiplier (L) |
|------|------|----------------------|---------------------|
| I    | I    | < 180                | 0.4                 |
| II   | II   | >= 180               | 0.8                 |
| III  | III  | >= 245               | 1.0                 |
| IV   | IV   | >= 333               | 1.5                 |
| V    | V    | >= 455               | 2.25                |

The tier multiplier L scales most tool and armor formulas, making tier a major lever on final equipment power.

---

## Tool Stat Formulas

All formulas use final alloy properties and the tier multiplier L.

### Durability

```
Dur = L * 0.00141 * T^3 * (1 + 0.022 * C) / (1 + 0.035 * M)
```

Toughness cubed is the foundation. Corrosion resistance provides a multiplicative boost. Malleability applies a minor penalty. Minimum 1.

### Attack Damage

**Sword:**
```
A = L * (0.011 * H + 0.072 * Du + 0.015 * rho)
```
Ductility dominates -- flexible, edge-holding materials make the best blades.

**Axe:**
```
A = L * (0.059 * H + 0.029 * rho)
```
Hardness is ~2x density weight. Axes reward hard, heavy materials.

**Spear:**
```
A = L * (0.003 * H + 0.074 * T + 0.010 * (50 - rho))
```
Toughness dominates. Lighter-than-iron spears (rho < 50) get a damage bonus.

**Pickaxe, Shovel, Hoe:** Fixed 1.0 damage (non-weapon tools).

### Attack Speed

```
S_A = 80 / rho
```

No tier multiplier. Density alone governs swing speed. Lighter alloys swing faster. Density has a minimum floor of 1.

### Mining Speed

```
S_M = 6 * L * (20 / rho + 0.01 * H)
```

Light, hard materials mine fastest. Scaled so pure iron at Tier III (L=1.0) yields 6.0 (vanilla iron mining speed).

### Mining Tier

Mining tier is equal to the alloy's tool tier (I-V), mapped to vanilla mining levels:

| Tool Tier | Mining Tier | Equivalent    |
|-----------|-------------|---------------|
| I         | 0           | Wood          |
| II        | 1           | Stone         |
| III       | 2           | Iron          |
| IV        | 3           | Diamond       |
| V         | 4           | Netherite     |

---

## Armor Stat Formulas

### Defense Points

```
a = L * (0.033 * H + 0.06 * Du)
```

Ductility weighted ~60% -- armor that absorbs impact is more protective. This base value is then scaled by slot multiplier.

| Slot       | Defense Multiplier | Durability Multiplier |
|------------|--------------------|-----------------------|
| Helmet     | 0.333              | 0.69                  |
| Chestplate | 1.0                | 1.0                   |
| Leggings   | 0.833              | 0.94                  |
| Boots      | 0.333              | 0.81                  |

### Armor Toughness

```
t = max(0, L * (0.037 * T - 0.053 * M))
```

Only high-toughness, low-malleability materials produce meaningful armor toughness. Malleable materials deform too easily.

### Movement Speed Modifier

```
M_v = clamp(-0.025 * ((rho - 50) / 30)^3, -0.025, +0.025)
```

Cubic centered on iron's baseline density (50). Light armor = speed bonus, heavy armor = speed penalty. Clamped to +/- 2.5% per piece. No tier multiplier.

### Armor Durability

Uses the same base durability formula as tools, then scaled by the slot's durability multiplier.

---

## Tool Crafting

Tools are crafted on a vanilla crafting table using **ingots** (head) and **rods** (handle). All ingots in a recipe must be the same material/alloy. All rods must be the same material/alloy. Sticks can substitute for rods but apply a **stick penalty**.

### Stick Penalty

When sticks are used as handles instead of alloy rods, a penalty is applied to computed stats:

```
penalized_stat = stat * (1 - handleWeight)
```

This affects attack damage, mining speed, and durability. For example, a sword (handleWeight = 0.30) with sticks gets 70% of its normal stats. This ensures alloy rods are always preferable to sticks.

### Crafting Patterns

```
Sword:     Pickaxe:     Axe:       Shovel:    Hoe:       Spear:
  I         I I I        I I          I        I I          . . I
  I         . R .        I R          R        . R        . R .
  R         . R .        . R          R        . R        R . .
```

Axe, Hoe, and Spear have mirrored variants.

### Stat Computation at Craft Time

1. Head composition and handle composition are extracted from the input items
2. Both are merged into a **combined composition**
3. All stats (damage, speed, durability, mining tier) are computed from the combined composition
4. Stats are baked into the item's `CustomModelData` component as floats
5. Attribute modifiers are set: `attackDamage = computed - 1.0` (base hand), `attackSpeed = computed - 4.0` (base speed)

### Tool Type Parameters

| Tool    | Head Weight | Handle Weight | Base Atk Spd | Dmg Mult | Head Count | Handle Count |
|---------|-------------|---------------|--------------|----------|------------|--------------|
| Sword   | 0.70        | 0.30          | -2.4         | 1.0      | 2          | 1            |
| Pickaxe | 0.60        | 0.40          | -2.8         | 0.667    | 3          | 2            |
| Axe     | 0.65        | 0.35          | -3.1         | 1.5      | 3          | 2            |
| Shovel  | 0.40        | 0.60          | -3.0         | 0.75     | 1          | 2            |
| Hoe     | 0.30        | 0.70          | -1.0         | 0.167    | 2          | 2            |
| Spear   | 0.50        | 0.50          | -2.4         | 0.833    | 1          | 2            |

---

## Armor Crafting

Armor uses standard vanilla patterns with all-same-material ingots:

```
Helmet:      Chestplate:    Leggings:    Boots:
I I I        I . I          I I I        I . I
I . I        I I I          I . I        I . I
             I I I          I . I
```

All ingots must be the same material/alloy. Stats are computed and baked at craft time.

---

## Smelter Multiblock

### Structure Rules

- **Size:** 3-10 blocks wide/deep, 3-9 blocks tall
- **Layer 0 (floor):** Not validated for block type. Interior scanned for heat sources.
- **Layer 1:** Perimeter = smelter blocks or controller. Interior = solid smelter blocks.
- **Layer 2+ (chamber):** Perimeter = smelter blocks or controller. Interior = air (or solid alloy blocks).
- **Controller** must be on the perimeter (not corner, not interior) at layer 1+.

### Heat Sources

Placed under the floor (layer 0 interior):

| Block    | Heat Value |
|----------|------------|
| Campfire | 10         |
| Lava     | 50         |

Heat is rescanned every 20 ticks. Materials require their `requiredHeat` to melt. If heat drops below the alloy's required heat, molten material re-solidifies into the unmelted pool.

### Volume Capacity

```
maxVolume = interiorW * interiorD * chamberH * 3240
```

Where `interiorW = width - 2`, `interiorD = depth - 2`, `chamberH = height - 2`, and each interior block = 3240 mL (9 ingots * 2 = 18 ingots worth, at 180 mL/ingot).

### Revalidation

Structure is revalidated every 40 ticks (2 seconds). If the structure becomes invalid, **all alloy data is lost** (molten and unmelted compositions are cleared).

### Item Processing

Items thrown into the smelter interior (above layer 1) are scanned every 10 ticks. Accepted items:

1. **Registered material items** (ingots, nuggets, blocks, raw ores, plates, rods, molds): Added at their volume. If the material's required heat exceeds current heat, placed in the "unmelted" pool instead.
2. **Alloy items** (alloy_ingot, alloy_nugget, alloy_rod, alloy_plate): Composition extracted from CustomModelData, re-normalized to the item's volume, then added. **Modifiers stored on the item are discarded** — only materials are added.
3. **Solid alloy blocks**: Block entity data extracted, composition re-normalized to stored volume. Modifiers discarded.
4. **Nether Wart**: Removes 1 item from a random modifier per wart (processes full stack). Modifier removed entirely if count reaches 0.
5. **Modifier items**: Added to molten alloy (requires molten alloy to exist). Each item = 180 mL of modifier volume. Only explicitly thrown modifier items add modifiers.
6. **Unrecognized items** in molten alloy: Destroyed with lava extinguish sound.

### Bonus Volume Chances

- **Raw ores:** 10% chance to double volume (fortune-like smelting bonus)
- **Diamond / Emerald (single):** 30% chance to double volume
- **Diamond Block / Emerald Block:** 9 independent 30% rolls, each adding 1 ingot volume (180 mL)

### Molten Alloy Effects

- **Damage:** Every 10 ticks, living entities in the fluid take 4 damage + 5 seconds fire
- **Light:** Controller block emits light (LIT state) when molten alloy is present
- **Sound:** Lava ambient sounds every 80 ticks (50% chance)
- **Particles:** Flame, smoke, lava particles scale with heat ratio (heat / 3200)

---

## Fluid Transport

### Valves

Valves attach to the outside of smelter walls. When open, the controller pushes fluid through them at **81 mL/tick** (9 ingots/second at 20 TPS). Valves store no fluid -- they are pass-through.

**Flow priority:**
1. Downward (waterfall) up to 3 blocks below: channels > basins > casting tables
2. Horizontal (3 open sides, excludes back/wall side): casting blocks first, then channels

### Channels

Channels transport fluid horizontally and downward.

- **Capacity:** 720 mL (4 ingots)
- **Flow rate:** 81 mL/tick (9 ingots/second)
- **Network processing:** Each tick, the entire horizontally-connected channel network is processed as one fluid body via BFS:
  1. BFS to find all connected channels
  2. Merge all fluid into a single pool
  3. Push from pool to gravity targets (waterfalls up to 3 blocks below)
  4. Distribute remaining fluid evenly across all channels in the network

This network-as-one-body approach eliminates Zeno's paradox where equalization would fight drainage.

**Downward targets** (checked up to 3 air blocks below): channels, casting basins, casting tables. First non-air block stops the search.

### Connection Logic

Channels connect horizontally to adjacent channels, basins, and tables via blockstate boolean properties (NORTH, SOUTH, EAST, WEST). DOWN property indicates a valid downward target exists within 3 blocks.

---

## Casting

### Casting Basin

- **Capacity:** 1620 mL (9 ingots)
- **Cooldown:** 80 ticks (4 seconds) after filling
- **Output:** Pure single material without modifiers = vanilla block/ingot/nugget items (greedy decomposition: blocks first, then ingots, then nuggets). Mixed alloy or modified material = solid alloy block item with embedded composition data.

### Casting Table

- **Default capacity:** 360 mL (2 ingots, for plates)
- **Cooldown:** 60 ticks (3 seconds) after filling
- **Pattern/mold system:**

**Mold Creation:** Place a raw pattern item (vanilla ingot, nugget, diamond, emerald, stick) on the table. Pour pure iron. Extract = mold item + pattern returned.

| Pattern Item        | Mold Produced    |
|---------------------|------------------|
| Any vanilla ingot   | Ingot Mold       |
| Any vanilla nugget  | Nugget Mold      |
| Diamond / Cast Diamond | Diamond Mold  |
| Emerald / Cast Emerald | Emerald Mold  |
| Stick               | Rod Mold         |

**Mold Casting:** Place a mold on the table. Pour any alloy. Capacity changes to match the mold:

| Mold         | Capacity | Output (pure)        | Output (alloy)  |
|--------------|----------|----------------------|------------------|
| Ingot Mold   | 180 mL   | Material ingot       | Alloy Ingot      |
| Nugget Mold  | 20 mL    | Material nugget      | Alloy Nugget     |
| Rod Mold     | 90 mL    | Material rod         | Alloy Rod        |
| Diamond Mold | 180 mL   | Cast Diamond         | Alloy Ingot      |
| Emerald Mold | 180 mL   | Cast Emerald         | Alloy Ingot      |

**Mold restrictions:** Diamond mold only accepts pure, unmodified diamond fluid. Emerald mold only accepts pure, unmodified emerald. Ingot mold rejects pure unmodified diamond and emerald (must use specialized molds). However, diamond or emerald alloys **with modifiers** are treated as alloys — they must use the ingot mold and produce alloy ingots.

**No pattern:** Produces a plate (pure material plate or alloy plate).

Molds are reusable -- extracting a cast item keeps the mold on the table.

---

## Proportional Draining

When fluid is drained from an AlloyComposition, **only materials** are drained proportionally. Modifiers stay in the source and are never removed by draining. The drained portion receives a full copy of the source's modifier set (for cast item encoding) without removing them from the source. When materials are fully drained (volume reaches 0), modifiers are also cleared.

Receivers (channels, basins, casting tables) use **set semantics** for modifiers — incoming modifiers overwrite rather than accumulate, since each drain tick copies the full modifier set.

The algorithm for material draining:

1. Compute `ratio = drainMl / totalMl`
2. For each material: `toDrain = round(ratio * volume)`
3. Rounding correction: adjust the largest-volume material by `targetDrain - roundedTotal` to ensure exact drain amount
4. Remove materials that reach 0

This ensures alloy ratios are preserved through any number of drain operations.

---

## Color Blending

### Material Color

Volume-weighted average in RGB space:

```
R = sum(ratio_i * R_i)
G = sum(ratio_i * G_i)
B = sum(ratio_i * B_i)
```

### Modifier Tint

Each modifier contributes a subtle tint based on item count: `weight = min(0.1, count * 0.02)`. Total modifier tint is capped at 40% influence. The final color blends material color (remaining weight) with modifier tint colors.

---

## Item Data Encoding

Alloy items store their composition in `CustomModelData.floats()`:

### Alloy Items (ingot, nugget, rod, plate)

Layout: `[7 material amounts (normalized to base 20), 11 modifier amounts (raw volumes)]`

Materials use a coarse base of 20 (5% resolution) to collapse +/- 1-2 mL rounding differences, ensuring items from the same alloy batch always stack. Modifier amounts are stored as raw volumes (absolute, not scaled with materials) — all items from the same smelter receive identical modifier data since modifiers don't drain.

`CustomModelData.strings()`: `[normalizedKey]` — the canonical composition key computed at cast time, used for exact name registry lookups.

`CustomModelData.colors()`: `[blendedColor]` for tinting.

### Tool Items

Layout: `[7 head materials, miningSpeed, miningTierIndex, 7 handle materials, attackDamage, attackSpeed, tier]`

`CustomModelData.colors()`: `[headColor, handleColor]`

### Armor Items

Layout: `[7 materials, defense, tier]`

`CustomModelData.colors()`: `[blendedColor]` for inventory tinting.

Armor also uses `DYED_COLOR` component for equipped color rendering on the player model (via a custom `smelty:alloy` equipment model with a `dyeable` layer). The dyed color tooltip is hidden via `TooltipDisplayComponent`.

---

## Equipment Tooltips

Each equipment type shows only its relevant stats, in this order:

| Equipment | Tooltip Stats                                      |
|-----------|----------------------------------------------------|
| Sword     | Tier, Attack Damage, Attack Speed, Durability      |
| Axe       | Tier, Attack Damage, Attack Speed, Mine Speed, Durability |
| Spear     | Tier, Attack Damage, Attack Speed, Durability      |
| Pickaxe   | Tier, Mine Speed, Durability                       |
| Shovel    | Tier, Mine Speed, Durability                       |
| Hoe       | Tier, Durability                                   |
| Armor     | Tier, Armor, Toughness, Move Speed                 |

Armor move speed is displayed as an integer percentage (floored). Vanilla attribute modifier and dyed color tooltips are hidden.

---

## Alloy Naming Registry

Players can name alloy compositions via the analysis bench. Names are stored in a world-level `PersistentState` (`AlloyRegistry`) keyed by a **normalized composition key**:

```
Format: "COPPER:30,IRON:70" (sorted by enum order, zeros omitted)
Modifiers appended after "|": "COPPER:50,IRON:50|COAL:5"
```

Materials are normalized to sum to 100. Modifiers use absolute item counts (volume / MODIFIER_VOLUME). Each alloy item stores its normalized key as a string in `CustomModelData.strings()` at cast time, ensuring name lookups are exact and don't suffer from normalization rounding. Named alloys display their name on items, tools, and armor (e.g., "Bronze Sword" instead of "Alloy Sword"). The registry is synced to clients for display.

---

## Multiblock Validation Algorithm

The validator tries all possible structure dimensions (width 3-10, depth 3-10, height 9 down to 3) and all possible placements where the controller sits on the perimeter. For each candidate:

1. Layer 0: scan interior for heat sources (campfires = 10, lava = 50)
2. Layer 1: perimeter must be smelter/controller blocks, interior must be solid smelter blocks
3. Layer 2+: perimeter must be smelter/controller blocks, interior must be air or solid alloy blocks
4. Controller must be found on the perimeter

Returns first valid match (largest height first).

---

## Metallurgy Guide

A written book item covering all mod systems: properties, smelting, materials, modifiers, molds, casting, analysis, tiers, and tool/armor stat formulas.

### Obtaining

- **First smelter:** Automatically given to the player when they place a smelter controller that successfully forms a valid multiblock for the first time. Tracked per-player via world-level persistent data (`SmeltyPlayerData`), so each player receives exactly one copy.
- **Crafting:** 3 paper over 1 smelter block (same layout as a vanilla book with leather replaced by a smelter block).

---

## Vanilla Recipe Overrides

The mod disables vanilla tool and armor crafting recipes for materials that the smelting system handles, forcing players through the Smelty crafting recipes which compute stats from alloy properties:

- **Diamond:** All 5 tools + 4 armor pieces disabled
- **Iron:** All 5 tools + 4 armor pieces disabled
- **Golden:** All 5 tools + 4 armor pieces disabled

Crafting these items with the standard patterns on a crafting table will use the Smelty special recipe (`smelty:tool_crafting` / `smelty:armor_crafting`), producing alloy tools/armor with computed stats.

---

## Tick Rates Summary

| System                    | Interval     |
|---------------------------|--------------|
| Structure revalidation    | 40 ticks (2s)|
| Heat rescan               | 20 ticks (1s)|
| Item entity processing    | 10 ticks (0.5s)|
| Valve output              | Every tick   |
| Channel network processing| Every tick   |
| Entity damage in smelter  | 10 ticks (0.5s)|
| Item destruction in smelter| 10 ticks (0.5s)|
| Lava ambient sound        | 80 ticks (4s), 50% chance |
| Casting table cooldown    | 60 ticks (3s)|
| Casting basin cooldown    | 80 ticks (4s)|
| Smoke particles (casting) | Every 10 ticks during cooldown |

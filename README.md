# 🔥 Smelty

**Multiblock smelting and forging for Minecraft 1.21.11**

Smelty overhauls ore processing and tool crafting with a Tinkers' Construct-inspired multiblock smelter and anvil forging system. Smelt ore in bulk, forge tools on an anvil, and race to craft before your ingots cool down.

![Fabric](https://img.shields.io/badge/mod%20loader-Fabric-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green)
![License](https://img.shields.io/badge/license-CC0--1.0-lightgrey)

---

## Features

### 🧱 Multiblock Smelter

Build a 3×3×3 structure out of any stone-type blocks, place a campfire in the center of the bottom layer, and leave one gap on the side for output. Light the campfire with flint & steel to activate the smelter.

**Structure layout:**

```
Bottom layer:        Middle & Top layers:
S S S                S S S
S 🔥 _               S   S
S S S                S S S

S = any stone    🔥 = campfire    _ = output gap (air)
```

**Valid wall blocks:** Stone, Cobblestone, Stone Bricks, Deepslate, Granite, Diorite, Andesite, Tuff, Bricks, Nether Bricks, Blackstone, and their polished/chiseled variants.

**How it works:**
- Throw raw ore into the top opening
- The smelter processes up to **64 items per batch**
- Processing time scales linearly: **4 seconds** for 1 item, **45 seconds** for a full stack
- Each ore produces **1 heated ingot** with a **50% chance for a bonus ingot**
- Regular ingots can be reheated at a 1:1 ratio (no bonus)
- Breaking any wall block or extinguishing the campfire deactivates the smelter

### 🌡️ Heated Ingots

Heated ingots are the output of the smelter. They come in three types:

| Item | Source Ore |
|------|-----------|
| Heated Iron Ingot | Raw Iron |
| Heated Gold Ingot | Raw Gold |
| Heated Copper Ingot | Raw Copper |

**Cooling system:**
- Heated ingots from the same batch **stack together** and share a single heat timer
- They cool over **60 seconds** and visually transition through **5 color stages** — from bright red-orange to nearly their original color
- When fully cooled, they transform back into regular ingots
- **Water quenching:** throw heated ingots into water to instantly convert them to regular ingots (with a steam effect!)

### ⚒️ Crafting Anvil

A custom anvil for forging tools and armor from heated ingots.

**Crafting recipe:** 7 iron ingots in an anvil shape

```
I I I
  I
I I I
```

**How to use:**
1. Place the Crafting Anvil
2. **Right-click** with **sticks** to add them (left side of anvil)
3. **Right-click** with **materials** (heated ingots or diamonds) to stack them (right side)
4. **Right-click** with an **empty hand** to remove the last item
5. **Left-click** with the **Hammer** to forge!

Items are rendered on top of the anvil with natural random rotations. On a successful forge, material-colored sparks fly off the anvil.

### 🔨 Hammer

A simple tool used to strike the Crafting Anvil and forge items.

**Crafting recipe:**

```
I
S
```
*(I = Iron Ingot, S = Stick)*

### 📖 Anvil Recipes

**Tools:**

| Sticks | Materials | Result |
|--------|-----------|--------|
| 1 | 1 | Shovel |
| 2 | 1 | Spear |
| 1 | 2 | Sword |
| 2 | 2 | Hoe |
| 2 | 3 | Pickaxe |
| 1 | 3 | Axe |

**Armor:**

| Sticks | Materials | Result |
|--------|-----------|--------|
| 0 | 4 | Boots |
| 0 | 5 | Helmet |
| 0 | 7 | Leggings |
| 0 | 8 | Chestplate |

**Supported materials:**

| Material | Tool/Armor Tier |
|----------|----------------|
| Heated Copper Ingot | Copper |
| Heated Iron Ingot | Iron |
| Heated Gold Ingot | Gold |
| Diamond | Diamond |

### 🚫 Disabled Vanilla Recipes

Smelty disables the following vanilla recipes to enforce the new progression:

- **Furnace/Blast Furnace smelting** of iron, gold, and copper ore into ingots
- **Crafting table recipes** for all iron, gold, copper, and diamond tools, armor, and spears

**Not affected:** Wooden and stone tools, iron/gold from nuggets/blocks, the mace, and all other vanilla recipes.

---

## Progression

1. **Early game:** Craft wooden/stone tools normally
2. **Get iron:** Mine 7 iron ore, build a smelter, smelt them into heated iron ingots
3. **Craft tools:** Build a Crafting Anvil (7 iron ingots) and a Hammer (1 iron + 1 stick)
4. **Forge:** Place sticks + heated ingots on the anvil, strike with the hammer
5. **Scale up:** Mine in bulk — the smelter processes up to 64 ore per batch with bonus chances
6. **Need regular ingots?** Throw heated ingots into water to quench them for use in pistons, hoppers, rails, etc.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop `smelty-1.0.0.jar` into your `mods/` folder

---

## Building from Source

```bash
git clone https://github.com/groothipp/smelty.git
cd smelty
./gradlew build
```

The built jar will be in `build/libs/`.

---

## Credits

- **Author:** Groot
- **Inspired by:** [Tinkers' Construct](https://www.curseforge.com/minecraft/mc-mods/tinkers-construct)
- **Built with:** [Fabric](https://fabricmc.net/) for Minecraft 1.21.11

## License

CC0-1.0

#!/usr/bin/env python3
"""
Generate modifier overlay textures for Smelty mod.
Run from project root: python3 generate_overlays.py

Creates overlay PNGs for each modifier effect x item type combination.
Colors are baked into the textures (no runtime tinting needed for overlays).
"""

import os
import math
import json
import hashlib
from PIL import Image

# ─── Configuration ────────────────────────────────────────────────────────

PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
TEXTURES_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/assets/smelty/textures/item")
OVERLAY_DIR = os.path.join(TEXTURES_DIR, "overlay")
MODELS_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/assets/smelty/models/item/overlay")
ITEMS_DIR = os.path.join(PROJECT_ROOT, "src/main/resources/assets/smelty/items")

# Base item types (non-tool, non-armor)
BASIC_ITEMS = ["alloy_ingot", "alloy_nugget", "alloy_rod", "alloy_plate"]

# Tool head textures (overlays applied to head only)
TOOL_HEADS = [
    "alloy_sword_head", "alloy_pickaxe_head", "alloy_axe_head",
    "alloy_hoe_head", "alloy_shovel_head", "alloy_spear_head",
]

# Armor textures (single-layer items)
ARMOR_ITEMS = ["alloy_helmet", "alloy_chestplate", "alloy_leggings", "alloy_boots"]

# All item types that get overlays
ALL_ITEMS = BASIC_ITEMS + TOOL_HEADS + ARMOR_ITEMS

# Modifier definitions: (name, effect_type, effect_params)
MODIFIERS = [
    ("coal", "speckle", {
        # "coated in coal dust" — very dense coverage
        "colors": [(0x22, 0x22, 0x22), (0x44, 0x44, 0x44), (0x66, 0x66, 0x66)],
        "density": 0.55,  # uses density mode, not count
        "seed": 1,
    }),
    ("bonemeal", "edge", {
        "color": (0xE8, 0xE4, 0xD4),
        "fade_steps": 1,  # thinner edge, faster falloff
    }),
    ("slime", "stripe", {
        "color": (0x7E, 0xBF, 0x6E),
        "direction": 1,  # (x+y) diagonal
        "period": 4,
        "width": 1,
    }),
    ("clay", "stripe", {
        "color": (0xA4, 0x90, 0x7C),
        "direction": -1,  # (x-y) diagonal (perpendicular)
        "period": 4,
        "width": 1,
    }),
    ("lapis", "edge", {
        "color": (0x34, 0x5E, 0xC3),
        "fade_steps": 1,  # thinner edge, faster falloff
    }),
    ("sugar", "speckle", {
        # "coated in sugar and sugar cane bits" — more white than green
        "colors": [(0xF0, 0xF0, 0xF0), (0xF0, 0xF0, 0xF0), (0xF0, 0xF0, 0xF0), (0x55, 0xCC, 0x33)],
        "density": 0.45,
        "seed": 2,
    }),
    ("blaze", "speckle", {
        # More prevalent dots
        "colors": [(0xFF, 0x8C, 0x00), (0xFF, 0x33, 0x33)],
        "density": 0.40,
        "seed": 3,
    }),
    ("glowstone", "speckle_animated", {
        # Bright golden dots, much slower pulsing — visually luminous
        "colors": [(0xFF, 0xEE, 0x66)],
        "density": 0.40,
        "seed": 4,
        "frames": 16,
        "min_brightness": 0.55,
        "frametime": 10,  # much slower pulse
    }),
    ("redstone", "speckle_animated", {
        # Bright red dots, same slow pulse as glowstone — visually luminous
        "colors": [(0xFF, 0x33, 0x33)],
        "density": 0.40,
        "seed": 5,
        "frames": 16,
        "min_brightness": 0.55,
        "frametime": 10,  # same slow pulse as glowstone
    }),
    ("enderpearl", "static_swirl_animated", {
        # Static swirl shapes, color gradient sweeps across them over time
        "color_a": (0x0C, 0x7E, 0x5E),  # green-teal
        "color_b": (0x4C, 0x3E, 0x6E),  # purple-teal
        "frames": 32,
        "frametime": 3,
        "seed": 6,
    }),
    ("meat", "grainy", {
        "colors": [(0xBB, 0x33, 0x33), (0x88, 0x22, 0x22)],
        "density": 0.35,
        "seed": 7,
    }),
]


# ─── Mask Utilities ───────────────────────────────────────────────────────

def load_alpha_mask(item_name):
    """Load a base texture and return its alpha mask as a set of (x, y) coords."""
    path = os.path.join(TEXTURES_DIR, f"{item_name}.png")
    img = Image.open(path).convert("RGBA")
    w, h = img.size
    mask = set()
    for y in range(h):
        for x in range(w):
            _, _, _, a = img.getpixel((x, y))
            if a > 0:
                mask.add((x, y))
    return mask, w, h


def compute_edge_distance(mask, w, h):
    """Compute distance from each pixel to the nearest transparent neighbor."""
    dist = {}
    for (x, y) in mask:
        is_edge = False
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nx, ny = x + dx, y + dy
            if (nx, ny) not in mask:
                is_edge = True
                break
        if is_edge:
            dist[(x, y)] = 0

    # BFS from edge pixels inward
    queue = list(dist.keys())
    idx = 0
    while idx < len(queue):
        x, y = queue[idx]
        idx += 1
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nx, ny = x + dx, y + dy
            if (nx, ny) in mask and (nx, ny) not in dist:
                dist[(nx, ny)] = dist[(x, y)] + 1
                queue.append((nx, ny))
    return dist


def deterministic_scatter(mask, seed, count):
    """Pick `count` deterministic positions within the mask."""
    pixels = sorted(mask)
    if not pixels:
        return []
    selected = []
    h = int(hashlib.md5(f"scatter_{seed}".encode()).hexdigest(), 16)
    for i in range(count):
        h = (h * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        idx = h % len(pixels)
        selected.append(pixels[idx])
    return selected


# ─── Effect Generators ────────────────────────────────────────────────────

def generate_speckle(mask, w, h, colors, seed, density=None, count_per_color=None):
    """Generate speckled dots with baked-in colors.

    If density is provided, uses density-based placement (fraction of mask pixels).
    Otherwise falls back to count_per_color fixed-count placement.
    """
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if density is not None:
        # Density-based: each pixel in mask has a chance to be a dot
        h_val = int(hashlib.md5(f"speckle_{seed}".encode()).hexdigest(), 16)
        for x, y in sorted(mask):
            h_val = (h_val * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
            if (h_val & 0xFF) / 255.0 < density:
                color = colors[(h_val >> 8) % len(colors)]
                img.putpixel((x, y), (*color, 255))
    else:
        cpc = count_per_color or 4
        for ci, color in enumerate(colors):
            positions = deterministic_scatter(mask, seed * 100 + ci, cpc)
            for x, y in positions:
                img.putpixel((x, y), (*color, 255))
    return img


def generate_speckle_animated(mask, w, h, colors, seed, frames, min_brightness,
                              density=None, count_per_color=None):
    """Generate animated speckle dots that pulse in brightness."""
    # Determine dot positions
    dot_positions = []  # list of (color, x, y)
    if density is not None:
        h_val = int(hashlib.md5(f"speckle_{seed}".encode()).hexdigest(), 16)
        for x, y in sorted(mask):
            h_val = (h_val * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
            if (h_val & 0xFF) / 255.0 < density:
                color = colors[(h_val >> 8) % len(colors)]
                dot_positions.append((color, x, y))
    else:
        cpc = count_per_color or 5
        for ci, color in enumerate(colors):
            positions = deterministic_scatter(mask, seed * 100 + ci, cpc)
            for x, y in positions:
                dot_positions.append((color, x, y))

    # Create a tall image with all frames stacked vertically
    img = Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))
    for frame in range(frames):
        # Sinusoidal brightness pulse
        t = frame / frames
        brightness = min_brightness + (1.0 - min_brightness) * (0.5 + 0.5 * math.sin(2 * math.pi * t))
        for color, x, y in dot_positions:
            r = int(color[0] * brightness)
            g = int(color[1] * brightness)
            b = int(color[2] * brightness)
            img.putpixel((x, y + frame * h), (r, g, b, 255))
    return img


def generate_edge(mask, w, h, color, fade_steps):
    """Generate edge coloring with inward lerp."""
    dist = compute_edge_distance(mask, w, h)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for (x, y), d in dist.items():
        if d <= fade_steps:
            # Alpha fades from 200 at edge to lower values inward
            alpha = int(200 * (1.0 - d / (fade_steps + 1)))
            if alpha > 0:
                img.putpixel((x, y), (*color, alpha))
    return img


def generate_stripe(mask, w, h, color, direction, period, width):
    """Generate diagonal stripes."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x, y in mask:
        if direction > 0:
            val = (x + y) % period
        else:
            val = (x - y + 100) % period  # +100 to avoid negative modulo
        if val < width:
            img.putpixel((x, y), (*color, 180))
    return img


def generate_static_swirl_animated(mask, w, h, color_a, color_b, frames, seed):
    """Generate static swirl shapes with a color gradient that sweeps across them over time.

    The swirl pattern itself doesn't move — only the color gradient animates,
    sweeping from color_a to color_b across the item in a direction that shifts
    each frame (bottom-to-top, left-to-right, etc.).
    """
    pixels = sorted(mask)
    if not pixels:
        return Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))

    cx = sum(x for x, y in pixels) / len(pixels)
    cy = sum(y for x, y in pixels) / len(pixels)

    # Pre-compute static swirl mask: which pixels are "swirl" pixels
    swirl_pixels = []
    for x, y in pixels:
        dx, dy = x - cx, y - cy
        angle = math.atan2(dy, dx)
        dist = math.sqrt(dx * dx + dy * dy)
        # Multiple overlapping swirl arms for denser coverage
        swirl_val = (math.sin(angle * 3 + dist * 0.9) +
                     math.sin(angle * 2 - dist * 0.7 + 1.5)) / 2
        if swirl_val > 0.15:
            alpha = int(180 * min(1.0, (swirl_val - 0.15) / 0.5))
            swirl_pixels.append((x, y, alpha))

    # Find bounding box for normalizing gradient position
    min_x = min(x for x, y in pixels)
    max_x = max(x for x, y in pixels)
    min_y = min(y for x, y in pixels)
    max_y = max(y for x, y in pixels)
    span_x = max(max_x - min_x, 1)
    span_y = max(max_y - min_y, 1)

    img = Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))
    for frame in range(frames):
        t = frame / frames
        # Gradient direction rotates over time
        grad_angle = t * 2 * math.pi
        gx = math.cos(grad_angle)
        gy = math.sin(grad_angle)

        for x, y, alpha in swirl_pixels:
            # Normalized position along gradient direction
            nx = (x - min_x) / span_x - 0.5
            ny = (y - min_y) / span_y - 0.5
            grad_pos = nx * gx + ny * gy  # range roughly -0.7 to 0.7
            # Map to blend factor with a wave
            blend = 0.5 + 0.5 * math.sin(grad_pos * math.pi * 2 + t * 2 * math.pi)
            r = int(color_a[0] * (1 - blend) + color_b[0] * blend)
            g = int(color_a[1] * (1 - blend) + color_b[1] * blend)
            b = int(color_a[2] * (1 - blend) + color_b[2] * blend)
            img.putpixel((x, y + frame * h), (r, g, b, alpha))
    return img


def generate_grainy(mask, w, h, colors, density, seed):
    """Generate a grainy/fleshy noise pattern."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    h_val = int(hashlib.md5(f"grainy_{seed}".encode()).hexdigest(), 16)
    for x, y in sorted(mask):
        h_val = (h_val * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        if (h_val & 0xFF) / 255.0 < density:
            color = colors[h_val >> 8 & 1] if len(colors) > 1 else colors[0]
            img.putpixel((x, y), (*color, 200))
    return img


# ─── Generation Pipeline ─────────────────────────────────────────────────

def generate_overlay_for_item(item_name, mod_name, effect_type, params, mask, w, h):
    """Generate a single overlay texture and save it."""
    if effect_type == "speckle":
        img = generate_speckle(mask, w, h, params["colors"], params["seed"],
                               density=params.get("density"),
                               count_per_color=params.get("count_per_color"))
    elif effect_type == "speckle_animated":
        img = generate_speckle_animated(mask, w, h, params["colors"], params["seed"],
                                        params["frames"], params["min_brightness"],
                                        density=params.get("density"),
                                        count_per_color=params.get("count_per_color"))
    elif effect_type == "edge":
        img = generate_edge(mask, w, h, params["color"], params["fade_steps"])
    elif effect_type == "stripe":
        img = generate_stripe(mask, w, h, params["color"],
                              params["direction"], params["period"], params["width"])
    elif effect_type == "static_swirl_animated":
        img = generate_static_swirl_animated(mask, w, h, params["color_a"], params["color_b"],
                                             params["frames"], params["seed"])
    elif effect_type == "grainy":
        img = generate_grainy(mask, w, h, params["colors"],
                              params["density"], params["seed"])
    else:
        raise ValueError(f"Unknown effect type: {effect_type}")

    # Derive short item name for filename (e.g., "alloy_ingot" -> "ingot")
    short = item_name.replace("alloy_", "")
    out_path = os.path.join(OVERLAY_DIR, f"{short}_{mod_name}.png")
    img.save(out_path)
    print(f"  Generated: {short}_{mod_name}.png")

    # Write .mcmeta for animated textures
    if effect_type in ("speckle_animated", "static_swirl_animated"):
        mcmeta = {
            "animation": {
                "frametime": params["frametime"],
                "interpolate": True,
            }
        }
        mcmeta_path = out_path + ".mcmeta"
        with open(mcmeta_path, "w") as f:
            json.dump(mcmeta, f, indent=2)
        print(f"  Generated: {short}_{mod_name}.png.mcmeta")


def generate_overlay_model(item_name, mod_name):
    """Generate a model JSON for an overlay texture."""
    short = item_name.replace("alloy_", "")
    model = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"smelty:item/overlay/{short}_{mod_name}"
        }
    }
    out_path = os.path.join(MODELS_DIR, f"{short}_{mod_name}.json")
    with open(out_path, "w") as f:
        json.dump(model, f, indent=2)


def generate_item_definition(item_name, is_tool_head=False):
    """Generate the item definition JSON with composite + conditions."""
    # For tool heads, we need to handle the existing 2-layer model differently
    # Tool items use a different definition structure (head + handle tints)
    if is_tool_head:
        return  # Handled separately in generate_tool_item_definition

    short = item_name.replace("alloy_", "")

    # Build composite model
    models = []

    # Base model (layer0 = alloy shape, tinted with material blend color)
    models.append({
        "type": "minecraft:model",
        "model": f"smelty:item/{item_name}",
        "tints": [{
            "type": "minecraft:custom_model_data",
            "index": 0,
            "default": 10526880  # 0xA0A0A0 fallback
        }]
    })

    # Conditional overlays for each modifier
    for i, (mod_name, _, _) in enumerate(MODIFIERS):
        models.append({
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": i,
            "on_true": {
                "type": "minecraft:model",
                "model": f"smelty:item/overlay/{short}_{mod_name}",
            },
            "on_false": {
                "type": "minecraft:empty",
            }
        })

    item_def = {
        "model": {
            "type": "minecraft:composite",
            "models": models
        }
    }

    out_path = os.path.join(ITEMS_DIR, f"{item_name}.json")
    with open(out_path, "w") as f:
        json.dump(item_def, f, indent=2)
    print(f"  Updated item definition: {item_name}.json")


def generate_tool_item_definition(tool_name):
    """Generate tool item definition with composite overlay system.

    Tools have head (layer0, tint 0) + handle (layer1, tint 1).
    Overlays are applied on top of both layers.
    """
    # Tool name like "alloy_sword" -> head texture is "alloy_sword_head"
    head_name = f"{tool_name}_head"
    short_head = head_name.replace("alloy_", "")

    models = []

    # Base model (head + handle, 2 tints)
    models.append({
        "type": "minecraft:model",
        "model": f"smelty:item/{tool_name}",
        "tints": [
            {"type": "minecraft:custom_model_data", "index": 0, "default": 10526880},
            {"type": "minecraft:custom_model_data", "index": 1, "default": 10526880},
        ]
    })

    # Conditional overlays for each modifier (using head overlay textures)
    for i, (mod_name, _, _) in enumerate(MODIFIERS):
        models.append({
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": i,
            "on_true": {
                "type": "minecraft:model",
                "model": f"smelty:item/overlay/{short_head}_{mod_name}",
            },
            "on_false": {
                "type": "minecraft:empty",
            }
        })

    item_def = {
        "model": {
            "type": "minecraft:composite",
            "models": models
        }
    }

    out_path = os.path.join(ITEMS_DIR, f"{tool_name}.json")
    with open(out_path, "w") as f:
        json.dump(item_def, f, indent=2)
    print(f"  Updated tool item definition: {tool_name}.json")


def generate_armor_item_definition(armor_name):
    """Generate armor item definition with composite overlay system."""
    short = armor_name.replace("alloy_", "")

    models = []
    models.append({
        "type": "minecraft:model",
        "model": f"smelty:item/{armor_name}",
        "tints": [{
            "type": "minecraft:custom_model_data",
            "index": 0,
            "default": 10526880
        }]
    })

    for i, (mod_name, _, _) in enumerate(MODIFIERS):
        models.append({
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": i,
            "on_true": {
                "type": "minecraft:model",
                "model": f"smelty:item/overlay/{short}_{mod_name}",
            },
            "on_false": {
                "type": "minecraft:empty",
            }
        })

    item_def = {
        "model": {
            "type": "minecraft:composite",
            "models": models
        }
    }

    out_path = os.path.join(ITEMS_DIR, f"{armor_name}.json")
    with open(out_path, "w") as f:
        json.dump(item_def, f, indent=2)
    print(f"  Updated armor item definition: {armor_name}.json")


# ─── Main ─────────────────────────────────────────────────────────────────

def main():
    os.makedirs(OVERLAY_DIR, exist_ok=True)
    os.makedirs(MODELS_DIR, exist_ok=True)

    print("=== Generating Modifier Overlay Textures ===\n")

    for item_name in ALL_ITEMS:
        print(f"Processing {item_name}...")
        mask, w, h = load_alpha_mask(item_name)

        for mod_name, effect_type, params in MODIFIERS:
            generate_overlay_for_item(item_name, mod_name, effect_type, params, mask, w, h)
            generate_overlay_model(item_name, mod_name)

    print("\n=== Generating Item Definitions ===\n")

    for item_name in BASIC_ITEMS:
        generate_item_definition(item_name)

    # Tools: generate definitions for the tool items (not the head textures)
    tool_items = ["alloy_sword", "alloy_pickaxe", "alloy_axe",
                  "alloy_hoe", "alloy_shovel", "alloy_spear"]
    for tool_name in tool_items:
        generate_tool_item_definition(tool_name)

    for armor_name in ARMOR_ITEMS:
        generate_armor_item_definition(armor_name)

    print("\n=== Done! ===")
    print(f"Overlay textures in: {OVERLAY_DIR}")
    print(f"Overlay models in: {MODELS_DIR}")
    print(f"Item definitions updated in: {ITEMS_DIR}")


if __name__ == "__main__":
    main()

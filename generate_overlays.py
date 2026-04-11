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

# Tool head textures (overlays for the head/blade part)
TOOL_HEADS = [
    "alloy_sword_head", "alloy_pickaxe_head", "alloy_axe_head",
    "alloy_hoe_head", "alloy_shovel_head", "alloy_spear_head",
]

# Tool handle textures (overlays for the hilt/handle part)
TOOL_HANDLES = [
    "alloy_sword_handle", "alloy_pickaxe_handle", "alloy_axe_handle",
    "alloy_hoe_handle", "alloy_shovel_handle", "alloy_spear_handle",
]

# Armor textures (single-layer items)
ARMOR_ITEMS = ["alloy_helmet", "alloy_chestplate", "alloy_leggings", "alloy_boots"]

# All item types that get overlays (heads + handles + basic + armor)
ALL_ITEMS = BASIC_ITEMS + TOOL_HEADS + TOOL_HANDLES + ARMOR_ITEMS

NUM_MODIFIERS = 11  # len(MODIFIERS), used for flag index offsets

# Modifier definitions: (name, effect_type, effect_params)
MODIFIERS = [
    ("coal", "speckle", {
        # "coated in coal dust" — very dense coverage
        "colors": [(0x22, 0x22, 0x22), (0x44, 0x44, 0x44), (0x66, 0x66, 0x66)],
        "density": 0.55,  # uses density mode, not count
        "seed": 1,
    }),
    ("bonemeal", "marbling", {
        "color": (0xE8, 0xE4, 0xD4),
        "seed": 40,
    }),
    ("slime", "cell_noise", {
        "color": (0x7E, 0xBF, 0x6E),
        "seed": 20,
        "num_points": 6,
    }),
    ("clay", "cell_noise_inverted", {
        "color": (0xA4, 0x90, 0x7C),
        "seed": 30,
        "num_points": 6,
    }),
    ("lapis", "mineral_veins", {
        "color_bright": (0x6B, 0x8E, 0xE8),  # lighter blue
        "color_dim": (0x34, 0x5E, 0xC3),      # deep lapis blue
        "seed": 50,
    }),
    ("sugar", "speckle", {
        # Varying shades of white, like sugar crystals
        "colors": [(0xFF, 0xFF, 0xFF), (0xF0, 0xF0, 0xF0), (0xE0, 0xE0, 0xE0)],
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
        "colors": [(0xFF, 0xEE, 0x66)],
        "density": 0.40,
        "seed": 4,
        "frames": 16,
        "min_brightness": 0.55,
        "frametime": 6,
    }),
    ("redstone", "speckle_animated", {
        "colors": [(0xAA, 0x11, 0x11)],
        "density": 0.60,
        "seed": 5,
        "frames": 16,
        "min_brightness": 0.55,
        "frametime": 6,
    }),
    ("enderpearl", "julia_animated", {
        # Julia set with spiral/helix-like arms (Siegel disk region)
        "color_a": (0x0C, 0x7E, 0x5E),  # green-teal
        "color_b": (0x4C, 0x3E, 0x6E),  # purple-teal
        "c_real": 0.285,
        "c_imag": 0.01,
        "frames": 32,
        "frametime": 8,
        "seed": 6,
    }),
    ("meat", "perlin", {
        "colors": [(0xBB, 0x33, 0x33), (0x88, 0x22, 0x22)],
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


# ─── Noise Utilities ──────────────────────────────────────────────────────

def _noise_grid(seed, grid_size=5):
    """Generate a grid_size x grid_size grid of random floats [0, 1) from a seed."""
    grid = []
    h = int(hashlib.md5(f"noise_{seed}".encode()).hexdigest(), 16)
    for i in range(grid_size * grid_size):
        h = (h * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        grid.append((h & 0xFFFF) / 65535.0)
    return grid, grid_size


def _sample_noise(grid, grid_size, x, y, w, h):
    """Bilinear interpolation of noise grid at pixel (x, y) in a w×h image."""
    # Map pixel coords to grid coords
    gx = x / w * (grid_size - 1)
    gy = y / h * (grid_size - 1)
    x0 = int(gx)
    y0 = int(gy)
    x1 = min(x0 + 1, grid_size - 1)
    y1 = min(y0 + 1, grid_size - 1)
    fx = gx - x0
    fy = gy - y0
    # Bilinear interpolation
    v00 = grid[y0 * grid_size + x0]
    v10 = grid[y0 * grid_size + x1]
    v01 = grid[y1 * grid_size + x0]
    v11 = grid[y1 * grid_size + x1]
    top = v00 * (1 - fx) + v10 * fx
    bot = v01 * (1 - fx) + v11 * fx
    return top * (1 - fy) + bot * fy


def _pixel_hash(seed, x, y):
    """Fast per-pixel hash for color variation, independent of noise."""
    h = int(hashlib.md5(f"px_{seed}_{x}_{y}".encode()).hexdigest()[:8], 16)
    return h


def noise_powder(mask, w, h, colors, seed, density):
    """Generate powder positions using value noise.

    Returns list of (color, x, y, alpha) tuples for pixels that have powder.
    Noise creates organic clumps — denser in some areas, sparser in others.
    A second noise layer at a different frequency adds fine-grain variation.
    """
    grid1, gs1 = _noise_grid(seed, grid_size=5)  # broad bias so it's not perfectly uniform

    threshold = 1.0 - density

    result = []
    for x, y in sorted(mask):
        n1 = _sample_noise(grid1, gs1, x, y, w, h)
        # Raw per-pixel hash — no interpolation, fully granular
        px = _pixel_hash(seed + 50, x, y)
        n_raw = (px & 0xFFFF) / 65535.0
        # Mostly raw grain with a slight broad bias for subtle clustering
        n = n1 * 0.15 + n_raw * 0.85
        if n > threshold:
            # How far above threshold determines alpha (more = more opaque)
            strength = (n - threshold) / (1.0 - threshold)
            alpha = int(140 + 115 * strength)  # range 140-255
            color = colors[_pixel_hash(seed, x, y) % len(colors)]
            result.append((color, x, y, alpha))
    return result


# ─── Effect Generators ────────────────────────────────────────────────────

def generate_speckle(mask, w, h, colors, seed, density=None, count_per_color=None):
    """Generate powder/dust overlay using noise-based coverage."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = density if density is not None else 0.35
    for color, x, y, alpha in noise_powder(mask, w, h, colors, seed, d):
        img.putpixel((x, y), (*color, alpha))
    return img


def generate_speckle_animated(mask, w, h, colors, seed, frames, min_brightness,
                              density=None, count_per_color=None):
    """Generate animated powder overlay that pulses in brightness."""
    d = density if density is not None else 0.35
    powder = noise_powder(mask, w, h, colors, seed, d)

    img = Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))
    for frame in range(frames):
        t = frame / frames
        brightness = min_brightness + (1.0 - min_brightness) * (0.5 + 0.5 * math.sin(2 * math.pi * t))
        for color, x, y, alpha in powder:
            r = int(color[0] * brightness)
            g = int(color[1] * brightness)
            b = int(color[2] * brightness)
            a = int(alpha * (0.7 + 0.3 * brightness))  # alpha also pulses slightly
            img.putpixel((x, y + frame * h), (r, g, b, a))
    return img


def generate_marbling(mask, w, h, color, seed):
    """Generate marble veins running through the item.

    Uses domain-warped noise: the coordinates are distorted by one noise field
    before sampling a second, creating the organic winding veins of marble.
    """
    # Multiple noise grids at different frequencies
    warp_x_grid, warp_x_gs = _noise_grid(seed, grid_size=4)
    warp_y_grid, warp_y_gs = _noise_grid(seed + 100, grid_size=4)
    vein_grid, vein_gs = _noise_grid(seed + 200, grid_size=6)
    detail_grid, detail_gs = _noise_grid(seed + 300, grid_size=10)

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x, y in sorted(mask):
        # Domain warping: offset the lookup coordinates by noise
        wx = _sample_noise(warp_x_grid, warp_x_gs, x, y, w, h) - 0.5
        wy = _sample_noise(warp_y_grid, warp_y_gs, x, y, w, h) - 0.5
        warp_strength = 6.0
        warped_x = x + wx * warp_strength
        warped_y = y + wy * warp_strength

        # Sample vein pattern at warped coordinates
        n1 = _sample_noise(vein_grid, vein_gs, warped_x % w, warped_y % h, w, h)
        n2 = _sample_noise(detail_grid, detail_gs, warped_x % w, warped_y % h, w, h)

        # Create veins: sharp falloff around the 0.5 midpoint of noise
        vein_val = abs(n1 * 0.7 + n2 * 0.3 - 0.5) * 2.0  # 0 = vein center, 1 = far from vein
        vein_val = max(0.0, 1.0 - vein_val * 3.0)  # sharpen into thin veins

        if vein_val > 0.05:
            alpha = int(200 * vein_val)
            img.putpixel((x, y), (*color, min(alpha, 220)))
    return img


def generate_starfield(mask, w, h, color_bright, color_dim, seed, density):
    """Generate scattered bright points on a subtle dim field, like lapis lazuli flecks.

    Bright points are sparse and intense (the "stars"), with a faint dim noise
    underneath for a subtle base shimmer.
    """
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    # Subtle dim base — very faint smooth noise
    base_grid, base_gs = _noise_grid(seed, grid_size=5)
    for x, y in sorted(mask):
        n = _sample_noise(base_grid, base_gs, x, y, w, h)
        if n > 0.4:
            alpha = int(50 * (n - 0.4) / 0.6)  # very faint, 0-50 alpha
            img.putpixel((x, y), (*color_dim, alpha))

    # Bright star points — sparse, high intensity
    for x, y in sorted(mask):
        px = _pixel_hash(seed + 50, x, y)
        chance = (px & 0xFFFF) / 65535.0
        if chance < density:
            # Vary brightness per star
            brightness = 0.7 + 0.3 * ((px >> 16) & 0xFF) / 255.0
            r = int(color_bright[0] * brightness)
            g = int(color_bright[1] * brightness)
            b = int(color_bright[2] * brightness)
            img.putpixel((x, y), (r, g, b, 220))
    return img


def generate_mineral_veins(mask, w, h, color_bright, color_dim, seed):
    """Generate wavy horizontal bands of varying blue, like lapis ore layers in stone.

    Uses noise-warped horizontal bands to create organic mineral vein patterns.
    """
    warp_grid, warp_gs = _noise_grid(seed, grid_size=5)
    band_grid, band_gs = _noise_grid(seed + 100, grid_size=4)

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x, y in sorted(mask):
        # Warp the y coordinate for wavy bands
        warp = _sample_noise(warp_grid, warp_gs, x, y, w, h) - 0.5
        warped_y = y + warp * 5.0
        # Create bands using sin of warped y
        band_val = math.sin(warped_y * 1.2) * 0.5 + 0.5  # 0-1
        # Add some broad noise to break up uniformity
        broad = _sample_noise(band_grid, band_gs, x, y, w, h)
        combined = band_val * 0.6 + broad * 0.4

        # Threshold into vein regions
        if combined > 0.35:
            strength = (combined - 0.35) / 0.65
            # Blend between dim and bright based on strength
            r = int(color_dim[0] * (1 - strength) + color_bright[0] * strength)
            g = int(color_dim[1] * (1 - strength) + color_bright[1] * strength)
            b = int(color_dim[2] * (1 - strength) + color_bright[2] * strength)
            alpha = int(120 + 100 * strength)
            img.putpixel((x, y), (r, g, b, alpha))
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


def generate_cell_noise(mask, w, h, color, seed, num_points=6):
    """Generate inverted Worley/Voronoi cell noise — cell edges are visible, interiors are transparent.

    Scatters seed points, computes distance to nearest point for each pixel,
    then inverts so pixels NEAR cell edges (where two cells meet) are opaque.
    """
    # Generate deterministic seed points within the bounding box of the mask
    pixels = sorted(mask)
    if not pixels:
        return Image.new("RGBA", (w, h), (0, 0, 0, 0))

    min_x = min(x for x, y in pixels)
    max_x = max(x for x, y in pixels)
    min_y = min(y for x, y in pixels)
    max_y = max(y for x, y in pixels)

    points = []
    h_val = int(hashlib.md5(f"cell_{seed}".encode()).hexdigest(), 16)
    for _ in range(num_points):
        h_val = (h_val * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        px = min_x + (h_val & 0xFF) % (max_x - min_x + 1)
        h_val = (h_val * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        py = min_y + (h_val & 0xFF) % (max_y - min_y + 1)
        points.append((px, py))

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x, y in mask:
        # Find distances to the two nearest seed points
        dists = sorted(math.sqrt((x - px) ** 2 + (y - py) ** 2) for px, py in points)
        d1 = dists[0] if len(dists) > 0 else 0
        d2 = dists[1] if len(dists) > 1 else d1

        # Edge factor: small difference between nearest and second-nearest = on an edge
        edge = d2 - d1
        # Normalize — at 16x16, typical edge values are 0-4
        edge_norm = min(edge / 2.5, 1.0)
        # Invert: 0 = on edge (fully opaque), 1 = deep inside cell (transparent)
        alpha = int(200 * (1.0 - edge_norm))
        if alpha > 20:
            img.putpixel((x, y), (*color, alpha))
    return img


def generate_cell_noise_inverted(mask, w, h, color, seed, num_points=6):
    """Generate Worley/Voronoi cell noise — cell interiors are visible, edges are transparent.

    Inverse of generate_cell_noise: looks like dried/cracked mud chunks with gaps between them.
    """
    pixels = sorted(mask)
    if not pixels:
        return Image.new("RGBA", (w, h), (0, 0, 0, 0))

    min_x = min(x for x, y in pixels)
    max_x = max(x for x, y in pixels)
    min_y = min(y for x, y in pixels)
    max_y = max(y for x, y in pixels)

    points = []
    h_val = int(hashlib.md5(f"cell_{seed}".encode()).hexdigest(), 16)
    for _ in range(num_points):
        h_val = (h_val * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        px = min_x + (h_val & 0xFF) % (max_x - min_x + 1)
        h_val = (h_val * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        py = min_y + (h_val & 0xFF) % (max_y - min_y + 1)
        points.append((px, py))

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x, y in mask:
        dists = sorted(math.sqrt((x - px) ** 2 + (y - py) ** 2) for px, py in points)
        d1 = dists[0] if len(dists) > 0 else 0
        d2 = dists[1] if len(dists) > 1 else d1

        edge = d2 - d1
        edge_norm = min(edge / 2.5, 1.0)
        # NOT inverted: large edge diff = deep inside cell = opaque
        alpha = int(200 * edge_norm)
        if alpha > 20:
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


def generate_julia_animated(mask, w, h, color_a, color_b, c_real, c_imag, frames, seed):
    """Generate a Julia set fractal that zooms in over time.

    Each frame renders the Julia set at a progressively deeper zoom level,
    centered on an interesting boundary point. Colors are derived from
    iteration count banding, blending between color_a and color_b.
    The animation loops by zooming in for half the frames then back out.
    """
    pixels = sorted(mask)
    if not pixels:
        return Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))

    min_x = min(x for x, y in pixels)
    max_x = max(x for x, y in pixels)
    min_y = min(y for x, y in pixels)
    max_y = max(y for x, y in pixels)
    span_x = max(max_x - min_x, 1)
    span_y = max(max_y - min_y, 1)
    cx = (min_x + max_x) / 2.0
    cy = (min_y + max_y) / 2.0

    max_iter = 50
    escape_radius = 4.0
    base_scale = 3.0 / max(span_x, span_y)
    max_zoom = 8.0  # how far we zoom in at peak

    # Find a zoom target: a point on the Julia set boundary
    # Search for a pixel whose iteration count is in the mid-range (interesting detail)
    best_target = (0.0, 0.0)
    best_score = -1
    target_iter = max_iter // 3
    for x, y in pixels[::2]:  # sample every other pixel for speed
        zr = (x - cx) * base_scale
        zi = (y - cy) * base_scale
        iteration = 0
        for iteration in range(max_iter):
            if zr * zr + zi * zi > escape_radius:
                break
            zr, zi = zr * zr - zi * zi + c_real, 2 * zr * zi + c_imag
        score = max_iter - abs(iteration - target_iter)
        if score > best_score:
            best_score = score
            best_target = ((x - cx) * base_scale, (y - cy) * base_scale)

    zoom_cr, zoom_ci = best_target

    img = Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))
    for frame in range(frames):
        # Ping-pong zoom: 0→1→0 over the frame range for seamless looping
        t = frame / frames
        ping_pong = 1.0 - abs(2.0 * t - 1.0)  # 0 at edges, 1 at middle
        zoom = 1.0 + (max_zoom - 1.0) * ping_pong
        scale = base_scale / zoom
        # Pan toward the zoom target as we zoom in
        pan_r = zoom_cr * (1.0 - 1.0 / zoom)
        pan_i = zoom_ci * (1.0 - 1.0 / zoom)

        for x, y in pixels:
            zr = (x - cx) * scale + pan_r
            zi = (y - cy) * scale + pan_i

            iteration = 0
            for iteration in range(max_iter):
                zr2 = zr * zr
                zi2 = zi * zi
                if zr2 + zi2 > escape_radius:
                    break
                zi = 2 * zr * zi + c_imag
                zr = zr2 - zi2 + c_real

            if iteration < max_iter - 1:
                # Use iteration banding for color
                smooth_iter = iteration + 1 - math.log(math.log(max(zr*zr + zi*zi, 1.001))) / math.log(2)
                band = (math.sin(smooth_iter * 0.8) + 1.0) / 2.0  # oscillate 0-1
                r = int(color_a[0] * (1 - band) + color_b[0] * band)
                g = int(color_a[1] * (1 - band) + color_b[1] * band)
                b = int(color_a[2] * (1 - band) + color_b[2] * band)
                # Alpha: boundary pixels (low iteration) are most opaque
                norm_iter = smooth_iter / max_iter
                alpha = int(200 * (1.0 - min(norm_iter * 1.5, 1.0)))
                if alpha > 15:
                    img.putpixel((x, y + frame * h), (r, g, b, alpha))
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


def generate_perlin(mask, w, h, colors, seed):
    """Generate smooth Perlin-like noise that blends between colors.

    Uses multiple octaves of value noise for organic, fleshy-looking patterns.
    """
    grid1, gs1 = _noise_grid(seed, grid_size=4)       # large smooth shapes
    grid2, gs2 = _noise_grid(seed + 100, grid_size=7)  # medium detail
    grid3, gs3 = _noise_grid(seed + 200, grid_size=11) # fine detail

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x, y in sorted(mask):
        n1 = _sample_noise(grid1, gs1, x, y, w, h)
        n2 = _sample_noise(grid2, gs2, x, y, w, h)
        n3 = _sample_noise(grid3, gs3, x, y, w, h)
        # Octave blend — mostly smooth with some detail
        n = n1 * 0.5 + n2 * 0.35 + n3 * 0.15
        # Blend between colors based on noise value
        c0 = colors[0]
        c1 = colors[1] if len(colors) > 1 else colors[0]
        r = int(c0[0] * (1 - n) + c1[0] * n)
        g = int(c0[1] * (1 - n) + c1[1] * n)
        b = int(c0[2] * (1 - n) + c1[2] * n)
        alpha = int(140 + 80 * n)  # 140-220 range
        img.putpixel((x, y), (r, g, b, alpha))
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
    # Mix item name into the seed so each item × modifier combo gets a unique pattern
    item_hash = int(hashlib.md5(item_name.encode()).hexdigest()[:8], 16)
    unique_seed = params["seed"] * 10000 + item_hash if "seed" in params else item_hash

    if effect_type == "speckle":
        img = generate_speckle(mask, w, h, params["colors"], unique_seed,
                               density=params.get("density"),
                               count_per_color=params.get("count_per_color"))
    elif effect_type == "speckle_animated":
        img = generate_speckle_animated(mask, w, h, params["colors"], unique_seed,
                                        params["frames"], params["min_brightness"],
                                        density=params.get("density"),
                                        count_per_color=params.get("count_per_color"))
    elif effect_type == "edge":
        img = generate_edge(mask, w, h, params["color"], params["fade_steps"])
    elif effect_type == "marbling":
        img = generate_marbling(mask, w, h, params["color"], unique_seed)
    elif effect_type == "mineral_veins":
        img = generate_mineral_veins(mask, w, h, params["color_bright"], params["color_dim"],
                                     unique_seed)
    elif effect_type == "cell_noise":
        img = generate_cell_noise(mask, w, h, params["color"], unique_seed,
                                  num_points=params.get("num_points", 6))
    elif effect_type == "cell_noise_inverted":
        img = generate_cell_noise_inverted(mask, w, h, params["color"], unique_seed,
                                           num_points=params.get("num_points", 6))
    elif effect_type == "stripe":
        img = generate_stripe(mask, w, h, params["color"],
                              params["direction"], params["period"], params["width"])
    elif effect_type == "julia_animated":
        img = generate_julia_animated(mask, w, h, params["color_a"], params["color_b"],
                                      params["c_real"], params["c_imag"],
                                      params["frames"], unique_seed)
    elif effect_type == "static_swirl_animated":
        img = generate_static_swirl_animated(mask, w, h, params["color_a"], params["color_b"],
                                             params["frames"], unique_seed)
    elif effect_type == "perlin":
        img = generate_perlin(mask, w, h, params["colors"], unique_seed)
    elif effect_type == "grainy":
        img = generate_grainy(mask, w, h, params["colors"],
                              params["density"], unique_seed)
    else:
        raise ValueError(f"Unknown effect type: {effect_type}")

    # Derive short item name for filename (e.g., "alloy_ingot" -> "ingot")
    short = item_name.replace("alloy_", "")
    out_path = os.path.join(OVERLAY_DIR, f"{short}_{mod_name}.png")
    img.save(out_path)
    print(f"  Generated: {short}_{mod_name}.png")

    # Write .mcmeta for animated textures
    if effect_type in ("speckle_animated", "static_swirl_animated", "julia_animated"):
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
    Head modifier overlays use flags[0..10], handle overlays use flags[11..21].
    """
    head_name = f"{tool_name}_head"
    handle_name = f"{tool_name}_handle"
    short_head = head_name.replace("alloy_", "")
    short_handle = handle_name.replace("alloy_", "")

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

    # Head modifier overlays — flags[0..10]
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

    # Handle modifier overlays — flags[11..21]
    for i, (mod_name, _, _) in enumerate(MODIFIERS):
        models.append({
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": NUM_MODIFIERS + i,
            "on_true": {
                "type": "minecraft:model",
                "model": f"smelty:item/overlay/{short_handle}_{mod_name}",
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

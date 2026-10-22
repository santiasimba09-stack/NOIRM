"""gen_particles.py: draws NOIRM's fire and lightning particle sprites (our own, from noise and maths).

Writes into src/main/resources/assets/noirm/textures/particle/:
  flame_0..3.png   cellular flame masses: yellow-white veins between orange cells, deep red edges,
                   a dark smoky rim (docs/ART_STYLE.md, "Jutsu effects")
  tongue_0..2.png  curling flame tongues (the licks that rise off fire)
  zap_0..2.png     lightning spark bursts: sharp "ice-shard" spikes around a hot centre (drawn white,
                   the game tints them blue)
  ash_0..1.png     ash flakes (grey; the game tints them)
  soot_0..3.png    soft billowing smoke puffs (light grey with a lit top; the game tints them dark)
and the matching sprite lists in assets/noirm/particles/.

Run from the repository root: python3 tools/vfxgen/gen_particles.py
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/noirm/textures/particle"
LISTS = "src/main/resources/assets/noirm/particles"

# Fire colours (RGB): white-hot, yellow, orange, deep red, smoky rim.
WHITE_HOT = np.array([255, 250, 214], float)
YELLOW = np.array([255, 214, 64], float)
ORANGE = np.array([250, 120, 18], float)
RED = np.array([190, 34, 8], float)
SMOKE = np.array([40, 16, 10], float)


def lerp(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return a * (1 - t) + b * t


def save(arr, name):
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name + ".png"))


def value_noise(size, cells, rng):
    """Smooth noise in [0, 1] (bilinear value noise)."""
    grid = rng.random((cells + 1, cells + 1))
    ys, xs = np.mgrid[0:size, 0:size] / (size - 1) * cells
    x0, y0 = np.floor(xs).astype(int).clip(0, cells - 1), np.floor(ys).astype(int).clip(0, cells - 1)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = grid[y0, x0], grid[y0, x0 + 1]
    c, d = grid[y0 + 1, x0], grid[y0 + 1, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def flame_mass(seed, size=64):
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    cx = cy = (size - 1) / 2
    dx, dy = (xs - cx) / (size / 2), (ys - cy) / (size / 2)
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    # A lumpy outline: a few lobes plus flame licks pointing up (negative y is up in the image).
    edge = 0.78 + 0.07 * np.sin(ang * 5 + rng.random() * 6) + 0.05 * np.sin(ang * 9 + rng.random() * 6)
    up = np.clip(-np.sin(ang), 0, 1)
    edge += 0.12 * up * np.clip(np.sin(ang * 13 + rng.random() * 6), 0, 1)
    edge += 0.06 * (value_noise(size, 6, rng) - 0.5)
    # Voronoi cells: the bubbling "cellular" look.
    n = 9 + int(rng.integers(0, 4))
    seeds = rng.random((n, 2)) * 1.5 - 0.75
    d = np.stack([np.hypot(dx - sx, dy - sy) for sx, sy in seeds])
    d.sort(axis=0)
    gap = d[1] - d[0]
    vein = np.exp(-gap / 0.065)
    # Each cell is a little darker in its middle (a bubble seen from the side).
    centre = np.clip(d[0] / 0.35, 0, 1)
    body = lerp(RED, ORANGE, 0.45 + 0.55 * centre)
    heat = np.clip(1.05 - r / edge, 0, 1)  # hotter toward the middle
    body = lerp(body, YELLOW, heat * 0.35)
    color = lerp(body, YELLOW, vein * 0.9)
    color = lerp(color, WHITE_HOT, vein * heat * 1.3)
    # Toward the outline: deep red, then a dark smoky rim.
    t = r / edge
    color = lerp(color, RED, np.clip((t - 0.78) / 0.12, 0, 1))
    color = lerp(color, SMOKE, np.clip((t - 0.9) / 0.1, 0, 1))
    alpha = np.clip((1.06 - t) / 0.12, 0, 1) * 255
    return np.dstack([color, alpha])


def tongue(seed, size=32):
    """A flame tongue: wide at the bottom, a curling tip at the top."""
    rng = random.Random(seed)
    img = np.zeros((size, size, 4), float)
    curl = (rng.random() - 0.5) * 1.6
    wobble = rng.random() * 6
    for y in range(size):
        v = 1 - y / (size - 1)  # 0 at the bottom, 1 at the tip
        half = (size * 0.32) * (1 - v) ** 0.7 * (0.85 + 0.15 * math.sin(v * 9 + wobble))
        spine = size / 2 + curl * size * 0.35 * v * v + math.sin(v * 5 + wobble) * size * 0.05 * v
        for x in range(size):
            off = abs(x - spine)
            if half <= 0.4 or off > half + 1:
                continue
            t = off / max(0.5, half)
            core = max(0.0, 1 - t * 1.6) * (1 - v * 0.6)
            c = ORANGE * (1 - min(1, t)) + RED * min(1, t)
            c = c * (1 - core) + (YELLOW * 0.4 + WHITE_HOT * 0.6) * core
            if t > 0.86:
                c = c * 0.4 + SMOKE * 0.6
            a = max(0.0, min(1.0, (half + 1 - off))) * min(1.0, (1 - v) * 6 + 0.2)
            img[y, x] = [*c, a * 255]
    return img


def zap(seed, size=32):
    """A spark burst: 6-9 sharp shards around a hot centre (white; tinted in game)."""
    rng = random.Random(seed)
    img = np.zeros((size, size), float)
    c = (size - 1) / 2
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    dx, dy = xs - c, ys - c
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    img = np.maximum(img, np.clip(1.2 - r / (size * 0.14), 0, 1))
    for _ in range(rng.randint(6, 9)):
        a0 = rng.random() * math.tau
        length = size * (0.34 + 0.15 * rng.random())
        width = 0.20 + 0.12 * rng.random()
        da = np.angle(np.exp(1j * (ang - a0)))
        along = r / length
        # A shard: wide near the middle, a needle point at the end, with a kink partway.
        kink = 0.04 * math.sin(rng.random() * 6)
        shard = np.clip(1 - np.abs(da - kink * along) / (width * (1 - along) + 1e-3), 0, 1) * (along < 1)
        img = np.maximum(img, np.clip(shard * 1.6, 0, 1) * np.clip(1.3 - along * 0.8, 0, 1))
    lum = np.clip(img * 1.3, 0, 1)
    rgb = np.dstack([lum * 255, lum * 255, lum * 255])
    return np.dstack([rgb, np.clip(img * 1.6, 0, 1) * 255])


def ash(seed, size=12):
    rng = np.random.default_rng(seed)
    img = np.zeros((size, size, 4), float)
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    c = (size - 1) / 2
    mask = (np.hypot((xs - c) * 1.4, ys - c) < size * 0.42 + (value_noise(size, 3, rng) - 0.5) * size * 0.5)
    shade = 120 + 80 * value_noise(size, 4, rng)
    img[..., 0] = shade
    img[..., 1] = shade
    img[..., 2] = shade
    img[..., 3] = mask * 255
    return img


def soot(seed, size=64):
    """A billowing puff: lumpy round outline, soft inside, lit a little from the top-left."""
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    c = (size - 1) / 2
    dx, dy = (xs - c) / (size / 2), (ys - c) / (size / 2)
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    edge = 0.8 + 0.08 * np.sin(ang * 4 + rng.random() * 6) + 0.05 * np.sin(ang * 7 + rng.random() * 6)
    n = value_noise(size, 5, rng) * 0.6 + value_noise(size, 11, rng) * 0.4
    t = r / edge
    alpha = np.clip((1.0 - t) / 0.35, 0, 1) * (0.55 + 0.45 * n)
    light = np.clip(0.55 + 0.35 * (-dx - dy) * 0.5 + 0.25 * (n - 0.5), 0, 1)
    shade = 150 + 105 * light
    return np.dstack([shade, shade, shade, alpha * 235])


def write_list(kind, names):
    with open(os.path.join(LISTS, kind + ".json"), "w") as f:
        json.dump({"textures": [f"noirm:{n}" for n in names]}, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    flames = []
    for i in range(4):
        save(flame_mass(100 + i, 128), f"flame_{i}")
        flames.append(f"flame_{i}")
    tongues = []
    for i in range(3):
        save(tongue(200 + i, 64), f"tongue_{i}")
        tongues.append(f"tongue_{i}")
    zaps = []
    for i in range(3):
        save(zap(300 + i), f"zap_{i}")
        zaps.append(f"zap_{i}")
    ashes = []
    for i in range(2):
        save(ash(400 + i), f"ash_{i}")
        ashes.append(f"ash_{i}")
    soots = []
    for i in range(4):
        save(soot(500 + i), f"soot_{i}")
        soots.append(f"soot_{i}")
    write_list("soot", soots)
    write_list("flame", flames)
    write_list("tongue", tongues)
    write_list("zap", zaps)
    write_list("ash", ashes)
    # A glow that lies flat on the ground (light pooling under lightning) reuses the glow sprites.
    write_list("ground_glow", ["glow_0", "glow_1"])
    print("wrote", len(flames) + len(tongues) + len(zaps) + len(ashes), "sprites")

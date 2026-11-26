"""gen_particles_kekkei_a.py: particle sprites for Ice, Lava and Boil release (our own, from noise and maths).

Writes into src/main/resources/assets/noirm/textures/particle/:
  crystal_0..3.png  ice crystals: a cluster of sharp faceted shards, white-cyan faces with lit and
                    shadowed sides, deep blue edges and a white glint star (drawn in colour; the game
                    keeps them as drawn with a white tint)
  magma_0..3.png    glowing cellular magma: black crust plates with yellow-white cracks between them
                    and an orange glow seeping through (drawn in colour)
  steam_0..3.png    thick boiling steam: a dense, lumpy billow, bright on top, grey underneath
                    (light grey; the game tints it)
and the matching sprite lists in assets/noirm/particles/.

Run from the repository root: python3 tools/vfxgen/gen_particles_kekkei_a.py
"""
import json
import math
import os

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/noirm/textures/particle"
LISTS = "src/main/resources/assets/noirm/particles"

ICE_WHITE = np.array([248, 254, 255], float)
ICE_LIGHT = np.array([196, 240, 255], float)
ICE_MID = np.array([110, 200, 245], float)
ICE_DEEP = np.array([22, 70, 150], float)
ICE_INK = np.array([8, 24, 60], float)

MAGMA_WHITE = np.array([255, 246, 196], float)
MAGMA_YELLOW = np.array([255, 200, 60], float)
MAGMA_ORANGE = np.array([240, 90, 10], float)
MAGMA_RED = np.array([140, 22, 4], float)
CRUST = np.array([26, 16, 14], float)
CRUST_LIT = np.array([70, 50, 44], float)


def lerp(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return a * (1 - t) + b * t


def save(arr, name):
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name + ".png"))


def value_noise(size, cells, rng):
    grid = rng.random((cells + 1, cells + 1))
    ys, xs = np.mgrid[0:size, 0:size] / (size - 1) * cells
    x0, y0 = np.floor(xs).astype(int).clip(0, cells - 1), np.floor(ys).astype(int).clip(0, cells - 1)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = grid[y0, x0], grid[y0, x0 + 1]
    c, d = grid[y0 + 1, x0], grid[y0 + 1, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def crystal(seed, size=64):
    """A cluster of 3-5 faceted shards fanning out of one root, each lit on one face, shadowed on the other."""
    rng = np.random.default_rng(seed)
    img = np.zeros((size, size, 4), float)
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    root = np.array([size * (0.44 + 0.12 * rng.random()), size * (0.8 + 0.06 * rng.random())])
    shards = int(rng.integers(3, 6))
    depth = np.full((size, size), -1.0)
    tip = root.copy()
    for k in range(shards):
        angle = -math.pi / 2 + (k - (shards - 1) / 2) * (0.5 + 0.2 * rng.random()) + (rng.random() - 0.5) * 0.3
        length = size * (0.34 + 0.2 * rng.random()) * (1.25 if k == shards // 2 else 1)
        width = size * (0.07 + 0.05 * rng.random())
        d = np.array([math.cos(angle), math.sin(angle)])
        if k == shards // 2:
            tip = root + d * length * 0.9
        n = np.array([-d[1], d[0]])
        rx, ry = xs - root[0], ys - root[1]
        along = (rx * d[0] + ry * d[1]) / length
        across = (rx * n[0] + ry * n[1]) / width
        # A long hexagonal prism seen from the side, ending in a sharp point.
        taper = np.where(along < 0.72, 1.0, np.clip((1 - along) / 0.28, 0, 1))
        inside = (along > -0.05) & (along < 1) & (np.abs(across) < taper)
        # Facets: the lit left face, a bright ridge, the shadowed right face.
        face = across / np.maximum(taper, 1e-3)
        col = np.where(face[..., None] < -0.15, lerp(ICE_LIGHT, ICE_WHITE, np.clip(-face - 0.15, 0, 1) * 0.6 + along * 0.4),
                       np.where(face[..., None] < 0.15, ICE_WHITE[None, None, :] * np.ones_like(face)[..., None],
                                lerp(ICE_MID, ICE_DEEP, np.clip(face - 0.15, 0, 1) * 1.3)))
        # Ink outline along the shard's edges and a cold dark root.
        edge = np.clip((np.abs(face) - 0.82) / 0.18, 0, 1)
        col = lerp(col, ICE_INK, edge * 0.85)
        col = lerp(col, ICE_DEEP, np.clip(0.15 - along, 0, 1) * 4)
        mask = inside & (along > depth)
        img[mask, :3] = col[mask]
        img[mask, 3] = 255
        depth = np.where(mask, along, depth)
    # Frost grain on the faces.
    grain = value_noise(size, 16, rng)
    img[..., :3] = img[..., :3] * (0.9 + 0.2 * grain[..., None])
    # A glint star near the tip of the tallest shard.
    gx, gy = tip[0], tip[1]
    dx, dy = xs - gx, ys - gy
    star = np.clip(1 - (np.abs(dx) * np.abs(dy)) / (size * 0.6), 0, 1) * np.clip(1 - np.hypot(dx, dy) / (size * 0.22), 0, 1)
    star = np.clip(star * 1.6, 0, 1)
    img[..., :3] = img[..., :3] * (1 - star[..., None]) + 255 * star[..., None]
    img[..., 3] = np.maximum(img[..., 3], star * 255)
    return img


def magma(seed, size=64):
    """Cellular magma: black crust plates, yellow-white cracks between them, orange glow under thin crust."""
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    c = (size - 1) / 2
    dx, dy = (xs - c) / (size / 2), (ys - c) / (size / 2)
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    edge = 0.78 + 0.05 * np.sin(ang * 3 + rng.random() * 6) + 0.05 * np.sin(ang * 7 + rng.random() * 6) + 0.12 * (value_noise(size, 5, rng) - 0.5)
    n = 11 + int(rng.integers(0, 5))
    seeds = rng.random((n, 2)) * 1.6 - 0.8
    d = np.stack([np.hypot(dx - sx, dy - sy) for sx, sy in seeds])
    d.sort(axis=0)
    gap = d[1] - d[0]
    crack = np.exp(-gap / 0.05)
    glow = np.exp(-gap / 0.16)
    # Each plate: dark crust, lit a little on its upper-left, thinner (hotter) toward its rim.
    lit = np.clip(0.5 + 0.5 * (-dx - dy) * 0.5, 0, 1)
    plate = lerp(CRUST, CRUST_LIT, lit * 0.6 + 0.3 * value_noise(size, 12, rng))
    plate = lerp(plate, MAGMA_RED, glow * 0.45)
    color = lerp(plate, MAGMA_ORANGE, np.clip(crack * 1.4, 0, 1))
    color = lerp(color, MAGMA_YELLOW, np.clip(crack * 1.6 - 0.4, 0, 1))
    color = lerp(color, MAGMA_WHITE, np.clip(crack * 2.0 - 1.2, 0, 1))
    t = r / edge
    color = lerp(color, CRUST, np.clip((t - 0.85) / 0.12, 0, 1))
    alpha = np.clip((1.04 - t) / 0.1, 0, 1) * 255
    return np.dstack([color, alpha])


def steam(seed, size=64):
    """A dense billow of steam: several lumps, opaque in the middle, bright tops, grey undersides."""
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    c = (size - 1) / 2
    dx, dy = (xs - c) / (size / 2), (ys - c) / (size / 2)
    body = np.zeros((size, size))
    shade = np.zeros((size, size))
    for _ in range(5 + int(rng.integers(0, 3))):
        bx, by = (rng.random() - 0.5) * 0.9, (rng.random() - 0.5) * 0.7
        br = 0.35 + 0.25 * rng.random()
        rr = np.hypot(dx - bx, dy - by) / br
        lump = np.clip(1 - rr, 0, 1)
        body = np.maximum(body, lump)
        # Each lump is lit from above: bright on top, shadowed under.
        shade = np.maximum(shade, lump * np.clip(0.5 - (dy - by) / br * 0.6, 0, 1))
    n = value_noise(size, 6, rng) * 0.6 + value_noise(size, 14, rng) * 0.4
    alpha = np.clip(body * 2.4 - 0.1 + (n - 0.5) * 0.5, 0, 1)
    light = np.clip(0.45 + 0.6 * shade + 0.2 * (n - 0.5), 0, 1)
    tone = 150 + 105 * light
    return np.dstack([tone, tone, tone, alpha * 245])


def write_list(kind, names):
    with open(os.path.join(LISTS, kind + ".json"), "w") as f:
        json.dump({"textures": [f"noirm:{n}" for n in names]}, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for kind, fn, base, size in (("crystal", crystal, 600, 64), ("magma", magma, 700, 96), ("steam", steam, 800, 64)):
        names = []
        for i in range(4):
            save(fn(base + i, size), f"{kind}_{i}")
            names.append(f"{kind}_{i}")
        write_list(kind, names)
    print("wrote crystal, magma and steam sprites")

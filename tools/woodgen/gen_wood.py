#!/usr/bin/env python3
"""Wood Release (Mokuton) art: raw grey-green wood with deep ink grain (docs/ART_STYLE.md).

Writes
    assets/noirm/textures/block/mokuton_wood.png       the side of a Wood Release trunk (32x32, grain runs up)
    assets/noirm/textures/block/mokuton_wood_top.png   its cut end (32x32, growth rings)
    assets/noirm/textures/entity/wood_release/bark.png  bark for the Wood Dragon and the colossus (64x64)
    assets/noirm/textures/entity/wood_release/clone.png a wood clone's body (64x64, the player skin
        layout): bark everywhere, with a face, collar and joints carved into the grain. The owner's
        own skin is laid over it faintly at runtime, so the clone looks carved from them.
    docs/screenshots/cw-wood-textures.png               a 4x preview of all four

All art is original and drawn by the code below (seeded noise and hand-placed strokes); nothing
is traced. Standard library only. Run from the repo root:  python3 tools/woodgen/gen_wood.py
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, hx, mix, noise, SKIN_PARTS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm/textures")
PREVIEW = os.path.join(ROOT, "docs/screenshots/cw-wood-textures.png")

# Raw grey-green wood: a pale silvery highlight, the green-grey body, a deep moss shadow, ink.
LIGHT = hx("#b4bb9a")
BODY = hx("#7f8a68")
SHADE = hx("#525c43")
DEEP = hx("#323a29")
INK = hx("#14170f")


def smooth(x, y, seed, scale, period=64):
    """Value noise, smoothly interpolated between lattice points (wraps every `period` px so textures tile)."""
    gx, gy = x / scale, y / scale
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - x0, gy - y0
    cells = max(1, int(round(period / scale)))

    def n(a, b):
        return noise(a % cells, b % cells, seed)
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    top = n(x0, y0) * (1 - fx) + n(x0 + 1, y0) * fx
    bot = n(x0, y0 + 1) * (1 - fx) + n(x0 + 1, y0 + 1) * fx
    return top * (1 - fy) + bot * fy


def ramp(t):
    """0 = ink … 1 = highlight, along the wood's colour ramp."""
    stops = [(0.0, INK), (0.18, DEEP), (0.42, SHADE), (0.7, BODY), (1.0, LIGHT)]
    t = max(0.0, min(1.0, t))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if t <= b:
            return mix(ca, cb, (t - a) / (b - a))
    return LIGHT


def grain(w, h, seed, fibres, wobble, knots=()):
    """Bark grain running up the texture: fibres that wander, ink furrows between them, knots
    the grain bends round. Returns an h×w array of 0..1 brightness."""
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            # The fibre position bends with low noise (and round each knot).
            bend = (smooth(x, y, seed, 16, w) - 0.5) * wobble + (smooth(x, y, seed + 7, 4, w) - 0.5) * wobble * 0.35
            for kx, ky, kr in knots:
                dx, dy = x - kx, y - ky
                d = math.hypot(dx, dy)
                if d < kr * 3:
                    bend += (dx / (d + 0.5)) * (kr * 3 - d) * 0.6
            phase = (x + bend) / (w / fibres)
            fibre = 0.5 + 0.5 * math.cos(phase * math.pi * 2)
            furrow = fibre ** 6  # thin, deep ink furrows
            v = 0.56 + 0.3 * smooth(x, y, seed + 3, 4, w) - 0.6 * furrow
            v += (noise(x, y, seed + 11) - 0.5) * 0.12
            # Weathered silver streaks on the ridges.
            if fibre < 0.12 and noise(x // 2, y // 5, seed + 5) > 0.7:
                v += 0.18
            out[y][x] = v
    for kx, ky, kr in knots:
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - kx, (y - ky) * 0.7)
                if d < kr:
                    ring = 0.5 + 0.5 * math.cos(d * 2.2)
                    out[y][x] = 0.12 + 0.35 * ring * (d / kr)
                elif d < kr + 1.2:
                    out[y][x] = 0.05  # the knot's ink rim
    return out


def cracks(arr, seed, count, length):
    """A few dark splits running along the grain."""
    h, w = len(arr), len(arr[0])
    for i in range(count):
        x = noise(i, 1, seed) * w
        y = noise(i, 2, seed) * h
        for step in range(length):
            xi, yi = int(x) % w, int(y) % h
            arr[yi][xi] = min(arr[yi][xi], 0.04)
            if noise(i, step, seed + 9) > 0.6:
                arr[yi][(xi + 1) % w] = min(arr[yi][(xi + 1) % w], 0.2)
            y += 1
            x += (noise(i, step, seed + 4) - 0.5) * 1.4


def to_canvas(arr):
    h, w = len(arr), len(arr[0])
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            c.set(x, y, ramp(arr[y][x]))
    return c


def side():
    arr = grain(32, 32, 101, fibres=7, wobble=6, knots=[(21, 11, 2.2)])
    cracks(arr, 102, 3, 12)
    return to_canvas(arr)


def top():
    w = h = 32
    arr = [[0.0] * w for _ in range(h)]
    cx, cy = 15.5, 15.5
    for y in range(h):
        for x in range(w):
            # Squarish rings, so the cut end fills the block like a sawn beam.
            dx, dy = abs(x - cx), abs(y - cy)
            d = max(dx, dy) * 0.55 + math.hypot(dx, dy) * 0.4 + (smooth(x, y, 201, 5, 32) - 0.5) * 2.2
            if d > 14.6:
                arr[y][x] = 0.1 if d > 15.4 else 0.28 + 0.2 * noise(x, y, 202)  # bark rim
                continue
            ring = 0.5 + 0.5 * math.cos(d * 1.6)
            arr[y][x] = 0.55 + 0.35 * (1 - ring ** 4) - 0.45 * ring ** 10 + (noise(x, y, 203) - 0.5) * 0.1
    # Radial checks (the end splits as it dries).
    for a in (0.4, 2.1, 4.0):
        for r in range(2, 13):
            arr[int(cy + math.sin(a) * r)][int(cx + math.cos(a) * r)] = 0.06
    return to_canvas(arr)


def bark():
    arr = grain(64, 64, 301, fibres=10, wobble=10, knots=[(14, 40, 2.6), (47, 13, 2.0)])
    cracks(arr, 302, 6, 22)
    # Shallow overlapping plates (the dragon's bark scales): a dark under-edge and a lit lip.
    for y in range(64):
        for x in range(64):
            # Each column of plates is offset, and each plate's edge curves down at its sides.
            u = ((x % 8) - 3.5) / 3.5
            k = (y + (x // 8) * 5 - int(round(2.5 * u * u))) % 12
            if k == 0:
                arr[y][x] = min(arr[y][x], 0.14)
            elif k == 1:
                arr[y][x] = min(1.0, arr[y][x] + 0.14)
    return to_canvas(arr)


def clone():
    """The player skin layout in bark, with a carved face and seams at the joints."""
    arr = grain(64, 64, 401, fibres=12, wobble=7)
    cracks(arr, 402, 5, 14)
    c = to_canvas(arr)
    # Only the base layer is wood; the outer layer (hat, jacket, sleeves, pants) stays clear.
    for part in ("hat", "jacket", "rsleeve", "lsleeve", "rpants", "lpants"):
        u, v, w, h, d = SKIN_PARTS[part]
        for yy in range(v, v + h + d):
            for xx in range(u, u + 2 * (w + d)):
                c.set(xx, yy, (0, 0, 0, 0))
    # The face (front of the head, 8x8 at 8,8): a heavy brow, two hollow eyes, a closed mouth seam.
    fx, fy = 8, 8
    ink, deep, light = INK + (255,), DEEP + (255,), LIGHT + (255,)
    for x in range(1, 7):
        c.set(fx + x, fy + 1, light)
        c.set(fx + x, fy + 2, deep)
    for ex in (1, 5):
        for dx in (0, 1):
            c.set(fx + ex + dx, fy + 3, ink)
            c.set(fx + ex + dx, fy + 4, deep)
    c.set(fx + 3, fy + 5, deep)
    c.set(fx + 4, fy + 5, deep)
    for x in range(2, 6):
        c.set(fx + x, fy + 6, ink)
    # Seams: collar and belt on the body, elbows and knees as dark rings.
    for part, rows in (("body", (0, 7)), ("rarm", (5,)), ("larm", (5,)), ("rleg", (6,)), ("lleg", (6,))):
        u, v, w, h, d = SKIN_PARTS[part]
        for r in rows:
            for xx in range(u, u + 2 * (w + d)):
                c.set(xx, v + d + r, ink)
                c.set(xx, v + d + r + 1, light if xx % 3 else deep)
    return c


def preview(images):
    scale = 4
    width = sum(img.w * scale + 8 for img in images) + 8
    height = max(img.h for img in images) * scale + 16
    out = Canvas(width, height, (24, 26, 22, 255))
    x = 8
    for img in images:
        out.blit(img, 0, 0, img.w, img.h, x, 8, scale=scale)
        x += img.w * scale + 8
    out.save(PREVIEW)


if __name__ == "__main__":
    s, t, b, c = side(), top(), bark(), clone()
    s.save(os.path.join(ASSETS, "block/mokuton_wood.png"))
    t.save(os.path.join(ASSETS, "block/mokuton_wood_top.png"))
    b.save(os.path.join(ASSETS, "entity/wood_release/bark.png"))
    c.save(os.path.join(ASSETS, "entity/wood_release/clone.png"))
    preview([s, t, b, c])
    print("wrote Wood Release textures")

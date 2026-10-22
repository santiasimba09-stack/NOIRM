#!/usr/bin/env python3
"""Gore textures for NOIRM (blueprint section 7), v2: damage marks, blood, ground decals, wounds.

Drawn at SCALE x the Minecraft layout (the owner's art direction: 4x, so 256x256 for the 64x64
skin layout and 256x128 for the 64x32 armor layout). Every brush works in layout pixels and is
sampled per output pixel, so the marks keep their shape at any SCALE (try --scale 1 or 8).

Body marks (player-skin layout, painted on the skin's OUTER layer regions: hat, jacket,
sleeves, pants), three nested stages per kind: stage 2 holds every mark of stage 1 (a little
bigger) plus new ones. client/GoreMarksLayer draws the stage reached and fades in the next.

    bruise  plum and blue blotches with yellow-green edges (fists, blunt hits)
    dirt    brown speckled grime, mostly low on the legs (falls, knockdowns, earth)
    soot    charcoal smudges (fire, blasts, lightning)
    cut     dark-red slashes that weep a little (blades, kunai, wind)
    blood   splatters with drips running down from them (drawn bright red; the client tints it
            darker as it dries)

Clothing marks (armor layout, 64x32 at 1x): the same marks, minus bruises, on the head, body,
arm and leg rects, so worn gear gets dirty, burnt, torn and bloody too (client/GoreArmorLayer).

Ground decals (blood_decals.png, 4x2 cells of 16x16 at 1x): six splat shapes and two pools,
drawn bright red (tinted darker as they dry). Particles: blood_drop_0..2. Wound cap: wound.png.

Palette from the blueprint's "ash, iron and chakra": soot #17191D, charcoal #292D33, ash #565B60.
All art is drawn by the code below (no franchise or NYDO assets). Standard library only.
Run from the repo root:  python3 tools/goregen/gen_gore.py [--scale 4]
"""
import argparse
import math
import os
import random
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "skingen"))
from pixlib import Canvas, SKIN_PARTS, ARMOR_PARTS, faces, hx, mix, noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "noirm", "textures", "entity", "gore")
PARTICLES = os.path.join(ROOT, "src", "main", "resources", "assets", "noirm", "textures", "particle")

S = 4  # output pixels per layout pixel (set by --scale)

# Outer layer regions of the skin, and the matching armor rects, by body part.
SKIN_REGIONS = {"head": "hat", "body": "jacket", "rarm": "rsleeve", "larm": "lsleeve", "rleg": "rpants", "lleg": "lpants"}
ARMOR_REGIONS = {"head": "head", "body": "body", "rarm": "arm", "larm": "arm", "rleg": "leg", "lleg": "leg"}
SIDES = ("front", "back", "right", "left")

# Where each kind of mark tends to land (part weights) and how many new marks each stage adds.
KINDS = {
    "bruise": {"weights": {"head": 3, "body": 4, "rarm": 3, "larm": 3, "rleg": 1.5, "lleg": 1.5}, "new": (14, 16, 18)},
    "dirt": {"weights": {"head": 1, "body": 2, "rarm": 1.5, "larm": 1.5, "rleg": 5, "lleg": 5}, "new": (22, 22, 24)},
    "soot": {"weights": {"head": 3, "body": 4, "rarm": 2.5, "larm": 2.5, "rleg": 2, "lleg": 2}, "new": (12, 14, 16)},
    "cut": {"weights": {"head": 1.5, "body": 3, "rarm": 3.5, "larm": 3.5, "rleg": 2, "lleg": 2}, "new": (9, 9, 11)},
    "blood": {"weights": {"head": 2, "body": 5, "rarm": 3, "larm": 3, "rleg": 2.5, "lleg": 2.5}, "new": (10, 12, 14)},
}
ARMOR_KINDS = ("dirt", "soot", "cut", "blood")
STAGES = 3


# --------------------------------------------------------------------------- helpers

def smooth(x, y, seed):
    """Value noise (0-1) that varies smoothly over layout pixels (pixlib's noise is a hash)."""
    x0, y0 = math.floor(x), math.floor(y)
    tx, ty = x - x0, y - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = noise(x0, y0, seed)
    b = noise(x0 + 1, y0, seed)
    c = noise(x0, y0 + 1, seed)
    d = noise(x0 + 1, y0 + 1, seed)
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


class Face:
    """One face of a part on a canvas: draws in face-local layout pixels, clipped to the face."""

    def __init__(self, cv, rect):
        self.cv = cv
        self.x, self.y, self.w, self.h = rect

    def put(self, px, py, c):
        """Blend c onto output pixel (px, py), given in face-local output pixels."""
        if 0 <= px < self.w * S and 0 <= py < self.h * S:
            c = tuple(max(0, min(255, int(v))) for v in c)
            self.cv.blend(self.x * S + px, self.y * S + py, c)

    def area(self, cx, cy, r):
        """Output pixels (px, py) and their layout-space centres (fx, fy) within r of (cx, cy)."""
        x0 = max(0, int(math.floor((cx - r) * S)))
        x1 = min(self.w * S - 1, int(math.ceil((cx + r) * S)))
        y0 = max(0, int(math.floor((cy - r) * S)))
        y1 = min(self.h * S - 1, int(math.ceil((cy + r) * S)))
        for py in range(y0, y1 + 1):
            for px in range(x0, x1 + 1):
                yield px, py, (px + 0.5) / S, (py + 0.5) / S


def pick(rng, weights):
    total = sum(weights.values())
    r = rng.random() * total
    for k, w in weights.items():
        r -= w
        if r <= 0:
            return k
    return list(weights)[-1]


def place(rng, kind):
    part = pick(rng, KINDS[kind]["weights"])
    face = rng.choice(SIDES if part != "head" else ("front", "front", "right", "left", "back"))
    fw, fh = faces(SKIN_PARTS[SKIN_REGIONS[part]])[face][2:]
    x = rng.random() * fw
    y = rng.random() * fh
    if kind == "dirt" and part in ("rleg", "lleg", "body"):
        y = max(y, rng.random() * fh)  # grime gathers low
    if kind == "blood":
        y = min(y, rng.random() * fh)  # blood starts higher and runs down
    return {"part": part, "face": face, "x": x, "y": y, "seed": rng.randrange(1 << 20),
            "angle": rng.random() * math.pi, "len": rng.uniform(3, 6.5), "drips": rng.randint(0, 3)}


# --------------------------------------------------------------------------- brushes
# Each brush draws mark m on face f; grow (0.2-1) is how far it has spread by this stage.

def bruise(f, m, grow):
    r = 1.9 + 1.4 * grow + (m["seed"] % 3) * 0.35
    core, mid, edge = hx("3E1E3E"), hx("553F72"), hx("72763A")
    for px, py, fx, fy in f.area(m["x"], m["y"], r + 1):
        d = math.hypot(fx - m["x"], (fy - m["y"]) * 1.1) + (smooth(fx * 1.3, fy * 1.3, m["seed"]) - 0.5) * 1.2
        if d > r:
            continue
        t = d / r
        c = mix(core, mid, min(1, t * 1.6)) if t < 0.65 else mix(mid, edge, (t - 0.65) / 0.35)
        a = int((240 - 80 * t) * (0.82 + 0.18 * grow))
        f.put(px, py, c + (a,))


def dirt(f, m, grow):
    r = 2.4 + 1.6 * grow
    tones = (hx("54402A"), hx("3A2C1E"), hx("6A563A"), hx("46382A"))
    for px, py, fx, fy in f.area(m["x"], m["y"], r):
        d = math.hypot((fx - m["x"]) * 0.8, fy - m["y"])
        if d > r:
            continue
        n = noise(px, py, m["seed"])  # grit at the output resolution
        blot = smooth(fx * 1.7, fy * 1.7, m["seed"] + 9)
        if n * 0.6 + blot * 0.6 < 0.3 + 0.35 * (d / r) - 0.1 * grow:
            continue
        c = tones[int(n * 97) % len(tones)]
        a = int(150 + 95 * blot * (0.8 + 0.2 * grow))
        f.put(px, py, c + (min(235, a),))


def soot(f, m, grow):
    r = 2.6 + 1.6 * grow
    dark, char, ash = hx("141518"), hx("26292E"), hx("565B60")
    for px, py, fx, fy in f.area(m["x"], m["y"], r + 1.5):
        d = math.hypot(fx - m["x"], (fy - m["y"]) * 0.9) + (smooth(fx * 1.2, fy * 1.2, m["seed"] + 3) - 0.5) * 2.0
        if d > r:
            continue
        t = d / r
        c = mix(dark, char, t) if noise(px, py, m["seed"]) > 0.15 else ash
        a = int((248 - 110 * t) * (0.82 + 0.18 * grow))
        f.put(px, py, c + (max(70, a),))


def segment_distance(fx, fy, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((fx - ax) * vx + (fy - ay) * vy) / max(1e-6, vx * vx + vy * vy)))
    return math.hypot(fx - (ax + vx * t), fy - (ay + vy * t)), t


def cut(f, m, grow):
    length = m["len"] * (0.8 + 0.3 * grow)
    ddx, ddy = math.cos(m["angle"]), math.sin(m["angle"])
    ax, ay = m["x"] - ddx * length / 2, m["y"] - ddy * length / 2
    bx, by = m["x"] + ddx * length / 2, m["y"] + ddy * length / 2
    core, dark, lip, seep = hx("5A070A"), hx("3A0406"), hx("B04044"), hx("8E1216")
    width = 0.32 + 0.16 * grow
    for px, py, fx, fy in f.area(m["x"], m["y"], length / 2 + 1.5):
        d, t = segment_distance(fx, fy, ax, ay, bx, by)
        taper = 1 - abs(t - 0.5) * 1.4  # thin at the ends
        w = width * max(0.25, taper)
        if d < w:
            f.put(px, py, (core if d < w * 0.6 else dark) + (245,))
        elif d < w + 0.35 and grow > 0.3:
            f.put(px, py, lip + (int(120 * (1 - (d - w) / 0.35)),))  # torn edge
        elif d < w + 0.9 * grow:
            f.put(px, py, seep + (int(70 * (1 - (d - w) / (0.9 * grow))),))  # blood seeping out
    if grow > 0.6:  # an old cut has bled a little
        drip(f, m["x"], m["y"] + 0.2, 1.5 + 2 * grow, 0.3, m["seed"] + 1, hx("7E0E12"))


def drip(f, x, y, length, width, seed, colour):
    """A run of blood down the face from (x, y): it wanders a little and ends in a bead."""
    for px, py, fx, fy in f.area(x, y + length / 2, length / 2 + width + 1.2):
        if fy < y:
            continue
        along = fy - y
        cx = x + (smooth(0.0, along * 0.6, seed) - 0.5) * 0.9
        thin = width * (1 - 0.35 * along / max(length, 0.1))
        if along <= length and abs(fx - cx) < thin:
            f.put(px, py, colour + (235,))
        elif abs(along - length) < width * 1.6 and math.hypot(fx - cx, (along - length) * 1.1) < width * 1.5:
            f.put(px, py, mix(colour, hx("4E0608"), 0.4) + (240,))  # the bead at the end


def blood(f, m, grow, side=True):
    """A splatter: an irregular blot, satellite droplets, and drips running down (side faces)."""
    r = 1.1 + 1.6 * grow + (m["seed"] % 4) * 0.2
    bright, deep = hx("B3171C"), hx("6E080C")
    for px, py, fx, fy in f.area(m["x"], m["y"], r + 1.5):
        dx, dy = fx - m["x"], fy - m["y"]
        ang = math.atan2(dy, dx)
        edge = r * (0.7 + 0.6 * smooth(ang * 2.2 + 10, m["seed"] % 97, m["seed"]))
        d = math.hypot(dx, dy)
        if d > edge:
            continue
        t = d / edge
        f.put(px, py, mix(deep, bright, min(1, t * 1.3)) + (int(250 - 40 * t),))
    rng = random.Random(m["seed"])
    for _ in range(int(3 + 5 * grow)):  # flung droplets around the blot
        a = rng.random() * math.tau
        dist = r * rng.uniform(1.1, 2.0)
        sx, sy = m["x"] + math.cos(a) * dist, m["y"] + math.sin(a) * dist
        sr = rng.uniform(0.18, 0.45)
        for px, py, fx, fy in f.area(sx, sy, sr):
            if math.hypot(fx - sx, fy - sy) < sr:
                f.put(px, py, bright + (240,))
    if side:
        for i in range(m["drips"] + (1 if grow > 0.7 else 0)):
            off = (rng.random() - 0.5) * r * 1.2
            drip(f, m["x"] + off, m["y"] + r * 0.5, rng.uniform(1.5, 3.0) + 4.5 * grow * rng.random(),
                 rng.uniform(0.22, 0.42), m["seed"] + 17 * i, mix(deep, bright, 0.55))


BRUSHES = {"bruise": bruise, "dirt": dirt, "soot": soot, "cut": cut, "blood": blood}


# --------------------------------------------------------------------------- textures

def stage_marks(kind):
    rng = random.Random({"bruise": 11, "dirt": 23, "soot": 37, "cut": 41, "blood": 53}[kind])
    marks = []  # (mark, stage it appears at)
    for stage in range(STAGES):
        for _ in range(KINDS[kind]["new"][stage]):
            marks.append((place(rng, kind), stage))
    return marks


def build(kind, armor):
    marks = stage_marks(kind)
    w, h = (64, 32) if armor else (64, 64)
    for stage in range(STAGES):
        cv = Canvas(w * S, h * S)
        for m, born in marks:
            if born > stage:
                continue
            if armor:
                rect = faces(ARMOR_PARTS[ARMOR_REGIONS[m["part"]]])[m["face"]]
            else:
                rect = faces(SKIN_PARTS[SKIN_REGIONS[m["part"]]])[m["face"]]
            grow = min(1.0, (stage - born) * 0.5 + 0.25)  # marks spread with each stage after they appear
            BRUSHES[kind](Face(cv, rect), m, grow)
        name = ("armor_%s_%d.png" if armor else "%s_%d.png") % (kind, stage + 1)
        cv.save(os.path.join(OUT, name))


def decals():
    """Ground blood: 4x2 cells of 16x16 layout pixels. 0-5 splats, 6-7 pools."""
    cv = Canvas(64 * S, 32 * S)
    bright, deep, sheen = hx("A8141A"), hx("62070B"), hx("D04A4E")
    for cell in range(8):
        f = Face(cv, ((cell % 4) * 16, (cell // 4) * 16, 16, 16))
        rng = random.Random(100 + cell)
        seed = rng.randrange(1 << 20)
        pool = cell >= 6
        # Keep a margin so nothing touches the cell edge (the quad is rotated freely).
        r = 5.2 if pool else rng.uniform(2.6, 4.0)
        stretch = 1.0 if pool else rng.uniform(1.0, 1.6)  # an elongated spray from a slanted hit
        for px, py, fx, fy in f.area(8, 8, 8):
            dx, dy = (fx - 8) / stretch, fy - 8
            ang = math.atan2(dy, dx)
            wobble = 0.65 + 0.7 * smooth(ang * (1.6 if pool else 2.6) + 20, cell * 3.1, seed)
            d = math.hypot(dx, dy)
            edge = r * wobble
            if d > edge:
                continue
            t = d / edge
            c = mix(deep, bright, min(1, t * 1.2)) if pool else mix(bright, deep, t * 0.6)
            a = 235 if pool else int(245 - 40 * t)
            if pool and t > 0.86:
                c = mix(c, deep, 0.6)  # a darker rim where it starts to dry
            if pool and 0.2 < t < 0.5 and smooth(fx * 1.5, fy * 1.5, seed + 4) > 0.78:
                c = mix(c, sheen, 0.5)  # a wet sheen
            f.put(px, py, c + (a,))
        for _ in range(rng.randint(5, 9) if not pool else rng.randint(3, 6)):
            a = rng.random() * math.tau
            dist = r * rng.uniform(1.1, 1.65)
            sx, sy = 8 + math.cos(a) * dist * stretch, 8 + math.sin(a) * dist
            sr = rng.uniform(0.3, 0.8)
            if math.hypot(sx - 8, sy - 8) + sr > 7.4:
                continue
            for px, py, fx, fy in f.area(sx, sy, sr):
                if math.hypot(fx - sx, fy - sy) < sr:
                    f.put(px, py, bright + (240,))
    cv.save(os.path.join(OUT, "blood_decals.png"))


def particles():
    """Falling blood droplets: 4x4 layout pixels each."""
    for i in range(3):
        cv = Canvas(4 * S, 4 * S)
        f = Face(cv, (0, 0, 4, 4))
        r = (1.0, 1.35, 1.7)[i]
        for px, py, fx, fy in f.area(2, 2, 2):
            d = math.hypot(fx - 2, (fy - 2) * (0.85 if i == 2 else 1.0))
            if d < r:
                f.put(px, py, mix(hx("C21E24"), hx("6A080C"), d / r) + (255,))
        cv.save(os.path.join(PARTICLES, "blood_drop_%d.png" % i))


def wound():
    cv = Canvas(16 * S, 16 * S)
    f = Face(cv, (0, 0, 16, 16))
    rim, flesh, deep, bone = hx("5A0A0C"), hx("9A2A2C"), hx("6E1416"), hx("D8CCB4")
    for px, py, fx, fy in f.area(8, 8, 8):
        d = math.hypot(fx - 8, fy - 8) / 8
        n = smooth(fx * 1.4, fy * 1.4, 5)
        if d > 0.85:
            c = mix(rim, deep, n * 0.5)
        elif d > 0.32:
            c = mix(flesh, deep, n * 0.7)
        else:
            c = bone if n > 0.3 else mix(bone, flesh, 0.5)
        f.put(px, py, c + (255,))
    cv.save(os.path.join(OUT, "wound.png"))


def main():
    global S
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--scale", type=int, default=4, help="output pixels per layout pixel (default 4)")
    S = parser.parse_args().scale
    for kind in KINDS:
        build(kind, False)
    for kind in ARMOR_KINDS:
        build(kind, True)
    decals()
    particles()
    wound()
    print("wrote gore textures (%dx) to %s" % (S, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()

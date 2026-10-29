#!/usr/bin/env python3
"""Gore textures for NOIRM (blueprint section 7), v2: damage marks, blood, ground decals, wounds.

Style: docs/ART_STYLE.md, "raw and tough". Ink first: hard edges, two or three flat tones, gritty
grain at the output resolution, flung specks and dry-brush streaks instead of soft gradients.
Blood is deep red with near-black clots (the client darkens it further as it dries).

Drawn at SCALE x the Minecraft layout (the owner's art direction: 4x, so 256x256 for the 64x64
skin layout and 256x128 for the 64x32 armor layout). Every brush works in layout pixels and is
sampled per output pixel, so the marks keep their shape at any SCALE (try --scale 1 or 8).

Body marks (player-skin layout, painted on the skin's OUTER layer regions: hat, jacket,
sleeves, pants), three nested stages per kind: stage 2 holds every mark of stage 1 (a little
bigger) plus new ones. client/GoreMarksLayer draws the stage reached and fades in the next.

    bruise  dark plum blotches with ragged olive edges (fists, blunt hits)
    dirt    gritty grime and dragged streaks, mostly low on the legs (falls, knockdowns, earth)
    soot    ink-black burns breaking into grain, ash flecks and scorch scratches (fire, blasts)
    cut     black gashes with a red lip that weep and drip (blades, kunai, wind)
    blood   torn splatters with clots, flung specks and drips running down

Clothing marks (armor layout, 64x32 at 1x): the same marks, minus bruises, on the head, body,
arm and leg rects, so worn gear gets dirty, burnt, torn and bloody too (client/GoreArmorLayer;
the client only draws them where the piece itself has cloth or metal).

Ground decals (blood_decals.png, 4x2 cells of 16x16 at 1x): six splat shapes and two pools.
Particles: blood_drop_0..2. Wound cap: wound.png.

All art is drawn by the code below (no franchise or NYDO assets, no reference images).
Standard library only. Run from the repo root:  python3 tools/goregen/gen_gore.py [--scale 4]
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

INK = hx("120A0B")         # near-black
BLOOD_DEEP = hx("7C070C")  # deep red
BLOOD_RED = hx("A50D14")   # the lit red of a wet splash
BLOOD_CLOT = hx("2E0306")  # red-black


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


def grain(px, py, seed):
    """Per output pixel: 0-1 grit that makes flat ink look printed and rough."""
    return noise(px, py, seed)


def ragged(fx, fy, cx, cy, r, seed, rough=0.55, freq=2.4):
    """Distance from (cx, cy) as a share of a torn edge of about r: below 1 is inside."""
    dx, dy = fx - cx, fy - cy
    ang = math.atan2(dy, dx)
    # sample the edge noise around a circle so it wraps without a seam
    edge = r * (1 - rough / 2 + rough * smooth(math.cos(ang) * freq + 10, math.sin(ang) * freq + 10, seed))
    return math.hypot(dx, dy) / max(edge, 1e-3)


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


def segment_distance(fx, fy, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((fx - ax) * vx + (fy - ay) * vy) / max(1e-6, vx * vx + vy * vy)))
    return math.hypot(fx - (ax + vx * t), fy - (ay + vy * t)), t


def specks(f, cx, cy, r, count, rng, colour, size=(0.12, 0.35), reach=(1.05, 2.1)):
    """Flung droplets around a blot (some stretched away from it, like a splash)."""
    for _ in range(count):
        a = rng.random() * math.tau
        dist = r * rng.uniform(*reach)
        sx, sy = cx + math.cos(a) * dist, cy + math.sin(a) * dist
        sr = rng.uniform(*size)
        tail = rng.random() < 0.35
        for px, py, fx, fy in f.area(sx, sy, sr * (2.6 if tail else 1) + 0.2):
            dx, dy = fx - sx, fy - sy
            if tail:
                along = dx * math.cos(a) + dy * math.sin(a)
                across = -dx * math.sin(a) + dy * math.cos(a)
                inside = (along / (sr * 2.4)) ** 2 + (across / (sr * 0.7)) ** 2 < 1
            else:
                inside = math.hypot(dx, dy) < sr
            if inside:
                f.put(px, py, colour + (255,))


# --------------------------------------------------------------------------- brushes
# Each brush draws mark m on face f; grow (0.25-1) is how far it has spread by this stage.

def bruise(f, m, grow):
    r = 1.9 + 1.5 * grow + (m["seed"] % 3) * 0.35
    core, mid, edge = hx("1E0E20"), hx("40284E"), hx("4E5028")
    for px, py, fx, fy in f.area(m["x"], m["y"], r * 1.4):
        t = ragged(fx, fy, m["x"], m["y"], r, m["seed"], 0.7, 1.8)
        if t > 1:
            continue
        g = grain(px, py, m["seed"])
        if t > 0.75 and g < 0.45:
            continue  # a rough, broken edge
        c = core if t < 0.45 else mid if t < 0.78 else edge
        f.put(px, py, c + (int(220 + 30 * grow) if g > 0.12 else 170,))


def dirt(f, m, grow):
    r = 2.4 + 1.7 * grow
    tones = (hx("3A2A1A"), hx("21180F"), hx("4E3C26"))
    for px, py, fx, fy in f.area(m["x"], m["y"], r * 1.3):
        t = ragged(fx, fy, m["x"], m["y"], r, m["seed"], 0.8, 2.0)
        if t > 1:
            continue
        g = grain(px, py, m["seed"])
        if g < 0.25 + 0.5 * t - 0.15 * grow:
            continue  # grit, denser in the middle
        f.put(px, py, tones[int(g * 7) % 3] + (230,))
    rng = random.Random(m["seed"])  # dry-brush streaks: dirt dragged down the cloth
    for _ in range(int(1 + 2 * grow)):
        sx = m["x"] + rng.uniform(-r, r) * 0.7
        length = rng.uniform(1.0, 2.5) * (1 + grow)
        for px, py, fx, fy in f.area(sx, m["y"] + length / 2, length / 2 + 0.3):
            if abs(fx - sx) < 0.18 and m["y"] <= fy <= m["y"] + length and grain(px, py, m["seed"] + 5) > 0.35:
                f.put(px, py, tones[1] + (220,))


def soot(f, m, grow):
    r = 2.6 + 1.7 * grow
    for px, py, fx, fy in f.area(m["x"], m["y"], r * 1.5):
        t = ragged(fx, fy, m["x"], m["y"], r, m["seed"] + 3, 0.9, 2.2)
        if t > 1.15:
            continue
        g = grain(px, py, m["seed"])
        if t > 0.7 and g < (t - 0.7) * 2.2:
            continue  # burnt edges break up into grain
        c = INK if t < 0.6 or g > 0.85 else hx("2A2C30")
        if g < 0.04:
            c = hx("6A6E72")  # flecks of ash
        f.put(px, py, c + (250 if t < 0.6 else 215,))
    rng = random.Random(m["seed"] + 1)  # scorch scratches
    for _ in range(int(2 + 3 * grow)):
        a = rng.uniform(-0.6, 0.6) - math.pi / 2
        length = rng.uniform(1.0, 2.8)
        ax, ay = m["x"] + rng.uniform(-r, r) * 0.6, m["y"] + rng.uniform(-r, r) * 0.6
        bx, by = ax + math.cos(a) * length, ay + math.sin(a) * length
        for px, py, fx, fy in f.area((ax + bx) / 2, (ay + by) / 2, length / 2 + 0.3):
            if segment_distance(fx, fy, ax, ay, bx, by)[0] < 0.12:
                f.put(px, py, INK + (255,))


def drip(f, x, y, length, width, seed):
    """A run of blood down the face from (x, y): wanders a little, thins, ends in a dark bead."""
    for px, py, fx, fy in f.area(x, y + length / 2, length / 2 + width + 1.2):
        if fy < y:
            continue
        along = fy - y
        cx = x + (smooth(0.0, along * 0.6, seed) - 0.5) * 0.8
        thin = width * (1 - 0.45 * along / max(length, 0.1))
        if along <= length and abs(fx - cx) < thin:
            f.put(px, py, (BLOOD_DEEP if abs(fx - cx) < thin * 0.55 else BLOOD_CLOT) + (255,))
        elif math.hypot(fx - cx, (along - length) * 1.1) < width * 1.35:
            f.put(px, py, BLOOD_CLOT + (255,))  # the bead at the end


def cut(f, m, grow):
    """An ink slash: a black gash with a red-black lip, blood smeared along it, weeping down."""
    length = m["len"] * (0.8 + 0.3 * grow)
    ddx, ddy = math.cos(m["angle"]), math.sin(m["angle"])
    ax, ay = m["x"] - ddx * length / 2, m["y"] - ddy * length / 2
    bx, by = m["x"] + ddx * length / 2, m["y"] + ddy * length / 2
    width = 0.3 + 0.18 * grow
    for px, py, fx, fy in f.area(m["x"], m["y"], length / 2 + 1.2):
        d, t = segment_distance(fx, fy, ax, ay, bx, by)
        w = width * max(0.15, 1 - abs(t - 0.5) * 1.6)  # knife-thin at the ends
        if d < w * 0.55:
            f.put(px, py, INK + (255,))
        elif d < w:
            f.put(px, py, BLOOD_CLOT + (255,))
        elif d < w + 0.3 + 0.4 * grow and grain(px, py, m["seed"]) > 0.45:
            f.put(px, py, BLOOD_DEEP + (235,))
    if grow > 0.4:
        drip(f, m["x"], m["y"], 1.5 + 3 * grow, 0.26, m["seed"] + 1)


def blood(f, m, grow):
    """A splatter: a torn blot (deep red, near-black clots), flung specks, drips running down."""
    r = 1.1 + 1.7 * grow + (m["seed"] % 4) * 0.2
    for px, py, fx, fy in f.area(m["x"], m["y"], r * 1.4):
        t = ragged(fx, fy, m["x"], m["y"], r, m["seed"], 0.75, 2.8)
        if t > 1:
            continue
        g = grain(px, py, m["seed"])
        clot = smooth(fx * 1.6, fy * 1.6, m["seed"] + 7) > 0.68
        c = BLOOD_CLOT if clot or t > 0.86 else (BLOOD_RED if g > 0.93 else BLOOD_DEEP)
        f.put(px, py, c + (255,))
    rng = random.Random(m["seed"])
    specks(f, m["x"], m["y"], r, int(5 + 8 * grow), rng, BLOOD_DEEP)
    for i in range(m["drips"] + (1 if grow > 0.7 else 0)):
        off = (rng.random() - 0.5) * r * 1.2
        drip(f, m["x"] + off, m["y"] + r * 0.4, rng.uniform(1.5, 3.0) + 5 * grow * rng.random(),
             rng.uniform(0.2, 0.4), m["seed"] + 17 * i)


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
    """Ground blood: 4x2 cells of 16x16 layout pixels. 0-5 splats, 6-7 pools. The quad turns
    freely, so everything stays inside the circle the cell's corners never clip."""
    cv = Canvas(64 * S, 32 * S)
    for cell in range(8):
        f = Face(cv, ((cell % 4) * 16, (cell // 4) * 16, 16, 16))
        rng = random.Random(100 + cell)
        seed = rng.randrange(1 << 20)
        pool = cell >= 6
        r = 5.0 if pool else rng.uniform(2.3, 3.6)
        stretch = 1.0 if pool else rng.uniform(1.0, 1.7)  # an elongated splash from a slanted hit
        for px, py, fx, fy in f.area(8, 8, 8):
            t = ragged(8 + (fx - 8) / stretch, fy, 8, 8, r, seed, 0.6 if pool else 0.8, 1.7 if pool else 3.0)
            if t > 1:
                continue
            g = grain(px, py, seed)
            clot = smooth(fx * 1.1, fy * 1.1, seed + 7) > (0.62 if pool else 0.7)
            if pool:
                c = BLOOD_CLOT if t > 0.82 or clot else BLOOD_DEEP
                if 0.25 < t < 0.55 and smooth(fx * 1.4, fy * 1.4, seed + 4) > 0.8 and g > 0.3:
                    c = BLOOD_RED  # a wet glint
            else:
                c = BLOOD_CLOT if clot or t > 0.85 else (BLOOD_RED if g > 0.9 else BLOOD_DEEP)
            f.put(px, py, c + (255,))
        for _ in range(rng.randint(10, 18) if not pool else rng.randint(5, 9)):
            a = rng.random() * math.tau
            dist = r * rng.uniform(1.1, 1.75)
            sx, sy = 8 + math.cos(a) * dist * stretch, 8 + math.sin(a) * dist
            sr = rng.uniform(0.15, 0.6)
            if math.hypot(sx - 8, sy - 8) + sr > 7.5:
                continue
            for px, py, fx, fy in f.area(sx, sy, sr):
                if math.hypot(fx - sx, fy - sy) < sr:
                    f.put(px, py, (BLOOD_DEEP if sr > 0.3 else BLOOD_CLOT) + (255,))
    cv.save(os.path.join(OUT, "blood_decals.png"))


def particles():
    """Falling blood droplets: 4x4 layout pixels each, a deep red bead with a dark rim."""
    for i in range(3):
        cv = Canvas(4 * S, 4 * S)
        f = Face(cv, (0, 0, 4, 4))
        r = (1.0, 1.35, 1.7)[i]
        for px, py, fx, fy in f.area(2, 2, 2):
            d = math.hypot(fx - 2, (fy - 2) * (0.8 if i == 2 else 1.0))
            if d < r:
                f.put(px, py, (BLOOD_RED if d < r * 0.35 else BLOOD_DEEP if d < r * 0.75 else BLOOD_CLOT) + (255,))
        cv.save(os.path.join(PARTICLES, "blood_drop_%d.png" % i))


def wound():
    """The raw cut at a stump: a red-black rim, torn flesh, a bone-white core."""
    cv = Canvas(16 * S, 16 * S)
    f = Face(cv, (0, 0, 16, 16))
    flesh, bone = hx("8A1A1E"), hx("D8CCB4")
    for px, py, fx, fy in f.area(8, 8, 8):
        t = ragged(fx, fy, 8, 8, 8.5, 5, 0.25, 2.0)
        n = smooth(fx * 1.4, fy * 1.4, 5)
        g = grain(px, py, 5)
        if t > 0.8:
            c = BLOOD_CLOT if g > 0.2 else INK
        elif t > 0.3:
            c = BLOOD_DEEP if n < 0.55 else (flesh if g > 0.3 else BLOOD_CLOT)
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

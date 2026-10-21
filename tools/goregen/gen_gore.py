#!/usr/bin/env python3
"""Gore textures for NOIRM (blueprint section 7): damage marks and the wound cap.

Marks are 64x64 overlays in the player-skin layout, painted on the skin's OUTER layer regions
(hat, jacket, sleeves, pants), so they show over clothes as well as skin. Each kind has three
nested stages: stage 2 holds every mark of stage 1 (a little bigger and darker) plus new ones, and
so on. The client (client/GoreMarksLayer) draws the stage a fighter has reached and fades in the
next one, so marks spread and deepen as damage builds.

    bruise  plum and blue blotches with yellow-green edges (fists, blunt hits)
    dirt    brown speckled grime, mostly low on the legs (falls, knockdowns, earth)
    soot    soft charcoal smudges (fire, blasts, lightning)
    cut     thin dark-red slashes (blades, kunai, wind)
    wound   16x16 raw cut for severed limbs and stumps

Palette from the blueprint's "ash, iron and chakra": soot #17191D, charcoal #292D33, ash #565B60.
All art is drawn by the code below (no franchise or NYDO assets). Standard library only.
Run from the repo root:  python3 tools/goregen/gen_gore.py
Writes src/main/resources/assets/noirm/textures/entity/gore/<kind>_<1-3>.png and wound.png.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "skingen"))
from pixlib import Canvas, SKIN_PARTS, faces, hx, mix, noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "noirm", "textures", "entity", "gore")

# Outer layer regions, by body part.
REGIONS = {"head": "hat", "body": "jacket", "rarm": "rsleeve", "larm": "lsleeve", "rleg": "rpants", "lleg": "lpants"}
SIDES = ("front", "back", "right", "left")

# Where each kind of mark tends to land (part weights) and how many new marks each stage adds.
KINDS = {
    "bruise": {"weights": {"head": 3, "body": 4, "rarm": 3, "larm": 3, "rleg": 1.5, "lleg": 1.5}, "new": (6, 8, 10)},
    "dirt": {"weights": {"head": 1, "body": 2, "rarm": 1.5, "larm": 1.5, "rleg": 5, "lleg": 5}, "new": (10, 12, 14)},
    "soot": {"weights": {"head": 3, "body": 4, "rarm": 2.5, "larm": 2.5, "rleg": 2, "lleg": 2}, "new": (5, 7, 9)},
    "cut": {"weights": {"head": 1.5, "body": 3, "rarm": 3.5, "larm": 3.5, "rleg": 2, "lleg": 2}, "new": (4, 5, 7)},
}
STAGES = 3


def face_rect(part, face):
    """(x, y, w, h) of one face of a part's outer layer in the 64x64 skin."""
    return faces(SKIN_PARTS[REGIONS[part]])[face]


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
    fx, fy, fw, fh = face_rect(part, face)
    x = rng.randrange(fw)
    y = rng.randrange(fh)
    if kind == "dirt" and part in ("rleg", "lleg", "body"):
        y = max(y, rng.randrange(fh))  # grime gathers low
    return {"part": part, "face": face, "rect": (fx, fy, fw, fh), "x": x, "y": y,
            "seed": rng.randrange(1 << 20), "angle": rng.random() * math.pi, "len": rng.randint(3, 6)}


def put(cv, rect, x, y, c):
    """Blend c onto face-local (x, y), clipped to the face."""
    fx, fy, fw, fh = rect
    if 0 <= x < fw and 0 <= y < fh:
        cv.blend(fx + x, fy + y, c)


# --------------------------------------------------------------------------- brushes

def bruise(cv, m, grow):
    r = 1.8 + 1.2 * grow + (m["seed"] % 3) * 0.4
    core, mid, edge = hx("4A2648"), hx("5E4A7A"), hx("7A7A3E")
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            d = math.hypot(dx, dy * 1.1) + (noise(dx, dy, m["seed"]) - 0.5) * 0.6
            if d > r:
                continue
            t = d / max(r, 0.01)
            c = mix(core, mid, min(1, t * 1.6)) if t < 0.65 else mix(mid, edge, (t - 0.65) / 0.35)
            a = int((175 - 80 * t) * (0.75 + 0.25 * grow))
            put(cv, m["rect"], m["x"] + dx, m["y"] + dy, c + (a,))


def dirt(cv, m, grow):
    r = 2.2 + 1.4 * grow
    tones = (hx("5A4630"), hx("3E3022"), hx("6E5A3E"), hx("4A3C2A"))
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            d = math.hypot(dx * 0.8, dy)
            if d > r:
                continue
            n = noise(m["x"] + dx * 7, m["y"] + dy * 5, m["seed"])
            if n < 0.22 + 0.25 * (d / r) - 0.08 * grow:
                continue
            c = tones[int(n * 97) % len(tones)]
            a = int(110 + 90 * n * (0.7 + 0.3 * grow))
            put(cv, m["rect"], m["x"] + dx, m["y"] + dy, c + (min(220, a),))


def soot(cv, m, grow):
    r = 2.4 + 1.4 * grow
    dark, char, ash = hx("17191D"), hx("292D33"), hx("565B60")
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            d = math.hypot(dx, dy * 0.9) + (noise(dx, dy, m["seed"] + 3) - 0.5) * 1.4
            if d > r:
                continue
            t = d / r
            n = noise(m["x"] + dx, m["y"] + dy, m["seed"])
            c = mix(dark, char, t) if n > 0.2 else ash
            a = int((215 - 130 * t) * (0.7 + 0.3 * grow))
            put(cv, m["rect"], m["x"] + dx, m["y"] + dy, c + (max(60, a),))


def cut(cv, m, grow):
    length = m["len"] + (1 if grow > 0.9 else 0)
    ang = m["angle"]
    ddx, ddy = math.cos(ang), math.sin(ang)
    core, dark, lip = hx("7E1014"), hx("4A080A"), hx("A8383A")
    for i in range(length):
        t = i - (length - 1) / 2
        x = int(round(m["x"] + ddx * t))
        y = int(round(m["y"] + ddy * t))
        end = i in (0, length - 1)
        put(cv, m["rect"], x, y, (dark if end else core) + (235,))
        if not end and grow > 0.4 and noise(x, y, m["seed"]) > 0.55:
            # a pale lip of torn skin beside the slash
            put(cv, m["rect"], x + int(round(-ddy)), y + int(round(ddx)), lip + (150,))


BRUSHES = {"bruise": bruise, "dirt": dirt, "soot": soot, "cut": cut}


def build(kind):
    rng = random.Random({"bruise": 11, "dirt": 23, "soot": 37, "cut": 41}[kind])
    marks = []  # (mark, stage it appears at)
    for stage in range(STAGES):
        for _ in range(KINDS[kind]["new"][stage]):
            marks.append((place(rng, kind), stage))
    for stage in range(STAGES):
        cv = Canvas(64, 64)
        for m, born in marks:
            if born <= stage:
                # marks grow a little with each stage after they appear
                grow = min(1.0, (stage - born) * 0.5 + 0.2)
                BRUSHES[kind](cv, m, grow)
        cv.save(os.path.join(OUT, "%s_%d.png" % (kind, stage + 1)))


def wound():
    cv = Canvas(16, 16)
    rim, flesh, deep, bone = hx("5A0A0C"), hx("9A2A2C"), hx("6E1416"), hx("D8CCB4")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            n = noise(x, y, 5)
            if d > 0.85:
                c = mix(rim, deep, n * 0.5)
            elif d > 0.3:
                c = mix(flesh, deep, n * 0.6)
            else:
                c = bone if n > 0.25 else mix(bone, flesh, 0.5)
            cv.set(x, y, c + (255,))
    cv.save(os.path.join(OUT, "wound.png"))


def main():
    for kind in KINDS:
        build(kind)
    wound()
    print("wrote gore textures to", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()

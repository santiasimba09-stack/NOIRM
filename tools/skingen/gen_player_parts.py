#!/usr/bin/env python3
"""Player appearance parts for NOIRM's character creation screen.

The creator (client/CreatorScreen) builds a player's skin at runtime from the part ids saved in
their look (creator/PlayerLook). client/PlayerSkins stacks the layers, from bottom to top:

    skin_base -> jaw -> nose -> mouth -> muscle -> weathering -> scar -> marking -> underwear
    -> pants -> shirt -> wraps -> sandals -> eyes -> iris -> brows -> hair -> hair length

The face, hair, clothes and marking parts NPC ninjas use come from tools/skingen/gen_parts.py
(textures/entity/ninja/parts/). This script draws the ones only players pick:

    skin_base           an athletic base body with no nose or mouth (those are parts now)
    nose_*, jaw_*, mouth_*   face variants, tinted by skin tone
    muscle_*            muscle definition, tinted by skin tone
    weather_*           grime and scuffs (fixed colours, see-through)
    scar_*              body and face scars (fixed colours)
    under_*             underwear sets, tinted by the underwear colour
    hairlen_*           hair hanging down the back, tinted by hair colour

Gray parts are tint layers (gray 128 = the colour itself; see pixlib.gray / tint).
All art is original and drawn by the code below. Standard library only.
Run from the repo root:  python3 tools/skingen/gen_player_parts.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixlib import Canvas, SKIN_PARTS, hx, noise, paint, paint_k, mirror_side  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/player_parts")

P = SKIN_PARTS
ARMS = ("rarm", "larm")
LEGS = ("rleg", "lleg")


def new():
    return Canvas(64, 64)


def face_k(fn):
    """A tint layer drawn only on the front of the head."""
    cv = new()
    paint_k(cv, P["head"], lambda f, x, y, W, H: fn(x, y) if f == "front" else None)
    return cv


# ---------------------------------------------------------------------------
# The base body: athletic, no nose or mouth (tinted by skin tone)
# ---------------------------------------------------------------------------

def part_skin_base():
    cv = new()

    def head(face, x, y, W, H):
        if face == "front":
            k = 0.0
            if x in (0, 7):
                k -= 0.25
            if y == 3 and x in (1, 2, 5, 6):
                k -= 0.15  # brow ridge
            if y == 5 and x in (1, 6):
                k += 0.12  # cheekbones
            return k
        if face in ("right", "left"):
            mx = mirror_side(face, x, W)
            if mx in (3, 4) and y in (3, 4, 5):
                return -1.0 if (mx == 3 and y == 4) else -0.35  # ear
            return -0.45 if y == 7 else 0.0
        if face == "bottom":
            return -0.9
        if face == "back":
            return -0.3
        return 0.2

    def body(face, x, y, W, H):
        k = 0.0
        if face == "front":
            if y in (2, 3) and x in (1, 2, 5, 6):
                k += 0.12
            if y == 4 and x in (1, 2, 5, 6):
                k -= 0.2  # chest line
            if x in (3, 4) and 5 <= y <= 9:
                k -= 0.08 if (y % 2) else 0.0
            if y == 8 and x == 4:
                k -= 0.5
        if face == "back":
            if x in (3, 4):
                k -= 0.12
            if y in (2, 3) and x in (1, 6):
                k += 0.1  # shoulder blades
        if face == "bottom":
            k -= 0.6
        return k

    def arm(face, x, y, W, H):
        k = 0.0
        if y == 5:
            k -= 0.12  # elbow
        if y >= 10:
            k += 0.05
            if face == "front" and y == 11 and x in (1, 2):
                k -= 0.35  # fingers
        if face == "bottom":
            k -= 0.5
        return k

    def leg(face, x, y, W, H):
        k = 0.0
        if y == 4 and face == "front":
            k += 0.12  # knee
        if face == "front" and y == 11 and x in (1, 2):
            k -= 0.4  # toes
        if face == "bottom":
            k -= 0.8
        return k

    paint_k(cv, P["head"], head)
    paint_k(cv, P["body"], body)
    for a in ARMS:
        paint_k(cv, P[a], arm)
    for l in LEGS:
        paint_k(cv, P[l], leg)
    return cv


# ---------------------------------------------------------------------------
# Face: nose (row 5, columns 3-4), mouth (row 6), jaw (rows 6-7 and the head's sides)
# ---------------------------------------------------------------------------

NOSES = {
    "button": {(4, 5): -0.45, (3, 5): 0.2},
    "straight": {(4, 4): -0.18, (4, 5): -0.6, (3, 5): 0.15},
    "broad": {(3, 5): -0.4, (4, 5): -0.45, (2, 5): -0.12, (5, 5): -0.15},
    "hooked": {(4, 4): 0.25, (4, 5): -0.75, (3, 5): -0.3},
    "flat": {(3, 5): -0.22, (4, 5): -0.28},
}

MOUTHS = {
    "neutral": {(3, 6): -0.95, (4, 6): -0.95, (3, 7): 0.1, (4, 7): 0.1},
    "smile": {(3, 6): -0.9, (4, 6): -0.9, (2, 5): -0.4, (5, 5): -0.4, (3, 7): 0.15, (4, 7): 0.15},
    "frown": {(3, 6): -0.9, (4, 6): -0.9, (2, 7): -0.45, (5, 7): -0.45},
    "wide": {(2, 6): -0.75, (3, 6): -0.95, (4, 6): -0.95, (5, 6): -0.75},
    "thin": {(3, 6): -0.5, (4, 6): -0.5},
}

JAWS = {
    # front pixels, plus how dark the lowest row of the head's sides is
    "soft": ({(0, 7): -0.3, (7, 7): -0.3}, -0.4),
    "square": ({(0, 6): -0.15, (7, 6): -0.15, (0, 7): -0.5, (7, 7): -0.5,
                (1, 7): -0.15, (2, 7): -0.15, (5, 7): -0.15, (6, 7): -0.15}, -0.6),
    "sharp": ({(0, 6): -0.45, (7, 6): -0.45, (0, 7): -0.85, (7, 7): -0.85, (1, 7): -0.45, (6, 7): -0.45,
               (3, 7): 0.1, (4, 7): 0.1}, -0.75),
    "round": ({(0, 7): -0.2, (7, 7): -0.2, (1, 7): -0.1, (6, 7): -0.1}, -0.3),
}


def part_face(table):
    return face_k(lambda x, y: table.get((x, y)))


def part_jaw(name):
    front, side = JAWS[name]
    cv = new()

    def fn(face, x, y, W, H):
        if face == "front":
            return front.get((x, y))
        if face in ("right", "left") and y == 7:
            return side
        return None
    paint_k(cv, P["head"], fn)
    return cv


# ---------------------------------------------------------------------------
# Muscle definition (tinted by skin tone, drawn over the base body)
# ---------------------------------------------------------------------------

MUSCLE = {"lean": 0.6, "defined": 1.0, "heavy": 1.45}


def part_muscle(name):
    s = MUSCLE[name]
    cv = new()

    def body(face, x, y, W, H):
        if face == "front":
            if y in (1, 2, 3) and x in (1, 2, 5, 6):
                return 0.2 * s if y < 3 else 0.1 * s  # pecs
            if y == 4 and x in (1, 2, 5, 6):
                return -0.4 * s  # under the chest
            if 5 <= y <= 9 and x in (3, 4):
                return -0.35 * s if y % 2 else 0.1 * s  # abs
            if 5 <= y <= 9 and x in (2, 5):
                return 0.12 * s if y % 2 == 0 else -0.1 * s
            if 6 <= y <= 9 and x in (0, 7):
                return -0.25 * s  # obliques
        if face == "back":
            if y in (0, 1) and x in (1, 2, 5, 6):
                return 0.2 * s  # traps
            if 3 <= y <= 7 and x in (3, 4):
                return -0.3 * s  # spine
            if 3 <= y <= 6 and x in (1, 6):
                return 0.15 * s  # lats
        if face == "top" and name == "heavy":
            return 0.25
        return None

    def arm(face, x, y, W, H):
        if face == "top":
            return 0.3 * s  # shoulders
        if face == "front" and y in (2, 3):
            return 0.25 * s  # biceps
        if face == "front" and y == 4:
            return -0.2 * s
        if face in ("right", "left") and y in (0, 1):
            return 0.18 * s  # deltoids
        if face == "back" and y in (2, 3, 4):
            return -0.12 * s if y == 4 else 0.1 * s  # triceps
        if face == "front" and y in (7, 8) and x in (1, 2) and name != "lean":
            return 0.12 * s  # forearms
        return None

    def leg(face, x, y, W, H):
        if face == "front" and y in (1, 2) and x in (1, 2):
            return 0.2 * s  # quads
        if face == "front" and y == 3:
            return -0.15 * s
        if face == "back" and y in (6, 7) and x in (1, 2):
            return 0.2 * s  # calves
        return None

    paint_k(cv, P["body"], body, light=False)
    for a in ARMS:
        paint_k(cv, P[a], arm, light=False)
    for l in LEGS:
        paint_k(cv, P[l], leg, light=False)
    return cv


# ---------------------------------------------------------------------------
# Weathering: grime, scuffs and small cuts (fixed colours, see-through)
# ---------------------------------------------------------------------------

GRIME = hx("#3b2f25")
DUST = hx("#6b6155")
CUT = hx("#7e3530")
WEATHER = {"light": (0.07, 0.0), "worn": (0.13, 0.02), "battered": (0.2, 0.05)}


def part_weather(name):
    density, cuts = WEATHER[name]
    cv = new()

    def make(salt):
        def fn(face, x, y, W, H):
            if face in ("top", "bottom"):
                return None
            n = noise(x, y, salt)
            if n < cuts:
                return CUT + (200,)
            if n < cuts + density:
                return (GRIME if noise(y, x, salt + 1) < 0.6 else DUST) + (70 + int(80 * noise(x, y, salt + 2)),)
            return None
        return fn

    for i, part in enumerate(("head", "body", "rarm", "larm", "rleg", "lleg")):
        fn = make(101 + i * 17)
        if part == "head":
            # keep the eyes clean
            inner = fn
            fn = (lambda inner_fn: lambda f, x, y, W, H: None if f == "front" and y == 4 else inner_fn(f, x, y, W, H))(inner)
        paint(cv, P[part], fn, light=False)
    return cv


# ---------------------------------------------------------------------------
# Scars (fixed colours). The eye and nose scars are NPC parts (ninja/parts/mark_scar_*).
# ---------------------------------------------------------------------------

SCAR = hx("#c48a7c")
SCAR_DARK = hx("#94564b")


def part_scar(name):
    cv = new()
    if name == "cheek":
        pts = {(6, 5): SCAR_DARK, (7, 6): SCAR, (5, 6): SCAR}
        paint(cv, P["head"], lambda f, x, y, W, H: (pts.get((x, y)) + (220,)) if f == "front" and pts.get((x, y)) else None,
              light=False)
    elif name == "chest":
        def fn(face, x, y, W, H):
            if face == "front" and 1 <= y <= 8 and x == 7 - y + 1 - (1 if y > 4 else 0):
                return (SCAR if y % 2 else SCAR_DARK) + (230,)
            return None
        paint(cv, P["body"], fn, light=False)
    elif name == "arm":
        def fn(face, x, y, W, H):
            if face == "front" and (x, y) in ((0, 3), (1, 4), (2, 4), (3, 5)):
                return SCAR + (230,)
            if face == "right" and y == 3 and x == 3:
                return SCAR_DARK + (200,)
            return None
        paint(cv, P["rarm"], fn, light=False)
    elif name == "back":
        def fn(face, x, y, W, H):
            if face == "back" and 2 <= y <= 9 and (x == y - 2 or x == 9 - y):
                return (SCAR if (x + y) % 2 else SCAR_DARK) + (230,)
            return None
        paint(cv, P["body"], fn, light=False)
    return cv


# ---------------------------------------------------------------------------
# Underwear (tinted by the underwear colour)
# ---------------------------------------------------------------------------

def part_under(name):
    cv = new()
    leg_rows = {"fundoshi": 2, "briefs": 2, "shorts": 5, "bindings": 4}[name]

    def waist(face, x, y, W, H):
        if face in ("top", "bottom"):
            return None
        if y == 10:
            return -0.5 if name == "fundoshi" else 0.25  # band
        if y == 11:
            if name == "fundoshi" and face in ("right", "left"):
                return None
            return 0.0 if not (face == "front" and x in (3, 4)) else -0.3
        if name == "bindings" and 2 <= y <= 5 and face != "top":
            return 0.3 if (x + y) % 3 == 0 else (-0.1 if y % 2 else 0.1)  # chest wraps
        return None

    def leg(face, x, y, W, H):
        if face in ("top", "bottom") or y >= leg_rows:
            return None
        if name == "fundoshi":
            return (-0.1 if y == 0 else 0.1) if face in ("front", "back") else None
        k = 0.0
        if y == leg_rows - 1:
            k += 0.25  # hem
        if noise(x, y, 77) < 0.1:
            k -= 0.2
        return k

    paint_k(cv, P["body"], waist)
    for l in LEGS:
        paint_k(cv, P[l], leg)
    return cv


# ---------------------------------------------------------------------------
# Hair length: hair hanging down the back (tinted by hair colour)
# ---------------------------------------------------------------------------

def part_hairlen(name):
    rows = {"nape": 2, "shoulder": 4, "waist": 10}[name]
    cv = new()

    def hat(face, x, y, W, H):
        if face == "back" and y >= 4:
            return 0.0 if noise(x, y, 5) > 0.2 else -0.8
        if face in ("right", "left") and y >= 5 and mirror_side(face, x, W) <= 2:
            return -0.3
        return None

    def jacket(face, x, y, W, H):
        if face == "back" and y < rows:
            if y == rows - 1 and x in (0, 7):
                return None  # tapered ends
            k = 0.0 if (x % 3) else -0.8
            if noise(x, y, 31) > 0.85:
                k += 0.9
            return k
        if face in ("right", "left") and y < min(rows, 3) and mirror_side(face, x, W) == 0:
            return -0.6
        return None

    paint_k(cv, P["hat"], hat)
    paint_k(cv, P["jacket"], jacket)
    return cv


def main():
    parts = {"skin_base": part_skin_base()}
    for name, table in NOSES.items():
        parts["nose_" + name] = part_face(table)
    for name, table in MOUTHS.items():
        parts["mouth_" + name] = part_face(table)
    for name in JAWS:
        parts["jaw_" + name] = part_jaw(name)
    for name in MUSCLE:
        parts["muscle_" + name] = part_muscle(name)
    for name in WEATHER:
        parts["weather_" + name] = part_weather(name)
    for name in ("cheek", "chest", "arm", "back"):
        parts["scar_" + name] = part_scar(name)
    for name in ("fundoshi", "briefs", "shorts", "bindings"):
        parts["under_" + name] = part_under(name)
    for name in ("nape", "shoulder", "waist"):
        parts["hairlen_" + name] = part_hairlen(name)
    for name, cv in parts.items():
        cv.save(os.path.join(OUT_DIR, name + ".png"))
    print("wrote %d parts to %s" % (len(parts), os.path.relpath(OUT_DIR, ROOT)))


if __name__ == "__main__":
    main()

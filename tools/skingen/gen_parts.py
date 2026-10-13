#!/usr/bin/env python3
"""Ninja appearance parts for NOIRM's runtime skin composer.

NPC ninjas no longer have fixed outfit skins. Each one carries an appearance key
("<clan folder>:<hex seed>"); the client (client/NinjaSkins) decodes it with the tables in
looks.json and stacks these 64x64 part layers into one player skin:

    skin -> mark -> shirt -> pants -> wraps -> sandals -> eyes -> iris -> brows -> hair -> accessory

Parts drawn in GRAY are tinted at runtime (skin tone, hair, eye, shirt, pants, mask colours;
see pixlib.gray/tint). Parts drawn in colour (marks, sandals, eye whites, glasses...) are used as is.
The bases are deliberately clean (skin, a dark undershirt and pants) so the real armour items
(tools/gear/gen_gear.py) look good on top.

Writes:
    src/main/resources/assets/noirm/textures/entity/ninja/parts/<part>.png
    src/main/resources/assets/noirm/textures/entity/ninja/parts/looks.json

All art is original and drawn by the code below. Standard library only.
Run from the repo root:  python3 tools/skingen/gen_parts.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixlib import (Canvas, SKIN_PARTS, hx, tone, mix, noise, paint, paint_k,  # noqa: E402
                    mirror_side)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/ninja/parts")

P = SKIN_PARTS
ARMS = ("rarm", "larm")
LEGS = ("rleg", "lleg")


def new():
    return Canvas(64, 64)


# ---------------------------------------------------------------------------
# Skin (tinted by skin tone)
# ---------------------------------------------------------------------------

def part_skin():
    cv = new()

    def head(face, x, y, W, H):
        if face == "front":
            k = 0.0
            if x in (0, 7):
                k -= 0.35
            if y == 7:
                k -= 0.3
            if y == 3 and x in (1, 2, 5, 6):
                k -= 0.15  # brow ridge
            if y == 5 and x == 4:
                k -= 0.55  # nose shadow
            if y == 5 and x == 3:
                k += 0.15
            if y == 6 and x in (3, 4):
                k -= 0.95  # mouth
            if y == 5 and x in (1, 6):
                k += 0.1
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
                k += 0.15
            if y == 4 and x in (1, 2, 5, 6):
                k -= 0.25  # chest line
            if x in (3, 4) and 5 <= y <= 9:
                k -= 0.12 if (y % 2) else 0.0
            if y == 8 and x == 4:
                k -= 0.6
        if face == "back" and x in (3, 4):
            k -= 0.15
        if face == "bottom":
            k -= 0.6
        return k

    def arm(face, x, y, W, H):
        k = 0.0
        if y == 5:
            k -= 0.15  # elbow
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
# Undershirts (tinted by shirt colour): dark and plain, so armour sits well on top
# ---------------------------------------------------------------------------

WRAP = [  # crossed collar: L = lapel (lighter), s = skin showing at the neck
    "sLs..sLs",
    ".Ls..sL.",
    "..Ls.L..",
    "...LL...",
    "....L...",
]


def part_shirt(style):
    cv = new()
    sleeve_len = {"mesh": 12, "tee": 5, "longsleeve": 11, "sleeveless": 0, "wrap": 9, "turtleneck": 11}[style]

    def cloth(face, x, y, salt):
        n = noise(x, y, salt)
        k = -0.05
        if n < 0.08:
            k -= 0.3
        elif n > 0.95:
            k += 0.2
        return k

    def mesh(x, y):
        """Mesh: lighter diagonal threads, see-through holes between them."""
        if (x + y) % 4 == 0 or (x - y) % 4 == 0:
            return 0.2
        return (-0.35, 215)

    def body(face, x, y, W, H):
        if y >= 10:
            return None  # the pants' waist covers rows 10-11
        if face in ("top", "bottom"):
            return 0.1 if face == "top" else None
        if style == "mesh":
            if face == "front" and y == 0 and x in (3, 4):
                return None
            return mesh(x, y)
        if style == "wrap" and face == "front" and y < len(WRAP):
            ch = WRAP[y][x]
            if ch == "s":
                return None
            if ch == "L":
                return 0.55 if y < 2 else 0.4
        if style == "tee" and face == "front" and y == 0 and x in (3, 4):
            return None
        if style == "turtleneck" and y == 0:
            return 0.35
        if style == "sleeveless" and face in ("right", "left") and y < 3:
            return None
        k = cloth(face, x, y, 11)
        if y == 9:
            k -= 0.25  # tucked in
        if face == "front" and y == 0 and style in ("tee", "longsleeve"):
            k += 0.3  # neckline
        return k

    def arm(face, x, y, W, H):
        if y >= sleeve_len:
            return None
        if face == "bottom":
            return None
        if face == "top":
            return 0.1
        if style == "mesh":
            return mesh(x, y)
        k = cloth(face, x, y, 13)
        if y == sleeve_len - 1:
            k += 0.3 if style != "wrap" else -0.4  # cuff / hem
        return k

    paint_k(cv, P["body"], body)
    if sleeve_len:
        for a in ARMS:
            paint_k(cv, P[a], arm)
    return cv


# ---------------------------------------------------------------------------
# Pants (tinted by pants colour)
# ---------------------------------------------------------------------------

def part_pants(style):
    cv = new()
    bottom = {"long": 10, "capri": 8, "shorts": 5}[style]

    def waist(face, x, y, W, H):
        if face in ("top", "bottom"):
            return None
        if y == 10:
            return -0.9  # belt
        if y == 11:
            return -0.1 if not (face == "front" and x in (3, 4)) else -0.35
        return None

    def leg(face, x, y, W, H):
        if face == "top":
            return 0.0
        if face == "bottom" or y >= bottom:
            return None
        k = 0.0
        n = noise(x, y, 41)
        if n < 0.08:
            k -= 0.3
        if face == "front" and y in (4, 5) and x in (1, 2):
            k -= 0.3 if y == 5 else 0.15  # knee fold
        if face in ("right", "left") and x == (0 if face == "right" else 3):
            k -= 0.2  # seam
        if y == bottom - 1:
            k -= 0.3 if style == "long" else -0.25  # cuff
        if y == 0:
            k -= 0.15
        return k

    paint_k(cv, P["body"], waist)
    for l in LEGS:
        paint_k(cv, P[l], leg)
    return cv


# ---------------------------------------------------------------------------
# Fixed-colour body pieces: wraps and sandals
# ---------------------------------------------------------------------------

WRAP_CLOTH = hx("#ddd7c9")


def wrap_px(x, y):
    if (x + 2 * y) % 5 == 0:
        return tone(WRAP_CLOTH, -0.7)
    return tone(WRAP_CLOTH, 0.2 if y % 2 == 0 else -0.25)


def part_leg_wraps():
    cv = new()
    for l in LEGS:
        paint(cv, P[l], lambda f, x, y, W, H: wrap_px(x, y) if 6 <= y <= 9 and f not in ("top", "bottom") else None)
    return cv


def part_arm_wraps():
    cv = new()
    for a in ARMS:
        paint(cv, P[a], lambda f, x, y, W, H: wrap_px(x, y) if 7 <= y <= 10 and f not in ("top", "bottom") else None)
    return cv


SANDAL = hx("#2b3043")


def part_sandals():
    cv = new()

    def leg(face, x, y, W, H):
        if face == "bottom":
            return tone(SANDAL, -0.6)
        if face == "top":
            return None
        if face == "front":
            if y == 10:
                return tone(SANDAL, 0.3)
            return None  # toes show
        if face == "back":
            if y >= 9:
                return tone(SANDAL, 0.0 if y < 11 else -0.4)
            return None
        mx = mirror_side(face, x, W)
        if y == 11:
            return tone(SANDAL, -0.4)
        if y == 10 and mx <= 2:
            return tone(SANDAL, 0.1)
        if y == 9 and mx <= 1:
            return tone(SANDAL, 0.2)  # ankle strap
        return None

    for l in LEGS:
        paint(cv, P[l], leg)
    return cv


# ---------------------------------------------------------------------------
# Eyes: whites (fixed colour) + iris (tinted by eye colour). Row 4 of the face, like the
# Sharingan / Byakugan glow layers (texture x 9-10 and 13-14, y 12).
# ---------------------------------------------------------------------------

WHITE = hx("#f1efe9")
LASH = hx("#2a1e1c")


def face_part(fn):
    """A part drawn only on the front of the head (base layer)."""
    cv = new()
    paint(cv, P["head"], lambda f, x, y, W, H: fn(x, y) if f == "front" else None)
    return cv


def face_part_k(fn):
    cv = new()
    paint_k(cv, P["head"], lambda f, x, y, W, H: fn(x, y) if f == "front" else None)
    return cv


EYE_STYLES = ("round", "sharp", "wide", "narrow", "pale")


def part_eyes(style):
    def whites(x, y):
        if style == "pale":
            if y == 4 and x in (1, 6):
                return hx("#f3eff8")
            return None
        if style == "narrow":
            if y == 4 and x in (1, 6):
                return tone(LASH, 0.6)
            return None
        if y == 4 and x in (1, 6):
            return WHITE
        if style == "wide" and y == 5 and x in (1, 6):
            return tone(WHITE, -0.8)
        if style == "sharp" and y == 3 and x in (2, 5):
            return LASH
        return None

    def iris(x, y):
        if y == 4 and x in (2, 5):
            return 0.25 if style in ("pale", "wide") else 0.0
        if style == "wide" and y == 5 and x in (2, 5):
            return -1.4
        if style == "pale" and y == 5 and x in (1, 2, 5, 6):
            return (-0.5, 200) if x in (2, 5) else (-0.9, 160)
        return None

    return face_part(whites), face_part_k(iris)


BROW_STYLES = ("thin", "thick", "fierce", "soft")


def part_brows(style):
    def fn(x, y):
        if style == "thin":
            if y == 3 and x in (1, 2, 5, 6):
                return -0.6 if x in (2, 5) else -0.2
        elif style == "thick":
            if y == 3 and x in (0, 1, 2, 5, 6, 7):
                return -1.0
            if y == 2 and x in (1, 2, 5, 6):
                return -0.6
        elif style == "fierce":
            if y == 3 and x in (2, 5):
                return -1.0
            if y == 3 and x in (1, 6):
                return -0.4
            if y == 2 and x in (0, 7):
                return -0.6
        elif style == "soft":
            if y == 3 and x in (1, 6):
                return -0.5
            if y == 2 and x in (2, 5):
                return -0.3
        return None
    return face_part_k(fn)


# ---------------------------------------------------------------------------
# Hair (tinted by hair colour). ASCII maps: . none, H highlight, h hair, d shade, x deep shade.
# Side maps are for the right face: column 0 = back of the head, column 7 = the face side.
# base_* go on the head itself, the others on the outer (hat) layer for volume.
# jacket_back: rows of hair hanging down the back (outer body layer).
# ---------------------------------------------------------------------------

E = "........"
HAIR = {
    "spiky": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h......h", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 7 + ["dhdhhdhd"],
        "front": ["HhHHhHHh", "hhdhhdhh", "d.hd.hd.", "d......d", E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhd", "hhhdhhd.", "hdhhd.d.", "dhd.d...", "d.d.....", E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hdhhdhhd", "hhdhhhdh", "dhhdhhhd", "hdhhdhdh", "d.dh.dd.", ".d..d..d"],
    },
    "messy": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hh.....h", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhh...", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["hHhHHhHh", "hhhdhhhd", "hdhh.dhd", ".d.h..d.", "...d....", E, E, E],
        "side": ["HhHHhHHh", "hhdhhhhh", "hhhhdhhd", "dhhhhd.d", "hdhd.d..", ".d.d....", E, E],
        "back": ["hHhHHhHh", "hhhhhhhh", "hhdhhdhh", "dhhhhhhd", "hhdhdhhh", "hdhhhdhd", "dhd.dhd.", "d...d..d"],
    },
    "short": {
        "base_front": ["hhhhhhhh", "hhhhhh.h", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhhh...", "hhh.....", "hh......", E, E],
        "base_back": ["hhhhhhhh"] * 6 + [E, E],
        "front": ["HhHHhHHh", "hhhhd..h", "d......d", E, E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "dhhhd...", "hd......", E, E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhhhhh", "hdhhhhdh", "dhhdhhhd", "dhdddhdd", E, E],
    },
    "ponytail": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhhh...", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHHhHHh", "hhd.hhdh", "d......d", "d.......", E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "hhhhd...", "dhhd....", "dd......", E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhdhhdhh", "hhhxxhhh", "dh.hh.hd", "...hd...", "...dh...", "...hd..."],
        "jacket_back": ["...hd...", "...dh...", "...hd...", "....d..."],
    },
    "long": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h......h", "h......h", E, E, E, E],
        "base_side": ["hhhhhhhh"] * 8,
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHhhHhH", "hhd..dhh", "hd....dh", "h......h", "h......h", "d......d", "d......d", "x......x"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhdhh", "hdhhhhhd", "hhhhdhhh", "dhhhhhdh", "hhdhhhhd", "dhhdhdhd"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhdhhhdh", "hhhhdhhh", "hdhhhhhd", "hhhdhhhh", "dhhhhdhh", "hhdhhhhd"],
        "jacket_back": ["hhhhhhhh", "hdhhhhdh", "dhhdhhhd", ".dhhdhd.", "..d..d.."],
    },
    "swept": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", ".hhhhh..", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 7 + ["dhhdhdhh"],
        "front": ["HHhHHhHh", "hhhhhhhd", ".dhhhhd.", "...dhhh.", "....dhh.", ".....d..", E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhdh", "hhhhd..d", "hdhd....", "dd......", E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhdhhh", "hdhhhhdh", "hhdhhdhh", "dhhhhhhd", "hdhdhdh.", ".d...d.."],
    },
    "bowl": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhh..hhh", "hh......", E, E, E],
        "base_back": ["hhhhhhhh"] * 6 + [E, E],
        "front": ["hHHHHHHh", "hhhhhhhh", "dddddddd", E, E, E, E, E],
        "side": ["HHHHHHHH", "hhhhhhhh", "hhhhhhhh", "ddd...dd", E, E, E, E],
        "back": ["HHHHHHHH", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "dddddddd", E, E],
    },
    "buzz": {
        "base_front": ["hhhhhhhh", "dhhhhhhd", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhh.....", "hhh.....", "hh......", E, E],
        "base_back": ["hhhhhhhh"] * 5 + ["dhdhdhdh", E, E],
        "front": [E] * 8, "side": [E] * 8, "back": [E] * 8,
        "no_hat_top": True,
    },
    "topknot": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhh....", "hhh.....", "hh......", E, E],
        "base_back": ["hhhhhhhh"] * 6 + ["dhhhhhhd", E],
        "front": ["..dHHd..", "d......d", E, E, E, E, E, E],
        "side": ["..dhhd..", "hhhhhhhd", "dhhhd...", E, E, E, E, E],
        "back": ["..dHHd..", "hhhxxhhh", "dhhhhhhd", "hdhhhhdh", "dhdhhdhd", E, E, E],
        "top_knot": True,
    },
    "duck": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hh....hh", "h......h", E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", E],
        "base_back": ["hhhhhhhh"] * 7 + ["dhdhhdhd"],
        "front": ["HhHHhHHh", "hhhdhhhh", "hd....dh", "h......h", "h......h", "d......d", E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhdh", "hhhhhd..", "hdhd....", "d.d.....", E, E],
        "back": ["HhHHhHHh", "hHhhHhhH", "hdhhdhhd", "dhhdhhhd", "hdhdhdhd", "dhdhhdhd", "d.dd.d.d", ".d..d..."],
    },
    "bun": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h.....hh", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", E],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHhhHhH", "hhhhhdhh", "hd....hd", "d.......", E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "dhhhd...", "hd......", E, E, E],
        "back": ["hhhhhhhh", "hhdHHdhh", "hdHhhHdh", "hdhhhhdh", "hhdxxdhh", "dhhhhhhd", "dhhhhhhd", ".dddddd."],
    },
    "hime": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hh....hh", "h......h", "h......h", E, E, E],
        "base_side": ["hhhhhhhh"] * 8,
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHhhHhH", "hhhhhhhh", "dd....dd", "h......h", "h......h", "h......h", "h......h", "d......d"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhhh", "hhhdhhhd", "hhhhhhhh", "dhhhhdhh", "hhhhhhhh", "hdhhdhhd"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhhhhh", "hdhhhhdh", "hhhhhhhh", "hhdhhdhh", "hhhhhhhh", "dhhhhhhd"],
        "jacket_back": ["hhhhhhhh", "hhhhhhhh", "hdhhhhdh", "hhhdhhhh", "dhhhhhhd", "hhdhhdhh", ".hdhhdh.", "..d..d.."],
    },
}

HAIR_K = {"H": 0.9, "h": 0.0, "d": -0.9, "x": -1.8}


def part_hair(name, salt):
    style = HAIR[name]
    cv = new()

    def k_of(ch, x, y, s):
        k = HAIR_K[ch]
        if ch == "h" and noise(x, y, salt * 13 + s) < 0.18:
            k -= 0.45
        return k

    def base(face, x, y, W, H):
        if face == "top":
            return k_of("h", x, y, 1)
        if face == "bottom":
            return None
        if face == "front":
            ch = style["base_front"][y][x]
        elif face in ("right", "left"):
            ch = style["base_side"][y][mirror_side(face, x, W)]
        else:
            ch = style["base_back"][y][x]
        return None if ch == "." else k_of(ch, x, y, 2)

    def hat(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            if style.get("no_hat_top"):
                return None
            if style.get("top_knot"):
                if 2 <= x <= 5 and 1 <= y <= 4:
                    return 1.0 if (x, y) in ((3, 2), (4, 2)) else (-0.8 if x in (2, 5) or y in (1, 4) else 0.3)
            r = noise(x, y, salt + 9)
            return 0.9 if r > 0.8 else (-0.6 if noise(x, y, salt + 3) < 0.2 else 0.35)
        if face == "front":
            ch = style["front"][y][x]
        elif face in ("right", "left"):
            ch = style["side"][y][mirror_side(face, x, W)]
        else:
            ch = style["back"][y][x]
        return None if ch == "." else k_of(ch, x, y, 5 if face == "front" else 6)

    paint_k(cv, P["head"], base)
    paint_k(cv, P["hat"], hat)
    rows = style.get("jacket_back", [])
    if rows:
        def jb(face, x, y, W, H):
            if face == "back" and y < len(rows) and rows[y][x] != ".":
                return k_of(rows[y][x], x, y, 7)
            return None
        paint_k(cv, P["jacket"], jb)
    return cv


# ---------------------------------------------------------------------------
# Face marks (fixed colours; on the face itself)
# ---------------------------------------------------------------------------

def rgba(c, a):
    return tuple(c) + (a,)


MARKS = {}


def mark(name):
    def deco(fn):
        MARKS[name] = fn
        return fn
    return deco


@mark("whiskers")
def _whiskers(x, y):
    if (x, y) in ((0, 5), (1, 5), (0, 6), (1, 6), (6, 5), (7, 5), (6, 6), (7, 6)) and (x + y) % 2 == 1:
        return rgba(hx("#3b2a24"), 170)
    if (x, y) in ((1, 5), (6, 5)):
        return rgba(hx("#3b2a24"), 120)
    return None


@mark("fangs")
def _fangs(x, y):
    red = hx("#b3262a")
    if (x, y) in ((1, 5), (6, 5)):
        return red
    if (x, y) in ((1, 6), (6, 6)):
        return tone(red, -0.7)
    if (x, y) in ((0, 5), (7, 5)):
        return tone(red, -0.3)
    return None


@mark("swirls")
def _swirls(x, y):
    red = hx("#c0392b")
    if (x, y) in ((0, 5), (1, 5), (1, 6), (7, 5), (6, 5), (6, 6)):
        return red
    if (x, y) in ((0, 6), (7, 6)):
        return tone(red, -0.8)
    return None


@mark("scar_eye")
def _scar_eye(x, y):
    if x == 6 and y in (2, 3, 5, 6):
        return rgba(hx("#9a5a4e"), 230)
    return None


@mark("scar_nose")
def _scar_nose(x, y):
    if y == 5 and 2 <= x <= 5:
        return rgba(hx("#a0604f"), 220 if x in (3, 4) else 160)
    return None


@mark("tear_lines")
def _tear(x, y):
    if x in (2, 5) and y in (5, 6):
        return rgba(hx("#4a3a3a"), 150 if y == 5 else 100)
    return None


@mark("diamond")
def _diamond(x, y):
    if (x, y) in ((3, 1), (4, 1)):
        return hx("#7b3fa0")
    if (x, y) in ((3, 2), (4, 2)):
        return hx("#5a2a7a")
    return None


@mark("war_paint")
def _war(x, y):
    if y == 3 and x in (0, 1, 6, 7):
        return hx("#6a2f8a")
    if y == 5 and x in (0, 7):
        return hx("#6a2f8a")
    return None


@mark("freckles")
def _freckles(x, y):
    if (x, y) in ((1, 5), (2, 6), (5, 5), (6, 6), (0, 6), (7, 5)):
        return rgba(hx("#7a4a30"), 110)
    return None


@mark("stripes")
def _stripes(x, y):
    if x in (0, 7) and y in (4, 5, 6):
        return hx("#2f4f8a") if y != 5 else hx("#3f63a8")
    return None


# ---------------------------------------------------------------------------
# Accessories (outer head layer). "mask" is tinted, the rest are fixed colours.
# ---------------------------------------------------------------------------

def part_mask():
    cv = new()

    def fn(face, x, y, W, H):
        if face in ("top", "bottom"):
            return None
        if face == "front" and y >= 5:
            if y == 5:
                return 0.6 if x in (3, 4) else (0.15 if 0 < x < 7 else -0.4)  # nose ridge
            return -0.15 if 0 < x < 7 else -0.5
        if face in ("right", "left") and y >= 5:
            mx = mirror_side(face, x, W)
            return -0.25 if mx > 2 else -0.6
        if face == "back" and y >= 5:
            return -0.5
        return None

    paint_k(cv, P["hat"], fn)
    return cv


def hat_part(fn):
    cv = new()
    paint(cv, P["hat"], fn)
    return cv


def part_glasses():
    lens, frame = hx("#20242c"), hx("#55585e")

    def fn(face, x, y, W, H):
        if face == "front" and y == 4:
            if x in (1, 2, 5, 6):
                return hx("#c8d4e0") if x in (1, 5) else lens  # glint
            if x in (3, 4):
                return frame
            return frame
        if face in ("right", "left") and y == 4 and mirror_side(face, x, W) >= 3:
            return frame
        return None
    return hat_part(fn)


def part_shades():
    dark = hx("#17181c")

    def fn(face, x, y, W, H):
        if face == "front" and y == 4:
            return hx("#3a4a5a") if x in (1, 5) else dark
        if face == "front" and y == 3 and 1 <= x <= 6:
            return tone(dark, 0.4)
        if face in ("right", "left") and y == 4 and mirror_side(face, x, W) >= 2:
            return dark
        return None
    return hat_part(fn)


def part_head_bandage():
    def fn(face, x, y, W, H):
        if face in ("top", "bottom"):
            return None
        if y in (1, 2):
            return wrap_px(x, y + (1 if face == "back" else 0))
        if face == "back" and x in (3, 4) and y in (3, 4):
            return tone(WRAP_CLOTH, -0.4 if y == 4 else 0.0)
        return None
    return hat_part(fn)


def part_eyepatch():
    black = hx("#151518")

    def fn(face, x, y, W, H):
        if face == "front":
            if x in (5, 6) and y in (3, 4, 5):
                return tone(black, 0.4) if (x, y) == (5, 3) else black
            if (x, y) in ((4, 2), (3, 1), (2, 1), (1, 1), (0, 1), (7, 3)):
                return black
        if face in ("right", "left") and y == (3 if face == "left" else 1):
            return black
        if face == "back" and y == 2:
            return black
        return None
    return hat_part(fn)


def part_face_bandage():
    def fn(face, x, y, W, H):
        if face == "front" and y == 5 and 1 <= x <= 6:
            return wrap_px(x, y)
        if face == "front" and y == 6 and x in (5, 6):
            return wrap_px(x, y)
        return None
    return hat_part(fn)


# ---------------------------------------------------------------------------
# The appearance tables (looks.json). Weights are relative.
# ---------------------------------------------------------------------------

SKIN = {
    "pale": "#f1cdb0", "light": "#e8bc98", "fair": "#e0ac84", "medium": "#c98e66",
    "tan": "#b07650", "brown": "#8a5a3c", "dark": "#6a4330",
}

DARK_SHIRTS = [["#23252c", 4], ["#1c2233", 3], ["#2a2e3a", 3], ["#20201f", 2], ["#2e2a27", 2], ["#283028", 2], ["#33343c", 2]]
DARK_PANTS = [["#24262e", 4], ["#1f2433", 3], ["#2e3038", 3], ["#2a2622", 2], ["#33302a", 1], ["#3a3d46", 2]]
SHIRT_STYLES = [["mesh", 3], ["tee", 3], ["longsleeve", 3], ["sleeveless", 2], ["wrap", 2], ["turtleneck", 2]]
PANTS_STYLES = [["long", 5], ["capri", 3], ["shorts", 1]]
WRAPS = [[0, 5], [1, 3], [2, 2], [3, 2]]  # bit 1 = leg wraps, bit 2 = arm wraps
NORMAL_EYES = [["round", 4], ["sharp", 3], ["wide", 2], ["narrow", 2]]
BROWS = [["thin", 4], ["fierce", 3], ["soft", 3], ["thick", 1]]
ACC_COLORS = [["#1f2638", 3], ["#22252b", 3], ["#3a3f48", 2], ["#2c2330", 1]]


def w(d, **weights):
    return [[d[k], v] for k, v in weights.items()]


CLANS = {
    "uchiha": {
        "skin": w(SKIN, pale=4, light=4, fair=2, medium=1),
        "hair_style": [["spiky", 4], ["duck", 4], ["long", 2], ["messy", 2], ["short", 2], ["ponytail", 2], ["hime", 1]],
        "hair_color": [["#1b1c22", 5], ["#141418", 4], ["#1e2230", 2], ["#2a2224", 1]],
        "eyes": [["sharp", 4], ["round", 3], ["narrow", 2], ["wide", 1]],
        "eye_color": [["#1e1a1f", 4], ["#2a1f24", 3], ["#2b2b33", 2]],
        "brows": [["fierce", 3], ["thin", 3], ["soft", 2]],
        "shirt": [["turtleneck", 3], ["longsleeve", 3], ["mesh", 2], ["tee", 2], ["sleeveless", 1]],
        "shirt_color": [["#1c2233", 4], ["#1a1a20", 3], ["#23293d", 2], ["#2a2e3a", 1]],
        "pants": PANTS_STYLES,
        "pants_color": [["#24262e", 3], ["#1f2433", 3], ["#d9d4c8", 1], ["#2e3038", 2]],
        "wraps": WRAPS,
        "mark": [["none", 10], ["tear_lines", 3], ["scar_eye", 1]],
        "accessory": [["none", 12], ["mask", 1], ["head_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "senju": {
        "skin": w(SKIN, fair=3, medium=4, tan=4, light=2, brown=2),
        "hair_style": [["long", 5], ["swept", 3], ["spiky", 2], ["short", 2], ["ponytail", 2], ["topknot", 2], ["messy", 1]],
        "hair_color": [["#3a2618", 4], ["#4a3020", 3], ["#2a1d16", 3], ["#5a3a24", 2], ["#d8d8dc", 1], ["#a8743a", 1]],
        "eyes": [["round", 4], ["sharp", 3], ["narrow", 2], ["wide", 1]],
        "eye_color": [["#3a2a1c", 4], ["#2c2018", 3], ["#5a3a1e", 2], ["#7a2a24", 1]],
        "brows": [["thin", 2], ["fierce", 2], ["soft", 2], ["thick", 1]],
        "shirt": [["longsleeve", 3], ["wrap", 3], ["mesh", 2], ["tee", 2], ["sleeveless", 1]],
        "shirt_color": [["#2e2a27", 3], ["#20201f", 3], ["#283028", 2], ["#2c2522", 2]],
        "pants": PANTS_STYLES,
        "pants_color": [["#2a2622", 3], ["#24262e", 3], ["#33302a", 2]],
        "wraps": WRAPS,
        "mark": [["none", 10], ["stripes", 2], ["diamond", 1], ["scar_nose", 1], ["freckles", 1]],
        "accessory": [["none", 12], ["head_bandage", 1], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "hyuga": {
        "skin": w(SKIN, pale=6, light=3, fair=2),
        "hair_style": [["hime", 4], ["long", 4], ["ponytail", 2], ["bun", 2], ["short", 1], ["topknot", 1]],
        "hair_color": [["#2b1d16", 4], ["#302018", 3], ["#1d1a1c", 3], ["#3a271c", 2], ["#2a2638", 1]],
        "eyes": [["pale", 1]],
        "eye_color": [["#e4dcef", 4], ["#d9d0ea", 3], ["#ece8f2", 2]],
        "brows": [["thin", 3], ["soft", 3], ["fierce", 1]],
        "shirt": [["wrap", 3], ["longsleeve", 3], ["turtleneck", 2], ["mesh", 1], ["tee", 1]],
        "shirt_color": [["#2c2a3c", 3], ["#23252c", 3], ["#3a3550", 2], ["#e8e2d6", 1]],
        "pants": [["long", 6], ["capri", 2]],
        "pants_color": [["#2c2c3e", 3], ["#24262e", 2], ["#47405e", 2], ["#ddd4bd", 1]],
        "wraps": WRAPS,
        "mark": [["none", 12], ["freckles", 1]],
        "accessory": [["none", 8], ["head_bandage", 4], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "uzumaki": {
        "skin": w(SKIN, fair=4, light=3, pale=2, medium=2, tan=1),
        "hair_style": [["spiky", 4], ["messy", 3], ["long", 3], ["bun", 2], ["ponytail", 2], ["hime", 1], ["short", 1]],
        "hair_color": [["#d8352a", 5], ["#e03a2e", 3], ["#b8261f", 3], ["#e85a2a", 2], ["#e8c24a", 2], ["#8a1e1e", 1]],
        "eyes": [["round", 4], ["wide", 3], ["sharp", 2]],
        "eye_color": [["#2f4a6e", 3], ["#3a6a9a", 3], ["#4a3a6a", 2], ["#3a5a4a", 1]],
        "brows": [["soft", 3], ["thin", 2], ["fierce", 2]],
        "shirt": [["tee", 3], ["mesh", 2], ["longsleeve", 2], ["sleeveless", 2], ["wrap", 1]],
        "shirt_color": [["#23252c", 3], ["#1c2233", 2], ["#2c2330", 2], ["#3a2224", 1]],
        "pants": PANTS_STYLES,
        "pants_color": [["#24262e", 3], ["#2a2b38", 3], ["#3b1c1e", 1], ["#e07a30", 1]],
        "wraps": WRAPS,
        "mark": [["none", 8], ["whiskers", 3], ["freckles", 1]],
        "accessory": [["none", 12], ["head_bandage", 1], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "generic": {
        "skin": w(SKIN, pale=2, light=3, fair=3, medium=3, tan=3, brown=2, dark=2),
        "hair_style": [["spiky", 3], ["messy", 3], ["short", 3], ["ponytail", 2], ["long", 2], ["swept", 2],
                       ["bowl", 1], ["buzz", 2], ["topknot", 1], ["duck", 1], ["bun", 2], ["hime", 1]],
        "hair_color": [["#2a2522", 4], ["#4a3426", 4], ["#141414", 3], ["#6a4a2a", 2], ["#c7ccd2", 2],
                       ["#e3c26a", 2], ["#8a3a22", 1], ["#2f5a3a", 1], ["#2f3f6a", 1], ["#d98aa8", 1],
                       ["#7a7f88", 1], ["#5a3a6a", 1]],
        "eyes": NORMAL_EYES,
        "eye_color": [["#3a2a1e", 4], ["#2a2a2e", 3], ["#2d4f6a", 2], ["#3a5a3a", 2], ["#6a4a2a", 2], ["#5a5a62", 1]],
        "brows": BROWS,
        "shirt": SHIRT_STYLES,
        "shirt_color": DARK_SHIRTS,
        "pants": PANTS_STYLES,
        "pants_color": DARK_PANTS,
        "wraps": WRAPS,
        "mark": [["none", 9], ["fangs", 1], ["swirls", 1], ["scar_eye", 1], ["scar_nose", 1], ["war_paint", 1],
                 ["freckles", 1], ["stripes", 1], ["whiskers", 1]],
        "accessory": [["none", 9], ["mask", 2], ["glasses", 1], ["shades", 1], ["head_bandage", 1],
                      ["eyepatch", 1], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
}

# A generated clan's members share a family look (seed bits 48-63 = the clan): most of them
# wear the family mark, and half share the family hair colour.
FAMILY = {
    "mark": [["fangs", 3], ["swirls", 3], ["war_paint", 2], ["stripes", 2], ["whiskers", 1], ["none", 3]],
    "mark_chance": 70,
    "hair_chance": 50,
}

TINTED_ACCESSORIES = ["mask"]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    parts = {"skin": part_skin(), "sandals": part_sandals(),
             "leg_wraps": part_leg_wraps(), "arm_wraps": part_arm_wraps()}
    for s in ("mesh", "tee", "longsleeve", "sleeveless", "wrap", "turtleneck"):
        parts["shirt_" + s] = part_shirt(s)
    for s in ("long", "capri", "shorts"):
        parts["pants_" + s] = part_pants(s)
    for s in EYE_STYLES:
        parts["eyes_" + s], parts["iris_" + s] = part_eyes(s)
    for s in BROW_STYLES:
        parts["brows_" + s] = part_brows(s)
    for i, name in enumerate(HAIR):
        parts["hair_" + name] = part_hair(name, 17 + i * 7)
    for name, fn in MARKS.items():
        parts["mark_" + name] = face_part(fn)
    parts["acc_mask"] = part_mask()
    parts["acc_glasses"] = part_glasses()
    parts["acc_shades"] = part_shades()
    parts["acc_head_bandage"] = part_head_bandage()
    parts["acc_eyepatch"] = part_eyepatch()
    parts["acc_face_bandage"] = part_face_bandage()
    for name, cv in parts.items():
        cv.save(os.path.join(OUT_DIR, name + ".png"))
    looks = {"_comment": "Generated by tools/skingen/gen_parts.py: appearance tables per clan folder (weights are relative).",
             "clans": CLANS, "family": FAMILY, "tinted_accessories": TINTED_ACCESSORIES}
    with open(os.path.join(OUT_DIR, "looks.json"), "w") as f:
        json.dump(looks, f, indent=1)
        f.write("\n")
    print("wrote %d parts to %s" % (len(parts), os.path.relpath(OUT_DIR, ROOT)))


if __name__ == "__main__":
    main()

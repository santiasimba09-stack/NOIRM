#!/usr/bin/env python3
"""Wearable ninja gear for NOIRM: armour layer textures, item icons and a preview sheet.

Writes
    src/main/resources/assets/noirm/textures/models/armor/<texture>_layer_1.png / _layer_2.png
        (the vanilla 64x32 armour layout drawn at hd.SCALE = 4x, so 256x128: layer 1 = helmet,
         chestplate (body + arms), boots (legs); layer 2 = leggings (waist + legs))
    src/main/resources/assets/noirm/textures/item/<item>.png  (16x16 icons)
    docs/screenshots/gear-and-skins.png  (gear worn on a clean base, plus random NPC looks)

The preview needs the appearance parts, so run tools/skingen/gen_parts.py first.
The second set (kimonos, cloaks, masks, vests...) lives in more_gear.py.
The worn textures are drawn at full resolution in the "raw and tough" style of docs/ART_STYLE.md:
hd.py adds cloth folds with dark crevices, seams, stitches, scratched metal, frayed hems and dirt
toward the feet. Item icons stay 16x16, like vanilla's.
All art is original and drawn by the code below. Standard library only.
Run from the repo root:  python3 tools/gear/gen_gear.py
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, ARMOR_PARTS, SKIN_PARTS, faces, hx, tone, mix, noise, mirror_side  # noqa: E402
import looks as L  # noqa: E402
import hd  # noqa: E402
from hd import (Layer, paint, paint_hd, pal, family, SCALE, in_circle, in_ring, in_rrect, in_poly,  # noqa: E402
                on_polyline, engrave, arc, spiral, side, cross_collar)

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")
ARMOR_DIR = os.path.join(ASSETS, "textures/models/armor")
ITEM_DIR = os.path.join(ASSETS, "textures/item")
PARTS_DIR = os.path.join(ASSETS, "textures/entity/ninja/parts")
PREVIEW = os.path.join(ROOT, "docs/screenshots/gear-and-skins.png")

A = ARMOR_PARTS


def layer():
    return Layer()


def weave(c, x, y, salt, amount=1.0):
    """The cloth's weave is drawn at full resolution by hd.py (material "cloth")."""
    return c


# Shared colours
STEEL = pal("#a7adb5", "#d8dde2", "#7c828a", "#4e535a", mat="metal")
WRAPC = hx("#ddd7c9")
family([WRAPC, tone(WRAPC, -0.7), tone(WRAPC, 0.2), tone(WRAPC, -0.25)], "wrap")
DARK_CLOTH = hx("#26232a")
family([DARK_CLOTH, tone(DARK_CLOTH, -0.3), tone(DARK_CLOTH, -0.6), tone(DARK_CLOTH, 0.4)])
GOLD = pal("#c9a03a", "#ecc860", "#94711f", "#5e470f", mat="metal")


def wraps(x, y):
    """Bandage wraps: the overlapping strips are drawn by hd.py (material "wrap")."""
    return WRAPC


# ---------------------------------------------------------------------------
# The designs below are drawn at full resolution (hd.paint_hd): u, v are positions in the
# 64x32 layout's pixels, as floats, so every shape keeps its place at any SCALE. Each distinct
# colour is one part: hd.py adds its material, folds, seams, bevels and stitches.
# ---------------------------------------------------------------------------

ROPE_PURPLE = hx("#5e3f86")
family([ROPE_PURPLE, tone(ROPE_PURPLE, 0.5), tone(ROPE_PURPLE, -0.4)], "rope")
LEATHER_DARK = hx("#1e2028")
family([LEATHER_DARK, tone(LEATHER_DARK, 0.6)], "leather")
SOLE = hx("#3a2f24")
family([SOLE], "leather")

# ---------------------------------------------------------------------------
# Forehead protectors (helmet): a steel plate on a cloth band
# ---------------------------------------------------------------------------

# Our own village mark: a hook curling into a spiral (not a symbol from the series).
GLYPH = arc(4.0, 2.5, 0.72, 150, 445, 30) + [(4.75, 3.25), (5.45, 3.55)]
RIVETS = ((1.55, 1.55), (6.45, 1.55), (1.55, 3.55), (6.45, 3.55))
PLATE_RIM = tone(STEEL["b"], 0.35)


def protector_plate(u, v):
    """The steel plate on the front of the head (design phase), or None outside it."""
    if not in_rrect(u, v, 1.0, 1.05, 7.0, 4.05, 0.5):
        return None
    for rx, ry in RIVETS:
        if in_circle(u, v, rx, ry, 0.27):
            return STEEL["s"]
    if not in_rrect(u, v, 1.3, 1.32, 6.7, 3.78, 0.3):
        return PLATE_RIM
    return STEEL["b"]


def protector_marks(u, v, slashed=False):
    """Engravings and glints on the plate (final phase)."""
    if not in_rrect(u, v, 1.0, 1.05, 7.0, 4.05, 0.5):
        return None
    for rx, ry in RIVETS:
        if in_circle(u, v, rx - 0.09, ry - 0.09, 0.09):
            return STEEL["h"]
    if slashed:
        cut = engrave(u, v, [(1.25, 3.9), (3.6, 2.6), (6.8, 1.15)], 0.32, hx("#2a2c31"), STEEL["h"])
        if cut:
            return cut
    return engrave(u, v, GLYPH, 0.24, STEEL["d"], STEEL["h"])


def band_cloth(face, u, v, C, top=1.55, bottom=3.85):
    """The cloth band round the head: main cloth and a sewn lower hem."""
    if top <= v < bottom - 0.42:
        return C["b"]
    if bottom - 0.42 <= v < bottom:
        return C["s"]
    return None


def band_knot(u, v, C):
    """The knot and its two tails at the back of the head."""
    if in_rrect(u, v, 3.05, 1.35, 4.95, 4.1, 0.6):
        return tone(C["b"], 0.35)
    if on_polyline(u, v, [(3.55, 3.9), (3.15, 5.6), (2.75, 7.7)], 0.9):
        return tone(C["b"], -0.15)
    if on_polyline(u, v, [(4.45, 3.9), (4.95, 5.7), (5.35, 7.3)], 0.82):
        return C["s"]
    return None


def headband(cloth_hex, slashed=False):
    C = pal(cloth_hex)
    cv = layer()

    def fn(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        if face == "front":
            p = protector_plate(u, v)
            if p is not None:
                return p
        if face == "back":
            k = band_knot(u, v, C)
            if k is not None:
                return k
        return band_cloth(face, u, v, C)

    def marks(face, u, v, W, H):
        return protector_marks(u, v, slashed) if face == "front" else None

    paint_hd(cv, A["head"], fn)
    cv.overlay(A["head"], marks, "final")
    return cv


# ---------------------------------------------------------------------------
# Senju battle armour: red lacquered lamellar plates, laced in navy
# ---------------------------------------------------------------------------

RED = pal("#a8261e", "#d4483a", "#741812", "#44100c", mat="lacquer")
RED2 = mix(RED["b"], RED["h"], 0.3)
LACE = hx("#1f2236")
family([LACE], "cloth")
LACE_CORD = hx("#3d466c")
SENJU_CLOTH = hx("#2b2a33")
family([SENJU_CLOTH, tone(SENJU_CLOTH, 0.4)], "cloth")


def lamellar(u, v, v0, rows, row_h=1.95, lace_h=0.45, lame_w=1.0):
    """Rows of small lacquered plates (lames) with a laced band under each row."""
    r = v - v0
    i = math.floor(r / row_h)
    if r < 0 or i >= rows:
        return None
    if r - i * row_h > row_h - lace_h:
        return LACE
    j = math.floor((u + (i % 2) * lame_w * 0.5) / lame_w)
    return RED["b"] if j % 2 == 0 else RED2


def lacing(u, v, v0, rows, row_h=1.95, lace_h=0.45, lame_w=1.0):
    """Crossed cords on the laced bands (final phase)."""
    r = v - v0
    i = math.floor(r / row_h)
    if r < 0 or i >= rows:
        return None
    t = r - i * row_h - (row_h - lace_h)
    if t < 0:
        return None
    a = ((u + (i % 2) * lame_w * 0.5) % lame_w) / lame_w
    w = 0.9 / SCALE / lace_h
    if abs(t / lace_h - a) < w or abs(t / lace_h - (1 - a)) < w:
        return LACE_CORD
    return None


def senju():
    l1, l2 = layer(), layer()

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RED["b"]
        if v < 0.9:
            return DARK_CLOTH
        if face == "front":
            if in_circle(u, v, 4.0, 1.45, 0.55):
                return GOLD["b"]  # the chest cord's knot
            if on_polyline(u, v, [(3.8, 1.8), (3.45, 3.4)], 0.32) or on_polyline(u, v, [(4.2, 1.8), (4.6, 3.3)], 0.32):
                return GOLD["s"]
        if v < 8.7:
            return lamellar(u, v, 0.9, 4)
        if v < 9.25:
            return LACE
        # the skirt (kusazuri): separate hanging panels
        n = 3 if face in ("front", "back") else 2
        pw = W / n
        j = math.floor(u / pw)
        if abs(u - round(u / pw) * pw) < 0.12 and 0 < round(u / pw) < n:
            return DARK_CLOTH
        if 10.55 <= v < 10.95:
            return LACE
        return RED["b"] if (j + (v >= 10.95)) % 2 == 0 else RED2

    def body_marks(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        return lacing(u, v, 0.9, 4)

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RED["b"]
        if v < 4.6:
            if face == "left":
                return DARK_CLOTH
            if v < 0.3:
                return LACE
            return lamellar(u, v, 0.3, 2, 2.15, 0.45, 1.0)
        if v < 7.4:
            return DARK_CLOTH
        if v < 10.8:
            if v < 7.85:
                return GOLD["b"]
            if face == "left":
                return DARK_CLOTH
            if face in ("front", "back", "right") and 1.55 <= u <= 2.45:
                return RED["h"]  # the bracer's raised ridge
            return RED["b"]
        return None

    def arm_marks(face, u, v, W, H):
        if face in ("top", "bottom", "left"):
            return None
        return lacing(u, v, 0.3, 2, 2.15, 0.45, 1.0)

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return SOLE
        if v < 5:
            return None
        if v >= 10:  # black tabi socks
            if face == "front" and abs(u - 1.35) < 0.12 and v > 10.5:
                return tone(DARK_CLOTH, -0.6)  # the split toe
            return tone(DARK_CLOTH, -0.3) if v < 11.55 else SOLE
        if v < 5.5:
            return GOLD["b"]
        if face == "front" or (face in ("right", "left") and side(face, u, W) >= 2):
            # shin guard (suneate): three splints
            k = u if face == "front" else side(face, u, W) - 2
            if face == "front":
                if abs(u - 1.33) < 0.1 or abs(u - 2.67) < 0.1:
                    return DARK_CLOTH
                return RED["b"] if 1.33 < u < 2.67 else RED2
            return RED2 if k < 1.9 else RED["b"]
        return DARK_CLOTH  # laced closed at the back

    def boot_marks(face, u, v, W, H):
        if not 5.6 <= v < 9.9:
            return None
        if face == "back" or (face in ("right", "left") and side(face, u, W) < 2):
            a = (v - 5.6) % 0.9 / 0.9
            k = u if face == "back" else side(face, u, W)
            span = W if face == "back" else 2.0
            if abs(k / span - a) < 0.07 or abs(k / span - (1 - a)) < 0.07:
                return LACE_CORD
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], arm_marks, "final")
    paint_hd(l1, A["leg"], boot)
    l1.overlay(A["leg"], boot_marks, "final")

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if v < 9.5:
            return GOLD["s"]
        if face == "front" and in_rrect(u, v, 3.0, 9.6, 5.0, 11.9, 0.3):
            return RED["b"]  # a small plate over the knot
        return SENJU_CLOTH

    def thigh(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        if v < 5.6 and face != "back":  # thigh guards (haidate): rows of small scales
            if v < 0.45:
                return LACE
            row = math.floor((v - 0.45) / 1.05)
            off = 0.5 if row % 2 else 0.0
            j = math.floor(u + off)
            cx = j - off + 0.5
            cy = 0.45 + row * 1.05 + 0.45
            # scales are rounded at the bottom
            if (u - cx) ** 2 / 0.25 + max(0.0, v - cy) ** 2 / 0.36 <= 1.0:
                return RED["b"] if (j + row) % 2 == 0 else RED2
            return LACE
        if face == "front" and abs(u - 2.0) < 0.1 and v > 5.6:
            return tone(SENJU_CLOTH, 0.4)  # a pressed crease
        return SENJU_CLOTH

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], thigh)
    return l1, l2


# ---------------------------------------------------------------------------
# Uchiha high-collar tunic (navy, fan crest on the back) and dark pants with a rope belt
# ---------------------------------------------------------------------------

NAVY = pal("#273055", "#3a4677", "#1a2140", "#11162b")
FAN_C = {"r": hx("#8e1c20"), "R": hx("#c0282d"), "d": hx("#3a0e10"), "W": hx("#eeeae0"),
         "w": hx("#c9c4b8"), "H": hx("#6e6a62")}
family([FAN_C["R"], FAN_C["r"]], "print")
family([FAN_C["W"], FAN_C["w"]], "print")
family([FAN_C["H"]], "print")


def fan_crest(u, v, cx=4.0, cy=4.9, r=2.35):
    """Our clan fan: a round red-and-white fan on a short handle."""
    if in_circle(u, v, cx, cy, r):
        if in_circle(u, v, cx, cy, r) and not in_circle(u, v, cx, cy, r - 0.18) and v < cy + 0.25:
            return FAN_C["r"]  # the rim
        return FAN_C["R"] if v < cy + 0.25 else FAN_C["W"]
    if abs(u - cx) <= 0.42 and cy + r - 0.1 <= v <= cy + r + 2.1:
        return FAN_C["H"]
    return None


def uchiha():
    l1, l2 = layer(), layer()

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return NAVY["h"]
        if v < 1.3:
            if face == "front" and abs(u - 4.0) < 0.75 - v * 0.3:
                return NAVY["d"]  # the collar opens in front
            return NAVY["h"]  # the high collar
        if face == "back":
            f = fan_crest(u, v)
            if f is not None:
                return f
        if face in ("right", "left") and v > 9.3 and abs(side(face, u, W) - 2.0) < 0.2:
            return None  # side slit
        if v >= 11.3:
            return NAVY["s"]  # sewn hem
        return NAVY["b"]

    def arm(face, u, v, W, H):
        if face == "bottom" or v > 3.7:
            return None
        if face == "top":
            return NAVY["h"]
        return NAVY["s"] if v >= 3.0 else NAVY["b"]

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)

    pants = pal("#2a2c33", "#3a3d47", "#1d1f25", "#121317")

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if v < 9.4:
            return pants["b"]
        if face == "front":
            if in_circle(u, v, 4.0, 10.15, 0.7):
                return tone(ROPE_PURPLE, 0.5)  # the knot
            if on_polyline(u, v, [(3.7, 10.5), (3.35, 11.95)], 0.42) or on_polyline(u, v, [(4.3, 10.5), (4.7, 11.95)], 0.42):
                return tone(ROPE_PURPLE, 0.5)
        if v < 10.9:
            return ROPE_PURPLE
        return pants["b"]

    def leg(face, u, v, W, H):
        if face in ("top", "bottom"):
            return pants["b"] if face == "top" else None
        if 7.8 <= v < 11.2:
            return WRAPC
        if v >= 11.2:
            return pants["s"]
        if face == "front" and abs(u - 2.0) < 0.1:
            return pants["h"]  # pressed crease
        return pants["b"]

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    return l1, l2


# ---------------------------------------------------------------------------
# Hyuga robes: cream wrap with lavender trim and sash; pale lavender trousers
# ---------------------------------------------------------------------------

CREAM = pal("#e9e2d2", "#f7f3ea", "#c9c0ac", "#9c927e")
LAV = pal("#9a8cc4", "#b8acdc", "#6f639a", "#4a4170")


def hyuga():
    l1, l2 = layer(), layer()

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return CREAM["b"] if 0.6 < u < W - 0.6 else LAV["b"]
        if 7.0 <= v < 8.6:  # the obi sash with a cord
            if 7.7 <= v < 7.95:
                return LAV["d"]
            if face == "back" and in_rrect(u, v, 2.6, 6.6, 5.4, 9.1, 0.5):
                return LAV["h"]  # the knot
            return LAV["b"]
        if face == "front":
            c = cross_collar(u, v)
            if c == "open":
                return None
            if c == "trim":
                return LAV["b"]
            if c == "edge":
                return CREAM["d"]
        if v >= 11.3:
            return CREAM["s"]
        return CREAM["b"]

    def arm(face, u, v, W, H):
        if face == "bottom" or v > 10.4:
            return None
        if face == "top":
            return CREAM["b"]
        if v >= 9.6:
            return LAV["b"]  # the cuff
        return tone(CREAM["b"], -0.35) if face == "left" else CREAM["b"]  # wide sleeve's inner fold

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)

    pants = pal("#cfc8dc", "#e2dcec", "#a9a1bf", "#7c7496")

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 10:
            return None
        return LAV["d"] if v < 10.9 else pants["b"]

    def leg(face, u, v, W, H):
        if face == "top":
            return pants["b"]
        if face == "bottom":
            return None
        if v >= 9.9:  # bound cuffs
            return LAV["s"] if v < 11.3 else LAV["d"]
        if face in ("front", "back") and (abs(u - 1.1) < 0.12 or abs(u - 2.9) < 0.12) and v > 0.6:
            return pants["s"]  # pleats
        return pants["b"]

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    return l1, l2


# ---------------------------------------------------------------------------
# Uzumaki coat: open red coat, orange trim and flame hem, spiral on the back
# ---------------------------------------------------------------------------

CRIMSON = pal("#b0281f", "#d8463a", "#7c1a14", "#4e100c")
ORANGE = pal("#e8782a", "#f5a04a", "#b65618", "#7a3610")
SPIRAL_PTS = spiral(4.0, 5.3, 0.15, 2.45, 2.25, -math.pi / 2)


def flames(u, v, bottom, height, period=1.6, phase=0.0):
    """Flame tongues rising from the hem: 'core', 'flame' or None."""
    t = ((u + phase) % period) / period
    tongue = height * (0.45 + 0.55 * (1 - abs(2 * t - 1)) ** 1.6)
    if v >= bottom - tongue:
        core = height * 0.55 * (0.3 + 0.7 * (1 - abs(2 * t - 1)) ** 2)
        return "core" if v >= bottom - core and v < bottom - 0.25 else "flame"
    return None


def uzumaki():
    l1 = layer()

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return ORANGE["b"] if u < 0.6 or u > W - 0.6 else CRIMSON["b"]
        if face == "front":
            if 3.0 <= u <= 5.0 and v >= 0.9:
                return None  # worn open
            if v < 0.9:
                return ORANGE["h"]
            if 2.25 <= u < 3.0 or 5.0 < u <= 5.75:
                return ORANGE["b"]  # the lapels
        if v < 0.9:
            return ORANGE["h"]
        if face == "back" and on_polyline(u, v, SPIRAL_PTS, 0.48):
            return ORANGE["h"]
        if v >= 11.45:
            return ORANGE["b"]
        f = flames(u, v, 11.45, 1.8, 1.6, 0.3 if face == "back" else 0.0)
        if f:
            return ORANGE["s"] if f == "flame" else ORANGE["b"]
        return CRIMSON["b"]

    def arm(face, u, v, W, H):
        if face == "bottom" or v > 9:
            return None
        if face == "top":
            return CRIMSON["b"]
        if v >= 8.45:
            return ORANGE["b"]
        f = flames(u, v, 8.45, 1.3, 1.33)
        if f:
            return ORANGE["s"] if f == "flame" else ORANGE["b"]
        return CRIMSON["b"]

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Flak vest: green, padded collar, scroll pouches, zip, quilted panels
# ---------------------------------------------------------------------------

GREEN = pal("#5d6b3a", "#7c8a50", "#414c28", "#2c341b")
GREEN2 = tone(GREEN["b"], -0.18)
POUCH = mix(GREEN["b"], GREEN["h"], 0.35)
RING_RED = hx("#a8302a")
family([RING_RED], "print")


def pouch(u, v, x0, x1, y0, y1, body, flap):
    """A scroll pouch with its flap: 'flap', 'body' or None."""
    if not in_rrect(u, v, x0, y0, x1, y1, 0.22):
        return None
    return flap if v < y0 + (y1 - y0) * 0.3 else body


POUCHES = ((0.3, 1.75), (1.95, 3.45), (4.55, 6.05), (6.25, 7.7))


def zip_teeth(face, u, v, W, H, x=4.0, top=1.3, bottom=11.6):
    if face != "front" or not (top <= v <= bottom) or abs(u - x) > 0.24:
        return None
    step = 2.0 / SCALE
    if abs(u - x) < 0.06:
        return None
    row = math.floor(v / step)
    left = u < x
    return STEEL["h"] if (row % 2 == 0) == left else None


def flak():
    l1 = layer()

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return GREEN["h"]
        if v < 1.3:
            if face == "front" and abs(u - 4.0) < 0.6:
                return GREEN["d"]
            return GREEN["h"]  # padded collar
        if face == "front":
            if abs(u - 4.0) <= 0.28:
                return GREEN["d"]  # the zip's tape
            for x0, x1 in POUCHES:
                p = pouch(u, v, x0, x1, 2.0, 6.3, POUCH, GREEN["h"])
                if p:
                    return p
        if face == "back":
            if in_ring(u, v, 4.0, 4.9, 0.95, 1.65):
                return RING_RED  # the red ring on the back
            if v < 2.3:
                return GREEN2  # the yoke
        if face in ("right", "left") and v < 2.2:
            return GREEN["d"]  # under the arm
        if v >= 11.25:
            return GREEN["s"]
        return GREEN2 if 6.8 <= v < 8.9 else GREEN["b"]  # quilted panels

    def body_marks(face, u, v, W, H):
        z = zip_teeth(face, u, v, W, H)
        if z:
            return z
        if face == "front":
            for x0, x1 in POUCHES:
                if in_circle(u, v, (x0 + x1) / 2, 3.1, 0.16):
                    return GREEN["d"]  # snap button
        return None

    def arm(face, u, v, W, H):
        if face == "bottom" or face == "left" or v > 1.9:
            return None
        if face == "top":
            return GREEN["h"]
        return GREEN["h"] if v < 0.95 else GREEN2

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# ANBU-style gear: porcelain mask (helmet) and a plated vest over a black suit
# ---------------------------------------------------------------------------

PORC = pal("#ecebe6", "#ffffff", "#c9c7c0", "#8e8b84", mat="porcelain")
MASK_RED = hx("#b02a2a")
family([MASK_RED], "print")
STRAP = hx("#2a2a30")
family([STRAP, tone(STRAP, 0.5)], "leather")
HOLE = hx("#0c0c0e")
family([HOLE], "void")
SUIT = hx("#1e1f25")
family([SUIT, tone(SUIT, 0.3)], "cloth")
PLATE = pal("#c3c6cc", "#e0e2e6", "#8a8f97", "#5c6068", mat="metal")
PLATE2 = mix(PLATE["b"], PLATE["s"], 0.35)
SWORD_STRAP = hx("#5a4130")
family([SWORD_STRAP], "leather")


def mask_shape(u, v):
    """The mask's outline on the front of the head: rounded, narrowing to the chin."""
    if not in_rrect(u, v, 0.3, 0.15, 7.7, 7.75, 1.5):
        return False
    if v > 5.6 and abs(u - 4.0) > 3.7 - (v - 5.6) * 0.75:
        return False
    return True


def almond(u, v, cx, cy, rx, ry, tilt=0.0):
    dy = v - cy - tilt * (u - cx)
    return ((u - cx) / rx) ** 2 + (dy / ry) ** 2 <= 1.0


def mask_side(face, u, v, W):
    if side(face, u, W) >= 7.25 and 0.6 <= v <= 7.2:
        return PORC["s"]  # the mask's edge seen from the side
    if 2.9 <= v < 3.65:
        return STRAP
    return None


def mask_back(u, v):
    if 2.9 <= v < 3.65:
        return STRAP
    if in_rrect(u, v, 3.2, 2.6, 4.8, 4.2, 0.35):
        return tone(STRAP, 0.5)  # the buckle
    return None


def anbu():
    mask, armor = layer(), layer()

    def head(face, u, v, W, H):
        if face == "front":
            if not mask_shape(u, v):
                return None
            if almond(u, v, 2.35, 4.25, 0.95, 0.4, 0.18) or almond(u, v, 5.65, 4.25, 0.95, 0.4, -0.18):
                return HOLE
            if on_polyline(u, v, [(1.3, 3.25), (2.3, 2.55), (3.2, 2.85)], 0.42) or \
                    on_polyline(u, v, [(6.7, 3.25), (5.7, 2.55), (4.8, 2.85)], 0.42):
                return MASK_RED  # brows
            if on_polyline(u, v, [(4.0, 0.45), (4.0, 1.75)], 0.62 - 0.0):
                return MASK_RED  # forehead mark
            if on_polyline(u, v, [(0.85, 5.1), (1.55, 6.3)], 0.42) or on_polyline(u, v, [(7.15, 5.1), (6.45, 6.3)], 0.42):
                return MASK_RED  # cheek marks
            if in_poly(u, v, [(3.65, 3.7), (4.35, 3.7), (4.6, 6.1), (3.4, 6.1)]):
                return PORC["h"]  # the snout ridge
            return PORC["b"]
        if face in ("right", "left"):
            return mask_side(face, u, v, W)
        if face == "back":
            return mask_back(u, v)
        return None

    def head_marks(face, u, v, W, H):
        if face == "front" and (in_circle(u, v, 3.72, 6.35, 0.13) or in_circle(u, v, 4.28, 6.35, 0.13)):
            return PORC["d"]  # nostrils
        return None

    paint_hd(mask, A["head"], head)
    mask.overlay(A["head"], head_marks, "final")

    def body(face, u, v, W, H):
        if face == "bottom" or v >= 10:
            return None
        if face == "top":
            return PLATE["b"] if 1 <= u <= W - 1 else SUIT
        if face == "back" and on_polyline(u, v, [(0.6, 0.0), (7.4, 8.8)], 0.85):
            return SWORD_STRAP
        if face in ("front", "back") and in_rrect(u, v, 0.75, 0.7, 7.25, 7.8, 0.55):
            if 3.2 <= v < 5.4:
                return PLATE2
            return PLATE["b"]
        if 9.0 <= v < 9.8:
            if face == "front" and in_rrect(u, v, 3.35, 8.9, 4.65, 9.9, 0.15):
                return STEEL["b"]  # buckle
            return tone(SUIT, 0.3)  # belt
        return SUIT

    def body_marks(face, u, v, W, H):
        if face in ("front", "back"):
            for rx in (1.25, 6.75):
                for ry in (1.2, 7.3):
                    if in_circle(u, v, rx, ry, 0.17):
                        return PLATE["d"]
                    if in_circle(u, v, rx - 0.05, ry - 0.05, 0.07):
                        return PLATE["h"]
        if face == "back" and in_rrect(u, v, 3.0, 2.55, 4.4, 3.95, 0.12) and \
                not in_rrect(u, v, 3.2, 2.75, 4.2, 3.75, 0.08):
            return STEEL["h"]  # the strap's buckle
        return None

    def arm(face, u, v, W, H):
        if face in ("bottom", "top") or v < 6.2 or v > 10.3:
            return None
        if face == "left":
            return PLATE2
        if 6.65 <= v < 6.95 or 9.6 <= v < 9.9:
            return STRAP
        if 1.6 <= u <= 2.4 and face == "right":
            return PLATE["h"]  # a raised ridge
        return PLATE["b"]

    paint_hd(armor, A["body"], body)
    armor.overlay(A["body"], body_marks, "final")
    paint_hd(armor, A["arm"], arm)
    return mask, armor


# ---------------------------------------------------------------------------
# Generic shinobi pants (layer 2) and sandals (layer 1 legs)
# ---------------------------------------------------------------------------

def shinobi():
    l1, l2 = layer(), layer()
    sandal = pal("#2b3a5a", "#3e5080", "#1d2740", "#121826")

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return hx("#1a1a1e")
        if v < 8.5:
            return None
        if v >= 11.4:
            return sandal["d"]  # the sole
        if face == "front":
            if 10.0 <= v < 10.7:
                return sandal["h"]  # toe strap
            if v >= 10.7 and (u < 0.35 or u > 3.65):
                return sandal["s"]
            return None  # open toes
        if face == "back":
            if v < 9.1:
                return sandal["h"]
            return sandal["b"]
        s = side(face, u, W)
        if s < 1.9:
            if v < 9.1:
                return sandal["h"]
            return sandal["b"]  # heel cup
        if 10.0 <= v < 10.7:
            return sandal["h"]
        if on_polyline(u if face == "right" else W - u, v, [(1.9, 9.2), (3.6, 10.2)], 0.5):
            return sandal["h"]  # the strap from heel to toes
        return None

    def boot_marks(face, u, v, W, H):
        if face in ("right", "left") and side(face, u, W) < 1.9 and in_circle(u if face == "right" else W - u, v, 1.35, 8.8, 0.16):
            return STEEL["h"]  # a buckle
        return None

    paint_hd(l1, A["leg"], boot)
    l1.overlay(A["leg"], boot_marks, "final")

    pants = pal("#353a4c", "#4a5068", "#252937", "#171a24")
    patch = mix(pants["b"], pants["h"], 0.4)

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if 9.35 <= v < 10.55:
            if face == "front" and in_rrect(u, v, 3.3, 9.25, 4.7, 10.65, 0.18):
                return STEEL["b"] if not in_rrect(u, v, 3.62, 9.55, 4.38, 10.35, 0.1) else LEATHER_DARK
            return LEATHER_DARK
        return pants["b"]

    def leg(face, u, v, W, H):
        if face == "top":
            return pants["b"]
        if face == "bottom" or v >= 11.4:
            return None
        if v >= 8.0:
            return WRAPC
        if face == "front" and in_rrect(u, v, 0.45, 4.5, 3.55, 6.7, 0.5):
            return patch  # knee patch
        if face == "right" and in_rrect(u, v, 0.55, 0.9, 3.45, 3.7, 0.25):
            return mix(pants["b"], pants["s"], 0.4) if v < 1.6 else patch  # thigh pocket
        return pants["b"]

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    return l1, l2


# ---------------------------------------------------------------------------
# Item icons (16x16). Silhouettes: o outline, h highlight, b base, s shade; detail functions
# repaint some pixels per item.
# ---------------------------------------------------------------------------

CHEST = [
    "................",
    "...oooo..oooo...",
    "..ohhhboobbbso..",
    ".ohhbbbbbbbbbso.",
    "ohhbbbbbbbbbbsso",
    "ohbbbbbbbbbbbsso",
    "ooobbbbbbbbbsooo",
    "...obbbbbbbbso..",
    "...ohbbbbbbbso..",
    "...ohbbbbbbbso..",
    "...ohbbbbbbbso..",
    "...ohbbbbbbsso..",
    "...obbbbbbbsso..",
    "...oooooooooo...",
    "................",
    "................",
]
LEGS = [
    "................",
    "...oooooooooo...",
    "...ohhbbbbbso...",
    "...ohbbbbbbso...",
    "...ohbbbbbbso...",
    "...ohbsoohbso...",
    "...ohbsoohbso...",
    "...ohbsoohbso...",
    "...ohbsoohbso...",
    "...ohbsoohbso...",
    "...ohbsoohbso...",
    "...ohbsoohbso...",
    "...ohbsoohbso...",
    "...oooooooooo...",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".oooo....oooo...",
    ".ohso....ohso...",
    ".ohso....ohso...",
    ".ohso....ohso...",
    ".ohbooo..ohbooo.",
    ".ohbbbso.ohbbbso",
    ".ooooooo.ooooooo",
    "................",
    "................",
    "................",
    "................",
]
BAND = [
    "................",
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "oooooMMMMMMooooo",
    "ohhhhmMMmmmhhhho",
    "obbbbmmeemmbbbbo",
    "osssssnnnnnsssso",
    "oooooonnnnoooooo",
    ".....oooooo.otto",
    "............o.to",
    ".............oo.",
    "................",
    "................",
    "................",
]
MASK = [
    "................",
    "....oooooooo....",
    "...owwwwwwwwo...",
    "..owwwwRRwwwwo..",
    "..owrwwwwwwrwo..",
    "..owwrwwwwrwwo..",
    "..oweewwwweewo..",
    "..owwwwwwwwwwo..",
    "..owrwwhhwwrwo..",
    "...owrwsswrwo...",
    "...owwwwwwwwo...",
    "....owwwwwwo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]


def icon(template, colors, detail=None):
    cv = Canvas(16, 16)
    for y, row in enumerate(template):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = colors.get(ch)
            if detail:
                d = detail(x, y, ch)
                if d is not None:
                    c = d
            if c is not None:
                cv.set(x, y, c)
    return cv


def cols(p, outline=None):
    return {"o": outline or tone(p["d"], -0.6), "h": p["h"], "b": p["b"], "s": p["s"]}


def icons():
    out = {}
    for name, cloth in (("navy", "#24305a"), ("black", "#1c1c22"), ("red", "#9e2226")):
        c = pal(cloth)
        out["forehead_protector_" + name] = icon(BAND, {
            "o": tone(c["d"], -0.8), "h": c["h"], "b": c["b"], "s": c["s"], "t": c["s"],
            "M": STEEL["h"], "m": STEEL["b"], "n": STEEL["s"], "e": STEEL["d"]})

    def senju_detail(x, y, ch):
        if ch in "bhs" and y >= 2:
            if y % 3 == 1:
                return LACE if x % 2 == 0 else RED["d"]
            if y % 3 == 2 and ch == "b":
                return RED["h"]
        if y == 2 and ch in "bh" and 6 <= x <= 9:
            return GOLD["b"]
        return None
    out["senju_chestplate"] = icon(CHEST, cols(RED), senju_detail)

    def senju_legs_detail(x, y, ch):
        if ch in "bhs" and 2 <= y <= 6:
            return RED["h"] if y % 2 == 0 else RED["b"]
        if ch in "bhs" and y > 6:
            return tone(DARK_CLOTH, 0.4 if ch == "h" else (0 if ch == "b" else -0.4))
        return None
    out["senju_leggings"] = icon(LEGS, cols(RED), senju_legs_detail)

    def senju_boots_detail(x, y, ch):
        if y >= 10 and ch in "bhs":
            return tone(DARK_CLOTH, 0.3)
        if y == 6 and ch in "hs":
            return GOLD["b"]
        return None
    out["senju_boots"] = icon(BOOTS, cols(RED), senju_boots_detail)

    def uchiha_detail(x, y, ch):
        if ch == "o":
            return None
        if y == 2 and 6 <= x <= 9:
            return NAVY["d"]
        if 6 <= x <= 9 and 6 <= y <= 9:  # the fan crest
            return FAN_C["R"] if y < 8 else FAN_C["W"]
        if y in (10, 11) and x in (7, 8):
            return FAN_C["H"]
        return None
    out["uchiha_tunic"] = icon(CHEST, cols(NAVY), uchiha_detail)
    dark = pal("#2a2c33", "#3a3d47", "#1d1f25", "#121317")

    def uchiha_pants_detail(x, y, ch):
        if y == 2 and ch in "bhs":
            return hx("#5e3f86") if x % 2 else hx("#432c62")
        if y in (10, 11) and ch in "bhs":
            return WRAPC if (x + y) % 2 else tone(WRAPC, -0.5)
        return None
    out["uchiha_pants"] = icon(LEGS, cols(dark), uchiha_pants_detail)

    def hyuga_detail(x, y, ch):
        if ch == "o":
            return None
        if y in (2, 3, 4, 5) and x in (5 + y - 2, 10 - (y - 2)) and 5 <= x <= 10:
            return LAV["b"]
        if y == 8 and ch != "o":
            return LAV["d"]
        if y == 7 and ch != "o":
            return LAV["h"]
        if y >= 9 and x == 9:
            return CREAM["d"]
        return None
    out["hyuga_robe"] = icon(CHEST, cols(CREAM, tone(LAV["d"], -0.5)), hyuga_detail)
    lpants = pal("#cfc8dc", "#e2dcec", "#a9a1bf", "#7c7496")

    def hyuga_pants_detail(x, y, ch):
        if y >= 11 and ch in "bhs":
            return LAV["s"]
        if y == 2 and ch in "bhs":
            return LAV["d"]
        return None
    out["hyuga_pants"] = icon(LEGS, cols(lpants, tone(LAV["d"], -0.6)), hyuga_pants_detail)

    def uzumaki_detail(x, y, ch):
        if ch == "o":
            return None
        if x in (7, 8) and y >= 3:
            return DARK_CLOTH if y > 3 else ORANGE["s"]
        if x in (6, 9) and y >= 2:
            return ORANGE["b"]
        if y == 12:
            return ORANGE["b"]
        if y == 11 and x % 3 == 0:
            return ORANGE["s"]
        if y in (5, 6) and x in (0, 15, 1, 14):
            return ORANGE["b"]
        return None
    out["uzumaki_coat"] = icon(CHEST, cols(CRIMSON), uzumaki_detail)

    def flak_detail(x, y, ch):
        if ch == "o":
            return None
        if x == 7 and y >= 2:
            return GREEN["s"]
        if x == 8 and y >= 2:
            return GREEN["h"]
        if 7 <= y <= 11 and x in (4, 5, 6, 9, 10, 11):
            if y == 7:
                return GREEN["h"]
            if y == 11:
                return GREEN["d"]
            if x in (5, 10):
                return GREEN["s"]
        if y in (2, 3) and ch in "bh":
            return GREEN["h"]
        return None
    out["flak_vest"] = icon(CHEST, cols(GREEN), flak_detail)

    out["anbu_mask"] = icon(MASK, {"o": tone(PORC["d"], -1.2), "w": PORC["b"], "r": MASK_RED, "R": MASK_RED,
                                   "e": hx("#0c0c0e"), "h": PORC["h"], "s": PORC["s"]})

    def anbu_detail(x, y, ch):
        if ch == "o":
            return None
        if x <= 2 or x >= 13 or y >= 11:
            return tone(SUIT, 0.5 if ch == "h" else 0)
        if 4 <= x <= 11 and 2 <= y <= 10:
            if y in (5, 8):
                return PLATE["s"]
            return PLATE["h"] if y in (2, 3) or x == 4 else PLATE["b"]
        return tone(SUIT, 0.3)
    out["anbu_armor"] = icon(CHEST, cols(PLATE), anbu_detail)

    spants = pal("#353a4c", "#4a5068", "#252937", "#171a24")

    def spants_detail(x, y, ch):
        if y in (10, 11, 12) and ch in "bhs":
            return WRAPC if (x + y) % 2 else tone(WRAPC, -0.5)
        if y == 2 and ch in "bhs":
            return hx("#1e2028") if x not in (7, 8) else STEEL["h"]
        return None
    out["shinobi_pants"] = icon(LEGS, cols(spants), spants_detail)
    sandal = pal("#2b3a5a", "#3e5080", "#1d2740", "#121826")

    def sandal_detail(x, y, ch):
        if y == 10 and ch == "b" and x in (4, 5, 12, 13):
            return None if x in (5, 13) else sandal["h"]  # open toes
        return None
    out["shinobi_sandals"] = icon(BOOTS, cols(sandal), sandal_detail)
    return out


# ---------------------------------------------------------------------------
# Preview
# ---------------------------------------------------------------------------

def doll(sheet, skin, armor, dx, dy, s, view):
    """Flat front/back doll: skin (base + outer layer), then leggings, boots, chest, helmet.
    s = screen pixels per base (64-layout) pixel; skins and armour may be any multiple of
    their base size (HD textures are resampled)."""
    def blit(cv, box, ox, oy, flip=False, face=None, grow=0):
        if hasattr(cv, "render"):
            cv = cv.render()
        k = cv.w // 64  # texture pixels per base pixel
        fx, fy, fw, fh = faces(box)[face or view]
        ow, oh = fw * s + 2 * grow, fh * s + 2 * grow
        for y in range(oh):
            for x in range(ow):
                sx = x * fw * k // ow
                c = cv.get(fx * k + (fw * k - 1 - sx if flip else sx), fy * k + y * fh * k // oh)
                if c[3]:
                    sheet.blend(dx + ox * s + x - grow, dy + oy * s + y - grow, c)

    P = SKIN_PARTS
    if view == "front":
        lay = {"head": (4, 0), "body": (4, 8), "r": (0, 8), "l": (12, 8), "rl": (4, 20), "ll": (8, 20)}
    else:
        lay = {"head": (4, 0), "body": (4, 8), "r": (12, 8), "l": (0, 8), "rl": (8, 20), "ll": (4, 20)}
    for base, outer, key in (("head", "hat", "head"), ("body", "jacket", "body"), ("rarm", "rsleeve", "r"),
                             ("larm", "lsleeve", "l"), ("rleg", "rpants", "rl"), ("lleg", "lpants", "ll")):
        blit(skin, P[base], *lay[key])
        blit(skin, P[outer], *lay[key])
    l2 = armor.get("legs")
    if l2 is not None:
        blit(l2, A["body"], *lay["body"], grow=1)
        blit(l2, A["leg"], *lay["rl"], grow=1)
        blit(l2, A["leg"], *lay["ll"], flip=True, grow=1)
    if armor.get("feet") is not None:
        f = armor["feet"]
        blit(f, A["leg"], *lay["rl"], grow=2)
        blit(f, A["leg"], *lay["ll"], flip=True, grow=2)
    if armor.get("chest") is not None:
        c = armor["chest"]
        blit(c, A["body"], *lay["body"], grow=2)
        blit(c, A["arm"], *lay["r"], grow=2)
        blit(c, A["arm"], *lay["l"], flip=True, grow=2)
    if armor.get("head") is not None:
        blit(armor["head"], A["head"], *lay["head"], grow=2)
        blit(armor["head"], A["hat"], *lay["head"], grow=3)


def text_bar(sheet, x, y, w, color):
    sheet.fill_rect(x, y, w, 4, color + (255,))


CLAN_BAND = {
    "uchiha": (190, 40, 45), "senju": (110, 140, 70), "hyuga": (190, 180, 220),
    "uzumaki": (235, 120, 40), "generic": (120, 130, 145),
}


def npc_gear(folder, rng, tex):
    """Python mirror of gear/ClanOutfits (roughly), to preview what NPCs wear."""
    g = {}
    if rng.random() < 0.12:
        return g
    band = lambda: tex["forehead_protector_" + rng.choice(["navy", "navy", "black", "red"])][0]
    if folder == "senju":
        if rng.random() < 0.6:
            g["chest"] = tex["senju"][0]
            if rng.random() < 0.6:
                g["legs"] = tex["senju"][1]
            g["feet"] = tex["senju"][0] if rng.random() < 0.6 else tex["shinobi"][0]
        elif rng.random() < 0.4:
            g["chest"] = tex["flak"][0]
        if rng.random() < 0.4:
            g["head"] = band()
    elif folder == "uchiha":
        if rng.random() < 0.7:
            g["chest"] = tex["uchiha"][0]
        if rng.random() < 0.6:
            g["legs"] = tex["uchiha"][1]
        if rng.random() < 0.35:
            g["head"] = tex["forehead_protector_" + rng.choice(["navy", "black"])][0]
    elif folder == "hyuga":
        if rng.random() < 0.7:
            g["chest"] = tex["hyuga"][0]
        if rng.random() < 0.65:
            g["legs"] = tex["hyuga"][1]
        if rng.random() < 0.4:
            g["head"] = tex["forehead_protector_navy"][0]
    elif folder == "uzumaki":
        if rng.random() < 0.65:
            g["chest"] = tex["uzumaki"][0]
        if rng.random() < 0.5:
            g["legs"] = tex["shinobi"][1]
        if rng.random() < 0.4:
            g["head"] = tex["forehead_protector_" + rng.choice(["red", "navy"])][0]
    else:
        if rng.random() < 0.05:
            g["head"], g["chest"] = tex["anbu"]
        else:
            if rng.random() < 0.45:
                g["chest"] = tex["flak"][0]
            if rng.random() < 0.5:
                g["head"] = band()
        if rng.random() < 0.5:
            g["legs"] = tex["shinobi"][1]
    if "feet" not in g and rng.random() < 0.5:
        g["feet"] = tex["shinobi"][0]
    return g


def preview(tex, item_icons):
    looks = L.load_looks(PARTS_DIR)
    s = 4
    doll_w, doll_h = 16 * s, 32 * s
    W = 1180
    sets = [
        ("Forehead protector + flak vest", "generic", {"head": tex["forehead_protector_navy"][0], "chest": tex["flak"][0],
                                                       "legs": tex["shinobi"][1], "feet": tex["shinobi"][0]}),
        ("Senju battle armour", "senju", {"head": tex["forehead_protector_red"][0], "chest": tex["senju"][0],
                                          "legs": tex["senju"][1], "feet": tex["senju"][0]}),
        ("Uchiha tunic + pants", "uchiha", {"head": tex["forehead_protector_black"][0], "chest": tex["uchiha"][0],
                                            "legs": tex["uchiha"][1], "feet": tex["shinobi"][0]}),
        ("Hyuga robes", "hyuga", {"chest": tex["hyuga"][0], "legs": tex["hyuga"][1], "feet": tex["shinobi"][0]}),
        ("Uzumaki coat", "uzumaki", {"head": tex["forehead_protector_red"][0], "chest": tex["uzumaki"][0],
                                     "legs": tex["shinobi"][1], "feet": tex["shinobi"][0]}),
        ("ANBU mask + armour", "generic", {"head": tex["anbu"][0], "chest": tex["anbu"][1],
                                           "legs": tex["shinobi"][1], "feet": tex["shinobi"][0]}),
        ("No gear (clean base)", "generic", {}),
    ]
    set_w = doll_w * 2 + 3 * s
    per_row = 7
    row1_h = doll_h + 16
    icon_h = 16 * 4 + 16
    npc_cols = 20
    npc_rows = 2
    npc_w = doll_w + 2
    npc_h = doll_h + 12
    H = 10 + row1_h + icon_h + npc_rows * npc_h * 2 + 40
    W = max(W, per_row * (set_w + 12) + 20, npc_cols * (npc_w + 4) + 20)
    sheet = Canvas(W, H, (46, 48, 54, 255))

    base_keys = {"generic": "generic:3a7f00c9b2", "senju": "senju:51e2a7", "uchiha": "uchiha:9c41d",
                 "hyuga": "hyuga:2b7e11", "uzumaki": "uzumaki:7aa2c3"}
    x = 10
    for title, folder, armor in sets:
        skin = L.compose(L.decode(base_keys[folder], looks), looks, PARTS_DIR)
        text_bar(sheet, x, 10, set_w, CLAN_BAND[folder])
        doll(sheet, skin, armor, x, 18, s, "front")
        doll(sheet, skin, armor, x + doll_w + 3 * s, 18, s, "back")
        x += set_w + 12
    # icons
    y0 = 10 + row1_h + 6
    x = 10
    for name, cv in item_icons.items():
        sheet.fill_rect(x - 2, y0 - 2, 16 * 4 + 4, 16 * 4 + 4, (62, 64, 72, 255))
        sheet.blit(cv, 0, 0, 16, 16, x, y0, 4)
        x += 16 * 4 + 10
    # random NPCs: top block bare, bottom block with clan gear
    rng = random.Random(7)
    folders = ["uchiha", "senju", "hyuga", "uzumaki", "generic"]
    y0 += icon_h
    keys = []
    for i in range(npc_cols * npc_rows):
        folder = folders[i // 8 % 5]
        seed = rng.getrandbits(48)
        if folder == "generic":
            seed |= (rng.choice([0x1a2b, 0x3c4d, 0x5e6f, 0x7081])) << 48
        keys.append("%s:%x" % (folder, seed))
    for block in range(2):
        for i, key in enumerate(keys):
            folder = key.split(":")[0]
            cx = 10 + (i % npc_cols) * (npc_w + 4)
            cy = y0 + block * npc_rows * npc_h + (i // npc_cols) * npc_h
            text_bar(sheet, cx, cy, doll_w, CLAN_BAND[folder])
            skin = L.compose(L.decode(key, looks), looks, PARTS_DIR)
            armor = npc_gear(folder, random.Random(i * 31 + 5), tex) if block == 1 else {}
            doll(sheet, skin, armor, cx, cy + 8, s, "front")
    sheet.save(PREVIEW)


def main():
    os.makedirs(ARMOR_DIR, exist_ok=True)
    tex = {}
    for name, cloth in (("navy", "#24305a"), ("black", "#1c1c22"), ("red", "#9e2226")):
        tex["forehead_protector_" + name] = (headband(cloth), None)
    tex["senju"] = senju()
    tex["uchiha"] = uchiha()
    tex["hyuga"] = hyuga()
    tex["uzumaki"] = (uzumaki(), None)
    tex["flak"] = (flak(), None)
    tex["anbu"] = anbu()  # (mask, armour): two layer-1 textures, see below
    tex["shinobi"] = shinobi()
    written = 0
    for name, pair in tex.items():
        if name == "anbu":
            pair[0].save(os.path.join(ARMOR_DIR, "anbu_mask_layer_1.png"))
            pair[1].save(os.path.join(ARMOR_DIR, "anbu_layer_1.png"))
            written += 2
            continue
        for i, cv in enumerate(pair):
            if cv is not None:
                cv.save(os.path.join(ARMOR_DIR, "%s_layer_%d.png" % (name, i + 1)))
                written += 1
    item_icons = icons()
    for name, cv in item_icons.items():
        cv.save(os.path.join(ITEM_DIR, name + ".png"))
    print("wrote %d armour layers and %d icons" % (written, len(item_icons)))
    preview(tex, item_icons)
    print("preview:", os.path.relpath(PREVIEW, ROOT))
    more(tex)
    import armor_wave  # the third set (round 16): shinobi basics, heavy armour, rogues, robes
    armor_wave.main()


MORE_PREVIEW = os.path.join(ROOT, "docs/screenshots/gear-more.png")


def more(tex):
    """The second gear set (tools/gear/more_gear.py): textures, icons and its own preview."""
    import more_gear as M
    extra = M.textures()
    written = 0
    for name, pair in extra.items():
        for i, cv in enumerate(pair):
            if cv is not None:
                cv.save(os.path.join(ARMOR_DIR, "%s_layer_%d.png" % (name, i + 1)))
                written += 1
    extra_icons = M.icons({"CHEST": CHEST, "LEGS": LEGS, "BOOTS": BOOTS, "BAND": BAND})
    for name, cv in extra_icons.items():
        cv.save(os.path.join(ITEM_DIR, name + ".png"))
    print("more gear: wrote %d armour layers and %d icons" % (written, len(extra_icons)))
    looks = L.load_looks(PARTS_DIR)
    skin = L.compose(L.decode("generic:3a7f00c9b2", looks), looks, PARTS_DIR)
    s = 4
    doll_w, doll_h = 16 * s, 32 * s
    l1 = lambda n: extra[n][0]
    pants, sandals = tex["shinobi"][1], tex["shinobi"][0]
    sets = [
        {"chest": l1("kimono_indigo"), "legs": extra["kimono_indigo"][1], "feet": l1("waraji")},
        {"chest": l1("kimono_crimson"), "legs": extra["kimono_crimson"][1], "feet": l1("tabi")},
        {"chest": l1("kimono_sage"), "legs": extra["kimono_sage"][1], "head": l1("straw_hat")},
        {"chest": l1("kimono_plum"), "legs": extra["kimono_plum"][1], "head": l1("oni_mask")},
        {"chest": l1("haori"), "legs": extra["hakama"][1], "feet": l1("tabi")},
        {"chest": l1("jonin_vest"), "legs": pants, "feet": sandals, "head": l1("cloth_mask")},
        {"chest": l1("vest_sand"), "legs": pants, "feet": sandals, "head": l1("head_wrap_protector")},
        {"chest": l1("vest_snow"), "legs": pants, "feet": l1("shin_guards")},
        {"chest": l1("vest_mist"), "legs": pants, "feet": sandals, "head": l1("mask_wolf")},
        {"chest": l1("vest_cloud"), "legs": pants, "feet": sandals, "head": l1("mask_hawk")},
        {"chest": l1("vest_stone"), "legs": pants, "feet": sandals, "head": l1("mask_boar")},
        {"chest": l1("travel_cloak"), "legs": pants, "feet": l1("waraji"), "head": l1("straw_hat")},
        {"chest": l1("rogue_cloak"), "legs": pants, "feet": sandals, "head": l1("forehead_protector_slashed")},
        {"chest": l1("kage_robe"), "legs": pants, "feet": sandals, "head": l1("kage_hat_red")},
        {"chest": l1("kage_robe"), "legs": pants, "feet": sandals, "head": l1("kage_hat_teal")},
        {"chest": l1("arm_guards"), "legs": extra["hakama"][1], "feet": l1("tabi"), "head": l1("mask_crane")},
        {"chest": l1("hand_wraps"), "legs": pants, "feet": l1("shin_guards")},
    ]
    per_row = 9
    set_w = doll_w * 2 + 3 * s
    rows = (len(sets) + per_row - 1) // per_row
    icon_rows = (len(extra_icons) + 15) // 16
    W = per_row * (set_w + 12) + 20
    H = 10 + rows * (doll_h + 20) + icon_rows * 74 + 20
    sheet = Canvas(W, H, (46, 48, 54, 255))
    for i, armor in enumerate(sets):
        x = 10 + (i % per_row) * (set_w + 12)
        y = 10 + (i // per_row) * (doll_h + 20)
        doll(sheet, skin, armor, x, y, s, "front")
        doll(sheet, skin, armor, x + doll_w + 3 * s, y, s, "back")
    y0 = 10 + rows * (doll_h + 20)
    for i, (name, cv) in enumerate(extra_icons.items()):
        x = 10 + (i % 16) * 74
        y = y0 + (i // 16) * 74
        sheet.fill_rect(x - 2, y - 2, 68, 68, (62, 64, 72, 255))
        sheet.blit(cv, 0, 0, 16, 16, x, y, 4)
    sheet.save(MORE_PREVIEW)
    print("preview:", os.path.relpath(MORE_PREVIEW, ROOT))


if __name__ == "__main__":
    main()

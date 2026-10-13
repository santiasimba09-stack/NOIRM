#!/usr/bin/env python3
"""Wearable ninja gear for NOIRM: armour layer textures, item icons and a preview sheet.

Writes
    src/main/resources/assets/noirm/textures/models/armor/<texture>_layer_1.png / _layer_2.png
        (64x32, vanilla armour layout: layer 1 = helmet, chestplate (body + arms), boots (legs);
         layer 2 = leggings (waist + legs))
    src/main/resources/assets/noirm/textures/item/<item>.png  (16x16 icons)
    docs/screenshots/gear-and-skins.png  (gear worn on a clean base, plus random NPC looks)

The preview needs the appearance parts, so run tools/skingen/gen_parts.py first.
All art is original and drawn by the code below. Standard library only.
Run from the repo root:  python3 tools/gear/gen_gear.py
"""
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, ARMOR_PARTS, SKIN_PARTS, faces, hx, tone, mix, noise, paint, mirror_side  # noqa: E402
import looks as L  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")
ARMOR_DIR = os.path.join(ASSETS, "textures/models/armor")
ITEM_DIR = os.path.join(ASSETS, "textures/item")
PARTS_DIR = os.path.join(ASSETS, "textures/entity/ninja/parts")
PREVIEW = os.path.join(ROOT, "docs/screenshots/gear-and-skins.png")

A = ARMOR_PARTS


def layer():
    return Canvas(64, 32)


def pal(base, hi=None, sh=None, deep=None):
    b = hx(base)
    return {"b": b, "h": hx(hi) if hi else tone(b, 1), "s": hx(sh) if sh else tone(b, -1),
            "d": hx(deep) if deep else tone(b, -2.2)}


def weave(c, x, y, salt, amount=1.0):
    """A little cloth texture."""
    n = noise(x, y, salt)
    if n < 0.09:
        return tone(c, -0.3 * amount)
    if n > 0.95:
        return tone(c, 0.25 * amount)
    return c


# Shared colours
STEEL = pal("#a7adb5", "#d8dde2", "#7c828a", "#4e535a")
WRAPC = hx("#ddd7c9")
DARK_CLOTH = hx("#26232a")
GOLD = pal("#c9a03a", "#ecc860", "#94711f", "#5e470f")


def wraps(x, y):
    if (x + 2 * y) % 5 == 0:
        return tone(WRAPC, -0.7)
    return tone(WRAPC, 0.2 if y % 2 == 0 else -0.25)


# ---------------------------------------------------------------------------
# Forehead protectors (helmet): a steel plate on a cloth band
# ---------------------------------------------------------------------------

def headband(cloth_hex):
    cloth = pal(cloth_hex)
    cv = layer()

    def fn(face, x, y, W, H):
        if face in ("top", "bottom"):
            return None
        if face == "front" and 1 <= x <= 6 and 1 <= y <= 3:
            if (x, y) in ((1, 1), (6, 1), (1, 3), (6, 3)):
                return STEEL["d"]  # rivets
            if (x, y) in ((3, 2), (4, 2), (4, 1)):
                return STEEL["d"]  # engraved hook: our village mark
            if (x, y) == (3, 1):
                return STEEL["s"]
            if y == 1:
                return STEEL["h"]
            if y == 3:
                return STEEL["s"]
            return STEEL["b"] if x not in (1, 6) else tone(STEEL["b"], -0.4)
        if y == 2:
            return weave(cloth["h"] if face != "back" else cloth["b"], x, y, 3)
        if y == 3:
            return weave(cloth["s"], x, y, 4)
        if face == "back":
            if (x, y) in ((3, 4), (4, 4)):
                return cloth["b"]  # knot
            if (x, y) in ((3, 5), (4, 6), (3, 7)):
                return cloth["s"]  # tails
            if (x, y) in ((4, 5), (3, 6)):
                return cloth["d"]
        return None

    paint(cv, A["head"], fn)
    return cv


# ---------------------------------------------------------------------------
# Senju battle armour: red lacquered lamellar plates, laced in navy
# ---------------------------------------------------------------------------

RED = pal("#a8261e", "#d4483a", "#741812", "#44100c")
LACE = hx("#2a2f48")


def plate_row(y0, y, x, top_row_hi=True):
    """Lamellar: every 3 rows = highlight, plate, lacing."""
    pos = (y - y0) % 3
    if pos == 0:
        return RED["h"] if top_row_hi else RED["b"]
    if pos == 1:
        return RED["b"] if x % 4 else RED["s"]
    return LACE if x % 2 == 0 else RED["d"]


def senju():
    l1, l2 = layer(), layer()

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RED["h"] if 0 < x < W - 1 else RED["b"]
        if y == 0:
            if face == "front" and x in (3, 4):
                return GOLD["b"]
            return weave(DARK_CLOTH, x, y, 5)
        if y <= 8:
            c = plate_row(1, y, x)
            if face in ("front", "back") and x in (0, W - 1) and (y - 1) % 3 != 2:
                c = tone(c, -0.6)
            if face == "front" and y in (1, 2) and x in (3, 4):
                c = GOLD["h"] if y == 1 else GOLD["s"]  # chest cord
            return c
        # skirt flaps (kusazuri): gaps between the plates
        if face in ("front", "back") and x in (2, 5):
            return RED["d"]
        if face in ("right", "left") and x == 2:
            return RED["d"]
        if y == 9:
            return LACE if x % 2 == 0 else GOLD["s"]
        return RED["h"] if y == 10 else RED["s"]

    def arm(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RED["h"]
        if y <= 4 and face != "left":  # shoulder guard (sode), not on the inner side
            c = plate_row(0, y, x)
            return tone(c, -0.5) if y == 4 else c
        if y <= 7:
            return weave(DARK_CLOTH, x, y, 7)
        if y <= 10:  # bracer (kote)
            if y == 8:
                return GOLD["b"] if x % 2 else LACE
            if face == "left":
                return weave(DARK_CLOTH, x, y, 8)
            return RED["h"] if y == 9 else RED["s"]
        return None

    def boot(face, x, y, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return hx("#3a2f24")
        if y >= 10:  # black tabi
            if face == "front" and y == 11 and x == 1:
                return tone(DARK_CLOTH, -0.6)
            return weave(DARK_CLOTH, x, y, 9) if y == 10 else tone(DARK_CLOTH, -0.3)
        if y < 5:
            return None
        if face == "front":  # shin guard (suneate) with splints
            if y == 5:
                return GOLD["b"]
            if x in (0, 3):
                return RED["s"]
            return RED["h"] if x == 1 else RED["b"]
        if face == "back":
            if y >= 6:
                return LACE if (x + y) % 2 == 0 else DARK_CLOTH  # laced closed at the back
            return None
        if y >= 6:
            mx = mirror_side(face, x, W)
            if mx >= 2:
                return RED["s"] if y < 9 else RED["d"]
            return LACE if y % 2 else DARK_CLOTH
        return None

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)
    paint(l1, A["leg"], boot)

    def waist(face, x, y, W, H):
        if face in ("top", "bottom") or y < 9:
            return None
        if y == 9:
            return GOLD["s"]
        return weave(hx("#2b2a33"), x, y, 11) if not (face == "front" and x in (3, 4)) else RED["b"]

    def thigh(face, x, y, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return None
        cloth = hx("#2b2a33")
        if y <= 5 and face in ("front", "right", "left"):  # thigh guards (haidate): small plates
            if y == 0:
                return LACE
            if (x + y) % 2 == 0 and y % 2 == 1:
                return RED["d"]
            return RED["h"] if y % 2 == 1 else RED["b"]
        c = weave(cloth, x, y, 12)
        if y == 6:
            c = tone(c, -0.4)
        if face == "front" and x == 2 and y > 6:
            c = tone(c, -0.3)  # fold
        return c

    paint(l2, A["body"], waist)
    paint(l2, A["leg"], thigh)
    return l1, l2


# ---------------------------------------------------------------------------
# Uchiha high-collar tunic (navy, fan crest on the back) and dark pants with a rope belt
# ---------------------------------------------------------------------------

NAVY = pal("#273055", "#3a4677", "#1a2140", "#11162b")
FAN = [
    "..rRRr..",
    ".rRRRRr.",
    ".RRRRRR.",
    ".dddddd.",
    ".WWWWWW.",
    "..WWWw..",
    "...Hw...",
    "...Hw...",
]
FAN_C = {"r": hx("#8e1c20"), "R": hx("#c0282d"), "d": hx("#3a0e10"), "W": hx("#eeeae0"),
         "w": hx("#c9c4b8"), "H": hx("#6e6a62")}


def uchiha():
    l1, l2 = layer(), layer()

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return NAVY["h"]
        if y == 0:
            if face == "front" and x in (3, 4):
                return NAVY["d"]  # the collar opens in front
            return NAVY["h"]  # the high collar's rolled edge
        if face == "front" and y == 1 and x in (3, 4):
            return NAVY["s"]
        if face == "back" and 2 <= y <= 9:
            ch = FAN[y - 2][x]
            if ch != ".":
                return FAN_C[ch]
        if face in ("right", "left") and y >= 9 and x in (1, 2):
            return NAVY["d"] if x == 1 else None  # side slit
        c = weave(NAVY["b"], x, y, 21)
        if y == 11:
            c = NAVY["s"]
        return c

    def arm(face, x, y, W, H):
        if face == "bottom" or y > 3:
            return None
        if face == "top":
            return NAVY["h"]
        return NAVY["h"] if y == 3 else weave(NAVY["b"], x, y, 22)

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)

    pants = pal("#2a2c33", "#3a3d47", "#1d1f25", "#121317")
    rope = (hx("#5e3f86"), hx("#432c62"))

    def waist(face, x, y, W, H):
        if face in ("top", "bottom") or y < 9:
            return None
        if y == 9:
            return pants["b"]
        if face == "front" and x in (3, 4) and y == 11:
            return tone(rope[0], 0.6)  # knot ends
        return rope[(x + y) % 2]

    def leg(face, x, y, W, H):
        if face in ("top", "bottom"):
            return pants["b"] if face == "top" else None
        if 8 <= y <= 10:
            return wraps(x, y)
        if y == 11:
            return pants["s"]
        c = weave(pants["b"], x, y, 23)
        if face == "front" and y == 4 and x in (1, 2):
            c = pants["s"]
        return c

    paint(l2, A["body"], waist)
    paint(l2, A["leg"], leg)
    return l1, l2


# ---------------------------------------------------------------------------
# Hyuga robes: cream wrap with lavender trim and sash; pale lavender trousers
# ---------------------------------------------------------------------------

CREAM = pal("#e9e2d2", "#f7f3ea", "#c9c0ac", "#9c927e")
LAV = pal("#9a8cc4", "#b8acdc", "#6f639a", "#4a4170")
ROBE_FRONT = [  # c cream, L lavender trim, . open (undershirt shows)
    "cL....Lc",
    "ccL..Lcc",
    "cccLLccc",
    "ccccLccc",
    "cccccLcc",
    "cccccLcc",
    "cccccLcc",
]


def hyuga():
    l1, l2 = layer(), layer()

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return CREAM["h"] if 0 < x < W - 1 else LAV["b"]
        if y in (7, 8):  # obi sash
            if y == 7:
                return LAV["h"] if face != "back" else LAV["b"]
            return LAV["d"]
        if face == "front":
            if y < len(ROBE_FRONT):
                ch = ROBE_FRONT[y][x]
                if ch == ".":
                    return None
                if ch == "L":
                    return LAV["b"] if y > 0 else LAV["h"]
            if y >= 9 and x == 5:
                return CREAM["d"]  # the robe's overlap
        c = weave(CREAM["b"], x, y, 31, 0.8)
        if face in ("right", "left"):
            c = tone(c, -0.2)
        if y == 11:
            c = CREAM["s"]
        if face == "back" and x in (3, 4) and y < 7:
            c = tone(c, -0.15)
        return c

    def arm(face, x, y, W, H):
        if face == "bottom" or y > 10:
            return None
        if face == "top":
            return CREAM["h"]
        if y == 10:
            return LAV["b"]
        if y == 9:
            return LAV["h"]
        c = weave(CREAM["b"], x, y, 32, 0.8)
        return tone(c, -0.3) if face == "left" else c  # wide sleeve's inner fold

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)

    pants = pal("#cfc8dc", "#e2dcec", "#a9a1bf", "#7c7496")

    def waist(face, x, y, W, H):
        if face in ("top", "bottom") or y < 10:
            return None
        return LAV["d"] if y == 10 else pants["s"]

    def leg(face, x, y, W, H):
        if face == "top":
            return pants["b"]
        if face == "bottom":
            return None
        if y >= 10:
            return LAV["s"] if y == 10 else LAV["d"]  # bound cuffs
        c = weave(pants["b"], x, y, 33, 0.8)
        if face == "front" and x == 1 and y > 2:
            c = pants["s"]  # pleat
        if face in ("right", "left"):
            c = tone(c, -0.2)
        return c

    paint(l2, A["body"], waist)
    paint(l2, A["leg"], leg)
    return l1, l2


# ---------------------------------------------------------------------------
# Uzumaki coat: open red coat, orange trim and flame hem, spiral on the back
# ---------------------------------------------------------------------------

CRIMSON = pal("#b0281f", "#d8463a", "#7c1a14", "#4e100c")
ORANGE = pal("#e8782a", "#f5a04a", "#b65618", "#7a3610")
SPIRAL = [
    "..oooo..",
    ".o....o.",
    "o..oo..o",
    "o.o..o.o",
    "o.o.oo.o",
    "o.o....o",
    ".o.oooo.",
    "..o.....",
]


def uzumaki():
    l1 = layer()

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return ORANGE["b"] if x in (0, W - 1) else CRIMSON["h"]
        if face == "front":
            if x in (3, 4) and y >= 1:
                return None  # open coat
            if y == 0:
                return ORANGE["h"] if x not in (3, 4) else None
            if x in (2, 5):
                return ORANGE["b"] if y % 3 else ORANGE["h"]
        if y == 0:
            return ORANGE["h"]
        if face == "back" and 2 <= y <= 9 and SPIRAL[y - 2][x] == "o":
            return ORANGE["h"] if y < 5 else ORANGE["b"]
        if y == 11:
            return ORANGE["b"]
        if y == 10:
            return ORANGE["s"] if (x + (1 if face == "back" else 0)) % 3 == 0 else CRIMSON["s"]  # flame tips
        c = weave(CRIMSON["b"], x, y, 41)
        if face in ("right", "left"):
            c = tone(c, -0.2)
        return c

    def arm(face, x, y, W, H):
        if face == "bottom" or y > 9:
            return None
        if face == "top":
            return CRIMSON["h"]
        if y == 9:
            return ORANGE["b"]
        if y == 8:
            return ORANGE["s"] if x % 2 == 0 else CRIMSON["s"]
        return weave(CRIMSON["b"], x, y, 42)

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Flak vest: green, padded collar, scroll pouches, zip
# ---------------------------------------------------------------------------

GREEN = pal("#5d6b3a", "#7c8a50", "#414c28", "#2c341b")


def flak():
    l1 = layer()

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return GREEN["h"]
        if y == 0:
            if face == "front" and x in (3, 4):
                return GREEN["d"]
            return GREEN["h"]  # padded collar
        if face == "front":
            if x == 3:
                return GREEN["s"]  # zip
            if x == 4:
                return GREEN["h"] if y % 2 else GREEN["b"]
            if 2 <= y <= 6 and x in (0, 1, 2, 5, 6, 7):  # two scroll pouches each side
                if y == 2:
                    return GREEN["h"]
                if y == 6:
                    return GREEN["d"]
                if x in (1, 6):
                    return GREEN["s"]
                return GREEN["b"]
            if y == 8:
                return GREEN["s"]
        if face == "back":
            r = abs(x - 3.5) + abs(y - 4.5)
            if 1.5 <= r <= 2.5 and 2 <= y <= 7:
                return hx("#a8302a") if y < 5 else hx("#7e2420")  # red ring on the back
            if y == 1:
                return GREEN["s"]
        if face in ("right", "left") and y in (1, 2):
            return GREEN["d"]  # under the arm
        c = weave(GREEN["b"], x, y, 51)
        if y == 11:
            c = GREEN["s"]
        return c

    def arm(face, x, y, W, H):
        if face == "bottom" or face == "left" or y > 1:
            return None
        if face == "top":
            return GREEN["h"]
        return GREEN["b"] if y == 0 else GREEN["s"]

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# ANBU-style gear: porcelain mask (helmet) and a plated vest over a black suit
# ---------------------------------------------------------------------------

PORC = pal("#ecebe6", "#ffffff", "#c9c7c0", "#8e8b84")
MASK_RED = hx("#b02a2a")
STRAP = hx("#2a2a30")
SUIT = hx("#1e1f25")
PLATE = pal("#c3c6cc", "#e0e2e6", "#8a8f97", "#5c6068")


def anbu():
    mask, armor = layer(), layer()

    def head(face, x, y, W, H):
        if face == "front":
            if (x, y) in ((0, 0), (7, 0), (0, 7), (7, 7)):
                return None
            if y == 4 and x in (1, 2, 5, 6):
                return hx("#0c0c0e")  # eye holes
            if (x, y) in ((1, 3), (2, 2), (6, 3), (5, 2)):
                return MASK_RED  # brows
            if (x, y) in ((0, 5), (1, 6), (7, 5), (6, 6)):
                return MASK_RED  # cheek marks
            if (x, y) in ((3, 0), (4, 0), (3, 1), (4, 1)):
                return MASK_RED if y == 0 else tone(MASK_RED, -0.6)
            if y == 6 and x in (3, 4):
                return PORC["d"]
            if y == 5 and x in (3, 4):
                return PORC["h"]  # snout ridge
            if x in (0, 7) or y == 7:
                return PORC["s"]
            return PORC["b"]
        if face in ("right", "left"):
            mx = mirror_side(face, x, W)
            if mx == 7 and 1 <= y <= 6:
                return PORC["s"]  # mask edge seen from the side
            return STRAP if y == 3 else None
        if face == "back":
            if y == 3:
                return STRAP
            if (x, y) in ((3, 4), (4, 4)):
                return tone(STRAP, 0.5)
        return None

    paint(mask, A["head"], head)

    def body(face, x, y, W, H):
        if face == "bottom" or y >= 10:
            return None
        if face == "top":
            return PLATE["b"] if 1 <= x <= W - 2 else SUIT
        if face in ("front", "back") and 1 <= x <= 6 and 1 <= y <= 7:
            if face == "back" and (x + 1 == y or x + 2 == y):
                return hx("#5a4130") if x + 1 == y else hx("#3e2c20")  # sword strap
            if y == 1:
                return PLATE["h"]
            if y in (3, 5):
                return PLATE["s"]
            if y == 7:
                return PLATE["d"]
            return PLATE["b"] if x not in (1, 6) else tone(PLATE["b"], -0.4)
        if y == 9:
            return tone(SUIT, 0.8) if face == "front" and x in (3, 4) else tone(SUIT, 0.3)  # belt
        return weave(SUIT, x, y, 61)

    def arm(face, x, y, W, H):
        if face == "bottom" or y < 6 or y > 10:
            return None
        if face == "top":
            return None
        if y == 6:
            return PLATE["h"]
        if y == 10:
            return PLATE["d"]
        return PLATE["s"] if (face == "left" or x == 0) else PLATE["b"]

    paint(armor, A["body"], body)
    paint(armor, A["arm"], arm)
    return mask, armor


# ---------------------------------------------------------------------------
# Generic shinobi pants (layer 2) and sandals (layer 1 legs)
# ---------------------------------------------------------------------------

def shinobi():
    l1, l2 = layer(), layer()
    sandal = pal("#2b3a5a", "#3e5080", "#1d2740", "#121826")

    def boot(face, x, y, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return hx("#1a1a1e")
        if face == "front":
            if y == 10:
                return sandal["h"]
            if y == 11:
                return sandal["s"] if x in (0, 3) else None  # open toes
            return None
        if face == "back":
            if y == 8:
                return sandal["h"]
            return sandal["b"] if y > 8 else None
        mx = mirror_side(face, x, W)
        if y == 11:
            return sandal["d"]
        if y == 10:
            return sandal["b"] if mx < 3 else sandal["s"]
        if y == 9 and mx < 2:
            return sandal["h"]
        return None

    paint(l1, A["leg"], boot)

    pants = pal("#353a4c", "#4a5068", "#252937", "#171a24")

    def waist(face, x, y, W, H):
        if face in ("top", "bottom") or y < 9:
            return None
        if y == 10:
            if face == "front" and x in (3, 4):
                return STEEL["h"] if x == 3 else STEEL["s"]  # buckle
            return hx("#1e2028")
        return pants["b"] if y == 9 else pants["s"]

    def leg(face, x, y, W, H):
        if face == "top":
            return pants["b"]
        if face == "bottom" or y == 11:
            return None
        if 8 <= y <= 10:
            return wraps(x, y)
        c = weave(pants["b"], x, y, 71)
        if face == "front" and y == 5:
            c = pants["s"]  # knee seam
        if face == "right" and 1 <= y <= 3:  # thigh pocket (outer side)
            c = pants["h"] if y == 1 else (pants["s"] if x in (0, 3) else pants["b"])
        return c

    paint(l2, A["body"], waist)
    paint(l2, A["leg"], leg)
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
    """Flat front/back doll: skin (base + outer layer), then leggings, boots, chest, helmet."""
    def blit(cv, box, ox, oy, flip=False, face=None, grow=0):
        fx, fy, fw, fh = faces(box)[face or view]
        if grow:  # armour sits a little outside the body
            tmp = Canvas(fw * s, fh * s)
            tmp.blit(cv, fx, fy, fw, fh, 0, 0, s, flip)
            ow, oh = fw * s + 2 * grow, fh * s + 2 * grow
            for y in range(oh):
                for x in range(ow):
                    c = tmp.get(x * fw * s // ow, y * fh * s // oh)
                    if c[3]:
                        sheet.blend(dx + ox * s + x - grow, dy + oy * s + y - grow, c)
            return
        sheet.blit(cv, fx, fy, fw, fh, dx + ox * s, dy + oy * s, s, flip)

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


if __name__ == "__main__":
    main()

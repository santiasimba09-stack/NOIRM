#!/usr/bin/env python3
"""NOIRM v0.3 clan wear (docs/WAVE_V03.md, item 6): clan outfits, capes, hoods, ANBU masks, gowns and
the cloud cloak.

Writes, for every piece:
  * the armour layers (64x32 designs rendered at 4x by hd.py, "raw and tough" ink style);
  * the 3D drape textures (drape_art.py) and assets/noirm/gear/drapes.json (which item carries what);
  * masks "pushed aside" (the same mask moved to the side of the head);
  * the 16x16 icons, item models, crafting recipes and the names in the three lang files;
  * a preview sheet: docs/screenshots/gear-clan-wear.png.

All art is our own: the crests are our own shapes (a round paper fan, a whirlpool spiral, three
leaves, a storm cloud), never emblems traced from the series. Owner references are mood only.

Run: python3 tools/gear/clan_wear.py
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, ARMOR_PARTS, hx, tone, mix, noise  # noqa: E402
import gen_gear as G  # noqa: E402
import more_gear as M  # noqa: E402
import armor_wave as W  # noqa: E402
import drape_art as D  # noqa: E402
from hd import (Layer, paint_hd, pal, family, SCALE, in_circle, in_rrect, in_poly, on_polyline,  # noqa: E402
                side, cross_collar, spiral)

A = ARMOR_PARTS
ROOT = G.ROOT
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")
DATA = os.path.join(ROOT, "src/main/resources/data/noirm")
DRAPE_DIR = os.path.join(ASSETS, "textures/models/drape")
PREVIEW = os.path.join(ROOT, "docs/screenshots/gear-clan-wear.png")

INK, BONE, ASH = W.INK, W.BONE, W.ASH
STEEL, STRAP, HOLE, WRAPC = G.STEEL, G.STRAP, G.HOLE, G.WRAPC
BLOOD = M.BLOOD


def layer():
    return Layer()


# ---------------------------------------------------------------------------
# Crests on the armour layers (base-pixel units)
# ---------------------------------------------------------------------------

def fan_at(u, v, cx, cy, r, red, white, ink):
    return D.fan_crest((u - cx) / r, (v - cy) / r, r, red, white, ink)


def swirl_at(u, v, cx, cy, r, col):
    return D.swirl_crest((u - cx) / r, (v - cy) / r, col, None)


def leaf_at(u, v, cx, cy, r, col):
    return D.leaf_crest((u - cx) / r, (v - cy) / r, col)


def cloud_at(u, v, cx, cy, r, red, white):
    return D.cloud_mark((u - cx) / r, (v - cy) / r, red, white)


# ---------------------------------------------------------------------------
# Builders
# ---------------------------------------------------------------------------

def robe(P, T, S, collar="cross", sleeves="wrist", crest=None, under=None, open_v=None, hem=True, cuffs=True):
    """A robe top. P robe, T trim, S sash (palettes). collar: "cross" (wrapped), "high" (a stand collar
    and a buttoned front), "none". sleeves: "wrist", "elbow", "none", "wide" (to the wrist; the 3D
    sleeves hang under them). crest(face, u, v) -> colour or None (drawn after shading)."""
    l1 = layer()
    under_c = under or INK["b"]

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["b"]
        if S is not None and 7.7 <= v < 9.1:
            return S["s"] if abs(v - 8.4) < 0.12 else S["b"]
        if face == "front":
            if collar == "cross":
                c = cross_collar(u, v, 0.8)
                if c == "open":
                    return under_c
                if c == "trim":
                    return T["b"]
                if c == "edge":
                    return P["d"]
            elif collar == "high":
                if v < 1.0:
                    return T["b"]
                if abs(u - 4.0) < 0.14:
                    return P["d"]
        if hem and v >= 11.3:
            return T["b"]
        return P["b"]

    def arm(face, u, v, Wd, H):
        if face == "bottom":
            return None
        limit = {"wrist": 11.6, "wide": 12.0, "elbow": 6.2, "none": -1}[sleeves]
        if v > limit:
            return None
        if face == "top":
            return P["b"]
        if cuffs and limit - v < 0.8:
            return T["b"]
        return tone(P["b"], -0.3) if face == "left" else P["b"]

    paint_hd(l1, A["body"], body)
    if sleeves != "none":
        paint_hd(l1, A["arm"], arm)
    if crest:
        l1.overlay(A["body"], crest, "final")
    return l1


def hem_legs(P, T, slit=False):
    """Layer 2 under a gown's 3D skirt: the robe's lower part on the hips and plain cloth legs."""
    l2 = layer()

    def body(face, u, v, Wd, H):
        if face in ("top", "bottom") or v < 7.5:
            return None
        return P["b"]

    def leg(face, u, v, Wd, H):
        if face == "top":
            return None
        if face == "bottom":
            return P["d"]
        if v >= 11.2:
            return T["b"]
        if slit and face == "front" and abs(u - 2.0) < 0.1:
            return P["d"]
        return tone(P["b"], -0.25) if face in ("left", "right") else P["b"]

    paint_hd(l2, A["body"], body)
    paint_hd(l2, A["leg"], leg)
    return l2


def plate_armour(LAC, CORD, UNDER, crest=None, bracer=True, collar_c=None):
    """Warring-states armour: lamellar chest plates over a dark under-robe, laced bracers; the shoulder
    plates and the thigh plates are 3D drapes."""
    l1 = layer()
    family([CORD], "cloth")

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "top":
            return LAC["b"] if 0.8 < u < Wd - 0.8 else UNDER["b"]
        if v >= 8.9:
            if 8.9 <= v < 9.7:
                return CORD  # the cord belt
            return UNDER["b"]
        if face in ("front", "back"):
            if v < 0.7:
                return collar_c or UNDER["s"]
            r = (v - 0.7) / 1.65
            f = r - math.floor(r)
            if f > 0.8:
                return CORD
            j = math.floor(u / 1.0 + (math.floor(r) % 2) * 0.5)
            return LAC["b"] if j % 2 == 0 else LAC["s"]
        # sides: laced panels
        if abs(u - 2.0) < 0.15:
            return CORD
        return LAC["s"]

    def arm(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "top":
            return UNDER["b"]
        if bracer and 6.6 <= v < 11.2:
            if 6.6 <= v < 7.0 or 10.8 <= v < 11.2:
                return CORD
            return LAC["b"] if face != "left" else LAC["s"]
        if v > 11.6:
            return None
        return UNDER["b"]

    def marks(face, u, v, Wd, H):
        if face in ("front", "back") and 0.7 < v < 8.9:
            r = (v - 0.7) / 1.65
            f = r - math.floor(r)
            if f < 0.1:
                return tone(LAC["h"], 0.5)  # the lacquer's glossy top edge
        if crest:
            return crest(face, u, v, Wd, H)
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


def plate_legs(UNDER, CORD):
    """Layer 2 under the 3D thigh plates: dark wide trousers bound at the shin."""
    l2 = layer()

    def body(face, u, v, Wd, H):
        if face in ("top", "bottom") or v < 8.0:
            return None
        return CORD if v < 8.8 else UNDER["b"]

    def leg(face, u, v, Wd, H):
        if face == "top":
            return None
        if face == "bottom":
            return UNDER["d"]
        if v >= 8.5:
            return WRAPC if (v * 2) % 1.0 > 0.15 else tone(WRAPC, -1)
        return UNDER["b"] if face != "left" else UNDER["s"]

    paint_hd(l2, A["body"], body)
    paint_hd(l2, A["leg"], leg)
    return l2


def vest(P, UNDER, POUCH, crest=None, collar=True, one_shoulder=False, sleeves="elbow"):
    """A jōnin-style vest over a dark undershirt: scroll pouches on the chest, a high collar, a crest on the back."""
    l1 = layer()

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["b"]
        if one_shoulder and face == "front" and u < 4 - (v - 0.0) * 0.0 and v < 3.0 and u < 3.2 - v:
            return UNDER["b"]
        if collar and v < 0.9:
            return P["h"]
        if v >= 10.6:
            return P["s"] if v < 11.4 else UNDER["b"]
        if face == "front":
            if abs(u - 4.0) < 0.12:
                return P["d"]  # the zip
            for x0 in (0.7, 2.25, 4.45, 6.0):
                if in_rrect(u, v, x0, 4.0, x0 + 1.35, 7.2, 0.25):
                    return POUCH["b"] if (v - 4.0) > 0.7 else POUCH["h"]
        return P["b"]

    def arm(face, u, v, Wd, H):
        if face == "bottom":
            return None
        limit = 5.8 if sleeves == "elbow" else 11.6
        if v > limit:
            return None
        if face == "top":
            return UNDER["b"]
        return UNDER["b"] if face != "left" else UNDER["s"]

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)
    if crest:
        l1.overlay(A["body"], crest, "final")
    return l1


def coat(P, T, BTN, belt=None, crest=None, sleeve_trim=None):
    """A long coat closed down the front (the 3D collar, tails and sleeves do the rest)."""
    l1 = layer()

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["b"]
        if belt and 8.2 <= v < 9.2:
            return belt["b"]
        if face == "front":
            if abs(u - 4.6) < 0.16:
                return P["d"]  # the overlapping front edge
            if 4.6 < u < 5.4:
                return P["s"]
        if face in ("right", "left") and abs(u - 2.0) < 0.12:
            return P["s"]
        return P["b"]

    def arm(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["b"]
        if sleeve_trim and v > 10.8:
            return sleeve_trim
        return tone(P["b"], -0.3) if face == "left" else P["b"]

    def marks(face, u, v, Wd, H):
        if face == "front" and 4.75 < u < 5.25:
            for y in (1.6, 3.6, 5.6, 7.6, 10.0):
                if in_circle(u, v, 5.0, y, 0.2):
                    return BTN
        if crest:
            return crest(face, u, v, Wd, H)
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


def straps(STR, CLASP, cloak_front=None):
    """A cape worn on its own: straps over the shoulders and a clasp at the collarbone.
    cloak_front: a palette for a cloak that also closes over the chest (the hooded cloak)."""
    l1 = layer()

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if cloak_front is not None:
            if face == "top":
                return cloak_front["b"]
            if face == "front" and abs(u - 4.0) < 0.15:
                return cloak_front["d"]
            if face == "front" and in_circle(u, v, 4.0, 1.4, 0.55):
                return CLASP
            return cloak_front["b"] if face != "back" else cloak_front["s"]
        if face == "top":
            return STR if 1.0 < u < 2.2 or 5.8 < u < 7.0 else None
        if face == "front":
            if in_circle(u, v, 4.0, 1.6, 0.7):
                return CLASP
            if on_polyline(u, v, [(1.6, 0.0), (3.6, 1.6)], 0.7) or on_polyline(u, v, [(6.4, 0.0), (4.4, 1.6)], 0.7):
                return STR
        if face == "back" and v < 0.9 and 0.6 < u < 7.4:
            return STR  # the cape's top edge folded over
        return None

    def arm(face, u, v, Wd, H):
        if cloak_front is None or face == "bottom":
            return None
        if face == "top":
            return cloak_front["b"]
        return cloak_front["b"] if v < 11.4 else None

    paint_hd(l1, A["body"], body)
    if cloak_front is not None:
        paint_hd(l1, A["arm"], arm)
    return l1


def head_piece(fn):
    l1 = layer()
    paint_hd(l1, A["head"], fn)
    return l1


def glasses_fn(LENS, FRAME, hood_shadow=None):
    """Round dark glasses across the eyes (and, under a hood, the shadow of its brim)."""
    def head(face, u, v, Wd, H):
        if face == "front":
            for cx in (2.35, 5.65):
                if in_circle(u, v, cx, 4.3, 1.15):
                    return FRAME if not in_circle(u, v, cx, 4.3, 0.95) else LENS
            if 3.5 < u < 4.5 and abs(v - 4.0) < 0.14:
                return FRAME  # the bridge
            if hood_shadow is not None and v < 2.2 - 0.6 * abs(u - 4) / 4:
                return hood_shadow
            return None
        if face in ("right", "left") and abs(v - 4.1) < 0.14 and side(face, u, Wd) > 3.2:
            return FRAME  # the arms of the glasses
        return None
    return head


# ---------------------------------------------------------------------------
# Palettes
# ---------------------------------------------------------------------------

NAVY = pal("#1f2436", "#323a54", "#151927", "#0b0d15")
FAN_RED = hx("#b8262c")
FAN_WHITE = hx("#ece6da")
family([FAN_RED, FAN_WHITE], "print")
WARMER = pal("#9a9890", "#c2bfb5", "#77756e", "#4e4d48")
CRIMSON = pal("#7c1820", "#a52a30", "#58101a", "#33080c", mat="lacquer")
BRICK = pal("#8a2c1c", "#b0442c", "#621c12", "#3a0f08", mat="lacquer")
BLACK_CORD = hx("#1b1a1f")
TAN_CORD = hx("#b08a4a")
NAVY_CORD = hx("#263457")
UZU_RED = pal("#8e2a1e", "#b0402c", "#661c14", "#3c0f0a")
UZU_VEST = pal("#5f2a1c", "#7d3a28", "#441d13", "#28100a")
CREAM = pal("#ddd3bc", "#efe7d4", "#bdb39c", "#8a8270")
ABU_PALE = pal("#b8b2a2", "#d4cfc1", "#948e80", "#66615a")
ABU_DARK = pal("#3b4038", "#525a4e", "#2a2e28", "#191b17")
ABU_SASH = pal("#8a1c22", "#ad2c30", "#601219", "#3a0a0e")
LENS = hx("#0d0e12")
family([LENS], "void")
FRAME = hx("#2a2a30")
MOSS = pal("#3c4a32", "#52624a", "#2a3424", "#181e14")
EARTH = pal("#5a4030", "#745442", "#402c20", "#261a12")
LILAC = pal("#d9d4e4", "#ece9f2", "#b6b0c4", "#827c92")
LILAC_T = pal("#8e86a6", "#aaa2c0", "#6e6886", "#4a4660")
OLIVE = pal("#4d5a34", "#667648", "#384226", "#212817")
AKI_RED = pal("#8e2622", "#b03a32", "#661a18", "#3c0e0c")
FUR_COAT = pal("#4b4440", "#625a54", "#36302d", "#211d1b")
KAG_WHITE = pal("#e4e0d6", "#f6f3ec", "#c2bdb2", "#8c877e")
KAG_RED = pal("#9a2026", "#c03036", "#6e161c", "#400c10")
SEA = pal("#3a4a56", "#506472", "#28343e", "#161e24")
PURPLE = pal("#5a3a6a", "#74508a", "#40284c", "#24162c")
GREEN = pal("#3f7a2a", "#5a9a3e", "#2c5a1e", "#173210")
ORANGE = pal("#d27a1e", "#ee9a3a", "#a65a10", "#6a3808")
SAND = pal("#b58a52", "#cfa66c", "#8e6a3c", "#5c4424")
ROOT_BLACK = pal("#1a1a1f", "#2c2c34", "#121216", "#08080a")
BLOODROBE = pal("#4e1418", "#6a2026", "#380e10", "#200608")
CLOUD_WHITE = pal("#d8d6d0", "#f0eee8", "#b4b2ac", "#7c7a76")
CLOAK_BLACK = pal("#16161b", "#26262e", "#0e0e12", "#060608")
CLOUD_RED = hx("#b0242a")
family([CLOUD_RED], "print")
TRAVEL = pal("#5c4a36", "#76604a", "#433526", "#281f16")
GOLDC = M.GOLDC
WHITE = M.WHITE
ANBU_GREY = pal("#7e8288", "#a4a8ae", "#5e6268", "#3c3f44", mat="metal")
TATTOO = hx("#a3242a")
family([TATTOO], "print")
GOWN_RED = pal("#a42a2c", "#c43c3c", "#7a1c20", "#4a0e12")
MEDIC = pal("#dfe6dc", "#f1f5ef", "#bdc6ba", "#87907f")
MEDIC_T = pal("#4e7a4a", "#6a9a62", "#3a5c36", "#22381f")


# ---------------------------------------------------------------------------
# The armour layers
# ---------------------------------------------------------------------------

def uchiha_collar_shirt():
    def crest(face, u, v, Wd, H):
        if face == "back":
            return fan_at(u, v, 4.0, 3.9, 2.1, FAN_RED, FAN_WHITE, INK["d"])
        return None
    l1 = robe(NAVY, NAVY, None, collar="high", sleeves="elbow", crest=crest, hem=False, cuffs=False)

    def warmers(face, u, v, Wd, H):  # arm warmers from below the elbow to the knuckles
        if face in ("top", "bottom") or v < 6.4 or v > 11.8:
            return None
        if (v - 6.4) % 1.35 < 0.18:
            return WARMER["s"]
        return WARMER["b"] if face != "left" else WARMER["s"]
    l1.overlay(A["arm"], warmers, "design")
    return l1


def uchiha_war():
    def crest(face, u, v, Wd, H):
        if face == "back":
            return fan_at(u, v, 4.0, 4.4, 1.7, FAN_RED, FAN_WHITE, INK["d"])
        return None
    return plate_armour(CRIMSON, BLACK_CORD, INK, crest=crest), plate_legs(INK, BLACK_CORD)


def senju_plates():
    def crest(face, u, v, Wd, H):
        if face == "back" and in_circle(u, v, 4.0, 4.4, 1.9):
            if not in_circle(u, v, 4.0, 4.4, 1.6):
                return TAN_CORD
            return leaf_at(u, v, 4.0, 4.4, 1.5, TAN_CORD)
        return None
    return plate_armour(BRICK, NAVY_CORD, MOSS, crest=crest, collar_c=MOSS["s"]), plate_legs(MOSS, NAVY_CORD)


def senju_robe():
    def crest(face, u, v, Wd, H):
        if face == "back":
            return leaf_at(u, v, 4.0, 3.6, 1.6, CREAM["b"])
        return None
    return robe(MOSS, CREAM, EARTH, crest=crest)


def uzumaki_vest():
    def crest(face, u, v, Wd, H):
        if face == "back":
            return swirl_at(u, v, 4.0, 4.6, 2.3, UZU_RED["h"])
        if face == "front" and 0.8 < v < 2.6:
            return swirl_at(u, v, 2.0, 1.9, 0.75, UZU_RED["h"])
        return None
    return vest(UZU_VEST, INK, pal("#6a3424", "#8a4834", "#4c2418", "#2c140c"), crest=crest)


def uzushio_robe():
    def crest(face, u, v, Wd, H):
        if face == "back":
            return swirl_at(u, v, 4.0, 3.8, 2.4, CREAM["b"])
        return None
    return robe(UZU_RED, CREAM, INK, crest=crest, sleeves="wide"), hem_legs(UZU_RED, CREAM)


def aburame_coat(P, sash=None, sleeve_trim=None):
    return coat(P, P, tone(P["d"], -1), belt=sash, sleeve_trim=sleeve_trim)


def hyuga_gown():
    return robe(LILAC, LILAC_T, LILAC_T, sleeves="wide"), hem_legs(LILAC, LILAC_T)


def nara_vest():
    def crest(face, u, v, Wd, H):
        if face == "back" and in_circle(u, v, 4.0, 4.6, 1.8):
            if not in_circle(u, v, 4.0, 4.6, 1.5):
                return OLIVE["d"]
            # our own antler mark: a fork with two tines
            if on_polyline(u, v, [(4.0, 5.9), (4.0, 4.0), (3.0, 3.3), (2.9, 3.0)], 0.32) or \
                    on_polyline(u, v, [(4.0, 4.4), (5.0, 3.4), (5.1, 3.0)], 0.32) or on_polyline(u, v, [(3.5, 3.65), (3.6, 3.0)], 0.28):
                return OLIVE["d"]
        return None
    return vest(OLIVE, INK, pal("#5a6840"), crest=crest)


def akimichi_armour():
    def crest(face, u, v, Wd, H):
        if face == "front":
            for cx in (2.2, 5.8):
                c = swirl_at(u, v, cx, 3.0, 1.1, BONE["h"])
                if c:
                    return c
        if face == "back":
            return swirl_at(u, v, 4.0, 4.4, 2.0, BONE["h"])
        return None
    return plate_armour(AKI_RED, BLACK_CORD, INK, crest=crest, bracer=False)


def inuzuka_coat():
    return coat(FUR_COAT, FUR_COAT, hx("#1a1714"), belt=EARTH, sleeve_trim=hx("#cfc6b6"))


def kaguya_robe():
    return robe(KAG_WHITE, KAG_RED, pal("#4a3c5a"), sleeves="wrist"), hem_legs(KAG_WHITE, KAG_RED)


def hoshigaki_cloak():
    return straps(STRAP, STEEL["b"], cloak_front=SEA)


def yamanaka_wrap():
    l1 = robe(PURPLE, PURPLE, pal("#2c2c34"), collar="none", sleeves="none", hem=False)

    def wraps(face, u, v, Wd, H):
        if face in ("top", "bottom") or v < 4.0 or v > 11.6:
            return None
        return WRAPC if (v * 1.6) % 1.0 > 0.12 else tone(WRAPC, -1.2)
    l1.overlay(A["arm"], wraps, "design")
    return l1


def might_suit():
    l1 = robe(GREEN, GREEN, None, collar="high", sleeves="wrist", hem=False, cuffs=False)

    def vest_marks(face, u, v, Wd, H):  # a zipped front and a belt
        if face == "front" and abs(u - 4.0) < 0.1 and v < 8:
            return GREEN["d"]
        if 8.4 <= v < 9.2 and face != "top":
            return hx("#d6c09a") if not (face == "front" and abs(u - 4) < 0.6) else STEEL["b"]
        return None
    l1.overlay(A["body"], vest_marks, "final")
    l2 = hem_legs(GREEN, GREEN)
    feet = layer()

    def warmers(face, u, v, Wd, H):  # orange leg warmers over the shins, dark sandals
        if face == "top":
            return None
        if face == "bottom":
            return hx("#2a2622")
        if v >= 10.6:
            return hx("#2a2622")
        if v < 4.5:
            return None
        if (v - 4.5) % 1.5 < 0.2:
            return ORANGE["s"]
        return ORANGE["b"] if face != "left" else ORANGE["s"]
    paint_hd(feet, A["leg"], warmers)
    return l1, l2, feet


def sand_gourd():
    l1 = layer()
    SASH = pal("#3e3228", "#56463a", "#2c231c", "#1a140f")

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "front" and on_polyline(u, v, [(6.8, 0.0), (1.0, 10.0)], 1.3):
            return SASH["b"]
        if face == "back" and on_polyline(u, v, [(1.2, 0.0), (7.0, 10.0)], 1.3):
            return SASH["b"]
        if face == "top" and 5.5 < u < 7.2:
            return SASH["b"]
        if face in ("right",) and 6 < v < 9:
            return SASH["b"]
        return None
    paint_hd(l1, A["body"], body)
    return l1


def root_robe():
    l1 = robe(ROOT_BLACK, pal("#d8d3c8"), pal("#3a3a42"), sleeves="wrist")

    def bandage(face, u, v, Wd, H):  # one arm bound in bandages under the robe
        if face in ("top", "bottom") or v < 6.5:
            return None
        return WRAPC if (v * 1.8) % 1.0 > 0.14 else tone(WRAPC, -1.3)
    l1.overlay(A["arm"], bandage, "design")
    return l1


def chinoike_robe():
    return robe(BLOODROBE, INK, INK, sleeves="wrist")


def yotsuki_harness():
    return vest(CLOUD_WHITE, INK, pal("#a8a6a0"), collar=False, one_shoulder=True, sleeves="none")


def cloud_cloak():
    """The long black cloak with red clouds outlined in white (the collar and the long skirt are 3D)."""
    l1 = layer()

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "front" and abs(u - 4.0) < 0.12:
            return CLOAK_BLACK["d"]
        return CLOAK_BLACK["b"]

    def arm(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if v > 11.4:
            return CLOAK_BLACK["s"]
        return CLOAK_BLACK["b"] if face != "left" else CLOAK_BLACK["s"]

    def clouds(face, u, v, Wd, H):
        if face == "front":
            return cloud_at(u, v, 2.2, 7.2, 1.4, CLOUD_RED, FAN_WHITE)
        if face == "back":
            return cloud_at(u, v, 5.2, 2.8, 1.5, CLOUD_RED, FAN_WHITE) or cloud_at(u, v, 2.6, 8.2, 1.3, CLOUD_RED, FAN_WHITE)
        return None

    def arm_cloud(face, u, v, Wd, H):
        if face == "right":
            return cloud_at(u, v, 2.0, 3.0, 1.2, CLOUD_RED, FAN_WHITE)
        return None
    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], clouds, "final")
    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], arm_cloud, "final")
    return l1


def anbu_uniform():
    """The uniform: a grey plated vest over a black sleeveless shirt, steel arm guards, and the spiral
    mark on the bare left upper arm (the arm itself shows through, only the mark is drawn)."""
    l1 = layer()
    SUIT = INK

    def body(face, u, v, Wd, H):
        if face == "bottom":
            return None
        if face == "top":
            return ANBU_GREY["b"] if 1 <= u <= Wd - 1 else SUIT["b"]
        if face in ("front", "back") and in_rrect(u, v, 0.7, 0.8, 7.3, 8.0, 0.6):
            return ANBU_GREY["s"] if 3.4 <= v < 5.6 else ANBU_GREY["b"]
        if 9.0 <= v < 9.8:
            return STRAP
        return SUIT["b"]

    def arm(face, u, v, Wd, H):
        if face in ("bottom", "top"):
            return None
        if 6.3 <= v < 10.8:
            if 6.3 <= v < 6.7 or 10.4 <= v < 10.8:
                return STRAP
            return STEEL["b"] if face != "left" else STEEL["s"]
        if v < 6.3 and face == "front":
            return None
        return None

    def tattoo(face, u, v, Wd, H):  # a red spiral on the outside of the upper arm
        if face in ("right", "front") and 0.6 < v < 5.4:
            cx, cy = (2.0, 2.9) if face == "right" else (2.0, 2.9)
            pts = spiral(cx, cy, 0.15, 1.5, 1.6, 0.4)
            if on_polyline(u, v, pts, 0.32):
                return TATTOO
        return None

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], tattoo, "final")
    l2 = layer()

    def pbody(face, u, v, Wd, H):
        if face in ("top", "bottom") or v < 8.0:
            return None
        return STRAP if v < 8.8 else SUIT["b"]

    def pleg(face, u, v, Wd, H):
        if face == "top":
            return None
        if face == "bottom":
            return SUIT["d"]
        if v >= 8.6:
            return WRAPC if (v * 2.2) % 1.0 > 0.15 else tone(WRAPC, -1)
        return SUIT["b"] if face != "left" else SUIT["s"]
    paint_hd(l2, A["body"], pbody)
    paint_hd(l2, A["leg"], pleg)
    return l1, l2


def kage_hem():
    return hem_legs(WHITE, pal("#c49a3c"))


def priestess():
    l1 = robe(WHITE, GOWN_RED, GOWN_RED, sleeves="wide")
    return l1, hem_legs(GOWN_RED, GOWN_RED)


def medic_gown():
    def crest(face, u, v, Wd, H):
        if face == "back":
            if (abs(u - 4.0) < 0.5 and 2.4 < v < 5.6) or (abs(v - 4.0) < 0.5 and 2.4 < u < 5.6):
                return MEDIC_T["b"]
        return None
    return robe(MEDIC, MEDIC_T, MEDIC_T, sleeves="wrist", crest=crest), hem_legs(MEDIC, MEDIC_T)


# ---------------------------------------------------------------------------
# Masks: four more animal masks and the hunter mask (8x8 front maps, as more_gear.ANIMAL_MASKS)
# ---------------------------------------------------------------------------

NEW_MASKS = {
    "mask_cat": ([
        "r......r",
        "wr....rw",
        "wwwwwwww",
        "wrwwwwrw",
        "weewweew",
        "wwwkkwww",
        "rrwkkwrr",
        ".wwwwww.",
    ], {"r": "#7a3a8a", "k": "#1c1c22"}),
    "mask_fox": ([
        "rr....rr",
        "wrr..rrw",
        "wwwwwwww",
        "wrrwwrrw",
        "weewweew",
        "wwrwwrww",
        ".wwkkww.",
        "...kk...",
    ], {"r": "#c0282d", "k": "#1c1c22"}),
    "mask_dog": ([
        "k......k",
        "kwwwwwwk",
        "wwrwwrww",
        "wrwwwwrw",
        "weewweew",
        "wwwkkwww",
        "wwkkkkww",
        ".wwrrww.",
    ], {"r": "#3a5a8a", "k": "#2a241e"}),
    "mask_bird": ([
        "..rrrr..",
        ".wwrrww.",
        "wwwwwwww",
        "wkwwwwkw",
        "weewweew",
        "wwwggwww",
        ".wwggww.",
        "..wggw..",
    ], {"r": "#2a6a5a", "k": "#1c1c22", "g": "#d8a030"}),
    "hunter_mask": ([
        "wwwwwwww",
        "wwwrrwww",
        "wwwwwwww",
        "wwwwwwww",
        "weewweew",
        "wwwwwwww",
        "wrwrwrww",
        ".wwwwww.",
    ], {"r": "#3a6a9a"}),
}


def aside(path_in, path_out):
    """The same mask pushed to the side of the head: its face moved onto the head's right side
    (a little smaller), only the strap left across the face."""
    import struct
    import zlib
    src = read_png(path_in)
    S = SCALE
    w, h = len(src[0]), len(src)
    out = [[(0, 0, 0, 0)] * w for _ in range(h)]
    # keep the strap (top/back/sides of the head) from the original, drop the front face
    for y in range(h):
        for x in range(w):
            fx, fy = x // S, y // S
            if 8 <= fx < 16 and 8 <= fy < 16:
                continue  # the front: drawn below
            if 0 <= fy < 16 and fx < 32:
                out[y][x] = src[y][x]
    # the strap runs across the face at the eyes' height
    strap = (42, 42, 48, 255)
    for y in range(int(10.9 * S), int(11.65 * S)):
        for x in range(8 * S, 16 * S):
            out[y][x] = strap
    # the face, 85% size, onto the right side face (0..8, 8..16), tilted back a little
    k = 0.85
    for y in range(8 * S):
        for x in range(8 * S):
            # target (x, y) on the side face -> source on the front face
            u = (x - 0.6 * S) / k
            v = (y - 0.5 * S) / k + (x / (8 * S)) * 0.3 * S
            if 0 <= u < 8 * S and 0 <= v < 8 * S:
                c = src[8 * S + int(v)][8 * S + int(u)]
                if c[3]:
                    out[8 * S + y][x] = c
    write_png(path_out, out)


def read_png(path):
    import zlib
    import struct
    data = open(path, "rb").read()
    pos = 8
    w = h = 0
    idat = b""
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
            assert depth == 8 and ctype == 6, "RGBA PNG expected"
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    rows = []
    stride = w * 4
    prev = bytearray(stride)
    i = 0
    for _ in range(h):
        f = raw[i]
        i += 1
        line = bytearray(raw[i:i + stride])
        i += stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([tuple(line[x:x + 4]) for x in range(0, stride, 4)])
        prev = line
    return rows


def write_png(path, rows):
    cv = Canvas(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            cv.set(x, y, c)
    cv.save(path)

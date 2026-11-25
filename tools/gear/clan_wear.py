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
        if face == "right" and 0.6 < v < 5.4:
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


# ---------------------------------------------------------------------------
# Drape textures (drape_art.py) and which item carries which drape
# ---------------------------------------------------------------------------

def drape_textures():
    t = {}
    cape_aspect = 11.0 / 19.0
    t["cape_plain"] = D.cape("#cfcac0", "#b8b2a6", 21, wear=0.6)  # dyed by the item's colour
    t["kage_cape"] = D.cape("#e8e4da", "#a8262c", 22, trim=("#a8262c", 0.86), top_trim=("#c49a3c", 0.045), wear=0.3)
    t["travel_cape"] = D.cape("#6a5642", "#8c7a62", 23, hem="fringe", mottle=0.35, wear=1.2)
    t["rogue_cape"] = D.cape("#2a2a2e", "#1c1c20", 24, hem="torn", mottle=0.4, wear=1.4)
    t["uchiha_cape"] = D.cape("#1f2436", "#151927", 25, crest=D.centred(0.5, 0.3, 0.13, cape_aspect,
                                                                       lambda x, y: D.fan_crest(x, y, 1, FAN_RED, FAN_WHITE, (20, 18, 24))))
    t["senju_cape"] = D.cape("#2f3a28", "#1e2618", 26, crest=D.centred(0.5, 0.3, 0.12, cape_aspect,
                                                                      lambda x, y: (TAN_CORD if 0.86 < math.hypot(x, y) <= 1.0 else
                                                                                    D.leaf_crest(x * 1.15, y * 1.15, TAN_CORD))),
                             trim=("#8a2c1c", 0.9))
    t["uzumaki_cape"] = D.cape("#7e2418", "#3c0f0a", 27, crest=D.centred(0.5, 0.32, 0.14, cape_aspect,
                                                                        lambda x, y: D.swirl_crest(x, y, hx("#e0b070"), None)))
    t["hoshigaki_cape"] = D.cape("#3a4a56", "#22303a", 28, hem="teeth", wear=0.9)
    t["hooded_cloak_cape"] = D.cape("#5c4a36", "#3e3226", 29, mottle=0.3, wear=1.1)

    def clouds(u, v):
        for cx, cy, r in ((0.28, 0.22, 0.09), (0.72, 0.52, 0.1), (0.3, 0.78, 0.09)):
            m = D.cloud_mark((u - cx) * (32 / 14.0) / r, (v - cy) / r, CLOUD_RED, FAN_WHITE)
            if m:
                return m
        return None

    def ring_clouds(u, v):
        for cx, cy in ((0.18, 0.35), (0.4, 0.7), (0.62, 0.3), (0.88, 0.62), (0.08, 0.78)):
            m = D.cloud_mark((u - cx) * 32 / 14.0 / 0.11, (v - cy) / 0.16, CLOUD_RED, FAN_WHITE)
            if m:
                return m
        return None
    t["cloud_skirt"] = D.ring("#16161b", "#6e1a1e", 31, front_edge="#08080a", marks=ring_clouds, wear=0.6)
    t["cloud_collar"] = D.ring("#16161b", "#6e1a1e", 32, w=128, h=32, folds=4, wear=0.2)
    # robes and gowns (skirts, and the same cloth for wide sleeves)
    t["uzushio_skirt"] = D.ring("#8e2a1e", "#5a1a12", 33, hem_trim=("#ddd3bc", 0.9))
    t["hyuga_skirt"] = D.ring("#d9d4e4", "#b6b0c4", 34, hem_trim=("#8e86a6", 0.92), wear=0.5)
    t["kaguya_skirt"] = D.ring("#e4e0d6", "#c2bdb2", 35, hem_trim=("#9a2026", 0.9), wear=0.6)
    t["kage_skirt"] = D.ring("#eeeae2", "#cfc9bd", 36, hem_trim=("#c49a3c", 0.9),
                             stripes=[("#8e1c20", 0.93, 0.95)], wear=0.4)
    t["priestess_skirt"] = D.ring("#a42a2c", "#7a1c20", 37, folds=14, wear=0.6)
    t["priestess_sleeve"] = D.ring("#eeeae2", "#cfc9bd", 38, hem_trim=("#a42a2c", 0.9), wear=0.3)
    t["medic_skirt"] = D.ring("#dfe6dc", "#bdc6ba", 39, hem_trim=("#4e7a4a", 0.92), wear=0.7)
    t["formal_skirt"] = D.ring("#17161b", "#2c2b33", 40, hem_trim=("#55545c", 0.93), wear=0.5)
    for name, (robe_hex, trim_hex, obi_hex, motif_hex) in M.KIMONOS.items():
        salt = 41 + len(name)
        motif = hx(motif_hex)

        def petals(u, v, salt=salt, motif=motif):
            if D.noise(int(u * 60), int(v * 22), salt) > 0.965 and v > 0.3:
                return motif
            return None
        t["kimono_%s_skirt" % name] = D.ring(robe_hex, trim_hex, salt, hem_trim=(trim_hex, 0.94), marks=petals, wear=0.5)
    # coats: tails, collars, sleeves
    t["aburame_pale_tails"] = D.ring("#b8b2a2", "#948e80", 50, front_edge="#66615a", wear=1.0)
    t["aburame_pale_collar"] = D.ring("#b8b2a2", "#948e80", 51, w=128, h=32, folds=4, wear=0.4)
    t["aburame_dark_tails"] = D.ring("#3b4038", "#2a2e28", 52, front_edge="#191b17", wear=1.0)
    t["aburame_dark_collar"] = D.ring("#3b4038", "#2a2e28", 53, w=128, h=32, folds=4, wear=0.4)
    t["inuzuka_tails"] = D.ring("#4b4440", "#36302d", 54, front_edge="#211d1b", wear=1.2)
    t["fur_collar"] = D.ring("#cfc6b6", "#a89e8e", 55, w=128, h=32, fur=True)
    t["uchiha_collar"] = D.ring("#1f2436", "#151927", 56, w=128, h=32, folds=4, wear=0.2)
    t["root_collar"] = D.ring("#1a1a1f", "#2c2c34", 57, w=128, h=32, folds=4, wear=0.2)
    # plates
    t["uchiha_tassets"] = D.ring("#7c1820", "#1b1a1f", 60, plates=("#7c1820", "#1b1a1f", 4))
    t["senju_tassets"] = D.ring("#8a2c1c", "#263457", 61, plates=("#8a2c1c", "#263457", 4))
    t["uchiha_plates"] = D.plates("#7c1820", "#1b1a1f", 62)
    t["senju_plates"] = D.plates("#8a2c1c", "#263457", 63)
    # hoods, the hat, things on the back
    t["aburame_hood"] = D.hood("#b8b2a2", "#948e80", 70)
    t["travel_hood"] = D.hood("#5c4a36", "#433526", 71)
    t["kasa_hat"] = D.hat("#c8ad72", "#8a7448", "#d8d2c2", 72)
    t["bug_jar"] = D.gourd("#3e3a34", "#a08a5c", 73)
    t["sand_gourd"] = D.gourd("#b58a52", "#6a4a2a", 74)
    return t


def cape_part(tex, length=19, width=11, wrap=1.4, flare=0.25, stiff=0.15, tinted=False):
    p = {"type": "cape", "texture": tex, "length": length, "width": width, "wrap": wrap, "flare": flare, "stiff": stiff}
    if tinted:
        p["tinted"] = True
    return p


def skirt(tex, length=14, top=3, flare=0.35, open_=0, panels=0, stiff=0.2):
    p = {"type": "skirt", "texture": tex, "length": length, "top": top, "flare": flare, "stiff": stiff}
    if open_:
        p["open"] = open_
    if panels:
        p["panels"] = panels
    return p


def collar(tex, back, front):
    return {"type": "collar", "texture": tex, "height": back, "open": front}


def sleeves(tex, length=4):
    return {"type": "sleeves", "texture": tex, "length": length}


DRAPES = {
    "uchiha_collar_shirt": [collar("uchiha_collar", 2.4, 1.5)],
    "uchiha_war_armor": [{"type": "shoulders", "texture": "uchiha_plates"}],
    "uchiha_war_tassets": [skirt("uchiha_tassets", length=7.5, top=3.2, flare=0.3, panels=5, stiff=0.9)],
    "uzushio_robe": [sleeves("uzushio_skirt", 4.5)],
    "uzushio_robe_hem": [skirt("uzushio_skirt", length=14.4, flare=0.4)],
    "aburame_coat": [collar("aburame_pale_collar", 5.0, 3.8), skirt("aburame_pale_tails", length=11, top=3.5, flare=0.3, open_=2.2),
                     sleeves("aburame_pale_tails", 3.6)],
    "aburame_field_coat": [collar("aburame_dark_collar", 5.0, 3.8), skirt("aburame_dark_tails", length=9.5, top=3.5, flare=0.3, open_=2.4),
                           {"type": "prop", "texture": "bug_jar", "shape": "jar"}],
    "aburame_hood": [{"type": "hood", "texture": "aburame_hood"}],
    "senju_plate_armor": [{"type": "shoulders", "texture": "senju_plates"}],
    "senju_thigh_plates": [skirt("senju_tassets", length=7.5, top=3.2, flare=0.3, panels=4, stiff=0.9)],
    "hyuga_gown": [sleeves("hyuga_skirt", 3.6)],
    "hyuga_gown_hem": [skirt("hyuga_skirt", length=14.4, flare=0.38)],
    "inuzuka_fur_coat": [collar("fur_collar", 1.9, 1.2), skirt("inuzuka_tails", length=6, top=3.5, flare=0.3, open_=2.0)],
    "kaguya_robe_hem": [skirt("kaguya_skirt", length=14.2, flare=0.35)],
    "hoshigaki_cloak": [cape_part("hoshigaki_cape", length=18, width=11.5, wrap=1.8)],
    "sand_gourd": [{"type": "prop", "texture": "sand_gourd", "shape": "gourd"}],
    "root_robe": [collar("root_collar", 2.0, 1.2)],
    "cape": [cape_part("cape_plain", tinted=True)],
    "kage_cape": [cape_part("kage_cape", length=20, width=11.5)],
    "travel_cape": [cape_part("travel_cape", length=18, width=11)],
    "rogue_cape": [cape_part("rogue_cape", length=18.5, width=11, wrap=1.2, flare=0.32)],
    "uchiha_cape": [cape_part("uchiha_cape")],
    "senju_cape": [cape_part("senju_cape")],
    "uzumaki_cape": [cape_part("uzumaki_cape")],
    "cloud_cloak": [collar("cloud_collar", 4.2, 3.0), skirt("cloud_skirt", length=14.4, top=3.0, flare=0.4),
                    sleeves("cloud_skirt", 2.6)],
    "kasa_hat": [{"type": "hat", "texture": "kasa_hat", "width": 9.5, "height": 4.8, "length": 7.5, "panels": 16}],
    "hooded_cloak": [cape_part("hooded_cloak_cape", length=19.5, width=11.5, wrap=2.0, flare=0.3)],
    "travel_hood": [{"type": "hood", "texture": "travel_hood"}],
    "kage_robe": [sleeves("kage_skirt", 4.0)],
    "kage_robe_hem": [skirt("kage_skirt", length=14.6, flare=0.42)],
    "priestess_gown": [sleeves("priestess_sleeve", 4.6)],
    "priestess_gown_hem": [skirt("priestess_skirt", length=14.2, flare=0.45)],
    "medic_gown_hem": [skirt("medic_skirt", length=13.8, flare=0.32)],
    "formal_robe_hem": [skirt("formal_skirt", length=14.4, flare=0.35)],
    "kimono_indigo_hem": [skirt("kimono_indigo_skirt", length=14.2)],
    "kimono_crimson_hem": [skirt("kimono_crimson_skirt", length=14.2)],
    "kimono_sage_hem": [skirt("kimono_sage_skirt", length=14.2)],
    "kimono_plum_hem": [skirt("kimono_plum_skirt", length=14.2)],
}


# ---------------------------------------------------------------------------
# The items: armour texture, icon, recipe, names
# ---------------------------------------------------------------------------

def mc(name):
    return "minecraft:" + name


def shaped(pattern, key):
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
            "key": {k: {"item": v if ":" in v else mc(v)} for k, v in key.items()}}


def r_chest(a, b, c):
    return shaped(["A A", "ABA", "ACA"], {"A": a, "B": b, "C": c})


def r_legs(a, b):
    return shaped(["ABA", "A A", "A A"], {"A": a, "B": b})


def r_head(a, b):
    return shaped(["ABA", "A A"], {"A": a, "B": b})


def r_cape(a, b):
    return shaped(["SBS", "AAA", "AAA"], {"A": a, "B": b, "S": "string"})


def r_mask(dye):
    return shaped(["BDB", "BBB"], {"B": "bone", "D": dye})


# id: (armour texture name, slot, recipe, icon kind, English, Spanish)
ITEMS = {
    "uchiha_collar_shirt": ("uchiha_collar_shirt", "chest", r_chest("blue_wool", "red_dye", "light_gray_wool"), "chest", "Uchiha High-Collar Shirt", "Camisa de cuello alto Uchiha"),
    "uchiha_war_armor": ("uchiha_war_armor", "chest", r_chest("iron_ingot", "red_dye", "black_wool"), "chest", "Uchiha War Armour", "Armadura de guerra Uchiha"),
    "uchiha_war_tassets": ("uchiha_war_armor", "legs", r_legs("iron_ingot", "red_dye"), "legs", "Uchiha Thigh Plates", "Faldar Uchiha"),
    "uzumaki_vest": ("uzumaki_vest", "chest", r_chest("leather", "orange_dye", "red_wool"), "chest", "Uzumaki Swirl Vest", "Chaleco del remolino Uzumaki"),
    "uzushio_robe": ("uzushio_robe", "chest", r_chest("red_wool", "white_wool", "orange_dye"), "chest", "Uzushio Robe", "Túnica de Uzushio"),
    "uzushio_robe_hem": ("uzushio_robe", "legs", r_legs("red_wool", "orange_dye"), "legs", "Uzushio Robe Skirt", "Falda de la túnica de Uzushio"),
    "aburame_coat": ("aburame_coat", "chest", r_chest("light_gray_wool", "spider_eye", "gray_wool"), "coat", "Aburame Coat", "Abrigo Aburame"),
    "aburame_field_coat": ("aburame_field_coat", "chest", r_chest("green_wool", "glass_bottle", "gray_wool"), "coat", "Aburame Field Coat", "Abrigo de campo Aburame"),
    "aburame_hood": ("aburame_hood", "head", r_head("light_gray_wool", "black_stained_glass_pane"), "hood", "Aburame Hood", "Capucha Aburame"),
    "aburame_glasses": ("aburame_glasses", "head", shaped(["GIG"], {"G": "black_stained_glass_pane", "I": "iron_nugget"}), "glasses", "Round Dark Glasses", "Gafas oscuras redondas"),
    "senju_plate_armor": ("senju_plate_armor", "chest", r_chest("iron_ingot", "red_dye", "green_wool"), "chest", "Senju Plate Armour", "Armadura de placas Senju"),
    "senju_thigh_plates": ("senju_plate_armor", "legs", r_legs("iron_ingot", "green_dye"), "legs", "Senju Thigh Plates", "Faldar Senju"),
    "senju_robe": ("senju_robe", "chest", r_chest("green_wool", "oak_sapling", "brown_wool"), "chest", "Senju Robe", "Túnica Senju"),
    "hyuga_gown": ("hyuga_gown", "chest", r_chest("white_wool", "purple_dye", "light_gray_wool"), "chest", "Hyūga Gown", "Túnica larga Hyūga"),
    "hyuga_gown_hem": ("hyuga_gown", "legs", r_legs("white_wool", "purple_dye"), "legs", "Hyūga Gown Skirt", "Falda de la túnica Hyūga"),
    "nara_vest": ("nara_vest", "chest", r_chest("leather", "green_dye", "brown_wool"), "chest", "Nara Jōnin Vest", "Chaleco jōnin Nara"),
    "akimichi_armor": ("akimichi_armor", "chest", r_chest("leather", "red_dye", "bread"), "chest", "Akimichi Armour", "Armadura Akimichi"),
    "inuzuka_fur_coat": ("inuzuka_fur_coat", "chest", r_chest("brown_wool", "rabbit_hide", "gray_wool"), "coat", "Inuzuka Fur-Trimmed Coat", "Abrigo Inuzuka con piel"),
    "kaguya_robe": ("kaguya_robe", "chest", r_chest("white_wool", "bone", "red_wool"), "chest", "Kaguya Robe", "Túnica Kaguya"),
    "kaguya_robe_hem": ("kaguya_robe", "legs", r_legs("white_wool", "bone"), "legs", "Kaguya Robe Skirt", "Falda de la túnica Kaguya"),
    "hoshigaki_cloak": ("hoshigaki_cloak", "chest", r_chest("cyan_wool", "prismarine_shard", "gray_wool"), "cloak", "Hoshigaki Sea Cloak", "Capa marina Hoshigaki"),
    "yamanaka_wrap": ("yamanaka_wrap", "chest", r_chest("purple_wool", "allium", "string"), "chest", "Yamanaka Wrap Top", "Top vendado Yamanaka"),
    "might_jumpsuit": ("might_jumpsuit", "chest", r_chest("lime_wool", "green_dye", "leather"), "chest", "Green Jumpsuit", "Mono verde"),
    "might_jumpsuit_legs": ("might_jumpsuit", "legs", r_legs("lime_wool", "green_dye"), "legs", "Green Jumpsuit Legs", "Perneras del mono verde"),
    "might_leg_warmers": ("might_leg_warmers", "feet", shaped(["A A", "B B"], {"A": "orange_wool", "B": "leather"}), "boots", "Orange Leg Warmers", "Calentadores naranjas"),
    "hunter_mask": ("hunter_mask", "head", r_mask("blue_dye"), "mask", "Hunter Mask", "Máscara de cazador"),
    "sand_gourd": ("sand_gourd", "chest", shaped(["S  ", " G ", " CS"], {"S": "string", "G": "decorated_pot", "C": "sand"}), "gourd", "Sand Gourd", "Calabaza de arena"),
    "root_robe": ("root_robe", "chest", r_chest("black_wool", "paper", "white_wool"), "chest", "Root Robe", "Túnica de Raíz"),
    "chinoike_robe": ("chinoike_robe", "chest", r_chest("red_wool", "black_dye", "black_wool"), "chest", "Chinoike Robe", "Túnica Chinoike"),
    "yotsuki_harness": ("yotsuki_harness", "chest", r_chest("white_wool", "iron_ingot", "leather"), "chest", "Yotsuki Harness", "Arnés Yotsuki"),
    "cape": ("cape", "chest", r_cape("white_wool", "gold_nugget"), "cape", "Cape", "Capa"),
    "kage_cape": ("kage_cape", "chest", r_cape("white_wool", "gold_ingot"), "cape", "Kage Cape", "Capa del Kage"),
    "travel_cape": ("travel_cape", "chest", r_cape("brown_wool", "leather"), "cape", "Travel Cape", "Capa de viaje"),
    "rogue_cape": ("rogue_cape", "chest", r_cape("black_wool", "iron_nugget"), "cape", "Tattered Rogue Cape", "Capa raída de renegado"),
    "uchiha_cape": ("uchiha_cape", "chest", r_cape("blue_wool", "red_dye"), "cape", "Uchiha Crest Cape", "Capa con el emblema Uchiha"),
    "senju_cape": ("senju_cape", "chest", r_cape("green_wool", "oak_sapling"), "cape", "Senju Crest Cape", "Capa con el emblema Senju"),
    "uzumaki_cape": ("uzumaki_cape", "chest", r_cape("red_wool", "orange_dye"), "cape", "Uzumaki Crest Cape", "Capa con el emblema Uzumaki"),
    "cloud_cloak": ("cloud_cloak", "chest", shaped(["ABA", "ACA", "AAA"], {"A": "black_wool", "B": "white_wool", "C": "red_wool"}), "cloud", "Cloud Cloak", "Capa de las nubes"),
    "kasa_hat": ("kasa_hat", "head", shaped([" W ", "WSW", "P P"], {"W": "wheat", "S": "string", "P": "paper"}), "hat", "Straw Travel Hat", "Sombrero de paja de viaje"),
    "hooded_cloak": ("hooded_cloak", "chest", shaped(["ABA", "AAA", "A A"], {"A": "brown_wool", "B": "leather"}), "cloak", "Hooded Travel Cloak", "Capa de viaje con capucha"),
    "travel_hood": ("travel_hood", "head", r_head("brown_wool", "string"), "hood", "Travel Hood", "Capucha de viaje"),
    "mask_cat": ("mask_cat", "head", r_mask("magenta_dye"), "mask", "Cat Mask", "Máscara de gato"),
    "mask_fox": ("mask_fox", "head", r_mask("orange_dye"), "mask", "Fox Mask", "Máscara de zorro"),
    "mask_dog": ("mask_dog", "head", r_mask("brown_dye"), "mask", "Dog Mask", "Máscara de perro"),
    "mask_bird": ("mask_bird", "head", r_mask("cyan_dye"), "mask", "Bird Mask", "Máscara de pájaro"),
    "anbu_uniform": ("anbu_uniform", "chest", r_chest("iron_ingot", "gray_dye", "black_wool"), "chest", "ANBU Uniform", "Uniforme ANBU"),
    "anbu_pants": ("anbu_uniform", "legs", r_legs("black_wool", "iron_nugget"), "legs", "ANBU Trousers", "Pantalones ANBU"),
    "kage_robe_hem": ("kage_robe_hem", "legs", r_legs("white_wool", "gold_nugget"), "legs", "Kage Robe Skirt", "Falda de la túnica del Kage"),
    "priestess_gown": ("priestess_gown", "chest", r_chest("white_wool", "red_dye", "red_wool"), "chest", "Priestess Gown", "Vestido de sacerdotisa"),
    "priestess_gown_hem": ("priestess_gown", "legs", r_legs("red_wool", "white_dye"), "legs", "Priestess Skirt", "Falda de sacerdotisa"),
    "medic_gown": ("medic_gown", "chest", r_chest("white_wool", "green_dye", "glistering_melon_slice"), "chest", "Medic Gown", "Bata de médico"),
    "medic_gown_hem": ("medic_gown", "legs", r_legs("white_wool", "lime_dye"), "legs", "Medic Gown Skirt", "Falda de la bata de médico"),
}

# Existing items that now carry drapes but keep their own names, recipes and armour layers.
EXISTING_DRAPED = ("kage_robe", "formal_robe_hem", "kimono_indigo_hem", "kimono_crimson_hem", "kimono_sage_hem", "kimono_plum_hem")

ANIMAL_ASIDE = ("anbu_mask", "mask_wolf", "mask_hawk", "mask_boar", "mask_crane", "mask_cat", "mask_fox", "mask_dog", "mask_bird", "hunter_mask")

LANG_EXTRA = {
    "en_us": {
        "tooltip.noirm.cape.sew": "Sew it onto any chest piece: craft them together (craft the piece alone to unpick it)",
        "tooltip.noirm.mask.stealth": "Over the face: hides your name, a little harder to notice",
        "tooltip.noirm.mask.aside": "Worn pushed aside: your face shows",
        "tooltip.noirm.mask.toggle": "Sneak + use: push it aside / back over the face",
        "message.noirm.mask.aside": "Mask pushed aside",
        "message.noirm.mask.face": "Mask over the face",
    },
    "es": {
        "tooltip.noirm.cape.sew": "Cósela a cualquier pieza de pecho: fabrícalas juntas (fabrica la pieza sola para descoserla)",
        "tooltip.noirm.mask.stealth": "Sobre la cara: oculta tu nombre y cuesta un poco más verte",
        "tooltip.noirm.mask.aside": "Llevada a un lado: se te ve la cara",
        "tooltip.noirm.mask.toggle": "Agáchate + usar: apártala / vuelve a ponerla sobre la cara",
        "message.noirm.mask.aside": "Máscara apartada a un lado",
        "message.noirm.mask.face": "Máscara sobre la cara",
    },
}


def armour_textures():
    """texture name -> (layer 1, layer 2)."""
    t = {}
    t["uchiha_collar_shirt"] = (uchiha_collar_shirt(), None)
    t["uchiha_war_armor"] = uchiha_war()
    t["uzumaki_vest"] = (uzumaki_vest(), None)
    t["uzushio_robe"] = uzushio_robe()
    t["aburame_coat"] = (aburame_coat(ABU_PALE), None)
    t["aburame_field_coat"] = (aburame_coat(ABU_DARK, sash=ABU_SASH), None)
    t["aburame_hood"] = (head_piece(glasses_fn(LENS, FRAME, hood_shadow=hx("#141210"))), None)
    t["aburame_glasses"] = (head_piece(glasses_fn(LENS, FRAME)), None)
    t["senju_plate_armor"] = senju_plates()
    t["senju_robe"] = (senju_robe(), None)
    t["hyuga_gown"] = hyuga_gown()
    t["nara_vest"] = (nara_vest(), None)
    t["akimichi_armor"] = (akimichi_armour(), None)
    t["inuzuka_fur_coat"] = (inuzuka_coat(), None)
    t["kaguya_robe"] = kaguya_robe()
    t["hoshigaki_cloak"] = (hoshigaki_cloak(), None)
    t["yamanaka_wrap"] = (yamanaka_wrap(), None)
    suit, legs, warmers = might_suit()
    t["might_jumpsuit"] = (suit, legs)
    t["might_leg_warmers"] = (warmers, None)
    t["sand_gourd"] = (sand_gourd(), None)
    t["root_robe"] = (root_robe(), None)
    t["chinoike_robe"] = (chinoike_robe(), None)
    t["yotsuki_harness"] = (yotsuki_harness(), None)
    for cape_id in ("cape", "kage_cape", "travel_cape", "rogue_cape", "uchiha_cape", "senju_cape", "uzumaki_cape"):
        clasp = GOLDC if cape_id in ("kage_cape", "cape") else STEEL["b"]
        t[cape_id] = (straps(STRAP, clasp), None)
    t["cloud_cloak"] = (cloud_cloak(), None)
    t["kasa_hat"] = (head_piece(lambda face, u, v, Wd, H: None), None)
    t["hooded_cloak"] = (straps(STRAP, STEEL["b"], cloak_front=TRAVEL), None)
    t["travel_hood"] = (head_piece(lambda face, u, v, Wd, H: hx("#141210") if face == "front" and v < 2.3 - 0.7 * abs(u - 4) / 4 else None), None)
    for name, (rows, colours) in NEW_MASKS.items():
        t[name] = (M.face_mask(rows, colours), None)
    t["anbu_uniform"] = anbu_uniform()
    t["kage_robe_hem"] = (None, kage_hem())
    t["priestess_gown"] = priestess()
    t["medic_gown"] = medic_gown()
    return t


# ---------------------------------------------------------------------------
# Icons
# ---------------------------------------------------------------------------

GLASSES_ICON = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".oooo....oooo...",
    "ohhkko..okhkko..",
    "okkkkoooookkkko.",
    "okkkko..okkkko..",
    ".oooo....oooo...",
    "................",
    "................",
    "................",
    "................",
    "................",
]
GOURD_ICON = [
    "................",
    ".......oo.......",
    "......orro......",
    ".....ohbbso.....",
    ".....obbbso.....",
    "......orro......",
    "....ohhbbbso....",
    "...ohbbbbbbso...",
    "...ohbbbbbbso...",
    "...ohbbbbbbso...",
    "...obbbbbbbso...",
    "....obbbbbso....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]
COAT_ICON = [
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
    "..ohbbbbbbbbbso.",
    "..ohbbbbbbbbbso.",
    ".ohbbbbbbbbbbbso",
    ".ohbbbb.bbbbbbso",
    ".oooooo.ooooooo.",
    "................",
]


def icons():
    I, cols = M.icon, M.cols
    out = {}
    CHEST, LEGS, BOOTS = G.CHEST, G.LEGS, G.BOOTS

    def mono(template, p, detail=None, outline=None):
        return I(template, cols(p, outline), detail)

    crest_px = {
        "uchiha_collar_shirt": (NAVY, lambda x, y, ch: FAN_RED if (x, y) in ((7, 4), (8, 4), (6, 5), (9, 5)) else
                                FAN_WHITE if (x, y) in ((7, 5), (8, 5), (7, 6), (8, 6)) else
                                WARMER["b"] if ch != "o" and y == 5 and (x < 2 or x > 13) else None),
        "uchiha_war_armor": (CRIMSON, lambda x, y, ch: BLACK_CORD if ch != "o" and y in (4, 7, 10) else None),
        "uzumaki_vest": (UZU_VEST, lambda x, y, ch: UZU_RED["h"] if (x - 7.5) ** 2 + (y - 7) ** 2 in (4.25, 6.25) else None),
        "uzushio_robe": (UZU_RED, lambda x, y, ch: CREAM["b"] if ch != "o" and 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)) else None),
        "senju_plate_armor": (BRICK, lambda x, y, ch: NAVY_CORD if ch != "o" and y in (4, 7, 10) else None),
        "senju_robe": (MOSS, lambda x, y, ch: CREAM["b"] if ch != "o" and 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)) else
                       (EARTH["b"] if y in (8, 9) and ch != "o" else None)),
        "hyuga_gown": (LILAC, lambda x, y, ch: LILAC_T["b"] if ch != "o" and (y in (8, 9) or (2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)))) else None),
        "nara_vest": (OLIVE, lambda x, y, ch: OLIVE["d"] if ch != "o" and x in (5, 10) and 6 <= y <= 9 else None),
        "akimichi_armor": (AKI_RED, lambda x, y, ch: BONE["h"] if (x, y) in ((5, 5), (10, 5), (5, 6), (10, 6)) else None),
        "kaguya_robe": (KAG_WHITE, lambda x, y, ch: KAG_RED["b"] if ch != "o" and 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)) else None),
        "yamanaka_wrap": (PURPLE, lambda x, y, ch: WRAPC if ch != "o" and (x < 3 or x > 12) else None),
        "might_jumpsuit": (GREEN, lambda x, y, ch: GREEN["d"] if ch != "o" and x in (7, 8) and y < 8 else None),
        "root_robe": (ROOT_BLACK, lambda x, y, ch: WRAPC if ch != "o" and x > 12 else None),
        "chinoike_robe": (BLOODROBE, None),
        "yotsuki_harness": (CLOUD_WHITE, lambda x, y, ch: INK["b"] if ch != "o" and x < 6 and y < 6 else None),
        "anbu_uniform": (ANBU_GREY, lambda x, y, ch: INK["b"] if ch != "o" and (x < 3 or x > 12) else None),
        "priestess_gown": (WHITE, lambda x, y, ch: GOWN_RED["b"] if ch != "o" and y >= 8 else None),
        "medic_gown": (MEDIC, lambda x, y, ch: MEDIC_T["b"] if (x in (7, 8) and 4 <= y <= 9) or (y in (6, 7) and 5 <= x <= 10) else None),
    }
    for name, (p, det) in crest_px.items():
        out[name] = mono(CHEST, p, det)
    legs = {
        "uchiha_war_tassets": (CRIMSON, lambda x, y, ch: BLACK_CORD if ch != "o" and y in (4, 7) else None),
        "uzushio_robe_hem": (UZU_RED, lambda x, y, ch: CREAM["b"] if ch != "o" and y == 12 else None),
        "senju_thigh_plates": (BRICK, lambda x, y, ch: NAVY_CORD if ch != "o" and y in (4, 7) else None),
        "hyuga_gown_hem": (LILAC, lambda x, y, ch: LILAC_T["b"] if ch != "o" and y == 12 else None),
        "kaguya_robe_hem": (KAG_WHITE, lambda x, y, ch: KAG_RED["b"] if ch != "o" and y == 12 else None),
        "might_jumpsuit_legs": (GREEN, None),
        "anbu_pants": (INK, lambda x, y, ch: WRAPC if ch != "o" and y >= 9 else None),
        "kage_robe_hem": (WHITE, lambda x, y, ch: hx("#c49a3c") if ch != "o" and y == 12 else None),
        "priestess_gown_hem": (GOWN_RED, None),
        "medic_gown_hem": (MEDIC, lambda x, y, ch: MEDIC_T["b"] if ch != "o" and y == 12 else None),
    }
    for name, (p, det) in legs.items():
        out[name] = mono(LEGS, p, det)
    out["might_leg_warmers"] = mono(BOOTS, ORANGE, lambda x, y, ch: hx("#2a2622") if ch != "o" and y >= 10 else None)
    out["aburame_coat"] = mono(COAT_ICON, ABU_PALE, lambda x, y, ch: tone(ABU_PALE["d"], -1) if ch != "o" and x == 8 and y % 2 == 0 and y > 2 else None)
    out["aburame_field_coat"] = mono(COAT_ICON, ABU_DARK, lambda x, y, ch: ABU_SASH["b"] if ch != "o" and y in (8, 9) else None)
    out["inuzuka_fur_coat"] = mono(COAT_ICON, FUR_COAT, lambda x, y, ch: hx("#cfc6b6") if ch != "o" and (y <= 2 or (y == 6 and (x < 3 or x > 12))) else None)
    hood_p = ABU_PALE
    out["aburame_hood"] = I(W.HOOD_ICON, {"o": tone(hood_p["d"], -1), "h": hood_p["h"], "b": hood_p["b"], "s": hood_p["s"], "K": hx("#141210")},
                            lambda x, y, ch: LENS if (x, y) in ((6, 6), (9, 6)) else None)
    out["travel_hood"] = I(W.HOOD_ICON, {"o": tone(TRAVEL["d"], -1), "h": TRAVEL["h"], "b": TRAVEL["b"], "s": TRAVEL["s"], "K": hx("#141210")})
    out["aburame_glasses"] = I(GLASSES_ICON, {"o": FRAME, "h": hx("#4a5060"), "k": LENS})
    out["sand_gourd"] = I(GOURD_ICON, {"o": tone(SAND["d"], -1), "h": SAND["h"], "b": SAND["b"], "s": SAND["s"], "r": hx("#6a4a2a")})
    out["hoshigaki_cloak"] = mono(M.CLOAK_ICON, SEA, lambda x, y, ch: SEA["d"] if y == 13 and x % 2 == 0 else None)
    out["hooded_cloak"] = mono(M.CLOAK_ICON, TRAVEL, lambda x, y, ch: hx("#141210") if 3 <= y <= 5 and 6 <= x <= 9 else None)
    cloak_black = CLOAK_BLACK

    def cloud_detail(x, y, ch):
        if ch == "o":
            return None
        if (x, y) in ((4, 8), (5, 8), (5, 9), (10, 10), (11, 10), (10, 11)):
            return CLOUD_RED
        if (x, y) in ((4, 7), (5, 7), (6, 8), (9, 10), (12, 10), (11, 9)):
            return FAN_WHITE
        return None
    out["cloud_cloak"] = mono(M.CLOAK_ICON, cloak_black, cloud_detail, outline=hx("#000000"))
    out["kasa_hat"] = I(M.HAT_ICON, {"o": hx("#3a2e1a"), "h": hx("#e2c88a"), "b": hx("#c8ad72"), "s": hx("#9a8250")},
                        lambda x, y, ch: hx("#d8d2c2") if y == 11 and x % 2 == 1 else None)
    capes = {
        "cape": ((206, 202, 194), None),
        "kage_cape": ((232, 228, 218), hx("#a8262c")),
        "travel_cape": ((106, 86, 66), None),
        "rogue_cape": ((42, 42, 46), None),
        "uchiha_cape": ((31, 36, 54), FAN_RED),
        "senju_cape": ((47, 58, 40), TAN_CORD),
        "uzumaki_cape": ((126, 36, 24), hx("#e0b070")),
    }
    for name, (base, mark) in capes.items():
        p = pal("#%02x%02x%02x" % base)

        def cape_detail(x, y, ch, mark=mark, name=name):
            if ch == "o":
                return None
            if name == "rogue_cape" and y == 13 and x % 3 == 0:
                return (0, 0, 0, 0)
            if mark and (x - 7.5) ** 2 + (y - 7) ** 2 < 3.5:
                return mark
            return None
        out[name] = mono(M.CLOAK_ICON, p, cape_detail)
    # the dyeable cape: a grey cloth layer (tinted) and a clasp overlay
    clasp = Canvas(16, 16)
    for (x, y) in ((6, 2), (7, 2), (8, 2), (9, 2), (7, 3), (8, 3)):
        clasp.set(x, y, GOLDC + (255,))
    out["cape_overlay"] = clasp
    for name, (rows, colours) in NEW_MASKS.items():
        out[name] = mask_icon(rows, colours)
    return out


def mask_icon(rows, colours):
    cmap = {k: hx(v) for k, v in colours.items()}
    base = cmap.get("w", M.PORC["b"])
    cv = Canvas(16, 16)
    for y, row in enumerate(M.FACE_ICON):
        for x, ch in enumerate(row):
            if ch == "o":
                cv.set(x, y, tone(M.PORC["d"], -1.2) + (255,))
            elif ch == "F":
                gx, gy = min(7, int((x - 2) / 1.5)), min(7, int((y - 2) / 1.25))
                c = rows[gy][gx]
                if c == "e":
                    col = HOLE
                elif c in cmap:
                    col = cmap[c]
                elif c == ".":
                    col = base
                else:
                    col = base
                cv.set(x, y, col + (255,))
    return cv


# ---------------------------------------------------------------------------
# Writing everything
# ---------------------------------------------------------------------------

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save_all():
    tex = armour_textures()
    for name, pair in tex.items():
        for i, cv in enumerate(pair):
            if cv is not None:
                cv.save(os.path.join(G.ARMOR_DIR, "%s_layer_%d.png" % (name, i + 1)))
    for name in ANIMAL_ASIDE:
        aside(os.path.join(G.ARMOR_DIR, name + "_layer_1.png"), os.path.join(G.ARMOR_DIR, name + "_aside_layer_1.png"))
    dt = drape_textures()
    for name, cv in dt.items():
        cv.save(os.path.join(DRAPE_DIR, name + ".png"))
    drapes = {"_comment": "Written by tools/gear/clan_wear.py. The 3D cloth and props each item carries (gear/Drapes, "
                          "client/DrapeLayer): cape, skirt, collar, hood, sleeves, shoulders, hat, prop. Sizes in model pixels."}
    for item, parts in DRAPES.items():
        for p in parts:
            assert p["texture"] in dt, (item, p["texture"])
        drapes["noirm:" + item] = parts
    write_json(os.path.join(ASSETS, "gear/drapes.json"), drapes)
    ic = icons()
    for name, cv in ic.items():
        cv.save(os.path.join(G.ITEM_DIR, name + ".png"))
    for item, (texname, slot, recipe, kind, en, es) in ITEMS.items():
        assert texname in tex, texname
        assert item in ic, "icon " + item
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": "noirm:item/" + item}}
        if item == "cape":
            model["textures"]["layer1"] = "noirm:item/cape_overlay"
        write_json(os.path.join(ASSETS, "models/item", item + ".json"), model)
        r = dict(recipe)
        r["result"] = {"item": "noirm:" + item}
        write_json(os.path.join(DATA, "recipes", item + ".json"), r)
    write_json(os.path.join(DATA, "recipes", "cape_sewing.json"), {"type": "noirm:cape_sewing", "category": "equipment"})
    write_lang()
    print("clan wear: %d items, %d armour textures, %d drape textures, %d icons" % (len(ITEMS), len(tex), len(dt), len(ic)))
    preview(tex, ic)


LANG_ANCHOR = '  "item.noirm.battle_hakama"'


def write_lang():
    for lang in ("en_us", "es_es", "es_mx"):
        path = os.path.join(ASSETS, "lang", lang + ".json")
        text = open(path, encoding="utf-8").read()
        data = json.loads(text)
        want = {}
        for item, (_, _, _, _, en, es) in ITEMS.items():
            want["item.noirm." + item] = en if lang == "en_us" else es
        want.update(LANG_EXTRA["en_us" if lang == "en_us" else "es"])
        new = {k: v for k, v in want.items() if data.get(k) != v}
        if not new:
            continue
        lines = text.split("\n")
        # drop old copies of our keys, then insert the block after the anchor line
        keys = set(want)
        lines = [ln for ln in lines if not any(ln.startswith('  "%s":' % k) for k in keys)]
        at = next(i for i, ln in enumerate(lines) if ln.startswith(LANG_ANCHOR))
        block = ['  %s: %s,' % (json.dumps(k, ensure_ascii=False), json.dumps(v, ensure_ascii=False)) for k, v in want.items()]
        lines[at + 1:at + 1] = block
        out = "\n".join(lines)
        json.loads(out)  # still valid
        open(path, "w", encoding="utf-8").write(out)


def preview(tex, ic):
    looks = G.L.load_looks(G.PARTS_DIR)
    skin = G.L.compose(G.L.decode("generic:3a7f00c9b2", looks), looks, G.PARTS_DIR)
    s = 4
    doll_w, doll_h = 16 * s, 32 * s
    shin = G.shinobi()
    l1 = lambda n: tex[n][0]  # noqa: E731
    l2 = lambda n: tex[n][1]  # noqa: E731
    sets = [
        {"chest": l1("uchiha_collar_shirt"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("uchiha_war_armor"), "legs": l2("uchiha_war_armor"), "feet": shin[0]},
        {"chest": l1("uzumaki_vest"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("uzushio_robe"), "legs": l2("uzushio_robe")},
        {"chest": l1("aburame_coat"), "legs": shin[1], "feet": shin[0], "head": l1("aburame_hood")},
        {"chest": l1("aburame_field_coat"), "legs": shin[1], "feet": shin[0], "head": l1("aburame_glasses")},
        {"chest": l1("senju_plate_armor"), "legs": l2("senju_plate_armor"), "feet": shin[0]},
        {"chest": l1("senju_robe"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("hyuga_gown"), "legs": l2("hyuga_gown")},
        {"chest": l1("nara_vest"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("akimichi_armor"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("inuzuka_fur_coat"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("kaguya_robe"), "legs": l2("kaguya_robe")},
        {"chest": l1("yamanaka_wrap"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("might_jumpsuit"), "legs": l2("might_jumpsuit"), "feet": l1("might_leg_warmers")},
        {"chest": l1("root_robe"), "legs": shin[1], "feet": shin[0], "head": l1("hunter_mask")},
        {"chest": l1("cloud_cloak"), "legs": shin[1], "feet": shin[0]},
        {"chest": l1("anbu_uniform"), "legs": l2("anbu_uniform"), "feet": shin[0], "head": l1("mask_fox")},
        {"chest": l1("priestess_gown"), "legs": l2("priestess_gown")},
        {"chest": l1("medic_gown"), "legs": l2("medic_gown")},
    ]
    per_row = 7
    set_w = doll_w * 2 + 3 * s
    rows = (len(sets) + per_row - 1) // per_row
    icon_rows = (len(ic) + 15) // 16
    Wd = max(per_row * (set_w + 12) + 20, 16 * 74 + 20)
    H = 10 + rows * (doll_h + 20) + icon_rows * 74 + 20
    sheet = Canvas(Wd, H, (46, 48, 54, 255))
    for i, armor in enumerate(sets):
        x = 10 + (i % per_row) * (set_w + 12)
        y = 10 + (i // per_row) * (doll_h + 20)
        G.doll(sheet, skin, armor, x, y, s, "front")
        G.doll(sheet, skin, armor, x + doll_w + 3 * s, y, s, "back")
    y0 = 10 + rows * (doll_h + 20)
    for i, (name, cv) in enumerate(ic.items()):
        x = 10 + (i % 16) * 74
        y = y0 + (i // 16) * 74
        sheet.fill_rect(x - 2, y - 2, 68, 68, (62, 64, 72, 255))
        sheet.blit(cv, 0, 0, 16, 16, x, y, 4)
    sheet.save(PREVIEW)
    print("preview:", os.path.relpath(PREVIEW, ROOT))


def main():
    save_all()


if __name__ == "__main__":
    main()

"""The second set of wearable gear (kimonos, hakama, haori, village vests, cloaks, Kage robes and
hats, masks, plain forehead protectors, boots and arm guards). Called by gen_gear.py, which saves
the textures and icons and draws the preview.

All art is original. The rogue cloak uses our own pattern (silver crescent moons on black with a
violet lining), never a symbol from the series; forehead plates carry only our hook glyph.
Layers use the vanilla 64x32 armour layout (layer 1 = helmet, chest + arms, boots; layer 2 =
leggings), so they fit any humanoid armour model.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, ARMOR_PARTS, hx, tone, noise, mirror_side  # noqa: E402
import hd  # noqa: E402
from hd import (Layer, paint, paint_hd, pal, family, SCALE, in_circle, in_ring, in_rrect, in_poly,  # noqa: E402
                on_polyline, side, scatter, flower, cross_collar)
from pixlib import mix  # noqa: E402,F401

A = ARMOR_PARTS


def layer():
    return Layer()


def weave(c, x, y, salt, amount=1.0):
    """The cloth's weave is drawn at full resolution by hd.py (material "cloth")."""
    return c


STEEL = pal("#a7adb5", "#d8dde2", "#7c828a", "#4e535a", mat="metal")
GOLD = pal("#c9a03a", "#ecc860", "#94711f", "#5e470f", mat="metal")
WRAPC = hx("#ddd7c9")
family([WRAPC, tone(WRAPC, -0.7), tone(WRAPC, 0.2), tone(WRAPC, -0.25)], "wrap")
WHITE = pal("#eeeae2", "#fbf9f4", "#cfc9bd", "#9e9788")
STRAP = hx("#2a2a30")
family([STRAP, tone(STRAP, 0.5)], "leather")
HOLE = hx("#0c0c0e")
family([HOLE], "void")


def wraps(x, y):
    """Bandage wraps: the overlapping strips are drawn by hd.py (material "wrap")."""
    return WRAPC


# ---------------------------------------------------------------------------
# Drawn at full resolution (hd.paint_hd): u, v are positions in the 64x32 layout's pixels, as
# floats. Each distinct colour is one part; hd.py adds material, folds, seams, wear and dirt.
# ---------------------------------------------------------------------------

def _g():
    """gen_gear's shared shapes (forehead plate, mask outline, zip), imported when needed."""
    import gen_gear
    return gen_gear


BLOOD = hx("#8e1c20")  # the accent: cords and stitching
family([BLOOD], "cloth")
TIE = hx("#6a5428")
family([TIE], "rope")
FUR = hx("#e4e0d6")
family([FUR, tone(FUR, -0.4)], "fur")


def crescent(u, v, cx, cy, r):
    """Our crescent moon: 'moon', 'rim' (a violet glow just outside it) or None."""
    inside = in_circle(u, v, cx, cy, r) and not in_circle(u, v, cx + 0.38 * r, cy - 0.2 * r, r * 0.82)
    if inside:
        return "moon"
    if in_circle(u, v, cx, cy, r + 0.2) and not in_circle(u, v, cx + 0.38 * r, cy - 0.2 * r, r * 0.82 - 0.2):
        return "rim"
    return None


def crease(u, v, pts, width=0.22):
    """A hand-drawn crease (final phase helper): True on the line."""
    return on_polyline(u, v, pts, width)


# ---------------------------------------------------------------------------
# Kimono (chest) + kimono hem (leggings): a wrap robe, crossed collar, obi sash, scattered flowers
# ---------------------------------------------------------------------------

KIMONOS = {
    # name: (robe, collar trim, obi, motif)
    "indigo": ("#2c3a6e", "#e6e0d2", "#b8862e", "#8fa2d8"),
    "crimson": ("#9a2430", "#efe6d6", "#2a2a34", "#e8a0a8"),
    "sage": ("#5d7a5a", "#efe9da", "#6a3f2a", "#c8dcb0"),
    "plum": ("#5e3458", "#e9dfe6", "#c9a03a", "#d6a8d0"),
}


def kimono(robe_hex, trim_hex, obi_hex, motif_hex, salt):
    P, T, O = pal(robe_hex), pal(trim_hex), pal(obi_hex)
    M = hx(motif_hex)
    family([M, tone(M, -1.2)], "print")
    l1, l2 = layer(), layer()

    def motif(u, v, face_salt):
        spot = scatter(u, v, salt + face_salt, 2.7, 0.5)
        if spot:
            f = flower(u, v, spot[0], spot[1], 0.62)
            if f == "petal":
                return M
            if f == "centre":
                return tone(M, -1.2)
        return None

    fs = {"front": 1, "back": 2, "right": 3, "left": 4, "top": 5}

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return T["b"] if u < 0.6 or u > W - 0.6 else P["b"]
        if face == "back":
            if in_rrect(u, v, 3.4, 6.7, 4.6, 8.9, 0.3):
                return O["s"]  # the bow's knot
            if in_poly(u, v, [(3.6, 7.8), (1.3, 6.2), (1.0, 9.4)]) or in_poly(u, v, [(4.4, 7.8), (6.7, 6.2), (7.0, 9.4)]):
                return O["h"]  # the bow's wings
        if 6.9 <= v < 8.7:  # obi sash and its cord
            if 7.65 <= v < 7.95:
                return T["s"]
            return O["b"]
        if face == "back" and 8.7 <= v < 10.3 and (on_polyline(u, v, [(3.6, 8.6), (3.0, 10.2)], 0.7) or
                                                  on_polyline(u, v, [(4.4, 8.6), (5.1, 10.0)], 0.7)):
            return O["h"]  # the bow's tails
        if face == "front":
            c = cross_collar(u, v)
            if c == "open":
                return None
            if c == "trim":
                return T["b"]
            if c == "edge":
                return P["d"]
        if v >= 11.4:
            return P["s"]
        return motif(u, v, fs[face]) or P["b"]

    def arm(face, u, v, W, H):
        if face == "bottom" or v > 9.6:
            return None
        if face == "top":
            return P["b"]
        if v >= 9.0:
            return T["s"]  # the sleeve's lined edge
        if face == "left":
            return tone(P["b"], -0.4)  # the wide sleeve's inside
        return motif(u, v, 10 + fs[face]) or P["b"]

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if face == "front" and abs(u - 5.2) < 0.13:
            return P["d"]
        return motif(u, v, 20 + fs[face]) or P["b"]

    def hem(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        if v >= 11.35:
            return T["s"]
        if v >= 10.5:
            return P["s"]
        if face == "front" and abs(u - 3.0) < 0.13:
            return P["d"]  # the robe's edge falls down the front
        return motif(u, v, 30 + fs[face]) or P["b"]

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], hem)
    return l1, l2


# ---------------------------------------------------------------------------
# Hakama: wide pleated trousers with a pale waist tie
# ---------------------------------------------------------------------------

def hakama():
    l2 = layer()
    P = pal("#34343e", "#4a4a58", "#25252d", "#18181e")
    P2 = tone(P["b"], -0.25)

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if face == "front" and (in_poly(u, v, [(4, 9.7), (2.6, 9.0), (2.7, 10.6)]) or
                                in_poly(u, v, [(4, 9.7), (5.4, 9.0), (5.3, 10.6)])):
            return WHITE["b"]  # the tie's bow
        if face == "front" and (on_polyline(u, v, [(3.8, 9.9), (3.4, 11.8)], 0.38) or
                                on_polyline(u, v, [(4.2, 9.9), (4.7, 11.6)], 0.38)):
            return WHITE["b"]
        if v < 9.65:
            return WHITE["s"]
        return P["b"]

    def leg(face, u, v, W, H):
        if face == "top":
            return P["b"]
        if face == "bottom":
            return None
        if v >= 11.55:
            return P["d"]
        if face in ("front", "back"):
            j = math.floor(u / 1.0)
            if abs(u - round(u)) < 0.07 and 0 < round(u) < 4:
                return P["d"]  # the pleats' deep folds
            return P["b"] if j % 2 == 0 else P2
        return P["b"]

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    return l2


# ---------------------------------------------------------------------------
# Haori: a short black jacket worn open, white cords, our ring crest
# ---------------------------------------------------------------------------

def haori():
    l1 = layer()
    P = pal("#1f2027", "#33343e", "#15161b", "#0c0c10")
    CREST = hx("#e6e1d6")
    family([CREST], "print")

    def ring(u, v, cx, cy, r):
        return in_ring(u, v, cx, cy, r * 0.55, r)

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["b"]
        if face == "front":
            if 3.0 <= u <= 5.0:
                return None  # worn open
            if 2.3 <= u < 3.0 or 5.0 < u <= 5.7:
                return P["h"]  # the collar band
            if ring(u, v, 1.25, 2.0, 0.72) or ring(u, v, 6.75, 2.0, 0.72):
                return CREST
        if face == "back" and ring(u, v, 4.0, 2.4, 1.1):
            return CREST
        if v >= 11.3:
            return P["d"]
        return P["b"]

    def body_marks(face, u, v, W, H):
        if face != "front":
            return None
        if in_circle(u, v, 4.0, 4.3, 0.36):
            return WHITE["h"]  # the cords' knot (himo)
        if on_polyline(u, v, [(2.9, 3.9), (4.0, 4.3), (5.1, 3.9)], 0.26) or on_polyline(u, v, [(4.0, 4.3), (3.85, 5.8)], 0.22):
            return WHITE["s"]
        return None

    def arm(face, u, v, W, H):
        if face == "bottom" or v > 8.6:
            return None
        if face == "top":
            return P["b"]
        if v >= 8.0:
            return P["s"]
        if face == "right" and ring(u, v, 2.0, 2.6, 0.62):
            return CREST
        return tone(P["b"], -0.3) if face == "left" else P["b"]

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Vests: the jonin vest and one per village look
# ---------------------------------------------------------------------------

VESTS = {
    # name: (base, accent, options)
    "jonin_vest": ("#3e4a2c", "#232a19", {"collar": "pad", "pouches": 2, "pads": True, "back": "straps"}),
    "vest_sand": ("#b89a66", "#6e5232", {"collar": None, "pouches": 1, "strap": True, "pad_right": True}),
    "vest_snow": ("#d4dbe4", "#506e9e", {"collar": "fur", "pouches": 1, "quilt": True}),
    "vest_mist": ("#5c6c7c", "#344050", {"collar": "pad", "pouches": 1, "ribs": True}),
    "vest_cloud": ("#e2dccb", "#7e7a6c", {"collar": None, "pouches": 2, "strap": True}),
    "vest_stone": ("#8a3e2a", "#4e2216", {"collar": "pad", "pouches": 1, "sleeve": True}),
}


def vest(base_hex, accent_hex, o):
    B, C = pal(base_hex), pal(accent_hex)
    B2 = tone(B["b"], -0.25)
    POUCHC = mix(B["b"], B["h"], 0.35)
    l1 = layer()
    l1.fold = 0.6  # padded: stiffer than a robe
    two = o.get("pouches", 1) >= 2
    pouches = [((0.3, 1.75), (1.95, 3.45), (4.55, 6.05), (6.25, 7.7)), (2.0, 6.3)] if two else \
        [((0.35, 3.4), (4.6, 7.65)), (5.0, 8.6)]

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return FUR if o.get("collar") == "fur" else B["h"]
        if v < 1.3 and o.get("collar") == "fur":
            return FUR if v < 1.0 or noise(int(u * 8), 3, 7) > 0.4 else None
        if v < 1.3 and o.get("collar") == "pad":
            return B["d"] if face == "front" and abs(u - 4.0) < 0.6 else B["h"]
        if o.get("strap") and face in ("front", "back"):
            a, b = ((0.5, 0.0), (7.6, 9.6)) if face == "front" else ((7.5, 0.0), (0.4, 9.6))
            if on_polyline(u, v, [a, b], 0.95):
                return C["b"]
        if face == "front":
            if abs(u - 4.0) <= 0.28:
                return C["b"]  # the closing edge
            for x0, x1 in pouches[0]:
                y0, y1 = pouches[1]
                if in_rrect(u, v, x0, y0, x1, y1, 0.22):
                    return B["h"] if v < y0 + (y1 - y0) * 0.3 else POUCHC
            if 9.0 <= v < 9.5:
                return C["s"]
        if face == "back":
            if o.get("back") == "straps" and (2.8 <= v < 3.3 or 6.8 <= v < 7.3):
                return C["b"]
            if v < 2.2:
                return B2  # the yoke
        if face in ("right", "left") and v < 2.2:
            return B["d"]
        if v >= 11.3:
            return C["s"]
        if o.get("ribs") and math.floor(u / 1.0) % 2 == 0:
            return B2
        if o.get("quilt") and math.floor(v / 2.0) % 2 == 1:
            return B2
        return B["b"]

    def body_marks(face, u, v, W, H):
        if face != "front":
            return None
        z = _g().zip_teeth(face, u, v, W, H, top=1.3 if o.get("collar") else 0.3, bottom=11.3)
        if z:
            return z
        for x0, x1 in pouches[0]:
            y0, y1 = pouches[1]
            if in_circle(u, v, (x0 + x1) / 2, y0 + (y1 - y0) * 0.24, 0.16):
                return C["d"]
        return None

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if o.get("sleeve"):
            if v > 6.2:
                return None
            if face == "top":
                return B["h"]
            return C["s"] if v >= 5.6 else B["b"]
        if o.get("pads") or o.get("pad_right"):
            if v > 2.2 or face == "left":
                return None
            if face == "top":
                return C["h"]
            return C["h"] if v < 1.0 else C["b"]
        if face == "left" or v > 1.6:
            return None
        return B["h"] if face == "top" else B["b"]

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Cloaks: a plain travelling cloak and the rogue cloak (our own crescent-moon pattern)
# ---------------------------------------------------------------------------

def cloak(base_hex, lining_hex, clasp, moons=False):
    P, L = pal(base_hex), pal(lining_hex)
    family([clasp], "metal")
    SILVER = hx("#c8ccd6")
    RIM = hx("#7d6a9e")
    family([SILVER], "print")
    family([RIM], "print")
    l1 = layer()
    spots = {"front": [(1.6, 5.6, 1.0), (5.9, 9.6, 0.9)], "back": [(2.6, 3.8, 1.1), (5.6, 8.2, 1.0)],
             "right": [(1.9, 6.8, 0.85)], "left": [(1.9, 9.6, 0.85)]}
    arm_spots = {"right": [(2.0, 4.6, 0.8)], "front": [(2.0, 8.4, 0.75)], "back": [(2.0, 8.4, 0.75)]}

    def pattern(face, u, v, table):
        if not moons:
            return None
        for cx, cy, r in table.get(face, []):
            m = crescent(u, v, cx, cy, r)
            if m == "moon":
                return SILVER
            if m == "rim":
                return RIM
        return None

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["h"]
        if face == "front":
            if in_circle(u, v, 4.0, 1.2, 0.5):
                return clasp
            if v < 1.4:
                return L["s"] if moons else P["h"]  # a high collar
            if v >= 8.4 and abs(u - 4.0) < (v - 8.4) * 0.38 + 0.08:
                return L["b"]  # the cloak parts at the bottom: lining shows
            if abs(u - 4.0) < 0.1:
                return P["d"]
        if face == "back" and not moons and v < 2.7:
            return P["s"] if v < 2.1 else P["d"]  # a folded hood
        p = pattern(face, u, v, spots)
        if p is not None:
            return p
        if v >= 11.4:
            return L["s"] if moons else P["d"]
        return P["b"]

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["h"]
        if face == "left":
            return L["s"] if v > 2.4 else P["s"]  # the lining inside
        p = pattern(face, u, v, arm_spots)
        if p is not None:
            return p
        return P["d"] if v >= 11.3 else P["b"]

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Kage robe: long white robe with gold trim, a red cord and wide sleeves
# ---------------------------------------------------------------------------

GOLDC = hx("#c49a3c")  # gold thread (cloth, not metal)
family([GOLDC, tone(GOLDC, -0.6)], "cloth")


def kage_robe():
    l1 = layer()
    SASH = hx("#8c8a92")
    family([SASH], "cloth")

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return WHITE["b"]
        if 7.9 <= v < 9.0:
            return BLOOD if 8.35 <= v < 8.55 else SASH
        if face == "front":
            c = cross_collar(u, v, 0.85)
            if c == "open":
                return None
            if c == "trim":
                return GOLDC
            if c == "edge":
                return tone(GOLDC, -0.6)
        if v >= 11.2:
            return GOLDC
        return WHITE["b"]

    def hem_marks(face, u, v, W, H):
        if face in ("top", "bottom") or not 11.3 <= v < 11.9:
            return None
        t = (u % 0.9) / 0.9
        if abs(v - 11.6) < 0.25 * (1 - abs(2 * t - 1)) + 0.02:
            return BLOOD  # a band of small diamonds in the gold hem
        return None

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return WHITE["b"]
        if v >= 11.2:
            return GOLDC
        return tone(WHITE["b"], -0.45) if face == "left" else WHITE["b"]

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], hem_marks, "final")
    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], hem_marks, "final")
    return l1


# ---------------------------------------------------------------------------
# Arm guards and hand wraps (chest slot: only the forearms are covered)
# ---------------------------------------------------------------------------

def arm_guards(steel=True):
    l1 = layer()

    def arm(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return WRAPC
        if steel:
            if v < 5.0:
                return None
            if v < 5.5:
                return STRAP
            if v < 9.6 and face != "left":
                if 6.4 <= v < 6.75 or 8.5 <= v < 8.85:
                    return STRAP
                if face == "right" and 1.6 <= u <= 2.4:
                    return STEEL["h"]  # a raised ridge
                return STEEL["b"]
            return WRAPC
        if v < 7.0:
            return None
        return WRAPC

    def marks(face, u, v, W, H):
        if steel and face in ("front", "back", "right"):
            for ry in (5.95, 9.2):
                for rx in (0.6, 3.4):
                    if in_circle(u, v, rx, ry, 0.15):
                        return STEEL["d"]
        if not steel and face == "back" and on_polyline(u, v, [(2.2, 7.2), (2.8, 6.0), (3.3, 5.4)], 0.32):
            return WRAPC  # the loose end
        return None

    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], marks, "final")
    return l1


# ---------------------------------------------------------------------------
# Boots: tabi, straw sandals with leg wraps, steel shin guards
# ---------------------------------------------------------------------------

def tabi():
    l1 = layer()
    P = pal("#23273a", "#363c58", "#181b28", "#0e1018")
    SOLE_C = hx("#c9c4b8")
    family([SOLE_C], "leather")

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return SOLE_C
        if v < 6.0:
            return None
        if v < 6.5:
            return P["h"]
        if v >= 11.5:
            return SOLE_C
        if face == "front" and abs(u - 1.35) < 0.11 and v > 10.2:
            return P["d"]  # the split toe
        return P["b"]

    def marks(face, u, v, W, H):
        if face in ("right", "left") and abs(side(face, u, W) - 0.7) < 0.3:
            for y in (7.3, 8.3, 9.3):
                if abs(v - y) < 0.14:
                    return STEEL["h"]  # the clasps (kohaze)
        return None

    paint_hd(l1, A["leg"], boot)
    l1.overlay(A["leg"], marks, "final")
    return l1


def waraji():
    l1 = layer()
    STRAW_W = pal("#c8a860", "#e0c47c", "#9c7e40", "#6a5428", mat="straw")

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return STRAW_W["b"]
        if v < 5.0:
            return None
        if v >= 11.3:
            return STRAW_W["b"]
        if v >= 10.2:
            if face == "front":
                return STRAW_W["h"] if 1.3 <= u <= 2.7 else None  # thong between the toes
            return STRAW_W["d"] if face == "back" else None
        # crossed ties over the leg wraps
        if on_polyline(u, v, [(0, 5.4), (W, 7.8)], 0.32) or on_polyline(u, v, [(W, 5.4), (0, 7.8)], 0.32) or \
                on_polyline(u, v, [(0, 7.8), (W, 10.2)], 0.32) or on_polyline(u, v, [(W, 7.8), (0, 10.2)], 0.32):
            return TIE
        return WRAPC

    paint_hd(l1, A["leg"], boot)
    return l1


def shin_guards():
    l1 = layer()
    LEATHER = pal("#4a3526", "#634836", "#33241a", "#20160f", mat="leather")

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return LEATHER["d"]
        if v < 3.0:
            return None
        if v >= 9.2:
            return LEATHER["s"] if v >= 11.4 else LEATHER["b"]
        if face == "front":
            if 1.65 <= u <= 2.35:
                return STEEL["h"]  # the ridge
            return STEEL["b"]
        s = side(face, u, W) if face in ("right", "left") else 0
        if face in ("right", "left") and s >= 2:
            return STEEL["s"]
        if 4.0 <= v < 4.45 or 7.0 <= v < 7.45:
            return STRAP  # straps round the back
        return None

    def marks(face, u, v, W, H):
        if face == "front":
            for ry in (3.5, 8.6):
                for rx in (0.55, 3.45):
                    if in_circle(u, v, rx, ry, 0.15):
                        return STEEL["d"]
        return None

    paint_hd(l1, A["leg"], boot)
    l1.overlay(A["leg"], marks, "final")
    return l1


# ---------------------------------------------------------------------------
# Masks (helmet). Front maps: w porcelain, s shade, e eye hole, r / k / g markings, . open
# ---------------------------------------------------------------------------

PORC = pal("#ecebe6", "#ffffff", "#c9c7c0", "#8e8b84", mat="porcelain")

ANIMAL_MASKS = {
    "mask_wolf": ([
        ".r....r.",
        "rwwwwwwr",
        "wwrwwrww",
        "wrwwwwrw",
        "weewweew",
        "wwwsswww",
        "wwrssrww",
        ".wwkkww.",
    ], {"r": "#4a6a9a", "k": "#1c1c22"}),
    "mask_hawk": ([
        "..wwww..",
        ".wkkkkw.",
        "wwrwwrww",
        "wrrwwrrw",
        "weewweew",
        "wwwggwww",
        ".wwggww.",
        "...gg...",
    ], {"r": "#b02a2a", "k": "#2a2a30", "g": "#c9a03a"}),
    "mask_boar": ([
        "r.wwww.r",
        "wwwwwwww",
        "wrrwwrrw",
        "wwwwwwww",
        "weewweew",
        "wwkkkkww",
        "wkskkskw",
        ".W.ww.W.",
    ], {"r": "#7a4a2a", "k": "#8a5a3a", "s": "#3a2418", "W": "#ffffff"}),
    "mask_crane": ([
        "...rr...",
        "..wrrw..",
        ".wwwwww.",
        "wkkwwkkw",
        "weewweew",
        "wwwwwwww",
        ".wwkkww.",
        "...kk...",
    ], {"r": "#c0282d", "k": "#1c1c22"}),
    "oni_mask": ([
        "H......H",
        "HrrrrrrH",
        "rkkrrkkr",
        "rrkrrkrr",
        "reyrryer",
        "rrrddrrr",
        "rWdWWdWr",
        ".rWrrWr.",
    ], {"r": "#a82a22", "k": "#1a1414", "y": "#e8c040", "d": "#4a0e0c", "W": "#f4f0e4", "H": "#e8dcc0",
        "w": "#a82a22", "s": "#7c1c16"}),
}


def face_mask(rows, colours, base=None):
    G = _g()
    cv = layer()
    cmap = {k: hx(v) for k, v in colours.items()}
    base_c = cmap.get("w", (base or PORC)["b"])
    if "w" in cmap:
        family([base_c, tone(base_c, -0.6)], "lacquer")
    shade = cmap.get("s", PORC["s"])
    marks = {}
    for ch, c in cmap.items():
        if ch not in ("w",) and c != base_c:
            marks[ch] = c
            family([c], "print")
    if "s" not in cmap:
        marks["s"] = PORC["s"]
    # the 8x8 map, enlarged with Scale2x so the markings come out smooth
    grid = [[marks.get(ch) for ch in row] for row in rows]
    big = hd.upscale(grid, SCALE)

    def head(face, u, v, W, H):
        if face == "front":
            if almond(u, v, 2.35, 4.25, 0.95, 0.4, 0.18) or almond(u, v, 5.65, 4.25, 0.95, 0.4, -0.18):
                return HOLE
            m = big[min(len(big) - 1, int(v * SCALE))][min(len(big[0]) - 1, int(u * SCALE))]
            ch = rows[min(7, int(v))][min(7, int(u))]
            if m is not None and (G.mask_shape(u, v) or ch != "."):
                return m
            if not G.mask_shape(u, v):
                return None
            return base_c
        if face in ("right", "left"):
            if side(face, u, W) >= 7.25 and 0.6 <= v <= 7.2:
                return shade
            return STRAP if 2.9 <= v < 3.65 else None
        if face == "back":
            return G.mask_back(u, v)
        return None

    paint_hd(cv, A["head"], head)
    return cv


def almond(u, v, cx, cy, rx, ry, tilt=0.0):
    dy = v - cy - tilt * (u - cx)
    return ((u - cx) / rx) ** 2 + (dy / ry) ** 2 <= 1.0


def cloth_mask():
    cv = layer()
    P = pal("#2e3240", "#434858", "#20232e", "#14161d")

    def head(face, u, v, W, H):
        if face in ("top", "bottom") or v < 4.9:
            return None
        if face == "front":
            return P["h"] if v < 5.5 and abs(u - 4.0) < 1.2 else P["b"]  # over the bridge of the nose
        if face == "back":
            if v > 6.6:
                return None
            return P["s"]
        return P["b"]

    def creases(face, u, v, W, H):
        if face == "front" and (crease(u, v, [(3.0, 5.6), (1.6, 6.6), (0.6, 7.6)]) or
                                crease(u, v, [(5.0, 5.6), (6.4, 6.6), (7.4, 7.6)]) or
                                crease(u, v, [(3.2, 6.9), (4.0, 7.1), (4.8, 6.9)], 0.18)):
            return P["d"]
        if face == "back" and in_rrect(u, v, 3.3, 4.9, 4.7, 6.3, 0.4):
            return P["h"]  # the knot
        return None

    paint_hd(cv, A["head"], head)
    cv.overlay(A["head"], creases, "final")
    return cv


# ---------------------------------------------------------------------------
# Forehead protectors: slashed plate (rogues) and a head wrap with a plate
# ---------------------------------------------------------------------------

def slashed_band():
    return _g().headband("#3a3a42", slashed=True)


def head_wrap():
    G = _g()
    cv = layer()
    cloth = pal("#4b4f5e", "#62677a", "#383b47", "#24262e")

    def fn(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return cloth["h"]
        if face == "front":
            p = G.protector_plate(u, v)
            if p is not None:
                return p
            return cloth["b"] if v <= 3.9 else None
        if face == "back":
            if v <= 6.4:
                return cloth["s"]
            if on_polyline(u, v, [(3.6, 6.2), (3.2, 7.9)], 0.75) or on_polyline(u, v, [(4.4, 6.2), (4.9, 7.7)], 0.7):
                return cloth["d"]  # the tied ends
            return None
        s = side(face, u, W)
        if v <= 3.9 or (s < 4.5 and v <= 6.4):
            return cloth["b"]
        return None

    def marks(face, u, v, W, H):
        if face == "front":
            return G.protector_marks(u, v)
        if face == "top" and (crease(u, v, [(0.5, 2.0), (4.0, 3.2), (7.5, 1.6)]) or crease(u, v, [(0.8, 5.6), (4.2, 4.8), (7.4, 6.2)])):
            return cloth["s"]
        if face == "back" and v < 6.2 and (crease(u, v, [(0.6, 1.0), (2.8, 3.6), (3.6, 6.2)]) or
                                          crease(u, v, [(7.4, 1.4), (5.2, 3.8), (4.4, 6.2)])):
            return cloth["d"]
        return None

    paint_hd(cv, A["head"], fn)
    cv.overlay(A["head"], marks, "final")
    return cv


# ---------------------------------------------------------------------------
# Hats (the outer "hat" box sits a little outside the head): straw hat and the Kage hats
# ---------------------------------------------------------------------------

STRAW = pal("#cdb070", "#e6cc8a", "#a08648", "#6e5a2c", mat="straw")
STRAW2 = tone(STRAW["b"], 0.25)

KAGE_HATS = {
    # name: panel colour (matches the village looks' paint)
    "kage_hat_red": "#b8302a",     # Leaf look
    "kage_hat_orange": "#d07a2a",  # Sand look
    "kage_hat_blue": "#3a5aa8",    # Snow look
    "kage_hat_teal": "#2a8a8a",    # Mist look
    "kage_hat_gold": "#d8b030",    # Cloud look
    "kage_hat_brown": "#7a4e2e",   # Stone look
}


def straw_hat():
    cv = layer()

    def hat(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            r = math.hypot(u - 4.0, v - 4.0)
            if r < 0.6:
                return STRAW["d"]
            return STRAW["b"] if math.floor(r / 0.8) % 2 == 0 else STRAW2  # woven rings
        if v > 1.6:
            return None
        return STRAW["s"] if v > 1.05 else STRAW["b"]

    def cord(face, u, v, W, H):
        if face in ("right", "left") and on_polyline(side(face, u, W), v, [(4.4, 1.0), (4.6, 4.5), (5.4, 7.7)], 0.24):
            return TIE  # the chin cord
        return None

    paint_hd(cv, A["hat"], hat)
    paint_hd(cv, A["head"], cord)
    return cv


def kage_hat(panel_hex):
    cv = layer()
    P = pal(panel_hex)

    def hat(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            r = max(abs(u - 4.0), abs(v - 4.0))
            if r < 0.7:
                return P["h"]  # the crown's knob
            if r < 2.4:
                return P["b"]  # the coloured crown
            if r > 3.55:
                return WHITE["s"]
            return WHITE["b"]
        if v > 2.6:
            return None
        if v >= 2.05:
            return P["s"]  # a coloured band at the brim
        if face == "front" and abs(u - 4.0) + v * 1.0 <= 2.6:
            return P["b"]  # a coloured panel at the front
        return WHITE["b"]

    def veil(face, u, v, W, H):
        if face == "back" and 1.0 <= v <= 7.6:
            return WHITE["b"] if v < 7.2 else WHITE["s"]
        if face in ("right", "left") and side(face, u, W) < 3.0 and 1.0 <= v <= 7.0:
            return WHITE["s"]
        return None

    def veil_folds(face, u, v, W, H):
        if face == "back" and 1.2 <= v <= 7.4:
            for x in (1.3, 2.9, 4.6, 6.3):
                if abs(u - (x + 0.25 * math.sin(v * 0.9 + x))) < 0.1 * (v - 1.0) / 6.4 + 0.04:
                    return tone(WHITE["b"], -0.9)
        return None

    paint_hd(cv, A["hat"], hat)
    paint_hd(cv, A["head"], veil)
    cv.overlay(A["head"], veil_folds, "final")
    return cv


# ---------------------------------------------------------------------------
# All the textures. Returns {texture: (layer1 or None, layer2 or None)}
# ---------------------------------------------------------------------------

def textures():
    tex = {}
    for i, (name, (robe, trim, obi, motif)) in enumerate(KIMONOS.items()):
        tex["kimono_" + name] = kimono(robe, trim, obi, motif, 200 + i * 10)
    tex["hakama"] = (None, hakama())
    tex["haori"] = (haori(), None)
    for name, (base, accent, o) in VESTS.items():
        tex[name] = (vest(base, accent, o), None)
    tex["travel_cloak"] = (cloak("#6b5038", "#4a3626", GOLD["b"]), None)
    tex["rogue_cloak"] = (cloak("#17161c", "#3c2f52", hx("#c8ccd6"), moons=True), None)
    tex["kage_robe"] = (kage_robe(), None)
    tex["arm_guards"] = (arm_guards(True), None)
    tex["hand_wraps"] = (arm_guards(False), None)
    tex["tabi"] = (tabi(), None)
    tex["waraji"] = (waraji(), None)
    tex["shin_guards"] = (shin_guards(), None)
    for name, (rows, colours) in ANIMAL_MASKS.items():
        tex[name] = (face_mask(rows, colours), None)
    tex["cloth_mask"] = (cloth_mask(), None)
    tex["forehead_protector_slashed"] = (slashed_band(), None)
    tex["head_wrap_protector"] = (head_wrap(), None)
    tex["straw_hat"] = (straw_hat(), None)
    for name, panel in KAGE_HATS.items():
        tex[name] = (kage_hat(panel), None)
    return tex


# ---------------------------------------------------------------------------
# Icons (16x16)
# ---------------------------------------------------------------------------

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


CLOAK_ICON = [
    "................",
    ".....oooooo.....",
    "....ohhhbbso....",
    "...ohhbbbbsso...",
    "..ohhbbbbbbsso..",
    "..ohbbbbbbbbso..",
    ".ohhbbbbbbbbsso.",
    ".ohbbbbbbbbbbso.",
    ".ohbbbbbbbbbbso.",
    "ohhbbbbbbbbbbsso",
    "ohbbbbbbbbbbbbso",
    "ohbbbbbbbbbbbbso",
    "ohbbbbbbbbbbbbso",
    "ohbbbbbbbbbbbsso",
    "oooooooooooooooo",
    "................",
]
HAT_ICON = [
    "................",
    "................",
    "................",
    "................",
    ".......oo.......",
    "......ohho......",
    ".....ohhbso.....",
    "....ohhbbbso....",
    "...ohhbbbbbso...",
    "..ohhbbbbbbbso..",
    ".ohbbbbbbbbbbso.",
    "oooooooooooooooo",
    "................",
    "................",
    "................",
    "................",
]
WRAPS_ICON = [
    "................",
    "................",
    "..oooo....oooo..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..owwo....owwo..",
    "..owwo....owwo..",
    ".owwwwo..owwwwo.",
    ".owwwwo..owwwwo.",
    "..oooo....oooo..",
    "................",
    "................",
]
FACE_ICON = [  # a mask: the 8x8 face map is drawn into the middle, scaled 1.5x by this outline
    "................",
    "....oooooooo....",
    "...oFFFFFFFFo...",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "...oFFFFFFFFo...",
    "...oFFFFFFFFo...",
    "....oFFFFFFo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]


def icons(base):
    """base: gen_gear's CHEST / LEGS / BOOTS / BAND templates (dict)."""
    CHEST, LEGS, BOOTS, BAND = base["CHEST"], base["LEGS"], base["BOOTS"], base["BAND"]
    out = {}

    for name, (robe, trim, obi, motif) in KIMONOS.items():
        P, T, O = pal(robe), pal(trim), pal(obi)

        def k_detail(x, y, ch, T=T, O=O, P=P, motif=hx(motif)):
            if ch == "o":
                return None
            if 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)):
                return T["b"]
            if y in (7, 8):
                return O["h"] if y == 7 else O["s"]
            if y >= 9 and x == 9:
                return P["d"]
            if noise(x, y, 7) > 0.88 and ch == "b":
                return motif
            return None
        out["kimono_" + name] = icon(CHEST, cols(P), k_detail)

        def h_detail(x, y, ch, T=T, P=P):
            if ch == "o":
                return None
            if y == 12:
                return T["s"]
            if y == 2:
                return O["s"]
            return None
        out["kimono_" + name + "_hem"] = icon(LEGS, cols(P), h_detail)

    hk = pal("#34343e", "#4a4a58", "#25252d", "#18181e")

    def hakama_detail(x, y, ch):
        if y == 2 and ch in "bhs":
            return WHITE["b"]
        if ch == "b" and x in (5, 10) and y > 3:
            return hk["s"]
        return None
    out["hakama"] = icon(LEGS, cols(hk), hakama_detail)

    hp = pal("#1f2027", "#33343e", "#15161b", "#0c0c10")

    def haori_detail(x, y, ch):
        if ch == "o":
            return None
        if x in (7, 8) and y >= 3:
            return WHITE["h"] if y == 6 else hx("#5a5a64")
        if x in (6, 9) and y >= 2:
            return hp["h"]
        if (x, y) in ((4, 4), (5, 4), (4, 5), (5, 5)):
            return WHITE["b"]
        return None
    out["haori"] = icon(CHEST, cols(hp, hx("#08080a")), haori_detail)

    for name, (b, a, o) in VESTS.items():
        B, C = pal(b), pal(a)

        def v_detail(x, y, ch, B=B, C=C, o=o):
            if ch == "o":
                return None
            if x == 7 and y >= 2:
                return C["b"]
            if o.get("strap") and x + y in (13, 14) and 2 <= y <= 11:
                return C["s"]
            if 7 <= y <= 11 and x in (4, 5, 6, 9, 10, 11):
                if y == 7:
                    return B["h"]
                if y == 11:
                    return C["s"]
            if y in (2, 3) and ch in "bh":
                return WHITE["h"] if o.get("collar") == "fur" else B["h"]
            if o.get("ribs") and ch == "b" and x % 2 == 0:
                return B["s"]
            if (o.get("pads") or o.get("pad_right") or o.get("sleeve")) and y in (4, 5) and x in (0, 1, 14, 15):
                return C["b"]
            return None
        out[name] = icon(CHEST, cols(B), v_detail)

    tp = pal("#6b5038")

    def travel_detail(x, y, ch):
        if (x, y) in ((7, 2), (8, 2)):
            return GOLD["b"]
        if x in (7, 8) and y >= 11 and ch != "o":
            return tone(tp["d"], 0.3)
        return None
    out["travel_cloak"] = icon(CLOAK_ICON, cols(tp), travel_detail)

    rp = pal("#17161c", "#2a2832", "#100f14", "#08080a")
    moons = {(4, 5), (5, 4), (5, 6), (10, 9), (11, 8), (11, 10), (3, 11), (4, 10), (4, 12)}

    def rogue_detail(x, y, ch):
        if ch == "o":
            return None
        if (x, y) in moons:
            return hx("#c8ccd6")
        if (x - 1, y) in moons and ch != "o":
            return hx("#7d6a9e")
        if y in (1, 2) and ch != "o":
            return hx("#3c2f52")
        if x in (7, 8) and y >= 11:
            return hx("#3c2f52")
        return None
    out["rogue_cloak"] = icon(CLOAK_ICON, cols(rp, hx("#050507")), rogue_detail)

    def robe_detail(x, y, ch):
        if ch == "o":
            return None
        if 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)):
            return GOLD["b"]
        if y == 8:
            return hx("#8c8a92")
        if y >= 9 and x == 9:
            return GOLD["s"]
        if y == 12 or (y in (5, 6) and x in (0, 1, 14, 15)):
            return GOLD["b"]
        return None
    out["kage_robe"] = icon(CHEST, cols(WHITE, hx("#5e5a50")), robe_detail)

    out["arm_guards"] = icon(WRAPS_ICON, {"o": hx("#2a2c32"), "h": STEEL["h"], "s": STEEL["s"], "w": WRAPC},
                             lambda x, y, ch: tone(WRAPC, -0.5) if ch == "w" and (x + y) % 2 else None)
    out["hand_wraps"] = icon(WRAPS_ICON, {"o": hx("#6e685c"), "h": WRAPC, "s": WRAPC, "w": WRAPC},
                             lambda x, y, ch: tone(WRAPC, -0.5) if ch in "hsw" and (x + 2 * y) % 3 == 0 else None)

    tb = pal("#23273a", "#363c58", "#181b28", "#0e1018")
    out["tabi"] = icon(BOOTS, cols(tb), lambda x, y, ch: tb["d"] if y >= 10 and x in (4, 12) and ch != "o" else None)
    out["waraji"] = icon(BOOTS, cols(STRAW),
                         lambda x, y, ch: (WRAPC if (x + y) % 2 else tone(WRAPC, -0.6)) if y < 10 and ch in "hbs" else None)
    out["shin_guards"] = icon(BOOTS, cols(STEEL),
                              lambda x, y, ch: pal("#4a3526")["b"] if y >= 10 and ch in "hbs" else None)

    for name, (rows, colours) in list(ANIMAL_MASKS.items()):
        cmap = {k: hx(v) for k, v in colours.items()}
        base_c = cmap.get("w", PORC["b"])

        def m_detail(x, y, ch, rows=rows, cmap=cmap, base_c=base_c):
            if ch != "F":
                return None
            fx, fy = min(7, (x - 2) * 8 // 12), min(7, (y - 2) * 8 // 10)
            c = rows[fy][fx]
            if c in (".", "w"):
                return base_c
            if c == "e":
                return HOLE
            if c == "s" and "s" not in cmap:
                return PORC["s"]
            return cmap.get(c, base_c)
        out[name] = icon(FACE_ICON, {"o": tone(base_c, -2.5), "F": base_c}, m_detail)

    cm = pal("#2e3240", "#434858", "#20232e", "#14161d")

    def cloth_detail(x, y, ch):
        if ch != "F":
            return None
        if y < 7:
            return None if (x + y) % 7 else None
        return cm["h"] if y == 7 and x in (7, 8) else cm["b"]
    cloth_icon = icon(FACE_ICON, {"o": cm["d"], "F": None}, cloth_detail)
    for y in range(7, 12):  # draw the outline only round the cloth
        for x in range(16):
            if FACE_ICON[y][x] == "o":
                cloth_icon.set(x, y, cm["d"])
    for x in range(3, 13):
        cloth_icon.set(x, 6, cm["d"])
    out["cloth_mask"] = cloth_icon

    band = pal("#3a3a42")
    slash = {(6, 8), (7, 7), (8, 7), (9, 6), (10, 6), (5, 8)}
    out["forehead_protector_slashed"] = icon(BAND, {
        "o": tone(band["d"], -0.8), "h": band["h"], "b": band["b"], "s": band["s"], "t": band["s"],
        "M": STEEL["h"], "m": STEEL["b"], "n": STEEL["s"], "e": STEEL["d"]},
        lambda x, y, ch: hx("#33363c") if (x, y) in slash else None)
    wrap = pal("#4b4f5e", "#62677a", "#383b47", "#24262e")

    def wrap_detail(x, y, ch):
        if y < 5 and 2 <= x <= 13 and y >= 1:
            if y == 1 and not 5 <= x <= 10:
                return None
            if y == 2 and not 3 <= x <= 12:
                return None
            return wrap["h"] if y < 3 else wrap["b"]
        return None
    out["head_wrap_protector"] = icon(BAND, {
        "o": tone(wrap["d"], -0.8), "h": wrap["h"], "b": wrap["b"], "s": wrap["s"], "t": wrap["s"],
        "M": STEEL["h"], "m": STEEL["b"], "n": STEEL["s"], "e": STEEL["d"]}, wrap_detail)

    out["straw_hat"] = icon(HAT_ICON, cols(STRAW),
                            lambda x, y, ch: tone(STRAW["b"], -0.4) if ch == "b" and (x + y) % 3 == 0 else None)
    for name, panel in KAGE_HATS.items():
        P = pal(panel)

        def kh_detail(x, y, ch, P=P):
            if ch == "o":
                return None
            if 5 <= y <= 9 and abs(x - 7.5) <= (y - 4) * 0.6:
                return P["h"] if y < 7 else P["b"]
            return None
        hat = icon(HAT_ICON, cols(WHITE, hx("#5e5a50")), kh_detail)
        for y in range(12, 15):  # the white veil hanging behind
            for x in range(3, 13):
                hat.set(x, y, WHITE["s"] if y < 14 else hx("#5e5a50"))
            hat.set(2, y, hx("#5e5a50"))
            hat.set(13, y, hx("#5e5a50"))
        out[name] = hat
    return out

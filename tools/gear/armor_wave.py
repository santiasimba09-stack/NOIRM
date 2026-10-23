#!/usr/bin/env python3
"""The third gear set ("armor wave", round 16): shinobi basics, heavy samurai-style armour, rogue
and assassin gear, village forehead protectors, a medic coat and ceremonial robes.

Same rules as more_gear.py: designs on the vanilla 64x32 armour layout, rendered at 4x by hd.py in
the "raw and tough" ink style (folds, seams, stitches, scratched metal, frayed hems, dirt toward
the feet). All art is original: the village plates carry our own marks (a curling wave, a forked
bolt, a split peak, a sun over dunes), never emblems from the series; crests are our own shapes.

Run on its own (writes only this set's textures, icons and preview):
    python3 tools/gear/armor_wave.py
gen_gear.py also calls it at the end of a full run.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, ARMOR_PARTS, hx, tone, mix, noise  # noqa: E402
import gen_gear as G  # noqa: E402  (registers the shared families first)
import more_gear as M  # noqa: E402
from hd import (Layer, paint_hd, pal, family, SCALE, in_circle, in_ring, in_rrect, in_poly,  # noqa: E402
                on_polyline, engrave, arc, side, cross_collar)

A = ARMOR_PARTS
STEEL, GOLD, WRAPC, STRAP, HOLE, WHITE = G.STEEL, G.GOLD, G.WRAPC, G.STRAP, G.HOLE, M.WHITE
BLOOD, TIE = M.BLOOD, M.TIE

PREVIEW = os.path.join(G.ROOT, "docs/screenshots/gear-armor-wave.png")


def layer():
    return Layer()


def torn(u, base, depth, salt, period=0.85):
    """A ragged hem: the v where the cloth ends at u (teeth of random depth)."""
    i = math.floor(u / period)
    t = (u / period) - i
    a = noise(i, 1, salt)
    b = noise(i + 1, 1, salt)
    tooth = 1 - abs(2 * t - 1)
    return base - depth * (0.25 + 0.75 * (a * (1 - t) + b * t)) * (0.35 + 0.65 * tooth)


def lames(u, v, v0, rows, row_h, lace_h, plate, plate2, lace, lame_w=1.0):
    """Rows of lacquered plates with a laced band under each row (design phase)."""
    r = v - v0
    i = math.floor(r / row_h)
    if r < 0 or i >= rows:
        return None
    if r - i * row_h > row_h - lace_h:
        return lace
    j = math.floor((u + (i % 2) * lame_w * 0.5) / lame_w)
    return plate if j % 2 == 0 else plate2


def lame_cords(u, v, v0, rows, row_h, lace_h, cord, lame_w=1.0):
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
        return cord
    return None


# ---------------------------------------------------------------------------
# Shared colours of this set
# ---------------------------------------------------------------------------

INK = pal("#1d1c21", "#34333b", "#141318", "#0b0b0e")          # ink-black cloth
ASH = pal("#7f7b74", "#a29d94", "#605c56", "#3e3b37")          # ash-grey cloth
BONE = pal("#d8d1c0", "#ece6d6", "#b3ab98", "#80796a")         # bone-white cloth
LACQ = pal("#1e1b1f", "#4a4450", "#141216", "#0a090b", mat="lacquer")  # black lacquer
LACQ2 = mix(LACQ["b"], LACQ["h"], 0.3)
family([LACQ2], "lacquer")
CRIMSON_LACE = hx("#8a1c22")
family([CRIMSON_LACE, tone(CRIMSON_LACE, -0.7)], "cloth")
CRIMSON_CORD = hx("#c0323a")
IRON = pal("#5a5d63", "#8c9097", "#41444a", "#26282c", mat="metal")
IRON2 = mix(IRON["b"], IRON["s"], 0.4)
family([IRON2], "metal")
BLUE_LACE = hx("#27345a")
family([BLUE_LACE], "cloth")
BLUE_CORD = hx("#5a6fa8")
LEATHER = pal("#4a3526", "#634836", "#33241a", "#20160f", mat="leather")
DIRTY_WRAP = tone(WRAPC, -0.9)
family([DIRTY_WRAP], "wrap")
SOLE = G.SOLE


# ---------------------------------------------------------------------------
# Shinobi basics
# ---------------------------------------------------------------------------

def mesh_shirt():
    """A fine steel-mesh undershirt: diamond mesh over dark cloth, sleeves to the elbow."""
    l1 = layer()
    l1.fold = 0.5
    GAP = hx("#202228")
    family([GAP], "cloth")
    WIRE = hx("#6d717a")
    family([WIRE], "print")
    HEM = tone(GAP, 0.6)
    family([HEM], "cloth")

    def mesh(u, v):
        p = 0.62
        a, b = (u + v) % p, (u - v) % p
        return WIRE if a < 0.13 or b < 0.13 else GAP

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return HEM if u < 0.7 or u > W - 0.7 else mesh(u, v)
        if v < 0.7 or v >= 11.3:
            return HEM  # neck and waist bands
        if face in ("right", "left") and abs(u - 2.0) < 0.09:
            return HEM  # side seams
        return mesh(u, v)

    def arm(face, u, v, W, H):
        if face == "bottom" or v > 5.6:
            return None
        if face == "top":
            return mesh(u, v)
        if v >= 5.0:
            return HEM
        return mesh(u, v)

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)
    return l1


def training_shirt():
    """A long-sleeved training shirt: charcoal body, darker raglan sleeves, wrapped wrists."""
    l1 = layer()
    P = pal("#3b4150", "#525a6e", "#2a2f3a", "#191c24")
    S = pal("#2a2e38", "#3c4250", "#1d2028", "#121419")

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return S["b"] if u < 1.6 or u > W - 1.6 else P["h"]
        if face == "front":
            if v < 0.6 and 2.6 < u < 5.4:
                return S["h"]  # the neck band
            if v < 2.6 and abs(u - 4.0) < 0.12 + (2.6 - v) * 0.0:
                return P["d"]  # a short placket
            if in_rrect(u, v, 0.6, 1.6, 2.4, 3.2, 0.2):
                return S["b"]  # a chest patch
        if face in ("front", "back") and (on_polyline(u, v, [(0, 2.4), (1.6, 0)], 0.5) or
                                         on_polyline(u, v, [(8, 2.4), (6.4, 0)], 0.5)):
            return S["b"]  # raglan seams run up to the neck
        if v >= 11.2:
            return S["s"]
        return P["b"]

    def arm(face, u, v, W, H):
        if face == "bottom":
            return WRAPC
        if face == "top":
            return S["b"]
        if v >= 9.6:
            return WRAPC  # wrist wraps
        if 8.9 <= v < 9.6:
            return S["h"]  # the rolled cuff
        if face == "right" and 5.2 <= v < 6.6:
            return P["b"]  # an elbow patch
        return S["b"]

    def marks(face, u, v, W, H):
        if face == "front" and v < 2.6:
            for y in (0.9, 1.7):
                if in_circle(u, v, 4.0, y, 0.12):
                    return BONE["h"]  # buttons
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


def high_collar_jacket():
    """An ash-grey jacket worn open, a high stiff collar, a black belt and long split tails."""
    l1 = layer()
    LIN = INK

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return ASH["h"] if (u < 1.6 or u > W - 1.6 or v < 1.2) else LIN["b"]
        if v < 1.7:
            if face == "front" and 3.3 < u < 4.7:
                return LIN["s"]  # the collar's open front
            return ASH["h"] if v < 1.25 else ASH["s"]  # the tall collar, folded edge
        if 8.6 <= v < 9.5:
            if face == "front" and in_rrect(u, v, 3.2, 8.5, 4.8, 9.6, 0.15):
                return STEEL["b"]
            return LEATHER["d"]  # the belt
        if face == "front":
            gap = 0.75 + max(0.0, v - 9.5) * 0.45  # the open front widens below the belt
            if abs(u - 4.0) < gap:
                return None
            if abs(u - 4.0) < gap + 0.45:
                return LIN["b"]  # the dark lining shows at the edges
        if face == "back" and v >= 9.5 and abs(u - 4.0) < 0.12 + (v - 9.5) * 0.3:
            return None  # the tail splits at the back
        if v >= 11.3:
            return ASH["s"]
        return ASH["b"]

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return ASH["h"]
        if v >= 11.0:
            return LIN["b"]
        if v >= 9.8:
            return ASH["s"]  # wide cuff
        if face == "left":
            return tone(ASH["b"], -0.35)
        return ASH["b"]

    def marks(face, u, v, W, H):
        if face == "front" and 1.8 < v < 8.5:
            for y in (2.6, 4.6, 6.6):
                if in_circle(u, v, 2.75, y, 0.15) or in_circle(u, v, 5.25, y, 0.15):
                    return STEEL["s"]  # toggles along the edges
        if face in ("front", "back", "right", "left") and abs(v - 1.25) < 0.07:
            return ASH["d"]  # the collar's fold
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


def sarashi_wraps():
    """Bandages wound round the chest and both arms, dirtier toward the hands, a blood stain."""
    l1 = layer()
    l1.fold = 0.3

    def body(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        if v < 3.4 or v >= 9.4:
            return None
        if face == "front" and in_circle(u, v, 6.1, 6.6, 0.55 + 0.15 * math.sin(u * 7)):
            return tone(BLOOD, -0.6)  # an old wound seeped through
        return WRAPC

    def arm(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return DIRTY_WRAP
        if 0.4 <= v < 3.0:
            return WRAPC  # upper arm
        if v >= 5.6:
            return DIRTY_WRAP if v >= 10.2 else WRAPC
        return None

    def marks(face, u, v, W, H):
        if face == "back" and on_polyline(u, v, [(5.6, 9.3), (6.2, 10.4), (6.0, 11.4)], 0.32):
            return WRAPC  # a loose end hanging
        return None

    def arm_marks(face, u, v, W, H):
        if face == "right" and on_polyline(u, v, [(2.6, 3.0), (3.0, 4.0), (2.8, 4.6)], 0.28):
            return WRAPC
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], marks, "final")
    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], arm_marks, "final")
    return l1


def bandage_leggings():
    """Dark knee-length shorts, both legs bound in bandages down to the ankles."""
    l2 = layer()
    P = pal("#2c2d34", "#40414b", "#1f2025", "#131317")

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if 9.2 <= v < 9.9:
            return TIE  # a rope belt
        return P["b"]

    def leg(face, u, v, W, H):
        if face == "top":
            return P["b"]
        if face == "bottom" or v >= 11.6:
            return None
        if v < 4.7:
            return P["s"] if v >= 4.2 else P["b"]
        return DIRTY_WRAP if v >= 9.8 else WRAPC

    def marks(face, u, v, W, H):
        if face == "front" and 9.9 <= v < 11.5 and (on_polyline(u, v, [(3.7, 9.9), (3.3, 11.2)], 0.28) or
                                                   on_polyline(u, v, [(4.3, 9.9), (4.8, 11.0)], 0.28)):
            return tone(TIE, 0.4)  # the belt's tied ends
        return None

    def leg_marks(face, u, v, W, H):
        if face in ("right", "left") and 5.4 <= v < 9.6 and on_polyline(side(face, u, W), v, [(0.3, 5.5), (3.7, 7.3)], 0.22):
            return P["d"]  # a binding cord
        return None

    paint_hd(l2, A["body"], waist)
    l2.overlay(A["body"], marks, "final")
    paint_hd(l2, A["leg"], leg)
    l2.overlay(A["leg"], leg_marks, "final")
    return l2


def jika_tabi(base_hex):
    """High split-toe boots up the shin with a row of clasps and a rubber sole."""
    l1 = layer()
    P = pal(base_hex)
    SOLE_C = hx("#2a2622")
    family([SOLE_C], "leather")

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return SOLE_C
        if v < 3.0:
            return None
        if v < 3.5:
            return P["h"]  # the cuff
        if v >= 11.4:
            return SOLE_C
        if face == "front" and abs(u - 1.35) < 0.11 and v > 10.0:
            return P["d"]  # the split toe
        if face in ("right", "left") and side(face, u, W) < 0.9:
            return P["s"]  # the clasp flap
        return P["b"]

    def marks(face, u, v, W, H):
        if face in ("right", "left") and abs(side(face, u, W) - 0.55) < 0.28:
            for i in range(7):
                if abs(v - (4.0 + i * 1.0)) < 0.12:
                    return STEEL["h"]  # the clasps (kohaze)
        if face == "front" and abs(v - 7.2) < 0.08:
            return P["d"]  # the stitched toe-cap seam
        return None

    paint_hd(l1, A["leg"], boot)
    l1.overlay(A["leg"], marks, "final")
    return l1


def wrapped_sandals():
    """Open ninja sandals with the shins bound in bandages and black ties."""
    l1 = layer()
    S = pal("#2b2b30", "#3e3e46", "#1d1d21", "#111114")

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return SOLE
        if v < 4.4:
            return None
        if v >= 11.4:
            return S["d"]  # the sole
        if v < 9.4:
            return WRAPC if v < 8.6 else DIRTY_WRAP  # bandaged shin
        if face == "front":
            if 10.0 <= v < 10.7:
                return S["h"]  # toe strap
            return None  # open toes
        if face == "back" or side(face, u, W) < 1.9:
            return S["b"]  # heel cup
        if on_polyline(u if face == "right" else W - u, v, [(1.9, 9.6), (3.6, 10.3)], 0.5):
            return S["h"]
        return None

    def marks(face, u, v, W, H):
        if 4.6 <= v < 9.2 and face in ("front", "right", "left", "back"):
            k = side(face, u, W) if face in ("right", "left") else u
            t = (v - 4.6) % 1.5
            if abs(k - (t / 1.5) * W) < 0.18 or abs(k - (1 - t / 1.5) * W) < 0.18:
                return S["s"]  # the crossed ties
        return None

    paint_hd(l1, A["leg"], boot)
    l1.overlay(A["leg"], marks, "final")
    return l1


# ---------------------------------------------------------------------------
# Heavy armour: cuirass, pauldrons over a robe, war helmet with a face guard, armoured skirt, greaves
# ---------------------------------------------------------------------------

def samurai_cuirass():
    """A black-lacquered cuirass laced in crimson, big shoulder plates and armoured sleeves."""
    l1 = layer()
    l1.fold = 0.4
    SLEEVE = INK

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return LACQ["b"] if 1 <= u <= W - 1 else SLEEVE["b"]
        if v < 0.8:
            return SLEEVE["s"]  # the collar of the robe under it
        if face in ("front", "back"):
            if v < 1.5:
                return CRIMSON_LACE  # the top cord
            if v < 6.4:
                return LACQ["b"]  # the solid breastplate
        else:
            if v < 6.4:
                r = lames(u, v, 0.8, 3, 1.85, 0.4, LACQ["b"], LACQ2, CRIMSON_LACE)
                return r
        if v < 9.4:
            return lames(u, v, 6.4, 2, 1.5, 0.4, LACQ["b"], LACQ2, CRIMSON_LACE)
        # short tassets
        n = 3 if face in ("front", "back") else 2
        pw = W / n
        if abs(u - round(u / pw) * pw) < 0.12 and 0 < round(u / pw) < n:
            return SLEEVE["d"]
        if v < 9.8:
            return CRIMSON_LACE
        return lames(u, v, 9.8, 1, 2.2, 0.35, LACQ["b"], LACQ2, CRIMSON_LACE, 2.0) or LACQ["b"]

    def body_marks(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        if face == "front":
            e = engrave(u, v, arc(4.0, 3.9, 1.15, 200, 340, 16), 0.2, GOLD["s"], GOLD["h"])
            if e:
                return e  # our crest: a gold crescent with a point
            if on_polyline(u, v, [(4.0, 3.0), (4.0, 4.7)], 0.2):
                return GOLD["h"]
        if face == "back":
            if in_circle(u, v, 4.0, 3.2, 0.42):
                return CRIMSON_CORD  # the knot (our bow)
            if on_polyline(u, v, [(4.0, 3.2), (2.9, 4.6)], 0.3) or on_polyline(u, v, [(4.0, 3.2), (5.1, 4.6)], 0.3):
                return CRIMSON_CORD
        if face in ("front", "back") and v < 6.4:
            for x in (0.7, 7.3):
                for y in (2.0, 5.6):
                    if in_circle(u, v, x, y, 0.14):
                        return GOLD["b"]  # rivets
        c = lame_cords(u, v, 6.4, 2, 1.5, 0.4, CRIMSON_CORD)
        if c:
            return c
        if face in ("right", "left") and v < 6.4:
            return lame_cords(u, v, 0.8, 3, 1.85, 0.4, CRIMSON_CORD)
        return None

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return LACQ["b"]
        if v < 5.0 and face != "left":
            return lames(u, v, 0.0, 3, 1.67, 0.38, LACQ["b"], LACQ2, CRIMSON_LACE)  # the sode
        if v < 5.0:
            return SLEEVE["b"]
        if v < 10.2:
            # the armoured sleeve: small iron plates on black cloth, chain between
            if face != "left" and (math.floor(v / 1.1) % 2 == 0) and abs((u % 1.33) - 0.66) < 0.42:
                return IRON["b"]
            return SLEEVE["s"]
        if v < 11.6:
            return IRON["b"] if face in ("back", "right") else SLEEVE["b"]  # hand plate
        return None

    def arm_marks(face, u, v, W, H):
        if face in ("top", "bottom", "left"):
            return None
        return lame_cords(u, v, 0.0, 3, 1.67, 0.38, CRIMSON_CORD)

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], arm_marks, "final")
    return l1


def sode_pauldrons():
    """Big iron-grey shoulder plates laced in blue, worn over a dark indigo battle robe."""
    l1 = layer()
    R = pal("#262c44", "#3a4262", "#1a1f30", "#10131d")
    UNDER = BONE

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return R["b"]
        if 7.4 <= v < 8.6:
            return LEATHER["s"] if face != "front" or not in_rrect(u, v, 3.3, 7.3, 4.7, 8.7, 0.15) else IRON["h"]
        if face == "front":
            c = cross_collar(u, v, 0.8)
            if c == "open":
                return UNDER["b"]
            if c == "trim":
                return R["h"]
            if c == "edge":
                return R["d"]
        if face == "back" and (on_polyline(u, v, [(0.4, 0.2), (7.6, 6.8)], 0.6) or on_polyline(u, v, [(7.6, 0.2), (0.4, 6.8)], 0.6)):
            return BLUE_LACE  # cords holding the pauldrons
        if v >= 11.3:
            return R["s"]
        return R["b"]

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return IRON["b"]
        if v < 5.6 and face != "left":
            return lames(u, v, 0.0, 3, 1.87, 0.42, IRON["b"], IRON2, BLUE_LACE, 1.33)
        if v > 9.2:
            return None
        if v >= 8.6:
            return R["s"]
        return tone(R["b"], -0.35) if face == "left" else R["b"]

    def arm_marks(face, u, v, W, H):
        if face in ("top", "bottom", "left") or v >= 5.6:
            return None
        return lame_cords(u, v, 0.0, 3, 1.87, 0.42, BLUE_CORD, 1.33)

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)
    l1.overlay(A["arm"], arm_marks, "final")
    return l1


def kabuto():
    """A riveted iron war helmet: twin gold horns, a laced neck guard and an iron face guard."""
    cv = layer()

    def hat(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            r = math.hypot(u - 4.0, v - 4.0)
            if r < 0.7:
                return GOLD["b"]  # the crown ring
            a = math.atan2(v - 4.0, u - 4.0)
            return LACQ["h"] if abs(math.sin(a * 4)) < 0.12 else LACQ["b"]  # ridged plates
        if face == "front":
            if v < 2.0:
                return LACQ["b"]
            if v < 2.7:
                return LACQ2  # the brim
            if (u < 0.8 or u > 7.2) and v < 4.4:
                return LACQ["b"]  # turned-back flaps
            return None
        if v < 2.3:
            return LACQ["b"]
        # the neck guard flaring down behind and at the sides
        edge = 7.6 if face == "back" else (7.0 if side(face, u, W) < 5 else 4.6)
        if v > edge:
            return None
        return lames(u, v, 2.3, 3, 1.75, 0.38, LACQ["b"], LACQ2, CRIMSON_LACE, 1.6)

    def hat_marks(face, u, v, W, H):
        if face == "front":
            if on_polyline(u, v, [(4.0, 1.9), (3.0, 1.2), (2.0, 0.6), (1.0, 0.25)], 0.28) or \
                    on_polyline(u, v, [(4.0, 1.9), (5.0, 1.2), (6.0, 0.6), (7.0, 0.25)], 0.28):
                return GOLD["h"]  # our twin horns
            if in_circle(u, v, 4.0, 1.9, 0.38):
                return GOLD["b"]
            if 2.0 <= v < 2.7 and abs(v - 2.05) < 0.07:
                return GOLD["s"]
            return None
        if face in ("right", "left", "back") and v >= 2.3:
            return lame_cords(u, v, 2.3, 3, 1.75, 0.38, CRIMSON_CORD, 1.6)
        if face in ("right", "left", "back") and v < 2.3:
            for x in (1.0, 3.0, 5.0, 7.0):
                if in_circle(u, v, x, 1.2, 0.13):
                    return GOLD["s"]  # rivets round the bowl
        return None

    def head(face, u, v, W, H):
        if face == "front":
            if v < 4.4:
                return None
            if v >= 7.0:
                return IRON2 if math.floor((v - 7.0) / 0.5) % 2 == 0 else IRON["s"]  # throat guard
            if abs(u - 4.0) < 0.55 and v < 5.9:
                return IRON["h"]  # the nose
            return IRON["b"]
        if face in ("right", "left"):
            if side(face, u, W) >= 5.4 and v >= 4.4:
                return IRON["s"]
            return STRAP if 4.9 <= v < 5.5 else None
        if face == "back":
            return STRAP if 4.9 <= v < 5.5 else None
        return None

    def head_marks(face, u, v, W, H):
        if face != "front" or v < 4.4:
            return None
        if on_polyline(u, v, [(2.6, 6.25), (4.0, 6.05), (5.4, 6.25)], 0.22):
            return HOLE  # the mouth slit
        if on_polyline(u, v, [(1.0, 4.8), (2.2, 5.4)], 0.18) or on_polyline(u, v, [(7.0, 4.8), (5.8, 5.4)], 0.18):
            return IRON["d"]  # hard cheek lines
        if on_polyline(u, v, [(2.9, 5.65), (3.5, 5.85)], 0.16) or on_polyline(u, v, [(5.1, 5.65), (4.5, 5.85)], 0.16):
            return CRIMSON_CORD  # a red-lacquered grimace
        return None

    paint_hd(cv, A["hat"], hat)
    cv.overlay(A["hat"], hat_marks, "final")
    paint_hd(cv, A["head"], head)
    cv.overlay(A["head"], head_marks, "final")
    return cv


def kusazuri():
    """An armoured skirt: lacquered panels over the hips and thighs, a dark pleated hakama below."""
    l2 = layer()
    H_ = pal("#24232a", "#36353e", "#18171c", "#0f0f12")

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if v < 9.6:
            return CRIMSON_LACE  # the cord the panels hang from
        return lames(u, v, 9.6, 1, 2.4, 0.4, LACQ["b"], LACQ2, CRIMSON_LACE, 2.0)

    def leg(face, u, v, W, H):
        if face == "top":
            return H_["b"]
        if face == "bottom":
            return None
        if v < 5.6 and face != "left":
            if abs(u - 2.0) < 0.1 and face in ("front", "back"):
                return H_["d"]  # the split between panels
            return lames(u, v, 0.0, 3, 1.87, 0.4, LACQ["b"], LACQ2, CRIMSON_LACE, 2.0)
        if v >= 11.5:
            return H_["d"]
        if face in ("front", "back") and abs(u - round(u)) < 0.07 and 0 < round(u) < 4:
            return H_["d"]  # pleats
        return H_["b"]

    def marks(face, u, v, W, H):
        if face in ("top", "bottom", "left") or v >= 5.6:
            return None
        return lame_cords(u, v, 0.0, 3, 1.87, 0.4, CRIMSON_CORD, 2.0)

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    l2.overlay(A["leg"], marks, "final")
    return l2


def samurai_greaves():
    """Lacquered shin splints with gold hinges, a knee plate and black tabi."""
    l1 = layer()

    def boot(face, u, v, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return SOLE
        if v < 2.2:
            return None
        if v >= 10.2:
            if face == "front" and abs(u - 1.35) < 0.12 and v > 10.6:
                return INK["d"]
            return INK["b"] if v < 11.5 else SOLE
        if v < 3.9:
            if face == "front" and in_rrect(u, v, 0.4, 2.2, 3.6, 3.9, 0.6):
                return LACQ2  # knee plate
            return INK["s"] if face != "back" else None
        if face == "front" or (face in ("right", "left") and side(face, u, W) >= 1.6):
            k = u if face == "front" else side(face, u, W) - 1.6
            if face == "front" and (abs(u - 1.33) < 0.09 or abs(u - 2.67) < 0.09):
                return GOLD["s"]  # hinges between the splints
            return LACQ["b"] if (face == "front" and 1.33 < u < 2.67) or k > 1.2 else LACQ2
        return INK["s"]  # tied at the back

    def marks(face, u, v, W, H):
        if face == "front":
            for y in (4.4, 9.6):
                for x in (0.6, 3.4):
                    if in_circle(u, v, x, y, 0.15):
                        return GOLD["b"]
        if face == "back" and 4.2 <= v < 9.8:
            a = (v - 4.2) % 1.4 / 1.4
            if abs(u / W - a) < 0.045 or abs(u / W - (1 - a)) < 0.045:
                return CRIMSON_LACE  # laced shut at the back
        return None

    paint_hd(l1, A["leg"], boot)
    l1.overlay(A["leg"], marks, "final")
    return l1


# ---------------------------------------------------------------------------
# Rogue and assassin gear
# ---------------------------------------------------------------------------

RAG = pal("#3b3631", "#544d45", "#2a2622", "#181613")  # dirty road-worn cloth


def tattered_hood():
    """A ragged hood: the brim throws the eyes into shadow, torn ends at the neck."""
    cv = layer()
    SHADOW = hx("#09090b")
    family([SHADOW], "void")

    def hat(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RAG["b"]
        if face == "front":
            if v < 1.5:
                return RAG["h"] if v < 0.5 else RAG["b"]  # the brim
            if v < 1.95:
                return RAG["s"]
            if u < 0.85 or u > 7.15:
                return RAG["s"] if v < torn(u, 8.0, 1.2, 31) else None
            return None
        if v > torn(u, 8.0, 1.8, 37 + len(face)):
            return None
        return RAG["b"]

    def hat_marks(face, u, v, W, H):
        if face == "back" and (on_polyline(u, v, [(1.8, 0.4), (2.6, 3.0), (2.2, 6.0)], 0.2) or
                               on_polyline(u, v, [(6.0, 0.6), (5.4, 3.4), (5.9, 6.4)], 0.2)):
            return RAG["d"]  # folds falling down the back
        if face in ("right", "left") and on_polyline(side(face, u, W), v, [(2.0, 0.8), (4.4, 3.6), (5.0, 6.8)], 0.2):
            return RAG["d"]
        if face == "back" and in_rrect(u, v, 4.4, 3.6, 5.6, 4.8, 0.1):
            return mix(RAG["b"], BONE["b"], 0.35)  # a sewn patch
        return None

    def head(face, u, v, W, H):
        if face == "front" and v < 2.4 + 0.25 * math.sin(u * 3.1):
            return SHADOW  # the hood's shadow over the brow
        return None

    paint_hd(cv, A["hat"], hat)
    cv.overlay(A["hat"], hat_marks, "final")
    paint_hd(cv, A["head"], head)
    return cv


def tattered_cloak():
    """A long road cloak: torn hem, rips, patches, a rope tie at the throat."""
    l1 = layer()
    l1.fold = 1.25
    PATCH = mix(RAG["b"], hx("#5a4a38"), 0.6)
    family([PATCH], "cloth")

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RAG["h"]
        if v > torn(u, 12.0, 1.9, 41 + len(face) * 3):
            return None
        if face == "front":
            if v < 1.2:
                return RAG["h"]  # bunched at the neck
            if v >= 6.0 and abs(u - 4.0) < (v - 6.0) * 0.35 + 0.05:
                return None  # falls open
        if face == "back" and in_poly(u, v, [(5.2, 4.2), (5.9, 4.0), (5.5, 6.0)]):
            return None  # a rip
        if (face == "back" and in_rrect(u, v, 1.2, 6.8, 2.8, 8.4, 0.15)) or \
                (face == "front" and in_rrect(u, v, 0.6, 3.0, 2.0, 4.4, 0.15)):
            return PATCH
        return RAG["b"]

    def body_marks(face, u, v, W, H):
        if face == "front":
            if in_circle(u, v, 4.0, 1.0, 0.36):
                return tone(TIE, 0.4)  # the knot
            if on_polyline(u, v, [(4.0, 1.0), (3.6, 2.6)], 0.22) or on_polyline(u, v, [(4.0, 1.0), (4.5, 2.4)], 0.22):
                return TIE
        if face in ("front", "back") and in_rrect(u, v, 0.6, 3.0, 2.0, 4.4, 0.15) and \
                not in_rrect(u, v, 0.75, 3.15, 1.85, 4.25, 0.1) and int(u * 8 + v * 8) % 3 == 0:
            return BONE["s"]  # coarse stitches round the patch
        return None

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RAG["h"]
        if v > torn(u, 11.0, 2.2, 53 + len(face)):
            return None
        if face == "left":
            return RAG["s"]
        return RAG["b"]

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


def half_mask():
    """A dark iron half-mask over nose and mouth with breathing slits, strapped behind."""
    cv = layer()
    P = IRON

    def head(face, u, v, W, H):
        if face == "front":
            if v < 4.5 + 0.35 * (1 - abs(u - 4.0) / 4.0):
                return None
            if v > 7.6 - 0.9 * (abs(u - 4.0) / 4.0) ** 2:
                return None
            return P["h"] if abs(u - 4.0) < 0.5 and v < 5.8 else P["b"]
        if face in ("right", "left"):
            s = side(face, u, W)
            if s >= 6.0 and 4.6 <= v <= 7.2:
                return P["s"]
            return STRAP if 5.2 <= v < 5.8 else None
        if face == "back":
            if 5.2 <= v < 5.8:
                return STRAP
            if in_rrect(u, v, 3.3, 5.0, 4.7, 6.0, 0.2):
                return tone(STRAP, 0.5)
        return None

    def marks(face, u, v, W, H):
        if face != "front":
            return None
        for i in range(4):
            y = 6.15 + i * 0.32
            if abs(v - y) < 0.07 and abs(u - 4.0) < 1.6 - i * 0.25:
                return HOLE  # breathing slits
        for x in (1.0, 7.0):
            if in_circle(u, v, x, 5.6, 0.15):
                return P["d"]
        return None

    paint_hd(cv, A["head"], head)
    cv.overlay(A["head"], marks, "final")
    return cv


def assassin_suit():
    """A tight black suit with quilted dark plates, crossing straps and a blood-red sash."""
    l1 = layer()
    l1.fold = 0.6
    P = INK
    PAD = pal("#2c2b31", "#403f47", "#1e1d22", "#121115")

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["b"]
        if v < 0.8:
            return PAD["h"]  # the high neck
        if 8.5 <= v < 9.6:
            return BLOOD  # the sash
        if face in ("front", "back") and (on_polyline(u, v, [(0.5, 0.8), (7.5, 8.5)], 0.6) or
                                         on_polyline(u, v, [(7.5, 0.8), (0.5, 8.5)], 0.6)):
            return STRAP
        if face in ("front", "back") and in_rrect(u, v, 0.7, 1.4, 7.3, 7.6, 0.5):
            return PAD["b"] if math.floor((v - 1.4) / 1.25) % 2 == 0 else PAD["s"]  # quilted plates
        if v >= 11.3:
            return P["s"]
        return P["b"]

    def body_marks(face, u, v, W, H):
        if face == "front" and in_circle(u, v, 4.0, 4.65, 0.3):
            return STEEL["s"]  # the straps' ring
        if face == "back" and face == "back" and 9.6 <= v < 11.4 and (on_polyline(u, v, [(5.2, 9.4), (5.6, 11.4)], 0.32) or
                                                                     on_polyline(u, v, [(5.6, 9.4), (6.3, 11.0)], 0.3)):
            return BLOOD  # the sash's tails
        return None

    def arm(face, u, v, W, H):
        if face == "bottom":
            return P["s"]
        if face == "top":
            return PAD["b"]
        if v < 2.2 and face != "left":
            return PAD["b"]  # shoulder pad
        if 6.6 <= v < 10.4 and face != "left":
            return PAD["s"] if 1.2 < u < 2.8 or face == "back" else P["b"]  # forearm plate
        return P["b"]

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


def assassin_pants():
    """Tight black trousers, dark leather knee pads, dark wraps down the shins."""
    l2 = layer()
    P = INK
    DARK_WRAP = hx("#3a3a40")
    family([DARK_WRAP, tone(DARK_WRAP, -0.6)], "wrap")

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if 9.0 <= v < 9.8:
            return BLOOD
        return P["b"]

    def leg(face, u, v, W, H):
        if face == "top":
            return P["b"]
        if face == "bottom" or v >= 11.5:
            return None
        if v >= 7.3:
            return DARK_WRAP
        if face == "front" and in_rrect(u, v, 0.4, 4.6, 3.6, 6.8, 0.6):
            return LEATHER["d"]
        if face == "right" and in_rrect(u, v, 0.6, 1.2, 3.4, 3.6, 0.25):
            return mix(P["b"], P["h"], 0.5)  # a thigh pouch
        return P["b"]

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    return l2


def bandolier():
    """A leather bandolier across a bare chest with kunai pouches, a belt and an arm strap."""
    l1 = layer()
    POUCH = pal("#3a2a1e", "#56402e", "#281c14", "#170f0a", mat="leather")

    def strap(face, u, v):
        if face == "front":
            return on_polyline(u, v, [(7.4, -0.2), (0.6, 9.0)], 1.05)
        if face == "back":
            return on_polyline(u, v, [(0.6, -0.2), (7.4, 9.0)], 1.05)
        return False

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return LEATHER["b"] if 0.2 < u < 1.6 else None
        if 9.0 <= v < 10.1:
            return LEATHER["s"]  # the belt
        if face == "front" and 10.1 <= v < 11.6 and (in_rrect(u, v, 0.4, 10.1, 2.0, 11.6, 0.2) or
                                                   in_rrect(u, v, 6.0, 10.1, 7.6, 11.6, 0.2)):
            return POUCH["b"]  # belt pouches
        if strap(face, u, v):
            return LEATHER["b"]
        if face in ("right", "left") and side(face, u, W) > 2.5 and v < 0.9 and face == "right":
            return LEATHER["b"]
        return None

    def body_marks(face, u, v, W, H):
        if face == "front":
            for t in (0.22, 0.42, 0.62, 0.82):
                cx, cy = 7.4 + (0.6 - 7.4) * t, -0.2 + 9.2 * t
                if in_rrect(u, v, cx - 0.45, cy - 0.35, cx + 0.45, cy + 0.45, 0.1):
                    return POUCH["h"] if v < cy - 0.1 else POUCH["s"]  # pouches on the strap
                if in_ring(u, v, cx + 0.15, cy - 0.75, 0.12, 0.26):
                    return STEEL["h"]  # kunai rings poking out
                if on_polyline(u, v, [(cx + 0.1, cy - 0.5), (cx, cy - 0.3)], 0.18):
                    return WRAPC
        if face == "front" and in_rrect(u, v, 3.4, 9.0, 4.6, 10.1, 0.12):
            return STEEL["b"] if not in_rrect(u, v, 3.65, 9.25, 4.35, 9.85, 0.08) else LEATHER["d"]
        if face == "back" and on_polyline(u, v, [(1.6, 1.2), (2.4, 1.7)], 0.2):
            return STEEL["s"]
        return None

    def arm(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        if 2.2 <= v < 3.0:
            return LEATHER["b"]  # an arm strap
        if face == "right" and in_rrect(u, v, 1.0, 3.0, 3.0, 4.6, 0.2):
            return POUCH["b"]
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Village forehead protectors: our own marks per village look
# ---------------------------------------------------------------------------

def _wave():
    return arc(4.3, 2.65, 0.75, 200, 520, 30) + [(3.7, 3.2), (2.4, 3.3), (1.8, 3.0)]


VILLAGE_MARKS = {
    # name: (band cloth, mark polylines)
    "mist": ("#35535c", [_wave()]),
    "cloud": ("#c9c4b6", [[(4.6, 1.5), (3.4, 2.55), (4.6, 2.45), (3.4, 3.6)], [(4.6, 2.45), (5.6, 3.2)]]),
    "stone": ("#5a4030", [[(1.9, 3.55), (4.0, 1.4), (6.1, 3.55)], [(4.0, 1.4), (3.7, 2.4), (4.3, 2.9), (4.0, 3.55)]]),
    "sand": ("#a8875a", [arc(4.0, 2.55, 0.5, 0, 360, 24), [(1.7, 3.55), (3.0, 3.15), (4.4, 3.5)], [(3.6, 3.6), (5.0, 3.2), (6.3, 3.55)]]),
}


def village_band(cloth_hex, marks):
    C = pal(cloth_hex)
    cv = layer()

    def fn(face, u, v, W, H):
        if face in ("top", "bottom"):
            return None
        if face == "front":
            p = G.protector_plate(u, v)
            if p is not None:
                return p
        if face == "back":
            k = G.band_knot(u, v, C)
            if k is not None:
                return k
        return G.band_cloth(face, u, v, C)

    def engr(face, u, v, W, H):
        if face != "front" or not in_rrect(u, v, 1.0, 1.05, 7.0, 4.05, 0.5):
            return None
        for rx, ry in G.RIVETS:
            if in_circle(u, v, rx - 0.09, ry - 0.09, 0.09):
                return STEEL["h"]
        for pts in marks:
            e = engrave(u, v, pts, 0.24, STEEL["d"], STEEL["h"])
            if e:
                return e
        return None

    paint_hd(cv, A["head"], fn)
    cv.overlay(A["head"], engr, "final")
    return cv


# ---------------------------------------------------------------------------
# Medic coat
# ---------------------------------------------------------------------------

def medic_coat():
    """A long bone-white coat: our medic mark (a red ring round a leaf) on the back, scroll
    pockets, a dark belt and sleeves rolled to the forearm."""
    l1 = layer()
    W_ = BONE
    MARK = hx("#a8262a")
    family([MARK], "print")
    LEAF = hx("#4e7a4a")
    family([LEAF], "print")

    def leaf(u, v, cx, cy, r):
        dx, dy = (u - cx) / (r * 0.45), (v - cy) / r
        return dx * dx + dy * dy <= 1.0

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return W_["h"]
        if 7.8 <= v < 8.6:
            return INK["b"]  # belt
        if face == "front":
            if v < 3.4 and abs(u - 4.0) < (3.4 - v) * 0.55:
                return None  # open neck
            if abs(u - 4.0) < 0.12 and v >= 3.4:
                return W_["d"]
            if in_rrect(u, v, 0.5, 9.0, 2.6, 11.0, 0.2) or in_rrect(u, v, 5.4, 9.0, 7.5, 11.0, 0.2):
                return W_["h"] if v < 9.5 else W_["s"]  # pockets
        if face == "back":
            if in_ring(u, v, 4.0, 3.7, 1.3, 1.75):
                return MARK
            if leaf(u, v, 4.0, 3.7, 0.95):
                return LEAF
        if face == "right" and in_ring(u, v, 2.0, 2.6, 0.55, 0.85):
            return MARK
        if v >= 11.3:
            return W_["s"]
        return W_["b"]

    def body_marks(face, u, v, W, H):
        if face == "back" and on_polyline(u, v, [(4.0, 2.9), (4.0, 4.6)], 0.12):
            return tone(LEAF, -1.2)  # the leaf's vein
        if face == "front" and in_rrect(u, v, 0.8, 8.6, 1.4, 9.4, 0.15):
            return tone(WRAPC, 0.2)  # a scroll in the pocket
        return None

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return W_["h"]
        if v >= 7.6:
            return WRAPC if v < 10.0 else None  # wrapped forearms
        if v >= 6.6:
            return W_["h"]  # rolled sleeve
        if face == "right" and in_ring(u, v, 2.0, 2.4, 0.55, 0.85):
            return MARK
        return W_["b"]

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Ceremonial: formal clan robe + striped hakama, battle kimono with an armoured obi + its hakama
# ---------------------------------------------------------------------------

def crest(u, v, cx, cy, r):
    """Our family crest: a ring round a diamond (in white)."""
    if in_ring(u, v, cx, cy, r * 0.78, r):
        return True
    return abs(u - cx) + abs(v - cy) <= r * 0.55


def formal_robe():
    """A black formal robe: white crests at chest, back and sleeves, a white under-collar,
    a grey sash and wide sleeves."""
    l1, l2 = layer(), layer()
    l1.fold = 1.1
    P = pal("#17161b", "#2c2b33", "#0f0f12", "#08080a")
    CREST_C = hx("#e8e3d6")
    family([CREST_C], "print")
    SASH = pal("#6e6c74")

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return BONE["b"] if u < 0.6 or u > W - 0.6 else P["b"]
        if 7.0 <= v < 8.6:
            return SASH["b"] if not 7.7 <= v < 7.9 else SASH["d"]
        if face == "front":
            c = cross_collar(u, v, 0.9)
            if c == "open":
                return BONE["s"]
            if c == "trim":
                return BONE["b"]
            if c == "edge":
                return P["d"]
            if crest(u, v, 1.3, 2.0, 0.7) or crest(u, v, 6.7, 2.0, 0.7):
                return CREST_C
        if face == "back" and crest(u, v, 4.0, 2.0, 0.85):
            return CREST_C
        if v >= 11.4:
            return P["s"]
        return P["b"]

    def arm(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["b"]
        if v >= 11.0:
            return P["d"]
        if face == "back" and crest(u, v, 2.0, 2.2, 0.65):
            return CREST_C
        return tone(P["b"], -0.4) if face == "left" else P["b"]

    paint_hd(l1, A["body"], body)
    paint_hd(l1, A["arm"], arm)

    G_ = pal("#55545c", "#6e6d76", "#3c3b42", "#26252a")

    def stripe(u):
        return G_["b"] if math.floor(u / 0.5) % 2 == 0 else G_["s"]

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if v < 9.6:
            return P["b"]
        return stripe(u)

    def leg(face, u, v, W, H):
        if face == "top":
            return G_["b"]
        if face == "bottom":
            return None
        if v >= 11.55:
            return G_["d"]
        if face in ("front", "back") and abs(u - round(u)) < 0.07 and 0 < round(u) < 4:
            return G_["d"]
        return stripe(u)

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    return l1, l2


def battle_kimono():
    """A charcoal battle kimono, blood-red collar, sleeves tied back with a crossed cord,
    forearms wrapped, and an armoured obi of riveted steel plates."""
    l1, l2 = layer(), layer()
    P = pal("#2c282c", "#423c42", "#1e1b1e", "#121013")
    RED = pal("#7c1a20", "#a42830", "#58121a", "#360a0e")
    OBI = pal("#8a8f97", "#b4b9c0", "#62666d", "#3e4146", mat="metal")

    def body(face, u, v, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return RED["b"] if u < 0.6 or u > W - 0.6 else P["b"]
        if 6.5 <= v < 9.1:
            if 6.5 <= v < 6.85 or 8.75 <= v < 9.1:
                return RED["s"]  # the obi's cloth edges
            seg = 2.0 if face in ("front", "back") else 2.0
            return OBI["b"] if abs((u % seg) - seg / 2) < seg / 2 - 0.08 else OBI["d"]
        if face == "front":
            c = cross_collar(u, v, 0.85)
            if c == "open":
                return None
            if c == "trim":
                return RED["b"]
            if c == "edge":
                return P["d"]
        if face == "back" and (on_polyline(u, v, [(0.3, 0.2), (7.7, 6.4)], 0.42) or
                               on_polyline(u, v, [(7.7, 0.2), (0.3, 6.4)], 0.42)):
            return RED["h"]  # the crossed tasuki cord
        if v >= 11.3:
            return P["s"]
        return P["b"]

    def body_marks(face, u, v, W, H):
        if 6.85 <= v < 8.75 and face in ("front", "back", "right", "left"):
            seg = 2.0
            for k in range(5):
                cx = k * seg + 0.35
                for cy in (7.25, 8.35):
                    if in_circle(u, v, cx, cy, 0.13) or in_circle(u, v, cx + seg - 0.7, cy, 0.13):
                        return OBI["s"]
        return None

    def arm(face, u, v, W, H):
        if face == "bottom":
            return WRAPC
        if face == "top":
            return P["b"]
        if v < 4.4:
            if v >= 3.6:
                return RED["h"]  # the cord holding the sleeve back
            return P["b"] if face != "left" else tone(P["b"], -0.4)
        if v < 5.4:
            return P["s"]  # the gathered sleeve
        if v >= 6.6:
            return WRAPC if v < 10.4 else DIRTY_WRAP
        return None

    paint_hd(l1, A["body"], body)
    l1.overlay(A["body"], body_marks, "final")
    paint_hd(l1, A["arm"], arm)

    HK = pal("#232227", "#35343b", "#18171b", "#0e0e11")

    def waist(face, u, v, W, H):
        if face in ("top", "bottom") or v < 9:
            return None
        if face == "front" and (in_poly(u, v, [(4, 9.7), (2.7, 9.0), (2.8, 10.5)]) or
                                in_poly(u, v, [(4, 9.7), (5.3, 9.0), (5.2, 10.5)])):
            return RED["b"]  # the tie's bow
        if v < 9.6:
            return RED["s"]
        return HK["b"]

    def leg(face, u, v, W, H):
        if face == "top":
            return HK["b"]
        if face == "bottom" or v >= 11.6:
            return None
        if v >= 7.4:
            return INK["s"] if v < 11.0 else INK["d"]  # black leg wraps (kyahan)
        if face in ("front", "back") and abs(u - round(u)) < 0.07 and 0 < round(u) < 4:
            return HK["d"]
        return HK["b"]

    def leg_marks(face, u, v, W, H):
        if 7.6 <= v < 11.0 and face in ("right", "left") and abs(side(face, u, W) - 0.5) < 0.08:
            return RED["s"]  # the wraps' tie
        return None

    paint_hd(l2, A["body"], waist)
    paint_hd(l2, A["leg"], leg)
    l2.overlay(A["leg"], leg_marks, "final")
    return l1, l2


# ---------------------------------------------------------------------------
# All the textures. Returns {texture: (layer1 or None, layer2 or None)}
# ---------------------------------------------------------------------------

def textures():
    tex = {
        "mesh_shirt": (mesh_shirt(), None),
        "training_shirt": (training_shirt(), None),
        "high_collar_jacket": (high_collar_jacket(), None),
        "sarashi_wraps": (sarashi_wraps(), None),
        "bandage_leggings": (None, bandage_leggings()),
        "jika_tabi": (jika_tabi("#1c1c21"), None),
        "jika_tabi_ash": (jika_tabi("#6c6964"), None),
        "wrapped_sandals": (wrapped_sandals(), None),
        "samurai_cuirass": (samurai_cuirass(), None),
        "sode_pauldrons": (sode_pauldrons(), None),
        "kabuto": (kabuto(), None),
        "kusazuri": (None, kusazuri()),
        "samurai_greaves": (samurai_greaves(), None),
        "tattered_hood": (tattered_hood(), None),
        "tattered_cloak": (tattered_cloak(), None),
        "half_mask": (half_mask(), None),
        "assassin_suit": (assassin_suit(), None),
        "assassin_pants": (None, assassin_pants()),
        "bandolier": (bandolier(), None),
        "medic_coat": (medic_coat(), None),
    }
    for name, (cloth, marks) in VILLAGE_MARKS.items():
        tex["forehead_protector_" + name] = (village_band(cloth, marks), None)
    tex["formal_robe"] = formal_robe()
    tex["battle_kimono"] = battle_kimono()
    return tex


# ---------------------------------------------------------------------------
# Icons (16x16)
# ---------------------------------------------------------------------------

HOOD_ICON = [
    "................",
    ".....oooooo.....",
    "....ohhhhbbo....",
    "...ohhbbbbbso...",
    "..ohhbbbbbbbso..",
    "..ohbKKKKKKbso..",
    "..ohbKKKKKKbso..",
    "..ohbKKKKKKbso..",
    "..ohbbKKKKbbso..",
    "..ohbbbbbbbbso..",
    ".ohbbbbbbbbbbso.",
    ".obbbbbbbbbbbbo.",
    ".o.obo.obbo.oo..",
    "................",
    "................",
    "................",
]
HELM_ICON = [
    "...g........g...",
    "....g......g....",
    ".....g.oo.g.....",
    "......ohhbo.....",
    ".....ohhbbbo....",
    "....ohhbbbbbo...",
    "...ohbbbbbbbbo..",
    "..oooooooooooo..",
    "..olll.ii.lllo..",
    "..olll.ii.lllo..",
    "..olllmmmmlllo..",
    "..ollmmmmmmllo..",
    "...oo.mmmm.oo...",
    "......oooo......",
    "................",
    "................",
]
HALF_MASK_ICON = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..oooooooooooo..",
    ".ohhhbbbbbbbbso.",
    ".ohbbbbbbbbbbso.",
    "..obbkkkkkkbbo..",
    "..obbbkkkkbbbo..",
    "...obbkkkkbbo...",
    "....obbbbbbo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]
STRAP_ICON = [
    "................",
    "...........oo...",
    "..........obbo..",
    ".........obpbo..",
    "........obbbo...",
    ".......obpbo....",
    "......obbbo.....",
    ".....obpbo......",
    "....obbbo.......",
    "...obpbo........",
    "..obbbo.........",
    ".obbbo..........",
    ".oooo...........",
    "................",
    "................",
    "................",
]


def icons():
    I = M.icon
    cols = M.cols
    CHEST, LEGS, BOOTS, BAND = G.CHEST, G.LEGS, G.BOOTS, G.BAND
    out = {}

    gap = hx("#202228")
    out["mesh_shirt"] = I(CHEST, cols(pal("#3a3d44"), hx("#0e0f12")),
                          lambda x, y, ch: (hx("#6d717a") if (x + y) % 2 == 0 else gap) if ch in "hbs" and 7 > y > 1 or (ch in "hbs" and y >= 7 and 4 <= x <= 11) else None)
    ts = pal("#3b4150")
    out["training_shirt"] = I(CHEST, cols(ts),
                              lambda x, y, ch: (WRAPC if ch != "o" and y == 6 and (x < 3 or x > 12) else
                                                (pal("#2a2e38")["b"] if ch in "hbs" and (x < 4 or x > 11) else None)))

    def jacket(x, y, ch):
        if ch == "o":
            return None
        if x in (7, 8) and y >= 3:
            return INK["b"]
        if y in (2, 3) and ch in "hbs":
            return ASH["h"]
        if y == 9 and ch in "hbs":
            return LEATHER["d"]
        return None
    out["high_collar_jacket"] = I(CHEST, cols(ASH), jacket)

    out["sarashi_wraps"] = I(M.WRAPS_ICON, {"o": hx("#6e685c"), "h": WRAPC, "s": WRAPC, "w": DIRTY_WRAP},
                             lambda x, y, ch: tone(WRAPC, -0.5) if ch in "hsw" and (x + 2 * y) % 3 == 0 else
                             (tone(BLOOD, -0.4) if (x, y) in ((4, 6), (4, 7)) else None))
    sh = pal("#2c2d34")
    out["bandage_leggings"] = I(LEGS, cols(sh), lambda x, y, ch: (WRAPC if (x + y) % 2 else tone(WRAPC, -0.6)) if ch in "hbs" and y >= 6 else
                                (TIE if y == 2 and ch in "hbs" else None))
    for name, base in (("jika_tabi", "#1c1c21"), ("jika_tabi_ash", "#6c6964")):
        P = pal(base)
        out[name] = I(BOOTS, cols(P, hx("#08080a")),
                      lambda x, y, ch, P=P: STEEL["h"] if ch == "s" and y in (6, 8) else (P["d"] if y == 10 and x in (4, 12) and ch != "o" else None))
    out["wrapped_sandals"] = I(BOOTS, cols(pal("#2b2b30")),
                               lambda x, y, ch: (WRAPC if (x + y) % 2 else tone(WRAPC, -0.6)) if y < 9 and ch in "hbs" else None)

    def cuirass(x, y, ch):
        if ch == "o":
            return None
        if y in (7, 9, 11) and ch in "hbs":
            return CRIMSON_LACE
        if (x, y) in ((7, 4), (8, 4), (6, 3), (9, 3)):
            return GOLD["h"]
        if y in (4, 5) and x in (0, 1, 14, 15):
            return CRIMSON_LACE if y == 5 else LACQ["h"]
        return None
    out["samurai_cuirass"] = I(CHEST, cols(LACQ, hx("#000000")), cuirass)
    rp = pal("#262c44", "#3a4262", "#1a1f30", "#10131d")

    def sode(x, y, ch):
        if ch == "o":
            return None
        if (x < 4 or x > 11) and y <= 6:
            return BLUE_LACE if y in (3, 5) else IRON["b"]
        if 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)):
            return BONE["b"]
        return None
    out["sode_pauldrons"] = I(CHEST, cols(rp), sode)
    out["kabuto"] = I(HELM_ICON, {"o": hx("#050506"), "g": GOLD["h"], "h": LACQ["h"], "b": LACQ["b"], "s": LACQ["s"],
                                   "l": LACQ2, "i": IRON["h"], "m": IRON["b"]},
                      lambda x, y, ch: CRIMSON_LACE if ch == "l" and y == 10 else None)
    hk = pal("#24232a")
    out["kusazuri"] = I(LEGS, cols(hk), lambda x, y, ch: (CRIMSON_LACE if y in (4, 7) else LACQ2 if x % 3 == 0 else LACQ["b"])
                        if ch in "hbs" and y <= 7 else None)
    out["samurai_greaves"] = I(BOOTS, cols(LACQ, hx("#000000")),
                               lambda x, y, ch: GOLD["b"] if ch == "b" and y in (6, 8) else (INK["b"] if y >= 10 and ch in "hbs" else None))
    out["tattered_hood"] = I(HOOD_ICON, {"o": hx("#0c0b0a"), "h": RAG["h"], "b": RAG["b"], "s": RAG["s"], "K": hx("#09090b")},
                             lambda x, y, ch: hx("#d8d1c0") if (x, y) in ((6, 6), (9, 6)) else None)

    def cloak_d(x, y, ch):
        if ch == "o" and y == 14:
            return None if x % 3 == 1 else hx("#0c0b0a")
        if ch in "hbs" and y == 13 and x % 3 == 1:
            return None  # torn hem
        if (x, y) in ((7, 2), (8, 2)):
            return TIE
        if ch == "b" and 9 <= y <= 10 and 3 <= x <= 4:
            return mix(RAG["b"], hx("#5a4a38"), 0.6)
        return None
    out["tattered_cloak"] = I(M.CLOAK_ICON, cols(RAG, hx("#0c0b0a")), cloak_d)
    out["half_mask"] = I(HALF_MASK_ICON, {"o": hx("#101113"), "h": IRON["h"], "b": IRON["b"], "s": IRON["s"], "k": IRON["d"]},
                         lambda x, y, ch: HOLE if ch == "k" and y in (8, 10) else None)

    def assassin(x, y, ch):
        if ch == "o":
            return None
        if y == 10 and ch in "hbs":
            return BLOOD
        if ch in "hbs" and (x - 3 == y - 2 or 12 - x == y - 2) and 2 <= y <= 9:
            return STRAP
        return None
    out["assassin_suit"] = I(CHEST, cols(INK, hx("#000000")), assassin)
    out["assassin_pants"] = I(LEGS, cols(INK, hx("#000000")),
                              lambda x, y, ch: BLOOD if y == 2 and ch in "hbs" else (hx("#3a3a40") if y >= 9 and ch in "hbs" else None))
    out["bandolier"] = I(STRAP_ICON, {"o": hx("#120c08"), "b": LEATHER["b"], "p": STEEL["h"]})
    for name, (cloth, _) in VILLAGE_MARKS.items():
        band = pal(cloth)
        out["forehead_protector_" + name] = I(BAND, {
            "o": tone(band["d"], -0.8), "h": band["h"], "b": band["b"], "s": band["s"], "t": band["s"],
            "M": STEEL["h"], "m": STEEL["b"], "n": STEEL["s"], "e": STEEL["d"]})

    def medic(x, y, ch):
        if ch == "o":
            return None
        if y == 9 and ch in "hbs":
            return INK["b"]
        if (x, y) in ((4, 4), (5, 4), (4, 5), (5, 5)):
            return hx("#a8262a")
        if (x, y) == (5, 5):
            return hx("#4e7a4a")
        if x in (7, 8) and y <= 4 and ch in "hbs":
            return None
        return None
    out["medic_coat"] = I(CHEST, cols(BONE, hx("#5e5a50")), medic)
    fp = pal("#17161b", "#2c2b33", "#0f0f12", "#08080a")

    def formal(x, y, ch):
        if ch == "o":
            return None
        if 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)):
            return BONE["b"]
        if (x, y) in ((4, 4), (11, 4)):
            return hx("#e8e3d6")
        if y == 8:
            return hx("#6e6c74")
        return None
    out["formal_robe"] = I(CHEST, cols(fp, hx("#000000")), formal)
    gp = pal("#55545c", "#6e6d76", "#3c3b42", "#26252a")
    out["formal_robe_hem"] = I(LEGS, cols(gp), lambda x, y, ch: (gp["s"] if x % 2 else gp["b"]) if ch in "hbs" else None)
    bp = pal("#2c282c")

    def bk(x, y, ch):
        if ch == "o":
            return None
        if 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)):
            return hx("#7c1a20")
        if y in (7, 8) and ch in "hbs":
            return hx("#b4b9c0") if x % 3 else hx("#62666d")
        if y == 6 and (x < 3 or x > 12):
            return WRAPC
        return None
    out["battle_kimono"] = I(CHEST, cols(bp, hx("#000000")), bk)
    hk2 = pal("#232227")
    out["battle_hakama"] = I(LEGS, cols(hk2), lambda x, y, ch: hx("#7c1a20") if y == 2 and ch in "hbs" else
                             (INK["d"] if y >= 9 and ch in "hbs" else None))
    return out


# ---------------------------------------------------------------------------
# Saving and the preview sheet
# ---------------------------------------------------------------------------

# item id -> texture (legs pieces share their outfit's texture)
ITEMS = {
    "formal_robe_hem": "formal_robe", "battle_hakama": "battle_kimono",
}


def outfits(tex):
    l1 = lambda n: tex[n][0]  # noqa: E731
    l2 = lambda n: tex[n][1]  # noqa: E731
    shin = G.shinobi()
    return [
        {"chest": l1("mesh_shirt"), "legs": l2("bandage_leggings"), "feet": l1("jika_tabi"), "head": l1("forehead_protector_mist")},
        {"chest": l1("training_shirt"), "legs": shin[1], "feet": l1("wrapped_sandals"), "head": l1("forehead_protector_cloud")},
        {"chest": l1("high_collar_jacket"), "legs": shin[1], "feet": l1("jika_tabi_ash"), "head": l1("forehead_protector_stone")},
        {"chest": l1("sarashi_wraps"), "legs": l2("bandage_leggings"), "feet": l1("wrapped_sandals"), "head": l1("forehead_protector_sand")},
        {"chest": l1("samurai_cuirass"), "legs": l2("kusazuri"), "feet": l1("samurai_greaves"), "head": l1("kabuto")},
        {"chest": l1("sode_pauldrons"), "legs": l2("kusazuri"), "feet": l1("samurai_greaves")},
        {"chest": l1("tattered_cloak"), "legs": l2("assassin_pants"), "feet": l1("jika_tabi"), "head": l1("tattered_hood")},
        {"chest": l1("assassin_suit"), "legs": l2("assassin_pants"), "feet": l1("jika_tabi"), "head": l1("half_mask")},
        {"chest": l1("bandolier"), "legs": l2("bandage_leggings"), "feet": l1("wrapped_sandals")},
        {"chest": l1("medic_coat"), "legs": shin[1], "feet": shin[0], "head": l1("forehead_protector_cloud")},
        {"chest": l1("formal_robe"), "legs": l2("formal_robe"), "feet": None},
        {"chest": l1("battle_kimono"), "legs": l2("battle_kimono"), "feet": l1("jika_tabi")},
    ]


def save(tex, item_icons, with_preview=True):
    written = 0
    for name, pair in tex.items():
        for i, cv in enumerate(pair):
            if cv is not None:
                cv.save(os.path.join(G.ARMOR_DIR, "%s_layer_%d.png" % (name, i + 1)))
                written += 1
    for name, cv in item_icons.items():
        cv.save(os.path.join(G.ITEM_DIR, name + ".png"))
    print("armor wave: wrote %d armour layers and %d icons" % (written, len(item_icons)))
    if with_preview:
        preview(tex, item_icons)


def preview(tex, item_icons):
    looks = G.L.load_looks(G.PARTS_DIR)
    skin = G.L.compose(G.L.decode("generic:3a7f00c9b2", looks), looks, G.PARTS_DIR)
    s = 4
    doll_w, doll_h = 16 * s, 32 * s
    sets = outfits(tex)
    per_row = 6
    set_w = doll_w * 2 + 3 * s
    rows = (len(sets) + per_row - 1) // per_row
    icon_rows = (len(item_icons) + 15) // 16
    W = max(per_row * (set_w + 12) + 20, 16 * 74 + 20)
    H = 10 + rows * (doll_h + 20) + icon_rows * 74 + 20
    sheet = Canvas(W, H, (46, 48, 54, 255))
    for i, armor in enumerate(sets):
        x = 10 + (i % per_row) * (set_w + 12)
        y = 10 + (i // per_row) * (doll_h + 20)
        G.doll(sheet, skin, armor, x, y, s, "front")
        G.doll(sheet, skin, armor, x + doll_w + 3 * s, y, s, "back")
    y0 = 10 + rows * (doll_h + 20)
    for i, (name, cv) in enumerate(item_icons.items()):
        x = 10 + (i % 16) * 74
        y = y0 + (i // 16) * 74
        sheet.fill_rect(x - 2, y - 2, 68, 68, (62, 64, 72, 255))
        sheet.blit(cv, 0, 0, 16, 16, x, y, 4)
    sheet.save(PREVIEW)
    print("preview:", os.path.relpath(PREVIEW, G.ROOT))


def main():
    save(textures(), icons())


if __name__ == "__main__":
    main()

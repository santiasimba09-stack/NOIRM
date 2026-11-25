"""Textures for NOIRM's 3D drapes (gear/Drapes, client/DrapeLayer): capes, skirts, collars, hoods,
wide sleeves, shoulder plates, the straw hat and its strips, gourds and jars. Standard library only.

Same "raw and tough" ink style as the armour layers (docs/ART_STYLE.md): woven cloth with big vertical
folds (dark crevices, lit ridges), a worn hem with dirt, stitched trims, lacquered plates with cord
lacing. Everything is drawn here from maths; nothing is traced or sampled.

Cloth textures put the OUTSIDE on the left half and the LINING on the right half:
  * capes: u runs across the cape (0 = the wearer's right edge as seen from behind), v from the
    shoulders (0) to the hem (1);
  * skirts, collars, sleeves: u runs round the ring: 0 at the wearer's left side, 0.25 the back
    centre, 0.5 the right side, 0.75 the front centre; v from the top (0) to the hem (1).
Model-part textures (hood, shoulder plates, props) use the 64x32 box layout at 4x.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, hx, tone, mix, noise  # noqa: E402

CLEAR = (0, 0, 0, 0)


def clamp8(c):
    return tuple(max(0, min(255, int(round(x)))) for x in c[:3])


def lit(c, k):
    """Brighten (k > 0) or darken (k < 0) a colour by a fraction."""
    if k >= 0:
        return clamp8(tuple(x + (255 - x) * k * 0.6 for x in c))
    return clamp8(tuple(x * (1 + k) for x in c))


def fbm(x, y, salt, octaves=3):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        f = 2 ** o
        xi, yi = math.floor(x * f), math.floor(y * f)
        tx, ty = x * f - xi, y * f - yi
        a = noise(xi, yi, salt + o * 31)
        b = noise(xi + 1, yi, salt + o * 31)
        c = noise(xi, yi + 1, salt + o * 31)
        d = noise(xi + 1, yi + 1, salt + o * 31)
        sx, sy = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        total += amp * ((a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy)
        norm += amp
        amp *= 0.5
    return total / norm


# ---------------------------------------------------------------------------
# Cloth shading
# ---------------------------------------------------------------------------

def fold_light(u, v, salt, folds=5.0, strength=1.0):
    """Vertical drapery: -1 (deep crevice) .. +1 (lit ridge), with folds that wander and fan out."""
    wobble = (fbm(u * 3, v * 2, salt) - 0.5) * 0.9
    phase = (u + wobble * 0.12 + v * 0.05 * math.sin(u * 7 + salt)) * folds * 2 * math.pi
    s = math.sin(phase)
    # sharpen: narrow dark crevices, broad soft ridges
    shade = s if s > 0 else -(abs(s) ** 0.55)
    return shade * strength * (0.45 + 0.55 * v)


def woven(c, X, Y, salt, amount=1.0):
    """Twill grain at full resolution."""
    t = ((X + Y * 2) % 4) / 3.0 - 0.5
    g = (noise(X, Y, salt) - 0.5)
    return lit(c, (t * 0.05 + g * 0.08) * amount)


def cloth_pixel(base, u, v, X, Y, salt, folds=5.0, fold=1.0, wear=1.0, top_shadow=0.25):
    f = fold_light(u, v, salt, folds, fold)
    k = f * 0.28
    if v < 0.12:
        k -= top_shadow * (1 - v / 0.12)  # shadow under the yoke / belt
    c = lit(base, k)
    # crevice ink lines
    if f < -0.82:
        c = lit(c, -0.35)
    c = woven(c, X, Y, salt)
    # faded patches and dirt towards the hem
    patch = fbm(u * 4 + 7, v * 3, salt + 5)
    if patch > 0.72:
        c = mix(c, lit(c, 0.18), 0.5 * wear)
    if v > 0.7:
        dirt = (v - 0.7) / 0.3 * 0.45 * wear * (0.6 + 0.4 * fbm(u * 9, v * 6, salt + 9))
        c = mix(c, (58, 48, 38), dirt)
    return clamp8(c)


def outline(c):
    return lit(c, -0.7)


# ---------------------------------------------------------------------------
# Shapes (u, v in 0..1 of a panel, aspect = width / height of the panel in model pixels)
# ---------------------------------------------------------------------------

def fan_crest(x, y, r, red, white, ink):
    """Our own round paper fan (an uchiwa): a red top, a pale lower half, a short handle.
    x, y relative to the centre in units of r. Returns a colour or None."""
    d = math.hypot(x, y)
    if d <= 1.0:
        if d > 0.86:
            return ink
        if y < -0.02:
            return red
        if y < 0.06:
            return ink
        return white
    if abs(x) <= 0.16 and 0.98 <= y <= 1.75:
        return ink if abs(x) > 0.09 or y > 1.68 else white
    return None


def swirl_crest(x, y, col, ink, turns=2.2):
    """A spiral in a ring (our own whirlpool mark). x, y in units of the radius."""
    d = math.hypot(x, y)
    if d > 1.0:
        return None
    if d > 0.84:
        return col
    if d > 0.74:
        return None
    a = math.atan2(y, x)
    # distance to the spiral arm r = k * theta
    k = 0.7 / (turns * 2 * math.pi)
    best = 9
    for n in range(-1, int(turns) + 2):
        th = a + 2 * math.pi * n
        if th < 0:
            continue
        r = k * th
        best = min(best, abs(d - r))
    if best < 0.075 and d < 0.72:
        return col
    return None


def cloud_mark(x, y, red, white):
    """Our own storm cloud: three puffs on a flat base with two curls, red outlined in white.
    x, y in units of the cloud's half-width (-1..1 wide, about -0.6..0.45 tall)."""
    puffs = [(-0.55, 0.05, 0.42), (0.02, -0.18, 0.5), (0.55, 0.02, 0.4), (-0.15, 0.15, 0.4), (0.3, 0.15, 0.38)]

    def inside(px, py, grow=0.0):
        if py > 0.38 + grow:
            return False
        return any(math.hypot(px - cx, py - cy) <= r + grow for cx, cy, r in puffs)

    if inside(x, y):
        # curls drawn in white inside the two side puffs
        for cx, cy, r in ((-0.55, 0.05, 0.22), (0.55, 0.02, 0.2)):
            d = math.hypot(x - cx, y - cy)
            if abs(d - r) < 0.055 and (math.atan2(y - cy, x - cx) < 1.2):
                return white
        return red
    if inside(x, y, 0.12):
        return white
    return None


def leaf_crest(x, y, col):
    """Three leaves on a short stem (our own mark for the Senju), in units of the radius."""
    if abs(x) < 0.07 and 0.1 < y < 0.9:
        return col
    for ang in (-math.pi / 2, -math.pi / 2 - 1.0, -math.pi / 2 + 1.0):
        cx, cy = 0.45 * math.cos(ang), 0.1 + 0.45 * math.sin(ang)
        dx, dy = x - cx, y - cy
        ca, sa = math.cos(-ang), math.sin(-ang)
        rx, ry = dx * ca - dy * sa, dx * sa + dy * ca
        if (rx / 0.48) ** 2 + (ry / 0.2) ** 2 <= 1:
            return col
    return None


# ---------------------------------------------------------------------------
# Capes (128 x 128: outside 0..63, lining 64..127)
# ---------------------------------------------------------------------------

CAPE_W, CAPE_H = 128, 128


def cape(base_hex, lining_hex, salt, hem="plain", crest=None, trim=None, top_trim=None, wear=1.0, folds=4.5, mottle=0.0,
         clouds=None):
    """crest(u, v) -> colour or None in cape coordinates (u across, v down, 0..1).
    hem: "plain", "torn" (ragged, holes), "teeth" (zig-zag), "fringe".
    trim: (colour, from_v) a band near the hem; top_trim: a band at the shoulders."""
    cv = Canvas(CAPE_W, CAPE_H)
    base, lining = hx(base_hex), hx(lining_hex)
    half = CAPE_W // 2
    for Y in range(CAPE_H):
        v = (Y + 0.5) / CAPE_H
        for X in range(CAPE_W):
            inside = X >= half
            u = ((X - half) if inside else X + 0.5) / half
            if inside:
                u = 1 - u  # the lining mirrors the outside (the same spot of cloth)
            # the hem's shape (cut out with alpha)
            edge = 1.0
            if hem == "torn":
                edge = 0.86 + 0.12 * fbm(u * 6, 1, salt) - 0.09 * abs(math.sin(u * 23 + salt))
                if v > 0.55 and fbm(u * 8, v * 8, salt + 3) > 0.83:
                    cv.set(X, Y, CLEAR)  # moth holes and rips
                    continue
            elif hem == "teeth":
                t = (u * 9) % 1.0
                edge = 0.9 + 0.08 * (1 - abs(2 * t - 1))
            elif hem == "fringe":
                edge = 0.95 if (X // 2) % 2 else 0.985
            if v > edge:
                cv.set(X, Y, CLEAR)
                continue
            c = lining if inside else base
            if mottle and not inside:
                c = mix(c, lit(c, -0.3), mottle * fbm(u * 5, v * 5, salt + 11))
            c = cloth_pixel(c, u, v, X, Y, salt, folds=folds, wear=wear * (0.6 if inside else 1.0), top_shadow=0.35)
            if inside:
                c = lit(c, -0.12)
            if not inside:
                if trim and trim[1] <= v and v <= edge:
                    c = cloth_pixel(hx(trim[0]), u, v, X, Y, salt + 1, folds=folds, wear=wear)
                if top_trim and v < top_trim[1]:
                    c = cloth_pixel(hx(top_trim[0]), u, v, X, Y, salt + 2, folds=folds, wear=0.2, top_shadow=0.2)
                if clouds:
                    m = clouds(u, v)
                    if m is not None:
                        c = lit(m, fold_light(u, v, salt, folds) * 0.22)
                if crest:
                    m = crest(u, v)
                    if m is not None:
                        c = lit(m, fold_light(u, v, salt, folds) * 0.18)
            # the hem: a dark rolled edge
            if edge - v < 0.018:
                c = lit(c, -0.45)
            # side edges: a stitched hem
            if not inside and (u < 0.025 or u > 0.975):
                c = lit(c, -0.3)
            cv.set(X, Y, c + (255,))
    return cv


def centred(cx, cy, r, aspect, fn):
    """Places a crest fn(x, y) at (cx, cy) with radius r (in v units); aspect = cape width / length."""
    def at(u, v):
        x = (u - cx) * aspect / r
        y = (v - cy) / r
        return fn(x, y)
    return at


# ---------------------------------------------------------------------------
# Rings: skirts, coat tails, plates, collars, sleeves (256 x 96: outside 0..127, lining 128..255)
# ---------------------------------------------------------------------------

def ring(base_hex, lining_hex, salt, w=256, h=96, folds=10.0, hem_trim=None, top_band=None, crest_back=None, front_edge=None,
         plates=None, fur=False, wear=1.0, stripes=None, hem="plain", marks=None):
    """A ring texture. hem_trim: (colour, from_v); top_band: (colour, to_v); crest_back(x, y) at the back
    centre (x, y in units of the band height); front_edge: colour of the front opening's edging;
    plates: (lacquer hex, cord hex, rows) for lamellar plates; marks(u, v) -> colour or None."""
    cv = Canvas(w, h)
    base, lining = hx(base_hex), hx(lining_hex)
    half = w // 2
    for Y in range(h):
        v = (Y + 0.5) / h
        for X in range(w):
            inside = X >= half
            u = ((X - half) if inside else X) / half + 0.5 / half
            if hem == "teeth":
                t = (u * 24) % 1.0
                if v > 0.92 + 0.07 * (1 - abs(2 * t - 1)):
                    cv.set(X, Y, CLEAR)
                    continue
            c = lining if inside else base
            if plates and not inside:
                lac, cord, rows = hx(plates[0]), hx(plates[1]), plates[2]
                rv = v * rows
                i = math.floor(rv)
                f = rv - i
                if f > 0.8:
                    c = cord
                    # crossed cords
                    a = ((u * 40 + i * 0.5) % 1.0)
                    if abs((f - 0.8) / 0.2 - a) < 0.18 or abs((f - 0.8) / 0.2 - (1 - a)) < 0.18:
                        c = lit(cord, 0.35)
                    c = woven(c, X, Y, salt)
                else:
                    j = math.floor(u * 40 + (i % 2) * 0.5)
                    c = lac if j % 2 == 0 else lit(lac, -0.12)
                    # glossy lacquer: a highlight band and a dark lower lip per plate
                    if f < 0.12:
                        c = lit(c, 0.35)
                    elif f > 0.68:
                        c = lit(c, -0.4)
                    if noise(X, Y, salt) > 0.985:
                        c = lit(c, 0.5)  # scratches
                cv.set(X, Y, clamp8(c) + (255,))
                continue
            if fur and not inside:
                strand = fbm(u * 60, v * 4, salt) * 0.6 + noise(X, Y // 2, salt) * 0.4
                c = lit(base, (strand - 0.5) * 0.9 - 0.25 * v)
                cv.set(X, Y, clamp8(c) + (255,))
                continue
            c = cloth_pixel(c, u, v, X, Y, salt, folds=folds, wear=wear * (0.5 if inside else 1.0), top_shadow=0.3)
            if inside:
                c = lit(c, -0.25)
            else:
                if stripes:
                    for col, a, b in stripes:
                        if a <= v < b:
                            c = cloth_pixel(hx(col), u, v, X, Y, salt + 4, folds=folds, wear=wear)
                if hem_trim and v >= hem_trim[1]:
                    c = cloth_pixel(hx(hem_trim[0]), u, v, X, Y, salt + 1, folds=folds, wear=wear)
                if top_band and v < top_band[1]:
                    c = cloth_pixel(hx(top_band[0]), u, v, X, Y, salt + 2, folds=folds, wear=0.3, top_shadow=0.1)
                if front_edge and abs(u - 0.75) < 0.012:
                    c = hx(front_edge)
                if crest_back:
                    # the back centre is at u = 0.25; the ring is about 32 model pixels round
                    x = (u - 0.25) * 32.0 / 12.0 / 0.25
                    y = (v - 0.4) / 0.25
                    m = crest_back(x * 0.25, y)
                    if m is not None:
                        c = lit(m, fold_light(u, v, salt, folds) * 0.2)
                if marks:
                    m = marks(u, v)
                    if m is not None:
                        c = m
            if v > 0.975 and not inside:
                c = lit(c, -0.45)  # the hem's rolled edge
            cv.set(X, Y, clamp8(c) + (255,))
    return cv


# ---------------------------------------------------------------------------
# Box-layout textures for model parts (64 x 32 at 4x = 256 x 128)
# ---------------------------------------------------------------------------

def box_cloth(base_hex, salt, trim_hex=None, trim_regions=(), shade=0.0, lacquer=None):
    """A uniform material over the whole 64x32 layout (model-part UVs overlap freely).
    trim_regions: (x0, y0, x1, y1) base-pixel rectangles painted in trim_hex."""
    S = 4
    cv = Canvas(64 * S, 32 * S)
    base = hx(base_hex)
    for Y in range(32 * S):
        for X in range(64 * S):
            u, v = X / (64.0 * S), Y / (32.0 * S)
            bx, by = X / S, Y / S
            c = base
            for (x0, y0, x1, y1) in trim_regions:
                if x0 <= bx < x1 and y0 <= by < y1:
                    c = hx(trim_hex)
            if lacquer:
                rows = lacquer
                f = (by * rows / 8.0) % 1.0
                if f < 0.14:
                    c = lit(c, 0.4)
                elif f > 0.78:
                    c = lit(c, -0.45) if f < 0.9 else hx("#1a1a1e")
                if noise(X, Y, salt) > 0.988:
                    c = lit(c, 0.5)
                c = lit(c, (fbm(u * 6, v * 6, salt) - 0.5) * 0.25 + shade)
            else:
                c = cloth_pixel(c, u * 3 % 1.0, (v * 2) % 1.0, X, Y, salt, folds=3, wear=0.3, top_shadow=0.0)
                c = lit(c, shade)
            cv.set(X, Y, clamp8(c) + (255,))
    return cv


def hood(base_hex, trim_hex, salt):
    """The hood shell (client DrapeLayer.hoodShell): the shell at 0,0 (10.4 cube), rims at 0,21 and
    42,0, the fall at 24,21, the peak at 46,0."""
    cv = box_cloth(base_hex, salt, trim_hex, trim_regions=((0, 21, 24, 24), (42, 0, 46, 10)))
    S = 4
    # big folds down the back and sides of the shell, a seam over the crown
    for Y in range(0, 21 * S):
        for X in range(0, 42 * S):
            c = cv.get(X, Y)[:3]
            u = X / (42.0 * S)
            v = Y / (21.0 * S)
            c = lit(c, fold_light(u * 2 % 1.0, v, salt, 3, 0.8) * 0.25)
            if abs(X - 15.6 * S) < 2 and Y < 10.4 * S:
                c = lit(c, -0.5)  # the crown seam
            cv.set(X, Y, clamp8(c) + (255,))
    return cv


def plates(lacquer_hex, cord_hex, salt):
    """Lamellar shoulder plates (DrapeLayer.shoulderPlate): rows of lacquered plates laced with cord."""
    S = 4
    cv = Canvas(64 * S, 32 * S)
    lac, cord = hx(lacquer_hex), hx(cord_hex)
    for Y in range(32 * S):
        for X in range(64 * S):
            by = Y / S
            f = (by / 1.6) % 1.0
            row = math.floor(by / 1.6)
            if f > 0.78:
                c = cord
                a = ((X / S / 1.2 + row * 0.5) % 1.0)
                g = (f - 0.78) / 0.22
                if abs(g - a) < 0.2 or abs(g - (1 - a)) < 0.2:
                    c = lit(cord, 0.4)
            else:
                j = math.floor(X / S / 1.2 + (row % 2) * 0.5)
                c = lac if j % 2 == 0 else lit(lac, -0.1)
                if f < 0.13:
                    c = lit(c, 0.45)
                elif f > 0.62:
                    c = lit(c, -0.4)
                if noise(X, Y, salt) > 0.985:
                    c = lit(c, 0.55)
            cv.set(X, Y, clamp8(c) + (255,))
    return cv


def hat(straw_hex, under_hex, strip_hex, salt):
    """The straw hat (DrapeLayer.hat): the cone's outside at u 0..0.4 (v from the tip to the brim), its
    underside at 0.5..0.9, the hanging strips at 0.92..1.0."""
    W, H = 128, 64
    cv = Canvas(W, H)
    straw, under, strip = hx(straw_hex), hx(under_hex), hx(strip_hex)
    for Y in range(H):
        v = (Y + 0.5) / H
        for X in range(W):
            u = (X + 0.5) / W
            if u < 0.5:
                # concentric woven rings, radial stitches, sun-bleached towards the tip
                ring_ = (v * 14) % 1.0
                c = lit(straw, 0.15 * (1 - v))
                if ring_ < 0.18:
                    c = lit(c, -0.45)
                elif ring_ < 0.5:
                    c = lit(c, 0.12)
                if (X % 6) == 0 and ring_ > 0.5:
                    c = lit(c, -0.25)
                c = lit(c, (noise(X, Y, salt) - 0.5) * 0.2)
                if v > 0.93:
                    c = lit(c, -0.55)  # the bound rim
            elif u < 0.92:
                ring_ = (v * 14) % 1.0
                c = lit(under, -0.15 if ring_ < 0.2 else 0)
                c = lit(c, (noise(X, Y, salt + 1) - 0.5) * 0.2)
            else:
                # a paper/cloth strip: pale, creased, darker at the bottom
                c = lit(strip, -0.4 * v + (noise(X // 2, Y, salt + 2) - 0.5) * 0.15)
                if (Y % 9) == 0:
                    c = lit(c, -0.3)
                if X == W - 1 or X == int(0.92 * W):
                    c = lit(c, -0.35)
            cv.set(X, Y, clamp8(c) + (255,))
    return cv


def gourd(base_hex, rope_hex, salt):
    """A gourd or a jar (DrapeLayer.propShape): the body material over the layout, rope at v 16..24."""
    S = 4
    cv = Canvas(64 * S, 32 * S)
    base, rope = hx(base_hex), hx(rope_hex)
    for Y in range(32 * S):
        for X in range(64 * S):
            by = Y / S
            if 16 <= by < 24:
                t = ((X + Y) % 6) / 5.0
                c = lit(rope, (t - 0.5) * 0.5)
            else:
                c = lit(base, (fbm(X / 40.0, Y / 40.0, salt) - 0.5) * 0.45)
                if fbm(X / 12.0, Y / 12.0, salt + 3) > 0.7:
                    c = lit(c, -0.2)  # weathering
                if noise(X, Y, salt) > 0.99:
                    c = lit(c, 0.4)
                band = (by % 8) / 8.0
                if band < 0.06:
                    c = lit(c, 0.25)  # a glaze highlight running round
            cv.set(X, Y, clamp8(c) + (255,))
    return cv

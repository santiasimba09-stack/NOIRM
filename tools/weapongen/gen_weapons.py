"""Draws NOIRM's ninja weapon icons in the HD ink style (docs/ART_STYLE.md) into
src/main/resources/assets/noirm/textures/item/. Run from the repository root:

    python3 tools/weapongen/gen_weapons.py [--scale 4] [--preview out.png]

Resolution-parametric: every shape is described in the old 16x16 "icon units" and rasterised at
16 * scale pixels (scale 4 -> 64x64, the HD standard). Our own designs, never traced.

The look ("raw and tough"): a thick ink silhouette first (it has to read at 16 px in the hotbar),
then carved two-tone shading with hard light edges (light from the top left), forged steel with a
bone-white edge line, nicks bitten out of the edge, scratches, grit; grips wrapped in cloth or
cord, lacquer with a hard shine, rope; blood red as the accent; chakra glow only where chakra flows.

Long weapons are drawn along the diagonal, tip to the top right, like a vanilla sword: the
thrown-weapon renderer relies on that to point kunai and senbon along their flight, and stars are
drawn centred so they spin flat.

The *_glow textures are layers for chakra flow (katana: lightning, tekko-kagi: wind, nodachi: fire,
yari: wind, tonfa: earth, tantō: white lightning): the item model draws them full-bright over the
weapon while its wielder's chakra flows into it. Long weapons also get a straight "held" texture
and model (tools/weapongen/gen_held.py).
"""
import math
import os
import sys

from PIL import Image

OUT = "src/main/resources/assets/noirm/textures/item"
S = 4  # pixels per icon unit (set by --scale)
N = 16 * S
W = H = N  # the canvas being drawn (pixels): N x N for icons, long and narrow for held models


def configure(scale):
    global S, N, W, H
    S = scale
    N = 16 * scale
    W = H = N


# ---------------------------------------------------------------- colours

def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def mix(a, b, t):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


INK = rgb(0x0E0C0E)
BONE = rgb(0xECE6D6)
BLOOD = rgb(0xA01216)
BLOOD_DK = rgb(0x5C080C)
BLOOD_HI = rgb(0xCE3028)


class Mat:
    """A material: hi (hard light edge), light, mid, dark, deep (ink shadow)."""

    def __init__(self, hi, light, mid, dark, deep):
        self.hi, self.light, self.mid, self.dark, self.deep = (rgb(c) for c in (hi, light, mid, dark, deep))


STEEL = Mat(0xF2EFE6, 0xC6CACE, 0x8A9098, 0x4C5058, 0x23252B)
DSTEEL = Mat(0xCFCFCC, 0x86888F, 0x55585F, 0x33353B, 0x18181C)       # blackened iron
CHAKRA_STEEL = Mat(0xE4F6FF, 0x9CC4DC, 0x5E86A6, 0x34506C, 0x18283A)
SCALE_SKIN = Mat(0xC8D8E0, 0x8EA6B6, 0x5E7486, 0x384A5A, 0x1C2630)
BOLT_STEEL = Mat(0xFFFBE0, 0xF2DA6A, 0xC09A2A, 0x7A5C14, 0x3A2A08)
CLOTH = Mat(0x8A8286, 0x58514F, 0x3A3436, 0x242022, 0x141214)        # black cloth wrap
CLOTH_RED = Mat(0xD8483C, 0xA6201C, 0x7A1414, 0x4E0A0C, 0x2A0406)
LEATHER = Mat(0xA07850, 0x6E4E32, 0x523822, 0x342216, 0x1C120A)
WOOD = Mat(0xA87A50, 0x7C5634, 0x5C3E26, 0x3C2818, 0x20140C)
DARK_WOOD = Mat(0x7A5C46, 0x4E3A2C, 0x36281E, 0x231A14, 0x120D0A)
LACQ_BLACK = Mat(0xB8B4B0, 0x3C383A, 0x262224, 0x181516, 0x0C0A0B)
LACQ_RED = Mat(0xF0A090, 0xB82A20, 0x8C1A16, 0x5A0E0C, 0x2C0606)
PAPER = Mat(0xF6EED6, 0xE6D8B4, 0xCCB88E, 0x9C8660, 0x5A4A32)
ROPE = Mat(0xE2CC98, 0xC0A26E, 0x957848, 0x63502E, 0x332816)
BRASS = Mat(0xFFE6A0, 0xCCA24C, 0x9A762E, 0x624818, 0x2E220A)
IVORY = Mat(0xF4EEDC, 0xD8CEB4, 0xB0A48A, 0x7C725E, 0x3E382E)
LIGHTNING = Mat(0xFFFFFF, 0xC4E6FF, 0x6EAAF0, 0x2E62C8, 0x162E78)
WIND = Mat(0xF4FFF8, 0xB6F2DA, 0x6ACFAE, 0x2E8E78, 0x14483E)
FIRE = Mat(0xFFF8D8, 0xFFD24A, 0xFF8A1E, 0xD2381A, 0x6A120A)
EARTH = Mat(0xFFF0C8, 0xF2C46A, 0xC88A34, 0x8A5A20, 0x46300E)
WHITE_LIGHT = Mat(0xFFFFFF, 0xF2F8FF, 0xCFE4FA, 0x96B8E6, 0x4E70A8)


# ---------------------------------------------------------------- noise

def hsh(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def grit(x, y, salt=0):
    """Blocky grain (2x2 blocks at 64 px), so it survives being shrunk to the hotbar."""
    b = max(1, S // 2)
    return hsh(x // b, y // b, salt)


def gritty(c, x, y, salt, amount=0.07, specks=0.035, speck_col=None):
    g = grit(x, y, salt)
    c = shade(c, 1 - amount + 2 * amount * g)
    if hsh(x // max(1, S // 2), y // max(1, S // 2), salt + 7) < specks:
        c = speck_col or shade(c, 0.55)
    return c


# ---------------------------------------------------------------- canvas

class Canvas:
    def __init__(self):
        self.px = [[None] * W for _ in range(H)]

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < W and 0 <= y < H else None

    def image(self):
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        p = img.load()
        for y in range(H):
            for x in range(W):
                c = self.px[y][x]
                if c is not None:
                    p[x, y] = tuple(c) + (255,)
        return img


def pixels():
    """Every pixel with its centre in icon units."""
    for y in range(H):
        for x in range(W):
            yield x, y, (x + 0.5) / S, (y + 0.5) / S


class Ctx:
    __slots__ = ("x", "y", "u", "v", "t", "k", "s", "w", "n", "L", "lt", "seg")


def along(cv, start, end, segments, curve=0.0, curve_from=0.0):
    """Fills a long shape along start->end (icon units). segments: (t0, t1, w0, w1, shader) with t
    from 0 (start) to 1 (end) and w a half-width; the shader gets a Ctx and returns a colour or
    None (leave the pixel as it is, e.g. a nick). `curve` bows the line (units, at the middle of
    the curved part, which starts at t = curve_from); positive bows towards the top left."""
    sx, sy = start
    ex, ey = end
    L = math.hypot(ex - sx, ey - sy)
    ux, uy = (ex - sx) / L, (ey - sy) / L
    for x, y, u, v in pixels():
        dx, dy = u - sx, v - sy
        t = (dx * ux + dy * uy) / L
        s = dx * -uy + dy * ux  # positive towards the bottom right
        if curve and t > curve_from:
            q = (t - curve_from) / (1 - curve_from)
            s += curve * math.sin(math.pi * q * 0.5) ** 2 * 1.0 if q < 1 else curve
        for i, (t0, t1, w0, w1, shader) in enumerate(segments):
            if t0 <= t <= t1:
                k = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                w = w0 + (w1 - w0) * k
                if abs(s) <= w and w > 0:
                    c = Ctx()
                    c.x, c.y, c.u, c.v, c.t, c.k, c.s, c.w = x, y, u, v, t, k, s, w
                    c.n, c.L, c.lt, c.seg = s / w, L, t * L, i
                    col = shader(c)
                    if col is not None:
                        cv.put(x, y, col)
                break


def disc(cv, cx, cy, r_out, shader, r_in=0.0, squash=1.0):
    for x, y, u, v in pixels():
        dx, dy = u - cx, (v - cy) * squash
        d = math.hypot(dx, dy)
        if r_in <= d <= r_out:
            c = Ctx()
            c.x, c.y, c.u, c.v = x, y, u, v
            c.s, c.t, c.w, c.n, c.k, c.lt, c.L, c.seg = d, math.atan2(dy, dx), r_out, (d - r_in) / max(1e-6, r_out - r_in), 0, d, r_out, 0
            col = shader(c)
            if col is not None:
                cv.put(x, y, col)


def polygon(cv, pts, shader):
    def inside(u, v):
        hit = False
        j = len(pts) - 1
        for i in range(len(pts)):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > v) != (yj > v) and u < (xj - xi) * (v - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit
    for x, y, u, v in pixels():
        if inside(u, v):
            c = Ctx()
            c.x, c.y, c.u, c.v = x, y, u, v
            c.s = c.t = c.w = c.n = c.k = c.lt = c.L = 0
            c.seg = 0
            col = shader(c)
            if col is not None:
                cv.put(x, y, col)


def outline(cv, width=None, colour=INK):
    """The ink silhouette: a solid outline `width` pixels thick (half an icon pixel at 4x)."""
    width = width or max(1, round(S * 0.5))
    src = [row[:] for row in cv.px]
    r2 = width * width + 0.5
    offs = [(dx, dy) for dy in range(-width, width + 1) for dx in range(-width, width + 1) if 0 < dx * dx + dy * dy <= r2]
    for y in range(H):
        for x in range(W):
            if src[y][x] is not None:
                continue
            for dx, dy in offs:
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H and src[ny][nx] is not None:
                    cv.px[y][x] = colour
                    break


def contour(cv, colour=INK, inner=None):
    """An inner ink line along the silhouette's lower-right edge (a cast shadow), 1 px."""
    src = [row[:] for row in cv.px]
    for y in range(H):
        for x in range(W):
            if src[y][x] is None:
                continue
            if (x + 1 < W and src[y][x + 1] is None) or (y + 1 < H and src[y + 1][x] is None):
                cv.px[y][x] = mix(src[y][x], colour, 0.55)


def punch(cv, cx, cy, r):
    """A hole (transparent) of radius r units."""
    for x, y, u, v in pixels():
        if math.hypot(u - cx, v - cy) <= r:
            cv.px[y][x] = None


# ---------------------------------------------------------------- shaders

def scratches(c, salt, count=5, width=0.13):
    """True if this pixel lies on one of the blade's scratches (thin lines across the steel)."""
    for i in range(count):
        p = hsh(i, 11, salt) * c.L
        ang = (hsh(i, 12, salt) - 0.5) * 1.6 + 0.9
        ln = 0.5 + hsh(i, 13, salt) * 1.2
        d = (c.lt - p) * math.cos(ang) - c.s * math.sin(ang)
        a = (c.lt - p) * math.sin(ang) + c.s * math.cos(ang)
        if abs(d) < width and abs(a) < ln:
            return True
    return False


def nicked(c, nicks, depth=0.3, edge=-1):
    """True if this pixel is bitten out of the edge by a nick: nicks = positions along the blade (0-1)."""
    for p in nicks:
        d = abs(c.t - p) * c.L
        if d < 0.45:
            bite = depth * (1 - d / 0.45)
            if edge < 0 and c.s < -c.w + bite:
                return True
            if edge > 0 and c.s > c.w - bite:
                return True
    return False


def blade(mat, salt=1, nicks=(), hamon=False, edge_line=True, double=False, blood=0.0, glow=None, ridge=0.05):
    """Forged steel. Single-edged: the cutting edge is the top-left side (n = -1), the spine the
    bottom right. Double-edged: a centre ridge, lit top-left facet, shadowed bottom-right facet."""
    def f(c):
        if nicks and nicked(c, nicks):
            return None
        n = c.n
        if double:
            if n < -0.72:
                col = mat.hi if edge_line else mat.light
            elif n < -0.08:
                col = mat.light
            elif n < 0.08:
                col = mat.hi  # the ridge catches the light
            elif n < 0.72:
                col = mat.dark
            else:
                col = mat.mid if n < 0.86 else mat.deep  # a thin rim light on the shadow edge
        else:
            wave = 0.13 * math.sin(c.lt * 2.6 + salt) + 0.07 * math.sin(c.lt * 6.1 + salt * 0.5) if hamon else 0
            if n < -0.7:
                col = mat.hi if edge_line else mat.light
            elif hamon and n < -0.32 + wave:
                col = mix(mat.light, mat.hi, 0.45)  # the tempered edge, pale
            elif n < ridge:
                col = mat.light if not hamon else mix(mat.light, mat.mid, 0.4)
            elif n < ridge + 0.16:
                col = mat.deep  # the ridge line (carved)
            elif n < 0.8:
                col = mat.dark
            else:
                col = mat.deep  # the spine
        if scratches(c, salt, 3, 0.09):
            col = shade(col, 0.72)
        col = gritty(col, c.x, c.y, salt, 0.06, 0.02)
        if blood and hsh(c.x // max(1, S // 2), c.y // max(1, S // 2), salt + 99) < blood * (0.4 + 0.6 * c.k) and n > -0.6:
            col = BLOOD if grit(c.x, c.y, salt + 5) > 0.4 else BLOOD_DK
        if glow is not None and n < -0.55:
            col = mix(col, glow.light, 0.55)
        return col
    return f


def wrap_diamond(cloth, gap, salt=2, period=1.25):
    """A sword grip: flat cord wound in crossing bands; diamond gaps show what's beneath."""
    def f(c):
        a = (c.lt + c.s) / period
        b = (c.lt - c.s) / period
        fa, fb = a - math.floor(a), b - math.floor(b)
        band_a = fa < 0.5
        band_b = fb < 0.5
        if not band_a and not band_b:
            col = gap.light if c.n < 0 else gap.mid  # the diamond showing through
            if abs(fa - 0.75) + abs(fb - 0.75) > 0.32:
                col = gap.dark
        else:
            f_ = fa if band_a else fb
            col = cloth.light if f_ < 0.14 else cloth.mid if f_ < 0.36 else cloth.dark
            if c.n > 0.55:
                col = shade(col, 0.7)
        if abs(c.n) > 0.86:
            col = cloth.deep
        return gritty(col, c.x, c.y, salt, 0.08, 0.03)
    return f


def wrap_spiral(cloth, salt=3, period=0.75, slant=0.6):
    """Cloth or leather strips wound round a handle: bands with a lit top and a dark crevice."""
    def f(c):
        a = (c.lt + c.s * slant) / period
        fa = a - math.floor(a)
        col = cloth.light if fa < 0.22 else cloth.mid if fa < 0.72 else cloth.dark if fa < 0.88 else cloth.deep
        if c.n > 0.5:
            col = shade(col, 0.72)
        elif c.n < -0.55 and fa < 0.5:
            col = mix(col, cloth.hi, 0.35)
        return gritty(col, c.x, c.y, salt, 0.1, 0.04)
    return f


def rope(mat=ROPE, salt=4, period=0.55):
    """Twisted rope: tight slanted strands."""
    def f(c):
        a = (c.lt * 1.0 + c.s * 1.4) / period
        fa = a - math.floor(a)
        col = mat.light if fa < 0.3 else mat.mid if fa < 0.7 else mat.dark
        if c.n > 0.6:
            col = mat.deep
        return gritty(col, c.x, c.y, salt, 0.1, 0.05)
    return f


def wood(mat=WOOD, salt=5, lacquer=None):
    """Wood with grain running along it; lacquer adds a hard white shine on the lit side."""
    def f(c):
        g = math.sin(c.s * 9 + math.sin(c.lt * 1.7 + salt) * 2.2)
        col = mat.light if c.n < -0.45 else mat.mid if c.n < 0.35 else mat.dark
        if g > 0.75:
            col = shade(col, 0.72)  # grain line
        if abs(c.n) > 0.85:
            col = mat.deep
        if lacquer and -0.62 < c.n < -0.4:
            col = lacquer
        return gritty(col, c.x, c.y, salt, 0.07, 0.03)
    return f


def metal(mat=DSTEEL, salt=6, rivets=0):
    """A forged fitting (guard, collar, cap): hard top-left light, a dark underside."""
    def f(c):
        col = mat.hi if c.n < -0.6 else mat.light if c.n < -0.1 else mat.mid if c.n < 0.5 else mat.dark
        if abs(c.n) > 0.9:
            col = mat.deep
        if rivets and abs(c.n) < 0.35 and (c.k * rivets) % 1 < 0.35 and c.k > 0.1:
            col = mat.hi if c.n < 0 else mat.deep
        return gritty(col, c.x, c.y, salt, 0.08, 0.04)
    return f


def lacquer(mat=LACQ_BLACK, salt=7):
    """Glossy lacquer: deep colour with one hard streak of light."""
    def f(c):
        col = mat.light if c.n < -0.2 else mat.mid if c.n < 0.5 else mat.dark
        if -0.6 < c.n < -0.35:
            col = mat.hi
        if abs(c.n) > 0.9:
            col = mat.deep
        return gritty(col, c.x, c.y, salt, 0.05, 0.015)
    return f


def flat(col):
    return lambda c: col


def glow_blade(mat, salt=21, crackle=True):
    """A chakra glow layer over a blade: a white-hot core and a coloured rim, crackling."""
    def f(c):
        n = abs(c.n)
        col = mat.hi if n < 0.3 else mat.light if n < 0.62 else mat.mid if n < 0.86 else mat.dark
        if crackle:
            z = math.sin(c.lt * 6.5 + salt) * 0.55 + math.sin(c.lt * 15.1 + salt * 2) * 0.25
            if abs(c.n - z) < 0.12:
                col = mat.hi
        return col
    return f


def flame_blade(mat=FIRE, salt=27):
    """A fire glow over a blade: a white-hot core along the edge, orange body, red tongues licking
    past the steel (the layer is drawn a little wider than the blade)."""
    def f(c):
        tongue = 0.72 + 0.2 * math.sin(c.lt * 4.3 + salt) + 0.12 * math.sin(c.lt * 9.7 + salt * 0.7)
        n = abs(c.n)
        if n > tongue:
            return None
        q = n / max(0.2, tongue)
        col = mat.hi if q < 0.22 else mat.light if q < 0.5 else mat.mid if q < 0.8 else mat.dark
        if hsh(c.x // max(1, S // 2), c.y // max(1, S // 2), salt) < 0.06:
            col = mat.hi  # embers
        return col
    return f


def stone_cracks(mat=EARTH, salt=29, period=1.4):
    """An earth glow: only the cracks of a stone skin over the wood, glowing amber (the rest of the
    layer is left clear, so the weapon shows through)."""
    def f(c):
        a = c.lt / period + 0.35 * math.sin(c.s * 3.1 + salt)
        fa = a - math.floor(a)
        crack = fa < 0.09 or abs(c.n - 0.45 * math.sin(c.lt * 2.3 + salt)) < 0.11
        if not crack:
            return None
        return mat.hi if abs(c.n) < 0.4 else mat.light
    return f


# ---------------------------------------------------------------- the weapons

DIAG = ((1.6, 14.4), (14.6, 1.4))


def offset_line(start, end, off):
    """A parallel line `off` units to the bottom right."""
    (sx, sy), (ex, ey) = start, end
    L = math.hypot(ex - sx, ey - sy)
    nx, ny = -(ey - sy) / L, (ex - sx) / L
    return (sx + nx * off, sy + ny * off), (ex + nx * off, ey + ny * off)


def ring_shader(mat):
    def f(c):
        lit = (c.u - c.v) < 0 if False else (math.cos(c.t + math.pi * 0.75) > 0)
        col = mat.hi if lit and c.n > 0.5 else mat.light if lit else mat.dark
        if c.n < 0.2 or c.n > 0.85:
            col = mat.deep if not lit else mat.mid
        return gritty(col, c.x, c.y, 31, 0.06, 0.02)
    return f


def kunai(tag=False):
    cv = Canvas()
    grip = flat(None)
    if tag:
        def grip(c):  # the explosive tag wrapped round the handle
            col = PAPER.light if c.n < 0 else PAPER.mid
            if int(c.lt * 3.2) % 2 == 0 and abs(c.n) < 0.35:
                col = BLOOD
            if abs(c.n) > 0.82:
                col = PAPER.dark
            return gritty(col, c.x, c.y, 41, 0.08, 0.05, INK)
    along(cv, *DIAG, [
        (0.19, 0.47, 0.78, 0.78, grip if tag else wrap_spiral(CLOTH, salt=12)),
        (0.47, 0.52, 0.8, 1.7, blade(STEEL, 13, double=True)),
        (0.52, 0.70, 1.7, 2.0, blade(STEEL, 13, double=True, nicks=(0.62,))),
        (0.70, 1.00, 2.0, 0.0, blade(STEEL, 13, double=True)),
    ])
    disc(cv, 2.55, 13.45, 1.75, ring_shader(DSTEEL), r_in=0.85)
    if tag:
        # the loose end of the tag hanging off, with a red seal stroke
        polygon(cv, [(5.2, 10.9), (7.4, 12.6), (6.6, 14.6), (4.6, 13.0)],
                lambda c: gritty(BLOOD if abs(c.u - c.v * 0.55 - (-1.15)) < 0.25 else PAPER.light if c.u < 6 else PAPER.mid, c.x, c.y, 42, 0.08, 0.04))
    outline(cv)
    return cv


def senbon():
    cv = Canvas()
    for off, t0, t1, salt in ((-2.0, 0.08, 0.82, 51), (0.4, 0.18, 0.98, 52), (2.6, 0.26, 0.9, 53)):
        a, b = offset_line(*DIAG, off * 0.75)
        along(cv, a, b, [
            (t0, t0 + 0.05, 0.42, 0.42, metal(DSTEEL, salt)),  # the blunt end
            (t0 + 0.05, t1 - 0.08, 0.34, 0.34, blade(STEEL, salt, double=True)),
            (t1 - 0.08, t1, 0.34, 0.0, blade(STEEL, salt, double=True)),
        ])
    outline(cv)
    return cv


def tanto(glow=False):
    cv = Canvas()
    start, end = (2.4, 13.6), (13.4, 2.6)
    if glow:  # lightning flow: the blade shines white (the White Fang's chakra sabre)
        along(cv, start, end, [
            (0.50, 0.86, 1.3, 1.4, glow_blade(WHITE_LIGHT, 66)),
            (0.86, 1.00, 1.4, 0.15, glow_blade(WHITE_LIGHT, 66)),
        ])
        return cv
    along(cv, start, end, [
        (0.00, 0.06, 1.0, 1.0, metal(DSTEEL, 61)),                    # kashira (pommel cap)
        (0.06, 0.40, 0.92, 0.92, wrap_diamond(CLOTH, CLOTH_RED, 62, 1.0)),
        (0.40, 0.45, 1.75, 1.75, metal(DSTEEL, 63)),                  # small guard
        (0.45, 0.50, 1.05, 1.05, metal(BRASS, 64)),                   # habaki collar
        (0.50, 0.86, 1.05, 1.15, blade(STEEL, 65, nicks=(0.7,))),
        (0.86, 1.00, 1.15, 0.0, blade(STEEL, 65)),                    # a sharp clipped point
    ])
    outline(cv)
    return cv


def ninjato():
    cv = Canvas()
    along(cv, (1.2, 14.8), (14.9, 1.1), [
        (0.00, 0.06, 1.05, 1.05, metal(DSTEEL, 71)),
        (0.06, 0.34, 0.88, 0.88, wrap_diamond(CLOTH, CLOTH_RED, 72, 1.15)),
        (0.34, 0.40, 2.45, 2.45, metal(DSTEEL, 73, rivets=2)),        # the square guard
        (0.40, 0.88, 0.92, 0.92, blade(STEEL, 74, nicks=(0.6, 0.78))),  # straight blade
        (0.88, 1.00, 0.92, 0.1, blade(STEEL, 74)),                    # angled tip
    ])
    outline(cv)
    return cv


def katana(glow=False):
    cv = Canvas()
    start, end = (1.0, 15.0), (15.3, 0.7)
    segs = [
        (0.00, 0.05, 0.95, 0.95, metal(DSTEEL, 81)),
        (0.05, 0.31, 0.85, 0.85, wrap_diamond(CLOTH, IVORY, 82, 1.1)),
        (0.31, 0.335, 0.95, 0.95, metal(BRASS, 83)),
        (0.335, 0.375, 2.15, 2.15, metal(DSTEEL, 84)),               # round-ish tsuba (squared by width)
        (0.375, 0.40, 1.0, 1.0, metal(BRASS, 85)),
        (0.40, 0.90, 0.82, 0.74, blade(STEEL, 86, hamon=True, nicks=(0.58,))),
        (0.90, 1.00, 0.74, 0.0, blade(STEEL, 86, hamon=True)),
    ]
    if glow:
        segs = [s if s[0] < 0.40 else (s[0], s[1], s[2], s[3], glow_blade(LIGHTNING, 87)) for s in segs]
        segs = [s for s in segs if s[0] >= 0.40]
    along(cv, start, end, segs, curve=-0.7, curve_from=0.4)
    if not glow:
        # a round tsuba: a disc over the guard
        disc(cv, 1.0 + (15.3 - 1.0) * 0.355, 15.0 + (0.7 - 15.0) * 0.355, 1.7,
             lambda c: gritty(DSTEEL.hi if math.cos(c.t + 2.36) > 0.55 and c.n > 0.6 else DSTEEL.mid if math.cos(c.t + 2.36) > 0 else DSTEEL.dark,
                              c.x, c.y, 88, 0.07, 0.03))
        along(cv, start, end, segs[1:4])  # grip and collar stay on top of the tsuba's lower half
        outline(cv)
    return cv


def nodachi(glow=False):
    cv = Canvas()
    start, end = (0.7, 15.3), (15.5, 0.5)
    if glow:  # fire flow: flames lick along the blade
        along(cv, start, end, [
            (0.44, 0.88, 1.75, 1.6, flame_blade(FIRE, 97)),
            (0.88, 1.00, 1.6, 0.2, flame_blade(FIRE, 97)),
        ], curve=-0.4, curve_from=0.42)
        return cv
    along(cv, start, end, [
        (0.00, 0.04, 1.05, 1.05, metal(DSTEEL, 91)),
        (0.04, 0.33, 0.95, 0.95, wrap_diamond(CLOTH, CLOTH_RED, 92, 1.25)),  # a long two-hand grip
        (0.33, 0.36, 1.15, 1.15, metal(BRASS, 93)),
        (0.36, 0.40, 2.5, 2.5, metal(DSTEEL, 94, rivets=2)),
        (0.40, 0.44, 1.45, 1.45, metal(DSTEEL, 95)),
        (0.44, 0.88, 1.25, 1.1, blade(STEEL, 96, hamon=True, nicks=(0.55, 0.7, 0.81), blood=0.08)),  # broad, heavy
        (0.88, 1.00, 1.1, 0.0, blade(STEEL, 96, hamon=True, blood=0.12)),
    ], curve=-0.4, curve_from=0.42)
    outline(cv)
    return cv


def chakra_blade():
    cv = Canvas()
    along(cv, *DIAG, [
        (0.10, 0.42, 0.82, 0.82, wrap_spiral(LEATHER, 101, 0.7)),
        (0.42, 0.47, 2.0, 2.0, metal(DSTEEL, 102)),
        (0.47, 0.62, 1.7, 1.95, blade(CHAKRA_STEEL, 103, double=True, glow=LIGHTNING)),
        (0.62, 1.00, 1.95, 0.0, blade(CHAKRA_STEEL, 103, double=True, nicks=(0.8,), glow=LIGHTNING)),
    ])
    # the knuckle bow: an iron bar beside the grip, joined at both ends
    along(cv, (3.4, 15.5), (8.9, 10.0), [(0.0, 1.0, 0.55, 0.55, metal(DSTEEL, 104))])
    outline(cv)
    return cv


def bo_staff():
    cv = Canvas()
    start, end = (0.6, 15.4), (15.4, 0.6)
    along(cv, start, end, [
        (0.00, 0.11, 1.15, 1.15, metal(DSTEEL, 111, rivets=2)),
        (0.11, 0.42, 0.82, 0.82, wood(DARK_WOOD, 112)),
        (0.42, 0.58, 0.9, 0.9, wrap_spiral(CLOTH_RED, 113, 0.6)),   # the grip in the middle
        (0.58, 0.89, 0.82, 0.82, wood(DARK_WOOD, 114)),
        (0.89, 1.00, 1.15, 1.15, metal(DSTEEL, 115, rivets=2)),
    ])
    outline(cv)
    return cv


def yari(glow=False):
    cv = Canvas()
    start, end = (0.5, 15.5), (15.4, 0.6)
    if glow:  # wind flow: a blade of wind around the head, reaching past the tip
        along(cv, start, end, [
            (0.70, 0.80, 0.7, 1.75, glow_blade(WIND, 127)),
            (0.80, 1.00, 1.75, 0.3, glow_blade(WIND, 127)),
        ])
        return cv
    along(cv, start, end, [
        (0.00, 0.05, 0.85, 0.85, metal(DSTEEL, 121)),                 # the butt cap
        (0.05, 0.62, 0.68, 0.68, wood(DARK_WOOD, 122, lacquer=LACQ_RED.light)),
        (0.62, 0.68, 0.95, 0.95, rope(ROPE, 123)),                   # binding
        (0.68, 0.72, 0.8, 0.8, metal(DSTEEL, 124)),                  # collar
        (0.72, 0.80, 0.55, 1.35, blade(STEEL, 125, double=True)),
        (0.80, 1.00, 1.35, 0.0, blade(STEEL, 125, double=True, nicks=(0.9,))),  # a straight leaf head
    ])
    # a blood-red tassel hanging from the collar
    for i, (dx, dy) in enumerate(((0.0, 0.0), (0.55, 0.25), (-0.4, 0.3))):
        along(cv, (10.5 + dx, 5.6 + dy), (9.4 + dx * 1.5, 8.9 + dy * 1.4), [
            (0.0, 1.0, 0.42, 0.25, lambda c, i=i: gritty(CLOTH_RED.light if c.n < -0.2 else CLOTH_RED.dark, c.x, c.y, 126 + i, 0.08, 0.04))])
    outline(cv)
    return cv


def tonfa(glow=False):
    cv = Canvas()
    # Two tonfa crossed: each a long shaft with a side handle near one end.
    for flip in (False, True):
        if flip:
            start, end, hstart, hend = (14.2, 13.6), (2.6, 2.0), (12.0, 11.4), (10.0, 13.9)
        else:
            start, end, hstart, hend = (1.8, 13.6), (13.4, 2.0), (4.0, 11.4), (6.0, 13.9)
        salt = 131 + flip * 10
        if glow:  # earth flow: the wood turns to cracked stone, the cracks glowing
            along(cv, start, end, [(0.06, 0.90, 0.95, 0.95, stone_cracks(EARTH, salt + 5))])
            continue
        along(cv, start, end, [
            (0.00, 0.06, 1.05, 1.05, metal(DSTEEL, salt)),
            (0.06, 0.90, 0.95, 0.95, wood(DARK_WOOD, salt + 1, lacquer=LACQ_BLACK.hi)),
            (0.90, 1.00, 1.05, 1.05, metal(DSTEEL, salt + 2, rivets=1)),
        ])
        along(cv, hstart, hend, [
            (0.0, 0.35, 0.7, 0.7, metal(DSTEEL, salt + 3)),
            (0.35, 1.0, 0.65, 0.65, wrap_spiral(CLOTH, salt + 4, 0.5)),
        ])
        contour(cv)
    if not glow:
        outline(cv)
    return cv


def tekko_kagi(glow=False):
    cv = Canvas()
    # Three long hooked claws reaching to the top right from a riveted iron plate on a leather cuff.
    for i, off in enumerate((-1.55, 0.0, 1.55)):
        a, b = offset_line((5.6, 10.4), (14.6, 1.4), off)
        shader = glow_blade(WIND, 141 + i) if glow else blade(STEEL, 141 + i, double=True, nicks=((0.72,) if i == 1 else ()))
        along(cv, a, b, [
            (0.10, 0.78, 0.62, 0.55, shader),
            (0.78, 1.00, 0.55, 0.0, shader),
        ], curve=0.9, curve_from=0.5)
    if not glow:
        along(cv, (1.8, 14.2), (6.6, 9.4), [(0.0, 1.0, 2.2, 2.2, wrap_spiral(LEATHER, 145, 0.8))])      # the cuff
        along(cv, (3.4, 12.6), (8.0, 8.0), [(0.0, 1.0, 2.0, 1.7, metal(DSTEEL, 146, rivets=3))])        # the plate
        along(cv, (0.7, 15.3), (2.6, 13.4), [(0.0, 1.0, 0.7, 0.7, wrap_spiral(CLOTH_RED, 147, 0.45))])  # the strap's loose end
        outline(cv)
    return cv


def tessen():
    cv = Canvas()
    # An iron war fan, half open: ribs fanning out from a riveted pivot.
    px, py = 3.0, 13.0
    a0, a1 = math.radians(-100), math.radians(-5)
    r_in, r_out = 2.2, 12.8

    def fan(c):
        ang = math.atan2(c.v - py, c.u - px)
        d = math.hypot(c.u - px, c.v - py)
        if not (a0 <= ang <= a1 and r_in <= d <= r_out):
            return None
        q = (ang - a0) / (a1 - a0)
        pleat = (q * 9) % 1
        col = PAPER.mid if pleat < 0.5 else PAPER.dark  # black-washed paper over iron
        col = shade(col, 0.35) if pleat < 0.5 else shade(col, 0.22)
        if pleat < 0.12 or pleat > 0.94:
            col = DSTEEL.light if pleat < 0.12 else DSTEEL.dark  # the iron ribs
        if q < 0.06 or q > 0.94:
            col = DSTEEL.hi if q < 0.03 or (0.94 < q < 0.97) else DSTEEL.mid  # heavy guard ribs
        if r_out - d < 0.75:
            col = DSTEEL.mid if pleat < 0.5 else DSTEEL.dark  # the sharpened iron rim
            if r_out - d < 0.3:
                col = STEEL.hi
        # one dry-brush stroke of blood red across the leaf
        half = 1.3 * max(0.0, 1 - abs(q - 0.5) * 1.7)
        if abs(d - 8.0) < half and r_out - d > 0.75 and hsh(int(q * 46), int(d * 2.5), 154) < 0.86:
            col = BLOOD if pleat < 0.5 else BLOOD_DK
        return gritty(col, c.x, c.y, 151, 0.06, 0.03)

    for x, y, u, v in pixels():
        c = Ctx()
        c.x, c.y, c.u, c.v = x, y, u, v
        col = fan(c)
        if col is not None:
            cv.put(x, y, col)
    disc(cv, px, py, 1.5, lambda c: gritty(DSTEEL.hi if c.n < 0.4 and math.cos(c.t + 2.36) > 0 else DSTEEL.dark, c.x, c.y, 152, 0.06, 0.02))
    # a tassel cord from the pivot
    along(cv, (2.6, 13.6), (1.2, 15.4), [(0.0, 1.0, 0.45, 0.3, rope(CLOTH_RED, 153, 0.4))])
    outline(cv)
    return cv


def facets(mat, centre, salt):
    """Shades a star's blades: each blade split along its ridge into a lit and a shadow facet."""
    cx, cy = centre

    def f(c):
        dx, dy = c.u - cx, c.v - cy
        ang = math.atan2(dy, dx)
        d = math.hypot(dx, dy)
        lit = math.sin(ang * 4 + 0.0) > 0  # alternate facets around the star
        light_dir = -(dx + dy) / max(0.01, d)
        col = (mat.light if light_dir > 0 else mat.mid) if lit else (mat.dark if light_dir < 0.3 else mat.mid)
        if d > 5.3:
            col = mat.hi if lit else col  # the bright edge on the blade tips
        return gritty(col, c.x, c.y, salt, 0.06, 0.03)
    return f


def star(points, outer, inner, rotation, centre=(8, 8)):
    pts = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        a = rotation + i * math.pi / points
        pts.append((centre[0] + r * math.cos(a), centre[1] + r * math.sin(a)))
    return pts


def shuriken():
    cv = Canvas()
    polygon(cv, star(4, 7.0, 1.9, math.pi / 4), facets(STEEL, (8, 8), 161))
    # nicks in two of the blade edges
    for x, y, u, v in pixels():
        if math.hypot(u - 12.0, v - 6.2) < 0.45 or math.hypot(u - 4.6, v - 9.6) < 0.4:
            cv.px[y][x] = None
    punch(cv, 8, 8, 1.05)
    outline(cv)
    return cv


def fuma_shuriken():
    cv = Canvas()
    pts = []
    for b in range(4):
        a = b * math.pi / 2 + math.pi / 4
        pts += [(8 + 2.4 * math.cos(a - 0.55), 8 + 2.4 * math.sin(a - 0.55)),
                (8 + 7.7 * math.cos(a + 0.12), 8 + 7.7 * math.sin(a + 0.12)),   # swept tip
                (8 + 4.4 * math.cos(a + 0.62), 8 + 4.4 * math.sin(a + 0.62))]
    polygon(cv, pts, facets(DSTEEL, (8, 8), 171))
    disc(cv, 8, 8, 2.5, lambda c: gritty(CLOTH_RED.light if (c.t * 3) % 1.0 < 0.5 else CLOTH_RED.dark, c.x, c.y, 172, 0.08, 0.04), r_in=1.4)
    disc(cv, 8, 8, 1.35, lambda c: gritty(DSTEEL.hi if math.cos(c.t + 2.36) > 0.3 else DSTEEL.dark, c.x, c.y, 173, 0.05, 0.02))
    punch(cv, 8, 8, 0.55)
    outline(cv)
    return cv


def explosive_tag():
    cv = Canvas()

    def paper(c):
        col = PAPER.light if c.u < 8.4 else PAPER.mid
        # fibres
        if hsh(int(c.u * 6), int(c.v * 1.5), 181) > 0.86:
            col = PAPER.dark
        # a red border just inside the edge, and a seal: our own glyph (strokes and a circle)
        if 5.55 < c.u < 5.95 or 10.05 < c.u < 10.45:
            col = BLOOD
        cx, cy = 8.0, 5.4
        if 1.1 < math.hypot(c.u - cx, c.v - cy) < 1.65:
            col = BLOOD
        if (abs(c.u - 8.0) < 0.28 and 7.2 < c.v < 13.4) or (abs(c.v - 9.0) < 0.26 and 6.6 < c.u < 9.4) \
                or (abs((c.u - 6.8) - (c.v - 11.0) * 0.8) < 0.26 and 10.6 < c.v < 12.8) \
                or (abs((c.u - 9.2) + (c.v - 11.0) * 0.8) < 0.26 and 10.6 < c.v < 12.8):
            col = INK
        if abs(c.u - cx) < 0.3 and abs(c.v - cy) < 0.3:
            col = INK
        # a scorched corner
        if math.hypot(c.u - 10.6, c.v - 14.6) < 2.0 + 0.5 * hsh(int(c.u * 3), int(c.v * 3), 182):
            col = mix(col, rgb(0x2A1A10), 0.75)
        return gritty(col, c.x, c.y, 183, 0.06, 0.03)
    # torn edges: a slightly irregular quad
    polygon(cv, [(5.0, 1.0), (11.0, 1.2), (10.9, 14.9), (5.1, 14.7)], paper)
    for x, y, u, v in pixels():  # rip a few bites out of the edges
        if cv.px[y][x] is not None and ((u < 5.6 or u > 10.4) and hsh(int(v * 2.5), int(u > 8), 184) > 0.72 and abs(u - 8) > 2.7):
            cv.px[y][x] = None
    outline(cv)
    return cv


# ---------------------------------------------------------------- special weapons (our designs)

def crescent_cleaver():
    cv = Canvas()
    along(cv, (1.4, 14.6), (14.2, 1.8), [
        (0.00, 0.06, 0.95, 0.95, metal(DSTEEL, 191)),
        (0.06, 0.30, 0.85, 0.85, wrap_spiral(CLOTH, 192, 0.7)),
        (0.30, 0.36, 1.7, 1.7, metal(DSTEEL, 193)),
        (0.36, 1.00, 3.1, 3.1, blade(STEEL, 194, nicks=(0.5, 0.66, 0.8, 0.93), blood=0.1)),  # a wide, square-ended slab
    ])
    punch(cv, 10.6, 4.6, 1.15)   # the round hole near the end
    punch(cv, 7.0, 4.6, 1.2)     # the crescent bite out of the back edge
    outline(cv)
    return cv


def sharkskin_greatsword():
    cv = Canvas()

    def scales(c):
        row = int(c.lt * 2.6)
        q = (c.lt * 2.6) % 1
        col = SCALE_SKIN.light if c.n < -0.3 else SCALE_SKIN.mid if c.n < 0.4 else SCALE_SKIN.dark
        if q < 0.2 + 0.15 * abs(math.sin(c.s * 3 + row)):
            col = SCALE_SKIN.deep  # the scale's dark lip
        elif q < 0.35:
            col = SCALE_SKIN.hi if c.n < 0.2 else SCALE_SKIN.light
        if abs(c.n) > 0.88:
            col = SCALE_SKIN.deep
            if row % 2 == 0 and q < 0.5:
                return None  # spines leave notches in the silhouette
        return gritty(col, c.x, c.y, 201, 0.07, 0.03)
    along(cv, (1.2, 14.8), (14.4, 1.6), [
        (0.00, 0.06, 1.0, 1.0, metal(LACQ_BLACK, 202)),
        (0.06, 0.30, 0.85, 0.85, wrap_diamond(CLOTH, CLOTH_RED, 203, 1.0)),
        (0.30, 0.35, 1.8, 1.8, metal(DSTEEL, 204)),
        (0.35, 0.92, 2.4, 2.9, scales),
        (0.92, 1.00, 2.9, 1.3, scales),            # a blunt, rounded head
    ])
    outline(cv)
    return cv


def thunder_fangs():
    cv = Canvas()
    for i, off in enumerate((-2.0, 2.0)):
        a, b = offset_line(*DIAG, off * 0.75)
        along(cv, a, b, [
            (0.06, 0.36, 0.66, 0.66, wrap_spiral(CLOTH, 211 + i, 0.55)),
            (0.36, 0.42, 1.35, 1.35, metal(BRASS, 213 + i)),
            (0.42, 0.95, 0.95, 0.95, blade(BOLT_STEEL, 215 + i, double=True, nicks=(0.7,))),
            (0.95, 1.00, 0.95, 0.0, blade(BOLT_STEEL, 215 + i, double=True)),
        ])
    # a forked spark crawling between the fangs
    along(cv, (8.2, 3.6), (11.6, 6.8), [(0.0, 1.0, 0.28, 0.28, lambda c: LIGHTNING.hi if abs(math.sin(c.lt * 5)) < 0.7 else None)])
    outline(cv)
    return cv


def reedcutter():
    cv = Canvas()
    along(cv, (1.0, 15.0), (15.2, 0.8), [
        (0.00, 0.05, 0.8, 0.8, metal(DSTEEL, 221)),
        (0.05, 0.27, 0.66, 0.66, wrap_spiral(CLOTH_RED, 222, 0.55)),
        (0.27, 0.30, 1.05, 1.05, metal(BRASS, 223)),
        (0.30, 0.94, 0.62, 0.62, blade(STEEL, 224, double=True)),    # a long, slim blade
        (0.94, 1.00, 0.62, 0.0, blade(STEEL, 224, double=True)),
    ])
    outline(cv)
    return cv


def gale_gunbai():
    cv = Canvas()
    along(cv, (1.2, 14.8), (8.2, 7.8), [
        (0.0, 0.12, 0.85, 0.85, metal(DSTEEL, 231)),
        (0.12, 1.0, 0.75, 0.75, wood(DARK_WOOD, 232)),
    ])
    cx, cy = 10.0, 6.0

    def leaf(c):
        d = math.hypot(c.u - cx, (c.v - cy) * 1.1)
        side = -(c.u - cx) - (c.v - cy)
        col = LACQ_RED.light if side > 2.2 else LACQ_RED.dark if side < -2.2 else LACQ_RED.mid
        if 1.6 < side < 2.4:
            col = LACQ_RED.hi  # the lacquer's hard shine
        if 2.5 <= d <= 3.3:
            col = PAPER.light if side > -1 else PAPER.mid  # our ring
        elif d < 1.1:
            col = LACQ_BLACK.mid
        elif d > 4.8:
            col = DSTEEL.mid if side > 0 else DSTEEL.dark  # an iron rim
        return gritty(col, c.x, c.y, 233, 0.05, 0.02)
    disc(cv, cx, cy, 5.35, leaf, squash=1.1)
    # a rope tassel from the handle's end
    along(cv, (1.6, 14.4), (0.6, 15.6), [(0.0, 1.0, 0.45, 0.3, rope(ROPE, 234, 0.4))])
    outline(cv)
    return cv


def kusarigama():
    cv = Canvas()
    along(cv, (3.0, 13.4), (8.6, 4.4), [
        (0.0, 0.1, 0.9, 0.9, metal(DSTEEL, 241)),
        (0.1, 0.92, 0.72, 0.72, wrap_spiral(CLOTH, 242, 0.6)),
        (0.92, 1.0, 0.9, 0.9, metal(DSTEEL, 243)),
    ])
    # the hooked sickle blade, sweeping right from the top of the handle (inner edge sharp)
    for x, y, u, v in pixels():
        d = math.hypot(u - 11.6, v - 7.4)
        ang = math.atan2(v - 7.4, u - 11.6)
        if 3.1 < d < 4.6 and -2.75 < ang < -0.1:
            thick = 4.6 - 0.9 * (ang + 2.75) / 2.65
            if d < thick:
                n = (d - 3.1) / max(0.1, thick - 3.1)
                col = STEEL.hi if n < 0.22 else STEEL.light if n < 0.5 else STEEL.dark
                cv.put(x, y, gritty(col, x, y, 244, 0.06, 0.02))
    # the chain: links curving down to a weight
    for i, (cx, cy) in enumerate(((3.6, 14.6), (5.2, 14.9), (6.8, 14.8), (8.4, 14.4), (9.9, 13.6), (11.2, 12.4))):
        disc(cv, cx, cy, 0.78, lambda c, i=i: gritty(DSTEEL.light if math.cos(c.t + 2.36) > 0 else DSTEEL.dark, c.x, c.y, 245 + i, 0.06, 0.02),
             r_in=0.32, squash=1.0 if i % 2 else 1.6)
    disc(cv, 12.6, 11.0, 1.1, lambda c: gritty(DSTEEL.hi if c.n < 0.5 and math.cos(c.t + 2.36) > 0.3 else DSTEEL.dark, c.x, c.y, 252, 0.06, 0.02))
    outline(cv)
    return cv


ICONS = {
    "kunai": lambda: kunai(),
    "explosive_kunai": lambda: kunai(tag=True),
    "shuriken": shuriken,
    "fuma_shuriken": fuma_shuriken,
    "senbon": senbon,
    "explosive_tag": explosive_tag,
    "bo_staff": bo_staff,
    "ninjato": ninjato,
    "chakra_blade": chakra_blade,
    "crescent_cleaver": crescent_cleaver,
    "sharkskin_greatsword": sharkskin_greatsword,
    "thunder_fangs": thunder_fangs,
    "reedcutter": reedcutter,
    "gale_gunbai": gale_gunbai,
    "kusarigama": kusarigama,
    # v0.13 weapons
    "tanto": lambda: tanto(),
    "katana": lambda: katana(),
    "nodachi": lambda: nodachi(),
    "tonfa": lambda: tonfa(),
    "yari": lambda: yari(),
    "tekko_kagi": lambda: tekko_kagi(),
    "tessen": tessen,
}

# Chakra-flow glow layers (drawn full-bright over the weapon by the item model).
GLOWS = {
    "katana_glow": lambda: katana(glow=True),
    "tekko_kagi_glow": lambda: tekko_kagi(glow=True),
    "nodachi_glow": lambda: nodachi(glow=True),
    "yari_glow": lambda: yari(glow=True),
    "tonfa_glow": lambda: tonfa(glow=True),
    "tanto_glow": lambda: tanto(glow=True),
}


def main():
    if "--scale" in sys.argv:
        configure(int(sys.argv[sys.argv.index("--scale") + 1]))
    os.makedirs(OUT, exist_ok=True)
    images = {}
    for name, draw in list(ICONS.items()) + list(GLOWS.items()):
        images[name] = draw().image()
        images[name].save(os.path.join(OUT, name + ".png"))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        cell = N * 2 + 8
        cols = 8
        rows = (len(images) + cols - 1) // cols
        # each cell: the HD texture at 2x, and below it the icon at hotbar size (16 and 32 px)
        sheet = Image.new("RGBA", (cols * cell, rows * (cell + 48)), (0x8B, 0x8B, 0x8B, 255))
        for i, img in enumerate(images.values()):
            ox, oy = (i % cols) * cell + 4, (i // cols) * (cell + 48) + 4
            sheet.alpha_composite(img.resize((N * 2, N * 2), Image.NEAREST), (ox, oy))
            sheet.alpha_composite(img.resize((16, 16), Image.BOX), (ox, oy + N * 2 + 8))
            sheet.alpha_composite(img.resize((32, 32), Image.BOX), (ox + 24, oy + N * 2 + 8))
        sheet.save(out)
    print("wrote", len(images), f"textures ({N}x{N}) to", OUT)


if __name__ == "__main__":
    main()

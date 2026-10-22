"""HD rendering for NOIRM's worn gear (standard library only).

The gear is still *designed* on the vanilla 64x32 armour layout: each piece's design function
answers "what is at pixel (x, y) of this face" just like before (gen_gear.py, more_gear.py). This
module turns that design into an HD texture SCALE times bigger (SCALE = 4: 256x128):

1. Upscale. Each face is enlarged on its own with Scale2x (twice for 4x), so diagonals and curves
   (collars, straps, crests, eye holes) come out smooth instead of as big stairs.
2. Families. Every colour belongs to a family (a palette: its highlight, base, shade and deep tones,
   plus a material). Tone steps inside one family are blended into soft shading; where two
   families meet there is a crisp seam.
3. Materials give each family its surface at full resolution: woven cloth (twill), brushed steel
   with a specular band, glossy lacquer, porcelain, woven straw, bandage wraps, leather grain,
   twisted rope, fur.
4. Cloth folds: drape lines down the body and legs, creases at the waist, elbows and knees (where
   the jointed BendyArmorModel bends).
5. Seams: a highlight on the top edge of every part and a shadow under it (raised plates get a
   stronger bevel), a dark hem line where cloth ends, and running stitches along cloth seams.
6. Overlays: design functions that work at full resolution in continuous coordinates (crests,
   flowers, rivets, engravings, lacing), drawn either before the shading (so they get seams and
   materials too) or after it (crisp details).

Change SCALE to change the resolution of every worn texture; nothing else depends on it.
The Java side (client/BendyArmorModel) declares the 64x32 layout and Minecraft normalises UVs,
so any SCALE loads correctly.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, ARMOR_PARTS, faces, hx, tone, noise  # noqa: E402

SCALE = 4
BASE_W, BASE_H = 64, 32

# Light per face (Minecraft also shades entity faces, so keep this gentle).
FACE_LIGHT = {"top": 0.6, "front": 0.0, "right": -0.25, "left": -0.25, "back": -0.15, "bottom": -0.6}

# ---------------------------------------------------------------------------
# Colour families and materials
# ---------------------------------------------------------------------------

MATERIALS = ("cloth", "print", "metal", "lacquer", "porcelain", "straw", "wrap", "leather", "rope", "fur", "void")
RAISED = ("metal", "lacquer", "porcelain", "leather")

_REGISTRY = []      # (rgb, family id, material)
_EXACT = {}         # rgb -> (family id, material)
_CACHE = {}
_next_family = [1]


def family(colours, mat="cloth"):
    """Registers colours (rgb tuples) as one family with a material. Returns the family id."""
    assert mat in MATERIALS, mat
    fid = _next_family[0]
    _next_family[0] += 1
    for c in colours:
        c = tuple(c[:3])
        if c in _EXACT:
            continue  # first registration wins (a shared colour keeps its first family)
        _EXACT[c] = (fid, mat)
        _REGISTRY.append((c, fid, mat))
    _CACHE.clear()
    return fid


def pal(base, hi=None, sh=None, deep=None, mat="cloth"):
    """A palette (h highlight, b base, s shade, d deep) registered as one family."""
    b = hx(base)
    p = {"b": b, "h": hx(hi) if hi else tone(b, 1), "s": hx(sh) if sh else tone(b, -1),
         "d": hx(deep) if deep else tone(b, -2.2)}
    family([p["h"], p["b"], p["s"], p["d"]], mat)
    return p


def lookup(c):
    """(family id, material) of a colour. Toned variants of a registered colour join its family."""
    c = tuple(c[:3])
    hit = _EXACT.get(c) or _CACHE.get(c)
    if hit:
        return hit
    best, best_d = None, None
    for rc, fid, mat in _REGISTRY:
        d = abs(rc[0] - c[0]) + abs(rc[1] - c[1]) + abs(rc[2] - c[2])
        if best_d is None or d < best_d:
            best, best_d = (fid, mat), d
    tol = 18 + 0.22 * sum(c)
    if best is None or best_d > tol:
        best = (("own",) + c, "cloth")  # a colour of its own: its own family
    _CACHE[c] = best
    return best


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


# ---------------------------------------------------------------------------
# Layers: the design at base resolution, rendered to HD
# ---------------------------------------------------------------------------

PART_OF = {v: k for k, v in ARMOR_PARTS.items()}

_layer_count = [0]


class Layer:
    """One armour layer (64x32 design). paint() fills it; render() returns the HD Canvas."""

    def __init__(self):
        self.base = [[None] * BASE_W for _ in range(BASE_H)]
        self.faces = []      # (part, face, fx, fy, fw, fh)
        self.design = []     # (box, fn): HD overlays before shading
        self.final = []      # (box, fn): HD overlays after shading
        _layer_count[0] += 1
        self.salt = _layer_count[0] * 7919
        self.fold = 1.0        # how strongly cloth folds show (0 = stiff)
        self.stitches = True   # running stitches along cloth seams
        self.wear = 1.0        # frayed hems, faded patches, dirt toward the feet (0 = brand new)
        self._hd = None

    # -- design ------------------------------------------------------------------------------
    def set(self, x, y, c):
        if 0 <= x < BASE_W and 0 <= y < BASE_H:
            self.base[y][x] = None if c is None else tuple(int(v) for v in c[:3])

    def overlay(self, box, fn, phase="design"):
        """fn(face, u, v, W, H) -> rgb or None, u/v in base pixels (floats), at HD resolution.
        phase "design": before shading (gets seams and material); "final": after it (crisp)."""
        (self.design if phase == "design" else self.final).append((box, fn))
        self._hd = None

    def empty(self):
        return all(c is None for row in self.base for c in row)

    # -- render ------------------------------------------------------------------------------
    def render(self):
        if self._hd is None:
            self._hd = _render(self)
        return self._hd

    def save(self, path):
        self.render().save(path)


def paint_hd(layer, box, fn, phase="design"):
    """Draws a box at full resolution: fn(face, u, v, W, H) -> colour or None, with u, v in base
    pixels (floats; u runs left to right across the face as seen, v top to bottom)."""
    part = PART_OF.get(tuple(box), "body")
    for face, (fx, fy, fw, fh) in faces(box).items():
        layer.faces.append((part, face, fx, fy, fw, fh))
    layer.overlay(box, fn, phase)


def paint(layer, box, fn, light=True):
    """fn(face, x, y, W, H) -> colour or None, like pixlib.paint, into a Layer. Light is applied
    when rendering (the flag is kept for the old signature)."""
    part = PART_OF.get(tuple(box), "body")
    for face, (fx, fy, fw, fh) in faces(box).items():
        layer.faces.append((part, face, fx, fy, fw, fh))
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is None:
                    continue
                if len(c) == 4 and c[3] == 0:
                    continue
                layer.set(fx + x, fy + y, c)
    layer._hd = None


# ---------------------------------------------------------------------------
# Scale2x
# ---------------------------------------------------------------------------

def scale2x(grid):
    h, w = len(grid), len(grid[0])
    out = [[None] * (w * 2) for _ in range(h * 2)]
    for y in range(h):
        for x in range(w):
            e = grid[y][x]
            b = grid[y - 1][x] if y > 0 else e
            hh = grid[y + 1][x] if y < h - 1 else e
            d = grid[y][x - 1] if x > 0 else e
            f = grid[y][x + 1] if x < w - 1 else e
            if b != hh and d != f:
                e0 = d if d == b else e
                e1 = f if b == f else e
                e2 = d if d == hh else e
                e3 = f if hh == f else e
            else:
                e0 = e1 = e2 = e3 = e
            out[2 * y][2 * x] = e0
            out[2 * y][2 * x + 1] = e1
            out[2 * y + 1][2 * x] = e2
            out[2 * y + 1][2 * x + 1] = e3
    return out


def upscale(grid, s):
    while s % 2 == 0 and s > 1:
        grid = scale2x(grid)
        s //= 2
    if s > 1:
        grid = [[row[x // s] for x in range(len(row) * s)] for row in grid for _ in range(s)]
    return grid


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

def _render(layer):
    S = SCALE
    W, H = BASE_W * S, BASE_H * S
    M = [[None] * W for _ in range(H)]
    for part, face, fx, fy, fw, fh in layer.faces:
        grid = [row[fx:fx + fw] for row in layer.base[fy:fy + fh]]
        if all(c is None for r in grid for c in r):
            continue
        up = upscale(grid, S)
        for y in range(fh * S):
            M[fy * S + y][fx * S:(fx + fw) * S] = up[y]
    for box, fn in layer.design:
        _run_overlay(M, box, fn, None)
    out = Canvas(W, H)
    seen = set()
    for part, face, fx, fy, fw, fh in layer.faces:
        if (fx, fy) in seen:
            continue
        seen.add((fx, fy))
        _shade_face(M, out, layer.salt, part, face, fx * S, fy * S, fw * S, fh * S, layer.fold, layer.stitches, layer.wear)
    for box, fn in layer.final:
        _run_overlay(None, box, fn, out)
    return out


def _run_overlay(M, box, fn, out):
    S = SCALE
    for face, (fx, fy, fw, fh) in faces(box).items():
        for y in range(fh * S):
            v = (y + 0.5) / S
            for x in range(fw * S):
                u = (x + 0.5) / S
                c = fn(face, u, v, fw, fh)
                if c is None:
                    continue
                X, Y = fx * S + x, fy * S + y
                if M is not None:
                    M[Y][X] = None if (len(c) == 4 and c[3] == 0) else tuple(int(t) for t in c[:3])
                else:
                    if len(c) == 4 and c[3] == 0:
                        out.set(X, Y, (0, 0, 0, 0))
                    else:
                        out.set(X, Y, tone(c, FACE_LIGHT[face] * 0.5) + (255,))


def _fold_lines(part, face, salt):
    """The folds of one face, worked out once: (kind, a, b, c0, c1, amp, freq, phase, strength).
    'v' folds run down the cloth (u = a + amp * sin(v * freq + phase), for v in c0..c1);
    'h' creases run across it (v = a + amp * sin(u * freq + phase), for u in c0..c1)."""
    key = (part, face, salt)
    if key in _FOLD_CACHE:
        return _FOLD_CACHE[key]
    s = (salt % 997) + {"front": 11, "back": 23, "right": 37, "left": 41}.get(face, 0) * 13
    W = 8.0 if (part == "body" and face in ("front", "back")) else 4.0
    r = lambda i: noise(i, s, 77)  # noqa: E731
    lines = []
    if part == "body":
        for i in range(3 if W > 4 else 2):  # drape folds falling from the chest
            a = (i + 0.35 + 0.4 * r(i)) * W / (3 if W > 4 else 2)
            lines.append(("v", a, 0, 1.0 + 3 * r(i + 10), 7.5 + 4 * r(i + 20), 0.25 + 0.35 * r(i + 30),
                          0.35 + 0.3 * r(i + 40), 6.28 * r(i + 50), 0.9 + 0.3 * r(i + 60)))
        for i in range(2):  # creases where the torso bends
            lines.append(("h", 5.3 + 0.9 * i + 0.3 * r(i + 70), 0, 0.5 + W * 0.3 * r(i + 80), W - W * 0.3 * r(i + 90),
                          0.14, 1.0 + r(i + 100), 6.28 * r(i + 110), 0.6))
    elif part == "arm":
        for i in range(2):  # bunched cloth at the elbow
            lines.append(("h", 5.3 + 0.9 * i + 0.25 * r(i), 0, 0.3 * r(i + 5), 4 - 0.8 * r(i + 6),
                          0.14, 1.2 + r(i + 7), 6.28 * r(i + 8), 0.8))
        lines.append(("v", 1.2 + 1.6 * r(20), 0, 0.5, 4.6, 0.2, 0.7, 6.28 * r(21), 0.7))
        lines.append(("v", 1.5 + 1.2 * r(22), 0, 7.4, 11.5, 0.2, 0.6, 6.28 * r(23), 0.6))
    elif part == "leg":
        for i in range(2):  # over and behind the knee
            lines.append(("h", 5.2 + 0.9 * i + 0.3 * r(i), 0, 0.3 * r(i + 5), 4 - 1.0 * r(i + 6),
                          0.3, 1.5 + r(i + 7), 6.28 * r(i + 8), 0.9))
        for i in range(2):
            lines.append(("v", 0.9 + 2.2 * i + 0.5 * r(i + 20), 0, 0.3 + 2 * r(i + 21), 4.8 + 5 * r(i + 22),
                          0.2, 0.5, 6.28 * r(i + 23), 0.8))
        lines.append(("v", 1.0 + 2.0 * r(30), 0, 7.0, 11.8, 0.25, 0.6, 6.28 * r(31), 0.7))
    _FOLD_CACHE[key] = lines
    return lines


_FOLD_CACHE = {}


def _folds(part, face, u, v, fw, fh, salt):
    """Cloth folds in tone steps: a dark crevice, a lit ridge beside it and a soft shadow on the
    other side (the 'raw' look: hard light edges, deep creases)."""
    if part in ("head", "hat") or face in ("top", "bottom"):
        return 0.0
    k = 0.0
    for kind, a, _, c0, c1, amp, freq, phase, st in _fold_lines(part, face, salt):
        if kind == "v":
            t, d = v, u - (a + amp * math.sin(v * freq + phase))
        else:
            t, d = u, v - (a + amp * math.sin(u * freq + phase))
        if not (c0 - 0.6 <= t <= c1 + 0.6):
            continue
        # taper at both ends of the fold
        taper = min(1.0, (t - c0 + 0.6) / 1.4, (c1 + 0.6 - t) / 1.4) * st
        if taper <= 0:
            continue
        if kind == "h":  # creases across: thin, sharp lines
            w, ridge, shadow = 0.07 + 0.03 * taper, 0.28, 0.3
        else:  # folds falling down the cloth: deep and wide
            w, ridge, shadow = 0.17 * taper + 0.05, 0.6 * taper, 0.8 * taper
        if abs(d) < w:
            k -= 1.3 * taper  # the dark crevice
        elif 0 < d < w + ridge:
            k += 0.55 * taper  # the lit ridge
        elif -(w + shadow) < d < 0:
            k -= 0.45 * taper
    return k


def _scratches(X, Y, salt, cell=7):
    """Short scratches on metal: +1 bright, -1 dark, 0 none."""
    i, j = X // cell, Y // cell
    for di in (0, -1):
        for dj in (0, -1):
            a, b = i + di, j + dj
            if noise(a, b, salt + 5) > 0.45:
                continue
            ang = noise(a, b, salt + 6) * math.pi
            L = cell * (0.6 + 0.9 * noise(a, b, salt + 7))
            x0, y0 = a * cell + cell * noise(a, b, salt + 8), b * cell + cell * noise(a, b, salt + 9)
            x1, y1 = x0 + L * math.cos(ang), y0 + L * math.sin(ang)
            if seg_dist(X + 0.5, Y + 0.5, x0, y0, x1, y1) < 0.55:
                return 1 if noise(a, b, salt + 10) > 0.3 else -1
    return 0


def _texture(mat, X, Y, salt):
    """Surface texture of a material at HD pixel (X, Y), in tone steps."""
    S = SCALE
    grain = (noise(X, Y, salt) - 0.5)
    streak = noise(X, Y // 5, salt + 3)  # dry-brush streaks running down
    if mat in ("cloth", "print"):
        k = -0.1 if (X + Y) % 3 == 0 else 0.0  # twill
        k += grain * 0.22
        if streak > 0.93:
            k -= 0.3
        return k
    if mat == "metal":
        k = (noise(Y * 5 + X // 7, 3, salt) - 0.5) * 0.2  # brushed
        sc = _scratches(X, Y, salt)
        if sc:
            k += 0.75 if sc > 0 else -0.6
        if grain > 0.47:
            k -= 0.6  # pits
        return k
    if mat == "lacquer":
        k = grain * 0.08
        sc = _scratches(X, Y, salt + 50, 9)
        if sc:
            k += 0.55 if sc > 0 else -0.5  # chipped lacquer
        return k
    if mat == "porcelain":
        k = grain * 0.08
        if _scratches(X, Y, salt + 90, 11) < 0:
            k -= 0.5  # hairline cracks
        return k
    if mat == "straw":
        band = (Y // max(1, S // 2)) % 2
        t = (X + (2 if band else 0)) % 4
        k = 0.22 if t == 0 else (-0.35 if t == 3 else 0.0)
        if Y % max(1, S // 2) == 0:
            k -= 0.2
        return k + grain * 0.2
    if mat == "wrap":
        t = (X + 2 * Y) % (2 * S - 1)
        k = -0.65 if t == 0 else (0.25 if t in (1, 2) else 0.0)
        return k + grain * 0.2
    if mat == "leather":
        k = (noise(X // 2, Y // 2, salt) - 0.5) * 0.4 + grain * 0.12
        if _scratches(X, Y, salt + 30, 8) > 0:
            k += 0.4  # scuffs
        return k
    if mat == "rope":
        t = (X + Y) % 4
        return (-0.6 if t == 0 else (0.35 if t == 1 else 0.0)) + grain * 0.1
    if mat == "fur":
        return (noise(X, Y // 3, salt) - 0.5) * 0.8
    return 0.0


DIRT = (58, 46, 36)
STEP = 0.3  # ink look: shading falls into bands this many tone steps apart


def _shade_face(M, out, salt, part, face, X0, Y0, w, h, fold=1.0, stitches=True, wear=1.0):
    S = SCALE
    # Every distinct colour is its own part (seams between parts); its family gives the material.
    cols = [M[Y0 + y][X0:X0 + w] for y in range(h)]
    fams = cols
    mats = [[None if c is None else lookup(c)[1] for c in row] for row in cols]

    def kind(x, y, f):
        if not (0 <= x < w and 0 <= y < h):
            return "out"
        g = fams[y][x]
        if g is None:
            return "none"
        return "same" if g == f else "other"

    def run(x, y, dx, dy, f, cap):
        """How many pixels from (x, y) in direction (dx, dy) stay in family f (max cap)."""
        n = 0
        while n < cap:
            x += dx
            y += dy
            if not (0 <= x < w and 0 <= y < h) or fams[y][x] != f:
                break
            n += 1
        return n

    def stitchable(x, y, f):
        """A different cloth part sewn on here (not a print, plate or open edge)."""
        if not (0 <= x < w and 0 <= y < h):
            return False
        g = fams[y][x]
        return g is not None and g != f and mats[y][x] == "cloth"

    light = FACE_LIGHT[face] * 0.5
    stitch_d = max(2, S // 2)
    soft = ("cloth", "print", "wrap", "fur", "rope")
    for y in range(h):
        v = (y + 0.5) / S
        for x in range(w):
            c = cols[y][x]
            if c is None:
                continue
            f, mat = fams[y][x], mats[y][x]
            X, Y = X0 + x, Y0 + y
            u = (x + 0.5) / S
            if mat == "void":
                out.set(X, Y, tone(c, light) + (255,))
                continue
            nu, nd, nl, nr = kind(x, y - 1, f), kind(x, y + 1, f), kind(x - 1, y, f), kind(x + 1, y, f)
            # frayed hems: open bottom edges of cloth lose threads
            if wear and mat in soft and face not in ("top", "bottom"):
                if nd == "none" and noise(X, Y, salt + 17) < 0.45:
                    continue
                if kind(x, y + 2, f) == "none" and nd == "same" and noise(X // 2, Y, salt + 18) < 0.12:
                    continue
            k = 0.0  # shading (banded), then light and edges on top
            k += _texture(mat, X, Y, salt)
            raised = mat in RAISED
            if mat in soft and fold:
                k += fold * _folds(part, face, u, v, w // S, h // S, salt)
            if raised and face not in ("top", "bottom"):
                # a curved surface: light on top, dark underneath, a hard highlight band
                up, down = run(x, y, 0, -1, f, 6 * S), run(x, y, 0, 1, f, 6 * S)
                rel = up / float(up + down + 1)
                k += 0.5 * (0.4 - rel)
                if mat in ("metal", "lacquer") and 0.12 <= rel <= 0.26 and up + down >= 3:
                    k += 0.7  # hard specular band
            # ink banding with a grain dither
            k = STEP * round((k + (noise(X, Y, salt + 31) - 0.5) * 0.22) / STEP)
            if k < 0:
                k *= 1.25  # ink: shadows go deep
            k += light
            # seams and hems: hard light edges and dark undersides
            if nu in ("other", "none"):
                k += 0.6 if raised else 0.28
            if nd == "other":
                k -= 0.75 if raised else 0.6
            elif nd == "none":
                k -= 0.8
            elif kind(x, y + 2, f) in ("other", "none") and h > 8:
                k -= 0.3
            if nl in ("other", "none"):
                k += 0.12
            if nr in ("other", "none"):
                k -= 0.3
            # running stitches along cloth seams (not along the open edges)
            if stitches and mat == "cloth" and "other" not in (nu, nd, nl, nr) and "none" not in (nu, nd, nl, nr):
                thread = 0.5 if lum(c) < 150 else -0.4
                dash = (stitch_d // 2) or 1
                for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                    if stitchable(x + dx * stitch_d, y + dy * stitch_d, f) and \
                            stitchable(x + dx * (stitch_d + 2), y + dy * (stitch_d + 2), f) and \
                            kind(x - dx * 2 * stitch_d, y - dy * 2 * stitch_d, f) == "same":
                        if ((x if dy else y) // dash) % 2 == 0:
                            k += thread
                        break
            col = tone(c, k)
            if wear:
                # faded patches on cloth
                if mat in soft and noise(X // 9, Y // 9, salt + 41) > 0.82:
                    g = lum(col)
                    col = tuple(min(255, int(t * 0.8 + g * 0.2 + 6)) for t in col)
                # dirt toward the feet (legs and long hems)
                d = 0.0
                if part == "leg" and face != "top":
                    d = max(0.0, (v - 5.0) / 7.0) ** 1.5 * (0.75 if mat in soft or mat == "leather" else 0.45)
                elif part == "body" and face != "top":
                    d = max(0.0, (v - 9.5) / 2.5) ** 2 * 0.3
                elif part == "arm" and face != "top":
                    d = max(0.0, (v - 9.0) / 3.0) ** 2 * 0.25
                if d > 0:
                    m = d * (0.45 + 0.75 * noise(X // 3, Y // 2, salt + 51))
                    if noise(X, Y, salt + 52) > 0.9:
                        m += 0.3  # specks of mud
                    m = min(0.8, m)
                    col = tuple(int(col[i] * (1 - m) + DIRT[i] * m) for i in range(3))
            out.set(X, Y, col + (255,))


# ---------------------------------------------------------------------------
# Shapes for overlays (all in base-pixel units)
# ---------------------------------------------------------------------------

def in_circle(u, v, cx, cy, r):
    return (u - cx) ** 2 + (v - cy) ** 2 <= r * r


def in_ring(u, v, cx, cy, r0, r1):
    d = (u - cx) ** 2 + (v - cy) ** 2
    return r0 * r0 <= d <= r1 * r1


def seg_dist(u, v, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((u - ax) * dx + (v - ay) * dy) / L))
    px, py = ax + t * dx, ay + t * dy
    return math.hypot(u - px, v - py)


def on_polyline(u, v, pts, width):
    for i in range(len(pts) - 1):
        if seg_dist(u, v, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]) <= width / 2.0:
            return True
    return False


def in_poly(u, v, pts):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > v) != (yj > v) and u < (xj - xi) * (v - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def in_rrect(u, v, x0, y0, x1, y1, r):
    """Inside a rounded rectangle."""
    if not (x0 <= u <= x1 and y0 <= v <= y1):
        return False
    cx = min(max(u, x0 + r), x1 - r)
    cy = min(max(v, y0 + r), y1 - r)
    return (u - cx) ** 2 + (v - cy) ** 2 <= r * r


def px():
    """The size of one HD pixel in base units."""
    return 1.0 / SCALE


# ---------------------------------------------------------------------------
# Garment helpers shared by gen_gear.py and more_gear.py (base-pixel units)
# ---------------------------------------------------------------------------

def side(face, u, W):
    """On a side face: distance from the back edge (the 'right' face runs back -> front)."""
    return W - u if face == "left" else u


def engrave(u, v, pts, width, dark, light):
    """An engraved line: dark groove with a light lip below-right. For the 'final' phase."""
    if on_polyline(u, v, pts, width):
        return dark
    o = 1.0 / SCALE
    if on_polyline(u - o, v - o, pts, width):
        return light
    return None


def arc(cx, cy, r, a0, a1, n=24):
    """Points along an arc (degrees, clockwise on screen as v grows down)."""
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def spiral(cx, cy, r0, r1, turns, a0=0.0, n=80):
    return [(cx + (r0 + (r1 - r0) * i / n) * math.cos(a0 + turns * 2 * math.pi * i / n),
             cy + (r0 + (r1 - r0) * i / n) * math.sin(a0 + turns * 2 * math.pi * i / n)) for i in range(n + 1)]


def flower(u, v, cx, cy, r):
    """A five-petal flower: 'centre', 'petal' or None."""
    dx, dy = u - cx, v - cy
    d = math.hypot(dx, dy)
    if d > r:
        return None
    if d < r * 0.3:
        return "centre"
    petal = r * (0.5 + 0.5 * abs(math.cos(2.5 * math.atan2(dy, dx))))
    return "petal" if d <= petal else None


def scatter(u, v, salt, cell=2.6, chance=0.55):
    """Deterministic scattered spots: the (cx, cy) of the spot whose cell holds (u, v), or None."""
    i, j = math.floor(u / cell), math.floor(v / cell)
    for di in (0, -1, 1):
        for dj in (0, -1, 1):
            a, b = i + di, j + dj
            if noise(a, b, salt) > chance:
                continue
            cx = (a + 0.25 + 0.5 * noise(a, b, salt + 1)) * cell
            cy = (b + 0.25 + 0.5 * noise(a, b, salt + 2)) * cell
            if abs(u - cx) < cell * 0.5 and abs(v - cy) < cell * 0.5:
                return cx, cy
    return None


def cross_collar(u, v, trim_w=0.75, depth=4.6, edge_u=5.2):
    """A wrapped robe's crossed collar on the body's front face (8 x 12).
    Returns 'open' (the V where whatever is under shows), 'trim' (the collar band),
    'edge' (the overlapping front edge falling to the hem) or None (the robe)."""
    ax, bx, by = 1.3, edge_u, depth          # the panel from the viewer's left crosses over
    cx, dx, dy = 6.7, 3.7, depth * 0.8       # the panel from the viewer's right goes under it
    right_of_top = (bx - ax) * v - by * (u - ax) < 0
    if seg_dist(u, v, ax, 0.0, bx, by) <= trim_w / 2:
        return "trim"
    if right_of_top and seg_dist(u, v, cx, 0.0, dx, dy) <= trim_w / 2:
        return "trim"
    # the V between the two collar bands
    t = (cx - ax) / ((bx - ax) / by + (cx - dx) / dy)  # where the two lines cross (v)
    if right_of_top and v < t and (cx - dx) * v - dy * (cx - u) < 0:
        return "open"
    if v >= depth and abs(u - edge_u) <= 0.13:
        return "edge"
    return None

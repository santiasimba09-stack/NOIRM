"""Paints a hound's fur onto its packed box UVs (docs/ART_STYLE.md: ink first, raw grain).

Every box knows its role (head, snout, ear, paw, scarf…) and every face of a box its direction,
so the painter can put eyes on the front of the skull, a nose on the front of the muzzle, toes on
the paws and a lighter belly underneath. All drawn from seeded noise and hand-placed shapes.
"""
import math

import numpy as np

from rig import TEX_SCALE

INK = np.array([0.06, 0.05, 0.05])
BONE = np.array([0.93, 0.90, 0.84])
BLOOD = np.array([0.55, 0.09, 0.08])


def hexc(s):
    s = s.lstrip("#")
    return np.array([int(s[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


# Coats chosen at bonding. base / back (saddle) / belly / points (muzzle, legs, brows) / ears / eyes.
COATS = {
    # Akamaru-like: white with tan ears.
    "white": dict(base="#e6e1d6", back="#d2ccbf", belly="#f4f1ea", points="#ece8df", ear="#c69a6a",
                  inner="#b07a6a", eye="#2a1a12", nose="#1a1414"),
    # A grey wolf: dark saddle, pale mask and belly, amber eyes.
    "grey": dict(base="#8f8b86", back="#55524f", belly="#d4cfc6", points="#cdc7bc", ear="#5a5550",
                 inner="#8a6a64", eye="#c48a2a", nose="#141212"),
    # Black with tan points (brows, muzzle, legs, chest), like an old clan tracker.
    "black": dict(base="#2a2726", back="#1c1a1a", belly="#3a3533", points="#a8743e", ear="#221f1f",
                  inner="#5a3a36", eye="#c8902e", nose="#0e0c0c"),
    # Brown: a warm coat with a darker back and a cream muzzle.
    "brown": dict(base="#8a5a34", back="#5e3a22", belly="#c8a07a", points="#d2b08a", ear="#5a3820",
                  inner="#8a5a4a", eye="#3a2412", nose="#161010"),
}

# Face directions in the box-UV layout: (name, u0, v0, width-axis, height-axis).
#   top/bottom rows: width w, height d;  sides: west (d x h), north = front (w x h), east (d x h), south = back (w x h)


def face_rects(cube):
    """(face, x0, y0, x1, y1) in texels for the six faces of one box."""
    u, v = cube["uv"]
    w, h, d = cube["size"]
    s = TEX_SCALE
    r = lambda a, b, c, e: (int(round(a * s)), int(round(b * s)), int(round(c * s)), int(round(e * s)))  # noqa: E731
    return [
        ("top",) + r(u + d, v, u + d + w, v + d),
        ("bottom",) + r(u + d + w, v, u + d + 2 * w, v + d),
        ("west",) + r(u, v + d, u + d, v + d + h),
        ("front",) + r(u + d, v + d, u + d + w, v + d + h),
        ("east",) + r(u + d + w, v + d, u + 2 * d + w, v + d + h),
        ("back",) + r(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    ]


class Painter:
    def __init__(self, tex_w, tex_h, coat, stage, seed):
        self.img = np.zeros((tex_h * TEX_SCALE, tex_w * TEX_SCALE, 4))
        self.c = {k: hexc(v) for k, v in coat.items()}
        self.stage = stage
        self.rng = np.random.default_rng(seed)
        # Older hounds: longer, rougher fur and harder ink; the puppy is soft.
        self.age = {"puppy": 0.0, "young": 0.35, "grown": 0.7, "great": 0.9, "giant": 1.0}[stage]

    # ------------------------------------------------------------------ colour of a box

    def base_colour(self, role, face):
        c = self.c
        if role in ("scarf", "scarf_knot"):
            return BLOOD
        if role == "fang":
            return BONE
        if role in ("ear", "ear_tip"):
            return c["ear"] if role == "ear_tip" or face in ("back", "top") else mix(c["ear"], c["base"], 0.4)
        if role in ("snout", "jaw"):
            return c["points"]
        if role in ("paw", "leg_lower"):
            return mix(c["base"], c["points"], 0.75)
        if role == "belly":
            return c["belly"]
        if face == "bottom":
            return c["belly"]
        if role in ("chest", "hips", "mane", "neck", "thigh") and face == "top":
            return c["back"]
        if role in ("ruff", "tuft"):
            return mix(c["base"], c["belly"], 0.55)
        if role in ("tail_tip",):
            return mix(c["back"], c["base"], 0.3)
        return c["base"]

    # ------------------------------------------------------------------ painting

    def paint_cube(self, cube):
        role = cube["role"]
        for face, x0, y0, x1, y1 in face_rects(cube):
            if x1 <= x0 or y1 <= y0:
                continue
            fw, fh = x1 - x0, y1 - y0
            col = self.base_colour(role, face)
            tile = np.ones((fh, fw, 3)) * col
            yy, xx = np.mgrid[0:fh, 0:fw]
            ty = yy / max(1, fh - 1)
            tx = xx / max(1, fw - 1)
            side = face in ("west", "east", "front", "back")
            if side and role not in ("fang",):
                # Lit from above: bone highlight at the top, a deep shadow towards the bottom.
                shade = 1.10 - 0.42 * ty ** 1.3 * (0.6 + 0.4 * self.age)
                if role in ("chest", "hips", "neck", "thigh", "mane") and face in ("west", "east"):
                    # The saddle: the back colour creeps down the upper flank.
                    saddle = np.clip(1.0 - ty * 2.2, 0, 1)[..., None]
                    tile = tile * (1 - saddle * 0.8) + self.c["back"] * saddle * 0.8
                    belly = np.clip((ty - 0.72) * 3.5, 0, 1)[..., None]
                    tile = tile * (1 - belly) + self.c["belly"] * belly
                tile = tile * shade[..., None]
            elif face == "top":
                tile = tile * 1.06
            elif face == "bottom":
                tile = tile * 0.62
            if role not in ("scarf", "scarf_knot", "fang"):
                tile = self.fur(tile, face, fw, fh)
            else:
                tile = self.cloth(tile, face, fw, fh, role)
            tile = self.grain(tile)
            alpha = np.ones((fh, fw, 1))
            tile = self.features(tile, alpha, role, face, fw, fh, cube)
            tile = self.ink_edges(tile, face, role, fw, fh)
            self.img[y0:y1, x0:x1, :3] = np.clip(tile, 0, 1)
            self.img[y0:y1, x0:x1, 3:] = alpha

    def fur(self, tile, face, fw, fh):
        """Short strands: darker and lighter strokes running down the sides (along the body on top)."""
        n = int(fw * fh / (10 - 4 * self.age))
        length = 2 + int(3 + 6 * self.age)
        vertical = face in ("west", "east", "front", "back")
        for _ in range(n):
            x = self.rng.integers(0, fw)
            y = self.rng.integers(0, fh)
            dark = self.rng.random() < 0.6
            k = (0.72 - 0.12 * self.age) if dark else (1.12 + 0.05 * self.age)
            ln = self.rng.integers(2, length + 1)
            for i in range(ln):
                yy, xx = (y + i, x + (i // 4 if self.rng.random() < 0.3 else 0)) if vertical else (y + (i // 4), x + i)
                if 0 <= yy < fh and 0 <= xx < fw:
                    f = 1.0 - i / (ln + 1)
                    tile[yy, xx] = tile[yy, xx] * (1 - f * 0.55 + f * 0.55 * k)
        return tile

    def cloth(self, tile, face, fw, fh, role):
        if role == "fang":
            return tile
        # Diagonal folds with dark crevices and lit ridges.
        yy, xx = np.mgrid[0:fh, 0:fw]
        fold = np.sin((xx + yy * 0.6) / 3.2 + self.rng.random() * 6)
        tile = tile * (0.85 + 0.25 * fold[..., None])
        return tile

    def grain(self, tile):
        n = self.rng.normal(0, 0.035 + 0.02 * self.age, tile.shape[:2])
        return tile * (1 + n[..., None])

    def ink_edges(self, tile, face, role, fw, fh):
        """The ink line round every face, heavier on the bottom edge; a ragged fringe for fur boxes."""
        k = 0.45 + 0.3 * self.age
        if role in ("fang",):
            k = 0.3
        t = tile.copy()
        t[0, :] *= 1 - k * 0.6
        t[-1, :] *= 1 - k
        t[:, 0] *= 1 - k * 0.7
        t[:, -1] *= 1 - k * 0.7
        if face in ("west", "east", "front", "back") and role in ("ruff", "mane", "tuft", "chest", "thigh", "tail", "tail_tip") and fh > 4:
            # Ragged fur fringe: dark notches up from the bottom edge.
            for x in range(fw):
                d = int(self.rng.integers(0, 2 + int(3 * self.age)))
                t[max(0, fh - 1 - d):fh, x] *= 0.55
        return t

    # ------------------------------------------------------------------ features

    def features(self, tile, alpha, role, face, fw, fh, cube):
        c = self.c
        s = TEX_SCALE
        if role == "head" and face == "front":
            tile = self.eyes(tile, fw, fh)
            if self.c is not None and "points" in c:
                # Brow spots (tan points on the black coat; a faint lighter brow on the others).
                for ex in (0.24, 0.76):
                    cx, cy = int(fw * ex), int(fh * 0.26)
                    blob(tile, cx, cy, s * 0.5, s * 0.35, mix(c["points"], tile[cy, cx], 0.35))
        elif role == "brow" and face == "front":
            tile[-2:, :] *= 0.35
        elif role == "snout":
            if face == "front":
                # The nose: a wide dark wedge at the top, a highlight, nostrils.
                nw, nh = fw * 0.62, fh * 0.42
                cx, cy = fw / 2, nh / 2 + 1
                blob(tile, cx, cy, nw / 2, nh / 2, c["nose"], square=0.6)
                blob(tile, cx - nw * 0.15, cy - nh * 0.2, nw * 0.12, nh * 0.15, mix(c["nose"], BONE, 0.45))
                for sx in (-1, 1):
                    blob(tile, cx + sx * nw * 0.22, cy + nh * 0.15, max(1, nw * 0.08), max(1, nh * 0.12), INK)
                # The philtrum down to the lip.
                tile[int(cy + nh / 2):, int(cx) - 1:int(cx) + 1] *= 0.45
                tile[-max(1, s // 2):, :] *= 0.4
            elif face == "top":
                # The bridge of the nose: a darker stripe for wolves, the nose leather at the front end.
                tile[-max(2, int(s * 0.8)):, int(fw * 0.25):int(fw * 0.75)] = c["nose"]
            elif face in ("west", "east"):
                tile[-max(1, s // 2):, :] *= 0.35  # the lip line
                for i in range(3):
                    blob(tile, fw * (0.25 + 0.2 * i), fh * 0.55, 0.7, 0.7, mix(tile[0, 0], INK, 0.6))
        elif role == "jaw":
            if face == "front" or face in ("west", "east"):
                tile[:max(1, s // 2), :] *= 0.3  # the mouth line
                if face == "front":
                    # Lower teeth and two small fangs.
                    tile[max(1, s // 2):max(2, s // 2) + 1, 1:-1] = BONE * 0.95
                    for fx in (1, fw - 3):
                        tile[0:min(fh, s), fx:fx + 2] = BONE
        elif role in ("ear", "ear_tip") and face == "front":
            # The inner ear: a dark, warm hollow.
            blob(tile, fw / 2, fh * (0.6 if role == "ear" else 0.4), fw * 0.32, fh * 0.42, mix(c["inner"], INK, 0.3))
        elif role == "paw":
            if face == "front":
                # Toes and claws.
                for i in range(1, 4):
                    x = int(fw * i / 4)
                    tile[:, max(0, x - 1):x] *= 0.45
                for i in range(4):
                    x = int(fw * (i + 0.5) / 4)
                    tile[-max(2, s // 2):, max(0, x - 1):x + 1] = BONE * 0.8
            elif face == "bottom":
                tile[:] = mix(INK, c["nose"], 0.5)
                blob(tile, fw / 2, fh * 0.6, fw * 0.3, fh * 0.25, INK * 1.5)
        elif role == "fang":
            tile[-1:, :] *= 0.7
        elif role in ("scarf", "scarf_knot"):
            # A frayed hem and a stitched seam.
            tile[-1:, ::2] *= 0.4
            if role == "scarf" and face in ("west", "east", "front", "back"):
                tile[1:2, ::3] = mix(BLOOD, BONE, 0.4)
        elif role in ("chest",) and face == "front":
            # A pale throat blaze.
            blob(tile, fw / 2, fh * 0.55, fw * 0.28, fh * 0.38, mix(tile[int(fh * 0.5), fw // 2], c["belly"], 0.7))
        if self.stage in ("great", "giant") and role in ("snout", "head") and face in ("west", "east"):
            # Old scars: a pale diagonal nick across the muzzle and brow.
            for i in range(min(fw, fh) - 1):
                x, y = int(fw * 0.3) + i // 2, int(fh * 0.2) + i
                if 0 <= x < fw and 0 <= y < fh:
                    tile[y, x] = mix(tile[y, x], BONE, 0.55)
        return tile

    def eyes(self, tile, fw, fh):
        """Sharp eyes that narrow with age: round and bright on a puppy, a hard leaf on a great hound."""
        c = self.c
        s = TEX_SCALE
        a = self.age
        ew = s * (1.55 + 0.45 * a)       # width
        eh = s * (1.45 - 0.75 * a)       # height
        for side, ex in ((-1, 0.27), (1, 0.73)):
            cx, cy = fw * ex, fh * (0.44 + 0.04 * a)
            # A dark socket round the eye.
            blob(tile, cx, cy, ew * 0.75, eh * 0.95 + 1, mix(tile[int(cy), int(cx)], INK, 0.45))
            yy, xx = np.mgrid[0:tile.shape[0], 0:tile.shape[1]]
            dx = (xx - cx) / (ew / 2)
            dy = (yy - cy) / (eh / 2)
            if a < 0.3:
                inside = dx ** 2 + dy ** 2 <= 1.0
            else:
                # A leaf: the lids meet in sharp points; the outer corner tilts up.
                tilt = side * 0.25 * a
                dyt = dy + tilt * dx * (eh and ew / eh)
                inside = (np.abs(dyt) <= (1 - dx ** 2) ** (0.9 + a)) & (np.abs(dx) <= 1)
            tile[inside] = c["eye"]
            # Pupil, dark rim, one highlight.
            pupil = (dx * 1.9) ** 2 + (dy * 1.6) ** 2 <= 1.0
            tile[pupil & inside] = INK
            rim = inside & ~((dx * 1.12) ** 2 + (dy * 1.12) ** 2 <= 1.0)
            tile[rim] = tile[rim] * 0.5
            hx_, hy_ = int(cx - side * ew * 0.15), int(cy - eh * 0.2)
            if 0 <= hy_ < tile.shape[0] and 0 <= hx_ < tile.shape[1]:
                tile[hy_, hx_] = BONE
                if a < 0.3 and hx_ + 1 < tile.shape[1]:
                    tile[hy_, hx_ + 1] = BONE
            # The heavy upper lash/lid line.
            top = inside & ~np.roll(inside, 1, axis=0)
            tile[np.roll(top, -1, axis=0) | top] = INK
        return tile


def mix(a, b, t):
    return np.asarray(a) * (1 - t) + np.asarray(b) * t


def blob(tile, cx, cy, rx, ry, colour, square=0.0):
    """Fills an ellipse (or a rounded rectangle when `square` > 0) on a tile."""
    h, w = tile.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    dx = (xx + 0.5 - cx) / max(0.5, rx)
    dy = (yy + 0.5 - cy) / max(0.5, ry)
    p = 2 + square * 6
    inside = np.abs(dx) ** p + np.abs(dy) ** p <= 1
    tile[inside] = colour

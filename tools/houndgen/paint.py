"""Paints the hound's atlas layers (docs/ART_STYLE.md: ink first, raw grain; docs/INUZUKA.md).

The game mixes these per hound at runtime (client/hound/HoundTextures), so one set of layers gives
every breed, pattern and colour:

    fur_<age>.png      R = light and shadow (shade / 1.4), G = countershading (pale belly, throat),
                       A = which texels are used. <age> = young (soft puppy fluff) or old (rough).
    pattern_<p>.png    R = where the second colour goes, G = where white goes (saddle, mask, socks,
                       spotted, brindle; a solid coat has no file).
    marks_<m>.png      G = optional white markings (blaze, chest, tail_tip, brows).
    details_<age>.png  already coloured: eyes, nose, mouth, teeth, claws, pads, inner ears, fangs and
                       the red clan scarf. Iris texels carry alpha 254: the game paints them in the
                       hound's eye colour.
"""
import zlib

import numpy as np

from atlas import layout

INK = np.array([0.06, 0.05, 0.05])
BONE = np.array([0.93, 0.90, 0.84])
BLOOD = np.array([0.55, 0.09, 0.08])
NOSE = np.array([0.08, 0.065, 0.06])
INNER = np.array([0.42, 0.26, 0.24])
SIDES = ("west", "east", "front", "back")
FIXED = ("scarf", "scarf_knot", "fang")
BODY = ("chest", "hips", "neck", "mane", "thigh", "belly")


class Layers:
    def __init__(self, seed=7):
        self.rects, self.h = layout()
        self.w = 256
        self.rng = np.random.default_rng(seed)

    def blank(self, channels=4):
        return np.zeros((self.h, self.w, channels))

    def faces(self):
        for role, faces in self.rects.items():
            for face, (x0, y0, x1, y1) in faces.items():
                yield role, face, x0, y0, x1, y1

    # ================================================================ fur: light, shadow, strands

    def fur(self, age):
        """age 0 = puppy fluff (soft, round, little ink), 1 = an old fighter (long rough strands, hard ink)."""
        img = self.blank()
        rng = np.random.default_rng(11 + int(age * 10))
        for role, face, x0, y0, x1, y1 in self.faces():
            fw, fh = x1 - x0, y1 - y0
            yy, xx = np.mgrid[0:fh, 0:fw]
            ty = yy / max(1, fh - 1)
            if role in FIXED:
                shade = np.ones((fh, fw))
            elif face in SIDES:
                # Lit from above: bone highlight at the top, deep shadow toward the bottom (harder with age).
                shade = 1.12 - (0.30 + 0.22 * age) * ty ** 1.25
            elif face == "top":
                shade = np.full((fh, fw), 1.08)
            else:
                shade = np.full((fh, fw), 0.62)
            if role not in FIXED:
                shade = self.strands(shade, face, age, rng)
                shade *= 1 + rng.normal(0, 0.03 + 0.03 * age, shade.shape)
                shade = self.ink_edges(shade, face, role, age, rng)
            counter = self.countershade(role, face, fw, fh, ty)
            img[y0:y1, x0:x1, 0] = np.clip(shade / 1.4, 0, 1)
            img[y0:y1, x0:x1, 1] = counter
            img[y0:y1, x0:x1, 3] = 1
        return img

    def strands(self, shade, face, age, rng):
        """Short fur strokes: darker and lighter, running down the sides and along the back."""
        fh, fw = shade.shape
        n = int(fw * fh / (9 - 4 * age))
        length = 2 + int(2 + 6 * age)
        vertical = face in SIDES
        for _ in range(n):
            x, y = rng.integers(0, fw), rng.integers(0, fh)
            dark = rng.random() < 0.58
            k = (0.70 - 0.12 * age) if dark else (1.10 + 0.06 * age)
            ln = rng.integers(2, length + 1)
            drift = rng.random() < 0.35
            for i in range(ln):
                yy, xx = (y + i, x + (i // 3 if drift else 0)) if vertical else (y + (i // 3 if drift else 0), x + i)
                if 0 <= yy < fh and 0 <= xx < fw:
                    f = 1.0 - i / (ln + 1)
                    shade[yy, xx] *= 1 - f * 0.5 + f * 0.5 * k
        if age < 0.4:
            # Puppy fluff: blur the strokes into soft tufts.
            soft = shade.copy()
            soft[1:-1, 1:-1] = (shade[1:-1, 1:-1] * 2 + shade[:-2, 1:-1] + shade[2:, 1:-1] + shade[1:-1, :-2] + shade[1:-1, 2:]) / 6
            shade = soft * (1 - age) + shade * age
        return shade

    def ink_edges(self, shade, face, role, age, rng):
        """The ink line round every face (heavier at the bottom) and a ragged fringe on furry boxes."""
        k = 0.35 + 0.35 * age
        s = shade.copy()
        fh, fw = s.shape
        s[0, :] *= 1 - k * 0.5
        s[-1, :] *= 1 - k
        s[:, 0] *= 1 - k * 0.6
        s[:, -1] *= 1 - k * 0.6
        if face in SIDES and role in ("ruff", "mane", "tuft", "chest", "thigh", "tail", "tail_tip", "neck") and fh > 4:
            for x in range(fw):
                d = int(rng.integers(0, 2 + int(4 * age)))
                s[max(0, fh - 1 - d):fh, x] *= 0.55
        return s

    def countershade(self, role, face, fw, fh, ty):
        """Where the coat goes pale: belly, throat, inside of the jaw, under the tail."""
        c = np.zeros((fh, fw))
        if role in FIXED:
            return c
        if face == "bottom":
            c[:] = 1.0
        elif role in BODY and face in ("west", "east"):
            c = np.clip((ty - 0.62) * 3.0, 0, 1)
        elif role == "chest" and face == "front":
            c = np.clip(0.4 + ty * 0.6, 0, 1)
        elif role == "jaw":
            c[:] = 0.65
        elif role == "snout" and face in ("west", "east", "front"):
            c = np.clip((ty - 0.45) * 1.6, 0, 1) * 0.6
        elif role == "head" and face == "front":
            c = np.clip((ty - 0.7) * 2.5, 0, 1) * 0.5
        elif role in ("ruff", "tuft"):
            c[:] = 0.6 if face == "front" else 0.35
        elif role in ("leg", "leg_lower") and face == "back":
            c[:] = 0.3
        return c

    # ================================================================ patterns and markings

    def pattern(self, name):
        img = self.blank()
        rng = np.random.default_rng(zlib.crc32(name.encode()) & 0xffff)
        for role, face, x0, y0, x1, y1 in self.faces():
            if role in FIXED:
                continue
            fw, fh = x1 - x0, y1 - y0
            yy, xx = np.mgrid[0:fh, 0:fw]
            ty = yy / max(1, fh - 1)
            tx = xx / max(1, fw - 1)
            second = np.zeros((fh, fw))
            white = np.zeros((fh, fw))
            ragged = rng.normal(0, 0.06, (fh, fw)) + 0.05 * np.sin(xx * 0.9 + rng.random() * 6)
            if name == "saddle":
                if role in ("chest", "hips", "neck", "mane") and face == "top":
                    second[:] = 1
                elif role in ("chest", "hips", "neck", "mane") and face in ("west", "east"):
                    second = (ty + ragged < 0.5).astype(float)
                elif role in ("chest", "hips") and face == "back":
                    second = (ty + ragged < 0.45).astype(float)
                elif role == "thigh" and face in ("west", "east"):
                    second = (ty + ragged < 0.35).astype(float)
                elif role == "head" and face in ("top", "back"):
                    second[:] = 1
                elif role == "head" and face in ("west", "east"):
                    second = (ty + ragged < 0.35).astype(float)
                elif role in ("ear", "ear_tip") and face != "front":
                    second[:] = 0.9
                elif role in ("tail", "tail_tip") and face == "top":
                    second[:] = 1
                elif role in ("tail", "tail_tip") and face in ("west", "east", "back"):
                    second = (ty + ragged < 0.5).astype(float)
            elif name == "mask":
                if role == "snout" and face != "bottom":
                    second[:] = 1
                elif role == "jaw" and face in ("front", "west", "east"):
                    second[:] = 0.8
                elif role == "head" and face == "front":
                    band = (ty > 0.22 + ragged) & (ty < 0.68 + ragged)
                    second = band.astype(float)
                    second = np.maximum(second, ((ty > 0.6) & (np.abs(tx - 0.5) < 0.3)).astype(float))
                elif role == "head" and face in ("west", "east"):
                    second = ((tx < 0.45 + ragged) & (ty > 0.2) & (ty < 0.75)).astype(float)
                elif role in ("ear", "ear_tip", "brow"):
                    second[:] = 1 if face != "front" or role == "brow" else 0.5
            elif name == "socks":
                if role in ("leg_lower", "paw"):
                    white[:] = 1
                elif role == "leg" and face in SIDES:
                    white = (ty + ragged > 0.6).astype(float)
            elif name == "spotted":
                if not (role in ("snout", "jaw", "fang", "brow") or (role == "head" and face == "front")):
                    n = max(1, int(fw * fh / 90))
                    for _ in range(n):
                        cx, cy = rng.random() * fw, rng.random() * fh
                        r = 1.5 + rng.random() * min(5.0, max(fw, fh) * 0.25)
                        d = ((xx - cx) / r) ** 2 + ((yy - cy) / (r * (0.7 + 0.6 * rng.random()))) ** 2
                        second = np.maximum(second, (d + rng.normal(0, 0.15, d.shape) < 1).astype(float))
            elif name == "brindle":
                if role in ("chest", "hips", "neck", "thigh", "leg", "mane", "tail") or (role == "head" and face in ("west", "east", "top")):
                    if face in SIDES or face == "top":
                        along = xx if face in SIDES else yy
                        wave = np.sin(along * 0.85 + np.sin(yy * 0.35 + xx * 0.1) * 2.2 + rng.random() * 6)
                        noise = rng.normal(0, 0.35, (fh, fw))
                        second = np.clip((wave + noise - 0.25) * 1.6, 0, 1) * 0.9
            img[y0:y1, x0:x1, 0] = np.clip(second, 0, 1)
            img[y0:y1, x0:x1, 1] = np.clip(white, 0, 1)
            img[y0:y1, x0:x1, 3] = 1
        return img

    def marks(self, name):
        img = self.blank()
        rng = np.random.default_rng((zlib.crc32(name.encode()) & 0xffff) + 5)
        for role, face, x0, y0, x1, y1 in self.faces():
            fw, fh = x1 - x0, y1 - y0
            yy, xx = np.mgrid[0:fh, 0:fw]
            ty = yy / max(1, fh - 1)
            tx = xx / max(1, fw - 1)
            white = np.zeros((fh, fw))
            jitter = rng.normal(0, 0.04, (fh, fw))
            if name == "blaze":
                if role == "head" and face == "front":
                    width = 0.10 + 0.22 * ty
                    white = (np.abs(tx - 0.5) + jitter < width).astype(float)
                elif role == "head" and face == "top":
                    white = ((np.abs(tx - 0.5) + jitter < 0.12) & (ty < 0.5)).astype(float)
                elif role == "snout" and face in ("top", "front"):
                    white = (np.abs(tx - 0.5) + jitter < 0.32).astype(float)
                elif role == "brow" and face in ("front", "top"):
                    white = (np.abs(tx - 0.5) < 0.15).astype(float)
            elif name == "chest":
                if role == "chest" and face == "front":
                    white = (((tx - 0.5) / 0.38) ** 2 + ((ty - 0.55) / 0.55) ** 2 + jitter < 1).astype(float)
                elif role in ("neck", "ruff") and face == "front":
                    white = (np.abs(tx - 0.5) + jitter < 0.3).astype(float)
                elif role == "jaw" and face == "bottom":
                    white[:] = 1
            elif name == "tail_tip":
                if role == "tail_tip":
                    white = (ty + jitter > 0.25).astype(float) if face in SIDES else np.ones((fh, fw))
            elif name == "brows":
                if role == "head" and face == "front":
                    for ex in (0.27, 0.73):
                        white = np.maximum(white, ((((tx - ex) / 0.12) ** 2 + ((ty - 0.24) / 0.07) ** 2) < 1).astype(float) * 0.85)
                elif role == "brow" and face == "front":
                    white = ((np.abs(tx - 0.27) < 0.12) | (np.abs(tx - 0.73) < 0.12)).astype(float) * 0.85
            img[y0:y1, x0:x1, 1] = np.clip(white, 0, 1)
            img[y0:y1, x0:x1, 3] = 1
        return img

    # ================================================================ details

    def details(self, age):
        img = self.blank()
        rng = np.random.default_rng(31 + int(age * 10))
        for role, face, x0, y0, x1, y1 in self.faces():
            fw, fh = x1 - x0, y1 - y0
            tile = np.zeros((fh, fw, 4))
            if role == "head" and face == "front":
                self.eyes(tile, age)
            elif role == "snout":
                self.snout(tile, face)
            elif role == "jaw" and face in ("front", "west", "east"):
                put(tile, (slice(0, 2), slice(None)), INK, 0.8)
                if face == "front":
                    put(tile, (slice(2, 3), slice(1, -1)), BONE * 0.95, 1)
                    for fx in (1, fw - 3):
                        put(tile, (slice(0, 4), slice(fx, fx + 2)), BONE, 1)
            elif role in ("ear", "ear_tip") and face == "front":
                blob(tile, fw / 2, fh * (0.62 if role == "ear" else 0.4), fw * 0.32, fh * 0.42, INNER * (0.8 - 0.2 * age), 0.9)
            elif role == "paw":
                if face == "front":
                    for i in range(1, 4):
                        x = int(fw * i / 4)
                        put(tile, (slice(None), slice(max(0, x - 1), x)), INK, 0.55)
                    for i in range(4):
                        x = int(fw * (i + 0.5) / 4)
                        put(tile, (slice(fh - 2, fh), slice(max(0, x - 1), x + 1)), BONE * 0.8, 1)
                elif face == "bottom":
                    put(tile, (slice(None), slice(None)), NOSE * 1.5, 0.95)
                    blob(tile, fw / 2, fh * 0.6, fw * 0.3, fh * 0.25, INK, 1)
            elif role == "fang":
                put(tile, (slice(None), slice(None)), BONE, 1)
                put(tile, (slice(fh - 1, fh), slice(None)), BONE * 0.7, 1)
            elif role in ("scarf", "scarf_knot"):
                self.cloth(tile, face, rng)
            if age > 0.5 and role in ("snout", "head") and face in ("west", "east"):
                # Old scars: a pale nick across the muzzle and the brow.
                for i in range(min(fw, fh) - 2):
                    x, y = int(fw * 0.3) + i // 2, int(fh * 0.2) + i
                    if 0 <= x < fw and 0 <= y < fh:
                        tile[y, x, :3] = BONE * 0.9
                        tile[y, x, 3] = 0.55
            img[y0:y1, x0:x1] = tile
        return img

    def eyes(self, tile, age):
        """Round and bright on a puppy, a hard narrow leaf on an old hound (the eye rule in ART_STYLE)."""
        fh, fw = tile.shape[:2]
        ew = fw * (0.17 + 0.03 * age)
        eh = fh * (0.17 - 0.08 * age)
        for side, ex in ((-1, 0.27), (1, 0.73)):
            cx, cy = fw * ex, fh * (0.44 + 0.03 * age)
            yy, xx = np.mgrid[0:fh, 0:fw]
            # A dark socket round the eye.
            blob(tile, cx, cy, ew * 0.78, eh * 0.95 + 1.2, INK, 0.45)
            dx = (xx + 0.5 - cx) / (ew / 2)
            dy = (yy + 0.5 - cy) / (eh / 2)
            if age < 0.35:
                inside = dx ** 2 + dy ** 2 <= 1.0
            else:
                tilt = side * 0.25 * age
                dyt = dy + tilt * dx * (ew / max(eh, 0.5))
                inside = (np.abs(dyt) <= np.clip(1 - dx ** 2, 0, 1) ** (0.9 + age)) & (np.abs(dx) <= 1)
            # The iris: a grey ramp the game tints with the eye colour (alpha 254 marks it).
            gray = np.clip(0.95 - 0.35 * (dy + 1) / 2, 0, 1)
            tile[inside, 0] = gray[inside]
            tile[inside, 1] = gray[inside]
            tile[inside, 2] = gray[inside]
            tile[inside, 3] = 254 / 255
            pupil = inside & ((dx * 1.9) ** 2 + (dy * 1.5) ** 2 <= 1.0)
            tile[pupil, :3] = INK
            tile[pupil, 3] = 1
            rim = inside & ~((dx * 1.15) ** 2 + (dy * 1.15) ** 2 <= 1.0)
            tile[rim, :3] = INK * 1.5
            tile[rim, 3] = 1
            top = inside & ~np.roll(inside, 1, axis=0)
            lash = np.roll(top, -1, axis=0) | top
            tile[lash, :3] = INK
            tile[lash, 3] = 1
            hx, hy = int(cx - side * ew * 0.15), int(cy - eh * 0.2)
            if 0 <= hy < fh and 0 <= hx < fw:
                tile[hy, hx, :3] = BONE
                tile[hy, hx, 3] = 1

    def snout(self, tile, face):
        fh, fw = tile.shape[:2]
        if face == "front":
            nw, nh = fw * 0.62, fh * 0.42
            cx, cy = fw / 2, nh / 2 + 1
            blob(tile, cx, cy, nw / 2, nh / 2, NOSE, 1, square=0.6)
            blob(tile, cx - nw * 0.15, cy - nh * 0.2, nw * 0.12, nh * 0.15, NOSE * 0.55 + BONE * 0.45, 1)
            for sx in (-1, 1):
                blob(tile, cx + sx * nw * 0.22, cy + nh * 0.15, max(1, nw * 0.08), max(1, nh * 0.12), INK, 1)
            put(tile, (slice(int(cy + nh / 2), fh), slice(int(cx) - 1, int(cx) + 1)), INK, 0.6)
            put(tile, (slice(fh - 2, fh), slice(None)), INK, 0.7)
        elif face == "top":
            put(tile, (slice(0, max(2, int(fh * 0.18))), slice(int(fw * 0.22), int(fw * 0.78))), NOSE, 1)
        elif face in ("west", "east"):
            put(tile, (slice(fh - 2, fh), slice(None)), INK, 0.65)
            for i in range(3):
                blob(tile, fw * (0.25 + 0.2 * i), fh * 0.55, 0.7, 0.7, INK, 0.5)

    def cloth(self, tile, face, rng):
        fh, fw = tile.shape[:2]
        yy, xx = np.mgrid[0:fh, 0:fw]
        fold = np.sin((xx + yy * 0.6) / 3.2 + rng.random() * 6)
        col = BLOOD[None, None, :] * (0.85 + 0.25 * fold[..., None])
        col *= 1 + rng.normal(0, 0.05, (fh, fw, 1))
        tile[..., :3] = np.clip(col, 0, 1)
        tile[..., 3] = 1
        tile[-1, ::2, :3] *= 0.4
        tile[0, :, :3] *= 0.6
        if face in SIDES and fh > 3:
            tile[1, ::3, :3] = BLOOD * 0.6 + BONE * 0.4


def put(tile, idx, colour, alpha):
    tile[idx + (slice(0, 3),)] = colour
    tile[idx + (3,)] = alpha


def blob(tile, cx, cy, rx, ry, colour, alpha, square=0.0):
    h, w = tile.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    dx = (xx + 0.5 - cx) / max(0.5, rx)
    dy = (yy + 0.5 - cy) / max(0.5, ry)
    p = 2 + square * 6
    inside = np.abs(dx) ** p + np.abs(dy) ** p <= 1
    tile[inside, :3] = colour
    tile[inside, 3] = np.maximum(tile[inside, 3], alpha) if alpha < 1 else 1

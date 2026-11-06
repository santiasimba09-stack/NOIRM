"""Tiny pixel-art toolkit shared by NOIRM's art generators (standard library only).

- Canvas: an RGBA pixel grid that can be saved to (and read from) PNG.
- tone / mix / hx: colour helpers. tone(c, k) is the one shading rule used everywhere:
  k > 0 lightens, k < 0 darkens; one step is roughly one shading tone.
- gray(k) / tint(c, g): grayscale "tint layers". A part drawn in gray is coloured at runtime by
  NinjaLook's colours. Gray 128 is the base colour; every 40 gray levels is one tone step.
  The Java side (client/NinjaSkins) uses exactly the same formula.
- faces(): the six faces of a box in Minecraft's texture layout (u, v, w, h, d).
"""
import os
import struct
import zlib


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [[tuple(fill)] * w for _ in range(h)]

    def set(self, x, y, c):
        if c is None or not (0 <= x < self.w and 0 <= y < self.h):
            return
        if len(c) == 3:
            c = (c[0], c[1], c[2], 255)
        self.px[y][x] = tuple(int(v) for v in c)

    def get(self, x, y):
        return self.px[y][x]

    def blend(self, x, y, c):
        """Alpha-blend c (rgba) over the pixel."""
        if not (0 <= x < self.w and 0 <= y < self.h) or c[3] == 0:
            return
        if c[3] == 255:
            self.px[y][x] = c
            return
        d = self.px[y][x]
        a = c[3] / 255.0
        out_a = a + d[3] / 255.0 * (1 - a)
        if out_a <= 0:
            return
        rgb = tuple(int((c[i] * a + d[i] * (d[3] / 255.0) * (1 - a)) / out_a) for i in range(3))
        self.px[y][x] = rgb + (int(out_a * 255),)

    def fill_rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def blit(self, src, sx, sy, sw, sh, dx, dy, scale=1, flip=False, alpha=True):
        """Copy a src rect onto self (alpha-blended), upscaled by an integer factor."""
        for y in range(sh):
            for x in range(sw):
                c = src.get(sx + (sw - 1 - x if flip else x), sy + y)
                if c[3] == 0:
                    continue
                for yy in range(scale):
                    for xx in range(scale):
                        if alpha:
                            self.blend(dx + x * scale + xx, dy + y * scale + yy, c)
                        else:
                            self.set(dx + x * scale + xx, dy + y * scale + yy, c)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for c in row:
                raw.extend(c)

        def chunk(tag, data):
            return (struct.pack(">I", len(data)) + tag + data
                    + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(png)

    @staticmethod
    def load(path):
        """Reads an 8-bit RGBA (or RGB) non-interlaced PNG."""
        data = open(path, "rb").read()
        i, idat, w, h, ctype = 8, b"", 0, 0, 6
        while i < len(data):
            n = struct.unpack(">I", data[i:i + 4])[0]
            tag, body = data[i + 4:i + 8], data[i + 8:i + 8 + n]
            i += 12 + n
            if tag == b"IHDR":
                w, h, _, ctype = struct.unpack(">IIBB", body[:10])
            elif tag == b"IDAT":
                idat += body
        bpp = 4 if ctype == 6 else 3
        raw = zlib.decompress(idat)
        stride = w * bpp
        cv = Canvas(w, h)
        prev = bytearray(stride)
        pos = 0
        for y in range(h):
            f = raw[pos]
            line = bytearray(raw[pos + 1:pos + 1 + stride])
            pos += 1 + stride
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                if f == 1:
                    line[x] = (line[x] + a) & 255
                elif f == 2:
                    line[x] = (line[x] + b) & 255
                elif f == 3:
                    line[x] = (line[x] + (a + b) // 2) & 255
                elif f == 4:
                    p = a + b - c
                    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                    pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                    line[x] = (line[x] + pr) & 255
            for x in range(w):
                px = line[x * bpp:x * bpp + bpp]
                cv.px[y][x] = (px[0], px[1], px[2], px[3] if bpp == 4 else 255)
            prev = line
        return cv


# ---------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------

def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def tone(c, k):
    """k > 0 lightens, k < 0 darkens. One step is roughly one shading tone."""
    if k >= 0:
        return tuple(min(255, int(v * (1 + 0.13 * k) + 14 * k)) for v in c[:3])
    return tuple(max(0, int(v * (1 + 0.2 * k))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def gray(k, alpha=255):
    """A tint-layer pixel for shading step k (see the module doc)."""
    g = max(0, min(255, int(round(128 + 40 * k))))
    return (g, g, g, alpha)


def tint(c, px):
    """Colours a tint-layer pixel: the same rule as NinjaSkins.tint in Java."""
    g, a = px[0], px[3]
    k = (g - 128) / 40.0
    return tone(c, k) + (a,)


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# Light per face: Minecraft also shades entity faces, so keep this gentle.
FACE_LIGHT = {"top": 0.6, "front": 0.0, "right": -0.25, "left": -0.25, "back": -0.15, "bottom": -0.6}


# ---------------------------------------------------------------------------
# UV layouts
# ---------------------------------------------------------------------------

# Player skin, 64x64 (wide arms). part -> (u, v, w, h, d)
SKIN_PARTS = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4),
    "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4),
    "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4),
}

# Armor layers, 64x32 (both arms share one rect, both legs share one rect; the left side is
# mirrored by the model). Layer 1: head, body, arms, legs (= boots). Layer 2: body (waist), legs.
ARMOR_PARTS = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4), "leg": (0, 16, 4, 12, 4),
}


def faces(box):
    u, v, w, h, d = box
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def paint(cv, box, fn, light=True):
    """fn(face, x, y, W, H) -> rgb / rgba colour, or None for transparent."""
    for face, (fx, fy, fw, fh) in faces(box).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is None:
                    continue
                a = c[3] if len(c) == 4 else 255
                if light:
                    c = tone(c, FACE_LIGHT[face] * 0.5)
                cv.set(fx + x, fy + y, tuple(c[:3]) + (a,))


def paint_k(cv, box, fn, light=True):
    """Grayscale tint layer: fn(face, x, y, W, H) -> k, (k, alpha), or None."""
    for face, (fx, fy, fw, fh) in faces(box).items():
        for y in range(fh):
            for x in range(fw):
                k = fn(face, x, y, fw, fh)
                if k is None:
                    continue
                a = 255
                if isinstance(k, tuple):
                    k, a = k
                if light:
                    k += FACE_LIGHT[face] * 0.5
                cv.set(fx + x, fy + y, gray(k, a))


def mirror_side(face, x, w):
    """Side maps are authored for the 'right' face (x=0 back edge, x=w-1 front edge)."""
    return w - 1 - x if face == "left" else x

#!/usr/bin/env python3
"""Procedural ninja NPC skins for NOIRM.

Writes standard 64x64 Minecraft player skins (modern layout, wide 4-pixel arms,
second layer used for hair, collars, vests, armour, headbands and masks) to

    src/main/resources/assets/noirm/textures/entity/ninja/<clan>/<n>.png

(n = 0 .. count-1) plus `catalog.json` (skin count per clan) and a preview contact sheet at
`docs/screenshots/skins-catalog.png`.

All art is original and drawn by the code below (no external skins are used).
Standard library only: PNGs are written with zlib + struct.

Run from the repo root:  python3 tools/skingen/gen_skins.py
"""
import json
import os
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/ninja")
PREVIEW = os.path.join(ROOT, "docs/screenshots/skins-catalog.png")

# ---------------------------------------------------------------------------
# Canvas + PNG
# ---------------------------------------------------------------------------


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if c is None or not (0 <= x < self.w and 0 <= y < self.h):
            return
        if len(c) == 3:
            c = (c[0], c[1], c[2], 255)
        self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x]

    def blit(self, src, sx, sy, sw, sh, dx, dy, scale=1, flip=False):
        """Copy src rect onto self (alpha-tested), upscaled by an integer factor."""
        for y in range(sh):
            for x in range(sw):
                c = src.get(sx + (sw - 1 - x if flip else x), sy + y)
                if c[3] == 0:
                    continue
                for yy in range(scale):
                    row = self.px[dy + y * scale + yy]
                    for xx in range(scale):
                        row[dx + x * scale + xx] = c

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


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# Light per face: Minecraft also shades entity faces, so keep this gentle.
FACE_LIGHT = {"top": 0.6, "front": 0.0, "right": -0.25, "left": -0.25, "back": -0.15, "bottom": -0.6}

# ---------------------------------------------------------------------------
# UV layout (64x64, wide arms). part -> (u, v, w, h, d)
# ---------------------------------------------------------------------------

PARTS = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4),
    "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4),
    "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4),
}


def faces(part):
    u, v, w, h, d = PARTS[part]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def paint(cv, part, fn, light=True):
    """fn(face, x, y, W, H) -> colour (rgb) or None for transparent."""
    for face, (fx, fy, fw, fh) in faces(part).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is None:
                    continue
                if light:
                    c = tone(c, FACE_LIGHT[face] * 0.5)
                cv.set(fx + x, fy + y, c)


def mirror_side(face, x, w):
    """Side maps are authored for the 'right' face (x=0 back edge, x=w-1 front edge)."""
    return w - 1 - x if face == "left" else x


# ---------------------------------------------------------------------------
# Hair styles: ASCII maps.  . = none, H = highlight, h = hair, d = shade, x = deep shade
# side maps are for the right face: column 0 = back of head, column 7 = face side.
# ---------------------------------------------------------------------------

HAIR = {
    "spiky": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h......h", "........", "........", "........", "........", "........"],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 7 + ["dhdhhdhd"],
        "front": ["HhHHhHHh", "hhdhhdhh", "d.hd.hd.", "d......d", "........", "........", "........", "........"],
        "side": ["HHhHHhHh", "hhhhhhhd", "hhhdhhd.", "hdhhd.d.", "dhd.d...", "d.d.....", "........", "........"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hdhhdhhd", "hhdhhhdh", "dhhdhhhd", "hdhhdhdh", "d.dh.dd.", ".d..d..d"],
        "jacket_back": [],
    },
    "messy": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hh.....h", "........", "........", "........", "........", "........"],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhh...", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["hHhHHhHh", "hhhdhhhd", "hdhh.dhd", ".d.h..d.", "...d....", "........", "........", "........"],
        "side": ["HhHHhHHh", "hhdhhhhh", "hhhhdhhd", "dhhhhd.d", "hdhd.d..", ".d.d....", "........", "........"],
        "back": ["hHhHHhHh", "hhhhhhhh", "hhdhhdhh", "dhhhhhhd", "hhdhdhhh", "hdhhhdhd", "dhd.dhd.", "d...d..d"],
        "jacket_back": [],
    },
    "short": {
        "base_front": ["hhhhhhhh", "hhhhhh.h", "........", "........", "........", "........", "........", "........"],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhhh...", "hhh.....", "hh......", "........", "........"],
        "base_back": ["hhhhhhhh"] * 6 + ["........", "........"],
        "front": ["HhHHhHHh", "hhhhd..h", "d......d", "........", "........", "........", "........", "........"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "dhhhd...", "hd......", "........", "........", "........"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhhhhh", "hdhhhhdh", "dhhdhhhd", "dhdddhdd", "........", "........"],
        "jacket_back": [],
    },
    "ponytail": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "........", "........", "........", "........", "........", "........"],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhhh...", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHHhHHh", "hhd.hhdh", "d......d", "d.......", "........", "........", "........", "........"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "hhhhd...", "dhhd....", "dd......", "........", "........"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhdhhdhh", "hhhxxhhh", "dh.hh.hd", "...hd...", "...dh...", "...hd..."],
        "jacket_back": ["...hd...", "...dh...", "...hd...", "....d..."],
    },
    "long": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h......h", "h......h", "........", "........", "........", "........"],
        "base_side": ["hhhhhhhh"] * 8,
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHhhHhH", "hhd..dhh", "hd....dh", "h......h", "h......h", "d......d", "d......d", "x......x"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhdhh", "hdhhhhhd", "hhhhdhhh", "dhhhhhdh", "hhdhhhhd", "dhhdhdhd"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhdhhhdh", "hhhhdhhh", "hdhhhhhd", "hhhdhhhh", "dhhhhdhh", "hhdhhhhd"],
        "jacket_back": ["hhhhhhhh", "hdhhhhdh", "dhhdhhhd", ".dhhdhd.", "..d..d.."],
    },
    "swept": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", ".hhhhh..", "........", "........", "........", "........", "........"],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 7 + ["dhhdhdhh"],
        "front": ["HHhHHhHh", "hhhhhhhd", ".dhhhhd.", "...dhhh.", "....dhh.", ".....d..", "........", "........"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhdh", "hhhhd..d", "hdhd....", "dd......", "........", "........"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhdhhh", "hdhhhhdh", "hhdhhdhh", "dhhhhhhd", "hdhdhdh.", ".d...d.."],
        "jacket_back": [],
    },
}

# Kimono-style crossed collar on the torso front: i = undershirt, L = lapel, f = fold line
WRAP = [
    ".LiiiiL.",
    "..LiiL..",
    "...iL...",
    "....L...",
    ".....f..",
    "......f.",
]

def bandage_k(x, y):
    """Wrapped cloth: alternating strips with a diagonal seam where they overlap."""
    if (x + 2 * y) % 5 == 0:
        return -0.7
    return 0.25 if y % 2 == 0 else -0.15


HAIR_TONE = {"H": 1.0, "h": 0.0, "d": -1.0, "x": -2.0}

# ---------------------------------------------------------------------------
# Emblems (on the back of the torso)
# ---------------------------------------------------------------------------

FAN = [  # red-and-white fan crest, handle below
    "..rRRr..",
    ".rRRRRr.",
    ".RRRRRR.",
    ".dddddd.",
    ".WWWWWW.",
    "..WWWw..",
    "...Hw...",
    "...Hw...",
]
SPIRAL = [  # an original swirl: outer ring curling inward
    "..RRRR..",
    ".R....R.",
    "R..RR..R",
    "R.R..R.R",
    "R.R.RR.R",
    "R.R....R",
    ".R.RRRR.",
    "..R.....",
]


# ---------------------------------------------------------------------------
# The painter
# ---------------------------------------------------------------------------


class Ninja:
    def __init__(self, spec, seed):
        self.s = dict(DEFAULTS)
        self.s.update(spec)
        self.seed = seed
        self.cv = Canvas(64, 64)
        s = self.s
        self.skin = hx(s["skin"])
        self.hair = hx(s["hair"])
        self.shirt = hx(s["shirt"])
        self.pants = hx(s["pants"])
        self.sandal = hx(s["sandals"])

    # -- small helpers ------------------------------------------------------
    def cloth(self, c, x, y, extra=0.0, salt=0):
        """Cloth tone with a little woven noise."""
        n = noise(x, y, self.seed * 7 + salt)
        k = extra
        if n < 0.07:
            k -= 0.35
        elif n > 0.96:
            k += 0.25
        return tone(c, k)

    def hair_px(self, ch, x, y, salt):
        k = HAIR_TONE[ch]
        n = noise(x, y, self.seed * 13 + salt)
        if ch == "h" and n < 0.18:
            k -= 0.5
        return tone(self.hair, k)

    # -- head ---------------------------------------------------------------
    def head(self):
        s, style = self.s, HAIR[self.s["hair_style"]]

        def base(face, x, y, W, H):
            if face == "top":
                return self.hair_px("h", x, y, 1)
            if face == "bottom":
                return tone(self.skin, -1)
            if face == "front":
                ch = style["base_front"][y][x]
                if ch != ".":
                    return self.hair_px(ch, x, y, 2)
                return self.face(x, y)
            if face in ("right", "left"):
                mx = mirror_side(face, x, W)
                ch = style["base_side"][y][mx]
                if ch != ".":
                    return self.hair_px(ch, x, y, 3)
                if mx in (3, 4) and y in (3, 4, 5):  # ear
                    return tone(self.skin, -1.0 if (mx == 3 and y == 4) else -0.4)
                return tone(self.skin, -0.4 if y == 7 else 0)
            ch = style["base_back"][y][x]
            if ch != ".":
                return self.hair_px(ch, x, y, 4)
            return tone(self.skin, -0.5)

        paint(self.cv, "head", base)

        # --- outer layer: collar, mask, hair, headband (drawn in that order) ---
        hat = {}

        def put(face, x, y, c):
            hat[(face, x, y)] = c

        if s["collar"] == "high":
            col = self.collar_color()
            for x in range(8):
                put("back", x, 7, tone(col, -0.3 if x % 3 else 0.2))
                for f in ("right", "left"):
                    put(f, x, 7, tone(col, 0))
            for x in (0, 1, 6, 7):
                put("front", x, 7, tone(col, 0.4 if x in (0, 7) else 0))

        if s["mask"]:
            m = hx(s["mask"])
            for x in range(8):
                put("front", x, 6, tone(m, 0.6 if x in (3, 4) else (0 if 0 < x < 7 else -0.5)))
                put("front", x, 7, tone(m, -0.4 if x in (0, 7) else -0.1))
                for f in ("right", "left"):
                    mx = mirror_side(f, x, 8)
                    put(f, x, 6, tone(m, -0.2 if mx > 2 else -0.6))
                    put(f, x, 7, tone(m, -0.5))

        for y in range(8):
            for x in range(8):
                ch = style["front"][y][x]
                if ch != ".":
                    put("front", x, y, self.hair_px(ch, x, y, 5))
                ch = style["back"][y][x]
                if ch != ".":
                    put("back", x, y, self.hair_px(ch, x, y, 6))
                for f in ("right", "left"):
                    mx = mirror_side(f, x, 8)
                    ch = style["side"][y][mx]
                    if ch != ".":
                        put(f, x, y, self.hair_px(ch, x, y, 7 if f == "right" else 8))
        for y in range(8):
            for x in range(8):
                k = 1.0 if noise(x, y, self.seed + 9) > 0.8 else (-0.6 if noise(x, y, self.seed + 3) < 0.2 else 0.4)
                put("top", x, y, tone(self.hair, k))

        hb = s["headband"]
        if hb in ("forehead", "bandage"):
            self.headband(put, hb)

        def hatfn(face, x, y, W, H):
            return hat.get((face, x, y))

        paint(self.cv, "hat", hatfn)

    def hair_down(self):
        """Rows of hair hanging down the upper back; kept short so a back emblem stays visible."""
        rows = HAIR[self.s["hair_style"]]["jacket_back"]
        return rows[:2] if self.s["emblem"] else rows

    def collar_color(self):
        s = self.s
        return hx(s["collar_color"]) if s["collar_color"] else tone(self.shirt, -0.2)

    def headband(self, put, kind):
        s = self.s
        cloth = hx(s["band_cloth"])
        if kind == "bandage":
            cloth = hx("#d9d3c3")
        rows = (1, 2)
        for x in range(8):
            for y in rows:
                k = 0.3 if y == rows[0] else -0.4
                if kind == "bandage" and (x + y) % 3 == 0:
                    k -= 0.6
                put("back", x, y, tone(cloth, k - 0.2))
                put("right", x, y, tone(cloth, k))
                put("left", x, y, tone(cloth, k))
                put("front", x, y, tone(cloth, k))
        # knot and tails at the back
        put("back", 3, 2, tone(cloth, 0.5))
        put("back", 4, 2, tone(cloth, 0.5))
        put("back", 3, 3, tone(cloth, -0.3))
        put("back", 4, 4, tone(cloth, -0.6))
        put("back", 3, 4, tone(cloth, -0.4))
        put("back", 4, 5, tone(cloth, -0.8))
        if kind == "bandage":
            return
        metal = hx(s["band_plate"])
        for x in range(1, 7):
            put("front", x, 1, tone(metal, 1.2 if x in (1, 2) else 0.7))
            put("front", x, 2, tone(metal, 0))
            put("front", x, 3, tone(metal, -1.2))
        # engraving: a simple original mark (line with a notch)
        for x in (2, 3, 4, 5):
            put("front", x, 2, tone(metal, -1.6))
        put("front", 4, 1, tone(metal, -1.0))
        put("front", 1, 2, tone(metal, 0.6))
        put("front", 6, 2, tone(metal, -0.5))

    def face(self, x, y):
        s, sk = self.s, self.skin
        if y in (4, 5) and x in (1, 2, 5, 6):
            outer = x in (1, 6)
            if s["eyes"] == "pale":
                if outer:
                    return hx("#efeaf5") if y == 4 else hx("#d9d0e6")
                return hx("#e3d9f2") if y == 4 else hx("#cbbfe0")
            if outer:
                return hx("#f1efe9") if y == 4 else hx("#cfcac0")
            iris = hx(s["eyes"])
            return tone(iris, 0.2) if y == 4 else tone(iris, -1.2)
        if y == 3 and x in (1, 2, 5, 6):  # brows
            return tone(self.hair, -1.0 if x in (2, 5) else -0.4)
        if y == 5 and x == 4:  # nose shadow
            return tone(sk, -0.5)
        if y == 6 and x in (3, 4):  # mouth
            return mix(tone(sk, -0.8), hx("#8a3b3b"), 0.25)
        if s["cheek_bandage"] and y in (5, 6) and x in (5, 6) and not (y == 5 and x == 5):
            return hx("#e9e4d6") if (x + y) % 2 else hx("#d2ccbc")
        if s["face_paint"] and x in (0, 7) and y in (5, 6):
            return hx(s["face_paint"])
        k = 0.0
        if x in (0, 7):
            k -= 0.4
        if y == 7:
            k -= 0.3
        if y in (4, 5) and x in (3, 4) and not (y == 5 and x == 4):
            k += 0.15
        return tone(sk, k)

    # -- torso --------------------------------------------------------------
    def torso(self):
        s = self.s
        sash_top, pants_top = 8, 10
        robe = s["robe_skirt"]

        def base(face, x, y, W, H):
            if face == "top":
                if s["collar"] in ("round", "v", "wrap") and 2 <= x <= 5 and y in (1, 2):
                    return tone(self.skin, -0.5)
                return self.cloth(self.shirt, x, y, 0.3, 20)
            if face == "bottom":
                return tone(self.pants, -1)
            if y < sash_top:
                c = self.shirt_px(face, x, y, W)
            elif y < pants_top:
                sash = hx(s["sash"])
                k = 0.4 if y == sash_top else -0.3
                if face == "front" and s["sash_knot"] and x in (5, 6):
                    k += 0.6 if y == sash_top else 0.1
                c = tone(sash, k)
            else:
                if robe:
                    c = self.cloth(self.shirt, x, y, -0.3 if face == "front" and x in (3, 4) else 0, 21)
                else:
                    c = self.cloth(self.pants, x, y, -0.4 if (face == "front" and x in (3, 4) and y == 11) else 0, 22)
            if face in ("front", "back") and x in (0, W - 1):
                c = tone(c, -0.35)
            return c

        paint(self.cv, "body", base)
        if s["emblem"] and not (s["vest"] or s["armor"] or s["haori"]):
            self.emblem(self.cv, faces("body")["back"])

        jacket = {}
        if s["collar"] == "high":
            col = self.collar_color()
            for x in range(8):
                jacket[("front", x, 0)] = tone(col, 0.4 if x in (0, 7) else -0.2)
                jacket[("back", x, 0)] = tone(col, -0.2)
            for x in range(4):
                jacket[("right", x, 0)] = tone(col, 0)
                jacket[("left", x, 0)] = tone(col, 0)
            for y in range(4):
                for x in range(8):
                    if not (2 <= x <= 5 and 1 <= y <= 2):
                        jacket[("top", x, y)] = tone(col, 0.5)
        if s["vest"]:
            self.vest(jacket)
        if s["armor"]:
            self.armor(jacket)
        if s["haori"]:
            self.haori(jacket)
        if robe:
            r = self.shirt
            for x in range(8):
                for y in (10, 11):
                    if x in (3, 4) and y == 11:
                        continue
                    jacket[("front", x, y)] = self.cloth(r, x, y, -0.2 if y == 11 else 0.1, 23)
                    jacket[("back", x, y)] = self.cloth(r, x, y, -0.4, 24)
            for x in range(4):
                for y in (10, 11):
                    jacket[("right", x, y)] = self.cloth(r, x, y, -0.3, 25)
                    jacket[("left", x, y)] = self.cloth(r, x, y, -0.3, 26)
        style = HAIR[s["hair_style"]]
        for y, row in enumerate(self.hair_down()):
            for x, ch in enumerate(row):
                if ch != ".":
                    jacket[("back", x, y)] = self.hair_px(ch, x, y, 30)

        paint(self.cv, "jacket", lambda f, x, y, W, H: jacket.get((f, x, y)))
        if s["emblem"] and (s["vest"] or s["armor"] or s["haori"]):
            self.emblem(self.cv, faces("jacket")["back"], skip_hair=True)

    def shirt_px(self, face, x, y, W):
        s, sh = self.s, self.shirt
        k = 0.0
        if face == "front":
            col = s["collar"]
            if col in ("round", "v") and ((y == 0 and 2 <= x <= 5) or (y == 1 and x in (3, 4)) or (col == "v" and y == 2 and x in (3, 4))):
                return tone(self.skin, -0.5 if y == 0 else -0.2)
            if col == "wrap":
                # kimono-style crossing: the right panel overlaps the left
                inner = hx(s["undershirt"]) if s["undershirt"] else tone(sh, -1.5)
                lapel = hx(s["collar_color"]) if s["collar_color"] else tone(sh, -1.1)
                ch = WRAP[y][x] if y < len(WRAP) else "."
                if ch == "i":
                    return tone(inner, -0.4 if y == 0 else 0)
                if ch == "L":
                    return tone(lapel, 0.2 if x < 4 else -0.2)
                if ch == "f":
                    return tone(sh, -0.7)  # fold where the robe overlaps
            if s["chest_strap"] and (x == y - 1 or x == y - 2) and 1 <= y <= 8:
                return tone(hx(s["chest_strap"]), 0 if x == y - 1 else -0.8)
            if y in (6, 7) and x in (2, 5):  # fold creases
                k -= 0.4
            if y == 0:
                k += 0.3
        elif face == "back":
            if y in (0, 1):
                k += 0.2
            if x in (3, 4) and y >= 6:
                k -= 0.3
        else:
            k -= 0.2
        return self.cloth(sh, x, y, k, 27)

    def emblem(self, cv, rect, skip_hair=False):
        fx, fy, fw, fh = rect
        pat = FAN if self.s["emblem"] == "fan" else SPIRAL
        top = 1 if self.s["emblem"] == "fan" else 2
        if self.s["emblem"] == "fan" and self.s["hair_style"] == "long":
            top = 2
        red = hx(self.s["emblem_color"])
        white = hx("#ebe7df")
        cols = {"R": tone(red, 0), "r": tone(red, 0.7), "d": tone(red, -1.0),
                "W": white, "w": tone(white, -0.8), "H": tone(white, -0.3)}
        style = HAIR[self.s["hair_style"]]
        for y, row in enumerate(pat):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                if skip_hair and y + top < len(self.hair_down()) and self.hair_down()[y + top][x] != ".":
                    continue
                c = cols[ch]
                if self.s["emblem"] == "spiral":
                    c = tone(red, 0.3 if y < 2 else (-0.4 if y > 5 else 0))
                cv.set(fx + x, fy + y + top, tone(c, FACE_LIGHT["back"] * 0.5))

    def vest(self, j):
        g = hx(self.s["vest"])
        for y in range(9):
            for x in range(8):
                if y == 0 and x in (3, 4):
                    continue
                k = 0.0
                if y == 0:
                    k = 0.9  # rolled collar
                elif y == 1 and x in (2, 5):
                    k = 0.5
                if x == 3 and y > 0:
                    k = -1.0  # zip
                elif x == 4 and y > 0:
                    k = 0.3
                if 3 <= y <= 5 and x in (1, 2, 5, 6):
                    k = 0.6 if y == 3 else -0.6  # chest pouches
                    if y == 5:
                        k = -0.9
                if y == 8:
                    k = -0.6
                if x in (0, 7) and k == 0:
                    k = -0.4
                j[("front", x, y)] = self.cloth(g, x, y, k, 40)
                kb = 0.6 if y == 0 else (-0.5 if x in (3, 4) and y < 6 else 0)
                if 5 <= y <= 7 and 1 <= x <= 6:
                    kb = 0.5 if y == 5 else -0.4  # back pouch panel
                if y == 8:
                    kb = -0.7
                j[("back", x, y)] = self.cloth(g, x, y, kb - 0.1, 41)
            for x in range(4):
                j[("right", x, y)] = self.cloth(g, x, y, 0.6 if y == 0 else -0.2, 42)
                j[("left", x, y)] = self.cloth(g, x, y, 0.6 if y == 0 else -0.2, 43)
        for y in range(4):
            for x in range(8):
                if not (2 <= x <= 5 and 1 <= y <= 2):
                    j[("top", x, y)] = tone(g, 0.8)
        self.shoulder_pads = (g, 2)

    def armor(self, j):
        a = hx(self.s["armor"])
        trim = hx(self.s["armor_trim"])
        for x in range(8):
            for y in range(1, 8):
                band = (y - 1) % 2
                k = 0.7 if band == 0 else -0.2
                if x in (0, 7):
                    k -= 0.6
                if y == 7:
                    k = -1.2
                j[("front", x, y)] = tone(a, k)
                j[("back", x, y)] = tone(a, k - 0.3)
            j[("front", x, 1)] = tone(trim, 0.4 if 1 < x < 6 else -0.3)
            # tassets (skirt plates) below the belt, split at the middle
            for y in (10, 11):
                if x in (3, 4):
                    continue
                kk = 0.5 if y == 10 else -0.5
                j[("front", x, y)] = tone(a, kk - (0.5 if x in (0, 7) else 0))
                j[("back", x, y)] = tone(a, kk - 0.4)
        for x in range(4):
            for y in range(1, 8):
                j[("right", x, y)] = tone(a, (0.3 if (y - 1) % 2 == 0 else -0.5) - 0.3)
                j[("left", x, y)] = tone(a, (0.3 if (y - 1) % 2 == 0 else -0.5) - 0.3)
            for y in (10, 11):
                j[("right", x, y)] = tone(a, -0.3 if y == 10 else -0.9)
                j[("left", x, y)] = tone(a, -0.3 if y == 10 else -0.9)
        self.shoulder_pads = (a, 4)

    def haori(self, j):
        c = hx(self.s["haori"])
        trim = hx(self.s["haori_trim"]) if self.s["haori_trim"] else tone(c, -1.2)
        for y in range(12):
            for x in range(8):
                j[("back", x, y)] = self.cloth(c, x, y, (0.3 if y < 2 else 0) - (0.4 if y > 9 else 0), 51)
                if x in (0, 1, 6, 7):
                    k = 0.0
                    if x in (1, 6):
                        k = 0.5 if x == 1 else -0.6
                        if y <= 9:
                            j[("front", x, y)] = tone(trim, 0.3 if x == 1 else -0.2)
                            continue
                    j[("front", x, y)] = self.cloth(c, x, y, k, 50)
            for x in range(4):
                j[("right", x, y)] = self.cloth(c, x, y, -0.2, 52)
                j[("left", x, y)] = self.cloth(c, x, y, -0.2, 53)
        for y in range(4):
            for x in range(8):
                if not (2 <= x <= 5 and 1 <= y <= 2):
                    j[("top", x, y)] = tone(c, 0.5)
        self.sleeve_override = c

    # -- arms ---------------------------------------------------------------
    def arms(self):
        s = self.s
        sleeve_end = {"long": 9, "short": 3, "none": 0}[s["sleeves"]]
        for part, layer, outer in (("rarm", "rsleeve", "right"), ("larm", "lsleeve", "left")):
            inner = "left" if outer == "right" else "right"

            def base(face, x, y, W, H, outer=outer, inner=inner):
                if face == "top":
                    return self.cloth(self.shirt, x, y, 0.4, 60) if sleeve_end else tone(self.skin, 0.3)
                if face == "bottom":
                    return tone(self.skin, -0.6)
                side = -0.3 if face == inner else 0
                if y < sleeve_end:
                    k = side
                    if y == sleeve_end - 1:
                        k -= 0.6  # cuff
                    if face == "front" and x == 0:
                        k -= 0.2
                    return self.cloth(self.shirt, x, y, k, 61)
                if y >= 9:  # hand
                    if s["gloves"] and y == 9:
                        return tone(hx(s["gloves"]), side + (0.3 if face == "front" else 0))
                    if s["gloves"] and y == 10 and face in ("back", outer):
                        return tone(hx(s["gloves"]), side - 0.3)
                    k = side + (0.15 if face == "front" else 0)
                    if face == "front" and y == 11 and x in (1, 3):
                        k -= 0.5  # knuckle gaps
                    return tone(self.skin, k)
                return tone(self.skin, side + (-0.2 if y == sleeve_end else 0))

            paint(self.cv, part, base)

            over = {}
            pads = getattr(self, "shoulder_pads", None)
            sleeve_col = getattr(self, "sleeve_override", None)
            if sleeve_col is not None:
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        for y in range(7):
                            k = (-0.8 if y == 6 else 0) + (-0.3 if f == inner else 0)
                            over[(f, x, y)] = self.cloth(sleeve_col, x, y, k, 62)
                for x in range(4):
                    for y in range(4):
                        over[("top", x, y)] = tone(sleeve_col, 0.5)
            if s["arm_warmers"]:
                w = hx(s["arm_warmers"])
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        for y in range(5, 10):
                            k = 0.4 if y == 5 else (-0.6 if y == 9 else 0)
                            over[(f, x, y)] = self.cloth(w, x, y, k + (-0.3 if f == inner else 0), 63)
            if s["arm_wraps"]:
                w = hx(s["arm_wraps"])
                top = max(sleeve_end, 4)
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        for y in range(top, 10):
                            k = bandage_k(x, y)
                            over[(f, x, y)] = tone(w, k + (-0.3 if f == inner else 0))
            if s["wide_sleeves"]:
                c = self.shirt
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        for y in range(6, 9):
                            k = 0.3 if y == 6 else (-0.4 if y == 7 else -1.0)
                            over[(f, x, y)] = self.cloth(c, x, y, k + (-0.3 if f == inner else 0), 64)
            if pads:
                c, rows = pads
                for f in ("front", "back", outer):
                    for x in range(4):
                        for y in range(rows):
                            k = (0.6 if y % 2 == 0 else -0.4) if rows > 2 else (0.4 if y == 0 else -0.5)
                            if y == rows - 1:
                                k = -1.0
                            over[(f, x, y)] = tone(c, k + (-0.2 if f == "back" else 0))
                for x in range(4):
                    for y in range(4):
                        over[("top", x, y)] = tone(c, 0.7)
            if s["arm_band"] and outer == "left":  # forehead protector worn on the upper arm
                cloth, metal = hx(s["band_cloth"]), hx(s["band_plate"])
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        over[(f, x, 2)] = tone(cloth, 0.2)
                        over[(f, x, 3)] = tone(cloth, -0.5)
                for x in range(4):
                    over[("left", x, 2)] = tone(metal, 1.0 if x < 2 else 0.4)
                    over[("left", x, 3)] = tone(metal, -1.0 if x in (1, 2) else -0.4)
            if s["bracers"]:
                b = hx(s["bracers"])
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        for y in range(6, 9):
                            k = 0.6 if y == 6 else (-0.2 if y == 7 else -0.9)
                            over[(f, x, y)] = tone(b, k + (-0.3 if f == inner else 0))
            paint(self.cv, layer, lambda f, x, y, W, H, o=over: o.get((f, x, y)))

    # -- legs ---------------------------------------------------------------
    def legs(self):
        s = self.s
        pants_end = {"long": 10, "shorts": 5, "capri": 8}[s["pants_len"]]
        for part, layer, outer in (("rleg", "rpants", "right"), ("lleg", "lpants", "left")):
            inner = "left" if outer == "right" else "right"

            def base(face, x, y, W, H, outer=outer, inner=inner):
                if face == "top":
                    return self.cloth(self.pants, x, y, 0, 70)
                if face == "bottom":
                    return tone(self.sandal, -1.0)
                side = -0.4 if face == inner else 0
                if y >= 10:  # open-toe sandals
                    if face == "front":
                        if y == 10:
                            return tone(self.sandal, 0.3)
                        return tone(self.skin, -0.2) if x in (1, 2) else tone(self.sandal, -0.4)
                    if face == "back":
                        return tone(self.skin, -0.4) if y == 10 else tone(self.sandal, -0.6)
                    return tone(self.sandal, side) if y == 11 or x in (1, 2) else tone(self.skin, side - 0.3)
                if y < pants_end:
                    k = side
                    if y == pants_end - 1 and s["pants_len"] != "long":
                        k -= 0.6
                    if face == "front" and x == (3 if outer == "right" else 0) and y < 4:
                        k -= 0.3
                    return self.cloth(self.pants, x, y, k, 71)
                return tone(self.skin, side - (0.2 if y == pants_end else 0))

            paint(self.cv, part, base)

            over = {}
            if s["leg_wraps"]:
                w = hx(s["leg_wraps"])
                top = 6 if s["pants_len"] != "shorts" else 6
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        for y in range(top, 10):
                            k = bandage_k(x + (2 if f in ("back", "left") else 0), y)
                            over[(f, x, y)] = tone(w, k + (-0.3 if f == inner else 0))
            if s["armor"]:  # thigh plates continue the tassets
                a = hx(s["armor"])
                for f in ("front", "back", outer):
                    for x in range(4):
                        for y in range(0, 3):
                            k = 0.4 if y % 2 == 0 else -0.4
                            if y == 2:
                                k = -1.1
                            over[(f, x, y)] = tone(a, k - (0.3 if f != "front" else 0))
                # shin guards
                for f in ("front", outer):
                    for x in range(4):
                        for y in range(6, 9):
                            if f == "front" or x in (1, 2):
                                if y == 6:
                                    c = tone(hx(s["armor_trim"]) if s["armor_trim"] else a, -0.2)
                                else:
                                    c = tone(a, -0.1 if y == 7 else -1.0)
                                over[(f, x, y)] = c
            if s["robe_skirt"]:
                r = self.shirt
                for f in ("front", "back", "right", "left"):
                    for x in range(4):
                        for y in range(0, 4):
                            if f == inner and y > 0:
                                continue
                            k = -0.2 - (0.8 if y == 3 else 0) - (0.3 if f == inner else 0)
                            over[(f, x, y)] = self.cloth(r, x, y, k, 72)
            if s["thigh_pouch"] and outer == "right":
                p = hx(s["thigh_pouch"])
                for x in range(4):
                    over[("right", x, 2)] = tone(p, 0.3)
                    over[("right", x, 3)] = tone(p, -0.5)
                    if x in (1, 2):
                        over[("right", x, 4)] = tone(p, -0.2)
                over[("front", 0, 2)] = tone(p, 0)
                over[("front", 0, 3)] = tone(p, -0.6)
            paint(self.cv, layer, lambda f, x, y, W, H, o=over: o.get((f, x, y)))

    def render(self):
        self.shoulder_pads = None
        self.sleeve_override = None
        self.head()
        self.torso()
        self.arms()
        self.legs()
        return self.cv


DEFAULTS = {
    "skin": "#e0ac84", "hair": "#2a2522", "hair_style": "short", "eyes": "#3b2a20",
    "shirt": "#2c3550", "collar": "round", "collar_color": None, "undershirt": None, "sleeves": "long",
    "pants": "#2a2e3a", "pants_len": "long", "sash": "#3a3a3a", "sash_knot": False,
    "sandals": "#2b2f3d", "leg_wraps": None, "arm_wraps": None, "arm_warmers": None,
    "gloves": None, "bracers": None, "wide_sleeves": False, "robe_skirt": False,
    "vest": None, "armor": None, "armor_trim": None, "haori": None, "haori_trim": None,
    "headband": None, "band_cloth": "#1f2433", "band_plate": "#a7adb5", "arm_band": False,
    "mask": None, "emblem": None, "emblem_color": "#c0282d",
    "cheek_bandage": False, "face_paint": None, "chest_strap": None, "thigh_pouch": None,
}

SKIN_TONES = {
    "pale": "#f1cdb0", "light": "#e8bc98", "fair": "#e0ac84", "medium": "#c98e66",
    "tan": "#b07650", "brown": "#8a5a3c", "dark": "#6a4330",
}

# ---------------------------------------------------------------------------
# The catalog. Each clan's skins are deliberate variations on a shared look.
# ---------------------------------------------------------------------------

CLANS = {
    "uchiha": [
        dict(skin=SKIN_TONES["light"], hair="#1b1c22", hair_style="spiky", eyes="#2a1f24",
             shirt="#1f2740", collar="high", sleeves="short", pants="#e4e1d8", pants_len="shorts",
             sash="#4a3f63", arm_warmers="#1a1f30", emblem="fan", sandals="#22283a"),
        dict(skin=SKIN_TONES["pale"], hair="#141418", hair_style="long", eyes="#1e1a1f",
             shirt="#18181d", collar="high", sleeves="long", pants="#25252c", pants_len="long",
             sash="#5a1e22", headband="forehead", band_cloth="#121218", leg_wraps="#cfc9bb", emblem="fan"),
        dict(skin=SKIN_TONES["medium"], hair="#1e1d24", hair_style="short", eyes="#2b2024",
             shirt="#263152", collar="high", sleeves="long", pants="#3a3d46", pants_len="capri",
             sash="#1b1b20", thigh_pouch="#4a4436", emblem="fan"),
        dict(skin=SKIN_TONES["fair"], hair="#17161c", hair_style="ponytail", eyes="#241c20",
             shirt="#2b2b34", collar="high", sleeves="short", pants="#e8e5dc", pants_len="shorts",
             sash="#24242c", arm_wraps="#d8d2c4", leg_wraps="#d8d2c4", emblem="fan"),
        dict(skin=SKIN_TONES["light"], hair="#1a1c27", hair_style="messy", eyes="#2a1d22",
             shirt="#1c2236", collar="high", collar_color="#141826", sleeves="long", pants="#1f2330",
             sash="#6b2328", sash_knot=True, headband="forehead", band_cloth="#1a2036", gloves="#15161c",
             emblem="fan"),
    ],
    "senju": [
        dict(skin=SKIN_TONES["tan"], hair="#3a2618", hair_style="long", eyes="#3a2a1c",
             shirt="#2c2522", collar="round", sleeves="long", pants="#2a2420", armor="#a3342c",
             armor_trim="#c9a25a", sash="#1c1714"),
        dict(skin=SKIN_TONES["medium"], hair="#1d1814", hair_style="long", eyes="#2c2018",
             shirt="#332a22", collar="round", sleeves="long", pants="#2e2620", armor="#8c2f2a",
             armor_trim="#7b6a52", sash="#24201b", headband="forehead", band_cloth="#3a3530",
             band_plate="#9aa0a6"),
        dict(skin=SKIN_TONES["brown"], hair="#4a3020", hair_style="messy", eyes="#2e2118",
             shirt="#3b3324", collar="round", sleeves="long", pants="#3a3226", armor="#9e3b2a",
             armor_trim="#b88d4a", sash="#2a2218", bracers="#5a4a38"),
        dict(skin=SKIN_TONES["fair"], hair="#5a3a24", hair_style="ponytail", eyes="#3d2b1e",
             shirt="#2a3326", collar="wrap", undershirt="#1c2018", sleeves="long", pants="#2b3027",
             armor="#a8392f", armor_trim="#d0b070", sash="#1d221a"),
    ],
    "hyuga": [
        dict(skin=SKIN_TONES["pale"], hair="#2b1d16", hair_style="long", eyes="pale",
             shirt="#ece7dc", collar="wrap", undershirt="#3a3550", sleeves="long", pants="#2c2c3e",
             sash="#5a5478", robe_skirt=True, wide_sleeves=True, sandals="#2a2a36"),
        dict(skin=SKIN_TONES["light"], hair="#302018", hair_style="long", eyes="pale",
             shirt="#d9d0e8", collar="wrap", undershirt="#ffffff", sleeves="long", pants="#3a3550",
             sash="#2e2a44", robe_skirt=True, headband="forehead", band_cloth="#2e2a44",
             band_plate="#b4b8c0"),
        dict(skin=SKIN_TONES["fair"], hair="#241812", hair_style="long", eyes="pale",
             shirt="#efe6cf", collar="wrap", undershirt="#6a5a8a", sleeves="long", pants="#ddd4bd",
             pants_len="long", sash="#6a5a8a", sash_knot=True, wide_sleeves=True, sandals="#3a3448"),
        dict(skin=SKIN_TONES["pale"], hair="#3a271c", hair_style="ponytail", eyes="pale",
             shirt="#f2f0ec", collar="high", collar_color="#c9c0dd", sleeves="short", pants="#47405e",
             pants_len="capri", sash="#47405e", arm_wraps="#e5e0d6", leg_wraps="#e5e0d6"),
    ],
    "uzumaki": [
        dict(skin=SKIN_TONES["fair"], hair="#d8352a", hair_style="spiky", eyes="#2f4a6e",
             shirt="#e2762a", collar="high", collar_color="#2a2b38", sleeves="long", pants="#2a2b38",
             sash="#8c1f1f", emblem="spiral", emblem_color="#b0201e"),
        dict(skin=SKIN_TONES["pale"], hair="#e03a2e", hair_style="long", eyes="#3a5a4a",
             shirt="#8e2224", collar="wrap", undershirt="#f0c27a", sleeves="long", pants="#3b1c1e",
             sash="#e88a2e", sash_knot=True, robe_skirt=True, emblem="spiral", emblem_color="#f08a24"),
        dict(skin=SKIN_TONES["light"], hair="#c42a24", hair_style="short", eyes="#3a3426",
             shirt="#2e2430", collar="round", sleeves="long", pants="#2e2430", haori="#d96a24",
             haori_trim="#7a1a1a", sash="#7a1a1a", emblem="spiral", emblem_color="#9a1a18",
             headband="forehead"),
        dict(skin=SKIN_TONES["tan"], hair="#e04434", hair_style="ponytail", eyes="#4a3a2a",
             shirt="#a3302a", collar="v", sleeves="short", pants="#e07a30", pants_len="capri",
             sash="#3a1a1a", arm_wraps="#e8e0d0", gloves="#3a1a1a", emblem="spiral",
             emblem_color="#f2b04a"),
    ],
    "generic": [
        dict(skin=SKIN_TONES["fair"], hair="#4a3426", hair_style="short", eyes="#3a2a1e",
             shirt="#283454", pants="#283454", vest="#5d6b3a", headband="forehead",
             leg_wraps="#d6d0c2", thigh_pouch="#3f3a30"),
        dict(skin=SKIN_TONES["light"], hair="#c7ccd2", hair_style="spiky", eyes="#2a2a2e",
             shirt="#24304c", pants="#24304c", vest="#56653a", headband="forehead", mask="#1f2638",
             gloves="#1a1d26", leg_wraps="#d6d0c2"),
        dict(skin=SKIN_TONES["pale"], hair="#e3c26a", hair_style="ponytail", eyes="#2d4f6a",
             shirt="#5c6068", collar="high", sleeves="short", pants="#44474e", pants_len="capri",
             sash="#2c2e33", arm_wraps="#ddd7c9", arm_band=True, thigh_pouch="#2e3036"),
        dict(skin=SKIN_TONES["dark"], hair="#141414", hair_style="short", eyes="#2a1c14",
             shirt="#2f4f7a", collar="v", sleeves="short", pants="#3b4352", pants_len="long",
             sash="#1d2a3e", headband="bandage", leg_wraps="#d8d2c4", bracers="#4b5563",
             cheek_bandage=True),
        dict(skin=SKIN_TONES["medium"], hair="#2f5a3a", hair_style="messy", eyes="#3a3022",
             shirt="#4a5866", collar="high", sleeves="long", pants="#3a4450", mask="#262c34",
             arm_wraps="#d9d3c5", headband="forehead", band_cloth="#3a4450", sash="#262c34"),
        dict(skin=SKIN_TONES["tan"], hair="#8a3a22", hair_style="swept", eyes="#2e3b2a",
             shirt="#3c6e70", collar="round", sleeves="long", pants="#555a60", pants_len="capri",
             sash="#2a3a3c", chest_strap="#4a3a2a", face_paint="#a02a2a", leg_wraps="#d4cebf",
             gloves="#2a2a2a"),
    ],
}


# ---------------------------------------------------------------------------
# Preview: front and back "paper doll" views plus the raw texture
# ---------------------------------------------------------------------------

def doll(sheet, skin, dx, dy, scale, view):
    """Draw a flat doll (16 x 32 skin pixels) of the front or back view."""
    def part(name, ox, oy, flip=False, face=None):
        fx, fy, fw, fh = faces(name)[face or view]
        sheet.blit(skin, fx, fy, fw, fh, dx + ox * scale, dy + oy * scale, scale, flip)

    if view == "front":
        layout = [("head", 4, 0), ("hat", 4, 0), ("body", 4, 8), ("jacket", 4, 8),
                  ("rarm", 0, 8), ("rsleeve", 0, 8), ("larm", 12, 8), ("lsleeve", 12, 8),
                  ("rleg", 4, 20), ("rpants", 4, 20), ("lleg", 8, 20), ("lpants", 8, 20)]
    else:
        layout = [("head", 4, 0), ("hat", 4, 0), ("body", 4, 8), ("jacket", 4, 8),
                  ("larm", 0, 8), ("lsleeve", 0, 8), ("rarm", 12, 8), ("rsleeve", 12, 8),
                  ("lleg", 4, 20), ("lpants", 4, 20), ("rleg", 8, 20), ("rpants", 8, 20)]
    for name, ox, oy in layout:
        part(name, ox, oy)


def side_doll(sheet, skin, dx, dy, scale):
    """Right-side profile: head side, arm outer side over the body side."""
    def part(name, face, ox, oy):
        fx, fy, fw, fh = faces(name)[face]
        sheet.blit(skin, fx, fy, fw, fh, dx + ox * scale, dy + oy * scale, scale)
    for n in ("head", "hat"):
        part(n, "right", 0, 0)
    for n in ("rleg", "rpants"):
        part(n, "right", 2, 20)
    for n in ("rarm", "rsleeve"):
        part(n, "right", 2, 8)


def contact_sheet(skins):
    scale = 5
    cell_w = (16 + 2 + 16 + 2 + 8) * scale + 64 * 3 + 30
    cell_h = 64 * 3 + 20
    cols = 3
    rows = (len(skins) + cols - 1) // cols
    W, H = cols * cell_w + 20, rows * cell_h + 20
    sheet = Canvas(W, H, (46, 48, 54, 255))
    for i, (clan, n, cv) in enumerate(skins):
        cx = 10 + (i % cols) * cell_w
        cy = 10 + (i // cols) * cell_h
        band = CLAN_BAND[clan]
        for y in range(cell_h - 8):
            for x in range(4):
                sheet.set(cx + x, cy + y, band)
        x0 = cx + 10
        # raw texture (checkered background so transparency is visible)
        for y in range(64 * 3):
            for x in range(64 * 3):
                sheet.set(x0 + x, cy + 4 + y, (70, 72, 80, 255) if ((x // 12 + y // 12) % 2) else (60, 62, 70, 255))
        sheet.blit(cv, 0, 0, 64, 64, x0, cy + 4, 3)
        x1 = x0 + 64 * 3 + 12
        doll(sheet, cv, x1, cy, scale, "front")
        doll(sheet, cv, x1 + 18 * scale, cy, scale, "back")
        side_doll(sheet, cv, x1 + 36 * scale, cy, scale)
    return sheet


CLAN_BAND = {
    "uchiha": (190, 40, 45, 255), "senju": (110, 140, 70, 255), "hyuga": (190, 180, 220, 255),
    "uzumaki": (235, 120, 40, 255), "generic": (120, 130, 145, 255),
}


def main():
    catalog = {}
    rendered = []
    for ci, (clan, specs) in enumerate(CLANS.items()):
        catalog[clan] = len(specs)
        for i, spec in enumerate(specs):
            cv = Ninja(spec, seed=ci * 31 + i * 7 + 1).render()
            path = os.path.join(OUT_DIR, clan, "%d.png" % i)
            cv.save(path)
            rendered.append((clan, i, cv))
    with open(os.path.join(OUT_DIR, "catalog.json"), "w") as f:
        json.dump(catalog, f, indent=2)
        f.write("\n")
    contact_sheet(rendered).save(PREVIEW)
    print("wrote %d skins: %s" % (len(rendered), catalog))
    print("preview:", os.path.relpath(PREVIEW, ROOT))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Art for NOIRM's foods, herbs, pills and the apothecary, in the ink style of docs/ART_STYLE.md.

    python3 tools/pillgen/gen_pills.py [--preview docs/screenshots/pills-icons.png]

Writes (all our own designs, drawn by the code below; nothing traced or sampled):
    assets/noirm/textures/item/<food|herb|seeds|pill>.png      16x16 item icons
    assets/noirm/textures/block/<herb>_crop_stage<0-3>.png     16x16 plant stages (rice and the five herbs)
    assets/noirm/textures/block/apothecary_*.png               the mortar, its stump and pestle
    assets/noirm/models/item/*.json, models/block/*.json, blockstates/*.json

The look: a near-black ink outline around every silhouette, two or three carved tones lit from the
top left, a little grit, few colours. Pills read by colour first (each pill has one) and by a mark
second; foods by their shape (a rice-ball triangle, a bowl, a skewer).
"""
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")
ITEM_TEX = os.path.join(ASSETS, "textures/item")
BLOCK_TEX = os.path.join(ASSETS, "textures/block")

INK = (14, 12, 14)


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def hsh(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def grit(c, x, y, salt, amount=0.07):
    return shade(c, 1 - amount + 2 * amount * hsh(x, y, salt))


def tones(base):
    """hi, light, mid, dark, deep: the carved ramp of one colour."""
    c = rgb(base) if isinstance(base, str) else base
    return [mix(c, (255, 250, 235), 0.45), shade(c, 1.12), c, shade(c, 0.68), shade(c, 0.42)]


class Icon:
    """A 16x16 drawing: colour pixels, then an ink outline around everything drawn."""

    def __init__(self, salt, size=16):
        self.n = size
        self.c = {}
        self.salt = salt
        self.ink = set()

    def put(self, x, y, col):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.c[(x, y)] = col

    def ink_at(self, x, y):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.ink.add((x, y))
            self.c[(x, y)] = INK

    def disc(self, cx, cy, r, ramp, light=(-0.6, -0.7), marks=None):
        """A shaded sphere. ramp = tones(); marks(x, y, dx, dy) may return a colour to paint instead."""
        for y in range(self.n):
            for x in range(self.n):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(dx, dy)
                if d > r:
                    continue
                nz = math.sqrt(max(0.0, 1 - (d / r) ** 2))
                lit = (-dx / r * light[0] * -1 + -dy / r * light[1] * -1) * 0.0
                lit = (dx / r) * light[0] + (dy / r) * light[1] + nz * 0.55
                t = 0 if lit > 0.95 else 1 if lit > 0.62 else 2 if lit > 0.25 else 3 if lit > -0.1 else 4
                col = ramp[t]
                if marks:
                    m = marks(x, y, dx, dy)
                    if m is not None:
                        col = m
                self.put(x, y, grit(col, x, y, self.salt))

    def rect(self, x0, y0, x1, y1, col, gritty=True):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, grit(col, x, y, self.salt) if gritty else col)

    def poly(self, pts, colfn):
        """Fills a polygon; colfn(x, y) gives the colour."""
        ys = [p[1] for p in pts]
        for y in range(max(0, int(min(ys))), min(self.n, int(math.ceil(max(ys))) + 1)):
            yc = y + 0.5
            xs = []
            for i in range(len(pts)):
                (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
                if (y1 <= yc < y2) or (y2 <= yc < y1):
                    xs.append(x1 + (yc - y1) * (x2 - x1) / (y2 - y1))
            xs.sort()
            for a, b in zip(xs[::2], xs[1::2]):
                for x in range(max(0, int(math.floor(a + 0.5))), min(self.n, int(math.floor(b + 0.5)))):
                    self.put(x, y, colfn(x, y))

    def line(self, x0, y0, x1, y1, col):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps + 1):
            t = i / max(1, steps)
            self.put(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), col)

    def outline(self):
        """Every drawn pixel that touches empty space (4 ways) becomes ink."""
        edge = []
        for (x, y) in self.c:
            for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + ddx, y + ddy) not in self.c:
                    edge.append((x, y))
                    break
        for p in edge:
            self.c[p] = INK

    def image(self):
        img = Image.new("RGBA", (self.n, self.n), (0, 0, 0, 0))
        px = img.load()
        for (x, y), col in self.c.items():
            px[x, y] = tuple(col) + (255,)
        return img


# ====================================================================== pills

def pill_pair(icon, base, mark=None, salt_shift=0):
    """Two pills: a small one behind (top left) and the main one (bottom right)."""
    ramp = tones(base)
    icon.disc(4.3, 4.3, 3.0, ramp)
    icon.disc(10.0, 10.0, 5.1, ramp, marks=mark)


def pill_icon(name, base, mark=None):
    icon = Icon(len(name) * 7)
    pill_pair(icon, base, mark)
    icon.outline()
    return icon.image()


def soldier_pill():
    # Brown field pill with a scored line across: the plain soldier's ration.
    ramp = tones("#8A6A3A")
    return pill_icon("soldier", "#8A6A3A", lambda x, y, dx, dy: ramp[4] if abs(dx - dy * 0.3) < 0.55 and abs(dx) < 3.6 else None)


def stamina_pill():
    leaf = tones("#E8F0C8")
    return pill_icon("stamina", "#4FA65A", lambda x, y, dx, dy: leaf[1] if (abs(dx + 0.5) + abs(dy - 0.5) * 1.7) < 1.6 else None)


def chakra_pill():
    glow = tones("#CDE6FF")

    def mark(x, y, dx, dy):
        a = math.atan2(dy, dx)
        r = math.hypot(dx, dy)
        swirl = (a + r * 0.9) % (2 * math.pi)
        return glow[1] if 1.2 < r < 3.8 and swirl < 0.8 else None
    return pill_icon("chakra", "#2F68C8", mark)


def blood_pill():
    return pill_icon("blood", "#A8141A", lambda x, y, dx, dy: (250, 210, 200) if -2.8 < dx < -1.6 and -3.2 < dy < -2.0 else None)


def antidote_pill():
    # A capsule: teal and bone halves, on the diagonal.
    icon = Icon(41)
    teal, bone = tones("#2FAE8A"), tones("#E8E2D0")
    for y in range(16):
        for x in range(16):
            # distance to the segment (4,11)-(11,4)
            px, py = x + 0.5, y + 0.5
            ax, ay, bx, by = 4.5, 11.5, 11.5, 4.5
            t = max(0, min(1, ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)))
            cx, cy = ax + t * (bx - ax), ay + t * (by - ay)
            d = math.hypot(px - cx, py - cy)
            if d > 3.4:
                continue
            ramp = teal if t < 0.5 else bone
            side = (px - cx) * -0.7 + (py - cy) * -0.7
            k = 0 if side > 2.0 else 1 if side > 0.8 else 2 if side > -1.2 else 3
            icon.put(x, y, grit(ramp[k], x, y, 41))
    icon.outline()
    icon.line(7, 9, 8, 8, INK)  # the seam
    return icon.image()


def focus_pill():
    white = (246, 240, 255)
    return pill_icon("focus", "#9C86E0", lambda x, y, dx, dy: INK if math.hypot(dx, dy) < 0.9 else white if math.hypot(dx, dy) < 1.9 else None)


def iron_bone_pill():
    bone = (236, 232, 220)
    return pill_icon("iron_bone", "#7C7E88", lambda x, y, dx, dy: bone if (abs(dx) < 0.6 and abs(dy) < 3) or (abs(dy) < 0.6 and abs(dx) < 3) else None)


def three_seal_pill():
    # One large pill in three wedges (blood red, leaf green, gold), sealed by ink lines.
    icon = Icon(77)
    ramps = [tones("#C42A20"), tones("#3E9A48"), tones("#E2B33C")]
    cx, cy, r = 8.0, 8.4, 6.6
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            a = (math.degrees(math.atan2(dy, dx)) + 90 + 360) % 360
            wedge = int(a // 120)
            nz = math.sqrt(max(0.0, 1 - (d / r) ** 2))
            lit = (dx / r) * -0.6 + (dy / r) * -0.7 + nz * 0.55
            t = 0 if lit > 0.95 else 1 if lit > 0.62 else 2 if lit > 0.25 else 3 if lit > -0.1 else 4
            col = ramps[wedge][t]
            edge_a = min(a % 120, 120 - a % 120)
            if edge_a < 9 and d > 1.2:
                col = INK
            icon.put(x, y, grit(col, x, y, 77))
    icon.outline()
    icon.put(8, 8, (250, 240, 210))
    return icon.image()


def ruined_pill():
    icon = Icon(91)
    ramp = tones("#3A3028")
    pts = [(3, 7), (6, 4), (10, 4), (13, 7), (12, 12), (8, 13), (4, 12)]
    icon.poly(pts, lambda x, y: grit(ramp[2 if (x + y) % 5 else 3], x, y, 91, 0.15))
    icon.outline()
    icon.line(6, 6, 9, 10, INK)
    icon.line(9, 10, 11, 9, INK)
    icon.put(7, 6, ramp[1])
    icon.put(5, 9, (120, 60, 40))
    return icon.image()


# ====================================================================== foods

RICE = tones("#F2EEE0")
NORI = tones("#22302A")
BOWL = tones("#7A3020")
WOOD = tones("#A87A50")


def rice_fn(x, y, ramp=RICE):
    k = 1 if (x * 3 + y * 5) % 7 else 2
    if (x + y * 2) % 11 == 0:
        k = 0
    return grit(ramp[k], x, y, 5, 0.05)


def onigiri(plum=False):
    icon = Icon(13 if plum else 17)
    icon.poly([(8, 1.5), (15, 13.5), (1, 13.5)], rice_fn)
    # Shade the right side.
    for (x, y), col in list(icon.c.items()):
        if x > 9 and (x - 9) > (13 - y) * 0.2:
            icon.c[(x, y)] = shade(col, 0.86)
    icon.rect(4, 10, 11, 13, NORI[2])
    icon.rect(4, 10, 11, 10, NORI[1])
    icon.outline()
    if plum:
        icon.rect(7, 5, 8, 6, (194, 54, 74), gritty=False)
        icon.put(7, 5, (236, 140, 150))
    return icon.image()


def steamed_rice():
    icon = Icon(19)
    # A small wooden bowl heaped with rice.
    icon.poly([(1.5, 8), (14.5, 8), (12, 14), (4, 14)], lambda x, y: grit(WOOD[2 if x > 8 else 1], x, y, 19))
    icon.disc(8, 8.5, 5.6, RICE, marks=lambda x, y, dx, dy: None if dy < 0.5 else None)
    for (x, y) in list(icon.c):
        if y >= 9 and (x, y) in icon.c and y < 14 and abs(x + 0.5 - 8) < 7:
            icon.c[(x, y)] = grit(WOOD[1 if x < 8 else 2], x, y, 19)
    icon.outline()
    icon.line(2, 9, 13, 9, WOOD[4])
    return icon.image()


def bowl(icon, broth, toppings):
    """A deep red-lacquer ramen bowl seen from the side, its broth and toppings showing at the top."""
    icon.poly([(0.5, 6), (15.5, 6), (12.5, 13), (3.5, 13)], lambda x, y: grit(BOWL[1 if x < 6 else 2 if x < 11 else 3], x, y, 23))
    icon.rect(5, 13, 10, 14, BOWL[3])
    for x in range(1, 15):
        icon.put(x, 6, grit(broth[1], x, 6, 23))
        icon.put(x, 5, grit(broth[2], x, 5, 23))
    for (x, y, col) in toppings:
        icon.put(x, y, col)
    icon.outline()
    icon.line(2, 9, 13, 9, BOWL[0])  # the lacquer band


def ramen_icon(kind):
    icon = Icon(29 + len(kind))
    noodle = (240, 214, 140)
    if kind == "pork":
        broth = tones("#E0A848")
        tops = [(3, 4, noodle), (4, 4, noodle), (5, 3, (232, 222, 210)), (6, 3, (232, 222, 210)), (6, 4, (150, 90, 70)),
                (9, 4, (250, 246, 236)), (10, 4, (250, 196, 60)), (11, 4, (250, 246, 236)), (10, 3, (250, 246, 236)),
                (12, 4, (60, 90, 50)), (13, 4, (60, 90, 50)), (7, 4, (236, 236, 236)), (8, 4, (220, 120, 140))]
    elif kind == "fish":
        broth = tones("#E8D6A8")
        tops = [(3, 4, noodle), (4, 4, noodle), (5, 4, noodle), (7, 3, (226, 132, 96)), (8, 3, (240, 160, 120)), (9, 3, (226, 132, 96)),
                (7, 4, (250, 236, 220)), (9, 4, (250, 236, 220)), (11, 4, (40, 54, 46)), (12, 4, (40, 54, 46)), (12, 3, (40, 54, 46))]
    else:
        broth = tones("#8A3A1E")
        tops = [(3, 4, noodle), (4, 4, noodle), (6, 3, (110, 52, 36)), (7, 3, (140, 70, 48)), (8, 3, (110, 52, 36)), (6, 4, (90, 40, 28)),
                (8, 4, (90, 40, 28)), (10, 4, (226, 120, 40)), (11, 4, (226, 120, 40)), (12, 3, (70, 120, 50)), (12, 4, (70, 120, 50))]
    bowl(icon, broth, tops)
    return icon.image()


def curry_rice():
    icon = Icon(43)
    plate = tones("#D8D0C0")
    curry = tones("#B8702A")
    icon.poly([(0.5, 9), (15.5, 9), (14, 13), (2, 13)], lambda x, y: grit(plate[1 if y < 11 else 2], x, y, 43))
    # Rice on the left, curry on the right, mounded.
    icon.disc(5.5, 9, 4.2, RICE)
    icon.disc(10.5, 9.5, 4.4, curry, marks=lambda x, y, dx, dy: (200, 120, 50) if (x + y) % 4 == 0 and dy < 1 else
              (120, 160, 70) if (x * 7 + y) % 13 == 0 else None)
    for (x, y) in list(icon.c):
        if y >= 11:
            icon.c[(x, y)] = grit(plate[2 if x > 8 else 1], x, y, 43)
    icon.outline()
    return icon.image()


def dango():
    icon = Icon(47)
    stick = tones("#C8A070")
    icon.line(2, 14, 13, 3, stick[2])
    icon.line(3, 14, 14, 3, stick[3])
    for (cx, cy, col) in ((5.0, 11.0, "#7FB86A"), (8.0, 8.0, "#F2EEE0"), (11.0, 5.0, "#E89AB0")):
        icon.disc(cx, cy, 2.6, tones(col))
    icon.outline()
    return icon.image()


def fish(cooked):
    icon = Icon(53 if cooked else 59)
    stick = tones("#C8A070")
    body = tones("#B07838") if cooked else tones("#6E8AA0")
    belly = tones("#E0B070") if cooked else tones("#C8D4DC")
    icon.line(1, 15, 14, 2, stick[2])
    # A fish along the skewer, head up-right.
    pts = [(3, 13), (6, 9), (10, 5), (13, 3), (13, 6), (10, 9), (7, 12), (4, 14)]
    icon.poly(pts, lambda x, y: grit(body[2] if (x - y) < 0 else belly[1], x, y, 53))
    icon.poly([(1, 15), (3, 12), (4, 15)], lambda x, y: grit(body[3], x, y, 53))  # tail
    icon.outline()
    icon.put(12, 4, (250, 250, 240))
    if cooked:
        for (x, y) in ((6, 10), (8, 8), (10, 6), (7, 11), (9, 9)):
            icon.put(x, y, (60, 34, 20))
    else:
        icon.put(7, 10, body[0])
        icon.put(9, 8, body[0])
    icon.line(14, 2, 15, 1, stick[3])
    return icon.image()


def field_ration():
    icon = Icon(61)
    paper = tones("#C8B488")
    icon.rect(2, 5, 13, 12, paper[2])
    icon.rect(2, 5, 13, 6, paper[1])
    icon.rect(12, 5, 13, 12, paper[3])
    for y in range(4, 14):
        icon.put(7, y, (150, 40, 30))
        icon.put(8, y, (120, 30, 24))
    icon.outline()
    icon.put(8, 3, (150, 40, 30))
    icon.put(9, 2, (150, 40, 30))
    icon.put(4, 8, paper[4])
    icon.put(10, 10, paper[4])
    return icon.image()


def nutrient_bar():
    icon = Icon(67)
    wrap = tones("#3A4A5C")
    bar = tones("#B88A48")
    icon.poly([(2, 10), (10, 2), (14, 6), (6, 14)], lambda x, y: grit(wrap[2] if x + y > 14 else bar[1 if (x + y) % 3 else 2], x, y, 67))
    icon.outline()
    icon.line(5, 9, 9, 5, bar[3])
    icon.put(11, 8, wrap[0])
    return icon.image()


def umeboshi():
    icon = Icon(71)
    ramp = tones("#B02A48")
    icon.disc(5.5, 9.5, 3.6, ramp)
    icon.disc(10, 7.5, 3.8, ramp, marks=lambda x, y, dx, dy: ramp[3] if (x * 3 + y) % 5 == 0 else None)
    icon.outline()
    icon.put(10, 3, (80, 120, 50))
    icon.put(11, 3, (80, 120, 50))
    return icon.image()


def rice_grain():
    # A small straw sack of rice, open at the top, the white grains heaped inside.
    icon = Icon(73)
    straw = tones("#C8A860")
    icon.poly([(3, 6), (13, 6), (14, 14), (2, 14)], lambda x, y: grit(straw[1 if x < 7 else 2 if x < 11 else 3], x, y, 73))
    icon.disc(8, 6.5, 4.4, RICE)
    for (x, y) in list(icon.c):
        if y >= 7 and 3 <= x <= 13 and y <= 14:
            icon.c[(x, y)] = grit(straw[1 if x < 7 else 2 if x < 11 else 3], x, y, 73)
    icon.outline()
    icon.line(3, 8, 13, 8, straw[4])
    for y in (10, 12):
        icon.put(5, y, straw[3])
        icon.put(10, y + 1, straw[3])
    return icon.image()


# ====================================================================== herbs

HERB_COLORS = {
    "moonroot": ("#5A7A58", "#BFD8F0"),     # leaves, pale blue root
    "bloodleaf": ("#5A1E1E", "#B8282A"),    # dark stem, blood-red leaf
    "ironmillet": ("#6A7A60", "#A8A894"),   # grey-green stalk, iron-grey grain
    "mistcap": ("#6A8A6A", "#D2C8E8"),      # stem, pale lilac cap
    "ashthorn": ("#5A4434", "#E07A2A"),     # bark, ember-orange thorns
    "rice": ("#6E9A4A", "#E0C870"),         # stalks, golden grain
}


def herb_icon(name):
    icon = Icon(len(name) * 11)
    stem, accent = tones(HERB_COLORS[name][0]), tones(HERB_COLORS[name][1])
    if name == "moonroot":
        # A fat pale-blue root with a tuft of three leaves.
        icon.poly([(4.5, 7), (11.5, 7), (12.5, 11), (8, 15.5), (3.5, 11)], lambda x, y: grit(accent[1 if x < 7 else 2 if x < 10 else 3], x, y, 3))
        icon.poly([(7, 7.5), (4, 1), (6, 1), (8, 6)], lambda x, y: grit(stem[1], x, y, 4))
        icon.poly([(8, 7.5), (8, 0.5), (9.5, 0.5), (9.5, 7)], lambda x, y: grit(stem[2], x, y, 4))
        icon.poly([(9, 7.5), (11, 1), (13, 1.5), (10, 7.5)], lambda x, y: grit(stem[3], x, y, 4))
        icon.outline()
        icon.put(6, 9, accent[0])
        icon.put(7, 10, accent[0])
        icon.line(9, 12, 10, 10, accent[3])
        return icon.image()
    elif name == "bloodleaf":
        icon.poly([(8, 1), (13, 6), (12, 11), (8, 14), (4, 11), (3, 6)], lambda x, y: grit(accent[1 if x < 8 else 2], x, y, 7))
        icon.outline()
        icon.line(8, 3, 8, 15, stem[3])
        for (a, b) in ((5, 6), (6, 9), (11, 6), (10, 9)):
            icon.line(8, b - 1, a, b - 2, accent[3])
        return icon.image()
    elif name == "ironmillet":
        # Two stalks with heavy iron-grey grain heads.
        icon.line(6, 15, 6, 8, stem[2])
        icon.line(10, 15, 10, 7, stem[3])
        icon.poly([(4, 9), (5, 2), (7, 1), (8, 8)], lambda x, y: grit(accent[1 if x < 6 else 2], x, y, 9))
        icon.poly([(8, 8), (9, 2), (11, 1), (12, 8)], lambda x, y: grit(accent[2 if x < 10 else 3], x, y, 9))
        icon.line(12, 13, 14, 11, stem[1])
        icon.outline()
        for y in range(3, 8, 2):
            icon.put(6, y, accent[0])
            icon.put(10, y, accent[0])
        return icon.image()
    elif name == "mistcap":
        icon.line(8, 15, 8, 8, stem[2])
        icon.line(9, 15, 9, 8, stem[3])
        icon.poly([(2, 8), (5, 3), (11, 3), (14, 8)], lambda x, y: grit(accent[1 if y < 6 else 2], x, y, 11))
        icon.put(6, 5, accent[0])
        icon.put(9, 4, accent[0])
    elif name == "ashthorn":
        icon.line(3, 14, 12, 3, stem[2])
        icon.line(4, 14, 13, 3, stem[3])
        for (x, y) in ((6, 10), (8, 8), (10, 6), (11, 9), (5, 7), (9, 4)):
            icon.put(x, y, accent[1])
            icon.put(x + (1 if x < 8 else -1), y - 1, accent[2])
    icon.outline()
    return icon.image()


def seeds_icon(name):
    icon = Icon(len(name) * 13)
    stem, accent = tones(HERB_COLORS[name][0]), tones(HERB_COLORS[name][1])
    seed = mix(rgb(HERB_COLORS[name][1]), (150, 120, 80), 0.55)
    ramp = tones(seed)
    # A twist of paper with four seeds spilled in front of it.
    paper = tones("#D8CCA8")
    icon.poly([(2, 2), (8, 1), (7, 7), (3, 8)], lambda x, y: grit(paper[1 if x < 5 else 2], x, y, 31))
    for (x, y) in ((6.5, 10.5), (10.5, 8.5), (10.5, 13.0), (13.5, 11.0)):
        icon.disc(x, y, 2.1, ramp)
    icon.outline()
    icon.put(4, 4, accent[1])
    icon.put(5, 5, accent[2])
    return icon.image()


def medical_scroll():
    icon = Icon(97)
    paper = tones("#E8DCB8")
    wood = tones("#7C5634")
    # A closed scroll on the diagonal, a green cord and a red medic's seal with a white cross.
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            t = (px + py) / 2
            d = abs(px - py) / math.sqrt(2)
            if 2.5 < t < 13.5 and d < 3.1:
                side = (py - px)
                k = 0 if side < -2 else 1 if side < 0.5 else 2
                end = t < 3.6 or t > 12.4
                icon.put(x, y, grit((wood if end else paper)[k + (1 if end else 0)], x, y, 97))
    icon.outline()
    for (x, y) in ((7, 9), (8, 8), (6, 8), (8, 6), (9, 7)):
        icon.put(x, y, (60, 130, 70))
    icon.rect(7, 6, 9, 8, (176, 32, 28), gritty=False)
    icon.put(8, 6, (250, 244, 230))
    icon.put(8, 8, (250, 244, 230))
    icon.put(7, 7, (250, 244, 230))
    icon.put(9, 7, (250, 244, 230))
    icon.put(8, 7, (250, 244, 230))
    return icon.image()


# ====================================================================== plants (block textures)

def crop_stage(name, stage):
    icon = Icon(len(name) * 17 + stage)
    stem, accent = tones(HERB_COLORS[name][0]), tones(HERB_COLORS[name][1])
    height = [4, 7, 11, 14][stage]
    tufts = [(3, 0), (7, 1), (11, 0), (5, 2), (13, 1)]
    for i, (x0, lean) in enumerate(tufts):
        h = height - (i % 3)
        if h < 2:
            continue
        top = 16 - h
        icon.line(x0, 15, x0 + lean - (i % 2), top, stem[2 if i % 2 else 1])
        if stage >= 1:
            icon.put(x0 - 1, top + 2, stem[1])
            icon.put(x0 + 1, top + 3, stem[3])
        if stage == 3:
            tx, ty = x0 + lean - (i % 2), top
            if name == "rice":
                for k in range(3):
                    icon.put(tx + 1 + k // 2, ty + k, accent[1 + k % 2])
            elif name == "moonroot":
                icon.put(tx, ty, stem[0])
                if i < 3:
                    icon.put(x0, 14, accent[1])
                    icon.put(x0 + 1, 14, accent[2])
                    icon.put(x0, 13, accent[0])
            elif name == "bloodleaf":
                icon.put(tx - 1, ty, accent[1])
                icon.put(tx, ty, accent[2])
                icon.put(tx + 1, ty + 1, accent[1])
                icon.put(tx, ty + 1, accent[3])
            elif name == "ironmillet":
                for k in range(4):
                    icon.put(tx + (k % 2), ty + k, accent[1 + k % 2])
            elif name == "mistcap":
                for dx in (-1, 0, 1):
                    icon.put(tx + dx, ty, accent[1])
                icon.put(tx, ty - 1, accent[0])
            elif name == "ashthorn":
                icon.put(tx, ty, accent[1])
                icon.put(tx + 1, ty + 2, accent[2])
                icon.put(tx - 1, ty + 4, accent[1])
    if stage == 2 and name in ("moonroot", "mistcap", "bloodleaf"):
        icon.put(7, 16 - height + 1, accent[2])
    # Plants keep a thin ink edge only on their darker side (outlining every blade turns them to mush).
    img = icon.image()
    px = img.load()
    for (x, y) in list(icon.c):
        if x + 1 < 16 and (x + 1, y) not in icon.c and (x, y) in icon.c and icon.c[(x, y)] != INK and y > 16 - height + 2:
            px[x, y] = tuple(shade(icon.c[(x, y)], 0.55)) + (255,)
    return img


# ====================================================================== the apothecary

def block_tex(fn, salt, size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    px = img.load()
    for y in range(size):
        for x in range(size):
            px[x, y] = tuple(fn(x, y)) + (255,)
    return img


STONE = tones("#8A8780")
DARK_STONE = tones("#5A5852")
BARK = tones("#6A4A30")
RINGS = tones("#B08858")


def apothecary_stone(x, y):
    # Rough-hewn mortar side: grey stone, horizontal tool marks, an ink rim at the top and a base line.
    if y == 0:
        return INK
    if y == 1:
        return STONE[0]
    k = 2
    if (x * 7 + y * 3) % 9 == 0:
        k = 3
    if y % 5 == 3 and (x + y) % 3:
        k = 3
    if hsh(x, y, 5) > 0.92:
        k = 1
    if x in (0, 15):
        k = 4
    return grit(STONE[k], x, y, 5, 0.09)


def apothecary_top(x, y):
    # Looking into the mortar: a stone ring, the dark bowl, ground green herb paste in the middle.
    d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
    if d > 7.2:
        return grit(STONE[2], x, y, 9)
    if d > 6.2:
        return STONE[0] if (x + y) < 15 else STONE[3]
    if d > 4.6:
        return grit(DARK_STONE[3], x, y, 9)
    paste = tones("#4E7A3A")
    k = 1 if hsh(x, y, 11) > 0.6 else 2 if hsh(x, y, 12) > 0.3 else 3
    if (x, y) in ((7, 6), (9, 8), (6, 9)):
        return (176, 40, 34)  # a crushed bloodleaf
    return grit(paste[k], x, y, 13)


def apothecary_bark(x, y):
    k = 2 if (x % 4) else 3
    if (x % 4) == 1:
        k = 1
    if hsh(x // 2, y // 3, 17) > 0.8:
        k = 4
    if y in (0,):
        return INK
    return grit(BARK[k], x, y, 17, 0.1)


def apothecary_rings(x, y):
    d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
    if d > 7.4:
        return BARK[3]
    k = 1 if int(d * 1.4) % 2 else 2
    if d < 1:
        k = 3
    return grit(RINGS[k], x, y, 19)


def apothecary_pestle(x, y):
    wood = tones("#C8A070")
    return grit(wood[1 if x < 8 else 2], x, y, 23)


# ====================================================================== models

def item_model(name, texture=None):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture or "noirm:item/" + name}}


def apothecary_model():
    t = "noirm:block/"
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": t + "apothecary_stone", "stone": t + "apothecary_stone", "top": t + "apothecary_top",
                     "bark": t + "apothecary_bark", "rings": t + "apothecary_rings", "pestle": t + "apothecary_pestle"},
        "elements": [
            {"from": [2, 0, 2], "to": [14, 7, 14], "faces": {
                "down": {"uv": [2, 2, 14, 14], "texture": "#rings", "cullface": "down"},
                "up": {"uv": [2, 2, 14, 14], "texture": "#rings"},
                "north": {"uv": [2, 9, 14, 16], "texture": "#bark"}, "south": {"uv": [2, 9, 14, 16], "texture": "#bark"},
                "west": {"uv": [2, 9, 14, 16], "texture": "#bark"}, "east": {"uv": [2, 9, 14, 16], "texture": "#bark"}}},
            {"from": [3, 7, 3], "to": [13, 12, 13], "faces": {
                "down": {"uv": [3, 3, 13, 13], "texture": "#stone"},
                "up": {"uv": [3, 3, 13, 13], "texture": "#top"},
                "north": {"uv": [3, 0, 13, 5], "texture": "#stone"}, "south": {"uv": [3, 0, 13, 5], "texture": "#stone"},
                "west": {"uv": [3, 0, 13, 5], "texture": "#stone"}, "east": {"uv": [3, 0, 13, 5], "texture": "#stone"}}},
            {"from": [9, 9, 7], "to": [11, 18, 9], "rotation": {"angle": 22.5, "axis": "z", "origin": [10, 10, 8]}, "faces": {
                "up": {"uv": [0, 0, 2, 2], "texture": "#pestle"}, "down": {"uv": [0, 0, 2, 2], "texture": "#pestle"},
                "north": {"uv": [6, 0, 8, 9], "texture": "#pestle"}, "south": {"uv": [6, 0, 8, 9], "texture": "#pestle"},
                "west": {"uv": [8, 0, 10, 9], "texture": "#pestle"}, "east": {"uv": [8, 0, 10, 9], "texture": "#pestle"}}},
        ],
    }


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    items = {
        "soldier_pill": soldier_pill(), "stamina_pill": stamina_pill(), "chakra_pill": chakra_pill(), "blood_pill": blood_pill(),
        "antidote_pill": antidote_pill(), "focus_pill": focus_pill(), "iron_bone_pill": iron_bone_pill(),
        "three_seal_pill": three_seal_pill(), "ruined_pill": ruined_pill(),
        "ramen": ramen_icon("pork"), "fish_ramen": ramen_icon("fish"), "beef_ramen": ramen_icon("beef"),
        "onigiri": onigiri(), "plum_onigiri": onigiri(True), "steamed_rice": steamed_rice(), "umeboshi": umeboshi(),
        "dango": dango(), "curry_rice": curry_rice(), "raw_fish_skewer": fish(False), "grilled_fish": fish(True),
        "field_ration": field_ration(), "nutrient_bar": nutrient_bar(), "rice": rice_grain(), "medical_scroll": medical_scroll(),
    }
    for herb in ("moonroot", "bloodleaf", "ironmillet", "mistcap", "ashthorn"):
        items[herb] = herb_icon(herb)
        items[herb + "_seeds"] = seeds_icon(herb)
    os.makedirs(ITEM_TEX, exist_ok=True)
    os.makedirs(BLOCK_TEX, exist_ok=True)
    for name, img in items.items():
        img.save(os.path.join(ITEM_TEX, name + ".png"))
        write_json(os.path.join(ASSETS, "models/item", name + ".json"), item_model(name))

    blocks = {}
    for crop in ("rice", "moonroot", "bloodleaf", "ironmillet", "mistcap", "ashthorn"):
        variants = {}
        for stage in range(4):
            tex = f"{crop}_crop_stage{stage}"
            img = crop_stage(crop, stage)
            img.save(os.path.join(BLOCK_TEX, tex + ".png"))
            blocks[tex] = img
            write_json(os.path.join(ASSETS, "models/block", tex + ".json"),
                       {"parent": "minecraft:block/crop", "render_type": "minecraft:cutout", "textures": {"crop": "noirm:block/" + tex}})
            variants[f"age={stage}"] = {"model": "noirm:block/" + tex}
        write_json(os.path.join(ASSETS, "blockstates", crop + "_crop.json"), {"variants": variants})

    for name, fn, salt in (("apothecary_stone", apothecary_stone, 5), ("apothecary_top", apothecary_top, 9),
                           ("apothecary_bark", apothecary_bark, 17), ("apothecary_rings", apothecary_rings, 19),
                           ("apothecary_pestle", apothecary_pestle, 23)):
        img = block_tex(fn, salt)
        img.save(os.path.join(BLOCK_TEX, name + ".png"))
        blocks[name] = img
    write_json(os.path.join(ASSETS, "models/block/apothecary.json"), apothecary_model())
    write_json(os.path.join(ASSETS, "models/item/apothecary.json"), {"parent": "noirm:block/apothecary"})
    write_json(os.path.join(ASSETS, "blockstates/apothecary.json"), {"variants": {
        "facing=north": {"model": "noirm:block/apothecary"}, "facing=east": {"model": "noirm:block/apothecary", "y": 90},
        "facing=south": {"model": "noirm:block/apothecary", "y": 180}, "facing=west": {"model": "noirm:block/apothecary", "y": 270}}})

    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        allimgs = list(items.items()) + list(blocks.items())
        cols = 10
        rows = (len(allimgs) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 72 + 8, rows * 72 + 8), (54, 56, 62, 255))
        for i, (name, img) in enumerate(allimgs):
            big = img.resize((64, 64), Image.NEAREST)
            sheet.paste(big, (8 + (i % cols) * 72, 8 + (i // cols) * 72), big)
        sheet.convert("RGB").save(out, quality=92)
    print("wrote %d item icons, %d block textures" % (len(items), len(blocks)))


if __name__ == "__main__":
    main()

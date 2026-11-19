"""Draws the space-time and genjutsu textures (our own designs, docs/ART_STYLE.md: ink first, raw
edges). Run from the repository root:

    python3 tools/vfxgen/gen_spacetime.py [--preview out.png]

Writes into src/main/resources/assets/noirm/textures/:
  misc/spacetime_white.png   a plain white square (afterimages and silhouettes are tinted from it)
  misc/spacetime_glow.png    a soft round light with a hot core (flashes, streak ends)
  misc/hiraishin_glyph.png   the space-time formula seal: white strokes on transparency, tinted in game
  misc/genjutsu_crow.png     a crow, two frames side by side (wings up, wings down), ink black
  misc/genjutsu_eye.png      a huge eye for the sky of an illusion: white, tinted in game
  item/space_time_scroll.png the Space-Time Formula scroll (16x16, like the other scrolls)
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = "src/main/resources/assets/noirm/textures"


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return img


def white():
    return Image.new("RGBA", (16, 16), (255, 255, 255, 255))


def glow(size=64):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            if d >= 1:
                continue
            core = max(0.0, 1 - d / 0.22) ** 1.5
            body = (1 - d) ** 2.2
            a = min(1.0, core + body * 0.85)
            px[x, y] = (255, 255, 255, int(255 * a))
    return img


def brush_line(draw, p0, p1, w0, w1, rng, steps=24, alpha=255):
    """A dry-brush stroke: overlapping round dabs whose width eases from w0 to w1, a little jitter."""
    for i in range(steps + 1):
        t = i / steps
        x = p0[0] + (p1[0] - p0[0]) * t + rng.uniform(-0.4, 0.4)
        y = p0[1] + (p1[1] - p0[1]) * t + rng.uniform(-0.4, 0.4)
        w = (w0 + (w1 - w0) * t) / 2
        if w <= 0.2:
            continue
        draw.ellipse((x - w, y - w, x + w, y + w), fill=(255, 255, 255, alpha))


def glyph(size=128):
    """The formula seal: a broken outer ring with tick marks, an inner ring, three prongs meeting in the
    middle (the kunai's own shape), and eight short brush strokes radiating out like script."""
    big = size * 4
    img = Image.new("RGBA", (big, big))
    d = ImageDraw.Draw(img)
    rng = random.Random(1918)
    c = big / 2
    s = big / 128
    # outer ring: four arcs with gaps, thick dry brush
    for k in range(4):
        a0 = k * 90 + 12
        a1 = a0 + 66
        pts = []
        for i in range(40):
            a = math.radians(a0 + (a1 - a0) * i / 39)
            r = 56 * s + rng.uniform(-0.6, 0.6) * s
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
        for i in range(len(pts) - 1):
            w = (5.5 - 3.0 * abs(i / 38 - 0.5)) * s
            brush_line(d, pts[i], pts[i + 1], w, w, rng, steps=3)
    # tick marks on the gaps (like the edge of a sealing array)
    for k in range(4):
        a = math.radians(k * 90 + 6)
        for r0, r1 in ((60, 50),):
            brush_line(d, (c + math.cos(a) * r0 * s, c + math.sin(a) * r0 * s), (c + math.cos(a) * r1 * s, c + math.sin(a) * r1 * s), 4 * s, 1 * s, rng)
    # inner ring, thin
    for i in range(72):
        a0 = math.radians(i * 5)
        a1 = math.radians(i * 5 + 5)
        r = 30 * s
        brush_line(d, (c + math.cos(a0) * r, c + math.sin(a0) * r), (c + math.cos(a1) * r, c + math.sin(a1) * r), 2.4 * s, 2.4 * s, rng, steps=2)
    # three prongs meeting at the centre, pointing out through the inner ring
    for k in range(3):
        a = math.radians(-90 + k * 120)
        tip = (c + math.cos(a) * 46 * s, c + math.sin(a) * 46 * s)
        brush_line(d, (c, c), tip, 9 * s, 0.5 * s, rng, steps=40)
        for side in (-1, 1):
            b = a + side * math.radians(28)
            prong_tip = (c + math.cos(b) * 26 * s, c + math.sin(b) * 26 * s)
            brush_line(d, (c + math.cos(a) * 8 * s, c + math.sin(a) * 8 * s), prong_tip, 4.5 * s, 0.5 * s, rng, steps=24)
    # eight short script strokes between the rings
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        p0 = (c + math.cos(a) * 36 * s, c + math.sin(a) * 36 * s)
        p1 = (c + math.cos(a + 0.12) * 47 * s, c + math.sin(a + 0.12) * 47 * s)
        brush_line(d, p0, p1, 5 * s, 1.5 * s, rng, steps=16)
    d.ellipse((c - 6 * s, c - 6 * s, c + 6 * s, c + 6 * s), fill=(255, 255, 255, 255))
    img = img.resize((size, size), Image.LANCZOS)
    # a soft halo under the ink so it reads as light when tinted
    halo = img.filter(ImageFilter.GaussianBlur(size / 40))
    out = Image.new("RGBA", (size, size))
    hp = halo.load()
    op = out.load()
    ip = img.load()
    for y in range(size):
        for x in range(size):
            a = max(ip[x, y][3], int(hp[x, y][3] * 0.45))
            op[x, y] = (255, 255, 255, a)
    return out


def crow():
    """Two frames, 64x32 each: wings raised, wings down. Ink black with a single red eye pixel."""
    w, h = 64, 32
    img = Image.new("RGBA", (w * 2, h))
    for frame in range(2):
        big = Image.new("RGBA", (w * 4, h * 4))
        d = ImageDraw.Draw(big)
        ox = 0
        ink = (12, 10, 14, 255)
        cx, cy = 128, 70
        # body and tail
        d.polygon([(cx - 34, cy + 2), (cx + 18, cy - 8), (cx + 34, cy + 2), (cx + 12, cy + 12), (cx - 20, cy + 10)], fill=ink)
        d.polygon([(cx - 34, cy + 2), (cx - 60, cy - 6), (cx - 64, cy + 12), (cx - 30, cy + 10)], fill=ink)  # tail
        d.polygon([(cx + 30, cy - 4), (cx + 52, cy + 2), (cx + 30, cy + 6)], fill=ink)  # beak
        lift = -46 if frame == 0 else 34
        for side in (-1, 1):
            base = (cx - 4 + side * 6, cy - 2)
            tip = (cx - 22 + side * 30, cy + lift)
            mid = (cx + 10 + side * 14, cy + lift * 0.55)
            d.polygon([base, mid, tip, (cx - 28 + side * 8, cy + lift * 0.4), (cx - 14, cy + 2)], fill=ink)
            # ragged primary feathers
            for f in range(5):
                t = f / 4
                fx = tip[0] + (mid[0] - tip[0]) * t
                fy = tip[1] + (mid[1] - tip[1]) * t
                d.polygon([(fx, fy), (fx - 6, fy + (8 if lift < 0 else -8)), (fx + 4, fy + (3 if lift < 0 else -3))], fill=ink)
        d.ellipse((cx + 22, cy - 4, cx + 28, cy + 2), fill=(200, 20, 24, 255))
        box = big.getbbox()
        part = big.crop(box)
        scale = min((w - 2) / part.size[0], (h - 2) / part.size[1])
        small = part.resize((max(1, int(part.size[0] * scale)), max(1, int(part.size[1] * scale))), Image.LANCZOS)
        img.alpha_composite(small, (frame * w + ox + (w - small.size[0]) // 2, (h - small.size[1]) // 2))
    return img


def eye(size=128):
    """A huge eye for an illusion's sky: a sharp leaf-shaped lid, an iris ring and three comma marks."""
    big = size * 4
    img = Image.new("RGBA", (big, big))
    d = ImageDraw.Draw(img)
    c = big / 2
    # the leaf of the lids
    pts = []
    for i in range(61):
        t = i / 60
        x = c - big * 0.46 + big * 0.92 * t
        y = c - math.sin(math.pi * t) ** 0.8 * big * 0.24
        pts.append((x, y))
    for i in range(60, -1, -1):
        t = i / 60
        x = c - big * 0.46 + big * 0.92 * t
        y = c + math.sin(math.pi * t) ** 1.2 * big * 0.17
        pts.append((x, y))
    d.polygon(pts, fill=(255, 255, 255, 110))
    r = big * 0.17
    d.ellipse((c - r, c - r, c + r, c + r), fill=(255, 255, 255, 255))
    d.ellipse((c - r * 0.72, c - r * 0.72, c + r * 0.72, c + r * 0.72), outline=(0, 0, 0, 0), width=int(big * 0.01))
    for k in range(3):
        a = math.radians(-90 + k * 120)
        px, py = c + math.cos(a) * r * 0.55, c + math.sin(a) * r * 0.55
        d.ellipse((px - r * 0.14, py - r * 0.14, px + r * 0.14, py + r * 0.14), fill=(0, 0, 0, 0))
    d.ellipse((c - r * 0.18, c - r * 0.18, c + r * 0.18, c + r * 0.18), fill=(0, 0, 0, 0))
    return img.resize((size, size), Image.LANCZOS)


def scroll():
    """16x16, the shape of the other scrolls: a cream scroll, black rollers, the seal in gold."""
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    ink = (14, 12, 14, 255)
    for y in range(3, 13):
        for x in range(3, 13):
            if x + y < 6 or x + y > 22:
                continue
            p[x, y] = (236, 224, 190, 255) if (x + y) % 5 else (214, 200, 160, 255)
    for i in range(2, 9):
        p[i, 11 - i + 2] = ink
    for (x, y) in ((2, 13), (3, 12), (4, 13), (3, 14), (12, 2), (13, 3), (12, 4), (11, 3), (13, 1), (14, 2)):
        p[x, y] = ink
    gold = (240, 196, 60, 255)
    for (x, y) in ((7, 7), (8, 8), (9, 7), (8, 6), (8, 7), (6, 8), (10, 6), (7, 9), (9, 9)):
        p[x, y] = gold
    for y in range(16):
        for x in range(16):
            if p[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and p[nx, ny][3] and p[nx, ny] != ink:
                        p[x, y] = ink
                        break
    return img


def main():
    images = {
        "misc/spacetime_white.png": save(white(), "misc/spacetime_white.png"),
        "misc/spacetime_glow.png": save(glow(), "misc/spacetime_glow.png"),
        "misc/hiraishin_glyph.png": save(glyph(), "misc/hiraishin_glyph.png"),
        "misc/genjutsu_crow.png": save(crow(), "misc/genjutsu_crow.png"),
        "misc/genjutsu_eye.png": save(eye(), "misc/genjutsu_eye.png"),
        "item/space_time_scroll.png": save(scroll(), "item/space_time_scroll.png"),
    }
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        sheet = Image.new("RGBA", (6 * 260, 260), (40, 36, 44, 255))
        for i, img in enumerate(images.values()):
            scale = 256 / max(img.size)
            im = img.resize((max(1, int(img.size[0] * scale)), max(1, int(img.size[1] * scale))), Image.NEAREST)
            bg = Image.new("RGBA", im.size, (90, 70, 20, 255) if "glyph" in str(i) else (110, 110, 120, 255))
            bg.alpha_composite(im)
            sheet.paste(bg, (i * 260 + 2, 2))
        sheet.save(out)
    print("wrote", len(images), "textures")


if __name__ == "__main__":
    main()

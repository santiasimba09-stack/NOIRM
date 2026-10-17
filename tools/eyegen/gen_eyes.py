"""Draws NOIRM's Mangekyo pattern atlas and the eye item icons (our own designs).

    python3 tools/eyegen/gen_eyes.py

Writes:
  src/main/resources/assets/noirm/textures/entity/mangekyo_patterns.png  (8 cells of 32x32, left to right)
  src/main/resources/assets/noirm/textures/item/sharingan_eye.png         (16x16)
  src/main/resources/assets/noirm/textures/item/mangekyo_eyes.png         (16x16)

Each pattern is a red iris with a black design, drawn supersampled then shrunk so the edges are
smooth. The cell order matches pattern.noirm.mangekyo.<n> in the lang files.
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "noirm", "textures")
S = 256  # supersampled cell size
RED = (205, 18, 26, 255)
DARK_RED = (120, 6, 12, 255)
BLACK = (12, 6, 8, 255)


def polar(cx, cy, r, a):
    return cx + r * math.cos(a), cy + r * math.sin(a)


def iris(draw):
    c = S / 2
    draw.ellipse([2, 2, S - 3, S - 3], fill=BLACK)
    draw.ellipse([10, 10, S - 11, S - 11], fill=RED)
    return c


def pupil(draw, c, r=16):
    draw.ellipse([c - r, c - r, c + r, c + r], fill=BLACK)


def five_petals(draw, c):
    # Five rounded petals with red gaps between them.
    for i in range(5):
        a = i * 2 * math.pi / 5 - math.pi / 2
        px, py = polar(c, c, 62, a)
        draw.ellipse([px - 34, py - 34, px + 34, py + 34], fill=BLACK)
    pupil(draw, c, 22)
    for i in range(5):
        a = i * 2 * math.pi / 5 - math.pi / 2
        px, py = polar(c, c, 66, a)
        draw.ellipse([px - 10, py - 10, px + 10, py + 10], fill=DARK_RED)


def twin_moons(draw, c):
    # Two crescents facing each other around the pupil.
    for side in (0, math.pi):
        ox, oy = polar(c, c, 46, side)
        draw.ellipse([ox - 70, oy - 70, ox + 70, oy + 70], fill=BLACK)
        ix, iy = polar(c, c, 70, side)
        draw.ellipse([ix - 66, iy - 66, ix + 66, iy + 66], fill=RED)
    pupil(draw, c, 20)


def whirlpool(draw, c):
    # Two thin arms spiralling out of the pupil.
    for arm in range(2):
        pts = []
        for t in range(0, 140):
            a = arm * math.pi + t * 0.045
            r = 18 + t * 0.78
            pts.append(polar(c, c, r, a))
        draw.line(pts, fill=BLACK, width=16, joint="curve")
    pupil(draw, c, 22)


def thorn_crown(draw, c):
    # A ring with seven thorns pointing outward.
    draw.ellipse([c - 62, c - 62, c + 62, c + 62], outline=BLACK, width=18)
    for i in range(7):
        a = i * 2 * math.pi / 7
        tip = polar(c, c, 112, a)
        l = polar(c, c, 58, a - 0.22)
        r = polar(c, c, 58, a + 0.22)
        draw.polygon([tip, l, r], fill=BLACK)
    pupil(draw, c)


def lattice(draw, c):
    # A square turned on its corner inside a ring; the corners pierce the ring.
    draw.ellipse([c - 80, c - 80, c + 80, c + 80], outline=BLACK, width=12)
    pts = [polar(c, c, 104, a) for a in (0, math.pi / 2, math.pi, 3 * math.pi / 2)]
    draw.polygon(pts, outline=BLACK, width=14)
    inner = [polar(c, c, 50, a + math.pi / 4) for a in (0, math.pi / 2, math.pi, 3 * math.pi / 2)]
    draw.polygon(inner, fill=BLACK)
    draw.ellipse([c - 14, c - 14, c + 14, c + 14], fill=RED)


def eclipse(draw, c):
    # A black disc with a red crescent cut off-centre, and four dots around it.
    draw.ellipse([c - 72, c - 72, c + 72, c + 72], fill=BLACK)
    draw.ellipse([c - 40, c - 78, c + 92, c + 54], fill=RED)
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        px, py = polar(c, c, 100, a)
        draw.ellipse([px - 11, py - 11, px + 11, py + 11], fill=BLACK)
    pupil(draw, c, 14)


def tide(draw, c):
    # A wavy ring with five lobes.
    pts = []
    for t in range(0, 361, 3):
        a = math.radians(t)
        r = 70 + 20 * math.sin(5 * a)
        pts.append(polar(c, c, r, a))
    draw.line(pts, fill=BLACK, width=18, joint="curve")
    pupil(draw, c, 26)


def broken_halo(draw, c):
    # Three thick arcs with gaps, and three small inner wedges between them.
    for i in range(3):
        start = i * 120 + 15
        draw.arc([c - 92, c - 92, c + 92, c + 92], start, start + 90, fill=BLACK, width=26)
        a = math.radians(start - 15)
        tip = polar(c, c, 70, a)
        l = polar(c, c, 30, a - 0.35)
        r = polar(c, c, 30, a + 0.35)
        draw.polygon([tip, l, r], fill=BLACK)
    pupil(draw, c, 18)


PATTERNS = [five_petals, twin_moons, whirlpool, thorn_crown, lattice, eclipse, tide, broken_halo]


def cell(fn, size):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    c = iris(draw)
    fn(draw, c)
    return img.resize((size, size), Image.LANCZOS)


def atlas():
    out = Image.new("RGBA", (32 * len(PATTERNS), 32), (0, 0, 0, 0))
    for i, fn in enumerate(PATTERNS):
        out.paste(cell(fn, 32), (32 * i, 0))
    out.save(os.path.join(ROOT, "entity", "mangekyo_patterns.png"))


def eye_icon(fn, name, pair):
    big = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    if pair:
        # Two small eyes in a sealed jar (a glass outline).
        d.rounded_rectangle([40, 30, 216, 236], 30, fill=(180, 220, 230, 90), outline=(220, 240, 245, 255), width=10)
        d.rectangle([60, 14, 196, 40], fill=(120, 80, 50, 255))
        for ox, oy in ((96, 104), (160, 168)):
            d.ellipse([ox - 46, oy - 46, ox + 46, oy + 46], fill=(240, 236, 230, 255))
            sub = cell(fn, 72)
            big.alpha_composite(sub, (ox - 36, oy - 36))
    else:
        d.ellipse([28, 28, 228, 228], fill=(240, 236, 230, 255), outline=(90, 40, 40, 255), width=8)
        big.alpha_composite(cell(fn, 150), (53, 53))
    big.resize((16, 16), Image.LANCZOS).save(os.path.join(ROOT, "item", name + ".png"))


def tomoe(draw, c):
    for i in range(3):
        a = i * 2 * math.pi / 3 - math.pi / 2
        px, py = polar(c, c, 66, a)
        draw.ellipse([px - 20, py - 20, px + 20, py + 20], fill=BLACK)
        tail = [polar(px, py, 18, a + 1.4), polar(px, py, 40, a + 2.3), polar(px, py, 18, a + 2.6)]
        draw.polygon(tail, fill=BLACK)
    draw.ellipse([c - 64, c - 64, c + 64, c + 64], outline=DARK_RED, width=4)
    pupil(draw, c, 20)


if __name__ == "__main__":
    atlas()
    eye_icon(tomoe, "sharingan_eye", False)
    eye_icon(thorn_crown, "mangekyo_eyes", True)
    print("wrote the pattern atlas and two item icons")

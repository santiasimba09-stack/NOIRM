"""Draws NOIRM's ninja weapon icons (16x16, vanilla-like palette, original art) into
src/main/resources/assets/noirm/textures/item/. Run from the repository root:

    python3 tools/weapongen/gen_weapons.py [--preview out.png]

Long weapons are drawn along the diagonal (tip to the top right, like a vanilla sword), from a
"profile": segments along the blade, each with a half-width and a material. Stars are polygons.
The thrown-weapon renderer relies on that diagonal to point kunai along their flight.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/noirm/textures/item"
SIZE = 16

# Materials: (light, mid, dark). Vanilla-ish tones.
STEEL = ((0xEE, 0xF0, 0xF2), (0xB5, 0xBA, 0xC0), (0x78, 0x7E, 0x88))
DARK_STEEL = ((0xA8, 0xAE, 0xB6), (0x6E, 0x74, 0x7E), (0x46, 0x4A, 0x52))
CHAKRA = ((0xC8, 0xEC, 0xFF), (0x82, 0xB8, 0xDC), (0x4A, 0x78, 0xA0))
WRAP = ((0x5A, 0x52, 0x72), (0x3A, 0x34, 0x4C), (0x24, 0x20, 0x30))
WOOD = ((0xB0, 0x7C, 0x48), (0x8A, 0x5C, 0x34), (0x62, 0x40, 0x24))
PAPER = ((0xF2, 0xE6, 0xBE), (0xDC, 0xC9, 0x96), (0xB8, 0xA2, 0x70))
GUARD = ((0x5A, 0x4E, 0x3A), (0x3A, 0x32, 0x26), (0x24, 0x1E, 0x16))
BRASS = ((0xE6, 0xC4, 0x5C), (0xC0, 0x98, 0x38), (0x8A, 0x6A, 0x22))
RED = (0xB8, 0x26, 0x1E)
INK = (0x24, 0x1C, 0x1C)
OUTLINE = (0x26, 0x26, 0x2C)


def blank():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def along(img, start, end, segments, stripes=None):
    """Fills a weapon along the line start->end. segments: (t0, t1, w0, w1, material); t from 0
    (start) to 1 (end), w = half-width in pixels. stripes(t, s) may return a colour override."""
    sx, sy = start
    ex, ey = end
    length = math.hypot(ex - sx, ey - sy)
    ux, uy = (ex - sx) / length, (ey - sy) / length
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - sx, y + 0.5 - sy
            t = (dx * ux + dy * uy) / length
            s = dx * -uy + dy * ux  # perpendicular; positive = towards the bottom right
            for t0, t1, w0, w1, material in segments:
                if t0 <= t <= t1:
                    k = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                    w = w0 + (w1 - w0) * k
                    if abs(s) <= w:
                        light, mid, dark = material
                        colour = light if s < -w * 0.25 else dark if s > w * 0.45 else mid
                        if w < 0.75:
                            colour = light if material is not WRAP else mid
                        if stripes:
                            colour = stripes(t, s, material) or colour
                        px[x, y] = colour + (255,)
                    break


def ring(img, cx, cy, r_in, r_out, material):
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r_in <= d <= r_out:
                px[x, y] = (material[0] if x + y < cx + cy else material[2]) + (255,)


def outline(img, colour=OUTLINE):
    """Adds a 1px dark outline around everything drawn (4-neighbourhood)."""
    src = img.copy().load()
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if src[x, y][3]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < SIZE and 0 <= ny < SIZE and src[nx, ny][3]:
                    px[x, y] = colour + (255,)
                    break


DIAG = ((1.6, 14.4), (14.6, 1.4))


def wrap_stripes(t, s, material):
    if material is WRAP and int(t * 40) % 2 == 0:
        return WRAP[0] if s < 0 else WRAP[1]
    return None


def kunai(tag=False):
    img = blank()
    along(img, *DIAG, [
        (0.20, 0.47, 0.75, 0.75, PAPER if tag else WRAP),  # handle (or the tag wrapped round it)
        (0.47, 0.52, 0.8, 1.6, STEEL),                    # blade base
        (0.52, 0.72, 1.6, 1.9, STEEL),                    # the leaf
        (0.72, 1.00, 1.9, 0.0, STEEL),                    # to the point
    ], stripes=None if tag else wrap_stripes)
    ring(img, 2.6, 13.4, 0.9, 1.8, STEEL)
    if tag:
        px = img.load()
        for x, y in ((5, 10), (6, 9), (7, 8)):
            px[x, y] = RED + (255,)
        # the loose end of the tag hanging off
        for x, y, c in ((5, 12, PAPER[1]), (6, 12, PAPER[0]), (6, 13, PAPER[1]), (7, 13, PAPER[2])):
            px[x, y] = c + (255,)
    outline(img)
    return img


def senbon():
    img = blank()
    for offset, t0, t1 in ((-2.2, 0.1, 0.8), (2.2, 0.25, 0.95)):
        start = (DIAG[0][0] + offset * 0.7, DIAG[0][1] + offset * 0.7)
        end = (DIAG[1][0] + offset * 0.7, DIAG[1][1] + offset * 0.7)
        along(img, start, end, [(t0, t1, 0.5, 0.5, STEEL)])
    outline(img)
    return img


def ninjato():
    img = blank()
    along(img, (1.2, 14.8), (14.9, 1.1), [
        (0.02, 0.08, 1.0, 1.0, GUARD),         # pommel
        (0.08, 0.36, 0.8, 0.8, WRAP),          # long wrapped grip
        (0.36, 0.42, 2.4, 2.4, GUARD),         # square guard
        (0.42, 0.88, 1.1, 1.1, STEEL),         # straight blade
        (0.88, 1.00, 1.1, 0.2, STEEL),         # angled tip
    ], stripes=wrap_stripes)
    outline(img)
    return img


def chakra_blade():
    img = blank()
    along(img, *DIAG, [
        (0.12, 0.42, 0.8, 0.8, GUARD),         # grip
        (0.42, 0.47, 1.9, 1.9, DARK_STEEL),    # guard
        (0.47, 0.62, 1.7, 1.9, CHAKRA),
        (0.62, 1.00, 1.9, 0.0, CHAKRA),        # a broad, short blade
    ])
    # the knuckle bow: a bar beside the grip, joined at both ends
    along(img, (3.6, 15.6), (8.9, 10.3), [(0.0, 1.0, 0.5, 0.5, DARK_STEEL)])
    outline(img)
    return img


def bo_staff():
    img = blank()

    def bands(t, s, material):
        return WOOD[0] if material is WOOD and 0.45 < t < 0.55 else None  # a lighter grip in the middle
    along(img, (0.6, 15.4), (15.4, 0.6), [
        (0.0, 0.12, 1.1, 1.1, STEEL),
        (0.12, 0.88, 0.8, 0.8, WOOD),
        (0.88, 1.0, 1.1, 1.1, STEEL),
    ], stripes=bands)
    outline(img)
    return img


def star(points, outer, inner, rotation, centre=(8, 8)):
    pts = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        a = rotation + i * math.pi / points
        pts.append((centre[0] + r * math.cos(a), centre[1] + r * math.sin(a)))
    return pts


def shade_by_side(img, material, centre=(8, 8)):
    """Lights the top-left half of every blade, darkens the bottom-right one."""
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3]:
                dx, dy = x + 0.5 - centre[0], y + 0.5 - centre[1]
                side = -dx - dy
                px[x, y] = (material[0] if side > 1.5 else material[2] if side < -1.5 else material[1]) + (255,)


def shuriken():
    img = blank()
    ImageDraw.Draw(img).polygon(star(4, 7.2, 1.7, math.pi / 4), fill=STEEL[1] + (255,))
    shade_by_side(img, STEEL)
    px = img.load()
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = (0, 0, 0, 0)  # the hole in the middle
    outline(img)
    for x, y in ((7, 7), (8, 8)):
        px[x, y] = OUTLINE + (255,)
    return img


def fuma_shuriken():
    img = blank()
    pts = []
    for blade in range(4):
        a = blade * math.pi / 2 + math.pi / 4
        pts += [(8 + 2.6 * math.cos(a - 0.5), 8 + 2.6 * math.sin(a - 0.5)),
                (8 + 7.6 * math.cos(a + 0.15), 8 + 7.6 * math.sin(a + 0.15)),  # swept tip
                (8 + 4.2 * math.cos(a + 0.6), 8 + 4.2 * math.sin(a + 0.6))]
    ImageDraw.Draw(img).polygon(pts, fill=DARK_STEEL[1] + (255,))
    shade_by_side(img, DARK_STEEL)
    ring(img, 8, 8, 0.0, 2.3, BRASS)  # the hub it folds around
    px = img.load()
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = OUTLINE + (255,)
    outline(img)
    return img


def explosive_tag():
    img = blank()
    px = img.load()
    for y in range(1, 15):
        for x in range(5, 11):
            px[x, y] = (PAPER[0] if x < 8 else PAPER[1]) + (255,)
    for y in range(2, 14):  # a red seal line down the middle…
        px[7, y] = RED + (255,)
    for x, y in ((6, 3), (8, 3), (6, 4), (9, 6), (6, 8), (8, 9), (9, 9), (6, 11), (8, 12)):  # …and ink marks
        px[x, y] = INK + (255,)
    for x in range(5, 11):  # a darker fold at the top
        px[x, 1] = PAPER[2] + (255,)
    outline(img)
    return img


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
}


def main():
    os.makedirs(OUT, exist_ok=True)
    images = {}
    for name, draw in ICONS.items():
        images[name] = draw()
        images[name].save(os.path.join(OUT, name + ".png"))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        sheet = Image.new("RGBA", (len(images) * 18 * 8, 18 * 8), (0x8B, 0x8B, 0x8B, 255))
        for i, img in enumerate(images.values()):
            sheet.alpha_composite(img.resize((16 * 8, 16 * 8), Image.NEAREST), (i * 18 * 8 + 8, 8))
        sheet.save(out)
    print("wrote", len(images), "icons to", OUT)


if __name__ == "__main__":
    main()

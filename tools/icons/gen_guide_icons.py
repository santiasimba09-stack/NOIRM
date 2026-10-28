#!/usr/bin/env python3
"""Item icons for the new-player guide and the medical ninja (16x16, our own pixel art).

Writes src/main/resources/assets/noirm/textures/item/:
    shinobi_handbook.png   an ink-black bound book, bone-white page edges, a blood-red seal with a
                           pale stroke on the cover and a red cord down the spine
    antidote.png           a small stoppered glass vial of green-teal medicine with a paper label

Run from the repo root:  python3 tools/icons/gen_guide_icons.py
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/item")

INK = (20, 17, 24, 255)
INK_HI = (54, 46, 62, 255)
INK_MID = (34, 29, 40, 255)
BONE = (226, 216, 196, 255)
BONE_DIM = (176, 164, 144, 255)
BLOOD = (160, 32, 28, 255)
BLOOD_DARK = (96, 16, 14, 255)
PALE = (240, 228, 210, 255)


def handbook():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    # The page block (seen at the right and bottom edges), then the cover over it.
    for y in range(3, 15):
        for x in range(4, 15):
            p[x, y] = BONE_DIM if (x + y) % 3 == 0 else BONE
    for y in range(2, 14):
        for x in range(3, 13):
            p[x, y] = INK_MID
    # Cover edges: lit top/left, dark rim.
    for x in range(3, 13):
        p[x, 2] = INK_HI
        p[x, 13] = INK
    for y in range(2, 14):
        p[3, y] = INK_HI
        p[12, y] = INK
    # Spine with a red cord.
    for y in range(2, 14):
        p[2, y] = INK
        if y % 3 != 1:
            p[4, y] = BLOOD_DARK
    # The seal: a red square with a pale brush stroke.
    for y in range(5, 10):
        for x in range(6, 11):
            p[x, y] = BLOOD
    for x in range(6, 11):
        p[x, 9] = BLOOD_DARK
    for y in range(5, 10):
        p[10, y] = BLOOD_DARK
    p[7, 6] = PALE
    p[8, 7] = PALE
    p[9, 6] = PALE
    p[8, 8] = PALE
    # Scratches on the leather.
    p[5, 11] = INK_HI
    p[9, 12] = INK_HI
    p[11, 4] = INK_HI
    return img


def antidote():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    glass = (190, 220, 225, 200)
    glass_dark = (90, 120, 130, 230)
    liquid = (60, 190, 150, 255)
    liquid_dark = (30, 120, 96, 255)
    liquid_hi = (150, 240, 205, 255)
    cork = (122, 84, 52, 255)
    cork_dark = (82, 54, 32, 255)
    # Cork.
    for y in range(1, 4):
        for x in range(6, 10):
            p[x, y] = cork if x < 9 else cork_dark
    # Neck.
    for y in range(4, 6):
        for x in range(6, 10):
            p[x, y] = glass if x != 9 else glass_dark
    # Body.
    for y in range(6, 15):
        for x in range(4, 12):
            edge = x in (4, 11) or y == 14
            p[x, y] = glass_dark if edge else liquid if y > 7 else glass
    for y in range(8, 14):
        p[10, y] = liquid_dark
        p[5, y] = liquid_hi if y < 11 else liquid
    # Paper label with an ink cross.
    for y in range(9, 12):
        for x in range(6, 10):
            p[x, y] = BONE
    p[7, 10] = BLOOD
    p[8, 10] = BLOOD
    p[7, 9] = BLOOD_DARK
    p[8, 11] = BLOOD_DARK
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    handbook().save(os.path.join(OUT, "shinobi_handbook.png"))
    antidote().save(os.path.join(OUT, "antidote.png"))
    print("wrote shinobi_handbook.png and antidote.png to", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()

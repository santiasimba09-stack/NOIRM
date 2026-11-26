#!/usr/bin/env python3
"""Item icons for the training wave (16x16, our own pixel art).

Writes src/main/resources/assets/noirm/textures/item/:
    training_weights.png   a pair of dull-grey weight bands strapped with olive cloth, the kind worn on the shins

Run from the repo root:  python3 tools/icons/gen_training_icons.py
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/item")

IRON = (118, 122, 128, 255)
IRON_HI = (170, 174, 180, 255)
IRON_DARK = (62, 64, 70, 255)
CLOTH = (96, 104, 52, 255)
CLOTH_DARK = (60, 66, 32, 255)


def band(p, x0, y0):
    """One weight band: a 5-wide stack of iron blocks on an olive strap."""
    for y in range(y0, y0 + 9):
        p[x0 + 2, y] = CLOTH_DARK if y % 2 else CLOTH
    for y in range(y0 + 1, y0 + 8):
        for x in range(x0, x0 + 5):
            edge = x in (x0, x0 + 4) or y in (y0 + 1, y0 + 7)
            p[x, y] = IRON_DARK if edge else (IRON_HI if x == x0 + 1 else IRON)
        if (y - y0) % 2 == 0 and y not in (y0 + 1, y0 + 7):
            for x in range(x0 + 1, x0 + 4):
                p[x, y] = IRON_DARK  # the seams between the iron plates


def weights():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    band(p, 2, 3)
    band(p, 9, 5)
    for x in range(3, 13):  # a cloth tie joining the pair
        p[x, 13] = CLOTH if x % 3 else CLOTH_DARK
    img.save(os.path.join(OUT, "training_weights.png"))


if __name__ == "__main__":
    weights()
    print("wrote training_weights.png")

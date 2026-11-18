#!/usr/bin/env python3
"""Draws NOIRM's status effect icons (18x18, our own art):

- chakra_seal: a sealing formula, a gold ring with a spiral and four ward strokes on ink.

Run from the repository root: python3 tools/icons/gen_effect_icons.py
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src/main/resources/assets/noirm/textures/mob_effect")


def chakra_seal():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = img.load()
    gold = (226, 182, 82, 255)
    dark = (120, 84, 30, 255)
    ink = (28, 22, 18, 220)
    cx = cy = 8.5
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - cx, y - cy)
            if d <= 8.2:
                px[x, y] = ink
            if 6.6 <= d <= 8.0:
                px[x, y] = gold if (x + y) % 5 else dark
    # the spiral (a whirlpool seal)
    for i in range(160):
        t = i / 160 * 3.2 * math.pi
        r = 0.6 + t * 0.55
        x = int(round(cx + math.cos(t) * r))
        y = int(round(cy + math.sin(t) * r))
        if 0 <= x < 18 and 0 <= y < 18 and math.hypot(x - cx, y - cy) < 6.4:
            px[x, y] = gold
    # four ward strokes
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        for k in (7, 8):
            x, y = int(cx + dx * k), int(cy + dy * k)
            if 0 <= x < 18 and 0 <= y < 18:
                px[x, y] = (255, 230, 150, 255)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    chakra_seal().save(os.path.join(OUT, "chakra_seal.png"))
    print("wrote chakra_seal.png")


if __name__ == "__main__":
    main()

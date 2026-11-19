#!/usr/bin/env python3
"""The Inuzuka ninja hound's fur and items (docs/INUZUKA.md, "How it's drawn").

Writes
    assets/noirm/hound/atlas.json
        where each box role's six faces sit on the fur atlas (see atlas.py).
    assets/noirm/textures/entity/hound/
        fur_young.png, fur_old.png, details_young.png, details_old.png,
        pattern_<saddle|mask|socks|spotted|brindle>.png, marks_<blaze|chest|tail_tip|brows>.png
        the layers the game mixes into each hound's coat (paint.py says what each channel means).
    assets/noirm/textures/item/ninken_contract.png, hound_pill.png   the two hound items
    assets/noirm/player_animation/fang_drill.json   your body spinning like a drill (Fang Over Fang)

The model itself is built in the game from the breed recipes in assets/noirm/hound/breeds/ and the
hound's level (client/hound/HoundRigBuilder), so there are no model files to write here.

Run from the repo root:   python3 tools/houndgen/gen_hound.py [--swatches out.png]
Needs numpy and Pillow. All art is drawn by this code; nothing is traced or copied.
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import atlas  # noqa: E402
from paint import Layers  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")
OUT = os.path.join(ASSETS, "textures/entity/hound")

PATTERNS = ["saddle", "mask", "socks", "spotted", "brindle"]
MARKS = ["blaze", "chest", "tail_tip", "brows"]


def save_png(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray((np.clip(arr, 0, 1) * 255).round().astype(np.uint8), "RGBA").save(path, optimize=True)


def layers():
    lay = Layers()
    atlas.write(os.path.join(ASSETS, "hound/atlas.json"))
    out = {}
    for age, name in ((0.0, "young"), (1.0, "old")):
        out["fur_" + name] = lay.fur(age)
        out["details_" + name] = lay.details(age)
    for p in PATTERNS:
        out["pattern_" + p] = lay.pattern(p)
    for m in MARKS:
        out["marks_" + m] = lay.marks(m)
    for name, img in out.items():
        save_png(img, os.path.join(OUT, name + ".png"))
    return out


# ---------------------------------------------------------------------- the same mix as the game (for swatches)

def compose(L, base, second, eye, pattern=None, marks=(), age=0.5):
    """The game's coat mix (client/hound/HoundTextures), in numpy: for checking the layers by eye."""
    def rgb(h):
        return np.array([(h >> 16 & 255) / 255, (h >> 8 & 255) / 255, (h & 255) / 255])
    fur = L["fur_young"] * (1 - age) + L["fur_old"] * age
    shade = fur[..., 0] * 1.4
    counter = fur[..., 1]
    b = rgb(base)
    col = np.broadcast_to(b, fur.shape[:2] + (3,)).copy()
    light = b * 0.55 + rgb(0xF0E8D8) * 0.45
    col = col * (1 - counter[..., None] * 0.8) + light * counter[..., None] * 0.8
    if pattern:
        p = L["pattern_" + pattern]
        col = col * (1 - p[..., 0:1]) + rgb(second) * p[..., 0:1]
        white = p[..., 1]
    else:
        white = np.zeros(fur.shape[:2])
    for m in marks:
        white = np.maximum(white, L["marks_" + m][..., 1])
    col = col * (1 - white[..., None]) + rgb(0xEEEAE2) * white[..., None]
    col = col * shade[..., None]
    d = L["details_old"] if age > 0.5 else L["details_young"]
    a = d[..., 3:4]
    iris = np.abs(d[..., 3] - 254 / 255) < 0.002
    blend = np.where(iris[..., None], rgb(eye) * d[..., 0:1] * 1.2, d[..., :3])
    col = col * (1 - np.where(iris[..., None], 1, a)) + blend * np.where(iris[..., None], 1, a)
    out = np.concatenate([np.clip(col, 0, 1), fur[..., 3:4]], axis=-1)
    return out


def swatches(L, path):
    looks = [(0xE8E4DA, 0xC9A86C, 0xC48A2A, None, ("chest",), 0.0), (0x8F8B86, 0x3E3C3A, 0xC48A2A, "saddle", (), 1.0),
             (0xB98A55, 0x242221, 0x5A3418, "mask", ("blaze",), 0.6), (0x242221, 0xA8743E, 0xD8B040, "socks", ("brows",), 1.0),
             (0xE0CFA8, 0x6A4428, 0x8CB8D0, "spotted", ("tail_tip",), 0.3), (0xB98A55, 0x3E3C3A, 0x5A3418, "brindle", (), 0.8)]
    tiles = []
    for base, second, eye, pat, marks, age in looks:
        img = compose(L, base, second, eye, pat, marks, age)
        bg = np.ones_like(img[..., :3]) * 0.2
        tiles.append(img[..., :3] * img[..., 3:4] + bg * (1 - img[..., 3:4]))
    sheet = np.concatenate([np.concatenate(tiles[:3], axis=1), np.concatenate(tiles[3:], axis=1)], axis=0)
    Image.fromarray((sheet * 255).astype(np.uint8)).resize((sheet.shape[1] * 2, sheet.shape[0] * 2), Image.NEAREST).save(path)
    print("swatches:", path)


def item_icons():
    s = 16
    # The ninken summoning contract: a rolled scroll with a red cord and a paw print.
    a = np.zeros((s, s, 4))
    paper, shade, ink, red = (np.array(x) / 255 for x in ([226, 214, 186], [168, 150, 120], [24, 20, 18], [150, 26, 22]))
    for y in range(s):
        for x in range(s):
            # A diagonal roll from bottom-left to top-right.
            d = (x - y)
            if abs(d) <= 3 and 2 <= x + y <= 27:
                t = (d + 3) / 6
                a[y, x, :3] = paper * (1 - 0.35 * abs(t - 0.4))
                a[y, x, 3] = 1
                if abs(d) == 3:
                    a[y, x, :3] = shade
            if (x + y in (8, 9, 20, 21)) and abs(d) <= 4:
                a[y, x, :3] = red
                a[y, x, 3] = 1
    for (x, y) in ((7, 8), (8, 7), (9, 8), (8, 9), (10, 10), (6, 10), (9, 11), (7, 11)):
        if a[y, x, 3] > 0:
            a[y, x, :3] = ink
    for (x, y) in ((1, 14), (2, 13), (14, 1), (13, 2)):
        a[y, x, :3] = ink
        a[y, x, 3] = 1
    save_png((a * 255).astype(np.uint8), os.path.join(ASSETS, "textures/item/ninken_contract.png"))
    # The hound ration pill: a small blood-red pill on a scrap of paper.
    b = np.zeros((s, s, 4))
    for y in range(s):
        for x in range(s):
            if 3 <= x <= 12 and 9 <= y <= 13:
                b[y, x, :3] = paper * (0.9 if (x + y) % 3 else 0.8)
                b[y, x, 3] = 1
            dx, dy = (x - 7.5) / 3.6, (y - 7.5) / 3.2
            if dx * dx + dy * dy <= 1:
                b[y, x, :3] = red * (1.15 - 0.4 * (dy + 1) / 2)
                b[y, x, 3] = 1
                if dx * dx + dy * dy > 0.7:
                    b[y, x, :3] = red * 0.45
    b[5, 6, :3] = [1, 0.85, 0.8]
    b[5, 7, :3] = [1, 0.75, 0.7]
    save_png((b * 255).astype(np.uint8), os.path.join(ASSETS, "textures/item/hound_pill.png"))


# ---------------------------------------------------------------------- the drill spin (player)

def fang_drill():
    """Your body tips forward flat and spins round its own length: a drill (Fang Over Fang)."""
    length = 1.0
    rot = {}
    pos = {}
    steps = 20
    for i in range(steps + 1):
        t = round(length * i / steps, 3)
        ease = "linear"
        pitch = -90.0 if 0 < i < steps else (0.0 if i == steps else -60.0)
        roll = 0.0 if i == 0 else (360.0 * 6 * i / steps if i < steps else 360.0 * 6)
        rot[str(t)] = {"vector": [pitch, 0.0, roll], "easing": ease}
        pos[str(t)] = {"vector": [0.0, -2.0 if 0 < i < steps else 0.0, 0.0], "easing": ease}
    tuck = {"rotation": {"0.0": {"vector": [-160.0, 0.0, 0.0], "easing": "linear"},
                         str(length): {"vector": [-160.0, 0.0, 0.0], "easing": "linear"}}}
    legs = {"rotation": {"0.0": {"vector": [-20.0, 0.0, 0.0], "easing": "linear"},
                         str(length): {"vector": [-20.0, 0.0, 0.0], "easing": "linear"}}}
    anim = {"format_version": "1.8.0", "animations": {"fang_drill": {
        "animation_length": length,
        "bones": {"body": {"rotation": rot, "position": pos},
                  "right_arm": tuck, "left_arm": tuck, "right_leg": legs, "left_leg": legs}}}}
    path = os.path.join(ASSETS, "player_animation/fang_drill.json")
    with open(path, "w") as f:
        json.dump(anim, f, indent=1)


# ---------------------------------------------------------------------- previews


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--swatches")
    args = ap.parse_args()
    L = layers()
    item_icons()
    fang_drill()
    if args.swatches:
        swatches(L, args.swatches)
    print("wrote the hound atlas layers to", OUT)


if __name__ == "__main__":
    main()

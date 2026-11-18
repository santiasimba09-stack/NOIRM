#!/usr/bin/env python3
"""The Inuzuka ninja hound's models and fur (docs/INUZUKA.md).

Writes
    assets/noirm/hound/geometry.json
        every body plan x growth stage (and the Two-Headed Wolf) as named, jointed boxes in
        Minecraft model space, with each stage's render scale. The game builds its models from
        this file (client/HoundModel) and animates the parts by name.
    assets/noirm/textures/entity/hound/<plan>/<stage>_<coat>.png
        the fur, painted onto each model's packed box UVs at 4 texels per model pixel.
    assets/noirm/textures/item/ninken_contract.png, hound_pill.png   the two hound items
    assets/noirm/player_animation/fang_drill.json   your body spinning like a drill (Fang Over Fang)

Run from the repo root:   python3 tools/houndgen/gen_hound.py [--preview out.png] [--only lean]
Needs numpy and Pillow. All art is drawn by this code; nothing is traced or copied.
"""
import argparse
import json
import zlib
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rig as R  # noqa: E402
from paint import COATS, Painter  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")

# The body plan the game uses (the others stay in the file for comparison: /summon with {Build:n}).
CHOSEN = "lean"
PLANS = ["lean", "classic", "heavy"]  # index = the entity's Build number


def make(variant, stage, two_heads=False):
    recipe = R.stage_recipe(variant, stage)
    rg = R.build(recipe, two_heads=two_heads)
    name = variant + "/" + ("fused" if two_heads else stage)
    tex_w = 64 if stage in ("puppy", "young") else 128
    geom, (tw, th) = R.export(rg, name, tex_w)
    shoulder = R.shoulder_px(rg)
    blocks = R.SHOULDER_BLOCKS[stage] * (1.55 if two_heads else 1.0)
    geom["scale"] = round(blocks * 16.0 / shoulder, 4)
    return geom


def paint(geom, coat, stage, seed):
    tw, th = geom["texture"]
    p = Painter(tw, th, COATS[coat], stage, seed)
    for part in geom["parts"]:
        for c in part["cubes"]:
            p.paint_cube(c)
    return (np.clip(p.img, 0, 1) * 255).astype(np.uint8)


def save_png(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(arr, "RGBA").save(path, optimize=True)


def strip_roles(geom):
    """The game file keeps the role (it names what a box is; handy when reading the file)."""
    return geom


# ---------------------------------------------------------------------- items

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

def preview(path, only):
    from preview import render
    rows = []
    plans = [only] if only else PLANS
    for plan in plans:
        tiles = []
        for stage in R.STAGES:
            geom = make(plan, stage)
            tex = paint(geom, "white" if plan == CHOSEN else "grey", stage, 7)
            img = render(geom, tex, 60, 12, (220, 180), extent=1.65, scale=geom["scale"] / 1.0,
                         centre=np.array([0, 1.25, 0]))
            side = render(geom, tex, 90, 5, (220, 180), extent=1.65, scale=geom["scale"],
                          centre=np.array([0, 1.25, 0]))
            tiles.append(np.concatenate([img, side], axis=0))
        rows.append(np.concatenate(tiles, axis=1))
    # Close-ups of the chosen plan's grown head and the puppy.
    sheet = np.concatenate(rows, axis=0)
    Image.fromarray(sheet).save(path)
    print("preview:", path)


def closeups(path, plan, coat):
    from preview import render
    tiles = []
    for stage in R.STAGES:
        geom = make(plan, stage)
        tex = paint(geom, coat, stage, 7)
        tiles.append(np.concatenate([render(geom, tex, 40, 15, (260, 220)), render(geom, tex, 160, 10, (260, 220)),
                                     render(geom, tex, 180, 5, (260, 220))], axis=0))
    geom = make(plan, "great", two_heads=True)
    tex = paint(geom, coat, "great", 7)
    tiles.append(np.concatenate([render(geom, tex, 40, 15, (260, 220)), render(geom, tex, 160, 10, (260, 220)),
                                 render(geom, tex, 180, 5, (260, 220))], axis=0))
    Image.fromarray(np.concatenate(tiles, axis=1)).save(path)
    print("closeups:", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--closeups")
    ap.add_argument("--coat", default="white")
    ap.add_argument("--only")
    ap.add_argument("--no-write", action="store_true")
    args = ap.parse_args()
    if args.preview:
        preview(args.preview, args.only)
    if args.closeups:
        closeups(args.closeups, args.only or CHOSEN, args.coat)
    if args.no_write:
        return
    out = {"chosen": CHOSEN, "plans": PLANS, "stages": R.STAGES, "coats": list(COATS), "models": {}}
    for plan in PLANS:
        for stage in R.STAGES:
            geom = make(plan, stage)
            out["models"][plan + "/" + stage] = geom
            # Every coat for the chosen plan; the others only in white (for the comparison).
            for coat in (COATS if plan == CHOSEN else ["white"]):
                save_png(paint(geom, coat, stage, zlib.crc32((plan + stage + coat).encode()) & 0xffff),
                         os.path.join(ASSETS, "textures/entity/hound", plan, stage + "_" + coat + ".png"))
        if plan == CHOSEN:
            geom = make(plan, "great", two_heads=True)
            out["models"][plan + "/fused"] = geom
            save_png(paint(geom, "white", "giant", 99), os.path.join(ASSETS, "textures/entity/hound", plan, "fused.png"))
    path = os.path.join(ASSETS, "hound/geometry.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(out, f, separators=(",", ":"))
    item_icons()
    fang_drill()
    print("wrote", path)


if __name__ == "__main__":
    main()

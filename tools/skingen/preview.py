#!/usr/bin/env python3
"""Previews composed skins as a flat 'paper doll' (front, back and a face close-up).

    python3 tools/skingen/preview.py npc <clan>:<hexseed> [...] -o out.png
    python3 tools/skingen/preview.py player [part=tint ...] -o out.png

Only for checking the art while drawing it; the game composes skins in Java (client/SkinComposer).
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import looks as L  # noqa: E402
from pixlib import SKIN_PARTS, faces  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
NINJA = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/ninja/parts")
PLAYER = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/player_parts")


def face_img(skin, box, face):
    s = skin.shape[0] // 64
    x, y, w, h = faces(SKIN_PARTS[box])[face]
    return skin[y * s:(y + h) * s, x * s:(x + w) * s]


def paste(canvas, img, x, y):
    h, w = img.shape[:2]
    a = img[..., 3:4] / 255.0
    canvas[y:y + h, x:x + w, :3] = img[..., :3] * a + canvas[y:y + h, x:x + w, :3] * (1 - a)
    canvas[y:y + h, x:x + w, 3] = np.maximum(canvas[y:y + h, x:x + w, 3], img[..., 3])


def doll(skin, face="front"):
    """Front (or back) view: head, body, arms and legs, inner layer then outer layer."""
    s = skin.shape[0] // 64
    W, H = 16 * s, 32 * s
    out = np.zeros((H + 2 * s, W + 2 * s, 4), np.float32)
    out[..., :3] = 70
    out[..., 3] = 255
    o = s
    flip = face == "back"
    parts = [("head", "hat", 4, 0), ("body", "jacket", 4, 8), ("rarm", "rsleeve", 0, 8), ("larm", "lsleeve", 12, 8),
             ("rleg", "rpants", 4, 20), ("lleg", "lpants", 8, 20)]
    for inner, outer, px, py in parts:
        if flip:
            w = SKIN_PARTS[inner][2]
            px = 16 - px - w
        for box in (inner, outer):
            paste(out, face_img(skin, box, face), o + px * s, o + py * s)
    return out


def sheet(skins, zoom=3):
    tiles = []
    for skin in skins:
        f = doll(skin, "front")
        b = doll(skin, "back")
        s = skin.shape[0] // 64
        head = np.zeros((8 * s, 8 * s, 4), np.float32)
        head[..., :3] = 70
        head[..., 3] = 255
        paste(head, face_img(skin, "head", "front"), 0, 0)
        paste(head, face_img(skin, "hat", "front"), 0, 0)
        side = np.zeros((8 * s, 8 * s, 4), np.float32)
        side[..., :3] = 70
        side[..., 3] = 255
        paste(side, face_img(skin, "head", "right"), 0, 0)
        paste(side, face_img(skin, "hat", "right"), 0, 0)
        headz = np.kron(head, np.ones((2, 2, 1)))
        sidez = np.kron(side, np.ones((2, 2, 1)))
        col = np.zeros((f.shape[0], f.shape[1] * 2 + headz.shape[1] + 4, 4), np.float32)
        col[..., :3] = 40
        col[..., 3] = 255
        col[:, :f.shape[1]] = f
        col[:, f.shape[1]:2 * f.shape[1]] = b
        col[:headz.shape[0], 2 * f.shape[1] + 4:] = headz
        col[headz.shape[0]:headz.shape[0] + sidez.shape[0], 2 * f.shape[1] + 4:] = sidez
        tiles.append(col)
    img = np.concatenate(tiles, 0)
    img = np.kron(img, np.ones((zoom, zoom, 1)))
    return Image.fromarray(img.astype(np.uint8), "RGBA")


def npc_skin(key, looks):
    return L.compose_array(L.decode(key, looks), looks, NINJA).astype(np.float32)


def parts_skin(spec):
    out = None
    for item in spec.split(","):
        where, _, rest = item.partition(":")
        name, _, colour = rest.partition("=")
        path = os.path.join(NINJA if where == "ninja" else PLAYER, name + ".png")
        p = np.array(Image.open(path).convert("RGBA"), np.float32)
        if out is None:
            out = np.zeros_like(p)
        if colour:
            c = np.array(L.hx(colour), np.float32)
            k = (p[..., 0:1] - 128) / 40.0
            rgb = np.where(k >= 0, np.minimum(255, c * (1 + 0.13 * k) + 14 * k), np.maximum(0, c * (1 + 0.2 * k)))
        else:
            rgb = p[..., :3]
        sa = p[..., 3:4] / 255.0
        da = out[..., 3:4] / 255.0
        oa = sa + da * (1 - sa)
        safe = np.where(oa > 0, oa, 1)
        out[..., :3] = np.where(oa > 0, (rgb * sa + out[..., :3] * da * (1 - sa)) / safe, out[..., :3])
        out[..., 3:4] = oa * 255
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("kind", choices=["npc", "parts"])
    ap.add_argument("keys", nargs="+")
    ap.add_argument("-o", "--out", required=True)
    ap.add_argument("--zoom", type=int, default=2)
    a = ap.parse_args()
    looks = L.load_looks(NINJA)
    if a.kind == "npc":
        skins = [npc_skin(k, looks) for k in a.keys]
    else:
        # one skin per argument: comma-separated layers, each "ninja:<part>[=#rrggbb]" or "player:<part>[=#rrggbb]"
        skins = [parts_skin(k) for k in a.keys]
    sheet(skins, a.zoom).save(a.out)
    print("wrote", a.out)


if __name__ == "__main__":
    main()

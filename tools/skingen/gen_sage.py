#!/usr/bin/env python3
"""The sage path's skin overlays for NOIRM, in HD (hdskin.SCALE x the 64x64 layout), our own art.

Writes src/main/resources/assets/noirm/textures/entity/sage/:
    sage_eyes_<style>.png   Sage Mode, one per eye style (so it fits that style's lids): pigment
                            marks in blood-ochre ink around the eyes (a wing flicking out from the
                            upper lid, a stroke under the lower lid, the lids stained dark) and sage
                            eyes (an amber iris with a dark ring and a flat, horizontal bar pupil).
    petrify_<1-4>.png       Petrification creeping over the body as raw grey stone with ink
                            cracks: 1 hands and feet, 2 the limbs and belly, 3 all but the face,
                            4 a statue (everything).

client/SageLayer draws them over the player. Run from the repo root:  python3 tools/skingen/gen_sage.py
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anatomy as A  # noqa: E402
import gen_parts as G  # noqa: E402
from hdskin import Part, paint, ell, ell_d, box_cov, stroke, vnoise, grain, hx, SCALE  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/sage")

PIGMENT = np.array(hx("#8a2a1a"), np.float32)       # blood ochre
PIGMENT_DARK = np.array(hx("#3a120c"), np.float32)
PIGMENT_LIGHT = np.array(hx("#b2462a"), np.float32)
IRIS = np.array(hx("#c99a2e"), np.float32)
IRIS_LIGHT = np.array(hx("#ecc95e"), np.float32)
IRIS_RING = np.array(hx("#4a3008"), np.float32)
PUPIL = np.array(hx("#070507"), np.float32)

STONE = np.array(hx("#8c8a84"), np.float32)
STONE_DARK = np.array(hx("#5e5c57"), np.float32)
STONE_LIGHT = np.array(hx("#c8c4b8"), np.float32)
CRACK = np.array(hx("#24221f"), np.float32)


def put(rgb, a, cov, colour):
    cov = np.asarray(cov, np.float32)
    rgb = np.where((cov > 0)[..., None], colour if np.ndim(colour) == 1 else colour, rgb)
    return rgb, np.maximum(a, cov)


# ---------------------------------------------------------------------------
# Sage Mode eyes
# ---------------------------------------------------------------------------

def part_sage_eyes(style):
    s = A.EYE_SHAPES[style]
    p = Part(tint=False)

    def fn(f):
        rgb = np.zeros(f.X.shape + (3,), np.float32)
        a = f.zeros()
        X, Y = f.X, f.Y
        g = grain(f, 41)
        for cx in A.EYE_XS:
            dirx = -1 if cx < 4 else 1                         # towards this eye's outer corner
            inside = A.eye_inside(f, cx, s)
            top, bottom = A.lid_curves(X, cx, s)
            up = G.lid_points(cx, s, "top", n=40)
            ox, oy = up[-1]
            # The lids stained dark with pigment (a band over the upper lid up to the brow's shadow).
            u = A.across_u(X, cx, s)
            band = ((top - Y) > 0) & ((top - Y) < s["hood"] + 0.35) & (u > -0.05) & (u < 1.15)
            stain = A.crisp(band.astype(np.float32)) * (1 - inside) * (g > 0.12)
            rgb, a = put(rgb, a, stain * 0.9, PIGMENT_DARK)
            # The wing: a heavy ochre stroke flicking out and up from the outer corner.
            wing = stroke(f, [(ox - dirx * 0.3, oy - 0.25), (ox + dirx * 0.7, oy - 0.55), (ox + dirx * 1.45, oy - 0.3)], 0.75, 0.25)
            # Under the eye: a stroke along the lower lid's outer half, out to the temple.
            lo = G.lid_points(cx, s, "bottom", n=24)[10:]
            lower = stroke(f, [(x, y + 0.38) for x, y in lo] + [(ox + dirx * 1.2, oy + 0.6)], 0.45, 0.65)
            marks = A.crisp(np.maximum(wing, lower)) * (1 - inside)
            ragged = marks * ((g > 0.08) | (marks > 0))  # dry-brush gaps at the tips
            light_edge = ragged * (vnoise(X * 6, Y * 6, 7) > 0.62)
            rgb, a = put(rgb, a, ragged, PIGMENT)
            rgb = np.where((light_edge > 0)[..., None], PIGMENT_LIGHT, rgb)
            dark_edge = A.crisp(np.maximum(stroke(f, [(ox + dirx * 0.2, oy - 0.3), (ox + dirx * 1.3, oy - 0.36)], 0.12, 0.08),
                                           0)) * (1 - inside)
            rgb, a = put(rgb, a, dark_edge, PIGMENT_DARK)
            # The sage eye: amber iris, dark ring, lighter lower half, a flat bar pupil.
            vis = G.visible_iris(f, cx, s)
            ix, iy = A.iris_centre(cx, s)
            rx, ry = A.iris_radii(s)
            d = ell_d(f, ix, iy, rx, ry)
            v = (Y - iy) / ry
            col = np.broadcast_to(IRIS, X.shape + (3,)).copy()
            col = np.where((v > 0.15)[..., None], IRIS_LIGHT, col)
            col = np.where((d > 0.8)[..., None], IRIS_RING, col)
            col = np.where((Y - top < 0.3)[..., None], IRIS_RING, col)
            bar = A.crisp(box_cov(f, ix - rx * 0.62, iy - 0.125, ix + rx * 0.62, iy + 0.125))
            col = np.where((bar > 0)[..., None], PUPIL, col)
            shine = A.crisp(box_cov(f, ix - rx * 0.5 - 0.125, iy - 0.375, ix - rx * 0.5 + 0.125, iy - 0.125))
            col = np.where((shine > 0)[..., None], np.array((255, 244, 214), np.float32), col)
            rgb = np.where((vis > 0)[..., None], col, rgb)
            a = np.maximum(a, vis)
            # The upper lid line on top again, so it still cuts across the iris.
            lid = G.upper_lid(f, cx, s)
            rgb, a = put(rgb, a, lid, np.array(G.LASH, np.float32))
        return rgb, a
    G.head_front(p, fn)
    return p


# ---------------------------------------------------------------------------
# Petrification
# ---------------------------------------------------------------------------

LIMBS = ("rarm", "larm", "rleg", "lleg")
OUTER = {"hat": "head", "jacket": "body", "rsleeve": "rarm", "lsleeve": "larm", "rpants": "rleg", "lpants": "lleg"}
STAGE_REACH = {1: 0.17, 2: 0.6, 3: 0.95, 4: 9.0}


def reach(f, base):
    """How far from the hands and feet this pixel is, 0 (fingertips, toes) .. ~1 (the face)."""
    if base in LIMBS:
        if f.name == "top":
            return f.full(0.5)
        if f.name == "bottom":
            return f.full(0.0)
        return (12 - f.Y) / 12 * 0.5
    if base == "body":
        if f.name == "top":
            return f.full(0.85)
        if f.name == "bottom":
            return f.full(0.5)
        return 0.5 + (12 - f.Y) / 12 * 0.35
    # the head: last of all, the middle of the face very last
    r = np.full_like(f.X, 0.88)
    if f.name == "front":
        r = r + 0.2 * np.clip(1 - (np.abs(f.X - 4) / 3.2) ** 2 - ((f.Y - 4.8) / 2.6) ** 2, 0, 1) * 1.0 + 0.02
    return r


def stone(f, seed):
    """Raw grey stone: blotchy tone, light speckles, dark ink cracks."""
    n = vnoise(f.X * 0.7, f.Y * 0.7, seed)
    col = np.where((n < 0.4)[..., None], STONE_DARK, STONE)
    col = np.where((n > 0.72)[..., None], STONE_LIGHT, col)
    g = grain(f, seed + 1)
    col = np.where((g > 0.97)[..., None], STONE_LIGHT, col)
    col = np.where((g < 0.03)[..., None], STONE_DARK, col)
    c1 = np.abs(vnoise(f.X * 0.55 + 3, f.Y * 0.55, seed + 2) - 0.5) < 0.04
    c2 = np.abs(vnoise(f.X * 0.4, f.Y * 0.5 + 7, seed + 3) - 0.5) < 0.03
    col = np.where((c1 | c2)[..., None], CRACK, col)
    return col


def part_petrify(stage):
    p = Part(tint=False)
    limit = STAGE_REACH[stage]
    boxes = {b: b for b in ("head", "body") + LIMBS}
    for i, (box, base) in enumerate(boxes.items()):
        def fn(f, base=base, outer=box in OUTER, i=i):
            r = reach(f, base) + (vnoise(f.X * 2.2, f.Y * 2.2, 11 + i) - 0.5) * 0.12
            cov = A.crisp((r < limit).astype(np.float32))
            col = stone(f, 20 + i)
            # the creeping edge: a dark rim where stone meets skin
            rim = A.crisp(((r >= limit - 0.035) & (r < limit)).astype(np.float32))
            col = np.where((rim > 0)[..., None], CRACK, col)
            if outer:
                cov = cov * (grain(f, 50 + i) > 0.35)  # a rough, broken shell over the hair and clothes
            return col, cov
        paint(p, box, fn, light=True)
    return p


def main():
    os.makedirs(OUT, exist_ok=True)
    for style in G.EYE_STYLES:
        part_sage_eyes(style).save(os.path.join(OUT, "sage_eyes_%s.png" % style))
    for stage in (1, 2, 3, 4):
        part_petrify(stage).save(os.path.join(OUT, "petrify_%d.png" % stage))
    print("wrote %d sage overlays (%dx%d) to %s" % (len(G.EYE_STYLES) + 4, 64 * SCALE, 64 * SCALE, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Ninja appearance parts for NOIRM's runtime skin composer, in HD (hdskin.SCALE x the 64x64 layout).

NPC ninjas don't have fixed outfit skins. Each one carries an appearance key
("<clan folder>:<hex seed>"); the client (client/NinjaSkins) decodes it with the tables in
looks.json and stacks these part layers into one player skin:

    skin -> mark -> shirt -> pants -> wraps -> sandals -> iris -> eyes -> brows -> hair -> accessory

(The iris goes under the eye: the eye part has the whites with a hole for the iris, the lids,
lashes, pupil and the shine on top.)

Parts drawn in GRAY are tinted at runtime (skin tone, hair, eye, shirt, pants, mask colours;
see hdskin's doc). Parts drawn in colour (marks, sandals, eye whites, glasses...) are used as is.
The face, skin shading and eye shapes live in anatomy.py, shared with the player's parts.

Also writes the dojutsu layers that sit on these faces, one per eye style (so they fit the lids):
    textures/entity/sharingan_eyes_<style>.png, byakugan_eyes_<style>.png

Writes:
    src/main/resources/assets/noirm/textures/entity/ninja/parts/<part>.png
    src/main/resources/assets/noirm/textures/entity/ninja/parts/looks.json

All art is original and drawn by the code below (numpy + Pillow).
Run from the repo root:  python3 tools/skingen/gen_parts.py
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anatomy as A  # noqa: E402
from hdskin import (Part, paint, tone, rgb_of, sstep, ell, ell_d, gauss, box_cov, bezier, stroke,  # noqa: E402
                    vnoise, fbm, grain, tri, sample_grid, hx, ink, grit, hash01, SCALE)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/ninja/parts")
ENTITY_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity")

ARMS = ("rarm", "larm")
LEGS = ("rleg", "lleg")


# ---------------------------------------------------------------------------
# Skin (tinted by skin tone)
# ---------------------------------------------------------------------------

def part_skin(nose=True, mouth=True):
    p = Part()
    paint(p, "head", lambda f: (A.head_skin(f, nose, mouth), 1.0), light=False)
    paint(p, "body", lambda f: (A.body_skin(f), 1.0), light=False)
    for a in ARMS:
        paint(p, a, lambda f: (A.arm_skin(f), 1.0), light=False)
    for leg in LEGS:
        paint(p, leg, lambda f: (A.leg_skin(f), 1.0), light=False)
    return p


# ---------------------------------------------------------------------------
# Cloth helpers (tint layers)
# ---------------------------------------------------------------------------

def weave(f, seed, k0=-0.05):
    """Base cloth: a fine weave, a little mottling."""
    g = grain(f, seed)
    wv = 0.05 * np.sin(f.X * SCALE * math.pi) * np.sin(f.Y * SCALE * math.pi)
    return k0 + (g - 0.5) * 0.08 + wv + (fbm(f.X * 0.8, f.Y * 0.8, seed + 3) - 0.5) * 0.18


def crease(f, x0, y0, x1, y1, width=0.22, strength=0.4):
    """A fold: a shadow line with a lit ridge just beside it, fading at both ends."""
    dx, dy = x1 - x0, y1 - y0
    ll = math.hypot(dx, dy) or 1e-6
    nx, ny = -dy / ll, dx / ll
    t = np.clip(((f.X - x0) * dx + (f.Y - y0) * dy) / (ll * ll), 0, 1)
    d = (f.X - x0) * nx + (f.Y - y0) * ny
    fade = np.clip(np.sin(t * math.pi), 0, 1) ** 0.7
    shadow = np.exp(-(d / width) ** 2)
    ridge = np.exp(-((d - width * 1.6) / (width * 1.2)) ** 2)
    return strength * fade * (0.55 * ridge - shadow)


def seam(f, pts, strength=0.3, stitches=True):
    """A sewn seam: a dark line with small lighter stitches beside it."""
    k = -strength * stroke(f, pts, 0.08)
    if stitches:
        p = np.asarray(pts, np.float32)
        d = np.sqrt(((p[1:] - p[:-1]) ** 2).sum(1)).sum()
        n = max(2, int(d / 0.45))
        for i in range(n):
            t = (i + 0.5) / n
            idx = min(int(t * (len(p) - 1)), len(p) - 2)
            u = t * (len(p) - 1) - idx
            x, y = p[idx] * (1 - u) + p[idx + 1] * u
            k += 0.25 * strength * ell(f, x + 0.12, y, 0.1, 0.05)
    return k


def hem(f, y, up=True, strength=0.35):
    """A hem band ending at row y (a rolled edge with stitching)."""
    band = sstep(y - 0.55, y - 0.4, f.Y) if up else sstep(y + 0.55, y + 0.4, f.Y)
    k = 0.18 * band
    k += -strength * np.exp(-((f.Y - (y - 0.5 if up else y + 0.5)) / 0.07) ** 2)
    k += -0.25 * np.exp(-((f.Y - (y - 0.05 if up else y + 0.05)) / 0.08) ** 2)
    stitch = (tri(f.X / 0.35) > 0.5) * np.exp(-((f.Y - (y - 0.28 if up else y + 0.28)) / 0.04) ** 2)
    return k + 0.22 * stitch


def inked(fn, seed, gain=1.4, dirt=0.0):
    """Wraps a cloth painter: its shading cut into ink tones, plus grit and dirt towards the feet."""
    def wrapped(f):
        res = fn(f)
        if res is None:
            return None
        k, a = res
        k = cloth_ink(f, k, seed)
        if dirt:
            grime = fbm(f.X * 1.6, f.Y * 1.6, seed + 2)
            k = k - dirt * sstep(0.45, 0.75, grime + 0.05 * f.Y) * sstep(4, 12, f.Y)
        return k, a
    return wrapped


CLOTH_TONES = (0.55, 0.0, -0.75, -1.6, -2.7)


def cloth_ink(f, k, seed):
    """Cloth in ink: the design's folds and seams (k) on top of the body's form light, cut into a few
    flat tones with crisp edges (the same light as the skin, so cloth and body read as one figure)."""
    if f.name in ("front", "back", "right", "left"):
        light = A.lit(f, None, None, A.ROUNDNESS[A.kind(f.box)])
        v = k * 1.15 + (light - 0.62) * 1.5
    else:
        v = k * 1.15 + (0.25 if f.name == "top" else -1.2)
    v = v + 0.04 * (fbm(f.X * 2, f.Y * 2, seed) - 0.5)
    out = np.full_like(v, CLOTH_TONES[4])
    out = np.where(v > -1.25, CLOTH_TONES[3], out)
    out = np.where(v > -0.5, CLOTH_TONES[2], out)
    out = np.where(v > -0.08, CLOTH_TONES[1], out)
    out = np.where(v > 0.42, CLOTH_TONES[0], out)
    return out


def edge_alpha(f, y_end):
    """Coverage that ends at row y_end (antialiased)."""
    return np.clip((y_end - f.Y) * SCALE + 0.5, 0, 1)


# ---------------------------------------------------------------------------
# Undershirts (tinted by shirt colour): dark and plain, so armour sits well on top
# ---------------------------------------------------------------------------

SLEEVES = {"mesh": 11.0, "tee": 4.6, "longsleeve": 10.4, "sleeveless": 0, "wrap": 9.0, "turtleneck": 10.4}


def mesh_net(f, k_thread=0.15):
    """A diamond net: lit threads, see-through holes."""
    a = tri((f.X + f.Y) / 0.7)
    b = tri((f.X - f.Y) / 0.7)
    thread = np.clip((np.maximum(a, b) - 0.72) * 6, 0, 1)
    knot = np.clip((np.minimum(a, b) - 0.75) * 8, 0, 1)
    k = np.where(thread > 0, k_thread + 0.2 * knot, -0.45)
    alpha = 0.5 + 0.5 * thread
    return k, alpha


def part_shirt(style):
    p = Part()
    sleeve = SLEEVES[style]

    def body(f):
        X, Y = f.X, f.Y
        if f.name == "bottom":
            return None
        alpha = edge_alpha(f, 10.0)                  # the pants' waist covers rows 10-12
        if f.name == "top":
            k = weave(f, 5, 0.1) + seam(f, [(0.3, 2.0), (7.7, 2.0)], 0.2, False)
            if style in ("tee", "longsleeve", "mesh", "sleeveless"):
                alpha = 1 - ell(f, 4, 4.0, 1.7, 1.7)  # the neck hole
            return k, alpha
        if style == "mesh":
            k, a = mesh_net(f)
            k = k + crease(f, 1.0, 9.6, 7.0, 9.6, 0.25, 0.3) if f.name in ("front", "back") else k
            if f.name == "front":
                a = a * (1 - ell(f, 4, 0.0, 1.2, 0.7))
            return k, a * alpha
        k = weave(f, 11)
        k += -0.25 * sstep(8.9, 10.0, Y)                  # tucked in: shadow at the belt
        if f.name in ("front", "back"):
            k += A.round_edges(f, 0.16, 0.6)
            k += crease(f, 0.4, 1.8, 2.6, 3.4, 0.22, 0.35) + crease(f, 7.6, 1.8, 5.4, 3.4, 0.22, 0.35)  # from the armpits
            k += crease(f, 1.4, 8.6, 3.2, 9.6, 0.2, 0.4) + crease(f, 6.6, 8.6, 4.8, 9.6, 0.2, 0.4)    # bunching at the tuck
            k += crease(f, 3.5, 8.9, 4.4, 9.8, 0.16, 0.3)
            k += 0.1 * gauss(f, 4, 2.6, 2.4, 1.2) if f.name == "front" else 0
        if f.side:
            k += seam(f, [(2.0, 0.2), (2.0, 9.6)], 0.25)
            k += crease(f, 0.5, 1.0, 3.2, 3.0, 0.2, 0.3)
            if style == "sleeveless":
                hole = ell(f, 2.0, 0.0, 1.9, 3.0)
                k += 0.3 * np.clip(1 - np.abs(ell_d(f, 2.0, 0.0, 1.9, 3.0) - 1.08) * 10, 0, 1)  # bound edge
                alpha = alpha * (1 - hole)
        if f.name == "front":
            if style == "tee" or style == "longsleeve":
                hole = ell(f, 4, 0.0, 1.35 if style == "tee" else 1.05, 0.85 if style == "tee" else 0.6)
                d = ell_d(f, 4, 0.0, 1.35 if style == "tee" else 1.05, 0.85 if style == "tee" else 0.6)
                k += 0.32 * np.clip(1 - np.abs(d - 1.18) * 5, 0, 1)          # ribbed neckline
                alpha = alpha * (1 - hole)
            if style == "sleeveless":
                hole = ell(f, 4, 0.0, 1.5, 1.1)
                d = ell_d(f, 4, 0.0, 1.5, 1.1)
                k += 0.3 * np.clip(1 - np.abs(d - 1.15) * 5, 0, 1)
                alpha = alpha * (1 - hole)
            if style == "wrap":
                # crossed collar: the left lapel over the right, skin in the V
                v_open = np.clip((np.abs(X - 4) * 1.15 - (Y - 0.2) * 0.95) * SCALE * 0.5 + 0.5, 0, 1) * (Y < 4.3)
                lap_r = np.clip(1 - np.abs((X - 4) * 1.0 - (Y - 0.2) * 0.87 - 0.55) / 0.55, 0, 1) * (X >= 3.4) * (Y < 5.8)
                lap_l = np.clip(1 - np.abs(-(X - 4) * 1.0 - (Y - 0.2) * 0.87 - 0.55) / 0.55, 0, 1) * (X <= 4.6) * (Y < 5.8)
                lap = np.maximum(lap_r * (X > 4) + lap_r * (X <= 4) * 0, lap_l)
                k = np.where(lap > 0.05, 0.45 + 0.12 * lap - 0.05 * (Y > 4), k)
                k += -0.4 * np.exp(-((np.abs(X - 4) * 1.0 - (Y - 0.2) * 0.87 - 1.12) / 0.08) ** 2) * (Y < 5.6)
                k += crease(f, 4.4, 5.4, 6.6, 7.6, 0.22, 0.4)        # the fold under the overlap
                alpha = alpha * (1 - v_open * (lap < 0.05))
        if style == "turtleneck":
            band = sstep(1.35, 1.2, Y)
            k = np.where(band > 0.5, 0.3 + 0.18 * np.sin(Y * 9.0) - 0.12 * tri(X / 0.5), k)
            k += -0.3 * np.exp(-((Y - 1.3) / 0.08) ** 2)
        return k, alpha

    def arm(f):
        if sleeve <= 0 or f.name == "bottom":
            return None
        X, Y = f.X, f.Y
        alpha = edge_alpha(f, sleeve)
        if f.name == "top":
            return weave(f, 13, 0.1), 1.0
        if style == "mesh":
            k, a = mesh_net(f)
            return k, a * alpha
        k = weave(f, 13) + A.round_edges(f, 0.14)
        k += 0.12 * gauss(f, 2, 0.8, 1.6, 0.9)
        if f.name == "front":
            k += crease(f, 0.8, 1.0, 3.0, 2.2, 0.2, 0.3)     # pull at the shoulder
        if sleeve > 6.5:
            # folds bunch at the elbow (the model bends at row 6) and above the cuff
            k += crease(f, 0.3, 5.3, 3.6, 6.1, 0.2, 0.45) + crease(f, 0.4, 6.4, 3.4, 5.9, 0.18, 0.35)
            k += crease(f, 0.6, 7.6, 3.2, 7.2, 0.18, 0.3)
            if sleeve > 9.8:
                k += crease(f, 0.3, sleeve - 1.1, 3.7, sleeve - 0.8, 0.16, 0.3)
        if f.side:
            k += seam(f, [(0.4, 0.2), (0.4, sleeve - 0.2)], 0.2, False)
        if style == "wrap":
            k += hem(f, sleeve, True, 0.5) - 0.15
        else:
            k += hem(f, sleeve, True, 0.35) + 0.1 * sstep(sleeve - 0.5, sleeve - 0.4, Y)
        return k, alpha

    paint(p, "body", inked(body, 201))
    for a in ARMS:
        paint(p, a, inked(arm, 203))
    return p


# ---------------------------------------------------------------------------
# Pants (tinted by pants colour)
# ---------------------------------------------------------------------------

PANTS = {"long": 10.0, "capri": 8.2, "shorts": 4.8}


def part_pants(style):
    p = Part()
    bottom = PANTS[style]

    def waist(f):
        if f.name in ("top", "bottom"):
            return None
        X, Y = f.X, f.Y
        alpha = sstep(9.9, 10.0, Y)
        # the sash: a darker band, wrinkled, tied at the front
        sash = (Y < 11.05)
        k = np.where(sash, -0.85 + 0.15 * np.sin(X * 7.0 + Y * 3) * 0.5 + (grain(f, 61) - 0.5) * 0.1,
                     weave(f, 63) - 0.1)
        k += -0.35 * np.exp(-((Y - 11.05) / 0.08) ** 2) + 0.15 * np.exp(-((Y - 10.1) / 0.1) ** 2)
        if f.name == "front":
            knot = ell(f, 5.2, 10.5, 0.5, 0.45)
            k = np.where(knot > 0.3, -0.55 + 0.3 * gauss(f, 5.1, 10.35, 0.25, 0.2), k)
            k += -0.3 * stroke(f, [(5.4, 10.9), (5.8, 11.9)], 0.3, 0.15) * 0 + crease(f, 4.0, 11.2, 4.0, 12.0, 0.12, 0.4)
            k += crease(f, 1.0, 11.4, 3.0, 11.9, 0.15, 0.3) + crease(f, 7.0, 11.4, 5.0, 11.9, 0.15, 0.3)
        if f.side:
            k += seam(f, [(2.0, 11.1), (2.0, 12.0)], 0.2, False)
        return k, alpha

    def leg(f):
        X, Y = f.X, f.Y
        if f.name == "top":
            return weave(f, 41), 1.0
        if f.name == "bottom":
            return None
        alpha = edge_alpha(f, bottom)
        k = weave(f, 41) + A.round_edges(f, 0.14)
        if f.name == "front":
            k += 0.1 * gauss(f, 2, 2.4, 1.3, 1.8)
            k += crease(f, 0.5, 0.4, 2.0, 1.4, 0.2, 0.3)       # pull from the hip
            if bottom > 6.5:
                k += crease(f, 0.4, 5.4, 3.6, 5.9, 0.18, 0.45) + crease(f, 0.6, 6.5, 3.4, 6.1, 0.16, 0.35)  # knee
                k += 0.12 * ell(f, 2, 6.0, 0.9, 0.5)
        if f.name == "back":
            k += crease(f, 0.4, 0.8, 3.6, 0.6, 0.18, 0.3)
            if bottom > 6.5:
                k += crease(f, 0.5, 5.8, 3.5, 6.2, 0.16, 0.45)
        if f.side:
            k += seam(f, [(2.0 if f.name == "right" else 2.0, 0.0), (2.0, bottom - 0.1)], 0.22, False)
        if style == "long":
            # gathered at the ankle (tied over the shin), puffing out above it
            k += 0.15 * gauss(f, 2, bottom - 1.2, 3, 0.4) + crease(f, 0.3, bottom - 1.0, 3.7, bottom - 0.7, 0.14, 0.4)
            band = sstep(bottom - 0.65, bottom - 0.55, Y)
            k = np.where(band > 0.5, -0.5 + 0.2 * tri((X + Y) / 0.5), k)
        elif style == "capri":
            k += crease(f, 0.3, bottom - 1.2, 3.7, bottom - 0.9, 0.16, 0.35)
            k += hem(f, bottom, True, 0.4)
        else:
            k += hem(f, bottom, True, 0.35)
        return k, alpha

    paint(p, "body", inked(waist, 205))
    for leg_box in LEGS:
        paint(p, leg_box, inked(leg, 207, dirt=0.6))
    return p


# ---------------------------------------------------------------------------
# Fixed-colour body pieces: wraps and sandals
# ---------------------------------------------------------------------------

WRAP_CLOTH = hx("#ddd7c9")


def wrap_rgb(f, y0, y1, seed):
    """Bandage strips wound diagonally between rows y0 and y1."""
    X, Y = f.X, f.Y
    step = 0.62
    u = (Y + X * 0.28) / step
    q = u - np.floor(u)
    k = 0.15 - 0.55 * sstep(0.7, 1.0, q) - 0.25 * sstep(0.25, 0.0, q)  # each strip: lit, shaded under the next
    k += (grain(f, seed) - 0.5) * 0.25 + (fbm(X * 2, Y * 2, seed) - 0.5) * 0.35
    k += -0.6 * (fbm(X * 1.5, Y * 1.5, seed + 9) > 0.68)               # grime
    rgb = tone(WRAP_CLOTH, k)
    alpha = np.clip((Y - y0) * SCALE + 0.5, 0, 1) * np.clip((y1 - Y) * SCALE + 0.5, 0, 1)
    # frayed ends
    alpha = alpha * (1 - (np.abs(Y - y1) < 0.2) * (grain(f, seed + 1) > 0.6))
    return rgb, alpha


def part_wraps(boxes, y0, y1):
    p = Part(tint=False)
    for b in boxes:
        paint(p, b, lambda f: None if f.name in ("top", "bottom") else wrap_rgb(f, y0, y1, 71))
    return p


SANDAL = hx("#2b3043")
SOLE = hx("#5a4a3a")


def part_sandals():
    p = Part(tint=False)

    def leg(f):
        X, Y = f.X, f.Y
        shape = X.shape
        if f.name == "top":
            return None
        if f.name == "bottom":
            k = -0.3 + (grain(f, 81) - 0.5) * 0.3 + 0.15 * tri(Y / 0.5)
            return tone(SOLE, k), 1.0
        sole = sstep(11.45, 11.55, Y)
        sole_rgb = tone(SOLE, -0.2 - 0.3 * sstep(11.6, 12, Y) + 0.3 * np.exp(-((Y - 11.55) / 0.06) ** 2))
        strap_k = 0.1 + (grain(f, 83) - 0.5) * 0.15
        a = np.zeros(shape, np.float32)
        k = np.full(shape, 0.0, np.float32)
        if f.name == "front":
            strap = np.clip(1 - np.abs(Y - 10.55) / 0.3, 0, 1)  # across the toes
            a = np.maximum(a, np.clip(strap * 3, 0, 1))
            k = np.where(strap > 0, strap_k + 0.3 * (Y < 10.45) - 0.3 * (Y > 10.7), k)
        elif f.name == "back":
            heel = sstep(9.3, 9.45, Y)
            a = np.maximum(a, heel)
            k = strap_k - 0.2 + 0.25 * np.exp(-((Y - 9.45) / 0.08) ** 2) + crease(f, 0.5, 10.6, 3.5, 10.6, 0.14, 0.4)
        else:
            mx = f.MX
            heel = (mx < 1.8) * sstep(9.3, 9.45, Y)
            strap = np.clip(1 - np.abs(Y - (9.6 + (mx - 1.0) * 0.55)) / 0.22, 0, 1) * (mx < 3.6)  # ankle strap
            a = np.maximum(heel, np.clip(strap * 3, 0, 1))
            k = np.where(heel > 0, strap_k - 0.15, strap_k + 0.2 * strap)
            k += 0.3 * ell(f, 1.0, 9.65, 0.14, 0.14)  # a rivet
        rgb = tone(SANDAL, k)
        rgb = np.where(sole[..., None] > 0.5, sole_rgb, rgb)
        return rgb, np.maximum(a, sole)

    for leg_box in LEGS:
        paint(p, leg_box, leg)
    return p


# ---------------------------------------------------------------------------
# Eyes: the iris (tinted by eye colour) sits under the eye part (whites with a hole for the iris,
# lids, lashes, pupil and shine). Shapes and centres come from anatomy.EYE_SHAPES / EYE_XS.
# ---------------------------------------------------------------------------

WHITE = hx("#ddd6cc")
WHITE_SHADE = hx("#8f837d")
WHITE_INNER = hx("#b89a92")
PUPIL = hx("#070507")
LASH = hx("#0e090b")
LID = hx("#3e211c")
HOOD = hx("#2a1410")      # the brow's shadow on the upper lid (laid over the skin, partly see-through)

EYE_STYLES = ("round", "sharp", "wide", "narrow", "pale")


def head_front(part, fn):
    paint(part, "head", lambda f: fn(f) if f.name == "front" else None, light=False)


def hat_paint(part, fn, light=True):
    paint(part, "hat", fn, light=light)


def lid_points(cx, s, which, n=32, x0=None, x1=None):
    """Points along the upper or lower lid, from the inner corner to the outer corner."""
    xin, xout = A.corner_xs(cx, s)
    xs = np.linspace(xin if x0 is None else x0, xout if x1 is None else x1, n)
    top, bottom = A.lid_curves(xs, cx, s)
    ys = top if which == "top" else bottom
    return list(zip(xs, ys))


def iris_k(f, cx, s, pale):
    """The iris in the eye colour, in crisp tones: the lid's shadow across its top, a dark ring at its
    edge, a lighter lower half (light coming through it gives the eye depth)."""
    ix, iy = A.iris_centre(cx, s)
    rx, ry = A.iris_radii(s)
    top, _ = A.lid_curves(f.X, cx, s)
    d = ell_d(f, ix, iy, rx, ry)
    v = (f.Y - iy) / ry                                           # -1 top .. +1 bottom
    under_lid = f.Y - top < 0.3                                   # the row right under the heavy lid
    if pale:
        k = np.full_like(f.X, 1.0)
        k = np.where(v < 0.0, 0.45, k)
        k = np.where(under_lid, -0.7, k)
        k = np.where(d > 0.8, -0.3, k)
        return k, A.crisp(ell(f, ix, iy, rx, ry))
    k = np.full_like(f.X, 0.0)
    k = np.where(v > 0.15, 1.0, k)                                # the lit lower half
    k = np.where((v > 0.15) & (d > 0.62), 0.45, k)
    k = np.where(v < -0.2, -0.8, k)
    k = np.where(under_lid, -1.7, k)                              # the lid's shadow
    k = np.where(d > 0.8, -2.3, k)                                # the dark ring
    return k, A.crisp(ell(f, ix, iy, rx, ry))


def part_iris(style):
    s = A.EYE_SHAPES[style]
    p = Part()

    def fn(f):
        k = f.zeros()
        a = f.zeros()
        for cx in A.EYE_XS:
            kk, cov = iris_k(f, cx, s, style == "pale")
            inside = A.eye_inside(f, cx, s)
            k = np.where(cov > 0, kk, k)
            a = np.maximum(a, cov * inside)
        return k, a
    head_front(p, fn)
    return p


def _over(rgb, a, cov, colour):
    cov = np.asarray(cov, np.float32)
    rgb = rgb * (1 - cov[..., None]) + np.asarray(colour, np.float32) * cov[..., None]
    return rgb, np.maximum(a, cov)


def upper_lid(f, cx, s):
    """The upper lid line: heavy, thicker towards the outer corner, then a flick out and down. It
    overlaps the top of the opening, so it cuts across the iris."""
    dirx = -1 if cx < 4 else 1
    up = lid_points(cx, s, "top", n=40)
    lid = A.crisp(stroke(f, [(x, y - s["lash"] * 0.3) for x, y in up], s["lash"] * 0.4, s["lash"]))
    ox, oy = up[-1]
    oy -= s["lash"] * 0.3
    wing = A.crisp(stroke(f, [(ox - dirx * 0.15, oy), (ox + dirx * s["wing"], oy - 0.02 - s["wing"] * 0.12)],
                          s["lash"] * 0.85, 0.14))
    return np.maximum(lid, wing)


def visible_iris(f, cx, s):
    """The iris pixels you can see: inside the opening and not under the upper lid's line."""
    ix, iy = A.iris_centre(cx, s)
    rx, ry = A.iris_radii(s)
    return A.crisp(ell(f, ix, iy, rx, ry)) * A.eye_inside(f, cx, s) * (1 - upper_lid(f, cx, s))


def part_eyes(style):
    """Everything of the eye but the iris colour: the brow's shadow over the lid, the white (showing
    on the outer side), a small pupil, one small sharp highlight, a heavy angled upper lid that cuts
    across the iris with a flick at the outer corner, and a thin lower lid line."""
    s = A.EYE_SHAPES[style]
    pale = style == "pale"
    p = Part(tint=False)

    def fn(f):
        rgb = np.zeros(f.X.shape + (3,), np.float32)
        a = f.zeros()
        X, Y = f.X, f.Y
        px = 1.0 / SCALE
        for cx in A.EYE_XS:
            top, bottom, across = A.eye_lids(f, cx, s)
            inside = A.eye_inside(f, cx, s)
            ix, iy = A.iris_centre(cx, s)
            rx, ry = A.iris_radii(s)
            iris = A.crisp(ell(f, ix, iy, rx, ry))
            dirx = -1 if cx < 4 else 1                     # towards this eye's outer corner
            u = A.across_u(X, cx, s)                       # 0 inner corner .. 1 outer corner
            lash_y = top - s["lash"] * 0.3                  # the lid line's centre: it overlaps the opening
            # the brow's shadow on the upper lid: ink laid over the skin in two steps (intensity)
            span = (u > -0.08) & (u < 1.02)
            gap = lash_y - Y
            near = A.crisp(((gap > 0) & (gap < s["hood"] * 0.55) & span).astype(np.float32))
            far = A.crisp(((gap >= s["hood"] * 0.55) & (gap < s["hood"]) & span & (u > 0.12) & (u < 0.9)).astype(np.float32))
            shade = near * 0.42 + far * 0.2
            rgb = np.where((shade > a)[..., None], np.array(HOOD, np.float32), rgb)
            a = np.maximum(a, shade)
            # the white: only where the iris isn't; the row under the lid in its shadow, the inner
            # corner pinker
            white = inside * (1 - iris)
            wcol = np.where((Y - top < 0.3)[..., None], np.array(WHITE_SHADE, np.float32), np.array(WHITE, np.float32))
            wcol = np.where((u < 0.14)[..., None], np.array(WHITE_INNER, np.float32), wcol)
            rgb, a = _over(rgb, a, white, wcol)
            if not pale:
                pup = A.crisp(ell(f, ix, iy + 0.06, max(rx * 0.3, px * 0.6), max(ry * 0.4, px))) * inside
                rgb, a = _over(rgb, a, pup, PUPIL)
            # one small, sharp highlight towards the light (the character's right = texture left)
            hx0, hy0 = ix - rx * 0.42, iy - ry * 0.12
            shine = A.crisp(box_cov(f, hx0 - px * 0.5, hy0 - px * 0.5, hx0 + px * 0.5, hy0 + px * 0.5)) * inside * iris
            rgb, a = _over(rgb, a, shine, (255, 255, 255))
            rgb, a = _over(rgb, a, upper_lid(f, cx, s), LASH)
            # the lower lid: a thin line under the outer half, not meeting the upper lid
            lo = lid_points(cx, s, "bottom", n=24)
            lo = lo[len(lo) // 2:-1]
            lower = A.crisp(stroke(f, [(x, y + 0.12) for x, y in lo], 0.1, 0.22))
            rgb, a = _over(rgb, a, lower * (1 - inside) * (a < 0.5), LID)
        return rgb, a
    head_front(p, fn)
    return p


BROW_STYLES = ("thin", "thick", "fierce", "soft")

BROW_SHAPES = {
    # (inner end, middle, outer end) as (x offset outward from the eye centre, y), widths inner -> outer
    "thin":   (((-1.05, 3.74), (0.05, 3.3), (1.25, 3.4)), 0.34, 0.14),
    "thick":  (((-1.1, 3.7), (0.05, 3.24), (1.25, 3.34)), 0.62, 0.36),
    "fierce": (((-1.05, 3.78), (0.15, 3.24), (1.3, 3.0)), 0.5, 0.2),
    "soft":   (((-1.0, 3.6), (0.05, 3.22), (1.15, 3.38)), 0.36, 0.18),
}


def part_brows(style):
    pts, w_in, w_out = BROW_SHAPES[style]
    p = Part()

    def fn(f):
        k = f.zeros()
        a = f.zeros()
        for cx in A.EYE_XS:
            sgn = -1 if cx < 4 else 1
            ctrl = [(cx + sgn * x, y) for x, y in pts]
            cov = A.crisp(stroke(f, bezier(ctrl[0], ctrl[1], ctrl[2], 24), w_in, w_out))
            # a lit upper edge, a dark body; strands at the inner end
            above = A.crisp(stroke(f, bezier((ctrl[0][0], ctrl[0][1] - 0.12), (ctrl[1][0], ctrl[1][1] - 0.12),
                                            (ctrl[2][0], ctrl[2][1] - 0.08), 24), w_in * 0.45, w_out * 0.3))
            kk = np.where(above > 0, -0.55, -1.5)
            if style == "soft":
                kk = kk + 0.5
            k = np.where(cov > a, kk, k)
            a = np.maximum(a, cov)
        return k, a
    head_front(p, fn)
    return p


# ---------------------------------------------------------------------------
# Hair (tinted by hair colour). The ASCII maps below are each style's design at the old
# 8 x 8 resolution: . none, H highlight, h hair, d shade, x deep shade. Side maps are for the
# right face: column 0 = back of the head, column 7 = the face side. base_* go on the head
# itself, the others on the outer (hat) layer for volume; jacket_back hangs down the back.
# They are drawn smoothly: blended outlines cut into locks with pointed tips, strands, a sheen.
# ---------------------------------------------------------------------------

E = "........"
HAIR = {
    "spiky": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h......h", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 7 + ["dhdhhdhd"],
        "front": ["HhHHhHHh", "hhdhhdhh", "d.hd.hd.", "d......d", E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhd", "hhhdhhd.", "hdhhd.d.", "dhd.d...", "d.d.....", E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hdhhdhhd", "hhdhhhdh", "dhhdhhhd", "hdhhdhdh", "d.dh.dd.", ".d..d..d"],
    },
    "messy": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hh.....h", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhh...", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["hHhHHhHh", "hhhdhhhd", "hdhh.dhd", ".d.h..d.", "...d....", E, E, E],
        "side": ["HhHHhHHh", "hhdhhhhh", "hhhhdhhd", "dhhhhd.d", "hdhd.d..", ".d.d....", E, E],
        "back": ["hHhHHhHh", "hhhhhhhh", "hhdhhdhh", "dhhhhhhd", "hhdhdhhh", "hdhhhdhd", "dhd.dhd.", "d...d..d"],
    },
    "short": {
        "base_front": ["hhhhhhhh", "hhhhhh.h", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhhh...", "hhh.....", "hh......", E, E],
        "base_back": ["hhhhhhhh"] * 6 + [E, E],
        "front": ["HhHHhHHh", "hhhhd..h", "d......d", E, E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "dhhhd...", "hd......", E, E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhhhhh", "hdhhhhdh", "dhhdhhhd", "dhdddhdd", E, E],
    },
    "ponytail": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhhh...", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHHhHHh", "hhd.hhdh", "d......d", "d.......", E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "hhhhd...", "dhhd....", "dd......", E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhdhhdhh", "hhhxxhhh", "dh.hh.hd", "...hd...", "...dh...", "...hd..."],
        "jacket_back": ["...hd...", "...dh...", "...hd...", "....d..."],
    },
    "long": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h......h", "h......h", E, E, E, E],
        "base_side": ["hhhhhhhh"] * 8,
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHhhHhH", "hhd..dhh", "hd....dh", "h......h", "h......h", "d......d", "d......d", "x......x"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhdhh", "hdhhhhhd", "hhhhdhhh", "dhhhhhdh", "hhdhhhhd", "dhhdhdhd"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhdhhhdh", "hhhhdhhh", "hdhhhhhd", "hhhdhhhh", "dhhhhdhh", "hhdhhhhd"],
        "jacket_back": ["hhhhhhhh", "hdhhhhdh", "dhhdhhhd", ".dhhdhd.", "..d..d.."],
    },
    "swept": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", ".hhhhh..", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", "h......."],
        "base_back": ["hhhhhhhh"] * 7 + ["dhhdhdhh"],
        "front": ["HHhHHhHh", "hhhhhhhd", ".dhhhhd.", "...dhhh.", "....dhh.", ".....d..", E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhdh", "hhhhd..d", "hdhd....", "dd......", E, E],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhdhhh", "hdhhhhdh", "hhdhhdhh", "dhhhhhhd", "hdhdhdh.", ".d...d.."],
    },
    "bowl": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhh..hhh", "hh......", E, E, E],
        "base_back": ["hhhhhhhh"] * 6 + [E, E],
        "front": ["hHHHHHHh", "hhhhhhhh", "dddddddd", E, E, E, E, E],
        "side": ["HHHHHHHH", "hhhhhhhh", "hhhhhhhh", "ddd...dd", E, E, E, E],
        "back": ["HHHHHHHH", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "dddddddd", E, E],
    },
    "buzz": {
        "base_front": ["hhhhhhhh", "dhhhhhhd", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhh.....", "hhh.....", "hh......", E, E],
        "base_back": ["hhhhhhhh"] * 5 + ["dhdhdhdh", E, E],
        "front": [E] * 8, "side": [E] * 8, "back": [E] * 8,
        "no_hat_top": True,
    },
    "topknot": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", E, E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhh.", "hhhh....", "hhh.....", "hh......", E, E],
        "base_back": ["hhhhhhhh"] * 6 + ["dhhhhhhd", E],
        "front": ["..dHHd..", "d......d", E, E, E, E, E, E],
        "side": ["..dhhd..", "hhhhhhhd", "dhhhd...", E, E, E, E, E],
        "back": ["..dHHd..", "hhhxxhhh", "dhhhhhhd", "hdhhhhdh", "dhdhhdhd", E, E, E],
        "top_knot": True,
    },
    "duck": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hh....hh", "h......h", E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", E],
        "base_back": ["hhhhhhhh"] * 7 + ["dhdhhdhd"],
        "front": ["HhHHhHHh", "hhhdhhhh", "hd....dh", "h......h", "h......h", "d......d", E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhdh", "hhhhhd..", "hdhd....", "d.d.....", E, E],
        "back": ["HhHHhHHh", "hHhhHhhH", "hdhhdhhd", "dhhdhhhd", "hdhdhdhd", "dhdhhdhd", "d.dd.d.d", ".d..d..."],
    },
    "bun": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "h.....hh", E, E, E, E, E],
        "base_side": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhh..", "hhhh....", "hhh.....", "hh......", E],
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHhhHhH", "hhhhhdhh", "hd....hd", "d.......", E, E, E, E],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhd.", "dhhhd...", "hd......", E, E, E],
        "back": ["hhhhhhhh", "hhdHHdhh", "hdHhhHdh", "hdhhhhdh", "hhdxxdhh", "dhhhhhhd", "dhhhhhhd", ".dddddd."],
    },
    "hime": {
        "base_front": ["hhhhhhhh", "hhhhhhhh", "hh....hh", "h......h", "h......h", E, E, E],
        "base_side": ["hhhhhhhh"] * 8,
        "base_back": ["hhhhhhhh"] * 8,
        "front": ["HhHhhHhH", "hhhhhhhh", "dd....dd", "h......h", "h......h", "h......h", "h......h", "d......d"],
        "side": ["HHhHHhHh", "hhhhhhhh", "hhhhhhhh", "hhhdhhhd", "hhhhhhhh", "dhhhhdhh", "hhhhhhhh", "hdhhdhhd"],
        "back": ["HhHHhHHh", "hhhhhhhh", "hhhhhhhh", "hdhhhhdh", "hhhhhhhh", "hhdhhdhh", "hhhhhhhh", "dhhhhhhd"],
        "jacket_back": ["hhhhhhhh", "hhhhhhhh", "hdhhhhdh", "hhhdhhhh", "dhhhhhhd", "hhdhhdhh", ".hdhhdh.", "..d..d.."],
    },
}

HAIR_K = {"H": 0.45, "h": 0.0, "d": -0.7, "x": -1.4, ".": -0.4}

# Lock width (model px) and how pointed the tips are (0 = a blunt cut) per style.
HAIR_LOCKS = {
    "spiky": (0.9, 0.55), "messy": (0.75, 0.45), "short": (0.7, 0.3), "ponytail": (0.8, 0.3),
    "long": (0.8, 0.3), "swept": (1.0, 0.4), "bowl": (1.0, 0.05), "buzz": (0.5, 0.15),
    "topknot": (0.8, 0.3), "duck": (0.85, 0.45), "bun": (0.8, 0.3), "hime": (1.0, 0.05),
}


LOCK_SCALE = 1.7     # locks are chunky: about 6 HD pixels wide at SCALE 4
HAIR_TONES = (-2.1, -1.2, -0.45, 0.2, 0.95)   # deep (between locks), dark, mid, light, sheen


def lock_shade(f, X, Y, lock_w, salt, y_band=1.5, sheen=True, depth=0.0):
    """Chunky locks: each lock is a round strand of hair lit from the left, with a dark gap at its right
    edge, a little longer or shorter than its neighbours, and a broken sheen band across the top.
    Returns (k cut into HAIR_TONES, per-lock length offset)."""
    wob = 0.18 * np.sin(Y * 1.3 + salt) + 0.12 * np.sin(Y * 0.55 + salt * 0.7)
    u = X / lock_w + wob
    li = np.floor(u)
    q = u - li                                            # 0 at the lock's lit edge, 1 at its dark edge
    rnd = hash01(li, np.zeros_like(li), salt)
    rnd2 = hash01(li, np.ones_like(li), salt + 3)
    v = 0.55 - 1.1 * q + (rnd - 0.5) * 0.5 - depth       # round: lit left, falling into shadow
    v = v - 0.9 * sstep(0.7, 0.92, q)                    # the gap between locks
    # a thin strand line splitting wide locks
    v = np.where((np.abs(q - 0.5) < 0.05) & (rnd2 > 0.7) & (Y > 1.6), v - 0.9, v)
    if sheen:
        yb = y_band + 0.35 * np.sin(li * 1.7 + salt) + 0.25 * (rnd2 - 0.5)
        band = (np.abs(Y - yb) < 0.2 + 0.12 * (q < 0.35)) & (q > 0.08) & (q < 0.6) & (rnd > 0.22)
        v = np.where(band, 2.0, v)
    k = np.full_like(v, HAIR_TONES[0])
    k = np.where(v > -0.75, HAIR_TONES[1], k)
    k = np.where(v > -0.2, HAIR_TONES[2], k)
    k = np.where(v > 0.3, HAIR_TONES[3], k)
    k = np.where(v > 1.5, HAIR_TONES[4], k)
    return k, rnd


def hair_field(f, rows, salt, lock_w, sharp, layer):
    """(k, alpha) for one face from an 8-row ASCII map: its silhouette cut into pointed locks."""
    cov_grid = [[0.0 if ch == "." else 1.0 for ch in row] for row in rows]
    tone_grid = [[HAIR_K[ch] for ch in row] for row in rows]
    W, H = f.W, f.H
    X = f.MX if f.side else f.X
    Y = f.Y
    cov = sample_grid(cov_grid, X, Y, W, H)
    lock_w = lock_w * LOCK_SCALE
    k, rnd = lock_shade(f, X, Y, lock_w, salt, depth=0.35 if layer == "base" else 0.0)
    # tips: each lock ends in a point; neighbours a little longer or shorter
    phase = (salt % 7) * 0.13
    u = X / lock_w + 0.18 * np.sin(Y * 1.3 + salt) + 0.12 * np.sin(Y * 0.55 + salt * 0.7)
    tooth = tri(u + 0.5)
    thr = 0.5 + sharp * ((tooth - 0.5) * 1.5 + (rnd - 0.5) * 0.5)
    alpha = A.crisp(np.clip((cov - thr) * 7 + 0.5, 0, 1)) * (cov > 0.3)
    # darker towards the tips and where the map asks for shade
    base = sample_grid(tone_grid, X, Y, W, H)
    k = k + np.minimum(base, 0) * 0.9
    k = np.where(cov < 0.75, np.minimum(k, HAIR_TONES[1]), k)
    k = np.where(cov < 0.6, HAIR_TONES[0] * 0.8, k)
    return k, alpha


def part_hair(name, salt):
    style = HAIR[name]
    lock_w, sharp = HAIR_LOCKS[name]
    p = Part()

    def crown(f, knot=False):
        X, Y = f.X, f.Y
        ang = np.arctan2(Y - 5.2, X - 4)
        r = np.hypot(X - 4, Y - 5.2)
        k, _ = lock_shade(f, ang * 2.6, r * 1.0, 1.0, salt + 13, y_band=2.6, depth=-0.1)
        k = np.where(r < 0.6, HAIR_TONES[1], k)                     # the whorl
        if knot:
            kd = ell_d(f, 4, 3.2, 1.25, 1.25)
            kk, _ = lock_shade(f, np.arctan2(Y - 3.2, X - 4) * 2.0, kd * 3, 1.0, salt + 17, y_band=1.2)
            k = np.where(kd < 1.0, kk, k)
            k = np.where(np.abs(kd - 1.0) < 0.1, HAIR_TONES[0], k)
        return k

    def base(f):
        if f.name == "bottom":
            return None
        if f.name == "top":
            return crown(f) - 0.4, 1.0
        rows = style["base_front"] if f.name == "front" else style["base_side"] if f.side else style["base_back"]
        return hair_field(f, rows, salt + (3 if f.side else 0), lock_w, sharp, "base")

    def hat(f):
        if f.name == "bottom":
            return None
        if f.name == "top":
            if style.get("no_hat_top"):
                return None
            return crown(f, style.get("top_knot", False)), 1.0
        rows = style["front"] if f.name == "front" else style["side"] if f.side else style["back"]
        return hair_field(f, rows, salt + (5 if f.side else 7), lock_w, sharp, "hat")

    paint(p, "head", base)
    paint(p, "hat", hat)
    rows = style.get("jacket_back", [])
    if rows:
        full = rows + ["........"] * (12 - len(rows))

        def jb(f):
            if f.name != "back":
                return None
            # the map's rows are model pixels here (12 rows on the body)
            return hair_field(f, full, salt + 9, lock_w * 0.9, max(sharp, 0.35), "hat")
        paint(p, "jacket", jb)
    return p


# ---------------------------------------------------------------------------
# Face marks (fixed colours; on the face itself)
# ---------------------------------------------------------------------------

MARKS = {}


def mark(name):
    def deco(fn):
        MARKS[name] = fn
        return fn
    return deco


def mirrored(f, fn):
    """Draws fn(f, X) on the texture-left cheek and mirrored on the right; returns coverage."""
    a = fn(f, f.X)
    b = fn(f, 8 - f.X)
    return np.maximum(a, b)


def colour_layer(f, cov, c, k=None):
    rgb = tone(c, k if k is not None else f.zeros())
    return rgb, cov


@mark("whiskers")
def _whiskers(f):
    def one(f, X):
        cov = f.zeros()
        for i, y in enumerate((5.45, 5.9, 6.35)):
            cov = np.maximum(cov, stroke(f, bezier((0.25, y - 0.12 + i * 0.05), (0.95, y), (1.75, y + 0.02 - i * 0.04), 12),
                                         0.13, 0.05, X=X))
        return cov
    return colour_layer(f, mirrored(f, one) * 0.85, hx("#3b2a24"))


@mark("fangs")
def _fangs(f):
    def one(f, X):
        return stroke(f, [(1.05, 5.15), (1.45, 6.85)], 0.55, 0.02, X=X)
    cov = mirrored(f, one)
    return colour_layer(f, cov, hx("#b3262a"), -0.4 * sstep(5.2, 6.8, f.Y))


@mark("swirls")
def _swirls(f):
    def one(f, X):
        t = np.linspace(0, 2.6 * math.pi, 40)
        r = 0.08 + 0.13 * t
        pts = list(zip(1.2 + r * np.cos(t), 5.9 + r * np.sin(t) * 0.9))
        return stroke(f, pts, 0.12, 0.2, X=X)
    return colour_layer(f, mirrored(f, one), hx("#c0392b"))


@mark("scar_eye")
def _scar_eye(f):
    cov = stroke(f, [(5.25, 2.5), (5.55, 4.4), (5.85, 6.5)], 0.18, 0.1) * 0.9
    stitches = f.zeros()
    for y in (3.0, 3.6, 5.3, 5.9):
        x = 5.25 + (y - 2.5) * 0.15
        stitches = np.maximum(stitches, stroke(f, [(x - 0.22, y - 0.05), (x + 0.22, y + 0.05)], 0.06))
    rgb = tone(hx("#9a5a4e"), 0.4 * gauss(f, 5.4, 4.4, 0.06, 3))
    rgb = np.where(stitches[..., None] > 0.3, tone(hx("#5a342e"), f.zeros()), rgb)
    return rgb, np.maximum(cov, stitches * 0.8)


@mark("scar_nose")
def _scar_nose(f):
    cov = stroke(f, [(2.3, 5.25), (4.0, 5.0), (5.7, 5.2)], 0.12, 0.22) * 0.85
    return colour_layer(f, cov, hx("#a0604f"), 0.3 * np.exp(-((f.Y - 5.05) / 0.05) ** 2))


@mark("tear_lines")
def _tear(f):
    def one(f, X):
        return stroke(f, bezier((2.95, 5.15), (2.9, 6.0), (2.75, 7.0), 12), 0.12, 0.04, X=X)
    return colour_layer(f, mirrored(f, one) * 0.6, hx("#4a3a3a"))


@mark("diamond")
def _diamond(f):
    d = np.abs(f.X - 4) / 0.42 + np.abs(f.Y - 1.75) / 0.6
    cov = np.clip((1 - d) * 0.42 * SCALE + 0.5, 0, 1)
    return colour_layer(f, cov, hx("#7b3fa0"), -0.5 * (f.Y > 1.75))


@mark("war_paint")
def _war(f):
    def one(f, X):
        a = stroke(f, [(0.0, 3.55), (1.35, 3.35)], 0.32, 0.12, X=X)
        b = stroke(f, [(0.0, 5.75), (1.1, 5.55)], 0.28, 0.08, X=X)
        return np.maximum(a, b)
    return colour_layer(f, mirrored(f, one) * 0.92, hx("#6a2f8a"), (grain(f, 3) - 0.5) * 0.4)


@mark("freckles")
def _freckles(f):
    cov = f.zeros()
    pts = [(1.2, 5.3), (1.7, 5.6), (1.0, 5.85), (1.55, 6.05), (2.1, 5.35), (3.3, 5.1), (4.7, 5.1),
           (6.8, 5.3), (6.3, 5.6), (7.0, 5.85), (6.45, 6.05), (5.9, 5.35), (2.6, 5.75), (5.4, 5.75)]
    for x, y in pts:
        cov = np.maximum(cov, ell(f, x, y, 0.08, 0.07))
    return colour_layer(f, cov * 0.55, hx("#7a4a30"))


@mark("stripes")
def _stripes(f):
    def one(f, X):
        a = stroke(f, [(0.35, 4.6), (0.6, 6.9)], 0.3, 0.08, X=X)
        b = stroke(f, [(0.95, 5.0), (1.1, 6.6)], 0.22, 0.05, X=X)
        return np.maximum(a, b)
    return colour_layer(f, mirrored(f, one), hx("#2f4f8a"), 0.3 * sstep(4.6, 5.4, f.Y) - 0.3 * sstep(5.5, 6.9, f.Y))


def part_mark(name):
    p = Part(tint=False)
    head_front(p, MARKS[name])
    return p


# ---------------------------------------------------------------------------
# Accessories (outer head layer). "mask" is tinted, the rest are fixed colours.
# ---------------------------------------------------------------------------

MASK_TOP = 5.05


def part_mask():
    p = Part()

    def fn(f):
        if f.name in ("top", "bottom"):
            return None
        X, Y = f.X, f.Y
        k = weave(f, 101, -0.1)
        if f.name == "front":
            top = MASK_TOP + 0.12 * np.abs(X - 4) ** 1.3 * 0.25
            a = np.clip((Y - top) * SCALE + 0.5, 0, 1)
            k += 0.5 * gauss(f, 4, 5.6, 0.32, 0.6) - 0.3 * gauss(f, 4.55, 5.9, 0.25, 0.5)  # over the nose
            k += crease(f, 2.2, 6.2, 3.6, 7.4, 0.18, 0.4) + crease(f, 5.8, 6.2, 4.4, 7.4, 0.18, 0.4)
            k += crease(f, 3.4, 6.9, 4.6, 6.9, 0.14, 0.25)
            k += A.round_edges(f, 0.25)
            k += 0.25 * np.exp(-((Y - top - 0.15) / 0.12) ** 2) - 0.3 * np.exp(-((Y - top - 0.38) / 0.06) ** 2)  # hem
            return k, a
        if f.side:
            top = MASK_TOP + 0.1 * (8 - f.MX) * 0.1
            a = np.clip((Y - top) * SCALE + 0.5, 0, 1)
            k += -0.15 - 0.3 * sstep(3.5, 0, f.MX) + crease(f, 1.0, 6.0, 3.5, 7.0, 0.18, 0.3)
            k += 0.25 * np.exp(-((Y - top - 0.15) / 0.12) ** 2) - 0.3 * np.exp(-((Y - top - 0.38) / 0.06) ** 2)
            return k, a
        a = np.clip((Y - MASK_TOP) * SCALE + 0.5, 0, 1)
        return k - 0.35 + seam(f, [(4, MASK_TOP), (4, 8)], 0.3), a

    hat_paint(p, fn)
    return p


METAL = hx("#55585e")


def part_glasses():
    p = Part(tint=False)

    def fn(f):
        if f.name == "front":
            rgb = np.zeros(f.X.shape + (3,), np.float32)
            a = f.zeros()
            for cx in A.EYE_XS:
                d = ell_d(f, cx, A.EYE_Y, 1.0, 0.9)
                lens = ell(f, cx, A.EYE_Y, 1.0, 0.9)
                glint = stroke(f, [(cx - 0.55, A.EYE_Y - 0.1), (cx - 0.15, A.EYE_Y - 0.55)], 0.16) * lens
                lens_rgb = np.array(hx("#9fb4c8"), np.float32)
                rgb = np.where(lens[..., None] > 0, lens_rgb, rgb)
                a = np.maximum(a, lens * 0.28)
                rgb = rgb * (1 - glint[..., None]) + 255 * glint[..., None]
                a = np.maximum(a, glint * 0.85)
                rim = np.clip(1 - np.abs(d - 1.0) * 1.0 * SCALE * 0.9 / 1.0 * 1.6, 0, 1)
                rgb = rgb * (1 - rim[..., None]) + tone(METAL, 0.4 * (f.Y < A.EYE_Y))[...] * rim[..., None]
                a = np.maximum(a, rim)
            bridge = stroke(f, bezier((3.5, A.EYE_Y - 0.3), (4, A.EYE_Y - 0.5), (4.5, A.EYE_Y - 0.3)), 0.12)
            rgb = rgb * (1 - bridge[..., None]) + np.array(METAL, np.float32) * bridge[..., None]
            a = np.maximum(a, bridge)
            for x0, x1 in ((0.0, 1.5), (6.5, 8.0)):
                arm = stroke(f, [(x0, A.EYE_Y - 0.35), (x1, A.EYE_Y - 0.35)], 0.12)
                rgb = rgb * (1 - arm[..., None]) + np.array(METAL, np.float32) * arm[..., None]
                a = np.maximum(a, arm)
            return rgb, a
        if f.side:
            arm = stroke(f, [(f.W, A.EYE_Y - 0.35), (2.8, A.EYE_Y - 0.25), (2.3, A.EYE_Y + 0.4)], 0.13)
            return rgb_of(METAL, f.X.shape), arm
        return None
    hat_paint(p, fn, light=True)
    return p


def part_shades():
    p = Part(tint=False)
    dark = hx("#17181c")

    def fn(f):
        if f.name == "front":
            a = f.zeros()
            k = f.zeros()
            for cx in A.EYE_XS:
                cov = box_cov(f, cx - 1.15, A.EYE_Y - 0.6, cx + 1.15, A.EYE_Y + 0.55) * ell(f, cx, A.EYE_Y - 0.1, 1.45, 0.9)
                a = np.maximum(a, cov)
                k = np.where(cov > 0, 0.6 * sstep(A.EYE_Y + 0.5, A.EYE_Y - 0.6, f.Y) - 0.2, k)
                k += 1.6 * stroke(f, [(cx - 0.7, A.EYE_Y - 0.25), (cx - 0.3, A.EYE_Y - 0.45)], 0.12) * cov
            bar = stroke(f, [(0.0, A.EYE_Y - 0.62), (8.0, A.EYE_Y - 0.62)], 0.18)
            a = np.maximum(a, bar)
            k = np.where(bar > 0.5, 0.5, k)
            return tone(dark, k), a
        if f.side:
            arm = stroke(f, [(f.W, A.EYE_Y - 0.55), (2.5, A.EYE_Y - 0.4)], 0.18)
            return rgb_of(dark, f.X.shape), arm
        return None
    hat_paint(p, fn)
    return p


def part_head_bandage():
    p = Part(tint=False)

    def fn(f):
        if f.name in ("top", "bottom"):
            return None
        y0, y1 = 0.95, 2.55
        rgb, a = wrap_rgb(f, y0, y1, 111)
        if f.name == "back":
            # the knot and two tails
            knot = ell(f, 4, 2.2, 0.55, 0.45)
            tails = np.maximum(stroke(f, [(3.9, 2.5), (3.3, 4.6)], 0.45, 0.25), stroke(f, [(4.1, 2.5), (4.8, 4.3)], 0.45, 0.25))
            extra = np.maximum(knot, tails)
            krgb = tone(WRAP_CLOTH, -0.25 + 0.3 * gauss(f, 3.9, 2.0, 0.25, 0.2) + (grain(f, 5) - 0.5) * 0.2)
            rgb = np.where(extra[..., None] > a[..., None], krgb, rgb)
            a = np.maximum(a, extra)
        return rgb, a
    hat_paint(p, fn)
    return p


def part_eyepatch():
    p = Part(tint=False)
    black = hx("#151518")

    def fn(f):
        if f.name == "front":
            cx = A.EYE_XS[1]
            patch = ell(f, cx, A.EYE_Y + 0.05, 1.05, 0.92)
            strap = np.maximum(stroke(f, [(cx - 0.6, A.EYE_Y - 0.6), (2.0, 2.0), (0.0, 1.2)], 0.22),
                               stroke(f, [(cx + 0.8, A.EYE_Y - 0.2), (8.0, A.EYE_Y - 0.4)], 0.22))
            k = 0.35 * gauss(f, cx - 0.3, A.EYE_Y - 0.3, 0.4, 0.3) - 0.3 * sstep(0.7, 1.0, ell_d(f, cx, A.EYE_Y + 0.05, 1.05, 0.92))
            k = np.where(patch > 0, k, 0.15)
            return tone(black, k), np.maximum(patch, strap)
        if f.side:
            y = 1.2 if f.name == "right" else A.EYE_Y - 0.4
            return rgb_of(tone(black, 0.1), f.X.shape), stroke(f, [(0, y + 0.3), (f.W, y)], 0.22)
        if f.name == "back":
            return rgb_of(tone(black, 0.1), f.X.shape), stroke(f, [(0, 1.5), (8, 1.5)], 0.22)
        return None
    hat_paint(p, fn)
    return p


def part_face_bandage():
    p = Part(tint=False)

    def fn(f):
        if f.name != "front":
            return None
        rgb, a = wrap_rgb(f, 4.85, 5.75, 121)
        a = a * box_cov(f, 1.0, 4.0, 7.1, 7.0)
        tab = box_cov(f, 5.2, 5.6, 6.5, 7.0)
        trgb = tone(WRAP_CLOTH, -0.1 + (grain(f, 7) - 0.5) * 0.2 - 0.35 * np.exp(-((f.Y - 5.75) / 0.06) ** 2))
        rgb = np.where(tab[..., None] > a[..., None], trgb, rgb)
        return rgb, np.maximum(a, tab)
    hat_paint(p, fn)
    return p


# ---------------------------------------------------------------------------
# Dojutsu layers, one per eye style (drawn full-bright over the head; see client/SharinganEyesLayer)
# ---------------------------------------------------------------------------

DOJUTSU_RED = hx("#c8121c")
DOJUTSU_RED_LIGHT = hx("#ec3a36")
DOJUTSU_RED_DARK = hx("#7a0a10")
DOJUTSU_RING = hx("#3a0508")
DOJUTSU_BLACK = hx("#0c0204")


def _dot(f, x, y):
    """One HD pixel's worth (a quarter model pixel) centred at (x, y): the Sharingan's marks are
    designed in quarter pixels around the iris centre, which sits on a pixel centre."""
    q = 0.125
    return A.crisp(box_cov(f, x - q, y - q, x + q, y + q))


def part_sharingan_eyes(style):
    """The Sharingan over one eye style (client/SharinganEyesLayer draws it opaque and full-bright
    over the head): a red iris that fits that style's opening exactly (the lid still cuts across
    it), a dark ring, a lighter lower half, a black pupil with three tomoe around it and a small
    highlight. Only the iris pixels you can see are drawn."""
    s = A.EYE_SHAPES[style]
    p = Part(tint=False)
    q = 0.25

    def fn(f):
        rgb = np.zeros(f.X.shape + (3,), np.float32)
        a = f.zeros()
        for cx in A.EYE_XS:
            vis = visible_iris(f, cx, s)
            ix, iy = A.iris_centre(cx, s)
            rx, ry = A.iris_radii(s)
            top, _ = A.lid_curves(f.X, cx, s)
            d = ell_d(f, ix, iy, rx, ry)
            v = (f.Y - iy) / ry
            col = rgb_of(DOJUTSU_RED, f.X.shape).copy()
            col = np.where((v > 0.15)[..., None], np.array(DOJUTSU_RED_LIGHT, np.float32), col)
            col = np.where((f.Y - top < 0.3)[..., None], np.array(DOJUTSU_RED_DARK, np.float32), col)
            col = np.where(((d > 0.8) & (v > 0.3))[..., None], np.array(DOJUTSU_RING, np.float32), col)
            # the pupil, and three tomoe around it (one above, two below: a turning pinwheel)
            marks = _dot(f, ix, iy)
            for dx, dy in ((0, -1), (-1, 1), (1, 1)):
                marks = np.maximum(marks, _dot(f, ix + dx * q, iy + dy * q))
            col = np.where((marks > 0)[..., None], np.array(DOJUTSU_BLACK, np.float32), col)
            shine = _dot(f, ix - q, iy - q)
            col = np.where((shine > 0)[..., None], np.array((255, 214, 206), np.float32), col)
            rgb = np.where((vis > 0)[..., None], col, rgb)
            a = np.maximum(a, vis)
        return rgb, a
    head_front(p, fn)
    return p


def part_byakugan_eyes(style):
    """The Byakugan over one eye style (drawn additively at full brightness): the iris glows pale
    inside that style's opening, and thin veins bulge from around the eye to the temple and cheek."""
    s = A.EYE_SHAPES[style]
    p = Part(tint=False)
    pale = hx("#e9e4f6")

    def fn(f):
        rgb = rgb_of(pale, f.X.shape).copy()
        a = f.zeros()
        for cx in A.EYE_XS:
            sgn = -1 if cx < 4 else 1
            disc = visible_iris(f, cx, s)
            a = np.maximum(a, disc * 0.6)
            # (drawn additively at full brightness, so the veins are a dim violet, thin and few)
            veins = f.zeros()
            for ang in (-0.6, 0.0, 0.6, 2.6):
                ang_o = ang if sgn > 0 else math.pi - ang
                x0, y0 = cx + 1.45 * math.cos(ang_o), A.EYE_Y + 0.8 * math.sin(ang_o)
                x1, y1 = cx + 1.9 * math.cos(ang_o + 0.2 * sgn), A.EYE_Y + 1.25 * math.sin(ang_o + 0.2 * sgn)
                x2, y2 = cx + 2.3 * math.cos(ang_o - 0.15 * sgn), A.EYE_Y + 1.5 * math.sin(ang_o - 0.15 * sgn)
                veins = np.maximum(veins, stroke(f, [(x0, y0), (x1, y1), (x2, y2)], 0.08, 0.02))
            veins = veins * (1 - A.eye_inside(f, cx, s))
            a = np.maximum(a, veins * 0.6)
            rgb = np.where((veins > disc)[..., None], np.array(hx("#4e4866"), np.float32), rgb)
        return rgb, a
    head_front(p, fn)
    return p


def write_dojutsu():
    """textures/entity/sharingan_eyes_<style>.png and byakugan_eyes_<style>.png, one per eye style."""
    for style in EYE_STYLES:
        part_sharingan_eyes(style).save(os.path.join(ENTITY_DIR, "sharingan_eyes_%s.png" % style))
        part_byakugan_eyes(style).save(os.path.join(ENTITY_DIR, "byakugan_eyes_%s.png" % style))


def mangekyo_fit():
    """The numbers client/SharinganEyesLayer needs to fit the Mangekyou pattern into each eye style:
    the iris centre (model pixels from the head's centre, the texture-right eye), its radius, and
    the band of it you can see (top and bottom, from the lid lines)."""
    from hdskin import F as Face
    out = {}
    for style in EYE_STYLES:
        s = A.EYE_SHAPES[style]
        cx = A.EYE_XS[1]
        ix, iy = A.iris_centre(cx, s)
        f = Face("head", "front", (8, 8, 8, 8))
        vis = visible_iris(f, cx, s)
        rows = np.nonzero(vis.any(axis=1))[0]
        top = rows.min() / SCALE
        bottom = (rows.max() + 1) / SCALE
        out[style] = (ix - 4, iy - 8, A.iris_radii(s)[0], top - 8, bottom - 8)
    return out


# ---------------------------------------------------------------------------
# The appearance tables (looks.json). Weights are relative.
# ---------------------------------------------------------------------------

SKIN = {
    "pale": "#f1cdb0", "light": "#e8bc98", "fair": "#e0ac84", "medium": "#c98e66",
    "tan": "#b07650", "brown": "#8a5a3c", "dark": "#6a4330",
}

DARK_SHIRTS = [["#23252c", 4], ["#1c2233", 3], ["#2a2e3a", 3], ["#20201f", 2], ["#2e2a27", 2], ["#283028", 2], ["#33343c", 2]]
DARK_PANTS = [["#24262e", 4], ["#1f2433", 3], ["#2e3038", 3], ["#2a2622", 2], ["#33302a", 1], ["#3a3d46", 2]]
SHIRT_STYLES = [["mesh", 3], ["tee", 3], ["longsleeve", 3], ["sleeveless", 2], ["wrap", 2], ["turtleneck", 2]]
PANTS_STYLES = [["long", 5], ["capri", 3], ["shorts", 1]]
WRAPS = [[0, 5], [1, 3], [2, 2], [3, 2]]  # bit 1 = leg wraps, bit 2 = arm wraps
NORMAL_EYES = [["round", 4], ["sharp", 3], ["wide", 2], ["narrow", 2]]
BROWS = [["thin", 4], ["fierce", 3], ["soft", 3], ["thick", 1]]
ACC_COLORS = [["#1f2638", 3], ["#22252b", 3], ["#3a3f48", 2], ["#2c2330", 1]]


def w(d, **weights):
    return [[d[k], v] for k, v in weights.items()]


CLANS = {
    "uchiha": {
        "skin": w(SKIN, pale=4, light=4, fair=2, medium=1),
        "hair_style": [["spiky", 4], ["duck", 4], ["long", 2], ["messy", 2], ["short", 2], ["ponytail", 2], ["hime", 1]],
        "hair_color": [["#1b1c22", 5], ["#141418", 4], ["#1e2230", 2], ["#2a2224", 1]],
        "eyes": [["sharp", 4], ["round", 3], ["narrow", 2], ["wide", 1]],
        "eye_color": [["#1e1a1f", 4], ["#2a1f24", 3], ["#2b2b33", 2]],
        "brows": [["fierce", 3], ["thin", 3], ["soft", 2]],
        "shirt": [["turtleneck", 3], ["longsleeve", 3], ["mesh", 2], ["tee", 2], ["sleeveless", 1]],
        "shirt_color": [["#1c2233", 4], ["#1a1a20", 3], ["#23293d", 2], ["#2a2e3a", 1]],
        "pants": PANTS_STYLES,
        "pants_color": [["#24262e", 3], ["#1f2433", 3], ["#d9d4c8", 1], ["#2e3038", 2]],
        "wraps": WRAPS,
        "mark": [["none", 10], ["tear_lines", 3], ["scar_eye", 1]],
        "accessory": [["none", 12], ["mask", 1], ["head_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "senju": {
        "skin": w(SKIN, fair=3, medium=4, tan=4, light=2, brown=2),
        "hair_style": [["long", 5], ["swept", 3], ["spiky", 2], ["short", 2], ["ponytail", 2], ["topknot", 2], ["messy", 1]],
        "hair_color": [["#3a2618", 4], ["#4a3020", 3], ["#2a1d16", 3], ["#5a3a24", 2], ["#d8d8dc", 1], ["#a8743a", 1]],
        "eyes": [["round", 4], ["sharp", 3], ["narrow", 2], ["wide", 1]],
        "eye_color": [["#3a2a1c", 4], ["#2c2018", 3], ["#5a3a1e", 2], ["#7a2a24", 1]],
        "brows": [["thin", 2], ["fierce", 2], ["soft", 2], ["thick", 1]],
        "shirt": [["longsleeve", 3], ["wrap", 3], ["mesh", 2], ["tee", 2], ["sleeveless", 1]],
        "shirt_color": [["#2e2a27", 3], ["#20201f", 3], ["#283028", 2], ["#2c2522", 2]],
        "pants": PANTS_STYLES,
        "pants_color": [["#2a2622", 3], ["#24262e", 3], ["#33302a", 2]],
        "wraps": WRAPS,
        "mark": [["none", 10], ["stripes", 2], ["diamond", 1], ["scar_nose", 1], ["freckles", 1]],
        "accessory": [["none", 12], ["head_bandage", 1], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "hyuga": {
        "skin": w(SKIN, pale=6, light=3, fair=2),
        "hair_style": [["hime", 4], ["long", 4], ["ponytail", 2], ["bun", 2], ["short", 1], ["topknot", 1]],
        "hair_color": [["#2b1d16", 4], ["#302018", 3], ["#1d1a1c", 3], ["#3a271c", 2], ["#2a2638", 1]],
        "eyes": [["pale", 1]],
        "eye_color": [["#e4dcef", 4], ["#d9d0ea", 3], ["#ece8f2", 2]],
        "brows": [["thin", 3], ["soft", 3], ["fierce", 1]],
        "shirt": [["wrap", 3], ["longsleeve", 3], ["turtleneck", 2], ["mesh", 1], ["tee", 1]],
        "shirt_color": [["#2c2a3c", 3], ["#23252c", 3], ["#3a3550", 2], ["#e8e2d6", 1]],
        "pants": [["long", 6], ["capri", 2]],
        "pants_color": [["#2c2c3e", 3], ["#24262e", 2], ["#47405e", 2], ["#ddd4bd", 1]],
        "wraps": WRAPS,
        "mark": [["none", 12], ["freckles", 1]],
        "accessory": [["none", 8], ["head_bandage", 4], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "uzumaki": {
        "skin": w(SKIN, fair=4, light=3, pale=2, medium=2, tan=1),
        "hair_style": [["spiky", 4], ["messy", 3], ["long", 3], ["bun", 2], ["ponytail", 2], ["hime", 1], ["short", 1]],
        "hair_color": [["#d8352a", 5], ["#e03a2e", 3], ["#b8261f", 3], ["#e85a2a", 2], ["#e8c24a", 2], ["#8a1e1e", 1]],
        "eyes": [["round", 4], ["wide", 3], ["sharp", 2]],
        "eye_color": [["#2f4a6e", 3], ["#3a6a9a", 3], ["#4a3a6a", 2], ["#3a5a4a", 1]],
        "brows": [["soft", 3], ["thin", 2], ["fierce", 2]],
        "shirt": [["tee", 3], ["mesh", 2], ["longsleeve", 2], ["sleeveless", 2], ["wrap", 1]],
        "shirt_color": [["#23252c", 3], ["#1c2233", 2], ["#2c2330", 2], ["#3a2224", 1]],
        "pants": PANTS_STYLES,
        "pants_color": [["#24262e", 3], ["#2a2b38", 3], ["#3b1c1e", 1], ["#e07a30", 1]],
        "wraps": WRAPS,
        "mark": [["none", 8], ["whiskers", 3], ["freckles", 1]],
        "accessory": [["none", 12], ["head_bandage", 1], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    "generic": {
        "skin": w(SKIN, pale=2, light=3, fair=3, medium=3, tan=3, brown=2, dark=2),
        "hair_style": [["spiky", 3], ["messy", 3], ["short", 3], ["ponytail", 2], ["long", 2], ["swept", 2],
                       ["bowl", 1], ["buzz", 2], ["topknot", 1], ["duck", 1], ["bun", 2], ["hime", 1]],
        "hair_color": [["#2a2522", 4], ["#4a3426", 4], ["#141414", 3], ["#6a4a2a", 2], ["#c7ccd2", 2],
                       ["#e3c26a", 2], ["#8a3a22", 1], ["#2f5a3a", 1], ["#2f3f6a", 1], ["#d98aa8", 1],
                       ["#7a7f88", 1], ["#5a3a6a", 1]],
        "eyes": NORMAL_EYES,
        "eye_color": [["#3a2a1e", 4], ["#2a2a2e", 3], ["#2d4f6a", 2], ["#3a5a3a", 2], ["#6a4a2a", 2], ["#5a5a62", 1]],
        "brows": BROWS,
        "shirt": SHIRT_STYLES,
        "shirt_color": DARK_SHIRTS,
        "pants": PANTS_STYLES,
        "pants_color": DARK_PANTS,
        "wraps": WRAPS,
        "mark": [["none", 9], ["fangs", 1], ["swirls", 1], ["scar_eye", 1], ["scar_nose", 1], ["war_paint", 1],
                 ["freckles", 1], ["stripes", 1], ["whiskers", 1]],
        "accessory": [["none", 9], ["mask", 2], ["glasses", 1], ["shades", 1], ["head_bandage", 1],
                      ["eyepatch", 1], ["face_bandage", 1]],
        "accessory_color": ACC_COLORS,
    },
    # Masters: one fixed look per folder (every seed decodes the same). A taijutsu master's green
    # training jumpsuit, leg warmers, a black bowl cut and thick brows. Our own simple parts only.
    "taijutsu_master": {
        "skin": [["#e0ac84", 1]],
        "hair_style": [["bowl", 1]],
        "hair_color": [["#141414", 1]],
        "eyes": [["round", 1]],
        "eye_color": [["#2a2a2e", 1]],
        "brows": [["thick", 1]],
        "shirt": [["turtleneck", 1]],
        "shirt_color": [["#3e7a2e", 1]],
        "pants": [["long", 1]],
        "pants_color": [["#3e7a2e", 1]],
        "wraps": [[1, 1]],
        "mark": [["none", 1]],
        "accessory": [["none", 1]],
        "accessory_color": ACC_COLORS,
    },
}

# A generated clan's members share a family look (seed bits 48-63 = the clan): most of them
# wear the family mark, and half share the family hair colour.
FAMILY = {
    "mark": [["fangs", 3], ["swirls", 3], ["war_paint", 2], ["stripes", 2], ["whiskers", 1], ["none", 3]],
    "mark_chance": 70,
    "hair_chance": 50,
}

TINTED_ACCESSORIES = ["mask"]



def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    parts = {"skin": part_skin(), "sandals": part_sandals(),
             "leg_wraps": part_wraps(LEGS, 6.0, 10.1), "arm_wraps": part_wraps(ARMS, 7.0, 10.9)}
    for s in SLEEVES:
        parts["shirt_" + s] = part_shirt(s)
    for s in PANTS:
        parts["pants_" + s] = part_pants(s)
    for s in EYE_STYLES:
        parts["eyes_" + s] = part_eyes(s)
        parts["iris_" + s] = part_iris(s)
    for s in BROW_STYLES:
        parts["brows_" + s] = part_brows(s)
    for i, name in enumerate(HAIR):
        parts["hair_" + name] = part_hair(name, 17 + i * 7)
    for name in MARKS:
        parts["mark_" + name] = part_mark(name)
    parts["acc_mask"] = part_mask()
    parts["acc_glasses"] = part_glasses()
    parts["acc_shades"] = part_shades()
    parts["acc_head_bandage"] = part_head_bandage()
    parts["acc_eyepatch"] = part_eyepatch()
    parts["acc_face_bandage"] = part_face_bandage()
    for name, part in parts.items():
        part.save(os.path.join(OUT_DIR, name + ".png"))
    write_dojutsu()
    for style, fit in mangekyo_fit().items():
        print("mangekyo fit %-7s x %.4f y %.4f r %.3f visible %.3f .. %.3f" % ((style,) + fit))
    looks = {"_comment": "Generated by tools/skingen/gen_parts.py: appearance tables per clan folder (weights are relative).",
             "clans": CLANS, "family": FAMILY, "tinted_accessories": TINTED_ACCESSORIES}
    with open(os.path.join(OUT_DIR, "looks.json"), "w") as f:
        json.dump(looks, f, indent=1)
        f.write("\n")
    print("wrote %d parts (%dx%d) to %s" % (len(parts), 64 * SCALE, 64 * SCALE, os.path.relpath(OUT_DIR, ROOT)))


if __name__ == "__main__":
    main()

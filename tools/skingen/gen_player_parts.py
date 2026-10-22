#!/usr/bin/env python3
"""Player appearance parts for NOIRM's character creation screen, in HD (hdskin.SCALE x 64x64).

The creator (client/CreatorScreen) builds a player's skin at runtime from the part ids saved in
their look (creator/PlayerLook). client/PlayerSkins stacks the layers, from bottom to top:

    skin_base -> jaw -> nose -> mouth -> muscle -> weathering -> scar -> marking -> underwear
    -> wraps -> iris -> eyes -> brows -> hair -> hair length

New characters wear only their underwear: clothes are gear items worn on top.
The face, hair, marking and wraps parts NPC ninjas use come from tools/skingen/gen_parts.py
(textures/entity/ninja/parts/). This script draws the ones only players pick:

    skin_base           the body in ink shading, no nose or mouth (those are parts)
    nose_*, jaw_*, mouth_*   face variants: exactly the pixels they change on the base head (crisp, tinted)
    muscle_*            the body redrawn (opaque) at a muscle level, over the soft base (tinted by skin tone)
    weather_*           grime, scuffs and small cuts (fixed colours, see-through)
    scar_*              body and face scars (fixed colours)
    under_*             underwear, tinted by the underwear colour
    hairlen_*           hair hanging down the back, tinted by hair colour

The look follows docs/ART_STYLE.md ("raw and tough": ink shadows, grit, carved anatomy).
All art is original and drawn by the code below (numpy + Pillow).
Run from the repo root:  python3 tools/skingen/gen_player_parts.py
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anatomy as A  # noqa: E402
import gen_parts as G  # noqa: E402
from hdskin import (Part, paint, tone, sstep, ell, ell_d, gauss, bezier, stroke, vnoise, fbm, grain,  # noqa: E402
                    tri, hx, ink, grit, box_cov, SCALE)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/player_parts")

ARMS = ("rarm", "larm")
LEGS = ("rleg", "lleg")


def feature_part(nose="none", mouth="none", jaw="none"):
    """A face feature as the exact pixels it changes on the base head: crisp, opaque, no blending mud."""
    p = Part()

    def fn(f):
        if f.name in ("top", "bottom", "back"):
            return None
        if f.side and jaw == "none":
            return None
        base = A.head_tones(f)
        k = A.head_tones(f, nose, mouth, jaw)
        changed = (np.abs(k - base) > 1e-3).astype(np.float32)
        return k, changed
    paint(p, "head", fn, light=False)
    return p


# ---------------------------------------------------------------------------
# The base body: soft muscle, no nose, mouth or jaw (those are parts)
# ---------------------------------------------------------------------------

BASE_MUSCLE = 0.25


def part_skin_base():
    p = Part()
    paint(p, "head", lambda f: (A.head_tones(f), 1.0), light=False)
    for box, seed in (("body", 13), ("rarm", 25), ("larm", 25), ("rleg", 39), ("lleg", 39)):
        paint(p, box, lambda f, sd=seed: (A.shade_body(f, BASE_MUSCLE, sd), 1.0), light=False)
    return p


NOSES = ("button", "straight", "broad", "hooked", "flat")
MOUTHS = ("neutral", "smile", "frown", "wide", "thin")
JAWS = ("soft", "square", "sharp", "round")


def part_jaw(name):
    return feature_part(jaw=name)


# ---------------------------------------------------------------------------
# Muscle definition: the whole body redrawn (opaque) at a muscle level, over the soft base
# ---------------------------------------------------------------------------

MUSCLE = {"lean": 0.7, "defined": 1.0, "heavy": 1.35}


def part_muscle(name):
    s = MUSCLE[name]
    p = Part()
    for box, seed in (("body", 13), ("rarm", 25), ("larm", 25), ("rleg", 39), ("lleg", 39)):
        paint(p, box, lambda f, sd=seed: (A.shade_body(f, s, sd), 1.0), light=False)
    return p


# ---------------------------------------------------------------------------
# Weathering: grime, dry-brush scuffs, splatter and small cuts (fixed colours, see-through)
# ---------------------------------------------------------------------------

GRIME = hx("#2e241c")
DUST = hx("#7a6e60")
CUT = hx("#7a1a18")
WEATHER = {"light": 0.55, "worn": 1.0, "battered": 1.5}


def part_weather(name):
    amount = WEATHER[name]
    p = Part(tint=False)

    def make(salt):
        def fn(f):
            if f.name in ("top", "bottom"):
                return None
            X, Y = f.X, f.Y
            low = sstep(2, 12, Y) if f.box != "head" else 0.4
            g = fbm(X * 1.4, Y * 1.4, salt)
            grime = sstep(0.74 - 0.05 * amount, 0.8, g + 0.1 * low) * (0.3 + 0.3 * grain(f, salt + 1))
            dry = (vnoise(X * 10, Y * 1.1, salt + 2) > 0.85) * (fbm(X, Y, salt + 3) > 0.62) * 0.3   # dry-brush streaks
            dust = (grain(f, salt + 4) > 1 - 0.015 * amount) * 0.45
            a = np.clip((grime + dry) * (0.5 + 0.35 * amount), 0, 0.75)
            rgb = np.where(a[..., None] > 0, tone(GRIME, (grain(f, salt + 5) - 0.5) * 0.6), 0)
            rgb = np.where(dust[..., None] > a[..., None], tone(DUST, 0.0 * X), rgb)
            a = np.maximum(a, dust)
            # cuts: short thin slashes, dark red with a lighter edge
            if amount >= 1.0:
                cuts = np.zeros_like(X)
                rng = np.random.RandomState(salt)
                for _ in range(int(2 * amount)):
                    x0, y0 = rng.uniform(0.5, f.W - 0.5), rng.uniform(0.5, f.H - 0.5)
                    if f.box == "head" and f.name == "front" and 3.5 < y0 < 5.5:
                        continue
                    ang = rng.uniform(-0.6, 0.6)
                    ln = rng.uniform(0.6, 1.3)
                    cuts = np.maximum(cuts, stroke(f, [(x0, y0), (x0 + ln * math.cos(ang), y0 + ln * math.sin(ang))], 0.12, 0.03))
                rgb = np.where(cuts[..., None] > 0.2, np.array(CUT, np.float32), rgb)
                a = np.maximum(a, cuts * 0.9)
            if f.box == "head" and f.name == "front":
                keep = 1 - sstep(0.9, 0.6, np.abs(Y - A.EYE_Y) / 1.0) * (np.abs(X - 4) < 3.8)   # keep the eyes clean
                a = a * keep
            return rgb, a
        return fn

    for i, box in enumerate(("head", "body", "rarm", "larm", "rleg", "lleg")):
        paint(p, box, make(101 + i * 17), light=False)
    return p


# ---------------------------------------------------------------------------
# Scars (fixed colours). The eye and nose scars are NPC parts (ninja/parts/mark_scar_*).
# ---------------------------------------------------------------------------

SCAR = hx("#c48a7c")
SCAR_DARK = hx("#6e3a32")


def scar_rgb(f, cov, centre_pts, stitches=()):
    """A raised scar: a pale ridge with a dark seam, plus optional stitch marks across it."""
    seam = stroke(f, centre_pts, 0.06)
    rgb = tone(SCAR, 0.3 * cov - 0.2 * (grain(f, 9) - 0.5))
    rgb = np.where(seam[..., None] > 0.3, np.array(SCAR_DARK, np.float32), rgb)
    a = np.maximum(cov * 0.9, seam)
    for (x0, y0, x1, y1) in stitches:
        st = stroke(f, [(x0, y0), (x1, y1)], 0.06)
        rgb = np.where(st[..., None] > 0.3, np.array(SCAR_DARK, np.float32), rgb)
        a = np.maximum(a, st * 0.85)
    return rgb, a


def part_scar(name):
    p = Part(tint=False)
    if name == "cheek":
        pts = [(5.3, 5.2), (6.2, 5.9), (7.0, 6.9)]

        def fn(f):
            if f.name != "front":
                return None
            return scar_rgb(f, stroke(f, pts, 0.24, 0.1), pts, [(5.6, 5.7, 5.95, 5.3), (6.3, 6.3, 6.65, 5.95)])
        paint(p, "head", fn, light=False)
    elif name == "chest":
        pts = [(6.9, 1.2), (4.6, 4.2), (1.6, 8.6)]

        def fn(f):
            if f.name != "front":
                return None
            st = [(x - 0.25, y - 0.2, x + 0.25, y + 0.2) for x, y in ((6.0, 2.4), (5.0, 3.7), (3.6, 5.6), (2.6, 7.1))]
            return scar_rgb(f, stroke(f, pts, 0.36, 0.12), pts, st)
        paint(p, "body", fn, light=False)
    elif name == "arm":
        pts = [(0.2, 3.0), (2.0, 4.0), (3.8, 4.6)]

        def fn(f):
            if f.name == "front":
                return scar_rgb(f, stroke(f, pts, 0.3, 0.14), pts, [(1.0, 3.2, 1.2, 3.9), (2.6, 3.9, 2.8, 4.6)])
            if f.name == "right":
                p2 = [(3.0, 2.8), (4.0, 3.0)]
                return scar_rgb(f, stroke(f, p2, 0.26, 0.2), p2)
            return None
        paint(p, "rarm", fn, light=False)
    elif name == "back":
        def fn(f):
            if f.name != "back":
                return None
            a1 = [(1.2, 2.0), (4.0, 5.6), (6.8, 9.6)]
            a2 = [(6.6, 2.4), (4.0, 6.0), (1.6, 9.0)]
            rgb1, al1 = scar_rgb(f, stroke(f, a1, 0.36, 0.14), a1)
            rgb2, al2 = scar_rgb(f, stroke(f, a2, 0.3, 0.12), a2)
            rgb = np.where((al2 > al1)[..., None], rgb2, rgb1)
            return rgb, np.maximum(al1, al2)
        paint(p, "body", fn, light=False)
    return p


# ---------------------------------------------------------------------------
# Underwear (tinted by the underwear colour)
# ---------------------------------------------------------------------------

UNDER = ("briefs", "fundoshi", "shorts", "bindings")

# Cloth is cut into these tones (k): a lit ridge, the cloth colour, half shade, shadow, a deep crevice.
CLOTH_TONES = (0.55, 0.0, -0.7, -1.5, -2.7)


def cloth_tones(f, h, seed, kd):
    """Cloth lit like the body under it (the same light and form), cut into CLOTH_TONES."""
    light = A.lit(f, h, None, A.ROUNDNESS[kd], relief=2.2)
    v = light + 0.03 * (fbm(f.X * 2, f.Y * 2, seed) - 0.5)
    k = np.full_like(v, CLOTH_TONES[3])
    k = np.where(v > 0.25, CLOTH_TONES[2], k)
    k = np.where(v > 0.6, CLOTH_TONES[1], k)
    k = np.where(v > 0.9, CLOTH_TONES[0], k)
    return k


def fold(f, x0, y0, x1, y1, w, depth, X=None):
    """A fold in the height field: a soft ridge (lit on top, shaded under) between two points."""
    X = f.X if X is None else X
    dx, dy = x1 - x0, y1 - y0
    ll = math.hypot(dx, dy) or 1e-6
    t = np.clip(((X - x0) * dx + (f.Y - y0) * dy) / (ll * ll), 0, 1)
    d = np.hypot(X - x0 - t * dx, f.Y - y0 - t * dy)
    return depth * np.clip(1 - (d / w) ** 2, 0, 1) * np.maximum(np.sin(np.clip(t, 0, 1) * math.pi), 0) ** 0.6


def band(f, y0, y1):
    return A.crisp(box_cov(f, -1, y0, 99, y1))


def part_under(name):
    p = Part()

    def waist(f):
        if f.name in ("top", "bottom"):
            return None
        X, Y = f.X, f.Y
        lx = f.MX if f.side else X
        h = np.zeros_like(X)
        a = np.zeros_like(X)
        top = 9.75
        if name == "fundoshi":
            # a twisted cloth rope at the hips, a panel hanging at the front, a strap at the back
            rope = band(f, 9.8, 10.6)
            twist = tri(X * 1.3 + Y * 2.4)
            panel = np.zeros_like(X)
            if f.name == "front":
                half = 1.35 + (Y - 10.6) * 0.35
                panel = A.crisp(np.clip((half - np.abs(X - 4)) * SCALE + 0.5, 0, 1)) * (Y > 10.4)
                h += fold(f, 3.5, 10.7, 3.2, 12.0, 0.35, 0.4) + fold(f, 4.5, 10.7, 4.8, 12.0, 0.35, 0.4)
            elif f.name == "back":
                half = 0.8 - (Y - 10.6) * 0.25
                panel = A.crisp(np.clip((half - np.abs(X - 4)) * SCALE + 0.5, 0, 1)) * (Y > 10.4)
                h += fold(f, 4.0, 10.6, 4.0, 12.0, 0.3, 0.3)
            k = cloth_tones(f, h, 401, "body")
            rk = np.where(twist > 0.62, CLOTH_TONES[0] * 0.6, np.where(twist < 0.2, CLOTH_TONES[3], CLOTH_TONES[1]))
            rk = np.where(np.abs(Y - 10.55) < 0.12, CLOTH_TONES[4], rk)
            k = np.where(rope > 0, rk, k)
            a = np.maximum(rope, panel)
            if f.name == "front":
                knot = A.crisp(ell(f, 4.0, 10.25, 0.55, 0.45))
                kd = ell_d(f, 4.0, 10.25, 0.55, 0.45)
                k = np.where(knot > 0, np.where(kd > 0.75, CLOTH_TONES[3], np.where((Y < 10.15) & (X < 4.05),
                                                                                     CLOTH_TONES[0], CLOTH_TONES[1])), k)
                a = np.maximum(a, knot)
            return k, a
        # briefs, shorts, bindings: a waistband, then the cloth to the bottom of the torso
        a = A.crisp(np.clip((Y - top) * SCALE + 0.5, 0, 1))
        if f.name == "front":
            # the cloth gathers at the crotch: folds pulling to the middle
            h += fold(f, 1.0, 10.9, 3.4, 11.9, 0.35, 0.35) + fold(f, 7.0, 10.9, 4.6, 11.9, 0.35, 0.35)
            h += 0.3 * np.clip(1 - ell_d(f, 4.0, 11.6, 1.0, 0.9) ** 2, 0, 1)        # the pouch
        elif f.name == "back":
            h += 0.3 * np.clip(1 - ell_d(f, 2.3, 11.5, 1.6, 1.1) ** 2, 0, 1) + 0.3 * np.clip(1 - ell_d(f, 5.7, 11.5, 1.6, 1.1) ** 2, 0, 1)
        k = cloth_tones(f, h, 403, "body")
        wb = band(f, top, 10.4)
        k = np.where(wb > 0, np.where(Y < top + 0.25, CLOTH_TONES[0], CLOTH_TONES[1] - 0.3), k)   # waistband
        k = np.where(np.abs(Y - 10.48) < 0.09, CLOTH_TONES[4], k)                             # its shadow
        k = np.where(wb > 0, np.where(tri(lx / 0.5) > 0.85, k - 0.5, k), k)                    # ribbing
        if f.name == "front" and name != "shorts":
            k = np.where(A.crisp(stroke(f, bezier((3.3, 10.6), (4, 11.9), (4.7, 10.6), 12), 0.14)) > 0, CLOTH_TONES[3], k)
        if f.side:
            k = np.where(A.crisp(stroke(f, [(2, 10.6), (2, 12)], 0.1)) > 0, CLOTH_TONES[3], k)
        if name == "bindings":
            # sarashi: strips of cloth wound around the chest, each lit on top and shaded under the next
            wrap = band(f, 1.9, 5.3)
            u = (Y + X * 0.1) / 0.68
            q = u - np.floor(u)
            wk = np.where(q < 0.25, CLOTH_TONES[0], np.where(q > 0.8, CLOTH_TONES[3], CLOTH_TONES[1]))
            wl = cloth_tones(f, None, 405, "body")
            wk = np.minimum(wk, np.where(wl <= CLOTH_TONES[3], CLOTH_TONES[2], 9))
            k = np.where(wrap > 0, wk, k)
            a = np.maximum(a, wrap)
        return k, a

    leg_end = {"fundoshi": 0.0, "briefs": 1.7, "shorts": 4.6, "bindings": 1.7}[name]

    def leg(f):
        X, Y = f.X, f.Y
        if f.name == "bottom":
            return None
        if f.name == "top":
            return np.full_like(X, CLOTH_TONES[2]), 1.0
        lx = A.lateral(f) if not f.side else f.MX
        if name == "fundoshi":
            if f.side:
                return None
            # the panel passes between the legs: a narrow strip on the inner edge of each leg
            inner = lx > 3.2 - Y * 0.4
            a = (inner & (Y < 1.6)).astype(np.float32)
            k = np.where(lx > 3.7, CLOTH_TONES[2], CLOTH_TONES[1])
            return k, a
        end = leg_end
        if name in ("briefs", "bindings") and f.side and A.is_outer(f):
            end = leg_end - 0.6
        a = A.crisp(np.clip((end - Y) * SCALE + 0.5, 0, 1))
        h = np.zeros_like(X)
        if name == "shorts":
            h += fold(f, 0.6, 1.0, 3.6, 2.2, 0.35, 0.35, X=lx) + fold(f, 0.5, 2.6, 3.3, 3.4, 0.3, 0.3, X=lx)
            h += fold(f, 3.2, 0.4, 3.6, 3.4, 0.3, 0.3, X=lx)
        else:
            h += fold(f, 0.6, 0.6, 3.4, 1.3, 0.3, 0.3, X=lx)
        k = cloth_tones(f, h, 409, "leg")
        hem = (Y > end - 0.3) & (Y < end)
        k = np.where(hem, CLOTH_TONES[2], k)
        k = np.where((Y > end - 0.36) & (Y < end - 0.27), CLOTH_TONES[3], k)
        return k, a

    paint(p, "body", waist, light=False)
    for leg_box in LEGS:
        paint(p, leg_box, leg, light=False)
    return p


# ---------------------------------------------------------------------------
# Hair length: hair hanging down the back (tinted by hair colour)
# ---------------------------------------------------------------------------

def part_hairlen(name):
    rows = {"nape": 2.2, "shoulder": 4.4, "waist": 10.6}[name]
    p = Part()
    salt = {"nape": 501, "shoulder": 511, "waist": 521}[name]

    def strands(f, y_end, lock_w=0.7):
        X, Y = f.X, f.Y
        lw = lock_w * G.LOCK_SCALE
        k, rnd = G.lock_shade(f, X, Y, lw, salt, y_band=1.0)
        u = X / lw + 0.18 * np.sin(Y * 1.3 + salt) + 0.12 * np.sin(Y * 0.55 + salt * 0.7)
        end = y_end - 0.7 * tri(u + 0.5) - 0.5 * rnd
        a = A.crisp(np.clip((end - Y) * SCALE + 0.5, 0, 1))
        k = np.where(Y > end - 1.2, np.minimum(k, G.HAIR_TONES[2]), k)      # darker towards the tips
        return k, a

    def hat(f):
        X, Y = f.X, f.Y
        if f.name == "back":
            k, a = strands(f, 8.6)
            return k, a * sstep(3.8, 4.4, Y)
        if f.side and True:
            a = sstep(4.6, 5.0, Y) * np.clip((2.6 - f.MX) * SCALE + 0.5, 0, 1)
            k = (vnoise(f.MX * 6, Y * 0.3, salt + 3) - 0.5) * 0.7 - 0.3
            return k, a
        return None

    def jacket(f):
        if f.name == "back":
            return strands(f, rows)
        if f.side and rows > 3:
            a = np.clip((min(rows, 3.2) - f.Y) * SCALE + 0.5, 0, 1) * np.clip((0.8 - f.MX) * SCALE + 0.5, 0, 1)
            return -0.5 + (grain(f, salt) - 0.5) * 0.3, a
        return None

    def ink_hair(fn, seed):
        def g(f):
            res = fn(f)
            if res is None:
                return None
            k, a = res
            return k, a
        return g
    paint(p, "hat", ink_hair(hat, salt + 7))
    paint(p, "jacket", ink_hair(jacket, salt + 9))
    return p


def main():
    parts = {"skin_base": part_skin_base()}
    for name in NOSES:
        parts["nose_" + name] = feature_part(nose=name)
    for name in MOUTHS:
        parts["mouth_" + name] = feature_part(mouth=name)
    for name in JAWS:
        parts["jaw_" + name] = part_jaw(name)
    for name in MUSCLE:
        parts["muscle_" + name] = part_muscle(name)
    for name in WEATHER:
        parts["weather_" + name] = part_weather(name)
    for name in ("cheek", "chest", "arm", "back"):
        parts["scar_" + name] = part_scar(name)
    for name in UNDER:
        parts["under_" + name] = part_under(name)
    for name in ("nape", "shoulder", "waist"):
        parts["hairlen_" + name] = part_hairlen(name)
    for name, part in parts.items():
        part.save(os.path.join(OUT_DIR, name + ".png"))
    print("wrote %d parts (%dx%d) to %s" % (len(parts), 64 * SCALE, 64 * SCALE, os.path.relpath(OUT_DIR, ROOT)))


if __name__ == "__main__":
    main()

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
    nose_*, jaw_*, mouth_*   face variants (shading drawn over the skin, tinted by skin tone)
    muscle_*            carved muscle definition (tinted by skin tone)
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
                    tri, hx, ink, grit, SCALE)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/player_parts")

ARMS = ("rarm", "larm")
LEGS = ("rleg", "lleg")


def inked_delta(f, dk, seed, gain=1.0):
    """A shading change cut into ink, as (k, alpha) to lay over the tinted skin."""
    d = ink(f, dk, seed, gain=gain, rough=0.05, shadow=(-0.13, -1.0), deep=(-0.5, -2.3), light=(0.12, 0.45), keep=0.2)
    return A.delta(d, dark=-2.4, light=1.2)


def face_delta(fn, seed):
    p = Part()
    paint(p, "head", lambda f: inked_delta(f, fn(f), seed) if f.name == "front" else None, light=False)
    return p


# ---------------------------------------------------------------------------
# The base body
# ---------------------------------------------------------------------------

def part_skin_base():
    return G.part_skin(nose=False, mouth=False)


# ---------------------------------------------------------------------------
# Face: nose, mouth (anatomy.py) and jaw
# ---------------------------------------------------------------------------

NOSES = ("button", "straight", "broad", "hooked", "flat")
MOUTHS = ("neutral", "smile", "frown", "wide", "thin")
JAWS = ("soft", "square", "sharp", "round")


def jaw_shading(f, name):
    X, Y = f.X, f.Y
    dk = np.zeros_like(X)
    if f.name == "front":
        ax = np.abs(X - 4)
        if name == "soft":
            dk += -0.18 * sstep(6.9, 8, Y) * sstep(1.5, 3.8, ax)
        elif name == "square":
            dk += -0.3 * sstep(7.0, 7.4, Y) * sstep(2.6, 3.0, ax)              # wide, square corners
            dk += -0.16 * sstep(7.55, 7.75, Y)
            dk += 0.16 * ell(f, 4, 7.45, 1.2, 0.3)                              # a broad chin
            dk += -0.2 * stroke(f, [(3.3, 7.75), (4.7, 7.75)], 0.1)
        elif name == "sharp":
            # a V-shaped jaw: the lower corners fall into shadow, a pointed lit chin
            v = (Y - 6.2) - (3.6 - ax) * 0.75
            dk += -0.42 * sstep(-0.15, 0.25, v)
            dk += 0.2 * ell(f, 4, 7.35, 0.55, 0.38)
            dk += -0.24 * stroke(f, [(2.0, 6.4), (3.4, 7.85)], 0.12) - 0.24 * stroke(f, [(6.0, 6.4), (4.6, 7.85)], 0.12)
        elif name == "round":
            dk += -0.16 * sstep(0.85, 1.0, ell_d(f, 4, 5.0, 3.9, 3.0))
            dk += 0.12 * ell(f, 4, 7.3, 1.0, 0.45)
        dk += -0.14 * stroke(f, bezier((3.3, 7.3), (4, 7.15), (4.7, 7.3)), 0.12)  # the chin's crease
    elif f.side:
        mx = f.MX
        strength = {"soft": 0.18, "square": 0.32, "sharp": 0.42, "round": 0.14}[name]
        y = 6.0 + (mx - 3.4) * (0.45 if name == "sharp" else 0.3)
        dk += -strength * sstep(y, y + 0.25, Y) * sstep(2.8, 3.6, mx)
        if name == "square":
            dk += -0.25 * stroke(f, [(3.4, 6.0), (3.4, 7.6)], 0.12)
    return dk


def part_jaw(name):
    p = Part()
    paint(p, "head", lambda f: inked_delta(f, jaw_shading(f, name), 301) if (f.name == "front" or f.side) else None,
          light=False)
    return p


# ---------------------------------------------------------------------------
# Muscle definition: carved, lean and wiry (inked shading over the base body)
# ---------------------------------------------------------------------------

MUSCLE = {"lean": 0.7, "defined": 1.0, "heavy": 1.35}


def muscle_body(f, s, heavy):
    X, Y = f.X, f.Y
    dk = np.zeros_like(X)
    if f.name == "front":
        for sx in (-1, 1):
            dk += 0.22 * s * ell(f, 4 + 1.9 * sx, 2.35, 1.6, 1.0)                  # pecs, lit
            dk += -0.5 * s * stroke(f, bezier((4 + 0.2 * sx, 3.5), (4 + 2.0 * sx, 4.45), (4 + 3.8 * sx, 3.0)), 0.5, 0.12)
            dk += -0.25 * s * stroke(f, [(4 + 3.7 * sx, 1.4), (4 + 3.0 * sx, 3.4)], 0.2, 0.05)  # where the chest meets the arm
            for i in range(4):                                                       # serratus
                y = 4.2 + 0.5 * i
                dk += -0.3 * s * stroke(f, [(4 + 3.95 * sx, y), (4 + 3.15 * sx, y + 0.35)], 0.16, 0.03)
            dk += -0.34 * s * stroke(f, [(4 + 3.2 * sx, 5.6), (4 + 2.6 * sx, 9.0), (4 + 0.9 * sx, 11.8)], 0.36, 0.14)  # obliques
        dk += -0.42 * s * stroke(f, [(4, 1.4), (4, 3.9)], 0.2)
        dk += -0.42 * s * stroke(f, [(4, 4.3), (4, 8.3)], 0.2)
        for y0, y1 in ((4.5, 5.6), (5.85, 6.95), (7.2, 8.25)):
            for sx in (-1, 1):
                dk += 0.2 * s * ell(f, 4 + 0.95 * sx, (y0 + y1) / 2 - 0.1, 0.7, (y1 - y0) / 2 * 0.85)
            dk += -0.34 * s * stroke(f, [(2.85, y1 + 0.1), (5.15, y1 + 0.1)], 0.18)
    elif f.name == "back":
        dk += -0.45 * s * stroke(f, [(4, 0.5), (4, 11.5)], 0.22, 0.14)              # spine
        for sx in (-1, 1):
            dk += 0.22 * s * ell(f, 4 + 1.6 * sx, 0.8, 1.5, 0.75)                  # traps
            dk += -0.3 * s * stroke(f, bezier((4 + 0.5 * sx, 1.6), (4 + 2.0 * sx, 1.7), (4 + 3.7 * sx, 0.9)), 0.2, 0.08)
            dk += 0.18 * s * ell(f, 4 + 1.9 * sx, 3.0, 1.1, 1.15)                  # shoulder blades
            dk += -0.4 * s * stroke(f, bezier((4 + 0.8 * sx, 1.9), (4 + 0.9 * sx, 3.8), (4 + 2.7 * sx, 4.7)), 0.24, 0.08)
            dk += 0.16 * s * ell(f, 4 + 2.6 * sx, 6.0, 1.0, 1.8)                   # lats
            dk += -0.32 * s * stroke(f, bezier((4 + 3.8 * sx, 4.2), (4 + 2.6 * sx, 7.2), (4 + 1.0 * sx, 8.8)), 0.3, 0.08)
            dk += -0.2 * s * stroke(f, [(4 + 0.5 * sx, 8.5), (4 + 0.5 * sx, 11.0)], 0.3, 0.1)  # lower back columns
    elif f.side:
        dk += 0.14 * s * ell(f, 2, 5.5, 1.3, 2.5)
        for i in range(3):
            dk += -0.25 * s * stroke(f, [(0.6, 4.0 + 0.55 * i), (3.4, 4.3 + 0.55 * i)], 0.12, 0.04)  # ribs
    elif f.name == "top" and heavy:
        dk += 0.2
    return dk


def muscle_arm(f, s):
    X, Y = f.X, f.Y
    dk = np.zeros_like(X)
    if f.name == "top":
        return dk + 0.25 * s
    if f.name == "bottom":
        return None
    dk += 0.22 * s * ell(f, 2, 1.0, 1.9, 1.5)                                       # deltoid cap
    dk += -0.45 * s * stroke(f, bezier((0.0, 2.2), (2, 3.0), (4.0, 2.2)), 0.26, 0.26)
    if f.name == "front":
        dk += 0.24 * s * ell(f, 2, 3.9, 1.15, 1.25)                                  # biceps
        dk += -0.36 * s * stroke(f, bezier((0.9, 5.2), (2, 5.7), (3.1, 5.2)), 0.22, 0.12)
        dk += 0.16 * s * ell(f, 1.6, 7.4, 1.0, 1.3)                                  # forearm
        dk += -0.4 * s * stroke(f, [(1.5, 6.6), (1.9, 7.9), (2.5, 9.5)], 0.08, 0.05)  # veins
        dk += -0.32 * s * stroke(f, [(1.9, 7.9), (3.0, 8.9)], 0.06, 0.03)
        dk += -0.26 * s * stroke(f, [(2.5, 9.5), (2.4, 9.9)], 0.06)
    elif f.name == "back":
        dk += 0.2 * s * ell(f, 1.4, 3.6, 0.85, 1.3) + 0.2 * s * ell(f, 2.7, 3.9, 0.8, 1.1)  # triceps heads
        dk += -0.34 * s * stroke(f, [(2.05, 2.8), (2.05, 5.1)], 0.16, 0.06)
        dk += -0.3 * s * stroke(f, [(1.0, 5.3), (3.0, 5.3)], 0.2, 0.08)
        dk += 0.14 * s * ell(f, 2, 7.6, 1.2, 1.5)
    else:
        dk += -0.36 * s * stroke(f, [(2.0, 2.9), (2.15, 5.5)], 0.18, 0.06)
        dk += -0.24 * s * stroke(f, [(1.0, 6.8), (2.4, 9.6)], 0.14, 0.04)          # forearm groove
    return dk


def muscle_leg(f, s):
    X, Y = f.X, f.Y
    dk = np.zeros_like(X)
    if f.name in ("top", "bottom"):
        return None
    if f.name == "front":
        dk += 0.22 * s * ell(f, 2.3, 2.5, 1.2, 2.0)                                  # quads
        dk += 0.2 * s * ell(f, 1.0, 4.8, 0.65, 0.75)                                 # teardrop above the knee
        dk += -0.4 * s * stroke(f, [(2.4, 0.4), (1.7, 3.5), (1.25, 5.3)], 0.18, 0.06)
        dk += -0.3 * s * stroke(f, [(3.6, 1.2), (3.2, 4.8)], 0.16, 0.05)
        dk += -0.36 * s * stroke(f, bezier((1.0, 6.75), (2, 7.05), (3.0, 6.75)), 0.22, 0.14)
        dk += -0.22 * s * stroke(f, [(3.0, 7.5), (2.7, 10.0)], 0.2, 0.06)            # beside the shin
    elif f.name == "back":
        dk += 0.22 * s * ell(f, 1.3, 7.4, 0.8, 1.35) + 0.22 * s * ell(f, 2.75, 7.5, 0.75, 1.25)  # calves
        dk += -0.36 * s * stroke(f, [(2.0, 6.5), (2.0, 8.6)], 0.16, 0.05)
        dk += -0.34 * s * stroke(f, bezier((0.5, 8.6), (2, 9.4), (3.5, 8.6)), 0.24, 0.08)
        dk += -0.3 * s * stroke(f, [(2.0, 0.6), (2.0, 4.6)], 0.16, 0.06)            # hamstrings
    else:
        dk += -0.3 * s * stroke(f, [(2.0, 0.5), (2.2, 5.0)], 0.16, 0.05)
        dk += 0.14 * s * ell(f, 1.2, 7.4, 0.9, 1.2)
    return dk


def part_muscle(name):
    s = MUSCLE[name]
    p = Part()

    def wrap(fn, seed):
        def g(f):
            dk = fn(f)
            return None if dk is None else inked_delta(f, dk, seed)
        return g
    paint(p, "body", wrap(lambda f: muscle_body(f, s, name == "heavy"), 311), light=False)
    for a in ARMS:
        paint(p, a, wrap(lambda f: muscle_arm(f, s), 313), light=False)
    for leg in LEGS:
        paint(p, leg, wrap(lambda f: muscle_leg(f, s), 317), light=False)
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


def part_under(name):
    p = Part()
    leg_end = {"fundoshi": 1.9, "briefs": 1.7, "shorts": 4.6, "bindings": 1.7}[name]

    def waist(f):
        if f.name in ("top", "bottom"):
            return None
        X, Y = f.X, f.Y
        k = G.weave(f, 401, 0.0)
        a = np.zeros_like(X)
        if name == "fundoshi":
            # a twisted rope belt and a cloth panel hanging at the front and back
            rope = np.clip((Y - 9.75) * SCALE + 0.5, 0, 1) * np.clip((10.55 - Y) * SCALE + 0.5, 0, 1)
            k = np.where(rope > 0, -0.1 + 0.55 * tri((X * 1.4 + Y * 2.2)) - 0.5 * sstep(0.25, 0.0, tri(X * 1.4 + Y * 2.2)), k)
            a = np.maximum(a, rope)
            if f.name in ("front", "back"):
                panel = (np.abs(X - 4) < 1.6 + (Y - 10.5) * 0.4) * (Y > 10.4)
                a = np.maximum(a, panel * 1.0)
                k = np.where((rope == 0) & panel, k + G.crease(f, 3.4, 10.6, 3.6, 12, 0.14, 0.4) + G.crease(f, 4.6, 10.6, 4.4, 12, 0.14, 0.4), k)
            if f.name == "front":
                knot = ell(f, 4.0, 10.2, 0.5, 0.42)
                k = np.where(knot > 0.3, 0.2 + 0.3 * gauss(f, 3.9, 10.05, 0.2, 0.15) - 0.6 * sstep(0.7, 1.0, ell_d(f, 4.0, 10.2, 0.5, 0.42)), k)
                a = np.maximum(a, knot)
            return k, a
        a = sstep(9.7, 9.8, Y)
        band = (Y < 10.45)
        k = np.where(band, 0.32 + 0.1 * tri(X / 0.25) - 0.1, k)                          # waistband
        k += -0.5 * np.exp(-((Y - 10.45) / 0.06) ** 2) + 0.3 * np.exp(-((Y - 9.8) / 0.06) ** 2)
        if f.name == "front":
            k += G.crease(f, 1.2, 10.8, 3.2, 11.9, 0.14, 0.4) + G.crease(f, 6.8, 10.8, 4.8, 11.9, 0.14, 0.4)
            k += -0.2 * stroke(f, bezier((3.2, 10.6), (4, 11.8), (4.8, 10.6)), 0.12) * (name != "shorts")
            if name == "shorts":
                k += G.seam(f, [(4, 10.5), (4, 12)], 0.3, False)
        if f.side:
            k += G.seam(f, [(2, 10.5), (2, 12)], 0.25, False)
        if name == "bindings":
            # chest wraps (sarashi) around the ribs
            wrap = np.clip((Y - 1.9) * SCALE + 0.5, 0, 1) * np.clip((5.4 - Y) * SCALE + 0.5, 0, 1)
            u = (Y + X * 0.12) / 0.6
            q = u - np.floor(u)
            wk = 0.2 - 0.6 * sstep(0.75, 1.0, q) + (grain(f, 403) - 0.5) * 0.2
            k = np.where(wrap > 0, wk, k)
            a = np.maximum(a, wrap)
        return k, a

    def leg(f):
        X, Y = f.X, f.Y
        if f.name == "bottom":
            return None
        if f.name == "top":
            return G.weave(f, 405), 1.0
        if name == "fundoshi":
            if f.side:
                return None
            # the panel passes between the legs: only the inner half of each leg
            inner = (X > 2.0) if (f.box == "rleg") == (f.name == "front") else (X < 2.0)
            end = leg_end - 0.8 * np.abs(X - 2) * 0.6
            a = inner * np.clip((end - Y) * SCALE + 0.5, 0, 1)
            return G.weave(f, 407, 0.0) + G.crease(f, 2.0, 0.0, 2.0, 1.8, 0.14, 0.3), a
        # leg openings: higher on the outside of the hip for briefs
        end = leg_end
        if name in ("briefs", "bindings") and f.side:
            outer = (f.box == "rleg") == (f.name == "right")
            end = leg_end - (0.9 * (f.MX / 4) if outer else 0) * 0
            if outer:
                end = leg_end - 0.7
        a = np.clip((end - Y) * SCALE + 0.5, 0, 1)
        k = G.weave(f, 409, 0.0) + A.round_edges(f, 0.2)
        k += G.hem(f, end if np.ndim(end) == 0 else leg_end, True, 0.35)
        if name == "shorts":
            k += G.crease(f, 0.5, 1.4, 3.5, 2.2, 0.16, 0.4) + G.crease(f, 0.6, 3.0, 3.2, 3.4, 0.14, 0.3)
        return k, a

    paint(p, "body", G.inked(waist, 411, gain=1.3))
    for leg_box in LEGS:
        paint(p, leg_box, G.inked(leg, 413, gain=1.3))
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
        tooth = tri(X / lock_w + 0.2 * vnoise(X, 0 * Y, salt))
        end = y_end - 0.55 * tooth
        a = np.clip((end - Y) * SCALE + 0.5, 0, 1)
        streak = vnoise(X * 6 + 0.3 * Y, Y * 0.3, salt + 1) - 0.5
        gap = tri(X / lock_w)
        k = streak * 0.7 - 0.6 * sstep(0.3, 0.0, gap) - 0.4 * sstep(y_end - 1.6, y_end, Y)
        band = np.exp(-((Y - (1.0 + 0.3 * np.sin(X * 1.9))) / 0.3) ** 2) * (vnoise(X * 4, Y, salt + 2) > 0.45)
        k += 0.9 * band
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
            return ink(f, k, seed, gain=1.2, rough=0.06, light=(0.35, 0.7), keep=0.35), a
        return g
    paint(p, "hat", ink_hair(hat, salt + 7))
    paint(p, "jacket", ink_hair(jacket, salt + 9))
    return p


def main():
    parts = {"skin_base": part_skin_base()}
    for name in NOSES:
        parts["nose_" + name] = face_delta(lambda f, n=name: A.nose_shading(f, n), 201)
    for name in MOUTHS:
        parts["mouth_" + name] = face_delta(lambda f, n=name: A.mouth_shading(f, n), 211)
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

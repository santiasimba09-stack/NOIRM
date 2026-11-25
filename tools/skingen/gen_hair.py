#!/usr/bin/env python3
"""NOIRM's 3D hair: the strand texture and every hair style, as data (docs/ART_STYLE.md, "Hair, eyes and
bodies rework").

Hair is built from LOCKS. A lock is a short polyline (2-6 points, in head model pixels) with a width and a
thickness at each point: a chunky, tapered, angled box when it's short (the 3D clumps of spiky and messy
styles), a wide flat ribbon when it's long (the draped sheets of long styles). Each lock has:

    pts    [[x, y, z], ...]   model pixels relative to the head pivot (the neck): the head is the box
                              x -4..4, y -8..0, z -4..4; y points down, the face looks towards -z,
                              +x is the character's left
    w, d   [..]               width (across) and thickness (outwards) at each point; 0 = a sharp tip
    out    [x, y, z]          which way the lock's flat side faces (usually away from the head's centre)
    shade  0.5..1             darker locks sit underneath, lighter ones on top
    cell   0..3               which strand pattern of the texture it shows
    tex    "clump" | "sheet"  clumps are opaque; sheets have jagged strand tips (cut out)
    swing  0..1               how much the lock swings with motion (hanging hair); 0 = stiff
    pivot  [x, y, z]          where a swinging lock turns around (default: its first point)
    hang   true               hair that hangs below a hat brim: still drawn under hats and helmets

The game (com.noirm.creator.HairStyles + client.HairLayer) reads assets/noirm/hair/styles/<id>.json, bakes each
style into a vertex list once, and draws it on the head with the strand texture tinted by the hair colour.
The scalp under the 3D hair is painted on the skin (ninja/parts/scalp_<name>.png, by gen_parts.py), named by
each style's "scalp".

    python3 tools/skingen/gen_hair.py              # texture + styles
    python3 tools/skingen/gen_hair.py --preview out.png [style ...]   # also draw a contact sheet

All art is original and drawn by this code.
"""
import argparse
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hdskin import hash01, vnoise, fbm, sstep  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")
STYLE_DIR = os.path.join(ASSETS, "hair/styles")
TEXTURE = os.path.join(ASSETS, "textures/entity/hair/strands.png")

# ---------------------------------------------------------------------------
# The strand texture (gray tint layer: 128 + 40k, the same rule as the skin parts)
# ---------------------------------------------------------------------------

TEX = 256
CLUMP_CELLS = 4          # x 0..127: four 32-px clump cells, v = root (top) .. tip (bottom)
SHEET_CELLS = 2          # x 128..255: two 64-px sheet cells with jagged tips
TONES = (-2.3, -1.35, -0.55, 0.15, 0.9)    # ink, dark, mid, light, sheen


def cut_tones(v):
    k = np.full_like(v, TONES[0])
    k = np.where(v > -0.95, TONES[1], k)
    k = np.where(v > -0.3, TONES[2], k)
    k = np.where(v > 0.3, TONES[3], k)
    k = np.where(v > 1.25, TONES[4], k)
    return k


def lock_light(q, rnd):
    """Light across one lock (q 0 lit edge .. 1 dark edge): a rounded strand bundle, inked edges."""
    v = 0.75 - 1.25 * q + (rnd - 0.5) * 0.35
    v = v - 1.6 * sstep(0.8, 0.97, q) - 1.2 * sstep(0.12, 0.0, q)
    return v


def texture():
    k = np.zeros((TEX, TEX), np.float32)
    a = np.zeros((TEX, TEX), np.float32)
    ys = (np.arange(TEX, dtype=np.float32) + 0.5) / TEX          # v
    for c in range(CLUMP_CELLS):
        x0 = c * 32
        xs = (np.arange(32, dtype=np.float32) + 0.5) / 32
        X, V = np.meshgrid(xs, ys)
        seed = 31 + c * 7
        # strands: the cell is one lock, split by a few thinner strand gaps that converge to the tip
        wob = 0.05 * np.sin(V * 9 + c) + 0.03 * np.sin(V * 23 + 2 * c)
        q = np.clip(X + wob * (1 - V), 0, 1)
        v = lock_light(q, 0.5 + 0.2 * (c - 1.5) / 1.5)
        n = 3 + c % 2
        sub = q * n
        sq = sub - np.floor(sub)
        gap = (sq > 0.86) & (np.floor(sub) < n - 1) & (V > 0.08 + 0.1 * hash01(np.floor(sub), 0 * sub, seed))
        v = np.where(gap, v - 1.1, v)
        fine = (vnoise(X * 40, V * 3, seed + 1) > 0.78) & (V > 0.1)
        v = np.where(fine, v - 0.6, v)
        # a broken sheen band high on the lock
        band = 0.3 + 0.06 * np.sin(q * 7 + c)
        sheen = (np.abs(V - band) < 0.045 + 0.03 * (q < 0.45)) & (q > 0.1) & (q < 0.62) \
            & (vnoise(X * 12, V * 4, seed + 2) > 0.3)
        v = np.where(sheen, 2.0, v)
        # darker at the root (the head's shadow) and toward the tip
        v = v - 1.4 * sstep(0.14, 0.0, V) - 0.7 * sstep(0.7, 1.0, V)
        k[:, x0:x0 + 32] = cut_tones(v)
        a[:, x0:x0 + 32] = 1.0
    for c in range(SHEET_CELLS):
        x0 = 128 + c * 64
        xs = (np.arange(64, dtype=np.float32) + 0.5) / 64
        X, V = np.meshgrid(xs, ys)
        seed = 71 + c * 13
        locks = 4 + c
        u = X * locks + 0.18 * np.sin(V * 6 + c * 2) * V
        li = np.floor(u)
        q = u - li
        rnd = hash01(li, 0 * li, seed)
        v = lock_light(q, rnd)
        fine = (vnoise(X * 70, V * 4, seed + 1) > 0.8) & (V > 0.06)
        v = np.where(fine, v - 0.55, v)
        band = 0.22 + 0.05 * np.sin(li * 1.9 + c) + 0.04 * (rnd - 0.5)
        sheen = (np.abs(V - band) < 0.03 + 0.02 * (q < 0.4)) & (q > 0.12) & (q < 0.6) & (rnd > 0.2)
        v = np.where(sheen, 2.0, v)
        v = v - 1.2 * sstep(0.08, 0.0, V) - 0.6 * sstep(0.75, 1.0, V)
        # jagged tips: each lock ends in a point at its own length
        end = 0.86 + 0.12 * hash01(li, 0 * li + 1, seed)
        tip = end - 0.14 * (1 - np.abs(q - 0.5) * 2) ** 0.8
        tooth = np.abs(q - 0.5) * 2                      # 0 at the lock's middle, 1 at its edges
        cov = (V < tip + 0.12 * (1 - tooth)) & (V < 0.995)
        k[:, x0:x0 + 64] = cut_tones(v)
        a[:, x0:x0 + 64] = cov.astype(np.float32)
    g = np.clip(np.round(128 + 40 * k), 0, 255).astype(np.uint8)
    al = np.clip(np.round(a * 255), 0, 255).astype(np.uint8)
    rgba = np.stack([g, g, g, al], -1)
    rgba[al == 0, :3] = 0
    os.makedirs(os.path.dirname(TEXTURE), exist_ok=True)
    Image.fromarray(rgba, "RGBA").save(TEXTURE, optimize=True)
    return rgba


# ---------------------------------------------------------------------------
# Vector helpers (model pixels; y down, front = -z, +x = the character's left)
# ---------------------------------------------------------------------------

CENTRE = np.array([0.0, -4.4, 0.3])


def v3(*a):
    return np.array(a, np.float64)


def unit(v):
    v = np.asarray(v, np.float64)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def direction(az, el):
    """az: 0 front, 90 the character's left, 180 back, 270 right; el: 90 straight up, 0 level, <0 down."""
    a, e = math.radians(az), math.radians(el)
    return unit(v3(math.sin(a) * math.cos(e), -math.sin(e), -math.cos(a) * math.cos(e)))


def surface(az, el, inset=0.35):
    """A point on the head's rounded box (a superellipsoid around CENTRE), pushed in by inset."""
    d = direction(az, el)
    rx, ry, rz = 4.25, 4.0, 4.25
    p = 6.0
    # solve |x/rx|^p + |y/ry|^p + |z/rz|^p = 1 along d
    s = (abs(d[0] / rx) ** p + abs(d[1] / ry) ** p + abs(d[2] / rz) ** p) ** (-1.0 / p)
    return CENTRE + d * (s - inset)


def outward(p):
    return unit(np.asarray(p) - CENTRE)


def rot(v, axis, deg):
    """Rodrigues rotation of v around axis."""
    axis = unit(axis)
    t = math.radians(deg)
    v = np.asarray(v, np.float64)
    return v * math.cos(t) + np.cross(axis, v) * math.sin(t) + axis * np.dot(axis, v) * (1 - math.cos(t))


# ---------------------------------------------------------------------------
# Building locks
# ---------------------------------------------------------------------------

class Style:
    def __init__(self, sid, scalp, seed, label=""):
        self.id = sid
        self.scalp = scalp
        self.locks = []
        self.rng = random.Random(seed)
        self.label = label

    def r(self, a, b):
        return self.rng.uniform(a, b)

    def add(self, pts, w, d, out=None, shade=0.9, cell=None, tex="clump", swing=0.0, pivot=None, hang=False):
        pts = [np.asarray(p, np.float64) for p in pts]
        if out is None:
            out = outward(pts[0])
        lock = {
            "pts": [[round(float(c), 3) for c in p] for p in pts],
            "w": [round(float(x), 3) for x in w],
            "d": [round(float(x), 3) for x in d],
            "out": [round(float(c), 3) for c in unit(out)],
            "shade": round(float(min(1.0, max(0.35, shade))), 3),
            "cell": int(self.rng.randrange(CLUMP_CELLS if tex == "clump" else SHEET_CELLS) if cell is None else cell),
            "tex": tex,
        }
        if swing > 0:
            lock["swing"] = round(float(swing), 3)
            lock["pivot"] = [round(float(c), 3) for c in (pts[0] if pivot is None else pivot)]
        if hang:
            lock["hang"] = True
        self.locks.append(lock)
        return lock

    def spike(self, az, el, dirv, length, width, depth=None, bend=0.0, bend_axis=None, tip=0.0, shade=None,
              inset=0.5, segs=3, twist=0.0, root=None):
        """A tapered clump from the head's surface: straight or curving (bend degrees over its length)."""
        p0 = surface(az, el, inset) if root is None else np.asarray(root, np.float64)
        d0 = unit(dirv)
        out = outward(p0)
        axis = unit(np.cross(d0, out)) if bend_axis is None else unit(bend_axis)
        if np.linalg.norm(axis) < 1e-6:
            axis = v3(1, 0, 0)
        pts = [p0]
        step = length / segs
        dcur = d0
        for i in range(segs):
            dcur = rot(dcur, axis, bend / segs)
            pts.append(pts[-1] + dcur * step)
        depth = width * 0.55 if depth is None else depth
        ws, ds = [], []
        for i in range(segs + 1):
            t = i / segs
            f = (1 - t) ** 0.9 * (1 - tip) + tip if i < segs else tip
            bulge = 1 + 0.18 * math.sin(math.pi * t)
            ws.append(width * f * bulge)
            ds.append(depth * (f * 0.9 + 0.1) * bulge if i < segs else depth * tip)
        o = rot(out, d0, twist) if twist else out
        return self.add(pts, ws, ds, out=o, shade=self.r(0.72, 1.0) if shade is None else shade)

    def sheet(self, pts, width, depth=0.55, tip=0.7, out=None, shade=0.85, swing=0.0, pivot=None, hang=False,
              widths=None, cell=None):
        """A draped ribbon of hair through pts (jagged strand tips, cut out)."""
        n = len(pts)
        ws = widths if widths is not None else [width * (1 - (1 - tip) * (i / (n - 1)) ** 1.4) for i in range(n)]
        ds = [depth * (1 - 0.5 * i / (n - 1)) for i in range(n)]
        return self.add(pts, ws, ds, out=out, shade=shade, tex="sheet", swing=swing, pivot=pivot, hang=hang, cell=cell)

    def json(self):
        return {"id": self.id, "scalp": self.scalp, "locks": self.locks}


# ---------------------------------------------------------------------------
# Shared pieces
# ---------------------------------------------------------------------------

UP = v3(0, -1, 0)
DOWN = v3(0, 1, 0)
BACK = v3(0, 0, 1)
FRONT = v3(0, 0, -1)
LEFT = v3(1, 0, 0)
RIGHT = v3(-1, 0, 0)


def radial(whorl_az=180, whorl_el=72, add=(0, 0, 0)):
    """Hair flowing away from a whorl over the skull (the natural fall), plus a push."""
    w = surface(whorl_az, whorl_el, 0.0)
    push = v3(*add)

    def flow(p, out):
        return unit(np.asarray(p) - w) + push
    return flow


def fixed(v):
    vv = unit(v)
    return lambda p, out: vv


def face_gap(az, el, half=52, top=40):
    """True where the face is (no shell locks there: bangs and side locks frame it instead)."""
    a = (az + 180) % 360 - 180
    return abs(a) < half and el < top


def shell(s, rows, flow, length=(2.8, 3.6), width=(2.9, 3.5), thick=(1.25, 1.65), lift=0.18, bend=-30, tip=0.35,
          shade=(0.62, 0.98), inset=0.25, jitter=0.2, skip=face_gap, twist=12):
    """The main mass: rows of chunky locks lying along the skull like shingles (each row overlaps the next),
    flowing along flow(p, out). Higher rows are lighter (they catch the light), lower rows darker."""
    for el, n, *span in rows:
        a0, a1 = span if span else (0, 360)
        for i in range(n):
            az = a0 + (a1 - a0) * (i + 0.5 + s.r(-jitter, jitter)) / n
            if skip is not None and skip(az, el):
                continue
            e = el + s.r(-4, 4)
            p = surface(az, e, inset)
            out = outward(p)
            f = flow(p, out)
            d = f - out * np.dot(f, out)
            if np.linalg.norm(d) < 1e-3:
                d = BACK
            d = unit(unit(d) + out * lift)
            t = min(1.0, max(0.0, (e + 30) / 115))
            sh = (shade[0] + (shade[1] - shade[0]) * t) * s.r(0.9, 1.04)
            s.spike(az, e, d, s.r(*length), s.r(*width), depth=s.r(*thick), bend=bend * 0.45 * s.r(0.7, 1.25), tip=tip,
                    shade=sh, root=p, twist=s.r(-twist, twist))


def bangs(s, n, length, width, spread=(-50, 50), el=50, droop=1.0, sweep=0.0, bend=-22, tip=0.25, forward=0.12,
          shade=(0.86, 1.0), thick=1.2, inset=0.7):
    """Fringe locks from the front hairline falling over the forehead (sweep > 0 pushes them to the character's
    left). They are rooted on the forehead itself, just under the top edge, so they drape over the face."""
    for i in range(n):
        t = (i + 0.5) / n
        az = spread[0] + (spread[1] - spread[0]) * t + s.r(-3, 3)
        x = max(-3.5, min(3.5, 4.4 * math.sin(math.radians(az))))
        p = v3(x, -8.15 + s.r(-0.15, 0.15), -4.0 - thick * 0.5 + 0.15)
        d = unit(DOWN * droop + FRONT * forward + LEFT * sweep + v3(x * 0.04, 0, 0))
        s.spike(az, el, d, length * s.r(0.85, 1.15), width * s.r(0.9, 1.1), depth=thick, bend=bend * 0.3 * s.r(0.6, 1.2),
                tip=tip, shade=s.r(*shade), root=p, twist=s.r(-8, 8))


def sideburns(s, length=2.4, width=2.0, el=8, tip=0.3, back=0.1, thick=1.0):
    for side in (-1, 1):
        az = (62 * side) % 360
        p = surface(az, el, 0.7)
        out = outward(p)
        d = unit(DOWN + out * 0.1 + BACK * back)
        s.spike(az, el, d, length, width, depth=thick, bend=-10, tip=tip, shade=s.r(0.68, 0.8), root=p)


def nape(s, n=6, length=2.6, width=2.6, el=-8, tip=0.3, shade=(0.55, 0.72), spread=(115, 245), out_k=0.12, thick=1.2):
    for i in range(n):
        az = spread[0] + (spread[1] - spread[0]) * (i + 0.5) / n + s.r(-4, 4)
        p = surface(az, el + s.r(-4, 4), 0.7)
        out = outward(p)
        d = unit(DOWN + out * out_k)
        s.spike(az, el, d, length * s.r(0.85, 1.15), width * s.r(0.9, 1.1), depth=thick, bend=-15, tip=tip,
                shade=s.r(*shade), root=p)


ROWS = [(80, 5), (56, 9), (32, 12), (8, 13), (-14, 9, 100, 260)]
ROWS_SHORT = [(80, 5), (56, 9), (32, 12), (8, 12)]


def back_sheets(s, length, n=4, top_el=40, width=3.6, depth=0.7, swing=0.85, spread=(130, 230), tip=0.75,
                shade=(0.65, 0.85), segs=6, flare=0.5):
    """Long hair down the back: ribbons from the crown over the back of the head, then hanging over the back."""
    for i in range(n):
        az = spread[0] + (spread[1] - spread[0]) * (i + 0.5) / n + s.r(-3, 3)
        p0 = surface(az, top_el, 0.5)
        side = math.sin(math.radians(az))
        # over the back of the skull, then out to the back of the torso (z ~ 2.4) and down
        p1 = surface(az, top_el * 0.3, -0.25)
        p2 = surface(az, -25, -0.35)
        pts = [p0, p1, p2]
        x = p2[0] + side * flare * 0.6
        z = max(2.6, p2[2] - 0.6)
        y = p2[1]
        remain = length
        k = 0
        while remain > 0.01 and k < segs:
            stepy = min(remain, length / max(1, segs - 3))
            y += stepy
            x += side * flare * 0.25
            z = max(2.5, z - 0.35)
            pts.append(v3(x, y, z))
            remain -= stepy
            k += 1
        s.sheet(pts, width * s.r(0.9, 1.1), depth=depth, tip=tip, out=v3(side * 0.3, -0.1, 1), shade=s.r(*shade),
                swing=swing, pivot=p1, hang=True)


def side_curtains(s, length, width=2.6, el=10, swing=0.7, front=True, tip=0.6, az_off=58, shade=(0.75, 0.92),
                  over_shoulder=True):
    """Locks framing the face, down past the jaw (in front of the shoulders when long)."""
    for side in (-1, 1):
        az = (az_off * side) % 360
        p0 = surface(az, el + 18, 0.45)
        p1 = surface(az, el - 15, -0.2)
        sx = side * 4.15
        pts = [p0, p1, v3(sx, p1[1] + 1.4, p1[2] - 0.2)]
        y = pts[-1][1]
        steps = max(2, int(length / 1.6))
        for i in range(steps):
            y += length / steps
            dz = -0.4 - 0.25 * i if (front and over_shoulder and y > 0.5) else -0.1
            pts.append(v3(sx + side * (0.15 * i if y > 0.5 else 0), y, pts[-1][2] + (dz if y > 0 else 0.05)))
        s.sheet(pts, width, depth=0.55, tip=tip, out=v3(side, 0, -0.15), shade=s.r(*shade), swing=swing, pivot=p1,
                hang=True)


def tail(s, root, length, width, segs=7, swing=1.0, droop=(0.0, 1.0, -0.12), tex="sheet", tip=0.4, braid=False,
         shade=0.88, up=0.0):
    """A ponytail or braid: a thick lock from root hanging down, swinging with motion."""
    pts = [np.asarray(root, np.float64)]
    d = unit(v3(0, -up, 1.0) if up else v3(0, 0.5, 1))
    for i in range(segs):
        t = (i + 1) / segs
        g = (0.12 + 2.0 * t ** 1.6) if up else (0.55 + 2.2 * t)
        d = unit(d + unit(droop) * g)    # gravity takes over after the first segments
        pts.append(pts[-1] + d * length / segs)
    if braid:
        # a braid: short fat segments, each a separate clump turned the other way
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            mid = (a + b) / 2
            for j, off in enumerate((-0.45, 0.45)):
                side = unit(np.cross(b - a, v3(0, 0, 1) if abs(b[2] - a[2]) < 0.9 * np.linalg.norm(b - a) else v3(1, 0, 0)))
                q0 = a + side * off * width * 0.35
                q1 = b - side * off * width * 0.35
                w = width * (1 - 0.35 * i / segs)
                s.add([q0, mid + side * off * 0.1, q1], [w * 0.55, w * 0.75, w * 0.45], [w * 0.55, w * 0.7, w * 0.4],
                      out=v3(0, 0, 1), shade=shade - 0.12 * j, swing=swing, pivot=pts[0], hang=True)
        # the tie and the tuft at the end
        end = pts[-1]
        s.add([end, end + v3(0, 1.4, 0.2), end + v3(0, 2.4, 0.3)], [width * 0.5, width * 0.7, 0.2], [width * 0.4, width * 0.5, 0.2],
              out=v3(0, 0, 1), shade=shade, tex="sheet", swing=swing, pivot=pts[0], hang=True)
        return
    n = len(pts)
    ws = [width * (0.7 + 0.5 * math.sin(math.pi * min(1, i / (n - 1) * 1.6))) * (1 - 0.55 * i / (n - 1)) for i in range(n)]
    ws[-1] = width * tip * 0.7
    ds = [w * 0.7 for w in ws]
    if tex == "sheet":
        s.add(pts, ws, ds, out=v3(0, 0, 1), shade=shade, tex="sheet", swing=swing, pivot=pts[0], hang=True)
        s.add([p + v3(0.15, 0.1, 0.25) for p in pts], [w * 0.75 for w in ws], [w * 0.5 for w in ds], out=v3(0.3, 0, 1),
              shade=shade - 0.15, tex="sheet", swing=swing, pivot=pts[0], hang=True)
    else:
        s.add(pts, ws, ds, out=v3(0, 0, 1), shade=shade, swing=swing, pivot=pts[0], hang=True)


def tie(s, root, size=1.1):
    """The band tying a tail: a small dark box."""
    r = np.asarray(root, np.float64)
    s.add([r + v3(0, 0, -0.3), r + v3(0, 0, 0.5)], [size * 1.4, size * 1.4], [size * 1.2, size * 1.2], out=v3(0, -1, 0),
          shade=0.4, cell=0)


def bun(s, centre, radius, n=10, shade=(0.7, 0.95)):
    """A bun: short curved locks wound around a ball."""
    c = np.asarray(centre, np.float64)
    for i in range(n):
        a = 2 * math.pi * i / n
        el = s.r(-50, 60)
        d = unit(v3(math.cos(a) * math.cos(math.radians(el)), -math.sin(math.radians(el)), math.sin(a) * math.cos(math.radians(el))))
        root = c + d * radius * 0.35
        tang = unit(np.cross(d, v3(0, 1, 0.2)))
        s.spike(0, 0, unit(d * 0.5 + tang), radius * 1.6, radius * 1.15, depth=radius * 0.8, bend=-120,
                bend_axis=unit(np.cross(tang, d)), tip=0.25, shade=s.r(*shade), root=root, segs=4)


# ---------------------------------------------------------------------------
# The styles
# ---------------------------------------------------------------------------

def style_spiky():
    s = Style("spiky", "spiky", 11, "Spiky")
    # a flat under-layer, then big sharp spikes bursting up and back from the crown
    shell(s, ROWS, radial(), length=(2.6, 3.2), lift=0.1, tip=0.3, shade=(0.55, 0.8))
    for el, n, ln in ((84, 3, 4.6), (62, 7, 5.0), (40, 9, 4.6), (18, 10, 4.0), (-4, 7, 3.2)):
        for i in range(n):
            az = 360 * (i + 0.5 * (n % 2) + s.r(-0.18, 0.18)) / n
            if face_gap(az, el, 58, 32):
                continue
            p = surface(az, el, 0.7)
            out = outward(p)
            d = unit(out * 0.9 + UP * 0.5 + BACK * 0.55)
            s.spike(az, el, d, ln * s.r(0.85, 1.15), s.r(2.8, 3.4), depth=s.r(1.5, 1.9), bend=-24 * s.r(0.6, 1.3),
                    tip=0.0, shade=s.r(0.72, 1.0), root=p, twist=s.r(-15, 15))
    bangs(s, 5, 3.6, 2.8, spread=(-55, 55), el=52, forward=0.35, bend=-8, tip=0.0)
    sideburns(s, 2.6, 2.0, tip=0.0)
    return s


def style_messy():
    s = Style("messy", "messy", 13, "Messy")
    shell(s, ROWS, radial(add=(0, -0.1, 0)), length=(2.6, 3.4), lift=0.35, tip=0.2, jitter=0.35, bend=-10,
          twist=35)
    for i in range(16):
        az = s.r(0, 360)
        el = s.r(-5, 85)
        if face_gap(az, el, 55, 35):
            continue
        p = surface(az, el, 0.7)
        out = outward(p)
        d = unit(out * 0.8 + v3(s.r(-0.7, 0.7), s.r(-0.5, 0.4), s.r(-0.3, 0.8)))
        s.spike(az, el, d, s.r(2.4, 3.4), s.r(2.4, 3.0), depth=1.5, bend=s.r(-50, 40), tip=0.1, shade=s.r(0.7, 1.0),
                root=p, twist=s.r(-30, 30))
    bangs(s, 6, 3.2, 2.5, spread=(-55, 55), el=50, forward=0.3, bend=-30, sweep=0.2, tip=0.15)
    sideburns(s, 2.6, 2.2, tip=0.1)
    nape(s, 6, 2.4, 2.6, tip=0.15)
    return s


def style_short():
    s = Style("short", "short", 17, "Short")
    shell(s, ROWS_SHORT, radial(add=(0, 0.2, 0.2)), length=(2.2, 2.8), lift=0.06, tip=0.5, bend=-34)
    bangs(s, 5, 2.4, 2.8, spread=(-46, 46), el=52, forward=0.25, bend=-35, sweep=0.35, tip=0.4)
    sideburns(s, 1.8, 1.8, tip=0.5)
    nape(s, 5, 1.8, 2.6, el=-6, tip=0.5)
    return s


def style_swept():
    s = Style("swept", "swept", 19, "Swept")
    shell(s, ROWS, fixed(LEFT * 1.0 + BACK * 0.6 + DOWN * 0.2), length=(2.8, 3.6), lift=0.2, tip=0.25, bend=-26)
    # one big swept lock across the forehead to the character's left, and smaller ones under it
    for az, el, ln, w, sh in ((-38, 60, 7.0, 3.8, 1.0), (-18, 50, 5.6, 3.1, 0.86), (8, 54, 4.2, 2.8, 0.8)):
        p = surface(az, el, 0.5)
        s.spike(0, 0, unit(LEFT * 1.0 + DOWN * 0.55 + FRONT * 0.15), ln, w, depth=1.7, bend=48,
                bend_axis=v3(0, 0, -1), tip=0.0, shade=sh, root=p, segs=4)
    sideburns(s, 2.4, 2.0, tip=0.2)
    nape(s, 6, 2.4, 2.6, tip=0.2)
    return s


def style_bowl():
    s = Style("bowl", "bowl", 23, "Bowl cut")
    # a smooth rounded dome, every lock falling straight down to a blunt line at the brow
    shell(s, [(82, 5), (58, 11), (34, 14), (10, 15, 60, 300)], radial(180, 90, add=(0, 0.6, 0)), length=(2.8, 3.2),
          width=(3.0, 3.4), lift=0.08, tip=0.92, bend=-22, jitter=0.05, twist=4, skip=None, shade=(0.66, 0.96))
    bangs(s, 8, 3.1, 2.6, spread=(-60, 60), forward=0.12, bend=0, tip=0.95, thick=1.1)
    return s


def style_buzz():
    s = Style("buzz", "buzz", 29, "Buzz cut")
    shell(s, ROWS_SHORT, radial(180, 60), length=(1.2, 1.5), width=(2.8, 3.2), thick=(0.55, 0.7), lift=0.0,
          tip=0.8, bend=-15, inset=0.42, skip=lambda az, el: face_gap(az, el, 48, 44), twist=6)
    return s


def style_topknot():
    s = Style("topknot", "topknot", 31, "Topknot")
    shell(s, ROWS_SHORT, radial(180, 85, add=(0, -0.4, 0)), length=(2.8, 3.4), lift=0.0, tip=0.4, bend=-20)
    top = surface(180, 72, -0.2)
    bun(s, top + v3(0, -1.3, 0.2), 1.6, n=9)
    tie(s, top + v3(0, -0.3, 0.2), 1.0)
    tail(s, top + v3(0, -1.2, 1.3), 4.0, 2.0, segs=4, swing=0.6, droop=(0, 1, 0.8))
    bangs(s, 4, 2.0, 2.6, spread=(-40, 40), el=55, droop=0.5, forward=0.5, bend=-40, tip=0.4)
    sideburns(s, 2.0, 1.8, tip=0.4)
    return s


def style_duck():
    s = Style("duck", "duck", 37, "Back spikes")
    # sleek over the top and sides, long bangs framing the face, a burst of spikes at the back
    shell(s, ROWS_SHORT, radial(180, 40, add=(0, 0, 0.6)), length=(3.0, 3.6), lift=0.05, tip=0.3, bend=-28)
    for el, n, ln in ((64, 5, 5.0), (42, 7, 5.6), (20, 7, 5.0), (-2, 6, 4.0)):
        for i in range(n):
            az = 112 + 136 * (i + 0.5) / n + s.r(-6, 6)
            p = surface(az, el, 0.7)
            d = unit(BACK * 1.2 + UP * 0.65 + outward(p) * 0.45)
            s.spike(az, el, d, ln * s.r(0.85, 1.15), s.r(2.8, 3.4), depth=s.r(1.5, 1.9), bend=-26, tip=0.0,
                    shade=s.r(0.62, 0.98), root=p, twist=s.r(-15, 15))
    for side in (-1, 1):
        for j, (az, ln, w) in enumerate(((54, 6.6, 2.7), (34, 5.0, 2.5))):
            p = surface(az * side % 360, 44, 0.6)
            d = unit(DOWN * 1.0 + FRONT * 0.12 + v3(side * 0.14, 0, 0))
            s.spike(0, 0, d, ln, w, depth=1.2, bend=12, bend_axis=v3(0, 0, side), tip=0.05, shade=0.94 - 0.08 * j,
                    root=p, segs=4)
    bangs(s, 2, 3.2, 2.4, spread=(-14, 14), el=54, forward=0.3, bend=-20, tip=0.0)
    return s


def style_bun():
    s = Style("bun", "bun", 41, "Bun")
    shell(s, ROWS_SHORT, radial(0, 50, add=(0, 0, 0.9)), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    c = surface(180, 28, -1.5)
    bun(s, c, 2.0, n=12)
    tie(s, surface(180, 28, 0.2), 1.0)
    bangs(s, 4, 2.6, 2.4, spread=(-48, 48), el=52, droop=0.8, forward=0.3, bend=-30, sweep=-0.25, tip=0.3)
    side_curtains(s, 2.6, width=1.6, el=8, swing=0.4, front=False, tip=0.5)
    return s


def style_hime():
    s = Style("hime", "hime", 43, "Hime cut")
    shell(s, ROWS_SHORT, radial(180, 85, add=(0, 0.3, 0)), length=(2.8, 3.4), lift=0.0, tip=0.6, bend=-24)
    bangs(s, 8, 3.5, 2.5, spread=(-58, 58), forward=0.08, bend=0, tip=0.96, thick=1.0)
    for side in (-1, 1):
        for j, az in enumerate((64, 78)):
            p = surface(az * side % 360, 26, 0.5)
            s.spike(0, 0, unit(DOWN + v3(side * 0.06, 0, 0)), 7.4 - j * 0.6, 2.9, depth=1.0, bend=0, tip=0.95,
                    shade=0.92 - 0.1 * j, root=p, segs=3)
    back_sheets(s, 10.0, n=5, width=3.9, tip=0.95, swing=0.7)
    return s


def style_ponytail():
    s = Style("ponytail", "ponytail", 47, "Ponytail")
    shell(s, ROWS_SHORT, radial(0, 55, add=(0, 0.1, 1.0)), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    root = surface(180, 14, -0.4)
    tie(s, root + v3(0, 0, 0.1), 1.15)
    tail(s, root + v3(0, 0.1, 0.5), 9.5, 3.0, segs=7, swing=1.0)
    bangs(s, 5, 2.6, 2.4, spread=(-50, 50), el=52, droop=0.8, forward=0.3, bend=-28, sweep=0.3, tip=0.3)
    return s


def style_high_ponytail():
    s = Style("high_ponytail", "ponytail", 53, "High ponytail")
    shell(s, ROWS_SHORT + [(-12, 8, 110, 250)], radial(180, 62, add=(0, -0.2, 0)), length=(3.0, 3.6), lift=-0.05,
          tip=0.35, bend=-24)
    root = surface(178, 64, -0.5)
    tie(s, root, 1.2)
    tail(s, root + v3(0, -0.3, 0.6), 11.0, 3.1, segs=8, swing=1.0, up=0.9)
    bangs(s, 5, 2.8, 2.4, spread=(-52, 52), el=52, droop=0.85, forward=0.3, bend=-30, sweep=-0.25, tip=0.2)
    return s


def style_braid():
    s = Style("braid", "ponytail", 59, "Braid")
    shell(s, ROWS_SHORT, radial(0, 55, add=(0, 0.2, 1.0)), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    root = surface(180, 4, -0.6)
    tie(s, root + v3(0, 0.6, 0.6), 1.0)
    tail(s, root + v3(0, 0.3, 0.5), 10.5, 2.6, segs=7, swing=0.9, braid=True)
    bangs(s, 5, 2.5, 2.3, spread=(-50, 50), el=52, droop=0.8, forward=0.3, bend=-26, tip=0.3)
    return s


def style_long():
    s = Style("long", "long", 61, "Long")
    shell(s, ROWS_SHORT, radial(180, 88, add=(0, 0.4, 0.2)), length=(3.2, 3.8), width=(3.2, 3.8), thick=(0.9, 1.15),
          lift=-0.02, tip=0.6, bend=-24, jitter=0.1, twist=5)
    back_sheets(s, 13.0, n=5, width=3.9, tip=0.6, swing=0.85)
    side_curtains(s, 9.0, width=2.7, el=10, swing=0.7, front=True, tip=0.55)
    bangs(s, 5, 3.2, 2.6, spread=(-46, 46), el=52, forward=0.25, bend=-18, sweep=0.12, tip=0.2)
    return s


def style_side_bangs():
    s = Style("side_bangs", "swept", 67, "Side bangs")
    shell(s, ROWS, fixed(RIGHT * 0.9 + BACK * 0.6 + DOWN * 0.3), length=(2.8, 3.6), lift=0.12, tip=0.25,
          bend=-28)
    # a long sheet of bangs sweeping over the character's right eye, down to the cheek
    for (p0, p1, rest, w, sh, cell) in (
            (surface(30, 62, 0.3), surface(-5, 40, -0.5), [v3(-1.6, -5.0, -4.8), v3(-2.9, -3.2, -4.75), v3(-3.7, -1.5, -4.55)], 3.8, 1.0, 0),
            (surface(10, 58, 0.3), surface(-24, 38, -0.45), [v3(-3.0, -4.6, -4.65), v3(-4.0, -2.8, -4.35)], 3.0, 0.84, 1)):
        s.sheet([p0, p1] + rest, w, depth=0.9, tip=0.45, out=v3(-0.1, -0.2, -1), shade=sh, cell=cell)
    nape(s, 7, 3.6, 2.6, el=-8, tip=0.0)
    sideburns(s, 3.2, 2.2, tip=0.0)
    return s


def style_shoulder_messy():
    s = Style("shoulder_messy", "messy", 71, "Shoulder length")
    shell(s, ROWS_SHORT, radial(180, 80, add=(0, 0.3, 0)), length=(3.0, 3.6), lift=0.25, tip=0.15, jitter=0.35,
          bend=-15, twist=30)
    for i in range(12):
        az = 72 + 216 * (i + 0.5) / 12 + s.r(-6, 6)
        p0 = surface(az, 18, 0.4)
        out = outward(p0)
        side = math.sin(math.radians(az))
        p1 = p0 + DOWN * 2.6 + out * 0.3
        p2 = p1 + DOWN * 2.6 + out * 0.55 + v3(side * 0.35, 0, 0.2)
        p3 = p2 + DOWN * s.r(1.6, 2.8) + out * 0.7
        s.sheet([p0, p1, p2, p3], s.r(2.8, 3.4), depth=0.8, tip=0.3, out=out, shade=s.r(0.58, 0.88), swing=0.5,
                pivot=p0, hang=True)
    bangs(s, 6, 3.4, 2.6, spread=(-55, 55), el=50, forward=0.3, bend=-30, sweep=-0.15, tip=0.1)
    return s


def style_twin_buns():
    s = Style("twin_buns", "bun", 73, "Twin buns")
    shell(s, ROWS_SHORT, radial(180, 88, add=(0, 0.2, 0)), length=(2.8, 3.4), lift=0.0, tip=0.35, bend=-26)
    for side in (-1, 1):
        c = surface(140 * side % 360, 66, -1.2) + v3(side * 0.4, -0.5, 0)
        bun(s, c, 1.65, n=9)
    bangs(s, 6, 2.7, 2.3, spread=(-55, 55), el=52, droop=0.85, forward=0.3, bend=-25, tip=0.25)
    return s


def style_wild_mane():
    s = Style("wild_mane", "messy", 79, "Wild mane")
    # a heavy, wild mane: big jagged locks bursting out and falling down the back
    shell(s, ROWS, radial(180, 75, add=(0, 0.4, 0.3)), length=(3.6, 4.6), width=(3.2, 3.8), thick=(1.5, 1.9),
          lift=0.4, tip=0.0, jitter=0.3, bend=-12, twist=25)
    back_sheets(s, 12.0, n=6, width=4.0, tip=0.35, swing=0.8, spread=(115, 245), flare=1.4)
    for i in range(8):
        az = 115 + 130 * (i + 0.5) / 8
        p = surface(az, -12, 0.3)
        d = unit(DOWN + outward(p) * 0.8)
        s.spike(az, -12, d, s.r(5.0, 7.0), s.r(2.8, 3.4), depth=1.6, bend=s.r(-20, 20), tip=0.0,
                shade=s.r(0.55, 0.8), root=p)
    bangs(s, 5, 3.9, 2.7, spread=(-55, 55), el=52, forward=0.45, bend=-12, tip=0.0)
    side_curtains(s, 6.5, width=2.9, el=12, swing=0.6, front=True, tip=0.3)
    return s


def style_undercut():
    s = Style("undercut", "buzz", 83, "Undercut")
    # shaved sides (the scalp shows), a long top swept back
    shell(s, [(84, 4), (64, 8, -95, 95), (64, 5, 120, 240), (46, 8, -80, 80)], fixed(BACK * 1.0 + UP * 0.15),
          length=(4.0, 5.0), lift=0.3, tip=0.1, bend=-24, skip=None)
    p = surface(8, 54, 0.5)
    s.spike(0, 0, unit(FRONT * 0.6 + UP * 0.6 + RIGHT * 0.25), 3.4, 3.2, depth=1.6, bend=-80, bend_axis=v3(1, 0, 0),
            tip=0.0, shade=1.0, root=p)
    return s


def style_mohawk():
    s = Style("mohawk", "buzz", 89, "Ridge")
    for i in range(11):
        t = i / 10
        el = 32 + 58 * math.sin(math.pi * t)
        az = 0 if t < 0.5 else 180
        p = surface(az, el, 0.6)
        d = unit(outward(p) * 1.0 + UP * 0.35 + BACK * 0.55)
        s.spike(0, 0, d, 3.8 + 1.6 * math.sin(math.pi * t), 3.0, depth=1.8, bend=-18, tip=0.0,
                shade=s.r(0.72, 1.0), root=p, twist=s.r(-10, 10))
    return s


def style_slick_back():
    s = Style("slick_back", "short", 97, "Slicked back")
    shell(s, ROWS_SHORT, fixed(BACK * 1.0 + DOWN * 0.1), length=(3.2, 3.8), lift=-0.08, tip=0.3, bend=-34,
          shade=(0.7, 1.0), skip=lambda az, el: face_gap(az, el, 52, 46), twist=4, jitter=0.08)
    nape(s, 7, 2.4, 2.6, el=-8, tip=0.0, out_k=0.35)
    sideburns(s, 1.8, 1.6, tip=0.4)
    return s


def style_curly():
    s = Style("curly", "messy", 101, "Curly")
    for el, n in ((82, 5), (62, 10), (40, 12), (18, 13), (-4, 10)):
        for i in range(n):
            az = 360 * (i + 0.5 * (el % 2) + s.r(-0.2, 0.2)) / n
            if face_gap(az, el, 52, 38):
                continue
            p = surface(az, el, 0.7)
            out = outward(p)
            tang = unit(np.cross(out, v3(0, 1, 0.3)))
            s.spike(0, 0, unit(out * 0.5 + tang), s.r(2.4, 3.0), s.r(1.9, 2.3), depth=1.5, bend=s.r(170, 230),
                    bend_axis=unit(np.cross(tang, out)), tip=0.35, shade=s.r(0.6, 1.0), root=p, segs=4)
    for i in range(6):
        az = -48 + 96 * (i + 0.5) / 6
        p = surface(az, 48, 0.6)
        out = outward(p)
        s.spike(0, 0, unit(out * 0.4 + DOWN * 0.6), 2.5, 2.0, depth=1.4, bend=210, bend_axis=v3(1, 0, 0), tip=0.35,
                shade=s.r(0.8, 1.0), root=p, segs=4)
    return s


def style_twin_tails():
    s = Style("twin_tails", "ponytail", 103, "Twin tails")
    shell(s, ROWS_SHORT, radial(180, 88, add=(0, 0.3, 0)), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    for side in (-1, 1):
        root = surface((180 - 68 * side) % 360, 32, -0.4)
        tie(s, root, 1.05)
        tail(s, root + v3(side * 0.4, 0.1, 0.3), 9.0, 2.6, segs=6, swing=1.0, droop=(side * 0.25, 1, 0.3))
    bangs(s, 6, 2.8, 2.3, spread=(-55, 55), el=52, droop=0.85, forward=0.3, bend=-26, tip=0.25)
    return s


# ---------------------------------------------------------------------------
# The creator wave (2026-11-19): 35 more styles. Inspired by the shapes of ninja anime in general (spikes,
# knots, buns, tails, braids), never a copy of one character's hair. They come after "none" in
# PlayerLook.Slot.HAIR, so every older style keeps its number.
# ---------------------------------------------------------------------------

def spike_ring(s, el, n, length, width, push, depth=(1.5, 1.9), bend=-20, tip=0.0, gap=(58, 32), shade=(0.7, 1.0),
               jitter=0.18, twist=15, span=(0, 360), segs=3):
    """A ring of big spikes at one height, each pointing along push(p, out)."""
    a0, a1 = span
    for i in range(n):
        az = a0 + (a1 - a0) * (i + 0.5 + s.r(-jitter, jitter)) / n
        if gap is not None and face_gap(az, el, *gap):
            continue
        p = surface(az, el, 0.7)
        out = outward(p)
        d = unit(push(p, out))
        s.spike(az, el, d, length * s.r(0.85, 1.15), width * s.r(0.92, 1.08), depth=s.r(*depth), bend=bend * s.r(0.6, 1.3),
                tip=tip, shade=s.r(*shade), root=p, twist=s.r(-twist, twist), segs=segs)


def style_drill_spikes():
    s = Style("drill_spikes", "spiky", 211, "Drill spikes")
    # a few huge twisted cones bursting back from the crown, like drill bits
    shell(s, ROWS, radial(), length=(2.4, 3.0), lift=0.08, tip=0.35, shade=(0.5, 0.78))
    for el, n, ln, w in ((70, 3, 7.0, 4.2), (40, 5, 7.6, 4.0), (10, 5, 6.4, 3.6)):
        for i in range(n):
            az = 110 + 140 * (i + 0.5) / n + s.r(-6, 6)
            p = surface(az, el, 0.8)
            d = unit(BACK * 1.0 + outward(p) * 0.7 + UP * 0.35)
            s.spike(az, el, d, ln * s.r(0.9, 1.1), w, depth=w * 0.8, bend=-14, tip=0.0, shade=s.r(0.7, 1.0), root=p,
                    segs=4, twist=s.r(30, 60))
    bangs(s, 4, 2.8, 3.0, spread=(-45, 45), el=52, forward=0.5, bend=-6, tip=0.0)
    sideburns(s, 2.4, 2.0, tip=0.0)
    return s


def style_flame_spikes():
    s = Style("flame_spikes", "spiky", 213, "Flame spikes")
    # tall spikes standing straight up like a flame, tips leaning back
    shell(s, ROWS_SHORT, radial(180, 90, add=(0, -0.4, 0)), length=(2.4, 3.0), lift=0.3, tip=0.3, shade=(0.5, 0.78))
    for el, n, ln in ((86, 3, 6.6), (66, 7, 6.2), (44, 9, 5.0), (22, 9, 3.6)):
        spike_ring(s, el, n, ln, 3.1, lambda p, o: UP * 1.2 + o * 0.45 + BACK * 0.3, bend=-16, gap=(50, 30))
    bangs(s, 5, 2.8, 2.6, spread=(-50, 50), el=54, droop=0.4, forward=0.6, bend=-30, tip=0.0)
    sideburns(s, 2.2, 1.9, tip=0.0)
    return s


def style_porcupine():
    s = Style("porcupine", "spiky", 217, "Porcupine")
    # many short sharp spikes all over, pointing straight out
    shell(s, ROWS_SHORT, radial(), length=(2.0, 2.4), lift=0.1, tip=0.4, shade=(0.5, 0.75))
    for el, n in ((84, 4), (64, 10), (42, 13), (20, 14), (-2, 11)):
        spike_ring(s, el, n, 2.9, 2.2, lambda p, o: o * 1.0 + UP * 0.15 + BACK * 0.15, depth=(1.2, 1.5), bend=-8,
                   gap=(54, 30))
    bangs(s, 6, 2.4, 2.1, spread=(-52, 52), el=52, forward=0.55, bend=-5, tip=0.0)
    return s


def style_windswept():
    s = Style("windswept", "swept", 219, "Windswept")
    # every lock blown hard to the character's right, as if running through a storm
    wind = RIGHT * 1.0 + BACK * 0.45 + UP * 0.1
    shell(s, ROWS, fixed(wind), length=(3.2, 4.2), lift=0.35, tip=0.1, bend=-14, twist=20)
    for el, n, ln in ((66, 5, 5.4), (40, 7, 5.6), (14, 7, 4.6)):
        spike_ring(s, el, n, ln, 3.0, lambda p, o: wind + o * 0.35, bend=-22, gap=(40, 36))
    bangs(s, 5, 3.8, 2.6, spread=(-50, 50), el=52, droop=0.6, sweep=-0.9, forward=0.3, bend=-10, tip=0.0)
    return s


def style_crown_spikes():
    s = Style("crown_spikes", "spiky", 223, "Crown spikes")
    # flat on top, a ring of spikes flaring out around the crown like a crown
    shell(s, ROWS_SHORT, radial(180, 88), length=(2.6, 3.2), lift=0.0, tip=0.4, bend=-28, shade=(0.55, 0.85))
    spike_ring(s, 48, 12, 4.8, 3.0, lambda p, o: o * 1.1 + UP * 0.55, bend=-30, gap=(30, 60), jitter=0.05)
    spike_ring(s, 22, 10, 3.4, 2.8, lambda p, o: o * 1.0 + DOWN * 0.1 + BACK * 0.3, bend=-20, gap=(56, 30))
    bangs(s, 5, 3.0, 2.6, spread=(-48, 48), el=52, forward=0.4, bend=-20, tip=0.0)
    sideburns(s, 2.4, 2.0, tip=0.0)
    return s


def style_bedhead():
    s = Style("bedhead", "messy", 227, "Bedhead")
    # short, chaotic clumps sticking out every which way
    shell(s, ROWS_SHORT, radial(add=(0, -0.2, 0)), length=(2.2, 2.8), lift=0.5, tip=0.25, jitter=0.4, bend=20, twist=50)
    for i in range(22):
        az = s.r(0, 360)
        el = s.r(0, 88)
        if face_gap(az, el, 52, 36):
            continue
        p = surface(az, el, 0.7)
        d = unit(outward(p) * 0.6 + v3(s.r(-1, 1), s.r(-0.8, 0.3), s.r(-0.6, 1)))
        s.spike(az, el, d, s.r(2.2, 3.2), s.r(2.0, 2.8), depth=1.4, bend=s.r(-70, 70), tip=0.15, shade=s.r(0.65, 1.0),
                root=p, twist=s.r(-40, 40))
    bangs(s, 6, 2.8, 2.3, spread=(-55, 55), el=50, forward=0.35, bend=-40, sweep=0.1, tip=0.2)
    sideburns(s, 2.2, 2.0, tip=0.2)
    return s


def style_messy_layers():
    s = Style("messy_layers", "messy", 229, "Messy layers")
    # three layers of choppy locks stacked down to the jaw
    shell(s, ROWS_SHORT, radial(180, 80, add=(0, 0.25, 0)), length=(3.0, 3.6), lift=0.2, tip=0.15, jitter=0.3, twist=25)
    for el, n, ln in ((26, 12, 3.4), (2, 12, 3.8), (-20, 9, 3.6)):
        span = (50, 310) if el > 0 else (75, 285)
        spike_ring(s, el, n, ln, 3.0, lambda p, o: DOWN * 1.0 + o * 0.45, bend=-18, tip=0.1, gap=None, span=span,
                   shade=(0.55, 0.9), twist=25)
    bangs(s, 6, 3.6, 2.6, spread=(-55, 55), el=50, forward=0.3, bend=-28, sweep=0.2, tip=0.1)
    return s


def style_shaggy():
    s = Style("shaggy", "messy", 231, "Shaggy")
    # long, uneven locks over the ears and down the nape, a heavy fringe in the eyes
    shell(s, ROWS, radial(180, 85, add=(0, 0.45, 0)), length=(3.6, 4.4), lift=0.15, tip=0.1, jitter=0.35, twist=30,
          bend=-10)
    nape(s, 9, 4.6, 2.8, el=-10, tip=0.0, spread=(95, 265), out_k=0.25)
    for side in (-1, 1):
        for j, az in enumerate((70, 84)):
            p = surface(az * side % 360, 16, 0.5)
            s.spike(0, 0, unit(DOWN + v3(side * 0.25, 0, 0.1)), 4.6 - j * 0.5, 2.8, depth=1.2, bend=-12, tip=0.05,
                    shade=0.82 - 0.1 * j, root=p)
    bangs(s, 7, 4.2, 2.4, spread=(-56, 56), el=50, forward=0.2, bend=-14, sweep=0.15, tip=0.05)
    return s


def style_wolf_cut():
    s = Style("wolf_cut", "messy", 233, "Wolf cut")
    # choppy and full on top, thinning into long ragged points at the nape
    shell(s, ROWS_SHORT, radial(180, 80, add=(0, 0.1, 0)), length=(3.0, 3.6), lift=0.4, tip=0.1, jitter=0.3, twist=30,
          bend=-8)
    for i in range(9):
        az = 110 + 140 * (i + 0.5) / 9 + s.r(-5, 5)
        p0 = surface(az, 6, 0.4)
        out = outward(p0)
        p1 = p0 + DOWN * 2.6 + out * 0.4
        p2 = p1 + DOWN * s.r(2.4, 3.8) + out * 0.5
        s.sheet([p0, p1, p2], s.r(2.4, 3.0), depth=0.8, tip=0.1, out=out, shade=s.r(0.55, 0.8), swing=0.5, pivot=p0, hang=True)
    sideburns(s, 3.4, 2.2, tip=0.0)
    bangs(s, 5, 3.6, 2.5, spread=(-50, 50), el=50, forward=0.3, bend=-24, sweep=-0.15, tip=0.0)
    return s


def style_mullet():
    s = Style("mullet", "short", 237, "Mullet")
    # short and tidy on top and the sides, long at the back
    shell(s, ROWS_SHORT, radial(180, 70, add=(0, 0.2, 0.2)), length=(2.4, 3.0), lift=0.05, tip=0.45, bend=-32)
    back_sheets(s, 6.0, n=5, top_el=10, width=3.4, tip=0.3, swing=0.7, spread=(120, 240), flare=0.6)
    bangs(s, 4, 2.4, 2.6, spread=(-44, 44), el=52, forward=0.3, bend=-34, sweep=0.2, tip=0.4)
    sideburns(s, 2.0, 1.8, tip=0.4)
    return s


def style_parted():
    s = Style("parted", "short", 239, "Side part")
    # neat and short, parted on the character's right and combed over to the left
    shell(s, ROWS_SHORT, fixed(LEFT * 0.9 + BACK * 0.5 + DOWN * 0.25), length=(2.6, 3.2), lift=0.02, tip=0.45, bend=-30,
          twist=4, jitter=0.08, shade=(0.66, 1.0))
    for az, el, ln, w in ((-34, 76, 4.6, 3.2), (-12, 72, 4.4, 3.0), (10, 70, 3.8, 2.8)):
        p = surface(az, el, 0.5)
        s.spike(0, 0, unit(LEFT * 1.0 + BACK * 0.2 + UP * 0.1), ln, w, depth=1.4, bend=24, bend_axis=v3(0, 0, -1),
                tip=0.3, shade=0.95, root=p, segs=3)
    sideburns(s, 1.8, 1.7, tip=0.5)
    nape(s, 5, 1.8, 2.6, el=-6, tip=0.5)
    return s


def style_crop():
    s = Style("crop", "buzz", 241, "Textured crop")
    # very short faded sides, a short textured top with a blunt fringe
    shell(s, ROWS_SHORT, radial(180, 60), length=(1.2, 1.5), width=(2.8, 3.2), thick=(0.55, 0.7), lift=0.0, tip=0.8,
          bend=-15, inset=0.42, skip=lambda az, el: face_gap(az, el, 48, 44), twist=6)
    shell(s, [(84, 4), (66, 8, -80, 80), (66, 4, 130, 230)], radial(180, 80, add=(0, 0.1, -0.3)), length=(2.2, 2.7),
          lift=0.3, tip=0.3, bend=-20, skip=None, twist=25, jitter=0.3)
    bangs(s, 6, 1.8, 2.4, spread=(-42, 42), el=56, droop=0.6, forward=0.5, bend=-10, tip=0.6)
    return s


def style_side_shave():
    s = Style("side_shave", "buzz", 243, "Side shave")
    # the character's right side shaved, everything else swept long over to the left
    shell(s, [(84, 4), (62, 9, -160, 110), (38, 8, -20, 120), (14, 6, 30, 120)], fixed(LEFT * 1.0 + DOWN * 0.5 + BACK * 0.2),
          length=(3.6, 4.6), lift=0.18, tip=0.15, bend=-20, skip=None, twist=10)
    for side_pts, w, sh in (([surface(-20, 64, 0.3), surface(40, 52, -0.4), v3(4.4, -5.2, -2.8), v3(4.6, -2.4, -2.6),
                              v3(4.5, 0.2, -2.0)], 3.8, 1.0),
                            ([surface(10, 66, 0.3), surface(70, 46, -0.4), v3(4.6, -4.0, 0.0), v3(4.7, -1.0, 0.2)], 3.4, 0.82)):
        s.sheet(side_pts, w, depth=0.9, tip=0.3, out=v3(1, -0.2, -0.2), shade=sh, swing=0.4, pivot=side_pts[1], hang=True)
    return s


def style_faux_hawk():
    s = Style("faux_hawk", "buzz", 247, "Faux hawk")
    # short sides, the middle strip pushed up into a soft peak
    shell(s, ROWS_SHORT, radial(180, 60), length=(1.2, 1.5), width=(2.8, 3.2), thick=(0.55, 0.7), lift=0.0, tip=0.8,
          bend=-15, inset=0.42, skip=lambda az, el: face_gap(az, el, 48, 44), twist=6)
    for i in range(9):
        t = i / 8
        el = 40 + 52 * math.sin(math.pi * min(1, t * 1.1))
        az = 0 if t < 0.55 else 180
        for dx in (-1.0, 1.0):
            p = surface(az, el, 0.6) + v3(dx, 0, 0)
            d = unit(UP * 1.0 + outward(p) * 0.4 + v3(-dx * 0.25, 0, 0.2))
            s.spike(0, 0, d, 2.8 + 1.4 * math.sin(math.pi * t), 2.4, depth=1.5, bend=-10, tip=0.0, shade=s.r(0.7, 1.0),
                    root=p, twist=s.r(-12, 12))
    return s


def style_tall_mohawk():
    s = Style("tall_mohawk", "buzz", 251, "Tall mohawk")
    # a towering crest of long spikes from the brow to the nape
    for i in range(13):
        t = i / 12
        el = 26 + 64 * math.sin(math.pi * t)
        az = 0 if t < 0.5 else 180
        p = surface(az, el, 0.6)
        d = unit(outward(p) * 1.0 + UP * 0.6 + BACK * 0.25)
        s.spike(0, 0, d, 5.4 + 2.6 * math.sin(math.pi * t), 2.6, depth=1.9, bend=-14, tip=0.0, shade=s.r(0.7, 1.0),
                root=p, twist=s.r(-8, 8))
    return s


def style_top_knot_shaved():
    s = Style("shaved_knot", "buzz", 253, "Shaved knot")
    # the sides shaved close, the long top gathered into a tight knot on the crown
    shell(s, ROWS_SHORT, radial(180, 60), length=(1.2, 1.5), width=(2.8, 3.2), thick=(0.55, 0.7), lift=0.0, tip=0.8,
          bend=-15, inset=0.42, skip=lambda az, el: face_gap(az, el, 48, 44), twist=6)
    shell(s, [(82, 5), (62, 9)], radial(180, 82, add=(0, -0.6, 0)), length=(2.8, 3.2), lift=-0.1, tip=0.4, bend=-30,
          skip=lambda az, el: face_gap(az, el, 60, 70), jitter=0.05, twist=4)
    top = surface(180, 80, -0.3)
    bun(s, top + v3(0, -1.4, 0.0), 1.5, n=9)
    tie(s, top + v3(0, -0.2, 0.0), 0.95)
    return s


def style_twin_braids():
    s = Style("twin_braids", "ponytail", 257, "Twin braids")
    shell(s, ROWS_SHORT, radial(0, 88, add=(0, 0.3, 0.2)), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    for side in (-1, 1):
        root = surface((180 - 62 * side) % 360, 6, -0.5)
        tie(s, root + v3(side * 0.3, 0.2, 0.2), 0.95)
        tail(s, root + v3(side * 0.4, 0.3, 0.2), 9.0, 2.3, segs=6, swing=0.9, braid=True, droop=(side * 0.2, 1, 0.1))
    bangs(s, 6, 2.6, 2.3, spread=(-52, 52), el=52, droop=0.85, forward=0.3, bend=-26, tip=0.3)
    return s


def style_side_braid():
    s = Style("side_braid", "ponytail", 259, "Side braid")
    # gathered to the character's left and braided over the shoulder, in front
    shell(s, ROWS_SHORT, fixed(LEFT * 0.8 + BACK * 0.7 + DOWN * 0.3), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    root = surface(112, 4, -0.5)
    tie(s, root + v3(0.4, 0.4, 0.0), 1.0)
    pts = [root + v3(0.5, 0.4, 0.0)]
    for p in (v3(5.0, 1.5, -1.2), v3(5.3, 3.5, -2.4), v3(5.2, 5.8, -2.9), v3(5.0, 8.0, -3.0), v3(4.9, 10.0, -3.0)):
        pts.append(p)
    width = 2.4
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        mid = (a + b) / 2
        side = unit(np.cross(b - a, v3(1, 0, 0)))
        for j, off in enumerate((-0.45, 0.45)):
            w = width * (1 - 0.3 * i / len(pts))
            s.add([a + side * off * width * 0.35, mid + side * off * 0.1, b - side * off * width * 0.35],
                  [w * 0.55, w * 0.75, w * 0.45], [w * 0.55, w * 0.7, w * 0.4], out=v3(1, 0, -0.3), shade=0.9 - 0.12 * j,
                  swing=0.5, pivot=pts[0], hang=True)
    end = pts[-1]
    s.add([end, end + v3(0, 1.3, 0), end + v3(0, 2.3, 0)], [width * 0.5, width * 0.7, 0.2], [width * 0.4, width * 0.5, 0.2],
          out=v3(1, 0, -0.3), shade=0.85, tex="sheet", swing=0.5, pivot=pts[0], hang=True)
    bangs(s, 5, 2.8, 2.4, spread=(-50, 50), el=52, droop=0.8, sweep=0.4, forward=0.3, bend=-26, tip=0.25)
    return s


def style_mushroom():
    s = Style("mushroom", "bowl", 261, "Mushroom")
    # a big rounded bowl that flares wide at the brow line and covers the ears
    shell(s, [(84, 5), (60, 11), (36, 14), (12, 15, 50, 310), (-10, 11, 80, 280)], radial(180, 90, add=(0, 0.5, 0)),
          length=(3.2, 3.6), width=(3.2, 3.6), lift=0.22, tip=0.9, bend=-14, jitter=0.05, twist=4, skip=None, shade=(0.6, 0.96))
    bangs(s, 9, 2.5, 2.6, spread=(-64, 64), forward=0.3, bend=-4, tip=0.92, thick=1.2)
    return s


def style_curtain():
    s = Style("curtain", "long", 263, "Curtain bangs")
    # parted in the middle, long bangs falling to both sides of the face, jaw length
    shell(s, ROWS_SHORT, radial(0, 86, add=(0, 0.4, 0.1)), length=(3.0, 3.6), lift=0.0, tip=0.4, bend=-24)
    for side in (-1, 1):
        # rooted at the parting, sheets that fall over the forehead and out past the outer brow to the cheek
        for j, (x0, w) in enumerate(((0.5, 2.6), (1.4, 2.4))):
            p0 = v3(side * x0, -8.4, -3.9)
            pts = [p0, v3(side * (x0 + 1.4), -7.0, -4.55), v3(side * (x0 + 2.6), -5.3, -4.6), v3(side * 4.1, -3.4, -4.4),
                   v3(side * 4.4, -1.4, -4.0)]
            s.sheet(pts, w, depth=0.8, tip=0.45, out=v3(side * 0.2, -0.2, -1), shade=0.98 - 0.12 * j, cell=j)
    side_curtains(s, 3.6, width=2.4, el=10, swing=0.5, front=True, tip=0.4)
    nape(s, 8, 3.4, 2.7, el=-8, tip=0.1, spread=(100, 260))
    return s


def style_veil_bangs():
    s = Style("veil_bangs", "swept", 267, "Veil bangs")
    # a long sweep of fringe hiding the character's left eye, medium hair at the back
    shell(s, ROWS, fixed(LEFT * 0.7 + BACK * 0.6 + DOWN * 0.4), length=(3.0, 3.8), lift=0.1, tip=0.2, bend=-24)
    for p0, p1, rest, w, sh, cell in (
            (surface(-34, 66, 0.3), surface(-4, 44, -0.5), [v3(0.6, -5.2, -4.8), v3(1.5, -3.4, -4.8), v3(2.2, -1.2, -4.6)], 3.9, 1.0, 0),
            (surface(-12, 62, 0.3), surface(14, 40, -0.45), [v3(2.4, -4.6, -4.7), v3(3.3, -2.4, -4.5)], 3.0, 0.86, 1)):
        s.sheet([p0, p1] + rest, w, depth=0.9, tip=0.4, out=v3(0.1, -0.2, -1), shade=sh, cell=cell)
    back_sheets(s, 4.0, n=5, top_el=20, width=3.4, tip=0.25, swing=0.6)
    sideburns(s, 3.0, 2.2, tip=0.0)
    return s


def style_drill_tails():
    s = Style("drill_tails", "ponytail", 269, "Drill tails")
    # twin tails wound into tight spiral drills
    shell(s, ROWS_SHORT, radial(180, 88, add=(0, 0.3, 0)), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    for side in (-1, 1):
        root = surface((180 - 70 * side) % 360, 40, -0.4)
        tie(s, root, 1.1)
        # a stack of cones down a slanting axis, each turned a little further: a drill
        c = root + v3(side * 1.4, 0.6, 0.9)
        axis = unit(v3(side * 0.25, 1.0, 0.25))
        for k in range(6):
            t = k / 5
            p0 = c + axis * (k * 1.45)
            d = unit(axis + v3(math.cos(k * 1.9) * 0.35, 0, math.sin(k * 1.9) * 0.35))
            s.spike(0, 0, d, 2.9 - 0.9 * t, 3.0 - 1.3 * t, depth=2.6 - 1.1 * t, bend=-25, tip=0.0,
                    shade=0.98 - 0.08 * (k % 2) - 0.12 * t, root=p0, segs=2, twist=k * 40)
        for lock in s.locks[-6:]:
            lock["swing"] = 0.7
            lock["pivot"] = [round(float(x), 3) for x in root]
            lock["hang"] = True
    bangs(s, 6, 2.6, 2.3, spread=(-55, 55), el=52, droop=0.85, forward=0.3, bend=-26, tip=0.25)
    return s


def style_high_twin_tails():
    s = Style("high_twin_tails", "ponytail", 271, "High twin tails")
    shell(s, ROWS_SHORT, radial(0, 88, add=(0, 0.2, 0)), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    for side in (-1, 1):
        root = surface((180 - 78 * side) % 360, 62, -0.4)
        tie(s, root, 1.1)
        tail(s, root + v3(side * 0.6, -0.2, 0.3), 10.5, 2.9, segs=7, swing=1.0, droop=(side * 0.35, 1, -0.3))
    bangs(s, 6, 2.8, 2.3, spread=(-55, 55), el=52, droop=0.85, forward=0.3, bend=-26, tip=0.2)
    return s


def style_side_ponytail():
    s = Style("side_ponytail", "ponytail", 273, "Side ponytail")
    shell(s, ROWS_SHORT, fixed(RIGHT * 0.9 + BACK * 0.6 + DOWN * 0.2), length=(3.0, 3.6), lift=-0.05, tip=0.35, bend=-26)
    root = surface(250, 44, -0.4)
    tie(s, root, 1.15)
    tail(s, root + v3(-0.6, 0.0, 0.2), 9.5, 3.0, segs=7, swing=1.0, droop=(-0.25, 1, -0.1))
    bangs(s, 5, 2.8, 2.4, spread=(-50, 50), el=52, droop=0.8, sweep=-0.35, forward=0.3, bend=-28, tip=0.25)
    return s


def style_spiky_tail():
    s = Style("spiky_tail", "spiky", 277, "Spiky tail")
    # wild spikes pulled back into a short tail of spikes at the back of the head
    shell(s, ROWS_SHORT, radial(0, 55, add=(0, 0.0, 1.0)), length=(3.0, 3.6), lift=0.15, tip=0.15, bend=-20, twist=25)
    root = surface(180, 30, -0.3)
    tie(s, root, 1.15)
    for i in range(9):
        a = 2 * math.pi * i / 9
        d = unit(BACK * 1.0 + v3(math.cos(a) * 0.45, math.sin(a) * 0.45 + 0.35, 0))
        s.spike(0, 0, d, s.r(4.0, 6.0), 2.4, depth=1.6, bend=-25, tip=0.0, shade=s.r(0.65, 1.0), root=root + v3(0, 0, 0.5),
                twist=s.r(-20, 20))
    spike_ring(s, 16, 6, 3.0, 2.8, lambda p, o: o * 0.9 + DOWN * 0.3, bend=-15, gap=(60, 30), span=(-90, 90))
    bangs(s, 5, 3.4, 2.6, spread=(-55, 55), el=52, forward=0.4, bend=-10, tip=0.0)
    return s


def style_half_up():
    s = Style("half_up", "long", 279, "Half up")
    # the top tied into a small knot, the rest left long down the back
    shell(s, ROWS_SHORT, radial(180, 80, add=(0, 0.2, 0.3)), length=(3.0, 3.6), lift=-0.02, tip=0.5, bend=-26)
    top = surface(180, 58, -0.3)
    bun(s, top + v3(0, -0.6, 0.8), 1.3, n=8)
    tie(s, top + v3(0, 0, 0.3), 0.9)
    back_sheets(s, 11.0, n=5, width=3.8, tip=0.55, swing=0.85, top_el=30)
    side_curtains(s, 6.0, width=2.4, el=10, swing=0.6, front=True, tip=0.5)
    bangs(s, 5, 3.0, 2.5, spread=(-48, 48), el=52, forward=0.25, bend=-18, sweep=0.12, tip=0.2)
    return s


def style_bun_tails():
    s = Style("bun_tails", "bun", 281, "Buns and tails")
    # two buns on top with long tails falling out of them
    shell(s, ROWS_SHORT, radial(180, 88, add=(0, 0.2, 0)), length=(2.8, 3.4), lift=0.0, tip=0.35, bend=-26)
    for side in (-1, 1):
        c = surface(140 * side % 360, 64, -1.2) + v3(side * 0.4, -0.5, 0)
        bun(s, c, 1.5, n=8)
        tail(s, c + v3(side * 0.6, 1.0, 0.8), 8.5, 2.2, segs=6, swing=1.0, droop=(side * 0.2, 1, -0.25))
    bangs(s, 6, 2.7, 2.3, spread=(-55, 55), el=52, droop=0.85, forward=0.3, bend=-25, tip=0.25)
    return s


def style_horn_buns():
    s = Style("horn_buns", "bun", 283, "Horn buns")
    # two buns wound up into points, like small horns
    shell(s, ROWS_SHORT, radial(180, 88, add=(0, 0.2, 0)), length=(2.8, 3.4), lift=0.0, tip=0.35, bend=-26)
    for side in (-1, 1):
        base = surface(120 * side % 360, 70, -0.6)
        for k in range(6):
            a = k * 2 * math.pi / 6
            off = v3(math.cos(a) * 0.9, 0, math.sin(a) * 0.9)
            d = unit(UP * 1.0 + v3(side * 0.45, 0, 0) - off * 0.25)
            s.spike(0, 0, d, 4.2, 2.0, depth=1.5, bend=-20, bend_axis=unit(np.cross(d, off + v3(0, 0.01, 0))), tip=0.0,
                    shade=0.8 + 0.03 * k, root=base + off, segs=3)
    bangs(s, 6, 2.7, 2.3, spread=(-55, 55), el=52, droop=0.85, forward=0.3, bend=-25, tip=0.25)
    return s


def style_puff():
    s = Style("puff", "messy", 287, "Curly puff")
    # a big round cloud of tight curls
    for el, n in ((86, 5), (68, 10), (48, 14), (28, 16), (8, 15), (-12, 10)):
        for i in range(n):
            az = 360 * (i + 0.5 * (n % 2) + s.r(-0.2, 0.2)) / n
            if face_gap(az, el, 48, 40):
                continue
            p = surface(az, el, -0.4)
            out = outward(p)
            tang = unit(np.cross(out, v3(0, 1, 0.3)))
            s.spike(0, 0, unit(out * 0.6 + tang), s.r(2.2, 2.8), s.r(2.0, 2.4), depth=1.7, bend=s.r(180, 240),
                    bend_axis=unit(np.cross(tang, out)), tip=0.4, shade=s.r(0.6, 1.0), root=p, segs=4)
    for i in range(5):
        az = -40 + 80 * (i + 0.5) / 5
        p = surface(az, 52, 0.2)
        s.spike(0, 0, unit(outward(p) * 0.5 + DOWN * 0.4), 2.2, 2.0, depth=1.5, bend=200, bend_axis=v3(1, 0, 0), tip=0.4,
                shade=s.r(0.8, 1.0), root=p, segs=4)
    return s


def style_locs():
    s = Style("locs", "messy", 289, "Locs")
    # thick rope-like locs hanging down all round, to the shoulders
    shell(s, ROWS_SHORT, radial(180, 88, add=(0, 0.6, 0)), length=(2.4, 2.8), width=(2.0, 2.4), lift=0.05, tip=0.7, bend=-20)
    for i in range(16):
        az = 360 * (i + 0.5) / 16 + s.r(-5, 5)
        if face_gap(az, 20, 40, 60):
            continue
        p0 = surface(az, 40, 0.3)
        out = outward(p0)
        p1 = surface(az, 4, -0.6)
        ln = s.r(5.5, 8.0)
        pts = [p0, p1, p1 + DOWN * ln * 0.5 + out * 0.6, p1 + DOWN * ln + out * 0.9]
        n = len(pts)
        s.add(pts, [1.5] * (n - 1) + [1.1], [1.4] * (n - 1) + [1.0], out=out, shade=s.r(0.6, 0.95), swing=0.6, pivot=p1,
              hang=True)
    for i in range(5):
        x = -3.0 + 6.0 * (i + 0.5) / 5
        p = v3(x, -8.3, -3.6)
        s.add([p, p + v3(x * 0.05, 0.4, -0.9), p + v3(x * 0.12, 2.6, -1.0)], [1.5, 1.5, 1.2], [1.4, 1.4, 1.1],
              out=v3(0, 0, -1), shade=s.r(0.8, 1.0))
    return s


def style_locs_tied():
    s = Style("locs_tied", "messy", 291, "Tied locs")
    # locs gathered high at the back and falling from the tie
    shell(s, ROWS_SHORT, radial(0, 50, add=(0, -0.2, 0.9)), length=(2.8, 3.2), width=(2.0, 2.4), lift=-0.05, tip=0.6, bend=-24)
    root = surface(180, 50, -0.4)
    tie(s, root, 1.3)
    for i in range(7):
        a = 2 * math.pi * i / 7
        off = v3(math.cos(a) * 0.8, 0, math.sin(a) * 0.5)
        p0 = root + v3(0, 0, 0.6) + off
        pts = [p0, p0 + v3(off[0] * 0.8, -0.8, 1.6), p0 + v3(off[0] * 1.4, 2.0, 2.4), p0 + v3(off[0] * 1.6, 6.0, 2.2),
               p0 + v3(off[0] * 1.7, 9.0, 1.8)]
        s.add(pts, [1.4, 1.5, 1.5, 1.4, 1.0], [1.3, 1.4, 1.4, 1.3, 0.9], out=v3(0, 0, 1), shade=s.r(0.62, 0.95), swing=0.9,
              pivot=p0, hang=True)
    return s


def style_ronin():
    s = Style("ronin", "long", 293, "Ronin")
    # long and unkempt, tied loosely low at the back, loose strands framing the face
    shell(s, ROWS_SHORT, radial(0, 60, add=(0, 0.3, 0.9)), length=(3.2, 3.8), lift=0.1, tip=0.25, jitter=0.25, twist=18)
    root = surface(180, -4, -0.5)
    tie(s, root + v3(0, 0.3, 0.5), 1.1)
    tail(s, root + v3(0, 0.4, 0.6), 8.0, 3.4, segs=6, swing=0.9, droop=(0, 1, -0.05))
    for side in (-1, 1):
        p0 = surface(34 * side % 360, 56, 0.5)
        s.spike(0, 0, unit(DOWN * 1.0 + FRONT * 0.25 + v3(side * 0.3, 0, 0)), 7.0, 1.9, depth=1.0, bend=10,
                bend_axis=v3(0, 0, side), tip=0.1, shade=0.92, root=p0, segs=4)
    sideburns(s, 3.2, 2.0, tip=0.0)
    return s


def style_spiky_fringe():
    s = Style("spiky_fringe", "spiky", 297, "Spiky fringe")
    # short at the back, a heavy fringe of long spikes hanging over the eyes
    shell(s, ROWS, radial(), length=(2.6, 3.2), lift=0.15, tip=0.2, shade=(0.55, 0.85))
    for i in range(7):
        t = (i + 0.5) / 7
        az = -58 + 116 * t
        x = 4.0 * math.sin(math.radians(az))
        p = v3(x, -8.2, -4.4)
        d = unit(DOWN * 1.0 + FRONT * 0.35 + v3(x * 0.07, 0, 0))
        s.spike(az, 50, d, 5.0 - 1.2 * abs(t - 0.5), 2.6, depth=1.5, bend=-8, tip=0.0, shade=s.r(0.82, 1.0), root=p,
                twist=s.r(-10, 10))
    spike_ring(s, 30, 8, 3.8, 3.0, lambda p, o: BACK * 1.0 + o * 0.6, bend=-20, gap=(70, 40), span=(90, 270))
    sideburns(s, 3.0, 2.0, tip=0.0)
    return s


def style_rat_tail():
    s = Style("rat_tail", "short", 299, "Rat tail")
    # short all over, one thin braid-like tail from the nape
    shell(s, ROWS_SHORT, radial(180, 70, add=(0, 0.2, 0.2)), length=(2.2, 2.8), lift=0.05, tip=0.45, bend=-32)
    root = surface(180, -16, -0.5)
    tie(s, root + v3(0, 0.3, 0.4), 0.8)
    tail(s, root + v3(0, 0.4, 0.5), 8.0, 1.5, segs=6, swing=0.9, tex="clump")
    bangs(s, 5, 2.6, 2.6, spread=(-46, 46), el=52, forward=0.3, bend=-34, sweep=0.2, tip=0.35)
    sideburns(s, 1.8, 1.8, tip=0.45)
    return s


def style_crescent():
    s = Style("crescent", "swept", 301, "Crescent sweep")
    # long bangs curling around both sides of the face in two crescents, medium at the back
    shell(s, ROWS, radial(180, 80, add=(0, 0.2, 0.2)), length=(3.0, 3.6), lift=0.12, tip=0.2, bend=-24)
    for side in (-1, 1):
        for j, (x0, w, k) in enumerate(((1.0, 2.8, 1.0), (2.0, 2.2, 0.8))):
            pts = [v3(side * x0, -8.4, -3.9), v3(side * (x0 + 1.9) * k + side * (1 - k) * 2.6, -6.6, -4.6),
                   v3(side * 4.3 * k + side * (1 - k) * 4.0, -4.4, -4.55), v3(side * 4.4, -2.3, -4.35),
                   v3(side * 3.3, -0.5, -4.45)]
            s.sheet(pts, w, depth=0.8, tip=0.1, out=v3(side * 0.25, -0.1, -1), shade=0.98 - 0.12 * j, cell=j)
    nape(s, 7, 3.6, 2.7, el=-8, tip=0.0)
    return s


def style_sweep_back_long():
    s = Style("swept_long", "long", 303, "Swept back long")
    # everything combed straight back off the forehead and hanging long down the back
    shell(s, ROWS_SHORT, fixed(BACK * 1.0 + DOWN * 0.15), length=(3.4, 4.0), lift=-0.06, tip=0.3, bend=-30,
          shade=(0.7, 1.0), skip=lambda az, el: face_gap(az, el, 50, 50), twist=4, jitter=0.08)
    back_sheets(s, 12.0, n=5, width=3.9, tip=0.5, swing=0.85, top_el=50)
    for side in (-1, 1):
        p0 = surface(16 * side % 360, 60, 0.4)
        s.sheet([p0, p0 + v3(side * 0.4, 2.0, -0.6), p0 + v3(side * 1.0, 4.0, -0.4)], 1.6, depth=0.6, tip=0.2,
                out=v3(0, 0, -1), shade=0.9, swing=0.4, pivot=p0, hang=True)
    return s


def style_spiky_long():
    s = Style("spiky_long", "spiky", 307, "Spiky long")
    # a spiky crown and long jagged locks down the back
    shell(s, ROWS_SHORT, radial(), length=(2.8, 3.4), lift=0.2, tip=0.1, shade=(0.55, 0.85))
    for el, n, ln in ((70, 5, 4.8), (46, 8, 4.6)):
        spike_ring(s, el, n, ln, 3.2, lambda p, o: o * 0.9 + UP * 0.5 + BACK * 0.6, bend=-24, gap=(50, 40))
    for i in range(8):
        az = 115 + 130 * (i + 0.5) / 8
        p0 = surface(az, 20, 0.4)
        out = outward(p0)
        pts = [p0, p0 + DOWN * 3.0 + out * 0.8, p0 + DOWN * 7.0 + out * 0.9, p0 + DOWN * s.r(10.0, 13.0) + out * 0.6]
        s.add(pts, [3.0, 2.8, 2.2, 0.0], [1.5, 1.3, 1.0, 0.1], out=out, shade=s.r(0.55, 0.85), swing=0.8, pivot=p0, hang=True)
    bangs(s, 5, 3.6, 2.7, spread=(-55, 55), el=52, forward=0.45, bend=-12, tip=0.0)
    side_curtains(s, 5.0, width=2.6, el=12, swing=0.6, front=True, tip=0.0)
    return s


def style_wave():
    s = Style("wave", "swept", 309, "Ocean wave")
    # one huge lock curling up off the forehead like a breaking wave, short sides
    shell(s, ROWS_SHORT, fixed(BACK * 1.0 + DOWN * 0.2), length=(2.6, 3.0), lift=0.0, tip=0.4, bend=-30)
    for az, el, ln, w, sh in ((0, 74, 6.4, 4.6, 1.0), (-26, 70, 5.2, 3.4, 0.86), (26, 70, 5.2, 3.4, 0.86)):
        p = surface(az, el, 0.5)
        s.spike(0, 0, unit(UP * 1.0 + FRONT * 0.35), ln, w, depth=2.0, bend=105, bend_axis=v3(1, 0, 0), tip=0.0, shade=sh,
                root=p, segs=5)
    nape(s, 6, 2.4, 2.6, tip=0.2)
    sideburns(s, 2.0, 1.8, tip=0.3)
    return s


MORE_STYLES = [style_drill_spikes, style_flame_spikes, style_porcupine, style_windswept, style_crown_spikes,
               style_bedhead, style_messy_layers, style_shaggy, style_wolf_cut, style_mullet, style_parted, style_crop,
               style_side_shave, style_faux_hawk, style_tall_mohawk, style_top_knot_shaved, style_twin_braids,
               style_side_braid, style_mushroom, style_curtain, style_veil_bangs, style_drill_tails,
               style_high_twin_tails, style_side_ponytail, style_spiky_tail, style_half_up, style_bun_tails,
               style_horn_buns, style_puff, style_locs, style_locs_tied, style_ronin, style_spiky_fringe,
               style_rat_tail, style_crescent, style_sweep_back_long, style_spiky_long, style_wave]



STYLES = [style_spiky, style_messy, style_short, style_swept, style_bowl, style_buzz, style_topknot, style_duck,
          style_bun, style_hime, style_ponytail, style_long, style_high_ponytail, style_braid, style_side_bangs,
          style_shoulder_messy, style_twin_buns, style_wild_mane, style_undercut, style_mohawk, style_slick_back,
          style_curly, style_twin_tails]
# PlayerLook.Slot.HAIR order: the first styles, then "none" (bald), then the creator wave's styles
ALL_STYLES = STYLES + MORE_STYLES



# ---------------------------------------------------------------------------
# A software preview (flat-shaded, textured by sampling) to iterate on the designs
# ---------------------------------------------------------------------------

def lock_quads(lock):
    """The same geometry the game builds (client/HairLayer): a ring of 4 corners at each point, the lock's flat side
    facing "out". Returns (corners, face, v0, v1): face 0 = outer, 1/3 = edges, 2 = inner, 4 = the blunt tip."""
    pts = [np.asarray(p, np.float64) for p in lock["pts"]]
    n = len(pts)
    out0 = unit(lock["out"])
    rings = []
    for i in range(n):
        t = unit(pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)])
        o = out0 - t * np.dot(out0, t)
        if np.linalg.norm(o) < 1e-4:
            o = np.cross(t, v3(1, 0, 0))
        o = unit(o)
        side = unit(np.cross(t, o))
        w, d = lock["w"][i] / 2, lock["d"][i] / 2
        rings.append([pts[i] + side * w + o * d, pts[i] - side * w + o * d, pts[i] - side * w - o * d,
                      pts[i] + side * w - o * d])
    lens = [0.0]
    for i in range(1, n):
        lens.append(lens[-1] + np.linalg.norm(pts[i] - pts[i - 1]))
    total = max(lens[-1], 1e-6)
    quads = []
    for i in range(n - 1):
        for f in range(4):
            a, b = f, (f + 1) % 4
            q = [rings[i][a], rings[i][b], rings[i + 1][b], rings[i + 1][a]]
            quads.append((q, f, lens[i] / total, lens[i + 1] / total))
    if lock["w"][-1] > 0.05 and lock["d"][-1] > 0.05:
        quads.append((rings[-1][::-1], 4, 0.97, 1.0))
    return quads


# How much light each face of a lock gets (outer, edge, inner, edge, tip): the same table as client/HairLayer.
FACE_SHADE = (1.0, 0.8, 0.6, 0.8, 0.7)


def tint(rgb, g):
    k = (np.asarray(g, np.float64) - 128) / 40.0
    c = np.asarray(rgb, np.float64)
    k = k[..., None]
    return np.where(k >= 0, np.minimum(255, c * (1 + 0.13 * k) + 14 * k), np.maximum(0, c * (1 + 0.2 * k)))


def project(p, view):
    yaw, pitch = view
    c, s_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    x, y, z = p
    x, z = x * c - z * s_, x * s_ + z * c
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    y, z = y * cp - z * sp, y * sp + z * cp
    return x, y, z


class Raster:
    """A tiny z-buffered triangle rasterizer (numpy) with textured, tinted triangles."""

    def __init__(self, size, scale, oy):
        self.n = size
        self.S = scale
        self.ox = size / 2
        self.oy = oy
        self.rgb = np.zeros((size, size, 3), np.float32)
        self.rgb[:] = (40, 42, 46)
        self.z = np.full((size, size), 1e9, np.float32)

    def tri(self, P, UV, shade, colour, tex, light):
        xs = [self.ox + p[0] * self.S for p in P]
        ys = [self.oy + p[1] * self.S for p in P]
        zs = [p[2] for p in P]
        x0, x1 = int(max(0, math.floor(min(xs)))), int(min(self.n - 1, math.ceil(max(xs))))
        y0, y1 = int(max(0, math.floor(min(ys)))), int(min(self.n - 1, math.ceil(max(ys))))
        if x1 < x0 or y1 < y0:
            return
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(d) < 1e-9:
            return
        a = ((ys[1] - ys[2]) * (X - xs[2]) + (xs[2] - xs[1]) * (Y - ys[2])) / d
        b = ((ys[2] - ys[0]) * (X - xs[2]) + (xs[0] - xs[2]) * (Y - ys[2])) / d
        c = 1 - a - b
        inside = (a >= -1e-4) & (b >= -1e-4) & (c >= -1e-4)
        if not inside.any():
            return
        z = a * zs[0] + b * zs[1] + c * zs[2]
        zb = self.z[y0:y1 + 1, x0:x1 + 1]
        draw = inside & (z < zb)
        if tex is not None:
            u = a * UV[0][0] + b * UV[1][0] + c * UV[2][0]
            v = a * UV[0][1] + b * UV[1][1] + c * UV[2][1]
            tx = np.clip((u * TEX).astype(int), 0, TEX - 1)
            ty = np.clip((v * TEX).astype(int), 0, TEX - 1)
            px = tex[ty, tx]
            draw &= px[..., 3] > 127
            col = tint(colour, px[..., 0]) * (shade * light)
        else:
            col = np.broadcast_to(np.asarray(colour, np.float32) * light, X.shape + (3,))
        zb[draw] = z[draw]
        self.rgb[y0:y1 + 1, x0:x1 + 1][draw] = col[draw]


def render(style, colour, view, size=200, tex=None, scale=10.0, swing=0.0):
    r = Raster(size * 2, scale * 2, size * 2 * 0.62)
    Ld = unit(v3(-0.35, -0.75, -0.55))

    def light_of(n):
        n = unit(n)
        return 0.45 + 0.55 * max(abs(np.dot(n, Ld)), 0)

    def box(x0, y0, z0, x1, y1, z1, rgb):
        cs = [v3(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
        for a, b, c_, d, n in ((0, 1, 3, 2, v3(-1, 0, 0)), (4, 6, 7, 5, v3(1, 0, 0)), (0, 4, 5, 1, v3(0, -1, 0)),
                               (2, 3, 7, 6, v3(0, 1, 0)), (0, 2, 6, 4, v3(0, 0, -1)), (1, 5, 7, 3, v3(0, 0, 1))):
            P = [project(cs[i], view) for i in (a, b, c_, d)]
            li = light_of(n)
            r.tri([P[0], P[1], P[2]], None, 1, rgb, None, li)
            r.tri([P[0], P[2], P[3]], None, 1, rgb, None, li)

    box(-4, -8, -4, 4, 0, 4, (214, 170, 132))
    box(-4, 0, -2, 4, 12, 2, (200, 158, 122))
    box(-8, 0, -2, -4, 12, 2, (190, 150, 116))
    box(4, 0, -2, 8, 12, 2, (190, 150, 116))
    for ex in (-1.6, 1.6):
        box(ex - 1.1, -4.1, -4.06, ex + 1.1, -3.1, -4.02, (24, 20, 24))
    for lock in style["locks"]:
        tex_kind = lock["tex"]
        if tex_kind == "clump":
            u0, uw = lock["cell"] * 32 / TEX, 32 / TEX
        else:
            u0, uw = (128 + lock["cell"] * 64) / TEX, 64 / TEX
        for q, face, v0, v1 in lock_quads(lock):
            n = np.cross(q[3] - q[0], q[1] - q[0])
            P = [project(p, view) for p in q]
            UV = [(u0, v0), (u0 + uw, v0), (u0 + uw, v1), (u0, v1)]
            li = light_of(n) if np.linalg.norm(n) > 1e-9 else 0.7
            sh = FACE_SHADE[face] * lock["shade"]
            r.tri([P[0], P[1], P[2]], [UV[0], UV[1], UV[2]], sh, colour, tex, li)
            r.tri([P[0], P[2], P[3]], [UV[0], UV[2], UV[3]], sh, colour, tex, li)
    img = Image.fromarray(np.clip(r.rgb, 0, 255).astype(np.uint8), "RGB")
    return img.resize((size, size), Image.LANCZOS)


ICONS = os.path.join(ASSETS, "textures/gui/hair_styles.png")
ICON = 48
ICON_COLS = 8
FIRST = 23   # styles before "none" in PlayerLook.Slot.HAIR


def icons(styles, tex):
    """The creator's style picker thumbnails: one ICON x ICON cell per style in PlayerLook's order (the last is
    bald), hair in light gray (the screen tints it with the chosen colour), the head a dark silhouette."""
    # PlayerLook's order: the first styles, bald, then the rest
    styles = list(styles[:FIRST]) + [{"locks": []}] + list(styles[FIRST:])
    n = len(styles)
    rows = (n + ICON_COLS - 1) // ICON_COLS
    sheet = np.zeros((rows * ICON, ICON_COLS * ICON, 4), np.uint8)
    big = ICON * 4
    for i in range(n):
        st = styles[i]
        r = Raster(big, big / 21.0, big * 0.66)
        r.rgb[:] = 0
        view = (32, 10)
        Ld = unit(v3(-0.35, -0.75, -0.55))
        head = [v3(x, y, z) for x in (-4, 4) for y in (-8, 0) for z in (-4, 4)]
        for a, b, c_, d, nrm in ((0, 1, 3, 2, v3(-1, 0, 0)), (4, 6, 7, 5, v3(1, 0, 0)), (0, 4, 5, 1, v3(0, -1, 0)),
                                 (2, 3, 7, 6, v3(0, 1, 0)), (0, 2, 6, 4, v3(0, 0, -1)), (1, 5, 7, 3, v3(0, 0, 1))):
            P = [project(head[k], view) for k in (a, b, c_, d)]
            li = 0.6 + 0.4 * abs(np.dot(nrm, Ld))
            r.tri([P[0], P[1], P[2]], None, 1, (70, 66, 64), None, li)
            r.tri([P[0], P[2], P[3]], None, 1, (70, 66, 64), None, li)
        for lock in st["locks"]:
            if lock["tex"] == "clump":
                u0, uw = lock["cell"] * 32 / TEX, 32 / TEX
            else:
                u0, uw = (128 + lock["cell"] * 64) / TEX, 64 / TEX
            for q, face, v0, v1 in lock_quads(lock):
                nrm = np.cross(q[3] - q[0], q[1] - q[0])
                P = [project(p, view) for p in q]
                UV = [(u0, v0), (u0 + uw, v0), (u0 + uw, v1), (u0, v1)]
                li = 0.5 + 0.5 * abs(np.dot(unit(nrm), Ld)) if np.linalg.norm(nrm) > 1e-9 else 0.7
                sh = FACE_SHADE[face] * lock["shade"]
                r.tri([P[0], P[1], P[2]], [UV[0], UV[1], UV[2]], sh, (205, 205, 205), tex, li)
                r.tri([P[0], P[2], P[3]], [UV[0], UV[2], UV[3]], sh, (205, 205, 205), tex, li)
        alpha = (r.z < 1e8).astype(np.float32) * 255
        # lit ×2: the pictures sit on dark tiles and are tinted with the hair colour in the creator
        img = np.concatenate([np.clip(r.rgb * 2.0, 0, 255), alpha[..., None]], -1).astype(np.uint8)
        small = np.array(Image.fromarray(img, "RGBA").resize((ICON, ICON), Image.LANCZOS))
        y0, x0 = (i // ICON_COLS) * ICON, (i % ICON_COLS) * ICON
        sheet[y0:y0 + ICON, x0:x0 + ICON] = small
    os.makedirs(os.path.dirname(ICONS), exist_ok=True)
    Image.fromarray(sheet, "RGBA").save(ICONS, optimize=True)
    print("wrote %d style icons to %s" % (n, os.path.relpath(ICONS, ROOT)))


def preview(styles, out_path, colours=None, views=None):
    tex = np.array(Image.open(TEXTURE).convert("RGBA"))
    views = views or [(0, 6), (40, 10), (90, 4), (180, 6)]
    colours = colours or [(30, 30, 36), (196, 70, 46), (226, 206, 140), (110, 136, 184), (60, 44, 36)]
    size = 200
    sheet = Image.new("RGB", (size * len(views), size * len(styles)), (30, 30, 34))
    for row, st in enumerate(styles):
        colour = colours[row % len(colours)]
        for c, view in enumerate(views):
            sheet.paste(render(st, colour, view, size, tex), (c * size, row * size))
        ImageDraw.Draw(sheet).text((4, row * size + 4), st["id"], fill=(230, 230, 230))
    sheet.save(out_path)
    print("wrote", out_path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("styles", nargs="*")
    a = ap.parse_args()
    texture()
    os.makedirs(STYLE_DIR, exist_ok=True)
    built = []
    for fn in ALL_STYLES:
        st = fn()
        data = st.json()
        with open(os.path.join(STYLE_DIR, st.id + ".json"), "w") as f:
            json.dump(data, f, separators=(",", ":"))
            f.write("\n")
        built.append(data)
    print("wrote %d hair styles (%d locks) to %s" % (len(built), sum(len(b["locks"]) for b in built),
                                                     os.path.relpath(STYLE_DIR, ROOT)))
    icons(built, np.array(Image.open(TEXTURE).convert("RGBA")))
    if a.preview:
        chosen = [b for b in built if not a.styles or b["id"] in a.styles]
        preview(chosen, a.preview)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""gen_shukaku.py - Shukaku, the One-Tail: writes models/bb/shukaku.bbmodel, textures/entity/beast/shukaku.png and
shukaku_glow.png (brief: docs/entities/shukaku.md).

Layout: per-face UV at RES x RES model px (shelf-packed, 1 px padding); the PNG is 4x that (64 px per block at the
authored size). Seed: 1 (from the id). Reruns give identical bytes.

The markings are painted as 3D "volumes": every texel knows its point on the rest-pose model, and the marking functions
take that point, so bands and rings line up across the faces of a cube and between cubes.

    python3 tools/beastgen/gen_shukaku.py            # writes the three files
    python3 tools/models/preview.py src/main/resources/assets/noirm/models/bb/shukaku.bbmodel --size 160
"""
import math
import os
import sys
import zlib

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'models'))
import bbmodel as bb  # noqa: E402

ID = 'shukaku'
MODEL = os.path.join(ROOT, 'src/main/resources/assets/noirm/models/bb', ID + '.bbmodel')
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/noirm/textures/entity/beast')
TEX = os.path.join(TEX_DIR, ID + '.png')
GLOW = os.path.join(TEX_DIR, ID + '_glow.png')
K = 4  # texels per model pixel (64 px per block)
SEED = 1


def hx(s):
    s = s.lstrip('#')
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


# ink, shadow, base, light, highlight (hue-shifted: shadows cooler, lights warmer)
PALETTE = {
    'fur': [hx('2A1C14'), hx('8E6A45'), hx('C9A46A'), hx('E2C48C'), hx('F2E2BC')],
    'mark': [hx('14162A'), hx('1E2240'), hx('2A2F55'), hx('3B4373'), hx('4A5488')],
    'mouth': [hx('140A0A'), hx('2A1212'), hx('3A1818'), hx('4A2020'), hx('5A2828')],
    'bone': [hx('3A3226'), hx('BDB49A'), hx('E6E0CC'), hx('F8F4E8'), hx('FFFFFF')],
    'nose': [hx('0E0C10'), hx('1A1620'), hx('262030'), hx('3A3248'), hx('8A86A0')],
}
EYE_SCLERA, EYE_IRIS, EYE_IRIS_LIGHT, EYE_PUPIL = hx('0E0C10'), hx('E8C030'), hx('FFE680'), hx('0E0C10')
GLOW_IRIS = hx('FFD84A')
LIGHT = np.array([-0.45, 0.75, -0.5]); LIGHT /= np.linalg.norm(LIGHT)  # above, front, the model's left (-x)

# Eye centres on the face (x, y), panel size; used by both the eye panels and the rings painted around them.
EYES = {'l': (-3.6, 39.2), 'r': (3.6, 39.2)}
EYE_W, EYE_H = 4.8, 3.4
HEAD_FRONT = -10.0

# ---------------------------------------------------------------- the model (bone tree from the brief)

CUBES = []  # (group, name, from, to, material, extra)


def cube(group, name, frm, to, mat='fur', rotation=(0, 0, 0), origin=None, sides=None, contact=()):
    CUBES.append(dict(group=group, name=name, frm=list(frm), to=list(to), mat=mat, rotation=rotation, origin=origin,
                      sides=sides, contact=set(contact)))


def build_groups(doc):
    g = {}

    def grp(name, origin, parent=None, rotation=(0, 0, 0)):
        g[name] = bb.add_group(doc, name, origin=origin, rotation=rotation, parent=g[parent] if parent else None)
        return name

    grp('root', [0, 0, 0])
    grp('body', [0, 13, 0], 'root')
    grp('belly', [0, 18, -1], 'body')
    grp('chest', [0, 27, 0], 'body')
    grp('head', [0, 32, -1], 'chest')
    grp('jaw', [0, 32, -7], 'head')
    grp('brow', [0, 41.5, -10], 'head')
    for s, sx in (('l', -1), ('r', 1)):
        ex, ey = EYES[s]
        grp('eye_' + s, [ex, ey, HEAD_FRONT], 'head')
        grp('lid_' + s, [ex, ey + EYE_H / 2, HEAD_FRONT - 0.3], 'head')
        grp('ear_' + s, [5 * sx, 43, -1.5], 'head', rotation=(0, 0, 14 * sx))
        grp('ear_' + s + '_tip', [5.6 * sx, 46, -1.5], 'ear_' + s)
        grp('arm_' + s, [9.5 * sx, 30, -1], 'chest', rotation=(8, 0, 16 * sx))
        grp('forearm_' + s, [10.5 * sx, 22.5, -1], 'arm_' + s, rotation=(28, 0, -6 * sx))
        grp('hand_' + s, [10.5 * sx, 15.5, -2], 'forearm_' + s, rotation=(10, 0, 0))
        grp('leg_' + s, [5 * sx, 12, 1], 'body')
        grp('shin_' + s, [5.5 * sx, 5.5, 1], 'leg_' + s)
        grp('foot_' + s, [5.5 * sx, 1.8, 0.5], 'shin_' + s)
    curl = [-14, -14, -20, -24, -26, -24]  # negative x lifts a chain that points back (+z)
    for i in range(6):
        z0 = 6 + 4.5 * i
        grp('tail%d' % (i + 1), [0, 11, z0], 'root' if i == 0 else 'tail%d' % i, rotation=(curl[i], 0, 0))
    return g


def build_cubes():
    # torso: pelvis, the big round belly (core + front bulge), the chest; the belly is the biggest mass
    cube('body', 'pelvis', [-8.5, 7, -6], [8.5, 13.5, 7], contact={'down'})
    cube('belly', 'belly', [-10, 9, -10], [10, 27, 7])
    cube('belly', 'belly_front', [-8, 11, -12.5], [8, 25, -9.5])
    cube('belly', 'belly_side_l', [-11, 12, -7], [-9.5, 24, 4])
    cube('belly', 'belly_side_r', [9.5, 12, -7], [11, 24, 4])
    cube('belly', 'belly_low', [-7, 9.5, -11.5], [7, 12, -9.5])
    cube('belly', 'belly_top', [-7.5, 26.5, -9], [7.5, 28, 4])
    cube('chest', 'chest', [-8.5, 25, -7.5], [8.5, 33, 6])
    cube('chest', 'hump', [-7, 21, 5.5], [7, 31, 8.5])
    # head: skull, muzzle that steps down, nose, jaw, mouth inside, brow ridge, cheeks
    cube('head', 'skull', [-8, 31, HEAD_FRONT], [8, 44, 4], contact={'down'})
    cube('head', 'cheek_l', [-9.5, 32, -8.5], [-7.5, 38, 1])
    cube('head', 'cheek_r', [7.5, 32, -8.5], [9.5, 38, 1])
    cube('head', 'muzzle', [-6, 32, -13.5], [6, 37, -9.5])
    cube('head', 'nose', [-1.6, 36, -14.2], [1.6, 37.6, -13.2], mat='nose')
    cube('head', 'mouth_inside', [-5.4, 30.4, -12.6], [5.4, 32.4, -8], mat='mouth')
    cube('jaw', 'jaw', [-5.5, 29.5, -13], [5.5, 32.2, -7])
    cube('brow', 'brow', [-7.5, 41, -10.9], [7.5, 42.6, -9.6])
    for s, sx in (('l', -1), ('r', 1)):
        ex, ey = EYES[s]
        cube('eye_' + s, 'eye_' + s, [ex - EYE_W / 2, ey - EYE_H / 2, HEAD_FRONT - 0.2], [ex + EYE_W / 2, ey + EYE_H / 2, HEAD_FRONT - 0.05],
             mat='eye', sides={'north'})
        # the eyelid rests above the eye (under the brow); code slides it down to blink
        cube('lid_' + s, 'lid_' + s, [ex - EYE_W / 2 - 0.2, ey + EYE_H / 2, HEAD_FRONT - 0.45],
             [ex + EYE_W / 2 + 0.2, ey + EYE_H / 2 + EYE_H + 0.2, HEAD_FRONT - 0.25])
        x0, x1 = sorted([3.2 * sx, 7.4 * sx])
        cube('ear_' + s, 'ear_' + s, [x0, 42.5, -3], [x1, 46.5, 0])
        x0, x1 = sorted([4.4 * sx, 6.8 * sx])
        cube('ear_' + s + '_tip', 'ear_' + s + '_tip', [x0, 46, -2.5], [x1, 49.5, -0.5])
        # arms: shoulder, forearm, hand with three claws
        x0, x1 = sorted([8.4 * sx, 12.6 * sx])
        cube('arm_' + s, 'arm_' + s, [x0, 22, -3], [x1, 31, 1], contact={'up'})
        x0, x1 = sorted([8.7 * sx, 12.3 * sx])
        cube('forearm_' + s, 'forearm_' + s, [x0, 15, -3.2], [x1, 23, 0.6])
        x0, x1 = sorted([8.4 * sx, 12.6 * sx])
        cube('hand_' + s, 'hand_' + s, [x0, 12.4, -4.6], [x1, 15.8, 0.2])
        for k, cx in enumerate((8.9, 10.1, 11.3)):
            x0, x1 = sorted([cx * sx, (cx + 0.9) * sx])
            cube('hand_' + s, 'claw_%s%d' % (s, k), [x0, 11, -5.8], [x1, 13.2, -4.4], mat='bone')
        # legs: haunch, shin, foot with toe claws
        x0, x1 = sorted([1.5 * sx, 9.5 * sx])
        cube('leg_' + s, 'leg_' + s, [x0, 4.5, -3.5], [x1, 13, 5.5])
        x0, x1 = sorted([3 * sx, 8 * sx])
        cube('shin_' + s, 'shin_' + s, [x0, 1.5, -2], [x1, 6.5, 3.5])
        x0, x1 = sorted([2.5 * sx, 8.5 * sx])
        cube('foot_' + s, 'foot_' + s, [x0, 0, -5.5], [x1, 2.3, 3.5], contact={'down'})
        for k, cx in enumerate((3.0, 4.9, 6.8)):
            x0, x1 = sorted([cx * sx, (cx + 1.1) * sx])
            cube('foot_' + s, 'toe_%s%d' % (s, k), [x0, 0, -6.7], [x1, 1.3, -5.4], mat='bone')
    # one thick tail: six segments, ~11 % thinner each, overlapping 1 px
    for i in range(6):
        z0 = 6 + 4.5 * i
        h = 4.8 * (0.89 ** i)
        cube('tail%d' % (i + 1), 'tail%d' % (i + 1), [-h, 11 - h, z0], [h, 11 + h, z0 + 5.5],
             contact={'north'} if i == 0 else ())


# ---------------------------------------------------------------- UV packing (shelf packer, per-face UV)

def face_size(c, side):
    dx, dy, dz = (abs(c['to'][i] - c['frm'][i]) for i in range(3))
    w, h = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[side]
    return max(1, math.ceil(w - 1e-4)), max(1, math.ceil(h - 1e-4))


def pack(res):
    items = []
    for ci, c in enumerate(CUBES):
        for side in bb.SIDES:
            if c['sides'] and side not in c['sides']:
                continue
            w, h = face_size(c, side)
            items.append((h, w, ci, side))
    items.sort(key=lambda t: (-t[0], -t[1]))
    x = y = shelf = 1
    out = {}
    for h, w, ci, side in items:
        if x + w + 1 > res:
            x, y = 1, y + shelf + 1
            shelf = 0
        if y + h + 1 > res:
            return None
        out[(ci, side)] = (x, y, w, h)
        x += w + 1
        shelf = max(shelf, h)
    return out


# ---------------------------------------------------------------- painting helpers

def rng_for(*parts):
    """A generator seeded from the id and the face, so every rerun paints the same texels."""
    return np.random.default_rng(zlib.crc32(':'.join(map(str, (ID, SEED) + parts)).encode()))


def face_world(c, side, tw, th):
    """World point (rest pose) of each texel centre on this face: arrays (th, tw, 3), and u, v in 0..1."""
    x0, y0, z0 = c['frm']; x1, y1, z1 = c['to']
    tl, tr, br, bl = (np.array(p, dtype=np.float32) for p in bb.corners(side, x0, y0, z0, x1, y1, z1))
    u = (np.arange(tw) + 0.5) / tw
    v = (np.arange(th) + 0.5) / th
    U, V = np.meshgrid(u, v)
    P = tl[None, None, :] + U[..., None] * (tr - tl)[None, None, :] + V[..., None] * (bl - tl)[None, None, :]
    return P, U, V


def form_normal(c, side, P):
    """A rounded normal: the face normal bent toward the cube's centre, so boxes shade like soft masses."""
    n = np.array(bb.NORMALS[side], dtype=np.float32)
    centre = (np.array(c['frm']) + np.array(c['to'])) / 2
    half = np.maximum(np.abs(np.array(c['to']) - np.array(c['frm'])) / 2, 0.5)
    q = (P - centre) / half
    N = n[None, None, :] * 0.85 + q * 0.55
    return N / np.linalg.norm(N, axis=-1, keepdims=True)


def cel(pal, lam, rng, highlight=True):
    """Hard-edged cel bands from a lambert value, with brushy jittered edges."""
    j = (rng.random(lam.shape) - 0.5) * 0.08
    l = lam + j
    out = np.empty(lam.shape + (3,), dtype=np.float32)
    out[:] = pal[2]
    out[l < 0.12] = pal[1]
    out[l > 0.58] = pal[3]
    if highlight:
        out[(l > 0.86) & (rng.random(lam.shape) > 0.55)] = pal[4]
    return out


def streaks(rng, th, tw, length=5):
    """Fur strands: noise smeared down the face (v), dark roots and light tips."""
    a = rng.random((th + length, tw))
    k = np.ones(length) / length
    s = np.apply_along_axis(lambda col: np.convolve(col, k, mode='valid'), 0, a)[:th]
    return s


def dilate(mask, r=1):
    out = mask.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx or dy:
                out |= np.roll(np.roll(mask, dy, 0), dx, 1)
    return out


# ---------------------------------------------------------------- markings (3D functions of the rest-pose point)

def markings(c, P):
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    g = c['group']
    m = np.zeros(x.shape, dtype=bool)
    if g.startswith('tail'):
        i = int(g[4:]) - 1
        zc = 6 + 4.5 * i + 2.75
        ang = np.arctan2(y - 11, x)
        band = np.abs(z - zc) < 0.6 + 0.4 * np.sin(ang * 3 + i)
        m |= band
        # crescents on top between the bands
        m |= (np.abs(z - zc + 2.25) < 0.45) & (y > 11 + 1.5)
        if i == 5:
            m |= z > 6 + 4.5 * 5 + 3.6  # a dark tip
    elif g == 'belly':
        # the seal "sun" on the belly front: rings and rays, and wavy bands round the sides
        r = np.hypot(x, (y - 18) * 1.1)
        front = z < -8.5
        ang = np.arctan2(y - 18, x)
        m |= front & (np.abs(r - 6.2) < 0.55 + 0.25 * np.sin(ang * 6))
        m |= front & (r < 2.2) & (r > 1.3)
        m |= front & (r > 7.2) & (r < 9.6) & (np.abs(((ang / (2 * np.pi) * 8) % 1.0) - 0.5) < 0.07)
        side = ~front
        m |= side & (np.sin(y * 0.6 + 1.6 * np.sin(z * 0.35)) > 0.9)
        m |= side & (np.abs(x) > 9.4) & ((np.round(y) % 3 == 0) & (np.abs(z % 3 - 1.5) < 0.5))  # dot rows
    elif g in ('chest', 'body'):
        m |= np.sin(y * 0.7 + 1.2 * np.sin(x * 0.4) + z * 0.15) > 0.9
        m |= (z > 6) & (np.abs(x) < 1.0 + 0.4 * np.sin(y))  # the spine line down the back
    elif g == 'head':
        for s, (ex, ey) in EYES.items():
            # dark rings round the eyes (an ellipse a little bigger than the eye)
            d = np.hypot((x - ex) / (EYE_W / 2 + 1.0), (y - ey) / (EYE_H / 2 + 0.9))
            m |= (z < -6) & (d < 1.0) & (d > 0.62)
            # tear streaks down the cheeks, tapering to a point
            t = (ey - EYE_H / 2 - y)
            m |= (z < -6) & (t > 0) & (t < 4.5) & (np.abs(x - ex * 1.25) < 0.9 * (1 - t / 4.5))
        # forehead crest: a wavy V with a dot
        fy = 42.8 - np.abs(x) * 0.35
        m |= (z < -6) & (np.abs(y - fy - 0.4 * np.sin(x * 1.3)) < 0.45) & (np.abs(x) < 6.5)
        m |= (z < -6) & (np.hypot(x, y - 43.3) < 0.6)
        # bands over the crown and the back of the head
        m |= (y > 43.5) & (np.sin(x * 0.9 + z * 0.4) > 0.88)
        m |= (z > 0) & (np.sin(y * 0.9 + x * 0.3) > 0.85)
        # cheeks: two curved stripes
        m |= (np.abs(x) > 7.4) & (np.sin(y * 1.1 + z * 0.5) > 0.85)
    elif g.startswith(('arm', 'forearm', 'leg', 'shin')):
        m |= np.sin(y * 0.9 + 0.8 * np.sin(z * 0.6)) > 0.86
        m |= (np.round(y * 0.8) % 4 == 1) & (np.abs(((np.arctan2(z, x) * 4) % 1.0) - 0.5) < 0.12)
    elif g.startswith('ear'):
        if g.endswith('tip'):
            m |= y > 47.8
    elif g in ('jaw',):
        m |= (z > -10) & (np.sin(x * 0.9) > 0.6)
    return m


# ---------------------------------------------------------------- face painters

def paint_eye(rgb, alpha, glow, tw, th, side_name):
    """Black sclera (almond, sharp corners), yellow iris, black four-point star pupil with four dots, one highlight."""
    U, V = np.meshgrid((np.arange(tw) + 0.5) / tw, (np.arange(th) + 0.5) / th)
    x = U * 2 - 1
    # outer corner (away from the nose) a little higher: a fierce slant
    outer = x if side_name == 'r' else -x
    v = V - 0.5 + 0.12 * outer
    almond = np.abs(v) < 0.5 * (1 - np.abs(x) ** 1.8) ** 0.8
    rgb[almond] = EYE_SCLERA
    alpha[almond] = 255
    cx, cy = 0.0, 0.0
    ix, iy = x / (th / tw), v * 2
    r = np.hypot(ix - cx, iy - cy)
    iris = almond & (r < 0.62)
    rgb[iris] = EYE_IRIS
    rgb[iris & (iy < -0.15)] = EYE_IRIS_LIGHT
    star = (np.abs(ix) ** 0.5 + np.abs(iy) ** 0.5) < 0.5 ** 0.5 * 1.0
    rgb[iris & star] = EYE_PUPIL
    for a in (45, 135, 225, 315):
        dx, dy = 0.42 * math.cos(math.radians(a)), 0.42 * math.sin(math.radians(a))
        rgb[iris & (np.hypot(ix - dx, iy - dy) < 0.09)] = EYE_PUPIL
    hl = iris & (np.hypot(ix + 0.3, iy + 0.32) < 0.08)
    rgb[hl] = hx('FFFFFF')
    glow_mask = iris & ~star
    glow[glow_mask, :3] = GLOW_IRIS
    glow[glow_mask, 3] = 255
    halo = dilate(iris, 1) & almond & ~glow_mask & ~star
    glow[halo, :3] = GLOW_IRIS
    glow[halo, 3] = np.maximum(glow[halo, 3], 110)


def paint_face(ci, c, side, tw, th):
    """Returns (rgb, alpha, glow) arrays for one face."""
    rng = rng_for(c['name'], side)
    rgb = np.zeros((th, tw, 3), dtype=np.float32)
    alpha = np.full((th, tw), 255, dtype=np.uint8)
    glow = np.zeros((th, tw, 4), dtype=np.float32)
    if c['mat'] == 'eye':
        alpha[:] = 0
        paint_eye(rgb, alpha, glow, tw, th, c['name'][-1])
        return rgb, alpha, glow
    P, U, V = face_world(c, side, tw, th)
    N = form_normal(c, side, P)
    lam = np.clip(N @ LIGHT, -1, 1)
    pal = PALETTE[c['mat']]
    rgb[:] = cel(pal, lam, rng, highlight=c['mat'] != 'mouth')
    if c['mat'] == 'fur':
        s = streaks(rng, th, tw)
        rgb *= (1 + (s - 0.5)[..., None] * 0.35)
        rgb[s < 0.33] = rgb[s < 0.33] * 0.9
        tips = (s > 0.68)
        rgb[tips] = rgb[tips] * 0.8 + pal[4] * 0.2
        m = markings(c, P)
        if c['name'] == 'muzzle':
            m[:] = False
        if m.any():
            mk = cel(PALETTE['mark'], lam, rng, highlight=False)
            rgb[m] = mk[m]
            edge = dilate(m, 1) & ~m
            rgb[edge] = rgb[edge] * 0.45 + PALETTE['mark'][0] * 0.55
        # short ink hatching in the shadow band
        hatch = (lam < 0.12) & (((np.arange(tw)[None, :] + np.arange(th)[:, None]) % 6) == 0) & (rng.random((th, tw)) > 0.4)
        rgb[hatch] = rgb[hatch] * 0.5 + pal[0] * 0.5
    # the mouth: a jagged, concave grin - dark inside, zig-zag teeth, no tongue
    if c['name'] == 'muzzle' and side == 'north':
        mouth_top = 0.52
        rgb[V > mouth_top] = PALETTE['mouth'][1]
        tooth = (V > mouth_top) & (V < mouth_top + 0.38 * (1 - np.abs(((U * 7) % 1.0) - 0.5) * 2))
        rgb[tooth] = PALETTE['bone'][2]
        rgb[tooth & (V > mouth_top + 0.25)] = PALETTE['bone'][1]
        lip = (np.abs(V - mouth_top) < 0.06)
        rgb[lip] = PALETTE['fur'][0]
    if c['name'] == 'muzzle' and side in ('east', 'west'):
        corner = (V > 0.5) & ((U < 0.45) if side == 'west' else (U > 0.55))
        rgb[corner] = PALETTE['mouth'][1]
    if c['name'] == 'jaw' and side == 'north':
        top = V < 0.5
        rgb[top] = PALETTE['mouth'][1]
        tooth = top & (V > 0.5 - 0.4 * (1 - np.abs(((U * 6 + 0.5) % 1.0) - 0.5) * 2))
        rgb[tooth] = PALETTE['bone'][2]
    if c['name'] == 'jaw' and side == 'up':
        rgb[:] = PALETTE['mouth'][2]
    if c['name'].startswith('ear_') and not c['name'].endswith('tip') and side == 'north':
        inner = (np.abs(U - 0.5) < 0.32 * (1 - V * 0.1)) & (V > 0.2)
        rgb[inner] = PALETTE['mark'][1]
    if c['name'] == 'nose' and side in ('north', 'up'):
        rgb[(np.hypot(U - 0.35, V - 0.3) < 0.15)] = PALETTE['nose'][4]
    # grain inside the tone bands
    rgb *= (1 + (rng.random((th, tw, 1)) - 0.5) * 0.1)
    # big contrast outlines: 2 texels on every edge, 3-4 where the part meets the body; brushy gaps on the lit side
    ink = PALETTE['mark'][0] * 0.4 + PALETTE[c['mat']][0] * 0.6
    w = 2
    yy, xx = np.mgrid[0:th, 0:tw]
    edge = (xx < w) | (xx >= tw - w) | (yy < w) | (yy >= th - w)
    thick = 4 if side in c['contact'] else 0
    if thick:
        edge |= (xx < thick) | (xx >= tw - thick) | (yy < thick) | (yy >= th - thick)
    if side == 'down' or c['name'] == 'mouth_inside':
        pass
    if side in ('up', 'north') and c['mat'] == 'fur':
        gaps = rng.random((th, tw)) > 0.0
        # break the line in a few short runs
        run = np.zeros((th, tw), dtype=bool)
        for _ in range(2):
            gx, gy = rng.integers(0, max(1, tw - 4)), rng.integers(0, max(1, th - 4))
            run[gy:gy + 2, gx:gx + 6] = True
        edge &= ~(run & gaps & ((yy < w) | (xx < w)))
    if c['mat'] not in ('eye',):
        rgb[edge] = rgb[edge] * 0.15 + ink * 0.85
    return np.clip(rgb, 0, 255), alpha, glow


# ---------------------------------------------------------------- animations (Blockbench keyframes)

def add_animation(doc, groups, name, length, tracks, loop='once'):
    """tracks: {bone: [(time, channel, (x, y, z), interpolation)]}. Rotation keyframes in Blockbench's sense:
    x and y turn opposite to the group rotation fields (BakedBbModel.animate follows the same rule)."""
    animators = {}
    for bone, keys in tracks.items():
        g = groups[bone]
        kfs = []
        for n, (t, ch, xyz, interp) in enumerate(keys):
            kfs.append({'channel': ch, 'data_points': [{'x': xyz[0], 'y': xyz[1], 'z': xyz[2]}],
                        'uuid': bb.stable_uid(ID, 'anim', name, bone, n), 'time': t, 'color': -1,
                        'interpolation': interp})
        animators[g['uuid']] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
    doc['animations'].append({'uuid': bb.stable_uid(ID, 'anim', name), 'name': name, 'loop': loop, 'override': False,
                              'length': length, 'snapping': 20, 'selected': False, 'anim_time_update': '',
                              'blend_weight': '', 'start_delay': '', 'loop_delay': '', 'animators': animators})


def animations(doc, groups):
    c = 'catmullrom'
    # Drilling Air Bullet: anticipation (lean back, right arm up), two pounds on the belly, the mouth opens, settle.
    add_animation(doc, groups, 'drum', 1.0, {
        'body': [(0, 'rotation', (0, 0, 0), c), (0.2, 'rotation', (8, 0, 0), c), (0.35, 'rotation', (-4, 0, 0), c),
                 (0.55, 'rotation', (6, 0, 0), c), (0.7, 'rotation', (-6, 0, 0), c), (1.0, 'rotation', (0, 0, 0), c)],
        'arm_r': [(0, 'rotation', (0, 0, 0), c), (0.2, 'rotation', (-70, 0, -20), c), (0.32, 'rotation', (-15, 0, 10), 'linear'),
                  (0.5, 'rotation', (-70, 0, -20), c), (0.62, 'rotation', (-15, 0, 10), 'linear'), (1.0, 'rotation', (0, 0, 0), c)],
        'arm_l': [(0, 'rotation', (0, 0, 0), c), (0.3, 'rotation', (-20, 0, 15), c), (1.0, 'rotation', (0, 0, 0), c)],
        'belly': [(0, 'scale', (1, 1, 1), c), (0.32, 'scale', (1.06, 0.94, 0.94), 'linear'), (0.4, 'scale', (1, 1, 1), c),
                  (0.62, 'scale', (1.08, 0.92, 0.92), 'linear'), (0.72, 'scale', (1, 1, 1), c)],
        'head': [(0, 'rotation', (0, 0, 0), c), (0.25, 'rotation', (-12, 0, 0), c), (0.7, 'rotation', (10, 0, 0), c),
                 (1.0, 'rotation', (0, 0, 0), c)],
        'jaw': [(0, 'rotation', (0, 0, 0), c), (0.6, 'rotation', (0, 0, 0), c), (0.72, 'rotation', (35, 0, 0), c),
                (0.9, 'rotation', (30, 0, 0), c), (1.0, 'rotation', (0, 0, 0), c)],
    })
    # Laugh: rocks back, jaw flaps, the belly shakes, arms out - proud and loud.
    keys_jaw, keys_belly, keys_body = [], [], []
    for n in range(9):
        t = round(0.2 + n * 0.15, 3)
        keys_jaw.append((t, 'rotation', (28 if n % 2 == 0 else 10, 0, 0), c))  # + opens: keyframe x is inverted
        keys_belly.append((t, 'scale', ((1.05, 0.96, 1.05) if n % 2 == 0 else (0.98, 1.02, 0.98)), c))
        keys_body.append((t, 'rotation', ((-10 if n % 2 == 0 else -6), 0, (3 if n % 4 == 0 else -3)), c))
    add_animation(doc, groups, 'laugh', 1.8, {
        'body': [(0, 'rotation', (0, 0, 0), c)] + keys_body + [(1.8, 'rotation', (0, 0, 0), c)],
        'jaw': [(0, 'rotation', (0, 0, 0), c)] + keys_jaw + [(1.8, 'rotation', (0, 0, 0), c)],
        'belly': [(0, 'scale', (1, 1, 1), c)] + keys_belly + [(1.8, 'scale', (1, 1, 1), c)],
        'head': [(0, 'rotation', (0, 0, 0), c), (0.3, 'rotation', (-20, 0, 0), c), (1.5, 'rotation', (-14, 0, 0), c),
                 (1.8, 'rotation', (0, 0, 0), c)],
        'arm_l': [(0, 'rotation', (0, 0, 0), c), (0.3, 'rotation', (-25, 0, 35), c), (1.5, 'rotation', (-25, 0, 30), c),
                  (1.8, 'rotation', (0, 0, 0), c)],
        'arm_r': [(0, 'rotation', (0, 0, 0), c), (0.3, 'rotation', (-25, 0, -35), c), (1.5, 'rotation', (-25, 0, -30), c),
                  (1.8, 'rotation', (0, 0, 0), c)],
    })


# ---------------------------------------------------------------- main

def main():
    build_cubes()
    res = next(r for r in (128, 160, 192, 224, 256) if pack(r) is not None)
    layout = pack(res)
    W = res * K
    sheet = np.zeros((W, W, 4), dtype=np.float32)
    glow = np.zeros((W, W, 4), dtype=np.float32)
    for (ci, side), (u, v, w, h) in layout.items():
        rgb, a, gl = paint_face(ci, CUBES[ci], side, w * K, h * K)
        sheet[v * K:(v + h) * K, u * K:(u + w) * K, :3] = rgb
        sheet[v * K:(v + h) * K, u * K:(u + w) * K, 3] = a
        glow[v * K:(v + h) * K, u * K:(u + w) * K] = gl
    os.makedirs(TEX_DIR, exist_ok=True)
    Image.fromarray(sheet.round().astype(np.uint8), 'RGBA').save(TEX, optimize=True)
    Image.fromarray(glow.round().astype(np.uint8), 'RGBA').save(GLOW, optimize=True)

    doc = bb.new_model(ID, res, res)
    groups = build_groups(doc)
    bb.add_texture(doc, TEX, relative_path='../../textures/entity/beast/%s.png' % ID, name=ID + '.png')
    for ci, c in enumerate(CUBES):
        faces = {}
        for side in bb.SIDES:
            if (ci, side) not in layout:
                continue
            u, v, w, h = layout[(ci, side)]
            faces[side] = [u, v, u + w, v + h]
        bb.add_cube(doc, groups[c['group']], c['name'], c['frm'], c['to'], origin=c['origin'], rotation=c['rotation'], faces=faces)
    animations(doc, groups)
    bb.save(doc, MODEL)
    print('shukaku: %d bones, %d cubes, UV %dx%d -> %dx%d px' % (len(groups), len(CUBES), res, res, W, W))


if __name__ == '__main__':
    main()

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
    'belly': [hx('2A1C14'), hx('A4825A'), hx('D9BC86'), hx('ECD6A6'), hx('F6EACA')],
    'plate': [hx('241812'), hx('7E5E3E'), hx('B8935E'), hx('D6B47E'), hx('EAD3A4')],
    'mark': [hx('14162A'), hx('1E2240'), hx('2A2F55'), hx('3B4373'), hx('4A5488')],
    'mouth': [hx('140A0A'), hx('2A1212'), hx('3A1818'), hx('4A2020'), hx('5A2828')],
    'bone': [hx('3A3226'), hx('BDB49A'), hx('E6E0CC'), hx('F8F4E8'), hx('FFFFFF')],
    'nose': [hx('0E0C10'), hx('1A1620'), hx('262030'), hx('3A3248'), hx('8A86A0')],
}
EYE_SCLERA, EYE_IRIS, EYE_IRIS_LIGHT, EYE_PUPIL = hx('0E0C10'), hx('E8C030'), hx('FFE680'), hx('0E0C10')
GLOW_IRIS = hx('FFD84A')
LIGHT = np.array([-0.45, 0.75, -0.5]); LIGHT /= np.linalg.norm(LIGHT)  # above, front, the model's left (-x)

# Eye centres on the face (x, y), panel size; used by both the eye panels and the black patches painted around them.
# Small, mean eyes set low in a head sunk between the shoulders (owner's reference notes, 2026-11-26).
EYES = {'l': (-3.4, 27.4), 'r': (3.4, 27.4)}
EYE_W, EYE_H = 4.0, 2.8
HEAD_FRONT = -26.0
TAIL_N = 7
TAIL_CURL = [-80, -22, -20, -18, -16, -13, -10]  # negative x lifts a chain that points back (+z): up, over, forward
TAIL_LEN = 6.0

# ---------------------------------------------------------------- the model (bone tree from the brief)

CUBES = []  # (group, name, from, to, material, extra)


def cube(group, name, frm, to, mat='fur', rotation=(0, 0, 0), origin=None, sides=None, contact=()):
    CUBES.append(dict(group=group, name=name, frm=list(frm), to=list(to), mat=mat, rotation=rotation, origin=origin,
                      sides=sides, contact=set(contact)))


def tail_half(i):
    return 6.2 * (0.9 ** i)


def build_groups(doc):
    """A hunched quadruped: hind legs under the hips, long thick arms as front legs, the head sunk between the
    shoulders, one huge plated tail arching over the back. Front toward -z, y up; legs hang from root so the body can
    rear up on them."""
    g = {}

    def grp(name, origin, parent=None, rotation=(0, 0, 0)):
        g[name] = bb.add_group(doc, name, origin=origin, rotation=rotation, parent=g[parent] if parent else None)
        return name

    grp('root', [0, 0, 0])
    grp('body', [0, 15, 7], 'root')          # pivots over the hind legs: + x rears the front up
    grp('belly', [0, 15, -2], 'body')
    grp('chest', [0, 26, -6], 'body')
    grp('head', [0, 26, -16], 'chest')
    grp('jaw', [0, 21.5, -21], 'head')
    grp('brow', [0, 30, -26], 'head')
    for s, sx in (('l', -1), ('r', 1)):
        ex, ey = EYES[s]
        grp('eye_' + s, [ex, ey, HEAD_FRONT], 'head')
        grp('lid_' + s, [ex, ey + EYE_H / 2, HEAD_FRONT - 0.3], 'head')
        grp('ear_' + s, [6.5 * sx, 31, -19.5], 'head', rotation=(0, 0, 28 * sx))
        grp('ear_' + s + '_tip', [7.2 * sx, 34.5, -19.5], 'ear_' + s)
        grp('arm_' + s, [11 * sx, 28, -10], 'chest', rotation=(0, 0, 4 * sx))
        grp('forearm_' + s, [11.5 * sx, 16, -10], 'arm_' + s)
        grp('hand_' + s, [11.5 * sx, 3.5, -10.5], 'forearm_' + s)
        grp('leg_' + s, [7.5 * sx, 15, 8], 'root')
        grp('shin_' + s, [8 * sx, 7, 9], 'leg_' + s)
        grp('foot_' + s, [8 * sx, 2.5, 8], 'shin_' + s)
    for i in range(TAIL_N):
        z0 = 12 + TAIL_LEN * i
        grp('tail%d' % (i + 1), [0, 16, z0], 'body' if i == 0 else 'tail%d' % i, rotation=(TAIL_CURL[i], 0, 0))
    return g


def build_cubes():
    # torso: haunches at the back, a huge round belly slung low, a top-heavy chest and shoulder hump in front
    cube('body', 'pelvis', [-8.5, 10, 1], [8.5, 22, 13])
    cube('belly', 'belly', [-11, 7.5, -10], [11, 25, 5])
    cube('belly', 'belly_low', [-9, 6, -8], [9, 8, 3], mat='belly')
    cube('belly', 'belly_front', [-8.5, 9, -12], [8.5, 22, -9.5], mat='belly')
    cube('belly', 'belly_side_l', [-12.2, 10, -7], [-10.5, 22, 2])
    cube('belly', 'belly_side_r', [10.5, 10, -7], [12.2, 22, 2])
    cube('chest', 'chest', [-12, 18, -16], [12, 33, -4], contact={'down'})
    cube('chest', 'hump', [-9.5, 30, -13], [9.5, 37, 0])
    cube('chest', 'back', [-9, 22, -4], [9, 30, 4])
    # head: low and wide, a broad muzzle, a jaw that opens very wide, a dark mouth
    cube('head', 'skull', [-8, 20, HEAD_FRONT], [8, 32, -14], contact={'south'})
    cube('head', 'cheek_l', [-9.5, 20.5, -24.5], [-7.5, 27, -16])
    cube('head', 'cheek_r', [7.5, 20.5, -24.5], [9.5, 27, -16])
    cube('head', 'muzzle', [-7, 21, -30], [7, 26, -25.5])
    cube('head', 'nose', [-1.7, 25, -30.8], [1.7, 26.6, -29.8], mat='nose')
    cube('head', 'mouth_inside', [-6.4, 18.6, -29], [6.4, 21.4, -21.5], mat='mouth')
    cube('jaw', 'jaw', [-6.5, 17.5, -29.6], [6.5, 21.2, -21])
    cube('brow', 'brow', [-7.5, 29.2, -26.9], [7.5, 30.8, -25.6])
    for s, sx in (('l', -1), ('r', 1)):
        ex, ey = EYES[s]
        cube('eye_' + s, 'eye_' + s, [ex - EYE_W / 2, ey - EYE_H / 2, HEAD_FRONT - 0.2], [ex + EYE_W / 2, ey + EYE_H / 2, HEAD_FRONT - 0.05],
             mat='eye', sides={'north'})
        # the eyelid rests above the eye (under the brow); code slides it down to blink
        cube('lid_' + s, 'lid_' + s, [ex - EYE_W / 2 - 0.2, ey + EYE_H / 2, HEAD_FRONT - 0.45],
             [ex + EYE_W / 2 + 0.2, ey + EYE_H / 2 + EYE_H + 0.2, HEAD_FRONT - 0.25])
        x0, x1 = sorted([5 * sx, 9 * sx])
        cube('ear_' + s, 'ear_' + s, [x0, 30.5, -21], [x1, 34.5, -18])
        x0, x1 = sorted([6 * sx, 8.4 * sx])
        cube('ear_' + s + '_tip', 'ear_' + s + '_tip', [x0, 34, -20.5], [x1, 37.5, -18.5])
        # long, thick arms that reach the ground: shoulder, forearm, a big paw with five claws
        x0, x1 = sorted([8.2 * sx, 14.2 * sx])
        cube('arm_' + s, 'arm_' + s, [x0, 15, -13.5], [x1, 31, -6], contact={'up'})
        x0, x1 = sorted([8.6 * sx, 14.2 * sx])
        cube('forearm_' + s, 'forearm_' + s, [x0, 3, -13.5], [x1, 17, -7])
        x0, x1 = sorted([7.8 * sx, 15 * sx])
        cube('hand_' + s, 'hand_' + s, [x0, 0, -16.5], [x1, 3.8, -7.5], contact={'down'})
        for k in range(5):
            cx = 8.1 + k * 1.42
            x0, x1 = sorted([cx * sx, (cx + 1.0) * sx])
            cube('hand_' + s, 'claw_%s%d' % (s, k), [x0, 0, -19], [x1, 2.2, -16.3], mat='bone')
        # short, thick hind legs
        x0, x1 = sorted([3 * sx, 12.5 * sx])
        cube('leg_' + s, 'leg_' + s, [x0, 6.5, 2.5], [x1, 18, 13.5])
        x0, x1 = sorted([4.5 * sx, 11.5 * sx])
        cube('shin_' + s, 'shin_' + s, [x0, 2, 5], [x1, 8, 12])
        x0, x1 = sorted([4 * sx, 12 * sx])
        cube('foot_' + s, 'foot_' + s, [x0, 0, 1.5], [x1, 2.8, 12], contact={'down'})
        for k in range(4):
            cx = 4.3 + k * 1.95
            x0, x1 = sorted([cx * sx, (cx + 1.2) * sx])
            cube('foot_' + s, 'toe_%s%d' % (s, k), [x0, 0, -0.2], [x1, 1.5, 1.6], mat='bone')
    # one enormous tail, as big as the body: a core per segment shingled with sand plates (top and both sides)
    for i in range(TAIL_N):
        z0 = 12 + TAIL_LEN * i
        h = tail_half(i)
        g = 'tail%d' % (i + 1)
        cube(g, g, [-h, 16 - h, z0], [h, 16 + h, z0 + TAIL_LEN + 1], contact={'north'} if i == 0 else ())
        # plates overlap the next segment and flare outward, like stacked slabs
        cube(g, g + '_plate_t', [-h - 0.7, 16 + h - 0.4, z0 + 0.6], [h + 0.7, 16 + h + 1.1, z0 + TAIL_LEN + 1.6], mat='plate',
             rotation=(-10, 0, 0), origin=[0, 16 + h, z0 + 0.6])
        cube(g, g + '_plate_l', [-h - 1.1, 16 - h * 0.7, z0 + 0.6], [-h + 0.4, 16 + h * 0.8, z0 + TAIL_LEN + 1.6], mat='plate',
             rotation=(0, 10, 0), origin=[-h, 16, z0 + 0.6])
        cube(g, g + '_plate_r', [h - 0.4, 16 - h * 0.7, z0 + 0.6], [h + 1.1, 16 + h * 0.8, z0 + TAIL_LEN + 1.6], mat='plate',
             rotation=(0, -10, 0), origin=[h, 16, z0 + 0.6])


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

def spiral(a, b, ca, cb, turns=2.2, size=4.5, width=0.55):
    """A spiral 'comma' on a face plane: (a, b) coordinates around a centre, `turns` turns out to `size`."""
    da, db = a - ca, b - cb
    r = np.hypot(da, db)
    th = np.arctan2(db, da)
    pitch = size / turns
    k = (r - pitch * (th + np.pi) / (2 * np.pi)) % pitch
    return (r < size) & ((k < width) | (k > pitch - width * 0.3)) | (r < width * 1.2)


def markings(c, P):
    """Dark navy seal markings (the identity read): swirls and commas, flame streaks, stripes across the limbs and
    belly, spirals on the shoulders and forehead, black eye patches, a dark mark on every tail plate."""
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    g, name = c['group'], c['name']
    m = np.zeros(x.shape, dtype=bool)
    if '_plate' in name:
        # one dark comma-shaped mark in the middle of each plate
        i = int(g[4:]) - 1
        zc = 12 + TAIL_LEN * i + TAIL_LEN * 0.55
        if name.endswith('_t'):
            m |= np.hypot((x) / 2.2, (z - zc) / 1.6) < 1.0
            m &= ~(np.hypot((x - 0.9) / 1.3, (z - zc + 0.6) / 1.0) < 0.8)
        else:
            m |= np.hypot((y - 16) / 1.8, (z - zc) / 1.5) < 1.0
            m &= ~(np.hypot((y - 16.8) / 1.0, (z - zc + 0.5) / 0.9) < 0.8)
    elif g.startswith('tail'):
        m |= False  # the core shows only in the gaps: plain sand
    elif g == 'belly':
        # stripes that curve down across the belly, and a comma on each flank
        front = z < -8.5
        m |= front & (np.sin(y * 0.62 - 0.022 * x * x) > 0.82)
        side = np.abs(x) > 10.4
        m |= side & spiral(z, y, -2.5, 16, turns=1.6, size=4.2)
        m |= ~front & ~side & (np.sin(z * 0.5 + 2 * np.sin(x * 0.3) + y * 0.2) > 0.85)
    elif g == 'body':
        m |= np.sin(z * 0.55 + 2.0 * np.sin(x * 0.35) + y * 0.1) > 0.84   # flame streaks over the haunches
    elif g == 'chest':
        # the shoulder spirals, and flame streaks along the hump
        shoulder = np.abs(x) > 11.5
        m |= shoulder & spiral(z, y, -10, 26, turns=2.0, size=5.5)
        m |= ~shoulder & (y > 30) & (np.sin(x * 0.7 + z * 0.35) > 0.8)
        m |= ~shoulder & (y <= 30) & (z > -4) & (np.sin(y * 0.7 + x * 0.3) > 0.85)
    elif g == 'head':
        for s, (ex, ey) in EYES.items():
            # black eye patches (filled), a bit bigger than the eye and drawn up at the outer corner
            d = np.hypot((x - ex) / (EYE_W / 2 + 1.3), (y - ey - 0.15 * (x - ex) * np.sign(ex)) / (EYE_H / 2 + 1.0))
            m |= (z < -24) & (d < 1.0)
        # a spiral on the forehead
        m |= (z < -24) & spiral(x, y, 0, 30.6, turns=1.6, size=2.4, width=0.45)
        # flame streaks back over the crown and cheeks
        m |= (y > 31.5) & (np.sin(x * 0.9 + z * 0.45) > 0.8)
        m |= (np.abs(x) > 7.4) & (np.sin(y * 1.1 + z * 0.6) > 0.8)
        m |= (z > -16) & (np.sin(y * 0.9 + x * 0.4) > 0.82)
    elif g.startswith(('arm', 'forearm', 'leg', 'shin')):
        # stripes across the limbs, wavy like flames
        m |= np.sin(y * 0.95 + 0.9 * np.sin(z * 0.6 + x * 0.3)) > 0.8
    elif g.startswith('ear'):
        if g.endswith('tip'):
            m |= y > 35.3
    elif g == 'jaw':
        m |= (z > -27) & (np.sin(x * 0.9) > 0.65)
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
    if c['mat'] in ('fur', 'belly', 'plate'):
        # packed sand / sandstone: short smeared strands plus a speckle of dark and light grains
        s = streaks(rng, th, tw, length=3)
        rgb *= (1 + (s - 0.5)[..., None] * 0.22)
        speck = rng.random((th, tw))
        rgb[speck < 0.06] = rgb[speck < 0.06] * 0.78
        rgb[speck > 0.95] = rgb[speck > 0.95] * 0.7 + pal[4] * 0.3
        if c['mat'] == 'plate':
            # slab strata: a couple of thin ink cracks across each plate
            strata = (np.abs(((V * 3.2 + 0.15 * np.sin(U * 9)) % 1.0) - 0.5) < 0.03)
            rgb[strata] = rgb[strata] * 0.55 + pal[0] * 0.45
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
        mouth_top = 0.42
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
    if side in ('up', 'north') and c['mat'] in ('fur', 'belly'):
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
    """Keyframe x and y are inverted (Blockbench's convention): body x -50 rears the front up 50 degrees, head x +35
    tips the nose down to keep the eyes on the target, jaw x +40 opens the mouth."""
    c = 'catmullrom'
    # Drilling Air Bullet: rears up off its front paws, pounds the belly twice, the mouth gapes, then drops back down.
    add_animation(doc, groups, 'drum', 1.0, {
        'body': [(0, 'rotation', (0, 0, 0), c), (0.2, 'rotation', (-50, 0, 0), c), (0.7, 'rotation', (-46, 0, 0), c),
                 (0.88, 'rotation', (6, 0, 0), c), (1.0, 'rotation', (0, 0, 0), c)],
        'head': [(0, 'rotation', (0, 0, 0), c), (0.2, 'rotation', (38, 0, 0), c), (0.7, 'rotation', (30, 0, 0), c),
                 (1.0, 'rotation', (0, 0, 0), c)],
        'arm_r': [(0, 'rotation', (0, 0, 0), c), (0.18, 'rotation', (-45, 0, 15), c), (0.3, 'rotation', (35, 0, -28), 'linear'),
                  (0.45, 'rotation', (-45, 0, 15), c), (0.58, 'rotation', (35, 0, -28), 'linear'), (1.0, 'rotation', (0, 0, 0), c)],
        'arm_l': [(0, 'rotation', (0, 0, 0), c), (0.25, 'rotation', (-45, 0, -15), c), (0.37, 'rotation', (35, 0, 28), 'linear'),
                  (0.52, 'rotation', (-45, 0, -15), c), (0.64, 'rotation', (35, 0, 28), 'linear'), (1.0, 'rotation', (0, 0, 0), c)],
        'belly': [(0, 'scale', (1, 1, 1), c), (0.3, 'scale', (1.07, 0.93, 0.93), 'linear'), (0.4, 'scale', (1, 1, 1), c),
                  (0.58, 'scale', (1.08, 0.92, 0.92), 'linear'), (0.68, 'scale', (1, 1, 1), c)],
        'jaw': [(0, 'rotation', (0, 0, 0), c), (0.6, 'rotation', (0, 0, 0), c), (0.7, 'rotation', (42, 0, 0), c),
                (0.86, 'rotation', (36, 0, 0), c), (1.0, 'rotation', (0, 0, 0), c)],
    })
    # Cackle: half rears, throws its head back, jaw flapping, arms flailing out, the belly shaking.
    keys_jaw, keys_belly, keys_body = [], [], []
    for n in range(9):
        t = round(0.2 + n * 0.15, 3)
        keys_jaw.append((t, 'rotation', (32 if n % 2 == 0 else 12, 0, 0), c))
        keys_belly.append((t, 'scale', ((1.05, 0.96, 1.05) if n % 2 == 0 else (0.98, 1.02, 0.98)), c))
        keys_body.append((t, 'rotation', ((-26 if n % 2 == 0 else -20), 0, (3 if n % 4 == 0 else -3)), c))
    add_animation(doc, groups, 'laugh', 1.8, {
        'body': [(0, 'rotation', (0, 0, 0), c)] + keys_body + [(1.8, 'rotation', (0, 0, 0), c)],
        'jaw': [(0, 'rotation', (0, 0, 0), c)] + keys_jaw + [(1.8, 'rotation', (0, 0, 0), c)],
        'belly': [(0, 'scale', (1, 1, 1), c)] + keys_belly + [(1.8, 'scale', (1, 1, 1), c)],
        'head': [(0, 'rotation', (0, 0, 0), c), (0.3, 'rotation', (-5, 0, 0), c), (1.5, 'rotation', (2, 0, 0), c),
                 (1.8, 'rotation', (0, 0, 0), c)],
        'arm_l': [(0, 'rotation', (0, 0, 0), c), (0.3, 'rotation', (-35, 0, -40), c), (1.5, 'rotation', (-30, 0, -32), c),
                  (1.8, 'rotation', (0, 0, 0), c)],
        'arm_r': [(0, 'rotation', (0, 0, 0), c), (0.3, 'rotation', (-35, 0, 40), c), (1.5, 'rotation', (-30, 0, 32), c),
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

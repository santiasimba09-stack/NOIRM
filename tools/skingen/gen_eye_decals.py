#!/usr/bin/env python3
"""High-detail eyes for NOIRM (docs/ART_STYLE.md, "Hair, eyes and bodies rework").

The skin's eyes are 4 HD pixels per model pixel: too coarse for a crisp Sharingan. client/EyeDecalLayer
draws each eye once more as a small flat quad just in front of the face, from an atlas drawn here at
RES pixels per model pixel, one atlas per eye shape (anatomy.EYE_SHAPES):

    textures/entity/eyes/<shape>.png   CELLS cells of CELL_W x CELL_H, left to right:
        0 base      the white of the eye (lid shadow, pink inner corner) and the lower lid line
        1 iris      gray (tinted by the eye colour in game): fibres, dark limbal ring, pupil, lid shadow
        2 top       the heavy upper lash line with its outer flick and lashes, the lid crease, the highlight
        3-5 sharingan  one, two and three tomoe (full colour, drawn full-bright)
        6 byakugan  the pale, pupil-less iris (full colour, full-bright; the veins stay on the skin)
        7 rinne     reserved (empty)

Each cell covers the texture-left eye's box: face x from EYE_XS[0] + X_FROM to + X_TO, y from EYE_Y + Y_FROM
to + Y_TO (head-front texture pixels); the other eye is the mirror image. Keep those numbers in step with
client/EyeDecalLayer. The lids are the same curves as the skin's eyes (anatomy.lid_curves), so the decal
sits exactly on them.

All art is original and drawn by this code.  Run:  python3 tools/skingen/gen_eye_decals.py
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anatomy as A  # noqa: E402
from hdskin import hash01, vnoise, hx  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/entity/eyes")

RES = 32
X_FROM, X_TO = -2.25, 1.75
Y_FROM, Y_TO = -1.65, 0.85
CELL_W, CELL_H = 128, 80
CELLS = 8
ATLAS_W, ATLAS_H = 1024, 128

WHITE = np.array(hx("#e2dcd2"), np.float32)
WHITE_SHADE = np.array(hx("#7c726e"), np.float32)
WHITE_INNER = np.array(hx("#c9a49a"), np.float32)
LASH = np.array(hx("#0b0709"), np.float32)
LID = np.array(hx("#4a2620"), np.float32)
CREASE = np.array(hx("#3a1d18"), np.float32)
HOOD = np.array(hx("#2a1410"), np.float32)
RED = np.array(hx("#c8121c"), np.float32)
RED_LIGHT = np.array(hx("#f0403a"), np.float32)
RED_DARK = np.array(hx("#6e0810"), np.float32)
BLACK = np.array(hx("#0a0204"), np.float32)
PALE = np.array(hx("#ece8f4"), np.float32)
PALE_EDGE = np.array(hx("#a89cc0"), np.float32)


def grid():
    cx = A.EYE_XS[0]
    xs = cx + X_FROM + (np.arange(CELL_W, dtype=np.float32) + 0.5) / RES
    ys = A.EYE_Y + Y_FROM + (np.arange(CELL_H, dtype=np.float32) + 0.5) / RES
    return np.meshgrid(xs, ys)


def aa(signed):
    """Coverage from a signed distance in model px (positive inside), one hi-res pixel of antialiasing."""
    return np.clip(signed * RES + 0.5, 0, 1)


def seg_cov(X, Y, pts, w0, w1=None):
    """Coverage of a tapered polyline (widths w0 -> w1 along its length)."""
    w1 = w0 if w1 is None else w1
    pts = np.asarray(pts, np.float32)
    lens = np.sqrt(((pts[1:] - pts[:-1]) ** 2).sum(1))
    total = max(lens.sum(), 1e-6)
    start = np.concatenate([[0], np.cumsum(lens)])[:-1]
    best = np.zeros_like(X)
    for i in range(len(pts) - 1):
        ax, ay = pts[i]
        bx, by = pts[i + 1]
        dx, dy = bx - ax, by - ay
        ll = dx * dx + dy * dy
        t = np.clip(((X - ax) * dx + (Y - ay) * dy) / max(ll, 1e-9), 0, 1)
        d = np.sqrt((X - ax - t * dx) ** 2 + (Y - ay - t * dy) ** 2)
        w = w0 + (w1 - w0) * (start[i] + t * lens[i]) / total
        best = np.maximum(best, aa(w / 2 - d))
    return best


def over(rgb, a, cov, colour):
    cov = np.clip(np.asarray(cov, np.float32), 0, 1)
    col = np.broadcast_to(np.asarray(colour, np.float32), rgb.shape)
    out_a = cov + a * (1 - cov)
    safe = np.where(out_a > 1e-6, out_a, 1)
    rgb = (col * cov[..., None] + rgb * (a * (1 - cov))[..., None]) / safe[..., None]
    return rgb, out_a


def lid_line(cx, s, which, n=48, u0=0.0, u1=1.0):
    xin, xout = A.corner_xs(cx, s)
    xs = np.linspace(xin + (xout - xin) * u0, xin + (xout - xin) * u1, n)
    top, bottom = A.lid_curves(xs, cx, s)
    return list(zip(xs, top if which == "top" else bottom))


def opening(X, Y, cx, s):
    top, bottom = A.lid_curves(X, cx, s)
    u = A.across_u(X, cx, s)
    across = aa(np.minimum(u, 1 - u) * (s["ein"] + s["eout"]))
    return np.clip(aa(Y - top) * aa(bottom - Y) * across, 0, 1), top, bottom, u


def iris_geo(cx, s):
    ix, iy = A.iris_centre(cx, s)
    r = s["iris"]
    return ix, iy, r


def cell_base(shape):
    s = A.EYE_SHAPES[shape]
    X, Y = grid()
    cx = A.EYE_XS[0]
    rgb = np.zeros(X.shape + (3,), np.float32)
    a = np.zeros(X.shape, np.float32)
    inside, top, bottom, u = opening(X, Y, cx, s)
    # the white: shaded under the upper lid (it casts a shadow), pinker at the inner corner, a little
    # shade along the lower lid
    shade = np.clip((Y - top) / 0.32, 0, 1)
    col = WHITE_SHADE + (WHITE - WHITE_SHADE) * shade[..., None]
    col = col * (1 - 0.18 * np.clip((Y - (bottom - 0.18)) / 0.18, 0, 1))[..., None]
    rgb, a = over(rgb, a, inside, col)
    # the lower lid: a thin line on the middle-to-outer part only, stopping short of the outer corner so
    # the eye stays open and sharp there (the upper sweep carries the corner), plus a faint inner touch
    lo = [(x, y + 0.05) for x, y in lid_line(cx, s, "bottom", u0=0.42, u1=0.9)]
    rgb, a = over(rgb, a, seg_cov(X, Y, lo, 0.05, 0.13), LID)
    li = [(x, y + 0.04) for x, y in lid_line(cx, s, "bottom", u0=0.1, u1=0.26)]
    rgb, a = over(rgb, a, seg_cov(X, Y, li, 0.03, 0.05) * 0.5, LID)
    if s.get("bags"):
        # sleepy: a soft line under the eye
        bg = [(x, y + 0.24) for x, y in lid_line(cx, s, "bottom", u0=0.28, u1=0.82)]
        rgb, a = over(rgb, a, seg_cov(X, Y, bg, 0.03, 0.07) * 0.45, CREASE)
    return rgb, a


def cell_iris(shape):
    """The iris as gray (1 = the eye colour, lighter in game), masked to the opening."""
    s = A.EYE_SHAPES[shape]
    pale = shape == "pale"
    X, Y = grid()
    cx = A.EYE_XS[0]
    inside, top, bottom, u = opening(X, Y, cx, s)
    ix, iy, r = iris_geo(cx, s)
    d = np.sqrt((X - ix) ** 2 + (Y - iy) ** 2) / r
    ang = np.arctan2(Y - iy, X - ix)
    disc = aa((1 - d) * r)
    v = (Y - iy) / r
    g = np.where(v > 0.05, 1.0, 0.72) + 0.18 * np.clip(v, 0, 1)
    # fibres: fine radial streaks, a little lighter collarette ring around the pupil
    fib = vnoise(ang * 9.0 + 3.0, d * 3.0, 5)
    g = g * (0.82 + 0.3 * fib)
    g = np.where(np.abs(d - 0.5) < 0.05, g * 1.15, g)
    # the dark limbal ring and the lid's shadow across the top
    g = np.where(d > 0.84, 0.18 + 0.15 * (d < 0.9), g)
    g = g * (0.3 + 0.7 * np.clip((Y - top) / 0.22, 0, 1))
    if pale:
        g = np.where(d > 0.84, 0.55, np.clip(g * 0.4 + 0.6, 0, 1))
    else:
        pupil = aa((0.3 - d) * r)
        g = g * (1 - pupil) + 0.03 * pupil
    g = np.clip(g, 0, 1)
    rgb = np.repeat((g * 255)[..., None], 3, -1).astype(np.float32)
    return rgb, disc * inside


def cell_top(shape):
    """The upper lash line sweeping past the outer corner, the crease line above it, a light lid shadow,
    one small highlight."""
    s = A.EYE_SHAPES[shape]
    X, Y = grid()
    cx = A.EYE_XS[0]
    rgb = np.zeros(X.shape + (3,), np.float32)
    a = np.zeros(X.shape, np.float32)
    inside, top, bottom, u = opening(X, Y, cx, s)
    lash = s["lash"]
    # a light shadow on the lid, between the lash line and the crease (not a heavy band)
    span = (u > 0.05) & (u < 1.0)
    gap = (top - lash * 0.25) - Y
    shadow = np.where(span & (gap > 0) & (gap < s["hood"]), 0.22 * (1 - gap / s["hood"]), 0)
    rgb, a = over(rgb, a, shadow, HOOD)
    # the crease: a separate thin line following the lid above it, fading in from the inner side
    crease = [(x, y - s["crease"] - lash * 0.3) for x, y in lid_line(cx, s, "top", u0=0.2, u1=0.98)]
    rgb, a = over(rgb, a, seg_cov(X, Y, crease, 0.02, 0.07) * 0.75, CREASE)
    # the lash line: thin at the inner corner, thickest past the peak, riding just over the lid
    up = lid_line(cx, s, "top", u0=0.02, u1=1.0)
    centre = [(x, y - lash * 0.25) for x, y in up]
    rgb, a = over(rgb, a, seg_cov(X, Y, centre, lash * 0.25, lash), LASH)
    # the sweep: the line carries on past the outer point in the lid's direction as a long thin stroke,
    # bending a little upwards at its end (the "end of the eye" in the owner's references)
    (x1, y1), (x2, y2) = centre[-8], centre[-1]
    dx, dy = x2 - x1, y2 - y1
    n = max(np.hypot(dx, dy), 1e-6)
    dx, dy = dx / n, dy / n
    wing = s["wing"]
    pts = []
    for k in range(9):
        t = k / 8
        pts.append((x2 + dx * wing * t, y2 + dy * wing * t - 0.1 * wing * t * t))
    rgb, a = over(rgb, a, seg_cov(X, Y, [(x2 - dx * 0.12, y2 - dy * 0.12)] + pts, lash * 0.9, 0.015), LASH)
    # the inner corner: the lash line hooks down a little past the corner, towards the nose
    xi, yi = centre[0]
    hook = [(xi, yi), (xi + 0.12, yi + 0.1), (xi + 0.2, yi + 0.24)]
    rgb, a = over(rgb, a, seg_cov(X, Y, hook, lash * 0.3, 0.02), LASH)
    # a thin warm rim under the lash line where it meets the white
    rim = np.clip(aa(Y - (top + 0.02)) - aa(Y - (top + 0.07)), 0, 1) * inside
    rgb, a = over(rgb, a, rim * 0.5, LID)
    # one small highlight, up and to the side
    ix, iy, r = iris_geo(cx, s)
    gx, gy = ix - r * 0.4, iy - r * 0.15
    glint = aa(0.075 - np.hypot(X - gx, (Y - gy) * 1.2))
    rgb, a = over(rgb, a, glint * 0.95 * inside * (Y > top + 0.06), (255, 255, 255))
    return rgb, a


def cell_sharingan(shape, tomoe):
    s = A.EYE_SHAPES[shape]
    X, Y = grid()
    cx = A.EYE_XS[0]
    inside, top, bottom, u = opening(X, Y, cx, s)
    ix, iy, r = iris_geo(cx, s)
    dx, dy = X - ix, Y - iy
    d = np.sqrt(dx * dx + dy * dy) / r
    ang = np.arctan2(dy, dx)
    disc = aa((1 - d) * r)
    v = dy / r
    t = np.clip((v + 1) / 2, 0, 1)[..., None]
    rgb = RED_DARK + (RED - RED_DARK) * np.clip(t * 1.6, 0, 1) + (RED_LIGHT - RED) * np.clip(t * 2 - 1.1, 0, 1)
    rgb = rgb * (0.9 + 0.2 * vnoise(ang * 8, d * 2.5, 11))[..., None]
    a = disc * inside
    # the black limbal ring, the pupil, the faint ring the tomoe sit on
    ring = aa((d - 0.86) * r)
    rgb, _ = over(rgb, a, ring * disc, BLACK)
    rgb, _ = over(rgb, a, aa((0.24 - d) * r), BLACK)
    rgb, _ = over(rgb, a, aa((0.035 - np.abs(d - 0.56)) * r) * 0.75, RED_DARK * 0.6)
    # tomoe: a round head on the ring and a tail curling around it, spaced evenly
    rr = 0.56 * r
    base = -math.pi / 2 + (0.35 if tomoe == 2 else 0.0)
    for k in range(tomoe):
        th = base + 2 * math.pi * k / tomoe
        hx_, hy_ = ix + rr * math.cos(th), iy + rr * math.sin(th)
        head = aa(0.2 * r - np.sqrt((X - hx_) ** 2 + (Y - hy_) ** 2))
        tail_pts = []
        for j in range(10):
            q = j / 9
            a2 = th + 1.25 * q
            rad = rr + 0.17 * r * math.sin(q * math.pi * 0.9)
            tail_pts.append((ix + rad * math.cos(a2), iy + rad * math.sin(a2)))
        tail = seg_cov(X, Y, tail_pts, 0.3 * r, 0.02)
        rgb, _ = over(rgb, a, np.maximum(head, tail), BLACK)
    # the lid's shadow across the top
    rgb = rgb * (0.45 + 0.55 * np.clip((Y - top) / 0.26, 0, 1))[..., None]
    return rgb, a


def cell_byakugan(shape):
    s = A.EYE_SHAPES[shape]
    X, Y = grid()
    cx = A.EYE_XS[0]
    inside, top, bottom, u = opening(X, Y, cx, s)
    ix, iy, r = iris_geo(cx, s)
    d = np.sqrt((X - ix) ** 2 + (Y - iy) ** 2) / r
    disc = aa((1 - d) * r)
    rgb = PALE + (PALE_EDGE - PALE) * np.clip((d - 0.55) / 0.45, 0, 1)[..., None]
    rgb = np.where((np.abs(d - 0.6) < 0.04)[..., None], PALE_EDGE * 1.05, rgb)
    rgb = rgb * (0.72 + 0.28 * np.clip((Y - top) / 0.26, 0, 1))[..., None]
    return rgb, disc * inside


def atlas(shape):
    out = np.zeros((ATLAS_H, ATLAS_W, 4), np.float32)
    cells = [cell_base(shape), cell_iris(shape), cell_top(shape)]
    cells += [cell_sharingan(shape, n) for n in (1, 2, 3)]
    cells.append(cell_byakugan(shape))
    for i, (rgb, a) in enumerate(cells):
        x0 = i * CELL_W
        out[:CELL_H, x0:x0 + CELL_W, :3] = np.clip(rgb, 0, 255)
        out[:CELL_H, x0:x0 + CELL_W, 3] = np.clip(a, 0, 1) * 255
    img = np.round(out).astype(np.uint8)
    img[img[..., 3] == 0, :3] = 0
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for shape in A.EYE_SHAPES:
        Image.fromarray(atlas(shape), "RGBA").save(os.path.join(OUT_DIR, shape + ".png"), optimize=True)
    print("wrote %d eye atlases (%dx%d, %d px per model px) to %s" % (len(A.EYE_SHAPES), ATLAS_W, ATLAS_H, RES,
                                                                      os.path.relpath(OUT_DIR, ROOT)))


if __name__ == "__main__":
    main()

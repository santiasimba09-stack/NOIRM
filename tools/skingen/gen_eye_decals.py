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

RES = 16
X_FROM, X_TO = -2.25, 1.75
Y_FROM, Y_TO = -1.65, 0.85
CELL_W, CELL_H = 64, 40
CELLS = 8
ATLAS_W, ATLAS_H = 512, 64

WHITE = np.array(hx("#ece6dc"), np.float32)
WHITE_SHADE = np.array(hx("#9a8e88"), np.float32)
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
    inner = np.clip((0.16 - u) / 0.1, 0, 1)
    col = col + (WHITE_INNER - col) * inner[..., None]
    rgb, a = over(rgb, a, inside, col)
    # the lower lid: a thin line along the outer two thirds, darker at its outer end, with two short lashes
    lo = lid_line(cx, s, "bottom", u0=0.35, u1=0.98)
    lo = [(x, y + 0.06) for x, y in lo]
    rgb, a = over(rgb, a, seg_cov(X, Y, lo, 0.05, 0.16) * 0.85, LID)
    ox, oy = lo[-1]
    out = -1.0
    for k, (dx, dy) in enumerate(((0.18, 0.14), (0.3, 0.06))):
        bx, by = lo[-6 - 4 * k]
        rgb, a = over(rgb, a, seg_cov(X, Y, [(bx, by), (bx + out * dx, by + dy)], 0.06, 0.01) * 0.7, LID)
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
    g = g * (0.35 + 0.65 * np.clip((Y - top) / 0.28, 0, 1))
    if pale:
        g = np.where(d > 0.84, 0.55, np.clip(g * 0.4 + 0.6, 0, 1))
    else:
        pupil = aa((0.3 - d) * r)
        g = g * (1 - pupil) + 0.03 * pupil
    g = np.clip(g, 0, 1)
    rgb = np.repeat((g * 255)[..., None], 3, -1).astype(np.float32)
    return rgb, disc * inside


def cell_top(shape):
    """The heavy upper lash line, its outer flick and lashes, the lid crease and the brow's shadow, the
    highlight."""
    s = A.EYE_SHAPES[shape]
    X, Y = grid()
    cx = A.EYE_XS[0]
    rgb = np.zeros(X.shape + (3,), np.float32)
    a = np.zeros(X.shape, np.float32)
    inside, top, bottom, u = opening(X, Y, cx, s)
    up = lid_line(cx, s, "top")
    lash = s["lash"]
    centre = [(x, y - lash * 0.22) for x, y in up]
    # the brow's shadow on the lid, then the crease above it
    crease = [(x, y - lash * 0.95 - 0.12) for x, y in lid_line(cx, s, "top", u0=0.22, u1=0.96)]
    span = (u > 0.0) & (u < 1.0)
    gap = (top - lash * 0.2) - Y
    hood_cov = np.where(span & (gap > 0) & (gap < s["hood"]), 0.28 * (1 - gap / s["hood"]) + 0.08, 0)
    rgb, a = over(rgb, a, hood_cov, HOOD)
    rgb, a = over(rgb, a, seg_cov(X, Y, crease, 0.04, 0.09) * 0.6, CREASE)
    # the lash line: thin at the inner corner, heavy at the outer one
    rgb, a = over(rgb, a, seg_cov(X, Y, centre, lash * 0.35, lash), LASH)
    # the flick: out and up from the outer corner, to a sharp point
    ox, oy = centre[-1]
    wing = s["wing"]
    tipx, tipy = ox - wing, oy - 0.08 - wing * 0.22
    rgb, a = over(rgb, a, seg_cov(X, Y, [(ox + 0.12, oy + 0.02), ((ox + tipx) / 2, (oy + tipy) / 2 + 0.02), (tipx, tipy)],
                                  lash * 0.9, 0.02), LASH)
    # a few lashes off the outer third
    for k, t in enumerate((0.62, 0.74, 0.86)):
        bx, by = centre[int(t * (len(centre) - 1))]
        ln = 0.13 + 0.06 * k
        rgb, a = over(rgb, a, seg_cov(X, Y, [(bx, by - lash * 0.3), (bx - ln * 0.85, by - lash * 0.3 - ln * 0.55)], 0.09, 0.01),
                      LASH)
    # a thin warm rim under the lash line where it meets the white (gives the lid thickness)
    rim = np.clip(aa(Y - (top + 0.02)) - aa(Y - (top + 0.08)), 0, 1) * inside
    rgb, a = over(rgb, a, rim * 0.55, LID)
    # the highlight: a sharp four-sided glint up-left in the iris, and a tiny one lower right
    ix, iy, r = iris_geo(cx, s)
    gx, gy = ix - r * 0.38, iy - r * 0.2
    glint = aa(0.13 - (np.abs(X - gx) * 0.9 + np.abs(Y - gy) * 1.3))
    gx2, gy2 = ix + r * 0.42, iy + r * 0.38
    glint2 = aa(0.05 - (np.abs(X - gx2) + np.abs(Y - gy2)))
    rgb, a = over(rgb, a, np.maximum(glint, glint2 * 0.85) * inside * (Y > top + 0.06), (255, 255, 255))
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

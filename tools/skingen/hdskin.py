"""HD painting toolkit for NOIRM's skin parts (numpy + Pillow).

The skins keep Minecraft's standard 64x64 player layout, drawn SCALE times finer (SCALE = 4:
256x256 images). Minecraft maps textures by normalised UVs, so the same model reads them as is.
Change SCALE here (and nothing else) to redraw every part at another resolution.

Coordinates: every painter works on one face of one box, in *model pixels* (floats): a head's front
is X 0..8, Y 0..8, sampled at the centre of each HD pixel. So the art is resolution-free; SCALE only
decides how many samples each model pixel gets.

Two kinds of part, both saved as RGBA PNGs:
  - tint parts (Part(tint=True)): a shading value k per pixel, saved as gray 128 + 40k. The game
    colours them with the look's colour (skin, hair, eyes, cloth): the rule is pixlib.tint and
    client/SkinComposer.tint in Java.
  - colour parts (Part(tint=False)): real colours (eye whites, lashes, marks, sandals...).

All art is original, drawn by the code that uses this module.
"""
import os

import numpy as np
from PIL import Image

from pixlib import SKIN_PARTS, FACE_LIGHT, faces as face_rects, hx  # noqa: F401  (re-exported)

SCALE = 4
SIZE = 64 * SCALE
P = SKIN_PARTS


# ---------------------------------------------------------------------------
# A part image
# ---------------------------------------------------------------------------

class Part:
    def __init__(self, tint=True):
        self.tint = tint
        self.k = np.zeros((SIZE, SIZE), np.float32)
        self.rgb = np.zeros((SIZE, SIZE, 3), np.float32)
        self.a = np.zeros((SIZE, SIZE), np.float32)

    def over(self, sl, value, alpha):
        """Alpha-blends value (k array, or rgb array (h, w, 3)) with coverage alpha over a region."""
        alpha = np.clip(np.broadcast_to(np.asarray(alpha, np.float32), self.a[sl].shape), 0, 1)
        a0 = self.a[sl]
        out = alpha + a0 * (1 - alpha)
        safe = np.where(out > 1e-6, out, 1)
        if self.tint:
            v = np.broadcast_to(np.asarray(value, np.float32), a0.shape)
            self.k[sl] = (v * alpha + self.k[sl] * a0 * (1 - alpha)) / safe
        else:
            v = np.broadcast_to(np.asarray(value, np.float32), a0.shape + (3,))
            self.rgb[sl] = (v * alpha[..., None] + self.rgb[sl] * (a0 * (1 - alpha))[..., None]) / safe[..., None]
        self.a[sl] = out

    def erase(self, sl, amount):
        """Cuts coverage away (amount 0..1)."""
        self.a[sl] = self.a[sl] * (1 - np.clip(amount, 0, 1))

    def pixels(self):
        a = np.clip(np.round(self.a * 255), 0, 255).astype(np.uint8)
        if self.tint:
            g = np.clip(np.round(128 + 40 * self.k), 0, 255).astype(np.uint8)
            rgb = np.stack([g, g, g], -1)
        else:
            rgb = np.clip(np.round(self.rgb), 0, 255).astype(np.uint8)
        rgb[a == 0] = 0
        return np.concatenate([rgb, a[..., None]], -1)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        Image.fromarray(self.pixels(), "RGBA").save(path, optimize=True)


# ---------------------------------------------------------------------------
# One face of a box
# ---------------------------------------------------------------------------

class F:
    """A face being painted: X, Y (model pixels, pixel centres), its size W x H, its name and box.

    MX: on side faces, the distance from the back edge (0) to the front edge (W), the same on both
    sides (the 'left' face is the mirror of the 'right' one)."""

    def __init__(self, box_name, face, rect):
        x, y, w, h = rect
        self.box, self.name, self.W, self.H = box_name, face, w, h
        xs = (np.arange(w * SCALE, dtype=np.float32) + 0.5) / SCALE
        ys = (np.arange(h * SCALE, dtype=np.float32) + 0.5) / SCALE
        self.X, self.Y = np.meshgrid(xs, ys)
        self.sl = (slice(y * SCALE, (y + h) * SCALE), slice(x * SCALE, (x + w) * SCALE))
        self.MX = (w - self.X) if face == "left" else self.X
        self.side = face in ("right", "left")

    def zeros(self):
        return np.zeros_like(self.X)

    def full(self, v):
        return np.full_like(self.X, v)


def paint(part, box_name, fn, light=True, only=None):
    """fn(F) -> (value, alpha) or None. value: k (tint part) or rgb (colour part); alpha 0..1."""
    box = P[box_name]
    for face, rect in face_rects(box).items():
        if only and face not in only:
            continue
        f = F(box_name, face, rect)
        res = fn(f)
        if res is None:
            continue
        value, alpha = res
        if light:
            if part.tint:
                value = value + FACE_LIGHT[face] * 0.5
            else:
                value = tone(value, FACE_LIGHT[face] * 0.5)
        part.over(f.sl, value, alpha)


# ---------------------------------------------------------------------------
# Colour and shape helpers (all vectorised)
# ---------------------------------------------------------------------------

def tone(c, k):
    """Array version of pixlib.tone: k > 0 lightens, k < 0 darkens."""
    c = np.asarray(c, np.float32)
    k = np.asarray(k, np.float32)
    if c.ndim == 1:
        c = np.broadcast_to(c, np.shape(k) + (3,))
    kk = k[..., None] if np.ndim(k) else k
    light = np.minimum(255, c * (1 + 0.13 * kk) + 14 * kk)
    dark = np.maximum(0, c * (1 + 0.2 * kk))
    return np.where(kk >= 0, light, dark)


def rgb_of(c, shape):
    return np.broadcast_to(np.asarray(c, np.float32), shape + (3,))


def sstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def ell(f, cx, cy, rx, ry, X=None, Y=None):
    """Antialiased filled ellipse coverage."""
    X = f.X if X is None else X
    Y = f.Y if Y is None else Y
    d = np.sqrt(((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2)
    return np.clip((1 - d) * min(rx, ry) * SCALE + 0.5, 0, 1)


def ell_d(f, cx, cy, rx, ry, X=None, Y=None):
    """Normalised distance from an ellipse centre (1 on the rim)."""
    X = f.X if X is None else X
    Y = f.Y if Y is None else Y
    return np.sqrt(((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2)


def gauss(f, cx, cy, sx, sy, X=None, Y=None):
    X = f.X if X is None else X
    Y = f.Y if Y is None else Y
    return np.exp(-0.5 * (((X - cx) / sx) ** 2 + ((Y - cy) / sy) ** 2))


def box_cov(f, x0, y0, x1, y1, X=None, Y=None):
    X = f.X if X is None else X
    Y = f.Y if Y is None else Y
    cx = np.clip((X - x0) * SCALE + 0.5, 0, 1) * np.clip((x1 - X) * SCALE + 0.5, 0, 1)
    cy = np.clip((Y - y0) * SCALE + 0.5, 0, 1) * np.clip((y1 - Y) * SCALE + 0.5, 0, 1)
    return cx * cy


def bezier(p0, p1, p2, n=24):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2 = (np.asarray(p, np.float32) for p in (p0, p1, p2))
    return (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2


def stroke(f, pts, w0, w1=None, X=None, Y=None):
    """Coverage of a polyline with width w0 at its start and w1 at its end (a tapered stroke)."""
    X = f.X if X is None else X
    Y = f.Y if Y is None else Y
    w1 = w0 if w1 is None else w1
    pts = np.asarray(pts, np.float32)
    n = len(pts) - 1
    seg_len = np.sqrt(((pts[1:] - pts[:-1]) ** 2).sum(1))
    total = max(seg_len.sum(), 1e-6)
    start = np.concatenate([[0], np.cumsum(seg_len)])[:-1]
    best = np.zeros_like(X)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[i + 1]
        dx, dy = bx - ax, by - ay
        ll = dx * dx + dy * dy
        t = np.clip(((X - ax) * dx + (Y - ay) * dy) / max(ll, 1e-9), 0, 1)
        d = np.sqrt((X - ax - t * dx) ** 2 + (Y - ay - t * dy) ** 2)
        along = (start[i] + t * seg_len[i]) / total
        w = w0 + (w1 - w0) * along
        cov = np.clip((w / 2 - d) * SCALE + 0.5, 0, 1)
        best = np.maximum(best, cov)
    return best


def hash01(ix, iy, seed):
    ix = np.asarray(ix).astype(np.int64)
    iy = np.asarray(iy).astype(np.int64)
    n = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, seed):
    """Smooth value noise, about one bump per unit."""
    x0 = np.floor(x)
    y0 = np.floor(y)
    tx = x - x0
    ty = y - y0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    a = hash01(x0, y0, seed)
    b = hash01(x0 + 1, y0, seed)
    c = hash01(x0, y0 + 1, seed)
    d = hash01(x0 + 1, y0 + 1, seed)
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def fbm(x, y, seed, octaves=3):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total = total + amp * vnoise(x * (2 ** o), y * (2 ** o), seed + o * 31)
        norm += amp
        amp *= 0.5
    return total / norm


def grain(f, seed):
    """Per-HD-pixel noise 0..1 (fine texture)."""
    return hash01(np.floor(f.X * SCALE) + 1000 * FACE_INDEX[f.name], np.floor(f.Y * SCALE), seed)


FACE_INDEX = {"top": 1, "bottom": 2, "right": 3, "front": 4, "left": 5, "back": 6}


def tri(x):
    """Triangle wave 0..1..0 with period 1 (0 at integers)."""
    q = x - np.floor(x)
    return 1 - np.abs(q - 0.5) * 2


def sample_grid(grid, X, Y, W, H):
    """Bilinear sample of a small grid (rows x cols) spread over a W x H face (cell centres)."""
    g = np.asarray(grid, np.float32)
    rows, cols = g.shape
    gx = np.clip(X / W * cols - 0.5, 0, cols - 1)
    gy = np.clip(Y / H * rows - 0.5, 0, rows - 1)
    x0 = np.floor(gx).astype(int)
    y0 = np.floor(gy).astype(int)
    x1 = np.minimum(x0 + 1, cols - 1)
    y1 = np.minimum(y0 + 1, rows - 1)
    tx = gx - x0
    ty = gy - y0
    return ((g[y0, x0] * (1 - tx) + g[y0, x1] * tx) * (1 - ty)
            + (g[y1, x0] * (1 - tx) + g[y1, x1] * tx) * ty)

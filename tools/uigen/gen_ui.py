#!/usr/bin/env python3
"""The NOIRM UI kit's textures (docs/UI.md, "The ninja look"): one 256x256 atlas per look, drawn here in
Minecraft-style pixels. client/ui/Ui and TabStrip draw panels, cards, buttons, header rails, seals and brush
strokes from it with nine-slicing, so every NOIRM screen changes at once.

    textures/gui/ui/<look>.png

Atlas layout (keep in step with client/ui/Skin.java):
    (0,0)     64x64   panel        nine-slice, border 7, the middle tiles
    (64,0)    32x32   card         border 3
    (96,0)    32x32   card, hover  border 3
    (128,0)   32x32   well         border 2 (sunken: slots, bar tracks, the 3D view)
    (160,0)   32x32   chip         border 2
    (192,0)   64x16   header rail  border 6 left/right, 3 top/bottom
    (192,16)  64x16   title swash  border 8 left/right (behind a screen's title)
    (0,64)    64x16   button       border 4 / 3
    (0,80)    64x16   button, hover
    (0,96)    64x16   button, primary
    (0,112)   64x16   button, disabled
    (64,64)   16x16   seal stamp   (white-ish, tinted with the seal's colour)
    (64,80)   64x6    brush stroke (white, tinted: the active tab, a selected row's mark)
    (64,88)   64x4    brush rule   (white, tinted: section rules, the tab rule)
    (64,96)   64x8    bar frame    border 3 (around HUD and screen bars)
    (0,128)   128x12  roller       border 10 left/right (the scroll look's wooden rods)
    (128,64)  16x16   corner       a corner ornament (top-left; the others are mirrored in code)

Three looks were mocked for the owner (docs/screenshots/cu-ui-directions.jpg): "shoji" (lacquered wood and
paper, hanko seals: chosen), "scroll" (tea-stained paper, ink-brush edges, wooden rollers) and "steel"
(forehead-protector plates, indigo cloth). All art is original and drawn by this code (numpy + Pillow).

Run from the repo root:  python3 tools/uigen/gen_ui.py [look ...]
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/gui/ui")
S = 256


def hx(c):
    c = c.lstrip("#")
    return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


def rng(seed):
    return np.random.RandomState(seed)


def periodic_noise(w, h, cells, seed):
    """Smooth value noise that tiles across w x h (cells per side)."""
    r = rng(seed).rand(cells, cells)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    fx, fy = xs / w * cells, ys / h * cells
    x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = r[y0 % cells, x0 % cells]
    b = r[y0 % cells, (x0 + 1) % cells]
    c = r[(y0 + 1) % cells, x0 % cells]
    d = r[(y0 + 1) % cells, (x0 + 1) % cells]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def grain(w, h, seed):
    return rng(seed).rand(h, w).astype(np.float32)


class Atlas:
    def __init__(self):
        self.img = np.zeros((S, S, 4), np.float32)

    def put(self, x, y, rgb, a=None):
        h, w = rgb.shape[:2]
        self.img[y:y + h, x:x + w, :3] = rgb
        self.img[y:y + h, x:x + w, 3] = 255 if a is None else a

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        out = np.clip(np.round(self.img), 0, 255).astype(np.uint8)
        out[out[..., 3] == 0, :3] = 0
        Image.fromarray(out, "RGBA").save(path, optimize=True)


def fill(w, h, c):
    return np.broadcast_to(hx(c), (h, w, 3)).copy()


def shade(rgb, k):
    """k > 0 lighter, k < 0 darker (per pixel, broadcast)."""
    k = np.asarray(k, np.float32)
    if k.ndim == 2:
        k = k[..., None]
    return np.where(k >= 0, rgb + (255 - rgb) * k, rgb * (1 + k))


def frame_mask(w, h, t):
    """True on a border t wide."""
    ys, xs = np.mgrid[0:h, 0:w]
    return (xs < t) | (ys < t) | (xs >= w - t) | (ys >= h - t)


def ring(w, h, d):
    """True on the ring at distance d from the edge."""
    ys, xs = np.mgrid[0:h, 0:w]
    m = np.minimum(np.minimum(xs, ys), np.minimum(w - 1 - xs, h - 1 - ys))
    return m == d


def edge_dist(w, h):
    ys, xs = np.mgrid[0:h, 0:w]
    return np.minimum(np.minimum(xs, ys), np.minimum(w - 1 - xs, h - 1 - ys))


def wood(w, h, base, seed, vertical=False, rings=0.35):
    """Lacquered wood: long grain streaks, a few darker knots of grain."""
    g = periodic_noise(w if not vertical else h, 4, 8, seed)  # streak profile
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    along, across = (ys, xs) if vertical else (xs, ys)
    n = periodic_noise(w, h, 6, seed + 1)
    streak = np.sin(across * 1.9 + n * 6.0 + np.sin(along * 0.07 + seed) * 2.0)
    k = 0.10 * streak * rings + (n - 0.5) * 0.16 + (grain(w, h, seed + 2) - 0.5) * 0.06
    return shade(fill(w, h, base), k)


def paper(w, h, base, seed, fibre=0.07, mottle=0.12):
    n = periodic_noise(w, h, 4, seed)
    f = grain(w, h, seed + 1)
    fib = (f > 0.93) * 0.5 + (f < 0.04) * -0.5
    return shade(fill(w, h, base), (n - 0.5) * mottle + fib * fibre)


def stamp_noise(w, h, seed):
    n = periodic_noise(w, h, 5, seed) * 0.6 + grain(w, h, seed + 3) * 0.4
    return n


def brush_stroke(w, h, seed, taper=0.25, dry=0.18):
    """A white brush stroke across w x h: tapered ends, a ragged edge, dry-brush gaps. Returns alpha 0..255."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    t = xs / max(1, w - 1)
    # thickness along the stroke: a press at the start, a long body, a flick at the end
    thick = np.clip(np.minimum(t / taper, 1) * np.minimum((1 - t) / (taper * 0.7), 1), 0, 1) ** 0.6
    thick = thick * (0.85 + 0.15 * np.sin(t * 9 + seed))
    centre = (h - 1) / 2 + np.sin(t * 3.1 + seed) * 0.3
    d = np.abs(ys - centre) / (h / 2)
    edge = periodic_noise(w, h, 8, seed) * 0.3
    cov = (d < thick - edge * 0.5).astype(np.float32)
    gaps = (periodic_noise(w, 4, 16, seed + 5)[0:1, :].repeat(h, 0) > 1 - dry) & (rng(seed).rand(h, w) > 0.35)
    cov = cov * (1 - gaps * 0.85)
    return cov * 255


# ---------------------------------------------------------------------------
# The looks
# ---------------------------------------------------------------------------

def common_stamps(a, seed, base="#f2ece4"):
    """The seal stamp (hanko), tinted in game: a rough-edged square, its ink worn in patches."""
    w = h = 16
    n = stamp_noise(w, h, seed)
    d = edge_dist(w, h)
    cov = ((d >= 0) & ~((d == 0) & (n < 0.45))).astype(np.float32)
    # a worn inner border ring, like a carved stamp's frame
    rgb = shade(fill(w, h, base), (n - 0.5) * 0.25)
    rgb = np.where(((d == 1) & (n > 0.3))[..., None], shade(rgb, -0.25), rgb)
    holes = (grain(w, h, seed + 9) > 0.9) & (d >= 2)
    cov = cov * (1 - holes * 0.55)
    a.put(64, 64, rgb, cov * 255)
    # the brush stroke (tabs) and the brush rule (sections)
    a.put(64, 80, fill(64, 6, "#ffffff"), brush_stroke(64, 6, seed + 1))
    a.put(64, 88, fill(64, 4, "#ffffff"), brush_stroke(64, 4, seed + 2, taper=0.15, dry=0.1) * 0.9)


def corner(a, rgb, cov):
    a.put(128, 64, rgb, cov)


def look_shoji():
    """Lacquered wood and paper: a black-lacquer frame with brass corner caps around dark paper with a faint
    shoji lattice; header rails of lighter wood; red hanko seals; wooden tag buttons, red lacquer for primary."""
    a = Atlas()
    # panel
    w = h = 64
    inner = paper(w, h, "#17141a", 11, fibre=0.06, mottle=0.08)
    ys, xs = np.mgrid[0:h, 0:w]
    lattice = ((xs - 7) % 26 == 0) | ((ys - 7) % 26 == 0)
    inner = np.where(lattice[..., None], shade(inner, 0.07), inner)
    fr = wood(w, h, "#2b1b14", 12)
    fr_v = wood(w, h, "#2b1b14", 13, vertical=True)
    side = (xs < 7) | (xs >= w - 7)
    fr = np.where(side[..., None], fr_v, fr)
    d = edge_dist(w, h)
    rgb = np.where((d < 7)[..., None], fr, inner)
    rgb = np.where((d == 0)[..., None], hx("#0a0807"), rgb)                       # outer ink line
    rgb = np.where((d == 1)[..., None] & ((xs < 32) & (ys < 32))[..., None] | ((d == 1) & (ys == 1))[..., None],
                   shade(fr, 0.18), rgb)                                          # lit top edge
    rgb = np.where((d == 5)[..., None], hx("#120c0a"), rgb)                       # the frame's inner groove
    rgb = np.where((d == 6)[..., None], hx("#5e4126"), rgb)                       # a thin gilt line round the paper
    # brass corner caps
    cap = ((xs < 6) | (xs >= w - 6)) & ((ys < 6) | (ys >= h - 6)) & (d < 6)
    brass = shade(fill(w, h, "#8a6c3e"), (grain(w, h, 14) - 0.5) * 0.2)
    rgb = np.where(cap[..., None], brass, rgb)
    rgb = np.where((cap & ((xs % 63 == 0) | (ys % 63 == 0) | (d == 0)))[..., None], hx("#2a1c10"), rgb)
    rgb = np.where((cap & (((xs == 3) | (xs == w - 4)) & ((ys == 3) | (ys == h - 4))))[..., None], hx("#e8d29a"), rgb)
    a.put(0, 0, rgb)
    # cards
    for x, base, seed in ((64, "#211b20", 21), (96, "#2c2428", 22)):
        c = paper(32, 32, base, seed, mottle=0.06)
        dd = edge_dist(32, 32)
        c = np.where((dd == 0)[..., None], hx("#0d0a0b"), c)
        c = np.where(((dd == 1) & (np.mgrid[0:32, 0:32][0] == 1))[..., None], shade(c, 0.12), c)
        a.put(x, 0, c)
    wl = paper(32, 32, "#0c0a0d", 23, mottle=0.05)
    dd = edge_dist(32, 32)
    wl = np.where((dd == 0)[..., None], hx("#060506"), wl)
    wl = np.where(((dd == 1) & (np.mgrid[0:32, 0:32][0] == 1))[..., None], hx("#040304"), wl)
    a.put(128, 0, wl)
    ch = paper(32, 32, "#2a2226", 24, mottle=0.05)
    ch = np.where((edge_dist(32, 32) == 0)[..., None], hx("#120e10"), ch)
    a.put(160, 0, ch)
    # header rail: a lighter wood plank with a dark groove under it
    rail = wood(64, 16, "#3b281c", 25)
    dd = edge_dist(64, 16)
    ry = np.mgrid[0:16, 0:64][0]
    rail = np.where((ry == 0)[..., None], shade(rail, 0.2), rail)
    rail = np.where((ry >= 14)[..., None], hx("#0e0a08"), rail)
    rail = np.where(((ry == 13))[..., None], shade(rail, -0.3), rail)
    a.put(192, 0, rail)
    # title swash: a red-ink brush swash
    sw = brush_stroke(64, 16, 31, taper=0.18, dry=0.22)
    a.put(192, 16, shade(fill(64, 16, "#6e1612"), (grain(64, 16, 32) - 0.5) * 0.3), sw * 0.9)
    # buttons: wooden tags (normal, hover, primary red lacquer, disabled)
    for i, (base, line, seed) in enumerate((("#3a291d", "#120c09", 41), ("#4c3626", "#120c09", 42),
                                           ("#7a1c18", "#2a0808", 43), ("#241c18", "#100c0a", 44))):
        b = wood(64, 16, base, seed)
        dd = edge_dist(64, 16)
        by = np.mgrid[0:16, 0:64][0]
        b = np.where(((by == 1) & (dd >= 1))[..., None], shade(b, 0.22 if i != 3 else 0.06), b)
        b = np.where(((by == 14) & (dd >= 1))[..., None], shade(b, -0.35), b)
        b = np.where((dd == 0)[..., None], hx(line), b)
        if i == 2:
            b = np.where(((dd == 1) & (by != 1) & (by != 14))[..., None], hx("#b08a48"), b)  # a gilt edge
        a.put(0, 64 + i * 16, b)
    common_stamps(a, 51)
    # bar frame: a dark lacquer channel
    bf = fill(64, 8, "#0b0909")
    dd = edge_dist(64, 8)
    bf = np.where((dd == 0)[..., None], hx("#3b2a1e"), bf)
    a.put(64, 96, bf)
    # corner ornament: a brass bracket with a rivet
    cr = fill(16, 16, "#8a6c3e")
    cy, cx = np.mgrid[0:16, 0:16]
    cov = ((cx < 4) | (cy < 4)) & (cx + cy < 16)
    cr = np.where(((cx == 1) & (cy == 1))[..., None], hx("#f0dca0"), cr)
    cr = np.where(((cx == 0) | (cy == 0))[..., None], hx("#3a2814"), cr)
    corner(a, cr, cov * 255)
    # roller (unused by this look, drawn anyway so the layout is complete)
    a.put(0, 128, wood(128, 12, "#2b1b14", 61))
    return a


def look_scroll():
    """A hanging scroll: tea-stained dark paper with an ink-brush edge, rolled on wooden rods with gilt caps;
    titles on a red brush swash; buttons as paper slips; seals as red stamps."""
    a = Atlas()
    w = h = 64
    rgb = paper(w, h, "#2a2118", 111, fibre=0.1, mottle=0.16)
    d = edge_dist(w, h)
    n = periodic_noise(w, h, 10, 112)
    ink = (d < 3 + (n > 0.55) * 1 + (n > 0.75) * 1)
    rgb = np.where((d < 7)[..., None], shade(rgb, 0.05), rgb)                    # a lighter mount around the paper
    rgb = np.where(((d == 6))[..., None], hx("#7a6448"), rgb)                    # the paper's edge
    rgb = np.where(ink[..., None], shade(fill(w, h, "#0d0a08"), (grain(w, h, 113) - 0.5) * 0.4), rgb)
    alpha = np.where((d == 0) & (n < 0.35), 0, 255)                              # a ragged outer edge
    a.put(0, 0, rgb, alpha)
    for x, base, seed in ((64, "#33281d", 121), (96, "#3f3224", 122)):
        c = paper(32, 32, base, seed, mottle=0.1)
        dd = edge_dist(32, 32)
        c = np.where((dd == 0)[..., None], hx("#16110c"), c)
        a.put(x, 0, c)
    wl = paper(32, 32, "#15100b", 123, mottle=0.06)
    wl = np.where((edge_dist(32, 32) == 0)[..., None], hx("#0a0705"), wl)
    a.put(128, 0, wl)
    ch = paper(32, 32, "#3a2e22", 124, mottle=0.06)
    a.put(160, 0, ch)
    # header rail: an ink wash band
    rail = shade(fill(64, 16, "#1c140e"), (periodic_noise(64, 16, 8, 125) - 0.5) * 0.3)
    ry = np.mgrid[0:16, 0:64][0]
    ra = np.where(ry >= 14, 120, 210)
    a.put(192, 0, rail, ra)
    sw = brush_stroke(64, 16, 131, taper=0.2, dry=0.3)
    a.put(192, 16, shade(fill(64, 16, "#7a1a14"), (grain(64, 16, 132) - 0.5) * 0.3), sw)
    for i, (base, seed) in enumerate((("#3b2f22", 141), ("#4d3d2c", 142), ("#6e1814", 143), ("#251e17", 144))):
        b = paper(64, 16, base, seed, mottle=0.12)
        dd = edge_dist(64, 16)
        bn = periodic_noise(64, 16, 12, seed)
        by, bx = np.mgrid[0:16, 0:64]
        ragged = ((bx < 2) | (bx > 61)) & (bn < 0.4)
        b = np.where(((by == 15) | (bx == 0) | (bx == 63))[..., None], hx("#0e0a07"), b)
        a.put(0, 64 + i * 16, b, np.where(ragged, 0, 255))
    common_stamps(a, 151)
    bf = fill(64, 8, "#120d09")
    bf = np.where((edge_dist(64, 8) == 0)[..., None], hx("#5a4630"), bf)
    a.put(64, 96, bf)
    cr = fill(16, 16, "#a8884a")
    cy, cx = np.mgrid[0:16, 0:16]
    corner(a, cr, ((cx + cy) < 5) * 255)
    # the roller: a dark wood rod, lit along its top, gilt caps at both ends
    r = wood(128, 12, "#3a2418", 161)
    ry, rx = np.mgrid[0:12, 0:128]
    prof = np.sin(np.clip((ry + 0.5) / 12, 0, 1) * math.pi)
    r = shade(r, (prof - 0.75) * 0.6)
    cap = (rx < 10) | (rx >= 118)
    gilt = shade(fill(128, 12, "#b08a48"), (prof - 0.7) * 0.7)
    r = np.where(cap[..., None], gilt, r)
    r = np.where(((rx == 0) | (rx == 127) | (ry == 0) | (ry == 11))[..., None], hx("#140c08"), r)
    r = np.where(((rx == 9) | (rx == 118))[..., None], hx("#3a2410"), r)
    a.put(0, 128, r)
    return a


def look_steel():
    """Forehead-protector steel: brushed metal frames with rivets, headers on engraved plates, indigo cloth
    behind; buttons as small plates, primary in red cloth."""
    a = Atlas()
    w = h = 64
    ys, xs = np.mgrid[0:h, 0:w]
    cloth = fill(w, h, "#13151d")
    weave = ((xs + ys) % 2 == 0) * 0.03 + ((xs % 4 == 0) | (ys % 4 == 0)) * -0.04
    cloth = shade(cloth, weave + (periodic_noise(w, h, 4, 211) - 0.5) * 0.08)
    d = edge_dist(w, h)
    brushed = shade(fill(w, h, "#5a5e66"), (periodic_noise(w, 2, 32, 212)[0:1].repeat(h, 0) - 0.5) * 0.3
                    + (grain(w, h, 213) - 0.5) * 0.1)
    brushed_v = shade(fill(w, h, "#5a5e66"), (periodic_noise(2, h, 32, 214)[:, 0:1].repeat(w, 1) - 0.5) * 0.3
                      + (grain(w, h, 215) - 0.5) * 0.1)
    side = (xs < 7) | (xs >= w - 7)
    metal = np.where(side[..., None], brushed_v, brushed)
    rgb = np.where((d < 6)[..., None], metal, cloth)
    rgb = np.where(((d == 1) & ((ys < 32) | (xs < 32)))[..., None], shade(metal, 0.35), rgb)
    rgb = np.where((d == 4)[..., None], shade(metal, -0.35), rgb)
    rgb = np.where((d == 0)[..., None], hx("#0a0b0e"), rgb)
    rgb = np.where((d == 5)[..., None], hx("#08090c"), rgb)
    for (rx, ry) in ((3, 3), (60, 3), (3, 60), (60, 60), (31, 2), (31, 61), (2, 31), (61, 31)):
        rgb[ry - 1:ry + 1, rx - 1:rx + 1] = hx("#2a2c31")
        rgb[ry - 1, rx - 1] = hx("#d4d8de")
    a.put(0, 0, rgb)
    for x, base, seed in ((64, "#1c1f28", 221), (96, "#252935", 222)):
        c = shade(fill(32, 32, base), (periodic_noise(32, 32, 4, seed) - 0.5) * 0.08)
        dd = edge_dist(32, 32)
        c = np.where((dd == 0)[..., None], hx("#3a3e46"), c)
        a.put(x, 0, c)
    wl = fill(32, 32, "#0a0b10")
    wl = np.where((edge_dist(32, 32) == 0)[..., None], hx("#2a2d33"), wl)
    a.put(128, 0, wl)
    a.put(160, 0, np.where((edge_dist(32, 32) == 0)[..., None], hx("#4a4e56"), fill(32, 32, "#22252e")))
    # header plate: a forehead protector: brushed plate, engraved inner line, two rivets each end
    p = shade(fill(64, 16, "#6a6e76"), (periodic_noise(64, 2, 32, 225)[0:1].repeat(16, 0) - 0.5) * 0.35)
    dd = edge_dist(64, 16)
    py, px = np.mgrid[0:16, 0:64]
    p = np.where((dd == 0)[..., None], hx("#0c0d10"), p)
    p = np.where(((dd == 1) & (py == 1))[..., None], hx("#c8ccd2"), p)
    p = np.where((dd == 3)[..., None], shade(p, -0.4), p)
    for rx in (4, 59):
        for ry in (4, 11):
            p[ry, rx] = hx("#e4e8ee")
            p[ry + 1, rx] = hx("#24262a")
    a.put(192, 0, p)
    sw = brush_stroke(64, 16, 231, taper=0.2, dry=0.2)
    a.put(192, 16, fill(64, 16, "#6e1612"), sw * 0.8)
    for i, (base, seed) in enumerate((("#2e323b", 241), ("#3c414c", 242), ("#7a1a18", 243), ("#20232a", 244))):
        b = shade(fill(64, 16, base), (periodic_noise(64, 2, 32, seed)[0:1].repeat(16, 0) - 0.5) * 0.2)
        dd = edge_dist(64, 16)
        by = np.mgrid[0:16, 0:64][0]
        b = np.where(((by == 1) & (dd >= 1))[..., None], shade(b, 0.3), b)
        b = np.where((dd == 0)[..., None], hx("#0a0b0e"), b)
        if i == 2:
            b = np.where(((dd == 1) & (by != 1))[..., None], hx("#8a8e96"), b)
        a.put(0, 64 + i * 16, b)
    common_stamps(a, 251)
    bf = fill(64, 8, "#08090c")
    bf = np.where((edge_dist(64, 8) == 0)[..., None], hx("#4a4e56"), bf)
    a.put(64, 96, bf)
    cr = fill(16, 16, "#8a8e96")
    cy, cx = np.mgrid[0:16, 0:16]
    cr = np.where(((cx == 2) & (cy == 2))[..., None], hx("#e4e8ee"), cr)
    corner(a, cr, (((cx < 5) & (cy < 5)) | ((cx + cy) < 3)) * 255)
    a.put(0, 128, fill(128, 12, "#5a5e66"))
    return a


LOOKS = {"shoji": look_shoji, "scroll": look_scroll, "steel": look_steel}


def main():
    names = sys.argv[1:] or list(LOOKS)
    for name in names:
        LOOKS[name]().save(os.path.join(OUT, name + ".png"))
    print("wrote %d UI atlases to %s" % (len(names), os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()

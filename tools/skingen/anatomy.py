"""The shared HD body and face for NOIRM's skins: where things are on the face and how skin is
shaded, used by gen_parts.py (NPC ninjas) and gen_player_parts.py (the player).

How the skin is lit (docs/ART_STYLE.md, "raw and tough"):

1. Form. Each box is treated as a rounded column (a superellipse cross-section), so the light flows
   around the corners: one lit side, one shadow side, no dark stripe at every cube edge.
2. Anatomy as relief. Muscles are bumps in a height field (pads); the same light falls on them,
   so every muscle gets a lit top-left and a shadowed bottom-right, the way an artist carves them.
3. Ink. The light is cut into a few flat tones (highlight, light, half, shadow), with slightly
   ragged edges, and deliberate dark grooves are inked where muscles meet (centre line, ab grid,
   under the chest, the V at the hips, the knees). Grit only lives in the shadows.

Positions are in model pixels on each face (the head's front is 8 x 8). The eye centres match the
Mangekyou overlay in client/SharinganEyesLayer (EYE_X 1.5 px either side of the middle, 4.5 px down).
"""
import numpy as np

from hdskin import (ink, grit, F, ell, ell_d, gauss, sstep, stroke, bezier, grain, fbm, vnoise, box_cov,  # noqa: F401
                    SCALE, P, hash01)

# ---------------------------------------------------------------------------
# Face geometry (head front, 8 x 8)
# ---------------------------------------------------------------------------

EYE_Y = 4.5
EYE_XS = (2.5, 5.5)       # texture-left eye, texture-right eye
NOSE_TIP_Y = 5.75
MOUTH_Y = 6.6

# Eye shapes: half width, lid heights above/below the centre, tilt (outer corner up), iris radius,
# lid line thickness, the flick at the outer corner, how far the iris rides up under the lid.
EYE_SHAPES = {
    "round":  dict(hw=1.0, up=0.62, down=0.5, tilt=0.06, iris=0.6, lash=0.36, wing=0.3, lift=0.0),
    "sharp":  dict(hw=1.08, up=0.46, down=0.34, tilt=0.2, iris=0.56, lash=0.4, wing=0.46, lift=0.08),
    "wide":   dict(hw=1.0, up=0.72, down=0.58, tilt=0.0, iris=0.6, lash=0.32, wing=0.22, lift=0.0),
    "narrow": dict(hw=1.04, up=0.34, down=0.26, tilt=0.1, iris=0.54, lash=0.4, wing=0.36, lift=0.1),
    "pale":   dict(hw=1.0, up=0.6, down=0.5, tilt=0.04, iris=0.6, lash=0.34, wing=0.26, lift=0.0),
}


def outward(x, cx):
    """+1 towards this eye's outer corner (away from the nose)."""
    return np.where(cx < 4, -(x - cx), (x - cx))


def lid_curves(x, cx, s):
    """(top lid y, bottom lid y) at x for the eye centred at cx: an almond, the upper lid arched."""
    dx = x - cx
    o = outward(x, cx)
    t = np.clip(np.abs(dx) / s["hw"], 0, 1)
    arch = np.sqrt(np.clip(1 - t ** 2, 0, 1))
    # the upper lid peaks a little towards the inner side, the lower lid sits flatter
    skew = 1 + 0.18 * np.clip(-o / s["hw"], -1, 1)
    top = EYE_Y - s["up"] * arch ** 0.7 * skew - s["tilt"] * o
    bottom = EYE_Y + s["down"] * arch ** 1.3 - s["tilt"] * o * 0.5
    return top, bottom


def eye_lids(f, cx, s):
    """(top lid y, bottom lid y, inside 0..1 across x) for one eye at centre cx."""
    top, bottom = lid_curves(f.X, cx, s)
    across = np.clip((s["hw"] - np.abs(f.X - cx)) * SCALE + 0.5, 0, 1)
    return top, bottom, across


def eye_inside(f, cx, s):
    top, bottom, across = eye_lids(f, cx, s)
    inside = np.clip((f.Y - top) * SCALE + 0.5, 0, 1) * np.clip((bottom - f.Y) * SCALE + 0.5, 0, 1) * across
    return crisp(inside)


def iris_centre(cx, s):
    # pushed slightly towards the nose's side? no: centred, riding up under the upper lid
    return cx, EYE_Y - 0.02 - s.get("lift", 0.0)


def iris_radii(s):
    """The iris is a tall oval: it fills the eye's height (the lids clip it), little white shows."""
    return s["iris"] * 0.95, s["iris"] * 1.15


def crisp(cov, at=0.5):
    """Pixel-art coverage: in or out, no blur (deliberate clusters instead of soft edges)."""
    return (np.asarray(cov) >= at).astype(np.float32)


# ---------------------------------------------------------------------------
# Delta layers: shading drawn over an already-tinted skin
# ---------------------------------------------------------------------------

def delta(dk, dark=-1.3, light=1.1):
    """Turns a shading change dk into (k, alpha): dark where dk < 0, light where dk > 0."""
    dk = np.asarray(dk, np.float32)
    k = np.where(dk < 0, dark, light)
    a = np.where(dk < 0, np.clip(dk / dark, 0, 1), np.clip(dk / light, 0, 1))
    return k, a


def round_edges(f, strength=0.14, start=0.55):
    """Darkens towards the left and right edges of a face (used by cloth, not by skin)."""
    c = np.abs(f.X - f.W / 2) / (f.W / 2)
    return -strength * sstep(start, 1.0, c)


def texture(f, seed, amount=0.05):
    return (grain(f, seed) - 0.5) * amount + (fbm(f.X * 1.3, f.Y * 1.3, seed + 7) - 0.5) * amount * 1.5


# ---------------------------------------------------------------------------
# Light and form
# ---------------------------------------------------------------------------

def _unit(*v):
    v = np.asarray(v, np.float32)
    return v / np.linalg.norm(v)


# x: towards the character's left, y: up, z: forwards. The key light comes from the front, above
# and the character's right (the viewer's left); a second light from behind and above on the other
# side, so the back (seen as often as the front in third person) is carved just as well.
KEY = _unit(-0.35, 0.55, 0.76)
RIM = _unit(0.4, 0.55, -0.72)

# The tones skin is cut into (shading steps k; 0 = the chosen skin colour).
TONE_HI, TONE_LIGHT, TONE_HALF, TONE_SHADOW, TONE_DEEP = 0.45, 0.0, -0.75, -1.75, -3.3

RELIEF = {"head": 1.7, "body": 1.9, "arm": 1.3, "leg": 1.2}   # how strongly the relief bends the light

ROUNDNESS = {"head": 5.0, "body": 3.0, "arm": 2.4, "leg": 2.5}


def kind(box):
    return {"head": "head", "body": "body", "rarm": "arm", "larm": "arm", "rleg": "leg", "lleg": "leg"}.get(box, box)


def section(f):
    """Where each pixel of a side face sits on the box's cross-section: (x, z, half width, half depth).
    x grows towards the character's left, z towards the front."""
    _, _, w, _, d = P[f.box]
    a, b = w / 2.0, d / 2.0
    X = f.X
    one = np.ones_like(X)
    if f.name == "front":
        return X - a, b * one, a, b
    if f.name == "back":
        return a - X, -b * one, a, b
    if f.name == "right":
        return -a * one, X - b, a, b
    if f.name == "left":
        return a * one, b - X, a, b
    return None


def tangent_x(f):
    """The world direction of the face's +X axis."""
    return {"front": (1, 0, 0), "back": (-1, 0, 0), "right": (0, 0, 1), "left": (0, 0, -1)}[f.name]


def form_normal(f, p):
    """The rounded-column normal (nx, ny, nz) of a side face."""
    cx, cz, a, b = section(f)
    gx = np.sign(cx) * np.abs(cx / a) ** (p - 1) / a
    gz = np.sign(cz) * np.abs(cz / b) ** (p - 1) / b
    n = np.sqrt(gx * gx + gz * gz) + 1e-6
    return gx / n, np.zeros_like(gx), gz / n


def lit(f, h=None, tilt=None, p=3.0, rim=0.97, relief=1.7):
    """Light (about -1..1) on a side face with an optional height field h (model px) bent over it."""
    nx, ny, nz = form_normal(f, p)
    if tilt is not None:
        ny = ny + tilt
    if h is not None:
        gy, gx = np.gradient(h * relief, 1.0 / SCALE)
        tx = tangent_x(f)
        nx = nx - gx * tx[0]
        nz = nz - gx * tx[2]
        ny = ny + gy
    n = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6
    nx, ny, nz = nx / n, ny / n, nz / n
    key = nx * KEY[0] + ny * KEY[1] + nz * KEY[2]
    back = nx * RIM[0] + ny * RIM[1] + nz * RIM[2]
    return np.maximum(key, rim * back - 0.04)


def cut(f, light, seed, levels=(0.93, 0.62, 0.25), rough=0.04):
    """Cuts light into the flat skin tones, with slightly ragged (dry-brush) edges."""
    j = rough * (fbm(f.X * 2.0, f.Y * 2.0, seed) - 0.5) * 2.0
    v = light + j
    hi, li, half = levels
    k = np.full_like(v, TONE_SHADOW)
    k = np.where(v > half, TONE_HALF, k)
    k = np.where(v > li, TONE_LIGHT, k)
    k = np.where(v > hi, TONE_HI, k)
    return k


def pad(f, cx, cy, rx, ry, height, X=None):
    """A muscle: a rounded plateau in the height field."""
    d = ell_d(f, cx, cy, rx, ry, X=X)
    return height * sstep(1.0, 0.2, d)


def groove(f, pts, w0, w1=None, X=None):
    """A carved groove along a polyline, tapered from w0 to w1: 1 = shadow, 2 = its deep ink core
    (only where the groove is wide enough to carry one)."""
    w1 = w0 if w1 is None else w1
    out = crisp(stroke(f, pts, w0, w1, X=X))
    if max(w0, w1) >= 0.3:
        core = crisp(stroke(f, pts, max(w0 - 0.24, 0.0), max(w1 - 0.24, 0.0), X=X))
        out = out + core
    return out


def shadow_grit(f, k, seed, amount=0.6):
    """Raw texture only in the shadows: sparse dark specks and short hatch strokes, none in the light."""
    g = grain(f, seed)
    in_shadow = k <= TONE_SHADOW + 0.01
    in_half = (k > TONE_SHADOW + 0.01) & (k <= TONE_HALF + 0.01)
    speck = (g < 0.05 * amount) & in_shadow
    # hatching: short diagonal strokes on a coarse grid, breaking up the half-tone near the shadow
    u = (f.X + f.Y) * SCALE / 3.0
    line = (np.abs(u - np.round(u)) < 0.18)
    seg = vnoise(f.X * 3.0, f.Y * 3.0, seed + 5) > 0.72
    hatch = line & seg & in_half
    out = k.copy()
    out = np.where(hatch & (amount > 1), TONE_SHADOW, out)
    out = np.where(speck & (amount > 0.5), TONE_DEEP, out)
    return out


def lateral(f):
    """On front/back faces of a limb: 0 at the outer side, W at the inner side (towards the body's middle)."""
    right_limb = f.box in ("rarm", "rleg")
    if f.name == "front":
        return f.X if right_limb else f.W - f.X
    if f.name == "back":
        return f.W - f.X if right_limb else f.X
    return f.X


def is_outer(f):
    """Side faces: whether this is the limb's outer face."""
    return (f.box in ("rarm", "rleg")) == (f.name == "right")


# ---------------------------------------------------------------------------
# The torso
# ---------------------------------------------------------------------------

def torso(f, s):
    """(height field, ink grooves, vertical tilt) for the torso at muscle level s (0 = soft)."""
    X, Y = f.X, f.Y
    h = np.zeros_like(X)
    g = np.zeros_like(X)
    tilt = 0.5 * sstep(1.3, 0.0, Y)                                  # shoulders face up
    if f.name == "front":
        for sx in (-1, 1):
            h += pad(f, 4 + 1.95 * sx, 2.45, 1.95, 1.4, 0.55 * s + 0.15)       # pecs
            h += pad(f, 4 + 3.45 * sx, 6.4, 0.75, 2.3, 0.3 * s)                 # obliques
            for i in range(3):
                h += pad(f, 4 + 3.45 * sx, 4.3 + 0.62 * i, 0.4, 0.26, 0.18 * s)  # serratus
            for row, cy in enumerate((5.0, 6.3, 7.6)):
                h += pad(f, 4 + 0.92 * sx, cy, 0.8, 0.58, (0.32 - 0.04 * row) * s)  # the six-pack
        h += pad(f, 4, 9.6, 1.6, 1.0, 0.18 * s)                                  # lower belly
        if s > 0:
            w = 0.22 + 0.12 * s
            for sx in (-1, 1):
                # under the chest: thick at the outer end, a hard cast shadow
                g = np.maximum(g, groove(f, bezier((4 + 0.15 * sx, 3.75), (4 + 1.9 * sx, 4.35), (4 + 3.55 * sx, 3.15), 20),
                                         w * 1.1, w * 2.6))
                # the V at the hips
                g = np.maximum(g, groove(f, bezier((4 + 3.25 * sx, 8.3), (4 + 2.6 * sx, 10.3), (4 + 1.15 * sx, 11.9), 16),
                                         w * 1.4, w * 0.5))
                # serratus teeth
                for i in range(3):
                    y = 4.55 + 0.62 * i
                    g = np.maximum(g, groove(f, [(4 + 3.95 * sx, y - 0.1), (4 + 3.15 * sx, y + 0.18)], w * 0.9, 0.04))
                # collarbones (a short line under the bone)
                g = np.maximum(g, groove(f, bezier((4 + 0.7 * sx, 1.05), (4 + 1.9 * sx, 1.2), (4 + 3.1 * sx, 0.85), 12),
                                         w * 0.5, 0.04))
            g = np.maximum(g, groove(f, [(4, 1.6), (4, 3.6)], w * 0.8))                     # sternum
            g = np.maximum(g, groove(f, [(4, 4.2), (4, 8.6)], w * 1.0, w * 0.7))           # centre line
            for y in (5.65, 6.95, 8.2):                                                    # the ab grid
                g = np.maximum(g, groove(f, bezier((3.0, y + 0.1), (4, y - 0.04), (5.0, y + 0.1), 10), w * 0.85, w * 0.85))
            g = np.maximum(g, crisp(ell(f, 4, 8.8, 0.16, 0.22)))                            # navel
    elif f.name == "back":
        m = 0.3 + 0.7 * s
        h += -0.25 * np.exp(-((X - 4) / 0.3) ** 2) * sstep(0.5, 2.0, Y)                  # the spine's valley
        for sx in (-1, 1):
            h += dome(f, 4 + 1.5 * sx, 0.9, 1.7, 1.3, 0.55 * m)                           # traps
            h += dome(f, 4 + 2.15 * sx, 3.2, 1.5, 1.5, 0.75 * m)                          # shoulder blades
            h += dome(f, 4 + 2.8 * sx, 6.4, 1.2, 2.5, 0.65 * m)                           # lats
            h += dome(f, 4 + 0.85 * sx, 9.6, 0.65, 2.3, 0.6 * m)                          # erectors
        if s > 0:
            w = 0.22 + 0.12 * s
            g = np.maximum(g, groove(f, [(4, 1.2), (4, 11.6)], w * 0.9, w * 1.2))
            for sx in (-1, 1):
                g = np.maximum(g, groove(f, bezier((4 + 0.8 * sx, 2.0), (4 + 0.9 * sx, 3.9), (4 + 2.8 * sx, 4.6), 14),
                                         w * 0.6, w * 1.6))                               # under the blades
                g = np.maximum(g, groove(f, bezier((4 + 3.85 * sx, 4.3), (4 + 2.7 * sx, 7.4), (4 + 1.25 * sx, 9.0), 16),
                                         w * 1.6, w * 0.4))                               # the lats' edge
                g = np.maximum(g, groove(f, [(4 + 1.3 * sx, 10.4), (4 + 1.3 * sx, 10.6)], w * 1.2))  # dimples
    elif f.side:
        mx = f.MX
        h += pad(f, 2.0, 5.6, 1.4, 2.6, 0.25 * s, X=mx)
        if s > 0:
            w = 0.18 + 0.1 * s
            for i in range(3):
                g = np.maximum(g, groove(f, [(0.8, 4.1 + 0.6 * i), (3.3, 4.45 + 0.6 * i)], w, 0.03, X=mx))
    return h, g, tilt


# ---------------------------------------------------------------------------
# Arms (the model bends at row 6) and legs (knee at row 6)
# ---------------------------------------------------------------------------

def dome(f, cx, cy, rx, ry, height, X=None):
    """A rounded muscle belly: brightest at the top-light side, a crescent of shadow underneath."""
    d = ell_d(f, cx, cy, rx, ry, X=X)
    return height * np.clip(1 - d * d, 0, 1) ** 0.75


def arm(f, s):
    X, Y = f.X, f.Y
    h = np.zeros_like(X)
    g = np.zeros_like(X)
    tilt = 0.8 * sstep(1.0, 0.0, Y)                                   # the top of the shoulder
    lx = lateral(f) if not f.side else f.MX
    w = 0.2 + 0.12 * s
    m = 0.3 + 0.7 * s
    # every face: the deltoid cap
    h += dome(f, 2.0, 1.0, 2.6, 2.1, 0.75 * m, X=lx)
    if f.name == "front":
        h += dome(f, 2.35, 4.1, 1.35, 1.6, 0.7 * m, X=lx)             # biceps
        h += dome(f, 1.45, 7.2, 1.45, 1.9, 0.55 * m, X=lx)            # forearm
        h += dome(f, 2.0, 9.3, 1.8, 0.8, -0.2, X=lx)                  # the wrist narrows
        if s > 0:
            g = np.maximum(g, groove(f, bezier((3.9, 2.0), (1.5, 2.55), (0.2, 3.45), 12), w * 0.7, w * 1.1, X=lx))
            g = np.maximum(g, groove(f, bezier((1.7, 6.0), (2.6, 6.3), (3.6, 5.85), 10), w * 1.1, w * 0.5, X=lx))
    elif f.name == "back":
        h += dome(f, 1.4, 3.7, 1.0, 1.7, 0.6 * m, X=lx) + dome(f, 2.8, 4.0, 0.95, 1.4, 0.55 * m, X=lx)   # triceps
        h += dome(f, 2.0, 6.1, 0.6, 0.5, 0.35)                                                        # elbow point
        h += dome(f, 2.0, 7.6, 1.5, 1.9, 0.4 * m, X=lx)
        if s > 0:
            g = np.maximum(g, groove(f, bezier((0.1, 2.3), (2.0, 2.85), (3.9, 2.3), 12), w * 0.9, w * 0.9, X=lx))
            g = np.maximum(g, groove(f, [(2.05, 3.1), (2.05, 5.0)], w * 0.7, 0.05, X=lx))
    else:
        h += dome(f, 2.2, 4.1, 1.3, 1.6, 0.5 * m, X=lx)
        h += dome(f, 1.7, 7.4, 1.4, 1.8, 0.4 * m, X=lx)
        if s > 0 and is_outer(f):
            g = np.maximum(g, groove(f, bezier((0.2, 2.4), (2.0, 3.6), (3.9, 2.4), 12), w * 0.8, w * 0.8, X=lx))
    # the hand (rows 10-12): knuckles and fingers on the front, tendons on the back, thumb on the inside
    if f.name == "front":
        for x in (0.5, 1.5, 2.5, 3.5):
            h += dome(f, x, 10.45, 0.5, 0.35, 0.25)
        for x in (1.0, 2.0, 3.0):
            g = np.maximum(g, groove(f, [(x, 10.95), (x, 12.0)], 0.2))
        g = np.maximum(g, groove(f, [(0.0, 9.95), (4.0, 9.95)], 0.14))
    elif f.name == "back":
        for x in (1.0, 2.0, 3.0):
            g = np.maximum(g, groove(f, [(x, 11.3), (x, 12.0)], 0.18))
    elif not is_outer(f):
        g = np.maximum(g, groove(f, bezier((0.6, 10.4), (2.0, 11.5), (3.4, 10.9), 10), 0.2))
    return h, g, tilt


def leg(f, s):
    X, Y = f.X, f.Y
    h = np.zeros_like(X)
    g = np.zeros_like(X)
    lx = lateral(f) if not f.side else f.MX
    w = 0.2 + 0.12 * s
    m = 0.3 + 0.7 * s
    tilt = None
    if f.name == "front":
        h += dome(f, 1.15, 3.0, 1.35, 2.8, 0.6 * m, X=lx)            # outer quad
        h += dome(f, 2.55, 2.4, 1.05, 2.3, 0.55 * m, X=lx)           # rectus
        h += dome(f, 3.15, 4.9, 0.9, 1.0, 0.6 * m, X=lx)             # the teardrop above the knee
        h += dome(f, 2.0, 6.35, 0.85, 0.7, 0.45)                     # kneecap
        h += dome(f, 1.4, 8.3, 1.0, 2.0, 0.4 * m, X=lx)              # shin muscle (outer)
        if s > 0:
            g = np.maximum(g, groove(f, bezier((2.0, 0.8), (1.85, 3.4), (2.45, 4.8), 12), w * 0.6, w * 0.9, X=lx))
            g = np.maximum(g, groove(f, bezier((1.2, 7.05), (2.0, 7.35), (2.8, 7.05), 10), w * 1.0, w * 0.7, X=lx))
    elif f.name == "back":
        h += dome(f, 2.0, 2.6, 1.8, 2.6, 0.45 * m, X=lx)             # hamstrings
        h += dome(f, 1.3, 7.3, 0.95, 1.5, 0.7 * m, X=lx) + dome(f, 2.75, 7.6, 0.9, 1.4, 0.7 * m, X=lx)  # calf
        if s > 0:
            g = np.maximum(g, groove(f, bezier((0.9, 5.7), (2.0, 6.05), (3.1, 5.7), 10), w * 0.5, w * 0.5, X=lx))
            g = np.maximum(g, groove(f, [(2.05, 7.6), (2.0, 9.0)], w * 0.6, 0.05, X=lx))
    else:
        h += dome(f, 2.0, 2.9, 1.7, 2.6, 0.45 * m, X=lx)
        h += dome(f, 1.35, 7.5, 1.1, 1.6, 0.45 * m, X=lx)
        h += dome(f, 1.9, 10.3, 0.4, 0.35, 0.2, X=lx)                # ankle bone
    # the foot (rows 10.2-12): toes on the front
    if f.name == "front":
        for x in (0.95, 1.8, 2.6, 3.3):
            g = np.maximum(g, groove(f, [(x, 11.05), (x, 12.0)], 0.18))
        g = np.maximum(g, groove(f, bezier((0.0, 10.6), (2.0, 10.45), (4.0, 10.6), 10), 0.14))
    return h, g, tilt


def shade_body(f, s, seed):
    """The inked skin of the torso, arms or legs at muscle level s (0 = soft, ~1 = defined)."""
    kd = kind(f.box)
    if f.name == "top":
        return np.full_like(f.X, TONE_LIGHT if kd != "body" else TONE_HALF)
    if f.name == "bottom":
        k = np.full_like(f.X, TONE_SHADOW)
        if kd == "arm":   # the fingertips
            for x in (1.0, 2.0, 3.0):
                k = np.where(groove(f, [(x, 0.3), (x, 3.7)], 0.1) > 0, TONE_DEEP, k)
        return k
    fn = {"body": torso, "arm": arm, "leg": leg}[kd]
    h, g, tilt = fn(f, s)
    light = lit(f, h, tilt, ROUNDNESS[kd], relief=RELIEF[kd])
    k = cut(f, light, seed)
    # occlusion: where the arms meet the torso, the torso's bottom in the hips' shadow
    if kd == "body" and f.side:
        k = np.where((f.Y < 3.2) & (k > TONE_HALF), TONE_HALF, k)
    return ink_grooves(k, g)


def ink_grooves(k, g):
    """Grooves: the shadow tone, with a deep ink core in the wide ones."""
    k = np.where(g >= 1, np.minimum(k, TONE_SHADOW), k)
    return np.where(g >= 2, TONE_DEEP, k)


# ---------------------------------------------------------------------------
# The head
# ---------------------------------------------------------------------------

def face_relief(f, nose="none", mouth="none", jaw="none"):
    """The head front's relief: brow ridge over deep eye sockets, forehead, chin."""
    X, Y = f.X, f.Y
    h = np.zeros_like(X)
    g = np.zeros_like(X)
    for cx in EYE_XS:
        h += pad(f, cx, 3.25, 1.5, 0.55, 0.35)
        h += -0.35 * sstep(1.0, 0.3, ell_d(f, cx, 4.45, 1.45, 0.75))
    h += pad(f, 4, 1.6, 3.6, 1.6, 0.2)                                             # forehead
    h += pad(f, 4, 7.45, 1.0, 0.45, 0.2)                                          # chin
    return h, g


def _wedge(f, x_top, y_top, x_bot, y_bot, w_top, w_bot):
    """A crisp tapered shape between two points (a shadow plane)."""
    return crisp(stroke(f, [(x_top, y_top), (x_bot, y_bot)], w_top, w_bot))


def nose_fill(f, style, k):
    """The nose as flat ink shapes: a shadow plane on the side away from the light, a cast shadow under
    it, nostrils, a lit tip. Light comes from the texture's left."""
    if style == "none":
        return k
    shade = np.zeros_like(f.X)
    under = np.zeros_like(f.X)
    deep = np.zeros_like(f.X)
    lit_ = np.zeros_like(f.X)
    if style == "straight":
        shade = _wedge(f, 4.12, 4.55, 4.3, 5.55, 0.18, 0.42)
        under = crisp(ell(f, 4.1, 5.98, 0.5, 0.13))
        deep = crisp(ell(f, 3.78, 5.92, 0.13, 0.08)) + crisp(ell(f, 4.36, 5.92, 0.15, 0.09))
        lit_ = crisp(ell(f, 3.9, 5.5, 0.17, 0.17))
    elif style == "button":
        shade = crisp(ell(f, 4.32, 5.55, 0.22, 0.3))
        under = crisp(ell(f, 4.08, 5.92, 0.42, 0.11))
        deep = crisp(ell(f, 3.8, 5.86, 0.11, 0.07)) + crisp(ell(f, 4.3, 5.86, 0.12, 0.07))
        lit_ = crisp(ell(f, 3.92, 5.48, 0.2, 0.18))
    elif style == "broad":
        shade = _wedge(f, 4.15, 4.7, 4.45, 5.6, 0.2, 0.5)
        under = crisp(ell(f, 4.05, 6.0, 0.75, 0.14))
        deep = crisp(ell(f, 3.6, 5.92, 0.18, 0.1)) + crisp(ell(f, 4.45, 5.92, 0.2, 0.1))
        deep += crisp(stroke(f, [(4.75, 5.45), (4.8, 5.85)], 0.12))                      # the nostril wing
        lit_ = crisp(ell(f, 3.88, 5.48, 0.22, 0.17))
    elif style == "hooked":
        shade = _wedge(f, 4.12, 4.4, 4.32, 5.75, 0.18, 0.44)
        under = crisp(ell(f, 4.1, 6.12, 0.5, 0.13))
        deep = crisp(stroke(f, [(3.7, 6.05), (4.5, 6.02)], 0.14))
        lit_ = crisp(ell(f, 3.9, 4.95, 0.13, 0.22)) + crisp(ell(f, 3.92, 5.7, 0.16, 0.14))  # the bump and the tip
    elif style == "flat":
        shade = crisp(ell(f, 4.35, 5.45, 0.2, 0.25))
        under = crisp(ell(f, 4.05, 5.88, 0.6, 0.11))
        deep = crisp(ell(f, 3.62, 5.82, 0.15, 0.08)) + crisp(ell(f, 4.42, 5.82, 0.15, 0.08))
    k = np.where(shade > 0, np.minimum(k, TONE_HALF), k)
    k = np.where(under > 0, np.minimum(k, TONE_SHADOW), k)
    k = np.where(lit_ > 0, TONE_HI, k)
    k = np.where(deep > 0, TONE_DEEP, k)
    return k


def mouth_fill(f, style, k):
    """The mouth: a bold line (darkest in the middle), the lower lip's shadow, corners."""
    if style == "none":
        return k
    y = MOUTH_Y
    line = np.zeros_like(f.X)
    soft = np.zeros_like(f.X)
    if style == "neutral":
        line = crisp(stroke(f, bezier((3.2, y), (4, y + 0.06), (4.8, y), 12), 0.16, 0.16))
        line += crisp(stroke(f, [(3.55, y + 0.02), (4.45, y + 0.02)], 0.28))
    elif style == "smile":
        line = crisp(stroke(f, bezier((3.0, y - 0.28), (4, y + 0.32), (5.0, y - 0.28), 14), 0.2, 0.2))
        for sx in (-1, 1):
            soft += crisp(stroke(f, [(4 + 1.22 * sx, y - 0.5), (4 + 1.12 * sx, y - 0.15)], 0.14, 0.08))
    elif style == "frown":
        line = crisp(stroke(f, bezier((3.15, y + 0.22), (4, y - 0.16), (4.85, y + 0.22), 12), 0.2, 0.2))
        for sx in (-1, 1):
            soft += crisp(stroke(f, [(4 + 0.92 * sx, y + 0.3), (4 + 1.02 * sx, y + 0.7)], 0.14, 0.06))
    elif style == "wide":
        line = crisp(stroke(f, bezier((2.7, y - 0.08), (4, y + 0.14), (5.3, y - 0.08), 14), 0.2, 0.2))
        line += crisp(stroke(f, [(3.4, y + 0.06), (4.6, y + 0.06)], 0.28))
    elif style == "thin":
        line = crisp(stroke(f, [(3.45, y), (4.55, y)], 0.16))
    # the lower lip: a short shadow under its middle
    lip = crisp(ell(f, 4.05, y + 0.48, 0.42, 0.1)) if style != "thin" else np.zeros_like(f.X)
    k = np.where(soft > 0, np.minimum(k, TONE_SHADOW), k)
    k = np.where(lip > 0, np.minimum(k, TONE_HALF), k)
    return np.where(line > 0, TONE_DEEP, k)


def jaw_fill(f, name, k):
    """The jaw's shape on the face: the lower corners turn away into shadow (flat tone shapes)."""
    if name == "none":
        return k
    X, Y = f.X, f.Y
    ax = np.abs(X - 4)
    if name == "soft":
        away = (Y - 6.4) * 1.1 > (3.3 - ax) * 1.3
    elif name == "square":
        away = ((Y > 7.45) & (ax > 1.5)) | ((ax > 3.55) & (Y > 6.2))
    elif name == "sharp":
        away = (Y - 5.7) > (3.75 - ax) * 0.95
    else:  # round
        away = ell_d(f, 4, 4.4, 4.15, 3.45) > 1.0
    # the shadow side of the face (the character's left = texture right) goes darker
    tone_away = np.where(X > 4, TONE_SHADOW, TONE_HALF)
    k = np.where(away, np.minimum(k, tone_away), k)
    return k


JAW_SIDE = {"none": 0.0, "soft": 0.4, "square": 0.6, "sharp": 0.75, "round": 0.3}


def head_tones(f, nose="none", mouth="none", jaw="none", seed=5):
    """The inked head. The front is lit flat-ish (an anime face), with carved shadow shapes."""
    X, Y = f.X, f.Y
    if f.name == "top":
        return np.full_like(X, TONE_LIGHT)
    if f.name == "bottom":
        return np.full_like(X, TONE_SHADOW)
    if f.name == "front":
        h, g = face_relief(f, nose, mouth, jaw)
        light = lit(f, h, None, ROUNDNESS["head"])
        k = cut(f, light, seed, levels=(9.0, 0.3, 0.02), rough=0.025)
        k = jaw_fill(f, jaw, k)
        k = nose_fill(f, nose, k)
        k = mouth_fill(f, mouth, k)
        # under the jaw: a hard shadow band
        k = np.where(Y > 7.7, np.minimum(k, TONE_HALF), k)
        k = np.where(g > 0, TONE_DEEP, k)
        return k
    # side and back faces: the head as a rounded block, an ear, the jaw line
    h = np.zeros_like(X)
    g = np.zeros_like(X)
    if f.side:
        mx = f.MX
        h += pad(f, 3.5, 4.8, 0.75, 1.1, 0.35, X=mx)                                  # the ear
        g = np.maximum(g, crisp(ell(f, 3.7, 5.0, 0.16, 0.34, X=mx)))                    # the ear's hollow
        js = JAW_SIDE.get(jaw, 0.0)
        jaw_y = 6.3 + (mx - 3.6) * (0.45 if jaw == "sharp" else 0.32)
        h += -js * sstep(jaw_y - 0.1, jaw_y + 0.3, Y) * sstep(4.6, 5.4, mx)
        h += 0.25 * sstep(1.0, 0.3, ell_d(f, 6.6, 5.3, 1.1, 0.8, X=mx))              # cheekbone
    light = lit(f, h, None, ROUNDNESS["head"])
    k = cut(f, light, seed, levels=(0.9, 0.3, 0.02), rough=0.03)
    if f.side:
        k = np.where((Y > 7.5) & (f.MX > 4.8), np.minimum(k, TONE_HALF), k)        # under the jaw
    k = np.where(g > 0, TONE_DEEP, k)
    return k


# ---------------------------------------------------------------------------
# Entry points used by the part generators
# ---------------------------------------------------------------------------

NPC_MUSCLE = 0.85


def head_skin(f, nose=True, mouth=True):
    return head_tones(f, "straight" if nose else "none", "neutral" if mouth else "none", "soft")


def body_skin(f, s=NPC_MUSCLE):
    return shade_body(f, s, 13)


def arm_skin(f, s=NPC_MUSCLE):
    return shade_body(f, s, 25)


def leg_skin(f, s=NPC_MUSCLE):
    return shade_body(f, s, 39)

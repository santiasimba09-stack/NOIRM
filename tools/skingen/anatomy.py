"""The shared HD body and face for NOIRM's skins: one place for where things are on the face and
how skin is shaded, used by gen_parts.py (NPC ninjas) and gen_player_parts.py (the player).

Style: a slightly stylised anime ninja. Soft, broad shading (a light from the front and above),
a few clear anatomy lines (collarbones, chest, abs, knees), big readable eyes.

Positions are in model pixels on each face (the head's front is 8 x 8). The eye centres match the
Mangekyou overlay in client/SharinganEyesLayer (EYE_X 1.5 px either side of the middle, 4.5 px down).
"""
import numpy as np

from hdskin import (ink, grit, F, ell, ell_d, gauss, sstep, stroke, bezier, grain, fbm, vnoise, box_cov, SCALE)  # noqa: F401

# ---------------------------------------------------------------------------
# Face geometry (head front, 8 x 8)
# ---------------------------------------------------------------------------

EYE_Y = 4.5
EYE_XS = (2.5, 5.5)       # texture-left eye, texture-right eye
NOSE_TIP_Y = 5.75
MOUTH_Y = 6.55

# Eye shapes: half width, lid heights above/below the centre, tilt (outer corner up), iris radius,
# lash thickness, extra flick at the outer corner.
EYE_SHAPES = {
    "round":  dict(hw=1.05, up=0.74, down=0.52, tilt=0.06, iris=0.64, lash=0.20, wing=0.10),
    "sharp":  dict(hw=1.12, up=0.50, down=0.34, tilt=0.24, iris=0.54, lash=0.24, wing=0.30),
    "wide":   dict(hw=1.10, up=0.86, down=0.66, tilt=0.00, iris=0.70, lash=0.18, wing=0.06),
    "narrow": dict(hw=1.08, up=0.30, down=0.22, tilt=0.12, iris=0.52, lash=0.22, wing=0.14),
    "pale":   dict(hw=1.05, up=0.72, down=0.54, tilt=0.04, iris=0.68, lash=0.18, wing=0.08),
}


def outward(x, cx):
    """+1 towards this eye's outer corner (away from the nose)."""
    return np.where(cx < 4, -(x - cx), (x - cx))


def eye_lids(f, cx, s):
    """(top lid y, bottom lid y, inside 0..1 across x) for one eye at centre cx."""
    dx = f.X - cx
    o = outward(f.X, cx)
    t = np.clip(np.abs(dx) / s["hw"], 0, 1)
    shape = np.sqrt(np.clip(1 - t ** 2, 0, 1))
    top = EYE_Y - s["up"] * shape ** 0.85 - s["tilt"] * o
    bottom = EYE_Y + s["down"] * shape ** 1.1 - s["tilt"] * o * 0.6
    # the inner corner sits a touch lower (a tear duct)
    inner = np.clip(-o / s["hw"], 0, 1)
    bottom = bottom + 0.05 * inner
    across = np.clip((s["hw"] - np.abs(dx)) * SCALE + 0.5, 0, 1)
    return top, bottom, across


def eye_inside(f, cx, s):
    top, bottom, across = eye_lids(f, cx, s)
    return np.clip((f.Y - top) * SCALE + 0.5, 0, 1) * np.clip((bottom - f.Y) * SCALE + 0.5, 0, 1) * across


def iris_centre(cx, s):
    return cx, EYE_Y - 0.04 - (0.08 if s["up"] < 0.4 else 0.0)


# ---------------------------------------------------------------------------
# Delta layers: shading drawn over an already-tinted skin
# ---------------------------------------------------------------------------

def delta(dk, dark=-1.3, light=1.1):
    """Turns a shading change dk into (k, alpha): dark where dk < 0, light where dk > 0."""
    dk = np.asarray(dk, np.float32)
    k = np.where(dk < 0, dark, light)
    a = np.where(dk < 0, np.clip(dk / dark, 0, 1), np.clip(dk / light, 0, 1))
    return k, a


# ---------------------------------------------------------------------------
# Skin shading per box (k fields, tinted by skin tone)
# ---------------------------------------------------------------------------

def texture(f, seed, amount=0.05):
    return (grain(f, seed) - 0.5) * amount + (fbm(f.X * 1.3, f.Y * 1.3, seed + 7) - 0.5) * amount * 1.5


def round_edges(f, strength=0.14, start=0.55):
    """Darkens towards the left and right edges of a face, so boxes read as rounded limbs."""
    c = np.abs(f.X - f.W / 2) / (f.W / 2)
    return -strength * sstep(start, 1.0, c)


def _head_soft(f, nose=True, mouth=True):
    """The head: face planes, eye sockets, cheekbones, ears; NPCs also get a nose and mouth."""
    X, Y = f.X, f.Y
    k = texture(f, 3)
    if f.name == "front":
        ax = np.abs(X - 4)
        k += -0.42 * sstep(2.6, 4.0, ax)                       # the face turns away at the sides
        k += 0.16 * gauss(f, 4, 1.7, 2.4, 1.1)                  # forehead
        for cx in EYE_XS:
            k += -0.3 * gauss(f, cx, 4.4, 1.15, 0.5)             # eye sockets: dark, intense eyes
            k += 0.10 * gauss(f, cx, 3.45, 1.0, 0.22)            # brow ridge
            side = -1 if cx < 4 else 1
            k += 0.16 * gauss(f, cx + 0.35 * side, 5.45, 0.75, 0.32)  # cheekbones
            k += -0.14 * gauss(f, 4 + 3.0 * side, 6.4, 0.55, 0.75)    # under the cheekbones
        k += -0.20 * gauss(f, 4, 4.7, 0.25, 0.5) * 0.0          # (bridge handled by the nose)
        k += -0.45 * sstep(7.15, 8.0, Y) * (0.6 + 0.4 * sstep(0.5, 3.5, ax))  # under the jaw
        k += -0.18 * gauss(f, 4, 7.25, 0.9, 0.22)               # under the lower lip
        k += 0.10 * gauss(f, 4, 7.55, 0.8, 0.3)                 # chin
        if nose:
            k += nose_shading(f, "straight")
        if mouth:
            k += mouth_shading(f, "neutral")
        return k
    if f.side:
        mx = f.MX
        k += -0.1 + 0.08 * sstep(4, 8, mx)                      # cheek side
        ear = ell_d(f, 3.55, 4.75, 0.66, 1.0)
        inner = ell_d(f, 3.72, 4.85, 0.36, 0.6)
        k += np.where(ear < 1, 0.12 - 0.7 * sstep(1.0, 0.6, inner) * (inner < 1), 0)       # rim and the hollow
        k += -0.5 * np.clip(1 - np.abs(ear - 1.0) * 6, 0, 1)                                  # its outline
        # the jaw line: from under the ear to the chin
        jaw_y = 6.1 + (mx - 3.6) * 0.38
        k += -0.30 * sstep(jaw_y - 0.1, jaw_y + 0.5, Y) * (mx > 3.0)
        k += -0.40 * sstep(7.2, 8, Y)
        return k
    if f.name == "back":
        return k - 0.28 - 0.25 * sstep(6.5, 8, Y)
    if f.name == "top":
        return k + 0.18
    return k - 0.9                                               # under the chin


def nose_shading(f, style):
    """A shading change (dk) for the nose on the head front."""
    X, Y = f.X, f.Y
    dk = np.zeros_like(X)
    if style == "straight":
        dk += 0.22 * stroke(f, [(3.88, 4.45), (3.86, 5.45)], 0.18, 0.28)        # bridge
        dk += -0.28 * stroke(f, [(4.32, 4.6), (4.38, 5.55)], 0.16, 0.34)        # shade side
        dk += 0.20 * ell(f, 3.95, 5.5, 0.22, 0.16)                              # tip
        dk += -0.45 * (ell(f, 3.68, 5.86, 0.13, 0.07) + ell(f, 4.32, 5.86, 0.13, 0.07))
        dk += -0.32 * gauss(f, 4, 5.98, 0.42, 0.08)                             # under the nose
    elif style == "button":
        dk += 0.22 * ell(f, 3.95, 5.5, 0.26, 0.2)
        dk += -0.2 * gauss(f, 4.3, 5.35, 0.18, 0.3)
        dk += -0.38 * (ell(f, 3.75, 5.8, 0.1, 0.06) + ell(f, 4.25, 5.8, 0.1, 0.06))
        dk += -0.22 * gauss(f, 4, 5.92, 0.32, 0.07)
    elif style == "broad":
        dk += 0.16 * stroke(f, [(3.9, 4.6), (3.88, 5.35)], 0.26, 0.4)
        dk += -0.22 * stroke(f, [(4.42, 4.7), (4.55, 5.5)], 0.2, 0.4)
        dk += 0.18 * ell(f, 3.95, 5.5, 0.36, 0.2)
        for sx in (-1, 1):
            dk += -0.30 * gauss(f, 4 + 0.68 * sx, 5.62, 0.16, 0.18)             # nostril wings
            dk += -0.42 * ell(f, 4 + 0.4 * sx, 5.86, 0.17, 0.08)
        dk += -0.3 * gauss(f, 4, 6.0, 0.6, 0.09)
    elif style == "hooked":
        dk += 0.32 * stroke(f, [(3.9, 4.4), (3.82, 4.95), (3.9, 5.5)], 0.2, 0.26)  # the hump
        dk += -0.34 * stroke(f, [(4.3, 4.5), (4.42, 5.6)], 0.18, 0.36)
        dk += 0.16 * ell(f, 3.98, 5.62, 0.2, 0.16)
        dk += -0.5 * gauss(f, 4.05, 5.92, 0.3, 0.1)                             # tip hangs down
        dk += -0.42 * (ell(f, 3.68, 5.95, 0.12, 0.06) + ell(f, 4.32, 5.95, 0.12, 0.06))
    elif style == "flat":
        dk += 0.1 * ell(f, 3.95, 5.45, 0.4, 0.22)
        dk += -0.16 * gauss(f, 4.4, 5.3, 0.22, 0.35)
        dk += -0.3 * (ell(f, 3.62, 5.8, 0.15, 0.07) + ell(f, 4.38, 5.8, 0.15, 0.07))
        dk += -0.18 * gauss(f, 4, 5.92, 0.55, 0.08)
    return dk


def mouth_shading(f, style):
    X, Y = f.X, f.Y
    dk = np.zeros_like(X)
    y = MOUTH_Y
    if style == "neutral":
        line = stroke(f, bezier((3.2, y), (4, y + 0.06), (4.8, y)), 0.14, 0.14)
        dk += -0.9 * line
        dk += -0.18 * gauss(f, 4, y - 0.2, 0.55, 0.1)            # upper lip
        dk += 0.18 * gauss(f, 4, y + 0.28, 0.45, 0.12)           # lower lip catches light
        dk += -0.22 * gauss(f, 4, 6.1, 0.12, 0.14)               # philtrum
    elif style == "smile":
        line = stroke(f, bezier((3.05, y - 0.22), (4, y + 0.28), (4.95, y - 0.22)), 0.12, 0.12)
        dk += -0.9 * line
        dk += 0.2 * gauss(f, 4, y + 0.42, 0.4, 0.1)
        for sx in (-1, 1):
            dk += -0.25 * gauss(f, 4 + 1.15 * sx, y - 0.28, 0.12, 0.16)   # dimples / cheek lift
            dk += 0.12 * gauss(f, 4 + 1.5 * sx, y - 0.7, 0.4, 0.25)
        dk += -0.2 * gauss(f, 4, 6.1, 0.12, 0.14)
    elif style == "frown":
        line = stroke(f, bezier((3.15, y + 0.2), (4, y - 0.12), (4.85, y + 0.2)), 0.13, 0.13)
        dk += -0.85 * line
        for sx in (-1, 1):
            dk += -0.3 * stroke(f, [(4 + 0.88 * sx, y + 0.22), (4 + 1.0 * sx, y + 0.55)], 0.1, 0.03)
        dk += 0.15 * gauss(f, 4, y + 0.3, 0.4, 0.1)
        dk += -0.2 * gauss(f, 4, 6.1, 0.12, 0.14)
    elif style == "wide":
        line = stroke(f, bezier((2.7, y - 0.05), (4, y + 0.12), (5.3, y - 0.05)), 0.15, 0.15)
        dk += -0.9 * line
        dk += -0.2 * gauss(f, 4, y - 0.22, 0.85, 0.1)
        dk += 0.2 * gauss(f, 4, y + 0.32, 0.75, 0.13)
        dk += -0.2 * gauss(f, 4, 6.1, 0.12, 0.14)
    elif style == "thin":
        line = stroke(f, [(3.4, y), (4.6, y)], 0.1, 0.1)
        dk += -0.6 * line
        dk += 0.1 * gauss(f, 4, y + 0.22, 0.4, 0.08)
    return dk


def _body_soft(f):
    """The torso: collarbones, chest, a light six-pack, navel, shoulder blades and spine."""
    X, Y = f.X, f.Y
    k = texture(f, 11)
    if f.name == "front":
        k += round_edges(f, 0.3, 0.55)
        k += -0.12 * sstep(8.5, 12, Y)
        for sx in (-1, 1):
            # collarbones: a highlight with a soft shadow under it
            k += 0.16 * stroke(f, bezier((4 + 0.35 * sx, 0.7), (4 + 1.6 * sx, 1.0), (4 + 3.4 * sx, 0.75)), 0.22, 0.14)
            k += -0.12 * stroke(f, bezier((4 + 0.6 * sx, 1.2), (4 + 1.8 * sx, 1.45), (4 + 3.2 * sx, 1.2)), 0.3, 0.2)
            # chest: lit on top, a crease under it
            k += 0.16 * gauss(f, 4 + 1.9 * sx, 2.4, 1.3, 0.8)
            k += -0.48 * stroke(f, bezier((4 + 0.3 * sx, 3.8), (4 + 2.0 * sx, 4.45), (4 + 3.6 * sx, 3.3)), 0.6, 0.2)
            # obliques and the V at the hips
            k += -0.22 * stroke(f, [(4 + 3.3 * sx, 6.0), (4 + 2.8 * sx, 9.0), (4 + 1.0 * sx, 11.6)], 0.4, 0.2)
        k += -0.08 * gauss(f, 4, 0.35, 0.45, 0.25)              # the notch at the neck
        k += -0.16 * stroke(f, [(4, 1.6), (4, 3.8)], 0.22)       # sternum
        k += -0.2 * stroke(f, [(4, 4.4), (4, 8.2)], 0.16)        # line down the abs
        for row, (y0, y1) in enumerate(((4.55, 5.65), (5.85, 6.95), (7.15, 8.2))):
            for sx in (-1, 1):
                k += 0.13 * ell(f, 4 + 0.95 * sx, (y0 + y1) / 2 - 0.08, 0.75, (y1 - y0) / 2 * 0.95)  # ab blocks, lit on top
            k += -0.14 * stroke(f, [(2.9, y1 + 0.1), (5.1, y1 + 0.1)], 0.16)
        for sx in (-1, 1):
            for i in range(3):  # serratus: short ribs of muscle under the arms
                y = 4.4 + 0.55 * i
                k += -0.16 * stroke(f, [(4 + 3.85 * sx, y), (4 + 3.2 * sx, y + 0.3)], 0.14, 0.04)
        k += -0.6 * ell(f, 4, 8.55, 0.17, 0.2) + 0.1 * gauss(f, 4, 8.8, 0.25, 0.1)  # navel
        return k
    if f.name == "back":
        k += round_edges(f, 0.2, 0.6) - 0.06
        k += -0.3 * gauss(f, 4, 6, 0.2, 4.5)                    # spine
        for sx in (-1, 1):
            k += 0.16 * gauss(f, 4 + 1.9 * sx, 2.7, 1.1, 1.1)   # shoulder blades
            k += -0.3 * stroke(f, bezier((4 + 0.8 * sx, 1.6), (4 + 0.9 * sx, 3.6), (4 + 2.6 * sx, 4.6)), 0.3, 0.1)
            k += -0.18 * stroke(f, bezier((4 + 3.6 * sx, 4.0), (4 + 2.6 * sx, 6.5), (4 + 1.0 * sx, 8.5)), 0.4, 0.1)  # lats
            k += -0.1 * gauss(f, 4 + 1.3 * sx, 10.4, 0.25, 0.25)  # lower back dimples
        return k
    if f.side:
        k += -0.08 + round_edges(f, 0.1)
        k += -0.1 * gauss(f, 2, 3.0, 1.0, 1.4)                  # armpit
        return k
    if f.name == "top":
        return k + 0.15
    return k - 0.6


def _arm_soft(f):
    """An arm: shoulder, biceps, elbow (the model bends at row 6), forearm and a hand."""
    X, Y = f.X, f.Y
    k = texture(f, 23)
    if f.name == "top":
        return k + 0.25
    if f.name == "bottom":
        # the end of the hand: palm and folded fingers
        k += -0.35
        for x in (1.0, 2.0, 3.0):
            k += -0.25 * stroke(f, [(x, 0.4), (x, 3.6)], 0.1)
        return k
    k += round_edges(f, 0.3, 0.45)
    k += 0.14 * gauss(f, 2, 0.9, 1.6, 1.0)                      # deltoid
    k += -0.26 * stroke(f, bezier((0.0, 2.3), (2, 3.0), (4.0, 2.3)), 0.2, 0.2)  # under the deltoid
    if f.side:
        k += -0.2 * stroke(f, [(2.0, 2.9), (2.1, 5.3)], 0.16, 0.06)   # between biceps and triceps
    if f.name == "front":
        k += -0.26 * stroke(f, [(1.55, 6.9), (2.0, 8.0), (2.45, 9.5)], 0.08, 0.06)  # a vein down the forearm
        k += -0.18 * stroke(f, [(2.0, 8.0), (2.9, 8.8)], 0.06, 0.03)
        k += 0.12 * gauss(f, 2, 3.7, 1.0, 1.2)                  # biceps
        k += -0.24 * stroke(f, bezier((1.0, 5.95), (2, 6.15), (3.0, 5.95)), 0.16, 0.16)  # inner elbow
    if f.name == "back":
        k += 0.08 * gauss(f, 2, 3.4, 1.1, 1.3)                  # triceps
        k += 0.16 * ell(f, 2, 6.05, 0.5, 0.35) - 0.1 * gauss(f, 2, 6.6, 0.6, 0.15)  # elbow point
    k += 0.08 * gauss(f, 2, 7.6, 1.1, 1.3)                      # forearm
    k += -0.12 * gauss(f, 2, 9.75, 2.5, 0.2)                    # wrist
    # the hand (rows 10-12)
    hand = sstep(9.85, 10.0, Y)
    if f.name == "front":
        for x in (0.5, 1.5, 2.5, 3.5):
            k += hand * (0.14 * ell(f, x, 10.35, 0.34, 0.2))     # knuckles
        for x in (1.0, 2.0, 3.0):
            k += hand * (-0.3 * stroke(f, [(x, 10.55), (x, 12.0)], 0.1))  # between the fingers
        k += hand * (-0.18 * sstep(11.6, 12.0, Y))
        for x in (0.5, 1.5, 2.5, 3.5):
            k += hand * (0.1 * ell(f, x, 11.65, 0.22, 0.18))     # nails
    if f.name == "back":
        k += hand * 0.06
        for x in (1.0, 2.0, 3.0):
            k += hand * (-0.12 * stroke(f, [(x, 10.4), (x, 11.6)], 0.08))  # tendons
    if f.side:
        k += hand * (-0.16 * stroke(f, bezier((0.6, 10.5), (2.0, 11.4), (3.4, 10.9)), 0.12))  # thumb crease
    return k


def _leg_soft(f):
    """A leg: thigh, knee (the model bends at row 6), calf, ankle and a bare foot."""
    X, Y = f.X, f.Y
    k = texture(f, 37)
    if f.name == "top":
        return k
    if f.name == "bottom":
        return k - 0.7 + 0.12 * gauss(f, 2, 2, 0.8, 1.2)
    k += round_edges(f, 0.3, 0.45)
    if f.name == "front":
        k += 0.18 * gauss(f, 2.2, 2.4, 1.1, 1.7)                 # thigh
        k += 0.18 * ell(f, 2, 6.0, 0.85, 0.7) - 0.14 * stroke(f, bezier((1.2, 6.85), (2, 7.05), (2.8, 6.85)), 0.2)  # knee
        k += 0.07 * stroke(f, [(2.25, 7.4), (2.1, 9.6)], 0.4)    # shin
        k += -0.2 * stroke(f, [(2.2, 0.5), (1.6, 3.6), (1.2, 5.2)], 0.14, 0.06)  # between the thigh muscles
        k += 0.12 * ell(f, 1.0, 4.9, 0.55, 0.6)                 # the inner bulge above the knee
    if f.name == "back":
        k += -0.24 * stroke(f, bezier((0.9, 5.95), (2, 6.1), (3.1, 5.95)), 0.14)  # back of the knee
        k += 0.16 * ell(f, 1.3, 7.4, 0.75, 1.3) + 0.16 * ell(f, 2.75, 7.5, 0.7, 1.2)  # calf, two heads
        k += -0.2 * stroke(f, [(2.0, 6.6), (2.0, 8.4)], 0.12, 0.04)
        k += -0.12 * gauss(f, 2, 9.0, 1.5, 0.3)
    if f.side:
        k += 0.08 * ell(f, 1.9, 10.3, 0.3, 0.25)                # ankle bone
    # the foot (rows 10.2-12)
    foot = sstep(10.1, 10.3, Y)
    if f.name == "front":
        k += foot * 0.05
        for x in (0.85, 1.7, 2.45, 3.15):
            k += foot * (-0.28 * stroke(f, [(x, 10.9), (x, 12.0)], 0.09))  # between the toes
        for x, w in ((0.42, 0.34), (1.28, 0.3), (2.08, 0.28), (2.8, 0.26), (3.55, 0.24)):
            k += foot * (0.12 * ell(f, x, 11.35, w, 0.2))        # toenails
        k += foot * (-0.15 * sstep(11.7, 12.0, Y))
    return k


SKIN_BOXES = {"head": "head", "body": "body", "rarm": "arm", "larm": "arm", "rleg": "leg", "lleg": "leg"}


# The skin in ink: the soft fields above, cut into hard light and shadow with gritty edges.
def head_skin(f, nose=True, mouth=True):
    return ink(f, _head_soft(f, nose, mouth), 5, gain=1.3, rough=0.06, keep=0.3) + grit(f, 6, 0.04)


def body_skin(f):
    return ink(f, _body_soft(f), 13, gain=2.0, rough=0.07) + grit(f, 14, 0.05)


def arm_skin(f):
    return ink(f, _arm_soft(f), 25, gain=1.8, rough=0.07) + grit(f, 26, 0.05)


def leg_skin(f):
    return ink(f, _leg_soft(f), 39, gain=1.8, rough=0.07) + grit(f, 40, 0.05)

"""The ninja hound's skeleton and boxes, as data (docs/INUZUKA.md, "The model").

A hound is a small rig of named parts (the Java model animates them by name), each with a pivot,
a rest rotation and a few boxes. Everything is designed here in **design space**: model pixels
(1/16 block), y up, the ground at y = 0, the head toward -z. `export()` turns a rig into the
Minecraft model space the game reads (y down, the ground at y = 24) and packs every box onto a
texture with the standard box UV layout.

Three body plans (`VARIANTS`) were tried side by side; `lean` won (see docs/INUZUKA.md). Each
plan has an adult recipe; a stage blends the puppy recipe into the adult one and adds the fur of
its age (ruff, tufts, the red scarf).
"""
import math

import numpy as np

# Part names the Java model animates (anything else just rides along with its parent).
ANIMATED = ["body", "chest", "hips", "neck", "head", "snout", "jaw", "ear_l", "ear_r",
            "leg_fl", "leg_fl_lower", "leg_fr", "leg_fr_lower",
            "leg_bl", "leg_bl_lower", "leg_bl_foot", "leg_br", "leg_br_lower", "leg_br_foot",
            "tail", "tail2", "tail3", "ruff", "head2", "neck2", "jaw2"]

STAGES = ["puppy", "young", "grown", "great", "giant"]


def rot_matrix(rx, ry, rz):
    """Minecraft's rotation order (ModelPart: Z, then Y, then X, applied to the vertex as Rz·Ry·Rx)."""
    ax, ay, az = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


class Rig:
    """Parts with pivots (relative to the parent, in the parent's frame) and boxes (relative to the pivot)."""

    def __init__(self):
        self.parts = {}
        self.order = []

    def part(self, name, parent, offset, rot=(0, 0, 0)):
        assert name not in self.parts, name
        self.parts[name] = {"name": name, "parent": parent, "offset": tuple(float(v) for v in offset),
                            "rot": tuple(float(v) for v in rot), "cubes": []}
        self.order.append(name)
        return name

    def box(self, part, frm, size, role, inflate=0.0, face=None):
        """A box from `frm` (its low corner, relative to the part's pivot) of `size` (w, h, d)."""
        size = tuple(snap(s) for s in size)
        self.parts[part]["cubes"].append({"from": tuple(float(v) for v in frm), "size": size,
                                          "role": role, "inflate": inflate, "face": face or {}})

    def centered(self, part, center, size, role, inflate=0.0, face=None):
        """A box centred on `center` (relative to the pivot)."""
        size = tuple(snap(s) for s in size)
        frm = tuple(c - s / 2 for c, s in zip(center, size))
        self.box(part, frm, size, role, inflate, face)

    # ------------------------------------------------------------------ world transforms (design space)

    def world(self):
        """Absolute (rotation, translation) of every part at rest, in design space."""
        out = {}
        for name in self.order:
            p = self.parts[name]
            r = rot_matrix(*p["rot"])
            t = np.array(p["offset"])
            if p["parent"]:
                pr, pt = out[p["parent"]]
                out[name] = (pr @ r, pt + pr @ t)
            else:
                out[name] = (r, t)
        return out


def snap(v):
    """Box sizes on a quarter-pixel grid (a whole texel at 4x)."""
    return max(0.25, round(v * 4) / 4)


# ====================================================================== recipes

# The puppy: a big round head, a short muzzle, a round body, short legs on big paws, floppy ears.
PUPPY = dict(
    chest=(5.0, 4.5, 4.5), hips=(4.5, 4.0, 4.0), belly_drop=0.0,
    f_upper=1.75, f_lower=1.5, f_paw=(2.0, 1.25, 2.25), f_th=1.75,
    b_thigh=2.25, b_shin=1.5, b_meta=1.0, b_paw=(2.0, 1.25, 2.25), b_th=2.0,
    neck_len=1.5, neck_th=3.25, neck_angle=60,
    skull=(6.0, 5.5, 5.0), snout=(3.0, 2.0, 2.0), jaw=(2.75, 1.0, 1.75), brow=0.0,
    ear=(2.0, 2.25, 1.0), ear_tip=(1.5, 1.25, 0.75), ear_flop=55, ear_spread=12,
    tail_n=2, tail_len=1.75, tail_th=1.5, tail_angle=45, tail_curl=-15,
    ruff=0.0, tufts=0, scarf=False, fang=0.0, mane_back=0.0,
)

# Adult body plans (the "grown" hound); the great hound and the giant add mass and fur on top.
VARIANTS = {
    # A: the vanilla wolf grown up: square boxes, straight legs, one-piece body.
    "classic": dict(
        chest=(6.0, 6.0, 7.0), hips=(6.0, 6.0, 7.5), belly_drop=0.0,
        f_upper=4.5, f_lower=4.0, f_paw=(2.25, 1.25, 2.5), f_th=2.0,
        b_thigh=4.5, b_shin=3.0, b_meta=2.0, b_paw=(2.25, 1.25, 2.5), b_th=2.25,
        neck_len=2.5, neck_th=4.0, neck_angle=55,
        skull=(6.0, 5.5, 4.5), snout=(3.0, 2.75, 4.0), jaw=(2.75, 1.0, 3.25), brow=0.0,
        ear=(2.0, 2.5, 1.0), ear_tip=(1.25, 1.0, 0.75), ear_flop=0, ear_spread=6,
        tail_n=2, tail_len=4.0, tail_th=2.0, tail_angle=-35, tail_curl=-10,
        ruff=1.0, tufts=0, scarf=True, fang=0.5, mane_back=0.0,
    ),
    # B: a lean wolf-hound: deep chest, tucked waist, long jointed legs, long muzzle, tall ears.
    "lean": dict(
        chest=(6.5, 7.0, 7.0), hips=(5.25, 5.0, 7.0), belly_drop=1.75,
        f_upper=5.0, f_lower=4.5, f_paw=(2.25, 1.25, 2.75), f_th=2.0,
        b_thigh=5.0, b_shin=4.0, b_meta=2.75, b_paw=(2.25, 1.25, 2.75), b_th=2.5,
        neck_len=3.5, neck_th=4.25, neck_angle=50,
        skull=(5.5, 5.0, 4.75), snout=(2.75, 2.5, 4.75), jaw=(2.5, 1.0, 4.0), brow=0.75,
        ear=(2.25, 2.75, 1.5), ear_tip=(1.25, 1.75, 1.0), ear_flop=0, ear_spread=10,
        tail_n=3, tail_len=3.0, tail_th=2.25, tail_angle=-50, tail_curl=-14,
        ruff=1.0, tufts=1, scarf=True, fang=0.75, mane_back=0.5,
    ),
    # C: a heavy ninken (a big fluffy guard dog): wide head, short muzzle, thick legs, a huge mane.
    "heavy": dict(
        chest=(8.0, 7.5, 7.5), hips=(7.0, 6.5, 7.0), belly_drop=0.5,
        f_upper=4.0, f_lower=3.5, f_paw=(3.0, 1.5, 3.25), f_th=3.0,
        b_thigh=4.0, b_shin=3.0, b_meta=1.75, b_paw=(3.0, 1.5, 3.25), b_th=3.25,
        neck_len=2.0, neck_th=5.5, neck_angle=50,
        skull=(7.0, 6.0, 5.5), snout=(3.75, 3.0, 3.25), jaw=(3.5, 1.25, 2.75), brow=0.5,
        ear=(2.25, 2.0, 1.25), ear_tip=(1.5, 1.0, 1.0), ear_flop=25, ear_spread=16,
        tail_n=2, tail_len=3.5, tail_th=3.0, tail_angle=-20, tail_curl=-40,
        ruff=1.6, tufts=1, scarf=True, fang=0.75, mane_back=1.0,
    ),
}

# How far each stage is from the puppy (0) to the adult plan (1), plus what each age adds.
STAGE_BLEND = {
    "puppy": dict(t=0.0, ruff=0.0, tufts=0, scarf=False, fang=0.0, mass=1.0, mane_back=0.0),
    # A gangly adolescent: legs and paws nearly adult, the head still big, the body thin.
    "young": dict(t=0.6, ruff=0.4, tufts=0, scarf=False, fang=0.25, mass=0.92, mane_back=0.0, legs=0.85, head=0.45),
    "grown": dict(t=1.0, ruff=1.0, tufts=1, scarf=True, fang=1.0, mass=1.0, mane_back=1.0),
    "great": dict(t=1.0, ruff=1.45, tufts=2, scarf=True, fang=1.3, mass=1.12, mane_back=1.5),
    "giant": dict(t=1.0, ruff=1.8, tufts=3, scarf=True, fang=1.6, mass=1.2, mane_back=2.0),
}

# Shoulder height (top of the withers) in blocks for each stage: the size you see in game. A
# puppy fits on your head; the great hound stands taller at the shoulder than a horse.
SHOULDER_BLOCKS = {"puppy": 0.32, "young": 0.62, "grown": 0.95, "great": 1.65, "giant": 2.45}


def lerp(a, b, t):
    if isinstance(a, tuple):
        return tuple(lerp(x, y, t) for x, y in zip(a, b))
    if isinstance(a, bool):
        return b if t >= 0.5 else a
    return a + (b - a) * t


def stage_recipe(variant, stage):
    adult = VARIANTS[variant]
    blend = STAGE_BLEND[stage]
    t = blend["t"]
    r = {}
    for k in adult:
        if k in ("ruff", "tufts", "scarf", "fang", "mane_back"):
            continue
        tt = t
        if k.startswith(("f_", "b_")):
            tt = blend.get("legs", t)   # adolescents shoot up in the legs first
        if k in ("skull", "snout", "jaw", "ear", "ear_tip", "ear_flop", "brow"):
            tt = blend.get("head", t)   # ...and grow into their head last
        r[k] = lerp(PUPPY[k], adult[k], tt)
        if isinstance(r[k], float) and k in ("tail_n",):
            r[k] = round(r[k])
    r["tail_n"] = int(round(lerp(PUPPY["tail_n"], adult["tail_n"], t)))
    r["ruff"] = adult["ruff"] * blend["ruff"]
    r["tufts"] = min(adult["tufts"] + blend["tufts"] - 1, 3) if blend["tufts"] else 0
    r["scarf"] = blend["scarf"]
    r["fang"] = adult["fang"] * blend["fang"]
    r["mane_back"] = adult["mane_back"] * blend["mane_back"]
    m = blend["mass"]
    for k in ("chest", "hips", "neck_th", "f_th", "b_th", "f_paw", "b_paw"):
        v = r[k]
        r[k] = tuple(x * m for x in v) if isinstance(v, tuple) else v * m
    if stage in ("great", "giant"):
        # Heavier in front: a broad chest and a deep mane over the shoulders.
        c = r["chest"]
        r["chest"] = (c[0] * 1.05, c[1] * 1.04, c[2])
    return r


# ====================================================================== building a rig

def build(r, two_heads=False):
    """The rig for one recipe (see PUPPY / VARIANTS for the keys)."""
    rig = Rig()
    cw, ch, cd = r["chest"]
    hw, hh_, hd = r["hips"]

    # Leg lengths give the joint heights.
    f_len = r["f_upper"] + r["f_lower"] + r["f_paw"][1] * 0.5
    a_thigh, a_shin, a_meta = 22, -48, 26  # rest angles: thigh forward, shin back, the hock near vertical
    b_drop = (r["b_thigh"] * math.cos(math.radians(a_thigh)) + r["b_shin"] * math.cos(math.radians(a_thigh + a_shin))
              + r["b_meta"] * math.cos(math.radians(a_thigh + a_shin + a_meta)) + r["b_paw"][1] * 0.5)
    shoulder_y = f_len
    # The back legs are scaled to put the hip joint just under the shoulder (a level back).
    k = (shoulder_y - 0.25) / b_drop
    r = dict(r, b_thigh=r["b_thigh"] * k, b_shin=r["b_shin"] * k, b_meta=r["b_meta"] * k)
    hip_y = shoulder_y - 0.25
    torso = cd + hd - 1.0  # chest and hips overlap a little
    chest_z = -torso / 2 + cd / 2
    hips_z = torso / 2 - hd / 2
    hip_joint_z = hips_z + hd * 0.2

    # The body pivots at the hip joint (sitting tips it up there); everything else hangs off it.
    rig.part("root", None, (0, 0, 0))
    rig.part("body", "root", (0, hip_y, hip_joint_z))
    rel = lambda y, z: (y - hip_y, z - hip_joint_z)  # noqa: E731  (design y, z → relative to the body pivot)

    # Chest: its top sits a little above the shoulder joint; a deep chest hangs below.
    chest_top = shoulder_y + ch * 0.32
    chest_cy = chest_top - ch / 2
    y, z = rel(chest_cy, chest_z)
    rig.part("chest", "body", (0, y, z))
    rig.centered("chest", (0, 0, 0), (cw, ch, cd), "chest")
    # Hips: a tucked waist (their bottom higher than the chest's).
    hips_top = min(hip_y + hh_ * 0.5, chest_top - 0.4)
    hips_cy = hips_top - hh_ / 2
    y, z = rel(hips_cy, hips_z)
    rig.part("hips", "body", (0, y, z))
    rig.centered("hips", (0, 0, 0), (hw, hh_, hd), "hips")
    if r["belly_drop"] > 0.5:
        # The tuck: a narrower box under the loin, between the chest and the hips.
        rig.centered("hips", (0, -hh_ / 2 + 0.4, -hd / 2 + 1.0), (hw * 0.85, 1.25, 2.5), "belly", inflate=-0.01)

    # Mane over the shoulders (grown and up): a fluffier box over the chest's top and front.
    if r["mane_back"] > 0.2:
        mb = r["mane_back"]
        rig.centered("chest", (0, ch / 2 - 0.3 + mb * 0.25, 0.5), (cw + mb * 0.8, 1.0 + mb * 0.8, cd * 0.75), "mane")

    # Front legs: upper arm from the shoulder, forearm, paw.
    for side, sx in (("l", 1), ("r", -1)):
        x = sx * (cw / 2 - r["f_th"] / 2 + 0.15)
        y, z = rel(shoulder_y, chest_z - cd * 0.18)
        up = rig.part("leg_f" + side, "body", (x, y, z), (6, 0, 0))
        rig.box(up, (-r["f_th"] / 2, -r["f_upper"], -r["f_th"] / 2 - 0.25), (r["f_th"], r["f_upper"] + 0.5, r["f_th"] + 0.5), "leg")
        lo = rig.part("leg_f" + side + "_lower", up, (0, -r["f_upper"], 0), (-6, 0, 0))
        th = r["f_th"] * 0.88
        rig.box(lo, (-th / 2, -r["f_lower"], -th / 2), (th, r["f_lower"], th), "leg_lower")
        pw, ph, pd = r["f_paw"]
        rig.box(lo, (-pw / 2, -r["f_lower"] - ph * 0.5, -pd + th / 2 + 0.1), (pw, ph, pd), "paw")
        if r["tufts"] >= 2:
            # Feathering behind the elbow.
            rig.box(up, (-r["f_th"] / 2 + 0.25, -r["f_upper"] + 0.5, r["f_th"] / 2 + 0.25), (r["f_th"] - 0.5, 2.0, 0.75), "tuft")

    # Back legs: thigh (forward), shin (back), hock (down), paw.
    for side, sx in (("l", 1), ("r", -1)):
        x = sx * (hw / 2 - r["b_th"] / 2 + 0.2)
        up = rig.part("leg_b" + side, "body", (x, 0, 0), (a_thigh, 0, 0))
        th = r["b_th"]
        rig.box(up, (-th / 2, -r["b_thigh"], -th / 2 - 0.5), (th, r["b_thigh"] + 1.0, th + 1.0), "thigh")
        sh = rig.part("leg_b" + side + "_lower", up, (0, -r["b_thigh"], 0.25), (a_shin, 0, 0))
        th2 = th * 0.78
        rig.box(sh, (-th2 / 2, -r["b_shin"], -th2 / 2), (th2, r["b_shin"] + 0.25, th2), "leg")
        ft = rig.part("leg_b" + side + "_foot", sh, (0, -r["b_shin"], 0), (a_meta, 0, 0))
        th3 = th * 0.66
        rig.box(ft, (-th3 / 2, -r["b_meta"], -th3 / 2), (th3, r["b_meta"] + 0.25, th3), "leg_lower")
        pw, ph, pd = r["b_paw"]
        # The paw lies flat whatever the leg's angles: undo the chain's rest tilt.
        paw = rig.part("paw_b" + side, ft, (0, -r["b_meta"], 0), (-(a_thigh + a_shin + a_meta), 0, 0))
        rig.box(paw, (-pw / 2, -ph * 0.5, -pd + th3 / 2 + 0.1), (pw, ph, pd), "paw")

    # Neck and head (the head's pivot is where the skull meets the neck).
    neck_base_y = chest_top - 1.0
    y, z = rel(neck_base_y, chest_z - cd / 2 + 1.25)
    na = r["neck_angle"]
    nt = r["neck_th"]
    rig.part("neck", "body", (0, y, z), (-na, 0, 0))
    rig.box("neck", (-nt / 2, -0.5, -nt / 2), (nt, r["neck_len"] + 1.0, nt), "neck")
    if r["scarf"]:
        # The ninken's red scarf: a band round the neck and a knot with two tails at the back.
        rig.box("neck", (-nt / 2 - 0.4, 0.2, -nt / 2 - 0.4), (nt + 0.8, 1.5, nt + 0.8), "scarf")
        rig.box("neck", (-0.9, 0.0, nt / 2 + 0.2), (1.8, 1.8, 0.9), "scarf_knot")
    if r["ruff"] > 0.1:
        rf = r["ruff"]
        # The ruff: fluffy fur round the throat and the cheeks.
        rig.box("neck", (-nt / 2 - 0.3 * rf, -0.5, -nt / 2 - 0.6 * rf), (nt + 0.6 * rf, r["neck_len"] * 0.8 + 0.8, 0.9 * rf + 0.5), "ruff")
        rig.box("neck", (-nt / 2 - 0.45 * rf, r["neck_len"] * 0.25, -nt / 2 + 0.25), (nt + 0.9 * rf, r["neck_len"] * 0.75 + 0.5, nt * 0.8), "ruff")
    rig.part("head", "neck", (0, r["neck_len"], 0), (na, 0, 0))
    _head(rig, "head", r, two_heads=False)
    if two_heads:
        # The Two-Headed Wolf: a second neck and head beside the first.
        y, z = rel(neck_base_y, chest_z - cd / 2 + 1.25)
        rig.part("neck2", "body", (-nt * 0.62, y - 0.25, z + 0.5), (-na + 5, 0, -16))
        rig.box("neck2", (-nt / 2, -0.5, -nt / 2), (nt, r["neck_len"] + 1.0, nt), "neck")
        rig.box("neck2", (-nt / 2 - 0.3, -0.5, -nt / 2 - 0.6), (nt + 0.6, r["neck_len"] * 0.8 + 0.8, 1.2), "ruff")
        rig.part("head2", "neck2", (0, r["neck_len"], 0), (na - 5, 12, 16))
        _head(rig, "head2", r, two_heads=True)
        rig.parts["neck"]["offset"] = (nt * 0.62, rig.parts["neck"]["offset"][1], rig.parts["neck"]["offset"][2])
        rig.parts["neck"]["rot"] = (-na, 0, 16)
        rig.parts["head"]["rot"] = (na, -12, -16)

    # Tail: segments curving down and back from the top of the hips.
    tt = r["tail_th"]
    y, z = rel(hips_top - 0.75, hips_z + hd / 2 - 0.25)
    prev = rig.part("tail", "body", (0, y, z), (90 - r["tail_angle"], 0, 0))
    rig.box(prev, (-tt / 2, -0.25, -tt / 2), (tt, r["tail_len"] + 0.25, tt), "tail")
    for i in range(1, r["tail_n"]):
        name = "tail" + str(i + 1)
        th = tt * (1.15 if i == 1 and r["tufts"] else 1.0) * (0.9 ** i)
        rig.part(name, prev, (0, r["tail_len"], 0), (r["tail_curl"], 0, 0))
        rig.box(name, (-th / 2, 0, -th / 2), (th, r["tail_len"], th), "tail" if i < r["tail_n"] - 1 else "tail_tip")
        prev = name
    return rig


def _head(rig, head, r, two_heads):
    sw, shh, sd = r["skull"]
    # The skull: centred a little ahead of and above the neck's end.
    rig.box(head, (-sw / 2, -shh * 0.45, -sd * 0.75), (sw, shh, sd), "head")
    if r["brow"] > 0.1:
        # A heavy brow over the eyes: a ledge on the skull's front top.
        rig.box(head, (-sw / 2 + 0.25, shh * 0.55 - 1.0, -sd * 0.75 - 0.5), (sw - 0.5, 1.0, 0.75), "brow")
    snw, snh, snd = r["snout"]
    sn = rig.part("snout" if head == "head" else "snout2", head, (0, -shh * 0.45 + 0.25, -sd * 0.75), (-3, 0, 0))
    rig.box(sn, (-snw / 2, 0, -snd), (snw, snh, snd), "snout")
    jw, jh, jd = r["jaw"]
    jaw = rig.part("jaw" if head == "head" else "jaw2", head, (0, -shh * 0.45 + 0.25, -sd * 0.75 + 0.75), (-2, 0, 0))
    rig.box(jaw, (-jw / 2, -jh, -jd - 0.75), (jw, jh, jd + 0.75), "jaw")
    # Ears: a base and a tip; a puppy's flop over.
    ew, eh, ed = r["ear"]
    tw, th, tdp = r["ear_tip"]
    for side, sx in (("l", 1), ("r", -1)):
        name = ("ear_" if head == "head" else "ear2_") + side
        ear = rig.part(name, head, (sx * (sw / 2 - ew / 2 - 0.1), shh * 0.55 - 0.25, -sd * 0.25), (-6, 0, sx * -r["ear_spread"]))
        rig.box(ear, (-ew / 2, 0, -ed / 2), (ew, eh, ed), "ear")
        tip = rig.part(name + "_tip", ear, (0, eh, 0), (-r["ear_flop"], 0, 0))
        rig.box(tip, (-tw / 2, 0, -tdp / 2), (tw, th, tdp), "ear_tip")
    # Cheek tufts (grown and up).
    if r["tufts"] >= 1:
        for sx in (1, -1):
            rig.box(head, (sx * (sw / 2) - (0.75 if sx > 0 else 0), -shh * 0.45 + 0.25, -sd * 0.5), (0.75, shh * 0.55, sd * 0.6), "tuft")
    if r["fang"] > 0.2:
        # Upper fangs peeking past the lips.
        fh = 0.5 + r["fang"] * 0.5
        for sx in (1, -1):
            rig.box("snout" if head == "head" else "snout2", (sx * (snw / 2 - 0.5) - 0.25, -fh + 0.1, -r["snout"][2] + 0.25),
                    (0.5, fh, 0.5), "fang")


# ====================================================================== export

TEX_SCALE = 4  # texels per model pixel (the HD art rule: 4x)


def export(rig, name, tex_w=64):
    """Minecraft-space parts (y down, ground at 24) with packed box UVs. Returns (geometry, texture size)."""
    m = lambda v: (v[0], -v[1], v[2])  # noqa: E731
    parts = []
    shelf_x, shelf_y, shelf_h = 0.0, 0.0, 0.0
    boxes = []
    for pname in rig.order:
        p = rig.parts[pname]
        off = m(p["offset"])
        if p["parent"] is None:
            off = (off[0], off[1] + 24.0, off[2])
        rx, ry, rz = p["rot"]
        out = {"name": pname, "parent": p["parent"] or "", "pivot": [round(v, 4) for v in off],
               "rot": [round(-rx, 3), round(ry, 3), round(-rz, 3)], "cubes": []}
        for c in p["cubes"]:
            w, h, d = c["size"]
            fx, fy, fz = c["from"]
            mc_from = (fx, -(fy + h), fz)
            uw, uh = 2 * d + 2 * w, d + h
            # Shelf packing on a quarter-pixel grid, with a pixel of padding.
            uw_p, uh_p = math.ceil(uw) + 1, math.ceil(uh) + 1
            if shelf_x + uw_p > tex_w:
                shelf_x, shelf_y, shelf_h = 0.0, shelf_y + shelf_h, 0.0
            u, v = shelf_x, shelf_y
            shelf_x += uw_p
            shelf_h = max(shelf_h, uh_p)
            cube = {"from": [round(x, 4) for x in mc_from], "size": [w, h, d], "uv": [u, v],
                    "inflate": c["inflate"], "role": c["role"]}
            out["cubes"].append(cube)
            boxes.append((pname, cube))
        parts.append(out)
    tex_h = int(math.ceil((shelf_y + shelf_h) / 16.0) * 16)
    return {"name": name, "texture": [tex_w, tex_h], "parts": parts}, (tex_w, tex_h)


def shoulder_px(rig):
    """The top of the withers in design pixels (for the stage's render scale)."""
    w = rig.world()
    r, t = w["chest"]
    top = -1e9
    for c in rig.parts["chest"]["cubes"]:
        if c["role"] != "chest":
            continue
        fx, fy, fz = c["from"]
        _, h, _ = c["size"]
        top = max(top, (t + r @ np.array([0, fy + h, 0]))[1])
    return top

"""motion.py: a small library for authoring NOIRM's keyframe animations (v2).

The output is the same JSON the game reads (GeckoLib/Bedrock style, plus NOIRM's "bend" channel),
in assets/noirm/player_animation/. The game plays it on players and NPC ninjas alike through
client/anim/HumanoidAnimator, on the jointed model (elbows, knees, waist; the torso carries the arms
and head), and adds springs, so limbs overshoot a little and settle.

Conventions (degrees and pixels, the model facing forward):
  arms / legs  x: negative raises the limb forward/up.  y: positive swings it to the model's right.
               z: positive moves it to the model's right (right limb outward, left limb inward).
               bend: positive flexes the elbow / knee.
  torso        pivots on the hips and carries head and arms.
               x: positive leans forward.  y: positive turns right (left shoulder comes forward).
               z: positive leans to the model's left.  bend: positive hunches at the waist.
  head         x positive looks down; y positive looks right.
  body         the whole character (use for lunges, jumps, spins):
               x: positive pitches forward.  y: positive turns right.  z: positive rolls left.
               pos: (x left, y up, z back) in pixels.
Leave the head out of an attack unless it matters: the game keeps the head looking at the target.

Timing: an attack is written in ticks against its move's windup / active / recovery, so the strike
lands exactly when the server's hit does. Easing names are those the game knows (see
KeyframeAnimation.ease): each key's easing shapes the way INTO that key.
"""
import json
import os

OUT = "src/main/resources/assets/noirm/player_animation"
PARTS = ["torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"]
ALL = ["body"] + PARTS
Z3 = (0.0, 0.0, 0.0)


class Pose:
    """A full or partial pose: bone -> dict(rot=(x,y,z), pos=(x,y,z), bend=b)."""

    def __init__(self, bones=None):
        self.bones = {k: dict(v) for k, v in (bones or {}).items()}

    def copy(self):
        return Pose(self.bones)

    def set(self, bone, rot=None, pos=None, bend=None):
        b = self.bones.setdefault(bone, {})
        if rot is not None:
            b["rot"] = tuple(float(v) for v in rot)
        if pos is not None:
            b["pos"] = tuple(float(v) for v in pos)
        if bend is not None:
            b["bend"] = float(bend)
        return self

    def __call__(self, **changes):
        """A copy with changes: bone=(x,y,z) or bone=dict(rot=..., pos=..., bend=...)
        or bone=(x,y,z,bend) for limbs and torso."""
        p = self.copy()
        for bone, value in changes.items():
            if isinstance(value, dict):
                p.set(bone, **value)
            elif len(value) == 4:
                p.set(bone, rot=value[:3], bend=value[3])
            else:
                p.set(bone, rot=value)
        return p

    def add(self, **changes):
        """A copy with rotations / bends / positions added (bone=(dx,dy,dz[,dbend]) or dict)."""
        p = self.copy()
        for bone, value in changes.items():
            b = p.bones.setdefault(bone, {})
            if not isinstance(value, dict):
                value = dict(rot=value[:3], bend=value[3] if len(value) > 3 else None)
            for key in ("rot", "pos"):
                if value.get(key) is not None:
                    old = b.get(key, Z3)
                    b[key] = tuple(o + d for o, d in zip(old, value[key]))
            if value.get("bend") is not None:
                b["bend"] = b.get("bend", 0.0) + value["bend"]
        return p


def mirror(pose):
    """Left <-> right: swap the limbs, flip yaw, roll and sideways offsets."""
    out = Pose()
    swap = {"right_arm": "left_arm", "left_arm": "right_arm", "right_leg": "left_leg", "left_leg": "right_leg"}
    for bone, b in pose.bones.items():
        nb = {}
        if "rot" in b:
            x, y, z = b["rot"]
            nb["rot"] = (x, -y, -z)
        if "pos" in b:
            x, y, z = b["pos"]
            nb["pos"] = (-x, y, z)
        if "bend" in b:
            nb["bend"] = b["bend"]
        out.bones[swap.get(bone, bone)] = nb
    return out


def P(**kw):
    return Pose()(**kw)


class Anim:
    """An animation built from keys at tick times; every key is a full pose of the bones used."""

    def __init__(self, name, ticks, loop=False, timing=None):
        self.name = name
        self.ticks = float(ticks)
        self.loop = loop
        self.timing = timing
        self.keys = []

    def key(self, tick, pose, ease="easeInOutSine"):
        self.keys.append((float(tick), pose, ease))
        return self

    def bones_used(self):
        used = []
        for bone in ALL:
            if any(bone in p.bones for _, p, _ in self.keys):
                used.append(bone)
        return used

    def to_json(self):
        keys = sorted(self.keys, key=lambda k: k[0])
        bones = {}
        for bone in self.bones_used():
            rot, pos, bend = {}, {}, {}
            has_pos = any("pos" in p.bones.get(bone, {}) for _, p, _ in keys)
            has_bend = any("bend" in p.bones.get(bone, {}) for _, p, _ in keys)
            for tick, pose, ease in keys:
                b = pose.bones.get(bone, {})
                stamp = f"{round(tick / 20.0, 4)}"
                rot[stamp] = {"vector": [round(v, 2) for v in b.get("rot", Z3)], "easing": ease}
                if has_pos:
                    pos[stamp] = {"vector": [round(v, 2) for v in b.get("pos", Z3)], "easing": ease}
                if has_bend:
                    bend[stamp] = {"vector": [round(b.get("bend", 0.0), 2)], "easing": ease}
            entry = {"rotation": rot}
            if has_pos:
                entry["position"] = pos
            if has_bend:
                entry["bend"] = bend
            bones[bone] = entry
        anim = {"animation_length": round(self.ticks / 20.0, 4), "bones": bones}
        if self.loop:
            anim["loop"] = True
        return {"format_version": "1.8.0", "animations": {self.name: anim}}

    def write(self, out=OUT):
        os.makedirs(out, exist_ok=True)
        with open(os.path.join(out, self.name + ".json"), "w") as f:
            json.dump(self.to_json(), f, indent=1)


def strike(name, start, windup, contact, follow, timing, end=None, antic_at=0.55, settle=None,
           ease_in="easeInCubic", hold=0.6):
    """The standard attack shape, timed to the move (windup, active, recovery ticks):
    start pose -> wind-up (anticipation) -> contact (exactly when the hit lands) -> follow-through
    (held through the active frames) -> settle -> back to the start (or `end`)."""
    w, a, r = timing
    total = w + a + r
    anim = Anim(name, total, timing=timing)
    anim.key(0, start)
    anim.key(max(0.6, w * antic_at), windup, "easeOutQuad")
    anim.key(w, contact, ease_in)
    anim.key(w + a * hold + 0.5, follow, "easeOutQuad")
    anim.key(w + a + r * 0.45, settle if settle is not None else follow, "easeInOutSine")
    anim.key(total, end if end is not None else start, "easeInOutSine")
    return anim

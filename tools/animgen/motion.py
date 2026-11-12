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

Snappy key poses (the 2026-11 rebuild, docs/COMBAT_DESIGN.md): attacks are written with snap(), as
3-5 strong poses: a quick wind-up, an instant snap ("snap" easing) to an exaggerated strike pose
that is held on the hit tick ("hold"), a short recovery, back to the stance. The game's springs
don't smooth snap keys away. exaggerate() pushes poses further for the blocky model: a bigger
torso twist, wider and lower legs, the striking arm fully straight.
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

        self.strike = None
        self.strike_hold = 0.0

    def key(self, tick, pose, ease="easeInOutSine", hold=0.0):
        """A key at `tick`. ease="snap": reached almost at once (see KeyframeAnimation.snap);
        `hold` (ticks): the pose is held that long before the next key starts."""
        self.keys.append((float(tick), pose, ease, float(hold)))
        return self

    def snapkey(self, tick, pose, hold=0.0, strike=True):
        """A snap key, optionally the strike marker (the hit lands here, held `hold` ticks)."""
        self.key(tick, pose, "snap", hold)
        if strike:
            self.strike = float(tick)
            self.strike_hold = float(hold)
        return self

    def bones_used(self):
        used = []
        for bone in ALL:
            if any(bone in k[1].bones for k in self.keys):
                used.append(bone)
        return used

    def to_json(self):
        keys = sorted(self.keys, key=lambda k: k[0])
        bones = {}
        for bone in self.bones_used():
            rot, pos, bend = {}, {}, {}
            has_pos = any("pos" in k[1].bones.get(bone, {}) for k in keys)
            has_bend = any("bend" in k[1].bones.get(bone, {}) for k in keys)
            for tick, pose, ease, hold in keys:
                b = pose.bones.get(bone, {})
                stamp = f"{round(tick / 20.0, 4)}"

                def frame(vector):
                    f = {"vector": vector, "easing": ease}
                    if hold > 0:
                        f["hold"] = round(hold / 20.0, 4)
                    return f
                rot[stamp] = frame([round(v, 2) for v in b.get("rot", Z3)])
                if has_pos:
                    pos[stamp] = frame([round(v, 2) for v in b.get("pos", Z3)])
                if has_bend:
                    bend[stamp] = frame([round(b.get("bend", 0.0), 2)])
            entry = {"rotation": rot}
            if has_pos:
                entry["position"] = pos
            if has_bend:
                entry["bend"] = bend
            bones[bone] = entry
        anim = {"animation_length": round(self.ticks / 20.0, 4), "bones": bones}
        if self.loop:
            anim["loop"] = True
        if self.strike is not None:
            anim["strike"] = round(self.strike / 20.0, 4)
            anim["strike_hold"] = round(self.strike_hold / 20.0, 4)
        return {"format_version": "1.8.0", "animations": {self.name: anim}}

    def write(self, out=OUT):
        os.makedirs(out, exist_ok=True)
        with open(os.path.join(out, self.name + ".json"), "w") as f:
            json.dump(self.to_json(), f, indent=1)


# Weight transfer added to every strike (pixels): the body sinks into the legs on the wind-up and
# drives forward and down at contact. The game keeps standing feet planted (leg IK), so this
# becomes bent knees and a pushing rear leg instead of feet sinking or sliding.
SINK_WINDUP = (0.0, -1.0, 0.5)
DRIVE_CONTACT = (0.0, -1.4, -1.5)


def _weighted(pose, offset):
    b = pose.bones.get("body", {})
    if "rot" in b and any(abs(v) > 20 for v in b["rot"]):
        return pose  # spins and flips leave the ground: no weight transfer
    return pose.add(body=dict(pos=offset))


def strike(name, start, windup, contact, follow, timing, end=None, antic_at=0.55, settle=None,
           ease_in="easeInCubic", hold=0.6, weight=True):
    """The standard attack shape, timed to the move (windup, active, recovery ticks):
    start pose -> wind-up (anticipation) -> contact (exactly when the hit lands) -> follow-through
    (held through the active frames) -> settle -> back to the start (or `end`).
    With `weight`, the body sinks on the wind-up and drives through the contact (footwork)."""
    if weight:
        windup = _weighted(windup, SINK_WINDUP)
        contact = _weighted(contact, DRIVE_CONTACT)
        follow = _weighted(follow, DRIVE_CONTACT)
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


# ------------------------------------------------------------------ snappy key poses (2026-11)

def full(pose, like):
    """`pose` with every bone/channel `like` has (missing ones taken from `like`), so keys line up."""
    out = pose.copy()
    for bone, b in like.bones.items():
        nb = out.bones.setdefault(bone, {})
        for k, v in b.items():
            nb.setdefault(k, v)
    return out


def mix(a, b, t):
    """The pose a fraction `t` of the way from `a` to `b` (missing channels count as zero)."""
    out = Pose()
    for bone in set(a.bones) | set(b.bones):
        ba, bb = a.bones.get(bone, {}), b.bones.get(bone, {})
        nb = {}
        for key in ("rot", "pos"):
            if key in ba or key in bb:
                va, vb = ba.get(key, Z3), bb.get(key, Z3)
                nb[key] = tuple(x + (y - x) * t for x, y in zip(va, vb))
        if "bend" in ba or "bend" in bb:
            nb["bend"] = ba.get("bend", 0.0) + (bb.get("bend", 0.0) - ba.get("bend", 0.0)) * t
        out.bones[bone] = nb
    return out


# How far exaggerate() pushes a pose (1 = the rebuild's default).
TWIST = 0.35      # torso yaw x(1 + 0.35)
LEAN = 0.2        # torso pitch x(1 + 0.2)
STRIDE = 0.25     # leg swing x(1 + 0.25)
SPREAD = 0.45     # leg spread (sideways) x(1 + 0.45), plus SPREAD_PX
SPREAD_PX = 2.0
LOWER = 1.2       # the body drops this many pixels
KNEES = 8.0       # knees bend this many degrees more
DRIVE = 0.3       # forward drive x(1 + 0.3)
STRAIGHT = 25.0   # on a strike, an arm (or leg) bent less than this is snapped fully straight


def exaggerate(pose, amount=1.0, strike=False):
    """A bigger version of `pose` for the blocky model: more twist and lean, legs wider and lower,
    the body driven further; on a strike, the hitting arm or leg goes fully straight."""
    out = pose.copy()
    t = out.bones.get("torso")
    if t and "rot" in t:
        x, y, z = t["rot"]
        t["rot"] = (x * (1 + LEAN * amount), max(-80.0, min(80.0, y * (1 + TWIST * amount))), z)
    for leg, side in (("right_leg", 1.0), ("left_leg", -1.0)):
        b = out.bones.get(leg)
        if b and "rot" in b:
            x, y, z = b["rot"]
            if abs(x) < 60:  # a stance or stepping leg (not a kick): wider, longer stride, lower
                z = z * (1 + SPREAD * amount) + side * SPREAD_PX * amount
                x = x * (1 + STRIDE * amount)
                b["bend"] = b.get("bend", 0.0) + KNEES * amount
            elif strike and b.get("bend", 0.0) <= STRAIGHT:
                b["bend"] = 0.0  # a kick lands with the leg straight
            b["rot"] = (x, y, z)
    if strike:
        for arm in ("right_arm", "left_arm"):
            b = out.bones.get(arm)
            if b and "rot" in b and b.get("bend", 99.0) <= STRAIGHT and b["rot"][0] < -40:
                b["bend"] = 0.0  # the hitting arm: fully extended
    body = out.bones.get("body", {})
    if not ("rot" in body and any(abs(v) > 20 for v in body["rot"])):  # not mid-spin or flip
        x, y, z = body.get("pos", Z3)
        out.bones.setdefault("body", {})["pos"] = (x, y - LOWER * amount, z * (1 + DRIVE * amount))
    return out


def hold_for(active):
    """How long a strike pose is held on the hit tick: the active frames, 1-3 ticks."""
    return max(1.0, min(3.0, float(active)))


def snap(name, start, windup, contact, follow=None, timing=(2, 2, 4), end=None, antic_at=0.5, settle=None,
         ease_in=None, hold=None, weight=True, amp=1.0, recover=0.4, **_):
    """The rebuild's attack shape, 3-5 strong poses timed to the move (windup, active, recovery):
    stance -> a quick wind-up -> SNAP to the exaggerated strike pose on the hit tick, held through
    the active frames -> a short recovery -> back to the stance (or `end`).
    `follow` / `settle` (from the old strike()) shape the recovery pose. Takes strike()'s arguments."""
    if weight:
        windup = _weighted(windup, SINK_WINDUP)
        contact = _weighted(contact, DRIVE_CONTACT)
    w, a, r = timing
    total = w + a + r
    h = hold_for(a) if hold is None else float(hold)
    hit = full(exaggerate(contact, amp, strike=True), start)
    wind = full(exaggerate(windup, amp * 0.8), start)
    back = settle if settle is not None else (follow if follow is not None else contact)
    rec = full(mix(full(exaggerate(back, amp * 0.6), start), start, 0.35), start)
    anim = Anim(name, total, timing=timing)
    anim.key(0, full(start, hit))
    if w >= 1.5:
        anim.key(max(0.6, w * antic_at), wind, "easeOutCubic")
    anim.snapkey(w, hit, hold=h)
    t_rec = w + h + max(1.0, (total - w - h) * recover)
    if t_rec < total - 0.5:
        anim.key(t_rec, rec, "easeOutQuad")
    anim.key(total, full(end if end is not None else start, hit), "easeInOutSine")
    return anim


def stance_set(name, pose, period, breathe, kinds=("aggressive", "defensive", "low"), lead="left"):
    """A style's stance and its sub-stances (each a loop): stance_<name>[_<kind>]. Returns the Anims.
    aggressive: forward on the toes, lead hand reaching, a faster bounce; defensive: hands high and
    tight, hunched, weight back; low: deep in the knees; air: limbs out for balance."""
    lead_arm = lead + "_arm"
    rear_arm = ("right" if lead == "left" else "left") + "_arm"
    out = [_loop("stance_" + name, pose, period, breathe)]
    for kind in kinds:
        if kind == "aggressive":
            p = pose.add(torso=(8, 0, 0, 4), body=dict(pos=(0, -0.8, -1.2)))
            p = p.add(**{lead_arm: (-14, 0, 0, -25), rear_arm: (-8, 0, 0, -5)})
            b = dict(breathe)
            b["body"] = dict(pos=(0, -0.9, 0))
            out.append(_loop("stance_%s_aggressive" % name, p, max(8, period * 0.5), b))
        elif kind == "defensive":
            p = pose.add(torso=(12, 0, 0, 10), head=(8, 0, 0), body=dict(pos=(0, -0.6, 1.0)),
                         right_leg=(0, 0, 0, 8), left_leg=(0, 0, 0, 8))
            p = p(right_arm=(-112, -24, 8, 128), left_arm=(-116, 26, -8, 132))
            out.append(_loop("stance_%s_defensive" % name, p, period, dict(torso=(1, 0, 0, 1), right_arm=(-2, 0, 0, 3), left_arm=(-2, 0, 0, 3))))
        elif kind == "low":
            p = pose.add(torso=(22, 0, 0, 12), body=dict(pos=(0, -4.5, 0)),
                         right_leg=(-12, 0, 0, 42), left_leg=(-12, 0, 0, 46))
            p = p.add(**{lead_arm: (14, 0, 0, -10), rear_arm: (10, 0, 0, 0)})
            out.append(_loop("stance_%s_low" % name, p, period, dict(torso=(2, 0, 0, 2), body=dict(pos=(0, -0.3, 0)))))
        elif kind == "air":
            p = pose.add(torso=(-8, 0, 0, -2), right_arm=(10, 0, 22, -20), left_arm=(10, 0, -22, -20),
                         right_leg=(-30, 0, 0, 60), left_leg=(-10, 0, 0, 40))
            out.append(_loop("stance_%s_air" % name, p, period, breathe))
    return out


def _loop(name, pose, period, breathe):
    a = Anim(name, period, loop=True)
    mid = pose.add(**breathe)
    a.key(0, pose, "catmullrom").key(period / 2, full(mid, pose), "catmullrom").key(period, pose, "catmullrom")
    return a

"""stylekit.py: building blocks for authoring a whole fighting style (moves, style JSON, animations).

Used by gen_styles.py (the styles wave, docs/COMBAT_DESIGN.md "Fighting styles"). Every attack is a
motion.snap() (quick wind-up, snapped and held strike on the hit tick, short recovery), timed from the
move's own windup / active / recovery, exaggerated for the blocky model. The pose builders below give
the common strike shapes (punches, palms, hooks, elbows, knees, kicks, sweeps, spins) for any stance;
a style passes its own stance and a "flavour" (a lean, a sway) so the same shape reads differently.

Conventions are motion.py's (degrees, pixels): limbs x negative = forward/up, y positive = swing to the
model's right, z positive = toward the model's right; torso y positive = turn right (left shoulder
forward), z positive = lean left; body pos = (left, up, back).
"""
import json
import os

from motion import Anim, P, full, snap, hold_for

ROOT = "src/main/resources/data/noirm/combat"


def N(x):
    return x if ":" in x else "noirm:" + x


def H(damage, posture, rng, arc, kb, hitstun, hitstop, shake, **extra):
    h = {"damage": damage, "posture": posture, "range": rng, "arc": arc, "knockback": kb,
         "hitstun": hitstun, "hitstop": hitstop, "shake": shake}
    h.update(extra)
    return h


def sgn(side):
    """+1 for the left limb, -1 for the right one."""
    return 1 if side == "left" else -1


def other(side):
    return "right" if side == "left" else "left"


def bone(pose, name):
    return pose.bones.get(name, {}).get("rot", (0.0, 0.0, 0.0))


# ------------------------------------------------------------------ pose builders: (windup, contact)

def punch(b, side, twist=34, lean=14, height=-90, drive=-2.5, chamber=110, low=0.0):
    """A straight punch or palm with `side`'s arm: the shoulder drives forward, the arm snaps straight."""
    s = sgn(side)
    arm = side + "_arm"
    t = bone(b, "torso")
    wind = b(**{"torso": (t[0] + 4, -s * twist * 0.4, t[2], 8), arm: (-50, s * 10, -s * 8, chamber)})
    hit = b(**{"torso": (lean, s * twist, t[2] * 0.5, 4), arm: (height, -s * twist * 0.9, 0, 0)})
    hit = hit.add(body=dict(pos=(0, -low, drive)))
    return wind, hit


def double_palm(b, height=-92, lean=18, drive=-4, spread=8):
    t = bone(b, "torso")
    wind = b(torso=(t[0] - 10, 0, 0, -2), right_arm=(-25, 15, 25, 115), left_arm=(-25, -15, -25, 115)).add(body=dict(pos=(0, -1, 1)))
    hit = b(torso=(lean, 0, 0, 8), right_arm=(height, spread, 0, 0), left_arm=(height, -spread, 0, 0)).add(body=dict(pos=(0, -1.5, drive)))
    return wind, hit


def hook(b, side, twist=36, lean=14, height=-90):
    s = sgn(side)
    arm = side + "_arm"
    t = bone(b, "torso")
    wind = b(**{"torso": (t[0], -s * twist * 1.1, t[2], 8), arm: (-55, -s * 5, -s * 25, 120)})
    hit = b(**{"torso": (lean, s * twist, t[2] * 0.4, 6), arm: (height, s * 62, 0, 92)}).add(body=dict(pos=(0, 0, -1)))
    return wind, hit


def uppercut(b, side, twist=22):
    s = sgn(side)
    arm = side + "_arm"
    wind = b(**{"torso": (28, -s * twist * 0.7, 0, 12), arm: (-5, s * 10, -s * 10, 115)}).add(body=dict(pos=(0, -2.5, 0)))
    hit = b(**{"torso": (-14, s * twist, 0, -4), arm: (-168, s * 18, 0, 50)}).add(body=dict(pos=(0, 2.2, -1)))
    return wind, hit


def elbow(b, side, twist=40, lean=20):
    s = sgn(side)
    arm = side + "_arm"
    wind = b(**{"torso": (lean - 4, -s * twist, 0, 14), arm: (-90, -s * 30, 0, 150)})
    hit = b(**{"torso": (lean, s * twist, 0, 14), arm: (-92, s * 70, 0, 150)}).add(body=dict(pos=(0, 0, -2)))
    return wind, hit


def hammer(b, lean=34):
    wind = b(torso=(-12, 0, 0, -6), right_arm=(-172, -12, 0, 45), left_arm=(-172, 12, 0, 45)).add(body=dict(pos=(0, 0.6, 0)))
    hit = b(torso=(lean, 0, 0, 18), right_arm=(-62, -14, 0, 18), left_arm=(-62, 14, 0, 18)).add(body=dict(pos=(0, -1.5, -2)))
    return wind, hit


def shoulder(b, side, lean=26, drive=-6):
    """A shoulder or body charge: turned side-on, arms tucked, the whole body driving through."""
    s = sgn(side)
    wind = b(torso=(10, -s * 30, 0, 8), right_arm=(-60, -20, 10, 120), left_arm=(-60, 20, -10, 120)).add(body=dict(pos=(0, -1, 1)))
    hit = b(torso=(lean, s * 70, 0, 16), right_arm=(-40, -10, 20, 130), left_arm=(-40, 10, -20, 130)).add(body=dict(pos=(0, -1.5, drive)))
    return wind, hit


def headbutt(b):
    wind = b(torso=(-22, 0, 0, -8), head=(-25, 0, 0), right_arm=(-95, -15, 10, 45), left_arm=(-95, 15, -10, 45)).add(body=dict(pos=(0, 0, 1)))
    hit = b(torso=(38, 0, 0, 24), head=(35, 0, 0), right_arm=(-70, -25, 10, 85), left_arm=(-70, 25, -10, 85)).add(body=dict(pos=(0, -1, -3)))
    return wind, hit


def grab(b):
    wind = b(torso=(8, 0, 0, 6), right_arm=(-70, -30, 20, 90), left_arm=(-70, 30, -20, 90))
    hit = b(torso=(22, 0, 0, 8), right_arm=(-104, -28, 0, 0), left_arm=(-104, 28, 0, 0)).add(body=dict(pos=(0, -1, -2.5)))
    return wind, hit


def knee(b, side, height=-112):
    s = sgn(side)
    leg, sup = side + "_leg", other(side) + "_leg"
    wind = b(**{"torso": (4, 0, 0, 6), leg: (25, 0, 0, 60), "right_arm": (-95, -20, 10, 70), "left_arm": (-95, 20, -10, 70)}).add(
        body=dict(pos=(0, -1, 1)))
    hit = b(**{"torso": (-12, 0, 0, 4), leg: (height, 0, 0, 128), sup: (-4, 0, s * 4, 6),
               "right_arm": (-60, -25, 10, 95), "left_arm": (-60, 25, -10, 95)}).add(body=dict(pos=(0, 2, -2)))
    return wind, hit


def front_kick(b, side, height=90, lean=-16):
    """A snap / push kick: chamber the knee, then the leg shoots straight out."""
    s = sgn(side)
    leg, sup = side + "_leg", other(side) + "_leg"
    wind = b(**{"torso": (-4, 0, 0, 2), leg: (-100, 0, 0, 125), sup: (-6, 0, s * 4, 20)})
    hit = b(**{"torso": (lean, 0, 0, -4), leg: (-height, 0, 0, 0), sup: (-4, 0, s * 4, 18)}).add(body=dict(pos=(0, 0.5, -1.5)))
    return wind, hit


def roundhouse(b, side, height=90, turn=95, tilt=30):
    """A turning kick: the hips turn over, the leg whips round flat, the body leans away."""
    s = sgn(side)
    leg, sup = side + "_leg", other(side) + "_leg"
    out = -s
    wind = b(**{"torso": (-4, s * 10, -out * 8, 0), leg: (-60, 0, out * 40, 110), sup: (-6, 0, -out * 4, 30)}).add(
        body=dict(rot=(0, s * 35, 0), pos=(0, 0.5, 0)))
    hit = b(**{"torso": (-12, 0, -out * tilt, 0), leg: (-12, 0, out * height, 0), sup: (-4, 0, -out * 10, 26),
               "right_arm": (30, 0, 50, 30), "left_arm": (-40, 0, -55, 40)}).add(body=dict(rot=(0, s * turn, 0), pos=(0, 1.2, -1)))
    return wind, hit


def side_kick(b, side, height=86):
    s = sgn(side)
    leg, sup = side + "_leg", other(side) + "_leg"
    out = -s
    wind = b(**{"torso": (0, s * 20, 0, 4), leg: (-80, 0, out * 30, 130), sup: (-6, 0, -out * 4, 34)}).add(body=dict(rot=(0, s * 50, 0)))
    hit = b(**{"torso": (-6, 0, -out * 42, 0), leg: (-4, 0, out * height, 0), sup: (-4, 0, -out * 8, 24),
               "right_arm": (-60, 0, 40, 60), "left_arm": (-60, 0, -40, 60)}).add(body=dict(rot=(0, s * 90, 0), pos=(0, 0.6, -1)))
    return wind, hit


def axe_kick(b, side):
    s = sgn(side)
    leg, sup = side + "_leg", other(side) + "_leg"
    wind = b(**{"torso": (-20, 10 * s, 0, -6), leg: (-168, 0, -s * 6, 0), sup: (-4, 0, s * 4, 22),
                "right_arm": (-20, 0, 40, 30), "left_arm": (-40, 0, -40, 40)}).add(body=dict(pos=(0, 1, 0.5)))
    hit = b(**{"torso": (34, 0, 0, 16), leg: (-42, 0, -s * 4, 0), sup: (-6, 0, s * 6, 46),
               "right_arm": (10, 0, 30, 30), "left_arm": (-50, 0, -30, 60)}).add(body=dict(pos=(0, -2.5, -2.5)))
    return wind, hit


def crescent(b, side):
    s = sgn(side)
    leg, sup = side + "_leg", other(side) + "_leg"
    out = -s
    wind = b(**{"torso": (-6, -s * 20, 0, 0), leg: (-120, 0, out * 45, 10), sup: (-4, 0, -out * 6, 26)})
    hit = b(**{"torso": (-14, s * 30, -out * 12, 0), leg: (-115, 0, -out * 25, 0), sup: (-4, 0, -out * 6, 24),
               "right_arm": (10, 0, 45, 30), "left_arm": (10, 0, -45, 30)}).add(body=dict(rot=(0, s * 20, 0), pos=(0, 0.8, -1)))
    return wind, hit


def low_sweep(b, side, depth=4.5):
    s = sgn(side)
    leg, sup = side + "_leg", other(side) + "_leg"
    out = -s
    wind = b(**{"torso": (28, s * 20, 0, 14), leg: (5, 0, out * 40, 10), sup: (-55, 0, -out * 8, 105),
                "right_arm": (-20, 0, 25, 60), "left_arm": (-40, 0, -20, 80)}).add(body=dict(pos=(0, -depth, 0)))
    hit = b(**{"torso": (30, -s * 25, 0, 14), leg: (-58, 0, -out * 22, 5), sup: (-55, 0, -out * 8, 105),
               "right_arm": (10, 0, 30, 40), "left_arm": (-40, 0, -25, 80)}).add(body=dict(pos=(0, -depth, -1)))
    return wind, hit


def back_kick(b, side):
    leg, sup = side + "_leg", other(side) + "_leg"
    wind = b(**{"torso": (20, 0, 0, 8), leg: (20, 0, 0, 80)}).add(body=dict(rot=(0, 90, 0)))
    hit = b(**{"torso": (42, 0, 0, 10), "right_arm": (-30, 0, 30, 40), leg: (80, 0, 0, 0), sup: (-8, 0, 0, 24)}).add(
        body=dict(rot=(0, 180, 0), pos=(0, 0, -1)))
    return wind, hit


# ------------------------------------------------------------------ the style writer

class Style:
    """One style: its stance (and sub-stances), moves, and its style JSON.

    base: the neutral stance pose; lead: "left" / "right" (the forward hand, for the sub-stances);
    flavour: a function applied to every wind-up and strike pose (a sway, a lean); kinds: the
    sub-stances it has (aggressive, defensive, low, air)."""

    def __init__(self, key, base, period, breathe, kinds, lead="left", flavour=None, weapons=None):
        self.key = key
        self.base = base
        self.period = period
        self.breathe = breathe
        self.kinds = kinds
        self.lead = lead
        self.flavour = flavour or (lambda p: p)
        self.weapons = weapons or []
        self.anims = []
        self.moves = {}

    def anim_name(self, name):
        return "%s_%s" % (self.key, name)

    def move(self, name, timing, hit, poses=None, nxt=None, variants=None, effects=None, anim=None, settle=None,
             end=None, weight=True, amp=1.0, start=None):
        """A move `<key>/<name>`: its JSON and (from `poses` = (windup, contact)) its snapped animation,
        or `anim` (a function (name, timing) -> Anim) for spins and flips."""
        path = "%s/%s" % (self.key, name)
        w, a, r, c = timing
        aname = self.anim_name(name)
        if anim is not None:
            self.anims.append(anim(aname, (w, a, r)))
        else:
            wind, contact = poses
            stance = start if start is not None else self.base
            self.anims.append(snap(aname, stance, self.flavour(wind), self.flavour(contact), timing=(w, a, r),
                                   settle=settle, end=end, weight=weight, amp=amp))
        d = {"animation": N(aname), "timing": {"windup": w, "active": a, "recovery": r, "cancel_after": c}, "hit": hit}
        if nxt:
            d["next"] = {k: N(v if "/" in v else "%s/%s" % (self.key, v)) for k, v in nxt.items()}
        if variants:
            d["variants"] = {k: N(v if "/" in v else "%s/%s" % (self.key, v)) for k, v in variants.items()}
        if effects:
            d["effects"] = effects
        self.moves[path] = d
        return path

    def stance_anims(self, stance_set):
        return stance_set(self.key, self.base, self.period, self.breathe, self.kinds, self.lead)

    def write(self, openers, counter, identity, learn, npc_weight=1, npc=None, taijutsu=0, extra=None):
        for path, d in self.moves.items():
            f = os.path.join(ROOT, "moves", path + ".json")
            os.makedirs(os.path.dirname(f), exist_ok=True)
            with open(f, "w") as fh:
                fh.write(json.dumps(d, indent=2) + "\n")
        stances = {"neutral": {"animation": N("stance_" + self.key), "switch": 2}}
        for kind in self.kinds:
            stances[kind] = {"animation": N("stance_%s_%s" % (self.key, kind)), "switch": 2}
        s = {"openers": {k: N(v if "/" in v else "%s/%s" % (self.key, v)) for k, v in openers.items()}}
        if taijutsu:
            s["taijutsu"] = taijutsu
        s["npc_weight"] = npc_weight
        s["stances"] = stances
        s["counter"] = N(counter if "/" in counter else "%s/%s" % (self.key, counter))
        s["identity"] = identity
        s["learn"] = learn
        if self.weapons:
            s["weapons"] = self.weapons
        if npc:
            s["npc"] = npc
        if extra:
            s.update(extra)
        with open(os.path.join(ROOT, "styles", self.key + ".json"), "w") as fh:
            fh.write(json.dumps(s, indent=2) + "\n")


# ------------------------------------------------------------------ custom shapes (spins, flips, leaps)

def spin_strike(stance, strike_pose, wind_pose, turn=-360, lift=0.0, mid_turn=0.55, flip=False):
    """An animation builder for a spinning (or flipping, flip=True) strike: winds up, turns through
    the strike (snapped and held on the hit tick), and lands back in stance after a full turn."""
    def build(name, t):
        w, a, r = t
        total = w + a + r
        h = hold_for(a)
        axis = 0 if flip else 1

        def rot(v):
            r3 = [0.0, 0.0, 0.0]
            r3[axis] = v
            return tuple(r3)
        anim = Anim(name, total, timing=t)
        hit = full(strike_pose.add(body=dict(rot=rot(turn * mid_turn), pos=(0, lift, -1))), stance)
        anim.key(0, full(stance, hit))
        if w >= 1.5:
            anim.key(max(0.6, w * 0.5), full(wind_pose.add(body=dict(rot=rot(turn * 0.12), pos=(0, lift * 0.3, 0))), stance), "easeOutCubic")
        anim.snapkey(w, hit, hold=h)
        t2 = w + h + max(1.0, (total - w - h) * 0.45)
        if t2 < total - 0.5:
            anim.key(t2, full(strike_pose.add(body=dict(rot=rot(turn * 0.88), pos=(0, lift * 0.4, -0.5))), stance), "easeOutQuad")
        anim.key(total, full(stance.add(body=dict(rot=rot(turn))), hit), "easeInOutSine")
        return anim
    return build


def leap_strike(stance, crouch, strike_pose, rise=7.0, drive=-10.0):
    """A leaping strike (flying knee, dive kick): crouch, launch into the snapped strike high up, land."""
    def build(name, t):
        w, a, r = t
        total = w + a + r
        h = hold_for(a)
        anim = Anim(name, total, timing=t)
        hit = full(strike_pose.add(body=dict(pos=(0, rise, drive))), stance)
        anim.key(0, full(stance, hit))
        if w >= 1.5:
            anim.key(max(0.6, w * 0.55), full(crouch.add(body=dict(pos=(0, -3, 0.5))), stance), "easeOutCubic")
        anim.snapkey(w, hit, hold=h)
        t2 = w + h + max(1.0, (total - w - h) * 0.5)
        if t2 < total - 0.5:
            anim.key(t2, full(crouch.add(body=dict(pos=(0, -2, drive * 0.6))), stance), "easeInQuad")
        anim.key(total, full(stance, hit), "easeInOutSine")
        return anim
    return build

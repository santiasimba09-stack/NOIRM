#!/usr/bin/env python3
"""Animations for the v0.13 blades (tanto, katana, nodachi, tonfa, yari, tekko-kagi, tessen):
their stances and one animation per move, timed from data/noirm/combat/moves/<moveset>/*.json so
each contact key lands on the server's hit tick. Run from the repo root, after gen_moves.py:

    python3 tools/weapongen/gen_blade_anims.py

Uses tools/animgen/motion.py (see its conventions). Kept apart from tools/animgen/gen_anims.py so the
weapon work doesn't touch the shared animation script.
"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "animgen"))
from motion import Anim, P, mirror, strike  # noqa: E402

MOVES = "src/main/resources/data/noirm/combat/moves"
ANIMS = []


def timing(move):
    t = json.load(open(os.path.join(MOVES, move + ".json")))["timing"]
    return t["windup"], t["active"], t["recovery"]


def add(anim):
    ANIMS.append(anim)
    return anim


def stance(name, pose, period, breathe):
    a = Anim(name, period, loop=True)
    a.key(0, pose, "catmullrom").key(period / 2, pose.add(**breathe), "catmullrom").key(period, pose, "catmullrom")
    return add(a)


LEGS_R = dict(right_leg=(-20, 0, 4, 30), left_leg=(18, 0, -10, 24))
LEGS_L = dict(left_leg=(-20, 0, -4, 30), right_leg=(18, 0, 10, 24))
WIDE = dict(left_leg=(-26, 0, -10, 40), right_leg=(22, 0, 14, 32))
BREATHE = dict(torso=(2, 0, 0, 2), right_arm=(-3, 0, 0, 3), left_arm=(-2, 0, 0, 3))

# ------------------------------------------------------------------ stances
TANTO = P(torso=(16, -20, 0, 12), right_arm=(-40, 30, 0, 95), left_arm=(-75, 10, -5, 105), body=dict(pos=(0, -1.2, 0)), **LEGS_R)
KATANA = P(torso=(6, -10, 0, 4), right_arm=(-55, 15, 0, 25), left_arm=(-60, 40, 0, 40), **LEGS_R)          # both hands, mid guard
GREAT = P(torso=(8, 20, 0, 6), right_arm=(-35, 35, 10, 30), left_arm=(-40, 55, -5, 45), body=dict(pos=(0, -0.8, 0)), **WIDE)
TONFA = P(torso=(12, 10, 0, 10), right_arm=(-70, -15, 8, 120), left_arm=(-80, 15, -8, 125), **LEGS_L)      # forearms up like a boxer
SPEAR = P(torso=(10, 30, 0, 6), right_arm=(-25, 10, 5, 40), left_arm=(-60, 40, -5, 25), body=dict(pos=(0, -1, 0)), **LEGS_L)
CLAWS = P(torso=(22, 0, 0, 16), right_arm=(-60, -20, 10, 70), left_arm=(-60, 20, -10, 70), body=dict(pos=(0, -1.8, 0)), **WIDE)
FAN = P(torso=(4, -25, 0, 2), right_arm=(-75, 35, 0, 40), left_arm=(-20, 0, -15, 40), **LEGS_R)

stance("stance_tanto", TANTO, 20, BREATHE)
stance("stance_katana", KATANA, 32, BREATHE)
stance("stance_greatsword", GREAT, 40, dict(torso=(2, 0, 0, 2), body=dict(pos=(0, -0.3, 0))))
stance("stance_tonfa", TONFA, 14, dict(torso=(3, 0, 0, 3), right_arm=(4, 0, 0, -5), left_arm=(4, 0, 0, -5), body=dict(pos=(0, -0.8, 0))))
stance("stance_spear", SPEAR, 30, BREATHE)
stance("stance_claws", CLAWS, 18, dict(torso=(3, 0, 0, 3), right_arm=(-4, 0, 0, 6), left_arm=(-4, 0, 0, 6)))
stance("stance_fan", FAN, 36, BREATHE)


def two(pose, right):
    """Both hands on one grip: the left arm follows the right, a little inward and more bent."""
    x, y, z, b = right
    return pose(right_arm=right, left_arm=(x + 5, y + 28, z - 8, b + 25))


def cut(name, move, base, wind_r, hit_r, torso_w, torso_h, two_hand=False, lunge=-2.5, sweep=12, legs=None, **kw):
    """A slash: the arm goes from wind_r to hit_r (x, y, z, bend) while the torso twists."""
    t = timing(move)
    mk = (lambda p, r: two(p, r)) if two_hand else (lambda p, r: p(right_arm=r))
    extra = legs or {}
    windup = mk(base(torso=torso_w), wind_r)
    contact = mk(base(torso=torso_h, body=dict(pos=(0, -0.5, lunge)), **extra), hit_r)
    fx, fy, fz, fb = hit_r
    follow = mk(base(torso=(torso_h[0] + 2, torso_h[1] + (sweep if torso_h[1] > torso_w[1] else -sweep), 0, torso_h[3]),
                     body=dict(pos=(0, -0.5, lunge)), **extra), (fx, fy + (sweep if fy > wind_r[1] else -sweep), fz, fb))
    return add(strike(name, base, windup, contact, follow, t, **kw))


def thrust(name, move, base, two_hand=False, depth=-6, arm=(-90, 10, 0, 0), wind=(-30, 20, 15, 115), **kw):
    t = timing(move)
    mk = (lambda p, r: two(p, r)) if two_hand else (lambda p, r: p(right_arm=r))
    lunge_legs = dict(right_leg=(-42, 0, 4, 40), left_leg=(32, 0, -8, 10))
    windup = mk(base(torso=(2, 25, 0, 2), body=dict(pos=(0, -1, 1.5))), wind)
    contact = mk(base(torso=(18, -25, 0, 6), body=dict(pos=(0, -2, depth)), **lunge_legs), arm)
    follow = mk(base(torso=(18, -22, 0, 6), body=dict(pos=(0, -2, depth)), **lunge_legs), (arm[0] + 3, arm[1], arm[2], arm[3] + 4))
    return add(strike(name, base, windup, contact, follow, t, **kw))


def overhead(name, move, base, two_hand=True, knee=True, **kw):
    t = timing(move)
    mk = (lambda p, r: two(p, r)) if two_hand else (lambda p, r: p(right_arm=r))
    legs = dict(left_leg=(-34, 0, -4, 46), right_leg=(26, 0, 10, 30)) if knee else {}
    windup = mk(base(torso=(-16, 8, 0, -6), body=dict(pos=(0, 1, 1))), (-172, 5, 10, 20))
    contact = mk(base(torso=(30, 0, 0, 16), body=dict(pos=(0, -2.5, -3)), **legs), (-68, 0, 0, 5))
    follow = mk(base(torso=(33, 0, 0, 16), body=dict(pos=(0, -2.5, -3)), **legs), (-55, 0, 0, 5))
    return add(strike(name, base, windup, contact, follow, t, antic_at=0.6, **kw))


def rising(name, move, base, two_hand=False):
    t = timing(move)
    mk = (lambda p, r: two(p, r)) if two_hand else (lambda p, r: p(right_arm=r))
    windup = mk(base(torso=(28, 20, 0, 14), body=dict(pos=(0, -3, 0))), (15, 20, 25, 20))
    contact = mk(base(torso=(-14, -20, 0, -4), body=dict(pos=(0, 4, -2))), (-172, -10, 0, 5))
    follow = mk(base(torso=(-16, -24, 0, -4), body=dict(pos=(0, 4.5, -2))), (-180, -10, 0, 8))
    return add(strike(name, base, windup, contact, follow, t, antic_at=0.6))


def spin(name, move, base, arms=((-85, 0, 30, 0), (-85, 0, -30, 0))):
    w, a, r = timing(move)
    sp = Anim(name, w + a + r, timing=(w, a, r))
    sp.key(0, base)
    sp.key(w * 0.6, base(torso=(8, 40, 0, 6), right_arm=(-80, 50, 0, 10), left_arm=(-80, 60, 0, 20), body=dict(rot=(0, 30, 0))), "easeOutQuad")
    sp.key(w, base(torso=(8, 0, 0, 4), right_arm=arms[0], left_arm=arms[1], body=dict(rot=(0, -150, 0), pos=(0, 1, 0))), "easeInQuad")
    sp.key(w + a, base(torso=(8, 0, 0, 4), right_arm=arms[0], left_arm=arms[1], body=dict(rot=(0, -330, 0), pos=(0, 1, 0))), "linear")
    sp.key(w + a + r, base(body=dict(rot=(0, -360, 0))), "easeOutQuad")
    return add(sp)


def left(name, move, base, wind_l, hit_l, torso_w, torso_h, lunge=-2):
    """A strike with the left hand (tonfa, claws): authored as a right-hand strike, then mirrored."""
    t = timing(move)
    mb = mirror(base)
    windup = mirror(mb(torso=torso_w, right_arm=wind_l))
    contact = mirror(mb(torso=torso_h, right_arm=hit_l, body=dict(pos=(0, -0.5, lunge))))
    follow = mirror(mb(torso=(torso_h[0] + 2, torso_h[1], 0, torso_h[3]), right_arm=(hit_l[0] + 3, hit_l[1], hit_l[2], hit_l[3] + 5),
                       body=dict(pos=(0, -0.5, lunge))))
    return add(strike(name, base, windup, contact, follow, t))


# ------------------------------------------------------------------ tanto: low, fast, close
cut("tanto_slash", "tanto/slash", TANTO, (-70, 60, 0, 50), (-88, -55, 0, 20), (12, 20, 0, 10), (18, -40, 0, 10))
cut("tanto_cut_2", "tanto/cut_2", TANTO, (-85, -60, 0, 60), (-90, 55, 0, 15), (16, -40, 0, 10), (16, 28, 0, 8))
cut("tanto_cut_3", "tanto/cut_3", TANTO, (-120, 40, 10, 60), (-60, -40, 0, 15), (6, 25, 0, 6), (22, -30, 0, 12))
spin("tanto_flurry", "tanto/flurry", TANTO, arms=((-88, 0, 35, 10), (-70, 0, -25, 60)))
thrust("tanto_stab", "tanto/stab", TANTO, depth=-6.5, arm=(-94, 25, 0, 0))
rising("tanto_rising", "tanto/rising", TANTO)
thrust("tanto_shadow_step", "tanto/shadow_step", TANTO, depth=-9, arm=(-94, 25, 0, 0))

# ------------------------------------------------------------------ katana: two hands, big clean cuts
cut("katana_slash", "katana/slash", KATANA, (-160, 35, 20, 20), (-55, -45, 0, 8), (-4, 20, 0, -2), (20, -35, 0, 10), two_hand=True, lunge=-3,
    legs=dict(right_leg=(-32, 0, 4, 34)))
cut("katana_cut_2", "katana/cut_2", KATANA, (-35, -55, 0, 25), (-150, 45, 0, 10), (14, -45, 0, 8), (-6, 30, 0, -2), two_hand=True, lunge=-2)
overhead("katana_finisher", "katana/finisher", KATANA)
# iaido: the blade low at the hip, then one explosive horizontal draw
t = timing("katana/iaido")
add(strike("katana_iaido", KATANA,
           KATANA(torso=(14, 45, 0, 10), right_arm=(10, 40, 20, 30), left_arm=(5, 30, -10, 60), body=dict(pos=(0, -2, 1))),
           KATANA(torso=(18, -50, 0, 8), right_arm=(-88, -70, 0, 0), left_arm=(-20, 20, -25, 30), right_leg=(-44, 0, 4, 40),
                  left_leg=(32, 0, -8, 12), body=dict(pos=(0, -2, -7))),
           KATANA(torso=(18, -58, 0, 8), right_arm=(-84, -82, 0, 4), left_arm=(-20, 20, -25, 30), right_leg=(-44, 0, 4, 40),
                  left_leg=(32, 0, -8, 12), body=dict(pos=(0, -2, -7))),
           t, antic_at=0.7, ease_in="easeInQuart"))
thrust("katana_thrust", "katana/thrust", KATANA, two_hand=True, depth=-7, arm=(-88, 8, 0, 0), wind=(-45, 20, 10, 90))
rising("katana_rising", "katana/rising", KATANA, two_hand=True)
cut("katana_dash_cut", "katana/dash_cut", KATANA, (-80, 60, 0, 20), (-85, -65, 0, 5), (10, 40, 0, 6), (18, -45, 0, 8), lunge=-10,
    legs=dict(right_leg=(-44, 0, 4, 40), left_leg=(32, 0, -8, 10)), ease_in="easeInQuart")

# ------------------------------------------------------------------ nodachi: heavy, wide, whole body
cut("greatsword_sweep", "greatsword/sweep", GREAT, (-60, 75, 10, 20), (-75, -70, 0, 5), (8, 60, 0, 6), (16, -55, 0, 8), two_hand=True,
    lunge=-2, sweep=18, antic_at=0.5)
cut("greatsword_return", "greatsword/return_sweep", GREAT, (-70, -75, 0, 15), (-70, 70, 0, 5), (12, -55, 0, 8), (14, 55, 0, 6), two_hand=True,
    lunge=-2, sweep=18, antic_at=0.5)
spin("greatsword_spin", "greatsword/spin", GREAT, arms=((-80, 0, 30, 5), (-78, 25, -15, 30)))
overhead("greatsword_cleave", "greatsword/cleave", GREAT)
rising("greatsword_rising", "greatsword/rising", GREAT, two_hand=True)
cut("greatsword_charge", "greatsword/charge", GREAT, (-150, 40, 20, 20), (-60, -40, 0, 5), (-6, 25, 0, -2), (24, -30, 0, 12), two_hand=True,
    lunge=-9, legs=dict(right_leg=(-44, 0, 4, 40), left_leg=(32, 0, -8, 10)))

# ------------------------------------------------------------------ tonfa: boxer's rhythm, both arms
left("tonfa_jab_l", "tonfa/jab_l", TONFA, (-70, 10, 0, 120), (-92, 0, 0, 5), (10, 15, 0, 8), (14, -20, 0, 8))
cut("tonfa_jab_r", "tonfa/jab_r", TONFA, (-70, -10, 0, 120), (-92, 0, 0, 5), (10, -15, 0, 8), (14, 22, 0, 8), sweep=0)
left("tonfa_hook_l", "tonfa/hook_l", TONFA, (-80, -30, 0, 100), (-88, 55, 0, 70), (10, 35, 0, 8), (16, -35, 0, 10))
t = timing("tonfa/hammer")
add(strike("tonfa_hammer", TONFA,
           TONFA(torso=(-12, 0, 0, -6), right_arm=(-165, -10, 10, 60), left_arm=(-165, 10, -10, 60), body=dict(pos=(0, 1, 1))),
           TONFA(torso=(28, 0, 0, 14), right_arm=(-70, -5, 0, 40), left_arm=(-70, 5, 0, 40), left_leg=(-34, 0, -4, 46), body=dict(pos=(0, -2.5, -3))),
           TONFA(torso=(30, 0, 0, 14), right_arm=(-60, -5, 0, 40), left_arm=(-60, 5, 0, 40), left_leg=(-34, 0, -4, 46), body=dict(pos=(0, -2.5, -3))),
           t, antic_at=0.6))
spin("tonfa_spin_strike", "tonfa/spin_strike", TONFA, arms=((-88, 0, 40, 30), (-80, 15, -10, 110)))
rising("tonfa_uppercut", "tonfa/uppercut", TONFA)
thrust("tonfa_rush", "tonfa/rush", TONFA, depth=-9, arm=(-92, 0, 0, 5), wind=(-70, -10, 0, 120))

# ------------------------------------------------------------------ spear: long, straight lines
thrust("spear_thrust", "spear/thrust", SPEAR, two_hand=True, depth=-5, arm=(-85, 5, 0, 0), wind=(-30, 25, 10, 70))
thrust("spear_thrust_2", "spear/thrust_2", SPEAR, two_hand=True, depth=-5, arm=(-80, -5, 0, 0), wind=(-25, 35, 10, 70))
thrust("spear_lunge_thrust", "spear/lunge_thrust", SPEAR, two_hand=True, depth=-9, arm=(-88, 0, 0, 0), wind=(-15, 40, 15, 80), antic_at=0.6)
cut("spear_sweep", "spear/sweep", SPEAR, (-50, 70, 10, 20), (-75, -60, 0, 10), (8, 55, 0, 6), (14, -50, 0, 6), two_hand=True, sweep=15)
rising("spear_vault", "spear/vault", SPEAR, two_hand=True)
thrust("spear_charge", "spear/charge", SPEAR, two_hand=True, depth=-11, arm=(-86, 0, 0, 0), wind=(-25, 30, 10, 70))

# ------------------------------------------------------------------ claws: low and feral, both hands
cut("claws_rake", "claws/rake", CLAWS, (-130, 40, 15, 40), (-45, -35, 0, 20), (14, 20, 0, 12), (26, -25, 0, 16))
left("claws_rake_back", "claws/rake_back", CLAWS, (-130, 40, 15, 40), (-45, -35, 0, 20), (14, 20, 0, 12), (26, -25, 0, 16))
cut("claws_rake_3", "claws/rake_3", CLAWS, (-60, 70, 0, 50), (-80, -60, 0, 15), (16, 35, 0, 12), (24, -40, 0, 14))
t = timing("claws/shred")
add(strike("claws_shred", CLAWS,
           CLAWS(torso=(-8, 0, 0, -4), right_arm=(-165, 20, 20, 30), left_arm=(-165, -20, -20, 30), body=dict(pos=(0, 0.5, 1))),
           CLAWS(torso=(32, 0, 0, 18), right_arm=(-40, -25, 0, 15), left_arm=(-40, 25, 0, 15), body=dict(pos=(0, -3, -4))),
           CLAWS(torso=(34, 0, 0, 18), right_arm=(-25, -30, 0, 15), left_arm=(-25, 30, 0, 15), body=dict(pos=(0, -3, -4))),
           t))
t = timing("claws/maul")
add(strike("claws_maul", CLAWS,
           CLAWS(torso=(4, 0, 0, 2), right_arm=(-150, 45, 10, 40), left_arm=(-150, -45, -10, 40), body=dict(pos=(0, 0, 1))),
           CLAWS(torso=(28, 0, 0, 16), right_arm=(-55, -10, 0, 20), left_arm=(-55, 10, 0, 20), body=dict(pos=(0, -2.5, -4))),
           CLAWS(torso=(30, 0, 0, 16), right_arm=(-40, -15, 0, 20), left_arm=(-40, 15, 0, 20), body=dict(pos=(0, -2.5, -4))),
           t, antic_at=0.6))
rising("claws_rising", "claws/rising", CLAWS)
thrust("claws_pounce", "claws/pounce", CLAWS, depth=-11, arm=(-95, 0, 0, 20), wind=(-40, 20, 10, 100))

# ------------------------------------------------------------------ tessen: elegant snaps and a gust
cut("fan_snap", "fan/snap", FAN, (-75, 60, 0, 60), (-90, -45, 0, 10), (4, 20, 0, 2), (12, -40, 0, 6))
cut("fan_snap_back", "fan/snap_back", FAN, (-90, -50, 0, 70), (-88, 50, 0, 10), (12, -40, 0, 6), (8, 30, 0, 4))
spin("fan_flourish", "fan/flourish", FAN, arms=((-90, 0, 45, 0), (-30, 0, -40, 20)))
t = timing("fan/gust")
add(strike("fan_gust", FAN,
           FAN(torso=(0, 45, 0, 0), right_arm=(-60, 80, 0, 60), left_arm=(-40, 0, -25, 40), body=dict(pos=(0, -1, 1))),
           FAN(torso=(16, -40, 0, 6), right_arm=(-95, -60, 0, 0), left_arm=(10, 0, -30, 20), left_leg=(-30, 0, -4, 30), body=dict(pos=(0, -1, -4))),
           FAN(torso=(18, -50, 0, 6), right_arm=(-95, -75, 0, 0), left_arm=(10, 0, -30, 20), left_leg=(-30, 0, -4, 30), body=dict(pos=(0, -1, -4))),
           t, antic_at=0.6))
rising("fan_rising", "fan/rising", FAN)
cut("fan_glide", "fan/glide", FAN, (-80, 50, 0, 40), (-90, -60, 0, 5), (6, 30, 0, 4), (16, -45, 0, 8), lunge=-9,
    legs=dict(right_leg=(-44, 0, 4, 40), left_leg=(32, 0, -8, 10)))


if __name__ == "__main__":
    for anim in ANIMS:
        anim.write()
    print("wrote", len(ANIMS), "blade animations")

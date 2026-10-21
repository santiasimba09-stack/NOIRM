#!/usr/bin/env python3
"""Generates every NOIRM animation (v2) into src/main/resources/assets/noirm/player_animation/.

Run from the repo root:  python3 tools/animgen/gen_anims.py
See motion.py for the conventions. Every attack is timed from its move file
(data/noirm/combat/moves/**), so its contact key lands on the tick the server's hit does.

Design notes (inspired by how Epic Fight and Better Combat animate, built from our own poses):
  * whole-body motion: the hips turn and the feet step into every strike, the torso carries the
    arms, the head is left to the engine (it keeps looking at the opponent);
  * anticipation -> fast contact (easing accelerates into the hit) -> follow-through -> settle;
  * each style has its own stance and footwork; combos alternate sides instead of repeating.
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from motion import Anim, P, mirror, strike  # noqa: E402

MOVES = "src/main/resources/data/noirm/combat/moves"


def timing_of(move, default=(3, 2, 6)):
    """(windup, active, recovery) of a move file, e.g. 'basic/jab'."""
    path = os.path.join(MOVES, move + ".json")
    if not os.path.exists(path):
        return default
    t = json.load(open(path)).get("timing", {})
    return (t.get("windup", default[0]), t.get("active", default[1]), t.get("recovery", default[2]))


ANIMS = []


def add(anim):
    ANIMS.append(anim)
    return anim


# ------------------------------------------------------------------ stances
# Orthodox footwork: left foot forward, right foot back and out, knees soft.
LEGS_L = dict(left_leg=(-20, 0, -4, 30), right_leg=(18, 0, 10, 24))
LEGS_R = dict(right_leg=(-20, 0, 4, 30), left_leg=(18, 0, -10, 24))

BASIC = P(torso=(10, 22, 0, 6), left_arm=(-62, 0, 0, 100), right_arm=(-45, -25, 5, 125), **LEGS_L)
BRAWLER = P(torso=(16, 12, 0, 14), left_arm=(-80, 12, -6, 118), right_arm=(-76, -22, 8, 128),
            left_leg=(-12, 0, -6, 34), right_leg=(12, 0, 8, 30))
STRONG = P(torso=(-2, -32, 0, 0), right_arm=(-78, 25, 0, 15), left_arm=(45, 0, 22, 75), **LEGS_R)
GENTLE = P(torso=(8, 50, 0, 8), left_arm=(-82, -42, 0, 22), right_arm=(-122, -30, 15, 60),
           left_leg=(-30, 0, -12, 42), right_leg=(28, 0, 14, 38))
KUNAI = P(torso=(12, -18, 0, 8), right_arm=(-55, 25, 0, 70), left_arm=(-70, 15, -5, 95), **LEGS_R)
STAFF = P(torso=(8, 25, 0, 5), right_arm=(-15, 0, 5, 25), left_arm=(-55, 35, -5, 60), **LEGS_L)
SWORD = P(torso=(6, -15, 0, 4), right_arm=(-40, 15, 0, 10), left_arm=(-50, 30, 0, 90), **LEGS_R)


def stance(name, pose, period, breathe, extra=None):
    """A looping stance: the pose, breathing (or bouncing) by `breathe` halfway through."""
    a = Anim(name, period, loop=True)
    mid = pose.add(**breathe)
    if extra:
        mid = mid(**extra)
    a.key(0, pose, "catmullrom").key(period / 2, mid, "catmullrom").key(period, pose, "catmullrom")
    return add(a)


stance("stance_basic", BASIC, 32, dict(torso=(2, 0, 0, 2), right_arm=(-3, 0, 0, 4), left_arm=(-3, 0, 0, -4)))
stance("stance_brawler", BRAWLER.add(body=dict(pos=(0, 0, 0))), 14,
       dict(torso=(3, 0, 0, 3), right_arm=(4, 0, 0, -5), left_arm=(4, 0, 0, -5), left_leg=(0, 0, 0, 10), right_leg=(0, 0, 0, 10),
            body=dict(pos=(0, -0.9, 0))))
stance("stance_strong_fist", STRONG, 36, dict(torso=(-1, 0, 0, 1), right_arm=(-3, 0, 0, 4)))
stance("stance_gentle_fist", GENTLE.add(body=dict(pos=(0, -1.2, 0))), 60,
       dict(torso=(1, 0, 0, 1), left_arm=(-1, 0, 0, 2), body=dict(pos=(0, -0.2, 0))))
stance("stance_kunai", KUNAI, 24, dict(torso=(2, 0, 0, 2), right_arm=(-4, 0, 0, 6), left_arm=(-3, 0, 0, 4)))
stance("stance_staff", STAFF, 30, dict(torso=(2, 0, 0, 2), right_arm=(-3, 0, 0, 3), left_arm=(-2, 0, 0, 3)))
stance("stance_sword", SWORD, 30, dict(torso=(2, 0, 0, 2), right_arm=(-3, 0, 0, 2), left_arm=(-2, 0, 0, 4)))

# ------------------------------------------------------------------ Academy (basic)
t = timing_of("basic/jab")
add(strike("quick_jab", BASIC,
           BASIC(torso=(14, 30, 0, 8), left_arm=(-55, 0, 0, 115)).add(left_leg=(0, 0, 0, 10), right_leg=(0, 0, 0, 10)),
           BASIC(torso=(16, 4, 0, 4), left_arm=(-90, 6, 0, 4), left_leg=(-28, 0, -4, 24), right_leg=(22, 0, 10, 18),
                 body=dict(pos=(0, 0, -2))),
           BASIC(torso=(15, 6, 0, 4), left_arm=(-88, 6, 0, 10), left_leg=(-28, 0, -4, 24), right_leg=(22, 0, 10, 18),
                 body=dict(pos=(0, 0, -2))),
           t))

t = timing_of("basic/hook")
add(strike("turning_hook", BASIC,
           BASIC(torso=(12, 40, 0, 8), right_arm=(-55, -5, 25, 120), right_leg=(16, 0, 12, 30)),
           BASIC(torso=(14, -30, 0, 6), right_arm=(-90, -62, 0, 92), left_arm=(-55, 0, 0, 115),
                 right_leg=(12, 0, 16, 34), left_leg=(-22, 0, -4, 30), body=dict(pos=(0, 0, -1))),
           BASIC(torso=(14, -42, 0, 6), right_arm=(-86, -76, 0, 96), left_arm=(-55, 0, 0, 115),
                 right_leg=(12, 0, 16, 34), left_leg=(-22, 0, -4, 30), body=dict(pos=(0, 0, -1))),
           t))

t = timing_of("basic/spin_kick")
w, a_, r = t
spin = Anim("rotating_kick", sum(t), timing=t)
spin.key(0, BASIC)
spin.key(w * 0.5, BASIC(torso=(-6, 10, 10, 0), right_arm=(-60, -20, 30, 110), left_arm=(-60, 10, -30, 110),
                        right_leg=(-45, 0, 25, 85), left_leg=(-6, 0, -4, 36), body=dict(rot=(0, -35, 0), pos=(0, 0.5, 0))), "easeOutQuad")
spin.key(w, BASIC(torso=(-12, 0, 28, 0), right_arm=(30, 0, 50, 30), left_arm=(-35, 0, -55, 40),
                  right_leg=(-12, 0, 88, 6), left_leg=(-4, 0, -8, 20), body=dict(rot=(0, -95, 0), pos=(0, 1.5, -1))), "easeInCubic")
spin.key(w + a_ + 0.5, BASIC(torso=(-10, 0, 22, 0), right_arm=(30, 0, 45, 30), left_arm=(-35, 0, -50, 40),
                             right_leg=(-12, 0, 76, 20), left_leg=(-4, 0, -8, 24), body=dict(rot=(0, -160, 0), pos=(0, 1, -1))), "easeOutQuad")
spin.key(w + a_ + r * 0.55, BASIC(right_leg=(-10, 0, 20, 70), body=dict(rot=(0, -300, 0), pos=(0, 0.3, 0))), "easeInOutSine")
spin.key(sum(t), BASIC(body=dict(rot=(0, -360, 0), pos=(0, 0, 0))), "easeOutQuad")
add(spin)

t = timing_of("basic/flip_kick")
w, a_, r = t
flip = Anim("aerial_flip_kick", sum(t), timing=t)
crouch = BASIC(torso=(28, 0, 0, 14), right_arm=(40, 0, 15, 30), left_arm=(40, 0, -15, 30),
               right_leg=(-40, 0, 6, 90), left_leg=(-40, 0, -6, 90), body=dict(pos=(0, -3, 0)))
flip.key(0, BASIC)
flip.key(w * 0.6, crouch, "easeOutQuad")
flip.key(w, P(torso=(-20, 0, 0, -5), right_arm=(-160, 0, 20, 10), left_arm=(-160, 0, -20, 10),
              right_leg=(-150, 0, 0, 0), left_leg=(-30, 0, 0, 90), body=dict(rot=(-110, 0, 0), pos=(0, 12, 2))), "easeInQuad")
flip.key(w + a_ + 1, P(torso=(10, 0, 0, 10), right_arm=(-90, 0, 30, 60), left_arm=(-90, 0, -30, 60),
                       right_leg=(-80, 0, 0, 120), left_leg=(-80, 0, 0, 120), body=dict(rot=(-250, 0, 0), pos=(0, 10, 3))), "linear")
flip.key(w + a_ + r * 0.6, crouch(body=dict(rot=(-360, 0, 0), pos=(0, -1.5, 2))), "easeOutQuad")
flip.key(sum(t), BASIC(body=dict(rot=(-360, 0, 0), pos=(0, 0, 0))), "easeInOutSine")
add(flip)

t = timing_of("basic/heavy_kick")
add(strike("counter_kick", BASIC,
           BASIC(torso=(-14, 18, 0, 0), right_leg=(-100, 0, 0, 125), left_leg=(-8, 0, -4, 34),
                 right_arm=(-60, -25, 15, 120), left_arm=(-75, 0, -10, 105), body=dict(pos=(0, 1, 1))),
           BASIC(torso=(-26, 10, 0, -4), right_leg=(-90, 0, 0, 0), left_leg=(-4, 0, -4, 22),
                 right_arm=(35, 0, 25, 40), left_arm=(-70, 0, -20, 60), body=dict(pos=(0, 1, -4))),
           BASIC(torso=(-24, 10, 0, -4), right_leg=(-86, 0, 0, 8), left_leg=(-4, 0, -4, 22),
                 right_arm=(35, 0, 25, 40), left_arm=(-70, 0, -20, 60), body=dict(pos=(0, 1, -4))),
           t, settle=BASIC(right_leg=(-70, 0, 0, 100), body=dict(pos=(0, 0.5, -2)))))

t = timing_of("basic/uppercut")
add(strike("rising_uppercut", BASIC,
           BASIC(torso=(28, 15, 0, 12), right_arm=(-5, -10, 10, 115), left_leg=(-26, 0, -4, 60), right_leg=(16, 0, 10, 55),
                 body=dict(pos=(0, -2.5, 0))),
           BASIC(torso=(-14, -20, 0, -4), right_arm=(-165, -18, 0, 55), left_leg=(-20, 0, -4, 12), right_leg=(10, 0, 8, 6),
                 body=dict(pos=(0, 2, -1))),
           BASIC(torso=(-16, -24, 0, -4), right_arm=(-172, -18, 0, 45), left_leg=(-20, 0, -4, 12), right_leg=(10, 0, 8, 6),
                 body=dict(pos=(0, 2.5, -1))),
           t, antic_at=0.6))

# ------------------------------------------------------------------ Brawler (Muay Thai)
t = timing_of("brawler/elbow")
add(strike("elbow_strike", BRAWLER,
           BRAWLER(torso=(16, 40, 0, 14), right_arm=(-90, 30, 0, 150)),
           BRAWLER(torso=(20, -38, 0, 14), right_arm=(-92, -68, 0, 150), right_leg=(16, 0, 14, 36), body=dict(pos=(0, 0, -2))),
           BRAWLER(torso=(20, -48, 0, 14), right_arm=(-90, -78, 0, 148), right_leg=(16, 0, 14, 36), body=dict(pos=(0, 0, -2))),
           t))

t = timing_of("brawler/back_elbow")
w, a_, r = t
be = Anim("back_elbow", sum(t), timing=t)
be.key(0, BRAWLER)
be.key(w * 0.6, BRAWLER(torso=(14, -20, 0, 12), right_arm=(-80, 20, 0, 150), body=dict(rot=(0, -60, 0))), "easeOutQuad")
be.key(w, BRAWLER(torso=(18, 25, 0, 14), right_arm=(-88, 75, 0, 150), body=dict(rot=(0, -185, 0), pos=(0, 0, -1))), "easeInCubic")
be.key(w + a_ + 0.5, BRAWLER(torso=(18, 30, 0, 14), right_arm=(-86, 80, 0, 150), body=dict(rot=(0, -215, 0), pos=(0, 0, -1))), "easeOutQuad")
be.key(sum(t), BRAWLER(body=dict(rot=(0, -360, 0))), "easeInOutSine")
add(be)

t = timing_of("brawler/rising_knee")
knee_pull = dict(right_arm=(-95, -20, 10, 70), left_arm=(-95, 20, -10, 70))
add(strike("knee_strike", BRAWLER,
           BRAWLER(torso=(4, 0, 0, 6), right_leg=(25, 0, 4, 60), body=dict(pos=(0, -1, 1)), **knee_pull),
           BRAWLER(torso=(-12, 0, 0, 4), right_leg=(-112, 0, 0, 128), left_leg=(-4, 0, -4, 6),
                   right_arm=(-60, -25, 10, 95), left_arm=(-60, 25, -10, 95), body=dict(pos=(0, 2, -2))),
           BRAWLER(torso=(-10, 0, 0, 4), right_leg=(-105, 0, 0, 125), left_leg=(-4, 0, -4, 6),
                   right_arm=(-62, -25, 10, 95), left_arm=(-62, 25, -10, 95), body=dict(pos=(0, 2, -2))),
           t))

t = timing_of("brawler/flying_knee")
add(strike("flying_knee", BRAWLER,
           BRAWLER(torso=(24, 0, 0, 16), right_arm=(30, 0, 15, 30), left_arm=(30, 0, -15, 30),
                   right_leg=(-30, 0, 4, 80), left_leg=(-30, 0, -4, 80), body=dict(pos=(0, -3, 0))),
           P(torso=(-8, 0, 0, 6), right_arm=(40, 0, 25, 20), left_arm=(-75, 0, -10, 85),
             right_leg=(-122, 0, 0, 135), left_leg=(38, 0, 0, 70), body=dict(pos=(0, 9, -7), rot=(-8, 0, 0))),
           P(torso=(-6, 0, 0, 6), right_arm=(40, 0, 25, 20), left_arm=(-75, 0, -10, 85),
             right_leg=(-115, 0, 0, 130), left_leg=(35, 0, 0, 70), body=dict(pos=(0, 7, -8), rot=(-6, 0, 0))),
           t, settle=BRAWLER(right_leg=(-40, 0, 4, 70), body=dict(pos=(0, 1, -4)))))

t = timing_of("brawler/headbutt")
add(strike("headbutt", BRAWLER,
           BRAWLER(torso=(-22, 0, 0, -8), head=(-25, 0, 0), right_arm=(-95, -15, 10, 45), left_arm=(-95, 15, -10, 45),
                   body=dict(pos=(0, 0, 1))),
           BRAWLER(torso=(38, 0, 0, 24), head=(35, 0, 0), right_arm=(-70, -25, 10, 85), left_arm=(-70, 25, -10, 85),
                   left_leg=(-26, 0, -6, 40), body=dict(pos=(0, -1, -3))),
           BRAWLER(torso=(34, 0, 0, 22), head=(30, 0, 0), right_arm=(-68, -25, 10, 85), left_arm=(-68, 25, -10, 85),
                   left_leg=(-26, 0, -6, 40), body=dict(pos=(0, -1, -3))),
           t, ease_in="easeInQuart"))

# ------------------------------------------------------------------ clinch
HOLD = BRAWLER(torso=(14, 0, 0, 10), right_arm=(-80, -12, 8, 85), left_arm=(-80, 12, -8, 85))
t = timing_of("clinch/grab")
w, a_, r = t
g = Anim("clinch_grab", sum(t), timing=t)
g.key(0, BRAWLER)
g.key(w, BRAWLER(torso=(18, 0, 0, 8), right_arm=(-102, -28, 0, 18), left_arm=(-102, 28, 0, 18),
                 left_leg=(-24, 0, -6, 34), body=dict(pos=(0, 0, -2))), "easeInQuad")
g.key(w + a_, HOLD(body=dict(pos=(0, 0, -1))), "easeOutQuad")
g.key(sum(t), HOLD, "easeInOutSine")
add(g)

t = timing_of("clinch/throw")
w, a_, r = t
th = Anim("throw", sum(t), timing=t)
th.key(0, HOLD)
th.key(w * 0.6, HOLD(torso=(10, 35, 0, 12), right_arm=(-95, 10, 20, 70), left_arm=(-95, 30, -10, 70),
                     right_leg=(-20, 0, 6, 50), left_leg=(-10, 0, -6, 50), body=dict(pos=(0, -2, 0))), "easeOutQuad")
th.key(w, HOLD(torso=(30, -30, 0, 18), right_arm=(-160, -20, 20, 30), left_arm=(-140, 0, -20, 30),
               right_leg=(-10, 0, 10, 20), left_leg=(10, 0, -6, 20), body=dict(rot=(0, -100, 0), pos=(0, 0, 0))), "easeInCubic")
th.key(w + a_ + 1, HOLD(torso=(40, -20, 0, 24), right_arm=(-40, -10, 20, 20), left_arm=(-40, 10, -20, 20),
                        body=dict(rot=(0, -135, 0), pos=(0, -1, 0))), "easeOutQuad")
th.key(sum(t), BRAWLER(body=dict(rot=(0, -360, 0))), "easeInOutSine")
add(th)

# ------------------------------------------------------------------ Gentle Fist (Jūken)
DEEP = dict(left_leg=(-36, 0, -12, 44), right_leg=(32, 0, 14, 40))
t = timing_of("gentle_fist/palm")
add(strike("palm_strike", GENTLE,
           GENTLE(torso=(10, 58, 0, 10), left_arm=(-70, -40, 0, 75)),
           GENTLE(torso=(12, 28, 0, 6), left_arm=(-90, -10, 0, 0), body=dict(pos=(0, -1.5, -3)), **DEEP),
           GENTLE(torso=(12, 26, 0, 6), left_arm=(-88, -10, 0, 6), body=dict(pos=(0, -1.5, -3)), **DEEP),
           t))
t = timing_of("gentle_fist/palm_2")
add(strike("palm_strike_2", GENTLE,
           GENTLE(torso=(10, 60, 0, 10), right_arm=(-70, -20, 20, 85)),
           GENTLE(torso=(14, -10, 0, 6), right_arm=(-90, 8, 0, 0), left_arm=(-60, -20, 0, 90),
                  body=dict(pos=(0, -1.5, -3)), **DEEP),
           GENTLE(torso=(14, -12, 0, 6), right_arm=(-88, 8, 0, 6), left_arm=(-60, -20, 0, 90),
                  body=dict(pos=(0, -1.5, -3)), **DEEP),
           t))
t = timing_of("gentle_fist/palm_3")
add(strike("palm_strike_3", GENTLE,
           GENTLE(torso=(18, 40, 0, 12), left_arm=(-30, -30, 0, 100), body=dict(pos=(0, -2, 0))),
           GENTLE(torso=(-6, 22, 0, 0), left_arm=(-140, -15, 0, 10), body=dict(pos=(0, 0.5, -3)), **DEEP),
           GENTLE(torso=(-8, 20, 0, 0), left_arm=(-145, -15, 0, 14), body=dict(pos=(0, 0.5, -3)), **DEEP),
           t))
t = timing_of("gentle_fist/lunging_palm")
add(strike("lunging_palm", GENTLE,
           GENTLE(torso=(6, 62, 0, 8), right_arm=(-40, -10, 25, 110), body=dict(pos=(0, -2, 1))),
           GENTLE(torso=(22, -5, 0, 8), right_arm=(-92, 5, 0, 0), left_arm=(-20, -10, -30, 30),
                  left_leg=(-55, 0, -10, 40), right_leg=(45, 0, 12, 20), body=dict(pos=(0, -3.5, -7))),
           GENTLE(torso=(22, -8, 0, 8), right_arm=(-90, 5, 0, 6), left_arm=(-20, -10, -30, 30),
                  left_leg=(-55, 0, -10, 40), right_leg=(45, 0, 12, 20), body=dict(pos=(0, -3.5, -7))),
           t, settle=GENTLE(body=dict(pos=(0, -2, -3)))))
t = timing_of("gentle_fist/double_palm")
add(strike("double_palm", GENTLE,
           GENTLE(torso=(-8, 20, 0, -2), right_arm=(-25, 15, 25, 115), left_arm=(-25, -15, -25, 115), body=dict(pos=(0, -2, 1))),
           GENTLE(torso=(16, 0, 0, 8), right_arm=(-92, 8, 0, 0), left_arm=(-92, -8, 0, 0), body=dict(pos=(0, -2, -4)), **DEEP),
           GENTLE(torso=(16, 0, 0, 8), right_arm=(-90, 8, 0, 6), left_arm=(-90, -8, 0, 6), body=dict(pos=(0, -2, -4)), **DEEP),
           t, antic_at=0.6))
t = timing_of("gentle_fist/rising_palm")
add(strike("rising_palm", GENTLE,
           GENTLE(torso=(26, 40, 0, 14), right_arm=(10, -10, 20, 80), body=dict(pos=(0, -3, 0))),
           GENTLE(torso=(-14, 0, 0, -4), right_arm=(-170, 10, 0, 8), left_arm=(-30, -10, -40, 40),
                  left_leg=(-20, 0, -8, 10), right_leg=(16, 0, 10, 8), body=dict(pos=(0, 2.5, -2))),
           GENTLE(torso=(-16, 0, 0, -4), right_arm=(-175, 10, 0, 6), left_arm=(-30, -10, -40, 40),
                  left_leg=(-20, 0, -8, 10), right_leg=(16, 0, 10, 8), body=dict(pos=(0, 3, -2))),
           t, antic_at=0.6))

# ------------------------------------------------------------------ Strong Fist (Gōken)
t = timing_of("strong_fist/jab")
add(strike("strong_fist_jab", STRONG,
           STRONG(torso=(0, -20, 0, 2), right_arm=(-70, 25, 0, 95)),
           STRONG(torso=(8, -48, 0, 2), right_arm=(-92, 40, 0, 0), right_leg=(-26, 0, 4, 28), body=dict(pos=(0, 0, -3))),
           STRONG(torso=(8, -46, 0, 2), right_arm=(-90, 40, 0, 4), right_leg=(-26, 0, 4, 28), body=dict(pos=(0, 0, -3))),
           t, ease_in="easeInQuart"))
t = timing_of("strong_fist/straight")
add(strike("strong_fist_straight", STRONG,
           STRONG(torso=(-4, -5, 0, 0), right_arm=(-60, 20, 15, 110), right_leg=(-10, 0, 4, 40), body=dict(pos=(0, -1, 1))),
           STRONG(torso=(14, -55, 0, 4), right_arm=(-93, 50, 0, 0), right_leg=(-38, 0, 4, 34), left_leg=(28, 0, -8, 8),
                  body=dict(pos=(0, -0.5, -5))),
           STRONG(torso=(12, -52, 0, 4), right_arm=(-90, 48, 0, 4), right_leg=(-38, 0, 4, 34), left_leg=(28, 0, -8, 8),
                  body=dict(pos=(0, -0.5, -5))),
           t))
t = timing_of("strong_fist/dynamic_entry")
add(strike("dynamic_entry", STRONG,
           STRONG(torso=(26, -10, 0, 14), right_arm=(40, 0, 15, 20), left_arm=(40, 0, -15, 20),
                  right_leg=(-35, 0, 4, 90), left_leg=(-35, 0, -4, 90), body=dict(pos=(0, -3.5, 1))),
           P(torso=(-14, 0, 0, -2), right_arm=(55, 0, 35, 20), left_arm=(-45, 0, -25, 50),
             right_leg=(-92, 0, 0, 0), left_leg=(30, 0, 0, 110), body=dict(rot=(-12, 0, 0), pos=(0, 9, -13))),
           P(torso=(-12, 0, 0, -2), right_arm=(55, 0, 35, 20), left_arm=(-45, 0, -25, 50),
             right_leg=(-88, 0, 0, 6), left_leg=(30, 0, 0, 105), body=dict(rot=(-10, 0, 0), pos=(0, 6, -12))),
           t, settle=STRONG(body=dict(pos=(0, -1, -6)), right_leg=(-30, 0, 4, 60))))
t = timing_of("strong_fist/leaf_rising_wind")
add(strike("leaf_rising_wind", STRONG,
           STRONG(torso=(30, -10, 0, 16), right_arm=(-20, 20, 25, 40), left_arm=(-20, 0, -25, 40),
                  right_leg=(-30, 0, 4, 85), left_leg=(-25, 0, -4, 80), body=dict(pos=(0, -4, 0))),
           STRONG(torso=(-28, 0, 0, -6), right_arm=(30, 0, 50, 10), left_arm=(30, 0, -50, 10),
                  right_leg=(-172, 0, 0, 0), left_leg=(-4, 0, -4, 10), body=dict(pos=(0, 3, -1), rot=(-10, 0, 0))),
           STRONG(torso=(-24, 0, 0, -6), right_arm=(30, 0, 50, 10), left_arm=(30, 0, -50, 10),
                  right_leg=(-165, 0, 0, 6), left_leg=(-4, 0, -4, 10), body=dict(pos=(0, 3, -1), rot=(-8, 0, 0))),
           t, settle=STRONG(right_leg=(-60, 0, 4, 70))))
t = timing_of("strong_fist/leaf_whirlwind")
w, a_, r = t
lw = Anim("leaf_whirlwind", sum(t), timing=t)
lw.key(0, STRONG)
lw.key(w * 0.6, STRONG(torso=(20, 10, 0, 12), right_arm=(-40, 0, 40, 30), left_arm=(-40, 0, -40, 30),
                       right_leg=(-25, 0, 4, 70), left_leg=(-25, 0, -4, 70), body=dict(pos=(0, -3, 0), rot=(0, 30, 0))), "easeOutQuad")
lw.key(w, P(torso=(-6, 0, -18, 0), right_arm=(0, 0, 75, 10), left_arm=(0, 0, -75, 10),
            right_leg=(-15, 0, 88, 0), left_leg=(10, 0, -10, 40), body=dict(pos=(0, 7, -1), rot=(0, -150, 0))), "easeInQuad")
lw.key(w + a_ + 0.5, P(torso=(-6, 0, -18, 0), right_arm=(0, 0, 75, 10), left_arm=(0, 0, -75, 10),
                       right_leg=(-15, 0, 85, 0), left_leg=(10, 0, -10, 40), body=dict(pos=(0, 6, -1), rot=(0, -300, 0))), "linear")
lw.key(w + a_ + r * 0.5, STRONG(right_leg=(-20, 0, 20, 60), body=dict(pos=(0, 0, 0), rot=(0, -360, 0))), "easeOutQuad")
lw.key(sum(t), STRONG(body=dict(rot=(0, -360, 0))), "easeInOutSine")
add(lw)

# ------------------------------------------------------------------ more moves per style (round 6)
# Academy: a turning backfist (dash), a low sweep (back) and a push kick (back heavy).
t = timing_of("basic/backfist")
add(strike("spinning_backfist", BASIC,
           BASIC(torso=(12, -40, 0, 8), right_arm=(-70, -70, 0, 125), right_leg=(16, 0, 12, 30)),
           BASIC(torso=(12, 30, 0, 6), right_arm=(-92, 55, 0, 12), left_arm=(-55, 0, 0, 115), left_leg=(-26, 0, -4, 28),
                 body=dict(pos=(0, 0, -1.5))),
           BASIC(torso=(12, 38, 0, 6), right_arm=(-88, 62, 0, 18), left_arm=(-55, 0, 0, 115), left_leg=(-26, 0, -4, 28),
                 body=dict(pos=(0, 0, -1.5))),
           t))
t = timing_of("basic/low_sweep")
add(strike("low_sweep", BASIC,
           BASIC(torso=(28, 20, 0, 14), right_arm=(-20, 0, 25, 60), left_arm=(-40, 0, -20, 80), left_leg=(-55, 0, -8, 105),
                 right_leg=(5, 0, 40, 10), body=dict(pos=(0, -4.5, 0))),
           BASIC(torso=(30, -20, 0, 14), right_arm=(10, 0, 30, 40), left_arm=(-40, 0, -25, 80), left_leg=(-55, 0, -8, 105),
                 right_leg=(-55, 0, -15, 5), body=dict(pos=(0, -4.5, -1))),
           BASIC(torso=(30, -30, 0, 14), right_arm=(10, 0, 30, 40), left_arm=(-40, 0, -25, 80), left_leg=(-55, 0, -8, 105),
                 right_leg=(-60, 0, -30, 5), body=dict(pos=(0, -4.5, -1))),
           t, weight=False))
t = timing_of("basic/push_kick")
add(strike("push_kick", BASIC,
           BASIC(torso=(-4, 5, 0, 2), right_leg=(-85, 0, 4, 105), left_leg=(-6, 0, -4, 20)),
           BASIC(torso=(-16, 0, 0, -4), right_arm=(-70, -20, 5, 120), right_leg=(-88, 0, 4, 0), left_leg=(-4, 0, -4, 18),
                 body=dict(pos=(0, 0.5, -1))),
           BASIC(torso=(-14, 0, 0, -4), right_arm=(-70, -20, 5, 120), right_leg=(-80, 0, 4, 10), left_leg=(-4, 0, -4, 18),
                 body=dict(pos=(0, 0.5, -1))),
           t, weight=False))

# Brawler: a body hook (back) and a two-handed hammer fist (heavy finisher).
t = timing_of("brawler/body_hook")
add(strike("body_hook", BRAWLER,
           BRAWLER(torso=(22, 30, 0, 16), left_arm=(-40, 10, -20, 112)),
           BRAWLER(torso=(26, -28, 0, 18), left_arm=(-58, 42, 0, 96), left_leg=(-18, 0, -6, 40), body=dict(pos=(0, -0.5, -1))),
           BRAWLER(torso=(26, -34, 0, 18), left_arm=(-56, 48, 0, 98), left_leg=(-18, 0, -6, 40), body=dict(pos=(0, -0.5, -1))),
           t))
t = timing_of("brawler/hammer_fist")
add(strike("hammer_fist", BRAWLER,
           BRAWLER(torso=(-12, 0, 0, -6), right_arm=(-172, -12, 0, 45), left_arm=(-172, 12, 0, 45), body=dict(pos=(0, 0.5, 0))),
           BRAWLER(torso=(34, 0, 0, 18), right_arm=(-62, -14, 0, 18), left_arm=(-62, 14, 0, 18), left_leg=(-24, 0, -6, 44),
                   body=dict(pos=(0, -1, -2))),
           BRAWLER(torso=(36, 0, 0, 20), right_arm=(-52, -14, 0, 22), left_arm=(-52, 14, 0, 22), left_leg=(-24, 0, -6, 44),
                   body=dict(pos=(0, -1, -2))),
           t))

# Strong Fist: a spinning back kick (back), the Leaf Great Flash heel drop (finisher) and the
# Leaf Gale, a low spinning sweep (back heavy).
t = timing_of("strong_fist/back_kick")
add(strike("strong_back_kick", STRONG,
           STRONG(torso=(20, 0, 0, 8), right_leg=(20, 0, 4, 80), body=dict(rot=(0, 90, 0))),
           STRONG(torso=(42, 0, 0, 10), right_arm=(-30, 0, 30, 40), right_leg=(80, 0, 4, 0), left_leg=(-8, 0, -4, 24),
                  body=dict(rot=(0, 180, 0), pos=(0, 0, -1))),
           STRONG(torso=(40, 0, 0, 10), right_arm=(-30, 0, 30, 40), right_leg=(72, 0, 4, 10), left_leg=(-8, 0, -4, 24),
                  body=dict(rot=(0, 190, 0), pos=(0, 0, -1))),
           t, settle=STRONG(body=dict(rot=(0, 270, 0))), end=STRONG(body=dict(rot=(0, 360, 0)))))
t = timing_of("strong_fist/leaf_great_flash")
add(strike("leaf_great_flash", STRONG,
           STRONG(torso=(-18, -10, 0, -4), right_arm=(-40, 25, 40, 15), left_arm=(-40, 0, -40, 15), right_leg=(-160, 0, 4, 0),
                  left_leg=(-4, 0, -4, 16), body=dict(pos=(0, 1, 0))),
           STRONG(torso=(28, -10, 0, 12), right_arm=(-20, 25, 40, 15), left_arm=(-20, 0, -40, 15), right_leg=(-48, 0, 4, 0),
                  left_leg=(-4, 0, -4, 40), body=dict(pos=(0, -2, -2))),
           STRONG(torso=(30, -10, 0, 12), right_arm=(-20, 25, 40, 15), left_arm=(-20, 0, -40, 15), right_leg=(-40, 0, 4, 10),
                  left_leg=(-4, 0, -4, 40), body=dict(pos=(0, -2, -2))),
           t, weight=False))
t = timing_of("strong_fist/leaf_gale")
low = dict(torso=(30, 0, 0, 14), left_leg=(-60, 0, -8, 110), right_arm=(10, 0, 40, 30), left_arm=(-30, 0, -30, 60))
add(strike("leaf_gale", STRONG,
           STRONG(right_leg=(10, 0, 50, 10), body=dict(pos=(0, -5, 0), rot=(0, -60, 0)), **low),
           STRONG(right_leg=(-10, 0, 80, 0), body=dict(pos=(0, -5, 0), rot=(0, -190, 0)), **low),
           STRONG(right_leg=(-10, 0, 75, 5), body=dict(pos=(0, -5, 0), rot=(0, -290, 0)), **low),
           t, weight=False, settle=STRONG(body=dict(pos=(0, -2, 0), rot=(0, -340, 0))), end=STRONG(body=dict(rot=(0, -360, 0)))))

# Gentle Fist: a pivoting palm (back), a low trigram sweep (back heavy) and twin lion palms (dash heavy).
t = timing_of("gentle_fist/back_palm")
add(strike("pivot_palm", GENTLE,
           GENTLE(torso=(8, 70, 0, 8), right_arm=(-60, -10, 20, 90)),
           GENTLE(torso=(10, -30, 0, 8), right_arm=(-92, -6, 0, 4), left_arm=(-30, -20, 0, 40), body=dict(pos=(0, -1.2, -1))),
           GENTLE(torso=(10, -36, 0, 8), right_arm=(-90, -6, 0, 8), left_arm=(-30, -20, 0, 40), body=dict(pos=(0, -1.2, -1))),
           t))
t = timing_of("gentle_fist/trigram_sweep")
add(strike("trigram_sweep", GENTLE,
           GENTLE(torso=(24, 60, 0, 14), right_arm=(-30, -40, 30, 50), left_arm=(-50, 30, -20, 40), left_leg=(-50, 0, -14, 90),
                  right_leg=(40, 0, 20, 50), body=dict(pos=(0, -4, 0))),
           GENTLE(torso=(28, -40, 0, 14), right_arm=(-50, 60, 0, 6), left_arm=(-50, -50, 0, 6), left_leg=(-50, 0, -14, 90),
                  right_leg=(40, 0, 20, 50), body=dict(pos=(0, -4, -1))),
           GENTLE(torso=(28, -50, 0, 14), right_arm=(-48, 66, 0, 8), left_arm=(-48, -56, 0, 8), left_leg=(-50, 0, -14, 90),
                  right_leg=(40, 0, 20, 50), body=dict(pos=(0, -4, -1))),
           t, weight=False))
t = timing_of("gentle_fist/lion_palms")
add(strike("lion_palms", GENTLE,
           GENTLE(torso=(-6, 0, 0, -2), right_arm=(35, 0, 15, 50), left_arm=(35, 0, -15, 50), right_leg=(20, 0, 10, 40)),
           GENTLE(torso=(22, 0, 0, 10), right_arm=(-96, 10, 0, 0), left_arm=(-96, -10, 0, 0), left_leg=(-42, 0, -10, 44),
                  right_leg=(34, 0, 12, 20), body=dict(pos=(0, -1, -5))),
           GENTLE(torso=(20, 0, 0, 10), right_arm=(-94, 10, 0, 4), left_arm=(-94, -10, 0, 4), left_leg=(-42, 0, -10, 44),
                  right_leg=(34, 0, 12, 20), body=dict(pos=(0, -1, -5))),
           t))

# ------------------------------------------------------------------ dodge rolls
# A tight ball rolling about a point ~0.4 blocks up (the body pivot is 0.7 up, hence the small drop).
TUCK = P(torso=(55, 0, 0, 60), head=(45, 0, 0), right_arm=(-70, 0, 20, 130), left_arm=(-70, 0, -20, 130),
         right_leg=(-125, 0, 0, 150), left_leg=(-125, 0, 0, 150))
roll = Anim("forward_roll", 12)
roll.key(0, P(torso=(20, 0, 0, 10), right_leg=(-30, 0, 0, 60), left_leg=(-30, 0, 0, 60), body=dict(pos=(0, -2, 0))))
roll.key(3, TUCK(body=dict(rot=(100, 0, 0), pos=(0, -4.5, -3))), "easeInQuad")
roll.key(7, TUCK(body=dict(rot=(260, 0, 0), pos=(0, -4.5, -8))), "linear")
roll.key(10, P(torso=(25, 0, 0, 15), right_leg=(-60, 0, 0, 110), left_leg=(-40, 0, 0, 90),
               body=dict(rot=(345, 0, 0), pos=(0, -3, -10))), "easeOutQuad")
roll.key(12, P(body=dict(rot=(360, 0, 0), pos=(0, 0, -10))), "easeInOutSine")
add(roll)
back = Anim("backward_roll", 12)
back.key(0, P(torso=(10, 0, 0, 10), right_leg=(-40, 0, 0, 80), left_leg=(-40, 0, 0, 80), body=dict(pos=(0, -3, 0))))
back.key(3, TUCK(body=dict(rot=(-100, 0, 0), pos=(0, -4.5, 3))), "easeInQuad")
back.key(7, TUCK(body=dict(rot=(-260, 0, 0), pos=(0, -4.5, 8))), "linear")
back.key(10, P(torso=(25, 0, 0, 15), right_leg=(-60, 0, 0, 110), left_leg=(-40, 0, 0, 90),
               body=dict(rot=(-345, 0, 0), pos=(0, -3, 10))), "easeOutQuad")
back.key(12, P(body=dict(rot=(-360, 0, 0), pos=(0, 0, 10))), "easeInOutSine")
add(back)

# ------------------------------------------------------------------ weapons
t = timing_of("kunai/slash", (3, 2, 6))
add(strike("kunai_slash", KUNAI,
           KUNAI(torso=(10, 20, 0, 8), right_arm=(-70, 55, 0, 45)),
           KUNAI(torso=(16, -40, 0, 8), right_arm=(-85, -55, 0, 25), body=dict(pos=(0, 0, -2))),
           KUNAI(torso=(16, -48, 0, 8), right_arm=(-80, -68, 0, 30), body=dict(pos=(0, 0, -2))),
           t))
t = timing_of("kunai/backhand", (3, 2, 6))
add(strike("kunai_backhand", KUNAI,
           KUNAI(torso=(14, -45, 0, 8), right_arm=(-80, -65, 0, 60)),
           KUNAI(torso=(14, 25, 0, 6), right_arm=(-88, 55, 0, 15), left_arm=(-50, 0, -20, 100), body=dict(pos=(0, 0, -2))),
           KUNAI(torso=(14, 32, 0, 6), right_arm=(-84, 66, 0, 20), left_arm=(-50, 0, -20, 100), body=dict(pos=(0, 0, -2))),
           t))
t = timing_of("kunai/stab", (6, 3, 10))
add(strike("kunai_stab", KUNAI,
           KUNAI(torso=(4, 0, 0, 4), right_arm=(-35, 15, 15, 125), body=dict(pos=(0, -1, 1))),
           KUNAI(torso=(18, -40, 0, 6), right_arm=(-94, 30, 0, 0), right_leg=(-40, 0, 4, 38), left_leg=(30, 0, -8, 10),
                 body=dict(pos=(0, -1.5, -6))),
           KUNAI(torso=(18, -38, 0, 6), right_arm=(-92, 30, 0, 4), right_leg=(-40, 0, 4, 38), left_leg=(30, 0, -8, 10),
                 body=dict(pos=(0, -1.5, -6))),
           t))

t = timing_of("staff/sweep", (5, 3, 8))
add(strike("staff_sweep", STAFF,
           STAFF(torso=(8, 55, 0, 6), right_arm=(-50, 60, 10, 20), left_arm=(-60, 60, -5, 50)),
           STAFF(torso=(14, -45, 0, 6), right_arm=(-75, -50, 0, 10), left_arm=(-80, -30, 0, 30), body=dict(pos=(0, -1, -2))),
           STAFF(torso=(14, -60, 0, 6), right_arm=(-70, -65, 0, 10), left_arm=(-75, -45, 0, 30), body=dict(pos=(0, -1, -2))),
           t))
t = timing_of("staff/thrust", (4, 2, 7))
add(strike("staff_thrust", STAFF,
           STAFF(torso=(4, 35, 0, 4), right_arm=(-20, 20, 10, 80), left_arm=(-50, 40, 0, 80), body=dict(pos=(0, 0, 1))),
           STAFF(torso=(16, 5, 0, 4), right_arm=(-80, 10, 0, 5), left_arm=(-85, 25, 0, 10), left_leg=(-36, 0, -4, 34),
                 body=dict(pos=(0, -1, -5))),
           STAFF(torso=(16, 5, 0, 4), right_arm=(-78, 10, 0, 8), left_arm=(-83, 25, 0, 12), left_leg=(-36, 0, -4, 34),
                 body=dict(pos=(0, -1, -5))),
           t))
t = timing_of("staff/overhead", (7, 3, 11))
add(strike("staff_overhead", STAFF,
           STAFF(torso=(-16, 10, 0, -6), right_arm=(-175, 0, 10, 20), left_arm=(-170, 15, -10, 30), body=dict(pos=(0, 1, 1))),
           STAFF(torso=(30, 0, 0, 16), right_arm=(-70, 0, 0, 5), left_arm=(-75, 15, 0, 10), left_leg=(-34, 0, -4, 46),
                 right_leg=(26, 0, 10, 30), body=dict(pos=(0, -2.5, -3))),
           STAFF(torso=(32, 0, 0, 16), right_arm=(-62, 0, 0, 5), left_arm=(-68, 15, 0, 10), left_leg=(-34, 0, -4, 46),
                 right_leg=(26, 0, 10, 30), body=dict(pos=(0, -2.5, -3))),
           t, antic_at=0.6))
t = timing_of("staff/spin", (6, 4, 10))
w, a_, r = t
sp = Anim("staff_spin", sum(t), timing=t)
sp.key(0, STAFF)
sp.key(w * 0.6, STAFF(torso=(8, 40, 0, 6), right_arm=(-80, 50, 0, 10), left_arm=(-80, 60, 0, 20), body=dict(rot=(0, 30, 0))), "easeOutQuad")
sp.key(w, STAFF(torso=(8, 0, 0, 4), right_arm=(-85, 0, 30, 0), left_arm=(-85, 0, -30, 0), body=dict(rot=(0, -150, 0), pos=(0, 1, 0))), "easeInQuad")
sp.key(w + a_, STAFF(torso=(8, 0, 0, 4), right_arm=(-85, 0, 30, 0), left_arm=(-85, 0, -30, 0), body=dict(rot=(0, -330, 0), pos=(0, 1, 0))), "linear")
sp.key(sum(t), STAFF(body=dict(rot=(0, -360, 0))), "easeOutQuad")
add(sp)

t = timing_of("sword/slash", (4, 2, 7))
add(strike("sword_slash", SWORD,
           SWORD(torso=(-4, 20, 0, -2), right_arm=(-165, 35, 25, 20), left_arm=(-60, 30, 0, 100)),
           SWORD(torso=(20, -35, 0, 10), right_arm=(-55, -45, 0, 5), right_leg=(-32, 0, 4, 34), body=dict(pos=(0, -1, -3))),
           SWORD(torso=(22, -45, 0, 10), right_arm=(-35, -60, 0, 8), right_leg=(-32, 0, 4, 34), body=dict(pos=(0, -1, -3))),
           t))
t = timing_of("sword/cross", (4, 2, 7))
add(strike("sword_cross", SWORD,
           SWORD(torso=(14, -45, 0, 8), right_arm=(-30, -55, 0, 30)),
           SWORD(torso=(-6, 30, 0, -2), right_arm=(-150, 45, 0, 10), body=dict(pos=(0, 0.5, -2))),
           SWORD(torso=(-8, 38, 0, -2), right_arm=(-160, 55, 0, 12), body=dict(pos=(0, 0.5, -2))),
           t))
t = timing_of("sword/thrust", (5, 2, 9))
add(strike("sword_thrust", SWORD,
           SWORD(torso=(2, 10, 0, 2), right_arm=(-50, 30, 20, 100), body=dict(pos=(0, -1, 1))),
           SWORD(torso=(18, -40, 0, 6), right_arm=(-88, 35, 0, 0), right_leg=(-44, 0, 4, 40), left_leg=(32, 0, -8, 10),
                 body=dict(pos=(0, -2, -7))),
           SWORD(torso=(18, -38, 0, 6), right_arm=(-86, 35, 0, 4), right_leg=(-44, 0, 4, 40), left_leg=(32, 0, -8, 10),
                 body=dict(pos=(0, -2, -7))),
           t))
t = timing_of("sword/rising", (6, 3, 11))
add(strike("sword_rising", SWORD,
           SWORD(torso=(28, 20, 0, 14), right_arm=(15, 20, 25, 20), body=dict(pos=(0, -3, 0))),
           SWORD(torso=(-14, -20, 0, -4), right_arm=(-175, -10, 0, 5), body=dict(pos=(0, 4, -2))),
           SWORD(torso=(-16, -24, 0, -4), right_arm=(-180, -10, 0, 8), body=dict(pos=(0, 4.5, -2))),
           t, antic_at=0.6))

# ------------------------------------------------------------------ throws
NEUTRAL = P(torso=(6, 0, 0, 4), right_arm=(-30, 0, 5, 30), left_arm=(-30, 0, -5, 30),
            left_leg=(-10, 0, -4, 16), right_leg=(10, 0, 6, 14))
t = (3, 2, 5)
add(strike("throw_kunai", NEUTRAL,
           NEUTRAL(torso=(-6, 35, 0, -4), right_arm=(-160, 10, 25, 95), left_arm=(-80, 0, -10, 20), right_leg=(18, 0, 8, 26)),
           NEUTRAL(torso=(18, -35, 0, 10), right_arm=(-85, -10, 0, 0), left_arm=(10, 0, -20, 30), left_leg=(-24, 0, -4, 26),
                   body=dict(pos=(0, 0, -2))),
           NEUTRAL(torso=(22, -40, 0, 12), right_arm=(-45, -25, 0, 10), left_arm=(10, 0, -20, 30), left_leg=(-24, 0, -4, 26),
                   body=dict(pos=(0, 0, -2))),
           t, ease_in="easeInQuart"))
t = (2, 2, 5)
add(strike("throw_shuriken", NEUTRAL,
           NEUTRAL(torso=(8, -30, 0, 6), right_arm=(-80, -75, 0, 125), left_arm=(-60, 20, -10, 60)),
           NEUTRAL(torso=(10, 30, 0, 4), right_arm=(-88, 50, 0, 0), left_arm=(-30, 0, -20, 40)),
           NEUTRAL(torso=(10, 35, 0, 4), right_arm=(-85, 60, 0, 5), left_arm=(-30, 0, -20, 40)),
           t, ease_in="easeInQuart"))
t = (5, 2, 7)
add(strike("throw_fuma", NEUTRAL,
           NEUTRAL(torso=(4, 60, 0, 6), right_arm=(-90, 85, 0, 10), left_arm=(-70, 30, -20, 40), right_leg=(22, 0, 12, 30),
                   body=dict(pos=(0, -1, 1))),
           NEUTRAL(torso=(16, -45, 0, 8), right_arm=(-90, -40, 0, 0), left_arm=(20, 0, -30, 20), left_leg=(-34, 0, -4, 34),
                   body=dict(pos=(0, -1, -4))),
           NEUTRAL(torso=(18, -55, 0, 8), right_arm=(-75, -60, 0, 5), left_arm=(20, 0, -30, 20), left_leg=(-34, 0, -4, 34),
                   body=dict(pos=(0, -1, -4))),
           t, antic_at=0.6))

# ------------------------------------------------------------------ jutsu: hand seals and release
SEAL_L = (-32, 35, 0, 70)  # hands together at chest height
SEAL_R = (-32, -35, 0, 70)
seals = Anim("cast_seals", 14)
base = P(torso=(-2, 0, 0, 0), right_arm=SEAL_R, left_arm=SEAL_L, head=(-4, 0, 0), left_leg=(-8, 0, -4, 14), right_leg=(8, 0, 6, 12))
seals.key(0, NEUTRAL)
seals.key(3, base, "easeOutQuad")
for i, tick in enumerate([5, 7, 9, 11]):
    shift = 6 if i % 2 == 0 else -6
    seals.key(tick, base.add(right_arm=(-shift, 0, 0, 8), left_arm=(shift, 0, 0, -8), torso=(0, shift * 0.5, 0, 0)), "easeInOutQuad")
seals.key(14, base, "easeOutQuad")
add(seals)
release = Anim("cast_release", 10)
release.key(0, base)
release.key(2, base.add(torso=(-10, 0, 0, -6), right_arm=(20, 0, 0, 0), left_arm=(20, 0, 0, 0)), "easeOutQuad")
release.key(4, NEUTRAL(torso=(16, 0, 0, 8), right_arm=(-90, 5, 0, 0), left_arm=(-90, -5, 0, 0), left_leg=(-26, 0, -4, 30),
                       body=dict(pos=(0, 0, -2))), "easeInCubic")
release.key(7, NEUTRAL(torso=(14, 0, 0, 8), right_arm=(-88, 5, 0, 4), left_arm=(-88, -5, 0, 4), left_leg=(-26, 0, -4, 30),
                       body=dict(pos=(0, 0, -2))), "easeOutQuad")
release.key(10, NEUTRAL, "easeInOutSine")
add(release)

# ------------------------------------------------------------------ meditation (senjutsu)
# Cross-legged on the ground: hips dropped to the floor, thighs forward and splayed out, shins folded
# in front; back straight, head a little bowed, hands resting together in the lap (a calm seal).
# meditate_sit (sitting down) -> meditate (a long loop of slow breaths, re-sent by the server every
# 4 breaths so it never ends) -> meditate_rise (standing back up). See senjutsu/Meditation.
SEATED = P(torso=(2, 0, 0, 0), head=(8, 0, 0),
           right_arm=(-28, -32, 0, 62), left_arm=(-28, 32, 0, 62),
           right_leg=(-82, 38, 0, 150), left_leg=(-82, -38, 0, 150),
           body=dict(pos=(0, -10.5, 0)))
INHALE = SEATED.add(torso=(-3, 0, 0, -1), head=(-2, 0, 0), right_arm=(-2, 0, 0, -3), left_arm=(-2, 0, 0, -3),
                    body=dict(pos=(0, 0.35, 0)))
BREATH = 80  # ticks per breath: 4 s, slow and calm
sit = Anim("meditate_sit", 16)
sit.key(0, NEUTRAL)
sit.key(7, NEUTRAL(torso=(18, 0, 0, 8), right_arm=(-40, -20, 0, 50), left_arm=(-40, 20, 0, 50),
                   right_leg=(-60, 20, 0, 110), left_leg=(-60, -20, 0, 110), body=dict(pos=(0, -6, 0))), "easeInQuad")
sit.key(16, SEATED, "easeOutQuad")
add(sit)
meditate = Anim("meditate", BREATH * 5, loop=True)
for i in range(5):
    meditate.key(i * BREATH, SEATED)
    meditate.key(i * BREATH + BREATH * 0.45, INHALE)  # in through the nose, a little faster than out
meditate.key(BREATH * 5, SEATED)
add(meditate)
rise = Anim("meditate_rise", 12)
rise.key(0, SEATED)
rise.key(6, NEUTRAL(torso=(20, 0, 0, 8), right_leg=(-50, 15, 0, 95), left_leg=(-50, -15, 0, 95), body=dict(pos=(0, -5, 0))),
         "easeOutQuad")
rise.key(12, NEUTRAL, "easeInOutSine")
add(rise)


if __name__ == "__main__":
    for anim in ANIMS:
        anim.write()
        print("wrote", anim.name)
    # Old files whose animations no longer exist here are left alone; list them so they can be reviewed.
    names = {a.name for a in ANIMS}
    for f in sorted(glob.glob(os.path.join("src/main/resources/assets/noirm/player_animation", "*.json"))):
        if os.path.basename(f)[:-5] not in names and not os.path.basename(f).startswith("seals_"):
            print("not generated here:", os.path.basename(f))

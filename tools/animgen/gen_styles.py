#!/usr/bin/env python3
"""The styles wave (docs/COMBAT_DESIGN.md "Fighting styles"): ten new unarmed styles and four weapon
styles, each with its own stance and sub-stances, footwork, guard, dodge, strings, enders, counter,
dash strike and guard break. Writes, for each style:

  * data/noirm/combat/moves/<style>/*.json   (timing, hit, routes, sub-stance variants, effects)
  * data/noirm/combat/styles/<style>.json    (openers, stances, counter, identity, learning, NPCs)
  * assets/noirm/player_animation/*.json     (the stances and every move, snapped key poses)

plus the dodge animations of each dodge kind (dodge_<kind>_<direction>) and the feint pull-back.

Run from the repo root (then tools/animgen/check_moves.py):

    python3 tools/animgen/gen_styles.py

All numbers are here (and so in the JSON): tune them here and re-run, or edit the JSON and stop
running this. Poses are our own, authored in plain degrees (see motion.py and stylekit.py).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from motion import Anim, P, full, stance_set, snap  # noqa: E402
from stylekit import (H, Style, punch, double_palm, hook, uppercut, elbow, hammer, shoulder, grab, knee,  # noqa: E402
                      front_kick, roundhouse, side_kick, axe_kick, crescent, low_sweep, back_kick, spin_strike, leap_strike)

ANIMS = []
STYLES = []


def style(s):
    STYLES.append(s)
    return s


def legs(front, back, side="left"):
    """Footwork: the front and back leg as (x, z-spread, knee bend), front leg on `side`."""
    fx, fz, fb = front
    bx, bz, bb = back
    if side == "left":
        return dict(left_leg=(fx, 0, -fz, fb), right_leg=(bx, 0, bz, bb))
    return dict(right_leg=(fx, 0, fz, fb), left_leg=(bx, 0, -bz, bb))


# ====================================================================== 1. Drunken Fist
# Loose and swaying, weight thrown about, a gourd hand up near the mouth. Irregular timing (wind-ups
# jitter +-2 ticks, NPCs read them half as well), a forgiving parry, a lurching step-dodge.
DRUNK = P(torso=(-6, 22, 14, 2), head=(0, 0, -10), left_arm=(-42, 10, -12, 110), right_arm=(-72, -32, 12, 140),
          left_leg=(-14, 0, 6, 30), right_leg=(18, 0, -2, 26), body=dict(pos=(0, -1, 0)))


def drunk_tilt(p):
    """Every drunken pose keeps leaning the wrong way."""
    return p.add(torso=(0, 0, 10, 0), head=(0, 0, -8))


s = style(Style("drunken_fist", DRUNK, 44, dict(torso=(3, -14, -22, 0), head=(0, 0, 16), right_arm=(6, 0, 0, -10),
                                                 body=dict(pos=(1.2, -0.6, 0))), ("aggressive", "defensive", "low"),
                flavour=drunk_tilt))
s.move("stagger_jab", (2, 2, 4, 2), H(3, 10, 2.5, 110, 0.15, 7, 1, 0.3, lunge=0.25), punch(DRUNK, "left", twist=40, lean=6),
       {"light": "tipsy_hook", "heavy": "tumbling_fist"}, {"low": "floor_sweep"})
s.move("tipsy_hook", (3, 2, 5, 2), H(3.5, 12, 2.5, 130, 0.2, 8, 2, 0.35), hook(DRUNK, "right", twist=48, lean=4),
       {"light": "falling_elbow", "heavy": "rising_stumble"})
s.move("falling_elbow", (3, 2, 6, 3), H(4, 16, 2.4, 110, 0.3, 9, 2, 0.4, lunge=0.35),
       elbow(DRUNK.add(torso=(0, 0, -20, 0)), "left", twist=44, lean=30), {"light": "drunken_kick", "heavy": "gourd_slam"})
s.move("drunken_kick", (4, 2, 7, 4), H(5, 18, 2.9, 90, 0.6, 10, 2, 0.5), front_kick(DRUNK, "right", height=105, lean=-34),
       {"light": "gourd_slam", "heavy": "tumbling_fist"})
s.move("gourd_slam", (5, 3, 9, 6), H(7, 32, 2.6, 100, 0.4, 16, 4, 0.9, lunge=0.3, guard_break=True, knockdown=True, ender="knockdown"),
       hammer(DRUNK, lean=40), {"light": "stagger_jab"})
s.move("tumbling_fist", (5, 2, 8, 5), H(6, 24, 2.8, 100, 1.0, 12, 3, 0.7, lunge=0.6, ender="splat"),
       (DRUNK(torso=(-34, 30, 24, -10), right_arm=(-20, 0, 40, 60), head=(-20, 0, 0)).add(body=dict(pos=(0, 0.5, 2))),
        DRUNK(torso=(20, -40, -10, 6), right_arm=(-95, 40, 0, 0), left_arm=(-10, 0, -30, 40)).add(body=dict(pos=(0, -2, -5)))),
       {"light": "stagger_jab"}, {"aggressive": "gourd_slam", "defensive": "lean_away_kick"})
s.move("rising_stumble", (4, 3, 8, 4), H(5, 18, 2.4, 100, 0.2, 16, 3, 0.7, lift=0.65, ender="launch"), uppercut(DRUNK, "right", twist=30),
       {"light": "stagger_jab"})
s.move("lurch_jab", (3, 2, 6, 3), H(4, 14, 2.8, 100, 0.5, 9, 2, 0.4, lunge=0.6), punch(DRUNK, "right", twist=50, lean=26, drive=-5),
       {"light": "tipsy_hook"})
s.move("falling_lunge", (5, 3, 9, 6), H(6.5, 24, 3.0, 100, 1.2, 12, 3, 0.7, lunge=1.0, ender="splat"),
       shoulder(DRUNK, "left", lean=44, drive=-9), {"light": "stagger_jab"})
s.move("backhand_swing", (3, 2, 6, 3), H(3.5, 12, 2.6, 140, 0.3, 8, 2, 0.35),
       (DRUNK(torso=(4, -40, 18, 2), left_arm=(-60, -60, 0, 110)), DRUNK(torso=(8, 40, 0, 2), left_arm=(-88, 70, 0, 20))),
       {"light": "falling_elbow"})
s.move("lean_away_kick", (4, 2, 7, 4), H(4.5, 16, 3.0, 90, 1.1, 9, 2, 0.5), front_kick(DRUNK, "left", height=88, lean=-42),
       {"light": "stagger_jab"})
s.move("counter_sway", (1, 2, 6, 2), H(4, 18, 2.8, 140, 0.5, 12, 3, 0.6), hook(DRUNK.add(torso=(0, 0, -24, 0)), "left", twist=50),
       {"light": "tipsy_hook"})
s.move("floor_sweep", (4, 3, 7, 4), H(3.5, 16, 2.6, 160, 0.2, 14, 2, 0.4, knockdown=True), low_sweep(DRUNK, "right", depth=6),
       {"light": "stagger_jab"}, weight=False)
s.write({"light": "stagger_jab", "heavy": "tumbling_fist", "launcher": "rising_stumble", "forward_light": "lurch_jab",
         "forward_heavy": "falling_lunge", "back_light": "backhand_swing", "back_heavy": "lean_away_kick"},
        "counter_sway",
        {"footwork": "swaying", "rhythm": "irregular", "move_speed": 1.0,
         "guard": {"type": "parry", "parry_window": 1.4, "keep": 0.3, "posture": 0.7},
         "dodge": {"kind": "step", "speed": 0.9, "iframes": 7},
         "strike": {"jitter": 2, "read": 0.5}},
        {"required": True, "watch": 0.025, "scroll": 4}, npc_weight=1)

# ====================================================================== 2. Iron Body
# Wide, rooted, upright, hands high and tight like a wall. Armoured guard (keeps out 90%, shrugs off a third
# of all posture damage) but you plod; heavy blows that keep coming through hits (armoured strikes).
IRON = P(torso=(4, 10, 0, 4), left_arm=(-102, 22, -10, 122), right_arm=(-96, -26, 10, 128),
         body=dict(pos=(0, -2.4, 0)), **legs((-14, 22, 34), (14, 22, 34)))
s = style(Style("iron_body", IRON, 52, dict(torso=(2, 0, 0, 2), left_arm=(-2, 0, 0, 3), body=dict(pos=(0, -0.5, 0))),
                ("aggressive", "defensive")))
s.move("iron_jab", (2, 2, 4, 2), H(4.5, 18, 2.4, 90, 0.3, 9, 2, 0.45), punch(IRON, "left", twist=24, lean=10),
       {"light": "iron_cross", "heavy": "iron_palm"})
s.move("iron_cross", (4, 2, 7, 4), H(5.5, 24, 2.5, 90, 0.4, 10, 3, 0.55), punch(IRON, "right", twist=40, lean=16, drive=-3),
       {"light": "iron_elbow", "heavy": "iron_uppercut"})
s.move("iron_elbow", (4, 2, 7, 4), H(6, 28, 2.3, 110, 0.4, 11, 3, 0.6), elbow(IRON, "left", twist=36, lean=18),
       {"light": "iron_hammer", "heavy": "iron_crash"}, effects={"armour": 0.4})
s.move("iron_hammer", (6, 3, 9, 6), H(7, 34, 2.4, 90, 0.5, 16, 4, 0.9, knockdown=True, ender="knockdown"), hammer(IRON, lean=36),
       {"light": "iron_crash"}, effects={"armour": 0.5})
s.move("iron_crash", (7, 3, 10, 7), H(9, 42, 2.8, 100, 1.4, 16, 4, 1.0, lunge=0.7, guard_break=True, ender="splat"),
       shoulder(IRON, "left", lean=30, drive=-8), {"light": "iron_jab"}, effects={"armour": 0.6})
s.move("iron_palm", (6, 3, 9, 6), H(8, 38, 2.6, 90, 1.2, 14, 4, 0.9, lunge=0.3, ender="splat"), double_palm(IRON, lean=24, drive=-5),
       {"light": "iron_jab"}, {"aggressive": "iron_crash", "defensive": "iron_brace"}, effects={"armour": 0.5})
s.move("iron_uppercut", (6, 3, 9, 6), H(7, 30, 2.4, 90, 0.2, 18, 4, 0.8, lift=0.7, ender="launch"), uppercut(IRON, "right"),
       {"light": "iron_jab"}, effects={"armour": 0.4})
s.move("iron_shoulder", (5, 2, 8, 5), H(6, 28, 2.8, 90, 1.0, 11, 3, 0.7, lunge=0.6), shoulder(IRON, "right", lean=24),
       {"light": "iron_cross"}, effects={"armour": 0.5})
s.move("iron_brace", (4, 2, 7, 4), H(4, 24, 2.4, 110, 1.0, 8, 2, 0.5),
       (IRON(torso=(-6, 0, 0, -2), right_arm=(-60, -10, 20, 120), left_arm=(-60, 10, -20, 120)),
        IRON(torso=(20, 0, 0, 8), right_arm=(-88, 12, 0, 20), left_arm=(-88, -12, 0, 20)).add(body=dict(pos=(0, -0.5, -3)))),
       {"light": "iron_jab"}, effects={"armour": 0.6})
s.move("iron_stomp", (6, 3, 9, 6), H(6, 30, 2.6, 360, 0.6, 18, 4, 1.0, knockdown=True),
       (IRON(torso=(-6, 0, 0, 0), right_leg=(-95, 0, 8, 100), left_arm=(-60, 0, -30, 80), right_arm=(-60, 0, 30, 80)).add(body=dict(pos=(0, 1.5, 0))),
        IRON(torso=(24, 0, 0, 14), right_leg=(-20, 0, 18, 30), left_arm=(-20, 0, -40, 40), right_arm=(-20, 0, 40, 40)).add(body=dict(pos=(0, -3.5, 0)))),
       {"light": "iron_jab"}, effects={"armour": 0.5}, weight=False)
s.move("iron_counter", (1, 2, 7, 3), H(6.5, 30, 2.6, 130, 0.8, 14, 4, 0.8), punch(IRON, "right", twist=46, lean=18),
       {"light": "iron_jab"})
s.write({"light": "iron_jab", "heavy": "iron_palm", "launcher": "iron_uppercut", "forward_light": "iron_shoulder",
         "forward_heavy": "iron_crash", "back_light": "iron_brace", "back_heavy": "iron_stomp"},
        "iron_counter",
        {"footwork": "wide", "rhythm": "heavy", "move_speed": 0.82,
         "guard": {"type": "armoured", "parry_window": 0.8, "keep": 0.1, "posture": 0.4, "armour": 0.35, "speed": 0.3},
         "dodge": {"kind": "step", "speed": 0.55, "ticks": 9, "iframes": 4, "stamina": 1.4, "lift": 0.05}},
        {"required": True, "watch": 0.02, "scroll": 3}, npc_weight=1,
        npc={"clans": {"akimichi": 4, "senju": 1}, "min_class": "C"})

# ====================================================================== 3. Viper
# Very low and bladed, the lead hand reaching like a snake's head. Fast finger jabs at pressure points that
# knock the breath out (stamina drain on every hit, more on the venom strike); a low slide for a dodge.
VIPER = P(torso=(26, 45, 0, 18), left_arm=(-80, -32, 0, 30), right_arm=(-40, -20, 15, 120),
          body=dict(pos=(0, -3.8, 0)), **legs((-34, 6, 60), (30, 14, 55)))
s = style(Style("viper", VIPER, 18, dict(torso=(0, 6, 0, 2), left_arm=(-8, 6, 0, 12), body=dict(pos=(0, -0.4, 0))),
                ("aggressive", "defensive", "low")))
s.move("fang_jab", (2, 2, 3, 1), H(2, 6, 2.6, 80, 0.05, 6, 1, 0.2, lunge=0.25), punch(VIPER, "left", twist=50, lean=28, low=0.5),
       {"light": "fang_jab_2", "heavy": "striking_cobra"}, {"low": "low_fang"}, effects={"stamina_drain": 3})
s.move("fang_jab_2", (2, 2, 3, 1), H(2, 6, 2.6, 80, 0.05, 6, 1, 0.2, lunge=0.2), punch(VIPER, "right", twist=40, lean=28, low=0.5),
       {"light": "double_fang", "heavy": "striking_cobra"}, effects={"stamina_drain": 3})
s.move("double_fang", (2, 2, 4, 2), H(2.5, 8, 2.6, 90, 0.1, 7, 1, 0.25, lunge=0.2), double_palm(VIPER, height=-80, lean=30, drive=-3, spread=4),
       {"light": "coil_strike", "heavy": "rising_fang"}, effects={"stamina_drain": 4})
s.move("coil_strike", (3, 2, 5, 2), H(3, 10, 2.8, 80, 0.15, 8, 2, 0.3, lunge=0.35),
       (VIPER(torso=(34, 70, 0, 22), left_arm=(-30, -10, 0, 130)).add(body=dict(pos=(0, -1, 1.5))),
        VIPER(torso=(30, 10, 0, 14), left_arm=(-82, -8, 0, 0)).add(body=dict(pos=(0, -0.5, -4)))),
       {"light": "venom_strike", "heavy": "venom_strike"}, effects={"stamina_drain": 8})
s.move("venom_strike", (4, 2, 7, 5), H(4.5, 22, 2.8, 70, 0.3, 18, 3, 0.6, lunge=0.4, guard_break=True, knockdown=True, ender="knockdown"),
       (VIPER(torso=(20, 80, 0, 18), right_arm=(-40, 20, 30, 140), left_arm=(-60, -10, 0, 60)).add(body=dict(pos=(0, -1, 2))),
        VIPER(torso=(32, -30, 0, 14), right_arm=(-86, 30, 0, 0), left_arm=(-20, -10, -30, 60)).add(body=dict(pos=(0, -0.5, -5)))),
       {"light": "fang_jab"}, effects={"stamina_drain": 16})
s.move("striking_cobra", (4, 2, 7, 4), H(4.5, 16, 3.2, 60, 0.4, 10, 2, 0.5, lunge=0.7),
       punch(VIPER, "left", twist=56, lean=34, drive=-6, low=1.0), {"light": "fang_jab_2"},
       {"aggressive": "venom_strike", "defensive": "recoil_strike"}, effects={"stamina_drain": 12})
s.move("rising_fang", (4, 2, 7, 4), H(3.5, 14, 2.5, 90, 0.1, 16, 3, 0.6, lift=0.65, ender="launch"), uppercut(VIPER, "right", twist=30),
       {"light": "fang_jab"}, effects={"stamina_drain": 6})
s.move("lunging_fang", (3, 2, 6, 3), H(3, 10, 3.0, 70, 0.2, 8, 2, 0.4, lunge=0.8), punch(VIPER, "left", twist=56, lean=34, drive=-7),
       {"light": "fang_jab_2"}, effects={"stamina_drain": 6})
s.move("viper_lunge", (4, 2, 8, 5), H(5, 18, 3.2, 70, 1.2, 12, 3, 0.6, lunge=1.0, ender="splat"),
       double_palm(VIPER, height=-84, lean=38, drive=-8, spread=4), {"light": "fang_jab"}, effects={"stamina_drain": 10})
s.move("recoil_strike", (2, 2, 4, 2), H(2.5, 8, 2.6, 80, 0.3, 7, 1, 0.3), punch(VIPER, "right", twist=40, lean=20),
       {"light": "fang_jab"}, effects={"stamina_drain": 4, "recoil": 0.6})
s.move("tail_sweep", (4, 3, 7, 4), H(3.5, 16, 2.8, 200, 0.2, 14, 2, 0.4, knockdown=True), low_sweep(VIPER, "right", depth=5.5),
       {"light": "fang_jab"}, weight=False, effects={"stamina_drain": 6})
s.move("counter_fang", (1, 2, 5, 2), H(3, 14, 2.8, 140, 0.4, 12, 3, 0.5), punch(VIPER, "right", twist=54, lean=30),
       {"light": "fang_jab_2"}, effects={"stamina_drain": 14})
s.move("low_fang", (2, 2, 4, 2), H(2, 10, 2.5, 90, 0.05, 8, 1, 0.25),
       (VIPER(torso=(34, 40, 0, 22), left_arm=(-30, -20, 0, 100)).add(body=dict(pos=(0, -1.5, 0))),
        VIPER(torso=(40, 20, 0, 22), left_arm=(-50, -10, 0, 0)).add(body=dict(pos=(0, -2, -2)))),
       {"light": "fang_jab_2"}, effects={"stamina_drain": 5})
s.write({"light": "fang_jab", "heavy": "striking_cobra", "launcher": "rising_fang", "forward_light": "lunging_fang",
         "forward_heavy": "viper_lunge", "back_light": "recoil_strike", "back_heavy": "tail_sweep"},
        "counter_fang",
        {"footwork": "low", "rhythm": "fast", "move_speed": 1.12,
         "guard": {"type": "standard", "keep": 0.3},
         "dodge": {"kind": "slide", "speed": 1.2, "ticks": 9, "iframes": 6, "lift": 0.0},
         "strike": {"stamina_drain": 2}},
        {"required": True, "watch": 0.02, "scroll": 3}, npc_weight=1, npc={"min_class": "C"})

# ====================================================================== 4. Crane
# High and upright on one leg, wings spread. Long kicks (reach 3+), a light guard with a wider parry, a hop
# for a dodge, and +35% damage from the air.
CRANE = P(torso=(-4, 20, 0, 0), left_arm=(-112, -10, -32, 8), right_arm=(-22, 0, 52, 14),
          right_leg=(-78, 0, 6, 112), left_leg=(-4, 0, -4, 14), body=dict(pos=(0, -0.6, 0)))
CRANE_DOWN = CRANE(right_leg=(18, 0, 10, 24), left_leg=(-16, 0, -8, 26))  # both feet down, for the turning kicks
s = style(Style("crane", CRANE, 30, dict(right_leg=(8, 0, 0, -10), left_arm=(-6, 0, 6, 0), right_arm=(0, 0, 8, 0)),
                ("aggressive", "defensive", "air")))
s.move("snap_kick", (2, 2, 4, 2), H(3, 10, 3.0, 70, 0.2, 7, 1, 0.3), front_kick(CRANE, "right", height=95, lean=-12),
       {"light": "roundhouse", "heavy": "beak_kick"}, {"air": "air_kick"}, weight=False)
s.move("roundhouse", (3, 2, 5, 3), H(4, 14, 3.1, 120, 0.4, 8, 2, 0.4), roundhouse(CRANE_DOWN, "right", height=100),
       {"light": "crescent", "heavy": "rising_kick"}, weight=False)
s.move("crescent", (3, 2, 6, 3), H(4.5, 16, 3.0, 140, 0.4, 9, 2, 0.45), crescent(CRANE_DOWN, "left"),
       {"light": "side_kick", "heavy": "beak_kick"}, weight=False)
s.move("side_kick", (4, 2, 7, 4), H(5, 18, 3.2, 70, 0.9, 10, 3, 0.55), side_kick(CRANE_DOWN, "right"),
       {"light": "wing_kick", "heavy": "axe_kick"}, weight=False)
s.move("wing_kick", (5, 3, 8, 6), H(6.5, 26, 3.2, 200, 1.0, 14, 4, 0.8, lift=0.2, ender="splat"),
       anim=spin_strike(CRANE, P(torso=(-16, 0, 30, 0), right_arm=(10, 0, 80, 0), left_arm=(10, 0, -80, 0),
                                 right_leg=(-14, 0, 100, 0), left_leg=(-40, 0, -10, 90)),
                        CRANE(torso=(10, 30, 0, 6), right_leg=(-40, 0, 20, 90)), turn=-360, lift=5.0), nxt={"light": "snap_kick"})
s.move("beak_kick", (5, 2, 7, 5), H(6, 22, 3.2, 70, 0.6, 12, 3, 0.7), front_kick(CRANE, "right", height=150, lean=-30),
       {"light": "snap_kick"}, {"aggressive": "axe_kick", "defensive": "push_kick"}, weight=False)
s.move("axe_kick", (5, 2, 8, 6), H(6, 30, 2.9, 70, 0.3, 14, 4, 0.8, lunge=0.3, guard_break=True, ender="knockdown"),
       axe_kick(CRANE_DOWN, "right"), {"light": "snap_kick"}, weight=False)
s.move("rising_kick", (4, 3, 8, 4), H(4.5, 16, 2.9, 90, 0.1, 16, 3, 0.6, lift=0.75, ender="launch"),
       front_kick(CRANE, "right", height=172, lean=-26), {"light": "snap_kick"}, weight=False)
s.move("flying_kick", (4, 2, 7, 4), H(5, 18, 3.3, 80, 0.8, 10, 3, 0.6, lunge=0.8),
       anim=leap_strike(CRANE, CRANE(torso=(26, 0, 0, 14), right_leg=(-35, 0, 4, 90), left_leg=(-35, 0, -4, 90)),
                        P(torso=(-16, 0, 0, -2), right_arm=(55, 0, 35, 20), left_arm=(-50, 0, -25, 40), right_leg=(-95, 0, 0, 0),
                          left_leg=(30, 0, 0, 115), body=dict(rot=(-14, 0, 0))), rise=7, drive=-11), nxt={"light": "roundhouse"},
       effects={"aerial": 1.1})
s.move("dive_kick", (5, 2, 8, 5), H(6.5, 24, 3.4, 80, 1.2, 12, 4, 0.8, lunge=1.0, ender="splat"),
       anim=leap_strike(CRANE, CRANE(torso=(30, 0, 0, 16), right_leg=(-40, 0, 4, 100), left_leg=(-40, 0, -4, 100)),
                        P(torso=(10, 0, 0, 6), right_arm=(-150, 0, 30, 10), left_arm=(-150, 0, -30, 10), right_leg=(-60, 0, 0, 0),
                          left_leg=(-10, 0, 0, 120), body=dict(rot=(25, 0, 0))), rise=10, drive=-13), nxt={"light": "snap_kick"},
       effects={"aerial": 1.2})
s.move("push_kick", (3, 2, 6, 3), H(4, 14, 3.1, 80, 1.0, 9, 2, 0.45), front_kick(CRANE, "left", height=88, lean=-20),
       {"light": "snap_kick"}, weight=False)
s.move("back_kick", (4, 2, 7, 4), H(5, 18, 3.0, 70, 0.8, 10, 3, 0.55), back_kick(CRANE_DOWN, "right"), {"light": "snap_kick"},
       weight=False, end=CRANE.add(body=dict(rot=(0, 360, 0))))
s.move("counter_kick", (1, 2, 6, 2), H(4.5, 18, 3.1, 130, 0.6, 12, 3, 0.6), roundhouse(CRANE_DOWN, "left", height=110),
       {"light": "roundhouse"}, weight=False)
s.move("air_kick", (3, 2, 6, 3), H(4.5, 16, 3.0, 90, 0.6, 10, 2, 0.5, lunge=0.4),
       (CRANE(torso=(24, 0, 0, 16), right_leg=(-70, 0, 0, 130), left_leg=(-50, 0, 0, 115), right_arm=(-150, 0, 30, 30)),
        CRANE(torso=(-14, 0, 0, -2), right_leg=(-80, 0, 0, 0), left_leg=(25, 0, 0, 95), right_arm=(40, 0, 45, 20)).add(body=dict(rot=(-12, 0, 0)))),
       {"light": "snap_kick"}, weight=False, effects={"aerial": 1.1})
s.write({"light": "snap_kick", "heavy": "beak_kick", "launcher": "rising_kick", "forward_light": "flying_kick",
         "forward_heavy": "dive_kick", "back_light": "push_kick", "back_heavy": "back_kick"},
        "counter_kick",
        {"footwork": "upright", "rhythm": "kicks", "move_speed": 1.05,
         "guard": {"type": "high", "parry_window": 1.25, "keep": 0.22, "speed": 0.6},
         "dodge": {"kind": "hop", "speed": 0.9, "ticks": 11, "iframes": 7, "lift": 0.38},
         "strike": {"aerial": 1.35}},
        {"required": True, "watch": 0.025, "scroll": 4}, npc_weight=1)

# ====================================================================== 5. Mountain
# A deep horse stance, palms open. Rooted: you barely move, but the guard is a counter stance: a parry
# window over twice as wide, and every parry answers at once with a heavy riposte.
MOUNT = P(torso=(6, 0, 0, 4), left_arm=(-76, -16, -10, 42), right_arm=(-56, 22, 10, 72),
          left_leg=(-6, 0, -28, 62), right_leg=(6, 0, 28, 62), body=dict(pos=(0, -4.2, 0)))
s = style(Style("mountain", MOUNT, 72, dict(torso=(1, 0, 0, 1), body=dict(pos=(0, -0.3, 0))), ("aggressive", "defensive")))
s.move("stone_palm", (2, 2, 4, 2), H(4, 16, 2.4, 90, 0.3, 9, 2, 0.4), punch(MOUNT, "left", twist=20, lean=12, drive=-1.5),
       {"light": "rock_elbow", "heavy": "landslide_palm"}, weight=False)
s.move("rock_elbow", (3, 2, 6, 3), H(4.5, 20, 2.3, 110, 0.3, 10, 2, 0.45), elbow(MOUNT, "right", twist=34, lean=14),
       {"light": "boulder_shoulder", "heavy": "rising_peak"}, weight=False)
s.move("boulder_shoulder", (4, 2, 7, 4), H(5, 22, 2.5, 100, 0.7, 10, 3, 0.55), shoulder(MOUNT, "left", lean=20, drive=-4),
       {"light": "landslide_palm", "heavy": "mountain_crush"}, weight=False)
s.move("landslide_palm", (5, 3, 8, 5), H(6.5, 30, 2.5, 100, 0.8, 12, 3, 0.7), punch(MOUNT, "right", twist=30, lean=16, drive=-3),
       {"light": "mountain_crush"}, weight=False)
s.move("mountain_crush", (6, 3, 9, 6), H(8, 40, 2.6, 100, 1.4, 16, 4, 1.0, guard_break=True, ender="splat"),
       double_palm(MOUNT, height=-88, lean=20, drive=-3), {"light": "stone_palm"}, weight=False)
s.move("earth_palm", (5, 3, 8, 5), H(6, 28, 2.5, 90, 1.0, 12, 3, 0.7, ender="splat"),
       (MOUNT(torso=(-6, 40, 0, -2), right_arm=(-30, 0, 20, 100)).add(body=dict(pos=(0, -0.5, 1))),
        MOUNT(torso=(16, -10, 0, 6), right_arm=(-90, 8, 0, 0), left_arm=(-30, 0, -20, 60)).add(body=dict(pos=(0, -0.5, -3)))),
       {"light": "stone_palm"}, {"aggressive": "mountain_crush", "defensive": "rooted_push"}, weight=False)
s.move("rising_peak", (5, 3, 8, 5), H(6, 24, 2.4, 90, 0.2, 18, 3, 0.7, lift=0.7, ender="launch"), uppercut(MOUNT, "left", twist=16),
       {"light": "stone_palm"}, weight=False)
s.move("avalanche_step", (4, 2, 7, 4), H(5, 22, 2.8, 90, 0.6, 10, 3, 0.55, lunge=0.5), punch(MOUNT, "left", twist=34, lean=18, drive=-5),
       {"light": "rock_elbow"}, weight=False)
s.move("avalanche_charge", (6, 3, 9, 6), H(7, 30, 2.9, 100, 1.3, 14, 4, 0.9, lunge=0.8, ender="splat"),
       shoulder(MOUNT, "right", lean=26, drive=-8), {"light": "stone_palm"}, weight=False)
s.move("rooted_push", (3, 2, 6, 3), H(3.5, 22, 2.4, 110, 1.3, 9, 2, 0.5), double_palm(MOUNT, height=-80, lean=10, drive=-1),
       {"light": "stone_palm"}, weight=False)
s.move("quake_stomp", (6, 3, 9, 6), H(5.5, 28, 2.6, 360, 0.5, 18, 4, 1.0, knockdown=True),
       (MOUNT(torso=(-4, 0, 0, 0), left_leg=(-90, 0, -20, 100), right_arm=(-160, 0, 30, 20), left_arm=(-160, 0, -30, 20)).add(body=dict(pos=(0, 1.5, 0))),
        MOUNT(torso=(26, 0, 0, 16), left_leg=(-10, 0, -30, 70), right_arm=(-40, 0, 40, 10), left_arm=(-40, 0, -40, 10)).add(body=dict(pos=(0, -4, 0)))),
       {"light": "stone_palm"}, weight=False)
s.move("riposte", (1, 3, 7, 3), H(6, 30, 2.6, 140, 0.9, 14, 4, 0.8), double_palm(MOUNT, height=-90, lean=22, drive=-3),
       {"light": "rock_elbow"}, weight=False)
s.write({"light": "stone_palm", "heavy": "earth_palm", "launcher": "rising_peak", "forward_light": "avalanche_step",
         "forward_heavy": "avalanche_charge", "back_light": "rooted_push", "back_heavy": "quake_stomp"},
        "riposte",
        {"footwork": "rooted", "rhythm": "counter", "move_speed": 0.85,
         "guard": {"type": "counter", "parry_window": 2.2, "keep": 0.2, "posture": 0.8, "speed": 0.4},
         "dodge": {"kind": "step", "speed": 0.5, "ticks": 8, "iframes": 5, "stamina": 0.8, "lift": 0.04}},
        {"required": True, "watch": 0.015, "scroll": 2}, npc_weight=1, npc={"min_class": "B"})

# ====================================================================== 6. Wind Step
# Upright, bouncing on the toes, hands low. No block at all: guard is a quick sway; the hop-dodge is cheap
# with a wider perfect window. Light lunging strikes that spring back out of reach (recoil).
WIND = P(torso=(6, 16, 0, 2), left_arm=(-36, 0, -14, 62), right_arm=(-26, 0, 14, 52),
         body=dict(pos=(0, -0.6, 0)), **legs((-16, 6, 28), (16, 8, 26)))
s = style(Style("wind_step", WIND, 10, dict(left_leg=(0, 0, 0, 14), right_leg=(0, 0, 0, 14), body=dict(pos=(0, -1.4, 0)),
                                           left_arm=(4, 0, 0, -6), right_arm=(4, 0, 0, -6)), ("aggressive", "air")))
s.move("gust_jab", (2, 2, 3, 1), H(2.5, 8, 2.6, 90, 0.15, 6, 1, 0.25, lunge=0.4), punch(WIND, "left", twist=40, lean=16, drive=-3),
       {"light": "gust_cross", "heavy": "passing_strike"}, {"air": "falling_leaf"})
s.move("gust_cross", (2, 2, 3, 1), H(2.5, 8, 2.6, 90, 0.15, 6, 1, 0.25, lunge=0.3), punch(WIND, "right", twist=44, lean=16, drive=-3),
       {"light": "whirl_kick", "heavy": "updraft_kick"})
s.move("whirl_kick", (3, 2, 5, 2), H(3.5, 12, 2.9, 130, 0.3, 8, 2, 0.35), roundhouse(WIND, "right", height=95),
       {"light": "gale_knee", "heavy": "tornado_heel"}, weight=False)
s.move("gale_knee", (3, 2, 5, 3), H(4, 14, 2.4, 90, 0.4, 9, 2, 0.4, lunge=0.5), knee(WIND, "left"),
       {"light": "cyclone_kick", "heavy": "tornado_heel"})
s.move("cyclone_kick", (4, 3, 7, 4), H(5, 20, 3.0, 220, 1.0, 12, 3, 0.7, ender="splat"),
       anim=spin_strike(WIND, P(torso=(-14, 0, -28, 0), right_arm=(20, 0, 70, 10), left_arm=(20, 0, -70, 10),
                                left_leg=(-14, 0, -95, 0), right_leg=(-30, 0, 10, 80)),
                        WIND(torso=(8, -30, 0, 4)), turn=360, lift=4.0), nxt={"light": "gust_jab"}, effects={"recoil": 0.5})
s.move("passing_strike", (4, 2, 6, 4), H(4.5, 14, 3.0, 90, 0.3, 9, 2, 0.45, lunge=1.0), punch(WIND, "right", twist=60, lean=28, drive=-8),
       {"light": "gust_jab"}, {"aggressive": "cyclone_kick"})
s.move("updraft_kick", (4, 3, 7, 4), H(4, 14, 2.8, 90, 0.1, 16, 3, 0.6, lift=0.75, ender="launch"),
       front_kick(WIND, "right", height=168, lean=-24), {"light": "gust_jab"}, weight=False)
s.move("swallow_dive", (3, 2, 6, 3), H(3.5, 12, 3.0, 80, 0.5, 8, 2, 0.4, lunge=0.9), punch(WIND, "left", twist=56, lean=30, drive=-9),
       {"light": "gust_cross"}, effects={"recoil": 0.5})
s.move("hurricane_dash", (4, 2, 7, 4), H(5, 18, 3.2, 80, 1.1, 10, 3, 0.6, lunge=1.2, ender="splat"),
       anim=leap_strike(WIND, WIND(torso=(26, 0, 0, 14), right_leg=(-35, 0, 4, 90), left_leg=(-35, 0, -4, 90)),
                        P(torso=(-14, 0, 0, -2), right_arm=(55, 0, 35, 20), left_arm=(-45, 0, -25, 50), right_leg=(-92, 0, 0, 0),
                          left_leg=(30, 0, 0, 110), body=dict(rot=(-12, 0, 0))), rise=6, drive=-12), nxt={"light": "gust_jab"})
s.move("hit_and_fade", (2, 2, 4, 2), H(2.5, 8, 2.6, 90, 0.2, 6, 1, 0.3), punch(WIND, "left", twist=36, lean=12),
       {"light": "gust_cross"}, effects={"recoil": 0.7})
s.move("tornado_heel", (4, 3, 7, 4), H(5, 26, 2.9, 360, 0.8, 12, 3, 0.7, guard_break=True),
       anim=spin_strike(WIND, P(torso=(-10, 0, 22, 0), right_arm=(0, 0, 80, 0), left_arm=(0, 0, -80, 0),
                                right_leg=(-15, 0, 92, 0), left_leg=(10, 0, -14, 46)),
                        WIND(torso=(20, 10, 0, 12)), turn=-360, lift=6.0), nxt={"light": "gust_jab"})
s.move("wind_counter", (1, 2, 5, 2), H(4, 16, 2.8, 140, 0.5, 11, 3, 0.55), punch(WIND, "right", twist=50, lean=22),
       {"light": "gust_cross"}, effects={"recoil": 0.4})
s.move("falling_leaf", (3, 2, 5, 3), H(3.5, 12, 2.9, 90, 0.5, 9, 2, 0.45, lunge=0.4),
       (WIND(torso=(24, 0, 0, 16), right_leg=(-70, 0, 0, 130), left_leg=(-50, 0, 0, 115)),
        WIND(torso=(-14, 0, 0, -2), right_leg=(-72, 0, 0, 0), left_leg=(25, 0, 0, 95), right_arm=(40, 0, 45, 20)).add(body=dict(rot=(-12, 0, 0)))),
       {"light": "gust_jab"}, weight=False)
s.write({"light": "gust_jab", "heavy": "passing_strike", "launcher": "updraft_kick", "forward_light": "swallow_dive",
         "forward_heavy": "hurricane_dash", "back_light": "hit_and_fade", "back_heavy": "tornado_heel"},
        "wind_counter",
        {"footwork": "bouncing", "rhythm": "hit_and_run", "move_speed": 1.25,
         "guard": {"type": "evasive"},
         "dodge": {"kind": "hop", "speed": 1.15, "ticks": 9, "iframes": 7, "perfect": 5, "stamina": 0.6, "lift": 0.2}},
        {"required": True, "watch": 0.025, "scroll": 4}, npc_weight=1)

# ====================================================================== 7. Shadow
# Low and bladed, one hand hidden behind the back. Feints (guard during your wind-up cancels it; the real
# blow then comes fast and slips through a guard), +60% damage from behind, a quick low slide.
SHADOW = P(torso=(20, 40, 0, 14), left_arm=(-62, -20, -10, 92), right_arm=(30, 0, 26, 62),
           body=dict(pos=(0, -3, 0)), **legs((-28, 8, 50), (26, 14, 48)))
s = style(Style("shadow", SHADOW, 26, dict(torso=(2, -8, 0, 2), left_arm=(-4, 4, 0, 6)), ("aggressive", "defensive", "low")))
s.move("shade_jab", (2, 2, 4, 1), H(2.5, 8, 2.5, 90, 0.1, 6, 1, 0.25, lunge=0.25), punch(SHADOW, "left", twist=48, lean=22),
       {"light": "night_hook", "heavy": "hidden_palm"}, {"low": "ankle_cut"})
s.move("night_hook", (2, 2, 5, 2), H(3, 10, 2.5, 120, 0.15, 7, 1, 0.3), hook(SHADOW, "right", twist=40, lean=18),
       {"light": "veil_elbow", "heavy": "rising_shade"})
s.move("veil_elbow", (3, 2, 5, 2), H(3.5, 12, 2.3, 110, 0.2, 8, 2, 0.35), elbow(SHADOW, "left", twist=44, lean=24),
       {"light": "eclipse_knee", "heavy": "hidden_palm"})
s.move("eclipse_knee", (3, 2, 6, 3), H(4, 14, 2.3, 90, 0.4, 9, 2, 0.4, lunge=0.35), knee(SHADOW, "right"),
       {"light": "shadow_reap", "heavy": "shadow_reap"})
s.move("shadow_reap", (5, 3, 8, 5), H(5.5, 22, 2.8, 160, 1.1, 12, 3, 0.7, ender="splat"),
       anim=spin_strike(SHADOW, SHADOW(torso=(10, -10, 0, 6), right_arm=(-92, -62, 0, 10), left_arm=(-40, 0, -20, 90)),
                        SHADOW(torso=(16, 60, 0, 10), right_arm=(-60, 60, 0, 110)), turn=-360, lift=0.5), nxt={"light": "shade_jab"})
s.move("hidden_palm", (5, 2, 8, 5), H(5.5, 26, 2.6, 80, 0.5, 11, 3, 0.6, lunge=0.4, guard_break=True),
       (SHADOW(torso=(16, 70, 0, 12), right_arm=(40, 20, 30, 70)).add(body=dict(pos=(0, -1, 1.5))),
        SHADOW(torso=(24, -30, 0, 10), right_arm=(-90, 30, 0, 0), left_arm=(-20, -20, -30, 60)).add(body=dict(pos=(0, -0.5, -4)))),
       {"light": "shade_jab"}, {"aggressive": "shadow_reap", "defensive": "fade_strike"})
s.move("rising_shade", (4, 3, 8, 4), H(4.5, 16, 2.4, 90, 0.2, 16, 3, 0.6, lift=0.7, ender="launch"), uppercut(SHADOW, "right", twist=30),
       {"light": "shade_jab"})
s.move("phantom_step", (3, 2, 6, 3), H(3.5, 12, 2.9, 80, 0.3, 8, 2, 0.4, lunge=0.9), punch(SHADOW, "left", twist=56, lean=30, drive=-8),
       {"light": "night_hook"})
s.move("phantom_lunge", (5, 2, 8, 5), H(5.5, 20, 3.1, 80, 1.1, 12, 3, 0.7, lunge=1.0, ender="splat"),
       (SHADOW(torso=(26, 70, 0, 16), right_arm=(40, 20, 30, 70)).add(body=dict(pos=(0, -1.5, 1))),
        SHADOW(torso=(30, -40, 0, 12), right_arm=(-92, 40, 0, 0), left_arm=(10, 0, -30, 40)).add(body=dict(pos=(0, -1, -9)))),
       {"light": "shade_jab"}, effects={"backstab": 1.2})
s.move("fade_strike", (2, 2, 5, 2), H(3, 10, 2.5, 90, 0.3, 7, 1, 0.3), punch(SHADOW, "left", twist=40, lean=14),
       {"light": "shade_jab"}, effects={"recoil": 0.6})
s.move("ghost_sweep", (4, 3, 7, 4), H(3.5, 16, 2.7, 180, 0.2, 14, 2, 0.4, knockdown=True), low_sweep(SHADOW, "right", depth=5),
       {"light": "shade_jab"}, weight=False)
s.move("shadow_counter", (1, 2, 6, 2), H(3.5, 16, 2.7, 140, 0.4, 12, 3, 0.55), punch(SHADOW, "right", twist=56, lean=26),
       {"light": "night_hook"})
s.move("ankle_cut", (2, 2, 4, 2), H(2.5, 10, 2.5, 100, 0.05, 8, 1, 0.25), low_sweep(SHADOW, "left", depth=4),
       {"light": "night_hook"}, weight=False)
s.write({"light": "shade_jab", "heavy": "hidden_palm", "launcher": "rising_shade", "forward_light": "phantom_step",
         "forward_heavy": "phantom_lunge", "back_light": "fade_strike", "back_heavy": "ghost_sweep"},
        "shadow_counter",
        {"footwork": "bladed", "rhythm": "deceptive", "move_speed": 1.1,
         "guard": {"type": "standard", "keep": 0.28},
         "dodge": {"kind": "slide", "speed": 1.3, "ticks": 7, "iframes": 5, "stamina": 0.9, "lift": 0.0},
         "strike": {"backstab": 1.6},
         "feint": {"cost": 6, "window": 14, "windup": 1, "pierce": 0.6, "posture": 1.6}},
        {"required": True, "watch": 0.015, "scroll": 3}, npc_weight=1, npc={"min_class": "C"})

# ====================================================================== 8. Leaf Hurricane (a Strong Fist variant)
# Strong Fist's bouncing cousin: a palm out, the other hand behind the back, and whirling kicks that build
# momentum (+7% per hit in a string, up to +35%). Taught by Might Guy (taijutsu 22).
HURR = P(torso=(4, -30, 0, 2), right_arm=(-96, 30, 0, 6), left_arm=(42, 0, 30, 80), body=dict(pos=(0, -2.4, 0)),
         **legs((-30, 12, 42), (28, 18, 36), side="right"))
s = style(Style("leaf_hurricane", HURR, 16, dict(body=dict(pos=(0, -1.0, 0)), right_leg=(0, 0, 0, 10), left_leg=(0, 0, 0, 10),
                                                 right_arm=(-3, 0, 0, 4)), ("aggressive", "air"), lead="right"))
s.move("hurricane_jab", (2, 2, 3, 1), H(2.5, 8, 2.6, 90, 0.1, 6, 1, 0.25), punch(HURR, "right", twist=50, lean=10),
       {"light": "whirlwind_low", "heavy": "leaf_hurricane"}, {"air": "air_hurricane"})
s.move("whirlwind_low", (3, 3, 5, 2), H(3, 12, 2.8, 200, 0.2, 8, 2, 0.35),
       anim=spin_strike(HURR, P(torso=(26, 0, -10, 12), right_arm=(10, 0, 60, 20), left_arm=(-30, 0, -50, 40),
                                right_leg=(-30, 0, 70, 0), left_leg=(-50, 0, -10, 100), body=dict(pos=(0, -4, 0))),
                        HURR(torso=(16, 20, 0, 8)), turn=-360), nxt={"light": "whirlwind_mid", "heavy": "rising_hurricane"})
s.move("whirlwind_mid", (3, 3, 5, 2), H(3.5, 14, 2.9, 240, 0.3, 8, 2, 0.4),
       anim=spin_strike(HURR, P(torso=(-8, 0, -22, 0), right_arm=(0, 0, 80, 0), left_arm=(0, 0, -80, 0),
                                right_leg=(-15, 0, 92, 0), left_leg=(10, 0, -14, 46)),
                        HURR(torso=(16, 20, 0, 8)), turn=-360, lift=2.0), nxt={"light": "whirlwind_high", "heavy": "leaf_hurricane_drop"})
s.move("whirlwind_high", (4, 3, 6, 3), H(4, 16, 3.0, 280, 0.4, 9, 2, 0.45, lift=0.2),
       anim=spin_strike(HURR, P(torso=(-16, 0, -34, 0), right_arm=(10, 0, 90, 0), left_arm=(10, 0, -90, 0),
                                right_leg=(-40, 0, 110, 0), left_leg=(10, 0, -14, 40)),
                        HURR(torso=(16, 20, 0, 8)), turn=-360, lift=5.0), nxt={"light": "leaf_tornado", "heavy": "leaf_hurricane_drop"})
s.move("leaf_tornado", (5, 4, 8, 5), H(5.5, 22, 3.0, 360, 0.6, 16, 3, 0.8, lift=0.8, ender="launch"),
       anim=spin_strike(HURR, P(torso=(-24, 0, -30, -4), right_arm=(-160, 0, 40, 10), left_arm=(10, 0, -90, 0),
                                right_leg=(-60, 0, 100, 0), left_leg=(-60, 0, -10, 110)),
                        HURR(torso=(28, 0, 0, 14), right_leg=(-30, 0, 4, 85), left_leg=(-25, 0, -4, 80)), turn=-720, lift=9.0),
       nxt={"light": "hurricane_jab"})
s.move("leaf_hurricane", (5, 4, 8, 5), H(6, 24, 3.0, 360, 1.0, 12, 3, 0.75, ender="splat"),
       anim=spin_strike(HURR, P(torso=(-8, 0, -22, 0), right_arm=(0, 0, 80, 0), left_arm=(0, 0, -80, 0),
                                right_leg=(-15, 0, 95, 0), left_leg=(10, 0, -14, 46)),
                        HURR(torso=(20, 10, 0, 12), right_arm=(-40, 0, 40, 30)), turn=-360, lift=7.0),
       nxt={"light": "hurricane_jab"}, variants={"aggressive": "leaf_hurricane_drop"})
s.move("leaf_hurricane_drop", (6, 3, 9, 6), H(7, 34, 2.9, 80, 0.4, 16, 4, 0.9, guard_break=True, knockdown=True, ender="knockdown"),
       axe_kick(HURR, "right"), {"light": "hurricane_jab"}, weight=False)
s.move("rising_hurricane", (5, 3, 8, 4), H(4.5, 18, 2.8, 120, 0.1, 16, 3, 0.65, lift=0.8, ender="launch"),
       front_kick(HURR, "right", height=172, lean=-28), {"light": "hurricane_jab"}, weight=False)
s.move("leaf_dash_kick", (3, 2, 6, 3), H(4, 14, 3.1, 80, 0.6, 9, 2, 0.45, lunge=0.8),
       front_kick(HURR, "right", height=92, lean=-14), {"light": "whirlwind_low"}, weight=False)
s.move("leaf_hurricane_dive", (5, 3, 9, 6), H(6.5, 24, 3.3, 200, 1.2, 12, 4, 0.8, lunge=1.0, ender="splat"),
       anim=spin_strike(HURR, P(torso=(-14, 0, -20, -2), right_arm=(55, 0, 35, 20), left_arm=(-45, 0, -25, 50),
                                right_leg=(-92, 0, 30, 0), left_leg=(30, 0, 0, 110)),
                        HURR(torso=(26, -10, 0, 14), right_leg=(-35, 0, 4, 90), left_leg=(-35, 0, -4, 90)), turn=-360, lift=7.0),
       nxt={"light": "hurricane_jab"})
s.move("hurricane_back_kick", (3, 2, 6, 3), H(4, 14, 2.9, 80, 0.7, 9, 2, 0.45), back_kick(HURR, "right"),
       {"light": "hurricane_jab"}, weight=False, end=HURR.add(body=dict(rot=(0, 360, 0))))
s.move("hurricane_counter", (1, 2, 6, 2), H(4.5, 18, 2.9, 140, 0.8, 12, 3, 0.6), roundhouse(HURR, "right", height=100),
       {"light": "whirlwind_low"}, weight=False)
s.move("air_hurricane", (3, 3, 6, 3), H(4, 14, 2.9, 360, 0.5, 9, 2, 0.45),
       anim=spin_strike(P(torso=(10, 0, 0, 8), right_arm=(-30, 0, 30, 40), left_arm=(-30, 0, -30, 40), right_leg=(-40, 0, 0, 80),
                          left_leg=(-20, 0, 0, 60)),
                        P(torso=(-10, 0, -20, 0), right_arm=(0, 0, 80, 0), left_arm=(0, 0, -80, 0), right_leg=(-15, 0, 95, 0),
                          left_leg=(-40, 0, -10, 90)),
                        P(torso=(14, 10, 0, 8), right_leg=(-60, 0, 0, 110), left_leg=(-40, 0, 0, 90)), turn=-360),
       nxt={"light": "hurricane_jab"})
s.write({"light": "hurricane_jab", "heavy": "leaf_hurricane", "launcher": "rising_hurricane", "forward_light": "leaf_dash_kick",
         "forward_heavy": "leaf_hurricane_dive", "back_light": "hurricane_back_kick", "back_heavy": "leaf_hurricane_drop"},
        "hurricane_counter",
        {"footwork": "bouncing", "rhythm": "spinning", "move_speed": 1.15,
         "guard": {"type": "standard", "parry_window": 1.2},
         "dodge": {"kind": "flip", "speed": 1.0, "ticks": 10, "iframes": 6, "lift": 0.25},
         "strike": {"momentum": 0.07, "momentum_max": 0.35, "aerial": 1.15}},
        {"required": True, "watch": 0.0, "scroll": 0}, npc_weight=1, taijutsu=22, npc={"min_class": "B"})

# ====================================================================== 9. Sumo
# Very wide and low, palms forward. Open-hand thrusts (tsuppari) that shove, a belt grab that leads to
# lifts and throws, an armoured guard (a quarter of all posture shrugged off) and charges that keep going.
SUMO = P(torso=(22, 0, 0, 14), left_arm=(-70, -16, -10, 46), right_arm=(-70, 16, 10, 46),
         left_leg=(-6, 0, -32, 72), right_leg=(6, 0, 32, 72), body=dict(pos=(0, -4.6, 0)))
SUMO_HOLD = SUMO(torso=(16, 0, 0, 10), right_arm=(-80, -14, 8, 80), left_arm=(-80, 14, -8, 80))
s = style(Style("sumo", SUMO, 40, dict(body=dict(pos=(0, -0.6, 0)), torso=(2, 0, 0, 2)), ("aggressive", "defensive")))
s.move("tsuppari", (2, 2, 4, 2), H(3, 12, 2.4, 80, 0.35, 7, 1, 0.3, lunge=0.2), punch(SUMO, "right", twist=24, lean=26, drive=-2),
       {"light": "tsuppari_2", "heavy": "harite", "grab": "belt_grab"}, weight=False)
s.move("tsuppari_2", (2, 2, 4, 2), H(3, 12, 2.4, 80, 0.35, 7, 1, 0.3, lunge=0.2), punch(SUMO, "left", twist=24, lean=26, drive=-2),
       {"light": "tsuppari_rush", "heavy": "harite", "grab": "belt_grab"}, weight=False)
s.move("tsuppari_rush", (3, 2, 5, 3), H(4, 16, 2.5, 90, 0.5, 8, 2, 0.4, lunge=0.3), double_palm(SUMO, height=-86, lean=30, drive=-3),
       {"light": "belt_grab", "heavy": "oshidashi", "grab": "belt_grab"}, weight=False)
s.move("belt_grab", (3, 3, 6, 2), H(0, 0, 2.2, 90, 0, 0, 1, 0.2, clinch="grab"), grab(SUMO),
       {"light": "belt_lift", "heavy": "belt_throw", "back_heavy": "sumo_crush"}, weight=False)
s.move("belt_lift", (3, 2, 5, 2), H(3, 14, 2.2, 90, 0, 8, 2, 0.4, clinch="hold"),
       (SUMO_HOLD(torso=(30, 0, 0, 18)).add(body=dict(pos=(0, -1, 0))), SUMO_HOLD(torso=(-10, 0, 0, -4), right_arm=(-120, -14, 8, 60),
                                                                               left_arm=(-120, 14, -8, 60)).add(body=dict(pos=(0, 2.5, 0)))),
       {"light": "belt_lift", "heavy": "belt_throw", "back_heavy": "sumo_crush"}, weight=False, start=SUMO_HOLD)
s.move("belt_throw", (5, 3, 9, 6), H(6, 28, 2.4, 120, 1.0, 16, 4, 0.9, clinch="release", knockdown=True, ender="knockdown"),
       (SUMO_HOLD(torso=(10, 35, 0, 12), right_arm=(-95, 10, 20, 70), left_arm=(-95, 30, -10, 70)).add(body=dict(pos=(0, -2, 0))),
        SUMO_HOLD(torso=(34, -50, 0, 18), right_arm=(-160, -20, 20, 20), left_arm=(-140, 0, -20, 20)).add(body=dict(rot=(0, -60, 0), pos=(0, -1.5, 0)))),
       {"light": "tsuppari"}, weight=False, start=SUMO_HOLD)
s.move("sumo_crush", (5, 3, 9, 6), H(6.5, 30, 2.4, 120, 1.4, 14, 4, 0.9, clinch="release", ender="splat"),
       (SUMO_HOLD(torso=(6, 0, 0, 4)).add(body=dict(pos=(0, 0, 1.5))), SUMO_HOLD(torso=(40, 0, 0, 22), right_arm=(-60, -10, 10, 30),
                                                                                left_arm=(-60, 10, -10, 30)).add(body=dict(pos=(0, -2.5, -6)))),
       {"light": "tsuppari"}, weight=False, start=SUMO_HOLD, effects={"armour": 0.4})
s.move("harite", (4, 2, 7, 4), H(5, 20, 2.5, 120, 1.0, 10, 3, 0.6, ender="splat"),
       (SUMO(torso=(14, 50, 0, 10), right_arm=(-70, 70, 20, 60)), SUMO(torso=(14, -40, 0, 6), right_arm=(-90, -60, 0, 10))),
       {"light": "tsuppari"}, {"aggressive": "oshidashi", "defensive": "sumo_brace"}, weight=False)
s.move("oshidashi", (5, 3, 8, 5), H(5.5, 34, 2.6, 100, 1.6, 12, 4, 0.8, lunge=0.7, guard_break=True),
       double_palm(SUMO, height=-80, lean=36, drive=-7), {"light": "tsuppari"}, weight=False, effects={"armour": 0.5})
s.move("tachiai_rise", (4, 3, 8, 4), H(5, 20, 2.4, 100, 0.3, 16, 3, 0.7, lift=0.7, lunge=0.3, ender="launch"),
       (SUMO(torso=(40, 0, 0, 24), right_arm=(-10, 0, 10, 60), left_arm=(-10, 0, -10, 60)).add(body=dict(pos=(0, -1.5, 0))),
        SUMO(torso=(-6, 0, 0, -2), right_arm=(-150, -10, 0, 20), left_arm=(-150, 10, 0, 20)).add(body=dict(pos=(0, 3, -2)))),
       {"light": "tsuppari"}, weight=False)
s.move("tachiai", (4, 2, 7, 4), H(5, 24, 2.8, 100, 1.2, 10, 3, 0.7, lunge=1.0), shoulder(SUMO, "left", lean=34, drive=-8),
       {"light": "tsuppari", "grab": "belt_grab"}, weight=False, effects={"armour": 0.5})
s.move("tachiai_crash", (5, 3, 8, 5), H(6.5, 30, 2.9, 100, 1.5, 12, 4, 0.9, lunge=1.1, ender="splat"),
       double_palm(SUMO, height=-70, lean=44, drive=-10), {"light": "tsuppari"}, weight=False, effects={"armour": 0.6})
s.move("sumo_brace", (3, 2, 6, 3), H(3, 18, 2.3, 110, 0.9, 8, 2, 0.45), double_palm(SUMO, height=-60, lean=14, drive=-1.5),
       {"light": "tsuppari", "grab": "belt_grab"}, weight=False, effects={"armour": 0.6})
s.move("shiko_stomp", (6, 3, 9, 6), H(5.5, 28, 2.6, 360, 0.5, 18, 4, 1.0, knockdown=True),
       (SUMO(torso=(4, 0, -22, 2), right_leg=(-20, 0, 60, 10), right_arm=(-20, 0, 20, 20), left_arm=(-20, 0, -20, 20)).add(body=dict(pos=(0, 0, 0))),
        SUMO(torso=(30, 0, 0, 18), right_leg=(-6, 0, 34, 76)).add(body=dict(pos=(0, -5.5, 0)))),
       {"light": "tsuppari"}, weight=False, effects={"armour": 0.4})
s.move("sumo_counter", (1, 2, 6, 2), H(4.5, 22, 2.5, 130, 1.0, 12, 3, 0.6),
       (SUMO(torso=(14, 50, 0, 10), right_arm=(-70, 70, 20, 60)), SUMO(torso=(14, -40, 0, 6), right_arm=(-90, -60, 0, 10))),
       {"light": "tsuppari", "grab": "belt_grab"}, weight=False)
s.write({"light": "tsuppari", "heavy": "harite", "launcher": "tachiai_rise", "forward_light": "tachiai",
         "forward_heavy": "tachiai_crash", "back_light": "sumo_brace", "back_heavy": "shiko_stomp", "grab": "belt_grab"},
        "sumo_counter",
        {"footwork": "wide", "rhythm": "grapple", "move_speed": 0.92,
         "guard": {"type": "armoured", "keep": 0.18, "posture": 0.6, "armour": 0.25, "speed": 0.4},
         "dodge": {"kind": "step", "speed": 0.6, "ticks": 9, "iframes": 4, "stamina": 1.2, "lift": 0.04}},
        {"required": True, "watch": 0.025, "scroll": 3}, npc_weight=1, npc={"clans": {"akimichi": 8}})

# ====================================================================== 10. Monkey
# A deep crouch, knuckles near the ground, twitchy. Acrobatic: swipes, low sweeps that floor, cartwheels and
# backflip kicks; flips for a dodge; +20% from the air; the spinning sweep slides under a guard.
MONKEY = P(torso=(34, 20, 0, 24), left_arm=(-20, 0, -22, 42), right_arm=(-10, 0, 26, 32),
           left_leg=(-40, 0, -14, 92), right_leg=(-20, 0, 14, 86), body=dict(pos=(0, -5, 0)))
s = style(Style("monkey", MONKEY, 14, dict(torso=(0, -12, 0, 0), left_arm=(-10, 0, 0, 12), right_arm=(-6, 0, 0, 8)),
                ("aggressive", "low", "air")))
s.move("swipe", (2, 2, 4, 1), H(2.5, 8, 2.5, 130, 0.1, 6, 1, 0.25, lunge=0.3),
       (MONKEY(torso=(30, 50, 0, 20), left_arm=(-60, -60, 0, 90)), MONKEY(torso=(30, -20, 0, 18), left_arm=(-85, 60, 0, 30)).add(body=dict(pos=(0, 0, -2)))),
       {"light": "swipe_2", "heavy": "monkey_leap"}, {"low": "tail_sweep"})
s.move("swipe_2", (2, 2, 4, 1), H(2.5, 8, 2.5, 130, 0.1, 6, 1, 0.25, lunge=0.3),
       (MONKEY(torso=(30, -50, 0, 20), right_arm=(-60, 60, 0, 90)), MONKEY(torso=(30, 30, 0, 18), right_arm=(-85, -60, 0, 30)).add(body=dict(pos=(0, 0, -2)))),
       {"light": "tail_sweep", "heavy": "rising_monkey"})
s.move("tail_sweep", (3, 3, 5, 3), H(3, 12, 2.7, 200, 0.2, 10, 2, 0.35), low_sweep(MONKEY, "right", depth=6.5),
       {"light": "cartwheel_kick", "heavy": "spinning_sweep"}, weight=False)
s.move("cartwheel_kick", (4, 3, 6, 3), H(4.5, 16, 2.9, 160, 0.5, 9, 2, 0.45),
       anim=spin_strike(MONKEY, P(torso=(0, 0, 0, 0), right_arm=(-170, 0, 20, 0), left_arm=(-170, 0, -20, 0),
                                  right_leg=(-10, 0, 60, 0), left_leg=(-10, 0, -60, 0), body=dict(pos=(0, 5, 0))),
                        MONKEY(torso=(40, 0, -20, 20)), turn=360, lift=2.0), nxt={"light": "backflip_kick", "heavy": "spinning_sweep"})
s.move("backflip_kick", (5, 3, 8, 5), H(5.5, 20, 2.8, 100, 0.2, 16, 3, 0.75, lift=0.8, ender="launch"),
       anim=spin_strike(MONKEY, P(torso=(-24, 0, 0, -5), right_arm=(-165, 0, 25, 0), left_arm=(-165, 0, -25, 0),
                                  right_leg=(-160, 0, 0, 0), left_leg=(-30, 0, 0, 100)),
                        MONKEY(torso=(40, 0, 0, 24), right_leg=(-50, 0, 6, 110), left_leg=(-50, 0, -6, 110)),
                        turn=-360, lift=10.0, mid_turn=0.32, flip=True), nxt={"light": "swipe"})
s.move("monkey_leap", (5, 2, 8, 5), H(5.5, 20, 3.1, 90, 1.1, 12, 3, 0.7, lunge=0.9, ender="splat"),
       anim=leap_strike(MONKEY, MONKEY(torso=(44, 0, 0, 28), left_leg=(-60, 0, -10, 120), right_leg=(-60, 0, 10, 120)),
                        P(torso=(-6, 0, 0, 4), right_arm=(-150, 0, 30, 10), left_arm=(-150, 0, -30, 10),
                          right_leg=(-112, 0, 6, 128), left_leg=(-100, 0, -6, 120), body=dict(rot=(-6, 0, 0))), rise=8, drive=-11),
       nxt={"light": "swipe"}, variants={"aggressive": "backflip_kick"})
s.move("rising_monkey", (4, 3, 7, 4), H(4, 14, 2.6, 110, 0.1, 16, 3, 0.6, lift=0.75, ender="launch"), uppercut(MONKEY, "left", twist=26),
       {"light": "swipe"})
s.move("monkey_pounce", (3, 2, 6, 3), H(3.5, 12, 3.0, 100, 0.4, 8, 2, 0.4, lunge=1.0),
       anim=leap_strike(MONKEY, MONKEY(torso=(40, 0, 0, 26)),
                        P(torso=(20, 0, 0, 10), right_arm=(-110, -20, 10, 20), left_arm=(-110, 20, -10, 20), right_leg=(-40, 0, 6, 90),
                          left_leg=(-30, 0, -6, 80)), rise=4, drive=-9), nxt={"light": "swipe_2"})
s.move("scratch_retreat", (2, 2, 4, 2), H(2.5, 8, 2.5, 120, 0.2, 6, 1, 0.3),
       (MONKEY(torso=(30, 50, 0, 20), left_arm=(-60, -60, 0, 90)), MONKEY(torso=(24, -20, 0, 14), left_arm=(-85, 60, 0, 30))),
       {"light": "swipe"}, effects={"recoil": 0.6})
s.move("spinning_sweep", (4, 3, 7, 4), H(4, 22, 2.8, 360, 0.3, 14, 3, 0.55, knockdown=True, guard_break=True),
       anim=spin_strike(MONKEY, P(torso=(34, 0, 0, 16), left_leg=(-60, 0, -8, 115), right_leg=(-10, 0, 80, 0),
                                  right_arm=(10, 0, 40, 30), left_arm=(-30, 0, -30, 60), body=dict(pos=(0, -6.5, 0))),
                        MONKEY(torso=(34, 30, 0, 22)), turn=-360), nxt={"light": "swipe"})
s.move("monkey_counter", (1, 2, 5, 2), H(4, 16, 2.7, 140, 0.4, 11, 3, 0.55),
       (MONKEY(torso=(30, -50, 0, 20), right_arm=(-60, 60, 0, 90)), MONKEY(torso=(26, 40, 0, 16), right_arm=(-90, -60, 0, 20))),
       {"light": "swipe_2"})
s.move("air_double_kick", (3, 2, 5, 3), H(4, 14, 2.9, 90, 0.6, 9, 2, 0.45, lunge=0.4),
       (MONKEY(torso=(24, 0, 0, 16), right_leg=(-70, 0, 0, 130), left_leg=(-70, 0, 0, 130)),
        MONKEY(torso=(-14, 0, 0, -2), right_leg=(-84, 0, 6, 0), left_leg=(-80, 0, -6, 0), right_arm=(40, 0, 45, 20),
               left_arm=(40, 0, -45, 20)).add(body=dict(rot=(-20, 0, 0), pos=(0, 4, 0)))),
       {"light": "swipe"}, weight=False)
s.move("air_swipe", (2, 2, 4, 2), H(3, 10, 2.6, 130, 0.2, 7, 1, 0.3),
       (MONKEY(torso=(10, 50, 0, 10), left_arm=(-90, -60, 0, 90)), MONKEY(torso=(10, -20, 0, 8), left_arm=(-95, 60, 0, 30))),
       {"light": "air_double_kick"}, weight=False)
# The air sub-stance turns the swipe into an air swipe (its own animation, light route into the double kick).
s.moves["monkey/swipe"]["variants"]["air"] = "noirm:monkey/air_swipe"
s.write({"light": "swipe", "heavy": "monkey_leap", "launcher": "rising_monkey", "forward_light": "monkey_pounce",
         "forward_heavy": "monkey_leap", "back_light": "scratch_retreat", "back_heavy": "spinning_sweep"},
        "monkey_counter",
        {"footwork": "low", "rhythm": "acrobatic", "move_speed": 1.15,
         "guard": {"type": "standard", "parry_window": 1.1, "keep": 0.3, "speed": 0.6},
         "dodge": {"kind": "flip", "speed": 1.05, "ticks": 11, "iframes": 7, "lift": 0.32},
         "strike": {"aerial": 1.2}},
        {"required": True, "watch": 0.025, "scroll": 4}, npc_weight=1)

# ====================================================================== weapon styles
# The held weapon renders in the right hand; these are alternatives to a weapon's own moves, picked per weapon.

# 11. Iai (katana, sword): crouched low, the blade held back and low by the hip. A counter stance: a parry
# window twice as wide, answered at once with a drawing cut. Draw cuts slip through guards.
IAI = P(torso=(18, -32, 0, 12), right_arm=(12, 24, 20, 30), left_arm=(-62, 22, -10, 72), body=dict(pos=(0, -3, 0)),
        **legs((-32, 10, 52), (30, 16, 46)))
DRAW_W = IAI(torso=(22, -50, 0, 14), right_arm=(20, 50, 10, 40))


def draw_cut(high=-120, end_y=-70, lean=18):
    return (DRAW_W, IAI(torso=(lean, 40, 0, 6), right_arm=(high, end_y, 0, 4), left_arm=(10, 0, -30, 40)).add(body=dict(pos=(0, -1, -3))))


s = style(Style("blade_iai", IAI, 48, dict(torso=(1, 0, 0, 1), body=dict(pos=(0, -0.3, 0))), ("aggressive", "defensive"),
                lead="left", weapons=["katana", "sword"]))
s.move("draw_cut", (3, 2, 6, 3), H(6, 18, 3.2, 140, 0.3, 9, 2, 0.45, lunge=0.4, pierce=0.2), draw_cut(),
       {"light": "return_cut", "heavy": "flash_draw"})
s.move("return_cut", (3, 2, 6, 3), H(5.5, 16, 3.2, 140, 0.3, 9, 2, 0.45, lunge=0.3),
       (IAI(torso=(10, 50, 0, 4), right_arm=(-130, -70, 0, 10)), IAI(torso=(18, -40, 0, 10), right_arm=(-70, 60, 0, 8)).add(body=dict(pos=(0, -1, -2)))),
       {"light": "iai_thrust", "heavy": "rising_draw"})
s.move("iai_thrust", (4, 2, 7, 4), H(6.5, 20, 3.6, 40, 0.6, 11, 3, 0.55, lunge=0.5, pierce=0.3),
       (IAI(torso=(6, 20, 0, 4), right_arm=(-50, 30, 20, 100)), IAI(torso=(20, -40, 0, 6), right_arm=(-88, 36, 0, 0)).add(body=dict(pos=(0, -2, -6)))),
       {"light": "sheathing_cut", "heavy": "sheathing_cut"})
s.move("sheathing_cut", (6, 3, 9, 6), H(9, 30, 3.3, 120, 0.8, 16, 4, 0.85, lunge=0.4, guard_break=True, knockdown=True, ender="knockdown"),
       (IAI(torso=(-10, 20, 0, -4), right_arm=(-170, 30, 20, 10)).add(body=dict(pos=(0, 1, 0))),
        IAI(torso=(32, -20, 0, 16), right_arm=(-40, -30, 0, 4)).add(body=dict(pos=(0, -3, -3)))),
       {"light": "draw_cut"})
s.move("flash_draw", (7, 2, 9, 6), H(9, 26, 3.6, 150, 0.7, 14, 4, 0.85, lunge=1.0, pierce=0.4, ender="splat"), draw_cut(high=-100, end_y=-90, lean=26),
       {"light": "draw_cut"}, {"aggressive": "sheathing_cut"})
s.move("rising_draw", (5, 3, 8, 5), H(6, 18, 3.1, 110, 0.1, 18, 3, 0.65, lift=0.8, ender="launch"),
       (DRAW_W(torso=(30, -40, 0, 16)).add(body=dict(pos=(0, -1.5, 0))), IAI(torso=(-14, 10, 0, -4), right_arm=(-175, -10, 0, 4)).add(body=dict(pos=(0, 3.5, -2)))),
       {"light": "draw_cut"})
s.move("dash_draw", (6, 2, 9, 6), H(8, 22, 3.6, 80, 0.6, 12, 3, 0.75, lunge=1.2, pierce=0.35), draw_cut(high=-95, end_y=-80, lean=30),
       {"light": "return_cut"})
s.move("whirl_cut", (6, 3, 9, 6), H(7, 24, 3.4, 360, 1.0, 14, 4, 0.8),
       anim=spin_strike(IAI, IAI(torso=(10, 0, 0, 4), right_arm=(-88, 0, 34, 0)), DRAW_W, turn=-360), nxt={"light": "draw_cut"})
s.move("iai_counter", (1, 2, 8, 4), H(8, 28, 3.3, 150, 0.8, 14, 4, 0.8, pierce=0.3), draw_cut(high=-110, end_y=-80, lean=22),
       {"light": "return_cut"})
s.write({"light": "draw_cut", "heavy": "flash_draw", "launcher": "rising_draw", "forward_light": "dash_draw",
         "forward_heavy": "dash_draw", "back_heavy": "whirl_cut"},
        "iai_counter",
        {"footwork": "low", "rhythm": "quick_draw", "move_speed": 0.95,
         "guard": {"type": "counter", "parry_window": 2.0, "keep": 0.3, "speed": 0.45},
         "dodge": {"kind": "step", "speed": 0.85}},
        {"required": True, "watch": 0.015, "scroll": 2}, npc_weight=1, npc={"min_class": "C"})

# 12. Blade Dance (sword, tantō, kunai): upright and light, the blade held high; flowing spinning cuts that
# build momentum, a flip for a dodge, quick on the feet; cuts from behind bite deeper.
DANCE = P(torso=(6, -24, 0, 2), right_arm=(-140, 30, 20, 40), left_arm=(-40, 0, -30, 60), body=dict(pos=(0, -1, 0)),
          **legs((-22, 8, 30), (22, 12, 28), side="right"))
s = style(Style("blade_dance", DANCE, 20, dict(torso=(0, 8, 0, 0), right_arm=(6, 0, 0, 6), body=dict(pos=(0, -0.8, 0))),
                ("aggressive", "air"), lead="right", weapons=["sword", "tanto", "kunai"]))
s.move("dance_cut", (2, 2, 5, 2), H(4, 10, 2.9, 130, 0.2, 7, 1, 0.3, lunge=0.3),
       (DANCE(torso=(0, 30, 0, 0), right_arm=(-160, 50, 20, 20)), DANCE(torso=(16, -40, 0, 8), right_arm=(-60, -50, 0, 6)).add(body=dict(pos=(0, -1, -2)))),
       {"light": "dance_spin", "heavy": "dance_crescent"})
s.move("dance_spin", (3, 3, 5, 2), H(4.5, 12, 3.0, 220, 0.3, 8, 2, 0.4),
       anim=spin_strike(DANCE, DANCE(torso=(8, 0, 0, 4), right_arm=(-88, 0, 40, 0), left_arm=(-60, 0, -40, 20)),
                        DANCE(torso=(10, 40, 0, 4)), turn=-360), nxt={"light": "dance_rising", "heavy": "dance_crescent"})
s.move("dance_rising", (3, 2, 6, 3), H(4.5, 14, 2.9, 110, 0.1, 14, 2, 0.5, lift=0.5),
       (DANCE(torso=(26, 10, 0, 14), right_arm=(10, 20, 25, 20)).add(body=dict(pos=(0, -2.5, 0))),
        DANCE(torso=(-14, -20, 0, -4), right_arm=(-178, -10, 0, 6)).add(body=dict(pos=(0, 3, -2)))),
       {"light": "dance_whirlwind"})
s.move("dance_whirlwind", (5, 4, 8, 5), H(6, 22, 3.1, 360, 1.0, 14, 3, 0.75, lift=0.3, ender="splat"),
       anim=spin_strike(DANCE, DANCE(torso=(-6, 0, -20, 0), right_arm=(-80, 0, 70, 0), left_arm=(0, 0, -70, 0)),
                        DANCE(torso=(20, 30, 0, 10)), turn=-720, lift=6.0), nxt={"light": "dance_cut"})
s.move("dance_crescent", (5, 3, 8, 5), H(6.5, 26, 3.1, 150, 0.6, 12, 3, 0.7, guard_break=True),
       (DANCE(torso=(-10, 40, 0, -4), right_arm=(-175, 40, 20, 10)).add(body=dict(pos=(0, 1, 0))),
        DANCE(torso=(30, -30, 0, 14), right_arm=(-40, -40, 0, 4)).add(body=dict(pos=(0, -2.5, -3)))),
       {"light": "dance_cut"}, {"aggressive": "dance_whirlwind"})
s.move("dance_lunge", (4, 2, 7, 4), H(5.5, 16, 3.3, 60, 0.5, 10, 3, 0.55, lunge=1.0, pierce=0.25),
       (DANCE(torso=(4, 20, 0, 4), right_arm=(-50, 30, 20, 100)), DANCE(torso=(20, -40, 0, 6), right_arm=(-90, 36, 0, 0)).add(body=dict(pos=(0, -2, -8)))),
       {"light": "dance_spin"})
s.move("dance_flip_cut", (5, 3, 8, 5), H(5.5, 18, 3.0, 120, 0.2, 16, 3, 0.7, lift=0.8, ender="launch"),
       anim=spin_strike(DANCE, P(torso=(-20, 0, 0, -4), right_arm=(-170, 0, 20, 0), left_arm=(-120, 0, -30, 20),
                                 right_leg=(-60, 0, 0, 60), left_leg=(-30, 0, 0, 90)),
                        DANCE(torso=(30, 0, 0, 16), right_leg=(-40, 0, 6, 100), left_leg=(-40, 0, -6, 100)),
                        turn=-360, lift=9.0, mid_turn=0.3, flip=True), nxt={"light": "dance_cut"})
s.move("dance_counter", (1, 2, 7, 3), H(6, 20, 3.0, 150, 0.6, 13, 3, 0.6), (DANCE, DANCE(torso=(16, -40, 0, 8), right_arm=(-60, -55, 0, 6))),
       {"light": "dance_spin"})
s.write({"light": "dance_cut", "heavy": "dance_crescent", "launcher": "dance_flip_cut", "forward_light": "dance_lunge",
         "forward_heavy": "dance_lunge", "back_heavy": "dance_whirlwind"},
        "dance_counter",
        {"footwork": "bouncing", "rhythm": "flowing", "move_speed": 1.12,
         "guard": {"type": "parry", "parry_window": 1.3, "keep": 0.3, "speed": 0.6},
         "dodge": {"kind": "flip", "speed": 1.0, "ticks": 10, "iframes": 6, "lift": 0.25},
         "strike": {"momentum": 0.06, "momentum_max": 0.24, "backstab": 1.3}},
        {"required": True, "watch": 0.02, "scroll": 3}, npc_weight=1)

# 13. Bō Wall (staff): square and high, the staff held across the body like a wall. A high guard (keeps out
# 88%, posture builds slower), long thrusts and sweeps that push people away, an overhead crash.
WALL = P(torso=(8, 0, 0, 4), right_arm=(-70, -20, 20, 40), left_arm=(-75, 30, -20, 45), body=dict(pos=(0, -2, 0)),
         left_leg=(-10, 0, -20, 40), right_leg=(10, 0, 20, 40))
s = style(Style("staff_wall", WALL, 40, dict(torso=(1, 0, 0, 1), body=dict(pos=(0, -0.4, 0))), ("aggressive", "defensive"),
                weapons=["staff"]))
s.move("wall_thrust", (3, 2, 6, 3), H(4.5, 14, 4.0, 40, 0.8, 9, 2, 0.4, lunge=0.3),
       (WALL(torso=(4, 30, 0, 4), right_arm=(-20, 20, 10, 80), left_arm=(-50, 40, 0, 80)).add(body=dict(pos=(0, 0, 1))),
        WALL(torso=(16, 5, 0, 4), right_arm=(-82, 10, 0, 5), left_arm=(-86, 25, 0, 10)).add(body=dict(pos=(0, -1, -5)))),
       {"light": "wall_sweep", "heavy": "wall_spin"})
s.move("wall_sweep", (4, 3, 7, 4), H(5, 16, 3.6, 180, 0.8, 9, 2, 0.45, lunge=0.2),
       (WALL(torso=(8, 55, 0, 6), right_arm=(-50, 60, 10, 20), left_arm=(-60, 60, -5, 50)),
        WALL(torso=(14, -50, 0, 6), right_arm=(-75, -55, 0, 10), left_arm=(-80, -35, 0, 30)).add(body=dict(pos=(0, -1, -2)))),
       {"light": "wall_return", "heavy": "wall_spin"})
s.move("wall_return", (4, 3, 7, 4), H(5, 16, 3.6, 180, 0.8, 9, 2, 0.45, lunge=0.2),
       (WALL(torso=(8, -55, 0, 6), right_arm=(-50, -60, 10, 20), left_arm=(-60, -40, -5, 50)),
        WALL(torso=(14, 50, 0, 6), right_arm=(-75, 55, 0, 10), left_arm=(-80, 65, 0, 30)).add(body=dict(pos=(0, -1, -2)))),
       {"light": "wall_crash", "heavy": "wall_crash"})
s.move("wall_crash", (6, 3, 9, 6), H(8, 34, 3.8, 70, 0.4, 18, 4, 0.9, guard_break=True, knockdown=True, ender="knockdown"),
       (WALL(torso=(-16, 10, 0, -6), right_arm=(-175, 0, 10, 20), left_arm=(-170, 15, -10, 30)).add(body=dict(pos=(0, 1, 1))),
        WALL(torso=(30, 0, 0, 16), right_arm=(-70, 0, 0, 5), left_arm=(-75, 15, 0, 10)).add(body=dict(pos=(0, -2.5, -3)))),
       {"light": "wall_thrust"})
s.move("wall_spin", (6, 4, 9, 6), H(6.5, 26, 3.6, 360, 1.3, 14, 4, 0.8, ender="splat"),
       anim=spin_strike(WALL, WALL(torso=(10, 0, 0, 4), right_arm=(-88, 0, 34, 0), left_arm=(-88, 0, -34, 0)),
                        WALL(torso=(8, 40, 0, 6), right_arm=(-80, 50, 0, 10), left_arm=(-80, 60, 0, 20)), turn=-360),
       nxt={"light": "wall_thrust"}, variants={"aggressive": "wall_crash"})
s.move("wall_vault_kick", (5, 3, 8, 5), H(5, 18, 3.0, 100, 0.2, 16, 3, 0.65, lift=0.75, ender="launch"),
       (WALL(torso=(24, 0, 0, 12), right_arm=(-30, 0, 10, 30), left_arm=(-30, 0, -10, 30)).add(body=dict(pos=(0, -2, 0))),
        WALL(torso=(-20, 0, 0, -4), right_arm=(-20, 0, 20, 10), left_arm=(-20, 0, -20, 10), right_leg=(-150, 0, 0, 0),
             left_leg=(-60, 0, 0, 90)).add(body=dict(pos=(0, 6, -2)))),
       {"light": "wall_thrust"}, weight=False)
s.move("wall_ram", (5, 2, 8, 5), H(6, 22, 4.2, 40, 1.2, 12, 3, 0.6, lunge=0.9),
       (WALL(torso=(4, 35, 0, 4), right_arm=(-20, 20, 10, 80), left_arm=(-50, 40, 0, 80)),
        WALL(torso=(20, 5, 0, 4), right_arm=(-84, 10, 0, 5), left_arm=(-88, 25, 0, 10)).add(body=dict(pos=(0, -1.5, -8)))),
       {"light": "wall_sweep"})
s.move("wall_ward", (4, 2, 7, 4), H(3.5, 22, 3.4, 140, 1.5, 9, 2, 0.5),
       (WALL(torso=(-4, 0, 0, -2), right_arm=(-50, -20, 20, 90), left_arm=(-55, 30, -20, 90)),
        WALL(torso=(16, 0, 0, 6), right_arm=(-88, -20, 20, 10), left_arm=(-90, 30, -20, 10)).add(body=dict(pos=(0, -1, -3)))),
       {"light": "wall_thrust"})
s.move("wall_counter", (1, 2, 8, 4), H(5.5, 22, 3.8, 140, 1.0, 13, 3, 0.6),
       (WALL, WALL(torso=(14, -50, 0, 6), right_arm=(-75, -55, 0, 10), left_arm=(-80, -35, 0, 30))), {"light": "wall_sweep"})
s.write({"light": "wall_thrust", "heavy": "wall_spin", "launcher": "wall_vault_kick", "forward_light": "wall_ram",
         "forward_heavy": "wall_ram", "back_light": "wall_ward", "back_heavy": "wall_ward"},
        "wall_counter",
        {"footwork": "upright", "rhythm": "defensive", "move_speed": 0.95,
         "guard": {"type": "high", "parry_window": 1.3, "keep": 0.12, "posture": 0.7, "speed": 0.55},
         "dodge": {"kind": "step", "speed": 0.8}},
        {"required": True, "watch": 0.02, "scroll": 3}, npc_weight=1)

# 14. Spear Dance (yari): bouncing, the spear held low and long. Mobile hit-and-run: thrusts that spring back
# out of reach, vaults and hops, quick on the feet.
SDANCE = P(torso=(10, 30, 0, 4), right_arm=(-40, 20, 10, 60), left_arm=(-70, 40, -5, 40), body=dict(pos=(0, -1.6, 0)),
           **legs((-24, 8, 34), (24, 14, 30)))
s = style(Style("spear_dance", SDANCE, 12, dict(body=dict(pos=(0, -1.2, 0)), left_leg=(0, 0, 0, 12), right_leg=(0, 0, 0, 12)),
                ("aggressive", "air"), weapons=["spear"]))
s.move("dance_thrust", (4, 2, 6, 3), H(5, 14, 4.8, 30, 0.6, 9, 2, 0.4, lunge=0.4, pierce=0.2),
       (SDANCE(torso=(4, 40, 0, 4), right_arm=(-20, 20, 10, 90), left_arm=(-50, 40, 0, 80)).add(body=dict(pos=(0, 0, 1))),
        SDANCE(torso=(18, 5, 0, 4), right_arm=(-84, 10, 0, 4), left_arm=(-88, 25, 0, 8)).add(body=dict(pos=(0, -1.5, -6)))),
       {"light": "spin_cut", "heavy": "dance_whirl"}, effects={"recoil": 0.3})
s.move("spin_cut", (4, 3, 7, 4), H(5.5, 18, 4.4, 200, 0.8, 10, 3, 0.5),
       anim=spin_strike(SDANCE, SDANCE(torso=(10, 0, 0, 4), right_arm=(-88, 0, 34, 0), left_arm=(-88, 0, -34, 0)),
                        SDANCE(torso=(8, 40, 0, 6)), turn=-360), nxt={"light": "double_thrust", "heavy": "dance_vault"})
s.move("double_thrust", (4, 2, 7, 4), H(5.5, 16, 4.8, 30, 0.7, 10, 3, 0.5, lunge=0.4, pierce=0.25),
       (SDANCE(torso=(4, 40, 0, 4), right_arm=(-30, 20, 10, 80), left_arm=(-50, 40, 0, 70)),
        SDANCE(torso=(20, 0, 0, 4), right_arm=(-86, 6, 0, 2), left_arm=(-90, 20, 0, 6)).add(body=dict(pos=(0, -2, -7)))),
       {"light": "skewer", "heavy": "skewer"})
s.move("skewer", (6, 2, 9, 6), H(8, 26, 5.2, 30, 1.3, 14, 4, 0.8, lunge=0.7, pierce=0.3, guard_break=True, ender="splat"),
       (SDANCE(torso=(0, 50, 0, 2), right_arm=(-10, 30, 10, 100), left_arm=(-40, 50, 0, 90)).add(body=dict(pos=(0, -0.5, 2))),
        SDANCE(torso=(24, -5, 0, 6), right_arm=(-88, 0, 0, 0), left_arm=(-92, 15, 0, 4)).add(body=dict(pos=(0, -2.5, -9)))),
       {"light": "dance_thrust"})
s.move("dance_whirl", (6, 3, 9, 6), H(6.5, 22, 4.4, 360, 1.1, 12, 3, 0.7),
       anim=spin_strike(SDANCE, SDANCE(torso=(10, 0, 0, 4), right_arm=(-88, 0, 40, 0), left_arm=(-88, 0, -30, 0)),
                        SDANCE(torso=(8, 40, 0, 6)), turn=-720, lift=3.0), nxt={"light": "dance_thrust"}, variants={"aggressive": "skewer"})
s.move("dance_vault", (5, 3, 8, 5), H(5, 16, 4.2, 90, 0.1, 18, 3, 0.65, lift=0.8, ender="launch"),
       (SDANCE(torso=(26, 0, 0, 14), right_arm=(-30, 0, 10, 30), left_arm=(-40, 0, -10, 30)).add(body=dict(pos=(0, -2.5, 0))),
        SDANCE(torso=(-18, 0, 0, -4), right_arm=(-160, 0, 10, 10), left_arm=(-150, 0, -10, 10), right_leg=(-100, 0, 0, 20),
               left_leg=(-40, 0, 0, 90)).add(body=dict(pos=(0, 6, -3)))),
       {"light": "dance_thrust"}, weight=False)
s.move("dance_lunge", (5, 2, 8, 5), H(7, 20, 5.0, 30, 1.0, 12, 3, 0.6, lunge=1.2, pierce=0.3),
       (SDANCE(torso=(2, 45, 0, 2), right_arm=(-15, 25, 10, 95), left_arm=(-45, 45, 0, 85)),
        SDANCE(torso=(26, 0, 0, 6), right_arm=(-88, 4, 0, 0), left_arm=(-92, 18, 0, 4)).add(body=dict(pos=(0, -3, -11)))),
       {"light": "spin_cut"})
s.move("retreat_thrust", (3, 2, 6, 3), H(4, 12, 4.6, 30, 0.6, 8, 2, 0.4, pierce=0.2),
       (SDANCE(torso=(4, 40, 0, 4), right_arm=(-20, 20, 10, 90), left_arm=(-50, 40, 0, 80)),
        SDANCE(torso=(12, 5, 0, 4), right_arm=(-84, 10, 0, 4), left_arm=(-88, 25, 0, 8)).add(body=dict(pos=(0, -1, -3)))),
       {"light": "dance_thrust"}, effects={"recoil": 0.7})
s.move("dance_counter", (1, 2, 8, 4), H(6, 20, 4.6, 140, 0.6, 13, 3, 0.6),
       (SDANCE, SDANCE(torso=(18, 5, 0, 4), right_arm=(-84, 10, 0, 4), left_arm=(-88, 25, 0, 8))), {"light": "dance_thrust"})
s.write({"light": "dance_thrust", "heavy": "dance_whirl", "launcher": "dance_vault", "forward_light": "dance_lunge",
         "forward_heavy": "dance_lunge", "back_light": "retreat_thrust", "back_heavy": "dance_whirl"},
        "dance_counter",
        {"footwork": "bouncing", "rhythm": "hit_and_run", "move_speed": 1.15,
         "guard": {"type": "standard", "parry_window": 1.1, "keep": 0.28, "speed": 0.6},
         "dodge": {"kind": "hop", "speed": 1.05, "ticks": 10, "iframes": 6, "lift": 0.3}},
        {"required": True, "watch": 0.02, "scroll": 3}, npc_weight=1)


# ====================================================================== dodges of each kind, and the feint
GUARD = P(torso=(8, 0, 0, 4), right_arm=(-70, -10, 5, 110), left_arm=(-70, 10, -5, 110), right_leg=(0, 0, 4, 20), left_leg=(0, 0, -4, 20))


def dodge(kind, direction, ticks, out, hold=3, ease="snap", extra_keys=()):
    """A dodge animation: snap to `out` (the pose of the dodge) at tick 2, hold, back to a guard."""
    a = Anim("dodge_%s_%s" % (kind, direction), ticks)
    a.key(0, full(GUARD, out))
    a.key(2, full(out, GUARD), ease, hold=hold)
    for t, pose, e in extra_keys:
        a.key(t, full(pose, GUARD), e)
    a.key(ticks, full(GUARD, out), "easeOutQuad")
    ANIMS.append(a)


for d, f, sd in (("forward", 1, 0), ("back", -1, 0), ("left", 0, 1), ("right", 0, -1)):
    # step: a short, upright step, the body shifting over the feet.
    dodge("step", d, 10, P(torso=(10 * f, 0, 14 * sd, 4), right_arm=(-90, -15, 8, 120), left_arm=(-92, 15, -8, 120),
                          right_leg=(-20 * f, 0, 14 if sd < 0 else 4, 40), left_leg=(20 * f, 0, -14 if sd > 0 else -4, 40),
                          body=dict(pos=(1.2 * sd, -1.5, -2.0 * f))), hold=3)
    # hop: off the ground, knees tucked, arms out.
    dodge("hop", d, 10, P(torso=(-6 * f + 6, 0, 10 * sd, 2), right_arm=(-40, 0, 40, 30), left_arm=(-40, 0, -40, 30),
                         right_leg=(-50, 0, 6, 100), left_leg=(-30, 0, -6, 80), body=dict(pos=(sd, 4.5, -1.5 * f))), hold=2,
          extra_keys=((6, P(torso=(8, 0, 0, 4), right_leg=(-20, 0, 6, 50), left_leg=(-20, 0, -6, 50), body=dict(pos=(0, -1.5, 0))), "easeInQuad"),))
    # slide: dropped low on one hip, the lead leg sliding out.
    dodge("slide", d, 10, P(torso=(-18 if f >= 0 else 30, 0, -10 * sd, -4), right_arm=(-20, 0, 30, 40), left_arm=(-110, 0, -10, 60),
                           right_leg=(-80, 0, 10, 10), left_leg=(-40, 0, -14, 120), body=dict(pos=(0, -7, -2.0 * f), rot=(0, 0, 0))), hold=4)
    # sway: barely moving: the whole upper body leans out of the line.
    dodge("sway", d, 10, P(torso=(-34 if f < 0 else (10 if f > 0 else 4), 0, 36 * sd, -6), head=(0, 0, -10 * sd),
                          right_arm=(-60, -10, 8, 120), left_arm=(-60, 10, -8, 120), right_leg=(0, 0, 8, 26), left_leg=(0, 0, -8, 26),
                          body=dict(pos=(2.0 * sd, -1.0, 0))), hold=4)
# flip: a back handspring, a front flip, cartwheels to the sides (a full turn of the body).
TUCKF = P(torso=(30, 0, 0, 30), right_arm=(-150, 0, 20, 10), left_arm=(-150, 0, -20, 10), right_leg=(-60, 0, 0, 90), left_leg=(-60, 0, 0, 90))
for d, axis, sign in (("back", 0, -1), ("forward", 0, 1), ("left", 2, 1), ("right", 2, -1)):
    a = Anim("dodge_flip_%s" % d, 11)

    def rot(v):
        r = [0.0, 0.0, 0.0]
        r[axis] = v
        return tuple(r)
    a.key(0, full(GUARD, TUCKF))
    a.key(2, full(TUCKF.add(body=dict(rot=rot(sign * 90), pos=(0, 4, 0))), GUARD), "snap")
    a.key(6, full(TUCKF.add(body=dict(rot=rot(sign * 260), pos=(0, 6, 0))), GUARD), "linear")
    a.key(9, full(GUARD.add(body=dict(rot=rot(sign * 345), pos=(0, 0.5, 0))), TUCKF), "easeOutQuad")
    a.key(11, full(GUARD.add(body=dict(rot=rot(sign * 360))), TUCKF), "easeInOutSine")
    ANIMS.append(a)

# The feint: the wind-up snaps back into a tight guard (6 ticks), as if it never happened.
feint = Anim("feint_pull", 6)
feint.key(0, P(torso=(14, 20, 0, 8), right_arm=(-60, 20, 10, 110), left_arm=(-70, 10, -10, 100)))
feint.key(1.5, P(torso=(-4, -10, 0, -2), right_arm=(-100, -20, 8, 130), left_arm=(-104, 20, -8, 132), body=dict(pos=(0, 0, 1.5))), "snap", hold=1.5)
feint.key(6, P(torso=(8, 0, 0, 6), right_arm=(-80, -10, 5, 115), left_arm=(-80, 10, -5, 115)), "easeOutQuad")
ANIMS.append(feint)


if __name__ == "__main__":
    for st in STYLES:
        for a in st.stance_anims(stance_set):
            a.write()
        for a in st.anims:
            a.write()
        print("wrote %s: %d moves" % (st.key, len(st.moves)))
    for a in ANIMS:
        a.write()
    print("wrote %d dodge and feint animations" % len(ANIMS))

#!/usr/bin/env python3
"""Writes the unarmed moves and styles (data/noirm/combat/moves/{basic,brawler,strong_fist,gentle_fist,
clinch,beast} and styles/{basic,brawler,strong_fist,gentle_fist,beast}.json) for the 2026-11 combat
rebuild (docs/COMBAT_DESIGN.md). Run from the repo root, then tools/animgen/gen_anims.py:

    python3 tools/animgen/gen_moves_unarmed.py

Existing move files keep any field this script doesn't set (chakra, pierce, clinch…); the fields
below are written over them. Tune numbers here, or edit the JSON and stop running this.

Timings are (windup, active, recovery, cancel_after) in ticks. Each style has:
  * a long string with branches (5 hits for the light route), enders that launch / knock down / splat;
  * a guard break, a dash strike (forward inputs: sprinting), a dodge counter;
  * sub-stances (neutral + 2-3) with their own stance animation and move variants.
"""
import json
import os

ROOT = "src/main/resources/data/noirm/combat"


def N(x):
    return x if ":" in x else "noirm:" + x


def move(path, anim, timing, hit=None, nxt=None, variants=None, drop=()):
    f = os.path.join(ROOT, "moves", path + ".json")
    d = json.load(open(f)) if os.path.exists(f) else {"hit": {}}
    if anim:
        d["animation"] = N(anim)
    w, a, r, c = timing
    d["timing"] = {"windup": w, "active": a, "recovery": r, "cancel_after": c}
    d["hit"].update(hit or {})
    for k in drop:
        d["hit"].pop(k, None)
    if nxt is not None:
        if nxt:
            d["next"] = {k: N(v) for k, v in nxt.items()}
        else:
            d.pop("next", None)
    if variants:
        d["variants"] = {k: N(v) for k, v in variants.items()}
    else:
        d.pop("variants", None)
    order = ["animation", "timing", "hit", "next", "variants"]
    out = {k: d[k] for k in order if k in d}
    os.makedirs(os.path.dirname(f), exist_ok=True)
    with open(f, "w") as fh:
        fh.write(json.dumps(out, indent=2) + "\n")


def H(damage, posture, rng, arc, kb, hitstun, hitstop, shake, **extra):
    h = {"damage": damage, "posture": posture, "range": rng, "arc": arc, "knockback": kb,
         "hitstun": hitstun, "hitstop": hitstop, "shake": shake}
    h.update(extra)
    return h


def style(name, openers, kinds, counter, **extra):
    f = os.path.join(ROOT, "styles", name + ".json")
    d = json.load(open(f)) if os.path.exists(f) else {}
    d["openers"] = {k: N(v) for k, v in openers.items()}
    if kinds is not None:
        stances = {"neutral": {"animation": "noirm:stance_" + name, "switch": 2}}
        for kind in kinds:
            stances[kind] = {"animation": "noirm:stance_%s_%s" % (name, kind), "switch": 2}
        d["stances"] = stances
    if counter:
        d["counter"] = N(counter)
    d.update(extra)
    with open(f, "w") as fh:
        fh.write(json.dumps(d, indent=2) + "\n")


# ------------------------------------------------------------------ Academy (basic)
# String: jab > hook > body shot > spin kick > axe kick (guard break, knocks down).
# Branches: hook+H flip kick (launch), body shot+H uppercut, spin kick+H heavy kick (splat).
move("basic/jab", "quick_jab", (2, 2, 4, 1), {"hitstop": 1}, {"light": "basic/hook", "heavy": "basic/heavy_kick"},
     {"low": "basic/low_kick", "air": "basic/air_kick"})
move("basic/hook", "turning_hook", (2, 2, 5, 1), {"hitstop": 1}, {"light": "basic/body_shot", "heavy": "basic/flip_kick"})
move("basic/body_shot", "body_shot", (2, 2, 4, 1),
     H(3, 12, 2.5, 80, 0.1, 7, 1, 0.25, lunge=0.2), {"light": "basic/spin_kick", "heavy": "basic/uppercut"})
move("basic/spin_kick", "rotating_kick", (4, 3, 7, 3), {"hitstop": 3}, {"light": "basic/axe_kick", "heavy": "basic/heavy_kick"})
move("basic/axe_kick", "axe_kick", (5, 2, 8, 6),
     H(6, 30, 2.6, 70, 0.3, 14, 4, 0.8, lunge=0.3, guard_break=True, ender="knockdown"), {})
move("basic/flip_kick", "aerial_flip_kick", (5, 3, 9, 6), {"hitstop": 4, "ender": "launch"}, {})
move("basic/heavy_kick", "counter_kick", (6, 3, 8, 5), {"hitstop": 3, "ender": "splat"}, {"light": "basic/jab"},
     {"aggressive": "basic/axe_kick", "defensive": "basic/push_kick", "air": "basic/air_kick"})
move("basic/uppercut", "rising_uppercut", (4, 3, 7, 4), {"hitstop": 3, "ender": "launch"}, {"light": "basic/jab", "heavy": "basic/flip_kick"})
move("basic/backfist", "spinning_backfist", (3, 2, 6, 2), {"hitstop": 2, "lunge": 0.5}, {"light": "basic/hook", "heavy": "basic/heavy_kick"})
move("basic/flying_kick", "flying_kick_basic", (4, 2, 8, 5),
     H(6.5, 22, 3.0, 80, 1.0, 12, 3, 0.7, lunge=0.8, ender="splat"), {"light": "basic/jab"})
move("basic/low_sweep", "low_sweep", (4, 3, 7, 4), {"hitstop": 2}, {"light": "basic/jab"})
move("basic/push_kick", "push_kick", (4, 2, 7, 4), {"hitstop": 3}, {"light": "basic/jab", "heavy": "basic/heavy_kick"})
move("basic/counter_strike", "counter_strike", (1, 2, 6, 2),
     H(4.5, 18, 2.8, 120, 0.5, 12, 3, 0.6), {"light": "basic/hook", "heavy": "basic/heavy_kick"})
move("basic/low_kick", "low_kick", (2, 2, 5, 2), H(2.5, 14, 2.4, 80, 0.1, 8, 1, 0.25), {"light": "basic/hook"})
move("basic/air_kick", "air_kick", (3, 2, 6, 3), H(4, 14, 2.8, 90, 0.5, 10, 2, 0.5, lunge=0.4), {"light": "basic/jab"})
style("basic", {"light": "basic/jab", "heavy": "basic/heavy_kick", "launcher": "basic/uppercut",
                "forward_light": "basic/backfist", "forward_heavy": "basic/flying_kick",
                "back_light": "basic/low_sweep", "back_heavy": "basic/push_kick"},
      ("aggressive", "defensive", "low", "air"), "basic/counter_strike")

# ------------------------------------------------------------------ Brawler (Muay Thai)
# String: elbow > rising knee > back elbow > body hook > uppercut elbow (launch).
move("brawler/elbow", "elbow_strike", (2, 2, 4, 1), {"hitstop": 1},
     {"light": "brawler/rising_knee", "heavy": "brawler/headbutt", "grab": "clinch/grab"}, {"low": "brawler/body_hook"})
move("brawler/rising_knee", "knee_strike", (3, 2, 6, 2), {"hitstop": 2},
     {"light": "brawler/back_elbow", "heavy": "brawler/hammer_fist"})
move("brawler/back_elbow", "back_elbow", (3, 2, 6, 3), {"hitstop": 2},
     {"light": "brawler/body_hook", "heavy": "brawler/flying_knee"})
move("brawler/body_hook", "body_hook", (2, 2, 5, 2), {"hitstop": 2},
     {"light": "brawler/uppercut_elbow", "heavy": "brawler/hammer_fist", "grab": "clinch/grab"})
move("brawler/uppercut_elbow", "uppercut_elbow", (4, 2, 8, 5),
     H(5.5, 20, 2.4, 90, 0.2, 16, 3, 0.7, lift=0.6, ender="launch"), {"light": "brawler/elbow"})
move("brawler/flying_knee", "flying_knee", (5, 3, 9, 6), {"hitstop": 4, "ender": "splat"}, {"light": "brawler/elbow"})
move("brawler/hammer_fist", "hammer_fist", (6, 2, 9, 6), {"hitstop": 4, "guard_break": True, "ender": "knockdown"}, {"light": "brawler/elbow"})
move("brawler/headbutt", "headbutt", (5, 2, 9, 5), {"hitstop": 3}, {"light": "brawler/elbow"},
     {"aggressive": "brawler/hammer_fist", "defensive": "brawler/back_elbow"})
move("brawler/counter_knee", "counter_knee", (1, 2, 6, 2),
     H(5, 20, 2.2, 120, 0.3, 12, 3, 0.6, lift=0.3), {"light": "brawler/elbow", "grab": "clinch/grab"})
style("brawler", {"light": "brawler/elbow", "heavy": "brawler/headbutt", "launcher": "basic/uppercut",
                  "forward_light": "brawler/rising_knee", "forward_heavy": "brawler/flying_knee",
                  "back_heavy": "brawler/back_elbow", "back_light": "brawler/body_hook"},
      ("aggressive", "defensive", "low"), "brawler/counter_knee")

# ------------------------------------------------------------------ Strong Fist (Gōken)
# String: jab > straight > rapid kick > Leaf Whirlwind > Leaf Great Flash (guard break, knocks down).
move("strong_fist/jab", "strong_fist_jab", (2, 2, 3, 1), {"hitstop": 1},
     {"light": "strong_fist/straight", "heavy": "strong_fist/leaf_whirlwind"}, {"air": "basic/air_kick"})
move("strong_fist/straight", "strong_fist_straight", (2, 2, 5, 1), {"hitstop": 2},
     {"light": "strong_fist/rapid_kick", "heavy": "strong_fist/leaf_great_flash", "launcher": "strong_fist/leaf_rising_wind"})
move("strong_fist/rapid_kick", "rapid_kick", (2, 2, 5, 2), H(3.5, 12, 2.6, 90, 0.2, 8, 2, 0.3, lunge=0.2),
     {"light": "strong_fist/leaf_whirlwind", "heavy": "strong_fist/leaf_great_flash"})
move("strong_fist/leaf_whirlwind", "leaf_whirlwind", (5, 4, 9, 5), {"hitstop": 3},
     {"light": "strong_fist/leaf_great_flash", "launcher": "strong_fist/leaf_rising_wind"})
move("strong_fist/leaf_great_flash", "leaf_great_flash", (6, 3, 9, 6), {"hitstop": 4, "guard_break": True, "ender": "knockdown"},
     {"light": "strong_fist/jab"})
move("strong_fist/leaf_rising_wind", "leaf_rising_wind", (5, 3, 9, 4), {"hitstop": 3, "ender": "launch"},
     {"light": "basic/flip_kick", "heavy": "strong_fist/dynamic_entry"})
move("strong_fist/dynamic_entry", "dynamic_entry", (4, 4, 10, 6), {"hitstop": 4, "ender": "splat"}, {"light": "strong_fist/jab"})
move("strong_fist/back_kick", "strong_back_kick", (4, 2, 7, 4), {"hitstop": 3},
     {"light": "strong_fist/jab", "heavy": "strong_fist/leaf_great_flash"})
move("strong_fist/leaf_gale", "leaf_gale", (5, 4, 8, 5), {"hitstop": 3},
     {"light": "strong_fist/jab", "launcher": "strong_fist/leaf_rising_wind"})
move("strong_fist/counter_heel", "counter_heel", (1, 2, 6, 2),
     H(5, 20, 2.8, 120, 1.0, 12, 3, 0.6), {"light": "strong_fist/straight"})
# The heavy opener: aggressive presses with the Great Flash, defensive answers with a back kick.
move("strong_fist/leaf_whirlwind", "leaf_whirlwind", (5, 4, 9, 5), {"hitstop": 3},
     {"light": "strong_fist/leaf_great_flash", "launcher": "strong_fist/leaf_rising_wind"},
     {"aggressive": "strong_fist/leaf_great_flash", "defensive": "strong_fist/back_kick"})
style("strong_fist", {"light": "strong_fist/jab", "heavy": "strong_fist/leaf_whirlwind", "launcher": "strong_fist/leaf_rising_wind",
                      "forward_light": "strong_fist/straight", "forward_heavy": "strong_fist/dynamic_entry",
                      "back_heavy": "strong_fist/leaf_gale", "back_light": "strong_fist/back_kick"},
      ("aggressive", "defensive", "air"), "strong_fist/counter_heel")

# ------------------------------------------------------------------ Gentle Fist (Jūken)
# String: palm > palm 2 > palm 3 > palm flurry > Twin Lion Palms (splat).
move("gentle_fist/palm", "palm_strike", (2, 2, 4, 1), {"hitstop": 1}, {"light": "gentle_fist/palm_2", "heavy": "gentle_fist/double_palm"},
     {"low": "gentle_fist/trigram_sweep", "defensive": "gentle_fist/back_palm"})
move("gentle_fist/palm_2", "palm_strike_2", (2, 2, 4, 1), {"hitstop": 1}, {"light": "gentle_fist/palm_3", "heavy": "gentle_fist/double_palm"})
move("gentle_fist/palm_3", "palm_strike_3", (2, 2, 5, 2), {"hitstop": 2},
     {"light": "gentle_fist/palm_flurry", "heavy": "gentle_fist/double_palm"})
move("gentle_fist/palm_flurry", "palm_flurry", (3, 3, 6, 3),
     H(3.5, 16, 2.6, 90, 0.2, 9, 2, 0.35, pierce=0.6, chakra=8, lunge=0.2),
     {"light": "gentle_fist/lion_palms", "heavy": "gentle_fist/rising_palm"})
move("gentle_fist/double_palm", "double_palm", (5, 3, 9, 5), {"hitstop": 3, "guard_break": True, "ender": "splat"},
     {"light": "gentle_fist/palm"}, {"aggressive": "gentle_fist/lion_palms"})
move("gentle_fist/lion_palms", "lion_palms", (5, 3, 9, 5), {"hitstop": 3, "ender": "splat"}, {"light": "gentle_fist/palm"})
move("gentle_fist/lunging_palm", "lunging_palm", (3, 2, 6, 2), {"hitstop": 2},
     {"light": "gentle_fist/palm_2", "heavy": "gentle_fist/double_palm"})
move("gentle_fist/rising_palm", "rising_palm", (5, 3, 9, 4), {"hitstop": 3, "ender": "launch"}, {"light": "gentle_fist/palm_2"})
move("gentle_fist/trigram_sweep", "trigram_sweep", (5, 3, 8, 5), {"hitstop": 3}, {"light": "gentle_fist/palm"})
move("gentle_fist/back_palm", "pivot_palm", (2, 2, 5, 2), {"hitstop": 1},
     {"light": "gentle_fist/palm_2", "heavy": "gentle_fist/double_palm"})
move("gentle_fist/counter_palm", "counter_palm", (1, 2, 6, 2),
     H(3.5, 18, 2.6, 120, 0.6, 12, 3, 0.55, pierce=0.6, chakra=10), {"light": "gentle_fist/palm_2"})
style("gentle_fist", {"light": "gentle_fist/palm", "heavy": "gentle_fist/double_palm", "launcher": "gentle_fist/rising_palm",
                      "forward_light": "gentle_fist/lunging_palm", "forward_heavy": "gentle_fist/lion_palms",
                      "back_light": "gentle_fist/back_palm", "back_heavy": "gentle_fist/trigram_sweep"},
      ("aggressive", "defensive", "low"), "gentle_fist/counter_palm")

# ------------------------------------------------------------------ clinch (grabs and throws)
move("clinch/grab", "clinch_grab", (3, 3, 6, 2))
move("clinch/knee", "clinch_knee", (2, 2, 5, 1), {"hitstop": 2})
move("clinch/headbutt", "clinch_headbutt", (4, 2, 8, 5), {"hitstop": 3})
move("clinch/throw", "throw", (5, 3, 9, 6), {"hitstop": 4, "ender": "knockdown"})

# ------------------------------------------------------------------ beasts (tailed beasts: their own model)
move("beast/claw", None, (10, 4, 12, 8), {"hitstop": 4}, {"light": "beast/claw_back", "heavy": "beast/slam"})
move("beast/claw_back", None, (7, 4, 12, 9), {"hitstop": 4, "ender": "splat"}, {})
move("beast/slam", None, (18, 4, 18, 15), {"hitstop": 5, "ender": "launch"}, {})
style("beast", {"light": "beast/claw", "heavy": "beast/slam", "launcher": "beast/slam"}, None, None)

print("wrote the unarmed moves and styles")

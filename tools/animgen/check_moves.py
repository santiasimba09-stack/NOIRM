#!/usr/bin/env python3
"""Checks the combat data against the animations (run from the repo root after the generators):
every move's animation exists, lasts the move and has its strike marker on the hit tick (the
windup); every style's openers, routes, variants, counter and stance animations exist.
The game test AnimationGameTests.strikePosesLandOnTheHitTick checks the same in the game."""
import glob
import json
import os
import sys

MOVES = "src/main/resources/data/noirm/combat/moves"
STYLES = "src/main/resources/data/noirm/combat/styles"
ANIMS = "src/main/resources/assets/noirm/player_animation"

anims = {}
for f in glob.glob(os.path.join(ANIMS, "*.json")):
    for name, a in json.load(open(f)).get("animations", {}).items():
        anims[name] = a
moves = {}
for f in glob.glob(os.path.join(MOVES, "*", "*.json")):
    moves["noirm:" + os.path.relpath(f, MOVES)[:-5]] = json.load(open(f))

bad = []
for mid, m in sorted(moves.items()):
    t = m["timing"]
    total = t["windup"] + t["active"] + t["recovery"]
    for k, target in list(m.get("next", {}).items()) + list(m.get("variants", {}).items()):
        if target not in moves:
            bad.append("%s routes %s to missing %s" % (mid, k, target))
    if "animation" not in m:
        continue
    name = m["animation"].split(":")[1]
    a = anims.get(name)
    if a is None:
        bad.append("%s: no animation %s" % (mid, name))
        continue
    length = a["animation_length"] * 20
    if abs(length - total) > 0.01:
        bad.append("%s: %s lasts %.1f ticks, the move %d" % (mid, name, length, total))
    if "strike" not in a:
        bad.append("%s: %s has no strike marker" % (mid, name))
    elif abs(a["strike"] * 20 - t["windup"]) > 0.01:
        bad.append("%s: %s strikes at %.1f, the hit lands at %d" % (mid, name, a["strike"] * 20, t["windup"]))
for f in glob.glob(os.path.join(STYLES, "*.json")):
    s = json.load(open(f))
    sid = os.path.basename(f)[:-5]
    for k, v in s["openers"].items():
        if v not in moves:
            bad.append("%s opens %s with missing %s" % (sid, k, v))
    if "counter" in s and s["counter"] not in moves:
        bad.append("%s: missing counter %s" % (sid, s["counter"]))
    for kind, st in s.get("stances", {}).items():
        if st["animation"].split(":")[1] not in anims:
            bad.append("%s: missing stance %s" % (sid, st["animation"]))
print("\n".join(bad) if bad else "all %d moves line up with their animations" % len(moves))
sys.exit(1 if bad else 0)

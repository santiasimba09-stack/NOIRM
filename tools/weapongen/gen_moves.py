"""Writes the weapon movesets: data/noirm/combat/moves/{kunai,staff,sword}/*.json and the
styles data/noirm/combat/styles/weapon_*.json. Run from the repository root:

    python3 tools/weapongen/gen_moves.py

Tune numbers here (or edit the JSON directly and stop running this script). The fields are
explained in CombatMove.java; animation ids are the lead's player_animation files.
"""
import json
import os

ROOT = "src/main/resources/data/noirm/combat"


def compact(obj):
    return json.dumps(obj).replace("{", "{ ").replace("}", " }")


def move(path, anim, timing, hit, nxt=None):
    w, a, r, c = timing
    lines = ["{",
             f'  "animation": "noirm:{anim}",',
             '  "timing": ' + compact({"windup": w, "active": a, "recovery": r, "cancel_after": c}) + ",",
             '  "hit": ' + compact(hit) + ("," if nxt else "")]
    if nxt:
        lines.append('  "next": ' + compact({k: "noirm:" + v for k, v in nxt.items()}))
    lines.append("}")
    out = os.path.join(ROOT, "moves", path + ".json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        f.write("\n".join(lines) + "\n")


def H(damage, posture, rng, arc, kb, hitstun, hitstop, shake, lunge=0, lift=0, **extra):
    h = {"damage": damage, "posture": posture, "range": rng, "arc": arc, "knockback": kb}
    if lift:
        h["lift"] = lift
    if lunge:
        h["lunge"] = lunge
    h.update({"hitstun": hitstun, "hitstop": hitstop, "shake": shake})
    h.update(extra)
    return h


def style(name, openers):
    d = {"openers": {k: "noirm:" + v for k, v in openers.items()}, "npc_weight": 0, "playable": False}
    with open(os.path.join(ROOT, "styles", name + ".json"), "w") as f:
        f.write(json.dumps(d, indent=2) + "\n")


# Kunai: fast, short cuts; a stab finisher. (Back + heavy with a kunai throws it instead.)
move("kunai/slash", "kunai_slash", (2, 2, 5, 2), H(3, 8, 2.4, 100, 0.1, 6, 1, 0.2, lunge=0.25), {"light": "kunai/backhand", "heavy": "kunai/stab"})
move("kunai/backhand", "kunai_backhand", (2, 2, 6, 2), H(3, 8, 2.4, 110, 0.15, 6, 1, 0.2, lunge=0.2), {"light": "kunai/slash_2", "heavy": "kunai/stab"})
move("kunai/slash_2", "kunai_slash", (3, 2, 7, 3), H(3.5, 10, 2.5, 110, 0.2, 7, 2, 0.3, lunge=0.25), {"light": "kunai/finishing_stab", "heavy": "kunai/finishing_stab"})
move("kunai/finishing_stab", "kunai_stab", (5, 2, 12, 7), H(6.5, 24, 2.7, 50, 0.9, 14, 5, 0.7, lunge=0.5, pierce=0.2), {"light": "kunai/slash"})
move("kunai/stab", "kunai_stab", (4, 2, 9, 5), H(5, 16, 2.7, 50, 0.5, 10, 3, 0.4, lunge=0.4, pierce=0.2), {"light": "kunai/slash", "heavy": "kunai/finishing_stab"})
move("kunai/rising_cut", "kunai_backhand", (5, 3, 10, 6), H(4, 14, 2.5, 100, 0.1, 20, 4, 0.6, lunge=0.2, lift=0.7), {"light": "kunai/slash"})
move("kunai/lunging_stab", "kunai_stab", (5, 2, 10, 6), H(5, 16, 3.0, 50, 0.6, 10, 4, 0.5, lunge=0.9, pierce=0.2), {"light": "kunai/backhand"})

# Staff: wide sweeps, reach, a spinning finisher and an overhead smash.
move("staff/sweep", "staff_sweep", (5, 3, 9, 5), H(4.5, 14, 3.6, 160, 0.6, 9, 3, 0.4, lunge=0.2), {"light": "staff/return_sweep", "heavy": "staff/overhead"})
move("staff/return_sweep", "staff_sweep", (5, 3, 9, 5), H(4.5, 14, 3.6, 160, 0.6, 9, 3, 0.4, lunge=0.2), {"light": "staff/spin", "heavy": "staff/thrust"})
move("staff/spin", "staff_spin", (7, 5, 12, 8), H(6, 25, 3.6, 360, 1.1, 14, 5, 0.7), {"light": "staff/sweep"})
move("staff/thrust", "staff_thrust", (6, 2, 10, 6), H(6, 22, 4.2, 40, 1.0, 12, 4, 0.6, lunge=0.3), {"light": "staff/sweep", "heavy": "staff/overhead"})
move("staff/overhead", "staff_overhead", (9, 3, 12, 7), H(8, 32, 3.8, 70, 0.4, 20, 6, 0.9, knockdown=True))
move("staff/rising_sweep", "staff_sweep", (6, 3, 10, 6), H(4.5, 15, 3.4, 120, 0.1, 20, 4, 0.6, lift=0.75), {"light": "staff/sweep"})
move("staff/lunging_thrust", "staff_thrust", (6, 2, 10, 6), H(6, 22, 4.2, 40, 1.0, 12, 4, 0.6, lunge=0.8), {"light": "staff/sweep"})

# Sword (ninjatō and vanilla swords): solid slashes, a thrust, a rising cut.
move("sword/slash", "sword_slash", (4, 2, 8, 4), H(5, 14, 3.0, 120, 0.25, 8, 3, 0.35, lunge=0.3), {"light": "sword/cross", "heavy": "sword/thrust"})
move("sword/cross", "sword_cross", (4, 2, 9, 4), H(5, 14, 3.0, 120, 0.3, 8, 3, 0.35, lunge=0.25), {"light": "sword/finishing_slash", "heavy": "sword/thrust"})
move("sword/finishing_slash", "sword_slash", (6, 3, 12, 7), H(7, 24, 3.1, 150, 0.9, 14, 5, 0.7, lunge=0.35), {"light": "sword/slash"})
move("sword/thrust", "sword_thrust", (6, 2, 10, 6), H(7, 20, 3.4, 40, 0.7, 12, 4, 0.6, lunge=0.5, pierce=0.25), {"light": "sword/slash", "heavy": "sword/finishing_slash"})
move("sword/rising", "sword_rising", (6, 3, 10, 6), H(5.5, 16, 3.0, 100, 0.1, 20, 4, 0.6, lunge=0.2, lift=0.75), {"light": "sword/slash"})
move("sword/dash_thrust", "sword_thrust", (6, 2, 10, 6), H(7, 20, 3.4, 40, 0.7, 12, 4, 0.6, lunge=0.9, pierce=0.25), {"light": "sword/cross"})

style("weapon_kunai", {"light": "kunai/slash", "heavy": "kunai/stab", "launcher": "kunai/rising_cut",
                       "forward_light": "kunai/lunging_stab", "forward_heavy": "kunai/lunging_stab", "back_light": "kunai/backhand"})
style("weapon_staff", {"light": "staff/sweep", "heavy": "staff/thrust", "launcher": "staff/rising_sweep",
                       "forward_heavy": "staff/lunging_thrust", "back_heavy": "staff/spin"})
style("weapon_sword", {"light": "sword/slash", "heavy": "sword/thrust", "launcher": "sword/rising",
                       "forward_light": "sword/dash_thrust", "forward_heavy": "sword/dash_thrust", "back_heavy": "sword/finishing_slash"})

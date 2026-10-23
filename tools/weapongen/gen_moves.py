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


# ---------------------------------------------------------------------------------------------
# v0.13 blades: seven movesets with clearly different feels. Their animations are written by
# tools/weapongen/gen_blade_anims.py (timed from these files; run it after this script).

# Tantō: the fastest blade. Tiny wind-ups, short reach; the heavy is a stab (a tantō in the back
# cuts 1.6x, see BladeEvents), the dash a shadow-step stab.
move("tanto/slash", "tanto_slash", (2, 2, 4, 2), H(2.5, 7, 2.4, 100, 0.1, 5, 1, 0.15, lunge=0.3), {"light": "tanto/cut_2", "heavy": "tanto/stab"})
move("tanto/cut_2", "tanto_cut_2", (2, 2, 4, 2), H(2.5, 7, 2.4, 110, 0.15, 5, 1, 0.15, lunge=0.25), {"light": "tanto/cut_3", "heavy": "tanto/stab"})
move("tanto/cut_3", "tanto_cut_3", (2, 2, 5, 2), H(3, 8, 2.5, 110, 0.2, 6, 2, 0.25, lunge=0.3), {"light": "tanto/flurry", "heavy": "tanto/flurry"})
move("tanto/flurry", "tanto_flurry", (4, 3, 10, 6), H(5, 20, 2.6, 90, 0.7, 12, 4, 0.5, lunge=0.5), {"light": "tanto/slash"})
move("tanto/stab", "tanto_stab", (5, 2, 9, 5), H(5.5, 16, 2.7, 40, 0.4, 10, 3, 0.4, lunge=0.5, pierce=0.3), {"light": "tanto/slash", "heavy": "tanto/flurry"})
move("tanto/rising", "tanto_rising", (4, 3, 9, 5), H(3.5, 12, 2.4, 100, 0.1, 18, 3, 0.5, lunge=0.2, lift=0.7), {"light": "tanto/slash"})
move("tanto/shadow_step", "tanto_shadow_step", (4, 2, 9, 5), H(5, 14, 3.0, 50, 0.4, 10, 3, 0.4, lunge=1.1, pierce=0.3), {"light": "tanto/cut_2"})

# Katana: two hands, slower, every cut lands hard; the heavy is a drawing cut (iaido) that slips
# 35% through a guard.
move("katana/slash", "katana_slash", (5, 2, 9, 5), H(6, 16, 3.2, 120, 0.3, 9, 3, 0.4, lunge=0.3), {"light": "katana/cut_2", "heavy": "katana/thrust"})
move("katana/cut_2", "katana_cut_2", (5, 2, 9, 5), H(6, 16, 3.2, 120, 0.35, 9, 3, 0.4, lunge=0.3), {"light": "katana/finisher", "heavy": "katana/thrust"})
move("katana/finisher", "katana_finisher", (7, 3, 13, 8), H(9, 28, 3.3, 90, 0.9, 16, 6, 0.8, lunge=0.4), {"light": "katana/slash"})
move("katana/iaido", "katana_iaido", (8, 2, 12, 7), H(9, 26, 3.6, 140, 0.6, 14, 6, 0.8, lunge=0.8, pierce=0.35), {"light": "katana/slash"})
move("katana/thrust", "katana_thrust", (6, 2, 10, 6), H(7.5, 20, 3.6, 40, 0.7, 12, 4, 0.6, lunge=0.5, pierce=0.3), {"light": "katana/slash", "heavy": "katana/finisher"})
move("katana/rising", "katana_rising", (6, 3, 10, 6), H(6, 16, 3.1, 100, 0.1, 20, 4, 0.6, lunge=0.2, lift=0.8), {"light": "katana/slash"})
move("katana/dash_cut", "katana_dash_cut", (7, 2, 11, 6), H(8, 22, 3.6, 60, 0.6, 12, 5, 0.7, lunge=1.0, pierce=0.3), {"light": "katana/cut_2"})

# Nodachi (greatsword): slow, huge, wide. 200-degree sweeps that clear a crowd, a full spin, an
# overhead cleave that floors; every hit also shoves (BladeEvents CLEAVE).
move("greatsword/sweep", "greatsword_sweep", (8, 3, 12, 7), H(8, 22, 4.0, 200, 0.9, 12, 5, 0.7, lunge=0.2), {"light": "greatsword/return_sweep", "heavy": "greatsword/cleave"})
move("greatsword/return_sweep", "greatsword_return", (8, 3, 12, 7), H(8, 22, 4.0, 200, 0.9, 12, 5, 0.7, lunge=0.2), {"light": "greatsword/spin", "heavy": "greatsword/cleave"})
move("greatsword/spin", "greatsword_spin", (10, 5, 14, 9), H(10, 32, 4.0, 360, 1.3, 16, 6, 0.9), {"light": "greatsword/sweep"})
move("greatsword/cleave", "greatsword_cleave", (11, 3, 15, 9), H(12, 40, 4.2, 60, 0.5, 24, 8, 1.0, knockdown=True))
move("greatsword/rising", "greatsword_rising", (9, 3, 13, 8), H(8, 24, 3.8, 120, 0.1, 22, 5, 0.8, lift=0.85), {"light": "greatsword/sweep"})
move("greatsword/charge", "greatsword_charge", (9, 3, 13, 8), H(9, 26, 4.2, 100, 1.0, 14, 6, 0.8, lunge=1.0), {"light": "greatsword/return_sweep"})

# Tonfa: both arms, quick alternating blunt blows; every blow crushes the guard (+8 posture).
move("tonfa/jab_l", "tonfa_jab_l", (2, 2, 4, 2), H(2.5, 10, 2.3, 70, 0.2, 5, 1, 0.2, lunge=0.25), {"light": "tonfa/jab_r", "heavy": "tonfa/spin_strike"})
move("tonfa/jab_r", "tonfa_jab_r", (2, 2, 4, 2), H(2.5, 10, 2.3, 70, 0.2, 5, 1, 0.2, lunge=0.25), {"light": "tonfa/hook_l", "heavy": "tonfa/spin_strike"})
move("tonfa/hook_l", "tonfa_hook_l", (3, 2, 5, 3), H(3, 12, 2.3, 110, 0.3, 6, 2, 0.3, lunge=0.2), {"light": "tonfa/hammer", "heavy": "tonfa/hammer"})
move("tonfa/hammer", "tonfa_hammer", (5, 2, 10, 6), H(5.5, 26, 2.4, 80, 0.8, 14, 5, 0.7, lunge=0.3), {"light": "tonfa/jab_l"})
move("tonfa/spin_strike", "tonfa_spin_strike", (5, 3, 10, 6), H(5, 22, 2.6, 140, 0.7, 10, 4, 0.5, lunge=0.3), {"light": "tonfa/jab_l", "heavy": "tonfa/hammer"})
move("tonfa/uppercut", "tonfa_uppercut", (4, 2, 9, 5), H(3.5, 14, 2.3, 80, 0.1, 18, 3, 0.5, lift=0.75), {"light": "tonfa/jab_l"})
move("tonfa/rush", "tonfa_rush", (4, 2, 9, 5), H(4, 16, 2.8, 70, 0.6, 10, 3, 0.4, lunge=0.9), {"light": "tonfa/jab_r"})

# Yari (spear): the longest reach (thrusts to 4.8-5.2 blocks), narrow thrusts, a wide shaft sweep.
move("spear/thrust", "spear_thrust", (5, 2, 8, 5), H(5, 14, 4.8, 30, 0.6, 9, 3, 0.4, lunge=0.3, pierce=0.2), {"light": "spear/thrust_2", "heavy": "spear/sweep"})
move("spear/thrust_2", "spear_thrust_2", (5, 2, 8, 5), H(5, 14, 4.8, 30, 0.6, 9, 3, 0.4, lunge=0.3, pierce=0.2), {"light": "spear/lunge_thrust", "heavy": "spear/sweep"})
move("spear/lunge_thrust", "spear_lunge_thrust", (7, 2, 12, 7), H(8, 24, 5.2, 30, 1.1, 14, 5, 0.7, lunge=0.6, pierce=0.3), {"light": "spear/thrust"})
move("spear/sweep", "spear_sweep", (7, 3, 11, 7), H(5.5, 20, 4.4, 180, 0.9, 10, 4, 0.6, lunge=0.1), {"light": "spear/thrust", "heavy": "spear/lunge_thrust"})
move("spear/vault", "spear_vault", (6, 3, 10, 6), H(5, 16, 4.2, 90, 0.1, 20, 4, 0.6, lift=0.8), {"light": "spear/thrust"})
move("spear/charge", "spear_charge", (6, 2, 11, 6), H(7, 20, 5.0, 30, 1.0, 12, 4, 0.6, lunge=1.1, pierce=0.3), {"light": "spear/thrust_2"})

# Tekko-kagi (claws): fast rakes from both hands; every rake makes the target bleed (BladeEvents).
move("claws/rake", "claws_rake", (2, 2, 4, 2), H(2.5, 8, 2.4, 110, 0.1, 5, 1, 0.2, lunge=0.3), {"light": "claws/rake_back", "heavy": "claws/maul"})
move("claws/rake_back", "claws_rake_back", (2, 2, 4, 2), H(2.5, 8, 2.4, 110, 0.1, 5, 1, 0.2, lunge=0.25), {"light": "claws/rake_3", "heavy": "claws/maul"})
move("claws/rake_3", "claws_rake_3", (3, 2, 5, 3), H(3, 9, 2.4, 120, 0.2, 6, 2, 0.3, lunge=0.3), {"light": "claws/shred", "heavy": "claws/shred"})
move("claws/shred", "claws_shred", (4, 3, 10, 6), H(5, 20, 2.5, 140, 0.6, 12, 4, 0.5, lunge=0.4), {"light": "claws/rake"})
move("claws/maul", "claws_maul", (5, 2, 9, 5), H(5.5, 18, 2.5, 100, 0.4, 11, 3, 0.5, lunge=0.4), {"light": "claws/rake", "heavy": "claws/shred"})
move("claws/rising", "claws_rising", (4, 3, 9, 5), H(3.5, 12, 2.4, 100, 0.1, 18, 3, 0.5, lift=0.75), {"light": "claws/rake"})
move("claws/pounce", "claws_pounce", (5, 2, 10, 6), H(5, 16, 3.2, 80, 0.5, 10, 4, 0.5, lunge=1.1), {"light": "claws/rake_back"})

# Tessen (iron war fan): quick snaps, a spinning flourish, a heavy gust that blows the target away.
move("fan/snap", "fan_snap", (2, 2, 5, 2), H(3, 10, 2.6, 100, 0.2, 6, 1, 0.2, lunge=0.25), {"light": "fan/snap_back", "heavy": "fan/gust"})
move("fan/snap_back", "fan_snap_back", (2, 2, 5, 2), H(3, 10, 2.6, 100, 0.2, 6, 1, 0.2, lunge=0.2), {"light": "fan/flourish", "heavy": "fan/gust"})
move("fan/flourish", "fan_flourish", (4, 3, 10, 6), H(4.5, 18, 2.7, 360, 0.8, 12, 4, 0.5), {"light": "fan/snap"})
move("fan/gust", "fan_gust", (6, 2, 10, 6), H(4.5, 16, 3.0, 130, 1.6, 10, 4, 0.5, lift=0.2), {"light": "fan/snap"})
move("fan/rising", "fan_rising", (4, 3, 9, 5), H(3.5, 12, 2.6, 100, 0.1, 18, 3, 0.5, lift=0.8), {"light": "fan/snap"})
move("fan/glide", "fan_glide", (4, 2, 9, 5), H(4, 14, 3.0, 90, 0.6, 10, 3, 0.4, lunge=0.9), {"light": "fan/snap_back"})

style("weapon_tanto", {"light": "tanto/slash", "heavy": "tanto/stab", "launcher": "tanto/rising",
                       "forward_light": "tanto/shadow_step", "forward_heavy": "tanto/shadow_step", "back_light": "tanto/cut_2"})
style("weapon_katana", {"light": "katana/slash", "heavy": "katana/iaido", "launcher": "katana/rising",
                        "forward_light": "katana/dash_cut", "forward_heavy": "katana/dash_cut", "back_heavy": "katana/finisher"})
style("weapon_greatsword", {"light": "greatsword/sweep", "heavy": "greatsword/cleave", "launcher": "greatsword/rising",
                            "forward_light": "greatsword/charge", "forward_heavy": "greatsword/charge", "back_heavy": "greatsword/spin"})
style("weapon_tonfa", {"light": "tonfa/jab_l", "heavy": "tonfa/spin_strike", "launcher": "tonfa/uppercut",
                       "forward_light": "tonfa/rush", "forward_heavy": "tonfa/rush", "back_light": "tonfa/hook_l"})
style("weapon_spear", {"light": "spear/thrust", "heavy": "spear/sweep", "launcher": "spear/vault",
                       "forward_light": "spear/charge", "forward_heavy": "spear/charge", "back_heavy": "spear/sweep"})
style("weapon_claws", {"light": "claws/rake", "heavy": "claws/maul", "launcher": "claws/rising",
                       "forward_light": "claws/pounce", "forward_heavy": "claws/pounce", "back_light": "claws/rake_back"})
style("weapon_fan", {"light": "fan/snap", "heavy": "fan/gust", "launcher": "fan/rising",
                     "forward_light": "fan/glide", "forward_heavy": "fan/glide", "back_heavy": "fan/flourish"})

# The blades' own numbers (read by weapon/BladeStats when the game starts). damage: the tooltip's
# attack damage (a fist is 1); speed: swings a second; reach: extra blocks.
STATS = {
    "tanto": {"moveset": "tanto", "damage": 4.5, "speed": 2.4, "reach": 0.0, "durability": 300, "enchantability": 16, "trait": "backstab"},
    "katana": {"moveset": "katana", "damage": 7.0, "speed": 1.5, "reach": 0.5, "durability": 600, "enchantability": 16, "trait": "none",
               "flow": {"nature": "lightning", "damage": 1.25, "chakra": 2.0, "stun": 5}},
    "nodachi": {"moveset": "greatsword", "damage": 10.0, "speed": 0.85, "reach": 1.0, "durability": 700, "enchantability": 12, "trait": "cleave"},
    "tonfa": {"moveset": "tonfa", "damage": 4.0, "speed": 2.3, "reach": 0.0, "durability": 400, "enchantability": 14, "trait": "crush"},
    "yari": {"moveset": "spear", "damage": 6.0, "speed": 1.3, "reach": 1.75, "durability": 350, "enchantability": 14, "trait": "none"},
    "tekko_kagi": {"moveset": "claws", "damage": 4.0, "speed": 2.2, "reach": 0.0, "durability": 350, "enchantability": 16, "trait": "bleed",
                   "flow": {"nature": "wind", "damage": 1.3, "chakra": 1.5, "bonus": 2}},
    "tessen": {"moveset": "fan", "damage": 4.0, "speed": 2.0, "reach": 0.25, "durability": 400, "enchantability": 18, "trait": "deflect"},
}
os.makedirs("src/main/resources/data/noirm/weapons", exist_ok=True)
with open("src/main/resources/data/noirm/weapons/stats.json", "w") as f:
    f.write(json.dumps(STATS, indent=2) + "\n")

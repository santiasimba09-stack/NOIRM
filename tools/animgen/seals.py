"""seals.py: hand seals (the twelve zodiac seals, plus the clone seal) and each jutsu's sequence.

Writes, for every jutsu listed in SEQUENCES:
  data/noirm/jutsu_seals/<nature>/<name>.json   {"seals": [...]}  (the game names them as you cast)
  assets/noirm/player_animation/seals_<nature>_<name>.json   the hands going through the sequence
Charged and channelled jutsu play the sequence while you charge; tap jutsu play it sped up
(2 ticks a seal, at most 12) before they fire, unless their JSON says "quick": true.
Run from the repository root: python3 tools/animgen/seals.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from motion import Anim, P  # noqa: E402

DATA = "src/main/resources/data/noirm/jutsu_seals"
TICKS_PER_SEAL = 3
CHEST_DROP = 40  # degrees
CHEST_UNBEND = 42  # degrees less elbow flex

# Each seal: (arm pitch, inward swing, elbow bend) for the right arm, the left arm, and a small
# torso turn. Arms pitch negative = raised forward; the hands meet in front of the chest.
SEAL_SHAPES = {
    "rat":    ((-70, -38, 0, 112), (-58, 40, 0, 122), 4),
    "ox":     ((-62, -48, 0, 128), (-62, 48, 0, 128), 0),
    "tiger":  ((-84, -36, 0, 104), (-84, 36, 0, 104), 0),
    "hare":   ((-72, -30, 0, 118), (-60, 44, 0, 120), -5),
    "dragon": ((-92, -34, 0, 98), (-88, 36, 0, 100), 0),
    "snake":  ((-60, -42, 0, 116), (-60, 42, 0, 116), 3),
    "horse":  ((-86, -26, 0, 78), (-86, 26, 0, 78), 0),
    "ram":    ((-76, -38, 0, 110), (-76, 38, 0, 110), 0),
    "monkey": ((-66, -34, 0, 100), (-74, 34, 0, 106), -3),
    "bird":   ((-78, -28, 0, 96), (-70, 30, 0, 100), 5),
    "dog":    ((-56, -46, 0, 124), (-66, 44, 0, 120), -4),
    "boar":   ((-50, -30, 0, 90), (-50, 30, 0, 90), 0),
    "clone":  ((-80, -40, 0, 106), (-80, 40, 0, 106), 0),
}

# Canon sequences where the series shows them; short plausible ones otherwise.
SEQUENCES = {
    "katon/great_fireball": ["snake", "ram", "monkey", "boar", "horse", "tiger"],
    "katon/great_fire_annihilation": ["snake", "ram", "monkey", "boar", "horse", "tiger"],
    "katon/phoenix_flower": ["rat", "tiger", "dog", "ox", "hare", "tiger"],
    "katon/fire_dragon": ["snake", "dragon", "hare", "tiger"],
    "katon/fire_stream": ["boar", "horse", "tiger"],
    "suiton/water_dragon": ["ox", "monkey", "hare", "rat", "boar", "bird", "ox", "horse", "bird", "tiger"],
    "suiton/great_waterfall": ["tiger", "rat", "dog", "ox", "hare", "tiger"],
    "suiton/water_bullet": ["tiger"],
    "suiton/water_prison": ["ram", "snake", "rat"],
    "suiton/water_formation_wall": ["tiger", "ox", "dog"],
    "doton/earth_wall": ["tiger", "hare", "boar", "dog"],
    "doton/earth_dragon": ["boar", "dog", "tiger", "snake"],
    "doton/earth_spikes": ["ram", "dog"],
    "doton/mud_swamp": ["rat", "snake", "boar"],
    "futon/great_breakthrough": ["tiger", "ox", "dog"],
    "futon/vacuum_bullets": ["ram", "dog"],
    "futon/pressure_damage": ["boar", "dog", "bird"],
    "raiton/chidori": ["ox", "hare", "monkey"],
    "universal/substitution": ["ram", "boar", "ox", "dog", "snake"],
    "universal/shadow_clone": ["clone"],
    "universal/body_flicker": ["ram"],
    "yin/shadow_imitation": ["rat"],
    "yin/demonic_illusion": ["ram"],
    "bakuton/explosive_clay": ["ram"],
    "bakuton/landmine": ["ram"],
    "mokuton/wood_binding": ["boar", "ram", "snake"],
    "mokuton/wood_spears": ["ram", "snake"],
    "hyoton/ice_prison": ["ram", "snake"],
    "yoton/melting_apparition": ["horse", "tiger"],
    "futton/skilled_mist": ["ram", "snake", "tiger"],
}

LEGS = dict(left_leg=(-8, 0, -4, 14), right_leg=(8, 0, 6, 12))
NEUTRAL = P(torso=(6, 0, 0, 4), right_arm=(-30, 0, 5, 30), left_arm=(-30, 0, -5, 30),
            left_leg=(-10, 0, -4, 16), right_leg=(10, 0, 6, 14))


def seal_pose(seal):
    right, left, turn = SEAL_SHAPES[seal]
    # The shapes are written for raised hands; drop them to chest height so the face stays visible.
    right = (right[0] + CHEST_DROP, right[1], right[2], right[3] - CHEST_UNBEND)
    left = (left[0] + CHEST_DROP, left[1], left[2], left[3] - CHEST_UNBEND)
    return P(torso=(-2, turn, 0, 0), head=(-4, 0, 0), right_arm=right, left_arm=left, **LEGS)


def sequence_anim(name, seals):
    """Each seal is formed (a quick snap), held, then the hands move to the next."""
    anim = Anim(name, TICKS_PER_SEAL * len(seals) + 4)
    anim.key(0, NEUTRAL)
    t = 2.0
    for seal in seals:
        pose = seal_pose(seal)
        anim.key(t, pose, "easeOutCubic")
        anim.key(t + TICKS_PER_SEAL - 1, pose, "linear")
        t += TICKS_PER_SEAL
    anim.key(anim.ticks, seal_pose(seals[-1]), "easeOutQuad")
    return anim


def anim_name(path):
    return "seals_" + path.replace("/", "_")


if __name__ == "__main__":
    for path, seals in SEQUENCES.items():
        assert all(s in SEAL_SHAPES for s in seals), path
        assert os.path.exists(f"src/main/resources/data/noirm/jutsu/{path}.json"), path
        out = os.path.join(DATA, path + ".json")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w") as f:
            json.dump({"seals": seals}, f)
            f.write("\n")
        sequence_anim(anim_name(path), seals).write()
        print("wrote", path)

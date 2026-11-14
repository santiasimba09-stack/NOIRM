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
    "katon/burning_ash": ["snake", "ram", "boar", "horse", "tiger"],
    "katon/majestic_destroyer_flame": ["snake", "ram", "monkey", "boar", "horse", "tiger"],
    "katon/flame_bomb": ["boar", "dog", "tiger"],
    # Rensai the Scholar's own jutsu (data/noirm/masters/scholar_rensai.json): short sequences, ours.
    "katon/ember_net": ["rat", "dog", "tiger"],
    "suiton/tide_lance": ["ox", "snake", "tiger"],
    "futon/gale_blades": ["dog", "bird"],
    "raiton/thunder_snare": ["ram", "monkey", "rat"],
    "doton/stone_fist": ["boar", "ox", "dog"],
    "katon/dragon_flame_caterwaul": ["snake", "dragon", "hare", "dragon", "tiger"],
    "suiton/water_dragon": ["ox", "monkey", "hare", "rat", "boar", "bird", "ox", "horse", "bird", "tiger"],
    "suiton/great_waterfall": ["tiger", "rat", "dog", "ox", "hare", "tiger"],
    "suiton/water_bullet": ["tiger"],
    "suiton/water_prison": ["ram", "snake", "rat"],
    "suiton/water_formation_wall": ["tiger", "ox", "dog"],
    # Water Release's own S-rank (ours): the sea opens, closes, and the leviathan rises.
    "suiton/leviathan_maelstrom": ["boar", "dog", "rat", "bird", "dragon", "snake", "ox", "tiger"],
    "doton/earth_wall": ["tiger", "hare", "boar", "dog"],
    "doton/earth_dragon": ["boar", "dog", "tiger", "snake"],
    "doton/earth_spikes": ["ram", "dog"],
    "doton/mud_swamp": ["rat", "snake", "boar"],
    "futon/great_breakthrough": ["tiger", "ox", "dog"],
    "futon/vacuum_bullets": ["ram", "dog"],
    "futon/pressure_damage": ["boar", "dog", "bird"],
    "raiton/chidori": ["ox", "hare", "monkey"],
    "raiton/chain_discharge": ["ox", "hare", "dog", "monkey"],
    "universal/substitution": ["ram", "boar", "ox", "dog", "snake"],
    "universal/shadow_clone": ["clone"],
    "universal/body_flicker": ["ram"],
    "yin/shadow_imitation": ["rat"],
    "yin/demonic_illusion": ["ram"],
    # Chakra wave: Yin, Yang, Yin-Yang and medical ninjutsu (canon where the series shows them).
    "universal/genjutsu_release": ["ram"],
    "yin/false_surroundings": ["ram", "snake", "bird"],
    "yin/shadow_neck_bind": ["rat"],
    "yin/shadow_stitching": ["rat", "snake", "tiger"],
    "yin/tree_binding_death": ["boar", "dog", "bird", "monkey", "ram"],
    "yang/rapid_regeneration": ["snake", "tiger"],
    "yang/yang_vitality": ["tiger", "ram"],
    "yang/super_expansion_fist": ["boar", "ox", "tiger"],
    "yang/life_force_conversion": ["dog", "boar"],
    "yang/life_reincarnation": ["boar", "dog", "bird", "monkey", "ram", "tiger"],
    "yin_yang/renewal": ["ram", "tiger"],
    "yin_yang/attraction": ["rat", "ox"],
    "yin_yang/repulsion": ["ox", "rat", "tiger"],
    "yin_yang/chakra_hawks": ["bird", "dragon", "bird"],
    "yin_yang/equilibrium_collapse": ["rat", "ox", "dragon", "snake", "tiger"],
    "medical/deep_mend": ["snake", "ram"],
    "medical/scalpel_strike": ["boar"],
    "medical/poison_mist": ["snake", "dog", "boar"],
    "medical/poison_extraction": ["ram", "snake"],
    "medical/healing_field": ["ox", "ram", "snake", "dog"],
    "medical/body_pathway_derangement": ["monkey", "hare"],
    "medical/hundred_healings": ["dragon", "snake", "ram", "boar", "tiger", "ox", "ram"],
    "bakuton/explosive_clay": ["ram"],
    "bakuton/landmine": ["ram"],
    "mokuton/wood_binding": ["boar", "ram", "snake"],
    "mokuton/wood_spears": ["ram", "snake"],
    # The chakra wave's Wood Release (WoodTechniques): plausible sequences, ours.
    "mokuton/root_snare": ["snake", "boar"],
    "mokuton/wood_clone": ["snake", "ram", "clone"],
    "mokuton/wood_locking_wall": ["dog", "ox", "ram"],
    "mokuton/wood_dragon": ["snake", "dragon", "boar", "ram"],
    "mokuton/deep_forest_emergence": ["snake", "dragon", "ram", "boar", "ox", "tiger"],
    "mokuton/thousand_arm_colossus": ["tiger", "boar", "dragon", "snake", "ox", "ram", "ram"],
    "hyoton/ice_prison": ["ram", "snake"],
    "yoton/melting_apparition": ["horse", "tiger"],
    "futton/skilled_mist": ["ram", "snake", "tiger"],
    # The chakra wave, share A (Ice, Lava, Boil, Storm, Explosion): short sequences, ours unless canon shows one.
    "hyoton/ice_spear": ["ram", "dragon"],
    "hyoton/freezing_field": ["ram", "snake", "ox"],
    "hyoton/ice_dragon": ["dragon", "snake", "ram", "dragon"],
    "hyoton/demonic_mirrors": ["tiger", "ram", "snake", "dragon", "tiger"],
    "yoton/magma_bullet": ["horse", "tiger"],
    "yoton/quicklime_trap": ["boar", "horse", "ox"],
    "yoton/lava_armor": ["dog", "boar", "horse", "tiger"],
    "yoton/volcanic_eruption": ["boar", "dog", "horse", "ox", "tiger", "dragon"],
    "futton/acid_mist": ["snake", "tiger"],
    "futton/scalding_geyser": ["rat", "boar", "tiger"],
    "futton/vapour_armor": ["ram", "boar", "ox", "tiger"],
    "futton/pressure_dome": ["snake", "ram", "boar", "ox", "dragon", "tiger"],
    "ranton/black_cloud": ["ox", "rat"],
    "ranton/light_fang": ["ox", "hare", "dragon"],
    "ranton/thunder_tide": ["ox", "hare", "boar", "rat", "tiger"],
    "ranton/hundred_bends": ["ox", "hare", "dragon", "monkey", "dog", "tiger"],
    "bakuton/clay_swallows": ["ram"],
    "bakuton/detonating_fist": ["ram", "tiger"],
    "bakuton/clay_effigy": ["ram", "snake", "ram"],
    "bakuton/final_art": ["ram", "tiger", "snake", "ram"],
    # Chakra wave: Scorch, Dust, Magnet, Wind, Earth (canon sequences where known, ours otherwise)
    "shakuton/heat_mote": ["ram", "tiger"],
    "shakuton/scorching_halo": ["snake", "ram", "tiger"],
    "shakuton/swarm_of_suns": ["horse", "snake", "ram", "tiger"],
    "shakuton/steaming_murder": ["ram", "snake", "horse", "bird", "tiger"],
    "jinton/detachment_needle": ["ram", "tiger"],
    "jinton/detachment_shell": ["tiger", "ram", "snake"],
    "jinton/cone_detachment": ["ram", "tiger", "ox"],
    "jinton/expanding_cube": ["tiger", "ram", "ox", "tiger"],
    "jiton/lodestone_pull": ["dog", "boar"],
    "jiton/iron_sand_shield": ["ram", "boar"],
    "jiton/iron_sand_arm": ["boar", "dog", "ram"],
    "jiton/iron_sand_rain": ["dog", "ram", "boar", "tiger"],
    "jiton/world_order": ["ram", "boar", "dog", "snake", "tiger"],
    "futon/wind_blade": ["dog"],
    "futon/vacuum_blast": ["ram", "dog", "bird"],
    "futon/gale_wall": ["tiger", "dog", "bird"],
    "futon/vacuum_wave": ["boar", "dog", "bird", "tiger"],
    "futon/raging_tornado": ["boar", "dog", "bird", "dragon", "tiger"],
    "doton/headhunter": ["snake", "boar"],
    "doton/rock_hounds": ["boar", "dog", "ox"],
    "doton/earth_flow_rampart": ["tiger", "hare", "boar", "dog", "ox"],
    "doton/added_weight_rock": ["ram", "boar", "ox"],
    "doton/falling_mountain": ["ram", "dog", "boar", "ox", "tiger"],
}

LEGS = dict(left_leg=(-16, 0, -9, 24), right_leg=(14, 0, 11, 22), body=dict(pos=(0, -1.2, 0)))  # a wide, grounded base
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
        anim.key(t, pose, "snap")  # snapped into the seal, then held (the springs keep it crisp)
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

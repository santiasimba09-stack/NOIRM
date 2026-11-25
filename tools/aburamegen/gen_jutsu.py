"""Writes the Aburame techniques (data/noirm/jutsu/aburame/*.json) from one table (docs/ABURAME.md, 2.5).

    python3 tools/aburamegen/gen_jutsu.py

No hand seals (there are no jutsu_seals files for these): the "hiden" block marks them as insect
techniques. Tune the numbers here (or in the JSON files directly) and rerun.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/data/noirm/jutsu/aburame")

# name: (behavior, cast, rank, chakra, cooldown, hive rank, insects, share, species, mode, drain, params, extra)
T = {
    "parasitic_swarm": ("swarm_send", "tap", "C", 4, 60, 1, 1500, 0.10, "kikaichu", "cling", 1.0, {"range": 28}, {}),
    "female_insect": ("swarm_mark", "tap", "D", 2, 40, 1, 1, 0, "kikaichu", "cling", 1.0, {"range": 6, "duration": 24000}, {}),
    "insect_clone": ("swarm_clone", "tap", "C", 8, 160, 2, 2500, 0, "kikaichu", "cling", 1.0, {"range": 40}, {}),
    "insect_wall": ("swarm_wall", "tap", "C", 6, 200, 2, 3000, 0, "kikaichu", "wall", 1.0, {"duration": 160}, {}),
    "scout_swarm": ("swarm_scout", "tap", "D", 3, 200, 2, 300, 0, "kikaichu", "scout", 1.0, {"range": 200}, {}),
    "poison_draw": ("poison_draw", "channel", "C", 5, 100, 2, 0, 0, "kikaichu", "cling", 1.0, {"range": 3}, {"max_duration": 200}),
    "insect_gathering": ("insect_gathering", "tap", "D", 3, 400, 2, 0, 0, "none", "cling", 1.0, {"range": 16}, {}),
    "insect_cocoon": ("insect_cocoon", "channel", "C", 0, 200, 2, 0, 0, "none", "cling", 1.0, {}, {"max_duration": 2400}),
    "insect_sphere": ("swarm_sphere", "tap", "B", 12, 300, 3, 6000, 0, "kikaichu", "sphere", 1.0, {"range": 24}, {}),
    "insect_jamming": ("swarm_jam", "tap", "B", 10, 900, 3, 4000, 0, "kikaichu", "jam", 1.0, {"duration": 600}, {}),
    "insect_jar": ("swarm_dome", "channel", "B", 4, 240, 3, 5000, 0, "kikaichu", "dome", 1.0, {}, {"max_duration": 120}),
    "insect_tether": ("swarm_tether", "tap", "D", 2, 30, 3, 400, 0, "kikaichu", "tether", 1.0, {"range": 20}, {}),
    "insect_tornado": ("swarm_tornado", "channel", "A", 8, 600, 4, 12000, 0, "kikaichu", "tornado", 1.0, {"range": 20}, {"max_duration": 100}),
    "bug_bite": ("bug_bite", "tap", "A", 15, 400, 4, 300, 0, "kidaichu", "cling", 1.0, {"range": 3}, {}),
    "chakra_gift": ("chakra_gift", "tap", "B", 0, 300, 4, 1000, 0, "none", "cling", 1.0, {"range": 6}, {}),
    "poison_cloud": ("poison_cloud", "charge", "A", 20, 600, 4, 3000, 0, "rinkaichu", "poison", 1.0, {"duration": 120},
                     {"min_charge": 10, "max_charge": 30}),
    "giant_insect": ("giant_insect", "tap", "A", 30, 600, 5, 0, 0, "none", "cling", 1.0, {}, {}),
    "jar_of_poison": ("jar_of_poison", "tap", "S", 60, 24000, 5, 0, 0, "none", "jar", 1.0, {}, {}),
    "living_hive": ("living_hive", "tap", "S", 0, 40, 6, 0, 0, "none", "cling", 1.0, {}, {}),
    "black_sea": ("black_sea", "tap", "S", 40, 2400, 6, 0, 0, "none", "sea", 2.0, {}, {}),
    "insect_legion": ("insect_legion", "tap", "S", 30, 2400, 6, 10000, 0, "kikaichu", "cling", 1.0, {}, {}),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (beh, cast, rank, chakra, cd, hrank, insects, share, species, mode, drain, params, extra) in T.items():
        d = {"cast": cast, "behavior": beh, "rank": rank, "chakra_cost": chakra, "cooldown": cd}
        d.update(extra)
        hiden = {"clan": "aburame", "rank": hrank, "species": species, "mode": mode}
        if insects:
            hiden["insects"] = insects
        if share:
            hiden["share"] = share
        if drain != 1.0:
            hiden["drain"] = drain
        d["hiden"] = hiden
        if params:
            d["params"] = params
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(d, f, indent=2)
            f.write("\n")
    print("wrote", len(T), "techniques")


if __name__ == "__main__":
    main()

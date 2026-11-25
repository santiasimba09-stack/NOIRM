"""Sets the Aburame clan's perks (docs/ABURAME.md, 2.12) in data/noirm/clans/aburame.json.

Poison immunity is no longer inborn (owner's decision 2: earned by breeding, Shino's route).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
P = os.path.join(ROOT, "src/main/resources/data/noirm/clans/aburame.json")

d = json.load(open(P))
d["perks"] = {"colony": 1, "dot_resist": 0.6, "sensor": 16, "chakra_leech": 4, "start_control": 3, "chakra_pool": 0.3,
              "fire_weakness": 0.3, "xp_speed": -0.2, "xp_taijutsu": -0.15, "genjutsu_resist": 0.4}
d["clan_jutsu"] = ["noirm:aburame/parasitic_swarm", "noirm:aburame/female_insect"]
with open(P, "w") as f:
    json.dump(d, f, indent=2)
    f.write("\n")
print("aburame.json updated")

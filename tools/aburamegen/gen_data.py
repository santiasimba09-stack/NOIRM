"""Writes the Aburame's recipes and block loot tables.

    python3 tools/aburamegen/gen_data.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/noirm")


def write(path, data):
    p = os.path.join(DATA, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def shaped(name, pattern, key, count=1):
    write(f"recipes/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                    "key": {k: v for k, v in key.items()}, "result": {"item": f"noirm:{name}", "count": count}})


shaped("insect_hive", ["LHL", "HPH", "LHL"], {"L": {"tag": "minecraft:logs"}, "H": {"item": "minecraft:honeycomb"},
                                               "P": {"item": "noirm:chakra_paper"}})
shaped("breeding_jar", ["GCG", "GHG", "GCG"], {"G": {"item": "minecraft:glass"}, "C": {"item": "minecraft:clay_ball"},
                                                "H": {"item": "noirm:insect_hive"}})
shaped("insect_jar", [" S ", "C C", "CCC"], {"S": {"item": "minecraft:string"}, "C": {"item": "minecraft:clay_ball"}})
shaped("insect_gourd", [" S ", "J J", "JLJ"], {"S": {"item": "minecraft:string"}, "J": {"item": "noirm:insect_jar"},
                                              "L": {"item": "minecraft:leather"}})
shaped("insect_case", ["PPP", "GBG", "PPP"], {"P": {"tag": "minecraft:planks"}, "G": {"item": "minecraft:glass_pane"},
                                             "B": {"item": "minecraft:book"}})
shaped("bug_net", ["  W", " S ", "S  "], {"W": {"item": "minecraft:cobweb"}, "S": {"item": "minecraft:stick"}})
shaped("aburame_gloves", ["L L", "B B"], {"L": {"item": "minecraft:leather"}, "B": {"item": "minecraft:black_dye"}})
write("recipes/wild_grass_salad.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                        "ingredients": [{"item": "minecraft:bowl"}, {"item": "minecraft:short_grass"} if False else {"item": "minecraft:grass"},
                                                        {"item": "minecraft:fern"}, {"item": "minecraft:dandelion"}],
                                        "result": {"item": "noirm:wild_grass_salad"}})
for b in ("insect_hive", "breeding_jar", "pitcher_plant"):
    write(f"loot_tables/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"noirm:{b}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
print("Aburame recipes and loot written")

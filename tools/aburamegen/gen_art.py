"""Draws the Aburame's pixel art from scratch (our own art: no copied images), and writes the models.

    python3 tools/aburamegen/gen_art.py

- entity/aburame/<species>.png: the voxel beetle's 16x16 skin (see client/aburame/BeetleMesh for the layout),
  after the owner's reference 33 (charcoal segmented shell, pale cracked shield, teal eyes);
- entity/aburame/giant_insect.png, wild.png;
- item/*.png, block/*.png, mob_effect/*.png; the item/block models and blockstates.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def save(img, *path):
    p = os.path.join(ASSETS, "textures", *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


SPECIES = {
    "kikaichu": ("#2b2b30", "#6e6a78", "#3fa8a8"),
    "kidaichu": ("#3a2a22", "#8a6a50", "#d0a040"),
    "shokaichu": ("#5a4a3a", "#9a8670", "#7a6a50"),
    "rinkaichu": ("#2a1830", "#5a3a6a", "#b060d0"),
    "kochu": ("#302020", "#6a4040", "#d04040"),
}


def beetle_skin(shell, shield, eyes, rnd):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    s, sh, e = rgb(shell), rgb(shield), rgb(eyes)
    # top face 3 wide x 4 long: row 0 head with eyes, row 1 the shield, rows 2-3 the shell with a seam
    px[0, 0], px[1, 0], px[2, 0] = e, shade(s, 0.8), e
    px[0, 1], px[1, 1], px[2, 1] = shade(sh, 0.9), sh, shade(sh, 1.1)
    px[0, 2], px[1, 2], px[2, 2] = s, shade(s, 0.6), shade(s, 1.35)
    px[0, 3], px[1, 3], px[2, 3] = shade(s, 1.15), shade(s, 0.6), s
    # underside: dark with leg stripes
    for x in range(4, 7):
        for y in range(0, 4):
            px[x, y] = shade(s, 0.55 if (x + y) % 2 else 0.4)
    # sides: shell with a light rim and leg hint below
    for x in range(8, 12):
        px[x, 0] = shade(s, 1.2)
        px[x, 1] = shade(s, 0.5) if x % 2 else shade(s, 0.8)
    # front/back: eyes on the front
    px[12, 0], px[13, 0], px[14, 0] = e, shade(s, 0.9), e
    px[12, 1], px[13, 1], px[14, 1] = shade(s, 0.7), shade(s, 0.7), shade(s, 0.7)
    # wings: pale, veined
    for x in range(0, 4):
        for y in range(6, 10):
            v = 200 + rnd.randint(-15, 15)
            px[x, y] = (v - 20, v - 5, v + 10, 255) if (x + y) % 3 else (140, 150, 165, 255)
    return img


def gen_entities():
    rnd = random.Random(33)
    for k, (s, sh, e) in SPECIES.items():
        save(beetle_skin(s, sh, e, rnd), "entity", "aburame", k + ".png")
    # the Giant Insect: red, yellow and black
    g = beetle_skin("#b02a1e", "#e8c040", "#101010", rnd)
    px = g.load()
    px[1, 2], px[1, 3] = rgb("#101010"), rgb("#101010")
    px[0, 3], px[2, 2] = rgb("#e8c040"), rgb("#e8c040")
    save(g, "entity", "aburame", "giant_insect.png")
    save(beetle_skin("#9a9a9a", "#d0d0d0", "#ffffff", rnd), "entity", "aburame", "wild.png")


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def fill(px, box, c):
    x0, y0, x1, y1 = box
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            px[x, y] = c


def jar(gourd):
    img = canvas()
    px = img.load()
    clay, dark, rim = rgb("#b89a74"), rgb("#7a5e44"), rgb("#d8c4a0")
    if gourd:
        clay, dark, rim = rgb("#d8cfb8"), rgb("#9a8e72"), rgb("#efe8d6")
        fill(px, (5, 1, 10, 6), clay)
        fill(px, (3, 6, 12, 14), clay)
        for y in range(6, 15):
            px[3, y] = dark
            px[12, y] = dark
        fill(px, (6, 0, 9, 0), rgb("#5a4030"))
        fill(px, (3, 9, 12, 9), rgb("#6a5a8a"))  # the harness strap
    else:
        fill(px, (4, 4, 11, 14), clay)
        fill(px, (5, 2, 10, 3), rim)
        fill(px, (6, 1, 9, 1), rgb("#5a4030"))
        for y in range(4, 15):
            px[4, y] = dark
            px[11, y] = dark
    for (x, y) in [(6, 8), (9, 10), (7, 12), (8, 7)]:
        px[x, y] = rgb("#2b2b30")
    return img


def gen_items():
    save(jar(False), "item", "insect_jar.png")
    save(jar(True), "item", "insect_gourd.png")
    # the case: a dark wooden box with three pinned bugs
    img = canvas()
    px = img.load()
    fill(px, (1, 3, 14, 13), rgb("#4a3424"))
    fill(px, (2, 4, 13, 12), rgb("#e8dcc0"))
    for (x, y, c) in [(4, 6, "#2b2b30"), (8, 7, "#1a6a3a"), (11, 9, "#d0b040"), (5, 10, "#3a2416")]:
        px[x, y] = rgb(c)
        px[x + 1, y] = shade(rgb(c), 1.3)
    save(img, "item", "insect_case.png")
    # the net: a stick and a ring with mesh
    img = canvas()
    px = img.load()
    for i in range(7):
        px[2 + i, 13 - i] = rgb("#8a6a40")
    for a in range(16):
        import math
        x = int(10 + 4 * math.cos(a * math.pi / 8))
        y = int(5 + 4 * math.sin(a * math.pi / 8))
        px[x, y] = rgb("#d0d0d0")
    for (x, y) in [(9, 4), (11, 6), (10, 5), (9, 6), (11, 4)]:
        px[x, y] = (220, 220, 220, 140)
    save(img, "item", "bug_net.png")
    # gloves
    img = canvas()
    px = img.load()
    fill(px, (3, 6, 7, 13), rgb("#1a1a1e"))
    fill(px, (9, 6, 13, 13), rgb("#1a1a1e"))
    for x in (3, 5, 7, 9, 11, 13):
        fill(px, (x, 3, x, 6), rgb("#26262c"))
    fill(px, (3, 12, 7, 13), rgb("#3a3a44"))
    fill(px, (9, 12, 13, 13), rgb("#3a3a44"))
    save(img, "item", "aburame_gloves.png")

    def egg(body, spots):
        im = canvas()
        p = im.load()
        for y in range(3, 14):
            w = int(4 * (1 - ((y - 8.5) / 6) ** 2) ** 0.5) + 1
            for x in range(8 - w, 8 + w):
                p[x, y] = rgb(body)
        for (x, y) in spots:
            p[x, y] = rgb("#4a3a2a")
        return im

    save(egg("#e8dcc0", [(6, 7), (9, 10), (8, 5)]), "item", "kidaichu_egg.png")
    save(egg("#c09060", [(7, 6), (9, 9), (6, 11)]), "item", "bikochu_egg.png")
    img = egg("#f0ece0", [])
    px = img.load()
    for y in range(4, 13, 2):
        for x in range(4, 12):
            if px[x, y][3]:
                px[x, y] = rgb("#d8d0b8")
    fill(px, (7, 7, 8, 9), rgb("#b02a1e"))
    save(img, "item", "queen_cocoon.png")
    # the bikochu: a little copper beetle
    img = canvas()
    px = img.load()
    fill(px, (6, 4, 9, 12), rgb("#6a3a2a"))
    fill(px, (6, 4, 9, 5), rgb("#e0a070"))
    px[6, 3], px[9, 3] = rgb("#e0a070"), rgb("#e0a070")
    for y in (6, 8, 10):
        px[5, y] = rgb("#3a2016")
        px[10, y] = rgb("#3a2016")
    save(img, "item", "bikochu.png")
    # salad: a bowl of wild greens
    img = canvas()
    px = img.load()
    fill(px, (3, 9, 12, 12), rgb("#8a6a40"))
    fill(px, (4, 13, 11, 13), rgb("#6a4e2e"))
    rnd = random.Random(7)
    for _ in range(26):
        x, y = rnd.randint(3, 12), rnd.randint(4, 9)
        px[x, y] = rgb(rnd.choice(["#4a8a2a", "#6aaa3a", "#90c050", "#3a6a20"]))
    save(img, "item", "wild_grass_salad.png")


def gen_blocks():
    rnd = random.Random(11)
    # the hive: dark clay with holes and lighter bands
    side = canvas()
    px = side.load()
    for x in range(16):
        for y in range(16):
            base = rgb("#5a4030") if (y // 4) % 2 else rgb("#4a3424")
            px[x, y] = shade(base, 0.9 + rnd.random() * 0.2)
    for (x, y) in [(3, 2), (10, 3), (6, 6), (12, 9), (2, 10), (8, 12), (13, 14), (5, 14)]:
        px[x, y] = rgb("#120c08")
        px[x + 1, y] = rgb("#1e1410")
    save(side, "block", "insect_hive_side.png")
    top = canvas()
    px = top.load()
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            px[x, y] = shade(rgb("#5a4030"), 1.1 - d / 20)
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        px[x, y] = rgb("#120c08")
    save(top, "block", "insect_hive_top.png")
    # the breeding jar: glass with a clay base and a frame inside
    glass = canvas()
    px = glass.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = (200, 220, 225, 70)
    for x in range(16):
        px[x, 0] = px[x, 15] = (230, 240, 240, 160)
    for y in range(16):
        px[0, y] = px[15, y] = (230, 240, 240, 160)
    fill(px, (0, 12, 15, 15), rgb("#a07850"))
    fill(px, (4, 3, 11, 11), (90, 70, 50, 200))
    for (x, y) in [(5, 5), (8, 6), (10, 8), (6, 9), (9, 4)]:
        px[x, y] = rgb("#2b2b30")
    save(glass, "block", "breeding_jar.png")
    # the pitcher plant
    pl = canvas()
    px = pl.load()
    fill(px, (6, 4, 9, 14), rgb("#5a8a2a"))
    fill(px, (5, 3, 10, 4), rgb("#a03a3a"))
    fill(px, (7, 5, 8, 9), rgb("#2a1a12"))
    for y in range(6, 14, 3):
        px[6, y] = rgb("#a03a3a")
        px[9, y + 1] = rgb("#a03a3a")
    fill(px, (3, 12, 5, 13), rgb("#3a6a20"))
    fill(px, (10, 11, 12, 12), rgb("#3a6a20"))
    save(pl, "block", "pitcher_plant.png")


def gen_effects():
    for name, c in [("drained", "#2b2b30"), ("exhausted", "#5a5068"), ("cell_rot", "#6a2a7a")]:
        img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        px = img.load()
        rnd = random.Random(name)
        for _ in range(14):
            x, y = rnd.randint(3, 13), rnd.randint(3, 13)
            px[x, y] = rgb(c)
            px[x + 1, y] = shade(rgb(c), 1.4)
            px[x, y + 1] = rgb("#3fa8a8") if name != "cell_rot" else rgb("#b060d0")
        save(img, "mob_effect", name + ".png")


def write(path, data):
    p = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def gen_models():
    for item in ["insect_jar", "insect_gourd", "insect_case", "bug_net", "aburame_gloves", "kidaichu_egg", "queen_cocoon",
                 "bikochu_egg", "bikochu", "wild_grass_salad"]:
        parent = "minecraft:item/handheld" if item == "bug_net" else "minecraft:item/generated"
        write(f"models/item/{item}.json", {"parent": parent, "textures": {"layer0": f"noirm:item/{item}"}})
    write("models/item/pitcher_plant.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "noirm:block/pitcher_plant"}})
    write("models/item/insect_hive.json", {"parent": "noirm:block/insect_hive"})
    write("models/item/breeding_jar.json", {"parent": "noirm:block/breeding_jar"})
    write("models/block/insect_hive.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "noirm:block/insect_hive_side", "side": "noirm:block/insect_hive_side", "top": "noirm:block/insect_hive_top"},
        "elements": [
            {"from": [2, 0, 2], "to": [14, 12, 14], "faces": {d: {"texture": "#side" if d not in ("up", "down") else "#top"}
                                                              for d in ("north", "south", "east", "west", "up", "down")}},
            {"from": [4, 12, 4], "to": [12, 15, 12], "faces": {d: {"texture": "#side" if d not in ("up", "down") else "#top"}
                                                              for d in ("north", "south", "east", "west", "up", "down")}},
        ]})
    write("models/block/breeding_jar.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": {"particle": "noirm:block/breeding_jar", "glass": "noirm:block/breeding_jar"},
        "elements": [
            {"from": [3, 0, 3], "to": [13, 14, 13], "faces": {d: {"texture": "#glass"} for d in ("north", "south", "east", "west", "up", "down")}},
        ]})
    write("models/block/pitcher_plant.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                              "textures": {"cross": "noirm:block/pitcher_plant"}})
    for b in ("insect_hive", "breeding_jar", "pitcher_plant"):
        write(f"blockstates/{b}.json", {"variants": {"": {"model": f"noirm:block/{b}"}}})


if __name__ == "__main__":
    gen_entities()
    gen_items()
    gen_blocks()
    gen_effects()
    gen_models()
    print("Aburame art written")

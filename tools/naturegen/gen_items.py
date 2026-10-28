"""Draws the item icons for natures and jutsu learning in NOIRM's ink style (docs/ART_STYLE.md):
the chakra paper, the five nature scrolls and the four jutsu scrolls (ranks C, B, A, S).

    python3 tools/naturegen/gen_items.py [--preview out.png]

Writes 16x16 icons (like the gear icons) to src/main/resources/assets/noirm/textures/item/ and
the matching item models to src/main/resources/assets/noirm/models/item/. Our own designs.

The look: a thick near-black ink outline, two-tone carved shading with the light from the top
left, gritty paper, blood red as the accent (wax seals), and colour only where chakra is: the
nature's colour on a nature scroll's cord and seal, chakra blue on the paper's seal.
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "src/main/resources/assets/noirm/textures/item")
MODELS = os.path.join(ROOT, "src/main/resources/assets/noirm/models/item")


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def hsh(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def gritty(c, x, y, salt, amount=0.08):
    return shade(c, 1 - amount + 2 * amount * hsh(x, y, salt))


INK = rgb(0x0E0C0E)
# Paper: hi, light, mid, dark
PAPER = [rgb(0xF6EED6), rgb(0xE6D8B4), rgb(0xCCB88E), rgb(0x9C8660)]
OLD_PAPER = [rgb(0xE8DCB8), rgb(0xD2C096), rgb(0xB49C70), rgb(0x806A48)]
WOOD = [rgb(0xA87A50), rgb(0x7C5634), rgb(0x5C3E26), rgb(0x3C2818)]
BLOOD = [rgb(0xCE3028), rgb(0xA01216), rgb(0x7A0E12), rgb(0x5C080C)]
CHAKRA = [rgb(0xB8E4FF), rgb(0x5EA8F0), rgb(0x2E62C8), rgb(0x162E78)]

NATURES = {
    "fire": 0xFF6A1F,
    "water": 0x2F8BFF,
    "wind": 0x9FE7B0,
    "earth": 0x9C7A4A,
    "lightning": 0xFFE14A,
}
RANK_SEALS = {
    "c": [rgb(0xD8D2C4), rgb(0xA8A090), rgb(0x7A7466), rgb(0x4A463E)],    # plain bone wax
    "b": CHAKRA,                                                            # blue
    "a": BLOOD,                                                             # blood red
    "s": [rgb(0xFFE6A0), rgb(0xD8A83C), rgb(0x9A762E), rgb(0x4E3810)],    # gold
}


def tones(hexcol):
    c = rgb(hexcol)
    return [shade(c, 1.3), c, shade(c, 0.72), shade(c, 0.45)]


def draw(template, palette, salt):
    """template rows: '.' empty, 'o' ink, then letters mapping to (material, tone)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(template):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == "o":
                px[x, y] = INK + (255,)
                continue
            mat, tone = palette[ch]
            c = mat[tone]
            px[x, y] = gritty(c, x, y, salt) + (255,)
    return img


# ---------------------------------------------------------------- chakra paper
# A small slip of paper, a corner curling up, a chakra-blue seal mark in the middle.
PAPER_T = [
    "................",
    "................",
    "...ooooooooo....",
    "...ohhhhhhhLoo..",
    "...ohpppppppLLo.",
    "...ohppppppppMo.",
    "...ohpppBBppppo.",
    "...ohppBbbBpppo.",
    "...ohppBbbBpppo.",
    "...ohpppBBppppo.",
    "...ohppppppppso.",
    "...ohpppppppsso.",
    "...ohppppppssso.",
    "...oosssssssso..",
    "....oooooooooo..",
    "................",
]


def chakra_paper():
    pal = {
        "h": (PAPER, 0), "p": (PAPER, 1), "s": (PAPER, 2), "L": (PAPER, 0), "M": (PAPER, 3),
        "B": (CHAKRA, 2), "b": (CHAKRA, 0),
    }
    return draw(PAPER_T, pal, 11)


# ---------------------------------------------------------------- nature scroll
# A rolled scroll lying on the diagonal (wooden knobs at both ends), tied in the middle with a
# cord in the nature's colour, a seal of that colour on the cord.
NATURE_T = [
    "................",
    "...........ooo..",
    "..........owwwo.",
    ".........ohhpwo.",
    "........ohhppso.",
    ".......ohhppso..",
    "......ohhpcso...",
    ".....ohhcCCo....",
    "....ohhcCKCo....",
    "...ohhpcCCso....",
    "..ohhppsccso....",
    ".owhppsso.......",
    "owwwpsso........",
    "owwwsso.........",
    ".owwoo..........",
    "..oo............",
]


def nature_scroll(name):
    col = tones(NATURES[name])
    pal = {
        "w": (WOOD, 1), "h": (OLD_PAPER, 0), "p": (OLD_PAPER, 1), "s": (OLD_PAPER, 2),
        "c": (col, 1), "C": (col, 2), "K": (col, 0),
    }
    return draw(NATURE_T, pal, 23 + len(name))


# ---------------------------------------------------------------- jutsu scroll
# An open scroll: a rolled end on the left, the sheet with ink lines, a wax seal by rank.
JUTSU_T = [
    "................",
    "................",
    ".oo.............",
    "owwoooooooooooo.",
    "owhohhhhhhhhhhLo",
    "owhopppppppppppo",
    "owhopIIIpIIIpppo",
    "owhoppppppppSSpo",
    "owhopIIpIIIpSXSo",
    "owhoppppppppSSso",
    "owhopIIIIpppppso",
    "owhoppppppppssso",
    "owwooooooooooooo",
    ".oo.............",
    "................",
    "................",
]


def jutsu_scroll(rank):
    seal = RANK_SEALS[rank]
    pal = {
        "w": (WOOD, 2), "h": (PAPER, 0), "p": (PAPER, 1), "s": (PAPER, 2), "L": (PAPER, 0),
        "I": ([INK, INK, INK, INK], 0), "S": (seal, 1), "X": (seal, 0),
    }
    return draw(JUTSU_T, pal, 37 + ord(rank))


def model(name):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": "noirm:item/" + name}}


def main():
    icons = {"chakra_paper": chakra_paper()}
    for n in NATURES:
        icons["nature_scroll_" + n] = nature_scroll(n)
    for r in RANK_SEALS:
        icons["jutsu_scroll_" + r] = jutsu_scroll(r)
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    for name, img in icons.items():
        img.save(os.path.join(TEX, name + ".png"))
        with open(os.path.join(MODELS, name + ".json"), "w") as f:
            json.dump(model(name), f)
            f.write("\n")
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        sheet = Image.new("RGBA", (len(icons) * 72 + 8, 80), (54, 56, 62, 255))
        for i, img in enumerate(icons.values()):
            big = img.resize((64, 64), Image.NEAREST)
            sheet.paste(big, (8 + i * 72, 8), big)
        sheet.save(out)
    print("wrote", len(icons), "icons")


if __name__ == "__main__":
    main()

"""Draws the bingo book's item icon in NOIRM's ink style (docs/ART_STYLE.md).

    python3 tools/bingogen/gen_book.py [--preview out.png]

Writes a 16x16 icon (like the other item icons) to src/main/resources/assets/noirm/textures/item/
and its item model to src/main/resources/assets/noirm/models/item/. Our own design.

The look: a worn, near-black leather book standing a little turned, a thick ink outline, bone-white
page edges on the right, a blood-red wax seal on the cover with a pale slash through it (a crossed-out
name), and a red ribbon hanging from the bottom.
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


def gritty(c, x, y, salt, amount=0.1):
    return shade(c, 1 - amount + 2 * amount * hsh(x, y, salt))


INK = rgb(0x0E0C0E)
LEATHER = [rgb(0x4A3A3E), rgb(0x34282C), rgb(0x241C20), rgb(0x161114)]
PAPER = [rgb(0xF2E8CE), rgb(0xD8C8A2), rgb(0xB0A07A)]
BLOOD = [rgb(0xD8362C), rgb(0xA81618), rgb(0x700C10)]
BONE = rgb(0xEDE4D3)

# '.' empty, 'o' ink, L/l/d/D leather light->dark, p/q/r paper, R/S/T blood, b bone slash, '1' ribbon
BOOK_T = [
    "................",
    "..ooooooooooo...",
    "..oLLLLLLLLlooo.",
    "..oLlllllllldpqo",
    "..oLllllllldopqo",
    "..oLlllRRRldopqo",
    "..oLllRSSbRdopqo",
    "..oLllRSbTRdopqo",
    "..oLllRbTTRdopqo",
    "..oLlllRRRldopro",
    "..oLllllllldopro",
    "..oLlllllldddoro",
    "..oDddddddddDooo",
    "..ooooo1oooooo..",
    "......o1o.......",
    ".......o........",
]


def book():
    pal = {
        "L": (LEATHER, 0), "l": (LEATHER, 1), "d": (LEATHER, 2), "D": (LEATHER, 3),
        "p": (PAPER, 0), "q": (PAPER, 1), "r": (PAPER, 2),
        "R": (BLOOD, 1), "S": (BLOOD, 0), "T": (BLOOD, 2), "1": (BLOOD, 1),
    }
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(BOOK_T):
        for x, ch in enumerate(row[:16]):
            if ch == ".":
                continue
            if ch == "o":
                px[x, y] = INK + (255,)
            elif ch == "b":
                px[x, y] = BONE + (255,)
            else:
                mat, tone = pal[ch]
                px[x, y] = gritty(mat[tone], x, y, 71) + (255,)
    # Scratches on the leather: a few lighter flecks.
    for (x, y) in [(5, 3), (9, 10), (4, 9), (10, 4)]:
        r, g, b, a = px[x, y]
        px[x, y] = shade((r, g, b), 1.35) + (a,)
    return img


def main():
    img = book()
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    img.save(os.path.join(TEX, "bingo_book.png"))
    with open(os.path.join(MODELS, "bingo_book.json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "noirm:item/bingo_book"}}, f)
        f.write("\n")
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        sheet = Image.new("RGBA", (16 * 8 + 16, 16 * 8 + 16), (54, 56, 62, 255))
        sheet.paste(img.resize((128, 128), Image.NEAREST), (8, 8), img.resize((128, 128), Image.NEAREST))
        sheet.save(out)


if __name__ == "__main__":
    main()

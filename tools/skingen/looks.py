"""Python twin of com.noirm.entity.NinjaLook (decode) and client/NinjaSkins (compose).

Only used for previews: the game does the same thing in Java. Keep the two in step:
same SplitMix64, same field order, same layer order, same tint rule.
"""
import json
import os

from pixlib import Canvas, hx, tint

M64 = (1 << 64) - 1
GOLDEN = 0x9E3779B97F4A7C15
FIELDS = ["skin", "hair_style", "hair_color", "eyes", "eye_color", "brows", "shirt", "shirt_color",
          "pants", "pants_color", "wraps", "mark", "accessory", "accessory_color"]


class SplitMix:
    def __init__(self, seed):
        self.state = seed & M64

    def next(self):
        self.state = (self.state + GOLDEN) & M64
        z = self.state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M64
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M64
        return z ^ (z >> 31)

    def next_int(self, n):
        return (self.next() >> 1) % n

    def pick(self, table):
        total = sum(e[1] for e in table)
        r = self.next_int(total)
        for value, weight in table:
            r -= weight
            if r < 0:
                return str(value)
        return str(table[-1][0])


def parse_key(key):
    folder, _, seed = key.partition(":")
    return folder, int(seed, 16) & M64


def decode(key, looks):
    folder, seed = parse_key(key)
    table = looks["clans"].get(folder) or looks["clans"]["generic"]
    rng = SplitMix(seed)
    f = {"folder": folder}
    for field in FIELDS:
        f[field] = rng.pick(table[field])
    family = seed >> 48
    if folder == "generic" and family != 0:
        fam = SplitMix((family * GOLDEN) & M64)
        fam_mark = fam.pick(looks["family"]["mark"])
        fam_hair = fam.pick(table["hair_color"])
        if rng.next_int(100) < looks["family"]["mark_chance"]:
            f["mark"] = fam_mark
        if rng.next_int(100) < looks["family"]["hair_chance"]:
            f["hair_color"] = fam_hair
    return f


def load_looks(parts_dir):
    with open(os.path.join(parts_dir, "looks.json")) as fh:
        return json.load(fh)


_PARTS = {}


def part(parts_dir, name):
    path = os.path.join(parts_dir, name + ".png")
    if path not in _PARTS:
        _PARTS[path] = Canvas.load(path) if os.path.exists(path) else None
    return _PARTS[path]


def layers(f, looks):
    """(part name, tint colour or None) in drawing order."""
    out = [("skin", f["skin"])]
    if f["mark"] != "none":
        out.append(("mark_" + f["mark"], None))
    out.append(("shirt_" + f["shirt"], f["shirt_color"]))
    out.append(("pants_" + f["pants"], f["pants_color"]))
    wraps = int(f["wraps"])
    if wraps & 1:
        out.append(("leg_wraps", None))
    if wraps & 2:
        out.append(("arm_wraps", None))
    out.append(("sandals", None))
    out.append(("eyes_" + f["eyes"], None))
    out.append(("iris_" + f["eyes"], f["eye_color"]))
    out.append(("brows_" + f["brows"], f["hair_color"]))
    out.append(("hair_" + f["hair_style"], f["hair_color"]))
    if f["accessory"] != "none":
        tinted = f["accessory"] in looks["tinted_accessories"]
        out.append(("acc_" + f["accessory"], f["accessory_color"] if tinted else None))
    return out


def compose(f, looks, parts_dir):
    cv = Canvas(64, 64)
    for name, colour in layers(f, looks):
        src = part(parts_dir, name)
        if src is None:
            continue
        c = hx(colour) if colour else None
        for y in range(64):
            for x in range(64):
                p = src.px[y][x]
                if p[3] == 0:
                    continue
                cv.blend(x, y, tint(c, p) if c else p)
    return cv

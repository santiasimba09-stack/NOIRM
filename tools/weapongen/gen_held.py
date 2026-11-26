"""Long held models for NOIRM's long weapons (yari, nodachi, bō staff). Run from the repository root:

    python3 tools/weapongen/gen_held.py [--preview out.png]

The inventory icon is drawn on the diagonal of a 16x16 square, so in the hand a long weapon came
out short (it can only be as long as that square's diagonal). Here each long weapon gets:

* a straight **held texture** (textures/item/held/<name>.png, 16x128: 4 x 32 icon units at the HD
  scale of 4 pixels a unit), drawn with the same shaders and materials as the icons
  (gen_weapons.py), plus a glow layer for weapons that take a chakra flow;
* an **item model** (models/item/<name>.json) using Forge's ``separate_transforms`` loader: in the
  hand (first and third person) the held texture, extruded like any item and stretched to the
  weapon's real length; everywhere else (inventory, ground, item frames, on the head) the usual
  icon. Weapons with a chakra flow get the same pair for their glowing variant (<name>_flow.json).

How it sits in the hand: vanilla holds a sword icon by its handle (around pixel 3.5, 12.5) with
the blade pointing along the icon's diagonal. We compute display transforms that put the held
texture's grip (a fraction along the weapon) exactly where vanilla puts the sword's handle and
point the weapon along the same line, just longer. So every animation made for swords holds these
weapons the same way.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_weapons as gw  # noqa: E402

TEX = "src/main/resources/assets/noirm/textures/item/held"
MODELS = "src/main/resources/assets/noirm/models/item"
WIDTH_UNITS, LENGTH_UNITS = 4, 32  # the held canvas in icon units (16 x 128 px at scale 4)
START, END = (2.0, LENGTH_UNITS - 0.4), (2.0, 0.4)  # butt at the bottom, tip at the top


def canvas():
    gw.configure(4)
    gw.W, gw.H = WIDTH_UNITS * gw.S, LENGTH_UNITS * gw.S
    return gw.Canvas()


# ---------------------------------------------------------------- the held textures

def yari(glow=False):
    cv = canvas()
    if glow:
        gw.along(cv, START, END, [
            (0.74, 0.83, 0.55, 1.45, gw.glow_blade(gw.WIND, 127)),
            (0.83, 1.00, 1.45, 0.25, gw.glow_blade(gw.WIND, 127)),
        ])
        return cv
    gw.along(cv, START, END, [
        (0.00, 0.03, 0.72, 0.72, gw.metal(gw.DSTEEL, 121)),                    # the butt cap
        (0.03, 0.70, 0.5, 0.5, gw.wood(gw.DARK_WOOD, 122, lacquer=gw.LACQ_RED.light)),
        (0.70, 0.735, 0.72, 0.72, gw.rope(gw.ROPE, 123)),                     # binding
        (0.735, 0.765, 0.62, 0.62, gw.metal(gw.DSTEEL, 124)),                 # collar
        (0.765, 0.83, 0.42, 1.15, gw.blade(gw.STEEL, 125, double=True)),
        (0.83, 1.00, 1.15, 0.0, gw.blade(gw.STEEL, 125, double=True, nicks=(0.93,))),  # straight leaf head
    ])
    # the blood-red tassel hanging from the collar down both sides of the shaft
    collar_v = START[1] + (END[1] - START[1]) * 0.735
    for i, dx in enumerate((-1.05, -0.6, 0.6, 1.05)):
        gw.along(cv, (2.0 + dx * 0.6, collar_v), (2.0 + dx, collar_v + 2.6 + 0.4 * (i % 2)), [
            (0.0, 1.0, 0.34, 0.2, lambda c, i=i: gw.gritty(gw.CLOTH_RED.light if c.n < -0.2 else gw.CLOTH_RED.dark, c.x, c.y, 126 + i, 0.08, 0.04))])
    gw.outline(cv)
    return cv


def nodachi(glow=False):
    cv = canvas()
    if glow:
        gw.along(cv, START, END, [
            (0.35, 0.92, 1.45, 1.3, gw.flame_blade(gw.FIRE, 97)),
            (0.92, 1.00, 1.3, 0.15, gw.flame_blade(gw.FIRE, 97)),
        ], curve=-0.35, curve_from=0.36)
        return cv
    gw.along(cv, START, END, [
        (0.00, 0.025, 0.95, 0.95, gw.metal(gw.DSTEEL, 91)),                   # kashira
        (0.025, 0.28, 0.82, 0.82, gw.wrap_diamond(gw.CLOTH, gw.CLOTH_RED, 92, 1.25)),  # long two-hand grip
        (0.28, 0.30, 0.95, 0.95, gw.metal(gw.BRASS, 93)),
        (0.30, 0.322, 1.9, 1.9, gw.metal(gw.DSTEEL, 94, rivets=2)),           # tsuba
        (0.322, 0.35, 1.1, 1.1, gw.metal(gw.DSTEEL, 95)),                     # habaki
        (0.35, 0.92, 1.0, 0.85, gw.blade(gw.STEEL, 96, hamon=True, nicks=(0.55, 0.7, 0.84), blood=0.06)),
        (0.92, 1.00, 0.85, 0.0, gw.blade(gw.STEEL, 96, hamon=True, blood=0.1)),
    ], curve=-0.35, curve_from=0.36)
    gw.outline(cv)
    return cv


def bo_staff(glow=False):
    cv = canvas()
    gw.along(cv, START, END, [
        (0.00, 0.06, 0.78, 0.78, gw.metal(gw.DSTEEL, 111, rivets=2)),
        (0.06, 0.42, 0.6, 0.6, gw.wood(gw.DARK_WOOD, 112)),
        (0.42, 0.58, 0.68, 0.68, gw.wrap_spiral(gw.CLOTH_RED, 113, 0.6)),     # the grip in the middle
        (0.58, 0.94, 0.6, 0.6, gw.wood(gw.DARK_WOOD, 114)),
        (0.94, 1.00, 0.78, 0.78, gw.metal(gw.DSTEEL, 115, rivets=2)),
    ])
    gw.outline(cv)
    return cv


# name: (draw, length in blocks, grip as a fraction from the butt, has a chakra flow)
HELD = {
    "yari": (yari, 2.2, 0.36, True),
    "nodachi": (nodachi, 1.55, 0.22, True),
    "bo_staff": (bo_staff, 1.75, 0.36, False),
}


# ---------------------------------------------------------------- display transforms

def rot(rx, ry, rz):
    """Minecraft's ItemTransform rotation (Quaternionf.rotationXYZ): R = Rx * Ry * Rz, degrees."""
    def rx_(a):
        c, s = math.cos(a), math.sin(a)
        return [[1, 0, 0], [0, c, -s], [0, s, c]]

    def ry_(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

    def rz_(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, -s, 0], [s, c, 0], [0, 0, 1]]
    return mul(mul(rx_(math.radians(rx)), ry_(math.radians(ry))), rz_(math.radians(rz)))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


# Vanilla item/handheld (right hand) and where a sword icon's handle sits (model blocks from the centre).
VANILLA = {
    "thirdperson_righthand": ((0, -90, 55), (0, 4.0, 0.5), 0.85),
    "firstperson_righthand": ((0, -90, 25), (1.13, 3.2, 1.13), 0.68),
}
# First person sits right under the camera: at full length the weapon fills the side of the screen,
# so it is drawn shorter there (a share of its length) and turned to lean up, left and away from
# the camera (pointing at about (-0.35, 0.85, -0.4) in view space) so the head shows. The rotation
# below gives that direction for a weapon along +y: (-sin z cos y, cos z, sin z sin y).
FP_LENGTH = float(os.environ.get("FP_LENGTH", "0.55"))
FP_ROT = tuple(float(v) for v in os.environ.get("FP_ROT", "0,-49,32").split(","))
HANDLE = (3.5 / 16 - 0.5, (16 - 12.5) / 16 - 0.5, 0.0)


def transforms(length, grip):
    """Display entries placing the held texture (16x16 model units, the weapon along +y, its grip
    at `grip` of the way from the butt) like vanilla's sword: same handle spot, same direction
    (the icon's diagonal is 45 degrees, so z turns 45 less), `length` blocks long (third person; first
    person shrinks it like vanilla does)."""
    out = {}
    for ctx, ((rx, ry, rz), t, scale) in VANILLA.items():
        fp = ctx.startswith("first")
        k = length * scale / 0.85 * (FP_LENGTH / 0.8 if fp else 1.0)
        sx, sy, sz = k * WIDTH_UNITS / LENGTH_UNITS, k, scale * 1.15
        r_vanilla = rot(rx, ry, rz)
        if fp:
            rx, ry, rz = FP_ROT[0], FP_ROT[1], FP_ROT[2] + 45  # (the 45 comes off again below)
        r_ours = rot(rx, ry, rz - 45)
        handle = apply(r_vanilla, [scale * h for h in HANDLE])
        target = [t[i] / 16 + handle[i] for i in range(3)]
        # where the grip is in the texture: the weapon runs from START (t=0) to END (t=1)
        v = START[1] + (END[1] - START[1]) * grip                 # icon units from the top
        g = [0.0, (1 - v / LENGTH_UNITS) - 0.5, 0.0]              # model blocks from the centre
        offset = apply(r_ours, [sx * g[0], sy * g[1], sz * g[2]])
        trans = [round((target[i] - offset[i]) * 16, 3) for i in range(3)]
        scl = [round(sx, 4), round(sy, 4), round(sz, 4)]
        out[ctx] = {"rotation": [rx, ry, rz - 45], "translation": trans, "scale": scl}
        left = ctx.replace("righthand", "lefthand")
        # Written like vanilla's left hand: rotation y and z negated, translation x negated (the
        # game mirrors it back).
        out[left] = {"rotation": [rx, -ry, -(rz - 45)], "translation": [-trans[0], trans[1], trans[2]], "scale": scl}
    return out


# ---------------------------------------------------------------- the models

def layers_model(textures, glow_index=None, display=None, parent="minecraft:item/handheld"):
    m = {"parent": parent, "loader": "forge:item_layers", "textures": {f"layer{i}": t for i, t in enumerate(textures)}}
    if glow_index is not None:
        m["forge_data"] = {"layers": {str(glow_index): {"block_light": 15, "sky_light": 15}}}
    if display:
        m["display"] = display
    return m


def model(name, length, grip, flow):
    icon = [f"noirm:item/{name}"] + ([f"noirm:item/{name}_glow"] if flow else [])
    held = [f"noirm:item/held/{name}"] + ([f"noirm:item/held/{name}_glow"] if flow else [])
    glow = 1 if flow else None
    icon_model = layers_model(icon, glow)
    m = {
        "loader": "forge:separate_transforms",
        "textures": {"particle": f"noirm:item/{name}"},
        "base": layers_model(held, glow, transforms(length, grip)),
        "perspectives": {p: icon_model for p in ("gui", "ground", "fixed", "head")},
    }
    return m


def main():
    os.makedirs(TEX, exist_ok=True)
    images = {}
    for name, (draw, length, grip, flow) in HELD.items():
        images[name] = draw().image()
        if flow:
            images[name + "_glow"] = draw(glow=True).image()
        plain = model(name, length, grip, False)
        if flow:
            plain["overrides"] = [{"predicate": {"noirm:flow": 1}, "model": f"noirm:item/{name}_flow"}]
            with open(os.path.join(MODELS, name + "_flow.json"), "w") as f:
                f.write(json.dumps(model(name, length, grip, True), indent=2) + "\n")
        with open(os.path.join(MODELS, name + ".json"), "w") as f:
            f.write(json.dumps(plain, indent=2) + "\n")
    for name, img in images.items():
        img.save(os.path.join(TEX, name + ".png"))
    if "--preview" in sys.argv:
        from PIL import Image
        out = sys.argv[sys.argv.index("--preview") + 1]
        w, h = gw.W * 2, gw.H * 2
        sheet = Image.new("RGBA", (len(HELD) * 2 * (w + 8), h + 8), (0x8B, 0x8B, 0x8B, 255))
        x = 4
        for name in HELD:
            base = images[name]
            sheet.alpha_composite(base.resize((w, h), Image.NEAREST), (x, 4))
            x += w + 8
            if name + "_glow" in images:
                lit = base.copy()
                lit.alpha_composite(images[name + "_glow"])
                sheet.alpha_composite(lit.resize((w, h), Image.NEAREST), (x, 4))
            x += w + 8
        sheet.save(out)
    print("wrote", len(images), f"held textures ({gw.W}x{gw.H}) and {len(HELD)} models")


if __name__ == "__main__":
    main()

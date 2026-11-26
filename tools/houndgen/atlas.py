"""The hound's fur atlas (docs/INUZUKA.md, "How it's drawn").

A hound's model is built in the game from its breed and level, so its boxes change size every
level. Instead of painting each model, every **box role** (chest, head, snout, ear, paw…) gets a
fixed set of six face rectangles on one atlas, and the game stretches each face over whatever size
that box has right now. Faces (model space, y down, the head toward -z):

    top    (y min)  u along +x, v along +z (row 0 = the front end)
    bottom (y max)  u along +x, v along +z
    front  (z min)  u along +x, v down
    back   (z max)  u along -x, v down
    west   (x min)  u from front to back, v down
    east   (x max)  u from front to back, v down

The game reads this layout from assets/noirm/hound/atlas.json.
"""
import json
import math

# Role: (width x, height y, depth z) in model pixels — about the biggest that box gets. Faces are
# painted at 4 texels per pixel (the HD art rule), capped so the atlas stays small.
ROLES = {
    "chest": (8.0, 7.5, 7.5), "hips": (7.0, 6.5, 7.5), "belly": (6.0, 1.5, 2.5), "mane": (9.0, 3.0, 6.0),
    "neck": (5.5, 4.5, 5.5), "ruff": (6.5, 3.5, 2.5), "scarf": (6.5, 1.5, 6.5), "scarf_knot": (2.0, 2.0, 1.0),
    "head": (7.0, 6.0, 5.5), "brow": (6.5, 1.0, 1.0), "snout": (3.75, 3.0, 4.75), "jaw": (3.5, 1.25, 4.75),
    "ear": (2.5, 3.5, 1.5), "ear_tip": (1.75, 2.0, 1.0), "fang": (0.5, 1.0, 0.5), "tuft": (2.5, 2.5, 1.0),
    "leg": (3.0, 5.0, 3.0), "leg_lower": (2.5, 4.5, 2.5), "paw": (3.0, 1.5, 3.25), "thigh": (3.25, 5.5, 4.0),
    "tail": (3.0, 4.0, 3.0), "tail_tip": (2.5, 4.0, 2.5),
}
SCALE = 4
CAP = 34
ATLAS_W = 256

FACES = ("top", "bottom", "west", "front", "east", "back")


def texels(v):
    return max(2, min(CAP, int(round(v * SCALE))))


def layout():
    """{role: {face: (x0, y0, x1, y1)}} and the atlas height. Box-net layout, shelf-packed."""
    out = {}
    x, y, shelf = 0, 0, 0
    # Big boxes first: a tighter pack.
    for role, (w, h, d) in sorted(ROLES.items(), key=lambda kv: -(texels(kv[1][0]) + texels(kv[1][2])) * (texels(kv[1][1]) + texels(kv[1][2]))):
        W, H, D = texels(w), texels(h), texels(d)
        net_w, net_h = 2 * D + 2 * W + 1, D + H + 1
        if x + net_w > ATLAS_W:
            x, y, shelf = 0, y + shelf, 0
        out[role] = {
            "top": (x + D, y, x + D + W, y + D),
            "bottom": (x + D + W, y, x + D + 2 * W, y + D),
            "west": (x, y + D, x + D, y + D + H),
            "front": (x + D, y + D, x + D + W, y + D + H),
            "east": (x + D + W, y + D, x + 2 * D + W, y + D + H),
            "back": (x + 2 * D + W, y + D, x + 2 * D + 2 * W, y + D + H),
        }
        x += net_w
        shelf = max(shelf, net_h)
    height = int(math.ceil((y + shelf) / 32.0) * 32)
    return out, height


def write(path):
    rects, height = layout()
    with open(path, "w") as f:
        json.dump({"size": [ATLAS_W, height], "roles": {r: {k: list(v) for k, v in faces.items()} for r, faces in rects.items()}},
                  f, separators=(",", ":"))
    return rects, height

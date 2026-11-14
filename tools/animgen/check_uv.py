#!/usr/bin/env python3
"""Sanity check for BendyNinjaModel's UVs against the standard 64x64 player skin.

Run from the repo root:  python3 tools/animgen/check_uv.py
It mirrors BendyNinjaModel.half() and Minecraft's ModelPart.Cube UV layout, then checks that:
  * every face samples inside its own limb's skin region (inner or outer layer);
  * the upper + lower halves' side faces tile the vanilla 12-row side strip exactly, no gaps or
    overlaps (so the split limb shows the same pixels as the vanilla limb);
  * the top face and the hand/sole face are the vanilla ones;
  * joint caps sample the limb's own side pixels.
It prints an ASCII map of which skin pixels the model uses. Exit code 1 on any problem.
"""
import sys

JOINT = 6
# Mirror of BendyNinjaModel: name -> (u, v, overlay u, overlay v, width, depth)
LIMBS = {
    "body":      (16, 16, 16, 32, 8, 4),
    "right_arm": (40, 16, 40, 32, 4, 4),
    "left_arm":  (32, 48, 48, 48, 4, 4),
    "right_leg": (0, 16, 0, 32, 4, 4),
    "left_leg":  (16, 48, 0, 48, 4, 4),
}
TOP_AND_SIDES = {"DOWN", "NORTH", "SOUTH", "EAST", "WEST"}
SIDES = {"NORTH", "SOUTH", "EAST", "WEST"}
BOTTOM = {"UP"}  # model Y points down, so Minecraft's "UP" face is the bottom end


def cube_faces(u, v, w, h, d, faces):
    """Minecraft 1.20.1 ModelPart.Cube: face -> (u0, v0, u1, v1) in pixels (min/max sorted)."""
    f27, f28, f29 = u, u + d, u + d + w
    f30, f31, f32 = u + d + w + w, u + d + w + d, u + d + w + d + w
    f33, f34, f35 = v, v + d, v + d + h
    rects = {
        "DOWN": (f28, f33, f29, f34),
        "UP": (f29, f34, f30, f33),
        "WEST": (f27, f34, f28, f35),
        "NORTH": (f28, f34, f29, f35),
        "EAST": (f29, f34, f31, f35),
        "SOUTH": (f31, f34, f32, f35),
    }
    out = {}
    for face in faces:
        a, b, c, e = rects[face]
        out[face] = (min(a, c), min(b, e), max(a, c), max(b, e))
    return out


def half(uv, w, d, upper):
    """Mirror of BendyNinjaModel.half(): list of (label, layer, faces dict)."""
    u, v, uo, vo = uv
    h = JOINT
    cubes = []
    if upper:
        cubes.append(("upper", "inner", cube_faces(u, v, w, h, d, TOP_AND_SIDES)))
        cubes.append(("upper-cap", "inner", cube_faces(u, v + h, w, 0, d, BOTTOM)))
        cubes.append(("upper", "outer", cube_faces(uo, vo, w, h, d, TOP_AND_SIDES)))
    else:
        cubes.append(("lower", "inner", cube_faces(u, v + h, w, h, d, TOP_AND_SIDES)))
        cubes.append(("lower-end", "inner", cube_faces(u, v, w, 0, d, BOTTOM)))
        cubes.append(("lower", "outer", cube_faces(uo, vo + h, w, h, d, SIDES)))
        cubes.append(("lower-end", "outer", cube_faces(uo, vo, w, 0, d, BOTTOM)))
    return cubes


def inside(rect, region):
    return region[0] <= rect[0] and region[1] <= rect[1] and rect[2] <= region[2] and rect[3] <= region[3]


def main():
    problems = []
    grid = [["." for _ in range(64)] for _ in range(64)]
    for name, (u, v, uo, vo, w, d) in LIMBS.items():
        regions = {"inner": (u, v, u + 2 * d + 2 * w, v + d + 12), "outer": (uo, vo, uo + 2 * d + 2 * w, vo + d + 12)}
        side_strip = {"inner": (v + d, v + d + 12), "outer": (vo + d, vo + d + 12)}
        vanilla = {"inner": cube_faces(u, v, w, 12, d, TOP_AND_SIDES | BOTTOM),
                   "outer": cube_faces(uo, vo, w, 12, d, TOP_AND_SIDES | BOTTOM)}
        cubes = half((u, v, uo, vo), w, d, True) + half((u, v, uo, vo), w, d, False)
        sides_seen = {}
        for label, layer, faces in cubes:
            for face, rect in faces.items():
                if not (0 <= rect[0] <= rect[2] <= 64 and 0 <= rect[1] <= rect[3] <= 64):
                    problems.append(f"{name} {label} {layer} {face} {rect} is off the 64x64 skin")
                if not inside(rect, regions[layer]):
                    problems.append(f"{name} {label} {layer} {face} {rect} leaves its region {regions[layer]}")
                if face in SIDES and label in ("upper", "lower"):
                    sides_seen.setdefault((layer, face), []).append(rect)
                if "cap" in label or (face == "DOWN" and label == "lower"):
                    lo, hi = side_strip[layer]
                    if not (lo <= rect[1] and rect[3] <= hi):
                        problems.append(f"{name} {label} {face} cap {rect} isn't limb-side pixels (rows {lo}-{hi})")
                mark = {"inner": "#", "outer": "o"}[layer]
                for y in range(int(rect[1]), int(rect[3])):
                    for x in range(int(rect[0]), int(rect[2])):
                        grid[y][x] = mark
            if label == "upper" and "DOWN" in faces and faces["DOWN"] != vanilla[layer]["DOWN"]:
                problems.append(f"{name} {layer} top face {faces['DOWN']} != vanilla {vanilla[layer]['DOWN']}")
            if label == "lower-end" and faces["UP"] != vanilla[layer]["UP"]:
                problems.append(f"{name} {layer} end face {faces['UP']} != vanilla {vanilla[layer]['UP']}")
        for (layer, face), rects in sides_seen.items():
            rects.sort(key=lambda r: r[1])
            want = vanilla[layer][face]
            ok = (len(rects) == 2 and rects[0][1] == want[1] and rects[0][3] == rects[1][1]
                  and rects[1][3] == want[3] and all(r[0] == want[0] and r[2] == want[2] for r in rects)
                  and rects[0][3] - rects[0][1] == JOINT)
            if not ok:
                problems.append(f"{name} {layer} {face} sides {rects} don't tile vanilla {want}")
        print(f"{name:10s} ok: inner region {regions['inner']}, outer region {regions['outer']}")
    print("\nSkin pixels used (# inner layer, o outer layer; head and hat are vanilla cubes, not shown):")
    for row in grid:
        print("".join(row))
    if problems:
        print("\nPROBLEMS:")
        for p in problems:
            print(" -", p)
        sys.exit(1)
    print("\nAll UV rectangles check out.")


if __name__ == "__main__":
    main()

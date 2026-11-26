#!/usr/bin/env python3
"""Export the Aburame beetles (client/aburame/BeetleMesh + AburameRenderers.Giant) to Blockbench models.

    python3 tools/models/export_beetle.py            # writes assets/noirm/models/bb/giant_insect.bbmodel and kikaichu.bbmodel

BeetleMesh draws a beetle as a box with each face textured from a fixed rectangle of a 16x16 texture. This script
runs the same maths (forward, right, up vectors; the same half-sizes; the same corner-to-texel mapping) and writes
each box as a Blockbench cube with per-face UVs, so the model opens in Blockbench looking exactly like the game,
and the loader (client/model/BbModels) draws it the same way.

Bones: `body`, `wings` (two flat wings, shown only while flapping), `legs` with `leg_r0..2` / `leg_l0..2`.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel as bb  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'noirm')
OUT = os.path.join(ASSETS, 'models', 'bb')


def norm(v):
    l = math.sqrt(sum(c * c for c in v))
    return tuple(c / l for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def add(*vs):
    return tuple(sum(c) for c in zip(*vs))


def mul(v, s):
    return tuple(c * s for c in v)


def beetle_frame(f):
    """BeetleMesh's right and up vectors for a forward vector."""
    f = norm(f)
    r = (-f[2], 0.0, f[0])
    if math.hypot(r[0], r[2]) < 1e-4:
        r = (1.0, 0.0, 0.0)
    r = norm(r)
    u = (r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
    return f, r, u


def beetle_quads(center, f, length, wings):
    """BeetleMesh.beetle(): [(normal, [(pos, (u, v)) x4])] in pixels; pos order as the Java code writes them."""
    f, r, u = beetle_frame(f)
    hz, hx, hy = length * 0.5, length * 0.375, length * 0.25
    a, b, c = mul(r, hx), mul(u, hy), mul(f, hz)
    quads = []

    def quad(ctr, us, vs, n, u0, v0, u1, v1):
        pts = [add(ctr, mul(us, -1), vs), add(ctr, us, vs), add(ctr, us, mul(vs, -1)), add(ctr, mul(us, -1), mul(vs, -1))]
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        quads.append((n, list(zip(pts, uvs))))

    quad(add(center, b), a, c, u, 0, 0, 3, 4)
    quad(add(center, mul(b, -1)), mul(a, -1), c, mul(u, -1), 4, 0, 7, 4)
    quad(add(center, a), b, c, r, 8, 0, 12, 2)
    quad(add(center, mul(a, -1)), mul(b, -1), c, mul(r, -1), 8, 0, 12, 2)
    quad(add(center, c), a, b, f, 12, 0, 15, 2)
    quad(add(center, mul(c, -1)), mul(a, -1), b, mul(f, -1), 12, 0, 15, 2)
    wing_quads = []
    if wings:
        o = add(center, mul(b, 1.4))
        for side in (1.6, -1.6):
            ctr = add(o, mul(a, side), mul(c, -0.2))
            us, vs = mul(a, 1.1), mul(c, 0.9)
            pts = [add(ctr, mul(us, -1), vs), add(ctr, us, vs), add(ctr, us, mul(vs, -1)), add(ctr, mul(us, -1), mul(vs, -1))]
            wing_quads.append((ctr, (hx * 1.1, hz * 0.9), list(zip(pts, [(0, 6), (4, 6), (4, 10), (0, 10)]))))
    return quads, wing_quads, (f, r, u), (hx, hy, hz)


def face_uv(bb_corners, mesh_quad):
    """Blockbench [u1, v1, u2, v2] and rotation that put each mesh texel on the same corner."""
    want = []
    for p in bb_corners:
        best = min(mesh_quad, key=lambda q: sum((p[i] - q[0][i]) ** 2 for i in range(3)))
        want.append(best[1])
    for k in range(4):
        base = [want[(m + k) % 4] for m in range(4)]
        (u1, v1), (u2, v1b), (u2b, v2), (u1b, v2b) = base
        if v1 == v1b and u2 == u2b and v2 == v2b and u1 == u1b:
            return [u1, v1, u2, v2], (k * 90) % 360
    raise SystemExit('no uv mapping found')


def export_box(doc, group, name, center, frame, half, quads):
    f, r, u = frame
    hx, hy, hz = half
    # rotation taking the canonical cube (right +x, up +y, front -z) onto the beetle's frame
    back = mul(f, -1)
    R = [[r[i], u[i], back[i]] for i in range(3)]
    rot = bb.euler_from_matrix(R)
    frm = [center[0] - hx, center[1] - hy, center[2] - hz]
    to = [center[0] + hx, center[1] + hy, center[2] + hz]
    faces = {}
    for side in bb.SIDES:
        cs = bb.corners(side, frm[0], frm[1], frm[2], to[0], to[1], to[2])
        cs = [bb.rotate(p, center, rot) for p in cs]
        n = bb.rotate(bb.NORMALS[side], (0, 0, 0), rot)
        mesh = max(quads, key=lambda q: sum(n[i] * q[0][i] for i in range(3)))
        uv, frot = face_uv(cs, mesh[1])
        faces[side] = (uv, frot) if frot else uv
    return bb.add_cube(doc, group, name, frm, to, origin=center, rotation=rot, faces=faces)


def export_wing(doc, group, name, ctr, half, quad):
    hx, hz = half
    frm = [ctr[0] - hx, ctr[1], ctr[2] - hz]
    to = [ctr[0] + hx, ctr[1], ctr[2] + hz]
    cs = bb.corners('up', frm[0], frm[1], frm[2], to[0], to[1], to[2])
    uv, frot = face_uv(cs, quad)
    return bb.add_cube(doc, group, name, frm, to, origin=ctr, faces={'up': (uv, frot) if frot else uv})


def giant_insect():
    """AburameRenderers.Giant at rest, facing -z (Blockbench's front): body 2.8 blocks long at y 0.75, six legs."""
    doc = bb.new_model('giant_insect')
    bb.add_texture(doc, os.path.join(ASSETS, 'textures', 'entity', 'aburame', 'giant_insect.png'),
                   relative_path='../../textures/entity/aburame/giant_insect.png')
    px = 16.0
    fwd = (0.0, 0.0, -1.0)
    body_c = (0.0, 0.75 * px, 0.0)
    quads, wings, frame, half = beetle_quads(body_c, fwd, 2.8 * px, True)
    body = bb.add_group(doc, 'body', origin=body_c)
    export_box(doc, body, 'body', body_c, frame, half, quads)
    wg = bb.add_group(doc, 'wings', origin=add(body_c, (0, 1.4 * half[1], 0)))
    for (ctr, wh, quad), nm in zip(wings, ('wing_r', 'wing_l')):
        export_wing(doc, wg, nm, ctr, wh, quad)
    legs = bb.add_group(doc, 'legs', origin=(0, 0.35 * px, 0))
    for i in range(6):
        side = 1 if i < 3 else -1
        along = (i % 3 - 1) * 0.55
        rx, rz = -fwd[2] * side, fwd[0] * side
        x, z = fwd[0] * along + rx * 0.75, fwd[2] * along + rz * 0.75
        c = (x * px, 0.35 * px, z * px)
        q, _, fr, hf = beetle_quads(c, (rx, -0.9, rz), 0.55 * px, False)
        nm = ('leg_r' if side > 0 else 'leg_l') + str(i % 3)
        lg = bb.add_group(doc, nm, origin=c, parent=legs)
        export_box(doc, lg, nm, c, fr, hf, q)
    return doc


def kikaichu():
    """One kikaichū beetle from the swarm, half a block long (the swarm draws them ~0.08 blocks), facing -z."""
    doc = bb.new_model('kikaichu')
    bb.add_texture(doc, os.path.join(ASSETS, 'textures', 'entity', 'aburame', 'kikaichu.png'),
                   relative_path='../../textures/entity/aburame/kikaichu.png')
    c = (0.0, 2.0, 0.0)
    quads, wings, frame, half = beetle_quads(c, (0, 0, -1), 8.0, True)
    body = bb.add_group(doc, 'body', origin=c)
    export_box(doc, body, 'body', c, frame, half, quads)
    wg = bb.add_group(doc, 'wings', origin=add(c, (0, 1.4 * half[1], 0)))
    for (ctr, wh, quad), nm in zip(wings, ('wing_r', 'wing_l')):
        export_wing(doc, wg, nm, ctr, wh, quad)
    return doc


def main():
    os.makedirs(OUT, exist_ok=True)
    for doc in (giant_insect(), kikaichu()):
        path = os.path.join(OUT, doc['name'] + '.bbmodel')
        bb.save(doc, path)
        print('wrote', os.path.relpath(path, ROOT), f"({len(doc['elements'])} cubes)")


if __name__ == '__main__':
    main()

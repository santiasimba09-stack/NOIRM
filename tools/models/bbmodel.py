"""Read, write and evaluate Blockbench models (.bbmodel) in Python.

The geometry here mirrors com.noirm.model.BbGeometry line for line (faces seen from outside, box UV layout,
rotations x then y then z about the pivot), so the offline preview draws what the game draws. Coordinates are
Blockbench's: pixels (16 = 1 block), y up, the model's front toward -z.

Used by preview.py (drawing) and the exporters (export_beetle.py). Small API:

    m = load('src/main/resources/assets/noirm/models/bb/giant_insect.bbmodel')
    for quad in model_quads(m): ...            # world-space quads with texel UVs, bones applied
    doc = new_model('my_thing', 16, 16)        # an empty model
    add_texture(doc, 'png path', relative_path='../../textures/entity/x.png')
    g = add_group(doc, 'body', origin=[0, 8, 0])
    add_cube(doc, g, 'body', [-4, 4, -6], [4, 12, 6], faces={...} or box_uv=(0, 0))
    save(doc, path)
"""
import base64
import json
import math
import os
import uuid as uuidlib

SIDES = ['north', 'east', 'south', 'west', 'up', 'down']
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


# ---------------------------------------------------------------- geometry (mirrors BbGeometry.java)

def box_uv(frm, to, u, v, mirror=False):
    w = math.floor(abs(to[0] - frm[0]) + 1e-4)
    h = math.floor(abs(to[1] - frm[1]) + 1e-4)
    d = math.floor(abs(to[2] - frm[2]) + 1e-4)
    m = {
        'east': [u, v + d, u + d, v + d + h],
        'north': [u + d, v + d, u + d + w, v + d + h],
        'west': [u + d + w, v + d, u + d + w + d, v + d + h],
        'south': [u + d + w + d, v + d, u + d + w + d + w, v + d + h],
        'up': [u + d + w, v + d, u + d, v],
        'down': [u + d + w + w, v, u + d + w, v + d],
    }
    if mirror:
        m['east'], m['west'] = m['west'], m['east']
        for r in m.values():
            r[0], r[2] = r[2], r[0]
    return m


def corners(side, x0, y0, z0, x1, y1, z1):
    """Top-left, top-right, bottom-right, bottom-left as seen from outside."""
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[side]


def rotate(p, origin, deg):
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if deg[0]:
        a = math.radians(deg[0]); c, s = math.cos(a), math.sin(a)
        y, z = y * c - z * s, y * s + z * c
    if deg[1]:
        a = math.radians(deg[1]); c, s = math.cos(a), math.sin(a)
        x, z = x * c + z * s, -x * s + z * c
    if deg[2]:
        a = math.radians(deg[2]); c, s = math.cos(a), math.sin(a)
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def cube_faces(cube, model_box_uv=False):
    """{side: (u1, v1, u2, v2, texture, rotation)} as the game resolves them."""
    frm, to = cube['from'], cube['to']
    faces = cube.get('faces', {})
    out = {}
    if cube.get('box_uv', model_box_uv):
        off = cube.get('uv_offset', [0, 0])
        uvs = box_uv(frm, to, off[0], off[1], cube.get('mirror_uv', False))
        for s in SIDES:
            tex = faces.get(s, {}).get('texture', 0) if s in faces else 0
            out[s] = (*uvs[s], tex if isinstance(tex, int) else -1, 0)
    else:
        for s in SIDES:
            if s in faces:
                f = faces[s]
                tex = f.get('texture')
                out[s] = (*f.get('uv', [0, 0, 0, 0]), tex if isinstance(tex, int) else -1, f.get('rotation', 0))
    return out


def cube_quads(cube, model_box_uv=False):
    """[(side, [4 corners ccw from outside], [4 uv], normal, texture)] in absolute pixels, cube rotation applied."""
    i = cube.get('inflate', 0)
    frm, to = cube['from'], cube['to']
    x0, y0, z0 = (min(frm[k], to[k]) - i for k in range(3))
    x1, y1, z1 = (max(frm[k], to[k]) + i for k in range(3))
    origin = cube.get('origin', [0, 0, 0])
    rot = cube.get('rotation', [0, 0, 0])
    out = []
    for side, (u1, v1, u2, v2, tex, frot) in cube_faces(cube, model_box_uv).items():
        if tex < 0:
            continue
        c = corners(side, x0, y0, z0, x1, y1, z1)
        base = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
        k = (frot // 90) % 4
        uv = [base[(n - k) % 4] for n in range(4)]
        c = [rotate(p, origin, rot) for p in c]
        nrm = rotate(NORMALS[side], (0, 0, 0), rot)
        out.append((side, [c[0], c[3], c[2], c[1]], [uv[0], uv[3], uv[2], uv[1]], nrm, tex))
    return out


def mat_rot(deg):
    """3x3 matrix (rows) for Euler degrees, x then y then z."""
    cols = [rotate(e, (0, 0, 0), deg) for e in ((1, 0, 0), (0, 1, 0), (0, 0, 1))]
    return [[cols[c][r] for c in range(3)] for r in range(3)]


def model_quads(doc, pose=None):
    """Every visible quad in model space with the bone tree applied. pose: {bone: {'rot': [deg], 'pos': [px], 'visible': bool}}."""
    pose = pose or {}
    by_uuid = {e['uuid']: e for e in doc.get('elements', []) if e.get('type', 'cube') == 'cube'}
    box = doc.get('meta', {}).get('box_uv', False)
    out = []

    def xf(stack, p):
        for (pivot, rot, off) in reversed(stack):
            p = rotate(p, pivot, rot)
            p = (p[0] + off[0], p[1] + off[1], p[2] + off[2])
        return p

    def xf_n(stack, n):
        for (_, rot, _) in reversed(stack):
            n = rotate(n, (0, 0, 0), rot)
        return n

    def walk(node, stack):
        if isinstance(node, str):
            c = by_uuid.get(node)
            if c and c.get('visibility', True):
                for side, pts, uv, nrm, tex in cube_quads(c, box):
                    out.append((side, [xf(stack, p) for p in pts], uv, xf_n(stack, nrm), tex))
            return
        name = node.get('name')
        p = pose.get(name, {})
        if not p.get('visible', node.get('visibility', True)):
            return
        rot = [a + b for a, b in zip(node.get('rotation', [0, 0, 0]), p.get('rot', [0, 0, 0]))]
        stack = stack + [(tuple(node.get('origin', [0, 0, 0])), rot, tuple(p.get('pos', [0, 0, 0])))]
        for ch in node.get('children', []):
            walk(ch, stack)

    for node in doc.get('outliner', [e['uuid'] for e in by_uuid.values()]):
        walk(node, [])
    return out


# ---------------------------------------------------------------- reading and writing

def load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def save(doc, path):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(doc, f, indent='\t', ensure_ascii=False)
        f.write('\n')


def uid():
    return str(uuidlib.uuid4())


def stable_uid(*parts):
    """A uuid that stays the same between exports (so diffs stay small)."""
    return str(uuidlib.uuid5(uuidlib.NAMESPACE_URL, 'noirm:' + ':'.join(map(str, parts))))


def new_model(name, tex_w=16, tex_h=16):
    return {
        'meta': {'format_version': '4.5', 'model_format': 'free', 'box_uv': False},
        'name': name,
        'model_identifier': name,
        'visible_box': [1, 1, 0],
        'variable_placeholders': '',
        'variable_placeholder_buttons': [],
        'timeline_setups': [],
        'unhandled_root_fields': {},
        'resolution': {'width': tex_w, 'height': tex_h},
        'elements': [],
        'outliner': [],
        'textures': [],
        'animations': [],
    }


def add_texture(doc, png_path, relative_path=None, name=None):
    """Embeds the PNG (so Blockbench shows it) and records where it lives in the mod (so the game finds it)."""
    with open(png_path, 'rb') as f:
        data = f.read()
    from PIL import Image
    w, h = Image.open(png_path).size
    tex = {
        'path': '', 'name': name or os.path.basename(png_path), 'folder': '', 'namespace': '', 'id': str(len(doc['textures'])),
        'width': w, 'height': h, 'uv_width': doc['resolution']['width'], 'uv_height': doc['resolution']['height'],
        'particle': False, 'render_mode': 'default', 'render_sides': 'auto', 'frame_time': 1, 'frame_order_type': 'loop',
        'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': True,
        'uuid': stable_uid(doc['name'], 'texture', len(doc['textures'])),
        'relative_path': relative_path or '',
        'source': 'data:image/png;base64,' + base64.b64encode(data).decode(),
    }
    doc['textures'].append(tex)
    return len(doc['textures']) - 1


def add_group(doc, name, origin=(0, 0, 0), rotation=(0, 0, 0), parent=None):
    g = {'name': name, 'origin': list(origin), 'rotation': list(rotation), 'color': 0,
         'uuid': stable_uid(doc['name'], 'group', name), 'export': True, 'mirror_uv': False, 'isOpen': True,
         'locked': False, 'visibility': True, 'autouv': 0, 'children': []}
    (parent['children'] if parent else doc['outliner']).append(g)
    return g


def add_cube(doc, group, name, frm, to, origin=None, rotation=(0, 0, 0), faces=None, box_uv=None, inflate=0, mirror=False):
    """faces: {side: [u1, v1, u2, v2] or (uv, rotation)}; missing sides are left empty. box_uv: (u, v) offset instead."""
    origin = list(origin) if origin is not None else [(a + b) / 2 for a, b in zip(frm, to)]
    c = {'name': name, 'box_uv': box_uv is not None, 'rescale': False, 'locked': False, 'render_order': 'default',
         'allow_mirror_modeling': True, 'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to],
         'autouv': 0, 'color': 0, 'origin': [round(v, 4) for v in origin], 'rotation': [round(v, 4) for v in rotation],
         'inflate': inflate, 'type': 'cube', 'uuid': stable_uid(doc['name'], 'cube', name)}
    if box_uv is not None:
        c['uv_offset'] = list(box_uv)
        c['mirror_uv'] = mirror
        c['faces'] = {s: {'uv': [0, 0, 0, 0], 'texture': 0} for s in SIDES}
        for s, r in globals()['box_uv'](frm, to, box_uv[0], box_uv[1], mirror).items():
            c['faces'][s]['uv'] = r
    else:
        c['faces'] = {}
        for s in SIDES:
            f = (faces or {}).get(s)
            if f is None:
                c['faces'][s] = {'uv': [0, 0, 0, 0], 'texture': None}
            else:
                uv, rot = (f if isinstance(f, tuple) else (f, 0))
                c['faces'][s] = {'uv': [round(v, 4) for v in uv], 'texture': 0}
                if rot:
                    c['faces'][s]['rotation'] = rot
    doc['elements'].append(c)
    group['children'].append(c['uuid'])
    return c


def euler_from_matrix(R):
    """Euler degrees (x, y, z; applied x then y then z) for a rotation matrix R (rows), the inverse of mat_rot."""
    # R = Rz * Ry * Rx  ->  R[2][0] = -sin(y)
    sy = -R[2][0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(sy) < 0.99999:
        x = math.atan2(R[2][1], R[2][2])
        z = math.atan2(R[1][0], R[0][0])
    else:
        x = math.atan2(-R[1][2], R[1][1])
        z = 0.0
    return [math.degrees(x), math.degrees(y), math.degrees(z)]

#!/usr/bin/env python3
"""Draw a Blockbench model (.bbmodel) with its texture to a small PNG from several angles, without Minecraft.

    python3 tools/models/preview.py src/main/resources/assets/noirm/models/bb/giant_insect.bbmodel
    python3 tools/models/preview.py MODEL.bbmodel --out shot.png --size 160 --views front,side,back,threequarter,top
    python3 tools/models/preview.py MODEL.bbmodel --hide wings          # hide a bone
    python3 tools/models/preview.py MODEL.bbmodel --anim walk --time 0.25
    python3 tools/models/preview.py MODEL.bbmodel --texture path/to.png # draw with another texture

Default output: build/preview/<model>.png (four 128 px views in a row, about 10-20 KB). Orthographic camera, nearest
texel sampling, alpha cut-out, a simple light. Faces follow com.noirm.model.BbGeometry (through bbmodel.py), so the
shapes and UVs match the game. It's a quick check for shape, proportions and texture placement, not a beauty shot.
"""
import argparse
import base64
import io
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel as bb  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

# yaw: turn of the camera around the model (0 = looking at its front, which faces -z), pitch: looking down
VIEWS = {
    'front': (0, 10), 'side': (90, 10), 'back': (180, 10), 'threequarter': (35, 25),
    'left': (-90, 10), 'top': (0, 89), 'bottom': (0, -89),
}


def load_texture(doc, model_path, override=None):
    if override:
        return np.asarray(Image.open(override).convert('RGBA'), dtype=np.float32) / 255.0
    for t in doc.get('textures', []):
        src = t.get('source', '')
        if src.startswith('data:image'):
            data = base64.b64decode(src.split(',', 1)[1])
            return np.asarray(Image.open(io.BytesIO(data)).convert('RGBA'), dtype=np.float32) / 255.0
        rel = t.get('relative_path')
        if rel:
            p = os.path.normpath(os.path.join(os.path.dirname(model_path), rel))
            if os.path.isfile(p):
                return np.asarray(Image.open(p).convert('RGBA'), dtype=np.float32) / 255.0
    tex = np.ones((16, 16, 4), dtype=np.float32)
    tex[::2, ::2, :3] = 0.6
    return tex


def sample_channel(keys, t):
    if not keys:
        return None
    keys = sorted(keys, key=lambda k: k['time'])

    def val(k):
        dp = k['data_points'][0]
        out = []
        for a in 'xyz':
            try:
                out.append(float(dp.get(a, 0)))
            except (TypeError, ValueError):
                out.append(0.0)
        return out
    if t <= keys[0]['time'] or len(keys) == 1:
        return val(keys[0])
    if t >= keys[-1]['time']:
        return val(keys[-1])
    i = max(j for j in range(len(keys) - 1) if keys[j]['time'] <= t)
    a, b = keys[i], keys[i + 1]
    f = (t - a['time']) / max(1e-6, b['time'] - a['time'])
    if a.get('interpolation') == 'step':
        return val(a)
    va, vb = val(a), val(b)
    return [x + (y - x) * f for x, y in zip(va, vb)]


def animation_pose(doc, name, t):
    """Bone pose from a Blockbench animation, the same way the game applies it (BakedBbModel.animate)."""
    anim = next((a for a in doc.get('animations', []) if a.get('name') == name), None)
    if anim is None:
        raise SystemExit(f'no animation {name!r}; the model has: {[a.get("name") for a in doc.get("animations", [])]}')
    length = anim.get('length', 0) or 0
    if length > 0:
        t = t % length if anim.get('loop') == 'loop' else min(t, length)
    names = {}

    def walk(n):
        if isinstance(n, dict):
            names[n['uuid']] = n['name']
            for c in n.get('children', []):
                walk(c)
    for n in doc.get('outliner', []):
        walk(n)
    pose = {}
    for uid, an in anim.get('animators', {}).items():
        bone = names.get(uid, an.get('name'))
        kfs = an.get('keyframes', [])
        rot = sample_channel([k for k in kfs if k.get('channel') == 'rotation'], t)
        pos = sample_channel([k for k in kfs if k.get('channel') == 'position'], t)
        p = pose.setdefault(bone, {})
        if rot:
            p['rot'] = [-rot[0], -rot[1], rot[2]]
        if pos:
            p['pos'] = [-pos[0], pos[1], pos[2]]
    return pose


def view_matrix(yaw, pitch):
    """Rows of a matrix taking model space to camera space (x right, y up, z toward the camera)."""
    # Looking at the front (which faces -z) means the camera sits at -z looking toward +z.
    y, p = math.radians(yaw), math.radians(pitch)
    # turn the model by -yaw around y, then tilt by pitch around x; then mirror z so -z faces the camera
    ry = np.array([[math.cos(y), 0, -math.sin(y)], [0, 1, 0], [math.sin(y), 0, math.cos(y)]])
    rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    flip = np.diag([-1.0, 1.0, -1.0])  # camera on the -z side: model +x appears on the left, like Blockbench's front view
    return rx @ flip @ ry


def render(quads, tex, yaw, pitch, size, scale, center):
    M = view_matrix(yaw, pitch)
    img = np.zeros((size, size, 3), dtype=np.float32)
    img[:] = (0.16, 0.17, 0.19)
    zbuf = np.full((size, size), -np.inf, dtype=np.float32)
    th, tw = tex.shape[:2]
    light = np.array([0.35, 0.8, 0.5])
    light /= np.linalg.norm(light)
    for side, pts, uv, nrm, t in quads:
        P = (np.array(pts, dtype=np.float64) - center) @ M.T
        n = M @ np.array(nrm)
        shade = 0.55 + 0.45 * max(0.0, float(n @ light))
        sx = size / 2 + P[:, 0] * scale
        sy = size / 2 - P[:, 1] * scale
        sz = P[:, 2]
        UV = np.array(uv, dtype=np.float64)
        u_scale, v_scale = tw / dw, th / dh
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], sz[list(tri)]
            uvs = UV[list(tri)]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(size - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(size - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            u = (w0 * uvs[0, 0] + w1 * uvs[1, 0] + w2 * uvs[2, 0]) * u_scale
            v = (w0 * uvs[0, 1] + w1 * uvs[1, 1] + w2 * uvs[2, 1]) * v_scale
            ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
            vi = np.clip(np.floor(v).astype(int), 0, th - 1)
            texel = tex[vi, ui]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (texel[..., 3] > 0.1) & (z > sub)
            sub[ok] = z[ok]
            img[y0:y1 + 1, x0:x1 + 1][ok] = texel[ok][:, :3] * shade
    return (np.clip(img, 0, 1) * 255).astype(np.uint8)


dw = dh = 16  # the model's UV resolution, set in main()


def main():
    global dw, dh
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('model')
    ap.add_argument('--out')
    ap.add_argument('--size', type=int, default=128, help='pixels per view (default 128)')
    ap.add_argument('--views', default='front,side,back,threequarter')
    ap.add_argument('--hide', default='', help='comma-separated bones to hide')
    ap.add_argument('--anim')
    ap.add_argument('--time', type=float, default=0.0)
    ap.add_argument('--texture')
    args = ap.parse_args()

    doc = bb.load(args.model)
    dw, dh = doc.get('resolution', {}).get('width', 16), doc.get('resolution', {}).get('height', 16)
    tex = load_texture(doc, args.model, args.texture)
    pose = animation_pose(doc, args.anim, args.time) if args.anim else {}
    for b in filter(None, args.hide.split(',')):
        pose.setdefault(b, {})['visible'] = False
    quads = bb.model_quads(doc, pose)
    if not quads:
        raise SystemExit('the model has no visible faces')
    pts = np.array([p for q in quads for p in q[1]])
    center = (pts.min(0) + pts.max(0)) / 2
    radius = np.linalg.norm(pts - center, axis=1).max()
    scale = (args.size * 0.46) / max(radius, 1e-3)
    names = [v.strip() for v in args.views.split(',') if v.strip()]
    tiles = []
    for name in names:
        yaw, pitch = VIEWS[name]
        tile = Image.fromarray(render(quads, tex, yaw, pitch, args.size, scale, center))
        ImageDraw.Draw(tile).text((3, 2), name, fill=(200, 200, 200))
        tiles.append(tile)
    sheet = Image.new('RGB', (args.size * len(tiles), args.size))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * args.size, 0))
    out = args.out or os.path.join(ROOT, 'build', 'preview', os.path.splitext(os.path.basename(args.model))[0] + '.png')
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out, optimize=True)
    bones = sorted({n for n in _bones(doc)})
    print(f'{out}  ({sheet.size[0]}x{sheet.size[1]}, {os.path.getsize(out) // 1024 + 1} KB, {len(quads)} faces, bones: {", ".join(bones)})')


def _bones(doc):
    def walk(n):
        if isinstance(n, dict):
            yield n['name']
            for c in n.get('children', []):
                yield from walk(c)
    for n in doc.get('outliner', []):
        yield from walk(n)


if __name__ == '__main__':
    main()

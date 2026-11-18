"""A small textured box renderer for the hound previews (orthographic, z-buffered, numpy).

It reads the **exported** Minecraft-space geometry (the same numbers the game reads), applies the
same transforms as the game's model renderer, and ray-casts every box, so what it draws is what
the game should draw (before animation). Used for quick contact sheets while tuning proportions;
the real check is the client (tools/client-smoke).
"""
import math

import numpy as np

from rig import rot_matrix, TEX_SCALE


def part_transforms(geom):
    """World transform (A, o) of every part: world = o + A @ local_px (Minecraft model px)."""
    out = {}
    flip = np.diag([-1.0, -1.0, 1.0])
    for p in geom["parts"]:
        r = rot_matrix(*p["rot"])  # (degrees, Minecraft space: same ZYX order)
        t = np.array(p["pivot"], dtype=float)
        if p["parent"]:
            pa, po = out[p["parent"]]
            out[p["name"]] = (pa @ r, po + pa @ t)
        else:
            out[p["name"]] = (r, t)
    world = {}
    for name, (a, o) in out.items():
        # The renderer: scale(-1,-1,1), translate(0,-1.501,0), model px / 16.
        world[name] = (flip @ a / 16.0, flip @ (o / 16.0) + np.array([0, 1.501, 0]))
    return world


def boxes(geom, scale=1.0):
    world = part_transforms(geom)
    out = []
    for p in geom["parts"]:
        a, o = world[p["name"]]
        for c in p["cubes"]:
            inf = c.get("inflate", 0.0)
            w, h, d = c["size"]
            fx, fy, fz = c["from"]
            # Box-local coordinates run 0..w etc. from the low corner (inflate grows it evenly).
            corner = np.array([fx - inf, fy - inf, fz - inf])
            size = np.array([w + 2 * inf, h + 2 * inf, d + 2 * inf])
            A = a * scale
            O = (o + a @ corner) * scale
            out.append((A, O, size, c))
    return out


def render(geom, texture, view_yaw, view_pitch=15.0, size=(320, 240), extent=None, scale=1.0, centre=None, bg=(0.16, 0.16, 0.17)):
    """An orthographic view from the yaw/pitch (degrees; yaw 0 looks at the hound's left side)."""
    bxs = boxes(geom, scale)
    W, H = size
    yaw, pitch = math.radians(view_yaw), math.radians(view_pitch)
    # Camera basis (world y up).
    fwd = np.array([math.cos(pitch) * math.sin(yaw), -math.sin(pitch), math.cos(pitch) * math.cos(yaw)])
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    if extent is None or centre is None:
        pts = []
        for A, O, s, _ in bxs:
            for cx in (0, 1):
                for cy in (0, 1):
                    for cz in (0, 1):
                        pts.append(O + A @ (s * np.array([cx, cy, cz])))
        pts = np.array(pts)
        lo, hi = pts.min(0), pts.max(0)
        if centre is None:
            centre = (lo + hi) / 2
        if extent is None:
            extent = float(np.max(hi - lo)) * 0.62
    aspect = W / H
    xs = (np.arange(W) + 0.5) / W * 2 - 1
    ys = 1 - (np.arange(H) + 0.5) / H * 2
    gx, gy = np.meshgrid(xs * extent * aspect, ys * extent)
    origins = centre[None, None, :] + gx[..., None] * right + gy[..., None] * up - fwd * 50
    origins = origins.reshape(-1, 3)
    dirs = np.broadcast_to(fwd, origins.shape)
    depth = np.full(origins.shape[0], np.inf)
    colour = np.tile(np.array(bg), (origins.shape[0], 1))
    tex = texture.astype(float) / 255.0 if texture.dtype != float else texture
    th, tw = tex.shape[:2]
    light = np.array([0.4, 0.85, 0.35])
    light /= np.linalg.norm(light)
    for A, O, s, c in bxs:
        inv = np.linalg.inv(A)
        lo_ = (origins - O) @ inv.T
        ld = dirs @ inv.T
        with np.errstate(divide="ignore", invalid="ignore"):
            t1 = (0 - lo_) / ld
            t2 = (s - lo_) / ld
        tmin = np.minimum(t1, t2)
        tmax = np.maximum(t1, t2)
        tnear = np.nanmax(tmin, axis=1)
        tfar = np.nanmin(tmax, axis=1)
        hit = (tnear <= tfar) & (tfar > 0) & (tnear < depth)
        if not hit.any():
            continue
        idx = np.nonzero(hit)[0]
        tn = tnear[idx]
        p = lo_[idx] + ld[idx] * tn[:, None]
        axis = np.argmax(tmin[idx], axis=1)
        w, h, d = c["size"]
        u0, v0 = c["uv"]
        inf = c.get("inflate", 0.0)
        lx = np.clip(p[:, 0] - inf, 0, w - 1e-4) if w > 0 else p[:, 0] * 0
        ly = np.clip(p[:, 1] - inf, 0, h - 1e-4)
        lz = np.clip(p[:, 2] - inf, 0, d - 1e-4)
        neg = ld[idx, :][np.arange(len(idx)), axis] > 0  # entered through the low face of that axis
        tu = np.zeros(len(idx))
        tv = np.zeros(len(idx))
        normal = np.zeros((len(idx), 3))
        # Box-UV faces (see paint.face_rects).
        m = (axis == 1) & neg   # local y = 0: Minecraft's "down" polygon = the visual top
        tu[m] = u0 + d + lx[m]; tv[m] = v0 + (d - lz[m])
        m = (axis == 1) & ~neg
        tu[m] = u0 + d + w + lx[m]; tv[m] = v0 + d - lz[m]
        m = (axis == 0) & neg
        tu[m] = u0 + (d - lz[m]); tv[m] = v0 + d + ly[m]
        m = (axis == 0) & ~neg
        tu[m] = u0 + d + w + lz[m]; tv[m] = v0 + d + ly[m]
        m = (axis == 2) & neg
        tu[m] = u0 + d + lx[m]; tv[m] = v0 + d + ly[m]
        m = (axis == 2) & ~neg
        tu[m] = u0 + 2 * d + w + (w - lx[m]); tv[m] = v0 + d + ly[m]
        for ax in range(3):
            m = axis == ax
            nrm = A[:, ax] / np.linalg.norm(A[:, ax])
            normal[m] = np.where(neg[m][:, None], -nrm, nrm)
        px = np.clip((tu * TEX_SCALE).astype(int), 0, tw - 1)
        py = np.clip((tv * TEX_SCALE).astype(int), 0, th - 1)
        texel = tex[py, px]
        keep = texel[:, 3] > 0.5
        lit = 0.55 + 0.45 * np.clip(normal @ light, 0, 1)
        sel = idx[keep]
        depth[sel] = tn[keep]
        colour[sel] = texel[keep, :3] * lit[keep, None]
    return (np.clip(colour.reshape(H, W, 3), 0, 1) * 255).astype(np.uint8)

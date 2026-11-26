package com.noirm.model;

import com.noirm.model.BbModel.Animation;
import com.noirm.model.BbModel.Cube;
import com.noirm.model.BbModel.Face;
import com.noirm.model.BbModel.Keyframe;
import com.noirm.model.BbModel.Quad;
import com.noirm.model.BbModel.Side;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The maths behind {@link BbModel}, written to match what Blockbench shows (and mirrored line for line by
 * {@code tools/models/bbmodel.py}, which the offline preview uses):
 * <ul>
 *   <li><b>Faces</b> are seen from outside: on a side face u runs to the viewer's right and v down; on the top face
 *   u runs east (+x) and v south (+z); on the bottom face u runs east and v north. A face's {@code rotation} turns
 *   its texture clockwise in 90° steps; u1 &gt; u2 (or v1 &gt; v2) mirrors it.</li>
 *   <li><b>Box UV</b> lays the six faces out the vanilla way from the UV offset (east, north, west, south in a row
 *   under up and down), using the cube's size rounded down.</li>
 *   <li><b>Rotations</b> are degrees, applied about the pivot in the order x, then y, then z.</li>
 * </ul>
 */
public final class BbGeometry {
    private BbGeometry() {}

    /** Box UV: each face's texel rectangle for a cube of this size at UV offset (u, v). */
    public static Map<Side, float[]> boxUv(float[] from, float[] to, float u, float v, boolean mirror) {
        float w = (float) Math.floor(Math.abs(to[0] - from[0]) + 1e-4);
        float h = (float) Math.floor(Math.abs(to[1] - from[1]) + 1e-4);
        float d = (float) Math.floor(Math.abs(to[2] - from[2]) + 1e-4);
        Map<Side, float[]> m = new EnumMap<>(Side.class);
        m.put(Side.EAST, new float[]{u, v + d, u + d, v + d + h});
        m.put(Side.NORTH, new float[]{u + d, v + d, u + d + w, v + d + h});
        m.put(Side.WEST, new float[]{u + d + w, v + d, u + d + w + d, v + d + h});
        m.put(Side.SOUTH, new float[]{u + d + w + d, v + d, u + d + w + d + w, v + d + h});
        m.put(Side.UP, new float[]{u + d + w, v + d, u + d, v});
        m.put(Side.DOWN, new float[]{u + d + w + w, v, u + d + w, v + d});
        if (mirror) {
            float[] east = m.get(Side.EAST);
            m.put(Side.EAST, m.get(Side.WEST));
            m.put(Side.WEST, east);
            for (float[] r : m.values()) {
                float t = r[0];
                r[0] = r[2];
                r[2] = t;
            }
        }
        return m;
    }

    /** The cube's drawable faces (see {@link Cube#quads()}). Faces with no texture are left out. */
    public static List<Quad> quads(Cube c) {
        float i = c.inflate;
        float x0 = Math.min(c.from[0], c.to[0]) - i, y0 = Math.min(c.from[1], c.to[1]) - i, z0 = Math.min(c.from[2], c.to[2]) - i;
        float x1 = Math.max(c.from[0], c.to[0]) + i, y1 = Math.max(c.from[1], c.to[1]) + i, z1 = Math.max(c.from[2], c.to[2]) + i;
        List<Quad> out = new ArrayList<>(6);
        for (Map.Entry<Side, Face> e : c.faces.entrySet()) {
            Face f = e.getValue();
            if (f.texture() < 0) {
                continue;
            }
            float[][] corners = corners(e.getKey(), x0, y0, z0, x1, y1, z1); // TL, TR, BR, BL from outside
            float[][] base = {{f.u1(), f.v1()}, {f.u2(), f.v1()}, {f.u2(), f.v2()}, {f.u1(), f.v2()}};
            int k = Math.floorMod(f.rotation() / 90, 4);
            float[][] uv = new float[4][];
            for (int n = 0; n < 4; n++) {
                uv[n] = base[Math.floorMod(n - k, 4)];
            }
            float[] normal = normal(e.getKey());
            for (int n = 0; n < 4; n++) {
                corners[n] = rotate(corners[n], c.origin, c.rotation);
            }
            normal = rotate(normal, new float[3], c.rotation);
            // counter-clockwise from outside: TL, BL, BR, TR
            out.add(new Quad(e.getKey(), new float[][]{corners[0], corners[3], corners[2], corners[1]},
                    new float[][]{uv[0], uv[3], uv[2], uv[1]}, normal, f.texture()));
        }
        return out;
    }

    /** A face's corners top-left, top-right, bottom-right, bottom-left, as seen from outside. */
    static float[][] corners(Side s, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (s) {
            case NORTH -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case DOWN -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        };
    }

    static float[] normal(Side s) {
        return switch (s) {
            case NORTH -> new float[]{0, 0, -1};
            case SOUTH -> new float[]{0, 0, 1};
            case WEST -> new float[]{-1, 0, 0};
            case EAST -> new float[]{1, 0, 0};
            case UP -> new float[]{0, 1, 0};
            case DOWN -> new float[]{0, -1, 0};
        };
    }

    /** {@code p} turned about {@code origin} by Euler degrees (x first, then y, then z). */
    public static float[] rotate(float[] p, float[] origin, float[] deg) {
        double x = p[0] - origin[0], y = p[1] - origin[1], z = p[2] - origin[2];
        if (deg[0] != 0) {
            double a = Math.toRadians(deg[0]), c = Math.cos(a), s = Math.sin(a);
            double ny = y * c - z * s, nz = y * s + z * c;
            y = ny;
            z = nz;
        }
        if (deg[1] != 0) {
            double a = Math.toRadians(deg[1]), c = Math.cos(a), s = Math.sin(a);
            double nx = x * c + z * s, nz = -x * s + z * c;
            x = nx;
            z = nz;
        }
        if (deg[2] != 0) {
            double a = Math.toRadians(deg[2]), c = Math.cos(a), s = Math.sin(a);
            double nx = x * c - y * s, ny = x * s + y * c;
            x = nx;
            y = ny;
        }
        return new float[]{(float) x + origin[0], (float) y + origin[1], (float) z + origin[2]};
    }

    // ================================================================ animation

    /** Where {@code seconds} falls in the animation (wrapped for loops, held at the end otherwise). */
    public static float animationTime(Animation a, float seconds) {
        if (a.length() <= 0f) {
            return 0f;
        }
        return a.loop() == BbModel.Loop.LOOP ? seconds % a.length() : Math.min(seconds, a.length());
    }

    /** A channel's value at {@code t} seconds, or null when the channel has no keyframes. */
    public static float[] sample(List<Keyframe> keys, float t) {
        if (keys.isEmpty()) {
            return null;
        }
        if (keys.size() == 1 || t <= keys.get(0).time()) {
            Keyframe k = keys.get(0);
            return new float[]{k.x(), k.y(), k.z()};
        }
        int n = keys.size();
        if (t >= keys.get(n - 1).time()) {
            Keyframe k = keys.get(n - 1);
            return new float[]{k.x(), k.y(), k.z()};
        }
        int i = 0;
        while (i < n - 2 && keys.get(i + 1).time() <= t) {
            i++;
        }
        Keyframe a = keys.get(i), b = keys.get(i + 1);
        float span = b.time() - a.time();
        float f = span <= 0 ? 1f : (t - a.time()) / span;
        if ("step".equals(a.interpolation())) {
            return new float[]{a.x(), a.y(), a.z()};
        }
        if ("catmullrom".equals(a.interpolation()) || "catmullrom".equals(b.interpolation())) {
            Keyframe p0 = keys.get(Math.max(0, i - 1)), p3 = keys.get(Math.min(n - 1, i + 2));
            return new float[]{cr(p0.x(), a.x(), b.x(), p3.x(), f), cr(p0.y(), a.y(), b.y(), p3.y(), f), cr(p0.z(), a.z(), b.z(), p3.z(), f)};
        }
        return new float[]{a.x() + (b.x() - a.x()) * f, a.y() + (b.y() - a.y()) * f, a.z() + (b.z() - a.z()) * f};
    }

    private static float cr(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}

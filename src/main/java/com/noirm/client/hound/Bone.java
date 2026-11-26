package com.noirm.client.hound;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * One jointed part of a hound's body, like a vanilla {@code ModelPart} but with boxes that take
 * their texture from the fur atlas by role: every face of a box stretches over its role's fixed
 * rectangle, whatever size the box is at this level (assets/noirm/hound/atlas.json). Pose fields
 * ({@link #x}, {@link #xRot}…) are set by the animation each frame and put back by {@link #resetPose}.
 */
public final class Bone {
    public final String name;
    public final List<Bone> children = new ArrayList<>();
    final List<Box> boxes = new ArrayList<>();
    private final float px, py, pz, prx, pry, prz;
    public float x, y, z, xRot, yRot, zRot;
    public boolean visible = true;

    Bone(String name, float px, float py, float pz, float rx, float ry, float rz) {
        this.name = name;
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.prx = rx;
        this.pry = ry;
        this.prz = rz;
        resetPose();
    }

    public void resetPose() {
        x = px;
        y = py;
        z = pz;
        xRot = prx;
        yRot = pry;
        zRot = prz;
        visible = true;
    }

    /** Rest position (model pixels, relative to the parent). */
    public float restX() {
        return px;
    }

    public float restY() {
        return py;
    }

    public float restZ() {
        return pz;
    }

    public void resetAll() {
        resetPose();
        for (Bone c : children) {
            c.resetAll();
        }
    }

    // ================================================================ boxes

    /** An axis-aligned box in this bone's space (model pixels, y down) and its six faces' atlas rectangles (0..1). */
    record Box(float x0, float y0, float z0, float x1, float y1, float z1, float[][] uv) {}

    /** Face order in {@link Box#uv}. */
    static final String[] FACES = {"top", "bottom", "front", "back", "west", "east"};

    void box(float x0, float y0, float z0, float w, float h, float d, float[][] uv) {
        boxes.add(new Box(x0 / 16f, y0 / 16f, z0 / 16f, (x0 + w) / 16f, (y0 + h) / 16f, (z0 + d) / 16f, uv));
    }

    // ================================================================ drawing

    private static final Vector4f POS = new Vector4f();
    private static final Vector3f NRM = new Vector3f();
    private static final Quaternionf ROT = new Quaternionf();

    public void render(PoseStack pose, VertexConsumer out, int light, int overlay, float r, float g, float b, float a) {
        if (!visible) {
            return;
        }
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, z / 16f);
        if (xRot != 0f || yRot != 0f || zRot != 0f) {
            pose.mulPose(ROT.rotationZYX(zRot, yRot, xRot));
        }
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        for (Box box : boxes) {
            emit(box, m, n, out, light, overlay, r, g, b, a);
        }
        for (Bone child : children) {
            child.render(pose, out, light, overlay, r, g, b, a);
        }
        pose.popPose();
    }

    private static void emit(Box bx, Matrix4f m, Matrix3f n, VertexConsumer out, int light, int overlay, float r, float g, float b, float a) {
        float x0 = bx.x0, y0 = bx.y0, z0 = bx.z0, x1 = bx.x1, y1 = bx.y1, z1 = bx.z1;
        // top (y0): u +x, v +z
        face(m, n, out, bx.uv[0], 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, light, overlay, r, g, b, a);
        // bottom (y1)
        face(m, n, out, bx.uv[1], 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, light, overlay, r, g, b, a, true);
        // front (z0): u +x, v down
        face(m, n, out, bx.uv[2], 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, light, overlay, r, g, b, a);
        // back (z1): u -x
        face(m, n, out, bx.uv[3], 0, 0, 1, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, light, overlay, r, g, b, a);
        // west (x0): u front→back
        face(m, n, out, bx.uv[4], -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, light, overlay, r, g, b, a);
        // east (x1): u front→back
        face(m, n, out, bx.uv[5], 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, light, overlay, r, g, b, a);
    }

    private static void face(Matrix4f m, Matrix3f n, VertexConsumer out, float[] uv, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             int light, int overlay, float r, float g, float b, float a) {
        face(m, n, out, uv, nx, ny, nz, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, light, overlay, r, g, b, a, false);
    }

    /**
     * Four corners in order top-left, top-right, bottom-right, bottom-left of the face's texture
     * rectangle ({@code flipV}: the corners were listed from the far edge, so v runs the other way).
     */
    private static void face(Matrix4f m, Matrix3f n, VertexConsumer out, float[] uv, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             int light, int overlay, float r, float g, float b, float a, boolean flipV) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float vt = flipV ? v1 : v0;
        float vb = flipV ? v0 : v1;
        n.transform(NRM.set(nx, ny, nz));
        float nnx = NRM.x(), nny = NRM.y(), nnz = NRM.z();
        vertex(m, out, ax, ay, az, u0, vt, light, overlay, r, g, b, a, nnx, nny, nnz);
        vertex(m, out, bx, by, bz, u1, vt, light, overlay, r, g, b, a, nnx, nny, nnz);
        vertex(m, out, cx, cy, cz, u1, vb, light, overlay, r, g, b, a, nnx, nny, nnz);
        vertex(m, out, dx, dy, dz, u0, vb, light, overlay, r, g, b, a, nnx, nny, nnz);
    }

    private static void vertex(Matrix4f m, VertexConsumer out, float x, float y, float z, float u, float v, int light, int overlay,
                               float r, float g, float b, float a, float nx, float ny, float nz) {
        m.transform(POS.set(x, y, z, 1f));
        out.vertex(POS.x(), POS.y(), POS.z(), r, g, b, a, u, v, overlay, light, nx, ny, nz);
    }
}

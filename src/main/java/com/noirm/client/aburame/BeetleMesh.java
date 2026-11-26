package com.noirm.client.aburame;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.aburame.Species;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Writes tiny voxel beetles (3×2×4 "pixels", after the owner's reference 33: a charcoal segmented shell,
 * a pale cracked shield behind the head, teal eyes) into one buffer, many at a time. No allocation per
 * beetle: a swarm of 700 is one pass of plain arithmetic. The texture is ours ({@code tools/aburamegen}).
 *
 * <p>Texture layout (16×16): top face u 0–3 × v 0–4 (eyes, head, shield, shell); underside u 4–7 × v 0–4;
 * sides u 8–12 × v 0–2; front/back u 12–15 × v 0–2; wings u 0–4 × v 6–10.
 */
public final class BeetleMesh {
    private final Matrix4f pose;
    private final Matrix3f normal;
    private final VertexConsumer out;
    private final int light;
    private float r = 1, g = 1, b = 1;

    public BeetleMesh(PoseStack stack, VertexConsumer out, int light) {
        this.pose = stack.last().pose();
        this.normal = stack.last().normal();
        this.out = out;
        this.light = light;
    }

    public static ResourceLocation texture(Species species) {
        return Noirm.id("textures/entity/aburame/" + species.key() + ".png");
    }

    public void tint(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    /**
     * One beetle at (x, y, z), heading along (fx, fy, fz) (needn't be unit length), {@code len} long.
     * {@code wings}: draw its wings open (a flicker frame).
     */
    public void beetle(float x, float y, float z, float fx, float fy, float fz, float len, boolean wings) {
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-5f) {
            fx = 0;
            fy = 0;
            fz = 1;
            fl = 1;
        }
        fx /= fl;
        fy /= fl;
        fz /= fl;
        // right = forward × up(0,1,0); up = right × forward
        float rx = -fz, ry = 0, rz = fx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        float ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        float hz = len * 0.5f, hx = len * 0.375f, hy = len * 0.25f;
        float ax = rx * hx, ay = ry * hx, az = rz * hx;
        float bx = ux * hy, by = uy * hy, bz = uz * hy;
        float cx = fx * hz, cy = fy * hz, cz = fz * hz;
        // top (+up), shell texture
        quad(x + bx, y + by, z + bz, ax, ay, az, cx, cy, cz, ux, uy, uz, 0, 0, 3, 4, 1f);
        // underside
        quad(x - bx, y - by, z - bz, -ax, -ay, -az, cx, cy, cz, -ux, -uy, -uz, 4, 0, 7, 4, 0.55f);
        // right and left sides
        quad(x + ax, y + ay, z + az, bx, by, bz, cx, cy, cz, rx, ry, rz, 8, 0, 12, 2, 0.75f);
        quad(x - ax, y - ay, z - az, -bx, -by, -bz, cx, cy, cz, -rx, -ry, -rz, 8, 0, 12, 2, 0.75f);
        // head and tail
        quad(x + cx, y + cy, z + cz, ax, ay, az, bx, by, bz, fx, fy, fz, 12, 0, 15, 2, 0.85f);
        quad(x - cx, y - cy, z - cz, -ax, -ay, -az, bx, by, bz, -fx, -fy, -fz, 12, 0, 15, 2, 0.7f);
        if (wings) {
            // Two pale wings flicking out to the sides, a little above the shell.
            float wy = 1.4f;
            float ox = x + bx * wy, oy = y + by * wy, oz = z + bz * wy;
            quad(ox + ax * 1.6f - cx * 0.2f, oy + ay * 1.6f - cy * 0.2f, oz + az * 1.6f - cz * 0.2f,
                    ax * 1.1f, ay * 1.1f, az * 1.1f, cx * 0.9f, cy * 0.9f, cz * 0.9f, ux, uy, uz, 0, 6, 4, 10, 1f);
            quad(ox - ax * 1.6f - cx * 0.2f, oy - ay * 1.6f - cy * 0.2f, oz - az * 1.6f - cz * 0.2f,
                    ax * 1.1f, ay * 1.1f, az * 1.1f, cx * 0.9f, cy * 0.9f, cz * 0.9f, ux, uy, uz, 0, 6, 4, 10, 1f);
        }
    }

    /** A quad centred at (x, y, z) spanned by ±(ux…) and ±(vx…), textured from texels (u0, v0)–(u1, v1). */
    private void quad(float x, float y, float z, float ux, float uy, float uz, float vx, float vy, float vz,
                      float nx, float ny, float nz, int u0, int v0, int u1, int v1, float shade) {
        float a0 = u0 / 16f, a1 = u1 / 16f, b0 = v0 / 16f, b1 = v1 / 16f;
        float cr = r * shade, cg = g * shade, cb = b * shade;
        vertex(x - ux + vx, y - uy + vy, z - uz + vz, a0, b0, cr, cg, cb, nx, ny, nz);
        vertex(x + ux + vx, y + uy + vy, z + uz + vz, a1, b0, cr, cg, cb, nx, ny, nz);
        vertex(x + ux - vx, y + uy - vy, z + uz - vz, a1, b1, cr, cg, cb, nx, ny, nz);
        vertex(x - ux - vx, y - uy - vy, z - uz - vz, a0, b1, cr, cg, cb, nx, ny, nz);
    }

    private void vertex(float x, float y, float z, float u, float v, float cr, float cg, float cb, float nx, float ny, float nz) {
        out.vertex(pose, x, y, z).color(cr, cg, cb, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, nx, ny, nz).endVertex();
    }
}

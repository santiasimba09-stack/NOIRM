package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.network.VfxPackets;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws lightning bolts: a jagged path made by splitting the line in half again and again and
 * nudging each midpoint sideways (less each time, and less near the ends so the bolt stays
 * attached), plus dimmer side forks that can fork again. Each segment is drawn three times with
 * additive blending: a wide faint glow, a coloured body and a thin white-hot core. The shape is
 * re-rolled every couple of ticks and its brightness flickers, so the bolt crackles, then it fades.
 *
 * <p>Style flags ride in the packet's {@code branches} (above the fork count):
 * {@link #ARC} bows the bolt sideways (arcs wrapping around a body), {@link #GROUND} keeps it
 * flat (forking along the ground), {@link #IMPACT} bursts sparks and a pool of light where it
 * lands, {@link #FORKY} lets the forks fork again.
 *
 * <p>Performance (blueprint §1): each bolt keeps its points in one float array made when it
 * arrives; shaping and drawing allocate nothing per frame. At most {@link #MAX_BOLTS} at once.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class BoltRenderer {
    public static final int ARC = 1 << 8;
    public static final int GROUND = 1 << 9;
    public static final int IMPACT = 1 << 10;
    public static final int FORKY = 1 << 11;

    private static final int DEPTH = 5;
    private static final int POINTS = (1 << DEPTH) + 1;
    private static final int MAX_BOLTS = 256;
    private static final List<Bolt> BOLTS = new ArrayList<>();

    private BoltRenderer() {}

    private static final class Bolt {
        final double x1, y1, z1, x2, y2, z2;
        final float r, g, b;
        final float width;
        final int life;
        final int branches;
        final int flags;
        final long born;
        /** Bow direction for ARC bolts (horizontal, perpendicular to the bolt). */
        final double bowX, bowZ;
        long seed;
        /** Up to 1 + branches·3 paths of POINTS points (x, y, z). */
        final float[] pts;
        /** Per path: points used (0 = unused) and strength (main 1, forks dimmer). */
        final int[] used;
        final float[] strength;
        int paths;
        float flicker = 1f;
        long shapedAt = -1000;

        Bolt(VfxPackets.Bolt packet, long now) {
            x1 = packet.x1();
            y1 = packet.y1();
            z1 = packet.z1();
            x2 = packet.x2();
            y2 = packet.y2();
            z2 = packet.z2();
            r = (packet.color() >> 16 & 0xFF) / 255f;
            g = (packet.color() >> 8 & 0xFF) / 255f;
            b = (packet.color() & 0xFF) / 255f;
            width = packet.width();
            life = Math.max(1, packet.life());
            branches = Math.min(8, packet.branches() & 0xFF);
            flags = packet.branches() & 0xFF00;
            born = now;
            seed = Double.doubleToLongBits(x1 * 31 + z2 * 17 + now) ^ System.nanoTime();
            int maxPaths = 1 + branches * ((flags & FORKY) != 0 ? 3 : 1);
            pts = new float[maxPaths * POINTS * 3];
            used = new int[maxPaths];
            strength = new float[maxPaths];
            double dx = x2 - x1, dz = z2 - z1;
            double h = Math.sqrt(dx * dx + dz * dz);
            double side = nextDouble() < 0.5 ? -1 : 1;
            if (h < 1e-4) {
                double a = nextDouble() * Math.PI * 2;
                bowX = Math.cos(a);
                bowZ = Math.sin(a);
            } else {
                bowX = -dz / h * side;
                bowZ = dx / h * side;
            }
        }

        double nextDouble() {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            return (seed >>> 11) * 0x1.0p-53;
        }

        /** A cheap bell-shaped random number (mean 0, about unit spread). */
        double nextGaussian() {
            return (nextDouble() + nextDouble() + nextDouble() - 1.5) * 2;
        }
    }

    public static void add(VfxPackets.Bolt packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || BOLTS.size() >= MAX_BOLTS) {
            return;
        }
        Bolt bolt = new Bolt(packet, minecraft.level.getGameTime());
        BOLTS.add(bolt);
        if ((bolt.flags & IMPACT) != 0) {
            impact(minecraft.level, bolt);
        }
    }

    /** Where a bolt lands: a spark burst of sharp shards, sparks flying, and a pool of pale blue light. */
    private static void impact(ClientLevel level, Bolt bolt) {
        int color = (Math.round(bolt.r * 255) << 16) | (Math.round(bolt.g * 255) << 8) | Math.round(bolt.b * 255);
        float size = 0.5f + bolt.width * 3f;
        level.addParticle(new VfxOptions(VfxKind.ZAP, 0xFFFFFF, color, size, size * 1.3f, 5, 0f, 1f), bolt.x2, bolt.y2, bolt.z2, 0, 0, 0);
        level.addParticle(new VfxOptions(VfxKind.ZAP, color, 0x1A3A8A, size * 1.6f, size * 0.6f, 4, 0f, 1f), bolt.x2, bolt.y2, bolt.z2, 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            level.addParticle(new VfxOptions(VfxKind.SPARK, 0xFFFFFF, color, 0.12f, 0.03f, 6 + level.random.nextInt(5), 0.04f, 0.86f),
                    bolt.x2, bolt.y2, bolt.z2, level.random.nextGaussian() * 0.3, Math.abs(level.random.nextGaussian()) * 0.3, level.random.nextGaussian() * 0.3);
        }
        level.addParticle(new VfxOptions(VfxKind.GROUND_GLOW, 0x3C6EB4, 0x0A1430, 1.5f + bolt.width * 8f, 2.5f + bolt.width * 10f, 8, 0f, 1f),
                bolt.x2, Math.floor(bolt.y2 + 0.01) + 0.03, bolt.z2, 0, 0, 0);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BOLTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            BOLTS.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        for (int k = BOLTS.size() - 1; k >= 0; k--) {
            Bolt bolt = BOLTS.get(k);
            float age = (now - bolt.born) + event.getPartialTick();
            if (age > bolt.life || now < bolt.born) {
                BOLTS.remove(k);
                continue;
            }
            if (bolt.paths == 0 || now - bolt.shapedAt >= 2) { // crackle: a new shape every other tick
                bolt.shapedAt = now;
                shape(bolt);
                bolt.flicker = 0.65f + 0.35f * (float) bolt.nextDouble();
            }
            // A strong first frame, then a slower decay.
            float t = age / bolt.life;
            float fade = (t < 0.15f ? 1f : 1f - (t - 0.15f) / 0.85f) * bolt.flicker;
            for (int i = 0; i < bolt.paths; i++) {
                if (bolt.used[i] < 2) {
                    continue;
                }
                float strength = bolt.strength[i];
                float w = bolt.width * (i == 0 ? 1f : 0.55f);
                int base = i * POINTS * 3;
                int n = bolt.used[i];
                ribbon(out, matrix, cam, bolt.pts, base, n, w * 5f, bolt.r * 0.6f, bolt.g * 0.7f, bolt.b, 0.22f * fade * strength);
                ribbon(out, matrix, cam, bolt.pts, base, n, w * 2.2f, bolt.r, bolt.g, bolt.b, 0.6f * fade * strength);
                ribbon(out, matrix, cam, bolt.pts, base, n, w, Math.min(1, bolt.r + 0.6f), Math.min(1, bolt.g + 0.6f), Math.min(1, bolt.b + 0.6f),
                        fade * strength);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    /** Re-rolls the bolt's main path and its forks (and the forks' forks). */
    private static void shape(Bolt bolt) {
        boolean ground = (bolt.flags & GROUND) != 0;
        boolean arc = (bolt.flags & ARC) != 0;
        bolt.paths = 0;
        double length = Math.sqrt(sq(bolt.x2 - bolt.x1) + sq(bolt.y2 - bolt.y1) + sq(bolt.z2 - bolt.z1));
        int main = addPath(bolt, bolt.x1, bolt.y1, bolt.z1, bolt.x2, bolt.y2, bolt.z2, 1f, ground);
        if (arc) {
            // Bow the whole path sideways: an arc wrapping around whatever it crawls over.
            int base = main * POINTS * 3;
            double bow = length * 0.42;
            for (int p = 0; p < POINTS; p++) {
                double s = Math.sin(Math.PI * p / (POINTS - 1)) * bow;
                bolt.pts[base + p * 3] += (float) (bolt.bowX * s);
                bolt.pts[base + p * 3 + 2] += (float) (bolt.bowZ * s);
            }
        }
        boolean forky = (bolt.flags & FORKY) != 0;
        for (int i = 0; i < bolt.branches; i++) {
            int fork = fork(bolt, main, length, 0.2 + 0.3 * bolt.nextDouble(), 0.5f, ground);
            if (forky && fork >= 0) {
                for (int j = 0; j < 2; j++) {
                    fork(bolt, fork, length * 0.5, 0.25 + 0.3 * bolt.nextDouble(), 0.3f, ground);
                }
            }
        }
    }

    /** A side fork off path {@code from}: starts partway along it and heads off roughly the same way. Returns its index or -1. */
    private static int fork(Bolt bolt, int from, double length, double share, float strength, boolean ground) {
        int n = bolt.used[from];
        if (n < 6 || bolt.paths >= bolt.used.length) {
            return -1;
        }
        int at = 2 + (int) (bolt.nextDouble() * (n - 4));
        int base = from * POINTS * 3;
        double sx = bolt.pts[base + at * 3], sy = bolt.pts[base + at * 3 + 1], sz = bolt.pts[base + at * 3 + 2];
        int last = base + (n - 1) * 3;
        double dx = bolt.pts[last] - bolt.pts[base], dy = bolt.pts[last + 1] - bolt.pts[base + 1], dz = bolt.pts[last + 2] - bolt.pts[base + 2];
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-4) {
            return -1;
        }
        double ox = bolt.nextGaussian(), oy = ground ? bolt.nextGaussian() * 0.1 : bolt.nextGaussian(), oz = bolt.nextGaussian();
        double ol = Math.sqrt(ox * ox + oy * oy + oz * oz) + 1e-6;
        double fx = dx / dl + ox / ol * 0.9, fy = dy / dl + oy / ol * 0.9, fz = dz / dl + oz / ol * 0.9;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz) + 1e-6;
        double reach = length * share;
        return addPath(bolt, sx, sy, sz, sx + fx / fl * reach, sy + fy / fl * reach, sz + fz / fl * reach, strength, ground);
    }

    /** Midpoint displacement with an envelope that pins both ends; writes into the bolt's arrays. */
    private static int addPath(Bolt bolt, double ax, double ay, double az, double bx, double by, double bz, float strength, boolean ground) {
        int index = bolt.paths++;
        int base = index * POINTS * 3;
        float[] p = bolt.pts;
        p[base] = (float) ax;
        p[base + 1] = (float) ay;
        p[base + 2] = (float) az;
        int end = base + (POINTS - 1) * 3;
        p[end] = (float) bx;
        p[end + 1] = (float) by;
        p[end + 2] = (float) bz;
        double offset = Math.sqrt(sq(bx - ax) + sq(by - ay) + sq(bz - az)) * 0.2;
        double vertical = ground ? 0.12 : 1;
        for (int step = POINTS - 1; step > 1; step /= 2, offset *= 0.55) {
            for (int i = step / 2; i < POINTS; i += step) {
                int a = base + (i - step / 2) * 3;
                int c = base + (i + step / 2) * 3;
                double t = i / (double) (POINTS - 1);
                double envelope = Math.min(1, Math.min(t, 1 - t) * 4) * offset;
                int m = base + i * 3;
                p[m] = (float) ((p[a] + p[c]) * 0.5 + bolt.nextGaussian() * envelope);
                p[m + 1] = (float) ((p[a + 1] + p[c + 1]) * 0.5 + bolt.nextGaussian() * envelope * vertical);
                p[m + 2] = (float) ((p[a + 2] + p[c + 2]) * 0.5 + bolt.nextGaussian() * envelope);
            }
        }
        bolt.used[index] = POINTS;
        bolt.strength[index] = strength;
        return index;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Camera-facing quads along a path (both windings: the render type culls back faces). */
    private static void ribbon(VertexConsumer out, Matrix4f matrix, Vec3 cam, float[] pts, int base, int count, float width,
                               float r, float g, float b, float a) {
        if (a <= 0.01f) {
            return;
        }
        double half = width / 2;
        for (int i = 0; i + 1 < count; i++) {
            int i0 = base + i * 3, i1 = i0 + 3;
            double p0x = pts[i0] - cam.x, p0y = pts[i0 + 1] - cam.y, p0z = pts[i0 + 2] - cam.z;
            double p1x = pts[i1] - cam.x, p1y = pts[i1 + 1] - cam.y, p1z = pts[i1 + 2] - cam.z;
            double ax = p1x - p0x, ay = p1y - p0y, az = p1z - p0z;
            double mx = (p0x + p1x) * 0.5, my = (p0y + p1y) * 0.5, mz = (p0z + p1z) * 0.5;
            // side = along × (towards the camera), so the ribbon faces the viewer.
            double sx = ay * mz - az * my, sy = az * mx - ax * mz, sz = ax * my - ay * mx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-9) {
                continue;
            }
            sx = sx / sl * half;
            sy = sy / sl * half;
            sz = sz / sl * half;
            float ax0 = (float) (p0x - sx), ay0 = (float) (p0y - sy), az0 = (float) (p0z - sz);
            float bx0 = (float) (p0x + sx), by0 = (float) (p0y + sy), bz0 = (float) (p0z + sz);
            float ax1 = (float) (p1x - sx), ay1 = (float) (p1y - sy), az1 = (float) (p1z - sz);
            float bx1 = (float) (p1x + sx), by1 = (float) (p1y + sy), bz1 = (float) (p1z + sz);
            out.vertex(matrix, ax0, ay0, az0).color(r, g, b, a).endVertex();
            out.vertex(matrix, bx0, by0, bz0).color(r, g, b, a).endVertex();
            out.vertex(matrix, bx1, by1, bz1).color(r, g, b, a).endVertex();
            out.vertex(matrix, ax1, ay1, az1).color(r, g, b, a).endVertex();
            out.vertex(matrix, ax1, ay1, az1).color(r, g, b, a).endVertex();
            out.vertex(matrix, bx1, by1, bz1).color(r, g, b, a).endVertex();
            out.vertex(matrix, bx0, by0, bz0).color(r, g, b, a).endVertex();
            out.vertex(matrix, ax0, ay0, az0).color(r, g, b, a).endVertex();
        }
    }

    /** Bolts on screen right now (for the client smoke test). */
    public static int count() {
        return BOLTS.size();
    }
}

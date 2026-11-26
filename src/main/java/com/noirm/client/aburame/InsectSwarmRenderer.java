package com.noirm.client.aburame;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noirm.aburame.ColonyGrowth;
import com.noirm.aburame.InsectSwarm;
import com.noirm.aburame.Species;
import com.noirm.aburame.SwarmMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a swarm as hundreds of tiny voxel beetles (docs/ABURAME.md, 2.5): one entity, many instanced
 * beetles, with level of detail by distance; the swarm's dust is the entity's own client particles.
 * Each mode has its own shape: a swirling ball, a stream with branching tendrils when it flies (ref 34),
 * a crawling coat on what it clings to, a shell for the Sphere, a sheet for the Wall, a spinning dome,
 * a funnel for the Tornado, a carpet for the Black Sea, purple-black for rinkaichū (ref 35).
 */
public class InsectSwarmRenderer extends EntityRenderer<InsectSwarm> {
    /** The most beetles drawn for one swarm, and per insect (1 beetle per this many insects). */
    public static final int MAX_BEETLES = 1600;
    public static final int SEA_BEETLES = 2400;
    public static final int INSECTS_PER_BEETLE = 4;
    /** A beetle's length in blocks by species. */
    static final float LEN_KIKAICHU = 0.06f, LEN_KIDAICHU = 0.16f, LEN_RINKAICHU = 0.05f, LEN_SHOKAICHU = 0.12f;

    /** Each swarm's smoothed flight direction (for its stream). */
    private static final Map<InsectSwarm, double[]> HEADING = new WeakHashMap<>();

    public InsectSwarmRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(InsectSwarm swarm) {
        return BeetleMesh.texture(swarm.species());
    }

    @Override
    public void render(InsectSwarm swarm, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        int count = swarm.count();
        if (count <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        SwarmMode mode = swarm.mode();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 here = swarm.getPosition(partial);
        double dist = cam.distanceTo(here);
        float lod = dist < 14 ? 1f : dist < 28 ? 0.7f : dist < 56 ? 0.4f : 0.2f;
        int max = mode == SwarmMode.SEA ? SEA_BEETLES : mode == SwarmMode.JAR ? 1200 : MAX_BEETLES;
        int n = Math.max(24, Math.min(max, count / INSECTS_PER_BEETLE));
        if (mode == SwarmMode.JAM) {
            n = Math.min(n, 260);
        }
        if (mode == SwarmMode.SPHERE || mode == SwarmMode.DOME || mode == SwarmMode.WALL) {
            n = Math.max(n, 2400); // a shell needs to be dense to read as solid
        }
        n = Math.max(12, Math.round(n * lod));
        float len = switch (swarm.species()) {
            case KIDAICHU -> LEN_KIDAICHU;
            case RINKAICHU -> LEN_RINKAICHU;
            case SHOKAICHU -> LEN_SHOKAICHU;
            default -> LEN_KIKAICHU;
        };
        if (dist > 20) {
            len *= 1f + (float) (dist - 20) / 40f; // far away they merge into a mass instead of vanishing
        }
        if (mode == SwarmMode.SPHERE || mode == SwarmMode.DOME || mode == SwarmMode.WALL || mode == SwarmMode.CLING) {
            len *= 1.5f; // packed shoulder to shoulder, the shell reads solid
        }
        float r = ColonyGrowth.radius(count);
        float t = (swarm.tickCount + partial) / 20f;
        stack.pushPose();
        float cy = swarm.getBbHeight() / 2f;
        BeetleMesh mesh = new BeetleMesh(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(swarm))), light);
        double[] head = heading(swarm, partial);
        Entity target = swarm.targetEntityId() >= 0 ? swarm.level().getEntity(swarm.targetEntityId()) : null;
        float[] p = new float[3];
        float[] q = new float[3];
        int flick = swarm.tickCount;
        for (int i = 0; i < n; i++) {
            place(mode, swarm, i, n, t, r, cy, head, target, here, p);
            place(mode, swarm, i, n, t + 0.05f, r, cy, head, target, here, q);
            boolean wings = ((i * 7 + flick) & 3) == 0;
            mesh.beetle(p[0], p[1], p[2], q[0] - p[0], q[1] - p[1], q[2] - p[2], len, wings);
        }
        stack.popPose();
        super.render(swarm, yaw, partial, stack, buffers, light);
    }

    /** The smoothed direction the swarm flies (from its movement), for the stream's tail. */
    private static double[] heading(InsectSwarm swarm, float partial) {
        double[] h = HEADING.computeIfAbsent(swarm, s -> new double[]{0, 0, 1, 0});
        double dx = swarm.getX() - swarm.xo, dy = swarm.getY() - swarm.yo, dz = swarm.getZ() - swarm.zo;
        double sp = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (sp > 0.02) {
            h[0] = Mth.lerp(0.2, h[0], dx / sp);
            h[1] = Mth.lerp(0.2, h[1], dy / sp);
            h[2] = Mth.lerp(0.2, h[2], dz / sp);
        }
        h[3] = Mth.lerp(0.2, h[3], sp);
        double l = Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
        if (l > 1e-4) {
            h[0] /= l;
            h[1] /= l;
            h[2] /= l;
        }
        return h;
    }

    // ---------------------------------------------------------------- where beetle i is

    /** A stable pseudo-random number in [0, 1) for beetle {@code i}, channel {@code c}. */
    static float hash(int i, int c) {
        int x = i * 374761393 + c * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        x ^= x >>> 16;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Writes beetle {@code i}'s position (relative to the entity's feet) at time {@code t} into {@code out}. */
    static void place(SwarmMode mode, InsectSwarm swarm, int i, int n, float t, float r, float cy, double[] head, Entity target,
                      Vec3 here, float[] out) {
        float h1 = hash(i, 1), h2 = hash(i, 2), h3 = hash(i, 3), h4 = hash(i, 4);
        switch (mode) {
            case TRAVEL, RETURN -> {
                if (head[3] < 0.05 || h1 < 0.38f) {
                    ball(i, t, r * 0.85f, cy, out, 1.3f); // the head of the swarm
                } else {
                    stream(i, t, r, cy, head, out);
                }
            }
            case CLING -> cling(i, t, r, cy, target, here, out);
            case SPHERE -> {
                // A ball that swallows the target whole and sits on the ground (it never sinks into it).
                float rad = target == null ? r : Math.max(Math.max(target.getBbWidth() * 0.95f, target.getBbHeight() * 0.62f), Math.min(r * 0.85f, 2.6f));
                float ox = 0, oy = cy, oz = 0;
                if (target != null) {
                    Vec3 tp = target.getPosition(Minecraft.getInstance().getFrameTime());
                    ox = (float) (tp.x - here.x);
                    oz = (float) (tp.z - here.z);
                    oy = (float) (tp.y - here.y) + Math.max(rad * 0.92f, target.getBbHeight() * 0.5f);
                }
                shell(i, n, t, rad, oy, out, 1.6f, h1 < 0.1f ? 1.18f : 1f);
                out[0] += ox;
                out[2] += oz;
            }
            case TORNADO -> {
                float hh = h1 * 5f - 2f;
                float rad = 0.6f + (hh + 2f) * 0.38f + (h2 - 0.5f) * 0.25f;
                float a = t * 6f + hh * 1.6f + h3 * Mth.TWO_PI;
                out[0] = Mth.cos(a) * rad;
                out[1] = cy + hh;
                out[2] = Mth.sin(a) * rad;
            }
            case WALL -> {
                float x = (h1 - 0.5f) * InsectSwarm.WALL_WIDTH + Mth.sin(t * 1.7f + h3 * 20f) * 0.25f;
                float y = (h2 - 0.5f) * InsectSwarm.WALL_HEIGHT + Mth.sin(t * 2.3f + h1 * 30f) * 0.15f;
                float z = (h3 - 0.5f) * 0.7f + Mth.sin(t * 3f + i) * 0.08f;
                float yaw = swarm.shape() * Mth.DEG_TO_RAD;
                // facing = (-sin yaw, 0, cos yaw); right = (-cos yaw, 0, -sin yaw)
                float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
                out[0] = -fz * x + fx * z;
                out[1] = cy + y;
                out[2] = fx * x + fz * z;
            }
            case DOME -> {
                float rad = InsectSwarm.DOME_RADIUS * (0.92f + h3 * 0.16f);
                float a = h1 * Mth.TWO_PI + t * 5f * (h4 < 0.5f ? 1f : 0.8f);
                float el = (float) Math.asin(h2) ; // upper half, denser low
                out[0] = Mth.cos(a) * Mth.cos(el) * rad;
                out[1] = Mth.sin(el) * rad - 0.2f;
                out[2] = Mth.sin(a) * Mth.cos(el) * rad;
            }
            case JAM -> {
                float rad = swarm.shape() * (float) Math.sqrt(h1);
                float a = h2 * Mth.TWO_PI + t * 0.15f * (h3 - 0.5f);
                out[0] = Mth.cos(a) * rad + Mth.sin(t + i) * 0.3f;
                out[1] = cy - 1f + h3 * 4f + Mth.sin(t * 2f + i) * 0.2f;
                out[2] = Mth.sin(a) * rad + Mth.cos(t + i) * 0.3f;
            }
            case SEA -> {
                float rad = swarm.shape() * (float) Math.pow(h1, 0.6);
                float a = h2 * Mth.TWO_PI + t * (0.6f + 0.4f * h4) * (1.6f - rad / Math.max(1f, swarm.shape()));
                float y = (float) Math.pow(h3, 2.2) * 3.2f;
                if (h4 < 0.08f) {
                    rad *= 0.08f; // a churning pillar at the heart
                    y = h3 * 9f;
                }
                out[0] = Mth.cos(a) * rad;
                out[1] = y - 0.6f + Mth.sin(t * 3f + i) * 0.12f;
                out[2] = Mth.sin(a) * rad;
            }
            case POISON -> {
                float range = swarm.shape();
                float d = (float) Math.sqrt(h1) * range;
                float spread = d * 0.62f * (float) Math.sqrt(h2);
                float a = h3 * Mth.TWO_PI + t * 2f;
                float yaw = swarm.getYRot() * Mth.DEG_TO_RAD;
                float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
                float ox = Mth.cos(a) * spread, oy = Mth.sin(a) * spread * 0.6f;
                float along = d - range * 0.5f;
                out[0] = fx * along - fz * ox;
                out[1] = cy + oy;
                out[2] = fz * along + fx * ox;
            }
            case JAR -> {
                float rad = swarm.shape() >= 40f ? swarm.shape() * (float) Math.sqrt(h1) : swarm.shape() * (0.96f + h1 * 0.08f);
                float a = h2 * Mth.TWO_PI + t * 0.8f;
                out[0] = Mth.cos(a) * rad;
                out[1] = h3 * (swarm.shape() >= 40f ? 8f : 3.5f);
                out[2] = Mth.sin(a) * rad;
            }
            default -> ball(i, t, mode == SwarmMode.SCOUT ? Math.min(r, 0.8f) : r, cy, out, 1f);
        }
    }

    /** A living ball: each beetle orbits the centre on its own tilted circle, denser in the middle, jittering. */
    static void ball(int i, float t, float r, float cy, float[] out, float speed) {
        float h1 = hash(i, 1), h2 = hash(i, 2), h3 = hash(i, 3), h4 = hash(i, 4), h5 = hash(i, 5);
        float rad = r * (0.18f + 0.82f * (float) Math.pow(h1, 0.55));
        float tilt = (h2 - 0.5f) * Mth.PI;
        float spin = h3 * Mth.TWO_PI;
        float w = (1.8f + 2.6f * h4) * speed / Math.max(0.35f, rad) * (h5 < 0.5f ? 1f : -1f);
        float a = spin + t * w;
        float x = Mth.cos(a) * rad, z = Mth.sin(a) * rad;
        float ct = Mth.cos(tilt), st = Mth.sin(tilt);
        float jitter = 0.04f + 0.03f * h2;
        out[0] = x + Mth.sin(t * 13f + i) * jitter;
        out[1] = cy + z * st * 0.85f + Mth.sin(t * 11f + i * 0.7f) * jitter;
        out[2] = z * ct + Mth.cos(t * 12f + i) * jitter;
        // a slow breathing so the whole mass churns
        float breathe = 1f + 0.08f * Mth.sin(t * 1.3f + h4 * 6f);
        out[0] *= breathe;
        out[2] *= breathe;
    }

    /** The stream behind a flying swarm: a few branching tendrils that trail and whip (ref 34). */
    static void stream(int i, float t, float r, float cy, double[] head, float[] out) {
        float h1 = hash(i, 6), h2 = hash(i, 7), h3 = hash(i, 8), h4 = hash(i, 9);
        int tendrils = 4;
        int k = (int) (h1 * tendrils);
        float s = (float) Math.pow(h2, 1.5); // more beetles near the head
        float length = r * 3.2f + (float) head[3] * 9f;
        float fx = (float) head[0], fy = (float) head[1], fz = (float) head[2];
        // two perpendicular axes
        float rx = -fz, rz = fx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-3f) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        float base = k * Mth.TWO_PI / tendrils + 0.4f * Mth.sin(t * 0.9f + k);
        float spread = r * (0.25f + 1.1f * s); // tendrils fan out further back
        float whip = Mth.sin(t * 3.1f - s * 6f + k * 1.7f) * r * 0.45f * s;
        float a = base + whip;
        // a sub-branch: some beetles split off mid-way
        if (h4 < 0.3f && s > 0.4f) {
            a += (h4 < 0.15f ? 0.6f : -0.6f) * (s - 0.4f);
        }
        float swirl = r * 0.22f * (1f - s) + 0.06f;
        float sa = t * 9f + h3 * Mth.TWO_PI;
        float ox = Mth.cos(a) * spread + Mth.cos(sa) * swirl;
        float oy = Mth.sin(a) * spread + Mth.sin(sa) * swirl;
        float back = -s * length;
        out[0] = fx * back + rx * ox + ux * oy;
        out[1] = cy + fy * back + uy * oy;
        out[2] = fz * back + rz * ox + uz * oy;
    }

    /** Crawling over what it holds: most beetles on the body's surface, some buzzing around it. */
    static void cling(int i, float t, float r, float cy, Entity target, Vec3 here, float[] out) {
        if (target == null) {
            ball(i, t, r, cy, out, 1f);
            return;
        }
        float h1 = hash(i, 1), h2 = hash(i, 2), h3 = hash(i, 3), h4 = hash(i, 4);
        float w = Math.max(0.35f, target.getBbWidth() * 0.62f);
        float hgt = target.getBbHeight();
        // target's feet relative to this entity's feet
        Vec3 tp = target.getPosition(Minecraft.getInstance().getFrameTime());
        float bx = (float) (tp.x - here.x), by = (float) (tp.y - here.y), bz = (float) (tp.z - here.z);
        if (h4 < 0.85f) {
            float y = h2 * hgt;
            float a = h1 * Mth.TWO_PI + t * (0.4f + h3 * 0.5f) * (h3 < 0.5f ? 1f : -1f) + y * 0.8f;
            float rad = w * (1.05f + 0.25f * hash(i, 9) + 0.08f * Mth.sin(t * 7f + i)) * (y > hgt * 0.8f ? 0.8f : 1f);
            out[0] = bx + Mth.cos(a) * rad;
            out[1] = by + y + Mth.sin(t * 5f + i) * 0.03f;
            out[2] = bz + Mth.sin(a) * rad;
        } else {
            ball(i, t, Math.max(r, hgt * 0.75f), by + hgt * 0.55f, out, 1.4f);
            out[0] += bx;
            out[2] += bz;
        }
    }

    /** A shell (the Sphere): beetles spread evenly over a turning ball, a few loose outside it. */
    static void shell(int i, int n, float t, float rad, float cy, float[] out, float speed, float outer) {
        float golden = 2.39996323f;
        float y = 1f - 2f * (i + 0.5f) / n;
        float ring = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        float a = i * golden + t * speed;
        float jitter = 1f + (hash(i, 5) - 0.5f) * 0.12f + 0.03f * Mth.sin(t * 9f + i);
        float rr = rad * jitter * outer;
        out[0] = Mth.cos(a) * ring * rr;
        out[1] = cy + y * rr;
        out[2] = Mth.sin(a) * ring * rr;
    }

    @Override
    public boolean shouldRender(InsectSwarm swarm, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (swarm.mode() == SwarmMode.SEA || swarm.mode() == SwarmMode.JAM || swarm.mode() == SwarmMode.JAR
                || swarm.mode() == SwarmMode.TRAVEL || swarm.mode() == SwarmMode.RETURN || swarm.mode() == SwarmMode.POISON) {
            return swarm.shouldRenderAtSqrDistance(swarm.distanceToSqr(x, y, z)); // big shapes reach past their box
        }
        return super.shouldRender(swarm, frustum, x, y, z);
    }

    /** Species palette for anything that wants to tint by species. */
    static int shell(Species s) {
        return s.shell;
    }
}

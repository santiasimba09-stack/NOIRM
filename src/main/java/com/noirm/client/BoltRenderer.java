package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.network.VfxPackets;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws lightning bolts: a jagged path made by splitting the line in half again and again and
 * nudging each midpoint sideways (less each time, and less near the ends so the bolt stays
 * attached), plus dimmer side forks. Each segment is drawn twice with additive blending: a wide
 * coloured glow and a thin white-hot core. The shape is re-rolled every couple of ticks so the
 * bolt crackles, and it fades out over its life.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class BoltRenderer {
    private static final int DEPTH = 5;
    private static final List<Bolt> BOLTS = new ArrayList<>();

    private BoltRenderer() {}

    private static final class Bolt {
        final Vec3 from;
        final Vec3 to;
        final float r;
        final float g;
        final float b;
        final float width;
        final int life;
        final int branches;
        final long born;
        long seed;
        List<List<Vec3>> paths = List.of();
        /** When the shape was last rolled; far in the past so the first frame shapes it. */
        long shapedAt = -1000;

        Bolt(VfxPackets.Bolt packet, long now) {
            from = new Vec3(packet.x1(), packet.y1(), packet.z1());
            to = new Vec3(packet.x2(), packet.y2(), packet.z2());
            r = (packet.color() >> 16 & 0xFF) / 255f;
            g = (packet.color() >> 8 & 0xFF) / 255f;
            b = (packet.color() & 0xFF) / 255f;
            width = packet.width();
            life = Math.max(1, packet.life());
            branches = packet.branches();
            born = now;
            seed = RandomSource.create().nextLong();
        }
    }

    public static void add(VfxPackets.Bolt packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || BOLTS.size() > 256) {
            return;
        }
        BOLTS.add(new Bolt(packet, minecraft.level.getGameTime()));
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
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt bolt = it.next();
            float age = (now - bolt.born) + event.getPartialTick();
            if (age > bolt.life) {
                it.remove();
                continue;
            }
            if (bolt.paths.isEmpty() || now - bolt.shapedAt >= 2) { // crackle: a new shape every other tick
                bolt.shapedAt = now;
                bolt.seed = bolt.seed * 6364136223846793005L + 1442695040888963407L;
                bolt.paths = shape(bolt, RandomSource.create(bolt.seed));
            }
            float fade = 1f - age / bolt.life;
            for (int i = 0; i < bolt.paths.size(); i++) {
                float strength = i == 0 ? 1f : 0.45f; // forks are dimmer than the main bolt
                float w = bolt.width * (i == 0 ? 1f : 0.6f);
                ribbon(out, matrix, cam, bolt.paths.get(i), w * 4f, bolt.r, bolt.g, bolt.b, 0.5f * fade * strength);
                ribbon(out, matrix, cam, bolt.paths.get(i), w * 2f, bolt.r, bolt.g, bolt.b, 0.6f * fade * strength);
                ribbon(out, matrix, cam, bolt.paths.get(i), w, Math.min(1, bolt.r + 0.6f), Math.min(1, bolt.g + 0.6f), Math.min(1, bolt.b + 0.6f),
                        1f * fade * strength);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static List<List<Vec3>> shape(Bolt bolt, RandomSource random) {
        List<List<Vec3>> paths = new ArrayList<>();
        List<Vec3> main = displace(bolt.from, bolt.to, random);
        paths.add(main);
        double length = bolt.from.distanceTo(bolt.to);
        for (int i = 0; i < bolt.branches && main.size() > 4; i++) {
            int at = 2 + random.nextInt(main.size() - 4);
            Vec3 start = main.get(at);
            Vec3 dir = bolt.to.subtract(bolt.from).normalize();
            Vec3 off = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vec3 end = start.add(dir.add(off.scale(0.9)).normalize().scale(length * (0.2 + 0.25 * random.nextDouble())));
            paths.add(displace(start, end, random));
        }
        return paths;
    }

    /** Midpoint displacement with an envelope that pins both ends. */
    private static List<Vec3> displace(Vec3 from, Vec3 to, RandomSource random) {
        int count = (1 << DEPTH) + 1;
        Vec3[] points = new Vec3[count];
        points[0] = from;
        points[count - 1] = to;
        double offset = from.distanceTo(to) * 0.18;
        for (int step = count - 1; step > 1; step /= 2, offset *= 0.55) {
            for (int i = step / 2; i < count; i += step) {
                Vec3 a = points[i - step / 2];
                Vec3 b = points[i + step / 2];
                double t = i / (double) (count - 1);
                double envelope = Math.min(1, Math.min(t, 1 - t) * 4);
                Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(offset * envelope);
                points[i] = a.add(b).scale(0.5).add(jitter);
            }
        }
        return List.of(points);
    }

    /** Camera-facing quads along a path. */
    private static void ribbon(VertexConsumer out, Matrix4f matrix, Vec3 cam, List<Vec3> path, float width,
                               float r, float g, float b, float a) {
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 p0 = path.get(i).subtract(cam);
            Vec3 p1 = path.get(i + 1).subtract(cam);
            Vec3 along = p1.subtract(p0);
            Vec3 side = along.cross(p0.add(p1).scale(0.5)).normalize().scale(width / 2);
            if (!Double.isFinite(side.x)) {
                continue;
            }
            out.vertex(matrix, (float) (p0.x - side.x), (float) (p0.y - side.y), (float) (p0.z - side.z)).color(r, g, b, a).endVertex();
            out.vertex(matrix, (float) (p0.x + side.x), (float) (p0.y + side.y), (float) (p0.z + side.z)).color(r, g, b, a).endVertex();
            out.vertex(matrix, (float) (p1.x + side.x), (float) (p1.y + side.y), (float) (p1.z + side.z)).color(r, g, b, a).endVertex();
            out.vertex(matrix, (float) (p1.x - side.x), (float) (p1.y - side.y), (float) (p1.z - side.z)).color(r, g, b, a).endVertex();
            // The same quad wound the other way: the render type culls back faces.
            out.vertex(matrix, (float) (p1.x - side.x), (float) (p1.y - side.y), (float) (p1.z - side.z)).color(r, g, b, a).endVertex();
            out.vertex(matrix, (float) (p1.x + side.x), (float) (p1.y + side.y), (float) (p1.z + side.z)).color(r, g, b, a).endVertex();
            out.vertex(matrix, (float) (p0.x + side.x), (float) (p0.y + side.y), (float) (p0.z + side.z)).color(r, g, b, a).endVertex();
            out.vertex(matrix, (float) (p0.x - side.x), (float) (p0.y - side.y), (float) (p0.z - side.z)).color(r, g, b, a).endVertex();
        }
    }
}

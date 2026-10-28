package com.noirm.vfx;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Lightning release, the way the owner's references draw it (docs/ART_STYLE.md, "Jutsu effects"):
 * blue-white, jagged and branching, never smooth. Arcs crawl over bodies (Lightning Armor), bolts
 * fork along the ground and between targets, sharp spark bursts flare at every contact point, and
 * a pale blue light pools on the ground under it all.
 *
 * <p>Bolts are drawn by the client ({@code client/BoltRenderer}); the style flags below ride in the
 * bolt packet's fork count (same wire format). Server side only for bolts; particles work on both.
 */
public final class LightningVfx {
    /** Bow the bolt sideways: an arc wrapping around a body. */
    public static final int ARC = 1 << 8;
    /** Keep it flat: forking along the ground. */
    public static final int GROUND = 1 << 9;
    /** Spark burst and a pool of light where it lands. */
    public static final int IMPACT = 1 << 10;
    /** Forks fork again. */
    public static final int FORKY = 1 << 11;

    /** Blue-white: the glow of the bolts. */
    public static final int BLUE_WHITE = 0x9FD4FF;
    /** The deeper blue of lesser arcs. */
    public static final int BLUE = Vfx.LIGHTNING;
    private static final int POOL = 0x2E5C9E;

    private LightningVfx() {}

    /** A crackling bolt with style {@code flags} (see the constants). */
    public static void bolt(Level level, Vec3 from, Vec3 to, int color, float width, int life, int branches, int flags) {
        Vfx.bolt(level, from, to, color, width, life, Math.min(8, branches) | flags);
    }

    /** A sharp spark burst at a contact point: shards of light and sparks flying. */
    public static void contact(Level level, Vec3 at, float size) {
        Vfx.burst(level, new VfxOptions(VfxKind.ZAP, 0xFFFFFF, BLUE_WHITE, size, size * 1.3f, 5, 0f, 1f), at, 1, 0, 0);
        Vfx.burst(level, new VfxOptions(VfxKind.ZAP, BLUE_WHITE, 0x1A3A8A, size * 1.7f, size * 0.5f, 4, 0f, 1f), at, 1, size * 0.15, 0);
        Vfx.burst(level, new VfxOptions(VfxKind.SPARK, 0xFFFFFF, BLUE_WHITE, 0.13f, 0.03f, 8, 0.04f, 0.86f), at, 6 + (int) (size * 4), size * 0.2, 0.25);
    }

    /** A pool of pale blue light on the ground (the glow lightning throws on its surroundings). */
    public static void pool(Level level, Vec3 ground, float radius, int life) {
        Vfx.burst(level, new VfxOptions(VfxKind.GROUND_GLOW, POOL, 0x0A1430, radius, radius * 1.2f, life, 0f, 1f),
                FireVfx.floorBelow(level, ground, 3), 1, 0, 0);
    }

    /**
     * Lightning Armor, every other tick: two or three arcs wrapping around the body at different
     * heights, one crawling from the feet up, a flickering blue-white shell of light, a pool of light
     * underfoot, and sparks snapping off the feet while moving.
     */
    public static void armor(Level level, Entity body, int tick) {
        RandomSource random = level.random;
        Vec3 feet = body.position();
        float w = body.getBbWidth();
        float h = body.getBbHeight();
        double reach = w * 0.9 + 0.25;
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double y0 = random.nextDouble() * h;
            double y1 = Math.max(0, Math.min(h * 1.1, y0 + (random.nextDouble() - 0.5) * h * 0.8));
            Vec3 from = feet.add(Math.cos(a) * reach, y0, Math.sin(a) * reach);
            double b = a + Math.PI * (0.7 + 0.6 * random.nextDouble());
            Vec3 to = feet.add(Math.cos(b) * reach, y1, Math.sin(b) * reach);
            bolt(level, from, to, BLUE_WHITE, 0.05f + random.nextFloat() * 0.03f, 4, 1, ARC);
        }
        if (tick % 6 == 0) {
            // A long arc climbing the body from the ground: the reference's big swirl.
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 from = feet.add(Math.cos(a) * (reach + 0.4), 0.05, Math.sin(a) * (reach + 0.4));
            Vec3 to = feet.add(-Math.cos(a) * reach, h * (0.8 + 0.4 * random.nextDouble()), -Math.sin(a) * reach);
            bolt(level, from, to, BLUE_WHITE, 0.08f, 4, 2, ARC);
        }
        // The shell: soft blue-white glows hugging the body, flickering.
        Vfx.burst(level, new VfxOptions(VfxKind.GLOW, 0xDDF2FF, 0x2A6AD0, 0.7f + random.nextFloat() * 0.35f, 0.3f, 5, -0.01f, 0.9f),
                feet.add(0, h * 0.55, 0), 5, w * 0.5, 0.01);
        Vfx.burst(level, new VfxOptions(VfxKind.GLOW, 0x6AA8FF, 0x0A1A50, 1.6f, 1.9f, 5, 0f, 0.9f), feet.add(0, h * 0.5, 0), 1, 0.05, 0);
        if (tick % 4 == 0) {
            pool(level, feet, 1.6f + random.nextFloat() * 0.5f, 6);
        }
        Vec3 motion = body.getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z > 0.004 || random.nextInt(4) == 0) {
            Vec3 foot = feet.add((random.nextDouble() - 0.5) * w, 0.05, (random.nextDouble() - 0.5) * w);
            Vfx.burst(level, new VfxOptions(VfxKind.ZAP, 0xFFFFFF, BLUE_WHITE, 0.35f, 0.5f, 3, 0f, 1f), foot, 1, 0, 0);
            Vfx.burst(level, new VfxOptions(VfxKind.SPARK, 0xFFFFFF, BLUE_WHITE, 0.1f, 0.02f, 6, 0.05f, 0.85f), foot, 3, 0.1, 0.18);
        }
    }

    /**
     * Bolts racing outward from {@code center} along the ground, forking as they go, each ending
     * in a spark burst (Chidori Current, Kirin's landing, lightning blasts).
     */
    public static void groundForks(Level level, Vec3 center, float radius, int count) {
        RandomSource random = level.random;
        Vec3 ground = new Vec3(center.x, Math.floor(center.y) + 0.1, center.z);
        for (int i = 0; i < count; i++) {
            double a = (i + random.nextDouble() * 0.6) / count * Math.PI * 2;
            double d = radius * (0.6 + 0.4 * random.nextDouble());
            Vec3 end = ground.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            bolt(level, ground, end, BLUE_WHITE, 0.07f, 7, 3, GROUND | FORKY | (i % 2 == 0 ? IMPACT : 0));
        }
        pool(level, ground, radius * 1.1f, 10);
    }

    /**
     * Lightning bindings (Thunder Snare): three rings of short arcs snapped shut around the body
     * (ankles, waist, shoulders), arcs running between the rings, and a flicker of sparks.
     * {@code tick} staggers the rings so they crawl.
     */
    public static void shackle(Level level, Entity body, int tick) {
        RandomSource random = level.random;
        Vec3 feet = body.position();
        float w = body.getBbWidth();
        float h = body.getBbHeight();
        double reach = w * 0.7 + 0.2;
        double[] heights = {0.15, 0.5, 0.8};
        for (int ring = 0; ring < heights.length; ring++) {
            double y = h * heights[ring];
            double start = (tick * 0.7 + ring * 2.1) % (Math.PI * 2);
            int segments = 3;
            for (int k = 0; k < segments; k++) {
                double a = start + k * Math.PI * 2 / segments;
                double b = a + Math.PI * 2 / segments;
                Vec3 from = feet.add(Math.cos(a) * reach, y, Math.sin(a) * reach);
                Vec3 to = feet.add(Math.cos(b) * reach, y + (random.nextDouble() - 0.5) * 0.15, Math.sin(b) * reach);
                bolt(level, from, to, k % 2 == 0 ? BLUE_WHITE : BLUE, 0.05f, 4, 1, ARC);
            }
        }
        // An arc tying the rings together, from the ankles up.
        double a = random.nextDouble() * Math.PI * 2;
        bolt(level, feet.add(Math.cos(a) * reach, h * 0.1, Math.sin(a) * reach),
                feet.add(-Math.cos(a) * reach, h * 0.85, -Math.sin(a) * reach), BLUE_WHITE, 0.06f, 4, 2, ARC);
        Vfx.burst(level, new VfxOptions(VfxKind.SPARK, 0xFFFFFF, BLUE_WHITE, 0.1f, 0.02f, 6, 0.04f, 0.85f),
                feet.add(0, h * 0.5, 0), 3, w * 0.5, 0.12);
        if (tick % 6 == 0) {
            pool(level, feet, 1.2f, 6);
        }
    }

    /** A forking bolt between two points with a spark burst at the far end (chains, beams, strikes). */
    public static void forked(Level level, Vec3 from, Vec3 to, float width, int life) {
        bolt(level, from, to, BLUE_WHITE, width, life, 4, FORKY | IMPACT);
        bolt(level, from, to, BLUE, width * 0.6f, Math.max(2, life - 2), 2, 0);
    }
}

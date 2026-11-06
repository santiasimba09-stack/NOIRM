package com.noirm.vfx;

import com.noirm.clan.Nature;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The look of three kekkei genkai (docs/CHAKRA_WAVE.md, Part 1):
 * <ul>
 *   <li><b>Scorch</b>: small suns. A solid white core, an orange body, a pale rim, and a faint heat
 *       haze rising off them; what they touch dries out in bursts of steam.</li>
 *   <li><b>Dust</b>: pale, exact geometry. Cubes and cones drawn by their white-hot edges, faint
 *       light on the faces, motes of matter coming apart.</li>
 *   <li><b>Magnet</b>: black iron sand that moves like a liquid: dark grains streaming along curves,
 *       heavy dark billows for volume, and a glitter of tiny pale sparks.</li>
 * </ul>
 * Same rules as {@link Vfx}: a strong first frame, then a slower decay. Works on both sides.
 */
public final class ScorchDustMagnetVfx {
    // Scorch
    public static final int SUN_WHITE = 0xFFFFF4;
    public static final int SUN_CORE = 0xFFF0B0;
    public static final int SUN_ORANGE = 0xFF8A10;
    public static final int SUN_RED = 0xB83000;
    public static final int SUN_RIM = 0xFFE6A0;
    public static final int STEAM = 0xFFFFFF;
    // Dust
    public static final int DUST_WHITE = 0xFFFFFF;
    public static final int DUST_PALE = 0xE4F2FF;
    public static final int DUST_EDGE = 0x8AA8C0;
    // Magnet
    public static final int SAND_BLACK = 0x0A0A10;
    public static final int SAND_DARK = 0x22222E;
    public static final int GLINT = 0xD8E0FF;
    public static final int FIELD = 0x6A6AB0;

    private ScorchDustMagnetVfx() {}

    // ================================================================ Scorch

    /** One tick of a scorch orb of {@code size}: solid white core, orange body, pale rim, heat haze. */
    public static void sun(Level level, Vec3 at, Vec3 motion, float size) {
        RandomSource random = level.random;
        Vfx.one(level, Vfx.glow(SUN_WHITE, SUN_CORE, size * 0.75f, size * 0.6f, 3), at, motion);
        Vfx.one(level, Vfx.glow(SUN_ORANGE, SUN_RED, size * 1.15f, size * 0.8f, 4), at, motion);
        Vfx.one(level, Vfx.glow(SUN_RIM, SUN_ORANGE, size * 1.5f, size * 1.1f, 2), at, motion);
        // The heat haze: a faint warm shimmer drifting up off it.
        if (random.nextInt(2) == 0) {
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, 0x3A2A14, 0x000000, size * 1.8f, size * 3f, 12, -0.01f, 0.9f),
                    at.add(0, size * 0.4, 0), new Vec3(0, 0.03, 0));
        }
        // A flare licking off the surface now and then.
        if (random.nextInt(4) == 0) {
            Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vfx.one(level, FireVfx.tongue(size * 0.7f, 6), at.add(out.scale(size * 0.5)), out.scale(0.05).add(motion.scale(0.6)));
        }
        if (random.nextInt(3) == 0) {
            Vfx.one(level, FireVfx.ember(16), at, motion.scale(-0.2).add(random.nextGaussian() * 0.04, 0.04, random.nextGaussian() * 0.04));
        }
    }

    /** A sun bursting: a white flash, a ring of heat, a puff of steam. */
    public static void sunBurst(Level level, Vec3 at, float size) {
        Vfx.burst(level, Vfx.glow(SUN_WHITE, SUN_RIM, size * 2.4f, size * 0.6f, 4), at, 1, 0, 0);
        Vfx.burst(level, Vfx.glow(SUN_ORANGE, SUN_RED, size * 1.6f, size * 2.2f, 10), at, 2, size * 0.2, 0);
        Vfx.radial(level, Vfx.spark(SUN_RIM, 0.16f, 10), at, 10, 0.35);
        steam(level, at, 4, size * 0.5);
    }

    /** White steam rising: water boiled out of a body. */
    public static void steam(Level level, Vec3 at, int count, double spread) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 from = at.add(random.nextGaussian() * spread, random.nextGaussian() * spread * 0.5, random.nextGaussian() * spread);
            Vfx.one(level, new VfxOptions(VfxKind.SMOKE, STEAM, 0xCFCAC4, 0.35f, 1.3f, 26 + random.nextInt(14), -0.012f, 0.93f), from,
                    new Vec3(random.nextGaussian() * 0.02, 0.06 + random.nextDouble() * 0.05, random.nextGaussian() * 0.02));
        }
    }

    /** A body drying out: steam gushing off it and dry flakes falling. */
    public static void desiccate(Level level, Entity body, float strength) {
        Vec3 feet = body.position();
        float h = body.getBbHeight();
        steam(level, feet.add(0, h * 0.6, 0), (int) (5 + strength * 4), body.getBbWidth() * 0.5);
        Vfx.burst(level, Vfx.glow(SUN_RIM, SUN_ORANGE, 0.6f + strength * 0.3f, 0.1f, 5), feet.add(0, h * 0.55, 0), 1, 0, 0);
        Vfx.burst(level, new VfxOptions(VfxKind.SHARD, 0xC8B088, 0x6A5A40, 0.08f, 0.05f, 24, 0.04f, 0.95f), feet.add(0, h * 0.6, 0),
                (int) (4 + strength * 3), body.getBbWidth() * 0.4, 0.04);
    }

    /** Suns circling a body (the Scorching Halo, a latched Steaming Murder): {@code count} of them at {@code radius}. */
    public static void orbitSuns(Level level, Vec3 center, float radius, int count, float size, long tick, double height) {
        for (int k = 0; k < count; k++) {
            double a = tick * 0.22 + k * Math.PI * 2 / count;
            double y = Math.sin(tick * 0.12 + k * 1.7) * height;
            sun(level, center.add(Math.cos(a) * radius, y, Math.sin(a) * radius), Vec3.ZERO, size);
        }
    }

    // ================================================================ Dust

    /** A crisp line of white light from {@code a} to {@code b} (an edge of a Dust shape). */
    public static void edge(Level level, Vec3 a, Vec3 b, float width, int life) {
        double length = a.distanceTo(b);
        Vec3 dir = length < 1e-4 ? Vec3.ZERO : b.subtract(a).scale(1 / length);
        int steps = Math.max(1, (int) Math.ceil(length / 0.3));
        for (int i = 0; i <= steps; i++) {
            Vfx.one(level, Vfx.glow(DUST_WHITE, DUST_PALE, width, width * 0.5f, life), a.add(dir.scale(length * i / steps)), Vec3.ZERO);
        }
    }

    /** The twelve edges of a cube of half-size {@code half} at {@code center}, plus a faint glow on the faces. */
    public static void cube(Level level, Vec3 center, double half, float width, int life) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            c[i] = center.add((i & 1) == 0 ? -half : half, (i & 2) == 0 ? -half : half, (i & 4) == 0 ? -half : half);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            edge(level, c[e[0]], c[e[1]], width, life);
        }
        // Corners flare brightest.
        for (Vec3 corner : c) {
            Vfx.one(level, Vfx.glow(DUST_WHITE, DUST_PALE, width * 1.4f, width * 0.5f, life + 1), corner, Vec3.ZERO);
        }
        RandomSource random = level.random;
        for (int i = 0; i < 2 + (int) half; i++) {
            Vec3 inside = center.add((random.nextDouble() * 2 - 1) * half, (random.nextDouble() * 2 - 1) * half, (random.nextDouble() * 2 - 1) * half);
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, 0x323C46, 0x080A0C, (float) half * 0.35f, (float) half * 0.2f, life + 2, 0f, 0.9f), inside, Vec3.ZERO);
        }
    }

    /** Motes of matter coming apart: tiny white sparks drifting up and out. */
    public static void motes(Level level, Vec3 at, int count, double spread) {
        Vfx.burst(level, new VfxOptions(VfxKind.SPARK, DUST_WHITE, DUST_EDGE, 0.08f, 0.02f, 18, -0.01f, 0.94f), at, count, spread, 0.03);
    }

    /**
     * A square cone (a pyramid of light) from {@code apex} along {@code dir}, {@code length} long and
     * {@code radius} wide at its base: four edges, the base square, and faint rings inside.
     */
    public static void cone(Level level, Vec3 apex, Vec3 dir, double length, double radius, int life) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        Vec3 base = apex.add(dir.scale(length));
        Vec3[] corners = {
                base.add(side.scale(radius)).add(up.scale(radius)), base.add(side.scale(-radius)).add(up.scale(radius)),
                base.add(side.scale(-radius)).add(up.scale(-radius)), base.add(side.scale(radius)).add(up.scale(-radius))};
        for (int i = 0; i < 4; i++) {
            edge(level, apex, corners[i], 0.16f, life);
            edge(level, corners[i], corners[(i + 1) % 4], 0.2f, life);
        }
        for (double d = length * 0.25; d < length; d += length * 0.25) {
            double r = radius * d / length;
            Vec3 at = apex.add(dir.scale(d));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Vec3 p = at.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                Vfx.one(level, new VfxOptions(VfxKind.GLOW, 0x6A7A88, 0x101418, (float) r * 0.5f + 0.2f, 0.1f, life + 3, 0f, 0.9f), p, dir.scale(0.02));
            }
        }
        Vfx.one(level, Vfx.glow(DUST_WHITE, DUST_PALE, 0.9f, 0.2f, life), apex, Vec3.ZERO);
    }

    /** A Dust shell around a body: a slowly turning cube of edges. */
    public static void dustShell(Level level, Vec3 center, double half, long tick) {
        double turn = tick * 0.05;
        Vec3[] square = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double a = turn + i * Math.PI / 2 + Math.PI / 4;
            square[i] = new Vec3(Math.cos(a) * half * 1.41, 0, Math.sin(a) * half * 1.41);
        }
        for (int i = 0; i < 4; i++) {
            Vec3 lo = center.add(square[i]).add(0, -half, 0);
            Vec3 hi = center.add(square[i]).add(0, half, 0);
            Vec3 lo2 = center.add(square[(i + 1) % 4]).add(0, -half, 0);
            Vec3 hi2 = center.add(square[(i + 1) % 4]).add(0, half, 0);
            edge(level, lo, hi, 0.1f, 3);
            edge(level, lo, lo2, 0.1f, 3);
            edge(level, hi, hi2, 0.1f, 3);
        }
        if (level.random.nextInt(2) == 0) {
            motes(level, center, 2, half * 0.8);
        }
    }

    // ================================================================ Magnet

    /** A grain of iron sand flowing with {@code velocity} (heavy, a touch of drag, no gravity: it is held). */
    public static VfxOptions grain(float size, int life) {
        return new VfxOptions(VfxKind.SHARD, SAND_BLACK, SAND_DARK, size, size * 0.7f, life, 0f, 0.9f);
    }

    /** A dark billow of sand: what gives a stream its volume. */
    public static VfxOptions billow(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, SAND_BLACK, SAND_DARK, size, size * 1.3f, life, 0f, 0.88f);
    }

    public static VfxOptions glint(int life) {
        return new VfxOptions(VfxKind.SPARK, 0xFFFFFF, GLINT, 0.07f, 0.02f, life, 0f, 0.9f);
    }

    /** A blob of iron sand at {@code at}: billows, grains churning, a glitter. */
    public static void sandMass(Level level, Vec3 at, Vec3 motion, float size) {
        RandomSource random = level.random;
        Vfx.one(level, billow(size * 0.9f, 6 + random.nextInt(4)), at, motion);
        for (int i = 0; i < 2 + (int) size * 2; i++) {
            Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(size * 0.35);
            Vfx.one(level, grain(0.12f + random.nextFloat() * 0.08f, 10 + random.nextInt(6)), at.add(jitter), motion.add(jitter.scale(-0.08)));
        }
        if (random.nextInt(2) == 0) {
            Vfx.one(level, glint(6), at.add(random.nextGaussian() * size * 0.4, random.nextGaussian() * size * 0.4, random.nextGaussian() * size * 0.4), motion);
        }
    }

    /**
     * A stream of iron sand along a quadratic curve from {@code a} through {@code bend} to {@code b},
     * drawn up to {@code progress} (0–1) of its length, {@code thickness} wide.
     */
    public static void stream(Level level, Vec3 a, Vec3 bend, Vec3 b, double progress, float thickness) {
        int steps = (int) Math.max(4, a.distanceTo(b) * 2.5);
        Vec3 last = a;
        for (int i = 1; i <= steps * progress; i++) {
            double t = i / (double) steps;
            Vec3 p = curve(a, bend, b, t);
            Vec3 flow = p.subtract(last).scale(0.4);
            sandMass(level, p, flow, thickness * (float) (0.7 + 0.3 * Math.sin(t * Math.PI)));
            last = p;
        }
    }

    /** Point {@code t} along the quadratic curve a → bend → b. */
    public static Vec3 curve(Vec3 a, Vec3 bend, Vec3 b, double t) {
        double u = 1 - t;
        return a.scale(u * u).add(bend.scale(2 * u * t)).add(b.scale(t * t));
    }

    /** Magnetic field loops around a body: pale arcs from the top of it to its feet. */
    public static void fieldLines(Level level, Entity body, long tick) {
        Vec3 c = body.position().add(0, body.getBbHeight() * 0.5, 0);
        double h = body.getBbHeight() * 0.6;
        for (int k = 0; k < 3; k++) {
            double a = tick * 0.15 + k * Math.PI * 2 / 3;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            for (int i = 0; i <= 8; i++) {
                double t = i / 8.0 * Math.PI;
                Vec3 p = c.add(out.scale(Math.sin(t) * (body.getBbWidth() + 0.5))).add(0, Math.cos(t) * h, 0);
                Vfx.one(level, Vfx.glow(GLINT, FIELD, 0.12f, 0.03f, 4), p, Vec3.ZERO);
            }
        }
    }

    /** Iron sand dome around a body (the shield): grains running over a sphere's surface, billows at the base. */
    public static void sandDome(Level level, Vec3 feet, double radius, long tick) {
        RandomSource random = level.random;
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double up = random.nextDouble() * Math.PI * 0.5;
            Vec3 p = feet.add(Math.cos(a) * Math.cos(up) * radius, Math.sin(up) * radius * 1.1, Math.sin(a) * Math.cos(up) * radius);
            Vec3 along = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.12); // the sand runs round the dome
            Vfx.one(level, grain(0.16f, 8), p, along);
            if (i % 3 == 0) {
                Vfx.one(level, billow(0.5f, 8), p, along.scale(0.5));
            }
        }
        Vfx.one(level, glint(5), feet.add(random.nextGaussian() * radius * 0.6, radius * random.nextDouble(), random.nextGaussian() * radius * 0.6), Vec3.ZERO);
    }

    /** A thrown needle of iron sand: a dark streak and a glint at the tip. */
    public static void needle(Level level, Vec3 at, Vec3 motion) {
        for (int i = 0; i < 3; i++) {
            Vfx.one(level, grain(0.13f - i * 0.02f, 3), at.subtract(motion.scale(i * 0.3)), Vec3.ZERO);
        }
        Vfx.one(level, glint(3), at, Vec3.ZERO);
        if (level.random.nextInt(2) == 0) {
            Vfx.one(level, billow(0.25f, 8), at.subtract(motion.scale(0.5)), motion.scale(-0.05));
        }
    }

    /** Iron sand hitting the ground: a dark splash of grains, a ring, a glitter. */
    public static void sandImpact(Level level, Vec3 at, float r) {
        RandomSource random = level.random;
        Vfx.burst(level, Vfx.glow(GLINT, FIELD, r * 0.8f, 0.1f, 3), at, 1, 0, 0);
        for (int i = 0; i < 8 + (int) (r * 6); i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.8, random.nextGaussian()).normalize();
            Vfx.one(level, new VfxOptions(VfxKind.SHARD, SAND_BLACK, SAND_DARK, 0.16f, 0.1f, 24, 0.05f, 0.94f), at, dir.scale(0.2 + r * 0.08));
        }
        Vfx.burst(level, billow(r * 0.5f, 22), at.add(0, 0.3, 0), (int) (3 + r * 2), r * 0.4, 0.02);
        Vfx.burst(level, Vfx.ring(FIELD, r * 1.6f, 10), at.add(0, 0.06, 0), 1, 0, 0);
        Vfx.burst(level, glint(10), at, (int) (4 + r * 3), r * 0.5, 0.06);
    }

    // ================================================================ projectile trails

    /**
     * The trail of a projectile of one of these natures (or Wind and Earth, see {@link WindEarthVfx}),
     * drawn by the client each tick. Returns false for natures it doesn't draw.
     */
    public static boolean projectileTrail(Level level, Nature nature, Vec3 at, Vec3 motion, float size, int age) {
        if (nature == null) {
            return false;
        }
        switch (nature) {
            case SCORCH -> {
                sun(level, at, motion, Math.max(0.35f, size * 0.8f));
                return true;
            }
            case MAGNET -> {
                needle(level, at, motion);
                return true;
            }
            case WIND, EARTH -> {
                return WindEarthVfx.projectileTrail(level, nature, at, motion, size, age);
            }
            default -> {
                return false;
            }
        }
    }
}

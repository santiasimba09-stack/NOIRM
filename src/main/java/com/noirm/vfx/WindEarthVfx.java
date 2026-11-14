package com.noirm.vfx;

import com.noirm.clan.Nature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The look of the two basic natures this wave fills out (docs/CHAKRA_WAVE.md, Part 1):
 * <ul>
 *   <li><b>Wind</b>: pale grey-white crescents with a bright leading edge, visible rings of
 *       pressure, and grey dust kicked up wherever the air passes. A thin grey smoke behind each
 *       crescent keeps it readable against a bright sky.</li>
 *   <li><b>Earth</b>: heavy rock, dust and debris. Slow, weighty motion: dust that billows and
 *       hangs, chunks that arc and fall, the ground's own blocks thrown up.</li>
 * </ul>
 * Works on both sides, like {@link Vfx}.
 */
public final class WindEarthVfx {
    // Wind
    public static final int WIND_WHITE = 0xFFFFFF;
    public static final int WIND_PALE = 0xDCE6EA;
    public static final int WIND_GREY = 0x8C989E;
    public static final int WIND_DUST = 0xA6A8A6;
    // Earth
    public static final int ROCK_DARK = 0x2A221C;
    public static final int ROCK = 0x5E5040;
    public static final int EARTH_DUST = 0x8E7E66;
    public static final int EARTH_DUST_LIGHT = 0xB8A88E;

    private WindEarthVfx() {}

    // ================================================================ Wind

    /** Grey smoke that keeps wind readable on a bright day. */
    public static VfxOptions windSmoke(float size, int life) {
        return new VfxOptions(VfxKind.SMOKE, WIND_GREY, 0xC8D0D4, size, size * 1.8f, life, -0.002f, 0.88f);
    }

    /** Dust kicked up off the ground by the wind. */
    public static VfxOptions windDust(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, WIND_DUST, 0xD4D6D4, size, size * 2f, life, -0.001f, 0.86f);
    }

    /**
     * A crescent blade of air at {@code center}, flying along {@code dir}, {@code width} from tip
     * to tip: a bright leading edge thickest in the middle, tips swept back, a grey wake.
     */
    public static void crescent(Level level, Vec3 center, Vec3 dir, double width, Vec3 velocity) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        int n = Math.max(4, (int) (width * 3));
        for (int i = -n; i <= n; i++) {
            double t = i / (double) n;
            double a = t * 1.2;
            Vec3 p = center.add(side.scale(Math.sin(a) * width * 0.5)).add(dir.scale((Math.cos(a) - 1) * width * 0.35));
            float s = (float) (0.26 * (1 - 0.7 * Math.abs(t)));
            Vfx.one(level, Vfx.glow(WIND_WHITE, WIND_PALE, s, s * 0.3f, 3), p, velocity);
            if ((i & 1) == 0) {
                Vfx.one(level, windSmoke(s * 1.3f, 10), p.subtract(dir.scale(0.3)), velocity.scale(0.2));
            }
        }
    }

    /**
     * A ring of pressure: points around {@code axis} at {@code radius}, flying outward, so the ring
     * visibly swells and thins out.
     */
    public static void pressureRing(Level level, Vec3 center, Vec3 axis, double radius, double expand, int life) {
        Vec3 a = axis.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : axis.normalize();
        Vec3 u = a.cross(Math.abs(a.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = a.cross(u).normalize();
        int n = Math.max(10, (int) (radius * 10));
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            Vec3 out = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            Vfx.one(level, Vfx.glow(WIND_PALE, WIND_GREY, 0.18f, 0.05f, life), center.add(out.scale(radius)), out.scale(expand));
            if (i % 3 == 0) {
                Vfx.one(level, windSmoke(0.25f, life + 6), center.add(out.scale(radius)), out.scale(expand * 0.6));
            }
        }
    }

    /** Dust skirting outward over the ground (a gust landing, a tornado's foot). */
    public static void dustSkirt(Level level, Vec3 at, float r, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.one(level, windDust(r * 0.3f + 0.2f, 24 + random.nextInt(12)), at.add(out.scale(r * 0.5)).add(0, 0.15, 0),
                    out.scale(0.2 + r * 0.04).add(0, 0.02, 0));
        }
    }

    /**
     * One tick of a tornado at {@code base}: a funnel of crescents whirling round (wider at the top),
     * grey dust and the ground's own blocks dragged up, a ring of dust at its foot.
     */
    public static void tornado(Level level, Vec3 base, double radius, double height, long tick) {
        RandomSource random = level.random;
        int layers = (int) Math.max(4, height / 0.9);
        for (int k = 0; k < layers; k++) {
            double t = k / (double) (layers - 1);
            double r = radius * (0.35 + 0.85 * t);
            double y = t * height;
            double spin = tick * 0.55 + k * 0.7;
            for (int j = 0; j < 2; j++) {
                double ang = spin + j * Math.PI;
                Vec3 p = base.add(Math.cos(ang) * r, y, Math.sin(ang) * r);
                Vec3 tangent = new Vec3(-Math.sin(ang), 0.15, Math.cos(ang));
                crescentArc(level, p, tangent, r * 0.9, tangent.scale(0.25));
            }
            if (random.nextInt(2) == 0) {
                double ang = random.nextDouble() * Math.PI * 2;
                Vfx.one(level, windDust(0.4f + (float) t * 0.5f, 18), base.add(Math.cos(ang) * r, y, Math.sin(ang) * r),
                        new Vec3(-Math.sin(ang) * 0.3, 0.08, Math.cos(ang) * 0.3));
            }
        }
        if (tick % 4 == 0) {
            Vfx.burst(level, Vfx.ring(WIND_PALE, (float) radius * 1.6f, 10), base.add(0, 0.06, 0), 1, 0, 0);
            dustSkirt(level, base, (float) radius, 4);
        }
        if (level instanceof ServerLevel server && tick % 3 == 0) {
            BlockState ground = groundBlock(server, base);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), base.x, base.y + 0.5, base.z, 4, radius * 0.5, 0.3, radius * 0.5, 0.2);
        }
    }

    /** A short arc of a crescent (tornado walls): a few bright points along {@code tangent}. */
    private static void crescentArc(Level level, Vec3 center, Vec3 tangent, double length, Vec3 velocity) {
        Vec3 dir = tangent.normalize();
        for (int i = -2; i <= 2; i++) {
            float s = 0.22f - Math.abs(i) * 0.04f;
            Vfx.one(level, Vfx.glow(WIND_WHITE, WIND_PALE, s, s * 0.3f, 4), center.add(dir.scale(i * length * 0.15)), velocity);
        }
        Vfx.one(level, windSmoke(0.35f, 10), center, velocity.scale(0.5));
    }

    /** A vortex wrapping a body (Gale Wall): crescents racing round it at {@code radius}. */
    public static void windWall(Level level, Vec3 feet, double radius, double height, long tick) {
        for (int k = 0; k < 3; k++) {
            double ang = tick * 0.6 + k * Math.PI * 2 / 3;
            double y = 0.2 + ((tick * 0.15 + k * 0.33) % 1.0) * height;
            Vec3 p = feet.add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
            Vec3 tangent = new Vec3(-Math.sin(ang), 0.1, Math.cos(ang));
            crescentArc(level, p, tangent, radius * 1.2, tangent.scale(0.2));
        }
        if (tick % 5 == 0) {
            dustSkirt(level, feet, (float) radius, 3);
        }
    }

    // ================================================================ Earth

    public static VfxOptions earthDust(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, EARTH_DUST, EARTH_DUST_LIGHT, size, size * 2.2f, life, -0.0015f, 0.9f);
    }

    /** A chunk of rock flying: falls heavily. */
    public static VfxOptions rock(float size, int life) {
        return new VfxOptions(VfxKind.SHARD, ROCK, ROCK_DARK, size, size * 0.8f, life, 0.09f, 0.97f);
    }

    /** The block under {@code at} (or packed mud), for block-chunk particles. */
    public static BlockState groundBlock(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 3; i++) {
            BlockState state = level.getBlockState(pos.below(i));
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return state;
            }
        }
        return Blocks.PACKED_MUD.defaultBlockState();
    }

    /**
     * The ground bursting at {@code at} with strength {@code r}: chunks of the ground's own block,
     * rocks arcing out and falling, a heavy billow of dust that hangs, a ring.
     */
    public static void groundBurst(Level level, Vec3 at, float r) {
        RandomSource random = level.random;
        for (int i = 0; i < 5 + (int) (r * 4); i++) {
            Vec3 dir = new Vec3(random.nextGaussian() * 0.6, 0.6 + random.nextDouble() * 0.8, random.nextGaussian() * 0.6).normalize();
            Vfx.one(level, rock(0.2f + random.nextFloat() * 0.15f * r, 30 + random.nextInt(10)), at.add(0, 0.2, 0), dir.scale(0.3 + r * 0.07));
        }
        for (int i = 0; i < 4 + (int) (r * 3); i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.one(level, earthDust(r * 0.35f + 0.3f, 40 + random.nextInt(20)), at.add(out.scale(r * 0.3)).add(0, 0.3, 0),
                    out.scale(0.08 + r * 0.03).add(0, 0.03, 0));
        }
        Vfx.burst(level, Vfx.ring(0xC8A878, r * 1.4f, 12), at.add(0, 0.06, 0), 1, 0, 0);
        if (level instanceof ServerLevel server) {
            BlockState ground = groundBlock(server, at);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.3, at.z, (int) (10 + r * 8),
                    r * 0.4, 0.2, r * 0.4, 0.25);
        }
    }

    /** A rock mass's surface churning (a hound's head, a boulder): dark billows and chips. */
    public static void rockMass(Level level, Vec3 at, Vec3 motion, float size) {
        RandomSource random = level.random;
        Vfx.one(level, new VfxOptions(VfxKind.SOOT, ROCK, ROCK_DARK, size, size * 0.8f, 4, 0f, 1f), at, motion);
        Vfx.one(level, new VfxOptions(VfxKind.SOOT, ROCK_DARK, ROCK, size * 0.7f, size * 0.5f, 3, 0f, 1f),
                at.add(random.nextGaussian() * size * 0.2, random.nextGaussian() * size * 0.2, random.nextGaussian() * size * 0.2), motion);
        if (random.nextInt(2) == 0) {
            Vfx.one(level, rock(0.14f, 20), at, motion.scale(0.3).add(random.nextGaussian() * 0.08, 0.12, random.nextGaussian() * 0.08));
        }
        if (random.nextInt(2) == 0) {
            Vfx.one(level, earthDust(size * 0.6f, 30), at.subtract(motion.scale(0.6)), motion.scale(0.05));
        }
    }

    /** Heavy rock rings pressing down around a body (Added-Weight Rock). */
    public static void weightRings(Level level, Vec3 feet, double width, double height) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            Vec3 p = feet.add(Math.cos(a) * width, height, Math.sin(a) * width);
            Vfx.one(level, new VfxOptions(VfxKind.SOOT, ROCK, ROCK_DARK, 0.3f, 0.2f, 10, 0.02f, 1f), p, new Vec3(0, -0.12, 0));
        }
    }

    // ================================================================ projectile trails

    static boolean projectileTrail(Level level, Nature nature, Vec3 at, Vec3 motion, float size, int age) {
        Vec3 dir = motion.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : motion.normalize();
        if (nature == Nature.WIND) {
            // A compressed ball of air: a bright, tight core, a crescent at its front, pressure rings shed behind.
            Vfx.one(level, Vfx.glow(WIND_WHITE, WIND_PALE, size * 0.6f, size * 0.3f, 3), at, motion);
            crescent(level, at.add(dir.scale(size * 0.3)), dir, Math.max(0.8, size * 1.6), motion.scale(0.6));
            if (age % 3 == 0) {
                pressureRing(level, at.subtract(dir.scale(0.4)), dir, Math.max(0.3, size * 0.5), 0.08, 6);
            }
            return true;
        }
        if (nature == Nature.EARTH) {
            rockMass(level, at, motion, Math.max(0.6f, size * 1.1f));
            if (age % 2 == 0) {
                Vfx.one(level, rock(0.18f, 24), at, motion.scale(0.2).add(level.random.nextGaussian() * 0.1, 0.1, level.random.nextGaussian() * 0.1));
            }
            return true;
        }
        return false;
    }
}

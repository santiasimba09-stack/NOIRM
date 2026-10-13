package com.noirm.vfx;

import com.noirm.clan.Nature;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * NOIRM's effect presets, built in layers the way game VFX artists do (see docs/RESEARCH.md §4):
 * a short <b>anticipation</b> (energy gathering), a sharp <b>impact</b> (white-hot flash), a
 * <b>shockwave</b> ring, <b>sparks and debris</b> flying out, then a slow <b>follow-through</b>
 * (smoke, embers). Colours follow a palette per nature: near-white core, saturated middle, dark
 * edge.
 *
 * <p>Works on both sides: on the server the particles are sent to everyone nearby, on the client
 * (projectile trails) they're spawned directly.
 */
public final class Vfx {
    /** Core (hot centre), mid (the nature's colour), edge (smoke/shadow). */
    public record Palette(int core, int mid, int edge) {}

    public static final Palette NEUTRAL = new Palette(0xFFFFFF, 0xCFE6FF, 0x5A6070);
    public static final Palette HIT = new Palette(0xFFFFFF, 0xFFE9B0, 0x6A5A40);

    private Vfx() {}

    public static Palette palette(Nature nature) {
        if (nature == null) {
            return NEUTRAL;
        }
        return switch (nature) {
            case FIRE -> new Palette(0xFFF6D0, 0xFF6A1A, 0x3A2018);
            case WATER -> new Palette(0xE8FAFF, 0x2F8BFF, 0x163A6A);
            case WIND -> new Palette(0xF4FFF8, 0x9FE7B0, 0x7A9A88);
            case EARTH -> new Palette(0xF0E0C0, 0x9C7A4A, 0x4A3A28);
            case LIGHTNING -> new Palette(0xFFFFFF, 0x9AD8FF, 0x2A3A8A);
            case WOOD -> new Palette(0xF0FFE0, 0x6FA84B, 0x4A3A20);
            case ICE -> new Palette(0xFFFFFF, 0xA8F0FF, 0x5A8AB0);
            case LAVA -> new Palette(0xFFF0A0, 0xFF4A1A, 0x2A1810);
            case STORM -> new Palette(0xFFFFFF, 0x6FC8FF, 0x1A2A5A);
            case BOIL -> new Palette(0xFFFFFF, 0xF0E8E0, 0x9A9A9A);
            case EXPLOSION -> new Palette(0xFFFFE0, 0xFFB347, 0x3A2A20);
            case MAGNET -> new Palette(0xB0B0C0, 0x4A4A5A, 0x1A1A20);
            case SCORCH -> new Palette(0xFFF0C0, 0xFF8C3A, 0x5A2A10);
            case DUST -> new Palette(0xFFFFFF, 0xF5F5F5, 0xB0B0B0);
            case YIN -> new Palette(0xC0A0FF, 0x3A1A6A, 0x0A0A12);
            case YANG -> new Palette(0xFFFFF0, 0xFFE070, 0xA08030);
            case YIN_YANG -> new Palette(0xFFFFFF, 0xC8B8FF, 0x2A2A3A);
        };
    }

    // ---------------------------------------------------------------- building blocks

    public static VfxOptions glow(int color, int endColor, float size, float endSize, int life) {
        return new VfxOptions(VfxKind.GLOW, color, endColor, size, endSize, life, 0f, 0.9f);
    }

    public static VfxOptions spark(int color, float size, int life) {
        return new VfxOptions(VfxKind.SPARK, 0xFFFFFF, color, size, size * 0.2f, life, 0.04f, 0.88f);
    }

    public static VfxOptions smoke(int color, float size, int life) {
        return new VfxOptions(VfxKind.SMOKE, color, color, size, size * 2.2f, life, -0.004f, 0.92f);
    }

    public static VfxOptions ring(int color, float radius, int life) {
        return new VfxOptions(VfxKind.RING, 0xFFFFFF, color, 0.3f, radius, life, 0f, 1f);
    }

    public static VfxOptions shard(int color, float size, int life) {
        return new VfxOptions(VfxKind.SHARD, color, color, size, size * 0.6f, life, 0.06f, 0.96f);
    }

    /** {@code count} particles in a cloud of {@code spread}, flying out at about {@code speed}. */
    public static void burst(Level level, ParticleOptions options, Vec3 at, int count, double spread, double speed) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(options, at.x, at.y, at.z, count, spread, spread, spread, speed);
            return;
        }
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(options, at.x + random.nextGaussian() * spread, at.y + random.nextGaussian() * spread,
                    at.z + random.nextGaussian() * spread, random.nextGaussian() * speed, random.nextGaussian() * speed, random.nextGaussian() * speed);
        }
    }

    /** One particle with an exact velocity. */
    public static void one(Level level, ParticleOptions options, Vec3 at, Vec3 velocity) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(options, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1);
        } else {
            level.addParticle(options, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
        }
    }

    /** Particles flying straight out in every direction (a sphere of directions). */
    public static void radial(Level level, ParticleOptions options, Vec3 at, int count, double speed) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
            one(level, options, at, dir.scale(speed * (0.6 + 0.4 * random.nextDouble())));
        }
    }

    // ---------------------------------------------------------------- presets

    /** A blinding flash: the moment of impact. */
    public static void flash(Level level, Vec3 at, Palette palette, float size) {
        burst(level, glow(palette.core(), palette.mid(), size, size * 0.3f, 5), at, 1, 0, 0);
        burst(level, glow(palette.mid(), palette.edge(), size * 0.7f, size * 1.2f, 8), at, 2, size * 0.1, 0);
    }

    /** A flat ring spreading over the ground. */
    public static void shockwave(Level level, Vec3 at, Palette palette, float radius) {
        burst(level, ring(palette.mid(), radius, 9), at.add(0, 0.05, 0), 1, 0, 0);
    }

    /** Energy gathering into a point (anticipation): particles start around it and fall inward. */
    public static void gather(Level level, Vec3 at, Palette palette, float radius, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vec3 from = at.add(dir.scale(radius));
            one(level, glow(palette.mid(), palette.core(), 0.18f, 0.05f, 10), from, dir.scale(-radius / 8));
        }
    }

    /** A hit landing: flash, sparks, a puff; heavies add a shockwave ring. */
    public static void hit(Level level, Vec3 at, Palette palette, boolean heavy) {
        flash(level, at, palette, heavy ? 1.1f : 0.6f);
        radial(level, spark(palette.mid(), heavy ? 0.22f : 0.15f, 8), at, heavy ? 14 : 7, heavy ? 0.6 : 0.4);
        burst(level, smoke(palette.edge(), 0.3f, 16), at, heavy ? 4 : 2, 0.15, 0.02);
        if (heavy) {
            shockwave(level, at.subtract(0, 0.8, 0), palette, 1.6f);
        }
    }

    /**
     * An explosion of {@code radius}: flash, a fireball of layered glows, a shockwave, sparks and
     * debris, then smoke rising and embers drifting.
     */
    public static void explosion(Level level, Vec3 at, Palette palette, float radius) {
        float r = Math.max(0.8f, radius);
        flash(level, at, palette, r * 1.4f);
        radial(level, glow(palette.core(), palette.mid(), r * 0.5f, r * 0.2f, 10), at, (int) (8 + r * 4), r * 0.18);
        radial(level, glow(palette.mid(), palette.edge(), r * 0.4f, r * 0.7f, 16), at, (int) (8 + r * 4), r * 0.12);
        shockwave(level, at, palette, r * 1.6f);
        radial(level, spark(palette.mid(), 0.25f, 14), at, (int) (10 + r * 6), r * 0.35);
        radial(level, shard(palette.edge(), 0.2f, 30), at, (int) (4 + r * 3), r * 0.25);
        burst(level, smoke(palette.edge(), r * 0.35f, 50), at.add(0, r * 0.3, 0), (int) (6 + r * 3), r * 0.4, 0.03);
        burst(level, new VfxOptions(VfxKind.GLOW, palette.mid(), palette.edge(), 0.08f, 0.02f, 40, -0.01f, 0.95f), at, (int) (6 + r * 3), r * 0.5, 0.05);
    }

    /** A ball of energy for one tick of a projectile's flight: hot core, coloured shell, a tail. */
    public static void orb(Level level, Vec3 at, Vec3 motion, Palette palette, float size) {
        burst(level, glow(palette.core(), palette.mid(), size * 0.8f, size * 0.4f, 3), at, 1, 0, 0);
        burst(level, glow(palette.mid(), palette.edge(), size * 0.9f, size * 0.2f, 8), at, 2, size * 0.25, 0.005);
        Vec3 behind = at.subtract(motion.scale(0.5));
        one(level, smoke(palette.edge(), size * 0.5f, 18), behind, motion.scale(-0.05));
        if (level.random.nextInt(3) == 0) {
            one(level, spark(palette.mid(), 0.12f, 10), at, motion.scale(-0.2).add(level.random.nextGaussian() * 0.05, 0.05, level.random.nextGaussian() * 0.05));
        }
    }

    /** A shimmering aura around a body (cloaks, armor). */
    public static void aura(Level level, Vec3 feet, float width, float height, Palette palette, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 at = feet.add((random.nextDouble() - 0.5) * width * 1.4, random.nextDouble() * height, (random.nextDouble() - 0.5) * width * 1.4);
            one(level, new VfxOptions(VfxKind.GLOW, palette.mid(), palette.edge(), 0.25f, 0.05f, 12, -0.02f, 0.9f), at, new Vec3(0, 0.04, 0));
        }
    }

    /** A line of energy from {@code from} to {@code to} (beams, chakra threads). */
    public static void line(Level level, Vec3 from, Vec3 to, Palette palette, float width) {
        double length = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        for (double d = 0; d <= length; d += 0.35) {
            Vec3 at = from.add(dir.scale(d));
            burst(level, glow(palette.core(), palette.mid(), width, width * 0.3f, 6), at, 1, 0, 0);
            if (level.random.nextInt(3) == 0) {
                burst(level, glow(palette.mid(), palette.edge(), width * 1.4f, width * 0.4f, 9), at, 1, width * 0.3, 0.01);
            }
        }
    }

    // ------------------------------------------------------------------ lightning

    /** A crackling lightning bolt from {@code from} to {@code to} (drawn by the client). */
    public static void bolt(Level level, Vec3 from, Vec3 to, int color, float width, int life, int branches) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            com.noirm.network.NoirmNetwork.sendNear(server, from, 96, new com.noirm.network.VfxPackets.Bolt(
                    from.x, from.y, from.z, to.x, to.y, to.z, color, width, life, branches));
        }
    }

    /** Lightning blue. */
    public static final int LIGHTNING = 0x5FB4FF;

    /** Small arcs crawling over a body (Lightning Armor, Chidori Current). */
    public static void arcs(Level level, net.minecraft.world.entity.Entity entity, int count, int color) {
        net.minecraft.util.RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            Vec3 a = entity.position().add((random.nextDouble() - 0.5) * entity.getBbWidth() * 1.6, random.nextDouble() * entity.getBbHeight(),
                    (random.nextDouble() - 0.5) * entity.getBbWidth() * 1.6);
            Vec3 b = a.add((random.nextDouble() - 0.5) * 1.4, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.4);
            bolt(level, a, b, color, 0.07f, 3, 1);
        }
    }

    /** Bolts lashing outward from a point to the ground around it (impacts, discharges). */
    public static void discharge(Level level, Vec3 at, float radius, int count, int color) {
        net.minecraft.util.RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = radius * (0.5 + 0.5 * random.nextDouble());
            Vec3 end = at.add(Math.cos(angle) * dist, -random.nextDouble() * radius * 0.6, Math.sin(angle) * dist);
            bolt(level, at, end, color, 0.12f, 6, 2);
        }
    }
}

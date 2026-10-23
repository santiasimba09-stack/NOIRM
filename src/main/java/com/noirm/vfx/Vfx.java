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
    /**
     * Core (white-hot centre), mid (the nature's saturated colour), edge (deep shadow/smoke) and
     * accent (a contrasting second colour for sparks, rings and embers: the "pop").
     */
    public record Palette(int core, int mid, int edge, int accent) {
        public Palette(int core, int mid, int edge) {
            this(core, mid, edge, mid);
        }
    }

    public static final Palette NEUTRAL = new Palette(0xFFFFFF, 0xBFE0FF, 0x2A3040, 0xFFFFFF);
    public static final Palette HIT = new Palette(0xFFFFFF, 0xFFD870, 0x3A2A10, 0xFFFFFF);

    private Vfx() {}

    public static Palette palette(Nature nature) {
        if (nature == null) {
            return NEUTRAL;
        }
        // Vivid, high-contrast: a near-white core, a fully saturated middle, a deep shadow, and an
        // accent that pops against the middle colour.
        return switch (nature) {
            case FIRE -> new Palette(0xFFFBE0, 0xFF5200, 0x220600, 0xFFD000);
            case WATER -> new Palette(0xF2FFFF, 0x0A7CFF, 0x021A44, 0x7FFFFF);
            case WIND -> new Palette(0xFFFFFF, 0x4CFFA0, 0x0E4A36, 0xD8FFF0);
            case EARTH -> new Palette(0xFFE8B0, 0xC4741E, 0x1E1206, 0xFFB050);
            case LIGHTNING -> new Palette(0xFFFFFF, 0x3FB8FF, 0x060E5A, 0xE0F8FF);
            case WOOD -> new Palette(0xF2FFD0, 0x3FD02A, 0x142808, 0xC8FF60);
            case ICE -> new Palette(0xFFFFFF, 0x6FE6FF, 0x0E3A80, 0xFFFFFF);
            case LAVA -> new Palette(0xFFF4A0, 0xFF3000, 0x140300, 0xFF9800);
            case STORM -> new Palette(0xFFFFFF, 0x2F9CFF, 0x06082E, 0x9FE4FF);
            case BOIL -> new Palette(0xFFFFFF, 0xFFB8A0, 0x4A3030, 0xFFF0E0);
            case EXPLOSION -> new Palette(0xFFFFE0, 0xFF9A10, 0x1E0800, 0xFF3A00);
            case MAGNET -> new Palette(0xE8E8FF, 0x5A5A9A, 0x06060E, 0xB0B0FF);
            case SCORCH -> new Palette(0xFFF6D0, 0xFF7000, 0x2E0C00, 0xFFC830);
            case DUST -> new Palette(0xFFFFFF, 0xEAF6FF, 0x6A88A0, 0xFFFFFF);
            case YIN -> new Palette(0xF0D8FF, 0x7A1AFF, 0x06000E, 0xC090FF);
            case YANG -> new Palette(0xFFFFF4, 0xFFD000, 0x5A3C00, 0xFFF4A0);
            case YIN_YANG -> new Palette(0xFFFFFF, 0xA890FF, 0x0C0C1A, 0xFFE060);
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

    /** A blinding flash: the moment of impact, with a halo in the accent colour. */
    public static void flash(Level level, Vec3 at, Palette palette, float size) {
        burst(level, glow(palette.core(), palette.mid(), size * 1.2f, size * 0.3f, 5), at, 1, 0, 0);
        burst(level, glow(palette.mid(), palette.edge(), size * 0.8f, size * 1.4f, 9), at, 3, size * 0.12, 0);
        burst(level, glow(palette.accent(), palette.mid(), size * 1.6f, size * 0.2f, 4), at, 1, 0, 0);
    }

    /** A flat ring spreading over the ground: the nature's colour, then a thinner accent ring outside it. */
    public static void shockwave(Level level, Vec3 at, Palette palette, float radius) {
        burst(level, ring(palette.mid(), radius, 9), at.add(0, 0.05, 0), 1, 0, 0);
        burst(level, ring(palette.accent(), radius * 1.35f, 12), at.add(0, 0.08, 0), 1, 0, 0);
    }

    /** Shakes the camera of every player within {@code radius} (stronger closer in). */
    public static void quake(Level level, Vec3 at, float strength, double radius) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (net.minecraft.server.level.ServerPlayer player : server.players()) {
            double distance = player.position().distanceTo(at);
            if (distance < radius) {
                float felt = strength * (float) (1 - distance / radius);
                com.noirm.network.NoirmNetwork.sendToPlayer(player, com.noirm.network.CombatClientPackets.Feedback.simple(
                        com.noirm.network.CombatClientPackets.Feedback.Kind.SHAKE, Math.min(1.5f, felt)));
            }
        }
    }

    /**
     * Every jutsu's opening: a flash at the hands and a ring at the feet; A and S rank jutsu also
     * raise a pillar of the nature's light. The "here it comes" moment.
     */
    public static void castFlourish(Level level, net.minecraft.world.entity.LivingEntity caster, Palette palette, String rank) {
        Vec3 hands = caster.getEyePosition().add(caster.getViewVector(1f).scale(0.6)).add(0, -0.45, 0);
        boolean big = "A".equals(rank) || "S".equals(rank);
        burst(level, glow(palette.core(), palette.mid(), big ? 0.9f : 0.5f, 0.1f, 6), hands, 1, 0, 0);
        radial(level, spark(palette.accent(), 0.14f, 8), hands, big ? 12 : 6, 0.3);
        burst(level, ring(palette.mid(), big ? 2.6f : 1.4f, 10), caster.position().add(0, 0.06, 0), 1, 0, 0);
        if (big) {
            for (int i = 0; i < 10; i++) {
                one(level, glow(palette.mid(), palette.accent(), 0.5f, 0.1f, 14), caster.position().add(
                        (level.random.nextDouble() - 0.5) * 0.8, 0.1, (level.random.nextDouble() - 0.5) * 0.8), new Vec3(0, 0.25 + level.random.nextDouble() * 0.2, 0));
            }
            if ("S".equals(rank)) {
                quake(level, caster.position(), 0.3f, 16);
            }
        }
    }

    /**
     * A cone of the nature's energy from {@code from} along {@code dir}: layered glows thickening
     * with distance, accent streaks racing outward, smoke at the edges (Air Palm, fire streams).
     */
    public static void cone(Level level, Vec3 from, Vec3 dir, double range, Palette palette, float density) {
        RandomSource random = level.random;
        for (double d = 0.8; d <= range; d += 0.7) {
            double width = 0.2 + d * 0.22;
            Vec3 at = from.add(dir.scale(d));
            int count = Math.max(1, Math.round(density * (2 + (float) d * 0.6f)));
            for (int i = 0; i < count; i++) {
                Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(width * 0.5);
                float size = (float) (0.35 + d * 0.08);
                int color = random.nextInt(4) == 0 ? palette.core() : palette.mid();
                one(level, glow(color, palette.edge(), size, size * 1.6f, 9 + random.nextInt(6)), at.add(jitter), dir.scale(0.25));
            }
            if (random.nextInt(2) == 0) {
                one(level, spark(palette.accent(), 0.16f, 7), at, dir.scale(0.8));
            }
            if (d > range * 0.6 && random.nextInt(2) == 0) {
                one(level, smoke(palette.edge(), (float) width * 0.8f, 24), at, dir.scale(0.06).add(0, 0.03, 0));
            }
        }
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
        flash(level, at, palette, r * 1.6f);
        radial(level, glow(palette.core(), palette.mid(), r * 0.6f, r * 0.25f, 10), at, (int) (10 + r * 5), r * 0.2);
        radial(level, glow(palette.mid(), palette.edge(), r * 0.5f, r * 0.9f, 18), at, (int) (10 + r * 5), r * 0.14);
        radial(level, glow(palette.accent(), palette.mid(), r * 0.3f, r * 0.1f, 8), at, (int) (6 + r * 3), r * 0.28);
        shockwave(level, at, palette, r * 1.8f);
        // A column of fire/energy punching upward: the mushroom of a big blast.
        for (int i = 0; i < 6 + (int) r * 2; i++) {
            one(level, glow(palette.mid(), palette.edge(), r * 0.35f, r * 0.6f, 20), at.add((level.random.nextDouble() - 0.5) * r * 0.4, 0,
                    (level.random.nextDouble() - 0.5) * r * 0.4), new Vec3(0, 0.12 + 0.08 * r * level.random.nextDouble(), 0));
        }
        radial(level, spark(palette.accent(), 0.28f, 16), at, (int) (14 + r * 8), r * 0.4);
        radial(level, shard(palette.edge(), 0.22f, 34), at, (int) (5 + r * 3), r * 0.28);
        burst(level, new VfxOptions(VfxKind.SOOT, palette.edge(), 0x2A2A2E, r * 0.4f, r * 0.9f, 60, -0.004f, 0.92f), at.add(0, r * 0.3, 0), (int) (6 + r * 2), r * 0.45, 0.03);
        burst(level, new VfxOptions(VfxKind.GLOW, palette.accent(), palette.edge(), 0.1f, 0.02f, 45, -0.01f, 0.95f), at, (int) (8 + r * 4), r * 0.55, 0.06);
        if (r >= 2.5f) {
            quake(level, at, Math.min(1.2f, r * 0.15f), r * 6);
        }
    }

    /** A ball of energy for one tick of a projectile's flight: hot core, coloured shell, a tail. */
    public static void orb(Level level, Vec3 at, Vec3 motion, Palette palette, float size) {
        burst(level, glow(palette.core(), palette.mid(), size * 0.9f, size * 0.45f, 3), at, 1, 0, 0);
        burst(level, glow(palette.mid(), palette.edge(), size * 1.0f, size * 0.25f, 9), at, 2, size * 0.25, 0.005);
        burst(level, glow(palette.accent(), palette.mid(), size * 1.3f, size * 0.6f, 2), at, 1, 0, 0);
        // Two ribbons spiralling around the ball as it flies.
        double spin = level.getGameTime() * 0.9;
        Vec3 dir = motion.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : motion.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0)).lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        for (int k = 0; k < 2; k++) {
            double a = spin + k * Math.PI;
            Vec3 offset = side.scale(Math.cos(a) * size * 0.7).add(up.scale(Math.sin(a) * size * 0.7));
            one(level, glow(palette.accent(), palette.edge(), size * 0.25f, 0.02f, 10), at.add(offset), Vec3.ZERO);
        }
        Vec3 behind = at.subtract(motion.scale(0.5));
        one(level, smoke(palette.edge(), size * 0.5f, 18), behind, motion.scale(-0.05));
        if (level.random.nextInt(3) == 0) {
            one(level, spark(palette.mid(), 0.12f, 10), at, motion.scale(-0.2).add(level.random.nextGaussian() * 0.05, 0.05, level.random.nextGaussian() * 0.05));
        }
    }

    /**
     * A helix rising around a body: binds wrapping their target, genjutsu spirals, power-ups.
     * Two strands, the nature's colour and the accent, winding {@code turns} times.
     */
    public static void spiral(Level level, Vec3 feet, Palette palette, float radius, float height, float turns) {
        int steps = Math.max(12, (int) (turns * 16));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = t * turns * Math.PI * 2;
            for (int strand = 0; strand < 2; strand++) {
                double angle = a + strand * Math.PI;
                Vec3 at = feet.add(Math.cos(angle) * radius, t * height, Math.sin(angle) * radius);
                one(level, glow(strand == 0 ? palette.mid() : palette.accent(), palette.edge(), 0.22f, 0.05f, 10 + (int) (t * 8)), at,
                        new Vec3(-Math.sin(angle) * 0.03, 0.02, Math.cos(angle) * 0.03));
            }
        }
    }

    /** A blur from {@code from} to {@code to}: someone moving too fast to see (Body Flicker). */
    public static void streak(Level level, Vec3 from, Vec3 to, Palette palette) {
        double length = from.distanceTo(to);
        Vec3 dir = length < 1e-3 ? Vec3.ZERO : to.subtract(from).normalize();
        for (double d = 0; d <= length; d += 0.4) {
            for (double y = 0.2; y < 1.8; y += 0.4) {
                one(level, glow(palette.core(), palette.mid(), 0.3f, 0.02f, 6), from.add(dir.scale(d)).add(0, y, 0), dir.scale(0.05));
            }
        }
        burst(level, ring(palette.accent(), 1.2f, 8), from.add(0, 0.06, 0), 1, 0, 0);
        burst(level, ring(palette.accent(), 1.2f, 8), to.add(0, 0.06, 0), 1, 0, 0);
    }

    /** A shimmering aura around a body (cloaks, armor). */
    public static void aura(Level level, Vec3 feet, float width, float height, Palette palette, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 at = feet.add((random.nextDouble() - 0.5) * width * 1.4, random.nextDouble() * height, (random.nextDouble() - 0.5) * width * 1.4);
            boolean pop = random.nextInt(4) == 0;
            one(level, new VfxOptions(VfxKind.GLOW, pop ? palette.accent() : palette.mid(), palette.edge(), pop ? 0.18f : 0.28f, 0.05f, 12,
                    -0.02f, 0.9f), at, new Vec3(0, pop ? 0.08 : 0.04, 0));
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

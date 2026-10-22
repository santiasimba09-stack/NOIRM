package com.noirm.vfx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fire release, the way the owner's references draw it (docs/ART_STYLE.md, "Jutsu effects"):
 * big layered <b>cellular</b> flame masses (yellow-white veins between orange cells, deep red
 * edges, a dark smoky rim), tongues curling up off them, ash and embers drifting, and blasts that
 * throw dust and rock.
 *
 * <p>The flame sprites carry their own colours ({@code tools/vfxgen/gen_particles.py}); a tint of
 * white keeps them as drawn, and each cell darkens toward smoke as it dies. Works on both sides
 * like {@link Vfx}: projectile trails call it on the client, impacts on the server.
 *
 * <p>Budget: the options below are made once; trails spawn a handful of particles a tick, and the
 * client thins them out with distance ({@code VfxParticle}).
 */
public final class FireVfx {
    /** A flame cell's colour as it dies: the fire cools to a dark smoky red. */
    private static final int COOLED = 0x6A2412;
    private static final int EMBER = 0xFFB030;
    private static final int SMOKE_DARK = 0x1E120E;
    private static final int ASH_GREY = 0x5A5450;
    private static final int DUST = 0x8A7A62;
    private static final int GLOW_HOT = 0xFFF2C0;
    private static final int GLOW_ORANGE = 0xFF6A10;

    private FireVfx() {}

    /** Just above the first solid ground at or below {@code at} (up to {@code depth} blocks down), for light pooling on the floor. */
    public static Vec3 floorBelow(Level level, Vec3 at, int depth) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(at.x), Math.floor(at.y + 0.01), Math.floor(at.z));
        for (int i = 0; i <= depth; i++) {
            BlockState below = level.getBlockState(pos.below());
            if (!below.getCollisionShape(level, pos.below()).isEmpty()) {
                return new Vec3(at.x, pos.getY() + 0.03, at.z);
            }
            pos.move(0, -1, 0);
        }
        return new Vec3(at.x, Math.floor(at.y + 0.01) + 0.03, at.z);
    }

    // ---------------------------------------------------------------- building blocks

    /** A cellular flame mass of {@code size} that lives {@code life} ticks and drifts with {@code gravity} (negative rises). */
    public static VfxOptions cell(float size, float endSize, int life, float gravity) {
        return new VfxOptions(VfxKind.FLAME, 0xFFFFFF, COOLED, size, endSize, life, gravity, 0.92f);
    }

    /** A cell that keeps its speed exactly (drag 1): it travels with a projectile it was given the velocity of. */
    public static VfxOptions riding(float size, float endSize, int life) {
        return new VfxOptions(VfxKind.FLAME, 0xFFFFFF, 0xFFD8B0, size, endSize, life, 0f, 1f);
    }

    /** A flame tongue licking up. */
    public static VfxOptions tongue(float size, int life) {
        return new VfxOptions(VfxKind.TONGUE, 0xFFFFFF, COOLED, size, size * 0.5f, life, -0.03f, 0.9f);
    }

    public static VfxOptions ember(int life) {
        return new VfxOptions(VfxKind.SPARK, 0xFFF0B0, EMBER, 0.09f, 0.03f, life, -0.006f, 0.96f);
    }

    public static VfxOptions ash(int life) {
        return new VfxOptions(VfxKind.ASH, ASH_GREY, 0x2A2624, 0.07f, 0.05f, life, 0.004f, 0.95f);
    }

    public static VfxOptions darkSmoke(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, SMOKE_DARK, 0x4A4440, size, size * 2.0f, life, -0.006f, 0.93f);
    }

    /** A soft hot light behind the cells, so the mass glows. */
    public static VfxOptions heat(float size, int life) {
        return new VfxOptions(VfxKind.GLOW, GLOW_HOT, GLOW_ORANGE, size, size * 0.6f, life, 0f, 0.9f);
    }

    // ---------------------------------------------------------------- shapes

    /**
     * One tick of the Great Fireball: a bubbling sphere of flame cells that ride along with it
     * (each lives a few ticks and is reborn elsewhere, so the surface churns), a hot glow inside, tongues
     * licking off the back and a smoky trail.
     */
    public static void fireball(Level level, Vec3 at, Vec3 motion, float radius) {
        RandomSource random = level.random;
        Vfx.one(level, heat(radius * 1.5f, 3), at, motion);
        int cells = 3 + Math.round(radius * 2);
        for (int i = 0; i < cells; i++) {
            double ox = random.nextGaussian(), oy = random.nextGaussian(), oz = random.nextGaussian();
            double len = Math.sqrt(ox * ox + oy * oy + oz * oz) + 1e-6;
            double r = radius * (0.25 + 0.45 * random.nextDouble());
            float s = radius * (0.7f + 0.5f * random.nextFloat());
            Vfx.one(level, riding(s, s * 1.15f, 3 + random.nextInt(3)), at.add(ox / len * r, oy / len * r, oz / len * r), motion);
        }
        Vec3 back = motion.lengthSqr() < 1e-6 ? at : at.subtract(motion.normalize().scale(radius * 0.8));
        if (random.nextInt(2) == 0) {
            Vfx.one(level, tongue(radius * 0.9f, 6), back.add(random.nextGaussian() * radius * 0.3, radius * 0.3, random.nextGaussian() * radius * 0.3),
                    motion.scale(0.5).add(0, 0.05, 0));
        }
        Vfx.one(level, cell(radius * 0.7f, radius * 0.3f, 8, -0.01f), back, motion.scale(0.15));
        Vfx.one(level, darkSmoke(radius * 0.6f, 22), back, motion.scale(0.05).add(0, 0.02, 0));
        if (random.nextInt(2) == 0) {
            Vfx.one(level, ember(18), back, new Vec3(random.nextGaussian() * 0.08, 0.05, random.nextGaussian() * 0.08));
        }
    }

    /** One tick of a Phoenix Flower bullet: a small flame head and a long tapering tail left behind it. */
    public static void bullet(Level level, Vec3 at, Vec3 motion, float size) {
        RandomSource random = level.random;
        Vfx.one(level, riding(size * 0.9f, size * 0.8f, 2), at, motion);
        Vfx.one(level, heat(size * 1.1f, 2), at, motion);
        // The tail: a thin streak of small cells left where the head was, shrinking as they age.
        Vec3 step = motion.scale(0.5);
        for (int i = 0; i < 2; i++) {
            Vec3 p = at.subtract(step.scale(i));
            Vfx.one(level, cell(size * (0.6f - 0.15f * i), 0.02f, 6 + random.nextInt(3), -0.01f), p, motion.scale(0.05));
        }
        if (random.nextInt(3) == 0) {
            Vfx.one(level, ember(14), at, new Vec3(random.nextGaussian() * 0.05, 0.04, random.nextGaussian() * 0.05));
        }
    }

    /**
     * One tick of a fire dragon: a readable head (skull, snout, an open lower jaw, two white-hot
     * eyes, swept-back horns) riding at the front, and a serpentine body of flame left behind it
     * that sways side to side and tapers to a tail.
     */
    public static void dragon(Level level, Vec3 at, Vec3 motion, float size, int age) {
        RandomSource random = level.random;
        Vec3 dir = motion.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : motion.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float s = size;
        // Head: skull, snout, lower jaw hanging open below it.
        Vfx.one(level, riding(s * 1.5f, s * 1.5f, 2), at, motion);
        Vfx.one(level, riding(s * 1.0f, s * 1.0f, 2), at.add(dir.scale(s * 0.75)).add(up.scale(s * 0.12)), motion);
        Vfx.one(level, riding(s * 0.75f, s * 0.75f, 2), at.add(dir.scale(s * 0.65)).add(up.scale(-s * 0.55)), motion);
        Vfx.one(level, heat(s * 1.6f, 2), at, motion);
        // Eyes: two white-hot points above the snout.
        for (int k = -1; k <= 1; k += 2) {
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, 0xFFFFFF, 0xFFF080, s * 0.32f, s * 0.28f, 2, 0f, 1f),
                    at.add(dir.scale(s * 0.45)).add(up.scale(s * 0.38)).add(side.scale(k * s * 0.32)), motion);
        }
        // Horns / mane: tongues swept back off the skull.
        if (age % 2 == 0) {
            for (int k = -1; k <= 1; k += 2) {
                Vfx.one(level, tongue(s * 1.1f, 5), at.add(dir.scale(-s * 0.4)).add(up.scale(s * 0.6)).add(side.scale(k * s * 0.4)),
                        motion.scale(0.6));
            }
        }
        // Body: a sway left behind, shrinking toward the tail.
        double sway = Math.sin(age * 0.45) * s * 0.9;
        Vec3 body = at.subtract(dir.scale(s * 1.1)).add(side.scale(sway)).add(up.scale(Math.cos(age * 0.3) * s * 0.3));
        Vfx.one(level, cell(s * 1.25f, s * 0.15f, 14, 0f), body, Vec3.ZERO);
        Vfx.one(level, cell(s * 0.9f, s * 0.1f, 12, 0f), body.subtract(dir.scale(s * 0.5)).add(side.scale(-sway * 0.2)), Vec3.ZERO);
        if (random.nextInt(3) == 0) {
            Vfx.one(level, darkSmoke(s * 0.6f, 26), body, new Vec3(0, 0.02, 0));
        }
        if (random.nextInt(2) == 0) {
            Vfx.one(level, ember(20), body, new Vec3(random.nextGaussian() * 0.06, 0.05, random.nextGaussian() * 0.06));
        }
    }

    /** One tick of a Flame Bomb: a glob of burning oil, dripping flame. */
    public static void oilGlob(Level level, Vec3 at, Vec3 motion, float size) {
        RandomSource random = level.random;
        Vfx.one(level, riding(size * 1.4f, size * 1.2f, 2), at, motion);
        Vfx.one(level, new VfxOptions(VfxKind.GLOW, 0xFFE070, 0xC03000, size * 1.3f, size, 2, 0f, 1f), at, motion);
        Vfx.one(level, cell(size * 0.6f, 0.05f, 10, 0.03f), at, motion.scale(0.1)); // drips fall
        if (random.nextInt(2) == 0) {
            Vfx.one(level, darkSmoke(size * 0.5f, 20), at, Vec3.ZERO);
        }
    }

    /**
     * A fire blast of {@code radius}: a white-hot flash, a ball of flame cells bursting outward,
     * tongues thrown up, a shockwave ring with a skirt of dust, rock and splinters flying,
     * a column of dark smoke, embers and ash drifting down after.
     */
    public static void blast(Level level, Vec3 at, float radius) {
        float r = Math.max(0.8f, radius);
        RandomSource random = level.random;
        Vfx.burst(level, Vfx.glow(0xFFFFFF, 0xFFD060, r * 2.2f, r * 0.6f, 4), at, 1, 0, 0);
        Vfx.burst(level, heat(r * 1.8f, 10), at, 2, r * 0.15, 0);
        Vfx.burst(level, cell(r * 1.1f, r * 1.5f, 12, -0.01f), at, 2, r * 0.15, 0.01);
        int cells = (int) (8 + r * 4);
        for (int i = 0; i < cells; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.7, random.nextGaussian()).normalize();
            float s = r * (0.5f + 0.5f * random.nextFloat());
            Vfx.one(level, cell(s, s * 1.4f, 12 + random.nextInt(8), -0.008f), at.add(dir.scale(r * 0.3)), dir.scale(r * (0.08 + 0.06 * random.nextDouble())));
        }
        for (int i = 0; i < 4 + (int) r; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), 0.4, random.nextGaussian()).normalize();
            Vfx.one(level, tongue(r * 0.7f, 10), at.add(dir.scale(r * 0.5)), dir.scale(0.15).add(0, 0.12, 0));
        }
        Vfx.shockwave(level, at, Vfx.palette(com.noirm.clan.Nature.FIRE), r * 1.8f);
        // Firelight thrown on the ground around it.
        Vfx.burst(level, new VfxOptions(VfxKind.GROUND_GLOW, 0x8A3A0C, 0x200800, r * 2.2f, r * 2.6f, 16, 0f, 1f),
                floorBelow(level, at, 4), 1, 0, 0);
        groundDebris(level, at, r);
        // A mushroom of dark smoke rising.
        Vfx.burst(level, darkSmoke(r * 0.6f, 60), at.add(0, r * 0.5, 0), (int) (6 + r * 2), r * 0.45, 0.03);
        Vfx.burst(level, ember(50), at, (int) (10 + r * 4), r * 0.5, 0.12);
        Vfx.burst(level, ash(70), at.add(0, r * 0.8, 0), (int) (8 + r * 3), r * 0.7, 0.03);
        if (r >= 2.5f) {
            Vfx.quake(level, at, Math.min(1.2f, r * 0.15f), r * 6);
        }
    }

    /** Dust skirting out along the ground and chunks of the ground itself flying (a shockwave's debris). */
    public static void groundDebris(Level level, Vec3 at, float r) {
        RandomSource random = level.random;
        for (int i = 0; i < 8 + (int) (r * 2); i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.one(level, new VfxOptions(VfxKind.SOOT, DUST, 0x5A5040, r * 0.35f, r * 0.9f, 30 + random.nextInt(15), -0.002f, 0.88f),
                    at.add(out.scale(r * 0.4)).add(0, 0.2, 0), out.scale(0.25 + 0.1 * r));
        }
        Vfx.radial(level, Vfx.shard(0x4A3C30, 0.18f, 30), at.add(0, 0.3, 0), (int) (6 + r * 3), 0.35 + r * 0.06);
        if (level instanceof ServerLevel server) {
            BlockPos below = BlockPos.containing(at).below();
            BlockState ground = server.getBlockState(below);
            if (ground.isAir()) {
                ground = server.getBlockState(below.below());
            }
            if (!ground.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.3, at.z, (int) (10 + r * 6),
                        r * 0.4, 0.2, r * 0.4, 0.3);
            }
        }
    }

    /**
     * One tick of fire pouring out along {@code dir} (Fire Stream): flame cells flying out and
     * swelling, tongues off the sides, smoke and ash at the far end.
     */
    public static void stream(Level level, Vec3 from, Vec3 dir, double range) {
        RandomSource random = level.random;
        for (double d = 0.6; d <= range; d += 1.1) {
            double width = 0.15 + d * 0.2;
            Vec3 at = from.add(dir.scale(d)).add(random.nextGaussian() * width * 0.4, random.nextGaussian() * width * 0.3, random.nextGaussian() * width * 0.4);
            float s = (float) (0.35 + d * 0.16);
            Vfx.one(level, cell(s, s * 1.5f, 6 + random.nextInt(4), -0.01f), at, dir.scale(0.35));
            if (random.nextInt(3) == 0) {
                Vfx.one(level, tongue(s * 0.9f, 7), at, dir.scale(0.2).add(0, 0.06, 0));
            }
        }
        Vfx.one(level, heat(0.6f, 3), from.add(dir.scale(0.6)), dir.scale(0.2));
        Vec3 end = from.add(dir.scale(range));
        if (random.nextInt(2) == 0) {
            Vfx.one(level, darkSmoke(0.8f, 30), end, dir.scale(0.05).add(0, 0.04, 0));
        }
        Vfx.one(level, ember(20), end, dir.scale(0.1).add(random.nextGaussian() * 0.06, 0.06, random.nextGaussian() * 0.06));
        if (random.nextInt(2) == 0) {
            Vfx.one(level, ash(50), end.add(0, 0.5, 0), new Vec3(random.nextGaussian() * 0.04, 0.03, random.nextGaussian() * 0.04));
        }
    }

    /**
     * One slice of a wall of fire {@code height} tall at {@code base} (Great Fire Annihilation,
     * Majestic Destroyer Flame): stacked flame cells, tongues licking off the top, smoke above.
     * Sent as a few batched packets, not one per particle.
     */
    public static void wallSlice(Level level, Vec3 base, double height, Vec3 dir, float thickness) {
        RandomSource random = level.random;
        float s = 0.8f + thickness * 0.4f;
        Vfx.burst(level, cell(s, s * 1.6f, 10, -0.02f), base.add(0, height * 0.35, 0), 2, thickness * 0.4, 0.04);
        Vfx.one(level, cell(s * 1.2f, s * 1.2f, 8, -0.03f), base.add(0, height * (0.55 + 0.35 * random.nextDouble()), 0), dir.scale(0.12).add(0, 0.05, 0));
        Vfx.one(level, tongue(s * 1.3f, 8), base.add(0, height, 0), dir.scale(0.1).add(0, 0.12, 0));
        if (random.nextInt(5) == 0) {
            Vfx.one(level, darkSmoke(s, 26), base.add(0, height + 0.6, 0), dir.scale(0.08).add(0, 0.05, 0));
        }
        if (random.nextInt(3) == 0) {
            Vfx.one(level, ember(26), base.add(0, height * random.nextDouble(), 0), dir.scale(0.2).add(0, 0.1, 0));
        }
    }

    /** One tick of burning ground (oil still on fire): tongues and small cells sprouting from it. */
    public static void burningGround(Level level, Vec3 center, float radius) {
        RandomSource random = level.random;
        for (int i = 0; i < 2 + (int) radius; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * radius;
            Vec3 at = center.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
            if (random.nextInt(3) == 0) {
                Vfx.one(level, cell(0.6f, 0.2f, 10, -0.03f), at.add(0, 0.2, 0), Vec3.ZERO);
            } else {
                Vfx.one(level, tongue(0.7f + random.nextFloat() * 0.4f, 9), at.add(0, 0.35, 0), new Vec3(0, 0.04, 0));
            }
        }
        if (random.nextInt(3) == 0) {
            Vfx.one(level, darkSmoke(0.6f, 30), center.add(random.nextGaussian() * radius * 0.5, 0.8, random.nextGaussian() * radius * 0.5), new Vec3(0, 0.04, 0));
        }
    }

    /** One tick of an ash cloud (Burning Ash): thick grey smoke, flakes fluttering, a few live embers in it. */
    public static void ashCloud(Level level, Vec3 center, float radius) {
        Vfx.burst(level, new VfxOptions(VfxKind.SOOT, 0x4A4644, 0x2A2826, radius * 0.6f, radius * 0.9f, 26, -0.002f, 0.9f), center, 3, radius * 0.45, 0.02);
        Vfx.burst(level, ash(40), center, 6, radius * 0.55, 0.03);
        if (level.random.nextInt(2) == 0) {
            Vfx.burst(level, ember(16), center, 2, radius * 0.5, 0.02);
        }
    }

    /** The ash cloud catching: flame racing through the whole volume at once. */
    public static void ashIgnite(Level level, Vec3 center, float radius) {
        RandomSource random = level.random;
        Vfx.burst(level, Vfx.glow(0xFFFFFF, 0xFFE070, radius * 2.4f, radius, 4), center, 1, 0, 0);
        Vfx.burst(level, cell(radius * 1.2f, radius * 1.6f, 14, -0.01f), center, 3, radius * 0.25, 0.02);
        int cells = (int) (10 + radius * 5);
        for (int i = 0; i < cells; i++) {
            Vec3 off = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).scale(radius * 0.45);
            float s = radius * (0.35f + 0.35f * random.nextFloat());
            Vfx.one(level, cell(s, s * 1.5f, 14 + random.nextInt(10), -0.01f), center.add(off), off.normalize().scale(0.08));
        }
        blast(level, center, radius * 0.8f);
    }
}

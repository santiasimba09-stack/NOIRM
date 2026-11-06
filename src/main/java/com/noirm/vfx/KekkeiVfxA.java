package com.noirm.vfx;

import com.noirm.clan.Nature;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The look of five combined natures (the chakra wave, share A). Each has its own palette and its
 * own sprite, so they never read as each other:
 * <ul>
 *   <li><b>Ice</b>: crystalline, cold and glinting. Faceted {@link VfxKind#CRYSTAL} clusters, white
 *       glints, pale frost mist and frost light on the ground.</li>
 *   <li><b>Lava</b>: glowing cellular {@link VfxKind#MAGMA} plates with black crust, a deep orange
 *       heat glow, black smoke and dripping lava.</li>
 *   <li><b>Boil</b>: thick white-grey {@link VfxKind#STEAM} billows that roll and rise, with a
 *       faint hot-pink heat shimmer under them.</li>
 *   <li><b>Storm</b>: dark blue-black thunderclouds (soot) with blue-black lasers: a white-hot core
 *       inside a deep blue sheath and black flecks, crackling with bolts.</li>
 *   <li><b>Explosion</b>: a white flash, then fire, then smoke rings rolling out along the ground
 *       and up the column. Clay is pale and chalky.</li>
 * </ul>
 * Every big effect follows the house rule: a strong first frame, then a slower decay. Works on
 * both sides like {@link Vfx} (projectile trails run on the client, impacts on the server).
 */
public final class KekkeiVfxA {
    // Ice
    public static final int ICE_WHITE = 0xF4FDFF;
    public static final int ICE_PALE = 0xBDEEFF;
    public static final int ICE_BLUE = 0x4FC4F0;
    public static final int ICE_DEEP = 0x0E3A80;
    // Lava
    public static final int LAVA_HOT = 0xFFE890;
    public static final int LAVA_ORANGE = 0xFF5A08;
    public static final int LAVA_COOLED = 0x2A0E08;
    public static final int LAVA_SMOKE = 0x140C0A;
    // Quicklime (the Lava user's cement)
    public static final int LIME = 0xE6E2D6;
    public static final int LIME_SHADE = 0x8C887E;
    // Boil
    public static final int STEAM_WHITE = 0xF6F6F4;
    public static final int STEAM_GREY = 0xA8A4A4;
    public static final int STEAM_HEAT = 0xFFB8A0;
    public static final int ACID = 0xE8F0B0;
    // Storm
    public static final int STORM_CORE = 0xF2FAFF;
    public static final int STORM_BLUE = 0x2A6CFF;
    public static final int STORM_DEEP = 0x0A1A6A;
    public static final int STORM_BLACK = 0x060818;
    public static final int CLOUD_DARK = 0x1A1E2E;
    // Explosion
    public static final int BLAST_WHITE = 0xFFFFF4;
    public static final int BLAST_SMOKE = 0x3A3634;
    public static final int CLAY = 0xEDE4D2;
    public static final int CLAY_SHADE = 0xB0A48C;

    private KekkeiVfxA() {}

    // ================================================================ building blocks

    /**
     * The biggest single sprite we draw. Larger ones look blocky, and the client drops a sprite
     * that would swallow the camera, so a self-centred blast would vanish: big flashes are built
     * from several of these instead ({@link #flashCloud}).
     */
    public static final float MAX_SPRITE = 3.5f;

    public static float big(float size) {
        return Math.min(size, MAX_SPRITE);
    }

    /** A flash of light filling a sphere of {@code r}: several capped glows instead of one huge one. */
    public static void flashCloud(Level level, Vec3 at, int color, int end, float r, int life) {
        Vfx.burst(level, Vfx.glow(color, end, big(r * 1.2f), big(r * 0.4f), life), at, 1, 0, 0);
        if (r > MAX_SPRITE * 0.6f) {
            int n = (int) Math.min(18, 4 + r * 1.5f);
            Vfx.radial(level, Vfx.glow(color, end, MAX_SPRITE, MAX_SPRITE * 0.4f, life + 2), at, n, r * 0.12);
        }
    }


    public static VfxOptions crystal(float size, float endSize, int life, float gravity) {
        return new VfxOptions(VfxKind.CRYSTAL, 0xFFFFFF, 0xCFEFFF, size, endSize, life, gravity, 0.94f);
    }

    public static VfxOptions magma(float size, float endSize, int life, float gravity) {
        return new VfxOptions(VfxKind.MAGMA, 0xFFFFFF, 0x7A3A24, size, endSize, life, gravity, 0.93f);
    }

    public static VfxOptions steam(int color, float size, int life) {
        return new VfxOptions(VfxKind.STEAM, color, STEAM_GREY, size, size * 2.0f, life, -0.012f, 0.9f);
    }

    public static VfxOptions frost(float size, int life) {
        return new VfxOptions(VfxKind.SMOKE, ICE_PALE, 0xE8F8FF, size, size * 2.2f, life, -0.002f, 0.9f);
    }

    public static VfxOptions glint(float size, int life) {
        return new VfxOptions(VfxKind.SPARK, 0xFFFFFF, ICE_PALE, size, size * 0.3f, life, 0.01f, 0.9f);
    }

    public static VfxOptions heat(float size, int life) {
        return new VfxOptions(VfxKind.GLOW, LAVA_HOT, LAVA_ORANGE, size, size * 0.6f, life, 0f, 0.9f);
    }

    public static VfxOptions blackSmoke(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, LAVA_SMOKE, 0x3A302C, size, size * 2f, life, -0.008f, 0.92f);
    }

    public static VfxOptions stormCloud(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, CLOUD_DARK, STORM_BLACK, size, size * 1.3f, life, 0f, 0.9f);
    }

    public static VfxOptions clay(float size, int life) {
        return new VfxOptions(VfxKind.STEAM, CLAY, CLAY_SHADE, size, size, life, 0f, 1f);
    }

    /** Light lying on the ground. */
    public static void groundLight(Level level, Vec3 at, int color, int end, float radius, int life) {
        Vfx.burst(level, new VfxOptions(VfxKind.GROUND_GLOW, color, end, radius, radius * 1.2f, life, 0f, 1f), FireVfx.floorBelow(level, at, 4), 1, 0, 0);
    }

    /** A projectile's trail in one of these natures; false = not ours (the caller draws its usual orb). */
    public static boolean trail(Level level, Nature nature, String look, Vec3 at, Vec3 motion, float size, int age) {
        if (nature == null) {
            return false;
        }
        switch (nature) {
            case ICE -> {
                if ("dragon".equals(look)) {
                    iceDragon(level, at, motion, size * 0.75f, age);
                } else if ("needle".equals(look)) {
                    iceNeedle(level, at, motion, size);
                } else {
                    iceShard(level, at, motion, size);
                }
            }
            case LAVA -> lavaGlob(level, at, motion, size, "bullet".equals(look));
            case BOIL -> {
                Vfx.one(level, steam(STEAM_WHITE, size * 1.1f, 6), at, motion);
                Vfx.one(level, steam(STEAM_WHITE, size * 0.6f, 24), at, motion.scale(-0.05));
            }
            case STORM -> {
                Vfx.one(level, Vfx.glow(STORM_CORE, STORM_BLUE, size * 0.7f, size * 0.3f, 3), at, motion);
                Vfx.one(level, stormCloud(size * 0.8f, 14), at.subtract(motion.scale(0.5)), Vec3.ZERO);
            }
            case EXPLOSION -> clayBird(level, at, motion, Math.max(0.35f, size * 0.8f), age);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A projectile's impact in one of these natures; false = not ours. */
    public static boolean impact(ServerLevel level, Nature nature, Vec3 at, float radius) {
        if (nature == null) {
            return false;
        }
        switch (nature) {
            case ICE -> iceShatter(level, at, Math.max(0.7f, radius));
            case LAVA -> lavaSplash(level, at, Math.max(0.7f, radius));
            case BOIL -> steamBurst(level, at, Math.max(0.8f, radius));
            case EXPLOSION -> blast(level, at, Math.max(0.9f, radius));
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================ ice

    /** One tick of an ice shard in flight: a crystal riding with it, a glint, a thin streak of frost. */
    public static void iceShard(Level level, Vec3 at, Vec3 motion, float size) {
        RandomSource random = level.random;
        Vfx.one(level, new VfxOptions(VfxKind.CRYSTAL, 0xFFFFFF, 0xFFFFFF, size * 1.3f, size * 1.2f, 2, 0f, 1f), at, motion);
        Vfx.one(level, Vfx.glow(ICE_WHITE, ICE_BLUE, size * 0.7f, size * 0.2f, 3), at, motion);
        Vfx.one(level, frost(size * 0.5f, 16), at.subtract(motion.scale(0.6)), motion.scale(0.02));
        if (random.nextInt(3) == 0) {
            Vfx.one(level, glint(0.12f, 6), at, motion.scale(-0.1));
        }
    }

    /** One tick of an ice needle or spear: a long white-blue streak, crystals peeling off its tail. */
    public static void iceNeedle(Level level, Vec3 at, Vec3 motion, float size) {
        RandomSource random = level.random;
        Vec3 dir = motion.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : motion.normalize();
        float w = Math.max(0.1f, size * 0.35f);
        for (int i = 0; i < 4; i++) {
            Vfx.one(level, Vfx.glow(i == 0 ? 0xFFFFFF : ICE_PALE, ICE_DEEP, w * (1.3f - i * 0.2f), w * 0.3f, 3), at.subtract(dir.scale(i * 0.28)), motion);
        }
        Vfx.one(level, new VfxOptions(VfxKind.CRYSTAL, 0xFFFFFF, 0xFFFFFF, size * 0.9f, size * 0.9f, 2, 0f, 1f), at, motion);
        if (random.nextInt(2) == 0) {
            Vfx.one(level, crystal(size * 0.35f, 0.05f, 14, 0.03f), at.subtract(dir.scale(0.6)), motion.scale(0.05));
        }
        if (random.nextInt(3) == 0) {
            Vfx.one(level, frost(0.25f, 14), at.subtract(dir.scale(0.8)), Vec3.ZERO);
        }
    }

    /**
     * One tick of an ice dragon: a horned head of crystals with an open jaw and two cold white eyes,
     * a long body of crystal plates swaying behind, frost breath and glittering mist.
     */
    public static void iceDragon(Level level, Vec3 at, Vec3 motion, float s, int age) {
        RandomSource random = level.random;
        Vec3 dir = motion.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : motion.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        // Head: a big crystal skull, a snout, an open lower jaw, a cold glow inside.
        Vfx.one(level, new VfxOptions(VfxKind.CRYSTAL, 0xFFFFFF, 0xFFFFFF, s * 1.7f, s * 1.7f, 2, 0f, 1f), at, motion);
        Vfx.one(level, new VfxOptions(VfxKind.CRYSTAL, 0xFFFFFF, 0xFFFFFF, s * 1.1f, s * 1.1f, 2, 0f, 1f), at.add(dir.scale(s * 0.8)).add(up.scale(s * 0.1)), motion);
        Vfx.one(level, new VfxOptions(VfxKind.CRYSTAL, 0xE0F6FF, 0xE0F6FF, s * 0.9f, s * 0.9f, 2, 0f, 1f), at.add(dir.scale(s * 0.7)).add(up.scale(-s * 0.6)), motion);
        Vfx.one(level, Vfx.glow(ICE_PALE, ICE_DEEP, s * 1.8f, s * 1.5f, 2), at, motion);
        for (int k = -1; k <= 1; k += 2) {
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, 0xFFFFFF, 0xC8F4FF, s * 0.3f, s * 0.26f, 2, 0f, 1f),
                    at.add(dir.scale(s * 0.45)).add(up.scale(s * 0.4)).add(side.scale(k * s * 0.34)), motion);
            if (age % 2 == 0) {
                // Swept-back horns of ice.
                Vfx.one(level, new VfxOptions(VfxKind.CRYSTAL, 0xFFFFFF, 0xFFFFFF, s * 1.0f, s * 0.9f, 3, 0f, 1f),
                        at.add(dir.scale(-s * 0.5)).add(up.scale(s * 0.75)).add(side.scale(k * s * 0.45)), motion);
            }
        }
        // Body: crystal plates left behind on a sway, glittering and thinning toward the tail.
        double sway = Math.sin(age * 0.45) * s * 0.9;
        Vec3 body = at.subtract(dir.scale(s * 1.2)).add(side.scale(sway)).add(up.scale(Math.cos(age * 0.3) * s * 0.3));
        Vfx.one(level, crystal(s * 1.3f, s * 0.2f, 14, 0f), body, Vec3.ZERO);
        Vfx.one(level, crystal(s * 0.9f, s * 0.1f, 11, 0f), body.subtract(dir.scale(s * 0.55)).add(side.scale(-sway * 0.2)), Vec3.ZERO);
        Vfx.one(level, frost(s * 0.7f, 26), body, new Vec3(0, -0.01, 0));
        if (random.nextInt(2) == 0) {
            Vfx.one(level, glint(0.16f, 10), body.add(random.nextGaussian() * s * 0.4, random.nextGaussian() * s * 0.4, random.nextGaussian() * s * 0.4), Vec3.ZERO);
        }
    }

    /**
     * Ice shattering on impact: a white flash, crystals bursting outward and falling, a pale frost
     * ring on the ground, glints, then a cold mist settling.
     */
    public static void iceShatter(Level level, Vec3 at, float r) {
        RandomSource random = level.random;
        flashCloud(level, at, 0xFFFFFF, ICE_PALE, r * 1.5f, 4);
        Vfx.burst(level, Vfx.glow(ICE_PALE, ICE_DEEP, big(r * 1.3f), big(r * 2f), 10), at, 1, 0, 0);
        int shards = (int) (8 + r * 6);
        for (int i = 0; i < shards; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.8 + 0.2, random.nextGaussian()).normalize();
            float s = 0.25f + r * 0.18f * random.nextFloat();
            Vfx.one(level, crystal(s, s * 0.6f, 18 + random.nextInt(12), 0.05f), at.add(d.scale(r * 0.2)), d.scale(0.2 + r * 0.06));
        }
        Vfx.radial(level, glint(0.2f, 10), at, (int) (6 + r * 4), 0.4 + r * 0.1);
        Vfx.burst(level, Vfx.ring(ICE_PALE, r * 1.6f, 12), FireVfx.floorBelow(level, at, 3), 1, 0, 0);
        groundLight(level, at, 0x5AA8D8, 0x0A2040, r * 1.6f, 16);
        Vfx.burst(level, frost(r * 0.5f, 40), at, (int) (4 + r * 2), r * 0.4, 0.02);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, (int) (8 + r * 6), r * 0.5, r * 0.4, r * 0.5, 0.05);
        }
        if (r >= 2.5f) {
            Vfx.quake(level, at, Math.min(1f, r * 0.12f), r * 5);
        }
    }

    /** One tick of ground freezing over in a ring from {@code inner} to {@code outer}: frost light, crystals sprouting, mist. */
    public static void frostRing(Level level, Vec3 center, float inner, float outer) {
        RandomSource random = level.random;
        int count = 4 + (int) (outer * 2);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = inner + (outer - inner) * random.nextDouble();
            Vec3 at = center.add(Math.cos(a) * d, 0.15, Math.sin(a) * d);
            Vfx.one(level, crystal(0.4f + random.nextFloat() * 0.35f, 0.25f, 30 + random.nextInt(20), 0f), at.add(0, 0.2, 0), new Vec3(0, 0.01, 0));
            if (random.nextInt(2) == 0) {
                Vfx.one(level, frost(0.5f, 30), at, new Vec3(Math.cos(a) * 0.04, 0.01, Math.sin(a) * 0.04));
            }
        }
        Vfx.burst(level, Vfx.ring(ICE_PALE, outer, 10), FireVfx.floorBelow(level, center, 3), 1, 0, 0);
        Vfx.radial(level, glint(0.15f, 8), center.add(0, 0.3, 0), 3, outer * 0.12);
    }

    /**
     * One tick of an ice mirror: a tall pane of crystal, white glints running down its face. The
     * pane faces {@code normal} (toward the dome's centre).
     */
    public static void mirror(Level level, Vec3 base, Vec3 normal, float width, float height, boolean flash) {
        RandomSource random = level.random;
        Vec3 across = new Vec3(-normal.z, 0, normal.x).normalize();
        Vec3 face = base.add(normal.scale(0.55)); // just inside the ice, toward the trapped enemy
        // The pane's face: a cold shimmer, brightest toward its top.
        for (int i = 0; i < 4; i++) {
            double u = (random.nextDouble() - 0.5) * width;
            double v = random.nextDouble() * height;
            Vfx.one(level, Vfx.glow(ICE_PALE, ICE_DEEP, 0.5f + (float) (v / height) * 0.3f, 0.35f, 6), face.add(across.scale(u)).add(0, v, 0), Vec3.ZERO);
        }
        // The frame: crystals along both edges and the top.
        double edge = (random.nextBoolean() ? -0.5 : 0.5) * width;
        Vfx.one(level, crystal(0.55f, 0.45f, 8, 0f), face.add(across.scale(edge)).add(0, random.nextDouble() * height, 0), Vec3.ZERO);
        Vfx.one(level, crystal(0.6f, 0.5f, 8, 0f), face.add(across.scale((random.nextDouble() - 0.5) * width)).add(0, height, 0), Vec3.ZERO);
        // A glint sliding down the glass.
        Vfx.one(level, glint(0.28f, 7), face.add(across.scale((random.nextDouble() - 0.5) * width)).add(0, height * random.nextDouble(), 0), new Vec3(0, -0.08, 0));
        if (flash) {
            // A figure flashes across the mirror: a white silhouette streak.
            Vec3 mid = face.add(0, height * 0.5, 0);
            Vfx.one(level, Vfx.glow(0xFFFFFF, ICE_PALE, 2.2f, 0.6f, 6), mid, Vec3.ZERO);
            for (double y = 0.1; y < height * 0.9; y += 0.3) {
                double w = y > height * 0.7 ? 0.18 : 0.32; // a head on shoulders: a body, not a bar
                Vfx.one(level, Vfx.glow(0xFFFFFF, ICE_BLUE, (float) w * 2f, 0.05f, 7), face.add(0, y, 0), across.scale(0.12));
            }
            Vfx.radial(level, glint(0.25f, 8), mid, 6, 0.3);
        }
    }

    // ================================================================ lava

    /** One tick of a lava glob: a churning magma plate riding with it, heat glow, black smoke, drips. */
    public static void lavaGlob(Level level, Vec3 at, Vec3 motion, float size, boolean bullet) {
        RandomSource random = level.random;
        Vfx.one(level, new VfxOptions(VfxKind.MAGMA, 0xFFFFFF, 0xFFE0C0, size * 1.4f, size * 1.3f, 2, 0f, 1f), at, motion);
        Vfx.one(level, heat(size * 1.5f, 3), at, motion);
        if (bullet) {
            for (int i = 1; i <= 2; i++) {
                Vfx.one(level, magma(size * (0.8f - 0.2f * i), 0.05f, 6, 0f), at.subtract(motion.scale(i * 0.4)), motion.scale(0.05));
            }
        } else {
            Vfx.one(level, magma(size * 0.5f, 0.1f, 14, 0.04f), at, motion.scale(0.1)); // drips fall
        }
        if (random.nextInt(2) == 0) {
            Vfx.one(level, blackSmoke(size * 0.6f, 22), at.subtract(motion.scale(0.5)), new Vec3(0, 0.02, 0));
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.DRIPPING_LAVA, at.x, at.y - size * 0.3, at.z, 0, 0, 0);
        }
    }

    /**
     * Lava splashing on impact: a deep orange flash (never white), magma plates flung out and
     * falling, a pool of glow on the ground, a column of black smoke.
     */
    public static void lavaSplash(Level level, Vec3 at, float r) {
        RandomSource random = level.random;
        flashCloud(level, at, LAVA_HOT, LAVA_ORANGE, r * 1.6f, 5);
        Vfx.burst(level, magma(big(r * 1.2f), big(r * 0.8f), 16, 0.01f), at, 2, r * 0.15, 0.01);
        int plates = (int) (6 + r * 5);
        for (int i = 0; i < plates; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.9 + 0.3, random.nextGaussian()).normalize();
            float s = big(r * (0.3f + 0.3f * random.nextFloat()));
            Vfx.one(level, magma(s, s * 0.5f, 16 + random.nextInt(12), 0.06f), at.add(d.scale(r * 0.2)), d.scale(0.18 + r * 0.06));
        }
        groundLight(level, at, 0xC04008, 0x200400, r * 2.2f, 30);
        Vfx.burst(level, Vfx.ring(LAVA_ORANGE, r * 1.7f, 12), FireVfx.floorBelow(level, at, 3), 1, 0, 0);
        Vfx.burst(level, blackSmoke(big(r * 0.6f), 60), at.add(0, r * 0.5, 0), (int) (4 + r * 2), r * 0.4, 0.03);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, (int) (4 + r * 3), r * 0.4, 0.2, r * 0.4, 0);
        }
        if (r >= 2.5f) {
            Vfx.quake(level, at, Math.min(1.2f, r * 0.15f), r * 6);
        }
    }

    /** One tick of molten ground: magma plates bubbling up, heat light, smoke. */
    public static void moltenGround(Level level, Vec3 center, float radius) {
        RandomSource random = level.random;
        for (int i = 0; i < 1 + (int) (radius * 0.6f); i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * radius;
            Vec3 at = center.add(Math.cos(a) * d, 0.15, Math.sin(a) * d);
            Vfx.one(level, magma(0.6f + random.nextFloat() * 0.4f, 0.2f, 16, -0.005f), at, Vec3.ZERO);
        }
        if (random.nextInt(3) == 0) {
            Vfx.one(level, blackSmoke(0.7f, 40), center.add(random.nextGaussian() * radius * 0.4, 0.5, random.nextGaussian() * radius * 0.4), new Vec3(0, 0.05, 0));
        }
    }

    /** One tick of an erupting vent: a fountain of magma thrown up, a glowing throat, a pillar of black smoke. */
    public static void eruption(Level level, Vec3 vent, float power) {
        RandomSource random = level.random;
        Vfx.one(level, heat(1.6f * power, 4), vent.add(0, 0.6, 0), Vec3.ZERO);
        for (int i = 0; i < 3 + (int) (power * 2); i++) {
            Vec3 v = new Vec3(random.nextGaussian() * 0.12, 0.5 + random.nextDouble() * 0.5 * power, random.nextGaussian() * 0.12);
            float s = 0.35f + random.nextFloat() * 0.45f * power;
            Vfx.one(level, magma(s, s * 0.5f, 20 + random.nextInt(10), 0.07f), vent.add(0, 0.4, 0), v);
        }
        Vfx.one(level, blackSmoke(big(1.4f * power), 70), vent.add(0, 2 + random.nextDouble() * 2, 0), new Vec3(random.nextGaussian() * 0.03, 0.12, random.nextGaussian() * 0.03));
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LAVA, vent.x, vent.y + 0.5, vent.z, 3, 0.4, 0.3, 0.4, 0);
        }
    }

    /** Quicklime in flight: a grey-white glob of setting cement. */
    public static void limeGlob(Level level, Vec3 at, Vec3 motion, float size) {
        Vfx.one(level, new VfxOptions(VfxKind.STEAM, LIME, LIME, size * 1.2f, size * 1.1f, 2, 0f, 1f), at, motion);
        Vfx.one(level, new VfxOptions(VfxKind.SMOKE, 0xFFFFFF, LIME_SHADE, size * 0.6f, size * 1.4f, 14, 0.01f, 0.9f), at.subtract(motion.scale(0.5)), Vec3.ZERO);
        Vfx.one(level, Vfx.shard(LIME_SHADE, 0.12f, 14), at, motion.scale(0.1).add(0, -0.05, 0));
    }

    /** A body set in quicklime: grey cement climbing it, hardening chips, white dust. */
    public static void limeEncase(Level level, Entity body, float progress) {
        Vec3 feet = body.position();
        float w = body.getBbWidth();
        float h = body.getBbHeight() * Math.min(1f, 0.3f + progress);
        for (int i = 0; i < 6; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            Vec3 at = feet.add(Math.cos(a) * w * 0.55, level.random.nextDouble() * h, Math.sin(a) * w * 0.55);
            Vfx.one(level, new VfxOptions(VfxKind.STEAM, LIME, LIME_SHADE, 0.45f, 0.4f, 10, 0f, 0.9f), at, Vec3.ZERO);
        }
        Vfx.burst(level, Vfx.shard(LIME_SHADE, 0.14f, 20), feet.add(0, h * 0.5, 0), 2, w * 0.5, 0.05);
    }

    /** Lava armour: magma plates crawling over the body, heat haze and smoke rising off it. */
    public static void lavaArmor(Level level, Entity body, int tick) {
        RandomSource random = level.random;
        Vec3 feet = body.position();
        float w = body.getBbWidth();
        float h = body.getBbHeight();
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 at = feet.add(Math.cos(a) * w * 0.6, random.nextDouble() * h, Math.sin(a) * w * 0.6);
            Vfx.one(level, magma(0.45f + random.nextFloat() * 0.25f, 0.2f, 8, -0.01f), at, new Vec3(0, 0.02, 0));
        }
        Vfx.one(level, new VfxOptions(VfxKind.GLOW, 0xFF9030, 0x400800, 1.5f, 1.8f, 4, 0f, 0.9f), feet.add(0, h * 0.5, 0), Vec3.ZERO);
        if (tick % 4 == 0) {
            Vfx.one(level, blackSmoke(0.5f, 30), feet.add((random.nextDouble() - 0.5) * w, h, (random.nextDouble() - 0.5) * w), new Vec3(0, 0.05, 0));
            groundLight(level, feet, 0x8A2A06, 0x200400, 1.4f, 6);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.DRIPPING_LAVA, feet.x + (random.nextDouble() - 0.5) * w, feet.y + h * 0.4, feet.z + (random.nextDouble() - 0.5) * w, 0, 0, 0);
        }
    }

    // ================================================================ boil

    /** A burst of boiling steam: a hot flash under thick billows rolling outward and up. */
    public static void steamBurst(Level level, Vec3 at, float r) {
        RandomSource random = level.random;
        flashCloud(level, at, 0xFFFFFF, STEAM_HEAT, r * 1.2f, 4);
        int puffs = (int) (8 + r * 5);
        for (int i = 0; i < puffs; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.6, random.nextGaussian()).normalize();
            float s = big(0.5f + r * (0.15f + 0.2f * random.nextFloat()));
            Vfx.one(level, new VfxOptions(VfxKind.STEAM, STEAM_WHITE, STEAM_GREY, s, s * 1.5f, 22 + random.nextInt(14), -0.01f, 0.86f),
                    at.add(d.scale(r * 0.2)), d.scale(0.2 + r * 0.08));
        }
        Vfx.burst(level, Vfx.ring(0xFFE8E0, r * 1.5f, 10), FireVfx.floorBelow(level, at, 3), 1, 0, 0);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, (int) (6 + r * 4), r * 0.4, 0.3, r * 0.4, 0.1);
        }
    }

    /** One tick of a cone of steam along {@code dir} ({@code color} tints it: acid is faintly yellow). */
    public static void steamCone(Level level, Vec3 from, Vec3 dir, double range, int color) {
        RandomSource random = level.random;
        for (double d = 0.8; d <= range; d += 0.9) {
            double width = 0.2 + d * 0.25;
            Vec3 at = from.add(dir.scale(d)).add(random.nextGaussian() * width * 0.4, random.nextGaussian() * width * 0.3, random.nextGaussian() * width * 0.4);
            float s = (float) (0.4 + d * 0.18);
            Vfx.one(level, new VfxOptions(VfxKind.STEAM, color, STEAM_GREY, s, s * 2.2f, 18 + random.nextInt(10), -0.01f, 0.88f), at, dir.scale(0.3));
        }
        Vfx.one(level, Vfx.glow(0xFFFFFF, STEAM_HEAT, 0.6f, 0.2f, 3), from.add(dir.scale(0.7)), dir.scale(0.2));
    }

    /** One tick of a geyser column {@code height} tall: a scalding jet of water and steam. */
    public static void geyser(Level level, Vec3 base, float height, float width) {
        RandomSource random = level.random;
        for (double y = 0; y < height; y += 0.8) {
            Vec3 at = base.add(random.nextGaussian() * width * 0.25, y, random.nextGaussian() * width * 0.25);
            Vfx.one(level, steam(STEAM_WHITE, width * (0.6f + 0.4f * (float) (y / height)), 14 + random.nextInt(8)), at, new Vec3(0, 0.35, 0));
        }
        Vfx.one(level, Vfx.glow(0xFFFFFF, STEAM_HEAT, width * 1.2f, width * 0.3f, 4), base.add(0, 0.5, 0), Vec3.ZERO);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, base.x, base.y + height * 0.5, base.z, 14, width * 0.3, height * 0.4, width * 0.3, 0.2);
            server.sendParticles(ParticleTypes.BUBBLE_POP, base.x, base.y + 0.3, base.z, 6, width * 0.5, 0.1, width * 0.5, 0.05);
        }
    }

    /** The ground hissing before a geyser: thin wisps and bubbles, a small warm glow. */
    public static void hiss(Level level, Vec3 base, float width) {
        RandomSource random = level.random;
        Vfx.one(level, steam(STEAM_WHITE, 0.35f, 20), base.add(random.nextGaussian() * width * 0.4, 0.1, random.nextGaussian() * width * 0.4), new Vec3(0, 0.08, 0));
        groundLight(level, base, 0x806050, 0x201010, width, 4);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.BUBBLE_POP, base.x, base.y + 0.1, base.z, 2, width * 0.4, 0, width * 0.4, 0.02);
        }
    }

    /** Boiling vapour armour: steam pouring off the body, thickest at the shoulders, a wake behind. */
    public static void vapourArmor(Level level, Entity body, Vec3 motion) {
        RandomSource random = level.random;
        Vec3 feet = body.position();
        float w = body.getBbWidth();
        float h = body.getBbHeight();
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 at = feet.add(Math.cos(a) * w * 0.6, h * (0.3 + 0.7 * random.nextDouble()), Math.sin(a) * w * 0.6);
            Vfx.one(level, steam(STEAM_WHITE, 0.55f + random.nextFloat() * 0.3f, 18), at, motion.scale(-0.2).add(0, 0.06, 0));
        }
        Vfx.one(level, Vfx.glow(0xFFFFFF, STEAM_HEAT, 1.6f, 1.9f, 4), feet.add(0, h * 0.5, 0), Vec3.ZERO);
    }

    /** One tick of a pressure dome: steam swirling on a hemisphere of {@code radius}, tighter and hotter as it builds. */
    public static void pressureDome(Level level, Vec3 center, float radius, float progress, int tick) {
        RandomSource random = level.random;
        int count = 10 + (int) (radius * 3f);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2 + tick * 0.15;
            double e = random.nextDouble() * Math.PI * 0.5;
            Vec3 at = center.add(Math.cos(a) * Math.cos(e) * radius, Math.sin(e) * radius * 0.8, Math.sin(a) * Math.cos(e) * radius);
            Vec3 swirl = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.15).add(center.subtract(at).normalize().scale(0.04 * progress));
            int tint = progress > 0.6f && random.nextInt(3) == 0 ? 0xFFD0C0 : STEAM_WHITE;
            Vfx.one(level, new VfxOptions(VfxKind.STEAM, tint, STEAM_GREY, 0.5f + random.nextFloat() * 0.4f, 0.8f, 10, 0f, 0.95f), at, swirl);
        }
        if (progress > 0.5f) {
            Vfx.one(level, Vfx.glow(0xFFFFFF, STEAM_HEAT, big(radius * progress * 0.6f), 0.5f, 3), center.add(0, radius * 0.3, 0), Vec3.ZERO);
            // The base glows hot pink where the dome meets the ground.
            groundLight(level, center, 0x804838, 0x200808, radius * (0.6f + 0.6f * progress), 4);
        }
    }

    // ================================================================ storm

    /** A dark thundercloud of {@code radius}: blue-black billows, a flicker of light inside. */
    public static void cloud(Level level, Vec3 center, float radius, boolean flicker) {
        RandomSource random = level.random;
        Vfx.burst(level, stormCloud(big(0.6f + radius * 0.3f), 22), center, 2 + (int) radius, radius * 0.45, 0.01);
        if (flicker) {
            Vfx.one(level, Vfx.glow(0x9FC8FF, STORM_DEEP, big(radius * 0.5f), big(radius * 0.25f), 4),
                    center.add(random.nextGaussian() * radius * 0.3, random.nextGaussian() * 0.2, random.nextGaussian() * radius * 0.3), Vec3.ZERO);
        }
    }

    /**
     * A laser of the Storm release from {@code from} to {@code to}: a white-hot core inside a deep
     * blue sheath, black flecks spinning round it, bolts crackling along it, a burst at the end.
     */
    public static void laser(Level level, Vec3 from, Vec3 to, float width) {
        RandomSource random = level.random;
        double length = from.distanceTo(to);
        if (length < 1e-3) {
            return;
        }
        Vec3 dir = to.subtract(from).normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        for (double d = 0; d <= length; d += 0.22) {
            Vec3 at = from.add(dir.scale(d));
            // A solid blue-black sheath (normal blending, so it reads against a bright sky)...
            Vfx.one(level, new VfxOptions(VfxKind.SOOT, STORM_DEEP, STORM_BLACK, width * 2.6f, width * 2.2f, 10, 0f, 1f), at, Vec3.ZERO);
            // ...around a white-hot core with a blue glow.
            Vfx.one(level, Vfx.glow(STORM_CORE, STORM_BLUE, width * 1.3f, width * 0.6f, 12), at, Vec3.ZERO);
            if (((int) (d / 0.22)) % 3 == 0) {
                Vfx.one(level, Vfx.glow(STORM_BLUE, STORM_DEEP, width * 3f, width * 1.6f, 14), at, Vec3.ZERO);
            }
            if (random.nextInt(3) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 off = side.scale(Math.cos(a) * width * 1.8).add(up.scale(Math.sin(a) * width * 1.8));
                Vfx.one(level, new VfxOptions(VfxKind.SOOT, STORM_BLACK, CLOUD_DARK, width * 1.2f, width * 2f, 16, 0f, 0.9f), at.add(off), off.scale(0.15));
            }
            if (random.nextInt(8) == 0) {
                Vfx.one(level, new VfxOptions(VfxKind.ZAP, 0xFFFFFF, STORM_BLUE, width * 2f, width * 2.5f, 4, 0f, 1f), at, Vec3.ZERO);
            }
        }
        Vfx.burst(level, new VfxOptions(VfxKind.ZAP, 0xFFFFFF, STORM_BLUE, width * 4f, width * 5f, 5, 0f, 1f), to, 1, 0, 0);
        Vfx.radial(level, Vfx.spark(STORM_BLUE, 0.16f, 8), to, 8, 0.4);
    }

    /** A dark cloud gathering in the hands before a laser fires (the anticipation). */
    public static void laserCharge(Level level, Vec3 hands, float progress) {
        Vfx.gather(level, hands, new Vfx.Palette(STORM_CORE, STORM_BLUE, STORM_BLACK, 0x9FE4FF), 1.4f - 0.8f * progress, 4);
        Vfx.one(level, stormCloud(0.3f + 0.4f * progress, 8), hands, Vec3.ZERO);
        Vfx.one(level, Vfx.glow(STORM_CORE, STORM_BLUE, 0.2f + 0.4f * progress, 0.1f, 4), hands, Vec3.ZERO);
    }

    /** One slice of a crackling storm tide: dark charged water, arcs running over its crest. */
    public static void tideSlice(Level level, Vec3 base, double height, Vec3 dir) {
        RandomSource random = level.random;
        Vfx.one(level, Vfx.glow(0x2A5ACC, STORM_DEEP, 1.1f, 1.4f, 8), base.add(0, height * random.nextDouble(), 0), dir.scale(0.1));
        Vfx.one(level, stormCloud(0.9f, 16), base.add(0, height, 0), dir.scale(0.1).add(0, 0.04, 0));
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, base.x, base.y + height * 0.5, base.z, 4, 0.3, height * 0.4, 0.3, 0.1);
            if (random.nextInt(3) == 0) {
                Vec3 a = base.add(0, height, 0);
                Vec3 b = a.add(random.nextGaussian() * 1.2, -height * random.nextDouble(), random.nextGaussian() * 1.2);
                LightningVfx.bolt(level, a, b, LightningVfx.BLUE_WHITE, 0.06f, 4, 2, LightningVfx.FORKY);
            }
        }
    }

    // ================================================================ explosion

    /** One tick of a clay bird in flight: a pale chalky body, two wings beating, a fuse spark. */
    public static void clayBird(Level level, Vec3 at, Vec3 motion, float s, int age) {
        Vec3 dir = motion.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : motion.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        double beat = Math.sin(age * 0.9) * s * 0.6;
        Vfx.one(level, clay(s, 2), at, motion);
        Vfx.one(level, clay(s * 0.6f, 2), at.add(dir.scale(s * 0.6)).add(0, s * 0.15, 0), motion); // head
        for (int k = -1; k <= 1; k += 2) {
            Vec3 wing = at.add(side.scale(k * s * 0.9)).add(0, beat, 0).subtract(dir.scale(s * 0.1));
            Vfx.one(level, clay(s * 0.55f, 2), wing, motion);
            Vfx.one(level, clay(s * 0.35f, 2), wing.add(side.scale(k * s * 0.5)).add(0, beat * 0.6, 0), motion);
        }
        Vfx.one(level, clay(s * 0.4f, 2), at.subtract(dir.scale(s * 0.8)), motion); // tail
        // The chakra inside: an orange fuse-glow flickering at its heart.
        if (age % 3 == 0) {
            Vfx.one(level, Vfx.spark(0xFF9A10, 0.14f, 6), at.subtract(dir.scale(s * 0.3)), motion.scale(-0.1));
        }
    }

    /**
     * An explosion the Explosion release's way: a <b>white flash</b> that fills the eye first, then
     * <b>fire</b> (flame cells and a hot core), then <b>smoke rings</b>: a ring rolling out along
     * the ground and one rising up the smoke column. Returns true.
     */
    public static boolean blast(Level level, Vec3 at, float r) {
        r = Math.max(0.8f, r);
        RandomSource random = level.random;
        // 1. The flash: pure white, huge, very short.
        flashCloud(level, at, BLAST_WHITE, BLAST_WHITE, r * 2f, 3);
        flashCloud(level, at, 0xFFFFFF, 0xFFE8A0, r * 1.4f, 6);
        // 2. The fire: flame cells bursting, a hot orange heart.
        Vfx.burst(level, FireVfx.heat(big(r * 1.6f), 10), at, 1 + (int) (r / 3), r * 0.2, 0);
        int cells = (int) (8 + r * 5);
        for (int i = 0; i < cells; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.6, random.nextGaussian()).normalize();
            float s = big(r * (0.45f + 0.4f * random.nextFloat()));
            Vfx.one(level, FireVfx.cell(s, big(s * 1.3f), 10 + random.nextInt(8), -0.01f), at.add(d.scale(r * 0.25 * random.nextDouble())),
                    d.scale(r * (0.08 + 0.05 * random.nextDouble())));
        }
        Vfx.radial(level, Vfx.spark(0xFF9A10, 0.24f, 14), at, (int) (10 + r * 5), r * 0.35);
        // 3. The smoke rings: one rolling out over the ground, one around the rising column.
        Vec3 ground = FireVfx.floorBelow(level, at, 4).add(0, 0.25, 0);
        smokeRing(level, ground, r * 0.5f, r * 0.12f + 0.15f, (int) (14 + r * 3), big(0.6f + r * 0.12f), 0f);
        smokeRing(level, at.add(0, r * 0.6, 0), r * 0.35f, r * 0.06f + 0.05f, (int) (10 + r * 2), big(0.5f + r * 0.1f), 0.06f + r * 0.01f);
        Vfx.burst(level, Vfx.ring(0xFFC060, r * 1.9f, 9), ground, 1, 0, 0);
        Vfx.burst(level, new VfxOptions(VfxKind.SOOT, BLAST_SMOKE, 0x1A1816, big(r * 0.55f), big(r * 1.1f), 60, -0.01f, 0.93f), at.add(0, r * 0.4, 0), (int) (4 + r * 3), r * 0.3, 0.02);
        groundLight(level, at, 0xA05010, 0x200800, r * 2.2f, 14);
        FireVfx.groundDebris(level, at, r * 0.8f);
        if (r >= 2f) {
            Vfx.quake(level, at, Math.min(1.5f, r * 0.18f), r * 6);
        }
        return true;
    }

    /**
     * A ring of smoke puffs at height {@code at}: {@code count} puffs on a circle of {@code radius},
     * each flying outward at {@code speed} (and rising at {@code rise}).
     */
    public static void smokeRing(Level level, Vec3 at, float radius, float speed, int count, float puff, float rise) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.one(level, new VfxOptions(VfxKind.SOOT, 0x6A625C, 0x2A2624, puff, big(puff * 2.2f), 40 + level.random.nextInt(15), -rise * 0.05f, 0.9f),
                    at.add(out.scale(radius)), out.scale(speed).add(0, rise, 0));
        }
    }

    /** The charge before a self-centred blast: light leaking through cracks in the body, the air pulled in. */
    public static void bodyCracks(Level level, Entity body, float progress) {
        RandomSource random = level.random;
        Vec3 feet = body.position();
        float w = body.getBbWidth();
        float h = body.getBbHeight();
        for (int i = 0; i < 3 + (int) (progress * 6); i++) {
            // A crack on the skin: a short jagged line of white-orange light, just outside the body.
            double ang = random.nextDouble() * Math.PI * 2;
            Vec3 a = feet.add(Math.cos(ang) * w * 0.55, random.nextDouble() * h, Math.sin(ang) * w * 0.55);
            Vec3 b = a.add(random.nextGaussian() * 0.3, random.nextGaussian() * 0.4, random.nextGaussian() * 0.3);
            for (double t = 0; t <= 1; t += 0.25) {
                Vfx.one(level, Vfx.glow(0xFFFFFF, 0xFF9A10, 0.26f + progress * 0.2f, 0.08f, 5), a.add(b.subtract(a).scale(t)), Vec3.ZERO);
            }
        }
        // Heat pouring off the body and the ground around it starting to glow.
        Vfx.one(level, Vfx.spark(0xFFB040, 0.18f, 12), feet.add((random.nextDouble() - 0.5) * w, h * random.nextDouble(), (random.nextDouble() - 0.5) * w),
                new Vec3(0, 0.12, 0));
        groundLight(level, feet, 0x904010, 0x200800, 1.5f + progress * 3f, 4);
        Vfx.gather(level, feet.add(0, h * 0.5, 0), new Vfx.Palette(0xFFFFFF, 0xFFD080, 0x3A2010, 0xFF6A10), 2.5f - progress, 3 + (int) (progress * 6));
        Vfx.one(level, Vfx.glow(0xFFFFF0, 0xFF9A10, 0.6f + progress * 1.8f, 0.4f, 4), feet.add(0, h * 0.5, 0), Vec3.ZERO);
    }
}

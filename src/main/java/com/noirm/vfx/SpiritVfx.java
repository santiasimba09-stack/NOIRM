package com.noirm.vfx;

import com.noirm.clan.Nature;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The spirit and body natures and medical ninjutsu (docs/CHAKRA_WAVE.md, "Yin, Yang, Yin-Yang,
 * Medical"), each with its own look:
 * <ul>
 *   <li><b>Yin</b>: dark violet-black. Ink (solid, unlit near-black puffs) with violet light at its
 *       rim: shadows that run over the ground, tendrils that wind up a body, phantoms and illusions
 *       that shatter like glass when broken.</li>
 *   <li><b>Yang</b>: warm golden-white. A bright, solid body glow: a white-hot core, gold body,
 *       light rising off the outline.</li>
 *   <li><b>Yin-Yang</b>: white light and black ink swirled together, never mixed into grey.</li>
 *   <li><b>Medical</b>: soft green, slow and calm: motes rising gently, nothing violent. Poisons
 *       are the exception: sickly violet mist with green specks.</li>
 * </ul>
 * Works on both sides like {@link Vfx}. Counts stay small; the client thins them with distance.
 */
public final class SpiritVfx {
    public static final Vfx.Palette YIN = Vfx.palette(Nature.YIN);
    public static final Vfx.Palette YANG = Vfx.palette(Nature.YANG);
    public static final Vfx.Palette YIN_YANG = new Vfx.Palette(0xFFFFFF, 0xE8E4FF, 0x08060E, 0xFFFFFF);
    public static final Vfx.Palette MEDICAL = new Vfx.Palette(0xF2FFF4, 0x5CE08A, 0x0E4A2A, 0xC8FFE0);
    public static final Vfx.Palette POISON = new Vfx.Palette(0xF0D8FF, 0x7A3AA8, 0x1A0A24, 0x9ACD32);
    /** Ink: almost black with a violet cast. */
    public static final int INK = 0x0A0612;
    private static final int LIFE_RED = 0xC0102A;

    private SpiritVfx() {}

    // ---------------------------------------------------------------- building blocks

    /** A solid ink puff (not a light: it reads dark against anything). */
    public static VfxOptions ink(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, INK, 0x000000, size, size * 1.4f, life, -0.002f, 0.86f);
    }

    /** A violet rim light for ink. */
    public static VfxOptions rim(float size, int life) {
        return Vfx.glow(YIN.mid(), YIN.edge(), size, size * 0.3f, life);
    }

    private static Vec3 jitter(RandomSource random, double scale) {
        return new Vec3(random.nextGaussian() * scale, random.nextGaussian() * scale, random.nextGaussian() * scale);
    }

    // ---------------------------------------------------------------- Yin

    /**
     * A shadow running over the ground from {@code from} to {@code to}: a solid ink band with violet
     * light flickering along its edges and a flare at its tip.
     */
    public static void shadowPath(Level level, Vec3 from, Vec3 to, float width) {
        double length = from.distanceTo(to);
        if (length < 1e-3) {
            return;
        }
        Vec3 dir = to.subtract(from).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        RandomSource random = level.random;
        for (double d = 0; d <= length; d += 0.45) {
            double wobble = Math.sin(d * 1.7 + level.getGameTime() * 0.4) * width * 0.25;
            Vec3 at = from.add(dir.scale(d)).add(side.scale(wobble));
            Vfx.one(level, ink(width, 6), at, Vec3.ZERO);
            if (random.nextInt(3) == 0) {
                double edge = (random.nextBoolean() ? 1 : -1) * width * 0.55;
                Vfx.one(level, rim(0.14f, 6), at.add(side.scale(edge)).add(0, 0.05, 0), new Vec3(0, 0.01, 0));
            }
        }
        Vfx.one(level, rim(0.4f, 4), to.add(0, 0.1, 0), Vec3.ZERO);
        Vfx.one(level, ink(width * 1.4f, 5), to, new Vec3(0, 0.02, 0));
    }

    /**
     * Ink tendrils winding up {@code target}: three strands climbing to {@code progress} (0..1) of
     * its height, a violet glint at each tip.
     */
    public static void inkWrap(Level level, Entity target, float progress) {
        float height = target.getBbHeight() * Math.min(1f, progress);
        float radius = target.getBbWidth() * 0.65f + 0.15f;
        double spin = level.getGameTime() * 0.25;
        for (int strand = 0; strand < 3; strand++) {
            double phase = spin + strand * Math.PI * 2 / 3;
            for (double h = 0; h <= height; h += 0.22) {
                double a = phase + h * 3.2;
                Vec3 at = target.position().add(Math.cos(a) * radius, h, Math.sin(a) * radius);
                Vfx.one(level, ink(0.22f, 5), at, Vec3.ZERO);
            }
            double a = phase + height * 3.2;
            Vfx.one(level, rim(0.2f, 5), target.position().add(Math.cos(a) * radius, height, Math.sin(a) * radius), Vec3.ZERO);
        }
    }

    /** A spike of ink bursting up out of the ground at {@code base}, {@code height} tall, tipped with violet. */
    public static void inkSpike(Level level, Vec3 base, float height) {
        RandomSource random = level.random;
        for (double h = 0; h <= height; h += 0.25) {
            float taper = (float) (1 - h / (height + 0.4));
            Vfx.one(level, ink(0.32f * taper + 0.06f, 8 + random.nextInt(4)), base.add(jitter(random, 0.04)).add(0, h, 0), new Vec3(0, 0.02, 0));
        }
        Vec3 tip = base.add(0, height, 0);
        Vfx.one(level, Vfx.glow(YIN.core(), YIN.mid(), 0.5f, 0.1f, 5), tip, Vec3.ZERO);
        Vfx.radial(level, Vfx.shard(INK, 0.16f, 14), base.add(0, 0.2, 0), 5, 0.18);
        Vfx.burst(level, Vfx.ring(YIN.mid(), 1.1f, 8), base.add(0, 0.05, 0), 1, 0, 0);
    }

    /**
     * A phantom body of ink and violet light at {@code feet}: a false image of someone (False
     * Surroundings' decoy, the genjutsu phantom behind a bound victim).
     */
    public static void phantom(Level level, Vec3 feet, float height, float yawRad) {
        RandomSource random = level.random;
        for (double h = 0.1; h < height * 0.78; h += 0.2) {
            float w = h < height * 0.45 ? 0.18f : 0.26f;
            Vfx.one(level, ink(w, 4), feet.add(jitter(random, 0.03)).add(0, h, 0), Vec3.ZERO);
        }
        Vec3 head = feet.add(0, height * 0.88, 0);
        Vfx.one(level, ink(0.3f, 4), head, Vec3.ZERO);
        // Two violet eyes looking where the phantom faces.
        Vec3 face = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).scale(0.16);
        Vec3 across = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).scale(0.07);
        Vfx.one(level, Vfx.glow(YIN.core(), YIN.mid(), 0.07f, 0.03f, 4), head.add(face).add(across), Vec3.ZERO);
        Vfx.one(level, Vfx.glow(YIN.core(), YIN.mid(), 0.07f, 0.03f, 4), head.add(face).subtract(across), Vec3.ZERO);
        if (random.nextInt(2) == 0) {
            Vfx.one(level, rim(0.12f, 8), feet.add((random.nextDouble() - 0.5) * 0.6, random.nextDouble() * height, (random.nextDouble() - 0.5) * 0.6),
                    new Vec3(0, 0.03, 0));
        }
    }

    /**
     * The phantom tree of Tree Binding Death around {@code feet}: a dark trunk grows up just behind
     * the victim ({@code back}: the flat direction away from the caster, so the victim stays in
     * view; {@code progress} 0..1), branches wrap their arms, violet light hangs in the crown.
     */
    public static void illusionTree(Level level, Vec3 feet, Vec3 back, float height, float progress) {
        RandomSource random = level.random;
        float tall = height * 1.9f;
        float grown = tall * Math.min(1f, progress);
        Vfx.one(level, new VfxOptions(VfxKind.GROUND_GLOW, YIN.mid(), YIN.edge(), 2.6f, 2.6f, 5, 0f, 1f), feet.add(0, 0.04, 0), Vec3.ZERO);
        // The trunk, rising up behind the victim: a thick ink column with violet light down its sides.
        Vec3 base = feet.add(back.scale(0.75));
        for (double h = 0; h <= grown; h += 0.25) {
            float w = (float) (0.95 - 0.45 * h / tall);
            Vec3 at = base.add(random.nextGaussian() * 0.04, h, random.nextGaussian() * 0.04);
            Vfx.one(level, ink(w, 6), at, Vec3.ZERO);
            if (random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                Vfx.one(level, rim(0.16f, 6), at.add(Math.cos(a) * w * 0.55, 0, Math.sin(a) * w * 0.55), new Vec3(0, 0.01, 0));
            }
        }
        // Roots spreading over the ground.
        for (int r = 0; r < 5; r++) {
            double a = r * Math.PI * 2 / 5 + 0.4;
            for (double s = 0.3; s < 1.6 * Math.min(1f, progress * 2f); s += 0.25) {
                Vfx.one(level, ink((float) (0.4 - s * 0.15), 6), feet.add(Math.cos(a) * s, 0.08, Math.sin(a) * s), Vec3.ZERO);
            }
        }
        if (progress < 0.3f) {
            return;
        }
        // Branches wrapping round the body and arms.
        float wrap = Math.min(1f, (progress - 0.3f) / 0.4f);
        for (int b = 0; b < 5; b++) {
            double a0 = b * Math.PI * 0.4 + level.getGameTime() * 0.02;
            double y = height * (0.2 + 0.16 * b);
            for (double s = 0; s < Math.PI * 1.4 * wrap; s += 0.3) {
                double r = 0.5 + 0.06 * Math.sin(s * 4);
                Vfx.one(level, ink(0.2f, 6), feet.add(Math.cos(a0 + s) * r, y + s * 0.07, Math.sin(a0 + s) * r), Vec3.ZERO);
            }
        }
        if (progress >= 0.6f) {
            // The crown: a dark canopy with violet light caught in it.
            for (int i = 0; i < 8; i++) {
                Vec3 leaf = base.add(back.scale(0.4)).add(random.nextGaussian() * 1.1, grown + random.nextGaussian() * 0.35, random.nextGaussian() * 1.1);
                Vfx.one(level, ink(0.8f, 8), leaf, Vec3.ZERO);
                if (i % 2 == 0) {
                    Vfx.one(level, Vfx.glow(YIN.accent(), YIN.mid(), 0.4f, 0.1f, 10), leaf.add(0, -0.25, 0), new Vec3(0, -0.01, 0));
                }
            }
        }
    }

    /** A genjutsu taking hold of {@code target}: an ink ring closing in, a violet spiral, the light dimming. */
    public static void takeHold(Level level, Entity target) {
        Vec3 eyes = target.position().add(0, target.getBbHeight() * 0.85, 0);
        Vfx.burst(level, new VfxOptions(VfxKind.RING, YIN.core(), YIN.mid(), 2.6f, 0.2f, 10, 0f, 1f), eyes, 1, 0, 0);
        Vfx.spiral(level, target.position(), YIN, target.getBbWidth() + 0.35f, target.getBbHeight() + 0.3f, 2.5f);
        Vfx.gather(level, eyes, YIN, 1.8f, 16);
        Vfx.burst(level, ink(0.5f, 16), target.position().add(0, target.getBbHeight() * 0.5, 0), 6, 0.4, 0.02);
    }

    /** A genjutsu breaking: the false world shatters into violet shards, with a crisp white ring. */
    public static void shatter(Level level, Vec3 at) {
        Vfx.burst(level, Vfx.glow(0xFFFFFF, YIN.core(), 0.9f, 0.1f, 4), at, 1, 0, 0);
        Vfx.burst(level, Vfx.ring(0xFFFFFF, 2.2f, 8), at, 1, 0, 0);
        Vfx.radial(level, Vfx.shard(YIN.accent(), 0.2f, 18), at, 16, 0.35);
        Vfx.radial(level, Vfx.spark(YIN.mid(), 0.15f, 10), at, 10, 0.45);
        Vfx.burst(level, ink(0.3f, 12), at, 5, 0.35, 0.05);
    }

    // ---------------------------------------------------------------- Yang

    /** A solid golden glow over a body: a white-hot core at the chest, gold light rising off the outline. */
    public static void yangGlow(Level level, Entity body, int count) {
        RandomSource random = level.random;
        Vec3 chest = body.position().add(0, body.getBbHeight() * 0.6, 0);
        Vfx.one(level, Vfx.glow(YANG.core(), YANG.mid(), 0.7f, 0.5f, 3), chest, Vec3.ZERO);
        for (int i = 0; i < count; i++) {
            Vec3 at = body.position().add((random.nextDouble() - 0.5) * body.getBbWidth() * 1.3, random.nextDouble() * body.getBbHeight(),
                    (random.nextDouble() - 0.5) * body.getBbWidth() * 1.3);
            boolean hot = random.nextInt(3) == 0;
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, hot ? YANG.core() : YANG.mid(), YANG.accent(), hot ? 0.3f : 0.38f, 0.06f, 10, -0.02f, 0.9f),
                    at, new Vec3(0, 0.05, 0));
        }
    }

    /** A burst of life: a white-gold flash and gold motes flying out, a ring at the feet. */
    public static void yangBurst(Level level, Vec3 feet, float height, float radius) {
        Vec3 chest = feet.add(0, height * 0.6, 0);
        Vfx.flash(level, chest, YANG, 1.4f * radius);
        Vfx.radial(level, Vfx.glow(YANG.mid(), YANG.accent(), 0.3f, 0.05f, 14), chest, 18, 0.25);
        Vfx.radial(level, Vfx.spark(YANG.core(), 0.16f, 10), chest, 10, 0.4);
        Vfx.shockwave(level, feet.add(0, 0.05, 0), YANG, 2.2f * radius);
        for (int i = 0; i < 8; i++) {
            Vfx.one(level, Vfx.glow(YANG.core(), YANG.mid(), 0.35f, 0.08f, 16), feet.add((level.random.nextDouble() - 0.5) * 0.8, 0.1,
                    (level.random.nextDouble() - 0.5) * 0.8), new Vec3(0, 0.22 + level.random.nextDouble() * 0.15, 0));
        }
    }

    /**
     * A giant fist of solid golden chakra, {@code size} across, at {@code at} facing {@code dir}:
     * a dense core, a gold shell, four knuckles on its front.
     */
    public static void fist(Level level, Vec3 at, Vec3 dir, float size) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        Vfx.one(level, Vfx.glow(YANG.core(), YANG.mid(), size * 0.9f, size * 0.85f, 2), at, Vec3.ZERO);
        Vfx.one(level, Vfx.glow(YANG.mid(), YANG.accent(), size * 1.2f, size * 1.1f, 2), at, Vec3.ZERO);
        for (int k = 0; k < 4; k++) {
            double offset = (k - 1.5) * size * 0.26;
            Vec3 knuckle = at.add(dir.scale(size * 0.42)).add(side.scale(offset)).add(0, size * 0.18, 0);
            Vfx.one(level, Vfx.glow(YANG.core(), YANG.mid(), size * 0.3f, size * 0.28f, 2), knuckle, Vec3.ZERO);
        }
        Vfx.one(level, Vfx.glow(YANG.mid(), YANG.edge(), size * 0.5f, size * 0.2f, 6), at.subtract(dir.scale(size * 0.6)), dir.scale(-0.05));
    }

    /** Life drawn out of a body: red wisps flowing into the chest, turning gold there. */
    public static void lifeWisps(Level level, Entity body) {
        RandomSource random = level.random;
        Vec3 chest = body.position().add(0, body.getBbHeight() * 0.6, 0);
        for (int i = 0; i < 14; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
            Vec3 from = chest.add(dir.scale(1.4));
            Vfx.one(level, Vfx.glow(LIFE_RED, YANG.mid(), 0.2f, 0.12f, 10), from, dir.scale(-0.13));
        }
        Vfx.one(level, Vfx.glow(YANG.core(), YANG.mid(), 0.8f, 0.2f, 8), chest, Vec3.ZERO);
    }

    // ---------------------------------------------------------------- Yin-Yang

    /**
     * White light and black ink swirling up together around {@code feet}: two strands, one white
     * glow, one ink, wound {@code turns} times over {@code height}.
     */
    public static void swirl(Level level, Vec3 feet, float radius, float height, float turns) {
        int steps = Math.max(10, (int) (turns * 12));
        double spin = level.getGameTime() * 0.3;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = spin + t * turns * Math.PI * 2;
            Vec3 white = feet.add(Math.cos(a) * radius, t * height, Math.sin(a) * radius);
            Vec3 black = feet.add(Math.cos(a + Math.PI) * radius, t * height, Math.sin(a + Math.PI) * radius);
            Vec3 drift = new Vec3(0, 0.02, 0);
            Vfx.one(level, Vfx.glow(0xFFFFFF, 0xD8D4FF, 0.24f, 0.05f, 10), white, drift);
            Vfx.one(level, ink(0.24f, 10), black, drift);
        }
    }

    /** A turning ball of yin and yang at {@code at}: a white half and a black half chasing each other. */
    public static void taiji(Level level, Vec3 at, float size) {
        double a = level.getGameTime() * 0.35;
        Vec3 w = new Vec3(Math.cos(a), Math.sin(a * 0.5) * 0.3, Math.sin(a)).scale(size * 0.28);
        // A pale halo round the whole ball, then the two halves: solid white light and solid ink.
        Vfx.one(level, Vfx.glow(0xE8E4FF, 0x8070C0, size * 1.5f, size * 1.3f, 4), at, Vec3.ZERO);
        Vfx.one(level, Vfx.glow(0xFFFFFF, 0xF4F2FF, size * 0.9f, size * 0.8f, 5), at.add(w), Vec3.ZERO);
        Vfx.one(level, new VfxOptions(VfxKind.SOOT, 0x000000, INK, size * 0.85f, size * 0.8f, 5, 0f, 1f), at.subtract(w), Vec3.ZERO);
        // The eye in each half: a black dot in the white, a white dot in the black.
        Vfx.one(level, Vfx.glow(0xFFFFFF, 0xFFFFFF, size * 0.22f, size * 0.15f, 4), at.subtract(w).add(0, 0, 0.01), Vec3.ZERO);
        Vfx.one(level, new VfxOptions(VfxKind.SOOT, 0x000000, 0x000000, size * 0.2f, size * 0.18f, 4, 0f, 1f), at.add(w), Vec3.ZERO);
    }

    /** Everything pulled into {@code at}: shrinking rings, white and black motes falling inward. */
    public static void implode(Level level, Vec3 at, float radius, int count) {
        Vfx.burst(level, new VfxOptions(VfxKind.RING, 0xFFFFFF, 0xD8D4FF, radius, 0.2f, 8, 0f, 1f), at, 1, 0, 0);
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5, random.nextGaussian()).normalize();
            Vec3 from = at.add(dir.scale(radius));
            Vfx.one(level, i % 2 == 0 ? Vfx.glow(0xFFFFFF, 0xD8D4FF, 0.22f, 0.05f, 9) : ink(0.24f, 9), from, dir.scale(-radius / 8));
        }
    }

    /** Everything thrown out of {@code at}: a white flash, an ink ring and a white ring racing out, black and white shards. */
    public static void explode(Level level, Vec3 at, float radius) {
        // A white-hot first frame, then streaks of white and ink thrown out; no lingering smoke to hide the field.
        Vfx.burst(level, Vfx.glow(0xFFFFFF, 0xE8E4FF, radius * 1.4f, radius * 0.4f, 6), at, 1, 0, 0);
        Vfx.burst(level, Vfx.glow(0xFFFFFF, 0xB0A0FF, radius * 0.9f, radius * 1.3f, 10), at, 2, radius * 0.1, 0);
        Vfx.burst(level, Vfx.ring(0xFFFFFF, radius * 1.8f, 10), at, 1, 0, 0);
        Vfx.burst(level, new VfxOptions(VfxKind.RING, INK, 0x000000, 0.4f, radius * 1.3f, 14, 0f, 1f), at, 1, 0, 0);
        Vfx.radial(level, Vfx.glow(0xFFFFFF, 0xC8C0FF, radius * 0.22f, 0.05f, 14), at, (int) (12 + radius * 3), radius * 0.3);
        Vfx.radial(level, ink(0.35f, 12), at, (int) (8 + radius * 2), radius * 0.28);
        Vfx.radial(level, Vfx.shard(INK, 0.24f, 26), at, (int) (6 + radius * 2), radius * 0.25);
        Vfx.radial(level, Vfx.shard(0xF4F2FF, 0.22f, 26), at, (int) (6 + radius * 2), radius * 0.25);
    }

    /**
     * A chakra hawk at {@code at} flying along {@code dir}: a white body with an ink-black head and
     * tail, wings flapping (one white, one black).
     */
    public static void hawk(Level level, Vec3 at, Vec3 dir, int seed) {
        Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        double flap = Math.sin(level.getGameTime() * 0.9 + seed) * 0.35;
        Vfx.one(level, Vfx.glow(0xFFFFFF, 0xE0DCFF, 0.36f, 0.3f, 2), at, Vec3.ZERO);
        Vfx.one(level, ink(0.22f, 3), at.add(d.scale(0.3)), Vec3.ZERO);
        Vfx.one(level, Vfx.glow(0xFFE060, 0xFFFFFF, 0.06f, 0.04f, 2), at.add(d.scale(0.42)).add(0, 0.05, 0), Vec3.ZERO);
        for (int k = 1; k <= 3; k++) {
            double out = k * 0.22;
            double lift = flap * k * 0.6;
            Vfx.one(level, Vfx.glow(0xFFFFFF, 0xC8C0FF, 0.18f, 0.12f, 3), at.add(side.scale(out)).add(0, lift, 0), Vec3.ZERO);
            Vfx.one(level, ink(0.18f, 3), at.subtract(side.scale(out)).add(0, lift, 0), Vec3.ZERO);
        }
        Vfx.one(level, ink(0.16f, 8), at.subtract(d.scale(0.35)), d.scale(-0.04));
    }

    // ---------------------------------------------------------------- Medical

    /** Soft green light on a patient: motes rising slowly off them, a glow at the hands of the healer. */
    public static void mend(Level level, Entity patient, Vec3 hands, int count) {
        RandomSource random = level.random;
        if (hands != null) {
            Vfx.one(level, Vfx.glow(MEDICAL.core(), MEDICAL.mid(), 0.34f, 0.18f, 5), hands, Vec3.ZERO);
        }
        for (int i = 0; i < count; i++) {
            Vec3 at = patient.position().add((random.nextDouble() - 0.5) * patient.getBbWidth() * 1.4, random.nextDouble() * patient.getBbHeight() * 0.8,
                    (random.nextDouble() - 0.5) * patient.getBbWidth() * 1.4);
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, random.nextInt(3) == 0 ? MEDICAL.core() : MEDICAL.mid(), MEDICAL.accent(), 0.2f, 0.04f, 22, -0.006f, 0.96f),
                    at, new Vec3(0, 0.02, 0));
        }
    }

    /** A slow, calm green ring: a medic's circle or a healing pulse. */
    public static void pulse(Level level, Vec3 feet, float radius) {
        Vfx.burst(level, new VfxOptions(VfxKind.RING, MEDICAL.core(), MEDICAL.mid(), 0.4f, radius, 18, 0f, 1f), feet.add(0, 0.05, 0), 1, 0, 0);
    }

    /** A poison cloud of {@code radius}: violet mist rolling slowly, sickly green specks hanging in it. */
    public static void mist(Level level, Vec3 center, float radius, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 at = center.add(random.nextGaussian() * radius * 0.45, Math.abs(random.nextGaussian()) * radius * 0.35, random.nextGaussian() * radius * 0.45);
            Vfx.one(level, new VfxOptions(VfxKind.SMOKE, POISON.mid(), 0x2A1A30, 0.9f, 1.8f, 30, -0.002f, 0.9f), at,
                    new Vec3(random.nextGaussian() * 0.01, 0.005, random.nextGaussian() * 0.01));
            if (random.nextInt(3) == 0) {
                Vfx.one(level, Vfx.glow(POISON.accent(), 0x203010, 0.1f, 0.04f, 20), at, new Vec3(0, -0.005, 0));
            }
        }
    }

    /**
     * A medic's field on the ground around {@code center}: a slowly turning ring of green lights at
     * its rim, a soft pool in its middle, motes rising.
     */
    public static void field(Level level, Vec3 center, float radius, int lights) {
        double turn = level.getGameTime() * 0.05;
        for (int i = 0; i < lights; i++) {
            double a = turn + i * Math.PI * 2 / lights;
            Vec3 at = center.add(Math.cos(a) * radius, 0.08, Math.sin(a) * radius);
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, MEDICAL.mid(), MEDICAL.edge(), 0.3f, 0.12f, 8, 0f, 0.9f), at, new Vec3(0, 0.01, 0));
        }
        Vfx.one(level, new VfxOptions(VfxKind.GROUND_GLOW, MEDICAL.mid(), MEDICAL.edge(), radius * 1.6f, radius * 1.6f, 6, 0f, 1f), center.add(0, 0.04, 0), Vec3.ZERO);
        RandomSource random = level.random;
        Vec3 mote = center.add((random.nextDouble() - 0.5) * radius * 1.6, 0.1, (random.nextDouble() - 0.5) * radius * 1.6);
        Vfx.one(level, new VfxOptions(VfxKind.GLOW, MEDICAL.core(), MEDICAL.mid(), 0.16f, 0.03f, 26, -0.004f, 0.97f), mote, new Vec3(0, 0.03, 0));
    }

    /** The Hundred Healings seal: a violet diamond on the forehead and green lines spreading down from it. */
    public static void seal(Level level, Entity body, boolean opening) {
        Vec3 brow = body.getEyePosition().add(body.getViewVector(1f).multiply(1, 0, 1).normalize().scale(0.28)).add(0, 0.14, 0);
        Vfx.one(level, Vfx.glow(0xF0D8FF, 0x9A40FF, 0.14f, 0.1f, 4), brow, Vec3.ZERO);
        if (opening) {
            for (int line = 0; line < 6; line++) {
                double a = line * Math.PI / 3;
                for (double h = 0; h < body.getBbHeight() * 0.85; h += 0.18) {
                    double r = body.getBbWidth() * 0.55;
                    Vec3 at = body.position().add(Math.cos(a + h) * r, body.getBbHeight() * 0.9 - h, Math.sin(a + h) * r);
                    Vfx.one(level, Vfx.glow(MEDICAL.core(), MEDICAL.mid(), 0.12f, 0.04f, (int) (6 + h * 10)), at, Vec3.ZERO);
                }
            }
        }
    }
}

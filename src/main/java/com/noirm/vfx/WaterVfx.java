package com.noirm.vfx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The look of Water Release's S-rank, the Leviathan Maelstrom (jutsu/WaterTechniques): deep sea blue
 * with white foam, never a flat cyan. Three pieces: a whirlpool turning on the ground, a churning
 * sphere of water (the prison), and the leviathan, a long serpent of water with a pale head that rises
 * out of the vortex and crashes down.
 */
public final class WaterVfx {
    /** Deep water, sea blue, foam white, and the dark of the abyss. */
    public static final int DEEP = 0x0A3C8C;
    public static final int SEA = 0x1E8CFF;
    public static final int FOAM = 0xEAFBFF;
    public static final int ABYSS = 0x020C24;

    private WaterVfx() {}

    /**
     * A whirlpool of {@code radius} turning at {@code center} (tick {@code age}): spiral arms of water
     * sweeping inward over the ground, a ring of white foam at the edge, spray thrown up.
     */
    public static void whirlpool(ServerLevel level, Vec3 center, float radius, int age, float rise) {
        RandomSource random = level.random;
        int arms = 4;
        double spin = age * 0.28;
        for (int arm = 0; arm < arms; arm++) {
            for (int k = 0; k < 7; k++) {
                double t = k / 7.0;
                double r = radius * (1 - t * 0.85);
                double a = spin + arm * Math.PI * 2 / arms + t * 2.6;
                Vec3 at = center.add(Math.cos(a) * r, 0.15 + rise * t * t, Math.sin(a) * r);
                Vec3 inward = new Vec3(-Math.cos(a), 0, -Math.sin(a)).scale(0.05).add(-Math.sin(a) * 0.12, 0.01, Math.cos(a) * 0.12);
                Vfx.one(level, Vfx.glow(k % 3 == 0 ? FOAM : SEA, DEEP, 0.55f + (float) (1 - t) * 0.35f, 0.15f, 9), at, inward);
            }
        }
        // The foam ring at the rim, and the dark eye in the middle.
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 rim = center.add(Math.cos(a) * radius, 0.2, Math.sin(a) * radius);
            Vfx.one(level, Vfx.glow(FOAM, SEA, 0.4f, 0.1f, 7), rim, new Vec3(-Math.sin(a) * 0.15, 0.02, Math.cos(a) * 0.15));
        }
        Vfx.burst(level, Vfx.glow(ABYSS, DEEP, 1.4f, 0.6f, 6), center.add(0, 0.12, 0), 1, 0, 0);
        level.sendParticles(ParticleTypes.SPLASH, center.x, center.y + 0.3, center.z, 10, radius * 0.5, 0.1, radius * 0.5, 0.2);
        if (age % 3 == 0) {
            Vfx.burst(level, Vfx.ring(SEA, radius, 8), center.add(0, 0.08, 0), 1, 0, 0);
        }
    }

    /**
     * The prison: a sphere of water of {@code radius} round {@code center}, its surface churning (two
     * bands winding opposite ways), bubbles rising inside, foam where it meets the ground.
     */
    public static void prison(ServerLevel level, Vec3 center, float radius, int age) {
        RandomSource random = level.random;
        double spin = age * 0.22;
        for (int band = 0; band < 2; band++) {
            double dir = band == 0 ? 1 : -1;
            for (int i = 0; i < 20; i++) {
                double phi = (i + 0.5) / 20.0 * Math.PI; // pole to pole
                double theta = dir * spin + i * 0.9 + band * Math.PI / 2;
                Vec3 n = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta));
                Vec3 at = center.add(n.scale(radius));
                Vfx.one(level, Vfx.glow(band == 0 ? SEA : FOAM, DEEP, 0.95f, 0.35f, 6), at, n.cross(new Vec3(0, dir, 0)).scale(0.06));
            }
        }
        // The body of the water: a dim, deep glow filling it.
        Vfx.burst(level, Vfx.glow(SEA, ABYSS, radius * 1.8f, radius * 1.5f, 4), center, 1, 0, 0);
        Vfx.burst(level, Vfx.glow(FOAM, SEA, radius * 0.9f, radius * 0.6f, 3), center.add(0, radius * 0.3, 0), 1, 0, 0);
        level.sendParticles(ParticleTypes.BUBBLE, center.x, center.y, center.z, 12, radius * 0.4, radius * 0.4, radius * 0.4, 0.05);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, center.x, center.y - radius * 0.5, center.z, 4, radius * 0.3, 0.1, radius * 0.3, 0.05);
        if (random.nextInt(2) == 0) {
            Vfx.burst(level, Vfx.ring(FOAM, radius * 1.2f, 6), center.subtract(0, radius * 0.9, 0), 1, 0, 0);
        }
    }

    /**
     * The leviathan: {@code body} is the path of its head over the last ticks (newest first). The head
     * is pale and bright with two white eyes and an open jaw of foam; the body thins toward the tail
     * and sheds spray.
     */
    public static void leviathan(ServerLevel level, List<Vec3> body, float scale) {
        if (body.isEmpty()) {
            return;
        }
        int n = body.size();
        for (int i = n - 1; i >= 1; i--) {
            float t = i / (float) n;
            float size = scale * (2.0f - t * 1.4f);
            Vec3 at = body.get(i);
            Vfx.one(level, Vfx.glow(i % 4 == 0 ? FOAM : SEA, DEEP, size, size * 0.7f, 4), at, Vec3.ZERO);
            // A second glow halfway to the next segment, so the body reads as one unbroken serpent.
            Vec3 mid = at.add(body.get(i - 1)).scale(0.5);
            Vfx.one(level, Vfx.glow(SEA, DEEP, size * 0.9f, size * 0.6f, 4), mid, Vec3.ZERO);
            if (i % 3 == 0) {
                Vfx.one(level, Vfx.glow(DEEP, ABYSS, size * 1.3f, size, 3), at, Vec3.ZERO);
                level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y, at.z, 2, size * 0.3, size * 0.3, size * 0.3, 0);
            }
        }
        Vec3 head = body.get(0);
        Vec3 behind = body.size() > 1 ? body.get(1) : head.subtract(0, 1, 0);
        Vec3 dir = head.subtract(behind);
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : dir.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float h = scale * 2.6f;
        // Skull and snout, the two eyes, the jaw of foam opening below.
        Vfx.one(level, Vfx.glow(FOAM, SEA, h, h * 0.8f, 3), head, Vec3.ZERO);
        Vfx.one(level, Vfx.glow(SEA, DEEP, h * 1.3f, h, 3), head.subtract(dir.scale(scale * 0.5)), Vec3.ZERO);
        Vfx.one(level, Vfx.glow(0xFFFFFF, FOAM, h * 0.28f, h * 0.22f, 3), head.add(side.scale(scale * 0.45)).add(up.scale(scale * 0.35)), Vec3.ZERO);
        Vfx.one(level, Vfx.glow(0xFFFFFF, FOAM, h * 0.28f, h * 0.22f, 3), head.subtract(side.scale(scale * 0.45)).add(up.scale(scale * 0.35)), Vec3.ZERO);
        Vec3 jaw = head.add(dir.scale(scale * 0.7)).subtract(up.scale(scale * 0.45));
        Vfx.one(level, Vfx.glow(FOAM, SEA, h * 0.6f, h * 0.3f, 3), jaw, dir.scale(0.05));
        for (int s = -1; s <= 1; s += 2) {
            Vec3 horn = head.subtract(dir.scale(scale * 0.4)).add(up.scale(scale * 0.8)).add(side.scale(s * scale * 0.5));
            Vfx.one(level, Vfx.spark(FOAM, scale * 0.5f, 5), horn, up.scale(0.04).subtract(dir.scale(0.06)));
        }
        level.sendParticles(ParticleTypes.SPLASH, head.x, head.y, head.z, 8, scale * 0.5, scale * 0.5, scale * 0.5, 0.1);
    }

    /** The crash: a column of water bursting up, two rings of foam rolling out, spray everywhere. */
    public static void crash(ServerLevel level, Vec3 at, float radius) {
        Vfx.flash(level, at.add(0, 1, 0), new Vfx.Palette(0xFFFFFF, SEA, ABYSS, FOAM), radius * 0.7f);
        for (int i = 0; i < 22; i++) {
            Vec3 jet = new Vec3(level.random.nextGaussian() * 0.25, 0.5 + level.random.nextDouble() * 0.6, level.random.nextGaussian() * 0.25);
            Vfx.one(level, Vfx.glow(i % 3 == 0 ? FOAM : SEA, DEEP, 0.9f, 0.4f, 18), at.add(0, 0.5, 0), jet);
        }
        Vfx.burst(level, Vfx.ring(SEA, radius * 1.4f, 12), at.add(0, 0.1, 0), 1, 0, 0);
        Vfx.burst(level, Vfx.ring(FOAM, radius * 2.0f, 16), at.add(0, 0.14, 0), 1, 0, 0);
        Vfx.radial(level, Vfx.glow(FOAM, SEA, 0.5f, 0.1f, 14), at.add(0, 0.6, 0), 24, radius * 0.12);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 120, radius * 0.6, 0.6, radius * 0.6, 0.5);
        level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y + 4, at.z, 60, radius * 0.6, 2, radius * 0.6, 0);
        Vfx.quake(level, at, 1.0f, radius * 4);
    }
}

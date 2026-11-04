package com.noirm.vfx;

import com.noirm.registry.NoirmBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Wood Release, the way the owner's references draw it (docs/ART_STYLE.md, "Wood Release ideas"):
 * raw grey-green wood, not glowing chakra. Everything that comes out of the ground throws up a
 * heavy skirt of dust, rock and splinters, and settles slowly; the only light is a faint green
 * pulse of chakra where the wood is born.
 *
 * <p>Budget: counts scale with size but stay capped; the client thins particles with distance
 * ({@code VfxParticle}). The big techniques call {@link #emerge} a few times a tick at most.
 */
public final class WoodVfx {
    public static final int DUST = 0x8C8572;
    public static final int DUST_DARK = 0x4E493C;
    public static final int ROCK = 0x4A4438;
    public static final int BARK = 0x7F8A68;
    public static final int BARK_DARK = 0x3A4230;
    /** The faint chakra green under new growth. */
    public static final int SAP = 0xC8FF8A;
    public static final int SAP_DEEP = 0x3F8A2A;

    private WoodVfx() {}

    /** A wood chunk (the Wood Release block breaking). */
    public static BlockParticleOption chunk() {
        return new BlockParticleOption(ParticleTypes.BLOCK, NoirmBlocks.MOKUTON_WOOD.get().defaultBlockState());
    }

    /** Heavy rolling dust that hangs in the air. */
    public static VfxOptions dust(float size, int life) {
        return new VfxOptions(VfxKind.SOOT, DUST, DUST_DARK, size, size * 2.4f, life, -0.0015f, 0.9f);
    }

    public static VfxOptions splinter(float size, int life) {
        return new VfxOptions(VfxKind.SHARD, BARK, BARK_DARK, size, size * 0.6f, life, 0.07f, 0.95f);
    }

    public static VfxOptions rock(float size, int life) {
        return new VfxOptions(VfxKind.SHARD, ROCK, 0x2A261E, size, size * 0.7f, life, 0.08f, 0.96f);
    }

    /**
     * Wood bursting out of the ground at {@code at} ({@code radius} wide): a pale-green pulse at
     * the root, rock and turf blown up, splinters, and a skirt of dust rolling out and rising slowly.
     * Strong first frame, slow decay. Shakes the camera for anything big.
     */
    public static void emerge(Level level, Vec3 at, float radius) {
        float r = Math.max(0.6f, Math.min(radius, 6f));
        RandomSource random = level.random;
        Vfx.burst(level, Vfx.glow(SAP, SAP_DEEP, r * 1.1f, r * 0.3f, 6), at.add(0, 0.3, 0), 1, 0, 0);
        Vfx.burst(level, Vfx.ring(SAP_DEEP, r * 2.2f, 10), at.add(0, 0.06, 0), 1, 0, 0);
        // Dust: a fast low skirt, then a slow column.
        int skirt = 6 + (int) (r * 3);
        for (int i = 0; i < skirt; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.one(level, dust(r * 0.45f, 40 + random.nextInt(20)), at.add(out.scale(r * 0.4)).add(0, 0.3, 0),
                    out.scale(0.18 + 0.08 * r).add(0, 0.02, 0));
        }
        Vfx.burst(level, dust(r * 0.6f, 70), at.add(0, r * 0.6, 0), 3 + (int) r, r * 0.4, 0.02);
        Vfx.radial(level, rock(0.16f + r * 0.03f, 30), at.add(0, 0.4, 0), 5 + (int) (r * 3), 0.35 + r * 0.05);
        Vfx.radial(level, splinter(0.14f + r * 0.02f, 34), at.add(0, 0.6, 0), 4 + (int) (r * 2), 0.3 + r * 0.05);
        if (level instanceof ServerLevel server) {
            BlockPos below = BlockPos.containing(at).below();
            BlockState ground = server.getBlockState(below);
            if (ground.isAir()) {
                ground = server.getBlockState(below.below());
            }
            if (!ground.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.3, at.z,
                        (int) (8 + r * 5), r * 0.4, 0.25, r * 0.4, 0.25);
            }
            server.sendParticles(chunk(), at.x, at.y + 0.6, at.z, (int) (4 + r * 3), r * 0.3, 0.4, r * 0.3, 0.2);
        }
        if (r >= 1.5f) {
            Vfx.quake(level, at, Math.min(1.1f, r * 0.18f), r * 7);
        }
    }

    /** A trunk's rising tip: chips and dust thrown off as it pushes up. */
    public static void tip(Level level, Vec3 at, float size) {
        Vfx.burst(level, splinter(0.12f, 20), at, 2, size * 0.4, 0.08);
        Vfx.burst(level, dust(size * 0.5f, 30), at, 1, size * 0.3, 0.01);
    }

    /** Wood breaking up and falling apart (a clone, the dragon, the colossus' limbs). */
    public static void crumble(Level level, Vec3 at, float size) {
        Vfx.burst(level, dust(size * 0.5f, 50), at, 2 + (int) size, size * 0.4, 0.02);
        Vfx.burst(level, splinter(0.15f, 30), at, 3 + (int) (size * 2), size * 0.4, 0.12);
        if (level instanceof ServerLevel server) {
            server.sendParticles(chunk(), at.x, at.y, at.z, (int) (4 + size * 4), size * 0.4, size * 0.4, size * 0.4, 0.1);
        }
    }

    /** A crack of earth running along the ground from {@code from} to {@code to} (roots racing under it). */
    public static void rootLine(Level level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        double length = step.length();
        if (length < 0.1) {
            return;
        }
        step = step.scale(1 / length);
        for (double d = 0; d <= length; d += 0.8) {
            Vec3 at = from.add(step.scale(d));
            Vfx.one(level, dust(0.35f, 24), at.add(0, 0.15, 0), new Vec3(0, 0.03, 0));
            if (level.random.nextInt(3) == 0) {
                Vfx.one(level, rock(0.12f, 16), at.add(0, 0.2, 0), new Vec3((level.random.nextDouble() - 0.5) * 0.1, 0.18, (level.random.nextDouble() - 0.5) * 0.1));
            }
        }
    }

    /** Faint green motes drawn into the caster's clasped hands while the wood answers. */
    public static void gather(Level level, Vec3 hands) {
        Vfx.burst(level, Vfx.glow(SAP, SAP_DEEP, 0.35f, 0.05f, 8), hands, 3, 0.3, 0.02);
    }
}

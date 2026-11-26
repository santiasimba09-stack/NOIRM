package com.noirm.aburame;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-side particles and sounds for the colony (the swarms' own look is drawn by the client renderer). */
public final class SwarmFx {
    public static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.10f, 0.10f, 0.12f), 0.35f);
    public static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.25f, 0.66f, 0.66f), 0.5f);
    public static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.42f, 0.16f, 0.48f), 0.7f);

    private SwarmFx() {}

    /** A little black cloud drifts off a starving host and dies. */
    public static void leaving(ServerLevel level, Entity host) {
        level.sendParticles(DUST, host.getX(), host.getY() + 1.2, host.getZ(), 24, 0.5, 0.6, 0.5, 0.02);
    }

    /** Insects pour out of (or into) a body. */
    public static void pour(ServerLevel level, Entity host, int amount) {
        int n = Math.min(24, 4 + amount / 600);
        Vec3 at = host.position().add(0, host.getBbHeight() * 0.55, 0).add(host.getViewVector(1f).multiply(1, 0, 1).scale(0.9));
        level.sendParticles(DUST, at.x, at.y, at.z, n, 0.25, 0.3, 0.25, 0.03);
    }

    /** Insects killed in a puff: ash for fire, a dark burst otherwise. */
    public static void deaths(ServerLevel level, Vec3 at, int killed, boolean fire) {
        int n = Math.min(30, 2 + killed / 60);
        level.sendParticles(fire ? ParticleTypes.SMOKE : DUST, at.x, at.y, at.z, n, 0.4, 0.4, 0.4, 0.04);
        if (fire) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, Math.max(1, n / 3), 0.4, 0.4, 0.4, 0.02);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> sound,
                             float volume, float pitch) {
        if (sound.isPresent()) {
            level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
        }
    }
}

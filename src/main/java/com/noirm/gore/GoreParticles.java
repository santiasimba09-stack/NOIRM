package com.noirm.gore;

import com.noirm.Noirm;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blood particles. The server sends them with vanilla's particle packets (no new network message);
 * the client ({@code client/BloodParticle}) turns them into stains on the ground ({@link BloodSplats}).
 * <ul>
 *   <li>{@code noirm:blood_drop}: a falling droplet that leaves a splat where it lands.</li>
 *   <li>{@code noirm:blood_pool}: draws nothing; a pool spreads on the ground under it. Sent with
 *   count 0, so its "velocity" x carries the pool's size ({@link Gore#pool}).</li>
 * </ul>
 */
public final class GoreParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Noirm.MOD_ID);

    public static final RegistryObject<SimpleParticleType> BLOOD_DROP = TYPES.register("blood_drop", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> BLOOD_POOL = TYPES.register("blood_pool", () -> new SimpleParticleType(false));

    private GoreParticles() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}

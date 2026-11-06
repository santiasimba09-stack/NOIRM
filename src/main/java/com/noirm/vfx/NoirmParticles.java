package com.noirm.vfx;

import com.mojang.serialization.Codec;
import com.noirm.Noirm;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Registers one particle type per {@link VfxKind} ({@code noirm:glow}, {@code noirm:spark}…). */
public final class NoirmParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Noirm.MOD_ID);
    private static final Map<VfxKind, RegistryObject<ParticleType<VfxOptions>>> BY_KIND = new EnumMap<>(VfxKind.class);

    static {
        for (VfxKind kind : VfxKind.values()) {
            BY_KIND.put(kind, TYPES.register(kind.name().toLowerCase(Locale.ROOT), () -> new ParticleType<>(true, VfxOptions.DESERIALIZER) {
                private final Codec<VfxOptions> codec = VfxOptions.codec(kind);

                @Override
                public Codec<VfxOptions> codec() {
                    return codec;
                }
            }));
        }
    }

    private NoirmParticles() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static ParticleType<VfxOptions> type(VfxKind kind) {
        return BY_KIND.get(kind).get();
    }
}

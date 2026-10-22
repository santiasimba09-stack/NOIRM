package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.gore.BloodSplats;
import com.noirm.gore.GoreParticles;
import com.noirm.gore.GoreSettings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A drop of blood (see {@code gore/GoreParticles}): it flies, falls heavily, and where it lands
 * on the ground it leaves a splat ({@link BloodSplats}). At the {@code MILD} gore level only half
 * the drops show and none stain; at {@code OFF}, none. The pool "particle" draws nothing: it just
 * spreads a pool of the size the server sent.
 */
public class BloodParticle extends TextureSheetParticle {
    /** Splat size (half-width, blocks) for the smallest and the biggest drop. */
    static final float SPLAT_MIN = 0.07f;
    static final float SPLAT_MAX = 0.2f;

    private final float splat;

    BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx;
        yd = vy + 0.05; // flung up a little before it falls
        zd = vz;
        gravity = 1.1f;
        friction = 0.98f;
        lifetime = 50 + random.nextInt(30);
        float big = random.nextFloat();
        quadSize = 0.025f + 0.04f * big;
        splat = SPLAT_MIN + (SPLAT_MAX - SPLAT_MIN) * big * big;
        hasPhysics = true;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed && onGround) {
            if (GoreSettings.full()) {
                BloodSplats.CLIENT.add(level, x, y, z, splat, false, random);
            }
            remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public record DropProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            if (!GoreSettings.any() || (!GoreSettings.full() && level.random.nextBoolean())) {
                return null;
            }
            return new BloodParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    /** A pool: nothing to draw; {@code vx} is its size. */
    public static final class PoolProvider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            if (GoreSettings.full() && vx > 0) {
                BloodSplats.CLIENT.add(level, x, y, z, (float) vx, true, level.random);
            }
            return null;
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class BloodProviderRegistration {
        private BloodProviderRegistration() {}

        @SubscribeEvent
        public static void registerBloodProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(GoreParticles.BLOOD_DROP.get(), DropProvider::new);
            event.registerSpecial(GoreParticles.BLOOD_POOL.get(), new PoolProvider());
        }
    }
}

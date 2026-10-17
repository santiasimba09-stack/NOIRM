package com.noirm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noirm.Noirm;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Draws NOIRM's particles: colour and size blend from birth to death, glowing kinds add light
 * (additive blending, full-bright) instead of covering what's behind them, smoke grows and fades,
 * shards spin, and rings lie flat on the ground and spread out.
 */
public class VfxParticle extends TextureSheetParticle {
    /** Additive blending: overlapping glows get brighter, like light. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "NOIRM_ADDITIVE";
        }
    };

    private final VfxOptions options;
    private final SpriteSet sprites;
    private final float spin;

    VfxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, VfxOptions options, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.options = options;
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = Math.max(1, options.lifetime());
        this.gravity = options.gravity();
        this.friction = options.drag();
        this.hasPhysics = options.kind() == VfxKind.SHARD;
        this.spin = options.kind() == VfxKind.SHARD ? (random.nextFloat() - 0.5f) * 0.6f : options.kind() == VfxKind.SMOKE ? (random.nextFloat() - 0.5f) * 0.05f : 0f;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        pickSprite(sprites);
        applyLife(0f);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        applyLife(age / (float) lifetime);
    }

    private void applyLife(float t) {
        int c = lerpColor(options.color(), options.endColor(), t);
        rCol = (c >> 16 & 0xFF) / 255f;
        gCol = (c >> 8 & 0xFF) / 255f;
        bCol = (c & 0xFF) / 255f;
        quadSize = Mth.lerp(t, options.size(), options.endSize());
        // Fade in quickly, hold, fade out over the last 40%.
        float fadeIn = Math.min(1f, t * 8f);
        float fadeOut = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
        alpha = Mth.clamp(fadeIn * fadeOut, 0f, 1f) * (options.kind() == VfxKind.SMOKE ? 0.7f : 1f);
    }

    @Override
    public int getLightColor(float partialTick) {
        return options.kind().glows() ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return options.kind().glows() ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        if (options.kind() != VfxKind.RING) {
            super.render(consumer, camera, partialTick);
            return;
        }
        // A ring lies flat (horizontal), so it reads as a shockwave on the ground.
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTick, yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTick, zo, this.z) - cam.z);
        float s = getQuadSize(partialTick);
        Vector3f[] corners = {new Vector3f(-s, 0, -s), new Vector3f(-s, 0, s), new Vector3f(s, 0, s), new Vector3f(s, 0, -s)};
        float[][] uv = {{getU1(), getV1()}, {getU1(), getV0()}, {getU0(), getV0()}, {getU0(), getV1()}};
        int light = getLightColor(partialTick);
        for (int pass = 0; pass < 2; pass++) { // both faces, so it shows from below too
            for (int i = 0; i < 4; i++) {
                int k = pass == 0 ? i : 3 - i;
                Vector3f v = corners[k];
                consumer.vertex(x + v.x(), y + v.y(), z + v.z()).uv(uv[k][0], uv[k][1]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            }
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    /** One provider per kind; each picks from that kind's textures. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<VfxOptions> {
        @Override
        public Particle createParticle(VfxOptions options, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new VfxParticle(level, x, y, z, vx, vy, vz, options, sprites);
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class VfxProviderRegistration {
        private VfxProviderRegistration() {}

        @SubscribeEvent
        public static void registerVfxProviders(RegisterParticleProvidersEvent event) {
            for (VfxKind kind : VfxKind.values()) {
                event.registerSpriteSet(kind.type(), Provider::new);
            }
        }
    }
}

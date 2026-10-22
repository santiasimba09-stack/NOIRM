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

    /**
     * Budget (blueprint §1): NOIRM particles alive at once. Past it, new small ones are skipped;
     * far from the camera most decorative ones are skipped too (level of detail).
     */
    public static final int MAX_LIVE = 4000;
    /** Distances (blocks) for the level of detail: full near, thinned mid, sparse far. */
    private static final double NEAR = 16, FAR = 48;
    private static int live;

    private final VfxOptions options;
    private final SpriteSet sprites;
    private final float spin;
    private final float flutter;

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
        this.spin = switch (options.kind()) {
            case SHARD, ASH -> (random.nextFloat() - 0.5f) * 0.6f;
            case SMOKE -> (random.nextFloat() - 0.5f) * 0.05f;
            case FLAME -> (random.nextFloat() - 0.5f) * 0.08f; // cells churn slowly
            case TONGUE -> 0f;
            default -> 0f;
        };
        // Tongues stand upright (a slight lean); everything else starts at a random angle.
        this.roll = options.kind() == VfxKind.TONGUE ? (random.nextFloat() - 0.5f) * 0.7f : random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.flutter = random.nextFloat() * Mth.TWO_PI;
        pickSprite(sprites);
        applyLife(0f);
        live++;
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        if (options.kind() == VfxKind.ASH) {
            // Ash flutters as it drifts down.
            float phase = flutter + age * 0.35f;
            xd += Mth.cos(phase) * 0.006f;
            zd += Mth.sin(phase) * 0.006f;
        }
        applyLife(age / (float) lifetime);
    }

    @Override
    public void remove() {
        if (!removed) {
            live = Math.max(0, live - 1);
        }
        super.remove();
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
        return options.kind().fullBright() ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return options.kind().glows() ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        if (options.kind() != VfxKind.RING && options.kind() != VfxKind.GROUND_GLOW) {
            super.render(consumer, camera, partialTick);
            return;
        }
        // A ring (or a pool of light) lies flat, so it reads as a shockwave on the ground.
        // Render thread only, so the corner tables are shared instead of allocated per frame.
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTick, yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTick, zo, this.z) - cam.z);
        float s = getQuadSize(partialTick);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTick);
        for (int pass = 0; pass < 2; pass++) { // both faces, so it shows from below too
            for (int i = 0; i < 4; i++) {
                int k = pass == 0 ? i : 3 - i;
                consumer.vertex(x + FLAT_X[k] * s, y, z + FLAT_Z[k] * s).uv(FLAT_U[k] == 0 ? u0 : u1, FLAT_V[k] == 0 ? v0 : v1)
                        .color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            }
        }
    }

    private static final float[] FLAT_X = {-1, -1, 1, 1};
    private static final float[] FLAT_Z = {-1, 1, 1, -1};
    private static final int[] FLAT_U = {1, 1, 0, 0};
    private static final int[] FLAT_V = {1, 0, 0, 1};

    /** NOIRM particles alive right now (for the budget and for tests). */
    public static int live() {
        return live;
    }

    /**
     * Level of detail and the budget: should a new particle of {@code options} at this spot be
     * skipped? Big ones (impacts, flame masses, rings) are always kept; small decorative ones thin
     * out with distance and when the budget runs low.
     */
    static boolean skip(VfxOptions options, double x, double y, double z, net.minecraft.util.RandomSource random) {
        boolean big = options.size() >= 1.2f || options.kind() == VfxKind.RING || options.kind() == VfxKind.GROUND_GLOW;
        if (live >= MAX_LIVE) {
            return !big || live >= MAX_LIVE + 1000;
        }
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        double d2 = camera.getPosition().distanceToSqr(x, y, z);
        // A big soft sprite born right on the camera only paints the screen one flat colour.
        if (options.kind() != VfxKind.RING && options.kind() != VfxKind.GROUND_GLOW) {
            double near = Math.max(options.size(), options.endSize()) * 0.9 + 0.4;
            if (d2 < near * near) {
                return true;
            }
        }
        if (big) {
            return false;
        }
        if (d2 > FAR * FAR) {
            return random.nextInt(10) != 0; // far: one in ten
        }
        if (d2 > NEAR * NEAR) {
            return random.nextInt(10) < 7; // mid: 70% fewer
        }
        // Near, but the budget is filling: thin out gradually from 60% of it.
        return live > MAX_LIVE * 0.6 && random.nextFloat() < (live - MAX_LIVE * 0.6f) / (MAX_LIVE * 0.4f);
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
            if (skip(options, x, y, z, level.random)) {
                return null;
            }
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

    /** The particle engine drops everything when the level changes, without telling each particle. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class VfxBudgetReset {
        private VfxBudgetReset() {}

        @SubscribeEvent
        public static void resetVfxBudget(net.minecraftforge.event.level.LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                live = 0;
            }
        }
    }
}

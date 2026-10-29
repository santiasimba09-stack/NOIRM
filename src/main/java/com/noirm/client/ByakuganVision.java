package com.noirm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noirm.Noirm;
import com.noirm.dojutsu.Byakugan;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the open Byakugan shows: every living thing within {@link Byakugan#SIGHT_RANGE} drawn as a
 * glowing blue chakra silhouette, <b>through walls</b>, plus a pale vignette at the screen edges.
 *
 * <p>How: each entity's normal renderer is run again into a buffer that turns every model quad
 * into one translucent color drawn with depth testing off. Text, shadows and glowing layers
 * (which have no outline version) are dropped.
 */
public final class ByakuganVision {
    private static final int SILHOUETTE_COLOR = 0x6FA8CCFF;
    private static final int HOSTILE_COLOR = 0x6FFF9080;
    /** Chakra points are drawn on bodies this close (further away they'd just be noise). */
    private static final double POINTS_RANGE = 16;
    /** The radar covers this many blocks around you. */
    private static final double RADAR_RANGE = 40;
    /**
     * Where the chakra points (tenketsu) sit on a humanoid, as (sideways, height, forward) for a
     * 0.6 × 1.8 body. Canon has 361; these are the ones big enough to read at a glance.
     */
    private static final float[][] TENKETSU = {
            {0, 1.62f, 0.1f}, {0, 1.45f, 0.13f}, {0.08f, 1.3f, 0.14f}, {0, 1.12f, 0.14f}, {0, 0.95f, 0.13f},
            {0.32f, 1.4f, 0.05f}, {-0.32f, 1.4f, 0.05f}, {0.36f, 1.1f, 0.05f}, {-0.36f, 1.1f, 0.05f},
            {0.36f, 0.82f, 0.05f}, {-0.36f, 0.82f, 0.05f}, {0.12f, 0.85f, 0.1f}, {-0.12f, 0.85f, 0.1f},
            {0.12f, 0.45f, 0.08f}, {-0.12f, 0.45f, 0.08f}, {0.12f, 0.1f, 0.08f}, {-0.12f, 0.1f, 0.08f}};

    private ByakuganVision() {}

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class World {
        private World() {}

        @SubscribeEvent
        public static void render(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !ClientPowers.localByakuganActive()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return;
            }
            Camera camera = event.getCamera();
            Vec3 cam = camera.getPosition();
            float partialTick = event.getPartialTick();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            buffers.endBatch(); // draw anything pending normally first
            AABB area = minecraft.player.getBoundingBox().inflate(Byakugan.SIGHT_RANGE);
            for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != minecraft.player && e.isAlive() && !e.isInvisible())) {
                EntityRenderer<? super LivingEntity> renderer = minecraft.getEntityRenderDispatcher().getRenderer(entity);
                double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - cam.x;
                double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - cam.y;
                double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cam.z;
                float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
                int color = entity instanceof net.minecraft.world.entity.monster.Enemy ? HOSTILE_COLOR : SILHOUETTE_COLOR;
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                try {
                    renderer.render(entity, yaw, partialTick, poseStack, new Silhouettes(buffers, color), 0xF000F0);
                } catch (RuntimeException ignored) {
                    // A modded renderer that can't draw this way just isn't shown.
                }
                if (entity.distanceTo(minecraft.player) <= POINTS_RANGE) {
                    float bodyYaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
                    drawChakraPoints(entity, bodyYaw, camera, poseStack.last().pose(), buffers.getBuffer(XRayType.SILHOUETTE),
                            minecraft.level.getGameTime() + partialTick);
                }
                poseStack.popPose();
            }
            // Vanilla's NO_DEPTH_TEST shard only works if depth testing is already off; mid-level it's on.
            RenderSystem.disableDepthTest();
            buffers.endBatch(XRayType.SILHOUETTE);
            RenderSystem.enableDepthTest();
        }
    }

    /**
     * The chakra points: small bright dots on the body, facing the camera. Humanoids get the full
     * set; anything else just shows its chakra core.
     */
    private static void drawChakraPoints(LivingEntity entity, float bodyYaw, Camera camera, Matrix4f pose, VertexConsumer out, float time) {
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float yaw = bodyYaw * Mth.DEG_TO_RAD;
        float sideX = -Mth.cos(yaw);
        float sideZ = -Mth.sin(yaw);
        float frontX = -Mth.sin(yaw);
        float frontZ = Mth.cos(yaw);
        float height = entity.getBbHeight();
        float width = entity.getBbWidth() / 0.6f;
        boolean humanoid = height > 1.2f && height < 2.6f && entity.getBbWidth() < 1.2f;
        float[][] points = humanoid ? TENKETSU : new float[][]{{0, height / 1.8f * 1.1f, 0}};
        float scale = humanoid ? height / 1.8f : 1f;
        for (int i = 0; i < points.length; i++) {
            float[] p = points[i];
            float x = p[0] * width * sideX + p[2] * width * frontX;
            float y = p[1] * scale;
            float z = p[0] * width * sideZ + p[2] * width * frontZ;
            float pulse = 0.6f + 0.4f * Mth.sin(time * 0.25f + i * 1.7f);
            float size = (humanoid ? 0.045f : 0.18f) * (0.8f + 0.4f * pulse);
            int alpha = (int) (200 * pulse) + 55;
            quad(out, pose, x, y, z, left, up, size, 0xE0, 0xF8, 0xFF, alpha);
        }
    }

    private static void quad(VertexConsumer out, Matrix4f pose, float x, float y, float z, Vector3f left, Vector3f up,
                             float size, int r, int g, int b, int a) {
        float lx = left.x() * size;
        float ly = left.y() * size;
        float lz = left.z() * size;
        float ux = up.x() * size;
        float uy = up.y() * size;
        float uz = up.z() * size;
        out.vertex(pose, x - lx - ux, y - ly - uy, z - lz - uz).color(r, g, b, a).endVertex();
        out.vertex(pose, x - lx + ux, y - ly + uy, z - lz + uz).color(r, g, b, a).endVertex();
        out.vertex(pose, x + lx + ux, y + ly + uy, z + lz + uz).color(r, g, b, a).endVertex();
        out.vertex(pose, x + lx - ux, y + ly - uy, z + lz - uz).color(r, g, b, a).endVertex();
    }

    /**
     * A 360° radar of chakra signatures in the top right: you at the centre, the way you face
     * pointing up. Red = hostile, blue = everyone else; bigger dots = more life force.
     */
    static void renderRadar(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        if (!ClientPowers.localByakuganActive() || minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) {
            return;
        }
        int radius = 34;
        int cx = width - radius - 8;
        int cy = radius + 8;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0x40102030);
        }
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI * 2 / 72;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            graphics.fill(x, y, x + 1, y + 1, 0xA0C8E8FF);
        }
        graphics.fill(cx, cy - radius + 2, cx + 1, cy - radius + 6, 0xA0FFFFFF); // where you face
        float yaw = Mth.lerp(partialTick, minecraft.player.yRotO, minecraft.player.getYRot()) * Mth.DEG_TO_RAD;
        double scale = radius / RADAR_RANGE;
        Vec3 me = minecraft.player.getPosition(partialTick);
        AABB area = minecraft.player.getBoundingBox().inflate(RADAR_RANGE);
        for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != minecraft.player && e.isAlive())) {
            Vec3 at = entity.getPosition(partialTick);
            double dx = at.x - me.x;
            double dz = at.z - me.z;
            double forward = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
            double right = dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw);
            if (forward * forward + right * right > RADAR_RANGE * RADAR_RANGE) {
                continue;
            }
            int x = cx - (int) Math.round(right * scale);
            int y = cy - (int) Math.round(forward * scale);
            int size = entity.getMaxHealth() >= 60 ? 3 : entity.getMaxHealth() >= 20 ? 2 : 1;
            int color = entity instanceof net.minecraft.world.entity.monster.Enemy ? 0xFFFF6A5A : 0xFF8FD4FF;
            graphics.fill(x - size, y - size, x + size + 1, y + size + 1, color);
        }
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
    }

    /** Pale veins at the screen edges while the eye is open. */
    static void renderVignette(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!ClientPowers.localByakuganActive() || gui.getMinecraft().options.hideGui) {
            return;
        }
        int color = 0x50D8D0FF;
        int edge = Math.max(24, width / 12);
        graphics.fillGradient(0, 0, width, edge, color, 0);
        graphics.fillGradient(0, height - edge, width, height, 0, color);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class VignetteRegistration {
        private VignetteRegistration() {}

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("byakugan_vignette", ByakuganVision::renderVignette);
            event.registerAboveAll("byakugan_radar", ByakuganVision::renderRadar);
        }
    }

    /** Sends every model quad to the see-through silhouette type, in one color; drops the rest. */
    private record Silhouettes(MultiBufferSource.BufferSource buffers, int color) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (type.outline().isEmpty()) {
                return NoVertices.INSTANCE;
            }
            return new Tinted(buffers.getBuffer(XRayType.SILHOUETTE), color);
        }
    }

    /** Keeps only position and forces the color (the silhouette format has nothing else). */
    private static final class Tinted implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int r;
        private final int g;
        private final int b;
        private final int a;

        Tinted(VertexConsumer delegate, int argb) {
            this.delegate = delegate;
            this.a = argb >>> 24;
            this.r = argb >> 16 & 0xFF;
            this.g = argb >> 8 & 0xFF;
            this.b = argb & 0xFF;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z).color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private static final class NoVertices implements VertexConsumer {
        static final NoVertices INSTANCE = new NoVertices();

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    /** A translucent, no-depth-test, color-only render type. Subclassing gives access to the shard constants. */
    private static final class XRayType extends RenderType {
        static final RenderType SILHOUETTE = RenderType.create("noirm_byakugan_silhouette", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private XRayType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                         Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }
}

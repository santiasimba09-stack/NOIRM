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
                poseStack.popPose();
            }
            // Vanilla's NO_DEPTH_TEST shard only works if depth testing is already off; mid-level it's on.
            RenderSystem.disableDepthTest();
            buffers.endBatch(XRayType.SILHOUETTE);
            RenderSystem.enableDepthTest();
        }
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

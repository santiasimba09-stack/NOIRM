package com.noirm.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Drawing helpers shared by the space-time effects and the genjutsu scenes (client only):
 * billboards, light ribbons, and <b>silhouettes</b>: an entity drawn with its real model and pose
 * but in one flat colour, used for afterimages (additive light) and ink phantoms (dark, see-through).
 */
public final class SpaceTimeDraw {
    public static final ResourceLocation WHITE = Noirm.id("textures/misc/spacetime_white.png");
    public static final ResourceLocation GLOW = Noirm.id("textures/misc/spacetime_glow.png");
    public static final ResourceLocation GLYPH = Noirm.id("textures/misc/hiraishin_glyph.png");
    public static final ResourceLocation CROW = Noirm.id("textures/misc/genjutsu_crow.png");
    public static final ResourceLocation EYE = Noirm.id("textures/misc/genjutsu_eye.png");

    /** Set while a silhouette is being drawn, so render events don't recurse into it. */
    static boolean drawingSilhouette;

    private SpaceTimeDraw() {}

    /**
     * Draws {@code entity}'s model at {@code at} (world position) in one colour: additive light
     * ({@code glow}) or a see-through flat ink. {@code yaw} is ignored by living renderers (they use the
     * entity's own body turn).
     */
    public static void silhouette(Entity entity, Vec3 at, Vec3 cam, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                  float partial, int rgb, float alpha, boolean glow) {
        if (entity == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) minecraft.getEntityRenderDispatcher().getRenderer(entity);
        RenderType type = glow ? RenderType.eyes(WHITE) : RenderType.entityTranslucent(WHITE);
        float r = (rgb >> 16 & 0xFF) / 255f * (glow ? alpha : 1f);
        float g = (rgb >> 8 & 0xFF) / 255f * (glow ? alpha : 1f);
        float b = (rgb & 0xFF) / 255f * (glow ? alpha : 1f);
        MultiBufferSource tinted = rt -> rt.format() == DefaultVertexFormat.NEW_ENTITY
                ? new Tint(buffers.getBuffer(type), r, g, b, glow ? 1f : alpha) : NOOP;
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        boolean was = drawingSilhouette;
        drawingSilhouette = true;
        try {
            renderer.render(entity, entity.getYRot(), partial, pose, tinted, LightTexture.FULL_BRIGHT);
        } catch (RuntimeException e) {
            // A renderer that can't be drawn this way is simply skipped.
        } finally {
            drawingSilhouette = was;
        }
        pose.popPose();
        buffers.endBatch(type);
    }

    /** Draws {@code entity} as itself (its normal look) at another spot: the caster's copies in a scene. */
    public static void copy(Entity entity, Vec3 at, Vec3 cam, PoseStack pose, MultiBufferSource.BufferSource buffers, float partial, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) minecraft.getEntityRenderDispatcher().getRenderer(entity);
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        boolean was = drawingSilhouette;
        drawingSilhouette = true;
        try {
            renderer.render(entity, entity.getYRot(), partial, pose, buffers, light);
        } catch (RuntimeException e) {
            // skipped
        } finally {
            drawingSilhouette = was;
        }
        pose.popPose();
    }

    /** A camera-facing square of {@code size} at {@code at} with {@code type}'s texture (whole texture, or a u range). */
    public static void billboard(VertexConsumer out, Matrix4f matrix, Vec3 at, Vec3 cam, Vector3f right, Vector3f up, float size,
                                 int rgb, float alpha, float u0, float u1) {
        float x = (float) (at.x - cam.x), y = (float) (at.y - cam.y), z = (float) (at.z - cam.z);
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float h = size / 2;
        vertex(out, matrix, x - right.x() * h - up.x() * h, y - right.y() * h - up.y() * h, z - right.z() * h - up.z() * h, r, g, b, alpha, u0, 1);
        vertex(out, matrix, x + right.x() * h - up.x() * h, y + right.y() * h - up.y() * h, z + right.z() * h - up.z() * h, r, g, b, alpha, u1, 1);
        vertex(out, matrix, x + right.x() * h + up.x() * h, y + right.y() * h + up.y() * h, z + right.z() * h + up.z() * h, r, g, b, alpha, u1, 0);
        vertex(out, matrix, x - right.x() * h + up.x() * h, y - right.y() * h + up.y() * h, z - right.z() * h + up.z() * h, r, g, b, alpha, u0, 0);
    }

    /** One vertex in the entity format. */
    public static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        out.vertex(matrix, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
    }

    /**
     * A flat ribbon of light from {@code a} to {@code b}, turned to face the camera, {@code width} wide,
     * in the entity format with a soft glow texture across it.
     */
    public static void ribbon(VertexConsumer out, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 cam, float width, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid)).normalize().scale(width / 2);
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, bl = (rgb & 0xFF) / 255f;
        Vec3 p1 = a.add(side).subtract(cam), p2 = b.add(side).subtract(cam), p3 = b.subtract(side).subtract(cam), p4 = a.subtract(side).subtract(cam);
        vertex(out, matrix, (float) p1.x, (float) p1.y, (float) p1.z, r, g, bl, alpha, 0.5f, 0);
        vertex(out, matrix, (float) p2.x, (float) p2.y, (float) p2.z, r, g, bl, alpha, 0.5f, 0);
        vertex(out, matrix, (float) p3.x, (float) p3.y, (float) p3.z, r, g, bl, alpha, 0.5f, 1);
        vertex(out, matrix, (float) p4.x, (float) p4.y, (float) p4.z, r, g, bl, alpha, 0.5f, 1);
    }

    /** A vertex consumer that discards everything (text and glints in a silhouette). */
    static final VertexConsumer NOOP = new VertexConsumer() {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
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
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    };

    /** Passes vertices on with one colour, full light and no overlay. */
    static final class Tint implements VertexConsumer {
        private final VertexConsumer out;
        private final int r, g, b, a;

        Tint(VertexConsumer out, float r, float g, float b, float a) {
            this.out = out;
            this.r = Math.round(Math.min(1f, r) * 255);
            this.g = Math.round(Math.min(1f, g) * 255);
            this.b = Math.round(Math.min(1f, b) * 255);
            this.a = Math.round(Math.min(1f, a) * 255);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            out.color(r, g, b, alpha < 8 ? 0 : a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            out.overlayCoords(OverlayTexture.NO_OVERLAY);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            out.uv2(LightTexture.FULL_BRIGHT);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            out.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            out.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            out.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }
}

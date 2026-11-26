package com.noirm.client.aburame;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noirm.Noirm;
import com.noirm.aburame.GiantInsect;
import com.noirm.aburame.InsectClone;
import com.noirm.aburame.Species;
import com.noirm.aburame.WildInsect;
import com.noirm.aburame.WildKind;
import com.noirm.client.ShadowCloneRenderer;
import com.noirm.entity.SparringNinja;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renderers for the Aburame's other entities: the insect clone, the Giant Insect and wild insects (all our own voxel art). */
public final class AburameRenderers {
    public static final ResourceLocation GIANT = Noirm.id("textures/entity/aburame/giant_insect.png");
    public static final ResourceLocation WILD = Noirm.id("textures/entity/aburame/wild.png");

    private AburameRenderers() {}

    /** An insect clone: your look, with beetles crawling all over it (it is made of them). */
    public static class Clone extends ShadowCloneRenderer {
        public Clone(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(SparringNinja ninja, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
            super.render(ninja, yaw, partial, stack, buffers, light);
            BeetleMesh mesh = new BeetleMesh(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(BeetleMesh.texture(Species.KIKAICHU))), light);
            float t = (ninja.tickCount + partial) / 20f;
            int n = ninja instanceof InsectClone c && c.isLegion() ? 160 : 220;
            float w = ninja.getBbWidth() * 0.55f, h = ninja.getBbHeight();
            for (int i = 0; i < n; i++) {
                float h1 = InsectSwarmRenderer.hash(i, 1), h2 = InsectSwarmRenderer.hash(i, 2), h3 = InsectSwarmRenderer.hash(i, 3);
                float y = h2 * h;
                float a = h1 * Mth.TWO_PI + t * (0.5f + h3) * (h3 < 0.5f ? 1 : -1);
                float a2 = a + 0.08f;
                float rad = w * (0.95f + 0.1f * Mth.sin(t * 6 + i));
                mesh.beetle(Mth.cos(a) * rad, y, Mth.sin(a) * rad, Mth.cos(a2) * rad - Mth.cos(a) * rad, 0.01f,
                        Mth.sin(a2) * rad - Mth.sin(a) * rad, 0.08f, ((i + ninja.tickCount) & 3) == 0);
            }
        }
    }

    /** The Giant Insect: one big voxel beetle, red, yellow and black, its wings beating when it flies. */
    public static class Giant extends EntityRenderer<GiantInsect> {
        public Giant(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 1.2f;
        }

        @Override
        public ResourceLocation getTextureLocation(GiantInsect entity) {
            return GIANT;
        }

        @Override
        public void render(GiantInsect bug, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
            stack.pushPose();
            float rot = Mth.rotLerp(partial, bug.yRotO, bug.getYRot()) * Mth.DEG_TO_RAD;
            float fx = -Mth.sin(rot), fz = Mth.cos(rot);
            BeetleMesh mesh = new BeetleMesh(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(GIANT)), light);
            boolean flying = !bug.onGround();
            boolean flap = flying && (bug.tickCount & 1) == 0;
            float bob = flying ? Mth.sin((bug.tickCount + partial) * 0.3f) * 0.08f : 0f;
            mesh.beetle(0, 0.75f + bob, 0, fx, 0, fz, 2.8f, flap || !flying && (bug.tickCount / 20) % 7 == 0);
            // legs: six little beetle-boxes pointing down and out
            for (int i = 0; i < 6; i++) {
                float side = i < 3 ? 1 : -1;
                float along = (i % 3 - 1) * 0.55f;
                float rx = -fz * side, rz = fx * side;
                float x = fx * along + rx * 0.75f, z = fz * along + rz * 0.75f;
                mesh.beetle(x, 0.35f + bob, z, rx, -0.9f, rz, 0.55f, false);
            }
            stack.popPose();
            super.render(bug, yaw, partial, stack, buffers, light);
        }
    }

    /** A wild insect: a tiny bug in its own colours; fireflies (and the bikōchū) glow. */
    public static class Wild extends EntityRenderer<WildInsect> {
        public Wild(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0f;
        }

        @Override
        public ResourceLocation getTextureLocation(WildInsect entity) {
            return WILD;
        }

        @Override
        public void render(WildInsect bug, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
            WildKind kind = bug.kind();
            int lit = kind.glows && ((bug.tickCount / 6) % 3 != 0) ? LightTexture.FULL_BRIGHT : light;
            BeetleMesh mesh = new BeetleMesh(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(WILD)), lit);
            mesh.tint(((kind.body >> 16) & 255) / 255f * 1.6f, ((kind.body >> 8) & 255) / 255f * 1.6f, (kind.body & 255) / 255f * 1.6f);
            float rot = Mth.rotLerp(partial, bug.yRotO, bug.getYRot()) * Mth.DEG_TO_RAD;
            float len = switch (kind) {
                case STAG_BEETLE, MANTIS, LOCUST -> 0.32f;
                case SNOW_FLEA, FIREFLY -> 0.14f;
                default -> 0.22f;
            };
            mesh.beetle(0, len * 0.3f, 0, -Mth.sin(rot), 0, Mth.cos(rot), len, bug.flies() && (bug.tickCount & 1) == 0);
            super.render(bug, yaw, partial, stack, buffers, light);
        }
    }
}

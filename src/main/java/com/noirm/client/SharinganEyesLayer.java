package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Glowing irises while a dōjutsu is active (players and ninjas): red Sharingan, pale Byakugan. */
public class SharinganEyesLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final RenderType EYES = RenderType.eyes(Noirm.id("textures/entity/sharingan_eyes.png"));
    private static final RenderType BYAKUGAN = RenderType.eyes(Noirm.id("textures/entity/byakugan_eyes.png"));
    private static final int FULL_BRIGHT = 0xF000F0;

    public SharinganEyesLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        int stage = entity instanceof SparringNinja ninja ? ninja.activeSharinganStage() : ClientPowers.eyeStage(entity.getId());
        boolean byakugan = entity instanceof SparringNinja ninja ? ninja.byakuganActive() : ClientPowers.byakugan(entity.getId());
        if (stage <= 0 && !byakugan) {
            return;
        }
        int mangekyo = entity instanceof SparringNinja ninja ? ninja.mangekyoLook() : ClientPowers.mangekyoLook(entity.getId());
        poseStack.pushPose();
        if (stage > 0 && mangekyo != 0) {
            drawMangekyo(poseStack, buffers, com.noirm.dojutsu.Mangekyo.lookPattern(mangekyo), com.noirm.dojutsu.Mangekyo.lookDonor(mangekyo),
                    ageInTicks);
        } else {
            getParentModel().head.render(poseStack, buffers.getBuffer(stage > 0 ? EYES : BYAKUGAN), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    private static final RenderType PATTERNS = RenderType.entityCutoutNoCull(MangekyoEffects.PATTERNS);
    /** Eye centres on the face, in model pixels (the 1-pixel eyes of the skin at columns 2 and 5, row 4). */
    private static final float EYE_X = 1.5f;
    private static final float EYE_Y = -3.5f;
    /** Each Mangekyō eye is drawn a little bigger than the skin's 1-pixel eye, so the pattern reads. */
    private static final float EYE_SIZE = 1.3f;

    /**
     * The Mangekyō: each eye is a small quad on the front of the head showing the pattern (the
     * Eternal one draws the donor's pattern over it, turned, so the two merge). Turns slowly.
     */
    private void drawMangekyo(PoseStack poseStack, MultiBufferSource buffers, int pattern, int donor, float ageInTicks) {
        getParentModel().head.translateAndRotate(poseStack);
        com.mojang.blaze3d.vertex.VertexConsumer consumer = buffers.getBuffer(PATTERNS);
        float spin = ageInTicks * 1.5f;
        for (int side = -1; side <= 1; side += 2) {
            quad(poseStack, consumer, side * EYE_X, EYE_Y, -4.02f, pattern, spin);
            if (donor >= 0) {
                quad(poseStack, consumer, side * EYE_X, EYE_Y, -4.03f, donor, 45f - spin);
            }
        }
    }

    private static void quad(PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, float x, float y, float z, int pattern, float degrees) {
        poseStack.pushPose();
        poseStack.translate(x / 16f, y / 16f, z / 16f);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        org.joml.Matrix4f pose = poseStack.last().pose();
        org.joml.Matrix3f normal = poseStack.last().normal();
        float h = EYE_SIZE / 32f;
        int cells = com.noirm.dojutsu.Mangekyo.PATTERNS;
        float u0 = Math.floorMod(pattern, cells) / (float) cells;
        float u1 = u0 + 1f / cells;
        vertex(consumer, pose, normal, -h, -h, u0, 0f);
        vertex(consumer, pose, normal, -h, h, u0, 1f);
        vertex(consumer, pose, normal, h, h, u1, 1f);
        vertex(consumer, pose, normal, h, -h, u1, 0f);
        poseStack.popPose();
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer consumer, org.joml.Matrix4f pose, org.joml.Matrix3f normal,
                               float x, float y, float u, float v) {
        consumer.vertex(pose, x, y, 0f).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT).normal(normal, 0f, 0f, -1f).endVertex();
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<?, ?> renderer = event.getSkin(skin);
                if (renderer instanceof PlayerRenderer playerRenderer) {
                    playerRenderer.addLayer(new SharinganEyesLayer<>(playerRenderer));
                }
            }
        }
    }
}

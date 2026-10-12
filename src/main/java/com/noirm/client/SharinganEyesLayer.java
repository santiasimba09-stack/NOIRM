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
        if (stage > 0) {
            getParentModel().head.render(poseStack, buffers.getBuffer(EYES), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else if (byakugan) {
            getParentModel().head.render(poseStack, buffers.getBuffer(BYAKUGAN), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
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

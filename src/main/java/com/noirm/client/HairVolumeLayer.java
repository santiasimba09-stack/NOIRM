package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noirm.creator.PlayerLook;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Hair volume from the creation screen: the head's outer layer (where the hair is painted) is
 * drawn once more, a little bigger (+6% per step), around the neck, so fuller hair stands out
 * further from the head and higher on top.
 */
public class HairVolumeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public HairVolumeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        PlayerLook look = PlayerSkins.lookFor(player);
        if (look == null || look.hairVolumeScale() <= 1f || look.id(PlayerLook.Slot.HAIR).equals("none")) {
            return;
        }
        ModelPart hat = getParentModel().hat;
        if (!hat.visible) {
            return;
        }
        float x = hat.xScale;
        float y = hat.yScale;
        float z = hat.zScale;
        float scale = look.hairVolumeScale();
        hat.xScale = x * scale;
        hat.yScale = y * scale;
        hat.zScale = z * scale;
        hat.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(player))), light,
                LivingEntityRenderer.getOverlayCoords(player, 0f));
        hat.xScale = x;
        hat.yScale = y;
        hat.zScale = z;
    }
}

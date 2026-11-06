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
 * drawn once more, a little bigger (+4% per step), around the middle of the head, so fuller hair
 * stands out further all round.
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
        // Grow around the middle of the head (4 px above the neck pivot), not the neck itself.
        org.joml.Vector3f centre = new org.joml.Quaternionf().rotationZYX(hat.zRot, hat.yRot, hat.xRot)
                .transform(new org.joml.Vector3f(0f, -4f, 0f)).mul(1f - scale);
        float px = hat.x;
        float py = hat.y;
        float pz = hat.z;
        hat.x += centre.x;
        hat.y += centre.y;
        hat.z += centre.z;
        hat.xScale = x * scale;
        hat.yScale = y * scale;
        hat.zScale = z * scale;
        hat.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(player))), light,
                LivingEntityRenderer.getOverlayCoords(player, 0f));
        hat.xScale = x;
        hat.yScale = y;
        hat.zScale = z;
        hat.x = px;
        hat.y = py;
        hat.z = pz;
    }
}

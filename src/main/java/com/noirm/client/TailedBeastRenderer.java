package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.entity.TailedBeast;
import net.minecraft.client.model.HoglinModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder art until we have an artist: the vanilla zoglin, scaled up with the beast's tails
 * (see {@link TailedBeast#scaleFor}). A subdued beast lies on its side.
 */
public class TailedBeastRenderer extends MobRenderer<TailedBeast, HoglinModel<TailedBeast>> {
    private static final ResourceLocation TEXTURE = Noirm.vanillaId("textures/entity/hoglin/zoglin.png");

    public TailedBeastRenderer(EntityRendererProvider.Context context) {
        super(context, new HoglinModel<>(context.bakeLayer(ModelLayers.HOGLIN)), 0.9f);
    }

    @Override
    public ResourceLocation getTextureLocation(TailedBeast beast) {
        return TEXTURE;
    }

    @Override
    protected void scale(TailedBeast beast, PoseStack poseStack, float partialTick) {
        float scale = beast.scale();
        poseStack.scale(scale, scale, scale);
        shadowRadius = 0.9f * scale;
    }

    @Override
    protected void setupRotations(TailedBeast beast, PoseStack poseStack, float ageInTicks, float bodyYaw, float partialTick) {
        super.setupRotations(beast, poseStack, ageInTicks, bodyYaw, partialTick);
        if (beast.isSubdued()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(75f));
            poseStack.translate(0.3, -0.2, 0);
        }
    }
}

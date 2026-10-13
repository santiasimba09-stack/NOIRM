package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the sparring ninja with the vanilla player model and skin (placeholder art), posed by
 * its combat state so attacks are readable: arm cocked back = light wind-up, both arms raised =
 * heavy wind-up, arms crossed = guard.
 */
public class SparringNinjaRenderer extends HumanoidMobRenderer<SparringNinja, PlayerModel<SparringNinja>> {
    /** One of vanilla's default player skins. */
    private static final ResourceLocation TEXTURE = Noirm.vanillaId("textures/entity/player/wide/kai.png");

    public SparringNinjaRenderer(EntityRendererProvider.Context context) {
        super(context, new NinjaModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new SharinganEyesLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(SparringNinja ninja) {
        String outfit = ninja.outfit();
        return outfit.isEmpty() ? TEXTURE : OUTFITS.computeIfAbsent(outfit, o -> Noirm.id("textures/entity/ninja/" + o + ".png"));
    }

    private static final java.util.Map<String, ResourceLocation> OUTFITS = new java.util.HashMap<>();

    static final class NinjaModel extends PlayerModel<SparringNinja> {
        NinjaModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(SparringNinja ninja, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(ninja, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            switch (ninja.ninjaPose()) {
                case WINDUP_LIGHT -> {
                    rightArm.xRot = -1.9f;
                    rightArm.yRot = -0.4f;
                    body.yRot = 0.35f;
                }
                case WINDUP_HEAVY -> {
                    rightArm.xRot = -2.7f;
                    leftArm.xRot = -2.7f;
                    body.xRot = -0.15f;
                }
                case ACTIVE -> {
                    rightArm.xRot = -1.55f;
                    rightArm.yRot = 0.1f;
                    leftArm.xRot = 0.4f;
                    body.yRot = -0.3f;
                }
                case GUARD -> {
                    rightArm.xRot = -1.3f;
                    rightArm.yRot = -0.6f;
                    leftArm.xRot = -1.3f;
                    leftArm.yRot = 0.6f;
                }
                case STAGGER -> {
                    head.xRot = 0.5f;
                    body.xRot = 0.25f;
                    rightArm.xRot = 0.3f;
                    leftArm.xRot = 0.3f;
                }
                case IDLE -> { }
            }
            // The outer skin layers follow the parts they cover.
            hat.copyFrom(head);
            jacket.copyFrom(body);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }
}

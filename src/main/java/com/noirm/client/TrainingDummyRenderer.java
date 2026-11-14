package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.entity.TrainingDummy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the training dummy as a straw humanoid with its arms held out (placeholder art: a
 * generated burlap texture on the vanilla zombie-shaped model). It wobbles when hit.
 */
public class TrainingDummyRenderer extends HumanoidMobRenderer<TrainingDummy, HumanoidModel<TrainingDummy>> {
    private static final ResourceLocation TEXTURE = Noirm.id("textures/entity/training_dummy.png");

    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new DummyModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingDummy dummy) {
        return TEXTURE;
    }

    static final class DummyModel extends HumanoidModel<TrainingDummy> {
        DummyModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(TrainingDummy dummy, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(dummy, 0, 0, ageInTicks, 0, 0);
            float hurt = dummy.ticksSinceHit() > 0 ? dummy.ticksSinceHit() - (ageInTicks - (int) ageInTicks) : 0;
            float wobble = hurt > 0 ? Mth.sin(hurt * 1.4f) * hurt * 0.025f : 0;
            head.xRot = wobble * 1.5f;
            head.yRot = 0;
            body.xRot = wobble;
            rightArm.xRot = 0;
            leftArm.xRot = 0;
            rightArm.yRot = 0;
            leftArm.yRot = 0;
            rightArm.zRot = 1.45f + wobble;
            leftArm.zRot = -1.45f - wobble;
            rightLeg.xRot = 0;
            leftLeg.xRot = 0;
            rightLeg.zRot = 0.03f;
            leftLeg.zRot = -0.03f;
            hat.copyFrom(head);
        }
    }
}

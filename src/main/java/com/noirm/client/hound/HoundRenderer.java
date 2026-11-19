package com.noirm.client.hound;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.hound.HoundGrowth;
import com.noirm.hound.NinjaHound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a ninja hound: its own body (breed, ears, tail, built for its level), its own coat (mixed
 * from its look), at its size; plus the whole-body moves the model can't do alone: lying on its side
 * when knocked out, the Fang Over Fang drill (spinning round its own length), and where a carried
 * puppy sits on you.
 */
public class HoundRenderer extends MobRenderer<NinjaHound, HoundModel> {
    /** Set while the hound panel draws the hound (then it shows even on your own head). */
    public static boolean inGui;
    private static final ResourceLocation MISSING = Noirm.id("textures/entity/hound/fur_young.png");

    public HoundRenderer(EntityRendererProvider.Context context) {
        super(context, new HoundModel(), 0.3f);
    }

    @Override
    public ResourceLocation getTextureLocation(NinjaHound hound) {
        if (!HoundModels.loaded()) {
            return MISSING;
        }
        return HoundTextures.get(hound.lookBits(), HoundGrowth.furAge(hound.houndLevel()));
    }

    @Override
    public void render(NinjaHound hound, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (hound.isHidden()) {
            return; // it's wearing your shape (Man Beast Clone)
        }
        Minecraft mc = Minecraft.getInstance();
        if (!inGui && hound.isCarried() && hound.carry() == NinjaHound.Carry.HEAD && hound.getVehicle() == mc.getCameraEntity()
                && mc.options.getCameraType().isFirstPerson()) {
            return; // on your own head: out of your view in first person
        }
        shadowRadius = hound.isCarried() ? 0f : Math.max(0.2f, hound.getBbWidth() * 0.6f);
        pose.pushPose();
        if (hound.isCarried() && hound.carry() == NinjaHound.Carry.JACKET) {
            // Low in the jacket's front: the body hides inside your torso, the head pokes out at the collar.
            pose.translate(0, -0.12, 0);
        }
        super.render(hound, yaw, partialTick, pose, buffers, light);
        pose.popPose();
    }

    @Override
    protected void scale(NinjaHound hound, PoseStack pose, float partialTick) {
        HoundModels.Rig rig = HoundModel.rigFor(hound);
        float s = rig == null ? 1f : HoundModels.scale(rig, hound.houndLevel(), hound.size(), hound.isFused());
        pose.scale(s, s, s);
    }

    @Override
    protected void setupRotations(NinjaHound hound, PoseStack pose, float age, float bodyYaw, float partialTick) {
        super.setupRotations(hound, pose, age, bodyYaw, partialTick);
        if (hound.isDown()) {
            // Knocked out: flat on its side.
            pose.translate(hound.getBbHeight() * 0.42f, hound.getBbWidth() * 0.22f, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(90f));
        } else if (hound.clientSpin > 0) {
            // Fang Over Fang: a drill turning round its own length, nose first.
            float h = hound.getBbHeight() * 0.5f;
            pose.translate(0, h, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(age * 62f));
            pose.translate(0, -h, 0);
        }
    }

    @Override
    protected float getFlipDegrees(NinjaHound hound) {
        return 0f; // it never dies: no death flip
    }
}

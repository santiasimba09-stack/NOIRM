package com.noirm.client.hound;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.hound.Hounds;
import com.noirm.hound.NinjaHound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a ninja hound: its stage's model at the stage's size, its coat, and the whole-body moves
 * the model can't do alone: lying on its side when knocked out, the Fang Over Fang drill (spinning
 * round its own length), and where a carried puppy sits on you.
 */
public class HoundRenderer extends MobRenderer<NinjaHound, HoundModel> {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    public HoundRenderer(EntityRendererProvider.Context context) {
        super(context, new HoundModel(), 0.3f);
    }

    /** {@code textures/entity/hound/<plan>/<stage>_<coat>.png}; the comparison plans only come in white. */
    public static ResourceLocation texture(int build, String stage, int coat, boolean fused) {
        String plan = HoundModels.plan(build);
        String path;
        if (fused) {
            path = HoundModels.chosenPlan() + "/fused";
        } else {
            String c = plan.equals(HoundModels.chosenPlan()) ? Hounds.COATS[Math.floorMod(coat, Hounds.COATS.length)] : "white";
            path = plan + "/" + stage + "_" + c;
        }
        return TEXTURES.computeIfAbsent(path, p -> Noirm.id("textures/entity/hound/" + p + ".png"));
    }

    @Override
    public ResourceLocation getTextureLocation(NinjaHound hound) {
        return texture(hound.build(), hound.stage().key(), hound.coat(), hound.isFused());
    }

    @Override
    public void render(NinjaHound hound, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (hound.isHidden()) {
            return; // it's wearing your shape (Man Beast Clone)
        }
        Minecraft mc = Minecraft.getInstance();
        if (hound.isCarried() && hound.carry() == NinjaHound.Carry.HEAD && hound.getVehicle() == mc.getCameraEntity()
                && mc.options.getCameraType().isFirstPerson()) {
            return; // on your own head: out of your view in first person
        }
        HoundModels.Rig rig = HoundModel.rigFor(hound);
        float scale = rig == null ? 1f : rig.scale;
        shadowRadius = hound.isCarried() ? 0f : 0.35f * Math.max(0.6f, hound.getBbWidth() / 0.6f) * 0.8f;
        pose.pushPose();
        if (hound.isCarried() && hound.carry() == NinjaHound.Carry.JACKET) {
            // Low in the jacket's front: the body hides inside your torso, the head pokes out at the collar.
            pose.translate(0, -0.42 * scale, 0);
        }
        super.render(hound, yaw, partialTick, pose, buffers, light);
        pose.popPose();
    }

    @Override
    protected void scale(NinjaHound hound, PoseStack pose, float partialTick) {
        HoundModels.Rig rig = HoundModel.rigFor(hound);
        float s = rig == null ? 1f : rig.scale;
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

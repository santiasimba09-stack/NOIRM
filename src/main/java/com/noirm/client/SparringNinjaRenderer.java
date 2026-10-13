package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.client.anim.NpcAnimator;
import com.noirm.client.anim.SkeletonPose;
import com.noirm.client.anim.SkeletonPose.Bone;
import com.noirm.entity.SparringNinja;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the sparring ninja with a jointed player-skin model ({@link BendyNinjaModel}: elbows,
 * knees and a waist) that plays the very same keyframe animations as the player
 * ({@link NpcAnimator}): the style's stance in a fight, each move's animation at the server's
 * timing, rolls on dodges, a guard and a hit flinch, all cross-faded.
 * Its skin is built from its look ({@link NinjaSkins}), with any worn gear on top.
 */
public class SparringNinjaRenderer extends HumanoidMobRenderer<SparringNinja, PlayerModel<SparringNinja>> {
    /** One of vanilla's default player skins. */
    private static final ResourceLocation TEXTURE = Noirm.vanillaId("textures/entity/player/wide/kai.png");

    public SparringNinjaRenderer(EntityRendererProvider.Context context) {
        super(context, new BendyNinjaModel(), 0.5f);
        addLayer(new SharinganEyesLayer<>(this));
        // What their leader dresses them in.
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(SparringNinja ninja) {
        String look = ninja.outfit();
        return look.isEmpty() ? TEXTURE : NinjaSkins.texture(look); // built from their look key
    }

    /**
     * The animation's {@code body} bone moves the whole character, exactly like PlayerAnimator does
     * for players: offset, then roll / yaw / pitch around a point 0.7 blocks above the feet.
     */
    @Override
    protected void setupRotations(SparringNinja ninja, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(ninja, poseStack, ageInTicks, rotationYaw, partialTicks);
        SkeletonPose body = NpcAnimator.stateFor(ninja, ageInTicks).bodyOnly;
        if (!body.isSet(Bone.BODY, SkeletonPose.RX) && !body.isSet(Bone.BODY, SkeletonPose.PX)) {
            return;
        }
        poseStack.translate(body.get(Bone.BODY, SkeletonPose.PX), body.get(Bone.BODY, SkeletonPose.PY) + 0.7f,
                body.get(Bone.BODY, SkeletonPose.PZ));
        poseStack.mulPose(Axis.ZP.rotation(body.get(Bone.BODY, SkeletonPose.RZ)));
        poseStack.mulPose(Axis.YP.rotation(body.get(Bone.BODY, SkeletonPose.RY)));
        poseStack.mulPose(Axis.XP.rotation(body.get(Bone.BODY, SkeletonPose.RX)));
        poseStack.translate(0, -0.7f, 0);
    }
}

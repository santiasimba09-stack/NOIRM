package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.client.anim.HumanoidAnimator;
import com.noirm.client.anim.WeaponStances;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-person arms that move like the third-person body: while you fight (combat mode, a move,
 * a guard) your view shows the model's real arms, seen from your eyes, so the stance's fists, a
 * jab, a palm strike or a sword cut look the same as from outside. Outside a fight, and when using
 * items (eating, bows), vanilla's hands are kept.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonArms {
    /** Below this, vanilla hands are drawn. */
    private static final float SHOW = 0.15f;

    private FirstPersonArms() {}

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.options.getCameraType().isFirstPerson() || player.isUsingItem() || player.isScoping()
                || player.isSpectator() || player.isSleeping() || player.isSwimming() || player.isInvisible()) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && WeaponStances.kind(main) == null) {
            return; // tools, blocks, food: vanilla hands
        }
        float fight = Math.max(HumanoidAnimator.actionWeight(player),
                ClientCombat.isCombatMode() ? HumanoidAnimator.stanceWeight(player) : 0f);
        if (ClientCombat.isGuarding()) {
            fight = 1f;
        }
        if (fight < SHOW) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return; // both arms are drawn once, with the main hand
        }
        NoirmPlayerRenderer renderer = NoirmPlayerRenderer.Swap.rendererFor(player);
        if (renderer == null || !(renderer.getModel() instanceof BendyModel<?> raw)) {
            return;
        }
        @SuppressWarnings("unchecked")
        BendyModel<net.minecraft.client.player.AbstractClientPlayer> model = (BendyModel<net.minecraft.client.player.AbstractClientPlayer>) raw;
        float partial = event.getPartialTick();
        pose(model, player, partial);
        draw(model, player, partial, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    /** Runs the full animation for this frame, facing where the camera faces. */
    private static void pose(BendyModel<net.minecraft.client.player.AbstractClientPlayer> model, LocalPlayer player, float partial) {
        model.attackTime = player.getAttackAnim(partial);
        model.riding = player.isPassenger();
        model.young = false;
        model.crouching = player.isCrouching();
        model.rightArmPose = player.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = player.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        float age = player.tickCount + partial;
        model.prepareMobModel(player, player.walkAnimation.position(partial), player.walkAnimation.speed(partial), partial);
        model.setupAnim(player, player.walkAnimation.position(partial), player.walkAnimation.speed(partial), age, 0f, 0f);
    }

    private static void draw(BendyModel<net.minecraft.client.player.AbstractClientPlayer> model, LocalPlayer player, float partial,
                             PoseStack poseStack, MultiBufferSource buffers, int light) {
        float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        poseStack.pushPose();
        // Camera space → the player's model space (the camera looks where the body faces).
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.translate(0, -player.getEyeHeight(), 0);
        // (The camera's yaw and the body's yaw cancel out: the body faces where the camera faces.)
        NoirmPlayerRenderer.applyBody(HumanoidAnimator.bodyTransform(player, player.tickCount + partial,
                player.walkAnimation.position(partial), player.walkAnimation.speed(partial)), poseStack);
        poseStack.scale(-1f, -1f, 1f);
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
        poseStack.translate(0, -1.501f, 0);
        RenderType type = RenderType.entityTranslucent(player.getSkinTextureLocation());
        model.rightArm.render(poseStack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY);
        model.leftArm.render(poseStack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY);
        item(model, player, HumanoidArm.RIGHT, player.getMainHandItem(), poseStack, buffers, light);
        item(model, player, HumanoidArm.LEFT, player.getOffhandItem(), poseStack, buffers, light);
        poseStack.popPose();
    }

    /** A held item in the model's hand, placed like vanilla's third-person item layer. */
    private static void item(BendyModel<?> model, LocalPlayer player, HumanoidArm arm, ItemStack stack, PoseStack poseStack,
                             MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) {
            return;
        }
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.pushPose();
        model.translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, poseStack, buffers, light);
        poseStack.popPose();
    }
}

package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.client.anim.HumanoidAnimator;
import com.noirm.client.anim.SkeletonPose;
import com.noirm.client.anim.SkeletonPose.Bone;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Players drawn with NOIRM's jointed model ({@link BendyModel}) and animation engine
 * ({@link HumanoidAnimator}), the same as NPC ninjas: elbows, knees and a waist, the torso
 * carrying the arms, our gait and ninja run, stances, moves, flinches and springs. It replaces
 * the vanilla player renderer at draw time (the vanilla one is still used for first-person hands).
 */
public class NoirmPlayerRenderer extends PlayerRenderer {
    public NoirmPlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, slim);
        this.model = new BendyModel<>(slim);
        // Armor that bends at the elbows, knees and waist like the body under it.
        this.layers.removeIf(layer -> layer instanceof net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<?, ?, ?>);
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new BendyArmorModel<>(0.5f), new BendyArmorModel<>(1.0f), context.getModelManager()));
        addLayer(new SharinganEyesLayer<>(this));
        addLayer(new WeaponTrails.Layer<>(this)); // blade arcs through the air
        addLayer(new GoreMarksLayer<>(this)); // bruises, dirt, soot, cuts and blood
        addLayer(new GoreArmorLayer<>(this)); // and on worn clothes
        addLayer(new HairVolumeLayer(this)); // character creation: fuller hair
    }

    /** Character creation height (0.85-1.15x); the hitbox follows on both sides ({@code creator.CreatorEvents}). */
    @Override
    protected void scale(AbstractClientPlayer player, PoseStack poseStack, float partialTick) {
        super.scale(player, poseStack, partialTick);
        float height = PlayerSkins.renderHeight(player);
        if (height != 1f) {
            poseStack.scale(height, height, height);
        }
    }

    /** The animation's {@code body} bone moves the whole character (lunges, spins, bob, lean). */
    @Override
    protected void setupRotations(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(player, poseStack, ageInTicks, rotationYaw, partialTicks);
        applyBody(HumanoidAnimator.bodyTransform(player, ageInTicks, player.walkAnimation.position(partialTicks),
                player.walkAnimation.speed(partialTicks)), poseStack);
    }

    /** Offset, then roll / yaw / pitch around a point 0.7 blocks above the feet (as PlayerAnimator did). */
    static void applyBody(SkeletonPose body, PoseStack poseStack) {
        if (!body.isSet(Bone.BODY, SkeletonPose.RX) && !body.isSet(Bone.BODY, SkeletonPose.PX)
                && !body.isSet(Bone.BODY, SkeletonPose.PY) && !body.isSet(Bone.BODY, SkeletonPose.RZ)) {
            return;
        }
        poseStack.translate(body.get(Bone.BODY, SkeletonPose.PX), body.get(Bone.BODY, SkeletonPose.PY) + 0.7f,
                body.get(Bone.BODY, SkeletonPose.PZ));
        poseStack.mulPose(Axis.ZP.rotation(body.get(Bone.BODY, SkeletonPose.RZ)));
        poseStack.mulPose(Axis.YP.rotation(body.get(Bone.BODY, SkeletonPose.RY)));
        poseStack.mulPose(Axis.XP.rotation(body.get(Bone.BODY, SkeletonPose.RX)));
        poseStack.translate(0, -0.7f, 0);
    }

    /** Swaps our renderer in for every player drawn in the world (and in the inventory). */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class Swap {
        private static NoirmPlayerRenderer wide;
        private static NoirmPlayerRenderer slim;
        private static boolean drawing;

        private Swap() {}

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void renderPlayer(RenderPlayerEvent.Pre event) {
            if (drawing || wide == null || event.getRenderer() instanceof NoirmPlayerRenderer
                    || !(event.getEntity() instanceof AbstractClientPlayer player)) {
                return;
            }
            NoirmPlayerRenderer renderer = "slim".equals(player.getModelName()) ? slim : wide;
            event.setCanceled(true);
            drawing = true;
            try {
                float partial = event.getPartialTick();
                renderer.render(player, Mth.lerp(partial, player.yRotO, player.getYRot()), partial,
                        event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
            } finally {
                drawing = false;
            }
        }

        /** Our renderer for this player's skin type (null before the renderers are built). */
        public static NoirmPlayerRenderer rendererFor(AbstractClientPlayer player) {
            return "slim".equals(player.getModelName()) ? slim : wide;
        }

        static void create(EntityRendererProvider.Context context) {
            wide = new NoirmPlayerRenderer(context, false);
            slim = new NoirmPlayerRenderer(context, true);
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class PlayerRendererSetup {
        private PlayerRendererSetup() {}

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            Swap.create(event.getContext());
        }
    }
}

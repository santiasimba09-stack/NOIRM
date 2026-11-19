package com.noirm.client;

import com.noirm.creator.PlayerLook;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Body types from the creator ({@link PlayerLook.Slot#BODY}): slim, average, athletic, broad. The build's
 * shading is a skin part ({@code player_parts/build_*}); this adds the small width change: the torso a
 * little narrower or wider and the arms thinner or thicker, set apart to match. Armor still fits (it's
 * drawn inflated around the same parts). Called once per model pose, after the animation.
 */
public final class BodyShape {
    /** Per body type (PlayerLook's BODY order): torso width, torso depth, arm thickness, leg thickness. */
    private static final float[][] SCALE = {
            {1f, 1f, 1f, 1f},             // average
            {0.93f, 0.94f, 0.9f, 1f},     // slim
            {1.04f, 1.03f, 1.05f, 1f},    // athletic
            {1.09f, 1.07f, 1.12f, 1f},    // broad
            {0.95f, 0.95f, 0.92f, 0.96f}, // lean
            {0.92f, 0.92f, 0.86f, 0.92f}, // wiry
            {1.08f, 1.1f, 1.1f, 1.08f},   // stocky
            {1.12f, 1.18f, 1.1f, 1.1f},   // heavy
            {1.15f, 1.1f, 1.2f, 1.06f},   // hulking
    };

    private BodyShape() {}

    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        int type = 0;
        if (entity instanceof Player player) {
            PlayerLook look = PlayerSkins.lookFor(player);
            if (look != null) {
                type = look.get(PlayerLook.Slot.BODY);
            }
        }
        float[] s = SCALE[Math.max(0, Math.min(SCALE.length - 1, type))];
        // the model is shared by everyone drawn with it: always set the scales, even back to 1
        model.body.xScale = s[0];
        model.body.zScale = s[1];
        model.rightArm.xScale = s[2];
        model.rightArm.zScale = s[2];
        model.leftArm.xScale = s[2];
        model.leftArm.zScale = s[2];
        model.rightLeg.xScale = s[3];
        model.rightLeg.zScale = s[3];
        model.leftLeg.xScale = s[3];
        model.leftLeg.zScale = s[3];
        if (type != 0) {
            float out = (s[0] - 1f) * 4f + (s[2] - 1f) * 2f; // the shoulders move with the torso's edge
            model.rightArm.x -= out;
            model.leftArm.x += out;
        }
    }
}

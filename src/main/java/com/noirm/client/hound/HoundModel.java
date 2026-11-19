package com.noirm.client.hound;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.hound.HoundStage;
import com.noirm.hound.NinjaHound;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The ninja hound's animated model. It wears whichever baked {@link HoundModels.Rig} fits the hound
 * right now (its body plan, its stage, or the Two-Headed Wolf) and poses the parts by name:
 * <ul>
 *   <li><b>walk</b>: a trot on diagonal pairs; <b>run</b>: a gallop (front pair, then back pair),
 *   the spine flexing and the body rocking. Bigger hounds take slower, longer strides.</li>
 *   <li><b>sit</b>: the body tips up on the hips until the front paws touch, the hind legs fold
 *   flat under it; <b>howl</b>: sitting, muzzle to the sky, jaw open.</li>
 *   <li><b>lunge</b> (a bite), <b>sniff</b> (nose to the ground), <b>spin</b> (legs tucked; the renderer
 *   turns it into a drill), <b>knocked out</b> (limp, on its side), <b>carried</b> (lying on your head or
 *   peeking out of your jacket).</li>
 *   <li>always: the head follows what it looks at, breathing, panting, a wagging tail (more when the
 *   bond is strong), ears pinned back in a fight.</li>
 * </ul>
 */
public class HoundModel extends EntityModel<NinjaHound> {
    /** Stride frequency per stage: a puppy patters, a great hound lopes. */
    private static final float[] STRIDE = {0.95f, 0.78f, 0.64f, 0.52f, 0.44f};
    private static final float DEG = Mth.DEG_TO_RAD;

    private HoundModels.Rig rig;
    private float tintR = 1f;
    private float tintG = 1f;
    private float tintB = 1f;

    public HoundModels.Rig rig() {
        return rig;
    }

    /** Picks the rig for this hound (null while the models haven't loaded). */
    public static HoundModels.Rig rigFor(NinjaHound hound) {
        return HoundModels.get(hound.build(), hound.isFused() ? "fused" : hound.stage().key());
    }

    @Override
    public void prepareMobModel(NinjaHound hound, float limbSwing, float amount, float partialTick) {
        rig = rigFor(hound);
        if (hound.isFrenzied()) {
            // A soldier pill: the fur flushes red while the frenzy lasts.
            tintR = 1f;
            tintG = 0.62f;
            tintB = 0.58f;
        } else if (hound.isDown()) {
            tintR = tintG = tintB = 0.72f;
        } else {
            tintR = tintG = tintB = 1f;
        }
    }

    @Override
    public void setupAnim(NinjaHound hound, float limbSwing, float amount, float age, float headYaw, float headPitch) {
        if (rig == null) {
            return;
        }
        rig.root.getAllParts().forEach(ModelPart::resetPose);
        float partial = age - hound.tickCount;
        HoundStage stage = hound.stage();
        boolean down = hound.isDown();
        NinjaHound.Carry carry = hound.isCarried() ? hound.carry() : NinjaHound.Carry.NONE;

        if (down) {
            poseDown(age);
            return;
        }
        if (carry == NinjaHound.Carry.HEAD) {
            poseOnHead(age, headYaw, headPitch);
            return;
        }
        if (carry == NinjaHound.Carry.JACKET) {
            poseInJacket(age, headYaw, headPitch);
            return;
        }
        if (hound.clientSpin > 0) {
            poseSpin(age);
            return;
        }

        float a = Mth.clamp(amount * 1.5f, 0f, 1f);
        boolean ridden = hound.isVehicle() && hound.getControllingPassenger() != null;
        // Gallop from a brisk pace (and always when ridden hard or chasing).
        float gallop = Mth.clamp((amount - 0.42f) / 0.25f, 0f, 1f);
        if (ridden || hound.isAggressive()) {
            gallop = Math.max(gallop, Mth.clamp(amount * 2f, 0f, 1f));
        }
        float freq = STRIDE[stage.ordinal()] * (hound.isFused() ? 0.85f : 1f);
        float p = limbSwing * freq;

        boolean sitting = hound.isInSittingPose() && a < 0.1f;
        float howl = envelope(hound.clientHowl, 50, partial, 6);
        if (sitting || howl > 0f) {
            poseSit(Math.max(sitting ? 1f : 0f, howl));
        } else {
            gait(p, a, gallop);
        }

        // Head and neck follow the look.
        float yaw = Mth.clamp(headYaw, -70f, 70f) * DEG;
        float pitch = Mth.clamp(headPitch, -40f, 40f) * DEG;
        add("neck", 0, yaw * 0.35f, 0);
        add("head", pitch * 0.8f, yaw * 0.65f, 0);
        add("neck2", 0, yaw * 0.25f, 0);
        add("head2", pitch * 0.6f, yaw * 0.4f + Mth.sin(age * 0.05f) * 0.15f, 0);

        // Breathing, panting.
        float breathe = Mth.sin(age * 0.09f) * 0.12f;
        offset("chest", 0, breathe, 0);
        boolean fight = hound.isAggressive() || hound.getTarget() != null;
        float pant = a > 0.25f || fight ? 0.14f + 0.07f * Mth.sin(age * 0.95f) : 0.03f + 0.02f * Mth.sin(age * 0.2f);
        add("jaw", pant, 0, 0);
        add("jaw2", pant * 0.8f, 0, 0);

        // Ears: pinned back in a fight or a gallop, a bounce in the stride.
        float pin = Math.max(fight ? 0.55f : 0f, gallop * 0.4f);
        for (String ear : new String[]{"ear_l", "ear_r", "ear2_l", "ear2_r"}) {
            add(ear, pin, 0, 0);
            add(ear + "_tip", Mth.sin(p * 2f) * 0.12f * a, 0, 0);
        }

        // Tail: up and stiff in a fight, streaming in a run, a happy wag otherwise (more with a strong bond).
        float wag = fight ? 0.06f : (0.25f + 0.35f * hound.bond() / 100f) * (stage == HoundStage.PUPPY ? 1.4f : 1f) * (1f - 0.6f * gallop);
        float wagSpeed = stage == HoundStage.PUPPY ? 0.9f : 0.6f;
        add("tail", fight ? -0.55f : -0.35f * gallop, Mth.sin(age * wagSpeed) * wag, 0);
        add("tail2", 0, Mth.sin(age * wagSpeed - 0.6f) * wag * 0.6f, 0);
        add("tail3", 0, Mth.sin(age * wagSpeed - 1.2f) * wag * 0.4f, 0);

        // Short moves on top.
        float lunge = envelope(hound.clientLunge, 10, partial, 3);
        if (lunge > 0f) {
            poseLunge(lunge);
        }
        if (howl > 0f) {
            add("neck", -1.05f * howl, 0, 0);
            add("head", -0.45f * howl, 0, 0);
            add("jaw", 0.45f * howl, 0, 0);
            add("neck2", -0.9f * howl, 0, 0);
            add("head2", -0.4f * howl, 0, 0);
            add("jaw2", 0.4f * howl, 0, 0);
        }
        float sniff = envelope(hound.clientSniff, 30, partial, 5);
        if (sniff > 0f) {
            add("neck", 0.75f * sniff, 0, 0);
            add("head", 0.35f * sniff, Mth.sin(age * 0.8f) * 0.25f * sniff, 0);
            add("snout", Mth.sin(age * 2.6f) * 0.05f * sniff, 0, 0);
        }
    }

    // ================================================================ poses

    /** Trot (diagonal pairs) blending into a gallop (front pair, then back pair). */
    private void gait(float p, float a, float g) {
        float frOff = Mth.lerp(g, Mth.PI, 0.45f);
        float blOff = Mth.PI;
        float brOff = Mth.lerp(g, 0f, Mth.PI + 0.45f);
        float front = Mth.lerp(g, 0.62f, 0.95f) * a;
        float back = Mth.lerp(g, 0.55f, 0.85f) * a;
        legFront("leg_fl", p, front);
        legFront("leg_fr", p + frOff, front);
        legBack("leg_bl", p + blOff, back);
        legBack("leg_br", p + brOff, back);
        if (g > 0f) {
            // The spine flexes and the body rocks over each bound.
            float rock = Mth.sin(p) * g * a;
            add("body", rock * 0.1f, 0, 0);
            add("chest", -rock * 0.08f, 0, 0);
            add("hips", rock * 0.1f, 0, 0);
            add("neck", -rock * 0.12f, 0, 0);
            add("neck2", -rock * 0.12f, 0, 0);
            offset("body", 0, -Math.abs(Mth.cos(p)) * 0.7f * g * a, 0);
        } else {
            offset("body", 0, -Math.abs(Mth.sin(p)) * 0.25f * a, 0);
        }
    }

    private void legFront(String leg, float phase, float amp) {
        add(leg, Mth.cos(phase) * amp, 0, 0);
        // The forearm folds back as the leg comes forward (lifting the paw).
        add(leg + "_lower", Math.max(0f, Mth.sin(phase)) * amp * 1.1f, 0, 0);
    }

    private void legBack(String leg, float phase, float amp) {
        add(leg, Mth.cos(phase) * amp, 0, 0);
        float lift = Math.max(0f, Mth.sin(phase)) * amp;
        add(leg + "_lower", lift * 0.7f, 0, 0);
        add(leg + "_foot", -lift * 0.6f, 0, 0);
    }

    /**
     * Sitting: the body tips up on the hip joint until the front paws reach the ground again, the
     * hind legs fold flat (thigh forward, shin back, the hock and foot on the ground).
     */
    private void poseSit(float k) {
        float hipTarget = 0.5f * (rig.thigh + rig.shin) + 0.6f;
        float drop = Math.max(0f, rig.hipHeight - hipTarget);
        float theta = (float) Math.asin(Mth.clamp(drop / rig.bodyLength, 0f, 0.82f));
        offset("body", 0, drop * k, 0);
        add("body", -theta * k, 0, 0);
        add("leg_fl", theta * k, 0, 0);
        add("leg_fr", theta * k, 0, 0);
        // Hind legs: thigh to 60° forward, shin folded back, foot flat forward.
        float thigh = (60f - 22f) * DEG - theta;
        for (String side : new String[]{"l", "r"}) {
            add("leg_b" + side, -thigh * k, 0, 0);
            add("leg_b" + side + "_lower", 72f * DEG * k, 0, 0);
            add("leg_b" + side + "_foot", -124f * DEG * k, 0, 0);
            add("paw_b" + side, 90f * DEG * k, 0, 0);
        }
        // Head stays level; the tail lies on the ground behind.
        add("head", theta * k * 0.8f, 0, 0);
        add("head2", theta * k * 0.8f, 0, 0);
        add("tail", theta * k + 0.5f * k, 0, 0);
    }

    private void poseLunge(float k) {
        add("body", 0.18f * k, 0, 0);
        add("leg_fl", -1.0f * k, 0, 0);
        add("leg_fr", -0.85f * k, 0, 0);
        add("leg_fl_lower", -0.2f * k, 0, 0);
        add("leg_fr_lower", -0.2f * k, 0, 0);
        add("leg_bl", 0.75f * k, 0, 0);
        add("leg_br", 0.65f * k, 0, 0);
        add("neck", 0.35f * k, 0, 0);
        add("head", -0.2f * k, 0, 0);
        add("jaw", 0.65f * k, 0, 0);
        add("neck2", 0.3f * k, 0, 0);
        add("jaw2", 0.6f * k, 0, 0);
    }

    /** Fang Over Fang: curled tight (legs tucked, tail wrapped) while the renderer spins it. */
    private void poseSpin(float age) {
        for (String side : new String[]{"l", "r"}) {
            add("leg_f" + side, -1.25f, 0, 0);
            add("leg_f" + side + "_lower", 1.7f, 0, 0);
            add("leg_b" + side, 1.0f, 0, 0);
            add("leg_b" + side + "_lower", 0.9f, 0, 0);
        }
        add("neck", 0.25f, 0, 0);
        add("jaw", 0.55f, 0, 0);
        add("jaw2", 0.55f, 0, 0);
        add("tail", 0.9f, 0, 0);
        add("ear_l", 0.7f, 0, 0);
        add("ear_r", 0.7f, 0, 0);
    }

    /** Knocked out: limp on its side (the renderer lays it down), head on the ground, tail flat. */
    private void poseDown(float age) {
        add("leg_fl", -0.25f, 0, 0);
        add("leg_fr", -0.1f, 0, 0);
        add("leg_bl", 0.2f, 0, 0);
        add("leg_br", 0.35f, 0, 0);
        add("neck", 0.55f, 0, 0);
        add("head", 0.25f, 0, 0);
        add("jaw", 0.12f, 0, 0);
        add("tail", 0.6f, 0, 0);
        add("ear_l", 0.6f, 0, 0);
        add("ear_r", 0.6f, 0, 0);
        offset("chest", 0, Mth.sin(age * 0.06f) * 0.15f, 0); // slow, shallow breaths
    }

    /** A puppy lying on your head: belly down, legs splayed out front and back, looking around. */
    private void poseOnHead(float age, float headYaw, float headPitch) {
        offset("body", 0, rig.hipHeight * 0.72f, 0);
        for (String side : new String[]{"l", "r"}) {
            add("leg_f" + side, -1.45f, 0, side.equals("l") ? -0.15f : 0.15f);
            add("leg_b" + side, 102f * DEG, 0, side.equals("l") ? -0.2f : 0.2f);
            add("leg_b" + side + "_lower", -48f * DEG, 0, 0);
            add("leg_b" + side + "_foot", 26f * DEG, 0, 0);
        }
        add("neck", -0.35f, Mth.clamp(headYaw, -50f, 50f) * DEG * 0.4f, 0);
        add("head", Mth.clamp(headPitch, -30f, 30f) * DEG * 0.5f + 0.2f, Mth.clamp(headYaw, -50f, 50f) * DEG * 0.5f, 0);
        add("tail", 0.6f, Mth.sin(age * 0.8f) * 0.4f, 0);
        add("jaw", 0.08f + 0.05f * Mth.sin(age * 0.9f), 0, 0);
    }

    /** A puppy tucked in your jacket: upright, only the head and front paws over the collar. */
    private void poseInJacket(float age, float headYaw, float headPitch) {
        float up = 78f * DEG;
        add("body", -up, 0, 0);
        add("leg_fl", up - 0.9f, 0, -0.1f);
        add("leg_fr", up - 0.9f, 0, 0.1f);
        add("leg_fl_lower", 0.9f, 0, 0);
        add("leg_fr_lower", 0.9f, 0, 0);
        add("neck", up * 0.35f, Mth.clamp(headYaw, -50f, 50f) * DEG * 0.4f, 0);
        add("head", up * 0.65f + Mth.clamp(headPitch, -30f, 30f) * DEG * 0.4f, Mth.clamp(headYaw, -50f, 50f) * DEG * 0.5f, 0);
        add("jaw", 0.06f + 0.05f * Mth.sin(age * 0.9f), 0, 0);
        for (String ear : new String[]{"ear_l", "ear_r"}) {
            add(ear + "_tip", Mth.sin(age * 0.3f) * 0.1f, 0, 0);
        }
        // What's inside the jacket stays hidden.
        hide("hips");
        hide("leg_bl");
        hide("leg_br");
        hide("tail");
    }

    // ================================================================ helpers

    /** 0..1: rises over {@code ease} ticks after the event, holds, and falls over the last {@code ease} ticks. */
    private static float envelope(int left, int total, float partial, int ease) {
        if (left <= 0) {
            return 0f;
        }
        float t = left - partial; // ticks remaining
        float since = total - t;
        float k = Math.min(1f, Math.min(since / ease, t / ease));
        return Mth.clamp(k, 0f, 1f);
    }

    private void add(String name, float x, float y, float z) {
        ModelPart part = rig.part(name);
        if (part != null) {
            part.xRot += x;
            part.yRot += y;
            part.zRot += z;
        }
    }

    private void offset(String name, float x, float y, float z) {
        ModelPart part = rig.part(name);
        if (part != null) {
            part.x += x;
            part.y += y;
            part.z += z;
        }
    }

    private void hide(String name) {
        ModelPart part = rig.part(name);
        if (part != null) {
            part.visible = false;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float alpha) {
        if (rig == null) {
            return;
        }
        rig.root.render(pose, buffer, light, overlay, r * tintR, g * tintG, b * tintB, alpha);
        // Hidden parts come back for the next frame (resetPose doesn't touch visibility).
        rig.root.getAllParts().forEach(part -> part.visible = true);
    }
}

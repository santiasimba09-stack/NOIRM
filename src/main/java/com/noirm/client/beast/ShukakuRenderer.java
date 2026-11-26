package com.noirm.client.beast;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.client.model.BakedBbModel;
import com.noirm.client.model.BbModels;
import com.noirm.client.model.CreatureRig;
import com.noirm.entity.TailedBeast;
import com.noirm.entity.TailedBeast.Mood;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shukaku, the One-Tail (docs/entities/shukaku.md): our Blockbench model {@code models/bb/shukaku.bbmodel} (drawn by
 * {@code tools/beastgen/gen_shukaku.py}) brought to life in layers, every frame:
 * mood posture → walk (planted feet, body bob) → breathing, blinks, ear flicks → look → action (Blockbench clips
 * "drum" and "laugh", the rest procedural) → springs on the tail, ears and belly → hurt rock. Then the colour pass
 * and the glowing eyes. The server decides the mood and the action; this only animates them.
 */
public final class ShukakuRenderer extends EntityRenderer<TailedBeast> {
    public static final ResourceLocation MODEL = Noirm.id("shukaku");
    public static final ResourceLocation TEXTURE = Noirm.id("textures/entity/beast/shukaku.png");
    public static final ResourceLocation GLOW = Noirm.id("textures/entity/beast/shukaku_glow.png");
    /** Tail springs (docs/TUNING.md): stiffer = snappier tail, more damping = less wobble. */
    static final float TAIL_STIFFNESS = 45f, TAIL_DAMPING = 8f;
    private static final float EYE_H = 3.4f;
    private static final String[] TAIL = {"tail1", "tail2", "tail3", "tail4", "tail5", "tail6"};

    private BakedBbModel cachedModel;
    private BakedBbModel.Part body, belly, chest, head, jaw, brow, lidL, lidR, earL, earR, earTipL, earTipR, armL, armR,
            forearmL, forearmR, handL, handR, legL, legR, shinL, shinR, footL, footR;
    private final BakedBbModel.Part[] tail = new BakedBbModel.Part[TAIL.length];

    public ShukakuRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(TailedBeast beast) {
        return TEXTURE;
    }

    /** The shadow size for the current beast (the dispatching renderer copies it). */
    public float shadowRadius() {
        return shadowRadius;
    }

    /** Draws Shukaku; false if its model isn't loaded (the caller falls back to the placeholder). */
    public boolean renderBeast(TailedBeast beast, float entityYaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        BakedBbModel model = BbModels.get(MODEL);
        if (model == null) {
            return false;
        }
        bind(model);
        ShukakuMotion m = ShukakuMotion.of(beast);
        float ticks = beast.tickCount + partial;
        float t = ticks / 20f;
        float dt = m.frameDelta(ticks);
        float scale = beast.scale();
        int lod = CreatureRig.lod(beast, scale);
        shadowRadius = 0.9f * scale;

        stack.pushPose();
        float bodyYaw = Mth.rotLerp(partial, beast.yBodyRotO, beast.yBodyRot);
        stack.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        if (beast.deathTime > 0) {
            float d = Math.min(1f, (beast.deathTime + partial - 1f) / 20f * 1.6f);
            stack.mulPose(Axis.XP.rotationDegrees(-80f * Mth.sqrt(d))); // topples backwards
        } else if (beast.mood() == Mood.SUBDUED) {
            stack.translate(0.6 * scale, 0.25 * scale, 0);
            stack.mulPose(Axis.ZP.rotationDegrees(80f)); // on its side
        }
        float scaleIn = beast.tickCount < 8 ? scaleIn((beast.tickCount + partial) / 8f) : 1f; // the summoning poof
        stack.scale(scale * scaleIn, scale * scaleIn, scale * scaleIn);

        model.resetPose();
        posture(beast, m, dt);
        walk(beast, m, partial, scale);
        idleLife(beast, m, t, lod);
        look(beast, m, partial, dt, bodyYaw);
        action(model, beast, m, partial, t, dt);
        secondary(beast, m, t, dt, bodyYaw, lod);
        reactions(beast, m, dt);

        int overlay = LivingEntityRenderer.getOverlayCoords(beast, 0f);
        model.render(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, 1f, 1f, 1f, 1f);
        float glow = m.glow * (lod < 2 ? 0.85f + 0.15f * Mth.sin(t * (beast.mood() == Mood.ANGRY || beast.mood() == Mood.MANIC ? 9f : 2.5f)) : 1f);
        if (glow > 0.02f) {
            model.render(stack, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, overlay, glow, glow, glow, 1f);
        }
        stack.popPose();
        super.render(beast, entityYaw, partial, stack, buffers, light); // the name tag
        return true;
    }

    private static float scaleIn(float x) {
        return x < 0.7f ? 0.6f + 0.45f * CreatureRig.smooth(x / 0.7f) : 1.05f - 0.05f * CreatureRig.smooth((x - 0.7f) / 0.3f);
    }

    private void bind(BakedBbModel model) {
        if (model == cachedModel) {
            return;
        }
        cachedModel = model;
        body = model.part("body"); belly = model.part("belly"); chest = model.part("chest"); head = model.part("head");
        jaw = model.part("jaw"); brow = model.part("brow"); lidL = model.part("lid_l"); lidR = model.part("lid_r");
        earL = model.part("ear_l"); earR = model.part("ear_r"); earTipL = model.part("ear_l_tip"); earTipR = model.part("ear_r_tip");
        armL = model.part("arm_l"); armR = model.part("arm_r"); forearmL = model.part("forearm_l"); forearmR = model.part("forearm_r");
        handL = model.part("hand_l"); handR = model.part("hand_r"); legL = model.part("leg_l"); legR = model.part("leg_r");
        shinL = model.part("shin_l"); shinR = model.part("shin_r"); footL = model.part("foot_l"); footR = model.part("foot_r");
        for (int i = 0; i < TAIL.length; i++) {
            tail[i] = model.part(TAIL[i]);
        }
    }

    // ---------------------------------------------------------------- 1. mood posture (blended, never snapped)

    /** lean +forward, headDown +down, tailLift +up, earBack +back, spread +arms out, eyes 0..1, brow +down, grin jaw. */
    private record Posture(float lean, float headDown, float tailLift, float earBack, float spread, float eyeOpen, float brow,
                           float grin, float breathRate, float breathAmp, float glow) {}

    private static final Posture CALM = new Posture(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0.25f, 1f, 0.7f);
    private static final Posture ALERT = new Posture(-0.05f, -0.1f, 0.15f, -0.15f, 0.05f, 1f, 0f, 0f, 0.35f, 1f, 0.85f);
    private static final Posture ANGRY = new Posture(0.32f, 0.22f, 0.25f, 0.6f, 0.35f, 0.7f, 1f, 0.08f, 0.6f, 1.2f, 1f);
    private static final Posture MANIC = new Posture(-0.08f, -0.15f, 0.4f, -0.1f, 0.3f, 1f, 0.3f, 0.3f, 0.55f, 1.2f, 1f);
    private static final Posture TIRED = new Posture(0.28f, 0.35f, -0.3f, 0.5f, -0.05f, 0.45f, 0f, 0.05f, 0.18f, 1.7f, 0.4f);
    private static final Posture SUBDUED = new Posture(0.1f, 0.2f, -0.4f, 0.6f, -0.1f, 0f, 0f, 0.1f, 0.12f, 1.5f, 0f);

    private static Posture postureFor(Mood mood) {
        return switch (mood) {
            case ALERT -> ALERT;
            case ANGRY -> ANGRY;
            case MANIC -> MANIC;
            case TIRED -> TIRED;
            case SUBDUED -> SUBDUED;
            default -> CALM;
        };
    }

    private void posture(TailedBeast beast, ShukakuMotion m, float dt) {
        Posture p = postureFor(beast.mood());
        float tau = 0.35f;
        m.lean = CreatureRig.approach(m.lean, p.lean, dt, tau);
        m.headDown = CreatureRig.approach(m.headDown, p.headDown, dt, tau);
        m.tailLift = CreatureRig.approach(m.tailLift, p.tailLift, dt, 0.5f);
        m.earBack = CreatureRig.approach(m.earBack, p.earBack, dt, 0.25f);
        m.spread = CreatureRig.approach(m.spread, p.spread, dt, tau);
        m.eyeOpen = CreatureRig.approach(m.eyeOpen, p.eyeOpen, dt, 0.3f);
        m.brow = CreatureRig.approach(m.brow, p.brow, dt, 0.25f);
        m.grin = CreatureRig.approach(m.grin, p.grin, dt, 0.3f);
        m.breathRate = CreatureRig.approach(m.breathRate, p.breathRate, dt, 1f);
        m.breathAmp = CreatureRig.approach(m.breathAmp, p.breathAmp, dt, 1f);
        m.glow = CreatureRig.approach(m.glow, p.glow, dt, 0.4f);

        // Part rotations: +x tips a part's top backwards, swings a hanging limb forward, lowers a backward chain (the
        // tail) and raises a forward one (head, jaw): so "lean forward" is -body.xRot, "head down" -head.xRot and
        // "jaw open" -jaw.xRot.
        body.xRot -= m.lean;
        head.xRot -= m.headDown - m.lean * 0.6f; // the head stays level-ish while the body leans
        brow.y -= m.brow * 0.6f;
        brow.xRot -= m.brow * 0.25f;
        jaw.xRot -= m.grin;
        armR.zRot += m.spread;
        armL.zRot -= m.spread;
        earL.xRot += m.earBack;
        earR.xRot += m.earBack;
    }

    // ---------------------------------------------------------------- 2. walk: planted feet, body bob, swinging arms

    private void walk(TailedBeast beast, ShukakuMotion m, float partial, float scale) {
        float speed = Math.min(1f, beast.walkAnimation.speed(partial));
        if (speed < 0.01f) {
            return;
        }
        // driven by the distance walked, not by time, so the feet keep in step with the ground; big = slower stride
        float phase = beast.walkAnimation.position(partial) * 0.55f / Mth.sqrt(Math.max(0.5f, scale));
        float s = Mth.sin(phase), c = Mth.cos(phase);
        float stride = 0.55f * speed;
        legL.xRot += s * stride;
        legR.xRot -= s * stride;
        // the swinging leg folds its knee to lift the foot; the stance leg stays straight
        shinL.xRot -= Math.max(0f, c) * 0.8f * speed;
        shinR.xRot -= Math.max(0f, -c) * 0.8f * speed;
        footL.xRot -= legL.xRot + shinL.xRot; // the foot stays level
        footR.xRot -= legR.xRot + shinR.xRot;
        // weight: down on each footfall, roll toward the stance side; the head counter-bobs to stay steady
        float bob = Math.abs(c);
        body.y -= bob * 1.2f * speed;
        body.zRot += s * 0.07f * speed;
        head.y += bob * 0.5f * speed;
        head.zRot -= s * 0.05f * speed;
        armL.xRot -= s * 0.35f * speed;
        armR.xRot += s * 0.35f * speed;
        m.lastBob = bob * speed;
    }

    // ---------------------------------------------------------------- 3. idle life: breathing, blinks, ear flicks

    private void idleLife(TailedBeast beast, ShukakuMotion m, float t, int lod) {
        float w = m.breathRate * Mth.TWO_PI;
        float breath = Mth.sin(t * w) + 0.5f * Mth.sin(t * w * 0.37f + 1.3f);
        float b = breath * 0.022f * m.breathAmp;
        belly.scaleX *= 1f + b;
        belly.scaleZ *= 1f + b * 1.3f;
        belly.scaleY *= 1f - b * 0.3f;
        chest.y += breath * 0.25f * m.breathAmp;
        // blinks (every other one at mid distance), on top of the mood's lid height
        float blink = lod == 2 ? 0f : m.blink.closed(t);
        if (lod == 1 && (int) (t / 4f) % 2 == 0) {
            blink = 0f;
        }
        float closed = Math.max(1f - m.eyeOpen, blink);
        closed = Math.max(closed, m.hurtFlash);
        lidL.y -= closed * EYE_H;
        lidR.y -= closed * EYE_H;
        // ear flicks, one ear at a time
        float fl = m.earL.value(t), fr = m.earR.value(t);
        earL.zRot -= fl * 0.45f;
        earTipL.zRot -= fl * 0.35f;
        earR.zRot += fr * 0.45f;
        earTipR.zRot += fr * 0.35f;
    }

    // ---------------------------------------------------------------- 4. look: chest 25 %, head 75 %, eased

    private void look(TailedBeast beast, ShukakuMotion m, float partial, float dt, float bodyYaw) {
        if (beast.mood() == Mood.SUBDUED || beast.deathTime > 0) {
            m.lookYaw = CreatureRig.approach(m.lookYaw, 0f, dt, 0.5f);
            m.lookPitch = CreatureRig.approach(m.lookPitch, 0f, dt, 0.5f);
        } else {
            float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, beast.yHeadRotO, beast.yHeadRot) - bodyYaw), -70f, 70f);
            float pitch = Mth.clamp(beast.getViewXRot(partial), -35f, 35f);
            m.lookYaw = CreatureRig.approach(m.lookYaw, yaw * Mth.DEG_TO_RAD, dt, 0.3f);
            m.lookPitch = CreatureRig.approach(m.lookPitch, pitch * Mth.DEG_TO_RAD, dt, 0.3f);
        }
        // the model faces -z and is turned by 180 - yaw, so a look to the entity's left is a negative part yaw
        chest.yRot -= m.lookYaw * 0.25f;
        head.yRot -= m.lookYaw * 0.75f;
        head.xRot -= m.lookPitch * 0.8f;
    }

    // ---------------------------------------------------------------- 5. actions (blend in 0.15 s, out 0.25 s)

    private void action(BakedBbModel model, TailedBeast beast, ShukakuMotion m, float partial, float t, float dt) {
        byte now = beast.action();
        if (now != m.action || now != TailedBeast.ACTION_NONE && beast.actionStart() != m.actionStart) {
            m.prevAction = m.action;
            m.prevStart = m.actionStart;
            m.prevWeight = m.actionWeight;
            m.action = now;
            m.actionStart = beast.actionStart();
            m.actionWeight = 0f;
        }
        m.actionWeight = now == TailedBeast.ACTION_NONE ? 0f : Math.min(1f, m.actionWeight + dt / 0.15f);
        m.prevWeight = Math.max(0f, m.prevWeight - dt / 0.25f);
        long time = beast.level().getGameTime();
        if (m.prevWeight > 0f && m.prevAction != TailedBeast.ACTION_NONE) {
            play(model, beast, m, m.prevAction, (time - m.prevStart + partial) / 20f, m.prevWeight, t);
        }
        if (m.actionWeight > 0f) {
            play(model, beast, m, m.action, (time - m.actionStart + partial) / 20f, m.actionWeight, t);
        }
    }

    private void play(BakedBbModel model, TailedBeast beast, ShukakuMotion m, byte action, float s, float w, float t) {
        switch (action) {
            case TailedBeast.ACTION_DRUM -> model.animate("drum", s, w);
            case TailedBeast.ACTION_LAUGH -> {
                model.animate("laugh", s % 1.8f, w);
                m.hurtFlash = Math.max(m.hurtFlash, 0.5f * w); // squints with laughter
            }
            case TailedBeast.ACTION_CLAW -> {
                // wind-up: both claws raised high and back; strike: they rake down and forward (attackAnimation 10 → 0)
                float strike = beast.getAttackAnimationRemainingTicks() / 10f;
                float raise = strike > 0f ? 0.5f + 1.4f * strike : 1.9f * CreatureRig.smooth(s / 0.3f);
                armR.xRot += raise * w;
                armL.xRot += raise * w * 0.8f;
                armR.zRot += 0.3f * w;
                armL.zRot -= 0.3f * w;
                body.xRot += (strike > 0f ? -0.25f * strike : 0.15f) * w;
                jaw.xRot -= 0.35f * w;
            }
            case TailedBeast.ACTION_SCRATCH -> {
                // the right claw across the belly, scratching fast; head tilted, eyes half shut with pleasure
                armR.xRot += 0.5f * w;
                armR.zRot -= 0.55f * w;
                forearmR.xRot += (0.7f + 0.3f * Mth.sin(t * 22f)) * w;
                forearmR.yRot -= 0.4f * w;
                head.zRot += 0.18f * w;
                m.hurtFlash = Math.max(m.hurtFlash, 0.5f * w);
            }
            case TailedBeast.ACTION_SIFT -> {
                // bends, scoops sand with both hands, lifts them and lets it trickle (particles: TailedBeast.sandAura)
                float scoop = CreatureRig.smooth(Math.min(1f, s / 0.8f)) - CreatureRig.smooth((s - 1.4f) / 0.6f) * 0.6f;
                body.xRot -= 0.35f * scoop * w;
                armL.xRot += 1.2f * scoop * w;
                armR.xRot += 1.2f * scoop * w;
                armL.zRot += 0.25f * w;
                armR.zRot -= 0.25f * w;
                handL.xRot -= 0.5f * w;
                handR.xRot -= 0.5f * w;
                head.xRot -= 0.3f * scoop * w;
            }
            case TailedBeast.ACTION_YAWN -> {
                // a slow yawn, eyes shut... then it shakes it off (Shukaku hates sleep)
                float open = CreatureRig.envelope(s, 1.3f);
                jaw.xRot -= 0.75f * open * w;
                head.xRot += 0.35f * open * w;
                m.hurtFlash = Math.max(m.hurtFlash, open * w);
                if (s > 1.3f) {
                    float shake = Math.max(0f, 1f - (s - 1.3f) / 0.6f);
                    head.yRot += 0.35f * Mth.sin((s - 1.3f) * 28f) * shake * w;
                }
            }
            case TailedBeast.ACTION_ROAR -> {
                // anticipation (a crouch), then jaw wide, arms spread: the intimidation display
                float crouch = CreatureRig.envelope(s, 0.3f), roar = CreatureRig.envelope(s - 0.25f, 1.2f);
                body.xRot += (0.12f * crouch - 0.2f * roar) * w;
                jaw.xRot -= 0.8f * roar * w;
                head.xRot += 0.25f * roar * w;
                armR.zRot += 0.7f * roar * w;
                armL.zRot -= 0.7f * roar * w;
                armR.xRot += 0.4f * roar * w;
                armL.xRot += 0.4f * roar * w;
                m.earBack = Math.max(m.earBack, 0.8f * roar);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- 6. secondary motion: springs on tail, ears, belly

    private void secondary(TailedBeast beast, ShukakuMotion m, float t, float dt, float bodyYaw, int lod) {
        float yawVel = 0f;
        if (!Float.isNaN(m.lastBodyYaw) && dt > 0f) {
            yawVel = Mth.wrapDegrees(bodyYaw - m.lastBodyYaw) * Mth.DEG_TO_RAD / dt;
        }
        m.lastBodyYaw = bodyYaw;
        float mood = beast.mood() == Mood.ANGRY || beast.mood() == Mood.MANIC ? 1f : 0f;
        float tip = m.tailTip.value(t);
        if (lod > 0) {
            // far: a cheap phase-delayed sine instead of springs
            for (int i = 0; i < tail.length; i++) {
                tail[i].yRot += Mth.sin(t * 1.3f - i * 0.6f) * 0.06f * (1 + i * 0.3f);
                tail[i].xRot -= m.tailLift * (i == 0 ? 1f : 0.15f);
            }
            return;
        }
        // yaw: the root lags behind body turns and sways slowly; each segment chases the one before it (flows to the tip)
        float sway = Mth.sin(t * (1.1f + mood * 1.6f)) * (0.12f + mood * 0.12f) + Mth.sin(t * 0.43f + 2f) * 0.06f;
        for (int i = 0; i < tail.length; i++) {
            float target = i == 0 ? Mth.clamp(-yawVel * 0.12f, -0.6f, 0.6f) + sway : m.tailYaw[i - 1].a * 0.92f;
            m.tailYaw[i].step(target, TAIL_STIFFNESS - i * 3f, TAIL_DAMPING, dt);
            tail[i].yRot += m.tailYaw[i].a;
        }
        // pitch: the mood carriage at the root, footfall bounce and a sag toward the tip; the tip curls now and then
        for (int i = 0; i < tail.length; i++) {
            float target = i == 0 ? -m.tailLift + m.lastBob * 0.1f : m.tailPitch[i - 1].a * 0.5f + (i >= 4 ? -0.25f * tip : 0f);
            m.tailPitch[i].step(target, TAIL_STIFFNESS + 10f, TAIL_DAMPING, dt);
            tail[i].xRot += m.tailPitch[i].a;
        }
        // ears swing against head turns; the belly jiggles on footfalls
        m.earSwing.step(Mth.clamp(yawVel * 0.05f, -0.4f, 0.4f), 90f, 9f, dt);
        earL.zRot += m.earSwing.a;
        earR.zRot += m.earSwing.a;
        m.bellyJiggle.step(-m.lastBob * 0.06f, 70f, 5f, dt);
        belly.scaleY *= 1f + m.bellyJiggle.a;
        belly.scaleX *= 1f - m.bellyJiggle.a * 0.5f;
    }

    // ---------------------------------------------------------------- 7. reactions: the hurt rock

    private void reactions(TailedBeast beast, ShukakuMotion m, float dt) {
        if (beast.hurtTime > m.lastHurt) {
            m.rock.kick(3.5f); // a big body: a slow rock back and a head jerk, eyes shut a moment
            m.hurtFlash = 1f;
        }
        m.lastHurt = beast.hurtTime;
        m.rock.step(0f, 40f, 7f, dt);
        body.xRot += m.rock.a * 0.5f;
        head.xRot += m.rock.a * 0.4f;
        m.hurtFlash = Math.max(0f, m.hurtFlash - dt / 0.25f);
    }
}

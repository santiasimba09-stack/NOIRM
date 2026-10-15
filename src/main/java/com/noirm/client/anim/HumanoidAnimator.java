package com.noirm.client.anim;

import com.noirm.Noirm;
import com.noirm.client.anim.SkeletonPose.Bone;
import com.noirm.entity.SparringNinja;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * NOIRM's animation engine for every humanoid fighter (players and NPC ninjas alike), drawn on
 * {@link com.noirm.client.BendyModel}. In the spirit of Epic Fight's "living animations" and
 * Better Combat's bendy limbs, built from our own parts:
 *
 * <ol>
 *   <li><b>Base:</b> vanilla's pose (head look, items, crouch, riding, swimming).</li>
 *   <li><b>Gait:</b> our own walk with knees and elbows, shoulders countering the hips and a
 *       bob; the <b>ninja run</b> when sprinting (leaning in, arms swept back).</li>
 *   <li><b>Air and landing:</b> knees tuck on the way up, limbs reach on the way down, and a
 *       landing sinks into the knees by how hard it was.</li>
 *   <li><b>Stance:</b> the style's (or weapon's) guard, upper body always, legs when standing.</li>
 *   <li><b>Moves:</b> the keyframe animation the server started, stretched to the move's real
 *       timing, cross-faded with the one before; frozen during hit-stop.</li>
 *   <li><b>Guard, stagger and hits:</b> a guard pose; a flinch that throws the body away from
 *       where the hit came from.</li>
 *   <li><b>Lean:</b> the body rolls into turns.</li>
 *   <li><b>Springs:</b> every joint follows its target through a small spring, so limbs
 *       overshoot and settle (follow-through) instead of snapping from key to key.</li>
 * </ol>
 * Layers fade in and out, so nothing snaps. Tuning lives in the constants below.
 */
public final class HumanoidAnimator {
    // Fades, in ticks.
    static final float ACTION_FADE_IN = 1.5f;
    static final float ACTION_FADE_OUT = 4f;
    static final float STANCE_FADE = 4f;
    static final float GUARD_FADE = 2.5f;
    static final float STAGGER_FADE_IN = 1.5f;
    static final float STAGGER_FADE_OUT = 4f;
    static final float LEGACY_FADE = 2f;
    static final float RUN_FADE = 5f;
    static final float AIR_FADE = 3f;
    /** Horizontal speed (blocks/tick) from which a sprinter breaks into the ninja run. */
    static final float RUN_SPEED = 0.25f;
    /** Ticks a flinch lasts. */
    static final float FLINCH_TICKS = 9f;
    /** Ticks a landing lasts. */
    static final float LAND_TICKS = 8f;
    /** How much the head turns back against a torso twist (keeps the eyes on the opponent). */
    static final float HEAD_STABILISE = 0.7f;
    /** Springs per bone: frequency (Hz), damping (1 = no overshoot), response (> 1 = anticipates / overshoots). */
    private static final float[][] SPRING = {
            {5.0f, 0.85f, 1.0f},  // body
            {4.5f, 0.75f, 1.0f},  // torso
            {9.0f, 0.9f, 1.0f},   // head
            {5.5f, 0.55f, 1.5f},  // right arm
            {5.5f, 0.55f, 1.5f},  // left arm
            {6.5f, 0.75f, 1.0f},  // right leg
            {6.5f, 0.75f, 1.0f},  // left leg
    };

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    /** Actions the server started on players, by entity id (NPCs carry theirs as synced data). */
    private static final Map<Integer, PlayerAction> PLAYER_ACTIONS = new HashMap<>();
    private static final SkeletonPose GUARD = guardPose();
    private static final SkeletonPose STAGGER = staggerPose();

    private HumanoidAnimator() {}

    record PlayerAction(int seq, String id, int ticks, long startedAt) {}

    /** What an entity is doing, as far as animation cares. */
    public record Inputs(int seq, String action, int actionTicks, ResourceLocation stance, boolean fighting,
                         boolean guarding, boolean staggered, SparringNinja.Pose legacy) {}

    // ------------------------------------------------------------ entry points

    /** The server started {@code animation} on a player (packet). */
    public static void playOnPlayer(int entityId, ResourceLocation animation, int ticks, long gameTime) {
        PlayerAction old = PLAYER_ACTIONS.get(entityId);
        PLAYER_ACTIONS.put(entityId, new PlayerAction(old == null ? 1 : old.seq + 1, animation.toString(), Math.max(1, ticks), gameTime));
    }

    /** A hit landed: the animation of {@code entity} holds still for {@code ticks} (hit-stop). */
    public static void hitstop(LivingEntity entity, int ticks) {
        State state = STATES.get(entity);
        if (state != null && !Float.isNaN(state.lastTime)) {
            state.freezeUntil = Math.max(state.freezeUntil, state.lastTime + ticks);
        }
    }

    public static void clear() {
        STATES.clear();
        PLAYER_ACTIONS.clear();
    }

    /** The whole-body transform (the {@code body} bone) for this frame, for the renderer. */
    public static SkeletonPose bodyTransform(LivingEntity entity, float time, float limbSwing, float limbAmount) {
        State state = stateFor(entity, time, limbSwing, limbAmount);
        return state.bodyOut;
    }

    /** The final pose of the parts for this frame, written over {@code pose} (vanilla's base). */
    public static void animateParts(LivingEntity entity, HumanoidModel<?> model, SkeletonPose pose, float limbSwing, float limbAmount, float time) {
        State state = stateFor(entity, time, limbSwing, limbAmount);
        if (state.partsTime == time && state.partsValid) {
            pose.copyFrom(state.parts);
            return;
        }
        compose(entity, model, state, pose, limbSwing, limbAmount);
        state.springs(pose, state.partsDt, state.partsTime != state.partsTime || !state.partsValid);
        plantFeet(entity, state, pose, limbAmount);
        state.parts.copyFrom(pose);
        state.partsTime = time;
        state.partsValid = true;
    }

    /** The last final pose of the parts (null if never animated), e.g. for first-person arms. */
    public static SkeletonPose lastParts(LivingEntity entity) {
        State state = STATES.get(entity);
        return state == null || !state.partsValid ? null : state.parts;
    }

    /** How strongly a move animation is playing right now (0 = none). */
    public static float actionWeight(LivingEntity entity) {
        State state = STATES.get(entity);
        return state == null ? 0f : Math.max(state.currentWeight, state.previousWeight);
    }

    /** True while a move the server started on this player is still playing (by game time). */
    public static boolean hasRecentAction(Player player) {
        PlayerAction action = PLAYER_ACTIONS.get(player.getId());
        return action != null && player.level().getGameTime() - action.startedAt <= action.ticks + 3;
    }

    /** How strongly the stance is held (0 = not fighting). */
    public static float stanceWeight(LivingEntity entity) {
        State state = STATES.get(entity);
        return state == null ? 0f : state.stanceWeight;
    }

    // ------------------------------------------------------------ state

    /** One animation instance being played. */
    static final class Playing {
        final KeyframeAnimation animation;
        final float start;
        final float duration;
        final float speed;
        float releasedWeight = -1f;
        float releasedAt;

        Playing(KeyframeAnimation animation, float start, float duration) {
            this.animation = animation;
            this.start = start;
            this.duration = Math.max(1f, duration);
            this.speed = animation.lengthTicks > 0 ? animation.lengthTicks / this.duration : 1f;
        }

        float weight(float clock) {
            float elapsed = clock - start;
            if (releasedWeight >= 0f) {
                return Math.max(0f, releasedWeight * (1f - (clock - releasedAt) / ACTION_FADE_OUT));
            }
            float in = Math.min(1f, Math.max(0f, elapsed) / ACTION_FADE_IN);
            float out = elapsed > duration ? 1f - (elapsed - duration) / ACTION_FADE_OUT : 1f;
            return Math.max(0f, in * out);
        }

        void sample(float clock, SkeletonPose out) {
            out.clear();
            animation.sample((clock - start) * speed, out);
        }
    }

    static final class State {
        float lastTime = Float.NaN;
        float dt;
        /** The clock moves would play on: it stands still during hit-stop. */
        float clock;
        float freezeUntil = Float.NEGATIVE_INFINITY;
        int seq;
        boolean seqKnown;
        Playing current;
        Playing previous;
        float currentWeight;
        float previousWeight;
        KeyframeAnimation stance;
        float stanceWeight;
        float guardWeight;
        float staggerWeight;
        float legacyWeight;
        SparringNinja.Pose legacyPose = SparringNinja.Pose.IDLE;
        float runWeight;
        float airWeight;
        float rising;
        boolean wasOnGround = true;
        int airTicks;
        float landStart = Float.NEGATIVE_INFINITY;
        float landStrength;
        float fallSpeed;
        int lastHurtTime;
        float flinchStart = Float.NEGATIVE_INFINITY;
        float flinchFront;
        float flinchSide;
        float lastBodyYaw = Float.NaN;
        float lean;
        float pelvisTarget;
        float pelvis;
        float speed;
        boolean procedural;
        final SkeletonPose stancePose = new SkeletonPose();
        final SkeletonPose stanceUpper = new SkeletonPose();
        final SkeletonPose stanceLegs = new SkeletonPose();
        boolean stanceHasLegs;
        final SkeletonPose currentPose = new SkeletonPose();
        final SkeletonPose previousPose = new SkeletonPose();
        final SkeletonPose legacy = new SkeletonPose();
        final SkeletonPose scratch = new SkeletonPose();
        final SkeletonPose bodyTarget = new SkeletonPose();
        final SkeletonPose bodyOut = new SkeletonPose();
        final SkeletonPose parts = new SkeletonPose();
        float partsTime = Float.NaN;
        float partsDt;
        boolean partsValid;
        // Springs: value and velocity per channel, plus the last target (for its velocity).
        final float[] springY = new float[Bone.values().length * SkeletonPose.CHANNELS];
        final float[] springV = new float[springY.length];
        final float[] springX = new float[springY.length];
        boolean springInit;

        /** Runs every channel of {@code pose} through its bone's spring, in place. */
        void springs(SkeletonPose pose, float dtTicks, boolean fresh) {
            if (!springInit || dtTicks > 10f) {
                for (Bone bone : Bone.values()) {
                    for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                        int i = bone.ordinal() * SkeletonPose.CHANNELS + c;
                        springY[i] = pose.get(bone, c);
                        springX[i] = springY[i];
                        springV[i] = 0f;
                    }
                }
                springInit = true;
                return;
            }
            if (dtTicks <= 0f) {
                for (Bone bone : Bone.values()) {
                    for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                        if (bone != Bone.BODY && pose.isSet(bone, c)) {
                            pose.set(bone, c, springY[bone.ordinal() * SkeletonPose.CHANNELS + c]);
                        }
                    }
                }
                return;
            }
            float seconds = dtTicks / 20f;
            int steps = Math.max(1, (int) Math.ceil(seconds / (1f / 120f)));
            float t = seconds / steps;
            for (Bone bone : Bone.values()) {
                if (bone == Bone.BODY) {
                    continue; // smoothed separately (bodyTransform)
                }
                float[] k = SPRING[bone.ordinal()];
                for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                    int i = bone.ordinal() * SkeletonPose.CHANNELS + c;
                    float target = pose.get(bone, c);
                    pose.set(bone, c, step(i, target, isAngle(c), k, seconds, steps, t));
                }
            }
        }

        float step(int i, float target, boolean angle, float[] k, float seconds, int steps, float t) {
            float y = springY[i];
            float x = angle ? y + SkeletonPose.wrap(target - y) : target;
            float xd = (angle ? SkeletonPose.wrap(target - springX[i]) : target - springX[i]) / seconds;
            springX[i] = target;
            float f = k[0], z = k[1], r = k[2];
            float k1 = z / ((float) Math.PI * f);
            float k2 = 1f / ((2f * (float) Math.PI * f) * (2f * (float) Math.PI * f));
            float k3 = r * z / (2f * (float) Math.PI * f);
            float v = springV[i];
            for (int s = 0; s < steps; s++) {
                float k2s = Math.max(k2, Math.max(t * t / 2f + t * k1 / 2f, t * k1));
                y += t * v;
                v += t * (x + k3 * xd - y - k1 * v) / k2s;
            }
            springY[i] = y;
            springV[i] = v;
            return y;
        }
    }

    private static boolean isAngle(int channel) {
        return channel <= SkeletonPose.RZ || channel == SkeletonPose.BEND;
    }

    private static State stateFor(LivingEntity entity, float time, float limbSwing, float limbAmount) {
        State state = STATES.computeIfAbsent(entity, e -> new State());
        if (state.lastTime != time) {
            update(entity, state, time, limbSwing, limbAmount);
        }
        return state;
    }

    // ------------------------------------------------------------ inputs

    static Inputs inputsFor(LivingEntity entity, float time) {
        if (entity instanceof SparringNinja ninja) {
            ResourceLocation stance = WeaponStances.stanceFor(ninja.getMainHandItem());
            if (stance == null) {
                stance = styleStance(ninja.syncedStyle());
            }
            return new Inputs(ninja.animationSeq(), ninja.animationId(), ninja.animationTicks(), stance, ninja.isFighting(),
                    ninja.ninjaPose() == SparringNinja.Pose.GUARD, ninja.ninjaPose() == SparringNinja.Pose.STAGGER, ninja.ninjaPose());
        }
        if (entity instanceof Player player) {
            PlayerAction action = PLAYER_ACTIONS.get(player.getId());
            boolean local = com.noirm.client.ClientPlayerState.isLocal(player);
            boolean fighting = local ? com.noirm.client.ClientPlayerState.combatMode()
                    : action != null && player.level().getGameTime() - action.startedAt < 80;
            ResourceLocation stance = null;
            if (fighting) {
                stance = WeaponStances.stanceFor(player.getMainHandItem());
                if (stance == null) {
                    stance = styleStance(local ? com.noirm.client.ClientPlayerState.style() : "");
                }
            }
            return new Inputs(action == null ? 0 : action.seq, action == null ? "" : action.id, action == null ? 1 : action.ticks,
                    stance, fighting, local && com.noirm.client.ClientPlayerState.guarding(), false, SparringNinja.Pose.IDLE);
        }
        return new Inputs(0, "", 1, null, false, false, false, SparringNinja.Pose.IDLE);
    }

    /** {@code noirm:brawler} or {@code brawler} → {@code noirm:stance_brawler}. */
    static ResourceLocation styleStance(String style) {
        if (style == null || style.isEmpty()) {
            return Noirm.id("stance_basic");
        }
        ResourceLocation id = ResourceLocation.tryParse(style.contains(":") ? style : Noirm.MOD_ID + ":" + style);
        return id == null ? Noirm.id("stance_basic") : new ResourceLocation(id.getNamespace(), "stance_" + id.getPath());
    }

    // ------------------------------------------------------------ per-frame update

    private static void update(LivingEntity entity, State state, float time, float limbSwing, float limbAmount) {
        float dt = Float.isNaN(state.lastTime) ? 0f : Math.max(0f, Math.min(time - state.lastTime, 20f));
        boolean first = Float.isNaN(state.lastTime);
        state.lastTime = time;
        state.dt = dt;
        state.partsDt = dt;
        state.partsValid = false;
        // The move clock stands still during hit-stop.
        float frozen = Math.max(0f, Math.min(time, state.freezeUntil) - (time - dt));
        state.clock += dt - Math.min(dt, frozen);
        float clock = state.clock;
        Inputs in = inputsFor(entity, time);

        // A new move from the server? (The first time we see someone, their old one is history.)
        if (!state.seqKnown) {
            state.seqKnown = true;
            state.seq = in.seq();
        } else if (in.seq() != state.seq) {
            state.seq = in.seq();
            KeyframeAnimation animation = NpcAnimations.get(ResourceLocation.tryParse(in.action()));
            if (animation != null) {
                if (state.current != null) {
                    state.current.releasedWeight = state.current.weight(clock);
                    state.current.releasedAt = clock;
                    state.previous = state.current;
                }
                state.current = new Playing(animation, clock, in.actionTicks());
            }
        }
        state.currentWeight = state.current == null ? 0f : state.current.weight(clock);
        if (state.current != null && state.currentWeight <= 0f && clock - state.current.start > state.current.duration) {
            state.current = null;
        }
        state.previousWeight = state.previous == null ? 0f : state.previous.weight(clock);
        if (state.previous != null && state.previousWeight <= 0f) {
            state.previous = null;
        }
        if (state.current != null) {
            state.current.sample(clock, state.currentPose);
        }
        if (state.previous != null) {
            state.previous.sample(clock, state.previousPose);
        }

        // Stance.
        KeyframeAnimation stance = in.stance() == null ? null : NpcAnimations.get(in.stance());
        if (stance == null && in.fighting()) {
            stance = NpcAnimations.get(Noirm.id("stance_basic"));
        }
        if (stance != null) {
            state.stance = stance;
        }
        state.stanceWeight = approach(state.stanceWeight, in.fighting() && stance != null ? 1f : 0f, dt / STANCE_FADE);
        if (state.stance != null) {
            state.stancePose.clear();
            state.stance.sample(time, state.stancePose);
            // Its legs (footwork) apply only while standing; the rest always.
            state.stanceLegs.clear();
            state.stanceHasLegs = false;
            for (Bone leg : new Bone[]{Bone.RIGHT_LEG, Bone.LEFT_LEG, Bone.BODY}) {
                for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                    if (state.stancePose.isSet(leg, c)) {
                        state.stanceLegs.set(leg, c, state.stancePose.get(leg, c));
                        state.stanceHasLegs |= leg != Bone.BODY;
                    }
                }
            }
            state.stanceUpper.clear();
            for (Bone bone : new Bone[]{Bone.TORSO, Bone.HEAD, Bone.RIGHT_ARM, Bone.LEFT_ARM}) {
                for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                    if (state.stancePose.isSet(bone, c)) {
                        state.stanceUpper.set(bone, c, state.stancePose.get(bone, c));
                    }
                }
            }
        }

        state.guardWeight = approach(state.guardWeight, in.guarding() ? 1f : 0f, dt / GUARD_FADE);
        state.staggerWeight = approach(state.staggerWeight, in.staggered() ? 1f : 0f, dt / (in.staggered() ? STAGGER_FADE_IN : STAGGER_FADE_OUT));
        SparringNinja.Pose pose = in.legacy();
        boolean legacy = (pose == SparringNinja.Pose.WINDUP_LIGHT || pose == SparringNinja.Pose.WINDUP_HEAVY
                || pose == SparringNinja.Pose.ACTIVE) && state.current == null;
        if (legacy && pose != state.legacyPose) {
            state.legacyPose = pose;
            legacyPose(pose, state.legacy);
        }
        state.legacyWeight = approach(state.legacyWeight, legacy ? 1f : 0f, dt / LEGACY_FADE);

        // Movement facts.
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo, dy = entity.getY() - entity.yo;
        state.speed = (float) Math.sqrt(dx * dx + dz * dz);
        state.procedural = !entity.isPassenger() && !entity.isFallFlying() && !entity.isSwimming() && !entity.isSleeping()
                && !entity.isAutoSpinAttack() && entity.getPose() != Pose.DYING && !entity.isInWater()
                && !(entity instanceof Player p && p.getAbilities().flying);
        boolean onGround = entity.onGround();
        boolean running = state.procedural && onGround && (entity.isSprinting() || state.speed > RUN_SPEED)
                && state.currentWeight < 0.5f && state.guardWeight < 0.5f;
        state.runWeight = approach(state.runWeight, running ? 1f : 0f, dt / RUN_FADE);
        if (onGround) {
            if (!state.wasOnGround && state.airTicks > 3 && !first) {
                state.landStart = time;
                state.landStrength = Mth.clamp(state.fallSpeed / 0.7f, 0.25f, 1f);
            }
            state.airTicks = 0;
        } else if (dt > 0) {
            state.airTicks++;
            state.fallSpeed = (float) Math.max(0, -dy);
        }
        state.wasOnGround = onGround;
        state.airWeight = approach(state.airWeight, state.procedural && !onGround && state.airTicks > 2 ? 1f : 0f, dt / AIR_FADE);
        state.rising = approach(state.rising, dy > 0.02 ? 1f : 0f, dt / 4f);

        // A new hit: flinch away from where it came from, and hold still a moment (hit-stop).
        if (entity.hurtTime > state.lastHurtTime && entity.hurtTime >= 8) {
            state.flinchStart = time;
            float rel = (entity.getHurtDir() - 90f) * Mth.DEG_TO_RAD; // 0 = from the front
            state.flinchFront = Mth.cos(rel);
            state.flinchSide = Mth.sin(rel);
            state.freezeUntil = Math.max(state.freezeUntil, time + 2f);
        }
        state.lastHurtTime = entity.hurtTime;

        // Lean into turns.
        float yaw = entity.yBodyRot;
        if (!Float.isNaN(state.lastBodyYaw) && dt > 0) {
            float rate = Mth.wrapDegrees(yaw - state.lastBodyYaw) / dt;
            float target = Mth.clamp(-rate * 0.012f, -0.35f, 0.35f) * Mth.clamp(state.speed * 5f, 0f, 1f);
            state.lean += (target - state.lean) * Math.min(1f, dt * 0.25f);
        }
        state.lastBodyYaw = yaw;

        // The whole-body transform: the layers' body bone, plus bob, landing, flinch push and lean.
        SkeletonPose body = state.bodyTarget;
        body.clear();
        if (state.stance != null && state.stanceWeight > 0f) {
            float still = state.stanceWeight * (1f - state.runWeight) * (1f - Mth.clamp(limbAmount * 2f, 0f, 1f)) * (1f - state.airWeight);
            SkeletonPose stanceBody = state.scratch;
            stanceBody.clear();
            for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                if (state.stanceLegs.isSet(Bone.BODY, c)) {
                    stanceBody.set(Bone.BODY, c, state.stanceLegs.get(Bone.BODY, c));
                }
            }
            body.blend(stanceBody, still);
        }
        composeLayers(state, body);
        float bob = state.procedural && onGround ? gaitBob(limbSwing, limbAmount, state.runWeight) : 0f;
        add(body, Bone.BODY, SkeletonPose.PY, bob - landingDip(state, time) - stanceDip(state, limbAmount));
        float flinch = flinchEnvelope(state, time);
        add(body, Bone.BODY, SkeletonPose.PZ, 0.07f * state.flinchFront * flinch);
        add(body, Bone.BODY, SkeletonPose.RZ, state.lean);
        state.pelvis += (state.pelvisTarget - state.pelvis) * Math.min(1f, dt * 0.5f);
        add(body, Bone.BODY, SkeletonPose.PY, -state.pelvis / 16f);
        smoothBody(state, body, dt);
    }

    private static void smoothBody(State state, SkeletonPose body, float dt) {
        state.bodyOut.clear();
        float[] k = SPRING[Bone.BODY.ordinal()];
        float seconds = dt / 20f;
        int steps = Math.max(1, (int) Math.ceil(seconds / (1f / 120f)));
        for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
            int i = Bone.BODY.ordinal() * SkeletonPose.CHANNELS + c;
            float target = body.isSet(Bone.BODY, c) ? body.get(Bone.BODY, c) : 0f;
            float value;
            if (dt <= 0f || dt > 10f) {
                state.springY[i] = target;
                state.springX[i] = target;
                state.springV[i] = 0f;
                value = target;
            } else {
                value = state.step(i, target, isAngle(c), k, seconds, steps, seconds / steps);
            }
            if (Math.abs(value) > 1.0e-5f || body.isSet(Bone.BODY, c)) {
                state.bodyOut.set(Bone.BODY, c, value);
            }
        }
    }

    // ------------------------------------------------------------ composing the parts

    private static void compose(LivingEntity entity, HumanoidModel<?> model, State state, SkeletonPose pose, float limbSwing, float limbAmount) {
        float time = state.lastTime;
        boolean armsFree = model.rightArmPose == HumanoidModel.ArmPose.EMPTY || model.rightArmPose == HumanoidModel.ArmPose.ITEM;
        armsFree &= model.leftArmPose == HumanoidModel.ArmPose.EMPTY || model.leftArmPose == HumanoidModel.ArmPose.ITEM;
        armsFree &= !entity.isUsingItem() && model.attackTime <= 0f;
        if (state.procedural && !model.crouching) {
            gait(pose, limbSwing, limbAmount, armsFree, state.runWeight);
            if (state.runWeight > 0f) {
                ninjaRun(pose, state.runWeight, armsFree);
            }
        }
        if (state.airWeight > 0f) {
            air(pose, state.airWeight, state.rising, armsFree);
        }
        float land = landingKnees(state, time);
        if (land > 0f) {
            kneel(pose, land);
        }
        // Stance: the upper body always; the legs only while standing still.
        if (state.stance != null && state.stanceWeight > 0f) {
            float w = state.stanceWeight * (1f - state.guardWeight) * (1f - state.runWeight);
            if (armsFree) {
                pose.blend(state.stanceUpper, w);
            } else { // using an item: the torso still sets itself, the arms do what the item needs
                SkeletonPose torso = state.scratch;
                torso.clear();
                for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                    if (state.stanceUpper.isSet(Bone.TORSO, c)) {
                        torso.set(Bone.TORSO, c, state.stanceUpper.get(Bone.TORSO, c));
                    }
                }
                pose.blend(torso, w);
            }
            float still = w * (1f - Mth.clamp(limbAmount * 2f, 0f, 1f)) * (1f - state.airWeight);
            if (still > 0f) {
                if (state.stanceHasLegs) {
                    SkeletonPose legs = state.scratch;
                    legs.clear();
                    legs.copyFrom(state.stanceLegs);
                    legs.clear(Bone.BODY);
                    pose.blend(legs, still);
                } else {
                    stanceLegs(pose, still);
                }
            }
        }
        composeLayers(state, pose);
        float flinch = flinchEnvelope(state, time);
        if (flinch > 0f) {
            flinch(pose, flinch, state.flinchFront, state.flinchSide);
        }
        // The head keeps looking ahead while the torso twists.
        add(pose, Bone.HEAD, SkeletonPose.RY, -HEAD_STABILISE * pose.get(Bone.TORSO, SkeletonPose.RY));
    }

    /** The keyframed layers (stance excluded): moves, guard, old wind-ups, stagger. */
    private static void composeLayers(State state, SkeletonPose pose) {
        if (state.previous != null) {
            pose.blend(state.previousPose, state.previousWeight);
        }
        if (state.current != null) {
            pose.blend(state.currentPose, state.currentWeight);
        }
        pose.blend(GUARD, state.guardWeight);
        pose.blend(state.legacy, state.legacyWeight);
        pose.blend(STAGGER, state.staggerWeight);
    }

    // ------------------------------------------------------------ procedural layers

    /** Our walk: strides with knees, arms with elbows, shoulders countering the hips. */
    static void gait(SkeletonPose p, float limbSwing, float limbAmount, boolean arms, float run) {
        float a = Mth.clamp(limbAmount, 0f, 1f);
        float phase = limbSwing * 0.6662f;
        float c = Mth.cos(phase), s = Mth.sin(phase);
        float stride = 1.15f + 0.35f * run;
        p.set(Bone.RIGHT_LEG, SkeletonPose.RX, c * stride * a);
        p.set(Bone.LEFT_LEG, SkeletonPose.RX, -c * stride * a);
        float lift = 1.0f + 0.7f * run;
        p.set(Bone.RIGHT_LEG, SkeletonPose.BEND, 0.08f + a * (0.15f + lift * Math.max(0f, s)));
        p.set(Bone.LEFT_LEG, SkeletonPose.BEND, 0.08f + a * (0.15f + lift * Math.max(0f, -s)));
        if (arms) {
            p.set(Bone.RIGHT_ARM, SkeletonPose.RX, p.get(Bone.RIGHT_ARM, SkeletonPose.RX) * (1f - a) - c * 0.85f * a);
            p.set(Bone.LEFT_ARM, SkeletonPose.RX, p.get(Bone.LEFT_ARM, SkeletonPose.RX) * (1f - a) + c * 0.85f * a);
            p.set(Bone.RIGHT_ARM, SkeletonPose.BEND, 0.15f + a * (0.2f + 0.55f * Math.max(0f, c)));
            p.set(Bone.LEFT_ARM, SkeletonPose.BEND, 0.15f + a * (0.2f + 0.55f * Math.max(0f, -c)));
        } else {
            p.set(Bone.RIGHT_ARM, SkeletonPose.BEND, 0.15f);
            p.set(Bone.LEFT_ARM, SkeletonPose.BEND, 0.15f);
        }
        p.set(Bone.TORSO, SkeletonPose.RY, p.get(Bone.TORSO, SkeletonPose.RY) - c * 0.12f * a);
        p.set(Bone.TORSO, SkeletonPose.RX, p.get(Bone.TORSO, SkeletonPose.RX) + 0.06f * a);
        p.set(Bone.TORSO, SkeletonPose.BEND, 0.03f + 0.05f * a);
    }

    /** The body's bob through the stride (blocks): lowest with the legs apart, highest as they pass. */
    static float gaitBob(float limbSwing, float limbAmount, float run) {
        float a = Mth.clamp(limbAmount, 0f, 1f);
        float c = Math.abs(Mth.cos(limbSwing * 0.6662f));
        return a * (0.035f + 0.03f * run) * (0.5f - c);
    }

    /** The ninja run: chest leaning in, head up, arms swept straight back. */
    static void ninjaRun(SkeletonPose p, float w, boolean arms) {
        SkeletonPose run = new SkeletonPose();
        run.set(Bone.TORSO, SkeletonPose.RX, 0.55f);
        run.set(Bone.TORSO, SkeletonPose.BEND, 0.18f);
        run.set(Bone.TORSO, SkeletonPose.RY, p.get(Bone.TORSO, SkeletonPose.RY) * 0.4f);
        run.set(Bone.HEAD, SkeletonPose.RX, p.get(Bone.HEAD, SkeletonPose.RX) - 0.6f);
        if (arms) {
            run.set(Bone.RIGHT_ARM, SkeletonPose.RX, 1.25f);
            run.set(Bone.LEFT_ARM, SkeletonPose.RX, 1.25f);
            run.set(Bone.RIGHT_ARM, SkeletonPose.RY, 0f);
            run.set(Bone.LEFT_ARM, SkeletonPose.RY, 0f);
            run.set(Bone.RIGHT_ARM, SkeletonPose.RZ, 0.22f);
            run.set(Bone.LEFT_ARM, SkeletonPose.RZ, -0.22f);
            run.set(Bone.RIGHT_ARM, SkeletonPose.BEND, 0.08f);
            run.set(Bone.LEFT_ARM, SkeletonPose.BEND, 0.08f);
        }
        p.blend(run, w);
    }

    /** In the air: knees tuck on the way up; on the way down the legs reach and the arms balance. */
    static void air(SkeletonPose p, float w, float rising, boolean arms) {
        SkeletonPose air = new SkeletonPose();
        float up = rising, down = 1f - rising;
        air.set(Bone.RIGHT_LEG, SkeletonPose.RX, -0.55f * up - 0.25f * down);
        air.set(Bone.LEFT_LEG, SkeletonPose.RX, 0.05f * up + 0.2f * down);
        air.set(Bone.RIGHT_LEG, SkeletonPose.BEND, 1.35f * up + 0.45f * down);
        air.set(Bone.LEFT_LEG, SkeletonPose.BEND, 0.9f * up + 0.3f * down);
        air.set(Bone.TORSO, SkeletonPose.BEND, 0.12f * up);
        if (arms) {
            air.set(Bone.RIGHT_ARM, SkeletonPose.RX, -0.35f * up - 0.5f * down);
            air.set(Bone.LEFT_ARM, SkeletonPose.RX, -0.2f * up - 0.5f * down);
            air.set(Bone.RIGHT_ARM, SkeletonPose.RZ, 0.35f * up + 0.7f * down);
            air.set(Bone.LEFT_ARM, SkeletonPose.RZ, -0.35f * up - 0.7f * down);
            air.set(Bone.RIGHT_ARM, SkeletonPose.BEND, 0.6f * up + 0.25f * down);
            air.set(Bone.LEFT_ARM, SkeletonPose.BEND, 0.6f * up + 0.25f * down);
        }
        p.blend(air, w);
    }

    /** Fighting stance legs: lead foot forward, rear foot back and out, knees soft. */
    static void stanceLegs(SkeletonPose p, float w) {
        SkeletonPose legs = new SkeletonPose();
        legs.set(Bone.LEFT_LEG, SkeletonPose.RX, -0.32f);
        legs.set(Bone.RIGHT_LEG, SkeletonPose.RX, 0.28f);
        legs.set(Bone.LEFT_LEG, SkeletonPose.RZ, -0.06f);
        legs.set(Bone.RIGHT_LEG, SkeletonPose.RZ, 0.14f);
        legs.set(Bone.LEFT_LEG, SkeletonPose.BEND, 0.5f);
        legs.set(Bone.RIGHT_LEG, SkeletonPose.BEND, 0.42f);
        p.blend(legs, w);
    }

    /** Sinks into the knees by {@code k} (radians of knee bend), feet staying on the ground. */
    static void kneel(SkeletonPose p, float k) {
        add(p, Bone.RIGHT_LEG, SkeletonPose.RX, -0.5f * k);
        add(p, Bone.LEFT_LEG, SkeletonPose.RX, -0.5f * k);
        add(p, Bone.RIGHT_LEG, SkeletonPose.BEND, k);
        add(p, Bone.LEFT_LEG, SkeletonPose.BEND, k);
        add(p, Bone.TORSO, SkeletonPose.RX, 0.25f * k);
        add(p, Bone.RIGHT_ARM, SkeletonPose.BEND, 0.3f * k);
        add(p, Bone.LEFT_ARM, SkeletonPose.BEND, 0.3f * k);
    }

    /** How far the body drops (blocks) when the knees bend by {@code k}: two 6-pixel segments. */
    static float dropFor(float k) {
        return 12f * (1f - Mth.cos(k * 0.5f)) / 16f;
    }

    private static float landingKnees(State state, float time) {
        float t = time - state.landStart;
        if (t < 0f || t > LAND_TICKS) {
            return 0f;
        }
        float e = t < 1.5f ? t / 1.5f : 1f - (t - 1.5f) / (LAND_TICKS - 1.5f);
        return 1.3f * state.landStrength * Math.max(0f, e);
    }

    private static float landingDip(State state, float time) {
        return dropFor(landingKnees(state, time));
    }

    private static float stanceDip(State state, float limbAmount) {
        float still = state.stanceWeight * (1f - state.runWeight) * (1f - Mth.clamp(limbAmount * 2f, 0f, 1f)) * (1f - state.airWeight);
        return state.stanceHasLegs ? 0f : dropFor(0.45f) * still;
    }

    private static float flinchEnvelope(State state, float time) {
        float t = time - state.flinchStart;
        if (t < 0f || t > FLINCH_TICKS) {
            return 0f;
        }
        return t < 1f ? t : Math.max(0f, 1f - (t - 1f) / (FLINCH_TICKS - 1f));
    }

    /** Thrown back from a hit: chest and head snap away from it, arms fly loose, knees give. */
    static void flinch(SkeletonPose p, float e, float front, float side) {
        add(p, Bone.TORSO, SkeletonPose.RX, -0.45f * front * e);
        add(p, Bone.TORSO, SkeletonPose.RZ, -0.3f * side * e);
        add(p, Bone.TORSO, SkeletonPose.RY, 0.35f * side * e);
        add(p, Bone.TORSO, SkeletonPose.BEND, 0.15f * e);
        add(p, Bone.HEAD, SkeletonPose.RX, -0.5f * front * e);
        add(p, Bone.HEAD, SkeletonPose.RZ, -0.3f * side * e);
        add(p, Bone.RIGHT_ARM, SkeletonPose.RX, 0.35f * e);
        add(p, Bone.LEFT_ARM, SkeletonPose.RX, 0.35f * e);
        add(p, Bone.RIGHT_ARM, SkeletonPose.RZ, 0.3f * e);
        add(p, Bone.LEFT_ARM, SkeletonPose.RZ, -0.3f * e);
        add(p, Bone.RIGHT_ARM, SkeletonPose.BEND, 0.4f * e);
        add(p, Bone.LEFT_ARM, SkeletonPose.BEND, 0.4f * e);
        add(p, Bone.RIGHT_LEG, SkeletonPose.BEND, 0.3f * e);
        add(p, Bone.LEFT_LEG, SkeletonPose.BEND, 0.3f * e);
    }

    // ------------------------------------------------------------ footwork: feet stay on the ground

    /** Feet within this many pixels of the ground count as standing on it. */
    static final float PLANT_REACH = 2.5f;
    /** Thigh and shin length (pixels). */
    static final float SEGMENT = 6f;

    /**
     * Keeps standing feet on the ground (two-bone IK). The animation decides where each foot goes
     * across the ground; this only makes sure a foot that should be down really is: when the body
     * lunges, dips or rises, the hips and knees bend to keep the soles planted instead of the feet
     * sliding along, sinking into the floor or floating. Feet the animation lifts (kicks, knees)
     * are left alone. Off while airborne, swimming, riding or flipping.
     */
    static void plantFeet(LivingEntity entity, State state, SkeletonPose pose, float limbAmount) {
        if (!state.procedural || !entity.onGround() || state.airWeight > 0.5f) {
            return;
        }
        SkeletonPose body = state.bodyOut;
        float bx = body.get(Bone.BODY, SkeletonPose.RX), by = body.get(Bone.BODY, SkeletonPose.RY), bz = body.get(Bone.BODY, SkeletonPose.RZ);
        if (Math.abs(bx) > 0.25f || Math.abs(bz) > 0.25f || Math.abs(by) > 0.35f) {
            return; // spinning or flipping: the whole body leaves the ground's frame
        }
        float walking = Mth.clamp(limbAmount * 2f, 0f, 1f);
        float weight = Math.max(state.currentWeight, Math.max(state.stanceWeight, state.guardWeight) * (1f - walking));
        weight = Math.max(weight, 1f - walking) * (1f - state.airWeight);
        if (weight <= 0.01f) {
            return;
        }
        // The body bone's offset in model space (pixels; model Y points down, X is mirrored).
        float ox = -body.get(Bone.BODY, SkeletonPose.PX) * 16f;
        float oy = -body.get(Bone.BODY, SkeletonPose.PY) * 16f;
        float oz = body.get(Bone.BODY, SkeletonPose.PZ) * 16f;
        float shortR = plantLeg(pose, Bone.RIGHT_LEG, Rig.REST[Bone.RIGHT_LEG.ordinal()], ox, oy, oz, weight);
        float shortL = plantLeg(pose, Bone.LEFT_LEG, Rig.REST[Bone.LEFT_LEG.ordinal()], ox, oy, oz, weight);
        // A wide stance the legs can't reach: lower the hips next frame instead of letting a foot hover.
        state.pelvisTarget = Math.min(MAX_PELVIS_DROP, Math.max(shortR, shortL) * weight);
    }

    /** How far (pixels) the hips may sink to keep both feet down. */
    static final float MAX_PELVIS_DROP = 3f;

    /** Plants one leg; returns how many pixels short of the ground it still is (0 if it reached). */
    private static float plantLeg(SkeletonPose pose, Bone leg, float[] rest, float ox, float oy, float oz, float weight) {
        if (Math.abs(pose.get(leg, SkeletonPose.RY)) > 0.3f) {
            return 0f;
        }
        float x = pose.get(leg, SkeletonPose.RX), z = pose.get(leg, SkeletonPose.RZ), b = pose.get(leg, SkeletonPose.BEND);
        float hx = rest[0] + pose.get(leg, SkeletonPose.PX), hy = rest[1] + pose.get(leg, SkeletonPose.PY), hz = rest[2] + pose.get(leg, SkeletonPose.PZ);
        // Where the animation puts the sole (leg plane, then the sideways swing).
        float ly = SEGMENT * (Mth.cos(x) + Mth.cos(x + b));
        float lz = SEGMENT * (Mth.sin(x) + Mth.sin(x + b));
        float fx = hx - ly * Mth.sin(z), fy = hy + ly * Mth.cos(z), fz = hz + lz;
        float ground = 24f;
        if (fy + oy < ground - PLANT_REACH) {
            return 0f; // lifted on purpose (a kick, a knee)
        }
        // The same spot on the ground, in model space.
        float tx = fx, ty = ground - oy, tz = fz;
        float vx = tx - hx, vy = ty - hy, vz = tz - hz;
        float d = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (d < 1.0e-3f) {
            return 0f;
        }
        float reach = Mth.clamp(d, 3f, 2 * SEGMENT - 0.05f);
        float bend = 2f * (float) Math.acos(reach / (2 * SEGMENT));
        float ux = vx / d, uy = vy / d, uz = vz / d;
        float pitch = (float) Math.asin(Mth.clamp(uz, -1f, 1f));
        float roll = (float) Math.atan2(-ux, uy);
        float newX = pitch - bend / 2f;
        pose.set(leg, SkeletonPose.RX, x + (newX - x) * weight);
        pose.set(leg, SkeletonPose.RZ, z + (roll - z) * weight);
        pose.set(leg, SkeletonPose.BEND, b + (bend - b) * weight);
        float horizontal = vx * vx + vz * vz, full = 2 * SEGMENT - 0.05f;
        float maxDown = horizontal >= full * full ? 0f : Mth.sqrt(full * full - horizontal);
        return Math.max(0f, vy - maxDown);
    }

    static void add(SkeletonPose p, Bone bone, int channel, float delta) {
        p.set(bone, channel, (p.isSet(bone, channel) ? p.get(bone, channel) : 0f) + delta);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    // ------------------------------------------------------------ fixed poses

    /** Both forearms up in front of the face, elbows tucked, knees soft. */
    private static SkeletonPose guardPose() {
        SkeletonPose p = new SkeletonPose();
        arm(p, Bone.RIGHT_ARM, -1.15f, -0.5f, 0.1f, 1.9f);
        arm(p, Bone.LEFT_ARM, -1.2f, 0.5f, -0.1f, 1.95f);
        p.set(Bone.TORSO, SkeletonPose.RX, 0.12f);
        p.set(Bone.TORSO, SkeletonPose.BEND, 0.18f);
        p.set(Bone.HEAD, SkeletonPose.RX, 0.25f);
        p.set(Bone.RIGHT_LEG, SkeletonPose.BEND, 0.5f);
        p.set(Bone.LEFT_LEG, SkeletonPose.BEND, 0.5f);
        p.set(Bone.RIGHT_LEG, SkeletonPose.RX, 0.1f);
        p.set(Bone.LEFT_LEG, SkeletonPose.RX, -0.35f);
        return p;
    }

    /** Guard broken: head down, chest folded, arms loose, knees buckling. */
    private static SkeletonPose staggerPose() {
        SkeletonPose p = new SkeletonPose();
        p.set(Bone.HEAD, SkeletonPose.RX, 0.55f);
        p.set(Bone.TORSO, SkeletonPose.RX, 0.25f);
        p.set(Bone.TORSO, SkeletonPose.BEND, 0.4f);
        arm(p, Bone.RIGHT_ARM, 0.35f, 0f, 0.2f, 0.5f);
        arm(p, Bone.LEFT_ARM, 0.35f, 0f, -0.2f, 0.5f);
        p.set(Bone.RIGHT_LEG, SkeletonPose.BEND, 0.55f);
        p.set(Bone.LEFT_LEG, SkeletonPose.BEND, 0.4f);
        return p;
    }

    /** The old hard-coded wind-ups and strike, for moves that have no animation file. */
    private static void legacyPose(SparringNinja.Pose pose, SkeletonPose p) {
        p.clear();
        switch (pose) {
            case WINDUP_LIGHT -> {
                arm(p, Bone.RIGHT_ARM, -1.6f, -0.4f, 0f, 1.6f);
                p.set(Bone.TORSO, SkeletonPose.RY, 0.35f);
            }
            case WINDUP_HEAVY -> {
                arm(p, Bone.RIGHT_ARM, -2.7f, 0f, 0f, 0.7f);
                arm(p, Bone.LEFT_ARM, -2.7f, 0f, 0f, 0.7f);
                p.set(Bone.TORSO, SkeletonPose.RX, -0.15f);
                p.set(Bone.TORSO, SkeletonPose.BEND, -0.1f);
            }
            case ACTIVE -> {
                arm(p, Bone.RIGHT_ARM, -1.55f, 0.1f, 0f, 0.05f);
                arm(p, Bone.LEFT_ARM, 0.4f, 0f, 0f, 1.2f);
                p.set(Bone.TORSO, SkeletonPose.RY, -0.3f);
            }
            default -> { }
        }
    }

    private static void arm(SkeletonPose p, Bone bone, float x, float y, float z, float bend) {
        p.set(bone, SkeletonPose.RX, x);
        p.set(bone, SkeletonPose.RY, y);
        p.set(bone, SkeletonPose.RZ, z);
        p.set(bone, SkeletonPose.BEND, bend);
    }
}

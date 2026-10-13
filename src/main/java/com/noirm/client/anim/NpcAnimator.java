package com.noirm.client.anim;

import com.noirm.Noirm;
import com.noirm.client.anim.SkeletonPose.Bone;
import com.noirm.entity.SparringNinja;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drives an NPC ninja's animation on the client, layer by layer:
 * <ol>
 *   <li>the base (vanilla walk + natural knee/elbow bends, done by the model);</li>
 *   <li>the style's <b>stance</b> loop while they're in a fight ({@code stance_<style>});</li>
 *   <li>the <b>move</b> or <b>roll</b> the server started ({@link SparringNinja#animationId()}),
 *       stretched to the move's real length like the player's PlayerAnimator layer;</li>
 *   <li>a procedural <b>guard</b>, the old hard-coded wind-up poses for moves without an
 *       animation, and a <b>flinch</b> while staggered.</li>
 * </ol>
 * Every layer fades in and out over a few ticks, and a new move cross-fades from the old one,
 * so nothing snaps.
 */
public final class NpcAnimator {
    /** Fades, in ticks. */
    static final float ACTION_FADE_IN = 2f;
    static final float ACTION_FADE_OUT = 3f;
    static final float STANCE_FADE = 3f;
    static final float GUARD_FADE = 3f;
    static final float FLINCH_FADE_IN = 1.5f;
    static final float FLINCH_FADE_OUT = 4f;
    static final float LEGACY_FADE = 2f;

    private static final Map<SparringNinja, State> STATES = new WeakHashMap<>();
    private static final SkeletonPose GUARD = guardPose();
    private static final SkeletonPose FLINCH = flinchPose();

    private NpcAnimator() {}

    /** One animation instance being played. */
    static final class Playing {
        final KeyframeAnimation animation;
        final float start;
        final float duration;
        final float speed;
        /** For the outgoing animation of a cross-fade: its weight when it was replaced, and when. */
        float releasedWeight = -1f;
        float releasedAt;

        Playing(KeyframeAnimation animation, float start, float duration) {
            this.animation = animation;
            this.start = start;
            this.duration = Math.max(1f, duration);
            this.speed = animation.lengthTicks > 0 ? animation.lengthTicks / this.duration : 1f;
        }

        float weight(float time) {
            float elapsed = time - start;
            if (releasedWeight >= 0f) {
                return releasedWeight * (1f - (time - releasedAt) / ACTION_FADE_OUT);
            }
            float in = Math.min(1f, Math.max(0f, elapsed) / ACTION_FADE_IN);
            float out = elapsed > duration ? 1f - (elapsed - duration) / ACTION_FADE_OUT : 1f;
            return in * out;
        }

        void sample(float time, SkeletonPose out) {
            out.clear();
            animation.sample((time - start) * speed, out);
        }
    }

    /** Per-NPC animation state, kept on the client. */
    public static final class State {
        private int seq;
        private boolean seqKnown;
        private Playing current;
        private Playing previous;
        private float lastTime = Float.NaN;
        float stanceWeight;
        float guardWeight;
        float flinchWeight;
        float legacyWeight;
        SparringNinja.Pose legacyPose = SparringNinja.Pose.IDLE;
        KeyframeAnimation stance;
        float time;
        final SkeletonPose stancePose = new SkeletonPose();
        final SkeletonPose currentPose = new SkeletonPose();
        final SkeletonPose previousPose = new SkeletonPose();
        final SkeletonPose legacy = new SkeletonPose();
        float currentWeight;
        float previousWeight;
        /** The whole-body transform (the "body" bone) after all layers, for the renderer. */
        public final SkeletonPose bodyOnly = new SkeletonPose();
    }

    /** The state for this frame ({@code time} = tick count + partial tick); updates it once per new time. */
    public static State stateFor(SparringNinja ninja, float time) {
        State state = STATES.computeIfAbsent(ninja, n -> new State());
        if (state.lastTime != time) {
            update(ninja, state, time);
        }
        return state;
    }

    private static void update(SparringNinja ninja, State state, float time) {
        float dt = Float.isNaN(state.lastTime) ? 1000f : Math.max(0f, Math.min(time - state.lastTime, 5f));
        state.lastTime = time;
        state.time = time;

        // A new move or roll from the server? (The first time we see a ninja, its old animation is history.)
        int seq = ninja.animationSeq();
        if (!state.seqKnown) {
            state.seqKnown = true;
            state.seq = seq;
        } else if (seq != state.seq) {
            state.seq = seq;
            KeyframeAnimation animation = NpcAnimations.get(ResourceLocation.tryParse(ninja.animationId()));
            if (animation != null) {
                if (state.current != null) {
                    state.current.releasedWeight = state.current.weight(time);
                    state.current.releasedAt = time;
                    state.previous = state.current;
                }
                state.current = new Playing(animation, time, ninja.animationTicks());
            }
        }
        state.currentWeight = state.current == null ? 0f : state.current.weight(time);
        if (state.current != null && state.currentWeight <= 0f && time - state.current.start > state.current.duration) {
            state.current = null;
        }
        state.previousWeight = state.previous == null ? 0f : state.previous.weight(time);
        if (state.previous != null && state.previousWeight <= 0f) {
            state.previous = null;
        }
        if (state.current != null) {
            state.current.sample(time, state.currentPose);
        }
        if (state.previous != null) {
            state.previous.sample(time, state.previousPose);
        }

        // The style's stance, held while fighting.
        state.stance = stanceFor(ninja.syncedStyle());
        state.stanceWeight = approach(state.stanceWeight, ninja.isFighting() && state.stance != null ? 1f : 0f, dt / STANCE_FADE);
        if (state.stance != null) {
            state.stancePose.clear();
            state.stance.sample(time, state.stancePose);
        }

        SparringNinja.Pose pose = ninja.ninjaPose();
        state.guardWeight = approach(state.guardWeight, pose == SparringNinja.Pose.GUARD ? 1f : 0f, dt / GUARD_FADE);
        boolean flinch = pose == SparringNinja.Pose.STAGGER;
        state.flinchWeight = approach(state.flinchWeight, flinch ? 1f : 0f, dt / (flinch ? FLINCH_FADE_IN : FLINCH_FADE_OUT));
        // The old wind-up / strike poses, for moves that have no animation file.
        boolean legacy = (pose == SparringNinja.Pose.WINDUP_LIGHT || pose == SparringNinja.Pose.WINDUP_HEAVY
                || pose == SparringNinja.Pose.ACTIVE) && state.current == null;
        if (legacy && pose != state.legacyPose) {
            state.legacyPose = pose;
            legacyPose(pose, state.legacy);
        }
        state.legacyWeight = approach(state.legacyWeight, legacy ? 1f : 0f, dt / LEGACY_FADE);

        state.bodyOnly.clear();
        compose(state, state.bodyOnly);
    }

    /** Lays every layer of {@code state} over {@code pose} (the base), in order. */
    public static void compose(State state, SkeletonPose pose) {
        if (state.stance != null) {
            pose.blend(state.stancePose, state.stanceWeight * (1f - state.guardWeight));
        }
        if (state.previous != null) {
            pose.blend(state.previousPose, state.previousWeight);
        }
        if (state.current != null) {
            pose.blend(state.currentPose, state.currentWeight);
        }
        pose.blend(GUARD, state.guardWeight);
        pose.blend(state.legacy, state.legacyWeight);
        pose.blend(FLINCH, state.flinchWeight);
    }

    private static KeyframeAnimation stanceFor(String style) {
        ResourceLocation id = ResourceLocation.tryParse(style == null ? "" : style);
        KeyframeAnimation stance = id == null || style.isEmpty() ? null
                : NpcAnimations.get(new ResourceLocation(id.getNamespace(), "stance_" + id.getPath()));
        return stance != null ? stance : NpcAnimations.get(Noirm.id("stance_basic"));
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    // ------------------------------------------------------------ procedural poses

    /** Both forearms up in front of the face, elbows tucked. */
    private static SkeletonPose guardPose() {
        SkeletonPose p = new SkeletonPose();
        arm(p, Bone.RIGHT_ARM, -0.95f, -0.45f, 0.1f, 1.75f);
        arm(p, Bone.LEFT_ARM, -0.95f, 0.45f, -0.1f, 1.75f);
        p.set(Bone.TORSO, SkeletonPose.BEND, 0.12f);
        p.set(Bone.HEAD, SkeletonPose.RX, 0.15f);
        return p;
    }

    /** Snapped back by a hit: head down, chest folded, arms loose. */
    private static SkeletonPose flinchPose() {
        SkeletonPose p = new SkeletonPose();
        p.set(Bone.HEAD, SkeletonPose.RX, 0.5f);
        p.set(Bone.TORSO, SkeletonPose.RX, 0.2f);
        p.set(Bone.TORSO, SkeletonPose.BEND, 0.35f);
        arm(p, Bone.RIGHT_ARM, 0.35f, 0f, 0.15f, 0.5f);
        arm(p, Bone.LEFT_ARM, 0.35f, 0f, -0.15f, 0.5f);
        p.set(Bone.RIGHT_LEG, SkeletonPose.BEND, 0.35f);
        p.set(Bone.LEFT_LEG, SkeletonPose.BEND, 0.25f);
        return p;
    }

    /** The pre-animation hard-coded poses (wind-ups and strike), now with elbows. */
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

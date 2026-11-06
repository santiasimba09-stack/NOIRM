package com.noirm.client.anim;

/**
 * One pose of the humanoid skeleton: for each bone, a rotation (radians), a position offset and a
 * bend angle (radians), plus which of those values are "set". Animations write only the channels
 * they have; blending then only touches those, so an upper-body stance leaves the walking legs
 * alone (the same rule PlayerAnimator uses for players).
 *
 * <p>Plain Java (no Minecraft client classes), so game tests can use it on the server.
 *
 * <p>Units, after {@link KeyframeAnimation} has converted the JSON the way PlayerAnimator does:
 * <ul>
 *   <li>parts (torso, head, arms, legs): rotation in radians as {@code ModelPart.xRot/yRot/zRot};
 *       position in pixels, added to the part's rest pivot ({@code ModelPart.x/y/z});</li>
 *   <li>{@code body} (the whole character): rotation in radians and position in blocks, applied
 *       to the render matrix around a pivot 0.7 blocks above the feet;</li>
 *   <li>bend: radians; positive flexes the joint naturally (elbow curls the forearm forward,
 *       knee folds the shin back, torso hunches forward).</li>
 * </ul>
 */
public final class SkeletonPose {
    public enum Bone {
        BODY("body"), TORSO("torso"), HEAD("head"), RIGHT_ARM("right_arm"), LEFT_ARM("left_arm"),
        RIGHT_LEG("right_leg"), LEFT_LEG("left_leg");

        public final String jsonName;

        Bone(String jsonName) {
            this.jsonName = jsonName;
        }

        public static Bone byName(String name) {
            for (Bone bone : values()) {
                if (bone.jsonName.equals(name)) {
                    return bone;
                }
            }
            // PlayerAnimator also accepts camelCase names ("rightArm").
            for (Bone bone : values()) {
                if (bone.jsonName.replace("_", "").equalsIgnoreCase(name)) {
                    return bone;
                }
            }
            return null;
        }
    }

    public static final int RX = 0, RY = 1, RZ = 2, PX = 3, PY = 4, PZ = 5, BEND = 6;
    public static final int CHANNELS = 7;
    private static final int BONES = Bone.values().length;
    private static final float PI = (float) Math.PI;

    private final float[] values = new float[BONES * CHANNELS];
    private final boolean[] set = new boolean[BONES * CHANNELS];

    private static int index(Bone bone, int channel) {
        return bone.ordinal() * CHANNELS + channel;
    }

    public float get(Bone bone, int channel) {
        return values[index(bone, channel)];
    }

    public boolean isSet(Bone bone, int channel) {
        return set[index(bone, channel)];
    }

    public void set(Bone bone, int channel, float value) {
        int i = index(bone, channel);
        values[i] = value;
        set[i] = true;
    }

    /** Everything back to zero and unset. */
    public void clear() {
        java.util.Arrays.fill(values, 0f);
        java.util.Arrays.fill(set, false);
    }

    /** Unsets every channel of one bone. */
    public void clear(Bone bone) {
        for (int c = 0; c < CHANNELS; c++) {
            values[index(bone, c)] = 0f;
            set[index(bone, c)] = false;
        }
    }

    public void copyFrom(SkeletonPose other) {
        System.arraycopy(other.values, 0, values, 0, values.length);
        System.arraycopy(other.set, 0, set, 0, set.length);
    }

    /**
     * Moves this pose toward {@code layer} by {@code weight} (0 = unchanged, 1 = the layer's
     * values), only on the channels the layer sets. Rotations take the short way round, so a
     * spin that ends at 360° fades back to 0° without unwinding.
     */
    public void blend(SkeletonPose layer, float weight) {
        if (weight <= 0f) {
            return;
        }
        float w = Math.min(weight, 1f);
        for (int i = 0; i < values.length; i++) {
            if (!layer.set[i]) {
                continue;
            }
            float from = set[i] ? values[i] : 0f;
            float to = layer.values[i];
            int channel = i % CHANNELS;
            if (channel <= RZ || channel == BEND) {
                float diff = wrap(to - from);
                values[i] = w >= 1f ? from + diff : from + diff * w;
            } else {
                values[i] = from + (to - from) * w;
            }
            set[i] = true;
        }
    }

    /** Wraps an angle difference into [-π, π]. */
    static float wrap(float angle) {
        float a = angle % (2 * PI);
        if (a > PI) {
            a -= 2 * PI;
        } else if (a < -PI) {
            a += 2 * PI;
        }
        return a;
    }

    /** True if every value is a finite number (no NaN or infinity). */
    public boolean isFinite() {
        for (float v : values) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }
}

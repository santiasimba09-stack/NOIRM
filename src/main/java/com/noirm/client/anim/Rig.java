package com.noirm.client.anim;

import com.noirm.client.anim.SkeletonPose.Bone;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The skeleton's hierarchy, baked into flat part transforms.
 *
 * <p>Vanilla's humanoid model has no hierarchy: the torso, head and arms are separate parts, so
 * twisting the torso leaves the shoulders behind (the reason a turned punch used to look
 * disjointed). Here the torso turns about the <b>hips</b> and carries the head and both arms with
 * it, and the waist bend carries them again; the legs stay on the hips. The result is written back
 * as plain positions and Euler angles per part, so everything that reads vanilla part poses (armor,
 * held items, eye layers) follows without knowing about the hierarchy.
 *
 * <p>Model space is vanilla's: pixels, Y pointing down, the model facing −Z. Rotations use
 * vanilla's order ({@code rotationZYX(z, y, x)}, as {@code ModelPart.translateAndRotate}).
 *
 * <p>Plain Java + JOML (no client classes), so game tests can check it.
 */
public final class Rig {
    /** Rest pivots (pixels) of each bone in model space; BODY is unused. */
    public static final float[][] REST = {
            {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
    /** The torso turns about the hips… */
    public static final float HIP_Y = 12f;
    /** …and bends at the waist (halfway down). */
    public static final float WAIST_Y = 6f;

    /** One baked part: pivot position (pixels) and Euler angles (radians, vanilla order). */
    public static final class Part {
        public float x, y, z, xRot, yRot, zRot;
    }

    public final Part torso = new Part();
    public final Part head = new Part();
    public final Part rightArm = new Part();
    public final Part leftArm = new Part();
    public final Part rightLeg = new Part();
    public final Part leftLeg = new Part();

    private final Quaternionf torsoRot = new Quaternionf();
    private final Quaternionf waist = new Quaternionf();
    private final Quaternionf scratch = new Quaternionf();
    private final Vector3f v = new Vector3f();
    private final Vector3f torsoPos = new Vector3f();
    private final Matrix3f m = new Matrix3f();

    /** Bakes {@code pose} (rotations in radians, positions as offsets from rest, in pixels). */
    public void bake(SkeletonPose pose) {
        torsoRot.rotationZYX(pose.get(Bone.TORSO, SkeletonPose.RZ), pose.get(Bone.TORSO, SkeletonPose.RY), pose.get(Bone.TORSO, SkeletonPose.RX));
        waist.rotationX(pose.get(Bone.TORSO, SkeletonPose.BEND));
        // The torso pivots on the hips: its part pivot moves to H + offset − R·H.
        float ox = pose.get(Bone.TORSO, SkeletonPose.PX), oy = pose.get(Bone.TORSO, SkeletonPose.PY), oz = pose.get(Bone.TORSO, SkeletonPose.PZ);
        torsoRot.transform(v.set(0, HIP_Y, 0));
        torsoPos.set(ox - v.x, HIP_Y + oy - v.y, oz - v.z);
        set(torso, torsoPos.x, torsoPos.y, torsoPos.z, torsoRot);

        upper(pose, Bone.HEAD, head);
        upper(pose, Bone.RIGHT_ARM, rightArm);
        upper(pose, Bone.LEFT_ARM, leftArm);
        plain(pose, Bone.RIGHT_LEG, rightLeg);
        plain(pose, Bone.LEFT_LEG, leftLeg);
    }

    /** A part riding the upper torso: pos = T + R·(W + B·(q − W)), rot = R·B·Rp. */
    private void upper(SkeletonPose pose, Bone bone, Part out) {
        float[] rest = REST[bone.ordinal()];
        v.set(rest[0] + pose.get(bone, SkeletonPose.PX), rest[1] + pose.get(bone, SkeletonPose.PY) - WAIST_Y, rest[2] + pose.get(bone, SkeletonPose.PZ));
        waist.transform(v);
        v.y += WAIST_Y;
        torsoRot.transform(v);
        v.add(torsoPos);
        scratch.set(torsoRot).mul(waist)
                .rotateZYX(pose.get(bone, SkeletonPose.RZ), pose.get(bone, SkeletonPose.RY), pose.get(bone, SkeletonPose.RX));
        set(out, v.x, v.y, v.z, scratch);
    }

    private void plain(SkeletonPose pose, Bone bone, Part out) {
        float[] rest = REST[bone.ordinal()];
        out.x = rest[0] + pose.get(bone, SkeletonPose.PX);
        out.y = rest[1] + pose.get(bone, SkeletonPose.PY);
        out.z = rest[2] + pose.get(bone, SkeletonPose.PZ);
        out.xRot = pose.get(bone, SkeletonPose.RX);
        out.yRot = pose.get(bone, SkeletonPose.RY);
        out.zRot = pose.get(bone, SkeletonPose.RZ);
    }

    private void set(Part out, float x, float y, float z, Quaternionf rotation) {
        out.x = x;
        out.y = y;
        out.z = z;
        float[] e = eulerZYX(rotation, m);
        out.xRot = e[0];
        out.yRot = e[1];
        out.zRot = e[2];
    }

    /**
     * The angles {x, y, z} with {@code rotationZYX(z, y, x)} equal to {@code q}. For M = Rz·Ry·Rx
     * the bottom row is (−sin y, cos y·sin x, cos y·cos x) and the first column (cos z·cos y, sin z·cos y, …).
     */
    public static float[] eulerZYX(Quaternionf q, Matrix3f m) {
        m.set(q);
        // JOML: mCR = column C, row R.
        float sy = Math.max(-1f, Math.min(1f, -m.m02));
        float y = (float) Math.asin(sy);
        float x, z;
        if (Math.abs(sy) < 0.9999f) {
            x = (float) Math.atan2(m.m12, m.m22);
            z = (float) Math.atan2(m.m01, m.m00);
        } else { // gimbal lock: put it all in x
            x = (float) Math.atan2(-m.m21, m.m11);
            z = 0f;
        }
        return new float[]{x, y, z};
    }
}

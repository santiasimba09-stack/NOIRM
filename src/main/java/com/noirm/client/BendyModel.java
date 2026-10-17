package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.client.anim.SkeletonPose;
import com.noirm.client.anim.SkeletonPose.Bone;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ninja's body with more moving parts, in the spirit of Epic Fight's jointed armature and
 * Better Combat's bendy limbs: each arm and leg is an upper and a lower segment (elbow / knee
 * joints), and the torso is two halves that bend at the waist (the head and arms ride along).
 *
 * <p>It reads the standard 64×64 player skin: every 12-pixel limb region is cut in two, the upper
 * half drawing rows 0–5 of the limb's sides and the lower half rows 6–11, with the top and the
 * sole/hand faces kept where the vanilla model has them; the outer layer (sleeves, pants,
 * jacket) is split the same way. The faces where the halves meet are "caps" textured from the
 * limb's own side pixels, so a bent joint shows skin instead of a hole.
 * {@code tools/animgen/check_uv.py} checks every rectangle against the skin layout.
 *
 * <p>Still a {@link PlayerModel} with the vanilla part names, so armor, held items and the eye
 * layer keep working; the vanilla sleeve / pants / jacket parts are left empty because the
 * segments carry their own outer layer.
 */
public class BendyModel<T extends LivingEntity> extends PlayerModel<T> {
    private static final float TEX = 64f;
    private static final Set<Direction> TOP_AND_SIDES = EnumSet.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    private static final Set<Direction> SIDES = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    private static final Set<Direction> BOTTOM = EnumSet.of(Direction.UP); // model Y points down: "UP" is the face at max Y
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);
    /** How far the outer skin layer sits outside the inner one (vanilla: 0.25). */
    private static final float LAYER = 0.25f;
    /** Where each joint is, measured from the top of its 12-pixel limb. */
    private static final float JOINT = 6f;

    public final ModelPart rightForearm;
    public final ModelPart leftForearm;
    public final ModelPart rightShin;
    public final ModelPart leftShin;
    public final ModelPart upperTorso;
    private final SkeletonPose scratch = new SkeletonPose();

    private final boolean slimArms;
    /** This frame's final pose (after every animation layer and the hierarchy), for first-person arms. */
    public final SkeletonPose lastPose = new SkeletonPose();
    private final com.noirm.client.anim.Rig rig = new com.noirm.client.anim.Rig();

    public BendyModel() {
        this(false);
    }

    /** {@code slim}: the 3-pixel arms of "slim" (Alex-type) player skins. */
    public BendyModel(boolean slim) {
        this(createRoot(slim), slim);
    }

    private BendyModel(ModelPart root, boolean slim) {
        super(root, slim);
        this.slimArms = slim;
        rightForearm = rightArm.getChild("forearm");
        leftForearm = leftArm.getChild("forearm");
        rightShin = rightLeg.getChild("shin");
        leftShin = leftLeg.getChild("shin");
        upperTorso = body.getChild("upper");
    }

    // ------------------------------------------------------------ geometry

    /** UV origins (inner layer, outer layer) of each 64×64 skin region; see check_uv.py. */
    static final int[] BODY_UV = {16, 16, 16, 32};
    static final int[] RIGHT_ARM_UV = {40, 16, 40, 32};
    static final int[] LEFT_ARM_UV = {32, 48, 48, 48};
    static final int[] RIGHT_LEG_UV = {0, 16, 0, 32};
    static final int[] LEFT_LEG_UV = {16, 48, 0, 48};

    static ModelPart createRoot(boolean slim) {
        Map<String, ModelPart> root = new HashMap<>();
        root.put("head", part(0, 0, 0, List.of(cube(0, 0, -4, -8, -4, 8, 8, 8, 0, 0, ALL)), Map.of()));
        root.put("hat", part(0, 0, 0, List.of(cube(32, 0, -4, -8, -4, 8, 8, 8, 0.5f, 0.5f, ALL)), Map.of()));

        // Torso: the lower half belongs to the body part; the upper half is a child that bends at the waist.
        List<ModelPart.Cube> torsoUpper = new ArrayList<>();
        half(torsoUpper, BODY_UV, -4, -JOINT, -2, 8, 4, true);
        List<ModelPart.Cube> torsoLower = new ArrayList<>();
        half(torsoLower, BODY_UV, -4, JOINT, -2, 8, 4, false);
        root.put("body", part(0, 0, 0, torsoLower, Map.of("upper", part(0, JOINT, 0, torsoUpper, Map.of()))));

        int arm = slim ? 3 : 4;
        root.put("right_arm", limb(RIGHT_ARM_UV, -5, 2, slim ? -2 : -3, -2, arm, "forearm"));
        root.put("left_arm", limb(LEFT_ARM_UV, 5, 2, -1, -2, arm, "forearm"));
        root.put("right_leg", limb(RIGHT_LEG_UV, -1.9f, 12, -2, 0, 4, "shin"));
        root.put("left_leg", limb(LEFT_LEG_UV, 1.9f, 12, -2, 0, 4, "shin"));

        for (String empty : new String[]{"ear", "cloak", "left_sleeve", "right_sleeve", "left_pants", "right_pants", "jacket"}) {
            root.put(empty, part(0, 0, 0, List.of(), Map.of()));
        }
        return new ModelPart(List.of(), root);
    }

    /**
     * An arm or leg: the upper segment at the shoulder / hip pivot, the lower segment a child at
     * the joint (on the limb's centre line, halfway down).
     */
    private static ModelPart limb(int[] uv, float pivotX, float pivotY, float boxX, float boxTop, int width, String lowerName) {
        List<ModelPart.Cube> upper = new ArrayList<>();
        half(upper, uv, boxX, boxTop, -2, width, 4, true);
        float jointX = boxX + width / 2f; // centre of the limb
        float jointY = boxTop + JOINT;
        List<ModelPart.Cube> lower = new ArrayList<>();
        half(lower, uv, -width / 2f, 0, -2, width, 4, false);
        ModelPart lowerPart = part(jointX, jointY, 0, lower, Map.of());
        return part(pivotX, pivotY, 0, upper, Map.of(lowerName, lowerPart));
    }

    /**
     * One 6-pixel half of a 12-pixel limb (both skin layers), with {@code top} its upper edge in
     * the holding part's coordinates.
     */
    static void half(List<ModelPart.Cube> out, int[] uv, float x, float top, float z, int w, int d, boolean upperHalf) {
        int u = uv[0], v = uv[1], uo = uv[2], vo = uv[3];
        int h = (int) JOINT;
        float g = LAYER, gy = LAYER / 2; // outer layer: grow sideways fully, but only outward at the ends
        if (upperHalf) {
            out.add(cube(u, v, x, top, z, w, h, d, 0, 0, TOP_AND_SIDES));           // top face + upper side rows
            out.add(cube(u, v + h, x, top + h, z, w, 0, d, 0, 0, BOTTOM));          // joint cap (limb-side pixels)
            out.add(cube(uo, vo, x, top - gy, z, w, h, d, g, gy, TOP_AND_SIDES));   // outer layer
        } else {
            out.add(cube(u, v + h, x, top, z, w, h, d, 0, 0, TOP_AND_SIDES));       // joint cap + lower side rows
            out.add(cube(u, v, x, top + h, z, w, 0, d, 0, 0, BOTTOM));              // hand / sole
            out.add(cube(uo, vo + h, x, top + gy, z, w, h, d, g, gy, SIDES));       // outer layer sides
            out.add(cube(uo, vo, x, top + h + gy, z, w, 0, d, g, gy, BOTTOM));      // outer layer end
        }
    }

    private static ModelPart.Cube cube(int u, int v, float x, float y, float z, float w, float h, float d, float grow, float growY, Set<Direction> faces) {
        return new ModelPart.Cube(u, v, x, y, z, w, h, d, grow, growY, grow, false, TEX, TEX, faces);
    }

    private static ModelPart part(float x, float y, float z, List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {
        ModelPart part = new ModelPart(cubes, new HashMap<>(children));
        PartPose pose = PartPose.offset(x, y, z);
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }

    // ------------------------------------------------------------ posing

    private ModelPart partFor(Bone bone) {
        return switch (bone) {
            case TORSO -> body;
            case HEAD -> head;
            case RIGHT_ARM -> rightArm;
            case LEFT_ARM -> leftArm;
            case RIGHT_LEG -> rightLeg;
            case LEFT_LEG -> leftLeg;
            default -> null;
        };
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Vanilla doesn't reset every position it never touches (leg x, head x/z…); last frame's
        // animated values must not leak into this frame's base pose.
        for (ModelPart part : new ModelPart[]{body, head, hat, rightArm, leftArm, rightLeg, leftLeg}) {
            part.resetPose();
        }
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        SkeletonPose pose = scratch;
        captureVanilla(pose);
        // Our layers: gait, stance, moves, guard, flinch, landing… then springs (HumanoidAnimator).
        com.noirm.client.anim.HumanoidAnimator.animateParts(entity, this, pose, limbSwing, limbSwingAmount, ageInTicks);
        lastPose.copyFrom(pose);
        applyPose(pose);
    }

    /**
     * Vanilla's pose as the base layer, rewritten for our hierarchy: vanilla fakes a torso twist
     * (attacks) and a crouch by moving the arms and head by hand; here the torso carries them, so
     * those hand-made offsets are taken back out and only the torso's own rotation is kept.
     */
    private void captureVanilla(SkeletonPose pose) {
        pose.clear();
        float twist = body.yRot;
        boolean crouch = crouching;
        for (Bone bone : Bone.values()) {
            ModelPart part = partFor(bone);
            if (part == null) {
                continue;
            }
            float[] rest = com.noirm.client.anim.Rig.REST[bone.ordinal()];
            boolean upper = bone == Bone.HEAD || bone == Bone.RIGHT_ARM || bone == Bone.LEFT_ARM;
            pose.set(bone, SkeletonPose.RX, part.xRot);
            pose.set(bone, SkeletonPose.RY, part.yRot);
            pose.set(bone, SkeletonPose.RZ, part.zRot);
            boolean resetPosition = bone == Bone.TORSO || upper || crouch;
            pose.set(bone, SkeletonPose.PX, resetPosition ? 0 : part.x - rest[0]);
            pose.set(bone, SkeletonPose.PY, resetPosition ? 0 : part.y - rest[1]);
            pose.set(bone, SkeletonPose.PZ, resetPosition ? 0 : part.z - rest[2]);
        }
        if (twist != 0f) { // the arms turned with the torso by hand; the hierarchy does it now
            pose.set(Bone.RIGHT_ARM, SkeletonPose.RY, rightArm.yRot - twist);
            pose.set(Bone.LEFT_ARM, SkeletonPose.RY, leftArm.yRot - twist);
            pose.set(Bone.LEFT_ARM, SkeletonPose.RX, leftArm.xRot - twist);
        }
        if (crouch) { // lean from the hips with soft knees, instead of vanilla's shifted parts
            pose.set(Bone.TORSO, SkeletonPose.RX, 0.42f);
            pose.set(Bone.RIGHT_LEG, SkeletonPose.BEND, 0.6f);
            pose.set(Bone.LEFT_LEG, SkeletonPose.BEND, 0.6f);
            pose.set(Bone.RIGHT_LEG, SkeletonPose.RX, rightLeg.xRot - 0.3f);
            pose.set(Bone.LEFT_LEG, SkeletonPose.RX, leftLeg.xRot - 0.3f);
        }
    }

    /** The final pose onto the model parts, through the hierarchy ({@link com.noirm.client.anim.Rig}). */
    private void applyPose(SkeletonPose pose) {
        rig.bake(pose);
        copy(rig.torso, body);
        copy(rig.head, head);
        copy(rig.rightArm, rightArm);
        copy(rig.leftArm, leftArm);
        copy(rig.rightLeg, rightLeg);
        copy(rig.leftLeg, leftLeg);
        setBends(pose.get(Bone.RIGHT_ARM, SkeletonPose.BEND), pose.get(Bone.LEFT_ARM, SkeletonPose.BEND),
                pose.get(Bone.RIGHT_LEG, SkeletonPose.BEND), pose.get(Bone.LEFT_LEG, SkeletonPose.BEND),
                pose.get(Bone.TORSO, SkeletonPose.BEND));
        hat.copyFrom(head);
    }

    private static void copy(com.noirm.client.anim.Rig.Part from, ModelPart to) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
    }

    /** Positive bends flex the joints: forearms curl forward, shins fold back, the chest leans in. */
    private void setBends(float rightElbow, float leftElbow, float rightKnee, float leftKnee, float waist) {
        rightForearm.setRotation(-rightElbow, 0, 0);
        leftForearm.setRotation(-leftElbow, 0, 0);
        rightShin.setRotation(rightKnee, 0, 0);
        leftShin.setRotation(leftKnee, 0, 0);
        upperTorso.setRotation(waist, 0, 0);
    }

    // ------------------------------------------------------------ drawing

    /**
     * Kept for callers from before the hierarchy was baked into the parts: the head and arms now
     * already ride the torso, so there is nothing left to add.
     */
    public void applyUpperBodyTransform(PoseStack poseStack) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
        body.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        rightLeg.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        leftLeg.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        head.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        hat.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        rightArm.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        leftArm.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
    }

    /** Held items sit in the hand at the end of the bent forearm. */
    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {
        ModelPart arm = getArm(side);
        ModelPart forearm = side == HumanoidArm.RIGHT ? rightForearm : leftForearm;
        float shift = slimArms ? 0.5f * (side == HumanoidArm.RIGHT ? 1 : -1) : 0f;
        arm.x += shift;
        arm.translateAndRotate(poseStack);
        arm.x -= shift;
        poseStack.translate(forearm.x / 16f, forearm.y / 16f, forearm.z / 16f);
        poseStack.mulPose(new Quaternionf().rotationZYX(forearm.zRot, forearm.yRot, forearm.xRot));
        poseStack.translate(-forearm.x / 16f, -forearm.y / 16f, -forearm.z / 16f);
    }

    /** Armor follows the joints too (the part poses are copied by vanilla; the bends here). */
    @Override
    public void copyPropertiesTo(net.minecraft.client.model.HumanoidModel<T> model) {
        super.copyPropertiesTo(model);
        if (model instanceof BendyArmorModel<T> armor) {
            armor.rightForearm.copyFrom(rightForearm);
            armor.leftForearm.copyFrom(leftForearm);
            armor.rightShin.copyFrom(rightShin);
            armor.leftShin.copyFrom(leftShin);
            armor.upperTorso.copyFrom(upperTorso);
        }
    }

    public boolean slim() {
        return slimArms;
    }
}

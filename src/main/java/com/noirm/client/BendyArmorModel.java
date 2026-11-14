package com.noirm.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Armor (and NOIRM gear) shaped like {@link BendyModel}: the sleeves and leggings are cut at the
 * elbow and knee and the chest at the waist, so clothes bend with the body instead of sticking
 * out straight. It reads the standard 64×32 armor texture layout (left limbs mirror the right ones),
 * so every vanilla armor and every NOIRM gear texture fits unchanged. UVs are normalised, so a
 * texture at any multiple of 64×32 works: NOIRM's gear is HD at 4× (256×128, tools/gear/hd.py).
 * {@link BendyModel#copyPropertiesTo} hands it the joint angles along with the part poses.
 */
public class BendyArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
    private static final Set<Direction> TOP_AND_SIDES = EnumSet.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    private static final Set<Direction> BOTTOM = EnumSet.of(Direction.UP);
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);
    private static final float JOINT = 6f;

    public final ModelPart rightForearm;
    public final ModelPart leftForearm;
    public final ModelPart rightShin;
    public final ModelPart leftShin;
    public final ModelPart upperTorso;

    /** {@code grow}: 1.0 for helmets, chestplates and boots, 0.5 for leggings (vanilla's sizes). */
    public BendyArmorModel(float grow) {
        this(createRoot(grow));
    }

    private BendyArmorModel(ModelPart root) {
        super(root);
        rightForearm = rightArm.getChild("forearm");
        leftForearm = leftArm.getChild("forearm");
        rightShin = rightLeg.getChild("shin");
        leftShin = leftLeg.getChild("shin");
        upperTorso = body.getChild("upper");
    }

    /** Parts the body under it has lost (gore; set by {@link BendyModel#copyPropertiesTo}): not drawn. */
    public int severedMask;

    /** Like vanilla, but leaves out the pieces over a severed part (bits: head, right/left arm, right/left leg). */
    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light,
                               int overlay, float red, float green, float blue, float alpha) {
        int cut = severedMask;
        if ((cut & 1) == 0) {
            head.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
            hat.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        }
        body.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        if ((cut & 2) == 0) {
            rightArm.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        }
        if ((cut & 4) == 0) {
            leftArm.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        }
        if ((cut & 8) == 0) {
            rightLeg.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        }
        if ((cut & 16) == 0) {
            leftLeg.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
        }
    }

    static ModelPart createRoot(float g) {
        Map<String, ModelPart> root = new HashMap<>();
        root.put("head", part(0, 0, 0, List.of(cube(0, 0, -4, -8, -4, 8, 8, 8, g, false, ALL)), Map.of()));
        root.put("hat", part(0, 0, 0, List.of(cube(32, 0, -4, -8, -4, 8, 8, 8, g + 0.5f, false, ALL)), Map.of()));
        List<ModelPart.Cube> upper = new ArrayList<>();
        half(upper, 16, 16, -4, -JOINT, -2, 8, 4, g, true, false);
        List<ModelPart.Cube> lower = new ArrayList<>();
        half(lower, 16, 16, -4, JOINT, -2, 8, 4, g, false, false);
        root.put("body", part(0, 0, 0, lower, Map.of("upper", part(0, JOINT, 0, upper, Map.of()))));
        root.put("right_arm", limb(40, 16, -5, 2, -3, -2, g, false, "forearm"));
        root.put("left_arm", limb(40, 16, 5, 2, -1, -2, g, true, "forearm"));
        root.put("right_leg", limb(0, 16, -1.9f, 12, -2, 0, g, false, "shin"));
        root.put("left_leg", limb(0, 16, 1.9f, 12, -2, 0, g, true, "shin"));
        return new ModelPart(List.of(), root);
    }

    private static ModelPart limb(int u, int v, float pivotX, float pivotY, float boxX, float boxTop, float g, boolean mirror, String lowerName) {
        List<ModelPart.Cube> upper = new ArrayList<>();
        half(upper, u, v, boxX, boxTop, -2, 4, 4, g, true, mirror);
        List<ModelPart.Cube> lower = new ArrayList<>();
        half(lower, u, v, -2, 0, -2, 4, 4, g, false, mirror);
        ModelPart lowerPart = part(boxX + 2, boxTop + JOINT, 0, lower, Map.of());
        return part(pivotX, pivotY, 0, upper, Map.of(lowerName, lowerPart));
    }

    /** One half of a 12-pixel piece; the face where the halves meet is capped with side pixels. */
    private static void half(List<ModelPart.Cube> out, int u, int v, float x, float top, float z, int w, int d, float g, boolean upperHalf, boolean mirror) {
        int h = (int) JOINT;
        if (upperHalf) {
            out.add(cube(u, v, x, top, z, w, h, d, g, mirror, TOP_AND_SIDES));
            out.add(cube(u, v + h, x, top + h, z, w, 0, d, g, mirror, BOTTOM));
        } else {
            out.add(cube(u, v + h, x, top, z, w, h, d, g, mirror, TOP_AND_SIDES));
            out.add(cube(u, v, x, top + h, z, w, 0, d, g, mirror, BOTTOM));
        }
    }

    private static ModelPart.Cube cube(int u, int v, float x, float y, float z, float w, float h, float d, float g, boolean mirror, Set<Direction> faces) {
        return new ModelPart.Cube(u, v, x, y, z, w, h, d, g, g, g, mirror, 64f, 32f, faces);
    }

    private static ModelPart part(float x, float y, float z, List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {
        ModelPart part = new ModelPart(cubes, new HashMap<>(children));
        PartPose pose = PartPose.offset(x, y, z);
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }
}

package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noirm.Noirm;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Gore on worn clothes and armor (see {@code com.noirm.gore.Gore}): the same dirt, soot, cuts
 * and blood as on the body, drawn over every worn piece on the bending armor shape
 * ({@link BendyArmorModel}), so outfits get dirty, burnt, torn and bloody. Bruises stay on the
 * skin. The textures are in the armor layout ({@code textures/entity/gore/armor_<kind>_<1-3>.png},
 * from {@code tools/goregen/gen_gore.py}), so they fit every vanilla armor and NOIRM gear piece.
 * Add it after the armor layer. Allocation-free: render types and models are built once.
 */
public class GoreArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** Kinds drawn on clothes, as indexes into {@link GoreMarksLayer#KINDS}: dirt, soot, cut (blood apart). */
    private static final int[] CLOTH_KINDS = {1, 2, 3};
    private static final RenderType[][] TYPES = new RenderType[GoreMarksLayer.KINDS.length][GoreMarksLayer.STAGES];
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    static {
        for (int kind = 1; kind < GoreMarksLayer.KINDS.length; kind++) {
            for (int stage = 0; stage < GoreMarksLayer.STAGES; stage++) {
                TYPES[kind][stage] = RenderType.entityTranslucent(Noirm.id("textures/entity/gore/armor_" + GoreMarksLayer.KINDS[kind]
                        + "_" + (stage + 1) + ".png"));
            }
        }
    }

    /** The same sizes as the armor layer's (leggings 0.5, the rest 1.0), so the marks sit on the cloth. */
    private final BendyArmorModel<T> inner = new BendyArmorModel<>(0.5f);
    private final BendyArmorModel<T> outer = new BendyArmorModel<>(1.0f);

    public GoreArmorLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        int packed = GoreClient.marks(entity.getId());
        int blood = GoreClient.blood(entity.getId());
        if ((packed & 0xFFFFFF00) == 0 && blood == 0) {
            return; // nothing that shows on cloth (bruises only, or nothing)
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        float dry = 1f - GoreClient.bloodFresh(blood);
        float br = 1f + (GoreClient.DRY_R - 1f) * dry, bg = 1f + (GoreClient.DRY_G - 1f) * dry, bb = 1f + (GoreClient.DRY_B - 1f) * dry;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) {
                continue;
            }
            BendyArmorModel<T> model = slot == EquipmentSlot.LEGS ? inner : outer;
            getParentModel().copyPropertiesTo(model);
            show(model, slot);
            for (int kind : CLOTH_KINDS) {
                GoreMarksLayer.stages(model, poseStack, buffers, TYPES[kind], GoreClient.level(packed, kind), light, overlay, 1f, 1f, 1f);
            }
            if (blood != 0) {
                GoreMarksLayer.stages(model, poseStack, buffers, TYPES[GoreMarksLayer.BLOOD], GoreClient.bloodLevel(blood), light, overlay,
                        br, bg, bb);
            }
        }
    }

    /** The parts a piece in this slot covers (as vanilla's armor layer shows them). */
    private static void show(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }
}

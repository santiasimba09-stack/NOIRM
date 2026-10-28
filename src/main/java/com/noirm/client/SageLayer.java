package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noirm.Noirm;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * The sage path on the body (players): <b>petrification</b> as stone creeping over the skin
 * ({@code textures/entity/sage/petrify_<1-3>.png}: hands and feet, then the limbs, then all but
 * the face; a statue is all stone, {@code petrify_4}), and in <b>Sage Mode</b> the pigment marks
 * around the eyes with sage eyes, one overlay per eye style so they sit inside its lids
 * ({@code textures/entity/sage/sage_eyes_<style>.png}). All drawn at the skin's 4× resolution by
 * {@code tools/skingen/gen_sage.py}.
 */
public class SageLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final String[] STYLES = {"round", "sharp", "wide", "narrow", "pale"};
    private static final RenderType[] EYES = new RenderType[STYLES.length];
    private static final RenderType[] STONE = new RenderType[4];
    /** The irises glow a little (not full-bright: the marks are paint, not light). */
    private static final int EYE_LIGHT = 0xA000A0;

    static {
        for (int i = 0; i < STYLES.length; i++) {
            EYES[i] = RenderType.entityCutoutNoCull(Noirm.id("textures/entity/sage/sage_eyes_" + STYLES[i] + ".png"));
        }
        for (int i = 0; i < STONE.length; i++) {
            STONE[i] = RenderType.entityTranslucent(Noirm.id("textures/entity/sage/petrify_" + (i + 1) + ".png"));
        }
    }

    public SageLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        int id = entity.getId();
        M model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        if (ClientSage.sage(id) && (GoreClient.severed(id) & 1) == 0) {
            int style = SharinganEyesLayer.eyeStyle(entity);
            model.head.render(poseStack, buffers.getBuffer(EYES[style]), Math.max(light, EYE_LIGHT), OverlayTexture.NO_OVERLAY);
        }
        float stone = ClientSage.petrification(id);
        if (ClientSage.statue(id)) {
            model.renderToBuffer(poseStack, buffers.getBuffer(STONE[3]), light, overlay, 1f, 1f, 1f, 1f);
        } else if (stone > 0f) {
            // The stage reached fully, the next one fading in (like gore marks).
            float s = stone / 25f;
            int full = Math.min(3, (int) s);
            float next = s - full;
            if (full > 0) {
                model.renderToBuffer(poseStack, buffers.getBuffer(STONE[full - 1]), light, overlay, 1f, 1f, 1f, 1f);
            }
            if (full < 3 && next > 0.12f) {
                model.renderToBuffer(poseStack, buffers.getBuffer(STONE[full]), light, overlay, 1f, 1f, 1f, next);
            }
        }
    }
}

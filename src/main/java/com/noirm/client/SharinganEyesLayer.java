package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.creator.PlayerLook;
import com.noirm.entity.NinjaLook;
import com.noirm.entity.SparringNinja;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Active dōjutsu over the eyes (players and ninjas). Each eye style ({@link PlayerLook} "eyes":
 * round, sharp, wide, narrow, pale) has its own overlay, drawn by {@code tools/skingen/gen_parts.py}
 * to fit that style's lids exactly: the Sharingan is opaque and full-bright (a red iris with black
 * tomoe, the heavy lid still cutting across it), the Byakugan glows additively.
 */
public class SharinganEyesLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final String[] STYLES = {"round", "sharp", "wide", "narrow", "pale"};
    private static final RenderType[] SHARINGAN = new RenderType[STYLES.length];
    private static final RenderType[] BYAKUGAN = new RenderType[STYLES.length];
    /**
     * Where the iris is in each eye style, for the Mangekyō pattern: one eye's centre (model pixels
     * from the middle of the head's front; the other eye is mirrored), its radius, and the band of
     * it you can see between the lids (top, bottom). Printed by {@code gen_parts.py} as "mangekyo fit".
     */
    private static final float[][] IRIS = {
            // x, y, radius, visible top, visible bottom
            {1.375f, -3.625f, 0.6f, -4.0f, -3.0f},    // round
            {1.375f, -3.625f, 0.56f, -4.0f, -3.0f},   // sharp
            {1.375f, -3.625f, 0.56f, -4.25f, -3.0f},  // wide
            {1.375f, -3.375f, 0.56f, -4.0f, -3.25f},  // narrow
            {1.375f, -3.625f, 0.6f, -4.0f, -3.0f},    // pale
    };
    private static final Map<String, Integer> NINJA_STYLES = new HashMap<>();
    private static final int FULL_BRIGHT = 0xF000F0;

    static {
        for (int i = 0; i < STYLES.length; i++) {
            SHARINGAN[i] = RenderType.entityCutoutNoCull(Noirm.id("textures/entity/sharingan_eyes_" + STYLES[i] + ".png"));
            BYAKUGAN[i] = RenderType.eyes(Noirm.id("textures/entity/byakugan_eyes_" + STYLES[i] + ".png"));
        }
    }

    public SharinganEyesLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || (GoreClient.severed(entity.getId()) & 1) != 0) { // invisible, or the head is gone
            return;
        }
        int stage = entity instanceof SparringNinja ninja ? ninja.activeSharinganStage() : ClientPowers.eyeStage(entity.getId());
        boolean byakugan = entity instanceof SparringNinja ninja ? ninja.byakuganActive() : ClientPowers.byakugan(entity.getId());
        if (stage <= 0 && !byakugan) {
            return;
        }
        int mangekyo = entity instanceof SparringNinja ninja ? ninja.mangekyoLook() : ClientPowers.mangekyoLook(entity.getId());
        int style = eyeStyle(entity);
        poseStack.pushPose();
        if (stage > 0 && mangekyo != 0) {
            drawMangekyo(poseStack, buffers, com.noirm.dojutsu.Mangekyo.lookPattern(mangekyo), com.noirm.dojutsu.Mangekyo.lookDonor(mangekyo),
                    ageInTicks, IRIS[style]);
        } else {
            RenderType type = stage > 0 ? SHARINGAN[style] : BYAKUGAN[style];
            getParentModel().head.render(poseStack, buffers.getBuffer(type), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    /** The eye style this entity is drawn with (0 = round, also for account skins). */
    static int eyeStyle(LivingEntity entity) {
        if (entity instanceof SparringNinja ninja) {
            String key = ninja.outfit();
            if (key.isEmpty()) {
                return 0;
            }
            Integer cached = NINJA_STYLES.get(key);
            if (cached != null) {
                return cached;
            }
            int style;
            try {
                style = index(NinjaLook.decode(key).get("eyes"));
            } catch (RuntimeException e) {
                style = 0;
            }
            if (NINJA_STYLES.size() > 512) {
                NINJA_STYLES.clear();
            }
            NINJA_STYLES.put(key, style);
            return style;
        }
        if (entity instanceof Player player) {
            PlayerLook look = PlayerSkins.lookFor(player);
            return look == null ? 0 : index(look.id(PlayerLook.Slot.EYES));
        }
        return 0;
    }

    private static int index(String id) {
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i].equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private static final RenderType PATTERNS = RenderType.entityCutoutNoCull(MangekyoEffects.PATTERNS);
    /** Points around each Mangekyō eye's circle. */
    private static final int SEGMENTS = 20;

    /**
     * The Mangekyō: each eye is a small disc on the front of the head showing the pattern (the
     * Eternal one draws the donor's pattern over it, turned, so the two merge). The disc fills that
     * eye style's iris, cut flat where the lids cross it; the pattern turns slowly inside the cut
     * disc, so the lids stay put.
     */
    private void drawMangekyo(PoseStack poseStack, MultiBufferSource buffers, int pattern, int donor, float ageInTicks, float[] iris) {
        getParentModel().head.translateAndRotate(poseStack);
        VertexConsumer consumer = buffers.getBuffer(PATTERNS);
        float spin = ageInTicks * 1.5f;
        for (int side = -1; side <= 1; side += 2) {
            disc(poseStack, consumer, side * iris[0], iris, -4.02f, pattern, spin);
            if (donor >= 0) {
                disc(poseStack, consumer, side * iris[0], iris, -4.03f, donor, 45f - spin);
            }
        }
    }

    private static void disc(PoseStack poseStack, VertexConsumer consumer, float x, float[] iris, float z, int pattern, float degrees) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float cy = iris[1];
        float r = iris[2];
        int cells = com.noirm.dojutsu.Mangekyo.PATTERNS;
        float u0 = Math.floorMod(pattern, cells) / (float) cells;
        float cw = 1f / cells;
        double turn = Math.toRadians(degrees);
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        float[] px = new float[SEGMENTS + 1];
        float[] py = new float[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i * 2 * Math.PI / SEGMENTS;
            px[i] = (float) Math.cos(t) * r;
            // cut flat at the lids: a point above the upper lid (or below the lower) slides onto it
            py[i] = Math.max(iris[3] - cy, Math.min(iris[4] - cy, (float) Math.sin(t) * r));
        }
        for (int i = 0; i < SEGMENTS; i++) {
            // a triangle, sent as a quad with a repeated corner: the centre and two neighbouring points
            vertex(consumer, pose, normal, x, cy, z, 0f, 0f, r, cos, sin, u0, cw);
            vertex(consumer, pose, normal, x, cy, z, px[i], py[i], r, cos, sin, u0, cw);
            vertex(consumer, pose, normal, x, cy, z, px[i + 1], py[i + 1], r, cos, sin, u0, cw);
            vertex(consumer, pose, normal, x, cy, z, px[i + 1], py[i + 1], r, cos, sin, u0, cw);
        }
    }

    /** One corner at (dx, dy) from the eye's centre; the pattern is turned by (cos, sin) in the texture. */
    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float cx, float cy, float z,
                               float dx, float dy, float r, float cos, float sin, float u0, float cw) {
        float tx = (dx * cos + dy * sin) / (2 * r);
        float ty = (-dx * sin + dy * cos) / (2 * r);
        consumer.vertex(pose, (cx + dx) / 16f, (cy + dy) / 16f, z / 16f).color(255, 255, 255, 255)
                .uv(u0 + (0.5f + tx) * cw, 0.5f + ty).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT).normal(normal, 0f, 0f, -1f).endVertex();
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<?, ?> renderer = event.getSkin(skin);
                if (renderer instanceof PlayerRenderer playerRenderer) {
                    playerRenderer.addLayer(new SharinganEyesLayer<>(playerRenderer));
                }
            }
        }
    }
}

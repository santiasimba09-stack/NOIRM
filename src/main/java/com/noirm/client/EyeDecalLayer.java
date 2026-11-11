package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.creator.PlayerLook;
import com.noirm.entity.NinjaLook;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.VillageMerchant;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * High-detail eyes (docs/ART_STYLE.md, "Hair, eyes and bodies rework"): each eye drawn once more as a small
 * quad just in front of the face, from an atlas at 16 pixels per model pixel
 * ({@code textures/entity/eyes/<shape>.png}, by {@code tools/skingen/gen_eye_decals.py}), on exactly the lids
 * the skin has. Three layers in one buffer (so they stack in order): the white, the iris (tinted by the eye
 * colour; the Sharingan's tomoe or the Byakugan full-bright instead while active), then the heavy lash line,
 * crease and highlight a hair in front, so a Mangekyō disc ({@link SharinganEyesLayer}) sits between them.
 */
public class EyeDecalLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final String[] SHAPES = {"round", "sharp", "wide", "narrow", "pale"};
    private static final RenderType[] TYPES = new RenderType[SHAPES.length];
    /** The cell box, in head-front texture pixels relative to the texture-left eye (gen_eye_decals.py). */
    private static final float X0 = 0f;
    private static final float X1 = 4f;
    private static final float Y0 = 4.5f - 1.65f;
    private static final float Y1 = 4.5f + 0.85f;
    private static final float CELL_U = 64f / 512f;
    private static final float CELL_V = 40f / 64f;
    private static final int BASE = 0;
    private static final int IRIS = 1;
    private static final int TOP = 2;
    private static final int SHARINGAN = 3;   // + tomoe - 1
    private static final int BYAKUGAN = 6;
    /** Depths in model pixels in front of the face (the Mangekyō disc is drawn at 4.02-4.03). */
    private static final float Z_BACK = -4.01f;
    private static final float Z_TOP = -4.045f;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Map<String, int[]> NPC = new HashMap<>();
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NRM = new Vector3f();

    static {
        for (int i = 0; i < SHAPES.length; i++) {
            TYPES[i] = RenderType.entityCutoutNoCull(Noirm.id("textures/entity/eyes/" + SHAPES[i] + ".png"));
        }
    }

    public EyeDecalLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || (GoreClient.severed(entity.getId()) & 1) != 0) {
            return;
        }
        int[] spec = spec(entity); // {shape, eye colour}
        if (spec == null) {
            return;
        }
        ModelPart head = getParentModel().head;
        if (!head.visible) {
            return;
        }
        int stage;
        boolean byakugan;
        int mangekyo;
        if (entity instanceof SparringNinja ninja) {
            stage = ninja.activeSharinganStage();
            byakugan = ninja.byakuganActive();
            mangekyo = ninja.mangekyoLook();
        } else {
            stage = ClientPowers.eyeStage(entity.getId());
            byakugan = ClientPowers.byakugan(entity.getId());
            mangekyo = ClientPowers.mangekyoLook(entity.getId());
        }
        poseStack.pushPose();
        head.translateAndRotate(poseStack);
        VertexConsumer consumer = buffers.getBuffer(TYPES[spec[0]]);
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        int iris = SkinComposer.tint(spec[1], 186);
        float ir = ((iris >> 16) & 0xFF) / 255f;
        float ig = ((iris >> 8) & 0xFF) / 255f;
        float ib = (iris & 0xFF) / 255f;
        for (int side = 0; side < 2; side++) {
            eye(consumer, pose, normal, side, BASE, Z_BACK, 1f, 1f, 1f, light, overlay);
            if (stage > 0 && mangekyo != 0) {
                // the Mangekyō disc is drawn by SharinganEyesLayer, between these layers
            } else if (stage > 0) {
                eye(consumer, pose, normal, side, SHARINGAN + Math.min(3, stage) - 1, Z_BACK, 1f, 1f, 1f, FULL_BRIGHT, overlay);
            } else if (byakugan) {
                eye(consumer, pose, normal, side, BYAKUGAN, Z_BACK, 1f, 1f, 1f, FULL_BRIGHT, overlay);
            } else {
                eye(consumer, pose, normal, side, IRIS, Z_BACK, ir, ig, ib, light, overlay);
            }
        }
        for (int side = 0; side < 2; side++) {
            eye(consumer, pose, normal, side, TOP, Z_TOP, 1f, 1f, 1f, light, overlay);
        }
        poseStack.popPose();
    }

    /** One eye's quad for one atlas cell. side 0: the texture-left eye (model x < 0); 1: its mirror. */
    private static void eye(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, int side, int cell, float z,
                            float r, float g, float b, int light, int overlay) {
        float u0 = cell * CELL_U;
        float u1 = u0 + CELL_U;
        float xa = side == 0 ? X0 - 4f : 4f - X0;   // the outer edge of the cell
        float xb = side == 0 ? X1 - 4f : 4f - X1;   // the inner edge (towards the nose)
        float y0 = Y0 - 8f;
        float y1 = Y1 - 8f;
        normal.transform(0f, 0f, -1f, NRM);
        vertex(consumer, pose, xa, y0, z, u0, 0f, r, g, b, light, overlay);
        vertex(consumer, pose, xb, y0, z, u1, 0f, r, g, b, light, overlay);
        vertex(consumer, pose, xb, y1, z, u1, CELL_V, r, g, b, light, overlay);
        vertex(consumer, pose, xa, y1, z, u0, CELL_V, r, g, b, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float u, float v,
                               float r, float g, float b, int light, int overlay) {
        pose.transformPosition(x / 16f, y / 16f, z / 16f, POS);
        consumer.vertex(POS.x(), POS.y(), POS.z(), r, g, b, 1f, u, v, overlay, light, NRM.x(), NRM.y(), NRM.z());
    }

    /** {eye shape index, eye colour} for this entity, or null if it has no look of ours (account skins). */
    static int[] spec(LivingEntity entity) {
        if (entity instanceof Player player) {
            PlayerLook look = PlayerSkins.lookFor(player);
            return look == null ? null : new int[]{shape(look.id(PlayerLook.Slot.EYES)), look.color(PlayerLook.Tone.EYE)};
        }
        String key;
        if (entity instanceof SparringNinja ninja) {
            key = ninja.outfit();
        } else if (entity instanceof VillageMerchant merchant) {
            key = VillageMerchantRenderer.lookKey(merchant);
        } else {
            return null;
        }
        if (key.isEmpty()) {
            return null;
        }
        int[] cached = NPC.get(key);
        if (cached == null) {
            try {
                NinjaLook.Features f = NinjaLook.decode(key);
                cached = new int[]{shape(f.get("eyes")), f.color("eye_color")};
            } catch (RuntimeException e) {
                cached = new int[]{0, 0x3A2A22};
            }
            if (NPC.size() > 512) {
                NPC.clear();
            }
            NPC.put(key, cached);
        }
        return cached;
    }

    private static int shape(String id) {
        for (int i = 0; i < SHAPES.length; i++) {
            if (SHAPES[i].equals(id)) {
                return i;
            }
        }
        return 0;
    }
}

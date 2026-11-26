package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.gore.BloodSplats;
import com.noirm.gore.GoreSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the blood on the ground ({@link BloodSplats#CLIENT}) as flat decals hugging the block tops:
 * every stain is one turned quad from one texture ({@code blood_decals.png}, six splats and two
 * pools), so they all go in a single batch. Fresh blood is deep red and dries darker; stains fade
 * at the end of their life, and nothing beyond {@link #DISTANCE} blocks is drawn. Only at the
 * {@code FULL} gore level. Allocation-free: vertices are transformed into reused vectors.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class GoreDecals {
    public static final double DISTANCE = 32;
    private static final RenderType TYPE = RenderType.entityTranslucent(Noirm.id("textures/entity/gore/blood_decals.png"));
    /** Texture cells: 4 across, 2 down. */
    private static final float CELL_U = 0.25f, CELL_V = 0.5f;
    private static final float[] CORNER_X = {-1f, -1f, 1f, 1f};
    private static final float[] CORNER_Z = {-1f, 1f, 1f, -1f};

    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();

    private GoreDecals() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || !GoreSettings.full()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BloodSplats splats = BloodSplats.CLIENT;
        if (level == null || splats.world() != level) {
            return;
        }
        long now = level.getGameTime();
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        double cx = eye.x, cy = eye.y, cz = eye.z;
        PoseStack poseStack = event.getPoseStack();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();
        NORMAL.set(0f, 1f, 0f);
        normalMat.transform(NORMAL);
        float nx = NORMAL.x, ny = NORMAL.y, nz = NORMAL.z;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = null;
        double max = DISTANCE * DISTANCE;
        for (int i = 0; i < splats.capacity(); i++) {
            float alpha = splats.alpha(i, now, partial);
            if (alpha <= 0f) {
                continue;
            }
            double dx = splats.x(i) - cx, dy = splats.y(i) - cy, dz = splats.z(i) - cz;
            if (dx * dx + dy * dy + dz * dz > max) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(TYPE);
            }
            float dry = splats.dryness(i, now);
            float r = 1f + (GoreClient.DRY_R - 1f) * dry, g = 1f + (GoreClient.DRY_G - 1f) * dry, b = 1f + (GoreClient.DRY_B - 1f) * dry;
            float a = alpha * (0.95f - 0.1f * dry);
            LIGHT_POS.set(Mth.floor(splats.x(i)), Mth.floor(splats.y(i)), Mth.floor(splats.z(i)));
            int light = LevelRenderer.getLightColor(level, LIGHT_POS);
            float size = splats.radius(i, now, partial);
            float cos = splats.cos(i) * size, sin = splats.sin(i) * size;
            int variant = splats.variant(i);
            float u0 = (variant & 3) * CELL_U, v0 = (variant >> 2) * CELL_V;
            for (int k = 0; k < 4; k++) {
                float lx = CORNER_X[k], lz = CORNER_Z[k];
                float wx = (float) dx + lx * cos - lz * sin;
                float wz = (float) dz + lx * sin + lz * cos;
                POS.set(wx, (float) dy, wz);
                pose.transformPosition(POS);
                float u = u0 + (lx + 1f) * 0.5f * CELL_U, v = v0 + (lz + 1f) * 0.5f * CELL_V;
                consumer.vertex(POS.x, POS.y, POS.z, r, g, b, a, u, v, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
            }
        }
        if (consumer != null) {
            buffers.endBatch(TYPE);
        }
    }
}

package com.noirm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.client.anim.HumanoidAnimator;
import com.noirm.client.anim.WeaponStances;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Weapon trails: while a move plays with a blade or staff in hand, the edge leaves a fading
 * ribbon through the air (as Epic Fight's swings do), so a cut reads as an arc instead of a
 * blink. The layer samples the blade's base and tip each frame in world space; the ribbon is
 * drawn after particles.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class WeaponTrails {
    /** How long a sample lives (ticks). */
    static final float LIFE = 4.5f;
    /** Only while a move plays at least this strongly. */
    static final float MIN_ACTION = 0.35f;

    private record Sample(float time, Vec3 base, Vec3 tip) {}

    private static final Map<LivingEntity, Deque<Sample>> TRAILS = new WeakHashMap<>();

    private WeaponTrails() {}

    /** Blade length (blocks) from the hand to the tip, by weapon kind; 0 for no trail. */
    private static float length(String kind) {
        return switch (kind == null ? "" : kind) {
            case "sword" -> 1.05f;
            case "kunai" -> 0.5f;
            case "staff" -> 1.5f;
            case "tanto" -> 0.6f;
            case "katana" -> 1.15f;
            case "greatsword" -> 1.25f; // the nodachi's held model: 1.55 long, held 22% up
            case "tonfa" -> 0.45f;
            case "spear" -> 1.45f; // the yari's held model: 2.2 long, held 36% up
            case "claws" -> 0.5f;
            case "fan" -> 0.55f;
            default -> 0f;
        };
    }

    /** A render layer that samples the trail (added to the player and ninja renderers). */
    public static final class Layer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        public Layer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float len = length(WeaponStances.kind(entity.getMainHandItem()));
            if (len <= 0f || HumanoidAnimator.actionWeight(entity) < MIN_ACTION || entity.isInvisible()) {
                return;
            }
            poseStack.pushPose();
            getParentModel().translateToHand(HumanoidArm.RIGHT, poseStack);
            Matrix4f m = poseStack.last().pose();
            Vec3 base = toWorld(m, -1f / 16f, 10f / 16f, -0.2f);
            Vec3 tip = toWorld(m, -1f / 16f, 10f / 16f, -0.2f - len);
            poseStack.popPose();
            if (base == null) {
                return;
            }
            Deque<Sample> trail = TRAILS.computeIfAbsent(entity, e -> new ArrayDeque<>());
            Sample last = trail.peekLast();
            if (last == null || last.time != ageInTicks) {
                trail.addLast(new Sample(ageInTicks, base, tip));
            }
        }
    }

    private static Vec3 toWorld(Matrix4f pose, float x, float y, float z) {
        Vector4f view = pose.transform(new Vector4f(x, y, z, 1f));
        Vector3f world = RenderSystem.getInverseViewRotationMatrix().transform(new Vector3f(view.x, view.y, view.z));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return new Vec3(world.x + cam.x, world.y + cam.y, world.z + cam.z);
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRAILS.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        for (Iterator<Map.Entry<LivingEntity, Deque<Sample>>> it = TRAILS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Deque<Sample>> entry = it.next();
            Deque<Sample> trail = entry.getValue();
            float now = entry.getKey().tickCount + event.getPartialTick();
            while (!trail.isEmpty() && (now - trail.peekFirst().time > LIFE || trail.peekFirst().time > now + 1f)) {
                trail.removeFirst();
            }
            if (trail.isEmpty()) {
                it.remove();
                continue;
            }
            Sample prev = null;
            for (Sample s : trail) {
                if (prev != null) {
                    float a0 = alpha(now - prev.time), a1 = alpha(now - s.time);
                    quad(out, m, cam, prev, s, a0, a1);
                }
                prev = s;
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static float alpha(float age) {
        return Math.max(0f, 1f - age / LIFE) * 0.55f;
    }

    /** One band of the ribbon, both faces (the lightning render type doesn't cull, but be safe). */
    private static void quad(VertexConsumer out, Matrix4f m, Vec3 cam, Sample a, Sample b, float a0, float a1) {
        vertex(out, m, cam, a.base, a0 * 0.2f);
        vertex(out, m, cam, a.tip, a0);
        vertex(out, m, cam, b.tip, a1);
        vertex(out, m, cam, b.base, a1 * 0.2f);
        vertex(out, m, cam, b.base, a1 * 0.2f);
        vertex(out, m, cam, b.tip, a1);
        vertex(out, m, cam, a.tip, a0);
        vertex(out, m, cam, a.base, a0 * 0.2f);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Vec3 cam, Vec3 p, float alpha) {
        out.vertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(0.85f, 0.92f, 1f, alpha).endVertex();
    }
}

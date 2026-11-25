package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.gear.ClothSim;
import com.noirm.gear.Drapes;
import com.noirm.gear.Drapes.Kind;
import com.noirm.gear.Drapes.Part;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the 3D cloth and props of worn gear ({@link Drapes}): capes that hang from the shoulders
 * and flap with the {@link ClothSim}, robe skirts that follow the legs, high collars, hoods, wide
 * sleeves, shoulder plates, a straw hat with strips and things carried on the back.
 *
 * <p>Cloth is a two-sided sheet: the outside on the left half of its texture, the lining on the
 * right half. It is built in model pixels on the jointed body ({@link BendyModel}: the waist bend,
 * the legs and arms) and kept off the body: a cape never sinks into the back, the legs or the arms
 * swinging behind; a skirt always wraps the leg that steps forward and the one left behind.
 */
public class DrapeLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Cape: columns across, and how far it sits behind the back (armour grows the body by 1 pixel). */
    private static final int COLS = 6;
    private static final float BACK = 3.2f;
    /** Skirts: points around the ring and rows down. */
    private static final int RING = 20, SKIRT_ROWS = 4;
    /** How far the lining sits inside the outside (pixels), so both faces draw. */
    private static final float SKIN = 0.06f;

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();
    private static int frames;

    private static final class State {
        final ClothSim sim = new ClothSim();
        int tick = Integer.MIN_VALUE;
        long seen;
    }

    // scratch (render thread only)
    private final float[][] grid = new float[ClothSim.ROWS + 2][(COLS + 1) * 3];
    private final float[][] ring = new float[SKIRT_ROWS + 1][(RING + 1) * 3];
    private final Vector3f n = new Vector3f();
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();

    private static final Map<String, ModelPart> PROPS = new java.util.HashMap<>();

    public DrapeLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        boolean any = false;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && (!Drapes.of(stack.getItem()).isEmpty() || (slot == EquipmentSlot.CHEST && !Drapes.sewnCape(stack).isEmpty()))) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        ClothSim sim = sim(entity);
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        int cut = GoreClient.severed(entity.getId());
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            draw(Drapes.of(stack.getItem()), stack, poseStack, buffers, light, overlay, sim, partialTick, cut, false);
            if (slot == EquipmentSlot.CHEST) {
                ItemStack cape = Drapes.sewnCape(stack);
                if (!cape.isEmpty()) {
                    draw(Drapes.of(cape.getItem()), cape, poseStack, buffers, light, overlay, sim, partialTick, cut, true);
                }
            }
        }
    }

    private void draw(List<Part> parts, ItemStack stack, PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClothSim sim,
                      float partial, int cut, boolean capesOnly) {
        for (Part part : parts) {
            if (capesOnly && part.kind() != Kind.CAPE) {
                continue;
            }
            float r = 1f, g = 1f, bl = 1f;
            if (part.tinted() && stack.getItem() instanceof DyeableLeatherItem dye) {
                int c = dye.getColor(stack);
                r = (c >> 16 & 255) / 255f;
                g = (c >> 8 & 255) / 255f;
                bl = (c & 255) / 255f;
            }
            ps.pushPose();
            switch (part.kind()) {
                case CAPE -> cape(part, ps, buffers, light, overlay, sim, partial, r, g, bl);
                case SKIRT -> skirt(part, ps, buffers, light, overlay, sim, partial, r, g, bl, cut);
                case COLLAR -> { if ((cut & 1) == 0) collar(part, ps, buffers, light, overlay, r, g, bl); }
                case HOOD -> { if ((cut & 1) == 0) hood(part, ps, buffers, light, overlay, r, g, bl); }
                case SLEEVES -> sleeves(part, ps, buffers, light, overlay, sim, partial, r, g, bl, cut);
                case SHOULDERS -> shoulders(part, ps, buffers, light, overlay, r, g, bl, cut);
                case HAT -> { if ((cut & 1) == 0) hat(part, ps, buffers, light, overlay, sim, partial, r, g, bl); }
                case PROP -> prop(part, ps, buffers, light, overlay, r, g, bl);
            }
            ps.popPose();
        }
    }

    // ------------------------------------------------------------ the simulation

    private static ClothSim sim(LivingEntity entity) {
        State state = STATES.get(entity.getId());
        if (state == null) {
            state = new State();
            STATES.put(entity.getId(), state);
        }
        long now = entity.level().getGameTime();
        state.seen = now;
        if (state.tick != entity.tickCount) {
            int steps = state.tick == Integer.MIN_VALUE ? 1 : Mth.clamp(entity.tickCount - state.tick, 1, 3);
            state.tick = entity.tickCount;
            double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
            float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
            float forward = (float) (-dx * Mth.sin(yaw) + dz * Mth.cos(yaw));
            float side = (float) (dx * Mth.cos(yaw) + dz * Mth.sin(yaw));
            float turn = Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO);
            for (int i = 0; i < steps; i++) {
                state.sim.step(forward, (float) dy, side, turn, 0.2f);
            }
        }
        if (++frames > 2000) { // forget wearers not drawn for a while
            frames = 0;
            STATES.int2ObjectEntrySet().removeIf(e -> now - e.getValue().seen > 200 || now < e.getValue().seen);
        }
        return state.sim;
    }

    // ------------------------------------------------------------ capes

    private void cape(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClothSim sim, float partial,
                      float r, float g, float bl) {
        M model = getParentModel();
        model.body.translateAndRotate(ps);
        float waist = model instanceof BendyModel<?> bendy ? bendy.upperTorso.xRot : 0f;
        float bodyPitch = model.body.xRot;
        float legBack = Math.max(0f, Math.max(model.rightLeg.xRot, model.leftLeg.xRot) - bodyPitch);
        float rightArmBack = Math.max(0f, model.rightArm.xRot - bodyPitch);
        float leftArmBack = Math.max(0f, model.leftArm.xRot - bodyPitch);
        float half = part.width() / 2f;
        float len = part.length();
        float seg = len / ClothSim.ROWS;
        float stiffScale = 1f - 0.4f * part.stiff();
        float roll = sim.roll(partial) * stiffScale;
        float flutter = sim.flutter(partial) * (1f - part.stiff());
        float time = sim.time(partial);
        float cw = Mth.cos(waist), sw = Mth.sin(waist);
        for (int c = 0; c <= COLS; c++) {
            float xn = c / (float) COLS * 2f - 1f;
            // row 0: over the top of the shoulders (the cape bends there)
            float yokeY = -7.15f, yokeZ = 0.9f - 1.4f * xn * xn;
            put(grid[0], c, xn * half * 0.8f, 6f + yokeY * cw - yokeZ * sw, yokeY * sw + yokeZ * cw);
            // row 1: the top of the back, where the cape hangs from
            float topY = -6.6f, topZ = BACK - part.wrap() * xn * xn;
            float x = xn * half;
            float y = 6f + topY * cw - topZ * sw;
            float z = topY * sw + topZ * cw;
            put(grid[1], c, x, y, z);
            float depth = 0f;
            for (int row = 0; row < ClothSim.ROWS; row++) {
                float t = (row + 1f) / ClothSim.ROWS;
                float angle = sim.pitch(row, partial) * stiffScale - bodyPitch;
                y += seg * Mth.cos(angle);
                z += seg * Mth.sin(angle);
                depth += seg;
                float px = xn * half * (1f + part.flare() * t) + roll * depth * 0.6f;
                // the sides curl forward less and less towards the hem; ripples run down the cloth
                float pz = z + part.wrap() * xn * xn * (1f - (1f - t) * (1f - t))
                        + Mth.sin(time * 0.55f + xn * 2.2f + t * 4f) * (0.12f + 0.55f * flutter) * t;
                pz = Math.max(pz, clearBehind(px, y, legBack, rightArmBack, leftArmBack));
                put(grid[row + 2], c, px, y, pz);
            }
        }
        RenderType type = RenderType.entityCutoutNoCull(part.texture());
        VertexConsumer vc = buffers.getBuffer(type);
        int rows = ClothSim.ROWS + 2;
        for (int row = 0; row < rows - 1; row++) {
            float v0 = row == 0 ? 0f : 0.05f + 0.95f * (row - 1) / ClothSim.ROWS;
            float v1 = 0.05f + 0.95f * row / ClothSim.ROWS;
            for (int c = 0; c < COLS; c++) {
                float u0 = (1f - c / (float) COLS) * 0.5f, u1 = (1f - (c + 1) / (float) COLS) * 0.5f;
                cell(ps, vc, grid[row], grid[row + 1], c, false, u0, u1, 1f - u0, 1f - u1, v0, v1, light, overlay, r, g, bl);
            }
        }
    }

    /** The least z a cape point may have at (x, y) in body space: behind the back, the legs and arms swinging back. */
    private static float clearBehind(float x, float y, float legBack, float rightArmBack, float leftArmBack) {
        float min = BACK - 0.2f;
        if (y > 12f) {
            min = Math.max(min, BACK + (y - 12f) * Mth.sin(Math.min(1.4f, legBack)) + 0.2f);
        }
        float armBack = x < 0 ? rightArmBack : leftArmBack;
        if (Math.abs(x) > 3.4f && armBack > 0f && y > 0f) {
            min = Math.max(min, BACK + (y - 2f) * Mth.sin(Math.min(1.4f, armBack)) + 0.3f);
        }
        return min;
    }

    private static void put(float[] row, int c, float x, float y, float z) {
        row[c * 3] = x;
        row[c * 3 + 1] = y;
        row[c * 3 + 2] = z;
    }

    /**
     * One cell of a two-sided sheet between two rows of points (columns c and c+1): the outside
     * at u0..u1, the lining at lu0..lu1 (facing the other way). {@code flip}: the points go round
     * the other way, so the outside's normal is flipped to point out.
     */
    private void cell(PoseStack ps, VertexConsumer vc, float[] top, float[] bottom, int c, boolean flip, float u0, float u1,
                      float lu0, float lu1, float v0, float v1, int light, int overlay, float r, float g, float bl) {
        int i = (flip ? c + 1 : c) * 3, j = (flip ? c : c + 1) * 3;
        if (flip) {
            float t = u0;
            u0 = u1;
            u1 = t;
            t = lu0;
            lu0 = lu1;
            lu1 = t;
        }
        a.set(top[j] - top[i], top[j + 1] - top[i + 1], top[j + 2] - top[i + 2]);
        b.set(bottom[i] - top[i], bottom[i + 1] - top[i + 1], bottom[i + 2] - top[i + 2]);
        a.cross(b, n);
        if (n.lengthSquared() < 1e-8f) {
            a.set(bottom[j] - bottom[i], bottom[j + 1] - bottom[i + 1], bottom[j + 2] - bottom[i + 2]);
            a.cross(b, n);
            if (n.lengthSquared() < 1e-8f) {
                n.set(0, 0, 1);
            }
        }
        n.normalize();
        float ox = n.x * SKIN, oy = n.y * SKIN, oz = n.z * SKIN;
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        vertex(vc, pose, normal, top[i] + ox, top[i + 1] + oy, top[i + 2] + oz, u0, v0, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[i] + ox, bottom[i + 1] + oy, bottom[i + 2] + oz, u0, v1, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[j] + ox, bottom[j + 1] + oy, bottom[j + 2] + oz, u1, v1, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, top[j] + ox, top[j + 1] + oy, top[j + 2] + oz, u1, v0, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, top[j] - ox, top[j + 1] - oy, top[j + 2] - oz, lu1, v0, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[j] - ox, bottom[j + 1] - oy, bottom[j + 2] - oz, lu1, v1, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[i] - ox, bottom[i + 1] - oy, bottom[i + 2] - oz, lu0, v1, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, top[i] - ox, top[i + 1] - oy, top[i + 2] - oz, lu0, v0, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light, int overlay, float r, float g, float b) {
        vc.vertex(pose, x / 16f, y / 16f, z / 16f).color(r, g, b, 1f).uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(normal, nx, ny, nz).endVertex();
    }

    // ------------------------------------------------------------ skirts, coat tails, thigh plates

    private void skirt(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClothSim sim, float partial,
                       float r, float g, float bl, int cut) {
        M model = getParentModel();
        model.body.translateAndRotate(ps);
        float bodyPitch = model.body.xRot;
        float right = (cut & 8) != 0 ? 0f : model.rightLeg.xRot - bodyPitch;
        float left = (cut & 16) != 0 ? 0f : model.leftLeg.xRot - bodyPitch;
        float lift = sim.lift(partial) * (1f - 0.6f * part.stiff());
        float flutter = sim.flutter(partial) * (1f - part.stiff());
        float time = sim.time(partial);
        float top = 6f + part.top();
        float hx = 5.0f, hz = 3.0f;
        for (int k = 0; k <= RING; k++) {
            float phi = k / (float) RING * Mth.TWO_PI;
            float cx = Mth.cos(phi), cz = Mth.sin(phi);
            float x0 = hx * Math.signum(cx) * (float) Math.pow(Math.abs(cx), 0.45);
            float z0 = hz * Math.signum(cz) * (float) Math.pow(Math.abs(cz), 0.45);
            // which leg moves this side of the skirt (the right leg is at -x)
            float wRight = 1f / (1f + (float) Math.exp(x0 * 1.3f));
            for (int row = 0; row <= SKIRT_ROWS; row++) {
                float t = row / (float) SKIRT_ROWS;
                float y = top + part.length() * t;
                float spread = 1f + part.flare() * t;
                float x = x0 * spread;
                float z = z0 * spread;
                float d = Math.max(0f, y - 12f);
                float dzR = d * Mth.sin(right), dzL = d * Mth.sin(left);
                z += wRight * dzR + (1f - wRight) * dzL;
                if (z0 < -0.5f) { // the front wraps the leg that steps forward
                    z = Math.min(z, Math.min(-2.6f + dzR, -2.6f + dzL) - 0.5f * (0.4f + t));
                } else if (z0 > 0.5f) { // the back wraps the leg left behind
                    z = Math.max(z, Math.max(2.6f + dzR, 2.6f + dzL) + 0.5f * (0.4f + t));
                }
                z += lift * 0.22f * part.length() * t * t; // running blows the hem back
                x += Mth.sin(time * 0.5f + phi * 3f) * 0.25f * flutter * t;
                put(ring[row], k, x, y, z);
            }
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture()));
        int panels = part.panels();
        for (int k = 0; k < RING; k++) {
            float mid = (k + 0.5f) / RING * Mth.TWO_PI;
            float mx = Mth.cos(mid) * hx, mz = Mth.sin(mid);
            if (part.open() > 0 && mz < -0.3f && Math.abs(mx) < part.open() / 2f) {
                continue; // an open front (coat tails)
            }
            if (panels > 0) {
                float f = ((k + 0.5f) / RING * panels + 0.5f) % 1f;
                if (f > 0.86f) {
                    continue; // the gap between two plates
                }
            }
            float u0 = k / (float) RING * 0.5f, u1 = (k + 1) / (float) RING * 0.5f;
            for (int row = 0; row < SKIRT_ROWS; row++) {
                cell(ps, vc, ring[row], ring[row + 1], k, true, u0, u1, 0.5f + u0, 0.5f + u1, row / (float) SKIRT_ROWS,
                        (row + 1f) / SKIRT_ROWS, light, overlay, r, g, bl);
            }
        }
    }

    /** A ring cell: the same as {@link #sheet} with the normal pointing out of the ring. */
    private void sheetFlipped(PoseStack ps, VertexConsumer vc, float[] top, float[] bottom, int c, float u0, float u1, float v0, float v1,
                              int light, int overlay, float r, float g, float bl) {
        // swap the column order so the cross product points outward for a ring that goes round counter-clockwise
        int i = (c + 1) * 3, j = c * 3;
        a.set(top[j] - top[i], top[j + 1] - top[i + 1], top[j + 2] - top[i + 2]);
        b.set(bottom[i] - top[i], bottom[i + 1] - top[i + 1], bottom[i + 2] - top[i + 2]);
        a.cross(b, n);
        if (n.lengthSquared() < 1e-8f) {
            n.set(0, 0, 1);
        }
        n.normalize();
        float ox = n.x * SKIN, oy = n.y * SKIN, oz = n.z * SKIN;
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        vertex(vc, pose, normal, top[i] + ox, top[i + 1] + oy, top[i + 2] + oz, u1, v0, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[i] + ox, bottom[i + 1] + oy, bottom[i + 2] + oz, u1, v1, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[j] + ox, bottom[j + 1] + oy, bottom[j + 2] + oz, u0, v1, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, top[j] + ox, top[j + 1] + oy, top[j + 2] + oz, u0, v0, n.x, n.y, n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, top[j] - ox, top[j + 1] - oy, top[j + 2] - oz, 0.5f + u0, v0, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[j] - ox, bottom[j + 1] - oy, bottom[j + 2] - oz, 0.5f + u0, v1, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, bottom[i] - ox, bottom[i + 1] - oy, bottom[i + 2] - oz, 0.5f + u1, v1, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
        vertex(vc, pose, normal, top[i] - ox, top[i + 1] - oy, top[i + 2] - oz, 0.5f + u1, v0, -n.x, -n.y, -n.z, light, overlay, r, g, bl);
    }

    // ------------------------------------------------------------ collars

    private void collar(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float r, float g, float bl) {
        getParentModel().head.translateAndRotate(ps);
        float back = part.height();
        float front = part.open() > 0 ? part.open() : back;
        int n = 16;
        float[] low = ring[0], high = ring[1];
        for (int k = 0; k <= n; k++) {
            float phi = k / (float) n * Mth.TWO_PI;
            float cx = Mth.cos(phi), cz = Mth.sin(phi);
            float sx = Math.signum(cx) * (float) Math.pow(Math.abs(cx), 0.35), sz = Math.signum(cz) * (float) Math.pow(Math.abs(cz), 0.35);
            // front (z < 0) at the front height, the back at the back height, blended round the sides
            float h = cz < 0 ? Mth.lerp(-cz, (front + back) / 2f, front) : Mth.lerp(cz, (front + back) / 2f, back);
            put(low, k, sx * 4.7f, 1.2f, sz * 4.7f);
            put(high, k, sx * 5.0f, -h, sz * 5.0f);
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture()));
        for (int k = 0; k < n; k++) {
            float u0 = k / (float) n * 0.5f, u1 = (k + 1) / (float) n * 0.5f;
            cell(ps, vc, high, low, k, true, u0, u1, 0.5f + u0, 0.5f + u1, 0f, 1f, light, overlay, r, g, bl);
        }
    }

    // ------------------------------------------------------------ hoods

    private void hood(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float r, float g, float bl) {
        getParentModel().head.translateAndRotate(ps);
        ModelPart shell = PROPS.computeIfAbsent("hood", k -> hoodShell());
        shell.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture())), light, overlay, r, g, bl, 1f);
    }

    /** The hood: a shell around the head open at the face, a stiff rim, and cloth falling to the shoulders. */
    private static ModelPart hoodShell() {
        Set<Direction> noFace = EnumSet.of(Direction.DOWN, Direction.SOUTH, Direction.EAST, Direction.WEST);
        java.util.List<ModelPart.Cube> cubes = new java.util.ArrayList<>();
        cubes.add(cube(0, 0, -5.2f, -9.6f, -4.9f, 10.4f, 10.4f, 10.4f, noFace));        // the shell
        Set<Direction> all = EnumSet.allOf(Direction.class);
        // (texture layout: the shell at 0,0; the rims at 0,21 and 42,0; the fall at 24,21; the peak at 46,0)
        cubes.add(cube(0, 21, -5.4f, -9.8f, -5.4f, 10.8f, 1.6f, 0.9f, all));            // the rim over the brow
        cubes.add(cube(42, 0, -5.4f, -8.4f, -5.4f, 1.1f, 9.0f, 0.9f, all));             // the rim at the sides
        cubes.add(cube(42, 0, 4.3f, -8.4f, -5.4f, 1.1f, 9.0f, 0.9f, all));
        cubes.add(cube(24, 21, -4.6f, -1.0f, 4.6f, 9.2f, 5.0f, 1.2f, all));             // the fall onto the back
        cubes.add(cube(46, 0, -1.6f, -10.6f, 2.2f, 3.2f, 1.2f, 3.0f, all));             // a soft peak at the back
        return new ModelPart(cubes, Map.of());
    }

    private static ModelPart.Cube cube(int u, int v, float x, float y, float z, float w, float h, float d, Set<Direction> faces) {
        return new ModelPart.Cube(u, v, x, y, z, w, h, d, 0f, 0f, 0f, false, 64f, 32f, faces);
    }

    // ------------------------------------------------------------ wide sleeves

    private final Quaternionf q = new Quaternionf();
    private final Quaternionf q2 = new Quaternionf();
    private final Vector3f down = new Vector3f();

    private void sleeves(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClothSim sim, float partial,
                         float r, float g, float bl, int cut) {
        M model = getParentModel();
        if (!(model instanceof BendyModel<?> bendy)) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture()));
        float lift = sim.lift(partial);
        if ((cut & 2) == 0) {
            sleeve(part, ps, vc, model.rightArm, bendy.rightForearm, light, overlay, lift, r, g, bl);
        }
        if ((cut & 4) == 0) {
            sleeve(part, ps, vc, model.leftArm, bendy.leftForearm, light, overlay, lift, r, g, bl);
        }
    }

    /** A wide sleeve round the forearm whose underside sags towards the ground. */
    private void sleeve(Part part, PoseStack ps, VertexConsumer vc, ModelPart arm, ModelPart forearm, int light, int overlay, float lift,
                        float r, float g, float bl) {
        ps.pushPose();
        arm.translateAndRotate(ps);
        forearm.translateAndRotate(ps);
        // the ground's direction in the forearm's frame
        q.rotationZYX(arm.zRot, arm.yRot, arm.xRot);
        q2.rotationZYX(forearm.zRot, forearm.yRot, forearm.xRot);
        q.mul(q2).conjugate();
        down.set(0f, 1f, lift * 0.3f);
        q.transform(down);
        float along = down.y; // the part of gravity along the forearm
        float px = down.x, pz = down.z; // and across it
        float across = (float) Math.sqrt(px * px + pz * pz);
        if (across > 1e-4f) {
            px /= across;
            pz /= across;
        }
        int loop = 10;
        float[][] rows = {ring[0], ring[1], ring[2]};
        float[] ys = {-0.5f, 3.5f, 7.4f};
        for (int s = 0; s < 3; s++) {
            float rad = 3.15f + s * 0.35f;
            for (int k = 0; k <= loop; k++) {
                float phi = k / (float) loop * Mth.TWO_PI;
                float cx = Mth.cos(phi), cz = Mth.sin(phi);
                float sx = Math.signum(cx) * (float) Math.pow(Math.abs(cx), 0.5) * rad;
                float sz = Math.signum(cz) * (float) Math.pow(Math.abs(cz), 0.5) * rad;
                float face = Math.max(0f, cx * px + cz * pz); // how much this side faces the ground
                float sag = part.length() * across * face * face * (0.35f + 0.65f * s / 2f);
                float drop = part.length() * Math.max(0f, along) * face * 0.4f * s / 2f;
                put(rows[s], k, sx + px * sag, ys[s] + drop, sz + pz * sag);
            }
        }
        for (int s = 0; s < 2; s++) {
            for (int k = 0; k < loop; k++) {
                float u0 = k / (float) loop * 0.5f, u1 = (k + 1) / (float) loop * 0.5f;
                cell(ps, vc, rows[s], rows[s + 1], k, true, u0, u1, 0.5f + u0, 0.5f + u1, s / 2f, (s + 1) / 2f, light, overlay, r, g, bl);
            }
        }
        ps.popPose();
    }

    // ------------------------------------------------------------ shoulder plates

    private void shoulders(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float r, float g, float bl, int cut) {
        M model = getParentModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture()));
        if ((cut & 2) == 0) {
            ps.pushPose();
            model.rightArm.translateAndRotate(ps);
            PROPS.computeIfAbsent("shoulder_r", k -> shoulderPlate(false)).render(ps, vc, light, overlay, r, g, bl, 1f);
            ps.popPose();
        }
        if ((cut & 4) == 0) {
            ps.pushPose();
            model.leftArm.translateAndRotate(ps);
            PROPS.computeIfAbsent("shoulder_l", k -> shoulderPlate(true)).render(ps, vc, light, overlay, r, g, bl, 1f);
            ps.popPose();
        }
    }

    /** A plate hanging off the outside of the upper arm with a cap over the shoulder, tilted out. */
    private static ModelPart shoulderPlate(boolean left) {
        Set<Direction> all = EnumSet.allOf(Direction.class);
        float s = left ? 1f : -1f;
        java.util.List<ModelPart.Cube> cubes = new java.util.ArrayList<>();
        // positions relative to the pivot below (the outer top edge of the arm)
        cubes.add(cube(0, 0, left ? 0f : -1.2f, 0f, -3.4f, 1.2f, 6.0f, 6.8f, all));       // the hanging plate
        cubes.add(cube(0, 14, left ? -4.2f : -0.6f, -1.0f, -3.2f, 4.8f, 1.2f, 6.4f, all)); // the cap over the shoulder
        ModelPart plate = new ModelPart(cubes, Map.of());
        PartPose pose = PartPose.offsetAndRotation(s * 3.2f, -2.6f, 0f, 0f, 0f, s * -0.32f);
        plate.setInitialPose(pose);
        plate.loadPose(pose);
        return new ModelPart(java.util.List.of(), Map.of("plate", plate));
    }

    // ------------------------------------------------------------ the straw hat

    private void hat(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, ClothSim sim, float partial,
                     float r, float g, float bl) {
        getParentModel().head.translateAndRotate(ps);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture()));
        float radius = part.width();
        float brimY = -6.6f, apexY = brimY - part.height();
        int n = 16;
        float[] rim = ring[0], apex = ring[1];
        for (int k = 0; k <= n; k++) {
            float phi = k / (float) n * Mth.TWO_PI;
            put(rim, k, Mth.cos(phi) * radius, brimY, Mth.sin(phi) * radius);
            put(apex, k, 0f, apexY, 0f);
        }
        // the cone: straw on the left half (u round the brim, v from the tip), the same on the underside
        for (int k = 0; k < n; k++) {
            float u0 = k / (float) n * 0.4f, u1 = (k + 1) / (float) n * 0.4f;
            cell(ps, vc, apex, rim, k, true, u0, u1, 0.5f + u0, 0.5f + u1, 0f, 1f, light, overlay, r, g, bl);
        }
        // strips hanging from the brim (not in front of the face); they swing back as you run
        int strips = Math.max(0, part.panels());
        float lift = sim.lift(partial) * 0.7f;
        float time = sim.time(partial);
        float flutter = sim.flutter(partial);
        HumanoidModel<?> model = getParentModel();
        float headPitch = model.head.xRot;
        for (int s = 0; s < strips; s++) {
            float phi = Mth.HALF_PI + (s + 0.5f) / strips * Mth.TWO_PI; // start at the back
            float cx = Mth.cos(phi), cz = Mth.sin(phi);
            if (cz < -0.86f) {
                continue; // leave the face free
            }
            float w = 0.7f;
            float tx = -cz * w, tz = cx * w; // along the brim
            float bx = cx * radius * 0.97f, bz = cz * radius * 0.97f;
            float sway = Mth.sin(time * 0.6f + s * 1.7f) * (0.15f + 0.5f * flutter);
            // down, kept upright against the head's nod, blown back by running
            float dx = cx * 0.12f + sway * -cz * 0.4f;
            float dy = Mth.cos(headPitch);
            float dz = cz * 0.12f + lift + Mth.sin(headPitch) * -1f + sway * cx * 0.4f;
            float len = part.length() * (0.85f + 0.3f * ((s * 37) % 7) / 7f);
            float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx = dx / dl * len;
            dy = dy / dl * len;
            dz = dz / dl * len;
            float[] t0 = grid[0], t1 = grid[1];
            put(t0, 0, bx - tx, brimY + 0.2f, bz - tz);
            put(t0, 1, bx + tx, brimY + 0.2f, bz + tz);
            put(t1, 0, bx - tx + dx, brimY + 0.2f + dy, bz - tz + dz);
            put(t1, 1, bx + tx + dx, brimY + 0.2f + dy, bz + tz + dz);
            cell(ps, vc, t0, t1, 0, false, 0.92f, 1f, 0.92f, 1f, 0f, 1f, light, overlay, r, g, bl);
        }
    }

    // ------------------------------------------------------------ things on the back

    private void prop(Part part, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float r, float g, float bl) {
        M model = getParentModel();
        model.body.translateAndRotate(ps);
        if (model instanceof BendyModel<?> bendy) {
            bendy.upperTorso.translateAndRotate(ps);
        } else {
            ps.translate(0, 6f / 16f, 0);
        }
        ModelPart shape = PROPS.computeIfAbsent("prop_" + part.shape(), k -> propShape(part.shape()));
        shape.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture())), light, overlay, r, g, bl, 1f);
    }

    /** A gourd (two bulbs, slung across the back) or a jar (a squat pot with a lid, at the small of the back). */
    private static ModelPart propShape(String shape) {
        Set<Direction> all = EnumSet.allOf(Direction.class);
        java.util.List<ModelPart.Cube> cubes = new java.util.ArrayList<>();
        PartPose pose;
        if ("jar".equals(shape)) {
            cubes.add(cube(0, 0, -3f, -3f, 0f, 6f, 6.5f, 5f, all));      // the pot
            cubes.add(cube(0, 0, -3.5f, -1.5f, -0.5f, 7f, 3.5f, 6f, all)); // its belly
            cubes.add(cube(0, 16, -2.2f, -4.2f, 0.6f, 4.4f, 1.3f, 3.8f, all)); // the neck
            cubes.add(cube(0, 16, -2.8f, -5.0f, 0.2f, 5.6f, 0.9f, 4.6f, all)); // the lid
            cubes.add(cube(0, 16, -0.7f, -5.8f, 1.8f, 1.4f, 0.9f, 1.4f, all)); // the knob
            pose = PartPose.offsetAndRotation(2.6f, 1.2f, 3.1f, 0.12f, 0f, -0.1f);
        } else {
            cubes.add(cube(0, 0, -4.5f, -1f, 0f, 9f, 8f, 7f, all));         // the big bulb
            cubes.add(cube(0, 0, -3.8f, -0.2f, -0.4f, 7.6f, 6.4f, 7.8f, all));
            cubes.add(cube(0, 16, -2f, -3f, 1.5f, 4f, 2.2f, 4f, all));        // the waist (rope)
            cubes.add(cube(0, 0, -3.2f, -8f, 0.4f, 6.4f, 5.2f, 6.2f, all));   // the small bulb
            cubes.add(cube(0, 16, -1.2f, -9.8f, 2.4f, 2.4f, 2f, 2.4f, all));  // the neck
            cubes.add(cube(0, 16, -1.6f, -10.6f, 2f, 3.2f, 1f, 3.2f, all));   // the stopper
            pose = PartPose.offsetAndRotation(0.5f, -1.5f, 3.0f, 0f, 0f, 0.42f);
        }
        ModelPart inner = new ModelPart(cubes, Map.of());
        inner.setInitialPose(pose);
        inner.loadPose(pose);
        return new ModelPart(java.util.List.of(), Map.of("prop", inner));
    }

    /** Drops every wearer's cloth state (a world change). */
    public static void clear() {
        STATES.clear();
    }
}

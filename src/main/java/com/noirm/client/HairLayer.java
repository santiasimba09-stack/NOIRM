package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.creator.HairStyles;
import com.noirm.creator.HairStyles.Cover;
import com.noirm.creator.HairStyles.Lock;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * NOIRM's 3D hair (docs/ART_STYLE.md, "Hair, eyes and bodies rework"): every lock of the wearer's style
 * ({@link HairStyles}) drawn on the head as a tapered box or a draped ribbon, textured with the strand
 * texture in the hair colour ({@link HairTextures}). Players get theirs from their look, NPC ninjas and
 * villagers from their look key.
 *
 * <p>Cheap by design: each style (with its volume and length) is baked once into a flat vertex array
 * ({@link Mesh}); a frame only transforms those vertices into the buffer, with no allocation. Hanging
 * hair swings a little: each vertex of a swinging lock turns around the lock's pivot by an angle that grows
 * towards the tip (walking, falling, the head turning while the hair stays with the body).
 *
 * <p>Head gear: a forehead band presses the hair in, a hat or helmet leaves only the hanging hair, a hood
 * hides it all ({@link HairStyles#cover}).
 */
public class HairLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** Floats per baked vertex: x y z (blocks), u v, nx ny nz, shade, along (0 root .. 1 tip), swing, pivot xyz, hang. */
    static final int STRIDE = 15;
    /** Light per face of a lock: outer, edge, inner, edge, the blunt tip (the same table as gen_hair.py). */
    private static final float[] FACE_SHADE = {1f, 0.8f, 0.6f, 0.8f, 0.7f};

    /** A style baked into quads (4 vertices each). */
    record Mesh(float[] data, int vertices, boolean anySwing) {}

    private static final Mesh EMPTY = new Mesh(new float[0], 0, false);
    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Mesh> MESHES = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private static final it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<String> NPC_SPECS = new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>();
    private static final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap MERCHANT_SPECS = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    static {
        NPC_SPECS.defaultReturnValue(Long.MIN_VALUE);
        MERCHANT_SPECS.defaultReturnValue(Long.MIN_VALUE);
    }
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NRM = new Vector3f();

    public HairLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || (GoreClient.severed(entity.getId()) & 1) != 0) {
            return;
        }
        long spec = spec(entity);
        if (spec == NONE) {
            return;
        }
        ModelPart head = getParentModel().head;
        if (!head.visible) {
            return;
        }
        Cover cover = HairStyles.cover(entity.getItemBySlot(EquipmentSlot.HEAD));
        if (cover == Cover.ALL) {
            return;
        }
        Mesh mesh = mesh(spec);
        if (mesh == null || mesh.vertices() == 0) {
            return;
        }
        ResourceLocation texture = HairTextures.get((int) (spec & 0xFFFFFF));
        if (texture == null) {
            return;
        }
        // how far the hanging hair swings back (radians, before the per-lock weight)
        float ax = 0f;
        float ay = 0f;
        if (mesh.anySwing()) {
            float walk = Mth.clamp(limbSwingAmount, 0f, 1f);
            float fall = (float) Math.max(0, -entity.getDeltaMovement().y);
            ax = -head.xRot * 0.9f + walk * 0.45f + Mth.sin(limbSwing * 1.3324f) * 0.05f * walk + Math.min(0.6f, fall * 0.9f)
                    + Mth.sin(ageInTicks * 0.05f) * 0.025f;
            ax = Mth.clamp(ax, -0.5f, 1.2f);
            ay = -head.yRot * 0.85f;
        }
        poseStack.pushPose();
        head.translateAndRotate(poseStack);
        if (cover == Cover.SQUASH) {
            // a band around the forehead: the hair pressed in towards the middle of the head
            poseStack.translate(0, -5f / 16f, 0);
            poseStack.scale(0.92f, 0.86f, 0.92f);
            poseStack.translate(0, 5f / 16f, 0);
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        draw(poseStack, consumer, mesh, light, LivingEntityRenderer.getOverlayCoords(entity, 0f), ax, ay, cover == Cover.CROWN);
        poseStack.popPose();
    }

    private static void draw(PoseStack poseStack, VertexConsumer consumer, Mesh mesh, int light, int overlay, float ax, float ay,
                             boolean hangingOnly) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float[] d = mesh.data();
        int quads = mesh.vertices() / 4;
        for (int q = 0; q < quads; q++) {
            int base = q * 4 * STRIDE;
            if (hangingOnly && d[base + 14] == 0f) {
                continue;
            }
            for (int v = 0; v < 4; v++) {
                int i = base + v * STRIDE;
                float x = d[i];
                float y = d[i + 1];
                float z = d[i + 2];
                float k = d[i + 10] * d[i + 9];
                if (k > 0f) {
                    // turn around the pivot: pitch (back/forward), then yaw (keeps hanging hair with the body)
                    float px = d[i + 11];
                    float py = d[i + 12];
                    float pz = d[i + 13];
                    float rx = x - px;
                    float ry = y - py;
                    float rz = z - pz;
                    float a = ax * k;
                    float c = Mth.cos(a);
                    float s = Mth.sin(a);
                    float ny = ry * c - rz * s;
                    float nz = ry * s + rz * c;
                    float b = ay * k;
                    float cb = Mth.cos(b);
                    float sb = Mth.sin(b);
                    float nx = rx * cb + nz * sb;
                    nz = -rx * sb + nz * cb;
                    x = px + nx;
                    y = py + ny;
                    z = pz + nz;
                }
                pose.transformPosition(x, y, z, POS);
                normal.transform(d[i + 5], d[i + 6], d[i + 7], NRM);
                float shade = d[i + 8];
                consumer.vertex(POS.x(), POS.y(), POS.z(), shade, shade, shade, 1f, d[i + 3], d[i + 4], overlay, light,
                        NRM.x(), NRM.y(), NRM.z());
            }
        }
    }

    // ------------------------------------------------------------ who wears what

    /** No hair. Specs are packed in a long so a frame allocates nothing: style index, volume, length, colour. */
    static final long NONE = -1L;
    private static final long UNKNOWN = Long.MIN_VALUE;
    private static final int NONE_STYLE = PlayerLook.Slot.HAIR.indexOf("none");

    static long pack(int style, int volume, int length, int color) {
        return ((long) style << 32) | ((long) (volume & 15) << 28) | ((long) (length & 15) << 24) | (color & 0xFFFFFFL);
    }

    /** The hair this entity is drawn with, or {@link #NONE} (bald, an account skin, no look). */
    static long spec(LivingEntity entity) {
        if (entity instanceof Player player) {
            PlayerLook look = PlayerSkins.lookFor(player);
            if (look == null) {
                return NONE;
            }
            int style = look.get(PlayerLook.Slot.HAIR);
            return style == NONE_STYLE ? NONE : pack(style, look.get(PlayerLook.Slot.HAIR_VOLUME), look.get(PlayerLook.Slot.HAIR_LENGTH),
                    look.color(PlayerLook.Tone.HAIR));
        }
        if (entity instanceof VillageMerchant merchant) {
            long cached = MERCHANT_SPECS.get(entity.getId());
            if (cached == UNKNOWN) {
                cached = decode(VillageMerchantRenderer.lookKey(merchant));
                if (MERCHANT_SPECS.size() > 512) {
                    MERCHANT_SPECS.clear();
                }
                MERCHANT_SPECS.put(entity.getId(), cached);
            }
            return cached;
        }
        if (!(entity instanceof SparringNinja ninja)) {
            return NONE;
        }
        String key = ninja.outfit();
        if (key.isEmpty()) {
            return NONE;
        }
        long cached = NPC_SPECS.getLong(key);
        if (cached == UNKNOWN) {
            cached = decode(key);
            if (NPC_SPECS.size() > 512) {
                NPC_SPECS.clear();
            }
            NPC_SPECS.put(key, cached);
        }
        return cached;
    }

    private static long decode(String key) {
        try {
            NinjaLook.Features f = NinjaLook.decode(key);
            int style = PlayerLook.Slot.HAIR.indexOf(f.get("hair_style"));
            return style < 0 || style == NONE_STYLE || HairStyles.get(f.get("hair_style")) == null ? NONE
                    : pack(style, 1, 0, f.color("hair_color"));
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    // ------------------------------------------------------------ baking

    static Mesh mesh(long spec) {
        int key = (int) ((spec >>> 24) & 0xFFFFF); // style, volume, length
        Mesh mesh = MESHES.get(key);
        if (mesh == null) {
            int styleIndex = (int) (spec >>> 32);
            HairStyles.Style style = HairStyles.get(PlayerLook.Slot.HAIR.option(styleIndex));
            mesh = style == null ? EMPTY : bake(style, PlayerLook.volumeScale((int) ((spec >>> 28) & 15)), (int) ((spec >>> 24) & 15));
            MESHES.put(key, mesh);
        }
        return mesh;
    }

    /** Forget every baked style (resource reload). */
    static void clear() {
        MESHES.clear();
        NPC_SPECS.clear();
        MERCHANT_SPECS.clear();
    }

    /** Bakes a style: every lock into quads, scaled by the volume, plus hair down the back for a chosen length. */
    static Mesh bake(HairStyles.Style style, float volume, int length) {
        List<Lock> locks = new ArrayList<>(style.locks());
        boolean hangs = locks.stream().anyMatch(Lock::hang);
        if (length > 0 && !hangs) {
            locks.addAll(backHair(length));
        }
        int quadCount = 0;
        for (Lock lock : locks) {
            int n = lock.points();
            quadCount += (n - 1) * 4 + (lock.width()[n - 1] > 0.05f && lock.depth()[n - 1] > 0.05f ? 1 : 0);
        }
        float[] data = new float[quadCount * 4 * STRIDE];
        int[] cursor = {0};
        boolean anySwing = false;
        for (Lock lock : locks) {
            bakeLock(lock, volume, data, cursor);
            anySwing |= lock.swing() > 0;
        }
        return new Mesh(data, cursor[0] / STRIDE, anySwing);
    }

    /** Long hair down the back for the "length" choice on styles that have none of their own. */
    private static List<Lock> backHair(int length) {
        float drop = switch (length) {
            case 1 -> 2.5f;
            case 2 -> 6.5f;
            default -> 12f;
        };
        List<Lock> out = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            float side = -1f + 2f * (i + 0.5f) / 5f;
            float x = side * 3.4f;
            int segs = 3 + (int) (drop / 3f);
            float[] pts = new float[(3 + segs) * 3];
            float[] w = new float[3 + segs];
            float[] d = new float[3 + segs];
            float[][] head = {{x * 0.8f, -6.8f, 3.6f}, {x, -3.5f, 4.45f}, {x * 1.05f, -0.3f, 3.3f}};
            for (int p = 0; p < 3; p++) {
                System.arraycopy(head[p], 0, pts, p * 3, 3);
            }
            for (int s = 0; s < segs; s++) {
                int p = 3 + s;
                pts[p * 3] = x * (1.05f + 0.02f * s);
                pts[p * 3 + 1] = -0.3f + drop * (s + 1) / segs;
                pts[p * 3 + 2] = Math.max(2.45f, 3.3f - 0.35f * (s + 1));
            }
            for (int p = 0; p < w.length; p++) {
                float t = p / (float) (w.length - 1);
                w[p] = 3.4f * (1f - 0.45f * t * t);
                d[p] = 0.6f * (1f - 0.5f * t);
            }
            out.add(new Lock(pts, w, d, new float[]{side * 0.3f, -0.1f, 1f}, 0.7f + 0.06f * (i % 3), i % 2, true,
                    0.8f, new float[]{x, -3.5f, 4.45f}, true));
        }
        return out;
    }

    private static void bakeLock(Lock lock, float volume, float[] out, int[] cursor) {
        int n = lock.points();
        float[] p = lock.pts().clone();
        float[] w = lock.width();
        float[] dd = lock.depth();
        // volume: each lock grows from its root
        for (int i = 1; i < n; i++) {
            for (int k = 0; k < 3; k++) {
                p[i * 3 + k] = p[k] + (p[i * 3 + k] - p[k]) * volume;
            }
        }
        float[][] rings = new float[n][12];
        float[] along = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                total += dist(p, i - 1, i);
            }
            along[i] = total;
            int a = Math.max(i - 1, 0);
            int b = Math.min(i + 1, n - 1);
            float tx = p[b * 3] - p[a * 3];
            float ty = p[b * 3 + 1] - p[a * 3 + 1];
            float tz = p[b * 3 + 2] - p[a * 3 + 2];
            float tl = len(tx, ty, tz);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            float[] o = lock.out();
            float dot = o[0] * tx + o[1] * ty + o[2] * tz;
            float ox = o[0] - tx * dot;
            float oy = o[1] - ty * dot;
            float oz = o[2] - tz * dot;
            float ol = len(ox, oy, oz);
            if (ol < 1e-4f) {
                // out is along the lock: any perpendicular will do (t x X)
                ox = 0;
                oy = tz;
                oz = -ty;
                ol = Math.max(1e-4f, len(ox, oy, oz));
            }
            ox /= ol;
            oy /= ol;
            oz /= ol;
            // side = t x o
            float sx = ty * oz - tz * oy;
            float sy = tz * ox - tx * oz;
            float sz = tx * oy - ty * ox;
            float hw = w[i] * volume / 2f;
            float hd = dd[i] * volume / 2f;
            float cx = p[i * 3];
            float cy = p[i * 3 + 1];
            float cz = p[i * 3 + 2];
            float[] r = rings[i];
            corner(r, 0, cx + sx * hw + ox * hd, cy + sy * hw + oy * hd, cz + sz * hw + oz * hd);
            corner(r, 1, cx - sx * hw + ox * hd, cy - sy * hw + oy * hd, cz - sz * hw + oz * hd);
            corner(r, 2, cx - sx * hw - ox * hd, cy - sy * hw - oy * hd, cz - sz * hw - oz * hd);
            corner(r, 3, cx + sx * hw - ox * hd, cy + sy * hw - oy * hd, cz + sz * hw - oz * hd);
        }
        total = Math.max(total, 1e-4f);
        float u0;
        float uw;
        if (lock.sheet()) {
            u0 = (128 + lock.cell() * 64) / 256f;
            uw = 64 / 256f;
        } else {
            u0 = lock.cell() * 32 / 256f;
            uw = 32 / 256f;
        }
        float[] pivot = lock.pivot();
        for (int i = 0; i < n - 1; i++) {
            float v0 = along[i] / total;
            float v1 = along[i + 1] / total;
            for (int f = 0; f < 4; f++) {
                int a = f;
                int b = (f + 1) % 4;
                float[] r0 = rings[i];
                float[] r1 = rings[i + 1];
                quad(out, cursor, lock, pivot, FACE_SHADE[f],
                        r0, a, u0, v0, r0, b, u0 + uw, v0, r1, b, u0 + uw, v1, r1, a, u0, v1);
            }
        }
        if (w[n - 1] > 0.05f && dd[n - 1] > 0.05f) {
            float[] r = rings[n - 1];
            quad(out, cursor, lock, pivot, FACE_SHADE[4], r, 3, u0, 0.97f, r, 2, u0 + uw, 0.97f, r, 1, u0 + uw, 1f, r, 0, u0, 1f);
        }
    }

    private static void quad(float[] out, int[] cursor, Lock lock, float[] pivot, float faceShade,
                             float[] ra, int ia, float ua, float va, float[] rb, int ib, float ub, float vb,
                             float[] rc, int ic, float uc, float vc, float[] rd, int id, float ud, float vd) {
        // the face normal from two edges (a->d) x (a->b), pointing out of the lock
        float e1x = rd[id * 3] - ra[ia * 3];
        float e1y = rd[id * 3 + 1] - ra[ia * 3 + 1];
        float e1z = rd[id * 3 + 2] - ra[ia * 3 + 2];
        float e2x = rb[ib * 3] - ra[ia * 3];
        float e2y = rb[ib * 3 + 1] - ra[ia * 3 + 1];
        float e2z = rb[ib * 3 + 2] - ra[ia * 3 + 2];
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float nl = len(nx, ny, nz);
        if (nl < 1e-6f) {
            nx = 0;
            ny = -1;
            nz = 0;
        } else {
            nx /= nl;
            ny /= nl;
            nz /= nl;
        }
        float shade = Mth.clamp(lock.shade() * faceShade, 0.2f, 1f);
        vertex(out, cursor, ra, ia, ua, va, nx, ny, nz, shade, lock, pivot);
        vertex(out, cursor, rb, ib, ub, vb, nx, ny, nz, shade, lock, pivot);
        vertex(out, cursor, rc, ic, uc, vc, nx, ny, nz, shade, lock, pivot);
        vertex(out, cursor, rd, id, ud, vd, nx, ny, nz, shade, lock, pivot);
    }

    private static void vertex(float[] out, int[] cursor, float[] ring, int corner, float u, float v, float nx, float ny, float nz,
                               float shade, Lock lock, float[] pivot) {
        int i = cursor[0];
        out[i] = ring[corner * 3] / 16f;
        out[i + 1] = ring[corner * 3 + 1] / 16f;
        out[i + 2] = ring[corner * 3 + 2] / 16f;
        out[i + 3] = u;
        out[i + 4] = v;
        out[i + 5] = nx;
        out[i + 6] = ny;
        out[i + 7] = nz;
        out[i + 8] = shade;
        out[i + 9] = v; // how far along the lock: the swing grows towards the tip
        out[i + 10] = lock.swing();
        out[i + 11] = pivot[0] / 16f;
        out[i + 12] = pivot[1] / 16f;
        out[i + 13] = pivot[2] / 16f;
        out[i + 14] = lock.hang() ? 1f : 0f;
        cursor[0] = i + STRIDE;
    }

    private static void corner(float[] ring, int c, float x, float y, float z) {
        ring[c * 3] = x;
        ring[c * 3 + 1] = y;
        ring[c * 3 + 2] = z;
    }

    private static float dist(float[] p, int a, int b) {
        return len(p[b * 3] - p[a * 3], p[b * 3 + 1] - p[a * 3 + 1], p[b * 3 + 2] - p[a * 3 + 2]);
    }

    private static float len(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}

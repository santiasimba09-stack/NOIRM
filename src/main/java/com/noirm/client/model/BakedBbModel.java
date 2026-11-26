package com.noirm.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.model.BbGeometry;
import com.noirm.model.BbModel;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Blockbench model baked for drawing: a tree of {@link Part}s (one per Blockbench group) holding ready-made quads.
 * Code poses parts by name before drawing, like vanilla {@code ModelPart}s: {@link Part#xRot} etc. (radians, added to
 * the rest pose), {@link Part#x} etc. (pixels, added to the pivot), {@link Part#scale}, {@link Part#visible}; or plays a
 * Blockbench animation with {@link #animate}. {@link #resetPose()} puts everything back.
 *
 * <p>Drawn in Blockbench's space at 1/16 block per pixel: y up, the model's front toward -z. A renderer usually
 * turns it to face the entity with {@code Axis.YP.rotationDegrees(180 - yaw)} and draws it from the entity's feet.
 */
public final class BakedBbModel {
    public final ResourceLocation id;
    /** The model's own texture (from the .bbmodel's first texture), or null when it names none the game can find. */
    public final ResourceLocation texture;
    public final List<Part> roots;
    private final Map<String, Part> parts;
    private final BbModel source;

    BakedBbModel(ResourceLocation id, ResourceLocation texture, BbModel model) {
        this.id = id;
        this.texture = texture;
        this.source = model;
        Map<String, Part> byName = new LinkedHashMap<>();
        List<Part> rootParts = new ArrayList<>();
        for (BbModel.Bone b : model.roots) {
            rootParts.add(bake(b, new float[3], model, byName));
        }
        this.roots = Collections.unmodifiableList(rootParts);
        this.parts = Collections.unmodifiableMap(byName);
    }

    /** A part by its Blockbench group name, or null. */
    public Part part(String name) {
        return parts.get(name);
    }

    public Map<String, Part> parts() {
        return parts;
    }

    public BbModel source() {
        return source;
    }

    public void resetPose() {
        for (Part p : parts.values()) {
            p.resetPose();
        }
    }

    /**
     * Poses the parts from the Blockbench animation {@code name} at {@code seconds} (looped or held as the animation
     * says), on top of the rest pose. Values follow Blockbench's own preview: rotation keyframes in degrees,
     * position keyframes in pixels, scale as a multiplier. Returns false if there is no such animation.
     */
    public boolean animate(String name, float seconds) {
        BbModel.Animation a = source.animations.get(name);
        if (a == null) {
            return false;
        }
        float t = BbGeometry.animationTime(a, seconds);
        for (BbModel.BoneTrack track : a.tracks().values()) {
            Part p = parts.get(track.bone());
            if (p == null) {
                continue;
            }
            float[] r = BbGeometry.sample(track.rotation(), t);
            if (r != null) {
                // Blockbench's preview: x and y rotation keyframes turn the other way from the group rotation fields.
                p.xRot -= (float) Math.toRadians(r[0]);
                p.yRot -= (float) Math.toRadians(r[1]);
                p.zRot += (float) Math.toRadians(r[2]);
            }
            float[] pos = BbGeometry.sample(track.position(), t);
            if (pos != null) {
                p.x -= pos[0];
                p.y += pos[1];
                p.z += pos[2];
            }
            float[] s = BbGeometry.sample(track.scale(), t);
            if (s != null) {
                p.scaleX *= s[0];
                p.scaleY *= s[1];
                p.scaleZ *= s[2];
            }
        }
        return true;
    }

    public void render(PoseStack pose, VertexConsumer out, int light, int overlay, float r, float g, float b, float a) {
        render(pose, out, light, overlay, r, g, b, a, null);
    }

    /**
     * Draws the model. {@code sideShade} (optional, indexed by {@link BbModel.Side#ordinal()}) darkens each cube face by
     * its own side, for models that copy an older hand-drawn mesh's baked shading; null = none (the game's own
     * lighting still applies).
     */
    public void render(PoseStack pose, VertexConsumer out, int light, int overlay, float r, float g, float b, float a, float[] sideShade) {
        for (Part p : roots) {
            p.render(pose, out, light, overlay, r, g, b, a, sideShade);
        }
    }

    private static Part bake(BbModel.Bone bone, float[] parentOrigin, BbModel model, Map<String, Part> byName) {
        float[] o = bone.origin;
        Part part = new Part(bone.name, o[0] - parentOrigin[0], o[1] - parentOrigin[1], o[2] - parentOrigin[2],
                (float) Math.toRadians(bone.rotation[0]), (float) Math.toRadians(bone.rotation[1]), (float) Math.toRadians(bone.rotation[2]),
                bone.visible);
        for (BbModel.Cube cube : bone.cubes) {
            if (!cube.visible) {
                continue;
            }
            for (BbModel.Quad q : cube.quads()) {
                BbModel.Texture tex = q.texture() < model.textures.size() ? model.textures.get(q.texture()) : null;
                float tw = tex != null ? tex.uvWidth() : model.textureWidth, th = tex != null ? tex.uvHeight() : model.textureHeight;
                float[] data = new float[4 * 5];
                for (int n = 0; n < 4; n++) {
                    data[n * 5] = (q.pos()[n][0] - o[0]) / 16f;
                    data[n * 5 + 1] = (q.pos()[n][1] - o[1]) / 16f;
                    data[n * 5 + 2] = (q.pos()[n][2] - o[2]) / 16f;
                    data[n * 5 + 3] = q.uv()[n][0] / tw;
                    data[n * 5 + 4] = q.uv()[n][1] / th;
                }
                part.quads.add(new BakedQuad(data, q.normal()[0], q.normal()[1], q.normal()[2], q.side().ordinal()));
            }
        }
        byName.putIfAbsent(bone.name, part);
        for (BbModel.Bone child : bone.children) {
            part.children.add(bake(child, o, model, byName));
        }
        return part;
    }

    record BakedQuad(float[] data, float nx, float ny, float nz, int side) {}

    /** One posable part (a Blockbench group). */
    public static final class Part {
        public final String name;
        public final List<Part> children = new ArrayList<>();
        final List<BakedQuad> quads = new ArrayList<>();
        private final float px, py, pz, prx, pry, prz;
        private final boolean restVisible;
        /** Pivot offset from the parent's pivot, in pixels (rest + animation). */
        public float x, y, z;
        /** Rotation in radians (rest + animation), applied x, then y, then z. */
        public float xRot, yRot, zRot;
        public float scaleX = 1, scaleY = 1, scaleZ = 1;
        public boolean visible;

        Part(String name, float px, float py, float pz, float rx, float ry, float rz, boolean visible) {
            this.name = name;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.prx = rx;
            this.pry = ry;
            this.prz = rz;
            this.restVisible = visible;
            resetPose();
        }

        public void resetPose() {
            x = px;
            y = py;
            z = pz;
            xRot = prx;
            yRot = pry;
            zRot = prz;
            scaleX = scaleY = scaleZ = 1;
            visible = restVisible;
        }

        private static final Vector4f POS = new Vector4f();
        private static final Vector3f NRM = new Vector3f();
        private static final Quaternionf ROT = new Quaternionf();

        public void render(PoseStack pose, VertexConsumer out, int light, int overlay, float r, float g, float b, float a) {
            render(pose, out, light, overlay, r, g, b, a, null);
        }

        public void render(PoseStack pose, VertexConsumer out, int light, int overlay, float r, float g, float b, float a, float[] sideShade) {
            if (!visible) {
                return;
            }
            pose.pushPose();
            pose.translate(x / 16f, y / 16f, z / 16f);
            if (xRot != 0f || yRot != 0f || zRot != 0f) {
                pose.mulPose(ROT.rotationZYX(zRot, yRot, xRot));
            }
            if (scaleX != 1f || scaleY != 1f || scaleZ != 1f) {
                pose.scale(scaleX, scaleY, scaleZ);
            }
            PoseStack.Pose last = pose.last();
            Matrix4f m = last.pose();
            Matrix3f nm = last.normal();
            for (BakedQuad q : quads) {
                nm.transform(NRM.set(q.nx, q.ny, q.nz));
                float k = sideShade == null ? 1f : sideShade[q.side];
                float cr = r * k, cg = g * k, cb = b * k;
                float[] d = q.data;
                for (int n = 0; n < 4; n++) {
                    int i = n * 5;
                    m.transform(POS.set(d[i], d[i + 1], d[i + 2], 1f));
                    out.vertex(POS.x(), POS.y(), POS.z(), cr, cg, cb, a, d[i + 3], d[i + 4], overlay, light, NRM.x(), NRM.y(), NRM.z());
                }
            }
            for (Part c : children) {
                c.render(pose, out, light, overlay, r, g, b, a, sideShade);
            }
            pose.popPose();
        }
    }
}

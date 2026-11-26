package com.noirm.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Blockbench model ({@code .bbmodel}, the file Blockbench opens and saves), read into plain data: the bone tree
 * (Blockbench "groups") with pivots and rotations, the cubes with their UVs (box UV or per-face UV), the textures
 * and the keyframe animations. No Minecraft client code here, so the server and game tests can read models too;
 * {@code client/model/BbModels} bakes them into something to draw. See {@code .claude/skills/blockbench-model}.
 *
 * <p><b>Coordinates</b> are Blockbench's own: pixels (16 = one block), y up, the model's feet at y 0 and its front
 * toward -z (north). Rotations are in degrees, applied in Blockbench's order (x, then y, then z, about the pivot).
 * What Blockbench shows is what the game draws.
 */
public final class BbModel {
    public final String name;
    public final int textureWidth;
    public final int textureHeight;
    public final List<Texture> textures;
    /** The top-level bones, in outliner order. Cubes outside any group sit in a bone named {@code root}. */
    public final List<Bone> roots;
    public final Map<String, Bone> bones;
    public final List<Cube> cubes;
    public final Map<String, Animation> animations;

    private BbModel(String name, int tw, int th, List<Texture> textures, List<Bone> roots, Map<String, Bone> bones,
                    List<Cube> cubes, Map<String, Animation> animations) {
        this.name = name;
        this.textureWidth = tw;
        this.textureHeight = th;
        this.textures = textures;
        this.roots = roots;
        this.bones = bones;
        this.cubes = cubes;
        this.animations = animations;
    }

    public Bone bone(String name) {
        return bones.get(name);
    }

    // ================================================================ data

    /**
     * A texture: its name and, when Blockbench saved it, its path relative to the model file (how the game finds it).
     * {@code uvWidth}/{@code uvHeight}: the UV size its faces use (the model's resolution unless the texture has its own).
     */
    public record Texture(String name, String relativePath, int uvWidth, int uvHeight) {}

    /** One face of a cube: its UV rectangle in texels (u1, v1, u2, v2; u1 &gt; u2 mirrors), texture index (-1 = no face), rotation (0/90/180/270). */
    public record Face(float u1, float v1, float u2, float v2, int texture, int rotation) {}

    public enum Side {
        NORTH, EAST, SOUTH, WEST, UP, DOWN;

        public final String key = name().toLowerCase(Locale.ROOT);
    }

    /** A Blockbench group: a bone that code can pose by name. */
    public static final class Bone {
        public final String name;
        public final String uuid;
        /** Pivot, absolute model pixels. */
        public final float[] origin;
        /** Rest rotation, degrees. */
        public final float[] rotation;
        public final boolean visible;
        public final List<Bone> children = new ArrayList<>();
        public final List<Cube> cubes = new ArrayList<>();
        public Bone parent;

        Bone(String name, String uuid, float[] origin, float[] rotation, boolean visible) {
            this.name = name;
            this.uuid = uuid;
            this.origin = origin;
            this.rotation = rotation;
            this.visible = visible;
        }
    }

    /** A cube: absolute corners and pivot (pixels), its own rotation (degrees), inflate, and its six faces' UVs (already resolved). */
    public static final class Cube {
        public final String name;
        public final String uuid;
        public final float[] from;
        public final float[] to;
        public final float[] origin;
        public final float[] rotation;
        public final float inflate;
        public final boolean boxUv;
        public final boolean visible;
        public final Map<Side, Face> faces;

        Cube(String name, String uuid, float[] from, float[] to, float[] origin, float[] rotation, float inflate,
             boolean boxUv, boolean visible, Map<Side, Face> faces) {
            this.name = name;
            this.uuid = uuid;
            this.from = from;
            this.to = to;
            this.origin = origin;
            this.rotation = rotation;
            this.inflate = inflate;
            this.boxUv = boxUv;
            this.visible = visible;
            this.faces = faces;
        }

        /**
         * The cube's faces as quads in absolute model pixels (the cube's own rotation applied, bones not), each with
         * its corners counter-clockwise seen from outside and the matching texel UVs.
         */
        public List<Quad> quads() {
            return BbGeometry.quads(this);
        }
    }

    /** One face ready to draw: 4 corners (x, y, z pixels) counter-clockwise from outside, their UVs in texels, the outward normal. */
    public record Quad(Side side, float[][] pos, float[][] uv, float[] normal, int texture) {}

    /** A keyframe animation: per bone, the rotation/position/scale channels. */
    public record Animation(String name, float length, Loop loop, Map<String, BoneTrack> tracks) {}

    public enum Loop { ONCE, LOOP, HOLD }

    /** One bone's channels (missing = untouched). */
    public record BoneTrack(String bone, List<Keyframe> rotation, List<Keyframe> position, List<Keyframe> scale) {}

    /** A keyframe at {@code time} seconds. Interpolation: linear, catmullrom or step (bezier is read as linear). */
    public record Keyframe(float time, float x, float y, float z, String interpolation) {}

    // ================================================================ reading

    public static BbModel read(InputStream in) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("can't read model: " + e.getMessage(), e);
        }
    }

    /** Reads a model from the classpath (e.g. {@code /assets/noirm/models/bb/giant_insect.bbmodel}). Null if it isn't there. */
    public static BbModel fromClasspath(String path) {
        InputStream in = BbModel.class.getResourceAsStream(path);
        return in == null ? null : read(in);
    }

    public static BbModel parse(JsonObject json) {
        String name = str(json, "name", "model");
        JsonObject res = json.has("resolution") ? json.getAsJsonObject("resolution") : new JsonObject();
        int tw = res.has("width") ? res.get("width").getAsInt() : 16;
        int th = res.has("height") ? res.get("height").getAsInt() : 16;
        boolean modelBoxUv = json.has("meta") && json.getAsJsonObject("meta").has("box_uv")
                && json.getAsJsonObject("meta").get("box_uv").getAsBoolean();

        List<Texture> textures = new ArrayList<>();
        if (json.has("textures")) {
            for (JsonElement e : json.getAsJsonArray("textures")) {
                JsonObject t = e.getAsJsonObject();
                textures.add(new Texture(str(t, "name", "texture"), str(t, "relative_path", null),
                        t.has("uv_width") ? t.get("uv_width").getAsInt() : tw, t.has("uv_height") ? t.get("uv_height").getAsInt() : th));
            }
        }

        Map<String, Cube> byUuid = new LinkedHashMap<>();
        List<Cube> cubes = new ArrayList<>();
        if (json.has("elements")) {
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject c = e.getAsJsonObject();
                String type = str(c, "type", "cube");
                if (!type.equals("cube")) {
                    continue; // meshes, locators, null objects: not supported (skipped)
                }
                Cube cube = cube(c, modelBoxUv, tw, th);
                byUuid.put(cube.uuid, cube);
                cubes.add(cube);
            }
        }

        List<Bone> roots = new ArrayList<>();
        Map<String, Bone> bones = new LinkedHashMap<>();
        Bone loose = null;
        if (json.has("outliner")) {
            for (JsonElement e : json.getAsJsonArray("outliner")) {
                if (e.isJsonPrimitive()) {
                    Cube c = byUuid.get(e.getAsString());
                    if (c != null) {
                        if (loose == null) {
                            loose = new Bone("root", "root", new float[3], new float[3], true);
                        }
                        loose.cubes.add(c);
                    }
                } else {
                    roots.add(group(e.getAsJsonObject(), null, byUuid, bones));
                }
            }
        } else {
            loose = new Bone("root", "root", new float[3], new float[3], true);
            loose.cubes.addAll(cubes);
        }
        if (loose != null) {
            roots.add(0, loose);
            bones.putIfAbsent("root", loose);
        }

        Map<String, Animation> animations = new LinkedHashMap<>();
        if (json.has("animations")) {
            Map<String, Bone> byBoneUuid = new LinkedHashMap<>();
            bones.values().forEach(b -> byBoneUuid.put(b.uuid, b));
            for (JsonElement e : json.getAsJsonArray("animations")) {
                Animation a = animation(e.getAsJsonObject(), byBoneUuid);
                animations.put(a.name(), a);
            }
        }
        return new BbModel(name, tw, th, Collections.unmodifiableList(textures), Collections.unmodifiableList(roots),
                Collections.unmodifiableMap(bones), Collections.unmodifiableList(cubes), Collections.unmodifiableMap(animations));
    }

    private static Bone group(JsonObject g, Bone parent, Map<String, Cube> byUuid, Map<String, Bone> bones) {
        Bone bone = new Bone(str(g, "name", "bone"), str(g, "uuid", "bone" + bones.size()), vec(g, "origin"), vec(g, "rotation"),
                !g.has("visibility") || g.get("visibility").getAsBoolean());
        bone.parent = parent;
        bones.putIfAbsent(bone.name, bone);
        if (g.has("children")) {
            for (JsonElement e : g.getAsJsonArray("children")) {
                if (e.isJsonPrimitive()) {
                    Cube c = byUuid.get(e.getAsString());
                    if (c != null) {
                        bone.cubes.add(c);
                    }
                } else {
                    bone.children.add(group(e.getAsJsonObject(), bone, byUuid, bones));
                }
            }
        }
        return bone;
    }

    private static Cube cube(JsonObject c, boolean modelBoxUv, int tw, int th) {
        float[] from = vec(c, "from");
        float[] to = vec(c, "to");
        float[] origin = c.has("origin") ? vec(c, "origin") : new float[3];
        boolean boxUv = c.has("box_uv") ? c.get("box_uv").getAsBoolean() : modelBoxUv;
        Map<Side, Face> faces = new EnumMap<>(Side.class);
        JsonObject fj = c.has("faces") ? c.getAsJsonObject("faces") : new JsonObject();
        if (boxUv) {
            float[] off = c.has("uv_offset") ? vec2(c.getAsJsonArray("uv_offset")) : new float[2];
            boolean mirror = c.has("mirror_uv") && c.get("mirror_uv").getAsBoolean();
            Map<Side, float[]> uvs = BbGeometry.boxUv(from, to, off[0], off[1], mirror);
            for (Side s : Side.values()) {
                JsonObject f = fj.has(s.key) ? fj.getAsJsonObject(s.key) : null;
                int tex = f == null ? 0 : texture(f);
                float[] uv = uvs.get(s);
                faces.put(s, new Face(uv[0], uv[1], uv[2], uv[3], tex, 0));
            }
        } else {
            for (Side s : Side.values()) {
                if (!fj.has(s.key)) {
                    continue;
                }
                JsonObject f = fj.getAsJsonObject(s.key);
                float[] uv = f.has("uv") ? vec4(f.getAsJsonArray("uv")) : new float[]{0, 0, 0, 0};
                faces.put(s, new Face(uv[0], uv[1], uv[2], uv[3], texture(f), f.has("rotation") ? f.get("rotation").getAsInt() : 0));
            }
        }
        return new Cube(str(c, "name", "cube"), str(c, "uuid", "cube" + System.identityHashCode(c)), from, to, origin, vec(c, "rotation"),
                c.has("inflate") ? c.get("inflate").getAsFloat() : 0f, boxUv, !c.has("visibility") || c.get("visibility").getAsBoolean(), faces);
    }

    private static int texture(JsonObject face) {
        if (!face.has("texture") || face.get("texture").isJsonNull()) {
            return -1;
        }
        JsonElement t = face.get("texture");
        return t.isJsonPrimitive() && t.getAsJsonPrimitive().isNumber() ? t.getAsInt() : -1;
    }

    private static Animation animation(JsonObject a, Map<String, Bone> byBoneUuid) {
        String loopName = str(a, "loop", "once");
        Loop loop = switch (loopName) {
            case "loop" -> Loop.LOOP;
            case "hold" -> Loop.HOLD;
            default -> Loop.ONCE;
        };
        Map<String, BoneTrack> tracks = new LinkedHashMap<>();
        if (a.has("animators")) {
            for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("animators").entrySet()) {
                JsonObject an = e.getValue().getAsJsonObject();
                if (an.has("type") && !"bone".equals(an.get("type").getAsString())) {
                    continue;
                }
                Bone bone = byBoneUuid.get(e.getKey());
                String boneName = bone != null ? bone.name : str(an, "name", e.getKey());
                List<Keyframe> rot = new ArrayList<>(), pos = new ArrayList<>(), scale = new ArrayList<>();
                if (an.has("keyframes")) {
                    for (JsonElement k : an.getAsJsonArray("keyframes")) {
                        JsonObject kf = k.getAsJsonObject();
                        JsonObject dp = kf.getAsJsonArray("data_points").get(0).getAsJsonObject();
                        Keyframe f = new Keyframe(kf.get("time").getAsFloat(), num(dp, "x"), num(dp, "y"), num(dp, "z"),
                                str(kf, "interpolation", "linear"));
                        switch (str(kf, "channel", "rotation")) {
                            case "position" -> pos.add(f);
                            case "scale" -> scale.add(f);
                            default -> rot.add(f);
                        }
                    }
                }
                for (List<Keyframe> list : List.of(rot, pos, scale)) {
                    list.sort((p, q) -> Float.compare(p.time(), q.time()));
                }
                tracks.put(boneName, new BoneTrack(boneName, rot, pos, scale));
            }
        }
        return new Animation(str(a, "name", "animation"), a.has("length") ? a.get("length").getAsFloat() : 0f, loop, tracks);
    }

    // ================================================================ json helpers

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    /** A keyframe value: a number, or a string holding a number (Molang expressions aren't supported: 0). */
    private static float num(JsonObject o, String key) {
        if (!o.has(key)) {
            return 0f;
        }
        JsonElement e = o.get(key);
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : Float.parseFloat(e.getAsString().trim());
        } catch (NumberFormatException ex) {
            return 0f;
        }
    }

    private static float[] vec(JsonObject o, String key) {
        float[] v = new float[3];
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) {
                v[i] = a.get(i).getAsFloat();
            }
        }
        return v;
    }

    private static float[] vec2(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    private static float[] vec4(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()};
    }
}

package com.noirm.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.client.anim.SkeletonPose.Bone;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A keyframe animation read from the same JSON files PlayerAnimator plays on players
 * ({@code assets/noirm/player_animation/*.json}, GeckoLib / Bedrock format), so NPCs move exactly
 * like the player does. Values are converted the way PlayerAnimator's {@code GeckoLibSerializer}
 * does (degrees → radians; {@code body} pitch/yaw and X position flipped and its position in
 * blocks; part Y positions flipped), then sampled with linear interpolation (or the keyframe's
 * {@code easing}).
 *
 * <p>NOIRM extension: an optional per-bone {@code "bend"} channel (degrees, one number per
 * keyframe) bends the elbow / knee / waist of the NPC model, like Better Combat's bends.
 * PlayerAnimator ignores it, so player animation is unchanged.
 *
 * <p>Plain Java + Gson only (no Minecraft client classes), so game tests can parse every file on
 * the server.
 */
public final class KeyframeAnimation {
    private static final int ROT = 0, POS = 1, BEND = 2;
    private static final float DEG = (float) (Math.PI / 180.0);

    public final String name;
    /** Length in ticks (seconds × 20). */
    public final float lengthTicks;
    public final boolean loop;
    private final Track[][] tracks = new Track[Bone.values().length][3];

    private KeyframeAnimation(String name, float lengthTicks, boolean loop) {
        this.name = name;
        this.lengthTicks = lengthTicks;
        this.loop = loop;
    }

    // ------------------------------------------------------------ sampling

    /**
     * Writes this animation's pose at {@code tick} (animation time, in ticks) into {@code out}.
     * Only channels the animation has are set; call {@link SkeletonPose#clear()} first.
     * Looping animations wrap; others hold their first / last frame outside their length.
     */
    public void sample(float tick, SkeletonPose out) {
        float t = tick;
        if (loop && lengthTicks > 0) {
            t = t % lengthTicks;
            if (t < 0) {
                t += lengthTicks;
            }
        } else {
            t = Math.max(0f, Math.min(t, lengthTicks));
        }
        for (Bone bone : Bone.values()) {
            Track[] bt = tracks[bone.ordinal()];
            if (bt[ROT] != null) {
                float[] v = bt[ROT].sample(t);
                out.set(bone, SkeletonPose.RX, v[0]);
                out.set(bone, SkeletonPose.RY, v[1]);
                out.set(bone, SkeletonPose.RZ, v[2]);
            }
            if (bt[POS] != null) {
                float[] v = bt[POS].sample(t);
                out.set(bone, SkeletonPose.PX, v[0]);
                out.set(bone, SkeletonPose.PY, v[1]);
                out.set(bone, SkeletonPose.PZ, v[2]);
            }
            if (bt[BEND] != null) {
                out.set(bone, SkeletonPose.BEND, bt[BEND].sample(t)[0]);
            }
        }
    }

    public boolean hasBend(Bone bone) {
        return tracks[bone.ordinal()][BEND] != null;
    }

    public int keyframeCount() {
        int n = 0;
        for (Track[] bt : tracks) {
            for (Track track : bt) {
                n += track == null ? 0 : track.ticks.length;
            }
        }
        return n;
    }

    private static final class Track {
        final float[] ticks;
        final float[][] values;
        final String[] eases;
        private final float[] scratch;

        Track(float[] ticks, float[][] values, String[] eases, int dim) {
            this.ticks = ticks;
            this.values = values;
            this.eases = eases;
            this.scratch = new float[dim];
        }

        float[] sample(float t) {
            int last = ticks.length - 1;
            if (t <= ticks[0]) {
                System.arraycopy(values[0], 0, scratch, 0, scratch.length);
                return scratch;
            }
            if (t >= ticks[last]) {
                System.arraycopy(values[last], 0, scratch, 0, scratch.length);
                return scratch;
            }
            int i = 0;
            while (i < last - 1 && t >= ticks[i + 1]) {
                i++;
            }
            float span = ticks[i + 1] - ticks[i];
            float f = span <= 0 ? 1f : (t - ticks[i]) / span;
            // GeckoLib's rule: a keyframe's easing shapes the way *into* it.
            f = ease(eases[i + 1], f);
            for (int k = 0; k < scratch.length; k++) {
                scratch[k] = values[i][k] + (values[i + 1][k] - values[i][k]) * f;
            }
            return scratch;
        }
    }

    static float ease(String name, float x) {
        if (name == null) {
            return x;
        }
        float pi = (float) Math.PI;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "step", "constant" -> 0f;
            case "easeinsine" -> 1 - (float) Math.cos(x * pi / 2);
            case "easeoutsine" -> (float) Math.sin(x * pi / 2);
            case "easeinoutsine", "catmullrom" -> -((float) Math.cos(pi * x) - 1) / 2;
            case "easeinquad" -> x * x;
            case "easeoutquad" -> 1 - (1 - x) * (1 - x);
            case "easeinoutquad" -> x < 0.5f ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
            case "easeincubic" -> x * x * x;
            case "easeoutcubic" -> 1 - (float) Math.pow(1 - x, 3);
            case "easeinoutcubic" -> x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
            case "easeinquart" -> x * x * x * x;
            case "easeoutquart" -> 1 - (float) Math.pow(1 - x, 4);
            default -> x;
        };
    }

    // ------------------------------------------------------------ parsing

    /** Parses a whole file: every animation under {@code "animations"}, by name. */
    public static Map<String, KeyframeAnimation> parse(Reader reader) {
        return parse(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public static Map<String, KeyframeAnimation> parse(JsonObject file) {
        Map<String, KeyframeAnimation> out = new LinkedHashMap<>();
        JsonObject animations = file.getAsJsonObject("animations");
        if (animations == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            out.put(entry.getKey(), parseAnimation(entry.getKey(), entry.getValue().getAsJsonObject()));
        }
        return out;
    }

    private static KeyframeAnimation parseAnimation(String name, JsonObject json) {
        boolean loop = json.has("loop") && json.get("loop").isJsonPrimitive()
                && (json.getAsJsonPrimitive("loop").isBoolean() ? json.get("loop").getAsBoolean()
                : "loop".equals(json.get("loop").getAsString()));
        float length = json.has("animation_length") ? json.get("animation_length").getAsFloat() * 20f : -1f;
        KeyframeAnimation animation = new KeyframeAnimation(name, length, loop);
        float maxTick = 0;
        JsonObject bones = json.has("bones") ? json.getAsJsonObject("bones") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
            Bone bone = Bone.byName(entry.getKey());
            if (bone == null || !entry.getValue().isJsonObject()) {
                continue; // a bone our skeleton doesn't have
            }
            JsonObject channels = entry.getValue().getAsJsonObject();
            Track[] bt = animation.tracks[bone.ordinal()];
            bt[ROT] = readTrack(channels.get("rotation"), 3, bone, ROT);
            bt[POS] = readTrack(channels.get("position"), 3, bone, POS);
            bt[BEND] = readTrack(channels.get("bend"), 1, bone, BEND);
            for (Track track : bt) {
                if (track != null) {
                    maxTick = Math.max(maxTick, track.ticks[track.ticks.length - 1]);
                }
            }
        }
        if (length < 0) {
            return new KeyframeAnimation(name, maxTick, loop).withTracks(animation);
        }
        return animation;
    }

    private KeyframeAnimation withTracks(KeyframeAnimation from) {
        for (int i = 0; i < tracks.length; i++) {
            System.arraycopy(from.tracks[i], 0, tracks[i], 0, 3);
        }
        return this;
    }

    private record Key(float tick, float[] value, String ease) {}

    private static Track readTrack(JsonElement channel, int dim, Bone bone, int kind) {
        if (channel == null || channel.isJsonNull()) {
            return null;
        }
        List<Key> keys = new ArrayList<>();
        if (channel.isJsonArray() || channel.isJsonPrimitive()) {
            keys.add(new Key(0, vector(channel, dim), null));
        } else if (channel.isJsonObject()) {
            JsonObject object = channel.getAsJsonObject();
            if (object.has("vector") && !isTimeKey(object)) {
                keys.add(new Key(0, vector(object.get("vector"), dim), null));
            } else {
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    float seconds;
                    try {
                        seconds = Float.parseFloat(entry.getKey());
                    } catch (NumberFormatException e) {
                        continue; // "easing" etc. at the channel level
                    }
                    JsonElement frame = entry.getValue();
                    String ease = null;
                    JsonElement value = frame;
                    if (frame.isJsonObject()) {
                        JsonObject f = frame.getAsJsonObject();
                        if (f.has("easing")) {
                            ease = f.get("easing").getAsString();
                        } else if (f.has("lerp_mode") && "catmullrom".equals(f.get("lerp_mode").getAsString())) {
                            ease = "catmullrom";
                        }
                        value = f.has("vector") ? f.get("vector") : f.has("post") ? f.get("post")
                                : f.has("pre") ? f.get("pre") : f.has("value") ? f.get("value") : null;
                        if (value != null && value.isJsonObject() && value.getAsJsonObject().has("vector")) {
                            value = value.getAsJsonObject().get("vector");
                        }
                    }
                    if (value == null) {
                        continue;
                    }
                    keys.add(new Key(seconds * 20f, vector(value, dim), ease));
                }
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        keys.sort((a, b) -> Float.compare(a.tick, b.tick));
        float[] ticks = new float[keys.size()];
        float[][] values = new float[keys.size()][];
        String[] eases = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            ticks[i] = key.tick;
            values[i] = convert(key.value, bone, kind);
            eases[i] = key.ease;
        }
        return new Track(ticks, values, eases, dim);
    }

    private static boolean isTimeKey(JsonObject object) {
        for (String key : object.keySet()) {
            try {
                Float.parseFloat(key);
                return true;
            } catch (NumberFormatException ignored) {
                // not a time
            }
        }
        return false;
    }

    private static float[] vector(JsonElement element, int dim) {
        float[] out = new float[dim];
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < dim && i < array.size(); i++) {
                out[i] = number(array.get(i));
            }
        } else if (element.isJsonPrimitive()) {
            float v = number(element);
            for (int i = 0; i < dim; i++) {
                out[i] = v;
            }
        }
        return out;
    }

    private static float number(JsonElement element) {
        try {
            float v = element.getAsFloat();
            return Float.isFinite(v) ? v : 0f;
        } catch (RuntimeException e) {
            return 0f; // a Molang expression: not supported, treated as 0
        }
    }

    /** The same conversions PlayerAnimator's GeckoLibSerializer applies. */
    private static float[] convert(float[] raw, Bone bone, int kind) {
        float[] v = raw.clone();
        switch (kind) {
            case ROT -> {
                if (bone == Bone.BODY) {
                    v[0] = -v[0];
                    v[1] = -v[1];
                }
                for (int i = 0; i < 3; i++) {
                    v[i] *= DEG;
                }
            }
            case POS -> {
                if (bone == Bone.BODY) {
                    v[0] = -v[0] / 16f;
                    v[1] = v[1] / 16f;
                    v[2] = v[2] / 16f;
                } else {
                    v[1] = -v[1];
                }
            }
            case BEND -> v[0] *= DEG;
            default -> { }
        }
        return v;
    }
}

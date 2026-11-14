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
 * <p>NOIRM key-pose extensions (the 2026-11 combat rebuild, see docs/COMBAT_DESIGN.md):
 * <ul>
 *   <li>{@code "easing": "snap"} (or {@code "snap": true}, or {@code "snap": <fraction>}) on a
 *       keyframe: a near-stepped move into it. The pose is reached within the first
 *       {@link #SNAP_REACH} of the gap (or the given fraction), with a tiny
 *       {@link #SNAP_OVERSHOOT}, then stays. The engine's springs don't smooth these out.</li>
 *   <li>{@code "hold": <seconds>} on a keyframe: its pose is held that long before the next key
 *       starts (a strike pose frozen on impact).</li>
 *   <li>{@code "strike": <seconds>} on the animation: the strike marker, the moment the hit lands
 *       (the move's windup), and {@code "strike_hold"} how long the strike pose is held.</li>
 * </ul>
 *
 * <p>Plain Java + Gson only (no Minecraft client classes), so game tests can parse every file on
 * the server.
 */
public final class KeyframeAnimation {
    private static final int ROT = 0, POS = 1, BEND = 2;
    private static final float DEG = (float) (Math.PI / 180.0);
    /** A snap key reaches its pose within this share of the gap before it. */
    public static final float SNAP_REACH = 0.35f;
    /** …overshooting it by this share of the move on the way (a tiny "crack", then still). */
    public static final float SNAP_OVERSHOOT = 0.06f;

    public final String name;
    /** Length in ticks (seconds × 20). */
    public final float lengthTicks;
    public final boolean loop;
    /** The strike marker: the tick the hit lands on (-1 = none). */
    public float strikeTick = -1f;
    /** How many ticks the strike pose is held after {@link #strikeTick}. */
    public float strikeHold;
    private final Track[][] tracks = new Track[Bone.values().length][3];
    /** Animation-time windows (start, end pairs, in ticks) where a snap key or its hold plays. */
    private float[] snapWindows = new float[0];

    private KeyframeAnimation(String name, float lengthTicks, boolean loop) {
        this.name = name;
        this.lengthTicks = lengthTicks;
        this.loop = loop;
    }

    /**
     * 1 while a snap key is being reached or held at animation time {@code tick} (the engine's
     * springs should follow it exactly), easing to 0 over one tick after. Looping animations wrap.
     */
    public float snapWeight(float tick) {
        if (snapWindows.length == 0) {
            return 0f;
        }
        float t = tick;
        if (loop && lengthTicks > 0) {
            t = t % lengthTicks;
            if (t < 0) {
                t += lengthTicks;
            }
        }
        float best = 0f;
        for (int i = 0; i < snapWindows.length; i += 2) {
            float start = snapWindows[i], end = snapWindows[i + 1];
            if (t >= start && t <= end) {
                return 1f;
            }
            if (t > end && t < end + 1f) {
                best = Math.max(best, 1f - (t - end));
            }
        }
        return best;
    }

    /** True if any key of this animation snaps. */
    public boolean hasSnaps() {
        return snapWindows.length > 0;
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
        /** Per key: 0 = normal, else a snap reaching its pose within this share of the gap. */
        final float[] snaps;
        private final float[] scratch;

        Track(float[] ticks, float[][] values, String[] eases, float[] snaps, int dim) {
            this.ticks = ticks;
            this.values = values;
            this.eases = eases;
            this.snaps = snaps;
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
            if (snaps[i + 1] > 0f) {
                f = snap(f, snaps[i + 1]);
                for (int k = 0; k < scratch.length; k++) {
                    scratch[k] = values[i][k] + (values[i + 1][k] - values[i][k]) * f;
                }
                return scratch;
            }
            String ease = eases[i + 1];
            if (ease != null && ease.equalsIgnoreCase("catmullrom")) {
                // A smooth curve through the neighbouring keys (no stop at each key).
                float[] p0 = values[Math.max(0, i - 1)], p1 = values[i], p2 = values[i + 1], p3 = values[Math.min(last, i + 2)];
                float f2 = f * f, f3 = f2 * f;
                for (int k = 0; k < scratch.length; k++) {
                    scratch[k] = 0.5f * (2 * p1[k] + (-p0[k] + p2[k]) * f
                            + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * f2
                            + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * f3);
                }
                return scratch;
            }
            f = ease(ease, f);
            for (int k = 0; k < scratch.length; k++) {
                scratch[k] = values[i][k] + (values[i + 1][k] - values[i][k]) * f;
            }
            return scratch;
        }
    }

    /**
     * Near-stepped: reaches 1 at {@code reach} (a share of the gap), overshooting by about
     * {@link #SNAP_OVERSHOOT} on the way, then holds 1.
     */
    static float snap(float x, float reach) {
        if (x >= reach) {
            return 1f;
        }
        float u = Math.max(0f, x / reach);
        // An "out-back" curve with a small peak overshoot, landing exactly on 1.
        float c1 = 1.70158f * SNAP_OVERSHOOT / 0.1f;
        float c3 = c1 + 1f;
        float v = u - 1f;
        return 1f + c3 * v * v * v + c1 * v * v;
    }

    static float ease(String name, float x) {
        if (name == null) {
            return x;
        }
        float pi = (float) Math.PI;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "step", "constant" -> 0f;
            case "snap" -> snap(x, SNAP_REACH);
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
            case "easeinoutquart" -> x < 0.5f ? 8 * x * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 4) / 2;
            case "easeinexpo" -> x <= 0f ? 0f : (float) Math.pow(2, 10 * x - 10);
            case "easeoutexpo" -> x >= 1f ? 1f : 1 - (float) Math.pow(2, -10 * x);
            // "Back": a small wind-back before (in) or overshoot after (out) — anticipation and follow-through.
            case "easeinback" -> 2.70158f * x * x * x - 1.70158f * x * x;
            case "easeoutback" -> 1 + 2.70158f * (float) Math.pow(x - 1, 3) + 1.70158f * (float) Math.pow(x - 1, 2);
            case "easeinoutback" -> x < 0.5f
                    ? (float) (Math.pow(2 * x, 2) * ((2.5949095f + 1) * 2 * x - 2.5949095f)) / 2
                    : (float) (Math.pow(2 * x - 2, 2) * ((2.5949095f + 1) * (x * 2 - 2) + 2.5949095f) + 2) / 2;
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
        KeyframeAnimation out = length < 0 ? new KeyframeAnimation(name, maxTick, loop).withTracks(animation) : animation;
        if (json.has("strike")) {
            out.strikeTick = json.get("strike").getAsFloat() * 20f;
            out.strikeHold = json.has("strike_hold") ? json.get("strike_hold").getAsFloat() * 20f : 0f;
        }
        out.snapWindows = snapWindows(out.tracks);
        return out;
    }

    /** Where snap keys play: from the key before each snap key to the snap key (and its hold). */
    private static float[] snapWindows(Track[][] tracks) {
        List<float[]> windows = new ArrayList<>();
        for (Track[] bt : tracks) {
            for (Track track : bt) {
                if (track == null) {
                    continue;
                }
                for (int i = 1; i < track.ticks.length; i++) {
                    if (track.snaps[i] > 0f) {
                        float end = track.ticks[i];
                        // A hold is a repeat of the same key right after it: include it.
                        if (i + 1 < track.ticks.length && java.util.Arrays.equals(track.values[i], track.values[i + 1])) {
                            end = track.ticks[i + 1];
                        }
                        windows.add(new float[]{track.ticks[i - 1], end});
                    }
                }
            }
        }
        windows.sort((a, b) -> Float.compare(a[0], b[0]));
        List<float[]> merged = new ArrayList<>();
        for (float[] w : windows) {
            float[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && w[0] <= last[1]) {
                last[1] = Math.max(last[1], w[1]);
            } else {
                merged.add(w.clone());
            }
        }
        float[] out = new float[merged.size() * 2];
        for (int i = 0; i < merged.size(); i++) {
            out[2 * i] = merged.get(i)[0];
            out[2 * i + 1] = merged.get(i)[1];
        }
        return out;
    }

    private KeyframeAnimation withTracks(KeyframeAnimation from) {
        for (int i = 0; i < tracks.length; i++) {
            System.arraycopy(from.tracks[i], 0, tracks[i], 0, 3);
        }
        return this;
    }

    private record Key(float tick, float[] value, String ease, float snap) {}

    private static Track readTrack(JsonElement channel, int dim, Bone bone, int kind) {
        if (channel == null || channel.isJsonNull()) {
            return null;
        }
        List<Key> keys = new ArrayList<>();
        if (channel.isJsonArray() || channel.isJsonPrimitive()) {
            keys.add(new Key(0, vector(channel, dim), null, 0f));
        } else if (channel.isJsonObject()) {
            JsonObject object = channel.getAsJsonObject();
            if (object.has("vector") && !isTimeKey(object)) {
                keys.add(new Key(0, vector(object.get("vector"), dim), null, 0f));
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
                    float snap = 0f;
                    float hold = 0f;
                    JsonElement value = frame;
                    if (frame.isJsonObject()) {
                        JsonObject f = frame.getAsJsonObject();
                        if (f.has("easing")) {
                            ease = f.get("easing").getAsString();
                        } else if (f.has("lerp_mode") && "catmullrom".equals(f.get("lerp_mode").getAsString())) {
                            ease = "catmullrom";
                        }
                        if ("snap".equalsIgnoreCase(ease)) {
                            snap = SNAP_REACH;
                        }
                        if (f.has("snap") && f.get("snap").isJsonPrimitive()) {
                            var p = f.getAsJsonPrimitive("snap");
                            snap = p.isBoolean() ? (p.getAsBoolean() ? SNAP_REACH : 0f) : Math.max(0.01f, Math.min(1f, p.getAsFloat()));
                        }
                        if (f.has("hold")) {
                            hold = Math.max(0f, f.get("hold").getAsFloat() * 20f);
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
                    float[] v = vector(value, dim);
                    keys.add(new Key(seconds * 20f, v, ease, snap));
                    if (hold > 0f) {
                        keys.add(new Key(seconds * 20f + hold, v.clone(), "linear", 0f)); // held still, then on to the next key
                    }
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
        float[] snaps = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            ticks[i] = key.tick;
            values[i] = convert(key.value, bone, kind);
            eases[i] = key.ease;
            snaps[i] = key.snap;
        }
        return new Track(ticks, values, eases, snaps, dim);
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

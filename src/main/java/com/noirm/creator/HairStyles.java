package com.noirm.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 3D hair styles (docs/ART_STYLE.md, "Hair, eyes and bodies rework"), read from
 * {@code assets/noirm/hair/styles/<id>.json}, which {@code tools/skingen/gen_hair.py} writes. A style is a
 * list of {@link Lock}s: short tapered clumps for spiky and messy hair, long draped ribbons for long hair.
 * The client bakes each style into a vertex list once ({@code client/HairLayer}).
 *
 * <p>This class has no client code, so game tests can check every style, and the server can name them.
 * Also here: what a helmet, hat, hood or mask does to the hair ({@link Cover}, from
 * {@code assets/noirm/hair/headgear.json}).
 */
public final class HairStyles {
    private static final String STYLES = "/assets/noirm/hair/styles/";
    private static final String HEADGEAR = "/assets/noirm/hair/headgear.json";

    /**
     * One lock of hair: a polyline (model pixels, relative to the head's pivot) with a width and a
     * thickness at each point. {@code out} is the way its flat side faces.
     */
    public record Lock(float[] pts, float[] width, float[] depth, float[] out, float shade, int cell, boolean sheet,
                       float swing, float[] pivot, boolean hang) {
        public int points() {
            return pts.length / 3;
        }
    }

    public record Style(String id, String scalp, List<Lock> locks) {}

    /** What a worn head piece does to the hair. */
    public enum Cover {
        /** Nothing in the way (masks, glasses). */
        NONE,
        /** A band around the head (forehead protectors): the hair is pressed in a little. */
        SQUASH,
        /** A hat or helmet: only the hair hanging below it shows. */
        CROWN,
        /** A hood: no hair at all. */
        ALL
    }

    private static final Map<String, Style> CACHE = new HashMap<>();
    private static JsonObject headgear;

    private HairStyles() {}

    /** Every style id, in the creator's order (the player's hair options, without "none"). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < PlayerLook.Slot.HAIR.size(); i++) {
            String id = PlayerLook.Slot.HAIR.option(i);
            if (!id.equals("none")) {
                out.add(id);
            }
        }
        return out;
    }

    /** The style, or null if there's none by that id ("none", unknown or broken). */
    public static synchronized Style get(String id) {
        if (id == null || id.isEmpty() || id.equals("none")) {
            return null;
        }
        if (CACHE.containsKey(id)) {
            return CACHE.get(id);
        }
        Style style = null;
        try {
            style = read(id);
        } catch (RuntimeException e) {
            Noirm.LOGGER.warn("Could not read hair style {}", id, e);
        }
        CACHE.put(id, style);
        return style;
    }

    /** The painted part under the 3D hair ({@code ninja/parts/scalp_<name>}), or null for bald. */
    public static String scalp(String id) {
        Style style = get(id);
        return style == null ? null : style.scalp();
    }

    /** Reads a style file; throws with a reason if it's missing or malformed. */
    public static Style read(String id) {
        try (InputStream in = HairStyles.class.getResourceAsStream(STYLES + id + ".json")) {
            if (in == null) {
                throw new IllegalStateException("missing " + STYLES + id + ".json");
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Lock> locks = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("locks")) {
                locks.add(lock(element.getAsJsonObject()));
            }
            return new Style(json.get("id").getAsString(), json.get("scalp").getAsString(), List.copyOf(locks));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Lock lock(JsonObject o) {
        JsonArray pts = o.getAsJsonArray("pts");
        float[] p = new float[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            JsonArray xyz = pts.get(i).getAsJsonArray();
            for (int k = 0; k < 3; k++) {
                p[i * 3 + k] = xyz.get(k).getAsFloat();
            }
        }
        float[] pivot = o.has("pivot") ? floats(o.getAsJsonArray("pivot")) : new float[]{p[0], p[1], p[2]};
        return new Lock(p, floats(o.getAsJsonArray("w")), floats(o.getAsJsonArray("d")), floats(o.getAsJsonArray("out")),
                o.get("shade").getAsFloat(), o.get("cell").getAsInt(), "sheet".equals(o.get("tex").getAsString()),
                o.has("swing") ? o.get("swing").getAsFloat() : 0f, pivot, o.has("hang") && o.get("hang").getAsBoolean());
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    /** Everything wrong with a style (empty if it's fine): for the game tests. */
    public static List<String> problems(Style style) {
        List<String> out = new ArrayList<>();
        if (style.locks().isEmpty() && !style.id().equals("buzz")) {
            out.add("no locks");
        }
        if (style.locks().size() > 160) {
            out.add("too many locks to draw cheaply: " + style.locks().size());
        }
        for (int i = 0; i < style.locks().size(); i++) {
            Lock lock = style.locks().get(i);
            int n = lock.points();
            String at = style.id() + " lock " + i + ": ";
            if (n < 2 || n > 12) {
                out.add(at + n + " points");
            }
            if (lock.width().length != n || lock.depth().length != n) {
                out.add(at + "widths/depths don't match the points");
            }
            if (lock.out().length != 3 || lock.pivot().length != 3) {
                out.add(at + "out/pivot need 3 numbers");
            }
            for (float v : lock.pts()) {
                if (!Float.isFinite(v) || Math.abs(v) > 40) {
                    out.add(at + "a point is out of range: " + v);
                    break;
                }
            }
            for (float v : lock.width()) {
                if (!(v >= 0 && v < 8)) {
                    out.add(at + "a width is out of range: " + v);
                    break;
                }
            }
            if (!(lock.shade() > 0 && lock.shade() <= 1)) {
                out.add(at + "shade " + lock.shade());
            }
            if (lock.cell() < 0 || lock.cell() >= (lock.sheet() ? 2 : 4)) {
                out.add(at + "cell " + lock.cell());
            }
            if (lock.swing() < 0 || lock.swing() > 1) {
                out.add(at + "swing " + lock.swing());
            }
        }
        if (style.scalp() == null || style.scalp().isEmpty()) {
            out.add(style.id() + ": no scalp part");
        }
        return out;
    }

    // ------------------------------------------------------------ head gear

    /** What the item worn on the head does to the hair (by its id, from headgear.json). */
    public static Cover cover(ItemStack head) {
        if (head.isEmpty()) {
            return Cover.NONE;
        }
        // asked every frame for everyone drawn: remembered per item
        return BY_ITEM.computeIfAbsent(head.getItem(), item -> {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            return key == null ? Cover.CROWN : cover(key);
        });
    }

    private static final Map<net.minecraft.world.item.Item, Cover> BY_ITEM = new java.util.concurrent.ConcurrentHashMap<>();

    public static synchronized Cover cover(ResourceLocation item) {
        JsonObject rules = headgear();
        String id = item.toString();
        for (Cover cover : new Cover[]{Cover.ALL, Cover.CROWN, Cover.SQUASH, Cover.NONE}) {
            JsonArray list = rules.getAsJsonArray(cover.name().toLowerCase(java.util.Locale.ROOT));
            if (list == null) {
                continue;
            }
            for (JsonElement prefix : list) {
                if (id.startsWith(prefix.getAsString())) {
                    return cover;
                }
            }
        }
        // anything else on the head (vanilla helmets, pumpkins, skulls) covers the crown
        return Cover.CROWN;
    }

    private static JsonObject headgear() {
        if (headgear == null) {
            try (InputStream in = HairStyles.class.getResourceAsStream(HEADGEAR)) {
                headgear = in == null ? new JsonObject()
                        : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                Noirm.LOGGER.warn("Could not read {}", HEADGEAR, e);
                headgear = new JsonObject();
            }
        }
        return headgear;
    }
}

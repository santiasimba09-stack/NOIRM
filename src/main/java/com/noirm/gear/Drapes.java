package com.noirm.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 3D cloth and props some gear carries on top of its flat armour picture: capes that hang and
 * flap, robe skirts that move with the legs, high collars, hoods, big sleeves, shoulder and thigh
 * plates, a straw hat with hanging strips, a gourd or a bug jar on the back. Which item carries what
 * is data: {@code assets/noirm/gear/drapes.json} (written by {@code tools/gear/clan_wear.py}).
 *
 * <p>This class only reads the data (it has no client code, so game tests can check it); the
 * client draws it ({@code client/DrapeLayer}) and moves the cloth with {@link ClothSim}.
 *
 * <p>A cape can also be <b>sewn onto</b> any chest piece ({@link CapeSewingRecipe}): the chest
 * stack then carries the cape stack in its {@link #SEWN_CAPE} tag, and draws its cape too.
 */
public final class Drapes {
    public static final String FILE = "/assets/" + Noirm.MOD_ID + "/gear/drapes.json";
    /** Tag on a chest stack holding the cape sewn onto it (a saved item stack). */
    public static final String SEWN_CAPE = "NoirmCape";

    /** What a drape is. */
    public enum Kind {
        /** Cloth hanging from the shoulders down the back; the cloth simulation moves it. */
        CAPE,
        /** A ring of cloth (or plates) hanging from the waist: robes, coat tails, thigh plates. */
        SKIRT,
        /** A high collar around the neck and jaw (it turns with the head). */
        COLLAR,
        /** A hood shell over the head (the hair is hidden by headgear.json). */
        HOOD,
        /** Wide sleeves hanging under the forearms. */
        SLEEVES,
        /** Plates on the shoulders (they ride the upper arms). */
        SHOULDERS,
        /** A wide straw hat (a cone) with strips hanging from the brim. */
        HAT,
        /** Something carried on the back: "gourd", "jar". */
        PROP
    }

    /**
     * One drape. Sizes are in model pixels (a player is 32 tall). Not every field means something
     * for every kind; unknown ones keep their defaults.
     *
     * @param texture  the texture (outside on the left half, lining on the right half for cloth)
     * @param length   how far it hangs (cape, skirt, sleeves, hat strips)
     * @param width    cape width; for a hat, the brim radius
     * @param wrap     how far the cape curls forward around the shoulders
     * @param flare    how much wider the bottom is than the top (0 = straight)
     * @param top      skirt: how far below the top of the hips the ring hangs from (0..6)
     * @param open     skirt: the gap at the front (coat tails, an open robe); collar: its front height
     * @param panels   skirt: split into this many separate plates (0 = one closed ring)
     * @param height   collar height above the neck; hood peak
     * @param stiff    0 = loose cloth, 1 = stiff (plates, heavy wool)
     * @param shape    prop shape ("gourd", "jar"), hat strips count as {@code panels}
     * @param tinted   the cloth takes the item's dye colour
     */
    public record Part(Kind kind, ResourceLocation texture, float length, float width, float wrap, float flare, float top,
                       float open, int panels, float height, float stiff, String shape, boolean tinted) {}

    private static Map<Item, List<Part>> byItem;
    private static final List<String> problems = new ArrayList<>();

    private Drapes() {}

    /** The drapes this item carries (empty for most items). */
    public static List<Part> of(Item item) {
        return all().getOrDefault(item, List.of());
    }

    /** Every item that carries drapes. */
    public static synchronized Map<Item, List<Part>> all() {
        if (byItem == null) {
            byItem = load();
        }
        return byItem;
    }

    /** Lines that couldn't be read (unknown item, unknown kind…); empty when the file is clean. */
    public static synchronized List<String> problems() {
        all();
        return List.copyOf(problems);
    }

    /** The cape sewn onto a chest stack, or empty. */
    public static ItemStack sewnCape(ItemStack chest) {
        if (chest.isEmpty() || !chest.hasTag() || !chest.getTag().contains(SEWN_CAPE, 10)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(chest.getTag().getCompound(SEWN_CAPE));
    }

    private static Map<Item, List<Part>> load() {
        problems.clear();
        Map<Item, List<Part>> out = new HashMap<>();
        try (InputStream in = Drapes.class.getResourceAsStream(FILE)) {
            if (in == null) {
                problems.add("missing " + FILE);
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (e.getKey().startsWith("_")) {
                    continue; // comments
                }
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                if (item == null || id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                    problems.add("unknown item " + e.getKey());
                    continue;
                }
                List<Part> parts = new ArrayList<>();
                JsonArray list = e.getValue().getAsJsonArray();
                for (JsonElement el : list) {
                    Part part = parse(e.getKey(), el.getAsJsonObject());
                    if (part != null) {
                        parts.add(part);
                    }
                }
                out.put(item, List.copyOf(parts));
            }
        } catch (Exception ex) {
            problems.add("can't read " + FILE + ": " + ex);
        }
        return out;
    }

    private static Part parse(String owner, JsonObject o) {
        Kind kind;
        try {
            kind = Kind.valueOf(str(o, "type", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            problems.add(owner + ": unknown type " + str(o, "type", ""));
            return null;
        }
        String tex = str(o, "texture", "");
        if (tex.isEmpty()) {
            problems.add(owner + ": no texture");
            return null;
        }
        ResourceLocation texture = tex.contains(":") ? ResourceLocation.tryParse(tex)
                : Noirm.id("textures/models/drape/" + tex + ".png");
        if (texture == null) {
            problems.add(owner + ": bad texture " + tex);
            return null;
        }
        return new Part(kind, texture, num(o, "length", 18), num(o, "width", 10), num(o, "wrap", 1.2f), num(o, "flare", 0.2f),
                num(o, "top", 2), num(o, "open", 0), (int) num(o, "panels", 0), num(o, "height", 3), num(o, "stiff", 0.2f),
                str(o, "shape", ""), o.has("tinted") && o.get("tinted").getAsBoolean());
    }

    private static float num(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }
}

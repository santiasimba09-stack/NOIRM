package com.noirm.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import net.minecraft.util.RandomSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a ninja looks like, as one short text key: {@code "<clan folder>:<hex seed>"}, e.g.
 * {@code "uchiha:5f3a9c21"}. The server only stores and syncs the key; the client turns it into
 * a skin ({@code client/NinjaSkins}) by decoding the seed into features (skin tone, hair style and
 * colour, eyes, brows, undershirt, pants, wraps, face mark, accessory) with the tables in
 * {@code textures/entity/ninja/parts/looks.json}, written by {@code tools/skingen/gen_parts.py}.
 * The same seed always gives the same look. For generated clans the top 16 bits of the seed are
 * the clan's "family", so its members tend to share a face mark and hair colour.
 *
 * <p>{@code tools/skingen/looks.py} is the Python twin of {@link #decode} (for previews); keep them
 * in step.
 */
public final class NinjaLook {
    /** Clan folders that have their own appearance tables; every other clan uses "generic". */
    public static final List<String> FOLDERS = List.of("uchiha", "senju", "hyuga", "uzumaki", "generic");
    /** Decoding order: changing it changes every ninja's look. */
    public static final List<String> FIELDS = List.of("skin", "hair_style", "hair_color", "eyes", "eye_color", "brows",
            "shirt", "shirt_color", "pants", "pants_color", "wraps", "mark", "accessory", "accessory_color");
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long LOW48 = (1L << 48) - 1;
    private static final String TABLES = "/assets/noirm/textures/entity/ninja/parts/looks.json";

    private static JsonObject tables;

    private NinjaLook() {}

    /** The decoded look: one value per field (colours as "#rrggbb"). */
    public record Features(String folder, Map<String, String> values) {
        public String get(String field) {
            return values.getOrDefault(field, "");
        }

        /** A colour field as 0xRRGGBB. */
        public int color(String field) {
            String hex = get(field);
            try {
                return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
            } catch (NumberFormatException e) {
                return 0x808080;
            }
        }

        public int wraps() {
            try {
                return Integer.parseInt(get("wraps"));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    // ---------------------------------------------------------------- keys

    public static String folderFor(String clanId) {
        return FOLDERS.contains(clanId) ? clanId : "generic";
    }

    public static String key(String folder, long seed) {
        return folder + ":" + Long.toHexString(seed);
    }

    /** A fresh random look for a member of this clan. */
    public static String random(String clanId, RandomSource random) {
        String folder = folderFor(clanId);
        long seed = random.nextLong() & LOW48;
        if (folder.equals("generic")) {
            seed |= (long) (clanId.hashCode() & 0xFFFF) << 48; // the clan's family look
        }
        return key(folder, seed);
    }

    /** Whether a look belongs to this clan (its folder and, for generated clans, its family). */
    public static boolean fits(String key, String clanId) {
        if (key.isEmpty()) {
            return false;
        }
        String folder = folderFor(clanId);
        if (!folder(key).equals(folder)) {
            return false;
        }
        return !folder.equals("generic") || (seed(key) >>> 48) == (clanId.hashCode() & 0xFFFF);
    }

    public static String folder(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? "generic" : key.substring(0, colon);
    }

    public static long seed(String key) {
        int colon = key.indexOf(':');
        try {
            return Long.parseUnsignedLong(key.substring(colon + 1), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Turns any saved value into a valid key. Ninjas saved before appearance keys had an outfit
     * file like {@code "uchiha/3"}: they keep their clan folder and get a fixed seed from the number.
     */
    public static String normalize(String saved) {
        if (saved == null || saved.isEmpty()) {
            return "";
        }
        int slash = saved.indexOf('/');
        if (slash > 0 && saved.indexOf(':') < 0) {
            String folder = folderFor(saved.substring(0, slash));
            int n;
            try {
                n = Integer.parseInt(saved.substring(slash + 1));
            } catch (NumberFormatException e) {
                n = 0;
            }
            return key(folder, ((n + 1L) * GOLDEN) & LOW48);
        }
        String folder = folderFor(folder(saved));
        return key(folder, seed(saved));
    }

    /** The next/previous look of the same clan (and family): for the ally screen's arrows. */
    public static String cycle(String key, int step) {
        String folder = key.isEmpty() ? "generic" : folder(key);
        long seed = seed(key);
        long low = (seed + step) & LOW48;
        return key(folder, (seed & ~LOW48) | low);
    }

    /** A short readable name for a look, e.g. "Uchiha #9c41". */
    public static String label(String key) {
        if (key.isEmpty()) {
            return "-";
        }
        String folder = folder(key);
        String hex = Long.toHexString(seed(key) & 0xFFFF);
        return folder.substring(0, 1).toUpperCase(Locale.ROOT) + folder.substring(1) + " #" + hex;
    }

    // ---------------------------------------------------------------- decoding

    public static Features decode(String key) {
        String folder = folder(key);
        long seed = seed(key);
        JsonObject clans = tables().getAsJsonObject("clans");
        JsonObject table = clans.has(folder) ? clans.getAsJsonObject(folder) : clans.getAsJsonObject("generic");
        SplitMix rng = new SplitMix(seed);
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : FIELDS) {
            values.put(field, rng.pick(table.getAsJsonArray(field)));
        }
        long family = seed >>> 48;
        if (folder.equals("generic") && family != 0) {
            JsonObject fam = tables().getAsJsonObject("family");
            SplitMix famRng = new SplitMix(family * GOLDEN);
            String famMark = famRng.pick(fam.getAsJsonArray("mark"));
            String famHair = famRng.pick(table.getAsJsonArray("hair_color"));
            if (rng.nextInt(100) < fam.get("mark_chance").getAsInt()) {
                values.put("mark", famMark);
            }
            if (rng.nextInt(100) < fam.get("hair_chance").getAsInt()) {
                values.put("hair_color", famHair);
            }
        }
        return new Features(folder, values);
    }

    /** Accessories drawn in gray and coloured with {@code accessory_color} (the rest have fixed colours). */
    public static boolean tintedAccessory(String accessory) {
        for (JsonElement e : tables().getAsJsonArray("tinted_accessories")) {
            if (e.getAsString().equals(accessory)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized JsonObject tables() {
        if (tables == null) {
            try (InputStream in = NinjaLook.class.getResourceAsStream(TABLES)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + TABLES);
                }
                tables = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                Noirm.LOGGER.error("Could not read ninja look tables", e);
                tables = new JsonObject();
                tables.add("clans", new JsonObject());
                tables.add("tinted_accessories", new JsonArray());
            }
        }
        return tables;
    }

    /** SplitMix64: tiny, fast and identical in Java and Python. */
    private static final class SplitMix {
        private long state;

        SplitMix(long seed) {
            state = seed;
        }

        long next() {
            state += GOLDEN;
            long z = state;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        int nextInt(int n) {
            return (int) ((next() >>> 1) % n);
        }

        String pick(JsonArray table) {
            if (table == null || table.isEmpty()) {
                return "";
            }
            int total = 0;
            for (JsonElement e : table) {
                total += e.getAsJsonArray().get(1).getAsInt();
            }
            int r = nextInt(Math.max(1, total));
            for (JsonElement e : table) {
                JsonArray entry = e.getAsJsonArray();
                r -= entry.get(1).getAsInt();
                if (r < 0) {
                    return entry.get(0).getAsString();
                }
            }
            return table.get(table.size() - 1).getAsJsonArray().get(0).getAsString();
        }
    }
}

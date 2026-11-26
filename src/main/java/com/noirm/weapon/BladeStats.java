package com.noirm.weapon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.clan.Nature;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The numbers of the v0.13 blades ({@link NinjaBlades}), read from
 * {@code data/noirm/weapons/stats.json} when the game starts (items are made before data packs load,
 * so this file is read from the mod itself, and a change needs a restart, not {@code /reload}).
 * {@code tools/weapongen/gen_moves.py} writes it; editing the JSON by hand works too.
 *
 * @param moveset        the combat moveset it fights with ({@link Moveset#key()})
 * @param damage         the tooltip's attack damage (a fist is 1)
 * @param speed          swings a second (a fist is 4)
 * @param reach          extra reach in blocks
 * @param durability     uses before it breaks
 * @param enchantability like a tier's (iron 14, gold 22)
 * @param trait          what it does beyond cutting (see {@link Trait})
 * @param flow           its chakra flow, or null: what it does when the wielder has that nature
 */
public record BladeStats(Moveset moveset, float damage, float speed, double reach, int durability, int enchantability,
                         Trait trait, Flow flow) {

    /** The path inside the mod. */
    public static final String RESOURCE = "/data/noirm/weapons/stats.json";

    /** What a blade does beyond cutting. */
    public enum Trait {
        NONE,
        /** Tantō: a hit in the back (the target faces away) cuts much deeper. */
        BACKSTAB,
        /** Tonfa: blunt blows crush the guard (extra posture damage). */
        CRUSH,
        /** Tekko-kagi: rakes leave the target bleeding for a few seconds. */
        BLEED,
        /** Tessen: swats arrows and thrown weapons out of the air while attacking or guarding. */
        DEFLECT,
        /** Nodachi: every hit pushes the target back hard (it's a lot of steel). */
        CLEAVE
    }

    /**
     * A chakra flow: when the wielder has {@code nature}, their chakra runs into the blade (it glows)
     * and every hit costs {@code chakra} (players only; ninjas pay nothing) for a bonus.
     *
     * @param damage       multiplies the hit's damage
     * @param stun         ticks a hit numbs the target (lightning)
     * @param bonus        extra effect strength: wind lengthens bleeding by this many seconds
     * @param burn         seconds a hit sets the target alight (fire); enemies right next to it catch too
     * @param reach        extra vanilla reach (blocks) while the chakra flows (wind)
     * @param pierce       blocks a wind lance carries on past the target, cutting whoever stands there
     * @param posture      extra posture damage a hit (earth)
     * @param slow         ticks of Slowness II a hit leaves (earth)
     * @param backstabStun ticks a hit in the back numbs the target (on top of {@code stun})
     */
    public record Flow(Nature nature, float damage, float chakra, int stun, float bonus, int burn, float reach, float pierce,
                       float posture, int slow, int backstabStun) {}

    private static Map<String, BladeStats> all;

    /** The stats of blade {@code id} (e.g. {@code katana}); throws if the file doesn't have it. */
    public static synchronized BladeStats get(String id) {
        if (all == null) {
            all = load();
        }
        BladeStats stats = all.get(id);
        if (stats == null) {
            throw new IllegalStateException("No stats for blade '" + id + "' in " + RESOURCE);
        }
        return stats;
    }

    private static Map<String, BladeStats> load() {
        Map<String, BladeStats> out = new HashMap<>();
        try (InputStream in = BladeStats.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : root.entrySet()) {
                JsonObject o = entry.getValue().getAsJsonObject();
                Moveset moveset = Moveset.byKey(o.get("moveset").getAsString());
                if (moveset == null) {
                    throw new IllegalStateException("Unknown moveset for " + entry.getKey());
                }
                Flow flow = null;
                if (o.has("flow")) {
                    JsonObject f = o.getAsJsonObject("flow");
                    flow = new Flow(Nature.valueOf(f.get("nature").getAsString().toUpperCase(Locale.ROOT)),
                            num(f, "damage", 1f), num(f, "chakra", 0f), (int) num(f, "stun", 0f), num(f, "bonus", 0f),
                            (int) num(f, "burn", 0f), num(f, "reach", 0f), num(f, "pierce", 0f), num(f, "posture", 0f),
                            (int) num(f, "slow", 0f), (int) num(f, "backstab_stun", 0f));
                }
                out.put(entry.getKey(), new BladeStats(moveset, num(o, "damage", 1f), num(o, "speed", 1.6f), num(o, "reach", 0f),
                        (int) num(o, "durability", 250f), (int) num(o, "enchantability", 14f),
                        o.has("trait") ? Trait.valueOf(o.get("trait").getAsString().toUpperCase(Locale.ROOT)) : Trait.NONE, flow));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Couldn't read " + RESOURCE, e);
        }
        return out;
    }

    private static float num(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }
}

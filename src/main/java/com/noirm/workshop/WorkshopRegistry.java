package com.noirm.workshop;

import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuSeals;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every creation the game currently knows of, as a normal jutsu: the design, the definition built
 * from it and its seals. Filled when player data loads, when a jutsu is made or learned, when a crew
 * mate who knows one loads, and on the client from the synced creations. {@code JutsuData.get} and
 * {@code JutsuSeals.get} look here for {@code noirm:workshop/…} ids. Not data-pack content: NPC jutsu
 * sets, the jutsu catalog and random scrolls never see these.
 */
public final class WorkshopRegistry {
    public record Entry(JutsuDesign design, JutsuDefinition definition, JutsuSeals.Sequence seals) {}

    private static final Map<ResourceLocation, Entry> ENTRIES = new ConcurrentHashMap<>();

    private WorkshopRegistry() {}

    /** Adds (or refreshes) a design. */
    public static void register(JutsuDesign design) {
        if (design == null) {
            return;
        }
        Entry old = ENTRIES.get(design.jutsuId());
        if (old != null && old.design().equals(design)) {
            return;
        }
        ENTRIES.put(design.jutsuId(), new Entry(design, Workshop.definition(design), Workshop.sequence(design)));
    }

    public static Entry get(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static JutsuDesign design(ResourceLocation id) {
        Entry e = get(id);
        return e == null ? null : e.design();
    }

    public static JutsuDefinition definition(ResourceLocation id) {
        Entry e = get(id);
        return e == null ? null : e.definition();
    }

    public static JutsuSeals.Sequence seals(ResourceLocation id) {
        Entry e = get(id);
        return e == null ? null : e.seals();
    }

    /** The creation's name, or null if it isn't one. */
    public static String name(ResourceLocation id) {
        if (!JutsuDesign.isCreation(id)) {
            return null;
        }
        JutsuDesign d = design(id);
        return d == null ? null : d.name();
    }

    public static String nameOr(ResourceLocation id, String fallback) {
        String n = name(id);
        return n == null ? fallback : n;
    }
}

package com.noirm.client;

import com.noirm.network.JutsuCatalogPacket;
import com.noirm.workshop.JutsuDesign;
import com.noirm.workshop.Workshop;

import java.util.ArrayList;
import java.util.List;

/**
 * Every jutsu that exists, as the server last described it (for the jutsu screen), plus the local
 * player's workshop creations (built from their synced designs). Free of client-only imports so the
 * packet handler can reference it safely.
 */
public final class ClientJutsuCatalog {
    private static volatile List<JutsuCatalogPacket.Entry> entries = List.of();
    private static volatile List<JutsuCatalogPacket.Entry> creations = List.of();
    private static volatile List<JutsuCatalogPacket.Entry> merged = List.of();

    private ClientJutsuCatalog() {}

    public static void set(List<JutsuCatalogPacket.Entry> list) {
        entries = List.copyOf(list);
        merge();
    }

    /** The local player's creations, as catalog entries (they show in the J screen like any jutsu). */
    public static void setCreations(List<JutsuDesign> designs) {
        List<JutsuCatalogPacket.Entry> list = new ArrayList<>();
        for (JutsuDesign d : designs) {
            Workshop.Stats s = Workshop.stats(d);
            list.add(new JutsuCatalogPacket.Entry(d.jutsuId(), d.nature().ordinal(), s.rank(), s.cast().ordinal(), s.behavior(), s.cost(),
                    s.cooldown(), "", 0, "", false));
        }
        if (!list.equals(creations)) {
            creations = List.copyOf(list);
            merge();
        }
    }

    private static void merge() {
        List<JutsuCatalogPacket.Entry> all = new ArrayList<>(entries);
        all.addAll(creations);
        merged = List.copyOf(all);
    }

    public static List<JutsuCatalogPacket.Entry> all() {
        return merged;
    }

    public static JutsuCatalogPacket.Entry get(String id) {
        for (JutsuCatalogPacket.Entry entry : merged) {
            if (entry.id().toString().equals(id)) {
                return entry;
            }
        }
        return null;
    }
}

package com.noirm.client;

import com.noirm.network.JutsuCatalogPacket;

import java.util.List;

/**
 * Every jutsu that exists, as the server last described it (for the jutsu screen). Free of
 * client-only imports so the packet handler can reference it safely.
 */
public final class ClientJutsuCatalog {
    private static volatile List<JutsuCatalogPacket.Entry> entries = List.of();

    private ClientJutsuCatalog() {}

    public static void set(List<JutsuCatalogPacket.Entry> list) {
        entries = List.copyOf(list);
    }

    public static List<JutsuCatalogPacket.Entry> all() {
        return entries;
    }

    public static JutsuCatalogPacket.Entry get(String id) {
        for (JutsuCatalogPacket.Entry entry : entries) {
            if (entry.id().toString().equals(id)) {
                return entry;
            }
        }
        return null;
    }
}

package com.noirm.bounty;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's copy of the classes the server told it about (players and ninjas it can see, and
 * the local player), by entity id. Deliberately free of client-only imports so the packet handler
 * can reference it safely.
 */
public final class ClientClasses {
    private static final Map<Integer, Integer> CODES = new HashMap<>();

    private ClientClasses() {}

    static void set(int entityId, int code) {
        CODES.put(entityId, code);
    }

    /** The class code of entity {@code entityId}, or -1 if unknown. */
    public static int get(int entityId) {
        return CODES.getOrDefault(entityId, -1);
    }

    public static void reset() {
        CODES.clear();
    }
}

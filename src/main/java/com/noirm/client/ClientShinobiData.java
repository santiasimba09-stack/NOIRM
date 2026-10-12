package com.noirm.client;

import com.noirm.network.SyncShinobiDataPacket;

/**
 * The client's copy of the local player's chakra and stamina, as last sent by the server.
 * Only the HUD reads it; gameplay decisions always use the server's {@code ShinobiData}.
 *
 * <p>Deliberately free of client-only imports so the packet handler can reference it safely.
 */
public final class ClientShinobiData {
    private static volatile SyncShinobiDataPacket latest;

    private ClientShinobiData() {}

    public static void update(SyncShinobiDataPacket packet) {
        latest = packet;
    }

    /** The latest values, or null before the server has sent any (e.g. on the title screen). */
    public static SyncShinobiDataPacket get() {
        return latest;
    }

    public static void reset() {
        latest = null;
    }
}

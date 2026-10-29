package com.noirm.client;

import com.noirm.mission.CareerPackets;

/**
 * The client's copy of the local player's career (ryo, rank, mission tracker) for the HUD and
 * the inventory. Free of client-only imports so the packet handler can reference it safely.
 */
public final class ClientCareer {
    private static volatile CareerPackets.Sync latest;

    private ClientCareer() {}

    public static void update(CareerPackets.Sync sync) {
        latest = sync;
    }

    /** The latest copy, or null before the server sent one. */
    public static CareerPackets.Sync get() {
        return latest;
    }

    public static void reset() {
        latest = null;
    }
}

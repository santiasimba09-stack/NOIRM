package com.noirm.pills;

import net.minecraft.resources.ResourceLocation;

/**
 * The client's copy of its own foods-and-pills state (from {@link PillPackets.Sync}), for the HUD,
 * tooltips and the apothecary screen. Plain data (no client classes), so items can read it too;
 * timers count down from the moment the copy arrived.
 */
public final class PillClientState {
    private static PillPackets.Sync last;
    private static long receivedAt;
    private static PillPackets.Station station;

    private PillClientState() {}

    static void receive(PillPackets.Sync sync) {
        last = sync;
        receivedAt = System.currentTimeMillis();
    }

    static void receive(PillPackets.Station rows) {
        station = rows;
    }

    public static PillPackets.Sync get() {
        return last;
    }

    /** The apothecary's recipe rows, as the server last sent them (null before the first). */
    public static PillPackets.Station station() {
        return station;
    }

    /** Ticks since the copy arrived. */
    public static int ticksSince() {
        return (int) Math.min(Integer.MAX_VALUE, (System.currentTimeMillis() - receivedAt) / 50L);
    }

    /** A pill's cooldown left (ticks), as best the client knows. */
    public static int cooldown(ResourceLocation pill) {
        if (last == null) {
            return 0;
        }
        for (PillPackets.Timer t : last.cooldowns()) {
            if (t.source().equals(pill)) {
                return Math.max(0, t.left() - ticksSince());
            }
        }
        return 0;
    }

    public static void reset() {
        last = null;
        station = null;
    }
}

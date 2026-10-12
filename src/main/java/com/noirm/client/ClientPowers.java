package com.noirm.client;

import com.noirm.network.PowerPackets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of the local player's powers (for the HUD and the jutsu screen), plus how
 * other players' powers look (eyes, cloak). Warnings are drawn by {@link EyeEffects}.
 */
public final class ClientPowers {
    private static PowerPackets.SyncPowers latest;
    /** Client ticks since {@link #latest} arrived, to count cooldowns down smoothly. */
    private static int ticksSinceSync;
    private static final Map<Integer, PowerPackets.Visuals> VISUALS = new HashMap<>();

    private ClientPowers() {}

    public static PowerPackets.SyncPowers get() {
        return latest;
    }

    public static int ticksSinceSync() {
        return ticksSinceSync;
    }

    /** Remaining cooldown of a loadout slot, counting down between syncs. */
    public static int cooldown(int slot) {
        if (latest == null || slot >= latest.cooldowns().size()) {
            return 0;
        }
        return Math.max(0, latest.cooldowns().get(slot) - ticksSinceSync);
    }

    public static PowerPackets.JutsuEntry entry(String id) {
        if (latest == null || id.isEmpty()) {
            return null;
        }
        return latest.learned().stream().filter(e -> e.id().toString().equals(id)).findFirst().orElse(null);
    }

    public static List<PowerPackets.JutsuEntry> learned() {
        return latest == null ? List.of() : latest.learned();
    }

    public static int eyeStage(int entityId) {
        PowerPackets.Visuals visuals = VISUALS.get(entityId);
        return visuals == null ? 0 : visuals.sharinganStage();
    }

    public static boolean byakugan(int entityId) {
        PowerPackets.Visuals visuals = VISUALS.get(entityId);
        return visuals != null && visuals.byakugan();
    }

    /** Is the local player's Byakugan open? */
    public static boolean localByakuganActive() {
        return latest != null && latest.byakugan() && latest.eyesActive() && latest.sharinganStage() == 0;
    }

    static void tick() {
        ticksSinceSync++;
        EyeEffects.tick();
    }

    static void reset() {
        latest = null;
        VISUALS.clear();
        EyeEffects.reset();
    }

    public static void sync(PowerPackets.SyncPowers packet) {
        if (latest != null && !latest.eyesActive() && packet.eyesActive()) {
            EyeEffects.onActivated(packet.sharinganStage() > 0);
        }
        latest = packet;
        ticksSinceSync = 0;
    }

    public static void visuals(PowerPackets.Visuals packet) {
        VISUALS.put(packet.entityId(), packet);
    }

    public static void precognition(PowerPackets.Precognition packet) {
        EyeEffects.onWarning(packet);
    }
}

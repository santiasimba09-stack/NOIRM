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

    /** The Mangekyō look of a player (0 = none; see dojutsu/Mangekyo#look). */
    public static int mangekyoLook(int entityId) {
        PowerPackets.Visuals visuals = VISUALS.get(entityId);
        return visuals == null ? 0 : visuals.mangekyoLook();
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
        MangekyoEffects.tick();
    }

    static void reset() {
        latest = null;
        VISUALS.clear();
        EyeEffects.reset();
        MangekyoEffects.reset();
    }

    public static void sync(PowerPackets.SyncPowers packet) {
        if (latest != null && (packet.mangekyo() && !latest.mangekyo() || packet.eternal() && !latest.eternal())) {
            MangekyoEffects.onAwakened(packet.mangekyoPattern(), packet.eternal() ? packet.mangekyoDonorPattern() : -1);
        } else if (latest != null && packet.sharinganStage() > latest.sharinganStage()) {
            EyeEffects.onTomoe(packet.sharinganStage()); // awakened, or a new tomoe: spin that many
        } else if (latest != null && !latest.eyesActive() && packet.eyesActive()) {
            EyeEffects.onActivated(packet.sharinganStage() > 0);
        }
        int hintBefore = latest == null ? 0 : com.noirm.dojutsu.SharinganAwakening.hintTier(latest.sharinganPressure());
        int hintNow = com.noirm.dojutsu.SharinganAwakening.hintTier(packet.sharinganPressure());
        if (latest != null && hintNow > hintBefore) {
            EyeEffects.onFlicker(hintNow); // a dormant Sharingan stirring
        }
        latest = packet;
        ticksSinceSync = 0;
        // Workshop creations: known to the client as jutsu (names, the J screen, the loadout).
        packet.creations().forEach(com.noirm.workshop.WorkshopRegistry::register);
        ClientJutsuCatalog.setCreations(packet.creations());
    }

    public static void visuals(PowerPackets.Visuals packet) {
        VISUALS.put(packet.entityId(), packet);
    }

    /** Closed chakra points per entity id: {closed, game time the info expires, game time the seal ends}. */
    private static final java.util.Map<Integer, long[]> TENKETSU = new java.util.HashMap<>();

    public static void tenketsu(PowerPackets.Tenketsu packet) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        TENKETSU.put(packet.entityId(), new long[]{packet.closed(), now + com.noirm.dojutsu.Byakugan.POINTS_RESET_TICKS, now + packet.sealedTicks()});
        if (TENKETSU.size() > 256) {
            TENKETSU.entrySet().removeIf(e -> e.getValue()[1] < now && e.getValue()[2] < now);
        }
    }

    /** Share of {@code entityId}'s chakra points closed, 0..1 (1 while sealed), as last heard. */
    public static float closedShare(int entityId, long now) {
        long[] info = TENKETSU.get(entityId);
        if (info == null) {
            return 0f;
        }
        if (now < info[2]) {
            return 1f;
        }
        return now > info[1] ? 0f : Math.min(1f, info[0] / (float) com.noirm.dojutsu.Byakugan.SEAL_POINTS);
    }

    public static void precognition(PowerPackets.Precognition packet) {
        EyeEffects.onWarning(packet);
    }
}

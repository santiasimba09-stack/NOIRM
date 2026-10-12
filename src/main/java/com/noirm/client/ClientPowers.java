package com.noirm.client;

import com.noirm.network.PowerPackets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of the local player's powers (for the HUD and the jutsu screen), plus how
 * other players' powers look (eyes, cloak), plus the precognition warning.
 */
public final class ClientPowers {
    private static PowerPackets.SyncPowers latest;
    /** Client ticks since {@link #latest} arrived, to count cooldowns down smoothly. */
    private static int ticksSinceSync;
    private static final Map<Integer, PowerPackets.Visuals> VISUALS = new HashMap<>();
    private static int precognitionTicks;
    private static int precognitionTotal;
    private static int precognitionAttacker = -1;

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

    /** 0..1 strength of the precognition warning flash. */
    public static float precognition() {
        return precognitionTotal <= 0 ? 0f : precognitionTicks / (float) precognitionTotal;
    }

    public static int precognitionAttacker() {
        return precognitionTicks > 0 ? precognitionAttacker : -1;
    }

    static void tick() {
        ticksSinceSync++;
        if (precognitionTicks > 0) {
            precognitionTicks--;
        }
    }

    static void reset() {
        latest = null;
        VISUALS.clear();
        precognitionTicks = 0;
    }

    public static void sync(PowerPackets.SyncPowers packet) {
        latest = packet;
        ticksSinceSync = 0;
    }

    public static void visuals(PowerPackets.Visuals packet) {
        VISUALS.put(packet.entityId(), packet);
    }

    public static void precognition(PowerPackets.Precognition packet) {
        precognitionAttacker = packet.attackerId();
        precognitionTotal = Math.max(8, packet.ticks());
        precognitionTicks = precognitionTotal;
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5f, 2f);
        }
    }
}

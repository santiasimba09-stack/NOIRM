package com.noirm.player;

import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Tells everyone who can see a player how their powers look (Sharingan on, chakra cloak), so
 * the eyes and aura render for all viewers, not just the player.
 */
public final class PowerVisuals {
    private PowerVisuals() {}

    public static PowerPackets.Visuals of(Player player) {
        return ShinobiCapability.get(player)
                .map(d -> new PowerPackets.Visuals(player.getId(), d.eyes().activeStage(), d.biju().cloakStage()))
                .orElse(new PowerPackets.Visuals(player.getId(), 0, 0));
    }

    public static void broadcast(ServerPlayer player) {
        NoirmNetwork.sendToTrackingAndSelf(player, of(player));
    }

    /** When {@code viewer} starts seeing {@code player}, tell them how they look right now. */
    public static void sendTo(ServerPlayer viewer, Player player) {
        NoirmNetwork.sendToPlayer(viewer, of(player));
    }
}

package com.noirm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** The local player's combat state, as the animation engine reads it. */
public final class ClientPlayerState {
    private ClientPlayerState() {}

    public static boolean isLocal(Player player) {
        return player == Minecraft.getInstance().player;
    }

    public static boolean combatMode() {
        return ClientCombat.isCombatMode();
    }

    public static boolean guarding() {
        return ClientCombat.isGuarding();
    }

    /** The stance the server picked for the local player's sub-stance, or null. */
    public static net.minecraft.resources.ResourceLocation stance() {
        return ClientCombat.stance();
    }

    /** Ticks a sub-stance switch snaps over. */
    public static int stanceSwitch() {
        return ClientCombat.stanceSwitch();
    }

    /** The player's style id or path (e.g. {@code brawler}), or empty. */
    public static String style() {
        var data = ClientShinobiData.get();
        return data == null || data.style() == null ? "" : data.style();
    }
}

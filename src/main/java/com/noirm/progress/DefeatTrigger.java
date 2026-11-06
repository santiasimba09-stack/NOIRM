package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:defeat}. Events: {@code defeat} (a ninja was killed by you or yielded to you;
 * {@code min}: their class code, 0 = D … 4 = S, 5 = a legend), {@code bounty} (a bingo-book bounty
 * was paid to you; key {@code legend} for a legend's page, {@code rogue} otherwise).
 */
public final class DefeatTrigger extends NoirmTrigger {
    public static final DefeatTrigger INSTANCE = new DefeatTrigger();

    private DefeatTrigger() {
        super("defeat");
    }

    public static void defeated(Player player, int classCode) {
        INSTANCE.fire(player, "defeat", null, classCode);
    }

    public static void bounty(Player player, boolean legend) {
        INSTANCE.fire(player, "bounty", legend ? "legend" : "rogue", 1);
    }
}

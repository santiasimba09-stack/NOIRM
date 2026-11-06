package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:beginnings}: a new shinobi's first steps. Events: {@code created} (the character
 * screen is done), {@code handbook} (the Shinobi Handbook was opened), {@code training} (a hit on a
 * training dummy).
 */
public final class BeginningsTrigger extends NoirmTrigger {
    public static final BeginningsTrigger INSTANCE = new BeginningsTrigger();

    private BeginningsTrigger() {
        super("beginnings");
    }

    public static void created(Player player) {
        INSTANCE.fire(player, "created", null, 1);
    }

    public static void handbook(Player player) {
        INSTANCE.fire(player, "handbook", null, 1);
    }

    public static void training(Player player) {
        INSTANCE.fire(player, "training", null, 1);
    }
}

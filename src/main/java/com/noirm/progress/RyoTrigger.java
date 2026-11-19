package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:ryo}: the wallet holds at least {@code min} ryo.
 */
public final class RyoTrigger extends NoirmTrigger {
    public static final RyoTrigger INSTANCE = new RyoTrigger();

    private RyoTrigger() {
        super("ryo");
    }

    public static void balance(Player player, int ryo) {
        INSTANCE.fire(player, "balance", null, ryo);
    }
}

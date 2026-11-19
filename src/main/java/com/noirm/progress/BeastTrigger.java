package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:beast}: events {@code subdued} (you beat a tailed beast down), {@code sealed} (you
 * caught one in a sealing scroll), {@code jinchuriki} (a beast lives in you); {@code min}: its tails.
 */
public final class BeastTrigger extends NoirmTrigger {
    public static final BeastTrigger INSTANCE = new BeastTrigger();

    private BeastTrigger() {
        super("beast");
    }

    public static void subdued(Player player, int tails) {
        INSTANCE.fire(player, "subdued", null, tails);
    }

    public static void sealed(Player player, int tails) {
        INSTANCE.fire(player, "sealed", null, tails);
    }

    public static void jinchuriki(Player player, int tails) {
        INSTANCE.fire(player, "jinchuriki", null, tails);
    }
}

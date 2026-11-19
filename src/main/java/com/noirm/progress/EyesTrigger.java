package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:eyes}: key {@code sharingan} ({@code min}: tomoe), {@code mangekyo}, or
 * {@code byakugan} (opened at least once).
 */
public final class EyesTrigger extends NoirmTrigger {
    public static final EyesTrigger INSTANCE = new EyesTrigger();

    private EyesTrigger() {
        super("eyes");
    }

    public static void sharingan(Player player, int tomoe) {
        INSTANCE.fire(player, "awakened", "sharingan", tomoe);
    }

    public static void mangekyo(Player player) {
        INSTANCE.fire(player, "awakened", "mangekyo", 1);
    }

    public static void byakugan(Player player) {
        INSTANCE.fire(player, "awakened", "byakugan", 1);
    }
}

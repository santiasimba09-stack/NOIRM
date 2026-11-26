package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:jutsu}. Events: {@code cast} (a jutsu was cast; key: its rank letter),
 * {@code learned} (key: the jutsu's rank letter, E–S), {@code mastery} ({@code min}: the highest ★
 * level of any jutsu you know).
 */
public final class JutsuTrigger extends NoirmTrigger {
    public static final JutsuTrigger INSTANCE = new JutsuTrigger();

    private JutsuTrigger() {
        super("jutsu");
    }

    public static void cast(Player player, String rank) {
        INSTANCE.fire(player, "cast", rank, 1);
    }

    public static void learned(Player player, String rank) {
        INSTANCE.fire(player, "learned", rank, 1);
    }

    public static void mastery(Player player, int stars) {
        INSTANCE.fire(player, "mastery", null, stars);
    }
}

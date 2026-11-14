package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:create_jutsu}: you designed a jutsu of your own in the jutsu workshop.
 *
 * <p><b>Placeholder for chakra wave part 2:</b> nothing calls {@link #fire} yet. When the workshop
 * saves a new jutsu, call {@code CreateJutsuTrigger.fire(player, rank)} (rank: the new jutsu's
 * letter, E–S, or null). The advancement {@code noirm:shinobi/jutsu/create_own} waits for it.
 */
public final class CreateJutsuTrigger extends NoirmTrigger {
    public static final CreateJutsuTrigger INSTANCE = new CreateJutsuTrigger();

    private CreateJutsuTrigger() {
        super("create_jutsu");
    }

    public static void fire(Player player, String rank) {
        INSTANCE.fire(player, "created", rank, 1);
    }
}

package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:mission}: missions completed. Event {@code completed}; key: the mission's rank letter
 * (D–S; leave it out for any); {@code min}: how many of that letter.
 */
public final class MissionTrigger extends NoirmTrigger {
    public static final MissionTrigger INSTANCE = new MissionTrigger();

    private MissionTrigger() {
        super("mission");
    }

    public static void completed(Player player, String letter, int count) {
        INSTANCE.fire(player, "completed", letter, count);
    }
}

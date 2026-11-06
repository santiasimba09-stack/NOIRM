package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:sage}: event {@code mode} (Sage Mode entered).
 */
public final class SageTrigger extends NoirmTrigger {
    public static final SageTrigger INSTANCE = new SageTrigger();

    private SageTrigger() {
        super("sage");
    }

    public static void mode(Player player) {
        INSTANCE.fire(player, "mode", null, 1);
    }
}

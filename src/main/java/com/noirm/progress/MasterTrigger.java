package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:master}: you passed a master's test (key: the master's id, e.g. {@code might_guy}).
 */
public final class MasterTrigger extends NoirmTrigger {
    public static final MasterTrigger INSTANCE = new MasterTrigger();

    private MasterTrigger() {
        super("master");
    }

    public static void passed(Player player, String master) {
        INSTANCE.fire(player, "passed", master, 1);
    }
}

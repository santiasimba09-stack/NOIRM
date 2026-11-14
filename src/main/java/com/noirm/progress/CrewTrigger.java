package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:crew}. Events: {@code size} ({@code min}: crew mates following you), {@code taught}
 * (a crew mate finished a nature lesson from you; key: the nature).
 */
public final class CrewTrigger extends NoirmTrigger {
    public static final CrewTrigger INSTANCE = new CrewTrigger();

    private CrewTrigger() {
        super("crew");
    }

    public static void size(Player player, int size) {
        INSTANCE.fire(player, "size", null, size);
    }

    public static void taught(Player player, String nature) {
        INSTANCE.fire(player, "taught", nature, 1);
    }
}

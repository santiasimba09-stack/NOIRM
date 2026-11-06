package com.noirm.progress;

import com.noirm.clan.Nature;
import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:nature}. Events: {@code revealed} (chakra paper showed a hidden nature),
 * {@code drill} (a nature drill was finished; key: the nature), {@code owned} ({@code min}: how many
 * natures you have), {@code combined} ({@code min}: how many kekkei genkai natures you have).
 */
public final class NatureTrigger extends NoirmTrigger {
    public static final NatureTrigger INSTANCE = new NatureTrigger();

    private NatureTrigger() {
        super("nature");
    }

    public static void revealed(Player player) {
        INSTANCE.fire(player, "revealed", null, 1);
    }

    public static void drill(Player player, Nature nature) {
        INSTANCE.fire(player, "drill", nature.getSerializedName(), 1);
    }

    public static void owned(Player player, int count) {
        INSTANCE.fire(player, "owned", null, count);
    }

    public static void combined(Player player, int count) {
        INSTANCE.fire(player, "combined", null, count);
    }
}

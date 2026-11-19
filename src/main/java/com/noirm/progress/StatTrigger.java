package com.noirm.progress;

import com.noirm.training.TrainingSystem;
import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:stat}: a trained stat reached a value. Key: the stat ({@code taijutsu}, {@code speed},
 * {@code vitality}, {@code reserves}, {@code durability}; leave it out for "any"). {@code min}: the value.
 */
public final class StatTrigger extends NoirmTrigger {
    public static final StatTrigger INSTANCE = new StatTrigger();

    private StatTrigger() {
        super("stat");
    }

    public static void reached(Player player, TrainingSystem.Stat stat, float value) {
        INSTANCE.fire(player, "reached", stat.key().toLowerCase(java.util.Locale.ROOT), (int) Math.floor(value));
    }
}

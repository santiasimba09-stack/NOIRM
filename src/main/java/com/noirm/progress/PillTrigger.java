package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:medicine}: foods, pills and the apothecary.
 * <ul>
 *   <li>{@code food} (key = the food's item path): you ate a NOIRM food;</li>
 *   <li>{@code pill} (key = the pill's path): you swallowed a pill;</li>
 *   <li>{@code brewed} (key = the pill's path, value = grade 0 crude … 3 perfect): a brew at the apothecary came out;</li>
 *   <li>{@code pharmacology} (value = the level): checked when it grows;</li>
 *   <li>{@code recovered} (key = the pill's path): its crash wore off;</li>
 *   <li>{@code toxicity} (value = the level): the body's toxicity after a pill.</li>
 * </ul>
 */
public final class PillTrigger extends NoirmTrigger {
    public static final PillTrigger INSTANCE = new PillTrigger();

    private PillTrigger() {
        super("medicine");
    }

    public static void ate(Player player, String food) {
        INSTANCE.fire(player, "food", food, 1);
    }

    public static void swallowed(Player player, String pill) {
        INSTANCE.fire(player, "pill", pill, 1);
    }

    public static void brewed(Player player, String pill, int grade) {
        INSTANCE.fire(player, "brewed", pill, grade);
    }

    public static void pharmacology(Player player, int level) {
        INSTANCE.fire(player, "pharmacology", null, level);
    }

    public static void recovered(Player player, String pill) {
        INSTANCE.fire(player, "recovered", pill, 1);
    }

    public static void toxicity(Player player, int level) {
        INSTANCE.fire(player, "toxicity", null, level);
    }
}

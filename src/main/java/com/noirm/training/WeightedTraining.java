package com.noirm.training;

import com.noirm.player.ShinobiData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Weighted training (wave "clones", 2026-11-26): carry <b>training weights</b> (the item, anywhere in your inventory)
 * and you move slower, but every point of Speed, Taijutsu and Durability XP counts ×{@link #XP_MULTIPLIER}. Take them
 * off for a fight and you feel how fast you really are. Numbers at the top (docs/TUNING.md).
 */
public final class WeightedTraining {
    /** XP multiplier for the body stats while carrying weights. */
    public static final float XP_MULTIPLIER = 1.5f;
    /** Movement while weighed down: −this share. */
    public static final double SLOW = 0.2;

    private static final UUID SLOW_ID = UUID.fromString("7a4c2e19-3b5d-4f6a-8c7e-1d2f3a4b5c6e");

    private WeightedTraining() {}

    /** True if {@code player} carries training weights. */
    public static boolean weighted(Player player) {
        return player.getInventory().hasAnyMatching(stack -> stack.is(com.noirm.registry.NoirmItems.TRAINING_WEIGHTS.get()));
    }

    /** The XP multiplier for {@code stat} (1 unless weighted and a body stat). */
    public static float xpMultiplier(Player player, TrainingSystem.Stat stat) {
        boolean body = stat == TrainingSystem.Stat.SPEED || stat == TrainingSystem.Stat.TAIJUTSU || stat == TrainingSystem.Stat.DURABILITY;
        return body && weighted(player) ? XP_MULTIPLIER : 1f;
    }

    /** Twice a second: keeps the slowdown in line with whether the weights are carried. */
    public static void tick(Player player, ShinobiData data) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean weighted = weighted(player);
        boolean slowed = speed.getModifier(SLOW_ID) != null;
        if (weighted && !slowed) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, "noirm_training_weights", -SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!weighted && slowed) {
            speed.removeModifier(SLOW_ID);
        }
        if (weighted) {
            data.training().setWeightTicks(data.training().weightTicks() + 10);
        }
    }
}

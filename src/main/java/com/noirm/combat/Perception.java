package com.noirm.combat;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The owner's idea: the stronger you are than an opponent, the slower their attacks play out
 * for you. 20% stronger → their wind-ups last 20% longer. Capped so it never becomes absurd.
 */
public final class Perception {
    /** Even a huge gap never slows an attack to less than 1/2.5 of its speed. */
    public static final float MAX_SLOW_FACTOR = 2.5f;

    private Perception() {}

    /** How well a fighter reads combat: the average of speed and taijutsu, times dōjutsu bonuses. */
    public static float perception(LivingEntity entity) {
        return (speed(entity) + taijutsu(entity)) / 2f * ocularMultiplier(entity);
    }

    /**
     * How much longer {@code attacker}'s wind-up lasts against {@code defender}. 1 = normal.
     * Weaker defenders get no slowdown (but are never sped up either).
     */
    public static float slowFactor(LivingEntity defender, LivingEntity attacker) {
        return Mth.clamp(perception(defender) / perception(attacker), 1f, MAX_SLOW_FACTOR);
    }

    /** Unarmed damage scaling from taijutsu: 1.0 at the starting value of 10. */
    public static float damageMultiplier(LivingEntity entity) {
        return Mth.clamp(0.6f + 0.04f * taijutsu(entity), 0.5f, 5f);
    }

    /** Hook for dōjutsu (Sharingan etc.), which will raise perception. Nothing yet. */
    static float ocularMultiplier(LivingEntity entity) {
        return 1f;
    }

    private static float speed(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getSpeed).orElse(ShinobiData.DEFAULT_STAT);
        }
        return entity instanceof CombatStats stats ? stats.combatSpeed() : ShinobiData.DEFAULT_STAT;
    }

    private static float taijutsu(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getTaijutsu).orElse(ShinobiData.DEFAULT_STAT);
        }
        return entity instanceof CombatStats stats ? stats.combatTaijutsu() : ShinobiData.DEFAULT_STAT;
    }
}

package com.noirm.combat;

import com.noirm.awakening.AwakeningSystem;
import com.noirm.biju.BijuSystem;
import com.noirm.clan.ClanTrait;
import com.noirm.dojutsu.Sharingan;
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

    /** How well a fighter reads combat: the average of speed and taijutsu, times eyes and states. */
    public static float perception(LivingEntity entity) {
        return (speed(entity) + taijutsu(entity)) / 2f * ocularMultiplier(entity);
    }

    /** Raw strength from stats alone (no eyes or states): the average of speed and taijutsu. */
    public static float basePower(LivingEntity entity) {
        return (speed(entity) + taijutsu(entity)) / 2f;
    }

    /**
     * How much longer {@code attacker}'s wind-up lasts against {@code defender}. 1 = normal.
     * Weaker defenders get no slowdown (but are never sped up either).
     */
    public static float slowFactor(LivingEntity defender, LivingEntity attacker) {
        return Mth.clamp(perception(defender) / perception(attacker), 1f, MAX_SLOW_FACTOR);
    }

    /**
     * Unarmed damage scaling: 1.0 at taijutsu 10, more while locked in (awakening) or in a
     * tailed beast's cloak.
     */
    public static float damageMultiplier(LivingEntity entity) {
        float base = Mth.clamp(0.6f + 0.04f * taijutsu(entity), 0.5f, 5f);
        return base * AwakeningSystem.damageMultiplier(entity) * BijuSystem.damageMultiplier(entity);
    }

    /**
     * Everything that sharpens perception beyond stats: the active Sharingan (+25% per tomoe),
     * the Keen Eyes clan trait (+10%), being locked in (+30%) and the beast's cloak.
     */
    static float ocularMultiplier(LivingEntity entity) {
        float keenEyes = entity instanceof Player player
                && ShinobiCapability.get(player).map(d -> d.identity().has(ClanTrait.KEEN_EYES)).orElse(false) ? 1.1f : 1f;
        return Sharingan.perceptionMultiplier(entity) * keenEyes
                * AwakeningSystem.perceptionMultiplier(entity) * BijuSystem.perceptionMultiplier(entity);
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

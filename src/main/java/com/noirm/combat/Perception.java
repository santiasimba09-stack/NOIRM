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

    /*
     * Speed → perception (APX 0.1). Movement slows down past speed 35 and stops at 50 (see
     * player.Movement); the speed it no longer takes goes into perception instead:
     *
     *   speed:            10   30   35   40    50    60    100
     *   speed as seen by
     *   perception:       10   30   35   43.5  60.5  70.5  110.5
     *
     * - up to 35: 1 point per point (as before);
     * - 35 to 50: 1 + SPEED_PERCEPTION_BONUS (1.7) per point, the share movement doesn't take;
     * - past 50: SPEED_PERCEPTION_PAST_CAP (1) per point, plus the 25.5 banked from 35–50.
     */
    /** Extra perception per speed point between Movement.GENTLE_UNTIL (35) and Movement.MOVEMENT_CAP_AT (50). */
    public static final float SPEED_PERCEPTION_BONUS = 0.7f;
    /** Perception per speed point past Movement.MOVEMENT_CAP_AT (50). */
    public static final float SPEED_PERCEPTION_PAST_CAP = 1f;

    /** How well a fighter reads combat: the average of speed (on the curve above) and taijutsu, times eyes and states. */
    public static float perception(LivingEntity entity) {
        return (perceptionSpeed(speed(entity)) + taijutsu(entity)) / 2f * ocularMultiplier(entity);
    }

    /** The speed stat as perception sees it (the curve above). */
    public static float perceptionSpeed(float speed) {
        float gentle = com.noirm.player.Movement.GENTLE_UNTIL;
        float cap = com.noirm.player.Movement.MOVEMENT_CAP_AT;
        if (speed <= gentle) {
            return speed;
        }
        if (speed <= cap) {
            return speed + (speed - gentle) * SPEED_PERCEPTION_BONUS;
        }
        return cap + (cap - gentle) * SPEED_PERCEPTION_BONUS + (speed - cap) * SPEED_PERCEPTION_PAST_CAP;
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
        return statPower(taijutsu(entity)) * stateMultiplier(entity);
    }

    /**
     * Everything that makes a hit harder besides taijutsu: devotion (crew mates), being locked in,
     * the beast's cloak, an active mode. Weapons get this too (taijutsu only scales the body).
     */
    public static float stateMultiplier(LivingEntity entity) {
        float morale = entity instanceof com.noirm.entity.SparringNinja ninja ? com.noirm.ally.AllyMind.morale(ninja) : 1f;
        return morale * AwakeningSystem.damageMultiplier(entity) * BijuSystem.damageMultiplier(entity)
                * com.noirm.jutsu.ModeSystem.damageMultiplier(entity) * com.noirm.senjutsu.SageMode.damageMultiplier(entity);
    }

    /**
     * Everything that sharpens perception beyond stats: the active Sharingan (+25% per tomoe),
     * the Keen Eyes clan trait (+10%), being locked in (+30%) and the beast's cloak.
     */
    static float ocularMultiplier(LivingEntity entity) {
        float keenEyes = entity instanceof Player player
                && ShinobiCapability.get(player).map(d -> d.identity().has(ClanTrait.KEEN_EYES)).orElse(false) ? 1.1f : 1f;
        return Sharingan.perceptionMultiplier(entity) * keenEyes
                * com.noirm.jutsu.ModeSystem.perceptionMultiplier(entity)
                * AwakeningSystem.perceptionMultiplier(entity) * BijuSystem.perceptionMultiplier(entity)
                * com.noirm.senjutsu.SageMode.perceptionMultiplier(entity);
    }

    /**
     * The power scale everything shares: proportional to the stat, 1 at the starting 10. A
     * taijutsu-100 fighter hits 10× harder than a beginner; a legend at 1000, 100×.
     */
    public static float statPower(float stat) {
        return Math.max(0.5f, stat / ShinobiData.DEFAULT_STAT);
    }

    /**
     * How much of every hit a body shrugs off (damage is divided by this): the square root of
     * the power of its vitality (players) or overall strength (ninjas, beasts). With the health
     * vitality gives, toughness keeps pace with strength, so equals still fight equal fights.
     */
    public static float durability(LivingEntity entity) {
        float stat;
        if (entity instanceof Player player) {
            stat = ShinobiCapability.get(player).map(ShinobiData::getVitality).orElse(ShinobiData.DEFAULT_STAT);
        } else if (entity instanceof CombatStats stats) {
            stat = stats.combatVitality();
        } else {
            return 1f;
        }
        return (float) Math.sqrt(Math.max(1f, stat / ShinobiData.DEFAULT_STAT));
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

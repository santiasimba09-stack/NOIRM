package com.noirm.hiraishin;

import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hook for clans born to sealing (an Uzumaki- or Namikaze-like line): a clan registers a
 * {@link Bonus} once, at load time, and every member gets it with the space-time formula.
 * <pre>
 *     HiraishinAffinity.register("uzumaki", new HiraishinAffinity.Bonus(10f, 2, 0.85f, 0.85f));
 * </pre>
 * Nothing is registered by default: the clan system decides who gets what.
 */
public final class HiraishinAffinity {
    /**
     * @param statDiscount       how many points less CTL and Reserves the formula and the Flying Raijin ask
     * @param extraMarks         marks you can hold above the normal cap
     * @param costMultiplier     chakra per jump (0.85 = 15% cheaper)
     * @param cooldownMultiplier the wait between jumps
     */
    public record Bonus(float statDiscount, int extraMarks, float costMultiplier, float cooldownMultiplier) {
        public static final Bonus NONE = new Bonus(0f, 0, 1f, 1f);
    }

    private static final Map<String, Bonus> CLANS = new ConcurrentHashMap<>();

    private HiraishinAffinity() {}

    /** Gives every member of {@code clanId} this bonus (replacing an earlier one). */
    public static void register(String clanId, Bonus bonus) {
        CLANS.put(clanId, bonus);
    }

    public static void unregister(String clanId) {
        CLANS.remove(clanId);
    }

    /** {@code entity}'s bonus from their clan, or {@link Bonus#NONE}. */
    public static Bonus of(LivingEntity entity) {
        String clan = null;
        if (entity instanceof Player player) {
            clan = ShinobiCapability.get(player).map(d -> d.identity().clanId()).orElse(null);
        } else if (entity instanceof SparringNinja ninja) {
            clan = ninja.clanId();
        }
        return clan == null ? Bonus.NONE : CLANS.getOrDefault(clan, Bonus.NONE);
    }
}

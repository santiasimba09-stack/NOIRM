package com.noirm.training;

import com.noirm.Noirm;
import com.noirm.combat.CombatStats;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * DUR, durability (owner, APX 0.1): a hardened body shrugs off a share of every hit.
 *
 * <pre>
 *   DUR:        10    20    30    40    60    100   150   200   cap
 *   negated:    0%    11%   20%   27%   38%   50%   56%   59%   60%
 * </pre>
 *
 * The share is {@code 60% × (1 − e^(−(DUR − 10) / 50))}: quick at first, then slowly closing in on
 * {@link #MAX_NEGATION} (60%) toward the stat cap ({@code ShinobiData.MAX_STAT}).
 *
 * <p><b>Where it applies:</b> last, on the damage that is left after everything else: guard and
 * modes ({@code LivingHurtEvent}), then armor, enchantments and absorption hearts (vanilla), then
 * DUR ({@code LivingDamageEvent}, high priority so health-based reactions see the real amount).
 * Because it multiplies what is left (it always keeps at least 40%) and never takes a hit below
 * {@link #MIN_DAMAGE}, stacking it with armor and guard can never make anyone immune. It never
 * touches {@code /kill}, the void or starvation.
 *
 * <p><b>Training:</b> taking hits in real fights: {@link #XP_PER_PERCENT} per percent of your max
 * health the hit was worth (before DUR), times how much the attacker teaches (stronger foes
 * more). Damage with no attacker (falls, fire) is a drill and stops teaching at 30.
 *
 * <p><b>NPCs:</b> ninjas have DUR equal to their strength (like their vitality); crew mates then
 * train it themselves ({@code SparringNinja.combatDurability}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Durability {
    /** The most DUR can negate, reached toward the stat cap. */
    public static final float MAX_NEGATION = 0.6f;
    /** DUR points above 10 per e-fold: ~20% at 30, ~38% at 60, ~50% at 100. */
    public static final float SCALE = 50f;
    /** DUR never takes a hit below this (unless it was already smaller). */
    public static final float MIN_DAMAGE = 0.5f;
    /** DUR XP per percent of max health a real hit was worth. */
    public static final float XP_PER_PERCENT = 0.6f;

    private Durability() {}

    /** The share of damage negated at {@code durability} (0 at 10 or less). */
    public static float negation(float durability) {
        if (durability <= ShinobiData.DEFAULT_STAT) {
            return 0f;
        }
        return MAX_NEGATION * (1f - (float) Math.exp(-(durability - ShinobiData.DEFAULT_STAT) / SCALE));
    }

    /** {@code entity}'s DUR: the player's stat, a ninja's {@code combatDurability}, else 10. */
    public static float of(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getDurability).orElse(ShinobiData.DEFAULT_STAT);
        }
        return entity instanceof CombatStats stats ? stats.combatDurability() : ShinobiData.DEFAULT_STAT;
    }

    /** Damage DUR never touches: {@code /kill} and the void (they bypass invulnerability), starvation. */
    public static boolean exempt(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypes.STARVE);
    }

    /** What is left of {@code amount} after {@code entity}'s DUR. */
    public static float reduce(LivingEntity entity, DamageSource source, float amount) {
        if (amount <= 0f || exempt(source)) {
            return amount;
        }
        float negation = negation(of(entity));
        if (negation <= 0f) {
            return amount;
        }
        return Math.max(Math.min(amount, MIN_DAMAGE), amount * (1f - negation));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        float amount = event.getAmount();
        if (victim.level().isClientSide() || event.isCanceled() || amount <= 0f) {
            return;
        }
        event.setAmount(reduce(victim, event.getSource(), amount));
        if (victim instanceof Player player && !exempt(event.getSource())) {
            onHitTaken(player, event.getSource().getEntity(), amount);
        }
    }

    /**
     * A hit worth {@code amount} (before DUR) taken from {@code attacker} (null: no attacker).
     * Trains DUR and logs the damage for kill XP. Public for tests.
     */
    public static void onHitTaken(Player player, Entity attacker, float amount) {
        if (player.level().isClientSide() || amount <= 0f || player.getAbilities().invulnerable) {
            return;
        }
        float percent = 100f * amount / Math.max(1f, player.getMaxHealth());
        boolean real = attacker != null && attacker != player;
        float factor = real ? TrainingSystem.opponentFactor(player, attacker, TrainingSystem.Stat.DURABILITY)
                : TrainingSystem.drillFactor(player, TrainingSystem.Stat.DURABILITY);
        TrainingSystem.grant(player, TrainingSystem.Stat.DURABILITY, percent * XP_PER_PERCENT * factor);
        if (real && factor > 0f && !(attacker instanceof com.noirm.entity.TrainingDummy)) {
            ShinobiCapability.get(player).ifPresent(d -> d.training().fight().damaged(player, percent));
        }
    }
}

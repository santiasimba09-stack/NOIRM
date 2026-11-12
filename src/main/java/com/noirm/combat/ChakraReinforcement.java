package com.noirm.combat;

import com.noirm.config.NoirmConfig;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.Durability;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Chakra reinforcement and vanilla armour against ninja attacks (owner, 2026-11-12: "it should mostly
 * ignore the vanilla armour, although I want you to be able to reinforce your body and armour with
 * chakra, so the more chakra and chakra control you have, the more damage you deal and the less you take").
 *
 * <p><b>Ninja attacks</b> are every hit NOIRM itself deals: combat-mode strikes (fists and weapons),
 * thrown ninja weapons, blade-flow cuts, every jutsu and the fire a jutsu lights. Call sites wrap their
 * {@code hurt} in {@link #hurt}, so the rules below know which hit is a ninja attack. Vanilla mobs
 * (zombies, skeletons, creepers…) never are: vanilla armour counts in full against them.
 *
 * <p><b>Vanilla armour</b> keeps only {@link #ARMOUR_SHARE} (25%, config {@code difficulty.ninjaArmour})
 * of what it would normally stop against a ninja attack; enchantments (Protection, Fire Protection…)
 * too. Diamond with Protection IV, which stops about 90% of a zombie's hit, stops about 22% of a
 * ninja's. The Resistance potion still counts in full (it's not armour). The sums run in
 * {@code mixin.LivingEntityArmourMixin}.
 *
 * <p><b>Reinforcement</b> comes from your <b>chakra stat</b>: the average of Reserves and CTL
 * ({@link #chakraStat}). It grows the same way Durability does, quick at first, then closing in on a
 * ceiling ({@link #strength}):
 * <pre>
 *   chakra stat (Reserves+CTL)/2:   10    20    30    40    60    80    120   200
 *   your hits (melee, weapons):     +0%   +11%  +20%  +26%  +36%  +41%  +47%  +50%
 *   ninja attacks you take:         −0%   −8%   −14%  −18%  −25%  −29%  −33%  −35%
 * </pre>
 * The damage-taken part stacks with Durability (it multiplies what DUR leaves), but the two together
 * never negate more than {@link #COMBINED_CAP} (70%) of a hit; DUR alone stops at 60%.
 *
 * <p><b>Chakra:</b> it only works while you have the chakra for it, and it is paid hit by hit (not
 * over time, so walking around costs nothing): {@link #STRIKE_COST} per reinforced strike you land,
 * and {@link #GUARD_COST_PER_DAMAGE} per point of damage it stops (at least {@link #MIN_GUARD_COST}).
 * Good chakra control makes both cheaper ({@link #costFactor}: ×1 at CTL 10, ×0.5 at CTL 40, at
 * least ×{@link #MIN_COST_FACTOR}). Out of chakra, the hit simply isn't reinforced.
 *
 * <p><b>NPC ninjas</b> follow the same rules from their own stats: Reserves is their reserves stat,
 * and their CTL is {@link CombatStats#combatControl} (ninjas don't train control the way players do:
 * {@link #NPC_CONTROL_SHARE} of their reserves above 10). They pay from their own chakra pool.
 *
 * <p><b>Look:</b> a reinforced strike leaves a faint chakra-blue sheen at the fist; a reinforced body
 * shrugs a hit off with a thin shimmer over the skin. The HUD shows "Chakra armour" next to the combat
 * line while you have the chakra for it.
 */
public final class ChakraReinforcement {
    /** Chakra-stat points above 10 per e-fold: ~22% of the ceiling at 20, ~53% at 40, ~83% at 80. */
    public static final float SCALE = 40f;
    /** The most reinforcement adds to your melee and weapon hits (+50%). */
    public static final float MAX_DAMAGE_BONUS = 0.5f;
    /** The most reinforcement takes off a ninja attack (−35%), before Durability. */
    public static final float MAX_REDUCTION = 0.35f;
    /** Durability and reinforcement together never negate more than this share of a hit. */
    public static final float COMBINED_CAP = 0.70f;
    /** The share of vanilla armour's (and its enchantments') normal reduction a ninja attack still meets. */
    public static final float ARMOUR_SHARE = 0.25f;

    /** Chakra per reinforced strike you land. */
    public static final float STRIKE_COST = 1.0f;
    /** Chakra per point of damage reinforcement stops… */
    public static final float GUARD_COST_PER_DAMAGE = 0.3f;
    /** …and at least this much per reinforced hit taken. */
    public static final float MIN_GUARD_COST = 0.5f;
    /** CTL makes reinforcement cheaper: cost × √(10 / CTL), never below this. */
    public static final float MIN_COST_FACTOR = 0.3f;
    /** NPC ninjas' chakra control: this share of their reserves above 10 (see {@link CombatStats#combatControl}). */
    public static final float NPC_CONTROL_SHARE = 0.3f;

    /** What kind of ninja attack is being applied (see {@link #hurt}). */
    public enum Kind { MELEE, WEAPON, JUTSU, FIRE }

    /** The ninja attack being applied right now (null outside one): who it lands on and what it is. */
    private static LivingEntity currentTarget;
    private static Kind currentKind;
    private static net.minecraft.world.entity.Entity currentAttacker;

    private ChakraReinforcement() {}

    // ---------------------------------------------------------------- the numbers

    /** The chakra behind reinforcement: the average of Reserves and CTL (NPCs: their reserves and control). */
    public static float chakraStat(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> (d.getReserves() + d.getControl()) / 2f).orElse(ShinobiData.DEFAULT_STAT);
        }
        if (entity instanceof CombatStats stats) {
            return (stats.combatReserves() + stats.combatControl()) / 2f;
        }
        return ShinobiData.DEFAULT_STAT;
    }

    /** The CTL that sets the chakra cost (NPCs: their control). */
    public static float control(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getControl).orElse(ShinobiData.DEFAULT_STAT);
        }
        return entity instanceof CombatStats stats ? stats.combatControl() : ShinobiData.DEFAULT_STAT;
    }

    /** 0 at chakra stat 10, rising toward 1 (×{@code difficulty.chakraReinforcement}). */
    public static float strength(float chakraStat) {
        if (chakraStat <= ShinobiData.DEFAULT_STAT) {
            return 0f;
        }
        float raw = 1f - (float) Math.exp(-(chakraStat - ShinobiData.DEFAULT_STAT) / SCALE);
        return raw * NoirmConfig.f(NoirmConfig.CHAKRA_REINFORCEMENT);
    }

    /** The bonus on melee and weapon hits at this chakra stat (0.2 = +20%). */
    public static float damageBonus(float chakraStat) {
        return MAX_DAMAGE_BONUS * strength(chakraStat);
    }

    /** The share of a ninja attack taken off at this chakra stat, before Durability (0.2 = −20%). */
    public static float reduction(float chakraStat) {
        return Math.min(0.9f, MAX_REDUCTION * strength(chakraStat));
    }

    /** Durability's and reinforcement's shares together: they multiply, capped at {@link #COMBINED_CAP} (never below DUR alone). */
    public static float combined(float durability, float reinforcement) {
        float together = 1f - (1f - durability) * (1f - reinforcement);
        return Math.max(durability, Math.min(COMBINED_CAP, together));
    }

    /** Chakra control makes reinforcement cheaper: √(10 / CTL), between {@link #MIN_COST_FACTOR} and 1. */
    public static float costFactor(float control) {
        return Mth.clamp((float) Math.sqrt(ShinobiData.DEFAULT_STAT / Math.max(1f, control)), MIN_COST_FACTOR, 1f);
    }

    /** The share of vanilla armour's normal reduction a ninja attack meets (config {@code difficulty.ninjaArmour}). */
    public static float armourShare() {
        return Mth.clamp(NoirmConfig.f(NoirmConfig.NINJA_ARMOUR), 0f, 1f);
    }

    // ---------------------------------------------------------------- which hits are ninja attacks

    /**
     * Applies a ninja attack: {@code target.hurt(source, amount)} with the hit marked as NOIRM's, so
     * armour, reinforcement and the fire rules treat it as one. Every NOIRM damage path goes through here.
     */
    public static boolean hurt(LivingEntity target, DamageSource source, float amount, Kind kind) {
        LivingEntity previousTarget = currentTarget;
        Kind previousKind = currentKind;
        net.minecraft.world.entity.Entity previousAttacker = currentAttacker;
        currentTarget = target;
        currentKind = kind;
        currentAttacker = source.getEntity();
        try {
            return target.hurt(source, amount);
        } finally {
            currentTarget = previousTarget;
            currentKind = previousKind;
            currentAttacker = previousAttacker;
        }
    }

    /** Is the damage {@code victim} is taking right now a ninja attack (a hit from {@link #hurt}, or ninja fire burning)? */
    public static boolean isNinjaAttack(LivingEntity victim, DamageSource source) {
        return currentTarget == victim || NinjaFire.isNinjaBurn(victim, source);
    }

    /** The kind of the ninja attack landing on {@code victim} right now, or null. */
    public static Kind currentKind(LivingEntity victim) {
        return currentTarget == victim ? currentKind : null;
    }

    /** Who is behind the ninja attack landing on {@code victim} right now (null: none, or burning). */
    public static net.minecraft.world.entity.Entity currentAttacker(LivingEntity victim) {
        return currentTarget == victim ? currentAttacker : null;
    }

    // ---------------------------------------------------------------- striking

    /**
     * A reinforced strike: the multiplier for a melee or weapon hit {@code attacker} lands on
     * {@code target} (1 + the bonus), paying {@link #STRIKE_COST} chakra (× the CTL factor). 1 if the
     * chakra isn't there, against a training dummy (no chakra wasted on straw) or with no bonus at all.
     */
    public static float strike(LivingEntity attacker, LivingEntity target, Vec3 at) {
        float bonus = damageBonus(chakraStat(attacker));
        if (bonus <= 0f || target instanceof com.noirm.entity.TrainingDummy) {
            return 1f;
        }
        if (!pay(attacker, STRIKE_COST * costFactor(control(attacker)))) {
            return 1f;
        }
        strikeSheen(attacker, at);
        return 1f + bonus;
    }

    // ---------------------------------------------------------------- taking hits

    /**
     * What is left of {@code amount} after Durability and (for ninja attacks) reinforcement, with the
     * reinforcement's chakra paid. Called once per hit, after armour (Durability's damage event).
     */
    public static float defend(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0f || Durability.exempt(source)) {
            return amount;
        }
        float dur = Durability.negation(Durability.of(victim));
        float total = dur;
        if (isNinjaAttack(victim, source)) {
            float rein = reduction(chakraStat(victim));
            if (rein > 0f) {
                float withRein = combined(dur, rein);
                float stopped = amount * (withRein - dur);
                float cost = Math.max(MIN_GUARD_COST, stopped * GUARD_COST_PER_DAMAGE) * costFactor(control(victim));
                if (stopped > 0f && pay(victim, cost)) {
                    total = withRein;
                    guardSheen(victim);
                }
            }
        }
        if (total <= 0f) {
            return amount;
        }
        return Math.max(Math.min(amount, Durability.MIN_DAMAGE), amount * (1f - total));
    }

    // ---------------------------------------------------------------- chakra

    /** Does {@code entity} have the chakra to reinforce a hit right now (the HUD's "Chakra armour")? */
    public static boolean ready(LivingEntity entity) {
        return chakra(entity) >= STRIKE_COST * costFactor(control(entity)) && damageBonus(chakraStat(entity)) > 0f;
    }

    public static float chakra(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getChakra).orElse(0f);
        }
        return entity instanceof SparringNinja ninja ? ninja.chakra() : 0f;
    }

    /** Spends {@code cost} chakra if there is enough (creative players and other mobs: free if they have stats). */
    private static boolean pay(LivingEntity entity, float cost) {
        if (entity instanceof Player player) {
            if (player.getAbilities().instabuild) {
                return true;
            }
            return ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(cost)).orElse(false);
        }
        if (entity instanceof SparringNinja ninja) {
            return ninja.tryUseChakra(cost);
        }
        return entity instanceof CombatStats; // beasts and other NOIRM fighters: their chakra is endless
    }

    // ---------------------------------------------------------------- the look

    /** The faint blue of chakra laid over a body: lighter than a jutsu's, so it reads as a sheen. */
    public static final int SHEEN = 0x8FD4FF;
    public static final int SHEEN_EDGE = 0x1E4E8C;

    /** A reinforced strike: a few faint chakra motes at the point of impact. */
    private static void strikeSheen(LivingEntity attacker, Vec3 at) {
        if (at == null || attacker.level().isClientSide()) {
            return;
        }
        Vfx.burst(attacker.level(), new VfxOptions(VfxKind.GLOW, SHEEN, SHEEN_EDGE, 0.5f, 0.06f, 7, 0f, 0.86f), at, 2, 0.1, 0.01);
        Vfx.radial(attacker.level(), new VfxOptions(VfxKind.GLOW, 0xFFFFFF, SHEEN, 0.12f, 0.03f, 9, 0f, 0.85f), at, 6, 0.2);
    }

    /** A reinforced body shrugging a hit off: a thin shimmer over the skin. */
    private static void guardSheen(LivingEntity victim) {
        if (victim.level().isClientSide()) {
            return;
        }
        var random = victim.getRandom();
        float w = victim.getBbWidth();
        float h = victim.getBbHeight();
        for (int i = 0; i < 6; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            Vec3 at = victim.position().add(Math.cos(angle) * w * 0.62, 0.2 + random.nextDouble() * h * 0.85, Math.sin(angle) * w * 0.62);
            Vfx.one(victim.level(), new VfxOptions(VfxKind.GLOW, SHEEN, SHEEN_EDGE, 0.22f, 0.04f, 9, -0.01f, 0.9f), at,
                    new Vec3(Math.cos(angle) * 0.02, 0.02, Math.sin(angle) * 0.02));
        }
    }
}

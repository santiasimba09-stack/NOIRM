package com.noirm.jutsu;

import com.noirm.awakening.AwakeningData;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Eight Gates' time limit (wave "clones", 2026-11-26). The Gates no longer burn health (no self-damage, so no
 * screen shake). Instead each gate stays open for a while, then slams shut and leaves the body worn out for a bit.
 *
 * <p>How long it stays open grows with five things: <b>Durability</b>, <b>Willpower</b>, <b>Taijutsu</b>,
 * <b>Vitality</b> (each point above 10 adds seconds) and your current <b>Resolve</b> (a full meter adds
 * {@link #RESOLVE_BONUS_SECONDS}). Higher gates are harder: the seconds are divided by {@link #TIER_COST}.
 *
 * <p>Once your body is trained enough (the average of DUR, WIL, TAI and VIT reaches {@link #UNLIMITED_AVERAGE} for
 * that gate) the gate has <b>no time limit</b>: it stays open while you have chakra. At the starting numbers that's
 * around average 35 for the first gate and 45 for the second, a mid-game point, not the endgame.
 *
 * <p>The Might line ({@code Perk.GATE_MASTERY}) holds them longer and reaches "no limit" sooner.
 *
 * <p>All the numbers are here at the top (docs/TUNING.md).
 */
public final class EightGates {
    /** Seconds a gate stays open on an untrained body (all stats 10, no Resolve), by tier 1..8. */
    public static final float[] BASE_SECONDS = {0, 20, 15, 12, 10, 8, 7, 6, 5};
    /** Seconds added per point above 10 of each stat. */
    public static final float DURABILITY_SECONDS = 0.6f;
    public static final float WILLPOWER_SECONDS = 0.6f;
    public static final float TAIJUTSU_SECONDS = 0.7f;
    public static final float VITALITY_SECONDS = 0.5f;
    /** Seconds a full Resolve meter adds (scaled by how full it is). */
    public static final float RESOLVE_BONUS_SECONDS = 20f;
    /** The stat seconds are divided by this for each tier (gate 2 gets ÷1.5, gate 3 ÷2…). */
    public static final float[] TIER_COST = {1, 1, 1.5f, 2, 2.5f, 3, 3.5f, 4, 5};
    /** Average of DUR, WIL, TAI, VIT at which a gate has no time limit, by tier 1..8. */
    public static final float[] UNLIMITED_AVERAGE = {0, 35, 45, 60, 75, 90, 110, 140, 200};
    /** The stats all start at this value. */
    public static final float BASE_STAT = 10f;
    /** After a gate slams shut: worn out (slow, weak) for this many seconds per tier, and it can't reopen meanwhile. */
    public static final int AFTERMATH_SECONDS_PER_TIER = 8;
    /** Warn on the action bar when this many seconds are left. */
    public static final int WARN_SECONDS = 5;
    /** NPCs (no saved stats): their taijutsu stands in for all four. */
    public static final float NPC_STAT_FACTOR = 1f;

    private static final Map<LivingEntity, Long> WORN_UNTIL = new WeakHashMap<>();

    private EightGates() {}

    private static int tier(int gate) {
        return Mth.clamp(gate, 1, 8);
    }

    /** The four body stats' average (players: their stats; NPCs: their taijutsu). */
    public static float bodyAverage(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> (d.getDurability() + d.getWillpower() + d.getTaijutsu() + d.getVitality()) / 4f)
                    .orElse(BASE_STAT);
        }
        if (entity instanceof SparringNinja ninja) {
            return ninja.combatTaijutsu() * NPC_STAT_FACTOR;
        }
        return BASE_STAT;
    }

    /** How much the Might line eases the Gates: 1 for anyone else, lower for them (see {@code ModeSystem.gateWear}). */
    private static float wear(LivingEntity entity, int gate) {
        return ModeSystem.gateWear(entity, gate);
    }

    /** True if {@code gate} has no time limit for this body. */
    public static boolean unlimited(LivingEntity entity, int gate) {
        float needed = UNLIMITED_AVERAGE[tier(gate)] * (0.5f + 0.5f * wear(entity, gate));
        return bodyAverage(entity) >= needed;
    }

    /** Seconds {@code gate} stays open for this body (ignoring "no limit"). */
    public static float limitSeconds(LivingEntity entity, int gate) {
        int t = tier(gate);
        float extra;
        float resolve = 0f;
        if (entity instanceof Player player) {
            ShinobiData d = ShinobiCapability.get(player).orElse(null);
            if (d == null) {
                return BASE_SECONDS[t];
            }
            extra = above(d.getDurability()) * DURABILITY_SECONDS + above(d.getWillpower()) * WILLPOWER_SECONDS
                    + above(d.getTaijutsu()) * TAIJUTSU_SECONDS + above(d.getVitality()) * VITALITY_SECONDS;
            resolve = d.awakening().resolve() / AwakeningData.MAX_RESOLVE * RESOLVE_BONUS_SECONDS;
        } else {
            float avg = bodyAverage(entity);
            extra = above(avg) * (DURABILITY_SECONDS + WILLPOWER_SECONDS + TAIJUTSU_SECONDS + VITALITY_SECONDS);
        }
        return (BASE_SECONDS[t] + (extra + resolve) / TIER_COST[t]) / wear(entity, gate);
    }

    private static float above(float stat) {
        return Math.max(0f, stat - BASE_STAT);
    }

    /** Ticks the gate stays open, or {@link Long#MAX_VALUE} with no limit. */
    public static long limitTicks(LivingEntity entity, int gate) {
        return unlimited(entity, gate) ? Long.MAX_VALUE : Math.max(20L, Math.round(limitSeconds(entity, gate) * 20));
    }

    /** Right after a gate opens: tell the player how long they have. */
    static void announceOpen(LivingEntity entity, int gate, long ticks) {
        if (entity instanceof Player player) {
            Component text = ticks == Long.MAX_VALUE
                    ? Component.translatable("message.noirm.gates.open_unlimited")
                    : Component.translatable("message.noirm.gates.open", ticks / 20);
            player.displayClientMessage(text.copy().withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Each tick a gate is open with a limit: a countdown warning near the end. */
    static void tickWarning(LivingEntity entity, long ticksLeft) {
        if (entity instanceof Player player && ticksLeft <= WARN_SECONDS * 20L && ticksLeft % 20 == 0 && ticksLeft > 0) {
            player.displayClientMessage(Component.translatable("message.noirm.gates.closing", ticksLeft / 20).withStyle(ChatFormatting.RED), true);
        }
    }

    /** The time ran out: the gate slams shut and the body is worn out for a while (no damage). */
    public static void slamShut(LivingEntity entity, int gate) {
        int seconds = AFTERMATH_SECONDS_PER_TIER * tier(gate);
        WORN_UNTIL.put(entity, entity.level().getGameTime() + seconds * 20L);
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20, 1, false, false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, seconds * 20, 0, false, false, true));
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.gates.spent", seconds).withStyle(ChatFormatting.RED), true);
        }
    }

    /** True while the body is still worn out from a gate that slammed shut (a gate can't reopen). */
    public static boolean wornOut(LivingEntity entity) {
        Long until = WORN_UNTIL.get(entity);
        return until != null && entity.level().getGameTime() < until;
    }

    /** Clears the worn-out state (tests, commands). */
    public static void rest(LivingEntity entity) {
        WORN_UNTIL.remove(entity);
    }
}

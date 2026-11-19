package com.noirm.pills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One effect running on a player from a food or a pill: what it does ({@link Kind}), how strong
 * ({@code amount}), how long it has left, and where it came from (the item, for the HUD). A pill's
 * <b>crash</b> is a boost with a {@code delay}: it waits until the pill wears off, then starts.
 *
 * <p>Stat boosts (speed, taijutsu, CTL...) add to the real stat while they run and take exactly that
 * much back when they end ({@link #applied}), so the stat's own growth in between is kept.
 */
public final class Boost {
    /** What a boost does. {@code amount} means a multiplier, points per second, or a share of the stat. */
    public enum Kind {
        /** Chakra regeneration × amount. */
        CHAKRA_REGEN(Unit.MULTIPLIER),
        /** Stamina regeneration × amount. */
        STAMINA_REGEN(Unit.MULTIPLIER),
        /** Health back per second. */
        HEALTH_REGEN(Unit.PER_SECOND),
        /** Posture recovered per second, on top of the normal recovery. */
        POSTURE_RECOVERY(Unit.PER_SECOND),
        /** Stat XP from training and fights × amount. */
        TRAINING_XP(Unit.MULTIPLIER),
        /** Perception × amount (how slow attacks look to you). */
        PERCEPTION(Unit.MULTIPLIER),
        SPEED(Unit.STAT_SHARE),
        TAIJUTSU(Unit.STAT_SHARE),
        VITALITY(Unit.STAT_SHARE),
        RESERVES(Unit.STAT_SHARE),
        CONTROL(Unit.STAT_SHARE),
        WILLPOWER(Unit.STAT_SHARE),
        DURABILITY(Unit.STAT_SHARE),
        /** Poison and wither are washed out as soon as they take hold. */
        POISON_WARD(Unit.NONE),
        /** A vanilla status effect ({@code effect}, {@code amplifier}) for the boost's length. */
        EFFECT(Unit.NONE);

        public enum Unit { MULTIPLIER, PER_SECOND, STAT_SHARE, NONE }

        private final Unit unit;

        Kind(Unit unit) {
            this.unit = unit;
        }

        public Unit unit() {
            return unit;
        }

        public boolean isStat() {
            return unit == Unit.STAT_SHARE;
        }

        /** "stamina_regen" (JSON and lang keys). */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Kind byKey(String key) {
            for (Kind k : values()) {
                if (k.key().equals(key)) {
                    return k;
                }
            }
            return null;
        }
    }

    public final Kind kind;
    public final float amount;
    public final ResourceLocation source;
    public final boolean crash;
    /** For {@link Kind#EFFECT}: the vanilla effect and its level (0 = I). */
    public final ResourceLocation effect;
    public final int amplifier;
    public final int total;
    int ticksLeft;
    /** Ticks before it starts (a crash waits for the pill to wear off). */
    int delay;
    /** For stat boosts: how many points were added (taken back at the end). */
    float applied;
    /** Whether {@link #applied} / the vanilla effect has been put on yet. */
    boolean started;

    public Boost(Kind kind, float amount, int ticks, int delay, ResourceLocation source, boolean crash, ResourceLocation effect, int amplifier) {
        this.kind = kind;
        this.amount = amount;
        this.total = Math.max(1, ticks);
        this.ticksLeft = this.total;
        this.delay = Math.max(0, delay);
        this.source = source;
        this.crash = crash;
        this.effect = effect;
        this.amplifier = amplifier;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public int delay() {
        return delay;
    }

    /** Running now (not still waiting to start). */
    public boolean active() {
        return delay <= 0;
    }

    public float applied() {
        return applied;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", kind.key());
        tag.putFloat("Amount", amount);
        tag.putInt("Total", total);
        tag.putInt("Left", ticksLeft);
        tag.putInt("Delay", delay);
        tag.putString("Source", source.toString());
        tag.putBoolean("Crash", crash);
        if (effect != null) {
            tag.putString("Effect", effect.toString());
        }
        tag.putInt("Amplifier", amplifier);
        tag.putFloat("Applied", applied);
        tag.putBoolean("Started", started);
        return tag;
    }

    static Boost load(CompoundTag tag) {
        Kind kind = Kind.byKey(tag.getString("Kind"));
        ResourceLocation source = ResourceLocation.tryParse(tag.getString("Source"));
        if (kind == null || source == null) {
            return null;
        }
        ResourceLocation effect = tag.contains("Effect") ? ResourceLocation.tryParse(tag.getString("Effect")) : null;
        Boost b = new Boost(kind, tag.getFloat("Amount"), tag.getInt("Total"), tag.getInt("Delay"), source, tag.getBoolean("Crash"),
                effect, tag.getInt("Amplifier"));
        b.ticksLeft = Math.max(1, tag.getInt("Left"));
        b.applied = tag.getFloat("Applied");
        b.started = tag.getBoolean("Started");
        return b;
    }
}

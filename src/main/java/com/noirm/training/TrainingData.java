package com.noirm.training;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/**
 * A player's progress toward their next point in each trained stat. The stats themselves live
 * on {@code ShinobiData}; this holds the experience gathered since the last point, plus the
 * bookkeeping for chakra spent (it trains Reserves) and the max-chakra bonus already applied.
 */
public final class TrainingData {
    private final Map<TrainingSystem.Stat, Float> xp = new EnumMap<>(TrainingSystem.Stat.class);
    /** Ticks spent sprinting since the last speed XP from running (not saved). */
    private int sprintTicks;
    /** Chakra spent since the last tick, turned into Reserves XP by {@link TrainingSystem#tick}. */
    private float chakraSpent;
    /** The max-chakra bonus from Reserves currently included in the max (so it can be adjusted). */
    private float appliedChakraBonus;
    /** XP toward the next CTL / WIL point ({@link Supplementary}). */
    private float controlXp;
    private float willpowerXp;
    /** Chakra spent while below half, since the last tick (CTL XP; not saved). */
    private float pressureSpent;
    /** Ticks walking on water since the last CTL XP from it (not saved). */
    private int waterTicks;
    /** Whether the player was locked in last tick (WIL XP when it ends; not saved). */
    private boolean wasLockedIn;
    /** What you've done in the current fight, for kill XP ({@link KillXp}; not saved). */
    private final FightLog fight = new FightLog();

    public FightLog fight() {
        return fight;
    }

    public float xp(TrainingSystem.Stat stat) {
        return xp.getOrDefault(stat, 0f);
    }

    public void setXp(TrainingSystem.Stat stat, float value) {
        xp.put(stat, Math.max(0f, value));
    }

    public float xp(Supplementary.Stat stat) {
        return stat == Supplementary.Stat.CONTROL ? controlXp : willpowerXp;
    }

    public void setXp(Supplementary.Stat stat, float value) {
        if (stat == Supplementary.Stat.CONTROL) {
            controlXp = Math.max(0f, value);
        } else {
            willpowerXp = Math.max(0f, value);
        }
    }

    public void addPressureSpent(float amount) {
        pressureSpent += Math.max(0f, amount);
    }

    float takePressureSpent() {
        float spent = pressureSpent;
        pressureSpent = 0f;
        return spent;
    }

    /** Counts one tick moving on water; returns true (and restarts) every {@code interval} ticks. */
    boolean waterTick(int interval) {
        if (++waterTicks >= interval) {
            waterTicks = 0;
            return true;
        }
        return false;
    }

    boolean wasLockedIn() {
        return wasLockedIn;
    }

    void setWasLockedIn(boolean value) {
        wasLockedIn = value;
    }

    /** Counts one sprinting tick; returns true (and restarts) every {@code interval} ticks. */
    boolean sprintTick(int interval) {
        if (++sprintTicks >= interval) {
            sprintTicks = 0;
            return true;
        }
        return false;
    }

    public void addChakraSpent(float amount) {
        chakraSpent += Math.max(0f, amount);
    }

    float takeChakraSpent() {
        float spent = chakraSpent;
        chakraSpent = 0f;
        return spent;
    }

    public float appliedChakraBonus() {
        return appliedChakraBonus;
    }

    public void setAppliedChakraBonus(float bonus) {
        appliedChakraBonus = bonus;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (TrainingSystem.Stat stat : TrainingSystem.Stat.values()) {
            tag.putFloat(stat.key() + "Xp", xp(stat));
        }
        tag.putFloat("AppliedChakraBonus", appliedChakraBonus);
        tag.putFloat("ControlXp", controlXp);
        tag.putFloat("WillpowerXp", willpowerXp);
        return tag;
    }

    public void load(CompoundTag tag) {
        xp.clear();
        for (TrainingSystem.Stat stat : TrainingSystem.Stat.values()) {
            setXp(stat, tag.getFloat(stat.key() + "Xp"));
        }
        appliedChakraBonus = tag.getFloat("AppliedChakraBonus");
        controlXp = Math.max(0f, tag.getFloat("ControlXp"));
        willpowerXp = Math.max(0f, tag.getFloat("WillpowerXp"));
        sprintTicks = 0;
        pressureSpent = 0f;
        waterTicks = 0;
        wasLockedIn = false;
        chakraSpent = 0f;
        fight.reset();
    }
}

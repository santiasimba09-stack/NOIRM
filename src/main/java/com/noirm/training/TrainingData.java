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

    public float xp(TrainingSystem.Stat stat) {
        return xp.getOrDefault(stat, 0f);
    }

    public void setXp(TrainingSystem.Stat stat, float value) {
        xp.put(stat, Math.max(0f, value));
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
        return tag;
    }

    public void load(CompoundTag tag) {
        xp.clear();
        for (TrainingSystem.Stat stat : TrainingSystem.Stat.values()) {
            setXp(stat, tag.getFloat(stat.key() + "Xp"));
        }
        appliedChakraBonus = tag.getFloat("AppliedChakraBonus");
        sprintTicks = 0;
        chakraSpent = 0f;
    }
}

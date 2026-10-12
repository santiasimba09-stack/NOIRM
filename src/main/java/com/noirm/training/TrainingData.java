package com.noirm.training;

import net.minecraft.nbt.CompoundTag;

/**
 * A player's progress toward their next speed and taijutsu point. The stats themselves live on
 * {@code ShinobiData}; this only holds the experience gathered since the last point.
 */
public final class TrainingData {
    private float taijutsuXp;
    private float speedXp;
    /** Ticks spent sprinting since the last speed XP from running (not saved). */
    private int sprintTicks;

    public float xp(TrainingSystem.Stat stat) {
        return stat == TrainingSystem.Stat.TAIJUTSU ? taijutsuXp : speedXp;
    }

    public void setXp(TrainingSystem.Stat stat, float value) {
        if (stat == TrainingSystem.Stat.TAIJUTSU) {
            taijutsuXp = Math.max(0f, value);
        } else {
            speedXp = Math.max(0f, value);
        }
    }

    /** Counts one sprinting tick; returns true (and restarts) every {@code interval} ticks. */
    boolean sprintTick(int interval) {
        if (++sprintTicks >= interval) {
            sprintTicks = 0;
            return true;
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("TaijutsuXp", taijutsuXp);
        tag.putFloat("SpeedXp", speedXp);
        return tag;
    }

    public void load(CompoundTag tag) {
        taijutsuXp = Math.max(0f, tag.getFloat("TaijutsuXp"));
        speedXp = Math.max(0f, tag.getFloat("SpeedXp"));
        sprintTicks = 0;
    }
}

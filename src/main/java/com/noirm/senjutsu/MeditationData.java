package com.noirm.senjutsu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A player's meditation state and their nature energy (the resource Sage Mode will use).
 * Only nature energy is saved; meditating always ends when you log out or die.
 * The rules live in {@link Meditation}.
 */
public final class MeditationData {
    /** Gathered nature energy, 0..{@link Meditation#NATURE_MAX}. Saved. */
    private float natureEnergy;
    /** Sitting in meditation right now (not saved). */
    private boolean meditating;
    /** Where meditation started: moving away from here ends it. */
    private double anchorX, anchorY, anchorZ;
    /** Ticks since meditation started. */
    private int ticks;
    /** Game time of the last damage taken (for "in a fight"); not saved. */
    private long lastHurtTime = Long.MIN_VALUE / 2;
    /** Last nature sample around the player: the gain multiplier from nearby natural blocks. */
    private float natureFactor = 1f;
    /** The nature energy (rounded) the client last heard about. */
    private int syncedEnergy = -1;

    public float natureEnergy() {
        return natureEnergy;
    }

    public void setNatureEnergy(float value) {
        natureEnergy = Mth.clamp(value, 0f, Meditation.NATURE_MAX);
    }

    public boolean isMeditating() {
        return meditating;
    }

    void begin(double x, double y, double z) {
        meditating = true;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        ticks = 0;
        natureFactor = 1f;
    }

    void end() {
        meditating = false;
        ticks = 0;
    }

    double anchorX() {
        return anchorX;
    }

    double anchorY() {
        return anchorY;
    }

    double anchorZ() {
        return anchorZ;
    }

    int tickAndCount() {
        return ++ticks;
    }

    public int ticks() {
        return ticks;
    }

    long lastHurtTime() {
        return lastHurtTime;
    }

    void setLastHurtTime(long time) {
        lastHurtTime = time;
    }

    public float natureFactor() {
        return natureFactor;
    }

    void setNatureFactor(float factor) {
        natureFactor = factor;
    }

    /** True (and remembers the new value) when the rounded energy changed since the last sync. */
    boolean energyChangedForSync() {
        int now = Math.round(natureEnergy);
        if (now != syncedEnergy) {
            syncedEnergy = now;
            return true;
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("NatureEnergy", natureEnergy);
        return tag;
    }

    public void load(CompoundTag tag) {
        setNatureEnergy(tag.getFloat("NatureEnergy"));
        meditating = false;
        ticks = 0;
        syncedEnergy = -1;
    }
}

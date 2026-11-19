package com.noirm.awakening;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Resolve and the state of the near-death awakening ("locked in", then exhausted). */
public final class AwakeningData {
    public static final float MAX_RESOLVE = 100f;

    private float resolve;
    /** Game time of the last awakening; -1 if never. */
    private long lastAwakening = -1;
    private int lockedInTicks;
    private int exhaustionTicks;
    /** Nature surge path: clan-nature jutsu are free and stronger while locked in. */
    private boolean natureSurge;

    public float resolve() {
        return resolve;
    }

    public void setResolve(float value) {
        resolve = Mth.clamp(value, 0f, MAX_RESOLVE);
    }

    public long lastAwakening() {
        return lastAwakening;
    }

    public void setLastAwakening(long gameTime) {
        lastAwakening = gameTime;
    }

    public boolean isLockedIn() {
        return lockedInTicks > 0;
    }

    public int lockedInTicks() {
        return lockedInTicks;
    }

    public void setLockedInTicks(int ticks) {
        lockedInTicks = Math.max(0, ticks);
    }

    public boolean isExhausted() {
        return exhaustionTicks > 0;
    }

    public int exhaustionTicks() {
        return exhaustionTicks;
    }

    public void setExhaustionTicks(int ticks) {
        exhaustionTicks = Math.max(0, ticks);
    }

    public boolean natureSurge() {
        return natureSurge && isLockedIn();
    }

    public void setNatureSurge(boolean surge) {
        natureSurge = surge;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Resolve", resolve);
        tag.putLong("LastAwakening", lastAwakening);
        tag.putInt("LockedIn", lockedInTicks);
        tag.putInt("Exhaustion", exhaustionTicks);
        tag.putBoolean("NatureSurge", natureSurge);
        return tag;
    }

    public void load(CompoundTag tag) {
        resolve = Mth.clamp(tag.getFloat("Resolve"), 0f, MAX_RESOLVE);
        lastAwakening = tag.contains("LastAwakening") ? tag.getLong("LastAwakening") : -1;
        lockedInTicks = Math.max(0, tag.getInt("LockedIn"));
        exhaustionTicks = Math.max(0, tag.getInt("Exhaustion"));
        natureSurge = tag.getBoolean("NatureSurge");
    }
}

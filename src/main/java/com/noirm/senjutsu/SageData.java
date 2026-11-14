package com.noirm.senjutsu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A player's sage state (kept inside {@link MeditationData}, saved with it): petrification, the
 * statue, Sage Mode and the exhaustion after it. The rules live in {@link Sage} and {@link SageMode}.
 */
public final class SageData {
    /** 0–100: how far the body has turned to stone. Saved. */
    private float petrification;
    /** Ticks spent as a statue (0 = not a statue). Saved. */
    private int statueTicks;
    /** Sage Mode is on. Saved. */
    private boolean active;
    /** Ticks of exhaustion left after Sage Mode ran dry (no Sage Mode until it's over). Saved. */
    private int exhaustionTicks;
    /** The stone-body stage whose slowdown is applied right now (not saved: re-applied after a load). */
    private int appliedStage = -1;
    /** What the client last heard (see {@link #visualsChanged}). */
    private int sentPetrify = -1, sentFlags = -1, sentLimit = -1;

    public float petrification() {
        return petrification;
    }

    public void setPetrification(float value) {
        petrification = Mth.clamp(value, 0f, Sage.STATUE);
    }

    public boolean isStatue() {
        return statueTicks > 0;
    }

    public int statueTicks() {
        return statueTicks;
    }

    void setStatueTicks(int ticks) {
        statueTicks = Math.max(0, ticks);
    }

    public boolean active() {
        return active;
    }

    void setActive(boolean on) {
        active = on;
    }

    public int exhaustionTicks() {
        return exhaustionTicks;
    }

    void setExhaustionTicks(int ticks) {
        exhaustionTicks = Math.max(0, ticks);
    }

    int appliedStage() {
        return appliedStage;
    }

    void setAppliedStage(int stage) {
        appliedStage = stage;
    }

    /** Flags for the visuals packet. */
    public int flags() {
        return (active ? SagePackets.SAGE : 0) | (isStatue() ? SagePackets.STATUE : 0) | (exhaustionTicks > 0 ? SagePackets.EXHAUSTED : 0);
    }

    /** True (and remembers the new values) when what the client shows has changed. */
    boolean visualsChanged(float limit) {
        int p = Math.round(petrification);
        int f = flags();
        int l = Math.round(limit);
        if (p != sentPetrify || f != sentFlags || l != sentLimit) {
            sentPetrify = p;
            sentFlags = f;
            sentLimit = l;
            return true;
        }
        return false;
    }

    /** Forget what was sent, so the next tick sends everything again (login, respawn). */
    public void resend() {
        sentPetrify = sentFlags = sentLimit = -1;
        appliedStage = -1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Petrification", petrification);
        tag.putInt("Statue", statueTicks);
        tag.putBoolean("Active", active);
        tag.putInt("Exhaustion", exhaustionTicks);
        return tag;
    }

    public void load(CompoundTag tag) {
        setPetrification(tag.getFloat("Petrification"));
        statueTicks = Math.max(0, tag.getInt("Statue"));
        active = tag.getBoolean("Active");
        exhaustionTicks = Math.max(0, tag.getInt("Exhaustion"));
        resend();
    }
}

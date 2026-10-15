package com.noirm.dojutsu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A player's dōjutsu (eye techniques): the Sharingan (how many tomoe, 0 = not awakened, and
 * progress) and the Byakugan (Hyūga are born with it). One toggle opens whichever eye you have;
 * if you somehow have both, the Sharingan wins.
 */
public final class EyeData {
    public static final int MAX_STAGE = 3;

    private int sharinganStage;
    private int sharinganXp;
    private boolean byakugan;
    /** Not saved: the eyes are always closed when you log in. */
    private boolean active;
    /** Sharingan XP that came from drills (the training dummy): capped, see {@link Sharingan#DRILL_XP_CAP}. */
    private int drillXp;
    /** A dormant Uchiha's building "pressure" (0–100): when full, the next intense moment awakens the eyes. */
    private float pressure;

    // Not saved: what the current fight looks like (see SharinganAwakening).
    /** Game time of the last exchange with a real opponent / with the training dummy; -1 = never. */
    private long lastFightTick = -1;
    private long lastDrillTick = -1;
    /** How much stronger than you the toughest opponent of the current fight is (their power / yours). */
    private float foeRatio = 1f;
    /** Game time of the last exchange with a much stronger opponent, and how long that hard fight has lasted. */
    private long lastHardTick = -1;
    private int hardFightTicks;
    /** Game time of the last precognition warning (to reward dodges the eyes foresaw). */
    private long lastWarnedTick = -1;
    /** Game time the eyes last studied someone's jutsu (XP has a cooldown). */
    private long lastStudyTick = -1;
    /** The Sharingan stays open for free this many more ticks (right after awakening or a new tomoe). */
    private int freeTicks;

    public int sharinganStage() {
        return sharinganStage;
    }

    public void setSharinganStage(int stage) {
        sharinganStage = Mth.clamp(stage, 0, MAX_STAGE);
        if (!hasEyes()) {
            active = false;
        }
    }

    public int sharinganXp() {
        return sharinganXp;
    }

    public void setSharinganXp(int xp) {
        sharinganXp = Math.max(0, xp);
    }

    public boolean hasByakugan() {
        return byakugan;
    }

    public void setByakugan(boolean byakugan) {
        this.byakugan = byakugan;
        if (!hasEyes()) {
            active = false;
        }
    }

    /** Has any dōjutsu at all. */
    public boolean hasEyes() {
        return sharinganStage > 0 || byakugan;
    }

    public boolean isActive() {
        return active && hasEyes();
    }

    public void setActive(boolean active) {
        this.active = active && hasEyes();
    }

    /** Sharingan tomoe while active, else 0. */
    public int activeStage() {
        return isActive() ? sharinganStage : 0;
    }

    /** The Byakugan is open (and no Sharingan is taking its place). */
    public boolean isByakuganActive() {
        return isActive() && byakugan && sharinganStage == 0;
    }

    public int drillXp() {
        return drillXp;
    }

    public void setDrillXp(int xp) {
        drillXp = Math.max(0, xp);
    }

    public float pressure() {
        return pressure;
    }

    public void setPressure(float value) {
        pressure = Mth.clamp(value, 0f, 100f);
    }

    public long lastFightTick() {
        return lastFightTick;
    }

    public void setLastFightTick(long gameTime) {
        lastFightTick = gameTime;
    }

    public long lastDrillTick() {
        return lastDrillTick;
    }

    public void setLastDrillTick(long gameTime) {
        lastDrillTick = gameTime;
    }

    public float foeRatio() {
        return foeRatio;
    }

    public void setFoeRatio(float ratio) {
        foeRatio = ratio;
    }

    public long lastHardTick() {
        return lastHardTick;
    }

    public void setLastHardTick(long gameTime) {
        lastHardTick = gameTime;
    }

    public int hardFightTicks() {
        return hardFightTicks;
    }

    public void setHardFightTicks(int ticks) {
        hardFightTicks = Math.max(0, ticks);
    }

    public long lastWarnedTick() {
        return lastWarnedTick;
    }

    public void setLastWarnedTick(long gameTime) {
        lastWarnedTick = gameTime;
    }

    public long lastStudyTick() {
        return lastStudyTick;
    }

    public void setLastStudyTick(long gameTime) {
        lastStudyTick = gameTime;
    }

    public int freeTicks() {
        return freeTicks;
    }

    public void setFreeTicks(int ticks) {
        freeTicks = Math.max(0, ticks);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SharinganStage", sharinganStage);
        tag.putInt("SharinganXp", sharinganXp);
        tag.putBoolean("Byakugan", byakugan);
        tag.putInt("SharinganDrillXp", drillXp);
        tag.putFloat("SharinganPressure", pressure);
        return tag;
    }

    public void load(CompoundTag tag) {
        sharinganStage = Mth.clamp(tag.getInt("SharinganStage"), 0, MAX_STAGE);
        sharinganXp = Math.max(0, tag.getInt("SharinganXp"));
        byakugan = tag.getBoolean("Byakugan");
        drillXp = Math.max(0, tag.getInt("SharinganDrillXp"));
        pressure = Mth.clamp(tag.getFloat("SharinganPressure"), 0f, 100f);
        active = false;
        freeTicks = 0;
    }
}

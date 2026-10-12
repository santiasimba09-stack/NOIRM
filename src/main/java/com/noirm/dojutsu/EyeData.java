package com.noirm.dojutsu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** A player's Sharingan: how many tomoe (0 = not awakened), progress, and whether it's on. */
public final class EyeData {
    public static final int MAX_STAGE = 3;

    private int sharinganStage;
    private int sharinganXp;
    /** Not saved: the eyes are always off when you log in. */
    private boolean active;

    public int sharinganStage() {
        return sharinganStage;
    }

    public void setSharinganStage(int stage) {
        sharinganStage = Mth.clamp(stage, 0, MAX_STAGE);
        if (sharinganStage == 0) {
            active = false;
        }
    }

    public int sharinganXp() {
        return sharinganXp;
    }

    public void setSharinganXp(int xp) {
        sharinganXp = Math.max(0, xp);
    }

    public boolean isActive() {
        return active && sharinganStage > 0;
    }

    public void setActive(boolean active) {
        this.active = active && sharinganStage > 0;
    }

    /** Stage while active, else 0. */
    public int activeStage() {
        return isActive() ? sharinganStage : 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SharinganStage", sharinganStage);
        tag.putInt("SharinganXp", sharinganXp);
        return tag;
    }

    public void load(CompoundTag tag) {
        sharinganStage = Mth.clamp(tag.getInt("SharinganStage"), 0, MAX_STAGE);
        sharinganXp = Math.max(0, tag.getInt("SharinganXp"));
        active = false;
    }
}

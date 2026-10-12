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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SharinganStage", sharinganStage);
        tag.putInt("SharinganXp", sharinganXp);
        tag.putBoolean("Byakugan", byakugan);
        return tag;
    }

    public void load(CompoundTag tag) {
        sharinganStage = Mth.clamp(tag.getInt("SharinganStage"), 0, MAX_STAGE);
        sharinganXp = Math.max(0, tag.getInt("SharinganXp"));
        byakugan = tag.getBoolean("Byakugan");
        active = false;
    }
}

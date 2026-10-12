package com.noirm.biju;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Whether the player is a jinchūriki (tails > 0) and the state of the chakra cloak. */
public final class BijuData {
    public static final int MAX_TAILS = 9;

    private int tails;
    /** 0 = off, 1 = cloak, 2 = the beast has taken over (awakening only). */
    private int cloakStage;
    private int cloakTicks;
    private int cooldownTicks;

    public boolean isJinchuriki() {
        return tails > 0;
    }

    public int tails() {
        return tails;
    }

    public void setTails(int value) {
        tails = Mth.clamp(value, 0, MAX_TAILS);
        if (tails == 0) {
            cloakStage = 0;
            cloakTicks = 0;
        }
    }

    public int cloakStage() {
        return cloakStage;
    }

    public int cloakTicks() {
        return cloakTicks;
    }

    public void setCloak(int stage, int ticks) {
        cloakStage = isJinchuriki() ? Mth.clamp(stage, 0, 2) : 0;
        cloakTicks = cloakStage == 0 ? 0 : Math.max(0, ticks);
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int ticks) {
        cooldownTicks = Math.max(0, ticks);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Tails", tails);
        tag.putInt("Cooldown", cooldownTicks);
        return tag;
    }

    public void load(CompoundTag tag) {
        tails = Mth.clamp(tag.getInt("Tails"), 0, MAX_TAILS);
        cooldownTicks = Math.max(0, tag.getInt("Cooldown"));
        cloakStage = 0;
        cloakTicks = 0;
    }
}

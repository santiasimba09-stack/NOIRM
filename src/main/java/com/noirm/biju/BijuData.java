package com.noirm.biju;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Whether the player is a jinchūriki ({@code tails} = which beast: 1 = One-Tail … 9 = Nine-Tails),
 * the chakra cloak (how many tails of chakra are drawn right now), how far the beast lets you go
 * ({@code unlockedTails}), and your bond with it (see {@link BeastBond}).
 */
public final class BijuData {
    public static final int MAX_TAILS = 9;

    private int tails;
    /** Tails of chakra drawn into the cloak right now (0 when off). */
    private int cloakTails;
    /** How many tails you can draw (grows with use and bond). */
    private int unlockedTails = 1;
    /** Progress toward the next tail. */
    private float tailXp;
    /** -100 (hatred) … 100 (true partners). */
    private int bond;
    private BeastBond.Request request;
    private long nextRequestAt;
    private int chakraCooldown;
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
        int before = tails;
        tails = Mth.clamp(value, 0, MAX_TAILS);
        if (tails == 0) {
            cloakStage = 0;
            cloakTicks = 0;
            cloakTails = 0;
        }
        if (tails != before) {
            // A new beast: you start again with it, and it doesn't like you yet.
            unlockedTails = 1;
            tailXp = 0;
            bond = BeastBond.startingBond(tails);
            request = null;
            nextRequestAt = 0;
        }
    }

    public int cloakTails() {
        return cloakStage == 0 ? 0 : cloakTails;
    }

    public void setCloakTails(int value) {
        cloakTails = Mth.clamp(value, 0, Math.max(1, tails));
    }

    public int unlockedTails() {
        return Math.min(Math.max(1, unlockedTails), Math.max(1, tails));
    }

    public void setUnlockedTails(int value) {
        unlockedTails = Mth.clamp(value, 1, MAX_TAILS);
    }

    public float tailXp() {
        return tailXp;
    }

    public void setTailXp(float value) {
        tailXp = Math.max(0, value);
    }

    public int bond() {
        return bond;
    }

    public void setBond(int value) {
        bond = Mth.clamp(value, -100, 100);
    }

    public BeastBond.Request request() {
        return request;
    }

    public void setRequest(BeastBond.Request request) {
        this.request = request;
    }

    public long nextRequestAt() {
        return nextRequestAt;
    }

    public void setNextRequestAt(long tick) {
        nextRequestAt = tick;
    }

    public int chakraCooldown() {
        return chakraCooldown;
    }

    public void setChakraCooldown(int ticks) {
        chakraCooldown = Math.max(0, ticks);
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
        if (cloakStage == 0) {
            cloakTails = 0;
        } else if (cloakTails == 0) {
            cloakTails = 1;
        }
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
        tag.putInt("Unlocked", unlockedTails);
        tag.putFloat("TailXp", tailXp);
        tag.putInt("Bond", bond);
        tag.putLong("NextRequest", nextRequestAt);
        tag.putInt("ChakraCooldown", chakraCooldown);
        if (request != null) {
            tag.put("Request", request.save());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        tails = Mth.clamp(tag.getInt("Tails"), 0, MAX_TAILS);
        cooldownTicks = Math.max(0, tag.getInt("Cooldown"));
        cloakStage = 0;
        cloakTicks = 0;
        cloakTails = 0;
        unlockedTails = tag.contains("Unlocked") ? Mth.clamp(tag.getInt("Unlocked"), 1, MAX_TAILS) : 1;
        tailXp = tag.getFloat("TailXp");
        bond = tag.contains("Bond") ? Mth.clamp(tag.getInt("Bond"), -100, 100) : BeastBond.startingBond(tails);
        nextRequestAt = tag.getLong("NextRequest");
        chakraCooldown = tag.getInt("ChakraCooldown");
        request = tag.contains("Request") ? BeastBond.Request.load(tag.getCompound("Request")) : null;
    }
}

package com.noirm.creator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * The character creation part of a player's saved data ({@code ShinobiData.creator()}): their
 * look, whether the creation screen is open and in which {@link Mode}, whether they are held in
 * place until they finish, and the genetics rolls they've seen so far.
 */
public final class CreatorData {
    /** What the creation screen is open for. */
    public enum Mode {
        /** Closed. */
        NONE,
        /** A newcomer: everything, with the clan rolls; held in place until done. */
        FIRST_JOIN,
        /** {@code /noirm creator}: appearance only; the clan and stats stay. */
        APPEARANCE,
        /** {@code /noirm creator reroll} (cheats): everything again, with fresh rolls. */
        FULL;

        public boolean genetics() {
            return this == FIRST_JOIN || this == FULL;
        }

        public static Mode byId(int id) {
            Mode[] all = values();
            return id >= 0 && id < all.length ? all[id] : NONE;
        }
    }

    private PlayerLook look;
    private Mode mode = Mode.NONE;
    private boolean held;
    private double holdX;
    private double holdY;
    private double holdZ;
    private final List<GeneticRoll> rolls = new ArrayList<>();
    private int chosen;
    private int rerollsUsed;

    /** The player's look, or null if they never made one (older characters: their own skin). */
    public PlayerLook look() {
        return look;
    }

    public boolean hasLook() {
        return look != null;
    }

    public void setLook(PlayerLook look) {
        this.look = look == null ? null : look.copy();
    }

    /** The height multiplier (1 without a look). */
    public float height() {
        return look == null ? 1f : look.height();
    }

    public Mode mode() {
        return mode;
    }

    void setMode(Mode mode) {
        this.mode = mode;
    }

    /** Held: frozen, untouchable and unable to act until the creation screen is finished. */
    public boolean held() {
        return held;
    }

    void hold(double x, double y, double z) {
        held = true;
        holdX = x;
        holdY = y;
        holdZ = z;
    }

    void release() {
        held = false;
    }

    public double holdX() {
        return holdX;
    }

    public double holdY() {
        return holdY;
    }

    public double holdZ() {
        return holdZ;
    }

    public List<GeneticRoll> rolls() {
        return List.copyOf(rolls);
    }

    void clearRolls() {
        rolls.clear();
        chosen = 0;
        rerollsUsed = 0;
    }

    void addRoll(GeneticRoll roll) {
        rolls.add(roll);
    }

    public int chosen() {
        return chosen;
    }

    void setChosen(int index) {
        chosen = index;
    }

    public GeneticRoll chosenRoll() {
        return chosen >= 0 && chosen < rolls.size() ? rolls.get(chosen) : null;
    }

    public int rerollsUsed() {
        return rerollsUsed;
    }

    void useReroll() {
        rerollsUsed++;
    }

    public int rerollsLeft() {
        return Math.max(0, CharacterCreator.MAX_REROLLS - rerollsUsed);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (look != null) {
            tag.put("Look", look.save());
        }
        tag.putByte("Mode", (byte) mode.ordinal());
        tag.putBoolean("Held", held);
        tag.putDouble("HoldX", holdX);
        tag.putDouble("HoldY", holdY);
        tag.putDouble("HoldZ", holdZ);
        ListTag list = new ListTag();
        rolls.forEach(r -> list.add(r.save()));
        tag.put("Rolls", list);
        tag.putInt("Chosen", chosen);
        tag.putInt("RerollsUsed", rerollsUsed);
        return tag;
    }

    public void load(CompoundTag tag) {
        look = tag.contains("Look") ? PlayerLook.load(tag.getCompound("Look")) : null;
        mode = Mode.byId(tag.getByte("Mode"));
        held = tag.getBoolean("Held");
        holdX = tag.getDouble("HoldX");
        holdY = tag.getDouble("HoldY");
        holdZ = tag.getDouble("HoldZ");
        rolls.clear();
        for (Tag entry : tag.getList("Rolls", Tag.TAG_COMPOUND)) {
            rolls.add(GeneticRoll.load((CompoundTag) entry));
        }
        chosen = tag.getInt("Chosen");
        rerollsUsed = tag.getInt("RerollsUsed");
    }
}

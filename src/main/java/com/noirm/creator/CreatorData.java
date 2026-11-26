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
 *
 * <p>Each roll also remembers every set of talents seen for it ({@link #talentSets}): a talent reroll
 * adds a new set to the chosen roll (same clan, same stats) and the player can switch back to any
 * earlier one, so neither kind of reroll is ever a loss.
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
    /** Per roll (same index): every talent set seen for it, and which one it holds now. */
    private final List<List<List<String>>> talentSets = new ArrayList<>();
    private final List<Integer> talentChoice = new ArrayList<>();
    private int chosen;
    private int rerollsUsed;
    private int talentRerollsUsed;

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
        talentSets.clear();
        talentChoice.clear();
        chosen = 0;
        rerollsUsed = 0;
        talentRerollsUsed = 0;
    }

    /** Rolls saved before talents existed get theirs (biased by the roll's clan). */
    void fillMissingTalents(java.util.function.Function<GeneticRoll, java.util.Map<String, Float>> bias, net.minecraft.util.RandomSource random) {
        for (int i = 0; i < rolls.size(); i++) {
            GeneticRoll roll = rolls.get(i);
            if (roll.talents().isEmpty()) {
                GeneticRoll filled = roll.withTalents(com.noirm.traits.Talents.roll(random, bias.apply(roll)));
                rolls.set(i, filled);
                talentSets.set(i, new ArrayList<>(List.of(filled.talents())));
                talentChoice.set(i, 0);
            }
        }
    }

    void addRoll(GeneticRoll roll) {
        rolls.add(roll);
        talentSets.add(new ArrayList<>(List.of(roll.talents())));
        talentChoice.add(0);
    }

    /** How many talent sets the roll at {@code index} has seen (1 = only its first). */
    public int talentSetCount(int index) {
        return index >= 0 && index < talentSets.size() ? talentSets.get(index).size() : 0;
    }

    /** Which of its talent sets the roll at {@code index} holds now (0-based). */
    public int talentChoice(int index) {
        return index >= 0 && index < talentChoice.size() ? talentChoice.get(index) : 0;
    }

    /** Every talent set the roll at {@code index} has seen. */
    public List<List<String>> talentSets(int index) {
        return index >= 0 && index < talentSets.size() ? List.copyOf(talentSets.get(index)) : List.of();
    }

    /** A new talent set for the chosen roll (its clan and stats stay); it now holds it. */
    void addTalentSet(List<String> talents) {
        if (chosen < 0 || chosen >= rolls.size()) {
            return;
        }
        talentSets.get(chosen).add(List.copyOf(talents));
        selectTalentSet(talentSets.get(chosen).size() - 1);
    }

    /** The chosen roll goes back to one of the talent sets it has seen. Returns whether it changed. */
    boolean selectTalentSet(int set) {
        if (chosen < 0 || chosen >= rolls.size() || set < 0 || set >= talentSets.get(chosen).size()) {
            return false;
        }
        talentChoice.set(chosen, set);
        rolls.set(chosen, rolls.get(chosen).withTalents(talentSets.get(chosen).get(set)));
        return true;
    }

    public int talentRerollsUsed() {
        return talentRerollsUsed;
    }

    void useTalentReroll() {
        talentRerollsUsed++;
    }

    public int talentRerollsLeft() {
        return Math.max(0, CharacterCreator.MAX_TALENT_REROLLS - talentRerollsUsed);
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
        ListTag sets = new ListTag();
        for (int i = 0; i < talentSets.size(); i++) {
            CompoundTag entry = new CompoundTag();
            ListTag seen = new ListTag();
            for (List<String> set : talentSets.get(i)) {
                ListTag ids = new ListTag();
                set.forEach(t -> ids.add(net.minecraft.nbt.StringTag.valueOf(t)));
                seen.add(ids);
            }
            entry.put("Seen", seen);
            entry.putInt("Choice", talentChoice.get(i));
            sets.add(entry);
        }
        tag.put("TalentSets", sets);
        tag.putInt("TalentRerollsUsed", talentRerollsUsed);
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
        talentRerollsUsed = tag.getInt("TalentRerollsUsed");
        // Talent sets per roll; rolls saved before talent rerolls existed start with their own set.
        talentSets.clear();
        talentChoice.clear();
        ListTag sets = tag.getList("TalentSets", Tag.TAG_COMPOUND);
        for (int i = 0; i < rolls.size(); i++) {
            List<List<String>> seen = new ArrayList<>();
            int choice = 0;
            if (i < sets.size()) {
                CompoundTag entry = sets.getCompound(i);
                for (Tag set : entry.getList("Seen", Tag.TAG_LIST)) {
                    List<String> ids = new ArrayList<>();
                    for (Tag id : (ListTag) set) {
                        ids.add(id.getAsString());
                    }
                    seen.add(List.copyOf(ids));
                }
                choice = entry.getInt("Choice");
            }
            if (seen.isEmpty() || choice < 0 || choice >= seen.size() || !seen.get(choice).equals(rolls.get(i).talents())) {
                seen = new ArrayList<>(List.of(rolls.get(i).talents()));
                choice = 0;
            }
            talentSets.add(new ArrayList<>(seen));
            talentChoice.add(choice);
        }
    }
}

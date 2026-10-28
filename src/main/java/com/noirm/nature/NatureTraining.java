package com.noirm.nature;

import com.noirm.clan.Nature;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's road to new natures (saved in {@code ShinobiData}): their <b>hidden affinity</b> (1–2
 * basic natures they'd learn more easily, rolled once), whether a chakra paper has shown it yet,
 * and how far each nature drill has come. The rules live in {@link NatureDrills}.
 */
public final class NatureTraining {
    /** The basic natures hidden in this player's chakra. Empty until rolled. */
    private final EnumSet<Nature> hidden = EnumSet.noneOf(Nature.class);
    /** Whether the hidden natures were rolled (old saves start without them). */
    private boolean rolled;
    /** Whether a chakra paper has shown them to the player. */
    private boolean revealed;
    /** Whether the player was told about the chakra paper (once). */
    private boolean hinted;
    /** Seconds of drill done per nature (at a pace of 1). */
    private final EnumMap<Nature, Float> progress = new EnumMap<>(Nature.class);
    /** The nature being drilled now (the last one that moved, or the one a scroll or command picked). */
    private Nature active;

    // ---------------------------------------------------------------- live state (not saved)
    /** The pace of the drill at the last check (0 = nothing moving), for the HUD. */
    private float pace;
    /** The kind of teacher helping at the last check: 0 none, 1 crew mate, 2 master. */
    private int teacher;
    /** Whether the client's copy is out of date. */
    private boolean dirty = true;

    public Set<Nature> hidden() {
        return hidden;
    }

    public boolean isHidden(Nature nature) {
        return hidden.contains(nature);
    }

    public boolean rolled() {
        return rolled;
    }

    public boolean revealed() {
        return revealed;
    }

    public void setRevealed(boolean value) {
        revealed = value;
        dirty = true;
    }

    public boolean hinted() {
        return hinted;
    }

    public void setHinted(boolean value) {
        hinted = value;
    }

    /**
     * Rolls 1–2 hidden natures among the basic natures in {@code candidates} (once; does nothing if
     * already rolled). {@link NatureDrills#TWO_HIDDEN_CHANCE} of players get two.
     */
    public void roll(RandomSource random, java.util.List<Nature> candidates) {
        if (rolled) {
            return;
        }
        rolled = true;
        java.util.List<Nature> pool = new java.util.ArrayList<>(candidates);
        int count = random.nextFloat() < NatureDrills.TWO_HIDDEN_CHANCE ? 2 : 1;
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            hidden.add(pool.remove(random.nextInt(pool.size())));
        }
        dirty = true;
    }

    /** Forgets the roll (the next {@link #roll} rolls again). */
    void clearRoll() {
        hidden.clear();
        rolled = false;
        dirty = true;
    }

    /** Sets the hidden natures directly (commands, tests). */
    public void setHidden(java.util.Collection<Nature> natures) {
        hidden.clear();
        hidden.addAll(natures);
        rolled = true;
        dirty = true;
    }

    public float progress(Nature nature) {
        return progress.getOrDefault(nature, 0f);
    }

    public Map<Nature, Float> allProgress() {
        return progress;
    }

    public void setProgress(Nature nature, float seconds) {
        if (seconds <= 0f) {
            progress.remove(nature);
        } else {
            progress.put(nature, seconds);
        }
        dirty = true;
    }

    public Nature active() {
        return active;
    }

    public void setActive(Nature nature) {
        if (active != nature) {
            active = nature;
            dirty = true;
        }
    }

    public float pace() {
        return pace;
    }

    public int teacher() {
        return teacher;
    }

    /** Remembers the current pace and teacher (for the HUD); marks dirty only when they change. */
    void setPace(float newPace, int newTeacher) {
        if (Math.abs(newPace - pace) > 1e-4f || newTeacher != teacher) {
            pace = newPace;
            teacher = newTeacher;
            dirty = true;
        }
    }

    public void markDirty() {
        dirty = true;
    }

    /** Returns whether a sync is needed, and clears the flag. */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Rolled", rolled);
        tag.putBoolean("Revealed", revealed);
        tag.putBoolean("Hinted", hinted);
        ListTag hiddenList = new ListTag();
        hidden.forEach(n -> hiddenList.add(StringTag.valueOf(n.getSerializedName())));
        tag.put("Hidden", hiddenList);
        CompoundTag progressTag = new CompoundTag();
        progress.forEach((n, s) -> progressTag.putFloat(n.getSerializedName(), s));
        tag.put("Progress", progressTag);
        if (active != null) {
            tag.putString("Active", active.getSerializedName());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        rolled = tag.getBoolean("Rolled");
        revealed = tag.getBoolean("Revealed");
        hinted = tag.getBoolean("Hinted");
        hidden.clear();
        for (Tag t : tag.getList("Hidden", Tag.TAG_STRING)) {
            Nature n = byName(t.getAsString());
            if (n != null) {
                hidden.add(n);
            }
        }
        progress.clear();
        CompoundTag progressTag = tag.getCompound("Progress");
        for (String key : progressTag.getAllKeys()) {
            Nature n = byName(key);
            if (n != null && progressTag.getFloat(key) > 0f) {
                progress.put(n, progressTag.getFloat(key));
            }
        }
        active = byName(tag.getString("Active"));
        pace = 0f;
        teacher = 0;
        dirty = true;
    }

    public static Nature byName(String name) {
        for (Nature n : Nature.values()) {
            if (n.getSerializedName().equals(name)) {
                return n;
            }
        }
        return null;
    }
}

package com.noirm.traits;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * A player's personal talents (part of {@code ShinobiData}, saved as "Talents") and the
 * bookkeeping for perks that change a maximum (applied once, so they can be taken back).
 *
 * <p>{@link #version}: 0 = a character made before talents existed. On their next login they roll
 * talents once and get their clan's perks ({@code PerkEvents.migrate}).
 */
public final class TalentData {
    /** The current version of talents and clan perks. */
    public static final int CURRENT = 1;

    private final List<String> talents = new ArrayList<>();
    private int version;
    /** Max stamina currently added by perks (so a change replaces it, never stacks). */
    private float appliedStamina;
    /** Whether the Inuzuka hound was handed out. */
    private boolean houndGiven;
    /** Bumped on every change: cached perk totals are rebuilt. */
    private int changes;

    // the cached totals (not saved)
    private EnumMap<Perk, Float> cache;
    private long cacheKey = Long.MIN_VALUE;

    public List<String> talents() {
        return Collections.unmodifiableList(talents);
    }

    public boolean has(String id) {
        return talents.contains(id);
    }

    public void set(List<String> ids) {
        talents.clear();
        ids.stream().distinct().forEach(talents::add);
        changes++;
    }

    public boolean add(String id) {
        if (talents.contains(id)) {
            return false;
        }
        talents.add(id);
        changes++;
        return true;
    }

    public boolean remove(String id) {
        boolean removed = talents.remove(id);
        if (removed) {
            changes++;
        }
        return removed;
    }

    public int version() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public float appliedStamina() {
        return appliedStamina;
    }

    public void setAppliedStamina(float value) {
        appliedStamina = value;
    }

    public boolean houndGiven() {
        return houndGiven;
    }

    public void setHoundGiven(boolean given) {
        houndGiven = given;
    }

    int changes() {
        return changes;
    }

    EnumMap<Perk, Float> cached(long key) {
        return key == cacheKey ? cache : null;
    }

    void cache(long key, EnumMap<Perk, Float> totals) {
        cacheKey = key;
        cache = totals;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        talents.forEach(t -> list.add(StringTag.valueOf(t)));
        tag.put("List", list);
        tag.putInt("Version", version);
        tag.putFloat("AppliedStamina", appliedStamina);
        tag.putBoolean("Hound", houndGiven);
        return tag;
    }

    public void load(CompoundTag tag) {
        talents.clear();
        for (Tag t : tag.getList("List", Tag.TAG_STRING)) {
            talents.add(t.getAsString());
        }
        version = tag.getInt("Version");
        appliedStamina = tag.getFloat("AppliedStamina");
        houndGiven = tag.getBoolean("Hound");
        changes++;
    }
}

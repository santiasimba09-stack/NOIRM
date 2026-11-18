package com.noirm.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's fighting styles (part of {@code ShinobiData}): the styles they learned in play (from a
 * master, a scroll, watching or sparring, or their clan), how each was learned, and which weapon
 * style they chose for each weapon moveset ({@code "katana" -> "noirm:blade_iai"}). Rules live in
 * {@link StyleSystem}; this just holds the data.
 */
public final class StyleBook {
    /** Style id -> how it was learned ({@code master}, {@code scroll}, {@code watch}, {@code clan}, {@code command}). */
    private final Map<String, String> known = new LinkedHashMap<>();
    /** Weapon moveset key -> the chosen weapon style id. */
    private final Map<String, String> weaponPicks = new LinkedHashMap<>();

    public boolean knows(String style) {
        return known.containsKey(style);
    }

    /** Learns {@code style}; false if it was known already. */
    public boolean learn(String style, String how) {
        if (known.containsKey(style)) {
            return false;
        }
        known.put(style, how == null ? "" : how);
        return true;
    }

    /** How {@code style} was learned, or "" if it wasn't. */
    public String howLearned(String style) {
        return known.getOrDefault(style, "");
    }

    public Set<String> known() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(known.keySet()));
    }

    /** The weapon style chosen for {@code moveset} ({@code "katana"}), or "" for the weapon's own. */
    public String weaponPick(String moveset) {
        return weaponPicks.getOrDefault(moveset, "");
    }

    public void setWeaponPick(String moveset, String style) {
        if (style == null || style.isEmpty()) {
            weaponPicks.remove(moveset);
        } else {
            weaponPicks.put(moveset, style);
        }
    }

    public Map<String, String> weaponPicks() {
        return Collections.unmodifiableMap(weaponPicks);
    }

    public void clear() {
        known.clear();
        weaponPicks.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        known.forEach((style, how) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", style);
            entry.putString("How", how);
            list.add(entry);
        });
        tag.put("Known", list);
        CompoundTag picks = new CompoundTag();
        weaponPicks.forEach(picks::putString);
        tag.put("WeaponPicks", picks);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        for (Tag t : tag.getList("Known", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            known.put(entry.getString("Id"), entry.getString("How"));
        }
        CompoundTag picks = tag.getCompound("WeaponPicks");
        for (String key : picks.getAllKeys()) {
            weaponPicks.put(key, picks.getString(key));
        }
    }
}

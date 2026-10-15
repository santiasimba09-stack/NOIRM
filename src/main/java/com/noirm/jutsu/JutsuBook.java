package com.noirm.jutsu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The jutsu a player knows, how well (mastery XP), which ones were copied with the Sharingan,
 * and the 4-slot loadout cast with keys 1–4 in combat mode.
 */
public final class JutsuBook {
    public static final int SLOTS = 4;
    /** Mastery XP needed for levels 1–5. */
    private static final int[] LEVEL_XP = {10, 30, 60, 100, 150};
    public static final int MAX_MASTERY = LEVEL_XP.length;

    private final Map<ResourceLocation, Integer> mastery = new LinkedHashMap<>();
    private final Set<ResourceLocation> copied = new HashSet<>();
    private final ResourceLocation[] loadout = new ResourceLocation[SLOTS];
    /** Remaining cooldown ticks per jutsu. Not saved. */
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();

    public boolean knows(ResourceLocation id) {
        return mastery.containsKey(id);
    }

    public Set<ResourceLocation> known() {
        return Collections.unmodifiableSet(mastery.keySet());
    }

    /** Learns a jutsu (no-op if known). Returns true if it was new. */
    public boolean learn(ResourceLocation id, boolean viaCopy) {
        if (knows(id)) {
            return false;
        }
        mastery.put(id, 0);
        if (viaCopy) {
            copied.add(id);
        }
        return true;
    }

    /** Forgets a jutsu (e.g. a Mangekyō ability the eyes no longer carry) and takes it out of the loadout. */
    public boolean forget(ResourceLocation id) {
        if (mastery.remove(id) == null) {
            return false;
        }
        copied.remove(id);
        cooldowns.remove(id);
        for (int i = 0; i < SLOTS; i++) {
            if (id.equals(loadout[i])) {
                loadout[i] = null;
            }
        }
        return true;
    }

    public boolean isCopied(ResourceLocation id) {
        return copied.contains(id);
    }

    public int xp(ResourceLocation id) {
        return mastery.getOrDefault(id, 0);
    }

    public void addXp(ResourceLocation id, int amount) {
        mastery.computeIfPresent(id, (k, v) -> v + amount);
    }

    /** 0 (new) to {@link #MAX_MASTERY}. */
    public int masteryLevel(ResourceLocation id) {
        return levelFor(xp(id));
    }

    /** XP needed to reach a mastery level (0 for level 0). */
    public static int xpForLevel(int level) {
        return level <= 0 ? 0 : LEVEL_XP[Math.min(level, LEVEL_XP.length) - 1];
    }

    public static int levelFor(int xp) {
        int level = 0;
        while (level < LEVEL_XP.length && xp >= LEVEL_XP[level]) {
            level++;
        }
        return level;
    }

    public ResourceLocation slot(int slot) {
        return slot >= 0 && slot < SLOTS ? loadout[slot] : null;
    }

    /** Puts a known jutsu (or nothing, if null) in a slot. A jutsu can only be in one slot. */
    public boolean equip(int slot, ResourceLocation id) {
        if (slot < 0 || slot >= SLOTS || (id != null && !knows(id))) {
            return false;
        }
        if (id != null) {
            for (int i = 0; i < SLOTS; i++) {
                if (id.equals(loadout[i])) {
                    loadout[i] = null;
                }
            }
        }
        loadout[slot] = id;
        return true;
    }

    /** Puts the jutsu in the first empty slot, if any. */
    public void equipIfRoom(ResourceLocation id) {
        for (int i = 0; i < SLOTS; i++) {
            if (id.equals(loadout[i])) {
                return;
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (loadout[i] == null) {
                loadout[i] = id;
                return;
            }
        }
    }

    public int cooldown(ResourceLocation id) {
        return cooldowns.getOrDefault(id, 0);
    }

    public void setCooldown(ResourceLocation id, int ticks) {
        if (ticks > 0) {
            cooldowns.put(id, ticks);
        } else {
            cooldowns.remove(id);
        }
    }

    public void tickCooldowns() {
        cooldowns.replaceAll((id, t) -> t - 1);
        cooldowns.values().removeIf(t -> t <= 0);
    }

    public void clear() {
        mastery.clear();
        copied.clear();
        cooldowns.clear();
        java.util.Arrays.fill(loadout, null);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag masteryTag = new CompoundTag();
        mastery.forEach((id, xp) -> masteryTag.putInt(id.toString(), xp));
        tag.put("Mastery", masteryTag);
        ListTag copiedList = new ListTag();
        copied.forEach(id -> copiedList.add(StringTag.valueOf(id.toString())));
        tag.put("Copied", copiedList);
        ListTag slots = new ListTag();
        for (ResourceLocation id : loadout) {
            slots.add(StringTag.valueOf(id == null ? "" : id.toString()));
        }
        tag.put("Loadout", slots);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        CompoundTag masteryTag = tag.getCompound("Mastery");
        for (String key : masteryTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                mastery.put(id, masteryTag.getInt(key));
            }
        }
        for (Tag t : tag.getList("Copied", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                copied.add(id);
            }
        }
        ListTag slots = tag.getList("Loadout", Tag.TAG_STRING);
        for (int i = 0; i < SLOTS && i < slots.size(); i++) {
            String value = slots.getString(i);
            loadout[i] = value.isEmpty() ? null : ResourceLocation.tryParse(value);
        }
    }
}

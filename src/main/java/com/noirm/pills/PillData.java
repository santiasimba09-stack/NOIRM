package com.noirm.pills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a player carries of foods and pills (a sub-object of {@code ShinobiData}, saved under
 * {@code "Pills"}): the <b>pharmacology</b> skill and its XP, the recipes learned from scrolls,
 * the body's <b>toxicity</b> (every pill adds some; it wears off), each pill's cooldown, and the
 * effects running now ({@link Boost}s, from foods and pills, including a pill's crash waiting to hit).
 *
 * <p>The server owns it; the client gets a copy for the HUD and the apothecary through
 * {@link PillPackets.Sync}.
 */
public final class PillData {
    /** Pharmacology starts here (a beginner can brew the simplest pills). */
    public static final float START_PHARMACOLOGY = 1f;
    /** Pharmacology tops out here. */
    public static final float MAX_PHARMACOLOGY = 100f;
    /** Toxicity is a meter from 0 to this. */
    public static final float MAX_TOXICITY = 100f;

    private float pharmacology = START_PHARMACOLOGY;
    private float pharmacologyXp;
    private float toxicity;
    /** Recipes learned from medical scrolls (masters' recipes come from their lessons, starters are always known). */
    private final Set<ResourceLocation> learned = new LinkedHashSet<>();
    /** Pill id → ticks left before another can be swallowed. */
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();
    /** Pill id → that cooldown's full length (for the HUD's ring). */
    private final Map<ResourceLocation, Integer> cooldownTotals = new HashMap<>();
    private final List<Boost> boosts = new ArrayList<>();
    /** Lifetime counts, for advancements and the first-steps tip. */
    private int brewed;
    private int bestGrade = -1;
    private int swallowed;
    private int eaten;
    private boolean dirty = true;

    // ---------------------------------------------------------------- pharmacology

    public float pharmacology() {
        return pharmacology;
    }

    public void setPharmacology(float value) {
        pharmacology = Mth.clamp(value, START_PHARMACOLOGY, MAX_PHARMACOLOGY);
        dirty = true;
    }

    public float pharmacologyXp() {
        return pharmacologyXp;
    }

    public void setPharmacologyXp(float xp) {
        pharmacologyXp = Math.max(0f, xp);
        dirty = true;
    }

    public Set<ResourceLocation> learned() {
        return learned;
    }

    public boolean learn(ResourceLocation recipe) {
        boolean added = learned.add(recipe);
        dirty |= added;
        return added;
    }

    // ---------------------------------------------------------------- toxicity and cooldowns

    public float toxicity() {
        return toxicity;
    }

    public void setToxicity(float value) {
        float clamped = Mth.clamp(value, 0f, MAX_TOXICITY);
        if (clamped != toxicity) {
            toxicity = clamped;
            dirty = true;
        }
    }

    public int cooldown(ResourceLocation pill) {
        return cooldowns.getOrDefault(pill, 0);
    }

    public int cooldownTotal(ResourceLocation pill) {
        return cooldownTotals.getOrDefault(pill, 0);
    }

    public void setCooldown(ResourceLocation pill, int ticks) {
        if (ticks <= 0) {
            cooldowns.remove(pill);
            cooldownTotals.remove(pill);
        } else {
            cooldowns.put(pill, ticks);
            cooldownTotals.put(pill, ticks);
        }
        dirty = true;
    }

    public Map<ResourceLocation, Integer> cooldowns() {
        return cooldowns;
    }

    /** One tick of every cooldown. */
    void tickCooldowns() {
        if (cooldowns.isEmpty()) {
            return;
        }
        var it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int left = e.getValue() - 1;
            if (left <= 0) {
                it.remove();
                cooldownTotals.remove(e.getKey());
                dirty = true;
            } else {
                e.setValue(left);
            }
        }
    }

    public void clearCooldowns() {
        cooldowns.clear();
        cooldownTotals.clear();
        dirty = true;
    }

    // ---------------------------------------------------------------- boosts

    /** The effects running now (and crashes waiting to start). Change it through {@link PillEffects}. */
    public List<Boost> boosts() {
        return boosts;
    }

    // ---------------------------------------------------------------- counts

    public int brewed() {
        return brewed;
    }

    public void addBrewed(int grade) {
        brewed++;
        bestGrade = Math.max(bestGrade, grade);
        dirty = true;
    }

    /** The best grade ever brewed (-1 = never brewed). */
    public int bestGrade() {
        return bestGrade;
    }

    public int swallowed() {
        return swallowed;
    }

    public void addSwallowed() {
        swallowed++;
    }

    public int eaten() {
        return eaten;
    }

    public void addEaten() {
        eaten++;
    }

    // ---------------------------------------------------------------- sync and save

    public void markDirty() {
        dirty = true;
    }

    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Pharmacology", pharmacology);
        tag.putFloat("PharmacologyXp", pharmacologyXp);
        tag.putFloat("Toxicity", toxicity);
        ListTag learnedTag = new ListTag();
        learned.forEach(id -> learnedTag.add(StringTag.valueOf(id.toString())));
        tag.put("Learned", learnedTag);
        CompoundTag cd = new CompoundTag();
        cooldowns.forEach((id, ticks) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("Left", ticks);
            one.putInt("Total", cooldownTotal(id));
            cd.put(id.toString(), one);
        });
        tag.put("Cooldowns", cd);
        ListTag boostTag = new ListTag();
        boosts.forEach(b -> boostTag.add(b.save()));
        tag.put("Boosts", boostTag);
        tag.putInt("Brewed", brewed);
        tag.putInt("BestGrade", bestGrade);
        tag.putInt("Swallowed", swallowed);
        tag.putInt("Eaten", eaten);
        return tag;
    }

    public void load(CompoundTag tag) {
        pharmacology = Mth.clamp(tag.contains("Pharmacology") ? tag.getFloat("Pharmacology") : START_PHARMACOLOGY,
                START_PHARMACOLOGY, MAX_PHARMACOLOGY);
        pharmacologyXp = Math.max(0f, tag.getFloat("PharmacologyXp"));
        toxicity = Mth.clamp(tag.getFloat("Toxicity"), 0f, MAX_TOXICITY);
        learned.clear();
        tag.getList("Learned", Tag.TAG_STRING).forEach(t -> {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                learned.add(id);
            }
        });
        cooldowns.clear();
        cooldownTotals.clear();
        CompoundTag cd = tag.getCompound("Cooldowns");
        for (String key : cd.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            CompoundTag one = cd.getCompound(key);
            if (id != null && one.getInt("Left") > 0) {
                cooldowns.put(id, one.getInt("Left"));
                cooldownTotals.put(id, Math.max(one.getInt("Left"), one.getInt("Total")));
            }
        }
        boosts.clear();
        tag.getList("Boosts", Tag.TAG_COMPOUND).forEach(t -> {
            Boost b = Boost.load((CompoundTag) t);
            if (b != null) {
                boosts.add(b);
            }
        });
        brewed = tag.getInt("Brewed");
        bestGrade = tag.contains("BestGrade") ? tag.getInt("BestGrade") : -1;
        swallowed = tag.getInt("Swallowed");
        eaten = tag.getInt("Eaten");
        dirty = true;
    }
}

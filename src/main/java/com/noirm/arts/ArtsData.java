package com.noirm.arts;

import com.noirm.clan.Nature;
import com.noirm.nature.NatureTraining;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's solo transformations (saved in {@code ShinobiData}): the chakra shapes they've trained
 * and how far ({@link ShapeTraining}), the shape + nature fusions they own, and the fusion trial for
 * a combined nature they may be in the middle of ({@link FusionTrial}). Synced to the client in
 * {@code PowerPackets.SyncPowers} (as {@link ArtsSync}).
 */
public final class ArtsData {
    /** Stages done per shape: 0 (untrained) to {@link ShapeTraining#STAGES}. */
    private final EnumMap<ChakraShape, Integer> stage = new EnumMap<>(ChakraShape.class);
    /** Progress into each shape's current stage (seconds of study at a pace of 1). */
    private final EnumMap<ChakraShape, Float> progress = new EnumMap<>(ChakraShape.class);
    /** The shape being trained (the last one that moved, or the one picked). */
    private ChakraShape activeShape;
    /** Shape + nature fusions owned ({@link #pair}). */
    private final Set<Integer> fused = new LinkedHashSet<>();
    /** Progress of shape + nature fusions under way (seconds). */
    private final Map<Integer, Float> pairProgress = new HashMap<>();
    /** The fusion picked to train (a {@link #pair} key), or -1 for "whichever the setting gives". */
    private int activePair = -1;

    /** The combined nature being fused alone, or null. */
    private Nature trialNature;
    private int trialStage;
    /** Seconds into the trial's current stage (or weaves made, in the proof). */
    private float trialProgress;
    private int trialFails;

    // ---------------------------------------------------------------- live state (not saved)
    /** Game time of the last cast of a jutsu of each nature (the fusion trial's proof). */
    private final Map<Nature, Long> lastNatureCast = new EnumMap<>(Nature.class);
    /** Game time of the last body training (a hit landed) and mind practice (a genjutsu cast or broken). */
    private long lastBody = Long.MIN_VALUE / 2;
    private long lastMind = Long.MIN_VALUE / 2;
    /** What moved at the last check, for the HUD: a shape, a fusion pair, the trial. */
    private int movingShape = -1;
    private int movingPair = -1;
    private boolean movingTrial;
    private boolean dirty = true;

    public static int pair(ChakraShape shape, Nature nature) {
        return shape.ordinal() * 32 + nature.ordinal();
    }

    public static ChakraShape pairShape(int pair) {
        int s = pair / 32;
        return s >= 0 && s < ChakraShape.values().length ? ChakraShape.values()[s] : null;
    }

    public static Nature pairNature(int pair) {
        int n = pair % 32;
        return n >= 0 && n < Nature.values().length ? Nature.values()[n] : null;
    }

    // ---------------------------------------------------------------- shapes

    public int stage(ChakraShape shape) {
        return stage.getOrDefault(shape, 0);
    }

    public void setStage(ChakraShape shape, int value) {
        int v = Math.max(0, Math.min(ShapeTraining.STAGES, value));
        if (v == 0) {
            stage.remove(shape);
        } else {
            stage.put(shape, v);
        }
        dirty = true;
    }

    public float progress(ChakraShape shape) {
        return progress.getOrDefault(shape, 0f);
    }

    public void setProgress(ChakraShape shape, float seconds) {
        if (seconds <= 0f) {
            progress.remove(shape);
        } else {
            progress.put(shape, seconds);
        }
        dirty = true;
    }

    public ChakraShape activeShape() {
        return activeShape;
    }

    public void setActiveShape(ChakraShape shape) {
        if (activeShape != shape) {
            activeShape = shape;
            dirty = true;
        }
    }

    public boolean fused(ChakraShape shape, Nature nature) {
        return fused.contains(pair(shape, nature));
    }

    public Set<Integer> fusedPairs() {
        return fused;
    }

    public void setFused(ChakraShape shape, Nature nature, boolean value) {
        if (value) {
            fused.add(pair(shape, nature));
            pairProgress.remove(pair(shape, nature));
        } else {
            fused.remove(pair(shape, nature));
        }
        dirty = true;
    }

    public float pairProgress(int pair) {
        return pairProgress.getOrDefault(pair, 0f);
    }

    public Map<Integer, Float> allPairProgress() {
        return pairProgress;
    }

    public void setPairProgress(int pair, float seconds) {
        if (seconds <= 0f) {
            pairProgress.remove(pair);
        } else {
            pairProgress.put(pair, seconds);
        }
        dirty = true;
    }

    public int activePair() {
        return activePair;
    }

    public void setActivePair(int pair) {
        if (activePair != pair) {
            activePair = pair;
            dirty = true;
        }
    }

    // ---------------------------------------------------------------- the fusion trial

    public Nature trialNature() {
        return trialNature;
    }

    public int trialStage() {
        return trialStage;
    }

    public float trialProgress() {
        return trialProgress;
    }

    public int trialFails() {
        return trialFails;
    }

    public void startTrial(Nature nature) {
        trialNature = nature;
        trialStage = 0;
        trialProgress = 0f;
        trialFails = 0;
        dirty = true;
    }

    public void clearTrial() {
        trialNature = null;
        trialStage = 0;
        trialProgress = 0f;
        trialFails = 0;
        dirty = true;
    }

    public void setTrialStage(int value) {
        trialStage = Math.max(0, value);
        trialProgress = 0f;
        dirty = true;
    }

    public void setTrialProgress(float value) {
        trialProgress = Math.max(0f, value);
        dirty = true;
    }

    public void addTrialFail() {
        trialFails++;
        dirty = true;
    }

    // ---------------------------------------------------------------- notes

    public long lastNatureCast(Nature nature) {
        return lastNatureCast.getOrDefault(nature, Long.MIN_VALUE / 2);
    }

    public void noteNatureCast(Nature nature, long time) {
        lastNatureCast.put(nature, time);
    }

    public long lastBody() {
        return lastBody;
    }

    public void noteBody(long time) {
        lastBody = time;
    }

    public long lastMind() {
        return lastMind;
    }

    public void noteMind(long time) {
        lastMind = time;
    }

    public int movingShape() {
        return movingShape;
    }

    public int movingPair() {
        return movingPair;
    }

    public boolean movingTrial() {
        return movingTrial;
    }

    /** Remembers what moved at the last check (for the HUD); marks dirty only when it changes. */
    public void setMoving(int shape, int pair, boolean trial) {
        if (shape != movingShape || pair != movingPair || trial != movingTrial) {
            movingShape = shape;
            movingPair = pair;
            movingTrial = trial;
            dirty = true;
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    // ---------------------------------------------------------------- saving

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag stages = new CompoundTag();
        stage.forEach((s, v) -> stages.putInt(s.key(), v));
        tag.put("Stages", stages);
        CompoundTag prog = new CompoundTag();
        progress.forEach((s, v) -> prog.putFloat(s.key(), v));
        tag.put("Progress", prog);
        if (activeShape != null) {
            tag.putString("ActiveShape", activeShape.key());
        }
        tag.put("Fused", new IntArrayTag(fused.stream().mapToInt(Integer::intValue).toArray()));
        CompoundTag pairs = new CompoundTag();
        pairProgress.forEach((p, v) -> pairs.putFloat(String.valueOf(p), v));
        tag.put("PairProgress", pairs);
        tag.putInt("ActivePair", activePair);
        if (trialNature != null) {
            CompoundTag trial = new CompoundTag();
            trial.putString("Nature", trialNature.getSerializedName());
            trial.putInt("Stage", trialStage);
            trial.putFloat("Progress", trialProgress);
            trial.putInt("Fails", trialFails);
            tag.put("Trial", trial);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        stage.clear();
        progress.clear();
        fused.clear();
        pairProgress.clear();
        CompoundTag stages = tag.getCompound("Stages");
        for (String key : stages.getAllKeys()) {
            ChakraShape s = ChakraShape.byKey(key);
            if (s != null && stages.getInt(key) > 0) {
                stage.put(s, Math.min(ShapeTraining.STAGES, stages.getInt(key)));
            }
        }
        CompoundTag prog = tag.getCompound("Progress");
        for (String key : prog.getAllKeys()) {
            ChakraShape s = ChakraShape.byKey(key);
            if (s != null && prog.getFloat(key) > 0f) {
                progress.put(s, prog.getFloat(key));
            }
        }
        activeShape = ChakraShape.byKey(tag.getString("ActiveShape"));
        for (int p : tag.getIntArray("Fused")) {
            if (pairShape(p) != null && pairNature(p) != null) {
                fused.add(p);
            }
        }
        CompoundTag pairs = tag.getCompound("PairProgress");
        for (String key : pairs.getAllKeys()) {
            try {
                int p = Integer.parseInt(key);
                if (pairShape(p) != null && pairNature(p) != null && pairs.getFloat(key) > 0f) {
                    pairProgress.put(p, pairs.getFloat(key));
                }
            } catch (NumberFormatException ignored) {
                // a broken key: skip it
            }
        }
        activePair = tag.contains("ActivePair", Tag.TAG_INT) ? tag.getInt("ActivePair") : -1;
        trialNature = null;
        trialStage = 0;
        trialProgress = 0f;
        trialFails = 0;
        if (tag.contains("Trial", Tag.TAG_COMPOUND)) {
            CompoundTag trial = tag.getCompound("Trial");
            trialNature = NatureTraining.byName(trial.getString("Nature"));
            trialStage = trial.getInt("Stage");
            trialProgress = trial.getFloat("Progress");
            trialFails = trial.getInt("Fails");
        }
        movingShape = -1;
        movingPair = -1;
        movingTrial = false;
        dirty = true;
    }
}

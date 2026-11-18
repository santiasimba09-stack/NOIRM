package com.noirm.hiraishin;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Every formula mark in the world, by owner (players and the NPC legends who know the Flying
 * Raijin). Saved with the world ({@code data/noirm_hiraishin.dat}), so a mark written on a wall is
 * still there tomorrow, until it fades ({@link HiraishinMark#expires}). The rules (cap, how long marks
 * last) are in {@link HiraishinMarking}; this class only keeps them.
 */
public final class HiraishinMarks extends SavedData {
    private static final String NAME = "noirm_hiraishin";

    private final Map<UUID, List<HiraishinMark>> marks = new HashMap<>();
    private final Map<UUID, Integer> nextId = new HashMap<>();
    private final Map<UUID, Integer> selected = new HashMap<>();
    /** Owners whose client needs a fresh copy of their marks. */
    final Set<UUID> changed = new HashSet<>();

    public static HiraishinMarks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HiraishinMarks::load, HiraishinMarks::new, NAME);
    }

    /** {@code owner}'s marks, oldest first. */
    public List<HiraishinMark> marks(UUID owner) {
        List<HiraishinMark> list = marks.get(owner);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public Set<UUID> owners() {
        return Collections.unmodifiableSet(marks.keySet());
    }

    public HiraishinMark find(UUID owner, int id) {
        for (HiraishinMark mark : marks(owner)) {
            if (mark.id() == id) {
                return mark;
            }
        }
        return null;
    }

    /**
     * Adds a mark, keeping at most {@code cap}: the oldest fades first (a kunai's mark too; the kunai
     * stays where it is). A mark on the same entity or the same block face is refreshed instead.
     * Returns the new (or refreshed) mark and the ones that faded to make room ({@code faded}).
     */
    public HiraishinMark add(UUID owner, HiraishinMark.Kind kind, ResourceKey<Level> dim, Vec3 pos, Direction face, UUID entity,
                             long now, long expires, int cap, List<HiraishinMark> faded) {
        List<HiraishinMark> list = marks.computeIfAbsent(owner, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            HiraishinMark old = list.get(i);
            boolean same = entity != null ? entity.equals(old.entity()) && old.kind() == kind
                    : old.kind() == HiraishinMark.Kind.BLOCK && kind == HiraishinMark.Kind.BLOCK && old.dim().equals(dim)
                    && old.pos().distanceToSqr(pos) < 0.04 && old.face() == face;
            if (same) {
                HiraishinMark refreshed = new HiraishinMark(old.id(), kind, dim, pos, face, entity, now, expires);
                list.remove(i);
                list.add(refreshed); // now the newest
                touch(owner);
                return refreshed;
            }
        }
        while (list.size() >= Math.max(1, cap)) {
            faded.add(list.remove(0));
        }
        int id = nextId.merge(owner, 1, Integer::sum);
        HiraishinMark mark = new HiraishinMark(id, kind, dim, pos, face, entity, now, expires);
        list.add(mark);
        selected.put(owner, id);
        touch(owner);
        return mark;
    }

    public boolean remove(UUID owner, int id) {
        return removeIf(owner, m -> m.id() == id) > 0;
    }

    public int removeIf(UUID owner, Predicate<HiraishinMark> test) {
        List<HiraishinMark> list = marks.get(owner);
        if (list == null) {
            return 0;
        }
        int before = list.size();
        list.removeIf(test);
        int removed = before - list.size();
        if (list.isEmpty()) {
            marks.remove(owner);
        }
        if (removed > 0) {
            touch(owner);
        }
        return removed;
    }

    /** Every owner's marks matching {@code test} go (a kunai picked up, a body that died). */
    public int removeEverywhere(Predicate<HiraishinMark> test) {
        int removed = 0;
        for (UUID owner : new ArrayList<>(marks.keySet())) {
            removed += removeIf(owner, test);
        }
        return removed;
    }

    public int clear(UUID owner) {
        List<HiraishinMark> list = marks.remove(owner);
        selected.remove(owner);
        touch(owner);
        return list == null ? 0 : list.size();
    }

    /** Replaces a mark with an updated copy (same id), e.g. a moving kunai's new position. */
    public void update(UUID owner, HiraishinMark mark, boolean notify) {
        List<HiraishinMark> list = marks.get(owner);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == mark.id()) {
                list.set(i, mark);
                if (notify) {
                    touch(owner);
                } else {
                    setDirty();
                }
                return;
            }
        }
    }

    /** The mark the cycle key last picked (or the newest), or null. */
    public HiraishinMark selected(UUID owner) {
        List<HiraishinMark> list = marks.get(owner);
        if (list == null || list.isEmpty()) {
            return null;
        }
        Integer id = selected.get(owner);
        HiraishinMark mark = id == null ? null : find(owner, id);
        return mark != null ? mark : list.get(list.size() - 1);
    }

    /** Picks the next mark (newest → oldest, wrapping); returns it. */
    public HiraishinMark cycle(UUID owner) {
        List<HiraishinMark> list = marks.get(owner);
        if (list == null || list.isEmpty()) {
            return null;
        }
        HiraishinMark current = selected(owner);
        int index = list.indexOf(current);
        HiraishinMark next = list.get(Math.floorMod(index - 1, list.size()));
        selected.put(owner, next.id());
        touch(owner);
        return next;
    }

    public void select(UUID owner, int id) {
        selected.put(owner, id);
        touch(owner);
    }

    private void touch(UUID owner) {
        changed.add(owner);
        setDirty();
    }

    // ---------------------------------------------------------------- saving

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag owners = new ListTag();
        marks.forEach((owner, list) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putInt("Next", nextId.getOrDefault(owner, 0));
            ListTag items = new ListTag();
            list.forEach(m -> items.add(m.save()));
            entry.put("Marks", items);
            owners.add(entry);
        });
        tag.put("Owners", owners);
        return tag;
    }

    public static HiraishinMarks load(CompoundTag tag) {
        HiraishinMarks data = new HiraishinMarks();
        for (Tag t : tag.getList("Owners", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            UUID owner = entry.getUUID("Owner");
            List<HiraishinMark> list = new ArrayList<>();
            for (Tag m : entry.getList("Marks", Tag.TAG_COMPOUND)) {
                list.add(HiraishinMark.load((CompoundTag) m));
            }
            if (!list.isEmpty()) {
                data.marks.put(owner, list);
            }
            data.nextId.put(owner, entry.getInt("Next"));
        }
        return data;
    }
}

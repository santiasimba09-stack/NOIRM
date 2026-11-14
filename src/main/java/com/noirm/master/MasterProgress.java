package com.noirm.master;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A player's standing with the masters (part of {@code ShinobiData}): whose tests they passed,
 * which lessons they learned ({@code "<master>/<lesson>"}), and the styles masters granted them.
 */
public final class MasterProgress {
    private final Set<String> passed = new LinkedHashSet<>();
    private final Set<String> lessons = new LinkedHashSet<>();
    private final Set<String> styles = new LinkedHashSet<>();

    public boolean passed(String master) {
        return passed.contains(master);
    }

    public boolean pass(String master) {
        return passed.add(master);
    }

    public boolean learned(String master, String lesson) {
        return lessons.contains(master + "/" + lesson);
    }

    public void learn(String master, String lesson) {
        lessons.add(master + "/" + lesson);
    }

    /** A style a master lets you use regardless of its taijutsu requirement. */
    public boolean hasStyle(String style) {
        return styles.contains(style);
    }

    public boolean grantStyle(String style) {
        return styles.add(style);
    }

    public Set<String> passedTests() {
        return Collections.unmodifiableSet(passed);
    }

    public void clear() {
        passed.clear();
        lessons.clear();
        styles.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Passed", list(passed));
        tag.put("Lessons", list(lessons));
        tag.put("Styles", list(styles));
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        read(tag.getList("Passed", Tag.TAG_STRING), passed);
        read(tag.getList("Lessons", Tag.TAG_STRING), lessons);
        read(tag.getList("Styles", Tag.TAG_STRING), styles);
    }

    private static ListTag list(Set<String> values) {
        ListTag list = new ListTag();
        values.forEach(v -> list.add(StringTag.valueOf(v)));
        return list;
    }

    private static void read(ListTag list, Set<String> into) {
        list.forEach(t -> into.add(t.getAsString()));
    }
}

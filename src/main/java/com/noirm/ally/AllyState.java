package com.noirm.ally;

import com.noirm.clan.Nature;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a crew mate carries on top of being a ninja: the tactics, job and behaviour their leader
 * set ({@link Tactics}, {@link Occupation}, {@link Behaviour}), the post they guard, the nature
 * they're being taught and the ones queued after it ({@link NatureTeaching}), the jutsu waiting
 * on a nature, the natures they learned that way (kept when their clan's natures are re-applied
 * on load), the nature energy they gather meditating and the loot a gatherer carries.
 * Saved in the ninja's NBT under {@code "Ally"}.
 */
public final class AllyState {
    private Tactics.Range range = Tactics.Range.CLOSE;
    private Tactics.JutsuUse jutsuUse = Tactics.JutsuUse.FREE;
    /** The nature being taught, or null. */
    private Nature lesson;
    /** Seconds of good study put into {@link #lesson} so far (see {@link NatureTeaching#secondsNeeded}). */
    private float lessonSeconds;
    /** Natures learned from a teacher, with their affinity. */
    private final EnumMap<Nature, Float> learned = new EnumMap<>(Nature.class);
    private Occupation occupation = Occupation.NONE;
    private Behaviour behaviour = Behaviour.DEFENSIVE;
    /** Where they guard or patrol (null: no post yet). */
    private BlockPos post;
    /** Natures to teach after the current lesson, in order. */
    private final List<Nature> queue = new ArrayList<>();
    /** Jutsu they'll learn as soon as they have the nature it needs. */
    private final List<ResourceLocation> pendingJutsu = new ArrayList<>();
    /** Nature energy (0–{@link CrewBrain#MAX_NATURE_ENERGY}) gathered meditating. */
    private float natureEnergy;
    /** What a gatherer is carrying to you. */
    private final List<ItemStack> carried = new ArrayList<>();

    // Not saved: the brain's short-term memory.
    /** Game time until which they don't pick new fights (after "fall back"). */
    public long holdFireUntil;
    /** Where they were at the last progress check, and for how long they've been stuck. */
    public double lastX, lastZ;
    public int stuckSeconds;
    /** When a scout last reported, and the patrol point they're walking to. */
    public long lastReport;
    public BlockPos patrolPoint;

    public Occupation occupation() {
        return occupation;
    }

    public void setOccupation(Occupation occupation) {
        this.occupation = occupation == null ? Occupation.NONE : occupation;
    }

    public Behaviour behaviour() {
        return behaviour;
    }

    public void setBehaviour(Behaviour behaviour) {
        this.behaviour = behaviour == null ? Behaviour.DEFENSIVE : behaviour;
    }

    public BlockPos post() {
        return post;
    }

    public void setPost(BlockPos post) {
        this.post = post == null ? null : post.immutable();
        patrolPoint = null;
    }

    public List<Nature> queue() {
        return queue;
    }

    public List<ResourceLocation> pendingJutsu() {
        return pendingJutsu;
    }

    public float natureEnergy() {
        return natureEnergy;
    }

    public void setNatureEnergy(float value) {
        natureEnergy = Math.max(0f, Math.min(CrewBrain.MAX_NATURE_ENERGY, value));
    }

    public List<ItemStack> carried() {
        return carried;
    }

    public Tactics.Range range() {
        return range;
    }

    public void setRange(Tactics.Range range) {
        this.range = range == null ? Tactics.Range.CLOSE : range;
    }

    public Tactics.JutsuUse jutsuUse() {
        return jutsuUse;
    }

    public void setJutsuUse(Tactics.JutsuUse use) {
        this.jutsuUse = use == null ? Tactics.JutsuUse.FREE : use;
    }

    public Nature lesson() {
        return lesson;
    }

    public float lessonSeconds() {
        return lessonSeconds;
    }

    /** Starts (or, with null, ends) a lesson; progress starts from zero. */
    public void setLesson(Nature nature) {
        lesson = nature;
        lessonSeconds = 0f;
    }

    public void setLessonSeconds(float seconds) {
        lessonSeconds = Math.max(0f, seconds);
    }

    public Map<Nature, Float> learned() {
        return learned;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Range", range.name());
        tag.putString("JutsuUse", jutsuUse.name());
        if (lesson != null) {
            tag.putString("Lesson", lesson.getSerializedName());
            tag.putFloat("LessonSeconds", lessonSeconds);
        }
        CompoundTag natures = new CompoundTag();
        learned.forEach((nature, affinity) -> natures.putFloat(nature.getSerializedName(), affinity));
        tag.put("Learned", natures);
        tag.putString("Occupation", occupation.name());
        tag.putString("Behaviour", behaviour.name());
        if (post != null) {
            tag.putLong("Post", post.asLong());
        }
        ListTag queued = new ListTag();
        queue.forEach(n -> queued.add(StringTag.valueOf(n.getSerializedName())));
        tag.put("Queue", queued);
        ListTag pending = new ListTag();
        pendingJutsu.forEach(id -> pending.add(StringTag.valueOf(id.toString())));
        tag.put("PendingJutsu", pending);
        tag.putFloat("NatureEnergy", natureEnergy);
        ListTag items = new ListTag();
        carried.forEach(stack -> items.add(stack.save(new CompoundTag())));
        tag.put("Carried", items);
        return tag;
    }

    public void load(CompoundTag tag) {
        range = parse(Tactics.Range.class, tag.getString("Range"), Tactics.Range.CLOSE);
        jutsuUse = parse(Tactics.JutsuUse.class, tag.getString("JutsuUse"), Tactics.JutsuUse.FREE);
        lesson = nature(tag.getString("Lesson"));
        lessonSeconds = lesson == null ? 0f : Math.max(0f, tag.getFloat("LessonSeconds"));
        learned.clear();
        CompoundTag natures = tag.getCompound("Learned");
        for (String key : natures.getAllKeys()) {
            Nature nature = nature(key);
            if (nature != null) {
                learned.put(nature, natures.getFloat(key));
            }
        }
        occupation = parse(Occupation.class, tag.getString("Occupation"), Occupation.NONE);
        behaviour = parse(Behaviour.class, tag.getString("Behaviour"), Behaviour.DEFENSIVE);
        post = tag.contains("Post") ? BlockPos.of(tag.getLong("Post")) : null;
        queue.clear();
        tag.getList("Queue", Tag.TAG_STRING).forEach(t -> {
            Nature n = nature(t.getAsString());
            if (n != null && !queue.contains(n)) {
                queue.add(n);
            }
        });
        pendingJutsu.clear();
        tag.getList("PendingJutsu", Tag.TAG_STRING).forEach(t -> {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                pendingJutsu.add(id);
            }
        });
        natureEnergy = tag.getFloat("NatureEnergy");
        carried.clear();
        tag.getList("Carried", Tag.TAG_COMPOUND).forEach(t -> {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (!stack.isEmpty()) {
                carried.add(stack);
            }
        });
    }

    public static Nature nature(String name) {
        for (Nature nature : Nature.values()) {
            if (nature.getSerializedName().equals(name)) {
                return nature;
            }
        }
        return null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return name.isEmpty() ? fallback : Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

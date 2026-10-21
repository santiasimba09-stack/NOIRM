package com.noirm.ally;

import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Teaching a crew mate one of your chakra natures. It takes time, but it moves whenever you're
 * near each other: at full pace under the <b>Train</b> or <b>Meditate</b> order, at half pace
 * under any other order, faster when you fight side by side, when you've mastered the nature,
 * and when you meditate together. A basic nature takes two Minecraft days of full-pace study, a
 * combined one longer.
 *
 * <p>Rules: you must own the nature and they must lack it. A combined nature (Wood = Earth +
 * Water…) needs its parts first, but they can be queued ahead of it. One lesson at a time; more
 * wait in a queue and start by themselves. When a lesson ends they gain the nature
 * ({@link Clan#TRAINED_AFFINITY} for a basic one; a combined one at their weakest part's
 * strength), its starting jutsu, and any jutsu you asked them to learn that needed it.
 */
public final class NatureTeaching {
    /** Seconds of full-pace study for a basic nature: 2 Minecraft days (a day is 1,200 seconds). */
    public static final float BASIC_SECONDS = 2400f;
    /** Pace under orders other than Train and Meditate. */
    public static final float CASUAL_RATE = 0.5f;
    /** Meditating near them (with them) doubles the pace. */
    public static final float MEDITATE_TOGETHER_BONUS = 2f;
    /** A two-part nature takes this much longer, a three-part one (Dust) more still. */
    public static final float COMBINED_FACTOR = 1.25f;
    public static final float TRIPLE_FACTOR = 1.5f;
    /** You must be within this many blocks for the lesson to move. */
    public static final double TEACH_RANGE = 16.0;
    /** Fighting side by side teaches faster (and counts under any order). */
    public static final float FIGHT_BONUS = 1.5f;
    /** Teaching a nature you've mastered (a jutsu of it at this many ★) is faster. */
    public static final int MASTERED_STARS = 3;
    public static final float MASTERED_BONUS = 1.5f;
    /** Respect gained when they finish. */
    public static final float RESPECT_ON_LEARN = 6f;
    /** Ticks between lesson updates. */
    public static final int INTERVAL = 20;

    private NatureTeaching() {}

    /** Seconds of study (at a rate of 1) the nature takes. */
    public static float secondsNeeded(Nature nature) {
        return BASIC_SECONDS * (nature.tier() >= 3 ? TRIPLE_FACTOR : nature.tier() == 2 ? COMBINED_FACTOR : 1f);
    }

    /** Why {@code teacher} can't start teaching {@code nature} to {@code ally} (a translation key), or "" if they can. */
    public static String blocker(Player teacher, SparringNinja ally, Nature nature) {
        if (ally.jutsuAffinity(nature) > 0f) {
            return "screen.noirm.ally.nature.why.owned";
        }
        if (teacherAffinity(teacher, nature) <= 0f) {
            return "screen.noirm.ally.nature.why.you_lack";
        }
        for (Nature part : nature.components()) {
            if (ally.jutsuAffinity(part) <= 0f) {
                return "screen.noirm.ally.nature.why.parts";
            }
        }
        return "";
    }

    /** {@link #blocker}, but a combined nature's parts may also be in the lesson or the queue ahead of it. */
    static String queueBlocker(Player teacher, SparringNinja ally, Nature nature) {
        String why = blocker(teacher, ally, nature);
        if (!why.equals("screen.noirm.ally.nature.why.parts")) {
            return why;
        }
        AllyState state = ally.allyState();
        for (Nature part : nature.components()) {
            if (ally.jutsuAffinity(part) <= 0f && state.lesson() != part && !state.queue().contains(part)) {
                return why;
            }
        }
        return "";
    }

    /**
     * Starts a lesson, or queues it behind the current one. Returns false (and changes nothing)
     * if a rule blocks it or it's already in hand.
     */
    public static boolean start(Player teacher, SparringNinja ally, Nature nature) {
        AllyState state = ally.allyState();
        if (state.lesson() == nature || state.queue().contains(nature)) {
            return false;
        }
        if (state.lesson() == null) {
            if (!blocker(teacher, ally, nature).isEmpty()) {
                return false;
            }
            state.setLesson(nature);
            teacher.displayClientMessage(Component.translatable("message.noirm.ally.nature.started", ally.getDisplayName(), name(nature)), true);
            return true;
        }
        if (!queueBlocker(teacher, ally, nature).isEmpty()) {
            return false;
        }
        state.queue().add(nature);
        teacher.displayClientMessage(Component.translatable("message.noirm.ally.nature.queued", ally.getDisplayName(), name(nature),
                state.queue().size()), true);
        return true;
    }

    /** Takes a nature out of the queue (and the jutsu waiting on it). */
    public static void dequeue(SparringNinja ally, Nature nature) {
        ally.allyState().queue().remove(nature);
        dropPending(ally, nature);
    }

    /** Stops the lesson (its progress is lost) and starts the next one in the queue. */
    public static void stop(SparringNinja ally) {
        Nature dropped = ally.allyState().lesson();
        ally.allyState().setLesson(null);
        if (dropped != null && ally.jutsuAffinity(dropped) <= 0f) {
            dropPending(ally, dropped);
        }
        next(ally, null);
    }

    /** Starts the first queued nature that can be taught now (dropping ones they already have). */
    static void next(SparringNinja ally, Player teacher) {
        AllyState state = ally.allyState();
        while (state.lesson() == null && !state.queue().isEmpty()) {
            Nature candidate = state.queue().remove(0);
            if (ally.jutsuAffinity(candidate) > 0f) {
                continue;
            }
            state.setLesson(candidate);
            if (teacher != null) {
                teacher.displayClientMessage(Component.translatable("message.noirm.ally.nature.started", ally.getDisplayName(), name(candidate)), true);
            }
        }
    }

    private static void dropPending(SparringNinja ally, Nature nature) {
        ally.allyState().pendingJutsu().removeIf(id -> {
            var def = JutsuData.get(id);
            return def == null || def.nature().orElse(null) == nature;
        });
    }

    /** What's helping or holding the lesson back right now, and the resulting speed (0 = paused). */
    public record Pace(boolean studying, boolean near, boolean fighting, boolean mastered, boolean teacherHas, boolean together, float rate) {
        public static final Pace NONE = new Pace(false, false, false, false, false, false, 0f);
    }

    public static Pace pace(SparringNinja ally, Player leader) {
        Nature nature = ally.allyState().lesson();
        if (nature == null || leader == null) {
            return Pace.NONE;
        }
        boolean studying = ally.order() == Order.TRAIN || ally.order() == Order.MEDITATE;
        boolean near = leader.isAlive() && leader.level() == ally.level() && leader.distanceTo(ally) <= TEACH_RANGE;
        LivingEntity target = ally.getTarget();
        boolean fighting = target != null && target.isAlive() && !(target instanceof TrainingDummy);
        boolean mastered = ShinobiCapability.get(leader)
                .map(data -> NatureSystem.bestMastery(data).getOrDefault(nature, 0) >= MASTERED_STARS).orElse(false);
        boolean teacherHas = teacherAffinity(leader, nature) > 0f;
        boolean together = near && leaderMeditating(leader);
        float rate = 0f;
        if (near && teacherHas) {
            rate = (studying ? 1f : CASUAL_RATE) * (fighting ? FIGHT_BONUS : 1f) * (mastered ? MASTERED_BONUS : 1f)
                    * (together ? MEDITATE_TOGETHER_BONUS : 1f);
        }
        return new Pace(studying, near, fighting, mastered, teacherHas, together, rate);
    }

    /** Every tick for a crew mate with their leader around (from {@code SparringNinja.tickCrew}). */
    public static void tick(SparringNinja ally, Player leader) {
        if (ally.allyState().lesson() != null && ally.tickCount % INTERVAL == 0) {
            study(ally, leader, INTERVAL / 20f);
        }
    }

    /** Puts {@code seconds} of time into the lesson at the current pace. Returns true if it finished. */
    public static boolean study(SparringNinja ally, Player leader, float seconds) {
        Nature nature = ally.allyState().lesson();
        if (nature == null) {
            return false;
        }
        if (ally.jutsuAffinity(nature) > 0f) {
            stop(ally); // they got it some other way
            return false;
        }
        float rate = pace(ally, leader).rate();
        if (rate <= 0f) {
            return false;
        }
        ally.allyState().setLessonSeconds(ally.allyState().lessonSeconds() + seconds * rate);
        if (ally.allyState().lessonSeconds() >= secondsNeeded(nature)) {
            complete(ally, leader, nature);
            return true;
        }
        return false;
    }

    /** The lesson is over: they gain the nature and its starting jutsu. */
    static void complete(SparringNinja ally, Player leader, Nature nature) {
        float affinity = Clan.TRAINED_AFFINITY;
        if (!nature.isBasic()) {
            affinity = Clan.SECONDARY_AFFINITY;
            for (Nature part : nature.components()) {
                affinity = Math.min(affinity, ally.jutsuAffinity(part));
            }
        }
        ally.learnNature(nature, affinity);
        for (ResourceLocation id : JutsuData.startersFor(nature)) {
            ally.teach(id);
        }
        // Jutsu you asked them to learn that were waiting on this nature.
        for (ResourceLocation id : java.util.List.copyOf(ally.allyState().pendingJutsu())) {
            var def = JutsuData.get(id);
            if (def != null && def.nature().orElse(null) == nature) {
                ally.allyState().pendingJutsu().remove(id);
                if (AllyPackets.blockerFor(ally, def).isEmpty()) {
                    ally.teach(id);
                    if (leader != null) {
                        leader.sendSystemMessage(Component.translatable("message.noirm.ally.taught", ally.getDisplayName(),
                                Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(id))).withStyle(ChatFormatting.AQUA));
                    }
                }
            }
        }
        ally.allyState().setLesson(null);
        next(ally, leader);
        AllyMind.respect(ally, RESPECT_ON_LEARN, null);
        if (leader != null) {
            leader.sendSystemMessage(Component.translatable("message.noirm.ally.nature.learned", ally.getDisplayName(), name(nature))
                    .withStyle(ChatFormatting.GOLD));
        }
        ally.level().playSound(null, ally.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 0.7f, 1.2f);
        Vfx.aura(ally.level(), ally.position(), ally.getBbWidth(), ally.getBbHeight(), Vfx.palette(nature), 30);
    }

    /** Whether the leader is meditating (their own meditation, from the senjutsu system). */
    static boolean leaderMeditating(Player leader) {
        return false; // wired to the player's meditation once it lands
    }

    private static float teacherAffinity(Player teacher, Nature nature) {
        return teacher == null ? 0f : ShinobiCapability.get(teacher).map(d -> d.identity().affinity(nature)).orElse(0f);
    }

    private static Component name(Nature nature) {
        return Component.translatable(nature.translationKey()).withStyle(s -> s.withColor(nature.color()));
    }
}

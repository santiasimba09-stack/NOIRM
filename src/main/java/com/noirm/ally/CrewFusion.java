package com.noirm.ally;

import com.noirm.arts.FusionTrial;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;

/**
 * <b>Fusion lessons</b>: a crew mate learns to fuse two (or three) of their own natures into a
 * combined one (a kekkei genkai such as Wood = Earth + Water), even when you don't have it
 * yourself. You can't teach what you don't know, so you <i>guide</i>: they do the work.
 *
 * <p>Rules: they must own every part (born with it or taught by you, see {@link NatureTeaching})
 * and not have the nature yet; only the elemental mixes can be fused (not Yin–Yang); one fusion at
 * a time (it runs beside a normal nature lesson). It moves like a lesson: while you're within
 * {@link #GUIDE_RANGE} blocks, at full pace under Train or Meditate, half pace otherwise, twice as
 * fast while you meditate with them. How fast depends on who they are:
 * <ul>
 *   <li><b>Gifted</b>: their clan has a gift for this mix ({@link #APTITUDE}: a Senju for Wood…),
 *       ×{@link #APTITUDE_RATE};</li>
 *   <li><b>Born</b>: both parts run in their blood (their clan's natures), ×{@link #BORN_RATE};</li>
 *   <li><b>Taught</b>: a part was taught to them, ×{@link #TAUGHT_RATE} (very slow).</li>
 * </ul>
 * At the end they gain the nature (at their weakest part's strength, at most secondary) and its
 * starting jutsu. Saved with the crew mate ({@link AllyState}).
 */
public final class CrewFusion {
    /** Seconds of full-pace study for an ordinary ninja (both parts born) to fuse a two-part nature: 4 Minecraft days. */
    public static final float FUSION_SECONDS = 4800f;
    /** A three-part nature (Dust) takes this much longer. */
    public static final float TRIPLE_FACTOR = 1.5f;
    /** Pace by who they are (see the class comment). */
    public static final float APTITUDE_RATE = 3f;
    public static final float BORN_RATE = 1f;
    public static final float TAUGHT_RATE = 0.35f;
    /** Pace under orders other than Train and Meditate. */
    public static final float CASUAL_RATE = 0.5f;
    /** Meditating with them doubles the pace. */
    public static final float TOGETHER_BONUS = 2f;
    /** You must be within this many blocks for the fusion to move. */
    public static final double GUIDE_RANGE = 16.0;
    /** Respect gained when they succeed. */
    public static final float RESPECT_ON_FUSE = 8f;
    /** Ticks between updates. */
    public static final int INTERVAL = 20;
    /** Clans with a gift for a mix (clan id → natures). Generated clans have none. */
    public static final Map<String, Set<Nature>> APTITUDE = Map.of(
            "senju", Set.of(Nature.WOOD),
            "yuki", Set.of(Nature.ICE),
            "kazekage", Set.of(Nature.MAGNET));

    /** How a crew mate comes to a fusion (the pace multiplier). */
    public enum Kind {
        NONE(0f), GIFTED(APTITUDE_RATE), BORN(BORN_RATE), TAUGHT(TAUGHT_RATE);

        private final float rate;

        Kind(float rate) {
            this.rate = rate;
        }

        public float rate() {
            return rate;
        }

        public String key() {
            return "screen.noirm.ally.fusion.kind." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private CrewFusion() {}

    /** Seconds of study (at a rate of 1) the fusion takes. */
    public static float secondsNeeded(Nature nature) {
        return FUSION_SECONDS * (nature.tier() >= 3 ? TRIPLE_FACTOR : 1f);
    }

    /** Whether their clan has a gift for this mix. */
    public static boolean gifted(SparringNinja ally, Nature nature) {
        return APTITUDE.getOrDefault(ally.clanId(), Set.of()).contains(nature);
    }

    /** Gifted, born or taught (NONE when they lack a part or it can't be fused). */
    public static Kind kind(SparringNinja ally, Nature nature) {
        if (!FusionTrial.fusable(nature)) {
            return Kind.NONE;
        }
        boolean born = true;
        for (Nature part : nature.components()) {
            if (ally.jutsuAffinity(part) <= 0f) {
                return Kind.NONE;
            }
            if (ally.allyState().learned().containsKey(part)) {
                born = false;
            }
        }
        return gifted(ally, nature) ? Kind.GIFTED : born ? Kind.BORN : Kind.TAUGHT;
    }

    /** Why {@code ally} can't start fusing {@code nature} (a translation key), or "" if they can. */
    public static String blocker(SparringNinja ally, Nature nature) {
        if (!FusionTrial.fusable(nature)) {
            return "screen.noirm.ally.fusion.why.not_fusable";
        }
        if (ally.jutsuAffinity(nature) > 0f) {
            return "screen.noirm.ally.nature.why.owned";
        }
        for (Nature part : nature.components()) {
            if (ally.jutsuAffinity(part) <= 0f) {
                return "screen.noirm.ally.fusion.why.parts";
            }
        }
        if (ally.allyState().fusion() != null) {
            return "screen.noirm.ally.fusion.why.busy";
        }
        return "";
    }

    /** Starts a fusion lesson. Returns false (and changes nothing) if a rule blocks it. */
    public static boolean start(Player leader, SparringNinja ally, Nature nature) {
        String why = blocker(ally, nature);
        if (!why.isEmpty()) {
            if (leader != null) {
                leader.displayClientMessage(Component.translatable(why), true);
            }
            return false;
        }
        ally.allyState().setFusion(nature);
        if (leader != null) {
            leader.displayClientMessage(Component.translatable("message.noirm.ally.fusion.started", ally.getDisplayName(), name(nature),
                    Component.translatable(kind(ally, nature).key())), true);
        }
        return true;
    }

    /** Stops the fusion (its progress is lost). */
    public static void stop(SparringNinja ally) {
        ally.allyState().setFusion(null);
    }

    /** What's helping or holding the fusion back right now, and the resulting speed (0 = paused). */
    public record Pace(boolean studying, boolean near, boolean together, Kind kind, float rate) {
        public static final Pace NONE = new Pace(false, false, false, Kind.NONE, 0f);
    }

    public static Pace pace(SparringNinja ally, Player leader) {
        Nature nature = ally.allyState().fusion();
        if (nature == null || leader == null) {
            return Pace.NONE;
        }
        boolean studying = ally.order() == Order.TRAIN || ally.order() == Order.MEDITATE;
        boolean near = leader.isAlive() && leader.level() == ally.level() && leader.distanceTo(ally) <= GUIDE_RANGE;
        boolean together = near && NatureTeaching.leaderMeditating(leader);
        Kind kind = kind(ally, nature);
        float rate = near ? kind.rate() * (studying ? 1f : CASUAL_RATE) * (together ? TOGETHER_BONUS : 1f) : 0f;
        return new Pace(studying, near, together, kind, rate);
    }

    /** Every tick for a crew mate with their leader around (from {@code SparringNinja.tickCrew}). */
    public static void tick(SparringNinja ally, Player leader) {
        if (ally.allyState().fusion() != null && ally.tickCount % INTERVAL == 0) {
            study(ally, leader, INTERVAL / 20f);
        }
    }

    /** Puts {@code seconds} of time into the fusion at the current pace. Returns true if it finished. */
    public static boolean study(SparringNinja ally, Player leader, float seconds) {
        Nature nature = ally.allyState().fusion();
        if (nature == null) {
            return false;
        }
        if (ally.jutsuAffinity(nature) > 0f) {
            stop(ally); // they got it some other way (a lesson from you)
            return false;
        }
        float rate = pace(ally, leader).rate();
        if (rate <= 0f) {
            return false;
        }
        AllyState state = ally.allyState();
        state.setFusionSeconds(state.fusionSeconds() + seconds * rate);
        if (state.fusionSeconds() >= secondsNeeded(nature)) {
            complete(ally, leader, nature);
            return true;
        }
        return false;
    }

    /** The fusion is done: they gain the nature and its starting jutsu. */
    static void complete(SparringNinja ally, Player leader, Nature nature) {
        float affinity = Clan.SECONDARY_AFFINITY;
        for (Nature part : nature.components()) {
            affinity = Math.min(affinity, ally.jutsuAffinity(part));
        }
        ally.learnNature(nature, affinity);
        for (ResourceLocation id : JutsuData.startersFor(nature)) {
            ally.teach(id);
        }
        ally.allyState().setFusion(null);
        AllyMind.respect(ally, RESPECT_ON_FUSE, null);
        if (leader != null) {
            leader.sendSystemMessage(Component.translatable("message.noirm.ally.fusion.done", ally.getDisplayName(), name(nature))
                    .withStyle(ChatFormatting.GOLD));
        }
        ally.level().playSound(null, ally.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 0.8f, 0.9f);
        Vfx.aura(ally.level(), ally.position(), ally.getBbWidth(), ally.getBbHeight(), Vfx.palette(nature), 40);
    }

    private static Component name(Nature nature) {
        return Component.translatable(nature.translationKey()).withStyle(s -> s.withColor(nature.color()));
    }
}

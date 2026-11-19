package com.noirm.arts;

import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.nature.NatureDrills;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * <b>Shape transformation</b>, trained alone. Hold a shape's focus item ({@link ChakraShape#focus})
 * and meditate (N): the shape's drill moves one second of study per second, in three stages like the
 * Rasengan's:
 * <ol>
 *   <li><b>Form</b> (the Rasengan's rotation): give the chakra the shape at all. A trickle of chakra.</li>
 *   <li><b>Power</b>: pour chakra into it ({@link #POWER_DRAIN} a second). Casting a canon jutsu of
 *       the shape while in this stage counts too ({@link #POWER_CAST_SECONDS} each).</li>
 *   <li><b>Containment</b>: hold it at full strength without it bursting: it only moves while your
 *       chakra is above {@link #CONTAIN_MIN_SHARE} of the bar, and faster with more CTL.</li>
 * </ol>
 * Each stage done makes canon jutsu of that shape stronger and cheaper ({@link #CANON_POWER_PER_STAGE},
 * {@link #CANON_COST_CUT_PER_STAGE}) and lets the workshop build bigger jutsu on it. The top step is
 * <b>shape + nature fusion</b>: with a shape at stage 3 and a nature you've mastered (a jutsu of it at
 * ★{@link #FUSION_NATURE_MASTERY}), meditate holding the focus in that nature's drill setting for
 * {@link #FUSION_SECONDS}. A fused pair lets the workshop make S-rank jutsu of that shape and nature
 * (a wind-natured ball: a Wind Rasengan).
 */
public final class ShapeTraining {
    // ---------------------------------------------------------------- tuning
    public static final int STAGES = 3;
    /** Seconds of focus each stage takes (a Minecraft day is 1,200 s): Form, Power, Containment. */
    public static final float[] STAGE_SECONDS = {600f, 600f, 900f};
    /** Chakra per second each stage draws while it moves: Form, Power, Containment. */
    public static final float[] STAGE_DRAIN = {0.2f, 1.5f, 1.0f};
    /** Containment only moves while your chakra is above this share of the bar. */
    public static final float CONTAIN_MIN_SHARE = 0.5f;
    /** Containment pace: CTL / this (between {@link #CONTAIN_MIN_PACE} and {@link #CONTAIN_MAX_PACE}). */
    public static final float CONTAIN_CTL = 20f;
    public static final float CONTAIN_MIN_PACE = 0.5f;
    public static final float CONTAIN_MAX_PACE = 2f;
    /** Seconds of Power a canon jutsu of the shape puts behind you when you cast it in the Power stage. */
    public static final float POWER_CAST_SECONDS = 20f;
    /** Shape + nature fusion: seconds of focus in the nature's setting. */
    public static final float FUSION_SECONDS = 1200f;
    /** The nature must be mastered: a jutsu of it at this many ★. */
    public static final int FUSION_NATURE_MASTERY = 3;
    /** Canon jutsu of a shape you own: +5% power and −4% chakra per stage. */
    public static final float CANON_POWER_PER_STAGE = 0.05f;
    public static final float CANON_COST_CUT_PER_STAGE = 0.04f;
    /** Ticks of meditation between updates. */
    public static final int INTERVAL = 20;

    /** Step names: 0 Form, 1 Power, 2 Containment, 3 nature fusion. */
    public static final String[] STEP_KEYS = {"form", "power", "contain", "fusion"};

    private ShapeTraining() {}

    /** Seconds the stage after {@code done} takes (0 = Form). */
    public static float secondsFor(int done) {
        return STAGE_SECONDS[Math.max(0, Math.min(STAGES - 1, done))];
    }

    /** Progress into the shape's current stage, 0..1 (1 when all stages are done). */
    public static float share(ArtsData arts, ChakraShape shape) {
        int stage = arts.stage(shape);
        return stage >= STAGES ? 1f : arts.progress(shape) / secondsFor(stage);
    }

    /** The shape whose focus item you hold (main hand first), or null. */
    public static ChakraShape held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            for (ChakraShape shape : ChakraShape.values()) {
                if (player.getItemInHand(hand).is(shape.focus())) {
                    return shape;
                }
            }
        }
        return null;
    }

    /** Whether you can use this nature for a shape + nature fusion (owned and mastered). */
    public static boolean natureReady(ShinobiData data, Nature nature, Map<Nature, Integer> best) {
        return data.identity().affinity(nature) > 0f && best.getOrDefault(nature, 0) >= FUSION_NATURE_MASTERY;
    }

    /** Whether the player stands in this nature's drill setting (a combined nature: any of its parts'). */
    public static boolean natureSetting(Player player, Nature nature) {
        if (nature.isElemental() && !nature.isBasic()) {
            for (Nature part : nature.components()) {
                if (NatureDrills.inSetting(player, part)) {
                    return true;
                }
            }
            return false;
        }
        return NatureDrills.inSetting(player, nature);
    }

    // ---------------------------------------------------------------- ticking

    /** Every server tick for every player: while meditating, the shape you hold moves. */
    public static void tick(Player player, ShinobiData data) {
        if (!data.senjutsu().isMeditating()) {
            return;
        }
        int ticks = data.senjutsu().ticks();
        if (ticks > 0 && ticks % INTERVAL == 0) {
            study(player, data, INTERVAL / 20f);
        }
    }

    /** What moved: the shape (ordinal) or -1, and the fusion pair or -1. */
    public record Moved(int shape, int pair) {
        public static final Moved NONE = new Moved(-1, -1);
    }

    /** Puts {@code seconds} of meditation into the shape you hold. Needs the player to be meditating. */
    public static Moved study(Player player, ShinobiData data, float seconds) {
        ArtsData arts = data.arts();
        if (!data.senjutsu().isMeditating()) {
            return Moved.NONE;
        }
        ChakraShape shape = held(player);
        if (shape == null) {
            return Moved.NONE;
        }
        arts.setActiveShape(shape);
        if (arts.stage(shape) < STAGES) {
            return advance(player, data, shape, seconds) ? new Moved(shape.ordinal(), -1) : Moved.NONE;
        }
        int pair = fusionPair(player, data, shape);
        if (pair < 0) {
            return Moved.NONE;
        }
        float before = arts.pairProgress(pair);
        float after = before + seconds;
        arts.setPairProgress(pair, after);
        if (after >= FUSION_SECONDS) {
            fuse(player, data, shape, ArtsData.pairNature(pair));
        } else {
            if ((int) (before / FUSION_SECONDS * 10) != (int) (after / FUSION_SECONDS * 10)) {
                notify(player, Component.translatable("message.noirm.shape.fusion_progress", name(shape),
                        natureName(ArtsData.pairNature(pair)), Math.round(after / FUSION_SECONDS * 100)).withStyle(ChatFormatting.AQUA));
            }
            effect(player, shape, ArtsData.pairNature(pair), false);
        }
        return new Moved(shape.ordinal(), pair);
    }

    /** The fusion pair that can move now for this shape: the picked one if its setting is met, else the first one that is. -1 if none. */
    static int fusionPair(Player player, ShinobiData data, ChakraShape shape) {
        ArtsData arts = data.arts();
        Map<Nature, Integer> best = NatureSystem.bestMastery(data);
        int picked = arts.activePair();
        if (picked >= 0 && ArtsData.pairShape(picked) == shape) {
            Nature n = ArtsData.pairNature(picked);
            if (n != null && !arts.fused(shape, n) && natureReady(data, n, best) && natureSetting(player, n)) {
                return picked;
            }
        }
        for (Nature n : Nature.values()) {
            if (!arts.fused(shape, n) && natureReady(data, n, best) && natureSetting(player, n)) {
                return ArtsData.pair(shape, n);
            }
        }
        return -1;
    }

    /** One step of a stage: pays its chakra, checks containment, moves it. Returns whether it moved. */
    static boolean advance(Player player, ShinobiData data, ChakraShape shape, float seconds) {
        ArtsData arts = data.arts();
        int stage = arts.stage(shape);
        float pace = 1f;
        if (stage == 2) {
            if (data.getChakra() < data.getMaxChakra() * CONTAIN_MIN_SHARE) {
                notify(player, Component.translatable("message.noirm.shape.contain_low", name(shape)).withStyle(ChatFormatting.GRAY));
                return false;
            }
            pace = Math.max(CONTAIN_MIN_PACE, Math.min(CONTAIN_MAX_PACE, data.getControl() / CONTAIN_CTL));
        }
        float drain = STAGE_DRAIN[stage] * seconds;
        if (!player.getAbilities().instabuild && !data.tryConsumeChakra(drain)) {
            notify(player, Component.translatable("message.noirm.shape.no_chakra", name(shape)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        addProgress(player, data, shape, seconds * pace);
        effect(player, shape, null, false);
        return true;
    }

    /** Adds stage progress (seconds) and finishes the stage when it's full. */
    public static void addProgress(Player player, ShinobiData data, ChakraShape shape, float seconds) {
        ArtsData arts = data.arts();
        int stage = arts.stage(shape);
        if (stage >= STAGES) {
            return;
        }
        float needed = secondsFor(stage);
        float before = arts.progress(shape);
        float after = before + seconds;
        if (after >= needed) {
            completeStage(player, data, shape);
            return;
        }
        arts.setProgress(shape, after);
        if ((int) (before / needed * 10) != (int) (after / needed * 10) || before == 0f) { // every 10%
            notify(player, Component.translatable("message.noirm.shape.progress", name(shape),
                    Component.translatable(stepKey(shape, stage)), Math.round(after / needed * 100)).withStyle(ChatFormatting.AQUA));
        }
    }

    /** A stage is done. */
    public static void completeStage(Player player, ShinobiData data, ChakraShape shape) {
        ArtsData arts = data.arts();
        int stage = arts.stage(shape);
        if (stage >= STAGES) {
            return;
        }
        arts.setStage(shape, stage + 1);
        arts.setProgress(shape, 0f);
        player.sendSystemMessage(Component.translatable(stage + 1 >= STAGES ? "message.noirm.shape.mastered" : "message.noirm.shape.stage_done",
                name(shape), Component.translatable(stepKey(shape, stage)), stage + 1, STAGES).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f + 0.1f * stage);
        effect(player, shape, null, true);
        data.markPowersDirty();
    }

    /** A shape + nature fusion is done. */
    public static void fuse(Player player, ShinobiData data, ChakraShape shape, Nature nature) {
        data.arts().setFused(shape, nature, true);
        if (data.arts().activePair() == ArtsData.pair(shape, nature)) {
            data.arts().setActivePair(-1);
        }
        player.sendSystemMessage(Component.translatable("message.noirm.shape.fused", name(shape), natureName(nature)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
        effect(player, shape, nature, true);
        data.markPowersDirty();
    }

    /** A jutsu was cast: in the Power stage, a canon jutsu of the shape you're training counts. */
    public static void onCast(Player player, ShinobiData data, JutsuDefinition def) {
        ArtsData arts = data.arts();
        ChakraShape active = arts.activeShape();
        if (active != null && arts.stage(active) == 1 && active.matches(def)) {
            addProgress(player, data, active, POWER_CAST_SECONDS);
        }
    }

    // ---------------------------------------------------------------- canon jutsu of a shape you own

    /** Power multiplier for a canon jutsu of a shape you've trained (1 if none). */
    public static float canonPower(ShinobiData data, JutsuDefinition def) {
        ChakraShape shape = ChakraShape.of(def);
        return shape == null ? 1f : 1f + CANON_POWER_PER_STAGE * data.arts().stage(shape);
    }

    /** Cost multiplier for a canon jutsu of a shape you've trained (1 if none). */
    public static float canonCost(ShinobiData data, JutsuDefinition def) {
        ChakraShape shape = ChakraShape.of(def);
        return shape == null ? 1f : 1f - CANON_COST_CUT_PER_STAGE * data.arts().stage(shape);
    }

    /** Commands: sets a shape's stage (and clears the progress). */
    public static void setStage(ShinobiData data, ChakraShape shape, int stage) {
        data.arts().setStage(shape, stage);
        data.arts().setProgress(shape, 0f);
        data.markPowersDirty();
    }

    /** Commands: sets the current stage's progress as a share; 1 finishes the stage. */
    public static void setShare(Player player, ShinobiData data, ChakraShape shape, float share) {
        data.arts().setActiveShape(shape);
        if (data.arts().stage(shape) >= STAGES) {
            return;
        }
        if (share >= 1f) {
            completeStage(player, data, shape);
        } else {
            data.arts().setProgress(shape, Math.max(0f, share) * secondsFor(data.arts().stage(shape)));
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The translation key of a stage's name for this shape (the ball's Form is "Rotation"…). */
    public static String stepKey(ChakraShape shape, int step) {
        if (step == 0) {
            return "shape.noirm." + shape.key() + ".form";
        }
        return "training.noirm.shape.step." + STEP_KEYS[Math.max(0, Math.min(STEP_KEYS.length - 1, step))];
    }

    public static Component name(ChakraShape shape) {
        return Component.translatable(shape.translationKey()).withStyle(s -> s.withColor(shape.color()));
    }

    static Component natureName(Nature nature) {
        return nature == null ? Component.literal("?") : Component.translatable(nature.translationKey()).withStyle(s -> s.withColor(nature.color()));
    }

    private static void notify(Player player, Component message) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(message, true);
        }
    }

    /** The shape forming in front of the meditating hands, in the nature's colours for a fusion. */
    private static void effect(Player player, ChakraShape shape, Nature nature, boolean big) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hands = player.position().add(0, 0.9, 0).add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.5));
        Vfx.Palette palette = nature != null ? Vfx.palette(nature)
                : new Vfx.Palette(0xFFFFFF, shape.color(), (shape.color() >> 1) & 0x7F7F7F, 0xFFFFFF);
        int k = big ? 4 : 1;
        switch (shape) {
            case BALL -> Vfx.burst(level, Vfx.glow(palette.core(), palette.mid(), 0.35f * k, 0.15f, 6), hands, 1, 0, 0);
            case FIELD, WALL -> Vfx.radial(level, Vfx.spark(palette.mid(), 0.08f, 8), hands, 4 * k, 0.4 + 0.2 * k);
            default -> Vfx.burst(level, Vfx.glow(palette.core(), palette.mid(), 0.18f, 0.08f, 5), hands, 2 * k, 0.15, 0.02);
        }
        if (big) {
            Vfx.aura(level, player.position(), player.getBbWidth(), player.getBbHeight(), palette, 30);
        }
    }
}

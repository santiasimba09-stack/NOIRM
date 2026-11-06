package com.noirm.arts;

import com.noirm.clan.Clan;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * <b>Fusing natures alone</b>: the very hard, rare road to a combined nature (Wood, Ice, Lava…) for
 * someone not born to it. Clans that carry one still get it for free.
 *
 * <p><b>To begin:</b> own every part, master each (a jutsu of it at ★{@link #PART_MASTERY}) and have
 * CTL {@link #MIN_CTL}. Then the trial runs over several in-game days, one stage at a time, while you
 * meditate (N):
 * <ol>
 *   <li><b>Attune</b> to each part in turn: meditate in that nature's drill setting ({@link #ATTUNE_SECONDS}).</li>
 *   <li><b>Blend</b>: meditate where two of the parts' settings meet at once (Wood: running water on
 *       bare stone) for {@link #BLEND_SECONDS}. Every {@link #CHECKPOINT_SECONDS} the blend may
 *       <b>fail</b>: chance {@link #FAIL_BASE}, less with CTL and WIL. A failure costs
 *       {@link #FAIL_PROGRESS_LOSS} of the blend so far, {@link #FAIL_HEALTH} of your health (never
 *       the last point) and {@link #FAIL_CHAKRA} of your chakra.</li>
 *   <li><b>Proof</b>: weave the natures together for real: cast a jutsu of each part within
 *       {@link #PROOF_WINDOW_TICKS} ticks of each other, {@link #PROOF_WEAVES} times.</li>
 * </ol>
 * Then the nature is yours (at your weakest part's strength, at most 1.0) with its starting jutsu.
 * A three-part nature (Dust) attunes to three parts and blends {@link #TRIPLE_FACTOR}× longer.
 */
public final class FusionTrial {
    // ---------------------------------------------------------------- tuning
    /** Each part must be mastered: a jutsu of it at this many ★. */
    public static final int PART_MASTERY = 4;
    /** CTL needed to begin. */
    public static final float MIN_CTL = 40f;
    /** Seconds of focus attuning to each part (a Minecraft day). */
    public static final float ATTUNE_SECONDS = 1200f;
    /** Seconds of focus blending (a day and a half). */
    public static final float BLEND_SECONDS = 1800f;
    /** A three-part nature blends this much longer. */
    public static final float TRIPLE_FACTOR = 1.5f;
    /** The blend may fail every this many seconds of it. */
    public static final float CHECKPOINT_SECONDS = 300f;
    /** Failure chance at each checkpoint: base, −per CTL point above {@link #MIN_CTL}, −per WIL point above 10, bounds. */
    public static final float FAIL_BASE = 0.15f;
    public static final float FAIL_PER_CTL = 0.002f;
    public static final float FAIL_PER_WIL = 0.001f;
    public static final float FAIL_MIN = 0.04f;
    public static final float FAIL_MAX = 0.25f;
    /** A failure loses this share of the blend so far… */
    public static final float FAIL_PROGRESS_LOSS = 0.5f;
    /** …this share of your max health (never the last point)… */
    public static final float FAIL_HEALTH = 0.3f;
    /** …and this share of your chakra. */
    public static final float FAIL_CHAKRA = 0.5f;
    /** The proof: weaves needed, and how close together the parts' jutsu must be cast (ticks). */
    public static final int PROOF_WEAVES = 5;
    public static final int PROOF_WINDOW_TICKS = 60;
    /** Ticks of meditation between updates. */
    public static final int INTERVAL = 20;

    public enum Step { ATTUNE, BLEND, PROOF }

    /** One stage of a trial: attune to {@code part}, blend, or prove. */
    public record Stage(Step step, Nature part, float needed) {}

    private FusionTrial() {}

    /** The natures you can fuse alone: the elemental combined ones. */
    public static boolean fusable(Nature nature) {
        return nature != null && !nature.isBasic() && nature.isElemental();
    }

    /** The parts in a fixed order (fire, water, wind, earth, lightning). */
    public static List<Nature> parts(Nature nature) {
        List<Nature> parts = new ArrayList<>(nature.components());
        parts.sort(Comparator.comparingInt(Enum::ordinal));
        return parts;
    }

    /** Every stage of this nature's trial, in order. */
    public static List<Stage> stages(Nature nature) {
        List<Stage> stages = new ArrayList<>();
        for (Nature part : parts(nature)) {
            stages.add(new Stage(Step.ATTUNE, part, ATTUNE_SECONDS));
        }
        stages.add(new Stage(Step.BLEND, null, BLEND_SECONDS * (nature.tier() >= 3 ? TRIPLE_FACTOR : 1f)));
        stages.add(new Stage(Step.PROOF, null, PROOF_WEAVES));
        return stages;
    }

    /** The stage the player's trial is on, or null. */
    public static Stage current(ArtsData arts) {
        if (arts.trialNature() == null) {
            return null;
        }
        List<Stage> stages = stages(arts.trialNature());
        return stages.get(Math.min(stages.size() - 1, arts.trialStage()));
    }

    /** Progress of the current stage, 0..1. */
    public static float share(ArtsData arts) {
        Stage stage = current(arts);
        return stage == null ? 0f : Math.min(1f, arts.trialProgress() / stage.needed());
    }

    /** Failure chance at a blend checkpoint for this player. */
    public static float failChance(ShinobiData data) {
        return failChance(data.getControl(), data.getWillpower());
    }

    /** The same from CTL and WIL (the screen uses the synced stats). */
    public static float failChance(float control, float willpower) {
        float chance = FAIL_BASE - FAIL_PER_CTL * Math.max(0f, control - MIN_CTL)
                - FAIL_PER_WIL * Math.max(0f, willpower - ShinobiData.DEFAULT_STAT);
        return Math.max(FAIL_MIN, Math.min(FAIL_MAX, chance));
    }

    /** Why the player can't begin fusing {@code nature} (a message), or null if they can. */
    public static Component blocker(ShinobiData data, Nature nature) {
        if (!fusable(nature)) {
            return Component.translatable("message.noirm.fusion.why.not_fusable", natureName(nature));
        }
        if (data.identity().affinity(nature) > 0f) {
            return Component.translatable("message.noirm.fusion.why.owned", natureName(nature));
        }
        if (data.arts().trialNature() != null) {
            return Component.translatable("message.noirm.fusion.why.busy", natureName(data.arts().trialNature()));
        }
        Map<Nature, Integer> best = NatureSystem.bestMastery(data);
        for (Nature part : parts(nature)) {
            if (data.identity().affinity(part) <= 0f) {
                return Component.translatable("message.noirm.fusion.why.part", natureName(part));
            }
            if (best.getOrDefault(part, 0) < PART_MASTERY) {
                return Component.translatable("message.noirm.fusion.why.mastery", natureName(part), PART_MASTERY, best.getOrDefault(part, 0));
            }
        }
        if (data.getControl() < MIN_CTL) {
            return Component.translatable("message.noirm.fusion.why.ctl", Math.round(MIN_CTL), Math.round(data.getControl()));
        }
        return null;
    }

    /** Begins the trial. Returns false (and tells the player why) if a rule blocks it. */
    public static boolean start(Player player, ShinobiData data, Nature nature) {
        Component why = blocker(data, nature);
        if (why != null) {
            player.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        begin(player, data, nature);
        return true;
    }

    /** Begins the trial without checking the rules (commands, tests). */
    public static void begin(Player player, ShinobiData data, Nature nature) {
        data.arts().startTrial(nature);
        player.sendSystemMessage(Component.translatable("message.noirm.fusion.started", natureName(nature),
                Component.translatable("training.noirm.fusion.tip.attune", natureName(parts(nature).get(0)),
                        Component.translatable("drill.noirm." + parts(nature).get(0).getSerializedName()))).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.2f);
        data.markPowersDirty();
    }

    /** Gives the trial up: its progress is lost. */
    public static boolean abandon(Player player, ShinobiData data) {
        Nature nature = data.arts().trialNature();
        if (nature == null) {
            return false;
        }
        data.arts().clearTrial();
        player.displayClientMessage(Component.translatable("message.noirm.fusion.abandoned", natureName(nature)).withStyle(ChatFormatting.GRAY), false);
        data.markPowersDirty();
        return true;
    }

    // ---------------------------------------------------------------- ticking

    /** Every server tick for every player: while meditating, the trial's stage may move. */
    public static void tick(Player player, ShinobiData data) {
        if (!data.senjutsu().isMeditating() || data.arts().trialNature() == null) {
            return;
        }
        int ticks = data.senjutsu().ticks();
        if (ticks > 0 && ticks % INTERVAL == 0) {
            study(player, data, INTERVAL / 20f, player.getRandom().nextFloat());
        }
    }

    /** Whether the current stage's setting is met right now. */
    public static boolean inSetting(Player player, ArtsData arts) {
        Stage stage = current(arts);
        if (stage == null) {
            return false;
        }
        return switch (stage.step()) {
            case ATTUNE -> NatureDrills.inSetting(player, stage.part());
            case BLEND -> {
                int met = 0;
                for (Nature part : parts(arts.trialNature())) {
                    if (NatureDrills.inSetting(player, part)) {
                        met++;
                    }
                }
                yield met >= 2;
            }
            case PROOF -> false;
        };
    }

    /**
     * Puts {@code seconds} of meditation into the trial (when its setting is met). {@code roll} (0..1)
     * decides a blend checkpoint. Returns whether it moved.
     */
    public static boolean study(Player player, ShinobiData data, float seconds, float roll) {
        ArtsData arts = data.arts();
        Stage stage = current(arts);
        if (stage == null || !data.senjutsu().isMeditating()) {
            return false;
        }
        if (stage.step() == Step.PROOF) {
            notify(player, Component.translatable("message.noirm.fusion.proof_hint").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!inSetting(player, arts)) {
            return false;
        }
        advance(player, data, seconds, roll);
        return true;
    }

    /** Adds stage progress; the blend may fail at a checkpoint; a full stage moves on. */
    static void advance(Player player, ShinobiData data, float seconds, float roll) {
        ArtsData arts = data.arts();
        Stage stage = current(arts);
        float before = arts.trialProgress();
        float after = before + seconds;
        if (stage.step() == Step.BLEND && (int) (before / CHECKPOINT_SECONDS) != (int) (after / CHECKPOINT_SECONDS)) {
            if (checkpoint(player, data, roll)) {
                return;
            }
        }
        if (after >= stage.needed()) {
            nextStage(player, data);
            return;
        }
        arts.setTrialProgress(after);
        if ((int) (before / stage.needed() * 10) != (int) (after / stage.needed() * 10) || before == 0f) {
            notify(player, Component.translatable("message.noirm.fusion.progress", natureName(arts.trialNature()),
                    Component.translatable(stepKey(stage)), Math.round(after / stage.needed() * 100)).withStyle(ChatFormatting.AQUA));
        }
        effect(player, arts.trialNature(), false);
    }

    /** A blend checkpoint: returns true if it failed. */
    public static boolean checkpoint(Player player, ShinobiData data, float roll) {
        if (roll >= failChance(data)) {
            return false;
        }
        fail(player, data);
        return true;
    }

    /** The blend broke: progress lost, a burn of health and chakra. */
    public static void fail(Player player, ShinobiData data) {
        ArtsData arts = data.arts();
        arts.setTrialProgress(arts.trialProgress() * (1f - FAIL_PROGRESS_LOSS));
        arts.addTrialFail();
        float hurt = Math.min(player.getMaxHealth() * FAIL_HEALTH, player.getHealth() - 1f);
        if (hurt > 0f) {
            player.setHealth(player.getHealth() - hurt);
        }
        data.setChakra(data.getChakra() * (1f - FAIL_CHAKRA));
        player.sendSystemMessage(Component.translatable("message.noirm.fusion.failed", natureName(arts.trialNature()),
                Math.round(FAIL_PROGRESS_LOSS * 100)).withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.6f);
        if (player.level() instanceof ServerLevel level) {
            Vec3 at = player.position().add(0, 1, 0);
            Vfx.burst(level, Vfx.smoke(0x151515, 0.8f, 40), at, 14, 0.5, 0.05);
            for (Nature part : parts(arts.trialNature())) {
                Vfx.radial(level, Vfx.spark(Vfx.palette(part).mid(), 0.12f, 10), at, 8, 0.4);
            }
        }
        data.markPowersDirty();
    }

    /** The stage is done: the next one, or the nature. */
    static void nextStage(Player player, ShinobiData data) {
        ArtsData arts = data.arts();
        Nature nature = arts.trialNature();
        List<Stage> stages = stages(nature);
        int next = arts.trialStage() + 1;
        if (next >= stages.size()) {
            complete(player, data);
            return;
        }
        Stage done = stages.get(arts.trialStage());
        arts.setTrialStage(next);
        player.sendSystemMessage(Component.translatable("message.noirm.fusion.stage_done", natureName(nature),
                Component.translatable(stepKey(done)), next, stages.size()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        effect(player, nature, true);
        com.noirm.progress.FuseNaturesTrigger.fire(player, nature.name().toLowerCase(java.util.Locale.ROOT)); // "Born of Nothing"
        data.markPowersDirty();
    }

    /** The trial is done: the nature is yours. */
    public static void complete(Player player, ShinobiData data) {
        Nature nature = data.arts().trialNature();
        if (nature == null) {
            return;
        }
        data.arts().clearTrial();
        float affinity = Clan.SECONDARY_AFFINITY;
        for (Nature part : nature.components()) {
            affinity = Math.min(affinity, data.identity().affinity(part));
        }
        NatureSystem.grant(data, nature, Math.max(Clan.TRAINED_AFFINITY, affinity));
        player.sendSystemMessage(Component.translatable("message.noirm.fusion.done", natureName(nature)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9f, 1f);
        effect(player, nature, true);
        data.markPowersDirty();
    }

    /** A jutsu was cast: in the proof, a jutsu of each part close together is one weave. */
    public static void onCast(Player player, ShinobiData data, JutsuDefinition def) {
        ArtsData arts = data.arts();
        Stage stage = current(arts);
        Nature cast = def.nature().orElse(null);
        if (stage == null || stage.step() != Step.PROOF || cast == null || !arts.trialNature().components().contains(cast)) {
            return;
        }
        long now = player.level().getGameTime();
        arts.noteNatureCast(cast, now);
        for (Nature part : arts.trialNature().components()) {
            if (now - arts.lastNatureCast(part) > PROOF_WINDOW_TICKS) {
                return;
            }
        }
        for (Nature part : arts.trialNature().components()) {
            arts.noteNatureCast(part, Long.MIN_VALUE / 2); // each weave needs fresh casts
        }
        float weaves = arts.trialProgress() + 1f;
        if (weaves >= PROOF_WEAVES) {
            complete(player, data);
            return;
        }
        arts.setTrialProgress(weaves);
        player.displayClientMessage(Component.translatable("message.noirm.fusion.weave", natureName(arts.trialNature()),
                Math.round(weaves), PROOF_WEAVES).withStyle(ChatFormatting.AQUA), true);
        effect(player, arts.trialNature(), false);
    }

    /** Commands: the current stage's progress as a share; 1 or more finishes the stage. */
    public static void setShare(Player player, ShinobiData data, float share) {
        Stage stage = current(data.arts());
        if (stage == null) {
            return;
        }
        if (share >= 1f) {
            nextStage(player, data);
        } else {
            data.arts().setTrialProgress(Math.max(0f, share) * stage.needed());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The translation key of a stage's name. */
    public static String stepKey(Stage stage) {
        return "training.noirm.fusion.step." + stage.step().name().toLowerCase(java.util.Locale.ROOT);
    }

    static Component natureName(Nature nature) {
        return Component.translatable(nature.translationKey()).withStyle(s -> s.withColor(nature.color()));
    }

    private static void notify(Player player, Component message) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(message, true);
        }
    }

    private static void effect(Player player, Nature nature, boolean big) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hands = player.position().add(0, 0.9, 0);
        for (Nature part : parts(nature)) {
            Vfx.Palette p = Vfx.palette(part);
            Vfx.burst(level, Vfx.glow(p.core(), p.mid(), big ? 0.5f : 0.2f, 0.08f, 8), hands, big ? 6 : 1, 0.35, 0.02);
        }
        if (big) {
            Vfx.spiral(level, player.position(), Vfx.palette(nature), 0.8f, 2.2f, 2f);
        }
    }
}

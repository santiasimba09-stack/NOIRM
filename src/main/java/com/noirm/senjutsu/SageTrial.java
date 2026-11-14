package com.noirm.senjutsu;

import com.noirm.entity.MasterNinja;
import com.noirm.master.MasterDefinition;
import com.noirm.master.MasterSystem;
import com.noirm.registry.NoirmEntities;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Stone Hermit's test (a master whose JSON says {@code "trial": "meditation"}): meditate by
 * him at the sacred pool until you've gathered {@code test.hits} seconds of stillness, within
 * {@code test.seconds}. Restless spirits ({@link NatureSpirit}) come at you every
 * {@value #SPIRIT_INTERVAL_SECONDS} s (at most {@value #MAX_SPIRITS} at once): a touch stands
 * you up, so drive them off and sit back down. You fail if time runs out, you leave
 * ({@value #LEASH} blocks) or the spirits wear you down to {@value #END_HEALTH} of your health
 * (they stop there: nobody dies in a test). Not saved: a reload ends it.
 */
public final class SageTrial {
    /** Stillness only counts within this many blocks of the hermit. */
    public static final double RANGE = 8;
    public static final double LEASH = 24;
    public static final int FIRST_SPIRIT_SECONDS = 5;
    public static final int SPIRIT_INTERVAL_SECONDS = 12;
    public static final int MAX_SPIRITS = 3;
    public static final float END_HEALTH = 0.3f;
    public static final String KIND = "meditation";

    /** One test in progress. */
    public static final class Trial {
        final Player student;
        final int stillNeeded;
        final int length;
        int ticks;
        int stillTicks;
        int spiritsSent;
        final List<NatureSpirit> spirits = new ArrayList<>();

        Trial(Player student, int stillNeeded, int length) {
            this.student = student;
            this.stillNeeded = stillNeeded;
            this.length = length;
        }

        public int stillSeconds() {
            return stillTicks / 20;
        }

        public int spiritsSent() {
            return spiritsSent;
        }

        public List<NatureSpirit> spirits() {
            return spirits;
        }

        public int secondsLeft() {
            return Math.max(0, (length - ticks) / 20);
        }
    }

    private static final Map<MasterNinja, Trial> TRIALS = new WeakHashMap<>();

    private SageTrial() {}

    /** Does this master test with a meditation trial (not a spar)? */
    public static boolean isTrialMaster(MasterNinja master) {
        MasterDefinition def = master.definition();
        return def != null && KIND.equals(def.trial());
    }

    public static Trial trial(MasterNinja master) {
        return TRIALS.get(master);
    }

    /** The trial {@code player} is taking, or null. */
    public static Trial trialOf(Player player) {
        for (Trial trial : TRIALS.values()) {
            if (trial.student == player) {
                return trial;
            }
        }
        return null;
    }

    public static MasterNinja masterOf(Player player) {
        for (var entry : TRIALS.entrySet()) {
            if (entry.getValue().student == player) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Starts the test (the master screen's button). Returns whether it started. */
    public static boolean start(Player player, MasterNinja master) {
        MasterDefinition def = master.definition();
        if (def == null || player.level().isClientSide()) {
            return false;
        }
        if (TRIALS.containsKey(master) || trialOf(player) != null) {
            player.displayClientMessage(Component.translatable("screen.noirm.master.why.busy").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Trial trial = new Trial(player, Math.max(1, def.test().hits()) * 20, def.test().seconds() * 20);
        TRIALS.put(master, trial);
        com.noirm.combat.CombatSystem.setCombatMode(player, false); // hands down: sit and listen
        player.sendSystemMessage(Component.translatable("message.noirm.sage.trial_start", master.getDisplayName(),
                def.test().hits(), def.test().seconds()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, master.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.8f, 0.7f);
        return true;
    }

    /** Every server tick, for every trial in progress. */
    public static void tickAll() {
        for (MasterNinja master : new ArrayList<>(TRIALS.keySet())) {
            tick(master);
        }
    }

    /** One tick of {@code master}'s trial (public so tests can drive it). */
    public static void tick(MasterNinja master) {
        Trial trial = TRIALS.get(master);
        if (trial == null) {
            return;
        }
        Player student = trial.student;
        if (!master.isAlive() || !student.isAlive() || student.isRemoved() || student.level() != master.level()
                || student.distanceTo(master) > LEASH) {
            end(master, false, "message.noirm.master.called_off");
            return;
        }
        trial.ticks++;
        trial.spirits.removeIf(s -> !s.isAlive());
        if (Meditation.isMeditating(student) && student.distanceTo(master) <= RANGE) {
            trial.stillTicks++;
            if (trial.stillTicks >= trial.stillNeeded) {
                end(master, true, "message.noirm.sage.trial_passed");
                return;
            }
        }
        if (trial.ticks >= trial.length) {
            end(master, false, "message.noirm.master.time_up");
            return;
        }
        int spiritAt = FIRST_SPIRIT_SECONDS * 20 + trial.spiritsSent * SPIRIT_INTERVAL_SECONDS * 20;
        if (trial.ticks >= spiritAt) {
            trial.spiritsSent++;
            if (trial.spirits.size() < MAX_SPIRITS) {
                NatureSpirit spirit = sendSpirit(master, student);
                if (spirit != null) {
                    trial.spirits.add(spirit);
                }
            }
        }
        for (NatureSpirit spirit : trial.spirits) {
            if (spirit.getTarget() != student) {
                spirit.setTarget(student);
            }
        }
        if (trial.ticks % 10 == 0) {
            student.displayClientMessage(Component.translatable("message.noirm.sage.trial_progress",
                    trial.stillSeconds(), trial.stillNeeded / 20, trial.secondsLeft()), true);
        }
    }

    /** A spirit rises out of the rock some way off and comes at the student. */
    static NatureSpirit sendSpirit(MasterNinja master, Player student) {
        if (!(master.level() instanceof ServerLevel level)) {
            return null;
        }
        NatureSpirit spirit = NoirmEntities.NATURE_SPIRIT.get().create(level);
        if (spirit == null) {
            return null;
        }
        double angle = master.getRandom().nextDouble() * Math.PI * 2;
        Vec3 at = student.position().add(Math.cos(angle) * 7, 2.5, Math.sin(angle) * 7);
        spirit.moveTo(at.x, at.y, at.z, master.getRandom().nextFloat() * 360f, 0f);
        spirit.finalizeSpawn(level, level.getCurrentDifficultyAt(spirit.blockPosition()), MobSpawnType.EVENT, null, null);
        spirit.setTarget(student);
        level.addFreshEntity(spirit);
        Vfx.burst(level, Vfx.smoke(0x9AA88A, 0.6f, 24), at, 8, 0.3, 0.04);
        level.playSound(null, spirit.blockPosition(), SoundEvents.VEX_AMBIENT, SoundSource.HOSTILE, 1f, 0.6f);
        return spirit;
    }

    /** Ends the trial: spirits gone, a pass recorded on the student. */
    public static void end(MasterNinja master, boolean passed, String messageKey) {
        Trial trial = TRIALS.remove(master);
        if (trial == null) {
            return;
        }
        for (NatureSpirit spirit : trial.spirits) {
            if (spirit.isAlive()) {
                Vfx.burst(spirit.level(), Vfx.smoke(0x9AA88A, 0.5f, 20), spirit.position(), 6, 0.2, 0.03);
                spirit.discard();
            }
        }
        MasterSystem.onSparEnded(trial.student, master, passed, messageKey);
    }

    /** Health the spirits stop at. */
    public static float floor(Player player) {
        return Math.max(1f, player.getMaxHealth() * END_HEALTH);
    }
}

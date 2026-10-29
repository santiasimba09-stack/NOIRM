package com.noirm.medical;

import com.noirm.clan.Nature;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.entity.MasterNinja;
import com.noirm.master.MasterDefinition;
import com.noirm.master.MasterSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The medical ninja's test ({@code "trial": "patient"}): she brings in a <b>wounded patient</b> who is
 * bleeding out, and you keep them alive for {@code test.seconds} with a calm flow of chakra. Crouch
 * within {@value #FLOW_RANGE} blocks of the patient, hands down (combat mode off): every second
 * that costs {@value #FLOW_CHAKRA} chakra and gives back {@value #FLOW_HEAL} health, while the
 * wound takes {@value #BLEED} a second. A healing jutsu works too. If the patient sinks to
 * {@value #CRITICAL} health, she steps in and you fail; leaving ({@value #LEASH} blocks) fails too.
 *
 * <p>The patient is a plain villager who can't move or be hurt, tagged {@value #PATIENT_TAG}; it
 * leaves with the test. Not saved: a reload ends the test (and a leftover patient never comes back).
 */
public final class MedicTrial {
    public static final String KIND = "patient";
    public static final String PATIENT_TAG = "noirm_patient";
    /** How close you must crouch to the patient for your chakra to reach them. */
    public static final double FLOW_RANGE = 3.0;
    public static final double LEASH = 24;
    /** The patient starts at this share of their health. */
    public static final float START_HEALTH = 0.45f;
    /** Health the wound takes each second. */
    public static final float BLEED = 0.6f;
    /** Health your chakra gives back each second, and the chakra it costs. */
    public static final float FLOW_HEAL = 1.5f;
    public static final float FLOW_CHAKRA = 1.5f;
    /** The patient's health at which the medic steps in (you fail). */
    public static final float CRITICAL = 1.5f;

    /** One test in progress. */
    public static final class Trial {
        final Player student;
        final Villager patient;
        final int length;
        int ticks;
        int flowTicks;

        Trial(Player student, Villager patient, int length) {
            this.student = student;
            this.patient = patient;
            this.length = length;
        }

        public Villager patient() {
            return patient;
        }

        public int flowSeconds() {
            return flowTicks / 20;
        }

        public int secondsLeft() {
            return Math.max(0, (length - ticks) / 20);
        }
    }

    private static final Map<MasterNinja, Trial> TRIALS = new WeakHashMap<>();

    private MedicTrial() {}

    public static boolean isTrialMaster(MasterNinja master) {
        MasterDefinition def = master.definition();
        return def != null && KIND.equals(def.trial());
    }

    public static Trial trial(MasterNinja master) {
        return TRIALS.get(master);
    }

    public static Trial trialOf(Player player) {
        for (Trial trial : TRIALS.values()) {
            if (trial.student == player) {
                return trial;
            }
        }
        return null;
    }

    /** Whether {@code entity} is the patient of a test in progress. */
    public static boolean isActivePatient(Entity entity) {
        for (Trial trial : TRIALS.values()) {
            if (trial.patient == entity) {
                return true;
            }
        }
        return false;
    }

    /** Starts the test (the master screen's button). Returns whether it started. */
    public static boolean start(Player player, MasterNinja master) {
        MasterDefinition def = master.definition();
        if (def == null || !(master.level() instanceof ServerLevel level)) {
            return false;
        }
        if (TRIALS.containsKey(master) || trialOf(player) != null) {
            player.displayClientMessage(Component.translatable("screen.noirm.master.why.busy").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Villager patient = EntityType.VILLAGER.create(level);
        if (patient == null) {
            return false;
        }
        // The patient lies by the medic, between her and the student.
        Vec3 toward = player.position().subtract(master.position());
        Vec3 flat = new Vec3(toward.x, 0, toward.z);
        Vec3 at = master.position().add(flat.lengthSqr() > 1e-4 ? flat.normalize().scale(1.6) : new Vec3(1.6, 0, 0));
        patient.moveTo(at.x, master.getY(), at.z, master.getYRot() + 180f, 0f);
        patient.finalizeSpawn(level, level.getCurrentDifficultyAt(patient.blockPosition()), MobSpawnType.EVENT, null, null);
        patient.setNoAi(true);
        patient.setInvulnerable(true);
        patient.setPersistenceRequired();
        patient.addTag(PATIENT_TAG);
        patient.setCustomName(Component.translatable("entity.noirm.patient"));
        patient.setHealth(Math.max(CRITICAL + 2f, patient.getMaxHealth() * START_HEALTH));
        Trial trial = new Trial(player, patient, def.test().seconds() * 20);
        TRIALS.put(master, trial); // before it joins the world: a patient without a test is turned away
        level.addFreshEntity(patient);
        com.noirm.combat.CombatSystem.setCombatMode(player, false); // hands down: this is healing, not a fight
        player.sendSystemMessage(Component.translatable("message.noirm.medic.trial_start", master.getDisplayName(),
                def.test().seconds()).withStyle(ChatFormatting.GOLD));
        level.playSound(null, master.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6f, 1.4f);
        return true;
    }

    public static void tickAll() {
        for (MasterNinja master : new ArrayList<>(TRIALS.keySet())) {
            tick(master);
        }
    }

    /** Whether the student is pouring calm chakra into the patient right now. */
    public static boolean flowing(Player student, Villager patient) {
        CombatState state = CombatStates.getIfPresent(student);
        return student.isShiftKeyDown() && student.distanceTo(patient) <= FLOW_RANGE && (state == null || !state.isCombatMode());
    }

    /** One tick of {@code master}'s test (public so tests can drive it). */
    public static void tick(MasterNinja master) {
        Trial trial = TRIALS.get(master);
        if (trial == null) {
            return;
        }
        Player student = trial.student;
        Villager patient = trial.patient;
        if (!master.isAlive() || !patient.isAlive() || patient.isRemoved() || !student.isAlive() || student.isRemoved()
                || student.level() != master.level() || student.distanceTo(master) > LEASH) {
            end(master, false, "message.noirm.master.called_off");
            return;
        }
        trial.ticks++;
        boolean flow = flowing(student, patient);
        if (flow) {
            trial.flowTicks++;
        }
        if (trial.ticks % 20 == 0) {
            patient.setHealth(Math.max(0.5f, patient.getHealth() - BLEED));
            if (flow) {
                ShinobiData data = ShinobiCapability.get(student).orElse(null);
                if (data != null && (student.getAbilities().instabuild || data.tryConsumeChakra(FLOW_CHAKRA))) {
                    patient.heal(FLOW_HEAL);
                } else {
                    student.displayClientMessage(Component.translatable("message.noirm.medic.no_chakra").withStyle(ChatFormatting.GRAY), true);
                }
            }
            effects(patient, flow);
        }
        if (patient.getHealth() <= CRITICAL) {
            end(master, false, "message.noirm.medic.trial_lost");
            return;
        }
        if (trial.ticks >= trial.length) {
            end(master, true, "message.noirm.medic.trial_passed");
            return;
        }
        if (trial.ticks % 10 == 0) {
            student.displayClientMessage(Component.translatable(flow ? "message.noirm.medic.trial_progress_flow" : "message.noirm.medic.trial_progress",
                    String.format(java.util.Locale.ROOT, "%.1f", patient.getHealth()), (int) patient.getMaxHealth(), trial.secondsLeft()), true);
        }
    }

    private static void effects(Villager patient, boolean flow) {
        if (!(patient.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 chest = patient.position().add(0, patient.getBbHeight() * 0.55, 0);
        // The wound: a few dark red drops.
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.45f, 0.02f, 0.02f), 1f),
                chest.x, chest.y, chest.z, 3, 0.15, 0.1, 0.15, 0);
        if (flow) {
            Vfx.aura(level, patient.position(), patient.getBbWidth(), patient.getBbHeight(), Vfx.palette(Nature.YANG), 4);
        }
    }

    /** Ends the test: the patient is carried off, a pass recorded on the student. */
    public static void end(MasterNinja master, boolean passed, String messageKey) {
        Trial trial = TRIALS.remove(master);
        if (trial == null) {
            return;
        }
        if (trial.patient.isAlive()) {
            Vfx.burst(trial.patient.level(), Vfx.smoke(0xE8E4DA, 0.5f, 18), trial.patient.position().add(0, 0.8, 0), 8, 0.3, 0.02);
            trial.patient.discard();
        }
        MasterSystem.onSparEnded(trial.student, master, passed, messageKey);
    }
}

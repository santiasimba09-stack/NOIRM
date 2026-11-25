package com.noirm.aburame;

import com.noirm.entity.MasterNinja;
import com.noirm.master.MasterDefinition;
import com.noirm.master.MasterSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shibi's trial ({@code "trial": "drain"}; docs/ABURAME.md, 2.10; the owner chose Shibi as clan master):
 * he fights <b>only with insects and only by draining</b>. You win by Draining him to 0 chakra first,
 * while keeping at least half of the insects you sent out alive. Blades and jutsu don't touch him; if
 * you're Drained, or leave, or time runs out, you fail. Passing is the Clan Master deed.
 */
public final class ShibiTrial {
    public static final String KIND = "drain";
    /** Shibi sends a swarm at you this often, this big. */
    public static final int SWARM_EVERY = 100, SWARM_SIZE = 2500;
    public static final double LEASH = 32;
    /** You must keep this share of what you sent alive. */
    public static final float KEEP = 0.5f;

    public static final class Trial {
        final Player student;
        final int length;
        int ticks;
        int sent;
        int lost;

        Trial(Player student, int length) {
            this.student = student;
            this.length = length;
        }

        public Player student() {
            return student;
        }
    }

    private static final Map<MasterNinja, Trial> TRIALS = new WeakHashMap<>();

    private ShibiTrial() {}

    public static boolean isTrialMaster(MasterNinja master) {
        MasterDefinition def = master.definition();
        return def != null && KIND.equals(def.trial());
    }

    public static Trial trial(MasterNinja master) {
        return TRIALS.get(master);
    }

    public static Trial trialOf(LivingEntity player) {
        for (Trial t : TRIALS.values()) {
            if (t.student == player) {
                return t;
            }
        }
        return null;
    }

    /** Whether {@code entity} is a master in the middle of this trial (blades and jutsu don't touch him). */
    public static boolean shielded(LivingEntity entity) {
        return entity instanceof MasterNinja m && TRIALS.containsKey(m);
    }

    public static boolean start(Player player, MasterNinja master) {
        if (Colony.of(player) == null) {
            player.displayClientMessage(Component.translatable("message.noirm.colony.none").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (TRIALS.containsKey(master) || trialOf(player) != null) {
            return false;
        }
        MasterDefinition def = master.definition();
        master.setChakra(master.maxChakra());
        TRIALS.put(master, new Trial(player, (def == null ? 120 : def.test().seconds()) * 20));
        player.sendSystemMessage(Component.translatable("message.noirm.colony.trial_start", master.getDisplayName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Your swarm lost insects during the trial (counted against you). */
    public static void onStudentLoss(LivingEntity owner, int killed) {
        Trial t = trialOf(owner);
        if (t != null) {
            t.lost += killed;
        }
    }

    public static void onStudentSend(LivingEntity owner, int sent) {
        Trial t = trialOf(owner);
        if (t != null) {
            t.sent += sent;
        }
    }

    public static void tickAll() {
        for (MasterNinja m : Map.copyOf(TRIALS).keySet()) {
            tick(m);
        }
    }

    public static void tick(MasterNinja master) {
        Trial t = TRIALS.get(master);
        if (t == null) {
            return;
        }
        t.ticks++;
        Player s = t.student;
        if (!s.isAlive() || !master.isAlive() || s.distanceTo(master) > LEASH || t.ticks > t.length) {
            end(master, false, "message.noirm.colony.trial_fail");
            return;
        }
        if (Drain.isDrained(s)) {
            end(master, false, "message.noirm.colony.trial_drained");
            return;
        }
        if (master.chakra() <= 0.5f) {
            boolean kept = t.sent == 0 || t.lost <= t.sent * (1f - KEEP);
            end(master, kept, kept ? "message.noirm.colony.trial_pass" : "message.noirm.colony.trial_careless");
            return;
        }
        master.getLookControl().setLookAt(s);
        if (t.ticks % SWARM_EVERY == 1 && master.level() instanceof ServerLevel level) {
            InsectSwarm swarm = InsectSwarm.create(level, master, Species.KIKAICHU, SWARM_SIZE, master.position().add(0, 1.4, 0), SwarmMode.TRAVEL);
            swarm.setTarget(s, SwarmMode.CLING);
            level.addFreshEntity(swarm);
        }
    }

    public static void end(MasterNinja master, boolean passed, String key) {
        Trial t = TRIALS.remove(master);
        if (t == null) {
            return;
        }
        for (InsectSwarm sw : Swarms.owned(master)) {
            sw.discard();
        }
        if (passed) {
            ColonyData c = Colony.of(t.student);
            if (c != null) {
                c.setTrialPassed(true);
                Colony.checkRank(t.student, c);
            }
        }
        master.setChakra(master.maxChakra());
        MasterSystem.onSparEnded(t.student, master, passed, key);
    }
}

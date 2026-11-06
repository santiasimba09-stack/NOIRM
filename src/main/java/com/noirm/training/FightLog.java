package com.noirm.training;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What a player has been doing in the current fight (APX 0.1, kill XP): hits landed, parries,
 * guarded hits, dodges, fast strings, jutsu cast, damage taken and ground covered. When they
 * defeat a ninja, {@link KillXp} turns it into XP for the stats they used most, then the log
 * starts over. It also forgets a fight after {@link KillXp#FORGET_TICKS} of peace. Not saved.
 */
public final class FightLog {
    private int hits;
    private int parries;
    private int guards;
    private int dodges;
    private int perfectDodges;
    private int strings;
    private int jutsu;
    /** Damage taken, in percent of max health (100 = a full health bar). */
    private float damagePercent;
    /** Blocks moved while fighting. */
    private double distance;
    private long lastActive = Long.MIN_VALUE / 2;
    private long lastHit = Long.MIN_VALUE / 2;
    // Movement and chasing (KillXp.tick).
    Vec3 lastPos;
    Vec3 lastFoePos;
    Vec3 chasePos;
    int lastFoeId = -1;
    double lastFoeDistance;

    private void touch(Player player) {
        lastActive = player.level().getGameTime();
    }

    /**
     * A hit landed. Returns the ticks since the previous one (for fast strings). Hits on the
     * training dummy ({@code drill}) still time strings but don't count toward kill XP.
     */
    public long hit(Player player, boolean drill) {
        long now = player.level().getGameTime();
        long since = now - lastHit;
        lastHit = now;
        if (!drill) {
            hits++;
            if (since <= TrainingSystem.FAST_STRING_TICKS) {
                strings++;
            }
            touch(player);
        }
        return since;
    }

    public void parried(Player player) {
        parries++;
        touch(player);
    }

    public void guarded(Player player) {
        guards++;
        touch(player);
    }

    public void dodged(Player player, boolean perfect) {
        if (perfect) {
            perfectDodges++;
        } else {
            dodges++;
        }
        touch(player);
    }

    public void jutsu(Player player) {
        jutsu++;
        touch(player);
    }

    public void damaged(Player player, float percent) {
        damagePercent += Math.max(0f, percent);
        touch(player);
    }

    void moved(double blocks) {
        distance += blocks;
    }

    /** In a fight right now: something was logged within {@link KillXp#FORGET_TICKS}. */
    boolean active(Player player) {
        return player.level().getGameTime() - lastActive < KillXp.FORGET_TICKS;
    }

    public void reset() {
        hits = parries = guards = dodges = perfectDodges = strings = jutsu = 0;
        damagePercent = 0f;
        distance = 0;
        lastActive = Long.MIN_VALUE / 2;
    }

    public int hits() {
        return hits;
    }

    public int parries() {
        return parries;
    }

    public int guards() {
        return guards;
    }

    public int dodges() {
        return dodges;
    }

    public int perfectDodges() {
        return perfectDodges;
    }

    public int strings() {
        return strings;
    }

    public int jutsu() {
        return jutsu;
    }

    public float damagePercent() {
        return damagePercent;
    }

    public double distance() {
        return distance;
    }
}

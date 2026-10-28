package com.noirm.training;

import com.noirm.Noirm;
import com.noirm.combat.Perception;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kill XP (owner, APX 0.1): defeating a ninja (killing them, or making them yield) teaches the
 * stats you used most in that fight. {@link FightLog} counts what you did; when the ninja falls,
 * a pool of {@link #KILL_XP} × how strong they were compared to you is shared between the stats
 * in proportion to how much you used them:
 * <ul>
 *   <li><b>taijutsu:</b> hits landed, parries, blocks;</li>
 *   <li><b>speed:</b> dodges (perfect ones count double), fast strings, ground covered;</li>
 *   <li><b>vitality and durability:</b> damage taken (blocks also count toward durability);</li>
 *   <li><b>reserves and chakra control:</b> jutsu cast;</li>
 *   <li><b>willpower:</b> a bonus on top for beating a foe at least 1.5× your strength.</li>
 * </ul>
 * A short summary tells you what you earned ("Defeated Kenji: +40 Taijutsu, +22 Speed…").
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class KillXp {
    /** XP shared out for beating an equal (× {@link #enemyWorth} ratio, {@link #MIN_WORTH}–{@link #MAX_WORTH}). */
    public static final float KILL_XP = 80f;
    public static final float MIN_WORTH = 0.25f;
    public static final float MAX_WORTH = 3f;
    /** Willpower bonus (share of the pool, on top) for beating a foe this many times your strength or more. */
    public static final float WIL_SHARE = 0.25f;
    public static final float WIL_RATIO = Supplementary.STRONG_FOE_RATIO;
    /** How much each thing you did counts toward its stat. */
    public static final float W_HIT = 1f;
    public static final float W_PARRY = 3f;
    public static final float W_GUARD = 0.5f; // to taijutsu and to durability
    public static final float W_DODGE = 1.5f;
    public static final float W_PERFECT_DODGE = 3f;
    public static final float W_STRING = 0.5f;
    public static final float W_DISTANCE = 0.05f; // per block moved while fighting
    public static final float W_DAMAGE = 0.05f; // per % of max health taken, to vitality and to durability
    public static final float W_JUTSU_RESERVES = 2f;
    public static final float W_JUTSU_CONTROL = 1f;
    /** A fight is forgotten after this long without anything happening (60 s). */
    public static final int FORGET_TICKS = 20 * 60;
    /** Marks a ninja whose defeat was already rewarded (a yielded ninja killed later gives nothing more). */
    public static final String REWARDED_TAG = "noirm_defeat_rewarded";

    private KillXp() {}

    // ------------------------------------------------------------ how much a foe is worth

    /**
     * How strong {@code entity} is, for kill XP: their raw strength (the average of speed and
     * taijutsu, {@link Perception#basePower}). Works for players too, so {@link #worthFactor} is
     * "their strength ÷ yours".
     *
     * <p><b>Ninja classes (D–S):</b> this is the one place to switch to the class. Return a value
     * on the same scale for both sides (e.g. D 10, C 20, B 40, A 80, S 160) and everything else
     * (the pool, willpower, the summary) follows.
     */
    public static float enemyWorth(LivingEntity entity) {
        return Perception.basePower(entity);
    }

    /** The foe's worth ÷ yours, kept within {@link #MIN_WORTH}–{@link #MAX_WORTH}. */
    public static float worthFactor(Player player, LivingEntity enemy) {
        return Mth.clamp(enemyWorth(enemy) / Math.max(1f, enemyWorth(player)), MIN_WORTH, MAX_WORTH);
    }

    // ------------------------------------------------------------ the reward

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !isNinja(dead)) {
            return;
        }
        Player killer = event.getSource().getEntity() instanceof Player p ? p
                : dead.getKillCredit() instanceof Player q ? q : null;
        if (killer != null) {
            onDefeated(killer, dead);
        }
    }

    /** Ninjas count (crew mates of the victor and shadow clones don't). */
    public static boolean isNinja(LivingEntity entity) {
        return entity instanceof SparringNinja && !(entity instanceof ShadowClone);
    }

    /**
     * {@code player} defeated {@code enemy} (killed them, or they yielded). Grants XP to the stats
     * the fight used and tells the player. Returns the XP given per stat (save-data key →
     * amount, biggest first); empty if nothing was given.
     */
    public static Map<String, Float> onDefeated(Player player, LivingEntity enemy) {
        if (player.level().isClientSide() || !isNinja(enemy) || com.noirm.ally.Crew.sameSide(player, enemy)
                || enemy.getPersistentData().getBoolean(REWARDED_TAG)) {
            return Map.of();
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return Map.of();
        }
        enemy.getPersistentData().putBoolean(REWARDED_TAG, true);
        FightLog log = data.training().fight();
        float worth = worthFactor(player, enemy);
        float pool = KILL_XP * worth;

        Map<String, Float> scores = new LinkedHashMap<>();
        scores.put(Stat.TAIJUTSU.key(), log.hits() * W_HIT + log.parries() * W_PARRY + log.guards() * W_GUARD);
        scores.put(Stat.SPEED.key(), log.dodges() * W_DODGE + log.perfectDodges() * W_PERFECT_DODGE
                + log.strings() * W_STRING + (float) log.distance() * W_DISTANCE);
        scores.put(Stat.VITALITY.key(), log.damagePercent() * W_DAMAGE);
        scores.put(Stat.DURABILITY.key(), log.damagePercent() * W_DAMAGE + log.guards() * W_GUARD);
        scores.put(Stat.RESERVES.key(), log.jutsu() * W_JUTSU_RESERVES);
        scores.put(Supplementary.Stat.CONTROL.key(), log.jutsu() * W_JUTSU_CONTROL);
        float total = 0f;
        for (float score : scores.values()) {
            total += score;
        }
        if (total <= 0f) {
            scores.put(Stat.TAIJUTSU.key(), 1f); // nothing logged (one blow?): it was still a fight
            total = 1f;
        }
        Map<String, Float> given = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            float xp = pool * entry.getValue() / total;
            if (xp >= 0.5f) {
                given.put(entry.getKey(), xp);
            }
        }
        if (worth >= WIL_RATIO) {
            given.put(Supplementary.Stat.WILLPOWER.key(), pool * WIL_SHARE);
        }
        List<Map.Entry<String, Float>> sorted = new ArrayList<>(given.entrySet());
        sorted.sort(Map.Entry.<String, Float>comparingByValue().reversed());
        Map<String, Float> result = new LinkedHashMap<>();
        MutableComponent list = Component.empty();
        for (Map.Entry<String, Float> entry : sorted) {
            result.put(entry.getKey(), entry.getValue());
            if (!list.getSiblings().isEmpty()) {
                list.append(", ");
            }
            list.append(Component.translatable("message.noirm.kill_xp.entry", Math.round(entry.getValue()),
                    Component.translatable("stat.noirm." + entry.getKey().toLowerCase(java.util.Locale.ROOT))));
        }
        // Summary first, then the stat-up messages the grants may send.
        player.sendSystemMessage(Component.translatable("message.noirm.kill_xp", enemy.getDisplayName(), list).withStyle(ChatFormatting.YELLOW));
        for (Map.Entry<String, Float> entry : sorted) {
            grant(player, data, entry.getKey(), entry.getValue());
        }
        log.reset();
        return result;
    }

    private static void grant(Player player, ShinobiData data, String key, float xp) {
        for (Stat stat : Stat.values()) {
            if (stat.key().equals(key)) {
                TrainingSystem.grant(player, stat, xp);
                return;
            }
        }
        for (Supplementary.Stat stat : Supplementary.Stat.values()) {
            if (stat.key().equals(key)) {
                Supplementary.grant(player, data, stat, xp);
                return;
            }
        }
    }

    // ------------------------------------------------------------ every tick: ground covered, chasing

    /** Who {@code player} is fighting: the last one they hit, else the last one who hit them (recently). */
    public static LivingEntity currentFoe(Player player) {
        LivingEntity hit = player.getLastHurtMob();
        if (hit != null && hit.isAlive() && player.tickCount - player.getLastHurtMobTimestamp() < Supplementary.FIGHTING_TICKS) {
            return hit;
        }
        LivingEntity by = player.getLastHurtByMob();
        if (by != null && by.isAlive() && player.tickCount - player.getLastHurtByMobTimestamp() < Supplementary.FIGHTING_TICKS) {
            return by;
        }
        return null;
    }

    /** From {@link TrainingSystem#tick}: logs ground covered in a fight, checks chases once a second, forgets old fights. */
    static void tick(Player player, ShinobiData data) {
        FightLog log = data.training().fight();
        Vec3 pos = player.position();
        boolean active = log.active(player);
        if (active && log.lastPos != null) {
            double moved = Math.sqrt(sq(pos.x - log.lastPos.x) + sq(pos.z - log.lastPos.z));
            if (moved < 3) { // not a teleport
                log.moved(moved);
            }
        }
        log.lastPos = pos;
        if (!active && log.hits() + log.dodges() + log.perfectDodges() + log.jutsu() + log.guards() + log.parries() > 0) {
            log.reset(); // a fight long over
        }
        if (player.tickCount % 20 == 0) {
            chaseCheck(player, currentFoe(player));
        }
    }

    /**
     * Once a second: if {@code foe} moved at least {@link TrainingSystem#CHASE_MIN_MOVE} blocks and
     * so did you, and you kept up with or closed the gap (within {@link TrainingSystem#CHASE_MAX_DISTANCE}),
     * the chase trains speed. Returns whether it did. Public for tests.
     */
    public static boolean chaseCheck(Player player, LivingEntity foe) {
        FightLog log = ShinobiCapability.get(player).map(d -> d.training().fight()).orElse(null);
        if (log == null) {
            return false;
        }
        Vec3 me = player.position();
        boolean chased = false;
        if (foe != null && foe.getId() == log.lastFoeId && log.lastFoePos != null && log.chasePos != null) {
            double distance = player.distanceTo(foe);
            double foeMoved = horizontal(foe.position(), log.lastFoePos);
            double iMoved = horizontal(me, log.chasePos);
            if (foeMoved >= TrainingSystem.CHASE_MIN_MOVE && iMoved >= TrainingSystem.CHASE_MIN_MOVE
                    && distance <= TrainingSystem.CHASE_MAX_DISTANCE && distance <= log.lastFoeDistance + 0.5) {
                TrainingSystem.grant(player, Stat.SPEED, TrainingSystem.CHASE_XP * TrainingSystem.opponentFactor(player, foe, Stat.SPEED));
                chased = true;
            }
        }
        log.lastFoeId = foe == null ? -1 : foe.getId();
        log.lastFoePos = foe == null ? null : foe.position();
        log.lastFoeDistance = foe == null ? 0 : player.distanceTo(foe);
        log.chasePos = me;
        return chased;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt(sq(a.x - b.x) + sq(a.z - b.z));
    }

    private static double sq(double v) {
        return v * v;
    }
}

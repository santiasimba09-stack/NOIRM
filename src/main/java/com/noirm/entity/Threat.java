package com.noirm.entity;

import com.noirm.Noirm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Who an NPC fighter goes after. Vanilla mobs stick to whoever hit them first; NOIRM's ninjas
 * (and tailed beasts) keep a threat table instead: every hit adds threat for the attacker
 * (more for harder hits), threat fades over a few seconds, and nearer attackers count more. The
 * fighter turns on the attacker with the most threat, but only when they clearly beat the
 * current target (which gets a bonus), and never sooner than {@link #MIN_HOLD_TICKS} after the
 * last switch, so it doesn't flip back and forth. So: you hit a rogue to point your crew at it,
 * your crew mate engages it, and the rogue turns to fight the one actually hurting it.
 *
 * <p>Server side. Ninjas call {@link #tick} from their AI step; hits are recorded here.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Threat {
    /** Threat per point of damage taken (after guard and toughness); every hit counts at least {@link #MIN_HIT}. */
    public static final float PER_DAMAGE = 1f;
    public static final float MIN_HIT = 1f;
    /** How often (ticks) the table is reconsidered; threat fades by {@link #DECAY} each time (about half every 6 s). */
    public static final int EVAL_TICKS = 5;
    public static final float DECAY = 0.97f;
    /** Distance weight: threat counts 1 / (1 + distance / DISTANCE_SCALE). */
    public static final float DISTANCE_SCALE = 6f;
    /** The current target counts this much extra threat, and then its score × {@link #STICKINESS} (hysteresis). */
    public static final float TARGET_BASE = 3f;
    public static final float STICKINESS = 1.5f;
    /** After a switch, stay on the new target at least this long. */
    public static final int MIN_HOLD_TICKS = 40;
    /** Below this an attacker is forgotten. */
    public static final float FORGET_BELOW = 0.25f;

    private static final class Table {
        final Map<LivingEntity, Float> threat = new WeakHashMap<>();
        long lastSwitch = Long.MIN_VALUE / 2;
    }

    private static final Map<Mob, Table> TABLES = new WeakHashMap<>();

    private Threat() {}

    /** Fighters that use threat: ninjas (and their clones) and tailed beasts. */
    public static boolean tracks(LivingEntity entity) {
        return entity instanceof SparringNinja || entity instanceof TailedBeast;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Mob mob && tracks(mob) && !mob.level().isClientSide()
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != mob) {
            add(mob, attacker, event.getAmount());
        }
    }

    /** {@code attacker} dealt {@code damage} to {@code mob}. */
    public static void add(Mob mob, LivingEntity attacker, float damage) {
        TABLES.computeIfAbsent(mob, m -> new Table()).threat.merge(attacker, Math.max(MIN_HIT, damage * PER_DAMAGE), Float::sum);
    }

    /** The threat {@code attacker} has built up on {@code mob} (for tests and tuning). */
    public static float of(Mob mob, LivingEntity attacker) {
        Table table = TABLES.get(mob);
        return table == null ? 0f : table.threat.getOrDefault(attacker, 0f);
    }

    /** Every tick for a fighter (server): fades threat and switches target when someone else clearly matters more. */
    public static void tick(Mob mob) {
        if (mob.tickCount % EVAL_TICKS != 0) {
            return;
        }
        Table table = TABLES.get(mob);
        if (table == null || table.threat.isEmpty()) {
            return;
        }
        LivingEntity current = mob.getTarget();
        LivingEntity best = null;
        float bestScore = 0f;
        for (Iterator<Map.Entry<LivingEntity, Float>> it = table.threat.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Float> entry = it.next();
            float value = entry.getValue() * DECAY;
            if (value < FORGET_BELOW || !canTurnOn(mob, entry.getKey())) {
                it.remove();
                continue;
            }
            entry.setValue(value);
            float score = score(mob, entry.getKey(), value, entry.getKey() == current);
            if (score > bestScore) {
                best = entry.getKey();
                bestScore = score;
            }
        }
        // Only switch away from a live target: picking one in the first place is the goals' job.
        if (best == null || current == null || best == current || !current.isAlive()) {
            return;
        }
        long now = mob.level().getGameTime();
        float currentScore = score(mob, current, table.threat.getOrDefault(current, 0f), true);
        if (now - table.lastSwitch < MIN_HOLD_TICKS || bestScore <= currentScore) {
            return;
        }
        mob.setTarget(best);
        table.lastSwitch = now;
    }

    private static float score(Mob mob, LivingEntity attacker, float threat, boolean isCurrent) {
        float base = isCurrent ? (threat + TARGET_BASE) * STICKINESS : threat;
        return base / (1f + mob.distanceTo(attacker) / DISTANCE_SCALE);
    }

    /** Someone {@code mob} could fight: alive, here, in range, not on its side, not a creative player. */
    private static boolean canTurnOn(Mob mob, LivingEntity attacker) {
        if (!attacker.isAlive() || attacker.isRemoved() || attacker.level() != mob.level() || !mob.canAttack(attacker)
                || mob.isAlliedTo(attacker) || com.noirm.ally.Crew.sameSide(mob, attacker)) {
            return false;
        }
        double range = mob.getAttribute(Attributes.FOLLOW_RANGE) == null ? 24 : mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return mob.distanceToSqr(attacker) <= range * range;
    }
}

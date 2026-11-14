package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.entity.SparringNinja;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ninja fire: the burning a jutsu lights, and the rules that keep it dangerous without one-shotting
 * a beginner (playthrough, 2026-11-11: a C-class rogue's fire killed a fresh player in full diamond in
 * about 4 seconds, because burning ignores armour and the scorched grass burns round you).
 *
 * <ul>
 *   <li><b>Marked burns.</b> A jutsu that sets someone alight marks them ({@link #ignite}). While the
 *       mark lasts, their burning ({@code on_fire}) and the flames they stand in ({@code in_fire}) count
 *       as a ninja attack: chakra reinforcement takes its share off, and armour and its enchantments
 *       (Fire Protection) count at the ninja-armour share, like any other jutsu.</li>
 *   <li><b>Shorter burns from NPCs.</b> An enemy ninja's fire burns a player for
 *       {@link #NPC_BURN_SCALE} (60%) of the jutsu's {@code fire_seconds}, at least
 *       {@link #MIN_BURN_SECONDS}: a Great Fireball's 6 s becomes 4 s (a beginner has time to reach water).</li>
 *   <li><b>A ceiling per second.</b> In any one second, an enemy ninja's fire (the jutsu's hit, its
 *       burning and the flames around) takes at most {@link #CAP_PER_SECOND} (35%) of a player's max
 *       health: a fresh player (20 health) loses at most 7 a second to fire, so a fireball, its burn and
 *       the burning grass can't kill them before they react. Two or three fireballs in a row still
 *       do. Legendary ninjas are exempt: they are meant to be terrifying.</li>
 * </ul>
 * Fire between players, and players' fire against NPCs, keep the vanilla burn and no ceiling.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class NinjaFire {
    /** An enemy ninja's fire burns a player this share of its seconds… */
    public static final float NPC_BURN_SCALE = 0.6f;
    /** …but at least this long. */
    public static final int MIN_BURN_SECONDS = 2;
    /** In any one second, an enemy ninja's fire takes at most this share of a player's max health. */
    public static final float CAP_PER_SECOND = 0.35f;
    /** The window the ceiling counts over (ticks). */
    public static final int CAP_WINDOW_TICKS = 20;
    /** A burn's mark lasts this long past the burn itself (the flames left on the ground). */
    public static final int MARK_GRACE_TICKS = 60;

    /** Who lit whom, until when, and the fire taken in the current one-second window. */
    private static final class Mark {
        boolean enemyNpc;
        boolean legendary;
        long until;
        long windowStart = Long.MIN_VALUE;
        float takenInWindow;
    }

    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();

    private NinjaFire() {}

    /**
     * {@code owner}'s jutsu sets {@code victim} alight for {@code seconds}: shorter from an enemy NPC
     * against a player, and marked as ninja fire. Returns the seconds it really burns.
     */
    public static int ignite(Entity owner, LivingEntity victim, int seconds) {
        if (seconds <= 0) {
            return 0;
        }
        boolean enemyNpc = isEnemyNpcFire(owner, victim);
        int burn = enemyNpc ? Math.max(MIN_BURN_SECONDS, Math.round(seconds * NPC_BURN_SCALE)) : seconds;
        victim.setSecondsOnFire(burn);
        Mark mark = MARKS.computeIfAbsent(victim, v -> new Mark());
        mark.enemyNpc = enemyNpc;
        mark.legendary = owner instanceof LivingEntity living && com.noirm.bounty.Legendary.isLegendary(living);
        // setSecondsOnFire never shortens a longer burn (Fire Protection may shorten it): mark the burn as it stands.
        mark.until = victim.level().getGameTime() + Math.max(burn * 20L, victim.getRemainingFireTicks()) + MARK_GRACE_TICKS;
        return burn;
    }

    /** Is {@code victim}'s burning (or the flames they stand in) ninja fire right now? */
    public static boolean isNinjaBurn(LivingEntity victim, DamageSource source) {
        if (source.getEntity() != null || !source.is(DamageTypeTags.IS_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.LAVA)) {
            return false;
        }
        Mark mark = MARKS.get(victim);
        return mark != null && victim.level().getGameTime() <= mark.until;
    }

    /** An enemy NPC ninja (not on the player's side) setting a player alight. */
    public static boolean isEnemyNpcFire(Entity owner, LivingEntity victim) {
        return victim instanceof Player player && owner instanceof SparringNinja ninja && !Crew.sameSide(ninja, player);
    }

    /**
     * The per-second ceiling: what is left of {@code amount} (already after armour, Durability and
     * reinforcement) once this second's enemy-ninja fire is counted. Public for tests.
     */
    public static float capped(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof Player) || amount <= 0f) {
            return amount;
        }
        Mark mark = MARKS.get(victim);
        long now = victim.level().getGameTime();
        boolean fireHit = ChakraReinforcement.currentKind(victim) == ChakraReinforcement.Kind.FIRE
                && isEnemyNpcFire(ChakraReinforcement.currentAttacker(victim), victim);
        boolean burn = mark != null && mark.enemyNpc && isNinjaBurn(victim, source);
        if (!fireHit && !burn) {
            return amount;
        }
        if (fireHit) {
            Entity attacker = ChakraReinforcement.currentAttacker(victim);
            if (attacker instanceof LivingEntity living && com.noirm.bounty.Legendary.isLegendary(living)) {
                return amount;
            }
            if (mark == null) {
                mark = MARKS.computeIfAbsent(victim, v -> new Mark());
                mark.enemyNpc = true;
                mark.until = now + MARK_GRACE_TICKS;
            }
        } else if (mark.legendary) {
            return amount;
        }
        if (now - mark.windowStart >= CAP_WINDOW_TICKS) {
            mark.windowStart = now;
            mark.takenInWindow = 0f;
        }
        float room = Math.max(0f, CAP_PER_SECOND * victim.getMaxHealth() - mark.takenInWindow);
        float allowed = Math.min(amount, room);
        mark.takenInWindow += allowed;
        return allowed;
    }

    /** After armour, Durability and reinforcement: the ceiling on an enemy ninja's fire. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.isCanceled()) {
            return;
        }
        float amount = capped(victim, event.getSource(), event.getAmount());
        if (amount != event.getAmount()) {
            if (amount <= 0f) {
                event.setCanceled(true);
            } else {
                event.setAmount(amount);
            }
        }
    }

    /** For tests: forget every mark. */
    public static void clear() {
        MARKS.clear();
    }
}

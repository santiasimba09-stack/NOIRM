package com.noirm.dojutsu;

import com.noirm.combat.Perception;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import com.noirm.player.ShinobiData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;

/**
 * The eyes against <b>everything</b>, not just ninjas. NOIRM fighters telegraph their attacks
 * through the combat system; vanilla mobs and projectiles don't, so this watches them:
 * <ul>
 *   <li><b>Precognition</b> (Sharingan 2+ tomoe, or the Byakugan): a warning before a mob's
 *       attack lands (a melee mob reaching you, a skeleton drawing its bow, a creeper swelling,
 *       a spider about to leap) and before a projectile on a collision course hits you.</li>
 *   <li><b>Perception</b> (the same rule as against ninjas: your perception ÷ theirs): projectiles
 *       slow down as they come near you, so you have time to dodge. Arrows keep their damage.</li>
 * </ul>
 */
public final class ThreatSense {
    /** How far around the player threats are watched. */
    public static final double RANGE = 20;
    /** Projectiles slow down once this close. */
    public static final double SLOW_RADIUS = 8;
    /** A projectile counts as "coming at you" if it will pass this close to your centre. */
    public static final double COLLISION_RADIUS = 1.6;
    /** Warn about a projectile only when it would hit within this many ticks. */
    public static final int PROJECTILE_WARNING_TICKS = 40;
    /** The same mob isn't warned about again for this long. */
    public static final int REWARN_TICKS = 20;
    /** Perception against a mob without NOIRM stats or against an ownerless projectile. */
    private static final float WORLD_PERCEPTION = ShinobiData.DEFAULT_STAT;

    private static final Map<Player, Map<Integer, Long>> WARNED = new WeakHashMap<>();
    private static final Set<Entity> SLOWED = Collections.newSetFromMap(new WeakHashMap<>());

    private ThreatSense() {}

    /** Can this player foresee attacks? */
    public static boolean hasForesight(LivingEntity entity) {
        return Sharingan.activeStage(entity) >= Sharingan.PRECOGNITION_STAGE || Byakugan.isActive(entity);
    }

    /** Every other server tick for each player. */
    public static void tick(Player player) {
        if (player.tickCount % 2 != 0 || player.isSpectator() || player.level().isClientSide()) {
            return;
        }
        boolean foresight = hasForesight(player);
        long now = player.level().getGameTime();
        Map<Integer, Long> warned = WARNED.computeIfAbsent(player, p -> new HashMap<>());
        warned.values().removeIf(at -> now - at > 200);
        AABB area = player.getBoundingBox().inflate(RANGE);

        if (foresight) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area, m -> m.isAlive() && m.getTarget() == player)) {
                int ticks = imminentAttack(mob, player);
                if (ticks >= 0) {
                    warn(player, mob, ticks, warned, now, REWARN_TICKS);
                }
            }
        }
        for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, area,
                p -> p.isAlive() && p.getOwner() != player)) {
            int impact = ticksToImpact(projectile, player);
            if (impact < 0) {
                continue;
            }
            if (foresight && impact <= PROJECTILE_WARNING_TICKS) {
                warn(player, projectile, impact, warned, now, Integer.MAX_VALUE);
            }
            if (projectile.distanceTo(player) <= SLOW_RADIUS && SLOWED.add(projectile)) {
                slow(projectile, player);
            }
        }
    }

    /**
     * Ticks until {@code mob} attacks, or -1 if it isn't about to. These are estimates: vanilla
     * mobs don't announce their attacks, so we read what they visibly do.
     */
    public static int imminentAttack(Mob mob, Player target) {
        if (mob instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            return Math.max(1, Math.round((1f - creeper.getSwelling(1f)) * 30));
        }
        if (mob.isUsingItem()) {
            return Math.max(1, 20 - mob.getTicksUsingItem()); // drawing a bow or crossbow
        }
        double distance = mob.distanceTo(target);
        if (mob instanceof Spider && mob.onGround() && distance > 2 && distance < 4) {
            return 6; // about to leap
        }
        double reach = mob.getBbWidth() * 2 + target.getBbWidth();
        if (mob.distanceToSqr(target) <= reach * reach + 1.5) {
            return 8;
        }
        return -1;
    }

    /** Ticks until {@code projectile} reaches {@code player}, or -1 if it will miss. */
    public static int ticksToImpact(Projectile projectile, Player player) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speedSqr = velocity.lengthSqr();
        if (speedSqr < 0.04) {
            return -1;
        }
        Vec3 toPlayer = player.getBoundingBox().getCenter().subtract(projectile.position());
        double t = toPlayer.dot(velocity) / speedSqr;
        if (t < 0) {
            return -1;
        }
        Vec3 closest = toPlayer.subtract(velocity.scale(t));
        return closest.length() <= COLLISION_RADIUS ? (int) Math.ceil(t) : -1;
    }

    /** How much slower {@code projectile} seems to {@code player}: their perception ÷ the shooter's. */
    public static float projectileSlowFactor(Projectile projectile, Player player) {
        if (projectile.getOwner() instanceof LivingEntity owner) {
            return Perception.slowFactor(player, owner);
        }
        return Math.min(Perception.MAX_SLOW_FACTOR, Math.max(1f, Perception.perception(player) / WORLD_PERCEPTION));
    }

    private static void slow(Projectile projectile, Player player) {
        float factor = projectileSlowFactor(projectile, player);
        if (factor <= 1.05f) {
            return;
        }
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(1f / factor));
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(arrow.getBaseDamage() * factor); // arrow damage scales with speed
        }
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
    }

    private static void warn(Player player, Entity threat, int ticks, Map<Integer, Long> warned, long now, int rewarnTicks) {
        Long last = warned.get(threat.getId());
        if (last != null && now - last < rewarnTicks) {
            return;
        }
        warned.put(threat.getId(), now);
        if (Sharingan.activeStage(player) >= Sharingan.PRECOGNITION_STAGE && threat instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.max(10, ticks + 10), 0, false, false));
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NoirmNetwork.sendToPlayer(serverPlayer, new PowerPackets.Precognition(threat.getId(), ticks));
        }
    }
}

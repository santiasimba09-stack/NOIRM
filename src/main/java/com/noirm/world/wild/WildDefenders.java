package com.noirm.world.wild;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.Threat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Squads (and other friendly groups) defend you: when a wild rogue hurts a player, every wild
 * ninja nearby whose encounter {@code defends_players} (village squads, travellers, clan groups)
 * and who can see the rogue or the player joins the fight <b>against the rogue</b>. They never
 * turn on the player they're defending: a stray hit from that player while they're defending
 * doesn't pull them off the rogue.
 *
 * <p>How: the rogue goes on top of each defender's {@link Threat} table (so the threat system keeps
 * them on it), and each defender's target is set to the rogue unless it's already fighting another
 * rogue. Server side.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class WildDefenders {
    /** Defenders this close to the attacked player (blocks) answer the call. */
    public static final double DEFEND_RANGE = 24;
    /** Extra threat the rogue gets on a defender's table per call (on top of the damage dealt). */
    public static final float DEFEND_THREAT = 6f;
    /** How long (ticks) after the last call a defender stays on the rogue no matter what. */
    public static final int DEFEND_TICKS = 20 * 15;

    /** Who each defender is defending against, and until when (game time). */
    private record Duty(LivingEntity rogue, Player ward, long until) {}

    private static final Map<SparringNinja, Duty> DUTIES = new WeakHashMap<>();

    private WildDefenders() {}

    /** Whether wild ninja {@code ninja}'s group steps in for players (its encounter's {@code defends_players}). */
    public static boolean defendsPlayers(SparringNinja ninja) {
        WildNinjas.Member member = WildNinjas.member(ninja);
        if (member == null || member.hostile) {
            return false;
        }
        EncounterType type = Encounters.get(member.type);
        return type != null && type.defendsPlayers();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || player.isCreative() || player.isSpectator() || !(event.getSource().getEntity() instanceof SparringNinja rogue)
                || !WildNinjas.isRogue(rogue)) {
            return;
        }
        rally((ServerLevel) player.level(), player, rogue, event.getAmount());
    }

    /**
     * {@code rogue} just hurt {@code player} for {@code damage}: every defender around who sees it
     * joins in against the rogue. Returns the ninjas who answered (tests).
     */
    public static List<SparringNinja> rally(ServerLevel level, Player player, LivingEntity rogue, float damage) {
        List<SparringNinja> answered = new ArrayList<>();
        AABB area = player.getBoundingBox().inflate(DEFEND_RANGE);
        long now = level.getGameTime();
        for (SparringNinja ninja : level.getEntitiesOfClass(SparringNinja.class, area, n -> n.isAlive() && defendsPlayers(n))) {
            if (ninja.isYielded() || WildNinjas.sameGroup(ninja, rogue) || ninja.distanceToSqr(player) > DEFEND_RANGE * DEFEND_RANGE
                    || !(ninja.hasLineOfSight(rogue) || ninja.hasLineOfSight(player))) {
                continue;
            }
            Threat.add(ninja, rogue, damage + DEFEND_THREAT);
            LivingEntity current = ninja.getTarget();
            if (current == null || !current.isAlive() || !WildNinjas.isRogue(current)) {
                ninja.setTarget(rogue);
            }
            DUTIES.put(ninja, new Duty(rogue, player, now + DEFEND_TICKS));
            answered.add(ninja);
        }
        return answered;
    }

    /** Whether {@code ninja} is defending someone right now (tests). */
    public static boolean isDefending(SparringNinja ninja) {
        Duty duty = DUTIES.get(ninja);
        return duty != null && duty.rogue.isAlive() && ninja.level().getGameTime() < duty.until;
    }

    /**
     * Every few ticks for each wild ninja (from {@link WildNinjas#tick}): a defender whose target
     * slipped to the player it's defending (a stray hit) goes back to the rogue.
     */
    static void tick(SparringNinja ninja) {
        if (DUTIES.isEmpty()) {
            return;
        }
        Duty duty = DUTIES.get(ninja);
        if (duty == null) {
            return;
        }
        if (!duty.rogue.isAlive() || duty.rogue.isRemoved() || ninja.level().getGameTime() >= duty.until) {
            DUTIES.remove(ninja);
            return;
        }
        LivingEntity target = ninja.getTarget();
        if (target == duty.ward || target == null && !ninja.isYielded()) {
            ninja.setTarget(duty.rogue);
        }
    }
}

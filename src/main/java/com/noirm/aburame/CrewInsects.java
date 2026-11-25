package com.noirm.aburame;

import com.noirm.entity.SparringNinja;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * An Aburame crew mate's insect work (docs/ABURAME.md, 2.12): as a scout it sends a real scout swarm
 * ahead and reports in chat; as a medic it draws poison out of you; when it backs off hurt, it jams.
 */
public final class CrewInsects {
    /** A scout swarm every this many ticks, this far ahead of you. */
    public static final int SCOUT_EVERY = 600;
    public static final double SCOUT_AHEAD = 30;
    public static final int SCOUT_INSECTS = 300;

    private CrewInsects() {}

    /** The scout job (called by CrewBrain). Returns true if it handled it. */
    public static boolean scout(SparringNinja ally, Player leader) {
        if (Colony.of(ally) == null || !(ally.level() instanceof ServerLevel level)) {
            return false;
        }
        if (ally.tickCount % SCOUT_EVERY == 0) {
            Vec3 ahead = leader.position().add(leader.getViewVector(1f).multiply(1, 0, 1).normalize().scale(SCOUT_AHEAD)).add(0, 3, 0);
            InsectSwarm s = Swarms.send(ally, Species.KIKAICHU, SCOUT_INSECTS, ally.position().add(0, 1.5, 0), SwarmMode.TRAVEL, 1f, 1f);
            if (s != null) {
                s.setPark(ahead);
                s.setTarget(null, SwarmMode.SCOUT);
                leader.displayClientMessage(Component.translatable("message.noirm.colony.crew_scout", ally.getDisplayName())
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
        return false; // the usual marking still runs too
    }

    /** Scout reports from a crew mate's swarm go to its leader. */
    public static Player reportTo(LivingEntity owner) {
        if (owner instanceof SparringNinja n && n.leaderId() != null) {
            return owner.level().getPlayerByUUID(n.leaderId());
        }
        return owner instanceof Player p ? p : null;
    }

    /** The medic job: an Aburame draws poison out of you first (every few seconds). */
    public static boolean medic(SparringNinja ally, Player leader) {
        if (Colony.of(ally) == null || leader.distanceTo(ally) > 6) {
            return false;
        }
        if (AburameBehaviors.drawOne(leader)) {
            if (ally.level() instanceof ServerLevel level) {
                level.sendParticles(SwarmFx.DUST, leader.getX(), leader.getY() + 1, leader.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
            }
            leader.displayClientMessage(Component.translatable("message.noirm.colony.crew_draw", ally.getDisplayName()), true);
            return true;
        }
        return false;
    }
}

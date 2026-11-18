package com.noirm.hound;

import com.noirm.ally.Crew;
import com.noirm.entity.SparringNinja;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The hound's nose. An Inuzuka hound tells people apart by smell from far away and can't be fooled
 * by what fools the eyes: invisibility, a crouch in the grass, a smoke body. Every second it sniffs
 * within its stage's range ({@link HoundStage#smell}) and <b>marks</b> any hidden enemy it finds
 * (a glow everyone can see, for a few seconds), growls and tells you. On your order it <b>tracks</b>
 * a scent and leads you there, leaving a faint trail.
 */
public final class HoundSmell {
    /** How long a mark from smelling lasts (ticks). */
    public static final int MARK_TICKS = 70;
    /** Tracking reaches this many times the smelling range. */
    public static final double TRACK_REACH = 4.0;

    /** When each hidden enemy was last pointed out (so you're told once, not every second). */
    private static final Map<LivingEntity, Long> TOLD = new WeakHashMap<>();

    private HoundSmell() {}

    /** Is {@code entity} hiding from eyes? (Invisible, or a crouching enemy player.) */
    public static boolean hidden(LivingEntity entity) {
        return entity.isInvisible() || entity.hasEffect(MobEffects.INVISIBILITY) || entity instanceof Player p && p.isCrouching();
    }

    /** Is {@code entity} an enemy of the hound's side? */
    public static boolean hostile(NinjaHound hound, LivingEntity owner, LivingEntity entity) {
        if (entity == hound || entity == owner || !entity.isAlive() || Crew.sameSide(hound, entity)) {
            return false;
        }
        if (entity instanceof Enemy) {
            return true;
        }
        if (entity instanceof Mob mob && (mob.getTarget() == owner || mob.getTarget() == hound)) {
            return true;
        }
        if (entity instanceof SparringNinja ninja) {
            return !ninja.isResident() && (ninja.getTarget() != null || owner.getLastHurtByMob() == ninja || owner.getLastHurtMob() == ninja);
        }
        return entity instanceof Player && (owner.getLastHurtByMob() == entity || owner.getLastHurtMob() == entity);
    }

    /** One sniff: marks hidden enemies in range. Returns how many it found. */
    public static int sniff(NinjaHound hound, LivingEntity owner) {
        if (!(hound.level() instanceof ServerLevel level) || hound.isHidden()) {
            return 0;
        }
        double range = hound.stage().smell;
        int found = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, hound.getBoundingBox().inflate(range),
                e -> hidden(e) && hostile(hound, owner, e) && e.distanceToSqr(hound) <= range * range)) {
            mark(e, MARK_TICKS);
            found++;
            long now = level.getGameTime();
            Long told = TOLD.get(e);
            if (told == null || now - told > 20 * 15) {
                TOLD.put(e, now);
                level.broadcastEntityEvent(hound, NinjaHound.EVENT_SNIFF);
                hound.playSound(SoundEvents.WOLF_GROWL, 1f, hound.getVoicePitch());
                hound.getLookControl().setLookAt(e);
                if (owner instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.noirm.hound.smells", hound.getDisplayName(),
                            e.getDisplayName(), Math.round(hound.distanceTo(e))), true);
                }
            }
        }
        return found;
    }

    /** A glow that shows the scent through anything (to everyone: your crew sees it too). */
    public static void mark(LivingEntity e, int ticks) {
        MobEffectInstance glow = e.getEffect(MobEffects.GLOWING);
        if (glow == null || glow.getDuration() < ticks) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        }
    }

    /** What to track: the enemy you look at, else the nearest enemy (ninja first) within reach. */
    public static LivingEntity findScent(NinjaHound hound, Player owner) {
        LivingEntity looked = owner instanceof net.minecraft.server.level.ServerPlayer sp ? com.noirm.ally.CrewPackets.lookTarget(sp) : null;
        if (looked != null && !Crew.sameSide(hound, looked)) {
            return looked;
        }
        double reach = hound.stage().smell * TRACK_REACH;
        return hound.level().getEntitiesOfClass(LivingEntity.class, hound.getBoundingBox().inflate(reach),
                        e -> e.isAlive() && !Crew.sameSide(hound, e) && (e instanceof Enemy || e instanceof SparringNinja n && !n.isResident()))
                .stream()
                .min(Comparator.<LivingEntity>comparingInt(e -> e instanceof SparringNinja ? 0 : 1)
                        .thenComparingDouble(e -> e.distanceToSqr(hound)))
                .orElse(null);
    }

    private static final DustParticleOptions SCENT = new DustParticleOptions(new Vector3f(0.72f, 0.78f, 0.42f), 0.9f);

    /** A faint trail from the hound toward the scent (a few blocks of it). */
    public static void scentTrail(ServerLevel level, NinjaHound hound, LivingEntity target) {
        Vec3 from = hound.position().add(0, 0.15, 0);
        Vec3 to = target.position().add(0, 0.15, 0).subtract(from);
        double length = Math.min(8, to.length());
        if (length < 0.5) {
            return;
        }
        Vec3 dir = to.normalize();
        for (double d = 0.5; d < length; d += 0.7) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(SCENT, p.x, p.y, p.z, 1, 0.08, 0.02, 0.08, 0);
        }
    }
}

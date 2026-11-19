package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.hiraishin.HiraishinFx;
import com.noirm.hiraishin.SpaceTimeInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Body Flicker (Shunshin): a burst of speed too fast to follow. You vanish in a puff of smoke and
 * leaves and are simply there, a blur left behind you. Server-authoritative: the path is swept here
 * and the client only draws it (an afterimage, a stretched trail, a puff at both ends, an FOV kick).
 * <ul>
 *   <li><b>Where:</b> locked on to someone (or an NPC's target) within reach, you land
 *       {@value #BEHIND} blocks behind them (sneaking: beside them); otherwise at the point your
 *       crosshair rests on (a wall, a roof, the ground), at most {@code range} blocks away (+5% per
 *       Speed point above 10, up to +{@value #MAX_SPEED_BONUS}).</li>
 *   <li><b>Walls stop you:</b> the body is swept along the path and halts at the first obstacle
 *       (a step up of one block is taken in stride). Never inside a block.</li>
 *   <li><b>Flicker-strike:</b> landing by an enemy opens the counter window: your next blow is thrown
 *       from behind them, can't be guarded and hits ×1.5.</li>
 *   <li><b>Chaining:</b> up to {@value #MAX_CHAIN} flickers in {@value #CHAIN_WINDOW} ticks, each one
 *       after the first costing +{@value #CHAIN_COST_STEP} of the cost more; then you're winded.</li>
 * </ul>
 */
public final class BodyFlicker {
    public static final double BEHIND = 1.25;
    public static final double LOCK_REACH_BONUS = 3;
    public static final double MAX_SPEED_BONUS = 5;
    public static final double STEP = 0.25;
    public static final double STEP_UP = 1.1;
    public static final int CHAIN_WINDOW = 60;
    public static final int MAX_CHAIN = 3;
    public static final float CHAIN_COST_STEP = 0.5f;

    public enum Mode { POINT, BEHIND, BESIDE, AWAY }

    /** What happened (tests read it). {@code blocked}: a wall cut the path short. */
    public record Result(Vec3 from, Vec3 to, LivingEntity target, Mode mode, boolean blocked) {}

    private static final Map<LivingEntity, Deque<Long>> RECENT = new WeakHashMap<>();

    private BodyFlicker() {}

    /** The jutsu behaviour {@code flicker}. */
    static void cast(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        Deque<Long> recent = RECENT.computeIfAbsent(caster, e -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst() > CHAIN_WINDOW) {
            recent.pollFirst();
        }
        float cost = JutsuCaster.cost(caster, ctx.id(), ctx.def());
        if (recent.size() >= MAX_CHAIN) {
            SpiritTechniques.addChakra(caster, cost); // winded: nothing happens, nothing is spent
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.flicker.winded"));
            return;
        }
        float extra = cost * CHAIN_COST_STEP * recent.size();
        if (extra > 0 && caster instanceof Player player && !player.getAbilities().instabuild
                && !com.noirm.player.ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(extra)).orElse(false)) {
            SpiritTechniques.addChakra(caster, cost);
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_chakra"));
            return;
        }
        recent.addLast(now);
        double range = range(caster, ctx.params().range());
        LivingEntity target = SpaceTimeInput.lockTarget(caster);
        Mode mode = Mode.POINT;
        if (target != null && (target.distanceTo(caster) > range + LOCK_REACH_BONUS || !caster.hasLineOfSight(target))) {
            target = null;
        }
        if (target != null) {
            Vec3 toTarget = target.position().subtract(caster.position());
            boolean facingAway = caster instanceof Mob && caster.getLookAngle().multiply(1, 0, 1).dot(toTarget.multiply(1, 0, 1)) < 0;
            mode = facingAway ? Mode.AWAY : caster.isShiftKeyDown() ? Mode.BESIDE : Mode.BEHIND;
            if (mode == Mode.AWAY) {
                target = null;
            }
        }
        flicker(caster, target, mode, range);
    }

    /** The reach: the jutsu's range, +5% per point of strength (speed and taijutsu) above 10, at most +5 blocks. */
    public static double range(LivingEntity caster, double base) {
        float speed = com.noirm.combat.Perception.basePower(caster); // speed and taijutsu
        return base + Mth.clamp((speed - 10f) * 0.05 * base, 0, MAX_SPEED_BONUS);
    }

    /** Flickers {@code caster}: behind/beside {@code target}, or to the crosshair point ({@code target} null). */
    public static Result flicker(LivingEntity caster, LivingEntity target, Mode mode, double range) {
        ServerLevel level = (ServerLevel) caster.level();
        Vec3 from = caster.position();
        Vec3 desired;
        if (target != null && (mode == Mode.BEHIND || mode == Mode.BESIDE)) {
            Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
            Vec3 offset = mode == Mode.BEHIND ? facing.scale(-1)
                    : new Vec3(-facing.z, 0, facing.x).scale(Math.signum(new Vec3(-facing.z, 0, facing.x).dot(from.subtract(target.position()))) >= 0 ? 1 : -1);
            desired = target.position().add(offset.scale(BEHIND + target.getBbWidth() * 0.5));
        } else {
            desired = aimPoint(caster, range);
        }
        if (desired.distanceTo(from) > range + LOCK_REACH_BONUS) {
            desired = from.add(desired.subtract(from).normalize().scale(range + LOCK_REACH_BONUS));
        }
        Vec3[] swept = sweep(level, caster, from, desired);
        Vec3 to = swept[0];
        boolean blocked = swept[1] != null;
        float yaw = caster.getYRot();
        if (target != null && !blocked) {
            yaw = (float) (Mth.atan2(target.getZ() - to.z, target.getX() - to.x) * Mth.RAD_TO_DEG) - 90f;
        }
        if (caster instanceof ServerPlayer player) {
            player.connection.teleport(to.x, to.y, to.z, yaw, player.getXRot());
        } else {
            caster.moveTo(to.x, to.y, to.z, yaw, caster.getXRot());
            caster.setYHeadRot(yaw);
            caster.setYBodyRot(yaw);
        }
        caster.setYRot(yaw);
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        caster.fallDistance = 0;
        HiraishinFx.flash(level, from, to, caster, HiraishinFx.FLICKER);
        if (target != null && !blocked && to.distanceTo(target.position()) <= BEHIND + target.getBbWidth() + 1.2) {
            CombatSystem.openDodgeCounter(caster, target, mode == Mode.BESIDE ? 1f : 0f); // flicker-strike
        }
        return new Result(from, to, target, mode, blocked);
    }

    /** Where the crosshair rests within {@code range}: a block face (standing on or before it), or the air at range. */
    static Vec3 aimPoint(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double eyeHeight = caster.getEyeHeight();
        if (hit.getType() == HitResult.Type.MISS) {
            // Looking at the air: the same height you're at, unless you look well up or down.
            Vec3 flat = end.subtract(0, eyeHeight, 0);
            return Math.abs(look.y) < 0.35 ? new Vec3(flat.x, caster.getY(), flat.z) : flat;
        }
        Vec3 at = hit.getLocation();
        return switch (hit.getDirection()) {
            case UP -> at;
            case DOWN -> at.subtract(0, caster.getBbHeight() + 0.05, 0);
            default -> {
                Vec3 n = new Vec3(hit.getDirection().getStepX(), 0, hit.getDirection().getStepZ());
                Vec3 before = at.add(n.scale(caster.getBbWidth() * 0.5 + 0.05));
                yield new Vec3(before.x, Math.min(caster.getY() + STEP_UP, Math.max(before.y - eyeHeight, caster.getY() - 3)), before.z);
            }
        };
    }

    /**
     * Sweeps {@code entity}'s body from {@code from} to {@code to}: returns [where it stops, null] or
     * [where it stops, the point where a wall cut it short].
     */
    static Vec3[] sweep(ServerLevel level, LivingEntity entity, Vec3 from, Vec3 to) {
        AABB box = entity.getBoundingBox();
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1e-4) {
            return new Vec3[] {from, null};
        }
        Vec3 dir = delta.scale(1 / length);
        Vec3 last = from;
        double lift = 0;
        for (double d = STEP; d <= length + 1e-6; d += STEP) {
            double t = Math.min(d, length);
            Vec3 next = from.add(dir.scale(t)).add(0, lift, 0);
            if (!level.noCollision(entity, box.move(next.subtract(from)))) {
                // A one-block step is taken in stride (stairs, a ledge, a fallen log).
                boolean stepped = false;
                for (double up = 0.5; up <= STEP_UP + 1e-6; up += 0.3) {
                    Vec3 raised = next.add(0, up, 0);
                    if (level.noCollision(entity, box.move(raised.subtract(from)))
                            && level.noCollision(entity, box.move(last.add(0, up, 0).subtract(from)))) {
                        lift += up;
                        next = raised;
                        stepped = true;
                        break;
                    }
                }
                if (!stepped) {
                    return new Vec3[] {last, next};
                }
            }
            last = next;
        }
        // Lifted over a step but aimed at the ground beyond it: settle back down onto the floor.
        if (lift > 0) {
            for (double down = 0.25; down <= lift + 1e-6; down += 0.25) {
                Vec3 lower = last.subtract(0, 0.25, 0);
                if (!level.noCollision(entity, box.move(lower.subtract(from)))) {
                    break;
                }
                last = lower;
            }
        }
        return new Vec3[] {last, null};
    }

    /** Forgets {@code entity}'s recent flickers (tests). */
    public static void reset(LivingEntity entity) {
        RECENT.remove(entity);
    }

    /** How many flickers {@code entity} has in its current chain. */
    public static int chain(LivingEntity entity) {
        Deque<Long> recent = RECENT.get(entity);
        if (recent == null) {
            return 0;
        }
        long now = entity.level().getGameTime();
        return (int) recent.stream().filter(t -> now - t <= CHAIN_WINDOW).count();
    }
}

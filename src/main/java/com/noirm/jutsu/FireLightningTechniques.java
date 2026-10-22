package com.noirm.jutsu;

import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.vfx.FireVfx;
import com.noirm.vfx.LightningVfx;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Two techniques that play out in steps:
 * <ul>
 *   <li><b>ash_cloud</b> (Burning Ash): the caster breathes out a cloud of hot ash that drifts
 *       forward {@code range} blocks over {@code duration} ticks, choking and slowing whoever is
 *       inside ({@code effect}), then ignites it all at once: a blast of {@code radius} that hits
 *       for {@code damage} and sets things alight. It catches early if it drifts into a wall.</li>
 *   <li><b>chain</b> (Chain Discharge): a bolt leaps from the hand to the enemy you look at
 *       within {@code range}, then on to the nearest enemy within {@code radius} of the last one, up
 *       to {@code count} enemies, a little weaker at each jump. Never your own side.</li>
 * </ul>
 */
public final class FireLightningTechniques {
    /** Each jump of a chain keeps this share of the damage. */
    public static final float CHAIN_FALLOFF = 0.8f;
    /** Ticks between jumps (fast, but readable). */
    public static final int CHAIN_DELAY = 2;
    /** Ticks between the ash cloud's chokes on whoever is inside. */
    private static final int ASH_CHOKE_INTERVAL = 10;

    private FireLightningTechniques() {}

    // ---------------------------------------------------------------- Burning Ash

    static void ashCloud(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 dir = ctx.look();
        Vec3 origin = caster.getEyePosition().add(dir.scale(1.5)).add(0, -0.3, 0);
        int duration = Math.max(10, p.duration());
        double speed = Math.max(0, p.range() - 1.5) / duration;
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        int[] age = {0};
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.5f);
        FireVfx.ashCloud(level, origin, radius * 0.4f);
        GreatTechniques.later(now -> {
            if (!caster.isAlive() || caster.level() != level) {
                return true; // no one left to set it alight: the ash just settles
            }
            age[0]++;
            float grown = radius * (0.45f + 0.55f * Math.min(1f, age[0] / (float) duration));
            Vec3 center = origin.add(dir.scale(speed * age[0]));
            boolean wall = !level.getBlockState(BlockPos.containing(center)).getCollisionShape(level, BlockPos.containing(center)).isEmpty();
            FireVfx.ashCloud(level, center, grown);
            if (age[0] % ASH_CHOKE_INTERVAL == 0) {
                for (LivingEntity victim : inside(caster, level, center, grown)) {
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                    if (victim instanceof Player) {
                        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
                    }
                }
            }
            if (age[0] < duration && !wall) {
                return false;
            }
            ignite(caster, level, center, grown, p, power);
            return true;
        });
    }

    /** The click of teeth, and the whole cloud goes up. */
    private static void ignite(LivingEntity caster, ServerLevel level, Vec3 center, float radius, JutsuDefinition.Params p, float power) {
        level.playSound(null, center.x, center.y, center.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, 0.7f);
        FireVfx.ashIgnite(level, center, radius);
        float reach = radius * 1.2f;
        for (LivingEntity victim : inside(caster, level, center, reach)) {
            float falloff = 1f - (float) Math.min(1, victim.position().add(0, victim.getBbHeight() * 0.5, 0).distanceTo(center) / (reach + 1));
            JutsuEffects.hit(caster, victim, p.damage() * power * (0.5f + 0.5f * falloff), p.knockback(), p.posture() * power, p.fireSeconds(),
                    victim.position().subtract(center));
        }
        Destruction.natureImpact(level, center, Nature.FIRE, radius * 0.6f, Math.max(0.5f, p.damage() * power / 10f));
    }

    private static List<LivingEntity> inside(LivingEntity caster, ServerLevel level, Vec3 center, float radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                        && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(center) <= radius + e.getBbWidth());
    }

    // ---------------------------------------------------------------- Chain Discharge

    static void chain(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 hands = caster.getEyePosition().add(ctx.look().scale(0.7)).add(0, -0.4, 0);
        LivingEntity first = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        JutsuBehaviors.sound(caster, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9f, 1.5f);
        LightningVfx.contact(level, hands, 0.9f);
        if (first == null) {
            // Nothing to leap to: it cracks into the ground ahead and forks out.
            Vec3 eye = caster.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(ctx.look().scale(p.range())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(ctx.look().scale(p.range())) : hit.getLocation();
            LightningVfx.forked(level, hands, end, 0.12f, 8);
            LightningVfx.groundForks(level, end, 3f, 5);
            return;
        }
        List<LivingEntity> struck = new ArrayList<>();
        int count = Math.max(1, p.count());
        Vec3[] from = {hands};
        LivingEntity[] next = {first};
        float[] damage = {p.damage() * power};
        int[] wait = {0};
        // The first jump lands at once; the rest follow a couple of ticks apart.
        if (jump(caster, level, p, from, next, damage, struck, count)) {
            return;
        }
        GreatTechniques.later(now -> {
            if (++wait[0] < CHAIN_DELAY) {
                return false;
            }
            wait[0] = 0;
            return jump(caster, level, p, from, next, damage, struck, count);
        });
    }

    /** One jump: strike {@code next[0]}, then pick the next. Returns true when the chain is over. */
    private static boolean jump(LivingEntity caster, ServerLevel level, JutsuDefinition.Params p, Vec3[] from, LivingEntity[] next,
                                float[] damage, List<LivingEntity> struck, int count) {
        LivingEntity target = next[0];
        if (target == null || !target.isAlive() || target.level() != level) {
            return true;
        }
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
        LightningVfx.forked(level, from[0], at, 0.11f, 7);
        LightningVfx.contact(level, at, 1.1f);
        LightningVfx.pool(level, target.position(), 2f, 8);
        JutsuEffects.hit(caster, target, damage[0], p.knockback(), p.posture(), p.fireSeconds(), at.subtract(from[0]));
        if (p.stun() > 0) {
            CombatSystem.stun(target, p.stun());
        }
        JutsuEffects.applyEffects(target, p.effect(), p.effectTicks());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.8f);
        struck.add(target);
        if (struck.size() >= count) {
            return true;
        }
        float reach = Math.max(1f, p.radius());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(reach),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && !struck.contains(e))) {
            double distance = candidate.distanceTo(target);
            if (distance <= reach && distance < bestDistance && target.hasLineOfSight(candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return true;
        }
        from[0] = at;
        next[0] = best;
        damage[0] *= CHAIN_FALLOFF;
        return false;
    }
}

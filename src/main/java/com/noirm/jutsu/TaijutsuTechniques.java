package com.noirm.jutsu;

import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.vfx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Taijutsu techniques a master teaches (no nature, no hand seals): moves that play out over a
 * second or so, so they're ticked here.
 * <ul>
 *   <li><b>{@code lotus}</b> (Front Lotus): a rising kick launches the target, you follow them up,
 *   wrap them and drive them head-first into the ground: knockdown and heavy posture damage.</li>
 *   <li><b>{@code flurry}</b> (Morning Peacock): a storm of punches so fast they catch fire, hitting
 *   everything in a cone {@code count} times over {@code duration} ticks.</li>
 * </ul>
 * Params: {@code range}, {@code damage} (the slam / each punch), {@code posture}, {@code speed}
 * (Lotus: how hard the launch is), {@code duration}, {@code count}, {@code knockback},
 * {@code fire_seconds}, {@code stun}.
 */
public final class TaijutsuTechniques {
    /** Front Lotus: ticks between the launch and the wrap, and the longest the fall may take. */
    public static final int LOTUS_RISE_TICKS = 8;
    public static final int LOTUS_MAX_TICKS = 40;
    /** Front Lotus: the kick that launches is this share of the damage; the slam is the rest. */
    public static final float LOTUS_KICK_SHARE = 0.25f;
    /** Front Lotus: how fast you both come down. */
    public static final double LOTUS_SLAM_SPEED = -2.2;
    /** Morning Peacock: half-angle of the cone the punches cover. */
    public static final double PEACOCK_HALF_ANGLE = 40;

    private TaijutsuTechniques() {}

    /** A technique in progress. */
    private static final class Sequence {
        final JutsuContext ctx;
        final LivingEntity target;
        int ticks;
        boolean falling;

        Sequence(JutsuContext ctx, LivingEntity target) {
            this.ctx = ctx;
            this.target = target;
        }
    }

    private static final Map<LivingEntity, Sequence> RUNNING = new WeakHashMap<>();

    /** Is {@code caster} in the middle of a technique? */
    public static boolean busy(LivingEntity caster) {
        return RUNNING.containsKey(caster);
    }

    // ------------------------------------------------------------ Front Lotus

    static void lotus(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 35);
        if (target == null) {
            target = JutsuBehaviors.inCone(caster, p.range(), 60).stream().findFirst().orElse(null);
        }
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        Vec3 direction = target.position().subtract(caster.position());
        float before = target.getHealth();
        JutsuEffects.hit(caster, target, p.damage() * LOTUS_KICK_SHARE * ctx.power(), 0f, 0f, 0, direction);
        if (!target.isAlive() || target.getHealth() >= before) {
            return; // the kick was dodged, parried, substituted or couldn't hurt them: no launch
        }
        CombatSystem.stun(target, LOTUS_MAX_TICKS + 10);
        double lift = Math.max(0.6, p.speed());
        target.setDeltaMovement(0, lift, 0);
        target.hurtMarked = true;
        caster.setDeltaMovement(0, lift * 0.95, 0);
        caster.hurtMarked = true;
        ServerLevel level = ctx.level();
        Vfx.shockwave(level, caster.position(), Vfx.NEUTRAL, 2f);
        Vfx.burst(level, Vfx.smoke(0xD8D0C0, 0.8f, 18), caster.position().add(0, 0.2, 0), 8, 0.5, 0.05);
        Vfx.streak(level, target.position(), target.position().add(0, 3, 0), Vfx.NEUTRAL);
        JutsuBehaviors.sound(caster, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
        RUNNING.put(caster, new Sequence(ctx, target));
    }

    private static void tickLotus(LivingEntity caster, Sequence seq) {
        LivingEntity target = seq.target;
        ServerLevel level = seq.ctx.level();
        caster.fallDistance = 0;
        target.fallDistance = 0; // the slam's damage is the technique's, not the fall's
        if (!seq.falling) {
            if (seq.ticks < LOTUS_RISE_TICKS) {
                return;
            }
            // The wrap: you appear behind them in the air, spin, and drive down.
            Vec3 at = target.position().add(target.getLookAngle().multiply(-0.6, 0, -0.6)).add(0, 0.3, 0);
            caster.teleportTo(at.x, at.y, at.z);
            Vfx.spiral(level, target.position().add(0, -0.3, 0), Vfx.palette(Nature.WIND), 0.7f, target.getBbHeight() + 0.6f, 3f);
            target.setDeltaMovement(0, LOTUS_SLAM_SPEED, 0);
            target.hurtMarked = true;
            caster.setDeltaMovement(0, LOTUS_SLAM_SPEED, 0);
            caster.hurtMarked = true;
            JutsuBehaviors.sound(caster, SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.4f);
            seq.falling = true;
            return;
        }
        if (target.onGround() || seq.ticks >= LOTUS_MAX_TICKS) {
            JutsuDefinition.Params p = seq.ctx.params();
            float damage = p.damage() * (1f - LOTUS_KICK_SHARE) * seq.ctx.power();
            JutsuEffects.hit(caster, target, damage, p.knockback(), p.posture() * seq.ctx.power(), 0,
                    target.position().subtract(caster.position()));
            CombatSystem.knockDown(target, Math.max(p.stun(), CombatSystem.KNOCKDOWN_TICKS));
            Vfx.shockwave(level, target.position(), Vfx.NEUTRAL, 3f);
            Vfx.burst(level, Vfx.smoke(0xB8A890, 1.1f, 30), target.position().add(0, 0.2, 0), 14, 0.8, 0.06);
            Vfx.quake(level, target.position(), 0.5f, 14);
            JutsuBehaviors.sound(target, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.3f);
            RUNNING.remove(caster);
        } else {
            Vfx.aura(level, target.position(), target.getBbWidth(), target.getBbHeight(), Vfx.NEUTRAL, 3);
        }
    }

    // ------------------------------------------------------------ Morning Peacock

    static void flurry(JutsuContext ctx) {
        JutsuBehaviors.sound(ctx.caster(), SoundEvents.BLAZE_SHOOT, 0.8f, 1.6f);
        RUNNING.put(ctx.caster(), new Sequence(ctx, null));
    }

    private static void tickFlurry(LivingEntity caster, Sequence seq) {
        JutsuDefinition.Params p = seq.ctx.params();
        ServerLevel level = seq.ctx.level();
        int count = Math.max(1, p.count());
        int every = Math.max(1, p.duration() / count);
        Vec3 eye = caster.getEyePosition().add(0, -0.3, 0);
        Vec3 look = caster.getViewVector(1f);
        // Fists like falling petals of fire: flaring glows across the cone.
        for (int i = 0; i < 3; i++) {
            Vec3 at = eye.add(look.scale(0.8 + caster.getRandom().nextDouble() * p.range() * 0.8))
                    .add((caster.getRandom().nextDouble() - 0.5) * 1.6, (caster.getRandom().nextDouble() - 0.5) * 1.2,
                            (caster.getRandom().nextDouble() - 0.5) * 1.6);
            Vfx.burst(level, Vfx.glow(0xFFF0C0, 0xFF6A10, 0.45f, 0.05f, 8), at, 1, 0, 0);
        }
        if (seq.ticks % every == 0) {
            int punch = seq.ticks / every;
            boolean last = punch >= count - 1;
            for (LivingEntity target : JutsuBehaviors.inCone(caster, p.range(), PEACOCK_HALF_ANGLE)) {
                JutsuEffects.hit(caster, target, p.damage() * seq.ctx.power() * (last ? 2.5f : 1f), last ? p.knockback() * 6 : p.knockback(),
                        p.posture() * seq.ctx.power(), p.fireSeconds(), target.position().subtract(caster.position()));
                if (!last) {
                    CombatSystem.stun(target, every + 4);
                }
            }
            Vfx.cone(level, eye, look, p.range(), Vfx.palette(Nature.FIRE), 0.5f);
            JutsuBehaviors.sound(caster, last ? SoundEvents.GENERIC_EXPLODE : SoundEvents.PLAYER_ATTACK_STRONG, last ? 0.6f : 0.5f,
                    1.2f + caster.getRandom().nextFloat() * 0.4f);
            if (last) {
                RUNNING.remove(caster);
            }
        }
    }

    // ------------------------------------------------------------ ticking

    /** Every server tick, for every living entity (from {@code PowerEvents}). */
    public static void tick(LivingEntity caster) {
        Sequence seq = RUNNING.get(caster);
        if (seq == null) {
            return;
        }
        seq.ticks++;
        boolean lotus = "lotus".equals(seq.ctx.def().behavior());
        if (!caster.isAlive() || (lotus && (seq.target == null || !seq.target.isAlive())) || seq.ticks > LOTUS_MAX_TICKS * 2) {
            RUNNING.remove(caster);
            return;
        }
        if (lotus) {
            tickLotus(caster, seq);
        } else {
            tickFlurry(caster, seq);
        }
    }
}

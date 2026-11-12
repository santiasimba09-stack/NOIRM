package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.vfx.WaterVfx;
import com.noirm.world.Destruction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Water Release's own S-rank (owner, 2026-11-12: "Water: add an S-rank jutsu of our own design"). Our
 * design, not canon.
 *
 * <p><b>maelstrom</b> (charged), the <b>Leviathan Maelstrom</b>, in three beats you can read from far
 * away and, if you're quick, escape from the first:
 * <ol>
 *   <li><b>The sea opens</b> ({@link #WHIRL_TICKS}): a whirlpool of {@code radius} (bigger with charge
 *       and power) turns on the ground where you aim. Enemies on it are dragged in and round, slowed,
 *       and scraped every half second ({@link #WHIRL_SHARE} of {@code damage}). Step off it in time and
 *       you're out.</li>
 *   <li><b>The prison</b> ({@link #PRISON_TICKS}): the water closes into a churning sphere over the
 *       eye. Whoever is in it is held ({@code stun}), put out if burning, and crushed every 8 ticks
 *       ({@link #CRUSH_SHARE} of {@code damage}, and posture).</li>
 *   <li><b>The leviathan</b> ({@link #RISE_TICKS} + {@link #DIVE_TICKS}): a serpent of water with a
 *       pale horned head bursts out of the sphere, coils up round it to {@link #HEIGHT} blocks, and
 *       crashes straight down on the eye: {@code damage} (less toward the edge of the whirlpool),
 *       {@code knockback} outward, {@code effect}, fires put out and a tidal ring of foam.</li>
 * </ol>
 * All of it takes about 4 seconds. The caster is free to move while it plays. NPC ninjas of Water at
 * S tier use it from range ({@code NinjaFightGoal}).
 */
public final class WaterTechniques {
    public static final int WHIRL_TICKS = 26;
    public static final int PRISON_TICKS = 24;
    public static final int RISE_TICKS = 28;
    public static final int DIVE_TICKS = 8;
    /** Whirlpool scrapes every this many ticks, for this share of the jutsu's damage. */
    public static final int WHIRL_PULSE = 10;
    public static final float WHIRL_SHARE = 0.1f;
    /** The prison crushes every this many ticks, for this share. */
    public static final int CRUSH_PULSE = 8;
    public static final float CRUSH_SHARE = 0.2f;
    /** How high the leviathan coils before it dives. */
    public static final double HEIGHT = 14;
    /** The prison's radius (blocks). */
    public static final float PRISON_RADIUS = 2.6f;
    /** The leviathan's body: this many head positions long. */
    private static final int BODY = 30;

    private WaterTechniques() {}

    static void maelstrom(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float charge = ctx.charge();
        float power = ctx.power() * (0.6f + 0.4f * charge);
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        Vec3 center = ElementalBehaviors.ground(level, target != null ? target.position() : ElementalBehaviors.aim(caster, p.range()));
        float radius = Mth.clamp(p.radius() * (0.75f + 0.25f * charge) * (float) Math.sqrt(Math.max(0.6f, Math.min(2.5f, power))), 3f, 10f);
        Vec3 eye = center.add(0, PRISON_RADIUS * 0.75, 0);
        Set<LivingEntity> held = new HashSet<>();
        List<Vec3> body = new ArrayList<>();
        int[] age = {0};
        Vec3[] dive = new Vec3[1];
        level.playSound(null, center.x, center.y, center.z, com.noirm.sound.NoirmSounds.WATER_SURGE.get(), SoundSource.PLAYERS, 2.2f, 0.55f);
        com.noirm.vfx.Vfx.quake(level, center, 0.4f, radius * 3);
        GreatTechniques.later(now -> {
            int t = ++age[0];
            if (!caster.isAlive() || caster.level() != level) {
                return true;
            }
            int prisonStart = WHIRL_TICKS;
            int riseStart = prisonStart + PRISON_TICKS;
            int diveStart = riseStart + RISE_TICKS;
            int end = diveStart + DIVE_TICKS;
            if (t <= riseStart + 6) {
                // 1. The whirlpool: it keeps turning under the prison and the leviathan's rise.
                WaterVfx.whirlpool(level, center, radius, t, t > prisonStart ? 1.5f : 0.4f);
            }
            if (t <= prisonStart) {
                for (LivingEntity victim : victims(level, caster, center, radius, 3)) {
                    Vec3 to = center.subtract(victim.position());
                    double flat = Math.hypot(to.x, to.z);
                    Vec3 inward = flat < 1e-3 ? Vec3.ZERO : new Vec3(to.x / flat, 0, to.z / flat);
                    Vec3 spin = new Vec3(-inward.z, 0, inward.x);
                    double pull = Mth.clamp(flat * 0.06 * Math.max(0.5, p.knockback() * 0.5), 0.05, 0.32);
                    victim.setDeltaMovement(inward.scale(pull).add(spin.scale(0.18)).add(0, Math.min(0, victim.getDeltaMovement().y), 0));
                    victim.hurtMarked = true;
                    victim.fallDistance = 0;
                    if (t % WHIRL_PULSE == 0) {
                        JutsuEffects.hit(caster, victim, p.damage() * power * WHIRL_SHARE, 0, p.posture() * power * 0.2f, 0, Vec3.ZERO);
                        JutsuEffects.applyEffects(victim, "slowness:1", 20);
                    }
                }
                if (t % 8 == 0) {
                    level.playSound(null, center.x, center.y, center.z, com.noirm.sound.NoirmSounds.WATER_SURGE.get(), SoundSource.PLAYERS, 1.2f, 0.7f + t * 0.01f);
                }
                return false;
            }
            if (t <= riseStart) {
                // 2. The prison closes over the eye.
                float grow = Math.min(1f, (t - prisonStart) / 6f);
                WaterVfx.prison(level, eye, PRISON_RADIUS * (0.4f + 0.6f * grow), t);
                if (t == prisonStart + 1) {
                    level.playSound(null, eye.x, eye.y, eye.z, com.noirm.sound.NoirmSounds.WATER_SPLASH.get(), SoundSource.PLAYERS, 2f, 0.6f);
                    Destruction.douse(level, center, radius);
                }
                for (LivingEntity victim : victims(level, caster, center, PRISON_RADIUS + 1.2f, PRISON_RADIUS * 2 + 1)) {
                    held.add(victim);
                    Vec3 to = eye.subtract(victim.position().add(0, victim.getBbHeight() * 0.4, 0));
                    victim.setDeltaMovement(to.scale(0.25));
                    victim.hurtMarked = true;
                    victim.fallDistance = 0;
                    victim.clearFire();
                    CombatSystem.stun(victim, Math.max(4, p.stun()));
                    if ((t - prisonStart) % CRUSH_PULSE == 0) {
                        JutsuEffects.hit(caster, victim, p.damage() * power * CRUSH_SHARE, 0, p.posture() * power * 0.4f, 0, Vec3.ZERO);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, victim.getX(), victim.getEyeY(), victim.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                    }
                }
                if ((t - prisonStart) % CRUSH_PULSE == 0) {
                    level.playSound(null, eye.x, eye.y, eye.z, com.noirm.sound.NoirmSounds.WATER_SURGE.get(), SoundSource.PLAYERS, 1.4f, 0.5f);
                }
                return false;
            }
            float scale = Mth.clamp(radius / 6f, 0.8f, 1.5f);
            if (t <= diveStart) {
                // 3a. The leviathan bursts out and coils up round the column.
                double k = (t - riseStart) / (double) RISE_TICKS;
                double a = k * Math.PI * 2.2;
                double r = PRISON_RADIUS + 0.8 + k * 1.2;
                Vec3 head = eye.add(Math.cos(a) * r, k * HEIGHT, Math.sin(a) * r);
                body.add(0, head);
                if (body.size() > BODY) {
                    body.remove(body.size() - 1);
                }
                WaterVfx.leviathan(level, body, scale);
                WaterVfx.prison(level, eye, PRISON_RADIUS * (float) (1 - k * 0.7), t);
                for (LivingEntity victim : held) {
                    if (victim.isAlive()) {
                        victim.setDeltaMovement(eye.subtract(victim.position()).scale(0.2));
                        victim.hurtMarked = true;
                        CombatSystem.stun(victim, 4);
                    }
                }
                if (t == riseStart + 1) {
                    level.playSound(null, eye.x, eye.y, eye.z, com.noirm.sound.NoirmSounds.WATER_SURGE.get(), SoundSource.PLAYERS, 3f, 0.45f);
                }
                return false;
            }
            if (t < end) {
                // 3b. It turns over and dives on the eye.
                if (dive[0] == null) {
                    dive[0] = body.get(0);
                }
                double k = (t - diveStart) / (double) DIVE_TICKS;
                Vec3 head = dive[0].add(center.add(0, 0.5, 0).subtract(dive[0]).scale(k * k));
                body.add(0, head);
                if (body.size() > BODY) {
                    body.remove(body.size() - 1);
                }
                WaterVfx.leviathan(level, body, scale * 1.1f);
                return false;
            }
            // The crash.
            WaterVfx.crash(level, center, radius);
            Destruction.douse(level, center, radius + 1);
            level.playSound(null, center.x, center.y, center.z, com.noirm.sound.NoirmSounds.WATER_SPLASH.get(), SoundSource.PLAYERS, 3f, 0.5f);
            level.playSound(null, center.x, center.y, center.z, com.noirm.sound.NoirmSounds.EXPLOSION_BOOM.get(), SoundSource.PLAYERS, 1.6f, 0.7f);
            for (LivingEntity victim : victims(level, caster, center, radius, 4)) {
                double flat = Math.hypot(victim.getX() - center.x, victim.getZ() - center.z);
                float falloff = (float) Mth.clamp(1 - flat / (radius + 0.5), 0, 1);
                JutsuEffects.hit(caster, victim, p.damage() * power * (0.5f + 0.5f * falloff), p.knockback(), p.posture() * power,
                        0, victim.position().subtract(center));
                JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                victim.clearFire();
                victim.fallDistance = 0;
            }
            return true;
        });
    }

    /** Enemies of {@code caster} within {@code radius} (flat) of {@code at}, from a little below to {@code height} above. */
    private static List<LivingEntity> victims(ServerLevel level, LivingEntity caster, Vec3 at, float radius, float height) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, height + 1, radius + 1),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                        && Math.hypot(e.getX() - at.x, e.getZ() - at.z) <= radius + e.getBbWidth() * 0.5
                        && e.getY() >= at.y - 1.5 && e.getY() <= at.y + height);
    }
}

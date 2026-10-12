package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.dojutsu.Byakugan;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Hyūga's Byakugan jutsu.
 * <ul>
 *   <li><b>Eight Trigrams Palm Rotation</b> ({@code rotation}): spin out a dome of chakra for
 *       {@code duration} ticks. It throws back everyone within {@code radius}, turns projectiles
 *       around, and no attack gets through while it spins.</li>
 *   <li><b>Eight Trigrams 64 Palms</b> ({@code palms}): strike the enemy in front in six
 *       accelerating waves (2, 4, 8, 16, 32, 64 palms) over {@code duration} ticks. The target
 *       is held in place, takes {@code damage} and {@code posture} per wave, has its chakra
 *       points closed (usually sealing its jutsu), and the last palm sends it flying.</li>
 * </ul>
 */
public final class HyugaTechniques {
    private static final DustParticleOptions CHAKRA = new DustParticleOptions(new Vector3f(0.6f, 0.8f, 1f), 1.2f);
    private static final int[] WAVES = {2, 4, 8, 16, 32, 64};

    private static final Map<LivingEntity, Spin> SPINS = new WeakHashMap<>();
    private static final Map<LivingEntity, Palms> PALMS = new WeakHashMap<>();

    private HyugaTechniques() {}

    // ---------------------------------------------------------------- Rotation

    static void rotation(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        Spin spin = new Spin(caster.level().getGameTime() + p.duration(), p.radius(), p.knockback(), p.posture() * ctx.power(),
                p.damage() * ctx.power());
        SPINS.put(caster, spin);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1f, 1.3f);
        spin(caster, spin, true);
    }

    public static boolean isSpinning(LivingEntity entity) {
        Spin spin = SPINS.get(entity);
        return spin != null && entity.level().getGameTime() <= spin.until;
    }

    /** While spinning, every attack is turned away (and the attacker thrown back). */
    public static boolean deflect(LivingEntity target, Entity attacker) {
        if (!isSpinning(target)) {
            return false;
        }
        if (attacker instanceof LivingEntity living && living != target && living.distanceTo(target) < 6) {
            push(target, living, SPINS.get(target).knockback);
        }
        return true;
    }

    private static void spin(LivingEntity caster, Spin spin, boolean first) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        AABB area = caster.getBoundingBox().inflate(spin.radius);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != caster && e.isAlive() && !e.isSpectator() && e.distanceTo(caster) <= spin.radius)) {
            if (first) {
                JutsuEffects.hit(caster, other, spin.damage, 0, spin.posture, 0, other.position().subtract(caster.position()));
            }
            push(caster, other, spin.knockback);
        }
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area.inflate(1),
                e -> e.getOwner() != caster && e.distanceTo(caster) <= spin.radius + 1)) {
            Vec3 away = projectile.position().subtract(caster.position()).normalize();
            projectile.setDeltaMovement(away.scale(Math.max(0.6, projectile.getDeltaMovement().length())));
            projectile.setOwner(caster);
        }
        // A spinning dome of chakra.
        long time = level.getGameTime();
        for (int i = 0; i < 16; i++) {
            double angle = time * 0.9 + i * (Math.PI * 2 / 16);
            double height = (i % 4) * 0.55;
            double r = spin.radius * 0.7 * Math.sqrt(1 - Math.pow(height / 2.2, 2));
            level.sendParticles(CHAKRA, caster.getX() + Math.cos(angle) * r, caster.getY() + 0.1 + height,
                    caster.getZ() + Math.sin(angle) * r, 1, 0.05, 0.05, 0.05, 0);
        }
        if (first) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, caster.getX(), caster.getY() + 1, caster.getZ(), 6, 1.2, 0.4, 1.2, 0);
        }
    }

    private static void push(LivingEntity from, LivingEntity other, float strength) {
        Vec3 away = other.position().subtract(from.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() < 1e-4) {
            flat = Vec3.directionFromRotation(0, from.getYRot());
        }
        flat = flat.normalize();
        other.knockback(strength, -flat.x, -flat.z);
        other.hurtMarked = true;
    }

    // ---------------------------------------------------------------- 64 Palms

    static void palms(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 25);
        if (target == null) {
            if (caster instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.palms.no_target"), true);
            }
            return;
        }
        Palms palms = new Palms(target, caster.level().getGameTime(), p.duration(), p.damage() * ctx.power(),
                p.posture() * ctx.power(), p.knockback());
        PALMS.put(caster, palms);
        CombatSystem.stun(target, p.duration() + 10);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, p.duration(), 4, false, false));
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (caster instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.palms.start"), true);
        }
    }

    /** Is {@code caster} in the middle of 64 Palms? */
    public static boolean isStriking(LivingEntity caster) {
        return PALMS.containsKey(caster);
    }

    // ---------------------------------------------------------------- ticking

    /** Called every server tick for every living entity. */
    public static void tick(LivingEntity entity) {
        Spin spin = SPINS.get(entity);
        if (spin != null) {
            if (entity.level().getGameTime() > spin.until || !entity.isAlive()) {
                SPINS.remove(entity);
            } else {
                spin(entity, spin, false);
            }
        }
        Palms palms = PALMS.get(entity);
        if (palms != null) {
            tickPalms(entity, palms);
        }
    }

    private static void tickPalms(LivingEntity caster, Palms palms) {
        LivingEntity target = palms.target;
        long elapsed = caster.level().getGameTime() - palms.start;
        if (!caster.isAlive() || !target.isAlive() || caster.distanceTo(target) > 6 || CombatSystem.isStaggered(caster)) {
            PALMS.remove(caster);
            return;
        }
        int interval = Math.max(1, palms.duration / WAVES.length);
        if (elapsed % interval != 0 || palms.wave >= WAVES.length) {
            return;
        }
        int count = WAVES[palms.wave++];
        boolean last = palms.wave == WAVES.length;
        caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        Vec3 direction = target.position().subtract(caster.position());
        JutsuEffects.hit(caster, target, palms.damage, last ? palms.knockback : 0, palms.posture, 0, direction);
        Byakugan.closePoints(caster, target, Math.max(1, count / 4), count * 0.4f);
        if (!last) {
            CombatSystem.stun(target, interval + 6);
        }
        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(CHAKRA, target.getX(), target.getY() + 1.1, target.getZ(), 6 + count / 2, 0.3, 0.4, 0.3, 0.05);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.1, target.getZ(), 4 + count / 4, 0.3, 0.4, 0.3, 0.2);
        }
        float pitch = Mth.clamp(0.9f + palms.wave * 0.12f, 0.9f, 1.8f);
        caster.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                last ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, pitch);
        if (caster instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.palms.wave", count), true);
        }
        if (last) {
            PALMS.remove(caster);
        }
    }

    private record Spin(long until, float radius, float knockback, float posture, float damage) {}

    private static final class Palms {
        final LivingEntity target;
        final long start;
        final int duration;
        final float damage;
        final float posture;
        final float knockback;
        int wave;

        Palms(LivingEntity target, long start, int duration, float damage, float posture, float knockback) {
            this.target = target;
            this.start = start;
            this.duration = duration;
            this.damage = damage;
            this.posture = posture;
            this.knockback = knockback;
        }
    }
}

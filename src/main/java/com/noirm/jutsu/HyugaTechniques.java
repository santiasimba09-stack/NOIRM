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
 *       points closed (usually sealing its jutsu), and the last palm sends it flying. {@code count} sets the
 *       number of waves: 7 is <b>128 Palms</b> (… 64, 128).</li>
 *   <li><b>Air Palm</b> ({@code air_palm}): a blast of compressed chakra from the palm at the first enemy in
 *       {@code range}: damage, knockback, internal damage that ignores armour, and {@code count} points closed.</li>
 *   <li><b>Twin Lion Fists</b> ({@code lion_fists}): chakra lions wrap both fists for {@code duration} ticks; every
 *       Gentle Fist hit closes {@link Byakugan#LION_FISTS_MULTIPLIER}× the points and deals as much more internal
 *       damage.</li>
 * </ul>
 * Every palm also strikes inside the body (internal damage, {@link Byakugan#INTERNAL_DAMAGE}).
 */
public final class HyugaTechniques {
    private static final DustParticleOptions CHAKRA = new DustParticleOptions(new Vector3f(0.6f, 0.8f, 1f), 1.2f);
    private static final int[] WAVES = {2, 4, 8, 16, 32, 64, 128, 256};
    /** Waves when the JSON gives no {@code count} (it defaults to 1): 6, up to 64 palms. */
    private static final int DEFAULT_WAVES = 6;

    // ---------------------------------------------------------------- wave "Hyūga": tunable numbers (docs/TUNING.md)

    /** Byakugan → Gentle Fist accuracy: the eye guides the palm, so 64/128 Palms lock on in this wide a cone (was 25°)… */
    public static final float PALMS_AIM_DEGREES = 40f;
    /** …and the Air Palm in this one (was 20°). */
    public static final float AIR_PALM_AIM_DEGREES = 32f;
    /** Per mastery level (up to Lv 10): Rotation's radius grows this many blocks (3.5 → 5.5)… */
    public static final float ROTATION_RADIUS_PER_LEVEL = 0.2f;
    /** …and it spins this many ticks longer (16 → 36). */
    public static final int ROTATION_TICKS_PER_LEVEL = 2;
    /** Per level: 64/128 Palms close this much more of each wave's points (Lv 10 = ×2). */
    public static final float PALMS_POINTS_PER_LEVEL = 0.1f;
    /** Per level: the Air Palm reaches this much further (10 → 22 blocks) and closes one more point every this many levels. */
    public static final float AIR_PALM_RANGE_PER_LEVEL = 1.2f;
    public static final int AIR_PALM_LEVELS_PER_POINT = 2;
    /** Per level: the Twin Lion Fists burn this many ticks longer (15 s → 25 s). */
    public static final int LION_TICKS_PER_LEVEL = 20;

    /** The caster's mastery level of this technique (players: their book; NPC ninjas: their own practice). */
    static int mastery(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster instanceof Player player) {
            return com.noirm.player.ShinobiCapability.get(player).map(d -> d.jutsu().masteryLevel(ctx.id())).orElse(0);
        }
        return caster instanceof com.noirm.entity.SparringNinja ninja ? ninja.jutsuMastery(ctx.id()) : 0;
    }

    /** Rotation's radius at this mastery level. */
    public static float rotationRadius(float base, int level) {
        return base + ROTATION_RADIUS_PER_LEVEL * level;
    }

    /** The Air Palm's reach at this mastery level. */
    public static float airPalmRange(float base, int level) {
        return base + AIR_PALM_RANGE_PER_LEVEL * level;
    }

    /** How much each palm wave's closed points are multiplied at this mastery level. */
    public static float palmsPoints(int level) {
        return 1f + PALMS_POINTS_PER_LEVEL * level;
    }

    private static final Map<LivingEntity, Spin> SPINS = new WeakHashMap<>();
    private static final Map<LivingEntity, Palms> PALMS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> LIONS = new WeakHashMap<>();

    private HyugaTechniques() {}

    // ---------------------------------------------------------------- Rotation

    static void rotation(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        int level = mastery(ctx);
        Spin spin = new Spin(caster.level().getGameTime() + p.duration() + ROTATION_TICKS_PER_LEVEL * level,
                rotationRadius(p.radius(), level), p.knockback(), p.posture() * ctx.power(), p.damage() * ctx.power());
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
        // The dome: bright chakra rings whirling around at several heights.
        com.noirm.vfx.Vfx.Palette chakra = new com.noirm.vfx.Vfx.Palette(0xFFFFFF, 0x7FD8FF, 0x0A2A6A, 0xE0F8FF);
        double turn = level.getGameTime() * 0.8;
        for (int i = 0; i < 16; i++) {
            double a = turn + i * Math.PI / 8;
            double h = (i % 4) * 0.55;
            double r = spin.radius * Math.sqrt(Math.max(0.15, 1 - Math.pow(h / 2.2, 2)));
            com.noirm.vfx.Vfx.one(level, com.noirm.vfx.Vfx.glow(i % 2 == 0 ? chakra.mid() : chakra.accent(), chakra.edge(), 0.4f, 0.1f, 6),
                    caster.position().add(Math.cos(a) * r, 0.2 + h, Math.sin(a) * r), new net.minecraft.world.phys.Vec3(-Math.sin(a) * 0.3, 0, Math.cos(a) * 0.3));
        }
        if (first) {
            com.noirm.vfx.Vfx.shockwave(level, caster.position(), chakra, spin.radius * 1.4f);
        }
        AABB area = caster.getBoundingBox().inflate(spin.radius);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && e.distanceTo(caster) <= spin.radius)) {
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
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), PALMS_AIM_DEGREES);
        if (target == null) {
            if (caster instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.palms.no_target"), true);
            }
            return;
        }
        int waves = p.count() > 1 ? Mth.clamp(p.count(), 1, WAVES.length) : DEFAULT_WAVES; // count defaults to 1: 64 Palms
        Palms palms = new Palms(target, caster.level().getGameTime(), p.duration(), p.damage() * ctx.power(),
                p.posture() * ctx.power(), p.knockback(), waves, palmsPoints(mastery(ctx)));
        PALMS.put(caster, palms);
        CombatSystem.stun(target, p.duration() + 10);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, p.duration(), 4, false, false));
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (caster instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.palms.start"), true);
        }
    }

    // ---------------------------------------------------------------- Air Palm

    static void airPalm(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        Vec3 look = caster.getLookAngle();
        Vec3 from = caster.getEyePosition().add(look.scale(0.6));
        int stars = mastery(ctx);
        float range = airPalmRange(p.range(), stars);
        LivingEntity target = JutsuBehaviors.lookTarget(caster, range, AIR_PALM_AIM_DEGREES);
        double reach = target != null ? target.distanceTo(caster) : range;
        if (caster.level() instanceof ServerLevel level) {
            com.noirm.vfx.Vfx.Palette chakra = new com.noirm.vfx.Vfx.Palette(0xFFFFFF, 0x9FE0FF, 0x0A2A6A, 0xE0F8FF);
            for (double d = 0; d < reach; d += 0.5) {
                Vec3 at = from.add(look.scale(d));
                com.noirm.vfx.Vfx.one(level, com.noirm.vfx.Vfx.glow(chakra.mid(), chakra.edge(), 0.5f, 0.15f, 5), at, look.scale(0.4));
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        }
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.6f);
        if (target == null) {
            return;
        }
        JutsuEffects.hit(caster, target, p.damage() * ctx.power(), p.knockback(), p.posture() * ctx.power(), 0, target.position().subtract(caster.position()));
        Byakugan.internalDamage(caster, target, p.damage() * ctx.power() * Byakugan.INTERNAL_DAMAGE);
        int points = Math.max(1, p.count()) + stars / AIR_PALM_LEVELS_PER_POINT;
        Byakugan.closePoints(caster, target, points, points * 1.5f);
    }

    // ---------------------------------------------------------------- Twin Lion Fists

    static void lionFists(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        LIONS.put(caster, caster.level().getGameTime() + Math.max(20, ctx.params().duration()) + LION_TICKS_PER_LEVEL * mastery(ctx));
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7f, 1.5f);
        if (caster instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.lion_fists.on"), true);
        }
    }

    /** True while the Twin Lion Fists burn on {@code entity}'s hands. */
    public static boolean lionFistsActive(LivingEntity entity) {
        Long until = LIONS.get(entity);
        return until != null && entity.level().getGameTime() <= until;
    }

    private static void tickLions(LivingEntity entity) {
        if (!lionFistsActive(entity) || !entity.isAlive()) {
            LIONS.remove(entity);
            return;
        }
        if (entity.tickCount % 2 != 0 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // A blue chakra lion's head around each fist.
        com.noirm.vfx.Vfx.Palette chakra = new com.noirm.vfx.Vfx.Palette(0xFFFFFF, 0x6FC8FF, 0x0A2A6A, 0xBFEFFF);
        float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 hand = entity.position().add(-Mth.cos(yaw) * 0.38 * side - Mth.sin(yaw) * 0.3, entity.getBbHeight() * 0.5,
                    -Mth.sin(yaw) * 0.38 * side + Mth.cos(yaw) * 0.3);
            com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.glow(chakra.mid(), chakra.edge(), 0.55f, 0.2f, 6), hand, 3, 0.12, 0.02);
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
        if (LIONS.containsKey(entity)) {
            tickLions(entity);
        }
    }

    private static void tickPalms(LivingEntity caster, Palms palms) {
        LivingEntity target = palms.target;
        long elapsed = caster.level().getGameTime() - palms.start;
        if (!caster.isAlive() || !target.isAlive() || caster.distanceTo(target) > 6 || CombatSystem.isStaggered(caster)) {
            PALMS.remove(caster);
            return;
        }
        int interval = Math.max(1, palms.duration / palms.waves);
        if (elapsed % interval != 0 || palms.wave >= palms.waves) {
            return;
        }
        int count = WAVES[palms.wave++];
        boolean last = palms.wave == palms.waves;
        caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        Vec3 direction = target.position().subtract(caster.position());
        if (last) {
            JutsuEffects.hit(caster, target, palms.damage, palms.knockback, palms.posture, 0, direction); // the last palm sends them flying
        } else {
            // The waves before it hold them in place: vanilla's push on every hit used to knock the
            // target out of reach after a few waves, so the technique rarely finished.
            CombatSystem.withoutVanillaKnockback(() -> JutsuEffects.hit(caster, target, palms.damage, 0, palms.posture, 0, direction));
        }
        Byakugan.internalDamage(caster, target, palms.damage * Byakugan.INTERNAL_DAMAGE); // every wave strikes inside
        Byakugan.closePoints(caster, target, Math.max(1, Math.round(count / 2f * palms.points)), count * 0.6f);
        if (!last) {
            CombatSystem.stun(target, interval + 6);
        }
        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(CHAKRA, target.getX(), target.getY() + 1.1, target.getZ(), 6 + count / 2, 0.3, 0.4, 0.3, 0.05);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.1, target.getZ(), 4 + count / 4, 0.3, 0.4, 0.3, 0.2);
        }
        float pitch = Mth.clamp(0.9f + palms.wave * 0.12f, 0.9f, 1.8f);
        caster.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                last ? com.noirm.sound.NoirmSounds.HIT_HEAVY.get() : com.noirm.sound.NoirmSounds.HIT_LIGHT.get(), SoundSource.PLAYERS, 1f, pitch);
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
        final int waves;
        /** Mastery: each wave's closed points are multiplied by this. */
        final float points;
        int wave;

        Palms(LivingEntity target, long start, int duration, float damage, float posture, float knockback, int waves, float points) {
            this.waves = waves;
            this.points = points;
            this.target = target;
            this.start = start;
            this.duration = duration;
            this.damage = damage;
            this.posture = posture;
            this.knockback = knockback;
        }
    }
}

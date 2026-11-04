package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.vfx.KekkeiVfxA;
import com.noirm.vfx.LightningVfx;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Storm and Explosion release (the chakra wave, share A; see {@link KekkeiTechniquesA}).
 * <ul>
 *   <li><b>storm_cloud</b> (Black Cloud): a small thundercloud drifts to your aim and hangs there for
 *       {@code duration} ticks; every second it darkens the eyes of whoever is under it ({@code effect})
 *       and snaps a bolt into one of them.</li>
 *   <li><b>laser</b> (Light Fang, charged): a straight laser ({@code range}) piercing everyone in line.</li>
 *   <li><b>storm_tide</b> (Raging Thunder Tide): a wave of charged black water ({@code radius} wide
 *       each side) rolls {@code range} blocks, stunning and carrying whoever it passes.</li>
 *   <li><b>bending_laser</b> (Laser Circus: Hundred Bends): one laser per enemy in {@code range}
 *       (up to {@code count}), each bending through the air to find its mark.</li>
 *   <li><b>blast_fist</b> (Detonating Fist): a punch of explosive chakra that goes off on contact.</li>
 *   <li><b>clay_bomb</b> (Great Clay Effigy): a huge clay figure drops on your aim and explodes.</li>
 *   <li><b>mega_blast</b> (Final Art, charged long): you become the bomb. A blast of {@code radius}
 *       around you, the biggest in the game, and a heavy backlash: most of your health, all of your
 *       chakra, and you lie crippled for a while.</li>
 * </ul>
 */
public final class KekkeiTechniquesAStormBlast {
    /** The Black Cloud drifts this many ticks before it settles. */
    public static final int CLOUD_DRIFT = 16;
    /** Ticks between two lasers of the Hundred Bends. */
    public static final int BEND_DELAY = 2;
    /** Final Art's backlash: health left (share of what you had), and how long you're crippled. */
    public static final float BACKLASH_HEALTH_KEPT = 0.35f;
    public static final int BACKLASH_TICKS = 300;
    public static final int BACKLASH_STUN = 40;

    private KekkeiTechniquesAStormBlast() {}

    // ================================================================ Black Cloud

    static void stormCloud(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 start = caster.getEyePosition().add(ctx.look().scale(1.5)).add(0, 0.6, 0);
        Vec3 settle = KekkeiTechniquesA.groundAim(caster, level, p.range()).add(0, 3.2, 0);
        float radius = Math.max(1.5f, p.radius());
        int duration = Math.max(CLOUD_DRIFT + 10, p.duration());
        int[] age = {0};
        KekkeiVfxA.cloud(level, start, 1f, true);
        KekkeiTechniquesA.playAt(level, start, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
        GreatTechniques.later(now -> {
            age[0]++;
            float t = Math.min(1f, age[0] / (float) CLOUD_DRIFT);
            Vec3 at = start.add(settle.subtract(start).scale(t * (2 - t)));
            if (age[0] % 2 == 0) {
                KekkeiVfxA.cloud(level, at, radius * (0.5f + 0.5f * t), age[0] % 6 == 0);
            }
            if (age[0] >= CLOUD_DRIFT && age[0] % 20 == 0) {
                List<LivingEntity> under = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 5, radius),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator());
                for (LivingEntity victim : under) {
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                }
                if (!under.isEmpty()) {
                    LivingEntity struck = under.get(level.random.nextInt(under.size()));
                    Vec3 chest = struck.position().add(0, struck.getBbHeight() * 0.6, 0);
                    LightningVfx.bolt(level, at, chest, LightningVfx.BLUE_WHITE, 0.1f, 5, 3, LightningVfx.FORKY | LightningVfx.IMPACT);
                    LightningVfx.contact(level, chest, 0.8f);
                    JutsuEffects.hit(caster, struck, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
                    if (p.stun() > 0) {
                        CombatSystem.stun(struck, p.stun());
                    }
                    KekkeiTechniquesA.playAt(level, chest, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.7f);
                }
            }
            return age[0] >= duration || !caster.isAlive();
        });
    }

    // ================================================================ Light Fang

    static void laser(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        float charge = ctx.charge();
        Vec3 from = KekkeiTechniquesA.hands(caster);
        Vec3 dir = ctx.look();
        Vec3 end = clip(level, caster, from, dir, p.range());
        float width = 0.2f + 0.15f * charge;
        float damage = (p.damage() + p.chargeDamage() * charge) * power;
        KekkeiVfxA.laser(level, from, end, width);
        KekkeiVfxA.cloud(level, from, 0.8f, true);
        Vfx.quake(level, from, 0.3f + 0.3f * charge, 14);
        Set<LivingEntity> hit = new HashSet<>();
        pierce(caster, level, from, end, 0.6, hit, victim -> {
            JutsuEffects.hit(caster, victim, damage, p.knockback(), p.posture() * power, 0, dir);
            if (p.stun() > 0) {
                CombatSystem.stun(victim, p.stun());
            }
            LightningVfx.contact(level, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 1f);
        });
        Destruction.crater(level, end, 0.9f + charge * 0.6f, power);
        // The beam burns on the eye for a moment.
        int[] age = {0};
        GreatTechniques.later(now -> {
            KekkeiVfxA.laser(level, from, end, width * (0.8f - 0.25f * age[0]));
            return ++age[0] >= 2;
        });
        KekkeiTechniquesA.playAt(level, from, SoundEvents.BEACON_DEACTIVATE, 1.5f, 1.6f);
        KekkeiTechniquesA.playAt(level, end, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.4f);
    }

    /** Where a line from {@code from} along {@code dir} meets a wall, up to {@code range}. */
    private static Vec3 clip(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir, double range) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return block.getType() == HitResult.Type.MISS ? to : block.getLocation();
    }

    /** Everyone along the segment (within {@code reach}) not yet in {@code hit}. */
    private static void pierce(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 to, double reach, Set<LivingEntity> hit,
                               java.util.function.Consumer<LivingEntity> onHit) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 1e-3) {
            return;
        }
        dir = dir.scale(1 / length);
        for (double d = 0; d <= length; d += 0.4) {
            Vec3 point = from.add(dir.scale(d));
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(reach),
                    e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && !hit.contains(e))) {
                hit.add(victim);
                onHit.accept(victim);
            }
            if (((int) (d / 0.4)) % 3 == 0) {
                Destruction.shatterFragile(level, BlockPos.containing(point), 0);
            }
        }
    }

    // ================================================================ Raging Thunder Tide

    static void stormTide(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 dir = ctx.flatLook();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float halfWidth = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        Vec3 origin = caster.position().add(dir.scale(1.5));
        double speed = Math.max(0.2, p.speed());
        double height = 1.6 + halfWidth * 0.4;
        Set<LivingEntity> hit = new HashSet<>();
        double[] travelled = {0};
        KekkeiVfxA.cloud(level, caster.position().add(ctx.flatLook().scale(3)).add(0, 5, 0), 2.5f, true);
        Vfx.quake(level, caster.position(), 0.6f, 16);
        KekkeiTechniquesA.playAt(level, caster.position(), SoundEvents.GENERIC_SPLASH, 2f, 0.5f);
        KekkeiTechniquesA.playAt(level, caster.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
        GreatTechniques.later(now -> {
            travelled[0] += speed;
            if (travelled[0] > p.range() || !caster.isAlive()) {
                return true;
            }
            Vec3 front = origin.add(dir.scale(travelled[0]));
            for (double s = -halfWidth; s <= halfWidth; s += 1.0) {
                KekkeiVfxA.tideSlice(level, front.add(side.scale(s)), height * (1.0 - 0.3 * Math.abs(s) / Math.max(1, halfWidth)), dir);
            }
            AABB band = new AABB(front.add(side.scale(-halfWidth)).add(0, -1, 0), front.add(side.scale(halfWidth)).add(0, height, 0)).inflate(0.8);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, band, e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator())) {
                if (hit.add(victim)) {
                    JutsuEffects.hit(caster, victim, p.damage() * power, 0, p.posture() * power, 0, dir);
                    if (p.stun() > 0) {
                        CombatSystem.stun(victim, p.stun());
                    }
                    LightningVfx.contact(level, victim.position().add(0, 1, 0), 1f);
                }
                victim.setDeltaMovement(dir.scale(p.knockback() * 0.5).add(0, 0.1, 0));
                victim.hurtMarked = true;
            }
            if (now % 3 == 0) {
                Destruction.douse(level, front, halfWidth);
                KekkeiTechniquesA.playAt(level, front, SoundEvents.GENERIC_SPLASH, 1f, 0.7f);
            }
            return false;
        });
    }

    // ================================================================ Laser Circus: Hundred Bends

    static void bendingLaser(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 from = KekkeiTechniquesA.hands(caster);
        List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(p.range()),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && e.distanceTo(caster) <= p.range() && caster.hasLineOfSight(e)));
        targets.sort(Comparator.comparingDouble(e -> e.distanceTo(caster)));
        if (targets.size() > p.count()) {
            targets = new ArrayList<>(targets.subList(0, Math.max(1, p.count())));
        }
        // A dark cloud boils above the caster and the lasers spill out of it.
        KekkeiVfxA.cloud(level, caster.position().add(ctx.flatLook().scale(3)).add(0, 5.5, 0), 3f, true);
        Vfx.quake(level, caster.position(), 0.7f, 20);
        KekkeiTechniquesA.playAt(level, from, SoundEvents.BEACON_ACTIVATE, 2f, 1.8f);
        if (targets.isEmpty()) {
            // Nothing to find: the lasers fan out ahead and bend down into the ground.
            for (int i = 0; i < 4; i++) {
                Vec3 dir = ctx.look().yRot((i - 1.5f) * 0.3f);
                Vec3 end = clip(level, caster, from, dir.add(0, -0.2, 0).normalize(), p.range() * 0.6);
                drawBent(level, from, end, dir, 0.18f);
            }
            return;
        }
        List<LivingEntity> queue = targets;
        int[] next = {0};
        int[] wait = {0};
        GreatTechniques.later(now -> {
            if (wait[0]-- > 0) {
                return false;
            }
            wait[0] = BEND_DELAY - 1;
            LivingEntity target = queue.get(next[0]++);
            if (target.isAlive() && target.level() == level) {
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
                Vec3 out = ctx.look().yRot((level.random.nextFloat() - 0.5f) * 1.6f).add(0, 0.3 + level.random.nextFloat() * 0.4, 0).normalize();
                drawBent(level, from, to, out, 0.2f);
                JutsuEffects.hit(caster, target, p.damage() * power, p.knockback(), p.posture() * power, 0, to.subtract(from));
                if (p.stun() > 0) {
                    CombatSystem.stun(target, p.stun());
                }
                LightningVfx.contact(level, to, 1.2f);
                LightningVfx.groundForks(level, target.position(), 2f, 4);
                KekkeiTechniquesA.playAt(level, to, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
            }
            return next[0] >= queue.size();
        });
    }

    /** A laser that leaves along {@code out}, bends twice in the air, and ends at {@code to}. */
    private static void drawBent(ServerLevel level, Vec3 from, Vec3 to, Vec3 out, float width) {
        double span = from.distanceTo(to);
        Vec3 a = from.add(out.scale(Math.max(3.5, span * 0.45)));
        Vec3 mid = a.add(to).scale(0.5);
        Vec3 b = mid.add(level.random.nextGaussian() * span * 0.15, Math.abs(level.random.nextGaussian()) * span * 0.12, level.random.nextGaussian() * span * 0.15);
        KekkeiVfxA.laser(level, from, a, width);
        KekkeiVfxA.laser(level, a, b, width);
        KekkeiVfxA.laser(level, b, to, width);
        int[] age = {0};
        GreatTechniques.later(now -> {
            if (++age[0] % 3 == 0) { // it burns on the eye a moment longer
                KekkeiVfxA.laser(level, from, a, width * 0.7f);
                KekkeiVfxA.laser(level, a, b, width * 0.7f);
                KekkeiVfxA.laser(level, b, to, width * 0.7f);
            }
            return age[0] >= 6;
        });
        Vfx.burst(level, new VfxOptions(VfxKind.ZAP, 0xFFFFFF, KekkeiVfxA.STORM_BLUE, 0.6f, 0.9f, 4, 0f, 1f), a, 1, 0, 0);
        Vfx.burst(level, new VfxOptions(VfxKind.ZAP, 0xFFFFFF, KekkeiVfxA.STORM_BLUE, 0.6f, 0.9f, 4, 0f, 1f), b, 1, 0, 0);
    }

    // ================================================================ Detonating Fist

    static void blastFist(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 look = ctx.flatLook();
        LivingEntity target = JutsuBehaviors.inCone(caster, p.range(), 40).stream()
                .min(Comparator.comparingDouble(e -> e.distanceTo(caster))).orElse(null);
        Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0)
                : caster.getEyePosition().add(look.scale(1.6)).add(0, -0.4, 0);
        float radius = 1.6f;
        KekkeiVfxA.blast(level, at, radius);
        KekkeiTechniquesA.playAt(level, at, SoundEvents.GENERIC_EXPLODE, 1.6f, 1.1f);
        KekkeiTechniquesA.playAt(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
        for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, at, radius + 0.6)) {
            JutsuEffects.hit(caster, victim, p.damage() * power * (victim == target || target == null ? 1f : 0.6f), p.knockback(), p.posture() * power, 0,
                    victim.position().subtract(caster.position()));
            if (p.stun() > 0) {
                CombatSystem.stun(victim, p.stun());
            }
        }
        Destruction.crater(level, at.subtract(0, 0.6, 0), 1.2f, power);
    }

    // ================================================================ Great Clay Effigy

    static void clayBomb(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        Vec3[] spot = {KekkeiTechniquesA.groundAim(caster, level, p.range())};
        int fall = Math.max(8, p.duration());
        double height = 16;
        int[] age = {0};
        KekkeiTechniquesA.playAt(level, caster.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 1.6f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (target != null && target.isAlive() && age[0] < fall - 4) {
                spot[0] = ElementalBehaviors.ground(level, target.position()); // it steers onto its mark until the last moment
            }
            Vec3 ground = spot[0];
            if (age[0] < fall) {
                double t = age[0] / (double) fall;
                Vec3 body = ground.add(0, 1.5 + height * (1 - t * t), 0);
                effigy(level, body, 1.4f, age[0]);
                if (age[0] % 4 == 0) {
                    Vfx.burst(level, Vfx.ring(0xFF6A10, radius * (1.2f - 0.4f * (float) t), 6), ground.add(0, 0.1, 0), 1, 0, 0);
                }
                return false;
            }
            KekkeiVfxA.blast(level, ground.add(0, 1, 0), radius);
            KekkeiTechniquesA.playAt(level, ground, SoundEvents.GENERIC_EXPLODE, 3f, 0.6f);
            for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, ground.add(0, 1, 0), radius)) {
                float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(ground) / (radius + 1));
                JutsuEffects.hit(caster, victim, p.damage() * power * (0.4f + 0.6f * falloff), p.knockback(), p.posture() * power, p.fireSeconds(),
                        victim.position().subtract(ground));
            }
            Destruction.crater(level, ground, radius * 0.55f, power * 1.3f);
            return true;
        });
    }

    /** One tick of the falling clay figure: a big chalky body, a head, stubby arms, glowing seams. */
    private static void effigy(ServerLevel level, Vec3 body, float s, int age) {
        Vfx.one(level, KekkeiVfxA.clay(s * 2.2f, 2), body, Vec3.ZERO);
        Vfx.one(level, KekkeiVfxA.clay(s * 1.3f, 2), body.add(0, s * 1.3, 0), Vec3.ZERO);
        Vfx.one(level, KekkeiVfxA.clay(s * 0.9f, 2), body.add(s * 1.1, s * 0.4, 0), Vec3.ZERO);
        Vfx.one(level, KekkeiVfxA.clay(s * 0.9f, 2), body.add(-s * 1.1, s * 0.4, 0), Vec3.ZERO);
        Vfx.one(level, KekkeiVfxA.clay(s * 0.9f, 2), body.add(0, s * 0.4, s * 1.1), Vec3.ZERO);
        Vfx.one(level, KekkeiVfxA.clay(s * 0.9f, 2), body.add(0, s * 0.4, -s * 1.1), Vec3.ZERO);
        Vfx.one(level, Vfx.glow(0xFFE0A0, 0xFF6A10, s * (0.6f + 0.1f * (age % 3)), s * 0.4f, 2), body, Vec3.ZERO);
        Vfx.one(level, KekkeiVfxA.clay(s * 0.7f, 18), body.add(0, s, 0), new Vec3(0, 0.15, 0)); // a trail of chalk dust above it
    }

    // ================================================================ Final Art

    static void megaBlast(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        float radius = p.radius() * (0.6f + 0.4f * ctx.charge()) * (float) Math.sqrt(Math.max(0.5f, ctx.power()));
        Vec3 center = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
        // A white flash that swallows everything, then the fire, then the rings of smoke.
        KekkeiVfxA.flashCloud(level, center, 0xFFFFFF, 0xFFFFFF, radius * 1.6f, 6);
        KekkeiVfxA.blast(level, center, radius * 0.75f);
        Vfx.quake(level, center, 1.5f, radius * 8);
        KekkeiTechniquesA.playAt(level, center, SoundEvents.GENERIC_EXPLODE, 6f, 0.4f);
        KekkeiTechniquesA.playAt(level, center, SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.6f);
        for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, center, radius)) {
            float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(center) / (radius + 1));
            JutsuEffects.hit(caster, victim, p.damage() * power * (0.35f + 0.65f * falloff), p.knockback() * (0.5f + 0.5f * falloff),
                    p.posture() * power, p.fireSeconds(), victim.position().subtract(center));
        }
        // A wide, shallow bowl (not a pit the caster vanishes into).
        Destruction.crater(level, caster.position().add(0, radius * 0.35, 0), radius * 0.48f, power * 2f);
        Destruction.scorch(level, caster.position(), radius * 0.45f);
        int[] age = {0};
        GreatTechniques.later(now -> {
            age[0]++;
            if (age[0] == 4) {
                KekkeiVfxA.smokeRing(level, center.add(0, -0.6, 0), radius * 0.6f, 0.7f, 28, 1.6f, 0f);
            }
            if (age[0] == 8) {
                KekkeiVfxA.smokeRing(level, center.add(0, radius * 0.5, 0), radius * 0.4f, 0.4f, 20, 1.4f, 0.12f);
                KekkeiVfxA.blast(level, center.add(0, radius * 0.4, 0), radius * 0.4f);
            }
            return age[0] >= 8;
        });
        backlash(caster);
    }

    /** What Final Art costs the body: most of its health, every drop of chakra, and a long collapse. */
    static void backlash(LivingEntity caster) {
        caster.setHealth(Math.max(1f, caster.getHealth() * BACKLASH_HEALTH_KEPT));
        caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BACKLASH_TICKS, 1));
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BACKLASH_TICKS, 1));
        caster.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, BACKLASH_TICKS, 1));
        CombatSystem.knockDown(caster, BACKLASH_STUN);
        if (caster instanceof Player player && !player.getAbilities().instabuild) {
            ShinobiCapability.get(player).ifPresent(d -> {
                d.setChakra(0f);
                d.markPowersDirty();
            });
        }
    }
}

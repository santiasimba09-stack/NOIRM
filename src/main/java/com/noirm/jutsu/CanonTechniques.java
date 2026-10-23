package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.entity.ShadowClone;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import com.noirm.vfx.Vfx;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Canon techniques that need their own mechanics (see docs/RESEARCH.md §1):
 * <ul>
 *   <li><b>dome</b>: Water Formation Wall: water blown out in a circle around you; nothing gets
 *       through while it stands, and it puts out fire.</li>
 *   <li><b>heal</b>: Mystical Palm (Yang): channel to heal whoever you look at, or yourself.</li>
 *   <li><b>rasengan</b>: form a spinning sphere in your palm (charge), then drive it into the
 *       enemy in front: grinding hits, then a spiral blast that throws them and leaves a crater.</li>
 *   <li><b>shadow_bind</b>: Shadow Imitation (Yin): channel; your shadow runs along the ground to
 *       the target, who then can't act and must copy your movement. Weaker in the dark.</li>
 *   <li><b>shadow_clone</b>: solid clones of you that fight; your chakra is divided with them.</li>
 * </ul>
 */
public final class CanonTechniques {
    private record Dome(long until, float radius) {}

    private static final class Grind {
        final LivingEntity target;
        final float damagePerTick;
        final float knockback;
        final float radius;
        final float power;
        int ticks;

        Grind(LivingEntity target, float damagePerTick, float knockback, float radius, float power, int ticks) {
            this.target = target;
            this.damagePerTick = damagePerTick;
            this.knockback = knockback;
            this.radius = radius;
            this.power = power;
            this.ticks = ticks;
        }
    }

    private static final class Bind {
        final LivingEntity target;
        Vec3 tip;
        boolean caught;
        long lastTick;

        Bind(LivingEntity target, Vec3 tip, long tick) {
            this.target = target;
            this.tip = tip;
            this.lastTick = tick;
        }
    }

    private static final Map<LivingEntity, Dome> DOMES = new WeakHashMap<>();
    private static final Map<LivingEntity, Grind> GRINDS = new WeakHashMap<>();
    private static final Map<LivingEntity, Bind> BINDS = new WeakHashMap<>();

    private CanonTechniques() {}

    // ---------------------------------------------------------------- Water Formation Wall

    static void dome(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        float radius = ctx.params().radius() * (float) Math.sqrt(Math.max(0.5f, ctx.power()));
        DOMES.put(caster, new Dome(caster.level().getGameTime() + ctx.params().duration(), radius));
        Destruction.douse(ctx.level(), caster.position(), radius + 1);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    /** Is a dome (or similar) stopping attacks on {@code target}? Called before every hit. */
    public static boolean deflect(LivingEntity target, Entity attacker) {
        Dome dome = DOMES.get(target);
        if (dome == null || target.level().getGameTime() > dome.until()) {
            return false;
        }
        Vfx.hit(target.level(), target.position().add(0, 1, 0), Vfx.palette(com.noirm.clan.Nature.WATER), false);
        return true;
    }

    // ---------------------------------------------------------------- Mystical Palm

    static final JutsuBehavior HEAL = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            ctx.caster().level().playSound(null, ctx.caster().blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            LivingEntity caster = ctx.caster();
            LivingEntity target = JutsuBehaviors.lookTarget(caster, ctx.params().range(), 20);
            LivingEntity patient = target != null ? target : caster;
            patient.heal(ctx.params().damage() * ctx.power() / 20f);
            Vec3 hand = caster.getEyePosition().add(caster.getViewVector(1f).scale(0.7)).add(0, -0.4, 0);
            Vfx.Palette palette = Vfx.palette(com.noirm.clan.Nature.YANG);
            Vfx.burst(caster.level(), Vfx.glow(palette.core(), palette.mid(), 0.3f, 0.1f, 6), hand, 1, 0.1, 0.01);
            if (tick % 3 == 0) {
                Vfx.aura(caster.level(), patient.position(), patient.getBbWidth(), patient.getBbHeight(), palette, 3);
            }
        }
    };

    // ---------------------------------------------------------------- Rasengan

    static void rasengan(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        LivingEntity target = JutsuBehaviors.lookTarget(caster, ctx.params().range(), 30);
        Vfx.Palette palette = new Vfx.Palette(0xFFFFFF, 0x7FC8FF, 0x2050A0);
        if (target == null) {
            // Nothing to hit: it bursts against whatever is in front.
            Vec3 front = caster.getEyePosition().add(caster.getViewVector(1f).scale(2));
            Vfx.explosion(level, front, palette, 1.2f * power);
            Destruction.crater(level, front, 1.2f * power, power);
            return;
        }
        // Close the gap, then grind it in.
        Vec3 toward = target.position().subtract(caster.position());
        if (toward.horizontalDistance() > 2.2) {
            Vec3 stop = target.position().subtract(new Vec3(toward.x, 0, toward.z).normalize().scale(1.6));
            if (level.noCollision(caster, caster.getBoundingBox().move(stop.subtract(caster.position())))) {
                caster.teleportTo(stop.x, stop.y, stop.z);
            }
        }
        float damage = ctx.params().damage() * power;
        GRINDS.put(caster, new Grind(target, damage * 0.5f / 6f, ctx.params().knockback() * power, ctx.params().radius() * power, power, 6));
        CombatSystem.stun(target, 12);
        caster.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.PLAYERS, 1.2f, 1.6f);
    }

    /** The Rasengan's orb while it's being formed: chakra spinning in many directions in the palm. */
    public static void rasenganOrb(LivingEntity caster, float progress) {
        Vec3 hand = caster.getEyePosition().add(caster.getViewVector(1f).scale(0.8)).add(0, -0.4, 0);
        float size = 0.25f + 0.45f * progress;
        Vfx.burst(caster.level(), Vfx.glow(0xFFFFFF, 0x7FC8FF, size, size * 0.6f, 3), hand, 1, 0, 0);
        long t = caster.level().getGameTime();
        for (int i = 0; i < 4; i++) {
            double a = t * 0.9 + i * Math.PI / 2;
            Vec3 ring = new Vec3(Math.cos(a) * size * 0.8, Math.sin(a * 1.3) * size * 0.8, Math.sin(a) * size * 0.8);
            Vfx.one(caster.level(), Vfx.glow(0xBFE6FF, 0x2050A0, 0.12f, 0.04f, 5), hand.add(ring), Vec3.ZERO);
        }
    }

    // ---------------------------------------------------------------- Shadow Imitation

    static final JutsuBehavior SHADOW_BIND = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            LivingEntity caster = ctx.caster();
            float light = lightFactor(caster);
            LivingEntity target = JutsuBehaviors.lookTarget(caster, ctx.params().range() * light, 20);
            if (target == null) {
                JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
                return;
            }
            BINDS.put(caster, new Bind(target, caster.position(), caster.level().getGameTime()));
            caster.level().playSound(null, caster.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1f, 0.6f);
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            LivingEntity caster = ctx.caster();
            Bind bind = BINDS.get(caster);
            if (bind == null || !bind.target.isAlive()) {
                return;
            }
            bind.lastTick = caster.level().getGameTime();
            Vfx.Palette palette = Vfx.palette(com.noirm.clan.Nature.YIN);
            Vec3 goal = bind.target.position();
            if (!bind.caught) {
                // The shadow runs along the ground toward the target.
                Vec3 step = goal.subtract(bind.tip);
                double speed = 1.2 * lightFactor(caster);
                bind.tip = step.length() <= speed ? goal : bind.tip.add(step.normalize().scale(speed));
                if (bind.tip.distanceTo(goal) < 0.6) {
                    bind.caught = true;
                    JutsuBehaviors.message(caster, Component.translatable("message.noirm.shadow.caught", bind.target.getDisplayName()));
                }
            }
            // Draw the shadow: a dark line on the ground from the caster to its tip.
            Vec3 from = caster.position().add(0, 0.05, 0);
            Vec3 to = bind.tip.add(0, 0.05, 0);
            double length = from.distanceTo(to);
            for (double d = 0; d < length; d += 0.5) {
                Vec3 at = from.add(to.subtract(from).normalize().scale(d));
                Vfx.one(caster.level(), new com.noirm.vfx.VfxOptions(com.noirm.vfx.VfxKind.SMOKE, 0x0A0A10, 0x000000, 0.45f, 0.5f, 4, 0f, 0.5f), at, Vec3.ZERO);
            }
            if (bind.caught) {
                LivingEntity target = bind.target;
                CombatSystem.stun(target, 4);
                // The target copies the caster's movement.
                Vec3 motion = caster.getDeltaMovement();
                target.setDeltaMovement(motion.x, target.getDeltaMovement().y, motion.z);
                target.setYRot(caster.getYRot());
                target.setYHeadRot(caster.getYHeadRot());
                target.hurtMarked = true;
                if (tick % 4 == 0) {
                    Vfx.aura(caster.level(), target.position(), target.getBbWidth(), 0.4f, palette, 2);
                }
            }
        }
    };

    /** Shadows are weaker in the dark: half range and speed below light level 7. */
    static float lightFactor(LivingEntity caster) {
        return caster.level().getMaxLocalRawBrightness(caster.blockPosition()) >= 7 ? 1f : 0.5f;
    }

    public static boolean isBound(LivingEntity target) {
        for (Bind bind : BINDS.values()) {
            if (bind.caught && bind.target == target) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Shadow Clone

    static void shadowClone(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        int count = Math.max(1, ctx.params().count());
        if (caster instanceof Player player && !player.getAbilities().instabuild) {
            // Chakra is divided evenly between you and your clones.
            ShinobiCapability.get(player).ifPresent(d -> d.setChakra(d.getChakra() / (count + 1)));
        }
        List<ShadowClone> existing = level.getEntitiesOfClass(ShadowClone.class, caster.getBoundingBox().inflate(64), c -> c.isOwnedBy(caster));
        for (int i = 0; i < existing.size() + count - ShadowClone.MAX_PER_OWNER; i++) {
            existing.get(i).dispel();
        }
        for (int i = 0; i < count; i++) {
            ShadowClone clone = NoirmEntities.SHADOW_CLONE.get().create(level);
            if (clone == null) {
                continue;
            }
            double angle = Math.toRadians(caster.getYRot()) + (i - (count - 1) / 2.0) * 1.2 + Math.PI / 2;
            Vec3 at = caster.position().add(Math.cos(angle) * 1.6, 0, Math.sin(angle) * 1.6);
            if (!level.noCollision(clone, clone.getType().getAABB(at.x, at.y, at.z))) {
                at = caster.position();
            }
            clone.moveTo(at.x, at.y, at.z, caster.getYRot(), 0f);
            clone.setOwner(caster);
            clone.setLife(ctx.params().duration());
            level.addFreshEntity(clone);
            level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 25, 0.3, 0.6, 0.3, 0.03);
            Vfx.burst(level, Vfx.smoke(0xE8E8E8, 0.8f, 30), at.add(0, 1, 0), 8, 0.4, 0.03);
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 0.5f);
    }

    // ---------------------------------------------------------------- ticking

    /** Every server tick, for every living entity. */
    public static void tick(LivingEntity entity) {
        Dome dome = DOMES.get(entity);
        if (dome != null) {
            if (entity.level().getGameTime() > dome.until() || !entity.isAlive()) {
                DOMES.remove(entity);
            } else {
                tickDome(entity, dome);
            }
        }
        Grind grind = GRINDS.get(entity);
        if (grind != null) {
            tickGrind(entity, grind);
        }
        Bind bind = BINDS.get(entity);
        if (bind != null && entity.level().getGameTime() - bind.lastTick > 3) {
            BINDS.remove(entity);
        }
    }

    private static void tickDome(LivingEntity caster, Dome dome) {
        Vfx.Palette palette = Vfx.palette(com.noirm.clan.Nature.WATER);
        long t = caster.level().getGameTime();
        for (int i = 0; i < 14; i++) {
            double a = t * 0.35 + i * (Math.PI * 2 / 14);
            double h = (i % 4) * 0.6;
            double r = dome.radius() * Math.sqrt(Math.max(0.1, 1 - Math.pow(h / 2.6, 2)));
            Vec3 at = caster.position().add(Math.cos(a) * r, 0.2 + h, Math.sin(a) * r);
            Vfx.one(caster.level(), Vfx.glow(palette.core(), palette.mid(), 0.45f, 0.2f, 6), at, new Vec3(0, 0.08, 0));
            if (i % 3 == 0) {
                Vfx.burst(caster.level(), ParticleTypes.SPLASH, at, 2, 0.1, 0.05);
            }
        }
        for (Projectile projectile : caster.level().getEntitiesOfClass(Projectile.class, caster.getBoundingBox().inflate(dome.radius() + 1),
                p -> p.getOwner() != caster && p.distanceTo(caster) <= dome.radius() + 1)) {
            Vfx.hit(caster.level(), projectile.position(), palette, false);
            projectile.discard();
        }
    }

    private static void tickGrind(LivingEntity caster, Grind grind) {
        LivingEntity target = grind.target;
        if (!target.isAlive() || caster.distanceTo(target) > 5) {
            GRINDS.remove(caster);
            return;
        }
        Vec3 contact = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vfx.Palette palette = new Vfx.Palette(0xFFFFFF, 0x7FC8FF, 0x2050A0);
        if (grind.ticks > 0) {
            grind.ticks--;
            target.invulnerableTime = 0;
            JutsuEffects.hit(caster, target, grind.damagePerTick, 0, 4, 0, Vec3.ZERO);
            CombatSystem.stun(target, 6);
            // The sphere holds them in place while it grinds.
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            for (int i = 0; i < 6; i++) {
                double a = caster.level().getGameTime() * 1.3 + i;
                Vfx.one(caster.level(), Vfx.glow(0xFFFFFF, 0x7FC8FF, 0.35f, 0.1f, 6), contact,
                        new Vec3(Math.cos(a) * 0.35, Math.sin(a * 0.7) * 0.2, Math.sin(a) * 0.35));
            }
            Vfx.radial(caster.level(), Vfx.spark(0x7FC8FF, 0.18f, 6), contact, 4, 0.5);
            return;
        }
        // The spiral blast: the rest of the damage, a big throw, a crater.
        GRINDS.remove(caster);
        Vec3 dir = target.position().subtract(caster.position());
        Vec3 flat = new Vec3(dir.x, 0, dir.z).lengthSqr() < 1e-4 ? caster.getViewVector(1f) : new Vec3(dir.x, 0, dir.z).normalize();
        target.invulnerableTime = 0;
        JutsuEffects.hit(caster, target, grind.damagePerTick * 6f, 0, 30 * grind.power, 0, Vec3.ZERO);
        target.setDeltaMovement(flat.scale(grind.knockback).add(0, 0.35, 0));
        target.hurtMarked = true;
        if (caster.level() instanceof ServerLevel level) {
            Vfx.explosion(level, contact, palette, 1.4f * grind.power);
            Vfx.shockwave(level, target.position(), palette, 3f * grind.power);
            Destruction.crater(level, target.position(), grind.radius, grind.power);
            Destruction.shatterFragile(level, BlockPos.containing(contact), 2);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 1.4f);
        }
    }

    public static boolean hasDome(LivingEntity entity) {
        Dome dome = DOMES.get(entity);
        return dome != null && entity.level().getGameTime() <= dome.until();
    }

    /** Is {@code caster} mid-Rasengan? */
    public static boolean isGrinding(LivingEntity caster) {
        return GRINDS.containsKey(caster);
    }
}

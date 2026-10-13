package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.vfx.Vfx;
import com.noirm.world.Destruction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Large-scale techniques that play out over time:
 * <ul>
 *   <li><b>strike</b>: Kirin. The sky darkens over the target for {@code duration} ticks, then a
 *       colossal bolt falls: everything within {@code radius} is blasted and the ground cratered.</li>
 *   <li><b>wave</b>: a wall of the jutsu's nature ({@code radius} wide each side) rolls forward
 *       {@code range} blocks at {@code speed} blocks a tick, hitting and carrying whatever it passes
 *       (Great Fire Annihilation, Great Waterfall, Pressure Damage).</li>
 *   <li><b>nova</b>: a discharge in every direction around you (Chidori Current).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class GreatTechniques {
    private static final List<Pending> PENDING = new ArrayList<>();

    private GreatTechniques() {}

    private interface Pending {
        /** Returns true when done. */
        boolean tick(long now);
    }

    // ---------------------------------------------------------------- strike (Kirin)

    static void strike(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 25);
        Vec3 at;
        if (target != null) {
            at = target.position();
        } else {
            Vec3 eye = caster.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(ctx.look().scale(p.range())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            at = hit.getType() == HitResult.Type.MISS ? eye.add(ctx.look().scale(p.range())) : hit.getLocation();
        }
        float power = ctx.power();
        int[] countdown = {Math.max(1, p.duration())};
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2f, 0.6f);
        Vec3[] spot = {at};
        PENDING.add(now -> {
            if (target != null && target.isAlive()) {
                spot[0] = target.position(); // it follows its target, like the dragon it's named for
            }
            Vec3 ground = spot[0];
            if (--countdown[0] > 0) {
                // Gathering: a dark storm and stray bolts in the clouds above.
                Vec3 sky = ground.add(0, 22, 0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, sky.x, sky.y, sky.z, 12, 4, 0.8, 4, 0.01);
                if (level.random.nextInt(3) == 0) {
                    Vec3 a = sky.add((level.random.nextDouble() - 0.5) * 8, 0, (level.random.nextDouble() - 0.5) * 8);
                    Vec3 b = sky.add((level.random.nextDouble() - 0.5) * 8, -1, (level.random.nextDouble() - 0.5) * 8);
                    Vfx.bolt(level, a, b, Vfx.LIGHTNING, 0.1f, 4, 1);
                }
                return false;
            }
            float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
            Vfx.bolt(level, ground.add(0, 45, 0), ground, 0x8FCBFF, 0.9f, 14, 6);
            for (int i = 0; i < 3; i++) {
                Vec3 side = ground.add((level.random.nextDouble() - 0.5) * radius, 0, (level.random.nextDouble() - 0.5) * radius);
                Vfx.bolt(level, ground.add(0, 40, 0), side, Vfx.LIGHTNING, 0.3f, 10, 3);
            }
            Vfx.discharge(level, ground.add(0, 0.5, 0), radius * 1.4f, 10, Vfx.LIGHTNING);
            Vfx.explosion(level, ground, Vfx.palette(Nature.LIGHTNING), radius);
            Vfx.shockwave(level, ground.add(0, 0.2, 0), Vfx.palette(Nature.LIGHTNING), radius * 1.6f);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 4f, 0.5f);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 3f, 0.6f);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(radius),
                    e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && e.position().distanceTo(ground) <= radius + e.getBbWidth())) {
                float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(ground) / (radius + 1));
                Vec3 push = victim.position().subtract(ground);
                JutsuEffects.hit(caster, victim, p.damage() * power * (0.4f + 0.6f * falloff), p.knockback(), p.posture() * power, 0, push);
                if (p.stun() > 0) {
                    CombatSystem.stun(victim, p.stun());
                }
            }
            Destruction.crater(level, ground, radius * 0.6f, power * 1.5f);
            Destruction.scorch(level, ground, radius);
            return true;
        });
    }

    // ---------------------------------------------------------------- wave

    static void wave(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        Nature nature = JutsuBehaviors.nature(ctx);
        Vec3 dir = ctx.flatLook();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        float halfWidth = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        Vec3 origin = caster.position().add(dir.scale(1.5));
        double speed = Math.max(0.2, p.speed());
        Set<LivingEntity> hit = new HashSet<>();
        double[] travelled = {0};
        Vfx.Palette palette = Vfx.palette(nature);
        PENDING.add(now -> {
            travelled[0] += speed;
            if (travelled[0] > p.range() || !caster.isAlive()) {
                return true;
            }
            Vec3 front = origin.add(dir.scale(travelled[0]));
            double height = 1.5 + halfWidth * 0.5;
            // The wall itself: a sheet of the nature's particles, thickest at the base.
            for (double s = -halfWidth; s <= halfWidth; s += 0.8) {
                Vec3 base = front.add(side.scale(s));
                Vfx.burst(level, Vfx.glow(palette.core(), palette.mid(), 0.7f, 0.3f, 8), base.add(0, height * level.random.nextDouble(), 0), 1, 0.3, 0.05);
                Vfx.one(level, Vfx.smoke(palette.edge(), 1.0f, 18), base.add(0, height, 0), dir.scale(0.1).add(0, 0.06, 0));
                if (nature == Nature.WATER) {
                    level.sendParticles(ParticleTypes.SPLASH, base.x, base.y + height * 0.5, base.z, 6, 0.3, height * 0.4, 0.3, 0.1);
                } else if (nature == Nature.FIRE) {
                    level.sendParticles(ParticleTypes.FLAME, base.x, base.y + height * 0.5, base.z, 4, 0.3, height * 0.4, 0.3, 0.03);
                } else {
                    level.sendParticles(ParticleTypes.CLOUD, base.x, base.y + height * 0.5, base.z, 3, 0.3, height * 0.4, 0.3, 0.08);
                }
            }
            AABB band = new AABB(front.add(side.scale(-halfWidth)).add(0, -1, 0), front.add(side.scale(halfWidth)).add(0, height, 0)).inflate(0.8);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, band, e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator())) {
                if (hit.add(victim)) {
                    JutsuEffects.hit(caster, victim, p.damage() * power, 0, p.posture() * power, p.fireSeconds(), dir);
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                }
                // Carried along by the wave.
                victim.setDeltaMovement(dir.scale(p.knockback() * 0.6).add(0, 0.12, 0));
                victim.hurtMarked = true;
            }
            if (now % 3 == 0) {
                switch (nature) {
                    case FIRE -> Destruction.scorch(level, front, halfWidth);
                    case WATER -> Destruction.douse(level, front, halfWidth);
                    default -> Destruction.shatterFragile(level, net.minecraft.core.BlockPos.containing(front.add(0, 1, 0)), (int) Math.ceil(halfWidth));
                }
                level.playSound(null, front.x, front.y, front.z, nature == Nature.WATER ? SoundEvents.GENERIC_SPLASH
                        : nature == Nature.FIRE ? SoundEvents.FIRECHARGE_USE : SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1f, 0.7f);
            }
            return false;
        });
        JutsuBehaviors.sound(caster, nature == Nature.WATER ? SoundEvents.GENERIC_SPLASH : SoundEvents.BLAZE_SHOOT, 1.5f, 0.5f);
    }

    // ---------------------------------------------------------------- nova (Chidori Current)

    static void nova(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        Nature nature = JutsuBehaviors.nature(ctx);
        float power = ctx.power();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        Vec3 center = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
        Vfx.Palette palette = Vfx.palette(nature);
        Vfx.flash(level, center, palette, radius * 0.8f);
        Vfx.shockwave(level, caster.position().add(0, 0.2, 0), palette, radius);
        if (nature == Nature.LIGHTNING) {
            Vfx.arcs(level, caster, 8, Vfx.LIGHTNING);
            Vfx.discharge(level, center, radius, 10, Vfx.LIGHTNING);
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && e.distanceTo(caster) <= radius)) {
            if (nature == Nature.LIGHTNING) {
                Vfx.bolt(level, center, victim.position().add(0, victim.getBbHeight() * 0.5, 0), Vfx.LIGHTNING, 0.08f, 6, 1);
            }
            JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, p.fireSeconds(), victim.position().subtract(caster.position()));
            if (p.stun() > 0) {
                CombatSystem.stun(victim, p.stun());
            }
            JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
        }
        JutsuBehaviors.sound(caster, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.8f);
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        List<Pending> copy = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Iterator<Pending> it = copy.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            boolean done;
            try {
                done = pending.tick(now);
            } catch (RuntimeException e) {
                Noirm.LOGGER.error("A jutsu effect failed", e);
                done = true;
            }
            if (!done) {
                PENDING.add(pending);
            }
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        PENDING.clear();
    }

    /** How many effects are still playing out (for tests). */
    public static int pending() {
        return PENDING.size();
    }
}

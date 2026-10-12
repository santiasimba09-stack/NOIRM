package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * More building blocks for nature jutsu, shared by the basic natures and their combinations.
 * Each reads its numbers from the jutsu's {@code params}; {@code block} gives it a look (oak logs
 * for Wood, packed ice for Ice, black sand for Magnet).
 * <ul>
 *   <li><b>bind</b>: catch the enemy you look at: damage, a {@code stun}, held in place.</li>
 *   <li><b>pillar</b>: spikes erupt under where you aim: everyone within {@code radius} is hurt
 *       and thrown up by {@code knockback}.</li>
 *   <li><b>cloud</b>: a lingering area ({@code radius}, {@code duration}) that hurts every 10
 *       ticks and applies {@code effect}.</li>
 *   <li><b>rain</b>: {@code count} strikes fall at random over an area during {@code duration}.</li>
 *   <li><b>beam</b>: {@code count} instant lines up to {@code range}, piercing; a {@code radius}
 *       blows up where they end.</li>
 *   <li><b>buff</b>: {@code effect} on yourself for {@code duration}, with an aura.</li>
 * </ul>
 */
public final class ElementalBehaviors {
    private ElementalBehaviors() {}

    static void bind(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        ParticleOptions look = look(ctx);
        com.noirm.vfx.Vfx.gather(ctx.level(), target.position().add(0, target.getBbHeight() / 2, 0), com.noirm.vfx.Vfx.palette(JutsuBehaviors.nature(ctx)), 2f, 24);
        JutsuEffects.hit(caster, target, p.damage() * ctx.power(), 0, p.posture() * ctx.power(), p.fireSeconds(), Vec3.ZERO);
        int hold = Math.round(p.stun() * Math.min(1.5f, ctx.power()));
        CombatSystem.stun(target, hold);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, hold, 9, false, false));
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        JutsuEffects.applyEffects(target, p.effect(), p.effectTicks());
        // It grows up around them, then holds them for as long as they're stuck.
        for (double y = 0; y < target.getBbHeight() + 0.4; y += 0.25) {
            JutsuEffects.particlesAt(ctx.level(), target.position().add(0, y, 0), look, 10, target.getBbWidth() * 0.6, 0.02);
        }
        JutsuAreas.add(new JutsuAreas.Area(ctx.level(), target.position(), target, caster, JutsuAreas.Mode.AURA,
                target.getBbWidth(), ctx.level().getGameTime() + hold, 0, 0, "", 0, 0, look));
        JutsuBehaviors.sound(target, JutsuBehaviors.castSound(JutsuBehaviors.nature(ctx)), 1f, 0.7f);
    }

    static void pillar(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        Vec3 at = ground(ctx.level(), aim(caster, p.range()));
        ParticleOptions look = look(ctx);
        float radius = Math.max(0.8f, p.radius());
        for (int i = 0; i < 6 + radius * 4; i++) {
            double angle = i * 2.4;
            double dist = radius * Math.sqrt((i + 0.5) / (6 + radius * 4));
            Vec3 spike = at.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            for (double y = 0; y < 2.2; y += 0.3) {
                JutsuEffects.particlesAt(ctx.level(), spike.add(0, y, 0), look, 3, 0.08, 0.05);
            }
        }
        com.noirm.vfx.Vfx.Palette palette = com.noirm.vfx.Vfx.palette(JutsuBehaviors.nature(ctx));
        com.noirm.vfx.Vfx.shockwave(ctx.level(), at, palette, radius * 1.4f);
        com.noirm.vfx.Vfx.radial(ctx.level(), com.noirm.vfx.Vfx.shard(palette.edge(), 0.25f, 30), at.add(0, 0.3, 0), (int) (8 + radius * 6), 0.5);
        com.noirm.vfx.Vfx.burst(ctx.level(), com.noirm.vfx.Vfx.smoke(palette.edge(), 0.6f, 40), at.add(0, 0.4, 0), (int) (4 + radius * 3), radius * 0.6, 0.02);
        if (JutsuBehaviors.nature(ctx) != com.noirm.clan.Nature.EXPLOSION && JutsuBehaviors.nature(ctx) != com.noirm.clan.Nature.DUST) {
            // Real spikes burst from the ground (and crumble back soon after).
            net.minecraft.world.level.block.state.BlockState spike = spikeBlock(p.block(), JutsuBehaviors.nature(ctx));
            com.noirm.world.Destruction.spikes(ctx.level(), at, radius, spike, 1 + Math.round(Math.min(3f, ctx.power() * 1.5f)));
        }
        if (JutsuBehaviors.nature(ctx) == com.noirm.clan.Nature.EXPLOSION || JutsuBehaviors.nature(ctx) == com.noirm.clan.Nature.DUST) {
            com.noirm.world.Destruction.natureImpact(ctx.level(), at, JutsuBehaviors.nature(ctx), radius, ctx.power());
            com.noirm.vfx.Vfx.explosion(ctx.level(), at.add(0, 0.5, 0), palette, radius);
            ctx.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, radius * 0.4, 0.3, radius * 0.4, 0);
        }
        AABB box = new AABB(at, at).inflate(radius, 2.5, radius);
        for (LivingEntity victim : ctx.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && e.isAlive() && !e.isSpectator() && horizontal(e.position(), at) <= radius + e.getBbWidth() / 2)) {
            JutsuEffects.hit(caster, victim, p.damage() * ctx.power(), 0, p.posture() * ctx.power(), p.fireSeconds(), Vec3.ZERO);
            Vec3 motion = victim.getDeltaMovement();
            victim.setDeltaMovement(motion.x, Math.max(motion.y, p.knockback()), motion.z);
            victim.hurtMarked = true;
            JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
            if (p.stun() > 0) {
                CombatSystem.stun(victim, p.stun());
            }
        }
        JutsuBehaviors.sound(caster, JutsuBehaviors.castSound(JutsuBehaviors.nature(ctx)), 1.2f, 0.6f);
    }

    static void cloud(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        Vec3 at = p.range() <= 0 ? caster.position() : ground(ctx.level(), aim(caster, p.range()));
        if (p.block().contains("mud")) {
            com.noirm.world.Destruction.swamp(ctx.level(), at, p.radius());
        }
        JutsuAreas.add(new JutsuAreas.Area(ctx.level(), at, null, caster, JutsuAreas.Mode.CLOUD, p.radius(),
                ctx.level().getGameTime() + p.duration(), 10, p.damage() * ctx.power(), p.effect(), Math.max(20, p.effectTicks()), 0, look(ctx)));
        JutsuBehaviors.sound(caster, SoundEvents.FIRE_EXTINGUISH, 1f, 0.6f);
    }

    static void rain(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        Vec3 at = ground(ctx.level(), aim(caster, p.range()));
        int interval = Math.max(1, p.duration() / Math.max(1, p.count()));
        JutsuAreas.add(new JutsuAreas.Area(ctx.level(), at, null, caster, JutsuAreas.Mode.RAIN, p.radius(),
                ctx.level().getGameTime() + p.duration(), interval, p.damage() * ctx.power(), p.effect(), p.effectTicks(), p.stun(), look(ctx)));
        JutsuBehaviors.sound(caster, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
    }

    static void beam(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        ParticleOptions look = look(ctx);
        Set<LivingEntity> hit = new HashSet<>();
        int count = Math.max(1, p.count());
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        for (int i = 0; i < count; i++) {
            float offset = count == 1 ? 0f : p.spread() * (i / (float) (count - 1) - 0.5f);
            Vec3 dir = ctx.look().yRot(-offset * net.minecraft.util.Mth.DEG_TO_RAD);
            Vec3 start = caster.getEyePosition().add(0, -0.2, 0);
            Vec3 end = start.add(dir.scale(p.range()));
            BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
            }
            double length = start.distanceTo(end);
            com.noirm.vfx.Vfx.line(level, start.add(dir.scale(0.6)), end, com.noirm.vfx.Vfx.palette(JutsuBehaviors.nature(ctx)), 0.22f);
            com.noirm.vfx.Vfx.hit(level, end, com.noirm.vfx.Vfx.palette(JutsuBehaviors.nature(ctx)), false);
            for (double d = 0.5; d <= length; d += 0.4) {
                Vec3 point = start.add(dir.scale(d));
                JutsuEffects.particlesAt(level, point, look, 1, 0.05, 0);
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(0.5),
                        e -> e != caster && e.isAlive() && !e.isSpectator() && !hit.contains(e))) {
                    hit.add(victim);
                    JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, p.fireSeconds(), dir);
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                    if (p.stun() > 0) {
                        CombatSystem.stun(victim, p.stun());
                    }
                }
            }
            if (p.radius() > 0) {
                // Where the beam ends, everything inside the radius is torn apart (Dust).
                Vec3 center = end;
                level.sendParticles(look, center.x, center.y, center.z, 60, p.radius() * 0.5, p.radius() * 0.5, p.radius() * 0.5, 0.02);
                com.noirm.vfx.Vfx.explosion(level, center, com.noirm.vfx.Vfx.palette(JutsuBehaviors.nature(ctx)), p.radius());
                com.noirm.world.Destruction.natureImpact(level, center, JutsuBehaviors.nature(ctx), p.radius(), power);
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(p.radius()),
                        e -> e != caster && e.isAlive() && !hit.contains(e) && e.position().distanceTo(center) <= p.radius() + 1)) {
                    hit.add(victim);
                    JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, p.fireSeconds(), dir);
                }
            }
        }
        JutsuBehaviors.sound(caster, JutsuBehaviors.castSound(JutsuBehaviors.nature(ctx)), 1f, 1.4f);
    }

    static void buff(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        JutsuEffects.applyEffects(caster, p.effect(), p.duration());
        JutsuAreas.add(new JutsuAreas.Area(ctx.level(), caster.position(), caster, caster, JutsuAreas.Mode.AURA, 0.6f,
                ctx.level().getGameTime() + p.duration(), 0, 0, "", 0, 0, look(ctx)));
        JutsuBehaviors.sound(caster, JutsuBehaviors.castSound(JutsuBehaviors.nature(ctx)), 0.8f, 1.6f);
    }

    // ---------------------------------------------------------------- helpers

    /** The block spikes are made of: the jutsu's {@code block}, else one that fits its nature. */
    private static net.minecraft.world.level.block.state.BlockState spikeBlock(String id, com.noirm.clan.Nature nature) {
        net.minecraft.resources.ResourceLocation key = id == null || id.isBlank() ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        net.minecraft.world.level.block.Block block = key == null ? null : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(key);
        if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
            return block.defaultBlockState();
        }
        return (nature == com.noirm.clan.Nature.ICE ? net.minecraft.world.level.block.Blocks.PACKED_ICE
                : nature == com.noirm.clan.Nature.WOOD ? net.minecraft.world.level.block.Blocks.OAK_LOG
                : net.minecraft.world.level.block.Blocks.PACKED_MUD).defaultBlockState();
    }

    /** The jutsu's look: its {@code block}, else its nature's particle. */
    private static ParticleOptions look(JutsuContext ctx) {
        return JutsuEffects.blockParticle(ctx.params().block(), JutsuProjectile.particle(JutsuBehaviors.nature(ctx)));
    }

    /** Where the caster aims: the enemy they look at, else the block, else {@code range} ahead. */
    static Vec3 aim(LivingEntity caster, double range) {
        LivingEntity target = JutsuBehaviors.lookTarget(caster, range, 12);
        if (target != null) {
            return target.position();
        }
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getViewVector(1f).scale(range));
        BlockHitResult hit = caster.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** Drops a point onto the ground below it (up to 6 blocks). */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockHitResult hit = level.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}

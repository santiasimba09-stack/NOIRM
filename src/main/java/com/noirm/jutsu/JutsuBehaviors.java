package com.noirm.jutsu;

import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.registry.NoirmBlocks;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The building blocks jutsu are made of. A jutsu JSON names one with {@code "behavior"} and tunes
 * it with {@code "params"}; new jutsu are mostly new JSON.
 */
public final class JutsuBehaviors {
    private static final Map<String, JutsuBehavior> BEHAVIORS = Map.ofEntries(
            Map.entry("projectile", JutsuBehaviors::projectile),
            Map.entry("burst", JutsuBehaviors::burst),
            Map.entry("wall", JutsuBehaviors::wall),
            Map.entry("dash_strike", JutsuBehaviors::dashStrike),
            Map.entry("substitution", JutsuBehaviors::substitution),
            Map.entry("flicker", JutsuBehaviors::flicker),
            Map.entry("genjutsu", JutsuBehaviors::genjutsu),
            Map.entry("stream", new Stream()),
            Map.entry("rotation", HyugaTechniques::rotation),
            Map.entry("palms", HyugaTechniques::palms),
            Map.entry("bind", ElementalBehaviors::bind),
            Map.entry("pillar", ElementalBehaviors::pillar),
            Map.entry("cloud", ElementalBehaviors::cloud),
            Map.entry("rain", ElementalBehaviors::rain),
            Map.entry("beam", ElementalBehaviors::beam),
            Map.entry("buff", ElementalBehaviors::buff),
            Map.entry("mode", ModeSystem::toggle),
            Map.entry("dome", CanonTechniques::dome),
            Map.entry("heal", CanonTechniques.HEAL),
            Map.entry("rasengan", CanonTechniques::rasengan),
            Map.entry("shadow_bind", CanonTechniques.SHADOW_BIND),
            Map.entry("shadow_clone", CanonTechniques::shadowClone),
            Map.entry("strike", GreatTechniques::strike),
            Map.entry("wave", GreatTechniques::wave),
            Map.entry("nova", GreatTechniques::nova),
            Map.entry("ash_cloud", FireLightningTechniques::ashCloud),
            Map.entry("chain", FireLightningTechniques::chain),
            // The Mangekyō Sharingan (dojutsu/MangekyoAbilities, dojutsu/Susanoo)
            Map.entry("amaterasu", com.noirm.dojutsu.MangekyoAbilities::amaterasu),
            Map.entry("tsukuyomi", com.noirm.dojutsu.MangekyoAbilities::tsukuyomi),
            Map.entry("kamui", com.noirm.dojutsu.MangekyoAbilities::kamui),
            Map.entry("kotoamatsukami", com.noirm.dojutsu.MangekyoAbilities::kotoamatsukami),
            Map.entry("izanagi", com.noirm.dojutsu.MangekyoAbilities::izanagi),
            Map.entry("susanoo", com.noirm.dojutsu.Susanoo::cast),
            Map.entry("lotus", TaijutsuTechniques::lotus),
            Map.entry("flurry", TaijutsuTechniques::flurry));

    private JutsuBehaviors() {}

    public static JutsuBehavior get(String name) {
        return BEHAVIORS.get(name);
    }

    public static boolean exists(String name) {
        return BEHAVIORS.containsKey(name);
    }

    // ------------------------------------------------------------ projectile: fireball, water bullet…

    /** {@code radius} = blast radius (0 = single target), {@code speed} = blocks per tick. */
    private static void projectile(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        JutsuProjectile projectile = NoirmEntities.JUTSU_PROJECTILE.get().create(ctx.level());
        if (projectile == null) {
            return;
        }
        float charge = ctx.charge();
        // More chakra and mastery: a bigger ball and a bigger blast (√power, so it grows without exploding).
        float grow = (float) Math.sqrt(Math.max(0.3f, ctx.power()));
        float blast = p.radius() * (0.6f + 0.4f * charge) * grow;
        float size = (0.35f + 0.3f * blast) * (p.radius() >= 3f ? 1.4f : 1f);
        float damage = (p.damage() + p.chargeDamage() * charge) * ctx.power();
        int count = Math.max(1, p.count());
        for (int i = 0; i < count; i++) {
            JutsuProjectile shot = i == 0 ? projectile : NoirmEntities.JUTSU_PROJECTILE.get().create(ctx.level());
            if (shot == null) {
                continue;
            }
            // Several shots fan out across `spread` degrees.
            float offset = count == 1 ? 0f : p.spread() * (i / (float) (count - 1) - 0.5f);
            Vec3 look = ctx.look().yRot(-offset * Mth.DEG_TO_RAD);
            if (count > 2 && "bullet".equals(p.look())) {
                // Phoenix Flower: a scatter of bullets, not a flat fan.
                look = look.add(0, (caster.getRandom().nextFloat() - 0.5f) * 0.3f, 0).normalize();
            }
            Vec3 start = caster.getEyePosition().add(look.scale(0.9)).add(0, -0.25, 0);
            shot.setOwner(caster);
            shot.moveTo(start.x, start.y, start.z, caster.getYRot(), caster.getXRot());
            shot.configure(nature(ctx), size, damage, p.knockback(), p.posture() * ctx.power(), blast, p.fireSeconds());
            shot.setOnHitEffect(p.effect(), p.effectTicks());
            JutsuProjectile.Look style = JutsuProjectile.Look.of(p.look());
            shot.setLook(style);
            if (style == JutsuProjectile.Look.OIL) {
                shot.setLingering(Math.max(1.5f, blast * 0.8f), p.duration(), p.damage() * 0.4f * ctx.power());
            }
            if (p.homing() > 0) {
                shot.setHoming(p.homing(), lookTarget(caster, 40, 25));
            }
            if (ctx.def().requires().filter(r -> "cloak".equals(r.dojutsu())).isPresent()) {
                shot.setBeastChakra(); // the Tailed Beast Ball
            }
            shot.setDeltaMovement(look.scale(p.speed()));
            ctx.level().addFreshEntity(shot);
        }
        sound(caster, castSound(nature(ctx)), 1f, 1f);
    }

    // ------------------------------------------------------------ burst: a cone of force (Air Palm)

    /** Hits everything in a 70° cone up to {@code range}, and turns enemy projectiles around. */
    private static void burst(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        Vec3 look = ctx.flatLook();
        for (LivingEntity target : inCone(caster, p.range(), 35)) {
            Vec3 direction = target.position().subtract(caster.position());
            JutsuEffects.hit(caster, target, p.damage() * ctx.power(), p.knockback(), p.posture() * ctx.power(), p.fireSeconds(), direction);
        }
        AABB area = caster.getBoundingBox().inflate(p.range());
        for (Projectile projectile : ctx.level().getEntitiesOfClass(Projectile.class, area,
                e -> e.getOwner() != caster && inCone(caster, e.position(), p.range(), 45))) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.2));
            projectile.setOwner(caster);
        }
        ParticleOptions particle = JutsuProjectile.particle(nature(ctx));
        Vec3 eye = caster.getEyePosition().add(0, -0.3, 0);
        com.noirm.vfx.Vfx.Palette palette = com.noirm.vfx.Vfx.palette(nature(ctx));
        com.noirm.vfx.Vfx.cone(ctx.level(), eye, look, p.range(), palette, 2.2f);
        com.noirm.vfx.Vfx.quake(ctx.level(), caster.position(), 0.35f, p.range() + 4);
        for (double d = 1; d <= p.range(); d += 0.75) {
            Vec3 at = eye.add(look.scale(d));
            JutsuEffects.particlesAt(ctx.level(), at, particle, 3, d * 0.15, 0.05);
            // The blast tears up what's in its way: leaves, glass, plants (and fire scorches).
            if (nature(ctx) == Nature.FIRE || nature(ctx) == Nature.SCORCH) {
                com.noirm.world.Destruction.scorch(ctx.level(), at, (float) (0.8 + d * 0.15));
            } else {
                com.noirm.world.Destruction.shatterFragile(ctx.level(), BlockPos.containing(at), 1);
            }
        }
        sound(caster, SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.5f);
    }

    // ------------------------------------------------------------ wall: Earth Wall

    /** Raises a wall ({@code radius}·2+1 wide, 3 tall) two blocks ahead, for {@code duration} ticks. */
    private static void wall(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        Direction facing = Direction.fromYRot(caster.getYRot());
        Direction side = facing.getClockWise();
        BlockPos center = caster.blockPosition().relative(facing, 2);
        int half = Math.max(0, Math.round(p.radius()));
        BlockState wall = NoirmBlocks.EARTH_WALL.get().defaultBlockState();
        int placed = 0;
        for (int w = -half; w <= half; w++) {
            for (int h = 0; h < Math.max(3, p.count()); h++) {
                BlockPos pos = center.relative(side, w).above(h);
                if (level.getBlockState(pos).canBeReplaced() && level.getEntities(null, new AABB(pos)).isEmpty()) {
                    level.setBlock(pos, wall, 3);
                    level.scheduleTick(pos, wall.getBlock(), p.duration() + level.random.nextInt(10));
                    placed++;
                }
            }
        }
        JutsuEffects.particlesAt(level, Vec3.atCenterOf(center).add(0, 1, 0), JutsuProjectile.particle(Nature.EARTH), 30 + placed * 4, 1.5, 0.1);
        com.noirm.vfx.Vfx.Palette earth = com.noirm.vfx.Vfx.palette(Nature.EARTH);
        com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.smoke(earth.edge(), 1.0f, 40), Vec3.atCenterOf(center).add(0, 0.5, 0), 10, 1.2, 0.04);
        com.noirm.vfx.Vfx.radial(level, com.noirm.vfx.Vfx.shard(earth.mid(), 0.25f, 30), Vec3.atCenterOf(center).add(0, 1, 0), 14, 0.5);
        com.noirm.vfx.Vfx.quake(level, caster.position(), 0.4f, 12);
        sound(caster, SoundEvents.GRAVEL_BREAK, 1.2f, 0.5f);
    }

    // ------------------------------------------------------------ dash_strike: Chidori

    /**
     * Charges forward up to {@code range} (scaled by charge) and strikes the first enemy in the
     * way: damage, {@code stun} ticks, posture damage. Stops at walls.
     */
    private static void dashStrike(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        Vec3 direction = ctx.flatLook();
        float reach = p.range() * (0.4f + 0.6f * ctx.charge());
        Vec3 start = caster.position();
        Vec3 end = start;
        LivingEntity victim = null;
        for (double d = 0.5; d <= reach; d += 0.5) {
            Vec3 next = start.add(direction.scale(d));
            AABB box = caster.getBoundingBox().move(next.subtract(start));
            if (!level.noCollision(caster, box)) {
                break;
            }
            end = next;
            List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.4), e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator());
            if (!hits.isEmpty()) {
                victim = hits.get(0);
                break;
            }
        }
        for (double d = 0; d <= start.distanceTo(end); d += 0.8) {
            JutsuEffects.particlesAt(level, start.add(direction.scale(d)).add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK, 2, 0.2, 0.05);
        }
        caster.teleportTo(end.x, end.y, end.z);
        com.noirm.vfx.Vfx.line(level, start.add(0, 1, 0), end.add(0, 1, 0), com.noirm.vfx.Vfx.palette(Nature.LIGHTNING), 0.18f);
        com.noirm.vfx.LightningVfx.forked(level, start.add(0, 1, 0), end.add(0, 1, 0), 0.1f, 8);
        // The hand drags lightning along the ground behind the charge: a forking furrow.
        com.noirm.vfx.LightningVfx.bolt(level, start.add(0, 0.1, 0), end.add(0, 0.1, 0), com.noirm.vfx.LightningVfx.BLUE_WHITE, 0.07f, 10, 4,
                com.noirm.vfx.LightningVfx.GROUND | com.noirm.vfx.LightningVfx.FORKY);
        for (double d = 0; d <= start.distanceTo(end); d += 1) {
            com.noirm.world.Destruction.shatterFragile(level, BlockPos.containing(start.add(direction.scale(d))), 1);
        }
        // Chidori's weakness (canon): tunnel vision during the charge. The Sharingan sees past it.
        if (com.noirm.dojutsu.Sharingan.activeStage(caster) == 0) {
            caster.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, false, false));
            com.noirm.combat.CombatSystem.stun(caster, 6);
        }
        if (victim != null) {
            JutsuEffects.hit(caster, victim, (p.damage() + p.chargeDamage() * ctx.charge()) * ctx.power(), p.knockback(),
                    p.posture() * ctx.power(), p.fireSeconds(), direction);
            CombatSystem.stun(victim, p.stun());
            JutsuEffects.particles(victim, ParticleTypes.ELECTRIC_SPARK, 20, 0.5, 0.3);
            Vec3 contact = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
            com.noirm.vfx.LightningVfx.contact(level, contact, 1.4f);
            com.noirm.vfx.Vfx.discharge(level, contact, 3.5f, 4, com.noirm.vfx.Vfx.LIGHTNING);
            com.noirm.vfx.LightningVfx.groundForks(level, victim.position(), 3.5f, 5);
        }
        sound(caster, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.6f);
    }

    // ------------------------------------------------------------ substitution / flicker

    private static void substitution(JutsuContext ctx) {
        Substitution.arm(ctx.caster(), ctx.params().duration());
        JutsuEffects.particles(ctx.caster(), ParticleTypes.POOF, 6, 0.3, 0.01);
        com.noirm.vfx.Vfx.burst(ctx.level(), com.noirm.vfx.Vfx.smoke(0xE8E8E8, 0.7f, 22), ctx.caster().position().add(0, 1, 0), 6, 0.35, 0.03);
        sound(ctx.caster(), SoundEvents.ARMOR_EQUIP_LEATHER, 1f, 1.6f);
    }

    /** Body Flicker: blink up to {@code range} blocks forward in a puff of smoke, stopping at walls. */
    private static void flicker(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        Vec3 direction = ctx.flatLook();
        Vec3 start = caster.position();
        Vec3 end = start;
        for (double d = 0.5; d <= ctx.params().range(); d += 0.5) {
            Vec3 next = start.add(direction.scale(d));
            if (!level.noCollision(caster, caster.getBoundingBox().move(next.subtract(start)))) {
                break;
            }
            end = next;
        }
        JutsuEffects.particlesAt(level, start.add(0, 1, 0), ParticleTypes.POOF, 15, 0.3, 0.02);
        com.noirm.vfx.Vfx.streak(level, start, end, com.noirm.vfx.Vfx.palette(nature(ctx)));
        caster.teleportTo(end.x, end.y, end.z);
        JutsuEffects.particlesAt(level, end.add(0, 1, 0), ParticleTypes.POOF, 10, 0.3, 0.02);
        sound(caster, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.8f);
    }

    // ------------------------------------------------------------ genjutsu (Sharingan)

    /**
     * Catch an enemy's gaze (the one you look at, within {@code range}) in a genjutsu: stunned for
     * {@code stun} ticks and dazed for {@code duration}. Stronger minds may resist: the chance
     * falls as their perception rises above yours.
     */
    private static void genjutsu(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        LivingEntity target = lookTarget(caster, p.range(), 12);
        if (target == null) {
            message(caster, Component.translatable("message.noirm.genjutsu.no_target"));
            return;
        }
        float ratio = Perception.perception(target) / Perception.perception(caster);
        float chance = Mth.clamp(1.5f - ratio, 0.15f, 1f) * Math.min(1.5f, ctx.power());
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.85f, 0.05f, 0.05f), 1.5f);
        if (caster.getRandom().nextFloat() >= chance) {
            JutsuEffects.particles(target, ParticleTypes.SMOKE, 15, 0.4, 0.02);
            message(caster, Component.translatable("message.noirm.genjutsu.resisted", target.getDisplayName()));
            return;
        }
        CombatSystem.stun(target, p.stun());
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, p.duration(), 3, false, false));
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        if (target instanceof Player) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, p.duration(), 0, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, p.stun(), 0, false, false));
        }
        JutsuEffects.particles(target, red, 40, 0.5, 0.05);
        // The illusion takes hold: crimson and black spirals closing in on the mind.
        com.noirm.vfx.Vfx.Palette illusion = nature(ctx) == Nature.YIN ? com.noirm.vfx.Vfx.palette(Nature.YIN)
                : new com.noirm.vfx.Vfx.Palette(0xFFE0E0, 0xE00010, 0x080000, 0x200000);
        com.noirm.vfx.Vfx.spiral(ctx.level(), target.position(), illusion, target.getBbWidth() + 0.4f, target.getBbHeight() + 0.4f, 3f);
        com.noirm.vfx.Vfx.burst(ctx.level(), com.noirm.vfx.Vfx.ring(illusion.mid(), 2.2f, 14), target.position().add(0, target.getBbHeight() * 0.8, 0), 1, 0, 0);
        sound(target, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.8f);
        message(caster, Component.translatable("message.noirm.genjutsu.caught", target.getDisplayName()));
    }

    // ------------------------------------------------------------ stream (channel): Fire Stream

    /** Held: a 60° cone of {@code range}, hitting every 4 ticks for {@code damage}. */
    private static final class Stream implements JutsuBehavior {
        @Override
        public void cast(JutsuContext ctx) {
            sound(ctx.caster(), castSound(nature(ctx)), 1f, 0.8f);
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            JutsuDefinition.Params p = ctx.params();
            LivingEntity caster = ctx.caster();
            Vec3 look = ctx.look();
            Vec3 eye = caster.getEyePosition().add(0, -0.25, 0);
            ParticleOptions particle = JutsuProjectile.particle(nature(ctx));
            if (nature(ctx) == Nature.FIRE) {
                com.noirm.vfx.FireVfx.stream(ctx.level(), eye, look, p.range());
            } else {
                com.noirm.vfx.Vfx.cone(ctx.level(), eye, look, p.range(), com.noirm.vfx.Vfx.palette(nature(ctx)), 0.7f);
                for (double d = 0.8; d <= p.range(); d += 1.2) {
                    JutsuEffects.particlesAt(ctx.level(), eye.add(look.scale(d)), particle, 2, d * 0.12, 0.02);
                }
            }
            if (tick % 10 == 0 && (nature(ctx) == Nature.FIRE || nature(ctx) == Nature.LAVA)) {
                com.noirm.world.Destruction.scorch(ctx.level(), eye.add(look.scale(p.range() * 0.8)), 1.5f);
            }
            if (tick % 4 == 0) {
                for (LivingEntity target : inCone(caster, p.range(), 30)) {
                    JutsuEffects.hit(caster, target, p.damage() * ctx.power(), p.knockback(), p.posture() * ctx.power(),
                            p.fireSeconds(), target.position().subtract(caster.position()));
                }
            }
            if (tick % 8 == 1) {
                sound(caster, nature(ctx) == Nature.FIRE ? SoundEvents.FIRECHARGE_USE : SoundEvents.GENERIC_SPLASH, 0.5f, 1f);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    static Nature nature(JutsuContext ctx) {
        return ctx.def().nature().orElse(null);
    }

    static SoundEvent castSound(Nature nature) {
        if (nature == null) {
            return SoundEvents.SNOWBALL_THROW;
        }
        return switch (nature) {
            case FIRE, SCORCH -> SoundEvents.BLAZE_SHOOT;
            case WATER, BOIL -> SoundEvents.GENERIC_SPLASH;
            case WIND -> SoundEvents.ENDER_DRAGON_FLAP;
            case EARTH, MAGNET -> SoundEvents.GRAVEL_BREAK;
            case LIGHTNING, STORM -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case WOOD -> SoundEvents.WOOD_BREAK;
            case ICE -> SoundEvents.GLASS_BREAK;
            case LAVA -> SoundEvents.LAVA_POP;
            case EXPLOSION -> SoundEvents.GENERIC_EXPLODE;
            case DUST -> SoundEvents.BEACON_POWER_SELECT;
            case YIN -> SoundEvents.ILLUSIONER_CAST_SPELL;
            case YANG, YIN_YANG -> SoundEvents.AMETHYST_BLOCK_CHIME;
        };
    }

    /** Living things within {@code range} and {@code halfAngle}° of where the caster looks (horizontally). */
    static List<LivingEntity> inCone(LivingEntity caster, double range, double halfAngle) {
        AABB area = caster.getBoundingBox().inflate(range);
        return caster.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && inCone(caster, e.position().add(0, e.getBbHeight() / 2, 0), range, halfAngle));
    }

    private static boolean inCone(LivingEntity caster, Vec3 point, double range, double halfAngle) {
        Vec3 offset = point.subtract(caster.getEyePosition());
        double distance = offset.length();
        if (distance > range + 0.5) {
            return false;
        }
        if (distance < 1) {
            return true;
        }
        Vec3 look = caster.getViewVector(1f);
        return look.dot(offset.normalize()) >= Math.cos(Math.toRadians(halfAngle));
    }

    /** The living thing closest to the caster's line of sight, within {@code maxAngle}°. */
    static LivingEntity lookTarget(LivingEntity caster, double range, double maxAngle) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && caster.hasLineOfSight(e))
                .stream()
                .filter(e -> angle(eye, look, e) <= maxAngle + apparentRadius(eye, e) && e.distanceTo(caster) <= range)
                .min(Comparator.comparingDouble(e -> angle(eye, look, e)))
                .orElse(null);
    }

    private static double angle(Vec3 eye, Vec3 look, LivingEntity entity) {
        Vec3 to = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(eye).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1, 1)));
    }

    /** How big the entity looks from {@code eye}, in degrees (half its height): close targets are easy to look at. */
    private static double apparentRadius(Vec3 eye, LivingEntity entity) {
        double distance = Math.max(0.5, eye.distanceTo(entity.position().add(0, entity.getBbHeight() * 0.5, 0)));
        return Math.toDegrees(Math.atan2(entity.getBbHeight() * 0.5, distance));
    }

    static void sound(LivingEntity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void message(LivingEntity to, Component text) {
        if (to instanceof Player player) {
            player.displayClientMessage(text, true);
        }
    }
}

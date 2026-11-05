package com.noirm.jutsu;

import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.registry.NoirmBlocks;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import com.noirm.vfx.WindEarthVfx;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chakra wave, Part 1: new Wind and Earth techniques (docs/CHAKRA_WAVE.md), bringing both up to
 * Fire's ten. Every number comes from the jutsu's {@code params}.
 * <ul>
 *   <li><b>wind_crescent</b>: {@code count} crescent blades of air fly out across {@code spread}
 *       degrees (360 = a ring all around the caster) at {@code speed}, {@code radius} wide, up to
 *       {@code range}: each enemy they pass through is cut once ({@code damage}, {@code effect}).</li>
 *   <li><b>tornado</b> (charged): a tornado rises where you aim and lives {@code duration} ticks,
 *       drifting at {@code speed} after the enemy you looked at. It drags enemies within twice its
 *       {@code radius} in and up, hurts them every half second, and flings them out at the end.</li>
 *   <li><b>headhunter</b>: the caster dives into the ground and comes up behind the enemy you look
 *       at, whose legs are seized by the earth ({@code damage}, {@code stun}, {@code effect}).</li>
 *   <li><b>rock_hounds</b>: {@code count} hounds of rock burst from the ground and run at
 *       {@code speed} along it after the enemies in {@code range}; a bite hits for {@code damage},
 *       {@code stun} and {@code effect}. They crumble after {@code duration} ticks.</li>
 *   <li><b>rampart</b>: a curved rampart of earth ({@code radius}·2+1 columns, {@code count} high)
 *       heaves up {@code range} blocks ahead, layer by layer, and stands for {@code duration} ticks;
 *       enemies where it rises are thrown off ({@code damage}, {@code knockback}).</li>
 *   <li><b>weight_rock</b>: the caster turns light (faster, higher jumps, a slow fall) and the enemy
 *       they look at turns heavy for {@code duration}: {@code effect}, pinned to the ground, posture worn down.</li>
 *   <li><b>falling_mountain</b> (charged): a mass of rock tears itself out of the ground and gathers
 *       in the sky over where you aim for {@code duration} ticks, then falls: {@code damage} in
 *       {@code radius}, a crater and rubble.</li>
 * </ul>
 */
public final class BasicNatureTechniques {
    /** Ticks between a tornado's hits and a weight's posture drain. */
    static final int PULSE = 10;
    /** Ticks per layer of a rampart rising. */
    static final int RAMPART_LAYER = 3;
    /** Ticks the mountain hangs at full size before it falls, and takes to fall. */
    static final int MOUNTAIN_HANG = 4;
    static final int MOUNTAIN_FALL_SLOW = 5;
    static final int MOUNTAIN_FALL_FAST = 5;
    /** How high over the target the mountain gathers. */
    static final double MOUNTAIN_HEIGHT = 13;

    private BasicNatureTechniques() {}

    // ================================================================ Wind: crescent blades

    private static final class Blade {
        Vec3 pos;
        final Vec3 dir;
        double travelled;
        boolean done;

        Blade(Vec3 pos, Vec3 dir) {
            this.pos = pos;
            this.dir = dir;
        }
    }

    static void windCrescent(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        int count = Math.max(1, p.count());
        boolean ring = p.spread() >= 359;
        double width = Math.max(0.8, p.radius());
        Vec3 hands = caster.getEyePosition().add(0, -0.5, 0);
        List<Blade> blades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offset = ring ? i * 360.0 / count : count == 1 ? 0 : p.spread() * (i / (double) (count - 1) - 0.5);
            Vec3 dir = (ring ? ctx.flatLook() : ctx.look()).yRot((float) Math.toRadians(-offset)).normalize();
            blades.add(new Blade(hands.add(dir.scale(ring ? 0.8 : 1.0)), dir));
        }
        // The swing: a flash of pressure at the hands, a ring round the caster for the big ones.
        // (Kept small and, for the ring, low: in first person a big flash at the hands whites out the screen.)
        Vfx.burst(level, Vfx.glow(0xFFFFFF, WindEarthVfx.WIND_PALE, 0.6f, 0.15f, 4), hands.add(ctx.look().scale(0.6)), 1, 0, 0);
        if (ring) {
            WindEarthVfx.pressureRing(level, caster.position().add(0, 0.3, 0), new Vec3(0, 1, 0), 1.4, 0.45, 10);
            WindEarthVfx.dustSkirt(level, caster.position(), 2f, 14);
            Vfx.quake(level, caster.position(), 0.35f, 14);
        } else {
            WindEarthVfx.pressureRing(level, hands.add(ctx.look().scale(0.8)), ctx.look(), 0.5, 0.12, 6);
        }
        Set<LivingEntity> hit = new HashSet<>();
        double speed = Math.max(0.3, p.speed());
        GreatTechniques.later(now -> {
            boolean any = false;
            for (Blade blade : blades) {
                if (blade.done) {
                    continue;
                }
                any = true;
                blade.pos = blade.pos.add(blade.dir.scale(speed));
                blade.travelled += speed;
                WindEarthVfx.crescent(level, blade.pos, blade.dir, width, blade.dir.scale(speed * 0.5));
                BlockPos block = BlockPos.containing(blade.pos);
                Destruction.shatterFragile(level, block, 0);
                if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
                    WindEarthVfx.dustSkirt(level, blade.pos.subtract(blade.dir.scale(0.4)), 0.8f, 5);
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                            level.getBlockState(block)), blade.pos.x, blade.pos.y, blade.pos.z, 8, 0.2, 0.2, 0.2, 0.15);
                    blade.done = true;
                    continue;
                }
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(blade.pos, blade.pos).inflate(width * 0.6 + 1, 1.6, width * 0.6 + 1),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && !hit.contains(e))) {
                    Vec3 c = KekkeiTechniquesB.chest(victim);
                    double flat = Math.hypot(c.x - blade.pos.x, c.z - blade.pos.z);
                    if (flat <= width * 0.55 + victim.getBbWidth() * 0.5 && Math.abs(c.y - blade.pos.y) <= victim.getBbHeight() * 0.5 + 0.6) {
                        hit.add(victim);
                        JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, blade.dir);
                        JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                        // The cut: a bright slash across the body and a spray of grey.
                        Vfx.burst(level, Vfx.glow(0xFFFFFF, WindEarthVfx.WIND_PALE, 0.9f, 0.1f, 4), c, 1, 0, 0);
                        Vfx.radial(level, Vfx.spark(WindEarthVfx.WIND_PALE, 0.15f, 8), c, 8, 0.4);
                        Vfx.burst(level, WindEarthVfx.windSmoke(0.35f, 16), c, 3, 0.25, 0.04);
                        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.4f);
                    }
                }
                if (blade.travelled >= p.range()) {
                    WindEarthVfx.pressureRing(level, blade.pos, blade.dir, width * 0.4, 0.1, 6);
                    blade.done = true;
                }
            }
            return !any;
        });
        level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, ring ? 0.6f : 1.2f);
        level.playSound(null, hands.x, hands.y, hands.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, ring ? 1.2f : 0.6f, 1.6f);
    }

    // ================================================================ Wind: the tornado

    static void tornado(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        Vec3[] base = {ElementalBehaviors.ground(level, target != null ? target.position() : ElementalBehaviors.aim(caster, p.range()))};
        float radius = Mth.clamp(p.radius() * (float) Math.sqrt(Math.max(0.5f, power)), 1f, 4f);
        double height = 5 + radius * 1.5;
        int duration = Math.max(20, Math.round(p.duration() * (0.7f + 0.3f * ctx.charge())));
        Set<LivingEntity> caught = new HashSet<>();
        int[] age = {0};
        Vfx.quake(level, base[0], 0.5f, 20);
        level.playSound(null, base[0].x, base[0].y, base[0].z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 2f, 0.6f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (!caster.isAlive() || caster.level() != level) {
                return true;
            }
            if (target != null && target.isAlive() && p.speed() > 0) {
                Vec3 to = target.position().subtract(base[0]);
                double flat = Math.hypot(to.x, to.z);
                if (flat > 0.3) {
                    double step = Math.min(p.speed(), flat);
                    base[0] = ElementalBehaviors.ground(level, base[0].add(to.x / flat * step, 1, to.z / flat * step));
                }
            }
            Vec3 foot = base[0];
            // Grows to full height in the first second.
            double tall = height * Math.min(1, 0.3 + age[0] / 20.0);
            WindEarthVfx.tornado(level, foot, radius, tall, now);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(foot, foot).inflate(radius * 2.2, tall, radius * 2.2),
                    e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator())) {
                Vec3 to = foot.subtract(victim.position());
                double flat = Math.hypot(to.x, to.z);
                if (flat > radius * 2.2 || victim.getY() < foot.y - 1 || victim.getY() > foot.y + tall) {
                    continue;
                }
                caught.add(victim);
                Vec3 inward = flat < 1e-3 ? Vec3.ZERO : new Vec3(to.x / flat, 0, to.z / flat);
                Vec3 spin = new Vec3(-inward.z, 0, inward.x);
                // Held in an orbit a little inside the funnel's wall: drawn in when outside it, spun
                // round it, carried up to about half its height. Set, not added, so it never builds into a sling.
                double lift = victim.getY() - foot.y < tall * 0.5 ? 0.12 : -0.03;
                double inSpeed = Mth.clamp((flat - radius * 0.6) * 0.25 * Math.max(0.5, p.knockback()), -0.15, 0.4);
                victim.setDeltaMovement(inward.scale(inSpeed).add(spin.scale(0.25)).add(0, lift, 0));
                victim.fallDistance = 0;
                victim.hurtMarked = true;
            }
            if (age[0] % PULSE == 0) {
                for (LivingEntity victim : caught) {
                    if (victim.isAlive() && victim.distanceTo(caster) < p.range() * 3 && Math.hypot(victim.getX() - foot.x, victim.getZ() - foot.z) <= radius * 2.4) {
                        JutsuEffects.hit(caster, victim, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
                        JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                        Vfx.radial(level, Vfx.spark(WindEarthVfx.WIND_PALE, 0.14f, 8), KekkeiTechniquesB.chest(victim), 6, 0.4);
                    }
                }
                Destruction.shatterFragile(level, BlockPos.containing(foot.add(0, 1, 0)), (int) Math.ceil(radius));
                level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1f, 0.5f + level.random.nextFloat() * 0.2f);
            }
            if (age[0] < duration) {
                return false;
            }
            // It bursts apart: everyone in it is flung out, and a last ring of pressure.
            WindEarthVfx.pressureRing(level, foot.add(0, 1, 0), new Vec3(0, 1, 0), radius, 0.6, 12);
            WindEarthVfx.dustSkirt(level, foot, radius * 1.5f, 18);
            Vfx.quake(level, foot, 0.6f, 20);
            for (LivingEntity victim : caught) {
                if (victim.isAlive() && Math.hypot(victim.getX() - foot.x, victim.getZ() - foot.z) <= radius * 3) {
                    victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0); // the spin stops: only the burst throws them
                    JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback() * 0.5f, p.posture() * power, 0, victim.position().subtract(foot));
                    victim.fallDistance = 0;
                }
            }
            level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 1.5f);
            return true;
        });
    }

    // ================================================================ Earth: Headhunter

    static void headhunter(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        Vec3 start = caster.position();
        WindEarthVfx.groundBurst(level, start, 1.2f);
        // Come up behind them (or beside, if something solid is there).
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 end = null;
        for (Vec3 offset : new Vec3[]{facing.scale(-1.3), facing.yRot((float) (Math.PI / 2)).scale(1.3), facing.yRot((float) (-Math.PI / 2)).scale(1.3)}) {
            Vec3 at = target.position().add(offset);
            if (level.noCollision(caster, caster.getBoundingBox().move(at.subtract(caster.position())))) {
                end = at;
                break;
            }
        }
        if (end != null) {
            caster.teleportTo(end.x, end.y, end.z);
            caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, KekkeiTechniquesB.chest(target));
            WindEarthVfx.groundBurst(level, end, 1.2f);
        }
        // The ground seizes their legs: rock closing in from all sides, dust thrown up.
        Vec3 feet = target.position();
        WindEarthVfx.groundBurst(level, feet, 1.6f);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Vec3 from = feet.add(Math.cos(a) * 0.9, 0.1, Math.sin(a) * 0.9);
            WindEarthVfx.rockMass(level, from, new Vec3(-Math.cos(a), 0.4, -Math.sin(a)).scale(0.12), 0.7f);
        }
        JutsuEffects.hit(caster, target, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
        CombatSystem.stun(target, Math.round(p.stun() * Math.min(1.5f, power)));
        JutsuEffects.applyEffects(target, p.effect(), p.effectTicks());
        target.setDeltaMovement(0, -0.4, 0);
        target.hurtMarked = true;
        int[] age = {0};
        int hold = Math.max(10, p.stun());
        GreatTechniques.later(now -> {
            if (!target.isAlive() || ++age[0] > hold) {
                if (target.isAlive()) {
                    WindEarthVfx.groundBurst(level, target.position(), 0.8f); // the earth lets go
                }
                return true;
            }
            if (age[0] % 3 == 0) {
                WindEarthVfx.rockMass(level, target.position().add(0, 0.3, 0), Vec3.ZERO, 0.9f);
            }
            return false;
        });
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
        JutsuBehaviors.sound(caster, SoundEvents.GRAVEL_BREAK, 1f, 0.6f);
    }

    // ================================================================ Earth: rock hounds

    private static final class Hound {
        Vec3 pos;
        final LivingEntity target;
        Vec3 dir;
        boolean done;

        Hound(Vec3 pos, LivingEntity target, Vec3 dir) {
            this.pos = pos;
            this.target = target;
            this.dir = dir;
        }
    }

    static void rockHounds(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        int count = Math.max(1, p.count());
        List<LivingEntity> targets = KekkeiTechniquesB.enemiesInSight(caster, p.range());
        Vec3 look = ctx.flatLook();
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
        List<Hound> hounds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0 : i / (double) (count - 1) - 0.5;
            Vec3 at = ElementalBehaviors.ground(level, caster.position().add(look.scale(1.5)).add(side.scale(t * 2.4)).add(0, 0.5, 0));
            hounds.add(new Hound(at, targets.isEmpty() ? null : targets.get(i % targets.size()), look.yRot((float) (-t * 0.6))));
            WindEarthVfx.groundBurst(level, at, 1f);
        }
        double speed = Math.max(0.2, p.speed());
        int life = Math.max(20, p.duration());
        int[] age = {0};
        Vfx.quake(level, caster.position(), 0.3f, 12);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1f, 0.5f);
        GreatTechniques.later(now -> {
            age[0]++;
            boolean any = false;
            for (Hound hound : hounds) {
                if (hound.done) {
                    continue;
                }
                any = true;
                if (hound.target != null && hound.target.isAlive()) {
                    Vec3 to = hound.target.position().subtract(hound.pos);
                    Vec3 flat = new Vec3(to.x, 0, to.z);
                    if (flat.lengthSqr() > 1e-4) {
                        hound.dir = hound.dir.add(flat.normalize().scale(0.35)).normalize();
                    }
                }
                Vec3 next = hound.pos.add(hound.dir.scale(speed));
                BlockPos inside = BlockPos.containing(next.add(0, 0.2, 0));
                if (!level.getBlockState(inside).getCollisionShape(level, inside).isEmpty()) {
                    next = next.add(0, 1, 0); // up a step
                    BlockPos higher = BlockPos.containing(next.add(0, 0.2, 0));
                    if (!level.getBlockState(higher).getCollisionShape(level, higher).isEmpty()) {
                        WindEarthVfx.groundBurst(level, hound.pos, 1f); // it runs into a wall and crumbles
                        hound.done = true;
                        continue;
                    }
                }
                hound.pos = ElementalBehaviors.ground(level, next.add(0, 0.5, 0));
                // The hound: its back humping through the ground, its head breaching every few strides.
                boolean breach = (age[0] + hounds.indexOf(hound) * 2) % 6 < 3;
                Vec3 head = hound.pos.add(hound.dir.scale(0.4)).add(0, breach ? 0.9 : 0.35, 0);
                WindEarthVfx.rockMass(level, head, hound.dir.scale(speed * 0.5), breach ? 0.9f : 0.6f);
                WindEarthVfx.rockMass(level, hound.pos.add(0, 0.3, 0), hound.dir.scale(speed * 0.3), 0.7f);
                if (age[0] % 2 == 0) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                            WindEarthVfx.groundBlock(level, hound.pos)), hound.pos.x, hound.pos.y + 0.2, hound.pos.z, 5, 0.3, 0.1, 0.3, 0.15);
                }
                if (age[0] % 5 == 0) {
                    level.playSound(null, hound.pos.x, hound.pos.y, hound.pos.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.6f, 0.6f);
                }
                for (LivingEntity victim : KekkeiTechniquesB.near(caster, level, hound.pos.add(0, 0.8, 0), 0.8)) {
                    bite(caster, level, victim, hound, p, power);
                    hound.done = true;
                    break;
                }
                if (age[0] >= life && !hound.done) {
                    WindEarthVfx.groundBurst(level, hound.pos, 0.8f);
                    hound.done = true;
                }
            }
            return !any;
        });
    }

    private static void bite(LivingEntity caster, ServerLevel level, LivingEntity victim, Hound hound, JutsuDefinition.Params p, float power) {
        Vec3 c = KekkeiTechniquesB.chest(victim);
        Vec3 side = hound.dir.cross(new Vec3(0, 1, 0)).normalize();
        // The jaws: two masses of rock snapping shut from either side.
        for (int s = -1; s <= 1; s += 2) {
            WindEarthVfx.rockMass(level, c.add(side.scale(s * 0.8)), side.scale(-s * 0.25), 1f);
        }
        WindEarthVfx.groundBurst(level, victim.position(), 1.4f);
        JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, hound.dir);
        if (p.stun() > 0) {
            CombatSystem.stun(victim, p.stun());
        }
        JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
        KekkeiTechniquesB.hitstop(victim, 3);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WOLF_HURT, SoundSource.PLAYERS, 0.8f, 0.4f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    // ================================================================ Earth: Earth Flow Rampart

    static void rampart(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        int half = Math.max(1, Math.round(p.radius()));
        int height = Math.max(2, p.count());
        double distance = Math.max(2.5, p.range());
        float yaw = (float) Math.toRadians(caster.getYRot());
        // Columns along an arc around the caster, bowed toward them at the ends.
        List<BlockPos> columns = new ArrayList<>();
        for (int w = -half; w <= half; w++) {
            double a = w * (Math.toRadians(100) / (2 * half));
            Vec3 dir = new Vec3(-Math.sin(yaw + a), 0, Math.cos(yaw + a));
            BlockPos base = BlockPos.containing(ElementalBehaviors.ground(level, caster.position().add(dir.scale(distance)).add(0, 1, 0)).add(0, 0.1, 0));
            if (!columns.contains(base)) {
                columns.add(base);
            }
        }
        BlockState wall = NoirmBlocks.EARTH_WALL.get().defaultBlockState();
        Set<LivingEntity> thrown = new HashSet<>();
        int[] age = {0};
        Vfx.quake(level, caster.position(), 0.45f, 16);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (age[0] % RAMPART_LAYER != 1) {
                return false;
            }
            int layer = age[0] / RAMPART_LAYER;
            for (BlockPos base : columns) {
                BlockPos pos = base.above(layer);
                Vec3 at = Vec3.atBottomCenterOf(pos);
                if (layer == 0) {
                    WindEarthVfx.groundBurst(level, at, 0.9f);
                } else if (level.random.nextInt(2) == 0) {
                    Vfx.one(level, WindEarthVfx.earthDust(0.6f, 40), at.add(0, 0.6, 0), new Vec3(0, 0.03, 0));
                }
                List<LivingEntity> there = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos));
                for (LivingEntity victim : there) {
                    if (JutsuEffects.isTarget(caster, victim) && thrown.add(victim)) {
                        JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, victim.position().subtract(caster.position()));
                        victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.35, 0));
                    }
                }
                if (level.getBlockState(pos).canBeReplaced() && there.isEmpty() && Destruction.enabled(level)) {
                    level.setBlock(pos, wall, 3);
                    level.scheduleTick(pos, wall.getBlock(), p.duration() + level.random.nextInt(20));
                }
            }
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.4f, 0.5f);
            return layer >= height - 1;
        });
    }

    // ================================================================ Earth: Added-Weight / Lightened Rock

    static void weightRock(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        int duration = Math.max(20, Math.round(p.duration() * Math.min(1.5f, power)));
        // The caster: light as a feather.
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.JUMP, duration, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, 0, false, false, true));
        Vfx.burst(level, new VfxOptions(VfxKind.SOOT, WindEarthVfx.EARTH_DUST_LIGHT, 0xE8DCC8, 0.4f, 1f, 30, -0.02f, 0.9f),
                caster.position().add(0, 0.3, 0), 10, 0.5, 0.04);
        Vfx.shockwave(level, caster.position().add(0, 0.05, 0), Vfx.palette(Nature.EARTH), 2f);
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        if (target != null) {
            JutsuEffects.hit(caster, target, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
            JutsuEffects.applyEffects(target, p.effect(), duration);
            if (p.stun() > 0) {
                CombatSystem.stun(target, p.stun());
            }
            WindEarthVfx.groundBurst(level, target.position(), 1.4f);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.5f);
        }
        int[] age = {0};
        GreatTechniques.later(now -> {
            age[0]++;
            if (age[0] > duration) {
                return true;
            }
            if (caster.isAlive() && age[0] % 4 == 0) {
                Vfx.one(level, new VfxOptions(VfxKind.SOOT, WindEarthVfx.EARTH_DUST_LIGHT, 0xE8DCC8, 0.25f, 0.6f, 24, -0.02f, 0.92f),
                        caster.position().add(level.random.nextGaussian() * 0.3, 0.1, level.random.nextGaussian() * 0.3), new Vec3(0, 0.05, 0));
            }
            if (target == null || !target.isAlive()) {
                return target != null || !caster.isAlive();
            }
            // Heavy: no jumping, no being thrown up; dragged back to the ground.
            Vec3 v = target.getDeltaMovement();
            if (v.y > 0 || !target.onGround()) {
                target.setDeltaMovement(v.x * 0.6, Math.min(v.y, target.onGround() ? 0 : -0.5), v.z * 0.6);
                target.hurtMarked = true;
            }
            if (age[0] % 5 == 0) {
                WindEarthVfx.weightRings(level, target.position(), target.getBbWidth() * 0.8 + 0.3, target.getBbHeight() + 0.5);
                Vfx.one(level, WindEarthVfx.earthDust(0.4f, 30), target.position().add(0, 0.1, 0), Vec3.ZERO);
            }
            if (age[0] % (PULSE * 2) == 0) {
                CombatSystem.addPostureDamage(target, p.posture() * power * 0.3f);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        WindEarthVfx.groundBlock(level, target.position())), target.getX(), target.getY() + 0.1, target.getZ(), 8, 0.4, 0.05, 0.4, 0.1);
            }
            return false;
        });
        JutsuBehaviors.sound(caster, SoundEvents.STONE_PLACE, 1f, 0.5f);
    }

    // ================================================================ Earth: the Falling Mountain

    static void fallingMountain(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        Vec3 ground = ElementalBehaviors.ground(level, target != null ? target.position() : ElementalBehaviors.aim(caster, p.range()));
        float radius = Mth.clamp(p.radius() * (float) Math.pow(Math.max(0.5f, power), 0.25), 2f, 5f);
        Vec3 sky = ground.add(0, MOUNTAIN_HEIGHT, 0);
        int gather = Math.max(8, p.duration());
        int fallStart = gather + MOUNTAIN_HANG;
        int impact = fallStart + MOUNTAIN_FALL_SLOW + MOUNTAIN_FALL_FAST;
        // The mass: a big core and boulders packed around it, each its own stone, turned at random.
        BlockState[] stones = {Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
                Blocks.TUFF.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState()};
        record Rock(Display.BlockDisplay display, Vector3f size, Quaternionf turn, Vector3f offset) {}
        List<Rock> rocks = new ArrayList<>();
        int[] age = {0};
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 2f, 0.3f);
        Vfx.quake(level, ground, 0.5f, radius * 6);
        GreatTechniques.later(now -> {
            age[0]++;
            int t = age[0];
            if (t == 1) {
                for (int i = 0; i < 7; i++) {
                    float s = i == 0 ? radius * 1.1f : radius * (0.45f + 0.25f * level.random.nextFloat());
                    Vector3f size = new Vector3f(s, s * (0.8f + 0.3f * level.random.nextFloat()), s);
                    Quaternionf turn = new Quaternionf().rotateXYZ(level.random.nextFloat() * 6.3f, level.random.nextFloat() * 6.3f, level.random.nextFloat() * 6.3f);
                    Vector3f offset = i == 0 ? new Vector3f() : new Vector3f((float) level.random.nextGaussian(), (float) level.random.nextGaussian() * 0.6f,
                            (float) level.random.nextGaussian()).normalize().mul(radius * 0.55f);
                    Vector3f tiny = new Vector3f(size).mul(0.05f);
                    Display.BlockDisplay d = RockAndCubeDisplays.spawn(level, sky, stones[i % stones.length], tiny,
                            RockAndCubeDisplays.centred(tiny, turn).add(offset), turn, false);
                    rocks.add(new Rock(d, size, turn, offset));
                }
                return false;
            }
            if (t == 2) {
                for (Rock rock : rocks) {
                    RockAndCubeDisplays.animate(rock.display(), rock.size(), RockAndCubeDisplays.centred(rock.size(), rock.turn()).add(rock.offset()),
                            rock.turn(), gather - 2);
                }
            }
            if (t < fallStart) {
                // The ground tears up into the sky to build it: rock and dust streaming upward,
                // and a dark ring on the ground showing where it will land.
                for (int i = 0; i < 4; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double d = radius * (0.8 + level.random.nextDouble());
                    Vec3 from = ground.add(Math.cos(a) * d, 0.3, Math.sin(a) * d);
                    Vec3 up = sky.subtract(from).normalize();
                    Vfx.one(level, new VfxOptions(VfxKind.SHARD, WindEarthVfx.ROCK, WindEarthVfx.ROCK_DARK, 0.3f, 0.25f, 24, -0.02f, 1f), from, up.scale(0.6));
                    Vfx.one(level, WindEarthVfx.earthDust(0.6f, 30), from, up.scale(0.25));
                }
                if (t % 4 == 0) {
                    WindEarthVfx.groundBurst(level, ground.add((level.random.nextDouble() - 0.5) * radius * 2, 0, (level.random.nextDouble() - 0.5) * radius * 2), 1f);
                    Vfx.burst(level, Vfx.ring(0x4A3420, radius * 1.1f, 10), ground.add(0, 0.06, 0), 1, 0, 0);
                }
                Vfx.burst(level, WindEarthVfx.earthDust(radius * 0.5f, 20), sky, 2, radius * 0.5, 0.02);
                return false;
            }
            if (t == fallStart || t == fallStart + MOUNTAIN_FALL_SLOW) {
                // It drops: slowly at first, then all at once.
                boolean slow = t == fallStart;
                float drop = slow ? 3f : (float) MOUNTAIN_HEIGHT;
                for (Rock rock : rocks) {
                    RockAndCubeDisplays.animate(rock.display(), rock.size(),
                            RockAndCubeDisplays.centred(rock.size(), rock.turn()).add(rock.offset()).add(0, -drop, 0), rock.turn(),
                            slow ? MOUNTAIN_FALL_SLOW : MOUNTAIN_FALL_FAST);
                }
                if (!slow) {
                    level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 2f, 0.4f);
                }
            }
            if (t < impact) {
                double fell = t < fallStart + MOUNTAIN_FALL_SLOW ? 3.0 * (t - fallStart) / MOUNTAIN_FALL_SLOW
                        : 3 + (MOUNTAIN_HEIGHT - 3) * (t - fallStart - MOUNTAIN_FALL_SLOW) / MOUNTAIN_FALL_FAST;
                Vec3 mass = sky.add(0, -fell, 0);
                Vfx.burst(level, WindEarthVfx.earthDust(radius * 0.4f, 24), mass.add(0, radius * 0.5, 0), 3, radius * 0.5, 0.02);
                return false;
            }
            // The impact.
            for (Rock rock : rocks) {
                RockAndCubeDisplays.remove(rock.display());
            }
            crash(caster, level, ground, radius, p, power);
            return true;
        });
    }

    private static void crash(LivingEntity caster, ServerLevel level, Vec3 ground, float radius, JutsuDefinition.Params p, float power) {
        WindEarthVfx.groundBurst(level, ground, radius);
        com.noirm.vfx.FireVfx.groundDebris(level, ground, radius);
        Vfx.burst(level, Vfx.glow(0xFFF0D0, 0x8A6A40, radius, radius * 0.4f, 4), ground.add(0, 0.5, 0), 1, 0, 0);
        for (int ring = 0; ring < 3; ring++) {
            Vfx.burst(level, Vfx.ring(ring == 0 ? 0x9A7A50 : 0x5A4430, radius * (1.4f + ring * 0.8f), 10 + ring * 4), ground.add(0, 0.06 + ring * 0.02, 0), 1, 0, 0);
        }
        // A heavy wall of dust rolling out, and hanging there.
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.one(level, WindEarthVfx.earthDust(radius * 0.22f + 0.4f, 36 + level.random.nextInt(14)), ground.add(out.scale(radius * 0.6)).add(0, 0.6, 0),
                    out.scale(0.25 + radius * 0.03).add(0, 0.02, 0));
        }
        for (int i = 0; i < 30; i++) {
            Vec3 dir = new Vec3(level.random.nextGaussian(), 0.6 + level.random.nextDouble(), level.random.nextGaussian()).normalize();
            Vfx.one(level, WindEarthVfx.rock(0.3f + level.random.nextFloat() * 0.3f, 40), ground.add(0, 1, 0), dir.scale(0.4 + radius * 0.06));
        }
        Vfx.quake(level, ground, 1.3f, radius * 8);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(radius + 1, radius, radius + 1),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && e.position().distanceTo(ground) <= radius + 1 + e.getBbWidth())) {
            float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(ground) / (radius + 2));
            JutsuEffects.hit(caster, victim, p.damage() * power * (0.45f + 0.55f * falloff), p.knockback(), p.posture() * power, 0, victim.position().subtract(ground));
            if (p.stun() > 0) {
                CombatSystem.stun(victim, p.stun());
            }
            KekkeiTechniquesB.hitstop(victim, 6);
        }
        KekkeiTechniquesB.hitstop(caster, 4);
        Destruction.crater(level, ground, radius * 0.7f, power * 1.6f);
        Destruction.spikes(level, ground, radius * 0.9f, Blocks.COBBLESTONE.defaultBlockState(), 2);
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3f, 0.4f);
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2f, 0.3f);
    }
}

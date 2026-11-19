package com.noirm.jutsu;

import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.network.CombatClientPackets;
import com.noirm.network.NoirmNetwork;
import com.noirm.vfx.ScorchDustMagnetVfx;
import com.noirm.vfx.Vfx;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chakra wave, Part 1: new techniques for Scorch, Dust and Magnet (docs/CHAKRA_WAVE.md), and the
 * defensive shell several natures share. Every number comes from the jutsu's {@code params}.
 * <ul>
 *   <li><b>heat_orbs</b> (Scorch): {@code count} small suns bloom in front of the caster, then drift
 *       at {@code speed} toward the enemies in {@code range} (spread among them), steering by
 *       {@code homing}. Each one that touches a body dries it out: {@code damage}, {@code effect}
 *       (thirst, weakness), steam. They live {@code duration} ticks and burst on walls.</li>
 *   <li><b>steam_mummify</b> (Scorch): three suns fly onto the enemy you look at and latch on for
 *       {@code duration} ticks, drying them out every half second ({@code damage}, {@code effect},
 *       held in place), then flare in a final burst of steam ({@code radius}).</li>
 *   <li><b>ward_shell</b> (Scorch, Dust, Magnet, Wind): a shell of the nature around the caster for
 *       {@code duration}: enemy projectiles that reach {@code radius} are destroyed (wind turns them
 *       back), enemies that touch it take {@code damage} and are pushed off ({@code knockback});
 *       {@code effect} on the caster while it lasts.</li>
 *   <li><b>detach_cube</b> (Dust): a cube of light swells around the enemy (or the point) you aim
 *       at to {@code radius} half-size over {@code duration} ticks, taking apart every block it can
 *       and hurting whoever is inside, then collapses in a white flash.</li>
 *   <li><b>detach_cone</b> (Dust): a square cone of light {@code range} long and {@code radius} wide
 *       at its base: everyone in it is hit, blocks in it are taken apart.</li>
 *   <li><b>magnet_pull</b> (Magnet): iron sand latches onto the enemy you look at and drags them in
 *       ({@code knockback}); metal armour and weapons make the pull, the damage and the posture
 *       damage stronger, and two or more metal pieces lock them up ({@code stun}).</li>
 *   <li><b>sand_arm</b> (Magnet): a giant arm of iron sand pours up beside the caster over
 *       {@code duration} ticks and slams down where you aim: {@code damage} in {@code radius}.</li>
 *   <li><b>needle_rain</b> (Magnet): a black cloud gathers over where you aim, then {@code count}
 *       iron needles rain down over {@code radius} during {@code duration} ticks.</li>
 *   <li><b>sand_tide</b> (Magnet, charged): a great sphere of iron sand forms in front of the caster
 *       over {@code duration} ticks, then bursts into {@code count} branching spikes that race
 *       {@code range} blocks along the ground in a fan, leaving black spikes behind.</li>
 * </ul>
 */
public final class KekkeiTechniquesB {
    /** Ticks a heat orb hovers and swells before it drifts off. */
    static final int ORB_BLOOM = 6;
    /** Ticks the Steaming Murder's suns take to reach their target. */
    static final int MUMMIFY_FLIGHT = 8;
    /** Ticks between shell contact hits, and between latched drying pulses. */
    static final int PULSE = 10;
    /** Ticks the Dust cube gathers, and holds at full size before it collapses. */
    static final int CUBE_GATHER = 4;
    static final int CUBE_HOLD = 6;
    static final int CUBE_COLLAPSE = 4;
    /** Ticks the iron sand cloud gathers before the needles fall. */
    static final int RAIN_GATHER = 12;
    /** Ticks the World Order's branches take to race their full length. */
    static final int TIDE_BRANCH = 12;

    /** Behaviours NPC ninjas use at range, and when they are hurt (see NinjaFightGoal). */
    public static final Set<String> NPC_RANGED = Set.of("heat_orbs", "steam_mummify", "detach_cube", "detach_cone", "magnet_pull",
            "sand_arm", "needle_rain", "sand_tide", "wind_crescent", "tornado", "headhunter", "rock_hounds", "weight_rock", "falling_mountain");
    public static final Set<String> NPC_DEFENSIVE = Set.of("ward_shell", "rampart");

    private KekkeiTechniquesB() {}

    // ================================================================ Scorch: heat orbs

    private static final class Orb {
        Vec3 pos;
        Vec3 vel;
        final LivingEntity target;
        final Vec3 fallback;
        boolean done;

        Orb(Vec3 pos, Vec3 vel, LivingEntity target, Vec3 fallback) {
            this.pos = pos;
            this.vel = vel;
            this.target = target;
            this.fallback = fallback;
        }
    }

    static void heatOrbs(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        int count = Math.max(1, p.count());
        float size = 0.35f + 0.3f * p.radius();
        List<LivingEntity> targets = enemiesInSight(caster, p.range());
        Vec3 look = ctx.look();
        Vec3 side = ctx.flatLook().cross(new Vec3(0, 1, 0)).normalize();
        Vec3 hands = caster.getEyePosition().add(look.scale(0.9)).add(0, -0.35, 0);
        List<Orb> orbs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0 : i / (double) (count - 1) - 0.5;
            // They bloom in an arc in front of the hands (a halo for a swarm).
            Vec3 at = hands.add(side.scale(t * Math.min(4, count * 0.6))).add(0, Math.abs(t) * -0.6 + (count > 3 ? 0.6 : 0), 0);
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            Vec3 aimDir = look.yRot((float) (-t * Math.toRadians(30)));
            orbs.add(new Orb(at, aimDir.scale(p.speed()), target, at.add(aimDir.scale(p.range()))));
        }
        Set<LivingEntity> scorched = new HashSet<>();
        int[] age = {0};
        int life = Math.max(ORB_BLOOM + 10, p.duration());
        float homing = Math.max(0.08f, p.homing());
        level.playSound(null, hands.x, hands.y, hands.z, com.noirm.sound.NoirmSounds.SCORCH_SIZZLE.get(), SoundSource.PLAYERS, 0.8f, 1.18f);
        level.playSound(null, hands.x, hands.y, hands.z, com.noirm.sound.NoirmSounds.FIRE_BURST.get(), SoundSource.PLAYERS, 0.6f, 0.77f);
        GreatTechniques.later(now -> {
            age[0]++;
            boolean anyLeft = false;
            for (Orb orb : orbs) {
                if (orb.done) {
                    continue;
                }
                anyLeft = true;
                if (age[0] <= ORB_BLOOM) {
                    // Blooming: they swell where they were born, a strong first frame.
                    ScorchDustMagnetVfx.sun(level, orb.pos, Vec3.ZERO, size * (0.4f + 0.6f * age[0] / ORB_BLOOM));
                    continue;
                }
                Vec3 goal = orb.target != null && orb.target.isAlive() ? chest(orb.target) : orb.fallback;
                Vec3 want = goal.subtract(orb.pos);
                if (want.lengthSqr() > 1e-4) {
                    orb.vel = orb.vel.normalize().add(want.normalize().scale(homing)).normalize().scale(p.speed());
                }
                orb.pos = orb.pos.add(orb.vel);
                ScorchDustMagnetVfx.sun(level, orb.pos, orb.vel.scale(0.5), size);
                BlockPos block = BlockPos.containing(orb.pos);
                if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
                    ScorchDustMagnetVfx.sunBurst(level, orb.pos, size);
                    Destruction.scorch(level, orb.pos, 1.2f);
                    orb.done = true;
                    continue;
                }
                for (LivingEntity victim : near(caster, level, orb.pos, size * 0.5 + 0.4)) {
                    desiccate(caster, victim, p, p.damage() * power, scorched.add(victim) ? 1f : 0.6f, orb.vel);
                    ScorchDustMagnetVfx.sunBurst(level, orb.pos, size);
                    orb.done = true;
                    break;
                }
                if (age[0] >= life) {
                    ScorchDustMagnetVfx.sunBurst(level, orb.pos, size * 0.7f);
                    orb.done = true;
                }
            }
            return !anyLeft || !caster.isAlive() && age[0] > life;
        });
    }

    /** A sun touching a body: it dries out. The second sun on the same body hurts a little less. */
    private static void desiccate(LivingEntity caster, LivingEntity victim, JutsuDefinition.Params p, float damage, float share, Vec3 dir) {
        JutsuEffects.hit(caster, victim, damage * share, p.knockback(), p.posture() * share, p.fireSeconds(), dir);
        JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
        ScorchDustMagnetVfx.desiccate(victim.level(), victim, 1f);
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), com.noirm.sound.NoirmSounds.SCORCH_SIZZLE.get(), SoundSource.PLAYERS, 0.8f, 1.14f);
    }

    // ================================================================ Scorch: Extreme Steaming Murder

    static void steamMummify(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        Vec3 hands = caster.getEyePosition().add(ctx.look().scale(0.8)).add(0, -0.35, 0);
        Vec3 side = ctx.flatLook().cross(new Vec3(0, 1, 0)).normalize();
        Vec3[] starts = {hands.add(side.scale(0.8)).add(0, 0.4, 0), hands.add(side.scale(-0.8)).add(0, 0.4, 0), hands.add(0, 1.0, 0)};
        int latched = Math.max(PULSE * 2, p.duration());
        int[] age = {0};
        level.playSound(null, hands.x, hands.y, hands.z, com.noirm.sound.NoirmSounds.SCORCH_SIZZLE.get(), SoundSource.PLAYERS, 1f, 0.84f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (!target.isAlive() || !caster.isAlive() || target.level() != level || target.distanceTo(caster) > p.range() * 2) {
                return true; // they escaped (or someone died): the suns gutter out
            }
            Vec3 c = chest(target);
            if (age[0] <= MUMMIFY_FLIGHT) {
                double t = age[0] / (double) MUMMIFY_FLIGHT;
                for (Vec3 start : starts) {
                    Vec3 mid = start.add(c).scale(0.5).add(0, 1.5, 0);
                    ScorchDustMagnetVfx.sun(level, ScorchDustMagnetVfx.curve(start, mid, c, t), Vec3.ZERO, 0.6f);
                }
                return false;
            }
            int held = age[0] - MUMMIFY_FLIGHT;
            // Latched: the suns circle tight around the body, which can barely move.
            ScorchDustMagnetVfx.orbitSuns(level, c, target.getBbWidth() * 0.7f + 0.35f, 3, 0.55f, now, target.getBbHeight() * 0.35);
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.3, 1, 0.3));
            target.hurtMarked = true;
            if (held % 3 == 0) {
                ScorchDustMagnetVfx.steam(level, c, 2, target.getBbWidth() * 0.4);
            }
            if (held % PULSE == 0 && held < latched) {
                JutsuEffects.hit(caster, target, p.damage() * power, 0, p.posture() * power * 0.5f, 0, Vec3.ZERO);
                JutsuEffects.applyEffects(target, p.effect(), p.effectTicks());
                CombatSystem.stun(target, Math.max(4, p.stun()));
                ScorchDustMagnetVfx.desiccate(level, target, 1f + held / (float) latched * 2f);
                level.playSound(null, c.x, c.y, c.z, com.noirm.sound.NoirmSounds.SCORCH_SIZZLE.get(), SoundSource.PLAYERS, 1f, 0.8f + held * 0.01f);
            }
            if (held < latched) {
                return false;
            }
            // The last flare: a white burst, a column of steam, the body left dry.
            float r = Math.max(1.5f, p.radius());
            ScorchDustMagnetVfx.sunBurst(level, c, r);
            Vfx.burst(level, Vfx.glow(ScorchDustMagnetVfx.SUN_WHITE, ScorchDustMagnetVfx.SUN_RIM, r * 2.4f, r, 6), c, 1, 0, 0);
            Vfx.shockwave(level, target.position().add(0, 0.1, 0), Vfx.palette(Nature.SCORCH), r * 1.6f);
            for (int i = 0; i < 6; i++) {
                ScorchDustMagnetVfx.steam(level, target.position().add(0, i * 0.5, 0), 4, 0.5);
            }
            JutsuEffects.hit(caster, target, p.damage() * power * 1.5f, p.knockback(), p.posture() * power, 0, target.position().subtract(caster.position()));
            for (LivingEntity other : near(caster, level, c, r)) {
                if (other != target) {
                    desiccate(caster, other, p, p.damage() * power * 0.6f, 1f, other.position().subtract(c));
                }
            }
            Destruction.scorch(level, target.position(), r);
            Vfx.quake(level, c, 0.6f, 18);
            hitstop(target, 6);
            hitstop(caster, 4);
            level.playSound(null, c.x, c.y, c.z, com.noirm.sound.NoirmSounds.EXPLOSION_BOOM.get(), SoundSource.PLAYERS, 1.2f, 1.18f);
            return true;
        });
    }

    // ================================================================ the shell (several natures)

    static void wardShell(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        Nature nature = JutsuBehaviors.nature(ctx);
        float power = ctx.power();
        float radius = Math.max(1.2f, p.radius());
        int duration = Math.max(20, p.duration());
        JutsuEffects.applyEffects(caster, p.effect(), duration);
        int[] age = {0};
        Set<Projectile> turned = new HashSet<>();
        shellOpening(level, caster, nature, radius);
        GreatTechniques.later(now -> {
            age[0]++;
            if (!caster.isAlive() || caster.level() != level || age[0] > duration) {
                shellClosing(level, caster, nature, radius);
                return true;
            }
            Vec3 feet = caster.position();
            Vec3 c = chest(caster);
            switch (nature == null ? Nature.WIND : nature) {
                case SCORCH -> ScorchDustMagnetVfx.orbitSuns(level, c, radius, 4, 0.45f, now, 0.6);
                case DUST -> ScorchDustMagnetVfx.dustShell(level, c, radius * 0.75, now);
                case MAGNET -> ScorchDustMagnetVfx.sandDome(level, feet, radius, now);
                default -> WindEarthVfx.windWall(level, feet, radius, caster.getBbHeight() + 0.6, now);
            }
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, caster.getBoundingBox().inflate(radius + 1),
                    e -> e.getOwner() != caster && !(e.getOwner() != null && JutsuEffects.isFriendly(caster, e.getOwner()))
                            && e.position().distanceTo(c) <= radius + 0.8)) {
                stopProjectile(level, caster, projectile, nature, turned);
            }
            if (age[0] % PULSE == 0) {
                for (LivingEntity victim : near(caster, level, c, radius + 0.4)) {
                    Vec3 away = victim.position().subtract(feet);
                    JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, p.fireSeconds(), away);
                    if (nature == Nature.SCORCH) {
                        ScorchDustMagnetVfx.desiccate(level, victim, 0.6f);
                    } else if (nature == Nature.DUST) {
                        ScorchDustMagnetVfx.motes(level, chest(victim), 8, 0.3);
                    }
                }
            }
            return false;
        });
        JutsuBehaviors.sound(caster, JutsuBehaviors.castSound(nature), 1f, 0.8f);
    }

    private static void shellOpening(ServerLevel level, LivingEntity caster, Nature nature, float radius) {
        Vec3 c = chest(caster);
        Vfx.Palette palette = Vfx.palette(nature);
        Vfx.flash(level, c, palette, radius * 0.7f);
        Vfx.shockwave(level, caster.position().add(0, 0.05, 0), palette, radius * 1.4f);
        if (nature == Nature.MAGNET) {
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                Vec3 ground = caster.position().add(Math.cos(a) * radius * 1.6, 0.1, Math.sin(a) * radius * 1.6);
                ScorchDustMagnetVfx.stream(level, ground, ground.add(c).scale(0.5).add(0, radius, 0), c.add(0, radius * 0.5, 0), 1, 0.5f);
            }
        } else if (nature == Nature.DUST) {
            ScorchDustMagnetVfx.cube(level, c, radius * 0.75, 0.16f, 6);
        } else if (nature == Nature.WIND || nature == null) {
            WindEarthVfx.pressureRing(level, c, new Vec3(0, 1, 0), radius, 0.2, 10);
            WindEarthVfx.dustSkirt(level, caster.position(), radius, 10);
        } else {
            ScorchDustMagnetVfx.sunBurst(level, c.add(0, 0.8, 0), 0.8f);
        }
    }

    private static void shellClosing(ServerLevel level, LivingEntity caster, Nature nature, float radius) {
        Vec3 c = chest(caster);
        if (nature == Nature.MAGNET) {
            ScorchDustMagnetVfx.sandImpact(level, caster.position(), radius * 0.6f);
        } else if (nature == Nature.DUST) {
            ScorchDustMagnetVfx.motes(level, c, 20, radius * 0.6);
        } else if (nature == Nature.SCORCH) {
            ScorchDustMagnetVfx.steam(level, c, 6, radius * 0.5);
        } else {
            WindEarthVfx.dustSkirt(level, caster.position(), radius, 6);
        }
    }

    /** A projectile meets the shell: wind throws it back at its thrower, the rest stop it dead. */
    private static void stopProjectile(ServerLevel level, LivingEntity caster, Projectile projectile, Nature nature, Set<Projectile> turned) {
        Vec3 at = projectile.position();
        if (nature == Nature.WIND || nature == null) {
            if (turned.add(projectile)) {
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.1));
                projectile.setOwner(caster);
                WindEarthVfx.pressureRing(level, at, projectile.getDeltaMovement(), 0.5, 0.15, 6);
            }
            return;
        }
        switch (nature) {
            case SCORCH -> ScorchDustMagnetVfx.sunBurst(level, at, 0.4f);
            case DUST -> {
                ScorchDustMagnetVfx.cube(level, at, 0.3, 0.1f, 4);
                ScorchDustMagnetVfx.motes(level, at, 10, 0.2);
            }
            default -> ScorchDustMagnetVfx.sandImpact(level, at, 0.5f);
        }
        if (projectile instanceof JutsuProjectile) {
            projectile.discard(); // the jutsu is swallowed
        } else {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.1)); // thrown steel just drops
        }
    }

    // ================================================================ Dust: the detachment cube

    static void detachCube(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 12);
        Vec3 center = target != null ? chest(target) : ElementalBehaviors.aim(caster, p.range()).add(0, 0.6, 0);
        float half = Mth.clamp(p.radius() * (float) Math.pow(Math.max(0.5f, power), 0.25), 0.8f, 3f);
        int grow = Math.max(4, p.duration());
        int end = CUBE_GATHER + grow + CUBE_HOLD + CUBE_COLLAPSE;
        Set<LivingEntity> caught = new HashSet<>();
        Display.BlockDisplay[] cube = {null};
        int[] age = {0};
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.8f);
        GreatTechniques.later(now -> {
            age[0]++;
            int t = age[0];
            if (t == 1) {
                cube[0] = RockAndCubeDisplays.spawn(level, center, Blocks.WHITE_STAINED_GLASS.defaultBlockState(), cubeSize(0.05f),
                        RockAndCubeDisplays.centred(cubeSize(0.05f), new Quaternionf()), new Quaternionf(), true);
            }
            if (t <= CUBE_GATHER) {
                // Anticipation: light falling into one point.
                Vfx.gather(level, center, Vfx.palette(Nature.DUST), half * 1.4f, 14);
                Vfx.burst(level, Vfx.glow(ScorchDustMagnetVfx.DUST_WHITE, ScorchDustMagnetVfx.DUST_PALE, 0.4f + t * 0.1f, 0.2f, 3), center, 1, 0, 0);
                if (t == CUBE_GATHER) {
                    level.playSound(null, center.x, center.y, center.z, com.noirm.sound.NoirmSounds.DUST_HUM.get(), SoundSource.PLAYERS, 1.4f, 1.22f);
                }
                return false;
            }
            int g = t - CUBE_GATHER;
            if (g <= grow) {
                double now2 = half * g / (double) grow;
                // The glass grows a step each tick (one long slide drifts out of step with the edges on a slow client).
                Vector3f size = cubeSize((float) now2 * 2);
                RockAndCubeDisplays.animate(cube[0], size, RockAndCubeDisplays.centred(size, new Quaternionf()), new Quaternionf(), 1);
                ScorchDustMagnetVfx.cube(level, center, now2, 0.14f + 0.06f * (float) (now2 / half), 3);
                eraseCube(level, center, now2, power * 1.6f);
                if (g % 3 == 0) {
                    for (LivingEntity victim : inCube(caster, level, center, now2)) {
                        caught.add(victim);
                        JutsuEffects.hit(caster, victim, p.damage() * power * 0.25f, 0, p.posture() * power * 0.3f, 0, Vec3.ZERO);
                        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4, false, false));
                        ScorchDustMagnetVfx.motes(level, chest(victim), 10, 0.3);
                    }
                }
                return false;
            }
            if (t <= CUBE_GATHER + grow + CUBE_HOLD) {
                ScorchDustMagnetVfx.cube(level, center, half, 0.22f, 3);
                return false;
            }
            if (t < end) {
                double shrink = half * (end - t) / (double) CUBE_COLLAPSE;
                ScorchDustMagnetVfx.cube(level, center, shrink, 0.2f, 3);
                Vector3f size = cubeSize((float) shrink * 2);
                RockAndCubeDisplays.animate(cube[0], size, RockAndCubeDisplays.centred(size, new Quaternionf()), new Quaternionf(), 1);
                return false;
            }
            // The collapse: everything inside comes apart in one white flash.
            RockAndCubeDisplays.remove(cube[0]);
            Vfx.burst(level, Vfx.glow(0xFFFFFF, ScorchDustMagnetVfx.DUST_PALE, half * 3f, half * 0.5f, 5), center, 1, 0, 0);
            Vfx.burst(level, Vfx.ring(ScorchDustMagnetVfx.DUST_PALE, half * 1.4f, 10), center, 1, 0, 0);
            ScorchDustMagnetVfx.motes(level, center, 40, half * 0.7);
            for (LivingEntity victim : inCube(caster, level, center, half + 0.3)) {
                caught.add(victim);
                JutsuEffects.hit(caster, victim, p.damage() * power * 0.6f, p.knockback(), p.posture() * power, 0, victim.position().subtract(center));
                if (p.stun() > 0) {
                    CombatSystem.stun(victim, p.stun());
                }
                hitstop(victim, 5);
            }
            Vfx.quake(level, center, 0.5f, half * 6);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.6f, 1.6f);
            level.playSound(null, center.x, center.y, center.z, com.noirm.sound.NoirmSounds.ICE_SHATTER.get(), SoundSource.PLAYERS, 1f, 0.77f);
            return true;
        });
    }

    private static Vector3f cubeSize(float side) {
        return new Vector3f(side, side, side);
    }

    /** Every block inside the cube that this power can take apart goes, with a mote of light. */
    private static void eraseCube(ServerLevel level, Vec3 center, double half, float power) {
        if (half < 0.4) {
            return;
        }
        BlockPos lo = BlockPos.containing(center.subtract(half, half, half));
        BlockPos hi = BlockPos.containing(center.add(half, half, half));
        for (BlockPos pos : BlockPos.betweenClosed(lo, hi)) {
            if (!level.getBlockState(pos).isAir() && Destruction.erase(level, pos, power) && level.random.nextInt(3) == 0) {
                ScorchDustMagnetVfx.motes(level, Vec3.atCenterOf(pos), 3, 0.3);
            }
        }
    }

    private static List<LivingEntity> inCube(LivingEntity caster, ServerLevel level, Vec3 center, double half) {
        AABB box = new AABB(center, center).inflate(half);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator());
    }

    // ================================================================ Dust: the cone

    static void detachCone(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 dir = ctx.look();
        Vec3 apex = caster.getEyePosition().add(dir.scale(0.7)).add(0, -0.3, 0);
        double length = p.range();
        double radius = Math.max(0.8, p.radius()) * Math.sqrt(Math.max(0.5f, power));
        ScorchDustMagnetVfx.cone(level, apex, dir, length, radius, 8);
        Vfx.burst(level, Vfx.glow(0xFFFFFF, ScorchDustMagnetVfx.DUST_PALE, 1.4f, 0.3f, 4), apex, 1, 0, 0);
        // Everyone in the cone.
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(length + radius),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && inCone(apex, dir, length, radius, chest(e), e.getBbWidth() * 0.5))) {
            JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, dir);
            JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
            ScorchDustMagnetVfx.cube(level, chest(victim), victim.getBbWidth() * 0.7, 0.1f, 5);
            ScorchDustMagnetVfx.motes(level, chest(victim), 12, 0.3);
        }
        // And every block it can take apart (a handful a tick would look timid: it all goes at once).
        int erased = 0;
        AABB box = new AABB(apex, apex.add(dir.scale(length))).inflate(radius);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (erased >= 160) {
                break;
            }
            if (!level.getBlockState(pos).isAir() && inCone(apex, dir, length, radius, Vec3.atCenterOf(pos), 0.3)
                    && Destruction.erase(level, pos, power * 1.4f)) {
                erased++;
                if (erased % 4 == 0) {
                    ScorchDustMagnetVfx.motes(level, Vec3.atCenterOf(pos), 3, 0.3);
                }
            }
        }
        int[] age = {0};
        GreatTechniques.later(now -> {
            // The afterimage: the cone fades out slower than it came.
            if (++age[0] == 3) {
                ScorchDustMagnetVfx.cone(level, apex, dir, length, radius, 14);
            }
            return age[0] >= 3;
        });
        level.playSound(null, apex.x, apex.y, apex.z, com.noirm.sound.NoirmSounds.DUST_HUM.get(), SoundSource.PLAYERS, 1.2f, 1.38f);
        level.playSound(null, apex.x, apex.y, apex.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
    }

    /** Is {@code point} (with {@code slack} of body) inside the square cone? */
    static boolean inCone(Vec3 apex, Vec3 dir, double length, double radius, Vec3 point, double slack) {
        Vec3 to = point.subtract(apex);
        double along = to.dot(dir);
        if (along < -0.5 || along > length + 0.5) {
            return false;
        }
        double width = radius * Math.max(0.1, along) / length + slack + 0.3;
        return to.subtract(dir.scale(along)).length() <= width;
    }

    // ================================================================ Magnet: the pull

    static void magnetPull(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        int metal = metalPieces(target);
        float boost = 1f + 0.35f * metal;
        Vec3 hands = caster.getEyePosition().add(ctx.look().scale(0.7)).add(0, -0.4, 0);
        Vec3 c = chest(target);
        ScorchDustMagnetVfx.stream(level, hands, hands.add(c).scale(0.5).add(0, 1.2, 0), c, 1, 0.45f);
        ScorchDustMagnetVfx.sandImpact(level, c, 0.6f);
        // Dragged in: stronger the more metal they carry, never past the caster.
        Vec3 in = caster.position().subtract(target.position());
        double distance = in.horizontalDistance();
        if (distance > 1.5) {
            Vec3 flat = new Vec3(in.x, 0, in.z).normalize();
            double pull = Math.min(p.knockback() * boost, (distance - 1.2) * 0.32);
            target.setDeltaMovement(flat.scale(pull).add(0, 0.22, 0));
            target.hurtMarked = true;
        }
        JutsuEffects.hit(caster, target, p.damage() * power * (1f + 0.25f * metal), 0, p.posture() * power * boost, 0, Vec3.ZERO);
        if (metal >= 2 && p.stun() > 0) {
            CombatSystem.stun(target, p.stun()); // the armour locks up in the field
        }
        int[] age = {0};
        GreatTechniques.later(now -> {
            if (!target.isAlive()) {
                return true;
            }
            ScorchDustMagnetVfx.fieldLines(level, target, now);
            if (age[0] % 2 == 0) {
                ScorchDustMagnetVfx.sandMass(level, chest(target), Vec3.ZERO, 0.4f);
            }
            return ++age[0] >= 8 + metal * 3;
        });
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
        JutsuBehaviors.sound(caster, com.noirm.sound.NoirmSounds.EARTH_CRACK.get(), 0.8f, 0.77f);
    }

    /** Metal a body carries: iron, chain, gold or netherite armour pieces, and a metal weapon in hand. */
    public static int metalPieces(LivingEntity body) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = body.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armor && (armor.getMaterial() == ArmorMaterials.IRON || armor.getMaterial() == ArmorMaterials.CHAIN
                    || armor.getMaterial() == ArmorMaterials.GOLD || armor.getMaterial() == ArmorMaterials.NETHERITE)) {
                count++;
            }
        }
        ItemStack hand = body.getMainHandItem();
        if (hand.getItem() instanceof TieredItem tiered && (tiered.getTier() == Tiers.IRON || tiered.getTier() == Tiers.GOLD || tiered.getTier() == Tiers.NETHERITE)) {
            count++;
        }
        return count;
    }

    // ================================================================ Magnet: the giant arm

    static void sandArm(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        Vec3[] spot = {ElementalBehaviors.ground(level, target != null ? target.position() : ElementalBehaviors.aim(caster, p.range()))};
        Vec3 side = ctx.flatLook().cross(new Vec3(0, 1, 0)).normalize();
        Vec3 shoulder = caster.position().add(side.scale(1.4)).add(0, 0.1, 0);
        int rise = Math.max(6, p.duration());
        float radius = Math.max(1.2f, p.radius());
        int[] age = {0};
        level.playSound(null, shoulder.x, shoulder.y, shoulder.z, com.noirm.sound.NoirmSounds.MAGNET_HISS.get(), SoundSource.PLAYERS, 1.4f, 0.71f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (!caster.isAlive()) {
                return true;
            }
            if (target != null && target.isAlive() && age[0] < rise - 2) {
                spot[0] = target.position(); // the hand follows its prey until it falls
            }
            Vec3 fist = spot[0].add(0, 0.6, 0);
            Vec3 bend = shoulder.add(fist).scale(0.5).add(0, 4.5 + shoulder.distanceTo(fist) * 0.25, 0);
            if (age[0] < rise) {
                // The arm pours up and over: drawn a little further each tick, the fist rearing high.
                double grown = Math.min(1, age[0] / (double) (rise - 2));
                Vec3 reared = ScorchDustMagnetVfx.curve(shoulder, bend, fist, 0.7 + 0.3 * grown);
                ScorchDustMagnetVfx.stream(level, shoulder, bend.add(0, 1.5 * grown, 0), reared.add(0, 3 * grown, 0), grown, 0.9f);
                ScorchDustMagnetVfx.sandMass(level, reared.add(0, 3 * grown, 0), Vec3.ZERO, 1.5f);
                if (age[0] % 3 == 0) {
                    ScorchDustMagnetVfx.sandImpact(level, shoulder, 0.6f);
                }
                return false;
            }
            // The slam.
            ScorchDustMagnetVfx.stream(level, shoulder, bend, fist, 1, 1.0f);
            ScorchDustMagnetVfx.sandMass(level, fist, new Vec3(0, -0.3, 0), 2f);
            ScorchDustMagnetVfx.sandImpact(level, spot[0], radius);
            WindEarthVfx.groundBurst(level, spot[0], radius);
            Vfx.quake(level, spot[0], 0.7f, radius * 7);
            for (LivingEntity victim : near(caster, level, spot[0].add(0, 0.8, 0), radius)) {
                Vec3 away = victim.position().subtract(spot[0]);
                JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, away);
                victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.25, 0));
                if (p.stun() > 0) {
                    CombatSystem.stun(victim, p.stun());
                }
                hitstop(victim, 4);
            }
            Destruction.crater(level, spot[0], radius * 0.45f, power);
            level.playSound(null, spot[0].x, spot[0].y, spot[0].z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.5f);
            level.playSound(null, spot[0].x, spot[0].y, spot[0].z, com.noirm.sound.NoirmSounds.MAGNET_HISS.get(), SoundSource.PLAYERS, 1.6f, 0.63f);
            return true;
        });
    }

    // ================================================================ Magnet: needle rain

    static void needleRain(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 center = ElementalBehaviors.ground(level, ElementalBehaviors.aim(caster, p.range()));
        Vec3 cloud = center.add(0, 7, 0);
        float radius = Math.max(1.5f, p.radius());
        int count = Math.max(1, p.count());
        int falling = Math.max(count, p.duration());
        int interval = Math.max(1, falling / count);
        Vec3 hands = caster.getEyePosition().add(ctx.look().scale(0.6)).add(0, -0.3, 0);
        int[] age = {0};
        int[] fallen = {0};
        level.playSound(null, hands.x, hands.y, hands.z, com.noirm.sound.NoirmSounds.MAGNET_HISS.get(), SoundSource.PLAYERS, 1.2f, 0.77f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (!caster.isAlive()) {
                return true;
            }
            // The cloud: a black, slowly turning mass with a glitter in it.
            double grown = Math.min(1, age[0] / (double) RAIN_GATHER);
            for (int i = 0; i < 4; i++) {
                double a = now * 0.12 + i * Math.PI / 2;
                Vec3 at = cloud.add(Math.cos(a) * radius * 0.7 * grown, Math.sin(now * 0.2 + i) * 0.3, Math.sin(a) * radius * 0.7 * grown);
                ScorchDustMagnetVfx.sandMass(level, at, new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.1), 1.2f * (float) grown + 0.3f);
            }
            if (age[0] <= RAIN_GATHER) {
                ScorchDustMagnetVfx.stream(level, hands, hands.add(cloud).scale(0.5).add(0, 2, 0), cloud, grown, 0.4f);
                return false;
            }
            if ((age[0] - RAIN_GATHER) % interval == 0 && fallen[0] < count) {
                fallen[0]++;
                double a = level.random.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(level.random.nextDouble()) * radius;
                Vec3 strike = ElementalBehaviors.ground(level, center.add(Math.cos(a) * d, 1.5, Math.sin(a) * d));
                // Aim some needles straight at whoever is under the cloud.
                List<LivingEntity> under = near(caster, level, center.add(0, 1, 0), radius + 0.5);
                if (!under.isEmpty() && level.random.nextInt(2) == 0) {
                    strike = under.get(level.random.nextInt(under.size())).position();
                }
                Vec3 from = cloud.add((level.random.nextDouble() - 0.5) * radius, 0, (level.random.nextDouble() - 0.5) * radius);
                Vec3 down = strike.subtract(from).normalize();
                for (double s = 0; s < from.distanceTo(strike); s += 0.6) {
                    ScorchDustMagnetVfx.needle(level, from.add(down.scale(s)), down.scale(0.6));
                }
                ScorchDustMagnetVfx.sandImpact(level, strike, 0.5f);
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(strike, strike).inflate(0.9, 2.2, 0.9),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator())) {
                    JutsuEffects.hit(caster, victim, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                }
                level.playSound(null, strike.x, strike.y, strike.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.5f, 0.6f);
            }
            if (fallen[0] >= count) {
                ScorchDustMagnetVfx.sandImpact(level, center, radius * 0.5f);
                return true;
            }
            return false;
        });
    }

    // ================================================================ Magnet: World Order

    private static final class Branch {
        Vec3 tip;
        Vec3 dir;
        double travelled;
        final double length;
        final float thickness;
        boolean split;

        Branch(Vec3 tip, Vec3 dir, double length, float thickness) {
            this.tip = tip;
            this.dir = dir;
            this.length = length;
            this.thickness = thickness;
        }
    }

    static void sandTide(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        Vec3 dir = ctx.flatLook();
        Vec3 sphere = caster.position().add(dir.scale(3)).add(0, 3.8, 0); // over the head: the target stays in sight
        float radius = Mth.clamp(p.radius() * (float) Math.pow(Math.max(0.5f, power), 0.25), 1f, 2.4f);
        int gather = Math.max(6, p.duration());
        int count = Math.max(3, p.count());
        double spread = Math.max(30, p.spread() <= 0 ? 110 : p.spread());
        double speed = p.range() / TIDE_BRANCH;
        List<Branch> branches = new ArrayList<>();
        Set<LivingEntity> hit = new HashSet<>();
        int[] age = {0};
        level.playSound(null, sphere.x, sphere.y, sphere.z, com.noirm.sound.NoirmSounds.MAGNET_HISS.get(), SoundSource.PLAYERS, 2f, 0.63f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (!caster.isAlive()) {
                return true;
            }
            if (age[0] <= gather) {
                // The sphere: iron sand streaming in from the ground all around and churning into a ball.
                double grown = age[0] / (double) gather;
                float r = radius * (float) (0.3 + 0.7 * grown);
                for (int i = 0; i < 8; i++) {
                    Vec3 off = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).normalize().scale(r);
                    Vec3 along = off.cross(new Vec3(0, 1, 0)).scale(0.15);
                    ScorchDustMagnetVfx.sandMass(level, sphere.add(off), along, 0.8f);
                }
                Vfx.one(level, ScorchDustMagnetVfx.billow(r * 0.9f, 4), sphere, Vec3.ZERO);
                if (age[0] % 3 == 1) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    Vec3 ground = caster.position().add(Math.cos(a) * 4, 0.1, Math.sin(a) * 4);
                    ScorchDustMagnetVfx.stream(level, ground, ground.add(sphere).scale(0.5).add(0, 1.5, 0), sphere, 1, 0.5f);
                }
                if (age[0] == gather) {
                    // It bursts: the branches start racing out.
                    for (int i = 0; i < count; i++) {
                        double off = count == 1 ? 0 : spread * (i / (double) (count - 1) - 0.5);
                        Vec3 d = dir.yRot((float) Math.toRadians(-off));
                        branches.add(new Branch(sphere, d, p.range(), 0.7f));
                    }
                    Vfx.burst(level, Vfx.glow(ScorchDustMagnetVfx.GLINT, ScorchDustMagnetVfx.FIELD, radius * 2f, radius * 0.4f, 4), sphere, 1, 0, 0);
                    ScorchDustMagnetVfx.sandImpact(level, sphere, radius);
                    Vfx.quake(level, sphere, 0.6f, 24);
                    level.playSound(null, sphere.x, sphere.y, sphere.z, com.noirm.sound.NoirmSounds.EXPLOSION_BOOM.get(), SoundSource.PLAYERS, 1.4f, 0.71f);
                }
                return false;
            }
            List<Branch> born = new ArrayList<>();
            boolean growing = false;
            for (Branch b : branches) {
                if (b.travelled >= b.length) {
                    continue;
                }
                growing = true;
                // A jagged path: each step wanders a little, and dives down to the ground first.
                Vec3 d = b.dir.yRot((float) ((level.random.nextDouble() - 0.5) * 0.5)).normalize();
                Vec3 next = b.tip.add(d.scale(speed));
                Vec3 floor = ElementalBehaviors.ground(level, next);
                next = new Vec3(next.x, Mth.lerp(0.5, next.y, floor.y + 0.8), next.z);
                ScorchDustMagnetVfx.stream(level, b.tip, b.tip.add(next).scale(0.5).add(0, 0.3, 0), next, 1, b.thickness);
                for (int s = 0; s < 3; s++) {
                    // Spikes bristling off the branch.
                    Vec3 spike = new Vec3(level.random.nextGaussian(), Math.abs(level.random.nextGaussian()) + 0.4, level.random.nextGaussian()).normalize();
                    for (double k = 0; k < 1.4; k += 0.35) {
                        Vfx.one(level, ScorchDustMagnetVfx.grain(0.16f - (float) k * 0.08f, 12), next.add(spike.scale(k)), Vec3.ZERO);
                    }
                }
                for (LivingEntity victim : near(caster, level, next, 1.4 + b.thickness)) {
                    if (hit.add(victim)) {
                        JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, d);
                        if (p.stun() > 0) {
                            CombatSystem.stun(victim, p.stun());
                        }
                        hitstop(victim, 6);
                        ScorchDustMagnetVfx.sandImpact(level, chest(victim), 0.8f);
                    }
                }
                if (b.travelled >= 4 && (int) (b.travelled / speed) % 4 == 1) {
                    Destruction.spikes(level, floor, 0.5f, Blocks.COAL_BLOCK.defaultBlockState(), 1 + Math.round(Math.min(1f, power)));
                }
                b.tip = next;
                b.travelled += speed;
                if (!b.split && b.travelled >= b.length * 0.45 && b.thickness > 0.4f) {
                    b.split = true;
                    double turn = level.random.nextBoolean() ? 35 : -35;
                    born.add(new Branch(next, b.dir.yRot((float) Math.toRadians(turn)), b.length * 0.45, b.thickness * 0.6f));
                }
            }
            branches.addAll(born);
            return !growing && born.isEmpty();
        });
    }

    // ================================================================ helpers

    /** The middle of a body. */
    static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /** Enemies whose middle is within {@code radius} (plus half their width) of {@code at}. */
    static List<LivingEntity> near(LivingEntity caster, ServerLevel level, Vec3 at, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 2),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                        && chest(e).distanceTo(at) <= radius + e.getBbWidth() * 0.5 + e.getBbHeight() * 0.25);
    }

    /** Enemies in sight within {@code range}, the one you look at first. */
    static List<LivingEntity> enemiesInSight(LivingEntity caster, double range) {
        LivingEntity first = JutsuBehaviors.lookTarget(caster, range, 20);
        Vec3 look = caster.getViewVector(1f);
        List<LivingEntity> all = new ArrayList<>(caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && e.distanceTo(caster) <= range && caster.hasLineOfSight(e)
                        && chest(e).subtract(caster.getEyePosition()).normalize().dot(look) > 0.3));
        all.sort(Comparator.comparingDouble(e -> -chest(e).subtract(caster.getEyePosition()).normalize().dot(look)));
        if (first != null) {
            all.remove(first);
            all.add(0, first);
        }
        return all;
    }

    /** Freezes a body's animation for a beat: the weight of a big hit. */
    static void hitstop(LivingEntity entity, int ticks) {
        NoirmNetwork.sendToTrackingAndSelf(entity, new CombatClientPackets.PlayAnimation(entity.getId(), CombatClientPackets.HITSTOP, ticks));
    }
}

package com.noirm.aburame;

import com.noirm.clan.Nature;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.player.ShinobiCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Working with swarms (docs/ABURAME.md, 2.5): sending one out of a technique, the Swarm key's recall,
 * park and send, and the hooks the rest of the game calls so swarms take part in fights: a sword
 * sweep thins them, a fire jutsu burns them, a wind jutsu scatters them, and a chakra projectile
 * that touches a wall, dome, cloud or sphere is <b>eaten</b> (Devour).
 */
public final class Swarms {
    /** Fire, wind and lightning jutsu reach swarms in this cone in front of the caster (degrees from the look). */
    public static final double JUTSU_CONE = 50;
    /** A chakra jutsu's cost is guessed from its projectile's damage (cost ≈ damage × this). */
    public static final float COST_PER_DAMAGE = 2f;

    private Swarms() {}

    /** The kind of harm a jutsu of {@code nature} does to insects. */
    public static Harm harmOf(Nature nature, boolean explosive) {
        if (explosive) {
            return Harm.EXPLOSION;
        }
        if (nature == null) {
            return Harm.JUTSU;
        }
        return switch (nature) {
            case FIRE, LAVA, SCORCH, BOIL, DUST -> Harm.FIRE;
            case EXPLOSION -> Harm.EXPLOSION;
            case LIGHTNING, STORM -> Harm.LIGHTNING;
            case ICE -> Harm.COLD;
            case WIND -> Harm.WIND;
            default -> Harm.JUTSU;
        };
    }

    /** Frogs, chickens and parrots eat insects (2.7). */
    public static boolean eatsInsects(Entity e) {
        return e instanceof Frog || e instanceof Chicken || e instanceof Parrot;
    }

    /** Every swarm {@code owner} has out in this level. */
    public static List<InsectSwarm> owned(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return level.getEntitiesOfClass(InsectSwarm.class, owner.getBoundingBox().inflate(ColonyGrowth.SCOUT_LEASH + 8),
                s -> !s.isRemoved() && owner.getUUID().equals(s.ownerId()));
    }

    // ================================================================ sending

    /** Mastery stars on {@code id} for a player (NPCs: none). Each adds 15% count, speed and drain. */
    public static int mastery(LivingEntity caster, ResourceLocation id) {
        if (caster instanceof Player player && id != null) {
            return ShinobiCapability.get(player).map(d -> d.jutsu().masteryLevel(id)).orElse(0);
        }
        return 0;
    }

    /**
     * Sends a swarm for a technique: commits the insects its {@code hiden} block asks for (scaled by
     * mastery), and puts the swarm in the world at {@code from}. Null if there was nothing to send
     * or the owner already has {@link ColonyGrowth#MAX_SWARMS_PER_OWNER} out.
     */
    public static InsectSwarm send(LivingEntity caster, ResourceLocation id, JutsuDefinition def, Vec3 from, SwarmMode mode) {
        if (!(caster.level() instanceof ServerLevel level) || def.hiden().isEmpty()) {
            return null;
        }
        JutsuDefinition.Hiden hiden = def.hiden().get();
        Species species = Species.byKey(hiden.species());
        if (species == null) {
            species = Species.KIKAICHU;
        }
        if (owned(caster).size() >= ColonyGrowth.MAX_SWARMS_PER_OWNER) {
            if (caster instanceof Player p) {
                p.displayClientMessage(Component.translatable("message.noirm.colony.too_many_swarms"), true);
            }
            return null;
        }
        int stars = mastery(caster, id);
        int want = Math.round(Colony.needed(caster, def, 1f) * (1f + ColonyGrowth.MASTERY_STEP * stars));
        return send(caster, species, want, from, mode, hiden.drain() * (1f + ColonyGrowth.MASTERY_STEP * stars), 1f + ColonyGrowth.MASTERY_STEP * stars);
    }

    /** Sends {@code want} insects of {@code species} as a swarm (fewer if fewer are left). */
    public static InsectSwarm send(LivingEntity caster, Species species, int want, Vec3 from, SwarmMode mode, float drain, float speed) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return null;
        }
        Colony.Commit commit = Colony.commit(caster, species, want);
        if (commit.count() <= 0) {
            return null;
        }
        InsectSwarm swarm = InsectSwarm.create(level, caster, species, commit.count(), from, mode);
        swarm.setDrainMul(drain);
        swarm.setSpeedMul(speed * (commit.fromJar() ? ColonyGrowth.JAR_SPEED : 1f));
        level.addFreshEntity(swarm);
        SwarmFx.pour(level, caster, commit.count());
        SwarmFx.sound(level, caster.position(), com.noirm.sound.NoirmSounds.INSECT_BURST, 0.6f, 1.1f);
        return swarm;
    }

    /** Where insects pour out of a body (the chest). */
    public static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.6, 0).add(e.getViewVector(1f).scale(0.6));
    }

    // ================================================================ the Swarm key

    /** Tap: every swarm flies home. */
    public static int recall(LivingEntity owner) {
        int n = 0;
        for (InsectSwarm s : owned(owner)) {
            if (s.mode() != SwarmMode.RETURN && s.mode() != SwarmMode.SEA && s.mode() != SwarmMode.JAR) {
                s.recall();
                n++;
            }
        }
        return n;
    }

    /** Hold on a creature: every free swarm (travelling, parked or returning) goes for it. */
    public static int sendAll(LivingEntity owner, LivingEntity target) {
        int n = 0;
        for (InsectSwarm s : owned(owner)) {
            SwarmMode m = s.mode();
            if (m == SwarmMode.CLOUD || m == SwarmMode.RETURN || m == SwarmMode.TRAVEL || m == SwarmMode.CLING && s.target() != target) {
                s.sendAt(target);
                n++;
            }
        }
        return n;
    }

    /** Hold on a block: park every free swarm there (an ambush cloud). */
    public static int parkAll(LivingEntity owner, Vec3 at) {
        int n = 0;
        for (InsectSwarm s : owned(owner)) {
            SwarmMode m = s.mode();
            if (m == SwarmMode.CLOUD || m == SwarmMode.RETURN || m == SwarmMode.TRAVEL || m == SwarmMode.CLING) {
                s.parkAt(at.add(0, 1, 0));
                n++;
            }
        }
        return n;
    }

    // ================================================================ hooks from the rest of the game

    /**
     * A jutsu was cast ({@code JutsuCaster}): fire, explosion and lightning jutsu burn enemy swarms in
     * front of the caster, wind scatters them. Projectiles are left to hit swarms themselves.
     */
    public static void onJutsuCast(LivingEntity caster, JutsuDefinition def, float power) {
        if (!(caster.level() instanceof ServerLevel level) || def.nature().isEmpty() || "projectile".equals(def.behavior())) {
            return;
        }
        Harm harm = harmOf(def.nature().get(), false);
        if (harm == Harm.JUTSU) {
            return;
        }
        float range = Math.max(4f, def.params().range() + def.params().radius());
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        float damage = Math.max(2f, (def.params().damage() + def.params().chargeDamage() * 0.5f) * Math.max(0.5f, power));
        for (InsectSwarm swarm : InsectSwarm.near(level, eye, range + 4)) {
            LivingEntity owner = swarm.owner();
            if (owner == null || owner == caster || JutsuEffects.isFriendly(owner, caster)) {
                continue;
            }
            Vec3 to = swarm.center().subtract(eye);
            double dist = to.length();
            boolean around = dist < def.params().radius() + swarm.radius() + 1; // a burst all around
            if (!around && (dist > range + swarm.radius() || look.dot(to.normalize()) < Math.cos(Math.toRadians(JUTSU_CONE)))) {
                continue;
            }
            if (harm == Harm.WIND) {
                swarm.scatter();
                swarm.harm(Harm.WIND, damage);
            } else {
                swarm.harm(harm, damage);
            }
        }
    }

    /** A melee sweep ({@code CombatSystem}): enemy swarms in the swing lose insects (30 per point). */
    public static void meleeSweep(LivingEntity attacker, float range, float damage) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 facing = Vec3.directionFromRotation(0, attacker.getYHeadRot());
        for (InsectSwarm swarm : InsectSwarm.near(level, attacker.position(), range + 4)) {
            if (!swarm.isPickable()) {
                continue;
            }
            LivingEntity owner = swarm.owner();
            if (owner == null || owner == attacker || JutsuEffects.isFriendly(owner, attacker)) {
                continue;
            }
            Vec3 to = swarm.center().subtract(attacker.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double reach = range + swarm.radius();
            if (flat.lengthSqr() > reach * reach) {
                continue;
            }
            if (flat.lengthSqr() > 1 && facing.dot(flat.normalize()) < 0.2) {
                continue;
            }
            swarm.hurt(attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker),
                    Math.max(1f, damage));
        }
    }

    /**
     * Devour (2.5, technique 12): a chakra projectile touching a wall, dome, cloud or sphere is eaten if
     * the swarm has 100 insects per chakra it cost (+2 insects per chakra). Fire, explosions and
     * lightning kill insects instead; a beast's chakra overfeeds them. Walls and domes stop arrows.
     */
    static void devourProjectiles(InsectSwarm swarm, ServerLevel level, LivingEntity owner) {
        SwarmMode m = swarm.mode();
        boolean devours = m == SwarmMode.WALL || m == SwarmMode.DOME || m == SwarmMode.CLOUD || m == SwarmMode.SPHERE || m == SwarmMode.SEA;
        boolean solid = m == SwarmMode.WALL || m == SwarmMode.DOME;
        AABB box = m == SwarmMode.SEA ? new AABB(swarm.center(), swarm.center()).inflate(swarm.radius()) : swarm.getBoundingBox().inflate(0.3);
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive())) {
            Entity shooter = p.getOwner();
            if (shooter == owner || shooter instanceof LivingEntity l && JutsuEffects.isFriendly(owner, l)) {
                continue;
            }
            if (m == SwarmMode.WALL && !SwarmShapes.inWall(swarm, p.position(), 0.6)) {
                continue;
            }
            if (m == SwarmMode.DOME && p.position().distanceTo(swarm.center()) > InsectSwarm.DOME_RADIUS + 0.6) {
                continue;
            }
            if (p instanceof JutsuProjectile jp) {
                Harm harm = harmOf(jp.nature(), jp.blastRadius() > 0);
                float cost = jp.damage() * COST_PER_DAMAGE;
                if (harm == Harm.JUTSU && devours && !jp.isBeastChakra()) {
                    if (swarm.count() >= cost * InsectSwarm.DEVOUR_PER_CHAKRA) {
                        swarm.grow(Math.round(cost * InsectSwarm.DEVOUR_GAIN)); // eaten
                        level.sendParticles(SwarmFx.TEAL, p.getX(), p.getY(), p.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
                        SwarmFx.sound(level, p.position(), com.noirm.sound.NoirmSounds.INSECT_ITCH, 0.8f, 0.7f);
                        p.discard();
                    } else if (solid) {
                        swarm.harm(Harm.JUTSU, jp.damage());
                        p.discard();
                    }
                } else if (harm != Harm.JUTSU || jp.isBeastChakra()) {
                    if (jp.isBeastChakra()) {
                        harm = Harm.EXPLOSION; // a tailed beast's chakra: they overfeed and burst
                    }
                    swarm.harm(harm, Math.max(2f, jp.damage()));
                    if (solid && !swarm.isRemoved()) {
                        p.discard(); // the wall holds for that hit (the anime)
                    }
                }
            } else if (solid) {
                swarm.harm(Harm.MELEE, 2f); // an arrow or a kunai stops in the insects
                p.setDeltaMovement(Vec3.ZERO);
                p.discard();
            }
            if (swarm.isRemoved()) {
                return;
            }
        }
    }

    /** Is {@code target} sheltered by one of its own domes from {@code attacker} (who is outside it)? */
    public static InsectSwarm domeFor(LivingEntity target, Entity attacker) {
        if (!(target.level() instanceof ServerLevel)) {
            return null;
        }
        for (InsectSwarm s : InsectSwarm.near(target.level(), target.position(), InsectSwarm.DOME_RADIUS + 2)) {
            if (s.mode() != SwarmMode.DOME) {
                continue;
            }
            LivingEntity owner = s.owner();
            if (owner == null || owner != target && !JutsuEffects.isFriendly(owner, target)) {
                continue;
            }
            double r = InsectSwarm.DOME_RADIUS;
            if (target.position().distanceToSqr(s.center()) < r * r + 1 && attacker.position().distanceToSqr(s.center()) > r * r) {
                return s;
            }
        }
        return null;
    }

    /** Total insects in {@code owner}'s swarms right now. */
    public static int countOut(LivingEntity owner) {
        int n = 0;
        for (InsectSwarm s : owned(owner)) {
            n += s.count();
        }
        return n;
    }
}

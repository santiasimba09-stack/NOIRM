package com.noirm.ally;

import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * A crew mate's mind, every tick: who to fight (their {@link Behaviour}, their
 * {@link Occupation}, their {@link Order}), where to be when there's no fight (following you,
 * holding or patrolling a post, training, meditating) and their job's duties (a scout's
 * report, a medic's healing, a gatherer's loot run). The fighting itself is
 * {@code NinjaFightGoal}'s.
 *
 * <p>Following is careful to never get stuck: a target that's a training dummy (outside the
 * Train order) or far behind is dropped, the path to you is renewed twice a second rather than
 * every tick, and a crew mate who can't make progress (or falls far behind) catches up by
 * jumping to a free spot next to you.
 */
public final class CrewBrain {
    // ---------------------------------------------------------------- following
    /** How close they keep to you (blocks): most, a bodyguard, and a scout ahead of you. */
    public static final double FOLLOW_DISTANCE = 4.0;
    public static final double BODYGUARD_DISTANCE = 2.5;
    public static final double SCOUT_AHEAD = 8.0;
    /** Farther than this from you (and you on the ground), they jump to your side. */
    public static final double TELEPORT_DISTANCE = 24.0;
    /** Seconds without getting closer before they jump to you (if farther than the next number). */
    public static final int STUCK_SECONDS = 3;
    public static final double STUCK_TELEPORT_DISTANCE = 10.0;
    /** While following, a fight farther than this from you is dropped. */
    public static final double LEASH = 20.0;
    /** Ticks between path updates. */
    public static final int REPATH_TICKS = 10;

    // ---------------------------------------------------------------- picking fights
    /** Aggressive crew mates (and vanguards, farther) go after hostiles this close. */
    public static final double AGGRO_RADIUS = 12.0;
    public static final double VANGUARD_RADIUS = 16.0;
    /** A bodyguard goes for anything targeting you within this range. */
    public static final double BODYGUARD_RADIUS = 16.0;
    /** Passive crew mates back off to you below this share of their health. */
    public static final float PASSIVE_RETREAT = 0.5f;
    /** After "fall back", no new fights for this long (ticks). */
    public static final int FALL_BACK_TICKS = 100;

    // ---------------------------------------------------------------- posts
    /** Guards fight hostiles this close to their post; patrols walk this far around it. */
    public static final double GUARD_RADIUS = 12.0;
    public static final double PATROL_RADIUS = 8.0;
    /** A guard chasing farther than this past their radius gives up and comes back. */
    public static final double POST_LEASH = 8.0;

    // ---------------------------------------------------------------- jobs
    public static final double SCOUT_RANGE = 32.0;
    public static final int SCOUT_MARK_TICKS = 200;
    public static final int SCOUT_REPORT_TICKS = 300;
    public static final int MEDIC_INTERVAL = 60;
    public static final double MEDIC_RANGE = 12.0;
    public static final float MEDIC_THRESHOLD = 0.7f;
    public static final float MEDIC_HEAL = 6f;
    public static final float MEDIC_COST = 15f;
    public static final double GATHER_RANGE = 10.0;
    public static final int GATHER_STACKS = 9;
    public static final double DELIVER_RANGE = 3.0;

    // ---------------------------------------------------------------- meditation
    public static final float MAX_NATURE_ENERGY = 100f;
    /** Nature energy a meditating crew mate gathers per second. */
    public static final float NATURE_ENERGY_PER_SECOND = 0.2f;

    private CrewBrain() {}

    // ================================================================ orders (from the screen, keys and commands)

    /**
     * Gives an order: a guard or patrol post is where you stand (or where they stand, if you're
     * far), and leaving Train drops the training dummy they were hitting.
     */
    public static void giveOrder(SparringNinja ally, Order order, Player leader) {
        if (order.usesPost()) {
            boolean near = leader != null && leader.level() == ally.level() && leader.distanceTo(ally) < 16;
            ally.allyState().setPost(near ? leader.blockPosition() : ally.blockPosition());
        }
        if (order != Order.TRAIN && ally.getTarget() instanceof TrainingDummy) {
            ally.setTarget(null);
        }
        ally.setOrder(order);
    }

    /** "Come here": drop the fight, follow, and run to you. */
    public static void come(SparringNinja ally, Player leader) {
        ally.setTarget(null);
        giveOrder(ally, Order.FOLLOW, leader);
        ally.allyState().holdFireUntil = ally.level().getGameTime() + 40;
        ally.getNavigation().moveTo(leader, 1.3);
    }

    /** "Fall back": drop the fight, follow, and don't pick a new one for a few seconds. */
    public static void fallBack(SparringNinja ally, Player leader) {
        come(ally, leader);
        ally.allyState().holdFireUntil = ally.level().getGameTime() + FALL_BACK_TICKS;
    }

    /** "Attack my target": fight {@code target} now, whatever their behaviour. */
    public static boolean attack(SparringNinja ally, LivingEntity target) {
        if (target == null || !target.isAlive() || Crew.sameSide(ally, target)) {
            return false;
        }
        ally.allyState().holdFireUntil = 0;
        ally.setTarget(target);
        return true;
    }

    // ================================================================ every tick

    /** The whole mind of a crew mate whose leader is around (from {@code SparringNinja.tickCrew}). */
    public static void tick(SparringNinja ally, Player leader) {
        AllyState state = ally.allyState();
        Order order = ally.order();
        if (order.usesPost() && state.post() == null) {
            state.setPost(ally.blockPosition());
        }
        dropBadTarget(ally, leader, order);
        if (ally.getTarget() == null && ally.level().getGameTime() >= state.holdFireUntil) {
            LivingEntity pick = pickTarget(ally, leader, order);
            if (pick != null) {
                ally.setTarget(pick);
            }
        }
        if (ally.tickCount % 20 == 0) {
            duties(ally, leader);
        }
        if (order == Order.MEDITATE && ally.getTarget() == null) {
            meditate(ally);
            return;
        }
        if (ally.getPose() == Pose.CROUCHING && !ally.isYielded()) {
            ally.setPose(Pose.STANDING);
        }
        if (ally.getTarget() == null) {
            move(ally, leader, order);
            if (ally.tickCount % 20 == 0 && ally.getHealth() < ally.getMaxHealth()) {
                ally.heal(Crew.REGEN_PER_SECOND);
            }
        }
    }

    // ================================================================ fights

    /** Drops a target that keeps them from doing their job (the old "won't follow" bug). */
    public static void dropBadTarget(SparringNinja ally, Player leader, Order order) {
        LivingEntity target = ally.getTarget();
        if (target == null) {
            return;
        }
        boolean drop = !target.isAlive() || Crew.sameSide(ally, target)
                || (target instanceof TrainingDummy && order != Order.TRAIN);
        if (!drop && order == Order.FOLLOW && ally.distanceTo(leader) > LEASH && target.distanceTo(leader) > LEASH - 4) {
            drop = true; // you moved on: leave it and catch up
        }
        BlockPos post = ally.allyState().post();
        if (!drop && order.usesPost() && post != null
                && target.distanceToSqr(Vec3.atCenterOf(post)) > sq(GUARD_RADIUS + POST_LEASH)) {
            drop = true; // don't get drawn away from the post
        }
        if (!drop && ally.allyState().behaviour() == Behaviour.PASSIVE && ally.getHealth() < ally.getMaxHealth() * PASSIVE_RETREAT) {
            drop = true; // a passive crew mate backs off when hurt
        }
        if (drop) {
            ally.setTarget(null);
        }
    }

    /** Who to fight next, or null: by behaviour, job and order. */
    public static LivingEntity pickTarget(SparringNinja ally, Player leader, Order order) {
        AllyState state = ally.allyState();
        Behaviour behaviour = state.behaviour();
        // Self-defence always: whoever just hurt them.
        LivingEntity attacker = ally.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && !Crew.sameSide(ally, attacker) && !(attacker instanceof TrainingDummy)
                && ally.tickCount - ally.getLastHurtByMobTimestamp() < 100) {
            return attacker;
        }
        if (order == Order.TRAIN) {
            return ally.tickCount % 20 == 0 ? nearest(ally, TrainingDummy.class, ally.position(), 24, d -> true) : null;
        }
        if (order.usesPost() && state.post() != null) {
            LivingEntity intruder = nearest(ally, LivingEntity.class, Vec3.atCenterOf(state.post()), GUARD_RADIUS, e -> hostile(ally, leader, e));
            if (intruder != null) {
                return intruder;
            }
        }
        if (behaviour == Behaviour.PASSIVE) {
            return null;
        }
        // Your fights: what you hit, and whoever hits you.
        LivingEntity pick = leader.getLastHurtMob();
        if (pick == null || !pick.isAlive() || Crew.sameSide(ally, pick) || pick instanceof TrainingDummy
                || leader.tickCount - leader.getLastHurtMobTimestamp() > 200) {
            pick = leader.getLastHurtByMob();
        }
        if (pick != null && pick.isAlive() && !Crew.sameSide(ally, pick) && !(pick instanceof TrainingDummy) && pick.distanceTo(ally) < 24) {
            return pick;
        }
        Occupation job = state.occupation();
        if (job == Occupation.BODYGUARD) {
            LivingEntity threat = nearest(ally, Mob.class, leader.position(), BODYGUARD_RADIUS,
                    m -> m.getTarget() == leader && !Crew.sameSide(ally, m));
            if (threat != null) {
                return threat;
            }
        }
        if (job == Occupation.MEDIC || job == Occupation.GATHERER || job == Occupation.SCOUT) {
            if (behaviour != Behaviour.AGGRESSIVE) {
                return null; // their job comes first
            }
        }
        if (behaviour == Behaviour.AGGRESSIVE || job == Occupation.VANGUARD) {
            double radius = job == Occupation.VANGUARD ? VANGUARD_RADIUS : AGGRO_RADIUS;
            Vec3 around = order == Order.FOLLOW ? leader.position() : ally.position();
            return nearest(ally, LivingEntity.class, around, radius, e -> hostile(ally, leader, e));
        }
        return null;
    }

    /**
     * Hostile to the crew: a monster (not a neutral one that's leaving you alone), or anything
     * that's after you or one of your crew. Never the crew, never village people, never dummies.
     */
    public static boolean hostile(SparringNinja ally, Player leader, LivingEntity e) {
        if (e == leader || e == ally || !e.isAlive() || Crew.sameSide(ally, e) || e instanceof TrainingDummy || e instanceof Player) {
            return false;
        }
        if (e instanceof SparringNinja ninja && ninja.isResident()) {
            return false;
        }
        LivingEntity aim = e instanceof Mob mob ? mob.getTarget() : null;
        boolean afterUs = aim != null && (aim == leader || Crew.sameSide(ally, aim));
        if (afterUs) {
            return true;
        }
        return e instanceof Enemy && !(e instanceof NeutralMob) && !(e instanceof SparringNinja);
    }

    private static <T extends LivingEntity> T nearest(SparringNinja ally, Class<T> type, Vec3 around, double radius,
                                                      java.util.function.Predicate<T> test) {
        List<T> found = ally.level().getEntitiesOfClass(type, new AABB(around, around).inflate(radius),
                e -> e != ally && e.isAlive() && e.position().distanceToSqr(around) <= radius * radius && test.test(e));
        return found.stream().min(Comparator.comparingDouble(ally::distanceToSqr)).orElse(null);
    }

    // ================================================================ moving (no fight)

    static void move(SparringNinja ally, Player leader, Order order) {
        AllyState state = ally.allyState();
        switch (order) {
            case FOLLOW -> follow(ally, leader);
            case WAIT, TRAIN -> ally.getNavigation().stop();
            case GUARD -> {
                BlockPos post = state.post();
                if (post != null && ally.distanceToSqr(Vec3.atBottomCenterOf(post)) > 2.5 * 2.5) {
                    if (ally.tickCount % REPATH_TICKS == 0 || ally.getNavigation().isDone()) {
                        ally.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
                    }
                } else {
                    ally.getNavigation().stop();
                }
            }
            case PATROL -> patrol(ally);
            default -> { }
        }
    }

    /** Keeps up with you: walks when a little behind, runs when far, jumps to you when stuck or very far. */
    static void follow(SparringNinja ally, Player leader) {
        AllyState state = ally.allyState();
        Occupation job = state.occupation();
        double keep = job == Occupation.BODYGUARD ? BODYGUARD_DISTANCE : FOLLOW_DISTANCE;
        Vec3 goal = leader.position();
        if (job == Occupation.SCOUT) {
            Vec3 look = leader.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() > 1.0E-4) {
                goal = goal.add(flat.normalize().scale(SCOUT_AHEAD));
            }
            keep = 2.5;
        }
        double distance = ally.distanceTo(leader);
        double toGoal = Math.sqrt(ally.distanceToSqr(goal));
        // Progress check, once a second: getting no closer while far behind means stuck.
        if (ally.tickCount % 20 == 0) {
            double moved = Math.hypot(ally.getX() - state.lastX, ally.getZ() - state.lastZ);
            state.stuckSeconds = toGoal > keep + 1 && moved < 0.5 ? state.stuckSeconds + 1 : 0;
            state.lastX = ally.getX();
            state.lastZ = ally.getZ();
        }
        boolean farAway = distance > TELEPORT_DISTANCE && leader.onGround();
        boolean stuck = state.stuckSeconds >= STUCK_SECONDS && distance > STUCK_TELEPORT_DISTANCE && leader.onGround();
        if (farAway || stuck || ally.level() != leader.level()) {
            jumpTo(ally, leader);
            return;
        }
        if (toGoal > keep) {
            if (ally.tickCount % REPATH_TICKS == 0 || ally.getNavigation().isDone()) {
                double speed = distance > 12 ? 1.3 : distance > 7 ? 1.15 : 1.0;
                if (job == Occupation.SCOUT) {
                    ally.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
                } else {
                    ally.getNavigation().moveTo(leader, speed);
                }
            }
        } else if (toGoal < keep - 1) {
            ally.getNavigation().stop();
        }
    }

    /** Lands next to you on a free spot (or on your spot if there's none). */
    static void jumpTo(SparringNinja ally, Player leader) {
        AllyState state = ally.allyState();
        state.stuckSeconds = 0;
        if (ally.level() != leader.level()) {
            if (leader.level() instanceof ServerLevel level) {
                ally.changeDimension(level);
            }
            return;
        }
        Vec3 spot = leader.position();
        for (int attempt = 0; attempt < 10; attempt++) {
            double x = leader.getX() + (ally.getRandom().nextDouble() - 0.5) * 4;
            double z = leader.getZ() + (ally.getRandom().nextDouble() - 0.5) * 4;
            AABB box = ally.getBoundingBox().move(x - ally.getX(), leader.getY() - ally.getY(), z - ally.getZ());
            BlockPos below = BlockPos.containing(x, leader.getY() - 0.5, z);
            if (ally.level().noCollision(ally, box) && ally.level().getBlockState(below).isSolid()) {
                spot = new Vec3(x, leader.getY(), z);
                break;
            }
        }
        ally.teleportTo(spot.x, spot.y, spot.z);
        ally.getNavigation().stop();
        state.lastX = ally.getX();
        state.lastZ = ally.getZ();
        if (ally.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 1, spot.z, 8, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Walks between random points around the post. */
    static void patrol(SparringNinja ally) {
        AllyState state = ally.allyState();
        BlockPos post = state.post();
        if (post == null) {
            return;
        }
        BlockPos point = state.patrolPoint;
        boolean arrived = point != null && ally.distanceToSqr(Vec3.atBottomCenterOf(point)) < 2.0;
        if (point == null || arrived || ally.getNavigation().isDone() && ally.tickCount % 40 == 0) {
            double angle = ally.getRandom().nextDouble() * Math.PI * 2;
            double r = PATROL_RADIUS * (0.4 + 0.6 * ally.getRandom().nextDouble());
            point = BlockPos.containing(post.getX() + 0.5 + Math.cos(angle) * r, post.getY(), post.getZ() + 0.5 + Math.sin(angle) * r);
            state.patrolPoint = point;
            ally.getNavigation().moveTo(point.getX() + 0.5, point.getY(), point.getZ() + 0.5, 0.75);
        }
    }

    /** Sitting still: recovering health and chakra, training reserves, gathering nature energy. */
    public static void meditate(SparringNinja ally) {
        ally.getNavigation().stop();
        ally.setPose(Pose.CROUCHING);
        if (ally.tickCount % 20 == 0) {
            AllyTraining.onMeditate(ally);
            ally.heal(Crew.REGEN_PER_SECOND * 2);
            AllyState state = ally.allyState();
            state.setNatureEnergy(state.natureEnergy() + NATURE_ENERGY_PER_SECOND);
            if (ally.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ENCHANT, ally.getX(), ally.getY() + 1.2, ally.getZ(), 6, 0.4, 0.4, 0.4, 0.4);
                if (ally.tickCount % 60 == 0) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 0.3, ally.getZ(), 3, 0.6, 0.2, 0.6, 0);
                }
            }
        }
    }

    // ================================================================ jobs (once a second)

    static void duties(SparringNinja ally, Player leader) {
        switch (ally.allyState().occupation()) {
            case SCOUT -> scout(ally, leader);
            case MEDIC -> {
                if (ally.tickCount % MEDIC_INTERVAL == 0) {
                    medic(ally, leader);
                }
            }
            case GATHERER -> gather(ally, leader);
            default -> { }
        }
    }

    /** Marks hostiles far around (they glow) and tells you how many it found. */
    public static int scout(SparringNinja ally, Player leader) {
        if (ally.tickCount % 40 != 0) {
            return 0;
        }
        int spotted = 0;
        for (LivingEntity e : ally.level().getEntitiesOfClass(LivingEntity.class, ally.getBoundingBox().inflate(SCOUT_RANGE),
                e -> hostile(ally, leader, e))) {
            if (!e.hasEffect(MobEffects.GLOWING)) {
                spotted++;
            }
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SCOUT_MARK_TICKS, 0, false, false));
        }
        AllyState state = ally.allyState();
        long now = ally.level().getGameTime();
        if (spotted > 0 && now - state.lastReport >= SCOUT_REPORT_TICKS) {
            state.lastReport = now;
            leader.displayClientMessage(Component.translatable("message.noirm.crew.scout.spotted", ally.getDisplayName(), spotted)
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return spotted;
    }

    /** Heals the most hurt of you and the crew nearby, for chakra. Returns who was healed. */
    public static LivingEntity medic(SparringNinja ally, Player leader) {
        LivingEntity patient = null;
        float worst = MEDIC_THRESHOLD;
        if (leader.isAlive() && leader.distanceTo(ally) <= MEDIC_RANGE && leader.getHealth() / leader.getMaxHealth() < worst) {
            patient = leader;
            worst = leader.getHealth() / leader.getMaxHealth();
        }
        for (SparringNinja mate : ally.level().getEntitiesOfClass(SparringNinja.class, ally.getBoundingBox().inflate(MEDIC_RANGE),
                m -> m.isAlive() && m.leaderId() != null && m.leaderId().equals(ally.leaderId()))) {
            float share = mate.getHealth() / mate.getMaxHealth();
            if (share < worst) {
                worst = share;
                patient = mate;
            }
        }
        if (patient == null || !ally.tryUseChakra(MEDIC_COST)) {
            return null;
        }
        patient.heal(MEDIC_HEAL + ally.combatReserves() * 0.1f);
        ally.getLookControl().setLookAt(patient);
        Vfx.aura(ally.level(), patient.position(), patient.getBbWidth(), patient.getBbHeight(), Vfx.palette(com.noirm.clan.Nature.YANG), 14);
        ally.level().playSound(null, patient.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.5f);
        return patient;
    }

    /** Picks up loot lying around you and hands it over when close. */
    public static void gather(SparringNinja ally, Player leader) {
        AllyState state = ally.allyState();
        List<ItemStack> carried = state.carried();
        if (!carried.isEmpty() && ally.distanceTo(leader) <= DELIVER_RANGE) {
            int count = carried.size();
            for (ItemStack stack : carried) {
                if (!leader.getInventory().add(stack)) {
                    leader.drop(stack, false);
                }
            }
            carried.clear();
            leader.displayClientMessage(Component.translatable("message.noirm.crew.gatherer.delivered", ally.getDisplayName(), count)
                    .withStyle(ChatFormatting.GREEN), true);
            ally.level().playSound(null, leader.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        if (ally.getTarget() != null || carried.size() >= GATHER_STACKS || ally.order() == Order.MEDITATE) {
            return;
        }
        Vec3 around = ally.order() == Order.FOLLOW ? leader.position() : ally.position();
        List<ItemEntity> loot = ally.level().getEntitiesOfClass(ItemEntity.class, new AABB(around, around).inflate(GATHER_RANGE),
                item -> item.isAlive() && !item.hasPickUpDelay() && item.getOwner() != leader);
        ItemEntity closest = loot.stream().min(Comparator.comparingDouble(ally::distanceToSqr)).orElse(null);
        if (closest == null) {
            return;
        }
        if (ally.distanceTo(closest) < 1.8) {
            pickUp(state, closest);
        } else {
            ally.getNavigation().moveTo(closest, 1.1);
        }
    }

    static void pickUp(AllyState state, ItemEntity item) {
        ItemStack stack = item.getItem().copy();
        for (ItemStack held : state.carried()) {
            if (ItemStack.isSameItemSameTags(held, stack) && held.getCount() < held.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                stack.shrink(moved);
            }
        }
        if (!stack.isEmpty()) {
            state.carried().add(stack);
        }
        item.discard();
    }

    private static double sq(double v) {
        return v * v;
    }

}

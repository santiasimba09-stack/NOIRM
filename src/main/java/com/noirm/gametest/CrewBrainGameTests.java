package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.ally.AllyState;
import com.noirm.ally.Behaviour;
import com.noirm.ally.CrewBrain;
import com.noirm.ally.NatureTeaching;
import com.noirm.ally.Occupation;
import com.noirm.ally.Order;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The crew mate's mind ({@link CrewBrain}): following, posts, behaviours, jobs, meditation. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrewBrainGameTests {
    private CrewBrainGameTests() {}

    /** The "won't follow" bug: after Train, the dummy stayed their target and they never left it. */
    @GameTest(template = "empty", batch = "noirm_crew_follow")
    public static void theyFollowAfterTraining(GameTestHelper helper) {
        TestKit.floor(helper);
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader);
        TrainingDummy dummy = helper.spawnWithNoFreeWill(NoirmEntities.TRAINING_DUMMY.get(), 2.5f, 1f, 2.5f);
        CrewBrain.giveOrder(ally, Order.TRAIN, leader);
        ally.setTarget(dummy);
        CrewBrain.giveOrder(ally, Order.FOLLOW, leader);
        helper.assertTrue(ally.getTarget() == null, "Follow drops the dummy");
        // Even if a dummy is their target some other way, following drops it on the next tick.
        ally.setTarget(dummy);
        CrewBrain.tick(ally, leader);
        helper.assertTrue(ally.getTarget() == null, "a dummy is never a target outside Train");
        // Far behind (you on the ground): they jump to your side.
        leader.setPos(leader.position().add(CrewBrain.TELEPORT_DISTANCE + 4, 0, 0));
        leader.setOnGround(true);
        CrewBrain.tick(ally, leader);
        helper.assertTrue(ally.distanceTo(leader) < 4, "they caught up, " + ally.distanceTo(leader) + " blocks away");
        ally.discard();
        dummy.discard();
        helper.succeed();
    }

    /** Guard: the post is where you stand; intruders near it are fought, far ones let go. */
    @GameTest(template = "empty", batch = "noirm_crew_guard")
    public static void guardsHoldTheirPost(GameTestHelper helper) {
        TestKit.floor(helper);
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader);
        ally.allyState().setBehaviour(Behaviour.PASSIVE); // a guard guards even when passive
        CrewBrain.giveOrder(ally, Order.GUARD, leader);
        helper.assertTrue(leader.blockPosition().equals(ally.allyState().post()), "the post is where you stand");
        Zombie zombie = zombie(helper, 0.5f, 2.5f);
        helper.assertTrue(CrewBrain.pickTarget(ally, leader, Order.GUARD) == zombie, "an intruder near the post is fought");
        ally.setTarget(zombie);
        zombie.setPos(Vec3.atCenterOf(ally.allyState().post()).add(CrewBrain.GUARD_RADIUS + CrewBrain.POST_LEASH + 4, 0, 0));
        CrewBrain.dropBadTarget(ally, leader, Order.GUARD);
        helper.assertTrue(ally.getTarget() == null, "they don't chase it far from the post");
        zombie.discard();
        ally.discard();
        helper.succeed();
    }

    /** Aggressive hunts hostiles, Defensive only fights yours, Passive none; a bodyguard defends you. */
    @GameTest(template = "empty", batch = "noirm_crew_behaviour")
    public static void behavioursPickDifferentFights(GameTestHelper helper) {
        TestKit.floor(helper);
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader);
        Zombie zombie = zombie(helper, 0.5f, 2.5f);
        ally.allyState().setBehaviour(Behaviour.AGGRESSIVE);
        helper.assertTrue(CrewBrain.pickTarget(ally, leader, Order.FOLLOW) == zombie, "aggressive: any hostile nearby");
        ally.allyState().setBehaviour(Behaviour.DEFENSIVE);
        helper.assertTrue(CrewBrain.pickTarget(ally, leader, Order.FOLLOW) == null, "defensive: not one that leaves you alone");
        ally.allyState().setOccupation(Occupation.BODYGUARD);
        zombie.setTarget(leader);
        helper.assertTrue(CrewBrain.pickTarget(ally, leader, Order.FOLLOW) == zombie, "a bodyguard goes for what's after you");
        ally.allyState().setOccupation(Occupation.NONE);
        zombie.setTarget(null);
        leader.setLastHurtMob(zombie);
        helper.assertTrue(CrewBrain.pickTarget(ally, leader, Order.FOLLOW) == zombie, "defensive: joins the fight you started");
        ally.allyState().setBehaviour(Behaviour.PASSIVE);
        helper.assertTrue(CrewBrain.pickTarget(ally, leader, Order.FOLLOW) == null, "passive: never starts one");
        // Fall back: no new fights for a while.
        ally.allyState().setBehaviour(Behaviour.AGGRESSIVE);
        CrewBrain.fallBack(ally, leader);
        CrewBrain.tick(ally, leader);
        helper.assertTrue(ally.getTarget() == null, "falling back, they hold fire");
        zombie.discard();
        ally.discard();
        helper.succeed();
    }

    /** A medic heals you for chakra, a scout marks hostiles, a gatherer brings you loot. */
    @GameTest(template = "empty", batch = "noirm_crew_jobs")
    public static void jobsDoTheirDuties(GameTestHelper helper) {
        TestKit.floor(helper);
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader);
        // Medic.
        leader.setHealth(leader.getMaxHealth() * 0.3f);
        float hurt = leader.getHealth();
        float chakra = ally.chakra();
        helper.assertTrue(CrewBrain.medic(ally, leader) == leader, "the medic tends to you");
        helper.assertTrue(leader.getHealth() > hurt && ally.chakra() < chakra, "healed, for chakra");
        // Scout.
        Zombie zombie = zombie(helper, 0.5f, 2.5f);
        ally.tickCount = 40;
        helper.assertTrue(CrewBrain.scout(ally, leader) == 1, "the scout spots the zombie");
        helper.assertTrue(zombie.hasEffect(MobEffects.GLOWING), "and marks it");
        zombie.discard();
        // Gatherer: picks up loot next to them, hands it over when you're close.
        ItemEntity item = new ItemEntity(helper.getLevel(), ally.getX(), ally.getY(), ally.getZ(), new ItemStack(Items.IRON_INGOT, 5));
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);
        leader.setPos(leader.position().add(8, 0, 0)); // too far to hand over
        CrewBrain.gather(ally, leader);
        helper.assertTrue(!item.isAlive() && ally.allyState().carried().size() == 1, "they picked it up");
        leader.setPos(ally.position().add(1, 0, 0));
        CrewBrain.gather(ally, leader);
        helper.assertTrue(ally.allyState().carried().isEmpty() && leader.getInventory().countItem(Items.IRON_INGOT) == 5, "and gave it to you");
        ally.discard();
        helper.succeed();
    }

    /** Meditating: they sit, and gather nature energy (kept with the rest of their crew data). */
    @GameTest(template = "empty", batch = "noirm_crew_meditate")
    public static void meditationGathersNatureEnergyAndAllIsSaved(GameTestHelper helper) {
        TestKit.floor(helper);
        Player leader = leader(helper);
        SparringNinja ally = ally(helper, leader);
        CrewBrain.giveOrder(ally, Order.MEDITATE, leader);
        ally.tickCount = 20;
        CrewBrain.tick(ally, leader);
        helper.assertTrue(ally.getPose() == Pose.CROUCHING, "they sit down");
        helper.assertTrue(Math.abs(ally.allyState().natureEnergy() - CrewBrain.NATURE_ENERGY_PER_SECOND) < 1e-4, "and gather nature energy");
        AllyState state = ally.allyState();
        state.setNatureEnergy(CrewBrain.MAX_NATURE_ENERGY + 50);
        helper.assertTrue(state.natureEnergy() == CrewBrain.MAX_NATURE_ENERGY, "up to a cap");
        state.setOccupation(Occupation.MEDIC);
        state.setBehaviour(Behaviour.DEFENSIVE);
        state.setPost(new BlockPos(1, 2, 3));
        state.queue().add(Nature.FIRE);
        state.pendingJutsu().add(Noirm.id("katon/great_fireball"));
        state.carried().add(new ItemStack(Items.APPLE, 3));
        CompoundTag tag = new CompoundTag();
        ally.saveWithoutId(tag);
        tag.remove("UUID");
        SparringNinja copy = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        copy.load(tag);
        AllyState back = copy.allyState();
        helper.assertTrue(back.occupation() == Occupation.MEDIC && back.behaviour() == Behaviour.DEFENSIVE, "job and behaviour kept");
        helper.assertTrue(new BlockPos(1, 2, 3).equals(back.post()) && back.queue().equals(List.of(Nature.FIRE)), "post and queue kept");
        helper.assertTrue(back.pendingJutsu().equals(List.of(Noirm.id("katon/great_fireball"))), "pending jutsu kept");
        helper.assertTrue(back.natureEnergy() == CrewBrain.MAX_NATURE_ENERGY, "nature energy kept");
        helper.assertTrue(back.carried().size() == 1 && back.carried().get(0).getCount() == 3, "carried loot kept");
        ally.discard();
        copy.discard();
        helper.succeed();
    }

    /** Meditating together speeds a lesson up; a jutsu asked for before its nature is learned the moment the nature lesson ends. */
    @GameTest(template = "empty", batch = "noirm_crew_pending")
    public static void pendingJutsuAreLearnedWithTheirNature(GameTestHelper helper) {
        TestKit.floor(helper);
        Player leader = leader(helper);
        ShinobiCapability.get(leader).ifPresent(d -> d.identity().addNature(Nature.WIND, Clan.SECONDARY_AFFINITY));
        SparringNinja ally = ally(helper, leader);
        ResourceLocation bullets = Noirm.id("futon/vacuum_bullets");
        helper.assertTrue(NatureTeaching.start(leader, ally, Nature.WIND), "the wind lesson starts");
        ally.allyState().pendingJutsu().add(bullets);
        ally.setOrder(Order.MEDITATE);
        // Meditating with them doubles the pace.
        leader.setOnGround(true);
        helper.assertTrue(com.noirm.senjutsu.Meditation.start(leader), "you sit down to meditate");
        NatureTeaching.study(ally, leader, 10f);
        helper.assertTrue(Math.abs(ally.allyState().lessonSeconds() - 10f * NatureTeaching.MEDITATE_TOGETHER_BONUS) < 1e-3,
                "meditating together: " + ally.allyState().lessonSeconds());
        com.noirm.senjutsu.Meditation.stop(leader);
        ally.allyState().setLessonSeconds(NatureTeaching.secondsNeeded(Nature.WIND));
        helper.assertTrue(NatureTeaching.study(ally, leader, 1f), "wind learned");
        helper.assertTrue(ally.knownJutsu().contains(bullets), "and the jutsu waiting on it");
        helper.assertTrue(ally.allyState().pendingJutsu().isEmpty(), "nothing left waiting");
        // Taking a nature out of the queue drops the jutsu waiting on it.
        ShinobiCapability.get(leader).ifPresent(d -> d.identity().addNature(Nature.FIRE, Clan.SECONDARY_AFFINITY));
        ShinobiCapability.get(leader).ifPresent(d -> d.identity().addNature(Nature.EARTH, Clan.SECONDARY_AFFINITY));
        NatureTeaching.start(leader, ally, Nature.EARTH);
        NatureTeaching.start(leader, ally, Nature.FIRE);
        ally.allyState().pendingJutsu().add(Noirm.id("katon/great_fireball"));
        NatureTeaching.dequeue(ally, Nature.FIRE);
        helper.assertTrue(ally.allyState().pendingJutsu().isEmpty(), "the fire jutsu was dropped with the fire lesson");
        ally.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static Player leader(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new Vec3(1.5, 1, 0.5)));
        return player;
    }

    /** A water-nature crew mate with AI off. */
    private static SparringNinja ally(GameTestHelper helper, Player leader) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        ninja.sealBeast(0);
        ninja.joinCrew(leader.getUUID());
        return ninja;
    }

    private static Zombie zombie(GameTestHelper helper, float x, float z) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, x, 1f, z);
        zombie.setInvulnerable(true);
        return zombie;
    }
}

package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.VillageMerchant;
import com.noirm.gear.NoirmGear;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.wild.EncounterType;
import com.noirm.world.wild.Encounters;
import com.noirm.world.wild.WildNinjas;
import com.noirm.world.wild.WildSpawner;
import com.noirm.world.wild.WildTravelGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Ninjas in the wild ({@code world/wild}): each encounter brings the right group, groups travel
 * and fight together, rogues go for players and the rest leave you be, the gamerule and the caps
 * hold, and wild ninjas go away when nobody is near (never crew mates or named ones).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildGameTests {
    private WildGameTests() {}

    /** A player in survival (the game's mock player counts as creative, and rogues leave creative players be). */
    private static Player survivalPlayer(GameTestHelper helper) {
        return new Player(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 0f, new GameProfile(UUID.randomUUID(), "wild-test")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    private static List<SparringNinja> spawn(GameTestHelper helper, ResourceLocation id, float danger) {
        return WildSpawner.spawn(helper.getLevel(), id, helper.absolutePos(new BlockPos(1, 1, 1)), danger, helper.getLevel().getRandom());
    }

    private static void discard(List<? extends LivingEntity> entities) {
        entities.forEach(LivingEntity::discard);
    }

    /** Removes every ninja in and around the test area (only for tests in their own batch). */
    private static void clearNinjas(GameTestHelper helper, int reach) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(reach);
        helper.getLevel().getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Player)).forEach(LivingEntity::discard);
    }

    /** Removes every wild ninja within the cap radius (any height). Only for tests in their own batch. */
    private static void clearWild(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(WildSpawner.CAP_RADIUS, 400, WildSpawner.CAP_RADIUS);
        helper.getLevel().getEntitiesOfClass(SparringNinja.class, area, WildNinjas::isWild).forEach(SparringNinja::discard);
    }

    private static boolean wearsChest(SparringNinja ninja, Item... items) {
        Item chest = ninja.getItemBySlot(EquipmentSlot.CHEST).getItem();
        for (Item item : items) {
            if (chest == item) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ groups

    /** Every encounter in the data brings a group of the right size, led by its first ninja, as hostile as it should be. */
    @GameTest(template = "empty", batch = "noirm_wild_types", timeoutTicks = 40)
    public static void encountersBringTheRightGroups(GameTestHelper helper) {
        floor(helper);
        helper.assertTrue(Encounters.all().size() >= 7, "the seven encounters are loaded, found " + Encounters.all().keySet());
        for (var entry : Encounters.all().entrySet()) {
            ResourceLocation id = entry.getKey();
            EncounterType type = entry.getValue();
            for (int round = 0; round < 3; round++) { // sizes are rolled: look at a few
                List<SparringNinja> group = spawn(helper, id, 20f);
                String name = id.getPath();
                helper.assertTrue(group.size() >= type.minSize() && group.size() <= type.clampedMax(), name + ": size " + group.size());
                SparringNinja leader = group.get(0);
                helper.assertTrue(WildNinjas.leaderOf(leader) == leader, name + ": the first one leads");
                helper.assertTrue(group.stream().filter(n -> WildNinjas.member(n).isLeader()).count() == 1, name + ": exactly one leader");
                for (SparringNinja n : group) {
                    helper.assertTrue(WildNinjas.isWild(n) && WildNinjas.leaderOf(n) == leader, name + ": everyone follows the leader");
                    helper.assertTrue(WildNinjas.isRogue(n) == type.hostile(), name + ": hostile " + type.hostile());
                    helper.assertTrue(!n.isPersistenceRequired(), name + ": not kept forever");
                    helper.assertTrue(!n.clanId().isEmpty(), name + ": they have a clan");
                    if (n != leader) {
                        helper.assertTrue(WildNinjas.sameGroup(n, leader) && n.isAlliedTo(leader) && leader.isAlliedTo(n), name + ": group mates are allies");
                    }
                    if ("rogue".equals(type.outfit())) {
                        helper.assertTrue(wearsChest(n, NoirmGear.ROGUE_CLOAK.get(), NoirmGear.TRAVEL_CLOAK.get(), NoirmGear.TATTERED_CLOAK.get(),
                                NoirmGear.ASSASSIN_SUIT.get(), NoirmGear.BANDOLIER.get()), name + ": rogues wear a cloak (or dress as assassins)");
                    }
                }
                if ("village".equals(type.outfit())) {
                    helper.assertTrue(wearsChest(leader, NoirmGear.JONIN_VEST.get()), name + ": the squad leader wears the jōnin vest");
                    Item uniform = group.get(1).getItemBySlot(EquipmentSlot.CHEST).getItem();
                    helper.assertTrue(group.stream().skip(1).allMatch(n -> n.getItemBySlot(EquipmentSlot.CHEST).getItem() == uniform)
                            && uniform != net.minecraft.world.item.Items.AIR, name + ": the others wear the same village vest");
                    helper.assertTrue(group.stream().skip(1).allMatch(n -> n.combatTaijutsu() < leader.combatTaijutsu()),
                            name + ": the jōnin is stronger than the genin");
                }
                if (type.sameClan()) {
                    helper.assertTrue(group.stream().allMatch(n -> n.clanId().equals(leader.clanId())), name + ": one clan");
                }
                discard(group);
            }
        }
        // Different groups are not allies.
        List<SparringNinja> a = spawn(helper, Encounters.TRAVELLERS, 20f);
        List<SparringNinja> b = spawn(helper, Encounters.TRAVELLERS, 20f);
        helper.assertFalse(WildNinjas.sameGroup(a.get(0), b.get(0)) || a.get(0).isAlliedTo(b.get(0)), "two groups are strangers");
        discard(a);
        discard(b);
        helper.succeed();
    }

    /** Stronger land, stronger ninjas: the danger grows with distance from spawn and with time played. */
    @GameTest(template = "empty", batch = "noirm_wild_types", timeoutTicks = 20)
    public static void dangerGrowsWithDistanceAndTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        float atSpawn = WildSpawner.danger(level, spawn, 0);
        float far = WildSpawner.danger(level, spawn.offset(2000, 0, 0), 0);
        float veteran = WildSpawner.danger(level, spawn, 72000L * 5);
        helper.assertTrue(Math.abs(atSpawn - WildSpawner.BASE_DANGER) < 0.01f, "at spawn, for a new player: the base danger");
        helper.assertTrue(far > atSpawn + 20, "far from spawn they're much stronger: " + far);
        helper.assertTrue(veteran > atSpawn + 10, "and after hours of play: " + veteran);
        helper.assertTrue(WildSpawner.danger(level, spawn.offset(100000, 0, 0), 72000L * 1000)
                <= WildSpawner.BASE_DANGER + WildSpawner.DISTANCE_DANGER_MAX + WildSpawner.TIME_DANGER_MAX + 0.01f, "with a ceiling");
        // Rogues don't turn up next to the spawn, the dangerous rogue only far away after an hour.
        EncounterType strong = Encounters.get(Encounters.ROGUE_STRONG);
        helper.assertTrue(strong.minDistance() >= 1000 && strong.minPlayedMinutes() >= 60 && strong.leaderStrength() >= 2f, "the dangerous rogue is rare and far");
        for (int i = 0; i < 40; i++) {
            ResourceLocation picked = WildSpawner.pick(level, spawn, 0, level.getRandom());
            helper.assertTrue(picked != null && !Encounters.get(picked).hostile(), "no rogues by the spawn for a new player, got " + picked);
        }
        helper.succeed();
    }

    /** Followers walk to their places in the formation around the leader. */
    @GameTest(template = "empty", batch = "noirm_wild_move", timeoutTicks = 260)
    public static void groupsStayTogether(GameTestHelper helper) {
        // A 13×13 floor (put back to air afterwards).
        for (int x = -5; x <= 7; x++) {
            for (int z = -5; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        List<SparringNinja> squad = WildSpawner.spawn(helper.getLevel(), Encounters.SQUAD, helper.absolutePos(new BlockPos(1, 1, 1)), 15f,
                helper.getLevel().getRandom());
        SparringNinja leader = squad.get(0);
        // Scatter the others to the corners.
        int[][] corners = {{-4, -4}, {6, 6}, {-4, 6}};
        for (int i = 1; i < squad.size(); i++) {
            BlockPos at = helper.absolutePos(new BlockPos(corners[i - 1][0], 1, corners[i - 1][1]));
            squad.get(i).moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
        // The formation: distinct places, behind the leader.
        Vec3 one = WildTravelGoal.slot(leader, 1), two = WildTravelGoal.slot(leader, 2), three = WildTravelGoal.slot(leader, 3);
        helper.assertTrue(one.distanceTo(two) > 1 && two.distanceTo(three) > 1 && one.distanceTo(three) > 1, "each follower has its own place");
        Vec3 forward = Vec3.directionFromRotation(0, leader.getYRot());
        helper.assertTrue(three.subtract(leader.position()).dot(forward) < 0, "and the places are behind the leader");
        helper.succeedWhen(() -> {
            for (SparringNinja n : squad) {
                helper.assertTrue(n.distanceTo(leader) < 6, "everyone gathers round the leader, one is " + n.distanceTo(leader)
                        + " DIAG " + squad.stream().map(m -> m.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()
                        + " t=" + (m.getTarget() == null ? "-" : m.getTarget().getType().toShortString() + "@" + m.getTarget().blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString())
                        + " alive=" + m.isAlive() + " yield=" + m.isYielded()).toList()
                        + " near=" + helper.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20), e -> !squad.contains(e))
                        .stream().map(e -> e.getType().toShortString() + "@" + e.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList());
            }
            discard(squad);
            for (int x = -5; x <= 7; x++) {
                for (int z = -5; z <= 7; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.AIR);
                }
            }
        });
    }

    // ------------------------------------------------------------ who fights whom

    /** Rogues go for players (not creative ones); travellers, wanderers and squads leave players and villagers be. */
    @GameTest(template = "empty", batch = "noirm_wild_targets", timeoutTicks = 20)
    public static void roguesAttackPlayersNeutralsDont(GameTestHelper helper) {
        floor(helper);
        Player survival = survivalPlayer(helper);
        Player creative = helper.makeMockPlayer();
        List<SparringNinja> rogues = spawn(helper, Encounters.ROGUE_BAND, 20f);
        List<SparringNinja> travellers = spawn(helper, Encounters.TRAVELLERS, 20f);
        List<SparringNinja> squad = spawn(helper, Encounters.SQUAD, 20f);
        VillageMerchant villager = helper.spawnWithNoFreeWill(NoirmEntities.VILLAGE_MERCHANT.get(), 0.5f, 1f, 0.5f);
        SparringNinja crewMate = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 2.5f);
        crewMate.joinCrew(survival.getUUID());
        SparringNinja rogue = rogues.get(0), traveller = travellers.get(0), jonin = squad.get(0);
        helper.assertTrue(WildNinjas.wouldAttack(rogue, survival), "a rogue attacks a player");
        helper.assertFalse(WildNinjas.wouldAttack(rogue, creative), "but not one in creative");
        helper.assertFalse(WildNinjas.wouldAttack(rogue, villager) || WildNinjas.wouldAttack(rogue, crewMate) || WildNinjas.wouldAttack(rogue, rogues.get(1)),
                "nor villagers, crew mates on their own, or its own band");
        for (SparringNinja neutral : List.of(traveller, travellers.get(1), jonin)) {
            helper.assertFalse(WildNinjas.wouldAttack(neutral, survival), "neutrals leave players be");
            helper.assertFalse(WildNinjas.wouldAttack(neutral, villager) || WildNinjas.wouldAttack(neutral, crewMate), "and villagers and crew mates");
            helper.assertFalse(neutral.wouldChallenge(survival), "and travellers and squads don't challenge you");
        }
        helper.assertTrue(WildNinjas.wouldAttack(jonin, rogue), "a squad goes after rogues");
        helper.assertFalse(WildNinjas.wouldAttack(traveller, rogue), "travellers don't");
        helper.assertTrue(WildNinjas.seeksFights(rogue) && WildNinjas.seeksFights(jonin) && !WildNinjas.seeksFights(traveller), "only rogues and squads look for fights");
        helper.assertTrue(WildNinjas.mayChallenge(crewMate), "ninjas from elsewhere keep their old ways");
        discard(rogues);
        discard(travellers);
        discard(squad);
        villager.discard();
        crewMate.discard();
        helper.succeed();
    }

    /** When one of a pair gets into a fight, the other joins it. */
    @GameTest(template = "empty", batch = "noirm_wild_fight", timeoutTicks = 100)
    public static void groupMatesJoinTheFight(GameTestHelper helper) {
        floor(helper);
        List<SparringNinja> pair = spawn(helper, Encounters.TRAVELLERS, 15f);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        pair.get(0).setTarget(zombie); // (as if the zombie had hit them)
        helper.succeedWhen(() -> {
            helper.assertTrue(pair.get(1).getTarget() == zombie, "the other traveller joins in");
            zombie.discard();
            discard(pair);
            clearNinjas(helper, 14);
        });
    }

    /** A squad that sees a rogue goes after it. */
    @GameTest(template = "empty", batch = "noirm_wild_hunt", timeoutTicks = 120)
    public static void squadsHuntRogues(GameTestHelper helper) {
        floor(helper);
        List<SparringNinja> squad = spawn(helper, Encounters.SQUAD, 15f);
        List<SparringNinja> rogue = WildSpawner.spawn(helper.getLevel(), Encounters.ROGUE, helper.absolutePos(new BlockPos(1, 1, 2)), 15f,
                helper.getLevel().getRandom());
        helper.succeedWhen(() -> {
            helper.assertTrue(squad.stream().anyMatch(n -> n.getTarget() == rogue.get(0)), "the squad goes after the rogue");
            discard(squad);
            discard(rogue);
            clearNinjas(helper, 14);
        });
    }

    // ------------------------------------------------------------ the spawner

    /** The gamerule at 0 stops wild ninjas; near the world spawn nothing turns up for a new player. */
    @GameTest(template = "empty", batch = "noirm_wild_rule", timeoutTicks = 20)
    public static void gameruleStopsSpawning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var rule = level.getGameRules().getRule(WildSpawner.RULE);
        int before = rule.get();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        try {
            rule.set(0, level.getServer());
            helper.assertTrue(WildSpawner.attempt(level, origin, 72000L * 10, level.getRandom(), 0, 1) == WildSpawner.Result.DISABLED,
                    "off: nothing turns up");
            rule.set(100, level.getServer());
            helper.assertTrue(WildSpawner.attempt(level, level.getSharedSpawnPos(), 0, level.getRandom(), 0, 1) == WildSpawner.Result.TOO_EARLY,
                    "a new player at the world spawn is left in peace");
            helper.assertTrue(WildSpawner.interval(1000) < WildSpawner.interval(100) && WildSpawner.chance(50) < WildSpawner.chance(100),
                    "the rule scales how often");
        } finally {
            rule.set(before, level.getServer());
        }
        helper.succeed();
    }

    /** A group turns up on open ground; with two groups around, no more come; never in water. */
    @GameTest(template = "empty", batch = "noirm_wild_caps", timeoutTicks = 20)
    public static void spawnerFindsLandAndCapsHold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        long veteran = 72000L * 10;
        clearWild(helper); // (anything a try left on the ground above)
        helper.assertTrue(WildSpawner.standable(level, origin), "the stone floor is good ground");
        helper.assertFalse(WildSpawner.nearVillage(level, origin), "and not in a village");
        // (The test world has ground high above the test area, so the "open sky" part of a good spot
        // is checked with a roof of our own; natural spawning is checked in a real world.)
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.WATER);
        helper.assertFalse(WildSpawner.standable(level, helper.absolutePos(new BlockPos(0, 1, 0))), "water isn't");
        helper.assertFalse(WildSpawner.isGoodSpot(level, helper.absolutePos(new BlockPos(0, 1, 0))), "so no spot there");
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        helper.assertFalse(WildSpawner.isGoodSpot(level, helper.absolutePos(new BlockPos(2, 1, 2))), "nor anywhere with a roof (caves)");
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
        helper.assertTrue(WildSpawner.attempt(level, origin, veteran, level.getRandom(), 0, 1) != WildSpawner.Result.CAPPED, "nobody around: no cap yet");
        clearWild(helper); // (that try may have found open ground on the surface far above the test)
        for (int i = 0; i < WildSpawner.MAX_GROUPS_NEAR; i++) {
            spawn(helper, Encounters.WANDERER, 15f);
        }
        int groups = WildNinjas.countNear(level, origin, WildSpawner.CAP_RADIUS)[0];
        helper.assertTrue(groups >= WildSpawner.MAX_GROUPS_NEAR, "two groups around, counted " + groups);
        helper.assertTrue(WildSpawner.attempt(level, origin, veteran, level.getRandom(), 0, 1) == WildSpawner.Result.CAPPED, "then the cap holds");
        clearWild(helper);
        var left = level.getEntitiesOfClass(SparringNinja.class, new AABB(origin).inflate(WildSpawner.CAP_RADIUS), WildNinjas::isWild);
        helper.assertTrue(left.isEmpty(), "(cleared) left: " + left.stream().map(n -> WildNinjas.member(n).type + "@" + n.blockPosition().subtract(origin)
                + (n.isAlive() ? "" : " dead")).toList());
        helper.succeed();
    }

    /** Far from everyone a group goes away, but never crew mates or named ninjas. */
    @GameTest(template = "empty", batch = "noirm_wild_despawn", timeoutTicks = 40)
    public static void despawnRules(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        SparringNinja loner = spawn(helper, Encounters.WANDERER, 15f).get(0);
        SparringNinja named = spawn(helper, Encounters.WANDERER, 15f).get(0);
        named.setCustomName(Component.literal("Kakuzu"));
        List<SparringNinja> pair = spawn(helper, Encounters.TRAVELLERS, 15f);
        SparringNinja recruit = pair.get(1);
        recruit.joinCrew(UUID.randomUUID());
        helper.assertTrue(WildNinjas.mayDespawn(loner, 200, 0), "far from every player: it may go");
        helper.assertFalse(WildNinjas.mayDespawn(loner, 30, 0), "close by: it stays");
        helper.assertTrue(WildNinjas.mayDespawn(loner, 80, WildNinjas.UNSEEN_TICKS + 1), "out of sight a while: it may go");
        helper.assertFalse(WildNinjas.mayDespawn(loner, 80, 20), "just out of sight: it stays");
        helper.assertFalse(WildNinjas.mayDespawn(named, 1000, 100000), "a named ninja never goes");
        helper.assertFalse(WildNinjas.mayDespawn(recruit, 1000, 100000), "a crew mate never goes");
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(recruit.getPersistentData().contains(WildNinjas.TAG), "a recruit leaves the wild for good");
            WildNinjas.sweep(level); // (no players in a test world: everyone is "far")
            helper.assertTrue(loner.isRemoved(), "the lone wanderer is gone");
            helper.assertTrue(pair.get(0).isRemoved(), "so is the recruit's old partner");
            helper.assertFalse(named.isRemoved() || recruit.isRemoved(), "the named one and the crew mate stay");
            named.discard();
            recruit.discard();
            helper.succeed();
        });
    }
}

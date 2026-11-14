package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.VillageMerchant;
import com.noirm.registry.NoirmEntities;
import com.noirm.registry.NoirmItems;
import com.noirm.world.village.NinjaVillageStructure;
import com.noirm.world.village.VillageBuilder;
import com.noirm.world.village.VillageKind;
import com.noirm.world.village.VillagePalette;
import com.noirm.world.village.VillagePlan;
import com.noirm.world.village.VillageRoutineGoal;
import com.noirm.world.village.VillageResidents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for ninja villages: the structures are registered, the builder places what it should,
 * merchants trade, and residents leave players in combat mode alone unless the village is
 * attacked. The building tests write a big area, so each runs in its own batch and puts every
 * block back afterwards.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    private VillageGameTests() {}

    @GameTest(template = "empty", batch = "noirm_village_registry")
    public static void villageStructuresAreRegistered(GameTestHelper helper) {
        var structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        var capital = structures.get(Noirm.id("hidden_village"));
        var ward = structures.get(Noirm.id("hidden_village_ward"));
        var outpost = structures.get(Noirm.id("ninja_outpost"));
        var town = structures.get(Noirm.id("ninja_town"));
        helper.assertTrue(capital instanceof NinjaVillageStructure village && village.kind() == VillageKind.CAPITAL, "the hidden village is registered");
        helper.assertTrue(ward instanceof com.noirm.world.village.VillageWardStructure wards && wards.anchors().location().equals(Noirm.id("hidden_villages"))
                && ward.terrainAdaptation() == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN,
                "with wards for the chunks around its core, anchored on the hidden villages");
        helper.assertTrue(outpost instanceof NinjaVillageStructure village && village.kind() == VillageKind.OUTPOST, "the outpost is registered");
        helper.assertTrue(town instanceof NinjaVillageStructure village && village.kind() == VillageKind.TOWN, "the town is registered");
        var sets = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var capitals = sets.get(Noirm.id("hidden_villages"));
        var wards = sets.get(Noirm.id("hidden_village_wards"));
        var settlements = sets.get(Noirm.id("ninja_settlements"));
        helper.assertTrue(capitals != null && wards != null && settlements != null, "and they have placement rules");
        helper.assertTrue(capitals.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread
                && spread.spacing() == 96 && spread.separation() == 48, "hidden villages are far apart (spacing 96, separation 48)");
        helper.assertTrue(wards.placement() instanceof com.noirm.world.village.WardPlacement placement
                && placement.anchors().location().equals(Noirm.id("hidden_villages")) && wards.structures().get(0).structure().value() == ward,
                "the wards are placed around the hidden villages");
        helper.assertTrue(settlements.structures().stream().allMatch(e -> e.structure().value() == town || e.structure().value() == outpost),
                "towns and outposts share a set");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_village_outpost", timeoutTicks = 40)
    public static void outpostIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BoundingBox area = new BoundingBox(origin.getX() - 18, origin.getY() - 15, origin.getZ() - 18,
                origin.getX() + 18, origin.getY() + 18, origin.getZ() + 18);
        Snapshot snapshot = new Snapshot(level, area);
        VillageBuilder.build(level, area, VillageKind.OUTPOST, VillagePalette.LEAF, origin, Rotation.NONE, 42L);
        try {
            expect(helper, level, origin.offset(0, 0, 0), Blocks.CAMPFIRE, "a campfire in the middle");
            expect(helper, level, origin.offset(0, 0, -VillageBuilder.OUTPOST_WALL), Blocks.OAK_LOG, "a palisade");
            expect(helper, level, origin.offset(0, 0, VillageBuilder.OUTPOST_WALL), Blocks.AIR, "with a gate in the south");
            expect(helper, level, origin.offset(-7, 9, -6), Blocks.OAK_PLANKS, "a watchtower platform");
            expect(helper, level, origin.offset(-7, 4, -5), Blocks.LADDER, "with a ladder");
            expect(helper, level, origin.offset(1, 2, -11), Blocks.TARGET, "a training post");
            expect(helper, level, origin.offset(-4, -1, 10), Blocks.GRASS_BLOCK, "on levelled grass");
        } catch (RuntimeException e) {
            snapshot.restore();
            throw e;
        }
        // The people are counted a couple of ticks later: the build may load the chunks next to
        // the test's own, and a freshly loaded chunk only shows its entities once the server has
        // caught up on chunk levels (counted at once, a resident there was missed now and then,
        // depending on where the test landed in the grid, and was then left behind by the cleanup).
        helper.runAfterDelay(2, () -> {
            try {
                AABB box = AABB.of(area);
                // (Other tests' leftovers may wander nearby, so only count residents.)
                var ninjas = level.getEntitiesOfClass(SparringNinja.class, box, SparringNinja::isResident);
                helper.assertTrue(ninjas.size() >= 3, "ninjas live there, found " + ninjas.size());
                helper.assertTrue(ninjas.stream().allMatch(n -> n.isPersistenceRequired()), "who never despawn");
                helper.assertTrue(!level.getEntitiesOfClass(VillageMerchant.class, box).isEmpty(), "and a merchant");
            } finally {
                snapshot.restore();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noirm_village_tower", timeoutTicks = 40)
    public static void kageTowerIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        int r = VillageBuilder.TOWER_RADIUS;
        int h = VillageBuilder.TOWER_HEIGHT;
        BoundingBox area = new BoundingBox(origin.getX() - 16, origin.getY() - 2, origin.getZ() - 16,
                origin.getX() + 16, origin.getY() + h + 22, origin.getZ() + 16);
        Snapshot snapshot = new Snapshot(level, area);
        VillageBuilder.buildKageTower(level, area, VillagePalette.LEAF, origin, true);
        try {
            expect(helper, level, origin.offset(r, 1, 0), Blocks.RED_TERRACOTTA, "a red drum");
            expect(helper, level, origin.offset(-r, h - 1, 0), Blocks.RED_TERRACOTTA, "all the way up");
            expect(helper, level, origin.offset(0, 1, r), Blocks.AIR, "with a door in front");
            expect(helper, level, origin.offset(0, 3, -(r - 1)), Blocks.LADDER, "a ladder inside");
            expect(helper, level, origin.offset(0, h, r + 2), Blocks.OAK_PLANKS, "a balcony on top");
            expect(helper, level, origin.offset(0, h + 1, r + 3), Blocks.OAK_FENCE, "with a railing");
            expect(helper, level, origin.offset(r + 1, VillageBuilder.TOWER_FLOOR, 0), Blocks.BRICK_STAIRS, "a flared eave at each floor");
            // The fire symbol on a round medallion over the door.
            expect(helper, level, origin.offset(-5, h - 8, r + 1), Blocks.ORANGE_TERRACOTTA, "a round medallion");
            for (int band = VillageBuilder.TOWER_FLOOR; band < h; band += VillageBuilder.TOWER_FLOOR) {
                if (Math.abs(band - (h - 8)) <= 5) {
                    expect(helper, level, origin.offset(0, band, r + 2), Blocks.AIR, "no eave across the medallion");
                }
            }
            int white = 0, red = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockState here = level.getBlockState(origin.offset(dx, h - 8 + dy, r + 1));
                    white += here.is(Blocks.WHITE_CONCRETE) ? 1 : 0;
                    red += here.is(Blocks.RED_CONCRETE) ? 1 : 0;
                }
            }
            helper.assertTrue(white >= 10 && red >= 5, "white, painted with the village symbol: " + white + " white, " + red + " red");
            expect(helper, level, origin.offset(0, h + 1, -3), Blocks.LECTERN, "the Kage's office");
            var kage = level.getEntitiesOfClass(SparringNinja.class, AABB.of(area), SparringNinja::hasCustomName);
            helper.assertTrue(kage.size() == 1, "the Kage is in the tower, found " + kage.size());
            SparringNinja leader = kage.get(0);
            helper.assertTrue(leader.getY() > origin.getY() + h, "up in the office");
            helper.assertTrue(leader.combatSpeed() >= 120 && leader.combatTaijutsu() >= 120, "far stronger than an ordinary ninja");
            helper.assertTrue(leader.getCustomName() != null
                    && leader.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title
                    && title.getKey().equals("entity.noirm.kage.hokage"), "called Hokage");
            helper.assertTrue(leader.isResident(), "and lives here");
        } finally {
            snapshot.restore();
        }
        helper.succeed();
    }

    /** The great gate and the wall of a hidden village, built from just the chunks around the gate. */
    @GameTest(template = "empty", batch = "noirm_village_gate", timeoutTicks = 40)
    public static void greatGateIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int w = VillageKind.CAPITAL.wall;
        // Put the village centre far to the north, so its gate lands in the test area.
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1 - w));
        BlockPos gate = origin.offset(0, 0, w);
        BoundingBox area = new BoundingBox(gate.getX() - 24, gate.getY() - 4, gate.getZ() - 14,
                gate.getX() + 24, gate.getY() + 26, gate.getZ() + 4);
        Snapshot snapshot = new Snapshot(level, area);
        VillageBuilder.build(level, area, VillageKind.CAPITAL, VillagePalette.LEAF, origin, Rotation.NONE, 7L, VillageKind.CAPITAL.height);
        try {
            expect(helper, level, gate.offset(8, 10, 0), Blocks.OAK_LOG, "the gate's great pillars");
            expect(helper, level, gate.offset(0, 6, 0), Blocks.AIR, "an open gateway");
            expect(helper, level, gate.offset(0, 18, -1), Blocks.OAK_PLANKS, "a lintel over it");
            expect(helper, level, gate.offset(-6, 3, -5), Blocks.GREEN_TERRACOTTA, "the great doors stand open");
            int wallZ = (int) Math.floor(Math.sqrt((double) w * w - 400)) - 1;
            expect(helper, level, origin.offset(20, 5, wallZ), VillagePalette.LEAF.wall, "the wall");
            expect(helper, level, origin.offset(0, -1, w - 2), Blocks.PACKED_MUD, "the avenue runs through");
            var guards = level.getEntitiesOfClass(SparringNinja.class, AABB.of(area), SparringNinja::isResident);
            helper.assertTrue(guards.size() >= 2, "guards at the gate, found " + guards.size());
        } finally {
            snapshot.restore();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_village_plan")
    public static void capitalPlanIsSound(GameTestHelper helper) {
        VillagePlan plan = VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.LEAF, 1234L);
        VillagePlan again = VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.LEAF, 1234L);
        helper.assertTrue(plan.lots.equals(again.lots) && plan.spawns.size() == again.spawns.size(), "the same seed gives the same village");
        helper.assertTrue(!plan.lots.equals(VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.LEAF, 99L).lots), "another seed, another village");
        helper.assertTrue(plan.lots.size() >= 400, "a big village: " + plan.lots.size() + " lots");
        for (VillagePlan.LotType landmark : new VillagePlan.LotType[] {VillagePlan.LotType.KAGE_TOWER, VillagePlan.LotType.ACADEMY,
                VillagePlan.LotType.HOSPITAL, VillagePlan.LotType.CLAN_COMPOUND, VillagePlan.LotType.SHRINE, VillagePlan.LotType.ONSEN,
                VillagePlan.LotType.RAMEN_SHOP, VillagePlan.LotType.GUARD_POST, VillagePlan.LotType.JONIN_STATION, VillagePlan.LotType.CEMETERY,
                VillagePlan.LotType.GIANT_TREE, VillagePlan.LotType.SPIRE, VillagePlan.LotType.YARD}) {
            helper.assertTrue(plan.first(landmark) != null, "it has a " + landmark);
        }
        helper.assertTrue(plan.count(VillagePlan.LotType.TRAINING_GROUND) >= 2, "training grounds");
        helper.assertTrue(plan.count(VillagePlan.LotType.APARTMENT) + plan.count(VillagePlan.LotType.ROUND_TOWER) >= 4, "tall buildings");
        helper.assertTrue(plan.count(VillagePlan.LotType.SHOP) + plan.count(VillagePlan.LotType.RESTAURANT) >= 30, "a market");
        helper.assertTrue(plan.hasRiver() && plan.gates.size() == 3, "a river, and three gates");
        int wall = VillageKind.CAPITAL.wall;
        for (int i = 0; i < plan.lots.size(); i++) {
            VillagePlan.Lot a = plan.lots.get(i);
            for (int[] corner : new int[][] {{a.x1(), a.z1()}, {a.x1(), a.z2()}, {a.x2(), a.z1()}, {a.x2(), a.z2()}}) {
                helper.assertTrue(corner[0] * corner[0] + corner[1] * corner[1] < wall * wall, a + " is inside the wall");
            }
            for (int j = i + 1; j < plan.lots.size(); j++) {
                helper.assertFalse(a.overlaps(plan.lots.get(j).x1(), plan.lots.get(j).z1(), plan.lots.get(j).x2(), plan.lots.get(j).z2(), 0),
                        a + " overlaps " + plan.lots.get(j));
            }
            boolean tower = a.type() == VillagePlan.LotType.KAGE_TOWER; // stands in the middle of its plaza
            for (int x = a.x1(); x <= a.x2(); x++) {
                for (int z = a.z1(); z <= a.z2(); z++) {
                    helper.assertFalse(!tower && plan.isRoad(x, z), a + " sits on a street");
                    helper.assertFalse(plan.isWater(x, z), a + " sits in the river");
                }
            }
        }
        java.util.Map<VillagePlan.Role, Long> people = plan.spawns.stream()
                .collect(java.util.stream.Collectors.groupingBy(VillagePlan.Spawn::role, java.util.stream.Collectors.counting()));
        helper.assertTrue(people.getOrDefault(VillagePlan.Role.KAGE, 0L) == 1, "one Kage");
        helper.assertTrue(people.getOrDefault(VillagePlan.Role.RUNNER, 0L) >= 3, "rooftop runners: " + people + " flat roofs " + plan.lots.stream().filter(l -> l.roofTop() > 0).count()
                + " types " + plan.lots.stream().collect(java.util.stream.Collectors.groupingBy(VillagePlan.Lot::type, java.util.stream.Collectors.counting())));
        helper.assertTrue(people.getOrDefault(VillagePlan.Role.PATROL, 0L) >= 3, "wall patrols: " + people);
        helper.assertTrue(people.getOrDefault(VillagePlan.Role.MERCHANT, 0L) >= 8, "shopkeepers: " + people);
        helper.assertTrue(people.getOrDefault(VillagePlan.Role.CIVILIAN, 0L) >= 10, "townsfolk: " + people);
        helper.assertTrue(people.getOrDefault(VillagePlan.Role.CHILD, 0L) >= 3, "children: " + people);
        for (VillagePlan.Spawn spawn : plan.spawns) {
            if (spawn.role() == VillagePlan.Role.RUNNER) {
                for (int[] point : spawn.route()) {
                    helper.assertTrue(plan.lots.stream().anyMatch(l -> l.roofTop() == point[1] && l.cx() == point[0] && l.cz() == point[2]),
                            "runners hop between flat roofs");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_village_plan")
    public static void townPlanIsSound(GameTestHelper helper) {
        VillagePlan plan = VillagePlan.uncached(VillageKind.TOWN, VillagePalette.SNOW, 55L);
        helper.assertTrue(plan.first(VillagePlan.LotType.CHIEF_HOUSE) != null && plan.first(VillagePlan.LotType.INN) != null
                && plan.first(VillagePlan.LotType.SHRINE) != null, "a chief's house, an inn and a shrine");
        helper.assertTrue(plan.lots.size() >= 40, "a real town: " + plan.lots.size() + " lots");
        helper.assertTrue(plan.first(VillagePlan.LotType.KAGE_TOWER) == null, "but no Kage");
        helper.assertTrue(plan.spawns.stream().anyMatch(s -> s.role() == VillagePlan.Role.CHIEF), "with a village chief");
        helper.assertTrue(plan.paving(0, 40) == VillagePlan.Paving.STREET && plan.paving(40, 0) == VillagePlan.Paving.STREET,
                "a main street and a cross street");
        helper.assertTrue(plan.props.stream().anyMatch(p -> p.type() == VillagePlan.PropType.LANTERN_LINE), "lanterns strung over the main street");
        helper.assertFalse(plan.hasRiver(), "no river");
        helper.succeed();
    }

    /** A rooftop runner's jump lands where it was aimed. */
    @GameTest(template = "empty", batch = "noirm_village_leap", timeoutTicks = 80)
    public static void ninjaLeapsBetweenRoofs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos to = from.offset(11, 3, 4);
        // The leap flies far outside this test's 3×3 area: clear its path of anything earlier tests left there.
        clearCorridor(level, from, to);
        level.setBlock(from.below(), Blocks.STONE.defaultBlockState(), 3);
        for (int y = 0; y < 4; y++) {
            level.setBlock(to.below().below(y), Blocks.STONE.defaultBlockState(), 3);
        }
        SparringNinja ninja = NoirmEntities.SPARRING_NINJA.get().create(level);
        ninja.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        ninja.setNoAi(false);
        ninja.joinClan(TestClans.PLAIN); // a fixed clan and no beast: random rolls made this flaky
        ninja.sealBeast(0);
        level.addFreshEntity(ninja);
        // Only the jump is under test: without goals nothing steers it mid-air (wandering made this flaky).
        ninja.goalSelector.removeAllGoals(goal -> true);
        ninja.targetSelector.removeAllGoals(goal -> true);
        net.minecraft.world.phys.Vec3 target = new net.minecraft.world.phys.Vec3(to.getX() + 0.5, to.getY() + 0.1, to.getZ() + 0.5);
        ninja.setDeltaMovement(VillageRoutineGoal.leapVelocity(ninja.position(), target));
        ninja.hasImpulse = true;
        helper.runAfterDelay(45, () -> {
            double miss = Math.hypot(ninja.getX() - target.x, ninja.getZ() - target.z);
            boolean landed = ninja.getY() >= to.getY() - 0.1 && miss < 2.5;
            BlockPos landedAt = ninja.blockPosition();
            ninja.discard();
            clearCorridor(level, from, to);
            helper.assertTrue(landed, "the leap lands on the far roof (landed at " + landedAt + ", aimed at " + to + ")");
            helper.succeed();
        });
    }

    private static void clearCorridor(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-1, -1, -1), to.offset(1, 4, 1))) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Villagers go out by day and home at night. */
    @GameTest(template = "empty", batch = "noirm_village_schedule")
    public static void villagersGoHomeAtNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long time = level.getDayTime();
        BlockPos home = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos work = helper.absolutePos(new BlockPos(2, 1, 2));
        VillageMerchant villager = helper.spawnWithNoFreeWill(NoirmEntities.VILLAGE_MERCHANT.get(), 1.5f, 1f, 1.5f);
        try {
            villager.setup(VillageMerchant.Role.CIVILIAN, 0, home, work, 8);
            helper.assertTrue(villager.getOffers().isEmpty(), "ordinary villagers don't trade");
            level.setDayTime(6000);
            villager.followSchedule();
            helper.assertTrue(villager.getRestrictCenter().equals(work), "out in the village by day");
            level.setDayTime(18000);
            villager.followSchedule();
            helper.assertTrue(villager.getRestrictCenter().equals(home), "home at night");
            VillageMerchant child = helper.spawnWithNoFreeWill(NoirmEntities.VILLAGE_MERCHANT.get(), 0.5f, 1f, 0.5f);
            child.setup(VillageMerchant.Role.CHILD, 0, home, work, 8);
            helper.assertTrue(child.isBaby(), "children are small");
            child.discard();
        } finally {
            level.setDayTime(time);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_village_merchant")
    public static void merchantsTrade(GameTestHelper helper) {
        VillageMerchant merchant = helper.spawnWithNoFreeWill(NoirmEntities.VILLAGE_MERCHANT.get(), 1.5f, 1f, 1.5f);
        var offers = merchant.getOffers();
        helper.assertTrue(offers.size() >= 8, "a full shop, got " + offers.size());
        helper.assertTrue(offers.stream().anyMatch(o -> o.getResult().is(NoirmItems.TRAINING_DUMMY.get())), "selling training dummies");
        helper.assertTrue(offers.stream().anyMatch(o -> o.getResult().is(NoirmItems.RAMEN.get())), "and ramen");
        helper.assertTrue(offers.stream().anyMatch(o -> o.getResult().is(NoirmItems.SEALING_SCROLL.get())), "and sealing scrolls");
        helper.assertTrue(offers.stream().allMatch(o -> o.getBaseCostA().is(net.minecraft.world.item.Items.EMERALD) || o.getResult().is(net.minecraft.world.item.Items.EMERALD)),
                "every trade is in emeralds");
        merchant.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_village_residents")
    public static void residentsOnlyDefend(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        CombatSystem.setCombatMode(player, true);
        BlockPos home = helper.absolutePos(new BlockPos(1, 1, 1));
        SparringNinja guard = VillageResidents.ninja(level, home, home, 5, 10, 10);
        SparringNinja neighbour = VillageResidents.ninja(level, home.offset(1, 0, 0), home, 5, 10, 10);
        SparringNinja wanderer = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        try {
            helper.assertTrue(guard.isResident() && guard.hasRestriction(), "a resident has a home");
            helper.assertFalse(guard.wouldChallenge(player), "residents don't challenge a player in combat mode");
            helper.assertTrue(wanderer.wouldChallenge(player), "ninjas in the wild still do");
            helper.assertTrue(guard.isAlliedTo(neighbour), "neighbours are on the same side");
            // Attack one: the village answers. (The mock player counts as creative, and creative
            // players are left alone, so a wild ninja does the attacking.)
            wanderer.setInvulnerable(false);
            helper.assertTrue(guard.hurt(guard.damageSources().mobAttack(wanderer), 2f), "the guard is hit");
            helper.assertTrue(neighbour.getTarget() == wanderer, "hurting a resident brings the neighbours");
            // Residents' flag survives a save.
            var tag = new net.minecraft.nbt.CompoundTag();
            guard.saveWithoutId(tag);
            SparringNinja loaded = NoirmEntities.SPARRING_NINJA.get().create(level);
            loaded.load(tag);
            helper.assertTrue(loaded.isResident() && loaded.hasRestriction(), "and is saved with the world");
        } finally {
            CombatSystem.setCombatMode(player, false);
            AABB around = new AABB(home).inflate(8);
            level.getEntitiesOfClass(LivingEntity.class, around, e -> !(e instanceof Player)).forEach(LivingEntity::discard);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static void expect(GameTestHelper helper, ServerLevel level, BlockPos pos, Block block, String what) {
        BlockState state = level.getBlockState(pos);
        helper.assertTrue(state.is(block), what + ": expected " + block + " at " + pos + ", found " + state);
    }

    /** Remembers every block in an area (and clears its mobs on restore), so a test can build freely. */
    private static final class Snapshot {
        private final ServerLevel level;
        private final BoundingBox area;
        private final BlockState[] states;

        Snapshot(ServerLevel level, BoundingBox area) {
            this.level = level;
            this.area = area;
            this.states = new BlockState[area.getXSpan() * area.getYSpan() * area.getZSpan()];
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                states[i++] = level.getBlockState(pos);
            }
        }

        void restore() {
            level.getEntitiesOfClass(LivingEntity.class, AABB.of(area), e -> !(e instanceof Player)).forEach(LivingEntity::discard);
            int i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                BlockState old = states[i++];
                if (level.getBlockState(pos) != old) {
                    level.setBlock(pos, old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, AABB.of(area).inflate(2)).forEach(e -> e.discard());
        }
    }
}

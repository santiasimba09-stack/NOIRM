package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.world.village.VillageBuilder;
import com.noirm.world.village.VillageKind;
import com.noirm.world.village.VillagePalette;
import com.noirm.world.village.VillagePlan;
import com.noirm.world.village.VillagePlan.Lot;
import com.noirm.world.village.VillagePlan.LotType;
import com.noirm.world.village.VillagePlan.Paving;
import com.noirm.world.village.VillagePlan.PropType;
import com.noirm.world.village.VillagePlan.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests for how villages are laid out (villages v3): the hidden village's fan of avenues and arcs
 * under the Kage Rock, its wards, small packed plots, river and bridges, colourways, street
 * furniture and people; the Sand village's spokes; the post town. The plan tests only plan (fast);
 * the building tests build a small slice of a capital and put every block back afterwards.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageLayoutGameTests {
    private VillageLayoutGameTests() {}

    // ------------------------------------------------------------ plans

    @GameTest(template = "empty", batch = "noirm_layout_plan")
    public static void leafCapitalIsAFan(GameTestHelper helper) {
        VillagePlan plan = VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.LEAF, 1234L);
        helper.assertTrue(fingerprint(plan) == fingerprint(VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.LEAF, 1234L)),
                "the same seed gives the same village, streets and all");
        checkCapital(helper, plan, true);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_layout_plan")
    public static void snowCapitalsAreSound(GameTestHelper helper) {
        for (long seed : new long[] {77L, 4242L}) {
            checkCapital(helper, VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.SNOW, seed), true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_layout_plan")
    public static void sandCapitalHasSpokes(GameTestHelper helper) {
        VillagePlan plan = VillagePlan.uncached(VillageKind.CAPITAL, VillagePalette.SAND, 31L);
        checkCapital(helper, plan, false);
        helper.assertTrue(plan.towerX == 0 && plan.towerZ == 0, "the Kazekage sits in the middle");
        // Eight spokes leave the central plaza.
        for (int bearing = 0; bearing < 360; bearing += 45) {
            double a = Math.toRadians(bearing);
            int x = (int) Math.round(Math.sin(a) * 120), z = (int) Math.round(Math.cos(a) * 120);
            helper.assertTrue(plan.paving(x, z) == Paving.AVENUE, "a spoke at " + bearing + "°");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_layout_plan")
    public static void townIsAPostTown(GameTestHelper helper) {
        VillagePlan plan = VillagePlan.uncached(VillageKind.TOWN, VillagePalette.LEAF, 8L);
        checkLots(helper, plan, plan.kind.wall - 4);
        for (int x = -3; x <= 3; x++) {
            helper.assertTrue(plan.paving(x, 30) == Paving.STREET, "a main street 7 wide (x = " + x + ")");
        }
        helper.assertTrue(plan.streets.stream().anyMatch(VillagePlan.Street::festival), "the main street is a festival street");
        helper.assertTrue(props(plan, PropType.LANTERN_LINE) >= 6, "lanterns strung across it: " + props(plan, PropType.LANTERN_LINE));
        helper.assertTrue(props(plan, PropType.STREET_GATE) == 2, "an arched gate at each end");
        long shopfronts = plan.lots.stream().filter(l -> Math.abs(l.cx()) < 14 && (l.type() == LotType.SHOP || l.type() == LotType.RESTAURANT)).count();
        helper.assertTrue(shopfronts >= 8, "shops along the main street: " + shopfronts);
        helper.assertTrue(plan.lots.stream().filter(l -> Math.abs(l.cx()) < 14 && l.type() == LotType.HOUSE).allMatch(l -> l.floors() == 2),
                "two-storey fronts");
        for (LotType type : new LotType[] {LotType.INN, LotType.CHIEF_HOUSE, LotType.SHRINE, LotType.TRAINING_GROUND, LotType.YARD}) {
            helper.assertTrue(plan.first(type) != null, "a " + type);
        }
        helper.assertTrue(plan.gates.size() == 4, "four gates");
        helper.succeed();
    }

    /** Everything a hidden village's plan must get right. */
    private static void checkCapital(GameTestHelper helper, VillagePlan plan, boolean fan) {
        String name = plan.palette + "/" + plan.seed;
        int wall = plan.kind.wall;
        int limit = wall - (fan ? 8 : 14);
        checkLots(helper, plan, limit);
        helper.assertTrue(plan.lots.size() >= 400, name + ": a big, dense village: " + plan.lots.size() + " lots");
        long buildings = plan.lots.stream().filter(l -> switch (l.type()) {
            case HOUSE, ROUND_HOUSE, APARTMENT, ROUND_TOWER, SHOP, RESTAURANT, STALL, WAREHOUSE -> true;
            default -> false;
        }).count();
        helper.assertTrue(buildings >= 250, name + ": mostly buildings: " + buildings);

        // The landmarks, at least as big as they need to be (front width × depth).
        Lot tower = plan.first(LotType.KAGE_TOWER);
        helper.assertTrue(tower != null && tower.width() == 61 && tower.depth() == 45 && tower.cx() == plan.towerX && tower.cz() == plan.towerZ,
                name + ": the Kage residence's lot is 61×45 round the tower");
        expectLandmark(helper, plan, LotType.ACADEMY, 45, 35);
        expectLandmark(helper, plan, LotType.HOSPITAL, 41, 31);
        expectLandmark(helper, plan, LotType.CLAN_COMPOUND, 61, 51);
        expectLandmark(helper, plan, LotType.SHRINE, 35, 41);
        expectLandmark(helper, plan, LotType.ONSEN, 35, 31);
        expectLandmark(helper, plan, LotType.CEMETERY, 25, 31);
        expectLandmark(helper, plan, LotType.JONIN_STATION, 15, 15);
        expectLandmark(helper, plan, LotType.GIANT_TREE, 24, 24);
        for (LotType type : new LotType[] {LotType.RAMEN_SHOP, LotType.GUARD_POST, LotType.YARD}) {
            helper.assertTrue(plan.first(type) != null, name + ": a " + type);
        }
        List<Lot> glades = plan.lots.stream().filter(l -> l.type() == LotType.TRAINING_GROUND).toList();
        helper.assertTrue(glades.size() >= 2 && glades.stream().allMatch(l -> frameW(l) >= 40 && frameD(l) >= 28), name + ": training glades");
        helper.assertTrue("uchiha".equals(plan.compoundClan) || "hyuga".equals(plan.compoundClan), name + ": a clan lives in the compound");

        // A low skyline: houses and shops up to 3 storeys; a few apartments and round towers up to 5, near the plaza.
        int tall = 0;
        for (Lot lot : plan.lots) {
            switch (lot.type()) {
                case HOUSE, SHOP, RESTAURANT, ROUND_HOUSE, WAREHOUSE -> helper.assertTrue(lot.floors() >= 1 && lot.floors() <= 3, name + ": " + lot + " is low");
                case APARTMENT, ROUND_TOWER -> {
                    tall++;
                    helper.assertTrue(lot.floors() <= 5, name + ": " + lot + " has at most 5 storeys");
                    helper.assertTrue(Math.hypot(lot.cx() - plan.towerX, lot.cz() - plan.towerZ) <= 130, name + ": " + lot + " stands near the plaza");
                }
                default -> {
                }
            }
        }
        helper.assertTrue(tall >= 4 && tall * 10 <= plan.lots.size(), name + ": a few tall buildings: " + tall);
        // Spires: 8–12, small, well apart.
        List<Lot> spires = plan.lots.stream().filter(l -> l.type() == LotType.SPIRE).toList();
        helper.assertTrue(spires.size() >= 8 && spires.size() <= 12, name + ": 8–12 spires, got " + spires.size());
        for (Lot a : spires) {
            helper.assertTrue(a.width() >= 5 && a.width() <= 7 && a.depth() >= 5 && a.depth() <= 7 && a.style() >= 0 && a.style() <= 2, name + ": " + a);
            for (Lot b : spires) {
                helper.assertTrue(a == b || Math.hypot(a.cx() - b.cx(), a.cz() - b.cz()) >= 25, name + ": spires stand apart");
            }
        }
        // Small plots near the plaza.
        long small = plan.lots.stream().filter(l -> l.type() == LotType.HOUSE && Math.hypot(l.cx() - plan.towerX, l.cz() - plan.towerZ) < 85
                && Math.min(l.width(), l.depth()) <= 8).count();
        helper.assertTrue(small >= 40, name + ": small party-wall houses in the core: " + small);
        // Street neighbours wear different roofs.
        int pairs = 0, differ = 0;
        for (int i = 0; i < plan.lots.size(); i++) {
            Lot a = plan.lots.get(i);
            if (!building(a)) {
                continue;
            }
            for (int j = i + 1; j < plan.lots.size(); j++) {
                Lot b = plan.lots.get(j);
                if (building(b) && a.facing() == b.facing() && sideBySide(a, b)) {
                    pairs++;
                    if (plan.palette.colourway(a.colour()).roofFlat() != plan.palette.colourway(b.colour()).roofFlat()) {
                        differ++;
                    }
                }
            }
        }
        helper.assertTrue(pairs >= 100 && differ * 4 >= pairs * 3, name + ": neighbours' roofs differ: " + differ + " of " + pairs);
        // Wards.
        Set<VillagePlan.Ward> wards = plan.lots.stream().map(l -> plan.ward(l.cx(), l.cz())).collect(Collectors.toCollection(() -> EnumSet.noneOf(VillagePlan.Ward.class)));
        helper.assertTrue(wards.size() >= 6, name + ": wards " + wards);

        // Gates, the river, the rock.
        helper.assertTrue(plan.gates.size() == (fan ? 3 : 1), name + ": gates " + plan.gates.size());
        for (int[] gate : plan.gates) {
            helper.assertTrue(Math.abs(Math.hypot(gate[0], gate[1]) - wall) <= 2, name + ": the gate is in the wall");
            helper.assertTrue(gate[2] == 13 || gate[2] == 9, name + ": gate width " + gate[2]);
            double len = Math.hypot(gate[0], gate[1]);
            int ix = (int) Math.round(gate[0] - gate[0] / len * 4), iz = (int) Math.round(gate[1] - gate[1] / len * 4);
            helper.assertTrue(plan.isRoad(ix, iz), name + ": an avenue runs through the gate at " + gate[0] + ", " + gate[1]);
        }
        helper.assertTrue(plan.gates.get(0)[0] == 0 && plan.gates.get(0)[1] == wall && plan.gates.get(0)[2] == (fan ? 13 : 9), name + ": the main gate");
        if (fan) {
            helper.assertTrue(plan.gates.stream().filter(g -> g[2] == 9).allMatch(g -> g[1] == plan.crossZ), name + ": side gates at crossZ");
            helper.assertTrue(plan.hasRiver(), name + ": a river");
            int water = 0;
            for (int x = -wall; x <= wall; x++) {
                for (int z = -wall; z <= wall; z++) {
                    if (plan.isWater(x, z)) {
                        water++;
                        helper.assertTrue(Math.hypot(x - plan.towerX, z - plan.towerZ) > 60, name + ": the river keeps clear of the plaza");
                    }
                }
            }
            helper.assertTrue(water >= 1500, name + ": a real river: " + water + " blocks of water");
            helper.assertTrue(plan.bridges.stream().anyMatch(VillagePlan.Bridge::arched), name + ": an arched bridge");
            helper.assertTrue(!plan.landings.isEmpty(), name + ": steps down to the water");
            Lot compound = plan.first(LotType.CLAN_COMPOUND);
            boolean riverside = plan.river.stream().anyMatch(p -> p[0] >= compound.x1() - 12 && p[0] <= compound.x2() + 12
                    && p[1] >= compound.z1() - 12 && p[1] <= compound.z2() + 12);
            helper.assertTrue(riverside, name + ": the clan district is by the river");
            helper.assertTrue(plan.towerX == 0 && plan.towerZ < -150 && plan.rockZ <= plan.towerZ - 30, name + ": the residence is right under the rock");
            for (Lot lot : plan.lots) {
                helper.assertFalse(lot.z1() <= plan.rockZ + 1 && Math.abs(lot.cx()) <= plan.rockHalf, name + ": " + lot + " is in the rock");
            }
            // The avenues fan out from the plaza.
            for (int bearing : new int[] {0, 28, -28, 55, -55, 80, -80}) {
                double a = Math.toRadians(bearing);
                int x = plan.towerX + (int) Math.round(Math.sin(a) * 60), z = plan.towerZ + (int) Math.round(Math.cos(a) * 60);
                helper.assertTrue(plan.paving(x, z) == Paving.AVENUE, name + ": an avenue at " + bearing + "°");
            }
        } else {
            helper.assertFalse(plan.hasRiver(), name + ": no river in the desert");
        }

        // Street furniture.
        helper.assertTrue(props(plan, PropType.LAMP_POST) >= 30, name + ": street lamps: " + props(plan, PropType.LAMP_POST));
        helper.assertTrue(plan.props.stream().filter(p -> p.type() == PropType.LAMP_POST).allMatch(p -> plan.paving(p.x(), p.z()) == Paving.AVENUE),
                name + ": lamps stand on the avenues");
        helper.assertTrue(props(plan, PropType.POLE) >= 30 && plan.wires.size() >= 40, name + ": poles and wires");
        helper.assertTrue(plan.wires.stream().anyMatch(w -> w.y() != w.y2()), name + ": service drops to the houses");
        helper.assertTrue(props(plan, PropType.LANTERN_LINE) >= 8 && props(plan, PropType.STREET_GATE) == 2, name + ": the lantern street");
        helper.assertTrue(props(plan, PropType.NOTICE_BOARD) >= 2 && props(plan, PropType.BENCH) >= 4 && props(plan, PropType.BARRELS) >= 5,
                name + ": benches, boards and barrels");
        helper.assertTrue(plan.props.stream().noneMatch(p -> p.type() == PropType.POLE
                && Math.hypot(p.x() - plan.towerX, p.z() - plan.towerZ) < 50), name + ": no wires over the plaza");

        // The people.
        Map<Role, Long> people = plan.spawns.stream().collect(Collectors.groupingBy(VillagePlan.Spawn::role, Collectors.counting()));
        helper.assertTrue(plan.spawns.size() <= 145, name + ": at most ~140 residents: " + plan.spawns.size());
        helper.assertTrue(plan.spawns.stream().anyMatch(s -> s.role() == Role.KAGE && s.x() == plan.towerX && s.z() == plan.towerZ
                && s.y() == VillageBuilder.TOWER_HEIGHT + 1), name + ": the Kage in the tower");
        helper.assertTrue(people.getOrDefault(Role.GUARD, 0L) >= 2L * plan.gates.size() + 2, name + ": guards " + people);
        helper.assertTrue(people.getOrDefault(Role.PATROL, 0L) >= 5, name + ": patrols " + people);
        helper.assertTrue(people.getOrDefault(Role.RUNNER, 0L) >= 6, name + ": rooftop runners " + people);
        helper.assertTrue(people.getOrDefault(Role.MERCHANT, 0L) >= 20, name + ": shopkeepers " + people);
        helper.assertTrue(people.getOrDefault(Role.CIVILIAN, 0L) >= 30, name + ": villagers " + people);
        helper.assertTrue(people.getOrDefault(Role.CHILD, 0L) >= 8, name + ": children " + people);
        helper.assertTrue(people.getOrDefault(Role.TRAINEE, 0L) >= 6 && people.getOrDefault(Role.CLAN_NINJA, 0L) >= 5, name + ": ninjas " + people);
        helper.assertTrue(people.getOrDefault(Role.CAT, 0L) >= 3, name + ": cats " + people);
        for (VillagePlan.Spawn spawn : plan.spawns) {
            if (spawn.role() == Role.RUNNER) {
                helper.assertTrue(spawn.route().size() >= 3, name + ": a runner goes somewhere");
                for (int[] point : spawn.route()) {
                    helper.assertTrue(plan.lots.stream().anyMatch(l -> l.roofTop() == point[1] && l.cx() == point[0] && l.cz() == point[2]),
                            name + ": runners hop between flat roofs");
                }
            }
        }
        Noirm.LOGGER.info("Village layout {}: {} lots ({} buildings, {} tall, {} spires), {} props, {} wires, {} bridges, {} residents, wards {}",
                name, plan.lots.size(), buildings, tall, spires.size(), plan.props.size(), plan.wires.size(), plan.bridges.size(), plan.spawns.size(), wards);
    }

    /** No overlaps, everything inside the limit, nothing on a street or in the river. */
    private static void checkLots(GameTestHelper helper, VillagePlan plan, int limit) {
        List<Lot> lots = plan.lots;
        for (int i = 0; i < lots.size(); i++) {
            Lot a = lots.get(i);
            for (int[] corner : new int[][] {{a.x1(), a.z1()}, {a.x1(), a.z2()}, {a.x2(), a.z1()}, {a.x2(), a.z2()}}) {
                helper.assertTrue(corner[0] * corner[0] + corner[1] * corner[1] <= limit * limit, a + " is inside the wall");
            }
            for (int j = i + 1; j < lots.size(); j++) {
                Lot b = lots.get(j);
                helper.assertFalse(a.overlaps(b.x1(), b.z1(), b.x2(), b.z2(), 0), a + " overlaps " + b);
            }
            boolean tower = a.type() == LotType.KAGE_TOWER; // stands in its plaza
            for (int x = a.x1(); x <= a.x2(); x++) {
                for (int z = a.z1(); z <= a.z2(); z++) {
                    helper.assertFalse(plan.isWater(x, z), a + " is in the river");
                    helper.assertFalse(!tower && plan.isRoad(x, z), a + " sits on a street at " + x + ", " + z);
                }
            }
        }
    }

    private static void expectLandmark(GameTestHelper helper, VillagePlan plan, LotType type, int frontW, int depth) {
        Lot lot = plan.first(type);
        helper.assertTrue(lot != null, plan.palette + ": a " + type);
        helper.assertTrue(frameW(lot) >= frontW && frameD(lot) >= depth, plan.palette + ": " + lot + " is at least " + frontW + "×" + depth);
    }

    private static int frameW(Lot lot) {
        return lot.facing().getAxis() == Direction.Axis.X ? lot.depth() : lot.width();
    }

    private static int frameD(Lot lot) {
        return lot.facing().getAxis() == Direction.Axis.X ? lot.width() : lot.depth();
    }

    private static boolean building(Lot lot) {
        return switch (lot.type()) {
            case HOUSE, ROUND_HOUSE, SHOP, RESTAURANT, APARTMENT, ROUND_TOWER, WAREHOUSE, STALL -> true;
            default -> false;
        };
    }

    /** Two lots next to each other along their shared street (touching or a garden strip apart, fronts in line). */
    private static boolean sideBySide(Lot a, Lot b) {
        if (a.facing().getAxis() == Direction.Axis.Z) {
            boolean frontsInLine = a.facing() == Direction.SOUTH ? Math.abs(a.z2() - b.z2()) <= 2 : Math.abs(a.z1() - b.z1()) <= 2;
            int gap = Math.max(b.x1() - a.x2(), a.x1() - b.x2()) - 1;
            return frontsInLine && gap >= 0 && gap <= 2;
        }
        boolean frontsInLine = a.facing() == Direction.EAST ? Math.abs(a.x2() - b.x2()) <= 2 : Math.abs(a.x1() - b.x1()) <= 2;
        int gap = Math.max(b.z1() - a.z2(), a.z1() - b.z2()) - 1;
        return frontsInLine && gap >= 0 && gap <= 2;
    }

    private static long props(VillagePlan plan, PropType type) {
        return plan.props.stream().filter(p -> p.type() == type).count();
    }

    /** A hash of everything in a plan: lots, streets, water, props, wires and people. */
    private static long fingerprint(VillagePlan plan) {
        long h = plan.lots.hashCode() * 31L + plan.props.hashCode();
        h = h * 31 + plan.wires.hashCode();
        for (VillagePlan.Spawn s : plan.spawns) {
            h = h * 31 + s.role().ordinal() * 7919L + s.x() * 31L + s.z();
        }
        int r = plan.kind.radius;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                Paving paving = plan.paving(x, z);
                h = h * 31 + (paving == null ? 0 : paving.ordinal() + 1) * 2 + (plan.isWater(x, z) ? 1 : 0);
            }
        }
        return h;
    }

    // ------------------------------------------------------------ building slices

    /** The river where a street crosses it: water between stone banks, and a red arched bridge with lanterns. */
    @GameTest(template = "empty", batch = "noirm_layout_river", timeoutTicks = 60)
    public static void riverAndBridgeAreBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = 1234L;
        VillagePlan plan = VillagePlan.of(VillageKind.CAPITAL, VillagePalette.LEAF, seed);
        VillagePlan.Bridge bridge = plan.bridges.stream().filter(VillagePlan.Bridge::arched).findFirst().orElse(null);
        helper.assertTrue(bridge != null, "an arched bridge to build");
        BlockPos centre = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos origin = centre.offset(-bridge.x(), 0, -bridge.z());
        int h = bridge.length() / 2;
        BoundingBox area = new BoundingBox(centre.getX() - h - 3, centre.getY() - 8, centre.getZ() - h - 3,
                centre.getX() + h + 3, centre.getY() + 8, centre.getZ() + h + 3);
        Snapshot snapshot = new Snapshot(level, area);
        VillageBuilder.build(level, area, VillageKind.CAPITAL, VillagePalette.LEAF, origin, Rotation.NONE, seed, 8);
        try {
            boolean alongX = bridge.axis() == Direction.Axis.X;
            BlockPos along = alongX ? new BlockPos(1, 0, 0) : new BlockPos(0, 0, 1);
            BlockPos across = alongX ? new BlockPos(0, 0, 1) : new BlockPos(1, 0, 0);
            expect(helper, level, centre.offset(0, -2, 0), Blocks.WATER, "water under the bridge");
            expect(helper, level, centre.offset(0, 1, 0), Blocks.MANGROVE_PLANKS, "a deck two blocks over the water");
            expect(helper, level, centre.offset(0, 0, 0), Blocks.AIR, "with room beneath");
            expect(helper, level, centre.offset(along.multiply(h)), Blocks.MANGROVE_STAIRS, "steps up at one end");
            expect(helper, level, centre.offset(along.multiply(-h)), Blocks.MANGROVE_STAIRS, "and the other");
            expect(helper, level, centre.offset(across.multiply(2)).above(2), Blocks.MANGROVE_FENCE, "a red rail");
            expect(helper, level, centre.offset(along.multiply(h)).offset(across.multiply(2)).above(2), Blocks.LANTERN, "lanterns on the end posts");
            // A stone bank beside the water somewhere in the slice.
            boolean bank = false;
            for (int dx = -h; dx <= h && !bank; dx++) {
                for (int dz = -h; dz <= h && !bank; dz++) {
                    int x = bridge.x() + dx, z = bridge.z() + dz;
                    if (!plan.isWater(x, z) && (plan.isWater(x + 1, z) || plan.isWater(x - 1, z) || plan.isWater(x, z + 1) || plan.isWater(x, z - 1))) {
                        BlockState stone = level.getBlockState(centre.offset(dx, -3, dz));
                        helper.assertTrue(stone.is(Blocks.STONE_BRICKS) || stone.is(Blocks.MOSSY_STONE_BRICKS) || stone.is(Blocks.CRACKED_STONE_BRICKS),
                                "a stone bank, found " + stone);
                        bank = true;
                    }
                }
            }
            helper.assertTrue(bank, "the river has banks");
        } finally {
            snapshot.restore();
        }
        helper.succeed();
    }

    /** An avenue street lamp lights itself at night: a redstone lamp under an inverted daylight detector. */
    @GameTest(template = "empty", batch = "noirm_layout_lamp", timeoutTicks = 120)
    public static void streetLampsLightAtNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long time = level.getDayTime();
        long seed = 1234L;
        VillagePlan plan = VillagePlan.of(VillageKind.CAPITAL, VillagePalette.LEAF, seed);
        VillagePlan.Prop lamp = plan.props.stream().filter(p -> p.type() == PropType.LAMP_POST
                && Math.hypot(p.x(), p.z()) < plan.kind.wall - 20).findFirst().orElseThrow();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos origin = at.offset(-lamp.x(), 0, -lamp.z());
        BoundingBox area = new BoundingBox(at.getX() - 2, at.getY() - 3, at.getZ() - 2, at.getX() + 2, at.getY() + 6, at.getZ() + 2);
        Snapshot snapshot = new Snapshot(level, area);
        VillageBuilder.build(level, area, VillageKind.CAPITAL, VillagePalette.LEAF, origin, Rotation.NONE, seed, 6);
        expect(helper, level, at.above(1), Blocks.STONE_BRICK_WALL, "a lamp post");
        expect(helper, level, at.above(3), Blocks.REDSTONE_LAMP, "a lamp");
        BlockState detector = level.getBlockState(at.above(4));
        helper.assertTrue(detector.is(Blocks.DAYLIGHT_DETECTOR) && detector.getValue(DaylightDetectorBlock.INVERTED), "under an inverted daylight detector");
        expect(helper, level, at.below(), Blocks.SMOOTH_STONE, "standing on the avenue's kerb");
        level.setDayTime(18000);
        helper.runAfterDelay(50, () -> {
            boolean lit = level.getBlockState(at.above(3)).getValue(RedstoneLampBlock.LIT);
            level.setDayTime(time);
            snapshot.restore();
            helper.assertTrue(lit, "the lamp lights up at night");
            helper.succeed();
        });
    }

    /** The lantern street: paper lanterns strung across it, an arched gate at its end. */
    @GameTest(template = "empty", batch = "noirm_layout_festival", timeoutTicks = 60)
    public static void lanternStreetIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = 1234L;
        VillagePlan plan = VillagePlan.of(VillageKind.CAPITAL, VillagePalette.LEAF, seed);
        VillagePlan.Prop line = plan.props.stream().filter(p -> p.type() == PropType.LANTERN_LINE).findFirst().orElseThrow();
        VillagePlan.Prop gate = plan.props.stream().filter(p -> p.type() == PropType.STREET_GATE).findFirst().orElseThrow();
        for (VillagePlan.Prop prop : new VillagePlan.Prop[] {line, gate}) {
            BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockPos origin = at.offset(-prop.x(), 0, -prop.z());
            BoundingBox area = new BoundingBox(at.getX() - 6, at.getY() - 2, at.getZ() - 6, at.getX() + 6, at.getY() + 9, at.getZ() + 6);
            Snapshot snapshot = new Snapshot(level, area);
            VillageBuilder.build(level, area, VillageKind.CAPITAL, VillagePalette.LEAF, origin, Rotation.NONE, seed, 9);
            try {
                if (prop == line) {
                    expect(helper, level, at.above(5), Blocks.CHAIN, "a line across the street");
                    BlockState lantern = level.getBlockState(at.above(4));
                    helper.assertTrue(lantern.is(Blocks.SHROOMLIGHT) || lantern.is(Blocks.PEARLESCENT_FROGLIGHT) || lantern.is(Blocks.OCHRE_FROGLIGHT),
                            "with paper lanterns hanging from it, found " + lantern);
                } else {
                    Direction across = prop.facing().getClockWise();
                    BlockPos post = at.relative(across, prop.style());
                    expect(helper, level, post.above(3), Blocks.RED_TERRACOTTA, "the gate's red posts");
                    expect(helper, level, at.above(6), Blocks.RED_TERRACOTTA, "its beam");
                    BlockState board = level.getBlockState(at.above(4));
                    helper.assertTrue(board.is(Blocks.WHITE_CONCRETE) || board.is(VillagePalette.LEAF.paint), "a signboard, found " + board);
                    expect(helper, level, at.above(1), Blocks.AIR, "and you can walk under it");
                }
            } finally {
                snapshot.restore();
            }
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
            level.getEntitiesOfClass(LivingEntity.class, AABB.of(area).inflate(2), e -> !(e instanceof Player)).forEach(LivingEntity::discard);
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

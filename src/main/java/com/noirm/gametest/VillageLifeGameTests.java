package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.VillageMerchant;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.village.BuiltVillages;
import com.noirm.world.village.VillageBuilder;
import com.noirm.world.village.VillageKind;
import com.noirm.world.village.VillagePalette;
import com.noirm.world.village.VillagePlan;
import com.noirm.world.village.VillagePlan.LotType;
import com.noirm.world.village.VillageResidents;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Village life: neighbours stop to chat (a speech bubble over their head), and the village's
 * ninjas see off monsters that wander in (but leave creepers be, and the civilians run).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageLifeGameTests {
    private VillageLifeGameTests() {}

    /** Two villagers chat: the one speaking gets a speech bubble, and it's gone a couple of seconds later. */
    @GameTest(template = "empty", batch = "noirm_village_chat", timeoutTicks = 120)
    public static void villagersChat(GameTestHelper helper) {
        VillageMerchant a = helper.spawn(NoirmEntities.VILLAGE_MERCHANT.get(), 0.5f, 1f, 0.5f);
        VillageMerchant b = helper.spawn(NoirmEntities.VILLAGE_MERCHANT.get(), 1.5f, 1f, 0.5f);
        BlockPos home = helper.absolutePos(new BlockPos(0, 1, 0));
        a.setup(VillageMerchant.Role.CIVILIAN, 0, home, home, 3);
        b.setup(VillageMerchant.Role.CIVILIAN, 0, home, home, 3);
        a.startChat(b, 60, true);
        b.startChat(a, 60, false);
        helper.assertTrue(a.chatPartner() == b && b.chatPartner() == a && a.chatStarter() && !b.chatStarter(), "they stop to talk");
        // (End the chat again, so the test's timing isn't up to their own turns; a chat leaves a cooldown.)
        a.endChat();
        b.endChat();
        helper.assertTrue(a.chatPartner() == null && a.chatCooldown() > 0, "and part, not to chat again straight away");
        a.sayLine(20);
        helper.assertTrue(a.getPassengers().stream().anyMatch(p -> p instanceof Display.TextDisplay && p.getTags().contains("noirm_bubble")),
                "a speech bubble over the speaker's head");
        helper.assertTrue(b.getPassengers().isEmpty(), "the listener is quiet");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(a.getPassengers().isEmpty(), "the bubble goes away");
            ServerLevel level = helper.getLevel();
            helper.assertTrue(level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(home).inflate(4)).isEmpty(), "and leaves nothing behind");
            a.discard();
            b.discard();
            helper.succeed();
        });
    }

    /** A resident ninja goes after a zombie in the village, not after a creeper. */
    @GameTest(template = "empty", batch = "noirm_village_defence", timeoutTicks = 100)
    public static void residentsFightMonsters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos home = helper.absolutePos(new BlockPos(1, 1, 1));
        SparringNinja guard = VillageResidents.ninja(level, home, home, 5, 10, 10);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        Creeper creeper = EntityType.CREEPER.create(level);
        zombie.moveTo(home.getX() + 3.5, home.getY(), home.getZ() + 0.5);
        creeper.moveTo(home.getX() - 3.5, home.getY(), home.getZ() + 0.5);
        zombie.setNoAi(true);
        creeper.setNoAi(true);
        level.addFreshEntity(zombie);
        level.addFreshEntity(creeper);
        helper.assertTrue(guard.wouldDefendAgainst(zombie), "a resident fights a zombie");
        helper.assertFalse(guard.wouldDefendAgainst(creeper), "but not a creeper");
        SparringNinja wanderer = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 2.5f);
        helper.assertFalse(wanderer.wouldDefendAgainst(zombie), "a ninja from the wild doesn't care");
        helper.succeedWhen(() -> {
            helper.assertTrue(guard.getTarget() == zombie, "the guard goes after the zombie");
            level.getEntitiesOfClass(LivingEntity.class, new AABB(home).inflate(8), e -> !(e instanceof net.minecraft.world.entity.player.Player))
                    .forEach(LivingEntity::discard);
        });
    }

    // ------------------------------------------------------------ seats, looks

    /** A villager sits on a stair (riding an invisible seat), and the seat goes when they get up. */
    @GameTest(template = "empty", batch = "noirm_village_seat", timeoutTicks = 60)
    public static void villagersSitDown(GameTestHelper helper) {
        BlockPos stair = new BlockPos(1, 1, 1);
        helper.setBlock(stair, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        VillageMerchant villager = helper.spawn(NoirmEntities.VILLAGE_MERCHANT.get(), 1.5f, 1f, 2.5f);
        BlockPos home = helper.absolutePos(stair);
        villager.setup(VillageMerchant.Role.CIVILIAN, 0, home, home, 3);
        var seat = com.noirm.entity.VillagerSitGoal.sit(villager, helper.absolutePos(stair));
        helper.assertTrue(seat != null && villager.getVehicle() == seat, "the villager sits on the stair");
        helper.assertTrue(Math.abs(villager.getYRot() - Direction.SOUTH.toYRot()) < 1, "facing out from its back");
        helper.assertTrue(villager.getY() < helper.absolutePos(stair).getY() + 0.1, "low, on the seat");
        villager.stopRiding();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!seat.isAlive(), "the empty seat goes away");
            villager.discard();
            helper.succeed();
        });
    }

    /** Each biome gets its village look: swamps the Mist, badlands the Stone, meadows the Cloud… */
    @GameTest(template = "empty", batch = "noirm_village_plan")
    public static void biomesPickTheLook(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>, VillagePalette> expected = java.util.Map.of(
                net.minecraft.world.level.biome.Biomes.SWAMP, VillagePalette.MIST,
                net.minecraft.world.level.biome.Biomes.DARK_FOREST, VillagePalette.MIST,
                net.minecraft.world.level.biome.Biomes.BADLANDS, VillagePalette.STONE,
                net.minecraft.world.level.biome.Biomes.MEADOW, VillagePalette.CLOUD,
                net.minecraft.world.level.biome.Biomes.WINDSWEPT_HILLS, VillagePalette.CLOUD,
                net.minecraft.world.level.biome.Biomes.DESERT, VillagePalette.SAND,
                net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS, VillagePalette.SNOW,
                net.minecraft.world.level.biome.Biomes.PLAINS, VillagePalette.LEAF);
        expected.forEach((key, look) -> helper.assertTrue(VillagePalette.forBiome(biomes.getHolderOrThrow(key)) == look,
                key.location() + " → " + look + ", got " + VillagePalette.forBiome(biomes.getHolderOrThrow(key))));
        helper.succeed();
    }

    /** A village built with /noirm village is remembered (and survives a save), so its mist and spawn rules work too. */
    @GameTest(template = "empty", batch = "noirm_village_plan")
    public static void builtVillagesAreRemembered(GameTestHelper helper) {
        BuiltVillages villages = new BuiltVillages();
        BlockPos centre = new BlockPos(5000, 70, -3000);
        villages.add(centre, VillageKind.CAPITAL, VillagePalette.MIST);
        BuiltVillages loaded = BuiltVillages.load(villages.save(new net.minecraft.nbt.CompoundTag()));
        BuiltVillages.Entry inside = loaded.at(centre.offset(150, 10, -120));
        helper.assertTrue(inside != null && inside.palette() == VillagePalette.MIST, "a street inside the Mist village is in it, got " + inside);
        helper.assertTrue(loaded.at(centre.offset(VillageKind.CAPITAL.radius + 20, 0, 0)) == null, "past the edge is outside");
        helper.assertTrue(loaded.at(centre.above(120)) == null, "high in the sky is outside");
        helper.succeed();
    }

    /** The new villages' signature buildings: the Cloud village's rock spire and domes, the Stone village's tower, the Mist village's drum. */
    @GameTest(template = "empty", batch = "noirm_village_looks", timeoutTicks = 100)
    public static void villageLooksAreBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int style = 0; style < 3; style++) {
            int s = style;
            build(helper, level, origin, VillagePalette.CLOUD, new VillagePlan.Lot(LotType.ROCK_SPIRE, 0, 0, 12, 12, Direction.SOUTH, 0, style, 31L + style, 0),
                    60, () -> {
                        helper.assertTrue(count(level, origin, 3, 20, 3, 9, 20, 9, st -> !st.isAir()) >= 20, "rock spire " + s + " rises");
                        helper.assertTrue(count(level, origin, 0, 0, -2, 12, 40, 12, st -> st.is(Blocks.LADDER)) >= 25, "with a ladder up it");
                    });
        }
        build(helper, level, origin, VillagePalette.CLOUD, new VillagePlan.Lot(LotType.ROUND_HOUSE, 0, 0, 9, 10, Direction.SOUTH, 1, 0, 41L, 0), 16,
                () -> helper.assertTrue(count(level, origin, 0, 4, 0, 9, 9, 10, st -> st.is(VillagePalette.CLOUD.colourway(0).roofFlat())) >= 12,
                        "a Cloud round house wears a dome"));
        build(helper, level, origin, VillagePalette.STONE, new VillagePlan.Lot(LotType.ROUND_TOWER, 0, 0, 10, 11, Direction.SOUTH, 5, 0, 42L, 0), 30, () -> {
            int low = count(level, origin, -1, 1, -1, 11, 1, 12, st -> !st.isAir());
            int high = count(level, origin, -1, 17, -1, 11, 17, 12, st -> !st.isAir());
            helper.assertTrue(high < low, "the Stone tower narrows as it rises: " + low + " → " + high);
        });
        build(helper, level, origin, VillagePalette.MIST, new VillagePlan.Lot(LotType.ROUND_HOUSE, 0, 0, 9, 10, Direction.SOUTH, 2, 0, 43L, 0), 16,
                () -> helper.assertTrue(count(level, origin, 0, 6, 0, 9, 12, 10, st -> st.is(Blocks.MOSS_CARPET) || st.is(Blocks.MOSS_BLOCK)) >= 6,
                        "a Mist drum is moss-topped"));
        // Each Kage tower's crown.
        for (VillagePalette look : new VillagePalette[] {VillagePalette.MIST, VillagePalette.CLOUD, VillagePalette.STONE}) {
            BoundingBox area = new BoundingBox(origin.getX() - 16, origin.getY() - 2, origin.getZ() - 16, origin.getX() + 16, origin.getY() + 64,
                    origin.getZ() + 16);
            BlockState[] saved = save(level, area);
            try {
                for (BlockPos pos : BlockPos.betweenClosed(area.minX(), origin.getY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                VillageBuilder.buildKageTower(level, area, look, origin, false);
                int crown = count(level, origin, -9, VillageBuilder.TOWER_HEIGHT + 7, -9, 9, VillageBuilder.TOWER_HEIGHT + 22, 9, st -> !st.isAir());
                helper.assertTrue(crown >= 40, look + "'s Kage tower has its crown: " + crown);
            } finally {
                restore(level, area, saved);
            }
        }
        helper.succeed();
    }

    private static BlockState[] save(ServerLevel level, BoundingBox area) {
        BlockState[] saved = new BlockState[area.getXSpan() * area.getYSpan() * area.getZSpan()];
        int i = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            saved[i++] = level.getBlockState(pos);
        }
        return saved;
    }

    private static void restore(ServerLevel level, BoundingBox area, BlockState[] saved) {
        int i = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            BlockState old = saved[i++];
            if (level.getBlockState(pos) != old) {
                level.setBlock(pos, old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, AABB.of(area).inflate(2),
                e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
    }

    // ------------------------------------------------------------ the new buildings

    /**
     * Every new kind of lot builds something sensible (each drawn on its own and put back): all
     * eight kinds of yard, the giant tree, the three spires, the jōnin station, the cemetery, and a
     * house with its façade (skirting, sills and shutters, a pent eave).
     */
    @GameTest(template = "empty", batch = "noirm_village_buildings", timeoutTicks = 100)
    public static void newBuildingsAreBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        // Yards: a kitchen garden has crops on farmland, a koi pond water in the middle, a well water down its shaft.
        for (int style = 0; style < 8; style++) {
            int s = style;
            build(helper, level, origin, new VillagePlan.Lot(LotType.YARD, 0, 0, 9, 9, Direction.SOUTH, 0, style, 77L + style, 5), 0, 8, () -> {
                int built = count(level, origin, 0, -1, 0, 9, 4, 9, st -> !st.isAir() && !st.is(Blocks.STONE) && !st.is(Blocks.GRASS_BLOCK));
                helper.assertTrue(built > 20, "yard " + s + " has something in it: " + built);
                if (s == 0) {
                    helper.assertTrue(count(level, origin, 0, -1, 0, 9, -1, 9, st -> st.is(Blocks.FARMLAND)) > 20, "a kitchen garden of farmland");
                } else if (s == 4) {
                    expect(helper, level, origin.offset(4, -1, 4), Blocks.WATER, "a koi pond");
                } else if (s == 7) {
                    expect(helper, level, origin.offset(4, -2, 4), Blocks.WATER, "a well");
                }
            });
        }
        build(helper, level, origin, new VillagePlan.Lot(LotType.GIANT_TREE, 0, 0, 23, 23, Direction.SOUTH, 0, 0, 5L, 0), 0, 30, () -> {
            helper.assertTrue(count(level, origin, 11, 8, 11, 12, 12, 12, st -> st.is(net.minecraft.tags.BlockTags.LOGS)) >= 8, "a thick trunk");
            int leaves = count(level, origin, -4, 8, -4, 27, 30, 27, st -> st.is(net.minecraft.tags.BlockTags.LEAVES));
            java.util.Map<net.minecraft.world.level.block.Block, Integer> seen = new java.util.HashMap<>();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 8, -4), origin.offset(27, 30, 27))) {
                seen.merge(level.getBlockState(pos).getBlock(), 1, Integer::sum);
            }
            helper.assertTrue(leaves > 300, "a wide crown: " + leaves + " leaves; up there: " + seen);
        });
        for (int style = 0; style < 3; style++) {
            int s = style;
            build(helper, level, origin, new VillagePlan.Lot(LotType.SPIRE, 0, 0, 6, 6, Direction.SOUTH, 0, style, 9L + style, 3), 0, 30, () ->
                    helper.assertTrue(count(level, origin, -2, 13, -2, 8, 28, 8, st -> !st.isAir()) > 15, "spire " + s + " stands tall"));
        }
        build(helper, level, origin, new VillagePlan.Lot(LotType.JONIN_STATION, 0, 0, 14, 14, Direction.SOUTH, 1, 0, 11L, 2), 0, 14, () -> {
            helper.assertTrue(count(level, origin, -2, 5, -2, 16, 5, 16, st -> st.getBlock() instanceof StairBlock) > 20, "a round station under a flared eave");
            helper.assertTrue(count(level, origin, 0, 0, 0, 14, 0, 14, st -> st.is(Blocks.SMOKER) || st.is(Blocks.BARREL)) >= 2, "with a tea counter");
        });
        build(helper, level, origin, new VillagePlan.Lot(LotType.CEMETERY, 0, 0, 24, 30, Direction.SOUTH, 0, 0, 13L, 0), 0, 8, () -> {
            helper.assertTrue(count(level, origin, 0, 0, 0, 24, 0, 30, st -> st.is(Blocks.CAMPFIRE)) == 1, "a flame at the memorial");
            helper.assertTrue(count(level, origin, 0, 0, 0, 24, 0, 30, st -> st.getBlock() instanceof net.minecraft.world.level.block.WallBlock) > 20,
                    "rows of headstones");
        });
        build(helper, level, origin, new VillagePlan.Lot(LotType.HOUSE, 0, 0, 9, 11, Direction.SOUTH, 2, 1, 21L, 4), 0, 12, () -> {
            var colours = VillagePalette.LEAF.colourway(4);
            expect(helper, level, origin.offset(1, 4, 8), colours.wall(), "walls in the lot's colourway");
            expect(helper, level, origin.offset(3, 0, 1), colours.accent(), "a timber skirting");
            helper.assertTrue(count(level, origin, 0, 4, 10, 9, 6, 10, st -> st.getBlock() instanceof TrapDoorBlock) >= 2, "sills and shutters upstairs");
            helper.assertTrue(count(level, origin, 0, 3, 10, 9, 3, 10, st -> st.is(colours.roofStairs())) >= 5, "a pent eave between the floors");
        });
        helper.succeed();
    }

    private static void build(GameTestHelper helper, ServerLevel level, BlockPos origin, VillagePlan.Lot lot, int below, int above, Runnable checks) {
        build(helper, level, origin, VillagePalette.LEAF, lot, above, checks);
    }

    /** Builds one lot at {@code origin} in a village look, runs the checks, and puts everything back. */
    private static void build(GameTestHelper helper, ServerLevel level, BlockPos origin, VillagePalette palette, VillagePlan.Lot lot, int above,
                              Runnable checks) {
        BoundingBox area = new BoundingBox(origin.getX() + lot.x1() - 4, origin.getY() - 5, origin.getZ() + lot.z1() - 4,
                origin.getX() + lot.x2() + 4, origin.getY() + above, origin.getZ() + lot.z2() + 4);
        BlockState[] saved = new BlockState[area.getXSpan() * area.getYSpan() * area.getZSpan()];
        int i = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            saved[i++] = level.getBlockState(pos);
        }
        // A cleared site, like the village builder leaves: air above the ground, grass on it, stone below.
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            BlockState site = pos.getY() >= origin.getY() ? Blocks.AIR.defaultBlockState()
                    : pos.getY() == origin.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
            level.setBlock(pos, site, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        VillageBuilder.buildLot(level, area, palette, origin, lot);
        try {
            checks.run();
        } finally {
            i = 0;
            for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                BlockState old = saved[i++];
                if (level.getBlockState(pos) != old) {
                    level.setBlock(pos, old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, AABB.of(area).inflate(2),
                    e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
        }
    }

    private static int count(ServerLevel level, BlockPos origin, int x1, int y1, int z1, int x2, int y2, int z2,
                             java.util.function.Predicate<BlockState> what) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(x1, y1, z1), origin.offset(x2, y2, z2))) {
            if (what.test(level.getBlockState(pos))) {
                n++;
            }
        }
        return n;
    }

    private static void expect(GameTestHelper helper, ServerLevel level, BlockPos pos, Block block, String what) {
        BlockState state = level.getBlockState(pos);
        helper.assertTrue(state.is(block), what + ": expected " + block + " at " + pos + ", found " + state);
    }
}

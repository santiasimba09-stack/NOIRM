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
        var outpost = structures.get(Noirm.id("ninja_outpost"));
        helper.assertTrue(capital instanceof NinjaVillageStructure village && village.kind() == VillageKind.CAPITAL, "the hidden village is registered");
        helper.assertTrue(outpost instanceof NinjaVillageStructure village && village.kind() == VillageKind.OUTPOST, "the outpost is registered");
        var sets = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        helper.assertTrue(sets.containsKey(Noirm.id("hidden_villages")) && sets.containsKey(Noirm.id("ninja_outposts")), "and both have placement rules");
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
    }

    @GameTest(template = "empty", batch = "noirm_village_tower", timeoutTicks = 40)
    public static void kageTowerIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BoundingBox area = new BoundingBox(origin.getX() - 12, origin.getY() - 2, origin.getZ() - 12,
                origin.getX() + 12, origin.getY() + VillageBuilder.TOWER_HEIGHT + 14, origin.getZ() + 12);
        Snapshot snapshot = new Snapshot(level, area);
        VillageBuilder.buildKageTower(level, area, VillagePalette.LEAF, origin, true);
        try {
            int r = VillageBuilder.TOWER_RADIUS;
            int h = VillageBuilder.TOWER_HEIGHT;
            expect(helper, level, origin.offset(r, 1, 0), Blocks.RED_TERRACOTTA, "a red drum");
            expect(helper, level, origin.offset(-r, h - 1, 0), Blocks.RED_TERRACOTTA, "all the way up");
            expect(helper, level, origin.offset(0, 1, r), Blocks.AIR, "with a door in front");
            expect(helper, level, origin.offset(0, 3, -(r - 1)), Blocks.LADDER, "a ladder inside");
            expect(helper, level, origin.offset(0, h, r + 2), Blocks.OAK_PLANKS, "a balcony on top");
            expect(helper, level, origin.offset(0, h + 1, r + 3), Blocks.OAK_FENCE, "with a railing");
            // The fire symbol: ". # . # ." on its top row.
            expect(helper, level, origin.offset(-2, h - 4, r + 1), Blocks.WHITE_CONCRETE, "a white plaque");
            expect(helper, level, origin.offset(-1, h - 4, r + 1), Blocks.RED_CONCRETE, "painted with the village symbol");
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

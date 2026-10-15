package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.chakra.WaterWalking;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Water walking: the surface holds ninjas and players with chakra; no chakra, sneaking or
 * being beaten means you sink. Each test builds a small glass well, two blocks of water deep,
 * in the middle of its 3×3 area: the top water block is (1, 2, 1).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterWalkGameTests {
    /** Ninjas fall and sink here: keep them away from other tests. */
    private static final String BATCH = "noirm_water";
    private static final BlockPos SURFACE = new BlockPos(1, 2, 1);

    private WaterWalkGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void ninjaLandsAndStandsOnWater(GameTestHelper helper) {
        well(helper);
        // Dropped from 8 blocks above the water: lands on it, unhurt, and stays on top.
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 11f, 1.5f);
        helper.runAfterDelay(60, () -> {
            double y = ninja.getY() - helper.absolutePos(BlockPos.ZERO).getY();
            helper.assertTrue(y > 2.85 && y < 3.05, "the ninja should stand on the water surface, not at y=" + y);
            helper.assertFalse(ninja.isInWater(), "standing on the water, not in it");
            helper.assertTrue(ninja.getHealth() >= ninja.getMaxHealth() - 0.01f,
                    "landing on water doesn't hurt (" + ninja.getHealth() + "/" + ninja.getMaxHealth() + ")");
            ninja.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void beatenNinjaSinks(GameTestHelper helper) {
        well(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 3.2f, 1.5f);
        ninja.setYielded(java.util.UUID.randomUUID(), helper.getLevel().getGameTime() + 10_000);
        helper.assertFalse(ninja.canWaterWalk(), "a beaten ninja has no chakra control left");
        helper.runAfterDelay(60, () -> {
            double y = ninja.getY() - helper.absolutePos(BlockPos.ZERO).getY();
            helper.assertTrue(y < 2.7, "a beaten ninja sinks, but is at y=" + y);
            helper.assertTrue(ninja.isInWater(), "in the water");
            ninja.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void chakraHoldsAPlayerUp(GameTestHelper helper) {
        well(helper);
        Player player = playerAbove(helper, 3.0);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        data.setChakra(data.getMaxChakra());
        VoxelShape skin = surfaceFor(helper, player);
        helper.assertFalse(skin.isEmpty(), "with chakra, the water's surface holds you");
        double top = skin.max(Direction.Axis.Y);
        helper.assertTrue(top > 8 / 9.0 && top < 0.95, "the skin sits just above the visible water, not at " + top);

        data.setChakra(WaterWalking.MIN_CHAKRA - 0.5f);
        helper.assertTrue(surfaceFor(helper, player).isEmpty(), "no chakra: you sink");
        data.setChakra(data.getMaxChakra());

        player.setShiftKeyDown(true);
        helper.assertTrue(surfaceFor(helper, player).isEmpty(), "sneaking lets you sink or dive");
        player.setShiftKeyDown(false);

        player.getAbilities().flying = true;
        helper.assertTrue(surfaceFor(helper, player).isEmpty(), "flying players don't snag on water");
        player.getAbilities().flying = false;

        Player swimmer = playerAbove(helper, 2.4);
        ShinobiCapability.get(swimmer).orElseThrow().setChakra(100);
        helper.assertTrue(surfaceFor(helper, swimmer).isEmpty(), "under the surface you swim, never stuck in the skin");

        // Flowing water holds you too, at its own (lower) height.
        helper.setBlock(SURFACE, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
        Player wader = playerAbove(helper, 2.5);
        ShinobiCapability.get(wader).orElseThrow().setChakra(100);
        VoxelShape flowing = surfaceFor(helper, wader);
        helper.assertTrue(!flowing.isEmpty() && flowing.max(Direction.Axis.Y) < 0.6, "flowing water is walkable at its height");
        helper.setBlock(SURFACE, Blocks.WATER);

        // Bubble columns keep pushing and pulling.
        helper.setBlock(SURFACE, Blocks.BUBBLE_COLUMN);
        helper.assertTrue(surfaceFor(helper, player).isEmpty(), "bubble columns are left alone");
        helper.setBlock(SURFACE, Blocks.WATER);

        // Lava walking is off by default.
        BlockPos corner = new BlockPos(0, 2, 0);
        helper.setBlock(corner, Blocks.LAVA);
        BlockState lava = helper.getBlockState(corner);
        Vec3 aboveLava = helper.absoluteVec(new Vec3(0.5, 3.0, 0.5));
        player.moveTo(aboveLava.x, aboveLava.y, aboveLava.z);
        helper.assertTrue(lava.getCollisionShape(helper.getLevel(), helper.absolutePos(corner), CollisionContext.of(player)).isEmpty()
                || WaterWalking.LAVA_WALKING, "no lava walking unless it's switched on");
        helper.setBlock(corner, Blocks.GLASS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ninjasAreHeldUpToo(GameTestHelper helper) {
        well(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 3.0f, 1.5f);
        helper.assertTrue(ninja.canWaterWalk(), "ninjas can walk on water");
        BlockState water = helper.getBlockState(SURFACE);
        helper.assertFalse(water.getCollisionShape(helper.getLevel(), helper.absolutePos(SURFACE), CollisionContext.of(ninja)).isEmpty(),
                "the surface holds a ninja");
        ninja.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void standingOnWaterDrainsChakra(GameTestHelper helper) {
        well(helper);
        Player player = playerAbove(helper, 2 + 29 / 32.0);
        player.getAbilities().instabuild = false; // test players are creative, which costs nothing
        player.setOnGround(true);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        data.setChakra(data.getMaxChakra());
        helper.assertTrue(WaterWalking.isOnSurface(player), "the player is standing on the water");
        for (int i = 0; i < 40; i++) {
            WaterWalking.tick(player);
        }
        float spent = data.getMaxChakra() - data.getChakra();
        helper.assertTrue(Math.abs(spent - 40 * WaterWalking.DRAIN_STANDING) < 0.05f,
                "standing still on water for 2 s costs " + 40 * WaterWalking.DRAIN_STANDING + " chakra, not " + spent);
        data.setChakra(0.03f);
        WaterWalking.tick(player);
        helper.assertTrue(data.getChakra() == 0f && !WaterWalking.playerCanWalk(player, false), "run dry and you sink");
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    /** A stone floor, a glass ring and a 1×1 well of water two blocks deep in the middle. */
    private static void well(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, z), x == 1 && z == 1 ? Blocks.WATER : Blocks.GLASS);
                }
            }
        }
    }

    private static Player playerAbove(GameTestHelper helper, double y) {
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, y, 1.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return player;
    }

    private static VoxelShape surfaceFor(GameTestHelper helper, Player player) {
        return helper.getBlockState(SURFACE).getCollisionShape(helper.getLevel(), helper.absolutePos(SURFACE), CollisionContext.of(player));
    }
}

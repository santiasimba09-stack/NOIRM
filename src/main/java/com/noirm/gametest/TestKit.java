package com.noirm.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Small helpers shared by the game tests (statically imported). */
final class TestKit {
    private TestKit() {}

    /** Lays a 3×3 stone floor so fighters don't fall into the void. */
    static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * Lays a stone floor over {@code x0..x1} × {@code z0..z1} (test coordinates, y 0) with
     * {@code height} blocks of air above it. Use it for any floor bigger than the 3×3 arena: the
     * game-test world is a normal world and the tests stand about 60 blocks underground, so outside
     * the small space the framework clears round each test there is solid deepslate (or whatever an
     * earlier test left there). A mob put down there is buried and can't walk out.
     */
    static void openFloor(GameTestHelper helper, int x0, int z0, int x1, int z1, int height) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= height; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /** Turns {@code entity} to look straight at {@code other}. */
    static void face(LivingEntity entity, LivingEntity other) {
        float yaw = (float) (Mth.atan2(other.getZ() - entity.getZ(), other.getX() - entity.getX()) * Mth.RAD_TO_DEG) - 90f;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setYHeadRot(yaw);
        entity.yHeadRotO = yaw;
        entity.setYBodyRot(yaw);
        entity.setXRot(0);
    }

    /**
     * Removes everything a test threw far out of its area, then succeeds. Only for tests in their
     * own batch: the 14-block reach would also wipe neighbouring tests.
     */
    static void clearAround(GameTestHelper helper) {
        clearStrays(helper);
        helper.succeed();
    }

    /**
     * Removes every mob within 14 blocks (not players). Tests sit only a few blocks apart and a test
     * that failed leaves its fighters behind, so a test that counts or fights what's around it calls
     * this first. Only for tests in their own batch, like {@link #clearAround}.
     */
    static void clearStrays(GameTestHelper helper) {
        clearStrays(helper, 14);
    }

    /** As {@link #clearStrays(GameTestHelper)}, {@code reach} blocks round the arena (e.g. a jutsu's range). */
    static void clearStrays(GameTestHelper helper, int reach) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(reach);
        helper.getLevel().getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Player)).forEach(LivingEntity::discard);
    }
}

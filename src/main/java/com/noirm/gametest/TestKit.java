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
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(14);
        helper.getLevel().getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Player)).forEach(LivingEntity::discard);
        helper.succeed();
    }
}

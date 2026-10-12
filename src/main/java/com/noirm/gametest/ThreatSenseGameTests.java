package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.dojutsu.ThreatSense;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The eyes against vanilla threats: reading mob attacks, predicting projectiles, slowing them. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThreatSenseGameTests {
    private static final String BATCH = "noirm_threats";

    private ThreatSenseGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void projectilesOnCourseArePredicted(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper, 1.5, 1.5);
        Arrow incoming = arrow(helper, player, 1.5, -4.5, new Vec3(0, 0, 1.5));
        Arrow away = arrow(helper, player, 1.5, -4.5, new Vec3(0, 0, -1.5));
        Arrow wide = arrow(helper, player, 1.5, -4.5, new Vec3(1.2, 0, 1.0));
        int ticks = ThreatSense.ticksToImpact(incoming, player);
        helper.assertTrue(ticks >= 3 && ticks <= 5, "an arrow 6 blocks away at 1.5/tick hits in ~4 ticks, got " + ticks);
        helper.assertTrue(ThreatSense.ticksToImpact(away, player) == -1, "an arrow flying away is no threat");
        helper.assertTrue(ThreatSense.ticksToImpact(wide, player) == -1, "an arrow that will miss is no threat");
        cleanUp(helper);
    }

    @GameTest(template = "empty", batch = BATCH + "_2")
    public static void mobAttacksAreRead(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper, 1.5, 1.5);
        Zombie close = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 0.6f);
        Zombie far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 8.5f);
        helper.assertTrue(ThreatSense.imminentAttack(close, player) > 0, "a zombie in reach is about to hit");
        helper.assertTrue(ThreatSense.imminentAttack(far, player) == -1, "a far zombie isn't");
        cleanUp(helper);
    }

    @GameTest(template = "empty", batch = BATCH + "_3")
    public static void perceptionSlowsProjectiles(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper, 1.5, 1.5);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        Zombie shooter = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, -10.5f);
        Arrow arrow = arrow(helper, player, 1.5, -3.5, new Vec3(0, 0, 1.5));
        arrow.setOwner(shooter);
        double baseDamage = arrow.getBaseDamage();
        data.setSpeed(30);
        data.setTaijutsu(30); // 3× the zombie's perception: the arrow seems 2.5× slower (the cap)
        ThreatSense.tick(player);
        double speed = arrow.getDeltaMovement().length();
        helper.assertTrue(Math.abs(speed - 1.5 / 2.5) < 0.01, "the arrow should slow to 1.5/2.5, got " + speed);
        helper.assertTrue(Math.abs(arrow.getBaseDamage() - baseDamage * 2.5) < 0.01, "but keep its damage");

        Arrow equal = arrow(helper, player, 0.5, -3.5, new Vec3(0, 0, 1.5));
        equal.setOwner(shooter);
        data.setSpeed(10);
        data.setTaijutsu(10);
        ThreatSense.tick(player);
        helper.assertTrue(Math.abs(equal.getDeltaMovement().length() - 1.5) < 0.01, "no slowdown against an equal");
        cleanUp(helper);
    }

    // ------------------------------------------------------------ helpers

    private static Player player(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(at.x, at.y, at.z, 180f, 0f);
        return player;
    }

    private static Arrow arrow(GameTestHelper helper, Player target, double x, double z, Vec3 velocity) {
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x, 1.9, z));
        arrow.moveTo(at.x, at.y, at.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(velocity);
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    private static void cleanUp(GameTestHelper helper) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(14);
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area, e -> !(e instanceof Player)).forEach(e -> e.discard());
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}

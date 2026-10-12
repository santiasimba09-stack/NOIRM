package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.training.TrainingSystem;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests for training: the XP curve, where XP comes from, the dummy, and speed's movement bonus. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainingGameTests {
    private TrainingGameTests() {}

    @GameTest(template = "empty")
    public static void xpRaisesStatsOnACurve(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        assertNear(helper, 100f, TrainingSystem.xpToNext(10), "stat 10 needs 100 XP");
        helper.assertTrue(TrainingSystem.xpToNext(50) > TrainingSystem.xpToNext(20), "higher stats cost more");
        TrainingSystem.grant(player, Stat.TAIJUTSU, 99f);
        assertNear(helper, 10f, data.getTaijutsu(), "99 XP is not enough yet");
        TrainingSystem.grant(player, Stat.TAIJUTSU, 1f);
        assertNear(helper, 11f, data.getTaijutsu(), "100 XP raises taijutsu by one");
        assertNear(helper, 0f, data.training().xp(Stat.TAIJUTSU), "the XP is spent");
        // Enough for two points at once: 108 (11→12) + 116 (12→13) + 5 left over.
        TrainingSystem.grant(player, Stat.SPEED, 100f + 108f + 5f);
        assertNear(helper, 12f, data.getSpeed(), "several points can come at once");
        assertNear(helper, 5f, data.training().xp(Stat.SPEED), "leftover XP carries over");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dummyTrainsUntilTheDrillCap(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        TrainingDummy dummy = helper.spawn(NoirmEntities.TRAINING_DUMMY.get(), 1.5f, 1f, 1.5f);
        TrainingSystem.onHitLanded(player, dummy, false, false);
        TrainingSystem.onHitLanded(player, dummy, true, false);
        assertNear(helper, TrainingSystem.HIT_XP * 2 + TrainingSystem.HEAVY_HIT_BONUS, data(player).training().xp(Stat.TAIJUTSU),
                "a light and a heavy hit on the dummy");
        data(player).setTaijutsu(TrainingSystem.DRILL_CAP);
        data(player).training().setXp(Stat.TAIJUTSU, 0f);
        TrainingSystem.onHitLanded(player, dummy, false, false);
        assertNear(helper, 0f, data(player).training().xp(Stat.TAIJUTSU), "past the drill cap the dummy teaches nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void strongerOpponentsTeachMore(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        SparringNinja equal = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        SparringNinja strong = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        equal.joinClan(TestClans.PLAIN);
        strong.joinClan(TestClans.PLAIN);
        strong.setCombatStats(20, 20);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1.5f, 1f, 2.5f);
        float vsEqual = TrainingSystem.opponentFactor(player, equal, Stat.TAIJUTSU);
        float vsStrong = TrainingSystem.opponentFactor(player, strong, Stat.TAIJUTSU);
        assertNear(helper, TrainingSystem.REAL_FIGHT_BONUS, vsEqual, "an equal opponent teaches more than a drill");
        assertNear(helper, 2f * TrainingSystem.REAL_FIGHT_BONUS, vsStrong, "twice as strong teaches twice as much");
        assertNear(helper, 0f, TrainingSystem.opponentFactor(player, cow, Stat.TAIJUTSU), "punching cows teaches nothing");
        // Real opponents still teach past the drill cap.
        data(player).setTaijutsu(50);
        helper.assertTrue(TrainingSystem.opponentFactor(player, strong, Stat.TAIJUTSU) > 0f, "real fights teach at any level");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void perfectDodgesAndParriesTrain(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.joinClan(TestClans.PLAIN);
        TrainingSystem.onPerfectDodge(player);
        assertNear(helper, TrainingSystem.PERFECT_DODGE_XP, data(player).training().xp(Stat.SPEED), "a perfect dodge trains speed");
        TrainingSystem.onParry(player, ninja);
        assertNear(helper, TrainingSystem.PARRY_XP * TrainingSystem.REAL_FIGHT_BONUS, data(player).training().xp(Stat.TAIJUTSU),
                "parrying an equal ninja trains taijutsu");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dummyNeverDiesOrMoves(GameTestHelper helper) {
        floor(helper);
        TrainingDummy dummy = helper.spawn(NoirmEntities.TRAINING_DUMMY.get(), 1.5f, 1f, 1.5f);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        ninja.joinClan(TestClans.PLAIN);
        helper.runAfterDelay(5, () -> {
            Vec3 start = dummy.position();
            for (int i = 0; i < 5; i++) {
                dummy.invulnerableTime = 0;
                dummy.hurt(dummy.damageSources().mobAttack(ninja), 60f);
                dummy.knockback(2.0, 1, 0);
            }
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(dummy.isAlive(), "the dummy should never die from hits");
                helper.assertTrue(dummy.getHealth() > 0, "health stays above zero");
                helper.assertTrue(dummy.position().distanceTo(start) < 0.05, "the dummy should stay planted, moved " + dummy.position().distanceTo(start));
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "noirm_dummy")
    public static void ninjaComboLandsOnTheDummy(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        TrainingDummy dummy = helper.spawn(NoirmEntities.TRAINING_DUMMY.get(), 1.5f, 1f, 2.0f);
        ninja.joinClan(TestClans.PLAIN);
        face(ninja, dummy);
        Vec3 start = dummy.position();
        CombatSystem.requestAttack(ninja, AttackInput.HEAVY);
        helper.succeedWhen(() -> {
            helper.assertTrue(dummy.getHealth() < dummy.getMaxHealth(), "the heavy kick hasn't landed yet");
            helper.assertTrue(dummy.position().distanceTo(start) < 0.05, "a heavy kick must not push the dummy");
        });
    }

    @GameTest(template = "empty")
    public static void speedMakesYouFaster(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        TrainingSystem.applyMovementBonus(player, 60f);
        double fast = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double expected = base * (1 + 50 * TrainingSystem.MOVE_BONUS_PER_POINT);
        helper.assertTrue(Math.abs(fast - expected) < 1e-4, "speed 60 should move " + expected + ", got " + fast);
        TrainingSystem.applyMovementBonus(player, 10f);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1e-6, "speed 10 is the normal pace");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vitalityRaisesMaxHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        TrainingSystem.applyVitality(player, 30f);
        assertNear(helper, 30f, player.getMaxHealth(), "vitality 30 = 20 + 10 health");
        TrainingSystem.applyVitality(player, 10f);
        assertNear(helper, 20f, player.getMaxHealth(), "vitality 10 = normal health");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reservesRaiseMaxChakra(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        data.setReserves(30f);
        TrainingSystem.applyReserves(data);
        assertNear(helper, 200f, data.getMaxChakra(), "reserves 30 doubles the chakra pool");
        TrainingSystem.applyReserves(data);
        assertNear(helper, 200f, data.getMaxChakra(), "applying again changes nothing");
        data.setReserves(20f);
        TrainingSystem.applyReserves(data);
        assertNear(helper, 150f, data.getMaxChakra(), "the pool follows the stat");
        helper.assertTrue(data.chakraRegenMultiplier() > 1.15f, "and chakra comes back faster");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spendingChakraTrainsReserves(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        helper.assertTrue(data.tryConsumeChakra(40f), "spend 40 chakra");
        TrainingSystem.tick(player, data);
        assertNear(helper, 40f * TrainingSystem.RESERVES_XP_PER_CHAKRA, data.training().xp(Stat.RESERVES), "chakra spent becomes reserves XP");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivingHitsTrainsVitality(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 1.5f);
        TrainingSystem.onDamageTaken(player, player.damageSources().mobAttack(zombie), 5f);
        assertNear(helper, 5f * TrainingSystem.VITALITY_XP_PER_DAMAGE * TrainingSystem.REAL_FIGHT_BONUS,
                data(player).training().xp(Stat.VITALITY), "a zombie's hit (an equal) trains vitality");
        data(player).training().setXp(Stat.VITALITY, 0f);
        TrainingSystem.onDamageTaken(player, player.damageSources().fall(), 4f);
        assertNear(helper, 4f * TrainingSystem.VITALITY_XP_PER_DAMAGE, data(player).training().xp(Stat.VITALITY), "a fall is a drill");
        zombie.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void face(LivingEntity entity, LivingEntity other) {
        float yaw = (float) (Mth.atan2(other.getZ() - entity.getZ(), other.getX() - entity.getX()) * Mth.RAD_TO_DEG) - 90f;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setYHeadRot(yaw);
        entity.yHeadRotO = yaw;
        entity.setYBodyRot(yaw);
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

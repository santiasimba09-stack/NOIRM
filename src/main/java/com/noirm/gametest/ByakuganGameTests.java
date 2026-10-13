package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanSystem;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.dojutsu.Byakugan;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.HyugaTechniques;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests for the Byakugan: Hyūga birthright, Gentle Fist, all-round guard, Rotation, 64 Palms. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ByakuganGameTests {
    private static final float NINJA_HEALTH = 40f;
    /** Rotation pushes everything around it; keep it away from other tests. */
    private static final String SPIN_BATCH = "noirm_rotation";

    private ByakuganGameTests() {}

    @GameTest(template = "empty")
    public static void hyugaAreBornWithTheByakugan(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        Clan hyuga = ClanData.canon("hyuga").orElseThrow(() -> new IllegalStateException("hyuga clan missing"));
        ClanSystem.assign(player, data, hyuga);
        helper.assertTrue(data.eyes().hasByakugan(), "Hyūga are born with the Byakugan");
        helper.assertFalse(Byakugan.isActive(player), "the eyes start closed");
        for (ResourceLocation id : Byakugan.JUTSU) {
            helper.assertTrue(JutsuData.get(id) != null, "missing jutsu data " + id);
            helper.assertTrue(data.jutsu().knows(id), "Hyūga know " + id);
        }
        data.eyes().setActive(true);
        helper.assertTrue(Byakugan.isActive(player), "G opens it");
        helper.assertTrue(data.eyes().activeStage() == 0, "it is not a Sharingan");
        ClanSystem.assign(player, data, TestClans.PLAIN);
        helper.assertFalse(data.eyes().hasByakugan(), "other clans don't have it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_gentle_fist")
    public static void gentleFistHurtsThroughGuard(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.BYAKUGAN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        face(attacker, target);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 4, () -> CombatSystem.requestAttack(attacker, AttackInput.LIGHT));
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "blocked jab hasn't landed yet");
            assertNear(helper, NINJA_HEALTH - 3f * Byakugan.GENTLE_FIST_GUARD, target.getHealth(), "Gentle Fist keeps half its damage through a guard");
            helper.assertTrue(Byakugan.closedPoints(target) > 0, "Gentle Fist closes chakra points");
        });
    }

    @GameTest(template = "empty", batch = "noirm_gentle_fist_2")
    public static void byakuganGuardsFromBehind(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja hyuga = ninja(helper, 1.5f, 2.0f, TestClans.BYAKUGAN);
        SparringNinja plain = ninja(helper, 0.5f, 2.0f, TestClans.PLAIN);
        face(attacker, hyuga);
        face(hyuga, attacker);
        hyuga.setYHeadRot(hyuga.getYHeadRot() + 180f); // back turned
        plain.setYHeadRot(hyuga.getYHeadRot());
        CombatSystem.setGuard(hyuga, true);
        CombatSystem.setGuard(plain, true);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 4, () -> {
            CombatState hyugaState = CombatStates.get(hyuga);
            CombatState plainState = CombatStates.get(plain);
            helper.assertTrue(CombatSystem.classify(hyuga, hyugaState, attacker) == CombatSystem.Outcome.GUARDED,
                    "the Byakugan blocks attacks from behind");
            helper.assertTrue(CombatSystem.classify(plain, plainState, attacker) == CombatSystem.Outcome.HIT,
                    "without it, a turned back gets hit");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void closedChakraPointsSealJutsu(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.BYAKUGAN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.FIRE);
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        helper.assertTrue(JutsuCaster.checkRequirements(target, fireball, JutsuData.get(fireball)) == JutsuCaster.Result.OK, "can cast before");
        for (int i = 0; i < Byakugan.SEAL_POINTS / Byakugan.POINTS_PER_HIT; i++) {
            Byakugan.gentleFist(attacker, target, false);
        }
        helper.assertTrue(Byakugan.isSealed(target), "enough closed points seal the target");
        helper.assertTrue(JutsuCaster.checkRequirements(target, fireball, JutsuData.get(fireball)) == JutsuCaster.Result.SEALED,
                "a sealed ninja can't cast");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = SPIN_BATCH)
    public static void rotationTurnsAttacksAway(GameTestHelper helper) {
        SparringNinja hyuga = ninja(helper, 1.5f, 1.5f, TestClans.BYAKUGAN);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        helper.assertTrue(JutsuCaster.npcCast(hyuga, Byakugan.ROTATION, 1f), "a Hyūga ninja can spin");
        helper.assertTrue(HyugaTechniques.isSpinning(hyuga), "spinning right after the cast");
        float before = hyuga.getHealth();
        hyuga.invulnerableTime = 0;
        hyuga.hurt(hyuga.damageSources().mobAttack(attacker), 8f);
        assertNear(helper, before, hyuga.getHealth(), "no attack gets through the spin");
        helper.runAfterDelay(30, () -> {
            helper.assertFalse(HyugaTechniques.isSpinning(hyuga), "the spin ends");
            clearKnockedAway(helper);
        });
    }

    @GameTest(template = "empty", batch = SPIN_BATCH + "_palms", timeoutTicks = 120)
    public static void sixtyFourPalmsSealTheTarget(GameTestHelper helper) {
        SparringNinja hyuga = ninja(helper, 1.5f, 0.5f, TestClans.BYAKUGAN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        face(hyuga, target);
        helper.assertTrue(JutsuCaster.npcCast(hyuga, Byakugan.SIXTY_FOUR_PALMS, 1f), "64 Palms should find the target in front");
        helper.onEachTick(() -> {
            if (HyugaTechniques.isStriking(hyuga)) {
                return;
            }
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "the palms should hurt");
            helper.assertTrue(Byakugan.isSealed(target), "64 palms close enough points to seal");
            clearKnockedAway(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    /** Rotation and 64 Palms throw ninjas out of the test area, where cleanup can't see them. */
    private static void clearKnockedAway(GameTestHelper helper) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(10);
        helper.getLevel().getEntitiesOfClass(SparringNinja.class, area).forEach(SparringNinja::discard);
        helper.succeed();
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        for (int bx = 0; bx < 3; bx++) {
            for (int bz = 0; bz < 3; bz++) {
                helper.setBlock(new BlockPos(bx, 0, bz), Blocks.STONE);
            }
        }
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(clan);
        return ninja;
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

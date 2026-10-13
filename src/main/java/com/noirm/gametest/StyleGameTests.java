package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.StyleSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests for fighting styles, directional attacks, the clinch and wall splats. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StyleGameTests {
    private StyleGameTests() {}

    @GameTest(template = "empty")
    public static void directionalAttacksAndStyles(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        assertMove(helper, player, AttackInput.FORWARD_LIGHT, "basic/jab", "a dash attack falls back to the plain one");
        assertMove(helper, player, AttackInput.GRAB, "clinch/grab", "everyone can grab");
        var data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        data.setCombatStyle(Noirm.id("brawler").toString());
        assertMove(helper, player, AttackInput.LIGHT, "brawler/elbow", "Brawlers open with an elbow");
        assertMove(helper, player, AttackInput.FORWARD_HEAVY, "brawler/flying_knee", "and dash in with a flying knee");
        helper.assertFalse(StyleSystem.available(data).contains(Noirm.id("strong_fist")), "Strong Fist needs taijutsu");
        helper.assertFalse(StyleSystem.available(data).contains(StyleSystem.GENTLE_FIST), "the Gentle Fist needs the Byakugan");
        data.setTaijutsu(20);
        helper.assertTrue(StyleSystem.available(data).contains(Noirm.id("strong_fist")), "Strong Fist opens at taijutsu 18");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_npc_styles")
    public static void ninjasFightInStyles(GameTestHelper helper) {
        SparringNinja hyuga = ninja(helper, 0.5f, 0.5f, TestClans.BYAKUGAN);
        helper.assertTrue(StyleSystem.GENTLE_FIST.equals(hyuga.combatStyle()), "Hyūga fight with the Gentle Fist, got " + hyuga.combatStyle());
        SparringNinja plain = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        helper.assertTrue(plain.combatStyle() != null && !StyleSystem.GENTLE_FIST.equals(plain.combatStyle()), "others don't");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_clinch", timeoutTicks = 80)
    public static void grabsBeatGuardAndKneesFollow(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 1.8f, TestClans.PLAIN);
        target.setInvulnerable(false);
        face(attacker, target);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 3, () -> CombatSystem.requestAttack(attacker, AttackInput.GRAB));
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 12, () -> {
            CombatState held = CombatStates.get(target);
            helper.assertTrue(held.clinchedBy() == attacker, "the grab goes through the guard");
            helper.assertFalse(held.isGuarding(), "and drops it");
            CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        });
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 24, () -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the knee lands");
            helper.assertTrue(CombatStates.get(attacker).clinchTarget() == target, "and the clinch holds");
            CombatSystem.requestDodge(target, -1f, 0f);
            helper.assertTrue(CombatStates.get(target).clinchedBy() == null, "a dodge breaks free");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_wall_splat", timeoutTicks = 60)
    public static void heavyHitsSplatAgainstWalls(GameTestHelper helper) {
        floor(helper);
        for (int x = 0; x < 3; x++) {
            for (int y = 1; y < 4; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.OBSIDIAN);
            }
        }
        SparringNinja attacker = ninja(helper, 1.5f, 0.2f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 1.4f, TestClans.PLAIN);
        attacker.setCombatStyle(CombatData.DEFAULT_STYLE);
        target.setInvulnerable(false);
        face(attacker, target);
        CombatSystem.requestAttack(attacker, AttackInput.HEAVY); // heavy kick: knockback 0.9
        helper.runAfterDelay(16, () -> {
            float lost = target.getMaxHealth() - target.getHealth();
            helper.assertTrue(lost > 7.5f, "the wall adds damage on top of the kick, lost " + lost);
            helper.assertTrue(CombatSystem.isStaggered(target), "and leaves them stunned");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_gentle_style", timeoutTicks = 60)
    public static void gentleFistGetsThroughGuard(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.2f, TestClans.PLAIN);
        attacker.setCombatStyle(StyleSystem.GENTLE_FIST);
        target.setInvulnerable(false);
        face(attacker, target);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 3, () -> CombatSystem.requestAttack(attacker, AttackInput.LIGHT));
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 12, () -> {
            float lost = target.getMaxHealth() - target.getHealth();
            helper.assertTrue(CombatStates.get(target).isGuarding() || CombatStates.get(target).posture() > 0, "the palm was guarded");
            helper.assertTrue(lost > 1.2f, "but 60% of it gets through the guard, lost " + lost);
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    private static void assertMove(GameTestHelper helper, LivingEntity entity, AttackInput input, String expected, String why) {
        var id = CombatSystem.resolveMove(entity, null, input);
        helper.assertTrue(Noirm.id(expected).equals(id), why + ": expected " + expected + ", got " + id);
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.joinClan(clan);
        return ninja;
    }
}

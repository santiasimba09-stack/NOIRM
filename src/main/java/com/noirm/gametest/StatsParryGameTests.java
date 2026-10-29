package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.dojutsu.Mangekyo;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.training.Supplementary;
import com.noirm.training.TrainingSystem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Blueprint phase 1: the supplementary stats (CTL, WIL), perception focus and the just-frame parry.
 * Fights run in their own batches (a parry's focus moment slows everything within 10 blocks).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StatsParryGameTests {
    private static final float NINJA_HEALTH = 40f;
    private static final ResourceLocation FIREBALL = Noirm.id("katon/great_fireball");

    private StatsParryGameTests() {}

    // ------------------------------------------------------------ CTL and WIL

    @GameTest(template = "empty")
    public static void controlLowersJutsuCost(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        JutsuDefinition fireball = JutsuData.get(FIREBALL);
        helper.assertTrue(fireball != null, "the fireball should load");
        float base = JutsuCaster.cost(player, FIREBALL, fireball);
        helper.assertTrue(base > 0f, "the fireball costs chakra");
        data.setControl(60f);
        assertNear(helper, base * 0.85f, JutsuCaster.cost(player, FIREBALL, fireball), "CTL 60 = -15% cost");
        data.setControl(500f);
        assertNear(helper, base * (1f - Supplementary.CTL_MAX_COST_CUT), JutsuCaster.cost(player, FIREBALL, fireball), "capped at -30%");
        data.setControl(5f);
        assertNear(helper, base, JutsuCaster.cost(player, FIREBALL, fireball), "below 10 never costs more");
        data.setControl(60f);
        helper.assertTrue(Supplementary.waterWalkMultiplier(data) < 1f, "and water walking is cheaper");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void willpowerLowersStrainAndSteadiesTheBeast(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        data.eyes().setSharinganStage(3);
        data.eyes().setMangekyo(true);
        Mangekyo.addStrain(player, 10f);
        assertNear(helper, 10f, data.eyes().strain(), "WIL 10: full strain");
        data.setWillpower(60f);
        Mangekyo.addStrain(player, 10f);
        assertNear(helper, 18f, data.eyes().strain(), "WIL 60: -20% strain");
        assertNear(helper, 1f - Supplementary.WIL_MAX_STRAIN_CUT, Supplementary.strainMultiplier(1000f), "capped at -40%");
        helper.assertTrue(Supplementary.extraSafeTails(data) == 2, "WIL 60 holds two more tails safely");
        data.setWillpower(10f);
        helper.assertTrue(Supplementary.extraSafeTails(data) == 0, "WIL 10 holds none");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void supplementaryStatsSaveAndGrow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        helper.assertTrue(data.getControl() == ShinobiData.DEFAULT_STAT && data.getWillpower() == ShinobiData.DEFAULT_STAT, "both start at 10");
        // Spending chakra while below half trains control; small upkeep drains don't.
        data.setChakra(data.getMaxChakra() * 0.4f);
        data.tryConsumeChakra(0.1f);
        data.tryConsumeChakra(10f);
        TrainingSystem.tick(player, data);
        assertNear(helper, 10f * Supplementary.CTL_PRESSURE_XP_PER_CHAKRA, data.training().xp(Supplementary.Stat.CONTROL), "pressure spend -> CTL XP");
        // Using a strained Mangekyō builds willpower.
        data.eyes().setSharinganStage(3);
        data.eyes().setMangekyo(true);
        data.eyes().setStrain(30f);
        Mangekyo.addStrain(player, 5f);
        assertNear(helper, 5f * Supplementary.WIL_STRAIN_XP, data.training().xp(Supplementary.Stat.WILLPOWER), "strained use -> WIL XP");
        // Enough XP raises the stat.
        Supplementary.grant(player, data, Supplementary.Stat.CONTROL, TrainingSystem.xpToNext(10f));
        assertNear(helper, 11f, data.getControl(), "a full point of XP raises CTL");

        data.setControl(23f);
        data.setWillpower(31f);
        data.training().setXp(Supplementary.Stat.WILLPOWER, 7f);
        CompoundTag saved = data.save();
        ShinobiData copy = new ShinobiData();
        copy.load(saved);
        assertNear(helper, 23f, copy.getControl(), "CTL saves");
        assertNear(helper, 31f, copy.getWillpower(), "WIL saves");
        assertNear(helper, 7f, copy.training().xp(Supplementary.Stat.WILLPOWER), "WIL XP saves");
        ShinobiData old = new ShinobiData();
        old.load(new CompoundTag());
        assertNear(helper, ShinobiData.DEFAULT_STAT, old.getControl(), "old saves start CTL at 10");
        helper.succeed();
    }

    // ------------------------------------------------------------ the just-frame parry

    @GameTest(template = "empty", batch = "noirm_just_frame")
    public static void justFrameParryBreaksGuardAndOpensCounter(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, 10f);
        SparringNinja defender = ninja(helper, 1.5f, 2.0f, 20f); // twice as perceptive: the heavy plays 2x slower
        face(attacker, defender);
        face(defender, attacker);
        CombatState attackerState = CombatStates.get(attacker);
        CombatState defenderState = CombatStates.get(defender);
        helper.assertTrue(CombatSystem.requestAttack(attacker, AttackInput.HEAVY), "the heavy kick starts");
        helper.assertTrue(attackerState.perceivedBy() == defender.getId(), "the defender's perception slows this heavy (focus)");
        boolean[] guarded = {false};
        boolean[] countered = {false};
        helper.onEachTick(() -> {
            // Guard at the last moment, as the kick comes out.
            if (!guarded[0] && attackerState.phase() == CombatPhase.ACTIVE) {
                guarded[0] = true;
                CombatSystem.setGuard(defender, true);
            }
            // The counter: a jab inside the window.
            if (!countered[0] && defenderState.counterTicks() > 0) {
                countered[0] = true;
                assertNear(helper, defender.getMaxHealth(), defender.getHealth(), "a just-frame parry takes no damage");
                helper.assertTrue(attackerState.phase() == CombatPhase.STAGGER
                        && attackerState.phaseLength() == CombatSystem.POSTURE_BREAK_STUN, "the attacker's posture breaks");
                helper.assertTrue(CombatSystem.lastPostureBreak(attacker) != Long.MIN_VALUE, "counted as a posture break");
                CombatSystem.setGuard(defender, false);
                helper.assertTrue(CombatSystem.requestAttack(defender, AttackInput.LIGHT), "the counter starts inside the window");
            }
        });
        // The jab: 3 x taijutsu 20 (x2) x counter 1.5 = 9.
        helper.succeedWhen(() -> {
            helper.assertTrue(countered[0] && attacker.getHealth() < NINJA_HEALTH, "the counter hasn't landed yet");
            assertNear(helper, NINJA_HEALTH - 3f * 2f * CombatSystem.COUNTER_DAMAGE, attacker.getHealth(), "the counter hits x1.5");
            helper.assertFalse(defenderState.hasCounter(), "the counter is spent");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_counter_guard")
    public static void counterCannotBeGuarded(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, 10f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, 10f);
        face(attacker, target);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 4, () -> {
            CombatSystem.openCounterWindow(attacker);
            helper.assertTrue(CombatSystem.requestAttack(attacker, AttackInput.LIGHT), "the counter jab starts");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "the counter hasn't landed yet");
            assertNear(helper, NINJA_HEALTH - 3f * CombatSystem.COUNTER_DAMAGE, target.getHealth(), "a counter goes through the guard, x1.5");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_normal_parry")
    public static void normalParryStaysTheSame(GameTestHelper helper) {
        // Equals: no perception slowdown, so a parry is an ordinary one.
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, 10f);
        SparringNinja defender = ninja(helper, 1.5f, 2.0f, 10f);
        face(attacker, defender);
        face(defender, attacker);
        CombatSystem.requestAttack(attacker, AttackInput.HEAVY);
        CombatState attackerState = CombatStates.get(attacker);
        CombatState defenderState = CombatStates.get(defender);
        helper.assertTrue(attackerState.perceivedBy() == -1, "no focus between equals");
        boolean[] guarded = {false};
        helper.onEachTick(() -> {
            if (!guarded[0] && attackerState.phase() == CombatPhase.ACTIVE) {
                guarded[0] = true;
                CombatSystem.setGuard(defender, true);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(attackerState.phase() == CombatPhase.STAGGER, "a parry staggers the attacker");
            assertNear(helper, CombatSystem.PARRY_STUN, attackerState.phaseLength(), "for the usual parry stun");
            assertNear(helper, NINJA_HEALTH, defender.getHealth(), "a parried hit deals no damage");
            helper.assertTrue(attackerState.posture() > 0 && attackerState.posture() < attackerState.maxPosture(), "posture builds but doesn't break");
            helper.assertFalse(defenderState.hasCounter(), "no counter window");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, float stats) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setCombatStats(stats, stats);
        return ninja;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(() -> new IllegalStateException("no shinobi data"));
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

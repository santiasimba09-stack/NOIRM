package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.client.anim.KeyframeAnimation;
import com.noirm.client.anim.SkeletonPose;
import com.noirm.client.anim.SkeletonPose.Bone;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatMove;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatStyle;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.SubStance;
import com.noirm.entity.SparringNinja;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * The combat animation rebuild (2026-11, docs/COMBAT_DESIGN.md): snap key poses lined up with the
 * hit, sub-stances, long strings, dodge counters, dash strikes, guard breaks and the faster pace.
 * (The parry, focus and seal lock keep their own tests in CombatGameTests, StatsParryGameTests and
 * CastingEngineGameTests.)
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatRebuildGameTests {
    private static final float NINJA_HEALTH = 40f;

    private CombatRebuildGameTests() {}

    // ------------------------------------------------------------ animation data

    /** Every move's animation lasts the move, its strike marker is the hit tick, and the strike pose is held there. */
    @GameTest(template = "empty")
    public static void strikePosesLandOnTheHitTick(GameTestHelper helper) {
        Map<String, KeyframeAnimation> all = AnimationGameTests.loadAll(helper);
        int[] checked = {0};
        SkeletonPose at = new SkeletonPose(), held = new SkeletonPose();
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        CombatData.styles().values().forEach(style -> collect(style, ids));
        for (ResourceLocation id : ids) {
            CombatMove move = CombatData.move(id);
            if (move == null || move.animation().isEmpty()) {
                continue;
            }
            String name = move.animation().get().getPath();
            KeyframeAnimation anim = all.get(name);
            helper.assertTrue(anim != null, id + " plays missing " + name);
            int total = move.totalTicks();
            near(helper, total, anim.lengthTicks, name + " lasts the move " + id);
            near(helper, move.timing().windup(), anim.strikeTick, name + "'s strike marker is " + id + "'s hit tick");
            helper.assertTrue(anim.strikeHold >= 1f, name + " holds its strike pose");
            helper.assertTrue(anim.snapWeight(anim.strikeTick) == 1f, name + " snaps into its strike (springs pinned)");
            at.clear();
            held.clear();
            anim.sample(anim.strikeTick, at);
            anim.sample(anim.strikeTick + anim.strikeHold - 0.05f, held);
            for (Bone bone : Bone.values()) {
                for (int c = 0; c < SkeletonPose.CHANNELS; c++) {
                    if (at.isSet(bone, c)) {
                        helper.assertTrue(Math.abs(at.get(bone, c) - held.get(bone, c)) < 1.0e-3f,
                                name + ": the strike pose is held (" + bone + ")");
                    }
                }
            }
            checked[0]++;
        }
        helper.assertTrue(checked[0] >= 100, "checked the moves' animations: " + checked[0]);
        // Every style's sub-stance stances exist, and so do the enders' reactions and the side steps.
        CombatData.styles().forEach((sid, style) -> style.stances().forEach((sub, stance) ->
                helper.assertTrue(all.containsKey(stance.animation().getPath()), sid + " " + sub + " stance missing: " + stance.animation())));
        for (String name : new String[]{"hit_launch", "hit_crumple", "hit_fly", "dodge_side_left", "dodge_side_right"}) {
            helper.assertTrue(all.containsKey(name), "missing " + name);
        }
        helper.succeed();
    }

    /** Every move reachable from a style: openers, routes, variants, the counter. */
    private static void collect(CombatStyle style, java.util.Set<ResourceLocation> out) {
        java.util.ArrayDeque<ResourceLocation> todo = new java.util.ArrayDeque<>(style.openers().values());
        todo.add(style.counterMove());
        while (!todo.isEmpty()) {
            ResourceLocation id = todo.poll();
            CombatMove move = CombatData.move(id);
            if (move != null && out.add(id)) {
                todo.addAll(move.next().values());
                todo.addAll(move.variants().values());
            }
        }
    }

    /** A snap key reaches its pose almost at once (a tiny overshoot), and a hold freezes it. */
    @GameTest(template = "empty")
    public static void snapKeysAreSteppedAndHeld(GameTestHelper helper) {
        JsonObject file = JsonParser.parseString("""
                {"animations": {"t": {"animation_length": 1.0, "strike": 0.5, "strike_hold": 0.15, "bones": {"right_arm": {"rotation": {
                  "0.0": {"vector": [0, 0, 0]},
                  "0.4": {"vector": [0, 0, 0]},
                  "0.5": {"vector": [-90, 0, 0], "easing": "snap", "hold": 0.15},
                  "1.0": {"vector": [0, 0, 0], "easing": "linear"}}}}}}}""").getAsJsonObject();
        KeyframeAnimation anim = KeyframeAnimation.parse(file).get("t");
        SkeletonPose pose = new SkeletonPose();
        float target = (float) Math.toRadians(-90);
        float peak = 0f;
        for (float t = 8f; t <= 10f; t += 0.05f) {
            pose.clear();
            anim.sample(t, pose);
            peak = Math.min(peak, pose.get(Bone.RIGHT_ARM, SkeletonPose.RX));
        }
        helper.assertTrue(peak < target && peak > target * 1.09f, "a tiny overshoot on the way in, peak " + peak);
        pose.clear();
        anim.sample(8f + 2f * KeyframeAnimation.SNAP_REACH, pose);
        near(helper, target, pose.get(Bone.RIGHT_ARM, SkeletonPose.RX), "the pose is reached a third of the way in");
        pose.clear();
        anim.sample(12.9f, pose);
        near(helper, target, pose.get(Bone.RIGHT_ARM, SkeletonPose.RX), "and held for the hold (3 ticks)");
        pose.clear();
        anim.sample(17.5f, pose);
        helper.assertTrue(pose.get(Bone.RIGHT_ARM, SkeletonPose.RX) > target * 0.5f, "then the recovery");
        near(helper, 10f, anim.strikeTick, "the strike marker");
        helper.assertTrue(anim.snapWeight(11f) == 1f && anim.snapWeight(16f) == 0f, "the springs are pinned only through the snap and hold");
        helper.succeed();
    }

    // ------------------------------------------------------------ pace

    /** Faster overall: quick lights, quicker heavies, a short hit-stop everywhere. */
    @GameTest(template = "empty")
    public static void fasterTimings(GameTestHelper helper) {
        CombatMove jab = CombatData.move(Noirm.id("basic/jab"));
        helper.assertTrue(jab.timing().windup() == 2 && jab.totalTicks() == 8, "the jab: 2-tick wind-up, 8 in all (was 3 / 11)");
        CombatMove heavy = CombatData.move(Noirm.id("basic/heavy_kick"));
        helper.assertTrue(heavy.totalTicks() == 17 && heavy.hit().isHeavy(), "the heavy kick: 17 ticks (was 24), still heavy");
        CombatData.styles().forEach((sid, style) -> {
            CombatMove light = CombatData.move(style.opener(AttackInput.LIGHT));
            if (style.playable() || sid.getPath().startsWith("weapon_")) {
                // Fists: a 2-tick wind-up, 8 ticks in all. Weapons: even the nodachi's sweep is under a second.
                boolean fists = style.playable();
                helper.assertTrue(fists ? light.timing().windup() <= 2 && light.totalTicks() <= 8 : light.totalTicks() <= 17,
                        sid + "'s light is quick: " + light.timing());
                CombatMove h = CombatData.move(style.opener(AttackInput.HEAVY));
                helper.assertTrue(h.totalTicks() > light.totalTicks(), sid + "'s heavy stays heavier than its light");
            }
        });
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        CombatData.styles().values().forEach(style -> collect(style, ids));
        for (ResourceLocation id : ids) {
            helper.assertTrue(CombatData.move(id).hit().hitstop() <= CombatSystem.MAX_HITSTOP, id + "'s hit-stop is short");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------ strings

    /** Each style's light route is a long string (5 hits) that ends on an ender, with branches on the way. */
    @GameTest(template = "empty")
    public static void longStringRoutes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        assertString(helper, player, "basic/jab", "basic/hook", "basic/body_shot", "basic/spin_kick", "basic/axe_kick");
        assertString(helper, player, "brawler/elbow", "brawler/rising_knee", "brawler/back_elbow", "brawler/body_hook", "brawler/uppercut_elbow");
        assertString(helper, player, "strong_fist/jab", "strong_fist/straight", "strong_fist/rapid_kick", "strong_fist/leaf_whirlwind",
                "strong_fist/leaf_great_flash");
        assertString(helper, player, "gentle_fist/palm", "gentle_fist/palm_2", "gentle_fist/palm_3", "gentle_fist/palm_flurry",
                "gentle_fist/lion_palms");
        // Branches: heavy off the second hit launches, heavy off the fourth splats.
        route(helper, player, "basic/hook", AttackInput.HEAVY, "basic/flip_kick");
        route(helper, player, "basic/spin_kick", AttackInput.HEAVY, "basic/heavy_kick");
        helper.assertTrue(CombatData.move(Noirm.id("basic/flip_kick")).hit().ender() == CombatMove.Hit.Ender.LAUNCH, "the flip kick launches");
        helper.assertTrue(CombatData.move(Noirm.id("basic/heavy_kick")).hit().ender() == CombatMove.Hit.Ender.SPLAT, "the heavy kick splats");
        // Every weapon's light string runs at least 4 hits.
        for (String weapon : new String[]{"kunai", "staff", "sword", "tanto", "katana", "greatsword", "tonfa", "spear", "claws", "fan"}) {
            ResourceLocation id = CombatData.style(Noirm.id("weapon_" + weapon)).opener(AttackInput.LIGHT);
            java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
            int hits = 0;
            while (id != null && seen.add(id)) {
                hits++;
                id = CombatData.move(id).next().get(AttackInput.LIGHT);
            }
            helper.assertTrue(hits >= 4, weapon + "'s light string has " + hits + " hits");
        }
        helper.succeed();
    }

    private static void assertString(GameTestHelper helper, Player player, String... moves) {
        for (int i = 1; i < moves.length; i++) {
            route(helper, player, moves[i - 1], AttackInput.LIGHT, moves[i]);
        }
        CombatMove last = CombatData.move(Noirm.id(moves[moves.length - 1]));
        helper.assertTrue(last.hit().ender() != CombatMove.Hit.Ender.NONE, moves[moves.length - 1] + " ends the string with an ender");
    }

    private static void route(GameTestHelper helper, Player player, String from, AttackInput input, String expected) {
        ResourceLocation result = CombatSystem.resolveMove(player, Noirm.id(from), input);
        helper.assertTrue(Noirm.id(expected).equals(result), from + " + " + input + " should be " + expected + ", got " + result);
    }

    /** A real string: three lights in a row land, each continuing the route, and the attacker turns aggressive. */
    @GameTest(template = "empty", batch = "noirm_rebuild_string", timeoutTicks = 80)
    public static void stringPlaysOut(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 1.9f);
        target.setInvulnerable(false);
        face(attacker, target);
        CombatState state = CombatStates.get(attacker);
        int[] presses = {0};
        int[] deepest = {0};
        helper.onEachTick(() -> {
            face(attacker, target);
            if (presses[0] < 3 && CombatSystem.canAttackNow(attacker) && CombatSystem.requestAttack(attacker, AttackInput.LIGHT)) {
                presses[0]++;
            }
            deepest[0] = Math.max(deepest[0], state.comboDepth());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(presses[0] == 3, "three presses");
            helper.assertTrue(Noirm.id("basic/body_shot").equals(state.moveId()), "the third hit is the body shot, got " + state.moveId());
            helper.assertTrue(deepest[0] == 2, "the string counted its hits, depth " + deepest[0]);
            helper.assertTrue(state.subStance() == SubStance.AGGRESSIVE, "pressing: the aggressive sub-stance, got " + state.subStance());
            helper.assertTrue(target.getHealth() <= NINJA_HEALTH - 9f, "three hits landed, health " + target.getHealth());
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ sub-stances

    @GameTest(template = "empty", batch = "noirm_rebuild_substance", timeoutTicks = 100)
    public static void subStanceAutoSelection(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper, 1.5f, 0.5f);
        SparringNinja other = ninja(helper, 1.5f, 2.5f);
        CombatState state = CombatStates.get(ninja);
        CombatSystem.tick(ninja);
        helper.assertTrue(state.subStance() == SubStance.NEUTRAL, "starts neutral, got " + state.subStance());
        CombatSystem.setGuard(ninja, true);
        CombatSystem.tick(ninja);
        helper.assertTrue(state.subStance() == SubStance.DEFENSIVE, "guarding: defensive, got " + state.subStance());
        helper.assertTrue(Noirm.id("stance_basic_defensive").equals(state.stanceAnimation()), "with its own stance: " + state.stanceAnimation());
        helper.assertTrue("noirm:stance_basic_defensive".equals(ninja.syncedStance()), "synced to the client: " + ninja.syncedStance());
        CombatSystem.setGuard(ninja, false);
        // Crouching (a player): low, and the jab's low variant comes out.
        Player player = helper.makeMockPlayer();
        CombatStates.get(player);
        player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        player.setShiftKeyDown(true);
        helper.assertTrue(CombatSystem.pickSubStance(player) == SubStance.LOW, "crouching: low, got " + CombatSystem.pickSubStance(player));
        CombatSystem.tick(player);
        helper.assertTrue(Noirm.id("basic/low_kick").equals(CombatSystem.resolveMove(player, null, AttackInput.LIGHT)),
                "low: the jab becomes a low kick, got " + CombatSystem.resolveMove(player, null, AttackInput.LIGHT));
        // Up in the air: air (the Academy has an air stance; the Brawler doesn't, so stays neutral).
        Vec3 up = ninja.position().add(0, 6, 0);
        ninja.setNoGravity(false);
        ninja.teleportTo(up.x, up.y, up.z);
        ninja.setOnGround(false); // (no AI: nothing moves them, so say so)
        other.setCombatStyle(Noirm.id("brawler"));
        other.teleportTo(other.getX(), other.getY() + 6, other.getZ());
        other.setOnGround(false);
        SubStance[] seen = {null, null};
        helper.runAfterDelay(1, () -> {
            for (int i = 0; i < CombatSystem.AIR_STANCE_TICKS + 1; i++) {
                CombatSystem.tick(ninja);
                CombatSystem.tick(other);
            }
            seen[0] = state.subStance();
            seen[1] = CombatStates.get(other).subStance();
            helper.assertTrue(seen[0] == SubStance.AIR, "in the air: air, got " + seen[0]);
            helper.assertTrue(Noirm.id("basic/air_kick").equals(CombatSystem.resolveMove(ninja, null, AttackInput.LIGHT)), "an air kick");
            helper.assertTrue(seen[1] == SubStance.NEUTRAL, "a style without an air stance stays neutral, got " + seen[1]);
            ninja.discard();
            other.discard();
            helper.succeed();
        });
    }

    /** Just hit: defensive (the heavy becomes the push kick); just landed one: aggressive (the heavy becomes the axe kick). */
    @GameTest(template = "empty", batch = "noirm_rebuild_substance_hits", timeoutTicks = 60)
    public static void subStanceFollowsHits(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 1.9f);
        target.setInvulnerable(false);
        face(attacker, target);
        CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "the jab lands");
            CombatSystem.tick(target);
            helper.assertTrue(CombatStates.get(target).subStance() == SubStance.DEFENSIVE, "hit: defensive");
            helper.assertTrue(Noirm.id("basic/push_kick").equals(CombatSystem.resolveMove(target, null, AttackInput.HEAVY)), "defensive heavy: push kick");
            helper.assertTrue(CombatStates.get(attacker).subStance() == SubStance.AGGRESSIVE, "landed it: aggressive");
            helper.assertTrue(Noirm.id("basic/axe_kick").equals(CombatSystem.resolveMove(attacker, null, AttackInput.HEAVY)), "aggressive heavy: axe kick");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ dodge into counter

    @GameTest(template = "empty", batch = "noirm_rebuild_dodge_counter", timeoutTicks = 60)
    public static void dodgeIntoCounterLandsBehind(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja dodger = ninja(helper, 1.5f, 2.0f);
        attacker.setInvulnerable(false);
        face(attacker, dodger);
        face(dodger, attacker);
        CombatState state = CombatStates.get(dodger);
        helper.assertTrue(CombatSystem.requestDodge(dodger, -1f, 0f), "the dodge starts");
        helper.assertTrue(CombatSystem.onIncomingAttack(dodger, dodger.damageSources().mobAttack(attacker)), "the attack misses the dodge");
        helper.assertTrue(state.dodgeCounterTicks() == CombatSystem.DODGE_COUNTER_WINDOW, "a perfect dodge opens the counter window");
        float yaw = attacker.getYRot();
        helper.runAfterDelay(CombatSystem.PERFECT_DODGE_TICKS + 1, () -> {
            attacker.setYRot(yaw);
            helper.assertTrue(state.dodgeCounterTicks() > 0, "still inside the window");
            helper.assertTrue(CombatSystem.requestAttack(dodger, AttackInput.LIGHT), "the counter cuts the roll short");
            helper.assertTrue(state.isDodgeCounter(), "it's a dodge counter");
            helper.assertTrue(Noirm.id("basic/counter_strike").equals(state.moveId()), "the style's counter, got " + state.moveId());
            helper.assertTrue(state.phaseLength() <= CombatSystem.DODGE_COUNTER_WINDUP, "a fast wind-up: " + state.phaseLength());
            Vec3 facing = Vec3.directionFromRotation(0, yaw);
            Vec3 offset = dodger.position().subtract(attacker.position());
            helper.assertTrue(offset.dot(facing) < -0.8, "it lands behind the enemy, offset " + offset + " facing " + facing);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(attacker.getHealth() < NINJA_HEALTH, "the counter hasn't landed yet");
            near(helper, NINJA_HEALTH - 4.5f * CombatSystem.COUNTER_DAMAGE, attacker.getHealth(), "the counter hits x1.5");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_rebuild_dodge_side", timeoutTicks = 40)
    public static void sideDodgeCountersFromTheSide(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja dodger = ninja(helper, 1.5f, 2.0f);
        face(attacker, dodger);
        float yaw = attacker.getYRot();
        CombatSystem.openDodgeCounter(dodger, attacker, 1f);
        helper.assertTrue(CombatSystem.requestAttack(dodger, AttackInput.LIGHT), "the counter starts");
        Vec3 facing = Vec3.directionFromRotation(0, yaw);
        Vec3 offset = dodger.position().subtract(attacker.position());
        helper.assertTrue(Math.abs(offset.dot(facing)) < 0.3 && offset.horizontalDistance() > 0.9, "beside the enemy, offset " + offset);
        // The window closes: later attacks are ordinary ones.
        SparringNinja late = ninja(helper, 0.5f, 2.0f);
        CombatSystem.openDodgeCounter(late, attacker, 0f);
        helper.runAfterDelay(CombatSystem.DODGE_COUNTER_WINDOW + 1, () -> {
            helper.assertTrue(CombatStates.get(late).dodgeCounterTicks() == 0, "the window closes");
            CombatSystem.requestAttack(late, AttackInput.LIGHT);
            helper.assertFalse(CombatStates.get(late).isDodgeCounter(), "no counter after it");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ dash strike and guard break

    @GameTest(template = "empty", batch = "noirm_rebuild_dash", timeoutTicks = 60)
    public static void dashStrike(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.3f);
        target.setInvulnerable(false);
        face(attacker, target);
        CombatState state = CombatStates.get(attacker);
        helper.assertTrue(CombatSystem.requestAttack(attacker, AttackInput.FORWARD_LIGHT), "the dash strike starts");
        helper.assertTrue(state.isDashStrike(), "sprinting into an attack is a dash strike");
        CombatMove backfist = CombatData.move(Noirm.id("basic/backfist"));
        near(helper, backfist.timing().windup() - CombatSystem.DASH_STRIKE_WINDUP_CUT, state.phaseLength(), "a shorter wind-up");
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < NINJA_HEALTH, "the dash strike hasn't landed yet");
            near(helper, NINJA_HEALTH - backfist.hit().damage() * CombatSystem.DASH_STRIKE_DAMAGE, target.getHealth(), "it hits x1.2");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_rebuild_guard_break", timeoutTicks = 80)
    public static void guardBreakShattersAGuard(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 1.9f);
        target.setInvulnerable(false);
        face(attacker, target);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        CombatState self = CombatStates.get(attacker);
        CombatState held = CombatStates.get(target);
        boolean[] pressed = {false, false};
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 3, () -> {
            pressed[0] = CombatSystem.requestAttack(attacker, AttackInput.LIGHT); // a guarded jab: pressing (aggressive)
        });
        helper.onEachTick(() -> {
            face(attacker, target);
            if (pressed[0] && !pressed[1] && held.posture() > 0 && CombatSystem.canAttackNow(attacker)) {
                pressed[1] = CombatSystem.requestAttack(attacker, AttackInput.HEAVY);
                helper.assertTrue(Noirm.id("basic/axe_kick").equals(self.moveId()), "pressing, the heavy is the axe kick: " + self.moveId());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pressed[1], "the axe kick started");
            helper.assertTrue(CombatSystem.lastPostureBreak(target) != Long.MIN_VALUE, "the guard shattered");
            helper.assertFalse(held.isGuarding(), "the guard is down");
            helper.assertTrue(held.phase() == CombatPhase.STAGGER, "and they reel");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        return ninja;
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.02f, what + ": expected " + expected + ", got " + actual);
    }
}

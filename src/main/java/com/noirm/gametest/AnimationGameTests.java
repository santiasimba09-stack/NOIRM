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
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * NPC animation: the keyframe files every model plays, and the server telling clients which
 * animation an NPC is playing. (What it looks like is checked in the real client.)
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimationGameTests {
    private AnimationGameTests() {}

    /** Every file in assets/noirm/player_animation, parsed with the NPC evaluator, by animation name. */
    private static Map<String, KeyframeAnimation> loadAll(GameTestHelper helper) {
        Path folder = ModList.get().getModFileById(Noirm.MOD_ID).getFile().findResource("assets", Noirm.MOD_ID, "player_animation");
        Map<String, KeyframeAnimation> all = new HashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Map<String, KeyframeAnimation> parsed = KeyframeAnimation.parse(reader);
                    helper.assertTrue(!parsed.isEmpty(), "No animation in " + file.getFileName());
                    all.putAll(parsed);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't read " + folder, e);
        }
        return all;
    }

    @GameTest(template = "empty")
    public static void everyAnimationFileParsesAndEvaluates(GameTestHelper helper) {
        Map<String, KeyframeAnimation> all = loadAll(helper);
        helper.assertTrue(all.size() >= 25, "Expected the 25 animation files, parsed " + all.size());
        SkeletonPose pose = new SkeletonPose();
        for (KeyframeAnimation animation : all.values()) {
            helper.assertTrue(animation.lengthTicks > 0, animation.name + " has no length");
            helper.assertTrue(animation.keyframeCount() > 0, animation.name + " has no keyframes");
            float length = animation.lengthTicks;
            for (float t : new float[]{-5f, 0f, 0.37f, length * 0.25f, length * 0.5f, length * 0.77f, length, length + 7.3f}) {
                pose.clear();
                animation.sample(t, pose);
                helper.assertTrue(pose.isFinite(), animation.name + " gives NaN/infinity at tick " + t);
            }
        }
        // Generated animations carry bends; the Boundless ones don't (and still sample fine).
        helper.assertTrue(all.get("elbow_strike").hasBend(Bone.RIGHT_ARM), "elbow_strike should bend the right elbow");
        helper.assertTrue(all.get("knee_strike").hasBend(Bone.RIGHT_LEG), "knee_strike should bend the right knee");
        helper.assertTrue(all.get("stance_brawler").hasBend(Bone.TORSO), "the brawler stance should hunch at the waist");
        KeyframeAnimation jab = all.get("quick_jab");
        helper.assertFalse(jab.hasBend(Bone.RIGHT_ARM), "quick_jab (Boundless) has no bends");
        pose.clear();
        jab.sample(3f, pose);
        helper.assertFalse(pose.isSet(Bone.RIGHT_ARM, SkeletonPose.BEND), "no bend channel means bend is left to the base pose");
        helper.assertTrue(pose.isSet(Bone.RIGHT_ARM, SkeletonPose.RX), "quick_jab moves the right arm");
        helper.assertFalse(pose.isSet(Bone.RIGHT_LEG, SkeletonPose.RX), "quick_jab leaves the legs to the walk");
        // Bends come out in radians: 130° at the elbow strike's wind-up key.
        KeyframeAnimation elbow = all.get("elbow_strike");
        pose.clear();
        elbow.sample(0.25f * elbow.lengthTicks, pose);
        assertNear(helper, (float) Math.toRadians(130), pose.get(Bone.RIGHT_ARM, SkeletonPose.BEND), "elbow bend at its key");
        // Linear interpolation halfway between two keys.
        pose.clear();
        elbow.sample(0.125f * elbow.lengthTicks, pose);
        assertNear(helper, (float) Math.toRadians(65), pose.get(Bone.RIGHT_ARM, SkeletonPose.BEND), "elbow bend halfway in");
        // Loops wrap: a stance one cycle later is the same pose.
        KeyframeAnimation stance = all.get("stance_basic");
        helper.assertTrue(stance.loop, "stances loop");
        SkeletonPose later = new SkeletonPose();
        pose.clear();
        stance.sample(7f, pose);
        stance.sample(7f + stance.lengthTicks, later);
        assertNear(helper, pose.get(Bone.RIGHT_ARM, SkeletonPose.RX), later.get(Bone.RIGHT_ARM, SkeletonPose.RX), "loop wraps");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyMoveAnimationExists(GameTestHelper helper) {
        Map<String, KeyframeAnimation> all = loadAll(helper);
        Path moves = ModList.get().getModFileById(Noirm.MOD_ID).getFile().findResource("data", Noirm.MOD_ID, "combat", "moves");
        int checked = 0;
        try (Stream<Path> files = Files.walk(moves)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject move = JsonParser.parseReader(reader).getAsJsonObject();
                    if (move.has("animation")) {
                        String id = move.get("animation").getAsString();
                        String name = id.substring(id.indexOf(':') + 1);
                        helper.assertTrue(all.containsKey(name), file.getFileName() + " plays missing animation " + id);
                        checked++;
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't read " + moves, e);
        }
        helper.assertTrue(checked >= 20, "Expected the moves' animations, checked " + checked);
        for (String roll : List.of("forward_roll", "backward_roll")) {
            helper.assertTrue(all.containsKey(roll), "Missing roll animation " + roll);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_npc_anim")
    public static void npcAttackSyncsItsAnimation(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper, 1.5f, 0.5f);
        int before = ninja.animationSeq();
        helper.assertTrue(CombatSystem.requestAttack(ninja, AttackInput.LIGHT), "an idle ninja can jab");
        var jab = CombatData.move(CombatData.style(CombatData.DEFAULT_STYLE).opener(AttackInput.LIGHT));
        String expected = jab.animation().orElseThrow().toString();
        helper.assertTrue(expected.equals(ninja.animationId()), "the NPC should play " + expected + ", plays " + ninja.animationId());
        helper.assertTrue(ninja.animationSeq() == before + 1, "a new move bumps the animation counter");
        int length = jab.timing().windup() + jab.timing().active() + jab.timing().recovery();
        helper.assertTrue(ninja.animationTicks() == length, "the animation lasts the move (" + length + "), got " + ninja.animationTicks());
        helper.assertTrue(ninja.isFighting(), "mid-move counts as fighting (the stance shows)");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(CombatData.DEFAULT_STYLE.toString().equals(ninja.syncedStyle()),
                    "the style should be synced for the stance, got '" + ninja.syncedStyle() + "'");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noirm_npc_anim")
    public static void npcDodgeSyncsARoll(GameTestHelper helper) {
        SparringNinja ninja = ninja(helper, 1.5f, 1.5f);
        int before = ninja.animationSeq();
        helper.assertTrue(CombatSystem.requestDodge(ninja, -1f, 0f), "an idle ninja can dodge");
        helper.assertTrue(Noirm.id("backward_roll").toString().equals(ninja.animationId()), "a back-step dodge rolls backward, got " + ninja.animationId());
        helper.assertTrue(ninja.animationSeq() == before + 1, "the roll bumps the animation counter");
        helper.runAfterDelay(10, () -> {
            // The dodge throws them back: keep the area clean for other tests.
            ninja.discard();
            helper.succeed();
        });
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        return ninja;
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 1.0e-3f, what + ": expected " + expected + ", got " + actual);
    }
}

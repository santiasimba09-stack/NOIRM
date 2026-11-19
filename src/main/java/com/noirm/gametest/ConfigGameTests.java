package com.noirm.gametest;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.bounty.BingoBook;
import com.noirm.clan.Nature;
import com.noirm.config.NoirmConfig;
import com.noirm.nature.NatureDrills;
import com.noirm.world.Destruction;
import com.noirm.world.wild.WildSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The survival config ({@code config/noirm-common.toml}, {@link NoirmConfig}): its defaults are the
 * old constants (so nothing changed for anyone), and a changed value takes effect where the
 * constant used to be. Tests that change a value run in their own batch and put it back before the
 * tick ends.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigGameTests {
    private ConfigGameTests() {}

    @GameTest(template = "empty", batch = "noirm_config_defaults")
    public static void defaultsAreTheOldConstants(GameTestHelper helper) {
        // The defaults written in the spec…
        helper.assertTrue(NoirmConfig.NPC_DAMAGE.defaultValue() == 1.0, "npc damage ×1");
        helper.assertTrue(NoirmConfig.NPC_HEALTH.defaultValue() == 1.0, "npc health ×1");
        helper.assertTrue(NoirmConfig.CHAKRA_COST.defaultValue() == 1.0, "chakra cost ×1");
        helper.assertTrue(NoirmConfig.WILD_RATE.defaultValue() == 100, "wild rate 100%");
        helper.assertTrue(NoirmConfig.WILD_MAX_GROUPS.defaultValue() == WildSpawner.MAX_GROUPS_NEAR, "wild group cap");
        helper.assertTrue(NoirmConfig.WILD_MAX_NINJAS.defaultValue() == WildSpawner.MAX_NINJAS_NEAR, "wild ninja cap");
        helper.assertTrue(NoirmConfig.WILD_SAFE_MINUTES.defaultValue() == WildSpawner.SAFE_PLAY_MINUTES, "wild safe start");
        helper.assertTrue(NoirmConfig.LEGEND_COUNT.defaultValue() == BingoBook.LEGEND_COUNT, "legends per world");
        helper.assertTrue(NoirmConfig.LEGEND_SAFE_RADIUS.defaultValue() == BingoBook.LEGEND_SAFE_RADIUS, "legend safe radius");
        helper.assertTrue(NoirmConfig.LEGEND_GRACE_MINUTES.defaultValue() == BingoBook.LEGEND_NEW_PLAYER_MINUTES, "legend grace");
        helper.assertTrue(NoirmConfig.TERRAIN_DESTRUCTION.defaultValue(), "terrain destruction on");
        helper.assertTrue(NoirmConfig.TERRAIN_REGEN_SCALE.defaultValue() == 1.0, "regen ×1");
        helper.assertTrue(NoirmConfig.GORE_ENABLED.defaultValue(), "gore on");
        helper.assertTrue(NoirmConfig.GORE_INTENSITY.defaultValue() == 1.0, "gore ×1");
        helper.assertTrue(NoirmConfig.TRAINING_SPEED.defaultValue() == 1.0, "training ×1");
        helper.assertTrue(NoirmConfig.DRILL_TIME.defaultValue() == 1.0, "drills ×1");
        helper.assertTrue(NoirmConfig.RYO_REWARDS.defaultValue() == 1.0, "ryo ×1");
        // …are the values in use (a fresh file)…
        for (NoirmConfig.Value<?> value : NoirmConfig.all()) {
            helper.assertTrue(value.get().equals(value.defaultValue()), value.path() + " is at its default: " + value.get());
        }
        // …and the game behaves as before.
        helper.assertTrue(NatureDrills.secondsNeeded(Nature.WIND) == NatureDrills.DRILL_SECONDS, "a drill still takes 3 days");
        helper.assertTrue(NoirmConfig.ryoReward(250) == 250, "mission pay unchanged");
        helper.assertTrue(WildSpawner.maxGroupsNear() == WildSpawner.MAX_GROUPS_NEAR && WildSpawner.maxNinjasNear() == WildSpawner.MAX_NINJAS_NEAR, "caps");
        helper.assertTrue(BingoBook.legendCount() == BingoBook.LEGEND_COUNT && BingoBook.legendSafeRadius() == BingoBook.LEGEND_SAFE_RADIUS, "legends");
        int rule = helper.getLevel().getGameRules().getInt(WildSpawner.RULE);
        helper.assertTrue(WildSpawner.rate(helper.getLevel()) == Math.min(WildSpawner.MAX_RATE, Math.max(0, rule)), "the rate is the gamerule's");
        int regen = helper.getLevel().getGameRules().getInt(Destruction.TERRAIN_REGEN);
        helper.assertTrue(Destruction.regenSeconds(helper.getLevel()) == Math.max(0, regen), "regen is the gamerule's seconds");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_config_change")
    public static void changedValuesTakeEffect(GameTestHelper helper) {
        var level = helper.getLevel();
        int rule = level.getGameRules().getInt(WildSpawner.RULE);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        long veteran = 100L * 60 * 60 * 20; // 100 hours played: no safe start
        try {
            // Wild ninjas: the rate multiplies the gamerule; 0 turns them off.
            NoirmConfig.WILD_RATE.override(0);
            helper.assertTrue(WildSpawner.rate(level) == 0, "rate 0 → no wild ninjas");
            helper.assertTrue(WildSpawner.attempt(level, origin, veteran, RandomSource.create(1), 40, 60) == WildSpawner.Result.DISABLED,
                    "a try is refused");
            NoirmConfig.WILD_RATE.override(200);
            helper.assertTrue(WildSpawner.rate(level) == Math.min(WildSpawner.MAX_RATE, rule * 2), "rate 200 doubles the gamerule: " + WildSpawner.rate(level));
            if (rule > 0) {
                helper.assertTrue(WildSpawner.averageMinutesBetweenGroups(WildSpawner.rate(level)) < WildSpawner.averageMinutesBetweenGroups(rule),
                        "groups turn up more often");
                // The cap: with room for no groups, a try is capped before anything spawns.
                NoirmConfig.WILD_MAX_GROUPS.override(0);
                helper.assertTrue(WildSpawner.attempt(level, origin, veteran, RandomSource.create(1), 40, 60) == WildSpawner.Result.CAPPED,
                        "the group cap applies");
            }
            // Drills, ryo, legends, terrain.
            NoirmConfig.DRILL_TIME.override(0.5);
            helper.assertTrue(NatureDrills.secondsNeeded(Nature.FIRE) == NatureDrills.DRILL_SECONDS * 0.5f, "half-length drills");
            NoirmConfig.RYO_REWARDS.override(2.0);
            helper.assertTrue(NoirmConfig.ryoReward(250) == 500, "double pay");
            NoirmConfig.LEGEND_SAFE_RADIUS.override(2000);
            helper.assertTrue(BingoBook.legendSafeRadius() == 2000, "legends kept farther out");
            NoirmConfig.TERRAIN_DESTRUCTION.override(false);
            helper.assertFalse(Destruction.enabled(level), "no terrain damage when the config turns it off");
        } finally {
            NoirmConfig.resetAll();
        }
        helper.assertTrue(WildSpawner.rate(level) == Math.min(WildSpawner.MAX_RATE, Math.max(0, rule)), "back to normal");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_config_cmd")
    public static void configCommandListsEveryOption(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_cfg_test"));
        CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        try {
            int shown = helper.getLevel().getServer().getCommands().getDispatcher().execute("noirm config", source);
            helper.assertTrue(shown == NoirmConfig.all().size(), "/noirm config lists all " + NoirmConfig.all().size() + " options: " + shown);
        } catch (Exception e) {
            throw new IllegalStateException("/noirm config failed: " + e.getMessage(), e);
        }
        CommandSourceStack plain = player.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        var parse = helper.getLevel().getServer().getCommands().getDispatcher().parse("noirm config", plain);
        helper.assertTrue(parse.getReader().canRead() || !parse.getExceptions().isEmpty() || parse.getContext().getCommand() == null,
                "and it's for operators only");
        helper.succeed();
    }
}

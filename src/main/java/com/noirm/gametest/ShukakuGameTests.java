package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.config.NoirmConfig;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TailedBeast;
import com.noirm.model.BbModel;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Shukaku, the One-Tail (docs/entities/shukaku.md): its own shape and configurable size, its moods, its idle habits
 * and its model. Its own batch: it is a big beast and wanders while it idles.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShukakuGameTests {
    private static final String BATCH = "noirm_shukaku";

    private ShukakuGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void shukakuHasItsOwnShapeAndScale(GameTestHelper helper) {
        floor(helper);
        TailedBeast one = helper.spawnWithNoFreeWill(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        one.setTails(1);
        helper.assertTrue(one.isShukaku(), "the One-Tail is Shukaku");
        float s = (float) (double) NoirmConfig.SHUKAKU_SCALE.get();
        helper.assertTrue(Math.abs(one.getBbWidth() - TailedBeast.SHUKAKU_WIDTH * s) < 0.01f
                && Math.abs(one.getBbHeight() - TailedBeast.SHUKAKU_HEIGHT * s) < 0.01f, "an upright tanuki: narrow and tall");
        try {
            NoirmConfig.SHUKAKU_SCALE.override(2.0);
            one.refreshDimensions();
            helper.assertTrue(Math.abs(one.getBbHeight() - TailedBeast.SHUKAKU_HEIGHT * 2f) < 0.01f, "creatures.shukakuScale sizes it");
        } finally {
            NoirmConfig.SHUKAKU_SCALE.reset();
            one.refreshDimensions();
        }
        helper.assertTrue(one.jutsuAffinity(Nature.WIND) > 0 && one.jutsuAffinity(Nature.FIRE) == 0, "wind, for the Drilling Air Bullet");
        TailedBeast two = helper.spawnWithNoFreeWill(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        two.setTails(2);
        helper.assertFalse(two.isShukaku(), "the Two-Tails is not");
        helper.assertTrue(two.jutsuAffinity(Nature.WIND) == 0, "and has no wind");
        cleanUp(helper);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void shukakuMoodsFollowTheFight(GameTestHelper helper) {
        floor(helper);
        TailedBeast beast = helper.spawnWithNoFreeWill(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        beast.setTails(1);
        helper.assertTrue(beast.mood() == TailedBeast.Mood.CALM, "calm at first");
        beast.setHealth(beast.getMaxHealth() * 0.3f);
        settle(beast);
        helper.assertTrue(beast.mood() == TailedBeast.Mood.TIRED, "worn down, it tires: " + beast.mood());
        beast.setHealth(beast.getMaxHealth());
        SparringNinja prey = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        beast.setTarget(prey);
        settle(beast);
        helper.assertTrue(beast.mood() == TailedBeast.Mood.ANGRY, "a foe in reach makes it angry: " + beast.mood());
        prey.setHealth(prey.getMaxHealth() * 0.2f);
        settle(beast);
        helper.assertTrue(beast.mood() == TailedBeast.Mood.MANIC, "it smells blood: " + beast.mood());
        beast.subdue();
        beast.updateMood();
        helper.assertTrue(beast.mood() == TailedBeast.Mood.SUBDUED, "subdued at once, whatever the hold");
        prey.discard();
        cleanUp(helper);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void shukakuIdleHabitsSetAndClearTheAction(GameTestHelper helper) {
        floor(helper);
        TailedBeast beast = helper.spawn(NoirmEntities.TAILED_BEAST.get(), 1.5f, 1f, 1.5f);
        beast.setTails(1);
        beast.setAction(TailedBeast.ACTION_SNIFF);
        helper.assertTrue(beast.action() == TailedBeast.ACTION_SNIFF && beast.actionStart() == (int) helper.getLevel().getGameTime(),
                "an action carries its start time");
        beast.clearAction(TailedBeast.ACTION_LAUGH);
        helper.assertTrue(beast.action() == TailedBeast.ACTION_SNIFF, "clearing another action leaves this one");
        beast.clearAction(TailedBeast.ACTION_SNIFF);
        helper.assertTrue(beast.action() == TailedBeast.ACTION_NONE, "cleared");
        beast.readyIdleHabits();
        boolean[] seen = {false};
        helper.onEachTick(() -> {
            if (beast.action() != TailedBeast.ACTION_NONE) {
                seen[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[0], "a calm Shukaku picks up an idle habit");
            helper.assertTrue(beast.action() == TailedBeast.ACTION_NONE, "and finishes it");
            beast.discard();
        });
    }

    /** The model the renderer draws: its bones (the animation finds them by name) and its two Blockbench clips. */
    @GameTest(template = "empty", batch = BATCH)
    public static void shukakuModelHasItsBonesAndClips(GameTestHelper helper) {
        BbModel model = BbModel.fromClasspath("/assets/noirm/models/bb/shukaku.bbmodel");
        helper.assertTrue(model != null, "models/bb/shukaku.bbmodel is in the mod");
        for (String bone : new String[]{"body", "belly", "chest", "head", "jaw", "brow", "lid_l", "lid_r", "ear_l", "ear_r_tip",
                "arm_l", "forearm_r", "hand_l", "leg_r", "shin_l", "foot_r", "tail1", "tail7"}) {
            helper.assertTrue(model.bone(bone) != null, "bone " + bone);
        }
        helper.assertTrue(model.animations.containsKey("drum") && model.animations.containsKey("laugh"), "the drum and laugh clips");
        helper.succeed();
    }

    private static void settle(TailedBeast beast) {
        for (int i = 0; i < 40; i++) {
            beast.updateMood();
        }
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void cleanUp(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(12);
        helper.getLevel().getEntitiesOfClass(TailedBeast.class, area).forEach(TailedBeast::discard);
        helper.succeed();
    }
}

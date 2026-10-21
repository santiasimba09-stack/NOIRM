package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.senjutsu.Meditation;
import com.noirm.training.TrainingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Meditation: when it can start, what ends it, and what it gives (chakra, Reserves XP, nature energy). */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MeditationGameTests {
    private MeditationGameTests() {}

    /** A fake (server) player standing on a stone floor in the middle of the test area. */
    private static FakePlayer sitter(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_meditator"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.setOnGround(true);
        return player;
    }

    private static ShinobiData data(FakePlayer player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void ticks(FakePlayer player, int count) {
        ShinobiData data = data(player);
        for (int i = 0; i < count; i++) {
            Meditation.tick(player, data);
        }
    }

    @GameTest(template = "empty", batch = "noirm_meditation_rules")
    public static void startingNeedsGroundAndCalm(GameTestHelper helper) {
        FakePlayer player = sitter(helper);
        player.setOnGround(false);
        helper.assertTrue(Meditation.canStart(player) == Meditation.Refusal.AIR, "can't meditate in the air");
        helper.assertFalse(Meditation.start(player), "start refused in the air");
        player.setOnGround(true);
        helper.assertTrue(Meditation.canStart(player) == Meditation.Refusal.NONE, "on the ground it's allowed");
        helper.assertTrue(Meditation.toggle(player), "toggle starts meditating");
        helper.assertTrue(Meditation.isMeditating(player), "meditating");
        helper.assertFalse(Meditation.toggle(player), "toggle again stands up");
        helper.assertFalse(Meditation.isMeditating(player), "no longer meditating");
        Meditation.onHurt(player); // took damage: now in a fight
        helper.assertTrue(Meditation.canStart(player) == Meditation.Refusal.FIGHTING, "hurt a moment ago = in a fight");
        helper.assertFalse(Meditation.start(player), "can't meditate in a fight");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_meditation_breaks")
    public static void movingCombatOrDamageEndIt(GameTestHelper helper) {
        FakePlayer player = sitter(helper);
        helper.assertTrue(Meditation.start(player), "starts");
        ticks(player, 20);
        helper.assertTrue(Meditation.isMeditating(player), "sitting still keeps meditating");
        player.setPos(player.getX() + 0.1, player.getY(), player.getZ());
        ticks(player, 1);
        helper.assertTrue(Meditation.isMeditating(player), "a tiny shift is fine");
        player.setPos(player.getX() + 1, player.getY(), player.getZ());
        ticks(player, 1);
        helper.assertFalse(Meditation.isMeditating(player), "walking away ends it");

        helper.assertTrue(Meditation.start(player), "starts again");
        CombatSystem.setCombatMode(player, true);
        ticks(player, 1);
        helper.assertFalse(Meditation.isMeditating(player), "combat mode ends it");
        CombatSystem.setCombatMode(player, false);

        helper.assertTrue(Meditation.start(player), "starts again");
        Meditation.onHurt(player);
        helper.assertFalse(Meditation.isMeditating(player), "damage ends it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_meditation_gains")
    public static void meditatingTrainsReservesAndGathersNature(GameTestHelper helper) {
        FakePlayer player = sitter(helper);
        ShinobiData data = data(player);
        data.setChakra(0f);
        data.senjutsu().setNatureEnergy(0f);
        float xpBefore = data.training().xp(TrainingSystem.Stat.RESERVES);
        helper.assertTrue(Meditation.start(player), "starts");
        ticks(player, 200); // 10 seconds
        helper.assertTrue(Meditation.isMeditating(player), "still meditating");
        float xp = data.training().xp(TrainingSystem.Stat.RESERVES) - xpBefore;
        assertNear(helper, Meditation.RESERVES_XP_PER_SECOND * 10, xp, "Reserves XP for 10 s");
        // Bare stone: the base rate (times the night bonus if it's night).
        float expected = 200 * Meditation.gainPerTick(helper.getLevel(), 1f);
        assertNear(helper, expected, data.senjutsu().natureEnergy(), "nature energy for 10 s on stone");
        float extraChakra = 200 * ShinobiData.CHAKRA_REGEN_PER_TICK * data.chakraRegenMultiplier() * (Meditation.CHAKRA_REGEN_MULTIPLIER - 1f);
        assertNear(helper, extraChakra, data.getChakra(), "chakra comes back faster");
        Meditation.stop(player);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_meditation_nature")
    public static void natureAroundSpeedsItUp(GameTestHelper helper) {
        FakePlayer player = sitter(helper);
        BlockPos center = player.blockPosition();
        int before = Meditation.countNature(helper.getLevel(), center);
        float plain = Meditation.natureFactor(helper.getLevel(), center);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.OAK_LEAVES);
            }
        }
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.POPPY);
        int after = Meditation.countNature(helper.getLevel(), center);
        helper.assertTrue(after - before == 19, "counted " + (after - before) + " natural blocks, expected 19");
        float green = Meditation.natureFactor(helper.getLevel(), center);
        helper.assertTrue(green > plain + 0.4f, "nature multiplies the gain: " + plain + " -> " + green);
        helper.assertTrue(Meditation.natureFactor(helper.getLevel(), center) <= Meditation.NATURE_MAX_MULTIPLIER, "capped");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_meditation_fade")
    public static void natureEnergyFadesWhenNotMeditating(GameTestHelper helper) {
        FakePlayer player = sitter(helper);
        ShinobiData data = data(player);
        data.senjutsu().setNatureEnergy(50f);
        ticks(player, 600); // 30 seconds
        assertNear(helper, 50f - 600 * Meditation.NATURE_FADE_PER_TICK, data.senjutsu().natureEnergy(), "fades 1 per 30 s");
        data.senjutsu().setNatureEnergy(500f);
        assertNear(helper, Meditation.NATURE_MAX, data.senjutsu().natureEnergy(), "capped at the max");
        assertNear(helper, Meditation.NATURE_MAX, Meditation.natureEnergy(player), "the API reads it");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_meditation_command")
    public static void commandToggles(GameTestHelper helper) {
        FakePlayer player = sitter(helper);
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            dispatcher.execute("noirm meditate", source);
            helper.assertTrue(Meditation.isMeditating(player), "/noirm meditate sits down");
            dispatcher.execute("noirm meditate", source);
            helper.assertFalse(Meditation.isMeditating(player), "again stands up");
            dispatcher.execute("noirm me nature 40", source);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertNear(helper, 40f, Meditation.natureEnergy(player), "/noirm me nature sets it");
        helper.succeed();
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f + Math.abs(expected) * 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

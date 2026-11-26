package com.noirm.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.economy.Ryo;
import com.noirm.entity.TrainingDummy;
import com.noirm.mission.MissionTemplate;
import com.noirm.mission.PlayerRank;
import com.noirm.nature.NatureDrills;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.progress.CombatFeatTrigger;
import com.noirm.progress.CreateJutsuTrigger;
import com.noirm.progress.DefeatTrigger;
import com.noirm.progress.FuseNaturesTrigger;
import com.noirm.progress.NoirmTriggers;
import com.noirm.progress.ProgressWatcher;
import com.noirm.registry.NoirmEntities;
import com.noirm.training.TrainingSystem;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The "Path of the Shinobi" advancement tab ({@code data/noirm/advancements/shinobi/}) and its
 * triggers ({@code com.noirm.progress}): every advancement loads and leads back to the root, and the
 * triggers grant the right advancements when the game code fires them.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressGameTests {
    private ProgressGameTests() {}

    static final ResourceLocation ROOT = Noirm.id("shinobi/root");

    /**
     * A fresh server player (a new UUID: no advancements yet) with a network handler that sends
     * nothing. Not a Forge FakePlayer: those can never earn advancements. It isn't added to the
     * world or the player list; call {@link #leave} when done.
     */
    static ServerPlayer player(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "noirm_adv_test"));
        new TestNetHandler(level.getServer(), player); // sets player.connection
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z);
        return player;
    }

    /** Stops the test player's advancements from listening. */
    static void leave(GameTestHelper helper, ServerPlayer player) {
        player.getAdvancements().stopListening();
    }

    static ShinobiData data(ServerPlayer player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    static Advancement advancement(ServerPlayer player, String path) {
        Advancement a = player.getServer().getAdvancements().getAdvancement(Noirm.id("shinobi/" + path));
        if (a == null) {
            throw new GameTestAssertException("no advancement noirm:shinobi/" + path);
        }
        return a;
    }

    static boolean done(ServerPlayer player, String path) {
        return player.getAdvancements().getOrStartProgress(advancement(player, path)).isDone();
    }

    static void assertDone(GameTestHelper helper, ServerPlayer player, String path, boolean expected) {
        helper.assertTrue(done(player, path) == expected, path + (expected ? " should be earned" : " should not be earned yet"));
    }

    // ---------------------------------------------------------------- the data

    @GameTest(template = "empty", batch = "noirm_progress")
    public static void everyAdvancementLoadsAndReachesTheRoot(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var files = server.getResourceManager().listResources("advancements",
                id -> id.getNamespace().equals(Noirm.MOD_ID) && id.getPath().endsWith(".json"));
        helper.assertTrue(files.size() >= 45, "the tab has its advancements: " + files.size());
        JsonObject lang = null;
        try (InputStream in = ProgressGameTests.class.getResourceAsStream("/assets/noirm/lang/en_us.json")) {
            if (in != null) {
                lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (ResourceLocation file : files.keySet()) {
            String path = file.getPath().substring("advancements/".length(), file.getPath().length() - ".json".length());
            Advancement a = server.getAdvancements().getAdvancement(Noirm.id(path));
            helper.assertTrue(a != null, "loaded: " + path);
            helper.assertTrue(a.getDisplay() != null, "shown in the tab: " + path);
            Advancement walk = a;
            int steps = 0;
            while (walk.getParent() != null && steps++ < 64) {
                walk = walk.getParent();
            }
            helper.assertTrue(walk.getId().equals(ROOT), path + " leads back to the root, not " + walk.getId());
            for (Map.Entry<String, Criterion> c : a.getCriteria().entrySet()) {
                var instance = c.getValue().getTrigger();
                helper.assertTrue(instance != null && CriteriaTriggers.getCriterion(instance.getCriterion()) != null,
                        path + ": criterion " + c.getKey() + " has a known trigger");
            }
            if (lang != null) {
                String key = "advancement.noirm." + path.replace('/', '.');
                helper.assertTrue(lang.has(key + ".title") && lang.has(key + ".description"), "en_us text for " + path);
            }
        }
        helper.assertTrue(server.getAdvancements().getAdvancement(ROOT).getDisplay().getBackground() != null, "the tab has a background");
        for (var trigger : NoirmTriggers.ALL) {
            helper.assertTrue((Object) CriteriaTriggers.getCriterion(trigger.getId()) == trigger, "registered: " + trigger.getId());
        }
        // The two workshop placeholders exist and wait for chakra wave part 2.
        helper.assertTrue(server.getAdvancements().getAdvancement(Noirm.id("shinobi/jutsu/create_own")) != null, "create your own jutsu");
        helper.assertTrue(server.getAdvancements().getAdvancement(Noirm.id("shinobi/natures/fuse_alone")) != null, "fuse two natures alone");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the triggers

    @GameTest(template = "empty", batch = "noirm_progress")
    public static void firstStepsAndTrainingDummy(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper);
        try {
            ProgressWatcher.check(player);
            assertDone(helper, player, "beginnings/character", true);
            assertDone(helper, player, "beginnings/handbook", false);
            data(player).guide().setOpenedHandbook(true);
            ProgressWatcher.check(player);
            assertDone(helper, player, "beginnings/handbook", true);

            TrainingDummy dummy = helper.spawn(NoirmEntities.TRAINING_DUMMY.get(), new BlockPos(1, 1, 1));
            assertDone(helper, player, "beginnings/training", false);
            TrainingSystem.onHitLanded(player, dummy, false, false); // a real hit on a dummy
            assertDone(helper, player, "beginnings/training", true);

            TrainingSystem.setStat(data(player), TrainingSystem.Stat.TAIJUTSU, 15f);
            ProgressWatcher.check(player);
            assertDone(helper, player, "beginnings/stat_15", true);
            assertDone(helper, player, "beginnings/stat_30", false);
            dummy.discard();
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_progress")
    public static void rankRyoAndMissions(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            ShinobiData data = data(player);
            data.career().addCompleted(MissionTemplate.Letter.D);
            ProgressWatcher.check(player);
            assertDone(helper, player, "missions/first", true);
            assertDone(helper, player, "missions/s_rank", false);

            int pouches = player.getInventory().countItem(com.noirm.mission.CareerItems.RYO_POUCH.get());
            helper.assertTrue(pouches >= 1, "the first mission's reward is a ryo pouch");

            Ryo.set(player, 1000);
            data.career().setRank(PlayerRank.CHUNIN); // ranks below it count too
            ProgressWatcher.check(player);
            assertDone(helper, player, "missions/ryo_1000", true);
            assertDone(helper, player, "missions/ryo_10000", false);
            assertDone(helper, player, "rank/genin", true);
            assertDone(helper, player, "rank/chunin", true);
            assertDone(helper, player, "rank/jonin", false);
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_progress")
    public static void combatClassesAndParry(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            DefeatTrigger.defeated(player, 3); // an A-class ninja beaten
            assertDone(helper, player, "combat/first_defeat", true);
            assertDone(helper, player, "combat/class_c", true);
            assertDone(helper, player, "combat/class_a", true);
            assertDone(helper, player, "combat/class_s", false);
            assertDone(helper, player, "combat/legend", false);
            assertDone(helper, player, "combat/parry", false);
            CombatFeatTrigger.justFrameParry(player);
            assertDone(helper, player, "combat/parry", true);
            DefeatTrigger.bounty(player, false);
            assertDone(helper, player, "combat/bounty", true);
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_progress")
    public static void naturesMastersAndPlaceholders(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            ShinobiData data = data(player);
            assertDone(helper, player, "natures/first_drill", false);
            NatureDrills.complete(player, data, Nature.WIND); // a drill finished
            assertDone(helper, player, "natures/first_drill", true);
            data.masters().pass("might_guy");
            ProgressWatcher.check(player);
            assertDone(helper, player, "masters/might_guy", true);
            assertDone(helper, player, "masters/stone_hermit", false);

            assertDone(helper, player, "jutsu/create_own", false);
            CreateJutsuTrigger.fire(player, "B");
            assertDone(helper, player, "jutsu/create_own", true);
            FuseNaturesTrigger.fire(player, "ice");
            assertDone(helper, player, "natures/fuse_alone", true);
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        TestKit.floor(helper);
    }
}

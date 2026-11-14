package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.entity.MasterNinja;
import com.noirm.entity.SparringNinja;
import com.noirm.nature.JutsuLearning;
import com.noirm.nature.NatureDrills;
import com.noirm.nature.NatureItems;
import com.noirm.nature.NaturePackets;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.senjutsu.Meditation;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Getting new natures and jutsu (nature/): the chakra paper, the five drills, nature scrolls,
 * teachers, jutsu scrolls, chest loot and saving. Each test has its own batch (drills read the
 * blocks around the player, so neighbours must not share a fire or a lightning rod).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureDrillGameTests {
    private NatureDrillGameTests() {}

    /** A fake (server) player on a stone floor in the middle of the test area, with only the natures given. */
    private static FakePlayer student(GameTestHelper helper, Nature... natures) {
        floor(helper);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_student"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.setOnGround(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ShinobiData data = data(player);
        data.identity().natures().clear();
        data.jutsu().clear();
        data.natureTraining().load(new CompoundTag());
        for (Nature nature : natures) {
            data.identity().addNature(nature, Clan.SECONDARY_AFFINITY);
        }
        data.setReserves(ShinobiData.DEFAULT_STAT);
        data.setChakra(data.getMaxChakra());
        return player;
    }

    private static ShinobiData data(net.minecraft.world.entity.player.Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 1e-3, what + ": expected " + expected + ", got " + actual);
    }

    /** The paper spends a little chakra, reveals 1–2 hidden natures (never ones you have) and is told to the client only then. */
    @GameTest(template = "empty", batch = "noirm_nature_paper")
    public static void chakraPaperRevealsHiddenNatures(GameTestHelper helper) {
        FakePlayer player = student(helper, Nature.FIRE);
        ShinobiData data = data(player);
        helper.assertFalse(data.natureTraining().rolled(), "a fresh (or old) save has no hidden natures yet");
        NaturePackets.Sync before = NaturePackets.Sync.of(data);
        helper.assertTrue(before.hiddenMask() == 0 && !before.revealed(), "nothing is shown before the paper");
        float chakra = data.getChakra();
        helper.assertTrue(NatureDrills.paperTest(player, data), "the paper test works with chakra");
        near(helper, chakra - NatureDrills.PAPER_CHAKRA, data.getChakra(), "the paper takes a little chakra");
        Set<Nature> hidden = data.natureTraining().hidden();
        helper.assertTrue(data.natureTraining().revealed(), "revealed");
        helper.assertTrue(hidden.size() >= 1 && hidden.size() <= 2, "1-2 hidden natures, got " + hidden);
        helper.assertFalse(hidden.contains(Nature.FIRE), "never a nature you already have");
        helper.assertTrue(NatureDrills.TRAINABLE.containsAll(hidden), "hidden natures are basic ones");
        NaturePackets.Sync after = roundTrip(NaturePackets.Sync.of(data));
        for (Nature n : hidden) {
            helper.assertTrue((after.hiddenMask() & (1 << n.ordinal())) != 0, "the client hears about " + n);
        }
        // Rolling happens once: another paper shows the same natures.
        Set<Nature> again = Set.copyOf(hidden);
        NatureDrills.paperTest(player, data);
        helper.assertTrue(again.equals(data.natureTraining().hidden()), "the hidden natures don't change");
        // Without chakra nothing happens.
        data.setChakra(1f);
        helper.assertFalse(NatureDrills.paperTest(player, data), "no chakra, no reaction");
        // The real item: hold use, the paper is used up.
        data.setChakra(data.getMaxChakra());
        ItemStack paper = new ItemStack(NatureItems.CHAKRA_PAPER.get(), 2);
        paper.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(paper.getCount() == 1, "the paper is used up");
        // A clan change that hands you your hidden natures: the next paper rolls fresh ones.
        for (Nature n : Set.copyOf(data.natureTraining().hidden())) {
            data.identity().addNature(n, Clan.SECONDARY_AFFINITY);
        }
        NatureDrills.paperTest(player, data);
        helper.assertFalse(data.natureTraining().hidden().isEmpty(), "rolled again");
        for (Nature n : data.natureTraining().hidden()) {
            helper.assertTrue(data.identity().affinity(n) == 0f, "the new hidden natures are ones you lack: " + n);
        }
        // A trained hidden nature keeps the roll (finishing your drill doesn't give a new one).
        Nature kept = data.natureTraining().hidden().iterator().next();
        data.natureTraining().setHidden(List.of(kept));
        data.identity().addNature(kept, Clan.TRAINED_AFFINITY);
        NatureDrills.ensureRolled(player, data);
        helper.assertTrue(data.natureTraining().hidden().equals(Set.of(kept)), "trained: same hidden nature");
        helper.succeed();
    }

    /** Every drill: only its own setting moves it, while meditating; hidden natures go twice as fast; finishing gives the nature and its starter. */
    @GameTest(template = "empty", batch = "noirm_nature_drills", timeoutTicks = 200)
    public static void eachDrillProgressesAndGrantsTheNature(GameTestHelper helper) {
        helper.getLevel().setWeatherParameters(6000, 0, false, false); // clear skies: rain would count as running water
        FakePlayer player = student(helper);
        ShinobiData data = data(player);
        data.natureTraining().setHidden(List.of(Nature.EARTH));
        BlockPos side = new BlockPos(0, 1, 0);
        BlockPos side2 = new BlockPos(2, 1, 2);
        for (Nature nature : NatureDrills.TRAINABLE) {
            // Nothing set up: no drill moves.
            helper.assertTrue(NatureDrills.current(player, data) == null, "no setting, no drill (before " + nature + ")");
            // Build the setting.
            switch (nature) {
                case WIND -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LEAVES));
                case FIRE -> helper.setBlock(side, Blocks.CAMPFIRE);
                case WATER -> helper.setBlock(side, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
                case LIGHTNING -> helper.setBlock(side2, Blocks.LIGHTNING_ROD);
                case EARTH -> {
                    for (int x = 0; x < 3; x++) {
                        helper.setBlock(new BlockPos(x, 1, 0), Blocks.STONE);
                        helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
                    }
                }
                default -> { }
            }
            for (Nature other : NatureDrills.TRAINABLE) {
                helper.assertTrue(NatureDrills.inSetting(player, other) == (other == nature),
                        other + " setting should be " + (other == nature) + " while setting up " + nature);
            }
            // Not meditating: no progress.
            helper.assertTrue(NatureDrills.study(player, data, 10f) == null && data.natureTraining().progress(nature) == 0f,
                    nature + ": no progress without meditating");
            helper.assertTrue(Meditation.start(player), "meditating");
            NatureDrills.study(player, data, 10f);
            float pace = nature == Nature.EARTH ? NatureDrills.HIDDEN_PACE : 1f;
            near(helper, 10f * pace, data.natureTraining().progress(nature), nature + " drill: 10 s of focus");
            helper.assertTrue(data.natureTraining().active() == nature, nature + " is the active drill");
            // Finish it.
            NatureDrills.setShare(player, data, nature, 0.999f);
            helper.assertTrue(NatureDrills.study(player, data, 10f) == nature, nature + " drill finishes");
            near(helper, Clan.TRAINED_AFFINITY, data.identity().affinity(nature), nature + " at trained strength");
            for (ResourceLocation starter : com.noirm.jutsu.JutsuData.startersFor(nature)) {
                helper.assertTrue(data.jutsu().knows(starter), nature + " starter learned: " + starter);
            }
            helper.assertTrue(data.natureTraining().progress(nature) == 0f, "finished drills are cleared");
            Meditation.stop(player);
            // Tear the setting down for the next one.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                }
            }
        }
        helper.succeed();
    }

    /** A nature scroll starts the drill and puts a quarter behind you, never finishing it. */
    @GameTest(template = "empty", batch = "noirm_nature_scrolls")
    public static void natureScrollsAccelerateButNeverFinish(GameTestHelper helper) {
        FakePlayer player = student(helper, Nature.WATER);
        ShinobiData data = data(player);
        helper.assertFalse(NatureDrills.readScroll(player, data, Nature.WATER), "a scroll for a nature you have does nothing");
        helper.assertTrue(NatureDrills.readScroll(player, data, Nature.EARTH), "an earth scroll is read");
        near(helper, NatureDrills.SCROLL_BOOST, NatureDrills.share(data, Nature.EARTH), "a quarter of the drill");
        helper.assertTrue(data.natureTraining().active() == Nature.EARTH, "it becomes the active drill");
        for (int i = 0; i < 6; i++) {
            NatureDrills.readScroll(player, data, Nature.EARTH);
        }
        near(helper, NatureDrills.SCROLL_CAP, NatureDrills.share(data, Nature.EARTH), "scrolls stop short of the end");
        helper.assertTrue(data.identity().affinity(Nature.EARTH) == 0f, "no nature from scrolls alone");
        helper.assertFalse(NatureDrills.readScroll(player, data, Nature.EARTH), "a capped drill refuses more scrolls");
        // The item is used up only when it helps.
        ItemStack scroll = NatureItems.natureScroll(Nature.FIRE);
        player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        scroll.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the fire scroll is used up");
        near(helper, NatureDrills.SCROLL_BOOST, NatureDrills.share(data, Nature.FIRE), "fire drill started");
        helper.succeed();
    }

    /** Crew mates and masters who have the nature teach you: faster, even without the drill's setting. */
    @GameTest(template = "empty", batch = "noirm_nature_teachers")
    public static void crewMatesAndMastersTeachFaster(GameTestHelper helper) {
        FakePlayer player = student(helper);
        ShinobiData data = data(player);
        data.natureTraining().setHidden(List.of());
        data.natureTraining().setActive(Nature.WIND); // the ninja also has water; we're working on wind
        helper.assertTrue(Meditation.start(player), "meditating");
        SparringNinja stranger = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        stranger.joinClan(TestClans.PLAIN);
        stranger.sealBeast(0);
        stranger.learnNature(Nature.WIND, Clan.SECONDARY_AFFINITY);
        helper.assertTrue(NatureDrills.teacher(player, Nature.WIND) == 0, "a stranger doesn't teach you");
        helper.assertTrue(NatureDrills.study(player, data, 10f) == null && NatureDrills.share(data, Nature.WIND) == 0f, "no setting, no teacher: nothing");
        stranger.joinCrew(player.getUUID());
        helper.assertTrue(NatureDrills.teacher(player, Nature.WIND) == 1, "a crew mate with wind teaches it");
        helper.assertTrue(NatureDrills.teacher(player, Nature.FIRE) == 0, "but not a nature they lack");
        NatureDrills.study(player, data, 10f);
        near(helper, 10f * NatureDrills.CREW_TEACHER_PACE, data.natureTraining().progress(Nature.WIND), "crew mate: ×1.5, no setting needed");
        helper.assertTrue(data.natureTraining().teacher() == 1, "the HUD hears about the crew teacher");
        // A master who has it beats the crew mate.
        MasterNinja master = helper.spawnWithNoFreeWill(NoirmEntities.MASTER.get(), 2.5f, 1f, 2.5f);
        master.setMaster(com.noirm.master.MasterData.MIGHT_GUY);
        master.learnNature(Nature.WIND, Clan.PRIMARY_AFFINITY);
        helper.assertTrue(NatureDrills.teacher(player, Nature.WIND) == 2, "a master teaches");
        float before = data.natureTraining().progress(Nature.WIND);
        NatureDrills.study(player, data, 10f);
        near(helper, before + 10f * NatureDrills.MASTER_TEACHER_PACE, data.natureTraining().progress(Nature.WIND), "master: ×2");
        // Out of range: back to nothing.
        stranger.discard();
        master.discard();
        before = data.natureTraining().progress(Nature.WIND);
        NatureDrills.study(player, data, 10f);
        near(helper, before, data.natureTraining().progress(Nature.WIND), "teachers gone: paused");
        Meditation.stop(player);
        helper.succeed();
    }

    /** Jutsu scrolls teach only jutsu of your natures, of their rank, and only if your Reserves (and the rank hook) allow. */
    @GameTest(template = "empty", batch = "noirm_jutsu_scrolls")
    public static void jutsuScrollsTeachOnlyAllowedJutsu(GameTestHelper helper) {
        FakePlayer player = student(helper, Nature.FIRE);
        ShinobiData data = data(player);
        // C rank: the fire C jutsu, one after another, then nothing left.
        ResourceLocation first = JutsuLearning.readScroll(player, data, "C", null);
        helper.assertTrue(first != null, "a C scroll teaches a C jutsu");
        helper.assertTrue(com.noirm.jutsu.JutsuData.get(first).nature().orElse(null) == Nature.FIRE, "of your nature: " + first);
        int guard = 0;
        while (JutsuLearning.readScroll(player, data, "C", null) != null && guard++ < 20) {
            // keep reading until none is left
        }
        helper.assertTrue(JutsuLearning.candidates(data, "C").isEmpty(), "every fire C jutsu learned");
        helper.assertTrue(JutsuLearning.readScroll(player, data, "C", null) == null, "then the scroll is kept");
        for (ResourceLocation id : data.jutsu().known()) {
            var def = com.noirm.jutsu.JutsuData.get(id);
            helper.assertTrue(def.nature().orElse(null) == Nature.FIRE && "C".equals(def.rank()), "only fire C jutsu: " + id);
        }
        // B rank needs Reserves.
        helper.assertTrue(JutsuLearning.rankBlocker(player, data, "B") != null, "B needs Reserves");
        helper.assertTrue(JutsuLearning.readScroll(player, data, "B", null) == null, "refused at Reserves 10");
        data.setReserves(JutsuLearning.RESERVES_FOR_RANK[3]);
        data.career().setRank(com.noirm.mission.PlayerRank.CHUNIN); // B jutsu also need chūnin (player rank gate)
        ResourceLocation b = JutsuLearning.readScroll(player, data, "B", null);
        helper.assertTrue(b != null && "B".equals(com.noirm.jutsu.JutsuData.get(b).rank()), "B learned at Reserves 15: " + b);
        // A named scroll of a nature you lack is refused.
        helper.assertTrue(JutsuLearning.readScroll(player, data, "C", Noirm.id("suiton/water_bullet")) == null, "no water: refused");
        helper.assertFalse(data.jutsu().knows(Noirm.id("suiton/water_bullet")), "not learned");
        // Eye and beast techniques never come from scrolls.
        helper.assertTrue(JutsuLearning.readScroll(player, data, "S", Noirm.id("mangekyo/amaterasu")) == null, "no Amaterasu from paper");
        // The rank hook (player ranks plug in here).
        data.setReserves(100f);
        var old = JutsuLearning.extraRankGate;
        try {
            JutsuLearning.extraRankGate = (p, rank) -> "A".equals(rank) ? net.minecraft.network.chat.Component.literal("jonin only") : null;
            helper.assertTrue(JutsuLearning.readScroll(player, data, "A", null) == null, "the rank hook can refuse");
        } finally {
            JutsuLearning.extraRankGate = old;
        }
        // The item: a named scroll teaches exactly that and is used up.
        data.identity().addNature(Nature.WATER, Clan.SECONDARY_AFFINITY);
        ItemStack scroll = NatureItems.jutsuScroll("C", Noirm.id("suiton/water_bullet"));
        player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        scroll.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.jutsu().knows(Noirm.id("suiton/water_bullet")), "the named scroll teaches its jutsu");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "and is used up");
        helper.succeed();
    }

    /** Hidden natures, revealed state, progress and the active drill survive saving; old saves roll sensibly. */
    @GameTest(template = "empty", batch = "noirm_nature_save")
    public static void natureTrainingSavesAndLoads(GameTestHelper helper) {
        FakePlayer player = student(helper, Nature.FIRE, Nature.WATER, Nature.WIND, Nature.EARTH);
        ShinobiData data = data(player);
        // An old save: nothing rolled; the roll only picks natures you lack (here: lightning).
        NatureDrills.ensureRolled(player, data);
        helper.assertTrue(data.natureTraining().hidden().equals(Set.of(Nature.LIGHTNING)), "only lightning is left: " + data.natureTraining().hidden());
        data.natureTraining().setRevealed(true);
        NatureDrills.setShare(player, data, Nature.LIGHTNING, 0.4f);
        CompoundTag saved = data.save();
        ShinobiData copy = new ShinobiData();
        copy.load(saved);
        helper.assertTrue(copy.natureTraining().rolled() && copy.natureTraining().revealed(), "rolled and revealed kept");
        helper.assertTrue(copy.natureTraining().hidden().equals(Set.of(Nature.LIGHTNING)), "hidden kept");
        near(helper, 0.4f, NatureDrills.share(copy, Nature.LIGHTNING), "progress kept");
        helper.assertTrue(copy.natureTraining().active() == Nature.LIGHTNING, "active drill kept");
        // A save from before this feature (no NatureTraining tag) loads clean.
        CompoundTag old = data.save();
        old.remove("NatureTraining");
        ShinobiData legacy = new ShinobiData();
        legacy.load(old);
        helper.assertFalse(legacy.natureTraining().rolled(), "old saves roll later (with a hint)");
        helper.assertTrue(legacy.natureTraining().allProgress().isEmpty(), "no progress");
        helper.succeed();
    }

    /** Chests in dungeons, temples and ruins can hold chakra paper and scrolls (the loot modifier is wired). */
    @GameTest(template = "empty", batch = "noirm_nature_loot")
    public static void chestsCanHoldChakraPaper(GameTestHelper helper) {
        LootTable table = helper.getLevel().getServer().getLootData().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1))).create(LootContextParamSets.CHEST);
        int papers = 0;
        for (int i = 0; i < 100; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(NatureItems.CHAKRA_PAPER.get())) {
                    papers++;
                }
            }
        }
        helper.assertTrue(papers > 0, "100 dungeon chests hold some chakra paper");
        helper.succeed();
    }

    private static NaturePackets.Sync roundTrip(NaturePackets.Sync sync) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sync.encode(buf);
        return NaturePackets.Sync.decode(buf);
    }
}

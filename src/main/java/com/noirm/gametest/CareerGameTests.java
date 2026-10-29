package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.economy.Ryo;
import com.noirm.economy.RyoPouchItem;
import com.noirm.economy.Shop;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.VillageMerchant;
import com.noirm.gear.NoirmGear;
import com.noirm.mission.CareerData;
import com.noirm.mission.CareerItems;
import com.noirm.mission.Exams;
import com.noirm.mission.Mission;
import com.noirm.mission.MissionTemplate;
import com.noirm.mission.MissionTemplates;
import com.noirm.mission.Missions;
import com.noirm.mission.PlayerRank;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.weapon.NinjaBlades;
import com.noirm.weapon.NinjaWeapons;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Ryo, shops in ryo, missions (every kind: taken, tracked, done, failed), rank points, exams and rank gates. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CareerGameTests {
    private CareerGameTests() {}

    // ---------------------------------------------------------------- helpers

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        return player;
    }

    private static CareerData career(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new).career();
    }

    private static BlockPos board(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 1, 1));
    }

    /** Starts mission {@code id} (rank permitting) at the test's board. */
    private static Mission start(GameTestHelper helper, Player player, String id) {
        var why = Missions.accept(player, Noirm.id(id), board(helper));
        helper.assertTrue(why == null, "mission " + id + " starts: " + (why == null ? "" : why.getString()));
        Mission m = Missions.active(player);
        helper.assertTrue(m != null, "and is active");
        return m;
    }

    /** A spot {@code dx}, {@code dz} from the test's corner, on the test's floor level. */
    private static BlockPos near(GameTestHelper helper, int dx, int dz) {
        return helper.absolutePos(new BlockPos(1 + dx, 1, 1 + dz));
    }

    private static void moveTo(Player player, BlockPos pos) {
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static List<SparringNinja> foes(GameTestHelper helper, Mission m) {
        ServerLevel level = helper.getLevel();
        return m.foes.stream().map(level::getEntity).filter(e -> e instanceof SparringNinja).map(e -> (SparringNinja) e).toList();
    }

    private static void beat(List<SparringNinja> foes, Player by) {
        for (SparringNinja n : foes) {
            n.invulnerableTime = 0;
            n.hurt(by == null ? n.damageSources().generic() : n.damageSources().playerAttack(by), 100000f);
            if (n.isAlive()) {
                n.kill();
            }
        }
    }

    /** Removes everything a career test spawned (rogues, clients, pouches) within {@code radius}. */
    private static void cleanup(GameTestHelper helper, int radius) {
        AABB area = new AABB(board(helper)).inflate(radius, 256, radius);
        helper.getLevel().getEntitiesOfClass(Entity.class, area, e -> !(e instanceof Player)
                && (e instanceof LivingEntity || e instanceof ItemEntity || e.getTags().contains(Missions.MISSION_TAG))).forEach(Entity::discard);
        helper.succeed();
    }

    // ---------------------------------------------------------------- ryo

    @GameTest(template = "empty", batch = "noirm_career_ryo")
    public static void ryoGiveTakeSaveAndPouches(GameTestHelper helper) {
        Player player = player(helper);
        helper.assertTrue(Ryo.balance(player) == 0, "a new ninja has no ryo");
        helper.assertTrue(Ryo.give(player, 250) == 250, "give adds");
        helper.assertFalse(Ryo.take(player, 300), "can't take more than you have");
        helper.assertTrue(Ryo.balance(player) == 250, "and nothing was taken");
        helper.assertTrue(Ryo.take(player, 100) && Ryo.balance(player) == 150, "take removes");
        helper.assertFalse(Ryo.give(player, -50) != 150, "negative gifts do nothing");
        // Saved and loaded with the player.
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        data.career().setRank(PlayerRank.CHUNIN);
        data.career().setPoints(170);
        ShinobiData copy = new ShinobiData();
        copy.load(data.save());
        helper.assertTrue(copy.career().ryo() == 150 && copy.career().rank() == PlayerRank.CHUNIN && copy.career().points() == 170,
                "ryo, rank and points survive saving");
        // Withdraw a pouch; a pouch goes back in.
        helper.assertTrue(Ryo.withdraw(player, 100) && Ryo.balance(player) == 50, "withdraw takes it out");
        ItemStack pouch = player.getInventory().items.stream().filter(Ryo::isPouch).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(RyoPouchItem.amount(pouch) == 100, "as a 100-ryo pouch");
        helper.assertFalse(Ryo.withdraw(player, 500), "can't withdraw more than you have");
        RyoPouchItem.deposit(player, pouch);
        helper.assertTrue(Ryo.balance(player) == 150, "a pouch deposits its ryo");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_career_ryo_cmd")
    public static void ryoAndRankCommands(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_ryo_test"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z);
        CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            dispatcher.execute("noirm ryo set 1000", source);
            helper.assertTrue(Ryo.balance(player) == 1000, "ryo set");
            dispatcher.execute("noirm ryo take 300", source);
            dispatcher.execute("noirm ryo give 50", source);
            helper.assertTrue(Ryo.balance(player) == 750, "take and give, got " + Ryo.balance(player));
            dispatcher.execute("noirm rank set chunin", source);
            helper.assertTrue(PlayerRank.of(player) == PlayerRank.CHUNIN && career(player).points() >= 150, "rank set");
            dispatcher.execute("noirm rank", source);
            dispatcher.execute("noirm mission list", source);
            dispatcher.execute("noirm me", source);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- shops

    @GameTest(template = "empty", batch = "noirm_career_shop")
    public static void shopsPriceInRyoWithRankGates(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        VillageMerchant smith = NoirmEntities.VILLAGE_MERCHANT.get().create(helper.getLevel());
        BlockPos home = helper.absolutePos(new BlockPos(1, 1, 1));
        smith.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        smith.setup(VillageMerchant.Role.MERCHANT, VillageMerchant.SHOP_WEAPONS, home, home, 4);
        helper.getLevel().addFreshEntity(smith);
        List<Shop.Row> rows = Shop.rows(smith, player);
        int kunai = index(rows, NinjaWeapons.KUNAI.get(), false);
        int katana = index(rows, NinjaBlades.KATANA.get(), false);
        int iron = index(rows, Items.IRON_INGOT, true);
        helper.assertTrue(kunai >= 0 && rows.get(kunai).price() == 2 * Shop.RYO_PER_EMERALD, "kunai cost 80 ryo");
        helper.assertTrue(katana >= 0 && rows.get(katana).need() == PlayerRank.CHUNIN, "a katana needs chūnin");
        helper.assertTrue(index(rows, NoirmGear.JONIN_VEST.get(), false) >= 0, "the smith keeps a jōnin vest for jōnin");
        helper.assertTrue(rows.stream().noneMatch(r -> !r.sell() && r.price() <= 0), "everything has a price");
        helper.assertTrue(iron >= 0 && rows.get(iron).price() == Shop.RYO_PER_EMERALD_SOLD, "they buy iron for ryo");

        helper.assertFalse(Shop.trade(player, smith, kunai), "no ryo, no kunai");
        Ryo.give(player, 1000);
        helper.assertTrue(Shop.trade(player, smith, kunai) && Ryo.balance(player) == 920, "kunai bought for 80");
        helper.assertTrue(Shop.count(player, NinjaWeapons.KUNAI.get()) == 4, "four kunai in the bag");
        helper.assertFalse(Shop.trade(player, smith, katana), "an academy student can't buy a katana");
        helper.assertTrue(com.noirm.nature.JutsuLearning.extraRankGate.apply(player, "B") != null
                && com.noirm.nature.JutsuLearning.extraRankGate.apply(player, "C") == null, "B jutsu need a rank, C don't");
        career(player).setRank(PlayerRank.CHUNIN);
        helper.assertTrue(Shop.trade(player, smith, katana), "a chūnin can");
        helper.assertTrue(PlayerRank.jutsuGate(player, "B") == null && PlayerRank.jutsuGate(player, "A") != null, "a chūnin learns B jutsu, not A");
        helper.assertTrue(PlayerRank.atLeast(player, PlayerRank.GENIN) && !PlayerRank.atLeast(player, PlayerRank.JONIN), "atLeast works");
        // Selling: iron, and loot (the katana goes back for a quarter of its price).
        int before = Ryo.balance(player);
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        helper.assertTrue(Shop.trade(player, smith, iron) && Ryo.balance(player) == before + Shop.RYO_PER_EMERALD_SOLD, "sold iron");
        List<Shop.Row> now = Shop.rows(smith, player);
        int sellKatana = index(now, NinjaBlades.KATANA.get(), true);
        helper.assertTrue(sellKatana >= 0, "the smith buys the katana back");
        helper.assertTrue(Shop.trade(player, smith, sellKatana) && Shop.count(player, NinjaBlades.KATANA.get()) == 0, "sold it");
        smith.discard();
        helper.succeed();
    }

    private static int index(List<Shop.Row> rows, net.minecraft.world.item.Item item, boolean sell) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).sell() == sell && rows.get(i).stack().is(item)) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- the board and rank gates

    @GameTest(template = "empty", batch = "noirm_career_board")
    public static void boardOffersAndRankGates(GameTestHelper helper) {
        Player player = player(helper);
        BlockPos board = board(helper);
        List<ResourceLocation> offers = Missions.offers(helper.getLevel(), board);
        helper.assertTrue(offers.size() == 9, "9 missions on offer, got " + offers.size());
        helper.assertTrue(offers.stream().filter(id -> MissionTemplates.get(id).rank() == MissionTemplate.Letter.D).count() * 10 >= 30,
                "enough D missions in a day for the genin exam's 30 points");
        helper.assertTrue(offers.equals(Missions.offers(helper.getLevel(), board)), "the same all day");
        helper.assertTrue(offers.stream().noneMatch(id -> MissionTemplates.get(id).exam()), "never an exam mission");
        helper.assertTrue(MissionTemplates.all().size() >= 18, "every mission loaded, got " + MissionTemplates.all().size());
        for (MissionTemplate.Letter letter : MissionTemplate.Letter.values()) {
            helper.assertTrue(offers.stream().anyMatch(id -> MissionTemplates.get(id).rank() == letter), "a " + letter + " mission is offered");
        }
        int c = 0;
        while (MissionTemplates.get(offers.get(c)).rank() != MissionTemplate.Letter.C) {
            c++;
        }
        helper.assertTrue(Missions.acceptOffer(player, board, c) != null, "an academy student can't take a C mission");
        career(player).setRank(PlayerRank.GENIN);
        helper.assertTrue(Missions.acceptOffer(player, board, c) == null, "a genin can");
        helper.assertTrue(Missions.acceptOffer(player, board, 0) != null, "one mission at a time");
        Missions.abandon(player);
        helper.assertTrue(Missions.acceptOffer(player, board, c) != null, "an offer is taken once a day");
        cleanup(helper, 8);
    }

    // ---------------------------------------------------------------- each kind of mission

    @GameTest(template = "empty", batch = "noirm_career_hunt", timeoutTicks = 100)
    public static void huntMissionSpawnsTrackPaysAndRanks(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        Mission m = start(helper, player, "d_bandit_hunt");
        helper.assertTrue(m.points.size() == 1 && Math.sqrt(m.board.distSqr(m.points.get(0))) >= 70, "the area is out in the wild");
        Missions.update(player);
        helper.assertFalse(m.spawned, "nobody there until you come near");
        m.points.set(0, near(helper, 6, 0));
        Missions.update(player);
        List<SparringNinja> rogues = foes(helper, m);
        helper.assertTrue(m.spawned && !rogues.isEmpty() && m.required == rogues.size(), "the rogues turn up, " + rogues.size());
        helper.assertTrue(rogues.get(0).isPersistenceRequired() && rogues.get(0).getTags().contains(Missions.MISSION_TAG), "kept and tagged");
        beat(rogues, player);
        Missions.update(player);
        helper.assertTrue(m.stage == Mission.Stage.RETURN && m.defeated == m.required, "objective done, report back");
        helper.assertTrue(Missions.report(player, board(helper)), "reported");
        helper.assertTrue(Missions.active(player) == null, "the mission is over");
        helper.assertTrue(Ryo.balance(player) == 120, "paid 120 ryo, got " + Ryo.balance(player));
        helper.assertTrue(career(player).points() == 10 && career(player).completed(MissionTemplate.Letter.D) == 1, "+10 rank points");
        List<ItemEntity> pouches = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(board(helper)).inflate(64, 256, 64),
                e -> Ryo.isPouch(e.getItem()));
        helper.assertTrue(!pouches.isEmpty() && RyoPouchItem.amount(pouches.get(0).getItem()) >= 5, "a beaten rogue dropped a ryo pouch; items near: "
                + helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(board(helper)).inflate(64)).stream()
                .map(e -> e.getItem().toString()).toList() + ", rogues " + rogues.stream().map(r -> r.isAlive() + "@" + r.blockPosition()).toList());
        cleanup(helper, 24);
    }

    /** Hunt rogues that were in unloaded chunks when the mission failed never come back with their chunk. */
    @GameTest(template = "empty", batch = "noirm_career_leftovers", timeoutTicks = 100)
    public static void leftoversOfAFailedMissionNeverComeBack(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        Player player = player(helper);
        Mission m = start(helper, player, "d_bandit_hunt");
        m.points.set(0, near(helper, 6, 0));
        Missions.update(player);
        List<SparringNinja> rogues = foes(helper, m);
        helper.assertTrue(!rogues.isEmpty(), "the rogues turn up");
        SparringNinja away = rogues.get(0);
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        away.save(saved);
        away.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // their chunk unloads
        Entity back = net.minecraft.world.entity.EntityType.loadEntityRecursive(saved, level, e -> e);
        level.addLegacyChunkEntities(java.util.stream.Stream.of(back)); // ...and loads again (from disk)
        helper.assertTrue(level.getEntity(away.getUUID()) == back, "while the mission runs, they come back with their chunk");
        back.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // out of reach again
        Missions.abandon(player); // the mission fails: what's loaded goes, this one can't be found
        Entity again = net.minecraft.world.entity.EntityType.loadEntityRecursive(saved, level, e -> e);
        level.addLegacyChunkEntities(java.util.stream.Stream.of(again));
        helper.assertTrue(level.getEntity(away.getUUID()) == null, "after it failed, the leftover never comes back");
        helper.assertTrue(rogues.stream().skip(1).noneMatch(Entity::isAlive), "and the loaded ones went when it failed");
        cleanup(helper, 24);
    }

    @GameTest(template = "empty", batch = "noirm_career_escort", timeoutTicks = 100)
    public static void escortMissionFollowsArrivesOrFails(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        Mission m = start(helper, player, "d_walk_the_merchant");
        Entity client = helper.getLevel().getEntity(m.npc);
        helper.assertTrue(client instanceof VillageMerchant && client.distanceTo(player) < 3, "the client stands by you");
        m.points.set(0, near(helper, 4, 0));
        Missions.update(player);
        helper.assertTrue(m.stage == Mission.Stage.RETURN && !client.isAlive(), "they arrived and went on their way");
        helper.assertTrue(Missions.report(player, board(helper)) && Ryo.balance(player) == 100, "paid 100");
        // Second time: the client dies, the mission fails, half the points go.
        career(player).setPoints(20);
        Mission again = start(helper, player, "d_walk_the_merchant");
        LivingEntity second = (LivingEntity) helper.getLevel().getEntity(again.npc);
        second.hurt(second.damageSources().generic(), 1000f);
        helper.assertTrue(Missions.active(player) == null, "a dead client fails the mission");
        helper.assertTrue(career(player).points() == 15, "half its points lost, got " + career(player).points());
        cleanup(helper, 8);
    }

    @GameTest(template = "empty", batch = "noirm_career_deliver", timeoutTicks = 100)
    public static void deliveryMissionHandsTheParcelOverOrRunsOutOfTime(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        Mission m = start(helper, player, "d_errand");
        helper.assertTrue(m.deadline > helper.getLevel().getGameTime(), "it's against the clock");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(s -> s.is(CareerItems.MISSION_PARCEL.get())), "you carry the parcel");
        m.points.set(0, near(helper, 1, 0));
        Missions.update(player);
        Entity recipient = helper.getLevel().getEntity(m.npc);
        helper.assertTrue(m.spawned && recipient instanceof VillageMerchant && recipient.isCurrentlyGlowing(), "the recipient waits, glowing");
        moveTo(player, recipient.blockPosition());
        Missions.update(player);
        helper.assertTrue(m.stage == Mission.Stage.RETURN, "handed over");
        helper.assertTrue(player.getInventory().items.stream().noneMatch(s -> s.is(CareerItems.MISSION_PARCEL.get())), "the parcel is gone");
        helper.assertTrue(Missions.report(player, board(helper)) && Ryo.balance(player) == 80, "paid 80");
        // Too slow: the clock runs out.
        Mission late = start(helper, player, "d_errand");
        late.deadline = helper.getLevel().getGameTime() - 1;
        Missions.update(player);
        helper.assertTrue(Missions.active(player) == null, "out of time fails it");
        helper.assertTrue(player.getInventory().items.stream().noneMatch(s -> s.is(CareerItems.MISSION_PARCEL.get())), "and takes the parcel back");
        cleanup(helper, 8);
    }

    @GameTest(template = "empty", batch = "noirm_career_scout")
    public static void scoutMissionVisitsEachPoint(GameTestHelper helper) {
        Player player = player(helper);
        Mission m = start(helper, player, "d_patrol_outskirts");
        helper.assertTrue(m.points.size() == 3, "three points to scout");
        for (int i = 0; i < 3; i++) {
            m.points.set(i, near(helper, 30 + i * 30, 0));
        }
        Missions.update(player);
        helper.assertTrue(m.index == 0, "not there yet");
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(m.target().equals(m.points.get(i)), "the arrow points at point " + (i + 1));
            moveTo(player, m.points.get(i));
            Missions.update(player);
        }
        helper.assertTrue(m.stage == Mission.Stage.RETURN, "all scouted");
        helper.assertTrue(m.target().equals(m.board), "now the arrow points home");
        helper.assertTrue(Missions.report(player, board(helper)) && Ryo.balance(player) == 90, "paid 90");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_career_retrieve", timeoutTicks = 100)
    public static void retrieveMissionPitchesACampWithTheScroll(GameTestHelper helper) {
        Player player = player(helper);
        career(player).setRank(PlayerRank.GENIN);
        Mission m = start(helper, player, "c_stolen_scroll");
        BlockPos camp = near(helper, 12, 12);
        m.points.set(0, camp);
        moveTo(player, near(helper, 0, 0));
        Missions.update(player);
        helper.assertTrue(m.spawned, "the camp is pitched");
        BlockPos at = m.points.get(0);
        BlockPos chestPos = at.offset(2, 0, 1);
        helper.assertTrue(helper.getLevel().getBlockEntity(chestPos) instanceof ChestBlockEntity, "with a chest");
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
        ItemStack scroll = chest.getItem(13);
        helper.assertTrue(scroll.is(CareerItems.MISSION_SCROLL.get()), "the scroll is inside");
        helper.assertTrue(!foes(helper, m).isEmpty(), "and guarded");
        chest.setItem(13, ItemStack.EMPTY);
        player.getInventory().add(scroll);
        Missions.update(player);
        helper.assertTrue(m.stage == Mission.Stage.RETURN, "scroll in hand: report back");
        helper.assertTrue(Missions.report(player, board(helper)) && Ryo.balance(player) == 300 && career(player).points() == 25, "paid 300, +25");
        helper.assertTrue(player.getInventory().items.stream().noneMatch(s -> s.is(CareerItems.MISSION_SCROLL.get())), "the scroll is handed in");
        // Tidy the camp away.
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, 3, 4))) {
            if (!helper.getLevel().getBlockState(p).isAir() && p.getY() >= at.getY()) {
                helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        cleanup(helper, 32);
    }

    @GameTest(template = "empty", batch = "noirm_career_defend", timeoutTicks = 100)
    public static void defendMissionSendsWavesAndFailsIfYouLeave(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        career(player).setRank(PlayerRank.CHUNIN);
        Mission m = start(helper, player, "b_raid");
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(m.nextAt > now, "a countdown before the first wave");
        Missions.update(player);
        helper.assertTrue(m.wave == 0, "nobody yet");
        for (int wave = 1; wave <= 2; wave++) {
            m.nextAt = helper.getLevel().getGameTime();
            Missions.update(player);
            helper.assertTrue(m.wave == wave && !m.foes.isEmpty(), "wave " + wave + " comes");
            beat(foes(helper, m), player);
            Missions.update(player);
        }
        helper.assertTrue(m.stage == Mission.Stage.RETURN, "both waves beaten");
        helper.assertTrue(Missions.report(player, board(helper)) && Ryo.balance(player) == 750, "paid 750");
        // Walking away fails it.
        start(helper, player, "b_raid");
        moveTo(player, near(helper, 200, 0));
        Missions.update(player);
        helper.assertTrue(Missions.active(player) == null, "leaving the village fails a defence");
        cleanup(helper, 48);
    }

    // ---------------------------------------------------------------- exams and ranks

    @GameTest(template = "empty", batch = "noirm_career_genin_exam", timeoutTicks = 100)
    public static void geninTestUnlocksWithPointsAndPassesNonLethally(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        BlockPos board = board(helper);
        helper.assertTrue(Exams.blocker(player) != null, "no exam without points");
        helper.assertFalse(Exams.start(player, board), "won't start");
        career(player).setPoints(30);
        helper.assertTrue(Exams.blocker(player) == null, "30 points unlock the genin test");
        helper.assertTrue(Exams.start(player, board), "it starts");
        CareerData.Exam exam = career(player).exam();
        SparringNinja examiner = (SparringNinja) helper.getLevel().getEntity(exam.examiner);
        helper.assertTrue(examiner != null && examiner.getTags().contains(Exams.EXAMINER_TAG), "a chūnin examiner steps up");
        helper.assertTrue(Missions.accept(player, Noirm.id("d_errand"), board) != null, "no missions during an exam");
        examiner.invulnerableTime = 0;
        examiner.hurt(examiner.damageSources().playerAttack(player), 100000f);
        helper.assertTrue(PlayerRank.of(player) == PlayerRank.GENIN, "worn down: you're a genin");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(s -> s.is(NoirmGear.FOREHEAD_PROTECTOR_NAVY.get())), "with a forehead protector");
        helper.assertTrue(career(player).exam() == null, "the exam is over");
        cleanup(helper, 12);
    }

    @GameTest(template = "empty", batch = "noirm_career_chunin_exam", timeoutTicks = 100)
    public static void chuninExamFailsThenWinsTwoRounds(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        BlockPos board = board(helper);
        career(player).setRank(PlayerRank.GENIN);
        career(player).setPoints(150);
        helper.assertTrue(Exams.start(player, board), "the chūnin exam starts");
        SparringNinja rival = (SparringNinja) helper.getLevel().getEntity(career(player).exam().examiner);
        player.invulnerableTime = 0;
        player.hurt(rival.damageSources().mobAttack(rival), 100000f);
        helper.assertTrue(player.isAlive() && player.getHealth() >= 1f, "nobody dies: " + player.getHealth());
        helper.assertTrue(career(player).exam() == null && PlayerRank.of(player) == PlayerRank.GENIN, "beaten: failed, still genin");
        helper.assertTrue(Exams.blocker(player) != null, "and must wait to retake it");
        helper.assertFalse(rival.isAlive() && !rival.isRemoved(), "the rival left");
        // Retake: two rounds.
        career(player).setExamCooldownUntil(0);
        player.setHealth(player.getMaxHealth());
        helper.assertTrue(Exams.start(player, board), "retaken");
        for (int round = 0; round < 2; round++) {
            CareerData.Exam exam = career(player).exam();
            helper.assertTrue(exam != null && exam.round == round, "round " + (round + 1));
            SparringNinja opponent = (SparringNinja) helper.getLevel().getEntity(exam.examiner);
            opponent.invulnerableTime = 0;
            opponent.hurt(opponent.damageSources().generic(), 100000f);
            helper.assertTrue(opponent.isAlive(), "only your blows count");
            opponent.invulnerableTime = 0;
            opponent.hurt(opponent.damageSources().playerAttack(player), 100000f);
        }
        helper.assertTrue(PlayerRank.of(player) == PlayerRank.CHUNIN, "won the tournament: chūnin");
        cleanup(helper, 12);
    }

    @GameTest(template = "empty", batch = "noirm_career_jonin_exam", timeoutTicks = 100)
    public static void joninTrialIsASoloMissionThenAFight(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        BlockPos board = board(helper);
        career(player).setRank(PlayerRank.CHUNIN);
        career(player).setPoints(500);
        // Someone else finishes the rogue: the solo trial fails.
        helper.assertTrue(Exams.start(player, board), "the jōnin trial starts");
        Mission trial = Missions.active(player);
        helper.assertTrue(trial != null && trial.template.equals(MissionTemplates.JONIN_TRIAL), "with the solo mission");
        trial.points.set(0, near(helper, 6, 0));
        Missions.update(player);
        beat(foes(helper, trial), null);
        helper.assertTrue(Missions.active(player) == null && career(player).exam() == null, "not your blow: failed");
        // Again, alone this time.
        career(player).setExamCooldownUntil(0);
        player.setHealth(player.getMaxHealth());
        helper.assertTrue(Exams.start(player, board), "retaken");
        trial = Missions.active(player);
        trial.points.set(0, near(helper, 6, 0));
        Missions.update(player);
        beat(foes(helper, trial), player);
        Missions.update(player);
        helper.assertTrue(Missions.report(player, board), "trial reported");
        helper.assertTrue(Ryo.balance(player) == 0 && career(player).points() == 500, "an exam mission pays nothing");
        CareerData.Exam exam = career(player).exam();
        helper.assertTrue(exam != null && exam.stage == CareerData.Exam.Stage.FIGHT, "now the fight");
        SparringNinja jonin = (SparringNinja) helper.getLevel().getEntity(exam.examiner);
        jonin.invulnerableTime = 0;
        jonin.hurt(jonin.damageSources().playerAttack(player), 100000f);
        helper.assertTrue(PlayerRank.of(player) == PlayerRank.JONIN, "jōnin");
        // ANBU comes by invitation: points and three A-rank missions.
        career(player).setPoints(1000);
        Exams.afterPoints(player);
        helper.assertTrue(PlayerRank.of(player) == PlayerRank.JONIN, "not without A missions");
        for (int i = 0; i < 3; i++) {
            career(player).addCompleted(MissionTemplate.Letter.A);
        }
        Exams.afterPoints(player);
        helper.assertTrue(PlayerRank.of(player) == PlayerRank.ANBU, "invited into the ANBU");
        cleanup(helper, 24);
    }
}

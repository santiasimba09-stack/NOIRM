package com.noirm.gametest;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.creator.CharacterCreator;
import com.noirm.creator.CreatorData;
import com.noirm.creator.GeneticRoll;
import com.noirm.creator.PlayerLook;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Character creation: the clan rolls, the stats they give, height and the hitbox, saving looks, the hold. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreatorGameTests {
    private CreatorGameTests() {}

    /** A fresh fake (server) player in the test area: never logged in, so never held by itself. */
    private static FakePlayer newcomer(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_newcomer"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        return player;
    }

    private static ShinobiData data(FakePlayer player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    /** One roll, then exactly three rerolls; every roll stays pickable and comes from the world's clan list. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void rollsAreLimitedToThreeRerolls(GameTestHelper helper) {
        FakePlayer player = newcomer(helper);
        ShinobiData data = data(player);
        CharacterCreator.beginFirstJoin(player, data);
        CreatorData creator = data.creator();
        helper.assertTrue(creator.mode() == CreatorData.Mode.FIRST_JOIN, "the screen opens for a newcomer");
        helper.assertTrue(creator.rolls().size() == 1, "the first roll is given");
        helper.assertTrue(data.identity().isAssigned(), "the first roll's clan is applied straight away");
        for (int i = 0; i < CharacterCreator.MAX_REROLLS; i++) {
            helper.assertTrue(CharacterCreator.reroll(player, data), "reroll " + (i + 1) + " is allowed");
        }
        helper.assertFalse(CharacterCreator.reroll(player, data), "a fourth reroll is refused");
        helper.assertTrue(creator.rolls().size() == 1 + CharacterCreator.MAX_REROLLS, "four rolls seen");
        helper.assertTrue(creator.rerollsLeft() == 0, "none left");
        helper.assertTrue(creator.chosen() == 3, "the newest roll is picked by default");
        helper.assertTrue(CharacterCreator.choose(player, data, 1), "an earlier roll can be picked");
        helper.assertFalse(CharacterCreator.choose(player, data, 4), "a roll that doesn't exist can't");
        WorldClans clans = WorldClans.get(helper.getLevel().getServer());
        for (GeneticRoll roll : creator.rolls()) {
            helper.assertTrue(clans.find(roll.clanId()).isPresent(), "roll clan " + roll.clanId() + " is in the world's list");
        }
        helper.succeed();
    }

    /** Finishing keeps the chosen roll: its clan and its stats; the screen closes and the hold ends. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void chosenRollIsApplied(GameTestHelper helper) {
        FakePlayer player = newcomer(helper);
        ShinobiData data = data(player);
        CharacterCreator.beginFirstJoin(player, data);
        CharacterCreator.reroll(player, data);
        CharacterCreator.reroll(player, data);
        CharacterCreator.choose(player, data, 0);
        GeneticRoll kept = data.creator().rolls().get(0);
        Clan clan = CharacterCreator.clanOf(helper.getLevel().getServer(), kept).orElseThrow();
        float[] expected = CharacterCreator.finalStats(kept, clan);
        PlayerLook look = PlayerLook.random(RandomSource.create(7));
        helper.assertTrue(CharacterCreator.finish(player, data, look), "finish works");
        helper.assertTrue(data.identity().clanId().equals(kept.clanId()), "joined the kept roll's clan " + kept.clanId()
                + ", got " + data.identity().clanId());
        helper.assertTrue(data.getReserves() == expected[0] && data.getTaijutsu() == expected[1] && data.getSpeed() == expected[2]
                && data.getControl() == expected[3] && data.getWillpower() == expected[4], "the kept roll's stats are set");
        helper.assertTrue(data.creator().mode() == CreatorData.Mode.NONE, "the screen closed");
        helper.assertFalse(data.creator().held(), "no longer held");
        helper.assertTrue(look.equals(data.creator().look()), "the look was saved");
        helper.assertFalse(CharacterCreator.reroll(player, data), "no rerolls once finished");
        helper.succeed();
    }

    /** Blank stats are rolled within bounds; traits only ever add, by at most the clan bonus and trait bonuses. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void statsAreRandomizedWithinBounds(GameTestHelper helper) {
        FakePlayer player = newcomer(helper);
        boolean varied = false;
        int first = -1;
        for (int i = 0; i < 60; i++) {
            GeneticRoll roll = CharacterCreator.roll(player);
            Clan clan = CharacterCreator.clanOf(helper.getLevel().getServer(), roll).orElseThrow();
            int[] base = roll.stats();
            float[] stats = CharacterCreator.finalStats(roll, clan);
            for (int s = 0; s < 5; s++) {
                helper.assertTrue(base[s] >= CharacterCreator.STAT_MIN && base[s] <= CharacterCreator.STAT_MAX,
                        "rolled stat " + s + " = " + base[s] + " is within bounds");
                float bonus = stats[s] - base[s];
                helper.assertTrue(bonus >= 0 && bonus <= 3 + Math.max(clan.speedBonus(), clan.taijutsuBonus()) + 0.001f,
                        "traits add a small bonus to stat " + s + ": +" + bonus);
            }
            if (first < 0) {
                first = base[0];
            } else if (base[0] != first) {
                varied = true;
            }
        }
        helper.assertTrue(varied, "the rolls are random");
        // Applying a roll sets exactly its stats; vitality stays at the default.
        ShinobiData data = data(player);
        GeneticRoll roll = new GeneticRoll(com.noirm.gametest.TestClans.PLAIN.id(), 8, 9, 10, 11, 12);
        WorldClans clans = WorldClans.get(helper.getLevel().getServer());
        if (clans.find(roll.clanId()).isEmpty()) {
            roll = new GeneticRoll(clans.allClans().get(0).id(), 8, 9, 10, 11, 12);
        }
        Clan clan = clans.find(roll.clanId()).orElseThrow();
        CharacterCreator.apply(player, data, roll);
        float[] stats = CharacterCreator.finalStats(roll, clan);
        helper.assertTrue(data.getReserves() == stats[0] && data.getWillpower() == stats[4], "apply sets the stats");
        helper.assertTrue(data.getVitality() == ShinobiData.DEFAULT_STAT, "vitality isn't rolled");
        helper.succeed();
    }

    /** Height 0.85–1.15 scales the hitbox and the eyes; values outside are clamped. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void heightScalesTheHitbox(GameTestHelper helper) {
        FakePlayer player = newcomer(helper);
        ShinobiData data = data(player);
        player.refreshDimensions();
        float baseHeight = player.getBbHeight();
        float baseWidth = player.getBbWidth();
        float baseEyes = player.getEyeHeight();
        CharacterCreator.setLook(player, data, new PlayerLook().setHeight(1.15f));
        helper.assertTrue(Math.abs(player.getBbHeight() - baseHeight * 1.15f) < 0.01f, "tall: hitbox " + player.getBbHeight());
        helper.assertTrue(Math.abs(player.getBbWidth() - baseWidth * 1.15f) < 0.01f, "tall: wider too");
        helper.assertTrue(Math.abs(player.getEyeHeight() - baseEyes * 1.15f) < 0.01f, "tall: eyes " + player.getEyeHeight());
        CharacterCreator.setLook(player, data, new PlayerLook().setHeight(0.85f));
        helper.assertTrue(Math.abs(player.getBbHeight() - baseHeight * 0.85f) < 0.01f, "short: hitbox " + player.getBbHeight());
        helper.assertTrue(Math.abs(player.getEyeHeight() - baseEyes * 0.85f) < 0.01f, "short: eyes");
        helper.assertTrue(new PlayerLook().setHeight(3f).height() == PlayerLook.MAX_HEIGHT, "too tall is clamped");
        helper.assertTrue(new PlayerLook().setHeight(0.1f).height() == PlayerLook.MIN_HEIGHT, "too short is clamped");
        helper.assertTrue(new PlayerLook().setHeight(Float.NaN).height() == 1f, "nonsense is ignored");
        CharacterCreator.setLook(player, data, null);
        helper.assertTrue(Math.abs(player.getBbHeight() - baseHeight) < 0.001f, "no look: normal size again");
        helper.succeed();
    }

    /** A look survives saving (light NBT ids), the player's whole save and the network packet. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void looksSaveAndLoad(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 20; i++) {
            PlayerLook look = PlayerLook.random(random);
            helper.assertTrue(PlayerLook.load(look.save()).equals(look), "NBT round trip " + look);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            look.encode(buf);
            helper.assertTrue(buf.readableBytes() < 48, "the packet is small: " + buf.readableBytes() + " bytes");
            helper.assertTrue(PlayerLook.decode(buf).equals(look), "packet round trip");
        }
        // Unknown ids (an option removed in a later version) fall back instead of failing.
        net.minecraft.nbt.CompoundTag tag = new PlayerLook().save();
        tag.putString("hair", "no_such_style");
        helper.assertTrue(PlayerLook.load(tag).get(PlayerLook.Slot.HAIR) == 0, "unknown ids fall back to the first option");
        // Through the whole player save (and a respawn copy).
        FakePlayer player = newcomer(helper);
        ShinobiData data = data(player);
        PlayerLook look = PlayerLook.random(random).setHeight(0.9f);
        data.creator().setLook(look);
        ShinobiData copy = new ShinobiData();
        copy.load(data.save());
        helper.assertTrue(look.equals(copy.creator().look()), "the look is part of the player's save");
        helper.succeed();
    }

    /** A newcomer is held: put back in place, can't be hurt or hurt anything, until they finish. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void newcomerIsHeldUntilDone(GameTestHelper helper) {
        FakePlayer player = newcomer(helper);
        ShinobiData data = data(player);
        helper.assertFalse(CharacterCreator.isHeld(player), "a fake player that never logged in isn't held");
        Vec3 spot = player.position();
        CharacterCreator.beginFirstJoin(player, data);
        helper.assertTrue(CharacterCreator.isHeld(player), "held while creating");
        player.setPos(spot.x + 2, spot.y + 1, spot.z);
        player.setDeltaMovement(0, 1, 0);
        CharacterCreator.enforceHold(player, data.creator());
        helper.assertTrue(player.position().distanceTo(spot) < 0.01, "put back on the spot: " + player.position());
        helper.assertTrue(player.getDeltaMovement().lengthSqr() == 0, "no momentum");
        Pig pig = EntityType.PIG.create(helper.getLevel());
        helper.assertFalse(ForgeHooks.onLivingAttack(pig, player.damageSources().playerAttack(player), 4f), "can't hit anything while held");
        helper.assertFalse(ForgeHooks.onPlayerAttack(player, player.damageSources().generic(), 4f), "can't be hurt while held");
        helper.assertFalse(CharacterCreator.cancel(player, data), "a newcomer can't cancel creation");
        helper.assertTrue(CharacterCreator.skip(player, data), "finishing (skip) works");
        helper.assertFalse(CharacterCreator.isHeld(player), "free after finishing");
        helper.assertTrue(data.creator().hasLook(), "skip keeps a look");
        helper.assertTrue(ForgeHooks.onLivingAttack(pig, player.damageSources().playerAttack(player), 4f), "can fight again");
        // Reopening for appearance only: not held, the clan stays.
        String clan = data.identity().clanId();
        CharacterCreator.open(player, data, false);
        helper.assertTrue(data.creator().mode() == CreatorData.Mode.APPEARANCE, "appearance mode");
        helper.assertFalse(CharacterCreator.isHeld(player), "appearance mode never holds");
        helper.assertFalse(CharacterCreator.reroll(player, data), "no rerolls in appearance mode");
        helper.assertTrue(CharacterCreator.finish(player, data, new PlayerLook().setHeight(1.1f)), "save the new look");
        helper.assertTrue(data.identity().clanId().equals(clan), "the clan stayed");
        helper.assertTrue(Math.abs(data.creator().height() - 1.1f) < 0.001f, "the new height was saved");
        if (pig != null) {
            pig.discard();
        }
        helper.succeed();
    }

    /** The gamerule is on by default; the screen's option tables are sane. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void creationIsOnByDefault(GameTestHelper helper) {
        helper.assertTrue(CharacterCreator.enabled(helper.getLevel().getServer()), "noirmCharacterCreation defaults to true");
        for (PlayerLook.Slot slot : PlayerLook.Slot.values()) {
            // top, bottom and feet are retired (clothes are gear now) and keep a single "nothing" option
            boolean retired = slot == PlayerLook.Slot.TOP || slot == PlayerLook.Slot.BOTTOM || slot == PlayerLook.Slot.FEET;
            helper.assertTrue(slot.size() >= (retired ? 1 : 2) && slot.size() <= 127, slot.key() + " has options");
        }
        List<String> hair = List.of("spiky", "long", "none");
        hair.forEach(h -> helper.assertTrue(PlayerLook.Slot.HAIR.indexOf(h) >= 0, "hair option " + h));
        helper.succeed();
    }

    /** New characters start in just their underwear: no top, no bottom, bare feet (clothes are gear). */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void newCharactersStartInUnderwear(GameTestHelper helper) {
        RandomSource random = RandomSource.create(5);
        java.util.List<PlayerLook> looks = new java.util.ArrayList<>();
        looks.add(new PlayerLook());
        for (int i = 0; i < 60; i++) {
            looks.add(PlayerLook.random(random));
        }
        FakePlayer player = newcomer(helper);
        ShinobiData data = data(player);
        CharacterCreator.beginFirstJoin(player, data);
        helper.assertTrue(data.creator().hasLook(), "a newcomer gets a starting look");
        looks.add(data.creator().look());
        for (PlayerLook look : looks) {
            helper.assertTrue(look.id(PlayerLook.Slot.TOP).equals("none") && look.id(PlayerLook.Slot.BOTTOM).equals("none")
                    && look.id(PlayerLook.Slot.FEET).equals("bare"), "no clothes on a new look: " + look);
            // the default underwear: black boxers (the owner's pick)
            helper.assertTrue(look.id(PlayerLook.Slot.UNDERWEAR).equals("boxers"), "new looks wear boxers: " + look);
            helper.assertTrue(look.color(PlayerLook.Tone.UNDERWEAR) == 0x1A1A1E, "the boxers are black: " + look);
        }
        helper.succeed();
    }

    /** A look saved before characters started in underwear (with base clothes) loads without them, the rest kept. */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void oldLooksWithClothesLoadInUnderwear(GameTestHelper helper) {
        net.minecraft.nbt.CompoundTag tag = new PlayerLook().save();
        tag.putString("top", "turtleneck");
        tag.putString("bottom", "long");
        tag.putString("feet", "sandals");
        tag.putString("eyes", "sharp");
        tag.putString("hair", "spiky");
        tag.putString("underwear", "fundoshi");
        tag.putInt("top_color", 0x3E7A2E);
        PlayerLook look = PlayerLook.load(tag);
        helper.assertTrue(look.id(PlayerLook.Slot.TOP).equals("none"), "the old top is dropped");
        helper.assertTrue(look.id(PlayerLook.Slot.BOTTOM).equals("none"), "the old pants are dropped");
        helper.assertTrue(look.id(PlayerLook.Slot.FEET).equals("bare"), "the old sandals are dropped");
        helper.assertTrue(look.id(PlayerLook.Slot.EYES).equals("sharp") && look.id(PlayerLook.Slot.HAIR).equals("spiky")
                && look.id(PlayerLook.Slot.UNDERWEAR).equals("fundoshi"), "everything else is kept");
        helper.succeed();
    }

    /**
     * Every part a player look or an NPC look can name exists as an HD image (256x256, the 64x64 skin
     * layout at 4x), and so do the eye glow layers drawn over those faces.
     */
    @GameTest(template = "empty", batch = "noirm_creator")
    public static void everySkinPartIsHd(GameTestHelper helper) {
        java.util.Set<String> paths = new java.util.TreeSet<>();
        String player = "textures/entity/player_parts/";
        String ninja = "textures/entity/ninja/parts/";
        paths.add(player + "skin_base");
        for (PlayerLook.Slot slot : PlayerLook.Slot.values()) {
            for (int i = 0; i < slot.size(); i++) {
                String id = slot.option(i);
                switch (slot) {
                    case JAW, NOSE, MOUTH -> paths.add(player + slot.key() + "_" + id);
                    case MUSCLE -> { if (!id.equals("none")) paths.add(player + "muscle_" + id); }
                    case WEATHERING -> { if (!id.equals("none")) paths.add(player + "weather_" + id); }
                    case SCAR -> {
                        if (id.equals("eye") || id.equals("nose")) paths.add(ninja + "mark_scar_" + id);
                        else if (!id.equals("none")) paths.add(player + "scar_" + id);
                    }
                    case MARKING -> { if (!id.equals("none")) paths.add(ninja + "mark_" + id); }
                    case UNDERWEAR -> paths.add(player + "under_" + id);
                    case EYES -> {
                        paths.add(ninja + "eyes_" + id);
                        paths.add(ninja + "iris_" + id);
                        // the dojutsu overlays are drawn per eye style, to fit its lids
                        paths.add("textures/entity/sharingan_eyes_" + id);
                        paths.add("textures/entity/byakugan_eyes_" + id);
                    }
                    case BROWS -> paths.add(ninja + "brows_" + id);
                    case HAIR -> { if (!id.equals("none")) paths.add(ninja + "hair_" + id); }
                    case HAIR_LENGTH -> { if (!id.equals("as_styled")) paths.add(player + "hairlen_" + id); }
                    default -> { }
                }
            }
        }
        paths.add(ninja + "leg_wraps");
        paths.add(ninja + "arm_wraps");
        paths.add(ninja + "sandals");
        paths.add(ninja + "skin");
        try (java.io.InputStream in = CreatorGameTests.class.getResourceAsStream("/assets/noirm/" + ninja + "looks.json")) {
            helper.assertTrue(in != null, "looks.json is there");
            com.google.gson.JsonObject clans = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("clans");
            String[][] fields = {{"hair_style", "hair_"}, {"eyes", "eyes_"}, {"eyes", "iris_"}, {"brows", "brows_"},
                    {"shirt", "shirt_"}, {"pants", "pants_"}, {"mark", "mark_"}, {"accessory", "acc_"}};
            for (String clan : clans.keySet()) {
                com.google.gson.JsonObject table = clans.getAsJsonObject(clan);
                for (String[] field : fields) {
                    for (com.google.gson.JsonElement entry : table.getAsJsonArray(field[0])) {
                        String id = entry.getAsJsonArray().get(0).getAsString();
                        if (!id.equals("none")) {
                            paths.add(ninja + field[1] + id);
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (String path : paths) {
            int[] size = pngSize("/assets/noirm/" + path + ".png");
            helper.assertTrue(size != null, "the part " + path + " exists");
            helper.assertTrue(size[0] == 256 && size[1] == 256, path + " is 256x256 (HD), not " + size[0] + "x" + size[1]);
        }
        helper.succeed();
    }

    /** A PNG's width and height from its header, or null if the file is missing. */
    private static int[] pngSize(String resource) {
        try (java.io.InputStream in = CreatorGameTests.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            byte[] head = in.readNBytes(24);
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(head);
            return new int[]{b.getInt(16), b.getInt(20)};
        } catch (java.io.IOException e) {
            return null;
        }
    }
}

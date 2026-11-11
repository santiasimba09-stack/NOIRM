package com.noirm.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.creator.CreatorPackets;
import com.noirm.creator.HairStyles;
import com.noirm.creator.HairStyles.Cover;
import com.noirm.creator.PlayerLook;
import com.noirm.creator.PlayerLook.Slot;
import com.noirm.creator.PlayerLook.Tone;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The hair, eyes and bodies rework (docs/ART_STYLE.md): every 3D hair style is valid data, looks with the
 * new parts save, load and sync, random looks are valid, clans keep their own hair, head gear covers the
 * hair the right way, and every new texture is there at the right size.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HairGameTests {
    private HairGameTests() {}

    /** Every hair style id has a JSON file that reads, names itself, and passes HairStyles.problems. */
    @GameTest(template = "empty", batch = "noirm_hair")
    public static void everyHairStyleIsValid(GameTestHelper helper) {
        List<String> ids = HairStyles.ids();
        helper.assertTrue(ids.size() >= 20, "about 20 styles, got " + ids.size());
        for (String id : ids) {
            HairStyles.Style style = HairStyles.read(id);
            helper.assertTrue(style.id().equals(id), id + " names itself");
            List<String> problems = HairStyles.problems(style);
            helper.assertTrue(problems.isEmpty(), id + ": " + problems);
            int[] size = png("/assets/noirm/textures/entity/ninja/parts/scalp_" + style.scalp() + ".png");
            helper.assertTrue(size != null && size[0] == 256, id + ": its scalp part is an HD skin part");
        }
        helper.assertTrue(HairStyles.get("none") == null && HairStyles.get("no_such_style") == null, "no style for none/unknown");
        // long styles really hang and swing; short ones don't
        helper.assertTrue(HairStyles.get("long").locks().stream().anyMatch(l -> l.hang() && l.swing() > 0), "long hair hangs and swings");
        helper.assertTrue(HairStyles.get("spiky").locks().stream().noneMatch(HairStyles.Lock::hang), "spiky hair doesn't hang");
        int[] icons = png("/assets/noirm/textures/gui/hair_styles.png");
        helper.assertTrue(icons != null && icons[0] == 48 * 8 && icons[1] >= 48 * ((ids.size() + 1 + 7) / 8), "a picker icon per style");
        int[] strands = png("/assets/noirm/textures/entity/hair/strands.png");
        helper.assertTrue(strands != null && strands[0] == 256 && strands[1] == 256, "the strand texture is 256x256");
        helper.succeed();
    }

    /** Looks with the new hair, build and frame survive saving, loading and the network. */
    @GameTest(template = "empty", batch = "noirm_hair")
    public static void looksWithNewPartsSaveLoadAndSync(GameTestHelper helper) {
        PlayerLook look = new PlayerLook().set(Slot.HAIR, Slot.HAIR.indexOf("twin_tails")).set(Slot.BODY, Slot.BODY.indexOf("broad"))
                .set(Slot.FRAME, Slot.FRAME.indexOf("feminine")).set(Slot.HAIR_LENGTH, 2).set(Slot.HAIR_VOLUME, 3)
                .setColor(Tone.HAIR, Tone.HAIR.palette()[33]);
        PlayerLook loaded = PlayerLook.load(look.save());
        helper.assertTrue(loaded.equals(look), "NBT round trip " + loaded);
        helper.assertTrue(loaded.id(Slot.HAIR).equals("twin_tails") && loaded.id(Slot.BODY).equals("broad")
                && loaded.id(Slot.FRAME).equals("feminine"), "the new parts are kept by name");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        look.encode(buf);
        helper.assertTrue(PlayerLook.decode(buf).equals(look), "packet round trip");
        // what the server sends everyone else
        buf = new FriendlyByteBuf(Unpooled.buffer());
        new CreatorPackets.LookSync(42, look).encode(buf);
        CreatorPackets.LookSync sync = CreatorPackets.LookSync.decode(buf);
        helper.assertTrue(sync.entityId() == 42 && look.equals(sync.look()), "look sync round trip");
        // a save from before the rework: no body or frame, an old hair id
        net.minecraft.nbt.CompoundTag old = look.save();
        old.remove("body");
        old.remove("frame");
        old.putString("hair", "hime");
        PlayerLook fromOld = PlayerLook.load(old);
        helper.assertTrue(fromOld.id(Slot.BODY).equals("average") && fromOld.id(Slot.FRAME).equals("masculine")
                && fromOld.id(Slot.HAIR).equals("hime"), "old saves load with the defaults");
        helper.succeed();
    }

    /** Random looks (creator rolls, newcomers) are always drawable: a real style or bald, valid build and frame. */
    @GameTest(template = "empty", batch = "noirm_hair")
    public static void randomLooksAreValid(GameTestHelper helper) {
        RandomSource random = RandomSource.create(11);
        int vivid = 0;
        java.util.Set<String> styles = new java.util.HashSet<>();
        for (int i = 0; i < 400; i++) {
            PlayerLook look = PlayerLook.random(random);
            String hair = look.id(Slot.HAIR);
            styles.add(hair);
            helper.assertTrue(hair.equals("none") || HairStyles.get(hair) != null, "a drawable style: " + hair);
            helper.assertTrue(Slot.BODY.indexOf(look.id(Slot.BODY)) >= 0 && Slot.FRAME.indexOf(look.id(Slot.FRAME)) >= 0, "build and frame");
            helper.assertTrue(look.hairVolumeScale() >= 0.85f && look.hairVolumeScale() <= 1.3f, "volume in range");
            int[] palette = Tone.HAIR.palette();
            boolean natural = false;
            for (int k = 0; k < PlayerLook.NATURAL_HAIR; k++) {
                natural |= palette[k] == look.color(Tone.HAIR);
            }
            vivid += natural ? 0 : 1;
        }
        helper.assertTrue(styles.size() >= 18, "random looks use most styles: " + styles.size());
        helper.assertTrue(vivid < 200, "vivid hair is the exception: " + vivid + "/400");
        helper.assertTrue(Tone.HAIR.palette().length >= 36, "about 40 hair swatches");
        helper.succeed();
    }

    /** Clans keep their own hair: every NPC table names real styles; Uchiha dark, Uzumaki red, Hyuga long and straight. */
    @GameTest(template = "empty", batch = "noirm_hair")
    public static void clansKeepTheirHair(GameTestHelper helper) {
        JsonObject clans;
        try (InputStream in = HairGameTests.class.getResourceAsStream("/assets/noirm/textures/entity/ninja/parts/looks.json")) {
            clans = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("clans");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        for (String clan : clans.keySet()) {
            for (JsonElement e : clans.getAsJsonObject(clan).getAsJsonArray("hair_style")) {
                String style = e.getAsJsonArray().get(0).getAsString();
                helper.assertTrue(HairStyles.get(style) != null, clan + " names a real hair style: " + style);
            }
        }
        for (JsonElement e : clans.getAsJsonObject("uchiha").getAsJsonArray("hair_color")) {
            int rgb = Integer.parseInt(e.getAsJsonArray().get(0).getAsString().substring(1), 16);
            helper.assertTrue(luma(rgb) < 50, "Uchiha hair is dark");
        }
        for (JsonElement e : clans.getAsJsonObject("uzumaki").getAsJsonArray("hair_color")) {
            int rgb = Integer.parseInt(e.getAsJsonArray().get(0).getAsString().substring(1), 16);
            helper.assertTrue(((rgb >> 16) & 0xFF) > 2 * ((rgb >> 8) & 0xFF) || weight(e) <= 2, "Uzumaki hair is mostly red");
        }
        helper.assertTrue(top(clans.getAsJsonObject("hyuga").getAsJsonArray("hair_style")).equals("long"), "Hyuga hair is long and straight");
        helper.assertTrue(top(clans.getAsJsonObject("uchiha").getAsJsonArray("hair_style")).equals("duck"), "Uchiha hair spikes at the back");
        helper.assertTrue(top(clans.getAsJsonObject("senju").getAsJsonArray("hair_style")).equals("long"), "Senju hair is long");
        helper.succeed();
    }

    /** Hats and helmets leave only the hanging hair, hoods hide it, bands press it in, masks leave it alone. */
    @GameTest(template = "empty", batch = "noirm_hair")
    public static void headGearCoversTheHair(GameTestHelper helper) {
        helper.assertTrue(HairStyles.cover(new ResourceLocation("noirm", "forehead_protector_navy")) == Cover.SQUASH, "band squashes");
        helper.assertTrue(HairStyles.cover(new ResourceLocation("noirm", "kage_hat_red")) == Cover.CROWN, "Kage hat covers the crown");
        helper.assertTrue(HairStyles.cover(new ResourceLocation("noirm", "straw_hat")) == Cover.CROWN, "straw hat covers the crown");
        helper.assertTrue(HairStyles.cover(new ResourceLocation("noirm", "tattered_hood")) == Cover.ALL, "a hood hides it all");
        helper.assertTrue(HairStyles.cover(new ResourceLocation("noirm", "anbu_mask")) == Cover.NONE, "a mask leaves the hair");
        helper.assertTrue(HairStyles.cover(new ResourceLocation("noirm", "mask_wolf")) == Cover.NONE, "a mask leaves the hair");
        helper.assertTrue(HairStyles.cover(new ResourceLocation("minecraft", "iron_helmet")) == Cover.CROWN, "vanilla helmets cover the crown");
        helper.assertTrue(HairStyles.cover(net.minecraft.world.item.ItemStack.EMPTY) == Cover.NONE, "nothing worn, nothing covered");
        helper.succeed();
    }

    /** The high-detail eye atlases exist for every eye shape, at their layout's size. */
    @GameTest(template = "empty", batch = "noirm_hair")
    public static void everyEyeShapeHasItsAtlas(GameTestHelper helper) {
        for (int i = 0; i < Slot.EYES.size(); i++) {
            String id = Slot.EYES.option(i);
            int[] size = png("/assets/noirm/textures/entity/eyes/" + id + ".png");
            helper.assertTrue(size != null && size[0] == 512 && size[1] == 64, "eye atlas for " + id);
        }
        helper.succeed();
    }

    private static String top(JsonArray table) {
        String best = "";
        int weight = -1;
        for (JsonElement e : table) {
            if (weight(e) > weight) {
                weight = weight(e);
                best = e.getAsJsonArray().get(0).getAsString();
            }
        }
        return best;
    }

    private static int weight(JsonElement e) {
        return e.getAsJsonArray().get(1).getAsInt();
    }

    private static int luma(int rgb) {
        return (((rgb >> 16) & 0xFF) * 3 + ((rgb >> 8) & 0xFF) * 6 + (rgb & 0xFF)) / 10;
    }

    private static int[] png(String resource) {
        try (InputStream in = HairGameTests.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(in.readNBytes(24));
            return new int[]{b.getInt(16), b.getInt(20)};
        } catch (java.io.IOException e) {
            return null;
        }
    }
}

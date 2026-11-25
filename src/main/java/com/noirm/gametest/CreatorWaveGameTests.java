package com.noirm.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.creator.CreatorPackets;
import com.noirm.creator.HairStyles;
import com.noirm.creator.LookLayers;
import com.noirm.creator.PlayerLook;
import com.noirm.creator.PlayerLook.Slot;
import com.noirm.creator.PlayerLook.Tone;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The creator wave (2026-11-19): many more hair styles, eye shapes, pupils, brows, builds, scars, marks, eye
 * details, face paint, tattoos, a second hair colour with dyes, and more swatches. New options must save, load
 * and sync; a look saved before the wave must build exactly the same skin; every new option has its art and its
 * name in all three languages.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreatorWaveGameTests {
    private CreatorWaveGameTests() {}

    /** How many options each slot had in v0.19 (protocol 32): the old options keep their place. */
    private static final Map<Slot, Integer> OLD_SIZES = new EnumMap<>(Slot.class);
    private static final String[] OLD_TONES = {"skin", "eye", "hair", "underwear", "top", "bottom"};

    static {
        Object[][] sizes = {{Slot.EYES, 5}, {Slot.NOSE, 5}, {Slot.JAW, 4}, {Slot.MOUTH, 5}, {Slot.BROWS, 4}, {Slot.MUSCLE, 4},
                {Slot.SCAR, 7}, {Slot.WEATHERING, 4}, {Slot.MARKING, 9}, {Slot.UNDERWEAR, 5}, {Slot.TOP, 1}, {Slot.BOTTOM, 1},
                {Slot.FEET, 1}, {Slot.WRAPS, 4}, {Slot.HAIR, 24}, {Slot.HAIR_LENGTH, 4}, {Slot.HAIR_VOLUME, 4}, {Slot.BODY, 4},
                {Slot.FRAME, 2}};
        for (Object[] s : sizes) {
            OLD_SIZES.put((Slot) s[0], (Integer) s[1]);
        }
    }

    /** A look with every new slot at its last option and the new colours set survives NBT and the network. */
    @GameTest(template = "empty", batch = "noirm_creator_wave")
    public static void newOptionsSaveLoadAndSync(GameTestHelper helper) {
        PlayerLook look = new PlayerLook();
        for (Slot slot : Slot.values()) {
            look.set(slot, slot.size() - 1);
        }
        look.set(Slot.HAIR, Slot.HAIR.indexOf("wave"));
        look.setColor(Tone.HAIR2, 0x38D0C0).setColor(Tone.PAINT, 0x5A2A8A).setColor(Tone.SKIN, Tone.SKIN.palette()[20]);
        PlayerLook loaded = PlayerLook.load(look.save());
        helper.assertTrue(loaded.equals(look), "NBT round trip " + loaded);
        helper.assertTrue(loaded.id(Slot.HAIR).equals("wave") && loaded.id(Slot.EYES).equals("glare")
                && loaded.id(Slot.PUPIL).equals("ringed") && loaded.id(Slot.TATTOO).equals("leg_bands")
                && loaded.id(Slot.HAIR_DYE).equals("bangs") && loaded.id(Slot.BODY).equals("hulking"), "kept by name");
        helper.assertTrue(loaded.color(Tone.HAIR2) == 0x38D0C0 && loaded.color(Tone.PAINT) == 0x5A2A8A, "the new colours are kept");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new CreatorPackets.LookSync(7, look).encode(buf);
        CreatorPackets.LookSync sync = CreatorPackets.LookSync.decode(buf);
        helper.assertTrue(sync.look().equals(look), "look sync round trip");
        // the skin cache key tells long lists apart (hair has more than 36 options now)
        PlayerLook a = new PlayerLook().set(Slot.HAIR, 1);
        PlayerLook b = new PlayerLook().set(Slot.HAIR, 37);
        helper.assertTrue(!a.key().equals(b.key()), "different hair, different skin key");
        // every option of every slot round-trips by name
        for (Slot slot : Slot.values()) {
            for (int i = 0; i < slot.size(); i++) {
                PlayerLook one = new PlayerLook().set(slot, i);
                helper.assertTrue(PlayerLook.load(one.save()).get(slot) == i, slot.key() + " option " + slot.option(i));
            }
        }
        helper.succeed();
    }

    /**
     * A look saved before the wave (only the old slots and colours in its NBT) loads with every new choice at its
     * default and builds exactly the skin layers it built before; old options kept their numbers.
     */
    @GameTest(template = "empty", batch = "noirm_creator_wave")
    public static void oldLooksKeepTheirLook(GameTestHelper helper) {
        // the v0.19 option lists still start every list
        String[] oldHair = {"spiky", "messy", "short", "swept", "bowl", "buzz", "topknot", "duck", "bun", "hime", "ponytail", "long",
                "high_ponytail", "braid", "side_bangs", "shoulder_messy", "twin_buns", "wild_mane", "undercut", "mohawk",
                "slick_back", "curly", "twin_tails", "none"};
        for (int i = 0; i < oldHair.length; i++) {
            helper.assertTrue(Slot.HAIR.option(i).equals(oldHair[i]), "hair " + i + " is still " + oldHair[i]);
        }
        String[] oldEyes = {"round", "sharp", "wide", "narrow", "pale"};
        for (int i = 0; i < oldEyes.length; i++) {
            helper.assertTrue(Slot.EYES.option(i).equals(oldEyes[i]), "eyes " + i + " is still " + oldEyes[i]);
        }
        RandomSource random = RandomSource.create(19);
        for (int n = 0; n < 300; n++) {
            CompoundTag tag = new CompoundTag();
            for (Map.Entry<Slot, Integer> e : OLD_SIZES.entrySet()) {
                tag.putString(e.getKey().key(), e.getKey().option(random.nextInt(e.getValue())));
            }
            for (String tone : OLD_TONES) {
                tag.putInt(tone + "_color", random.nextInt(0x1000000));
            }
            tag.putFloat("height", 0.9f + random.nextFloat() * 0.2f);
            PlayerLook look = PlayerLook.load(tag);
            for (Slot slot : new Slot[]{Slot.HAIR_DYE, Slot.PUPIL, Slot.EYE_DETAIL, Slot.FACE_PAINT, Slot.TATTOO}) {
                helper.assertTrue(look.get(slot) == 0, slot.key() + " starts at its default on an old look");
            }
            for (Map.Entry<Slot, Integer> e : OLD_SIZES.entrySet()) {
                helper.assertTrue(look.id(e.getKey()).equals(tag.getString(e.getKey().key())), e.getKey().key() + " kept");
            }
            List<Map.Entry<String, Integer>> now = LookLayers.layers(look);
            List<Map.Entry<String, Integer>> before = layersV019(look);
            helper.assertTrue(now.equals(before), "the same skin as before the wave:\n" + now + "\n" + before);
        }
        helper.succeed();
    }

    /** Every new option has its art (styles, atlases, parts) and its name in en_us, es_es and es_mx. */
    @GameTest(template = "empty", batch = "noirm_creator_wave")
    public static void everyOptionHasArtAndNames(GameTestHelper helper) {
        helper.assertTrue(HairStyles.ids().size() >= 60, "at least 60 hair styles: " + HairStyles.ids().size());
        for (String id : HairStyles.ids()) {
            helper.assertTrue(HairStyles.get(id) != null && HairStyles.problems(HairStyles.get(id)).isEmpty(), "hair style " + id);
        }
        for (int i = 0; i < Slot.EYES.size(); i++) {
            helper.assertTrue(HairStyles.class.getResource("/assets/noirm/textures/entity/eyes/" + Slot.EYES.option(i) + ".png") != null,
                    "eye atlas " + Slot.EYES.option(i));
        }
        // every look's parts exist (all slots at all options, one at a time)
        for (Slot slot : Slot.values()) {
            for (int i = 0; i < slot.size(); i++) {
                for (Map.Entry<String, Integer> layer : LookLayers.layers(new PlayerLook().set(slot, i))) {
                    helper.assertTrue(HairStyles.class.getResource("/assets/noirm/" + layer.getKey() + ".png") != null,
                            "part " + layer.getKey() + " for " + slot.key() + "=" + slot.option(i));
                }
            }
        }
        for (String lang : new String[]{"en_us", "es_es", "es_mx"}) {
            JsonObject json;
            try (InputStream in = CreatorWaveGameTests.class.getResourceAsStream("/assets/noirm/lang/" + lang + ".json")) {
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            List<String> missing = new ArrayList<>();
            for (Slot slot : Slot.values()) {
                if (slot == Slot.TOP || slot == Slot.BOTTOM || slot == Slot.FEET) {
                    continue; // retired, never shown
                }
                if (!json.has("creator.noirm.slot." + slot.key())) {
                    missing.add("creator.noirm.slot." + slot.key());
                }
                for (int i = 0; i < slot.size(); i++) {
                    if (!json.has(slot.translationKey(i))) {
                        missing.add(slot.translationKey(i));
                    }
                }
            }
            for (Tone tone : new Tone[]{Tone.SKIN, Tone.EYE, Tone.HAIR, Tone.UNDERWEAR, Tone.HAIR2, Tone.PAINT}) {
                if (!json.has("creator.noirm.tone." + tone.key())) {
                    missing.add("creator.noirm.tone." + tone.key());
                }
            }
            helper.assertTrue(missing.isEmpty(), lang + " is missing " + missing);
        }
        helper.succeed();
    }

    /** Random looks stay drawable and keep the new extras the exception. */
    @GameTest(template = "empty", batch = "noirm_creator_wave")
    public static void randomLooksUseTheNewOptions(GameTestHelper helper) {
        RandomSource random = RandomSource.create(23);
        int painted = 0;
        int dyed = 0;
        java.util.Set<String> styles = new java.util.HashSet<>();
        for (int i = 0; i < 400; i++) {
            PlayerLook look = PlayerLook.random(random);
            styles.add(look.id(Slot.HAIR));
            painted += look.get(Slot.FACE_PAINT) > 0 ? 1 : 0;
            dyed += look.get(Slot.HAIR_DYE) > 0 ? 1 : 0;
            helper.assertTrue(look.id(Slot.HAIR).equals("none") || HairStyles.get(look.id(Slot.HAIR)) != null, "drawable hair");
        }
        helper.assertTrue(styles.size() >= 45, "random looks use the new styles too: " + styles.size());
        helper.assertTrue(painted > 0 && painted < 200, "face paint is the exception: " + painted);
        helper.assertTrue(dyed > 0 && dyed < 250, "dyed hair is the exception: " + dyed);
        helper.succeed();
    }

    /** The v0.19 layer rules, frozen (client/PlayerSkins before the creator wave), to compare old looks against. */
    private static List<Map.Entry<String, Integer>> layersV019(PlayerLook look) {
        String player = "textures/entity/player_parts/";
        String ninja = "textures/entity/ninja/parts/";
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        int skin = look.color(Tone.SKIN);
        int hair = look.color(Tone.HAIR);
        out.add(Map.entry(player + "skin_base", skin));
        out.add(Map.entry(player + "jaw_" + look.id(Slot.JAW), skin));
        out.add(Map.entry(player + "nose_" + look.id(Slot.NOSE), skin));
        out.add(Map.entry(player + "mouth_" + look.id(Slot.MOUTH), skin));
        boolean feminine = look.id(Slot.FRAME).equals("feminine");
        if (feminine) {
            out.add(Map.entry(player + "fem_" + look.id(Slot.MUSCLE), skin));
        } else if (!look.id(Slot.MUSCLE).equals("none")) {
            out.add(Map.entry(player + "muscle_" + look.id(Slot.MUSCLE), skin));
        }
        if (!look.id(Slot.BODY).equals("average")) {
            out.add(Map.entry(player + "build_" + look.id(Slot.BODY), skin));
        }
        if (!look.id(Slot.WEATHERING).equals("none")) {
            out.add(Map.entry(player + "weather_" + look.id(Slot.WEATHERING), -1));
        }
        switch (look.id(Slot.SCAR)) {
            case "none" -> { }
            case "eye" -> out.add(Map.entry(ninja + "mark_scar_eye", -1));
            case "nose" -> out.add(Map.entry(ninja + "mark_scar_nose", -1));
            default -> out.add(Map.entry(player + "scar_" + look.id(Slot.SCAR), -1));
        }
        if (!look.id(Slot.MARKING).equals("none")) {
            out.add(Map.entry(ninja + "mark_" + look.id(Slot.MARKING), -1));
        }
        out.add(Map.entry(player + "under_" + look.id(Slot.UNDERWEAR), look.color(Tone.UNDERWEAR)));
        if (feminine) {
            out.add(Map.entry(player + "under_top", look.color(Tone.UNDERWEAR)));
        }
        String wraps = look.id(Slot.WRAPS);
        if (wraps.equals("legs") || wraps.equals("both")) {
            out.add(Map.entry(ninja + "leg_wraps", -1));
        }
        if (wraps.equals("arms") || wraps.equals("both")) {
            out.add(Map.entry(ninja + "arm_wraps", -1));
        }
        out.add(Map.entry(ninja + "iris_" + look.id(Slot.EYES), look.color(Tone.EYE)));
        out.add(Map.entry(ninja + "eyes_" + look.id(Slot.EYES), -1));
        out.add(Map.entry(ninja + "brows_" + look.id(Slot.BROWS), hair));
        String scalp = HairStyles.scalp(look.id(Slot.HAIR));
        if (scalp != null) {
            out.add(Map.entry(ninja + "scalp_" + scalp, hair));
        }
        return out;
    }
}

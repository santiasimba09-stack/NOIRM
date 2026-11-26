package com.noirm.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.sound.NoirmSounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Our own sounds (docs/SOUND.md): registered, listed with files and subtitles, and really played. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoundGameTests {
    private static final ResourceLocation PHOENIX = Noirm.id("katon/phoenix_flower");

    private SoundGameTests() {}

    private static JsonObject json(String path) {
        try (InputStream in = SoundGameTests.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Every SoundEvent is registered and in sounds.json, every file it names exists, and it has a subtitle in all three languages. */
    @GameTest(template = "empty", batch = "noirm_sound_registry")
    public static void everySoundIsRegisteredListedAndPresent(GameTestHelper helper) {
        JsonObject sounds = json("/assets/noirm/sounds.json");
        helper.assertTrue(sounds != null, "assets/noirm/sounds.json is missing");
        List<JsonObject> langs = new ArrayList<>();
        for (String lang : new String[]{"en_us", "es_es", "es_mx"}) {
            JsonObject l = json("/assets/noirm/lang/" + lang + ".json");
            helper.assertTrue(l != null, "no " + lang + " lang file");
            langs.add(l);
        }
        helper.assertTrue(NoirmSounds.all().size() >= 50, "about fifty sounds, got " + NoirmSounds.all().size());
        for (Map.Entry<String, RegistryObject<SoundEvent>> entry : NoirmSounds.all().entrySet()) {
            String id = entry.getKey();
            helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(Noirm.id(id)), "not registered: " + id);
            helper.assertTrue(entry.getValue().get().getLocation().equals(Noirm.id(id)), "wrong location for " + id);
            helper.assertTrue(sounds.has(id), "not in sounds.json: " + id);
            JsonObject def = sounds.getAsJsonObject(id);
            String subtitle = def.get("subtitle").getAsString();
            for (JsonObject lang : langs) {
                helper.assertTrue(lang.has(subtitle), "no subtitle text for " + subtitle);
            }
            var files = def.getAsJsonArray("sounds");
            helper.assertTrue(files.size() >= 2, "2-4 variants for " + id + ", got " + files.size());
            for (var f : files) {
                String name = f.isJsonObject() ? f.getAsJsonObject().get("name").getAsString() : f.getAsString();
                ResourceLocation file = new ResourceLocation(name);
                String path = "/assets/" + file.getNamespace() + "/sounds/" + file.getPath() + ".ogg";
                helper.assertTrue(SoundGameTests.class.getResource(path) != null, "missing file " + path);
            }
        }
        for (String key : sounds.keySet()) {
            helper.assertTrue(NoirmSounds.all().containsKey(key), "sounds.json lists an unregistered sound: " + key);
        }
        helper.succeed();
    }

    /** Every nature has a sound of our own for each kind (cast, impact, held), and no nature is left out. */
    @GameTest(template = "empty", batch = "noirm_sound_natures")
    public static void everyNatureMapsToOurSound(GameTestHelper helper) {
        Set<ResourceLocation> casts = new HashSet<>();
        for (Nature nature : Nature.values()) {
            for (NoirmSounds.Kind kind : NoirmSounds.Kind.values()) {
                SoundEvent sound = NoirmSounds.forNature(nature, kind);
                helper.assertTrue(sound != null && sound.getLocation().getNamespace().equals(Noirm.MOD_ID),
                        nature + "/" + kind + " has no NOIRM sound");
            }
            casts.add(NoirmSounds.forNature(nature, NoirmSounds.Kind.CAST).getLocation());
        }
        helper.assertTrue(NoirmSounds.forNature(null, NoirmSounds.Kind.CAST) == NoirmSounds.CHAKRA_RELEASE.get(), "plain chakra: the release");
        helper.assertTrue(NoirmSounds.forNature(Nature.FIRE, NoirmSounds.Kind.CAST) == NoirmSounds.FIRE_ROAR.get(), "fire roars");
        helper.assertTrue(NoirmSounds.forNature(Nature.LIGHTNING, NoirmSounds.Kind.SUSTAIN) == NoirmSounds.LIGHTNING_CRACKLE.get(), "held lightning crackles");
        helper.assertTrue(casts.size() >= 14, "natures sound different from each other, only " + casts.size() + " distinct cast sounds");
        helper.succeed();
    }

    /** Casting Phoenix Flower plays our sounds: a clap per seal, the seals locking, then fire. */
    @GameTest(template = "empty", batch = "noirm_sound_cast")
    public static void jutsuCastPlaysOurSounds(GameTestHelper helper) {
        TestKit.floor(helper);
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        ClanSystem.assign(player, data, TestClans.FIRE);
        data.jutsu().learn(PHOENIX, false);
        data.jutsu().equip(0, PHOENIX);
        data.setChakra(100f);

        List<ResourceLocation> heard = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getPosition().distanceToSqr(pos) < 4) {
                heard.add(event.getSound().value().getLocation());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "the seals start");
            for (int i = 0; i < JutsuCaster.SEAL_PHASE_MAX_TICKS + 2 && JutsuCaster.casting(player) != null; i++) {
                JutsuCaster.tick(player); // test players aren't ticked by the world
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        long claps = heard.stream().filter(NoirmSounds.SEAL_CLAP.getId()::equals).count();
        helper.assertTrue(claps == 6, "one clap per seal (6), heard " + claps + ": " + heard);
        helper.assertTrue(heard.contains(NoirmSounds.SEAL_COMPLETE.getId()), "the seals lock: " + heard);
        helper.assertTrue(heard.contains(NoirmSounds.FIRE_ROAR.getId()), "and the fire roars: " + heard);
        helper.assertFalse(heard.stream().anyMatch(id -> !id.getNamespace().equals(Noirm.MOD_ID)), "only our own sounds: " + heard);
        helper.getLevel().getEntitiesOfClass(com.noirm.jutsu.JutsuProjectile.class,
                new net.minecraft.world.phys.AABB(pos, pos).inflate(30)).forEach(com.noirm.jutsu.JutsuProjectile::discard);
        helper.succeed();
    }
}

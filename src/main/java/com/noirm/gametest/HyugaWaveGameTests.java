package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.NatureSystem;
import com.noirm.combat.CombatSystem;
import com.noirm.dojutsu.Byakugan;
import com.noirm.jutsu.HyugaTechniques;
import com.noirm.jutsu.JutsuBook;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.traits.Perk;
import com.noirm.traits.PerkEvents;
import com.noirm.traits.Perks;
import com.noirm.training.TrainingSystem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Wave "Hyūga" (docs/WAVE_HYUGA.md): chakra-free Hyūga techniques, no Reserves gate, mastery up to 10, and the
 * clan's new numbers.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HyugaWaveGameTests {
    private static final String BATCH = "noirm_hyuga_wave";
    private static final List<ResourceLocation> TECHNIQUES = List.of(Byakugan.ROTATION, Byakugan.SIXTY_FOUR_PALMS,
            Byakugan.HUNDRED_TWENTY_EIGHT_PALMS, Byakugan.AIR_PALM, Byakugan.TWIN_LION_FISTS);

    private HyugaWaveGameTests() {}

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow();
    }

    private static Clan canon(String id) {
        return ClanData.canon(id).orElseThrow(() -> new IllegalStateException("no canon clan " + id));
    }

    private static Player member(GameTestHelper helper, Clan clan) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, clan);
        data.talents().set(List.of());
        TrainingSystem.applyReserves(data);
        PerkEvents.tick(player, data);
        return player;
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }

    /** Every Hyūga technique is a chakra-free body art with mastery up to 10, and costs nothing to cast. */
    @GameTest(template = "empty", batch = BATCH)
    public static void hyugaTechniquesCostNoChakra(GameTestHelper helper) {
        Player player = member(helper, canon("hyuga"));
        ShinobiData data = data(player);
        for (ResourceLocation id : TECHNIQUES) {
            JutsuDefinition def = JutsuData.get(id);
            helper.assertTrue(def != null, "missing " + id);
            helper.assertTrue(def.chakraFree(), id + " is chakra-free");
            helper.assertTrue(def.maxMastery() == 10, id + " masters up to 10");
            near(helper, 0f, JutsuCaster.cost(player, id, def), id + " costs no chakra");
        }
        JutsuDefinition fireball = JutsuData.get(Noirm.id("katon/great_fireball"));
        helper.assertTrue(!fireball.chakraFree() && fireball.maxMastery() == JutsuBook.DEFAULT_MAX_MASTERY,
                "ordinary jutsu keep their cost and the usual ceiling");
        // A real cast with an empty chakra pool.
        data.eyes().setActive(true);
        data.setChakra(0f);
        data.jutsu().equip(0, Byakugan.ROTATION);
        helper.assertTrue(JutsuCaster.onKey(player, 0, true) == JutsuCaster.Result.OK, "Rotation spins with no chakra at all");
        near(helper, 0f, data.getChakra(), "and still no chakra spent");
        // The open eye doesn't drain either.
        near(helper, 0f, Byakugan.DRAIN_PER_TICK, "the Byakugan is free to keep open");
        helper.succeed();
    }

    /** A Hyūga with almost no Reserves still unlocks the S-rank 128 Palms through mastery alone. */
    @GameTest(template = "empty", batch = BATCH)
    public static void hyugaTechniquesNeedNoReserves(GameTestHelper helper) {
        Player player = member(helper, canon("hyuga"));
        ShinobiData data = data(player);
        data.setReserves(5f);
        JutsuDefinition palms128 = JutsuData.get(Byakugan.HUNDRED_TWENTY_EIGHT_PALMS);
        near(helper, 0f, NatureSystem.reservesFor(palms128), "no Reserves needed for a body art");
        helper.assertTrue(NatureSystem.reservesFor(JutsuData.get(Noirm.id("katon/great_fireball"))) >= 0f, "others keep their gate");
        int unlockAt = palms128.unlock().orElseThrow().mastery();
        helper.assertTrue(unlockAt > 2, "128 Palms takes more mastery now (★" + unlockAt + ")");
        data.jutsu().addXp(Byakugan.SIXTY_FOUR_PALMS, JutsuBook.xpForLevel(unlockAt) - data.jutsu().xp(Byakugan.SIXTY_FOUR_PALMS));
        NatureSystem.checkMasteryUnlocks(player, data, Byakugan.SIXTY_FOUR_PALMS, true); // the gated path real casts use
        helper.assertTrue(data.jutsu().knows(Byakugan.HUNDRED_TWENTY_EIGHT_PALMS), "128 Palms learned with 5 Reserves");
        helper.succeed();
    }

    /** Mastery climbs to 10 on a Hyūga technique, stops at 5 elsewhere, and keeps old ★ meanings. */
    @GameTest(template = "empty", batch = BATCH)
    public static void masteryReachesTenAndScales(GameTestHelper helper) {
        Player player = member(helper, canon("hyuga"));
        ShinobiData data = data(player);
        JutsuBook book = data.jutsu();
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        book.learn(fireball, false);
        // Old saves: 30 XP was ★2 and still is, on both kinds of jutsu.
        book.addXp(Byakugan.ROTATION, 30);
        book.addXp(fireball, 30);
        helper.assertTrue(book.masteryLevel(Byakugan.ROTATION) == 2 && book.masteryLevel(fireball) == 2, "★2 means ★2");
        float power2 = JutsuCaster.power(player, Byakugan.ROTATION, JutsuData.get(Byakugan.ROTATION));
        book.addXp(Byakugan.ROTATION, JutsuBook.xpForLevel(10));
        book.addXp(fireball, JutsuBook.xpForLevel(10));
        helper.assertTrue(book.masteryLevel(Byakugan.ROTATION) == 10, "a Hyūga technique reaches Lv 10");
        helper.assertTrue(book.masteryLevel(fireball) == JutsuBook.DEFAULT_MAX_MASTERY, "an ordinary jutsu stops at ★5");
        helper.assertTrue(JutsuBook.xpForLevel(10) >= JutsuBook.xpForLevel(5) * 5, "Lv 10 takes far more casts than ★5");
        // Each level is a steady gain.
        for (int level = 1; level <= 10; level++) {
            helper.assertTrue(JutsuCaster.masteryPower(level) > JutsuCaster.masteryPower(level - 1), "power grows at level " + level);
            helper.assertTrue(JutsuCaster.masterySaving(level) < JutsuCaster.masterySaving(level - 1), "cooldown shrinks at level " + level);
            helper.assertTrue(HyugaTechniques.rotationRadius(3.5f, level) > HyugaTechniques.rotationRadius(3.5f, level - 1), "Rotation widens");
            helper.assertTrue(HyugaTechniques.airPalmRange(10f, level) > HyugaTechniques.airPalmRange(10f, level - 1), "Air Palm reaches further");
            helper.assertTrue(HyugaTechniques.palmsPoints(level) > HyugaTechniques.palmsPoints(level - 1), "palms close more points");
        }
        near(helper, 1.75f, JutsuCaster.masteryPower(5), "★5 power as before");
        near(helper, 2.25f, JutsuCaster.masteryPower(10), "Lv 10 power");
        float power10 = JutsuCaster.power(player, Byakugan.ROTATION, JutsuData.get(Byakugan.ROTATION));
        near(helper, JutsuCaster.masteryPower(10) / JutsuCaster.masteryPower(2), power10 / power2, "the cast itself scales");
        JutsuDefinition rotation = JutsuData.get(Byakugan.ROTATION);
        helper.assertTrue(JutsuCaster.cooldown(player, Byakugan.ROTATION, rotation) < rotation.cooldown() * 0.45f, "Lv 10 cooldown");
        helper.succeed();
    }

    /** The Hyūga's new clan numbers: strongest clan in the Leaf. */
    @GameTest(template = "empty", batch = BATCH)
    public static void hyugaPerkNumbers(GameTestHelper helper) {
        Player hyugaPlayer = member(helper, canon("hyuga"));
        Player plainPlayer = member(helper, TestClans.PLAIN);
        Player uchihaPlayer = member(helper, canon("uchiha"));
        ShinobiData hyuga = data(hyugaPlayer);
        ShinobiData plain = data(plainPlayer);
        helper.assertTrue(hyuga.getControl() >= plain.getControl() + 12f, "+12 CTL from the start: " + hyuga.getControl());
        helper.assertTrue(hyuga.getTaijutsu() >= plain.getTaijutsu() + 5f, "+5 Taijutsu from the start: " + hyuga.getTaijutsu());
        near(helper, 2.5f, Perks.mult(hyuga, Perk.GENTLE_FIST), "Gentle Fist ×2.5");
        near(helper, 2f, Perks.mult(hyuga, Perk.XP_CONTROL), "CTL trains twice as fast");
        near(helper, 2f, Perks.mult(hyuga, Perk.XP_TAIJUTSU), "Taijutsu too");
        helper.assertTrue(Perks.get(hyuga, Perk.PERCEPTION) > Perks.get(data(uchihaPlayer), Perk.PERCEPTION), "sharper eyes than the Uchiha");
        helper.assertTrue(CombatSystem.parryWindow(hyugaPlayer) > CombatSystem.parryWindow(plainPlayer), "a longer parry window");
        near(helper, 0.7f, CombatSystem.guardKept(hyugaPlayer), "a sturdier guard");
        near(helper, 1f, CombatSystem.guardKept(plainPlayer), "others' guards unchanged");
        // Canon costs stay: weak ninjutsu (but not on their own arts), and a blind spot until CTL 45.
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        near(helper, 0.7f, Perks.jutsuPowerMultiplier(hyugaPlayer, fireball, JutsuData.get(fireball)), "ninjutsu still weaker");
        near(helper, 1f, Perks.jutsuPowerMultiplier(hyugaPlayer, Byakugan.ROTATION, JutsuData.get(Byakugan.ROTATION)),
                "the clan's own arts aren't");
        helper.assertTrue(Byakugan.blindSpotDegrees(hyuga.getControl()) > 0f, "a fresh Hyūga still has a blind spot");
        helper.assertTrue(Byakugan.blindSpotDegrees(Byakugan.BLIND_SPOT_CLOSED_AT) == 0f && Byakugan.BLIND_SPOT_CLOSED_AT <= 45f,
                "it closes by CTL 45");
        helper.assertTrue(Byakugan.sightRange(hyuga.getControl()) >= 100, "a fresh Hyūga sees 100+ blocks: " + Byakugan.sightRange(hyuga.getControl()));
        helper.succeed();
    }
}

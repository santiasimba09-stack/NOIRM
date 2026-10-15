package com.noirm.gametest;

import static com.noirm.gametest.TestKit.face;
import static com.noirm.gametest.TestKit.floor;

import com.noirm.Noirm;
import com.noirm.clan.ClanSystem;
import com.noirm.combat.CombatSystem;
import com.noirm.dojutsu.EyeData;
import com.noirm.dojutsu.Mangekyo;
import com.noirm.dojutsu.MangekyoAbilities;
import com.noirm.dojutsu.MangekyoAbility;
import com.noirm.dojutsu.MangekyoNpcs;
import com.noirm.dojutsu.MangekyoTransplant;
import com.noirm.dojutsu.MangekyoTransplant.EyeInfo;
import com.noirm.dojutsu.MangekyoTransplant.Outcome;
import com.noirm.dojutsu.Sharingan;
import com.noirm.dojutsu.SharinganAwakening;
import com.noirm.dojutsu.Susanoo;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Mangekyō Sharingan: awakening by a deep loss or at the edge of death, strain and blindness,
 * transplants (Eternal, borrowed, upgraded, implanted, refused), the eye items, each ability's
 * effect on the server, Susanoo, and Mangekyō ninjas and the eyes they leave.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MangekyoGameTests {
    private MangekyoGameTests() {}

    // ------------------------------------------------------------ awakening

    @GameTest(template = "empty", batch = "noirm_ms_awaken")
    public static void aDeepLossAwakensTheMangekyo(GameTestHelper helper) {
        Player player = uchihaAt(helper, 0.5, 0.5, 3);
        Player young = uchihaAt(helper, 0.5, 2.5, 2);
        ShinobiData data = data(player);
        SparringNinja distant = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        SparringNinja devoted = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 2.5f);
        SparringNinja killer = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        distant.joinCrew(player.getUUID());
        devoted.joinCrew(player.getUUID());
        distant.setRespect(20);
        devoted.setRespect(80);

        SharinganAwakening.witnessDeath(player, distant, killer);
        helper.assertFalse(data.eyes().hasMangekyo(), "a crew mate you weren't close to doesn't open it");
        helper.assertTrue(SharinganAwakening.witnessDeath(player, devoted, killer), "a devoted crew mate's death...");
        helper.assertTrue(data.eyes().hasMangekyo() && data.eyes().isMangekyoActive(), "...awakens the Mangekyō, open");
        helper.assertTrue(data.eyes().left() != null && data.eyes().right() != null && data.eyes().left() != data.eyes().right(),
                "two different eye abilities");
        helper.assertTrue(data.jutsu().knows(data.eyes().left().jutsu()) && data.jutsu().knows(data.eyes().right().jutsu())
                && data.jutsu().knows(Mangekyo.SUSANOO), "both abilities and Susanoo are learned as jutsu");
        helper.assertTrue(data.eyes().pattern() >= 0 && data.eyes().pattern() < Mangekyo.PATTERNS, "a pattern of its own");
        helper.assertTrue(Sharingan.perceptionMultiplier(player) > 1.75f + 0.2f, "it sees more than 3 tomoe");

        helper.assertFalse(Mangekyo.onLoss(young, data(young), player), "2 tomoe aren't enough");
        helper.assertFalse(data(young).eyes().hasMangekyo(), "still a plain Sharingan");

        Player edge = uchihaAt(helper, 1.5, 0.5, 3);
        helper.assertFalse(Mangekyo.onResolveAwakening(edge, data(edge), 90f), "a near-death awakening at 90 Resolve: not yet");
        helper.assertTrue(Mangekyo.onResolveAwakening(edge, data(edge), 96f), "at 95+, with 3 tomoe, the Mangekyō");
        distant.discard();
        devoted.discard();
        killer.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------ strain and blindness

    @GameTest(template = "empty")
    public static void strainWeakensThenBlinds(GameTestHelper helper) {
        Player player = uchihaAt(helper, 1.5, 1.5, 3);
        ShinobiData data = data(player);
        Mangekyo.awaken(player, data, Mangekyo.Route.COMMAND);
        data.eyes().setAbilities(MangekyoAbility.AMATERASU, MangekyoAbility.KAMUI);
        Mangekyo.syncJutsu(data);
        ResourceLocation amaterasu = MangekyoAbility.AMATERASU.jutsu();
        helper.assertTrue(JutsuCaster.checkRequirements(player, amaterasu, JutsuData.get(amaterasu)) == JutsuCaster.Result.OK, "usable while open");
        helper.assertFalse(data.jutsu().knows(MangekyoAbility.TSUKUYOMI.jutsu()), "abilities the eyes don't carry aren't known");
        helper.assertTrue(JutsuCaster.checkRequirements(player, MangekyoAbility.TSUKUYOMI.jutsu(),
                JutsuData.get(MangekyoAbility.TSUKUYOMI.jutsu())) == JutsuCaster.Result.NEEDS_DOJUTSU, "nor usable");

        Mangekyo.addStrain(player, 69f);
        helper.assertTrue(Mangekyo.powerScale(player) == 1f && Mangekyo.strainTier(data.eyes().strain()) == 2, "69: blurred, full power");
        Mangekyo.addStrain(player, 1f);
        helper.assertTrue(Mangekyo.powerScale(player) == Mangekyo.WEAKENED_POWER, "70: weakened");
        Mangekyo.addStrain(player, 15f);
        helper.assertTrue(Mangekyo.powerScale(player) == Mangekyo.FAILING_POWER, "85: failing");
        Mangekyo.addStrain(player, 20f);
        helper.assertTrue(data.eyes().isBlind() && !data.eyes().isActive(), "100: blind, the eyes close");
        data.eyes().setActive(true);
        helper.assertFalse(data.eyes().isActive(), "and they won't open again");
        helper.assertTrue(JutsuCaster.checkRequirements(player, amaterasu, JutsuData.get(amaterasu)) == JutsuCaster.Result.NEEDS_DOJUTSU,
                "no eye techniques while blind");

        data.eyes().setEternal(true);
        data.eyes().setActive(true);
        Mangekyo.addStrain(player, 50f);
        helper.assertTrue(data.eyes().strain() == 0f && data.eyes().isActive(), "the Eternal Mangekyō feels no strain");
        helper.assertTrue(Mangekyo.powerScale(player) == Mangekyo.ETERNAL_POWER, "and is stronger");
        helper.succeed();
    }

    // ------------------------------------------------------------ transplants and items

    @GameTest(template = "empty")
    public static void transplantOutcomes(GameTestHelper helper) {
        EyeInfo msEyes = new EyeInfo(3, true, 5, MangekyoAbility.KAMUI, MangekyoAbility.IZANAGI, 0x22C878, 30f, "Donor");

        Player outsider = playerAt(helper, 0.5, 0.5, TestClans.PLAIN);
        helper.assertTrue(MangekyoTransplant.transplant(outsider, data(outsider), EyeInfo.sharingan(2, "x")) == Outcome.IMPLANTED, "implanted");
        EyeData eyes = data(outsider).eyes();
        helper.assertTrue(eyes.isTransplanted() && eyes.sharinganStage() == 2 && eyes.isActive(), "a one-eyed Sharingan, open");
        Player outsiderMs = playerAt(helper, 0.5, 1.5, TestClans.PLAIN);
        MangekyoTransplant.transplant(outsiderMs, data(outsiderMs), msEyes);
        EyeData one = data(outsiderMs).eyes();
        helper.assertTrue(one.hasMangekyo() && one.left() == MangekyoAbility.KAMUI && one.right() == null, "one Mangekyō eye: one ability");
        helper.assertFalse(data(outsiderMs).jutsu().knows(Mangekyo.SUSANOO), "no Susanoo with a single eye");

        Player young = uchihaAt(helper, 1.5, 0.5, 1);
        helper.assertTrue(MangekyoTransplant.transplant(young, data(young), EyeInfo.sharingan(3, "x")) == Outcome.UPGRADED
                && data(young).eyes().sharinganStage() == 3, "a stronger eye: 3 tomoe");
        helper.assertTrue(MangekyoTransplant.transplant(young, data(young), EyeInfo.sharingan(2, "x")) == Outcome.REJECTED_WEAKER
                && data(young).eyes().sharinganStage() == 3, "a weaker one is refused, nothing changes");

        Player borrower = uchihaAt(helper, 1.5, 1.5, 2);
        helper.assertTrue(MangekyoTransplant.transplant(borrower, data(borrower), msEyes) == Outcome.BORROWED, "borrowed");
        helper.assertTrue(data(borrower).eyes().pattern() == 5 && data(borrower).eyes().strain() == 30f
                && data(borrower).jutsu().knows(Mangekyo.SUSANOO), "the donor's pattern, strain and Susanoo");

        Player blind = uchihaAt(helper, 2.5, 0.5, 3);
        ShinobiData blindData = data(blind);
        Mangekyo.awaken(blind, blindData, Mangekyo.Route.COMMAND);
        blindData.eyes().setPattern(1);
        blindData.eyes().setStrain(100f);
        helper.assertTrue(blindData.eyes().isBlind(), "blind first");
        helper.assertTrue(MangekyoTransplant.transplant(blind, blindData, EyeInfo.sharingan(3, "x")) == Outcome.REJECTED_HAS_MANGEKYO, "plain eyes refused");
        helper.assertTrue(MangekyoTransplant.transplant(blind, blindData, msEyes) == Outcome.ETERNAL, "another Mangekyō: Eternal");
        helper.assertTrue(!blindData.eyes().isBlind() && blindData.eyes().isActive() && blindData.eyes().strain() == 0f, "sight restored");
        helper.assertTrue(blindData.eyes().pattern() == 1 && blindData.eyes().donorPattern() == 5, "own pattern kept, the donor's merged in");
        helper.assertTrue(MangekyoTransplant.transplant(blind, blindData, msEyes) == Outcome.REJECTED_ETERNAL, "nothing more to gain");

        Player hyuga = playerAt(helper, 2.5, 1.5, TestClans.BYAKUGAN);
        data(hyuga).eyes().setByakugan(true);
        helper.assertTrue(MangekyoTransplant.transplant(hyuga, data(hyuga), msEyes) == Outcome.REJECTED_BYAKUGAN, "the Byakugan rejects it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eyeItemsAndDataRoundTrip(GameTestHelper helper) {
        EyeInfo eye = new EyeInfo(3, true, 6, MangekyoAbility.TSUKUYOMI, MangekyoAbility.AMATERASU, 0xFFC020, 42.5f, "Itsuki");
        ItemStack stack = eye.toStack();
        helper.assertTrue(stack.getItem() == com.noirm.registry.NoirmItems.MANGEKYO_EYES.get(), "Mangekyō eyes item");
        helper.assertTrue(eye.equals(EyeInfo.of(stack)), "item NBT round trip: " + EyeInfo.of(stack));
        EyeInfo plain = EyeInfo.sharingan(2, "Ren");
        helper.assertTrue(plain.toStack().getItem() == com.noirm.registry.NoirmItems.SHARINGAN_EYE.get()
                && plain.equals(EyeInfo.of(plain.toStack())), "Sharingan eye item round trip");
        helper.assertTrue(EyeInfo.of(new ItemStack(com.noirm.registry.NoirmItems.SHARINGAN_EYE.get())) == null, "an empty eye holds nothing");

        EyeData eyes = new EyeData();
        eyes.setSharinganStage(3);
        eyes.setMangekyo(true);
        eyes.setPattern(4);
        eyes.setDonorPattern(2);
        eyes.setAbilities(MangekyoAbility.KOTOAMATSUKAMI, MangekyoAbility.IZANAGI);
        eyes.setSusanooColor(0x123456);
        eyes.setStrain(33f);
        eyes.setTransplanted(true);
        EyeData loaded = new EyeData();
        loaded.load(eyes.save());
        helper.assertTrue(loaded.hasMangekyo() && loaded.pattern() == 4 && loaded.donorPattern() == 2
                && loaded.left() == MangekyoAbility.KOTOAMATSUKAMI && loaded.right() == MangekyoAbility.IZANAGI
                && loaded.susanooColor() == 0x123456 && loaded.strain() == 33f && loaded.isTransplanted(), "player eye data round trip");
        helper.assertTrue(eye.equals(MangekyoTransplant.fromEyes(eyesOf(eye), "Itsuki")), "a player's eyes as an item");
        int look = Mangekyo.look(3, 7);
        helper.assertTrue(Mangekyo.lookPattern(look) == 3 && Mangekyo.lookDonor(look) == 7 && Mangekyo.lookDonor(Mangekyo.look(3, -1)) == -1, "look packing");
        helper.succeed();
    }

    // ------------------------------------------------------------ abilities

    @GameTest(template = "empty", batch = "noirm_ms_amaterasu", timeoutTicks = 120)
    public static void amaterasuBurnsThroughWater(GameTestHelper helper) {
        Player player = mangekyoUser(helper, MangekyoAbility.AMATERASU, MangekyoAbility.KAMUI);
        SparringNinja target = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 1.5f);
        face(player, target);
        cast(player, MangekyoAbility.AMATERASU.jutsu());
        helper.assertTrue(MangekyoAbilities.isBurning(target), "black flames on the target");
        helper.assertTrue(data(player).eyes().strain() == MangekyoAbility.AMATERASU.strain(), "the eyes strain");
        float start = target.getHealth();
        helper.runAfterDelay(50, () -> {
            target.clearFire(); // what water does to ordinary fire
            helper.assertTrue(MangekyoAbilities.isBurning(target), "water doesn't put them out");
            helper.assertTrue(target.getHealth() < start, "they burn");
            MangekyoAbilities.reset(target);
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noirm_ms_tsukuyomi")
    public static void tsukuyomiFreezesAndWoundsTheMind(GameTestHelper helper) {
        Player player = mangekyoUser(helper, MangekyoAbility.TSUKUYOMI, MangekyoAbility.KAMUI);
        SparringNinja target = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 1.5f);
        SparringNinja rival = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        MangekyoNpcs.makeMangekyo(rival, rival.getRandom());
        float health = target.getHealth();
        face(player, target);
        cast(player, MangekyoAbility.TSUKUYOMI.jutsu());
        helper.assertTrue(target.getHealth() <= health - target.getMaxHealth() * MangekyoAbilities.TSUKUYOMI_HEALTH_SHARE * 0.9f,
                "a quarter of their health torn away, got " + (health - target.getHealth()));
        helper.assertTrue(CombatSystem.isStaggered(target), "frozen in place");
        float rivalHealth = rival.getHealth();
        face(player, rival);
        data(player).jutsu().setCooldown(MangekyoAbility.TSUKUYOMI.jutsu(), 0);
        cast(player, MangekyoAbility.TSUKUYOMI.jutsu());
        helper.assertTrue(rival.getHealth() == rivalHealth, "another Mangekyō resists");
        target.discard();
        rival.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_ms_kamui")
    public static void kamuiPhasesAndIzanagiRewritesDeath(GameTestHelper helper) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        MangekyoAbilities.phase(ninja, 40);
        float health = ninja.getHealth();
        ninja.hurt(ninja.damageSources().generic(), 10f);
        helper.assertTrue(ninja.getHealth() == health && MangekyoAbilities.isPhased(ninja), "intangible: the hit passes through");
        MangekyoAbilities.reset(ninja);

        Vec3 start = ninja.position();
        MangekyoAbilities.arm(ninja, 100);
        ninja.moveTo(start.x + 0.5, start.y, start.z);
        ninja.invulnerableTime = 0;
        ninja.hurt(ninja.damageSources().generic(), 1000f);
        helper.assertTrue(ninja.isAlive() && ninja.getHealth() >= ninja.getMaxHealth() * MangekyoAbilities.IZANAGI_MIN_HEALTH,
                "Izanagi: the death doesn't happen");
        helper.assertTrue(ninja.position().distanceTo(start) < 0.1 && !MangekyoAbilities.isArmed(ninja), "back where it was cast, spent");
        ninja.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_ms_koto")
    public static void kotoamatsukamiTurnsAnEnemy(GameTestHelper helper) {
        Player player = mangekyoUser(helper, MangekyoAbility.KOTOAMATSUKAMI, MangekyoAbility.KAMUI);
        SparringNinja foe = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 1.5f);
        face(player, foe);
        cast(player, MangekyoAbility.KOTOAMATSUKAMI.jutsu());
        helper.assertTrue(MangekyoAbilities.controller(foe) == player, "its will is rewritten");
        foe.setTarget(player);
        helper.assertTrue(foe.getTarget() == null, "it won't target you");
        helper.assertTrue(data(player).eyes().strain() == MangekyoAbility.KOTOAMATSUKAMI.strain(), "a heavy strain");
        MangekyoAbilities.reset(foe);
        foe.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_ms_susanoo")
    public static void susanooGrowsGuardsAndSwipes(GameTestHelper helper) {
        Player player = mangekyoUser(helper, MangekyoAbility.AMATERASU, MangekyoAbility.KAMUI);
        SparringNinja foe = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 1.5f);
        face(player, foe);
        ShinobiData data = data(player);
        helper.assertTrue(Susanoo.summon(player, 1f) && Susanoo.stage(player) == 1, "a ribcage");
        helper.assertTrue(Susanoo.swipe(player, 1f) == 0, "no arms yet");
        float chakra = data.getChakra();
        for (int i = 0; i < Susanoo.GROW_TICKS * 2; i++) {
            Susanoo.tick(player);
        }
        helper.assertTrue(Susanoo.stage(player) == 3, "complete after two growth steps");
        helper.assertTrue(data.getChakra() < chakra && data.eyes().strain() > Susanoo.SUMMON_STRAIN, "it drains chakra and strains the eyes");
        helper.assertTrue(Susanoo.defenseMultiplier(player) == Susanoo.DEFENSE[3], "takes far less damage");
        float health = foe.getHealth();
        helper.assertTrue(Susanoo.swipe(player, 1f) == 1 && foe.getHealth() < health, "a giant swipe");
        data.eyes().setActive(false);
        Susanoo.tick(player);
        helper.assertFalse(Susanoo.isActive(player), "closing the eyes dismisses it");
        foe.discard();
        TestKit.clearAround(helper);
    }

    // ------------------------------------------------------------ NPCs and eyes left behind

    @GameTest(template = "empty", batch = "noirm_ms_npc")
    public static void mangekyoNinjasFightWithItAndLeaveTheirEyes(GameTestHelper helper) {
        floor(helper);
        Player player = uchihaAt(helper, 0.5, 1.5, 1);
        SparringNinja ms = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        SparringNinja victim = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        SparringNinja plain = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 2.5f);
        ms.joinClan(TestClans.SHARINGAN);
        plain.joinClan(TestClans.SHARINGAN);
        plain.setSharinganStage(2);
        com.noirm.dojutsu.MangekyoNpcs.makeMangekyo(ms, ms.getRandom());
        helper.assertTrue(Mangekyo.isActive(ms) && ms.mangekyoLook() != 0 && ms.activeSharinganStage() == 3, "a Mangekyō ninja");
        MangekyoAbility[] abilities = MangekyoNpcs.abilities(ms);
        helper.assertTrue(ms.knownJutsu().contains(abilities[0].jutsu()) && ms.knownJutsu().contains(Mangekyo.SUSANOO), "with its jutsu");

        ms.setTarget(victim);
        face(ms, victim);
        ResourceLocation amaterasu = MangekyoAbility.AMATERASU.jutsu();
        helper.assertTrue(JutsuCaster.npcCast(ms, amaterasu, 1f) && MangekyoAbilities.isBurning(victim), "it uses Amaterasu on its target");
        MangekyoAbilities.reset(victim);

        var byPlayer = player.damageSources().playerAttack(player);
        var byNothing = player.damageSources().generic();
        ItemStack msDrop = MangekyoNpcs.eyesFor(ms, byNothing, 0.99f);
        EyeInfo msEye = EyeInfo.of(msDrop);
        helper.assertTrue(msEye != null && msEye.mangekyo() && msEye.left() == abilities[0] && msEye.right() == abilities[1],
                "a Mangekyō ninja always leaves its eyes, abilities and all");
        helper.assertTrue(EyeInfo.of(MangekyoNpcs.eyesFor(plain, byPlayer, 0.05f)).stage() == 2, "a beaten Uchiha, rarely");
        helper.assertTrue(MangekyoNpcs.eyesFor(plain, byPlayer, 0.5f).isEmpty(), "usually not");
        helper.assertTrue(MangekyoNpcs.eyesFor(plain, byNothing, 0.05f).isEmpty(), "not when no one on a player's side did it");
        plain.joinCrew(player.getUUID());
        helper.assertTrue(!MangekyoNpcs.eyesFor(plain, byNothing, 0.99f).isEmpty(), "a fallen crew Uchiha always leaves its eye");
        ms.discard();
        victim.discard();
        plain.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static void cast(Player player, ResourceLocation id) {
        com.noirm.jutsu.JutsuBehaviors.get(JutsuData.get(id).behavior()).cast(new JutsuContext(player, id, JutsuData.get(id), 1f, 1f));
    }

    private static Player mangekyoUser(GameTestHelper helper, MangekyoAbility left, MangekyoAbility right) {
        Player player = uchihaAt(helper, 0.5, 1.5, 3);
        ShinobiData data = data(player);
        Mangekyo.awaken(player, data, Mangekyo.Route.COMMAND);
        data.eyes().setAbilities(left, right);
        Mangekyo.syncJutsu(data);
        data.eyes().setActive(true);
        return player;
    }

    private static EyeData eyesOf(EyeInfo eye) {
        EyeData eyes = new EyeData();
        eyes.setSharinganStage(3);
        eyes.setMangekyo(true);
        eyes.setPattern(eye.pattern());
        eyes.setAbilities(eye.left(), eye.right());
        eyes.setSusanooColor(eye.color());
        eyes.setStrain(eye.strain());
        return eyes;
    }

    private static Player uchihaAt(GameTestHelper helper, double x, double z, int tomoe) {
        Player player = playerAt(helper, x, z, TestClans.SHARINGAN);
        for (int i = 0; i < tomoe; i++) {
            Sharingan.advance(player, data(player), "x");
        }
        return player;
    }

    private static Player playerAt(GameTestHelper helper, double x, double z, com.noirm.clan.Clan clan) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        ClanSystem.assign(player, data(player), clan);
        return player;
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow();
    }
}

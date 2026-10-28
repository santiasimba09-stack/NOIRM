package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.combat.CombatSystem;
import com.noirm.dojutsu.MangekyoTransplant;
import com.noirm.entity.MasterNinja;
import com.noirm.guide.FirstSteps;
import com.noirm.guide.GuideItems;
import com.noirm.guide.Handbook;
import com.noirm.guide.HandbookItem;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.master.MasterData;
import com.noirm.master.MasterPerks;
import com.noirm.master.MasterSystem;
import com.noirm.medical.AntidoteItem;
import com.noirm.medical.ChakraScalpel;
import com.noirm.medical.EyeSurgery;
import com.noirm.medical.MedicTrial;
import com.noirm.medical.MedicalEvents;
import com.noirm.medical.MedicalItems;
import com.noirm.mission.PlayerRank;
import com.noirm.nature.NatureDrills;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.village.VillageKind;
import com.noirm.world.village.VillagePalette;
import com.noirm.world.village.VillagePlan;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * APX "more masters and polish": Rensai the Scholar (ninjutsu), Tsubaki the medic, their trials and
 * lessons (rank gates, ryo, perks, medical jutsu, eye transplants), where they live, and the
 * Shinobi Handbook with its first steps.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApxMastersGameTests {
    private static final ResourceLocation SCHOLAR = MasterData.SCHOLAR;
    private static final ResourceLocation MEDIC = MasterData.MEDIC;
    private static final ResourceLocation EMBER_NET = Noirm.id("katon/ember_net");
    private static final ResourceLocation HEALING_PALM = Noirm.id("medical/healing_palm");
    private static final ResourceLocation SCALPEL = Noirm.id("medical/chakra_scalpel");
    private static final ResourceLocation FIREBALL = Noirm.id("katon/great_fireball");

    private ApxMastersGameTests() {}

    // ---------------------------------------------------------------- data and placement

    @GameTest(template = "empty", batch = "noirm_apxm_data")
    public static void newMastersComeFromData(GameTestHelper helper) {
        floor(helper);
        helper.assertTrue(MasterData.get(SCHOLAR) != null && MasterData.get(MEDIC) != null, "both masters' JSON is loaded");
        helper.assertTrue(MasterSystem.JUTSU_TRIAL.equals(MasterData.get(SCHOLAR).trial()), "Rensai tests with a jutsu duel");
        helper.assertTrue(MedicTrial.KIND.equals(MasterData.get(MEDIC).trial()), "Tsubaki tests with a patient");
        for (var id : List.of(SCHOLAR, MEDIC)) {
            for (var lesson : MasterData.get(id).lessons()) {
                helper.assertTrue(lesson.teachesJutsu() ? JutsuData.get(lesson.jutsu()) != null : MasterPerks.known(lesson),
                        id + "/" + lesson.id() + " teaches something real");
                helper.assertTrue(lesson.rank().isEmpty() || lesson.neededRank() != null, lesson.id() + " names a real rank");
            }
        }
        MasterNinja rensai = master(helper, SCHOLAR);
        helper.assertTrue(rensai.outfit().startsWith("ninjutsu_scholar:"), "Rensai's own look, got " + rensai.outfit());
        helper.assertTrue(rensai.knownJutsu().size() == 3, "he duels with jutsu: " + rensai.knownJutsu());
        for (Nature n : Nature.BASIC) {
            helper.assertTrue(rensai.jutsuAffinity(n) > 0f, "he has " + n);
        }
        Player player = player(helper);
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.PLAIN); // water only
        helper.assertTrue(NatureDrills.teacher(player, Nature.FIRE) == 2, "he counts as a master teacher for the fire drill");
        // Saved and loaded, still himself (natures and jutsu come back with him).
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        rensai.saveWithoutId(tag);
        helper.assertTrue(SCHOLAR.toString().equals(tag.getString("Master")), "his id is saved");
        rensai.discard();
        MasterNinja tsubaki = master(helper, MEDIC);
        helper.assertTrue(tsubaki.outfit().startsWith("medic:"), "Tsubaki's own look, got " + tsubaki.outfit());
        helper.assertTrue(tsubaki.knownJutsu().isEmpty(), "she doesn't fight");
        tsubaki.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apxm_village")
    public static void newMastersLiveInEveryCapital(GameTestHelper helper) {
        for (VillagePalette palette : VillagePalette.values()) {
            VillagePlan plan = VillagePlan.uncached(VillageKind.CAPITAL, palette, 77L);
            VillagePlan.Lot academy = plan.first(VillagePlan.LotType.ACADEMY);
            VillagePlan.Lot hospital = plan.first(VillagePlan.LotType.HOSPITAL);
            if (palette == VillagePalette.LEAF) {
                helper.assertTrue(academy != null && hospital != null, "a Leaf capital has an Academy and a hospital");
            }
            // Wherever the Academy and the hospital are, the masters are inside them (and only there).
            helper.assertTrue(academy == null || plan.spawns.stream().anyMatch(s -> s.role() == VillagePlan.Role.SCHOLAR && inside(academy, s)),
                    palette + ": Rensai teaches inside the Academy");
            helper.assertTrue(hospital == null || plan.spawns.stream().anyMatch(s -> s.role() == VillagePlan.Role.MEDIC && inside(hospital, s)),
                    palette + ": Tsubaki works inside the hospital");
            helper.assertTrue(plan.spawns.stream().filter(s -> s.role() == VillagePlan.Role.MEDIC).count() == (hospital == null ? 0 : 1),
                    palette + ": one medic per hospital");
        }
        VillagePlan town = VillagePlan.uncached(VillageKind.TOWN, VillagePalette.LEAF, 77L);
        helper.assertFalse(town.spawns.stream().anyMatch(s -> s.role() == VillagePlan.Role.SCHOLAR), "towns have no Academy master");
        helper.succeed();
    }

    private static boolean inside(VillagePlan.Lot lot, VillagePlan.Spawn s) {
        return s.x() >= lot.x1() && s.x() <= lot.x2() && s.z() >= lot.z1() && s.z() <= lot.z2();
    }

    // ---------------------------------------------------------------- Rensai: the jutsu duel and his lessons

    @GameTest(template = "empty", batch = "noirm_apxm_duel", timeoutTicks = 60)
    public static void jutsuDuelCountsOnlyJutsuHits(GameTestHelper helper) {
        floor(helper);
        MasterNinja rensai = master(helper, SCHOLAR);
        Player player = player(helper);
        ShinobiData data = data(player);
        helper.assertTrue(MasterSystem.startSpar(player, rensai), "the duel starts");
        helper.assertTrue(rensai.isSparring(), "he's duelling");
        for (int i = 0; i < 3; i++) {
            rensai.invulnerableTime = 0;
            rensai.hurt(rensai.damageSources().playerAttack(player), 1f);
        }
        helper.assertTrue(rensai.cleanHits() == 0, "punches don't count in a jutsu duel, got " + rensai.cleanHits());
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(rensai.isSparring(), "still duelling before jutsu hit " + (i + 1));
            rensai.invulnerableTime = 0;
            rensai.hurt(rensai.damageSources().indirectMagic(player, player), 1f);
        }
        helper.assertFalse(rensai.isSparring(), "four jutsu hits end it");
        helper.assertTrue(data.masters().passed(SCHOLAR.toString()), "and pass the test");
        rensai.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apxm_scholar")
    public static void scholarLessonsNeedRankRyoAndNature(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.PLAIN); // water only
        var def = MasterData.get(SCHOLAR);
        MasterSystem.passTest(player, SCHOLAR);
        data.setControl(12);
        // Swift Seals: genin, 150 ryo.
        helper.assertTrue(key(MasterSystem.lessonBlocker(data, SCHOLAR, def, def.lesson("swift_seals").orElseThrow()), "screen.noirm.master.why.rank"),
                "an Academy student is too junior");
        data.career().setRank(PlayerRank.GENIN);
        helper.assertTrue(key(MasterSystem.lessonBlocker(data, SCHOLAR, def, def.lesson("swift_seals").orElseThrow()), "screen.noirm.master.why.ryo"),
                "and it costs ryo");
        data.career().setRyo(200);
        int sealsBefore = MasterPerks.sealTicks(player, 10);
        helper.assertTrue(MasterSystem.learn(player, SCHOLAR, "swift_seals"), "Swift Seals learned");
        helper.assertTrue(data.career().ryo() == 50, "150 ryo paid, " + data.career().ryo() + " left");
        helper.assertTrue(sealsBefore == 10 && MasterPerks.sealTicks(player, 10) == 6, "seals take 60% of the time: " + MasterPerks.sealTicks(player, 10));
        helper.assertFalse(MasterSystem.learn(player, SCHOLAR, "swift_seals"), "a perk is learned once");
        // A nature jutsu: chūnin, and only for a nature you have.
        data.career().setRank(PlayerRank.CHUNIN);
        data.career().setRyo(5000);
        helper.assertTrue(key(MasterSystem.lessonBlocker(data, SCHOLAR, def, def.lesson("ember_net").orElseThrow()), "screen.noirm.master.why.nature"),
                "Ember Net needs the fire nature");
        helper.assertTrue(MasterSystem.learn(player, SCHOLAR, "tide_lance"), "Tide Lance fits a water user");
        NatureSystem.grant(data, Nature.FIRE, 0.5f);
        helper.assertTrue(MasterSystem.learn(player, SCHOLAR, "ember_net") && data.jutsu().knows(EMBER_NET), "Ember Net once fire is yours");
        helper.assertTrue(MasterData.taughtByMaster(JutsuData.get(EMBER_NET)), "a master's own jutsu");
        // Chakra Efficiency: jōnin, CTL 20; the cut grows with CTL.
        var fireball = JutsuData.get(FIREBALL);
        data.setControl(20);
        float before = JutsuCaster.cost(player, FIREBALL, fireball);
        helper.assertTrue(key(MasterSystem.lessonBlocker(data, SCHOLAR, def, def.lesson("chakra_efficiency").orElseThrow()), "screen.noirm.master.why.rank"),
                "Chakra Efficiency is for jōnin");
        data.career().setRank(PlayerRank.JONIN);
        helper.assertTrue(MasterSystem.learn(player, SCHOLAR, "chakra_efficiency"), "Chakra Efficiency learned");
        float after = JutsuCaster.cost(player, FIREBALL, fireball);
        helper.assertTrue(Math.abs(after / before - 0.84f) < 0.01f, "16% cheaper at CTL 20: " + before + " -> " + after);
        data.setControl(40);
        helper.assertTrue(Math.abs(MasterPerks.costMultiplier(data) - 0.75f) < 1e-4, "capped at 25% off");
        // Nature Insight: half a drill of a hidden nature (once the paper showed it).
        data.career().setRank(PlayerRank.GENIN);
        helper.assertTrue(key(MasterSystem.lessonBlocker(data, SCHOLAR, def, def.lesson("nature_insight").orElseThrow()), "screen.noirm.master.why.insight"),
                "nothing to study yet");
        data.natureTraining().setHidden(List.of(Nature.EARTH));
        data.natureTraining().setRevealed(true);
        helper.assertTrue(MasterSystem.learn(player, SCHOLAR, "nature_insight"), "Nature Insight learned");
        helper.assertTrue(Math.abs(NatureDrills.share(data, Nature.EARTH) - MasterPerks.INSIGHT_BOOST) < 1e-3,
                "half the earth drill is behind you: " + NatureDrills.share(data, Nature.EARTH));
        helper.succeed();
    }

    // ---------------------------------------------------------------- Tsubaki: the patient and her lessons

    @GameTest(template = "empty", batch = "noirm_apxm_patient", timeoutTicks = 100)
    public static void medicTrialNeedsCalmChakra(GameTestHelper helper) {
        floor(helper);
        MasterNinja tsubaki = master(helper, MEDIC);
        Player player = player(helper);
        ShinobiData data = data(player);
        // 1. Hands idle: the patient bleeds out and she steps in.
        helper.assertTrue(MasterSystem.startSpar(player, tsubaki), "the test starts");
        MedicTrial.Trial trial = MedicTrial.trial(tsubaki);
        helper.assertTrue(trial != null && trial.patient().getTags().contains(MedicTrial.PATIENT_TAG), "a tagged patient is brought in");
        Villager patient = trial.patient();
        helper.assertTrue(patient.isNoAi() && patient.isInvulnerable(), "the patient lies still and can't be hurt");
        helper.assertTrue(MasterSystem.sparBlocker(player, tsubaki) != null, "she's busy meanwhile");
        for (int i = 0; i < 2000 && MedicTrial.trial(tsubaki) != null; i++) {
            MedicTrial.tick(tsubaki);
        }
        helper.assertTrue(MedicTrial.trial(tsubaki) == null && !data.masters().passed(MEDIC.toString()), "lost the patient: no pass");
        helper.assertTrue(patient.isRemoved(), "the patient is carried off");
        // 2. Crouched beside them, chakra flowing: they live, and you pass.
        player.setShiftKeyDown(true);
        data.setChakra(data.getMaxChakra());
        helper.assertTrue(MasterSystem.startSpar(player, tsubaki), "a second try");
        trial = MedicTrial.trial(tsubaki);
        helper.assertTrue(player.distanceTo(trial.patient()) <= MedicTrial.FLOW_RANGE, "the patient lies within reach: " + player.distanceTo(trial.patient()));
        float chakra = data.getChakra();
        for (int i = 0; i < 2000 && MedicTrial.trial(tsubaki) != null; i++) {
            MedicTrial.tick(tsubaki);
        }
        helper.assertTrue(data.masters().passed(MEDIC.toString()), "kept them alive: passed");
        helper.assertTrue(data.getChakra() < chakra, "it cost chakra: " + chakra + " -> " + data.getChakra());
        player.setShiftKeyDown(false);
        tsubaki.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apxm_medic")
    public static void medicLessonsHealCutAndCure(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        ShinobiData data = data(player);
        MasterSystem.passTest(player, MEDIC);
        data.career().setRank(PlayerRank.GENIN);
        data.career().setRyo(2000);
        // Healing Palm: no nature needed; heals over time and costs chakra.
        helper.assertTrue(MasterSystem.learn(player, MEDIC, "healing_palm") && data.jutsu().knows(HEALING_PALM), "Healing Palm learned");
        var palm = JutsuData.get(HEALING_PALM);
        helper.assertTrue(JutsuCaster.checkRequirements(player, HEALING_PALM, palm) == JutsuCaster.Result.OK, "anyone can use it");
        helper.assertTrue(JutsuCaster.cost(player, HEALING_PALM, palm) > 0f, "it costs chakra");
        player.setHealth(6f);
        JutsuContext palmCtx = new JutsuContext(player, HEALING_PALM, palm, 1f, 1f);
        for (int t = 0; t < 40; t++) {
            JutsuBehaviors.get(palm.behavior()).channel(palmCtx, t);
        }
        helper.assertTrue(player.getHealth() > 10f, "two seconds of it heal you: " + player.getHealth());
        // The Chakra Scalpel: chūnin, CTL 16, Healing Palm practised.
        helper.assertFalse(MasterSystem.learn(player, MEDIC, "chakra_scalpel"), "not yet");
        data.career().setRank(PlayerRank.CHUNIN);
        data.setControl(16);
        data.jutsu().addXp(HEALING_PALM, 10); // ★1
        helper.assertTrue(MasterSystem.learn(player, MEDIC, "chakra_scalpel") && data.jutsu().knows(SCALPEL), "the Chakra Scalpel learned");
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1.5f, 1f, 2.5f);
        helper.assertTrue(ChakraScalpel.cut(player, pig, player.damageSources().playerAttack(player)) == 0f, "no scalpels up yet");
        ChakraScalpel.cast(new JutsuContext(player, SCALPEL, JutsuData.get(SCALPEL), 1f, 1f));
        helper.assertTrue(ChakraScalpel.active(player), "scalpels up");
        helper.assertTrue(ChakraScalpel.cut(player, pig, player.damageSources().indirectMagic(player, player)) == 0f, "jutsu aren't scalpel cuts");
        float extra = ChakraScalpel.cut(player, pig, player.damageSources().playerAttack(player));
        helper.assertTrue(extra >= 3f, "a strike cuts deeper: +" + extra);
        helper.assertTrue(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && pig.hasEffect(MobEffects.WEAKNESS), "and cuts the muscles");
        ChakraScalpel.end(player);
        pig.discard();
        // Poison Lore: botched antidotes before, working ones after; poisons weaker.
        ItemStack before = new ItemStack(MedicalItems.ANTIDOTE.get());
        MedicalEvents.onCrafted(player, before);
        helper.assertTrue(AntidoteItem.botched(before), "mixed by an untrained hand: botched");
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        helper.assertFalse(AntidoteItem.apply(player, before), "a botched antidote cures nothing");
        helper.assertTrue(player.hasEffect(MobEffects.POISON), "still poisoned");
        player.removeAllEffects();
        helper.assertTrue(MasterSystem.learn(player, MEDIC, "poison_lore"), "Poison Lore learned");
        helper.assertTrue(player.getInventory().countItem(MedicalItems.ANTIDOTE.get()) == MasterPerks.LORE_ANTIDOTES, "with antidotes to start");
        ItemStack after = new ItemStack(MedicalItems.ANTIDOTE.get());
        MedicalEvents.onCrafted(player, after);
        helper.assertFalse(AntidoteItem.botched(after), "now they come out right");
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        MobEffectInstance poison = player.getEffect(MobEffects.POISON);
        helper.assertTrue(poison != null && poison.getDuration() == 100 && poison.getAmplifier() == 0,
                "poison half as long and a level weaker: " + poison);
        helper.assertTrue(AntidoteItem.apply(player, after) && !player.hasEffect(MobEffects.POISON), "a good antidote cures it");
        player.removeAllEffects();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apxm_eyes")
    public static void eyeTransplantsBelongToTheMedic(GameTestHelper helper) {
        floor(helper);
        Player player = player(helper);
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.PLAIN);
        MasterSystem.passTest(player, MEDIC);
        data.career().setRank(PlayerRank.JONIN);
        data.career().setRyo(1500);
        helper.assertFalse(MasterSystem.learn(player, MEDIC, "eye_transplant"), "no eye, no surgery");
        helper.assertTrue(data.career().ryo() == 1500, "and nothing paid");
        player.getInventory().add(MangekyoTransplant.EyeInfo.sharingan(2, "Itsuki").toStack());
        helper.assertTrue(MasterSystem.learn(player, MEDIC, "eye_transplant"), "the medic operates");
        helper.assertTrue(data.eyes().sharinganStage() == 2 && data.eyes().isTransplanted(), "a 2-tomoe eye, implanted");
        helper.assertTrue(data.career().ryo() == 0, "1500 ryo paid");
        helper.assertTrue(player.getInventory().countItem(com.noirm.registry.NoirmItems.SHARINGAN_EYE.get()) == 0, "the eye is used up");
        helper.assertFalse(data.masters().learned(MEDIC.toString(), "eye_transplant"), "a service is never 'learned'");
        // Operating on yourself: a bad roll ruins the eye.
        Player self = player(helper);
        ShinobiData selfData = data(self);
        ClanSystem.assign(self, selfData, TestClans.PLAIN);
        ItemStack eye = MangekyoTransplant.EyeInfo.sharingan(3, "x").toStack();
        float health = self.getHealth();
        helper.assertFalse(EyeSurgery.selfSurgery(self, selfData, eye, 0f), "your hands slip");
        helper.assertTrue(eye.isEmpty() && selfData.eyes().sharinganStage() == 0, "the eye is ruined and nothing changes");
        helper.assertTrue(self.getHealth() < health && self.hasEffect(MobEffects.BLINDNESS), "and it hurts");
        self.setHealth(self.getMaxHealth());
        ItemStack second = MangekyoTransplant.EyeInfo.sharingan(3, "x").toStack();
        helper.assertTrue(EyeSurgery.selfSurgery(self, selfData, second, 0.9f), "a good roll: it takes");
        helper.assertTrue(selfData.eyes().sharinganStage() == 3 && second.isEmpty(), "three tomoe");
        helper.assertTrue(self.getHealth() < self.getMaxHealth(), "but it still hurts");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the handbook and the first steps

    @GameTest(template = "empty", batch = "noirm_apxm_guide")
    public static void handbookIsGivenOnceAndTipsFollowYou(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        FirstSteps.tick(player, data, 0);
        helper.assertFalse(data.guide().handbookGiven(), "not before the character exists");
        ClanSystem.assign(player, data, TestClans.PLAIN);
        FirstSteps.tick(player, data, 0);
        helper.assertTrue(data.guide().handbookGiven() && count(player) == 1, "the handbook, once creation is done");
        FirstSteps.tick(player, data, 20);
        player.getInventory().clearContent();
        FirstSteps.tick(player, data, 40);
        helper.assertTrue(count(player) == 0, "given once, ever (not again after you drop it)");
        // The tips: one at a time, each ticked off when you do it.
        helper.assertTrue(data.guide().step() == 0, "first: open the handbook");
        FirstSteps.tick(player, data, 1000);
        helper.assertTrue(data.guide().reminders() == 1, "the tip is shown");
        HandbookItem.opened(player);
        FirstSteps.tick(player, data, 1020);
        helper.assertTrue(data.guide().step() == 1, "opened: next, combat mode");
        CombatSystem.setCombatMode(player, true);
        FirstSteps.tick(player, data, 1040);
        helper.assertTrue(data.guide().step() == 2, "combat mode tried: next, the missions board");
        CombatSystem.setCombatMode(player, false);
        data.guide().setUsedBoard(true);
        FirstSteps.tick(player, data, 1060);
        helper.assertTrue(data.guide().step() == FirstSteps.Step.values().length, "all done");
        // Skippable: with the tips off, nothing more is said.
        data.guide().reset();
        data.guide().setHandbookGiven(true);
        data.guide().setTipsOff(true);
        FirstSteps.tick(player, data, 5000);
        helper.assertTrue(data.guide().reminders() == 0, "tips off: no tips");
        var tip = FirstSteps.tip(FirstSteps.Step.COMBAT, 1);
        helper.assertTrue(tip.getString().contains("[") || !tip.getSiblings().isEmpty(), "a tip carries the skip link");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(Noirm.id("shinobi_handbook")).isPresent(), "the handbook can be crafted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_apxm_guide")
    public static void handbookChaptersHaveTextInEveryLanguage(GameTestHelper helper) {
        helper.assertTrue(Handbook.CHAPTERS.size() >= 10, "ten chapters or more");
        for (String lang : List.of("en_us", "es_es", "es_mx")) {
            JsonObject json = lang(lang);
            for (Handbook.Chapter chapter : Handbook.CHAPTERS) {
                helper.assertTrue(json.has(chapter.titleKey()) && json.has(chapter.bodyKey()), lang + " has chapter " + chapter.id());
                for (ResourceLocation icon : chapter.icons()) {
                    helper.assertTrue(ForgeRegistries.ITEMS.containsKey(icon), "chapter " + chapter.id() + " draws a real item: " + icon);
                }
            }
            for (String key : List.of("combat_mode", "light", "heavy", "guard", "dodge", "lock_on", "grab", "style", "jutsu", "jutsu_screen",
                    "meditate", "crew", "eyes", "cloak")) {
                helper.assertTrue(json.has("handbook.noirm.key." + key), lang + " names the control " + key);
            }
            for (var id : List.of(SCHOLAR, MEDIC)) {
                var def = MasterData.get(id);
                helper.assertTrue(json.has(def.name()) && json.has(def.greeting()), lang + " names " + id);
                for (var lesson : def.lessons()) {
                    helper.assertTrue(json.has(lesson.titleKey()), lang + " names lesson " + lesson.titleKey());
                }
            }
        }
        helper.assertTrue(GuideItems.HANDBOOK.get() instanceof HandbookItem, "the handbook item");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static JsonObject lang(String code) {
        try (var in = ApxMastersGameTests.class.getResourceAsStream("/assets/noirm/lang/" + code + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("can't read " + code, e);
        }
    }

    private static int count(Player player) {
        return player.getInventory().countItem(GuideItems.HANDBOOK.get());
    }

    private static boolean key(net.minecraft.network.chat.Component c, String key) {
        return c != null && c.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
    }

    private static MasterNinja master(GameTestHelper helper, ResourceLocation id) {
        MasterNinja master = helper.spawnWithNoFreeWill(NoirmEntities.MASTER.get(), 1.5f, 1f, 1.5f);
        master.setMaster(id);
        return master;
    }

    /** A mock player standing in the test area. */
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.moveTo(at.x, at.y, at.z);
        return player;
    }
}

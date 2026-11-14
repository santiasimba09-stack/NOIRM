package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.CastType;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.jutsu.MedicalTechniques;
import com.noirm.jutsu.ModeSystem;
import com.noirm.jutsu.NpcJutsuSets;
import com.noirm.jutsu.SpiritTechniques;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chakra wave, part 1: the new Yin, Yang, Yin-Yang and medical jutsu. Each loads with seals and
 * text, casts without error, and does its one job; genjutsu has its counterplay (Kai, friends).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritGameTests {
    static final List<String> NEW_JUTSU = List.of(
            "universal/genjutsu_release", "yin/false_surroundings", "yin/shadow_neck_bind", "yin/shadow_stitching", "yin/tree_binding_death",
            "yang/rapid_regeneration", "yang/yang_vitality", "yang/super_expansion_fist", "yang/life_force_conversion", "yang/life_reincarnation",
            "yin_yang/renewal", "yin_yang/attraction", "yin_yang/repulsion", "yin_yang/chakra_hawks", "yin_yang/equilibrium_collapse",
            "medical/deep_mend", "medical/scalpel_strike", "medical/poison_mist", "medical/poison_extraction", "medical/healing_field",
            "medical/body_pathway_derangement", "medical/hundred_healings");

    private SpiritGameTests() {}

    @GameTest(template = "empty")
    public static void spiritJutsuLoadWithSealsAndText(GameTestHelper helper) {
        List<JsonObject> langs = List.of(lang("en_us"), lang("es_es"), lang("es_mx"));
        for (String path : NEW_JUTSU) {
            ResourceLocation id = Noirm.id(path);
            var def = JutsuData.get(id);
            helper.assertTrue(def != null, "missing " + path);
            helper.assertTrue(JutsuBehaviors.exists(def.behavior()), "unknown behavior for " + path);
            helper.assertTrue(JutsuSeals.get(id) != null, "no hand seals for " + path);
            for (JsonObject lang : langs) {
                helper.assertTrue(lang.has("jutsu.noirm." + path.replace('/', '.')), "no name for " + path);
                helper.assertTrue(lang.has("screen.noirm.jutsu.behavior." + def.behavior()), "no behavior text for " + def.behavior());
            }
            boolean learnable = def.unlock().isPresent() || com.noirm.master.MasterData.taughtByMaster(def);
            helper.assertTrue(learnable, path + " can be learned");
        }
        // NPCs: Kai and Reincarnation are never in a set; medical jutsu need Yang.
        var kai = JutsuData.get(Noirm.id("universal/genjutsu_release"));
        helper.assertFalse(NpcJutsuSets.eligibleForTest(Noirm.id("universal/genjutsu_release"), kai, Map.of(Nature.YIN, 1f)), "no Kai in sets");
        var mist = JutsuData.get(Noirm.id("medical/poison_mist"));
        helper.assertFalse(NpcJutsuSets.eligibleForTest(Noirm.id("medical/poison_mist"), mist, Map.of(Nature.FIRE, 1f)), "medics need Yang");
        helper.assertTrue(NpcJutsuSets.eligibleForTest(Noirm.id("medical/poison_mist"), mist, Map.of(Nature.YANG, 1f)), "a Yang ninja can know it");
        helper.succeed();
    }

    /** Every new jutsu runs once from one caster, at an enemy in front, without errors. */
    @GameTest(template = "empty", batch = "noirm_spirit_all", timeoutTicks = 120)
    public static void everySpiritJutsuCasts(GameTestHelper helper) {
        pad(helper, 0, 8);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        for (String path : NEW_JUTSU) {
            var def = JutsuData.get(Noirm.id(path));
            run(caster, path, 1f);
            if (def.cast() == CastType.CHANNEL) {
                for (int t = 1; t <= 5; t++) {
                    channel(caster, path, t);
                }
            }
        }
        helper.runAfterDelay(60, () -> {
            caster.discard();
            enemy.discard();
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Yin

    @GameTest(template = "empty", batch = "noirm_spirit_kai")
    public static void kaiBreaksAGenjutsuEvenWhileStunned(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 0.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja victim = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        SpiritTechniques.catchIn(caster, victim, SpiritTechniques.Illusion.TREE, 60, 10f, 10f, 0);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(SpiritTechniques.inIllusion(victim) && CombatSystem.isStaggered(victim), "held by the tree");
            run(victim, "universal/genjutsu_release", 1f);
            helper.assertFalse(SpiritTechniques.inIllusion(victim), "Kai broke it");
            helper.assertFalse(CombatSystem.isStaggered(victim), "and the stun with it");
            helper.assertTrue(victim.getHealth() == victim.getMaxHealth(), "no strike lands");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_spirit_kai_mate")
    public static void kaiFreesACrewMateAndAFriendWakesYou(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 0.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja victim = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        SparringNinja mate = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        SparringNinja far = ninja(helper, 0.5f, 2.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        victim.joinCrew(leader);
        mate.joinCrew(leader);
        SpiritTechniques.catchIn(caster, victim, SpiritTechniques.Illusion.MIRAGE, 200, 0, 0, 3);
        run(mate, "universal/genjutsu_release", 1f);
        helper.assertFalse(SpiritTechniques.inIllusion(victim), "the crew mate's Kai frees them");
        // A crew mate standing close for a second shakes them awake too.
        SpiritTechniques.catchIn(caster, victim, SpiritTechniques.Illusion.MIRAGE, 200, 0, 0, 3);
        far.discard();
        helper.runAfterDelay(25, () -> {
            helper.assertFalse(SpiritTechniques.inIllusion(victim), "shaken awake by a friend beside them");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_spirit_mirage")
    public static void falseSurroundingsTurnsAttacksAside(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 0.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja victim = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        caster.setInvulnerable(false);
        SpiritTechniques.catchIn(caster, victim, SpiritTechniques.Illusion.MIRAGE, 200, 0, 0, 2);
        caster.hurt(caster.damageSources().mobAttack(victim), 4f);
        caster.invulnerableTime = 0;
        caster.hurt(caster.damageSources().mobAttack(victim), 4f);
        caster.invulnerableTime = 0;
        helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "two blows strike a false image");
        helper.assertTrue(SpiritTechniques.missesLeft(victim) == 0, "and use the illusion up");
        caster.hurt(caster.damageSources().mobAttack(victim), 4f);
        helper.assertTrue(caster.getHealth() < caster.getMaxHealth(), "the third lands");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_mirage_mobs")
    public static void falseSurroundingsTurnsCreaturesOnTheirOwn(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 0.5f, 0.5f, TestClans.EVERY_NATURE);
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2.5f, 1f, 2.5f);
        Zombie other = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2.5f, 1f, 0.5f);
        victim.setTarget(caster);
        SpiritTechniques.catchIn(caster, victim, SpiritTechniques.Illusion.MIRAGE, 100, 0, 0, 1);
        helper.assertTrue(victim.getTarget() == other, "it attacks its own kind: " + victim.getTarget());
        clearAround(helper);
    }

    @GameTest(template = "empty")
    public static void strongerMindsResistGenjutsu(GameTestHelper helper) {
        SparringNinja weak = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja strong = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        strong.setStat(com.noirm.training.TrainingSystem.Stat.SPEED, 60);
        strong.setStat(com.noirm.training.TrainingSystem.Stat.TAIJUTSU, 60);
        helper.assertTrue(SpiritTechniques.genjutsuChance(weak, strong, 1f) < SpiritTechniques.genjutsuChance(strong, weak, 1f), "the stronger mind wins");
        helper.assertTrue(SpiritTechniques.genjutsuChance(weak, strong, 1f) >= SpiritTechniques.GENJUTSU_MIN_CHANCE, "never hopeless");
        weak.discard();
        strong.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_spirit_tree", timeoutTicks = 80)
    public static void treeBindingDeathStrikesAtTheEnd(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 0.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja victim = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        victim.setInvulnerable(false);
        SpiritTechniques.catchIn(caster, victim, SpiritTechniques.Illusion.TREE, 20, 6f, 10f, 0);
        helper.runAfterDelay(10, () -> helper.assertTrue(CombatSystem.isStaggered(victim) && victim.getHealth() == victim.getMaxHealth(), "held, not hurt yet"));
        helper.runAfterDelay(25, () -> {
            helper.assertFalse(SpiritTechniques.inIllusion(victim), "released after the strike");
            helper.assertTrue(victim.getHealth() < victim.getMaxHealth(), "the phantom struck");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_spirit_neck", timeoutTicks = 80)
    public static void shadowNeckBindChokes(GameTestHelper helper) {
        pad(helper, 0, 6);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 4.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "yin/shadow_neck_bind", 1f);
        for (int t = 1; t <= 40; t++) {
            channel(caster, "yin/shadow_neck_bind", t);
        }
        helper.assertTrue(SpiritTechniques.isChoked(enemy), "the shadow holds them");
        helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "and chokes");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_stitch", timeoutTicks = 60)
    public static void shadowStitchingPiercesEnemiesNotMates(GameTestHelper helper) {
        pad(helper, 0, 8);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja a = ninja(helper, 0.5f, 5.5f, TestClans.PLAIN);
        SparringNinja b = ninja(helper, 2.5f, 6.5f, TestClans.PLAIN);
        SparringNinja mate = ninja(helper, 1.5f, 4.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        for (SparringNinja n : List.of(a, b, mate)) {
            n.setInvulnerable(false);
        }
        facePoint(caster, caster.position().add(0, 0, 5));
        run(caster, "yin/shadow_stitching", 1f);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(a.getHealth() < a.getMaxHealth() && b.getHealth() < b.getMaxHealth(), "both enemies pierced");
            helper.assertTrue(mate.getHealth() == mate.getMaxHealth(), "the crew mate is spared");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Yang

    @GameTest(template = "empty", batch = "noirm_spirit_yang_self")
    public static void yangHealsModesAndConverts(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.EVERY_NATURE);
        caster.setHealth(caster.getMaxHealth() * 0.5f);
        float before = caster.getHealth();
        run(caster, "yang/rapid_regeneration", 1f);
        helper.assertTrue(caster.getHealth() > before && caster.hasEffect(MobEffects.REGENERATION), "Rapid Regeneration heals and keeps healing");
        run(caster, "yang/yang_vitality", 1f);
        helper.assertTrue(ModeSystem.isActive(caster, Noirm.id("yang/yang_vitality")) && ModeSystem.damageMultiplier(caster) > 1f, "Vitality mode is on");
        ModeSystem.end(caster);
        caster.setHealth(caster.getMaxHealth());
        caster.setChakra(10);
        run(caster, "yang/life_force_conversion", 1f);
        helper.assertTrue(caster.chakra() > 10 && caster.getHealth() < caster.getMaxHealth(), "life turned into chakra");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_fist", timeoutTicks = 60)
    public static void superExpansionFistSlams(GameTestHelper helper) {
        pad(helper, 0, 7);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        caster.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "yang/super_expansion_fist", 1f);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "the fist lands");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "not on the caster");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_spirit_reincarnation", timeoutTicks = 120)
    public static void reincarnationBringsAFallenMateBack(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 0.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja mate = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        UUID id = mate.getUUID();
        int remembered = SpiritTechniques.fallenCount();
        mate.setInvulnerable(false);
        mate.kill();
        helper.assertTrue(SpiritTechniques.fallenCount() > remembered, "the fallen mate is remembered");
        helper.runAfterDelay(30, () -> run(caster, "yang/life_reincarnation", 1f));
        helper.runAfterDelay(70, () -> {
            var back = helper.getLevel().getEntity(id);
            helper.assertTrue(back instanceof SparringNinja n && n.isAlive() && leader.equals(n.leaderId()), "they live again, still in the crew");
            helper.assertTrue(caster.getHealth() < caster.getMaxHealth() * 0.5f, "at a heavy price");
            if (back != null) {
                back.discard();
            }
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Yin-Yang

    @GameTest(template = "empty", batch = "noirm_spirit_renewal")
    public static void renewalHealsAndCleanses(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.EVERY_NATURE);
        caster.setHealth(caster.getMaxHealth() * 0.5f);
        caster.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        float before = caster.getHealth();
        run(caster, "yin_yang/renewal", 1f);
        helper.assertTrue(caster.getHealth() > before && !caster.hasEffect(MobEffects.POISON), "healed and cleansed");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_attraction")
    public static void attractionPullsTheTargetIn(GameTestHelper helper) {
        pad(helper, 0, 9);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "yin_yang/attraction", 1f);
        helper.assertTrue(enemy.getDeltaMovement().z < -0.2 && enemy.getDeltaMovement().z > -SpiritTechniques.MAX_THROW - 0.01, "pulled toward the caster: " + enemy.getDeltaMovement());
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_repulsion")
    public static void repulsionThrowsEnemiesOut(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        SparringNinja mate = ninja(helper, 0.5f, 1.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        enemy.setInvulnerable(false);
        mate.setInvulnerable(false);
        run(caster, "yin_yang/repulsion", 1f);
        helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() && enemy.getDeltaMovement().z > 0, "thrown away: " + enemy.getDeltaMovement());
        helper.assertTrue(mate.getHealth() == mate.getMaxHealth(), "the crew mate is spared");
        enemy.setDeltaMovement(Vec3.ZERO);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_hawks", timeoutTicks = 120)
    public static void chakraHawksHuntAndDissolve(GameTestHelper helper) {
        pad(helper, 0, 7);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        int before = SpiritTechniques.hawksAlive();
        run(caster, "yin_yang/chakra_hawks", 1f);
        helper.assertTrue(SpiritTechniques.hawksAlive() == before + 3, "three hawks");
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "the hawks strike");
            caster.discard();
        });
        helper.runAfterDelay(64, () -> {
            helper.assertTrue(SpiritTechniques.hawksAlive() == before, "they dissolve with their maker");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_spirit_equilibrium", timeoutTicks = 80)
    public static void equilibriumCollapsePullsThenBursts(GameTestHelper helper) {
        pad(helper, 0, 9);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        caster.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "yin_yang/equilibrium_collapse", 1f);
        helper.runAfterDelay(10, () -> helper.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "nothing yet: it's gathering"));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), "the burst hits");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "not the caster");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Medical

    @GameTest(template = "empty", batch = "noirm_spirit_mend", timeoutTicks = 60)
    public static void deepMendHealsAFriendAndDrawsPoison(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja patient = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        patient.joinCrew(leader);
        patient.setHealth(patient.getMaxHealth() * 0.4f);
        patient.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
        face(caster, patient);
        float before = patient.getHealth();
        run(caster, "medical/deep_mend", 1f);
        for (int t = 1; t <= 21; t++) {
            channel(caster, "medical/deep_mend", t);
        }
        helper.assertTrue(patient.getHealth() > before && !patient.hasEffect(MobEffects.POISON), "healed and the poison drawn out");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_scalpel")
    public static void scalpelStrikeLungesAndCuts(GameTestHelper helper) {
        pad(helper, 0, 6);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 4.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        double start = caster.getZ();
        run(caster, "medical/scalpel_strike", 1f);
        helper.assertTrue(caster.getZ() > start + 1, "lunged in");
        helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() && enemy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "cut and slowed");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_mist", timeoutTicks = 80)
    public static void poisonMistPoisonsEnemiesAndExtractionCures(GameTestHelper helper) {
        pad(helper, 0, 8);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "medical/poison_mist", 1f);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(enemy.hasEffect(MobEffects.POISON), "the mist poisons the enemy");
            helper.assertFalse(caster.hasEffect(MobEffects.POISON), "not the caster");
            caster.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            caster.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0));
            facePoint(caster, caster.position().add(5, 0, 0)); // looking at no one: treats themselves
            run(caster, "medical/poison_extraction", 1f);
            helper.assertFalse(caster.hasEffect(MobEffects.POISON) || caster.hasEffect(MobEffects.WITHER), "drawn out");
        });
        helper.runAfterDelay(40, () -> clearAround(helper));
    }

    @GameTest(template = "empty", batch = "noirm_spirit_field", timeoutTicks = 60)
    public static void healingFieldHealsOnlyYourSide(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.EVERY_NATURE);
        SparringNinja mate = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja enemy = ninja(helper, 2.5f, 2.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        mate.setHealth(mate.getMaxHealth() * 0.4f);
        enemy.setHealth(enemy.getMaxHealth() * 0.4f);
        float mateBefore = mate.getHealth();
        float enemyBefore = enemy.getHealth();
        run(caster, "medical/healing_field", 1f);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(mate.getHealth() > mateBefore, "the crew mate heals");
            helper.assertTrue(enemy.getHealth() <= enemyBefore, "the enemy doesn't");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_spirit_derange")
    public static void derangementScramblesAndWillShortensIt(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "medical/body_pathway_derangement", 1f);
        helper.assertTrue(MedicalTechniques.isDeranged(enemy) && enemy.getHealth() < enemy.getMaxHealth(), "the palm scrambles them");
        var player = helper.makeMockPlayer();
        int plain = MedicalTechniques.derangeTicks(player, 80);
        com.noirm.player.ShinobiCapability.get(player).ifPresent(d -> d.setWillpower(60));
        helper.assertTrue(MedicalTechniques.derangeTicks(player, 80) < plain, "a strong will shakes it off sooner");
        helper.assertTrue(MedicalTechniques.derangeTicks(player, 80) >= Math.round(80 * MedicalTechniques.DERANGE_MIN_SHARE), "but not at once");
        player.discard();
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_spirit_seal", timeoutTicks = 60)
    public static void hundredHealingsSealHeals(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f, TestClans.EVERY_NATURE);
        caster.setHealth(caster.getMaxHealth() * 0.6f);
        run(caster, "medical/hundred_healings", 1f);
        helper.assertTrue(MedicalTechniques.hasSeal(caster), "the seal opens");
        float before = caster.getHealth();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(caster.getHealth() > before, "and keeps healing");
            caster.setHealth(caster.getMaxHealth() * 0.2f);
        });
        helper.runAfterDelay(23, () -> {
            helper.assertTrue(caster.getHealth() > caster.getMaxHealth() * 0.4f, "it snaps back once when low");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    private static void run(LivingEntity caster, String path, float charge) {
        ResourceLocation id = Noirm.id(path);
        var def = JutsuData.get(id);
        com.noirm.jutsu.FriendlyZones.mark(caster);
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), charge));
    }

    private static void channel(LivingEntity caster, String path, int tick) {
        ResourceLocation id = Noirm.id(path);
        var def = JutsuData.get(id);
        JutsuBehaviors.get(def.behavior()).channel(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), 1f), tick);
    }

    private static void pad(GameTestHelper helper, int z0, int z1) {
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= z1 + 1; z++) {
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                if (x >= 0 && x <= 2 && z >= z0 && z <= z1) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(12);
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                e -> !(e instanceof LivingEntity)).forEach(net.minecraft.world.entity.Entity::discard);
    }

    private static void facePoint(LivingEntity entity, Vec3 point) {
        float yaw = (float) (Math.atan2(point.z - entity.getZ(), point.x - entity.getX()) * (180 / Math.PI)) - 90f;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setYHeadRot(yaw);
        entity.yHeadRotO = yaw;
        entity.setYBodyRot(yaw);
        entity.setXRot(0);
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.setHealth(ninja.getMaxHealth());
        return ninja;
    }

    private static JsonObject lang(String code) {
        try (var in = SpiritGameTests.class.getResourceAsStream("/assets/noirm/lang/" + code + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("can't read " + code, e);
        }
    }
}

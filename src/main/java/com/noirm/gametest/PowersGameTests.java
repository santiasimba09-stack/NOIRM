package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.awakening.AwakeningSystem;
import com.noirm.biju.BijuSystem;
import com.noirm.clan.AwakeningPath;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanGenerator;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.clan.WorldClans;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.dojutsu.Sharingan;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.jutsu.Substitution;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests for clans, jutsu, the Sharingan, the awakening and the tailed-beast cloak. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowersGameTests {
    private static final ResourceLocation FIREBALL = Noirm.id("katon/great_fireball");
    private static final ResourceLocation WATER_BULLET = Noirm.id("suiton/water_bullet");
    private static final ResourceLocation CHIDORI = Noirm.id("raiton/chidori");
    private static final ResourceLocation SUBSTITUTION = Noirm.id("universal/substitution");
    private static final ResourceLocation BODY_FLICKER = Noirm.id("universal/body_flicker");
    /** Sharingan and genjutsu tests trigger effects nearby; keep them apart from the rest. */
    private static final String EYES_BATCH = "noirm_eyes";

    private PowersGameTests() {}

    // ------------------------------------------------------------ clans

    @GameTest(template = "empty")
    public static void generatedClansAreUniqueAndValid(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            Clan clan = ClanGenerator.generate(random, names);
            helper.assertTrue(names.add(clan.name()), "duplicate clan name " + clan.name());
            helper.assertTrue(!clan.canon(), "generated clans are custom");
            long combined = clan.natures().keySet().stream().filter(n -> !n.isBasic()).count();
            helper.assertTrue(combined <= 1, clan.name() + " has at most one bloodline nature");
            helper.assertTrue(clan.natures().size() >= 1 && clan.natures().size() <= (combined == 1 ? 3 : 2),
                    clan.name() + " should have 1-2 natures (3 with a bloodline: its two parts plus it)");
            clan.natures().keySet().stream().filter(n -> !n.isBasic())
                    .forEach(n -> helper.assertTrue(clan.natures().keySet().containsAll(n.components()), clan.name() + " has " + n + " without its parts"));
            helper.assertTrue(clan.natures().containsValue(Clan.PRIMARY_AFFINITY), clan.name() + " needs a strong nature");
            helper.assertTrue(clan.traits().size() == 2 && clan.traits().get(0) != clan.traits().get(1), clan.name() + " needs 2 different traits");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canonClansLoad(GameTestHelper helper) {
        Clan uchiha = ClanData.canon("uchiha").orElse(null);
        helper.assertTrue(uchiha != null && uchiha.canon(), "the Uchiha should load from data/noirm/clans");
        helper.assertTrue(uchiha.hasSharingan() && uchiha.awakening() == AwakeningPath.SHARINGAN, "Uchiha carry the Sharingan");
        helper.assertTrue(ClanData.canonClans().size() >= 4, "expected Uchiha, Hyūga, Uzumaki, Senju");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldClansGrowAndAreSaved(GameTestHelper helper) {
        WorldClans clans = new WorldClans();
        RandomSource random = RandomSource.create(11);
        int canon = 0;
        int rolls = 400;
        for (int i = 0; i < rolls; i++) {
            if (clans.rollForNewcomer(random).canon()) {
                canon++;
            }
        }
        int custom = clans.customClans().size();
        helper.assertTrue(canon > rolls * 0.04 && canon < rolls * 0.18, "about 10% canon expected, got " + canon + "/" + rolls);
        helper.assertTrue(custom > 10 && custom < rolls - canon, "custom clans should both grow and be reused, got " + custom);
        WorldClans reloaded = WorldClans.load(clans.save(new CompoundTag()));
        helper.assertTrue(reloaded.customClans().equals(clans.customClans()), "custom clans should survive save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void joiningAClanGivesStatsNaturesAndJutsu(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, ClanData.canon("uchiha").orElseThrow());
        assertNear(helper, Clan.PRIMARY_AFFINITY, data.identity().affinity(Nature.FIRE), "Uchiha fire affinity");
        helper.assertTrue(data.identity().affinity(Nature.WATER) == 0, "no water for the Uchiha");
        for (ResourceLocation id : List.of(FIREBALL, CHIDORI, SUBSTITUTION, BODY_FLICKER)) {
            helper.assertTrue(data.jutsu().knows(id), "Uchiha should start with " + id);
        }
        helper.assertTrue(FIREBALL.equals(data.jutsu().slot(0)), "the strongest nature's jutsu goes in slot 1");
        assertNear(helper, 14f, data.getTaijutsu(), "Uchiha taijutsu: 10 + 1 bonus + 3 (Fierce)");

        ClanSystem.assign(player, data, ClanData.canon("uzumaki").orElseThrow());
        assertNear(helper, 130f, data.getMaxChakra(), "Uzumaki Deep Reserves: +30 chakra");
        helper.assertFalse(data.jutsu().knows(FIREBALL), "changing clan resets jutsu");
        helper.succeed();
    }

    // ------------------------------------------------------------ jutsu

    @GameTest(template = "empty")
    public static void jutsuDataIsValid(GameTestHelper helper) {
        helper.assertTrue(JutsuData.all().size() >= 9, "expected the 9 starter jutsu, got " + JutsuData.all().size());
        JutsuData.all().forEach((id, def) -> helper.assertTrue(JutsuBehaviors.exists(def.behavior()), id + " uses unknown behavior " + def.behavior()));
        for (Nature nature : Nature.values()) {
            helper.assertFalse(JutsuData.startersFor(nature).isEmpty(), "no starter jutsu for " + nature);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void affinityScalesPowerAndCost(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        JutsuDefinition fireball = JutsuData.get(FIREBALL);
        ClanSystem.assign(player, data, TestClans.FIRE);
        assertNear(helper, 1.25f, JutsuCaster.power(player, FIREBALL, fireball), "strong nature power");
        assertNear(helper, fireball.chakraCost() * 0.75f, JutsuCaster.cost(player, FIREBALL, fireball), "strong nature cost");

        ClanSystem.assign(player, data, TestClans.PLAIN);
        helper.assertTrue(JutsuCaster.checkRequirements(player, FIREBALL, fireball) == JutsuCaster.Result.NO_NATURE, "no fire nature, no fireball");
        data.identity().addNature(Nature.FIRE, Clan.TRAINED_AFFINITY);
        assertNear(helper, 0.75f, JutsuCaster.power(player, FIREBALL, fireball), "trained nature power");
        assertNear(helper, fireball.chakraCost() * 1.25f, JutsuCaster.cost(player, FIREBALL, fireball), "trained nature cost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tapJutsuSpendsChakraAndGoesOnCooldown(GameTestHelper helper) {
        Player player = mockPlayerAt(helper, 1.5, 0.5);
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.PLAIN);
        int slot = slotOf(data, WATER_BULLET);
        helper.assertTrue(slot >= 0, "water clan should have Water Bullet equipped");
        float before = data.getChakra();
        helper.assertTrue(JutsuCaster.onKey(player, slot, true) == JutsuCaster.Result.OK, "cast should succeed");
        assertNear(helper, before - JutsuCaster.cost(player, WATER_BULLET, JutsuData.get(WATER_BULLET)), data.getChakra(), "chakra spent");
        helper.assertTrue(data.jutsu().cooldown(WATER_BULLET) > 0, "should be on cooldown");
        helper.assertTrue(JutsuCaster.onKey(player, slot, true) == JutsuCaster.Result.COOLDOWN, "second cast blocked by cooldown");
        helper.assertTrue(data.jutsu().xp(WATER_BULLET) == 1, "casting earns mastery");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargeJutsuNeedsMinimumCharge(GameTestHelper helper) {
        Player player = mockPlayerAt(helper, 1.5, 0.5);
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.FIRE);
        int slot = slotOf(data, CHIDORI);
        float before = data.getChakra();
        JutsuCaster.onKey(player, slot, true);
        JutsuCaster.onKey(player, slot, false); // released at once: below Chidori's minimum
        assertNear(helper, before, data.getChakra(), "an early release refunds the chakra");
        helper.assertTrue(data.jutsu().cooldown(CHIDORI) == 0, "a fizzled charge has no cooldown");

        JutsuCaster.onKey(player, slot, true);
        for (int i = 0; i < 15; i++) {
            JutsuCaster.tick(player);
        }
        JutsuCaster.onKey(player, slot, false);
        helper.assertTrue(data.jutsu().cooldown(CHIDORI) > 0, "a charged Chidori is released");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireballHitsItsTarget(GameTestHelper helper) {
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        SparringNinja target = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        face(caster, target);
        helper.assertTrue(JutsuCaster.npcCast(caster, FIREBALL, 1f), "the fire ninja should cast");
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the fireball should hit");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, target.getBoundingBox().inflate(8)).isEmpty(), "the fireball should be gone");
        });
    }

    @GameTest(template = "empty")
    public static void substitutionAvoidsTheNextHit(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        face(attacker, target);
        Substitution.arm(target, 40);
        CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(CombatStates.get(attacker).phase() == CombatPhase.STAGGER, "the attacker is thrown off");
            assertNear(helper, target.getMaxHealth(), target.getHealth(), "the log takes the hit");
            helper.assertFalse(Substitution.isArmed(target), "substitution is used up");
        });
    }

    // ------------------------------------------------------------ Sharingan

    @GameTest(template = "empty")
    public static void sharinganSharpensPerception(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.SHARINGAN);
        float base = Perception.perception(player);
        Sharingan.advance(player, data, "x");
        Sharingan.advance(player, data, "x");
        assertNear(helper, base, Perception.perception(player), "an inactive Sharingan does nothing");
        data.eyes().setActive(true);
        assertNear(helper, base * 1.5f, Perception.perception(player), "2 active tomoe: +50% perception");
        helper.assertTrue(data.jutsu().knows(Sharingan.GENJUTSU), "2 tomoe teaches the Sharingan genjutsu");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = EYES_BATCH)
    public static void threeTomoeCopiesJutsu(GameTestHelper helper) {
        SparringNinja caster = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        Player watcher = mockPlayerAt(helper, 1.5, 0.5);
        lookAt(watcher, caster);
        ShinobiData data = data(watcher);
        ClanSystem.assign(watcher, data, TestClans.SHARINGAN);
        for (int i = 0; i < 3; i++) {
            Sharingan.advance(watcher, data, "x");
        }
        data.eyes().setActive(true);
        helper.assertFalse(data.jutsu().knows(WATER_BULLET), "the watcher starts without Water Bullet");
        // (Test players aren't placed in the world, so call the copy check the cast would make.)
        helper.assertTrue(JutsuCaster.npcCast(caster, WATER_BULLET, 1f), "the water ninja casts");
        helper.assertTrue(Sharingan.tryCopy(watcher, caster, WATER_BULLET), "watching with 3 tomoe should copy");
        helper.assertTrue(data.jutsu().knows(WATER_BULLET) && data.jutsu().isCopied(WATER_BULLET), "the copy is remembered as copied");
        data.eyes().setActive(false);
        helper.assertFalse(Sharingan.tryCopy(watcher, caster, FIREBALL), "no copying with the eyes off");
        helper.assertTrue(JutsuCaster.checkRequirements(watcher, WATER_BULLET, JutsuData.get(WATER_BULLET)) == JutsuCaster.Result.OK,
                "a copied jutsu can be used without its nature (imitation)");
        assertNear(helper, Clan.IMITATION_AFFINITY, JutsuCaster.affinity(watcher, WATER_BULLET, JutsuData.get(WATER_BULLET)), "imitation affinity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = EYES_BATCH + "_2")
    public static void genjutsuStunsAWeakerMind(GameTestHelper helper) {
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.SHARINGAN);
        SparringNinja target = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        caster.setCombatStats(60, 60);
        caster.setSharinganStage(3);
        lookAt(caster, target);
        helper.assertTrue(JutsuCaster.npcCast(caster, Sharingan.GENJUTSU, 1f), "an Uchiha with 2+ tomoe can cast it");
        helper.assertTrue(CombatStates.get(target).phase() == CombatPhase.STAGGER, "the target is caught (stunned)");
        helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and dazed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = EYES_BATCH + "_3")
    public static void precognitionMarksTheAttacker(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja seer = ninja(helper, 1.5f, 2.0f, TestClans.SHARINGAN);
        face(attacker, seer);
        helper.assertTrue(Sharingan.activeStage(seer) >= 1, "an Uchiha ninja's Sharingan is active");
        seer.setSharinganStage(Sharingan.PRECOGNITION_STAGE);
        CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        helper.assertTrue(attacker.hasEffect(MobEffects.GLOWING), "the attacker should glow for the Sharingan user");
        helper.succeed();
    }

    // ------------------------------------------------------------ awakening

    @GameTest(template = "empty")
    public static void awakeningSavesAtTheBrink(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.SHARINGAN);
        data.awakening().setResolve(100);
        float applied = AwakeningSystem.onIncomingDamage(player, player.getHealth() + 5f, 0f);
        helper.assertTrue(player.getHealth() - applied >= 1f, "a lethal hit should leave 1 health");
        helper.assertTrue(data.awakening().isLockedIn(), "the player locks in");
        assertNear(helper, 0f, data.awakening().resolve(), "Resolve is spent");
        helper.assertTrue(data.eyes().sharinganStage() == 1 && data.eyes().isActive(), "an Uchiha awakens the Sharingan");
        helper.assertTrue(AwakeningSystem.chance(player, data) == 0f, "no second awakening right away");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noAwakeningWithoutResolve(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.PLAIN);
        data.awakening().setResolve(AwakeningSystem.MIN_RESOLVE - 1);
        float damage = player.getHealth() + 5f;
        assertNear(helper, damage, AwakeningSystem.onIncomingDamage(player, damage, 0f), "not enough Resolve: the hit lands");
        helper.assertFalse(data.awakening().isLockedIn(), "no awakening");
        helper.succeed();
    }

    // ------------------------------------------------------------ biju

    @GameTest(template = "empty")
    public static void theBeastTakesOverJinchuriki(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, TestClans.PLAIN);
        BijuSystem.seal(player, data, 9);
        data.awakening().setResolve(100);
        AwakeningSystem.awaken(player, data);
        helper.assertTrue(data.biju().cloakStage() == 2, "a jinchūriki's awakening is the beast taking over");
        helper.assertFalse(BijuSystem.canGuard(player), "no guarding while the beast is in control");
        helper.assertTrue(BijuSystem.damageMultiplier(player) > 1.5f, "the beast hits harder");
        BijuSystem.endCloak(player, data);
        helper.assertTrue(data.biju().cloakStage() == 0 && data.biju().cooldownTicks() > 0, "the cloak ends and needs to recover");
        helper.succeed();
    }

    // ------------------------------------------------------------ helpers

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow();
    }

    private static int slotOf(ShinobiData data, ResourceLocation id) {
        for (int i = 0; i < 4; i++) {
            if (id.equals(data.jutsu().slot(i))) {
                return i;
            }
        }
        return -1;
    }

    private static Player mockPlayerAt(GameTestHelper helper, double x, double z) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        net.minecraft.world.phys.Vec3 pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(x, 1, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return player;
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        floor(helper);
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(clan);
        // Bare hands: a rolled weapon (bow, kusarigama…) would swap the moveset the tests expect.
        ninja.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        ninja.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
        return ninja;
    }

    /** Face and look straight at the other's eyes. */
    private static void lookAt(LivingEntity entity, LivingEntity other) {
        face(entity, other);
        double dx = other.getX() - entity.getX();
        double dz = other.getZ() - entity.getZ();
        double dy = other.getEyeY() - entity.getEyeY();
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        entity.setXRot(pitch);
        entity.xRotO = pitch;
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }
}

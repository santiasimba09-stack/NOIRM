package com.noirm.gametest;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanGenerator;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.clan.WorldClans;
import com.noirm.creator.CharacterCreator;
import com.noirm.creator.GeneticRoll;
import com.noirm.entity.NinjaLook;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.NpcJutsuSets;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.traits.ClanJutsu;
import com.noirm.traits.NpcTalents;
import com.noirm.traits.Perk;
import com.noirm.traits.PerkEffects;
import com.noirm.traits.PerkEvents;
import com.noirm.traits.Perks;
import com.noirm.traits.Talent;
import com.noirm.traits.TalentData;
import com.noirm.traits.Talents;
import com.noirm.training.TrainingSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Clans that matter (perks, clan jutsu, looks) and personal talents (docs/CLANS.md, docs/TRAITS.md). */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClanPerkGameTests {
    private static final String BATCH = "noirm_clan_perks";
    private static final ResourceLocation CHAINS = Noirm.id("clan/adamantine_sealing_chains");

    private ClanPerkGameTests() {}

    private static ShinobiData data(Player player) {
        return ShinobiCapability.get(player).orElseThrow();
    }

    private static Clan canon(String id) {
        return ClanData.canon(id).orElseThrow(() -> new IllegalStateException("no canon clan " + id));
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }

    /** A plain member of {@code clan}: assigned, no talents. */
    private static Player member(GameTestHelper helper, Clan clan) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, clan);
        data.talents().set(List.of());
        TrainingSystem.applyReserves(data);
        PerkEvents.tick(player, data);
        return player;
    }

    // ---------------------------------------------------------------- the data

    @GameTest(template = "empty", batch = BATCH)
    public static void canonClansAreCompleteAndValid(GameTestHelper helper) {
        List<String> expected = List.of("uzumaki", "uchiha", "hyuga", "senju", "nara", "akimichi", "yamanaka", "aburame", "inuzuka",
                "hatake", "sarutobi", "kaguya", "hozuki", "yuki", "chinoike", "kurama", "fuma", "iburi", "jugo", "kamizuru", "yotsuki",
                "kazekage", "kurogane", "shimura", "hoshigaki", "might");
        for (String id : expected) {
            Clan clan = canon(id);
            helper.assertTrue(clan.perks().size() >= 4, id + " has at least 4 perks, has " + clan.perks().size());
            clan.perks().keySet().forEach(k -> helper.assertTrue(Perk.byKey(k) != null, id + " names a real perk: " + k));
            clan.talentBias().keySet().forEach(k -> helper.assertTrue(Talents.get(k) != null, id + " biases a real talent: " + k));
            clan.clanJutsu().forEach(j -> helper.assertTrue(JutsuData.get(j) != null, id + " clan jutsu exists: " + j));
            helper.assertTrue(clan.look().isPresent() && clan.look().get().defaultHair() >= 0, id + " has a default hair colour");
            helper.assertTrue(NinjaLook.folderFor(id).equals(id), id + " has its own NPC look table");
        }
        Set<ResourceLocation> clanJutsu = new HashSet<>();
        ClanData.canonClans().forEach(c -> c.clanJutsu().stream().filter(ClanJutsu::isClanJutsu).forEach(clanJutsu::add));
        helper.assertTrue(clanJutsu.size() >= 8 && clanJutsu.size() <= 12, "8-12 clan jutsu, got " + clanJutsu.size());
        helper.assertTrue(Talents.all().size() >= 20 && Talents.all().size() <= 30, "20-30 talents, got " + Talents.all().size());
        Talents.all().forEach((id, t) -> t.modifiers().keySet().forEach(k -> helper.assertTrue(Perk.byKey(k) != null, id + ": " + k)));
        long negative = Talents.all().values().stream().filter(t -> t.kind() == Talent.Kind.NEGATIVE).count();
        long mixed = Talents.all().values().stream().filter(t -> t.kind() == Talent.Kind.MIXED).count();
        helper.assertTrue(negative >= 2 && mixed >= 4, "some mixed and negative talents");
        // Red hair for the Uzumaki, pale eyes for the Hyūga.
        int hair = canon("uzumaki").look().get().defaultHair();
        helper.assertTrue(((hair >> 16) & 0xFF) > 2 * ((hair >> 8) & 0xFF), "Uzumaki hair is red");
        helper.assertTrue(canon("hyuga").look().get().defaultEyes() > 0xD00000, "Hyūga eyes are pale");
        helper.succeed();
    }

    // ---------------------------------------------------------------- canon clans on players

    @GameTest(template = "empty", batch = BATCH)
    public static void uzumakiReservesRegenLongevityAndSealing(GameTestHelper helper) {
        Player plain = member(helper, TestClans.PLAIN);
        Player uzumaki = member(helper, canon("uzumaki"));
        ShinobiData p = data(plain);
        ShinobiData u = data(uzumaki);
        helper.assertTrue(u.getMaxChakra() >= 9 * p.getMaxChakra(), "an Uzumaki's chakra pool is huge: " + u.getMaxChakra() + " vs " + p.getMaxChakra());
        helper.assertTrue(u.chakraRegenMultiplier() >= 2.5f * p.chakraRegenMultiplier(), "Uzumaki chakra regenerates far faster");
        // health comes back on its own
        uzumaki.setHealth(10f);
        for (int t = 0; t < 100; t++) {
            uzumaki.tickCount++;
            PerkEvents.tick(uzumaki, u);
        }
        helper.assertTrue(uzumaki.getHealth() > 10.5f, "Uzumaki heal by themselves: " + uzumaki.getHealth());
        // longevity: slower strain, gentler pills, resistant to damage over time
        helper.assertTrue(Perks.mult(u, Perk.STRAIN) < 0.7f, "the Mangekyō wears an Uzumaki slower");
        helper.assertTrue(Perks.mult(u, Perk.PILL_TOXICITY) < 0.8f, "pills sit easier");
        helper.assertTrue(Perks.get(u, Perk.DOT_RESIST) >= 0.3f, "poison and burns hurt less");
        // sealing: cheaper and stronger, and a faster bond with a beast
        JutsuDefinition chains = JutsuData.get(CHAINS);
        helper.assertTrue(u.jutsu().knows(CHAINS), "Uzumaki are born knowing the Adamantine Sealing Chains");
        ResourceLocation flicker = Noirm.id("universal/body_flicker");
        helper.assertTrue(Perks.jutsuCostMultiplier(uzumaki, CHAINS) < Perks.jutsuCostMultiplier(uzumaki, flicker) - 0.4f,
                "sealing is far cheaper for them than their other jutsu");
        helper.assertTrue(Perks.jutsuPowerMultiplier(uzumaki, CHAINS, chains) >= 1.5f, "and strong");
        helper.assertTrue(JutsuCaster.checkRequirements(plain, CHAINS, chains) == JutsuCaster.Result.NOT_CLAN, "outsiders can't use clan jutsu");
        helper.assertTrue(JutsuCaster.checkRequirements(uzumaki, CHAINS, chains) == JutsuCaster.Result.OK, "Uzumaki can");
        com.noirm.biju.BijuSystem.seal(uzumaki, u, 9);
        int before = u.biju().bond();
        com.noirm.biju.BeastBond.changeBond(uzumaki, u.biju(), 10);
        helper.assertTrue(u.biju().bond() - before >= 15, "the beast bonds faster with an Uzumaki: +" + (u.biju().bond() - before));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void chainsSealChakra(GameTestHelper helper) {
        Player uzumaki = member(helper, canon("uzumaki"));
        uzumaki.addEffect(new MobEffectInstance(PerkEffects.CHAKRA_SEAL.get(), 100));
        ResourceLocation flicker = Noirm.id("universal/body_flicker");
        helper.assertTrue(JutsuCaster.checkRequirements(uzumaki, flicker, JutsuData.get(flicker)) == JutsuCaster.Result.SEALED,
                "a chakra seal stops every jutsu");
        String effect = JutsuData.get(CHAINS).params().effect();
        helper.assertTrue(effect.contains("noirm:chakra_seal"), "the chains put the chakra seal on whoever they bind");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void hyugaSenjuAkimichiKaguyaPerks(GameTestHelper helper) {
        ShinobiData plain = data(member(helper, TestClans.PLAIN));
        Player hyugaPlayer = member(helper, canon("hyuga"));
        ShinobiData hyuga = data(hyugaPlayer);
        helper.assertTrue(Perks.mult(hyuga, Perk.GENTLE_FIST) >= 1.25f, "Hyūga Gentle Fist is stronger");
        helper.assertTrue(hyuga.getControl() > plain.getControl(), "Hyūga start with finer chakra control");
        helper.assertTrue(hyuga.eyes().hasByakugan(), "and the Byakugan");

        Player senjuPlayer = member(helper, canon("senju"));
        ShinobiData senju = data(senjuPlayer);
        helper.assertTrue(senjuPlayer.getMaxHealth() >= 34f, "Senju vitality: " + senjuPlayer.getMaxHealth());
        helper.assertTrue(senju.getMaxStamina() >= plain.getMaxStamina() + 100, "huge Senju stamina: " + senju.getMaxStamina());
        helper.assertTrue(senju.getMaxChakra() >= 3.5f * plain.getMaxChakra(), "and a huge chakra pool: " + senju.getMaxChakra());
        helper.assertTrue(senju.getControl() < plain.getControl(), "but rougher chakra control");
        NatureSystem.grant(senju, Nature.WOOD, Clan.SECONDARY_AFFINITY);
        near(helper, Clan.PRIMARY_AFFINITY, senju.identity().affinity(Nature.WOOD), "Senju Wood potential");

        ShinobiData akimichi = data(member(helper, canon("akimichi")));
        helper.assertTrue(Perks.mult(akimichi, Perk.FOOD_POWER) >= 1.5f, "food fuels the Akimichi");
        helper.assertTrue(akimichi.jutsu().knows(Noirm.id("clan/multi_size")), "they know Multi-Size");
        helper.assertTrue(akimichi.getDurability() > plain.getDurability(), "Akimichi are tough");

        ShinobiData kaguya = data(member(helper, canon("kaguya")));
        near(helper, ShinobiData.DEFAULT_STAT + 10, kaguya.getDurability(), "Kaguya durability");
        helper.assertTrue(Perks.get(kaguya, Perk.DAMAGE_TAKEN) < 0, "bones take the blows");

        ShinobiData nara = data(member(helper, canon("nara")));
        helper.assertTrue(Perks.statXpMultiplier(nara, "control", 10) >= 1.4f, "Nara intellect: control trains fast");
        helper.assertTrue(Perks.mult(nara, Perk.MASTERY) >= 1.3f, "and jutsu are learned fast");
        ShinobiData uchiha = data(member(helper, canon("uchiha")));
        helper.assertTrue(Perks.mult(uchiha, Perk.STRAIN) > 1f, "the Uchiha curse: the Mangekyō strains faster");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the big gaps (owner follow-up)

    /** An Uzumaki's pool is ten times a normal shinobi's at the same Reserves, twenty with a beast; raw control is the price. */
    @GameTest(template = "empty", batch = BATCH)
    public static void uzumakiPoolDwarfsANormalOne(GameTestHelper helper) {
        Player plain = member(helper, TestClans.PLAIN);
        Player uzumaki = member(helper, canon("uzumaki"));
        ShinobiData p = data(plain);
        ShinobiData u = data(uzumaki);
        for (float reserves : new float[]{10f, 30f, 60f}) {
            p.setReserves(reserves);
            u.setReserves(reserves);
            TrainingSystem.applyReserves(p);
            TrainingSystem.applyReserves(u);
            float ratio = u.getMaxChakra() / p.getMaxChakra();
            helper.assertTrue(ratio >= 9.5f && ratio <= 11f, "at Reserves " + reserves + " an Uzumaki has ~10x the chakra: " + ratio);
        }
        com.noirm.biju.BijuSystem.seal(uzumaki, u, 9);
        TrainingSystem.applyReserves(u);
        float jin = u.getMaxChakra() / p.getMaxChakra();
        helper.assertTrue(jin >= 19f, "an Uzumaki jinchuriki has ~20x: " + jin);
        // the drawbacks: raw control (cost up, genjutsu weak) that fades with CTL training; a loud chakra
        ResourceLocation genjutsu = Noirm.id("yin/demonic_illusion");
        ResourceLocation flicker = Noirm.id("universal/body_flicker");
        u.setControl(6f);
        helper.assertTrue(Perks.jutsuCostMultiplier(uzumaki, flicker) >= 1.35f, "rough control: jutsu cost more at low CTL");
        helper.assertTrue(Perks.jutsuPowerMultiplier(uzumaki, genjutsu, JutsuData.get(genjutsu)) <= 0.25f, "and genjutsu barely work");
        u.setControl(50f);
        near(helper, 1f, Perks.jutsuCostMultiplier(uzumaki, flicker), "CTL 50 smooths it out");
        near(helper, 1.75f, Perks.beaconMultiplier(uzumaki), "a loud chakra draws attention");
        com.noirm.traits.ClanHiraishin.register();
        helper.assertTrue(com.noirm.hiraishin.HiraishinAffinity.of(uzumaki).extraMarks() == com.noirm.traits.ClanHiraishin.UZUMAKI_EXTRA_MARKS,
                "the Uzumaki's Flying Raijin gift: more marks");
        helper.assertTrue(com.noirm.hiraishin.HiraishinAffinity.of(plain).extraMarks() == 0, "others have none");
        helper.succeed();
    }

    /** The Might line: taijutsu trains four times as fast, iron will and stamina, safer Gates; almost no ninjutsu. */
    @GameTest(template = "empty", batch = BATCH)
    public static void mightLineTrainsTaijutsuFarFaster(GameTestHelper helper) {
        Player plain = member(helper, TestClans.PLAIN);
        Player might = member(helper, canon("might"));
        ShinobiData p = data(plain);
        ShinobiData m = data(might);
        near(helper, 4f, Perks.statXpMultiplier(m, "taijutsu", 20), "taijutsu XP x4");
        float beforeP = p.training().xp(TrainingSystem.Stat.TAIJUTSU);
        float beforeM = m.training().xp(TrainingSystem.Stat.TAIJUTSU);
        TrainingSystem.grant(plain, TrainingSystem.Stat.TAIJUTSU, 10f);
        TrainingSystem.grant(might, TrainingSystem.Stat.TAIJUTSU, 10f);
        float gotP = p.training().xp(TrainingSystem.Stat.TAIJUTSU) - beforeP;
        float gotM = m.training().xp(TrainingSystem.Stat.TAIJUTSU) - beforeM;
        helper.assertTrue(gotM >= 3.9f * gotP, "the same drill teaches a Might four times as much: " + gotM + " vs " + gotP);
        helper.assertTrue(m.getWillpower() >= p.getWillpower() + 8, "iron will: " + m.getWillpower());
        helper.assertTrue(m.getMaxStamina() >= p.getMaxStamina() + 150, "endless stamina: " + m.getMaxStamina());
        helper.assertTrue(Perks.statXpMultiplier(m, "willpower", 20) >= 2f, "willpower trains twice as fast");
        ResourceLocation gate = Noirm.id("taijutsu/gate_of_opening");
        helper.assertTrue(m.jutsu().knows(gate), "born knowing the Gate of Opening");
        near(helper, 0.5f, com.noirm.jutsu.ModeSystem.gateWear(might, JutsuData.get(gate).mode().orElseThrow()), "the Gates burn half as much");
        near(helper, 1f, com.noirm.jutsu.ModeSystem.gateWear(plain, JutsuData.get(gate).mode().orElseThrow()), "for anyone else, full");
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        helper.assertTrue(Perks.jutsuPowerMultiplier(might, fireball, JutsuData.get(fireball)) <= 0.45f, "ninjutsu barely work for them");
        helper.assertTrue(Perks.jutsuCostMultiplier(might, fireball) >= 1.6f, "and cost far more");
        ResourceLocation genjutsu = Noirm.id("yin/demonic_illusion");
        helper.assertTrue(Perks.jutsuPowerMultiplier(might, genjutsu, JutsuData.get(genjutsu)) <= 0.15f, "genjutsu hardly at all");
        helper.succeed();
    }

    /** Every clan's drawbacks really apply. */
    @GameTest(template = "empty", batch = BATCH)
    public static void clanDrawbacksApply(GameTestHelper helper) {
        Player plainPlayer = member(helper, TestClans.PLAIN);
        ShinobiData plain = data(plainPlayer);
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        Player hyuga = member(helper, canon("hyuga"));
        near(helper, 0.7f, Perks.jutsuPowerMultiplier(hyuga, fireball, JutsuData.get(fireball)), "Hyūga ninjutsu is weaker");
        Player kurama = member(helper, canon("kurama"));
        near(helper, 14f, kurama.getMaxHealth(), "the Kurama are frail");
        ShinobiData akimichi = data(member(helper, canon("akimichi")));
        helper.assertTrue(akimichi.getSpeed() < plain.getSpeed(), "the Akimichi are slow");
        ShinobiData uchiha = data(member(helper, canon("uchiha")));
        near(helper, 1.6f, Perks.mult(uchiha, Perk.STRAIN), "the Uchiha curse: strain x1.6");
        ShinobiData kaguya = data(member(helper, canon("kaguya")));
        helper.assertTrue(Perks.get(kaguya, Perk.DOT_RESIST) <= -0.5f, "the Kaguya illness");
        ShinobiData hatake = data(member(helper, canon("hatake")));
        helper.assertTrue(hatake.getMaxChakra() < plain.getMaxChakra(), "a Hatake's small reserves: " + hatake.getMaxChakra());
        ShinobiData nara = data(member(helper, canon("nara")));
        helper.assertTrue(Perks.statXpMultiplier(nara, "taijutsu", 20) <= 0.6f, "the Nara don't like hard work");
        ShinobiData aburame = data(member(helper, canon("aburame")));
        near(helper, 0.6f, Perks.get(aburame, Perk.FIRE_WEAKNESS), "fire burns the Aburame's insects");
        helper.succeed();
    }

    // ---------------------------------------------------------------- NPCs of a clan

    @GameTest(template = "empty", batch = "noirm_clan_perks_npc")
    public static void npcsLookThePartAndUseTheirPerks(GameTestHelper helper) {
        SparringNinja kaguya = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 1f, 0.5f);
        SparringNinja plain = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 2.5f, 1f, 0.5f);
        kaguya.joinClan(canon("kaguya"));
        plain.joinClan(TestClans.PLAIN);
        helper.assertTrue(NinjaLook.folder(kaguya.outfit()).equals("kaguya"), "a Kaguya ninja wears the Kaguya look: " + kaguya.outfit());
        String hair = NinjaLook.decode(kaguya.outfit()).get("hair_color");
        helper.assertTrue(hair.equals("#f2f0ea") || hair.equals("#d8d4cc"), "Kaguya hair is white: " + hair);
        DamageSource generic = kaguya.damageSources().generic();
        LivingHurtEvent onKaguya = new LivingHurtEvent(kaguya, generic, 10f);
        LivingHurtEvent onPlain = new LivingHurtEvent(plain, generic, 10f);
        MinecraftForge.EVENT_BUS.post(onKaguya);
        MinecraftForge.EVENT_BUS.post(onPlain);
        helper.assertTrue(onKaguya.getAmount() < onPlain.getAmount(), "a Kaguya ninja takes less damage: " + onKaguya.getAmount());
        near(helper, -0.15f * Perks.NPC_COMBAT_SHARE, Perks.get(kaguya, Perk.DAMAGE_TAKEN), "NPC combat perks count at half");
        near(helper, 8f * Perks.NPC_COMBAT_SHARE, Perks.get(kaguya, Perk.MAX_HEALTH), "health too");
        // talents on NPCs (tests set them; elsewhere they roll by themselves)
        NpcTalents.set(plain, List.of("tough_as_nails"));
        helper.assertTrue(Perks.get(plain, Perk.MAX_HEALTH) == 4f, "an NPC's talents count");
        // clan jutsu in NPC sets
        Map<Nature, Float> earth = Map.of(Nature.EARTH, 1.0f);
        boolean bones = false;
        for (long seed = 1; seed < 40 && !bones; seed++) {
            bones = NpcJutsuSets.roll(NpcJutsuSets.Tier.JONIN, earth, "kaguya", false, seed).contains(Noirm.id("clan/finger_bone_bullets"));
        }
        helper.assertTrue(bones, "Kaguya ninjas learn their clan jutsu");
        for (long seed = 1; seed < 40; seed++) {
            helper.assertFalse(NpcJutsuSets.roll(NpcJutsuSets.Tier.KAGE, earth, "test_plain", false, seed).stream()
                    .anyMatch(ClanJutsu::isClanJutsu), "no clan jutsu outside the clan");
        }
        kaguya.discard();
        plain.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_clan_perks_water")
    public static void hozukiSlipThroughBlowsNearWaterAburameEatPoison(GameTestHelper helper) {
        SparringNinja hozuki = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 2f, 1.5f);
        hozuki.joinClan(canon("hozuki"));
        helper.getLevel().setWeatherParameters(6000, 0, false, false); // no rain: rain counts as water
        FakePlayer attacker = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_clan_test"));
        DamageSource blow = hozuki.damageSources().playerAttack(attacker);
        int dry = 0;
        for (int i = 0; i < 60; i++) {
            LivingAttackEvent e = new LivingAttackEvent(hozuki, blow, 2f);
            MinecraftForge.EVENT_BUS.post(e);
            dry += e.isCanceled() ? 1 : 0;
        }
        helper.assertTrue(dry == 0, "on dry land a Hōzuki is hit like anyone: " + dry + " dodged");
        for (int x = 0; x < 3; x++) { // a pool: nine water sources around them
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                helper.setBlock(x, 1, z, Blocks.WATER);
            }
        }
        int wet = 0;
        for (int i = 0; i < 60; i++) {
            LivingAttackEvent e = new LivingAttackEvent(hozuki, blow, 2f);
            MinecraftForge.EVENT_BUS.post(e);
            wet += e.isCanceled() ? 1 : 0;
        }
        helper.assertTrue(wet >= 30 && wet <= 59, "in water, blows wash through a Hōzuki about 75% of the time: " + wet + "/60");
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 1, z, Blocks.AIR);
            }
        }

        SparringNinja aburame = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 0.5f, 2f, 0.5f);
        aburame.joinClan(canon("aburame"));
        aburame.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        helper.assertFalse(aburame.hasEffect(MobEffects.POISON), "the Aburame's insects eat poison");
        hozuki.discard();
        aburame.discard();
        helper.succeed();
    }

    // ---------------------------------------------------------------- generated clans

    @GameTest(template = "empty", batch = BATCH)
    public static void generatedClansGetValidPackages(GameTestHelper helper) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            Clan clan = ClanGenerator.generate(RandomSource.create(1000 + i), List.of());
            helper.assertTrue(clan.packages().size() == Talents.PACKAGES_PER_CLAN, clan.name() + " has " + clan.packages().size() + " packages");
            helper.assertTrue(clan.packages().stream().distinct().count() == clan.packages().size(), "no package twice");
            helper.assertTrue(!clan.perks().isEmpty(), clan.name() + " has perks");
            clan.perks().keySet().forEach(k -> helper.assertTrue(Perk.byKey(k) != null, "real perk " + k));
            clan.talentBias().keySet().forEach(k -> helper.assertTrue(Talents.get(k) != null, "real talent " + k));
            seen.addAll(clan.packages());
        }
        helper.assertTrue(seen.size() >= 10, "many different packages appear: " + seen.size());
        // A generated clan saved before perks existed gets the same package every time.
        Clan old = new Clan("custom_oldclan", "Oldclan", false, Map.of(Nature.FIRE, Clan.PRIMARY_AFFINITY), List.of(), 0, 0,
                java.util.Optional.empty(), com.noirm.clan.AwakeningPath.WILL, 1);
        Clan a = WorldClans.upgrade(old);
        Clan b = WorldClans.upgrade(old);
        helper.assertTrue(!a.perks().isEmpty() && a.packages().equals(b.packages()), "old generated clans get a fixed package");
        helper.assertTrue(WorldClans.upgrade(a) == a, "and keep it");
        helper.succeed();
    }

    // ---------------------------------------------------------------- talents

    @GameTest(template = "empty", batch = BATCH)
    public static void talentRollsAreBiasedByClanButVary(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        Map<String, Float> uzumaki = canon("uzumaki").talentBias();
        int sealersUzumaki = 0;
        int sealersPlain = 0;
        Set<List<String>> distinct = new HashSet<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        for (int i = 0; i < 500; i++) {
            List<String> u = Talents.roll(random, uzumaki);
            List<String> p = Talents.roll(random, Map.of());
            sealersUzumaki += u.contains("natural_sealer") ? 1 : 0;
            sealersPlain += p.contains("natural_sealer") ? 1 : 0;
            distinct.add(u);
            sizes.merge(u.size(), 1, Integer::sum);
            helper.assertTrue(u.size() >= 2 && u.size() <= 4, "2-4 talents, got " + u);
            helper.assertTrue(u.stream().distinct().count() == u.size(), "no talent twice: " + u);
            helper.assertTrue(u.stream().filter(t -> Talents.get(t).kind() == Talent.Kind.NEGATIVE).count() <= Talents.MAX_NEGATIVE, "at most one weakness");
            helper.assertFalse(u.contains("quick_learner") && u.contains("late_bloomer"), "exclusions hold: " + u);
            helper.assertFalse(u.contains("fine_control") && u.contains("shaky_control"), "exclusions hold: " + u);
        }
        helper.assertTrue(sealersUzumaki > 2 * sealersPlain, "Uzumaki roll Natural Sealer far more: " + sealersUzumaki + " vs " + sealersPlain);
        helper.assertTrue(sealersUzumaki < 400, "but not every Uzumaki has it: " + sealersUzumaki);
        helper.assertTrue(distinct.size() > 150, "members of one clan differ: " + distinct.size() + " different sets");
        helper.assertTrue(sizes.getOrDefault(2, 0) > 0 && sizes.getOrDefault(3, 0) > 0 && sizes.getOrDefault(4, 0) > 0, "2, 3 and 4 all happen: " + sizes);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void talentsTakeEffect(GameTestHelper helper) {
        Player player = member(helper, TestClans.PLAIN);
        ShinobiData data = data(player);
        float chakra = data.getMaxChakra();
        data.talents().set(List.of("quick_learner", "prodigy", "deep_reserves", "iron_will"));
        TrainingSystem.applyReserves(data);
        near(helper, chakra + 40f, data.getMaxChakra(), "Deep Reserves: +40 chakra");
        near(helper, 1.25f, Perks.statXpMultiplier(data, "speed", 20), "Quick Learner: +25% training");
        near(helper, 2f, Perks.mult(data, Perk.MASTERY), "Prodigy: mastery twice as fast");
        near(helper, 0.7f, Perks.genjutsuTakeMultiplier(player), "Iron Will: 30% less likely to be caught");
        // XP for real: a training point arrives sooner with Quick Learner
        float before = data.training().xp(TrainingSystem.Stat.SPEED);
        TrainingSystem.grant(player, TrainingSystem.Stat.SPEED, 4f);
        float gained = data.training().xp(TrainingSystem.Stat.SPEED) - before;
        helper.assertTrue(gained > 4.5f || data.getSpeed() > ShinobiData.DEFAULT_STAT, "the XP is multiplied: +" + gained);
        data.talents().set(List.of("late_bloomer"));
        near(helper, 0.75f, Perks.statXpMultiplier(data, "speed", 20), "Late Bloomer: slow below 30");
        near(helper, 1.6f, Perks.statXpMultiplier(data, "speed", 40), "Late Bloomer: fast from 30");
        data.talents().set(List.of("tough_as_nails"));
        PerkEvents.tick(player, data);
        near(helper, 24f, player.getMaxHealth(), "Tough as Nails: +4 health");
        data.talents().set(List.of());
        PerkEvents.tick(player, data);
        near(helper, 20f, player.getMaxHealth(), "and it goes away");
        TrainingSystem.applyReserves(data);
        near(helper, chakra, data.getMaxChakra(), "chakra back to normal");
        helper.succeed();
    }

    /** A roll's talents and clan perks reach the character's stats. */
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorRollAppliesTalentsAndClanStats(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_clan_roll"));
        ShinobiData data = data(player);
        GeneticRoll roll = new GeneticRoll("kaguya", 10, 10, 10, 10, 10, List.of("fleet_footed", "stubborn_body"));
        CharacterCreator.apply(player, data, roll);
        helper.assertTrue(data.identity().clanId().equals("kaguya"), "joined the Kaguya");
        near(helper, 13f, data.getSpeed(), "Fleet-footed: +3 speed");
        near(helper, ShinobiData.DEFAULT_STAT + 10 + 4, data.getDurability(), "Kaguya +10 and Stubborn Body +4 durability");
        helper.assertTrue(data.talents().talents().equals(List.of("fleet_footed", "stubborn_body")), "the roll's talents are yours");
        helper.assertTrue(data.talents().version() == TalentData.CURRENT, "a new character is current");
        helper.succeed();
    }

    /** A character made before talents existed rolls them once, and gets their clan's perks. */
    @GameTest(template = "empty", batch = BATCH)
    public static void oldSavesMigrateOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, canon("uzumaki"));
        CompoundTag saved = data.save();
        saved.remove("Talents");
        saved.getCompound("Identity").remove("Perks");
        saved.getCompound("Identity").remove("ClanJutsu");
        data.load(saved);
        data.jutsu().clear();
        helper.assertTrue(data.talents().version() == 0 && data.identity().perks().isEmpty(), "an old save: no talents, no perks");
        float control = data.getControl();
        helper.assertTrue(PerkEvents.migrate(player, data), "it migrates");
        helper.assertTrue(data.identity().perks().equals(canon("uzumaki").perks()), "the clan's perks arrive");
        helper.assertTrue(data.jutsu().knows(CHAINS), "and its clan jutsu");
        int n = data.talents().talents().size();
        helper.assertTrue(n >= 2 && n <= 4, "2-4 talents rolled: " + data.talents().talents());
        helper.assertTrue(data.talents().version() == TalentData.CURRENT, "now current");
        helper.assertTrue(data.getControl() != control || Perks.resolve(data.identity().perks(), data.talents().talents())
                .getOrDefault(Perk.START_CONTROL, 0f) == 0f, "starting stats were added");
        List<String> talents = data.talents().talents();
        helper.assertFalse(PerkEvents.migrate(player, data), "only once");
        helper.assertTrue(data.talents().talents().equals(talents), "the talents stay");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_clan_perks_cmd")
    public static void clanAndTraitCommandsRun(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_trait_cmd"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            dispatcher.execute("noirm clan set uzumaki", source);
            helper.assertTrue(dispatcher.execute("noirm clan perks", source) >= 4, "/noirm clan perks lists the Uzumaki blood");
            helper.assertTrue(dispatcher.execute("noirm clan perks kaguya", source) >= 4, "and another clan's");
            dispatcher.execute("noirm trait give natural_sealer", source);
            helper.assertTrue(data(player).talents().has("natural_sealer"), "/noirm trait give");
            dispatcher.execute("noirm trait list", source);
            dispatcher.execute("noirm trait list all", source);
            dispatcher.execute("noirm trait take natural_sealer", source);
            helper.assertFalse(data(player).talents().has("natural_sealer"), "/noirm trait take");
            dispatcher.execute("noirm trait reroll", source);
            helper.assertTrue(data(player).talents().talents().size() >= 2, "/noirm trait reroll");
            dispatcher.execute("noirm me", source);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        helper.succeed();
    }
}

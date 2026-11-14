package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.NpcJutsuSets;
import com.noirm.jutsu.NpcJutsuSets.Tier;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.noirm.gametest.TestKit.*;

/** NPC ninjas know a personal, limited jutsu set (jutsu/NpcJutsuSets) instead of every jutsu of their natures. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcJutsuGameTests {
    private static final String BATCH = "noirm_npc_jutsu";

    private NpcJutsuGameTests() {}

    /** Weak ninjas know 1–2 jutsu up to C rank; the strongest 6–8 with S allowed; always one of their best nature. */
    @GameTest(template = "empty", batch = BATCH)
    public static void setsAreSizedByStrength(GameTestHelper helper) {
        helper.assertTrue(NpcJutsuSets.tierFor(10) == Tier.GENIN && NpcJutsuSets.tierFor(25) == Tier.CHUNIN
                && NpcJutsuSets.tierFor(60) == Tier.JONIN && NpcJutsuSets.tierFor(150) == Tier.KAGE, "strength picks the tier");
        Clan every = TestClans.EVERY_NATURE;
        for (Tier tier : Tier.values()) {
            for (long seed = 1; seed <= 40; seed++) {
                List<ResourceLocation> set = NpcJutsuSets.roll(tier, every.natures(), every.id(), false, seed);
                helper.assertTrue(set.size() >= tier.min && set.size() <= tier.max, tier + " knows " + tier.min + "-" + tier.max + ", got " + set);
                for (ResourceLocation id : set) {
                    JutsuDefinition def = JutsuData.get(id);
                    helper.assertTrue(def != null && NpcJutsuSets.rankIndex(def.rank()) <= NpcJutsuSets.rankIndex(tier.maxRank),
                            tier + " doesn't know " + id + " (rank " + (def == null ? "?" : def.rank()) + ")");
                    helper.assertTrue(def.requires().isEmpty(), "no dōjutsu techniques without the eye: " + id);
                }
                helper.assertTrue(set.stream().anyMatch(id -> JutsuData.get(id).nature().orElse(null) == Nature.FIRE),
                        "one of their best nature, got " + set);
            }
        }
        // A real ninja: weak at first, then (before they settle) made strong by their spawner.
        SparringNinja ninja = still(helper, TestClans.FIRE, 1.5f);
        ninja.setCombatStats(10, 10);
        helper.assertTrue(ninja.personalJutsu().size() <= 2 && ninja.knownJutsu().size() <= 3, "a weak ninja knows little: " + ninja.knownJutsu());
        helper.assertTrue(ninja.knownJutsu().contains(Noirm.id("katon/great_fireball")), "a fire ninja's first jutsu is the fire starter");
        helper.assertFalse(ninja.knownJutsu().contains(Noirm.id("katon/great_fire_annihilation")), "and never an S-rank");
        ninja.setCombatStats(150, 150);
        helper.assertTrue(ninja.personalJutsu().size() >= 6, "a Kage-level ninja knows 6-8: " + ninja.personalJutsu());
        // Roles: trainees stay genin, guards are at least chūnin.
        ninja.setCombatStats(10, 10);
        ninja.setVillageRole("GUARD");
        helper.assertTrue(NpcJutsuSets.tierOf(ninja) == Tier.CHUNIN, "a guard is at least chūnin");
        ninja.setVillageRole("TRAINEE");
        ninja.setCombatStats(60, 60);
        helper.assertTrue(NpcJutsuSets.tierOf(ninja) == Tier.GENIN, "a trainee stays genin");
        // Hyūga: the Byakugan's techniques are signatures (a chūnin may know them), never without the eye.
        boolean rotation = false;
        for (long seed = 1; seed <= 40 && !rotation; seed++) {
            rotation = NpcJutsuSets.roll(Tier.CHUNIN, TestClans.BYAKUGAN.natures(), "hyuga", true, seed).contains(Noirm.id("byakugan/rotation"));
        }
        helper.assertTrue(rotation, "some chūnin Hyūga know Rotation");
        clear(helper);
    }

    /** The same seed gives the same set; it's saved, and kept even if their strength changes later. */
    @GameTest(template = "empty", batch = BATCH)
    public static void setsAreSeededAndSaved(GameTestHelper helper) {
        Clan uchiha = com.noirm.clan.WorldClans.get(helper.getLevel().getServer()).find("uchiha").orElseThrow();
        SparringNinja a = still(helper, uchiha, 0.5f);
        SparringNinja b = still(helper, uchiha, 2.5f);
        a.setCombatStats(60, 60);
        b.setCombatStats(60, 60);
        a.setJutsuSeed(1234);
        b.setJutsuSeed(1234);
        helper.assertTrue(a.personalJutsu().equals(b.personalJutsu()), "same seed, same set: " + a.personalJutsu() + " / " + b.personalJutsu());
        helper.assertTrue(a.personalJutsu().size() >= 4 && a.personalJutsu().size() <= 6, "a jōnin knows 4-6");

        CompoundTag tag = new CompoundTag();
        a.saveWithoutId(tag);
        SparringNinja loaded = NoirmEntities.SPARRING_NINJA.get().create(helper.getLevel());
        loaded.load(tag);
        helper.assertTrue(loaded.personalJutsu().equals(a.personalJutsu()), "the set is saved: " + loaded.personalJutsu());
        helper.assertTrue(loaded.jutsuSettled(), "and settled");
        loaded.setCombatStats(10, 10);
        helper.assertTrue(loaded.personalJutsu().equals(a.personalJutsu()), "a settled set doesn't change with strength");

        // Saved before sets existed: rolled now (limited), and a crew mate keeps what they practised.
        Player player = helper.makeMockPlayer();
        a.joinCrew(player.getUUID());
        a.setCombatStats(10, 10);
        CompoundTag old = new CompoundTag();
        a.saveWithoutId(old);
        old.remove("JutsuSet");
        old.remove("JutsuSetClan");
        CompoundTag xp = new CompoundTag();
        xp.putInt("noirm:katon/fire_dragon", 50);
        old.put("JutsuXp", xp);
        SparringNinja legacy = NoirmEntities.SPARRING_NINJA.get().create(helper.getLevel());
        legacy.load(old);
        helper.assertTrue(legacy.personalJutsu().size() <= 2, "an old genin gets a small set: " + legacy.personalJutsu());
        helper.assertTrue(legacy.knownJutsu().contains(Noirm.id("katon/fire_dragon")), "but keeps the jutsu they had practised");
        a.leaveCrew();
        clear(helper);
    }

    /** Crew mates grow by being taught, and work out the next jutsu themselves at mastery milestones. */
    @GameTest(template = "empty", batch = BATCH)
    public static void crewMatesGrowByTeachingAndPractice(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        SparringNinja mate = still(helper, TestClans.PLAIN, 1.5f);
        mate.setCombatStats(10, 10);
        mate.joinCrew(player.getUUID());
        ResourceLocation bullet = Noirm.id("suiton/water_bullet");
        ResourceLocation dragon = Noirm.id("suiton/water_dragon");
        helper.assertTrue(mate.knownJutsu().contains(bullet), "a water genin knows Water Bullet");
        helper.assertFalse(mate.knownJutsu().contains(dragon), "but not Water Dragon yet");
        int before = mate.knownJutsu().size();
        // Practice: Water Bullet at ★2 unlocks Water Dragon (and the other jutsu waiting on it stay for later).
        mate.addJutsuXp(bullet, com.noirm.jutsu.JutsuBook.xpForLevel(2));
        helper.assertTrue(mate.knownJutsu().contains(dragon), "practice taught them Water Dragon");
        helper.assertTrue(mate.knownJutsu().size() == before + 1, "just one new jutsu: " + mate.knownJutsu());
        // Teaching adds on top and survives re-joining the clan.
        mate.teach(Noirm.id("suiton/great_waterfall"));
        mate.joinClan(TestClans.PLAIN);
        helper.assertTrue(mate.knownJutsu().contains(Noirm.id("suiton/great_waterfall")) && mate.knownJutsu().contains(dragon),
                "taught and self-learned jutsu are kept");
        // Learning a nature gives its starters, not all of it.
        mate.learnNature(Nature.WIND, Clan.TRAINED_AFFINITY);
        JutsuData.startersFor(Nature.WIND).forEach(mate::teach);
        mate.joinClan(TestClans.PLAIN);
        helper.assertTrue(mate.knownJutsu().contains(Noirm.id("futon/air_palm")), "the wind starter");
        helper.assertFalse(mate.knownJutsu().contains(Noirm.id("futon/great_breakthrough"))
                || mate.knownJutsu().contains(Noirm.id("futon/vacuum_bullets")), "and nothing else of wind");
        mate.leaveCrew();
        clear(helper);
    }

    /**
     * In a fight, a ninja only casts jutsu it knows: its own set plus what it was taught (here a
     * close-range Chidori Current, so the test doesn't depend on the range dance).
     */
    @GameTest(template = "empty", batch = "noirm_npc_jutsu_fight", timeoutTicks = 300)
    public static void ninjasCastFromTheirOwnSet(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        setUp(caster, TestClans.FIRE);
        caster.setCombatStats(10, 10);
        caster.teach(Noirm.id("raiton/chidori_current"));
        SparringNinja target = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 2.5f);
        setUp(target, TestClans.PLAIN);
        target.setInvulnerable(true);
        caster.setTarget(target);
        List<ResourceLocation> known = List.copyOf(caster.knownJutsu());
        helper.assertTrue(known.size() <= 4, "a genin set (+1 taught): " + known);
        helper.onEachTick(() -> {
            caster.setTarget(target);
            if (caster.jutsuCasts() > 0) {
                for (ResourceLocation id : JutsuData.all().keySet()) {
                    if (!known.contains(id)) {
                        helper.assertTrue(JutsuCaster.npcReady(caster, id), "cast " + id + ", which isn't in its set " + known);
                    }
                }
                helper.assertTrue(known.stream().anyMatch(id -> !JutsuCaster.npcReady(caster, id)), "it cast one of its own");
                com.noirm.jutsu.ModeSystem.end(caster);
                clearAround(helper);
            }
        });
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja still(GameTestHelper helper, Clan clan, float x) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, 1.5f);
        setUp(ninja, clan);
        return ninja;
    }

    private static void setUp(SparringNinja ninja, Clan clan) {
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.sealBeast(0);
        ninja.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        ninja.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
    }

    /** Removes this test's ninjas (they stand still, so only the 3×3 area). */
    private static void clear(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(SparringNinja.class, new net.minecraft.world.phys.AABB(helper.absolutePos(
                BlockPos.ZERO)).expandTowards(3, 3, 3)).forEach(SparringNinja::discard);
        helper.succeed();
    }
}

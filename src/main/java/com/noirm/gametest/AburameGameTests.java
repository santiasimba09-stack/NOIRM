package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.aburame.AburameBehaviors;
import com.noirm.aburame.AburameFightGoal;
import com.noirm.aburame.AburameGlovesItem;
import com.noirm.aburame.AburameRegistry;
import com.noirm.aburame.BugBite;
import com.noirm.aburame.Colony;
import com.noirm.aburame.ColonyData;
import com.noirm.aburame.ColonyEvents;
import com.noirm.aburame.ColonyGrowth;
import com.noirm.aburame.Drain;
import com.noirm.aburame.Harm;
import com.noirm.aburame.HiveRank;
import com.noirm.aburame.InsectClone;
import com.noirm.aburame.InsectHiveBlockEntity;
import com.noirm.aburame.InsectJarItem;
import com.noirm.aburame.InsectSwarm;
import com.noirm.aburame.NestRoll;
import com.noirm.aburame.NpcColony;
import com.noirm.aburame.RotTouch;
import com.noirm.aburame.Species;
import com.noirm.aburame.SwarmMode;
import com.noirm.aburame.Swarms;
import com.noirm.clan.ClanData;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.WorldClans;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.mission.PlayerRank;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** The Aburame clan (docs/ABURAME.md, Part 3): colony, swarms, drain, weaknesses, techniques, hives, species, endgame, NPCs. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AburameGameTests {
    private static final ResourceLocation SWARM = Noirm.id("aburame/parasitic_swarm");

    private AburameGameTests() {}

    // ================================================================ helpers

    /** A fake Aburame player with a fresh colony of 3,000 kikaichū, full chakra, in the arena. */
    static FakePlayer aburame(GameTestHelper helper, String name, boolean rinkaichu) {
        floor(helper);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ShinobiData data = data(player);
        ClanSystem.assign(player, data, ClanData.canon("aburame").orElseThrow());
        data.colony().clear();
        Colony.grant(player, data, new NestRoll(3000, rinkaichu, 0));
        data.setChakra(data.getMaxChakra());
        data.career().setRank(PlayerRank.GENIN);
        return player;
    }

    static ShinobiData data(FakePlayer p) {
        return ShinobiCapability.get(p).orElseThrow(IllegalStateException::new);
    }

    static SparringNinja ninja(GameTestHelper helper, String clan, double x, double z) {
        SparringNinja n = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), (float) x, 2f, (float) z);
        WorldClans.get(helper.getLevel().getServer()).find(clan).ifPresent(n::joinClan);
        n.setChakra(n.maxChakra());
        return n;
    }

    static InsectSwarm swarm(GameTestHelper helper, LivingEntity owner, int count, SwarmMode mode) {
        InsectSwarm s = InsectSwarm.create(helper.getLevel(), owner, Species.KIKAICHU, count, owner.position().add(0, 1, 0), mode);
        helper.getLevel().addFreshEntity(s);
        ColonyData c = Colony.of(owner);
        if (c != null) {
            c.addField(count);
        }
        return s;
    }

    /** Clears swarms and strays, then succeeds. */
    static void done(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(40);
        helper.getLevel().getEntitiesOfClass(InsectSwarm.class, area).forEach(s -> s.discard());
        helper.getLevel().getEntitiesOfClass(InsectClone.class, area).forEach(s -> s.discard());
        clearAround(helper);
    }

    static void near(GameTestHelper helper, float expected, float actual, float tolerance, String what) {
        helper.assertTrue(Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    // ================================================================ Phase A: the colony

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void colonyGrowsWhenFed(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_grow", false);
        ShinobiData d = data(p);
        for (int i = 0; i < 60; i++) {
            d.setChakra(d.getMaxChakra());
            Colony.tickSecond(p, d);
        }
        int body = d.colony().body(Species.KIKAICHU);
        // +1.2%/min + 15 at Vigour 70 (×1.08): about +55 in a minute
        helper.assertTrue(body > 3030 && body < 3100, "a minute fed breeds about 55 insects, got " + (body - 3000));
        helper.assertFalse(d.colony().restless(), "fed, not restless");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void colonyStarvesAtZeroChakra(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_starve", false);
        ShinobiData d = data(p);
        d.setChakra(0f);
        for (int i = 0; i < 10; i++) {
            Colony.tickSecond(p, d);
        }
        helper.assertTrue(d.colony().restless(), "starving makes them restless");
        near(helper, 2970, d.colony().body(Species.KIKAICHU), 1, "1% every 10 s");
        helper.assertTrue(Colony.needed(p, JutsuData.get(SWARM), 1f) >= 2250, "restless techniques cost 50% more insects");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void upkeepScalesWithBodyCount(GameTestHelper helper) {
        near(helper, 0.24f, ColonyGrowth.upkeep(30000), 0.001f, "0.08/s per 10,000");
        FakePlayer p = aburame(helper, "noirm_ab_upkeep", false);
        ShinobiData d = data(p);
        d.colony().setBody(Species.KIKAICHU, Colony.capacity(p)); // full: no breeding cost
        float before = d.getMaxChakra();
        d.setChakra(before);
        Colony.tickSecond(p, d);
        near(helper, ColonyGrowth.upkeep(Colony.capacity(p)), before - d.getChakra(), 0.01f, "one second of upkeep");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void capacityFollowsReservesAndControl(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_cap", false);
        ShinobiData d = data(p);
        d.setReserves(40);
        d.setControl(30);
        helper.assertTrue(Colony.capacity(p) == 34000, "4,000 + 600×40 + 200×30 = 34,000, got " + Colony.capacity(p));
        helper.assertTrue(ColonyGrowth.bodyCapacity(10, 10) == 12000, "Reserves 10, Control 10: 12,000");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void gourdAndJarsAddCapacity(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_jars", false);
        for (int i = 0; i < 3; i++) {
            p.getInventory().add(new ItemStack(AburameRegistry.INSECT_JAR.get()));
        }
        p.getInventory().add(new ItemStack(AburameRegistry.INSECT_GOURD.get()));
        helper.assertTrue(Colony.storeCapacity(p) == 35000, "two jars and one gourd count: 35,000, got " + Colony.storeCapacity(p));
        ItemStack gourd = InsectJarItem.worn(p).stream().filter(s -> ((InsectJarItem) s.getItem()).isGourd()).findFirst().orElseThrow();
        InsectJarItem.set(gourd, 25000, Species.KIKAICHU);
        helper.assertTrue(Colony.total(p) == 28000, "jars count toward the whole colony, got " + Colony.total(p));
        helper.assertTrue(ColonyEvents.burstGourd(p) && InsectJarItem.count(gourd) == 0, "a big explosion bursts the gourd: its insects die");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void hiveRankNeedsDeeds(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_rank", false);
        ShinobiData d = data(p);
        d.colony().setBody(Species.KIKAICHU, 11000);
        Colony.checkRank(p, d.colony());
        helper.assertTrue(d.colony().rank() == HiveRank.NESTLING, "10,000 insects alone isn't enough");
        d.colony().addDrained(Colony.DEED_DRAIN);
        Colony.checkRank(p, d.colony());
        helper.assertTrue(d.colony().rank() == HiveRank.GENIN, "with 300 chakra drained: Genin hive");
        helper.assertTrue(d.jutsu().knows(Noirm.id("aburame/insect_clone")), "and the Genin techniques are learned");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void nestRollIsStableAndRare(GameTestHelper helper) {
        NestRoll a = NestRoll.of("aburame", new int[]{10, 9, 8, 13, 10}, List.of("chakra_sensor"));
        NestRoll b = NestRoll.of("aburame", new int[]{10, 9, 8, 13, 10}, List.of("chakra_sensor"));
        helper.assertTrue(a.equals(b), "the same roll gives the same nest (screen and server agree)");
        int rink = 0;
        for (int i = 0; i < 1200; i++) {
            NestRoll n = NestRoll.of(i * 7919L);
            helper.assertTrue(n.colony() >= NestRoll.MIN_COLONY && n.colony() <= NestRoll.MAX_COLONY, "2,400–3,600");
            rink += n.rinkaichu() ? 1 : 0;
        }
        helper.assertTrue(rink > 50 && rink < 150, "about 1 in 12 are rinkaichū: " + rink + "/1200");
        helper.succeed();
    }

    // ================================================================ Phase B: swarms and drain

    @GameTest(template = "empty", batch = "noirm_aburame_cast")
    public static void swarmCostsInsectsNotSeals(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_cast", false);
        ShinobiData d = data(p);
        helper.assertTrue(d.jutsu().knows(SWARM), "an Aburame is born knowing Parasitic Swarm");
        d.jutsu().equip(0, SWARM);
        int before = d.colony().bodyTotal();
        float chakra = d.getChakra();
        JutsuCaster.Result r = JutsuCaster.onKey(p, 0, true);
        helper.assertTrue(r == JutsuCaster.Result.OK, "cast: " + r);
        helper.assertTrue(JutsuCaster.casting(p) == null, "no seal phase: it fired on the same tick");
        helper.assertTrue(before - d.colony().bodyTotal() >= 1400, "it committed about 1,500 insects: " + (before - d.colony().bodyTotal()));
        helper.assertTrue(chakra - d.getChakra() < 6, "and only a little chakra");
        helper.assertFalse(Swarms.owned(p).isEmpty(), "a swarm is out in the world");
        // Stunned (or bound): insects act on their own.
        d.jutsu().setCooldown(SWARM, 0);
        CombatSystem.stun(p, 40);
        helper.assertFalse(CombatSystem.canAct(p), "stunned");
        helper.assertTrue(JutsuCaster.onKey(p, 0, true) == JutsuCaster.Result.OK, "an Aburame still sends insects while stunned");
        CombatSystem.clearStun(p);
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_drain")
    public static void swarmClingsAndDrains(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_drain", false);
        SparringNinja t = ninja(helper, "hyuga", 1.5, 3.5);
        InsectSwarm s = swarm(helper, p, 2000, SwarmMode.CLING);
        s.setTarget(t, SwarmMode.CLING);
        float before = t.chakra();
        data(p).setChakra(10f);
        s.drainSecond(helper.getLevel(), p, t);
        float took = before - t.chakra();
        near(helper, 1.0f * ColonyGrowth.vigourDrain(data(p).colony().vigour()), took, 0.05f, "0.5/s per 1,000 clinging");
        helper.assertTrue(data(p).getChakra() > 10f, "half of it comes to you");
        helper.assertTrue(data(p).colony().drainedTotal() > 0, "it counts toward the drain deed");
        t.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_drain2")
    public static void drainStopsAtAppetiteCap(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_appetite", false);
        SparringNinja t = ninja(helper, "hyuga", 1.5, 3.5);
        InsectSwarm s = swarm(helper, p, 700, SwarmMode.CLING); // under 800: not noticed, so never shaken off
        s.setTarget(t, SwarmMode.CLING);
        for (int i = 0; i < 80; i++) {
            t.setChakra(t.maxChakra());
            s.drainSecond(helper.getLevel(), p, t);
        }
        helper.assertTrue(s.eaten() <= 14.01f && s.eaten() > 13.5f, "700 insects eat at most 14 chakra (count ÷ 50): " + s.eaten());
        t.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_drain3")
    public static void drainedTargetCannotCast(GameTestHelper helper) {
        SparringNinja t = ninja(helper, "uchiha", 1.5, 1.5);
        Drain.take(t, 100000f);
        helper.assertTrue(Drain.isDrained(t), "at 0 chakra: Drained");
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        helper.assertTrue(JutsuCaster.checkRequirements(t, fireball, JutsuData.get(fireball)) == JutsuCaster.Result.DRAINED, "no jutsu while Drained");
        helper.assertFalse(CombatSystem.requestDodge(t, -1f, 0f), "no dodging while Drained");
        t.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_drain4")
    public static void beastCloakOverfeedsSwarm(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_overfeed", false);
        FakePlayer big = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_ab_bigpool"));
        ShinobiCapability.get(big).ifPresent(d -> {
            d.setMaxChakra(data(p).getMaxChakra() * 12f);
            d.setChakra(d.getMaxChakra());
        });
        InsectSwarm s = swarm(helper, p, 2000, SwarmMode.CLING);
        s.drainSecond(helper.getLevel(), p, big);
        helper.assertTrue(s.count() < 2000 && s.eaten() == 0f, "too much chakra: they burst instead of eating (" + s.count() + ")");
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_harm")
    public static void fireKillsSixTimesMoreThanBlades(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_fire", false);
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 3.5f);
        InsectSwarm a = swarm(helper, p, 8000, SwarmMode.CLOUD);
        InsectSwarm b = swarm(helper, p, 8000, SwarmMode.CLOUD);
        a.hurt(helper.getLevel().damageSources().mobAttack(enemy), 5f);
        b.hurt(helper.getLevel().damageSources().onFire(), 5f);
        int blade = 8000 - a.count(), fire = 8000 - b.count();
        helper.assertTrue(blade == 150, "a 5-point blow kills 150: " + blade);
        helper.assertTrue(fire == 6 * blade, "fire kills six times as many: " + fire);
        enemy.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_harm")
    public static void windScattersSphere(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_wind", false);
        InsectSwarm s = swarm(helper, p, 6000, SwarmMode.SPHERE);
        s.scatter();
        helper.assertTrue(s.mode() == SwarmMode.CLOUD, "wind breaks the sphere into a loose cloud");
        helper.assertTrue(Swarms.harmOf(com.noirm.clan.Nature.WIND, false) == Harm.WIND, "wind jutsu scatter");
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_water")
    public static void waterShakesOffClingers(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_water", false);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 1, z, Blocks.WATER);
                helper.setBlock(x, 2, z, Blocks.WATER);
                helper.setBlock(x, 3, z, Blocks.WATER);
            }
        }
        Zombie t = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 1f, 1.5f);
        helper.runAfterDelay(5, () -> {
            InsectSwarm s = swarm(helper, p, 2000, SwarmMode.CLING);
            s.setTarget(t, SwarmMode.CLING);
            s.tick();
            helper.assertTrue(s.mode() == SwarmMode.CLOUD, "diving shakes clinging insects off: " + s.mode());
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    for (int y = 1; y < 4; y++) {
                        helper.setBlock(x, y, z, Blocks.AIR);
                    }
                }
            }
            t.discard();
            done(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_aburame_recall")
    public static void recallReturnsSurvivors(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_recall", false);
        ShinobiData d = data(p);
        int before = d.colony().bodyTotal();
        InsectSwarm s = Swarms.send(p, Species.KIKAICHU, 1500, p.position().add(0, 1, 0), SwarmMode.CLOUD, 1f, 1f);
        helper.assertTrue(s != null && d.colony().bodyTotal() == before - 1500, "1,500 sent");
        s.harm(Harm.MELEE, 10f); // 300 die
        s.returnHome(p);
        helper.assertTrue(d.colony().bodyTotal() == before - 300, "1,200 come back: body " + d.colony().bodyTotal());
        helper.assertTrue(d.colony().field() == 0, "none left out in the field");
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_mark")
    public static void femaleMarkTracksWithoutLineOfSight(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_mark", false);
        Zombie t = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 3.5f);
        t.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 200));
        for (int x = 0; x < 3; x++) {
            helper.setBlock(x, 2, 2, Blocks.STONE);
            helper.setBlock(x, 3, 2, Blocks.STONE);
        }
        p.setYRot(0f); // facing +z, toward the zombie, through the wall
        helper.assertTrue(AburameBehaviors.target(p, 28) == null, "no line of sight: nothing to aim at");
        helper.assertTrue(Colony.mark(p, t, Colony.MARK_TICKS), "a female insect left on it");
        helper.assertTrue(AburameBehaviors.target(p, 28) == t, "a marked creature is found behind walls, even invisible");
        for (int x = 0; x < 3; x++) {
            helper.setBlock(x, 2, 2, Blocks.AIR);
            helper.setBlock(x, 3, 2, Blocks.AIR);
        }
        t.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_mark")
    public static void marksExpire(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_expire", false);
        Zombie t = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 1.5f);
        Colony.mark(p, t, -5);
        helper.assertFalse(Colony.marked(p, t), "an expired mark doesn't count");
        Colony.tickSecond(p, data(p));
        helper.assertTrue(data(p).colony().marks().isEmpty(), "and it's cleaned away");
        for (int i = 0; i < 5; i++) {
            Colony.mark(p, helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 0.5f, 2f, 0.5f), Colony.MARK_TICKS);
        }
        helper.assertTrue(data(p).colony().marks().size() == Colony.MAX_MARKS, "at most 3 marks");
        t.discard();
        done(helper);
    }

    // ================================================================ Phase C: the techniques

    private static JutsuProjectile projectile(GameTestHelper helper, LivingEntity shooter, com.noirm.clan.Nature nature, float damage, Vec3 at) {
        JutsuProjectile proj = NoirmEntities.JUTSU_PROJECTILE.get().create(helper.getLevel());
        proj.setOwner(shooter);
        proj.configure(nature, 0.5f, damage, 0f, 0f, 0f, nature == com.noirm.clan.Nature.FIRE ? 3 : 0);
        proj.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(proj);
        return proj;
    }

    @GameTest(template = "empty", batch = "noirm_aburame_wall")
    public static void wallEatsChakraProjectile(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_wall", false);
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 6.5f);
        InsectSwarm wall = swarm(helper, p, 5000, SwarmMode.WALL);
        wall.setShape(0f);
        JutsuProjectile proj = projectile(helper, enemy, com.noirm.clan.Nature.WATER, 6f, wall.center());
        wall.tick();
        helper.assertTrue(proj.isRemoved(), "the chakra jutsu was eaten");
        helper.assertTrue(wall.count() == 5000 + Math.round(6f * Swarms.COST_PER_DAMAGE * InsectSwarm.DEVOUR_GAIN), "+2 insects per chakra: " + wall.count());
        enemy.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_wall2")
    public static void wallDoesNotEatFire(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_wallfire", false);
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 6.5f);
        InsectSwarm wall = swarm(helper, p, 5000, SwarmMode.WALL);
        JutsuProjectile proj = projectile(helper, enemy, com.noirm.clan.Nature.FIRE, 6f, wall.center());
        wall.tick();
        helper.assertTrue(wall.count() == 5000 - 6 * 180, "fire kills insects instead (180 a point): " + wall.count());
        helper.assertTrue(proj.isRemoved(), "the wall still holds for that hit");
        enemy.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_jam")
    public static void jammingClearsNpcThreat(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_jam", false);
        Zombie hunter = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 1.5f);
        hunter.setTarget(p);
        InsectSwarm jam = swarm(helper, p, 4000, SwarmMode.JAM);
        jam.setShape(24f);
        jam.setPark(p.position().add(0, 1.5, 0));
        for (int i = 0; i < 21; i++) {
            jam.tick();
        }
        helper.assertTrue(hunter.getTarget() == null, "false chakra signals: the hunter lost you");
        hunter.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_clone")
    public static void cloneBurstsIntoClingSwarm(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_clone", false);
        InsectClone clone = AburameRegistry.INSECT_CLONE.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 2.5));
        clone.moveTo(at.x, at.y, at.z, 0f, 0f);
        clone.setOwner(p);
        clone.setInsects(2500);
        helper.getLevel().addFreshEntity(clone);
        Zombie striker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 0.5f);
        clone.hurt(helper.getLevel().damageSources().mobAttack(striker), 5f);
        helper.assertTrue(clone.isRemoved(), "struck for 4+, the clone bursts");
        InsectSwarm s = Swarms.owned(p).stream().findFirst().orElse(null);
        helper.assertTrue(s != null && s.count() == 2500 && s.target() == striker, "into a swarm that goes for the striker");
        striker.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void adaptationReducesFireLosses(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_adapt", false);
        ColonyData c = data(p).colony();
        float before = Colony.killRate(p, Harm.FIRE);
        c.addField(1000);
        Colony.killed(p, 1000, Harm.FIRE); // 1,000 burned = 10 pressure
        Colony.generation(p, c);
        near(helper, 0.02f, c.resist(Harm.FIRE), 0.001f, "one generation: +2% heat-hardened");
        helper.assertTrue(Colony.killRate(p, Harm.FIRE) < before, "fewer die to fire");
        c.setFocus(Harm.FIRE);
        Colony.generation(p, c);
        near(helper, 0.06f, c.resist(Harm.FIRE), 0.001f, "the Breeding Jar's focus adapts twice as fast");
        c.setResist(Harm.FIRE, 0.9f);
        near(helper, ColonyGrowth.ADAPT_CAP, c.resist(Harm.FIRE), 0.001f, "capped at 40%");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void saladAndInsectEatersMoveVigour(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_vigour", false);
        ColonyData c = data(p).colony();
        c.setVigour(50);
        new ItemStack(AburameRegistry.WILD_GRASS_SALAD.get()).finishUsingItem(helper.getLevel(), p);
        near(helper, 60f, c.vigour(), 0.01f, "wild-grass salad: +10 Vigour");
        helper.assertTrue(ColonyGrowth.vigourBreeding(0) == 0.25f && ColonyGrowth.vigourBreeding(100) == 1.5f, "Vigour scales breeding ×0.25 to ×1.5");
        helper.succeed();
    }

    // ================================================================ Phase D: species, hives, collecting

    @GameTest(template = "empty", batch = "noirm_aburame_bite")
    public static void bugBiteGrowsAndCanBeBurned(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_bite", false);
        Zombie t = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 2.5f);
        float hp = t.getHealth();
        BugBite.start(p, t);
        BugBite.tickSecond(t);
        BugBite.tickSecond(t);
        helper.assertTrue(hp - t.getHealth() >= 2.9f, "1 then 2 damage as they grow: " + (hp - t.getHealth()));
        t.setSecondsOnFire(3);
        BugBite.tickSecond(t);
        helper.assertFalse(BugBite.active(t), "fire on the victim burns them out");
        t.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_bite")
    public static void kidaichuBiteHostAtLowControl(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_dose", false);
        data(p).setControl(10);
        helper.assertTrue(BugBite.wrongDose(p), "Control under 30 is the wrong dose");
        data(p).setControl(40);
        data(p).setChakra(data(p).getMaxChakra());
        helper.assertFalse(BugBite.wrongDose(p), "Control 40 and full chakra: the right dose");
        data(p).setChakra(data(p).getMaxChakra() * 0.1f);
        helper.assertTrue(BugBite.wrongDose(p), "under 25% chakra: the wrong dose again");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_rot")
    public static void rotTouchSpreadsAndSevers(GameTestHelper helper) {
        clearStrays(helper);
        SparringNinja victim = ninja(helper, "hyuga", 1.5, 1.5);
        SparringNinja beside = ninja(helper, "hyuga", 1.5, 1.5);
        RotTouch.infect(victim, 3);
        helper.assertTrue(RotTouch.stacks(victim) == 3, "three stacks of Cell Rot");
        helper.runAtTickTime(helper.getTick() + 1, () -> {
            for (int i = 0; i < 2; i++) {
                RotTouch.tickSecond(victim);
            }
            helper.assertTrue(RotTouch.stacks(beside) > 0, "it spreads by touch");
            RotTouch.infect(victim, 10);
            RotTouch.tickSecond(victim);
            helper.assertTrue(RotTouch.stacks(victim) == 0, "at 8 stacks an NPC cuts the limb off, and the rot goes with it");
            victim.discard();
            beside.discard();
            done(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_aburame_rot2")
    public static void glovesContainRinkaichu(GameTestHelper helper) {
        clearStrays(helper);
        FakePlayer p = aburame(helper, "noirm_ab_gloves", true);
        helper.assertTrue(RotTouch.host(p) && data(p).colony().antivenom(), "a rinkaichū host: immune, with the antibodies");
        Zombie a = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 0.5f, 2f, 2.5f);
        Zombie b = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2.5f, 2f, 2.5f);
        ItemStack gloves = new ItemStack(AburameRegistry.ABURAME_GLOVES.get());
        gloves.getOrCreateTag().putBoolean(AburameGlovesItem.WORN, true);
        p.getInventory().setItem(9, gloves); // in the bag, not in hand (hands stay bare)
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(AburameRegistry.ABURAME_GLOVES.get())) {
                gloves = p.getInventory().getItem(i); // the inventory keeps its own copy
            }
        }
        RotTouch.onMelee(p, a);
        helper.assertTrue(RotTouch.stacks(a) == 0, "gloves on: nothing leaks");
        gloves.getTag().putBoolean(AburameGlovesItem.WORN, false);
        RotTouch.onMelee(p, b);
        helper.assertTrue(RotTouch.stacks(b) == 1, "bare hands rot what they touch");
        data(p).career().setRank(PlayerRank.ACADEMY);
        gloves.getTag().putBoolean(AburameGlovesItem.WORN, true);
        RotTouch.onMelee(p, a);
        helper.assertTrue(RotTouch.stacks(a) == 1, "a child can't hold them in, gloves or not");
        a.discard();
        b.discard();
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_hive")
    public static void hiveBreedsAndRefillsOnSleep(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_hive", false);
        helper.setBlock(1, 1, 1, AburameRegistry.INSECT_HIVE.get());
        InsectHiveBlockEntity hive = (InsectHiveBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
        hive.setOwner(p.getUUID());
        hive.setCount(30000);
        for (int i = 0; i < 60; i++) {
            hive.secondOfLife(helper.getLevel(), hive.getBlockPos());
        }
        helper.assertTrue(hive.count() > 30250 && hive.count() < 30350, "+1% a minute, for free: " + hive.count());
        int before = data(p).colony().bodyTotal();
        int moved = hive.refill(p);
        helper.assertTrue(moved > 0 && data(p).colony().bodyTotal() == before + moved, "the body refills from the hive");
        hive.setCount(20000);
        hive.exploded();
        helper.assertTrue(hive.count() == 10000, "an explosion next to it kills half");
        helper.setBlock(1, 1, 1, Blocks.AIR);
        done(helper);
    }

    @GameTest(template = "empty", batch = "noirm_aburame_hive")
    public static void antivenomIsEarnedByBreeding(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_antivenom", false);
        ColonyData c = data(p).colony();
        helper.assertFalse(Colony.poisonImmune(p), "Aburame start without poison immunity (owner's decision)");
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100));
        helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "poison takes hold");
        p.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
        c.setAntivenom(true);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100));
        helper.assertFalse(p.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "the Antivenom strain: immune");
        helper.succeed();
    }

    // ================================================================ Phase E: endgame and NPCs

    @GameTest(template = "empty", batch = "noirm_aburame_body")
    public static void swarmBodySavesFromLethalHit(GameTestHelper helper) {
        FakePlayer p = aburame(helper, "noirm_ab_swarmbody", false);
        ColonyData c = data(p).colony();
        c.setBody(Species.KIKAICHU, 10000);
        c.setLivingHive(true);
        LivingDeathEvent death = new LivingDeathEvent(p, helper.getLevel().damageSources().generic());
        MinecraftForge.EVENT_BUS.post(death);
        helper.assertTrue(death.isCanceled(), "a blow that would kill dissolves you into the swarm instead");
        helper.assertTrue(c.body(Species.KIKAICHU) == 6000, "it costs 40% of the body colony: " + c.body(Species.KIKAICHU));
        LivingDeathEvent again = new LivingDeathEvent(p, helper.getLevel().damageSources().generic());
        MinecraftForge.EVENT_BUS.post(again);
        helper.assertFalse(again.isCanceled(), "then a 5-minute cooldown");
        helper.assertTrue(Colony.capacity(p) == 2 * ColonyGrowth.bodyCapacity(data(p).getReserves(), data(p).getControl()), "the Living Hive doubles capacity");
        c.setLivingHive(false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_aburame_npc", timeoutTicks = 200)
    public static void npcAburameKeepsDistanceAndSpheres(GameTestHelper helper) {
        clearStrays(helper, 30);
        SparringNinja ab = ninja(helper, "aburame", 1.5, 1.5);
        ColonyData c = NpcColony.get(ab);
        helper.assertTrue(c != null && c.bodyTotal() >= 4000, "an Aburame ninja has a colony by class");
        c.setRank(HiveRank.CHUNIN);
        c.setBody(Species.KIKAICHU, 30000);
        Zombie foe = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1.5f, 2f, 4.5f);
        ab.setTarget(foe);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (ticks[0]++ % 2 == 0 && foe.isAlive()) {
                AburameFightGoal.tick(ab, foe, ab.distanceTo(foe));
            }
        });
        helper.runAfterDelay(140, () -> {
            boolean swarmed = Swarms.owned(ab).stream().anyMatch(s -> s.target() == foe || s.mode() == SwarmMode.SPHERE);
            helper.assertTrue(swarmed, "it sends insects at the foe");
            helper.assertTrue(ab.getNavigation().isInProgress() || ab.distanceTo(foe) > 3.5f, "and backs away instead of brawling");
            foe.discard();
            ab.discard();
            done(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_aburame_colony")
    public static void techniquesHaveNoSealsAndCantBeCopied(GameTestHelper helper) {
        for (ResourceLocation id : JutsuData.all().keySet()) {
            if (id.getPath().startsWith("aburame/")) {
                var def = JutsuData.get(id);
                helper.assertTrue(def.isAburame(), id + " is an insect technique");
                helper.assertTrue(JutsuCaster.sealPhaseTicks(id, def) == 0, id + " has no seal phase");
            }
        }
        helper.succeed();
    }
}

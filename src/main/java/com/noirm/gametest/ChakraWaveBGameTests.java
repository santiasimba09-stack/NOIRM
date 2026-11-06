package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.jutsu.KekkeiTechniquesB;
import com.noirm.jutsu.RockAndCubeDisplays;
import com.noirm.registry.NoirmBlocks;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Chakra wave, share B: the new Scorch, Dust, Magnet, Wind and Earth jutsu. Every one loads with
 * its seals and names and casts cleanly; each new behaviour hurts its enemy and never its caster.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChakraWaveBGameTests {
    static final List<String> NEW_JUTSU = List.of(
            "shakuton/heat_mote", "shakuton/scorching_halo", "shakuton/swarm_of_suns", "shakuton/steaming_murder",
            "jinton/detachment_needle", "jinton/detachment_shell", "jinton/cone_detachment", "jinton/expanding_cube",
            "jiton/lodestone_pull", "jiton/iron_sand_shield", "jiton/iron_sand_arm", "jiton/iron_sand_rain", "jiton/world_order",
            "futon/wind_blade", "futon/vacuum_blast", "futon/gale_wall", "futon/vacuum_wave", "futon/raging_tornado",
            "doton/headhunter", "doton/rock_hounds", "doton/earth_flow_rampart", "doton/added_weight_rock", "doton/falling_mountain");

    private ChakraWaveBGameTests() {}

    @GameTest(template = "empty")
    public static void waveBJutsuLoadWithSealsAndNames(GameTestHelper helper) {
        List<JsonObject> langs = List.of(lang("en_us"), lang("es_es"), lang("es_mx"));
        for (String path : NEW_JUTSU) {
            ResourceLocation id = Noirm.id(path);
            JutsuDefinition def = JutsuData.get(id);
            helper.assertTrue(def != null, "missing " + path);
            helper.assertTrue(JutsuBehaviors.exists(def.behavior()), "unknown behavior for " + path);
            helper.assertTrue(JutsuSeals.get(id) != null, "no hand seals for " + path);
            helper.assertTrue(def.nature().isPresent(), path + " has a nature");
            helper.assertTrue(def.starter() || def.unlock().isPresent(), path + " can be learned (starter or unlock chain)");
            for (JsonObject lang : langs) {
                helper.assertTrue(lang.has("jutsu.noirm." + path.replace('/', '.')), "no name for " + path);
                helper.assertTrue(lang.has("screen.noirm.jutsu.behavior." + def.behavior()), "no label for behavior " + def.behavior());
            }
        }
        helper.succeed();
    }

    /** Every new jutsu, cast at an enemy in a row: none throws, and every effect plays out and ends. */
    @GameTest(template = "empty", batch = "noirm_waveb_all", timeoutTicks = 200)
    public static void waveBJutsuAllCastCleanly(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 6.5f, TestClans.PLAIN);
        for (String path : NEW_JUTSU) {
            face(caster, enemy);
            try {
                run(caster, path, 1f);
            } catch (RuntimeException e) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(path + " threw " + e);
            }
        }
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(RockAndCubeDisplays.live() == 0, "every solid shape is gone once its jutsu ends");
            discardProjectiles(helper);
            finish(helper);
        });
    }

    // ------------------------------------------------------------ Scorch

    @GameTest(template = "empty", batch = "noirm_waveb_heat_orbs", timeoutTicks = 100)
    public static void heatMoteDriesOutTheEnemy(GameTestHelper helper) {
        duel(helper, "shakuton/heat_mote", 6.5f, 50, enemy ->
                helper.assertTrue(enemy.hasEffect(MobEffects.HUNGER), "the sun dries them out (thirst)"));
    }

    @GameTest(template = "empty", batch = "noirm_waveb_mummify", timeoutTicks = 120)
    public static void steamingMurderLatchesOn(GameTestHelper helper) {
        duel(helper, "shakuton/steaming_murder", 5.5f, 85, enemy ->
                helper.assertTrue(enemy.getMaxHealth() - enemy.getHealth() >= 8 || !enemy.isAlive(), "pulse after pulse, then the flare: " + enemy.getHealth()));
    }

    /** The shell swallows an enemy jutsu flying at the caster. */
    @GameTest(template = "empty", batch = "noirm_waveb_shell", timeoutTicks = 80)
    public static void ironSandShieldSwallowsProjectiles(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        face(caster, enemy);
        run(caster, "jiton/iron_sand_shield", 1f);
        JutsuProjectile shot = NoirmEntities.JUTSU_PROJECTILE.get().create(helper.getLevel());
        Vec3 from = caster.position().add(0, 1, 2.6);
        shot.setOwner(enemy);
        shot.moveTo(from.x, from.y, from.z, 0, 0);
        shot.configure(com.noirm.clan.Nature.FIRE, 0.4f, 6f, 0.5f, 0f, 0f, 0);
        shot.setDeltaMovement(0, 0, -0.15);
        helper.getLevel().addFreshEntity(shot);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(shot.isRemoved(), "the shield takes the jutsu");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is untouched");
            finish(helper);
        });
    }

    // ------------------------------------------------------------ Dust

    @GameTest(template = "empty", batch = "noirm_waveb_cube", timeoutTicks = 80)
    public static void expandingCubeErasesAndHurts(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 6.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        BlockPos pillar = new BlockPos(2, 2, 6);
        helper.setBlock(pillar, Blocks.STONE);
        face(caster, enemy);
        run(caster, "jinton/expanding_cube", 1f);
        helper.runAfterDelay(12, () -> helper.assertTrue(RockAndCubeDisplays.live() > 0, "a cube of light swells"));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getBlockState(pillar).isAir(), "the stone inside the cube is taken apart");
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), "the enemy inside is hurt");
            helper.assertTrue(RockAndCubeDisplays.live() == 0, "and the cube is gone");
            finish(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_waveb_cone", timeoutTicks = 40)
    public static void coneDetachmentPiercesAWall(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        BlockPos wall = new BlockPos(1, 2, 4);
        helper.setBlock(wall, Blocks.STONE);
        face(caster, enemy);
        run(caster, "jinton/cone_detachment", 1f);
        helper.assertTrue(helper.getBlockState(wall).isAir(), "the stone in the cone is gone");
        helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "the enemy behind it is hit");
        finish(helper);
    }

    // ------------------------------------------------------------ Magnet

    /** Iron armour makes the pull far stronger, and locks them up. */
    @GameTest(template = "empty", batch = "noirm_waveb_pull", timeoutTicks = 40)
    public static void lodestonePullDragsMetal(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        enemy.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        helper.assertTrue(KekkeiTechniquesB.metalPieces(enemy) == 2, "two metal pieces");
        double before = enemy.distanceTo(caster);
        face(caster, enemy);
        run(caster, "jiton/lodestone_pull", 1f);
        helper.assertTrue(com.noirm.combat.CombatSystem.isStaggered(enemy), "their armour locks up");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(enemy.distanceTo(caster) < before - 1, "dragged in: " + before + " -> " + enemy.distanceTo(caster));
            helper.assertTrue(enemy.distanceTo(caster) > 0.8, "but never through the caster");
            finish(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_waveb_arm", timeoutTicks = 60)
    public static void ironSandArmSlams(GameTestHelper helper) {
        duel(helper, "jiton/iron_sand_arm", 6.5f, 20, enemy -> { });
    }

    @GameTest(template = "empty", batch = "noirm_waveb_rain", timeoutTicks = 100)
    public static void ironSandRainFalls(GameTestHelper helper) {
        duel(helper, "jiton/iron_sand_rain", 6.5f, 55, enemy -> { });
    }

    @GameTest(template = "empty", batch = "noirm_waveb_world_order", timeoutTicks = 80)
    public static void worldOrderBranchesHit(GameTestHelper helper) {
        duel(helper, "jiton/world_order", 7.5f, 40, enemy -> { });
    }

    // ------------------------------------------------------------ Wind

    @GameTest(template = "empty", batch = "noirm_waveb_blade", timeoutTicks = 40)
    public static void windBladeCuts(GameTestHelper helper) {
        duel(helper, "futon/wind_blade", 3.5f, 8, enemy -> { });
    }

    /** The Vacuum Wave is a ring: it cuts the enemy behind the caster too, but not the crew mate. */
    @GameTest(template = "empty", batch = "noirm_waveb_vacuum_wave", timeoutTicks = 100)
    public static void vacuumWaveCutsAllAround(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 3.5f, TestClans.EVERY_NATURE);
        SparringNinja ahead = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        SparringNinja behind = ninja(helper, 1.5f, -0.5f, TestClans.PLAIN);
        SparringNinja mate = ninja(helper, 3.5f, 3.5f, TestClans.PLAIN);
        java.util.UUID leader = java.util.UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        for (SparringNinja n : List.of(ahead, behind, mate)) {
            n.setInvulnerable(false);
        }
        // The blades fly out into the chunks round the arena: a blade that crosses into a chunk
        // that isn't entity-ticking yet freezes there, so cast only once every target's chunk ticks.
        whenTicking(helper, List.of(ahead, behind, mate), 60, () -> {
            face(caster, ahead);
            run(caster, "futon/vacuum_wave", 1f);
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(ahead.getHealth() < ahead.getMaxHealth(), "ahead is cut");
                helper.assertTrue(behind.getHealth() < behind.getMaxHealth(), "behind is cut too");
                helper.assertTrue(mate.getHealth() == mate.getMaxHealth(), "the crew mate is spared");
                finish(helper);
            });
        });
    }

    /** Runs {@code then} as soon as every one of {@code who} stands in an entity-ticking chunk (or after {@code maxTicks}). */
    private static void whenTicking(GameTestHelper helper, List<? extends LivingEntity> who, int maxTicks, Runnable then) {
        boolean ready = who.stream().allMatch(e -> helper.getLevel().isPositionEntityTicking(e.blockPosition()));
        if (ready || maxTicks <= 0) {
            then.run();
        } else {
            helper.runAfterDelay(1, () -> whenTicking(helper, who, maxTicks - 1, then));
        }
    }

    @GameTest(template = "empty", batch = "noirm_waveb_tornado", timeoutTicks = 140)
    public static void ragingTornadoCatchesAndKeepsInArena(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 6.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        Vec3 start = enemy.position();
        face(caster, enemy);
        run(caster, "futon/raging_tornado", 1f);
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), "the tornado hurts");
            helper.assertTrue(!enemy.isAlive() || enemy.position().distanceTo(start) < 9, "and doesn't throw them far: " + enemy.position().distanceTo(start));
            finish(helper);
        });
    }

    // ------------------------------------------------------------ Earth

    @GameTest(template = "empty", batch = "noirm_waveb_headhunter", timeoutTicks = 40)
    public static void headhunterComesUpBehind(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 6.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "doton/headhunter", 1f);
        helper.assertTrue(caster.distanceTo(enemy) < 2.5, "the caster comes up beside them");
        helper.assertTrue(com.noirm.combat.CombatSystem.isStaggered(enemy) && enemy.getHealth() < enemy.getMaxHealth(), "their legs are seized");
        finish(helper);
    }

    @GameTest(template = "empty", batch = "noirm_waveb_hounds", timeoutTicks = 80)
    public static void rockHoundsBite(GameTestHelper helper) {
        duel(helper, "doton/rock_hounds", 6.5f, 40, enemy -> { });
    }

    @GameTest(template = "empty", batch = "noirm_waveb_rampart", timeoutTicks = 60)
    public static void rampartRises(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        caster.setYRot(0);
        caster.setYHeadRot(0);
        run(caster, "doton/earth_flow_rampart", 1f);
        helper.runAfterDelay(20, () -> {
            int walls = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-3, 1, 1)), helper.absolutePos(new BlockPos(6, 5, 7)))) {
                if (helper.getLevel().getBlockState(pos).is(NoirmBlocks.EARTH_WALL.get())) {
                    walls++;
                }
            }
            helper.assertTrue(walls >= 12, "a rampart of earth stands: " + walls);
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-3, 1, 1)), helper.absolutePos(new BlockPos(6, 5, 7)))) {
                if (helper.getLevel().getBlockState(pos).is(NoirmBlocks.EARTH_WALL.get())) {
                    helper.getLevel().removeBlock(pos, false);
                }
            }
            finish(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_waveb_weight", timeoutTicks = 40)
    public static void addedWeightRockLightAndHeavy(GameTestHelper helper) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 6.5f, TestClans.PLAIN);
        face(caster, enemy);
        run(caster, "doton/added_weight_rock", 1f);
        helper.assertTrue(caster.hasEffect(MobEffects.JUMP) && caster.hasEffect(MobEffects.SLOW_FALLING), "the caster is light");
        helper.assertTrue(enemy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the enemy is heavy");
        enemy.setDeltaMovement(0, 0.8, 0);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(enemy.getDeltaMovement().y <= 0.01, "and can't leave the ground: " + enemy.getDeltaMovement().y);
            finish(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_waveb_mountain", timeoutTicks = 80)
    public static void fallingMountainCrushes(GameTestHelper helper) {
        duel(helper, "doton/falling_mountain", 6.5f, 40, enemy ->
                helper.assertTrue(RockAndCubeDisplays.live() == 0, "the rock mass is gone once it lands"));
    }

    // ------------------------------------------------------------ helpers

    private interface Check {
        void check(SparringNinja enemy);
    }

    /** Caster at the near end, enemy {@code enemyZ} ahead: after {@code delay} the enemy is hurt and the caster isn't. */
    private static void duel(GameTestHelper helper, String path, float enemyZ, int delay, Check check) {
        arena(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, enemyZ, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        caster.setInvulnerable(false);
        face(caster, enemy);
        run(caster, path, 1f);
        helper.runAfterDelay(delay, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), path + " hits the enemy");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), path + " never hits its caster");
            check.check(enemy);
            discardProjectiles(helper);
            finish(helper);
        });
    }

    private static void run(LivingEntity caster, String path, float charge) {
        ResourceLocation id = Noirm.id(path);
        JutsuDefinition def = JutsuData.get(id);
        com.noirm.jutsu.FriendlyZones.mark(caster);
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), charge));
    }

    /** A stone strip with clear air above it, wide enough for every jutsu here. */
    private static void arena(GameTestHelper helper) {
        openFloor(helper, -3, -2, 6, 10, 7);
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(14);
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                e -> !(e instanceof LivingEntity)).forEach(net.minecraft.world.entity.Entity::discard);
    }

    /**
     * Ends a test and leaves the space as it found it: craters, erased blocks and spikes healed at
     * once (not after a minute), earth walls taken down, stray projectiles and fighters removed.
     * Later tests stand on this ground.
     */
    private static void finish(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 4));
        com.noirm.world.Destruction.healNear(helper.getLevel(), Vec3.atCenterOf(center), 24);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -2, -12), center.offset(12, 8, 12))) {
            if (helper.getLevel().getBlockState(pos).is(NoirmBlocks.EARTH_WALL.get())) {
                helper.getLevel().removeBlock(pos, false);
            }
        }
        discardProjectiles(helper);
        clearAround(helper);
    }

    private static void discardProjectiles(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(30);
        helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, area).forEach(JutsuProjectile::discard);
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.setHealth(ninja.getMaxHealth());
        return ninja;
    }

    private static JsonObject lang(String file) {
        Path path = ModList.get().getModFileById(Noirm.MOD_ID).getFile().findResource("assets", Noirm.MOD_ID, "lang", file + ".json");
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

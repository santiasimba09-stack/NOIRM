package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.jutsu.ModeSystem;
import com.noirm.registry.NoirmEntities;
import com.noirm.world.Destruction;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The chakra wave, share A: twenty new Ice, Lava, Boil, Storm and Explosion jutsu. Every one
 * loads with its seals and names and casts cleanly; each new behaviour does what it says, hits
 * enemies and spares the caster's side.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KekkeiAGameTests {
    /** Every jutsu this share added. */
    static final List<String> NEW_JUTSU = List.of(
            "hyoton/ice_spear", "hyoton/freezing_field", "hyoton/ice_dragon", "hyoton/demonic_mirrors",
            "yoton/magma_bullet", "yoton/quicklime_trap", "yoton/lava_armor", "yoton/volcanic_eruption",
            "futton/acid_mist", "futton/scalding_geyser", "futton/vapour_armor", "futton/pressure_dome",
            "ranton/black_cloud", "ranton/light_fang", "ranton/thunder_tide", "ranton/hundred_bends",
            "bakuton/clay_swallows", "bakuton/detonating_fist", "bakuton/clay_effigy", "bakuton/final_art");

    private KekkeiAGameTests() {}

    @GameTest(template = "empty")
    public static void kekkeiAJutsuLoadWithSealsAndNames(GameTestHelper helper) {
        List<JsonObject> langs = List.of(lang("en_us"), lang("es_es"), lang("es_mx"));
        int perNature[] = new int[5];
        List<String> natures = List.of("hyoton", "yoton", "futton", "ranton", "bakuton");
        for (String path : NEW_JUTSU) {
            ResourceLocation id = Noirm.id(path);
            JutsuDefinition def = JutsuData.get(id);
            helper.assertTrue(def != null, "missing " + path);
            helper.assertTrue(JutsuBehaviors.exists(def.behavior()), "unknown behaviour for " + path);
            helper.assertTrue(JutsuSeals.get(id) != null && !JutsuSeals.get(id).seals().isEmpty(), "no hand seals for " + path);
            helper.assertTrue(def.nature().isPresent(), "no nature for " + path);
            helper.assertTrue(def.starter() || def.unlock().map(u -> u.after() != null && JutsuData.get(u.after()) != null).orElse(false),
                    path + " is a starter or unlocks from a jutsu that exists");
            String key = "jutsu.noirm." + path.replace('/', '.');
            for (JsonObject lang : langs) {
                helper.assertTrue(lang.has(key), "no name for " + key);
                helper.assertTrue(lang.has("screen.noirm.jutsu.behavior." + def.behavior()) || List.of("projectile", "mode").contains(def.behavior()),
                        "no behaviour name for " + def.behavior());
            }
            perNature[natures.indexOf(path.substring(0, path.indexOf('/')))]++;
        }
        for (int n : perNature) {
            helper.assertTrue(n >= 4, "four or more new jutsu per nature");
        }
        helper.assertTrue(NEW_JUTSU.stream().filter(p -> "S".equals(JutsuData.get(Noirm.id(p)).rank())).count() == 5, "one S-rank signature per nature");
        helper.succeed();
    }

    /** Every new jutsu casts without an error, one after the other (terrain left alone). */
    @GameTest(template = "empty", batch = "noirm_kekkei_a_all", timeoutTicks = 200)
    public static void everyKekkeiAJutsuCasts(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 9);
        terrain(helper, false);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 6.5f, TestClans.PLAIN);
        aimAt(caster, enemy);
        for (String path : NEW_JUTSU) {
            try {
                run(caster, path, 1f);
                if ("yoton/lava_armor".equals(path)) {
                    helper.assertTrue(ModeSystem.isActive(caster, Noirm.id(path)), "Magma Armour switches on");
                    run(caster, path, 1f); // and off again
                }
            } catch (RuntimeException e) {
                helper.fail(path + " threw " + e);
            }
            aimAt(caster, enemy);
        }
        helper.runAfterDelay(130, () -> {
            terrain(helper, true);
            discardProjectiles(helper);
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Ice

    @GameTest(template = "empty", batch = "noirm_kekkei_a_frost", timeoutTicks = 60)
    public static void freezingFieldFreezesWhoeverItReaches(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 6);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja mate = ninja(helper, 0.5f, 1.5f, TestClans.PLAIN);
        SparringNinja enemy = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        crew(caster, mate);
        vulnerable(caster, mate, enemy);
        run(caster, "hyoton/freezing_field", 1f);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(hurt(enemy), "the frost reaches the enemy");
            helper.assertTrue(!enemy.isAlive() || enemy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slows it");
            untouched(helper, caster, mate);
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_mirrors", timeoutTicks = 140)
    public static void demonicMirrorsTrapAndPierce(GameTestHelper helper) {
        clearStrays(helper);
        openFloor(helper, -4, -1, 6, 12, 5);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        run(caster, "hyoton/demonic_mirrors", 1f);
        int panes = count(helper, enemy.blockPosition(), 5, Blocks.ICE);
        helper.assertTrue(panes > 0, "mirrors of ice rise round the enemy");
        helper.runAfterDelay(30, () -> helper.assertTrue(hurt(enemy), "senbon fly from the mirrors"));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(count(helper, enemy.blockPosition(), 6, Blocks.ICE) == 0, "and the mirrors shatter at the end");
            untouched(helper, caster);
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Lava

    @GameTest(template = "empty", batch = "noirm_kekkei_a_lime", timeoutTicks = 60)
    public static void quicklimeSetsAroundTheEnemy(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 7);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        run(caster, "yoton/quicklime_trap", 1f);
        helper.assertTrue(!hurt(enemy), "the glob is still in the air");
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(hurt(enemy) && CombatSystem.isStaggered(enemy), "the lime sets: hurt and held");
            helper.assertTrue(enemy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_volcano", timeoutTicks = 120)
    public static void volcanoRupturesAndTheGroundMelts(GameTestHelper helper) {
        clearStrays(helper);
        openFloor(helper, -6, -1, 8, 14, 5);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        BlockPos vent = enemy.blockPosition();
        run(caster, "yoton/volcanic_eruption", 1f);
        helper.assertTrue(hurt(enemy) && enemy.getRemainingFireTicks() > 0 || !enemy.isAlive(), "the rupture burns the enemy");
        helper.assertTrue(countAbs(helper, vent, 5, Blocks.MAGMA_BLOCK) > 0, "the ground turns to magma");
        untouched(helper, caster);
        helper.runAfterDelay(80, () -> {
            // Put the ground back now rather than in 15 seconds, so later tests stand on stone.
            meltBack(helper, vent, 7);
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_armor", timeoutTicks = 40)
    public static void magmaArmourBurnsWhoeverStrikesIt(GameTestHelper helper) {
        clearStrays(helper);
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        run(caster, "yoton/lava_armor", 1f);
        helper.assertTrue(ModeSystem.isActive(caster, Noirm.id("yoton/lava_armor")), "the armour is on");
        caster.hurt(caster.damageSources().mobAttack(enemy), 1f);
        helper.assertTrue(enemy.getRemainingFireTicks() > 0, "the attacker is burned");
        ModeSystem.end(caster);
        clearAround(helper);
    }

    // ------------------------------------------------------------ Boil

    @GameTest(template = "empty", batch = "noirm_kekkei_a_acid", timeoutTicks = 40)
    public static void acidMistCorrodesArmour(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 4);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 3f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        enemy.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        aimAt(caster, enemy);
        run(caster, "futton/acid_mist", 1f);
        helper.assertTrue(hurt(enemy) && enemy.hasEffect(MobEffects.WEAKNESS), "the acid scalds and weakens");
        helper.assertTrue(enemy.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() > 0, "and eats into the armour");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_geyser", timeoutTicks = 60)
    public static void geyserHissesThenThrowsUp(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 7);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        run(caster, "futton/scalding_geyser", 1f);
        helper.runAfterDelay(6, () -> helper.assertTrue(!hurt(enemy), "a warning hiss first"));
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(hurt(enemy), "then the jet scalds");
            helper.assertTrue(enemy.getY() > helper.absoluteVec(new Vec3(0, 1.2, 0)).y || enemy.getDeltaMovement().y > 0, "and throws it up");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_charge", timeoutTicks = 60)
    public static void vapourArmourChargesThrough(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 12);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 4.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        double start = caster.getZ();
        run(caster, "futton/vapour_armor", 1f);
        helper.assertTrue(caster.hasEffect(MobEffects.DAMAGE_RESISTANCE), "wrapped in boiling steam");
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(caster.getZ() > start + 2.5, "the caster charges forward: " + (caster.getZ() - start));
            helper.assertTrue(hurt(enemy), "and bowls through the enemy");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_dome", timeoutTicks = 80)
    public static void pressureDomeBuildsThenBursts(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 9);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        Vec3 start = enemy.position();
        run(caster, "futton/pressure_dome", 1f);
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(hurt(enemy), "the steam scalds the trapped enemy");
            helper.assertTrue(!enemy.isAlive() || enemy.position().distanceTo(start) > 1.5,
                    "then the dome bursts and throws it out: moved " + enemy.position().distanceTo(start));
            untouched(helper, caster);
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Storm

    @GameTest(template = "empty", batch = "noirm_kekkei_a_cloud", timeoutTicks = 80)
    public static void blackCloudDarkensAndStrikes(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 7);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        run(caster, "ranton/black_cloud", 1f);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(enemy.hasEffect(MobEffects.DARKNESS), "the cloud darkens the enemy's eyes");
            helper.assertTrue(hurt(enemy), "and strikes it");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_laser", timeoutTicks = 40)
    public static void lightFangPiercesTheLine(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 10);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja first = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        SparringNinja second = ninja(helper, 1.5f, 9.5f, TestClans.PLAIN);
        vulnerable(caster, first, second);
        aimAt(caster, first);
        caster.setXRot(caster.getXRot() * 0.5f); // level enough to reach the second one behind
        run(caster, "ranton/light_fang", 1f);
        helper.assertTrue(hurt(first) && CombatSystem.isStaggered(first), "the laser strikes the first");
        helper.assertTrue(hurt(second), "and pierces on to the second");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_tide", timeoutTicks = 60)
    public static void thunderTideRollsOverTheEnemy(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 8);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja mate = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        crew(caster, mate);
        vulnerable(caster, mate, enemy);
        aimAt(caster, enemy);
        run(caster, "ranton/thunder_tide", 1f);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(hurt(enemy), "the charged wave hits");
            untouched(helper, caster, mate);
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_bends", timeoutTicks = 60)
    public static void hundredBendsFindsEveryEnemy(GameTestHelper helper) {
        clearStrays(helper);
        openFloor(helper, -3, -1, 5, 8, 5);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja mate = ninja(helper, 2.5f, 3.5f, TestClans.PLAIN);
        List<SparringNinja> enemies = List.of(ninja(helper, -2.5f, 4.5f, TestClans.PLAIN), ninja(helper, 1.5f, 6.5f, TestClans.PLAIN),
                ninja(helper, 4.5f, 5.5f, TestClans.PLAIN));
        crew(caster, mate);
        vulnerable(caster, mate);
        enemies.forEach(e -> e.setInvulnerable(false));
        aimAt(caster, enemies.get(1));
        run(caster, "ranton/hundred_bends", 1f);
        helper.runAfterDelay(10, () -> {
            for (SparringNinja enemy : enemies) {
                helper.assertTrue(hurt(enemy), "a laser bends to every enemy");
            }
            untouched(helper, caster, mate);
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ Explosion

    @GameTest(template = "empty", batch = "noirm_kekkei_a_fist", timeoutTicks = 40)
    public static void detonatingFistGoesOffOnContact(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 4);
        terrain(helper, false);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        run(caster, "bakuton/detonating_fist", 1f);
        terrain(helper, true);
        helper.assertTrue(hurt(enemy) && CombatSystem.isStaggered(enemy), "the fist blows the enemy away");
        untouched(helper, caster);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_effigy", timeoutTicks = 60)
    public static void clayEffigyFallsAndExplodes(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 9);
        terrain(helper, false);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 8.5f, TestClans.PLAIN);
        vulnerable(caster, enemy);
        aimAt(caster, enemy);
        run(caster, "bakuton/clay_effigy", 1f);
        helper.runAfterDelay(12, () -> helper.assertTrue(!hurt(enemy), "the effigy is still falling"));
        helper.runAfterDelay(30, () -> {
            terrain(helper, true);
            helper.assertTrue(hurt(enemy), "it lands and explodes");
            untouched(helper, caster);
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_final_art", timeoutTicks = 40)
    public static void finalArtBlastsEverythingAndBreaksTheCaster(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 8);
        terrain(helper, false);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja near = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        SparringNinja far = ninja(helper, 1.5f, 6.5f, TestClans.PLAIN);
        vulnerable(caster, near, far);
        float before = caster.getHealth();
        run(caster, "bakuton/final_art", 1f);
        terrain(helper, true);
        helper.assertTrue(hurt(near) && hurt(far), "everyone around is caught in the blast");
        helper.assertTrue(caster.getHealth() <= before * 0.4f && caster.isAlive(), "the backlash leaves the caster barely alive: " + caster.getHealth());
        helper.assertTrue(CombatSystem.isStaggered(caster) && caster.hasEffect(MobEffects.WEAKNESS), "and crippled");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_kekkei_a_swallows", timeoutTicks = 30)
    public static void claySwallowsAreBirds(GameTestHelper helper) {
        clearStrays(helper);
        pad(helper, 0, 6);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        caster.setYRot(0); // facing +z
        run(caster, "bakuton/clay_swallows", 1f);
        List<JutsuProjectile> birds = helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, new AABB(caster.blockPosition()).inflate(4));
        helper.assertTrue(birds.size() == 3, "three clay swallows: " + birds.size());
        discardProjectiles(helper);
        clearAround(helper);
    }

    // ------------------------------------------------------------ helpers

    private static JsonObject lang(String name) {
        try (var in = KekkeiAGameTests.class.getResourceAsStream("/assets/noirm/lang/" + name + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("can't read " + name, e);
        }
    }

    private static void run(LivingEntity caster, String path, float charge) {
        ResourceLocation id = Noirm.id(path);
        JutsuDefinition def = JutsuData.get(id);
        com.noirm.jutsu.FriendlyZones.mark(caster);
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), charge));
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.setHealth(ninja.getMaxHealth());
        return ninja;
    }

    private static void crew(SparringNinja... members) {
        UUID leader = UUID.randomUUID();
        for (SparringNinja member : members) {
            member.joinCrew(leader);
        }
    }

    private static void vulnerable(LivingEntity... all) {
        for (LivingEntity e : all) {
            e.setInvulnerable(false);
        }
    }

    private static boolean hurt(LivingEntity e) {
        return !e.isAlive() || e.getHealth() < e.getMaxHealth();
    }

    private static void untouched(GameTestHelper helper, LivingEntity... all) {
        for (LivingEntity e : all) {
            helper.assertTrue(e.getHealth() == e.getMaxHealth(), "the caster's side is never hit: " + e.getHealth());
        }
    }

    /** Looks straight at the middle of {@code target}, as a player aiming would. */
    private static void aimAt(LivingEntity caster, LivingEntity target) {
        face(caster, target);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(caster.getEyePosition());
        caster.setXRot((float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance())));
        caster.xRotO = caster.getXRot();
    }

    private static void terrain(GameTestHelper helper, boolean on) {
        helper.getLevel().getGameRules().getRule(Destruction.TERRAIN_DAMAGE).set(on, helper.getLevel().getServer());
    }

    /** Blocks of {@code block} within {@code r} of an absolute position. */
    private static int countAbs(GameTestHelper helper, BlockPos center, int r, net.minecraft.world.level.block.Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            if (helper.getLevel().getBlockState(pos).is(block)) {
                n++;
            }
        }
        return n;
    }

    private static int count(GameTestHelper helper, BlockPos center, int r, net.minecraft.world.level.block.Block block) {
        return countAbs(helper, center, r, block);
    }

    private static void meltBack(GameTestHelper helper, BlockPos center, int r) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            if (helper.getLevel().getBlockState(pos).is(Blocks.MAGMA_BLOCK)) {
                helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    /** Stone under x 0..2, z0..z1 with clear air above (and leftover non-living entities removed). */
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

    private static void discardProjectiles(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(30);
        helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, area).forEach(JutsuProjectile::discard);
    }
}

package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.WoodClone;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.jutsu.NpcJutsuSets;
import com.noirm.jutsu.Substitution;
import com.noirm.jutsu.WoodColossus;
import com.noirm.jutsu.WoodDragon;
import com.noirm.registry.NoirmBlocks;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The chakra wave's Wood Release (jutsu/WoodTechniques): Root Snare, Wood Clone, Wood Locking
 * Wall, Wood Dragon, Deep Forest Emergence and the Thousand-Arm Colossus. Each hits (or holds) its
 * enemy, spares its caster, and cleans up after itself: the dome splinters away, the dragon and
 * the colossus leave, the forest's trunks are registered to regrow.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WoodReleaseGameTests {
    static final List<String> NEW_WOOD = List.of("mokuton/root_snare", "mokuton/wood_clone", "mokuton/wood_locking_wall",
            "mokuton/wood_dragon", "mokuton/deep_forest_emergence", "mokuton/thousand_arm_colossus");
    static final Clan WOOD = TestClans.clan("test_wood", Map.of(Nature.WOOD, Clan.PRIMARY_AFFINITY, Nature.EARTH, Clan.SECONDARY_AFFINITY),
            Optional.empty(), com.noirm.clan.AwakeningPath.WILL);

    private WoodReleaseGameTests() {}

    @GameTest(template = "empty")
    public static void newWoodJutsuLoadWithSealsAndNames(GameTestHelper helper) {
        List<JsonObject> langs = List.of(lang("en_us"), lang("es_es"), lang("es_mx"));
        Set<String> ranks = new HashSet<>();
        for (String path : NEW_WOOD) {
            ResourceLocation id = Noirm.id(path);
            JutsuDefinition def = JutsuData.get(id);
            helper.assertTrue(def != null, "missing " + path);
            helper.assertTrue(def.nature().orElse(null) == Nature.WOOD, path + " is Wood Release");
            helper.assertTrue(JutsuBehaviors.exists(def.behavior()), "unknown behavior for " + path);
            helper.assertTrue(JutsuSeals.get(id) != null && !JutsuSeals.get(id).seals().isEmpty(), "no hand seals for " + path);
            String key = "jutsu.noirm." + path.replace('/', '.');
            for (JsonObject json : langs) {
                helper.assertTrue(json.has(key), "no name for " + key);
            }
            def.unlock().filter(u -> u.after() != null).ifPresent(u -> helper.assertTrue(JutsuData.get(u.after()) != null, path + " unlocks after a missing jutsu"));
            ranks.add(def.rank());
        }
        helper.assertTrue(ranks.containsAll(List.of("C", "B", "A", "S")), "a basic, a tool, an attack and a signature: " + ranks);
        for (JsonObject json : langs) {
            helper.assertTrue(json.has("entity.noirm.wood_clone") && json.has("entity.noirm.wood_dragon") && json.has("entity.noirm.wood_colossus")
                    && json.has("block.noirm.mokuton_wood"), "the wood entities and block have names");
        }
        helper.succeed();
    }

    /** Wood ninjas know the new techniques: across many rolls, a kage of wood draws every one of them. */
    @GameTest(template = "empty")
    public static void woodNinjasRollTheNewJutsu(GameTestHelper helper) {
        Set<ResourceLocation> seen = new HashSet<>();
        for (long seed = 0; seed < 400; seed++) {
            seen.addAll(NpcJutsuSets.roll(NpcJutsuSets.Tier.KAGE, Map.of(Nature.WOOD, 1f, Nature.EARTH, 0.6f), "test_wood", false, seed));
        }
        for (String path : NEW_WOOD) {
            helper.assertTrue(seen.contains(Noirm.id(path)), "no wood kage ever knows " + path);
        }
        Set<ResourceLocation> genin = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            genin.addAll(NpcJutsuSets.roll(NpcJutsuSets.Tier.GENIN, Map.of(Nature.WOOD, 1f), "test_wood", false, seed));
        }
        helper.assertTrue(genin.contains(Noirm.id("mokuton/root_snare")), "a wood genin can know the basic Root Snare: " + genin);
        helper.succeed();
    }

    /** Every new wood jutsu casts with nobody in front, and what it leaves is cleaned up. */
    @GameTest(template = "empty", batch = "noirm_wood_all", timeoutTicks = 60)
    public static void everyWoodJutsuCastsWithNoTarget(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f);
        facePoint(caster, caster.position().add(0, 0, 5));
        for (String path : NEW_WOOD) {
            run(caster, path, 1f);
        }
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(caster.isAlive() && caster.getHealth() == caster.getMaxHealth(), "nothing it made turned on its caster");
            cleanUp(helper);
            clearAround(helper);
        });
    }

    /** Roots race under the ground to the enemy: hurt, held, and real roots round their feet for a moment. */
    @GameTest(template = "empty", batch = "noirm_wood_roots", timeoutTicks = 60)
    public static void rootSnareHoldsTheEnemy(GameTestHelper helper) {
        floor(helper);
        pad(helper, 3, 8);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 6.5f);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "mokuton/root_snare", 1f);
        helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "the roots hurt");
        helper.assertTrue(CombatSystem.isStaggered(enemy), "and hold the enemy");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is never hit");
            cleanUp(helper);
            clearAround(helper);
        });
    }

    /**
     * Two wood clones step out in a fan behind the caster. Whoever breaks one is caught by its roots;
     * with Substitution ready, the caster trades places with the other.
     */
    @GameTest(template = "empty", batch = "noirm_wood_clone", timeoutTicks = 200)
    public static void woodClonesFanOutTrapAndStandIn(GameTestHelper helper) {
        clearStrays(helper);
        openFloor(helper, -2, -4, 4, 6, 3);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 4.5f);
        enemy.setInvulnerable(false);
        // The clones step out ~2 blocks behind the caster, which can be the next chunk over: wait until
        // its entities are loaded and ticking (TestWorld forces it, the load itself takes a few ticks),
        // or a clone placed there exists but can't be found yet.
        BlockPos at = caster.blockPosition();
        whenEntityTicking(helper, List.of(at.offset(-3, 0, -3), at.offset(0, 0, -3), at.offset(3, 0, -3)), () -> {
            facePoint(caster, caster.position().add(0, 0, 5)); // facing +z
            run(caster, "mokuton/wood_clone", 1f);
            List<WoodClone> clones = clones(helper, caster);
            helper.assertTrue(clones.size() == 2, "two wood clones: " + clones.size());
            helper.assertTrue(clones.stream().allMatch(c -> c.getZ() < caster.getZ() - 0.5), "both behind the caster");
            helper.assertTrue(Math.abs(clones.get(0).getX() - clones.get(1).getX()) > 2, "fanned out to the sides");
            helper.assertTrue(clones.stream().allMatch(c -> c.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()), "carved, not dressed");
            WoodClone first = clones.get(0);
            enemy.setPos(first.getX(), first.getY(), first.getZ() + 1.2);
            first.hurt(enemy.damageSources().mobAttack(enemy), 1000f);
            helper.assertTrue(first.isRemoved(), "the clone breaks");
            helper.assertTrue(CombatSystem.isStaggered(enemy), "and its roots catch whoever broke it");
            WoodClone second = clones.get(1);
            Vec3 spot = second.position();
            Vec3 from = caster.position();
            Substitution.arm(caster, 20);
            helper.assertTrue(Substitution.trySubstitute(caster, enemy), "the substitution fires");
            helper.assertTrue(caster.position().distanceTo(spot) < 0.6, "the caster takes the clone's place: " + caster.position() + " vs " + spot);
            helper.assertTrue(second.isRemoved() && second.position().distanceTo(from) < 0.6, "the clone took the caster's place, and the hit");
            cleanUp(helper);
            clearAround(helper);
        });
    }

    /** The dome closes over its caster from the ground up, then splinters away by itself. */
    @GameTest(template = "empty", batch = "noirm_wood_dome", timeoutTicks = 200)
    public static void woodLockingWallClosesThenSplinters(GameTestHelper helper) {
        clearStrays(helper);
        openFloor(helper, -4, -4, 6, 6, 6);
        SparringNinja caster = ninja(helper, 1.5f, 1.5f);
        run(caster, "mokuton/wood_locking_wall", 1f);
        helper.runAfterDelay(8, () -> {
            int wood = countWood(helper, 6);
            helper.assertTrue(wood > 40, "a dome of wood stands round the caster: " + wood);
            helper.assertTrue(helper.getLevel().getBlockState(caster.blockPosition()).isAir()
                    && helper.getLevel().getBlockState(caster.blockPosition().above()).isAir(), "with room inside for its caster");
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(countWood(helper, 6) == 0, "and it's gone after its time: " + countWood(helper, 6));
            clearAround(helper);
        });
    }

    /** The dragon surges to the enemy, coils round it, drinks its chakra, then crushes it and falls apart. */
    @GameTest(template = "empty", batch = "noirm_wood_dragon", timeoutTicks = 160)
    public static void woodDragonCoilsDrainsAndCrushes(GameTestHelper helper) {
        clearStrays(helper);
        floor(helper);
        pad(helper, 3, 9);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 8.5f);
        enemy.setInvulnerable(false);
        enemy.setChakra(80f);
        caster.setChakra(20f);
        // Ninjas regain chakra by themselves: these two, untouched, show how much.
        SparringNinja restingHigh = ninja(helper, 0.5f, 4.5f);
        SparringNinja restingLow = ninja(helper, 2.5f, 4.5f);
        restingHigh.setChakra(80f);
        restingLow.setChakra(20f);
        face(caster, enemy);
        run(caster, "mokuton/wood_dragon", 1f);
        List<WoodDragon> dragons = helper.getLevel().getEntitiesOfClass(WoodDragon.class, new AABB(caster.blockPosition()).inflate(8));
        helper.assertTrue(dragons.size() == 1 && dragons.get(0).heldTarget() == enemy, "one dragon, after the enemy");
        WoodDragon dragon = dragons.get(0);
        int hold = dragon.hold();
        List<Vec3> body = dragon.path(enemy.position(), enemy.getBbWidth(), enemy.getBbHeight(), WoodDragon.SURGE + 5);
        helper.assertTrue(body.size() > 15, "a long body: " + body.size() + " segments");
        helper.assertTrue(body.get(0).y > enemy.getY() + enemy.getBbHeight(), "its head rears over the enemy's");
        helper.runAfterDelay(WoodDragon.SURGE + 10, () -> {
            helper.assertTrue(CombatSystem.isStaggered(enemy), "the coiled enemy can't act");
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "the catch hurts");
        });
        helper.runAfterDelay(WoodDragon.SURGE + 45, () -> {
            helper.assertTrue(enemy.chakra() < restingHigh.chakra() - 5f, "it drinks the enemy's chakra: " + enemy.chakra() + " vs " + restingHigh.chakra());
            helper.assertTrue(caster.chakra() > restingLow.chakra() + 2f, "and some flows back to the caster: " + caster.chakra() + " vs " + restingLow.chakra());
        });
        helper.runAfterDelay(WoodDragon.SURGE + hold + 6, () -> {
            helper.assertTrue(dragon.isRemoved(), "after the crush it falls apart");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is never hit");
            clearAround(helper);
        });
    }

    /** Huge trunks burst out across the field: the enemy is thrown up; the trunks are registered to regrow away. */
    @GameTest(template = "empty", batch = "noirm_deep_forest", timeoutTicks = 120)
    public static void deepForestEmergesUnderTheEnemy(GameTestHelper helper) {
        clearStrays(helper, 32);
        openFloor(helper, -4, -2, 7, 14, 12);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 8.5f);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        // A smaller forest than the real one, so it fits the test's cleared space.
        run(caster, "mokuton/deep_forest_emergence", 1f, p -> new JutsuDefinition.Params(p.damage(), 0, p.speed(), 4f, p.range(), p.knockback(),
                p.posture(), 30, p.stun(), 0, 5, 0, "", "", 40, 0, ""));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth(), "the first trunk bursts under the enemy");
            helper.assertTrue(CombatSystem.isStaggered(enemy), "and throws it off its feet");
        });
        helper.runAfterDelay(40, () -> {
            int wood = countWood(helper, 12);
            helper.assertTrue(wood > 30, "a forest of trunks stands: " + wood);
            helper.assertTrue(woodPositions(helper, 12).stream().allMatch(com.noirm.world.Destruction::willRestore), "every block of it will regrow away");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is never hit");
            helper.assertTrue(helper.getLevel().getBlockState(caster.blockPosition()).isAir(), "and nothing grows inside its caster");
            cleanUp(helper);
            clearAround(helper);
        });
    }

    /** The colossus rises behind the caster and its first fist comes down on the enemy; then it sinks away. */
    @GameTest(template = "empty", batch = "noirm_wood_colossus", timeoutTicks = 220)
    public static void colossusFistsCrashOnTheEnemy(GameTestHelper helper) {
        clearStrays(helper, 32); // (a stray further down the line would be picked: a level gaze favours the far one)
        openFloor(helper, -2, -2, 4, 12, 6); // clear sight from caster to enemy (earlier tests leave things around)
        SparringNinja caster = ninja(helper, 1.5f, 0.5f);
        SparringNinja enemy = ninja(helper, 1.5f, 10.5f);
        enemy.setInvulnerable(false);
        face(caster, enemy);
        Vec3 to = enemy.position().add(0, enemy.getBbHeight() * 0.5, 0).subtract(caster.getEyePosition());
        caster.setXRot((float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance())));
        caster.xRotO = caster.getXRot();
        run(caster, "mokuton/thousand_arm_colossus", 1f);
        List<WoodColossus> found = helper.getLevel().getEntitiesOfClass(WoodColossus.class, new AABB(caster.blockPosition()).inflate(10));
        helper.assertTrue(found.size() == 1, "one colossus");
        WoodColossus colossus = found.get(0);
        helper.assertTrue(colossus.position().distanceTo(enemy.position()) > caster.position().distanceTo(enemy.position()), "it rises behind its caster");
        helper.assertTrue(colossus.fistTarget(0).distanceTo(enemy.position()) < 1, "the first fist is aimed at the enemy: " + colossus.fistTarget(0) + " vs " + enemy.position() + ", in sight " + caster.hasLineOfSight(enemy));
        for (int s = 1; s < colossus.strikes(); s++) {
            helper.assertTrue(colossus.fistTarget(s).distanceTo(colossus.focus()) <= WoodColossus.SPREAD + 0.01, "every fist falls round the target");
        }
        Vec3 swing = colossus.strikeHand(0, WoodColossus.strikeStart(0) + WoodColossus.STRIKE_LAND + 1);
        helper.assertTrue(swing != null && swing.distanceTo(colossus.fistTarget(0)) < 2, "the drawn fist lands where it hits");
        int lands = WoodColossus.strikeStart(0) + WoodColossus.STRIKE_LAND;
        helper.runAfterDelay(lands - 2, () -> helper.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "nothing before the fist lands"));
        helper.runAfterDelay(lands + 2, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), "the fist crushes the enemy");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is never hit");
        });
        helper.runAfterDelay(colossus.lifetime() + 4, () -> {
            helper.assertTrue(colossus.isRemoved(), "it sinks back into the ground and is gone");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    private static void run(LivingEntity caster, String path, float charge) {
        run(caster, path, charge, p -> p);
    }

    private static void run(LivingEntity caster, String path, float charge, java.util.function.UnaryOperator<JutsuDefinition.Params> tweak) {
        ResourceLocation id = Noirm.id(path);
        JutsuDefinition def = JutsuData.get(id);
        JutsuDefinition used = new JutsuDefinition(def.nature(), def.cast(), def.behavior(), def.chakraCost(), def.cooldown(), def.minCharge(),
                def.maxCharge(), def.maxDuration(), def.requires(), def.starter(), def.rank(), tweak.apply(def.params()), def.unlock(), def.mode(), def.quick());
        com.noirm.jutsu.FriendlyZones.mark(caster);
        JutsuBehaviors.get(used.behavior()).cast(new JutsuContext(caster, id, used, JutsuCaster.power(caster, id, def), charge));
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.sealBeast(0);
        ninja.joinClan(WOOD);
        ninja.setHealth(ninja.getMaxHealth());
        ninja.setInvulnerable(false);
        return ninja;
    }

    /** Runs {@code then} once every spot's chunk has its entities loaded and ticking. */
    private static void whenEntityTicking(GameTestHelper helper, List<BlockPos> spots, Runnable then) {
        if (spots.stream().allMatch(p -> helper.getLevel().isPositionEntityTicking(p))) {
            then.run();
        } else {
            helper.runAfterDelay(1, () -> whenEntityTicking(helper, spots, then));
        }
    }

    private static List<WoodClone> clones(GameTestHelper helper, LivingEntity owner) {
        return helper.getLevel().getEntitiesOfClass(WoodClone.class, new AABB(owner.blockPosition()).inflate(8), c -> c.isOwnedBy(owner));
    }

    private static int countWood(GameTestHelper helper, int reach) {
        return woodPositions(helper, reach).size();
    }

    private static List<BlockPos> woodPositions(GameTestHelper helper, int reach) {
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BlockPos> found = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -1, -reach), center.offset(reach, reach + 6, reach + 8))) {
            if (helper.getLevel().getBlockState(pos).is(NoirmBlocks.MOKUTON_WOOD.get())) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /** Removes the wood, dragons, colossi and clones a test left (the forest would otherwise stand for a minute). */
    private static void cleanUp(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -2, -16), center.offset(16, 22, 30))) {
            if (helper.getLevel().getBlockState(pos).is(NoirmBlocks.MOKUTON_WOOD.get())) {
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        AABB area = new AABB(center).inflate(40);
        helper.getLevel().getEntitiesOfClass(Entity.class, area, e -> e instanceof WoodDragon || e instanceof WoodColossus || e instanceof WoodClone)
                .forEach(Entity::discard);
    }

    /** Stone under a strip from {@code z0} to {@code z1} (x 0..2) and clear air above it. */
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

    private static JsonObject lang(String code) {
        try (var in = WoodReleaseGameTests.class.getResourceAsStream("/assets/noirm/lang/" + code + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("can't read " + code, e);
        }
    }
}

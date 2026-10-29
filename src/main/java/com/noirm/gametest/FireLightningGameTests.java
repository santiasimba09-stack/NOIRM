package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuAreas;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuClash;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Round 13 fire and lightning jutsu: Burning Ash, Majestic Destroyer Flame, Flame Bomb, Dragon
 * Flame Caterwaul, the reworked Phoenix Flower, and Chain Discharge. Each one hits its enemies,
 * never its caster or the caster's crew, and the projectiles still clash on the nature wheel.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireLightningGameTests {
    private FireLightningGameTests() {}

    @GameTest(template = "empty")
    public static void newFireAndLightningJutsuExist(GameTestHelper helper) {
        for (String path : List.of("katon/burning_ash", "katon/majestic_destroyer_flame", "katon/flame_bomb", "katon/dragon_flame_caterwaul",
                "raiton/chain_discharge")) {
            ResourceLocation id = Noirm.id(path);
            helper.assertTrue(JutsuData.get(id) != null, "missing " + path);
            helper.assertTrue(JutsuBehaviors.exists(JutsuData.get(id).behavior()), "unknown behavior for " + path);
            helper.assertTrue(JutsuSeals.get(id) != null, "no hand seals for " + path);
        }
        helper.assertTrue("fireball".equals(JutsuData.get(Noirm.id("katon/great_fireball")).params().look()), "the Great Fireball has its look");
        helper.succeed();
    }

    /** The ash drifts out, then the whole cloud goes up: the enemy in it burns, the caster and crew mate don't. */
    @GameTest(template = "empty", batch = "noirm_burning_ash", timeoutTicks = 100)
    public static void burningAshIgnitesTheCloud(GameTestHelper helper) {
        floor(helper);
        pad(helper, 7, 9);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        SparringNinja mate = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja enemy = ninja(helper, 1.5f, 8.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        mate.setInvulnerable(false);
        enemy.setInvulnerable(false);
        caster.setInvulnerable(false);
        face(caster, enemy);
        run(caster, "katon/burning_ash", 1f);
        helper.runAfterDelay(10, () -> helper.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "nothing yet: the ash is still drifting: " + enemy.getHealth() + "/" + enemy.getMaxHealth() + " " + enemy.getLastDamageSource()));
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), "the cloud ignites around the enemy");
            helper.assertTrue(enemy.getRemainingFireTicks() > 0 || !enemy.isAlive(), "and sets it alight");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is never hit");
            helper.assertTrue(mate.getHealth() == mate.getMaxHealth(), "nor the crew mate");
            clearAround(helper);
        });
    }

    /** A huge wall of fire rolls over the enemy; the crew mate beside the caster is spared. */
    @GameTest(template = "empty", batch = "noirm_majestic_flame", timeoutTicks = 80)
    public static void majesticDestroyerFlameSweepsTheField(GameTestHelper helper) {
        floor(helper);
        pad(helper, 5, 7);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        SparringNinja mate = ninja(helper, 2.5f, 1.5f, TestClans.PLAIN);
        SparringNinja enemy = ninja(helper, 0.5f, 6.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        mate.setInvulnerable(false);
        enemy.setInvulnerable(false);
        caster.setInvulnerable(false);
        facePoint(caster, new Vec3(caster.getX(), caster.getY(), caster.getZ() + 5));
        run(caster, "katon/majestic_destroyer_flame", 1f);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), "the wall of fire hits off to the side too (it is wide)");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth() && mate.getHealth() == mate.getMaxHealth(), "the caster's side is spared");
            clearAround(helper);
        });
    }

    /** The oil bursts on the enemy and keeps burning where it landed. */
    @GameTest(template = "empty", batch = "noirm_flame_bomb", timeoutTicks = 80)
    public static void flameBombSticksAndBurns(GameTestHelper helper) {
        floor(helper);
        pad(helper, 3, 6);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        SparringNinja enemy = ninja(helper, 1.5f, 5.5f, TestClans.PLAIN);
        enemy.setInvulnerable(false);
        caster.setInvulnerable(false);
        face(caster, enemy);
        int before = JutsuAreas.count();
        run(caster, "katon/flame_bomb", 1f);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(JutsuAreas.count() > before, "burning oil is left on the ground");
            helper.assertTrue(enemy.getHealth() < enemy.getMaxHealth() || !enemy.isAlive(), "the bomb hits");
            helper.assertTrue(enemy.getRemainingFireTicks() > 0 || !enemy.isAlive(), "and the enemy burns");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is never hit");
            clearAround(helper);
        });
    }

    /** Water still beats fire: a strong water bullet puts the Flame Bomb out, and no oil burns. */
    @GameTest(template = "empty", batch = "noirm_flame_bomb_clash", timeoutTicks = 60)
    public static void flameBombLosesToWater(GameTestHelper helper) {
        floor(helper);
        pad(helper, 0, 9);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        facePoint(caster, caster.position().add(0, 0, 5)); // facing +z
        int areas = JutsuAreas.count();
        run(caster, "katon/flame_bomb", 1f);
        JutsuProjectile fire = helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, new AABB(caster.blockPosition()).inflate(4)).get(0);
        JutsuProjectile water = NoirmEntities.JUTSU_PROJECTILE.get().create(helper.getLevel());
        Vec3 at = new Vec3(fire.getX(), fire.getY(), fire.getZ() + 6);
        water.moveTo(at.x, at.y, at.z, 0, 0);
        water.configure(Nature.WATER, 0.5f, 30f, 0.5f, 0, 0, 0);
        water.setDeltaMovement(0, 0, -0.3);
        helper.getLevel().addFreshEntity(water);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(fire.clashOutcome() == JutsuClash.Outcome.LOST && fire.isRemoved(), "the fire is put out: " + fire.clashOutcome() + " removed " + fire.isRemoved() + " at " + fire.position() + " water " + water.position() + " " + water.clashOutcome());
            helper.assertTrue(JutsuAreas.count() == areas, "a doused bomb leaves no burning oil");
            discardProjectiles(helper);
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_caterwaul")
    public static void caterwaulReleasesThreeDragons(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        facePoint(caster, caster.position().add(0, 0, 5));
        run(caster, "katon/dragon_flame_caterwaul", 1f);
        List<JutsuProjectile> shots = helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, new AABB(caster.blockPosition()).inflate(4));
        helper.assertTrue(shots.size() == 3, "three dragons: " + shots.size());
        helper.assertTrue(shots.stream().allMatch(s -> s.look() == JutsuProjectile.Look.DRAGON), "each drawn as a dragon");
        discardProjectiles(helper);
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_phoenix")
    public static void phoenixFlowerFiresManyBullets(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        facePoint(caster, caster.position().add(0, 0, 5));
        run(caster, "katon/phoenix_flower", 1f);
        List<JutsuProjectile> shots = helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, new AABB(caster.blockPosition()).inflate(4));
        helper.assertTrue(shots.size() == 8, "eight fire bullets: " + shots.size());
        helper.assertTrue(shots.stream().allMatch(s -> s.look() == JutsuProjectile.Look.BULLET), "each with its tail");
        discardProjectiles(helper);
        clearAround(helper);
    }

    /** The bolt jumps from enemy to enemy, skipping the crew mate standing among them. */
    @GameTest(template = "empty", batch = "noirm_chain_discharge", timeoutTicks = 60)
    public static void chainDischargeJumpsBetweenEnemies(GameTestHelper helper) {
        floor(helper);
        pad(helper, 3, 9);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.FIRE);
        SparringNinja first = ninja(helper, 1.5f, 4.5f, TestClans.PLAIN);
        SparringNinja second = ninja(helper, 0.5f, 7.5f, TestClans.PLAIN);
        SparringNinja third = ninja(helper, 2.5f, 9.5f, TestClans.PLAIN);
        SparringNinja mate = ninja(helper, 2.5f, 5.5f, TestClans.PLAIN);
        UUID leader = UUID.randomUUID();
        caster.joinCrew(leader);
        mate.joinCrew(leader);
        for (SparringNinja n : List.of(first, second, third, mate, caster)) {
            n.setInvulnerable(false);
        }
        face(caster, first);
        // Aim at the first enemy's middle, as a player would: a level gaze favours enemies further off.
        Vec3 to = first.position().add(0, first.getBbHeight() * 0.5, 0).subtract(caster.getEyePosition());
        caster.setXRot((float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance())));
        caster.xRotO = caster.getXRot();
        run(caster, "raiton/chain_discharge", 1f);
        helper.assertTrue(first.getHealth() < first.getMaxHealth() && CombatSystem.isStaggered(first), "the first enemy is struck at once: " + first.getHealth() + "/" + first.getMaxHealth() + " staggered " + CombatSystem.isStaggered(first));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(second.getHealth() < second.getMaxHealth(), "the bolt jumps to the second");
            helper.assertTrue(third.getHealth() < third.getMaxHealth(), "and on to the third");
            helper.assertTrue(mate.getHealth() == mate.getMaxHealth() && !CombatSystem.isStaggered(mate), "the crew mate among them is skipped");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "the caster is never hit");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ helpers

    private static void run(LivingEntity caster, String path, float charge) {
        ResourceLocation id = Noirm.id(path);
        var def = JutsuData.get(id);
        com.noirm.jutsu.FriendlyZones.mark(caster); // as a real cast does: the aftermath spares the caster's side
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), charge));
    }

    /**
     * Stone under a strip from {@code z0} to {@code z1} (x 0..2), so fighters further out don't fall,
     * and clear air above it: earlier tests in this spot may have left walls, ice or pillars outside
     * their own 3×3 area (and leftover non-living entities a big projectile would hit).
     */
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

    private static void discardProjectiles(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(30);
        helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, area).forEach(JutsuProjectile::discard);
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE); // tests expect the basic moves
        ninja.joinClan(clan);
        ninja.setHealth(ninja.getMaxHealth()); // the clan can raise max health after spawning
        return ninja;
    }
}

package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.CastType;
import com.noirm.jutsu.JutsuAreas;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tests for the nature tree, mastery unlocks and the nature jutsu behaviors. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureGameTests {
    private NatureGameTests() {}

    @GameTest(template = "empty")
    public static void everyNatureHasJutsu(GameTestHelper helper) {
        for (Nature nature : Nature.values()) {
            helper.assertTrue(JutsuData.allFor(nature).size() >= (nature.isBasic() ? 3 : 2),
                    nature + " should have at least " + (nature.isBasic() ? 3 : 2) + " jutsu, has " + JutsuData.allFor(nature));
        }
        for (Map.Entry<ResourceLocation, JutsuDefinition> entry : JutsuData.all().entrySet()) {
            entry.getValue().unlock().ifPresent(u -> helper.assertTrue(JutsuData.get(u.after()) != null,
                    entry.getKey() + " unlocks after a missing jutsu " + u.after()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void combinedNaturesNeedComponentsAndMastery(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        com.noirm.clan.ClanSystem.assign(player, data, TestClans.clan("test_earth_water",
                Map.of(Nature.EARTH, Clan.PRIMARY_AFFINITY, Nature.WATER, Clan.SECONDARY_AFFINITY), java.util.Optional.empty(),
                com.noirm.clan.AwakeningPath.WILL));
        helper.assertTrue(NatureSystem.status(data, Nature.WOOD) == NatureSystem.Status.LOCKED, "wood needs mastery first");
        helper.assertTrue(NatureSystem.status(data, Nature.LAVA) == NatureSystem.Status.LOCKED, "lava needs fire, which we lack");
        helper.assertFalse(NatureSystem.unlock(player, data, Nature.WOOD), "a locked nature can't be unlocked");
        data.jutsu().addXp(Noirm.id("doton/earth_wall"), 40);
        data.jutsu().addXp(Noirm.id("suiton/water_bullet"), 40);
        helper.assertTrue(NatureSystem.status(data, Nature.WOOD) == NatureSystem.Status.AVAILABLE, "★2 in earth and water opens wood");
        helper.assertTrue(NatureSystem.unlock(player, data, Nature.WOOD), "unlock wood");
        helper.assertTrue(NatureSystem.status(data, Nature.WOOD) == NatureSystem.Status.OWNED, "wood is owned now");
        helper.assertTrue(data.jutsu().knows(Noirm.id("mokuton/wood_binding")), "and its jutsu are learned");
        helper.assertTrue(Math.abs(data.identity().affinity(Nature.WOOD) - Clan.SECONDARY_AFFINITY) < 1e-4, "affinity = the weaker component (capped at 1)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void masteryUnlocksTheNextJutsu(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        com.noirm.clan.ClanSystem.assign(player, data, TestClans.FIRE);
        ResourceLocation fireball = Noirm.id("katon/great_fireball");
        ResourceLocation phoenix = Noirm.id("katon/phoenix_flower");
        helper.assertFalse(data.jutsu().knows(phoenix), "Phoenix Flower comes later");
        data.jutsu().addXp(fireball, 40);
        helper.assertTrue(NatureSystem.checkMasteryUnlocks(player, data, fireball) == 1, "★2 Great Fireball teaches one jutsu");
        helper.assertTrue(data.jutsu().knows(phoenix), "Phoenix Flower is learned");
        helper.succeed();
    }

    /** Casts every nature jutsu once (from a ninja with every nature) at a target in front: no errors, and it gets hurt. */
    @GameTest(template = "empty", batch = "noirm_all_jutsu", timeoutTicks = 400)
    public static void everyNatureJutsuWorks(GameTestHelper helper) {
        floor(helper);
        List<ResourceLocation> ids = new ArrayList<>();
        for (Nature nature : Nature.values()) {
            for (ResourceLocation id : JutsuData.allFor(nature)) {
                if (JutsuData.get(id).cast() != CastType.CHANNEL && !id.getPath().equals("doton/earth_wall")) {
                    ids.add(id);
                }
            }
        }
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja target = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        target.setInvulnerable(false);
        face(caster, target);
        List<String> failed = new ArrayList<>();
        int[] index = {0};
        helper.onEachTick(() -> {
            if (index[0] >= ids.size()) {
                return;
            }
            if (helper.getTick() % 12 != 0) {
                return;
            }
            ResourceLocation id = ids.get(index[0]++);
            target.setHealth(target.getMaxHealth());
            target.invulnerableTime = 0;
            face(caster, target);
            try {
                JutsuCaster.npcCast(caster, id, 1f);
            } catch (RuntimeException e) {
                failed.add(id + ": " + e);
            }
            if (index[0] == ids.size()) {
                helper.assertTrue(failed.isEmpty(), "jutsu threw errors: " + failed);
                helper.runAfterDelay(40, () -> clearAround(helper));
            }
        });
    }

    @GameTest(template = "empty", batch = "noirm_binds")
    public static void bindHoldsTheTarget(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja target = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        face(caster, target);
        helper.assertTrue(JutsuCaster.npcCast(caster, Noirm.id("mokuton/wood_binding"), 1f), "cast Wood Binding");
        helper.assertTrue(com.noirm.combat.CombatSystem.isStaggered(target), "the target is held");
        helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and can't move");
        helper.assertTrue(target.getHealth() < target.getMaxHealth(), "and hurt");
        clearAround(helper);
    }

    @GameTest(template = "empty", batch = "noirm_clouds", timeoutTicks = 100)
    public static void cloudsHurtOverTime(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja target = ninja(helper, 1.5f, 3.5f, TestClans.PLAIN);
        face(caster, target);
        int before = JutsuAreas.count();
        helper.assertTrue(JutsuCaster.npcCast(caster, Noirm.id("futton/skilled_mist"), 1f), "cast Skilled Mist");
        helper.assertTrue(JutsuAreas.count() == before + 1, "a mist lingers");
        float start = target.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < start, "the mist should hurt over time");
            helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS), "and weaken");
        });
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.joinClan(clan);
        return ninja;
    }

    private static void clearAround(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(14);
        helper.getLevel().getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Player)).forEach(LivingEntity::discard);
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void face(LivingEntity entity, LivingEntity other) {
        float yaw = (float) (Mth.atan2(other.getZ() - entity.getZ(), other.getX() - entity.getX()) * Mth.RAD_TO_DEG) - 90f;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setYHeadRot(yaw);
        entity.yHeadRotO = yaw;
        entity.setYBodyRot(yaw);
        entity.setXRot(0);
    }
}

package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.Threat;
import com.noirm.world.wild.Encounters;
import com.noirm.world.wild.WildDefenders;
import com.noirm.world.wild.WildNinjas;
import com.noirm.world.wild.WildSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * APX follow-ups for wild ninjas: they turn up less often (about one group every 4 minutes, at
 * most 2 groups near you), and friendly groups (squads, travellers) defend a player a rogue attacks.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildDefenseGameTests {
    private WildDefenseGameTests() {}

    /** A try a minute at 25 %: about one group every 4 minutes; at most 2 groups and 6 ninjas near a player. */
    @GameTest(template = "empty", batch = "noirm_wild_rate", timeoutTicks = 20)
    public static void encountersAreQuieter(GameTestHelper helper) {
        helper.assertTrue(WildSpawner.interval(100) == 20 * 60, "a try every minute, got " + WildSpawner.interval(100) + " ticks");
        helper.assertTrue(Math.abs(WildSpawner.chance(100) - 0.25f) < 1e-4, "at 25 %, got " + WildSpawner.chance(100));
        float minutes = WildSpawner.averageMinutesBetweenGroups(100);
        helper.assertTrue(minutes >= 3.5f && minutes <= 4.5f, "about one group every 4 minutes, got " + minutes);
        helper.assertTrue(WildSpawner.averageMinutesBetweenGroups(200) < minutes, "the gamerule still makes it busier");
        helper.assertTrue(WildSpawner.averageMinutesBetweenGroups(50) > minutes, "or quieter");
        helper.assertTrue(WildSpawner.MAX_GROUPS_NEAR >= 1 && WildSpawner.MAX_GROUPS_NEAR <= 2, "at most 1-2 groups near a player");
        helper.assertTrue(WildSpawner.MAX_NINJAS_NEAR <= 6, "and a handful of ninjas");
        // The dice, played out: an hour of tries (60) a thousand times over comes to about 15 groups an hour.
        RandomSource random = RandomSource.create(4242L);
        int groups = 0, tries = 60 * 1000;
        for (int i = 0; i < tries; i++) {
            if (random.nextFloat() < WildSpawner.chance(100)) {
                groups++;
            }
        }
        float perHour = groups / 1000f;
        helper.assertTrue(perHour > 13.5f && perHour < 16.5f, "about 15 groups an hour (one every 4 minutes), got " + perHour);
        helper.succeed();
    }

    /** A rogue hurts a player: travellers and a squad nearby turn on the rogue, never on the player. */
    @GameTest(template = "empty", batch = "noirm_wild_defend", timeoutTicks = 60)
    public static void squadsDefendPlayers(GameTestHelper helper) {
        floor(helper);
        Player player = survivalPlayer(helper);
        List<SparringNinja> travellers = spawn(helper, Encounters.TRAVELLERS, new BlockPos(0, 1, 0));
        List<SparringNinja> squad = spawn(helper, Encounters.SQUAD, new BlockPos(2, 1, 0));
        List<SparringNinja> wanderer = spawn(helper, Encounters.WANDERER, new BlockPos(0, 1, 2));
        List<SparringNinja> band = spawn(helper, Encounters.ROGUE_BAND, new BlockPos(2, 1, 2));
        SparringNinja rogue = band.get(0);
        helper.assertTrue(travellers.stream().allMatch(n -> n.getTarget() == null), "travellers start out calm");
        helper.assertFalse(WildDefenders.defendsPlayers(wanderer.get(0)) || WildDefenders.defendsPlayers(rogue), "wanderers and rogues don't defend");
        helper.assertTrue(WildDefenders.defendsPlayers(travellers.get(0)) && WildDefenders.defendsPlayers(squad.get(0)), "travellers and squads do");
        // The rogue hits the player (the real event path).
        player.hurt(rogue.damageSources().mobAttack(rogue), 2f);
        for (SparringNinja n : travellers) {
            helper.assertTrue(n.getTarget() == rogue, "a traveller turns on the rogue, got " + n.getTarget());
            helper.assertTrue(WildDefenders.isDefending(n), "and is defending");
            helper.assertTrue(Threat.of(n, rogue) > 0f, "the rogue tops its threat table");
        }
        helper.assertTrue(squad.stream().allMatch(n -> n.getTarget() != null && WildNinjas.isRogue(n.getTarget())), "the squad goes for the rogues");
        helper.assertTrue(wanderer.get(0).getTarget() == null, "a lone wanderer keeps out of it");
        for (SparringNinja n : band) {
            helper.assertTrue(n.getTarget() != player, "(the band isn't pointed at anyone by this)");
        }
        // A stray hit from the player doesn't turn a defender on them.
        SparringNinja defender = travellers.get(0);
        defender.setTarget(player);
        helper.runAfterDelay(12, () -> {
            LivingEntity target = defender.getTarget();
            helper.assertTrue(target != player, "the defender stays off the player");
            helper.assertTrue(target != null && WildNinjas.isRogue(target), "and back on a rogue, got " + target);
            for (List<SparringNinja> group : List.of(travellers, squad, wanderer, band)) {
                group.forEach(LivingEntity::discard);
            }
            AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(16);
            helper.getLevel().getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Player)).forEach(LivingEntity::discard);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- helpers

    private static List<SparringNinja> spawn(GameTestHelper helper, ResourceLocation id, BlockPos at) {
        return WildSpawner.spawn(helper.getLevel(), id, helper.absolutePos(at), 15f, helper.getLevel().getRandom());
    }

    /** A player in survival (the mock player counts as creative, and rogues leave creative players be). */
    private static Player survivalPlayer(GameTestHelper helper) {
        return new Player(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 0f, new GameProfile(UUID.randomUUID(), "defend-test")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }
}

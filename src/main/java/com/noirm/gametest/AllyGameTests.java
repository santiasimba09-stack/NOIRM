package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.mojang.authlib.GameProfile;
import com.noirm.Noirm;
import com.noirm.ally.AllyFood;
import com.noirm.clan.AwakeningPath;
import com.noirm.clan.Clan;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.Threat;
import com.noirm.registry.NoirmEntities;
import com.noirm.registry.NoirmItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Allies and NPC fighting: weapons and their enchantments, shields, totems, feeding, who a
 * rogue fights (threat), jutsu in fights and archers. Fighters swing and throw things, so every
 * test has its own batch.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AllyGameTests {
    private static final float NINJA_HEALTH = 40f;
    /** No natures at all: only the universal starters (Substitution, Body Flicker). */
    private static final Clan NO_NATURE = TestClans.clan("test_no_nature", Map.of(), Optional.empty(), AwakeningPath.WILL);

    private AllyGameTests() {}

    // ------------------------------------------------------------ held items

    /** The owner's netherite sword with Sharpness and Fire Aspect: real damage, enchantments, wear. */
    @GameTest(template = "empty", batch = "noirm_ally_sword", timeoutTicks = 60)
    public static void swordsHitHarderAndTheirEnchantmentsWork(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 5);
        sword.enchant(Enchantments.FIRE_ASPECT, 2);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, sword);
        face(attacker, target);
        // A tick for the sword's attack damage to apply, then a jab.
        helper.runAfterDelay(2, () -> {
            face(attacker, target);
            CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        });
        // Jab 3 + netherite's 7 × share × the jab's weight + Sharpness V's 3.
        float expected = 3f + 7f * CombatSystem.WEAPON_DAMAGE_SHARE
                * Math.max(CombatSystem.WEAPON_WEIGHT_MIN, 3f / CombatSystem.WEAPON_REFERENCE_DAMAGE) + 3f;
        helper.onEachTick(() -> {
            if (target.getHealth() >= NINJA_HEALTH) {
                return;
            }
            float dealt = NINJA_HEALTH - target.getHealth();
            helper.assertTrue(Math.abs(dealt - expected) < 0.05f, "the sword jab should deal " + expected + ", dealt " + dealt);
            helper.assertTrue(target.isOnFire(), "Fire Aspect sets the target alight");
            ItemStack held = attacker.getMainHandItem();
            helper.assertTrue(held.getDamageValue() == 1, "the sword wears by 1, got " + held.getDamageValue());
            target.clearFire();
            clearAround(helper);
        });
    }

    /** Guarding with a shield in the off hand: the shield takes the hit (and wears). */
    @GameTest(template = "empty", batch = "noirm_ally_shield", timeoutTicks = 60)
    public static void shieldsBlockWhileGuarding(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        face(attacker, target);
        face(target, attacker);
        CombatSystem.setGuard(target, true);
        // Past the parry window, so it's a guard, then a heavy kick (8 damage).
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 3, () -> CombatSystem.requestAttack(attacker, AttackInput.HEAVY));
        helper.runAfterDelay(CombatSystem.PARRY_WINDOW + 40, () -> {
            helper.assertTrue(target.getOffhandItem().getDamageValue() > 0, "the shield took the hit");
            helper.assertTrue(target.getHealth() == NINJA_HEALTH, "and nothing got through, health " + target.getHealth());
            clearAround(helper);
        });
    }

    /** A totem of undying in hand works for ninjas like it does for players. */
    @GameTest(template = "empty", batch = "noirm_ally_totem", timeoutTicks = 20)
    public static void totemsSaveNinjas(GameTestHelper helper) {
        SparringNinja holder = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja enemy = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        holder.setInvulnerable(false);
        holder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        holder.hurt(holder.damageSources().mobAttack(enemy), 500f);
        helper.assertTrue(holder.isAlive(), "the totem saved them");
        helper.assertTrue(holder.getMainHandItem().isEmpty(), "and was used up");
        clearAround(helper);
    }

    // ------------------------------------------------------------ feeding

    @GameTest(template = "empty", batch = "noirm_ally_feed", timeoutTicks = 100)
    public static void feedingAllies(GameTestHelper helper) {
        Player player = realPlayer(helper);
        SparringNinja ally = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        ally.setInvulnerable(false);
        ally.joinCrew(player.getUUID());
        ally.setHealth(10f);
        float respect = ally.respect();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 2));
        ally.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ally.getHealth() > 15f, "a golden apple heals, health " + ally.getHealth());
        helper.assertTrue(ally.hasEffect(MobEffects.REGENERATION) && ally.hasEffect(MobEffects.ABSORPTION), "with its regeneration and absorption");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "and it's used up");
        helper.assertTrue(ally.respect() > respect, "being looked after earns respect");
        ally.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "they need a moment before the next bite");
        // Ramen refills chakra, and the bowl comes back.
        ally.setChakra(0f);
        helper.runAfterDelay(AllyFood.COOLDOWN_TICKS + 2, () -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NoirmItems.RAMEN.get()));
            ally.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(ally.chakra() >= 40f, "ramen restores chakra, got " + ally.chakra());
            helper.assertTrue(player.getMainHandItem().is(Items.BOWL), "and gives the bowl back");
            // Without food in hand, the same click doesn't feed.
            helper.assertFalse(AllyFood.isFood(player.getMainHandItem(), ally), "a bowl isn't food");
            clearAround(helper);
        });
    }

    @GameTest(template = "empty", batch = "noirm_ally_self_feed", timeoutTicks = 40)
    public static void hurtAlliesEatFromTheirOffHand(GameTestHelper helper) {
        SparringNinja ally = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        ally.setInvulnerable(false);
        ally.joinCrew(UUID.randomUUID());
        ally.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BREAD, 3));
        ally.setHealth(NINJA_HEALTH * 0.2f);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(ally.getOffhandItem().getCount() == 2, "badly hurt, they eat one bread, got " + ally.getOffhandItem().getCount());
            helper.assertTrue(ally.getHealth() > NINJA_HEALTH * 0.2f + 5f, "and it heals them");
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ threat

    /**
     * The owner's playtest: you hit a rogue to point your crew at it, your crew mate fights it,
     * and the rogue should turn on the one hurting it instead of chasing you.
     */
    @GameTest(template = "empty", batch = "noirm_ally_threat", timeoutTicks = 300)
    public static void roguesTurnOnTheAllyFightingThem(GameTestHelper helper) {
        // A wider floor than usual: fighters knock each other around.
        List<BlockPos> ground = new java.util.ArrayList<>();
        for (int x = -3; x <= 5; x++) {
            for (int z = -3; z <= 5; z++) {
                ground.add(new BlockPos(x, 0, z));
            }
        }
        ground.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        Player player = realPlayer(helper);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, -1.5)));
        SparringNinja rogue = fighter(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja ally = fighter(helper, 1.5f, 2.5f, TestClans.PLAIN);
        rogue.setInvulnerable(false);
        ally.setInvulnerable(false);
        ally.joinCrew(player.getUUID());
        // The player's one hit (a few ticks in: vanilla ignores hits stamped at the mob's tick 0),
        // and the rogue goes after the player.
        helper.runAfterDelay(3, () -> rogue.hurt(rogue.damageSources().playerAttack(player), 2f));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(rogue.getTarget() == player, "first, the rogue turns on the player who hit it");
            ally.setTarget(rogue); // the crew mate joins in
        });
        helper.onEachTick(() -> {
            if (helper.getTick() > 9 && rogue.getTarget() == ally) {
                helper.assertTrue(Threat.of(rogue, ally) > 0f, "because the ally built up threat");
                ground.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
                clearAround(helper);
            }
        });
    }

    // ------------------------------------------------------------ jutsu and archers

    /** A ninja in a fight uses its jutsu (here: Lightning Armor to open, Chidori Current up close). */
    @GameTest(template = "empty", batch = "noirm_ally_jutsu", timeoutTicks = 200)
    public static void ninjasUseJutsuInFights(GameTestHelper helper) {
        floor(helper);
        SparringNinja caster = fighter(helper, 1.5f, 0.5f, TestClans.FIRE);
        SparringNinja target = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        target.setInvulnerable(true);
        caster.setTarget(target);
        float full = caster.maxChakra();
        helper.onEachTick(() -> {
            if (caster.jutsuCasts() > 0) {
                helper.assertTrue(caster.chakra() < full, "and paid chakra for it");
                com.noirm.jutsu.ModeSystem.end(caster);
                clearAround(helper);
            }
        });
    }

    /** A bow in hand: they keep their distance and shoot. */
    @GameTest(template = "empty", batch = "noirm_ally_archer", timeoutTicks = 200)
    public static void archersShoot(GameTestHelper helper) {
        List<BlockPos> lane = new java.util.ArrayList<>();
        for (int x = -6; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                lane.add(new BlockPos(x, 0, z));
            }
        }
        lane.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        SparringNinja archer = fighter(helper, 1.5f, 1.5f, NO_NATURE);
        SparringNinja target = ninja(helper, -5.5f, 1.5f, TestClans.PLAIN);
        target.setInvulnerable(false);
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        archer.setTarget(target);
        helper.onEachTick(() -> {
            if (target.getHealth() < NINJA_HEALTH) {
                helper.assertTrue(archer.getMainHandItem().getDamageValue() > 0, "the bow wears");
                AABB around = new AABB(helper.absolutePos(new BlockPos(-2, 1, 1))).inflate(16);
                helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around).forEach(AbstractArrow::discard);
                lane.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
                clearAround(helper);
            }
        });
    }

    // ------------------------------------------------------------ helpers

    /** A ninja with AI off. */
    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        setUp(ninja, clan);
        return ninja;
    }

    /** A ninja with its AI on (it fights its target). */
    private static SparringNinja fighter(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawn(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        setUp(ninja, clan);
        return ninja;
    }

    private static void setUp(SparringNinja ninja, Clan clan) {
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.sealBeast(0); // never a surprise jinchūriki
    }

    /**
     * A survival-mode stand-in player (the framework's mock player is creative, and mobs don't
     * target creative players). It isn't in the world: only what's tested touches it.
     */
    private static Player realPlayer(GameTestHelper helper) {
        return new Player(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), 0f, new GameProfile(UUID.randomUUID(), "test-player")) {
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

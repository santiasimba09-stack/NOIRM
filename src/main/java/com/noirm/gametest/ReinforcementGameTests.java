package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.ChakraArmour;
import com.noirm.combat.ChakraReinforcement;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.NinjaFire;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import com.noirm.training.Durability;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Chakra reinforcement, ninja armour, ninja fire and chakra durability (combat.ChakraReinforcement,
 * NinjaArmour, NinjaFire, ChakraArmour), and Water Release's S-rank, the Leviathan Maelstrom.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReinforcementGameTests {
    private static final float NINJA_HEALTH = 40f;

    private ReinforcementGameTests() {}

    // ------------------------------------------------------------ armour

    /** A ninja attack meets a quarter of vanilla armour; a zombie meets all of it. Enchantments too. */
    @GameTest(template = "empty", batch = "noirm_cr_armour", timeoutTicks = 40)
    public static void ninjaAttacksMostlyIgnoreArmourButMobsDont(GameTestHelper helper) {
        floor(helper);
        SparringNinja victim = ninja(helper, 1.5f, 1.5f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 0.5f, 1f, 0.5f);
        Player rival = helper.makeMockPlayer();
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            victim.setItemSlot(slot, new ItemStack(switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                default -> Items.DIAMOND_BOOTS;
            }));
        }
        helper.runAfterDelay(2, () -> { // the armour's attributes apply on the next tick
            helper.assertTrue(victim.getArmorValue() == 20, "full diamond: 20 armour, got " + victim.getArmorValue());
            // Vanilla: 10 → 3 (diamond stops 70%).
            float zombieHit = lose(victim, () -> victim.hurt(victim.damageSources().mobAttack(zombie), 10f));
            near(helper, 3.0f, zombieHit, "a zombie meets the full armour");
            // A ninja's strike: the armour keeps 25% of its 7: 10 − 1.75.
            float strike = lose(victim, () -> ChakraReinforcement.hurt(victim, victim.damageSources().playerAttack(rival), 10f,
                    ChakraReinforcement.Kind.MELEE));
            near(helper, 8.25f, strike, "a ninja strike mostly ignores it");
            // A jutsu skips armour in vanilla; a ninja's meets the same quarter.
            float jutsu = lose(victim, () -> ChakraReinforcement.hurt(victim, victim.damageSources().indirectMagic(rival, rival), 10f,
                    ChakraReinforcement.Kind.JUTSU));
            near(helper, 8.25f, jutsu, "a jutsu meets the same quarter of the armour");
            // Protection IV on every piece: vanilla ~1.08, a ninja's ~6.93.
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                victim.getItemBySlot(slot).enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
            }
            float zombieEnchanted = lose(victim, () -> victim.hurt(victim.damageSources().mobAttack(zombie), 10f));
            near(helper, 1.08f, zombieEnchanted, "Protection IV works in full against a zombie");
            float strikeEnchanted = lose(victim, () -> ChakraReinforcement.hurt(victim, victim.damageSources().playerAttack(rival), 10f,
                    ChakraReinforcement.Kind.MELEE));
            near(helper, 6.93f, strikeEnchanted, "and at a quarter against a ninja");
            helper.assertTrue(ChakraReinforcement.armourShare() == ChakraReinforcement.ARMOUR_SHARE, "25% by default");
            zombie.discard();
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ reinforcement: the numbers

    @GameTest(template = "empty")
    public static void reinforcementScalesWithChakraAndControl(GameTestHelper helper) {
        near(helper, 0f, ChakraReinforcement.damageBonus(10f), "a beginner has none");
        near(helper, 0f, ChakraReinforcement.reduction(10f), "a beginner has none");
        near(helper, 0.111f, ChakraReinforcement.damageBonus(20f), "+11% at 20");
        near(helper, 0.264f, ChakraReinforcement.damageBonus(40f), "+26% at 40");
        near(helper, 0.413f, ChakraReinforcement.damageBonus(80f), "+41% at 80");
        near(helper, 0.185f, ChakraReinforcement.reduction(40f), "−18% at 40");
        near(helper, 0.289f, ChakraReinforcement.reduction(80f), "−29% at 80");
        helper.assertTrue(ChakraReinforcement.damageBonus(ShinobiData.MAX_STAT) <= ChakraReinforcement.MAX_DAMAGE_BONUS + 1e-4f, "capped at +50%");
        helper.assertTrue(ChakraReinforcement.reduction(ShinobiData.MAX_STAT) <= ChakraReinforcement.MAX_REDUCTION + 1e-4f, "capped at −35%");
        float last = 0f;
        for (int stat = 10; stat <= 400; stat += 5) {
            float b = ChakraReinforcement.damageBonus(stat);
            helper.assertTrue(b >= last, "never goes down at " + stat);
            last = b;
        }
        // Both stats count: Reserves and CTL.
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        data.setReserves(50f);
        near(helper, 30f, ChakraReinforcement.chakraStat(player), "Reserves 50 + CTL 10 → 30");
        data.setControl(70f);
        near(helper, 60f, ChakraReinforcement.chakraStat(player), "Reserves 50 + CTL 70 → 60");
        // CTL makes it cheaper.
        near(helper, 1f, ChakraReinforcement.costFactor(10f), "full price at CTL 10");
        near(helper, 0.5f, ChakraReinforcement.costFactor(40f), "half at CTL 40");
        near(helper, ChakraReinforcement.MIN_COST_FACTOR, ChakraReinforcement.costFactor(5000f), "never free");
        helper.succeed();
    }

    /** A real strike from a ninja with chakra: harder, and it pays for it. Without chakra: a plain hit. */
    @GameTest(template = "empty", batch = "noirm_cr_strike", timeoutTicks = 80)
    public static void npcReinforcedStrikeHitsHarderAndCostsChakra(GameTestHelper helper) {
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f);
        SparringNinja target = ninja(helper, 1.5f, 2.0f);
        attacker.setStat(Stat.RESERVES, 60f); // CTL 10 + 30% of 50 = 25; chakra stat 42.5
        float control = attacker.combatControl();
        near(helper, 25f, control, "an NPC's CTL comes from its reserves");
        float bonus = ChakraReinforcement.damageBonus(42.5f);
        float before = attacker.chakra();
        face(attacker, target);
        CombatSystem.requestAttack(attacker, AttackInput.LIGHT);
        helper.runAfterDelay(14, () -> {
            near(helper, NINJA_HEALTH - 3f * (1f + bonus), target.getHealth(), "the jab hits " + Math.round(bonus * 100) + "% harder");
            near(helper, before - ChakraReinforcement.STRIKE_COST * ChakraReinforcement.costFactor(control), attacker.chakra(),
                    "and costs a little chakra");
            // Out of chakra: no reinforcement, and nothing spent. (Checked on the spot: a ninja's chakra
            // comes back a little every tick, so a real jab a moment later would be reinforced again.)
            attacker.setChakra(0f);
            near(helper, 1f, ChakraReinforcement.strike(attacker, target, null), "with no chakra the strike is plain");
            near(helper, 0f, attacker.chakra(), "and costs nothing");
            clearAround(helper);
        });
    }

    /** Taking a ninja attack: reinforcement softens it and costs chakra; without chakra it doesn't; vanilla mobs never. */
    @GameTest(template = "empty", batch = "noirm_cr_defend", timeoutTicks = 40)
    public static void reinforcementSoftensNinjaAttacksAndNeedsChakra(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        Player rival = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        data.setReserves(60f);
        data.setControl(60f);
        data.setMaxChakra(400f);
        data.setChakra(400f);
        float rein = ChakraReinforcement.reduction(60f);
        float lost = lose(player, () -> ChakraReinforcement.hurt(player, player.damageSources().playerAttack(rival), 10f, ChakraReinforcement.Kind.MELEE));
        near(helper, 10f * (1f - rein), lost, "reinforcement takes " + Math.round(rein * 100) + "% off");
        helper.assertTrue(data.getChakra() < 400f, "and it cost chakra");
        // A plain hit (not a ninja attack) isn't reinforced.
        float plain = lose(player, () -> player.hurt(player.damageSources().playerAttack(rival), 10f));
        near(helper, 10f, plain, "a vanilla hit isn't reinforced");
        // No chakra: no reinforcement.
        data.setChakra(0f);
        float dry = lose(player, () -> ChakraReinforcement.hurt(player, player.damageSources().playerAttack(rival), 10f, ChakraReinforcement.Kind.MELEE));
        near(helper, 10f, dry, "out of chakra, the hit lands in full");
        // NPC ninjas reinforce from their own stats and chakra.
        SparringNinja ninja = ninja(helper, 1.5f, 1.5f);
        ninja.setStat(Stat.RESERVES, 60f);
        float npcChakra = ninja.chakra();
        float npcLost = lose(ninja, () -> ChakraReinforcement.hurt(ninja, ninja.damageSources().playerAttack(rival), 10f, ChakraReinforcement.Kind.MELEE));
        float npcRein = ChakraReinforcement.reduction(ChakraReinforcement.chakraStat(ninja));
        float npcDur = Durability.negation(Durability.of(ninja));
        near(helper, 10f * (1f - ChakraReinforcement.combined(npcDur, npcRein)), npcLost, "an NPC reinforces too");
        helper.assertTrue(ninja.chakra() < npcChakra, "from its own chakra");
        player.setHealth(20f);
        clearAround(helper);
    }

    /** Durability and reinforcement stack, but never past 70%. */
    @GameTest(template = "empty", batch = "noirm_cr_cap", timeoutTicks = 40)
    public static void durabilityAndReinforcementShareACap(GameTestHelper helper) {
        floor(helper);
        near(helper, 0.70f, ChakraReinforcement.combined(0.6f, 0.35f), "60% and 35% → capped at 70%");
        near(helper, 0.40f, ChakraReinforcement.combined(0.2f, 0.25f), "20% and 25% → 40%");
        near(helper, 0.5f, ChakraReinforcement.combined(0.5f, 0f), "no reinforcement: DUR alone");
        Player player = helper.makeMockPlayer();
        Player rival = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        data.setDurability(ShinobiData.MAX_STAT);
        data.setReserves(1000f);
        data.setControl(1000f);
        data.setMaxChakra(5000f);
        data.setChakra(5000f);
        float ninjaHit = lose(player, () -> ChakraReinforcement.hurt(player, player.damageSources().playerAttack(rival), 10f, ChakraReinforcement.Kind.MELEE));
        near(helper, 10f * (1f - ChakraReinforcement.COMBINED_CAP), ninjaHit, "at most 70% off a ninja attack");
        float plainHit = lose(player, () -> player.hurt(player.damageSources().generic(), 10f));
        near(helper, 10f * (1f - Durability.negation(ShinobiData.MAX_STAT)), plainHit, "DUR alone (60%) for everything else");
        player.setHealth(20f);
        clearAround(helper);
    }

    // ------------------------------------------------------------ ninja fire

    /**
     * A fresh player caught square by a C-class rogue's fully charged Great Fireball, then burning and
     * standing in the flames: they lose at most 35% of their health in any second, and they're alive
     * four seconds later. The same fireball on a ninja isn't capped (fire stays dangerous).
     */
    @GameTest(template = "empty", batch = "noirm_cr_fire", timeoutTicks = 120)
    public static void beginnerSurvivesARoguesFireLongEnoughToReact(GameTestHelper helper) {
        floor(helper);
        NinjaFire.clear();
        SparringNinja rogue = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 0.5f);
        rogue.joinClan(TestClans.FIRE);
        rogue.setCombatStats(16f, 16f); // a lone C-class rogue (power ~17)
        SparringNinja other = ninja(helper, 0.5f, 2.5f);
        Player player = helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new Vec3(1.5, 1, 2.5)));
        ResourceLocation id = Noirm.id("katon/great_fireball");
        JutsuDefinition def = JutsuData.get(id);
        float damage = (def.params().damage() + def.params().chargeDamage()) * JutsuCaster.power(rogue, id, def);
        helper.assertTrue(damage > 20f, "a full fireball would one-shot a beginner on its own: " + damage);
        float cap = NinjaFire.CAP_PER_SECOND * player.getMaxHealth();
        float[] lastSecond = {player.getHealth()};
        int[] tick = {0};
        int[] burnTicks = {0};
        JutsuEffects.hit(rogue, player, damage, 0f, 0f, def.params().fireSeconds(), Vec3.ZERO);
        JutsuEffects.hit(rogue, other, damage, 0f, 0f, def.params().fireSeconds(), Vec3.ZERO);
        near(helper, Math.max(0f, NINJA_HEALTH - damage * (1f - Durability.negation(Durability.of(other)))), other.getHealth(),
                "a ninja takes the whole fireball");
        helper.assertTrue(20f - player.getHealth() <= cap + 0.01f, "the hit alone is capped: lost " + (20f - player.getHealth()));
        int burn = player.getRemainingFireTicks() / 20;
        helper.assertTrue(burn <= Math.round(def.params().fireSeconds() * NinjaFire.NPC_BURN_SCALE) && burn >= NinjaFire.MIN_BURN_SECONDS,
                "an NPC's fire burns a player shorter: " + burn + " s");
        helper.onEachTick(() -> {
            tick[0]++;
            // Vanilla burning (1 a second while alight) and the grass burning under them (every half second, first 2 s).
            if (player.getRemainingFireTicks() > 0) {
                burnTicks[0]++;
                if (player.getRemainingFireTicks() % 20 == 0) {
                    player.invulnerableTime = 0; // (a mock player doesn't tick its invulnerability down)
                    player.hurt(player.damageSources().onFire(), 1f);
                }
                player.setRemainingFireTicks(player.getRemainingFireTicks() - 1);
            }
            if (tick[0] <= 40 && tick[0] % 10 == 0) {
                player.invulnerableTime = 0;
                player.hurt(player.damageSources().inFire(), 1f);
            }
            if (tick[0] % 20 == 0) {
                // (+1: the test's seconds and the ceiling's may be a tick apart)
                helper.assertTrue(lastSecond[0] - player.getHealth() <= cap + 1.01f,
                        "at most 35% a second: lost " + (lastSecond[0] - player.getHealth()) + " in second " + tick[0] / 20);
                lastSecond[0] = player.getHealth();
            }
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(player.isAlive() && player.getHealth() > 0f, "alive four seconds later, health " + player.getHealth());
            helper.assertTrue(burnTicks[0] <= 4 * 20 + 1, "the burn is over in 4 s: " + burnTicks[0]);
            player.setHealth(20f);
            NinjaFire.clear();
            clearAround(helper);
        });
    }

    // ------------------------------------------------------------ chakra durability

    @GameTest(template = "empty", batch = "noirm_cr_armour_pool", timeoutTicks = 40)
    public static void chakraDurabilityTakesWearFirstAndRefills(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElseThrow();
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        ChakraArmour.refill(player);
        helper.assertTrue(ChakraArmour.pool(chest) == 0, "a beginner's armour holds no chakra");
        data.setReserves(40f);
        data.setControl(40f);
        data.setMaxChakra(300f);
        data.setChakra(300f);
        int size = ChakraArmour.poolSize(player, chest);
        helper.assertTrue(size == Math.round(528 * ChakraArmour.POOL_SHARE * ChakraReinforcement.strength(40f)), "pool size: " + size);
        for (int i = 0; i < 40; i++) {
            ChakraArmour.refill(player);
        }
        helper.assertTrue(ChakraArmour.pool(chest) == size && ChakraArmour.poolMax(chest) == size, "full after ~25 refills: " + ChakraArmour.pool(chest));
        helper.assertTrue(data.getChakra() < 300f && data.getChakra() > 290f, "filling it cost a little chakra: " + data.getChakra());
        // Wear comes out of the pool first.
        chest.hurtAndBreak(30, player, p -> { });
        helper.assertTrue(chest.getDamageValue() == 0, "the item didn't wear");
        helper.assertTrue(ChakraArmour.pool(chest) == size - 30, "the pool took it");
        chest.hurtAndBreak(size, player, p -> { });
        helper.assertTrue(ChakraArmour.pool(chest) == 0 && chest.getDamageValue() == 30, "the rest wears the item: " + chest.getDamageValue());
        // It refills, and only while worn and with chakra.
        ChakraArmour.refill(player);
        helper.assertTrue(ChakraArmour.pool(chest) > 0, "refilling");
        data.setChakra(0f);
        int now = ChakraArmour.pool(chest);
        ChakraArmour.refill(player);
        helper.assertTrue(ChakraArmour.pool(chest) == now, "no chakra, no refill");
        // Saved with the item.
        ItemStack loaded = ItemStack.of(chest.save(new CompoundTag()));
        helper.assertTrue(ChakraArmour.pool(loaded) == now && ChakraArmour.poolMax(loaded) == size, "it survives save and load");
        // A piece not worn isn't touched by the pool.
        ItemStack loose = loaded.copy();
        loose.hurtAndBreak(5, player, p -> { });
        helper.assertTrue(loose.getDamageValue() == 35, "a loose piece wears normally");
        // NPC ninjas fill theirs from their own stats.
        SparringNinja ninja = ninja(helper, 1.5f, 1.5f);
        ninja.setStat(Stat.RESERVES, 60f);
        ItemStack npcChest = new ItemStack(Items.IRON_CHESTPLATE);
        ninja.setItemSlot(EquipmentSlot.CHEST, npcChest);
        ChakraArmour.refill(ninja);
        helper.assertTrue(ChakraArmour.pool(npcChest) > 0, "an NPC's armour fills too");
        clearAround(helper);
    }

    // ------------------------------------------------------------ Water S-rank: the Leviathan Maelstrom

    private static final String MAELSTROM = "suiton/leviathan_maelstrom";

    @GameTest(template = "empty")
    public static void leviathanMaelstromLoads(GameTestHelper helper) {
        ResourceLocation id = Noirm.id(MAELSTROM);
        JutsuDefinition def = JutsuData.get(id);
        helper.assertTrue(def != null, "the jutsu loads");
        helper.assertTrue("S".equals(def.rank()), "S rank");
        helper.assertTrue(def.nature().map(n -> n == com.noirm.clan.Nature.WATER).orElse(false), "Water");
        helper.assertTrue(JutsuBehaviors.exists(def.behavior()), "its behaviour exists");
        helper.assertTrue(JutsuSeals.get(id) != null, "it has hand seals");
        helper.assertTrue(def.unlock().map(u -> u.after().equals(Noirm.id("suiton/great_waterfall"))).orElse(false),
                "it comes by mastery from the Great Waterfall");
        helper.assertTrue(com.noirm.nature.JutsuLearning.RESERVES_FOR_RANK[5] == 40f, "and waits for Reserves 40 like every S");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "noirm_cr_maelstrom", timeoutTicks = 160)
    public static void leviathanMaelstromHoldsAndCrushes(GameTestHelper helper) {
        openFloor(helper, -4, -3, 7, 12, 8);
        clearStrays(helper, 16);
        SparringNinja caster = ninja(helper, 1.5f, 0.5f);
        caster.joinClan(TestClans.EVERY_NATURE);
        SparringNinja enemy = ninja(helper, 1.5f, 7.5f);
        enemy.setInvulnerable(false);
        caster.setInvulnerable(false);
        face(caster, enemy);
        ResourceLocation id = Noirm.id(MAELSTROM);
        JutsuDefinition def = JutsuData.get(id);
        com.noirm.jutsu.FriendlyZones.mark(caster);
        JutsuBehaviors.get(def.behavior()).cast(new JutsuContext(caster, id, def, JutsuCaster.power(caster, id, def), 1f));
        Vec3 start = enemy.position();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(enemy.getHealth() < NINJA_HEALTH, "the whirlpool and the prison hurt: " + enemy.getHealth());
            helper.assertTrue(com.noirm.combat.CombatStates.get(enemy).phase() == com.noirm.combat.CombatPhase.STAGGER
                    || !enemy.isAlive(), "the prison holds them");
        });
        float[] beforeCrash = {0};
        helper.runAfterDelay(72, () -> beforeCrash[0] = enemy.getHealth());
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(!enemy.isAlive() || enemy.getHealth() < beforeCrash[0], "the leviathan crashes down on them");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "never on its caster");
            helper.assertTrue(!enemy.isAlive() || enemy.position().distanceTo(start) < 12, "and they stay near the arena");
            // NPCs cast it too (their seals start).
            caster.setStat(Stat.RESERVES, 120f);
            helper.assertTrue(JutsuCaster.npcCast(caster, id, 1f), "an NPC ninja can cast it");
            JutsuCaster.cancel(caster);
            AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(30);
            helper.getLevel().getEntitiesOfClass(JutsuProjectile.class, area).forEach(JutsuProjectile::discard);
            clearStrays(helper, 16);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(com.noirm.combat.CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setHealth(ninja.getMaxHealth());
        return ninja;
    }

    /** Health lost to {@code hit} (invulnerability reset first, health topped up after). */
    private static float lose(LivingEntity victim, Runnable hit) {
        victim.invulnerableTime = 0;
        float before = victim.getHealth();
        hit.run();
        float lost = before - victim.getHealth();
        victim.setHealth(victim.getMaxHealth());
        victim.invulnerableTime = 0;
        return lost;
    }

    private static void near(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) <= 0.02f + Math.abs(expected) * 0.005f, what + ": expected " + expected + ", got " + actual);
    }
}

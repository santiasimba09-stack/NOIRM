package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatMove;
import com.noirm.combat.CombatStyle;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.registry.NoirmEntities;
import com.noirm.weapon.Moveset;
import com.noirm.weapon.NinjaWeapons;
import com.noirm.weapon.ThrowStats;
import com.noirm.weapon.Throwing;
import com.noirm.weapon.ThrownWeapon;
import com.noirm.weapon.WeaponMoves;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ninja weapons: throwing, sticking, picking up, fans, movesets, explosive tags, recipes. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeaponGameTests {
    private WeaponGameTests() {}

    /** A ninja's kunai hurts what it hits and drops (its copies can't be picked up); senbon numb. */
    @GameTest(template = "empty", batch = "noirm_weapon_hit", timeoutTicks = 80)
    public static void thrownKunaiHitsAndSenbonNumb(GameTestHelper helper) {
        floor(helper);
        SparringNinja thrower = ninja(helper, 1.5f, 0.3f);
        SparringNinja target = ninja(helper, 1.5f, 2.7f);
        target.setInvulnerable(false);
        face(thrower, target);
        ThrownWeapon kunai = Throwing.npcThrow(thrower, target, new ItemStack(NinjaWeapons.KUNAI.get()), false);
        helper.assertTrue(kunai != null && kunai.pickup == AbstractArrow.Pickup.DISALLOWED, "a ninja's thrown copy can't be picked up");
        helper.runAfterDelay(8, () -> {
            float lost = target.getMaxHealth() - target.getHealth();
            helper.assertTrue(lost >= 4f, "the kunai hits for about 5, lost " + lost);
            helper.assertTrue(kunai.hasDealtDamage(), "and glances off after the hit");
        });
        helper.runAfterDelay(30, () -> Throwing.npcThrow(thrower, target, new ItemStack(NinjaWeapons.SENBON.get()), false));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "senbon numb (slowness)");
            clearWeapons(helper);
            clearAround(helper);
        });
    }

    /** A player's kunai sticks in a wall like an arrow and can be picked back up. */
    @GameTest(template = "empty", batch = "noirm_weapon_stick", timeoutTicks = 80)
    public static void kunaiSticksAndIsPickedUp(GameTestHelper helper) {
        floor(helper);
        for (int x = 0; x < 3; x++) {
            for (int y = 1; y < 4; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.OAK_PLANKS);
            }
        }
        Player player = helper.makeMockPlayer();
        ItemStack stack = new ItemStack(NinjaWeapons.KUNAI.get());
        ThrownWeapon kunai = ThrownWeapon.thrownBy(player, stack);
        kunai.setPos(helper.absoluteVec(new Vec3(1.5, 1.6, 0.5)));
        kunai.shoot(0, 0, 1, ThrowStats.KUNAI.speed(), 0f);
        helper.getLevel().addFreshEntity(kunai);
        helper.assertTrue(kunai.pickup == AbstractArrow.Pickup.ALLOWED, "a survival player's kunai can be picked up");
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(kunai.isStuck(), "the kunai sticks in the planks");
            helper.assertTrue(kunai.isAlive(), "and stays there");
            int before = player.getInventory().countItem(NinjaWeapons.KUNAI.get());
            kunai.playerTouch(player);
            helper.assertTrue(player.getInventory().countItem(NinjaWeapons.KUNAI.get()) == before + 1, "walking over it picks it up");
            helper.assertFalse(kunai.isAlive(), "and it's gone from the wall");
            clearWeapons(helper);
            helper.succeed();
        });
    }

    /** Sneak + throw with shuriken: three stars in a fan, three used up, a cooldown. */
    @GameTest(template = "empty", batch = "noirm_weapon_fan", timeoutTicks = 40)
    public static void shurikenFan(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.setPos(at.x, at.y, at.z);
        player.setYRot(0);
        player.setXRot(0);
        ItemStack stack = new ItemStack(NinjaWeapons.SHURIKEN.get(), 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        int thrown = Throwing.playerThrow(player, InteractionHand.MAIN_HAND, stack, NinjaWeapons.SHURIKEN.get(), true);
        helper.assertTrue(thrown == 3, "a fan of three, got " + thrown);
        helper.assertTrue(stack.getCount() == 2, "three used up, " + stack.getCount() + " left");
        helper.assertTrue(player.getCooldowns().isOnCooldown(NinjaWeapons.SHURIKEN.get()), "then a cooldown");
        List<ThrownWeapon> stars = weapons(helper);
        helper.assertTrue(stars.size() == 3, "three stars in the air, got " + stars.size());
        Set<Integer> headings = new HashSet<>();
        for (ThrownWeapon star : stars) {
            Vec3 v = star.getDeltaMovement();
            headings.add(Math.round((float) Math.toDegrees(Math.atan2(v.x, v.z))));
            helper.assertTrue(star.getItem().is(NinjaWeapons.SHURIKEN.get()) && star.stats().flat(), "shuriken fly flat");
        }
        helper.assertTrue(headings.size() == 3, "spread in three directions: " + headings);
        clearWeapons(helper);
        helper.succeed();
    }

    /** Weapons in hand bring their movesets, for players and ninjas; their moves all exist. */
    @GameTest(template = "empty", batch = "noirm_weapon_moves")
    public static void weaponMovesetsWhenHeld(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        assertOpener(helper, player, ItemStack.EMPTY, AttackInput.LIGHT, "basic/jab");
        assertOpener(helper, player, new ItemStack(NinjaWeapons.KUNAI.get()), AttackInput.LIGHT, "kunai/slash");
        assertOpener(helper, player, new ItemStack(NinjaWeapons.KUNAI.get()), AttackInput.HEAVY, "kunai/stab");
        assertOpener(helper, player, new ItemStack(NinjaWeapons.CHAKRA_BLADE.get()), AttackInput.LIGHT, "kunai/slash");
        assertOpener(helper, player, new ItemStack(NinjaWeapons.BO_STAFF.get()), AttackInput.LIGHT, "staff/sweep");
        assertOpener(helper, player, new ItemStack(NinjaWeapons.BO_STAFF.get()), AttackInput.LAUNCHER, "staff/rising_sweep");
        assertOpener(helper, player, new ItemStack(NinjaWeapons.NINJATO.get()), AttackInput.LIGHT, "sword/slash");
        assertOpener(helper, player, new ItemStack(Items.IRON_SWORD), AttackInput.HEAVY, "sword/thrust");
        assertOpener(helper, player, new ItemStack(NinjaWeapons.SHURIKEN.get()), AttackInput.LIGHT, "basic/jab");
        helper.assertTrue(Noirm.id("stance_kunai").equals(WeaponMoves.stanceFor(new ItemStack(NinjaWeapons.KUNAI.get()))), "the kunai stance");
        helper.assertTrue(Noirm.id("stance_staff").equals(WeaponMoves.stanceFor(new ItemStack(NinjaWeapons.BO_STAFF.get()))), "the staff stance");
        helper.assertTrue(Noirm.id("stance_sword").equals(WeaponMoves.stanceFor(new ItemStack(Items.DIAMOND_SWORD))), "the sword stance");
        // Holding a weapon never changes the player's own chosen style.
        var data = com.noirm.player.ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        helper.assertTrue(com.noirm.combat.StyleSystem.styleOf(data).equals(CombatData.DEFAULT_STYLE), "own style kept");
        helper.assertFalse(com.noirm.combat.StyleSystem.available(data).contains(Moveset.KUNAI.style()), "weapon movesets aren't styles to pick");
        // Ninjas holding one fight with it too.
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        ninja.setCombatStyle(CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        ninja.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(NinjaWeapons.BO_STAFF.get()));
        helper.assertTrue(Moveset.STAFF.style().equals(CombatSystem.styleFor(ninja)), "a ninja with a staff fights with it");
        // Every move a weapon style can reach exists.
        for (Moveset moveset : Moveset.values()) {
            CombatStyle style = CombatData.style(moveset.style());
            helper.assertTrue(style != null && !style.playable(), moveset.style() + " loaded, not playable");
            Set<ResourceLocation> seen = new HashSet<>();
            List<ResourceLocation> open = new java.util.ArrayList<>(style.openers().values());
            while (!open.isEmpty()) {
                ResourceLocation id = open.remove(open.size() - 1);
                if (!seen.add(id)) {
                    continue;
                }
                CombatMove move = CombatData.move(id);
                helper.assertTrue(move != null, "move " + id + " exists");
                helper.assertTrue(move.animation().isPresent(), id + " has an animation");
                open.addAll(move.next().values());
            }
        }
        ninja.discard();
        helper.succeed();
    }

    /** Explosive tags only dig with noirmTerrainDamage on; they always go off. */
    @GameTest(template = "empty", batch = "noirm_weapon_blast", timeoutTicks = 200)
    public static void explosiveTagRespectsTerrainRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var rule = level.getGameRules().getRule(Destruction.TERRAIN_DAMAGE);
        boolean before = rule.get();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
            }
        }
        BlockPos under = new BlockPos(1, 1, 1);
        rule.set(false, level.getServer());
        ThrownWeapon first = slapTag(helper, under);
        helper.runAfterDelay(5, () -> helper.assertTrue(first.isStuck() && first.fuse() > 0, "the tag sticks and burns"));
        helper.runAfterDelay(ThrowStats.PLACED_TAG.fuse() + 10, () -> {
            helper.assertFalse(first.isAlive(), "the tag went off");
            helper.assertBlockPresent(Blocks.DIRT, under);
            rule.set(true, level.getServer());
            slapTag(helper, under);
        });
        helper.runAfterDelay(2 * ThrowStats.PLACED_TAG.fuse() + 20, () -> {
            rule.set(before, level.getServer());
            int gone = 0;
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if (helper.getBlockState(new BlockPos(x, 1, z)).isAir()) {
                        gone++;
                    }
                }
            }
            helper.assertTrue(gone > 0, "with terrain damage on, the blast digs a crater");
            clearWeapons(helper);
            helper.succeed();
        });
    }

    /** Every weapon is craftable, has a texture-backed name, and the melee ones hit harder than fists. */
    @GameTest(template = "empty", batch = "noirm_weapon_recipes")
    public static void weaponRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(NinjaWeapons.ALL.size() >= 9, "nine weapons, got " + NinjaWeapons.ALL.size());
        for (RegistryObject<? extends Item> weapon : NinjaWeapons.ALL) {
            helper.assertTrue(recipes.byKey(weapon.getId()).isPresent(), "a recipe for " + weapon.getId());
        }
        helper.assertTrue(ThrowStats.FUMA_SHURIKEN.damage() > ThrowStats.KUNAI.damage()
                && ThrowStats.KUNAI.damage() > ThrowStats.SHURIKEN.damage()
                && ThrowStats.SHURIKEN.damage() > ThrowStats.SENBON.damage(), "fūma > kunai > shuriken > senbon");
        helper.assertTrue(new ItemStack(NinjaWeapons.KUNAI.get()).isEnchantable(), "a single kunai takes enchantments");
        helper.assertFalse(new ItemStack(NinjaWeapons.KUNAI.get(), 4).isEnchantable(), "a stack doesn't");
        helper.assertTrue(new ItemStack(NinjaWeapons.BO_STAFF.get()).canApplyAtEnchantingTable(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK),
                "staffs take Knockback");
        // Chakra blades carry the wielder's strongest basic nature.
        SparringNinja fire = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
        fire.joinClan(TestClans.FIRE);
        helper.assertTrue(com.noirm.weapon.ChakraBladeItem.natureOf(fire) == com.noirm.clan.Nature.FIRE, "a fire clan's blade burns");
        fire.discard();
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static ThrownWeapon slapTag(GameTestHelper helper, BlockPos onTopOf) {
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(onTopOf.above()).add(0, 0.3, 0));
        ThrownWeapon tag = ThrownWeapon.placedTag(helper.getLevel(), null, at, new Vec3(0, -0.35, 0));
        helper.getLevel().addFreshEntity(tag);
        return tag;
    }

    private static void assertOpener(GameTestHelper helper, Player player, ItemStack held, AttackInput input, String expected) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        ResourceLocation got = CombatSystem.resolveMove(player, null, input);
        helper.assertTrue(Noirm.id(expected).equals(got), (held.isEmpty() ? "fists" : held.getItem().toString()) + " " + input
                + " should open with " + expected + ", got " + got);
    }

    private static SparringNinja ninja(GameTestHelper helper, float x, float z) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(CombatData.DEFAULT_STYLE);
        ninja.joinClan(TestClans.PLAIN);
        return ninja;
    }

    private static List<ThrownWeapon> weapons(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(14);
        return helper.getLevel().getEntitiesOfClass(ThrownWeapon.class, area);
    }

    /** Thrown weapons aren't living, so {@link TestKit#clearAround} leaves them: remove them here. */
    private static void clearWeapons(GameTestHelper helper) {
        weapons(helper).forEach(ThrownWeapon::discard);
    }
}

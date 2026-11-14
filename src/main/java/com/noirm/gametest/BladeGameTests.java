package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatMove;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatStyle;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.registry.NoirmEntities;
import com.noirm.weapon.BladeEvents;
import com.noirm.weapon.BladeStats;
import com.noirm.weapon.NinjaArsenal;
import com.noirm.weapon.NinjaBladeItem;
import com.noirm.weapon.NinjaBlades;
import com.noirm.weapon.NinjaWeapons;
import com.noirm.weapon.SpecialWeapons;
import com.noirm.weapon.WeaponMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The v0.13 blades: data, movesets, damage, traits, chakra flow, NPCs, and the HD textures. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BladeGameTests {
    private static final float NINJA_HEALTH = 40f;

    private BladeGameTests() {}

    // ---------------------------------------------------------------- data

    /** Every blade exists with a recipe, a name, its data numbers, a loaded moveset whose moves all exist. */
    @GameTest(template = "empty", batch = "noirm_blades_data")
    public static void bladesAreComplete(GameTestHelper helper) {
        helper.assertTrue(NinjaBlades.ALL.size() == 7, "seven new blades, got " + NinjaBlades.ALL.size());
        JsonObject stats = json("/data/noirm/weapons/stats.json");
        JsonObject lang = json("/assets/noirm/lang/en_us.json");
        var recipes = helper.getLevel().getRecipeManager();
        Player player = helper.makeMockPlayer();
        Set<String> movesets = new HashSet<>();
        for (RegistryObject<NinjaBladeItem> blade : NinjaBlades.ALL) {
            String id = blade.getId().getPath();
            ItemStack stack = new ItemStack(blade.get());
            helper.assertTrue(recipes.byKey(blade.getId()).isPresent(), "a recipe for " + id);
            helper.assertTrue(lang.has("item.noirm." + id), "an English name for " + id);
            // The numbers come from data.
            JsonObject s = stats.getAsJsonObject(id);
            helper.assertTrue(s != null, id + " has stats in stats.json");
            assertNear(helper, s.get("damage").getAsFloat() - 1f, modifier(stack, Attributes.ATTACK_DAMAGE), id + " attack damage");
            assertNear(helper, s.get("speed").getAsFloat() - 4f, modifier(stack, Attributes.ATTACK_SPEED), id + " attack speed");
            assertNear(helper, s.has("reach") ? s.get("reach").getAsFloat() : 0f, modifier(stack, ForgeMod.ENTITY_REACH.get()), id + " reach");
            helper.assertTrue(stack.getMaxDamage() == s.get("durability").getAsInt(), id + " durability");
            // Its moveset: a loaded, non-playable style; every reachable move exists and is animated.
            var moveset = WeaponMoves.movesetFor(stack);
            helper.assertTrue(moveset != null && moveset.key().equals(s.get("moveset").getAsString()), id + " fights with its moveset");
            movesets.add(moveset.key());
            CombatStyle style = CombatData.style(moveset.style());
            helper.assertTrue(style != null && !style.playable(), moveset.style() + " loaded, not playable");
            for (AttackInput input : new AttackInput[] {AttackInput.LIGHT, AttackInput.HEAVY, AttackInput.LAUNCHER}) {
                helper.assertTrue(style.openers().containsKey(input), id + " opens with a " + input);
            }
            Set<ResourceLocation> seen = new HashSet<>();
            List<ResourceLocation> open = new ArrayList<>(style.openers().values());
            while (!open.isEmpty()) {
                ResourceLocation moveId = open.remove(open.size() - 1);
                if (!seen.add(moveId)) {
                    continue;
                }
                CombatMove move = CombatData.move(moveId);
                helper.assertTrue(move != null, "move " + moveId + " exists");
                helper.assertTrue(move.animation().isPresent(), moveId + " has an animation");
                open.addAll(move.next().values());
            }
            helper.assertTrue(seen.size() >= 6, id + " has a real moveset (" + seen.size() + " moves)");
            // A player holding it opens with the moveset's light move.
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(style.opener(AttackInput.LIGHT).equals(CombatSystem.resolveMove(player, null, AttackInput.LIGHT)),
                    id + ": light opens with its own move");
        }
        helper.assertTrue(movesets.size() == 7, "seven different movesets: " + movesets);
        helper.assertTrue(com.noirm.command.KitCommand.all().containsKey(Noirm.id("arsenal")), "the arsenal kit");
        helper.assertTrue(NinjaBlades.KATANA.get().stats().flow() != null && NinjaBlades.TEKKO_KAGI.get().stats().flow() != null,
                "two blades take a chakra flow");
        helper.succeed();
    }

    /** Every weapon texture is HD (64x64); the flow glows exist too. */
    @GameTest(template = "empty", batch = "noirm_blades_data")
    public static void weaponTexturesAreHd(GameTestHelper helper) {
        List<String> names = new ArrayList<>();
        NinjaWeapons.ALL.forEach(w -> names.add(w.getId().getPath()));
        SpecialWeapons.ALL.forEach(w -> names.add(w.getId().getPath()));
        NinjaBlades.ALL.forEach(w -> names.add(w.getId().getPath()));
        names.add("katana_glow");
        names.add("tekko_kagi_glow");
        for (String name : names) {
            String path = "/assets/noirm/textures/item/" + name + ".png";
            try (InputStream in = BladeGameTests.class.getResourceAsStream(path)) {
                helper.assertTrue(in != null, "texture " + path);
                BufferedImage img = ImageIO.read(in);
                helper.assertTrue(img.getWidth() == 64 && img.getHeight() == 64, name + " is 64x64, got " + img.getWidth() + "x" + img.getHeight());
            } catch (java.io.IOException e) {
                helper.fail("couldn't read " + path + ": " + e);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- damage (one fight per batch)

    @GameTest(template = "empty", batch = "noirm_blade_hit_tanto")
    public static void tantoHitsForItsDamage(GameTestHelper helper) {
        landsLight(helper, NinjaBlades.TANTO.get());
    }

    @GameTest(template = "empty", batch = "noirm_blade_hit_katana")
    public static void katanaHitsForItsDamage(GameTestHelper helper) {
        landsLight(helper, NinjaBlades.KATANA.get());
    }

    @GameTest(template = "empty", batch = "noirm_blade_hit_nodachi")
    public static void nodachiHitsForItsDamage(GameTestHelper helper) {
        landsLight(helper, NinjaBlades.NODACHI.get());
    }

    @GameTest(template = "empty", batch = "noirm_blade_hit_tonfa")
    public static void tonfaHitsForItsDamage(GameTestHelper helper) {
        landsLight(helper, NinjaBlades.TONFA.get());
    }

    @GameTest(template = "empty", batch = "noirm_blade_hit_yari")
    public static void yariHitsForItsDamage(GameTestHelper helper) {
        landsLight(helper, NinjaBlades.YARI.get());
    }

    @GameTest(template = "empty", batch = "noirm_blade_hit_claws")
    public static void tekkoKagiHitsForItsDamage(GameTestHelper helper) {
        landsLight(helper, NinjaBlades.TEKKO_KAGI.get());
    }

    @GameTest(template = "empty", batch = "noirm_blade_hit_tessen")
    public static void tessenHitsForItsDamage(GameTestHelper helper) {
        landsLight(helper, NinjaBlades.TESSEN.get());
    }

    /**
     * A ninja holding {@code blade} attacks (light) in combat: it fights with the blade's moveset,
     * and the hit deals the move's damage plus the blade's share ({@link CombatSystem#weaponDamage}).
     */
    private static void landsLight(GameTestHelper helper, NinjaBladeItem blade) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
        face(attacker, target);
        face(target, attacker);
        helper.assertTrue(blade.moveset().style().equals(CombatSystem.styleFor(attacker)), "the ninja fights with the " + blade.bladeId() + " moveset");
        ResourceLocation opener = CombatData.style(blade.moveset().style()).opener(AttackInput.LIGHT);
        CombatMove move = CombatData.move(opener);
        helper.assertTrue(CombatSystem.requestAttack(attacker, AttackInput.LIGHT), "the attack starts");
        helper.assertTrue(opener.equals(CombatStates.get(attacker).moveId()), "with " + opener);
        float[] first = {-1f};
        helper.onEachTick(() -> {
            if (first[0] < 0 && target.getHealth() < NINJA_HEALTH) {
                first[0] = NINJA_HEALTH - target.getHealth();
            }
        });
        helper.runAfterDelay(move.totalTicks() + 4, () -> {
            float bonus = CombatSystem.weaponDamage(attacker, target, move.hit().damage()); // the held blade's attributes apply by now
            helper.assertTrue(bonus > 0.5f, blade.bladeId() + " adds its own damage: " + bonus);
            float expected = move.hit().damage() + bonus;
            helper.assertTrue(first[0] > 0, blade.bladeId() + "'s light hit landed");
            helper.assertTrue(Math.abs(first[0] - expected) < 0.3f,
                    blade.bladeId() + " hits for its damage: expected " + expected + ", got " + first[0]);
            clearAround(helper);
        });
    }

    /** Heavier blades hit harder than lighter ones (the blade's share of a move). */
    @GameTest(template = "empty", batch = "noirm_blades_data")
    public static void heavierBladesHitHarder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Map<Item, Float> share = new HashMap<>();
        for (var blade : NinjaBlades.ALL) {
            var mods = new ItemStack(blade.get()).getAttributeModifiers(EquipmentSlot.MAINHAND);
            player.getAttributes().addTransientAttributeModifiers(mods);
            share.put(blade.get(), CombatSystem.weaponDamage(player, null, 4f));
            player.getAttributes().removeAttributeModifiers(mods);
        }
        helper.assertTrue(share.get(NinjaBlades.NODACHI.get()) > share.get(NinjaBlades.KATANA.get())
                && share.get(NinjaBlades.KATANA.get()) > share.get(NinjaBlades.YARI.get())
                && share.get(NinjaBlades.YARI.get()) > share.get(NinjaBlades.TANTO.get()), "nodachi > katana > yari > tantō: " + share);
        helper.succeed();
    }

    // ---------------------------------------------------------------- traits

    /** A tantō in the back cuts much deeper than one from the front. */
    @GameTest(template = "empty", batch = "noirm_blade_backstab")
    public static void tantoBackstabs(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TANTO.get()));
        face(attacker, target);
        face(target, attacker);
        helper.assertFalse(BladeEvents.isBehind(attacker, target), "face to face is not behind");
        target.hurt(attacker.damageSources().mobAttack(attacker), 4f);
        float front = NINJA_HEALTH - target.getHealth();
        target.setHealth(NINJA_HEALTH);
        target.invulnerableTime = 0;
        face(target, attacker);
        float away = target.getYRot() + 180f;
        target.setYRot(away);
        target.setYHeadRot(away);
        target.setYBodyRot(away);
        helper.assertTrue(BladeEvents.isBehind(attacker, target), "now the target faces away");
        target.hurt(attacker.damageSources().mobAttack(attacker), 4f);
        float back = NINJA_HEALTH - target.getHealth();
        helper.assertTrue(front > 0 && back > front * 1.4f, "the stab in the back cuts deeper: front " + front + ", back " + back);
        clearAround(helper);
    }

    /** Tonfa blows crush the guard; nodachi hits shove; claws make the target bleed. */
    @GameTest(template = "empty", batch = "noirm_blade_traits", timeoutTicks = 100)
    public static void tonfaCrushesClawsBleedNodachiShoves(GameTestHelper helper) {
        floor(helper);
        SparringNinja attacker = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        face(attacker, target);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TONFA.get()));
        float posture = CombatStates.get(target).posture();
        target.hurt(attacker.damageSources().mobAttack(attacker), 2f);
        helper.assertTrue(CombatStates.get(target).posture() >= posture + BladeEvents.CRUSH_POSTURE, "tonfa crush the guard: "
                + posture + " -> " + CombatStates.get(target).posture());
        target.invulnerableTime = 0;
        target.setDeltaMovement(Vec3.ZERO);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.NODACHI.get()));
        target.hurt(attacker.damageSources().mobAttack(attacker), 2f);
        helper.assertTrue(target.getDeltaMovement().z > 0.3, "the nodachi shoves: " + target.getDeltaMovement());
        target.invulnerableTime = 0;
        target.setDeltaMovement(Vec3.ZERO);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TEKKO_KAGI.get()));
        target.hurt(attacker.damageSources().mobAttack(attacker), 2f);
        helper.assertTrue(BladeEvents.bleedTicks(target) >= BladeEvents.BLEED_SECONDS * 20 - 1, "the claws make it bleed");
        target.setHealth(NINJA_HEALTH);
        helper.runAfterDelay(BladeEvents.BLEED_SECONDS * 20 + 5, () -> {
            float bled = NINJA_HEALTH - target.getHealth();
            helper.assertTrue(bled >= BladeEvents.BLEED_DAMAGE * (BladeEvents.BLEED_SECONDS - 0.5f), "it bled for a few seconds: " + bled);
            helper.assertTrue(BladeEvents.bleedTicks(target) == 0, "and stopped");
            clearAround(helper);
        });
    }

    /** A tessen, raised in guard, swats an arrow from the front out of the air. */
    @GameTest(template = "empty", batch = "noirm_blade_tessen", timeoutTicks = 60)
    public static void tessenSwatsArrows(GameTestHelper helper) {
        floor(helper);
        SparringNinja defender = ninja(helper, 1.5f, 0.5f, TestClans.PLAIN);
        SparringNinja shooter = ninja(helper, 1.5f, 2.5f, TestClans.PLAIN);
        defender.setInvulnerable(false);
        face(defender, shooter);
        defender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TESSEN.get()));
        CombatSystem.setGuard(defender, true);
        Arrow arrow = new Arrow(helper.getLevel(), shooter);
        arrow.setPos(helper.absoluteVec(new Vec3(1.5, 1.9, 1.7)));
        arrow.shoot(0, 0, -1, 1.0f, 0f);
        helper.assertTrue(BladeEvents.deflects(defender, arrow), "a raised tessen swats arrows from the front");
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(defender.getHealth() >= defender.getMaxHealth(), "the arrow never hit: " + defender.getHealth());
            helper.getLevel().getEntitiesOfClass(Arrow.class, new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(16)).forEach(Arrow::discard);
            clearAround(helper);
        });
    }

    // ---------------------------------------------------------------- chakra flow

    /** Lightning flows into a lightning user's katana: it glows, hits harder and numbs; not for a water user. */
    @GameTest(template = "empty", batch = "noirm_blade_flow", timeoutTicks = 60)
    public static void chakraFlowsIntoKatanaAndClaws(GameTestHelper helper) {
        floor(helper);
        SparringNinja lightning = ninja(helper, 0.5f, 0.5f, TestClans.FIRE);       // fire + lightning
        SparringNinja water = ninja(helper, 2.5f, 0.5f, TestClans.PLAIN);          // water only
        SparringNinja wind = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);    // has wind
        SparringNinja target = ninja(helper, 1.5f, 2.2f, TestClans.PLAIN);
        target.setInvulnerable(false);
        lightning.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.KATANA.get()));
        water.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.KATANA.get()));
        wind.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TEKKO_KAGI.get()));
        helper.runAfterDelay(BladeEvents.FLOW_CHECK_TICKS + 2, () -> {
            helper.assertTrue(NinjaBladeItem.isFlowing(lightning.getMainHandItem()), "the lightning user's katana glows");
            helper.assertFalse(NinjaBladeItem.isFlowing(water.getMainHandItem()), "the water user's doesn't");
            helper.assertTrue(NinjaBladeItem.isFlowing(wind.getMainHandItem()), "the wind user's claws glow");
            // Same blow, both katanas: the flowing one cuts harder and numbs.
            target.hurt(water.damageSources().mobAttack(water), 4f);
            float plain = NINJA_HEALTH - target.getHealth();
            helper.assertFalse(CombatSystem.isStaggered(target), "a plain cut doesn't numb");
            target.setHealth(NINJA_HEALTH);
            target.invulnerableTime = 0;
            target.hurt(lightning.damageSources().mobAttack(lightning), 4f);
            float charged = NINJA_HEALTH - target.getHealth();
            BladeStats.Flow flow = NinjaBlades.KATANA.get().stats().flow();
            helper.assertTrue(Math.abs(charged - plain * flow.damage()) < 0.05f, "lightning flow: x" + flow.damage() + ": " + plain + " -> " + charged);
            helper.assertTrue(CombatSystem.isStaggered(target), "and numbs");
            // Wind flow makes the claws' bleeding last longer.
            target.setHealth(NINJA_HEALTH);
            target.invulnerableTime = 0;
            target.hurt(wind.damageSources().mobAttack(wind), 2f);
            helper.assertTrue(BladeEvents.bleedTicks(target) > BladeEvents.BLEED_SECONDS * 20, "wind lengthens the bleeding: " + BladeEvents.bleedTicks(target));
            clearAround(helper);
        });
    }

    // ---------------------------------------------------------------- ninjas

    /** Ninjas carry the new blades (the nodachi only wanderers), ANBU patrols the tantō. */
    @GameTest(template = "empty", batch = "noirm_blades_data")
    public static void ninjasCarryBlades(GameTestHelper helper) {
        RandomSource r = RandomSource.create(1234);
        Map<Item, Integer> resident = new HashMap<>();
        Map<Item, Integer> rogue = new HashMap<>();
        for (int i = 0; i < 6000; i++) {
            Item a = NinjaArsenal.mainWeapon(false, r);
            Item b = NinjaArsenal.mainWeapon(true, r);
            if (a != null) {
                resident.merge(a, 1, Integer::sum);
            }
            if (b != null) {
                rogue.merge(b, 1, Integer::sum);
            }
        }
        for (var blade : NinjaBlades.ALL) {
            helper.assertTrue(rogue.getOrDefault(blade.get(), 0) > 10, "wanderers carry " + blade.getId() + ": " + rogue.get(blade.get()));
        }
        helper.assertTrue(resident.getOrDefault(NinjaBlades.NODACHI.get(), 0) == 0, "village ninjas never carry a nodachi");
        helper.assertTrue(NinjaArsenal.isRogueBlade(NinjaBlades.NODACHI.get()), "a nodachi marks a rogue swordsman");
        int tanto = 0;
        for (int i = 0; i < 40; i++) {
            SparringNinja anbu = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), 1.5f, 1f, 1.5f);
            anbu.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(NinjaWeapons.KUNAI.get()));
            NinjaArsenal.armAnbu(anbu, r);
            if (anbu.getMainHandItem().is(NinjaBlades.TANTO.get())) {
                tanto++;
            }
            anbu.discard();
        }
        helper.assertTrue(tanto > 15 && tanto < 40, "most ANBU carry a tantō: " + tanto + "/40");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        return ninja;
    }

    private static float modifier(ItemStack stack, Attribute attribute) {
        return (float) stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute).stream().mapToDouble(m -> m.getAmount()).sum();
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }

    private static JsonObject json(String path) {
        try (InputStream in = BladeGameTests.class.getResourceAsStream(path)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("couldn't read " + path, e);
        }
    }
}

package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.registry.NoirmEntities;
import com.noirm.weapon.BladeEvents;
import com.noirm.weapon.BladeStats;
import com.noirm.weapon.NinjaBladeItem;
import com.noirm.weapon.NinjaBlades;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The APX follow-up chakra flows (nodachi + fire, yari + wind, tonfa + earth, tantō + lightning):
 * each glows and works only for a wielder with the right nature; and the long held models
 * (yari, nodachi, bō staff) and every flow model are complete.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BladeFlowGameTests {
    private static final float HEALTH = 40f;

    private BladeFlowGameTests() {}

    // ---------------------------------------------------------------- the flows (one fight per batch)

    /** Fire flows into a fire user's nodachi: it glows, cuts harder and sets the target (and an enemy beside it) alight. */
    @GameTest(template = "empty", batch = "noirm_flow_nodachi", timeoutTicks = 60)
    public static void nodachiFireBurns(GameTestHelper helper) {
        floor(helper);
        SparringNinja fire = ninja(helper, 0.5f, 0.5f, TestClans.FIRE);
        SparringNinja water = ninja(helper, 2.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        SparringNinja beside = ninja(helper, 2.4f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        fire.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.NODACHI.get()));
        water.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.NODACHI.get()));
        helper.runAfterDelay(BladeEvents.FLOW_CHECK_TICKS + 2, () -> {
            helper.assertTrue(NinjaBladeItem.isFlowing(fire.getMainHandItem()), "the fire user's nodachi glows");
            helper.assertFalse(NinjaBladeItem.isFlowing(water.getMainHandItem()), "the water user's doesn't");
            target.hurt(water.damageSources().mobAttack(water), 4f);
            float plain = HEALTH - target.getHealth();
            helper.assertFalse(target.isOnFire(), "a plain cut doesn't burn");
            target.setHealth(HEALTH);
            target.invulnerableTime = 0;
            target.hurt(fire.damageSources().mobAttack(fire), 4f);
            float burning = HEALTH - target.getHealth();
            BladeStats.Flow flow = NinjaBlades.NODACHI.get().stats().flow();
            helper.assertTrue(flow.burn() > 0, "the nodachi's flow burns");
            helper.assertTrue(Math.abs(burning - plain * flow.damage()) < 0.05f, "fire flow: x" + flow.damage() + ": " + plain + " -> " + burning);
            helper.assertTrue(target.getRemainingFireTicks() > 20, "the target is alight: " + target.getRemainingFireTicks());
            helper.assertTrue(beside.getRemainingFireTicks() > 0, "and so is the enemy beside it");
            helper.assertTrue(fire.getRemainingFireTicks() <= 0, "never the wielder");
            clearAround(helper);
        });
    }

    /** Wind flows into a wind user's yari: longer reach, and the thrust pierces to whoever stands behind. */
    @GameTest(template = "empty", batch = "noirm_flow_yari", timeoutTicks = 60)
    public static void yariWindPierces(GameTestHelper helper) {
        floor(helper);
        SparringNinja wind = ninja(helper, 1.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja water = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 1.5f, TestClans.PLAIN);
        SparringNinja behind = ninja(helper, 1.5f, 2.6f, TestClans.PLAIN);
        target.setInvulnerable(false);
        behind.setInvulnerable(false);
        face(wind, target);
        face(water, target);
        wind.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.YARI.get()));
        water.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.YARI.get()));
        helper.runAfterDelay(BladeEvents.FLOW_CHECK_TICKS + 2, () -> {
            BladeStats.Flow flow = NinjaBlades.YARI.get().stats().flow();
            BladeStats stats = NinjaBlades.YARI.get().stats();
            helper.assertTrue(NinjaBladeItem.isFlowing(wind.getMainHandItem()), "the wind user's yari glows");
            helper.assertFalse(NinjaBladeItem.isFlowing(water.getMainHandItem()), "the water user's doesn't");
            helper.assertTrue(Math.abs(reach(wind.getMainHandItem()) - (stats.reach() + flow.reach())) < 0.01f,
                    "flowing: +" + flow.reach() + " reach, got " + reach(wind.getMainHandItem()));
            helper.assertTrue(Math.abs(reach(water.getMainHandItem()) - stats.reach()) < 0.01f, "plain: its own reach");
            // A plain thrust stops at the target.
            target.hurt(water.damageSources().mobAttack(water), 4f);
            helper.assertTrue(behind.getHealth() >= HEALTH, "a plain thrust doesn't reach the one behind");
            target.setHealth(HEALTH);
            target.invulnerableTime = 0;
            target.hurt(wind.damageSources().mobAttack(wind), 4f);
            float cut = HEALTH - behind.getHealth();
            helper.assertTrue(cut > 0.5f, "the wind lance cuts the one behind: " + cut);
            helper.assertTrue(cut < HEALTH - target.getHealth(), "for less than the target took");
            clearAround(helper);
        });
    }

    /** Earth flows into an earth user's tonfa: harder blows that slow the target; not for a water user. */
    @GameTest(template = "empty", batch = "noirm_flow_tonfa", timeoutTicks = 60)
    public static void tonfaEarthSlows(GameTestHelper helper) {
        floor(helper);
        SparringNinja earth = ninja(helper, 0.5f, 0.5f, TestClans.EVERY_NATURE);
        SparringNinja water = ninja(helper, 2.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        earth.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TONFA.get()));
        water.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TONFA.get()));
        helper.runAfterDelay(BladeEvents.FLOW_CHECK_TICKS + 2, () -> {
            helper.assertTrue(NinjaBladeItem.isFlowing(earth.getMainHandItem()), "the earth user's tonfa glow");
            helper.assertFalse(NinjaBladeItem.isFlowing(water.getMainHandItem()), "the water user's don't");
            target.hurt(water.damageSources().mobAttack(water), 3f);
            helper.assertFalse(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "plain blows don't slow");
            float plain = HEALTH - target.getHealth();
            target.setHealth(HEALTH);
            target.invulnerableTime = 0;
            target.hurt(earth.damageSources().mobAttack(earth), 3f);
            float stone = HEALTH - target.getHealth();
            BladeStats.Flow flow = NinjaBlades.TONFA.get().stats().flow();
            helper.assertTrue(Math.abs(stone - plain * flow.damage()) < 0.05f, "earth flow: x" + flow.damage() + ": " + plain + " -> " + stone);
            helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "stone-heavy blows slow");
            clearAround(helper);
        });
    }

    /** Lightning flows into a lightning user's tantō: a stab in the back paralyses, one from the front doesn't. */
    @GameTest(template = "empty", batch = "noirm_flow_tanto", timeoutTicks = 60)
    public static void tantoLightningParalysesFromBehind(GameTestHelper helper) {
        floor(helper);
        SparringNinja lightning = ninja(helper, 1.5f, 0.5f, TestClans.FIRE); // fire + lightning
        SparringNinja water = ninja(helper, 0.5f, 0.5f, TestClans.PLAIN);
        SparringNinja target = ninja(helper, 1.5f, 2.0f, TestClans.PLAIN);
        target.setInvulnerable(false);
        lightning.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TANTO.get()));
        water.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NinjaBlades.TANTO.get()));
        helper.runAfterDelay(BladeEvents.FLOW_CHECK_TICKS + 2, () -> {
            helper.assertTrue(NinjaBladeItem.isFlowing(lightning.getMainHandItem()), "the lightning user's tantō shines");
            helper.assertFalse(NinjaBladeItem.isFlowing(water.getMainHandItem()), "the water user's doesn't");
            // From the front: no paralysis.
            face(target, lightning);
            target.hurt(lightning.damageSources().mobAttack(lightning), 2f);
            helper.assertFalse(CombatSystem.isStaggered(target), "a stab from the front doesn't paralyse");
            // From behind: the target faces away.
            face(target, lightning);
            float away = target.getYRot() + 180f;
            target.setYRot(away);
            target.setYHeadRot(away);
            target.yHeadRotO = away;
            helper.assertTrue(BladeEvents.isBehind(lightning, target), "(the lightning user is behind it now)");
            target.invulnerableTime = 0;
            target.hurt(water.damageSources().mobAttack(water), 2f);
            helper.assertFalse(CombatSystem.isStaggered(target), "a plain stab in the back doesn't paralyse");
            target.invulnerableTime = 0;
            target.hurt(lightning.damageSources().mobAttack(lightning), 2f);
            helper.assertTrue(CombatSystem.isStaggered(target), "a lightning stab in the back does");
            clearAround(helper);
        });
    }

    // ---------------------------------------------------------------- models

    /** The six flow blades each have a glowing model (glow texture 64x64); the long weapons hold a long model in the hand. */
    @GameTest(template = "empty", batch = "noirm_blades_data")
    public static void flowAndHeldModelsAreComplete(GameTestHelper helper) {
        JsonObject lang = json("/assets/noirm/lang/en_us.json");
        int flows = 0;
        for (RegistryObject<NinjaBladeItem> blade : NinjaBlades.ALL) {
            String id = blade.getId().getPath();
            BladeStats.Flow flow = blade.get().stats().flow();
            if (flow == null) {
                continue;
            }
            flows++;
            helper.assertTrue(lang.has("tooltip.noirm.blade.flow." + id), "a flow tooltip for " + id);
            JsonObject model = json("/assets/noirm/models/item/" + id + ".json");
            helper.assertTrue(model.has("overrides") && model.getAsJsonArray("overrides").toString().contains("noirm:item/" + id + "_flow"),
                    id + " swaps to its glowing model");
            JsonObject glowing = json("/assets/noirm/models/item/" + id + "_flow.json");
            helper.assertTrue(glowing.toString().contains("noirm:item/" + id + "_glow"), id + "'s glowing model draws its glow");
            image(helper, "/assets/noirm/textures/item/" + id + "_glow.png", 64, 64);
        }
        helper.assertTrue(flows == 6, "six blades take a chakra flow, got " + flows);
        for (String id : List.of("yari", "nodachi", "bo_staff")) {
            JsonObject model = json("/assets/noirm/models/item/" + id + ".json");
            helper.assertTrue("forge:separate_transforms".equals(model.get("loader").getAsString()), id + ": a held model and an icon");
            JsonObject gui = model.getAsJsonObject("perspectives").getAsJsonObject("gui");
            helper.assertTrue(gui.getAsJsonObject("textures").get("layer0").getAsString().equals("noirm:item/" + id), id + " keeps its icon in the inventory");
            JsonObject base = model.getAsJsonObject("base");
            helper.assertTrue(base.getAsJsonObject("textures").get("layer0").getAsString().equals("noirm:item/held/" + id), id + " holds its long texture");
            for (String hand : List.of("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand")) {
                JsonObject t = base.getAsJsonObject("display").getAsJsonObject(hand);
                helper.assertTrue(t != null, id + " has a " + hand + " transform");
                float length = t.getAsJsonArray("scale").get(1).getAsFloat();
                float min = hand.startsWith("third") ? 1.4f : 0.8f; // (first person draws it shorter: it sits under the camera)
                helper.assertTrue(length >= min && length <= 4f, id + " is long in the " + hand + ": " + length);
            }
            image(helper, "/assets/noirm/textures/item/held/" + id + ".png", 16, 128);
            if (model.has("overrides")) {
                JsonObject glowing = json("/assets/noirm/models/item/" + id + "_flow.json");
                helper.assertTrue(glowing.getAsJsonObject("base").toString().contains("noirm:item/held/" + id + "_glow"), id + " glows at full length");
                image(helper, "/assets/noirm/textures/item/held/" + id + "_glow.png", 16, 128);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static SparringNinja ninja(GameTestHelper helper, float x, float z, Clan clan) {
        SparringNinja ninja = helper.spawnWithNoFreeWill(NoirmEntities.SPARRING_NINJA.get(), x, 1f, z);
        ninja.setCombatStyle(CombatData.DEFAULT_STYLE);
        ninja.joinClan(clan);
        ninja.setHealth(HEALTH);
        return ninja;
    }

    private static float reach(ItemStack stack) {
        return (float) stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(ForgeMod.ENTITY_REACH.get()).stream()
                .mapToDouble(m -> m.getAmount()).sum();
    }

    private static void image(GameTestHelper helper, String path, int width, int height) {
        try (InputStream in = BladeFlowGameTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "texture " + path);
            BufferedImage img = ImageIO.read(in);
            helper.assertTrue(img.getWidth() == width && img.getHeight() == height,
                    path + " is " + width + "x" + height + ", got " + img.getWidth() + "x" + img.getHeight());
        } catch (java.io.IOException e) {
            helper.fail("couldn't read " + path + ": " + e);
        }
    }

    private static JsonObject json(String path) {
        try (InputStream in = BladeFlowGameTests.class.getResourceAsStream(path)) {
            require(in != null, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("couldn't read " + path, e);
        }
    }

    private static void require(boolean ok, String path) {
        if (!ok) {
            throw new IllegalStateException("missing " + path);
        }
    }
}

package com.noirm.gametest;

import static com.noirm.gametest.TestKit.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.noirm.Noirm;
import com.noirm.ally.Order;
import com.noirm.command.CommandGuide;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** The {@code /noirm} commands: every documented example parses, and the main ones do what they say. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommandGameTests {
    private CommandGameTests() {}

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "noirm_cmd_test"));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        return player;
    }

    private static CommandSourceStack source(FakePlayer player) {
        return player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return dispatcher(helper).execute(command, source);
        } catch (Exception e) {
            throw new IllegalStateException("/" + command + " failed: " + e.getMessage(), e);
        }
    }

    private static JsonObject lang(String file) {
        Path path = ModList.get().getModFileById(Noirm.MOD_ID).getFile().findResource("assets", Noirm.MOD_ID, "lang", file + ".json");
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The help can't drift from the real commands: every example parses to a runnable command, and has text in every language. */
    @GameTest(template = "empty", batch = "noirm_commands_parse")
    public static void everyHelpExampleParses(GameTestHelper helper) {
        CommandSourceStack source = source(player(helper));
        List<JsonObject> langs = List.of(lang("en_us"), lang("es_es"), lang("es_mx"));
        helper.assertTrue(CommandGuide.all().size() >= 80, "the guide lists " + CommandGuide.all().size() + " commands");
        for (CommandGuide.Entry entry : CommandGuide.all()) {
            String command = entry.example().substring(1);
            ParseResults<CommandSourceStack> parse = dispatcher(helper).parse(command, source);
            helper.assertTrue(parse.getExceptions().isEmpty() && !parse.getReader().canRead() && parse.getContext().getCommand() != null,
                    "help example doesn't parse: /" + command + " (stopped at '" + parse.getReader().getRemaining() + "')");
            helper.assertTrue(command.startsWith("noirm"), "examples are /noirm commands: " + command);
            for (JsonObject lang : langs) {
                helper.assertTrue(lang.has(entry.descriptionKey()), "no text for " + entry.descriptionKey());
            }
        }
        for (String section : CommandGuide.sections().keySet()) {
            for (JsonObject lang : langs) {
                helper.assertTrue(lang.has("help.noirm.section." + section), "no section title for " + section);
            }
            run(helper, source, "noirm help " + section);
        }
        run(helper, source, "noirm help");
        run(helper, source, "noirm");
        helper.succeed();
    }

    /** The main commands change what they say they change. */
    @GameTest(template = "empty", batch = "noirm_commands_run", timeoutTicks = 60)
    public static void commandsDoWhatTheySay(GameTestHelper helper) {
        floor(helper);
        FakePlayer player = player(helper);
        CommandSourceStack source = source(player);
        ShinobiData data = ShinobiCapability.get(player).orElseThrow(IllegalStateException::new);
        try {
            run(helper, source, "noirm me max");
            helper.assertTrue(data.getTaijutsu() == ShinobiData.MAX_STAT && data.getReserves() == ShinobiData.MAX_STAT, "me max sets every stat");
            run(helper, source, "noirm me");

            run(helper, source, "noirm jutsu learn all");
            helper.assertTrue(data.jutsu().known().size() > 20, "learned every jutsu");
            run(helper, source, "noirm jutsu mastery all 3");
            run(helper, source, "noirm jutsu forget all");
            helper.assertTrue(data.jutsu().known().isEmpty(), "forget all empties the book");
            run(helper, source, "noirm jutsu learn nature fire");
            helper.assertTrue(data.jutsu().knows(Noirm.id("katon/great_fireball")), "learned the fire jutsu");

            run(helper, source, "noirm nature all");
            helper.assertTrue(data.identity().natures().size() >= 5, "every nature");
            run(helper, source, "noirm nature remove water");
            helper.assertFalse(data.identity().natures().containsKey(com.noirm.clan.Nature.WATER), "water removed");

            run(helper, source, "noirm style set strong_fist");
            helper.assertTrue("noirm:strong_fist".equals(data.getCombatStyle()), "style set");

            run(helper, source, "noirm kit tools");
            int kunai = player.getInventory().countItem(com.noirm.weapon.NinjaWeapons.KUNAI.get());
            helper.assertTrue(kunai >= 32, "the tools kit gives 32 kunai, got " + kunai);
            run(helper, source, "noirm kit kage");
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getItem() == com.noirm.gear.NoirmGear.KAGE_HAT_RED.get(), "kits put outfits on");

            String clan = com.noirm.clan.WorldClans.get(helper.getLevel().getServer()).allClans().get(0).id();
            run(helper, source, "noirm spawn ally " + clan);
            helper.assertTrue(data.crew().size() == 1, "spawn ally recruits");
            run(helper, source, "noirm crew order wait");
            run(helper, source, "noirm crew teach water");
            run(helper, source, "noirm spawn rogue 10");
            AABB area = new AABB(player.blockPosition()).inflate(12);
            List<SparringNinja> ninjas = helper.getLevel().getEntitiesOfClass(SparringNinja.class, area);
            SparringNinja ally = ninjas.stream().filter(SparringNinja::inCrew).findFirst().orElseThrow(() -> new IllegalStateException("no ally"));
            helper.assertTrue(ally.order() == Order.WAIT, "crew order reaches the ally");
            helper.assertTrue(ally.natures().containsKey(com.noirm.clan.Nature.WATER), "crew teach gives the nature");
            helper.assertTrue(ninjas.size() == 2, "a rogue and an ally, got " + ninjas.size());
            run(helper, source, "noirm world cleanup 12");
            List<SparringNinja> left = helper.getLevel().getEntitiesOfClass(SparringNinja.class, area);
            helper.assertTrue(left.size() == 1 && left.get(0) == ally, "cleanup removes the rogue but not the crew");
            run(helper, source, "noirm crew dismiss");
            helper.assertTrue(data.crew().isEmpty() && !ally.inCrew(), "dismissed");
            ally.discard();

            run(helper, source, "noirm world destruction off");
            helper.assertFalse(helper.getLevel().getGameRules().getBoolean(com.noirm.world.Destruction.TERRAIN_DAMAGE), "destruction off");
            run(helper, source, "noirm world destruction on");
        } finally {
            helper.getLevel().getGameRules().getRule(com.noirm.world.Destruction.TERRAIN_DAMAGE).set(true, helper.getLevel().getServer());
        }
        clearAround(helper);
    }
}

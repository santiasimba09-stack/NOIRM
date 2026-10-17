package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.clan.WorldClans;
import com.noirm.dojutsu.EyeData;
import com.noirm.dojutsu.Mangekyo;
import com.noirm.dojutsu.MangekyoAbility;
import com.noirm.dojutsu.MangekyoNpcs;
import com.noirm.dojutsu.MangekyoTransplant;
import com.noirm.dojutsu.Sharingan;
import com.noirm.entity.SparringNinja;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * {@code /noirm mangekyo …} (cheats), to test the Mangekyō without waiting for a loss:
 * <ul>
 *   <li>{@code awaken} — open it now (needs a 3-tomoe Uchiha; it sets 3 tomoe first) ·
 *       {@code clear} — back to a plain Sharingan · {@code eternal} — make it Eternal</li>
 *   <li>{@code strain <0-100>} · {@code pattern <0-7>} · {@code ability <left|right> <name>}</li>
 *   <li>{@code eye <1-3>} — a Sharingan eye item · {@code eyes} — a pair of Mangekyō eyes (random)</li>
 *   <li>{@code npc} — a Mangekyō Uchiha ninja in front of you · {@code info}</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MangekyoCommand {
    private MangekyoCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("mangekyo").requires(s -> s.hasPermission(2))
                        .executes(ctx -> info(ctx.getSource()))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("awaken").executes(ctx -> awaken(ctx.getSource())))
                        .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                        .then(Commands.literal("eternal").executes(ctx -> eternal(ctx.getSource())))
                        .then(Commands.literal("strain").then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                .executes(ctx -> strain(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
                        .then(Commands.literal("pattern").then(Commands.argument("n", IntegerArgumentType.integer(0, Mangekyo.PATTERNS - 1))
                                .executes(ctx -> pattern(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.literal("ability").then(Commands.argument("eye", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"left", "right"}, b))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(MangekyoAbility.values()).map(MangekyoAbility::key), b))
                                        .executes(ctx -> ability(ctx.getSource(), StringArgumentType.getString(ctx, "eye"),
                                                StringArgumentType.getString(ctx, "name"))))))
                        .then(Commands.literal("eye").then(Commands.argument("tomoe", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> give(ctx.getSource(), MangekyoTransplant.EyeInfo.sharingan(IntegerArgumentType.getInteger(ctx, "tomoe"), "")))))
                        .then(Commands.literal("eyes").executes(ctx -> giveMangekyoEyes(ctx.getSource())))
                        .then(Commands.literal("npc").executes(ctx -> npc(ctx.getSource())))));
    }

    private static ShinobiData data(CommandSourceStack source) throws CommandSyntaxException {
        return ShinobiCapability.get(source.getPlayerOrException()).orElseThrow();
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        EyeData eyes = data(source).eyes();
        if (!eyes.hasMangekyo()) {
            source.sendSuccess(() -> Component.translatable("command.noirm.mangekyo.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.noirm.mangekyo.info",
                Component.translatable("pattern.noirm.mangekyo." + eyes.pattern()),
                eyes.left() == null ? Component.literal("-") : Component.translatable(Sharingan.jutsuKey(eyes.left().jutsu())),
                eyes.right() == null ? Component.literal("-") : Component.translatable(Sharingan.jutsuKey(eyes.right().jutsu())),
                Math.round(eyes.strain()), String.format("#%06X", eyes.susanooColor()),
                eyes.isEternal() ? "eternal" : eyes.isTransplanted() ? "transplanted" : "own"), false);
        return 1;
    }

    private static int awaken(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = data(source);
        if (!data.identity().hasSharingan()) {
            source.sendFailure(Component.translatable("command.noirm.mangekyo.not_uchiha"));
            return 0;
        }
        while (data.eyes().sharinganStage() < EyeData.MAX_STAGE) {
            Sharingan.advance(player, data, "message.noirm.sharingan.tomoe_growth");
        }
        if (!Mangekyo.awaken(player, data, Mangekyo.Route.COMMAND)) {
            source.sendFailure(Component.translatable("command.noirm.mangekyo.cannot"));
            return 0;
        }
        return 1;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = data(source);
        Mangekyo.clear(data);
        PowerVisuals.broadcast(source.getPlayerOrException());
        return 1;
    }

    private static int eternal(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = data(source);
        if (!data.eyes().hasMangekyo()) {
            source.sendFailure(Component.translatable("command.noirm.mangekyo.none"));
            return 0;
        }
        MangekyoTransplant.transplant(source.getPlayerOrException(), data, new MangekyoTransplant.EyeInfo(3, true,
                (data.eyes().pattern() + 3) % Mangekyo.PATTERNS, MangekyoAbility.AMATERASU, MangekyoAbility.TSUKUYOMI, 0, 0f, "command"));
        return 1;
    }

    private static int strain(CommandSourceStack source, float value) throws CommandSyntaxException {
        ShinobiData data = data(source);
        data.eyes().setStrain(0f);
        Mangekyo.addStrain(source.getPlayerOrException(), value);
        data.eyes().setStrain(value);
        data.markPowersDirty();
        PowerVisuals.broadcast(source.getPlayerOrException());
        return 1;
    }

    private static int pattern(CommandSourceStack source, int n) throws CommandSyntaxException {
        ShinobiData data = data(source);
        data.eyes().setPattern(n);
        data.markPowersDirty();
        PowerVisuals.broadcast(source.getPlayerOrException());
        return 1;
    }

    private static int ability(CommandSourceStack source, String eye, String name) throws CommandSyntaxException {
        ShinobiData data = data(source);
        MangekyoAbility ability = MangekyoAbility.byKey(name);
        if (ability == null || !data.eyes().hasMangekyo()) {
            source.sendFailure(Component.translatable("command.noirm.mangekyo.cannot"));
            return 0;
        }
        EyeData eyes = data.eyes();
        if ("right".equals(eye)) {
            eyes.setAbilities(eyes.left(), ability);
        } else {
            eyes.setAbilities(ability, eyes.right());
        }
        Mangekyo.syncJutsu(data);
        return 1;
    }

    private static int give(CommandSourceStack source, MangekyoTransplant.EyeInfo eye) throws CommandSyntaxException {
        source.getPlayerOrException().getInventory().add(eye.toStack());
        return 1;
    }

    private static int giveMangekyoEyes(CommandSourceStack source) throws CommandSyntaxException {
        var random = source.getPlayerOrException().getRandom();
        MangekyoAbility left = MangekyoAbility.roll(random, null);
        return give(source, new MangekyoTransplant.EyeInfo(3, true, random.nextInt(Mangekyo.PATTERNS), left,
                MangekyoAbility.roll(random, left), Mangekyo.SUSANOO_COLORS[random.nextInt(Mangekyo.SUSANOO_COLORS.length)], 0f, "?"));
    }

    private static int npc(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var clan = WorldClans.get(source.getServer()).find("uchiha").orElse(null);
        SparringNinja ninja = NoirmEntities.SPARRING_NINJA.get().create(source.getLevel());
        if (clan == null || ninja == null) {
            return 0;
        }
        var at = player.position().add(player.getLookAngle().multiply(4, 0, 4));
        ninja.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        ninja.joinClan(clan);
        ninja.setSharinganStage(EyeData.MAX_STAGE);
        MangekyoNpcs.makeMangekyo(ninja, player.getRandom());
        source.getLevel().addFreshEntity(ninja);
        return 1;
    }
}

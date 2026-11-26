package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.creator.CharacterCreator;
import com.noirm.creator.PlayerLook;
import com.noirm.player.ShinobiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /noirm creator}: the character creation screen.
 * <ul>
 *   <li>{@code /noirm creator}: reopen it to change your look (and height). Your clan and stats stay.</li>
 *   <li>{@code /noirm creator reroll}: everything again, with fresh clan rolls (cheats)</li>
 *   <li>{@code /noirm creator skip}: finish creation now with the look so far (cheats; tests and the smoke world)</li>
 *   <li>{@code /noirm creator height <0.85-1.15>}: change only your height</li>
 *   <li>{@code /noirm creator set <part> <option>}: change one part of your look (cheats)</li>
 *   <li>{@code /noirm creator colour <colour> <rrggbb>}: change one colour of your look (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CreatorCommand {
    private CreatorCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("creator")
                .executes(ctx -> open(ctx.getSource(), false))
                .then(Commands.literal("reroll").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> open(ctx.getSource(), true)))
                .then(Commands.literal("skip").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> skip(ctx.getSource())))
                .then(Commands.literal("set").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("part", StringArgumentType.word()).suggests(PARTS)
                                .then(Commands.argument("option", StringArgumentType.word()).suggests(OPTIONS)
                                        .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "part"),
                                                StringArgumentType.getString(ctx, "option"))))))
                .then(Commands.literal("colour").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("colour", StringArgumentType.word()).suggests(TONES)
                                .then(Commands.argument("rgb", StringArgumentType.word())
                                        .executes(ctx -> colour(ctx.getSource(), StringArgumentType.getString(ctx, "colour"),
                                                StringArgumentType.getString(ctx, "rgb"))))))
                .then(Commands.literal("height")
                        .then(Commands.argument("height", FloatArgumentType.floatArg(PlayerLook.MIN_HEIGHT, PlayerLook.MAX_HEIGHT))
                                .executes(ctx -> height(ctx.getSource(), FloatArgumentType.getFloat(ctx, "height")))))));
    }

    private static final SuggestionProvider<CommandSourceStack> PARTS = (ctx, b) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(PlayerLook.Slot.values()).map(PlayerLook.Slot::key), b);
    private static final SuggestionProvider<CommandSourceStack> TONES = (ctx, b) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(PlayerLook.Tone.values()).map(PlayerLook.Tone::key), b);
    private static final SuggestionProvider<CommandSourceStack> OPTIONS = (ctx, b) -> {
        PlayerLook.Slot slot = slot(StringArgumentType.getString(ctx, "part"));
        java.util.List<String> out = new java.util.ArrayList<>();
        if (slot != null) {
            for (int i = 0; i < slot.size(); i++) {
                out.add(slot.option(i));
            }
        }
        return SharedSuggestionProvider.suggest(out, b);
    };

    static PlayerLook.Slot slot(String key) {
        for (PlayerLook.Slot slot : PlayerLook.Slot.values()) {
            if (slot.key().equals(key)) {
                return slot;
            }
        }
        return null;
    }

    static int set(CommandSourceStack source, String part, String option) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        PlayerLook.Slot slot = slot(part);
        int index = slot == null ? -1 : slot.indexOf(option);
        if (index < 0) {
            return Cmd.fail(source, "command.noirm.creator.unknown", part, option);
        }
        PlayerLook look = data.creator().hasLook() ? data.creator().look() : PlayerLook.random(player.getRandom());
        CharacterCreator.setLook(player, data, look.set(slot, index));
        return Cmd.ok(source, "command.noirm.creator.set", part, option);
    }

    static int colour(CommandSourceStack source, String tone, String rgb) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        PlayerLook.Tone found = null;
        for (PlayerLook.Tone t : PlayerLook.Tone.values()) {
            if (t.key().equals(tone)) {
                found = t;
            }
        }
        int value;
        try {
            value = Integer.parseInt(rgb.startsWith("#") ? rgb.substring(1) : rgb, 16);
        } catch (NumberFormatException e) {
            value = -1;
        }
        if (found == null || value < 0 || value > 0xFFFFFF) {
            return Cmd.fail(source, "command.noirm.creator.unknown", tone, rgb);
        }
        PlayerLook look = data.creator().hasLook() ? data.creator().look() : PlayerLook.random(player.getRandom());
        CharacterCreator.setLook(player, data, look.setColor(found, value));
        return Cmd.ok(source, "command.noirm.creator.set", tone, String.format("#%06X", value));
    }

    static int open(CommandSourceStack source, boolean genetics) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        CharacterCreator.open(player, Cmd.data(source), genetics);
        return Cmd.ok(source, genetics ? "command.noirm.creator.reroll" : "command.noirm.creator.open");
    }

    static int skip(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        if (!CharacterCreator.skip(player, data)) {
            return Cmd.fail(source, "command.noirm.creator.not_open");
        }
        return Cmd.ok(source, "command.noirm.creator.skipped");
    }

    static int height(CommandSourceStack source, float height) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        PlayerLook look = data.creator().hasLook() ? data.creator().look() : PlayerLook.random(player.getRandom());
        CharacterCreator.setLook(player, data, look.setHeight(height));
        return Cmd.ok(source, "command.noirm.creator.height", String.format("%.2f", data.creator().height()));
    }
}

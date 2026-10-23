package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.creator.CharacterCreator;
import com.noirm.creator.PlayerLook;
import com.noirm.player.ShinobiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
                .then(Commands.literal("height")
                        .then(Commands.argument("height", FloatArgumentType.floatArg(PlayerLook.MIN_HEIGHT, PlayerLook.MAX_HEIGHT))
                                .executes(ctx -> height(ctx.getSource(), FloatArgumentType.getFloat(ctx, "height")))))));
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

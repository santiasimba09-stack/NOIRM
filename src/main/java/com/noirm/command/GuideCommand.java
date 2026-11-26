package com.noirm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.guide.FirstSteps;
import com.noirm.player.ShinobiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /noirm guide} commands (the new-player guide, see {@code guide.FirstSteps}):
 * <ul>
 *   <li>{@code /noirm guide} — a Shinobi Handbook, if you don't carry one</li>
 *   <li>{@code /noirm guide tips on|off} — the first-steps tips in chat</li>
 *   <li>{@code /noirm guide reset} — start the first steps over, as a new player (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class GuideCommand {
    private GuideCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("guide")
                .executes(ctx -> book(ctx.getSource()))
                .then(Commands.literal("tips")
                        .then(Commands.literal("on").executes(ctx -> tips(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> tips(ctx.getSource(), false))))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> reset(ctx.getSource())))));
    }

    private static int book(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (FirstSteps.hasHandbook(player)) {
            source.sendFailure(Component.translatable("command.noirm.guide.has_one"));
            return 0;
        }
        FirstSteps.giveHandbook(player);
        return Cmd.ok(source, "command.noirm.guide.given");
    }

    private static int tips(CommandSourceStack source, boolean on) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        data.guide().setTipsOff(!on);
        data.markDirty();
        return Cmd.ok(source, on ? "command.noirm.guide.tips_on" : "command.noirm.guide.tips_off");
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        data.guide().reset();
        data.markDirty();
        return Cmd.ok(source, "command.noirm.guide.reset");
    }
}

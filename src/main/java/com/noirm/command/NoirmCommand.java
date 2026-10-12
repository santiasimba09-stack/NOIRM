package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.BiConsumer;

/**
 * {@code /noirm} debug command, acting on the player who runs it.
 * <ul>
 *   <li>{@code /noirm stats}: show chakra and stamina (anyone)</li>
 *   <li>{@code /noirm set chakra|stamina <value>}: set the current value (cheats)</li>
 *   <li>{@code /noirm max chakra|stamina <value>}: set the maximum (cheats)</li>
 *   <li>{@code /noirm refill}: fill both (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class NoirmCommand {
    /** Permission level 2 = "cheats enabled" in single-player, operator on servers. */
    private static final int CHEAT_PERMISSION = 2;

    private NoirmCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("stats").executes(ctx -> showStats(ctx.getSource())))
                .then(Commands.literal("set").requires(s -> s.hasPermission(CHEAT_PERMISSION))
                        .then(valueArgument("chakra", ShinobiData::setChakra))
                        .then(valueArgument("stamina", ShinobiData::setStamina)))
                .then(Commands.literal("max").requires(s -> s.hasPermission(CHEAT_PERMISSION))
                        .then(valueArgument("chakra", ShinobiData::setMaxChakra))
                        .then(valueArgument("stamina", ShinobiData::setMaxStamina)))
                .then(Commands.literal("refill").requires(s -> s.hasPermission(CHEAT_PERMISSION))
                        .executes(ctx -> {
                            data(ctx.getSource()).refill();
                            return showStats(ctx.getSource());
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> valueArgument(String name, BiConsumer<ShinobiData, Float> setter) {
        return Commands.literal(name).then(Commands.argument("value", FloatArgumentType.floatArg(0f))
                .executes(ctx -> {
                    setter.accept(data(ctx.getSource()), FloatArgumentType.getFloat(ctx, "value"));
                    return showStats(ctx.getSource());
                }));
    }

    private static int showStats(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = data(source);
        source.sendSuccess(() -> Component.translatable("command.noirm.stats",
                Math.round(data.getChakra()), Math.round(data.getMaxChakra()),
                Math.round(data.getStamina()), Math.round(data.getMaxStamina())), false);
        return 1;
    }

    private static ShinobiData data(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return ShinobiCapability.get(player).orElseThrow(() -> new IllegalStateException("Player has no shinobi data"));
    }
}

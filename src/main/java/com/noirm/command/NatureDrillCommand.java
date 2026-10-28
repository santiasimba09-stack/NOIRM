package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.nature.NatureDrills;
import com.noirm.nature.NatureTraining;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Nature drills:
 * <ul>
 *   <li>{@code /noirm drills} — your drills: progress, hidden natures (once a chakra paper showed them), what each needs</li>
 *   <li>{@code /noirm nature train <nature> <0-100>} — set a drill's progress in percent; 100 finishes it (cheats)</li>
 *   <li>{@code /noirm nature hidden <nature>} — make that your (revealed) hidden nature (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class NatureDrillCommand {
    private static final SuggestionProvider<CommandSourceStack> BASIC = (ctx, builder) ->
            SharedSuggestionProvider.suggest(NatureDrills.TRAINABLE.stream().map(Nature::getSerializedName), builder);

    private NatureDrillCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("drills").executes(ctx -> show(ctx.getSource())))
                // "nature" is a cheats-only branch (PowersCommand); keep the same requirement so the merge can't loosen it.
                .then(Commands.literal("nature").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.literal("train").then(Commands.argument("nature", StringArgumentType.word()).suggests(BASIC)
                                .then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> train(ctx.getSource(), StringArgumentType.getString(ctx, "nature"),
                                                FloatArgumentType.getFloat(ctx, "percent"))))))
                        .then(Commands.literal("hidden").then(Commands.argument("nature", StringArgumentType.word()).suggests(BASIC)
                                .executes(ctx -> hidden(ctx.getSource(), StringArgumentType.getString(ctx, "nature")))))));
    }

    private static Nature basic(String name) {
        Nature n = NatureTraining.byName(name.toLowerCase(java.util.Locale.ROOT));
        return n != null && NatureDrills.TRAINABLE.contains(n) ? n : null;
    }

    private static int train(CommandSourceStack source, String name, float percent) throws CommandSyntaxException {
        Nature nature = basic(name);
        if (nature == null) {
            return Cmd.fail(source, "command.noirm.drill.unknown", name);
        }
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = Cmd.data(source);
        if (data.identity().affinity(nature) > 0f) {
            return Cmd.fail(source, "command.noirm.drill.owned", Component.translatable(nature.translationKey()));
        }
        NatureDrills.ensureRolled(player, data);
        NatureDrills.setShare(player, data, nature, percent / 100f);
        return Cmd.ok(source, "command.noirm.drill.set", Component.translatable(nature.translationKey()), Math.round(percent));
    }

    private static int hidden(CommandSourceStack source, String name) throws CommandSyntaxException {
        Nature nature = basic(name);
        if (nature == null) {
            return Cmd.fail(source, "command.noirm.drill.unknown", name);
        }
        ShinobiData data = Cmd.data(source);
        data.natureTraining().setHidden(List.of(nature));
        data.natureTraining().setRevealed(true);
        return Cmd.ok(source, "command.noirm.drill.hidden", Component.translatable(nature.translationKey()));
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = Cmd.data(source);
        NatureTraining t = data.natureTraining();
        source.sendSuccess(() -> Component.translatable("command.noirm.drill.title").withStyle(ChatFormatting.GOLD), false);
        for (Nature nature : NatureDrills.TRAINABLE) {
            MutableComponent line = Component.literal(" ").append(Component.translatable(nature.translationKey())
                    .withStyle(s -> s.withColor(nature.color()))).append(": ");
            if (data.identity().affinity(nature) > 0f) {
                line.append(Component.translatable("command.noirm.drill.have").withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.literal(Math.round(NatureDrills.share(data, nature) * 100) + "% ").withStyle(ChatFormatting.AQUA));
                if (t.revealed() && t.isHidden(nature)) {
                    line.append(Component.translatable("command.noirm.drill.hidden_tag").withStyle(ChatFormatting.LIGHT_PURPLE)).append(" ");
                }
                line.append(Component.translatable("drill.noirm." + nature.getSerializedName()).withStyle(ChatFormatting.GRAY));
                var pace = NatureDrills.pace(player, data, nature);
                if (pace.rate() > 0f) {
                    line.append(Component.translatable("command.noirm.drill.ready", String.format(java.util.Locale.ROOT, "%.1f", pace.rate()))
                            .withStyle(ChatFormatting.GREEN));
                }
            }
            source.sendSuccess(() -> line, false);
        }
        if (!t.revealed()) {
            source.sendSuccess(() -> Component.translatable("command.noirm.drill.unrevealed").withStyle(ChatFormatting.DARK_AQUA), false);
        }
        return 1;
    }
}

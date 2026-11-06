package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.arts.ArtsData;
import com.noirm.arts.ChakraShape;
import com.noirm.arts.FusionTrial;
import com.noirm.arts.ShapeTraining;
import com.noirm.clan.Nature;
import com.noirm.nature.NatureTraining;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiData;
import com.noirm.workshop.JutsuDesign;
import com.noirm.workshop.Workshop;
import com.noirm.workshop.WorkshopPackets;
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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Solo transformations and the jutsu workshop ({@code /noirm help arts}):
 * <ul>
 *   <li>{@code /noirm training [open]}, {@code /noirm shape …}, {@code /noirm fusion …}</li>
 *   <li>{@code /noirm workshop [open|create|forget|scroll]}</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ArtsCommand {
    private static final SuggestionProvider<CommandSourceStack> SHAPES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(ChakraShape.values()).map(ChakraShape::key), b);
    private static final SuggestionProvider<CommandSourceStack> NATURES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Nature.values()).map(Nature::getSerializedName), b);
    private static final SuggestionProvider<CommandSourceStack> FUSABLE = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Nature.values()).filter(FusionTrial::fusable).map(Nature::getSerializedName), b);

    private ArtsCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("training").executes(ctx -> training(ctx.getSource()))
                        .then(Commands.literal("open").executes(ctx -> open(ctx.getSource(), 1))))
                .then(Commands.literal("shape").executes(ctx -> shapes(ctx.getSource()))
                        .then(Commands.literal("train").then(Commands.argument("shape", StringArgumentType.word()).suggests(SHAPES)
                                .executes(ctx -> pick(ctx.getSource(), StringArgumentType.getString(ctx, "shape")))))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("shape", StringArgumentType.word()).suggests(SHAPES)
                                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, ShapeTraining.STAGES))
                                                .executes(ctx -> setStage(ctx.getSource(), StringArgumentType.getString(ctx, "shape"),
                                                        IntegerArgumentType.getInteger(ctx, "stage"))))))
                        .then(Commands.literal("progress").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("shape", StringArgumentType.word()).suggests(SHAPES)
                                        .then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100))
                                                .executes(ctx -> shapeProgress(ctx.getSource(), StringArgumentType.getString(ctx, "shape"),
                                                        FloatArgumentType.getFloat(ctx, "percent"))))))
                        .then(Commands.literal("fuse").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("shape", StringArgumentType.word()).suggests(SHAPES)
                                        .then(Commands.argument("nature", StringArgumentType.word()).suggests(NATURES)
                                                .executes(ctx -> fuse(ctx.getSource(), StringArgumentType.getString(ctx, "shape"),
                                                        StringArgumentType.getString(ctx, "nature")))))))
                .then(Commands.literal("fusion").executes(ctx -> trial(ctx.getSource()))
                        .then(Commands.literal("start").then(Commands.argument("nature", StringArgumentType.word()).suggests(FUSABLE)
                                .executes(ctx -> startTrial(ctx.getSource(), StringArgumentType.getString(ctx, "nature"), false))))
                        .then(Commands.literal("force").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("nature", StringArgumentType.word()).suggests(FUSABLE)
                                        .executes(ctx -> startTrial(ctx.getSource(), StringArgumentType.getString(ctx, "nature"), true))))
                        .then(Commands.literal("progress").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> trialProgress(ctx.getSource(), FloatArgumentType.getFloat(ctx, "percent")))))
                        .then(Commands.literal("fail").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> failTrial(ctx.getSource())))
                        .then(Commands.literal("abandon").executes(ctx -> abandon(ctx.getSource()))))
                .then(Commands.literal("workshop").executes(ctx -> creations(ctx.getSource()))
                        .then(Commands.literal("open").executes(ctx -> open(ctx.getSource(), 0)))
                        .then(Commands.literal("create").then(Commands.argument("nature", StringArgumentType.word()).suggests(NATURES)
                                .then(Commands.argument("shape", StringArgumentType.word()).suggests(SHAPES)
                                        .then(Commands.argument("size", IntegerArgumentType.integer(1, 5))
                                                .then(Commands.argument("speed", IntegerArgumentType.integer(1, 5))
                                                        .then(Commands.argument("power", IntegerArgumentType.integer(1, 5))
                                                                .then(Commands.argument("range", IntegerArgumentType.integer(1, 5))
                                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                                .executes(ctx -> create(ctx.getSource(),
                                                                                        StringArgumentType.getString(ctx, "nature"),
                                                                                        StringArgumentType.getString(ctx, "shape"),
                                                                                        IntegerArgumentType.getInteger(ctx, "size"),
                                                                                        IntegerArgumentType.getInteger(ctx, "speed"),
                                                                                        IntegerArgumentType.getInteger(ctx, "power"),
                                                                                        IntegerArgumentType.getInteger(ctx, "range"),
                                                                                        StringArgumentType.getString(ctx, "name")))))))))))
                        .then(Commands.literal("forget").then(Commands.argument("number", IntegerArgumentType.integer(1, Workshop.MAX_TOTAL))
                                .executes(ctx -> forget(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number")))))
                        .then(Commands.literal("scroll").then(Commands.argument("number", IntegerArgumentType.integer(1, Workshop.MAX_TOTAL))
                                .executes(ctx -> scroll(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number")))))));
    }

    private static Component name(Nature n) {
        return Component.translatable(n.translationKey()).withStyle(s -> s.withColor(n.color()));
    }

    private static int open(CommandSourceStack source, int screen) throws CommandSyntaxException {
        NoirmNetwork.sendToPlayer(source.getPlayerOrException(), new WorkshopPackets.Open(screen));
        return 1;
    }

    // ---------------------------------------------------------------- training overview

    private static int training(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        source.sendSuccess(() -> Component.translatable("command.noirm.arts.title").withStyle(ChatFormatting.GOLD), false);
        trialLine(source, data);
        shapes(source);
        return 1;
    }

    private static void trialLine(CommandSourceStack source, ShinobiData data) {
        ArtsData arts = data.arts();
        if (arts.trialNature() == null) {
            source.sendSuccess(() -> Component.translatable("command.noirm.fusion.none").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        FusionTrial.Stage stage = FusionTrial.current(arts);
        List<FusionTrial.Stage> all = FusionTrial.stages(arts.trialNature());
        source.sendSuccess(() -> Component.translatable("command.noirm.fusion.status", name(arts.trialNature()), arts.trialStage() + 1, all.size(),
                Component.translatable(FusionTrial.stepKey(stage)), Math.round(FusionTrial.share(arts) * 100), arts.trialFails())
                .withStyle(ChatFormatting.AQUA), false);
    }

    private static int trial(CommandSourceStack source) throws CommandSyntaxException {
        trialLine(source, Cmd.data(source));
        return 1;
    }

    // ---------------------------------------------------------------- shapes

    private static ChakraShape shape(String key) {
        return ChakraShape.byKey(key);
    }

    private static int shapes(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        ArtsData arts = data.arts();
        for (ChakraShape s : ChakraShape.values()) {
            MutableComponent line = Component.literal(" " + s.kanji() + " ").append(Component.translatable(s.translationKey())).append(": ");
            int stage = arts.stage(s);
            line.append(Component.translatable("command.noirm.shape.stage", stage, ShapeTraining.STAGES,
                    Math.round(ShapeTraining.share(arts, s) * 100)).withStyle(stage > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            for (Nature n : Nature.values()) {
                if (arts.fused(s, n)) {
                    line.append(" +").append(name(n));
                }
            }
            if (arts.activeShape() == s) {
                line.append(Component.literal(" ◀").withStyle(ChatFormatting.GOLD));
            }
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int pick(CommandSourceStack source, String key) throws CommandSyntaxException {
        ChakraShape s = shape(key);
        if (s == null) {
            return Cmd.fail(source, "command.noirm.shape.unknown", key);
        }
        Cmd.data(source).arts().setActiveShape(s);
        return Cmd.ok(source, "command.noirm.shape.picked", Component.translatable(s.translationKey()), s.focus().getDescription());
    }

    private static int setStage(CommandSourceStack source, String key, int stage) throws CommandSyntaxException {
        ChakraShape s = shape(key);
        if (s == null) {
            return Cmd.fail(source, "command.noirm.shape.unknown", key);
        }
        ShapeTraining.setStage(Cmd.data(source), s, stage);
        return Cmd.ok(source, "command.noirm.shape.set", Component.translatable(s.translationKey()), stage);
    }

    private static int shapeProgress(CommandSourceStack source, String key, float percent) throws CommandSyntaxException {
        ChakraShape s = shape(key);
        if (s == null) {
            return Cmd.fail(source, "command.noirm.shape.unknown", key);
        }
        ShapeTraining.setShare(source.getPlayerOrException(), Cmd.data(source), s, percent / 100f);
        return Cmd.ok(source, "command.noirm.shape.progress", Component.translatable(s.translationKey()), Math.round(percent));
    }

    private static int fuse(CommandSourceStack source, String key, String natureName) throws CommandSyntaxException {
        ChakraShape s = shape(key);
        Nature n = NatureTraining.byName(natureName.toLowerCase(Locale.ROOT));
        if (s == null || n == null) {
            return Cmd.fail(source, "command.noirm.shape.unknown", key + " / " + natureName);
        }
        ShinobiData data = Cmd.data(source);
        ShapeTraining.setStage(data, s, ShapeTraining.STAGES);
        ShapeTraining.fuse(source.getPlayerOrException(), data, s, n);
        return 1;
    }

    // ---------------------------------------------------------------- the fusion trial

    private static int startTrial(CommandSourceStack source, String natureName, boolean force) throws CommandSyntaxException {
        Nature n = NatureTraining.byName(natureName.toLowerCase(Locale.ROOT));
        if (n == null || !FusionTrial.fusable(n)) {
            return Cmd.fail(source, "command.noirm.fusion.unknown", natureName);
        }
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = Cmd.data(source);
        if (force) {
            data.arts().clearTrial();
            FusionTrial.begin(player, data, n);
            return 1;
        }
        return FusionTrial.start(player, data, n) ? 1 : 0;
    }

    private static int trialProgress(CommandSourceStack source, float percent) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        if (data.arts().trialNature() == null) {
            return Cmd.fail(source, "command.noirm.fusion.none");
        }
        FusionTrial.setShare(source.getPlayerOrException(), data, percent / 100f);
        return 1;
    }

    private static int failTrial(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        if (data.arts().trialNature() == null) {
            return Cmd.fail(source, "command.noirm.fusion.none");
        }
        FusionTrial.fail(source.getPlayerOrException(), data);
        return 1;
    }

    private static int abandon(CommandSourceStack source) throws CommandSyntaxException {
        return FusionTrial.abandon(source.getPlayerOrException(), Cmd.data(source)) ? 1 : Cmd.fail(source, "command.noirm.fusion.none");
    }

    // ---------------------------------------------------------------- the workshop

    private static int creations(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        List<JutsuDesign> list = data.creations().list();
        source.sendSuccess(() -> Component.translatable("command.noirm.workshop.title", list.size()).withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < list.size(); i++) {
            JutsuDesign d = list.get(i);
            Workshop.Stats s = Workshop.stats(d);
            int number = i + 1;
            int mastery = data.jutsu().masteryLevel(d.jutsuId());
            source.sendSuccess(() -> Component.literal(" " + number + ". ").append(Component.literal(d.name()).withStyle(st -> st.withColor(d.nature().color())))
                    .append(Component.translatable("command.noirm.workshop.line", s.rank(), Component.translatable(d.shape().translationKey()),
                            Math.round(s.cost()), mastery, Math.round(Workshop.misfireChance(mastery) * 100)).withStyle(ChatFormatting.GRAY)), false);
        }
        return list.size();
    }

    private static int create(CommandSourceStack source, String natureName, String shapeName, int size, int speed, int power, int range,
                              String name) throws CommandSyntaxException {
        Nature n = NatureTraining.byName(natureName.toLowerCase(Locale.ROOT));
        if (n == null) {
            return Cmd.fail(source, "command.noirm.shape.unknown", natureName);
        }
        return Workshop.create(source.getPlayerOrException(), Cmd.data(source), name, n, shapeName.toLowerCase(Locale.ROOT), size, speed, power, range)
                .design() != null ? 1 : 0;
    }

    private static JutsuDesign numbered(ShinobiData data, int number) {
        List<JutsuDesign> list = data.creations().list();
        return number >= 1 && number <= list.size() ? list.get(number - 1) : null;
    }

    private static int forget(CommandSourceStack source, int number) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        JutsuDesign d = numbered(data, number);
        if (d == null) {
            return Cmd.fail(source, "command.noirm.workshop.no_such", number);
        }
        return Workshop.delete(source.getPlayerOrException(), data, d.jutsuId()) ? 1 : 0;
    }

    private static int scroll(CommandSourceStack source, int number) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        JutsuDesign d = numbered(data, number);
        if (d == null) {
            return Cmd.fail(source, "command.noirm.workshop.no_such", number);
        }
        return Workshop.writeScroll(source.getPlayerOrException(), data, d.jutsuId()).error() == null ? 1 : 0;
    }
}

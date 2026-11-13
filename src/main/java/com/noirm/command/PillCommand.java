package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.pills.Apothecary;
import com.noirm.pills.Boost;
import com.noirm.pills.Grade;
import com.noirm.pills.PillData;
import com.noirm.pills.PillDefinition;
import com.noirm.pills.PillEffects;
import com.noirm.pills.PillRegistry;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;

/**
 * {@code /noirm pill} commands (foods and pills, see {@code docs/PILLS.md}):
 * <ul>
 *   <li>{@code /noirm pill} — your pharmacology, toxicity, running effects and cooldowns</li>
 *   <li>{@code /noirm pill recipes} — every recipe and whether you can brew it</li>
 *   <li>{@code /noirm pill give <pill> [grade] [count]} — pills in your bag (cheats)</li>
 *   <li>{@code /noirm pill take <pill> [grade]} — swallow one at once, ignoring its cooldown (cheats)</li>
 *   <li>{@code /noirm pill pharmacology <1-100>} — set the skill (cheats)</li>
 *   <li>{@code /noirm pill learn <recipe|all>} — learn a recipe as from a medical scroll (cheats)</li>
 *   <li>{@code /noirm pill clear} — end every effect and crash, clear toxicity and cooldowns (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class PillCommand {
    private PillCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("pill")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("recipes").executes(ctx -> recipes(ctx.getSource())))
                .then(Commands.literal("give").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("pill", StringArgumentType.word()).suggests((ctx, b) -> suggestPills(b))
                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "pill"), "standard", 1))
                                .then(Commands.argument("grade", StringArgumentType.word()).suggests((ctx, b) -> suggestGrades(b))
                                        .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "pill"),
                                                StringArgumentType.getString(ctx, "grade"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "pill"),
                                                        StringArgumentType.getString(ctx, "grade"), IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("take").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("pill", StringArgumentType.word()).suggests((ctx, b) -> suggestPills(b))
                                .executes(ctx -> take(ctx.getSource(), StringArgumentType.getString(ctx, "pill"), "standard"))
                                .then(Commands.argument("grade", StringArgumentType.word()).suggests((ctx, b) -> suggestGrades(b))
                                        .executes(ctx -> take(ctx.getSource(), StringArgumentType.getString(ctx, "pill"),
                                                StringArgumentType.getString(ctx, "grade"))))))
                .then(Commands.literal("pharmacology").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("level", FloatArgumentType.floatArg(PillData.START_PHARMACOLOGY, PillData.MAX_PHARMACOLOGY))
                                .executes(ctx -> pharmacology(ctx.getSource(), FloatArgumentType.getFloat(ctx, "level")))))
                .then(Commands.literal("learn").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("recipe", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),
                                                Cmd.shortIds(PillRegistry.recipes().stream().map(java.util.Map.Entry::getKey).toList())), b))
                                .executes(ctx -> learn(ctx.getSource(), StringArgumentType.getString(ctx, "recipe")))))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> clear(ctx.getSource())))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPills(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Cmd.shortIds(PillRegistry.pills().keySet()), builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestGrades(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(Grade.values()).map(Grade::key), builder);
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        PillData pills = data.pills();
        MutableComponent text = Component.translatable("command.noirm.pill.status", Math.round(pills.pharmacology()),
                Math.round(com.noirm.pills.Pharmacology.progress(pills) * 100), Math.round(pills.toxicity())).withStyle(ChatFormatting.GOLD);
        for (Boost b : pills.boosts()) {
            text.append("\n ").append(Component.translatable(b.crash ? "command.noirm.pill.crash_line" : "command.noirm.pill.effect_line",
                    Component.translatable(PillEffects.itemKey(b.source)), Component.translatable("pill.noirm.effect." + b.kind.key()),
                    (b.delay() > 0 ? b.delay() : b.ticksLeft()) / 20).withStyle(b.crash ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        pills.cooldowns().forEach((id, ticks) -> text.append("\n ").append(Component.translatable("command.noirm.pill.cooldown_line",
                Component.translatable(PillEffects.itemKey(id)), ticks / 20).withStyle(ChatFormatting.DARK_GRAY)));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int recipes(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        MutableComponent text = Component.translatable("command.noirm.pill.recipes").withStyle(ChatFormatting.GOLD);
        int n = 0;
        for (var e : PillRegistry.recipes()) {
            PillDefinition.Recipe r = e.getValue().recipe().get();
            Apothecary.State state = Apothecary.state(data, e.getKey(), r);
            Item item = ForgeRegistries.ITEMS.getValue(e.getValue().item());
            text.append("\n ").append(Component.translatable("command.noirm.pill.recipe_line",
                    item == null ? Component.literal(e.getKey().toString()) : Component.translatable(item.getDescriptionId()),
                    Math.round(r.minPharmacology()), Math.round(Apothecary.successChance(data, r) * 100),
                    Component.translatable("command.noirm.pill.state." + state.name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(state == Apothecary.State.READY ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            n++;
        }
        source.sendSuccess(() -> text, false);
        return n;
    }

    private static Grade grade(CommandSourceStack source, String name) {
        Grade grade = Grade.byName(name);
        if (grade == null) {
            source.sendFailure(Component.translatable("command.noirm.pill.unknown_grade", name));
        }
        return grade;
    }

    private static int give(CommandSourceStack source, String name, String gradeName, int count) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ResourceLocation id = Cmd.id(name);
        PillDefinition def = PillRegistry.pill(id);
        Grade grade = grade(source, gradeName);
        if (grade == null) {
            return 0;
        }
        Item item = def == null ? null : ForgeRegistries.ITEMS.getValue(def.item());
        if (item == null) {
            return Cmd.fail(source, "command.noirm.pill.unknown", name);
        }
        ItemStack stack = grade.apply(new ItemStack(item, count));
        Component shown = stack.getHoverName();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return Cmd.ok(source, "command.noirm.pill.given", count, shown);
    }

    private static int take(CommandSourceStack source, String name, String gradeName) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        ResourceLocation id = Cmd.id(name);
        Grade grade = grade(source, gradeName);
        if (grade == null) {
            return 0;
        }
        if (PillRegistry.pill(id) == null) {
            return Cmd.fail(source, "command.noirm.pill.unknown", name);
        }
        data.pills().setCooldown(id, 0);
        PillEffects.take(player, data, id, grade);
        return Cmd.ok(source, "command.noirm.pill.taken", Component.translatable(PillEffects.itemKey(PillRegistry.pill(id).item())),
                Math.round(data.pills().toxicity()));
    }

    private static int pharmacology(CommandSourceStack source, float level) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        data.pills().setPharmacology(level);
        data.pills().setPharmacologyXp(0f);
        com.noirm.progress.PillTrigger.pharmacology(Cmd.player(source), Math.round(data.pills().pharmacology()));
        return Cmd.ok(source, "command.noirm.pill.pharmacology", Math.round(data.pills().pharmacology()));
    }

    private static int learn(CommandSourceStack source, String name) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        int n = 0;
        for (var e : PillRegistry.recipes()) {
            if (name.equals("all") || e.getKey().equals(Cmd.id(name))) {
                if (data.pills().learn(e.getKey())) {
                    n++;
                }
            }
        }
        if (n == 0 && !name.equals("all") && PillRegistry.pill(Cmd.id(name)) == null) {
            return Cmd.fail(source, "command.noirm.pill.unknown", name);
        }
        return Cmd.ok(source, "command.noirm.pill.learned", n);
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        PillEffects.clearAll(player, data);
        data.pills().setToxicity(0f);
        data.pills().clearCooldowns();
        return Cmd.ok(source, "command.noirm.pill.cleared");
    }
}

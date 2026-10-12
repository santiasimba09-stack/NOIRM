package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.awakening.AwakeningSystem;
import com.noirm.biju.BijuData;
import com.noirm.biju.BijuSystem;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.clan.WorldClans;
import com.noirm.dojutsu.EyeData;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuBook;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /noirm} commands for powers, to test everything without waiting for the right clan:
 * <ul>
 *   <li>{@code /noirm clan} — your clan · {@code clan list} — clans known in this world</li>
 *   <li>{@code /noirm clan set <id>} · {@code clan reroll} · {@code clan new} (cheats)</li>
 *   <li>{@code /noirm nature add <nature>} — learn a nature as if by training (cheats)</li>
 *   <li>{@code /noirm jutsu list} · {@code jutsu learn <id>|all} · {@code jutsu equip <slot> <id>} (cheats)</li>
 *   <li>{@code /noirm sharingan <0-3>} · {@code resolve <0-100>} · {@code awaken} (cheats)</li>
 *   <li>{@code /noirm biju seal <tails>} · {@code biju release} (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class PowersCommand {
    private static final int CHEATS = 2;

    private static final SuggestionProvider<CommandSourceStack> CLANS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(WorldClans.get(ctx.getSource().getServer()).allClans().stream().map(Clan::id), builder);
    private static final SuggestionProvider<CommandSourceStack> JUTSU = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(JutsuData.all().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> NATURES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Nature.values()).map(Nature::getSerializedName), builder);

    private PowersCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("clan")
                        .executes(ctx -> showClan(ctx.getSource()))
                        .then(Commands.literal("list").executes(ctx -> listClans(ctx.getSource())))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(CLANS)
                                        .executes(ctx -> setClan(ctx, StringArgumentType.getString(ctx, "clan")))))
                        .then(Commands.literal("reroll").requires(s -> s.hasPermission(CHEATS))
                                .executes(ctx -> assign(ctx.getSource(), WorldClans.get(ctx.getSource().getServer())
                                        .rollForNewcomer(ctx.getSource().getPlayerOrException().getRandom()))))
                        .then(Commands.literal("new").requires(s -> s.hasPermission(CHEATS))
                                .executes(ctx -> assign(ctx.getSource(), WorldClans.get(ctx.getSource().getServer())
                                        .generate(ctx.getSource().getPlayerOrException().getRandom())))))
                .then(Commands.literal("nature").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.literal("add").then(Commands.argument("nature", StringArgumentType.word()).suggests(NATURES)
                                .executes(ctx -> addNature(ctx, StringArgumentType.getString(ctx, "nature"))))))
                .then(Commands.literal("jutsu")
                        .then(Commands.literal("list").executes(ctx -> listJutsu(ctx.getSource())))
                        .then(Commands.literal("learn").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.literal("all").executes(ctx -> learnAll(ctx.getSource())))
                                .then(Commands.argument("jutsu", ResourceLocationArgument.id()).suggests(JUTSU)
                                        .executes(ctx -> learn(ctx.getSource(), ResourceLocationArgument.getId(ctx, "jutsu")))))
                        .then(Commands.literal("equip").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, JutsuBook.SLOTS))
                                        .then(Commands.argument("jutsu", ResourceLocationArgument.id()).suggests(JUTSU)
                                                .executes(ctx -> equip(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot") - 1,
                                                        ResourceLocationArgument.getId(ctx, "jutsu")))))))
                .then(Commands.literal("sharingan").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("tomoe", IntegerArgumentType.integer(0, EyeData.MAX_STAGE))
                                .executes(ctx -> setSharingan(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tomoe")))))
                .then(Commands.literal("resolve").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                .executes(ctx -> setResolve(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
                .then(Commands.literal("awaken").requires(s -> s.hasPermission(CHEATS))
                        .executes(ctx -> awaken(ctx.getSource())))
                .then(Commands.literal("biju").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.literal("seal").then(Commands.argument("tails", IntegerArgumentType.integer(1, BijuData.MAX_TAILS))
                                .executes(ctx -> seal(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tails")))))
                        .then(Commands.literal("release").executes(ctx -> seal(ctx.getSource(), 0)))));
    }

    // ------------------------------------------------------------ clans

    private static int showClan(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = data(source);
        source.sendSuccess(() -> ClanSystem.describe(data.identity()), false);
        return 1;
    }

    private static int listClans(CommandSourceStack source) {
        MutableComponent text = Component.translatable("command.noirm.clan.list");
        for (Clan clan : WorldClans.get(source.getServer()).allClans()) {
            text.append("\n - ").append(Component.literal(clan.name()).withStyle(clan.canon() ? ChatFormatting.RED : ChatFormatting.GOLD))
                    .append(Component.literal(" (" + clan.id() + ")").withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int setClan(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        Clan clan = WorldClans.get(ctx.getSource().getServer()).find(id).orElse(null);
        if (clan == null) {
            ctx.getSource().sendFailure(Component.translatable("command.noirm.clan.unknown", id));
            return 0;
        }
        return assign(ctx.getSource(), clan);
    }

    private static int assign(CommandSourceStack source, Clan clan) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = data(source);
        ClanSystem.assign(player, data, clan);
        PowerVisuals.broadcast(player);
        source.sendSuccess(() -> ClanSystem.describe(data.identity()), false);
        return 1;
    }

    private static int addNature(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        Nature nature = Arrays.stream(Nature.values()).filter(n -> n.getSerializedName().equals(name.toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        if (nature == null) {
            ctx.getSource().sendFailure(Component.translatable("command.noirm.nature.unknown", name));
            return 0;
        }
        ShinobiData data = data(ctx.getSource());
        data.identity().addNature(nature, Clan.TRAINED_AFFINITY);
        JutsuData.startersFor(nature).forEach(id -> data.jutsu().learn(id, false));
        data.markPowersDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.noirm.clan.natures", ClanSystem.natures(data.identity())), false);
        return 1;
    }

    // ------------------------------------------------------------ jutsu

    private static int listJutsu(CommandSourceStack source) throws CommandSyntaxException {
        JutsuBook book = data(source).jutsu();
        MutableComponent text = Component.translatable("command.noirm.jutsu.list");
        for (ResourceLocation id : JutsuData.all().keySet().stream().sorted().toList()) {
            boolean known = book.knows(id);
            text.append("\n ").append(Component.literal(known ? "✔ " : "· ").withStyle(known ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(Sharingan.jutsuKey(id)))
                    .append(Component.literal(" " + id + (known ? " ★" + book.masteryLevel(id) : "")).withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int learn(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        if (JutsuData.get(id) == null) {
            source.sendFailure(Component.translatable("command.noirm.jutsu.unknown", id.toString()));
            return 0;
        }
        ShinobiData data = data(source);
        data.jutsu().learn(id, false);
        data.jutsu().equipIfRoom(id);
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.jutsu.learned", Component.translatable(Sharingan.jutsuKey(id))), false);
        return 1;
    }

    private static int learnAll(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = data(source);
        JutsuData.all().keySet().forEach(id -> data.jutsu().learn(id, false));
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.jutsu.learned_all", JutsuData.all().size()), false);
        return 1;
    }

    private static int equip(CommandSourceStack source, int slot, ResourceLocation id) throws CommandSyntaxException {
        ShinobiData data = data(source);
        if (!data.jutsu().equip(slot, id)) {
            source.sendFailure(Component.translatable("command.noirm.jutsu.not_known", id.toString()));
            return 0;
        }
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.jutsu.equipped", Component.translatable(Sharingan.jutsuKey(id)), slot + 1), false);
        return 1;
    }

    // ------------------------------------------------------------ eyes, awakening, biju

    private static int setSharingan(CommandSourceStack source, int tomoe) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = data(source);
        data.eyes().setSharinganStage(0);
        data.eyes().setSharinganXp(0);
        for (int i = 0; i < tomoe; i++) {
            Sharingan.advance(player, data, "message.noirm.sharingan.tomoe_growth");
        }
        data.markPowersDirty();
        PowerVisuals.broadcast(player);
        return 1;
    }

    private static int setResolve(CommandSourceStack source, float value) throws CommandSyntaxException {
        ShinobiData data = data(source);
        data.awakening().setResolve(value);
        data.awakening().setLastAwakening(-1);
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.resolve", Math.round(value)), false);
        return 1;
    }

    private static int awaken(CommandSourceStack source) throws CommandSyntaxException {
        AwakeningSystem.awaken(source.getPlayerOrException(), data(source));
        return 1;
    }

    private static int seal(CommandSourceStack source, int tails) throws CommandSyntaxException {
        BijuSystem.seal(source.getPlayerOrException(), data(source), tails);
        source.sendSuccess(() -> tails == 0 ? Component.translatable("command.noirm.biju.released")
                : Component.translatable("command.noirm.biju.sealed", tails), false);
        return 1;
    }

    private static ShinobiData data(CommandSourceStack source) throws CommandSyntaxException {
        return ShinobiCapability.get(source.getPlayerOrException()).orElseThrow(() -> new IllegalStateException("No shinobi data"));
    }
}

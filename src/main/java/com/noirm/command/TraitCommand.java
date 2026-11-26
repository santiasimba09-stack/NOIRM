package com.noirm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.player.ShinobiData;
import com.noirm.traits.Perks;
import com.noirm.traits.Talent;
import com.noirm.traits.Talents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;

/**
 * Clan perks and personal talents (docs/CLANS.md, docs/TRAITS.md).
 * <ul>
 *   <li>{@code /noirm clan perks [clan]} — what your blood (or another clan's) gives, plainly</li>
 *   <li>{@code /noirm trait list [all]} — your talents (or every talent there is)</li>
 *   <li>{@code /noirm trait give|take <talent>} — add or remove one (cheats)</li>
 *   <li>{@code /noirm trait reroll} — roll your talents again, biased by your clan (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class TraitCommand {
    private static final SuggestionProvider<CommandSourceStack> TALENTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Talents.all().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> CLANS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(WorldClans.get(ctx.getSource().getServer()).allClans().stream().map(Clan::id), builder);

    private TraitCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("clan").then(Commands.literal("perks")
                        .executes(ctx -> perks(ctx.getSource(), null))
                        .then(Commands.argument("clan", StringArgumentType.word()).suggests(CLANS)
                                .executes(ctx -> perks(ctx.getSource(), StringArgumentType.getString(ctx, "clan"))))))
                .then(Commands.literal("trait")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource(), false))
                                .then(Commands.literal("all").executes(ctx -> list(ctx.getSource(), true))))
                        .then(Commands.literal("give").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("talent", StringArgumentType.word()).suggests(TALENTS)
                                        .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "talent")))))
                        .then(Commands.literal("take").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("talent", StringArgumentType.word()).suggests(TALENTS)
                                        .executes(ctx -> take(ctx.getSource(), StringArgumentType.getString(ctx, "talent")))))
                        .then(Commands.literal("reroll").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .executes(ctx -> reroll(ctx.getSource())))));
    }

    /** {@code /noirm clan perks [clan]}. */
    static int perks(CommandSourceStack source, String clanId) throws CommandSyntaxException {
        String name;
        Map<String, Float> perks;
        java.util.List<net.minecraft.resources.ResourceLocation> jutsu;
        String id;
        java.util.List<String> packages = java.util.List.of();
        boolean canon;
        if (clanId == null) {
            ShinobiData data = Cmd.data(source);
            name = data.identity().clanName();
            perks = data.identity().perks();
            jutsu = data.identity().clanJutsu();
            id = data.identity().clanId();
            canon = data.identity().isCanon();
            packages = WorldClans.get(source.getServer()).find(id).map(Clan::packages).orElse(java.util.List.of());
        } else {
            Optional<Clan> clan = WorldClans.get(source.getServer()).find(clanId);
            if (clan.isEmpty()) {
                return Cmd.fail(source, "command.noirm.clan.unknown", clanId);
            }
            name = clan.get().name();
            perks = clan.get().perks();
            jutsu = clan.get().clanJutsu();
            id = clan.get().id();
            canon = clan.get().canon();
            packages = clan.get().packages();
        }
        MutableComponent text = Component.translatable("command.noirm.clan.perks", Component.literal(name).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.BOLD);
        if (canon) {
            text.append("\n ").append(Component.translatable("clan.noirm." + id + ".desc")
                    .withStyle(s -> s.withBold(false).withItalic(true).withColor(ChatFormatting.GRAY)));
        }
        for (String pkg : packages) {
            text.append("\n ").append(Component.translatable("clan_perks.noirm." + pkg).withStyle(s -> s.withBold(false).withColor(ChatFormatting.AQUA)))
                    .append(Component.literal(" — ").withStyle(s -> s.withBold(false).withColor(ChatFormatting.DARK_GRAY)))
                    .append(Component.translatable("clan_perks.noirm." + pkg + ".desc").withStyle(s -> s.withBold(false).withColor(ChatFormatting.GRAY)));
        }
        if (perks.isEmpty()) {
            text.append("\n ").append(Component.translatable("command.noirm.clan.no_perks").withStyle(s -> s.withBold(false).withColor(ChatFormatting.GRAY)));
        }
        for (Component line : Perks.lines(perks)) {
            text.append("\n • ").append(line.copy().withStyle(s -> s.withBold(false)));
        }
        if (!jutsu.isEmpty()) {
            text.append("\n ").append(Component.translatable("message.noirm.clan.clan_jutsu", Perks.jutsuNames(jutsu))
                    .withStyle(s -> s.withBold(false)));
        }
        source.sendSuccess(() -> text, false);
        return Math.max(1, perks.size());
    }

    /** {@code /noirm trait list [all]}. */
    static int list(CommandSourceStack source, boolean all) throws CommandSyntaxException {
        MutableComponent text;
        if (all) {
            text = Component.translatable("command.noirm.trait.all", Talents.all().size()).withStyle(ChatFormatting.BOLD);
            for (Map.Entry<String, Talent> e : Talents.all().entrySet()) {
                text.append("\n • ").append(Perks.talentNames(java.util.List.of(e.getKey())).withStyle(s -> s.withBold(false)))
                        .append(Component.literal(" (" + e.getKey() + ")").withStyle(s -> s.withBold(false).withColor(ChatFormatting.DARK_GRAY)));
            }
            source.sendSuccess(() -> text, false);
            return Talents.all().size();
        }
        ShinobiData data = Cmd.data(source);
        text = Component.translatable("command.noirm.trait.yours").withStyle(ChatFormatting.BOLD);
        for (String id : data.talents().talents()) {
            Talent t = Talents.get(id);
            text.append("\n • ").append(Perks.talentNames(java.util.List.of(id)).withStyle(s -> s.withBold(false)))
                    .append(Component.literal(" — ").withStyle(s -> s.withBold(false).withColor(ChatFormatting.DARK_GRAY)))
                    .append(Component.translatable(Talent.descriptionKey(id)).withStyle(s -> s.withBold(false).withColor(ChatFormatting.GRAY)));
            if (t != null) {
                text.append(Component.literal("\n   ").withStyle(s -> s.withBold(false))).append(Perks.inline(t.modifiers()).withStyle(s -> s.withBold(false)));
            }
        }
        if (data.talents().talents().isEmpty()) {
            text.append("\n ").append(Component.translatable("talent.noirm.none").withStyle(s -> s.withBold(false).withColor(ChatFormatting.GRAY)));
        }
        source.sendSuccess(() -> text, false);
        return Math.max(1, data.talents().talents().size());
    }

    static int give(CommandSourceStack source, String id) throws CommandSyntaxException {
        if (Talents.get(id) == null) {
            return Cmd.fail(source, "command.noirm.trait.unknown", id);
        }
        ShinobiData data = Cmd.data(source);
        if (!data.talents().add(id)) {
            return Cmd.fail(source, "command.noirm.trait.already", Component.translatable(Talent.translationKey(id)));
        }
        data.markDirty();
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.trait.given", Component.translatable(Talent.translationKey(id)));
    }

    static int take(CommandSourceStack source, String id) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        if (!data.talents().remove(id)) {
            return Cmd.fail(source, "command.noirm.trait.missing", id);
        }
        data.markDirty();
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.trait.taken", Component.translatable(Talent.translationKey(id)));
    }

    static int reroll(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        Map<String, Float> bias = WorldClans.get(source.getServer()).find(data.identity().clanId()).map(Clan::talentBias).orElse(Map.of());
        data.talents().set(Talents.roll(Cmd.player(source).getRandom(), bias));
        data.markDirty();
        data.markPowersDirty();
        return Cmd.ok(source, "message.noirm.talents.yours", Perks.talentNames(data.talents().talents()));
    }
}

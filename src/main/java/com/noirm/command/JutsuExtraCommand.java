package com.noirm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuBook;
import com.noirm.jutsu.JutsuData;
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
import java.util.List;
import java.util.Locale;

/**
 * More jutsu and nature commands (the basics live in {@link PowersCommand}).
 * <ul>
 *   <li>{@code /noirm jutsu loadout} — your four slots</li>
 *   <li>{@code /noirm jutsu learn nature <nature>} · {@code jutsu forget <id>|all} · {@code jutsu unequip <slot>|all} (cheats)</li>
 *   <li>{@code /noirm jutsu mastery all <level>} · {@code jutsu ready} — no cooldowns (cheats)</li>
 *   <li>{@code /noirm nature list} · {@code nature remove <nature>} · {@code nature all} (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class JutsuExtraCommand {
    static final SuggestionProvider<CommandSourceStack> NATURES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Nature.values()).map(Nature::getSerializedName), builder);
    private static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(Cmd.data(ctx.getSource()).jutsu().known(), builder);

    private JutsuExtraCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("jutsu")
                        .then(Commands.literal("loadout").executes(ctx -> loadout(ctx.getSource())))
                        .then(Commands.literal("learn").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.literal("nature").then(Commands.argument("nature", StringArgumentType.word()).suggests(NATURES)
                                        .executes(ctx -> learnNature(ctx.getSource(), StringArgumentType.getString(ctx, "nature"))))))
                        .then(Commands.literal("forget").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.literal("all").executes(ctx -> forgetAll(ctx.getSource())))
                                .then(Commands.argument("jutsu", ResourceLocationArgument.id()).suggests(KNOWN)
                                        .executes(ctx -> forget(ctx.getSource(), ResourceLocationArgument.getId(ctx, "jutsu")))))
                        .then(Commands.literal("unequip").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.literal("all").executes(ctx -> unequip(ctx.getSource(), -1)))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, JutsuBook.SLOTS))
                                        .executes(ctx -> unequip(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot") - 1))))
                        .then(Commands.literal("mastery").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.literal("all").then(Commands.argument("level", IntegerArgumentType.integer(0, JutsuBook.MAX_MASTERY))
                                        .executes(ctx -> masteryAll(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level"))))))
                        .then(Commands.literal("ready").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> ready(ctx.getSource()))))
                .then(Commands.literal("nature").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.literal("list").executes(ctx -> listNatures(ctx.getSource())))
                        .then(Commands.literal("all").executes(ctx -> allNatures(ctx.getSource())))
                        .then(Commands.literal("remove").then(Commands.argument("nature", StringArgumentType.word()).suggests(NATURES)
                                .executes(ctx -> removeNature(ctx.getSource(), StringArgumentType.getString(ctx, "nature")))))));
    }

    static Nature nature(String name) {
        return Arrays.stream(Nature.values()).filter(n -> n.getSerializedName().equals(name.toLowerCase(Locale.ROOT))).findFirst().orElse(null);
    }

    static Component jutsuName(ResourceLocation id) {
        return Component.translatable(Sharingan.jutsuKey(id));
    }

    // ------------------------------------------------------------ jutsu

    static int loadout(CommandSourceStack source) throws CommandSyntaxException {
        JutsuBook book = Cmd.data(source).jutsu();
        MutableComponent text = Component.translatable("command.noirm.jutsu.loadout").withStyle(ChatFormatting.GOLD);
        for (int slot = 0; slot < JutsuBook.SLOTS; slot++) {
            ResourceLocation id = book.slot(slot);
            text.append("\n ").append(Component.literal((slot + 1) + " ").withStyle(ChatFormatting.AQUA));
            if (id == null) {
                text.append(Component.translatable("command.noirm.jutsu.empty_slot").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                text.append(jutsuName(id)).append(Component.literal(" ★" + book.masteryLevel(id)).withStyle(ChatFormatting.YELLOW));
                int cooldown = book.cooldown(id);
                if (cooldown > 0) {
                    text.append(Component.literal(" " + (cooldown + 19) / 20 + "s").withStyle(ChatFormatting.RED));
                }
            }
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    static int learnNature(CommandSourceStack source, String name) throws CommandSyntaxException {
        Nature nature = nature(name);
        if (nature == null) {
            return Cmd.fail(source, "command.noirm.nature.unknown", name);
        }
        ShinobiData data = Cmd.data(source);
        List<ResourceLocation> ids = JutsuData.all().entrySet().stream()
                .filter(e -> e.getValue().nature().orElse(null) == nature).map(java.util.Map.Entry::getKey).sorted().toList();
        ids.forEach(id -> data.jutsu().learn(id, false));
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.jutsu.learned_nature", ids.size(), Component.translatable(nature.translationKey()));
    }

    static int forget(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        if (!data.jutsu().forget(id)) {
            return Cmd.fail(source, "command.noirm.jutsu.not_known", id.toString());
        }
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.jutsu.forgot", jutsuName(id));
    }

    static int forgetAll(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        int count = data.jutsu().known().size();
        data.jutsu().clear();
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.jutsu.forgot_all", count);
    }

    static int unequip(CommandSourceStack source, int slot) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        for (int i = 0; i < JutsuBook.SLOTS; i++) {
            if (slot < 0 || i == slot) {
                data.jutsu().equip(i, null);
            }
        }
        data.markPowersDirty();
        return loadout(source);
    }

    static int masteryAll(CommandSourceStack source, int level) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        int xp = level <= 0 ? 0 : JutsuBook.xpForLevel(level);
        for (ResourceLocation id : List.copyOf(data.jutsu().known())) {
            data.jutsu().addXp(id, xp - data.jutsu().xp(id));
            NatureSystem.checkMasteryUnlocks(player, data, id);
        }
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.jutsu.mastery_all", data.jutsu().known().size(), level);
    }

    static int ready(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        List.copyOf(data.jutsu().known()).forEach(id -> data.jutsu().setCooldown(id, 0));
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.jutsu.ready");
    }

    // ------------------------------------------------------------ natures

    static int listNatures(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        MutableComponent text = Component.translatable("command.noirm.nature.list").withStyle(ChatFormatting.GOLD);
        for (Nature nature : Nature.values()) {
            NatureSystem.Status status = NatureSystem.status(data, nature);
            float affinity = data.identity().affinity(nature);
            ChatFormatting color = switch (status) {
                case OWNED -> ChatFormatting.GREEN;
                case AVAILABLE -> ChatFormatting.YELLOW;
                default -> ChatFormatting.DARK_GRAY;
            };
            text.append("\n ").append(Component.translatable(nature.translationKey()).withStyle(color))
                    .append(Component.literal(" " + nature.getSerializedName() + " · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("command.noirm.nature.status." + status.name().toLowerCase(Locale.ROOT)).withStyle(color));
            if (affinity > 0f) {
                text.append(Component.literal(" (" + Math.round(affinity * 100) + "%)").withStyle(ChatFormatting.GRAY));
            }
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    static int allNatures(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        for (Nature nature : Nature.values()) {
            NatureSystem.grant(data, nature, Clan.TRAINED_AFFINITY);
        }
        data.markPowersDirty();
        return Cmd.ok(source, "message.noirm.clan.natures", ClanSystem.natures(data.identity()));
    }

    static int removeNature(CommandSourceStack source, String name) throws CommandSyntaxException {
        Nature nature = nature(name);
        if (nature == null) {
            return Cmd.fail(source, "command.noirm.nature.unknown", name);
        }
        ShinobiData data = Cmd.data(source);
        if (data.identity().natures().remove(nature) == null) {
            return Cmd.fail(source, "command.noirm.nature.not_owned", Component.translatable(nature.translationKey()));
        }
        data.markPowersDirty();
        return Cmd.ok(source, "message.noirm.clan.natures", ClanSystem.natures(data.identity()));
    }
}

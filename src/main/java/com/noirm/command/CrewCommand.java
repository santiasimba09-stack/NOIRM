package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.ally.Order;
import com.noirm.ally.Tactics;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /noirm crew}: your allies, all at once.
 * <ul>
 *   <li>{@code /noirm crew} — who's in it: health, respect, order, natures, lesson</li>
 *   <li>{@code /noirm crew order|range|jutsu <value>} — give every crew mate the same order or tactics</li>
 *   <li>{@code /noirm crew recruit} — recruit the ninja you're looking at (cheats)</li>
 *   <li>{@code /noirm crew summon|heal|respect|teach|dismiss} (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CrewCommand {
    private CrewCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("crew")
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("order").then(Commands.argument("order", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(Order.values()), b))
                        .executes(ctx -> order(ctx.getSource(), StringArgumentType.getString(ctx, "order")))))
                .then(Commands.literal("range").then(Commands.argument("range", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(Tactics.Range.values()), b))
                        .executes(ctx -> range(ctx.getSource(), StringArgumentType.getString(ctx, "range")))))
                .then(Commands.literal("jutsu").then(Commands.argument("use", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(Tactics.JutsuUse.values()), b))
                        .executes(ctx -> jutsuUse(ctx.getSource(), StringArgumentType.getString(ctx, "use")))))
                .then(Commands.literal("recruit").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> recruit(ctx.getSource())))
                .then(Commands.literal("summon").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> summon(ctx.getSource())))
                .then(Commands.literal("heal").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> heal(ctx.getSource())))
                .then(Commands.literal("respect").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                .executes(ctx -> respect(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
                .then(Commands.literal("teach").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("nature", StringArgumentType.word()).suggests(JutsuExtraCommand.NATURES)
                                .executes(ctx -> teach(ctx.getSource(), StringArgumentType.getString(ctx, "nature")))))
                .then(Commands.literal("dismiss").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> dismiss(ctx.getSource())))));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(v -> v.name().toLowerCase(Locale.ROOT)).toList();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The crew mates loaded in the player's world (unloaded ones can't be reached). */
    static List<SparringNinja> members(ServerPlayer player) {
        List<SparringNinja> found = new ArrayList<>();
        ShinobiData data = com.noirm.player.ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return found;
        }
        for (UUID id : data.crew()) {
            for (ServerLevel level : player.server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity instanceof SparringNinja ninja && ninja.isAlive()) {
                    found.add(ninja);
                    break;
                }
            }
        }
        return found;
    }

    /** Runs {@code action} on every crew mate and says how many it reached. */
    private static int each(CommandSourceStack source, String doneKey, Consumer<SparringNinja> action, Object... args) throws CommandSyntaxException {
        List<SparringNinja> crew = members(Cmd.player(source));
        if (crew.isEmpty()) {
            return Cmd.fail(source, "command.noirm.crew.empty");
        }
        crew.forEach(action);
        Object[] all = new Object[args.length + 1];
        all[0] = crew.size();
        System.arraycopy(args, 0, all, 1, args.length);
        return Cmd.ok(source, doneKey, all);
    }

    static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        List<SparringNinja> crew = members(player);
        MutableComponent text = Component.translatable("command.noirm.crew.header", data.crew().size(), Crew.maxSize(player))
                .withStyle(ChatFormatting.GOLD);
        for (SparringNinja ninja : crew) {
            text.append("\n ").append(ninja.getDisplayName().copy().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" ♥" + Math.round(ninja.getHealth()) + "/" + Math.round(ninja.getMaxHealth())).withStyle(ChatFormatting.RED))
                    .append(Component.translatable("command.noirm.crew.line", Math.round(ninja.respect()),
                            Component.translatable(ninja.order().key()),
                            Math.round(ninja.distanceTo(player))).withStyle(ChatFormatting.GRAY));
            Nature lesson = ninja.allyState().lesson();
            if (lesson != null) {
                text.append(Component.translatable("command.noirm.crew.lesson", Component.translatable(lesson.translationKey()))
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        int away = data.crew().size() - crew.size();
        if (away > 0) {
            text.append("\n ").append(Component.translatable("command.noirm.crew.away", away).withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> text, false);
        return crew.size();
    }

    static int order(CommandSourceStack source, String name) throws CommandSyntaxException {
        Order order = parse(Order.class, name);
        if (order == null) {
            return Cmd.fail(source, "command.noirm.crew.bad_value", name);
        }
        return each(source, "command.noirm.crew.ordered", ninja -> ninja.setOrder(order), Component.translatable(order.key()));
    }

    static int range(CommandSourceStack source, String name) throws CommandSyntaxException {
        Tactics.Range range = parse(Tactics.Range.class, name);
        if (range == null) {
            return Cmd.fail(source, "command.noirm.crew.bad_value", name);
        }
        return each(source, "command.noirm.crew.tactics", ninja -> ninja.allyState().setRange(range), name.toLowerCase(Locale.ROOT));
    }

    static int jutsuUse(CommandSourceStack source, String name) throws CommandSyntaxException {
        Tactics.JutsuUse use = parse(Tactics.JutsuUse.class, name);
        if (use == null) {
            return Cmd.fail(source, "command.noirm.crew.bad_value", name);
        }
        return each(source, "command.noirm.crew.tactics", ninja -> ninja.allyState().setJutsuUse(use), name.toLowerCase(Locale.ROOT));
    }

    static int recruit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        SparringNinja ninja = Cmd.lookedAt(player, SparringNinja.class, 24);
        if (ninja == null) {
            return Cmd.fail(source, "command.noirm.crew.no_target");
        }
        if (ninja.inCrew()) {
            return Cmd.fail(source, "command.noirm.crew.already", ninja.getDisplayName());
        }
        ninja.setTarget(null);
        return Crew.recruit(player, ninja) ? 1 : 0;
    }

    static int summon(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        return each(source, "command.noirm.crew.summoned", ninja -> {
            if (ninja.level() != player.level()) {
                ninja.changeDimension(player.serverLevel());
            }
            ninja.teleportTo(player.getX() + ninja.getRandom().nextGaussian(), player.getY(), player.getZ() + ninja.getRandom().nextGaussian());
            ninja.getNavigation().stop();
        });
    }

    static int heal(CommandSourceStack source) throws CommandSyntaxException {
        return each(source, "command.noirm.crew.healed", ninja -> {
            ninja.setHealth(ninja.getMaxHealth());
            ninja.clearFire();
            ninja.removeAllEffects();
            ninja.setChakra(ninja.maxChakra());
        });
    }

    static int respect(CommandSourceStack source, float value) throws CommandSyntaxException {
        return each(source, "command.noirm.crew.respect", ninja -> ninja.setRespect(value), Math.round(value));
    }

    static int teach(CommandSourceStack source, String name) throws CommandSyntaxException {
        Nature nature = JutsuExtraCommand.nature(name);
        if (nature == null) {
            return Cmd.fail(source, "command.noirm.nature.unknown", name);
        }
        return each(source, "command.noirm.crew.taught", ninja -> {
            ninja.learnNature(nature, Clan.TRAINED_AFFINITY);
            com.noirm.jutsu.JutsuData.startersFor(nature).forEach(ninja::teach);
            if (ninja.allyState().lesson() == nature) {
                com.noirm.ally.NatureTeaching.stop(ninja);
            }
        }, Component.translatable(nature.translationKey()));
    }

    static int dismiss(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        int count = each(source, "command.noirm.crew.dismissed", SparringNinja::leaveCrew);
        data.crew().clear();
        return count;
    }
}

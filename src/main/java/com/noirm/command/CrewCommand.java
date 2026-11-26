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
                .then(Commands.literal("role").then(Commands.argument("job", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(com.noirm.ally.Occupation.values()), b))
                        .executes(ctx -> role(ctx.getSource(), StringArgumentType.getString(ctx, "job")))))
                .then(Commands.literal("behaviour").then(Commands.argument("behaviour", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(com.noirm.ally.Behaviour.values()), b))
                        .executes(ctx -> behaviour(ctx.getSource(), StringArgumentType.getString(ctx, "behaviour")))))
                .then(Commands.literal("come").executes(ctx -> come(ctx.getSource(), false)))
                .then(Commands.literal("fallback").executes(ctx -> come(ctx.getSource(), true)))
                .then(Commands.literal("attack").executes(ctx -> attack(ctx.getSource())))
                .then(Commands.literal("lesson").then(Commands.argument("nature", StringArgumentType.word()).suggests(JutsuExtraCommand.NATURES)
                        .executes(ctx -> lesson(ctx.getSource(), StringArgumentType.getString(ctx, "nature")))))
                .then(Commands.literal("teachjutsu")
                        .then(Commands.literal("all").executes(ctx -> teachJutsu(ctx.getSource(), null)))
                        .then(Commands.argument("jutsu", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(Cmd.data(ctx.getSource()).jutsu().known(), b))
                                .executes(ctx -> teachJutsu(ctx.getSource(),
                                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "jutsu")))))
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

    /** The crew mates loaded on the server (unloaded ones can't be reached). */
    static List<SparringNinja> members(ServerPlayer player) {
        return com.noirm.ally.CrewPackets.loaded(player);
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
            if (ninja.allyState().occupation() != com.noirm.ally.Occupation.NONE) {
                text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable(ninja.allyState().occupation().key()).withStyle(ChatFormatting.GOLD));
            }
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
        ServerPlayer player = Cmd.player(source);
        return each(source, "command.noirm.crew.ordered", ninja -> com.noirm.ally.CrewBrain.giveOrder(ninja, order, player),
                Component.translatable(order.key()));
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

    static int role(CommandSourceStack source, String name) throws CommandSyntaxException {
        com.noirm.ally.Occupation job = parse(com.noirm.ally.Occupation.class, name);
        if (job == null) {
            return Cmd.fail(source, "command.noirm.crew.bad_value", name);
        }
        return each(source, "command.noirm.crew.role", ninja -> ninja.allyState().setOccupation(job), Component.translatable(job.key()));
    }

    static int behaviour(CommandSourceStack source, String name) throws CommandSyntaxException {
        com.noirm.ally.Behaviour behaviour = parse(com.noirm.ally.Behaviour.class, name);
        if (behaviour == null) {
            return Cmd.fail(source, "command.noirm.crew.bad_value", name);
        }
        return each(source, "command.noirm.crew.behaviour", ninja -> ninja.allyState().setBehaviour(behaviour),
                Component.translatable(behaviour.key()));
    }

    /** "Come here" (or "fall back": the same, and no new fights for a few seconds). */
    static int come(CommandSourceStack source, boolean fallBack) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        return each(source, fallBack ? "command.noirm.crew.fell_back" : "command.noirm.crew.coming", ninja -> {
            if (fallBack) {
                com.noirm.ally.CrewBrain.fallBack(ninja, player);
            } else {
                com.noirm.ally.CrewBrain.come(ninja, player);
            }
        });
    }

    /** Everyone goes for what you're looking at. */
    static int attack(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        net.minecraft.world.entity.LivingEntity target = Cmd.lookedAt(player, net.minecraft.world.entity.LivingEntity.class, 40);
        if (target == null || Crew.sameSide(player, target)) {
            return Cmd.fail(source, "command.noirm.crew.no_enemy");
        }
        return each(source, "command.noirm.crew.attacking", ninja -> com.noirm.ally.CrewBrain.attack(ninja, target), target.getDisplayName());
    }

    /** Starts (or queues) teaching everyone a nature of yours. */
    static int lesson(CommandSourceStack source, String name) throws CommandSyntaxException {
        Nature nature = JutsuExtraCommand.nature(name);
        if (nature == null) {
            return Cmd.fail(source, "command.noirm.nature.unknown", name);
        }
        ServerPlayer player = Cmd.player(source);
        int started = 0;
        for (SparringNinja ninja : members(player)) {
            if (com.noirm.ally.NatureTeaching.start(player, ninja, nature)) {
                started++;
            }
        }
        return Cmd.ok(source, "command.noirm.crew.lesson_started", started, Component.translatable(nature.translationKey()));
    }

    /** Teaches everyone a jutsu of yours (or all you can), queuing its nature first where needed. */
    static int teachJutsu(CommandSourceStack source, net.minecraft.resources.ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        int count = 0;
        for (SparringNinja ninja : members(player)) {
            count += id == null ? com.noirm.ally.AllyPackets.teachAll(player, ninja)
                    : com.noirm.ally.AllyPackets.teachOrQueue(player, ninja, id) ? 1 : 0;
        }
        return Cmd.ok(source, "command.noirm.crew.taught_jutsu", count);
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

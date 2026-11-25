package com.noirm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.aburame.Colony;
import com.noirm.aburame.ColonyData;
import com.noirm.aburame.Harm;
import com.noirm.aburame.HiveRank;
import com.noirm.aburame.NestRoll;
import com.noirm.aburame.Species;
import com.noirm.aburame.Swarms;
import com.noirm.aburame.WildInsect;
import com.noirm.aburame.WildKind;
import com.noirm.player.ShinobiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * {@code /noirm aburame} commands (the colony, see {@code docs/ABURAME.md}):
 * <ul>
 *   <li>{@code /noirm aburame} — your colony: counts, Vigour, rank and what the next one needs</li>
 *   <li>{@code /noirm aburame recall} — call every swarm home (the Swarm key's tap)</li>
 *   <li>{@code /noirm aburame grant [rinkaichu]} — a colony for you, whatever your clan (cheats)</li>
 *   <li>{@code /noirm aburame set <n> [species]} — set the body count (cheats)</li>
 *   <li>{@code /noirm aburame rank <rank>} / {@code vigour <0-100>} / {@code adapt <harm> <pct>} (cheats)</li>
 *   <li>{@code /noirm aburame species <species>} — host a species (cheats)</li>
 *   <li>{@code /noirm aburame bug <kind>} — a wild insect beside you; {@code strain <kind>} — bred in (cheats)</li>
 *   <li>{@code /noirm aburame generation [n]} — pass generations (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class AburameCommand {
    private AburameCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("aburame")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("recall").executes(ctx -> recall(ctx.getSource())))
                .then(Commands.literal("grant").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> grant(ctx.getSource(), false))
                        .then(Commands.literal("rinkaichu").executes(ctx -> grant(ctx.getSource(), true))))
                .then(Commands.literal("set").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), "kikaichu"))
                                .then(Commands.argument("species", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Species.values()).map(Species::key), b))
                                        .executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "species"))))))
                .then(Commands.literal("rank").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HiveRank.values()).map(HiveRank::key), b))
                                .executes(ctx -> rank(ctx.getSource(), StringArgumentType.getString(ctx, "rank")))))
                .then(Commands.literal("vigour").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("vigour", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> vigour(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "vigour")))))
                .then(Commands.literal("adapt").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("harm", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Harm.values()).filter(h -> h.adaptable).map(Harm::key), b))
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 40))
                                        .executes(ctx -> adapt(ctx.getSource(), StringArgumentType.getString(ctx, "harm"),
                                                IntegerArgumentType.getInteger(ctx, "percent"))))))
                .then(Commands.literal("species").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Species.values()).map(Species::key), b))
                                .executes(ctx -> species(ctx.getSource(), StringArgumentType.getString(ctx, "species")))))
                .then(Commands.literal("bug").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(WildKind.values()).map(WildKind::key), b))
                                .executes(ctx -> bug(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))))
                .then(Commands.literal("strain").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(WildKind.collection()).map(WildKind::key), b))
                                .executes(ctx -> strain(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))))
                .then(Commands.literal("generation").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> generation(ctx.getSource(), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> generation(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    private static ColonyData colony(CommandSourceStack source) throws CommandSyntaxException {
        ColonyData c = Colony.of(Cmd.player(source));
        if (c == null) {
            Cmd.fail(source, "message.noirm.colony.none");
        }
        return c;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ColonyData c = colony(source);
        if (c == null) {
            return 0;
        }
        Cmd.ok(source, "command.noirm.aburame.status", c.bodyTotal(), Colony.capacity(player), Colony.stored(player), c.field(),
                c.hiveTotal(), Math.round(c.vigour()), c.generation(), Component.translatable(c.rank().translationKey()));
        HiveRank next = c.rank().next();
        if (next != null) {
            Cmd.ok(source, "command.noirm.aburame.next", Component.translatable(next.translationKey()), Colony.total(player), next.colony,
                    Component.translatable("screen.noirm.colony.deed." + next.key(), Math.round(c.drainedTotal())),
                    Colony.deedDone(player, c, next) ? "✓" : "✗");
        }
        return 1;
    }

    private static int recall(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        return Cmd.ok(source, "message.noirm.colony.recalled", Swarms.recall(player));
    }

    private static int grant(CommandSourceStack source, boolean rinkaichu) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        data.colony().clear();
        Colony.grant(player, data, new NestRoll(3000, rinkaichu, 0));
        return Cmd.ok(source, "command.noirm.aburame.granted", 3000);
    }

    private static int set(CommandSourceStack source, int count, String speciesKey) throws CommandSyntaxException {
        ColonyData c = colony(source);
        Species s = Species.byKey(speciesKey);
        if (c == null || s == null) {
            return 0;
        }
        c.setBody(s, count);
        if (s != Species.KIKAICHU) {
            c.hosted().add(s);
        }
        Colony.changed(Cmd.player(source));
        return Cmd.ok(source, "command.noirm.aburame.set", count, Component.translatable(s.translationKey()));
    }

    private static int rank(CommandSourceStack source, String key) throws CommandSyntaxException {
        ColonyData c = colony(source);
        HiveRank r = HiveRank.byKey(key);
        if (c == null || r == null) {
            return 0;
        }
        Colony.setRank(Cmd.player(source), c, r);
        return Cmd.ok(source, "command.noirm.aburame.rank", Component.translatable(r.translationKey()));
    }

    private static int vigour(CommandSourceStack source, int v) throws CommandSyntaxException {
        ColonyData c = colony(source);
        if (c == null) {
            return 0;
        }
        c.setVigour(v);
        Colony.changed(Cmd.player(source));
        return Cmd.ok(source, "command.noirm.aburame.vigour", v);
    }

    private static int adapt(CommandSourceStack source, String key, int pct) throws CommandSyntaxException {
        ColonyData c = colony(source);
        Harm h = Harm.byKey(key);
        if (c == null || h == null || !h.adaptable) {
            return 0;
        }
        c.setResist(h, pct / 100f);
        Colony.changed(Cmd.player(source));
        return Cmd.ok(source, "command.noirm.aburame.adapt", key, pct);
    }

    private static int species(CommandSourceStack source, String key) throws CommandSyntaxException {
        ColonyData c = colony(source);
        Species s = Species.byKey(key);
        if (c == null || s == null) {
            return 0;
        }
        c.hosted().add(s);
        if (c.body(s) == 0) {
            c.setBody(s, s == Species.KIDAICHU ? 600 : 2000);
        }
        Colony.changed(Cmd.player(source));
        return Cmd.ok(source, "message.noirm.colony.species_added", Component.translatable(s.translationKey()));
    }

    private static int bug(CommandSourceStack source, String key) throws CommandSyntaxException {
        WildKind k = WildKind.byKey(key);
        if (k == null) {
            return 0;
        }
        WildInsect.spawn(source.getLevel(), Cmd.inFront(source, 2).add(0, 1, 0), k);
        return Cmd.ok(source, "command.noirm.aburame.bug", Component.translatable(k.translationKey()));
    }

    private static int strain(CommandSourceStack source, String key) throws CommandSyntaxException {
        ColonyData c = colony(source);
        WildKind k = WildKind.byKey(key);
        if (c == null || k == null) {
            return 0;
        }
        c.caught().add(k.key());
        c.strains().add(k.key());
        Colony.changed(Cmd.player(source));
        return Cmd.ok(source, "message.noirm.colony.jar_strain");
    }

    private static int generation(CommandSourceStack source, int n) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ColonyData c = colony(source);
        if (c == null) {
            return 0;
        }
        for (int i = 0; i < n; i++) {
            Colony.generation(player, c);
        }
        Colony.changed(player);
        return Cmd.ok(source, "command.noirm.aburame.generation", c.generation());
    }
}

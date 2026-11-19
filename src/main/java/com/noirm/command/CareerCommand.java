package com.noirm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.economy.Ryo;
import com.noirm.mission.CareerData;
import com.noirm.mission.Exams;
import com.noirm.mission.Mission;
import com.noirm.mission.MissionTemplate;
import com.noirm.mission.MissionTemplates;
import com.noirm.mission.Missions;
import com.noirm.mission.PlayerRank;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * Ryo, missions and rank:
 * <ul>
 *   <li>{@code /noirm ryo} — your wallet; {@code withdraw <amount>} (a pouch to drop or give),
 *       {@code pay <player> <amount>}; {@code give|take|set <amount>} (cheats)</li>
 *   <li>{@code /noirm mission} — the mission you're on; {@code list} (every mission by rank),
 *       {@code abandon}; {@code start <mission>} (cheats, here as the board)</li>
 *   <li>{@code /noirm rank} — your rank and points; {@code set <rank>}, {@code points <n>},
 *       {@code exam} (start your exam here) (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CareerCommand {
    private CareerCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var amount = IntegerArgumentType.integer(0, CareerData.MAX_RYO);
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("ryo")
                .executes(ctx -> showRyo(ctx.getSource()))
                .then(Commands.literal("withdraw").then(Commands.argument("amount", IntegerArgumentType.integer(1, CareerData.MAX_RYO))
                        .executes(ctx -> withdraw(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("pay").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, CareerData.MAX_RYO))
                                .executes(ctx -> pay(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("give").requires(s -> s.hasPermission(Cmd.CHEATS)).then(Commands.argument("amount", amount)
                        .executes(ctx -> change(ctx.getSource(), "give", IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("take").requires(s -> s.hasPermission(Cmd.CHEATS)).then(Commands.argument("amount", amount)
                        .executes(ctx -> change(ctx.getSource(), "take", IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("set").requires(s -> s.hasPermission(Cmd.CHEATS)).then(Commands.argument("amount", amount)
                        .executes(ctx -> change(ctx.getSource(), "set", IntegerArgumentType.getInteger(ctx, "amount")))))));
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("mission")
                .executes(ctx -> showMission(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("abandon").executes(ctx -> abandon(ctx.getSource())))
                .then(Commands.literal("start").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("mission", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Cmd.shortIds(MissionTemplates.all().keySet()), builder))
                                .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "mission")))))));
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("rank")
                .executes(ctx -> showRank(ctx.getSource()))
                .then(Commands.literal("set").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(PlayerRank.values()).map(PlayerRank::key), builder))
                                .executes(ctx -> setRank(ctx.getSource(), StringArgumentType.getString(ctx, "rank")))))
                .then(Commands.literal("points").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("points", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(ctx -> setPoints(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "points")))))
                .then(Commands.literal("exam").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> exam(ctx.getSource())))));
    }

    // ---------------------------------------------------------------- ryo

    static int showRyo(CommandSourceStack source) throws CommandSyntaxException {
        return Cmd.ok(source, "command.noirm.ryo.balance", Ryo.format(Ryo.balance(Cmd.player(source))));
    }

    static int change(CommandSourceStack source, String how, int amount) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        switch (how) {
            case "give" -> Ryo.give(player, amount);
            case "take" -> {
                if (!Ryo.take(player, amount)) {
                    return Cmd.fail(source, "command.noirm.ryo.not_enough", Ryo.format(Ryo.balance(player)));
                }
            }
            default -> Ryo.set(player, amount);
        }
        return showRyo(source);
    }

    static int withdraw(CommandSourceStack source, int amount) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        if (!Ryo.withdraw(player, amount)) {
            return Cmd.fail(source, "command.noirm.ryo.not_enough", Ryo.format(Ryo.balance(player)));
        }
        return Cmd.ok(source, "command.noirm.ryo.withdrawn", Ryo.format(amount), Ryo.format(Ryo.balance(player)));
    }

    static int pay(CommandSourceStack source, ServerPlayer to, int amount) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        if (to == player) {
            return Cmd.fail(source, "command.noirm.ryo.self");
        }
        if (!Ryo.take(player, amount)) {
            return Cmd.fail(source, "command.noirm.ryo.not_enough", Ryo.format(Ryo.balance(player)));
        }
        Ryo.give(to, amount);
        to.sendSystemMessage(Component.translatable("command.noirm.ryo.received", Ryo.format(amount), player.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        return Cmd.ok(source, "command.noirm.ryo.paid", Ryo.format(amount), to.getDisplayName());
    }

    // ---------------------------------------------------------------- missions

    static int showMission(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        Mission m = Missions.active(player);
        if (m == null || m.definition() == null) {
            return Cmd.ok(source, "command.noirm.mission.none");
        }
        MissionTemplate def = m.definition();
        var target = m.target();
        int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(target)));
        return Cmd.ok(source, "command.noirm.mission.show", def.rank().name(), Component.translatable(MissionTemplate.nameKey(m.template)),
                Missions.objective(player, m), target.getX(), target.getZ(), distance);
    }

    static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        MutableComponent text = Component.translatable("command.noirm.mission.list").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (MissionTemplate.Letter letter : MissionTemplate.Letter.values()) {
            boolean open = PlayerRank.atLeast(player, letter.required());
            for (ResourceLocation id : MissionTemplates.offerable(letter)) {
                MissionTemplate def = MissionTemplates.get(id);
                text.append("\n ").append(Component.literal("[" + letter.name() + "] ").withStyle(s -> s.withColor(letter.color())))
                        .append(Component.translatable(MissionTemplate.nameKey(id)).withStyle(open ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("command.noirm.mission.list_row", Ryo.format(def.reward()), def.points(),
                                Component.translatable("mission.noirm.kind." + def.kind().key())).withStyle(ChatFormatting.GRAY));
                if (!open) {
                    text.append(" ").append(Component.translatable("mission.noirm.why.rank", letter.required().title()).withStyle(ChatFormatting.DARK_RED));
                }
            }
        }
        source.sendSuccess(() -> text, false);
        return MissionTemplates.all().size();
    }

    static int abandon(CommandSourceStack source) throws CommandSyntaxException {
        return Missions.abandon(Cmd.player(source)) ? 1 : Cmd.fail(source, "command.noirm.mission.none");
    }

    static int start(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ResourceLocation id = Cmd.id(name);
        if (id == null || MissionTemplates.get(id) == null) {
            return Cmd.fail(source, "command.noirm.mission.unknown", name);
        }
        Mission current = Missions.active(player);
        if (current != null) {
            Missions.abandon(player);
        }
        PlayerRank before = PlayerRank.of(player);
        // A cheat: take it whatever your rank, with where you stand as the board.
        Missions.career(player).ifPresent(c -> c.setRank(PlayerRank.KAGE_CANDIDATE));
        Component why = Missions.accept(player, id, player.blockPosition());
        Missions.career(player).ifPresent(c -> c.setRank(before));
        return why == null ? 1 : Cmd.fail(source, "command.noirm.mission.cant", why);
    }

    // ---------------------------------------------------------------- rank

    static int showRank(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        CareerData career = Cmd.data(source).career();
        PlayerRank next = career.rank().next();
        source.sendSuccess(() -> summary(career), false);
        if (next != null) {
            Component why = next.exam() ? Exams.blocker(player) : null;
            Cmd.ok(source, next.exam() ? "command.noirm.rank.next_exam" : "command.noirm.rank.next_invite", next.title(), next.points(),
                    next.missions(), next.letter() == null ? "" : next.letter().name(), why == null ? Component.translatable("command.noirm.rank.ready") : why);
        }
        return career.rank().ordinal() + 1;
    }

    /** One line: rank, points, ryo and missions done (also for {@code /noirm me}). */
    public static Component summary(CareerData career) {
        MissionTemplate.Letter[] letters = MissionTemplate.Letter.values();
        StringBuilder done = new StringBuilder();
        for (MissionTemplate.Letter letter : letters) {
            done.append(done.isEmpty() ? "" : " ").append(letter.name()).append(career.completed(letter));
        }
        PlayerRank next = career.rank().next();
        return Component.translatable("command.noirm.rank.summary", career.rank().title(), career.points(),
                next == null ? "—" : String.valueOf(next.points()), Ryo.format(career.ryo()), done.toString());
    }

    static int setRank(CommandSourceStack source, String name) throws CommandSyntaxException {
        PlayerRank rank = PlayerRank.byKey(name);
        if (rank == null) {
            return Cmd.fail(source, "command.noirm.rank.unknown", name);
        }
        ServerPlayer player = Cmd.player(source);
        CareerData career = Cmd.data(source).career();
        career.setRank(rank);
        career.setPoints(Math.max(career.points(), rank.points()));
        player.refreshDisplayName();
        return Cmd.ok(source, "command.noirm.rank.set", rank.title());
    }

    static int setPoints(CommandSourceStack source, int points) throws CommandSyntaxException {
        Cmd.data(source).career().setPoints(points);
        Exams.afterPoints(Cmd.player(source));
        return showRank(source);
    }

    static int exam(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        CareerData career = Cmd.data(source).career();
        PlayerRank next = career.rank().next();
        if (next != null && next.exam()) {
            career.setPoints(Math.max(career.points(), next.points()));
            career.setExamCooldownUntil(0);
        }
        return Exams.start(player, player.blockPosition()) ? 1 : 0;
    }
}

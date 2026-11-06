package com.noirm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.bounty.BingoBook;
import com.noirm.bounty.BingoEntry;
import com.noirm.bounty.BingoEvents;
import com.noirm.bounty.BountyItems;
import com.noirm.bounty.BountyPackets;
import com.noirm.bounty.NinjaClass;
import com.noirm.economy.Ryo;
import com.noirm.entity.SparringNinja;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ninja classes and the bingo book:
 * <ul>
 *   <li>{@code /noirm class [<target>]} — a fighter's class (D–S) and how it's worked out (you, or
 *       a player or ninja)</li>
 *   <li>{@code /noirm bingo} / {@code list} — the book's pages in chat</li>
 *   <li>{@code /noirm bingo refresh} — a day's refresh now (cheats)</li>
 *   <li>{@code /noirm bingo spawn <entry>} — brings a page's rogue in front of you (cheats)</li>
 *   <li>{@code /noirm bingo give} — a bingo book (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class BountyCommand {
    private BountyCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("class")
                .executes(ctx -> showClass(ctx.getSource(), Cmd.player(ctx.getSource())))
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(ctx -> showClass(ctx.getSource(), EntityArgument.getEntity(ctx, "target"))))));
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("bingo")
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("refresh").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> refresh(ctx.getSource())))
                .then(Commands.literal("give").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> give(ctx.getSource())))
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("entry", IntegerArgumentType.integer(1))
                                .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "entry")))))));
    }

    /** One line for {@code /noirm me}: your class and power, and how that differs from your rank. */
    public static Component meLine(LivingEntity entity) {
        int code = NinjaClass.code(entity);
        return Component.translatable("command.noirm.class.me", NinjaClass.tag(code), String.format("%.1f", NinjaClass.power(entity)));
    }

    static int showClass(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living) || NinjaClass.statPower(living) <= 0f) {
            return Cmd.fail(source, "command.noirm.class.not_fighter", target.getDisplayName());
        }
        int code = NinjaClass.code(living);
        NinjaClass cls = NinjaClass.of(living);
        NinjaClass next = cls.ordinal() + 1 < NinjaClass.values().length ? NinjaClass.values()[cls.ordinal() + 1] : null;
        MutableComponent text = Component.translatable("command.noirm.class.show", NinjaClass.nameplate(code, living.getDisplayName()),
                String.format("%.1f", NinjaClass.power(living)), String.format("%.1f", NinjaClass.statPower(living)),
                Math.round(NinjaClass.extras(living) * 100), Math.round(NinjaClass.worth(living))).withStyle(ChatFormatting.GOLD);
        text.append("\n ").append(next == null || code == NinjaClass.LEGENDARY_CODE
                ? Component.translatable("command.noirm.class.top").withStyle(ChatFormatting.GRAY)
                : Component.translatable("command.noirm.class.next", next.name(), Math.round(next.minPower())).withStyle(ChatFormatting.GRAY));
        text.append("\n ").append(Component.translatable("command.noirm.class.thresholds").withStyle(ChatFormatting.DARK_GRAY));
        source.sendSuccess(() -> text, false);
        return code + 1;
    }

    static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        BingoBook book = BingoBook.get(source.getServer());
        MutableComponent text = Component.translatable("command.noirm.bingo.header", book.activeRegulars(),
                book.entries().stream().filter(e -> e.legendary && e.active()).count()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (BingoEntry e : book.pages()) {
            MutableComponent row = Component.literal("\n #" + e.id + " ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(NinjaClass.tag(e.code())).append(" ");
            if (e.active()) {
                row.append(e.name().copy().withStyle(e.legendary ? ChatFormatting.GOLD : ChatFormatting.WHITE))
                        .append(Component.translatable("command.noirm.bingo.row", Ryo.format(e.bounty), BountyPackets.seen(player, e))
                                .withStyle(ChatFormatting.GRAY));
            } else {
                row.append(e.name().copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH))
                        .append(Component.translatable("command.noirm.bingo.row_claimed", e.claimedBy).withStyle(ChatFormatting.DARK_RED));
            }
            text.append(row);
        }
        source.sendSuccess(() -> text, false);
        return book.entries().size();
    }

    static int refresh(CommandSourceStack source) {
        BingoBook book = BingoBook.get(source.getServer());
        var level = source.getServer().overworld();
        if (!book.seeded()) {
            book.seed(level, level.getRandom());
        }
        BingoEntry added = book.refresh(level, level.getRandom());
        return added == null ? Cmd.ok(source, "command.noirm.bingo.refreshed")
                : Cmd.ok(source, "command.noirm.bingo.refreshed_new", added.name());
    }

    static int give(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ItemStack book = new ItemStack(BountyItems.BINGO_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        return Cmd.ok(source, "command.noirm.bingo.given");
    }

    static int spawn(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        BingoBook book = BingoBook.get(source.getServer());
        BingoEntry e = book.find(id);
        if (e == null) {
            return Cmd.fail(source, "command.noirm.bingo.unknown", id);
        }
        if (!e.active()) {
            return Cmd.fail(source, "command.noirm.bingo.claimed", e.name(), e.claimedBy);
        }
        if (player.level() != source.getServer().overworld()) {
            return Cmd.fail(source, "command.noirm.bingo.overworld");
        }
        SparringNinja existing = BingoEvents.body(e);
        if (existing != null) {
            existing.discard(); // brought here instead
        }
        Vec3 at = Cmd.inFront(source, 6);
        e.area = BlockPos.containing(at);
        SparringNinja n = BingoEvents.spawn(player.serverLevel(), book, e, e.area);
        return n == null ? Cmd.fail(source, "command.noirm.bingo.no_spot") : Cmd.ok(source, "command.noirm.bingo.spawned", e.name());
    }
}

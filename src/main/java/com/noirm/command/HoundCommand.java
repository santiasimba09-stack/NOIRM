package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.combat.Perception;
import com.noirm.hound.HoundData;
import com.noirm.hound.HoundStage;
import com.noirm.hound.Hounds;
import com.noirm.hound.NinjaHound;
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
import java.util.List;

/**
 * {@code /noirm hound} commands (the ninja hound, see {@code docs/INUZUKA.md}):
 * <ul>
 *   <li>{@code /noirm hound} — your hound: stage, bond, XP and what the next stage needs</li>
 *   <li>{@code /noirm hound order <order>} — follow, sit, guard, come, attack, track, carry, jacket, ride, pet, howl, recall</li>
 *   <li>{@code /noirm hound give [coat]} — a new puppy (cheats)</li>
 *   <li>{@code /noirm hound stage <stage>} — grow (or shrink) it to a stage (cheats)</li>
 *   <li>{@code /noirm hound bond <0-100>} / {@code xp <amount>} — set the bond, add XP (cheats)</li>
 *   <li>{@code /noirm hound remove} — send it away for good (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HoundCommand {
    static final List<String> ORDERS = List.of("follow", "sit", "guard", "come", "attack", "track", "carry", "jacket", "ride", "pet", "howl", "recall");

    private HoundCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("hound")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("order")
                        .then(Commands.argument("order", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ORDERS, b))
                                .executes(ctx -> order(ctx.getSource(), StringArgumentType.getString(ctx, "order")))))
                .then(Commands.literal("give").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> give(ctx.getSource(), null))
                        .then(Commands.argument("coat", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Hounds.COATS, b))
                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "coat")))))
                .then(Commands.literal("stage").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HoundStage.values()).map(HoundStage::key), b))
                                .executes(ctx -> stage(ctx.getSource(), StringArgumentType.getString(ctx, "stage")))))
                .then(Commands.literal("bond").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("bond", FloatArgumentType.floatArg(0, 100))
                                .executes(ctx -> bond(ctx.getSource(), FloatArgumentType.getFloat(ctx, "bond")))))
                .then(Commands.literal("xp").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0, 100000))
                                .executes(ctx -> xp(ctx.getSource(), FloatArgumentType.getFloat(ctx, "amount")))))
                .then(Commands.literal("remove").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> remove(ctx.getSource())))));
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        HoundData hd = Hounds.data(player);
        if (hd == null || !hd.has()) {
            return Cmd.fail(source, "message.noirm.hound.none");
        }
        NinjaHound hound = Hounds.find(player);
        if (hound != null) {
            Hounds.snapshot(hound);
        }
        Component stage = Component.translatable("screen.noirm.hound.stage." + hd.stage().key());
        Cmd.ok(source, "command.noirm.hound.status", hd.name(), stage, Math.round(hd.bond()), Math.round(hd.xp()));
        HoundStage next = hd.stage().next();
        if (next == null) {
            return Cmd.ok(source, "command.noirm.hound.top");
        }
        Component blocker = Hounds.blocker(next, hd.xp(), hd.bond(), Perception.basePower(player));
        Component nextName = Component.translatable("screen.noirm.hound.stage." + next.key());
        return blocker == null ? Cmd.ok(source, "command.noirm.hound.ready", nextName)
                : Cmd.ok(source, "command.noirm.hound.next", nextName, blocker);
    }

    private static int order(CommandSourceStack source, String order) throws CommandSyntaxException {
        if (!ORDERS.contains(order)) {
            return Cmd.fail(source, "command.noirm.hound.bad_order", String.join(", ", ORDERS));
        }
        return Hounds.command(Cmd.player(source), order) ? 1 : 0;
    }

    private static int give(CommandSourceStack source, String coat) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        int c = coat == null ? player.getRandom().nextInt(Hounds.COATS.length) : Arrays.asList(Hounds.COATS).indexOf(coat);
        if (c < 0) {
            return Cmd.fail(source, "command.noirm.hound.bad_coat", String.join(", ", Hounds.COATS));
        }
        NinjaHound hound = Hounds.grantPuppy(player, c, false);
        return hound == null ? Cmd.fail(source, "command.noirm.hound.no_room") : Cmd.ok(source, "command.noirm.hound.given", hound.getDisplayName());
    }

    private static NinjaHound live(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        HoundData hd = Hounds.data(player);
        if (hd == null || !hd.has()) {
            Cmd.fail(source, "message.noirm.hound.none");
            return null;
        }
        NinjaHound hound = Hounds.find(player);
        if (hound == null) {
            Cmd.fail(source, "message.noirm.hound.away");
        }
        return hound;
    }

    private static int stage(CommandSourceStack source, String key) throws CommandSyntaxException {
        HoundStage stage = HoundStage.byKey(key);
        if (stage == null) {
            return Cmd.fail(source, "command.noirm.hound.bad_stage");
        }
        NinjaHound hound = live(source);
        if (hound == null) {
            return 0;
        }
        // Enough XP and bond to stay there, so it doesn't feel it skipped a step.
        hound.setXp(Math.max(hound.xp(), stage.xpNeeded));
        hound.setBond(Math.max(hound.bond(), stage.bondNeeded));
        Hounds.grow(hound, stage);
        return Cmd.ok(source, "command.noirm.hound.staged", hound.getDisplayName(), Component.translatable("screen.noirm.hound.stage." + stage.key()));
    }

    private static int bond(CommandSourceStack source, float bond) throws CommandSyntaxException {
        NinjaHound hound = live(source);
        if (hound == null) {
            return 0;
        }
        hound.setBond(bond);
        Hounds.snapshot(hound);
        Cmd.data(source).markPowersDirty();
        return Cmd.ok(source, "command.noirm.hound.bonded", hound.getDisplayName(), Math.round(bond));
    }

    private static int xp(CommandSourceStack source, float amount) throws CommandSyntaxException {
        NinjaHound hound = live(source);
        if (hound == null) {
            return 0;
        }
        Hounds.addXp(hound, amount);
        Hounds.snapshot(hound);
        Cmd.data(source).markPowersDirty();
        return Cmd.ok(source, "command.noirm.hound.xp", hound.getDisplayName(), Math.round(hound.xp()));
    }

    private static int remove(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        NinjaHound hound = Hounds.find(player);
        if (hound != null) {
            if (hound.isCarried()) {
                Hounds.putDown(hound);
            }
            hound.discard();
        }
        data.hound().clear();
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.hound.removed");
    }
}

package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.combat.Perception;
import com.noirm.hound.HoundData;
import com.noirm.hound.HoundGrowth;
import com.noirm.hound.HoundLook;
import com.noirm.hound.HoundTraits;
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
 *   <li>{@code /noirm hound give [breed]} — a new puppy, look and traits rolled (cheats)</li>
 *   <li>{@code /noirm hound stage <milestone>} / {@code level <n>} — grow (or shrink) it (cheats)</li>
 *   <li>{@code /noirm hound reroll} — a new look and new traits for your hound (cheats)</li>
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
                        .then(Commands.argument("breed", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HoundLook.Breed.values()).map(HoundLook.Breed::key), b))
                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "breed")))))
                .then(Commands.literal("level").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 999))
                                .executes(ctx -> level(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.literal("reroll").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> reroll(ctx.getSource())))
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
        Component breed = Component.translatable("hound.noirm.breed." + hd.look().breed().key());
        Cmd.ok(source, "command.noirm.hound.status", hd.name(), hd.level(), stage, breed, Math.round(hd.bond()), Math.round(hd.xp()));
        if (!hd.traits().isEmpty()) {
            Cmd.ok(source, "command.noirm.hound.traits", String.join(", ", hd.traits().stream()
                    .map(t -> Component.translatable("hound_trait.noirm." + t).getString()).toList()));
        }
        Component blocker = Hounds.blocker(hd.level() + 1, hd.xp(), hd.bond(), Perception.basePower(player));
        return blocker == null ? Cmd.ok(source, "command.noirm.hound.ready", hd.level() + 1)
                : Cmd.ok(source, "command.noirm.hound.next", hd.level() + 1, blocker);
    }

    private static int order(CommandSourceStack source, String order) throws CommandSyntaxException {
        if (!ORDERS.contains(order)) {
            return Cmd.fail(source, "command.noirm.hound.bad_order", String.join(", ", ORDERS));
        }
        return Hounds.command(Cmd.player(source), order) ? 1 : 0;
    }

    private static int give(CommandSourceStack source, String breedKey) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        HoundLook look = null;
        if (breedKey != null) {
            HoundLook.Breed breed = HoundLook.breedByKey(breedKey);
            if (breed == null) {
                return Cmd.fail(source, "command.noirm.hound.bad_breed",
                        String.join(", ", Arrays.stream(HoundLook.Breed.values()).map(HoundLook.Breed::key).toList()));
            }
            look = HoundLook.roll(player.getRandom(), breed);
        }
        NinjaHound hound = Hounds.grantPuppy(player, look, false);
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
        setLevel(hound, stage.minLevel);
        return Cmd.ok(source, "command.noirm.hound.staged", hound.getDisplayName(), stage.minLevel,
                Component.translatable("screen.noirm.hound.stage." + stage.key()));
    }

    private static int level(CommandSourceStack source, int level) throws CommandSyntaxException {
        NinjaHound hound = live(source);
        if (hound == null) {
            return 0;
        }
        setLevel(hound, level);
        return Cmd.ok(source, "command.noirm.hound.staged", hound.getDisplayName(), level,
                Component.translatable("screen.noirm.hound.stage." + hound.stage().key()));
    }

    /** Enough XP and bond to stay at {@code level}, so it doesn't feel it skipped a step. */
    private static void setLevel(NinjaHound hound, int level) {
        hound.setXp(Math.max(hound.xp(), HoundGrowth.xpFor(level)));
        hound.setBond(Math.max(hound.bond(), HoundGrowth.bondFor(level)));
        Hounds.grow(hound, level);
    }

    private static int reroll(CommandSourceStack source) throws CommandSyntaxException {
        NinjaHound hound = live(source);
        if (hound == null) {
            return 0;
        }
        hound.setLook(HoundLook.roll(hound.getRandom()));
        hound.setTraits(HoundTraits.roll(hound.getRandom()));
        Hounds.snapshot(hound);
        Cmd.data(source).markPowersDirty();
        return Cmd.ok(source, "command.noirm.hound.rerolled", hound.getDisplayName(),
                Component.translatable("hound.noirm.breed." + hound.look().breed().key()));
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

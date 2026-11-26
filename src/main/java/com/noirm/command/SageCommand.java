package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.master.MasterData;
import com.noirm.master.MasterSystem;
import com.noirm.player.ShinobiData;
import com.noirm.senjutsu.Meditation;
import com.noirm.senjutsu.Sage;
import com.noirm.senjutsu.SageMode;
import com.noirm.world.sanctuary.SanctuaryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /noirm sage} commands (the sage path, see {@code senjutsu.Sage}):
 * <ul>
 *   <li>{@code /noirm sage} — your nature energy, safe limit, petrification and lessons</li>
 *   <li>{@code /noirm sage on|off} — Sage Mode (once learned)</li>
 *   <li>{@code /noirm sage rumour} — a vague hint where the Stone Hermit lives</li>
 *   <li>{@code /noirm sage learn} — pass his test and learn every lesson (cheats)</li>
 *   <li>{@code /noirm sage energy <v>}, {@code petrify <v>} — set them (cheats)</li>
 *   <li>{@code /noirm sage sanctuary} — build a sanctuary here, with the hermit (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SageCommand {
    public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> SANCTUARIES =
            TagKey.create(Registries.STRUCTURE, Noirm.id("sage_sanctuaries"));

    private SageCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("sage")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> on(ctx.getSource())))
                .then(Commands.literal("off").executes(ctx -> off(ctx.getSource())))
                .then(Commands.literal("rumour").executes(ctx -> rumour(ctx.getSource())))
                .then(Commands.literal("learn").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> learn(ctx.getSource())))
                .then(Commands.literal("energy").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0, Meditation.NATURE_MAX))
                                .executes(ctx -> energy(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
                .then(Commands.literal("petrify").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0, 99))
                                .executes(ctx -> petrify(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
                .then(Commands.literal("sanctuary").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .executes(ctx -> sanctuary(ctx.getSource())))));
    }

    static int status(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        var sage = data.senjutsu().sage();
        MutableComponent text = Component.translatable("command.noirm.sage.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        text.append("\n ").append(Component.translatable("command.noirm.sage.energy", Math.round(data.senjutsu().natureEnergy()),
                Math.round(Sage.safeLimit(data)), Math.round(data.getControl())).withStyle(ChatFormatting.GRAY));
        text.append("\n ").append(Component.translatable("command.noirm.sage.stone", Math.round(sage.petrification())).withStyle(ChatFormatting.GRAY));
        String stage = !Sage.student(data) ? "untested" : Sage.learned(data, Sage.LESSON_SAGE) ? "sage"
                : Sage.learned(data, Sage.LESSON_BALANCE) ? "balance" : Sage.opened(data) ? "open" : "student";
        text.append("\n ").append(Component.translatable("command.noirm.sage.path." + stage, String.format(java.util.Locale.ROOT, "%.1f",
                Sage.gainMultiplier(data))).withStyle(ChatFormatting.GRAY));
        text.append("\n ").append(Component.translatable(sage.active() ? "command.noirm.sage.mode_on" : "command.noirm.sage.mode_off")
                .withStyle(sage.active() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    static int on(CommandSourceStack source) throws CommandSyntaxException {
        return SageMode.start(Cmd.player(source)) ? 1 : 0;
    }

    static int off(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        if (!SageMode.isActive(player)) {
            return Cmd.fail(source, "command.noirm.sage.not_on");
        }
        SageMode.end(player, false);
        return 1;
    }

    /** Passes the hermit's test and learns all three lessons (their jutsu too). */
    static int learn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        var def = MasterData.get(Sage.HERMIT);
        if (def == null) {
            return Cmd.fail(source, "commands.noirm.master.unknown", Sage.HERMIT.toString());
        }
        if (!Sage.student(data)) {
            MasterSystem.passTest(player, Sage.HERMIT);
        }
        for (var lesson : def.lessons()) {
            data.masters().learn(Sage.HERMIT.toString(), lesson.id());
            if (com.noirm.jutsu.JutsuData.get(lesson.jutsu()) != null && data.jutsu().learn(lesson.jutsu(), false)) {
                data.jutsu().equipIfRoom(lesson.jutsu());
            }
        }
        data.markDirty();
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.sage.learned");
    }

    static int energy(CommandSourceStack source, float value) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        data.senjutsu().setNatureEnergy(value);
        data.markPowersDirty();
        return Cmd.ok(source, "command.noirm.sage.energy_set", Math.round(value), Math.round(Sage.safeLimit(data)));
    }

    static int petrify(CommandSourceStack source, float value) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        data.senjutsu().sage().setPetrification(value);
        return Cmd.ok(source, "command.noirm.sage.petrify_set", Math.round(value));
    }

    /** Builds a sanctuary centred where you stand (the floor at your feet), with the hermit. */
    static int sanctuary(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        BlockPos origin = player.blockPosition();
        SanctuaryBuilder.build(player.serverLevel(), SanctuaryBuilder.fullBox(origin), origin, player.getRandom().nextLong(), true);
        return Cmd.ok(source, "command.noirm.sage.built");
    }

    /** "They say the hermit lives far to the north-east, some 2,300 blocks away." */
    static int rumour(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ServerLevel level = player.serverLevel();
        BlockPos found = level.findNearestMapStructure(SANCTUARIES, player.blockPosition(), 100, false);
        if (found == null) {
            return Cmd.fail(source, "command.noirm.sage.rumour_none");
        }
        double dx = found.getX() - player.getX(), dz = found.getZ() - player.getZ();
        int distance = (int) (Math.round(Math.sqrt(dx * dx + dz * dz) / 250.0) * 250);
        String[] dirs = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
        int octant = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
        return Cmd.ok(source, "command.noirm.sage.rumour", Component.translatable("command.noirm.sage.dir." + dirs[octant]),
                Math.max(250, distance));
    }
}

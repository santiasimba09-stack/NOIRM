package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.clan.ClanSystem;
import com.noirm.combat.Perception;
import com.noirm.combat.StyleSystem;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * {@code /noirm me}: everything about you in one place, and quick ways to get back on your feet.
 * <ul>
 *   <li>{@code /noirm me} — your profile: clan, natures, bars, stats, style, eyes, beast, crew, masters</li>
 *   <li>{@code /noirm me heal} — full health, chakra and stamina, no cooldowns or bad effects (cheats)</li>
 *   <li>{@code /noirm me max} — every stat to the maximum, then heal (cheats)</li>
 *   <li>{@code /noirm me stats <speed> <taijutsu> <vitality> <reserves> [<ctl> <wil>]} — all at once (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MeCommand {
    private MeCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var stat = FloatArgumentType.floatArg(1f, ShinobiData.MAX_STAT);
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("me")
                .executes(ctx -> profile(ctx.getSource()))
                .then(Commands.literal("heal").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> heal(ctx.getSource())))
                .then(Commands.literal("max").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> max(ctx.getSource())))
                .then(Commands.literal("stats").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("speed", stat).then(Commands.argument("taijutsu", stat)
                                .then(Commands.argument("vitality", stat).then(Commands.argument("reserves", stat)
                                        .executes(ctx -> setStats(ctx.getSource(), FloatArgumentType.getFloat(ctx, "speed"),
                                                FloatArgumentType.getFloat(ctx, "taijutsu"), FloatArgumentType.getFloat(ctx, "vitality"),
                                                FloatArgumentType.getFloat(ctx, "reserves"), -1f, -1f))
                                        // Optional: chakra control and willpower too.
                                        .then(Commands.argument("ctl", stat).then(Commands.argument("wil", stat)
                                                .executes(ctx -> setStats(ctx.getSource(), FloatArgumentType.getFloat(ctx, "speed"),
                                                        FloatArgumentType.getFloat(ctx, "taijutsu"), FloatArgumentType.getFloat(ctx, "vitality"),
                                                        FloatArgumentType.getFloat(ctx, "reserves"), FloatArgumentType.getFloat(ctx, "ctl"),
                                                        FloatArgumentType.getFloat(ctx, "wil"))))))))))));
    }

    static int profile(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        MutableComponent text = Component.translatable("command.noirm.me.header", player.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        var identity = data.identity();
        line(text, Component.translatable("message.noirm.clan.member",
                Component.literal(identity.clanName()).withStyle(identity.isCanon() ? ChatFormatting.RED : ChatFormatting.GOLD)));
        line(text, Component.translatable("message.noirm.clan.natures", ClanSystem.natures(identity)));
        line(text, Component.translatable("command.noirm.me.bars", Math.round(player.getHealth()), Math.round(player.getMaxHealth()),
                Math.round(data.getChakra()), Math.round(data.getMaxChakra()), Math.round(data.getStamina()), Math.round(data.getMaxStamina())));
        line(text, Component.translatable("command.noirm.stat", Math.round(data.getSpeed()), Math.round(data.getTaijutsu()),
                Math.round(Perception.perception(player)), Math.round(data.getVitality()), Math.round(data.getReserves()),
                Math.round(data.getControl()), Math.round(data.getWillpower())));
        line(text, Component.translatable("command.noirm.me.style", Component.translatable(StyleSystem.translationKey(StyleSystem.styleOf(data)))));
        line(text, EyesCommand.summary(data));
        line(text, data.biju().isJinchuriki()
                ? Component.translatable("command.noirm.me.beast", com.noirm.biju.BijuSystem.beastName(data.biju().tails()), data.biju().bond())
                : Component.translatable("command.noirm.me.no_beast"));
        line(text, Component.translatable("command.noirm.me.progress", data.jutsu().known().size(), data.crew().size(),
                data.masters().passedTests().size(), Math.round(data.awakening().resolve())));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static void line(MutableComponent text, Component line) {
        text.append("\n ").append(line.copy().withStyle(ChatFormatting.GRAY));
    }

    static int heal(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        restore(player, data);
        return Cmd.ok(source, "command.noirm.me.healed");
    }

    /** Full health, chakra, stamina and food; no jutsu cooldowns, fire or harmful effects. */
    static void restore(ServerPlayer player, ShinobiData data) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.getFoodData().eat(20, 1f);
        List<MobEffectInstance> bad = player.getActiveEffects().stream()
                .filter(e -> e.getEffect().getCategory() == MobEffectCategory.HARMFUL).toList();
        bad.forEach(e -> player.removeEffect(e.getEffect()));
        data.refill();
        List.copyOf(data.jutsu().known()).forEach(id -> data.jutsu().setCooldown(id, 0));
        data.markPowersDirty();
    }

    static int max(CommandSourceStack source) throws CommandSyntaxException {
        setStats(source, ShinobiData.MAX_STAT, ShinobiData.MAX_STAT, ShinobiData.MAX_STAT, ShinobiData.MAX_STAT,
                ShinobiData.MAX_STAT, ShinobiData.MAX_STAT);
        return 1;
    }

    /** Sets the stats; {@code control} / {@code willpower} below 0 leave those two as they are. */
    static int setStats(CommandSourceStack source, float speed, float taijutsu, float vitality, float reserves,
                        float control, float willpower) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        data.setSpeed(speed);
        data.setTaijutsu(taijutsu);
        data.setVitality(vitality);
        data.setReserves(reserves);
        if (control >= 0f) {
            data.setControl(control);
        }
        if (willpower >= 0f) {
            data.setWillpower(willpower);
        }
        restore(player, data);
        PowerVisuals.broadcast(player);
        return Cmd.ok(source, "command.noirm.stat", Math.round(speed), Math.round(taijutsu), Math.round(Perception.perception(player)),
                Math.round(vitality), Math.round(reserves), Math.round(data.getControl()), Math.round(data.getWillpower()));
    }
}

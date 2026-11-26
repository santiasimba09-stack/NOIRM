package com.noirm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.dojutsu.Byakugan;
import com.noirm.dojutsu.EyeData;
import com.noirm.dojutsu.Mangekyo;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /noirm eyes}: your dōjutsu at a glance · {@code /noirm eyes clear}: back to plain eyes (cheats).
 * The detailed eye commands are {@code /noirm sharingan}, {@code byakugan} and {@code mangekyo}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class EyesCommand {
    private EyesCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("eyes")
                .executes(ctx -> show(ctx.getSource()))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(Cmd.CHEATS)).executes(ctx -> clear(ctx.getSource())))));
    }

    /** One line: Sharingan stage and XP, Byakugan, Mangekyō and its strain. */
    static Component summary(ShinobiData data) {
        EyeData eyes = data.eyes();
        MutableComponent text = Component.translatable("command.noirm.eyes.label");
        boolean any = false;
        if (eyes.sharinganStage() > 0) {
            text.append(" ").append(Component.translatable("command.noirm.eyes.sharingan", "●".repeat(eyes.sharinganStage()), eyes.sharinganXp())
                    .withStyle(ChatFormatting.RED));
            any = true;
        }
        if (eyes.hasMangekyo()) {
            text.append(" ").append(Component.translatable(eyes.isEternal() ? "command.noirm.eyes.eternal" : "command.noirm.eyes.mangekyo",
                    Math.round(eyes.strain())).withStyle(ChatFormatting.DARK_RED));
            any = true;
        }
        if (eyes.hasByakugan()) {
            text.append(" ").append(Component.translatable("command.noirm.eyes.byakugan").withStyle(ChatFormatting.WHITE));
            any = true;
        }
        if (!any) {
            text.append(" ").append(Component.translatable("command.noirm.eyes.none").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (eyes.isActive()) {
            text.append(" ").append(Component.translatable("command.noirm.eyes.open").withStyle(ChatFormatting.GOLD));
        }
        return text;
    }

    static int show(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        source.sendSuccess(() -> summary(data), false);
        return 1;
    }

    static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = Cmd.player(source);
        ShinobiData data = Cmd.data(source);
        EyeData eyes = data.eyes();
        eyes.setEternal(false);
        eyes.setStrain(0f);
        Mangekyo.clear(data);
        eyes.setActive(false);
        eyes.setSharinganStage(0);
        eyes.setSharinganXp(0);
        eyes.setPressure(0f);
        eyes.setByakugan(false);
        for (ResourceLocation id : Byakugan.JUTSU) {
            data.jutsu().forget(id);
        }
        data.markPowersDirty();
        PowerVisuals.broadcast(player);
        return Cmd.ok(source, "command.noirm.eyes.cleared");
    }
}

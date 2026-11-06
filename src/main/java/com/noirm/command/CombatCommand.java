package com.noirm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatData;
import com.noirm.combat.CombatStyle;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.StyleSystem;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Fighting commands.
 * <ul>
 *   <li>{@code /noirm combat on|off} — combat mode (the R key)</li>
 *   <li>{@code /noirm style} — your style and the ones you can use · {@code style set <style>} — any style (cheats)</li>
 *   <li>{@code /noirm style moves [style]} — which input does which move</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CombatCommand {
    static final SuggestionProvider<CommandSourceStack> STYLES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Cmd.shortIds(CombatData.styles().keySet()), builder);

    private CombatCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("combat")
                        .then(Commands.literal("on").executes(ctx -> mode(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> mode(ctx.getSource(), false))))
                .then(Commands.literal("style")
                        .executes(ctx -> show(ctx.getSource()))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(Cmd.CHEATS))
                                .then(Commands.argument("style", StringArgumentType.greedyString()).suggests(STYLES)
                                        .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "style")))))
                        .then(Commands.literal("moves")
                                .executes(ctx -> moves(ctx.getSource(), StyleSystem.styleOf(Cmd.data(ctx.getSource()))))
                                .then(Commands.argument("style", StringArgumentType.greedyString()).suggests(STYLES)
                                        .executes(ctx -> moves(ctx.getSource(), Cmd.id(StringArgumentType.getString(ctx, "style"))))))));
    }

    static int mode(CommandSourceStack source, boolean on) throws CommandSyntaxException {
        CombatSystem.setCombatMode(Cmd.player(source), on);
        return Cmd.ok(source, on ? "command.noirm.combat.on" : "command.noirm.combat.off");
    }

    static int show(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = Cmd.data(source);
        ResourceLocation current = StyleSystem.styleOf(data);
        List<ResourceLocation> available = StyleSystem.available(data);
        MutableComponent text = Component.translatable("command.noirm.style.current", Component.translatable(StyleSystem.translationKey(current)))
                .withStyle(ChatFormatting.GOLD);
        for (ResourceLocation id : CombatData.styles().keySet().stream().sorted().toList()) {
            CombatStyle style = CombatData.style(id);
            if (style == null || !style.playable()) {
                continue;
            }
            boolean usable = available.contains(id);
            String run = "/noirm style set " + Cmd.shortIds(java.util.List.of(id)).findFirst().orElse(id.toString());
            text.append("\n ").append(Component.literal((id.equals(current) ? "▶ " : usable ? "✔ " : "✖ "))
                            .withStyle(usable ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(StyleSystem.translationKey(id)).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, run))))
                    .append(Component.literal(" " + id).withStyle(ChatFormatting.DARK_GRAY));
            if (!usable) {
                style.missingRequirement(data.getTaijutsu(), data.identity().hasByakugan()).ifPresent(why ->
                        text.append(Component.literal(" ").append(Component.translatable("command.noirm.style.needs." + why,
                                Math.round(style.taijutsu()))).withStyle(ChatFormatting.RED)));
            }
        }
        source.sendSuccess(() -> text, false);
        return available.size();
    }

    static int set(CommandSourceStack source, String name) throws CommandSyntaxException {
        ResourceLocation id = Cmd.id(name);
        if (id == null || CombatData.style(id) == null) {
            return Cmd.fail(source, "command.noirm.style.unknown", name);
        }
        ShinobiData data = Cmd.data(source);
        data.setCombatStyle(id.toString());
        data.markDirty();
        return Cmd.ok(source, "command.noirm.style.set", Component.translatable(StyleSystem.translationKey(id)));
    }

    /** Each input of the style and the move it opens with. */
    static int moves(CommandSourceStack source, ResourceLocation id) {
        CombatStyle style = id == null ? null : CombatData.style(id);
        if (style == null) {
            return Cmd.fail(source, "command.noirm.style.unknown", String.valueOf(id));
        }
        MutableComponent text = Component.translatable("command.noirm.style.moves", Component.translatable(StyleSystem.translationKey(id)))
                .withStyle(ChatFormatting.GOLD);
        for (AttackInput input : AttackInput.values()) {
            ResourceLocation move = style.opener(input);
            if (move == null) {
                continue;
            }
            text.append("\n ").append(Component.translatable("command.noirm.input." + input.getSerializedName()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(pretty(move)).withStyle(ChatFormatting.WHITE));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    /** {@code noirm:brawler/body_hook} → "body hook". */
    static String pretty(ResourceLocation move) {
        String path = move.getPath();
        return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
    }
}

package com.noirm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.entity.MasterNinja;
import com.noirm.master.MasterData;
import com.noirm.master.MasterSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /noirm master} commands:
 * <ul>
 *   <li>{@code /noirm master list} — the masters this world knows</li>
 *   <li>{@code /noirm master spawn <id>} — a master in front of you (cheats)</li>
 *   <li>{@code /noirm master pass <id>} — count a master's test as passed (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MasterCommand {
    private static final int CHEATS = 2;

    private static final SuggestionProvider<CommandSourceStack> MASTERS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(MasterData.all().keySet(), builder);

    private MasterCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("master")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("id", StringArgumentType.greedyString()).suggests(MASTERS)
                                .executes(ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("pass").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("id", StringArgumentType.greedyString()).suggests(MASTERS)
                                .executes(ctx -> pass(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))));
    }

    /** "might_guy" or "noirm:might_guy". */
    private static ResourceLocation parse(String text) {
        return text.contains(":") ? ResourceLocation.tryParse(text) : Noirm.id(text);
    }

    private static int list(CommandSourceStack source) {
        if (MasterData.all().isEmpty()) {
            source.sendFailure(Component.translatable("commands.noirm.master.none"));
            return 0;
        }
        MasterData.all().forEach((id, def) -> source.sendSuccess(() -> Component.literal(id + " — ")
                .append(Component.translatable(def.name())).append(" (" + def.lessons().size() + ")"), false));
        return MasterData.all().size();
    }

    private static int spawn(CommandSourceStack source, String text) {
        ResourceLocation id = parse(text);
        if (id == null || MasterData.get(id) == null) {
            source.sendFailure(Component.translatable("commands.noirm.master.unknown", text));
            return 0;
        }
        Vec3 at = source.getPosition();
        if (source.getEntity() != null) {
            Vec3 look = source.getEntity().getViewVector(1f);
            at = at.add(look.x * 3, 0, look.z * 3);
        }
        MasterNinja master = MasterSystem.spawn(source.getLevel(), id, at, source.getRotation().y + 180f);
        if (master == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.noirm.master.spawned", master.getDisplayName()), true);
        return 1;
    }

    private static int pass(CommandSourceStack source, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation id = parse(text);
        if (id == null || MasterData.get(id) == null) {
            source.sendFailure(Component.translatable("commands.noirm.master.unknown", text));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        MasterSystem.passTest(player, id);
        return 1;
    }
}

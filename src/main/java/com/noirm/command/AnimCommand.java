package com.noirm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.noirm.Noirm;
import com.noirm.combat.CombatAnimated;
import com.noirm.network.CombatClientPackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

/**
 * {@code /noirm anim <animation> [ticks] [targets]}: plays an animation on yourself (or on
 * players / ninjas), to preview animations while making them. Ticks default to the file's length.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class AnimCommand {
    private AnimCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("anim")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("animation", ResourceLocationArgument.id())
                        .executes(ctx -> play(ctx.getSource(), ResourceLocationArgument.getId(ctx, "animation"), 0, null))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 400))
                                .executes(ctx -> play(ctx.getSource(), ResourceLocationArgument.getId(ctx, "animation"),
                                        IntegerArgumentType.getInteger(ctx, "ticks"), null))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> play(ctx.getSource(), ResourceLocationArgument.getId(ctx, "animation"),
                                                IntegerArgumentType.getInteger(ctx, "ticks"), EntityArgument.getEntities(ctx, "targets"))))))));
    }

    private static int play(CommandSourceStack source, ResourceLocation id, int ticks, Collection<? extends Entity> targets) {
        ResourceLocation animation = id.getNamespace().equals("minecraft") ? Noirm.id(id.getPath()) : id;
        Collection<? extends Entity> who = targets != null ? targets : source.getEntity() == null ? List.of() : List.of(source.getEntity());
        int length = ticks > 0 ? ticks : 20;
        int played = 0;
        for (Entity entity : who) {
            if (entity instanceof Player) {
                NoirmNetwork.sendToTrackingAndSelf(entity, new CombatClientPackets.PlayAnimation(entity.getId(), animation, length));
                played++;
            } else if (entity instanceof CombatAnimated animated) {
                animated.playCombatAnimation(animation, length);
                played++;
            }
        }
        int count = played;
        source.sendSuccess(() -> Component.literal("Playing " + animation + " (" + length + " ticks) on " + count), false);
        return played;
    }
}

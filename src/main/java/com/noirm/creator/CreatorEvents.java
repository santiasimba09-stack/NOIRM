package com.noirm.creator;

import com.noirm.Noirm;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Character creation in the game: the hold on a newcomer (frozen, untouchable, can't act), the
 * height that resizes the hitbox and eyes, and sending looks to whoever sees a player.
 * (The first-join hook itself is in {@code PowerEvents.loggedIn}, where the old random clan was.)
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CreatorEvents {
    private CreatorEvents() {}

    /** Height: the hitbox and eye height scale with the look (both sides, every pose). */
    @SubscribeEvent
    public static void size(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        float height;
        try {
            height = CharacterCreator.height(player);
        } catch (RuntimeException e) {
            return; // capabilities not attached yet (the entity is still being built)
        }
        if (height == 1f) {
            return;
        }
        float eyes = event.getNewEyeHeight();
        event.setNewSize(event.getNewSize().scale(height));
        event.setNewEyeHeight(eyes * height);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        CharacterCreator.data(player).ifPresent(creator -> CharacterCreator.enforceHold(player, creator));
    }

    /** Held players can't be hurt, and can't hurt anyone. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (CharacterCreator.isHeld(event.getEntity()) || CharacterCreator.isHeld(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attackEntity(AttackEntityEvent event) {
        if (CharacterCreator.isHeld(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** No using blocks or items while held. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent event) {
        if (event.isCancelable() && CharacterCreator.isHeld(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Monsters don't pick a held player as a target. */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        if (CharacterCreator.isHeld(event.getNewTarget())) {
            event.setCanceled(true);
        }
    }

    /** A viewer starts seeing a player: send that player's look. */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            NoirmNetwork.sendToPlayer(viewer, CreatorPackets.LookSync.of(target));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
            ShinobiCapability.get(player).ifPresent(data -> {
                if (!data.creator().hasLook() && data.creator().mode() == CreatorData.Mode.NONE) {
                    player.sendSystemMessage(Component.translatable("message.noirm.creator.no_look_hint"));
                }
            });
        }
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    /** A new player object (login, respawn, new dimension): resize it and tell everyone its look. */
    private static void refresh(ServerPlayer player) {
        player.refreshDimensions();
        CharacterCreator.broadcastLook(player);
    }
}

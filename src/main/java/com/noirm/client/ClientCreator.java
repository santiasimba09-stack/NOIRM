package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.creator.CreatorData;
import com.noirm.creator.CreatorPackets;
import com.noirm.creator.PlayerLook;
import com.noirm.player.ShinobiCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The client side of character creation: opening the screen and storing everyone's looks. */
public final class ClientCreator {
    /** The last state the server sent (null when the screen is closed). */
    private static CreatorPackets.State current;
    /** The look being edited (kept if the screen is closed and reopened, e.g. after the pause menu). */
    private static PlayerLook draft;

    private ClientCreator() {}

    /** The server opened, refreshed or closed the creation screen. */
    public static void state(CreatorPackets.State state) {
        Minecraft mc = Minecraft.getInstance();
        if (state.modeEnum() == CreatorData.Mode.NONE) {
            closed();
            if (mc.screen instanceof CreatorScreen screen) {
                screen.closeFromServer();
            }
            return;
        }
        boolean wasOpen = current != null;
        current = state;
        if (!wasOpen) {
            draft = null; // a fresh session starts from the server's look
        }
        if (mc.screen instanceof CreatorScreen screen) {
            screen.update(state);
        } else {
            mc.setScreen(new CreatorScreen(state));
        }
    }

    /** The look being edited: the draft so far, or the one the server suggested. */
    static PlayerLook draft(CreatorPackets.State state) {
        if (draft == null) {
            draft = state.look().copy();
        }
        return draft;
    }

    static void setDraft(PlayerLook look) {
        draft = look;
    }

    /** The screen was closed on purpose (confirmed or cancelled): don't reopen it. */
    static void closed() {
        current = null;
        draft = null;
    }

    /** Someone's look: stored on their player (the renderer, hitbox and skin read it from there). */
    public static void look(CreatorPackets.LookSync packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(packet.entityId());
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(data -> data.creator().setLook(packet.look()));
            player.refreshDimensions();
        }
    }

    /** Reopens the screen if a newcomer closed it (the pause menu), and forgets everything on leaving. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class CreatorReopen {
        private CreatorReopen() {}

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || current == null) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null && current.held()) {
                mc.setScreen(new CreatorScreen(current));
            }
        }

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            closed();
        }
    }
}

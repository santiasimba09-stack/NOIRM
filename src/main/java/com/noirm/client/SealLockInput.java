package com.noirm.client;

import com.noirm.Noirm;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client half of {@link com.noirm.combat.SealLock}: while your hands form seals, a left click
 * (light attack, or a vanilla punch with combat mode off) does nothing at all, with no swing, and
 * the first try in each cast says why above the hotbar. The server refuses those attacks anyway.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class SealLockInput {
    /** If the "seals done" message were ever lost, the lock lifts on its own after this long. */
    private static final int MAX_LOCK_TICKS = 100;

    private static boolean forming;
    private static boolean hinted;
    private static int lockTicks;

    private SealLockInput() {}

    /** From the server: your seal phase started or ended. */
    public static void setForming(boolean value) {
        forming = value;
        lockTicks = 0;
        if (value) {
            hinted = false;
        }
    }

    public static boolean forming() {
        return forming;
    }

    /** Should this melee press be dropped? Says why the first time in a cast. */
    public static boolean refuse(LocalPlayer player) {
        if (!forming) {
            return false;
        }
        if (!hinted) {
            hinted = true;
            player.displayClientMessage(Component.translatable("message.noirm.jutsu.hands_busy").withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    /** Runs before {@link CombatInput}: a cancelled click never reaches it. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && event.isAttack() && refuse(player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && forming && ++lockTicks > MAX_LOCK_TICKS) {
            forming = false;
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forming = false;
    }
}

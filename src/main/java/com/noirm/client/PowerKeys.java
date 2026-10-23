package com.noirm.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.noirm.Noirm;
import com.noirm.jutsu.JutsuBook;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Power keys: G = Sharingan, B = chakra cloak, J = jutsu screen, N = meditate. In combat mode, the hotbar
 * keys 1–4 cast the jutsu in loadout slots 1–4 (holding them charges or channels) instead of
 * switching hotbar slots.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class PowerKeys {
    public static final KeyMapping SHARINGAN = key("sharingan", GLFW.GLFW_KEY_G);
    public static final KeyMapping CLOAK = key("cloak", GLFW.GLFW_KEY_B);
    public static final KeyMapping JUTSU_SCREEN = key("jutsu_screen", GLFW.GLFW_KEY_J);
    public static final KeyMapping MEDITATE = key("meditate", GLFW.GLFW_KEY_N);

    private static final boolean[] HELD = new boolean[JutsuBook.SLOTS];

    private PowerKeys() {}

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.noirm." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, defaultKey, CombatKeys.CATEGORY);
    }

    /**
     * Runs before vanilla handles keys this tick, so in combat mode the 1–4 presses are taken for
     * jutsu and don't also switch the hotbar.
     */
    @SubscribeEvent
    public static void clientTickStart(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (event.phase != TickEvent.Phase.START || player == null) {
            return;
        }
        boolean active = ClientCombat.isCombatMode() && minecraft.screen == null;
        for (int slot = 0; slot < JutsuBook.SLOTS; slot++) {
            KeyMapping hotbarKey = minecraft.options.keyHotbarSlots[slot];
            if (active) {
                while (hotbarKey.consumeClick()) {
                    if (!HELD[slot]) {
                        HELD[slot] = true;
                        NoirmNetwork.sendToServer(new PowerPackets.JutsuKey(slot, true));
                    }
                }
            }
            if (HELD[slot] && (!hotbarKey.isDown() || !active)) {
                HELD[slot] = false;
                NoirmNetwork.sendToServer(new PowerPackets.JutsuKey(slot, false));
            }
        }
    }

    @SubscribeEvent
    public static void clientTickEnd(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.player == null) {
            return;
        }
        ClientPowers.tick();
        while (SHARINGAN.consumeClick()) {
            NoirmNetwork.sendToServer(new PowerPackets.TogglePower(PowerPackets.Toggle.SHARINGAN));
        }
        while (CLOAK.consumeClick()) {
            NoirmNetwork.sendToServer(new PowerPackets.TogglePower(PowerPackets.Toggle.CLOAK));
        }
        while (MEDITATE.consumeClick()) {
            NoirmNetwork.sendToServer(new PowerPackets.TogglePower(PowerPackets.Toggle.MEDITATE));
        }
        while (JUTSU_SCREEN.consumeClick()) {
            minecraft.setScreen(new JutsuScreen());
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        java.util.Arrays.fill(HELD, false);
        ClientPowers.reset();
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(SHARINGAN);
            event.register(CLOAK);
            event.register(JUTSU_SCREEN);
            event.register(MEDITATE);
        }
    }
}

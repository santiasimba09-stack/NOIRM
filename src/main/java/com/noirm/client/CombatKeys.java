package com.noirm.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.noirm.Noirm;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * NOIRM's keys. All can be rebound in Options → Controls → Key Binds → NOIRM.
 * Attacks use the normal mouse buttons while combat mode is on.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CombatKeys {
    public static final String CATEGORY = "key.categories.noirm";

    public static final KeyMapping COMBAT_MODE = key("combat_mode", GLFW.GLFW_KEY_R);
    public static final KeyMapping DODGE = key("dodge", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping GUARD = key("guard", GLFW.GLFW_KEY_V);
    public static final KeyMapping LOCK_ON = key("lock_on", GLFW.GLFW_KEY_Z);

    private CombatKeys() {}

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.noirm." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(COMBAT_MODE);
        event.register(DODGE);
        event.register(GUARD);
        event.register(LOCK_ON);
    }
}

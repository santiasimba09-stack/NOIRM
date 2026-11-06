package com.noirm.client;

import com.noirm.Noirm;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Camera feel: shake on impact, and a quick zoom pulse when a focus moment starts. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class CombatCamera {
    /** Degrees of shake at full strength. */
    private static final float SHAKE_DEGREES = 2.5f;

    private CombatCamera() {}

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float shake = ClientCombat.shake();
        if (shake <= 0 || Minecraft.getInstance().level == null) {
            return;
        }
        float t = (float) (Minecraft.getInstance().level.getGameTime() + event.getPartialTick());
        event.setPitch(event.getPitch() + Mth.sin(t * 7.3f) * shake * SHAKE_DEGREES);
        event.setYaw(event.getYaw() + Mth.cos(t * 5.9f) * shake * SHAKE_DEGREES);
        event.setRoll(event.getRoll() + Mth.sin(t * 9.1f) * shake * SHAKE_DEGREES * 0.6f);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        int focus = ClientCombat.focusTicks();
        if (focus > 0) {
            float strength = focus / (float) ClientCombat.FOCUS_PULSE_TICKS;
            event.setFOV(event.getFOV() * (1.0 - 0.1 * strength));
        }
    }
}

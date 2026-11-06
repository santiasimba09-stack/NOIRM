package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.chakra.WaterWalking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Water walking, the part only your own computer can do: holding jump while in the water near
 * the surface springs you back up onto it (your movement runs on the client). The surface itself
 * and the chakra drain are in {@link WaterWalking}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class WaterWalkInput {
    private WaterWalkInput() {}

    @SubscribeEvent
    public static void waterWalkTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.isPaused() || !player.input.jumping || !WaterWalking.canResurface(player)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < WaterWalking.RESURFACE_SPEED * 0.5) {
            if (motion.y < 0.1) {
                player.playSound(SoundEvents.PLAYER_SPLASH, 0.25f, 1.5f);
            }
            player.setDeltaMovement(motion.x, WaterWalking.RESURFACE_SPEED, motion.z);
        }
    }
}

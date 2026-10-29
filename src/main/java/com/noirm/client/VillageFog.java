package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.world.village.VillagePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The air inside a village: walking into the Mist village, a pale mist closes in over a few
 * seconds (fog from 4 to 48 blocks, tinted grey-blue) and lifts again when you leave; the Cloud
 * village has a light white haze. The server says which village the player is in
 * ({@link com.noirm.network.VillagePackets.Atmosphere}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VillageFog {
    /** How fast the mist comes and goes (fraction per tick: about 4 seconds). */
    private static final float FADE = 0.0125f;
    private static int village = -1;
    private static float strength, previous;
    private static float near = 4, far = 48, red = 0.78f, green = 0.82f, blue = 0.86f;

    private VillageFog() {}

    public static void setVillage(int palette) {
        village = palette;
    }

    /** The mist for a village look: {near, far, r, g, b}, or null for none. */
    private static float[] mistFor(int palette) {
        if (palette == VillagePalette.MIST.ordinal()) {
            return new float[] {4, 48, 0.78f, 0.82f, 0.86f};
        }
        if (palette == VillagePalette.CLOUD.ordinal()) {
            return new float[] {24, 140, 0.92f, 0.94f, 0.97f};
        }
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().level == null) {
            return;
        }
        previous = strength;
        float[] mist = mistFor(village);
        if (mist != null) {
            near = mist[0];
            far = mist[1];
            red = mist[2];
            green = mist[3];
            blue = mist[4];
        }
        strength = Mth.clamp(strength + (mist != null ? FADE : -FADE), 0, 1);
    }

    private static float current(double partialTick) {
        return Mth.lerp((float) partialTick, previous, strength);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        float s = current(event.getPartialTick());
        if (s <= 0 || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        // Ease in, and never push the fog further out than the game already has it.
        float t = s * s * (3 - 2 * s);
        event.setNearPlaneDistance(Mth.lerp(t, event.getNearPlaneDistance(), Math.min(near, event.getNearPlaneDistance())));
        event.setFarPlaneDistance(Mth.lerp(t, event.getFarPlaneDistance(), Math.min(far, event.getFarPlaneDistance())));
        event.setCanceled(true); // (Forge only applies new distances when the event is cancelled.)
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float s = current(event.getPartialTick());
        if (s <= 0) {
            return;
        }
        float t = s * s * (3 - 2 * s) * 0.8f;
        event.setRed(Mth.lerp(t, event.getRed(), red));
        event.setGreen(Mth.lerp(t, event.getGreen(), green));
        event.setBlue(Mth.lerp(t, event.getBlue(), blue));
    }
}

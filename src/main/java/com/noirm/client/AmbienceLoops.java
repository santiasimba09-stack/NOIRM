package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.sound.NoirmSounds;
import com.noirm.world.village.VillageAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Ambience loops, client side only: soft wind and distant life inside a village, a still drone and
 * falling water in the Stone Hermit's sanctuary. The server says where you are (the same message as
 * the village mist, {@link VillageFog}); the loop fades in over a few seconds and fades out when you
 * leave. Volume follows the Ambient/Environment slider.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AmbienceLoops {
    /** Loudest the loop gets (the files themselves are mixed quiet). */
    private static final float MAX_VOLUME = 0.7f;
    /** Fade speed per tick: about 4 seconds in, 3 seconds out. */
    private static final float FADE_IN = MAX_VOLUME / 80f, FADE_OUT = MAX_VOLUME / 60f;

    private static int place = -1;
    private static Loop current, fading;

    private AmbienceLoops() {}

    /** From the village atmosphere message: a palette ordinal, {@link VillageAtmosphere#SANCTUARY}, or -1. */
    public static void setPlace(int value) {
        place = value;
    }

    /** The loop wanted for the current place, or null for silence. */
    static RegistryObject<SoundEvent> wanted() {
        if (place == VillageAtmosphere.SANCTUARY) {
            return NoirmSounds.AMBIENT_SANCTUARY;
        }
        return place >= 0 ? NoirmSounds.AMBIENT_VILLAGE : null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            place = -1;
            for (Loop loop : new Loop[]{current, fading}) {
                if (loop != null) {
                    loop.finish();
                }
            }
            current = fading = null;
            return;
        }
        RegistryObject<SoundEvent> want = wanted();
        if (current != null && current.source != want) {
            current.leaving = true; // fades out on its own (a cross-fade if another loop starts)
            fading = current;
            current = null;
        }
        if (fading != null && fading.isStopped()) {
            fading = null;
        }
        if (current != null && (current.isStopped()
                || (++current.age > 40 && !minecraft.getSoundManager().isActive(current)))) {
            current = null; // the sound engine dropped it (a resource reload): start it again
        }
        if (want != null && current == null) {
            current = new Loop(want);
            minecraft.getSoundManager().play(current);
        }
    }

    /** One looping, non-positional ambience track with its own fade. */
    static final class Loop extends AbstractTickableSoundInstance {
        final RegistryObject<SoundEvent> source;
        boolean leaving;
        /** Ticks since it was started (the sound engine needs a moment before it reports it active). */
        int age;

        Loop(RegistryObject<SoundEvent> source) {
            super(source.get(), SoundSource.AMBIENT, RandomSource.create());
            this.source = source;
            this.looping = true;
            this.delay = 0;
            this.relative = true; // follows the listener: everywhere around you, not at a point
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001f;
        }

        void finish() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true; // starts at volume ~0 and fades in
        }

        @Override
        public void tick() {
            if (leaving) {
                volume -= FADE_OUT;
                if (volume <= 0.001f) {
                    volume = 0f;
                    stop();
                }
            } else {
                volume = Math.min(MAX_VOLUME, volume + FADE_IN);
            }
        }
    }
}

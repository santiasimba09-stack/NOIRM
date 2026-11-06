package com.noirm.client.ui;

import com.noirm.sound.NoirmSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * The UI kit's sounds: quiet paper and wood (our own, tools/soundgen). Screens never pick sounds
 * themselves: {@link ScreenMotion} plays open, close and tab, {@link HoverTracker} the hover tick,
 * and buttons call {@link #click()}. UI sounds follow the Master volume, like vanilla's buttons.
 */
public final class UiSounds {
    /** Hover ticks closer together than this are dropped (sweeping over a list stays quiet). */
    private static final long HOVER_GAP_MS = 70;
    private static long lastHover;

    private UiSounds() {}

    public static void play(RegistryObject<SoundEvent> sound, float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null || !sound.isPresent()) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1f, volume));
    }

    public static void click() {
        play(NoirmSounds.UI_CLICK, 1f);
    }

    public static void open() {
        play(NoirmSounds.UI_OPEN, 1f);
    }

    public static void close() {
        play(NoirmSounds.UI_CLOSE, 1f);
    }

    public static void tab() {
        play(NoirmSounds.UI_TAB, 1f);
    }

    public static void hover() {
        long now = Anim.now();
        if (now - lastHover >= HOVER_GAP_MS) {
            lastHover = now;
            play(NoirmSounds.UI_HOVER, 1f);
        }
    }
}

package com.noirm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Perception focus, the blueprint's "time dilation" (client only). When the server says an incoming
 * heavy attack is playing slower for you because of your perception, the screen's edges go cold
 * and grey, a heartbeat and a low whoosh play, and the rest of the world's sounds dip and drop in
 * pitch, as if muffled. How strong it is follows the slowdown: faint at 15%, full at 40%.
 *
 * <p>The game's speed is never touched: the server only stretches that one attack's wind-up (see
 * {@code CombatSystem.startAttack}). A just-frame parry inside the window adds a bright flash.
 *
 * <p>The muffle scales the volume and pitch of sounds that start during the window (a stand-in for
 * the blueprint's ~100 Hz low-pass; real OpenAL filters would need access to the sound engine's
 * internals). Our own focus sounds play on the master channel and aren't muffled.
 */
public final class PerceptionFocus {
    private static final ResourceLocation VIGNETTE = Noirm.vanillaId("textures/misc/vignette.png");
    /** Ticks to fade in and out. */
    private static final int FADE_IN = 3;
    private static final int FADE_OUT = 8;
    /** At full strength: world sounds at 35% volume and 85% pitch. */
    private static final float MUFFLE_VOLUME = 0.65f;
    private static final float MUFFLE_PITCH = 0.15f;
    private static final int FLASH_TICKS = 6;
    /** The blueprint's Cold Iron, for the grey wash. */
    private static final int COLD_IRON = 0x606A73;

    private static int age;
    private static int length;
    private static float intensity;
    private static int flashLeft;

    private PerceptionFocus() {}

    /** A focus window opens: {@code ticks} long, for an attack slowed by {@code slowdown} (0.15..). */
    public static void start(int ticks, float slowdown) {
        float strength = 0.35f + 0.65f * Mth.clamp((slowdown - CombatSystem.PERCEPTION_FOCUS_MIN)
                / (CombatSystem.PERCEPTION_FOCUS_FULL - CombatSystem.PERCEPTION_FOCUS_MIN), 0f, 1f);
        boolean fresh = !active();
        age = 0;
        length = Math.max(8, ticks) + FADE_OUT;
        intensity = Math.max(fresh ? 0f : intensity, strength);
        Minecraft minecraft = Minecraft.getInstance();
        if (fresh && minecraft.player != null) {
            SoundManager sounds = minecraft.getSoundManager();
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.7f, 0.55f + 0.35f * intensity));
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 0.25f * intensity));
        }
    }

    /** A just-frame parry: a white flash over the focus. */
    public static void justFrame() {
        flashLeft = FLASH_TICKS;
    }

    public static boolean active() {
        return age < length;
    }

    static void tick() {
        if (age < length) {
            age++;
        }
        if (flashLeft > 0) {
            flashLeft--;
        }
    }

    static void reset() {
        age = length = 0;
        flashLeft = 0;
    }

    /** 0..1: how strongly the focus shows right now (eases in, holds, fades out). */
    public static float strength(float partialTick) {
        if (!active()) {
            return 0f;
        }
        float t = age + partialTick;
        float in = Mth.clamp(t / FADE_IN, 0f, 1f);
        float out = Mth.clamp((length - t) / FADE_OUT, 0f, 1f);
        return intensity * Math.min(in, out);
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.player == null) {
            return;
        }
        float s = strength(partialTick);
        if (s > 0.01f) {
            // A grey-blue wash drains some colour from the whole view…
            graphics.fill(0, 0, width, height, (Math.round(85 * s) << 24) | COLD_IRON);
            // …and cold edges: the vanilla vignette darkens by its colour, red most, blue least.
            // Two full-screen passes: the edges close in without a visible seam.
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            RenderSystem.setShaderColor(0.8f * s, 0.65f * s, 0.45f * s, 1f);
            graphics.blit(VIGNETTE, 0, 0, -90, 0f, 0f, width, height, width, height);
            graphics.blit(VIGNETTE, 0, 0, -90, 0f, 0f, width, height, width, height);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        if (flashLeft > 0) {
            float f = (flashLeft - partialTick) / FLASH_TICKS;
            graphics.fill(0, 0, width, height, (Math.round(150 * Math.max(0f, f) * f) << 24) | 0xE8F4FF);
        }
    }

    // ------------------------------------------------------------ muffling the world

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
    public static final class FocusSoundMuffler {
        private FocusSoundMuffler() {}

        @SubscribeEvent
        public static void muffleFocusSounds(PlaySoundEvent event) {
            SoundInstance sound = event.getSound();
            if (sound == null || !active() || sound instanceof Muffled || sound instanceof TickableSoundInstance) {
                return;
            }
            SoundSource source = sound.getSource();
            if (source == SoundSource.MASTER || source == SoundSource.MUSIC || source == SoundSource.RECORDS) {
                return; // our own cues, music and jukeboxes stay clear
            }
            float s = strength(0f);
            if (s > 0.01f) {
                event.setSound(new Muffled(sound, 1f - MUFFLE_VOLUME * s, 1f - MUFFLE_PITCH * s));
            }
        }
    }

    /** A sound played quieter and lower: wraps the original and scales its volume and pitch. */
    private record Muffled(SoundInstance inner, float volumeScale, float pitchScale) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * volumeScale;
        }

        @Override
        public float getPitch() {
            return inner.getPitch() * pitchScale;
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }

        @Override
        public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(
                net.minecraft.client.sounds.SoundBufferLibrary library, Sound sound, boolean looping) {
            return inner.getStream(library, sound, looping);
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class PerceptionFocusOverlayRegistration {
        private PerceptionFocusOverlayRegistration() {}

        @SubscribeEvent
        public static void registerPerceptionFocusOverlay(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("perception_focus", PerceptionFocus::render);
        }
    }
}

package com.noirm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.noirm.Noirm;
import com.noirm.network.SpiritPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What a Yin genjutsu and Body Pathway Derangement do to the local player (client only). The
 * server decides who is caught and for how long ({@link SpiritPackets.Vision}); here it's drawn.
 * Both stay playable: you can still see your enemy, the HUD and your hands.
 * <ul>
 *   <li><b>Genjutsu:</b> the world tints violet, ink closes in from the edges and breathes, the
 *       view swims a little (a slow FOV sway). Kai clears it at once with a white flash.</li>
 *   <li><b>Derangement:</b> your movement keys are scrambled (forward walks back, left walks right)
 *       with a faint green shimmer at the edges so you know why.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class SpiritVision {
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    /** How far the view sways in a genjutsu (share of the FOV). */
    public static final float GENJUTSU_SWAY = 0.035f;
    private static final int RELEASE_FLASH_TICKS = 8;

    private static int genjutsuLeft;
    private static int genjutsuTotal = 1;
    private static int derangeLeft;
    private static int derangeTotal = 1;
    private static int releaseFlash;
    private static int age;

    private SpiritVision() {}

    public static void apply(int kind, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (kind) {
            case SpiritPackets.GENJUTSU -> {
                genjutsuLeft = Math.max(genjutsuLeft, ticks);
                genjutsuTotal = Math.max(1, genjutsuLeft);
                if (minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.7f, 0.6f);
                }
            }
            case SpiritPackets.DERANGE -> {
                derangeLeft = Math.max(derangeLeft, ticks);
                derangeTotal = Math.max(1, derangeLeft);
            }
            case SpiritPackets.RELEASE -> {
                if (genjutsuLeft > 0) {
                    releaseFlash = RELEASE_FLASH_TICKS;
                }
                genjutsuLeft = 0;
            }
            default -> { }
        }
    }

    /** Is the local player's movement scrambled right now? (for tests and the HUD) */
    public static boolean deranged() {
        return derangeLeft > 0;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        age++;
        if (genjutsuLeft > 0) {
            genjutsuLeft--;
        }
        if (derangeLeft > 0) {
            derangeLeft--;
        }
        if (releaseFlash > 0) {
            releaseFlash--;
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        genjutsuLeft = 0;
        derangeLeft = 0;
        releaseFlash = 0;
    }

    /** Derangement: the body's signals cross. Forward is back and left is right. */
    @SubscribeEvent
    public static void scrambleKeys(MovementInputUpdateEvent event) {
        if (derangeLeft <= 0 || !(event.getEntity() instanceof LocalPlayer)) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = -input.forwardImpulse;
        input.leftImpulse = -input.leftImpulse;
        boolean up = input.up;
        input.up = input.down;
        input.down = up;
        boolean left = input.left;
        input.left = input.right;
        input.right = left;
    }

    /** A genjutsu makes the view swim, slowly and only a little. */
    @SubscribeEvent
    public static void swayFov(ComputeFovModifierEvent event) {
        float strength = strength(genjutsuLeft, genjutsuTotal);
        if (strength > 0f) {
            event.setNewFovModifier(event.getNewFovModifier() * (1f + GENJUTSU_SWAY * strength * Mth.sin(age * 0.12f)));
        }
    }

    private static float strength(int left, int total) {
        if (left <= 0) {
            return 0f;
        }
        float t = left / (float) total;
        float fadeIn = Math.min(1f, (total - left) / 6f + 0.3f);
        return Math.min(1f, t * 4f) * Math.min(1f, fadeIn);
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        float genjutsu = strength(genjutsuLeft, genjutsuTotal);
        if (genjutsu > 0f) {
            float breath = 0.75f + 0.25f * Mth.sin((age + partialTick) * 0.18f);
            graphics.fill(0, 0, width, height, (Math.round(70 * genjutsu) << 24) | 0x3A0A6A);
            vignette(graphics, width, height, 0.6f * genjutsu * breath, 0.75f, 1f, 0.55f, 2);
            // Ink bands drifting over the top and bottom of the view: the false world seeping in.
            int band = Math.round(height * 0.09f * genjutsu * breath);
            graphics.fill(0, 0, width, band, (Math.round(170 * genjutsu) << 24) | 0x07030C);
            graphics.fill(0, height - band, width, height, (Math.round(170 * genjutsu) << 24) | 0x07030C);
        }
        float derange = strength(derangeLeft, derangeTotal);
        if (derange > 0f) {
            float shimmer = 0.6f + 0.4f * Mth.sin((age + partialTick) * 0.5f);
            vignette(graphics, width, height, 0.4f * derange * shimmer, 1f, 0.35f, 0.9f, 1);
        }
        if (releaseFlash > 0) {
            float flash = (releaseFlash - partialTick) / RELEASE_FLASH_TICKS;
            graphics.fill(0, 0, width, height, (Math.round(150 * Math.max(0f, flash)) << 24) | 0xF4ECFF);
        }
    }

    /** The vanilla vignette, tinted: takes away {@code amount} × (r, g, b) at the edges (take less of a channel to keep its colour). */
    private static void vignette(GuiGraphics graphics, int width, int height, float amount, float r, float g, float b, int passes) {
        if (amount <= 0.01f) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        for (int i = 0; i < passes; i++) {
            RenderSystem.setShaderColor(amount * r, amount * g, amount * b, 1f);
            graphics.blit(VIGNETTE, 0, 0, -90, 0f, 0f, width, height, width, height);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class SpiritVisionRegistration {
        private SpiritVisionRegistration() {}

        @SubscribeEvent
        public static void registerSpiritVision(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("spirit_vision", SpiritVision::render);
        }
    }
}

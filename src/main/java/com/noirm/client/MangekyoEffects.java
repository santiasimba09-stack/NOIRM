package com.noirm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.noirm.Noirm;
import com.noirm.dojutsu.Mangekyo;
import com.noirm.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Mangekyō on the local player's screen (client only):
 * <ul>
 *   <li><b>Awakening:</b> a dark red flash and the eye's pattern revealed, huge and turning
 *       (the Eternal Mangekyō shows both patterns merging).</li>
 *   <li><b>Strain:</b> the vision darkens from the edges as the strain grows (from 10), heavier
 *       past each threshold; blind, only a dim hole in the middle remains.</li>
 *   <li><b>Tsukuyomi:</b> caught in one, the world turns red and black for a while.</li>
 * </ul>
 */
public final class MangekyoEffects {
    /** The pattern atlas: {@link Mangekyo#PATTERNS} cells of {@link #CELL}×{@link #CELL}, left to right. */
    public static final ResourceLocation PATTERNS = Noirm.id("textures/entity/mangekyo_patterns.png");
    public static final int CELL = 32;
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    private static final int REVEAL_TICKS = 80;

    private static int revealAge = -1;
    private static int revealPattern;
    private static int revealDonor = -1;
    private static int tsukuyomiLeft;
    private static int tsukuyomiTotal = 1;

    private MangekyoEffects() {}

    static void onAwakened(int pattern, int donor) {
        revealAge = 0;
        revealPattern = pattern;
        revealDonor = donor;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
        }
    }

    public static void onTsukuyomi(int ticks) {
        tsukuyomiLeft = ticks;
        tsukuyomiTotal = Math.max(1, ticks);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
        }
    }

    static void tick() {
        if (revealAge >= 0 && ++revealAge > REVEAL_TICKS) {
            revealAge = -1;
        }
        if (tsukuyomiLeft > 0) {
            tsukuyomiLeft--;
        }
    }

    static void reset() {
        revealAge = -1;
        tsukuyomiLeft = 0;
    }

    /** How dark the edges get for {@code strain} (0..1); 1 = blind. */
    public static float strainDarkness(float strain) {
        return Mth.clamp((strain - 10f) / (Mangekyo.MAX_STRAIN - 10f), 0f, 1f);
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.player == null) {
            return;
        }
        PowerPackets.SyncPowers powers = ClientPowers.get();
        if (powers != null && powers.mangekyo() && !powers.eternal()) {
            drawStrain(graphics, width, height, powers.mangekyoStrain(), powers.blind());
        }
        if (tsukuyomiLeft > 0) {
            drawTsukuyomi(graphics, width, height, partialTick);
        }
        if (revealAge >= 0) {
            drawReveal(graphics, width, height, partialTick);
        }
    }

    private static void drawStrain(GuiGraphics graphics, int width, int height, float strain, boolean blind) {
        float dark = blind ? 1f : strainDarkness(strain);
        if (dark <= 0f) {
            return;
        }
        // The vanilla vignette darkens the screen by its colour: stack it to close in from the edges.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        int passes = 1 + Math.round(dark * 4);
        for (int i = 0; i < passes; i++) {
            float shade = Math.min(1f, dark * 1.2f);
            RenderSystem.setShaderColor(shade, shade, shade, 1f);
            int inset = -Math.round(width * 0.25f * (1f - dark)) + i * Math.round(width * 0.03f * dark);
            graphics.blit(VIGNETTE, inset, inset * height / Math.max(1, width), -90, 0f, 0f,
                    width - 2 * inset, height - 2 * inset * height / Math.max(1, width), width, height);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        if (dark > 0.6f) {
            graphics.fill(0, 0, width, height, (Math.round(140 * (dark - 0.6f)) << 24)); // the whole world dims
        }
        if (blind) {
            graphics.fill(0, 0, width, height, 0x90000000);
        }
    }

    private static void drawTsukuyomi(GuiGraphics graphics, int width, int height, float partialTick) {
        float left = Math.max(0f, tsukuyomiLeft - partialTick) / tsukuyomiTotal;
        float strength = Math.min(1f, left * 3f);
        graphics.fill(0, 0, width, height, (Math.round(120 * strength) << 24) | 0xB00010);
        int band = Math.round(height * 0.12f * strength);
        graphics.fill(0, 0, width, band, (Math.round(230 * strength) << 24));
        graphics.fill(0, height - band, width, height, (Math.round(230 * strength) << 24));
        // A red moon high in the sky.
        graphics.pose().pushPose();
        graphics.pose().translate(width * 0.5f, height * 0.25f, 0);
        graphics.pose().scale(6f, 6f, 1f);
        graphics.drawCenteredString(Minecraft.getInstance().font, "●", 0, -4, (Math.round(200 * strength) << 24) | 0xFF1020);
        graphics.pose().popPose();
    }

    private static void drawReveal(GuiGraphics graphics, int width, int height, float partialTick) {
        float t = (revealAge + partialTick) / REVEAL_TICKS;
        float flash = Math.max(0f, 1f - t * 3f);
        graphics.fill(0, 0, width, height, (Math.round(170 * flash) << 24) | 0x600008);
        float alpha = t < 0.15f ? t / 0.15f : t > 0.65f ? Math.max(0f, (1f - t) / 0.35f) : 1f;
        int size = Math.round(Math.min(width, height) * (0.45f + 0.15f * t));
        drawPattern(graphics, width / 2f, height / 2f, size, revealPattern, t * 120f, alpha);
        if (revealDonor >= 0) {
            drawPattern(graphics, width / 2f, height / 2f, size, revealDonor, -t * 120f + 45f, alpha * 0.75f);
        }
    }

    /** One eye pattern, centred at (x, y), {@code size} pixels, turned by {@code degrees}. */
    static void drawPattern(GuiGraphics graphics, float x, float y, int size, int pattern, float degrees, float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        int cell = Math.floorMod(pattern, Mangekyo.PATTERNS);
        graphics.blit(PATTERNS, -size / 2, -size / 2, size, size, cell * CELL, 0, CELL, CELL, CELL * Mangekyo.PATTERNS, CELL);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class MangekyoOverlayRegistration {
        private MangekyoOverlayRegistration() {}

        @SubscribeEvent
        public static void registerMangekyoOverlay(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("mangekyo_effects", MangekyoEffects::render);
        }
    }
}

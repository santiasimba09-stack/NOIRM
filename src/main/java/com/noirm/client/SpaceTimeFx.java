package com.noirm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noirm.Noirm;
import com.noirm.hiraishin.HiraishinFx;
import com.noirm.hiraishin.HiraishinPackets;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * How the Flying Raijin and the Body Flicker look (client only; the server says what happened in an
 * {@link HiraishinPackets.Fx}).
 * <ul>
 *   <li><b>Flying Raijin:</b> no travel. An afterimage of you, lit yellow-white, is left standing where
 *       you were for half a second; one streak of light joins the two ends for 4 ticks; a sharp flash at
 *       each end; the formula seal flares on the ground where you land. On your own screen: a white-gold
 *       flash and a tiny punch of the field of view.</li>
 *   <li><b>Body Flicker:</b> a pale afterimage where you were and a stretched trail of fading copies
 *       along the path (motion too fast to see), two thin blur streaks; on your screen, the field of view
 *       kicks wide and speed lines rush in from the edges.</li>
 *   <li><b>Read:</b> an enemy who reads a Flying Raijin (Sharingan, perception) sees the seal flare where
 *       it will land, and an arrow at the edge of their screen pointing to it.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class SpaceTimeFx {
    private static final int YELLOW = 0xFFE050;
    private static final int HOT = 0xFFFBE8;
    private static final int PALE = 0xD8E4F0;

    private record Ghost(int entityId, Vec3 pos, long born, int life, int color, float alpha) {}

    private record Streak(Vec3 from, Vec3 to, long born, int life, int color, float width) {}

    private record Flash(Vec3 pos, long born, int life, float size, int color, boolean glyph, boolean flat) {}

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final int MAX = 96;

    private static long clock;
    private static int screenFlash;
    private static int screenFlashColor = HOT;
    private static int speedLines;
    private static float fovKick;
    private static int readLeft;
    private static Vec3 readAt;

    private SpaceTimeFx() {}

    public static void onFx(HiraishinPackets.Fx fx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 from = new Vec3(fx.fx(), fx.fy(), fx.fz());
        Vec3 to = new Vec3(fx.tx(), fx.ty(), fx.tz());
        boolean me = minecraft.player != null && fx.entityId() == minecraft.player.getId();
        Entity who = minecraft.level.getEntity(fx.entityId());
        float height = who == null ? 1.8f : who.getBbHeight();
        switch (fx.kind()) {
            case HiraishinFx.FLICKER -> {
                ghost(fx.entityId(), from, 9, PALE, 0.55f);
                for (int i = 1; i <= 3; i++) {
                    float t = i / 4f;
                    ghost(fx.entityId(), from.lerp(to, t), 4 + i, PALE, 0.32f - 0.06f * i);
                }
                streak(from.add(0, height * 0.6, 0), to.add(0, height * 0.6, 0), 4, PALE, 0.35f);
                streak(from.add(0, height * 0.25, 0), to.add(0, height * 0.25, 0), 3, PALE, 0.22f);
                if (me) {
                    fovKick = Math.max(fovKick, 0.16f);
                    speedLines = 6;
                }
            }
            case HiraishinFx.JUMP, HiraishinFx.SLASH -> {
                ghost(fx.entityId(), from, 15, YELLOW, 1f);
                ghost(fx.entityId(), from, 6, HOT, 0.8f);
                streak(from.add(0, height * 0.55, 0), to.add(0, height * 0.55, 0), 6, YELLOW, 1.1f);
                streak(from.add(0, height * 0.55, 0), to.add(0, height * 0.55, 0), 5, HOT, 0.18f);
                flash(from.add(0, height * 0.55, 0), 7, 4.4f, YELLOW, false, false);
                flash(to.add(0, height * 0.55, 0), 8, 5f, YELLOW, false, false);
                flash(from.add(0, 0.04, 0), 12, 2.4f, YELLOW, true, true);
                flash(to.add(0, 0.04, 0), 14, 3f, YELLOW, true, true);
                if (me) {
                    screenFlash = 5;
                    screenFlashColor = fx.kind() == HiraishinFx.SLASH ? 0xFFF0A0 : HOT;
                    fovKick = Math.min(fovKick, -0.07f);
                }
            }
            case HiraishinFx.REDIRECT -> {
                streak(from, to, 5, YELLOW, 0.7f);
                flash(from, 5, 3f, YELLOW, false, false);
                flash(to, 6, 3.4f, YELLOW, false, false);
                flash(from, 8, 1.6f, YELLOW, true, false);
            }
            case HiraishinFx.RECALL -> flash(from, 5, 1.4f, YELLOW, false, false);
            case HiraishinFx.READ -> {
                readAt = to;
                readLeft = 10;
                flash(to.add(0, 1.0, 0), 10, 1.8f, YELLOW, true, false);
            }
            case HiraishinFx.LEARNED -> {
                flash(to.add(0, 0.05, 0), 30, 5f, YELLOW, true, true);
                if (me) {
                    screenFlash = 10;
                    screenFlashColor = 0xFFE890;
                }
            }
            default -> { }
        }
    }

    private static void ghost(int id, Vec3 at, int life, int color, float alpha) {
        if (GHOSTS.size() < MAX) {
            GHOSTS.add(new Ghost(id, at, clock, life, color, alpha));
        }
    }

    private static void streak(Vec3 a, Vec3 b, int life, int color, float width) {
        if (STREAKS.size() < MAX) {
            STREAKS.add(new Streak(a, b, clock, life, color, width));
        }
    }

    static void flash(Vec3 at, int life, float size, int color, boolean glyph, boolean flat) {
        if (FLASHES.size() < MAX) {
            FLASHES.add(new Flash(at, clock, life, size, color, glyph, flat));
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clock++;
        GHOSTS.removeIf(g -> clock - g.born() > g.life());
        STREAKS.removeIf(s -> clock - s.born() > s.life());
        FLASHES.removeIf(f -> clock - f.born() > f.life());
        if (screenFlash > 0) {
            screenFlash--;
        }
        if (speedLines > 0) {
            speedLines--;
        }
        if (readLeft > 0) {
            readLeft--;
        }
        fovKick *= 0.62f;
        if (Math.abs(fovKick) < 0.003f) {
            fovKick = 0;
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GHOSTS.clear();
        STREAKS.clear();
        FLASHES.clear();
        screenFlash = speedLines = readLeft = 0;
        fovKick = 0;
    }

    @SubscribeEvent
    public static void fov(ComputeFovModifierEvent event) {
        if (fovKick != 0) {
            event.setNewFovModifier(event.getNewFovModifier() * (1f + fovKick));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || GHOSTS.isEmpty() && STREAKS.isEmpty() && FLASHES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (Ghost ghost : GHOSTS) {
            Entity entity = minecraft.level.getEntity(ghost.entityId());
            float age = (clock - ghost.born()) + partial;
            float fade = Mth.clamp(1f - age / ghost.life(), 0f, 1f);
            if (entity != null && fade > 0) {
                SpaceTimeDraw.silhouette(entity, ghost.pos(), cam, pose, buffers, partial, ghost.color(), ghost.alpha() * fade * fade, true);
            }
        }
        Matrix4f matrix = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SpaceTimeDraw.GLOW));
        for (Streak streak : STREAKS) {
            float age = (clock - streak.born()) + partial;
            float fade = Mth.clamp(1f - age / streak.life(), 0f, 1f);
            SpaceTimeDraw.ribbon(glow, matrix, streak.from(), streak.to(), cam, streak.width() * (0.6f + 0.4f * fade), streak.color(), fade);
        }
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        for (Flash flash : FLASHES) {
            if (flash.glyph()) {
                continue;
            }
            float age = (clock - flash.born()) + partial;
            float t = Mth.clamp(age / flash.life(), 0f, 1f);
            float size = flash.size() * (0.55f + 0.6f * (float) Math.sqrt(t));
            SpaceTimeDraw.billboard(glow, matrix, flash.pos(), cam, right, up, size, flash.color(), (1 - t) * (1 - t), 0, 1);
            SpaceTimeDraw.billboard(glow, matrix, flash.pos(), cam, right, up, size * 0.4f, HOT, 1 - t, 0, 1);
        }
        buffers.endBatch(RenderType.eyes(SpaceTimeDraw.GLOW));
        VertexConsumer glyph = buffers.getBuffer(RenderType.eyes(SpaceTimeDraw.GLYPH));
        for (Flash flash : FLASHES) {
            if (!flash.glyph()) {
                continue;
            }
            float age = (clock - flash.born()) + partial;
            float t = Mth.clamp(age / flash.life(), 0f, 1f);
            float size = flash.size() * (0.7f + 0.5f * t);
            float alpha = t < 0.15f ? t / 0.15f : (1 - t) / 0.85f;
            if (flash.flat()) {
                SpaceTimeDraw.billboard(glyph, matrix, flash.pos(), cam, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), size, flash.color(), alpha, 0, 1);
            } else {
                SpaceTimeDraw.billboard(glyph, matrix, flash.pos(), cam, right, up, size, flash.color(), alpha, 0, 1);
            }
        }
        buffers.endBatch(RenderType.eyes(SpaceTimeDraw.GLYPH));
    }

    static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partial, int width, int height) {
        if (screenFlash > 0) {
            float t = (screenFlash - partial) / 5f;
            int alpha = Math.round(Mth.clamp(t, 0f, 1f) * 115);
            graphics.fill(0, 0, width, height, (alpha << 24) | screenFlashColor);
        }
        if (speedLines > 0) {
            drawSpeedLines(graphics, width, height, (speedLines - partial) / 6f);
        }
        if (readLeft > 0 && readAt != null) {
            drawReadArrow(graphics, width, height, (readLeft - partial) / 10f);
        }
    }

    /** Thin streaks rushing in from the edges toward the middle. */
    private static void drawSpeedLines(GuiGraphics graphics, int width, int height, float strength) {
        java.util.Random random = new java.util.Random(clock * 31);
        int lines = 26;
        float cx = width / 2f, cy = height / 2f;
        for (int i = 0; i < lines; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            float r0 = Math.max(width, height) * (0.33f + 0.25f * random.nextFloat());
            float r1 = Math.max(width, height) * 0.75f;
            int alpha = Math.round(strength * (70 + random.nextInt(80)));
            for (float r = r0; r < r1; r += 2) {
                int x = Math.round(cx + (float) Math.cos(angle) * r);
                int y = Math.round(cy + (float) Math.sin(angle) * r);
                graphics.fill(x, y, x + 1, y + 1, (alpha << 24) | 0xF4F4FF);
            }
        }
    }

    /** A gold arrow at the screen edge, pointing where a read Flying Raijin is about to land. */
    private static void drawReadArrow(GuiGraphics graphics, int width, int height, float strength) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Vec3 to = readAt.subtract(minecraft.player.position());
        double yawTo = Math.toDegrees(Math.atan2(to.z, to.x)) - 90;
        double rel = Math.toRadians(Mth.wrapDegrees(yawTo - minecraft.player.getYRot()));
        int alpha = Math.round(strength * 220);
        int edge = Math.max(24, height / 10);
        graphics.fillGradient(0, 0, width, edge, (Math.round(strength * 90) << 24) | 0xFFD040, 0);
        float cx = width / 2f + (float) Math.sin(rel) * width * 0.38f;
        float cy = height / 2f - (float) Math.cos(rel) * height * 0.38f;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) rel));
        for (int i = 0; i < 9; i++) {
            graphics.fill(-i, i * 2 - 8, i + 1, i * 2 - 6, (alpha << 24) | 0xFFD040);
        }
        graphics.pose().popPose();
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class SpaceTimeFxRegistration {
        private SpaceTimeFxRegistration() {}

        @SubscribeEvent
        public static void registerSpaceTimeFx(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("spacetime_fx", SpaceTimeFx::renderOverlay);
        }
    }
}

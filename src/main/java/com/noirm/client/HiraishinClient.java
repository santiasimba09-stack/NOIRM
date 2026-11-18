package com.noirm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noirm.Noirm;
import com.noirm.hiraishin.HiraishinPackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Flying Raijin on the owner's side (client only):
 * <ul>
 *   <li><b>Keys:</b> Y writes a mark on what you look at (sneak + Y recalls your kunai), X jumps to the
 *       aimed mark (or sends an incoming attack there), U picks the next mark.</li>
 *   <li><b>Your marks</b> show as faint gold seal glyphs, through walls, out to {@value #SHOW_RANGE}
 *       blocks; the one you're aiming at burns bright and turns slowly. Only you see them.</li>
 *   <li><b>The HUD:</b> marks held and the cap, and your nearest marks with their distance.</li>
 * </ul>
 * It also tells the server who you're locked on to (the Body Flicker and the Flying Raijin aim there).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, value = Dist.CLIENT)
public final class HiraishinClient {
    public static final KeyMapping MARK = key("hiraishin_mark", GLFW.GLFW_KEY_Y);
    public static final KeyMapping JUMP = key("hiraishin_jump", GLFW.GLFW_KEY_X);
    public static final KeyMapping CYCLE = key("hiraishin_cycle", GLFW.GLFW_KEY_U);
    public static final double SHOW_RANGE = 64;
    /** The same cone the server uses to pick the aimed mark (degrees). */
    private static final double AIM_CONE = 22;

    private static List<HiraishinPackets.MarkInfo> marks = List.of();
    private static int selected = -1;
    private static int cap = 8;
    private static boolean canJump;
    private static int lastLock = -1;
    private static int aimed = -1;
    private static int age;

    private HiraishinClient() {}

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.noirm." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, defaultKey, CombatKeys.CATEGORY);
    }

    public static void onMarks(HiraishinPackets.Marks packet) {
        marks = List.copyOf(packet.marks());
        selected = packet.selected();
        cap = packet.cap();
        canJump = packet.canJump();
    }

    /** Your marks as last sent (screenshots, tests). */
    public static List<HiraishinPackets.MarkInfo> marks() {
        return marks;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.player == null) {
            return;
        }
        age++;
        while (MARK.consumeClick()) {
            NoirmNetwork.sendToServer(new HiraishinPackets.Action(minecraft.player.isShiftKeyDown() ? HiraishinPackets.RECALL : HiraishinPackets.MARK, 0));
        }
        while (JUMP.consumeClick()) {
            NoirmNetwork.sendToServer(new HiraishinPackets.Action(HiraishinPackets.JUMP, 0));
        }
        while (CYCLE.consumeClick()) {
            NoirmNetwork.sendToServer(new HiraishinPackets.Action(HiraishinPackets.CYCLE, 0));
        }
        LivingEntity lock = LockOn.target();
        int id = lock == null ? -1 : lock.getId();
        if (id != lastLock) {
            lastLock = id;
            NoirmNetwork.sendToServer(new HiraishinPackets.Action(HiraishinPackets.LOCK, id));
        }
        aimed = aimedMark(minecraft);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        marks = List.of();
        selected = -1;
        canJump = false;
        lastLock = -1;
    }

    /** Where a mark is now: following its kunai or body if the client can see it. */
    private static Vec3 where(Minecraft minecraft, HiraishinPackets.MarkInfo mark, float partial) {
        if (mark.entityId() >= 0 && minecraft.level != null) {
            Entity carrier = minecraft.level.getEntity(mark.entityId());
            if (carrier != null) {
                Vec3 pos = carrier.getPosition(partial);
                return carrier instanceof LivingEntity body ? pos.add(0, body.getBbHeight() + 0.45, 0) : pos.add(0, 0.15, 0);
            }
        }
        return new Vec3(mark.x(), mark.y(), mark.z());
    }

    private static int aimedMark(Minecraft minecraft) {
        if (marks.isEmpty() || minecraft.player == null) {
            return -1;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getViewVector(1f);
        int best = -1;
        double bestAngle = Double.MAX_VALUE;
        for (HiraishinPackets.MarkInfo mark : marks) {
            Vec3 at = where(minecraft, mark, 1f);
            double distance = at.distanceTo(eye);
            if (distance < 0.8 || distance > 256) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(at.subtract(eye).normalize()), -1, 1)));
            if (angle <= AIM_CONE + Math.toDegrees(Math.atan2(0.8, distance)) && angle < bestAngle) {
                best = mark.id();
                bestAngle = angle;
            }
        }
        return best >= 0 ? best : selected;
    }

    // ---------------------------------------------------------------- the glyphs, through walls

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || marks.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        Vector3f left = new Vector3f(camera.getLeftVector());
        Vector3f up = new Vector3f(camera.getUpVector());
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SpaceTimeDraw.GLYPH);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (HiraishinPackets.MarkInfo mark : marks) {
            Vec3 at = where(minecraft, mark, partial);
            double distance = at.distanceTo(cam);
            if (distance > SHOW_RANGE) {
                continue;
            }
            boolean target = mark.id() == aimed;
            float fadeOut = mark.ticksLeft() < 0 ? 1f : Mth.clamp(mark.ticksLeft() / 200f, 0.25f, 1f);
            float alpha = (target ? 0.95f : 0.42f) * fadeOut * (float) Mth.clamp(1.15 - distance / SHOW_RANGE, 0.25, 1);
            float size = (float) Math.max(0.6, distance * 0.075) * (target ? 1.5f + 0.1f * Mth.sin((age + partial) * 0.4f) : 1f);
            float turn = target ? (age + partial) * 0.06f : 0f;
            int rgb = target ? 0xFFE868 : mark.kind() == 3 ? 0x9CE8FF : 0xE8B840;
            glyph(builder, matrix, at.subtract(cam), left, up, size, turn, rgb, alpha);
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void glyph(BufferBuilder b, Matrix4f m, Vec3 at, Vector3f left, Vector3f up, float size, float turn, int rgb, float alpha) {
        float h = size / 2;
        float c = Mth.cos(turn), s = Mth.sin(turn);
        Vector3f r = new Vector3f(left).mul(-c).add(new Vector3f(up).mul(s)).mul(h);
        Vector3f u = new Vector3f(up).mul(c).add(new Vector3f(left).mul(s)).mul(h);
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        int red = rgb >> 16 & 0xFF, green = rgb >> 8 & 0xFF, blue = rgb & 0xFF, a = Math.round(alpha * 255);
        b.vertex(m, x - r.x - u.x, y - r.y - u.y, z - r.z - u.z).uv(0, 1).color(red, green, blue, a).endVertex();
        b.vertex(m, x + r.x - u.x, y + r.y - u.y, z + r.z - u.z).uv(1, 1).color(red, green, blue, a).endVertex();
        b.vertex(m, x + r.x + u.x, y + r.y + u.y, z + r.z + u.z).uv(1, 0).color(red, green, blue, a).endVertex();
        b.vertex(m, x - r.x + u.x, y - r.y + u.y, z - r.z + u.z).uv(0, 0).color(red, green, blue, a).endVertex();
    }

    // ---------------------------------------------------------------- the HUD

    static void renderHud(ForgeGui gui, GuiGraphics graphics, float partial, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.player == null || minecraft.options.hideGui || marks.isEmpty() && !canJump) {
            return;
        }
        Font font = minecraft.font;
        int x = width - 112;
        int y = height - 74;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 0.85f, 0.35f, 0.95f);
        graphics.blit(SpaceTimeDraw.GLYPH, x, y - 1, 0, 0, 12, 12, 12, 12);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.drawString(font, Component.translatable("hud.noirm.hiraishin.marks", marks.size(), cap), x + 15, y + 1, 0xFFE8C060, true);
        Vec3 me = minecraft.player.position();
        List<HiraishinPackets.MarkInfo> nearest = new ArrayList<>(marks);
        nearest.sort(Comparator.comparingDouble(m -> where(minecraft, m, partial).distanceTo(me)));
        int line = 0;
        for (HiraishinPackets.MarkInfo mark : nearest) {
            if (line >= 3) {
                break;
            }
            boolean target = mark.id() == aimed;
            String kind = switch (mark.kind()) {
                case 0 -> "kunai";
                case 1 -> "block";
                case 2 -> "body";
                default -> "ally";
            };
            Component text = Component.translatable("hud.noirm.hiraishin.line", Component.translatable("hud.noirm.hiraishin.kind." + kind),
                    Math.round(where(minecraft, mark, partial).distanceTo(me)));
            graphics.drawString(font, (target ? "▶ " : "  ") + text.getString(), x, y + 13 + line * 10, target ? 0xFFFFF0A0 : 0xC0D8C8A0, true);
            line++;
        }
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class HiraishinKeysRegistration {
        private HiraishinKeysRegistration() {}

        @SubscribeEvent
        public static void registerHiraishinKeys(RegisterKeyMappingsEvent event) {
            event.register(MARK);
            event.register(JUMP);
            event.register(CYCLE);
        }

        @SubscribeEvent
        public static void registerHiraishinHud(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("hiraishin_hud", HiraishinClient::renderHud);
        }
    }
}
